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
 * Copyright 2013-2014 ForgeRock AS.
 */

package org.forgerock.openam.authz.filter;

import org.forgerock.auth.common.AuditLogger;
import org.forgerock.authz.AuthZFilter;
import org.forgerock.authz.AuthorizationLoggingConfigurator;
import org.forgerock.openam.rest.router.RestEndpointManager;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class RestAuthorizationDispatcherFilterTest {

    private RestAuthorizationDispatcherFilter restAuthorizationDispatcherFilter;
    private RestEndpointManager endpointManager;

    private AuthZFilter authZFilter;

    @BeforeMethod
    public void setUp() {

        authZFilter = mock(AuthZFilter.class);
        endpointManager = mock(RestEndpointManager.class);

        restAuthorizationDispatcherFilter = new RestAuthorizationDispatcherFilter(endpointManager, authZFilter);
    }

    @Test (expectedExceptions = ServletException.class)
    public void shouldThrowServletExceptionWhenRealmsAuthzConfiguratorNotSet() throws ServletException {

        //Given
        FilterConfig filterConfig = mock(FilterConfig.class);
        given(filterConfig.getInitParameter("usersAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("groupsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("agentsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());

        //When
        restAuthorizationDispatcherFilter.init(filterConfig);

        //Then
        fail();
    }

    @Test (expectedExceptions = ServletException.class)
    public void shouldThrowServletExceptionWhenUsersAuthzConfiguratorNotSet() throws ServletException {

        //Given
        FilterConfig filterConfig = mock(FilterConfig.class);
        given(filterConfig.getInitParameter("realmsAuthzConfigurator")).willReturn(AdminAuthzClass.class.getName());
        given(filterConfig.getInitParameter("groupsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("agentsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());

        //When
        restAuthorizationDispatcherFilter.init(filterConfig);

        //Then
        fail();
    }

    @Test (expectedExceptions = ServletException.class)
    public void shouldThrowServletExceptionWhenGroupsAuthzConfiguratorNotSet() throws ServletException {

        //Given
        FilterConfig filterConfig = mock(FilterConfig.class);
        given(filterConfig.getInitParameter("realmsAuthzConfigurator")).willReturn(AdminAuthzClass.class.getName());
        given(filterConfig.getInitParameter("usersAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("agentsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());

        //When
        restAuthorizationDispatcherFilter.init(filterConfig);

        //Then
        fail();
    }

    @Test (expectedExceptions = ServletException.class)
    public void shouldThrowServletExceptionWhenAgentsAuthzConfiguratorNotSet() throws ServletException {

        //Given
        FilterConfig filterConfig = mock(FilterConfig.class);
        given(filterConfig.getInitParameter("realmsAuthzConfigurator")).willReturn(AdminAuthzClass.class.getName());
        given(filterConfig.getInitParameter("usersAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("groupsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());

        //When
        restAuthorizationDispatcherFilter.init(filterConfig);

        //Then
        fail();
    }

    @Test (expectedExceptions = ServletException.class)
    public void shouldThrowServletExceptionIfRequestIsNotHttpServletRequest() throws ServletException, IOException {

        //Given
        FilterConfig filterConfig = mock(FilterConfig.class);
        given(filterConfig.getInitParameter("realmsAuthzConfigurator")).willReturn(AdminAuthzClass.class.getName());
        given(filterConfig.getInitParameter("usersAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("groupsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("agentsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());

        restAuthorizationDispatcherFilter.init(filterConfig);

        ServletRequest request = mock(ServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        fail();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldSkipAuthorizationIfEndpointNotFound() throws ServletException, IOException {

        //Given
        FilterConfig filterConfig = mock(FilterConfig.class);
        given(filterConfig.getInitParameter("realmsAuthzConfigurator")).willReturn(AdminAuthzClass.class.getName());
        given(filterConfig.getInitParameter("usersAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("groupsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("agentsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());

        restAuthorizationDispatcherFilter.init(filterConfig);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        given(endpointManager.findEndpoint(anyString())).willReturn(null);

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        verify(authZFilter, never()).init(Matchers.<FilterConfig>anyObject());
        verify(authZFilter, never()).doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    public void shouldFilterAuthorizationForRealms() throws ServletException, IOException {

        //Given
        FilterConfig filterConfig = mock(FilterConfig.class);
        given(filterConfig.getInitParameter("realmsAuthzConfigurator")).willReturn(AdminAuthzClass.class.getName());
        given(filterConfig.getInitParameter("usersAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("groupsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("agentsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());

        restAuthorizationDispatcherFilter.init(filterConfig);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        given(endpointManager.findEndpoint(anyString())).willReturn("/realms");

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        ArgumentCaptor<FilterConfig> filterConfigCaptor = ArgumentCaptor.forClass(FilterConfig.class);
        verify(authZFilter).init(filterConfigCaptor.capture());
        assertEquals(filterConfigCaptor.getValue().getInitParameter("module-configurator-factory-class"), AdminAuthzClass.class.getName());
        verify(authZFilter).doFilter(request, response, filterChain);
    }

    @Test
    public void shouldFilterAuthorizationForUsers() throws ServletException, IOException {

        //Given
        FilterConfig filterConfig = mock(FilterConfig.class);
        given(filterConfig.getInitParameter("realmsAuthzConfigurator")).willReturn(AdminAuthzClass.class.getName());
        given(filterConfig.getInitParameter("usersAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("groupsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("agentsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());

        restAuthorizationDispatcherFilter.init(filterConfig);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        given(endpointManager.findEndpoint(anyString())).willReturn("/users");

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        ArgumentCaptor<FilterConfig> filterConfigCaptor = ArgumentCaptor.forClass(FilterConfig.class);
        verify(authZFilter).init(filterConfigCaptor.capture());
        assertEquals(filterConfigCaptor.getValue().getInitParameter("module-configurator-factory-class"),
                PassthrouhgAuthzClass.class.getName());
        verify(authZFilter).doFilter(request, response, filterChain);
    }

    @Test
    public void shouldFilterAuthorizationForGroups() throws ServletException, IOException {

        //Given
        FilterConfig filterConfig = mock(FilterConfig.class);
        given(filterConfig.getInitParameter("realmsAuthzConfigurator")).willReturn(AdminAuthzClass.class.getName());
        given(filterConfig.getInitParameter("usersAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("groupsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("agentsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());

        restAuthorizationDispatcherFilter.init(filterConfig);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        given(endpointManager.findEndpoint(anyString())).willReturn("/groups");

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        ArgumentCaptor<FilterConfig> filterConfigCaptor = ArgumentCaptor.forClass(FilterConfig.class);
        verify(authZFilter).init(filterConfigCaptor.capture());
        assertEquals(filterConfigCaptor.getValue().getInitParameter("module-configurator-factory-class"),
                PassthrouhgAuthzClass.class.getName());
        verify(authZFilter).doFilter(request, response, filterChain);
    }

    @Test
    public void shouldFilterAuthorizationForAgents() throws ServletException, IOException {

        //Given
        FilterConfig filterConfig = mock(FilterConfig.class);
        given(filterConfig.getInitParameter("realmsAuthzConfigurator")).willReturn(AdminAuthzClass.class.getName());
        given(filterConfig.getInitParameter("usersAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("groupsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("agentsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());

        restAuthorizationDispatcherFilter.init(filterConfig);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        given(endpointManager.findEndpoint(anyString())).willReturn("/agents");

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        ArgumentCaptor<FilterConfig> filterConfigCaptor = ArgumentCaptor.forClass(FilterConfig.class);
        verify(authZFilter).init(filterConfigCaptor.capture());
        assertEquals(filterConfigCaptor.getValue().getInitParameter("module-configurator-factory-class"),
                PassthrouhgAuthzClass.class.getName());
        verify(authZFilter).doFilter(request, response, filterChain);
    }

    @Test
    public void shouldFilterAuthorizationForOtherEndpoints() throws ServletException, IOException {

        //Given
        FilterConfig filterConfig = mock(FilterConfig.class);
        given(filterConfig.getInitParameter("realmsAuthzConfigurator")).willReturn(AdminAuthzClass.class.getName());
        given(filterConfig.getInitParameter("usersAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("groupsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());
        given(filterConfig.getInitParameter("agentsAuthzConfigurator"))
                .willReturn(PassthrouhgAuthzClass.class.getName());

        restAuthorizationDispatcherFilter.init(filterConfig);

        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        given(endpointManager.findEndpoint(anyString())).willReturn("/other");

        //When
        restAuthorizationDispatcherFilter.doFilter(request, response, filterChain);

        //Then
        verify(authZFilter, never()).init(Matchers.<FilterConfig>anyObject());
        verify(authZFilter, never()).doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    public void shouldDestroyFilter() {

        //Given

        //When
        restAuthorizationDispatcherFilter.destroy();

        //Then
    }

    static final class AdminAuthzClass implements AuthorizationLoggingConfigurator {

        public AuditLogger<HttpServletRequest> getAuditLogger() {
            return null;
        }
    }

    static final class PassthrouhgAuthzClass implements AuthorizationLoggingConfigurator {

        public AuditLogger<HttpServletRequest> getAuditLogger() {
            return null;
        }
    }
}
