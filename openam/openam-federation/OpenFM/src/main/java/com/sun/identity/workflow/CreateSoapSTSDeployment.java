/*
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

package com.sun.identity.workflow;

import com.iplanet.am.util.SystemProperties;
import com.iplanet.services.util.JCEEncryption;
import com.sun.identity.password.plugins.RandomPasswordGenerator;
import com.sun.identity.password.ui.model.PWResetException;
import com.sun.identity.shared.Constants;
import com.sun.identity.shared.configuration.SystemPropertiesManager;
import com.sun.identity.shared.debug.Debug;
import org.apache.commons.lang.ArrayUtils;
import org.forgerock.guava.common.annotations.VisibleForTesting;
import org.forgerock.openam.shared.security.crypto.KeyStoreBuilder;
import org.forgerock.openam.shared.security.crypto.KeyStoreType;
import org.forgerock.openam.shared.sts.SharedSTSConstants;
import org.forgerock.openam.utils.StringUtils;
import org.forgerock.util.encode.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * This class is responsible for creating the soap-sts .war file which will expose soap-sts instances published to
 * a given realm. As such, it has a correspondence to the SoapSTS Agent created in a given realm.
 *
 * This class will create a .war file, based off of the .war file generated by the openam-sts/openam-soap-sts/openam-soap-sts-server
 * module. This class will customize this .war file in the following way:
 * 1. modify the config.properties file contained in the .war WEB-INF/classes directory to:
 *      a. specify the user-entered soap-sts-agent username as the value corresponding to the soap_sts_agent_username property
 *      b. specify the encrypted soap-sts-agent password as the value corresponding to the soap_sts_agent_password property
 *      c. specify the cookie name for this OpenAM deployment as the value corresponding to the am_session_cookie_name property
 *      d. specify the realm for this soap-sts deployment as the value corresponding to the am_realm property
 *      e. specify the url for the OpenAM deployment as the value corresponding to the am_deployment_url property
 * 2. include any user-specified custom wsdl files in the WEB-INF/classes directory
 * 3. include the keystore file(s) referenced in the Soap Keystore Configuration section of any soap-sts instances published
 * to this realm in the WEB-INF/classes directory. The Soap Keystore Configuration entries describe the keystore location,
 * aliases, and passwords necessary to enforce the SecurityPolicy binding specified in the wsdl files referenced in the
 * soap-sts instances published in a given realm. Note that that the CXF runtime can reference keystore locations on the
 * filesystem, as well as the classpath, so it is not mandatory that a keystore file be included in the .war file. In addition,
 * for soap-sts instances protected by OpenAM session tokens protected by the transport binding, no keystore state is necessary.
 *
 * Note that the first thought would be to modify the openam-soap-sts-server .war file bundled in the OpenAM .war file
 * (were the OpenAM build modified to effect said bundling), yet such an approach would prevent the creation of a
 * soap-sts .war file with soap-sts bits modified post-13 release. In other words, it would be best if this class could
 * create a soap-sts-agent-specific .war file based on soap-sts bits modified after the 13 release. Because there is no
 * generic file upload/download functionality in OpenAM, and because keystore files must be included in the soap-sts .war file, there
 * seems to be no other choice than to source .war file constituents from the filesystem, this despite the desire to
 * remove filesystem dependencies to support cloud deployments. Given the need to source .war file constituents from the
 * filesystem, it makes sense to also source the openam-soap-sts-server .war file from the filesystem, rather than from
 * the OpenAM .war file, in order that this common-task may create .war files containing post-13 release soap-sts bits.
 * Thus this class will write the customized .war file to the filesystem, just as the CreateFedlet common task does, but
 * also source keystores, custom .wsdl files, and the openam-soap-sts-server .war file from the filesystem.
 *
 */
