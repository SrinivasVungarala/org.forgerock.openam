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
 * $Id: AmFilterHandlerBase.java,v 1.2 2008/06/25 05:51:43 qcheng Exp $
 *
 * Portions Copyrighted 2015 ForgeRock AS.
 */

package com.sun.identity.agents.filter;

import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;

import com.sun.identity.agents.arch.AgentBase;
import com.sun.identity.agents.arch.AgentException;
import com.sun.identity.agents.arch.Manager;
import com.sun.identity.agents.common.ISSOTokenValidator;
import com.sun.identity.agents.util.IUtilConstants;
import org.forgerock.openam.utils.StringUtils;

/**
 * The base class for agent filter handler
 */
public abstract class AmFilterHandlerBase extends AgentBase implements
IUtilConstants, IFilterConfigurationConstants, IAmFilterHandler
{
    public AmFilterHandlerBase(Manager manager) {
        super(manager);
    }
    
    public void initialize(ISSOContext context, AmFilterMode mode) 
    throws AgentException {
        setSSOContext(context);
        setFilterMode(mode);
    }   
    
    /**
     * Returns the name of the handler.
     * @return the name of the handler
     */
    public String toString() {
        return getHandlerName();
    }
    
    protected String getRequestBody(HttpServletRequest request) 
    throws AgentException
    {
        String result = null;
        InputStream istream = null;
        String encoding = request.getCharacterEncoding();
        try {
            istream = request.getInputStream();
            StringBuffer buffer = new StringBuffer();
            byte[] strBytes = new byte[1024];
            int length = 0;
            while ((length = istream.read(strBytes, 0, 1024)) != -1) {
                buffer.append(new String(strBytes, 0, length, encoding));
            }
            result = buffer.toString();
        } catch (Exception ex) {
            throw new AgentException("Unable to read request body", ex);
        } finally {
            if (istream != null) {
                try {
                    istream.close();
                } catch (Exception ex) {
                    logError("AmFilterHandlerBase: Failed to close reader", ex);
                }
            }
        }
        return result;
    }
    
    protected String getClientIPAddress(HttpServletRequest request) {
        return getSSOContext().getSSOTokenValidator().getClientIPAddress(
                request);
    }
    
    protected String getClientHostName(HttpServletRequest request) {
        return getSSOContext().getSSOTokenValidator().getClientHostName(
                request);
    }
    
    protected String getApplicationName(HttpServletRequest request) {
        return getSSOContext().getSSOTokenValidator().getAppName(request);
    }
    
    protected ISSOTokenValidator getSSOTokenValidator() {
        return getSSOContext().getSSOTokenValidator();
    }
    
    protected boolean isModeJ2EEPolicyActive() {
        return isModeJ2EEPolicy() || isModeAll();
    }
    
    protected boolean isModeURLPolicyActive() {
        return isModeURLPolicy() || isModeAll();
    }
    
    protected boolean isModeSSOOnlyActive() {
        return !isModeNone();
    }
    
    protected boolean isModeNone() {
        return getFilterMode().equals(AmFilterMode.MODE_NONE);
    }
    
    protected boolean isModeSSOOnly() {
        return getFilterMode().equals(AmFilterMode.MODE_SSO_ONLY);
    }

    protected boolean isModeJ2EEPolicy() {
        return getFilterMode().equals(AmFilterMode.MODE_J2EE_POLICY);
    }
    
    protected boolean isModeURLPolicy() {
        return getFilterMode().equals(AmFilterMode.MODE_URL_POLICY);
    }

    protected boolean isModeAll() {
        return getFilterMode().equals(AmFilterMode.MODE_ALL);
    }

    
    protected AmFilterMode getFilterMode() {
        return _filterMode;
    }
    
    protected ISSOContext getSSOContext() {
        return _ssoContext;
    }

    /**
     * Returns true if the request URI is the logout URI for the App under this request.
     */
    protected boolean isLogoutURI(String requestURI, String appName) {

        boolean result = false;

        String logoutURI = getApplicationConfigurationString(requestURI, CONFIG_LOGOUT_URI_MAP, appName);
        if (StringUtils.isNotEmpty(logoutURI)) {
            if (logoutURI.equals(requestURI)) {
                result = true;
                if (isLogMessageEnabled()) {
                    logMessage("AmFilterHandlerBase : The request URI "
                            + requestURI + " matched the logout URI "
                            + logoutURI + " for the App:" + appName);
                }
            } else {
                if (isLogMessageEnabled()) {
                    logMessage("AmFilterHandlerBase : The request URI "
                            + requestURI + " did not match the logout URI "
                            + logoutURI + " for the App:" + appName);
                }
            }
        }

        return result;
    }

    /**
     * get the property id's value based on possible second context as the key.
     * The key of second context is in the form of appName/path1 from requested
     * URI /appName/path1/path2/...
     */
    protected String getApplicationConfigurationString(String requestURI, String id, String appName) {

        int index1 = requestURI.indexOf("/", 1);
        int index2 = -1;
        if (index1 > 0) {
            index2 = requestURI.indexOf("/", index1 + 1);
        }
        String secondContextKey = null;
        if (index2 > 0) {
            secondContextKey = requestURI.substring(1, index2);
        }

        // return if the key is null.
        if (secondContextKey == null || secondContextKey.length() == 0) {
            return getManager().getApplicationConfigurationString(id, appName);
        }

        // return to use appName as the key if second context value is null.
        String secondContextValue = getManager().getApplicationConfigurationString(id, secondContextKey);
        if (secondContextValue == null || secondContextValue.length() == 0) {
            return getManager().getApplicationConfigurationString(id, appName);
        }

        // get default or global value for this property.
        String defaultValue = getManager().getConfigurationString(id);
        if (defaultValue == null || defaultValue.length() == 0) {
            return secondContextValue;
        }

        if (secondContextValue.equals(defaultValue)) {
            return getManager().getApplicationConfigurationString(id, appName);
        } else {
            return secondContextValue;
        }
    }

    private void setFilterMode(AmFilterMode mode) {
        _filterMode = mode;
    }
    
    private void setSSOContext(ISSOContext context) {
        _ssoContext = context;
    }
    
    private AmFilterMode _filterMode;
    private ISSOContext _ssoContext;

}
