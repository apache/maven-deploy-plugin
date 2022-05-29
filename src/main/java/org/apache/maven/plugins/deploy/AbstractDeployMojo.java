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

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;

/**
 * Abstract class for Deploy mojo's.
 */
public abstract class AbstractDeployMojo
    extends AbstractMojo
{

    /**
     * Flag whether Maven is currently in online/offline mode.
     */
    @Parameter( defaultValue = "${settings.offline}", readonly = true )
    private boolean offline;

    /**
     * Parameter used to control how many times a failed deployment will be retried before giving up and failing. If a
     * value outside the range 1-10 is specified it will be pulled to the nearest value within the range 1-10.
     * 
     * @since 2.7
     */
    @Parameter( property = "retryFailedDeploymentCount", defaultValue = "1" )
    private int retryFailedDeploymentCount;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private MavenSession session;

    @Component
    private RuntimeInformation runtimeInformation;

    private static final String AFFECTED_MAVEN_PACKAGING = "maven-plugin";

    private static final String FIXED_MAVEN_VERSION = "3.9.0";

    /* Setters and Getters */

    void failIfOffline()
        throws MojoFailureException
    {
        if ( offline )
        {
            throw new MojoFailureException( "Cannot deploy artifacts when Maven is in offline mode" );
        }
    }

    int getRetryFailedDeploymentCount()
    {
        return retryFailedDeploymentCount;
    }

    protected ArtifactRepository createDeploymentArtifactRepository( String id, String url )
    {
        return new MavenArtifactRepository( id, url, new DefaultRepositoryLayout(), new ArtifactRepositoryPolicy(),
                                            new ArtifactRepositoryPolicy() );
    }
    
    protected final MavenSession getSession()
    {
        return session;
    }

    protected void warnIfAffectedPackagingAndMaven( final String packaging )
    {
        if ( AFFECTED_MAVEN_PACKAGING.equals( packaging ) )
        {
            try
            {
                GenericVersionScheme versionScheme = new GenericVersionScheme();
                Version fixedMavenVersion = versionScheme.parseVersion( FIXED_MAVEN_VERSION );
                Version currentMavenVersion = versionScheme.parseVersion( runtimeInformation.getMavenVersion() );
                if ( fixedMavenVersion.compareTo( currentMavenVersion ) > 0 )
                {
                    getLog().warn( "" );
                    getLog().warn( "You are about to deploy a maven-plugin using Maven " + currentMavenVersion + "." );
                    getLog().warn( "This plugin should be used ONLY with Maven 3.9.0 and newer, as MNG-7055" );
                    getLog().warn( "is fixed in those versions of Maven only!" );
                    getLog().warn( "" );
                }
            }
            catch ( InvalidVersionSpecificationException e )
            {
                // skip it: Generic does not throw, only API contains this exception
            }
        }
    }
}
