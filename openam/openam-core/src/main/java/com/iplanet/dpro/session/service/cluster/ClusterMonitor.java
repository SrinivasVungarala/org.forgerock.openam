/**
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */

package com.iplanet.dpro.session.service.cluster;

import com.iplanet.dpro.session.SessionException;
import com.iplanet.dpro.session.SessionID;

/**
 * API for querying status of servers in cluster.
 *
 * Extracted from SessionService class as part of first-pass refactoring to improve SessionService adherence to SRP.
 *
 * @since 13.0.0
 */
public interface ClusterMonitor {

    /**
     * Indicates if the specified site is up.
     *
     * @param siteId A possibly null Site Id.
     * @return True if the Site is up, False if it failed to respond to a query.
     */
    boolean isSiteUp(String siteId);

    /**
     * Indicates if the specified server is up.
     *
     * @param serverId server id
     * @return true if server is up, false otherwise
     */
    boolean checkServerUp(String serverId);

    /**
     * Identify the host (aka home or authoritative) server for the provided session.
     *
     * If the currently assigned host server for the session is found to be down, this method uses a
     * deterministic algorithm to select a new one from those known to the clustering servive
     * based on the session's STORAGE_KEY.
     *
     * This is the key method of "internal request routing".
     *
     * @param sessionId SessionID for which the home server is to be found
     * @return server id for the server instance determined to be the current host
     * @throws SessionException
     */
    String getCurrentHostServer(SessionID sessionId) throws SessionException;

    /**
     * Signals that the ClusterMonitor instance should shutdown. Once called the instance
     * will not receive any further calls.
     */
    void shutdown();
}
