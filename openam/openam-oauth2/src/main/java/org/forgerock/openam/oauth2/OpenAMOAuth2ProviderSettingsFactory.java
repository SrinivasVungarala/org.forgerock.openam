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
 * Copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.openam.oauth2;

import java.security.AccessController;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

import org.forgerock.services.context.Context;
import org.forgerock.json.resource.http.HttpContext;
import org.forgerock.oauth2.core.OAuth2Constants;
import org.forgerock.oauth2.core.AccessToken;
import org.forgerock.oauth2.core.OAuth2ProviderSettings;
import org.forgerock.oauth2.core.OAuth2ProviderSettingsFactory;
import org.forgerock.oauth2.core.OAuth2Request;
import org.forgerock.oauth2.core.exceptions.NotFoundException;
import org.forgerock.oauth2.resources.ResourceSetStore;
import org.forgerock.openam.oauth2.resources.ResourceSetStoreFactory;
import org.forgerock.openam.services.baseurl.BaseURLProviderFactory;
import org.forgerock.openam.utils.RealmNormaliser;
import org.forgerock.util.Reject;
import org.restlet.Request;
import org.restlet.ext.servlet.ServletUtils;

import com.iplanet.sso.SSOToken;
import com.sun.identity.security.AdminTokenAction;
import com.sun.identity.shared.debug.Debug;
import com.sun.identity.sm.DNMapper;
import com.sun.identity.sm.ServiceConfigManager;
import com.sun.identity.sm.ServiceListener;


/**
 * A factory for creating/retrieving OpenAMOAuth2ProviderSettings instances.
 *
 * @since 12.0.0
 */
@Singleton
public class OpenAMOAuth2ProviderSettingsFactory implements OAuth2ProviderSettingsFactory, ServiceListener {

    private final Debug logger = Debug.getInstance("OAuth2Provider");
    private final Map<String, OAuth2ProviderSettings> providerSettingsMap = new HashMap<String, OAuth2ProviderSettings>();
    private final RealmNormaliser realmNormaliser;
    private final CookieExtractor cookieExtractor;
    private final ResourceSetStoreFactory resourceSetStoreFactory;
    private final BaseURLProviderFactory baseURLProviderFactory;

    /**
     * Constructs a new OpenAMOAuth2ProviderSettingsFactory.
     *
     * @param realmNormaliser An instance of the RealmNormaliser.
     * @param cookieExtractor An instance of the CookieExtractor.
     * @param resourceSetStoreFactory An instance of the ResourceSetStoreFactory.
     */
    @Inject
    public OpenAMOAuth2ProviderSettingsFactory(RealmNormaliser realmNormaliser, CookieExtractor cookieExtractor,
            ResourceSetStoreFactory resourceSetStoreFactory, BaseURLProviderFactory baseURLProviderFactory) {
        this.realmNormaliser = realmNormaliser;
        this.cookieExtractor = cookieExtractor;
        this.resourceSetStoreFactory = resourceSetStoreFactory;
        this.baseURLProviderFactory = baseURLProviderFactory;
        addServiceListener();
    }

    private void addServiceListener() {
        try {
            final SSOToken token = AccessController.doPrivileged(AdminTokenAction.getInstance());
            final ServiceConfigManager serviceConfigManager = new ServiceConfigManager(token,
                    OAuth2Constants.OAuth2ProviderService.NAME, OAuth2Constants.OAuth2ProviderService.VERSION);
            if (serviceConfigManager.addListener(this) == null) {
                logger.error("Could not add listener to ServiceConfigManager instance. OAuth2 provider service " +
                        "removals will not be dynamically updated");
            }
        } catch (Exception e) {
            String message = "OAuth2Utils::Unable to construct ServiceConfigManager: " + e;
            logger.error(message, e);
            throw OAuthProblemException.OAuthError.SERVER_ERROR.handle(null, message);
        }
    }

    /**
     * {@inheritDoc}
     */
    public OAuth2ProviderSettings get(OAuth2Request request) throws NotFoundException {
        final OpenAMAccessToken accessToken = (OpenAMAccessToken) request.getToken(AccessToken.class);

        final String realm = accessToken != null ? accessToken.getRealm() :
                realmNormaliser.normalise(request.<String>getParameter(OAuth2Constants.Custom.REALM));
        final HttpServletRequest req = ServletUtils.getRequest(request.<Request>getRequest());
        return get(realm, req);
    }

    /**
     * Cache each provider settings on the realm it was created for.
     * {@inheritDoc}
     */
    public OAuth2ProviderSettings get(String realm) throws NotFoundException {
        OAuth2ProviderSettings providerSettings = providerSettingsMap.get(realm);
        if (providerSettings == null) {
            throw new IllegalStateException("Realm provider settings have not yet been constructed.");
        }
        return providerSettings;
    }

    /**
     * Cache each provider settings on the realm it was created for.
     * {@inheritDoc}
     */
    public OAuth2ProviderSettings get(String realm, HttpServletRequest req) throws NotFoundException {
        Reject.ifNull(realm, "Realm cannot be null");
        Reject.ifNull(req, "Request cannot be null");
        String baseDeploymentUri = baseURLProviderFactory.get(realm).getURL(req);
        return getProviderSettings(realm, baseDeploymentUri);
    }

    @Override
    public OAuth2ProviderSettings get(String realm, Context context) throws NotFoundException {
        Reject.ifNull(realm, "Realm cannot be null");
        Reject.ifNull(context, "Context cannot be null");
        String baseDeploymentUri = baseURLProviderFactory.get(realm).getURL(context.asContext(HttpContext.class));
        return getProviderSettings(realm, baseDeploymentUri);
    }

    private OAuth2ProviderSettings getProviderSettings(String realm, String baseDeploymentUri) throws NotFoundException {
        synchronized (providerSettingsMap) {
            OAuth2ProviderSettings providerSettings = providerSettingsMap.get(realm);
            if (providerSettings == null) {
                ResourceSetStore resourceSetStore = resourceSetStoreFactory.create(realm);
                providerSettings = new OpenAMOAuth2ProviderSettings(realm, baseDeploymentUri, resourceSetStore,
                        cookieExtractor);
                if (providerSettings.exists()) {
                    providerSettingsMap.put(realm, providerSettings);
                } else {
                    throw new NotFoundException("No OpenID Connect provider for realm " + realm);
                }
            }
            return providerSettings;
        }
    }

    @Override
    public void schemaChanged(String serviceName, String version) {

    }

    @Override
    public void globalConfigChanged(String serviceName, String version, String groupName, String serviceComponent, int type) {

    }

    @Override
    public void organizationConfigChanged(String serviceName, String version, String orgName, String groupName,
            String serviceComponent, int type) {
        if (type == ServiceListener.REMOVED) {
            final String realm = DNMapper.orgNameToRealmName(orgName);
            logger.message("Removing OAuth2 provider for realm {}", realm);
            providerSettingsMap.remove(realm);
        }
    }
}