public class CreateSoapSTSDeployment extends Task {
    /*
    The following 6 static query param keys must be the same strings as set in CreateSoapSTSDeployment.jsp in the getData
    javascript method.
     */
    private static final String REALM_PARAM = "realm";
    private static final String OPENAM_URL_PARAM = "openAMUrl";
    private static final String SOAP_AGENT_NAME_PARAM = "soapAgentName";
    private static final String SOAP_AGENT_PASSWORD_PARAM = "soapAgentPassword";
    private static final String KEYSTORE_FILE_NAMES_PARAM = "keystoreFileNames";
    private static final String WSDL_FILE_NAMES_PARAM = "wsdlFileNames";

    private static final String SOAP_DEPLOYMENT_DIRECTORY_NAME = "soapstsdeployment";

    private static final String SOAP_STS_SERVER_JAR_FILE_PREFIX = "openam-soap-sts-server";

    /*
    Want to keep the name of deployable .war files different from the openam-soap-sts server .war file, for
    deployments in the root directory will end up in the same place as the source .war.
     */
    private static final String DEPLOYABLE_SOAP_STS_SERVER_JAR_FILE_PREFIX = "deployable-soap-sts-server";

    /*
    Note that there is a dependency between this string and the name of the config file in the
    openam-soap-sts-server/src/main/resources/config.properties. This file is used by the soap-sts bootstrap process
    to connect to the OpenAM deployment to obtain the state corresponding to published soap-sts instances.
    Note this refers to a path in a jar file, where the path separator, '/', is not platform dependent.
     */
    private static final String SOAP_PROPERTY_FILE_JAR_ENTRY_NAME = "WEB-INF/classes/config.properties";

    /*
    Note this refers to a path in a jar file, where the path separator, '/', is not platform dependent.
     */
    private static final String CLASSES_WAR_DIRECTORY = "WEB-INF/classes/";

    /*
    The following definitions correspond to the keys in the openam-soap-sts-server/src/main/resource/config.properties
    file. They are need to update the file bundled in the openam-soap-sts-server .war file with user-provided entries.
     */
    private static final String SOAP_PROPERTY_FILE_AM_DEPLOYMENT_URL_KEY = "am_deployment_url";
    private static final String SOAP_PROPERTY_FILE_AM_SESSION_COOKIE_NAME_KEY = "am_session_cookie_name";
    private static final String SOAP_PROPERTY_FILE_SOAP_STS_AGENT_USERNAME_KEY = "soap_sts_agent_username";
    private static final String SOAP_PROPERTY_FILE_SOAP_STS_AGENT_PASSWORD_KEY = "soap_sts_agent_password";
    private static final String SOAP_PROPERTY_FILE_REALM_KEY = "am_realm";

    private static final String COMMA = ",";
    private static final String[] NO_MULTI_VALUE_ENTRIES = {};

    /*
    The name of the JarEntry referencing the keystore containing the agent password encryption key.
     */
    private static final String SOAP_KEYSTORE_JAR_ENTRY_NAME = CLASSES_WAR_DIRECTORY + SharedSTSConstants.AM_INTERNAL_SOAP_STS_KEYSTORE;

    /*
    We will use the JCEEncryption class to encrypt/decrypt the agent's password. By default, it uses the algorithm below.
     */
    private static final String SECRET_KEY_ALGORITHM_TYPE = "PBEWithMD5AndDES";

