package com.jeedsoft.tomcat.session.util;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.DataSource;

import com.jeedsoft.tomcat.session.impl.rdb.RdbStore;

public class Closer
{
	private static final Log log = LogFactory.getLog(RdbStore.class);
	
	public static void close(Closeable... items)
	{
		for (Closeable item: items) {
			try {
				if (item != null) {
					item.close();
				}
			}
			catch (Throwable e) {
				log.error("failed to close.", e);
			}
		}
	}

	public static void close(DataSource ds)
	{
		try {
			if (ds != null) {
				ds.close();
			}
		}
		catch (Throwable e) {
			log.error("failed to close.", e);
		}
	}

	public static void close(Connection cn)
	{
		try {
			if (cn != null) {
				cn.close();
			}
		}
		catch (Throwable e) {
			log.error("failed to close.", e);
		}
	}

	public static void close(Statement stmt)
	{
		try {
			if (stmt != null) {
				stmt.close();
			}
		}
		catch (Throwable e) {
			log.error("failed to close.", e);
		}
	}

	public static void close(ResultSet rs)
	{
		try {
			if (rs != null) {
				rs.close();
			}
		}
		catch (Throwable e) {
			log.error("failed to close.", e);
		}
	}
	
	public static void close(Connection cn, Statement stmt)
	{
		close(stmt);
		close(cn);
	}
	
	public static void close(Statement stmt, ResultSet rs)
	{
		close(rs);
		close(stmt);
	}
	
	public static void close(Connection cn, Statement stmt, ResultSet rs)
	{
		close(rs);
		close(stmt);
		close(cn);
	}
}
