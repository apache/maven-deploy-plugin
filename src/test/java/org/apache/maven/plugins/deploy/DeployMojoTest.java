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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.plugins.deploy.stubs.ArtifactRepositoryStub;
import org.apache.maven.plugins.deploy.stubs.DeployArtifactStub;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Ignore;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
public class DeployMojoTest extends AbstractMojoTestCase {
    private File remoteRepo;

    private File localRepo;

    private final String LOCAL_REPO = getBasedir() + "/target/local-repo";

    private final String REMOTE_REPO = getBasedir() + "/target/remote-repo";

    DeployArtifactStub artifact;

    final MavenProjectStub project = new MavenProjectStub();

    private MavenSession session;

    @InjectMocks
    private DeployMojo mojo;

    public void setUp() throws Exception {
        super.setUp();

        session = mock(MavenSession.class);
        when(session.getPluginContext(any(PluginDescriptor.class), any(MavenProject.class)))
                .thenReturn(new ConcurrentHashMap<String, Object>());
        DefaultRepositorySystemSession repositorySession = new DefaultRepositorySystemSession();
        repositorySession.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory()
                .newInstance(repositorySession, new LocalRepository(LOCAL_REPO)));
        when(session.getRepositorySession()).thenReturn(repositorySession);

        remoteRepo = new File(REMOTE_REPO);

        remoteRepo.mkdirs();

        localRepo = new File(LOCAL_REPO);

        if (localRepo.exists()) {
            FileUtils.deleteDirectory(localRepo);
        }

