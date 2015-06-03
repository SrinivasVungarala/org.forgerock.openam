/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html See the License for the specific
 * language governing permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://forgerock.org/license/CDDLv1.0.html If
 * applicable, add the following below the CDDL Header, with the fields enclosed
 * by brackets [] replaced by your own identifying information: "Portions
 * Copyrighted [year] [name of copyright owner]"
 *
 */
package org.forgerock.openam.session.service;

import com.iplanet.dpro.session.Session;
import com.iplanet.dpro.session.SessionException;
import com.iplanet.dpro.session.SessionID;
import com.iplanet.dpro.session.service.InternalSession;
import com.iplanet.dpro.session.service.QuotaExhaustionAction;
import com.iplanet.dpro.session.service.SessionService;
import com.sun.identity.shared.debug.Debug;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This action retrieves all the sessions using the Session service and
 * refreshes them in the local cache (so they have up-to-date session expiration
 * information). The session with the lowest max session time will be destroyed.
 *
 * @author Peter Major
 */
public class DestroyOldestAction implements QuotaExhaustionAction {

    private static Debug debug = SessionService.sessionDebug;

    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map)
	{
	    List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>(map.entrySet());
	    Collections.sort( list, new Comparator<Map.Entry<K, V>>()
	    {
	        public int compare( Map.Entry<K, V> o1, Map.Entry<K, V> o2 )
	        {
	            return (o2.getValue()).compareTo( o1.getValue() );
	        }
	    } );
	
	    Map<K, V> result = new LinkedHashMap<K, V>(list.size());
	    for (Map.Entry<K, V> entry : list)
	        result.put( entry.getKey(), entry.getValue() );
	    return result;
	}
	
    @Override
    public boolean action(InternalSession is, Map<String, Long> sessions) {
    	final Integer quota=(Integer)is.getObject("quota");
    	
    	if (sessions.size()<quota)
    		return false;
    	
    	final Map<String, Long> sessions2=new HashMap<String, Long>(sessions.size());
    	for (Entry<String, Long> entry : sessions.entrySet()) 
    		try{
	    		Session session = Session.getSession(new SessionID(entry.getKey()));
	            session.refresh(false);
	            sessions2.put(entry.getKey(), session.getTimeLeft());
			}catch (SessionException e) {}

    	final Map<String, Long> ordered=sortByValue(sessions2);
    	while (ordered.size()>=quota){
    		 SessionID sessID = new SessionID(ordered.keySet().iterator().next());
             try {
                 Session s = Session.getSession(sessID);
                 debug.error("DestroyOldestAction destroy "+s.getTimeLeft()+" "+ordered.size()+"/"+quota +" "+ sessID+" "+s.getClientID());
                 s.destroySession(s);
             } catch (SessionException e) {
                 if (debug.messageEnabled()) {
                     debug.message("Failed to destroy the next expiring session.", e);
                 }
             }finally{
            	 ordered.remove(sessID.toString());
             }
    	}
        return false;
    }
}
