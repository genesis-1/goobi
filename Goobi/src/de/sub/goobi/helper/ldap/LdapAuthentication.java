package de.sub.goobi.helper.ldap;

/**
 * This file is part of the Goobi Application - a Workflow tool for the support of mass digitization.
 * 
 * Visit the websites for more information.
 *     		- https://goobi.io
 * 			- https://www.intranda.com
 * 			- https://github.com/intranda/goobi
 * 			- http://digiverso.com
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 * Linking this library statically or dynamically with other modules is making a combined work based on this library. Thus, the terms and conditions
 * of the GNU General Public License cover the whole combination. As a special exception, the copyright holders of this library give you permission to
 * link this library with independent modules to produce an executable, regardless of the license terms of these independent modules, and to copy and
 * distribute the resulting executable under terms of your choice, provided that you also meet, for each linked independent module, the terms and
 * conditions of the license of that module. An independent module is a module which is not derived from or based on this library. If you modify this
 * library, you may extend this exception to your version of the library, but you are not obliged to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.goobi.beans.User;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.FilesystemHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.encryption.MD4;

public class LdapAuthentication {
    private static final Logger logger = Logger.getLogger(LdapAuthentication.class);

    public LdapAuthentication() {

    }

    private String getUserDN(User inBenutzer) {
        String userDN = inBenutzer.getLdapGruppe().getUserDN();
        userDN = userDN.replaceAll("\\{login\\}", inBenutzer.getLogin());
        if (inBenutzer.getLdaplogin() != null) {
            userDN = userDN.replaceAll("\\{ldaplogin\\}", inBenutzer.getLdaplogin());
        }
        userDN = userDN.replaceAll("\\{firstname\\}", inBenutzer.getVorname());
        userDN = userDN.replaceAll("\\{lastname\\}", inBenutzer.getNachname());
        return userDN;
    }

    /**
     * create new user in LDAP-directory
     * 
     * @param inBenutzer
     * @param inPasswort
     * @throws NamingException
     * @throws NoSuchAlgorithmException
     * @throws InterruptedException
     * @throws IOException
     */
    public void createNewUser(User inBenutzer, String inPasswort) throws NamingException, NoSuchAlgorithmException, IOException,
    InterruptedException {

        if (!ConfigurationHelper.getInstance().isLdapReadOnly()) {
            Hashtable<String, String> env = LdapConnectionSettings();
            env.put(Context.SECURITY_PRINCIPAL, ConfigurationHelper.getInstance().getLdapAdminLogin());
            env.put(Context.SECURITY_CREDENTIALS, ConfigurationHelper.getInstance().getLdapAdminPassword());

            LdapUser dr = new LdapUser();
            dr.configure(inBenutzer, inPasswort, getNextUidNumber());
            DirContext ctx = new InitialDirContext(env);
            ctx.bind(getUserDN(inBenutzer), dr);
            ctx.close();
            setNextUidNumber();
            Helper.setMeldung(null, Helper.getTranslation("ldapWritten") + " " + inBenutzer.getNachVorname(), "");
            /*
             * -------------------------------- check if HomeDir exists, else create it --------------------------------
             */
            if (logger.isDebugEnabled()) {
                logger.debug("HomeVerzeichnis pruefen");
            }
            String homePath = getUserHomeDirectory(inBenutzer);
            if (!StorageProvider.getInstance().isFileExists(Paths.get(homePath))) {
                if (logger.isDebugEnabled()) {
                    logger.debug("HomeVerzeichnis existiert noch nicht");
                }
                FilesystemHelper.createDirectoryForUser(homePath, inBenutzer.getLogin());
                if (logger.isDebugEnabled()) {
                    logger.debug("HomeVerzeichnis angelegt");
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("HomeVerzeichnis existiert schon");
                }
            }
        } else {
            Helper.setMeldung(Helper.getTranslation("ldapIsReadOnly"));
        }
    }

    /**
     * Check if connection with login and password possible
     * 
     * @param inBenutzer
     * @param inPasswort
     * @return Login correct or not
     */
    public boolean isUserPasswordCorrect(User inBenutzer, String inPasswort) {
        if (logger.isDebugEnabled()) {
            logger.debug("start login session with ldap");
        }
        Hashtable<String, String> env = LdapConnectionSettings();

        // Start TLS
        if (ConfigurationHelper.getInstance().isLdapUseTLS()) {
            if (logger.isDebugEnabled()) {
                logger.debug("use TLS for auth");
            }
            env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, ConfigurationHelper.getInstance().getLdapUrl());
            env.put("java.naming.ldap.version", "3");
            LdapContext ctx = null;
            StartTlsResponse tls = null;
            try {
                ctx = new InitialLdapContext(env, null);

                // Authentication must be performed over a secure channel
                tls = (StartTlsResponse) ctx.extendedOperation(new StartTlsRequest());
                tls.negotiate();

                // Authenticate via SASL EXTERNAL mechanism using client X.509
                // certificate contained in JVM keystore
                ctx.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
                ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, getUserDN(inBenutzer));
                ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, inPasswort);
                ctx.reconnect(null);
                return true;
                // Perform search for privileged attributes under authenticated context

            } catch (IOException e) {
                logger.error("TLS negotiation error:", e);
                return false;
            } catch (NamingException e) {
                logger.error("JNDI error:", e);
                return false;
            } finally {
                if (tls != null) {
                    try {
                        // Tear down TLS connection
                        tls.close();
                    } catch (IOException e) {
                    }
                }
                if (ctx != null) {
                    try {
                        // Close LDAP connection
                        ctx.close();
                    } catch (NamingException e) {
                    }
                }
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("don't use TLS for auth");
            }
            env.put(Context.SECURITY_PRINCIPAL, getUserDN(inBenutzer));
            env.put(Context.SECURITY_CREDENTIALS, inPasswort);
            if (logger.isDebugEnabled()) {
                logger.debug("ldap environment set");
            }
            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("start classic ldap authentification");
                    logger.debug("user DN is " + getUserDN(inBenutzer));
                }
                if (ConfigurationHelper.getInstance().getLdapAttribute() == null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("ldap attribute to test is null");
                    }
                    new InitialDirContext(env);
                    return true;
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("ldap attribute to test is not null");
                    }
                    DirContext ctx = new InitialDirContext(env);

                    Attributes attrs = ctx.getAttributes(getUserDN(inBenutzer));
                    Attribute la = attrs.get(ConfigurationHelper.getInstance().getLdapAttribute());
                    if (logger.isDebugEnabled()) {
                        logger.debug("ldap attributes set");
                    }
                    String test = (String) la.get(0);
                    if (test.equals(ConfigurationHelper.getInstance().getLdapAttributeValue())) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("ldap ok");
                        }
                        ctx.close();
                        return true;
                    } else {
                        if (logger.isDebugEnabled()) {
                            logger.debug("ldap not ok");
                        }
                        ctx.close();
                        return false;
                    }
                }
            } catch (NamingException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("login not allowed for " + inBenutzer.getLogin(), e);
                }
                return false;
            }
        }
    }

    /**
     * retrieve home directory of given user
     * 
     * @param inBenutzer
     * @return path as string
     */
    public String getUserHomeDirectory(User inBenutzer) {
        // skip ldap check, when user is inactive or deleted
        if (!inBenutzer.isIstAktiv() || StringUtils.isNotBlank(inBenutzer.getIsVisible())) {
            return "";
        }
        if (ConfigurationHelper.getInstance().isLdapUseLocalDirectory()) {
            return ConfigurationHelper.getInstance().getUserFolder() + inBenutzer.getLogin();
        }
        // use local directory, when user has no ldap group assigned
        if (inBenutzer.getLdapGruppe() == null) {
            return ConfigurationHelper.getInstance().getUserFolder() + inBenutzer.getLogin();
        }

        Hashtable<String, String> env = LdapConnectionSettings();
        if (ConfigurationHelper.getInstance().isLdapUseTLS()) {

            env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, ConfigurationHelper.getInstance().getLdapUrl());
            env.put("java.naming.ldap.version", "3");
            LdapContext ctx = null;
            StartTlsResponse tls = null;
            try {
                ctx = new InitialLdapContext(env, null);

                // Authentication must be performed over a secure channel
                tls = (StartTlsResponse) ctx.extendedOperation(new StartTlsRequest());
                tls.negotiate();

                // Authenticate via SASL EXTERNAL mechanism using client X.509
                // certificate contained in JVM keystore
                ctx.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
                ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, ConfigurationHelper.getInstance().getLdapAdminLogin());
                ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, ConfigurationHelper.getInstance().getLdapAdminPassword());

                ctx.reconnect(null);

                Attributes attrs = ctx.getAttributes(getUserDN(inBenutzer));
                Attribute la = attrs.get(ConfigurationHelper.getInstance().getLdapHomeDirectory());
                return (String) la.get(0);

                // Perform search for privileged attributes under authenticated context

            } catch (IOException e) {
                logger.error("TLS negotiation error:", e);

                return ConfigurationHelper.getInstance().getUserFolder() + inBenutzer.getLogin();
            } catch (NamingException e) {

                logger.error("JNDI error:", e);

                return ConfigurationHelper.getInstance().getUserFolder() + inBenutzer.getLogin();
            } finally {
                if (tls != null) {
                    try {
                        // Tear down TLS connection
                        tls.close();
                    } catch (IOException e) {
                    }
                }
                if (ctx != null) {
                    try {
                        // Close LDAP connection
                        ctx.close();
                    } catch (NamingException e) {
                    }
                }
            }
        } else if (ConfigurationHelper.getInstance().isLdapReadDirectoryAnonymous()) {
            env.put(Context.SECURITY_AUTHENTICATION, "none");
        } else {
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, ConfigurationHelper.getInstance().getLdapAdminLogin());
            env.put(Context.SECURITY_CREDENTIALS, ConfigurationHelper.getInstance().getLdapAdminPassword());

        }
        DirContext ctx;
        String rueckgabe = "";
        try {
            ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(getUserDN(inBenutzer));
            Attribute la = attrs.get(ConfigurationHelper.getInstance().getLdapHomeDirectory());
            rueckgabe = (String) la.get(0);
            ctx.close();
        } catch (NamingException e) {
            logger.error(e);
        }
        return rueckgabe;
    }

    /**
     * check if User already exists on system
     * 
     * @param inBenutzer
     * @return path as string
     */
    public boolean isUserAlreadyExists(String inLogin) {
        Hashtable<String, String> env = LdapConnectionSettings();
        env.put(Context.SECURITY_PRINCIPAL, ConfigurationHelper.getInstance().getLdapAdminLogin());
        env.put(Context.SECURITY_CREDENTIALS, ConfigurationHelper.getInstance().getLdapAdminPassword());
        DirContext ctx;
        boolean rueckgabe = false;
        try {
            ctx = new InitialDirContext(env);
            Attributes matchAttrs = new BasicAttributes(true);
            NamingEnumeration<SearchResult> answer = ctx.search("ou=users,dc=gdz,dc=sub,dc=uni-goettingen,dc=de", matchAttrs);
            rueckgabe = answer.hasMoreElements();

            while (answer.hasMore()) {
                SearchResult sr = answer.next();
                if (logger.isDebugEnabled()) {
                    logger.debug(">>>" + sr.getName());
                }
                Attributes attrs = sr.getAttributes();
                String givenName = " ";
                String surName = " ";
                String mail = " ";
                String cn = " ";
                String hd = " ";
                try {
                    givenName = attrs.get("givenName").toString();
                } catch (Exception err) {
                    givenName = " ";
                }
                try {
                    surName = attrs.get("sn").toString();
                } catch (Exception e2) {
                    surName = " ";
                }
                try {
                    mail = attrs.get("mail").toString();
                } catch (Exception e3) {
                    mail = " ";
                }
                try {
                    cn = attrs.get("cn").toString();
                } catch (Exception e4) {
                    cn = " ";
                }
                try {
                    hd = attrs.get(ConfigurationHelper.getInstance().getLdapHomeDirectory()).toString();
                } catch (Exception e4) {
                    hd = " ";
                }
                if (logger.isDebugEnabled()) {
                    logger.debug(givenName);
                    logger.debug(surName);
                    logger.debug(mail);
                    logger.debug(cn);
                    logger.debug(hd);
                }
            }

            ctx.close();
        } catch (NamingException e) {
            logger.error(e);
        }
        return rueckgabe;
    }

    /**
     * Get next free uidNumber
     * 
     * @return next free uidNumber
     * @throws NamingException
     */
    private String getNextUidNumber() {
        Hashtable<String, String> env = LdapConnectionSettings();
        env.put(Context.SECURITY_PRINCIPAL, ConfigurationHelper.getInstance().getLdapAdminLogin());
        env.put(Context.SECURITY_CREDENTIALS, ConfigurationHelper.getInstance().getLdapAdminPassword());
        DirContext ctx;
        String rueckgabe = "";
        try {
            ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(ConfigurationHelper.getInstance().getLdapNextId());
            Attribute la = attrs.get("uidNumber");
            rueckgabe = (String) la.get(0);
            ctx.close();
        } catch (NamingException e) {
            logger.error(e);
            Helper.setFehlerMeldung(e.getMessage());
        }
        return rueckgabe;
    }

    /**
     * Set next free uidNumber
     * 
     * @throws NamingException
     */
    private void setNextUidNumber() {
        Hashtable<String, String> env = LdapConnectionSettings();
        env.put(Context.SECURITY_PRINCIPAL, ConfigurationHelper.getInstance().getLdapAdminLogin());
        env.put(Context.SECURITY_CREDENTIALS, ConfigurationHelper.getInstance().getLdapAdminPassword());
        DirContext ctx;

        try {
            ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(ConfigurationHelper.getInstance().getLdapNextId());
            Attribute la = attrs.get("uidNumber");
            String oldValue = (String) la.get(0);
            int bla = Integer.parseInt(oldValue) + 1;

            BasicAttribute attrNeu = new BasicAttribute("uidNumber", String.valueOf(bla));
            ModificationItem[] mods = new ModificationItem[1];
            mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, attrNeu);
            ctx.modifyAttributes(ConfigurationHelper.getInstance().getLdapNextId(), mods);

            ctx.close();
        } catch (NamingException e) {
            logger.error(e);
        }
    }

    public void deleteUser(User inBenutzer) {

        Hashtable<String, String> env = LdapConnectionSettings();
        if (ConfigurationHelper.getInstance().isLdapUseTLS()) {
            env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, ConfigurationHelper.getInstance().getLdapUrl());
            env.put("java.naming.ldap.version", "3");
            LdapContext ctx = null;
            StartTlsResponse tls = null;
            try {
                ctx = new InitialLdapContext(env, null);

                // Authentication must be performed over a secure channel
                tls = (StartTlsResponse) ctx.extendedOperation(new StartTlsRequest());
                tls.negotiate();

                // Authenticate via SASL EXTERNAL mechanism using client X.509
                // certificate contained in JVM keystore
                ctx.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
                ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, ConfigurationHelper.getInstance().getLdapAdminLogin());
                ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, ConfigurationHelper.getInstance().getLdapAdminPassword());
                ctx.reconnect(null);
                ctx.unbind(getUserDN(inBenutzer));
            } catch (IOException e) {
                logger.error("TLS negotiation error:", e);

            } catch (NamingException e) {

                logger.error("JNDI error:", e);

            } finally {
                if (tls != null) {
                    try {
                        // Tear down TLS connection
                        tls.close();
                    } catch (IOException e) {
                    }
                }
                if (ctx != null) {
                    try {
                        // Close LDAP connection
                        ctx.close();
                    } catch (NamingException e) {
                    }
                }
            }
        } else if (ConfigurationHelper.getInstance().isLdapReadDirectoryAnonymous()) {
            env.put(Context.SECURITY_AUTHENTICATION, "none");
        } else {
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, ConfigurationHelper.getInstance().getLdapAdminLogin());
            env.put(Context.SECURITY_CREDENTIALS, ConfigurationHelper.getInstance().getLdapAdminPassword());

        }
        DirContext ctx;
        try {
            ctx = new InitialDirContext(env);
            ctx.unbind(getUserDN(inBenutzer));

            ctx.close();
        } catch (NamingException e) {
            logger.error(e);
        }
    }

    /**
     * change password of given user, needs old password for authentification
     * 
     * @param inUser
     * @param inOldPassword
     * @param inNewPassword
     * @return boolean about result of change
     * @throws NoSuchAlgorithmException
     */
    public boolean changeUserPassword(User inBenutzer, String inOldPassword, String inNewPassword) throws NoSuchAlgorithmException {
        Hashtable<String, String> env = LdapConnectionSettings();
        if (!ConfigurationHelper.getInstance().isLdapReadOnly()) {
            env.put(Context.SECURITY_PRINCIPAL, ConfigurationHelper.getInstance().getLdapAdminLogin());
            env.put(Context.SECURITY_CREDENTIALS, ConfigurationHelper.getInstance().getLdapAdminPassword());

            try {
                DirContext ctx = new InitialDirContext(env);

                /*
                 * -------------------------------- Encryption of password and Base64-Encoding --------------------------------
                 */
                MessageDigest md = MessageDigest.getInstance(ConfigurationHelper.getInstance().getLdapEncryption());
                md.update(inNewPassword.getBytes());
                String digestBase64 = new String(Base64.encodeBase64(md.digest()));
                ModificationItem[] mods = new ModificationItem[4];

                /*
                 * -------------------------------- UserPasswort-Attribut ändern --------------------------------
                 */
                BasicAttribute userpassword = new BasicAttribute("userPassword", "{" + ConfigurationHelper.getInstance().getLdapEncryption() + "}"
                        + digestBase64);

                /*
                 * -------------------------------- LanMgr-Passwort-Attribut ändern --------------------------------
                 */
                BasicAttribute lanmgrpassword = null;
                try {
                    lanmgrpassword = new BasicAttribute("sambaLMPassword", LdapUser.toHexString(LdapUser.lmHash(inNewPassword)));
                    // TODO: Don't catch super class exception, make sure that the password isn't logged here
                } catch (Exception e) {
                    logger.error(e);
                }

                /*
                 * -------------------------------- NTLM-Passwort-Attribut ändern --------------------------------
                 */
                BasicAttribute ntlmpassword = null;
                try {
                    byte hmm[] = MD4.mdfour(inNewPassword.getBytes("UnicodeLittleUnmarked"));
                    ntlmpassword = new BasicAttribute("sambaNTPassword", LdapUser.toHexString(hmm));
                } catch (UnsupportedEncodingException e) {
                    // TODO: Make sure that the password isn't logged here
                    logger.error(e);
                }
                BasicAttribute sambaPwdLastSet = new BasicAttribute("sambaPwdLastSet", String.valueOf(System.currentTimeMillis() / 1000l));
                mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, userpassword);
                mods[1] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, lanmgrpassword);
                mods[2] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, ntlmpassword);
                mods[3] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, sambaPwdLastSet);
                ctx.modifyAttributes(getUserDN(inBenutzer), mods);

                // Close the context when we're done
                ctx.close();
                return true;
            } catch (NamingException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Benutzeranmeldung nicht korrekt oder Passwortänderung nicht möglich", e);
                }
                return false;
            }
        }
        return false;
    }

    private Hashtable<String, String> LdapConnectionSettings() {
        // Set up environment for creating initial context
        Hashtable<String, String> env = new Hashtable<>(11);
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ConfigurationHelper.getInstance().getLdapUrl());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        /* wenn die Verbindung über ssl laufen soll */
        if (ConfigurationHelper.getInstance().isUseLdapSSLConnection()) {
            String keystorepath = ConfigurationHelper.getInstance().getLdapKeystore();
            String keystorepasswd = ConfigurationHelper.getInstance().getLdapKeystoreToken();

            // add all necessary certificates first
            loadCertificates(keystorepath, keystorepasswd);

            // set properties, so that the current keystore is used for SSL
            System.setProperty("javax.net.ssl.keyStore", keystorepath);
            System.setProperty("javax.net.ssl.trustStore", keystorepath);
            System.setProperty("javax.net.ssl.keyStorePassword", keystorepasswd);
            env.put(Context.SECURITY_PROTOCOL, "ssl");
        }
        return env;
    }

    private void loadCertificates(String path, String passwd) {
        /* wenn die Zertifikate noch nicht im Keystore sind, jetzt einlesen */
        Path myPfad = Paths.get(path);
        if (!StorageProvider.getInstance().isFileExists(myPfad)) {
            try {
                FileOutputStream ksos = new FileOutputStream(path);
                // TODO: Rename parameters to something more meaningful, this is quite specific for the GDZ
                FileInputStream cacertFile = new FileInputStream(ConfigurationHelper.getInstance().getLdapRootCert());
                FileInputStream certFile2 = new FileInputStream(ConfigurationHelper.getInstance().getLdapPdcCert());

                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate cacert = (X509Certificate) cf.generateCertificate(cacertFile);
                X509Certificate servercert = (X509Certificate) cf.generateCertificate(certFile2);

                KeyStore ks = KeyStore.getInstance("jks");
                char[] password = passwd.toCharArray();

                // TODO: Let this method really load a keystore if configured
                // initalize the keystore, if file is available, load the keystore
                ks.load(null);

                ks.setCertificateEntry("ROOTCERT", cacert);
                ks.setCertificateEntry("PDC", servercert);

                ks.store(ksos, password);
                ksos.close();
            } catch (Exception e) {
                logger.error(e);
            }

        }
    }

}