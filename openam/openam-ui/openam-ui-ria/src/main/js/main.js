/**
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
 * Portions copyright 2011-2015 ForgeRock AS.
 */

/*global require, define, window */
require.config({
    paths: {
        "autosizeInput": "libs/jquery.autosize.input.min",

        "backbone"           : "libs/backbone-1.1.2-min",
        "backbone.paginator" : "libs/backbone-paginator.min",
        "backbone-relational": "libs/backbone-relational",

        "backgrid"          : "libs/backgrid.min",
        "backgrid.filter"   : "libs/backgrid-filter.min",
        "backgrid.paginator": "libs/backgrid-paginator.min",
        "backgrid.selectall": "libs/backgrid-select-all.min",

        "bootstrap"               : "libs/bootstrap-3.3.4-custom",
        "bootstrap-datetimepicker": "libs/bootstrap-datetimepicker-4.14.30-min",
        "bootstrap-dialog"        : "libs/bootstrap-dialog-1.34.4-min",
        "bootstrap-multiselect"   : "libs/bootstrap-multiselect.0.9.13",
        "bootstrap-tabdrop"       : "libs/bootstrap-tabdrop-1.0",

        "clockPicker" : "libs/bootstrap-clockpicker-0.0.7-min",
        "doTimeout"   : "libs/jquery.ba-dotimeout-1.0-min",
        "form2js"     : "libs/form2js-2.0",
        "handlebars"  : "libs/handlebars-3.0.3-min",
        "i18next"     : "libs/i18next-1.7.3-min",
        "jquery"      : "libs/jquery-2.1.1-min",
        "jqueryui"    : "libs/jquery-ui-1.11.1-min", // TODO this is used only for date picker, remove it and use something more lightweight
        "js2form"     : "libs/js2form-2.0",
        "jsonEditor"  : "libs/jsoneditor-custom.min",
        "moment"      : "libs/moment-2.8.1-min",
        "qrcode"      : "libs/qrcode-1.0.0-min",
        "sortable"    : "libs/jquery-nestingSortable-0.9.12",
        "spin"        : "libs/spin-2.0.1-min",
        "underscore"  : "libs/lodash-2.4.1-min",
        "xdate"       : "libs/xdate-0.8-min",
        "selectize"   : "libs/selectize-non-standalone-0.12.1-min",
        "sifter"      : "libs/sifter-0.4.1-min",
        "microplugin" : "libs/microplugin-0.0.3",
        "ThemeManager": "org/forgerock/openam/ui/common/util/ThemeManager",
        "UserDelegate": "org/forgerock/openam/ui/user/delegates/UserDelegate"
    },
    shim: {
        "autosizeInput": {
            deps: ["jquery"],
            exports: "autosizeInput"
        },
        "backbone": {
            deps: ["underscore"],
            exports: "Backbone"
        },
        "backbone.paginator": {
            deps: ["backbone"]
        },
        "backbone-relational": {
            deps: ['backbone']
        },

        "backgrid": {
            deps: ["jquery", "underscore", "backbone"],
            exports: "Backgrid"
        },
        "backgrid.filter": {
            deps: ["backgrid"]
        },
        "backgrid.paginator": {
            deps: ["backgrid", "backbone.paginator"]
        },
        "backgrid.selectall": {
            deps: ["backgrid"]
        },

        "bootstrap": {
            deps: ["jquery"]
        },
        "bootstrap-dialog": {
            deps: ["jquery", "underscore", "backbone", "bootstrap"]
        },
        "bootstrap-multiselect": {
            deps: ["jquery", "bootstrap"]
        },
        "bootstrap-tabdrop": {
            deps: ["jquery", "bootstrap"]
        },
        
        "clockPicker": {
            deps: ["jquery"],
            exports: "clockPicker"
        },
        "doTimeout": {
            deps: ["jquery"],
            exports: "doTimeout"
        },
        "form2js": {
            exports: "form2js"
        },
        "i18next": {
            deps: ["jquery", "handlebars"],
            exports: "i18next"
        },
        "js2form": {
            exports: "js2form"
        },
        "jsonEditor": {
            exports: "JSONEditor"
        },
        "jqueryui": {
            deps: ["jquery"],
            exports: "jqueryui"
        },
        "moment": {
            exports: "moment"
        },
        "qrcode": {
            exports: "qrcode"
        },
        "selectize": {
            // sifter, microplugin is additional dependencies for fix release build. It related with this issue https://github.com/brianreavis/selectize.js/issues/417
            deps: ["jquery", "sifter", "microplugin"]
        },
        "spin": {
            exports: "spin"
        },
        "underscore": {
            exports: "_"
        },
        "xdate": {
            exports: "xdate"
        },
        "sortable": {
            deps: ["jquery"]
        }
    }
});

/**
 * Loads all application on start, so each module will be available to
 * required synchronously
 */
require([
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/main/EventManager",
    "jquery",
    "underscore",
    "backbone",
    "autosizeInput",
    "backgrid",
    "clockPicker",
    "form2js",
    "js2form",
    "jsonEditor",
    "spin",
    "xdate",
    "moment",
    "doTimeout",
    "handlebars",
    "i18next",
    "org/forgerock/openam/ui/common/util/ThemeManager",
    "org/forgerock/commons/ui/common/main/i18nManager",
    "config/main",
    "org/forgerock/openam/ui/common/main",
    "org/forgerock/openam/ui/user/main",
    "org/forgerock/openam/ui/dashboard/main",
    "UserDelegate",
    "ThemeManager",
    "org/forgerock/commons/ui/user/main",
    "org/forgerock/commons/ui/common/main",
    "selectize",
    "backbone.paginator",
    "backgrid.paginator",
    "backgrid.filter",
    "backgrid.selectall",
    "bootstrap",
    "bootstrap-datetimepicker",
    "bootstrap-dialog",
    "bootstrap-multiselect",
    "bootstrap-tabdrop",
    "org/forgerock/openam/ui/uma/main",
    "org/forgerock/openam/ui/admin/main",
    "sortable",
    "qrcode"
], function (Constants, EventManager, $, _, Backbone) {
    // Helpers for the code that hasn't been properly migrated to require these as explicit dependencies:
    window.$ = $;
    window._ = _;
    window.Backbone = Backbone;

    EventManager.sendEvent(Constants.EVENT_DEPENDECIES_LOADED);
});
