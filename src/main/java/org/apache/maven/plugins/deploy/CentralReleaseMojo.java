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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.deploy.CentralPortalClient.CENTRAL_PORTAL_URL;

/**
 * Can be reached with
 * <code>mvn verify deploy:release</code>
 */
@Mojo(name = "release", defaultPhase = LifecyclePhase.DEPLOY)
public class CentralReleaseMojo extends AbstractDeployMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(property = "central.username")
    private String username;

    @Parameter(property = "central.password")
    private String password;

    @Parameter(property = "central.url", defaultValue = CENTRAL_PORTAL_URL)
    private String centralUrl;

    @Parameter(defaultValue = "true", property = "autoDeploy")
    private boolean autoDeploy;

    @Override
    public void execute() throws MojoExecutionException {
        File targetDir = new File(project.getBuild().getDirectory());
        File bundleFile = new File(targetDir, project.getArtifactId() + "-" + project.getVersion() + "-bundle.zip");

        try {
            BundleService bundleService = new BundleService(project, getLog());
            bundleService.createZipBundle(bundleFile);

            CentralPortalClient client = new CentralPortalClient(username, password, centralUrl);

            String deploymentId;
            try {
                deploymentId = client.upload(bundleFile, autoDeploy);
                if (deploymentId == null) {
                    throw new MojoExecutionException("Failed to upload bundle, no deployment id found");
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to upload bundle", e);
            }

            getLog().info("Deployment ID: " + deploymentId);
            Thread.sleep(5000);
            String status = client.getStatus(deploymentId);

            int retries = 10;
            while (!Arrays.asList("PUBLISHING", "PUBLISHED", "FAILED").contains(status) && retries-- > 0) {
                getLog().info("Deploy status is " + status);
                Thread.sleep(5000);
                status = client.getStatus(deploymentId);
            }

            switch (status) {
                case "PUBLISHING":
                    getLog().info("Published: Project is publishing on Central");
                    break;
                case "PUBLISHED":
                    getLog().info("Published successfully");
                    break;
                default:
                    throw new MojoExecutionException("Release failed with status: " + status);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Release process failed", e);
        }
    }
}
