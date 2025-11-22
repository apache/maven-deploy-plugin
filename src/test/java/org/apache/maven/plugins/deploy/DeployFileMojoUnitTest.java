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

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="jerome@coffeebreaks.org">Jerome Lacoste</a>
 */
class DeployFileMojoUnitTest {
    private MockDeployFileMojo mojo;
    private Parent parent;

    @BeforeEach
    void setUp() {
        Model pomModel = new Model();
        pomModel.setPackaging(null);

        parent = new Parent();
        parent.setGroupId("parentGroup");
        parent.setArtifactId("parentArtifact");
        parent.setVersion("parentVersion");

        mojo = new MockDeployFileMojo(pomModel);
    }

    static class MockDeployFileMojo extends DeployFileMojo {
        private Model model;

        MockDeployFileMojo(Model model) {
            super(null, null);
            this.model = model;
        }

        @Override
        protected Model readModel(File pomFile) {
            return model;
        }
    }

    @Test
    void testProcessPomFromPomFileWithParent1() {
        mojo.setPomFile(new File("foo.bar"));

        setMojoModel(mojo.model, null, null, null, null, parent);

        try {
            mojo.initProperties();
        } catch (MojoExecutionException expected) {
            assertTrue(true); // missing artifactId and packaging
        }

        checkMojoProperties("parentGroup", null, "parentVersion", null);
    }

    @Test
    void testProcessPomFromPomFileWithParent2() {
        mojo.setPomFile(new File("foo.bar"));
        setMojoModel(mojo.model, null, "artifact", null, null, parent);

        try {
            mojo.initProperties();
        } catch (MojoExecutionException expected) {
            assertTrue(true); // missing packaging
        }

        checkMojoProperties("parentGroup", "artifact", "parentVersion", null);
    }

    @Test
    void testProcessPomFromPomFileWithParent3() {
        mojo.setPomFile(new File("foo.bar"));
        setMojoModel(mojo.model, null, "artifact", "version", null, parent);

        try {
            mojo.initProperties();
        } catch (MojoExecutionException expected) {
            assertTrue(true); // missing version and packaging
        }

        checkMojoProperties("parentGroup", "artifact", "version", null);
    }

    @Test
    void testProcessPomFromPomFileWithParent4() throws MojoExecutionException {
        mojo.setPomFile(new File("foo.bar"));
        setMojoModel(mojo.model, null, "artifact", "version", "packaging", parent);

        mojo.initProperties();

        checkMojoProperties("parentGroup", "artifact", "version", "packaging");
    }

    @Test
    void testProcessPomFromPomFileWithParent5() throws MojoExecutionException {
        mojo.setPomFile(new File("foo.bar"));
        setMojoModel(mojo.model, "group", "artifact", "version", "packaging", parent);

        mojo.initProperties();

        checkMojoProperties("group", "artifact", "version", "packaging");
    }

    @Test
    void testProcessPomFromPomFileWithParent6() throws MojoExecutionException {
        mojo.setPomFile(new File("foo.bar"));
        setMojoModel(mojo.model, "group", "artifact", "version", "packaging", null);

        mojo.initProperties();

        checkMojoProperties("group", "artifact", "version", "packaging");
    }

    @Test
    void testProcessPomFromPomFileWithOverrides() throws MojoExecutionException {
        mojo.setPomFile(new File("foo.bar"));
        setMojoModel(mojo.model, "group", "artifact", "version", "packaging", null);

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

    private void setMojoModel(
            Model model, String group, String artifact, String version, String packaging, Parent parent) {
        model.setGroupId(group);
        model.setArtifactId(artifact);
        model.setVersion(version);
        model.setPackaging(packaging);
        model.setParent(parent);
    }
}
