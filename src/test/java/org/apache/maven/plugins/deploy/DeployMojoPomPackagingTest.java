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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.maven.api.Artifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.model.Repository;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.api.plugin.testing.stubs.SessionStub;
import org.apache.maven.api.services.ArtifactDeployer;
import org.apache.maven.api.services.ArtifactDeployerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.api.services.RepositoryFactory;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.internal.impl.DefaultProject;
import org.apache.maven.internal.impl.InternalSession;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.plugin.testing.stubs.DefaultArtifactHandlerStub;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.codehaus.plexus.testing.PlexusExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.codehaus.plexus.testing.PlexusExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@MojoTest
@ExtendWith(MockitoExtension.class)
public class DeployMojoPomPackagingTest {

    private final String LOCAL_REPO = getBasedir() + "/target/local-repo";

    @Inject
    @SuppressWarnings("unused")
    private InternalSession session;

    @Inject
    @SuppressWarnings("unused")
    private ArtifactManager artifactManager;

    @Inject
    @SuppressWarnings("unused")
    private ProjectManager projectManager;

    @Inject
    @SuppressWarnings("unused")
    private ArtifactDeployer artifactDeployer;

    @Test
    @InjectMojo(goal = "deploy")
    public void testBasicDeployWithPackagingAsPom(DeployMojo mojo) throws Exception {
        assertNotNull(mojo);

        ArtifactDeployerRequest request = execute(mojo);

        assertNotNull(request);
        Collection<Artifact> artifacts = request.getArtifacts();
        assertEquals(
                Collections.singletonList("org.apache.maven.test:maven-deploy-test:pom:1.0-SNAPSHOT"),
                artifacts.stream().map(Artifact::key).collect(Collectors.toList()));
        assertEquals(
                getBasedir().replace(File.separator, "/"),
                request.getRepository().getUrl());
    }

    private ArtifactDeployerRequest execute(DeployMojo mojo) {
        ArgumentCaptor<ArtifactDeployerRequest> requestCaptor = ArgumentCaptor.forClass(ArtifactDeployerRequest.class);
        doNothing().when(artifactDeployer).deploy(requestCaptor.capture());

        mojo.execute();

        List<ArtifactDeployerRequest> requests = requestCaptor.getAllValues();
        assertNotNull(requests);
        return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }

    @Provides
    @Singleton
    @SuppressWarnings("unused")
    private InternalSession createSession() {
        InternalSession session = SessionStub.getMockSession(LOCAL_REPO);
        when(session.getArtifact(any()))
                .thenAnswer(iom -> new org.apache.maven.internal.impl.DefaultArtifact(
                        session, iom.getArgument(0, org.eclipse.aether.artifact.Artifact.class)));
        when(session.createRemoteRepository(any())).thenAnswer(iom -> session.getService(RepositoryFactory.class)
                .createRemote(iom.getArgument(0, Repository.class)));
        return session;
    }

    @Provides
    @Singleton
    @SuppressWarnings("unused")
    private Project createProject() {
        MavenProjectStub project = new MavenProjectStub();
        project.setFile(new File(PlexusExtension.getBasedir(), "src/test/resources/unit/pom.xml"));
        project.setGroupId("org.apache.maven.test");
        project.setArtifactId("maven-deploy-test");
        project.setVersion("1.0-SNAPSHOT");
        project.setPackaging("pom");
        DistributionManagement dm = new DistributionManagement();
        DeploymentRepository dr = new DeploymentRepository();
        dr.setId("remote-repo");
        dr.setUrl(MojoExtension.getBasedir());
        dm.setRepository(dr);
        project.getModel().setDistributionManagement(dm);
        DefaultArtifact artifact = new DefaultArtifact(
                "org.apache.maven.test",
                "maven-deploy-test",
                "1.0-SNAPSHOT",
                null,
                "pom",
                null,
                new DefaultArtifactHandlerStub("pom"));
        // artifact.setFile(new File(PlexusExtension.getBasedir(),
        // "src/test/resources/unit/maven-install-test-1.0-SNAPSHOT.jar"));
        project.setArtifact(artifact);
        project.setAttachedArtifacts(new ArrayList<>());
        return new DefaultProject(session, project);
    }
}
