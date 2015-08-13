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
 * Copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.openam.forgerockrest;

import static com.sun.identity.idsvcs.opensso.IdentityServicesImpl.asAttributeArray;
import static com.sun.identity.idsvcs.opensso.IdentityServicesImpl.asMap;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.resource.ResourceException.*;
import static org.forgerock.json.resource.Responses.*;
import static org.forgerock.openam.forgerockrest.RestUtils.getCookieFromServerContext;
import static org.forgerock.openam.forgerockrest.RestUtils.isAdmin;
import static org.forgerock.util.promise.Promises.newExceptionPromise;
import static org.forgerock.util.promise.Promises.newResultPromise;

import javax.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.iplanet.am.util.SystemProperties;
import com.iplanet.sso.SSOException;
import com.iplanet.sso.SSOToken;
import com.iplanet.sso.SSOTokenManager;
import com.sun.identity.authentication.util.ISAuthConstants;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoException;
import com.sun.identity.idsvcs.AccessDenied;
import com.sun.identity.idsvcs.GeneralFailure;
import com.sun.identity.idsvcs.IdentityDetails;
import com.sun.identity.idsvcs.NeedMoreCredentials;
import com.sun.identity.idsvcs.ObjectNotFound;
import com.sun.identity.idsvcs.TokenExpired;
import com.sun.identity.idsvcs.opensso.IdentityServicesImpl;
import com.sun.identity.shared.Constants;
import com.sun.identity.shared.debug.Debug;
import com.sun.identity.shared.encode.Hash;
import com.sun.identity.sm.SMSException;
import com.sun.identity.sm.ServiceConfig;
import com.sun.identity.sm.ServiceConfigManager;
import com.sun.identity.sm.ServiceNotFoundException;
import org.apache.commons.lang.RandomStringUtils;
import org.forgerock.guice.core.InjectorHolder;
import org.forgerock.http.context.ServerContext;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.ConflictException;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.ForbiddenException;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.PermanentException;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.json.resource.http.HttpContext;
import org.forgerock.openam.core.CoreWrapper;
import org.forgerock.openam.cts.CTSPersistentStore;
import org.forgerock.openam.cts.exceptions.CoreTokenException;
import org.forgerock.openam.cts.exceptions.DeleteFailedException;
import org.forgerock.openam.forgerockrest.utils.MailServerLoader;
import org.forgerock.openam.forgerockrest.utils.PrincipalRestUtils;
import org.forgerock.openam.rest.resource.RealmContext;
import org.forgerock.openam.services.RestSecurity;
import org.forgerock.openam.services.RestSecurityProvider;
import org.forgerock.openam.services.email.MailServer;
import org.forgerock.openam.services.email.MailServerImpl;
import org.forgerock.openam.tokens.CoreTokenField;
import org.forgerock.openam.tokens.TokenType;
import org.forgerock.openam.utils.StringUtils;
import org.forgerock.openam.utils.TimeUtils;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.Promise;

/**
 * A simple {@code Map} based collection resource provider.
 */
public final class IdentityResourceV1 implements CollectionResourceProvider {

    private static final String AM_ENCRYPTION_PWD = "am.encryption.pwd";

    private static final String SEND_NOTIF_TAG = "IdentityResource.sendNotification() :: ";
    private static Debug debug = Debug.getInstance("frRest");
    public static final String USER_TYPE = "user";

    public static final String GROUP_TYPE = "group";
    public static final String AGENT_TYPE = "agent";

    // TODO: filters, sorting, paged results.

    private final String userType;

    private ServiceConfigManager mailmgr;
    private ServiceConfig mailscm;
    private Map<String, HashSet<String>> mailattrs;

    final static String MAIL_IMPL_CLASS = "forgerockMailServerImplClassName";
    final static String MAIL_SUBJECT = "forgerockEmailServiceSMTPSubject";
    final static String MAIL_MESSAGE = "forgerockEmailServiceSMTPMessage";

    final static String UNIVERSAL_ID = "universalid";
    final static String MAIL = "mail";
    final static String UNIVERSAL_ID_ABBREV = "uid";
    final static String USERNAME = "username";
    final static String EMAIL = "email";
    final static String TOKEN_ID = "tokenId";
    final static String CONFIRMATION_ID = "confirmationId";
    public static final String OLD_PASSWORD = "olduserpassword";

    private final RestSecurityProvider restSecurityProvider;
    private final IdentityServicesImpl identityServices;

    private final MailServerLoader mailServerLoader;

    private final CoreWrapper coreWrapper;

    /**
     * Creates a backend
     */
    public IdentityResourceV1(String userType, MailServerLoader mailServerLoader,
            IdentityServicesImpl identityServices, CoreWrapper coreWrapper, RestSecurityProvider restSecurityProvider) {
        this(userType, null, null, mailServerLoader, identityServices, coreWrapper, restSecurityProvider);
    }

    /**
     * Enum to lazy init the CTSPersistentStore variable in a thread safe manner.
     */
    private enum CTSHolder {
        INSTANCE;

        private final CTSPersistentStore cts;

        private CTSHolder() {
            cts = InjectorHolder.getInstance(CTSPersistentStore.class);
        }

        static CTSPersistentStore getCTS() {
            return INSTANCE.cts;
        }
    }

    // Constructor used for testing...
    IdentityResourceV1(String userType, ServiceConfigManager mailmgr, ServiceConfig mailscm,
            MailServerLoader mailServerLoader, IdentityServicesImpl identityServices, CoreWrapper coreWrapper,
            RestSecurityProvider restSecurityProvider) {
        this.userType = userType;
        this.mailmgr = mailmgr;
        this.mailscm = mailscm;
        this.mailServerLoader = mailServerLoader;
        this.identityServices = identityServices;
        this.coreWrapper = coreWrapper;
        this.restSecurityProvider = restSecurityProvider;
    }

    /**
     * Gets the user id from the session provided in the server context
     *
     * @param context Current Server Context
     * @param request Request from client to retrieve id
     */
    private Promise<ActionResponse, ResourceException> idFromSession(final ServerContext context, final ActionRequest request) {

        JsonValue result = new JsonValue(new LinkedHashMap<String, Object>(1));
        SSOToken ssotok;
        AMIdentity amIdentity;

        try {
            SSOTokenManager mgr = SSOTokenManager.getInstance();
            ssotok = mgr.createSSOToken(getCookieFromServerContext(context));
            amIdentity = new AMIdentity(ssotok);

            // build resource
            result.put("id", amIdentity.getName());
            result.put("realm", com.sun.identity.sm.DNMapper.orgNameToRealmName(amIdentity.getRealm()));
            result.put("dn", amIdentity.getUniversalId());
            result.put("successURL", ssotok.getProperty(ISAuthConstants.SUCCESS_URL,false));
            result.put("fullLoginURL", ssotok.getProperty(ISAuthConstants.FULL_LOGIN_URL,false));
            if (debug.messageEnabled()) {
                debug.message("IdentityResource.idFromSession() :: Retrieved ID for user, " + amIdentity.getName());
            }
            return newResultPromise(newActionResponse(result));

        } catch (SSOException e) {
            debug.error("IdentityResource.idFromSession() :: Cannot retrieve SSO Token: " + e);
            return newExceptionPromise(adapt(new ForbiddenException("SSO Token cannot be retrieved.", e)));
        } catch (IdRepoException ex) {
            debug.error("IdentityResource.idFromSession() :: Cannot retrieve user from IdRepo" + ex);
            return newExceptionPromise(adapt(new ForbiddenException("Cannot retrieve id from session.", ex)));
        }
    }

