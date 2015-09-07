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

package org.forgerock.openam.uma.rest;

import static org.forgerock.openam.uma.UmaConstants.UMA_BACKEND_POLICY_RESOURCE_HANDLER;

import javax.inject.Inject;
import javax.inject.Named;
import javax.security.auth.Subject;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.iplanet.sso.SSOException;
import com.iplanet.sso.SSOToken;
import com.iplanet.sso.SSOTokenManager;
import com.sun.identity.entitlement.Application;
import com.sun.identity.entitlement.ApplicationType;
import com.sun.identity.entitlement.DenyOverride;
import com.sun.identity.entitlement.EntitlementException;
import com.sun.identity.entitlement.opensso.SubjectUtils;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdEventListener;
import com.sun.identity.idm.IdRepoException;
import com.sun.identity.idm.IdSearchControl;
import com.sun.identity.idm.IdSearchResults;
import com.sun.identity.idm.IdType;
import com.sun.identity.security.AdminTokenAction;
import com.sun.identity.shared.debug.Debug;
import com.sun.identity.sm.DNMapper;
import com.sun.identity.sm.SMSException;
import org.forgerock.http.Context;
import org.forgerock.http.context.AbstractContext;
import org.forgerock.http.context.RootContext;
import org.forgerock.http.Context;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.oauth2.core.exceptions.NotFoundException;
import org.forgerock.oauth2.core.exceptions.ServerException;
import org.forgerock.oauth2.resources.ResourceSetDescription;
import org.forgerock.oauth2.resources.ResourceSetStore;
import org.forgerock.openam.cts.api.fields.ResourceSetTokenField;
import org.forgerock.openam.forgerockrest.entitlements.wrappers.ApplicationManagerWrapper;
import org.forgerock.openam.forgerockrest.entitlements.wrappers.ApplicationTypeManagerWrapper;
import org.forgerock.openam.identity.idm.AMIdentityRepositoryFactory;
import org.forgerock.openam.oauth2.resources.ResourceSetStoreFactory;
import org.forgerock.openam.rest.RealmContext;
import org.forgerock.openam.rest.resource.SubjectContext;
import org.forgerock.openam.uma.UmaConstants;
import org.forgerock.openam.utils.OpenAMSettings;
import org.forgerock.openam.utils.OpenAMSettingsImpl;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.forgerock.util.query.QueryFilter;

/**
 * Listens for changes to UMA Resource Server (OAuth2 Agent) to create or delete its policy
 * application.
 *
 * @since 13.0.0
 */
public class UmaPolicyApplicationListener implements IdEventListener {

    private final Debug logger = Debug.getInstance("UmaProvider");

    private final AMIdentityRepositoryFactory idRepoFactory;
    private final ApplicationManagerWrapper applicationManager;
    private final ApplicationTypeManagerWrapper applicationTypeManagerWrapper;
    private final RequestHandler policyResource;
    private final ResourceSetStoreFactory resourceSetStoreFactory;

    /**
     * Creates an instance of the {@code UmaPolicyApplicationListener}.
     *
     * @param idRepoFactory An instance of the {@code AMIdentityRepositoryFactory}.
     * @param applicationManager An instance of the {@code ApplicationManagerWrapper}.
     * @param applicationTypeManagerWrapper An instance of the {@code ApplicationTypeManagerWrapper}.
     * @param policyResource An instance of the policy backend {@code PromisedRequestHandler}.
     * @param resourceSetStoreFactory An instance of the {@code ResourceSetStoreFactory}.
     */
    @Inject
    public UmaPolicyApplicationListener(final AMIdentityRepositoryFactory idRepoFactory,
            ApplicationManagerWrapper applicationManager,
            ApplicationTypeManagerWrapper applicationTypeManagerWrapper,
            @Named(UMA_BACKEND_POLICY_RESOURCE_HANDLER) RequestHandler policyResource,
            ResourceSetStoreFactory resourceSetStoreFactory) {
        this.idRepoFactory = idRepoFactory;
        this.applicationManager = applicationManager;
        this.applicationTypeManagerWrapper = applicationTypeManagerWrapper;
        this.policyResource = policyResource;
        this.resourceSetStoreFactory = resourceSetStoreFactory;
    }

