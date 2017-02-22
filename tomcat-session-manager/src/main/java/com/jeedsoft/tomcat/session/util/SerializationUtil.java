package com.jeedsoft.tomcat.session.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.catalina.util.CustomObjectInputStream;

public class SerializationUtil
{
	public static byte[] serialize(Object object)
	{
		try (
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
		) {
			oos.writeObject(object);
			oos.flush();
			return baos.toByteArray();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T deserialize(byte[] bytes)
	{
		try (
			ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
			//ObjectInputStream ois = new ObjectInputStream(bais);
			ObjectInputStream ois = new CustomObjectInputStream(bais, Thread.currentThread().getContextClassLoader());
		) {
			//return (T)ois.readObject();
			T t = (T)ois.readObject();
			return t;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T deserialize(byte[] bytes, ClassLoader loader)
	{
		try (
			ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
			ObjectInputStream ois = new CustomObjectInputStream(bais, loader);
		) {
			return (T)ois.readObject();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