    /**
     * Generates a secure hash to use as token ID
     * @param resource string that will be used to create random hash
     * @return random string
     */
    static private String generateTokenID(String resource) {
        if (StringUtils.isBlank(resource)) {
            return null;
        }
        return Hash.hash(resource + RandomStringUtils.randomAlphanumeric(32));
    }

    /**
     * Generates a CTS REST Token, including realm information in its {@code CoreTokenField.STRING_ONE} field.
     *
     * @param resource The resource for which the tokenID will be generated
     * @param userId The user's ID, associated with the token
     * @param tokenLifeTimeSeconds Length of time from now in second for the token to remain valid
     * @param realmName The name of the realm in which this token is valid
     * @return the generated CTS REST token
     */
    private org.forgerock.openam.cts.api.tokens.Token generateToken(String resource, String userId,
                                                                    Long tokenLifeTimeSeconds, String realmName) {
        Calendar ttl = Calendar.getInstance();
        org.forgerock.openam.cts.api.tokens.Token ctsToken = new org.forgerock.openam.cts.api.tokens.Token(
                generateTokenID(resource), TokenType.REST);
        if (!StringUtils.isBlank(userId)) {
            ctsToken.setUserId(userId);
        }
        ctsToken.setAttribute(CoreTokenField.STRING_ONE, realmName);
        ttl.setTimeInMillis(ttl.getTimeInMillis() + (tokenLifeTimeSeconds*1000));
        ctsToken.setExpiryTimestamp(ttl);
        return ctsToken;
    }
    /**
     * This method will create a confirmation email that contains a {@link org.forgerock.openam.cts.api.tokens.Token},
     * confirmationId and email that was provided in the request.
     * @param context Current Server Context
     * @param request Request from client to retrieve id
     */
    private Promise<ActionResponse, ResourceException> createRegistrationEmail(final ServerContext context,
            final ActionRequest request, final String realm, final RestSecurity restSecurity) {

        JsonValue result = new JsonValue(new LinkedHashMap<String, Object>(1));
        final JsonValue jVal = request.getContent();
        String emailAddress = null;
        String confirmationLink;
        String tokenID;

        try {
            if (restSecurity == null) {
                if (debug.warningEnabled()) {
                    debug.warning("IdentityResource.createRegistrationEmail(): " +
                            "Rest Security not created. restSecurity = " + restSecurity);
                }
                throw new NotFoundException("Rest Security Service not created" );
            }
            if (!restSecurity.isSelfRegistration()) {
                if (debug.warningEnabled()) {
                    debug.warning("IdentityResource.createRegistrationEmail(): Self-Registration set to :"
                            + restSecurity.isSelfRegistration());
                }
                throw new NotSupportedException("Self Registration is not enabled.");
            }

            // Get full deployment URL
            HttpContext header = context.asContext(HttpContext.class);
            StringBuilder deploymentURL = RestUtils.getFullDeploymentURI(header.getPath());

            // Get the email address provided from registration page
            emailAddress = jVal.get(EMAIL).asString();
            if (StringUtils.isBlank(emailAddress)){
                throw new BadRequestException("Email not provided");
            }

            String subject = jVal.get("subject").asString();
            String message = jVal.get("message").asString();

            // Retrieve email registration token life time
            Long tokenLifeTime = restSecurity.getSelfRegTLT();

            // Create CTS Token
            org.forgerock.openam.cts.api.tokens.Token ctsToken = generateToken(emailAddress, "anonymous",
                    tokenLifeTime, realm);

            // Store token in datastore
            CTSHolder.getCTS().createAsync(ctsToken);
            tokenID = ctsToken.getTokenId();

            // Create confirmationId
            String confirmationId = Hash.hash(tokenID + emailAddress + SystemProperties.get(AM_ENCRYPTION_PWD));

            // Build Confirmation URL
            String confURL = restSecurity.getSelfRegistrationConfirmationUrl();
            StringBuilder confURLBuilder = new StringBuilder(100);
            if (StringUtils.isBlank(confURL)) {
                confURLBuilder.append(deploymentURL.append("/json/confirmation/register").toString());
            } else {
                confURLBuilder.append(confURL);
            }

            confirmationLink = confURLBuilder.append("?confirmationId=").append(requestParamEncode(confirmationId))
                    .append("&email=").append(requestParamEncode(emailAddress))
                    .append("&tokenId=").append(requestParamEncode(tokenID))
                    .append("&realm=").append(realm)
                    .toString();

            // Send Registration
            sendNotification(emailAddress, subject, message, realm, confirmationLink);

            if (debug.messageEnabled()) {
                debug.message("IdentityResource.createRegistrationEmail() :: Sent notification to, " + emailAddress +
                        " with subject, " + subject + ". In realm, " + realm + " for token ID, " + tokenID);
            }

            return newResultPromise(newActionResponse(result));
        } catch (BadRequestException | NotFoundException be) {
            debug.warning("IdentityResource.createRegistrationEmail: Cannot send email to : " + emailAddress
                    + be.getMessage());
            return newExceptionPromise(adapt(be));
        } catch (NotSupportedException nse) {
            debug.error("IdentityResource.createRegistrationEmail: Operation not enabled " + nse.getMessage());
            return newExceptionPromise(adapt(nse));
        } catch (Exception e) {
            debug.error("IdentityResource.createRegistrationEmail: Cannot send email to : " + emailAddress
                    + e.getMessage());
            return newExceptionPromise(newNotFoundException("Email not sent"));
        }
    }

