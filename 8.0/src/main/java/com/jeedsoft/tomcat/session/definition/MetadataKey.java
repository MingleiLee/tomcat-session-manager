package com.jeedsoft.tomcat.session.definition;

import java.util.HashSet;
import java.util.Set;

public class MetadataKey
{
	//required by javax.servlet.http.HttpSession
	public static final String CREATION_TIME			= "CreateTime";
	public static final String LAST_ACCESSED_TIME		= "LastAccessTime";
	public static final String MAX_INACTIVE_INTERVAL	= "MaxInactive";

	//required by org.apache.catalina.Session
	public static final String AUTH_TYPE				= "AuthType";
	public static final String PRINCIPAL				= "Principal";
	public static final String THIS_ACCESSED_TIME		= "ThisAccessTime";
	public static final String EXPIRE_TIME				= "ExpireTime";
	
	//type map
	private static final Set<String> VALID_KEYS	= new HashSet<>();
	
	static
	{
		VALID_KEYS.add(CREATION_TIME);
		VALID_KEYS.add(LAST_ACCESSED_TIME);
		VALID_KEYS.add(MAX_INACTIVE_INTERVAL);
		VALID_KEYS.add(AUTH_TYPE);
		VALID_KEYS.add(PRINCIPAL);
		VALID_KEYS.add(THIS_ACCESSED_TIME);
		VALID_KEYS.add(EXPIRE_TIME);
	}
	
	public static boolean isValidKey(String key)
	{
		return VALID_KEYS.contains(key);
	}
}
