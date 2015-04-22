/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2006 Sun Microsystems Inc. All Rights Reserved
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
 * $Id: URLValidator.java,v 1.2 2008/06/25 05:53:07 qcheng Exp $
 *
 */

package com.sun.identity.shared.validation;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Validator for URL format.
 */
public class URLValidator
    extends ValidatorBase
{
    private static URLValidator instance = new URLValidator();

    public final static String ERROR_CODE = "errorCode3";

    private URLValidator() {
    }

    /**
     * Returns an instance of this validator.
     *
     * @return an instance of this validator.
     */
    public static URLValidator getInstance() {
        return instance;
    }

    protected void performValidation(String strData)
        throws ValidationException {
        if ((strData == null) || (strData.trim().length() == 0)) {
            throw new ValidationException(resourceBundleName, ERROR_CODE);
        }

        try {
            new URL(strData);
        } catch (MalformedURLException e) {
            throw new ValidationException(resourceBundleName, ERROR_CODE);
        }
    }
}
