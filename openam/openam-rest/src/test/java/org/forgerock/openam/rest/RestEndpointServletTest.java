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

package org.forgerock.openam.rest;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.reset;
import static org.mockito.Mockito.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

import org.forgerock.openam.rest.service.RestletServiceServlet;
import org.mockito.Matchers;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class RestEndpointServletTest {

    private RestEndpointServlet restEndpointServlet;

    private RestletServiceServlet restletXACMLServiceServlet;
    private RestletServiceServlet restletOAuth2ServiceServlet;
    private RestletServiceServlet restletUMAServiceServlet;

    @BeforeClass
    public void setupMocks() {
        restletXACMLServiceServlet = mock(RestletServiceServlet.class);
        restletOAuth2ServiceServlet = mock(RestletServiceServlet.class);
        restletUMAServiceServlet = mock(RestletServiceServlet.class);
    }

    @BeforeMethod
    public void setUp() {

        reset(restletXACMLServiceServlet);
        reset(restletOAuth2ServiceServlet);
        reset(restletUMAServiceServlet);

        restEndpointServlet = new RestEndpointServlet(restletXACMLServiceServlet, restletOAuth2ServiceServlet,
                restletUMAServiceServlet);
    }

    @Test
    public void shouldCallInit() throws ServletException {

        //Given

        //When
        restEndpointServlet.init();

        //Then
        verifyZeroInteractions(restletXACMLServiceServlet);
        verifyZeroInteractions(restletOAuth2ServiceServlet);
        verifyZeroInteractions(restletUMAServiceServlet);
    }

    @DataProvider(name = "restletPaths")
    public Object[][] restletPathData() {
        return new Object[][] {
                {"/xacml", restletXACMLServiceServlet},
                {"/oauth2", restletOAuth2ServiceServlet},
                {"/uma", restletUMAServiceServlet}
        };
    }

    @Test(dataProvider = "restletPaths")
    public void shouldHandleRequestWithRestletServlet(String path, RestletServiceServlet servlet) throws Exception {

        //Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        given(request.getServletPath()).willReturn(path);

        //When
        restEndpointServlet.service(request, response);

        //Then
        verify(servlet).service(Matchers.<HttpServletRequest>anyObject(), eq(response));
        for (RestletServiceServlet s : Arrays.asList(restletXACMLServiceServlet, restletOAuth2ServiceServlet,
                restletUMAServiceServlet)) {
            if (s != servlet) {
                verifyZeroInteractions(s);
            }
        }
    }

    @Test
    public void shouldCallDestroy() {

        //Given

        //When
        restEndpointServlet.destroy();

        //Then
        verify(restletXACMLServiceServlet).destroy();
        verify(restletOAuth2ServiceServlet).destroy();
        verify(restletUMAServiceServlet).destroy();
    }
}
