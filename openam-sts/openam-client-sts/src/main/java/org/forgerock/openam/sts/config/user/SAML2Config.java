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
* information: "Portions Copyrighted [year] [name of copyright owner]".
*
* Copyright 2014-2016 ForgeRock AS.
* Portions Copyrighted 2022 Wren Security
*/

package org.forgerock.openam.sts.config.user;

import org.apache.xml.security.encryption.XMLCipher;
import org.wrensecurity.guava.common.base.Objects;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.shared.sts.SharedSTSConstants;
import org.forgerock.openam.sts.AMSTSConstants;
import org.forgerock.openam.sts.MapMarshallUtils;
import org.forgerock.openam.utils.CollectionUtils;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

/**
 * Encapsulates the configuration state necessary to produce SAML2 assertions.
 *
 * Each published rest-sts instance will encapsulate state to allow it to issue saml2 assertions for a single
 * SP. Thus the spEntityId, and spAcsUrl (the url of the SP's assertion consumer service) are specified in this class.
 * The signatureAlias corresponds to the IDP's signing key, and the encryptionKeyAlias could correspond to the SP's
 * public key corresponding to the key used to encrypt the symmetric key used to encrypt assertion elements.
 *
 * @supported.all.api
 */
public class SAML2Config {

    /*
    * TODO: Ambiguity in the context of setting the customAttributeStatementsProviderClassName
    * and the customAttributeMapperClassName. As it currently stands, the customAttributeStatementsProvider will be passed
    * an instance of the customAttributeMapper if both are specified. The usual case will simply to set the customAttributeMapper,
    * as this allows custom attributes to be set in the AttributeStatement.
    *
    * TODO: do I want a name-qualifier in addition to a nameIdFormat?
    */

    private static final String EQUALS = "=";

    /**
     * Builder used to programmatically create {@linkplain SAML2Config} objects
     *
     * @supported.all.api
     */
    public static class SAML2ConfigBuilder {
        private String idpId;
        /*
        Cannot use the SAML2Constants defined in openam-federation, as this dependency
        introduces a dependency on openam-core, which pulls the ws-* dependencies into the soap-sts, which I don't want.
        Also can't use the SAML2Constants in wss4j, as I don't want this dependency in the rest-sts (as it depends upon
        SAML2Config). Just define the value here.
         */
        private String nameIdFormat = "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified";
        private Map<String, String> attributeMap;
        private long tokenLifetimeInSeconds = 60 * 10; //default token lifetime is 10 minutes
        private String customConditionsProviderClassName;
        private String customSubjectProviderClassName;
        private String customAuthenticationStatementsProviderClassName;
        private String customAttributeStatementsProviderClassName;
        private String customAuthzDecisionStatementsProviderClassName;
        private String customAttributeMapperClassName;
        private String customAuthNContextMapperClassName;
        private String spEntityId;
        private String spAcsUrl;
        private boolean signAssertion;
        private boolean encryptNameID;
        private boolean encryptAttributes;
        private boolean encryptAssertion;
        private String encryptionAlgorithm;
        private int encryptionAlgorithmStrength;
        private String keystoreFileName;
        private byte[] keystorePassword;
        /*
        Corresponds to the key used to sign the assertion.
         */
        private String signatureKeyAlias;
        private byte[] signatureKeyPassword;
        /*
        Corresponds to the SP's x509 cert -  the corresponding public key is used to encrypt the symmetric key used to
        encrypt assertion elements
         */
        private String encryptionKeyAlias;

        private SAML2ConfigBuilder() {}

        /**
         * Sets the name-id format on the SAML2ConfigBuilder.
         *
         * @param nameIdFormat the name-id format.
         * @return the SAML2ConfigBuilder with the specified name-id format.
         */
        public SAML2ConfigBuilder nameIdFormat(String nameIdFormat) {
            //TODO - test to see if it matches one of the allowed values?
            this.nameIdFormat = nameIdFormat;
            return this;
        }

        /**
         * Sets the Idenity Provider id on the SAML2ConfigBuilder.
         *
         * @param idpId the Identity Provider id.
         * @return the SAML2ConfigBuilder with the specified Identity Provider id.
         */
        public SAML2ConfigBuilder idpId(String idpId) {
            this.idpId = idpId;
            return this;
        }

        /**
         * Sets the attribute map on the SAML2ConfigBuilder.
         *
         * @param attributeMap the attribute map.
         * @return the SAML2ConfigBuilder with the specified attribute map.
         */
        public SAML2ConfigBuilder attributeMap(Map<String, String> attributeMap) {
            this.attributeMap = Collections.unmodifiableMap(attributeMap);
            return this;
        }

        /**
         * Sets the token lifetime (in seconds) on the SAML2ConfigBuilder.
         *
         * @param lifetimeInSeconds the token lifetime.
         * @return the SAML2ConfigBuilder with the specified token lifetime.
         */
        public SAML2ConfigBuilder tokenLifetimeInSeconds(long lifetimeInSeconds) {
            this.tokenLifetimeInSeconds = lifetimeInSeconds;
            return this;
        }

        /**
         * Sets the CustomConditionsProvider classname on the SAML2ConfigBuilder.
         *
         * @param customConditionsProviderClassName the CustomConditionsProvider classname.
         * @return the SAML2ConfigBuilder with the specified CustomConditionsProvider classname.
         */
        public SAML2ConfigBuilder customConditionsProviderClassName(String customConditionsProviderClassName) {
            this.customConditionsProviderClassName = customConditionsProviderClassName;
            return this;
        }

        /**
         * Sets the CustomSubjectProvider classname on the SAML2ConfigBuilder.
         *
         * @param customSubjectProviderClassName the CustomSubjectProvider classname.
         * @return the SAML2ConfigBuilder with the specified CustomSubjectProvider classname.
         */
        public SAML2ConfigBuilder customSubjectProviderClassName(String customSubjectProviderClassName) {
            this.customSubjectProviderClassName = customSubjectProviderClassName;
            return this;
        }

