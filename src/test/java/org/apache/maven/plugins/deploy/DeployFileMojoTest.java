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

import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.codehaus.plexus.testing.PlexusExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.maven.api.Artifact;
import org.apache.maven.api.Session;
import org.apache.maven.api.services.ArtifactDeployer;
import org.apache.maven.api.services.ArtifactDeployerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectBuilder;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.model.Model;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugins.deploy.stubs.ArtifactStub;
import org.apache.maven.plugins.deploy.stubs.SessionStub;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest
public class DeployFileMojoTest
{
    private static final String LOCAL_REPO = getBasedir() + "/target/local-repo";
    
    @Inject @SuppressWarnings( "unused" )
    private Session session;

    @Inject @SuppressWarnings( "unused" )
    private ArtifactDeployer artifactDeployer;

    @Inject @SuppressWarnings( "unused" )
    private ArtifactManager artifactManager;
    
    @Test
    @InjectMojo( goal = "deploy-file", pom = "classpath:/unit/deploy-file/plugin-config-test.xml" )
    public void testDeployTestEnvironment( DeployFileMojo mojo )
    {
        assertNotNull( mojo );
    }

    @Test
    @InjectMojo( goal = "deploy-file", pom = "classpath:/unit/deploy-file/plugin-config-test.xml" )
    public void testBasicDeployFile( DeployFileMojo mojo )
        throws Exception
    {
        assertNotNull( mojo );
        
        String groupId = (String) getVariableValueFromObject( mojo, "groupId" );
        String artifactId = (String) getVariableValueFromObject( mojo, "artifactId" );
        String version = (String) getVariableValueFromObject( mojo, "version" );
        String packaging = (String) getVariableValueFromObject( mojo, "packaging" );
        File file = (File) getVariableValueFromObject( mojo, "file" );
        String repositoryId = (String) getVariableValueFromObject( mojo, "repositoryId" );
        String url = (String) getVariableValueFromObject( mojo, "url" );

        assertEquals( "org.apache.maven.test", groupId );
        assertEquals( "maven-deploy-file-test", artifactId );
        assertEquals( "1.0", version );
        assertEquals( "jar", packaging );
        assertTrue( file.exists() );
        assertEquals( "deploy-test", repositoryId );
        assertEquals( "file://" + getBasedir() + "/target/remote-repo/deploy-file-test", url );

        ArtifactDeployerRequest request = execute( mojo );

        assertNotNull( request );
        List<Artifact> artifacts = new ArrayList<>( request.getArtifacts() );
        assertEquals( 2, artifacts.size() );
        Artifact a1 = artifacts.get( 0 );
        Path p1 = artifactManager.getPath( a1 ).orElse( null );
        assertEquals( file.toPath(), p1 );
        Artifact a2 = artifacts.get( 1 );
        Path p2 = artifactManager.getPath( a2 ).orElse( null );
        assertNotNull( p2 );
        assertTrue( p2.toString().endsWith( ".pom" ) );

        assertNotNull( request.getRepository() );
        assertEquals( url, request.getRepository().getUrl() );

        //check the generated pom
        File pom = p2.toFile();
        assertTrue( pom.exists() );

        Model model = mojo.readModel( pom );
        assertEquals( "4.0.0", model.getModelVersion() );
        assertEquals( groupId, model.getGroupId() );
        assertEquals( artifactId, model.getArtifactId() );
        assertEquals( version, model.getVersion() );
        assertEquals( packaging, model.getPackaging() );
        assertEquals( "POM was created from deploy:deploy-file", model.getDescription() );
    }

