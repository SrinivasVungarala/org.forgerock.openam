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
package org.forgerock.openam.audit;

import static org.assertj.core.api.Assertions.fail;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.resource.Responses.newResourceResponse;
import static org.forgerock.openam.audit.AuditConstants.*;
import static org.forgerock.openam.utils.CollectionUtils.asSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.mockito.Mockito.*;

import org.forgerock.audit.AuditException;
import org.forgerock.audit.AuditService;
import org.forgerock.audit.events.AuditEvent;
import org.forgerock.audit.events.handlers.AuditEventHandler;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.openam.audit.configuration.AMAuditServiceConfiguration;
import org.forgerock.openam.audit.configuration.AuditServiceConfigurator;
import org.forgerock.util.promise.Promise;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * @since 13.0.0
 */
@SuppressWarnings("unchecked")
public class AuditEventPublisherTest {

    private AuditEventHandler mockHandler;
    private AuditEventPublisher auditEventPublisher;
    private AuditServiceConfigurator mockConfigurator;
    private AMAuditServiceConfiguration configuration;
    private ArgumentCaptor<JsonValue> auditEventCaptor;
    private Promise<ResourceResponse, Exception> dummyPromise;

    @BeforeMethod
    protected void setUp() throws AuditException {
        AuditService auditService = new AuditService();
        mockHandler = mock(AuditEventHandler.class);
        mockConfigurator = mock(AuditServiceConfigurator.class);
        configuration = new AMAuditServiceConfiguration();
        when(mockConfigurator.getAuditServiceConfiguration()).thenReturn(configuration);
        auditService.register(mockHandler, "handler", asSet("access"));
        auditEventPublisher = new AuditEventPublisher(auditService, mockConfigurator);
        auditEventCaptor = ArgumentCaptor.forClass(JsonValue.class);
        dummyPromise = newResultPromise(newResourceResponse("", "", json(object())));
    }

    @Test
    public void publishesProvidedAuditEventToAuditService() throws Exception {
        // Given
        AuditEvent auditEvent = new AMAccessAuditEventBuilder()
                .eventName(EventName.AM_ACCESS_ATTEMPT)
                .transactionId(UUID.randomUUID().toString())
                .authentication("id=amadmin,ou=user,dc=openam,dc=forgerock,dc=org")
                .client("172.16.101.7", 62375)
                .server("216.58.208.36", 80)
                .resourceOperation("/some/path", "CREST", "READ")
                .http("GET", "/some/path", "p1=v1&p2=v2", Collections.<String, List<String>>emptyMap())
                .response("200", 42)
                .toEvent();

        when(mockHandler.publishEvent(eq("access"), auditEventCaptor.capture())).thenReturn(dummyPromise);

        // When
        auditEventPublisher.publish("access", auditEvent);

        // Then
        assertThat(auditEventCaptor.getValue()).isEqualTo(auditEvent.getValue());
    }

    @Test
    public void shouldSuppressExceptionsOnPublish() {
        // Given
        AuditEvent auditEvent = new AMAccessAuditEventBuilder()
                .eventName(EventName.AM_ACCESS_ATTEMPT)
                .transactionId(UUID.randomUUID().toString())
                .authentication("id=amadmin,ou=user,dc=openam,dc=forgerock,dc=org")
                .client("172.16.101.7", 62375)
                .server("216.58.208.36", 80)
                .resourceOperation("/some/path", "CREST", "READ")
                .http("GET", "/some/path", "p1=v1&p2=v2", Collections.<String, List<String>>emptyMap())
                .response("200", 42)
                .toEvent();
        configuration.setAuditFailureSuppressed(true);

        // When
        try {
            auditEventPublisher.publish("unknownTopic", auditEvent);
        } catch (AuditException e) {
            fail("Audit exceptions should be suppressed when publish fails.");
        }
    }

    @Test(expectedExceptions = AuditException.class)
    public void shouldNotSuppressExceptionsOnPublish() throws AuditException {
        // Given
        AuditEvent auditEvent = new AMAccessAuditEventBuilder()
                .eventName(EventName.AM_ACCESS_ATTEMPT)
                .transactionId(UUID.randomUUID().toString())
                .authentication("id=amadmin,ou=user,dc=openam,dc=forgerock,dc=org")
                .client("172.16.101.7", 62375)
                .server("216.58.208.36", 80)
                .resourceOperation("/some/path", "CREST", "READ")
                .http("GET", "/some/path", "p1=v1&p2=v2", Collections.<String, List<String>>emptyMap())
                .response("200", 42)
                .toEvent();
        configuration.setAuditFailureSuppressed(false);

        // When
        auditEventPublisher.publish("unknownTopic", auditEvent);

        // Then
        // expect exception
    }
}
