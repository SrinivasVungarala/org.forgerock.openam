/*
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
package org.forgerock.openam.core.rest;

import com.iplanet.sso.SSOException;
import com.iplanet.sso.SSOToken;
import com.iplanet.sso.SSOTokenManager;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoBundle;
import com.sun.identity.idm.IdRepoException;
import com.sun.identity.idm.IdType;
import com.sun.identity.idsvcs.IdentityDetails;
import com.sun.identity.shared.debug.Debug;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ForbiddenException;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.PermanentException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.openam.utils.JsonValueBuilder;
import org.forgerock.services.context.Context;
import org.forgerock.openam.rest.resource.SSOTokenContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.sun.identity.idsvcs.opensso.IdentityServicesImpl.asMap;

public final class IdentityRestUtils {

    public static final String USER_TYPE = "user";
    public static final String GROUP_TYPE = "group";
    public static final String AGENT_TYPE = "agent";

    public static final String UNIVERSAL_ID = "universalid";
    public static final String FIELD_MAIL = "mail";

    public static final String USER_KBA_ATTRIBUTE = "kbaInformation";

    public static final String USERNAME = "username";

    private static final Debug debug = Debug.getInstance("frRest");

    private IdentityRestUtils() {
    }

    public static void changePassword(Context serverContext, String realm, String username, String oldPassword,
            String newPassword) throws ResourceException {
        try {
            SSOToken token = serverContext.asContext(SSOTokenContext.class).getCallerSSOToken();
            AMIdentity userIdentity = new AMIdentity(token, username, IdType.USER, realm, null);
            userIdentity.changePassword(oldPassword, newPassword);
        } catch (SSOException ssoe) {
            debug.warning("IdentityRestUtils.changePassword() :: SSOException occurred while changing "
                    + "the password for user: " + username, ssoe);
            throw new PermanentException(401, "An error occurred while trying to change the password", ssoe);
        } catch (IdRepoException ire) {
            if (IdRepoBundle.ACCESS_DENIED.equals(ire.getErrorCode())) {
                throw new ForbiddenException("The user is not authorized to change the password");
            } else {
                debug.warning("IdentityRestUtils.changePassword() :: IdRepoException occurred while "
                        + "changing the password for user: " + username, ire);
                throw new InternalServerErrorException("An error occurred while trying to change the password", ire);
            }
        }
    }

    public static Map<String, Set<String>> getIdentityServicesAttributes(String realm, String objectType) {
        Map<String, Set<String>> identityServicesAttributes = new HashMap<>();
        identityServicesAttributes.put("objecttype", Collections.singleton(objectType));
        identityServicesAttributes.put("realm", Collections.singleton(realm));
        return identityServicesAttributes;
    }

    public static SSOToken getSSOToken(String ssoTokenId) throws SSOException {
        SSOTokenManager mgr = SSOTokenManager.getInstance();
        return mgr.createSSOToken(ssoTokenId);
    }

    /**
     * Convert an {@link IdentityDetails} object into a {@link JsonValue}.  Package private for IdentityResourceV2.
     *
     * @param details The IdentityDetails
     * @return The JsonValue
     */
    public static JsonValue identityDetailsToJsonValue(IdentityDetails details) {
        JsonValue result = new JsonValue(new LinkedHashMap<String, Object>());
        try {
            result.put(USERNAME, details.getName());
            result.put("realm", details.getRealm());
            Map<String, Set<String>> attrs = asMap(details.getAttributes());

            for (Map.Entry<String, Set<String>> entry : attrs.entrySet()) {

                // Handle the KBA attribute especially.  This originally came from "outside" OpenAM and was passed to
                // us as JSON.  We took the JSON and (via toString) turned it into text.  Now we take the text and
                // turn it back into JSON.  This is all ok because we're not required to understand it, just pass it
                // back and forth.
                if (entry.getKey().equals(USER_KBA_ATTRIBUTE)) {
                    List<Object> kbaChildren = new ArrayList<>();
                    for (String kbaString : entry.getValue()) {
                        JsonValue kbaValue = JsonValueBuilder.toJsonValue(kbaString);
                        kbaChildren.add(kbaValue.getObject());
                    }
                    result.put(USER_KBA_ATTRIBUTE, kbaChildren.toArray(new Object[0]));
                } else {
                    result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
            }
            return result;
        } catch (final Exception e) {
            throw new JsonValueException(result);
        }
    }

}