        /**
         * Sets the CustomAuthenticationStatementsProvider classname on the SAML2ConfigBuilder.
         *
         * @param customAuthenticationStatementsProviderClassName the CustomAuthenticationStatementsProvider classname.
         * @return the SAML2ConfigBuilder with the specified CustomAuthenticationStatementsProvider classname.
         */
        public SAML2ConfigBuilder customAuthenticationStatementsProviderClassName(String customAuthenticationStatementsProviderClassName) {
            this.customAuthenticationStatementsProviderClassName = customAuthenticationStatementsProviderClassName;
            return this;
        }

        /**
         * Sets the CustomAttributeStatementsProvider classname on the SAML2ConfigBuilder.
         *
         * @param customAttributeStatementsProviderClassName the CustomAuthenticationStatementsProvider classname.
         * @return the SAML2ConfigBuilder with the specified CustomAuthenticationStatementsProvider classname.
         */
        public SAML2ConfigBuilder customAttributeStatementsProviderClassName(String customAttributeStatementsProviderClassName) {
            this.customAttributeStatementsProviderClassName = customAttributeStatementsProviderClassName;
            return this;
        }

        /**
         * Sets the CustomAuthzDecisionStatementsProvider classname on the SAML2ConfigBuilder.
         *
         * @param customAuthzDecisionStatementsProviderClassName the CustomAuthzDecisionStatementsProvider classname.
         * @return the SAML2ConfigBuilder with the specified CustomAuthzDecisionStatementsProvider classname.
         */
        public SAML2ConfigBuilder customAuthzDecisionStatementsProviderClassName(String customAuthzDecisionStatementsProviderClassName) {
            this.customAuthzDecisionStatementsProviderClassName = customAuthzDecisionStatementsProviderClassName;
            return this;
        }

        /**
         * Sets the CustomAttributeMapper classname on the SAML2ConfigBuilder.
         *
         * @param customAttributeMapperClassName the CustomAttributeMapper classname.
         * @return the SAML2ConfigBuilder with the specified CustomAttributeMapper classname.
         */
        public SAML2ConfigBuilder customAttributeMapperClassName(String customAttributeMapperClassName) {
            this.customAttributeMapperClassName = customAttributeMapperClassName;
            return this;
        }

        /**
         * Sets the CustomAuthNContextMapper classname on the SAML2ConfigBuilder.
         *
         * @param customAuthNContextMapperClassName the CustomAuthNContextMapper classname.
         * @return the SAML2ConfigBuilder with the specified CustomAuthNContextMapper classname.
         */
        public SAML2ConfigBuilder customAuthNContextMapperClassName(String customAuthNContextMapperClassName) {
            this.customAuthNContextMapperClassName = customAuthNContextMapperClassName;
            return this;
        }

        /**
         * Sets the SP entity id on the SAML2ConfigBuilder.
         *
         * @param spEntityId the SP entity id.
         * @return the SAML2Config builder with the specified SP entity id.
         */
        public SAML2ConfigBuilder spEntityId(String spEntityId) {
            this.spEntityId = spEntityId;
            return this;
        }

        /**
         * Sets the SP ACS url on the SAML2ConfigBuilder.
         *
         * @param spAcsUrl the SP ACS url.
         * @return the SAML2Config builder with the specified SP ACS url.
         */
        public SAML2ConfigBuilder spAcsUrl(String spAcsUrl) {
            this.spAcsUrl = spAcsUrl;
            return this;
        }

        /**
         * Sets the signature key alias on the SAML2ConfigBuilder.
         *
         * @param signatureKeyAlias the signature key alias.
         * @return the SAML2Config builder with the specified signature key alias.
         */
        public SAML2ConfigBuilder signatureKeyAlias(String signatureKeyAlias) {
            this.signatureKeyAlias = signatureKeyAlias;
            return this;
        }

        /**
         * Sets the signature key password on the SAML2ConfigBuilder.
         *
         * @param signatureKeyPassword the signature key password.
         * @return the SAML2Config builder with the specified signature key password.
         */
        public SAML2ConfigBuilder signatureKeyPassword(byte[] signatureKeyPassword) {
            this.signatureKeyPassword = signatureKeyPassword;
            return this;
        }

        /**
         * Sets the encryption key alias on the SAML2ConfigBuilder.
         *
         * @param encryptionKeyAlias the encryption key alias.
         * @return the SAML2Config builder with the specified encryption key alias.
         */
        public SAML2ConfigBuilder encryptionKeyAlias(String encryptionKeyAlias) {
            this.encryptionKeyAlias = encryptionKeyAlias;
            return this;
        }

        /**
         * Sets whether the SAML2Config assertion should be signed.
         *
         * @param signAssertion whether the assertion should be signed.
         * @return the SAML2ConfigBuilder with the assertion signed flag set.
         */
        public SAML2ConfigBuilder signAssertion(boolean signAssertion) {
            this.signAssertion = signAssertion;
            return this;
        }

        /**
         * Sets whether the SAML2Config name-id should be encrypted.
         *
         * @param encryptNameID whether the name-id should be encrypted.
         * @return the SAML2ConfigBuilder with the name-id encryption flag set.
         */
        public SAML2ConfigBuilder encryptNameID(boolean encryptNameID) {
            this.encryptNameID = encryptNameID;
            return this;
        }

        /**
         * Sets whether SAML2Config attributes should be encrypted.
         *
         * @param encryptAttributes whether the attributes should be encrypted.
         * @return the SAML2ConfigBuilder with the attribute encryption flag set.
         */
        public SAML2ConfigBuilder encryptAttributes(boolean encryptAttributes) {
            this.encryptAttributes = encryptAttributes;
            return this;
        }

        /**
         * Sets whether SAML2Config assertion should be encrypted.
         *
         * @param encryptAssertion whether the assertion should be encrypted.
         * @return the SAML2ConfigBuilder with the assertion encryption flag set.
         */
        public SAML2ConfigBuilder encryptAssertion(boolean encryptAssertion) {
            this.encryptAssertion = encryptAssertion;
            return this;
        }

