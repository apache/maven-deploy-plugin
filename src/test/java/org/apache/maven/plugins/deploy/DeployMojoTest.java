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
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Priority;
import org.apache.maven.api.di.Provides;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.model.Repository;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.api.plugin.testing.stubs.ProducedArtifactStub;
import org.apache.maven.api.plugin.testing.stubs.ProjectStub;
import org.apache.maven.api.plugin.testing.stubs.SessionMock;
import org.apache.maven.api.services.ArtifactDeployer;
import org.apache.maven.api.services.ArtifactDeployerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.api.services.RepositoryFactory;
import org.apache.maven.internal.impl.InternalSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.maven.api.plugin.testing.MojoExtension.getBasedir;
import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest
@ExtendWith(MockitoExtension.class)
public class DeployMojoTest {

    private static final String LOCAL_REPO = "target/local-repo";

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
    public void testDeployTestEnvironment(DeployMojo mojo) {
        assertNotNull(mojo);
    }

    @Test
    @InjectMojo(goal = "deploy")
    @MojoParameter(name = "deployAtEnd", value = "false")
    public void testBasicDeploy(DeployMojo mojo) throws Exception {
        assertNotNull(mojo);
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        artifactManager.setPath(
                project.getMainArtifact().get(),
                Paths.get(getBasedir(), "target/test-classes/unit/maven-deploy-test-1.0-SNAPSHOT.jar"));

        ArtifactDeployerRequest request = execute(mojo);

        assertNotNull(request);
        Collection<Artifact> artifacts = request.getArtifacts();
        assertEquals(
                Arrays.asList(
                        "org.apache.maven.test:maven-deploy-test:pom:1.0-SNAPSHOT",
                        "org.apache.maven.test:maven-deploy-test:jar:1.0-SNAPSHOT"),
                artifacts.stream().map(Artifact::key).collect(Collectors.toList()));
        assertEquals(
                Paths.get(getBasedir()).toUri().toString(),
                request.getRepository().getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    @MojoParameter(name = "deployAtEnd", value = "false")
    public void testSkippingDeploy(DeployMojo mojo) throws Exception {
        assertNotNull(mojo);

        File file = new File(getBasedir(), "target/test-classes/unit/maven-deploy-test-1.0-SNAPSHOT.jar");
        assertTrue(file.exists());
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        String packaging = project.getPackaging().id();
        assertEquals("jar", packaging);
        artifactManager.setPath(project.getMainArtifact().get(), file.toPath());

        setVariableValueToObject(mojo, "skip", Boolean.TRUE.toString());

        ArtifactDeployerRequest request = execute(mojo);
        assertNull(request);
    }

    @Test
    @InjectMojo(goal = "deploy")
    @MojoParameter(name = "deployAtEnd", value = "false")
    public void testDeployIfArtifactFileIsNull(DeployMojo mojo) throws Exception {
        assertNotNull(mojo);

        Project project = (Project) getVariableValueFromObject(mojo, "project");
        assertFalse(artifactManager.getPath(project.getMainArtifact().get()).isPresent());

        assertThrows(MojoException.class, mojo::execute, "Did not throw mojo execution exception");
    }

    @Test
    @InjectMojo(goal = "deploy")
    @MojoParameter(name = "deployAtEnd", value = "false")
    public void testDeployWithAttachedArtifacts(DeployMojo mojo) throws Exception {
        assertNotNull(mojo);
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        projectManager.attachArtifact(
                project,
                new ProducedArtifactStub("org.apache.maven.test", "attached-artifact-test", "", "1.0-SNAPSHOT", "jar"),
                Paths.get(getBasedir(), "target/test-classes/unit/attached-artifact-test-1.0-SNAPSHOT.jar"));
        artifactManager.setPath(
                project.getMainArtifact().get(),
                Paths.get(getBasedir(), "target/test-classes/unit/maven-deploy-test-1.0-SNAPSHOT.jar"));

        ArtifactDeployerRequest request = execute(mojo);

        assertNotNull(request);
        Collection<Artifact> artifacts = request.getArtifacts();
        assertEquals(
                Arrays.asList(
                        "org.apache.maven.test:maven-deploy-test:pom:1.0-SNAPSHOT",
                        "org.apache.maven.test:maven-deploy-test:jar:1.0-SNAPSHOT",
                        "org.apache.maven.test:attached-artifact-test:jar:1.0-SNAPSHOT"),
                artifacts.stream().map(Artifact::key).collect(Collectors.toList()));
        assertEquals(
                Paths.get(getBasedir()).toUri().toString(),
                request.getRepository().getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    public void testLegacyAltDeploymentRepositoryWithDefaultLayout(DeployMojo mojo) throws IllegalAccessException {
        setVariableValueToObject(mojo, "altDeploymentRepository", "altDeploymentRepository::default::http://localhost");

        RemoteRepository repository = mojo.getDeploymentRepository(true);
        assertEquals("altDeploymentRepository", repository.getId());
        assertEquals("http://localhost", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    public void testLegacyAltDeploymentRepositoryWithLegacyLayout(DeployMojo mojo) throws IllegalAccessException {
        setVariableValueToObject(mojo, "altDeploymentRepository", "altDeploymentRepository::legacy::http://localhost");

        MojoException e = assertThrows(
                MojoException.class,
                () -> mojo.getDeploymentRepository(true),
                "Should throw: Invalid legacy syntax and layout for repository.");
        assertEquals(e.getMessage(), "Invalid legacy syntax and layout for repository.");
        assertEquals(
                e.getLongMessage(),
                "Invalid legacy syntax and layout for alternative repository. Use \"altDeploymentRepository::http://localhost\" instead, and only default layout is supported.");
    }

    @Test
    @InjectMojo(goal = "deploy")
    public void testInsaneAltDeploymentRepository(DeployMojo mojo) throws IllegalAccessException {
        setVariableValueToObject(
                mojo, "altDeploymentRepository", "altDeploymentRepository::hey::wow::foo::http://localhost");

        MojoException e = assertThrows(
                MojoException.class,
                () -> mojo.getDeploymentRepository(true),
                "Should throw: Invalid legacy syntax and layout for repository.");
        assertEquals(e.getMessage(), "Invalid legacy syntax and layout for repository.");
        assertEquals(
                e.getLongMessage(),
                "Invalid legacy syntax and layout for alternative repository. Use \"altDeploymentRepository::wow::foo::http://localhost\" instead, and only default layout is supported.");
    }

    @Test
    @InjectMojo(goal = "deploy")
    public void testDefaultScmSvnAltDeploymentRepository(DeployMojo mojo) throws IllegalAccessException {
        setVariableValueToObject(
                mojo, "altDeploymentRepository", "altDeploymentRepository::default::scm:svn:http://localhost");

        RemoteRepository repository = mojo.getDeploymentRepository(true);
        assertEquals("altDeploymentRepository", repository.getId());
        assertEquals("scm:svn:http://localhost", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    public void testLegacyScmSvnAltDeploymentRepository(DeployMojo mojo) throws IllegalAccessException {
        setVariableValueToObject(
                mojo, "altDeploymentRepository", "altDeploymentRepository::legacy::scm:svn:http://localhost");

        MojoException e = assertThrows(
                MojoException.class,
                () -> mojo.getDeploymentRepository(true),
                "Should throw: Invalid legacy syntax and layout for repository.");
        assertEquals(e.getMessage(), "Invalid legacy syntax and layout for repository.");
        assertEquals(
                e.getLongMessage(),
                "Invalid legacy syntax and layout for alternative repository. Use \"altDeploymentRepository::scm:svn:http://localhost\" instead, and only default layout is supported.");
    }

    @Test
    @InjectMojo(goal = "deploy")
    public void testAltSnapshotDeploymentRepository(DeployMojo mojo) throws IllegalAccessException {
        setVariableValueToObject(mojo, "altDeploymentRepository", "altReleaseDeploymentRepository::http://localhost");

        RemoteRepository repository = mojo.getDeploymentRepository(true);
        assertEquals("altReleaseDeploymentRepository", repository.getId());
        assertEquals("http://localhost", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    public void testAltReleaseDeploymentRepository(DeployMojo mojo) throws IllegalAccessException {
        setVariableValueToObject(mojo, "altDeploymentRepository", "altReleaseDeploymentRepository::http://localhost");

        RemoteRepository repository = mojo.getDeploymentRepository(false);
        assertEquals("altReleaseDeploymentRepository", repository.getId());
        assertEquals("http://localhost", repository.getUrl());
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
    @Priority(10)
    @SuppressWarnings("unused")
    private InternalSession createSession() {
        InternalSession session = SessionMock.getMockSession(LOCAL_REPO);
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
        ProjectStub project = new ProjectStub();
        project.setBasedir(Paths.get(getBasedir()));
        project.setPomPath(Paths.get(getBasedir(), "src/test/resources/unit/pom.xml"));
        project.setGroupId("org.apache.maven.test");
        project.setArtifactId("maven-deploy-test");
        project.setVersion("1.0-SNAPSHOT");
        project.setPackaging("jar");
        project.setModel(project.getModel()
                .withDistributionManagement(org.apache.maven.api.model.DistributionManagement.newBuilder()
                        .repository(org.apache.maven.api.model.DeploymentRepository.newBuilder()
                                .id("remote-repo")
                                .url(Paths.get(getBasedir()).toUri().toString())
                                .build())
                        .build()));
        ProducedArtifactStub jar =
                new ProducedArtifactStub("org.apache.maven.test", "maven-deploy-test", "", "1.0-SNAPSHOT", "jar");
        project.setMainArtifact(jar);
        return project;
    }
}
