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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * Deploys an artifact to remote repository.
 * 
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 * @author <a href="mailto:jdcasey@apache.org">John Casey (refactoring only)</a>
 */
@Mojo( name = "deploy", defaultPhase = LifecyclePhase.DEPLOY, threadSafe = true )
public class DeployMojo
    extends AbstractDeployMojo
{
    private static final Pattern ALT_LEGACY_REPO_SYNTAX_PATTERN = Pattern.compile( "(.+?)::(.+?)::(.+)" );

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile( "(.+?)::(.+)" );

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    private List<MavenProject> reactorProjects;

    @Parameter( defaultValue = "${plugin}", required = true, readonly = true )
    private PluginDescriptor pluginDescriptor;

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
    private String altDeploymentRepository;

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

    private enum State
    {
        SKIPPED, DEPLOYED, TO_BE_DEPLOYED
    }

    private static final String DEPLOY_PROCESSED_MARKER = DeployMojo.class.getName() + ".processed";

    private static final String DEPLOY_ALT_RELEASE_DEPLOYMENT_REPOSITORY =
        DeployMojo.class.getName() + ".altReleaseDeploymentRepository";

    private static final String DEPLOY_ALT_SNAPSHOT_DEPLOYMENT_REPOSITORY =
        DeployMojo.class.getName() + ".altSnapshotDeploymentRepository";

    private static final String DEPLOY_ALT_DEPLOYMENT_REPOSITORY =
        DeployMojo.class.getName() + ".altDeploymentRepository";

    private void putState( State state )
    {
        getPluginContext().put( DEPLOY_PROCESSED_MARKER, state.name() );
    }

    private void putPluginContextValue( String key, String value )
    {
        if ( value != null )
        {
            getPluginContext().put( key, value );
        }
    }

    private String getPluginContextValue( Map<String, Object> pluginContext, String key )
    {
        return (String) pluginContext.get( key );
    }

    private State getState( Map<String, Object> pluginContext )
    {
        return State.valueOf( getPluginContextValue( pluginContext, DEPLOY_PROCESSED_MARKER ) );
    }

    private boolean hasState( MavenProject project )
    {
        Map<String, Object> pluginContext = session.getPluginContext( pluginDescriptor, project );
        return pluginContext.containsKey( DEPLOY_PROCESSED_MARKER );
    }

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( Boolean.parseBoolean( skip )
            || ( "releases".equals( skip ) && !ArtifactUtils.isSnapshot( project.getVersion() ) )
            || ( "snapshots".equals( skip ) && ArtifactUtils.isSnapshot( project.getVersion() ) )
        )
        {
            getLog().info( "Skipping artifact deployment" );
            putState( State.SKIPPED );
        }
        else
        {
            failIfOffline();
            warnIfAffectedPackagingAndMaven( project.getPackaging() );

            if ( !deployAtEnd )
            {
                deploy( session.getRepositorySession(),
                        processProject( project,
                        altSnapshotDeploymentRepository, altReleaseDeploymentRepository, altDeploymentRepository ) );
                putState( State.DEPLOYED );
            }
            else
            {
                putPluginContextValue( DEPLOY_ALT_RELEASE_DEPLOYMENT_REPOSITORY, altReleaseDeploymentRepository );
                putPluginContextValue( DEPLOY_ALT_SNAPSHOT_DEPLOYMENT_REPOSITORY, altSnapshotDeploymentRepository );
                putPluginContextValue( DEPLOY_ALT_DEPLOYMENT_REPOSITORY, altDeploymentRepository );
                putState( State.TO_BE_DEPLOYED );
                getLog().info( "Deferring deploy for " + project.getGroupId()
                        + ":" + project.getArtifactId() + ":" + project.getVersion() + " at end" );
            }
        }

        if ( allProjectsMarked() )
        {
            for ( MavenProject reactorProject : reactorProjects )
            {
                Map<String, Object> pluginContext = session.getPluginContext( pluginDescriptor, reactorProject );
                State state = getState( pluginContext );
                if ( state == State.TO_BE_DEPLOYED )
                {
                    String altReleaseDeploymentRepository =
                        getPluginContextValue( pluginContext, DEPLOY_ALT_RELEASE_DEPLOYMENT_REPOSITORY );
                    String altSnapshotDeploymentRepository =
                        getPluginContextValue( pluginContext, DEPLOY_ALT_SNAPSHOT_DEPLOYMENT_REPOSITORY );
                    String altDeploymentRepository =
                        getPluginContextValue( pluginContext, DEPLOY_ALT_DEPLOYMENT_REPOSITORY );

                    deploy( session.getRepositorySession(),
                            processProject( reactorProject,
                            altSnapshotDeploymentRepository, altReleaseDeploymentRepository, altDeploymentRepository )
                    );
                }
            }
        }
    }

    private boolean allProjectsMarked()
    {
        for ( MavenProject reactorProject : reactorProjects )
        {
            if ( !hasState( reactorProject ) )
            {
                return false;
            }
        }
        return true;
    }

    private DeployRequest processProject( final MavenProject project,
                                          final String altSnapshotDeploymentRepository,
                                          final String altReleaseDeploymentRepository,
                                          final String altDeploymentRepository )
            throws MojoExecutionException, MojoFailureException
    {
        DeployRequest request = new DeployRequest();
        request.setRepository( getDeploymentRepository( project,
                altSnapshotDeploymentRepository, altReleaseDeploymentRepository, altDeploymentRepository ) );

        org.apache.maven.artifact.Artifact mavenMainArtifact = project.getArtifact();
        String packaging = project.getPackaging();
        File pomFile = project.getFile();
        boolean isPomArtifact = "pom".equals( packaging );
        boolean pomArtifactAttached = false;

        if ( pomFile != null )
        {
            request.addArtifact( RepositoryUtils.toArtifact( new ProjectArtifact( project ) ) );
            pomArtifactAttached = true;
        }

        if ( !isPomArtifact )
        {
            File file = mavenMainArtifact.getFile();
            if ( file != null && file.isFile() )
            {
                org.eclipse.aether.artifact.Artifact mainArtifact = RepositoryUtils.toArtifact( mavenMainArtifact );
                request.addArtifact( mainArtifact );

                if ( !pomArtifactAttached )
                {
                    for ( Object metadata : mavenMainArtifact.getMetadataList() )
                    {
                        if ( metadata instanceof ProjectArtifactMetadata )
                        {
                            request.addArtifact( new SubArtifact(
                                    mainArtifact,
                                    "",
                                    "pom"
                            ).setFile( ( (ProjectArtifactMetadata) metadata ).getFile() ) );
                            pomArtifactAttached = true;
                        }
                    }
                }
            }
            else if ( !project.getAttachedArtifacts().isEmpty() )
            {
                throw new MojoExecutionException( "The packaging plugin for this project did not assign "
                        + "a main file to the project but it has attachments. Change packaging to 'pom'." );
            }
            else
            {
                throw new MojoExecutionException( "The packaging for this project did not assign "
                        + "a file to the build artifact" );
            }
        }

        if ( !pomArtifactAttached )
        {
            throw new MojoExecutionException( "The POM could not be attached" );
        }

        for ( org.apache.maven.artifact.Artifact attached : project.getAttachedArtifacts() )
        {
            getLog().debug( "Attaching for install: " + attached.getId() );
            request.addArtifact( RepositoryUtils.toArtifact( attached ) );
        }

        return request;
    }

    /**
     * Visible for testing.
     */
    RemoteRepository getDeploymentRepository( final MavenProject project,
                                              final String altSnapshotDeploymentRepository,
                                              final String altReleaseDeploymentRepository,
                                              final String altDeploymentRepository )

        throws MojoExecutionException, MojoFailureException
    {
        RemoteRepository repo = null;

        String altDeploymentRepo;
        if ( ArtifactUtils.isSnapshot( project.getVersion() ) && altSnapshotDeploymentRepository != null )
        {
            altDeploymentRepo = altSnapshotDeploymentRepository;
        }
        else if ( !ArtifactUtils.isSnapshot( project.getVersion() ) && altReleaseDeploymentRepository != null )
        {
            altDeploymentRepo = altReleaseDeploymentRepository;
        }
        else
        {
            altDeploymentRepo = altDeploymentRepository;
        }

        if ( altDeploymentRepo != null )
        {
            getLog().info( "Using alternate deployment repository " + altDeploymentRepo );

            Matcher matcher = ALT_LEGACY_REPO_SYNTAX_PATTERN.matcher( altDeploymentRepo );

            if ( matcher.matches() )
            {
                String id = matcher.group( 1 ).trim();
                String layout = matcher.group( 2 ).trim();
                String url = matcher.group( 3 ).trim();

                if ( "default".equals( layout ) )
                {
                    getLog().warn( "Using legacy syntax for alternative repository. "
                            + "Use \"" + id + "::" + url + "\" instead." );
                    repo = getRemoteRepository( id, url );
                }
                else
                {
                    throw new MojoFailureException( altDeploymentRepo,
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
                    throw new MojoFailureException( altDeploymentRepo,
                            "Invalid syntax for repository.",
                            "Invalid syntax for alternative repository. Use \"id::url\"."
                    );
                }
                else
                {
                    String id = matcher.group( 1 ).trim();
                    String url = matcher.group( 2 ).trim();

                    repo = getRemoteRepository( id, url );
                }
            }
        }

        if ( repo == null )
        {
            repo = RepositoryUtils.toRepo( project.getDistributionManagementArtifactRepository() );
        }

        if ( repo == null )
        {
            String msg = "Deployment failed: repository element was not specified in the POM inside"
                + " distributionManagement element or in -DaltDeploymentRepository=id::url parameter";

            throw new MojoExecutionException( msg );
        }

        return repo;
    }

}