        if (remoteRepo.exists()) {
            FileUtils.deleteDirectory(remoteRepo);
        }
    }

    public void tearDown() throws Exception {
        super.tearDown();

        if (remoteRepo.exists()) {
            // FileUtils.deleteDirectory( remoteRepo );
        }
    }

    public void testDeployTestEnvironment() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/basic-deploy-test/plugin-config.xml");

        DeployMojo mojo = (DeployMojo) lookupMojo("deploy", testPom);

        assertNotNull(mojo);
    }

    public void testBasicDeploy() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/basic-deploy-test/plugin-config.xml");

        mojo = (DeployMojo) lookupMojo("deploy", testPom);

        MockitoAnnotations.initMocks(this);

        assertNotNull(mojo);

        ProjectBuildingRequest buildingRequest = mock(ProjectBuildingRequest.class);
        when(session.getProjectBuildingRequest()).thenReturn(buildingRequest);
        DefaultRepositorySystemSession repositorySession = new DefaultRepositorySystemSession();
        repositorySession.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory()
                .newInstance(repositorySession, new LocalRepository(LOCAL_REPO)));
        when(buildingRequest.getRepositorySession()).thenReturn(repositorySession);
        when(session.getRepositorySession()).thenReturn(repositorySession);

        File file = new File(
                getBasedir(),
                "target/test-classes/unit/basic-deploy-test/target/" + "deploy-test-file-1.0-SNAPSHOT.jar");

        assertTrue(file.exists());

        MavenProject project = (MavenProject) getVariableValueFromObject(mojo, "project");
        project.setGroupId("org.apache.maven.test");
        project.setArtifactId("maven-deploy-test");
        project.setVersion("1.0-SNAPSHOT");

        setVariableValueToObject(mojo, "pluginContext", new ConcurrentHashMap<>());
        setVariableValueToObject(mojo, "reactorProjects", Collections.singletonList(project));

        artifact = (DeployArtifactStub) project.getArtifact();

        String packaging = project.getPackaging();

        assertEquals("jar", packaging);

        artifact.setFile(file);

        ArtifactRepositoryStub repo = getRepoStub(mojo);

        assertNotNull(repo);

        repo.setAppendToUrl("basic-deploy-test");

        assertEquals("deploy-test", repo.getId());
        assertEquals("deploy-test", repo.getKey());
        assertEquals("file", repo.getProtocol());
        assertEquals("file://" + getBasedir() + "/target/remote-repo/basic-deploy-test", repo.getUrl());

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

        File localRepo = new File(LOCAL_REPO, "");

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

        remoteRepo = new File(remoteRepo, "basic-deploy-test");

        files = remoteRepo.listFiles();

        for (File file1 : Objects.requireNonNull(files)) {
            addFileToList(file1, fileList);
        }

        assertEquals(expectedFiles.size(), fileList.size());

        assertEquals(0, getSizeOfExpectedFiles(fileList, expectedFiles));
    }

    public void testSkippingDeploy() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/basic-deploy-test/plugin-config.xml");

        DeployMojo mojo = (DeployMojo) lookupMojo("deploy", testPom);

        assertNotNull(mojo);

        File file = new File(
                getBasedir(),
                "target/test-classes/unit/basic-deploy-test/target/" + "deploy-test-file-1.0-SNAPSHOT.jar");

        assertTrue(file.exists());

        MavenProject project = (MavenProject) getVariableValueFromObject(mojo, "project");

        setVariableValueToObject(mojo, "pluginDescriptor", new PluginDescriptor());
        setVariableValueToObject(mojo, "pluginContext", new ConcurrentHashMap<>());
        setVariableValueToObject(mojo, "reactorProjects", Collections.singletonList(project));
        setVariableValueToObject(mojo, "session", session);

        artifact = (DeployArtifactStub) project.getArtifact();

        String packaging = project.getPackaging();

        assertEquals("jar", packaging);

        artifact.setFile(file);

        ArtifactRepositoryStub repo = getRepoStub(mojo);

        assertNotNull(repo);

        repo.setAppendToUrl("basic-deploy-test");

        assertEquals("deploy-test", repo.getId());
        assertEquals("deploy-test", repo.getKey());
        assertEquals("file", repo.getProtocol());
        assertEquals("file://" + getBasedir() + "/target/remote-repo/basic-deploy-test", repo.getUrl());

        setVariableValueToObject(mojo, "skip", Boolean.TRUE.toString());

        mojo.execute();

        File localRepo = new File(LOCAL_REPO, "");

        File[] files = localRepo.listFiles();

        assertNull(files);

        remoteRepo = new File(remoteRepo, "basic-deploy-test");

        files = remoteRepo.listFiles();

        assertNull(files);
    }

    public void testBasicDeployWithPackagingAsPom() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/basic-deploy-pom/plugin-config.xml");

        mojo = (DeployMojo) lookupMojo("deploy", testPom);

        MockitoAnnotations.initMocks(this);

        assertNotNull(mojo);

        ProjectBuildingRequest buildingRequest = mock(ProjectBuildingRequest.class);
        when(session.getProjectBuildingRequest()).thenReturn(buildingRequest);
        DefaultRepositorySystemSession repositorySession = new DefaultRepositorySystemSession();
        repositorySession.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory()
                .newInstance(repositorySession, new LocalRepository(LOCAL_REPO)));
        when(buildingRequest.getRepositorySession()).thenReturn(repositorySession);
        when(session.getRepositorySession()).thenReturn(repositorySession);

        File pomFile = new File(
                getBasedir(),
                "target/test-classes/unit/basic-deploy-pom/target/" + "deploy-test-file-1.0-SNAPSHOT.pom");

        assertTrue(pomFile.exists());

        MavenProject project = (MavenProject) getVariableValueFromObject(mojo, "project");
        project.setGroupId("org.apache.maven.test");
        project.setArtifactId("maven-deploy-test");
        project.setVersion("1.0-SNAPSHOT");

        setVariableValueToObject(mojo, "pluginContext", new ConcurrentHashMap<>());
        setVariableValueToObject(mojo, "reactorProjects", Collections.singletonList(project));

        artifact = (DeployArtifactStub) project.getArtifact();

        artifact.setArtifactHandlerExtension(project.getPackaging());

        artifact.setFile(pomFile);

        ArtifactRepositoryStub repo = getRepoStub(mojo);

        repo.setAppendToUrl("basic-deploy-pom");

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
        remoteRepo = new File(remoteRepo, "basic-deploy-pom");

        File[] files = remoteRepo.listFiles();

        for (File file : Objects.requireNonNull(files)) {
            addFileToList(file, fileList);
        }

        assertEquals(expectedFiles.size(), fileList.size());

        assertEquals(0, getSizeOfExpectedFiles(fileList, expectedFiles));
    }

    public void testDeployIfArtifactFileIsNull() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/basic-deploy-test/plugin-config.xml");

        DeployMojo mojo = (DeployMojo) lookupMojo("deploy", testPom);

        MockitoAnnotations.initMocks(this);

        ProjectBuildingRequest buildingRequest = mock(ProjectBuildingRequest.class);
        when(session.getProjectBuildingRequest()).thenReturn(buildingRequest);

        setVariableValueToObject(mojo, "session", session);

        assertNotNull(mojo);

        MavenProject project = (MavenProject) getVariableValueFromObject(mojo, "project");
        project.setGroupId("org.apache.maven.test");
        project.setArtifactId("maven-deploy-test");
        project.setVersion("1.0-SNAPSHOT");

        setVariableValueToObject(mojo, "pluginContext", new ConcurrentHashMap<>());
        setVariableValueToObject(mojo, "reactorProjects", Collections.singletonList(project));

        artifact = (DeployArtifactStub) project.getArtifact();

        artifact.setFile(null);

        assertNull(artifact.getFile());

        try {
            mojo.execute();

            fail("Did not throw mojo execution exception");
        } catch (MojoExecutionException e) {
            // expected
        }
    }

    public void testDeployWithAttachedArtifacts() throws Exception {
        File testPom = new File(
                getBasedir(), "target/test-classes/unit/basic-deploy-with-attached-artifacts/" + "plugin-config.xml");

        mojo = (DeployMojo) lookupMojo("deploy", testPom);

        MockitoAnnotations.initMocks(this);

        assertNotNull(mojo);

        ProjectBuildingRequest buildingRequest = mock(ProjectBuildingRequest.class);
        when(session.getProjectBuildingRequest()).thenReturn(buildingRequest);
        DefaultRepositorySystemSession repositorySession = new DefaultRepositorySystemSession();
        repositorySession.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory()
                .newInstance(repositorySession, new LocalRepository(LOCAL_REPO)));
        when(buildingRequest.getRepositorySession()).thenReturn(repositorySession);
        when(session.getRepositorySession()).thenReturn(repositorySession);

        MavenProject project = (MavenProject) getVariableValueFromObject(mojo, "project");
        project.setGroupId("org.apache.maven.test");
        project.setArtifactId("maven-deploy-test");
        project.setVersion("1.0-SNAPSHOT");

        setVariableValueToObject(mojo, "pluginContext", new ConcurrentHashMap<>());
        setVariableValueToObject(mojo, "reactorProjects", Collections.singletonList(project));

        artifact = (DeployArtifactStub) project.getArtifact();

        File file = new File(
                getBasedir(),
                "target/test-classes/unit/basic-deploy-with-attached-artifacts/target/"
                        + "deploy-test-file-1.0-SNAPSHOT.jar");

        artifact.setFile(file);

        ArtifactRepositoryStub repo = getRepoStub(mojo);

        repo.setAppendToUrl("basic-deploy-with-attached-artifacts");

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
        // as we are in SNAPSHOT the file is here twice
        expectedFiles.add("maven-metadata.xml");
        expectedFiles.add("maven-metadata.xml.md5");
        expectedFiles.add("maven-metadata.xml.sha1");
        expectedFiles.add("attached-artifact-test-0");
        expectedFiles.add("1.0-SNAPSHOT");
        expectedFiles.add("maven-metadata.xml");
        expectedFiles.add("maven-metadata.xml.md5");
        expectedFiles.add("maven-metadata.xml.sha1");
        expectedFiles.add("attached-artifact-test-0-1.0-SNAPSHOT.jar");
        expectedFiles.add("attached-artifact-test-0-1.0-SNAPSHOT.jar.md5");
        expectedFiles.add("attached-artifact-test-0-1.0-SNAPSHOT.jar.sha1");
        // as we are in SNAPSHOT the file is here twice
        expectedFiles.add("maven-metadata.xml");
        expectedFiles.add("maven-metadata.xml.md5");
        expectedFiles.add("maven-metadata.xml.sha1");

        remoteRepo = new File(remoteRepo, "basic-deploy-with-attached-artifacts");

        File[] files = remoteRepo.listFiles();

        for (File file1 : Objects.requireNonNull(files)) {
            addFileToList(file1, fileList);
        }

        assertEquals(expectedFiles.size(), fileList.size());

        assertEquals(0, getSizeOfExpectedFiles(fileList, expectedFiles));
    }

    @Ignore("SCP is not part of Maven3 distribution. Aether handles transport extensions.")
    public void _testBasicDeployWithScpAsProtocol() throws Exception {
        String originalUserHome = System.getProperty("user.home");

        // FIX THE DAMN user.home BEFORE YOU DELETE IT!!!
        File altHome = new File(getBasedir(), "target/ssh-user-home");
        altHome.mkdirs();

        System.out.println("Testing user.home value for .ssh dir: " + altHome.getCanonicalPath());

        Properties props = System.getProperties();
        props.setProperty("user.home", altHome.getCanonicalPath());

        System.setProperties(props);

        File testPom = new File(getBasedir(), "target/test-classes/unit/basic-deploy-scp/plugin-config.xml");

        mojo = (DeployMojo) lookupMojo("deploy", testPom);

        assertNotNull(mojo);

        RepositorySystem repositorySystem = mock(RepositorySystem.class);

        setVariableValueToObject(mojo, "repositorySystem", repositorySystem);

        File file = new File(
                getBasedir(),
                "target/test-classes/unit/basic-deploy-scp/target/" + "deploy-test-file-1.0-SNAPSHOT.jar");

        assertTrue(file.exists());

        MavenProject project = (MavenProject) getVariableValueFromObject(mojo, "project");

        setVariableValueToObject(mojo, "pluginContext", new ConcurrentHashMap<>());
        setVariableValueToObject(mojo, "reactorProjects", Collections.singletonList(project));

        artifact = (DeployArtifactStub) project.getArtifact();

        artifact.setFile(file);

        String altUserHome = System.getProperty("user.home");

        if (altUserHome.equals(originalUserHome)) {
            // this is *very* bad!
            throw new IllegalStateException(
                    "Setting 'user.home' system property to alternate value did NOT work. Aborting test.");
        }

        File sshFile = new File(altUserHome, ".ssh");

        System.out.println("Testing .ssh dir: " + sshFile.getCanonicalPath());

        // delete first the .ssh folder if existing before executing the mojo
        if (sshFile.exists()) {
            FileUtils.deleteDirectory(sshFile);
        }

        mojo.execute();

        assertTrue(sshFile.exists());

        FileUtils.deleteDirectory(sshFile);
    }

    public void testLegacyAltDeploymentRepositoryWithDefaultLayout() throws Exception {
        DeployMojo mojo = new DeployMojo();

        setVariableValueToObject(mojo, "project", project);
        setVariableValueToObject(mojo, "session", session);
        setVariableValueToObject(mojo, "altDeploymentRepository", "altDeploymentRepository::default::http://localhost");

        project.setVersion("1.0-SNAPSHOT");

        assertEquals(
                new RemoteRepository.Builder("altDeploymentRepository", "default", "http://localhost").build(),
                mojo.getDeploymentRepository(
                        project, null, null, "altDeploymentRepository::default::http://localhost"));
    }

    public void testLegacyAltDeploymentRepositoryWithLegacyLayout() throws Exception {
        DeployMojo mojo = new DeployMojo();

        setVariableValueToObject(mojo, "project", project);
        setVariableValueToObject(mojo, "session", session);
        setVariableValueToObject(mojo, "altDeploymentRepository", "altDeploymentRepository::legacy::http://localhost");

        project.setVersion("1.0-SNAPSHOT");
        try {
            mojo.getDeploymentRepository(project, null, null, "altDeploymentRepository::legacy::http://localhost");
            fail("Should throw: Invalid legacy syntax and layout for repository.");
        } catch (MojoExecutionException e) {
            assertEquals(e.getMessage(), "Invalid legacy syntax and layout for repository.");
            assertEquals(
                    e.getLongMessage(),
                    "Invalid legacy syntax and layout for alternative repository. Use \"altDeploymentRepository::http://localhost\" instead, and only default layout is supported.");
        }
    }

    public void testInsaneAltDeploymentRepository() throws Exception {
        DeployMojo mojo = new DeployMojo();

        setVariableValueToObject(mojo, "project", project);
        setVariableValueToObject(mojo, "session", session);
        setVariableValueToObject(
                mojo, "altDeploymentRepository", "altDeploymentRepository::hey::wow::foo::http://localhost");

        project.setVersion("1.0-SNAPSHOT");
        try {
            mojo.getDeploymentRepository(
                    project, null, null, "altDeploymentRepository::hey::wow::foo::http://localhost");
            fail("Should throw: Invalid legacy syntax and layout for repository.");
        } catch (MojoExecutionException e) {
            assertEquals(e.getMessage(), "Invalid legacy syntax and layout for repository.");
            assertEquals(
                    e.getLongMessage(),
                    "Invalid legacy syntax and layout for alternative repository. Use \"altDeploymentRepository::wow::foo::http://localhost\" instead, and only default layout is supported.");
        }
    }

    public void testDefaultScmSvnAltDeploymentRepository() throws Exception {
        DeployMojo mojo = new DeployMojo();

        setVariableValueToObject(mojo, "project", project);
        setVariableValueToObject(mojo, "session", session);
        setVariableValueToObject(
                mojo, "altDeploymentRepository", "altDeploymentRepository::default::scm:svn:http://localhost");

        project.setVersion("1.0-SNAPSHOT");

        assertEquals(
                new RemoteRepository.Builder("altDeploymentRepository", "default", "scm:svn:http://localhost").build(),
                mojo.getDeploymentRepository(
                        project, null, null, "altDeploymentRepository::default::scm:svn:http://localhost"));
    }

    public void testLegacyScmSvnAltDeploymentRepository() throws Exception {
        DeployMojo mojo = new DeployMojo();

        setVariableValueToObject(mojo, "project", project);
        setVariableValueToObject(
                mojo, "altDeploymentRepository", "altDeploymentRepository::legacy::scm:svn:http://localhost");

        project.setVersion("1.0-SNAPSHOT");
        try {
            mojo.getDeploymentRepository(
                    project, null, null, "altDeploymentRepository::legacy::scm:svn:http://localhost");
            fail("Should throw: Invalid legacy syntax and layout for repository.");
        } catch (MojoExecutionException e) {
            assertEquals(e.getMessage(), "Invalid legacy syntax and layout for repository.");
            assertEquals(
                    e.getLongMessage(),
                    "Invalid legacy syntax and layout for alternative repository. Use \"altDeploymentRepository::scm:svn:http://localhost\" instead, and only default layout is supported.");
        }
    }

    public void testAltSnapshotDeploymentRepository() throws Exception {
        DeployMojo mojo = new DeployMojo();

        setVariableValueToObject(mojo, "project", project);
        setVariableValueToObject(mojo, "session", session);
        setVariableValueToObject(
                mojo, "altSnapshotDeploymentRepository", "altSnapshotDeploymentRepository::http://localhost");

        project.setVersion("1.0-SNAPSHOT");

        assertEquals(
                new RemoteRepository.Builder("altSnapshotDeploymentRepository", "default", "http://localhost").build(),
                mojo.getDeploymentRepository(project, "altSnapshotDeploymentRepository::http://localhost", null, null));
    }

    public void testAltReleaseDeploymentRepository() throws Exception {
        DeployMojo mojo = new DeployMojo();

        setVariableValueToObject(mojo, "project", project);
        setVariableValueToObject(mojo, "session", session);
        setVariableValueToObject(
                mojo, "altReleaseDeploymentRepository", "altReleaseDeploymentRepository::http://localhost");

        project.setVersion("1.0");

        assertEquals(
                new RemoteRepository.Builder("altReleaseDeploymentRepository", "default", "http://localhost").build(),
                mojo.getDeploymentRepository(project, null, "altReleaseDeploymentRepository::http://localhost", null));
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

    private ArtifactRepositoryStub getRepoStub(Object mojo) throws Exception {
        MavenProject project = (MavenProject) getVariableValueFromObject(mojo, "project");
        return (ArtifactRepositoryStub) project.getDistributionManagementArtifactRepository();
    }
}
