/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 ForgeRock AS. All rights reserved.
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

/*global require, define, window*/
require.config({

    map: {
        "*" : {
            "ThemeManager" : "org/forgerock/openam/ui/common/util/ThemeManager",
            // TODO: Remove this when there are no longer any references to the "underscore" dependency
            "underscore"   : "lodash"
        }
    },

    paths: {
        "lodash":       "libs/lodash-3.10.1-min",
        "handlebars":   "libs/handlebars-3.0.3-min",
        "i18next":      "libs/i18next-1.7.3-min",
        "jquery":       "libs/jquery-2.1.1-min",
        "text":         "libs/text"
    },

    shim: {
        "handlebars": {
            exports: "handlebars"
        },
        "i18next": {
            deps: ["jquery", "handlebars"],
            exports: "i18next"
        },
        "underscore": {
            exports: "_"
        }
    }
});

require([
    "jquery",
    "underscore",
    "handlebars",
    "org/forgerock/commons/ui/common/main/Configuration",
    "org/forgerock/openam/ui/common/util/Constants",
    "text!templates/user/AuthorizeTemplate.html",
    "text!templates/common/LoginBaseTemplate.html",
    "text!templates/common/FooterTemplate.html",
    "org/forgerock/commons/ui/common/main/i18nManager",
    "ThemeManager"
], function ($, _, HandleBars, Configuration, Constants, AuthorizeTemplate,
            LoginBaseTemplate, FooterTemplate, i18nManager, ThemeManager) {

    // Helpers for the code that hasn't been properly migrated to require these as explicit dependencies:
    window.$ = $;
    window._ = _;

    var formTemplate,
        baseTemplate,
        footerTemplate,
        data = window.pageData;

    i18nManager.init({
        paramLang: {
            locale: data.locale
        },
        defaultLang: Constants.DEFAULT_LANGUAGE,
        nameSpace: "authorize"
    });

    Configuration.globalData = { realm : data.realm };

    ThemeManager.getTheme().always(function (theme) {
        data.theme = theme;
        baseTemplate = HandleBars.compile(LoginBaseTemplate);
        formTemplate = HandleBars.compile(AuthorizeTemplate);
        footerTemplate = HandleBars.compile(FooterTemplate);

        $("#wrapper").html(baseTemplate(data));
        $("#footer").html(footerTemplate(data));
        $("#content").html(formTemplate(data)).find(".panel-heading")
        .click(function (e) {
            $(this).toggleClass("expanded").next(".panel-collapse").slideToggle();
        });

    });

});
