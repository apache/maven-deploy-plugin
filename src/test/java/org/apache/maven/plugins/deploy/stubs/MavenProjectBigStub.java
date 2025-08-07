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
package org.apache.maven.plugins.deploy.stubs;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.model.Build;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;

public class MavenProjectBigStub extends org.apache.maven.plugin.testing.stubs.MavenProjectStub {
    private ArtifactRepository deploymentRepository;
    private Build build;
    private Artifact artifact;
    private File file;
    private final List<Artifact> attachedArtifacts = new ArrayList<>();
    private DistributionManagement distributionManagement;

    @Override
    public Artifact getArtifact() {
        return artifact;
    }

    @Override
    public void setArtifact(Artifact artifact) {
        this.artifact = artifact;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public void setFile(File file) {
        this.file = file;
    }

    public ArtifactRepository getDistributionManagementArtifactRepository() {
        if (deploymentRepository != null) {
            return deploymentRepository;
        }
        if (distributionManagement != null && distributionManagement.getRepository() != null) {
            DeploymentRepository repo = distributionManagement.getRepository();
            return new DefaultArtifactRepository(repo.getId(), repo.getUrl(), new DefaultRepositoryLayout());
        }
        return null;
    }

    public void setReleaseArtifactRepository(ArtifactRepositoryStub repo) {
        this.deploymentRepository = repo;
    }

    public ArtifactRepository getReleaseArtifactRepository() {
        return deploymentRepository;
    }

    @Override
    public Build getBuild() {
        if (build == null) {
            Plugin plugin = new Plugin();
            plugin.setGroupId("org.apache.maven.plugins");
            plugin.setArtifactId("maven-deploy-plugin");
            PluginExecution pluginExecution = new PluginExecution();
            pluginExecution.setGoals(Collections.singletonList("deploy"));
            plugin.setExecutions(Collections.singletonList(pluginExecution));
            Build bld = new Build();
            bld.setPlugins(Collections.singletonList(plugin));
            bld.setDirectory("target");
            this.build = bld;
        }
        return build;
    }

    @Override
    public void setBuild(Build build) {
        this.build = build;
    }

    @Override
    public void addAttachedArtifact(Artifact artifact) {
        this.attachedArtifacts.add(artifact);
    }

    @Override
    public List<Artifact> getAttachedArtifacts() {
        return this.attachedArtifacts;
    }

    @Override
    public DistributionManagement getDistributionManagement() {
        return distributionManagement;
    }

    @Override
    public void setDistributionManagement(DistributionManagement distributionManagement) {
        this.distributionManagement = distributionManagement;
    }
}