    @Test
    @InjectMojo( goal = "deploy-file", pom = "classpath:/unit/deploy-file/plugin-config-classifier.xml" )
    public void testDeployIfClassifierIsSet( DeployFileMojo mojo )
        throws Exception
    {
        assertNotNull( mojo );
        
        String groupId = ( String ) getVariableValueFromObject( mojo, "groupId" );
        String artifactId = ( String ) getVariableValueFromObject( mojo, "artifactId" );
        String classifier = ( String ) getVariableValueFromObject( mojo, "classifier" );
        assertEquals( "bin", classifier );
        String version = ( String ) getVariableValueFromObject( mojo, "version" );
        String url = (String) getVariableValueFromObject( mojo, "url" );

        ArtifactDeployerRequest request = execute( mojo );

        assertNotNull( request );
        List<Artifact> artifacts = new ArrayList<>( request.getArtifacts() );
        assertEquals( 2, artifacts.size() );
        // first artifact
        Artifact a1 = artifacts.get( 0 );
        assertEquals( new ArtifactStub( groupId, artifactId, "", version, "pom" ), a1 );
        Path p1 = artifactManager.getPath( a1 ).orElse( null );
        assertNotNull( p1 );
        assertTrue( p1.toString().endsWith( ".pom" ) );
        // second artifact
        Artifact a2 = artifacts.get( 1 );
        assertEquals( new ArtifactStub( groupId, artifactId, "bin", version, "jar" ), a2 );
        Path p2 = artifactManager.getPath( a2 ).orElse( null );
        assertNotNull( p2 );
        assertTrue( p2.toString().endsWith( "deploy-test-file-1.0-SNAPSHOT.jar" ) );
        // remote repository
        assertNotNull( request.getRepository() );
        assertEquals( url, request.getRepository().getUrl() );
    }

    @Test
    @InjectMojo( goal = "deploy-file", pom = "classpath:/unit/deploy-file/plugin-config-artifact-not-jar.xml" )
    public void testDeployIfArtifactIsNotJar( DeployFileMojo mojo )
        throws Exception
    {
        assertNotNull( mojo );

        String groupId = (String) getVariableValueFromObject( mojo, "groupId" );
        String artifactId = (String) getVariableValueFromObject( mojo, "artifactId" );
        String version = (String) getVariableValueFromObject( mojo, "version" );
        assertEquals( "org.apache.maven.test", groupId );
        assertEquals( "maven-deploy-file-test", artifactId );
        assertEquals( "1.0", version );

        ArtifactDeployerRequest request = execute( mojo );

        assertNotNull( request );
        List<Artifact> artifacts = new ArrayList<>( request.getArtifacts() );
        assertEquals( 2, artifacts.size() );
        Artifact a1 = artifacts.get( 0 );
        Artifact a2 = artifacts.get( 1 );
        Path p1 = artifactManager.getPath( a1 ).orElse( null );
        Path p2 = artifactManager.getPath( a2 ).orElse( null );
        assertNotNull( p1 );
        assertTrue( p1.toString().endsWith( "deploy-test-file.zip" ) );
        assertNotNull( p2 );
        assertTrue( p2.toString().endsWith( ".pom" ) );

        assertNotNull( request.getRepository() );
        assertEquals( "file://" + getBasedir() + "/target/remote-repo/deploy-file", request.getRepository().getUrl() );
    }

    private ArtifactDeployerRequest execute( DeployFileMojo mojo )
    {
        ArgumentCaptor<ArtifactDeployerRequest> requestCaptor = ArgumentCaptor.forClass( ArtifactDeployerRequest.class );
        doNothing().when( artifactDeployer ).deploy( requestCaptor.capture() );

        mojo.execute();

        return requestCaptor.getValue();
    }

    @Provides @Singleton @SuppressWarnings( "unused" )
    private Session getMockSession()
    {
        return SessionStub.getMockSession( LOCAL_REPO );
    }

    @Provides @SuppressWarnings( "unused" )
    private ArtifactDeployer getMockArtifactDeployer( Session session )
    {
        return session.getService( ArtifactDeployer.class );
    }

    @Provides @SuppressWarnings( "unused" )
    private ArtifactManager getMockArtifactManager( Session session )
    {
        return session.getService( ArtifactManager.class );
    }

    @Provides @SuppressWarnings( "unused" )
    private ProjectManager getMockProjectManager( Session session )
    {
        return session.getService( ProjectManager.class );
    }

    @Provides @SuppressWarnings( "unused" )
    private ProjectBuilder getMockProjectBuilder( Session session )
    {
        return session.getService( ProjectBuilder.class );
    }

}