        /*
        Note that the encryption of SAML2 assertions, is, by default, delegated to the FMEncProvider class, which supports
        only http://www.w3.org/2001/04/xmlenc#aes128-cbc, http://www.w3.org/2001/04/xmlenc#aes192-cbc,
        http://www.w3.org/2001/04/xmlenc#aes256-cbc, or http://www.w3.org/2001/04/xmlenc#tripledes-cbc. However, because
        this EncProvider implementation can be over-ridden by setting the com.sun.identity.saml2.xmlenc.EncryptionProvider
        property, I can't reject the specification of an encryption algorithm not supported by the FMEncProvider, as
        I don't know whether this property has been over-ridden.

        Note also that I will remove http://www.w3.org/2001/04/xmlenc#tripledes-cbc from the set of choices exposed
        in the UI. There seems to be a bug in the FMEncProvider - when the tripledes-cbc is chosen, note on line 294 that
        this string http://www.w3.org/2001/04/xmlenc#tripledes-cbc is passed to XMLCipher.getInstance resulting in the
        error below:
        org.apache.xml.security.encryption.XMLEncryptionException: Wrong algorithm: DESede or TripleDES required
        The correct thing is done in FMEncProvider#generateSecretKey, where the http://www.w3.org/2001/04/xmlenc#tripledes-cbc
        is translated to 'TripleDES' before being passed to the XMLCipher - and this actually works.
         */
        /**
         * Sets the SAML2Config encryption algorithm.
         *
         * @param encryptionAlgorithm the encryption algorithm.
         * @return the SAML2ConfigBuilder with the specified encryption algorithm.
         */
        public SAML2ConfigBuilder encryptionAlgorithm(String encryptionAlgorithm) {
            this.encryptionAlgorithm = encryptionAlgorithm;
            return this;
        }

        /*
        Note that the encryption of SAML2 assertions, is, by default, delegated to the FMEncProvider class, which supports
        only encryption algorithm strength values of 128, 192, and 256 for the encryption types XMLCipher.AES_128,
        XMLCipher.AES_192, and XMLCipher.AES_256, respectively. It does not look like the XMLCipher.TRIPLEDES supports a
        key encryption strength (see FMEncProvider for details). Given that the encryption strength is directly related
        to the cipher, it seems a bit silly to set these values. However, because
        this EncProvider implementation can be over-ridden by setting the com.sun.identity.saml2.xmlenc.EncryptionProvider
        property, and because the EncProvider specifies an encryption strength parameter, it would seem that I would have
        to support the setting of this seemingly superfluous parameter, just to support the plug-in interface. For now,
        I will not expose this value in the UI, as it adds unnecessary complexity, and the encryption algorithms are
        pre-defined as well. I will simply set this value in the UI context based upon the encryption algorithm. If
        a customer wants to specify a custom value because they have implemented their own EncryptionProvider, then they
        can publish a rest-sts instance programmatically.
         */
        /**
         * Sets the SAML2Config encryption strength.
         *
         * @param encryptionAlgorithmStrength the encryption strength.
         * @return the SAML2ConfigBuilder with the specified encryption strength.
         */
        public SAML2ConfigBuilder encryptionAlgorithmStrength(int encryptionAlgorithmStrength) {
            this.encryptionAlgorithmStrength = encryptionAlgorithmStrength;
            return this;
        }

        /**
         * Sets the keystore filename on the SAML2ConfigBuilder.
         *
         * @param keystoreFileName the keystore filename.
         * @return the SAML2Config builder with the specified keystore filename.
         */
        public SAML2ConfigBuilder keystoreFile(String keystoreFileName) {
            this.keystoreFileName = keystoreFileName;
            return this;
        }

        /**
         * Sets the keystore password on the SAML2ConfigBuilder.
         *
         * @param keystorePassword the keystore password.
         * @return the SAML2Config builder with the specified keystore password.
         */
        public SAML2ConfigBuilder keystorePassword(byte[] keystorePassword) {
            this.keystorePassword = keystorePassword;
            return this;
        }

        /**
         * Builds a SAML2Config object.
         * 
         * @return a SAML2Config object.
         */
        public SAML2Config build() {
            return new SAML2Config(this);
        }
    }

