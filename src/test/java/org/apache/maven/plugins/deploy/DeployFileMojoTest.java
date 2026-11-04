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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.plugin.testing.MojoExtension.getBasedir;
import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest(realRepositorySession = true)
class DeployFileMojoTest {

    private File remoteRepo;

    @Inject
    private MavenSession session;

    @BeforeEach
    void setUp() throws IOException {
        remoteRepo = new File(getBasedir(), "target/remote-repo");
        // a repository left by an earlier run would hide a deployment that did not happen
        FileUtils.deleteDirectory(remoteRepo);
        remoteRepo.mkdirs();
    }

    @Test
    @InjectMojo(goal = "deploy-file")
    void testDeployTestEnvironment(DeployFileMojo mojo) {
        assertNotNull(mojo);
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-test")
    void testBasicDeployFile(DeployFileMojo mojo) throws Exception {

        assertNotNull(mojo);

        String groupId = getVariableValueFromObject(mojo, "groupId");
        String artifactId = getVariableValueFromObject(mojo, "artifactId");
        String version = getVariableValueFromObject(mojo, "version");
        String packaging = getVariableValueFromObject(mojo, "packaging");
        File file = getVariableValueFromObject(mojo, "file");
        String repositoryId = getVariableValueFromObject(mojo, "repositoryId");
        String url = getVariableValueFromObject(mojo, "url");
        String skip = getVariableValueFromObject(mojo, "skip");

        assertEquals("org.apache.maven.test", groupId);
        assertEquals("maven-deploy-file-test", artifactId);
        assertEquals("1.0", version);
        assertEquals("jar", packaging);
        assertEquals("snapshots", skip);
        assertTrue(file.exists());
        assertEquals("deploy-test", repositoryId);
        assertEquals("file://" + getBasedir() + "/target/remote-repo/deploy-file-test", url);

        mojo.execute();

        // check the generated pom
        File pom = new File(
                remoteRepo,
                "deploy-file-test/" + groupId.replace('.', '/') + "/"
                        + artifactId + "/" + version + "/" + artifactId + "-"
                        + version + ".pom");

        assertTrue(pom.exists());

        Model model = mojo.readModel(pom);

        assertEquals("4.0.0", model.getModelVersion());
        assertEquals(groupId, model.getGroupId());
        assertEquals(artifactId, model.getArtifactId());
        assertEquals(version, model.getVersion());
        assertEquals(packaging, model.getPackaging());
        assertEquals("POM was created from deploy:deploy-file", model.getDescription());

        // check the remote-repo
        ArrayList<String> expectedFiles = new ArrayList<>();
        List<String> fileList = new ArrayList<>();

        File repo = new File(remoteRepo, "deploy-file-test");

        File[] files = repo.listFiles();

        for (File file1 : Objects.requireNonNull(files)) {
            addFileToList(file1, fileList);
        }

        expectedFiles.add("org");
        expectedFiles.add("apache");
        expectedFiles.add("maven");
        expectedFiles.add("test");
        expectedFiles.add("maven-deploy-file-test");
        expectedFiles.add("1.0");
        expectedFiles.add("maven-metadata.xml");
        expectedFiles.add("maven-metadata.xml.md5");
        expectedFiles.add("maven-metadata.xml.sha1");
        expectedFiles.add("maven-deploy-file-test-1.0.jar");
        expectedFiles.add("maven-deploy-file-test-1.0.jar.md5");
        expectedFiles.add("maven-deploy-file-test-1.0.jar.sha1");
        expectedFiles.add("maven-deploy-file-test-1.0.pom");
        expectedFiles.add("maven-deploy-file-test-1.0.pom.md5");
        expectedFiles.add("maven-deploy-file-test-1.0.pom.sha1");

        assertEquals(expectedFiles.size(), fileList.size());

        assertEquals(0, getSizeOfExpectedFiles(fileList, expectedFiles));
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-classifier")
    void testDeployIfClassifierIsSet(DeployFileMojo mojo) throws Exception {

        assertNotNull(mojo);

        String classifier = getVariableValueFromObject(mojo, "classifier");
        String groupId = getVariableValueFromObject(mojo, "groupId");
        String artifactId = getVariableValueFromObject(mojo, "artifactId");
        String version = getVariableValueFromObject(mojo, "version");

        assertEquals("bin", classifier);

        mojo.execute();

        File deployedArtifact = new File(
                remoteRepo,
                "deploy-file-classifier/" + groupId.replace('.', '/') + "/"
                        + artifactId + "/" + version + "/" + artifactId + "-"
                        + version + "-" + classifier + ".jar");

        assertTrue(deployedArtifact.exists());

        mojo.setClassifier("prod");

        assertEquals("prod", mojo.getClassifier());

        mojo.execute();

        File prodDeployedArtifact = new File(
                remoteRepo,
                "deploy-file-classifier/" + groupId.replace('.', '/') + "/"
                        + artifactId + "/" + version + "/" + artifactId + "-"
                        + version + "-" + mojo.getClassifier() + ".jar");

        assertTrue(prodDeployedArtifact.exists());
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-artifact-not-jar")
    void testDeployIfArtifactIsNotJar(DeployFileMojo mojo) throws Exception {

        assertNotNull(mojo);

        String groupId = getVariableValueFromObject(mojo, "groupId");
        String artifactId = getVariableValueFromObject(mojo, "artifactId");
        String version = getVariableValueFromObject(mojo, "version");

        assertEquals("org.apache.maven.test", groupId);
        assertEquals("maven-deploy-file-test", artifactId);
        assertEquals("1.0", version);

        mojo.execute();

        File file = new File(
                remoteRepo,
                "deploy-file-artifact-not-jar/" + groupId.replace('.', '/') + "/"
                        + artifactId + "/" + version + "/" + artifactId + "-"
                        + version + ".zip");

        assertTrue(file.exists());
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-packaging")
    void testDeployFileIfPackagingIsSet(DeployFileMojo mojo) throws Exception {

        assertNotNull(mojo);

        String packaging = getVariableValueFromObject(mojo, "packaging");
        String groupId = getVariableValueFromObject(mojo, "groupId");
        String artifactId = getVariableValueFromObject(mojo, "artifactId");
        String version = getVariableValueFromObject(mojo, "version");

        assertEquals("differentpackaging", packaging);

        mojo.execute();

        File deployedArtifact = new File(
                remoteRepo,
                "deploy-file-packaging/" + groupId.replace('.', '/') + "/"
                        + artifactId + "/" + version + "/" + artifactId + "-"
                        + version + "." + packaging);

        assertTrue(deployedArtifact.exists());
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-3rd-party-extras")
    void testDeployJarWithExtraFilesAndNoPom(DeployFileMojo mojo) throws Exception {
        mojo.execute();

        String ga = "org/apache/maven/its/deploy/tpjwop1/test/";
        assertDeployed(
                ga + "maven-metadata.xml",
                ga + "1.0/test-1.0.jar",
                ga + "1.0/test-1.0.zip",
                ga + "1.0/test-1.0-src.tar.gz",
                ga + "1.0/test-1.0.tar.gz");
        assertNotDeployed(ga + "1.0/test-1.0.pom");
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-3rd-party-no-pom")
    void testDeployJarWithoutPom(DeployFileMojo mojo) throws Exception {
        mojo.execute();

        String ga = "org/apache/maven/its/deploy/tpjwop2/test/";
        assertDeployed(ga + "maven-metadata.xml", ga + "1.0/test-1.0.jar");
        assertNotDeployed(ga + "1.0/test-1.0.pom");
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-3rd-party-pom")
    void testDeployPomWithExtraFiles(DeployFileMojo mojo) throws Exception {
        mojo.execute();

        String ga = "org/apache/maven/its/deploy/tppwoc/test/";
        assertDeployed(
                ga + "maven-metadata.xml",
                ga + "1.0/test-1.0.pom",
                ga + "1.0/test-1.0.zip",
                ga + "1.0/test-1.0-src.tar.gz",
                ga + "1.0/test-1.0.tar.gz");
        assertNotDeployed(ga + "1.0/test-1.0.jar");
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-external-pom")
    void testDeployJarWithExternalPomInheritingFromParent(DeployFileMojo mojo) throws Exception {
        mojo.execute();

        // the coordinates come from the parent element of the POM file (MDEPLOY-56)
        String ga = "org/apache/maven/its/deploy/erj/test/";
        assertDeployed(ga + "maven-metadata.xml", ga + "1.0/test-1.0.pom", ga + "1.0/test-1.0.jar");
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-default-packaging")
    void testDeployWithoutPackagingUsesFileExtension(DeployFileMojo mojo) throws Exception {
        assertNull(getVariableValueFromObject(mojo, "packaging"));

        mojo.execute();

        assertDeployed("org/apache/maven/test/test/1.1/test-1.1.jar");
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-gav")
    void testIncompleteArtifactInformationIsRejected(DeployFileMojo mojo) {
        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(e.getMessage().contains("The artifact information is incomplete"), e.getMessage());
        assertEquals(0, remoteRepo.list().length);
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-gav")
    void testInvalidArtifactInformationIsRejected(DeployFileMojo mojo) throws Exception {
        setVariableValueToObject(mojo, "groupId", "invalid~group");
        setVariableValueToObject(mojo, "artifactId", "invalid/artifactId");
        setVariableValueToObject(mojo, "version", "1+");

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(e.getMessage().contains("The artifact information is not valid"), e.getMessage());
        assertEquals(0, remoteRepo.list().length);
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-embedded-pom")
    void testDeployUsesPomEmbeddedInJar(DeployFileMojo mojo) throws Exception {
        String pomXml = "<project><modelVersion>4.0.0</modelVersion>"
                + "<groupId>org.apache.maven.plugins.deploy.its</groupId><artifactId>mdeploy178</artifactId>"
                + "<version>1.0</version><url>https://issues.apache.org/jira/browse/MDEPLOY-178</url></project>";
        createJar(
                new File(getBasedir(), "target/mdeploy178-1.0.jar"),
                "META-INF/maven/org.apache.maven.plugins.deploy.its/mdeploy178/pom.xml",
                pomXml);

        mojo.execute();

        String dir = "org/apache/maven/plugins/deploy/its/mdeploy178/1.0/";
        assertDeployed(dir + "mdeploy178-1.0.jar", dir + "mdeploy178-1.0.pom");
        // the deployed POM is the embedded one, not a generated one
        Model deployed = mojo.readModel(new File(remoteRepo, dir + "mdeploy178-1.0.pom"));
        assertEquals("https://issues.apache.org/jira/browse/MDEPLOY-178", deployed.getUrl());
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-skip")
    void testSkipTrueDeploysNothing(DeployFileMojo mojo) throws Exception {
        assertSkip(mojo, "true", "1.0", false);
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-skip")
    void testSkipFalseDeploys(DeployFileMojo mojo) throws Exception {
        assertSkip(mojo, "false", "1.0", true);
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-skip")
    void testSkipReleasesDoesNotDeployRelease(DeployFileMojo mojo) throws Exception {
        assertSkip(mojo, "releases", "1.0", false);
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-skip")
    void testSkipSnapshotsDeploysRelease(DeployFileMojo mojo) throws Exception {
        assertSkip(mojo, "snapshots", "1.0", true);
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-skip")
    void testSkipSnapshotsDoesNotDeploySnapshot(DeployFileMojo mojo) throws Exception {
        assertSkip(mojo, "snapshots", "1.0-SNAPSHOT", false);
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-skip")
    void testSkipReleasesDeploysSnapshot(DeployFileMojo mojo) throws Exception {
        assertSkip(mojo, "releases", "1.0-SNAPSHOT", true);
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-extra-files")
    void testDeployOnlyConfiguredFilesWithClassifier(DeployFileMojo mojo) throws Exception {
        mojo.execute();

        // MDEPLOY-212: the main artifact is a tar.gz, the zip is a side artifact with the classifier "it"
        String dir = "org/apache/maven/its/mdeploy-212/test/1.0/";
        assertDeployed(dir + "test-1.0.pom", dir + "test-1.0.tar.gz", dir + "test-1.0-it.zip");
        assertNotDeployed(dir + "test-1.0.jar", dir + "test-1.0-javadoc.jar");
    }

    @Test
    @InjectMojo(goal = "deploy-file", pom = "plugin-config.xml")
    @Basedir("/unit/deploy-file-sources-javadoc")
    void testDeploySourcesAndJavadoc(DeployFileMojo mojo) throws Exception {
        File target = new File(getBasedir(), "target");
        createJar(new File(target, "test-1.0.jar"), "a.txt", "main");
        createJar(new File(target, "test-1.0-sources.jar"), "a.txt", "sources");
        createJar(new File(target, "test-1.0-javadoc.jar"), "a.txt", "javadoc");

        mojo.execute();

        String dir = "org/apache/maven/its/deploy/test/1.0/";
        assertDeployed(
                dir + "test-1.0.jar", dir + "test-1.0.pom", dir + "test-1.0-sources.jar", dir + "test-1.0-javadoc.jar");
    }

    private void assertSkip(DeployFileMojo mojo, String skip, String version, boolean deployed) throws Exception {
        createJar(new File(getBasedir(), "target/deployfile-skip.jar"), "a.txt", "content");
        setVariableValueToObject(mojo, "skip", skip);
        setVariableValueToObject(mojo, "version", version);

        mojo.execute();

        File dir = new File(remoteRepo, "org/apache/maven/plugins/deploy/its/deployfile-skip/" + version);
        if (deployed) {
            // a snapshot file name carries the timestamp and the build number
            String artifactAndVersion = "deployfile-skip-1\\.0(-SNAPSHOT|-\\d{8}\\.\\d{6}-\\d+)?";
            List<String> names = Arrays.asList(Objects.requireNonNull(dir.list()));
            assertEquals(
                    1,
                    names.stream()
                            .filter(n -> n.matches(artifactAndVersion + "\\.jar"))
                            .count(),
                    names.toString());
            assertEquals(
                    1,
                    names.stream()
                            .filter(n -> n.matches(artifactAndVersion + "\\.pom"))
                            .count(),
                    names.toString());
        } else {
            assertEquals(0, remoteRepo.list().length, "something was deployed to " + remoteRepo);
        }
    }

    private void assertDeployed(String... paths) {
        for (String path : paths) {
            assertTrue(new File(remoteRepo, path).isFile(), "missing in " + remoteRepo + ": " + path);
        }
    }

    private void assertNotDeployed(String... paths) {
        for (String path : paths) {
            assertFalse(new File(remoteRepo, path).exists(), "unexpected in " + remoteRepo + ": " + path);
        }
    }

    private static void createJar(File jar, String entry, String content) throws IOException {
        jar.getParentFile().mkdirs();
        try (OutputStream out = Files.newOutputStream(jar.toPath());
                JarOutputStream jos = new JarOutputStream(out)) {
            jos.putNextEntry(new JarEntry(entry));
            jos.write(content.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
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
            if (expectedFiles.contains(fileName)) {
                expectedFiles.remove(fileName);
            } else {
                fail(fileName + " is not included in the expected files");
            }
        }
        return expectedFiles.size();
    }
}
