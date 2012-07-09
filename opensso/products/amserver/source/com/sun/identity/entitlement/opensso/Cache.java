/**
 * Copyright [2012] [vharseko@openam.org.ru]
 */

package com.sun.identity.entitlement.opensso;

public class Cache<K, V> extends com.iplanet.am.util.Cache<K, V>{

	public Cache(String cacheName) {
		super(cacheName);
	}

	public Cache(String name, int maxEntriesInMemory) {
		super(name, maxEntriesInMemory);
	}
}
