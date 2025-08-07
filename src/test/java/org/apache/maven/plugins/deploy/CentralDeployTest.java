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
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugins.deploy.stubs.MavenProjectBigStub;
import org.apache.maven.project.DefaultMavenProjectHelper;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
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
    private static final String LOCAL_REPO = getBasedir() + "/target/local-repo";
    private static final String SERVER_ID = "central";
    private static final String SERVER_URL = "http://localhost:8081/api/v1";

    MavenProjectBigStub project;

    private AutoCloseable openMocks;

    private MavenSession session;

    private File localRepo;

    private CentralPortalClient centralPortalClient;

    private DeployMojo mojo;

    public void setUp() throws Exception {
        super.setUp();
        project = new MavenProjectBigStub();
        session = mock(MavenSession.class);
        Settings settings = mock(Settings.class);
        Server server = new Server();
        server.setId(SERVER_ID);
        server.setUsername("dummy-user");
        server.setPassword("dummy-password");
        when(session.getPluginContext(any(PluginDescriptor.class), any(MavenProject.class)))
                .thenReturn(new ConcurrentHashMap<>());
        DefaultRepositorySystemSession repositorySession = new DefaultRepositorySystemSession();
        repositorySession.setLocalRepositoryManager(
                new SimpleLocalRepositoryManagerFactory(new DefaultLocalPathComposer())
                        .newInstance(repositorySession, new LocalRepository(LOCAL_REPO)));
        when(session.getRepositorySession()).thenReturn(repositorySession);
        when(settings.getServer(SERVER_ID)).thenReturn(server);
        when(session.getSettings()).thenReturn(settings);

        localRepo = new File(LOCAL_REPO);

        if (localRepo.exists()) {
            FileUtils.deleteDirectory(localRepo);
        }
    }

    public void tearDown() throws Exception {
        super.tearDown();

        if (openMocks != null) {
            openMocks.close();
        }
    }

    // (1) autoDeploy = true, uploadToCentral = true
    public void testCentralPortalAutoDeployTrueUploadToCentralTrue() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/central-deploy-test/plugin-config.xml");
        mojo = (DeployMojo) lookupMojo("deploy", testPom);
        openMocks = MockitoAnnotations.openMocks(this);
        assertNotNull(mojo);

        setVariableValueToObject(mojo, "session", session);

        setVariableValueToObject(mojo, "useCentralPortalApi", true);
        setVariableValueToObject(mojo, "autoDeploy", true);
        setVariableValueToObject(mojo, "uploadToCentral", true);

        centralPortalClient = mock(CentralPortalClient.class);
        String fakeDeploymentId = "deployment-123";
        when(centralPortalClient.upload(any(File.class), anyBoolean())).thenReturn(fakeDeploymentId);
        when(centralPortalClient.getStatus(fakeDeploymentId)).thenReturn("PUBLISHING");
        when(centralPortalClient.getPublishUrl()).thenReturn(SERVER_URL);

        setVariableValueToObject(mojo, "centralPortalClient", centralPortalClient);

        DefaultRepositorySystemSession repoSession = new DefaultRepositorySystemSession();
        repoSession.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory(new DefaultLocalPathComposer())
                .newInstance(repoSession, new LocalRepository(LOCAL_REPO)));
        when(session.getRepositorySession()).thenReturn(repoSession);

        setVariableValueToObject(mojo, "project", project);
        ArtifactHandler artifactHandler = new DefaultArtifactHandler("jar");
        Artifact projectArtifact = new DefaultArtifact(
                GROUP_ID,
                ARTIFACT_ID,
                VERSION,
                null, // scope
                "jar", // type
                null, // classifier
                artifactHandler);

        project.setArtifact(projectArtifact);
        project.setGroupId(GROUP_ID);
        project.setArtifactId(ARTIFACT_ID);
        project.setVersion(VERSION);
        setVariableValueToObject(mojo, "pluginContext", new ConcurrentHashMap<>());
        setVariableValueToObject(mojo, "reactorProjects", Collections.singletonList(project));

        File baseDir = new File(getBasedir(), "target");
        String baseName = "central-deploy-test-1.0.0";

        File artifactFile = createAndAttachFakeSignedArtifacts(baseDir);

        Artifact artifact = project.getArtifact();
        artifact.setFile(artifactFile);
        project.setFile(new File(baseDir, baseName + ".pom"));

        Build build = new Build();
        build.setDirectory(baseDir.getAbsolutePath());
        project.setBuild(build);

        DistributionManagement distributionManagement = new DistributionManagement();
        DeploymentRepository deploymentRepository = new DeploymentRepository();
        deploymentRepository.setId(SERVER_ID);
        deploymentRepository.setUrl(SERVER_URL);
        distributionManagement.setRepository(deploymentRepository);
        project.setDistributionManagement(distributionManagement);

        mojo.execute();

        File bundleZip = new File(project.getBasedir(), "target/" + BASE_NAME + "-bundle.zip");
        assertTrue("Expected central bundle zip to be created at " + bundleZip.getAbsolutePath(), bundleZip.exists());
        String prefix = GROUP_ID.replace('.', '/') + "/" + ARTIFACT_ID + "/" + VERSION + "/";
        assertBundleContains(prefix, bundleZip);

        // Also assert that nothing was deployed to the mock remote repo
        File remoteDir = new File(getBasedir(), "target/remote-repo/" + ARTIFACT_ID);
        assertFalse("Jar should NOT be deployed to remote repo", new File(remoteDir, baseName + ".jar").exists());
    }

    // Helper method to create fake signed files for central bundle
    private File createAndAttachFakeSignedArtifacts(File baseDir)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        MavenProjectHelper projectHelper = new DefaultMavenProjectHelper();
        ArtifactHandlerManager artifactHandlerManager = mock(ArtifactHandlerManager.class);
        when(artifactHandlerManager.getArtifactHandler(anyString())).thenAnswer(invocation -> {
            String type = invocation.getArgument(0);
            DefaultArtifactHandler handler = new DefaultArtifactHandler(type);
            handler.setExtension(type);
            return handler;
        });
        Field handlerField = DefaultMavenProjectHelper.class.getDeclaredField("artifactHandlerManager");
        handlerField.setAccessible(true);
        handlerField.set(projectHelper, artifactHandlerManager);

        baseDir.mkdirs();

        // === Main artifact ===
        File mainJar = new File(baseDir, BASE_NAME + ".jar");
        File mainJarAsc = new File(baseDir, BASE_NAME + ".jar.asc");

        // === Sources ===
        File sourcesJar = new File(baseDir, BASE_NAME + "-sources.jar");
        File sourcesAsc = new File(baseDir, BASE_NAME + "-sources.jar.asc");

        // === Javadoc ===
        File javadocJar = new File(baseDir, BASE_NAME + "-javadoc.jar");
        File javadocAsc = new File(baseDir, BASE_NAME + "-javadoc.jar.asc");

        // === POM ===
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
        String pomXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 "
                + "http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <groupId>" + GROUP_ID + "</groupId>\n"
                + "  <artifactId>" + ARTIFACT_ID + "</artifactId>\n"
                + "  <version>" + VERSION + "</version>\n"
                + "  <description>Test deployment with sources and javadoc</description>\n"
                + "  <licenses>\n"
                + "    <license>\n"
                + "      <name>The Apache License, Version 2.0</name>\n"
                + "      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>\n"
                + "      <distribution>repo</distribution>\n"
                + "    </license>\n"
                + "  </licenses>\n"
                + "  <scm>\n"
                + "    <url>https://github.com/apache/maven-deploy-plugin</url>\n"
                + "    <connection>scm:git:https://github.com/apache/maven-deploy-plugin.git</connection>\n"
                + "    <developerConnection>scm:git:https://github.com/apache/maven-deploy-plugin.git</developerConnection>\n"
                + "  </scm>\n"
                + "  <developers>\n"
                + "    <developer>\n"
                + "      <id>jdoe</id>\n"
                + "      <name>John Doe</name>\n"
                + "      <email>jdoe@example.com</email>\n"
                + "    </developer>\n"
                + "  </developers>\n"
                + "</project>\n";

        Files.write(pomFile.toPath(), pomXml.getBytes(StandardCharsets.UTF_8));
        Files.write(pomAsc.toPath(), Collections.singletonList("signature"), StandardCharsets.UTF_8);

        // === Attach main artifacts ===
        project.getArtifact().setFile(mainJar);
        project.setFile(pomFile);

        // === Attach other artifacts ===
        projectHelper.attachArtifact(project, "jar", "sources", sourcesJar);
        projectHelper.attachArtifact(project, "jar", "javadoc", javadocJar);

        return mainJar;
    }

    // Helper method to verify central bundle contents
    private void assertBundleContains(String prefix, File bundleZip) throws IOException {
        try (ZipFile zip = new ZipFile(bundleZip)) {
            assertNotNull(zip.getEntry(prefix + BASE_NAME + ".jar"));
            assertNotNull(zip.getEntry(prefix + BASE_NAME + ".jar.asc"));
            assertNotNull(zip.getEntry(prefix + BASE_NAME + ".pom"));
            assertNotNull(zip.getEntry(prefix + BASE_NAME + ".pom.asc"));
        }
    }
}
