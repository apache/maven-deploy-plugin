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
import java.util.List;

import org.apache.maven.api.Project;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

import static org.apache.maven.plugins.deploy.CentralPortalClient.CENTRAL_PORTAL_URL;

/**
 * mvn deploy:release
 */
@Mojo(name = "release", defaultPhase = "deploy")
public class CentralReleaseMojo extends AbstractDeployMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private Project project;

    @Parameter(property = "central.username")
    private String username;

    @Parameter(property = "central.password")
    private String password;

    @Parameter(property = "central.url", defaultValue = CENTRAL_PORTAL_URL)
    private String centralUrl;

    private static final List<String> CHECKSUM_ALGOS = List.of("MD5", "SHA-1");

    @Override
    public void execute() throws MojoException {
        File targetDir = new File(project.getBuild().getDirectory());
        File bundleFile = new File(targetDir, project.getArtifactId() + "-" + project.getVersion() + "-bundle.zip");

        try {
            BundleService bundleService = new BundleService(project, session, getLog());
            bundleService.createZipBundle(bundleFile);

            CentralPortalClient client = new CentralPortalClient(username, password, centralUrl);

            String deploymentId = client.upload(bundleFile);
            if (deploymentId == null) {
                throw new MojoException("Failed to upload bundle");
            }

            getLog().info("Deployment ID: " + deploymentId);
            String status = client.getStatus(deploymentId);

            int retries = 10;
            while (!List.of("PUBLISHING", "PUBLISHED", "FAILED").contains(status) && retries-- > 0) {
                getLog().info("Deploy status is " + status);
                Thread.sleep(10000);
                status = client.getStatus(deploymentId);
            }

            switch (status) {
                case "PUBLISHING" -> getLog().info("Published: Project is publishing on Central");
                case "PUBLISHED" -> getLog().info("Published successfully");
                default -> throw new MojoException("Release failed with status: " + status);
            }
        } catch (Exception e) {
            throw new MojoException("Release process failed", e);
        }
    }
}