    @SuppressWarnings("unchecked")
    @Override
    public String execute(Locale locale, Map mapParams) throws WorkflowException {
        try {
            validatePresenceOfMandatoryParams(mapParams);
            final JarInputStream soapSTSServerWar = getJarInputStream();
            final Path outputJarPath = getOutputJarFilePath(getStringParam(mapParams, REALM_PARAM));
            final JarOutputStream modifiedSoapSTSServerWar = getJarOutputStream(outputJarPath, soapSTSServerWar.getManifest());
            processFileContents(soapSTSServerWar, modifiedSoapSTSServerWar, mapParams);
            return getCompletionMessage(locale, outputJarPath);
        } catch (WorkflowException e) {
            Debug.getInstance("workflow").error("Exception caught in CreateSoapSTSDeployment#execute: " + e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void validatePresenceOfMandatoryParams(Map mapParams) throws WorkflowException {
        String[] mandatoryParams = {REALM_PARAM, OPENAM_URL_PARAM, SOAP_AGENT_NAME_PARAM, SOAP_AGENT_PASSWORD_PARAM};
        for (String param : mandatoryParams) {
            if (StringUtils.isEmpty(getStringParam(mapParams, param))) {
                throw new WorkflowException("soap.sts.deployment.workflow.error.missing.param", param);
            }
        }
    }
    /*
    In the 13 release, this method will only open the soap-sts-server .jar file deposited under the soapstsdeployment
    configuration sub-directory. In subsequent releases, as part of the 'no filesystem dependencies for cloud deployments'
    initiative, this method may be satisfied by consuming the yield of a generic file upload process.
    Method is package-private so test class can subclass.
     */
    @VisibleForTesting
    JarInputStream getJarInputStream() throws WorkflowException {
        final Path baseDirectory = getDeploymentBaseDirectory();
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(baseDirectory, SOAP_STS_SERVER_JAR_FILE_PREFIX + "*.war")) {
            for (Path jar : jars) {
                return new JarInputStream(Files.newInputStream(jar, StandardOpenOption.READ));
            }
        } catch (IOException e) {
            throw new WorkflowException("soap.sts.deployment.workflow.error.read.exception.soap.sts.server.jar.file", e.getMessage());
        }
        //must use the WorkflowException ctor with two params to trigger resource-bundle lookup
        throw new WorkflowException("soap.sts.deployment.workflow.error.no.soap.sts.server.jar.file", null);
    }

    /*
     This method will create a JarOutputStream corresponding to the modified, realm-specific soap-sts-server .jar file.
     Method is package-private so test class can subclass.
     */
    @VisibleForTesting
    JarOutputStream getJarOutputStream(Path outputJarPath, Manifest inputWarManifest) throws WorkflowException {
        try {
            return new JarOutputStream(Files.newOutputStream(outputJarPath, StandardOpenOption.CREATE_NEW), inputWarManifest);
        } catch (IOException e) {
            throw new WorkflowException("soap.sts.deployment.workflow.error.output.jar.open.error", e.toString());
        }
    }

    /*
     Method is package-private so test class can subclass.
     */
    @VisibleForTesting
    String getCompletionMessage(Locale locale, Path outputJarPath) {
        String messageTemplate = getMessage("soap.sts.deployment.workflow.complete", locale);
        return MessageFormat.format(messageTemplate, outputJarPath.toString());
    }

    /*
    This method will read jar entries from the soapSTSServerJar file and write them to the modifiedSoapSTSServerJar file,
    updating the config.properties file with user-specified values, and adding any custom wsdl files or keystore files.
     */
    @SuppressWarnings("unchecked")
    private void processFileContents(JarInputStream soapSTSServerWar, JarOutputStream modifiedSoapSTSServerWar, Map mapParams) throws WorkflowException {
        try (JarInputStream in = soapSTSServerWar; JarOutputStream out = modifiedSoapSTSServerWar){
            final byte[] buffer = new byte[4096];
            final String agentPasswordEncryptionKey = getAgentPasswordEncryptionKey();
            final String encryptedAgentPassword = encryptAgentPassword(agentPasswordEncryptionKey, getStringParam(mapParams, SOAP_AGENT_PASSWORD_PARAM));
            JarEntry currentEntry = in.getNextJarEntry();
            while (currentEntry != null) {
                //if we are dealing with the property file, it needs to be updated with user-specified state, not just copied
                if (SOAP_PROPERTY_FILE_JAR_ENTRY_NAME.equals(currentEntry.getName())) {
                    updatePropertyFile(in, out, mapParams, encryptedAgentPassword);
                } else {
                    writeBitsToModifiedWar(in, out, currentEntry, buffer);
                }
                currentEntry = in.getNextJarEntry();
            }
            //now add any custom wsdl and keystore files
            processUserSpecifiedKeystoreAndCustomWsdlFiles(out, mapParams);
            //careful about calling this not as the last modification done to the .war. KeyStores write themselves to the
            //OutputStream, and close the stream itself. Seems to have to be the last thing done to the JarOutputStream.
            addAgentPasswordKeystore(out, agentPasswordEncryptionKey);
        } catch (IOException | PWResetException e) {
            throw new WorkflowException("soap.sts.deployment.workflow.error.exception.transferring.jar.file.contents", e.toString());
        }
    }

    private void writeBitsToModifiedWar(InputStream bitsSource, JarOutputStream modifiedSoapSTSServerWar,
                                        JarEntry jarEntry, byte[] buffer) throws IOException {
        modifiedSoapSTSServerWar.putNextEntry(new JarEntry(jarEntry));
        writeBits(bitsSource, modifiedSoapSTSServerWar, buffer);
    }

    private void writeBitsToModifiedWar(InputStream bitsSource, JarOutputStream modifiedSoapSTSServerWar,
                                        String jarEntryName, byte[] buffer) throws IOException {
        modifiedSoapSTSServerWar.putNextEntry(new JarEntry(jarEntryName));
        writeBits(bitsSource, modifiedSoapSTSServerWar, buffer);
    }

    private void writeBits(InputStream bitsSource, JarOutputStream modifiedSoapSTSServerWar, byte[] buffer) throws IOException {
        int bytesRead;
        while ((bytesRead = bitsSource.read(buffer)) != -1) {
            modifiedSoapSTSServerWar.write(buffer, 0, bytesRead);
        }
        modifiedSoapSTSServerWar.flush();
        modifiedSoapSTSServerWar.closeEntry();
    }

    private Path getDeploymentBaseDirectory() {
        return Paths.get(
                SystemProperties.get(SystemProperties.CONFIG_PATH), SOAP_DEPLOYMENT_DIRECTORY_NAME);
    }

    /*
    package-private so test class can subclass
     */
    @VisibleForTesting
    Path getOutputJarFilePath(String realm) throws WorkflowException {
        try {
            final Path realmPath = Paths.get(getDeploymentBaseDirectory().toString(), realm);
            Files.createDirectories(realmPath);
            return Paths.get(getDeploymentBaseDirectory().toString(), realm,
                    DEPLOYABLE_SOAP_STS_SERVER_JAR_FILE_PREFIX + "_" + getCurrentTimeAsString() + ".war");
        } catch (IOException e) {
            throw new WorkflowException("soap.sts.deployment.workflow.error.exception.creating.output.war.path", e.toString());
        }
    }

    private String getCurrentTimeAsString() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(Calendar.getInstance().getTime());
    }

    /*
    When this method is called, the soapSTSServerJar input stream will be pointing at the property file. The property file
    will be updated with the user-specified state, and a corresponding entry created in the modifiedSoapSTSServerJar output
    stream.
     */
    private void updatePropertyFile(JarInputStream soapSTSServerWar, JarOutputStream modifiedSoapSTSServerWar, Map mapParams,
                                    String encryptedAgentPassword) throws IOException {
        Properties properties = new Properties();
        properties.load(soapSTSServerWar);
        properties.setProperty(SOAP_PROPERTY_FILE_REALM_KEY, getStringParam(mapParams, REALM_PARAM));
        properties.setProperty(SOAP_PROPERTY_FILE_AM_DEPLOYMENT_URL_KEY, getStringParam(mapParams, OPENAM_URL_PARAM));
        properties.setProperty(SOAP_PROPERTY_FILE_SOAP_STS_AGENT_USERNAME_KEY, getStringParam(mapParams, SOAP_AGENT_NAME_PARAM));
        properties.setProperty(SOAP_PROPERTY_FILE_SOAP_STS_AGENT_PASSWORD_KEY, encryptedAgentPassword);
        properties.setProperty(SOAP_PROPERTY_FILE_AM_SESSION_COOKIE_NAME_KEY, getAMSessionIdCookieNameForDeployment());
        modifiedSoapSTSServerWar.putNextEntry(new JarEntry(SOAP_PROPERTY_FILE_JAR_ENTRY_NAME));
        final String nullComments = null;
        properties.store(modifiedSoapSTSServerWar, nullComments);
        modifiedSoapSTSServerWar.flush();
        modifiedSoapSTSServerWar.closeEntry();
    }

    /*
    Method is package-private so test class can subclass.
     */
    @VisibleForTesting
    String getAMSessionIdCookieNameForDeployment() {
        return SystemPropertiesManager.get(Constants.AM_COOKIE_NAME);
    }

    private void processUserSpecifiedKeystoreAndCustomWsdlFiles(JarOutputStream modifiedSoapSTSServerWar, Map mapParams) throws WorkflowException {
        final String[] keystoreFiles = getMultiValuedStringParam(mapParams, KEYSTORE_FILE_NAMES_PARAM);
        final byte[] buffer = new byte[4096];
        if (!ArrayUtils.isEmpty(keystoreFiles)) {
            addKeystoreOrCustomWsdlFiles(modifiedSoapSTSServerWar, keystoreFiles, buffer);
        }
        final String[] customWsdlFiles = getMultiValuedStringParam(mapParams, WSDL_FILE_NAMES_PARAM);
        if (!ArrayUtils.isEmpty(customWsdlFiles)) {
            addKeystoreOrCustomWsdlFiles(modifiedSoapSTSServerWar, customWsdlFiles, buffer);
        }
    }

    /*
    Adds the keystore used to store the secret used to encrypt the agent secret. This method will:
    1. create an empty keystore
    2. Obtain the keystore password (either hard-coded or obtained from user-specified parameters)
    3. add a secret key entry to the keystore specifying the password encryption key, which will be protected by the keystore password
    4. store the keystore in the updated .war file
     */
    private void addAgentPasswordKeystore(JarOutputStream modifiedSoapSTSServerWar, String agentPasswordEncryptionKey) throws WorkflowException {
        try {
            final KeyStore soapSTSKeystore = initializeKeyStore();
            final char[] keystorePassword = getKeystorePassword();
            setAgentPasswordEncryptionKeyEntry(soapSTSKeystore, keystorePassword, agentPasswordEncryptionKey);
            storeKeystoreInWar(soapSTSKeystore, keystorePassword, modifiedSoapSTSServerWar);
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException | IllegalStateException e) {
            throw new WorkflowException("soap.sts.deployment.workflow.error.exception.generating.internal.keystore", e.toString());
        }
    }

    private KeyStore initializeKeyStore() throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException {
        return new KeyStoreBuilder().withKeyStoreType(KeyStoreType.JCEKS).build();
    }

    private void setAgentPasswordEncryptionKeyEntry(KeyStore soapSTSKeystore, char[] keystorePassword,
                                                    String agentPasswordEncryptionKey) throws KeyStoreException {
        //the keystore password will also protected the password encryption key
        KeyStore.ProtectionParameter protParam =
                new KeyStore.PasswordProtection(keystorePassword);

        SecretKey agentPasswordEncryptionSecretKey =
                new SecretKeySpec(agentPasswordEncryptionKey.getBytes(StandardCharsets.US_ASCII), SECRET_KEY_ALGORITHM_TYPE);
        KeyStore.SecretKeyEntry secretKeyEntry =
                new KeyStore.SecretKeyEntry(agentPasswordEncryptionSecretKey);
        soapSTSKeystore.setEntry(SharedSTSConstants.AM_INTERNAL_PEK_ALIAS, secretKeyEntry, protParam);
    }

    private void storeKeystoreInWar(KeyStore soapSTSKeystore, char[] keystorePassword, JarOutputStream modifiedSoapSTSServerWar)
            throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        //store the keystore in the .war
        modifiedSoapSTSServerWar.putNextEntry(new JarEntry(SOAP_KEYSTORE_JAR_ENTRY_NAME));
        soapSTSKeystore.store(modifiedSoapSTSServerWar, keystorePassword);
        //don't need to flush/close the stream - KeyStore does it itself
    }

