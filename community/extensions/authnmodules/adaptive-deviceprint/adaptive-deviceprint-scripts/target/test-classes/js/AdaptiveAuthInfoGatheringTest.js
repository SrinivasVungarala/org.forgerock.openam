/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009 Sun Microsystems Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * https://opensso.dev.java.net/public/CDDLv1.0.html or
 * opensso/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at opensso/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 */

/*global define, require*/

require.config({
    "packages": [{
                name: "qunit",
                main: "qunit-amd-1.2.0-SNAPSHOT",
                location: "js/org/codehaus/mojo/qunit-amd/1.2.0-SNAPSHOT"
            }]
});

require(["org/forgerock/openam/extensions/authmodules/adaptivedeviceprint/DevicePrintInfoAggregator", 
         "qunit",
         "org/forgerock/openam/extensions/authmodules/adaptivedeviceprint/infocollectors/ScreenInfoCollector",
         "org/forgerock/openam/extensions/authmodules/adaptivedeviceprint/infocollectors/TimezoneCollector",
         "org/forgerock/openam/extensions/authmodules/adaptivedeviceprint/infocollectors/BrowserPluginsCollector",
         "org/forgerock/openam/extensions/authmodules/adaptivedeviceprint/infocollectors/GeolocationCollector",
         ],function(cut, qu) {
   
    qu.test("passing test", function() {
       z QUnit.equals("a","a");
    });
    
    qu.test("results length test", function() {
        var result = cut.collectInfo();
        //QUnit.equals(result.length,1); 
        console.log(JSON.stringify(result, undefined, 2));
    });
});

