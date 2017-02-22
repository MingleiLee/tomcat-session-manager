package com.jeedsoft.tomcat.session;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.Valve;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.TooManyActiveSessionsException;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

import com.jeedsoft.tomcat.session.definition.MetadataKey;
import com.jeedsoft.tomcat.session.definition.StatisticsKey;
import com.jeedsoft.tomcat.session.util.ObjectStringConverter;

/**
 * A manager to store session in some fast store (for example, Redis).
 * 
 * Load Strategy:
 * 1. The session metadata (creationTime, maxInactiveInterval, etc.) is loaded together at the first time.
 * 2. The attributes are separately loaded when required.
 * 3. When the data above is loaded, they are cached in memory for this request.
 * 
 * Restrictions:
 * 1. Session attribute must be serializable.
 * 2. The argument of Session.setPrinciple must be serializable.
 * 3. Session.addSessionListener is not supported. You cannot bind session listeners per session.  
 *
 * @author Minglei Lee
 */
public class FastStoreSessionManager extends LifecycleMBeanBase implements Manager, PropertyChangeListener
{
	private static final Log log = LogFactory.getLog(FastStoreSessionManager.class);

	//Session expiration should be done in 5 minutes. If timeout, session will be removed by force.
	public static final int EXPIRING_TIMEOUT = 5 * 60 * 1000;
	
    protected static final StringManager sm = StringManager.getManager(ManagerBase.class);

	private FastStore store;
	
    private Context context;

    private ThreadLocal<FastStoreSession>  currentSession = new ThreadLocal<>();
    
	private SessionIdGenerator sessionIdGenerator = new StandardSessionIdGenerator();

	private boolean distributable;
	
    private int maxActiveSessions = -1;

	private int maxInactiveInterval = 30 * 60;
	
	private long sessionCounter = 0;

	private int backgroundProcessInterval = 60; //60 seconds

    private long nextBackgroundProcessTime = 0;

    private long backgroundProcessSerial = 0;
    
	private final PropertyChangeSupport propertyChangeListeners = new PropertyChangeSupport(this);
	
	private ObjectStringConverter converter;
	
	public FastStore getStore()
	{
		return store;
	}

	protected void setStore(FastStore store)
	{
		this.store = store;
	}

	public int getBackgroundProcessInterval()
	{
		return backgroundProcessInterval;
	}

	public void setBackgroundProcessInterval(int backgroundProcessInterval)
	{
        if (backgroundProcessInterval > 0) {
            this.backgroundProcessInterval = backgroundProcessInterval;
        }
	}

	public int getMaxActiveSessions()
	{
		return maxActiveSessions;
	}

	public void setMaxActiveSessions(int maxActiveSessions)
	{
		this.maxActiveSessions = maxActiveSessions;
	}

	//-------------------------------------------------------------------------
	// method from Manager
	//-------------------------------------------------------------------------

	@Override
	public Context getContext()
	{
		return context;
	}

	/**
	 * @see org.apache.catalina.session.ManagerBase
	 */
	@Override
	public void setContext(Context context)
	{
        if (this.context != null) {
            this.context.removePropertyChangeListener(this);
        }

        Context oldContext = this.context;
        this.context = context;
        propertyChangeListeners.firePropertyChange("context", oldContext, context);
        // TODO - delete the line below in Tomcat 9 onwards
        propertyChangeListeners.firePropertyChange("container", oldContext, context);

        // Register with the new Context (if any)
        if (context != null) {
            setMaxInactiveInterval(context.getSessionTimeout() * 60);
            context.addPropertyChangeListener(this);
        }
	}

	@Override
	@Deprecated
	public Container getContainer()
	{
        return getContext();
	}

	@Override
    @Deprecated
	public void setContainer(Container container)
	{
	    setContext((Context)container);
	}

	@Override
	public SessionIdGenerator getSessionIdGenerator()
	{
		return sessionIdGenerator;
	}

