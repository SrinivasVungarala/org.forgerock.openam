/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2015 ForgeRock AS.
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

/*global define, $, _ */

define("org/forgerock/openam/ui/uma/views/share/DialogShare", [
        "org/forgerock/commons/ui/common/components/Dialog",
        "org/forgerock/openam/ui/uma/views/share/CommonShare",
        "org/forgerock/openam/ui/uma/views/resource/EditResource",
        "org/forgerock/commons/ui/common/main/Router"
], function(Dialog, CommonShare, editResourceView, router) {

    var DialogShare = Dialog.extend({
        contentTemplate: "templates/uma/views/share/BaseShare.html", //TODO .. need to use a blank base
        baseTemplate: "templates/common/DefaultBaseTemplate.html",

        events: {
            "click .dialogCloseCross img": "saveThenClose",
            "click input[name='close']": "saveThenClose"
        },

        render: function(args, callback) {
            $("#dialogs").hide();
            this.show(_.bind(function() {
                $("#dialogs").show();
                this.shareView = new CommonShare();
                this.shareView.baseTemplate= 'templates/common/DefaultBaseTemplate.html';
                this.shareView.element = '#dialogs .dialogContent';
                this.shareView.render(args, callback);

            }, this));
        },

        // Override Dialog method
        bgClickToClose: function(e) {
            e.stopPropagation();
            // return if not a button press
            if (e.target !== e.currentTarget  ) {
                return;
            }
            this.saveThenClose(e);
        },

        saveThenClose: function(e){
            //TODO :Add save code here.
            editResourceView.data.userPolicies.fetch({reset: true, processData: false});
            router.routeTo( router.configuration.routes.editResource, {args: [this.data.resourceSet.uid], trigger: true});

            this.close(e);
        }
    });

    return new DialogShare();
});
