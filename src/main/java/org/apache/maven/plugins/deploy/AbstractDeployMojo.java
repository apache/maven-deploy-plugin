/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.deploy;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.Version;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.Mojo;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.settings.Mirror;
import org.apache.maven.api.settings.Profile;
import org.apache.maven.api.settings.Repository;
import org.apache.maven.api.settings.Server;
import org.apache.maven.api.settings.Settings;

/**
 * Abstract class for Deploy mojo's.
 */
public abstract class AbstractDeployMojo implements Mojo {
    private static final String AFFECTED_MAVEN_PACKAGING = "maven-plugin";

    private static final String FIXED_MAVEN_VERSION = "3.9.0";

    /**
     * User property (not settable from the POM, only via {@code -D} on the command line or in
     * {@code MAVEN_OPTS}/{@code .mvn/maven.config}) that disables the repository id&#8594;URL
     * credential-binding check performed before a credentials-bearing deployment repository is used.
     */
    static final String ALLOW_CREDENTIAL_REUSE_PROPERTY = "maven.deploy.allowCredentialReuse";

    @Inject
    protected Log logger;

    @Inject
    protected Session session;

    /**
     * Flag whether Maven is currently in online/offline mode.
     */
    @Parameter(defaultValue = "${settings.offline}", readonly = true)
    private boolean offline;

    /**
     * Parameter used to control how many times a failed deployment will be retried before giving up and failing. If a
     * value outside the range 1-10 is specified it will be pulled to the nearest value within the range 1-10.
     *
     * @since 2.7
     */
    @Parameter(property = "retryFailedDeploymentCount", defaultValue = "1")
    private int retryFailedDeploymentCount;

    /* Setters and Getters */

    void failIfOffline() throws MojoException {
        if (offline) {
            throw new MojoException("Cannot deploy artifacts when Maven is in offline mode");
        }
    }

    public int getRetryFailedDeploymentCount() {
        return retryFailedDeploymentCount;
    }

    /**
     * If this plugin used in pre-3.9.0 Maven, the packaging {@code maven-plugin} will not deploy G level metadata.
     */
    protected void warnIfAffectedPackagingAndMaven(String packaging) {
        if (AFFECTED_MAVEN_PACKAGING.equals(packaging)) {
            Version fixedMavenVersion = session.parseVersion(FIXED_MAVEN_VERSION);
            Version currentMavenVersion = session.getMavenVersion();
            if (fixedMavenVersion.compareTo(currentMavenVersion) > 0) {
                getLog().warn("");
                getLog().warn("You are about to deploy a maven-plugin using Maven " + currentMavenVersion + ".");
                getLog().warn("This plugin should be used ONLY with Maven 3.9.0 and newer, as MNG-7055");
                getLog().warn("is fixed in those versions of Maven only!");
                getLog().warn("");
            }
        }
    }

    /**
     * Creates resolver {@link RemoteRepository} equipped with needed whistles and bells.
     */
    protected RemoteRepository createDeploymentArtifactRepository(String id, String url) {
        return getSession().createRemoteRepository(id, url);
    }

    /**
     * Guards the repository id&#8594;URL credential binding: Maven resolves the credentials for a
     * deployment repository purely by matching its id against a {@code <server>} entry in
     * {@code settings.xml}, so any component that pairs a known server id with a <em>new</em> URL
     * re-targets those credentials. Equivalent to
     * {@link #validateCredentialBinding(String, String, boolean) validateCredentialBinding(id, url, true)}:
     * this overload is for values that are command-line-supplied by nature (deploy-file's
     * {@code repositoryId}/{@code url} are plain {@code -D} parameters typed by the operator), so
     * the no-URL-on-record case warns instead of refusing.
     *
     * @param id the repository id the deployment would bind credentials for
     * @param url the URL the deployment would send those credentials to
     * @throws MojoException when the binding re-targets known credentials to an unknown URL
     */
    protected void validateCredentialBinding(String id, String url) throws MojoException {
        validateCredentialBinding(id, url, true);
    }

