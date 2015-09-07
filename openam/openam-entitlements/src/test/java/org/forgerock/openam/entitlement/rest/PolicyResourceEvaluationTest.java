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

package org.forgerock.openam.entitlement.rest;

import com.sun.identity.entitlement.Entitlement;
import com.sun.identity.entitlement.EntitlementException;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.InternalContext;
import org.forgerock.json.resource.RequestType;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.http.Context;
import org.forgerock.openam.entitlement.guice.EntitlementRestGuiceModule;
import org.forgerock.openam.entitlement.rest.EntitlementsExceptionMappingHandler;
import org.forgerock.openam.entitlement.rest.PolicyAction;
import org.forgerock.openam.entitlement.rest.PolicyEvaluator;
import org.forgerock.openam.entitlement.rest.PolicyEvaluatorFactory;
import org.forgerock.openam.entitlement.rest.PolicyParser;
import org.forgerock.openam.entitlement.rest.PolicyRequestFactory;
import org.forgerock.openam.entitlement.rest.PolicyResource;
import org.forgerock.openam.entitlement.rest.PolicyStoreProvider;
import org.forgerock.openam.entitlement.rest.model.json.PolicyRequest;
import org.forgerock.openam.forgerockrest.guice.ForgerockRestGuiceModule;
import org.forgerock.openam.rest.RealmContext;
import org.forgerock.openam.rest.resource.SubjectContext;
import org.forgerock.util.promise.Promise;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.security.auth.Subject;
import java.util.Arrays;
import java.util.List;

import static org.forgerock.json.JsonValue.array;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.forgerock.json.resource.test.assertj.AssertJActionResponseAssert.assertThat;

/**
* Unit test for the evaluation logic within {@link PolicyResource}.
*
* @since 12.0.0
*/
public class PolicyResourceEvaluationTest {

    @Mock
    private PolicyEvaluatorFactory factory;
    @Mock
    private PolicyRequestFactory requestFactory;
    @Mock
    private PolicyEvaluator evaluator;
    @Mock
    private PolicyParser parser;
    @Mock
    private ActionRequest request;
    @Mock
    private SubjectContext subjectContext;
    @Mock
    private PolicyRequest policyRequest;

    private Subject restSubject;
    private Subject policySubject;

    private PolicyResource policyResource;

    @BeforeMethod
    public void setupMocks() throws Exception {
        MockitoAnnotations.initMocks(this);
        restSubject = new Subject();
        policySubject = new Subject();

        // Use a real error handler as this is a core part of the functionality we are testing and doesn't need to be mocked
        EntitlementsExceptionMappingHandler resourceErrorHandler =
                new EntitlementsExceptionMappingHandler(EntitlementRestGuiceModule.getEntitlementsErrorHandlers());

        policyResource = new PolicyResource(factory, requestFactory, parser,
                mock(PolicyStoreProvider.class), resourceErrorHandler);
    }

    @Test
    public void shouldMakeBatchEvaluation() throws EntitlementException {
        // Given...
        given(request.getAction()).willReturn("evaluate");

        Context context = buildContextStructure("/abc");
        given(requestFactory.buildRequest(PolicyAction.EVALUATE, context, request)).willReturn(policyRequest);
        given(policyRequest.getRestSubject()).willReturn(restSubject);
        given(policyRequest.getApplication()).willReturn("some-application");
        given(factory.getEvaluator(restSubject, "some-application")).willReturn(evaluator);
        given(policyRequest.getApplication()).willReturn("some-application");
        given(policyRequest.getRealm()).willReturn("/abc");

        List<Entitlement> decisions = Arrays.asList(new Entitlement());
        given(evaluator.routePolicyRequest(policyRequest)).willReturn(decisions);

        JsonValue jsonDecision = JsonValue.json(array());
        given(parser.printEntitlements(decisions)).willReturn(jsonDecision);

        // When...
        Promise<ActionResponse, ResourceException> promise = policyResource.actionCollection(context, request);

        // Then...
        verify(request).getAction();
        verify(requestFactory).buildRequest(PolicyAction.EVALUATE, context, request);
        verify(policyRequest).getRestSubject();
        verify(policyRequest, times(2)).getApplication();
        verify(policyRequest).getRealm();
        verify(factory).getEvaluator(restSubject, "some-application");
        verify(evaluator).routePolicyRequest(policyRequest);
        verify(parser).printEntitlements(decisions);
        assertThat(promise).succeeded().withContent().isEqualTo(jsonDecision);
        verifyNoMoreInteractions(request, requestFactory,
                policyRequest, factory, evaluator, parser);
    }