	@Override
	public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator)
	{
	    this.sessionIdGenerator = sessionIdGenerator;
	}

	@Override
    @Deprecated
	public int getSessionIdLength()
	{
		return getSessionIdGenerator().getSessionIdLength();
	}

	@Override
    @Deprecated
	public void setSessionIdLength(int idLength)
	{
	    sessionIdGenerator.setSessionIdLength(idLength);
	}

	@Override
	public boolean getDistributable()
	{
		return distributable;
	}

	@Override
	public void setDistributable(boolean distributable)
	{
		this.distributable = distributable;
	}

	@Override
	public int getMaxInactiveInterval()
	{
		return maxInactiveInterval;
	}

	@Override
	public void setMaxInactiveInterval(int maxInactiveInterval)
	{
		int oldValue = this.maxInactiveInterval;
		int newValue = maxInactiveInterval;
        this.maxInactiveInterval = maxInactiveInterval;
        propertyChangeListeners.firePropertyChange("maxInactiveInterval", oldValue, newValue);
	}

	@Override
	public long getSessionCounter()
	{
		return sessionCounter;
	}

	@Override
	public void setSessionCounter(long sessionCounter)
	{
		this.sessionCounter = sessionCounter;		
	}

	@Override
	public void addPropertyChangeListener(PropertyChangeListener listener)
	{
		propertyChangeListeners.addPropertyChangeListener(listener);		
	}

	@Override
	public void removePropertyChangeListener(PropertyChangeListener listener)
	{
	    propertyChangeListeners.removePropertyChangeListener(listener);
	}

	@Override
	public void add(Session session)
	{
	    //This method is never called when using FastStoreSessionManager
		throw new UnsupportedOperationException();
	}

	@Override
	public Session createEmptySession()
	{
	    //This method is never called when using FastStoreSessionManager
		throw new UnsupportedOperationException();
	}

	@Override
	public Session createSession(String sessionId)
	{
		int currentActiveCount = getActiveSessions();
        if (maxActiveSessions >= 0 && currentActiveCount >= maxActiveSessions) {
        	store.incStatisticsValue(StatisticsKey.REJECTED_SESSIONS, 1);
        	String message = sm.getString("managerBase.createSession.ise");
            throw new TooManyActiveSessionsException(message, maxActiveSessions);
        }
		
		if (sessionId == null) {
			sessionId = getSessionIdGenerator().generateSessionId();
        }
		if (log.isDebugEnabled()) {
			log.debug("create session: id=" + sessionId);
		}
		long now = System.currentTimeMillis();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(MetadataKey.CREATION_TIME, now);
		metadata.put(MetadataKey.LAST_ACCESSED_TIME, now);
		metadata.put(MetadataKey.THIS_ACCESSED_TIME, now);
		metadata.put(MetadataKey.MAX_INACTIVE_INTERVAL, getMaxInactiveInterval());
		store.create(sessionId, metadata);
    	store.incStatisticsValue(StatisticsKey.ACTIVE_SESSIONS, 1);
    	if (currentActiveCount >=  getMaxActive()) {
    		setMaxActive(currentActiveCount + 1);
    	}
    	FastStoreSession session = new FastStoreSession(this, sessionId, metadata);
		session.setNew(true);
		currentSession.set(session);
		return session;
	}

	@Override
	public Session findSession(String sessionId) throws IOException
	{
		FastStoreSession session = currentSession.get();
		if (session != null && sessionId != null && sessionId.equals(session.getId())) {
			return session;
		}
		Map<String, Object> metadata = store.getMetadata(sessionId);
		boolean found = metadata != null;
		if (log.isDebugEnabled()) {
			log.debug("find session in store: id=" + sessionId + ", found=" + found);
		}
		session = found ? new FastStoreSession(this, sessionId, metadata) : null;
		currentSession.set(session);
		return session;
	}

	@Override
	public Session[] findSessions()
	{
		Map<String, Map<String, Object>> map = store.getSessions();
		List<String> sessionIds = new ArrayList<>(map.keySet());
		Session[] sessions = new Session[sessionIds.size()];
		if (log.isDebugEnabled()) {
			log.debug("find sessions: count=" + sessions.length);
		}
		for (int i = 0; i < sessions.length; ++i) {
			String sessionId = sessionIds.get(i);
			sessions[i] = new FastStoreSession(this, sessionId, map.get(sessionId));
		}
		return sessions;
	}

	@Override
	public void changeSessionId(Session session)
	{
	    this.changeSessionId(session, sessionIdGenerator.generateSessionId());
	}

	@Override
	public void changeSessionId(Session session, String sessionId)
	{
		((FastStoreSession)session).changeId(sessionId);
	}

	@Override
	public void remove(Session session)
	{
		remove(session, false);		
	}

	@Override
	public void remove(Session session, boolean update)
	{
	    if (update) {
            long now = System.currentTimeMillis();
            int alive = (int)((now - session.getCreationTimeInternal()) / 1000);
            int maxAlive = getSessionMaxAliveTime();
            if (alive > maxAlive) {
            	setSessionMaxAliveTime(alive);
            }
            store.incStatisticsValue(StatisticsKey.EXPIRED_SESSIONS, 1);
            //TODO timing
	    }
		store.remove(session.getId());
    	store.incStatisticsValue(StatisticsKey.ACTIVE_SESSIONS, -1);
	}

	@Override
	public int getActiveSessions()
	{
		return (int)store.getStatisticsValue(StatisticsKey.ACTIVE_SESSIONS);
	}

	@Override
	public long getExpiredSessions()
	{
		return store.getStatisticsValue(StatisticsKey.EXPIRED_SESSIONS);
	}

	@Override
	public void setExpiredSessions(long count)
	{
		store.setStatisticsValue(StatisticsKey.EXPIRED_SESSIONS, count);
	}

	@Override
	public int getRejectedSessions()
	{
		return (int)store.getStatisticsValue(StatisticsKey.REJECTED_SESSIONS);
	}

	@Override
	public int getMaxActive()
	{
		return (int)store.getStatisticsValue(StatisticsKey.MAX_ACTIVE);
	}

	@Override
	public void setMaxActive(int maxActive)
	{
		store.setStatisticsValue(StatisticsKey.MAX_ACTIVE, maxActive);
	}

	@Override
	public int getSessionMaxAliveTime()
	{
		return (int)store.getStatisticsValue(StatisticsKey.SESSION_MAX_ALIVE_TIME);
	}

	@Override
	public void setSessionMaxAliveTime(int sessionMaxAliveTime)
	{
		store.setStatisticsValue(StatisticsKey.SESSION_MAX_ALIVE_TIME, sessionMaxAliveTime);
	}

	@Override
	public int getSessionAverageAliveTime()
	{
		return (int)store.getStatisticsValue(StatisticsKey.SESSION_AVERAGE_ALIVE_TIME);
	}

	@Override
	public int getSessionCreateRate()
	{
		return (int)store.getStatisticsValue(StatisticsKey.SESSION_CREATE_RATE);
	}

	@Override
	public int getSessionExpireRate()
	{
		return (int)store.getStatisticsValue(StatisticsKey.SESSION_EXPIRE_RATE);
	}

	@Override
	public void load() throws ClassNotFoundException, IOException
	{
	    //This method is never called when using FastStoreSessionManager
		throw new UnsupportedOperationException();
	}

	@Override
	public void unload() throws IOException
	{
	    //This method is never called when using FastStoreSessionManager
		throw new UnsupportedOperationException();
	}

	@Override
	public void backgroundProcess()
	{
		if (log.isTraceEnabled()) {
			log.trace("run background progress by container.");
		}
		long now = System.currentTimeMillis();
		if (now >= nextBackgroundProcessTime) {
			long interval = backgroundProcessInterval * 1000;
			nextBackgroundProcessTime = (now / interval) * interval + interval;
			long serial = store.tryIncrementBackgroundProcessSerial(backgroundProcessSerial);
			if (log.isTraceEnabled()) {
				log.trace("background process serial: " + serial);
			}
			if (serial == -1) {
				++backgroundProcessSerial;
	        	doBackgroundProcess();
			}
			else {
				backgroundProcessSerial = serial;
			}
		}
	}

	//-------------------------------------------------------------------------
	// method from PropertyChangeListener
	//-------------------------------------------------------------------------
	
	@Override
	public void propertyChange(PropertyChangeEvent event)
	{
        //code from ManagerBase
        if (!(event.getSource() instanceof Context)) {
            return;
        }
        if (event.getPropertyName().equals("sessionTimeout")) {
            try {
                setMaxInactiveInterval(((Integer)event.getNewValue()).intValue() * 60);
            }
            catch (NumberFormatException e) {
                log.error(sm.getString("managerBase.sessionTimeout", event.getNewValue()));
            }
        }
	}

	//-------------------------------------------------------------------------
	// method from LifecycleMBeanBase
	//-------------------------------------------------------------------------

	@Override
	protected String getDomainInternal()
	{
        return context.getDomain();
	}

	@Override
	protected String getObjectNameKeyProperties()
	{
        StringBuilder name = new StringBuilder();
        name.append("type=Manager");
        name.append(",host=").append(context.getParent().getName());
        name.append(",context=");
        String contextName = context.getName();
        if (!contextName.startsWith("/")) {
            name.append('/');
        }
        name.append(contextName);
        return name.toString();
	}

	@Override
	protected void startInternal() throws LifecycleException
	{
		log.info("starting manager. class=" + this.getClass().getName());
        sessionIdGenerator.setJvmRoute(getJvmRoute());
        if (sessionIdGenerator instanceof Lifecycle) {
            ((Lifecycle)sessionIdGenerator).start();
        }
        setState(LifecycleState.STARTING);
        
		boolean valveFound = false;
		Context context = getContext();
		for (Valve valve : context.getPipeline().getValves()) {
			if (valve instanceof FastStoreSessionValve) {
				((FastStoreSessionValve)valve).setSessionManager(this);
				valveFound = true;
				break;
			}
		}
		if (!valveFound) {
			log.warn("Please config the FastStoreSessionValve in Tomcat's XML.");
		}
		
	    Loader loader = context.getLoader();
	    ClassLoader classLoader = loader == null ? null : loader.getClassLoader();
		log.info("ClassLoader: " + classLoader.getClass());
		converter = new ObjectStringConverter(classLoader);
	}

	@Override
	protected void stopInternal() throws LifecycleException
	{
		log.info("stopping manager. class=" + this.getClass().getName());
        setState(LifecycleState.STOPPING);
        if (sessionIdGenerator instanceof Lifecycle) {
            ((Lifecycle)sessionIdGenerator).stop();
        }
	}

	//-------------------------------------------------------------------------
	// other protected/private methods
	//-------------------------------------------------------------------------
	
    protected String getJvmRoute()
    {
        Engine e = null;
        for (Container c = getContext(); e == null && c != null ; c = c.getParent()) {
            if (c instanceof Engine) {
                e = (Engine)c;
            }
        }
        return e == null ? null : e.getJvmRoute();
    }

	protected void doBackgroundProcess()
	{
        long start = System.currentTimeMillis();
        store.clean();
        Session sessions[] = findSessions();
        if (log.isTraceEnabled()) {
            log.trace("start expire sessions. totalCount=" + sessions.length);
        }
        int expireHere = 0 ;
        for (int i = 0; i < sessions.length; i++) {
            if (!sessions[i].isValid()) {
                expireHere++;
            }
        }
		store.setStatisticsValue(StatisticsKey.ACTIVE_SESSIONS, sessions.length - expireHere);
        long end = System.currentTimeMillis();
        if (expireHere > 0) {
        	if (log.isDebugEnabled()) {
        		log.debug("end expire sessions. timeCost=" + (end - start) + "ms, expiredCount=" + expireHere);
        	}
        }
        else {
        	if (log.isTraceEnabled()) {
        		log.trace("end expire sessions. timeCost=" + (end - start) + "ms, no sessoin expired.");
        	}
        }
	}

	public void afterRequest()
	{
	    FastStoreSession session = currentSession.get();
	    if (session != null) {
	    	try {
	    		session.storeIfModified();
	    	}
	    	catch (Throwable e) {
	            log.error("failed to save session when request finished.", e);
	    	}
	    	finally {
	    		currentSession.remove();
	    	}
	    }
	}

	public ObjectStringConverter getConverter()
	{
		return converter;
	}

	@Override
	public boolean willAttributeDistribute(String name, Object value)
	{
		return false;
	}
}
