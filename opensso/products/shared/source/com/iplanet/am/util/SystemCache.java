package com.iplanet.am.util;

import com.iplanet.am.util.Cache;

public class SystemCache  {
	final static Cache<String, String> cache=new Cache<String, String>(System.class.getName());

    public static String getProperty(String key) {
	String value=cache.get(key);
	if (value==null){
		synchronized (System.class.getName()+"."+value) {
			value=cache.get(key);
			if (value==null){
				value=System.getProperty(key);
				cache.put(key, value==null?"":value);
			}
			}
		}
	return "".equals(value)?null:value;
    }

    public static String getProperty(String key, String def) {
	String value=getProperty(key);
	return (value==null)?def:value;
    }
}
