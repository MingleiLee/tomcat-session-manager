package com.jeedsoft.tomcat.session;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

public class FastStoreSessionValve extends ValveBase
{
	private FastStoreSessionManager manager;

	public void setSessionManager(FastStoreSessionManager manager)
	{
		this.manager = manager;
	}

	@Override
	public void invoke(Request request, Response response) throws IOException, ServletException
	{
		try {
			getNext().invoke(request, response);
		}
		finally {
			manager.afterRequest();
		}
	}
}
