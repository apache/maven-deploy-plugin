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

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.bridge.MavenRepositorySystem;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest(realRepositorySession = true)
@Basedir("/unit/deploy-test")
class DeployMojoTest {

    @TempDir
    private Path tempDir;

    private File remoteRepo;

    private File localRepo;

    @Inject
    private MavenSession mavenSession;

    @Inject
    private MavenProject mavenProject;

    @Inject
    private MavenRepositorySystem repositorySystem;

    @Inject
    private ArtifactHandlerManager artifactHandlerManager;

    @Inject
    private MavenProjectHelper mavenProjectHelper;

    @BeforeEach
    void setUp() throws Exception {
        remoteRepo = tempDir.resolve("remote-repo").toFile();
        localRepo = tempDir.resolve("local-repo").toFile();

        Repository deploymentRepository = new Repository();
        deploymentRepository.setId("deploy-test");
        deploymentRepository.setUrl("file://" + remoteRepo.getAbsolutePath());

        mavenProject.setSnapshotArtifactRepository(
                repositorySystem.buildArtifactRepositoryFromRepo(deploymentRepository));

        mavenProject.setGroupId("org.apache.maven.test");
        mavenProject.setArtifactId("maven-deploy-test");
        mavenProject.setVersion("1.0-SNAPSHOT");

        when(mavenSession.getProjects()).thenReturn(Collections.singletonList(mavenProject));
        mavenSession.getRequest().setLocalRepositoryPath(localRepo);
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testDeployTestEnvironment(DeployMojo mojo) {
        assertNotNull(mojo);
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testBasicDeploy(DeployMojo mojo) throws Exception {
        mojo.setPluginContext(new ConcurrentHashMap<>());

        setProjectArtifact(mavenProject, "jar");
        assertEquals("jar", mavenProject.getPackaging());

        mojo.execute();

        // check the artifact in local repository
        List<String> expectedFiles = new ArrayList<>();
        List<String> fileList = new ArrayList<>();

        expectedFiles.add("org");
        expectedFiles.add("apache");
        expectedFiles.add("maven");
        expectedFiles.add("test");
        expectedFiles.add("maven-deploy-test");
        expectedFiles.add("1.0-SNAPSHOT");
        expectedFiles.add("maven-metadata-deploy-test.xml");
        // expectedFiles.add( "maven-deploy-test-1.0-SNAPSHOT.jar" );
        // expectedFiles.add( "maven-deploy-test-1.0-SNAPSHOT.pom" );
        // as we are in SNAPSHOT the file is here twice
        expectedFiles.add("maven-metadata-deploy-test.xml");
        // extra Aether files
        expectedFiles.add("resolver-status.properties");
        expectedFiles.add("resolver-status.properties");

        File[] files = localRepo.listFiles();

        for (File file2 : Objects.requireNonNull(files)) {
            addFileToList(file2, fileList);
        }

        assertEquals(expectedFiles.size(), fileList.size());

        assertEquals(0, getSizeOfExpectedFiles(fileList, expectedFiles));

        // check the artifact in remote repository
        expectedFiles = new ArrayList<>();
        fileList = new ArrayList<>();

        expectedFiles.add("org");
        expectedFiles.add("apache");
        expectedFiles.add("maven");
        expectedFiles.add("test");
        expectedFiles.add("maven-deploy-test");
        expectedFiles.add("1.0-SNAPSHOT");
        expectedFiles.add("maven-metadata.xml");
        expectedFiles.add("maven-metadata.xml.md5");
        expectedFiles.add("maven-metadata.xml.sha1");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.jar");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.jar.md5");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.jar.sha1");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.pom");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.pom.md5");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.pom.sha1");
        // as we are in SNAPSHOT the file is here twice
        expectedFiles.add("maven-metadata.xml");
        expectedFiles.add("maven-metadata.xml.md5");
        expectedFiles.add("maven-metadata.xml.sha1");

        files = remoteRepo.listFiles();

