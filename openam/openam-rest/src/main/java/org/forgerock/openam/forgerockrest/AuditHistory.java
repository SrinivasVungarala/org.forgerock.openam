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

package org.forgerock.openam.forgerockrest;

import static org.forgerock.json.resource.ResourceException.*;
import static org.forgerock.json.resource.Responses.*;
import static org.forgerock.util.promise.Promises.*;
import com.google.inject.Inject;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdUtils;
import org.forgerock.http.Context;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.openam.forgerockrest.entitlements.query.QueryResponsePresentation;
import org.forgerock.openam.rest.RealmContext;
import org.forgerock.openam.sm.datalayer.impl.uma.UmaAuditEntry;
import org.forgerock.openam.sm.datalayer.store.ServerException;
import org.forgerock.openam.uma.audit.UmaAuditLogger;
import org.forgerock.util.promise.Promise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class AuditHistory implements CollectionResourceProvider {

    private final UmaAuditLogger auditLogger;

    @Inject
    public AuditHistory(UmaAuditLogger datastore) {
        this.auditLogger = datastore;
    }

    @Override
    public Promise<ActionResponse, ResourceException> actionCollection(Context context, ActionRequest request) {
        AMIdentity identity = getIdentity(context);
        try {
            return newResultPromise(newActionResponse(new JsonValue(auditLogger.getHistory(identity, null))));
        } catch (ServerException e) {
            return newExceptionPromise((ResourceException) new InternalServerErrorException(e));
        }
    }

    @Override
    public Promise<QueryResponse, ResourceException> queryCollection(Context context, QueryRequest request,
            QueryResourceHandler handler) {
        AMIdentity identity = getIdentity(context);

        Set<UmaAuditEntry> history;
        try {
            if (request.getQueryFilter().toString().equals("true")) {
                history = auditLogger.getEntireHistory(identity);
            } else {
                history = auditLogger.getHistory(identity, request);
            }
        } catch (ServerException e) {
            return newExceptionPromise((ResourceException) new InternalServerErrorException(e));
        }

        Collection<JsonValue> results = new ArrayList<>();
        for (UmaAuditEntry entry : history) {
            results.add(entry.asJson());
        }

        QueryResponsePresentation.enableDeprecatedRemainingQueryResponse(request);
        return QueryResponsePresentation.perform(handler, request, results, new JsonPointer(UmaAuditEntry.ID));
    }

    private AMIdentity getIdentity(Context context) {
        String realm = context.asContext(RealmContext.class).getResolvedRealm();
        final String user = context.asContext(UriRouterContext.class).getUriTemplateVariables().get("user");
        return IdUtils.getIdentity(user, realm);
    }

    @Override
    public Promise<ActionResponse, ResourceException> actionInstance(Context context, String resourceId,
            ActionRequest request) {
        return newExceptionPromise(newNotSupportedException("Not supported."));
    }

    @Override
    public Promise<ResourceResponse, ResourceException> createInstance(Context context, CreateRequest request) {
        return newExceptionPromise(newNotSupportedException("Not supported."));
    }

    @Override
    public Promise<ResourceResponse, ResourceException> deleteInstance(Context context, String resourceId,
            DeleteRequest request) {
        return newExceptionPromise(newNotSupportedException("Not supported."));
    }

    @Override
    public Promise<ResourceResponse, ResourceException> patchInstance(Context context, String resourceId,
            PatchRequest request) {
        return newExceptionPromise(newNotSupportedException("Not supported."));
    }

    @Override
    public Promise<ResourceResponse, ResourceException> readInstance(Context context, String resourceId,
            ReadRequest request) {
        return newExceptionPromise(newNotSupportedException("Not supported."));
    }

    @Override
    public Promise<ResourceResponse, ResourceException> updateInstance(Context context, String resourceId,
            UpdateRequest request) {
        return newExceptionPromise(newNotSupportedException("Not supported."));
    }
}
