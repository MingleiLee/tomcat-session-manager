package com.jeedsoft.tomcat.session.impl.redis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import com.jeedsoft.tomcat.session.type.CasResult;
import com.jeedsoft.tomcat.session.util.ListUtil;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.util.Pool;

/**
 * A static executor based on Redis(Jedis) pool 
 * @author Minglei Lee
 */
public class RedisPoolExecutor
{
	private static final Log log = LogFactory.getLog(RedisPoolExecutor.class);

	//-------------------------------------------------------------------------
	// server methods
	//-------------------------------------------------------------------------

	public static String info(Pool<Jedis> pool, String section)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.info(section);
		}
	}

	//-------------------------------------------------------------------------
	// pool methods
	//-------------------------------------------------------------------------

	public static void destroy(Pool<Jedis> pool)
	{
		try {
			pool.destroy();
		}
		catch (Throwable e) {
			log.error("failed to destroy Jedis pool.", e);
		}
	}
	
	//-------------------------------------------------------------------------
	// key methods
	//-------------------------------------------------------------------------

	public static long del(Pool<Jedis> pool, String... keys)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.del(keys);
		}
	}

	public static String rename(Pool<Jedis> pool, String oldKey, String newKey)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.rename(oldKey, newKey);
		}
	}

	public static Set<String> keys(Pool<Jedis> pool, String pattern)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.keys(pattern);
		}
	}

	public static ScanResult<String> scan(Pool<Jedis> pool, String cursor, ScanParams params)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.scan(cursor, params);
		}
	}

	//-------------------------------------------------------------------------
	// string methods
	//-------------------------------------------------------------------------

	public static String get(Pool<Jedis> pool, String key)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.get(key);
		}
	}
	
	public static List<String> mget(Pool<Jedis> pool, String... keys)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.mget(keys);
		}
	}
	
	public static String set(Pool<Jedis> pool, String key, String value)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.set(key, value);
		}
	}

	//-------------------------------------------------------------------------
	// hash methods
	//-------------------------------------------------------------------------

	public static boolean hexists(Pool<Jedis> pool, String key, String field)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.hexists(key, field);
		}
	}

	public static String hget(Pool<Jedis> pool, String key, String field)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.hget(key, field);
		}
	}

	public static Map<String, String> hgetAll(Pool<Jedis> pool, String key)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.hgetAll(key);
		}
	}

	public static long hset(Pool<Jedis> pool, String key, String field, String value)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.hset(key, field, value);
		}
	}

	public static long hsetnx(Pool<Jedis> pool, String key, String field, String value)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.hsetnx(key, field, value);
		}
	}

	public static String hmset(Pool<Jedis> pool, String key, Map<String, String> hash)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.hmset(key, hash);
		}
	}

	public static long hdel(Pool<Jedis> pool, String key, String... fields)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.hdel(key, fields);
		}
	}

	public static long hincrBy(Pool<Jedis> pool, String key, String field, long value)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.hincrBy(key, field, value);
		}
	}

	public static Set<String> hkeys(Pool<Jedis> pool, String key)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.hkeys(key);
		}
	}

	//-------------------------------------------------------------------------
	// script methods
	//-------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	public static <T> T eval(Pool<Jedis> pool, String script, String[] keys, String... args)
	{
		List<String> keyList = keys == null ? new ArrayList<String>() : ListUtil.toList(keys);
		List<String> argList = ListUtil.toList(args);
		try (Jedis jedis = pool.getResource()) {
			return (T)jedis.eval(script, keyList, argList);
		}
	}

	//-------------------------------------------------------------------------
	// server methods
	//-------------------------------------------------------------------------

	public static String flushDb(Pool<Jedis> pool)
	{
		try (Jedis jedis = pool.getResource()) {
			return jedis.flushDB();
		}
	}

	public static long time(Pool<Jedis> pool)
	{
		try (Jedis jedis = pool.getResource()) {
			List<String> list = jedis.time();
			long seconds = Long.parseLong(list.get(0));
			long microsecond = Long.parseLong(list.get(1));
			return seconds * 1000 + microsecond / 1000;
		}
	}

	//-------------------------------------------------------------------------
	// custom methods
	//-------------------------------------------------------------------------

	public static long delByPattern(Pool<Jedis> pool, String pattern)
	{
		String script	= "local n, keys = 0, redis.call('keys', ARGV[1]) \n"
						+ "for i = 1, #keys, 5000 do \n"
						+ 	"n = n + redis.call('del', unpack(keys, i, math.min(i + 4999, #keys))) \n"
						+ "end \n"
						+ "return n";
		return eval(pool, script, null, pattern);
	}

	public static CasResult<Long> checkAndIncr(Pool<Jedis> pool, String key, long oldValue)
	{
		String script	=	"local v = redis.call('get', KEYS[1]) \n"
						+ 	"if v == ARGV[1] or (not v and ARGV[1] == '0') then \n"
						+		"return {1, redis.call('incr', KEYS[1])} \n"
						+	"else \n"
						+		"return {0, v} \n"
						+	"end \n";
		List<?> list	= eval(pool, script, new String[]{key}, Long.toString(oldValue));
		Object v		= list.get(1);
		boolean success	= (Long)list.get(0) == 1;
		long value		= v == null ? 0 : v instanceof Long ? (Long)v : Long.parseLong((String)v);
		return new CasResult<Long>(success, value);
	}

	public static CasResult<Long> hcheckAndIncr(Pool<Jedis> pool, String key, String field, long oldValue)
	{
		String script	=	"local v = redis.call('hget', KEYS[1], KEYS[2]) \n"
						+ 	"if v == ARGV[1] or (not v and ARGV[1] == '0') then \n"
						+		"return {1, redis.call('hincrby', KEYS[1], KEYS[2], 1)} \n"
						+	"else \n"
						+		"return {0, v} \n"
						+	"end \n";
		List<?> list	= eval(pool, script, new String[]{key, field}, Long.toString(oldValue));
		Object v		= list.get(1);
		boolean success	= (Long)list.get(0) == 1;
		long value		= v == null ? 0 : v instanceof Long ? (Long)v : Long.parseLong((String)v);
		return new CasResult<Long>(success, value);
	}

	public static CasResult<Long> checkAndSet(Pool<Jedis> pool, String key, long oldValue, long newValue)
	{
		String script	=	"local v = redis.call('get', KEYS[1]) \n"
						+ 	"if v == ARGV[1] or (not v and ARGV[1] == '0') then \n"
						+		"redis.call('set', KEYS[1], ARGV[2]) \n"
						+		"return {1, ARGV[2]} \n"
						+	"else \n"
						+		"return {0, v} \n"
						+	"end \n";
		List<?> list	= eval(pool, script, new String[]{key}, Long.toString(oldValue), Long.toString(newValue));
		Object v		= list.get(1);
		boolean success	= (Long)list.get(0) == 1;
		long value		= v == null ? 0 : Long.parseLong((String)v);
		return new CasResult<Long>(success, value);
	}

	public static CasResult<String> checkAndSet(Pool<Jedis> pool, String key, String oldValue, String newValue)
	{
		if (newValue == null) {
			throw new IllegalArgumentException("new value cannot be null");
		}
		if (oldValue == null) {
			oldValue = "";
		}
		String script	=	"local v = redis.call('get', KEYS[1]) \n"
						+ 	"if v == ARGV[1] or (not v and ARGV[1] == '') then \n"
						+		"redis.call('set', KEYS[1], ARGV[2]) \n"
						+		"return {1} \n"
						+	"else \n"
						+		"return {0, v} \n"
						+	"end \n";
		List<?> list	= eval(pool, script, new String[]{key}, oldValue, newValue);
		boolean success	= (Long)list.get(0) == 1;
		String value	= list.size() == 1 ? newValue : (String)list.get(1);
		return new CasResult<String>(success, value);
	}
}