    /**
     * Guards the repository id&#8594;URL credential binding, weighing the <em>provenance</em> of the
     * binding. When the given id matches a {@code settings.xml} server entry (so credentials are at
     * stake), two cases are distinguished:
     * <ul>
     *   <li><b>URLs on record for the id</b> (mirrors, profile repositories, and &mdash; for the
     *   deploy goal &mdash; the project's {@code distributionManagement}): the given URL must match
     *   one of them; otherwise the deployment is refused, naming both URLs &mdash; regardless of
     *   provenance.</li>
     *   <li><b>No URL on record for the id</b>: the binding cannot be cross-checked, which is the
     *   mainline redirection shape (credentials stored for an id such as {@code ossrh} that
     *   settings.xml binds to no URL). A value the operator typed on the command line
     *   ({@code fromUserProperty}) proceeds with a WARN naming the URL the credentials will be sent
     *   to; a POM-sourced value (pom property or plugin configuration &mdash; attacker-writable in
     *   the malicious-POM model) is refused.</li>
     * </ul>
     * Every refusal can be overridden with {@code -D}{@value #ALLOW_CREDENTIAL_REUSE_PROPERTY}{@code =true}
     * (a user property: it cannot be set from a POM).
     *
     * @param id the repository id the deployment would bind credentials for
     * @param url the URL the deployment would send those credentials to
     * @param fromUserProperty whether the id/url pair was supplied on the command line
     *        ({@code -D} session user property) rather than from the POM or plugin configuration
     * @throws MojoException when the binding re-targets known credentials to an unknown URL, or when
     *         a POM-sourced binding pairs stored credentials with a URL this build knows nothing about
     */
    protected void validateCredentialBinding(String id, String url, boolean fromUserProperty) throws MojoException {
        if (id == null || id.isEmpty() || url == null || url.isEmpty()) {
            return;
        }
        Settings settings = session.getSettings();
        if (settings == null) {
            return;
        }
        boolean idHasCredentials = false;
        for (Server server : settings.getServers()) {
            if (id.equals(server.getId())) {
                idHasCredentials = true;
                break;
            }
        }
        if (!idHasCredentials) {
            return;
        }
        Collection<String> knownUrls = getKnownRepositoryUrls(id);
        if (knownUrls.isEmpty()) {
            // The server id carries credentials but no URL is on record for it in this build.
            // This must not pass silently: it is the mainline redirection shape. Provenance decides:
            // an operator-typed (-D) value proceeds with a warning, a POM-sourced value is refused.
            if (fromUserProperty) {
                getLog().warn("Repository id '" + id + "' has credentials stored in settings.xml but no URL is on"
                        + " record for that id in this build; those credentials will be sent to " + url);
                return;
            }
            if (isCredentialReuseAllowed()) {
                getLog().warn("Repository id '" + id + "' has credentials stored in settings.xml but no URL is on"
                        + " record for that id in this build, and the repository was configured from the POM;"
                        + " sending those credentials to " + url + " because -D"
                        + ALLOW_CREDENTIAL_REUSE_PROPERTY + "=true is set");
                return;
            }
            throw new MojoException(
                    "Refusing to deploy: repository id '" + id + "' matches a settings.xml server entry (stored"
                            + " credentials), no URL is on record for that id in this build, and the alternative"
                            + " repository was configured from the POM rather than the command line. The deployment"
                            + " would send those credentials to " + url + ". If this is intentional, supply the"
                            + " repository on the command line (-D user property), or re-run with -D"
                            + ALLOW_CREDENTIAL_REUSE_PROPERTY + "=true (user property; it cannot be set from a POM).");
        }
        String requested = normalizeRepositoryUrl(url);
        for (String known : knownUrls) {
            if (requested.equals(normalizeRepositoryUrl(known))) {
                return;
            }
        }
        String knownList = String.join(", ", knownUrls);
        if (isCredentialReuseAllowed()) {
            getLog().warn("Repository id '" + id + "' binds settings.xml credentials that are on record for "
                    + knownList + " but the deployment targets " + url + "; proceeding because -D"
                    + ALLOW_CREDENTIAL_REUSE_PROPERTY + "=true is set");
            return;
        }
        throw new MojoException(
                "Refusing to deploy: repository id '" + id + "' matches a settings.xml server entry whose"
                        + " credentials are on record for " + knownList + ", but the deployment would send them to "
                        + url + ". If this redirection is intentional, re-run with -D"
                        + ALLOW_CREDENTIAL_REUSE_PROPERTY + "=true (user property; it cannot be set from a POM).");
    }

    /**
     * Collects the URLs this build already associates with the given repository id: mirror entries
     * and profile repositories from {@code settings.xml}. Subclasses add further sources (the
     * deploy goal adds the project's {@code distributionManagement}).
     * <p>
     * <b>Trust note:</b> sources read from the project model (such as {@code distributionManagement})
     * are attacker-controlled in the malicious-POM model and are therefore <em>advisory</em>: they can
     * only widen the accepted set for the mismatch check, never authorize a binding by their absence
     * &mdash; the empty-record case is handled by provenance in
     * {@link #validateCredentialBinding(String, String, boolean)}. Settings.xml-sourced entries
     * (mirrors, profiles) are operator-controlled.
     */
    protected Collection<String> getKnownRepositoryUrls(String id) {
        Collection<String> urls = new LinkedHashSet<>();
        Settings settings = session.getSettings();
        if (settings != null) {
            for (Mirror mirror : settings.getMirrors()) {
                if (id.equals(mirror.getId()) && mirror.getUrl() != null) {
                    urls.add(mirror.getUrl());
                }
            }
            for (Profile profile : settings.getProfiles()) {
                for (Repository repository : profile.getRepositories()) {
                    if (id.equals(repository.getId()) && repository.getUrl() != null) {
                        urls.add(repository.getUrl());
                    }
                }
                for (Repository repository : profile.getPluginRepositories()) {
                    if (id.equals(repository.getId()) && repository.getUrl() != null) {
                        urls.add(repository.getUrl());
                    }
                }
            }
        }
        return urls;
    }

    private boolean isCredentialReuseAllowed() {
        Map<String, String> userProperties = session.getUserProperties();
        return userProperties != null && Boolean.parseBoolean(userProperties.get(ALLOW_CREDENTIAL_REUSE_PROPERTY));
    }

    static String normalizeRepositoryUrl(String url) {
        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    protected Session getSession() {
        return session;
    }

    protected Log getLog() {
        return logger;
    }
}
