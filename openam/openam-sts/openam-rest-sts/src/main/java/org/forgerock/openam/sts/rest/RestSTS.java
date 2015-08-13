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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright 2013-2015 ForgeRock AS.
 */

package org.forgerock.openam.sts.rest;

import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.http.HttpContext;
import org.forgerock.openam.sts.TokenCancellationException;
import org.forgerock.openam.sts.TokenCreationException;
import org.forgerock.openam.sts.TokenMarshalException;
import org.forgerock.openam.sts.TokenValidationException;
import org.forgerock.openam.sts.rest.operation.cancel.IssuedTokenCancelOperation;
import org.forgerock.openam.sts.rest.operation.translate.TokenTranslateOperation;
import org.forgerock.openam.sts.rest.operation.validate.IssuedTokenValidateOperation;
import org.forgerock.openam.sts.rest.service.RestSTSServiceHttpServletContext;
import org.forgerock.openam.sts.user.invocation.RestSTSTokenCancellationInvocationState;
import org.forgerock.openam.sts.user.invocation.RestSTSTokenTranslationInvocationState;
import org.forgerock.openam.sts.user.invocation.RestSTSTokenValidationInvocationState;

/**
 * This is the top-level interface invoked directly by the REST-STS resource. Each of the methods defined in this interface
 * will correlate to the top-level operations defined in the REST-STS.
 */
public interface RestSTS extends TokenTranslateOperation, IssuedTokenValidateOperation, IssuedTokenCancelOperation {
    /**
     *
     * @param invocationState An object encapsulating the input and output token state specifications
     * @param httpContext The HttpContext
     * @param restSTSServiceHttpServletContext The RestSTSServiceHttpServletContext, which can be consulted to
     *                                         obtain the X509Certificate[] set by the container following a two-way-tls
     *                                         handshake
     * @return A JsonValue with a 'issued_token' key and the value corresponding to the issued token
     * @throws TokenValidationException if the input token could not be validated
     * @throws TokenCreationException if the desired token could not be produced
     * @throws TokenMarshalException if the invocation state was invalid
     */
    @Override
    JsonValue translateToken(RestSTSTokenTranslationInvocationState invocationState,
                                    HttpContext httpContext,
                                    RestSTSServiceHttpServletContext restSTSServiceHttpServletContext)
                                    throws TokenMarshalException, TokenValidationException, TokenCreationException;

    /**
     *
     * @param invocationState An object encapsulating the to-be-validated token state
     * @return A JsonValue with a 'token_valid' key with a value of either true or false
     * @throws TokenValidationException if the input token could not be validated
     * @throws TokenMarshalException if the invocation state was invalid
     */
    @Override
    JsonValue validateToken(RestSTSTokenValidationInvocationState invocationState)
            throws TokenMarshalException, TokenValidationException;

    /**
     *
     * @param invocationState The invocationState, as generated by the caller, containing the to-be-cancelled token state
     * @return a JsonValue indicating operation success
     * @throws org.forgerock.openam.sts.TokenMarshalException if the token state corresponding to the to-be-validated token was incorrect
     * @throws org.forgerock.openam.sts.TokenCancellationException if an exception occurred which prevented token cancellation from occurring
     */
    @Override
    JsonValue cancelToken(RestSTSTokenCancellationInvocationState invocationState) throws TokenMarshalException, TokenCancellationException;

}
