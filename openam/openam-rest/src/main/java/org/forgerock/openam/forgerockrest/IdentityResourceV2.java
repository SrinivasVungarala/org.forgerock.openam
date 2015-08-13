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
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openam.forgerockrest.RestUtils.getCookieFromServerContext;
import static org.forgerock.openam.forgerockrest.RestUtils.isAdmin;

import javax.mail.MessagingException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
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
import com.sun.identity.idm.AMIdentityRepository;
import com.sun.identity.idm.IdRepoException;
import com.sun.identity.idsvcs.AccessDenied;
import com.sun.identity.idsvcs.GeneralFailure;
import com.sun.identity.idsvcs.IdentityDetails;
import com.sun.identity.idsvcs.NeedMoreCredentials;
import com.sun.identity.idsvcs.ObjectNotFound;
import com.sun.identity.idsvcs.TokenExpired;
import com.sun.identity.idsvcs.opensso.GeneralAccessDeniedError;
import com.sun.identity.idsvcs.opensso.IdentityServicesImpl;
import com.sun.identity.shared.debug.Debug;
import com.sun.identity.shared.encode.Hash;
import com.sun.identity.sm.SMSException;
import com.sun.identity.sm.ServiceConfig;
import com.sun.identity.sm.ServiceConfigManager;
import com.sun.identity.sm.ServiceNotFoundException;
import org.apache.commons.lang.RandomStringUtils;
import org.forgerock.guice.core.InjectorHolder;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.resource.ActionRequest;
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
import org.forgerock.json.resource.QueryResultHandler;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.http.context.ServerContext;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.json.resource.http.HttpContext;
import org.forgerock.openam.core.CoreWrapper;
import org.forgerock.openam.cts.CTSPersistentStore;
import org.forgerock.openam.cts.exceptions.CoreTokenException;
import org.forgerock.openam.cts.exceptions.DeleteFailedException;
import org.forgerock.openam.forgerockrest.utils.MailServerLoader;
import org.forgerock.openam.forgerockrest.utils.PrincipalRestUtils;
import org.forgerock.openam.rest.resource.RealmContext;
import org.forgerock.openam.security.whitelist.ValidGotoUrlExtractor;
import org.forgerock.openam.services.RestSecurity;
import org.forgerock.openam.services.RestSecurityProvider;
import org.forgerock.openam.services.email.MailServer;
import org.forgerock.openam.services.email.MailServerImpl;
import org.forgerock.openam.shared.security.whitelist.RedirectUrlValidator;
import org.forgerock.openam.tokens.CoreTokenField;
import org.forgerock.openam.tokens.TokenType;
import org.forgerock.openam.utils.StringUtils;
import org.forgerock.openam.utils.TimeUtils;
import org.forgerock.util.Reject;

/**
 * A simple {@code Map} based collection resource provider.
 */
public final class IdentityResourceV2 implements CollectionResourceProvider {

    private static final String AM_ENCRYPTION_PWD = "am.encryption.pwd";

    private static final String SEND_NOTIF_TAG = "IdentityResource.sendNotification() :: ";
    private static Debug debug = Debug.getInstance("frRest");

    public static final String USER_TYPE = "user";
    public static final String GROUP_TYPE = "group";
    public static final String AGENT_TYPE = "agent";


    private final static RedirectUrlValidator<String> URL_VALIDATOR =
            new RedirectUrlValidator<String>(ValidGotoUrlExtractor.getInstance());

    // TODO: filters, sorting, paged results.

    private final String userType;
    private final RestSecurityProvider restSecurityProvider;

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
    final static String CURRENT_PASSWORD = "currentpassword";
    final static String USER_PASSWORD = "userpassword";

    private final MailServerLoader mailServerLoader;

    private final IdentityResourceV1 identityResourceV1;

    private final IdentityServicesImpl identityServices;

