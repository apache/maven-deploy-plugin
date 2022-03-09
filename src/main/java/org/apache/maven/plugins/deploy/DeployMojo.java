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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.LifecyclePhase;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactDeployer;
import org.apache.maven.api.services.ArtifactDeployerException;
import org.apache.maven.api.services.ArtifactDeployerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.api.services.RepositoryFactory;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.shared.utils.StringUtils;

/**
 * Deploys an artifact to remote repository.
 * 
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 * @author <a href="mailto:jdcasey@apache.org">John Casey (refactoring only)</a>
 */
@Mojo( name = "deploy", defaultPhase = LifecyclePhase.DEPLOY )
public class DeployMojo
    extends AbstractDeployMojo
{
    private static final Pattern ALT_LEGACY_REPO_SYNTAX_PATTERN = Pattern.compile( "(.+?)::(.+?)::(.+)" );

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile( "(.+?)::(.+)" );

    /**
     * When building with multiple threads, reaching the last project doesn't have to mean that all projects are ready
     * to be deployed
     */
    private static final AtomicInteger READYPROJECTSCOUNTER = new AtomicInteger();

    private static final List<ArtifactDeployerRequest> DEPLOYREQUESTS =
        Collections.synchronizedList( new ArrayList<>() );

    /**
     */
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private Project project;

    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    private List<Project> reactorProjects;

    /**
     * Whether every project should be deployed during its own deploy-phase or at the end of the multimodule build. If
     * set to {@code true} and the build fails, none of the reactor projects is deployed.
     * <strong>(experimental)</strong>
     * 
     * @since 2.8
     */
    @Parameter( defaultValue = "false", property = "deployAtEnd" )
    private boolean deployAtEnd;

    /**
     * Specifies an alternative repository to which the project artifacts should be deployed (other than those specified
     * in &lt;distributionManagement&gt;). <br/>
     * Format: <code>id::url</code>
     * <dl>
     * <dt>id</dt>
     * <dd>The id can be used to pick up the correct credentials from the settings.xml</dd>
     * <dt>url</dt>
     * <dd>The location of the repository</dd>
     * </dl>
     * <b>Note:</b> In version 2.x, the format was <code>id::<i>layout</i>::url</code> where <code><i>layout</i></code>
     * could be <code>default</code> (ie. Maven 2) or <code>legacy</code> (ie. Maven 1), but since 3.0.0 the layout part
     * has been removed because Maven 3 only supports Maven 2 repository layout.
     */
    @Parameter( property = "altDeploymentRepository" )
    String altDeploymentRepository;

    /**
     * The alternative repository to use when the project has a snapshot version.
     *
     * <b>Note:</b> In version 2.x, the format was <code>id::<i>layout</i>::url</code> where <code><i>layout</i></code>
     * could be <code>default</code> (ie. Maven 2) or <code>legacy</code> (ie. Maven 1), but since 3.0.0 the layout part
     * has been removed because Maven 3 only supports Maven 2 repository layout.
     * @since 2.8
     * @see DeployMojo#altDeploymentRepository
     */
    @Parameter( property = "altSnapshotDeploymentRepository" )
    private String altSnapshotDeploymentRepository;

    /**
     * The alternative repository to use when the project has a final version.
     *
     * <b>Note:</b> In version 2.x, the format was <code>id::<i>layout</i>::url</code> where <code><i>layout</i></code>
     * could be <code>default</code> (ie. Maven 2) or <code>legacy</code> (ie. Maven 1), but since 3.0.0 the layout part
     * has been removed because Maven 3 only supports Maven 2 repository layout.
     * @since 2.8
     * @see DeployMojo#altDeploymentRepository
     */
    @Parameter( property = "altReleaseDeploymentRepository" )
    private String altReleaseDeploymentRepository;

    /**
     * Set this to 'true' to bypass artifact deploy
     * Since since 3.0.0-M2 it's not anymore a real boolean as it can have more than 2 values:
     * <ul>
     *     <li><code>true</code>: will skip as usual</li>
     *     <li><code>releases</code>: will skip if current version of the project is a release</li>
     *     <li><code>snapshots</code>: will skip if current version of the project is a snapshot</li>
     *     <li>any other values will be considered as <code>false</code></li>
     * </ul>
     * @since 2.4
     */
    @Parameter( property = "maven.deploy.skip", defaultValue = "false" )
    private String skip = Boolean.FALSE.toString();

    public void execute()
        throws MojoException
    {
        ArtifactManager artifactManager = getSession().getService( ArtifactManager.class );

        boolean addedDeployRequest = false;
        boolean isSnapshot = artifactManager.isSnapshot( project.getVersion() );
        if ( Boolean.parseBoolean( skip )
            || ( "releases".equals( skip ) && !isSnapshot )
            || ( "snapshots".equals( skip ) && isSnapshot )
        )
        {
            logger.info( "Skipping artifact deployment" );
        }
        else
        {
            failIfOffline();

            List<Artifact> deployables = new ArrayList<>();

            deployables.add( project.getArtifact() );

            Path pomPath = project.getPomPath();

            if ( !"pom".equals( project.getPackaging() ) )
            {
                Artifact pomArtifact = getSession().createArtifact(
                        project.getGroupId(), project.getArtifactId(), "",
                        project.getVersion(), "pom" );
                if ( pomPath != null )
                {
                    artifactManager.setPath( pomArtifact, pomPath );
                }
                deployables.add( pomArtifact );
            }
            else
            {
                if ( pomPath != null )
                {
                    artifactManager.setPath( project.getArtifact(), pomPath );
                }
            }

            ProjectManager projectManager = getSession().getService( ProjectManager.class );
            deployables.addAll( projectManager.getAttachedArtifacts( project ) );

            for ( Artifact artifact : deployables )
            {
                Path path = artifactManager.getPath( artifact ).orElse( null );
                if ( path == null || !Files.isRegularFile( path ) )
                {
                    throw new MojoException( "The packaging plugin for this project did not assign "
                            + "a main file to the project but it has attachments. Change packaging to 'pom'." );
                }
            }

            // CHECKSTYLE_OFF: LineLength
            // @formatter:off
            ArtifactDeployerRequest adr = ArtifactDeployerRequest.builder()
                .session( getSession() )
                .repository( getDeploymentRepository( isSnapshot ) )
                .artifacts( deployables )
                .retryFailedDeploymentCount( getRetryFailedDeploymentCount() )
                .build();
            // @formatter:on
            // CHECKSTYLE_ON: LineLength

            if ( !deployAtEnd )
            {
                deployProject( adr );
            }
            else
            {
                DEPLOYREQUESTS.add( adr );
                addedDeployRequest = true;
            }
        }

        boolean projectsReady = READYPROJECTSCOUNTER.incrementAndGet() == reactorProjects.size();
        if ( projectsReady )
        {
            synchronized ( DEPLOYREQUESTS )
            {
                while ( !DEPLOYREQUESTS.isEmpty() )
                {
                    deployProject( DEPLOYREQUESTS.remove( 0 ) );
                }
            }
        }
        else if ( addedDeployRequest )
        {
            logger.info( "Deploying " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                + project.getVersion() + " at end" );
        }
    }

    private void deployProject( ArtifactDeployerRequest adr )
        throws MojoException
    {
        try
        {
            ArtifactDeployer artifactDeployer = getSession().getService( ArtifactDeployer.class );
            artifactDeployer.deploy( adr );
        }
        catch ( ArtifactDeployerException e )
        {
            throw new MojoException( "ProjectDeployerException", e );
        }

    }

    RemoteRepository getDeploymentRepository( boolean isSnapshot )
        throws MojoException
    {
        RemoteRepository repo = null;

        String altDeploymentRepo;
        if ( isSnapshot && altSnapshotDeploymentRepository != null )
        {
            altDeploymentRepo = altSnapshotDeploymentRepository;
        }
        else if ( !isSnapshot && altReleaseDeploymentRepository != null )
        {
            altDeploymentRepo = altReleaseDeploymentRepository;
        }
        else
        {
            altDeploymentRepo = altDeploymentRepository;
        }

        if ( altDeploymentRepo != null )
        {
            logger.info( "Using alternate deployment repository " + altDeploymentRepo );

            Matcher matcher = ALT_LEGACY_REPO_SYNTAX_PATTERN.matcher( altDeploymentRepo );

            if ( matcher.matches() )
            {
                String id = matcher.group( 1 ).trim();
                String layout = matcher.group( 2 ).trim();
                String url = matcher.group( 3 ).trim();

                if ( "default".equals( layout ) )
                {
                    logger.warn( "Using legacy syntax for alternative repository. "
                            + "Use \"" + id + "::" + url + "\" instead." );
                    repo = createDeploymentArtifactRepository( id, url );
                }
                else
                {
                    throw new MojoException( altDeploymentRepo,
                            "Invalid legacy syntax and layout for repository.",
                            "Invalid legacy syntax and layout for alternative repository. Use \""
                                    + id + "::" + url + "\" instead, and only default layout is supported."
                    );
                }
            }
            else
            {
                matcher = ALT_REPO_SYNTAX_PATTERN.matcher( altDeploymentRepo );

                if ( !matcher.matches() )
                {
                    throw new MojoException( altDeploymentRepo,
                            "Invalid syntax for repository.",
                            "Invalid syntax for alternative repository. Use \"id::url\"."
                    );
                }
                else
                {
                    String id = matcher.group( 1 ).trim();
                    String url = matcher.group( 2 ).trim();

                    repo = createDeploymentArtifactRepository( id, url );
                }
            }
        }

        if ( repo == null )
        {
            DistributionManagement dm = project.getModel().getDistributionManagement();
            if ( dm != null )
            {
                boolean snapshot = isSnapshot;
                if ( snapshot && dm.getSnapshotRepository() != null
                        && StringUtils.isNotEmpty(  dm.getSnapshotRepository().getId() )
                        && StringUtils.isNotEmpty( dm.getSnapshotRepository().getUrl() ) )
                {
                    repo = getSession().getService( RepositoryFactory.class )
                            .createRemote( dm.getSnapshotRepository() );
                }
                else if ( dm.getRepository() != null
                        && StringUtils.isNotEmpty(  dm.getRepository().getId() )
                        && StringUtils.isNotEmpty( dm.getRepository().getUrl() )  )
                {
                    repo = getSession().getService( RepositoryFactory.class )
                            .createRemote( dm.getRepository() );
                }
            }
        }

        if ( repo == null )
        {
            String msg = "Deployment failed: repository element was not specified in the POM inside"
                + " distributionManagement element or in -DaltDeploymentRepository=id::url parameter";

            throw new MojoException( msg );
        }

        return repo;
    }

}
