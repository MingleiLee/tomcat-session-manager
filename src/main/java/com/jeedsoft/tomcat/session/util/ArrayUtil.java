package com.jeedsoft.tomcat.session.util;

import java.lang.reflect.Array;
import java.util.Collection;

public class ArrayUtil
{
	@SuppressWarnings("unchecked")
	public static <T> T[] toArray(Class<T> elementClass, Iterable<T> elements)
	{
		if (elements == null) {
			return null;
		}
		if (!(elements instanceof Collection<?>)) {
			elements = ListUtil.toList(elements);
		}
		Collection<T> collection = (Collection<T>)elements;
		T[] array = (T[])Array.newInstance(elementClass, collection.size());
		return collection.toArray(array);
	}
	
	public static String[] toStringArray(Iterable<String> elements)
	{
		return toArray(String.class, elements);
	}
}
