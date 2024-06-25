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

import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.Version;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.Mojo;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Parameter;

/**
 * Abstract class for Deploy mojo's.
 */
public abstract class AbstractDeployMojo implements Mojo {
    private static final String AFFECTED_MAVEN_PACKAGING = "maven-plugin";

    private static final String FIXED_MAVEN_VERSION = "3.9.0";

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

    protected Session getSession() {
        return session;
    }

    protected Log getLog() {
        return logger;
    }
}
