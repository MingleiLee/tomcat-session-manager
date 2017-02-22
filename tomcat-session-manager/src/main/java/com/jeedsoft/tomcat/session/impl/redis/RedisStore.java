package com.jeedsoft.tomcat.session.impl.redis;

import static com.jeedsoft.tomcat.session.FastStoreSessionManager.EXPIRING_TIMEOUT;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import com.jeedsoft.tomcat.session.FastStore;
import com.jeedsoft.tomcat.session.definition.MetadataKey;
import com.jeedsoft.tomcat.session.definition.StatisticsKey;
import com.jeedsoft.tomcat.session.type.CasResult;
import com.jeedsoft.tomcat.session.util.ArrayUtil;
import com.jeedsoft.tomcat.session.util.ObjectStringConverter;
import com.jeedsoft.tomcat.session.util.StringUtil;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.util.Pool;

public class RedisStore implements FastStore
{
	private static final Log log = LogFactory.getLog(RedisStore.class);

	private static final String PREFIX			= "session"; 
	private static final String SUFFIX_META		= "meta"; 
	private static final String SUFFIX_ATTR		= "attr";
	private static final String DELIMITER		= ":";
	
	private static final String STATISTICS_KEY	= "statistics";
	
	private static final String KEY_PATTERN		= "^session:(.+):(meta|attr|note)$";

	private RedisSessionManager manager;
	
	private Pool<Jedis> pool;
	
	public RedisStore(RedisSessionManager manager, Pool<Jedis> pool)
	{
		this.manager = manager;
		this.pool = pool;
	}

	@Override
	public long tryIncrementBackgroundProcessSerial(long localSerial)
	{
		String field = StatisticsKey.BACKGROUND_PROCESS_SERIAL;
		CasResult<Long> result = RedisPoolExecutor.hcheckAndIncr(pool, STATISTICS_KEY, field, localSerial);
		return result.isSuccess() ? -1 : result.getValue();
	}

	@Override
	public void create(String sessionId, Map<String, Object> metadata)
	{
		String metaKey = getKey(sessionId, SUFFIX_META);
		Map<String, String> hash = metadataToHash(metadata);
		RedisPoolExecutor.hmset(pool, metaKey, hash);
	}

	@Override
	public Map<String, Object> getMetadata(String sessionId)
	{
		String metaKey = getKey(sessionId, SUFFIX_META);
		Map<String, String> hash = RedisPoolExecutor.hgetAll(pool, metaKey);
		Map<String, Object> metadata = hashToMetadata(hash);
		return isLive(metadata) ? metadata : null;
	}

	@Override
	public void setMetadataValue(String sessionId, String field, Object value)
	{
		String metadataKey = getKey(sessionId, SUFFIX_META);
		if (value == null) {
			RedisPoolExecutor.hdel(pool, metadataKey, field);
		}
		else {
			RedisPoolExecutor.hset(pool, metadataKey, field, manager.getConverter().toString(value));
			if (value instanceof Long && manager.isRedisWriteDateCopyForLong()) {
				String dateString = manager.getConverter().toString(new Date((Long)value));
				RedisPoolExecutor.hset(pool, metadataKey, field + "$", dateString);
			}
		}
	}

	@Override
	public boolean setExpireTime(String sessionId, long expireTime)
	{
		String metaKey = getKey(sessionId, SUFFIX_META);
		String value = manager.getConverter().toString(expireTime);
		if (RedisPoolExecutor.hsetnx(pool, metaKey, MetadataKey.EXPIRE_TIME, value) == 0) {
			return false;
		}
		if (manager.isRedisWriteDateCopyForLong()) {
			String dateString = manager.getConverter().toString(new Date(expireTime));
			RedisPoolExecutor.hset(pool, metaKey, MetadataKey.EXPIRE_TIME + "$", dateString);
		}
		boolean creationTimeExists = RedisPoolExecutor.hexists(pool, metaKey, MetadataKey.EXPIRE_TIME);
		if (!creationTimeExists) {
			RedisPoolExecutor.del(pool, metaKey);
		}
		return creationTimeExists;
	}

	@Override
	public String getAttribute(String sessionId, String name)
	{
		String key = getKey(sessionId, SUFFIX_ATTR);
		String value = RedisPoolExecutor.hget(pool, key, name);
		return StringUtil.isEmpty(value) ? null : value;
	}

	@Override
	public void setAttribute(String sessionId, String name, String value)
	{
		String key = getKey(sessionId, SUFFIX_ATTR);
		RedisPoolExecutor.hset(pool, key, name, value);
	}

	@Override
	public void setAttributes(String sessionId, Map<String, String> map)
	{
		String key = getKey(sessionId, SUFFIX_ATTR);
		RedisPoolExecutor.hmset(pool, key, map);
	}

	@Override
	public void removeAttribute(String sessionId, String name)
	{
		String key = getKey(sessionId, SUFFIX_ATTR);
		RedisPoolExecutor.hdel(pool, key, name);
	}

	@Override
	public List<String> getAttributeNames(String sessionId)
	{
		String key = getKey(sessionId, SUFFIX_ATTR);
		Set<String> names = RedisPoolExecutor.hkeys(pool, key);
		return new ArrayList<>(names);
	}

	@Override
	public Map<String, Map<String, Object>> getSessions()
	{
		final Map<String, Map<String, Object>> map = new HashMap<>();
		iterateAllSessions(new SessionIterateCallback() {
			public void call(String sessionId, Map<String, Object> metadata) {
				if (isLive(metadata)) {
					map.put(sessionId, metadata);
				}
			}
		});
		return map;
	}

