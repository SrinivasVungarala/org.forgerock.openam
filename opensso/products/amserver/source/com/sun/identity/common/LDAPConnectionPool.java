package com.sun.identity.common;

import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

import com.iplanet.am.util.SystemProperties;
import com.iplanet.services.ldap.DSConfigMgr;
import com.sun.identity.shared.Constants;
import com.sun.identity.shared.ldap.LDAPConnection;
import com.sun.identity.shared.ldap.LDAPException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class LDAPConnectionPool {
	@Override
	public String toString() {
		return MessageFormat.format("{0} {1} {2} {3}/{4}/{5}", name,server,servers,pool.size(), busy.size(),maxSize);
	}

	final static Logger logger = LoggerFactory.getLogger(LDAPConnectionPool.class);

	public LDAPConnectionPool(String name, int min, int max, String host, int port, String authdn, String authpw) throws LDAPException {
		this(name, min, max, host, port, authdn, authpw, null, null);
	}

	public LDAPConnectionPool(String name, int min, int max, String host, int port) throws LDAPException {
		this(name, min, max, host, port, "", "");
	}

	public LDAPConnectionPool(String name, int min, int max, String host, int port, String authdn, String authpw, @SuppressWarnings("rawtypes") HashMap connOptions) throws LDAPException {
		this(name, min, max, host, port, authdn, authpw, null, connOptions);
	}

	public LDAPConnectionPool(String name, String host, int port) throws LDAPException {
		this(name, 10, 20, host, port, "", "", null);
	}

	public LDAPConnectionPool(String name, int min, int max, LDAPConnection ldc) throws LDAPException {
		this(name, min, max, ldc.getHost(), ldc.getPort(), ldc.getAuthenticationDN(), ldc.getAuthenticationPassword(), ldc, null);
	}

	public LDAPConnectionPool(String name, int min, int max, String host, int port, String authdn, String authpw, LDAPConnection ldc, @SuppressWarnings("rawtypes") HashMap connOptions)
			throws LDAPException {
		this(name, min, max, host, port, authdn, authpw, ldc, getIdleTime(name), connOptions);
	}

	private static int getIdleTime(String poolName) {
		String idleStr = SystemProperties.get(Constants.LDAP_CONN_IDLE_TIME_IN_SECS);
		int idleTimeInSecs = 120;
		if (idleStr != null && idleStr.length() > 0) {
			try {
				idleTimeInSecs = Integer.parseInt(idleStr);
			} catch (NumberFormatException nex) {
				logger.error("LDAPConnection pool: " + poolName + ": Cannot parse idle time: " + idleStr + " Connection reaping is disabled.");
			}
		}
		return idleTimeInSecs;
	}

	private String name; // name of connection pool;
	private int minSize; // Min pool size
	private int maxSize; // Max pool size
	private Server server=null;
	private String authdn; // Identity of connections
	private String authpw; // Password for authdn
	// @SuppressWarnings("rawtypes")
	// private Map connOptions;
	private LDAPConnection ldc = null; // Connection to clone
	final long idleTime; // idle time in milli seconds

	public class Server{
		@Override
		public String toString() {
			return MessageFormat.format("{0}:{1}:{2}", host,port,type);
		}
		public String host="localhost";
		public int port=389;
		public String type=DSConfigMgr.VAL_STYPE_SIMPLE;
		public Server(String host, int port,String type){
			this.host = host;
			this.port = port;
			this.type = type;
		}
		public Server(String host,String type){
			String[] hpNameParts = host.split(":");
			this.host = (hpNameParts.length > 0) ? hpNameParts[0] : "";
			this.port = ((hpNameParts.length > 1) && (hpNameParts[1].matches("([0-9]+?)+"))) ? Integer.valueOf(hpNameParts[1]).intValue() : 389;
			this.type=type;
		}
	}
	LinkedTransferQueue<Server> servers=new LinkedTransferQueue<Server>();

	private void createHostList(String hostNameFromConfig, LDAPConnection ldc) {
		StringTokenizer st = new StringTokenizer(hostNameFromConfig);
		while (st.hasMoreElements())
			if (ldc.getSocketFactory() != null)
				servers.add(new Server(st.nextToken(), DSConfigMgr.VAL_STYPE_SSL));
			else
				servers.add(new Server(st.nextToken(), DSConfigMgr.VAL_STYPE_SIMPLE));
		logger.info("createHostList: {}",servers);
	}

	public synchronized void nextServer(){
		server=servers.poll();
		servers.put(server);
		logger.info("set active: {}",server);
	}
	public class LDAPConnectionWithTime{
		public LDAPConnection con;
		public long time=System.currentTimeMillis();
		public LDAPConnectionWithTime(LDAPConnection con){
			this.con=con;
		}
		@Override
		public String toString() {
			return MessageFormat.format("{0} {1}", con,new Date(time));
		}
	}

	ArrayBlockingQueue<LDAPConnectionWithTime> pool = null;
	ConcurrentHashMap<LDAPConnection, Long> busy = null;
	private LDAPConnectionPool(String name, int min, int max, String host, int port, String authdn, String authpw, LDAPConnection ldc, int idleTimeInSecs,@SuppressWarnings("rawtypes") HashMap connOptions) throws LDAPException {
		this.name = name;
		this.minSize = min;
		this.maxSize = max;
		if (connOptions != null)
			createHostList(host, ldc);
		else
			servers.put(new Server(host,port,DSConfigMgr.VAL_STYPE_SIMPLE));
		nextServer();
		this.authdn = authdn;
		this.authpw = authpw;
		this.ldc = ldc;
		this.idleTime = idleTimeInSecs * 1000;
		//		createPool();
		if (minSize <= 0)
			throw new LDAPException("LDAPConnection pool:" + name + ":ConnectionPoolSize invalid");
		if (maxSize < minSize) {
			logger.error("LDAPConnection pool:" + name + ":ConnectionPoolMax is invalid, set to " + minSize);
			maxSize = minSize;
		}
		pool = new ArrayBlockingQueue<LDAPConnectionWithTime>(maxSize);
		busy = new ConcurrentHashMap<LDAPConnection, Long>(maxSize);
		logger.info("createPool: " + this);
		for (int i = 0; i < minSize; i++) {
			LDAPConnection con = createConnection();
			pool.add(new LDAPConnectionWithTime(con));
		}
		if (idleTime>0){
			new Thread("clean-idle-"+this){
				@Override
				public void run() {
					boolean stop=false;
					while (!stop){
						try{
							sleep(idleTime);
							for (Entry<LDAPConnection, Long> e : busy.entrySet())
								if (System.currentTimeMillis()-e.getValue()>idleTime/2){ //return leak too pool
									close(e.getKey());
									logger.warn("clean remove leak {}: {} {}",this,e.getKey(),new Date(e.getValue()));
								}
							for (LDAPConnectionWithTime c : pool)
								if (System.currentTimeMillis()-c.time>idleTime){
									pool.remove(c);
									if (c.con.isConnected())
										try{
											c.con.disconnect();
											logger.info("clean remove unused {}: {} {}",this,c.con,new Date(c.time));
										}catch (LDAPException e) {
											logger.error("clean remove unused {}: {} {}",this,c.con,new Date(c.time),e);
										}
								}
						}catch(InterruptedException e){
							stop=true;
						}
					}
				}
			}.start();
		}
	}

	private LDAPConnection createConnection() throws LDAPException {
		long start = System.currentTimeMillis();
		// Make LDAP connection, using template if available
		LDAPConnection newConn = (ldc != null) ? (LDAPConnection) ldc.clone() : new LDAPConnection();
		//String key = name + ":" + server.host + ":" + server.port + ":" + authdn;
		if (newConn.isConnected()) {// If using a template, then reconnect to create a separate physical connection NPCTE fix for bugId esc 1-15977888, March-2006
			newConn.cloneConnectionManager();
			newConn.reconnect();
			logger.info("createConnection {} {}ms with template primary {}",this, System.currentTimeMillis() - start, server);
		} else {// Not using a template, so connect with simpleauthentication using ldap v3
			try {
				newConn.connect(3, server.host, server.port, authdn, authpw);
				logger.info("createConnection {} {}ms with no template primary {}",this, System.currentTimeMillis() - start,server);
			} catch (LDAPException connEx) {
				if (connEx.getLDAPResultCode() == LDAPException.PROTOCOL_ERROR) {// fallback to ldap v2 if v3 is not supported
					newConn.connect(2, server.host, server.port, authdn, authpw);
					logger.info("createConnection {} {}ms with no template primary v2 {}",this, System.currentTimeMillis() - start, server);
				} else {
					close(ldc, connEx.getLDAPResultCode());
					throw connEx;
				}
			}
		}
		return newConn;
	}

	public LDAPConnection poll() {
		LDAPConnectionWithTime res=pool.poll();
		return res==null?null:res.con;
	}

	public LDAPConnection poll(long t,TimeUnit tu) throws InterruptedException {
		LDAPConnectionWithTime res=pool.poll(t,tu);
		return res==null?null:res.con;
	}

	public LDAPConnection getConnection() {
		return getConnection(0);
	}

	public LDAPConnection getConnection(int timeout) {
		while (true){
			long start = System.currentTimeMillis();
			LDAPConnection res = poll();
			if (res == null && busy.size() < maxSize)
				try {
					res = createConnection();
				} catch (LDAPException e) {
					nextServer();
					logger.error("getConnection {} {}ms: {}",this, System.currentTimeMillis() - start, e.getMessage());
					continue;
				}
			if (res == null)// wait
				try {
					res = poll(timeout, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {}
			if (res != null)
				busy.put(res, System.currentTimeMillis());
			if (res == null)
				logger.warn("getConnection {} {}ms: {} timeout {}ms {}/{}/{}", this,System.currentTimeMillis() - start, res, timeout, pool.size(), busy.size(),maxSize);
			else if (logger.isDebugEnabled())
				logger.debug("getConnection {} {}ms: {} timeout {}ms {}/{}/{}",this, System.currentTimeMillis() - start, res, timeout, pool.size(), busy.size(),maxSize);
			return res;
		}
	}

	public void close(LDAPConnection ld) {
		long start = System.currentTimeMillis();
		busy.remove(ld);
		if (ld.isConnected()) {
			pool.add(new LDAPConnectionWithTime(ld));
			if (logger.isDebugEnabled())
				logger.debug("close with return {} {}ms: {} ",this, System.currentTimeMillis() - start, ld);
		}else
			logger.warn("close as disconnected {} {}ms: {} ",this, System.currentTimeMillis() - start, ld);
	}

	private static Set<String> retryErrorCodes = new HashSet<String>();
	static {
		String retryErrs = SystemProperties.get("com.iplanet.am.ldap.connection.ldap.error.codes.retries");
		if (retryErrs != null) {
			logger.info("com.iplanet.am.ldap.connection.ldap.error.codes.retries={}", retryErrs);
			StringTokenizer stz = new StringTokenizer(retryErrs, ",");
			while (stz.hasMoreTokens())
				retryErrorCodes.add(stz.nextToken().trim());
		}
	}

	public void close(LDAPConnection ld, int errCode) {
		long start = System.currentTimeMillis();
		if (retryErrorCodes.contains(Integer.toString(errCode)) || (errCode == LDAPException.UNWILLING_TO_PERFORM) ) {
			logger.error("close {} {} by error {}ms: {}",this, ld, System.currentTimeMillis() - start,errCode);
			try {
				if (ld.isConnected())
					ld.disconnect();
			} catch (LDAPException e) {
				busy.remove(ld);
				logger.warn("close {} {} by error disconnect {}ms: {}",this, ld,System.currentTimeMillis() - start,e.getMessage());
			}
		}
		close(ld);
	}

	public void destroy(){
		while (pool.size()>0||busy.size()>0){
			LDAPConnection con=poll();
			try{
				if (con.isConnected())
					con.disconnect();
			}catch(LDAPException e){}
			pool.remove(con);
		}
	}

	public boolean fallBack(LDAPConnection con) {
		logger.warn("fallBack:{} {} with {}",this,con,servers);
		return false;
	}
}
