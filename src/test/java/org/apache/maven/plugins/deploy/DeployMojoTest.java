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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.ProducedArtifact;
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
import org.apache.maven.api.settings.Mirror;
import org.apache.maven.api.settings.Server;
import org.apache.maven.api.settings.Settings;
import org.apache.maven.impl.InternalSession;
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
class DeployMojoTest {

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
    void deployTestEnvironment(DeployMojo mojo) {
        assertNotNull(mojo);
    }

    @Test
    @InjectMojo(goal = "deploy")
    @MojoParameter(name = "deployAtEnd", value = "false")
    void basicDeploy(DeployMojo mojo) throws Exception {
        assertNotNull(mojo);
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        artifactManager.setPath(
                project.getMainArtifact().get(),
                Paths.get(getBasedir(), "target/test-classes/unit/maven-deploy-test-1.0-SNAPSHOT.jar"));

        ArtifactDeployerRequest request = execute(mojo);

        assertNotNull(request);
        Collection<ProducedArtifact> artifacts = request.getArtifacts();
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
    void skippingDeploy(DeployMojo mojo) throws Exception {
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
    void deployIfArtifactFileIsNull(DeployMojo mojo) throws Exception {
        assertNotNull(mojo);

        Project project = (Project) getVariableValueFromObject(mojo, "project");
        assertFalse(artifactManager.getPath(project.getMainArtifact().get()).isPresent());

        assertThrows(MojoException.class, mojo::execute, "Did not throw mojo execution exception");
    }

    @Test
    @InjectMojo(goal = "deploy")
    @MojoParameter(name = "deployAtEnd", value = "false")
    void deployWithAttachedArtifacts(DeployMojo mojo) throws Exception {
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
        Collection<ProducedArtifact> artifacts = request.getArtifacts();
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
    void legacyAltDeploymentRepositoryWithDefaultLayout(DeployMojo mojo) throws Exception {
        setVariableValueToObject(mojo, "altDeploymentRepository", "altDeploymentRepository::default::http://localhost");

        RemoteRepository repository = mojo.getDeploymentRepository(true);
        assertEquals("altDeploymentRepository", repository.getId());
        assertEquals("http://localhost", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void legacyAltDeploymentRepositoryWithLegacyLayout(DeployMojo mojo) throws Exception {
        setVariableValueToObject(mojo, "altDeploymentRepository", "altDeploymentRepository::legacy::http://localhost");

        MojoException e = assertThrows(
                MojoException.class,
                () -> mojo.getDeploymentRepository(true),
                "Should throw: Invalid legacy syntax and layout for repository.");
        assertEquals("Invalid legacy syntax and layout for repository.", e.getMessage());
        assertEquals(
                "Invalid legacy syntax and layout for alternative repository. Use \"altDeploymentRepository::http://localhost\" instead, and only default layout is supported.",
                e.getLongMessage());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void insaneAltDeploymentRepository(DeployMojo mojo) throws Exception {
        setVariableValueToObject(
                mojo, "altDeploymentRepository", "altDeploymentRepository::hey::wow::foo::http://localhost");

        MojoException e = assertThrows(
                MojoException.class,
                () -> mojo.getDeploymentRepository(true),
                "Should throw: Invalid legacy syntax and layout for repository.");
        assertEquals("Invalid legacy syntax and layout for repository.", e.getMessage());
        assertEquals(
                "Invalid legacy syntax and layout for alternative repository. Use \"altDeploymentRepository::wow::foo::http://localhost\" instead, and only default layout is supported.",
                e.getLongMessage());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void defaultScmSvnAltDeploymentRepository(DeployMojo mojo) throws Exception {
        setVariableValueToObject(
                mojo, "altDeploymentRepository", "altDeploymentRepository::default::scm:svn:http://localhost");

        RemoteRepository repository = mojo.getDeploymentRepository(true);
        assertEquals("altDeploymentRepository", repository.getId());
        assertEquals("scm:svn:http://localhost", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void legacyScmSvnAltDeploymentRepository(DeployMojo mojo) throws Exception {
        setVariableValueToObject(
                mojo, "altDeploymentRepository", "altDeploymentRepository::legacy::scm:svn:http://localhost");

        MojoException e = assertThrows(
                MojoException.class,
                () -> mojo.getDeploymentRepository(true),
                "Should throw: Invalid legacy syntax and layout for repository.");
        assertEquals("Invalid legacy syntax and layout for repository.", e.getMessage());
        assertEquals(
                "Invalid legacy syntax and layout for alternative repository. Use \"altDeploymentRepository::scm:svn:http://localhost\" instead, and only default layout is supported.",
                e.getLongMessage());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void altSnapshotDeploymentRepository(DeployMojo mojo) throws Exception {
        setVariableValueToObject(mojo, "altDeploymentRepository", "altReleaseDeploymentRepository::http://localhost");

        RemoteRepository repository = mojo.getDeploymentRepository(true);
        assertEquals("altReleaseDeploymentRepository", repository.getId());
        assertEquals("http://localhost", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void altReleaseDeploymentRepository(DeployMojo mojo) throws Exception {
        setVariableValueToObject(mojo, "altDeploymentRepository", "altReleaseDeploymentRepository::http://localhost");

        RemoteRepository repository = mojo.getDeploymentRepository(false);
        assertEquals("altReleaseDeploymentRepository", repository.getId());
        assertEquals("http://localhost", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void altDeploymentRepositoryRefusedWhenCredentialsBoundToOtherUrl(DeployMojo mojo) throws Exception {
        when(session.getSettings())
                .thenReturn(Settings.newBuilder()
                        .servers(List.of(Server.newBuilder().id("remote-repo").build()))
                        .mirrors(List.of(Mirror.newBuilder()
                                .id("remote-repo")
                                .url("https://good.example/repo")
                                .build()))
                        .build());
        setVariableValueToObject(mojo, "altDeploymentRepository", "remote-repo::https://evil.example/repo");

        MojoException e = assertThrows(MojoException.class, () -> mojo.getDeploymentRepository(false));
        assertTrue(e.getMessage().contains("Refusing to deploy"), e.getMessage());
        assertTrue(e.getMessage().contains("https://evil.example/repo"), e.getMessage());
        assertTrue(e.getMessage().contains("https://good.example/repo"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void altDeploymentRepositoryAcceptedWhenUrlKnownForId(DeployMojo mojo) throws Exception {
        when(session.getSettings())
                .thenReturn(Settings.newBuilder()
                        .servers(List.of(Server.newBuilder().id("remote-repo").build()))
                        .build());
        // the project's own distributionManagement URL for "remote-repo" is a known binding
        String dmUrl = Paths.get(getBasedir()).toUri().toString();
        setVariableValueToObject(mojo, "altDeploymentRepository", "remote-repo::" + dmUrl);

        RemoteRepository repository = mojo.getDeploymentRepository(false);
        assertEquals("remote-repo", repository.getId());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void altDeploymentRepositoryCredentialReuseOptOut(DeployMojo mojo) throws Exception {
        when(session.getSettings())
                .thenReturn(Settings.newBuilder()
                        .servers(List.of(Server.newBuilder().id("remote-repo").build()))
                        .mirrors(List.of(Mirror.newBuilder()
                                .id("remote-repo")
                                .url("https://good.example/repo")
                                .build()))
                        .build());
        session.getUserProperties().put(AbstractDeployMojo.ALLOW_CREDENTIAL_REUSE_PROPERTY, "true");
        setVariableValueToObject(mojo, "altDeploymentRepository", "remote-repo::https://evil.example/repo");

        RemoteRepository repository = mojo.getDeploymentRepository(false);
        assertEquals("https://evil.example/repo", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void pomSourcedAltRepositoryRefusedWhenNoUrlOnRecordForCredentialedId(DeployMojo mojo) throws Exception {
        // the mainline attack shape: settings.xml stores credentials for "ossrh" but binds no URL
        // to that id anywhere (no mirror, no profile repository, and the project's
        // distributionManagement uses a different id), and a POM-bindable parameter pairs the id
        // with an attacker URL. The parameter is set without a matching -D user property, which is
        // exactly what a pom <properties> entry or plugin <configuration> produces.
        when(session.getSettings())
                .thenReturn(Settings.newBuilder()
                        .servers(List.of(Server.newBuilder().id("ossrh").build()))
                        .build());
        setVariableValueToObject(mojo, "altDeploymentRepository", "ossrh::https://evil.example/repo");

        MojoException e = assertThrows(MojoException.class, () -> mojo.getDeploymentRepository(false));
        assertTrue(e.getMessage().contains("Refusing to deploy"), e.getMessage());
        assertTrue(e.getMessage().contains("'ossrh'"), e.getMessage());
        assertTrue(e.getMessage().contains("https://evil.example/repo"), e.getMessage());
        assertTrue(e.getMessage().contains("configured from the POM"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void cliSourcedAltRepositoryWarnsButProceedsWhenNoUrlOnRecordForCredentialedId(DeployMojo mojo) throws Exception {
        // same empty-record shape, but the operator typed the pair on the command line: the value
        // is present as a session user property and matches the parameter value, so the binding
        // proceeds (with a warning naming the target URL) - fully failing closed here would break
        // legitimate -DaltDeploymentRepository workflows against ids that settings.xml only stores
        // credentials for
        when(session.getSettings())
                .thenReturn(Settings.newBuilder()
                        .servers(List.of(Server.newBuilder().id("ossrh").build()))
                        .build());
        session.getUserProperties().put("altDeploymentRepository", "ossrh::https://elsewhere.example/repo");
        setVariableValueToObject(mojo, "altDeploymentRepository", "ossrh::https://elsewhere.example/repo");

        RemoteRepository repository = mojo.getDeploymentRepository(false);
        assertEquals("ossrh", repository.getId());
        assertEquals("https://elsewhere.example/repo", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void pomSourcedAltRepositoryEmptyRecordRefusalHasKnobOverride(DeployMojo mojo) throws Exception {
        when(session.getSettings())
                .thenReturn(Settings.newBuilder()
                        .servers(List.of(Server.newBuilder().id("ossrh").build()))
                        .build());
        session.getUserProperties().put(AbstractDeployMojo.ALLOW_CREDENTIAL_REUSE_PROPERTY, "true");
        setVariableValueToObject(mojo, "altDeploymentRepository", "ossrh::https://evil.example/repo");

        RemoteRepository repository = mojo.getDeploymentRepository(false);
        assertEquals("https://evil.example/repo", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void altDeploymentRepositoryAcceptedWhenIdHasNoCredentials(DeployMojo mojo) throws Exception {
        when(session.getSettings())
                .thenReturn(Settings.newBuilder()
                        .servers(List.of(Server.newBuilder().id("other-server").build()))
                        .build());
        setVariableValueToObject(mojo, "altDeploymentRepository", "no-creds-repo::https://elsewhere.example/repo");

        RemoteRepository repository = mojo.getDeploymentRepository(false);
        assertEquals("https://elsewhere.example/repo", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void insecureHttpAltDeploymentRepositoryRefused(DeployMojo mojo) throws Exception {
        setVariableValueToObject(mojo, "altDeploymentRepository", "insecure-repo::http://insecure.example/repo");

        MojoException e = assertThrows(MojoException.class, () -> mojo.getDeploymentRepository(false));
        assertTrue(e.getMessage().contains("insecure (cleartext) URL"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void insecureFtpAltDeploymentRepositoryRefused(DeployMojo mojo) throws Exception {
        setVariableValueToObject(mojo, "altDeploymentRepository", "insecure-repo::ftp://insecure.example/repo");

        MojoException e = assertThrows(MojoException.class, () -> mojo.getDeploymentRepository(false));
        assertTrue(e.getMessage().contains("insecure (cleartext) URL"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void insecureAltDeploymentRepositoryOptOut(DeployMojo mojo) throws Exception {
        session.getUserProperties().put(AbstractDeployMojo.ALLOW_INSECURE_URL_PROPERTY, "true");
        setVariableValueToObject(mojo, "altDeploymentRepository", "insecure-repo::http://insecure.example/repo");

        RemoteRepository repository = mojo.getDeploymentRepository(false);
        assertEquals("http://insecure.example/repo", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void loopbackHttpAltDeploymentRepositoryAccepted(DeployMojo mojo) throws Exception {
        setVariableValueToObject(mojo, "altDeploymentRepository", "local-repo::http://127.0.0.1:8081/repo");

        RemoteRepository repository = mojo.getDeploymentRepository(false);
        assertEquals("http://127.0.0.1:8081/repo", repository.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy")
    void deployAtEndBatchIsNotRedeployedOnReentry(DeployMojo mojo) throws Exception {
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        artifactManager.setPath(
                project.getMainArtifact().get(),
                Paths.get(getBasedir(), "target/test-classes/unit/maven-deploy-test-1.0-SNAPSHOT.jar"));
        // give the session a persistent plugin context and a real reactor project list
        Map<Project, Map<String, Object>> contexts = new HashMap<>();
        when(session.getPluginContext(any(Project.class)))
                .thenAnswer(iom -> contexts.computeIfAbsent(iom.getArgument(0, Project.class), p -> new HashMap<>()));
        when(session.getProjects()).thenReturn(List.of(project));

        ArgumentCaptor<ArtifactDeployerRequest> captor = ArgumentCaptor.forClass(ArtifactDeployerRequest.class);
        doNothing().when(artifactDeployer).deploy(captor.capture());

        // deployAtEnd defaults to true: the single-project batch fires within the first execution
        mojo.execute();
        assertEquals(1, captor.getAllValues().size(), "batch must fire exactly once");

        // re-entry (second bound deploy execution, or direct deploy:deploy) must be a no-op
        mojo.execute();
        assertEquals(1, captor.getAllValues().size(), "re-entry must not re-deploy the batch");
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
                .thenAnswer(iom -> new org.apache.maven.impl.DefaultArtifact(
                        session, iom.getArgument(0, org.eclipse.aether.artifact.Artifact.class)));
        when(session.createRemoteRepository(any()))
                .thenAnswer(iom ->
                        session.getService(RepositoryFactory.class).createRemote(iom.getArgument(0, Repository.class)));
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
