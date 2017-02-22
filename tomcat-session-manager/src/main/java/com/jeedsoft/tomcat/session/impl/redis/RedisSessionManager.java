package com.jeedsoft.tomcat.session.impl.redis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import com.jeedsoft.tomcat.session.FastStoreSessionManager;
import com.jeedsoft.tomcat.session.util.StringUtil;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Protocol;
import redis.clients.util.Pool;

public class RedisSessionManager extends FastStoreSessionManager
{
	private static final Log log = LogFactory.getLog(RedisSessionManager.class);

	private JedisPoolConfig poolConfig = new JedisPoolConfig();
	
	private Pool<Jedis> jedisPool;
	
	private String redisMaster;
	
	private String redisServer; //host[:port], e.g., 192.168.1.12, cache-server:12345
	
	private List<HostAndPort> servers;
	
	private String redisPassword;
	
	private int redisDatabase = 10; //0~15
	
	private int redisTimeout = Protocol.DEFAULT_TIMEOUT;
	
	private boolean redisWriteDateCopyForLong = false;

	@Override
	protected void startInternal() throws LifecycleException
	{
		super.startInternal();
		if (StringUtil.isEmpty(redisMaster)) {
			if (servers == null || servers.size() != 1) {
				throw new LifecycleException("For standalone mode, server count should be 1.");
			}
			HostAndPort server = servers.get(0);
			Object[] args = {server.getHost(), server.getPort(), redisDatabase};
			log.info(String.format("Redis session store: mode=standalone, host=%s, port=%d, database=%d", args));
			jedisPool = new JedisPool(poolConfig, server.getHost(), server.getPort(), redisTimeout, redisPassword, redisDatabase);
		}
		else {
			if (servers == null || servers.size() < 3) {
				throw new LifecycleException("For sentinel mode, server count should be 3 or greater.");
			}
			log.info(String.format("Redis session store: mode=sentinel, master=%s, database=%d", redisMaster, redisDatabase));
			Set<String> sentinels = new HashSet<>();
			for (HostAndPort server: servers) {
				sentinels.add(server.getHost() + ":" + server.getPort());
				log.info(String.format("    sentinel: host=%s, port=%d", server.getHost(), server.getPort()));
			}
			jedisPool = new JedisSentinelPool(redisMaster, sentinels, poolConfig, redisTimeout, redisPassword, redisDatabase);
		}
		setStore(new RedisStore(this, jedisPool));
		
		String info = RedisPoolExecutor.info(jedisPool, "server");
		String version = getServerProperty(info, "redis_version");
		String os = getServerProperty(info, "os");
		String bits = getServerProperty(info, "arch_bits");
		log.info(String.format("Redis server information: version=%s, os=%s, arch=%sbit", version, os, bits));
	}
	
	@Override
	protected void stopInternal() throws LifecycleException
	{
		try {
			super.stopInternal();
		}
		finally {
			RedisPoolExecutor.destroy(jedisPool);
			setStore(null);
		}
	}

	//-------------------------------------------------------------------------
	// basic properties
	//-------------------------------------------------------------------------

	public String getRedisMaster()
	{
		return redisMaster;
	}

	public void setRedisMaster(String redisMaster)
	{
		this.redisMaster = redisMaster;
	}

	public String getRedisServer()
	{
		return redisServer;
	}

	public void setRedisServer(String redisServer)
	{
		this.redisServer = redisServer;
		this.servers = new ArrayList<>();
		if (!StringUtil.isEmpty(redisServer)) {
			for (String item: redisServer.split(",")) {
				String[] pair = item.split(":");
				String host = pair[0].trim();
				int port = pair.length == 2 ? Integer.parseInt(pair[1].trim()) : Protocol.DEFAULT_PORT;
				servers.add(new HostAndPort(host, port));
			}
		}
	}

	public String getRedisPassword()
	{
		return redisPassword;
	}

	public void setRedisPassword(String redisPassword)
	{
		this.redisPassword = redisPassword != null && redisPassword.length() > 0 ? redisPassword : null;
	}

	public int getRedisDatabase()
	{
		return redisDatabase;
	}

	public void setRedisDatabase(int redisDatabase)
	{
		this.redisDatabase = redisDatabase;
	}

	public int getRedisTimeout()
	{
		return redisTimeout;
	}

	public void setRedisTimeout(int redisTimeout)
	{
		this.redisTimeout = redisTimeout;
	}

	public boolean isRedisWriteDateCopyForLong()
	{
		return redisWriteDateCopyForLong;
	}

	public void setRedisWriteDateCopyForLong(boolean redisWriteDateCopyForLong)
	{
		this.redisWriteDateCopyForLong = redisWriteDateCopyForLong;
	}

	//-------------------------------------------------------------------------
	// pool config - org.apache.commons.pool2.impl.GenericObjectPoolConfig
	//-------------------------------------------------------------------------

	public int getRedisPoolMaxTotal()
	{
		return poolConfig.getMaxTotal();
	}

	public void setRedisPoolMaxTotal(int maxTotal)
	{
		poolConfig.setMaxTotal(maxTotal);
	}

