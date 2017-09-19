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
define("org/forgerock/openam/ui/admin/delegates/SMSDelegateUtils", [
    "underscore"
], function (_) {
    /**
     * @exports org/forgerock/openam/ui/admin/delegates/SMSDelegateUtils
     */
    var obj = {};

    /**
     * Adds a type attribute of <code>object</code> if not present
     * @param {Object} schema Schema to check
     */
    function addSchemaType (schema) {
        if (!schema.type) {
            console.warn("JSON schema detected without root type attribute! Defaulting to \"object\" type.");
            schema.type = "object";
        }
    }
    /**
     * Determines whether the specified object is of type <code>object</code>
     * @param   {Object}  object Object to determine the type of
     * @returns {Boolean}        Whether the object is of type <code>object</code>
     */
    function isObjectType (object) {
        return object.type === "object";
    }
    /**
     * Recursively invokes the specified functions over each object's properties
     * @param {Object} object   Object with properties
     * @param {Array} callbacks Array of functions
     */
    function eachProperty (object, callbacks) {
        if (isObjectType(object)) {
            _.forEach(object.properties, function (property, key) {
                _.forEach(callbacks, function (callback) {
                    callback(property, key);
                });

                if (isObjectType(property)) {
                    eachProperty(property, callbacks);
                }
            });
        }
    }
    /**
    * Removes schema <code>defaults</code> attribute if present
    * @param {Object} schema Schema to check
    */
    function removeSchemaDefaults (schema) {
        if (schema.properties.defaults) {
            console.warn("JSON schema detected with a \"defaults\" section present in it's properties. Removing.");
            delete schema.properties.defaults;
        }
    }
    /**
    * Transforms boolean types to checkbox format
    * FIXME: To fix server side? Visual only?
    * @param {Object} property Property to transform
    */
    function transformBooleanTypeToCheckboxFormat (property) {
        if (property.hasOwnProperty("type") && property.type === "boolean") {
            property.format = "checkbox";
        }
    }
    /**
    * Recursively add string type to enum
    * FIXME: To fix server side
    * @param {Object} property Property to transform
    */
    function transformEnumTypeToString (property) {
        if (property.hasOwnProperty("enum")) {
            property.type = "string";
        }
    }
    /**
     * Transforms propertyOrder attribute to integer
     * @param {Object} property Property to transform
     */
    function transformPropertyOrderAttributeToInt (property) {
        if (property.hasOwnProperty("propertyOrder")) {
            property.propertyOrder = parseInt(property.propertyOrder.slice(1), 10);
        }
    }
    /**
     * Warns if a property is inferred to be a password and does not have a format of password
     * @param {Object} property Property to transform
     * @param {String} name Raw property name
     */
    function warnOnInferredPasswordWithoutFormat (property, name) {
        var possiblePassword = name.toLowerCase().indexOf("password", name.length - 8) !== -1,
            hasFormat = property.format === "password";
        if (property.type === "string" && possiblePassword && !hasFormat) {
            console.warn("JSON schema password property detected (inferred) without format of \"password\"");
        }
    }

    /**
     * Sanitizes JSON Schemas.
     * @param  {Object} schema Schema to sanitize
     * @returns {Object}       Sanitized schema
     */
    obj.sanitizeSchema = function (schema) {
        var transformedSchema = _.cloneDeep(schema);

        /**
         * Missing and superfluous attribute checks
         */
        addSchemaType(transformedSchema);
        removeSchemaDefaults(transformedSchema);

        /**
         * Property transforms & warnings
         */
        eachProperty(transformedSchema, [transformPropertyOrderAttributeToInt,
                                         transformBooleanTypeToCheckboxFormat,
                                         transformEnumTypeToString,
                                         warnOnInferredPasswordWithoutFormat]);

        /**
         * Additional attributes
         */
        // Adds attribute indicating if all the schema properties are of the type "object" (hence grouped)
        transformedSchema.grouped = _.every(transformedSchema.properties, isObjectType);
        // Create ordered array
        transformedSchema.orderedProperties = _.sortBy(_.map(transformedSchema.properties, function (value, key) {
            value._id = key;
            return value;
        }), "propertyOrder");

        return transformedSchema;
    };

    obj.sortResultBy = function (attribute) {
        return function (data) {
            data.result = _.sortBy(data.result, attribute);
        };
    };

    return obj;
});
