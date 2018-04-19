package com.jeedsoft.tomcat.session.impl.rdb.builder;

public class Field
{
	public String name;
	
	public int sqlType;
	
	public Object value;
	
	public Field(String name, int sqlType, Object value)
	{
		this.name		= name;
		this.sqlType	= sqlType;
		this.value		= value;
	}
	
	public String toString()
	{
		return value == null ? null : value.toString();
	}
}
