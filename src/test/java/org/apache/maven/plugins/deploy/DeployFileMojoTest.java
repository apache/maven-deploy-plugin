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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Priority;
import org.apache.maven.api.di.Provides;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.api.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.api.plugin.testing.stubs.SessionMock;
import org.apache.maven.api.services.ArtifactDeployer;
import org.apache.maven.api.services.ArtifactDeployerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.impl.InternalSession;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.plugin.testing.MojoExtension.getBasedir;
import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest
class DeployFileMojoTest {
    private static final String LOCAL_REPO = "target/local-repo";

    @Inject
    @SuppressWarnings("unused")
    private Session session;

    @Inject
    @SuppressWarnings("unused")
    private ArtifactDeployer artifactDeployer;

    @Inject
    @SuppressWarnings("unused")
    private ArtifactManager artifactManager;

    @Test
    @InjectMojo(goal = "deploy-file")
    void deployTestEnvironment(DeployFileMojo mojo) {
        assertNotNull(mojo);
    }

    @Test
    @InjectMojo(goal = "deploy-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-deploy-file-test")
    @MojoParameter(name = "version", value = "1.0")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${session.topDirectory}/src/test/resources/unit/maven-deploy-test-1.0-SNAPSHOT.jar")
    @MojoParameter(name = "repositoryId", value = "deploy-test")
    @MojoParameter(name = "url", value = "file://${session.topDirectory}/target/remote-repo/deploy-file")
    @MojoParameter(name = "description", value = "POM was created from deploy:deploy-file")
    @MojoParameter(name = "generatePom", value = "true")
    @MojoParameter(name = "skip", value = "snapshots")
    void basicDeployFile(DeployFileMojo mojo) throws Exception {
        assertNotNull(mojo);

        String groupId = (String) getVariableValueFromObject(mojo, "groupId");
        String artifactId = (String) getVariableValueFromObject(mojo, "artifactId");
        String version = (String) getVariableValueFromObject(mojo, "version");
        String packaging = (String) getVariableValueFromObject(mojo, "packaging");
        Path file = (Path) getVariableValueFromObject(mojo, "file");
        String repositoryId = (String) getVariableValueFromObject(mojo, "repositoryId");
        String url = (String) getVariableValueFromObject(mojo, "url");

        assertEquals("org.apache.maven.test", groupId);
        assertEquals("maven-deploy-file-test", artifactId);
        assertEquals("1.0", version);
        assertEquals("jar", packaging);
        assertTrue(Files.exists(file), file.toString());
        assertEquals("deploy-test", repositoryId);
        assertEquals("file://" + getBasedir() + "/target/remote-repo/deploy-file", url);

        execute(mojo, request -> {
            assertNotNull(request);
            List<Artifact> artifacts = new ArrayList<>(request.getArtifacts());
            assertEquals(2, artifacts.size());
            Artifact a1 = artifacts.get(0);
            Path p1 = artifactManager.getPath(a1).orElse(null);
            assertEquals(file, p1);
            Artifact a2 = artifacts.get(1);
            Path p2 = artifactManager.getPath(a2).orElse(null);
            assertNotNull(p2);
            assertTrue(p2.toString().endsWith(".pom"));

            assertNotNull(request.getRepository());
            assertEquals(
                    url.replace(File.separator, "/"), request.getRepository().getUrl());

            // check the generated pom
            File pom = p2.toFile();
            assertTrue(pom.exists());

            Model model = mojo.readModel(p2);
            assertEquals("4.0.0", model.getModelVersion());
            assertEquals(groupId, model.getGroupId());
            assertEquals(artifactId, model.getArtifactId());
            assertEquals(version, model.getVersion());
            assertEquals(packaging, model.getPackaging());
            assertEquals("POM was created from deploy:deploy-file", model.getDescription());
        });
    }

