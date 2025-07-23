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

import org.apache.maven.api.Project;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

/**
 * mvn deploy:bundle
 */
@Mojo(name = "bundle", defaultPhase = "package")
public class CentralBundleMojo extends AbstractDeployMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private Project project;

    @Override
    public void execute() throws MojoException {
        File targetDir = new File(project.getBuild().getDirectory());
        File bundleFile = new File(targetDir, project.getArtifactId() + "-" + project.getVersion() + "-bundle.zip");

        try {
            BundleService bundleService = new BundleService(project, session, getLog());
            bundleService.createZipBundle(bundleFile);
            getLog().info("Bundle created successfully: " + bundleFile);
        } catch (Exception e) {
            throw new MojoException("Failed to create bundle", e);
        }
    }
}
