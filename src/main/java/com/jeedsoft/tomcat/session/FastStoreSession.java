package com.jeedsoft.tomcat.session;

import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.SessionListener;
import org.apache.catalina.TomcatPrincipal;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.session.StandardSessionFacade;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

import com.jeedsoft.tomcat.session.definition.MetadataKey;
import com.jeedsoft.tomcat.session.util.EnumUtil;
import com.jeedsoft.tomcat.session.util.ObjectStringConverter;

@SuppressWarnings("deprecation")
public class FastStoreSession implements HttpSession, Session
{
	private static final Log log = LogFactory.getLog(FastStoreSession.class);

    protected static final boolean LAST_ACCESS_AT_START = Globals.STRICT_SERVLET_COMPLIANCE;

    protected static final StringManager sm = StringManager.getManager(StandardSession.class);

	private static final String PREFIX_ATTRIBUTE = "A:";
	
	private static final String PREFIX_NOTE = "N:";

	private FastStoreSessionManager manager;
	
	private String id;
	
	private boolean isNew;
	
	private boolean valid = true;
	
    protected transient StandardSessionFacade facade;

	protected Map<String, Object> metadata = new HashMap<>();

	protected List<String> noteNames;

	protected Map<String, Object> notes = new HashMap<>();

	protected List<String> attributeNames;

