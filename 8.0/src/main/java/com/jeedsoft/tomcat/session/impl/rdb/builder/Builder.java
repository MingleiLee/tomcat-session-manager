package com.jeedsoft.tomcat.session.impl.rdb.builder;

public interface Builder
{
	public String getSql();
	
	public Field[] getParameters();
}
