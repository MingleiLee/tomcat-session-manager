package com.jeedsoft.tomcat.session.impl.rdb;

import org.apache.catalina.LifecycleException;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;

import com.jeedsoft.tomcat.session.FastStoreSessionManager;
import com.jeedsoft.tomcat.session.util.Closer;

public class RdbSessionManager extends FastStoreSessionManager
{
	private DataSource dataSource;
	
	private PoolProperties poolProperties = new PoolProperties();
	
	private String sessionTable;
	
	private String attrbuteTable;
	
	private String statisticsTable;
	
	public RdbSessionManager()
	{
		poolProperties.setTimeBetweenEvictionRunsMillis(30000);
	}

	@Override
	protected void startInternal() throws LifecycleException
	{
		super.startInternal();
		dataSource = new DataSource(poolProperties);
		setStore(new RdbStore(this));
	}
	
	@Override
	protected void stopInternal() throws LifecycleException
	{
		try {
			super.stopInternal();
		}
		finally {
			Closer.close(dataSource);
			dataSource.close(true);
			setStore(null);
		}
	}

	public DataSource getDataSource()
	{
		return dataSource;
	}

	//-------------------------------------------------------------------------
	// table properties
	//-------------------------------------------------------------------------
	
	public String getSessionTable()
	{
		return sessionTable;
	}

	public void setSessionTable(String sessionTable)
	{
		this.sessionTable = sessionTable;
	}

	public String getAttrbuteTable()
	{
		return attrbuteTable;
	}

	public void setAttrbuteTable(String attrbuteTable)
	{
		this.attrbuteTable = attrbuteTable;
	}

	public String getStatisticsTable()
	{
		return statisticsTable;
	}

	public void setStatisticsTable(String statisticsTable)
	{
		this.statisticsTable = statisticsTable;
	}

	//-------------------------------------------------------------------------
	// basic properties
	//-------------------------------------------------------------------------
	
	public String getRdbUrl()
	{
		return poolProperties.getUrl();
	}

	public void setRdbUrl(String url)
	{
		poolProperties.setUrl(url);
	}
	
	public String getRdbDriverClass()
	{
		return poolProperties.getDriverClassName();
	}
	
	public void setRdbDriverClass(String driverClass)
	{
		poolProperties.setDriverClassName(driverClass);
	}

	public String getRdbUser()
	{
		return poolProperties.getUsername();
	}
	
	public void setRdbUser(String user)
	{
		poolProperties.setUsername(user);
	}

	public String getRdbPassword()
	{
		return poolProperties.getPassword();
	}
	
	public void setRdbPassword(String password)
	{
		poolProperties.setPassword(password);
	}
	
	//-------------------------------------------------------------------------
	// pool properties - org.apache.tomcat.jdbc.pool.PoolProperties
	//-------------------------------------------------------------------------

	public boolean isRdbJmxEnabled()
	{
		return poolProperties.isJmxEnabled();
	}
	
	public void setRdbJmxEnabled(boolean isJmxEnabled)
	{
		poolProperties.setJmxEnabled(isJmxEnabled);
	}

	public String getRdbValidationQuery()
	{
		return poolProperties.getValidationQuery();
	}
	
	public void setRdbValidationQuery(String validationQuery)
	{
		poolProperties.setValidationQuery(validationQuery);
	}

	public boolean isRdbTestWhileIdle()
	{
		return poolProperties.isTestWhileIdle();
	}
	
	public void setRdbTestWhileIdle(boolean isTestWhileIdle)
	{
		poolProperties.setTestWhileIdle(isTestWhileIdle);
	}

	public boolean isRdbTestOnBorrow()
	{
		return poolProperties.isTestOnBorrow();
	}
	
	public void setRdbTestOnBorrow(boolean isTestOnBorrow)
	{
		poolProperties.setTestOnBorrow(isTestOnBorrow);
	}

	public boolean isRdbTestOnReturn()
	{
		return poolProperties.isTestOnReturn();
	}
	
	public void setRdbTestOnReturn(boolean isTestOnReturn)
	{
		poolProperties.setTestOnReturn(isTestOnReturn);
	}

	public String getRdbJdbcInterceptors()
	{
		return poolProperties.getJdbcInterceptors();
	}
	
	public void setRdbJdbcInterceptors(String jdbcInterceptors)
	{
		poolProperties.setJdbcInterceptors(jdbcInterceptors);
	}

	public int getRdbInitialCount()
	{
		return poolProperties.getInitialSize();
	}
	
	public void setRdbInitialCount(int initialCount)
	{
		poolProperties.setInitialSize(initialCount);
	}

	public int getRdbMinIdleCount()
	{
		return poolProperties.getMinIdle();
	}
	
	public void setRdbMinIdleCount(int minIdleCount)
	{
		poolProperties.setMinIdle(minIdleCount);
	}

	public int getRdbMaxIdleCount()
	{
		return poolProperties.getMaxIdle();
	}
	
	public void setRdbMaxIdleCount(int maxIdleCount)
	{
		poolProperties.setMaxIdle(maxIdleCount);
	}

	public int getRdbMaxActiveCount()
	{
		return poolProperties.getMaxActive();
	}
	
	public void setRdbMaxActiveCount(int maxActiveCount)
	{
		poolProperties.setMaxActive(maxActiveCount);
	}

	public int getRdbMaxWait()
	{
		return poolProperties.getMaxWait();
	}
	
	public void setRdbMaxWait(int maxWait)
	{
		poolProperties.setMaxWait(maxWait);
	}

	public int getRdbTimeBetweenEvictionRunsMillis()
	{
		return poolProperties.getTimeBetweenEvictionRunsMillis();
	}
	
	public void setRdbTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis)
	{
		poolProperties.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
	}
}
