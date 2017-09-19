/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2006 Sun Microsystems Inc. All Rights Reserved
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
 * $Id: RealmUtils.java,v 1.2 2008/06/25 05:42:16 qcheng Exp $
 *
 * Portions Copyrighted 2015 ForgeRock AS.
 */

package org.forgerock.openam.utils;

import com.iplanet.sso.SSOToken;
import com.sun.identity.sm.OrganizationConfigManager;
import com.sun.identity.sm.SMSException;

import java.util.HashSet;
import java.util.Set;

/**
 * Realm related utilities.
 */
public final class RealmUtils {
    private RealmUtils() {
    }

    /**
     * Returns parent realm from a given fully qualified realm.
     *
     * @param path Fully qualified realm.
     * @return parent realm.
     */
    public static String getParentRealm(String path) {
        String parent = "/";
        path = normalizeRealm(path);

        if ((path != null) && (path.length() > 0)) {
            int idx = path.lastIndexOf('/');
            if (idx > 0) {
                parent = path.substring(0, idx);
            }
        }

        return parent;
    }

    /**
     * Returns child realm from a given fully qualified realm.
     *
     * @param path Fully qualified realm.
     * @return child realm.
     */
    public static String getChildRealm(String path) {
        String child = "/";
        path = normalizeRealm(path);
        if ((path != null) && (path.length() > 0)) {
            int idx = path.lastIndexOf('/');
            if (idx != -1) {
                child = path.substring(idx+1);
            }
        }

        return child;
    }

    private static String normalizeRealm(String path) {
        if (path != null) {
            path = path.trim();
            if (path.length() > 0) {
                while (path.indexOf("//") != -1) {
                    path = path.replaceAll("//", "/");
                }
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() -1);
                }
            }
        }
        return path.trim();
    }

    /**
     * Retrieve the names of all the realms starting with '/' and including '/'.
     *
     * @param adminToken The admin token.
     * @return A list of all the realm names.
     * @throws SMSException If reading from the SMS failed.
     */
    @SuppressWarnings("unchecked")
    public static Set<String> getRealmNames(SSOToken adminToken) throws SMSException {
        Set<String> rootSubRealms = new OrganizationConfigManager(adminToken, "/").getSubOrganizationNames("*", true);
        Set<String> qualifiedRealmNames = new HashSet<>();
        qualifiedRealmNames.add("/");
        for (String subRealm : rootSubRealms) {
            qualifiedRealmNames.add("/" + subRealm);
        }
        return qualifiedRealmNames;
    }
}
