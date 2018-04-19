package com.jeedsoft.tomcat.session.impl.rdb;

import com.jeedsoft.tomcat.session.util.StringUtil;

public class RdbException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	public RdbException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public RdbException(String message)
	{
		super(message);
	}

	public RdbException(Throwable cause)
	{
		super(cause);
	}

	public RdbException(String sql, Object[] args)
	{
		super("sql execution failed. sql=" + sql + ", args=[" + StringUtil.join(args) + "]");
	}
}