    private char[] getKeystorePassword() {
        return SharedSTSConstants.AM_INTERNAL_SOAP_STS_KEYSTORE_PW.toCharArray();
    }

    /*
    The JCEEncryption class does PBE (password based encryption). Thus the secret used to initialized the JCEEncryption
    class should be a random password.
     */
    private String getAgentPasswordEncryptionKey() throws PWResetException {
        return new RandomPasswordGenerator().generatePassword(null);
    }

    private String encryptAgentPassword(String agentPasswordEncryptionKey, String agentPassword) throws IllegalStateException {
        final JCEEncryption jceEncryption = new JCEEncryption();
        try {
            jceEncryption.setPassword(agentPasswordEncryptionKey);
        } catch (Exception e) {
            throw new IllegalStateException("Exception thrown from JCEEncryption#setPassword: " + e, e);
        }
        final byte[] encryptedBytes = jceEncryption.encrypt(agentPassword.getBytes(StandardCharsets.UTF_8));
        return Base64.encode(encryptedBytes);
    }

    private void addKeystoreOrCustomWsdlFiles(JarOutputStream modifiedSoapSTSServerWar, String[] fileNames, byte[] buffer) throws WorkflowException {
        for (String fileName : fileNames) {
            try {
                final InputStream wsdlFileInputStream = getInputStreamForKeystoreFileOrCustomWsdlFile(fileName);
                writeBitsToModifiedWar(wsdlFileInputStream, modifiedSoapSTSServerWar, CLASSES_WAR_DIRECTORY + fileName, buffer);
            } catch (IOException e) {
                throw new WorkflowException("soap.sts.deployment.workflow.error.exception.writing.wsdl.or.keystore.state", e.toString());
            }
        }
    }

