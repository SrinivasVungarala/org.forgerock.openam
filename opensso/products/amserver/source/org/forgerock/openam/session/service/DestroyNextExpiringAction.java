/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2005 Sun Microsystems Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * https://opensso.dev.java.net/public/CDDLv1.0.html or
 * opensso/legal/CDDLv1.0.txt See the License for the specific language
 * governing permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL Header Notice in each file
 * and include the License file at opensso/legal/CDDLv1.0.txt. If applicable,
 * add the following below the CDDL Header, with the fields enclosed by brackets
 * [] replaced by your own identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 */
/**
 * Portions Copyrighted 2011-2012 ForgeRock AS
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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DestroyNextExpiringAction implements QuotaExhaustionAction {

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
    	
    	final Map<String, Long> ordered=sortByValue(sessions);
    	while (ordered.size()>=quota){
    		 SessionID sessID = new SessionID(ordered.keySet().iterator().next());
             try {
                 Session s = Session.getSession(sessID);
                 debug.error("DestroyNextExpiringAction destroy "+s.getIdleTime()+" "+ordered.size()+"/"+quota +" "+ sessID+" "+s.getClientID());
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