    /**
     * Ensures that if the identity is a UMA resource server then a policy application exists for
     * it, otherwise (based on configuration) deletes the resource servers policy application,
     * policies and resource sets.
     *
     * @param universalId {@inheritDoc}
     */
    @Override
    public void identityChanged(String universalId) {
        try {
            AMIdentity identity = getIdentity(universalId);
            if (!isAgentIdentity(identity)) {
                return;
            }

            if (isResourceServer(identity)) {
                createApplication(identity.getRealm(), identity.getName());
            } else {
                removeApplication(identity.getRealm(), identity.getName());
            }

        } catch (IdRepoException e) {
            logger.error("Failed to get identity", e);
        } catch (SSOException e) {
            logger.error("Failed to get identity", e);
        } catch (NotFoundException e) {
            logger.error("Failed to get UMA Provider settings", e);
        } catch (ServerException e) {
            logger.error("Failed to get UMA Provider settings", e);
        }
    }

    /**
     * Not required.
     *
     * @param universalId {@inheritDoc}
     */
    @Override
    public void identityRenamed(String universalId) {
        //OAuth2 agents cannot be renamed
    }

    /**
     * Deletes, (based on configuration), the resource servers policy application, policies and
     * resource sets.
     *
     * @param universalId {@inheritDoc}
     */
    @Override
    public void identityDeleted(String universalId) {
        try {
            AMIdentity identity = getIdentity(universalId);
            if (!isAgentIdentity(identity)) {
                return;
            }
            removeApplication(identity.getRealm(), identity.getName());

        } catch (IdRepoException e) {
            logger.error("Failed to get identity", e);
        } catch (NotFoundException e) {
            logger.error("Failed to get UMA Provider settings", e);
        } catch (ServerException e) {
            logger.error("Failed to get UMA Provider settings", e);
        }
    }

    /**
     * Not required.
     */
    @Override
    public void allIdentitiesChanged() {
    }

    private AMIdentity getIdentity(String universalId) throws IdRepoException {
        return new AMIdentity(null, universalId);
    }