    /*
    Define the names of fields to aid in json marshalling. Note that these names match the names of the AttributeSchema
    entries in restSTS.xml and soapSTS.xml, as this aids in marshalling an instance of this class into the attribute map needed for
    SMS persistence.
     */
    static final String NAME_ID_FORMAT = "saml2-name-id-format";
    static final String ATTRIBUTE_MAP = SharedSTSConstants.SAML2_ATTRIBUTE_MAP;
    static final String TOKEN_LIFETIME = SharedSTSConstants.SAML2_TOKEN_LIFETIME;
    static final String CUSTOM_CONDITIONS_PROVIDER_CLASS = "saml2-custom-conditions-provider-class-name";
    static final String CUSTOM_SUBJECT_PROVIDER_CLASS = "saml2-custom-subject-provider-class-name";
    static final String CUSTOM_ATTRIBUTE_STATEMENTS_PROVIDER_CLASS = "saml2-custom-attribute-statements-provider-class-name";
    static final String CUSTOM_AUTHENTICATION_STATEMENTS_PROVIDER_CLASS = "saml2-custom-authentication-statements-provider-class-name";
    static final String CUSTOM_AUTHZ_DECISION_STATEMENTS_PROVIDER_CLASS = "saml2-custom-authz-decision-statements-provider-class-name";
    static final String CUSTOM_ATTRIBUTE_MAPPER_CLASS = "saml2-custom-attribute-mapper-class-name";
    static final String CUSTOM_AUTHN_CONTEXT_MAPPER_CLASS = "saml2-custom-authn-context-mapper-class-name";
    static final String SIGN_ASSERTION = SharedSTSConstants.SAML2_SIGN_ASSERTION;
    static final String ENCRYPT_ATTRIBUTES = SharedSTSConstants.SAML2_ENCRYPT_ATTRIBUTES;
    static final String ENCRYPT_NAME_ID = SharedSTSConstants.SAML2_ENCRYPT_NAME_ID;
    static final String ENCRYPT_ASSERTION = SharedSTSConstants.SAML2_ENCRYPT_ASSERTION;
    static final String ENCRYPTION_ALGORITHM = SharedSTSConstants.SAML2_ENCRYPTION_ALGORITHM;
    static final String ENCRYPTION_ALGORITHM_STRENGTH = SharedSTSConstants.SAML2_ENCRYPTION_ALGORITHM_STRENGTH;
    static final String KEYSTORE_FILE_NAME = SharedSTSConstants.SAML2_KEYSTORE_FILE_NAME;
    static final String KEYSTORE_PASSWORD = SharedSTSConstants.SAML2_KEYSTORE_PASSWORD;
    static final String SP_ENTITY_ID = SharedSTSConstants.SAML2_SP_ENTITY_ID;
    static final String SP_ACS_URL = SharedSTSConstants.SAML2_SP_ACS_URL;
    static final String ENCRYPTION_KEY_ALIAS = SharedSTSConstants.SAML2_ENCRYPTION_KEY_ALIAS;
    static final String SIGNATURE_KEY_ALIAS = SharedSTSConstants.SAML2_SIGNATURE_KEY_ALIAS;
    static final String SIGNATURE_KEY_PASSWORD = SharedSTSConstants.SAML2_SIGNATURE_KEY_PASSWORD;
    /*
    Note that this attribute(issuer-name) was defined in STSInstanceConfig, and thus global to an STS instance. It was
    used to set the issuer field in issued SAML2 assertions, when the STS only issued SAML2 assertions. Now that
    OIDC tokens are also issued, the issuer-name must be scoped to the token-specific config. Thus, by convention,
    the name of the AttributeSchema element would be saml2-issuer-name. However, changing the name of this attribute
    would involve a schema migration step. This can be avoided by encapsulating this same attribute in the SAML2Config
    class, and via an update to the AdminUI to describe this attribute as the IDP identifier
     */
    static final String ISSUER_NAME = SharedSTSConstants.ISSUER_NAME;

    private final String nameIdFormat;
    private final Map<String, String> attributeMap;
    private final long tokenLifetimeInSeconds;
    private final String customConditionsProviderClassName;
    private final String customSubjectProviderClassName;
    private final String customAuthenticationStatementsProviderClassName;
    private final String customAttributeStatementsProviderClassName;
    private final String customAuthzDecisionStatementsProviderClassName;
    private final String customAttributeMapperClassName;
    private final String customAuthNContextMapperClassName;
    private final String spEntityId;
    private final String spAcsUrl;
    private final boolean signAssertion;
    private final boolean encryptNameID;
    private final boolean encryptAttributes;
    private final boolean encryptAssertion;
    private final String encryptionAlgorithm;
    private final int encryptionAlgorithmStrength;
    private final String keystoreFileName;
    private final byte[] keystorePassword;
    private final String signatureKeyAlias;
    private final byte[] signatureKeyPassword;
    private final String encryptionKeyAlias;
    private final String idpId;

    private SAML2Config(SAML2ConfigBuilder builder) {
        this.nameIdFormat = builder.nameIdFormat; //not required so don't reject if null
        if (builder.attributeMap != null) {
            this.attributeMap = Collections.unmodifiableMap(builder.attributeMap);
        } else {
            attributeMap = Collections.emptyMap();
        }
        tokenLifetimeInSeconds = builder.tokenLifetimeInSeconds; //will be set to default if not explicitly set
        customConditionsProviderClassName = builder.customConditionsProviderClassName;
        customSubjectProviderClassName = builder.customSubjectProviderClassName;
        customAuthenticationStatementsProviderClassName = builder.customAuthenticationStatementsProviderClassName;
        customAuthzDecisionStatementsProviderClassName = builder.customAuthzDecisionStatementsProviderClassName;
        customAttributeStatementsProviderClassName = builder.customAttributeStatementsProviderClassName;
        customAttributeMapperClassName = builder.customAttributeMapperClassName;
        customAuthNContextMapperClassName = builder.customAuthNContextMapperClassName;
        this.signAssertion = builder.signAssertion;
        this.encryptNameID = builder.encryptNameID;
        this.encryptAttributes = builder.encryptAttributes;
        this.encryptAssertion = builder.encryptAssertion;
        this.encryptionAlgorithm = builder.encryptionAlgorithm;
        this.encryptionAlgorithmStrength = builder.encryptionAlgorithmStrength;
        this.keystoreFileName = builder.keystoreFileName;
        this.keystorePassword = builder.keystorePassword;
        this.spEntityId = builder.spEntityId;
        this.spAcsUrl = builder.spAcsUrl;
        this.signatureKeyAlias = builder.signatureKeyAlias;
        this.signatureKeyPassword = builder.signatureKeyPassword;
        this.encryptionKeyAlias = builder.encryptionKeyAlias;
        this.idpId = builder.idpId;

        if (spEntityId ==  null) {
            throw new IllegalArgumentException("The entity id of the consumer (SP) for issued assertions must be specified.");
        }
        if (encryptAssertion || encryptNameID || encryptAttributes) {
            if (encryptionAlgorithm == null) {
                throw new IllegalArgumentException("If elements of the assertion are to be encrypted, an encryption " +
                        "algorithm must be specified.");
            }
            if (encryptionAlgorithmStrength == 0 && !XMLCipher.TRIPLEDES.equals(encryptionAlgorithm)) {
                throw new IllegalArgumentException("If elements of the assertion are to be encrypted, an encryption " +
                        "algorithm strength must be specified.");
            }
            if (encryptionKeyAlias ==  null) {
                throw new IllegalArgumentException("If elements of the assertion are to be encrypted, an encryption key" +
                        "alias  must be specified.");
            }
        }
        if (encryptAssertion || encryptNameID || encryptAttributes || signAssertion) {
            if (keystorePassword == null || keystoreFileName == null) {
                throw new IllegalArgumentException("If the assertions are to be signed or encrypted, then the keystore " +
                        "file and password must be specified.");
            }
        }
        if (signAssertion) {
            if ((signatureKeyPassword == null) || (signatureKeyAlias == null)) {
                throw new IllegalArgumentException("If the assertion is to be signed, then the signature key alias and" +
                        " signature key password must be specified.");
            }
        }

        if (encryptAssertion && (encryptNameID || encryptAttributes)) {
            throw new IllegalArgumentException("Either the entire assertion can be encrypted, or the Attributes and/or NameID.");
        }

        if (idpId == null) {
            throw new IllegalArgumentException("The Identity Provider id must be set.");
        }
    }

