package com.jeedsoft.tomcat.session.type;

/**
 * check-and-save result
 * @author Minglei Lee
 */
public class CasResult<T>
{
	private boolean success;
	
	private T value;

	public CasResult(boolean success, T value)
	{
		this.success = success;
		this.value = value;
	}

	public boolean isSuccess()
	{
		return success;
	}

	public T getValue()
	{
		return value;
	}
	
	public String toString()
	{
		return success + ", " + value;
	}
}
