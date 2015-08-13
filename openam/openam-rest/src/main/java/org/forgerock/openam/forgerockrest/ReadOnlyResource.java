/*
 * Copyright 2013-2015 ForgeRock AS.
 *
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
 */

package org.forgerock.openam.forgerockrest;

import static org.forgerock.util.promise.Promises.newExceptionPromise;

import org.forgerock.http.context.ServerContext;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.util.promise.Promise;

/**
 * Represents a read only view of a resource.
 */
public abstract class ReadOnlyResource implements CollectionResourceProvider  {

    /**
     * Creates a NotSupportedException for a given operation type.
     *
     * @param type The type of operation which is not supported.
     * @return A NotSupportedException.
     */
    private ResourceException generateException(String type) {
        return new NotSupportedException(type + " are not supported for this Resource");
    }

    /**
     * Will throw an exception as Creates are not allowed for a Read Only Resource.
     *
     * {@inheritDoc}
     */
    public final Promise<ResourceResponse, ResourceException> createInstance(ServerContext ctx, CreateRequest request) {
        return newExceptionPromise(generateException("Creates"));
    }

    /**
     * Will throw an exception as Deletes are not allowed for a Read Only Resource.
     *
     * {@inheritDoc}
     */
    public final Promise<ResourceResponse, ResourceException> deleteInstance(ServerContext ctx, String resId,
            DeleteRequest request) {
        return newExceptionPromise(generateException("Deletes"));
    }

    /**
     * Will throw an exception as Patches are not allowed for a Read Only Resource.
     *
     * {@inheritDoc}
     */
    public final Promise<ResourceResponse, ResourceException> patchInstance(ServerContext ctx, String resId,
            PatchRequest request) {
        return newExceptionPromise(generateException("Patches"));
    }

    /**
     * Will throw an exception as Updates are not allowed for a Read Only Resource.
     *
     * {@inheritDoc}
     */
    public final Promise<ResourceResponse, ResourceException> updateInstance(ServerContext ctx, String resId,
            UpdateRequest request) {
        return newExceptionPromise(generateException("Updates"));
    }
}