	protected Map<String, Attribute> attributes = new HashMap<>();

    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);
	
	//-------------------------------------------------------------------------
	// constructor
	//-------------------------------------------------------------------------

	public FastStoreSession(FastStoreSessionManager manager, String id, Map<String, Object> metadata)
	{
		this.manager = manager;
		this.id = id;
		this.metadata.putAll(metadata);
	}
	
	//-------------------------------------------------------------------------
	// method from HttpSession
	//-------------------------------------------------------------------------

	@Override
	public String getId()
	{
		return id;
	}

	@Override
	public ServletContext getServletContext()
	{
        if (manager == null) {
            return null;
        }
        Context context = manager.getContext();
        return context == null ? null : context.getServletContext();
	}

	@Override
    @Deprecated
	public HttpSessionContext getSessionContext()
	{
        return null;
	}

	@Override
	public int getMaxInactiveInterval()
	{
		return getMetadataValue(MetadataKey.MAX_INACTIVE_INTERVAL, 0);
	}

	@Override
	public void setMaxInactiveInterval(int maxInactiveInterval)
	{
		setMetadataValue(MetadataKey.MAX_INACTIVE_INTERVAL, maxInactiveInterval);
	}

	@Override
	public boolean isNew()
	{
		return isNew;
	}

	@Override
	public long getCreationTime()
	{
		checkValidInternal("standardSession.getCreationTime.ise");
		return getCreationTimeInternal();
	}

	@Override
	public long getLastAccessedTime()
	{
		checkValidInternal("standardSession.getLastAccessedTime.ise");
		return getLastAccessedTimeInternal();
	}

	@Override
	public Object getAttribute(String name)
	{
		checkValidInternal("standardSession.getAttribute.ise");
		return getAttributeInternal(name);
	}

	@Override
	public Enumeration<String> getAttributeNames()
	{
		checkValidInternal("standardSession.getAttributeNames.ise");
		return Collections.enumeration(getAttributeNamesInternal());
	}

	@Override
	public void setAttribute(String name, Object value)
	{
		checkValidInternal("standardSession.setAttribute.ise");
        if (name == null) {
            throw new IllegalArgumentException(sm.getString("standardSession.setAttribute.namenull"));
        }
		if (value != null && !(value instanceof Serializable)) {
            throw new IllegalArgumentException("session attribute must be serializable");
		}
		if (value == null) {
			removeAttribute(name);
			return;
		}

		// Call the valueBound() method if necessary
		if (value instanceof HttpSessionBindingListener) {
			try {
				HttpSessionBindingEvent event = new HttpSessionBindingEvent(getSession(), name, value);
				((HttpSessionBindingListener)value).valueBound(event);
			}
			catch (Throwable t) {
				log.error(sm.getString("standardSession.bindingEvent"), t);
			}
		}

		// Replace or add this attribute
		Object unbound = getAttributeInternal(name);
		String text = manager.getConverter().toString(value);
		manager.getStore().setAttribute(id, PREFIX_ATTRIBUTE + name, text);
		attributes.put(name, new Attribute(value, text));

		// Call the valueUnbound() method if necessary
		if (unbound instanceof HttpSessionBindingListener) {
			try {
				HttpSessionBindingEvent event = new HttpSessionBindingEvent(getSession(), name);
				((HttpSessionBindingListener)unbound).valueUnbound(event);
			}
			catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				log.error(sm.getString("standardSession.bindingEvent"), t);
			}
		}

		// Notify interested application event listeners
		Context context = manager.getContext();
		Object listeners[] = context.getApplicationEventListeners();
		if (listeners == null) {
			return;
		}
		HttpSessionBindingEvent event = null;
		for (int i = 0; i < listeners.length; i++) {
			if (!(listeners[i] instanceof HttpSessionAttributeListener)) {
				continue;
			}
			HttpSessionAttributeListener listener = (HttpSessionAttributeListener) listeners[i];
			try {
				if (unbound != null) {
					context.fireContainerEvent("beforeSessionAttributeReplaced", listener);
					if (event == null) {
						event = new HttpSessionBindingEvent(getSession(), name, unbound);
					}
					listener.attributeReplaced(event);
					context.fireContainerEvent("afterSessionAttributeReplaced", listener);
				}
				else {
					context.fireContainerEvent("beforeSessionAttributeAdded", listener);
					if (event == null) {
						event = new HttpSessionBindingEvent(getSession(), name, value);
					}
					listener.attributeAdded(event);
					context.fireContainerEvent("afterSessionAttributeAdded", listener);
				}
			}
			catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				try {
					if (unbound != null) {
						context.fireContainerEvent("afterSessionAttributeReplaced", listener);
					}
					else {
						context.fireContainerEvent("afterSessionAttributeAdded", listener);
					}
				}
				catch (Exception e) {
					// Ignore
				}
				log.error(sm.getString("standardSession.attributeEvent"), t);
			}
		}
	}

	@Override
	public void removeAttribute(String name)
	{
		checkValidInternal("standardSession.removeAttribute.ise");
		removeAttributeInternal(name);
	}

	@Override
    @Deprecated
	public Object getValue(String name)
	{
        return getAttribute(name);
	}

	@Override
    @Deprecated
	public String[] getValueNames()
	{
		return EnumUtil.toArray(String.class, getAttributeNames());
	}

	@Override
    @Deprecated
	public void putValue(String name, Object value)
	{
		setAttribute(name, value);
	}

	@Override
    @Deprecated
	public void removeValue(String name)
	{
        removeAttribute(name);
	}

	@Override
	public void invalidate()
	{
		checkValidInternal("standardSession.invalidate.ise");
        expire();
	}

	//-------------------------------------------------------------------------
	// method from Session
	//-------------------------------------------------------------------------

	@Override
	public String getIdInternal()
	{
		return id;
	}

	@Override
	public void setId(String id)
	{
	    //This method is never called when using FastStoreSessionManager
		throw new UnsupportedOperationException();
	}

	@Override
	public void setId(String id, boolean notify)
	{
	    //This method is never called when using FastStoreSessionManager
		throw new UnsupportedOperationException();
	}

	@Override
	public Manager getManager()
	{
		return manager;
	}

	@Override
	public void setManager(Manager manager)
	{
		this.manager = (FastStoreSessionManager)manager;
	}

	@Override
	public String getAuthType()
	{
		return getMetadataValue(MetadataKey.AUTH_TYPE, null);
	}

	@Override
	public void setAuthType(String authType)
	{
		String oldValue = getAuthType();
		setMetadataValue(MetadataKey.AUTH_TYPE, authType);
        support.firePropertyChange("authType", oldValue, authType);
	}

	@Override
	public Principal getPrincipal()
	{
		return getMetadataValue(MetadataKey.PRINCIPAL, null);
	}

	@Override
	public void setPrincipal(Principal principal)
	{
	    if (principal != null && !(principal instanceof Serializable)) {
	    	String className = principal.getClass().getName();
	    	throw new IllegalArgumentException("The principle should be Serializable. class:" + className);
	    }
	    Principal oldValue = getPrincipal();
		setMetadataValue(MetadataKey.PRINCIPAL, principal);
        support.firePropertyChange("principal", oldValue, principal);
	}

	@Override
	public void addSessionListener(SessionListener listener)
	{
		//TODO this method is used in SingleSignOnEntry, check it
		throw new UnsupportedOperationException("method not supported currently.");
	}

	@Override
	public void removeSessionListener(SessionListener listener)
	{
		throw new UnsupportedOperationException("method not supported currently.");
	}

	@Override
	public HttpSession getSession()
	{
        if (facade == null){
        	facade = new StandardSessionFacade(this);
        }
        return facade;
	}

	@Override
	public long getCreationTimeInternal()
	{
		return getMetadataValue(MetadataKey.CREATION_TIME, 0L);
	}

	@Override
	public void setCreationTime(long time)
	{
	    //This method is never called when using FastStoreSessionManager
		throw new UnsupportedOperationException();
	}

	@Override
	public long getThisAccessedTime()
	{
		checkValidInternal("standardSession.getThisAccessedTime.ise");
		return getThisAccessedTimeInternal();
	}

	@Override
	public long getThisAccessedTimeInternal()
	{
		return getMetadataValue(MetadataKey.THIS_ACCESSED_TIME, 0L);
	}

	@Override
	public long getLastAccessedTimeInternal()
	{
		return getMetadataValue(MetadataKey.LAST_ACCESSED_TIME, 0L);
	}

	@Override
	public long getIdleTime()
	{
		checkValidInternal("standardSession.getIdleTime.ise");
		return getIdleTimeInternal();
	}

	@Override
	public long getIdleTimeInternal()
	{
        long now = System.currentTimeMillis();
        if (LAST_ACCESS_AT_START) {
            return Math.max(0, now - getLastAccessedTimeInternal());
        }
        else {
            return Math.max(0, now - getThisAccessedTimeInternal());
        }
	}

	@Override
	public void setNew(boolean isNew)
	{
		this.isNew = isNew;
	}

	@Override
	public boolean isValid()
	{
		if (!isValidInternal()) {
			return false;
		}
        int maxInactiveInterval = getMaxInactiveInterval();
        if (maxInactiveInterval > 0 && getIdleTimeInternal() > 1000 * maxInactiveInterval) {
            expire();
            return false;
        }
        return true;
	}

	@Override
	public void setValid(boolean valid)
	{
		this.valid = valid;
	}

	@Override
	public void access()
	{
		setMetadataValue(MetadataKey.THIS_ACCESSED_TIME, System.currentTimeMillis());
	}

	@Override
	public void endAccess()
	{
        isNew = false;
        long now = System.currentTimeMillis();
        long lastAccessedTime = LAST_ACCESS_AT_START ? getThisAccessedTime() : now;
		setMetadataValue(MetadataKey.LAST_ACCESSED_TIME, lastAccessedTime);
		setMetadataValue(MetadataKey.THIS_ACCESSED_TIME, now);
	}

	@Override
	public Object getNote(String name)
	{
		if (notes.containsKey(name)) {
			return notes.get(name);
		}
		String text = manager.getStore().getAttribute(id, PREFIX_NOTE + name);
		Object note = text == null ? null : manager.getConverter().toObject(text);
		notes.put(name, note);
		return note;
	}

	@Override
	public Iterator<String> getNoteNames()
	{
		if (noteNames == null) {
			noteNames = getBindNames(PREFIX_NOTE);
		}
		return new ArrayList<>(noteNames).iterator();
	}

	@Override
	public void setNote(String name, Object note)
	{
		if (note == null) {
			removeNote(name);
		}
		else {
			String text = manager.getConverter().toString(note); 
			manager.getStore().setAttribute(id, PREFIX_NOTE + name, text);
			notes.put(name, note);
		}
	}

	@Override
	public void removeNote(String name)
	{
		manager.getStore().removeAttribute(id, PREFIX_NOTE + name);
		notes.remove(name);
	}

	@Override
	public void tellChangedSessionId(String newId, String oldId, boolean notifySessionListeners,
			boolean notifyContainerListeners)
	{
		//copied from StandardSession
		Context context = manager.getContext();
		// notify ContainerListeners
		if (notifyContainerListeners) {
			context.fireContainerEvent(Context.CHANGE_SESSION_ID_EVENT, new String[] {oldId, newId});
		}
		// notify HttpSessionIdListener
		if (notifySessionListeners) {
			Object listeners[] = context.getApplicationEventListeners();
			if (listeners != null && listeners.length > 0) {
				HttpSessionEvent event = new HttpSessionEvent(getSession());
				for (Object listener : listeners) {
					if (!(listener instanceof HttpSessionIdListener)) {
						continue;
					}
					HttpSessionIdListener idListener = (HttpSessionIdListener)listener;
					try {
						idListener.sessionIdChanged(event, oldId);
					}
					catch (Throwable t) {
						manager.getContext().getLogger().error(sm.getString("standardSession.sessionEvent"), t);
					}
				}
			}
		}
	}

	/**
	 * @see org.apache.catalina.session.StandardSession
	 */
	@Override
	public void expire()
	{
		if (manager == null || !manager.getStore().setExpireTime(id, System.currentTimeMillis())) {
			return;
		}
		
		// Notify interested application event listeners
		// FIXME - Assumes we call listeners in reverse order
		Context context = manager.getContext();
		// The call to expire() may not have been triggered by the webapp.
		// Make sure the webapp's class loader is set when calling the
		// listeners
		ClassLoader oldContextClassLoader = null;
		try {
			oldContextClassLoader = context.bind(Globals.IS_SECURITY_ENABLED, null);
			Object listeners[] = context.getApplicationLifecycleListeners();
			if (listeners != null && listeners.length > 0) {
				HttpSessionEvent event = new HttpSessionEvent(getSession());
				for (int i = 0; i < listeners.length; i++) {
					int j = (listeners.length - 1) - i;
					if (!(listeners[j] instanceof HttpSessionListener)) {
						continue;
					}
					HttpSessionListener listener = (HttpSessionListener) listeners[j];
					try {
						context.fireContainerEvent("beforeSessionDestroyed", listener);
						listener.sessionDestroyed(event);
						context.fireContainerEvent("afterSessionDestroyed", listener);
					}
					catch (Throwable t) {
						ExceptionUtils.handleThrowable(t);
						try {
							context.fireContainerEvent("afterSessionDestroyed", listener);
						}
						catch (Exception e) {
							// Ignore
						}
						log.error(sm.getString("standardSession.sessionEvent"), t);
					}
				}
			}
		}
		finally {
			context.unbind(Globals.IS_SECURITY_ENABLED, oldContextClassLoader);
		}

		manager.remove(this, true);

		// Call the logout method
		Principal principal = getPrincipal();
		if (principal instanceof TomcatPrincipal) {
			TomcatPrincipal gp = (TomcatPrincipal) principal;
			try {
				gp.logout();
			}
			catch (Exception e) {
				manager.getContext().getLogger().error(sm.getString("standardSession.logoutfail"), e);
			}
		}

		setValid(false);
		
		// Unbind any objects associated with this session
		oldContextClassLoader = null;
		try {
			oldContextClassLoader = context.bind(Globals.IS_SECURITY_ENABLED, null);
			for (String key: getAttributeNamesInternal()) {
				removeAttributeInternal(key);
			}
		}
		finally {
			context.unbind(Globals.IS_SECURITY_ENABLED, oldContextClassLoader);
		}
	}

	@Override
	public void recycle()
	{
	    //This method is never called when using FastStoreSessionManager
		id = null;
        isNew = false;
        facade = null;
        manager = null;
        noteNames = null;
        attributeNames = null;
        metadata.clear();
        notes.clear();
        attributes.clear();
	}

	//-------------------------------------------------------------------------
	// other public methods
	//-------------------------------------------------------------------------

	public void changeId(String newId)
	{
		String oldId = getId();
		log.debug("change session id: old=" + oldId + ", new=" + newId);
		manager.getStore().changeSessionId(oldId, newId);
		this.id = newId;
        tellChangedSessionId(newId, oldId, true, true);
	}
	
    @Override
    public String toString()
    {
    	return "FastStoreSession[" + id + "]";
    }

	//-------------------------------------------------------------------------
	// other protected/private methods
	//-------------------------------------------------------------------------

	protected boolean isValidInternal()
	{
		return valid && metadata.containsKey(MetadataKey.CREATION_TIME);
	}

	protected void checkValidInternal(String messageKey)
	{
        if (!isValidInternal()) {
            throw new IllegalStateException(sm.getString(messageKey));
        }
	}

	private Object getAttributeInternal(String name)
	{
		Attribute attribute = attributes.get(name);
		if (attribute != null) {
			return attribute.value;
		}
		String text = manager.getStore().getAttribute(id, PREFIX_ATTRIBUTE + name);
		Object value = text == null ? null : manager.getConverter().toObject(text);
		attributes.put(name, new Attribute(value, text));
		return value;
	}
	
	private List<String> getAttributeNamesInternal()
	{
		if (attributeNames == null) {
			attributeNames = getBindNames(PREFIX_ATTRIBUTE);
		}
		return attributeNames;
	}
	
	private List<String> getBindNames(String prefix)
	{
		List<String> list = new ArrayList<>();
		int startIndex = prefix.length();
		for (String key: manager.getStore().getAttributeNames(id)) {
			if (key.startsWith(prefix)) {
				list.add(key.substring(startIndex));
			}
		}
		return list;
	}

	/**
	 * @see org.apache.catalina.session.StandardSession
	 */
	private void removeAttributeInternal(String name)
	{
		if (name == null) {
			return;
		}
        Object value = getAttributeInternal(name);
		manager.getStore().removeAttribute(id, PREFIX_ATTRIBUTE + name);
		attributes.remove(name);
        if (value == null) {
        	return;
        }

		// Call the valueUnbound() method if necessary
		HttpSessionBindingEvent event = null;
		if (value instanceof HttpSessionBindingListener) {
			event = new HttpSessionBindingEvent(getSession(), name, value);
			((HttpSessionBindingListener) value).valueUnbound(event);
		}

		// Notify interested application event listeners
		Context context = manager.getContext();
		Object listeners[] = context.getApplicationEventListeners();
		if (listeners == null) {
			return;
		}
		for (int i = 0; i < listeners.length; i++) {
			if (!(listeners[i] instanceof HttpSessionAttributeListener)) {
				continue;
			}
			HttpSessionAttributeListener listener = (HttpSessionAttributeListener) listeners[i];
			try {
				context.fireContainerEvent("beforeSessionAttributeRemoved", listener);
				if (event == null) {
					event = new HttpSessionBindingEvent(getSession(), name, value);
				}
				listener.attributeRemoved(event);
				context.fireContainerEvent("afterSessionAttributeRemoved", listener);
			}
			catch (Throwable t) {
				ExceptionUtils.handleThrowable(t);
				try {
					context.fireContainerEvent("afterSessionAttributeRemoved", listener);
				}
				catch (Exception e) {
					// Ignore
				}
				manager.getContext().getLogger().error(sm.getString("standardSession.attributeEvent"), t);
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected <T> T getMetadataValue(String key, T defaultValue)
	{
		T value = (T)metadata.get(key);
		return value == null ? defaultValue : value;
	}

	protected void setMetadataValue(String key, Object value)
	{
		manager.getStore().setMetadataValue(id, key, value);
		metadata.put(key, value);
	}

	public void storeIfModified()
	{
		Map<String, String> modified = new HashMap<>();
		ObjectStringConverter converter = manager.getConverter();
		for (String key: attributes.keySet()) {
			Attribute attribute = attributes.get(key);
			if (attribute.value != null) {
				String text = converter.toString(attribute.value);
				if (!text.equals(attribute.text)) {
					modified.put(PREFIX_ATTRIBUTE + key, text);
				}
			}
		}
		if (!modified.isEmpty()) {
			if (log.isDebugEnabled()) {
				log.debug("Saving modified attributes: " + modified.keySet());
			}
			manager.getStore().setAttributes(id, modified);
		}
	}
	
	private static class Attribute
	{
		private Object value;
		
		private String text;
		
		private Attribute(Object value, String text)
		{
			this.value = value;
			this.text = text;
		}
	}

	@Override
	public boolean isAttributeDistributable(String name, Object value)
	{
		return false;
	}
}