    /**
     * Creates a new {@code SAML2ConfigBuilder}.
     *
     * @return a new {@code SAML2ConfigBuilder}.
     */
    public static SAML2ConfigBuilder builder() {
        return new SAML2ConfigBuilder();
    }

    /**
     * Gets the name-id format.
     *
     * @return the name-id format.
     */
    public String getNameIdFormat() {
        return nameIdFormat;
    }

    /**
     * Gets the token lifetime (in seconds).
     *
     * @return the token lifetime.
     */
    public long getTokenLifetimeInSeconds() {
        return tokenLifetimeInSeconds;
    }

    /**
     * Gets the attribute map.
     *
     * @return the attribute map.
     */
    public Map<String, String> getAttributeMap() {
        return attributeMap;
    }

    /**
     * Gets the classname of the CustomConditionsProvider.
     *
     * @return the classname of the CustomConditionsProvider.
     */
    public String getCustomConditionsProviderClassName() {
        return customConditionsProviderClassName;
    }

    /**
     * Gets the classname of the CustomSubjectProvider.
     *
     * @return the classname of the CustomSubjectProvider.
     */
    public String getCustomSubjectProviderClassName() {
        return customSubjectProviderClassName;
    }

    /**
     * Gets the classname of the CustomAuthenticationStatementsProvider.
     *
     * @return the classname of the CustomAuthenticationStatementsProvider.
     */
    public String getCustomAuthenticationStatementsProviderClassName() {
        return customAuthenticationStatementsProviderClassName;
    }

    /**
     * Gets the classname of the CustomAttributeMapper.
     *
     * @return the classname of the CustomAttributeMapper.
     */
    public String getCustomAttributeMapperClassName() {
        return customAttributeMapperClassName;
    }

    /**
     * Gets the classname of the CustomAuthNContextMapper.
     *
     * @return the classname of the CustomAuthNContextMapper.
     */
    public String getCustomAuthNContextMapperClassName() {
        return customAuthNContextMapperClassName;
    }

    /**
     * Gets the classname of the CustomAttributeStatementsProvider.
     *
     * @return the classname of the CustomAttributeStatementsProvider.
     */
    public String getCustomAttributeStatementsProviderClassName() {
        return customAttributeStatementsProviderClassName;
    }

    /**
     * Gets the classname of the CustomAuthzDecisionStatementsProvider.
     *
     * @return the classname of the CustomAuthzDecisionStatementsProvider.
     */
    public String getCustomAuthzDecisionStatementsProviderClassName() {
        return customAuthzDecisionStatementsProviderClassName;
    }

    /**
     * Gets whether the assertion should be signed.
     *
     * @return whether the assertion should be signed.
     */
    public boolean signAssertion() {
        return signAssertion;
    }

    /**
     * Gets whether the name-id should be encrypted.
     *
     * @return whether the name-id should be encrypted.
     */
    public boolean encryptNameID() {
        return encryptNameID;
    }

    /**
     * Gets whether the attributes should be encrypted.
     *
     * @return whether the attributes should be encrypted.
     */
    public boolean encryptAttributes() {
        return encryptAttributes;
    }

    /**
     * Gets whether the assertion should be encrypted.
     *
     * @return whether the assertion should be encrypted.
     */
    public boolean encryptAssertion() {
        return encryptAssertion;
    }

    /**
     * Gets the encryption algorithm.
     *
     * @return the encryption algorithm.
     */
    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    /**
     * Gets the encryption algorithm strength.
     *
     * @return the encryption algorithm strength.
     */
    public int getEncryptionAlgorithmStrength() {
        return encryptionAlgorithmStrength;
    }

    /**
     * Gets the keystore filename.
     *
     * @return the keystore filename.
     */
    public String getKeystoreFileName() {
        return keystoreFileName;
    }

    /**
     * Gets the keystore password.
     *
     * @return they keystore password.
     */
    public byte[] getKeystorePassword() {
        return keystorePassword;
    }

    /**
     * Gets the SP entity id.
     *
     * @return the SP entity id.
     */
    public String getSpEntityId() {
        return spEntityId;
    }

    /**
     * Gets the SP ACS url.
     *
     * @return the SP ACS url.
     */
    public String getSpAcsUrl() {
        return spAcsUrl;
    }

    /**
     * Gets the encryption key alias.
     *
     * @return the encryption key alias.
     */
    public String getEncryptionKeyAlias() {
        return encryptionKeyAlias;
    }

    /**
     * Gets the signature key alias.
     *
     * @return the signature key alias.
     */
    public String getSignatureKeyAlias() {
        return signatureKeyAlias;
    }

    /**
     * Gets the signature key password.
     *
     * @return the signature key password.
     */
    public byte[] getSignatureKeyPassword() {
        return signatureKeyPassword;
    }

