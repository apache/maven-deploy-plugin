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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Parent;
import org.apache.maven.api.plugin.MojoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="jerome@coffeebreaks.org">Jerome Lacoste</a>
 */
class DeployFileMojoUnitTest {
    MockDeployFileMojo mojo;
    Parent parent;

    @BeforeEach
    void setUp() {
        parent = Parent.newBuilder()
                .groupId("parentGroup")
                .artifactId("parentArtifact")
                .version("parentVersion")
                .build();
        Model pomModel = Model.newBuilder().packaging(null).parent(parent).build();
        mojo = new MockDeployFileMojo(pomModel);
    }

    static class MockDeployFileMojo extends DeployFileMojo {
        private Model model;

        MockDeployFileMojo(Model model) {
            this.model = model;
        }

        @Override
        protected Model readModel(Path pomFile) throws MojoException {
            return model;
        }
    }

    @Test
    void processPomFromPomFileWithParent4() {
        mojo.setPomFile(Paths.get("foo.bar"));
        setMojoModel(mojo, null, "artifact", "version", "packaging", parent);
        mojo.initProperties();
        checkMojoProperties("parentGroup", "artifact", "version", "packaging");
    }

    @Test
    void processPomFromPomFileWithParent5() {
        mojo.setPomFile(Paths.get("foo.bar"));
        setMojoModel(mojo, "group", "artifact", "version", "packaging", parent);
        mojo.initProperties();
        checkMojoProperties("group", "artifact", "version", "packaging");
    }

    @Test
    void processPomFromPomFileWithParent6() {
        mojo.setPomFile(Paths.get("foo.bar"));
        setMojoModel(mojo, "group", "artifact", "version", "packaging", null);
        mojo.initProperties();
        checkMojoProperties("group", "artifact", "version", "packaging");
    }

    @Test
    void processPomFromPomFileWithOverrides() {
        mojo.setPomFile(Paths.get("foo.bar"));
        setMojoModel(mojo, "group", "artifact", "version", "packaging", null);
        mojo.setGroupId("groupO");
        mojo.setArtifactId("artifactO");
        mojo.setVersion("versionO");
        mojo.setPackaging("packagingO");
        mojo.initProperties();
        checkMojoProperties("groupO", "artifactO", "versionO", "packagingO");
    }

    private void checkMojoProperties(
            final String expectedGroup,
            final String expectedArtifact,
            final String expectedVersion,
            final String expectedPackaging) {
        assertEquals(expectedGroup, mojo.getGroupId());
        assertEquals(expectedArtifact, mojo.getArtifactId());
        assertEquals(expectedVersion, mojo.getVersion());
        assertEquals(expectedPackaging, mojo.getPackaging());
    }

    @Test
    void idValidationRejectsDotOnlyAndEmptySegments() {
        assertFalse(AbstractDeployMojo.isValidId("."));
        assertFalse(AbstractDeployMojo.isValidId(".."));
        assertFalse(AbstractDeployMojo.isValidId(".a"));
        assertFalse(AbstractDeployMojo.isValidId("a."));
        assertFalse(AbstractDeployMojo.isValidId("a..b"));
        assertFalse(AbstractDeployMojo.isValidId("a/b"));
        assertFalse(AbstractDeployMojo.isValidId("a\\b"));
        assertFalse(AbstractDeployMojo.isValidId(""));
        assertFalse(AbstractDeployMojo.isValidId(null));
        assertTrue(AbstractDeployMojo.isValidId("org.apache.maven"));
        assertTrue(AbstractDeployMojo.isValidId("maven-deploy-plugin"));
    }

    @Test
    void versionValidationRejectsDotOnlyWhitespaceAndControlChars() {
        assertFalse(AbstractDeployMojo.isValidVersion("."));
        assertFalse(AbstractDeployMojo.isValidVersion(".."));
        assertFalse(AbstractDeployMojo.isValidVersion("1.0 "));
        assertFalse(AbstractDeployMojo.isValidVersion("1\t0"));
        assertFalse(AbstractDeployMojo.isValidVersion("1.0/x"));
        assertFalse(AbstractDeployMojo.isValidVersion(""));
        assertFalse(AbstractDeployMojo.isValidVersion(null));
        assertTrue(AbstractDeployMojo.isValidVersion("1.0-SNAPSHOT"));
        assertTrue(AbstractDeployMojo.isValidVersion("4.0.0-beta-3"));
    }

    @Test
    void classifierAndTypeValidationRejectsLayoutTraversal() {
        assertFalse(AbstractDeployMojo.isValidClassifier("../../../../org/other/1.0/other-1.0"));
        assertFalse(AbstractDeployMojo.isValidClassifier(".."));
        assertFalse(AbstractDeployMojo.isValidClassifier("a b"));
        assertTrue(AbstractDeployMojo.isValidClassifier(null));
        assertTrue(AbstractDeployMojo.isValidClassifier(""));
        assertTrue(AbstractDeployMojo.isValidClassifier("sources"));
        assertTrue(AbstractDeployMojo.isValidClassifier("site.pdf"));
        assertFalse(AbstractDeployMojo.isValidTypeOrExtension(null));
        assertFalse(AbstractDeployMojo.isValidTypeOrExtension(""));
        assertFalse(AbstractDeployMojo.isValidTypeOrExtension(".."));
        assertFalse(AbstractDeployMojo.isValidTypeOrExtension("jar/../x"));
        assertTrue(AbstractDeployMojo.isValidTypeOrExtension("jar"));
        assertTrue(AbstractDeployMojo.isValidTypeOrExtension("tar.gz"));
        assertTrue(AbstractDeployMojo.isValidTypeOrExtension("maven-plugin"));
    }

    @Test
    void urlUserInfoIsRedactedForLogging() {
        assertEquals(
                "https://***@repo.example/releases",
                AbstractDeployMojo.redactUrlUserInfo("https://user:s3cr3t@repo.example/releases"));
        assertEquals(
                "my-repo::https://***@repo.example/releases",
                AbstractDeployMojo.redactUrlUserInfo("my-repo::https://ci-bot:tok3n@repo.example/releases"));
        assertEquals(
                "https://repo.example/releases", AbstractDeployMojo.redactUrlUserInfo("https://repo.example/releases"));
        assertEquals("file:///tmp/repo", AbstractDeployMojo.redactUrlUserInfo("file:///tmp/repo"));
    }

    @Test
    void containmentDirectoryIsEnforced() throws IOException {
        Path root = Files.createTempDirectory("deploy-file-containment");
        Path inside = Files.createFile(root.resolve("artifact.jar"));

        assertTrue(DeployFileMojo.isContainedIn(inside, root));
        assertTrue(DeployFileMojo.isContainedIn(root.resolve("sub/other.jar"), root));
        assertFalse(DeployFileMojo.isContainedIn(root.resolve("../escaped.jar"), root));
        assertFalse(DeployFileMojo.isContainedIn(Paths.get("/etc/passwd"), root));
    }

    private void setMojoModel(
            MockDeployFileMojo mojo, String group, String artifact, String version, String packaging, Parent parent) {
        mojo.model = Model.newBuilder()
                .groupId(group)
                .artifactId(artifact)
                .version(version)
                .packaging(packaging)
                .parent(parent)
                .build();
    }
}