    /*
    Method is package-private so test class can subclass.
     */
    @VisibleForTesting
    InputStream getInputStreamForKeystoreFileOrCustomWsdlFile(String fileName) throws IOException {
        final Path filePath = Paths.get(getDeploymentBaseDirectory().toString(), fileName);
        return Files.newInputStream(filePath, StandardOpenOption.READ);
    }

    /*
    Called to obtain the parameter set in the getData javascript method in CreateSoapSTSDeployment.jsp and transferred
    to the parameter map in AjaxProxy.jsp (see around line 147). The parameter entries should all be strings.
     */
    @SuppressWarnings("unchecked")
    private String getStringParam(Map params, String key) {
        String result = null;
        Object value = params.get(key);
        if (value != null) {
            if (value instanceof String) {
                result = ((String)value).trim();
            } else {
                throw new IllegalStateException("Illegal state in CreateSoapSTSDeployment: the state in the params map " +
                        "for key does not have an expected type. The type: " + value.getClass().getCanonicalName());
            }
        }
        return result;
    }

    /*
    Some user-entered state, like the keystore files or the custom wsdl files, can have multiple values. In this case, the
    getData javascript method in CreateSoapSTSDeployment.jsp will separate each individual value with a ','. In fact,
    a single value will have a ',' appended, and an empty list will be sent as "". Thus to obtain the keystore and custom wsdl file
    entries, this method should be called.
     */
    @SuppressWarnings("unchecked")
    private String[] getMultiValuedStringParam(Map params, String key) {
        String value = getStringParam(params, key);
        if (!StringUtils.isEmpty(value)) {
            return value.split(COMMA);
        } else {
            return NO_MULTI_VALUE_ENTRIES;
        }
    }
}
