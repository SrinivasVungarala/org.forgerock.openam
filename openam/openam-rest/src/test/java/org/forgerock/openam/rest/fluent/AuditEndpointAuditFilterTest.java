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
package org.forgerock.openam.rest.fluent;

import static org.mockito.Mockito.*;

import com.sun.identity.shared.debug.Debug;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

/**
 * @since 13.0.0
 */
public class AuditEndpointAuditFilterTest extends AbstractAuditFilterTest {

    private AuditEndpointAuditFilter auditEndpointAuditFilter;

    @BeforeMethod
    protected void setUp() throws Exception {
        super.setUp();
        AuditFilter auditFilter = new AuditFilter(mock(Debug.class), auditEventPublisher, auditEventFactory);
        auditEndpointAuditFilter = new AuditEndpointAuditFilter(auditFilter);
    }

    @SuppressWarnings("unchecked")
    @DataProvider(name = "auditedCrudpaqOperations")
    public Object[][] auditedCrudpaqOperations() throws IllegalAccessException, InstantiationException {
        return new Object[][] {
                {new Runnable() {
                    @Override
                    public void run() {
                        auditEndpointAuditFilter.filterRead(serverContext, readRequest, resultHandler, filterChain);
                    }
                }},
                {new Runnable() {
                    @Override
                    public void run() {
                        auditEndpointAuditFilter.filterUpdate(serverContext, updateRequest, resultHandler, filterChain);
                    }
                }},
                {new Runnable() {
                    @Override
                    public void run() {
                        auditEndpointAuditFilter.filterDelete(serverContext, deleteRequest, resultHandler, filterChain);
                    }
                }},
                {new Runnable() {
                    @Override
                    public void run() {
                        auditEndpointAuditFilter.filterPatch(serverContext, patchRequest, resultHandler, filterChain);
                    }
                }},
                {new Runnable() {
                    @Override
                    public void run() {
                        auditEndpointAuditFilter.filterAction(serverContext, actionRequest, resultHandler, filterChain);
                    }
                }},
                {new Runnable() {
                    @Override
                    public void run() {
                        auditEndpointAuditFilter.filterQuery(serverContext, queryRequest, queryResultHandler, filterChain);
                    }
                }}
        };
    }

    @SuppressWarnings("unchecked")
    @DataProvider(name = "unauditedCrudpaqOperations")
    public Object[][] unauditedCrudpaqOperations() throws IllegalAccessException, InstantiationException {
        return new Object[][] {
                {new Runnable() {
                    @Override
                    public void run() {
                        auditEndpointAuditFilter.filterCreate(serverContext, createRequest, resultHandler, filterChain);
                    }
                }}
        };
    }

}
