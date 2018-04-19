package com.jeedsoft.tomcat.session.impl.rdb;

import java.util.List;
import java.util.Map;

import com.jeedsoft.tomcat.session.FastStore;

public class RdbStore implements FastStore
{
	@SuppressWarnings("unused")
	private RdbSessionManager sessionManager;
	
	public RdbStore(RdbSessionManager sessionManager)
	{
		this.sessionManager = sessionManager;
	}

	@Override
	public void clean()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public long tryIncrementBackgroundProcessSerial(long localBackgroundProcessSerial)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void create(String sessionId, Map<String, Object> metadata)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void remove(String sesionId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void changeSessionId(String oldId, String newId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public Map<String, Map<String, Object>> getSessions()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Object> getMetadata(String sessionId)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMetadataValue(String sessionId, String key, Object value)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean setExpireTime(String sessionId, long expireTime)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getAttribute(String sessionId, String name)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setAttribute(String sessionId, String name, String value)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setAttributes(String sessionId, Map<String, String> map)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeAttribute(String sessionId, String name)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<String> getAttributeNames(String sessionId)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getStatisticsValue(String key)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setStatisticsValue(String key, long value)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void incStatisticsValue(String key, long increment)
	{
		// TODO Auto-generated method stub
		
	}
}