    /**
     * Sends email notification to end user
     * @param to Resource receiving notification
     * @param subject Notification subject
     * @param message Notification Message
     * @param confirmationLink Confirmation Link to be sent
     * @throws Exception when message cannot be sent
     */
    private void sendNotification(String to, String subject, String message,
                                  String realm, String confirmationLink) throws ResourceException {

        try {
            mailmgr = new ServiceConfigManager(RestUtils.getToken(),
                    MailServerImpl.SERVICE_NAME, MailServerImpl.SERVICE_VERSION);
            mailscm = mailmgr.getOrganizationConfig(realm,null);
            mailattrs = mailscm.getAttributes();

        } catch (SMSException smse) {
            if (debug.errorEnabled()) {
                debug.error(SEND_NOTIF_TAG + "Cannot create service " + MailServerImpl.SERVICE_NAME + smse);
            }
            throw new InternalServerErrorException("Cannot create the service: " + MailServerImpl.SERVICE_NAME, smse);

        } catch (SSOException ssoe) {
            if (debug.errorEnabled()) {
                debug.error(SEND_NOTIF_TAG + "Invalid SSOToken " + ssoe);
            }
            throw new InternalServerErrorException("Cannot create the service: " + MailServerImpl.SERVICE_NAME, ssoe);
        }

        if (mailattrs == null || mailattrs.isEmpty()) {
            if (debug.errorEnabled()) {
                debug.error(SEND_NOTIF_TAG + "no attrs set" + mailattrs);
            }
            throw new NotFoundException("No service Config Manager found for realm " + realm);
        }

        // Get MailServer Implementation class
        String attr = mailattrs.get(MAIL_IMPL_CLASS).iterator().next();
        MailServer mailServer;
        try {
            mailServer = mailServerLoader.load(attr, realm);
        } catch (IllegalStateException e) {
            String error = "Failed to load mail server implementation: " + attr;
            debug.error(SEND_NOTIF_TAG + error);
            throw new InternalServerErrorException(error, e);
        }

        try {
            // Check if subject has not  been included
            if (StringUtils.isBlank(subject)){
                // Use default email service subject
                subject = mailattrs.get(MAIL_SUBJECT).iterator().next();
            }
        } catch (Exception e) {
            if (debug.warningEnabled()) {
                debug.warning(SEND_NOTIF_TAG + "no subject found ", e);
            }
            subject = "";
        }
        try {
            // Check if Custom Message has been included
            if (StringUtils.isBlank(message)){
                // Use default email service message
                message = mailattrs.get(MAIL_MESSAGE).iterator().next();
            }
            message = message + System.getProperty("line.separator") + confirmationLink;
        } catch (Exception e) {
            if (debug.warningEnabled()) {
                debug.warning(SEND_NOTIF_TAG + "no message found", e);
            }
            message = confirmationLink;
        }

        // Send the emails via the implementation class
        try {
            mailServer.sendEmail(to, subject, message);
        } catch (MessagingException e) {
            String error = "Failed to send mail";
            if (debug.errorEnabled()) {
                debug.error(SEND_NOTIF_TAG + error, e);
            }
            throw new InternalServerErrorException(error, e);
        }
    }

    /**
     * Will validate confirmationId is correct
     * @param context Current Server Context
     * @param request Request from client to confirm registration
     */
    private Promise<ActionResponse, ResourceException> confirmationIdCheck(final ServerContext context,
            final ActionRequest request, final String realm) {
        final String METHOD = "IdentityResource.confirmationIdCheck";
        final JsonValue jVal = request.getContent();
        String tokenID;
        String confirmationId;
        String email = null;
        String username = null;
        //email or username value used to create confirmationId
        String hashComponent = null;
        String hashComponentAttr = null;
        JsonValue result = new JsonValue(new LinkedHashMap<String, Object>(1));

        try{
            tokenID = jVal.get(TOKEN_ID).asString();
            confirmationId = jVal.get(CONFIRMATION_ID).asString();
            email = jVal.get(EMAIL).asString();
            username = jVal.get(USERNAME).asString();

            if (StringUtils.isBlank(confirmationId)) {

                if (debug.errorEnabled()) {
                    debug.error(METHOD + ": Bad Request - confirmationId not found in request.");
                }
                throw new BadRequestException("confirmationId not provided");
            }
            if (StringUtils.isBlank(email) && !StringUtils.isBlank(username)) {
                hashComponent = username;
                hashComponentAttr = USERNAME;
            }
            if (!StringUtils.isBlank(email) && StringUtils.isBlank(username)) {
                hashComponent = email;
                hashComponentAttr = EMAIL;
            }
            if (StringUtils.isBlank(hashComponent)) {
                if (debug.errorEnabled()) {
                    debug.error(METHOD + ": Bad Request - hashcomponent not found in request.");
                }
                throw new BadRequestException("Required information not provided");
            }
            if (StringUtils.isBlank(tokenID)) {
                if (debug.errorEnabled()) {
                    debug.error(METHOD + ": Bad Request - tokenID not found in request.");
                }
                throw new BadRequestException("tokenId not provided");
            }

            validateToken(tokenID, realm, hashComponent, confirmationId);

            // build resource
            result.put(hashComponentAttr,hashComponent);
            result.put(TOKEN_ID, tokenID);
            result.put(CONFIRMATION_ID, confirmationId);

            if (debug.messageEnabled()) {
                debug.message(METHOD + ": Confirmed for token, " + tokenID + ", with confirmation " + confirmationId);
            }

            return newResultPromise(newActionResponse(result));

        } catch (BadRequestException be) {
            debug.warning(METHOD + ": Cannot confirm registration/forgotPassword for : " + hashComponent, be);
            return newExceptionPromise(adapt(be));
        } catch (Exception e) {
            debug.error(METHOD + ": Cannot confirm registration/forgotPassword for : " + hashComponent, e);
            return newExceptionPromise(newNotFoundException(e.getMessage()));
        }
    }

