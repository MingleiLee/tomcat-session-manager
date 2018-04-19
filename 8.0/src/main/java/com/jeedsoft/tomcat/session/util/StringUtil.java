package com.jeedsoft.tomcat.session.util;

public class StringUtil
{
	public static boolean isEmpty(String s)
	{
		return s == null || s.trim().length() == 0;
	}

	public static String join(Object[] items)
	{
		return join(items, ", ");
	}
	
	public static String join(Object[] items, String joiner)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < items.length; ++i) {
			if (i > 0) {
				sb.append(joiner);
			}
			sb.append(items[i]);
		}
		return sb.toString();
	}
	
	public static String join(Iterable<?> items)
	{
		return join(items, ", ");
	}

	public static String join(Iterable<?> items, String joiner)
	{
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (Object item: items) {
			if (isFirst) {
				isFirst = false;
			}
			else {
				sb.append(joiner);
			}
			sb.append(item);
		}
		return sb.toString();
	}

	public static String join(String item, int count)
	{
		return join(item, count, ", ");
	}

	public static String join(String item, int count, String joiner)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; ++i) {
			if (i > 0) {
				sb.append(joiner);
			}
			sb.append(item);
		}
		return sb.toString();
	}
}