	public int getRedisPoolMaxIdle()
	{
		return poolConfig.getMaxIdle();
	}

	public void setRedisPoolMaxIdle(int maxIdle)
	{
		poolConfig.setMaxIdle(maxIdle);
	}

	public int getRedisPoolMinIdle()
	{
		return poolConfig.getMinIdle();
	}

	public void setRedisPoolMinIdle(int minIdle)
	{
		poolConfig.setMinIdle(minIdle);
	}

	//-------------------------------------------------------------------------
	// pool config - org.apache.commons.pool2.impl.BaseObjectPoolConfig
	//-------------------------------------------------------------------------

	public boolean getRedisPoolLifo()
	{
		return poolConfig.getLifo();
	}

	public void setRedisPoolLifo(boolean lifo)
	{
		poolConfig.setLifo(lifo);
	}

	public long getRedisPoolMaxWaitMillis()
	{
		return poolConfig.getMaxWaitMillis();
	}

	public void setRedisPoolMaxWaitMillis(long maxWaitMillis)
	{
		poolConfig.setMaxWaitMillis(maxWaitMillis);
	}

	public long getRedisPoolMinEvictableIdleTimeMillis()
	{
		return poolConfig.getMinEvictableIdleTimeMillis();
	}

	public void setRedisPoolMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis)
	{
		poolConfig.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
	}

	public long getRedisPoolSoftMinEvictableIdleTimeMillis()
	{
		return poolConfig.getSoftMinEvictableIdleTimeMillis();
	}

	public void setRedisPoolSoftMinEvictableIdleTimeMillis(long softMinEvictableIdleTimeMillis)
	{
		poolConfig.setSoftMinEvictableIdleTimeMillis(softMinEvictableIdleTimeMillis);
	}

	public int getRedisPoolNumTestsPerEvictionRun()
	{
		return poolConfig.getNumTestsPerEvictionRun();
	}

	public void setRedisPoolNumTestsPerEvictionRun(int numTestsPerEvictionRun)
	{
		poolConfig.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
	}

	public boolean getRedisPoolTestOnCreate()
	{
		return poolConfig.getTestOnCreate();
	}

	public void setRedisPoolTestOnCreate(boolean testOnCreate)
	{
		poolConfig.setTestOnCreate(testOnCreate);
	}

	public boolean getRedisPoolTestOnBorrow()
	{
		return poolConfig.getTestOnBorrow();
	}

	public void setRedisPoolTestOnBorrow(boolean testOnBorrow)
	{
		poolConfig.setTestOnBorrow(testOnBorrow);
	}

	public boolean getRedisPoolTestOnReturn()
	{
		return poolConfig.getTestOnReturn();
	}

	public void setRedisPoolTestOnReturn(boolean testOnReturn)
	{
		poolConfig.setTestOnReturn(testOnReturn);
	}

	public boolean getRedisPoolTestWhileIdle()
	{
		return poolConfig.getTestWhileIdle();
	}

	public void setRedisPoolTestWhileIdle(boolean testWhileIdle)
	{
		poolConfig.setTestWhileIdle(testWhileIdle);
	}

	public long getRedisPoolTimeBetweenEvictionRunsMillis()
	{
		return poolConfig.getTimeBetweenEvictionRunsMillis();
	}

	public void setRedisPoolTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis)
	{
		poolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
	}

	public String getRedisPoolEvictionPolicyClassName()
	{
		return poolConfig.getEvictionPolicyClassName();
	}

	public void setRedisPoolEvictionPolicyClassName(String evictionPolicyClassName)
	{
		poolConfig.setEvictionPolicyClassName(evictionPolicyClassName);
	}

	public boolean getRedisPoolBlockWhenExhausted()
	{
		return poolConfig.getBlockWhenExhausted();
	}

	public void setRedisPoolBlockWhenExhausted(boolean blockWhenExhausted)
	{
		poolConfig.setBlockWhenExhausted(blockWhenExhausted);
	}

	public boolean getRedisPoolJmxEnabled()
	{
		return poolConfig.getJmxEnabled();
	}

	public void setRedisPoolJmxEnabled(boolean jmxEnabled)
	{
		poolConfig.setJmxEnabled(jmxEnabled);
	}

	public String getRedisPoolJmxNameBase()
	{
		return poolConfig.getJmxNameBase();
	}

	public void setRedisPoolJmxNameBase(String jmxNameBase)
	{
		poolConfig.setJmxNameBase(jmxNameBase);
	}

	public String getRedisPoolJmxNamePrefix()
	{
		return poolConfig.getJmxNamePrefix();
	}

	public void setRedisPoolJmxNamePrefix(String jmxNamePrefix)
	{
		poolConfig.setJmxNamePrefix(jmxNamePrefix);
	}

	//-------------------------------------------------------------------------
	// private
	//-------------------------------------------------------------------------

	private static String getServerProperty(String info, String key)
	{
		Matcher matcher = Pattern.compile("^" + key + ":(.*)$", Pattern.MULTILINE).matcher(info);
		return matcher.find() ? matcher.group(1).trim() : "unknown";
	}
}
