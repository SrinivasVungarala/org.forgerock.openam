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

package org.forgerock.openam.sm.datalayer.impl;

import com.google.inject.Key;
import com.google.inject.PrivateBinder;
import org.forgerock.openam.cts.adapters.JavaBeanAdapter;
import org.forgerock.openam.cts.adapters.JavaBeanAdapterFactory;
import org.forgerock.openam.sm.datalayer.api.DataLayer;
import org.forgerock.openam.sm.datalayer.api.TaskExecutor;
import org.forgerock.openam.sm.datalayer.impl.ldap.LdapDataLayerConfiguration;
import org.forgerock.openam.sm.datalayer.impl.ldap.LdapDataLayerConnectionModule;
import org.forgerock.openam.sm.datalayer.impl.tasks.TaskFactory;
import org.forgerock.openam.sm.datalayer.store.TokenDataStore;
import org.forgerock.openam.sm.datalayer.impl.uma.UmaAuditEntry;

import javax.inject.Inject;
import javax.inject.Singleton;

public class UmaAuditConnectionModule extends LdapDataLayerConnectionModule {

    @Override
    protected Class<? extends LdapDataLayerConfiguration> getLdapConfigurationType() {
        return UmaAuditDataLayerConfiguration.class;
    }

    @Override
    protected void configureDataStore(PrivateBinder binder) {
        Key<TokenDataStore> storeKey = Key.get(TokenDataStore.class, DataLayer.Types.typed(connectionType));
        binder.bind(storeKey).to(UmaAuditTokenDatastore.class).in(Singleton.class);
        binder.expose(storeKey);
    }

    private static final class UmaAuditTokenDatastore extends TokenDataStore<UmaAuditEntry> {

        /**
         * Create a new TokenDataStore. This could be called from an extension class, or constructed as a raw store.
         *
         * @param adapterFactory The guice java bean token adapter factory.
         * @param taskExecutor The data layer task executor, for executing operations on the data store. Should be a
         *                     {@link SimpleTaskExecutor}.
         * @param taskFactory  The task factory for creating data store operations.
         */
        @Inject
        public UmaAuditTokenDatastore(JavaBeanAdapterFactory adapterFactory, TaskExecutor taskExecutor,
                TaskFactory taskFactory) {
            super((JavaBeanAdapter<UmaAuditEntry>) adapterFactory.create(UmaAuditEntry.class), taskExecutor,
                    taskFactory);
        }
    }
}
