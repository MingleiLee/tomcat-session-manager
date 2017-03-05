# tomcat-session-manager
Tomcat session manager for Tomcat 8.x

Overview
--------

A tomcat session manager implementation that stores sessions in Redis for easy distribution of requests across a cluster of Tomcat servers. Sessions are implemented as as non-sticky--that is, each request is able to go to any server in the cluster.

We will support using MongoDB or relational database as session store later.

Usage
-----

Add the following into your Tomcat context.xml (or the context block of the server.xml if applicable.)

    <Valve className="com.jeedsoft.tomcat.session.FastStoreSessionValve"/>
    <Manager className="com.jeedsoft.tomcat.session.impl.redis.RedisSessionManager"
        redisServer="127.0.0.1:6379"
        redisPassword=""
        redisDatabase="10"
    />
             
For Redis sentinels, use the following instead:

    <Valve className="com.jeedsoft.tomcat.session.FastStoreSessionValve"/>
    <Manager className="com.jeedsoft.tomcat.session.impl.redis.RedisSessionManager"
        redisMaster="mymaster"
        redisServer="192.168.1.100:5000,192.168.1.101:5000,192.168.1.102:5000"
        redisPassword=""
        redisDatabase="10"
    />

There are some other attributes start with "redis". The following is a full configuration exapmle:

    <Manager className="com.jeedsoft.tomcat.session.impl.redis.RedisSessionManager"
        redisServer="127.0.0.1:6379"
        redisPassword=""
        redisDatabase="10"
        redisTimeout="2000"
        redisWriteDateCopyForLong="false"
        redisPoolMaxTotal="8"
        redisPoolMaxIdle="8"
        redisPoolMinIdle="0"
        redisPoolLifo="true"
        redisPoolMaxWaitMillis="-1"
        redisPoolMinEvictableIdleTimeMillis="1800000"
        redisPoolSoftMinEvictableIdleTimeMillis="-1"
        redisPoolNumTestsPerEvictionRun="3"
        redisPoolTestOnCreate="false"
        redisPoolTestOnBorrow="false"
        redisPoolTestOnReturn="false"
        redisPoolTestWhileIdle="false"
        redisPoolTimeBetweenEvictionRunsMillis="-1"
        redisPoolEvictionPolicyClassName="org.apache.commons.pool2.impl.DefaultEvictionPolicy"
        redisPoolBlockWhenExhausted="true"
        redisPoolJmxEnabled="true"
        redisPoolJmxNameBase=""
        redisPoolJmxNamePrefix="pool"
    />