    /**
     * Gets the Identity Provider id.
     *
     * @return the Identity Provider id.
     */
    public String getIdpId() {
        return idpId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SAML2Config instance:").append('\n');
        sb.append('\t').append("IDP id: ").append(idpId).append('\n');
        sb.append('\t').append("nameIDFormat: ").append(nameIdFormat).append('\n');
        sb.append('\t').append("attributeMap: ").append(attributeMap).append('\n');
        sb.append('\t').append("tokenLifetimeInSeconds: ").append(tokenLifetimeInSeconds).append('\n');
        sb.append('\t').append("customConditionsProviderClassName: ").append(customConditionsProviderClassName).append('\n');
        sb.append('\t').append("customSubjectProviderClassName: ").append(customSubjectProviderClassName).append('\n');
        sb.append('\t').append("customAttributeStatementsProviderClassName: ").append(customAttributeStatementsProviderClassName).append('\n');
        sb.append('\t').append("customAttributeMapperClassName: ").append(customAttributeMapperClassName).append('\n');
        sb.append('\t').append("customAuthNContextMapperClassName: ").append(customAuthNContextMapperClassName).append('\n');
        sb.append('\t').append("customAuthenticationStatementsProviderClassName: ").append(customAuthenticationStatementsProviderClassName).append('\n');
        sb.append('\t').append("customAuthzDecisionStatementsProviderClassName: ").append(customAuthzDecisionStatementsProviderClassName).append('\n');
        sb.append('\t').append("Sign assertion ").append(signAssertion).append('\n');
        sb.append('\t').append("Encrypt NameID ").append(encryptNameID).append('\n');
        sb.append('\t').append("Encrypt Attributes ").append(encryptAttributes).append('\n');
        sb.append('\t').append("Encrypt Assertion ").append(encryptAssertion).append('\n');
        sb.append('\t').append("Encryption Algorithm ").append(encryptionAlgorithm).append('\n');
        sb.append('\t').append("Encryption Algorithm Strength ").append(encryptionAlgorithmStrength).append('\n');
        sb.append('\t').append("Keystore File ").append(keystoreFileName).append('\n');
        sb.append('\t').append("Keystore Password ").append("xxx").append('\n');
        sb.append('\t').append("SP Entity Id ").append(spEntityId).append('\n');
        sb.append('\t').append("SP ACS URL ").append(spAcsUrl).append('\n');
        sb.append('\t').append("Encryption key alias ").append(encryptionKeyAlias).append('\n');
        sb.append('\t').append("Signature key alias").append(signatureKeyAlias).append('\n');
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof SAML2Config) {
            SAML2Config otherConfig = (SAML2Config)other;
            return  nameIdFormat.equals(otherConfig.nameIdFormat) &&
                    idpId.equals(otherConfig.idpId) &&
                    tokenLifetimeInSeconds == otherConfig.tokenLifetimeInSeconds &&
                    attributeMap.equals(otherConfig.attributeMap) &&
                    signAssertion == otherConfig.signAssertion &&
                    encryptAssertion == otherConfig.encryptAssertion &&
                    encryptAttributes == otherConfig.encryptAttributes &&
                    encryptNameID == otherConfig.encryptNameID &&
                    encryptionAlgorithmStrength == otherConfig.encryptionAlgorithmStrength &&
                    spEntityId.equals(otherConfig.spEntityId) &&
                    Objects.equal(encryptionAlgorithm, otherConfig.encryptionAlgorithm) &&
                    Objects.equal(customConditionsProviderClassName, otherConfig.customConditionsProviderClassName) &&
                    Objects.equal(customSubjectProviderClassName, otherConfig.customSubjectProviderClassName) &&
                    Objects.equal(customAttributeStatementsProviderClassName, otherConfig.customAttributeStatementsProviderClassName) &&
                    Objects.equal(customAuthzDecisionStatementsProviderClassName, otherConfig.customAuthzDecisionStatementsProviderClassName) &&
                    Objects.equal(customAttributeMapperClassName, otherConfig.customAttributeMapperClassName) &&
                    Objects.equal(customAuthNContextMapperClassName, otherConfig.customAuthNContextMapperClassName) &&
                    Objects.equal(customAuthenticationStatementsProviderClassName, otherConfig.customAuthenticationStatementsProviderClassName) &&
                    Objects.equal(keystoreFileName, otherConfig.keystoreFileName) &&
                    Arrays.equals(keystorePassword, otherConfig.keystorePassword) &&
                    Objects.equal(spAcsUrl, otherConfig.spAcsUrl) &&
                    Objects.equal(signatureKeyAlias, otherConfig.signatureKeyAlias) &&
                    Objects.equal(encryptionKeyAlias, otherConfig.encryptionKeyAlias) &&
                    Arrays.equals(signatureKeyPassword, otherConfig.signatureKeyPassword);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return (nameIdFormat + attributeMap + spEntityId + Long.toString(tokenLifetimeInSeconds)).hashCode();
    }

