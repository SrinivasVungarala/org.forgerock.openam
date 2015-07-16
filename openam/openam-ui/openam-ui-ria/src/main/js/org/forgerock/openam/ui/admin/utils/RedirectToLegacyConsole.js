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

/*global define, window*/
define('org/forgerock/openam/ui/admin/utils/RedirectToLegacyConsole', [
    'jquery',
    'org/forgerock/commons/ui/common/main/AbstractDelegate',
    'org/forgerock/commons/ui/common/main/Configuration',
    'org/forgerock/commons/ui/common/util/Constants'
], function ($, AbstractDelegate, Configuration, Constants) {
    var obj = new AbstractDelegate(Constants.host + '/' + Constants.context),
        redirector = function (tab) {
            return function (realm) {
                obj.realm.redirectToTab(tab, realm);
            };
        };

    obj.global = {
        accessControl: function () { obj.global.redirectToTab(1); },
        federation   : function () { obj.global.redirectToTab(2); },
        configuration: function () { obj.global.redirectToTab(4); },
        sessions     : function () { obj.global.redirectToTab(5); },
        redirectToTab: function (tabIndex) {
            obj.getJATOPageSession('/').done(function (session) {
                window.location.href = '/' + Constants.context + '/task/Home?' +
                'Home.tabCommon.TabHref=' + tabIndex +
                '&jato.pageSession=' + session + '&requester=XUI';
            });
        }
    };

    obj.commonTasks = function (realm, link) {
        var query = link.indexOf('?') === -1 ? '?' : '&';
        window.location.href = '/' + Constants.context + '/' + link + query + 'realm=' + encodeURIComponent(realm);
    };

    obj.realm = {
        general       : redirector(11),
        authentication: redirector(12),
        services      : redirector(13),
        dataStores    : redirector(14),
        privileges    : redirector(15),
        policies      : redirector(16),
        subjects      : redirector(17),
        agents        : redirector(18),
        sts           : redirector(19),
        scripts       : redirector(20),
        redirectToTab : function (tabIndex, realm) {
            obj.getJATOPageSession(realm).done(function (session) {
                window.location.href = '/' + Constants.context + '/realm/RealmProperties?' +
                'RMRealm.tblDataActionHref=' + realm +
                '&RealmProperties.tabCommon.TabHref=' + tabIndex +
                '&jato.pageSession=' + session + '&requester=XUI';
            });
        }
    };

    obj.getJATOPageSession = function (realm) {
        var promise = obj.serviceCall({
            url: '/realm/RMRealm?RMRealm.tblDataActionHref=' + realm + '&requester=XUI',
            dataType: 'html'
        });

        return $.when(promise)
        .then(function (data) {
            return data.match(/jato.pageSession=(.*?)"/)[1];
        });
    };

    return obj;
});
