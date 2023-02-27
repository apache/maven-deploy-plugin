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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.maven.api.Artifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.api.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.api.plugin.testing.stubs.ProjectStub;
import org.apache.maven.api.plugin.testing.stubs.SessionStub;
import org.apache.maven.api.services.ArtifactDeployer;
import org.apache.maven.api.services.ArtifactDeployerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectBuilder;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.internal.impl.DefaultLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.codehaus.plexus.testing.PlexusExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest
@ExtendWith(MockitoExtension.class)
public class DeployMojoTest {

    private final String LOCAL_REPO = getBasedir() + "/target/local-repo";

    @Inject
    @SuppressWarnings("unused")
    private Session session;

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
    @InjectMojo(goal = "deploy", pom = "classpath:/unit/basic-deploy/plugin-config.xml")
    public void testDeployTestEnvironment(DeployMojo mojo) {
        assertNotNull(mojo);
    }

    @Test
    @InjectMojo(goal = "deploy", pom = "classpath:/unit/basic-deploy/plugin-config.xml")
    public void testBasicDeploy(DeployMojo mojo) throws Exception {
        assertNotNull(mojo);

        File file = new File(getBasedir(), "target/test-classes/unit/basic-deploy/deploy-test-file-1.0-SNAPSHOT.jar");
        assertTrue(file.exists());
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        String packaging = project.getPackaging();
        assertEquals("jar", packaging);
        artifactManager.setPath(project.getArtifact(), file.toPath());

        ArtifactDeployerRequest request = execute(mojo);

        assertNotNull(request);
        Set<Artifact> artifacts = new HashSet<>(request.getArtifacts());
        assertEquals(
                new HashSet<>(Arrays.asList(
                        new ArtifactStub("org.apache.maven.test", "maven-deploy-test", "", "1.0-SNAPSHOT", "jar"),
                        new ArtifactStub("org.apache.maven.test", "maven-deploy-test", "", "1.0-SNAPSHOT", "pom"))),
                artifacts);
        assertEquals(getBasedir(), request.getRepository().getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy", pom = "classpath:/unit/basic-deploy/plugin-config.xml")
    public void testSkippingDeploy(DeployMojo mojo) throws Exception {
        assertNotNull(mojo);

        File file = new File(getBasedir(), "target/test-classes/unit/basic-deploy/deploy-test-file-1.0-SNAPSHOT.jar");
        assertTrue(file.exists());
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        String packaging = project.getPackaging();
        assertEquals("jar", packaging);
        artifactManager.setPath(project.getArtifact(), file.toPath());

        setVariableValueToObject(mojo, "skip", Boolean.TRUE.toString());

        ArtifactDeployerRequest request = execute(mojo);
        assertNull(request);
    }

    @Test
    @InjectMojo(goal = "deploy", pom = "classpath:/unit/basic-deploy/plugin-config.xml")
    public void testBasicDeployWithPackagingAsPom(DeployMojo mojo) throws Exception {
        assertNotNull(mojo);

        File pomFile =
                new File(getBasedir(), "target/test-classes/unit/basic-deploy/deploy-test-file-1.0-SNAPSHOT.pom");
        assertTrue(pomFile.exists());
        ProjectStub project = (ProjectStub) getVariableValueFromObject(mojo, "project");
        project.setPackaging("pom");
        ((ArtifactStub) project.getArtifact()).setExtension("pom");
        artifactManager.setPath(project.getArtifact(), pomFile.toPath());

        ArtifactDeployerRequest request = execute(mojo);

        assertNotNull(request);
        Set<Artifact> artifacts = new HashSet<>(request.getArtifacts());
        assertEquals(
                Collections.singleton(
                        new ArtifactStub("org.apache.maven.test", "maven-deploy-test", "", "1.0-SNAPSHOT", "pom")),
                artifacts);
        assertEquals(getBasedir(), request.getRepository().getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy", pom = "classpath:/unit/basic-deploy/plugin-config.xml")
    public void testDeployIfArtifactFileIsNull(DeployMojo mojo) throws Exception {
        assertNotNull(mojo);

        Project project = (Project) getVariableValueFromObject(mojo, "project");
        artifactManager.setPath(project.getArtifact(), null);

        assertThrows(MojoException.class, mojo::execute, "Did not throw mojo execution exception");
    }

    @Test
    @InjectMojo(goal = "deploy", pom = "classpath:/unit/basic-deploy/plugin-config.xml")
    public void testDeployWithAttachedArtifacts(DeployMojo mojo) throws Exception {
        assertNotNull(mojo);
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        File file = new File(getBasedir(), "target/test-classes/unit/basic-deploy/deploy-test-file-1.0-SNAPSHOT.jar");
        artifactManager.setPath(project.getArtifact(), file.toPath());
        projectManager.attachArtifact(
                project,
                new ArtifactStub("org.apache.maven.test", "attached-artifact-test", "", "1.0-SNAPSHOT", "jar"),
                Paths.get(
                        getBasedir(), "target/test-classes/unit/basic-deploy/attached-artifact-test-1.0-SNAPSHOT.jar"));

        ArtifactDeployerRequest request = execute(mojo);

        assertNotNull(request);
        Set<Artifact> artifacts = new HashSet<>(request.getArtifacts());
        assertEquals(
                new HashSet<>(Arrays.asList(
                        new ArtifactStub("org.apache.maven.test", "maven-deploy-test", "", "1.0-SNAPSHOT", "jar"),
                        new ArtifactStub("org.apache.maven.test", "maven-deploy-test", "", "1.0-SNAPSHOT", "pom"),
                        new ArtifactStub(
                                "org.apache.maven.test", "attached-artifact-test", "", "1.0-SNAPSHOT", "jar"))),
                artifacts);
        assertEquals(getBasedir(), request.getRepository().getUrl());
    }

    @Test
    public void testLegacyAltDeploymentRepositoryWithDefaultLayout() throws IllegalAccessException {
        DeployMojo mojo = spy(new TestDeployMojo());
        setVariableValueToObject(mojo, "altDeploymentRepository", "altDeploymentRepository::default::http://localhost");

        RemoteRepository repository = mojo.getDeploymentRepository(true);
        assertEquals("altDeploymentRepository", repository.getId());
        assertEquals("http://localhost", repository.getUrl());
    }

    @Test
    public void testLegacyAltDeploymentRepositoryWithLegacyLayout() throws IllegalAccessException {
        DeployMojo mojo = spy(new TestDeployMojo());
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
    public void testInsaneAltDeploymentRepository() throws IllegalAccessException {
        DeployMojo mojo = spy(new TestDeployMojo());
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
    public void testDefaultScmSvnAltDeploymentRepository() throws IllegalAccessException {
        DeployMojo mojo = spy(new TestDeployMojo());
        setVariableValueToObject(
                mojo, "altDeploymentRepository", "altDeploymentRepository::default::scm:svn:http://localhost");

        RemoteRepository repository = mojo.getDeploymentRepository(true);
        assertEquals("altDeploymentRepository", repository.getId());
        assertEquals("scm:svn:http://localhost", repository.getUrl());
    }

    @Test
    public void testLegacyScmSvnAltDeploymentRepository() throws IllegalAccessException {
        DeployMojo mojo = spy(new TestDeployMojo());
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
    public void testAltSnapshotDeploymentRepository() throws IllegalAccessException {
        DeployMojo mojo = spy(new TestDeployMojo());
        setVariableValueToObject(mojo, "altDeploymentRepository", "altReleaseDeploymentRepository::http://localhost");

        RemoteRepository repository = mojo.getDeploymentRepository(true);
        assertEquals("altReleaseDeploymentRepository", repository.getId());
        assertEquals("http://localhost", repository.getUrl());
    }

    @Test
    public void testAltReleaseDeploymentRepository() throws IllegalAccessException {
        DeployMojo mojo = spy(new TestDeployMojo());
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

    class TestDeployMojo extends DeployMojo {
        TestDeployMojo() {
            super();
            this.session = DeployMojoTest.this.session;
            this.logger = new DefaultLog(LoggerFactory.getLogger("anonymous"));
        }
    }

    @Provides
    @Singleton
    @SuppressWarnings("unused")
    private Session getMockSession() {
        return SessionStub.getMockSession(LOCAL_REPO);
    }

    @Provides
    @SuppressWarnings("unused")
    private ArtifactDeployer getMockArtifactDeployer(Session session) {
        return session.getService(ArtifactDeployer.class);
    }

    @Provides
    @SuppressWarnings("unused")
    private ArtifactManager getMockArtifactManager(Session session) {
        return session.getService(ArtifactManager.class);
    }

    @Provides
    @SuppressWarnings("unused")
    private ProjectManager getMockProjectManager(Session session) {
        return session.getService(ProjectManager.class);
    }

    @Provides
    @SuppressWarnings("unused")
    private ProjectBuilder getMockProjectBuilder(Session session) {
        return session.getService(ProjectBuilder.class);
    }
}
