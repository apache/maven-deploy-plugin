package org.apache.maven.plugins.deploy;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="jerome@coffeebreaks.org">Jerome Lacoste</a>
 */
public class DeployFileMojoUnitTest
{
    MockDeployFileMojo mojo;
    Parent parent;

    @BeforeEach
    public void setUp()
    {
        Model pomModel = new Model();
        pomModel.setPackaging( null );

        parent = new Parent();
        parent.setGroupId( "parentGroup" );
        parent.setArtifactId( "parentArtifact" );
        parent.setVersion( "parentVersion" );

        mojo = new MockDeployFileMojo( pomModel );
    }

    static class MockDeployFileMojo extends DeployFileMojo {
        private final Model model;

        public MockDeployFileMojo(Model model) {
            this.model = model;
        }

        protected Model readModel(File pomFile) throws MojoException {
            return model;
        }
    }

    @Test
    public void testProcessPomFromPomFileWithParent4()
    {
        mojo.setPomFile( new File( "foo.bar" ) );
        setMojoModel( mojo.model, null, "artifact", "version", "packaging", parent );
        mojo.initProperties();
        checkMojoProperties("parentGroup", "artifact", "version", "packaging");
    }

    @Test
    public void testProcessPomFromPomFileWithParent5()
    {
        mojo.setPomFile( new File( "foo.bar" ) );
        setMojoModel( mojo.model, "group", "artifact", "version", "packaging", parent );
        mojo.initProperties();
        checkMojoProperties("group", "artifact", "version", "packaging");
    }

    @Test
    public void testProcessPomFromPomFileWithParent6()
    {
        mojo.setPomFile( new File( "foo.bar" ) );
        setMojoModel( mojo.model, "group", "artifact", "version", "packaging", null );
        mojo.initProperties();
        checkMojoProperties("group", "artifact", "version", "packaging");
    }

    @Test
    public void testProcessPomFromPomFileWithOverrides()
    {
        mojo.setPomFile( new File( "foo.bar" ) );
        setMojoModel( mojo.model, "group", "artifact", "version", "packaging", null );
        mojo.setGroupId( "groupO" );
        mojo.setArtifactId( "artifactO" );
        mojo.setVersion( "versionO" );
        mojo.setPackaging( "packagingO" );
        mojo.initProperties();
        checkMojoProperties("groupO", "artifactO", "versionO", "packagingO");
    }

    private void checkMojoProperties(final String expectedGroup, final String expectedArtifact, final String expectedVersion, final String expectedPackaging) {
        assertEquals( expectedGroup, mojo.getGroupId() );
        assertEquals( expectedArtifact, mojo.getArtifactId() );
        assertEquals( expectedVersion, mojo.getVersion() );
        assertEquals( expectedPackaging, mojo.getPackaging() );
    }

    private void setMojoModel(Model model, String group, String artifact, String version, String packaging, Parent parent ) {
        model.setGroupId( group );
        model.setArtifactId( artifact );
        model.setVersion( version );
        model.setPackaging( packaging );
        model.setParent( parent );
    }

}
