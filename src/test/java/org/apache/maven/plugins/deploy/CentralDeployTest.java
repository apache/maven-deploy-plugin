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
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugins.deploy.stubs.MavenProjectBigStub;
import org.apache.maven.project.DefaultMavenProjectHelper;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:per@alipsa.se">Per Nyfelt</a>
 */
public class CentralDeployTest extends AbstractMojoTestCase {
    private static final String GROUP_ID = "org.apache.maven.test";
    private static final String ARTIFACT_ID = "central-deploy-test";
    private static final String VERSION = "1.0.0";
    private static final String BASE_NAME = ARTIFACT_ID + "-" + VERSION;
    private static final String SERVER_ID = "central";
    private static final String SERVER_URL = "http://localhost:8081/api/v1";

    MavenProjectBigStub project;

    private AutoCloseable openMocks;

    private MavenSession session;

    private CentralPortalClient centralPortalClient;

    private DeployMojo mojo;

    private ConcurrentHashMap<String, Object> pluginContext;

    private ArtifactHandler artifactHandler;

    public void setUp() throws Exception {
        super.setUp();
        project = new MavenProjectBigStub();
        session = mock(MavenSession.class);
        pluginContext = new ConcurrentHashMap<>();
        Settings settings = mock(Settings.class);
        Server server = new Server();
        server.setId(SERVER_ID);
        server.setUsername("dummy-user");
        server.setPassword("dummy-password");
        DefaultRepositorySystemSession repositorySession = new DefaultRepositorySystemSession();
        when(session.getRepositorySession()).thenReturn(repositorySession);
        when(session.getPluginContext(any(), any())).thenReturn(pluginContext);

        when(settings.getServer(SERVER_ID)).thenReturn(server);
        when(session.getSettings()).thenReturn(settings);
        File testPom = new File(getBasedir(), "target/test-classes/unit/central-deploy-test/plugin-config.xml");
        mojo = (DeployMojo) lookupMojo("deploy", testPom);
        openMocks = MockitoAnnotations.openMocks(this);
        assertNotNull(mojo);

        setVariableValueToObject(mojo, "pluginContext", pluginContext);
        setVariableValueToObject(mojo, "reactorProjects", Collections.singletonList(project));
        setVariableValueToObject(mojo, "session", session);
        setVariableValueToObject(mojo, "project", project);
        artifactHandler = new DefaultArtifactHandler("jar");
        project.setDistributionManagement(createDistributionManagement());
    }

    public void tearDown() throws Exception {
        super.tearDown();

        if (openMocks != null) {
            openMocks.close();
        }
    }

    /**
     * (1.0) autoDeploy = true, uploadToCentral = true, deployAtEnd = false
     * Individual bundles (even when there is only a simple project) are named after the artifactId
     */
    public void testCentralPortalAutoDeployTrueDeployAtEndFalse() throws Exception {
        sunnyDayTest(BASE_NAME + "-bundle.zip", true, false, "central-deploy-test-1");
    }

    /**
     * (1.1) autoDeploy = true, uploadToCentral = true, deployAtEnd = true
     * Mega-bundles are named after the groupId
     */
    public void testCentralPortalAutoDeployTrueDeployAtEndTrue() throws Exception {
        sunnyDayTest(GROUP_ID + "-" + VERSION + "-bundle.zip", true, true, "central-deploy-test-2");
    }

    public void testCentralPortalAutoDeployFalseDeployAtEndTrue() throws Exception {
        sunnyDayTest(GROUP_ID + "-" + VERSION + "-bundle.zip", false, true, "central-deploy-test-3");
    }

    public void testCentralPortalAutoDeployFalseDeployAtEndFalse() throws Exception {
        sunnyDayTest(BASE_NAME + "-bundle.zip", false, false, "central-deploy-test-4");
    }

    private void sunnyDayTest(String bundleName, boolean autoDeploy, boolean deployAtEnd, String subDirName)
            throws Exception {

        setVariableValueToObject(mojo, "useCentralPortalApi", true);
        setVariableValueToObject(mojo, "autoDeploy", autoDeploy);
        setVariableValueToObject(mojo, "uploadToCentral", true);
        setVariableValueToObject(mojo, "deployAtEnd", deployAtEnd);

        centralPortalClient = mock(CentralPortalClient.class);
        String fakeDeploymentId = "deployment-" + subDirName;
        when(centralPortalClient.upload(any(File.class), anyBoolean())).thenReturn(fakeDeploymentId);
        String status = autoDeploy ? "PUBLISHING" : "VALIDATED";
        when(centralPortalClient.getStatus(fakeDeploymentId)).thenReturn(status);
        when(centralPortalClient.getPublishUrl()).thenReturn(SERVER_URL);
        setVariableValueToObject(mojo, "centralPortalClient", centralPortalClient);

        Artifact projectArtifact = createProjectArtifact();

        project.setArtifact(projectArtifact);
        project.setGroupId(GROUP_ID);
        project.setArtifactId(ARTIFACT_ID);
        project.setVersion(VERSION);

        // create a subdir under target to isolate tests from each other
        File targetSubDir = new File(getBasedir(), project.getBuild().getDirectory() + "/" + subDirName);
        project.getBuild().setDirectory(targetSubDir.getAbsolutePath());
        createAndAttachFakeSignedArtifacts(targetSubDir);

        mojo.execute();

        File bundleZip = new File(targetSubDir, bundleName);
        assertTrue("Expected central bundle zip to be created at " + bundleZip.getAbsolutePath(), bundleZip.exists());

        assertBundleContent(bundleZip);
    }

