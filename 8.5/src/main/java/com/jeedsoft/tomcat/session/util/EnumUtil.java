package com.jeedsoft.tomcat.session.util;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class EnumUtil
{
	public static <T> T[] toArray(Class<T> cls, Enumeration<T> items)
	{
		List<T> list = new ArrayList<T>(32);
		while (items.hasMoreElements()) {
			list.add(items.nextElement());
		}
		@SuppressWarnings("unchecked")
		T[] array = (T[])Array.newInstance(cls, list.size());
		return list.toArray(array);
	}
}
