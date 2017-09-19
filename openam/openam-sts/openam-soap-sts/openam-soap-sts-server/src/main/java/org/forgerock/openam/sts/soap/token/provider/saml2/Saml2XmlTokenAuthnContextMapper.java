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
* Copyright 2014-2015 ForgeRock AS.
*/

package org.forgerock.openam.sts.soap.token.provider.saml2;

import org.apache.cxf.sts.request.ReceivedToken;
import org.apache.ws.security.handler.WSHandlerResult;

import java.util.List;

/**
 * Defines the functionality which maps a TokenType to a SAML2 AuthnContext value (see section 2.7.2.2
 * of http://docs.oasis-open.org/security/saml/v2.0/saml-core-2.0-os.pdf for details). This AuthnContext will
 * be sent to the TokenGenerationService for inclusion in the AuthnStatement of the issued assertion. It specifies
 * the manner in which the subject was authenticated. In the context of token transformation, the validated input
 * token will determine the AuthnContext specified in the TokenGenerationService invocation. This interface defines
 * the contract for this mapping.
 *
 */
public interface Saml2XmlTokenAuthnContextMapper {
    /**
     * Returns the SAML2 AuthnContext class reference corresponding the the SupportingToken specified in the SecurityPolicy
     * bindings protecting a soap-sts instance.
     * @param securityPolicyBindingTraversalYield The yield of SecurityPolicy binding verification, as generated by apache
     *                                            wss4j. See the DefaultSaml2XmlTokenAuthnContextMapper for details on
     *                                            how to parse the classes corresponding to this processing.
     * @return  @return A valid AuthnContext value, as defined here: http://docs.oasis-open.org/security/saml/v2.0/saml-authn-context-2.0-os.pdf
     */
    String getAuthnContext(List<WSHandlerResult> securityPolicyBindingTraversalYield);

    /**
     * Returns the SAML2 AuthnContext class reference for delegated tokens (specified as part of the ActAs/OnBehalfOf
     * element in the RequestSecurityToken). The {@code List<WSHandlerResult> } which is the yield of the SecurityPolicy binding
     * traversal, is only provided as additional context, as the manner in which the delegated token was secured may well
     * have bearing on the type of AuthnContext class ref chosen, though it is not examined for the default implementation.
     * @param securityPolicyBindingTraversalYield The yield of SecurityPolicy binding verification, as generated by apache
     *                                            wss4j. See the DefaultSaml2XmlTokenAuthnContextMapper for details on
     *                                            how to parse the classes corresponding to this processing.
     * @param delegatedToken The token provided as part of the ActAs/OnBehalfOf element specified in the RequestSecurityToken.
     * @return  @return A valid AuthnContext value, as defined here: http://docs.oasis-open.org/security/saml/v2.0/saml-authn-context-2.0-os.pdf
     */
    String getAuthnContextForDelegatedToken(List<WSHandlerResult> securityPolicyBindingTraversalYield, ReceivedToken delegatedToken);
}