    private boolean isAgentIdentity(AMIdentity identity) {
        return IdType.AGENT.equals(identity.getType());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> getIdentityAttributes(AMIdentity identity) throws IdRepoException, SSOException {
        IdSearchControl searchControl = new IdSearchControl();
        searchControl.setAllReturnAttributes(true);
        searchControl.setMaxResults(0);
        SSOToken adminToken = AccessController.doPrivileged(AdminTokenAction.getInstance());
        IdSearchResults searchResults = idRepoFactory.create(identity.getRealm(), adminToken)
                .searchIdentities(IdType.AGENT, identity.getName(), searchControl);
        if (searchResults.getSearchResults().size() != 1) {
            throw new IdRepoException("UmaPolicyApplicationListener.getIdentityAttributes : More than one agent found");
        }
        return new HashMap<String, Set<String>>((Map) searchResults.getResultAttributes().values().iterator().next());
    }

    private Set<String> getScopes(Map<String, Set<String>> identity) {
        return identity.get("com.forgerock.openam.oauth2provider.scopes");
    }

    private boolean isResourceServer(AMIdentity identity) throws IdRepoException, SSOException {

        Set<String> scopes = getScopes(getIdentityAttributes(identity));
        if (scopes != null) {
            for (String scope : scopes) {
                String[] scopeParts = scope.split("\\|");
                if (scopeParts[0].contains("uma_protection")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void createApplication(String realm, String resourceServerId) {
        Subject adminSubject = SubjectUtils.createSuperAdminSubject();
        try {
            Application application = applicationManager.getApplication(adminSubject, realm, resourceServerId);
            if (application == null) {
                ApplicationType applicationType = applicationTypeManagerWrapper.getApplicationType(adminSubject,
                        UmaConstants.UMA_POLICY_APPLICATION_TYPE);
                application = new Application(resourceServerId, applicationType);
                application.setEntitlementCombiner(DenyOverride.class);
                applicationManager.saveApplication(adminSubject, realm, application);
            }
        } catch (EntitlementException e) {
            logger.error("Failed to create policy application", e);
        }
    }

    private void removeApplication(String realm, String resourceServerId) throws NotFoundException, ServerException {
        OpenAMSettingsImpl umaSettings = new OpenAMSettingsImpl(UmaConstants.SERVICE_NAME, UmaConstants.SERVICE_VERSION);
        if (onDeleteResourceServerDeletePolicies(umaSettings, realm)) {
            deletePolicies(realm, resourceServerId);
            try {
                Subject adminSubject = SubjectUtils.createSuperAdminSubject();
                if (applicationManager.getApplication(adminSubject, realm, resourceServerId) != null) {
                    applicationManager.deleteApplication(adminSubject, realm, resourceServerId);
                }
            } catch (EntitlementException e) {
                logger.error("Failed to remove policy application", e);
            }
        }
        if (onDeleteResourceServerDeleteResourceSets(umaSettings, realm)) {
            deleteResourceSets(realm, resourceServerId);
        }
    }

    private boolean onDeleteResourceServerDeletePolicies(OpenAMSettings umaSettings, String realm) throws ServerException {
        try {
            return umaSettings.getBooleanSetting(realm, UmaConstants.DELETE_POLICIES_ON_RESOURCE_SERVER_DELETION);
        } catch (SMSException e) {
            logger.error(e.getMessage());
            throw new ServerException(e);
        } catch (SSOException e) {
            logger.error(e.getMessage());
            throw new ServerException(e);
        }
    }

    private boolean onDeleteResourceServerDeleteResourceSets(OpenAMSettings umaSettings, String realm) throws ServerException {
        try {
            return umaSettings.getBooleanSetting(realm, UmaConstants.DELETE_RESOURCE_SETS_ON_RESOURCE_SERVER_DELETION);
        } catch (SMSException e) {
            logger.error(e.getMessage());
            throw new ServerException(e);
        } catch (SSOException e) {
            logger.error(e.getMessage());
            throw new ServerException(e);
        }
    }

    private void deletePolicies(String realm, String resourceServerId) {
        RealmContext realmContext = new RealmContext(new RootContext());
        realmContext.addDnsAlias("/", realm);
        final Context context = new AdminSubjectContext(realmContext);
        QueryRequest request = Requests.newQueryRequest("")
                .setQueryFilter(QueryFilter.equalTo(new JsonPointer("applicationName"), resourceServerId));
        final List<ResourceResponse> resources = new ArrayList<>();
        policyResource.handleQuery(context, request, new QueryResourceHandler() {
            @Override
            public boolean handleResource(ResourceResponse resource) {
                resources.add(resource);
                return true;
            }
        })
                .thenAsync(new AsyncFunction<QueryResponse, List<ResourceResponse>, ResourceException>() {
                    @Override
                    public Promise<List<ResourceResponse>, ResourceException> apply(QueryResponse response) {
                        List<Promise<ResourceResponse, ResourceException>> promises = new ArrayList<>();
                        for (ResourceResponse policy : resources) {
                            DeleteRequest deleteRequest = Requests.newDeleteRequest("", policy.getId());
                            promises.add(policyResource.handleDelete(context, deleteRequest));
                        }
                        Promise<List<ResourceResponse>, ResourceException> when = Promises.when(promises);
                        return when;
                    }
                })
                .thenOnException(new ExceptionHandler<ResourceException>() {
                    @Override
                    public void handleException(ResourceException error) {
                        logger.error(error.getReason());
                    }
                });
    }

    private void deleteResourceSets(String realm, String resourceServerId) throws NotFoundException, ServerException {
        ResourceSetStore resourceSetStore = resourceSetStoreFactory.create(DNMapper.orgNameToRealmName(realm));
        QueryFilter<String> queryFilter
                = QueryFilter.equalTo(ResourceSetTokenField.CLIENT_ID, resourceServerId);
        Set<ResourceSetDescription> results = resourceSetStore.query(queryFilter);
        for (ResourceSetDescription resourceSet : results) {
            resourceSetStore.delete(resourceSet.getId(), resourceSet.getResourceOwnerId());
        }
    }

    /**
     * SubjectContext implementation which contains an admin token.
     */
    private static final class AdminSubjectContext extends AbstractContext implements SubjectContext {

        private final SSOToken adminToken;

        private AdminSubjectContext(Context parent) {
            super(parent, "subjectContext");
            this.adminToken = AccessController.doPrivileged(AdminTokenAction.getInstance());
        }

        @Override
        public Subject getCallerSubject() {
            return SubjectUtils.createSubject(adminToken);
        }

        @Override
        public Subject getSubject(String tokenId) {
            try {
                return SubjectUtils.createSubject(getSSOToken(tokenId));
            } catch (SSOException ssoE) {
                return null;
            }
        }

        @Override
        public SSOToken getCallerSSOToken() throws SSOException {
            return adminToken;
        }

        private SSOToken getSSOToken(String tokenId) throws SSOException {
            return getSSOToken(tokenId, SSOTokenManager.getInstance());
        }

        private SSOToken getSSOToken(String tokenId, SSOTokenManager tokenManager) throws SSOException {
            Reject.ifNull(tokenManager, "A valid SSO token manager is required");
            return tokenManager.createSSOToken(tokenId);
        }
    }
}