    @Test
    @InjectMojo(goal = "deploy-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-deploy-file-test")
    @MojoParameter(name = "version", value = "1.0")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${session.topDirectory}/src/test/resources/unit/maven-deploy-test-1.0-SNAPSHOT.jar")
    @MojoParameter(name = "repositoryId", value = "deploy-test")
    @MojoParameter(name = "url", value = "file://${session.topDirectory}/target/remote-repo/deploy-file")
    @MojoParameter(name = "classifier", value = "bin")
    @MojoParameter(name = "generatePom", value = "true")
    void deployIfClassifierIsSet(DeployFileMojo mojo) throws Exception {
        assertNotNull(mojo);

        String groupId = (String) getVariableValueFromObject(mojo, "groupId");
        String artifactId = (String) getVariableValueFromObject(mojo, "artifactId");
        String classifier = (String) getVariableValueFromObject(mojo, "classifier");
        assertEquals("bin", classifier);
        String version = (String) getVariableValueFromObject(mojo, "version");
        String url = (String) getVariableValueFromObject(mojo, "url");
        execute(mojo, request -> {
            assertNotNull(request);
            List<Artifact> artifacts = new ArrayList<>(request.getArtifacts());
            assertEquals(2, artifacts.size());
            // first artifact
            Artifact a1 = artifacts.get(0);
            assertEquals(new ArtifactStub(groupId, artifactId, "bin", version, "jar"), a1);
            Path p1 = artifactManager.getPath(a1).orElse(null);
            assertNotNull(p1);
            assertTrue(p1.toString().endsWith("maven-deploy-test-1.0-SNAPSHOT.jar"));
            // second artifact
            Artifact a2 = artifacts.get(1);
            assertEquals(new ArtifactStub(groupId, artifactId, "", version, "pom"), a2);
            Path p2 = artifactManager.getPath(a2).orElse(null);
            assertNotNull(p2);
            assertTrue(p2.toString().endsWith(".pom"));
            // remote repository
            assertNotNull(request.getRepository());
            assertEquals(
                    url.replace(File.separator, "/"), request.getRepository().getUrl());
        });
    }

    @Test
    @InjectMojo(goal = "deploy-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-deploy-file-test")
    @MojoParameter(name = "version", value = "1.0")
    @MojoParameter(name = "file", value = "${session.topDirectory}/src/test/resources/unit/maven-deploy-test.zip")
    @MojoParameter(name = "repositoryId", value = "deploy-test")
    @MojoParameter(name = "url", value = "file://${session.topDirectory}/target/remote-repo/deploy-file")
    @MojoParameter(name = "generatePom", value = "true")
    void deployIfArtifactIsNotJar(DeployFileMojo mojo) throws Exception {
        assertNotNull(mojo);

        String groupId = (String) getVariableValueFromObject(mojo, "groupId");
        String artifactId = (String) getVariableValueFromObject(mojo, "artifactId");
        String version = (String) getVariableValueFromObject(mojo, "version");
        assertEquals("org.apache.maven.test", groupId);
        assertEquals("maven-deploy-file-test", artifactId);
        assertEquals("1.0", version);

        execute(mojo, request -> {
            assertNotNull(request);
            List<Artifact> artifacts = new ArrayList<>(request.getArtifacts());
            assertEquals(2, artifacts.size());
            Artifact a1 = artifacts.get(0);
            Artifact a2 = artifacts.get(1);
            Path p1 = artifactManager.getPath(a1).orElse(null);
            Path p2 = artifactManager.getPath(a2).orElse(null);
            assertNotNull(p1);
            assertTrue(p1.toString().endsWith("maven-deploy-test.zip"));
            assertNotNull(p2);
            assertTrue(p2.toString().endsWith(".pom"));

            assertNotNull(request.getRepository());
            assertEquals(
                    "file://" + getBasedir().replace(File.separator, "/") + "/target/remote-repo/deploy-file",
                    request.getRepository().getUrl());
        });
    }

    private ArtifactDeployerRequest execute(DeployFileMojo mojo) {
        AtomicReference<ArtifactDeployerRequest> holder = new AtomicReference<>();
        execute(mojo, holder::set);
        return holder.get();
    }

    private void execute(DeployFileMojo mojo, Consumer<ArtifactDeployerRequest> consumer) {
        doAnswer(iom -> {
                    ArtifactDeployerRequest request = iom.getArgument(0, ArtifactDeployerRequest.class);
                    consumer.accept(request);
                    return null;
                })
                .when(artifactDeployer)
                .deploy(any(ArtifactDeployerRequest.class));
        mojo.execute();
    }

    @Provides
    @Singleton
    @Priority(10)
    @SuppressWarnings("unused")
    private InternalSession createSession() {
        InternalSession session = SessionMock.getMockSession(LOCAL_REPO);
        when(session.getTopDirectory()).thenReturn(Paths.get(getBasedir()));
        return session;
    }
}
