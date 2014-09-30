/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2005 Sun Microsystems Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * https://opensso.dev.java.net/public/CDDLv1.0.html or
 * opensso/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at opensso/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * $Id: Cache.java,v 1.4 2008/06/27 20:56:21 arviranga Exp $
 *
 */

/**
 * Portions Copyrighted [2011] [ForgeRock AS]
 */
/**
 * Portions Copyrighted [2012] [vharseko@openam.org.ru]
 */
package com.iplanet.am.util;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

public class Cache<K, V>   {
	final static Logger logger = LoggerFactory.getLogger(Cache.class);
	static boolean debug=logger.isDebugEnabled();
	static CacheManager cacheManager=CacheManager.getCacheManager(null);
	String cacheName;
	static{
		if (cacheManager==null)
			cacheManager=CacheManager.getInstance();
	}
	
	static ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
	protected class Cleaner implements Runnable{
		public void run() {
			if (cache!=null){
				logger.info("stat: {}",new Object[]{cache.getStatistics()});
				cache.evictExpiredElements();
			}
		}
	}

	Ehcache cache;

	public Cache(){
		this(java.util.UUID.randomUUID().toString());
	}

	public Cache(String cacheName) {
		this.cacheName=cacheName;
		cache=cacheManager.getEhcache(cacheName);
		if (cache==null)
			synchronized (cacheName) {
				cache=cacheManager.getEhcache(cacheName);
				if (cache==null){
					try{
						cacheManager.addCache(cacheName);
					}catch(net.sf.ehcache.ObjectExistsException e){}
					cache=cacheManager.getEhcache(cacheName);
					logger.warn("not found ({})",cache);
				}else
					logger.info("found ({})",cache);
			}
		Long period=Math.max(cache.getCacheConfiguration().getTimeToIdleSeconds(), cache.getCacheConfiguration().getTimeToIdleSeconds());
		period=Math.min(3*60*60, period); //maximum clean interval
		period=Math.max(20*60, period); //minimum clean interval
		exec.scheduleWithFixedDelay(new Cleaner(),new Random().nextLong() * (period*2-period) + period/2, period, TimeUnit.SECONDS);
    }

	public Cache(String name,int maxEntriesInMemory) {
		this(name);
		//cache.getCacheConfiguration().setMaxEntriesLocalHeap(maxEntriesInMemory);
    }

    @SuppressWarnings("unchecked")
	public V get(K key) {
    	Element e=cache.get(key);
		if (debug)
			logger.debug("get [{}] [{}]=[{}]",new Object[]{cacheName,key,(e==null)?null:(V)e.getObjectValue()});
		return (e==null||e.isExpired())?null:(V)e.getObjectValue();
    }

    public void put(K key, V value) {
		if (debug)
			logger.debug("put [{}] [{}]=[{}]",new Object[]{cacheName,key,value});
		cache.put(new Element(key, value));
    }

    public void remove(K key) {
		V value=get(key);
		if (value!=null){
			cache.remove(key);
			if (debug)
				logger.debug("remove [{}] [{}]=[{}]",new Object[]{cacheName,key,value});
		}
    }

    public void clear() {
    	cache.removeAll();
    }

    public Ehcache get() {
		return cache;
	}

    public void removeCache(){
    	clear();
    	cache=null;
    }

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		removeCache();
	}
}
