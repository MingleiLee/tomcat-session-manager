package com.jeedsoft.tomcat.session.util;

import java.util.ArrayList;
import java.util.List;

public class ListUtil
{
	public static <T> List<T> toList(T[] array)
	{
		List<T> list = new ArrayList<>(array.length);
		for (T item: array) {
			list.add(item);
		}
		return list;
	}
	
	public static <T> List<T> toList(Iterable<T> items)
	{
		List<T> list = new ArrayList<>();
		for (T item: items) {
			list.add(item);
		}
		return list;
	}
}