        for (File file1 : Objects.requireNonNull(files)) {
            addFileToList(file1, fileList);
        }

        assertEquals(expectedFiles.size(), fileList.size());

        assertEquals(0, getSizeOfExpectedFiles(fileList, expectedFiles));
    }

    @Test
    @InjectMojo(goal = "deploy")
    @MojoParameter(name = "skip", value = "true")
    void testSkippingDeploy(DeployMojo mojo) throws Exception {
        mojo.setPluginContext(new ConcurrentHashMap<>());

        mojo.execute();

        File[] files = localRepo.listFiles();
        assertNull(files);

        files = remoteRepo.listFiles();
        assertNull(files);
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testBasicDeployWithPackagingAsPom(DeployMojo mojo) throws Exception {
        mojo.setPluginContext(new ConcurrentHashMap<>());

        setProjectArtifact(mavenProject, "pom");
        mavenProject.setPackaging("pom");

        mojo.execute();

        List<String> expectedFiles = new ArrayList<>();
        List<String> fileList = new ArrayList<>();

        expectedFiles.add("org");
        expectedFiles.add("apache");
        expectedFiles.add("maven");
        expectedFiles.add("test");
        expectedFiles.add("maven-deploy-test");
        expectedFiles.add("1.0-SNAPSHOT");
        expectedFiles.add("maven-metadata.xml");
        expectedFiles.add("maven-metadata.xml.md5");
        expectedFiles.add("maven-metadata.xml.sha1");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.pom");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.pom.md5");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.pom.sha1");
        // as we are in SNAPSHOT the file is here twice
        expectedFiles.add("maven-metadata.xml");
        expectedFiles.add("maven-metadata.xml.md5");
        expectedFiles.add("maven-metadata.xml.sha1");

        File[] files = remoteRepo.listFiles();

        for (File file : Objects.requireNonNull(files)) {
            addFileToList(file, fileList);
        }

        assertEquals(expectedFiles.size(), fileList.size());

        assertEquals(0, getSizeOfExpectedFiles(fileList, expectedFiles));
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testBasicDeployWithPackagingAsBom(DeployMojo mojo) throws Exception {
        mojo.setPluginContext(new ConcurrentHashMap<>());

        setProjectArtifact(mavenProject, "pom");
        mavenProject.setPackaging("bom");

        mojo.execute();

        List<String> expectedFiles = new ArrayList<>();
        List<String> fileList = new ArrayList<>();

        expectedFiles.add("org");
        expectedFiles.add("apache");
        expectedFiles.add("maven");
        expectedFiles.add("test");
        expectedFiles.add("maven-deploy-test");
        expectedFiles.add("1.0-SNAPSHOT");
        expectedFiles.add("maven-metadata.xml");
        expectedFiles.add("maven-metadata.xml.md5");
        expectedFiles.add("maven-metadata.xml.sha1");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.pom");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.pom.md5");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.pom.sha1");
        // as we are in SNAPSHOT the file is here twice
        expectedFiles.add("maven-metadata.xml");
        expectedFiles.add("maven-metadata.xml.md5");
        expectedFiles.add("maven-metadata.xml.sha1");

        File[] files = remoteRepo.listFiles();

        for (File file : Objects.requireNonNull(files)) {
            addFileToList(file, fileList);
        }

        assertEquals(expectedFiles.size(), fileList.size());

        assertEquals(0, getSizeOfExpectedFiles(fileList, expectedFiles));
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testDeployIfArtifactFileIsNull(DeployMojo mojo) throws Exception {
        mojo.setPluginContext(new ConcurrentHashMap<>());

        setProjectArtifact(mavenProject, "jar");
        mavenProject.getArtifact().setFile(null);

        try {
            mojo.execute();
            fail("Did not throw mojo execution exception");
        } catch (MojoExecutionException e) {
            // expected, message should include artifactId
            assertEquals(
                    "The packaging plugin for project maven-deploy-test did not assign a file to the build artifact",
                    e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testDeployIfProjectFileIsNull(DeployMojo mojo) throws Exception {
        mojo.setPluginContext(new ConcurrentHashMap<>());

        setProjectArtifact(mavenProject, "jar");
        mavenProject.setFile(null);

        try {
            mojo.execute();
            fail("Did not throw mojo execution exception");
        } catch (MojoExecutionException e) {
            // expected, message should include artifactId
            assertEquals("The POM for project maven-deploy-test could not be attached", e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testDeployWithAttachedArtifacts(DeployMojo mojo) throws Exception {
        mojo.setPluginContext(new ConcurrentHashMap<>());

        setProjectArtifact(mavenProject, "jar");

        mavenProjectHelper.attachArtifact(
                mavenProject,
                "jar",
                "next1",
                Files.createTempFile(tempDir, "test-artifact1", "jar").toFile());
        mavenProjectHelper.attachArtifact(
                mavenProject,
                "jar",
                "next2",
                Files.createTempFile(tempDir, "test-artifact2", "jar").toFile());

        mojo.execute();

        // check the artifacts in remote repository
        List<String> expectedFiles = new ArrayList<>();
        List<String> fileList = new ArrayList<>();

        expectedFiles.add("org");
        expectedFiles.add("apache");
        expectedFiles.add("maven");
        expectedFiles.add("test");
        expectedFiles.add("maven-deploy-test");
        expectedFiles.add("1.0-SNAPSHOT");
        expectedFiles.add("maven-metadata.xml");
        expectedFiles.add("maven-metadata.xml.md5");
        expectedFiles.add("maven-metadata.xml.sha1");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.jar");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.jar.md5");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.jar.sha1");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.pom");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.pom.md5");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT.pom.sha1");

        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT-next1.jar");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT-next1.jar.md5");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT-next1.jar.sha1");

        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT-next2.jar");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT-next2.jar.md5");
        expectedFiles.add("maven-deploy-test-1.0-SNAPSHOT-next2.jar.sha1");

        // as we are in SNAPSHOT the file is here twice
        expectedFiles.add("maven-metadata.xml");
        expectedFiles.add("maven-metadata.xml.md5");
        expectedFiles.add("maven-metadata.xml.sha1");

        File[] files = remoteRepo.listFiles();

        for (File file1 : Objects.requireNonNull(files)) {
            addFileToList(file1, fileList);
        }

        assertEquals(expectedFiles.size(), fileList.size());

        assertEquals(0, getSizeOfExpectedFiles(fileList, expectedFiles));
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testNonPomDeployWithAttachedArtifactsOnly(DeployMojo mojo) throws Exception {
        mojo.setPluginContext(new ConcurrentHashMap<>());

        setProjectArtifact(mavenProject, "jar");
        mavenProjectHelper.attachArtifact(
                mavenProject,
                "jar",
                "next1",
                Files.createTempFile(tempDir, "test-artifact1", "jar").toFile());

        mavenProject.getArtifact().setFile(null);

        try {
            mojo.execute();
            fail("Did not throw mojo execution exception");
        } catch (MojoExecutionException e) {
            // expected, message should include artifactId
            assertEquals(
                    "The packaging plugin for project maven-deploy-test did not assign a main file to the project "
                            + "but it has attachments. Change packaging to 'pom'.",
                    e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testLegacyAltDeploymentRepositoryWithDefaultLayout(DeployMojo mojo) throws Exception {
        assertEquals(
                new RemoteRepository.Builder("altDeploymentRepository", "default", "http://localhost").build(),
                mojo.getDeploymentRepository(
                        mavenProject, null, null, "altDeploymentRepository::default::http://localhost"));
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testLegacyAltDeploymentRepositoryWithLegacyLayout(DeployMojo mojo) {
        String altDeploymentRepository = "altDeploymentRepository::legacy::http://localhost";

        try {
            mojo.getDeploymentRepository(mavenProject, null, null, altDeploymentRepository);
            fail("Should throw: Invalid legacy syntax and layout for repository.");
        } catch (MojoExecutionException e) {
            assertEquals(
                    "Invalid legacy syntax and layout for alternative repository: \"" + altDeploymentRepository
                            + "\". Use \"altDeploymentRepository::http://localhost\" instead, and only default layout is supported.",
                    e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testInsaneAltDeploymentRepository(DeployMojo mojo) {
        String altDeploymentRepository = "altDeploymentRepository::hey::wow::foo::http://localhost";

        try {
            mojo.getDeploymentRepository(mavenProject, null, null, altDeploymentRepository);
            fail("Should throw: Invalid legacy syntax and layout for repository.");
        } catch (MojoExecutionException e) {
            assertEquals(
                    "Invalid legacy syntax and layout for alternative repository: \"" + altDeploymentRepository
                            + "\". Use \"altDeploymentRepository::wow::foo::http://localhost\" instead, and only default layout is supported.",
                    e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testDefaultScmSvnAltDeploymentRepository(DeployMojo mojo) throws Exception {
        assertEquals(
                new RemoteRepository.Builder("altDeploymentRepository", "default", "scm:svn:http://localhost").build(),
                mojo.getDeploymentRepository(
                        mavenProject, null, null, "altDeploymentRepository::default::scm:svn:http://localhost"));
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testLegacyScmSvnAltDeploymentRepository(DeployMojo mojo) {
        String altDeploymentRepository = "altDeploymentRepository::legacy::scm:svn:http://localhost";

        try {
            mojo.getDeploymentRepository(mavenProject, null, null, altDeploymentRepository);
            fail("Should throw: Invalid legacy syntax and layout for repository.");
        } catch (MojoExecutionException e) {
            assertEquals(
                    "Invalid legacy syntax and layout for alternative repository: \"" + altDeploymentRepository
                            + "\". Use \"altDeploymentRepository::scm:svn:http://localhost\" instead, and only default layout is supported.",
                    e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testAltSnapshotDeploymentRepository(DeployMojo mojo) throws Exception {
        assertEquals(
                new RemoteRepository.Builder("altSnapshotDeploymentRepository", "default", "http://localhost").build(),
                mojo.getDeploymentRepository(
                        mavenProject, "altSnapshotDeploymentRepository::http://localhost", null, null));
    }

    @Test
    @InjectMojo(goal = "deploy")
    void testAltReleaseDeploymentRepository(DeployMojo mojo) throws Exception {
        mavenProject.setVersion("1.0");

        assertEquals(
                new RemoteRepository.Builder("altReleaseDeploymentRepository", "default", "http://localhost").build(),
                mojo.getDeploymentRepository(
                        mavenProject, null, "altReleaseDeploymentRepository::http://localhost", null));
    }

    private void addFileToList(File file, List<String> fileList) {
        if (!file.isDirectory()) {
            fileList.add(file.getName());
        } else {
            fileList.add(file.getName());

            File[] files = file.listFiles();

            for (File file1 : Objects.requireNonNull(files)) {
                addFileToList(file1, fileList);
            }
        }
    }

    private int getSizeOfExpectedFiles(List<String> fileList, List<String> expectedFiles) {
        for (String fileName : fileList) {
            // translate uniqueVersion to -SNAPSHOT
            fileName = fileName.replaceFirst("-\\d{8}\\.\\d{6}-\\d+", "-SNAPSHOT");

            if (!expectedFiles.remove(fileName)) {
                fail(fileName + " is not included in the expected files");
            }
        }
        return expectedFiles.size();
    }

    private void setProjectArtifact(MavenProject mavenProject, String type) throws IOException {
        DefaultArtifact artifact = new DefaultArtifact(
                mavenProject.getGroupId(),
                mavenProject.getArtifactId(),
                mavenProject.getVersion(),
                null,
                type,
                null,
                artifactHandlerManager.getArtifactHandler(type));
        artifact.setFile(Files.createTempFile(tempDir, "test-artifact", type).toFile());
        mavenProject.setArtifact(artifact);
    }
}