    /**
     * Validates a provided token against a selection of criteria to ensure that it's valid for the given
     * realm. This function is the validation equiv. of
     * {@link IdentityResourceV2#generateToken(String, String, Long, String)}.
     *
     * @param tokenID The token ID to retrieve from the store, against which to perform validation
     * @param realm The realm under which the current request is being made, must match the realm the token was
     *              generated by, not null
     * @param hashComponent The hash component used to created the confirmationId
     * @param confirmationId The confirmationId, not null
     * @throws NotFoundException If the token doesn't exist in the store
     * @throws CoreTokenException If there were unexpected issues communicating with the CTS
     * @throws BadRequestException If the realm or confirmationId were invalid for the token retrieved
     */
    private void validateToken(String tokenID, String realm, String hashComponent, String confirmationId)
            throws NotFoundException, CoreTokenException, BadRequestException {

        Reject.ifNull(realm);
        Reject.ifNull(confirmationId);

        //check expiry
        org.forgerock.openam.cts.api.tokens.Token ctsToken = CTSHolder.getCTS().read(tokenID);

        if (ctsToken == null || TimeUtils.toUnixTime(ctsToken.getExpiryTimestamp()) < TimeUtils.currentUnixTime()) {
            throw new NotFoundException("Cannot find tokenID: " + tokenID);
        }

        // check confirmationId
        if (!confirmationId.equalsIgnoreCase(Hash.hash(tokenID + hashComponent +
                SystemProperties.get(AM_ENCRYPTION_PWD)))) {
            debug.error("IdentityResource.validateToken: Invalid confirmationId : " + confirmationId);
            throw new BadRequestException("Invalid confirmationId", null);
        }

        //check realm
        if (!realm.equals(ctsToken.getValue(CoreTokenField.STRING_ONE))) {
            debug.error("IdentityResource.validateToken: Invalid realm : " + realm);
            throw new BadRequestException("Invalid realm", null);
        }

        if (debug.messageEnabled()) {
            debug.message("Validated token with ID, " + tokenID + ", in realm, " + realm + " against "
                    + confirmationId);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ActionResponse, ResourceException> actionCollection(final ServerContext context,
            final ActionRequest request) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        final String realm = realmContext.getResolvedRealm();
        RestSecurity restSecurity = restSecurityProvider.get(realm);

        final String action = request.getAction();
        if (action.equalsIgnoreCase("idFromSession")) {
            return idFromSession(context, request);
        } else if (action.equalsIgnoreCase("register")){
            return createRegistrationEmail(context,request, realm, restSecurity);
        } else if (action.equalsIgnoreCase("confirm")) {
            return confirmationIdCheck(context, request, realm);
        } else if (action.equalsIgnoreCase("anonymousCreate")) {
            return anonymousCreate(context, request, realm, restSecurity);
        } else if (action.equalsIgnoreCase("forgotPassword")) {
            return generateNewPasswordEmail(context, request, realm, restSecurity);
        } else if (action.equalsIgnoreCase("forgotPasswordReset")) {
            return anonymousUpdate(context, request, realm);
        } else { // for now this is the only case coming in, so fail if otherwise
            return newExceptionPromise(newNotSupportedException("Actions are not supported for resource instances"));
        }
    }

    /**
     * Uses an amAdmin SSOtoken to create an AMIdentity from the UID provided and checks
     * whether the AMIdentity in context is active/inactive
     * @param uid the universal identifier of the user
     * @return true is the user is active;false otherwise
     * @throws NotFoundException invalid SSOToken, invalid UID
     */
    private boolean isUserActive(String uid) throws NotFoundException {
        try {
            AMIdentity userIdentity = new AMIdentity(RestUtils.getToken(), uid);
            if (debug.messageEnabled()) {
                debug.message("IdentityResource.isUserActive() : UID: " + uid + " isActive,  " +
                        userIdentity.isActive());
            }
            return userIdentity.isActive();
        } catch (IdRepoException idr) {
            if (debug.errorEnabled()) {
                debug.error("IdentityResource.isUserActive() : Invalid UID: " + uid + " Exception " + idr);
            }
            throw new NotFoundException("Invalid UID, could not retrived " + uid);
        } catch (SSOException ssoe){
            if (debug.errorEnabled()) {
                debug.error("IdentityResource.isUserActive() : Invalid SSOToken" + " Exception " + ssoe);
            }
            throw new NotFoundException("Invalid SSOToken " + ssoe.getMessage());
        }
    }

    SSOToken getSSOToken(String ssoTokenId) throws SSOException {
        SSOTokenManager mgr = SSOTokenManager.getInstance();
        return mgr.createSSOToken(ssoTokenId);
    }

    /**
     * Generates the e-mail contents based on the incoming request.
     *
     * Will only send the e-mail if all the following conditions are true:
     *
     * - Forgotten Password service is enabled
     * - User exists
     * - User has an e-mail address in their profile
     * - E-mail service is correctly configured.
     *
     * @param context Non null.
     * @param request Non null.
     * @param realm Used as part of user lookup.
     * @param restSecurity Non null.
     */
    private Promise<ActionResponse, ResourceException> generateNewPasswordEmail(final ServerContext context,
            final ActionRequest request, final String realm, final RestSecurity restSecurity) {
        JsonValue result = new JsonValue(new LinkedHashMap<String, Object>(1));
        final JsonValue jsonBody = request.getContent();

        try {

            // Check to make sure forgotPassword enabled
            if (restSecurity == null) {
                if (debug.warningEnabled()) {
                    debug.warning("Rest Security not created. restSecurity = " + restSecurity);
                }
                throw ResourceException.getException(ResourceException.UNAVAILABLE, "Rest Security Service not created");
            }
            if (!restSecurity.isForgotPassword()) {
                if (debug.warningEnabled()) {
                    debug.warning("Forgot Password set to : " + restSecurity.isForgotPassword());
                }
                throw ResourceException.getException(ResourceException.UNAVAILABLE, "Forgot password is not accessible.");
            }

            // Generate Admin Token
            SSOToken adminToken = getSSOToken(RestUtils.getToken().getTokenID().toString());

            Map<String, Set<String>> searchAttributes = getIdentityServicesAttributes(realm);
            searchAttributes.putAll(getAttributeFromRequest(jsonBody));

            List searchResults = identityServices.search(null, searchAttributes, adminToken);

            if (searchResults.isEmpty()) {
                throw new NotFoundException("User not found");

            } else if (searchResults.size() > 1) {
                throw new ConflictException("Multiple users found");

            } else {
                String username = (String) searchResults.get(0);

                IdentityDetails identityDetails = identityServices.read(username,
                        getIdentityServicesAttributes(realm), adminToken);

                String email = null;
                String uid = null;
                for (Map.Entry<String, Set<String>> attribute : asMap(identityDetails.getAttributes()).entrySet()) {
                    String attributeName = attribute.getKey();
                    if (MAIL.equalsIgnoreCase(attributeName)) {
                        if (attribute.getValue() != null && !attribute.getValue().isEmpty()) {
                            email = attribute.getValue().iterator().next();
                        }
                    } else if (UNIVERSAL_ID.equalsIgnoreCase(attributeName)) {
                        if (attribute.getValue() != null && !attribute.getValue().isEmpty()) {
                            uid = attribute.getValue().iterator().next();
                        }
                    }
                }
                // Check to see if user is Active/Inactive
                if (!isUserActive(uid)) {
                    throw new ForbiddenException("Request is forbidden for this user");
                }
                // Check if email is provided
                if (email == null || email.isEmpty()) {
                    throw new BadRequestException("No email provided in profile.");
                }

                // Get full deployment URL
                HttpContext header = context.asContext(HttpContext.class);
                StringBuilder deploymentURL = RestUtils.getFullDeploymentURI(header.getPath());

                String subject = jsonBody.get("subject").asString();
                String message = jsonBody.get("message").asString();

                // Retrieve email registration token life time
                if (restSecurity == null) {
                    if (debug.warningEnabled()) {
                        debug.warning("Rest Security not created. restSecurity = " + restSecurity);
                    }
                    throw new NotFoundException("Rest Security Service not created");
                }
                Long tokenLifeTime = restSecurity.getForgotPassTLT();

                // Generate Token
                org.forgerock.openam.cts.api.tokens.Token ctsToken = generateToken(email, username, tokenLifeTime, realm);

                // Store token in datastore
                CTSHolder.getCTS().createAsync(ctsToken);

                // Create confirmationId
                String confirmationId = Hash.hash(
                        ctsToken.getTokenId() + username + SystemProperties.get(AM_ENCRYPTION_PWD));

                // Build Confirmation URL
                String confURL = restSecurity.getForgotPasswordConfirmationUrl();
                StringBuilder confURLBuilder = new StringBuilder(100);
                if (confURL == null || confURL.isEmpty()) {
                    confURLBuilder.append(deploymentURL.append("/json/confirmation/forgotPassword").toString());
                } else {
                    confURLBuilder.append(confURL);
                }
                String confirmationLink = confURLBuilder.append("?confirmationId=").append(requestParamEncode(confirmationId))
                        .append("&tokenId=").append(requestParamEncode(ctsToken.getTokenId()))
                        .append("&username=").append(requestParamEncode(username))
                        .append("&realm=").append(realm)
                        .toString();

                // Send Registration
                sendNotification(email, subject, message, realm, confirmationLink);

                String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);

                if (debug.messageEnabled()) {
                    debug.message("IdentityResource.generateNewPasswordEmail :: ACTION of generate new password email " +
                        " for username " + username + " in realm " + realm + " performed by " + principalName);
                }
            }
            return newResultPromise(newActionResponse(result));
        } catch (NotFoundException e) {
            debug.warning("Could not find user", e);
            return newExceptionPromise(adapt(e));
        } catch (ResourceException re) {
            // Service not available, Neither or both Username/Email provided, User inactive
            debug.warning(re.getMessage(), re);
            return newExceptionPromise(re);
        } catch (Exception e) {
            // Intentional - all other errors are considered Internal Error.
            debug.error("Internal error", e);
            return newExceptionPromise(ResourceException.getException(ResourceException.INTERNAL_ERROR, "Failed to send mail", e));
        }
    }

