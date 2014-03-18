package com.sun.identity.common;

import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
import com.sun.identity.shared.ldap.LDAPSearchConstraints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class LDAPConnectionPool {
	@Override
	public String toString() {super.toString();
		return MessageFormat.format("{0} name={1} server={2} failover={3} {4}/{5}/{6}", Integer.toHexString(hashCode()),name,server,servers,pool.size(), busy.size(),maxSize);
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
		int idleTimeInSecs = 60;
		if (idleStr != null && idleStr.length() > 0) {
			try {
				idleTimeInSecs = Integer.parseInt(idleStr);
			} catch (NumberFormatException nex) {
				logger.error("pool: " + poolName + ": Cannot parse idle time: " + idleStr + " Connection reaping is disabled.");
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
	 @SuppressWarnings("rawtypes")
	private Map connOptions;
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

	volatile Boolean stop=false;
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
		this.connOptions=connOptions;
		this.idleTime = idleTimeInSecs * 1000;
		//		createPool();
		if (minSize <= 0)
			throw new LDAPException("init pool:" + name + ":ConnectionPoolSize invalid");
		if (maxSize < minSize) {
			logger.error("init pool:" + name + ":ConnectionPoolMax is invalid, set to " + minSize);
			maxSize = minSize;
		}
		pool = new ArrayBlockingQueue<LDAPConnectionWithTime>(maxSize);
		busy = new ConcurrentHashMap<LDAPConnection, Long>(maxSize);
		if (ldc!=null && ldc.isConnected())
			pool.add(new LDAPConnectionWithTime(ldc));

		new Thread("create-"+MessageFormat.format("{0} name={1} server={2} failover={3}", Integer.toHexString(hashCode()),name,server,servers)){
			@Override
			public void run() {
				logger.info("create: " + this);
				while (!stop)
					try{
						if (pool.size()<minSize && busy.size()<maxSize)
							try{
								pool.add(new LDAPConnectionWithTime(createConnection()));
								continue;
							}catch (LDAPException e) {
								nextServer();
							}
						else
							sleep(10);
					}catch(Throwable e){}
			}
		}.start();

		if (idleTime>0){
			new Thread("clean-"+MessageFormat.format("{0} name={1} server={2} failover={3}", Integer.toHexString(hashCode()),name,server,servers)){
				@Override
				public void run() {
					while (!stop || busy.size()>0 || pool.size()>0)
						try{
							sleep(idleTime);
							for (Entry<LDAPConnection, Long> e : busy.entrySet())
								if (System.currentTimeMillis()-e.getValue()>idleTime*5){ //return leak too pool
									logger.warn("remove busy leak {}: {} borrow {}",LDAPConnectionPool.this,e.getKey(),new Date(e.getValue()));
									close(e.getKey());
								}
							if (pool.size()>minSize)
								for (LDAPConnectionWithTime c : pool)
									if (System.currentTimeMillis()-c.time>idleTime && pool.size()>minSize){
										pool.remove(c);
										if (!busy.containsKey(c) && c.con.isConnected())
											try{
												c.con.disconnect();
												logger.info("remove unused {}: {} {}",LDAPConnectionPool.this,c.con,new Date(c.time));
											}catch (LDAPException e) {
												logger.error("remove unused {}: {} {}",LDAPConnectionPool.this,c.con,new Date(c.time),e);
											}
									}
						}catch(InterruptedException e){
							stop=true;
						}
					}
			}.start();
		}
	}

	private LDAPConnection createConnection() throws LDAPException {
		long start = System.currentTimeMillis();
		final LDAPConnection newConn = new LDAPConnection();
		if (connOptions!=null){
			Object value=null;
			value=connOptions.get("maxbacklog");
			if (value!=null)
				newConn.setOption(LDAPConnection.MAXBACKLOG, (Integer)value);
			value=connOptions.get("referrals");
			if (value!=null)
				newConn.setOption(LDAPConnection.REFERRALS, (Boolean)value);
			value=connOptions.get("searchconstraints");
			if (value!=null)
				newConn.setSearchConstraints((LDAPSearchConstraints)value);
		}
		//String key = name + ":" + server.host + ":" + server.port + ":" + authdn;
		try {
			newConn.connect(3, server.host, server.port, authdn, authpw);
			logger.info("createConnection-v3 {}ms {}: {}",System.currentTimeMillis() - start,this, newConn);
		} catch (LDAPException connEx) {
			if (connEx.getLDAPResultCode() == LDAPException.PROTOCOL_ERROR) {// fallback to ldap v2 if v3 is not supported
				newConn.connect(2, server.host, server.port, authdn, authpw);
				logger.info("createConnection-v2 {}ms {}: {}",System.currentTimeMillis() - start,this, newConn);
			} else {
				logger.error("createConnection {}ms {}: {}",System.currentTimeMillis() - start, this,  connEx.getMessage());
				throw connEx;
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
		LDAPConnection res=null;
		long start = System.currentTimeMillis();
		try{
			while (!stop && (timeout<=0 || System.currentTimeMillis()-start<timeout*1000))
				try {
					res=poll(timeout<=0?1000:timeout*1000,TimeUnit.MILLISECONDS);
					if (res!=null)
						return res;
				} catch (InterruptedException e) {}
		}finally{
			if (res != null)
				busy.put(res, System.currentTimeMillis());
			if (res == null)
				logger.warn("getConnection {}ms {}: {}", System.currentTimeMillis() - start,this, res);
			else if (System.currentTimeMillis()-start>2*1000)
				logger.warn("getConnection {}ms {}: {}", System.currentTimeMillis() - start,this, res);
			else if (logger.isDebugEnabled())
				logger.debug("getConnection {}ms {}: {}",System.currentTimeMillis() - start,this, res);
		}
		return null;
	}

	public void close(LDAPConnection ld) {
		long start = System.currentTimeMillis();
		Long startUsage=busy.remove(ld);
		if (ld.isConnected()) {
			pool.add(new LDAPConnectionWithTime(ld));
			if (logger.isDebugEnabled())
				logger.debug("close {}ms {} with return: {} ",System.currentTimeMillis() - start,this,ld);
		}else
			logger.warn("close {}ms {} as disconnected: {} ",System.currentTimeMillis() - start,this,ld);
		if (startUsage!=null&&(start-startUsage)>5*1000)
			logger.warn("close busy usage {}ms {}: {}", start-startUsage,this,ld);
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
			logger.error("close {}ms {} {}: {}",System.currentTimeMillis() - start, this, ld, errCode);
			try {
				if (ld.isConnected())
					ld.disconnect();
			} catch (LDAPException e) {
				busy.remove(ld);
				logger.warn("close {}ms {} disconnect {}: {}",System.currentTimeMillis() - start,this, ld,e.getMessage());
			}
		}
		close(ld);
	}

	public void destroy(){
		logger.warn("destroy {}",this);
		stop=true;
		while (pool.size()>0||busy.size()>0){
			LDAPConnection con=poll();
			try{
				if (con.isConnected())
					con.disconnect();
			}catch(LDAPException e){}
		}
	}

	public boolean fallBack(LDAPConnection con) {
		logger.warn("fallBack {}: {}",this,con);
		return false;
	}
}