    @Test
    public void shouldMakeTreeEvaluation() throws EntitlementException {
        // Given...
        given(request.getAction()).willReturn("evaluateTree");

        Context context = buildContextStructure("/abc");
        given(requestFactory.buildRequest(PolicyAction.TREE_EVALUATE, context, request)).willReturn(policyRequest);
        given(policyRequest.getRestSubject()).willReturn(restSubject);
        given(policyRequest.getApplication()).willReturn("some-application");
        given(factory.getEvaluator(restSubject, "some-application")).willReturn(evaluator);
        given(policyRequest.getApplication()).willReturn("some-application");
        given(policyRequest.getRealm()).willReturn("/abc");

        List<Entitlement> decisions = Arrays.asList(new Entitlement());
        given(evaluator.routePolicyRequest(policyRequest)).willReturn(decisions);

        JsonValue jsonDecision = JsonValue.json(array());
        given(parser.printEntitlements(decisions)).willReturn(jsonDecision);

        // When...
        Promise<ActionResponse, ResourceException> promise = policyResource.actionCollection(context, request);

        // Then...
        verify(request).getAction();
        verify(requestFactory).buildRequest(PolicyAction.TREE_EVALUATE, context, request);
        verify(policyRequest).getRestSubject();
        verify(policyRequest, times(2)).getApplication();
        verify(policyRequest).getRealm();
        verify(factory).getEvaluator(restSubject, "some-application");
        verify(evaluator).routePolicyRequest(policyRequest);
        verify(parser).printEntitlements(decisions);

        assertThat(promise).succeeded().withContent().isEqualTo(jsonDecision);
        verifyNoMoreInteractions(request, subjectContext, requestFactory,
                policyRequest, factory, evaluator, parser);
    }

    @Test
    public void shouldHandleEntitlementExceptions() throws EntitlementException {
        // Given...
        given(request.getAction()).willReturn("evaluate");

        Context context = buildContextStructure("/abc");
        EntitlementException eE = new EntitlementException(EntitlementException.INVALID_VALUE);
        given(requestFactory.buildRequest(PolicyAction.EVALUATE, context, request)).willThrow(eE);
        given(request.getRequestType()).willReturn(RequestType.ACTION);

        // When...
        Promise<ActionResponse, ResourceException> promise = policyResource.actionCollection(context, request);

        // Then...
        verify(request).getAction();
        verify(requestFactory).buildRequest(PolicyAction.EVALUATE, context, request);
        verify(request).getRequestType();

        assertThat(promise).failedWithException().isInstanceOf(BadRequestException.class);
        verifyNoMoreInteractions(request, requestFactory,
                policyRequest, factory, evaluator, parser);
    }

    @Test
    public void shouldHandleUnknownAction() {
        // Given...
        given(request.getAction()).willReturn("unknownAction");

        // When...
        Context context = buildContextStructure("/abc");
        policyResource.actionCollection(context, request);

        // Then...
        verify(request).getAction();

        //verify(jsonHandler).handleError(isA(NotSupportedException.class));
        verifyNoMoreInteractions(request, subjectContext, requestFactory,
                policyRequest, factory, evaluator, parser);

    }

    /**
     * Creates a server context hierarchy based on the passed realm.
     *
     * @param realm
     *         the realm
     *
     * @return the server context hierarchy
     */
    private Context buildContextStructure(final String realm) {
        return new InternalContext(new RealmContext(subjectContext));
    }

    /**
     * Given a resource exception verifies that the code matches the expected code.
     */
    private final static class ResourceExceptionMatcher extends BaseMatcher<ResourceException> {

        private final int expectedCode;

        public ResourceExceptionMatcher(final int expectedCode) {
            this.expectedCode = expectedCode;
        }

        @Override
        public boolean matches(final Object o) {
            if (!(o instanceof ResourceException)) {
                return false;
            }

            ResourceException exception = (ResourceException)o;
            return exception.getCode() == expectedCode;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("Expected resource exception code was " + expectedCode);
        }

    }
}
