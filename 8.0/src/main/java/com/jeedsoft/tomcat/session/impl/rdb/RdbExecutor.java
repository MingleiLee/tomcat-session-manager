package com.jeedsoft.tomcat.session.impl.rdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.tomcat.jdbc.pool.DataSource;

import com.jeedsoft.tomcat.session.impl.rdb.builder.Builder;
import com.jeedsoft.tomcat.session.impl.rdb.builder.Field;
import com.jeedsoft.tomcat.session.util.Closer;

/**
 * A static executor based on relational database's datasource 
 * @author Minglei Lee
 */
public class RdbExecutor
{
	public static int execute(DataSource ds, String sql, Object... args)
	{
		Connection cn = null;
		PreparedStatement stmt = null;
		try {
			stmt = prepare(cn, sql, args);
			stmt.execute();
			return stmt.getUpdateCount();
		}
		catch (SQLException e) {
			throw new RdbException(sql, args);
		}
		finally {
			Closer.close(cn, stmt);
		}
	}
	
	public static void execute(Connection cn, Builder builder)
	{
		String sql = builder.getSql();
		Field[] fields = builder.getParameters();
		PreparedStatement stmt = null;
		try {
			stmt = cn.prepareStatement(builder.getSql());
			int index = 0;
			for (Field field: fields) {
				setParameter(stmt, ++index, field.sqlType, field.value);
			}
			stmt.execute();
		}
		catch (SQLException e) {
			throw new RdbException(sql, fields);
		}
		finally {
			Closer.close(stmt);
		}
	}

	public static long getLong(DataSource ds, String sql, Object... args)
	{
		Connection cn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			stmt = prepare(cn, sql, args);
			rs = stmt.executeQuery();
			return rs.next() ? rs.getLong(1) : 0;
		}
		catch (SQLException e) {
			throw new RdbException(e);
		}
		finally {
			Closer.close(cn, stmt, rs);
		}
	}

	private static PreparedStatement prepare(Connection cn, String sql, Object... args) throws SQLException
	{
		PreparedStatement stmt = cn.prepareStatement(sql);
		for (int i = 0; i < args.length; ++i) {
			setParameter(stmt, i + 1, args[i]);
		}
		return stmt;
	}
	
	private static void setParameter(PreparedStatement stmt, int index, Object value) throws SQLException
	{
		if (value instanceof String) {
			stmt.setString(index, (String)value);
		}
		else if (value instanceof Long) {
			stmt.setLong(index, (Long)value);
		}
		else if (value instanceof Integer) {
			stmt.setInt(index, (Integer)value);
		}
		else {
			throw new RdbException("unsupported type: " + (value == null ? null : value.getClass()));
		}
	}

	private static void setParameter(PreparedStatement stmt, int index, int sqlType, Object value) throws SQLException
	{
		if (value == null) {
			stmt.setNull(index, sqlType);
		}
		else if (sqlType == Types.VARCHAR) {
			stmt.setString(index, (String)value);
		}
		else if (sqlType == Types.BIGINT) {
			stmt.setLong(index, (Long)value);
		}
		else if (sqlType == Types.INTEGER) {
			stmt.setInt(index, (Integer)value);
		}
		else {
			throw new RdbException("unsupported sql type: " + sqlType);
		}
	}
}
