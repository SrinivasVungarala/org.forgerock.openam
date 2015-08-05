/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

/*global define, require, window, _*/
define("config/process/AMConfig", [
    "jquery",
    "org/forgerock/openam/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/EventManager",
    'org/forgerock/openam/ui/admin/delegates/SMSGlobalDelegate',
    "org/forgerock/commons/ui/common/util/UIUtils"
], function ($, Constants, EventManager, SMSGlobalDelegate, UIUtils) {
    var obj = [
        {
            startEvent: Constants.EVENT_LOGOUT,
            description: "used to override common logout event",
            override: true,
            dependencies: [
                "org/forgerock/commons/ui/common/main/Router",
                "org/forgerock/commons/ui/common/main/Configuration",
                "org/forgerock/commons/ui/common/main/SessionManager"
            ],
            processDescription: function (event, router, conf, sessionManager) {
                var argsURLFragment = event ? (event.args ? event.args[0] : '') : '',
                    urlParams = UIUtils.convertQueryParametersToJSON(argsURLFragment),
                    gotoURL = urlParams.goto;

                sessionManager.logout(function () {
                    conf.setProperty('loggedUser', null);
                    EventManager.sendEvent(Constants.EVENT_AUTHENTICATION_DATA_CHANGED, { anonymousMode: true});
                    delete conf.gotoURL;
                    if (gotoURL) {
                        UIUtils.setUrl(decodeURIComponent(gotoURL));
                    } else {
                        EventManager.sendEvent(Constants.EVENT_CHANGE_VIEW, {route: router.configuration.routes.loggedOut });
                    }
                }, function () {
                    conf.setProperty('loggedUser', null);
                    EventManager.sendEvent(Constants.EVENT_AUTHENTICATION_DATA_CHANGED, { anonymousMode: true});
                    EventManager.sendEvent(Constants.EVENT_DISPLAY_MESSAGE_REQUEST, "unauthorized");
                    if (gotoURL) {
                        UIUtils.setUrl(decodeURIComponent(gotoURL));
                    } else {
                        EventManager.sendEvent(Constants.EVENT_CHANGE_VIEW, {route: router.configuration.routes.login });
                    }
                });
            }
        },
        {
            startEvent: Constants.EVENT_INVALID_REALM,
            override: true,
            dependencies: [
                "org/forgerock/commons/ui/common/main/Router",
                "org/forgerock/commons/ui/common/main/Configuration"
            ],
            processDescription: function (event, router, conf) {
                if (event.error.responseJSON.message.indexOf('Invalid realm') > -1) {
                    if (conf.baseTemplate) {
                        EventManager.sendEvent(Constants.EVENT_DISPLAY_MESSAGE_REQUEST, "invalidRealm");
                    } else {
                        router.navigate('login', {trigger: true});
                        EventManager.sendEvent(Constants.EVENT_DISPLAY_MESSAGE_REQUEST, "invalidRealm");
                    }
                }
            }
        },
        {
            startEvent: Constants.EVENT_SHOW_CONFIRM_PASSWORD_DIALOG,
            description: "",
            dependencies: [
                "org/forgerock/commons/ui/user/profile/ConfirmPasswordDialog"
            ],
            processDescription: function (event, ConfirmPasswordDialog) {
                ConfirmPasswordDialog.show();
            }
        },
        {
            startEvent: Constants.EVENT_SHOW_CHANGE_SECURITY_DIALOG,
            override: true,
            dependencies: [
                "org/forgerock/openam/ui/user/profile/ChangeSecurityDataDialog"
            ],
            processDescription: function (event, ChangeSecurityDataDialog) {
                ChangeSecurityDataDialog.show(event);
            }
        },
        {
            startEvent: Constants.EVENT_ADD_NEW_REALM_DIALOG,
            override: true,
            dependencies: [
                "org/forgerock/commons/ui/common/main/Router",
                "org/forgerock/openam/ui/admin/views/realms/CreateUpdateRealmDialog",
                "org/forgerock/openam/ui/admin/views/realms/RealmsListView"
            ],
            processDescription: function (event, Router, CreateUpdateRealmDialog, RealmsListView) {
                Router.routeTo(Router.configuration.routes.realms, {
                    args: [],
                    trigger: true
                });

                CreateUpdateRealmDialog.show({
                    callback : function(){
                        RealmsListView.render();
                    }
                });
            }
        },
        {
            startEvent: Constants.EVENT_RETURN_TO_AM_CONSOLE,
            description: "",
            dependencies: [
                "org/forgerock/commons/ui/common/main/Configuration"
            ],
            processDescription: function (event, conf) {
                var subRealm = conf.globalData.auth.subRealm || "/";
                window.location.href = "/" + Constants.context + "/realm/RMRealm?RMRealm.tblDataActionHref=" + encodeURIComponent(subRealm);
            }
        },
        {
            startEvent: Constants.EVENT_AUTHENTICATED,
            description: "",
            dependencies: [
                "org/forgerock/commons/ui/common/main/Configuration",
                "org/forgerock/commons/ui/common/components/Navigation"
            ],
            processDescription: function (event, Configuration, Navigation) {
                if (_.contains(Configuration.loggedUser.roles, 'ui-admin')) {
                    Navigation.configuration.links.admin.urls.realms.urls.push({
                        'url': '#realms/' + encodeURIComponent('/'),
                        'name': $.t('console.common.topLevelRealm'),
                        'cssClass': 'dropdown-sub'
                    }, {
                        'url': '#realms',
                        'name': $.t('config.AppConfiguration.Navigation.links.realms.viewAll'),
                        'cssClass': 'dropdown-sub'
                    });

                    SMSGlobalDelegate.realms.all().done(function (data) {
                        var urls = Navigation.configuration.links.admin.urls.realms.urls,
                            realms = [];

                        _.forEach(data.result, function (realm) {
                            if (realm.active === true && realm.path !== '/' && realms.length < 2) {
                                realms.push({
                                    'url': '#realms/' + encodeURIComponent(realm.path),
                                    'name': realm.name,
                                    'cssClass': 'dropdown-sub'
                                });
                            }
                        });

                        urls.splice.apply(urls, [-1, 0].concat(realms));

                        Navigation.reload();
                    });

                    Navigation.reload();
                }
            }
        }

    ];
    return obj;
});