    /**
     * Gets the {@link JsonValue} representation of the SAML2Config.
     *
     * @return The {@link JsonValue} representation of the SAML2Config.
     */
    public JsonValue toJson() {
        /*
        Because toJson will be used to produce the map that will also be used to marshal to the SMS attribute map
        format, and because the SMS attribute map format represents all values as Set<String>, I need to represent all
        of the json values as strings as well.
        */
        try {
            return json(object(
                    field(ISSUER_NAME, idpId),
                    field(NAME_ID_FORMAT, nameIdFormat),
                    field(TOKEN_LIFETIME, String.valueOf(tokenLifetimeInSeconds)),
                    field(CUSTOM_CONDITIONS_PROVIDER_CLASS, customConditionsProviderClassName),
                    field(CUSTOM_SUBJECT_PROVIDER_CLASS, customSubjectProviderClassName),
                    field(CUSTOM_ATTRIBUTE_STATEMENTS_PROVIDER_CLASS, customAttributeStatementsProviderClassName),
                    field(CUSTOM_ATTRIBUTE_MAPPER_CLASS, customAttributeMapperClassName),
                    field(CUSTOM_AUTHN_CONTEXT_MAPPER_CLASS, customAuthNContextMapperClassName),
                    field(CUSTOM_AUTHENTICATION_STATEMENTS_PROVIDER_CLASS, customAuthenticationStatementsProviderClassName),
                    field(CUSTOM_AUTHZ_DECISION_STATEMENTS_PROVIDER_CLASS, customAuthzDecisionStatementsProviderClassName),
                    field(SIGN_ASSERTION, String.valueOf(signAssertion)),
                    field(ENCRYPT_ASSERTION, String.valueOf(encryptAssertion)),
                    field(ENCRYPT_ATTRIBUTES, String.valueOf(encryptAttributes)),
                    field(ENCRYPT_NAME_ID, String.valueOf(encryptNameID)),
                    field(ENCRYPTION_ALGORITHM, encryptionAlgorithm),
                    field(ENCRYPTION_ALGORITHM_STRENGTH, String.valueOf(encryptionAlgorithmStrength)),
                    field(ATTRIBUTE_MAP, attributeMap),
                    field(KEYSTORE_FILE_NAME, keystoreFileName),
                    field(KEYSTORE_PASSWORD,
                            keystorePassword != null ? new String(keystorePassword, AMSTSConstants.UTF_8_CHARSET_ID) : null),
                    field(SP_ACS_URL, spAcsUrl),
                    field(SP_ENTITY_ID, spEntityId),
                    field(SIGNATURE_KEY_ALIAS, signatureKeyAlias),
                    field(SIGNATURE_KEY_PASSWORD,
                            signatureKeyPassword != null ? new String(signatureKeyPassword, AMSTSConstants.UTF_8_CHARSET_ID) : null),
                    field(ENCRYPTION_KEY_ALIAS, encryptionKeyAlias)));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported encoding when marshalling from String to to byte[]: " + e, e);
        }
    }

    /**
     * Creates a SAML2Config object from a {@link JsonValue} representation
     *
     * @param json the {@link JsonValue} representation.
     * @return a SAML2Config object
     * @throws IllegalStateException
     */
    public static SAML2Config fromJson(JsonValue json) throws IllegalStateException {
        try {
            return SAML2Config.builder()
                    .idpId(json.get(ISSUER_NAME).asString())
                    .nameIdFormat(json.get(NAME_ID_FORMAT).asString())
                    //because we have to go to the SMS Map representation, where all values are Set<String>, I need to
                    // pull the value from Json as a string, and then parse out a Long.
                    .tokenLifetimeInSeconds(Long.valueOf(json.get(TOKEN_LIFETIME).asString()))
                    .customConditionsProviderClassName(json.get(CUSTOM_CONDITIONS_PROVIDER_CLASS).asString())
                    .customSubjectProviderClassName(json.get(CUSTOM_SUBJECT_PROVIDER_CLASS).asString())
                    .customAttributeStatementsProviderClassName(json.get(CUSTOM_ATTRIBUTE_STATEMENTS_PROVIDER_CLASS).asString())
                    .customAttributeMapperClassName(json.get(CUSTOM_ATTRIBUTE_MAPPER_CLASS).asString())
                    .customAuthNContextMapperClassName(json.get(CUSTOM_AUTHN_CONTEXT_MAPPER_CLASS).asString())
                    .customAuthenticationStatementsProviderClassName(json.get(CUSTOM_AUTHENTICATION_STATEMENTS_PROVIDER_CLASS).asString())
                    .customAuthzDecisionStatementsProviderClassName(json.get(CUSTOM_AUTHZ_DECISION_STATEMENTS_PROVIDER_CLASS).asString())
                    .signAssertion(Boolean.valueOf(json.get(SIGN_ASSERTION).asString()))
                    .encryptAssertion(Boolean.valueOf(json.get(ENCRYPT_ASSERTION).asString()))
                    .encryptNameID(Boolean.valueOf(json.get(ENCRYPT_NAME_ID).asString()))
                    .encryptAttributes(Boolean.valueOf(json.get(ENCRYPT_ATTRIBUTES).asString()))
                    .encryptionAlgorithm(json.get(ENCRYPTION_ALGORITHM).asString())
                    .encryptionAlgorithmStrength(Integer.valueOf(json.get(ENCRYPTION_ALGORITHM_STRENGTH).asString()))
                    .attributeMap(json.get(ATTRIBUTE_MAP).asMap(String.class))
                    .keystoreFile(json.get(KEYSTORE_FILE_NAME).asString())
                    .keystorePassword(json.get(KEYSTORE_PASSWORD).isString()
                            ? json.get(KEYSTORE_PASSWORD).asString().getBytes(AMSTSConstants.UTF_8_CHARSET_ID) : null)
                    .signatureKeyPassword(json.get(SIGNATURE_KEY_PASSWORD).isString()
                            ? json.get(SIGNATURE_KEY_PASSWORD).asString().getBytes(AMSTSConstants.UTF_8_CHARSET_ID) : null)
                    .signatureKeyAlias(json.get(SIGNATURE_KEY_ALIAS).asString())
                    .spAcsUrl(json.get(SP_ACS_URL).asString())
                    .spEntityId(json.get(SP_ENTITY_ID).asString())
                    .encryptionKeyAlias(json.get(ENCRYPTION_KEY_ALIAS).asString())
                    .build();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported encoding when marshalling from String to to byte[]: " + e, e);
        }
    }