	@Override
	public long getStatisticsValue(String key)
	{
		String s = RedisPoolExecutor.hget(pool, STATISTICS_KEY, key);
		return StringUtil.isEmpty(s) ? 0 : Long.parseLong(s);
	}

	@Override
	public void setStatisticsValue(String key, long value)
	{
		RedisPoolExecutor.hset(pool, STATISTICS_KEY, key, Long.toString(value));
	}

	@Override
	public void incStatisticsValue(String key, long increment)
	{
		RedisPoolExecutor.hincrBy(pool, STATISTICS_KEY, key, increment);
	}

	@Override
	public void remove(String sessionId)
	{
		String[] keys = {
			getKey(sessionId, SUFFIX_META),
			getKey(sessionId, SUFFIX_ATTR),
		};
		RedisPoolExecutor.del(pool, keys);
	}

	@Override
	public void changeSessionId(String oldId, String newId)
	{
		String pattern = getKey(oldId, "*");
		Set<String> keys = RedisPoolExecutor.keys(pool, pattern);
		String[] suffixes = {SUFFIX_META, SUFFIX_ATTR};
		for (String suffix: suffixes) {
			try {
				String oldKey = getKey(oldId, suffix);
				String newKey = getKey(newId, suffix);
				if (keys.contains(oldKey)) {
					RedisPoolExecutor.rename(pool, oldKey, newKey);
				}
			}
			catch (Throwable e) {
				log.error("rename redis key failed: oldId=" + oldId + ", suffix=" + suffix);
			}
		}
	}

	@Override
	public void clean()
	{
		Set<String> sessionIds = getSessions().keySet();
		Set<String> keys = RedisPoolExecutor.keys(pool, "*");
		Pattern pattern = Pattern.compile(KEY_PATTERN);
		List<String> invalidKeys = new ArrayList<>();
		for (String key: keys) {
			if (key.equals(STATISTICS_KEY)) {
				continue;
			}
			Matcher m = pattern.matcher(key);
			if (!m.matches() || !sessionIds.contains(m.group(1))) {
				invalidKeys.add(key);
			}
		}
		if (!invalidKeys.isEmpty()) {
			RedisPoolExecutor.del(pool, ArrayUtil.toStringArray(invalidKeys));
			if (log.isDebugEnabled()) {
				log.debug("clean redis store: " + invalidKeys.size() + " items removed.");
			}
		}
	}

	//-------------------------------------------------------------------------
	// protected/private methods
	//-------------------------------------------------------------------------
	
	private String getKey(String sessionId, String suffix)
	{
		return join(PREFIX, sessionId, suffix); 
	}
	
	private String getSessionId(String redisKey)
	{
		int firstIndex = redisKey.indexOf(DELIMITER);
		int lastIndex = redisKey.lastIndexOf(DELIMITER);
		return redisKey.substring(firstIndex + 1, lastIndex);
	}

	private boolean isLive(Map<String, Object> metadata)
	{
		if (!metadata.containsKey(MetadataKey.CREATION_TIME)) {
			return false;
		}
		Long expireTime = (Long)metadata.get(MetadataKey.EXPIRE_TIME);
		return expireTime == null || expireTime + EXPIRING_TIMEOUT > System.currentTimeMillis();
	}
	
	private void iterateAllSessions(SessionIterateCallback callback)
	{
		ScanParams params = new ScanParams().match(join(PREFIX, "*", SUFFIX_META));
		ScanResult<String> result;
		String cursor = "0";
		Set<String> set = new HashSet<>();
		do {
			result = RedisPoolExecutor.scan(pool, cursor, params);
			for (String key: result.getResult()) {
				String sessionId = getSessionId(key);
				if (set.add(sessionId)) {
					Map<String, String> hash = RedisPoolExecutor.hgetAll(pool, key);
					callback.call(sessionId, hashToMetadata(hash));
				}
			}
			cursor = result.getStringCursor();
		} while (!cursor.equals("0"));
	}

	private String join(String... parts)
	{
		StringBuilder sb = new StringBuilder();
		for (String part: parts) {
			if (sb.length() > 0) {
				sb.append(DELIMITER);
			}
			sb.append(part);
		}
		return sb.toString();
	}

	private Map<String, String> metadataToHash(Map<String, Object> metadata)
	{
		Map<String, String> hash = new HashMap<>();
		ObjectStringConverter converter = manager.getConverter();
		for (Map.Entry<String, Object> entry: metadata.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (MetadataKey.isValidKey(key) && value != null) {
				hash.put(key, converter.toString(value));
				if (value instanceof Long && manager.isRedisWriteDateCopyForLong()) {
					hash.put(key + "$", converter.toString(new Date((Long)value)));
				}
			}
		}
		return hash;
	}

	private Map<String, Object> hashToMetadata(Map<String, String> hash)
	{
		Map<String, Object> metadata = new HashMap<>();
		ObjectStringConverter converter = manager.getConverter();
		for (Map.Entry<String, String> entry: hash.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if (MetadataKey.isValidKey(key) && value != null) {
				metadata.put(key, converter.toObject(value));
			}
		}
		return metadata;
	}

	//-------------------------------------------------------------------------
	// classes
	//-------------------------------------------------------------------------

	private static interface SessionIterateCallback
	{
		void call(String sessionId, Map<String, Object> metadata);
	}
}
