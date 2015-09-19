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
 * Copyright 2013-2015 ForgeRock AS.
 */

package org.forgerock.openam.core.rest.session;

import static org.forgerock.json.resource.test.assertj.AssertJActionResponseAssert.assertThat;
import static org.forgerock.openam.core.rest.session.SessionResource.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.iplanet.sso.SSOException;
import com.iplanet.sso.SSOToken;
import com.iplanet.sso.SSOTokenID;
import com.iplanet.sso.SSOTokenManager;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoException;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.AdviceContext;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.services.context.ClientContext;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.services.context.Context;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.openam.authentication.service.AuthUtilsWrapper;
import org.forgerock.openam.core.rest.session.query.SessionQueryManager;
import org.forgerock.openam.rest.resource.SSOTokenContext;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SessionResourceTest {

    private SessionResource sessionResource;

    private SessionQueryManager sessionQueryManager;
    private SSOTokenManager ssoTokenManager;
    private AuthUtilsWrapper authUtilsWrapper;

    private AMIdentity amIdentity;

    private String headerResponse;
    private String urlResponse;
    private String cookieResponse;

    @BeforeMethod
    public void setUp() throws IdRepoException {
        sessionQueryManager = mock(SessionQueryManager.class);
        ssoTokenManager = mock(SSOTokenManager.class);
        authUtilsWrapper = mock(AuthUtilsWrapper.class);
        headerResponse = null;
        urlResponse = null;
        cookieResponse = null;

        amIdentity = new AMIdentity(DN.valueOf("id=demo,dc=example,dc=com"), null);

        sessionResource = new SessionResource(sessionQueryManager, ssoTokenManager, authUtilsWrapper) {
            @Override
            AMIdentity getIdentity(SSOToken ssoToken) throws IdRepoException, SSOException {
                return amIdentity;
            }

            @Override
            String convertDNToRealm(String dn) {
                return "/";
            }

            @Override
            protected String getTokenIdFromHeader(Context context, String cookieName) {
                return headerResponse;
            }

            @Override
            protected String getTokenIdFromUrlParam(ActionRequest request) {
                return urlResponse;
            }

            @Override
            protected String getTokenIdFromCookie(Context context, String cookieName) {
                return cookieResponse;
            }
        };
    }

    @Test
    public void shouldUseSessionQueryManagerForAllSessionsQuery() {
        // Given
        String badger = "badger";
        String weasel = "weasel";

        SessionQueryManager mockManager = mock(SessionQueryManager.class);
        QueryRequest request = mock(QueryRequest.class);
        given(request.getQueryId()).willReturn(SessionResource.KEYWORD_ALL);
        QueryResourceHandler handler = mock(QueryResourceHandler.class);

        SessionResource resource = spy(new SessionResource(mockManager, null, null));
        List<String> list = Arrays.asList(badger, weasel);
        doReturn(list).when(resource).getAllServerIds();

        // When
        resource.queryCollection(null, request, handler);

        // Then
        List<String> result = Arrays.asList(badger, weasel);
        verify(mockManager, times(1)).getAllSessions(result);
    }

    @Test
    public void shouldQueryNamedServerInServerMode() {
        // Given
        String badger = "badger";

        SessionQueryManager mockManager = mock(SessionQueryManager.class);
        QueryResourceHandler mockHandler = mock(QueryResourceHandler.class);
        QueryRequest request = mock(QueryRequest.class);
        given(request.getQueryId()).willReturn(badger);

        SessionResource resource = spy(new SessionResource(mockManager, null, null));

        // When
        resource.queryCollection(null, request, mockHandler);

        // Then
        verify(resource, times(0)).getAllServerIds();

        List<String> result = Collections.singletonList(badger);
        verify(mockManager, times(1)).getAllSessions(result);
    }

    @Test
    public void actionCollectionShouldFailToValidateSessionWhenSSOTokenIdNotSet() {
        //Given
        final SSOTokenContext tokenContext = mock(SSOTokenContext.class);
        final Context context = ClientContext.newInternalClientContext(tokenContext);
        final ActionRequest request = mock(ActionRequest.class);

        given(request.getAction()).willReturn(VALIDATE_ACTION_ID);

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionCollection(context, request);

        //Then
        assertThat(promise).failedWithException().isInstanceOf(BadRequestException.class);
    }

    @Test
    public void actionCollectionShouldValidateSessionAndReturnTrueWhenSSOTokenValid() throws SSOException {
        //Given
        cookieResponse = "SSO_TOKEN_ID";
        final SSOTokenContext tokenContext = mock(SSOTokenContext.class);
        final Context context = ClientContext.newInternalClientContext(tokenContext);
        final ActionRequest request = mock(ActionRequest.class);
        final SSOToken ssoToken = mock(SSOToken.class);
        final SSOTokenID ssoTokenId = mock(SSOTokenID.class);

        given(request.getAction()).willReturn(VALIDATE_ACTION_ID);
        given(tokenContext.getCallerSSOToken(ssoTokenManager)).willReturn(ssoToken);
        given(ssoTokenManager.isValidToken(ssoToken)).willReturn(true);
        given(ssoToken.getTokenID()).willReturn(ssoTokenId);
        given(ssoTokenId.toString()).willReturn("SSO_TOKEN_ID");
        given(ssoTokenManager.createSSOToken(ssoTokenId.toString())).willReturn(ssoToken);

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionCollection(context, request);

        //Then
        assertThat(promise).succeeded().withContent().booleanAt("valid").isTrue();
        assertThat(promise).succeeded().withContent().stringAt("uid").isEqualTo("demo");
        assertThat(promise).succeeded().withContent().stringAt("realm").isEqualTo("/");
    }

    @Test
    public void actionCollectionShouldLogoutSessionAndReturnEmptyJsonObjectWhenSSOTokenValid() throws SSOException {
        //Given
        cookieResponse = "SSO_TOKEN_ID";
        final SSOTokenContext tokenContext = mock(SSOTokenContext.class);
        final Context context = ClientContext.newInternalClientContext(new AdviceContext(tokenContext, Collections.<String>emptySet()));
        final ActionRequest request = mock(ActionRequest.class);
        final SSOTokenID ssoTokenId = mock(SSOTokenID.class);

        given(request.getAction()).willReturn(LOGOUT_ACTION_ID);
        given(authUtilsWrapper.logout(ssoTokenId.toString(), null, null)).willReturn(true);

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionCollection(context, request);

        //Then
        assertThat(promise).succeeded().withContent().stringAt("result").isEqualTo("Successfully logged out");
    }

    @Test
    public void actionInstanceShouldValidateSessionAndReturnFalseWhenSSOTokenCreationThrowsException()
            throws SSOException {
        //Given
        final Context context = mock(Context.class);
        final String resourceId = "SSO_TOKEN_ID";
        final ActionRequest request = mock(ActionRequest.class);

        given(request.getAction()).willReturn(VALIDATE_ACTION_ID);
        doThrow(SSOException.class).when(ssoTokenManager).createSSOToken("SSO_TOKEN_ID");

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionInstance(context, resourceId, request);

        //Then
        assertThat(promise).succeeded().withContent().booleanAt("valid").isFalse();
    }

    @Test
    public void actionInstanceShouldValidateSessionAndReturnTrueWhenSSOTokenValid() throws SSOException {
        //Given
        final Context context = mock(Context.class);
        final String resourceId = "SSO_TOKEN_ID";
        final ActionRequest request = mock(ActionRequest.class);
        final SSOToken ssoToken = mock(SSOToken.class);
        final Principal principal = mock(Principal.class);

        given(request.getAction()).willReturn(VALIDATE_ACTION_ID);
        given(ssoTokenManager.createSSOToken("SSO_TOKEN_ID")).willReturn(ssoToken);
        given(ssoTokenManager.isValidToken(ssoToken)).willReturn(true);
        given(ssoToken.getPrincipal()).willReturn(principal);
        given(principal.getName()).willReturn("PRINCIPAL");

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionInstance(context, resourceId, request);

        //Then
        assertThat(promise).succeeded().withContent().booleanAt("valid").isTrue();
        assertThat(promise).succeeded().withContent().stringAt("uid").isEqualTo("demo");
        assertThat(promise).succeeded().withContent().stringAt("realm").isEqualTo("/");
    }

    @Test
    public void actionInstanceShouldBeActiveWhenSSOTokenValid() throws SSOException {
        //Given
        final Context context = mock(Context.class);
        final String resourceId = "SSO_TOKEN_ID";
        final ActionRequest request = mock(ActionRequest.class);
        final SSOToken ssoToken = mock(SSOToken.class);

        given(request.getAction()).willReturn(IS_ACTIVE_ACTION_ID);
        given(ssoTokenManager.retrieveValidTokenWithoutResettingIdleTime("SSO_TOKEN_ID")).willReturn(ssoToken);
        given(ssoTokenManager.isValidToken(ssoToken, false)).willReturn(true);

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionInstance(context, resourceId, request);

        //Then
        assertThat(promise).succeeded().withContent().booleanAt("active").isTrue();
    }

    @Test
    public void actionInstanceShouldRefreshWhenParameterPresentAndSSOTokenValid() throws SSOException {
        //Given
        final Context context = mock(Context.class);
        final String resourceId = "SSO_TOKEN_ID";
        final ActionRequest request = mock(ActionRequest.class);
        final SSOToken ssoToken = mock(SSOToken.class);

        given(request.getAction()).willReturn(IS_ACTIVE_ACTION_ID);
        given(request.getAdditionalParameter("refresh")).willReturn("true");
        given(ssoTokenManager.retrieveValidTokenWithoutResettingIdleTime("SSO_TOKEN_ID")).willReturn(ssoToken);
        given(ssoTokenManager.isValidToken(ssoToken, false)).willReturn(true);

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionInstance(context, resourceId, request);

        //Then
        verify(ssoTokenManager).refreshSession(ssoToken);
        assertThat(promise).succeeded();
    }

    @Test
    public void actionInstanceShouldBeInactiveWhenSSOTokenInvalid() throws SSOException {
        //Given
        final Context context = mock(Context.class);
        final String resourceId = "SSO_TOKEN_ID";
        final ActionRequest request = mock(ActionRequest.class);
        final SSOToken ssoToken = mock(SSOToken.class);

        given(request.getAction()).willReturn(IS_ACTIVE_ACTION_ID);
        given(ssoTokenManager.retrieveValidTokenWithoutResettingIdleTime("SSO_TOKEN_ID")).willReturn(ssoToken);
        given(ssoTokenManager.isValidToken(ssoToken, false)).willReturn(false);

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionInstance(context, resourceId, request);

        //Then
        assertThat(promise).succeeded().withContent().booleanAt("active").isFalse();
    }

    @Test
    public void actionInstanceShouldGiveTimeLeftWhenSSOTokenValid() throws SSOException {

        final int TIME_LEFT = 5000;

        //Given
        final Context context = mock(Context.class);
        final String resourceId = "SSO_TOKEN_ID";
        final ActionRequest request = mock(ActionRequest.class);
        final SSOToken ssoToken = mock(SSOToken.class);

        given(request.getAction()).willReturn(GET_MAX_TIME_ACTION_ID);
        given(ssoTokenManager.retrieveValidTokenWithoutResettingIdleTime("SSO_TOKEN_ID")).willReturn(ssoToken);
        given(ssoTokenManager.isValidToken(ssoToken, false)).willReturn(true);
        given(ssoToken.getTimeLeft()).willReturn((long) TIME_LEFT);

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionInstance(context, resourceId, request);

        //Then
        assertThat(promise).succeeded().withContent().integerAt("maxtime").isEqualTo(TIME_LEFT);
    }

    @Test
    public void actionInstanceShouldGiveMinusOneForMaxTimeWhenSSOTokenInvalid() throws SSOException {
        //Given
        final Context context = mock(Context.class);
        final String resourceId = "SSO_TOKEN_ID";
        final ActionRequest request = mock(ActionRequest.class);
        final SSOToken ssoToken = mock(SSOToken.class);

        given(request.getAction()).willReturn(GET_MAX_TIME_ACTION_ID);
        given(ssoTokenManager.retrieveValidTokenWithoutResettingIdleTime("SSO_TOKEN_ID")).willReturn(ssoToken);
        given(ssoTokenManager.isValidToken(ssoToken, false)).willReturn(false);

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionInstance(context, resourceId, request);

        //Then
        assertThat(promise).succeeded().withContent().integerAt("maxtime").isEqualTo(-1);
    }

    @Test
    public void actionInstanceShouldGiveIdleTimeWhenSSOTokenValid() throws SSOException {

        final int IDLE = 50;

        //Given
        final Context context = mock(Context.class);
        final String resourceId = "SSO_TOKEN_ID";
        final ActionRequest request = mock(ActionRequest.class);
        final SSOToken ssoToken = mock(SSOToken.class);

        given(request.getAction()).willReturn(GET_IDLE_ACTION_ID);
        given(ssoTokenManager.retrieveValidTokenWithoutResettingIdleTime("SSO_TOKEN_ID")).willReturn(ssoToken);
        given(ssoTokenManager.isValidToken(ssoToken, false)).willReturn(true);
        given(ssoToken.getIdleTime()).willReturn((long) IDLE);

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionInstance(context, resourceId, request);

        //Then
        assertThat(promise).succeeded().withContent().integerAt("idletime").isEqualTo(IDLE);
    }

    @Test
    public void actionInstanceShouldGiveMinusOneForIdleTimeWhenSSOTokenInvalid() throws SSOException {
        //Given
        final Context context = mock(Context.class);
        final String resourceId = "SSO_TOKEN_ID";
        final ActionRequest request = mock(ActionRequest.class);
        final SSOToken ssoToken = mock(SSOToken.class);

        given(request.getAction()).willReturn(GET_IDLE_ACTION_ID);
        given(ssoTokenManager.retrieveValidTokenWithoutResettingIdleTime("SSO_TOKEN_ID")).willReturn(ssoToken);
        given(ssoTokenManager.isValidToken(ssoToken, false)).willReturn(false);

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionInstance(context, resourceId, request);

        //Then
        assertThat(promise).succeeded().withContent().integerAt("idletime").isEqualTo(-1);
    }

    @Test
    public void actionInstanceShouldReturnNotSupportedForUnknownAction() throws SSOException {
        //Given
        final Context context = mock(Context.class);
        final String resourceId = "SSO_TOKEN_ID";
        final ActionRequest request = mock(ActionRequest.class);

        given(request.getAction()).willReturn("unknown-action");

        //When
        Promise<ActionResponse, ResourceException> promise = sessionResource.actionInstance(context, resourceId, request);

        //Then
        assertThat(promise).failedWithException().isExactlyInstanceOf(NotSupportedException.class);
    }
}
