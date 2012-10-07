package com.iplanet.am.util;

import java.util.concurrent.ConcurrentHashMap;

public class ClassCache {
	final static ConcurrentHashMap<String, Class> cache=new ConcurrentHashMap<String, Class>();
	public static Class forName(String name) throws ClassNotFoundException{
		Class res=cache.get(name);
		if (res==null){
			synchronized (name) {
				res=cache.get(name);
				if (res==null){
					res=Class.forName(name);
					cache.put(name, res);
				}
			}
		}
		return res;
	}

	public static Class forName(String name,boolean a,ClassLoader loader) throws ClassNotFoundException{
		Class res=cache.get(name);
		if (res==null){
			synchronized (name) {
				res=cache.get(name);
				if (res==null){
					res=Class.forName(name,a,loader);
					cache.put(name, res);
				}
			}
		}
		return res;
	}
}
