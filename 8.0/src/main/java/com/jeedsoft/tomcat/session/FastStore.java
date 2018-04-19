package com.jeedsoft.tomcat.session;

import java.util.List;
import java.util.Map;

public interface FastStore 
{
	void clean();
	long tryIncrementBackgroundProcessSerial(long localBackgroundProcessSerial);

	void create(String sessionId, Map<String, Object> metadata);
	void remove(String sesionId);
	void changeSessionId(String oldId, String newId);
	Map<String, Map<String, Object>> getSessions();

	Map<String, Object> getMetadata(String sessionId);
	void setMetadataValue(String sessionId, String key, Object value);
	boolean setExpireTime(String sessionId, long expireTime);
	
	String getAttribute(String sessionId, String name);
	void setAttribute(String sessionId, String name, String value);
	void setAttributes(String sessionId, Map<String, String> map);
	void removeAttribute(String sessionId, String name);
	List<String> getAttributeNames(String sessionId);

	long getStatisticsValue(String key);
	void setStatisticsValue(String key, long value);
	void incStatisticsValue(String key, long increment);
}