    private Map<String, Set<String>> getAttributeFromRequest(JsonValue jsonBody) throws BadRequestException {
        String username = jsonBody.get(USERNAME).asString();
        String email = jsonBody.get(EMAIL).asString();

        if (username != null && email != null) {
            throw new BadRequestException("Both username and email specified - only one allowed in request.");
        }

        if (username != null && !username.isEmpty()) {
            return Collections.singletonMap(UNIVERSAL_ID_ABBREV, Collections.singleton(username));
        }

        if (email != null && !email.isEmpty()) {
            return Collections.singletonMap(MAIL, Collections.singleton(email));
        }

        throw new BadRequestException("Username or email not provided in request");
    }

    /**
     * Perform an anonymous update of a user's password using the provided token.
     *
     * The token must match a token placed in the CTS in order for the request
     * to proceed.
     *
     * @param context Non null
     * @param request Non null
     * @param realm Non null
     */
    private Promise<ActionResponse, ResourceException> anonymousUpdate(final ServerContext context,
            final ActionRequest request, final String realm) {
        final String tokenID;
        String confirmationId;
        String username;
        String nwpassword;
        final JsonValue jVal = request.getContent();

        try{
            tokenID = jVal.get(TOKEN_ID).asString();
            jVal.remove(TOKEN_ID);
            confirmationId = jVal.get(CONFIRMATION_ID).asString();
            jVal.remove(CONFIRMATION_ID);
            username = jVal.get(USERNAME).asString();
            nwpassword =  jVal.get("userpassword").asString();

            if(username == null || username.isEmpty()){
                throw new BadRequestException("username not provided");
            }
            if(nwpassword == null || username.isEmpty()) {
                throw new BadRequestException("new password not provided");
            }

            validateToken(tokenID, realm, username, confirmationId);

            // update Identity
            SSOToken admin = RestUtils.getToken();

            // Update instance with new password value
            return updateInstance(admin, jVal, realm)
                    .thenAsync(new AsyncFunction<ActionResponse, ActionResponse, ResourceException>() {
                        @Override
                        public Promise<ActionResponse, ResourceException> apply(ActionResponse response) {
                            // Only remove the token if the update was successful, errors will be set in the handler.
                            try {
                                // Even though the generated token will eventually timeout, delete it after a successful read
                                // so that the reset password request cannot be made again using the same token.
                                CTSHolder.getCTS().deleteAsync(tokenID);
                            } catch (DeleteFailedException e) {
                                // Catch this rather than letting it stop the process as it is possible that between successfully
                                // reading and deleting, the token has expired.
                                if (debug.messageEnabled()) {
                                    debug.message("Deleting token " + tokenID + " after a successful " +
                                            "read failed due to " + e.getMessage(), e);
                                }
                            } catch (CoreTokenException cte) { // For any unexpected CTS error
                                debug.error("Error performing anonymousUpdate", cte);
                                return newExceptionPromise(ResourceException.getException(ResourceException.INTERNAL_ERROR, cte.getMessage(), cte));
                            }
                            return newResultPromise(response);
                        }
                    });
        } catch (BadRequestException bre) { // For any malformed request.
            debug.warning("Bad request received for anonymousUpdate " + bre.getMessage());
            return newExceptionPromise(adapt(bre));
        } catch (CoreTokenException cte) { // For any unexpected CTS error
            debug.error("Error performing anonymousUpdate", cte);
            return newExceptionPromise(ResourceException.getException(ResourceException.INTERNAL_ERROR, cte.getMessage(), cte));
        } catch (NotFoundException nfe) {
            debug.message("Unable to find token for anonymousUpdate " + nfe.getMessage());
            return newExceptionPromise(ResourceException.getException(HttpURLConnection.HTTP_GONE, nfe.getMessage(), nfe));
        }
    }

