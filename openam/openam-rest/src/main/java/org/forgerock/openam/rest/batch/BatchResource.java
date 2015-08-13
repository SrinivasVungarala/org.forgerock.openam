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
package org.forgerock.openam.rest.batch;

import static org.forgerock.openam.scripting.ScriptConstants.*;

import com.sun.identity.entitlement.opensso.SubjectUtils;
import com.sun.identity.shared.debug.Debug;
import javax.inject.Inject;
import javax.inject.Named;
import javax.script.Bindings;
import javax.script.SimpleBindings;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResultHandler;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.http.context.ServerContext;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.openam.errors.ExceptionMappingHandler;
import org.forgerock.openam.forgerockrest.RestUtils;
import org.forgerock.openam.forgerockrest.entitlements.RealmAwareResource;
import org.forgerock.openam.rest.batch.helpers.Requester;
import org.forgerock.openam.rest.batch.helpers.ScriptResponse;
import org.forgerock.openam.scripting.ScriptEvaluator;
import org.forgerock.openam.scripting.ScriptException;
import org.forgerock.openam.scripting.ScriptObject;
import org.forgerock.openam.scripting.service.ScriptConfiguration;
import org.forgerock.openam.scripting.service.ScriptingServiceFactory;

/**
 * Executes scripts with all the stuff needed to understand how batching operates.
 */
public class BatchResource extends RealmAwareResource {

    private static final String BATCH = "batch";

    private static final String PAYLOAD = "payload";
    private static final String CONTEXT = "context";
    private static final String LOGGER = "logger";
    private static final String REQUESTER = "requester";
    private static final String RESPONSE = "response";

    private static final JsonPointer SCRIPT_ID = new JsonPointer("/scriptId");
    private static final JsonPointer REQUESTS = new JsonPointer("/requests");

    private final ExceptionMappingHandler<ScriptException, ResourceException> exceptionMappingHandler;

    private final ScriptEvaluator scriptEvaluator;
    private final ScriptingServiceFactory scriptingServiceFactory;
    private final Debug debug;
    private final Requester requester;

    @Inject
    public BatchResource(@Named(SDK_NAME) ScriptEvaluator scriptEvaluator,
                         ScriptingServiceFactory scriptingServiceFactory,
                         @Named("frRest") Debug debug,
                         ExceptionMappingHandler<ScriptException, ResourceException> exceptionMappingHandler,
                         Requester requester) {
        this.scriptEvaluator = scriptEvaluator;
        this.scriptingServiceFactory = scriptingServiceFactory;
        this.debug = debug;
        this.exceptionMappingHandler = exceptionMappingHandler;
        this.requester = requester;
    }

    @Override
    public void actionCollection(ServerContext serverContext, ActionRequest actionRequest,
                                 ResultHandler<JsonValue> resultHandler) {

        if (!actionRequest.getAction().equals(BATCH)) {
            final String msg = "Action '" + actionRequest.getAction() + "' not implemented for this resource";
            final NotSupportedException exception = new NotSupportedException(msg);
            debug.error(msg, exception);
            resultHandler.handleError(exception);
            return;
        }

        String scriptId = null;

        try {
            JsonValue scriptIdValue = actionRequest.getContent().get(SCRIPT_ID);

            if (scriptIdValue == null) {
                if (debug.errorEnabled()) {
                    debug.error("BatchResource :: actionCollection - ScriptId null. Default scripts not implemented.");
                }
                resultHandler.handleError(ResourceException.getException(ResourceException.BAD_REQUEST));
                return;
            } else {
                scriptId = scriptIdValue.asString();
            }

            final JsonValue requests = actionRequest.getContent().get(REQUESTS);

            final String realm = getRealm(serverContext);
            final ScriptConfiguration scriptConfig =
                    scriptingServiceFactory.create(SubjectUtils.createSuperAdminSubject(), realm).get(scriptId);
            final ScriptObject script = new ScriptObject(scriptConfig.getName(), scriptConfig.getScript(),
                    scriptConfig.getLanguage());
            final ScriptResponse response = new ScriptResponse();

            final Bindings bindings = new SimpleBindings();
            bindings.put(PAYLOAD, requests);
            bindings.put(CONTEXT, serverContext);
            bindings.put(LOGGER, debug);
            bindings.put(REQUESTER, requester);
            bindings.put(RESPONSE, response);

            resultHandler.handleResult((JsonValue) scriptEvaluator.evaluateScript(script, bindings));

        } catch (ScriptException e) {
            debug.error("BatchResource :: actionCollection - Error running script : {}", scriptId);
            resultHandler.handleError(exceptionMappingHandler.handleError(serverContext, actionRequest, e));
        } catch (javax.script.ScriptException e) {
            debug.error("BatchResource :: actionCollection - Error running script : {}", scriptId);
            resultHandler.handleError(ResourceException.getException(ResourceException.INTERNAL_ERROR));
        }
    }

    @Override
    public void actionInstance(ServerContext serverContext, String s, ActionRequest actionRequest,
                               ResultHandler<JsonValue> resultHandler) {
        RestUtils.generateUnsupportedOperation(resultHandler);
    }

    @Override
    public void createInstance(ServerContext serverContext, CreateRequest createRequest,
                               ResultHandler<Resource> resultHandler) {
        RestUtils.generateUnsupportedOperation(resultHandler);
    }

    @Override
    public void deleteInstance(ServerContext serverContext, String s, DeleteRequest deleteRequest,
                               ResultHandler<Resource> resultHandler) {
        RestUtils.generateUnsupportedOperation(resultHandler);
    }

    @Override
    public void patchInstance(ServerContext serverContext, String s, PatchRequest patchRequest,
                              ResultHandler<Resource> resultHandler) {
        RestUtils.generateUnsupportedOperation(resultHandler);
    }

    @Override
    public void queryCollection(ServerContext serverContext, QueryRequest queryRequest,
                                QueryResultHandler queryResultHandler) {
        RestUtils.generateUnsupportedOperation(queryResultHandler);
    }

    @Override
    public void readInstance(ServerContext serverContext, String s, ReadRequest readRequest,
                             ResultHandler<Resource> resultHandler) {
        RestUtils.generateUnsupportedOperation(resultHandler);
    }

    @Override
    public void updateInstance(ServerContext serverContext, String s, UpdateRequest updateRequest,
                               ResultHandler<Resource> resultHandler) {
        RestUtils.generateUnsupportedOperation(resultHandler);
    }
}
