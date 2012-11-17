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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

public class Cache<K, V>   {
	final static Logger logger = LoggerFactory.getLogger(Cache.class);
	static boolean debug=logger.isDebugEnabled();
	//static CacheManager cacheManager=new CacheManager();
	static CacheManager cacheManager=CacheManager.getCacheManager(null);
	String cacheName;
	static{
		if (cacheManager==null)
			cacheManager=CacheManager.getInstance();
	}
	static Stat stat=new Stat();
	static class Stat extends Thread{

		public Stat() {
			super(Cache.class.getName()+"-stat");
			setPriority(MIN_PRIORITY);
			start();
		}

		@Override
		public void run() {
			super.run();
				while (true){
					try{
						sleep(20*60*1000);
						logger.info("stat -------------------------------------------------------------------------------");
						for (String name : cacheManager.getCacheNames()) {
							net.sf.ehcache.Cache cache=cacheManager.getCache(name);
							if (cache!=null){
								logger.info("stat ============= {}",new Object[]{cache.getStatistics()});
							}
						}
						sleep(50*60*1000);
					}catch (Exception e) {
						logger.error("statistic",e);
					}
				}
		}
	}

	public Cache(String cacheName) {
		this.cacheName=cacheName;
		Ehcache cache=cacheManager.getEhcache(cacheName);
		if (cache==null){
			synchronized (cacheName) {
				cache=cacheManager.getEhcache(cacheName);
				if (cache==null){
				try{
					cacheManager.addCache(cacheName);
				}catch(net.sf.ehcache.ObjectExistsExceprion e){}
				cache=cacheManager.getEhcache(cacheName);
				logger.warn("not found ({})",cache);
			}else if (cache.getKeysNoDuplicateCheck().size()>0)
				logger.warn("re-found ({}) with {} values {}",new Object[]{cache,cache.getKeysNoDuplicateCheck().size()});
			else
				logger.info("found ({})",cache);
			}
    }

	public Cache(String name,int maxEntriesInMemory) {
		this(name);
		cacheManager.getCache(cacheName).getCacheConfiguration().setMaxEntriesLocalHeap(maxEntriesInMemory);
    }

    @SuppressWarnings("unchecked")
	public V get(K key) {
	Element e=cacheManager.getEhcache(cacheName).get(key);
	if (debug)
		logger.debug("[{}] [{}]=[{}]",new Object[]{cacheName,key,(e==null)?null:(V)e.getObjectValue()});
	return (e==null)?null:(V)e.getObjectValue();
    }

    public void put(K key, V value) {
	if (debug)
		logger.debug("put [{}] [{}]=[{}]",new Object[]{cacheName,key,value});
	get().put(new Element(key, value));
    }

    public void remove(K key) {
	get().remove(key);
    }

    public void clear() {
	get().removeAll();
    }

    public Ehcache get() {
		return cacheManager.getEhcache(cacheName);
	}

    public void removeCache(){
	if (cacheManager.getCache(cacheName)!=null){
		logger.info("remove ({})",cacheName);
		cacheManager.removeCache(cacheName);
	}
    }

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		//removeCache();
	}
}