    /**
     * Updates an instance given a JSON object with User Attributes
     * @param admin Token that has administrative privileges
     * @param details Json Value containing details of user identity
     * @return A successful promise if the update was successful
     */
    private Promise<ActionResponse, ResourceException> updateInstance(SSOToken admin, final JsonValue details,
            final String realm) {
        JsonValue jVal = details;
        IdentityDetails newDtls;
        String resourceId = jVal.get(USERNAME).asString();

        try {
            newDtls = jsonValueToIdentityDetails(jVal, realm);

            if (newDtls.getAttributes() == null || newDtls.getAttributes().length < 1) {
                throw new BadRequestException("Illegal arguments: One or more required arguments is null or empty");
            }

            newDtls.setName(resourceId);

            // update resource with new details
            identityServices.update(newDtls, admin);
            debug.message("IdentityResource.updateInstance :: Anonymous UPDATE in realm " + realm + " for " +
                    resourceId);
            // read updated identity back to client
            IdentityDetails checkIdent = identityServices.read(resourceId, getIdentityServicesAttributes(realm),
                    admin);
            // handle updated resource
            return newResultPromise(newActionResponse(identityDetailsToJsonValue(checkIdent)));
        }  catch (ResourceException e) {
            debug.warning("IdentityResource.updateInstance() :: Cannot UPDATE! " + e);
            return newExceptionPromise(e);
        } catch (final Exception exception) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE! " + exception);
            return newExceptionPromise(newNotFoundException(exception.getMessage(), exception));
        }
    }


    private Promise<ActionResponse, ResourceException> anonymousCreate(final ServerContext context,
            final ActionRequest request, final String realm, RestSecurity restSecurity) {

        final JsonValue jVal = request.getContent();
        String tokenID = null;
        String confirmationId;
        String email;

        try{
            if (!restSecurity.isSelfRegistration()) {
                throw new BadRequestException("Self-registration disabled");
            }

            tokenID = jVal.get(TOKEN_ID).asString();
            jVal.remove(TOKEN_ID);
            confirmationId = jVal.get(CONFIRMATION_ID).asString();
            jVal.remove(CONFIRMATION_ID);
            email = jVal.get(EMAIL).asString();

            if (email == null || email.isEmpty()) {
                throw new BadRequestException("Email not provided");
            }
            // Convert to IDRepo Attribute schema
            jVal.put("mail", email);

            if (confirmationId == null || confirmationId.isEmpty()) {
                throw new BadRequestException("confirmationId not provided");
            }

            if (tokenID == null || tokenID.isEmpty()) {
                throw new BadRequestException("tokenId not provided");
            }

            validateToken(tokenID, realm, email, confirmationId);

            // create an Identity
            SSOToken admin = RestUtils.getToken();
            final String finalTokenID = tokenID;
            return createInstance(admin, jVal, realm)
                    .thenAsync(new AsyncFunction<ActionResponse, ActionResponse, ResourceException>() {
                        @Override
                        public Promise<ActionResponse, ResourceException> apply(ActionResponse response) {
                            // Only remove the token if the create was successful, errors will be set in the handler.
                            try {
                                // Even though the generated token will eventually timeout, delete it after a successful read
                                // so that the completed registration request cannot be made again using the same token.
                                CTSHolder.getCTS().deleteAsync(finalTokenID);
                            } catch (DeleteFailedException e) {
                                // Catch this rather than letting it stop the process as it is possible that between successfully
                                // reading and deleting, the token has expired.
                                if (debug.messageEnabled()) {
                                    debug.message("IdentityResource.anonymousCreate: Deleting token " + finalTokenID +
                                            " after a successful read failed due to " + e.getMessage(), e);
                                }
                            } catch (CoreTokenException cte) { // For any unexpected CTS error
                                debug.error("IdentityResource.anonymousCreate(): CTS Error : " + cte.getMessage());
                                return newExceptionPromise(ResourceException.getException(ResourceException.INTERNAL_ERROR, cte.getMessage(), cte));
                            }
                            return newResultPromise(response);
                        }
                    });
        } catch (BadRequestException be){
            debug.warning("IdentityResource.anonymousCreate() :: Invalid Parameter " + be);
            return newExceptionPromise(adapt(be));
        } catch (NotFoundException nfe){
            debug.warning("IdentityResource.anonymousCreate(): Invalid tokenID : " + tokenID);
            return newExceptionPromise(adapt(nfe));
        } catch (CoreTokenException cte){ // For any unexpected CTS error
            debug.error("IdentityResource.anonymousCreate(): CTS Error : " + cte.getMessage());
            return newExceptionPromise(ResourceException.getException(ResourceException.INTERNAL_ERROR, cte.getMessage(), cte));
        } catch (ServiceNotFoundException e) {
            // Failure from RestSecurity
            debug.error("Internal error", e);
            return newExceptionPromise(ResourceException.getException(ResourceException.INTERNAL_ERROR, e.getMessage(), e));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ActionResponse, ResourceException> actionInstance(final ServerContext context,
            final String resourceId, final ActionRequest request) {
        return newExceptionPromise(newNotSupportedException("Actions are not supported for resource instances"));
    }

    /**
     * Creates an a resource using a privileged token
     * @param admin Token that has administrative privileges
     * @param details resource details that needs to be created
     * @return A successful promise if the create was successful
     */
    private Promise<ActionResponse, ResourceException> createInstance(SSOToken admin, final JsonValue details,
            final String realm) {

        JsonValue jVal = details;
        IdentityDetails identity = jsonValueToIdentityDetails(jVal, realm);
        final String resourceId = identity.getName();

        return attemptResourceCreation(realm, admin, identity, resourceId)
                .thenAsync(new AsyncFunction<IdentityDetails, ActionResponse, ResourceException>() {
                    @Override
                    public Promise<ActionResponse, ResourceException> apply(IdentityDetails dtls) {
                        if (dtls != null) {
                            debug.message("IdentityResource.createInstance :: Anonymous CREATE in realm "
                                    + realm + " for " + resourceId);

                            return newResultPromise(newActionResponse(identityDetailsToJsonValue(dtls)));
                        } else {
                            return newExceptionPromise(newNotFoundException(resourceId + " not found"));
                        }
                    }
                });
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ResourceResponse, ResourceException> createInstance(final ServerContext context,
            final CreateRequest request) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        final String realm = realmContext.getResolvedRealm();

        try {
            // anyone can create an account add
            SSOToken admin = getSSOToken(getCookieFromServerContext(context));

            final JsonValue jVal = request.getContent();
            String resourceId = request.getNewResourceId();

            IdentityDetails identity = jsonValueToIdentityDetails(jVal, realm);
            // check to see if request has included resource ID
            if (resourceId != null) {
                if (identity.getName() != null) {
                    if (!resourceId.equalsIgnoreCase(identity.getName())) {
                        ResourceException be = new BadRequestException("id in path does not match id in request body");
                        debug.error("IdentityResource.createInstance() :: Cannot CREATE ", be);
                        return newExceptionPromise(be);
                    }
                }
                identity.setName(resourceId);
            } else {
                resourceId = identity.getName();
            }

            final String id = resourceId;
            return attemptResourceCreation(realm, admin, identity, resourceId)
                    .thenAsync(new AsyncFunction<IdentityDetails, ResourceResponse, ResourceException>() {
                        @Override
                        public Promise<ResourceResponse, ResourceException> apply(IdentityDetails dtls) {
                            if (dtls != null) {
                                String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);
                                debug.message("IdentityResource.createInstance :: CREATE of " + id + " in realm "
                                        + realm + " performed by " + principalName);

                                return newResultPromise(newResourceResponse(id, "0", identityDetailsToJsonValue(dtls)));
                            } else {
                                return newExceptionPromise(newNotFoundException("Identity not found"));
                            }
                        }
                    });
        } catch (SSOException e) {
            debug.error("IdentityResource.createInstance() :: Cannot CREATE! " + e);
            return newExceptionPromise(newNotFoundException(e.getMessage(), e));
        }
    }

    private Promise<IdentityDetails, ResourceException> attemptResourceCreation(String realm, SSOToken admin,
            IdentityDetails identity, String resourceId) {
        IdentityDetails dtls = null;

        try {
            identityServices.create(identity, admin);
            dtls = identityServices.read(resourceId, getIdentityServicesAttributes(realm), admin);
            if (debug.messageEnabled()) {
                debug.message("IdentityResource.createInstance() :: Created " + resourceId + " in realm " + realm +
                        " by Admin with ID: " + admin.getTokenID().toString());
            }
        } catch (final ObjectNotFound notFound) {
            debug.error("IdentityResource.createInstance() :: Cannot READ " +
                    resourceId + ": Resource cannot be found." + notFound);
            return newExceptionPromise(newNotFoundException("Resource not found.", notFound));
        } catch (final TokenExpired tokenExpired) {
            debug.error("IdentityResource.createInstance() :: Cannot CREATE " +
                    resourceId + ":" + tokenExpired);
            return newExceptionPromise(adapt(new PermanentException(401, "Unauthorized", null)));
        } catch (final NeedMoreCredentials needMoreCredentials) {
            debug.error("IdentityResource.createInstance() :: Cannot CREATE " +
                    needMoreCredentials);
            return newExceptionPromise(adapt(new ForbiddenException("Token is not authorized", needMoreCredentials)));
        } catch (ResourceException e) {
            debug.warning("IdentityResource.createInstance() :: Cannot CREATE! " + e);
            return newExceptionPromise(e);
        } catch (final Exception exception) {
            debug.error("IdentityResource.createInstance() :: Cannot CREATE! " +
                    exception);
            return newExceptionPromise(newNotFoundException(exception.getMessage(), exception));
        }
        return newResultPromise(dtls);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ResourceResponse, ResourceException> deleteInstance(final ServerContext context,
            final String resourceId, final DeleteRequest request) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        final String realm = realmContext.getResolvedRealm();


        JsonValue result = new JsonValue(new LinkedHashMap<String, Object>(1));
        ResourceResponse resource;
        IdentityDetails dtls;

        try {
            SSOToken admin = getSSOToken(getCookieFromServerContext(context));

            // read to see if resource is available to user
            dtls = identityServices.read(resourceId, getIdentityServicesAttributes(realm), admin);

            // delete the resource
            identityServices.delete(dtls, admin);
            String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);
            debug.message("IdentityResource.deleteInstance :: DELETE of " + resourceId + " in realm " + realm +
                    " performed by " + principalName);

            result.put("success", "true");
            resource = newResourceResponse(resourceId, "0", result);
            return newResultPromise(resource);

        } catch (final NeedMoreCredentials ex) {
            debug.error("IdentityResource.deleteInstance() :: Cannot DELETE " +
                    resourceId + ": User does not have enough privileges.");
            return newExceptionPromise(adapt(new ForbiddenException(resourceId, ex)));
        } catch (final ObjectNotFound notFound) {
            debug.error("IdentityResource.deleteInstance() :: Cannot DELETE " +
                    resourceId + ":" + notFound);
            return newExceptionPromise(newNotFoundException("Resource cannot be found.", notFound));
        } catch (final TokenExpired tokenExpired) {
            debug.error("IdentityResource.deleteInstance() :: Cannot DELETE " +
                    resourceId + ":" + tokenExpired);
            return newExceptionPromise(adapt(new PermanentException(401, "Unauthorized", null)));
        } catch (final AccessDenied accessDenied) {
            debug.error("IdentityResource.deleteInstance() :: Cannot DELETE " +
                    resourceId + ":" + accessDenied);
            return newExceptionPromise(adapt(new ForbiddenException(accessDenied.getMessage(), accessDenied)));
        } catch (final GeneralFailure generalFailure) {
            debug.error("IdentityResource.deleteInstance() :: Cannot DELETE " +
                    generalFailure.getMessage());
            return newExceptionPromise(newBadRequestException(generalFailure.getMessage(), generalFailure));
        } catch (ForbiddenException ex) {
            debug.warning("IdentityResource.deleteInstance() :: Cannot DELETE " + resourceId
                    + ": User does not have enough privileges.");
            return newExceptionPromise(adapt(new ForbiddenException(resourceId, ex)));
        } catch (NotFoundException notFound) {
            debug.warning("IdentityResource.deleteInstance() :: Cannot DELETE " + resourceId + ":" + notFound);
            return newExceptionPromise(adapt(new NotFoundException("Resource cannot be found.", notFound)));
        } catch (ResourceException exception) {
            debug.warning("IdentityResource.deleteInstance() :: Cannot DELETE! " + exception.getMessage());
            result.put("success", "false");
            resource = newResourceResponse(resourceId, "0", result);
            return newResultPromise(resource);
        } catch (Exception exception) {
            debug.error("IdentityResource.deleteInstance() :: Cannot DELETE! " + exception.getMessage());
            result.put("success", "false");
            resource = newResourceResponse(resourceId, "0", result);
            return newResultPromise(resource);
        }
    }

    /**
     * Returns a JsonValue containing appropriate identity details
     *
     * @param details The IdentityDetails of a Resource
     * @return The JsonValue Object
     */
    private JsonValue identityDetailsToJsonValue(IdentityDetails details) {
        JsonValue result = new JsonValue(new LinkedHashMap<String, Object>(1));
        try {
            result.put(USERNAME, details.getName());
            result.put("realm", details.getRealm());
            Map<String, Set<String>> attrs = asMap(details.getAttributes());

            for (Map.Entry<String, Set<String>>aix : attrs.entrySet()) {
                result.put(aix.getKey(), aix.getValue());
            }
            return result;
        } catch (final Exception e) {
            throw new JsonValueException(result);
        }
    }

    /**
     * Returns an IdentityDetails from a JsonValue
     *
     * @param jVal The JsonValue Object to be converted
     * @return The IdentityDetails object
     */
    private IdentityDetails jsonValueToIdentityDetails(final JsonValue jVal, final String realm) {

        IdentityDetails identity = new IdentityDetails();
        Map<String, Set<String>> identityAttrList = new HashMap<>();

        try {
            identity.setType(userType); //set type ex. user
            identity.setRealm(realm); //set realm
            identity.setName(jVal.get(USERNAME).asString());//set name from JsonValue object

            try {
                for (String s : jVal.keys()) {
                    JsonValue childValue = jVal.get(s);
                    if (childValue.isString()) {
                        identityAttrList.put(s, Collections.singleton(childValue.asString()));
                    } else if (childValue.isList()) {
                        ArrayList<String> tList = (ArrayList<String>) childValue.getObject();
                        identityAttrList.put(s, new HashSet<>(tList));
                    }
                }
            } catch (Exception e) {
                debug.error("IdentityResource.jsonValueToIdentityDetails() :: " +
                        "Cannot Traverse JsonValue" + e);
            }
            identity.setAttributes(asAttributeArray(identityAttrList));

        } catch (final Exception e) {
            debug.error("IdentityResource.jsonValueToIdentityDetails() ::" +
                    " Cannot convert JsonValue to IdentityDetails." + e);
            //deal with better exceptions
        }
        return identity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ResourceResponse, ResourceException> patchInstance(final ServerContext context,
            final String resourceId, final PatchRequest request) {
        return newExceptionPromise(newNotSupportedException("Patch operations are not supported"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<QueryResponse, ResourceException> queryCollection(final ServerContext context,
            final QueryRequest request, final QueryResourceHandler handler) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        final String realm = realmContext.getResolvedRealm();

        String queryFilter;

        try {
            SSOToken admin = getSSOToken(getCookieFromServerContext(context));
            // This will only return 1 user..
            // getQueryFilter() is not implemented yet..returns dummy false value
            queryFilter = request.getQueryId();
            if (queryFilter == null || queryFilter.isEmpty()) {
                queryFilter = "*";
            }
            List<String> users = identityServices.search(queryFilter, getIdentityServicesAttributes(realm), admin);
            String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);
            debug.message("IdentityResource.queryCollection :: QUERY performed on realm " + realm +
                    " by " + principalName);

            for (final String user : users) {
                JsonValue val = new JsonValue(user);
                ResourceResponse resource = newResourceResponse(user, "0", val);
                handler.handleResource(resource);
            }
        } catch (Exception ex) {

        }

        return newResultPromise(newQueryResponse());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<ResourceResponse, ResourceException> readInstance(final ServerContext context,
            final String resourceId, final ReadRequest request) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        final String realm = realmContext.getResolvedRealm();

        IdentityDetails dtls;
        ResourceResponse resource;

        try {
            SSOToken admin = getSSOToken(getCookieFromServerContext(context));
            dtls = identityServices.read(resourceId, getIdentityServicesAttributes(realm), admin);
            String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);
            debug.message("IdentityResource.readInstance :: READ of " + resourceId + " in realm " + realm +
                    " performed by " + principalName);
            resource = newResourceResponse(resourceId, "0", addRoleInformation(context, resourceId, identityDetailsToJsonValue(dtls)));
            return newResultPromise(resource);
        } catch (final NeedMoreCredentials needMoreCredentials) {
            debug.error("IdentityResource.readInstance() :: Cannot READ " +
                    resourceId + ":" + needMoreCredentials);
            return newExceptionPromise(adapt(new ForbiddenException("User does not have enough privileges.", needMoreCredentials)));
        } catch (final ObjectNotFound objectNotFound) {
            debug.error("IdentityResource.readInstance() :: Cannot READ " +
                    resourceId + ":" + objectNotFound);
            return newExceptionPromise(newNotFoundException("Resource cannot be found.", objectNotFound));
        } catch (final TokenExpired tokenExpired) {
            debug.error("IdentityResource.readInstance() :: Cannot READ " +
                    resourceId + ":" + tokenExpired);
            return newExceptionPromise(adapt(new PermanentException(401, "Unauthorized", null)));
        } catch (final AccessDenied accessDenied) {
            debug.error("IdentityResource.readInstance() :: Cannot READ " +
                    resourceId + ":" + accessDenied);
            return newExceptionPromise(adapt(new ForbiddenException(accessDenied.getMessage(), accessDenied)));
        } catch (final GeneralFailure generalFailure) {
            debug.error("IdentityResource.readInstance() :: Cannot READ " +
                    generalFailure);
            return newExceptionPromise(newBadRequestException(generalFailure.getMessage(), generalFailure));
        } catch (final Exception exception) {
            debug.error("IdentityResource.readInstance() :: Cannot READ! " +
                    exception);
            return newExceptionPromise(newNotFoundException(exception.getMessage(), exception));
        }
    }

    private JsonValue addRoleInformation(ServerContext context, String resourceId, JsonValue value) {
        if (isAdmin(context) && authenticatedUserMatchesUserProfile(context, resourceId)) {
            value.put("roles", array("ui-admin"));
        }
        return value;
    }

    private boolean authenticatedUserMatchesUserProfile(ServerContext context, String resourceId) {
        try {
            SSOToken ssoToken = SSOTokenManager.getInstance().createSSOToken(getCookieFromServerContext(context));
            String requestRealm = coreWrapper.convertRealmNameToOrgName(context.asContext(RealmContext.class)
                    .getResolvedRealm());
            return resourceId.equalsIgnoreCase(ssoToken.getProperty("UserId"))
                    && requestRealm.equals(ssoToken.getProperty(Constants.ORGANIZATION));
        } catch (SSOException e) {
            debug.error("IdentityResource::Failed to determine if requesting user is accessing own profile");
        }
        return false;
    }

    @Override
    public Promise<ResourceResponse, ResourceException> updateInstance(final ServerContext context,
            final String resourceId, final UpdateRequest request) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        final String realm = realmContext.getResolvedRealm();

        final JsonValue jsonValue = request.getContent();
        final String rev = request.getRevision();
        IdentityDetails dtls, newDtls;
        ResourceResponse resource;
        try {
            SSOToken admin = getSSOToken(getCookieFromServerContext(context));
            // Retrieve details about user to be updated
            dtls = identityServices.read(resourceId, getIdentityServicesAttributes(realm), admin);

            //check first if the password is modified as part of the update request, so if necessary, the password can
            //be removed from the IdentityDetails object.
            if (!isAdmin(context)) {
                for (String attrName : jsonValue.keys()) {
                    if ("userpassword".equalsIgnoreCase(attrName)) {
                        String newPassword = jsonValue.get(attrName).asString();
                        if (!StringUtils.isBlank(newPassword)) {
                            String oldPassword = RestUtils.getMimeHeaderValue(context, OLD_PASSWORD);
                            if (StringUtils.isBlank(oldPassword)) {
                                throw new BadRequestException("The old password is missing from the request");
                            }
                            //This is an end-user trying to change the password, so let's change the password by
                            //verifying that the provided old password is correct. We also remove the password from the
                            //list of attributes to prevent the administrative password reset via the update call.
                            jsonValue.remove(attrName);
                            IdentityRestUtils.changePassword(context, realm, resourceId, oldPassword, newPassword);
                        }
                        break;
                    }
                }
            }
            newDtls = jsonValueToIdentityDetails(jsonValue, realm);
            if (newDtls.getName() != null && !resourceId.equalsIgnoreCase(newDtls.getName())) {
                throw new BadRequestException("id in path does not match id in request body");
            }
            newDtls.setName(resourceId);

            // update resource with new details
            identityServices.update(newDtls, admin);
            // read updated identity back to client
            IdentityDetails checkIdent = identityServices.read(dtls.getName(),
                    getIdentityServicesAttributes(realm), admin);
            // handle updated resource
            resource = newResourceResponse(resourceId, "0", identityDetailsToJsonValue(checkIdent));
            return newResultPromise(resource);
        } catch (final ObjectNotFound onf) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE! " +
                    onf);
            return newExceptionPromise(newNotFoundException("Could not find the resource [ " + resourceId + " ] to update", onf));
        } catch (final NeedMoreCredentials needMoreCredentials) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE " +
                    resourceId + ":" + needMoreCredentials);
            return newExceptionPromise(adapt(new ForbiddenException("Token is not authorized", needMoreCredentials)));
        } catch (final TokenExpired tokenExpired) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE " +
                    resourceId + ":" + tokenExpired);
            return newExceptionPromise(adapt(new PermanentException(401, "Unauthorized", null)));
        } catch (final AccessDenied accessDenied) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE " +
                    resourceId + ":" + accessDenied);
            return newExceptionPromise(adapt(new ForbiddenException(accessDenied.getMessage(), accessDenied)));
        } catch (final GeneralFailure generalFailure) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE " +
                    generalFailure);
            return newExceptionPromise(newBadRequestException(generalFailure.getMessage(), generalFailure));
        }  catch (NotFoundException e) {
            debug.warning("IdentityResource.updateInstance() :: Cannot UPDATE! " + e);
            return newExceptionPromise(newNotFoundException("Could not find the resource [ " + resourceId + " ] to update",
                    e));
        } catch (ResourceException re) {
            debug.warning("IdentityResource.updateInstance() :: Cannot UPDATE! " + resourceId + ":" + re);
            return newExceptionPromise(re);
        } catch (SSOException e) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE " +
                    resourceId + ":" + e);
            return newExceptionPromise(adapt(new ForbiddenException(e.getMessage(), e)));
        }catch (final Exception exception) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE! " +
                    exception);
            return newExceptionPromise(newNotFoundException(exception.getMessage(), exception));
        }
    }

    private Map<String, Set<String>> getIdentityServicesAttributes(String realm) {
        Map<String, Set<String>> identityServicesAttributes = new HashMap<>();
        identityServicesAttributes.put("objecttype", Collections.singleton(userType));
        identityServicesAttributes.put("realm", Collections.singleton(realm));
        return identityServicesAttributes;
    }

    private String requestParamEncode(String toEncode) throws UnsupportedEncodingException {
        if (toEncode != null && !toEncode.isEmpty()) {
            return URLEncoder.encode(toEncode, "UTF-8").replace("+", "%20");
        } else {
            return toEncode;
        }
    }
}