    // (5) Negative test: missing .asc files should fail
    public void testRainySignatureMissing() throws Exception {
        setVariableValueToObject(mojo, "useCentralPortalApi", true);
        setVariableValueToObject(mojo, "autoDeploy", true);
        setVariableValueToObject(mojo, "uploadToCentral", true);
        setVariableValueToObject(mojo, "deployAtEnd", true);

        centralPortalClient = mock(CentralPortalClient.class);
        String fakeDeploymentId = "deployment-5";
        when(centralPortalClient.upload(any(File.class), anyBoolean())).thenReturn(fakeDeploymentId);
        when(centralPortalClient.getStatus(fakeDeploymentId)).thenReturn("PUBLISHING");
        when(centralPortalClient.getPublishUrl()).thenReturn(SERVER_URL);
        setVariableValueToObject(mojo, "centralPortalClient", centralPortalClient);

        Artifact projectArtifact = createProjectArtifact();

        project.setArtifact(projectArtifact);
        project.setGroupId(GROUP_ID);
        project.setArtifactId(ARTIFACT_ID);
        project.setVersion(VERSION);

        // create a subdir under target to isolate tests from each other
        File targetSubDir = new File(getBasedir(), project.getBuild().getDirectory() + "/central-deploy-test-5");
        project.getBuild().setDirectory(targetSubDir.getAbsolutePath());
        createAndAttachFakeSignedArtifacts(targetSubDir);
        // Remove the pom sign file
        new File(targetSubDir, ARTIFACT_ID + "-" + VERSION + ".pom.asc").delete();

        MojoExecutionException thrown = assertThrows(MojoExecutionException.class, () -> mojo.execute());
        assertTrue(
                "Expected MojoExecutionException to be 'POM file [pomFile] is not signed, [pomFile].asc is missing' but was "
                        + thrown.toString(),
                thrown.getMessage().contains("pom is not signed,")
                        && thrown.getMessage().contains(".asc is missing"));
    }

    // (6) Negative test: missing javadoc files should fail
    public void testRainyJavadocMissing() throws Exception {
        setVariableValueToObject(mojo, "useCentralPortalApi", true);
        setVariableValueToObject(mojo, "autoDeploy", false);
        setVariableValueToObject(mojo, "uploadToCentral", false);
        setVariableValueToObject(mojo, "deployAtEnd", false);

        centralPortalClient = mock(CentralPortalClient.class);
        String fakeDeploymentId = "deployment-6";
        when(centralPortalClient.upload(any(File.class), anyBoolean())).thenReturn(fakeDeploymentId);
        when(centralPortalClient.getStatus(fakeDeploymentId)).thenReturn("PUBLISHING");
        when(centralPortalClient.getPublishUrl()).thenReturn(SERVER_URL);
        setVariableValueToObject(mojo, "centralPortalClient", centralPortalClient);

        Artifact projectArtifact = createProjectArtifact();

        project.setArtifact(projectArtifact);
        project.setGroupId(GROUP_ID);
        project.setArtifactId(ARTIFACT_ID);
        project.setVersion(VERSION);

        // create a subdir under target to isolate tests from each other
        File targetSubDir = new File(getBasedir(), project.getBuild().getDirectory() + "/central-deploy-test-6");
        project.getBuild().setDirectory(targetSubDir.getAbsolutePath());
        createAndAttachFakeSignedArtifacts(targetSubDir);
        // Remove the javadoc files
        for (File file : Objects.requireNonNull(targetSubDir.listFiles())) {
            if (file.getName().contains("-javadoc.")) {
                assertTrue(file.delete());
            }
        }

        MojoExecutionException thrown = assertThrows(MojoExecutionException.class, () -> mojo.execute());
        assertTrue(
                "Expected MojoExecutionException to be 'File [javadocFile] is not signed, [javadocFile].asc is missing' but was "
                        + thrown.toString(),
                thrown.getMessage().contains(" is not signed,")
                        && thrown.getMessage().contains(".asc is missing"));
    }

    private Artifact createProjectArtifact() {
        return new DefaultArtifact(
                GROUP_ID,
                ARTIFACT_ID,
                VERSION,
                null, // scope
                "jar", // type
                null, // classifier
                artifactHandler);
    }

