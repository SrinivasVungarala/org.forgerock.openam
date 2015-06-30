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
 * Copyright 2015 ForgeRock AS.
 */

/*global define, window, FileReader*/

define("org/forgerock/openam/ui/admin/views/realms/scripts/EditScriptView", [
    "jquery",
    "underscore",
    "bootstrap-dialog",
    "libs/codemirror/lib/codemirror",
    "libs/codemirror/mode/groovy/groovy",
    "libs/codemirror/mode/javascript/javascript",
    "org/forgerock/commons/ui/common/main/AbstractView",
    "org/forgerock/commons/ui/common/main/EventManager",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/commons/ui/common/util/Base64",
    "org/forgerock/commons/ui/common/util/Constants",
    "org/forgerock/commons/ui/common/util/UIUtils",
    "org/forgerock/openam/ui/admin/models/scripts/ScriptModel",
    "org/forgerock/openam/ui/admin/delegates/ScriptsDelegate"
], function ($, _, BootstrapDialog, CodeMirror, Groovy, Javascript, AbstractView, EventManager, Router, Base64,
             Constants, UIUtils, Script, ScriptsDelegate) {

    return AbstractView.extend({
        initialize: function (options) {
            this.model = null;
        },

        template: "templates/admin/views/realms/scripts/EditScriptTemplate.html",
        data: {},
        events: {
            "click #upload": "uploadScript",
            "keyup #upload": "uploadScript",
            "change [name=upload]": "readUploadedFile",
            "click #validateScript": "validateScript",
            "keyup #validateScript": "validateScript",
            "click #changeContext": "openDialog",
            "keyup #changeContext": "openDialog",
            "click input[name=save]": "submitForm",
            "keyup input[name=save]": "submitForm",
            "change input[name=language]": "changeLanguage",
            "submit form": "submitForm",
            "click #cancelEdit": "cancelEdit",
            "keyup #cancelEdit": "cancelEdit"
        },

        onModelSync: function (model, response) {
            this.renderAfterSyncModel();
        },

        render: function (args, callback) {
            var uuid = null;

            this.realmPath  = args[0];

            // As we interrupt render to update the model, we need to remember the callback
            if (callback) {
                this.renderCallback = callback;
            }

            // Realm location is the first argument, second one is the script uuid
            if (args.length === 2) {
                uuid = args[1];
            }

            this.contextsPromise = ScriptsDelegate.getAllContexts();
            this.defaultContextPromise = ScriptsDelegate.getDefaultGlobalContext();

            /**
             * Guard clause to check if model requires sync'ing/updating
             * Reason: We do not know the id of the data we need until the render function is called with args,
             * thus we can only check at this point if we have the correct model to render this view (the model
             * might already contain the correct data).
             * Behaviour: If the model does require sync'ing then we abort this render via the return and render
             * will it invoked again when the model is updated
             */
            if (this.syncModel(uuid)) {
                return;
            }

            this.renderAfterSyncModel();
        },

        /**
         * So the uuid can be omitted to the render function for two reasons:
         * 1. need to create a new script
         * 2. the render function is called from the function onModelSync
         * Then there is a conflict in the function syncModel.
         * In the first case we should to create a new model, in second case is not create.
         * So I divided the render function into two parts, so as not to cause a re-check and avoid the second case.
         */
        renderAfterSyncModel: function () {
            var self = this;

            this.data.entity = _.pick(this.model.attributes, "uuid", "name", "description", "language", "context", "script");

            if (!this.data.contexts) {
                this.contextsPromise.done(function (contexts) {
                    self.data.contexts = contexts.result;
                    self.renderScript();
                });
            } else {
                self.renderScript();
            }
        },

        renderScript: function () {
            var self = this;

            if (this.model.id) {
                this.data.languages = _.findWhere(this.data.contexts,function (context) {
                    return context._id === self.data.entity.context;
                }).languages;
            } else {
                this.data.languages = [];
                this.data.newScript = true;
            }

            this.parentRender(function () {
                this.showUploadButton();

                if (this.data.newScript) {
                    this.openDialog();
                } else {
                    this.initScriptEditor();
                }

                if (this.renderCallback) {
                    this.renderCallback();
                }
            });
        },

        updateFields: function () {
            var app = this.data.entity,
                dataFields = this.$el.find("[data-field]"),
                dataField;

            _.each(dataFields, function (field, key, list) {
                dataField = field.getAttribute("data-field");

                if (field.type === "radio") {
                    if (field.checked) {
                        app[dataField] = field.value;
                    }
                } else {
                    app[dataField] = field.value;
                }
            });

            app.script = this.scriptEditor.getValue();
        },

        submitForm: function (e) {
            e.preventDefault();

            if (e.type === "keyup" && e.keyCode !== 13) {
                return;
            }

            var savePromise,
                nonModifiedAttributes = _.clone(this.model.attributes),
                self = this;

            this.updateFields();

            _.extend(this.model.attributes, this.data.entity);
            savePromise = this.model.save();

            if (savePromise) {
                savePromise.done(function (e) {
                    Router.routeTo(Router.configuration.routes.realmsScripts,
                        {args: [encodeURIComponent(self.realmPath)], trigger: true});
                    EventManager.sendEvent(Constants.EVENT_DISPLAY_MESSAGE_REQUEST, "changesSaved");
                });
            } else {
                _.extend(this.model.attributes, nonModifiedAttributes);
                EventManager.sendEvent(Constants.EVENT_DISPLAY_MESSAGE_REQUEST, this.model.validationError);
            }
        },

        syncModel: function (uuid) {
            var syncRequired = !this.model || (uuid && this.model.id !== uuid);

            if (syncRequired && uuid) {
                // edit existing script
                this.stopListening(this.model);
                this.model = new Script({_id: uuid});
                this.listenTo(this.model, "sync", this.onModelSync);
                this.model.fetch();
            } else if (!uuid) {
                // create new script, sync is not needed
                syncRequired = false;
                this.stopListening(this.model);
                this.model = new Script();
            }

            return syncRequired;
        },

        validateScript: function (e) {
            if (e.type === "keyup" && e.keyCode !== 13) {
                return;
            }

            var scriptText = this.scriptEditor.getValue(),
                language = this.$el.find("input[name=language]:checked"),
                script,
                self = this;

            script = {
                script: Base64.encodeUTF8(scriptText),
                language: language.val()
            };

            ScriptsDelegate.validateScript(script).done(function (result) {
                self.$el.find("#validation").html(UIUtils.fillTemplateWithData(
                    "templates/admin/views/realms/scripts/ScriptValidationTemplate.html", result));
            });
        },

        uploadScript: function (e) {
            this.$el.find("[name=upload]").trigger("click");
        },

        readUploadedFile: function (e) {
            var self = this,
                file = e.target.files[0],
                reader = new FileReader();

            reader.onload = (function (file) {
                return function (e) {
                    self.scriptEditor.setValue(e.target.result);
                };
            }(file));

            reader.readAsText(file);
        },

        openDialog: function (e) {
            var self = this;

            if (!this.data.defaultContext) {
                this.defaultContextPromise.done(function (context) {
                    self.data.defaultContext = context.defaultContext;
                    self.renderDialog();
                });
            } else {
                self.renderDialog();
            }
        },

        renderDialog: function () {
            var self = this,
                footerButtons = [],
                options = {
                    type: self.data.newScript ? BootstrapDialog.TYPE_PRIMARY : BootstrapDialog.TYPE_DANGER,
                    title: self.data.newScript ?
                        $.t("console.scripts.edit.dialog.title.select") : $.t("console.scripts.edit.dialog.title.change"),
                    cssClass: "script-change-context",
                    closable: !self.data.newScript,
                    message: $("<div></div>"),
                    onshow: function (dialog) {
                        this.message.append(UIUtils.fillTemplateWithData(
                            "templates/admin/views/realms/scripts/ChangeContextTemplate.html", self.data));
                    }
                };

            if (!self.data.newScript) {
                footerButtons.push({
                    label: $.t("common.form.cancel"),
                    cssClass: "btn-default",
                    action: function (dialog) {
                        dialog.close();
                    }
                });
            }

            footerButtons.push({
                label: self.data.newScript ? $.t("common.form.save") : $.t("common.form.change"),
                cssClass: self.data.newScript ? "btn-primary" : "btn-danger",
                action: function (dialog) {
                    var newContext = dialog.$modalContent.find("[name=changeContext]:checked").val();
                    if (self.data.entity.context !== newContext) {
                        self.data.entity.context = newContext;
                        self.changeContext().done(function () {
                            self.parentRender(function () {
                                self.showUploadButton();
                                self.initScriptEditor();
                            });
                        });
                    }
                    dialog.close();
                }
            });

            options.buttons = footerButtons;
            BootstrapDialog.show(options);

            this.data.newScript = false;
        },

        changeContext: function () {
            var self = this,
                selectedContext = _.findWhere(this.data.contexts, function (context) {
                    return context._id === self.data.entity.context;
                }),
                defaultScript,
                promise = $.Deferred();

            this.data.languages = selectedContext.languages;

            if (selectedContext.defaultScript === "[Empty]") {
                this.data.entity.script = "";
                this.data.entity.language = "";
                promise.resolve();
            } else {
                defaultScript = new Script({_id: selectedContext.defaultScript});
                this.listenTo(defaultScript, "sync", function (model, response) {
                    self.data.entity.script = model.attributes.script;
                    self.data.entity.language = model.attributes.language;
                    promise.resolve();
                });
                defaultScript.fetch();
            }

            return promise;
        },

        initScriptEditor: function () {
            this.scriptEditor = CodeMirror.fromTextArea(this.$el.find("#script")[0], {
                lineNumbers: true,
                autofocus: true,
                viewportMargin: Infinity,
                mode: this.data.entity.language.toLowerCase(),
                theme: "forgerock"
            });
        },

        changeLanguage: function (e) {
            this.data.entity.language = e.target.value;
            this.scriptEditor.setOption("mode", this.data.entity.language.toLowerCase());
        },

        showUploadButton: function () {
            // Show the Upload button for modern browsers only. Documented feature.
            // File: Chrome 13; Firefox (Gecko) 3.0 (1.9) (non standard), 7 (7) (standard); Internet Explorer 10.0; Opera 11.5; Safari (WebKit) 6.0
            // FileReader: Firefox (Gecko) 3.6 (1.9.2);	Chrome 7; Internet Explorer 10; Opera 12.02; Safari 6.0.2
            if (window.File && window.FileReader && window.FileList) {
                this.$el.find("#upload").show();
            }
        },

        cancelEdit: function (e) {
            if (e.type === "keyup" && e.keyCode !== 13) {
                return;
            }

            Router.routeTo(Router.configuration.routes.realmsScripts, {
                args: [encodeURIComponent(this.realmPath)],
                trigger: true
            });
        }
    });
});