    /**
     * Marshals the SAML2Config into an attribute map
     *
     * @return a map containing the SAML2Config attributes.
     */
    public Map<String, Set<String>> marshalToAttributeMap() {
        /*
        We need to marshal the SAML2Config instance to a Map<String, Object>. The JsonValue of toJson gets us there,
        except for the complex types for the audiences and attribute map. These need to be marshaled into aSet<String>,
        and these entries included in the top-level map, replacing the existing complex entries.
        */
        Map<String, Object> preMap = toJson().asMap();
        Map<String, Set<String>> finalMap = MapMarshallUtils.toSmsMap(preMap);
        Object attributesObject = preMap.get(ATTRIBUTE_MAP);
        if (attributesObject instanceof Map) {
            finalMap.remove(ATTRIBUTE_MAP);
            Set<String> attributeValues = new LinkedHashSet<>();
            finalMap.put(ATTRIBUTE_MAP, attributeValues);
            for (Map.Entry<String, String> entry : ((Map<String, String>)attributesObject).entrySet()) {
                attributeValues.add(entry.getKey() + EQUALS + entry.getValue());
            }
        } else {
            throw new IllegalStateException("Type corresponding to " + ATTRIBUTE_MAP + " key unexpected. Type: "
                    + (attributesObject != null ? attributesObject.getClass().getName() :" null"));
        }
        return finalMap;
    }

    /**
     * Marshals an attribute map into a SAML2Config
     *
     * @param smsAttributeMap the attribute map.
     * @return a SAML2Config object.
     */
    public static SAML2Config marshalFromAttributeMap(Map<String, Set<String>> smsAttributeMap) {
        /*
        Here we have to modify the ATTRIBUTE_MAP and AUDIENCES entries to match the JsonValue format expected by
        fromJson, and then call the static fromJson. This method must marshal between the Json representation of a
        complex object, and the representation expected by the SMS
        */
        Set<String> issuerName = smsAttributeMap.get(ISSUER_NAME);
        /*
        The STSInstanceConfig may not have SAML2Config, if there are no defined token transformations that result
        in a SAML2 assertion. So we check for the ISSUER_NAME attribute, which is the IdP id, a mandatory field if
        SAML2 assertions are to be issued.
         */
        if (CollectionUtils.isEmpty(issuerName)) {
            return null;
        }
        Map<String, Object> jsonAttributes = MapMarshallUtils.toJsonValueMap(smsAttributeMap);
        jsonAttributes.remove(ATTRIBUTE_MAP);
        Set<String> attributes = smsAttributeMap.get(ATTRIBUTE_MAP);
        Map<String, Object> jsonAttributeMap = new LinkedHashMap<>();
        for (String entry : attributes) {
            String[] breakdown = entry.split(EQUALS);
            jsonAttributeMap.put(breakdown[0], breakdown[1]);
        }
        jsonAttributes.put(ATTRIBUTE_MAP, new JsonValue(jsonAttributeMap));

        return fromJson(new JsonValue(jsonAttributes));
    }

    /**
     * Returns an empty attribute map.
     *
     * @return an empty attribute map.
     */
    public static Map<String, Set<String>> getEmptySMSAttributeState() {
        /*
        This method is called from Rest/SoapSTSInstanceConfig if the encapsulated SAML2Config reference is null. It
        should return a Map<String,Set<String>> for each of the sms attributes defined for the SAML2Config object, with
        an empty Set<String> value, so that SMS writes will over-write any previous, non-null values. This will occur
        in the AdminUI when a sts instance goes from issuing SAML2 tokens, to not issuing these token types.
        */
        HashMap<String, Set<String>> emptyAttributeMap = new HashMap<>();
        emptyAttributeMap.put(NAME_ID_FORMAT, Collections.<String>emptySet());
        emptyAttributeMap.put(ATTRIBUTE_MAP, Collections.<String>emptySet());
        emptyAttributeMap.put(TOKEN_LIFETIME, Collections.<String>emptySet());
        emptyAttributeMap.put(CUSTOM_CONDITIONS_PROVIDER_CLASS, Collections.<String>emptySet());
        emptyAttributeMap.put(CUSTOM_SUBJECT_PROVIDER_CLASS, Collections.<String>emptySet());
        emptyAttributeMap.put(CUSTOM_ATTRIBUTE_STATEMENTS_PROVIDER_CLASS, Collections.<String>emptySet());
        emptyAttributeMap.put(CUSTOM_AUTHENTICATION_STATEMENTS_PROVIDER_CLASS, Collections.<String>emptySet());
        emptyAttributeMap.put(CUSTOM_AUTHZ_DECISION_STATEMENTS_PROVIDER_CLASS, Collections.<String>emptySet());
        emptyAttributeMap.put(CUSTOM_ATTRIBUTE_MAPPER_CLASS, Collections.<String>emptySet());
        emptyAttributeMap.put(CUSTOM_AUTHN_CONTEXT_MAPPER_CLASS, Collections.<String>emptySet());
        emptyAttributeMap.put(SIGN_ASSERTION, Collections.<String>emptySet());
        emptyAttributeMap.put(ENCRYPT_ATTRIBUTES, Collections.<String>emptySet());
        emptyAttributeMap.put(ENCRYPT_NAME_ID, Collections.<String>emptySet());
        emptyAttributeMap.put(ENCRYPT_ASSERTION, Collections.<String>emptySet());
        emptyAttributeMap.put(ENCRYPTION_ALGORITHM, Collections.<String>emptySet());
        emptyAttributeMap.put(ENCRYPTION_ALGORITHM_STRENGTH, Collections.<String>emptySet());
        emptyAttributeMap.put(KEYSTORE_FILE_NAME, Collections.<String>emptySet());
        emptyAttributeMap.put(KEYSTORE_PASSWORD, Collections.<String>emptySet());
        emptyAttributeMap.put(SP_ENTITY_ID, Collections.<String>emptySet());
        emptyAttributeMap.put(SP_ACS_URL, Collections.<String>emptySet());
        emptyAttributeMap.put(ENCRYPTION_KEY_ALIAS, Collections.<String>emptySet());
        emptyAttributeMap.put(SIGNATURE_KEY_ALIAS, Collections.<String>emptySet());
        emptyAttributeMap.put(SIGNATURE_KEY_PASSWORD, Collections.<String>emptySet());
        emptyAttributeMap.put(ISSUER_NAME, Collections.<String>emptySet());
        return emptyAttributeMap;
    }
}