    private DistributionManagement createDistributionManagement() {
        DistributionManagement distributionManagement = new DistributionManagement();
        DeploymentRepository deploymentRepository = new DeploymentRepository();
        deploymentRepository.setId(SERVER_ID);
        deploymentRepository.setUrl(SERVER_URL);
        distributionManagement.setRepository(deploymentRepository);
        return distributionManagement;
    }

    // Helper method to create fake signed files for central bundle
    private void createAndAttachFakeSignedArtifacts(File baseDir)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        MavenProjectHelper projectHelper = new DefaultMavenProjectHelper();

        ArtifactHandlerManager artifactHandlerManager = mock(ArtifactHandlerManager.class);
        when(artifactHandlerManager.getArtifactHandler(any())).thenReturn(artifactHandler);
        Field handlerField = DefaultMavenProjectHelper.class.getDeclaredField("artifactHandlerManager");
        handlerField.setAccessible(true);
        handlerField.set(projectHelper, artifactHandlerManager);

        baseDir.mkdirs();

        File mainJar = new File(baseDir, BASE_NAME + ".jar");
        File mainJarAsc = new File(baseDir, BASE_NAME + ".jar.asc");

        File sourcesJar = new File(baseDir, BASE_NAME + "-sources.jar");
        File sourcesAsc = new File(baseDir, BASE_NAME + "-sources.jar.asc");

        File javadocJar = new File(baseDir, BASE_NAME + "-javadoc.jar");
        File javadocAsc = new File(baseDir, BASE_NAME + "-javadoc.jar.asc");

        File pomFile = new File(baseDir, BASE_NAME + ".pom");
        File pomAsc = new File(baseDir, BASE_NAME + ".pom.asc");

        // Write fake content
        List<String> content = Collections.singletonList("generated " + new Date());
        Files.write(mainJar.toPath(), content, StandardCharsets.UTF_8);
        Files.write(mainJarAsc.toPath(), Collections.singletonList("signature"), StandardCharsets.UTF_8);

        Files.write(sourcesJar.toPath(), content, StandardCharsets.UTF_8);
        Files.write(sourcesAsc.toPath(), Collections.singletonList("signature"), StandardCharsets.UTF_8);

        Files.write(javadocJar.toPath(), content, StandardCharsets.UTF_8);
        Files.write(javadocAsc.toPath(), Collections.singletonList("signature"), StandardCharsets.UTF_8);

        // Write minimal POM XML
        String pomXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\""
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 "
                + "http://maven.apache.org/xsd/maven-4.0.0.xsd\">"
                + "  <modelVersion>4.0.0</modelVersion>"
                + "  <groupId>" + GROUP_ID + "</groupId>"
                + "  <artifactId>" + ARTIFACT_ID + "</artifactId>"
                + "  <version>" + VERSION + "</version>"
                + "  <description>Test deployment with sources and javadoc</description>"
                + "  <licenses>"
                + "    <license>"
                + "      <name>The Apache License, Version 2.0</name>"
                + "      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>"
                + "      <distribution>repo</distribution>"
                + "    </license>"
                + "  </licenses>"
                + "  <scm>"
                + "    <url>https://github.com/apache/maven-deploy-plugin</url>"
                + "    <connection>scm:git:https://github.com/apache/maven-deploy-plugin.git</connection>"
                + "    <developerConnection>scm:git:https://github.com/apache/maven-deploy-plugin.git</developerConnection>"
                + "  </scm>"
                + "  <developers>"
                + "    <developer>"
                + "      <id>jdoe</id>"
                + "      <name>John Doe</name>"
                + "      <email>jdoe@example.com</email>"
                + "    </developer>"
                + "  </developers>"
                + "</project>";

        Files.write(pomFile.toPath(), pomXml.getBytes(StandardCharsets.UTF_8));
        Files.write(pomAsc.toPath(), Collections.singletonList("signature"), StandardCharsets.UTF_8);

        // === Attach main artifacts ===
        project.getArtifact().setFile(mainJar);
        project.setFile(pomFile);

        // === Attach other artifacts ===
        projectHelper.attachArtifact(project, "jar", "sources", sourcesJar);
        projectHelper.attachArtifact(project, "jar", "javadoc", javadocJar);
    }

    // Helper method to verify central bundle contents
    private void assertBundleContent(File bundleZip) throws IOException {
        String prefix = GROUP_ID.replace('.', '/') + "/" + ARTIFACT_ID + "/" + VERSION + "/";
        try (ZipFile zip = new ZipFile(bundleZip)) {
            assertZipHasEntries(
                    zip,
                    prefix,
                    ".pom",
                    ".pom.asc",
                    ".jar",
                    ".jar.asc",
                    "-javadoc.jar",
                    "-javadoc.jar.asc",
                    "-sources.jar",
                    "-sources.jar.asc");
        }
    }

    private void assertZipHasEntries(ZipFile zip, String prefix, String... suffixes) {
        for (String suffix : suffixes) {
            String entryName = prefix + BASE_NAME + suffix;
            assertNotNull("Missing zip entry: " + entryName, zip.getEntry(entryName));
        }
    }
}
