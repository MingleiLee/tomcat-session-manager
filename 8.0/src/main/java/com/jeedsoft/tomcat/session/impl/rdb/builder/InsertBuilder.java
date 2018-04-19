package com.jeedsoft.tomcat.session.impl.rdb.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeedsoft.tomcat.session.util.StringUtil;

public class InsertBuilder implements Builder
{
	private String table = null;
	
	private Map<String, Field> fieldMap = new HashMap<String, Field>();
	
	public InsertBuilder()
	{
	}

	public InsertBuilder(String table)
	{
		this.table = table;
	}
	
	public void setField(String name, int sqlType, Object value)
	{
		fieldMap.put(name, new Field(name, sqlType, value));
	}
	
	public boolean hasField()
	{
		return !fieldMap.isEmpty();
	}
	
	public boolean hasField(String name)
	{
		return fieldMap.containsKey(name);
	}
	
	@Override
	public String getSql()
	{
		List<String> fieldNames = new ArrayList<String>(fieldMap.size());
		for (Field field: fieldMap.values()) {
			fieldNames.add(field.name);
		}
		StringBuilder sb = new StringBuilder(1024);
		sb.append("insert into ").append(table);
		sb.append(" (").append(StringUtil.join(fieldNames)).append(")");
		sb.append(" values ");
		sb.append(" (").append(StringUtil.join("?", fieldNames.size())).append(")");
		return sb.toString();
	}
	
	@Override
	public Field[] getParameters()
	{
		Field[] array = new Field[fieldMap.size()];
		int i = 0;
		for (Field field: fieldMap.values()) {
			array[i++] = field;
		}
		return array;
	}
}
