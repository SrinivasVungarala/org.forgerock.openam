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

/*global define*/
define("org/forgerock/openam/ui/admin/views/realms/authentication/chains/EditChainView", [
    "jquery",
    "underscore",
    "org/forgerock/commons/ui/common/main/AbstractView",
    "org/forgerock/openam/ui/admin/utils/FormHelper",
    "handlebars",
    "org/forgerock/openam/ui/admin/views/realms/authentication/chains/LinkView",
    "org/forgerock/commons/ui/common/components/Messages",
    "org/forgerock/openam/ui/admin/views/realms/authentication/chains/PostProcessView",
    "org/forgerock/commons/ui/common/main/Router",
    "org/forgerock/openam/ui/admin/delegates/SMSRealmDelegate",

    // jquery dependencies
    "sortable"
], function ($, _, AbstractView, FormHelper, Handlebars, LinkView, Messages,
             PostProcessView, Router, SMSRealmDelegate) {
    var EditChainView = AbstractView.extend({
        template: "templates/admin/views/realms/authentication/chains/EditChainTemplate.html",
        events: {
            "click #saveChanges":   "saveChanges",
            "click #addModuleLink": "addModuleLink",
            "click #delete":        "deleteChain"
        },
        partials: [
            "partials/alerts/_Alert.html",
            "templates/admin/views/realms/partials/_HeaderDeleteButton.html"
        ],

        addItemToList: function (element) {
            this.$el.find("ol#sortableAuthChain").append(element);
        },

        addModuleLink: function (e) {
            if (e) {
                e.preventDefault();
            }
            var index = this.data.form.chainData.authChainConfiguration.length,
                linkView = this.createLinkView(index);
            linkView.editItem();
        },

        createLinkView: function (index) {
            var linkView = new LinkView();

            /**
             * A new list item is being dynamically created and added to the current EditChainView as a child View.
             * In order to do this we must create the element here, parent and pass it to the child so that it has
             * something to render inside of.
             */
            linkView.el = $("<li class='chain-link' />");
            linkView.element = linkView.el;
            linkView.parent = this;

            linkView.data = {
                // Each linkview instance requires allCriteria and allModules to render. These values are never changed
                // Because multiple instances require this same data, I grab it only in this parent view, then pass it
                // on to to all the child linkview instances.
                typeDescription : "",
                allModules : this.data.allModules,
                linkConfig : this.data.form.chainData.authChainConfiguration[index],
                allCriteria : {
                    REQUIRED : $.t("console.authentication.editChains.criteria.0.title"),
                    OPTIONAL : $.t("console.authentication.editChains.criteria.1.title"),
                    REQUISITE : $.t("console.authentication.editChains.criteria.2.title"),
                    SUFFICIENT : $.t("console.authentication.editChains.criteria.3.title")
                }
            };

            return linkView;
        },

        deleteChain: function (e) {
            var self = this;

            SMSRealmDelegate.authentication.chains.remove(
                self.data.realmPath,
                self.data.form.chainData._id)
            .then(function () {
                Messages.addMessage({
                    type: Messages.TYPE_INFO,
                    message: $.t("console.authentication.editChains.deletedChain")
                });
                Router.routeTo(Router.configuration.routes.realmsAuthenticationChains, {
                    args: [encodeURIComponent(self.data.realmPath)],
                    trigger: true
                });
            },function (e) {
                Messages.addMessage({
                    type: Messages.TYPE_DANGER,
                    response: e
                });
            });
        },

        initSortable: function () {
            var self = this;

            this.$el.find("ol#sortableAuthChain").nestingSortable({
                exclude:"li:not(.chain-link)",
                delay: 100,
                vertical: true,
                placeholder: "<li class='placeholder'><div class='placeholder-inner'></div></i>",
                tolerance: 0,

                onDrag: function (item, position) {
                    item.css({
                        left: position.left - self.adjustment.left,
                        top: position.top - self.adjustment.top
                    });
                },

                onDragStart: function (item, container, _super) {
                    var offset = item.offset(),
                        pointer = container.rootGroup.pointer;

                    self.adjustment = {
                        left: pointer.left - offset.left + 5,
                        top: pointer.top - offset.top
                    };
                    self.originalIndex = item.index();
                    item.addClass("dragged");
                    item.width(item.width());
                    $("body").addClass("dragging");
                },

                onDrop: function (item, container, _super, event) {
                    self.sortChainData(self.originalIndex, item.index());
                    _super(item, container);
                }
            });
        },

        render: function (args, callback) {
            var self = this;

            SMSRealmDelegate.authentication.chains.get(args[0], args[1]).then(function (data) {

                self.data = {
                    realmPath : args[0],
                    allModules : data.modulesData,
                    form : { chainData: data.chainData }
                };

                self.parentRender(function () {

                    if (self.data.form.chainData.authChainConfiguration.length > 0) {

                        _.each(self.data.form.chainData.authChainConfiguration, function (linkConfig, index) {
                            var linkView = self.createLinkView(index);
                            linkView.render();
                            self.addItemToList(linkView.element);
                        });

                    } else {
                        self.validateChain();
                    }

                    self.initSortable();
                    PostProcessView.render(self.data.form.chainData);
                });

            }, function (e) {
                Messages.addMessage({
                    type: Messages.TYPE_DANGER,
                    response: e
                });
            });
        },

        saveChanges: function (e) {

            var self = this,
                chainData = this.data.form.chainData;

            chainData.loginSuccessUrl[0] = this.$el.find("#loginSuccessUrl").val();
            chainData.loginFailureUrl[0] = this.$el.find("#loginFailureUrl").val();

            PostProcessView.addClassNameDialog().then(function () {
                var promise = SMSRealmDelegate.authentication.chains.update(self.data.realmPath, chainData._id, chainData);
                promise.fail(function (error) {
                    Messages.addMessage({
                        type: Messages.TYPE_DANGER,
                        response: error
                    });
                });
                FormHelper.bindSavePromiseToElement(promise, e.currentTarget);
            });
        },

        sortChainData: function (from, to) {
            var addItem = this.data.form.chainData.authChainConfiguration.splice(from, 1)[0];
            this.data.form.chainData.authChainConfiguration.splice(to, 0, addItem);
        },

        validateChain: function () {
            var invalid = false,
                alert = "";
            if (this.data.form.chainData.authChainConfiguration.length === 0) {
                invalid = true;
                this.$el.find("#sortableAuthChain").addClass("hidden");
                alert = Handlebars.compile("{{> alerts/_Alert type='warning' " +
                        "text='console.authentication.editChains.alerts.atLeastOne'}}");
            } else {
                this.$el.find("#sortableAuthChain").removeClass("hidden");
            }

            this.$el.find("#alertContainer").html(alert);
            this.$el.find("#saveChanges").prop("disabled", invalid);
        }

    });

    return EditChainView;

});
