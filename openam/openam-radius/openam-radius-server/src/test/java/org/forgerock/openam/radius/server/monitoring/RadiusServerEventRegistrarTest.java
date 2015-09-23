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
 * Copyrighted 2015 Intellectual Reserve, Inc (IRI)
 */
package org.forgerock.openam.radius.server.monitoring;

import org.testng.annotations.Test;

public class RadiusServerEventRegistrarTest {

    @Test(enabled = true)
  public void RadiusServerEventRegistrar() {
        final RadiusServerEventRegistrar eventRegistrar = new RadiusServerEventRegistrar();
        System.out.println("Registered name is " + eventRegistrar.getRegiseredName());
  }

    @Test(enabled = false)
  public void authRequestAccepted() {
    throw new RuntimeException("Test not implemented");
  }

    @Test(enabled = false)
  public void authRequestRejected() {
    throw new RuntimeException("Test not implemented");
  }

    @Test(enabled = false)
  public void getNumberOfAcceptedPackets() {
    throw new RuntimeException("Test not implemented");
  }

    @Test(enabled = false)
  public void getNumberOfAuthRequestsAccepted() {
    throw new RuntimeException("Test not implemented");
  }

    @Test(enabled = false)
  public void getNumberOfAuthRequestsRejected() {
    throw new RuntimeException("Test not implemented");
  }

    @Test(enabled = false)
  public void getNumberOfPacketsProcessed() {
    throw new RuntimeException("Test not implemented");
  }

    @Test(enabled = false)
  public void getNumberOfPacketsRecieved() {
    throw new RuntimeException("Test not implemented");
  }

    @Test(enabled = false)
  public void packetAccepted() {
    throw new RuntimeException("Test not implemented");
  }

    @Test(enabled = false)
  public void packetProcessed() {
    throw new RuntimeException("Test not implemented");
  }

    @Test(enabled = false)
  public void packetReceived() {
    throw new RuntimeException("Test not implemented");
  }
}