    /**
     * Creates a backend
     */
    public IdentityResourceV2(String userType, MailServerLoader mailServerLoader, IdentityServicesImpl identityServices,
            CoreWrapper coreWrapper, RestSecurityProvider restSecurityProvider) {
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
    IdentityResourceV2(String userType, ServiceConfigManager mailmgr, ServiceConfig mailscm,
            MailServerLoader mailServerLoader, IdentityServicesImpl identityServices, CoreWrapper coreWrapper,
            RestSecurityProvider restSecurityProvider) {
        this.userType = userType;
        this.mailmgr = mailmgr;
        this.mailscm = mailscm;
        this.mailServerLoader = mailServerLoader;
        this.restSecurityProvider = restSecurityProvider;
        this.identityResourceV1 = new IdentityResourceV1(userType, mailServerLoader, identityServices, coreWrapper,
                restSecurityProvider);
        this.identityServices = identityServices;
    }

    /**
     * Gets the user id from the session provided in the server context
     *
     * @param context Current Server Context
     * @param handler Result handler
     */
    private void idFromSession(final ServerContext context, final ResultHandler<JsonValue> handler) {

        JsonValue result = new JsonValue(new LinkedHashMap<String, Object>(1));
        SSOToken ssotok;
        AMIdentity amIdentity;

        try {
            SSOTokenManager mgr = SSOTokenManager.getInstance();
            ssotok = mgr.createSSOToken(getCookieFromServerContext(context));
            amIdentity = new AMIdentity(ssotok);

            // build resource
            result.put("id", amIdentity.getName());
            result.put("realm", getRelativeRealm(context, amIdentity));
            result.put("dn", amIdentity.getUniversalId());
            result.put("successURL", ssotok.getProperty(ISAuthConstants.SUCCESS_URL,false));
            result.put("fullLoginURL", ssotok.getProperty(ISAuthConstants.FULL_LOGIN_URL,false));
            if (debug.messageEnabled()) {
                debug.message("IdentityResource.idFromSession() :: Retrieved ID for user, " + amIdentity.getName());
            }
            handler.handleResult(result);

        } catch (SSOException e) {
            debug.error("IdentityResource.idFromSession() :: Cannot retrieve SSO Token. ", e);
            handler.handleError(new ForbiddenException("SSO Token cannot be retrieved.", e));
        } catch (IdRepoException ex) {
            debug.error("IdentityResource.idFromSession() :: Cannot retrieve user from IdRepo", ex);
            handler.handleError(new ForbiddenException("Cannot retrieve id from session.", ex));
        }
    }

    private String getRelativeRealm(ServerContext context, AMIdentity amIdentity) {
        RealmContext realmContext = context.asContext(RealmContext.class);

        String relativeRealm = realmContext.getRelativeRealm();

        /*
        Check the scenario when a user with an active session is redirected back to the XUI without the relative realm
        in the URI path. The XUI will fail to get the users profile unless we can determine what is the relative realm
        based from the users realm and the base realm from the realm context.
         */
        if ("/".equals(relativeRealm)) {
            String userRealm = com.sun.identity.sm.DNMapper.orgNameToRealmName(amIdentity.getRealm());
            String baseRealm = realmContext.getBaseRealm();
            if (userRealm.startsWith(baseRealm)) {
                String realm = userRealm.substring(baseRealm.length());
                if (!realm.startsWith("/")) {
                    realm = "/" + realm;
                }
                return realm;
            }
        }

        return relativeRealm;
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
     * @param handler Result handler
     */
    private void createRegistrationEmail(final ServerContext context, final ActionRequest request, final String realm,
            final RestSecurity restSecurity, final ResultHandler<JsonValue> handler){


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

            handler.handleResult(result);
        } catch (BadRequestException be) {
            if (debug.warningEnabled()) {
                debug.warning("IdentityResource.createRegistrationEmail(): Cannot send email to : " + emailAddress
                        + be.getMessage());
            }
            handler.handleError(be);
        } catch (NotFoundException nfe) {
            if (debug.warningEnabled()) {
                debug.warning("IdentityResource.createRegistrationEmail(): Cannot send email to : " + emailAddress, nfe);
            }
            handler.handleError(nfe);
        } catch (NotSupportedException nse) {
            if (debug.warningEnabled()) {
                debug.warning("IdentityResource.createRegistrationEmail(): Operation not enabled.", nse);
            }
            handler.handleError(nse);
        } catch (Exception e) {
            if (debug.errorEnabled()) {
                debug.error("IdentityResource.createRegistrationEmail(): Cannot send email to : " + emailAddress, e);
            }
            handler.handleError(new NotFoundException("Email not sent"));
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
     * Validates the current goto against the list of allowed gotos, and returns either the allowed
     * goto as sent in, or the server's default goto value.
     *
     * @param context Current Server Context
     * @param request Request from client to confirm registration
     * @param handler Result handler
     */
    private void validateGoto(final ServerContext context, final ActionRequest request,
            final ResultHandler<JsonValue> handler) {

        final JsonValue jVal = request.getContent();
        JsonValue result = new JsonValue(new LinkedHashMap<String, Object>(1));

        try {
            SSOTokenManager mgr = SSOTokenManager.getInstance();
            SSOToken ssoToken = mgr.createSSOToken(getCookieFromServerContext(context));

            String gotoURL = URL_VALIDATOR.getRedirectUrl(ssoToken.getProperty(ISAuthConstants.ORGANIZATION),
                    URL_VALIDATOR.getValueFromJson(jVal, RedirectUrlValidator.GOTO),
                    ssoToken.getProperty("successURL"));

            result.put("successURL", gotoURL);
            handler.handleResult(result);
        } catch (SSOException ssoe){
            if (debug.errorEnabled()) {
                debug.error("IdentityResource.validateGoto() :: Invalid SSOToken.", ssoe);
            }
            handler.handleError(ResourceException.getException(ResourceException.FORBIDDEN, ssoe.getMessage(), ssoe));
        }
    }

    /**
     * Will validate confirmationId is correct
     * @param request Request from client to confirm registration
     * @param handler Result handler
     */
    private void confirmationIdCheck(final ActionRequest request,
                                     final ResultHandler<JsonValue> handler, final String realm){
        final String METHOD = "IdentityResource.confirmationIdCheck";
        final JsonValue jVal = request.getContent();
        String tokenID;
        String confirmationId;
        String email;
        String username;
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

            handler.handleResult(result);

        } catch (BadRequestException be){
            debug.warning(METHOD + ": Cannot confirm registration/forgotPassword for : " + hashComponent, be);
            handler.handleError(be);
        } catch (Exception e){
            debug.error(METHOD + ": Cannot confirm registration/forgotPassword for : " + hashComponent, e);
            handler.handleError(new NotFoundException(e.getMessage()));
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
    public void actionCollection(final ServerContext context, final ActionRequest request,
                                 final ResultHandler<JsonValue> handler) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        final String realm = realmContext.getResolvedRealm();
        RestSecurity restSecurity = restSecurityProvider.get(realm);

        final String action = request.getAction();
        if (action.equalsIgnoreCase("idFromSession")) {
            idFromSession(context, handler);
        } else if (action.equalsIgnoreCase("register")){
            createRegistrationEmail(context,request, realm, restSecurity, handler);
        } else if (action.equalsIgnoreCase("confirm")) {
            confirmationIdCheck(request, handler, realm);
        } else if (action.equalsIgnoreCase("anonymousCreate")) {
            anonymousCreate(context, request, realm, handler, restSecurity);
        } else if (action.equalsIgnoreCase("forgotPassword")) {
            generateNewPasswordEmail(context, request, realm, restSecurity, handler);
        } else if (action.equalsIgnoreCase("forgotPasswordReset")) {
            anonymousUpdate(context, request, realm, handler);
        } else if (action.equalsIgnoreCase("validateGoto")) {
            validateGoto(context, request, handler);
        } else { // for now this is the only case coming in, so fail if otherwise
            RestUtils.generateUnsupportedOperation(handler);
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
     * @param handler Required for return response to caller.
     */
    private void generateNewPasswordEmail(final ServerContext context, final ActionRequest request, final String realm,
            final RestSecurity restSecurity, final ResultHandler<JsonValue> handler) {
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
            SSOToken adminToken = identityResourceV1.getSSOToken(RestUtils.getToken().getTokenID().toString());

            Map<String, Set<String>> searchAttributes = getIdentityServicesAttributes(realm);
            searchAttributes.putAll(getAttributeFromRequest(jsonBody));

            List<String> searchResults = identityServices.search(null, searchAttributes, adminToken);

            if (searchResults.isEmpty()) {
                throw new NotFoundException("User not found");

            } else if (searchResults.size() > 1) {
                throw new ConflictException("Multiple users found");

            } else {
                String username = searchResults.get(0);

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
                org.forgerock.openam.cts.api.tokens.Token ctsToken = generateToken(email, username,
                        tokenLifeTime, realm);

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
            handler.handleResult(result);
        } catch (ResourceException re) {
            // Service not available, Neither or both Username/Email provided, User inactive
            debug.warning(re.getMessage(), re);
            handler.handleError(re);
        } catch (Exception e) {
            // Intentional - all other errors are considered Internal Error.
            debug.error("Internal error", e);
            handler.handleError(ResourceException.getException(ResourceException.INTERNAL_ERROR, "Failed to send mail", e));
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
     * @param handler Non null
     */
    private void anonymousUpdate(final ServerContext context, final ActionRequest request, final String realm,
            final ResultHandler<JsonValue> handler) {
        String tokenID;
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
            if (updateInstance(admin, jVal, realm, handler)) {
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
                }
            }
        } catch (BadRequestException bre){ // For any malformed request.
            debug.warning("Bad request received for anonymousUpdate " + bre.getMessage());
            handler.handleError(bre);
        } catch (CoreTokenException cte){ // For any unexpected CTS error
            debug.error("Error performing anonymousUpdate", cte);
            handler.handleError(ResourceException.getException(ResourceException.INTERNAL_ERROR, cte.getMessage(), cte));
        } catch (NotFoundException nfe) {
            debug.message("Unable to find token for anonymousUpdate " + nfe.getMessage());
            handler.handleError(ResourceException.getException(HttpURLConnection.HTTP_GONE, nfe.getMessage(), nfe));
        }

    }

    /**
     * Updates an instance given a JSON object with User Attributes
     * @param admin Token that has administrative privileges
     * @param details Json Value containing details of user identity
     * @param handler handles result of operation
     * @return true if the update was successful
     */
    private boolean updateInstance(SSOToken admin, final JsonValue details, final String realm,
            final ResultHandler<JsonValue> handler) {
        JsonValue jVal = details;
        IdentityDetails newDtls;
        String resourceId = jVal.get(USERNAME).asString();

        boolean successfulUpdate = false;

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
            handler.handleResult(identityDetailsToJsonValue(checkIdent));
            successfulUpdate = true;
        } catch (final Exception exception) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE! " +
                    exception);
            handler.handleError(new NotFoundException(exception.getMessage(), exception));
        }

        return successfulUpdate;
    }


    private void anonymousCreate(final ServerContext context, final ActionRequest request, final String realm,
            final ResultHandler<JsonValue> handler, RestSecurity restSecurity) {

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
            jVal.put("mail",email);

            if (confirmationId == null || confirmationId.isEmpty()) {
                throw new BadRequestException("confirmationId not provided");
            }

            if (tokenID == null || tokenID.isEmpty()) {
                throw new BadRequestException("tokenId not provided");
            }

            validateToken(tokenID, realm, email, confirmationId);

            // create an Identity
            SSOToken admin = RestUtils.getToken();
            if (createInstance(admin, jVal, realm, handler)) {

                // Only remove the token if the create was successful, errors will be set in the handler.
                try {
                    // Even though the generated token will eventually timeout, delete it after a successful read
                    // so that the completed registration request cannot be made again using the same token.
                    CTSHolder.getCTS().deleteAsync(tokenID);
                } catch (DeleteFailedException e) {
                    // Catch this rather than letting it stop the process as it is possible that between successfully
                    // reading and deleting, the token has expired.
                    if (debug.messageEnabled()) {
                        debug.message("IdentityResource.anonymousCreate: Deleting token " + tokenID +
                                " after a successful read failed due to " + e.getMessage(), e);
                    }
                }
            }
        } catch (BadRequestException be){
            debug.warning("IdentityResource.anonymousCreate() :: Invalid Parameter " + be);
            handler.handleError(be);
        } catch (NotFoundException nfe){
            debug.warning("IdentityResource.anonymousCreate(): Invalid tokenID : " + tokenID);
            handler.handleError(nfe);
        } catch (CoreTokenException cte){ // For any unexpected CTS error
            debug.error("IdentityResource.anonymousCreate(): CTS Error : " + cte.getMessage());
            handler.handleError(ResourceException.getException(ResourceException.INTERNAL_ERROR, cte.getMessage(), cte));
        } catch (ServiceNotFoundException e) {
            // Failure from RestSecurity
            debug.error("Internal error", e);
            handler.handleError(ResourceException.getException(ResourceException.INTERNAL_ERROR, e.getMessage(), e));
            return;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionInstance(final ServerContext context, final String resourceId, final ActionRequest request,
            final ResultHandler<JsonValue> handler) {

        String action = request.getAction();

        if ("changePassword".equalsIgnoreCase(action)) {

            RealmContext realmContext = context.asContext(RealmContext.class);
            final String realm = realmContext.getResolvedRealm();

            JsonValue value = request.getContent();

            try {
                String userPassword = value.get(USER_PASSWORD).asString();
                if (StringUtils.isBlank(userPassword)) {
                    throw new BadRequestException("'" + USER_PASSWORD + "' attribute not set in JSON content.");
                }
                String currentPassword = value.get(CURRENT_PASSWORD).asString();
                if (StringUtils.isBlank(currentPassword)) {
                    throw new BadRequestException("'" + CURRENT_PASSWORD + "' attribute not set in JSON content.");
                }

                IdentityRestUtils.changePassword(context, realm, resourceId, currentPassword, userPassword);

                if (debug.messageEnabled()) {
                    String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);
                    debug.message("IdentityResource.actionInstance :: ACTION of change password for " + resourceId
                            + " in realm " + realm + " performed by " + principalName);
                }
                handler.handleResult(json(object()));
            } catch (ResourceException re) {
                debug.warning("Cannot change password! " + resourceId + ":" + re);
                handler.handleError(re);
            }
        } else {
            handler.handleError(new NotSupportedException(action + " not supported for resource instances"));
        }
    }

    /**
     * Creates an a resource using a privileged token
     * @param admin Token that has administrative privileges
     * @param details resource details that needs to be created
     * @param handler handles result of operation
     * @return true if the create was successful
     *
     */
    private boolean createInstance(SSOToken admin, final JsonValue details, final String realm,
            final ResultHandler<JsonValue> handler) {

        JsonValue jVal = details;
        IdentityDetails identity = jsonValueToIdentityDetails(jVal, realm);
        String resourceId = identity.getName();

        boolean successfulCreate = false;

        IdentityDetails dtls = attemptResourceCreation(handler, realm, admin, identity, resourceId);

        if (dtls != null) {
            debug.message("IdentityResource.createInstance :: Anonymous CREATE in realm " + realm + " for " +
                    resourceId);

            handler.handleResult(identityDetailsToJsonValue(dtls));
            successfulCreate = true;
        }

        return successfulCreate;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void createInstance(final ServerContext context, final CreateRequest request,
                               final ResultHandler<Resource> handler) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        final String realm = realmContext.getResolvedRealm();

        try {
            // anyone can create an account add
            SSOToken admin = identityResourceV1.getSSOToken(getCookieFromServerContext(context));

            final JsonValue jVal = request.getContent();
            String resourceId = request.getNewResourceId();

            IdentityDetails identity = jsonValueToIdentityDetails(jVal, realm);
            // check to see if request has included resource ID
            if (resourceId != null) {
                if (identity.getName() != null) {
                    if (!resourceId.equalsIgnoreCase(identity.getName())) {
                        ResourceException be = new BadRequestException("id in path does not match id in request body");
                        debug.warning("IdentityResource.createInstance() :: Cannot CREATE ", be);
                        handler.handleError(be);
                        return;
                    }
                }
                identity.setName(resourceId);
            } else {
                resourceId = identity.getName();
            }

            IdentityDetails dtls = attemptResourceCreation(handler, realm, admin, identity, resourceId);

            if (dtls != null) {
                String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);
                debug.message("IdentityResource.createInstance :: CREATE of " + resourceId + " in realm " + realm +
                        " performed by " + principalName);

                Resource resource = new Resource(resourceId, "0", identityDetailsToJsonValue(dtls));
                handler.handleResult(resource);
            }
        } catch (SSOException e) {
            handler.handleError(new ForbiddenException(e));
        }
    }

    private IdentityDetails attemptResourceCreation(ResultHandler<?> handler, String realm, SSOToken admin,
            IdentityDetails identity, String resourceId) {

        IdentityDetails dtls = null;

        try {

            // Create the resource
            identityServices.create(identity, admin);
            // Read created resource
            dtls = identityServices.read(resourceId, getIdentityServicesAttributes(realm), admin);
            if (debug.messageEnabled()) {
                debug.message("IdentityResource.createInstance() :: Created " + resourceId + " in realm " + realm +
                        " by Admin with ID: " + admin.getTokenID().toString());
            }

        } catch (final ObjectNotFound notFound) {
            debug.error("IdentityResource.createInstance() :: Cannot READ " +
                    resourceId + ": Resource cannot be found." + notFound);
            handler.handleError(new NotFoundException("Resource not found.", notFound));
        } catch (final TokenExpired tokenExpired) {
            debug.error("IdentityResource.createInstance() :: Cannot CREATE " + resourceId + ":" + tokenExpired);
            handler.handleError(new PermanentException(401, "Unauthorized", null));
        } catch (final NeedMoreCredentials needMoreCredentials) {
            debug.error("IdentityResource.createInstance() :: Cannot CREATE " + needMoreCredentials);
            handler.handleError(new ForbiddenException("Token is not authorized", needMoreCredentials));
        } catch (final GeneralAccessDeniedError accessDenied) {
            debug.error("IdentityResource.createInstance() :: Cannot CREATE " + accessDenied);
            handler.handleError(ResourceException.getException(ResourceException.FORBIDDEN));
        } catch (GeneralFailure generalFailure) {
            debug.error("IdentityResource.createInstance() :: Cannot CREATE " +
                    generalFailure);
            handler.handleError(new BadRequestException("Resource cannot be created: "
                    + generalFailure.getMessage(), generalFailure));
        } catch (AccessDenied accessDenied) {
            debug.error("IdentityResource.createInstance() :: Cannot CREATE " +
                    accessDenied);
            handler.handleError(new ForbiddenException("Token is not authorized: "
                    + accessDenied.getMessage(), accessDenied));
        } catch (ResourceException e) {
            debug.warning("IdentityResource.createInstance() :: Cannot CREATE " + e);
            handler.handleError(e);
        } catch (final Exception exception) {
            debug.error("IdentityResource.createInstance() :: Cannot CREATE! " + exception);
            handler.handleError(new NotFoundException(exception.getMessage(), exception));
        }

            return dtls;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteInstance(final ServerContext context, final String resourceId, final DeleteRequest request,
            final ResultHandler<Resource> handler) {
        identityResourceV1.deleteInstance(context, resourceId, request, handler);
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

            for (Map.Entry<String, Set<String>> aix : attrs.entrySet()) {
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
    public void patchInstance(final ServerContext context, final String resourceId, final PatchRequest request,
            final ResultHandler<Resource> handler) {
        identityResourceV1.patchInstance(context, resourceId, request, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void queryCollection(final ServerContext context, final QueryRequest request,
            final QueryResultHandler handler) {
        identityResourceV1.queryCollection(context, request, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readInstance(final ServerContext context, final String resourceId, final ReadRequest request,
            final ResultHandler<Resource> handler) {
        identityResourceV1.readInstance(context, resourceId, request, handler);
    }

    private boolean checkValidPassword(String username, char[] password, String realm) throws BadRequestException {
        if (username == null || password == null) {
            throw new BadRequestException("Invalid Username or Password");
        }
        try {
            Callback[] callbacks = new Callback[2];
            NameCallback nc = new NameCallback("dummy");
            nc.setName(username);
            callbacks[0] = nc;
            PasswordCallback pc = new PasswordCallback("dummy", false);
            pc.setPassword(password);
            callbacks[1] = pc;
            AMIdentityRepository idRepo = new AMIdentityRepository(null, realm);
            return idRepo.authenticate(callbacks);
        } catch (Exception ex) {
            if (debug.messageEnabled()) {
                debug.message("Failed to verify password for user: " + username + " due to: " + ex.getMessage());
            }
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateInstance(final ServerContext context, final String resourceId, final UpdateRequest request,
            final ResultHandler<Resource> handler) {

        RealmContext realmContext = context.asContext(RealmContext.class);
        final String realm = realmContext.getResolvedRealm();


        final JsonValue jVal = request.getContent();
        final String rev = request.getRevision();
        IdentityDetails dtls, newDtls;
        Resource resource;
        try {
            SSOToken admin = identityResourceV1.getSSOToken(getCookieFromServerContext(context));
            // Retrieve details about user to be updated
            dtls = identityServices.read(resourceId, getIdentityServicesAttributes(realm), admin);
            // Continue modifying the identity if read success

            for (String key : jVal.keys()) {
                if ("userpassword".equalsIgnoreCase(key)) {
                    handler.handleError(new BadRequestException("Cannot update user password via PUT. "
                            + "Use POST with _action=changePassword or _action=forgotPassword."));
                    return;
                }
            }

            newDtls = jsonValueToIdentityDetails(jVal, realm);

            if (newDtls.getAttributes() == null || newDtls.getAttributes().length < 1) {
                throw new BadRequestException("Illegal arguments: One or more required arguments is null or empty");
            }

            if (newDtls.getName() != null && !resourceId.equalsIgnoreCase(newDtls.getName())){
                throw new BadRequestException("id in path does not match id in request body");
            }
            newDtls.setName(resourceId);

            // Handle attribute change when password is required
            // Get restSecurity for this realm
            RestSecurity restSecurity = restSecurityProvider.get(realm);
            // Make sure user is not admin and check to see if we are requiring a password to change any attributes
            Set<String> protectedUserAttributes = restSecurity.getProtectedUserAttributes();
            if (protectedUserAttributes != null && !isAdmin(context)) {
                boolean hasReauthenticated = false;
                for (String protectedAttr : protectedUserAttributes) {
                    JsonValue jValAttr = jVal.get(protectedAttr);
                    if(!jValAttr.isNull()){
                        // If attribute is not available set newAttr variable to empty string for use in comparison
                        String newAttr = (jValAttr.isString()) ? jValAttr.asString() : "";
                        // Get the value of current attribute
                        String currentAttr = "";
                        Map<String, Set<String>> attrs = asMap(dtls.getAttributes());
                        for (Map.Entry<String, Set<String>> attribute : attrs.entrySet()) {
                            String attributeName = attribute.getKey();
                            if(protectedAttr.equalsIgnoreCase(attributeName)){
                                currentAttr = attribute.getValue().iterator().next();
                            }
                        }
                        // Compare newAttr and currentAttr
                        if (!currentAttr.equals(newAttr) && !hasReauthenticated) {
                            // check header to make sure that password is there then check to see if it's correct
                            String strCurrentPass = RestUtils.getMimeHeaderValue(context, CURRENT_PASSWORD);
                            if (strCurrentPass != null && !strCurrentPass.isEmpty() &&
                                    checkValidPassword(resourceId, strCurrentPass.toCharArray(), realm)) {
                                //set a boolean value so we know reauth has been done
                                hasReauthenticated = true;
                                //continue will allow attribute(s) change(s)
                            } else {
                                throw new BadRequestException("Must provide a valid confirmation password to change " +
                                        "protected attribute (" + protectedAttr + ") from '" + currentAttr + "' to '" +
                                        newAttr + "'");
                            }
                        }
                    }
                }
            }

            // update resource with new details
            identityServices.update(newDtls, admin);
            String principalName = PrincipalRestUtils.getPrincipalNameFromServerContext(context);
            debug.message("IdentityResource.updateInstance :: UPDATE of " + resourceId + " in realm " + realm +
                    " performed by " + principalName);
            // read updated identity back to client
            IdentityDetails checkIdent = identityServices.read(dtls.getName(),
                    getIdentityServicesAttributes(realm), admin);
            // handle updated resource
            resource = new Resource(resourceId, "0", identityDetailsToJsonValue(checkIdent));
            handler.handleResult(resource);
        } catch (final ObjectNotFound onf) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE! " +
                    onf);
            handler.handleError(new NotFoundException("Could not find the resource [ " + resourceId + " ] to update", onf));
        } catch (final NeedMoreCredentials needMoreCredentials) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE " +
                    resourceId + ":" + needMoreCredentials);
            handler.handleError(new ForbiddenException("Token is not authorized", needMoreCredentials));
        } catch (final TokenExpired tokenExpired) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE " +
                    resourceId + ":" + tokenExpired);
            handler.handleError(new PermanentException(401, "Unauthorized", null));
        } catch (final AccessDenied accessDenied) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE " +
                    resourceId + ":" + accessDenied);
            handler.handleError(new ForbiddenException(accessDenied.getMessage(), accessDenied));
        } catch (final GeneralFailure generalFailure) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE " +
                    generalFailure);
            handler.handleError(new BadRequestException(generalFailure.getMessage(), generalFailure));
        } catch (BadRequestException bre){
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE! "
                    + resourceId + ":" + bre);
            handler.handleError(bre);
        } catch (NotFoundException e) {
            debug.warning("IdentityResource.updateInstance() :: Cannot UPDATE! " + e);
            handler.handleError(new NotFoundException("Could not find the resource [ " + resourceId + " ] to update",
                    e));
        } catch (ResourceException e) {
            debug.warning("IdentityResource.updateInstance() :: Cannot UPDATE! " + resourceId + ":" + e);
            handler.handleError(e);
        } catch (final Exception exception) {
            debug.error("IdentityResource.updateInstance() :: Cannot UPDATE! " +
                    exception);
            handler.handleError(new NotFoundException(exception.getMessage(), exception));
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
