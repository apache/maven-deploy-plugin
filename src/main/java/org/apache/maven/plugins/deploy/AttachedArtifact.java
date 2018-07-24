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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.utils.StringUtils;

import java.util.List;

/**
 * The attached artifact data
 *
 * @author <a href="mailto:gregory.callea@gmail.com">Gregory Callea</a>
 *
 */
@Mojo( name = "artifact" )
public class AttachedArtifact
{

    /**
     * GroupId of the attached artifact
     */
    @Parameter( property = "groupId" )
    private String groupId;

    /**
     * ArtifactId of the attached artifact
     */
    @Parameter( property = "artifactId" )
    private String artifactId;

    /**
     * Version of the attached artifact
     */
    @Parameter( property = "version" )
    private String version;


    /**
     * Packaging of the attached artifact
     */
    @Parameter( property = "packaging" )
    private String packaging;

    /**
     * Classifier to the attached artifact
     */
    @Parameter( property = "classifier" )
    private String classifier;


    public AttachedArtifact( )
    {

    }

    AttachedArtifact( String groupId, String artifactId, String version, String packaging )
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging;
    }

    /**
     * Validate the attached artifact
     *
     * @throws MojoExecutionException If the attached artifact misses some required parameter
     */
    private void validate() throws MojoExecutionException
    {

        if ( StringUtils.isEmpty( groupId ) || StringUtils.isEmpty( artifactId )
                || StringUtils.isEmpty( version ) || StringUtils.isEmpty( packaging ) )
        {
            throw new MojoExecutionException(
                    "The artifact information is incomplete: 'groupId', 'artifactId', "
                            + "'version', 'packaging' are required" );
        }

    }

    /**
     * Check if attached artifact exists on provided lists
     *
     * @param attachedArtifacts The attached artifact lists
     * @return The found attached artifact
     * @throws MojoExecutionException If no attached artifact that matches the current one is found
     */
    Artifact checkIfExists( final List<Artifact> attachedArtifacts ) throws MojoExecutionException
    {
        this.validate();
        for ( final Artifact attachedArtifact : attachedArtifacts )
        {
            if ( this.groupId.equals( attachedArtifact.getGroupId() )
                    && this.artifactId.equals( attachedArtifact.getArtifactId() )
                    && this.version.equals( attachedArtifact.getVersion() )
                    && this.packaging.equals( attachedArtifact.getType() )
                    && (
                    this.classifier == null && attachedArtifact.getClassifier() == null
                            || ( this.classifier != null
                            && attachedArtifact.getClassifier() != null
                            && this.classifier.equals( attachedArtifact.getClassifier() )
                    )
            ) )
            {
                return attachedArtifact;
            }
        }
        throw new MojoExecutionException(
                "No attached artifact " + this.toString() + " to exclude from deploy found" );
    }

    public String getGroupId()
    {
        return groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public String getPackaging()
    {
        return packaging;
    }

    public String getClassifier()
    {
        return classifier;
    }

    public String getVersion()
    {
        return version;
    }

    public AttachedArtifact setGroupId( String groupId )
    {
        this.groupId = groupId;
        return this;
    }

    public AttachedArtifact setArtifactId( String artifactId )
    {
        this.artifactId = artifactId;
        return this;
    }

    public AttachedArtifact setPackaging( String packaging )
    {
        this.packaging = packaging;
        return this;
    }

    public AttachedArtifact setClassifier( String classifier )
    {
        this.classifier = classifier;
        return this;
    }

    public AttachedArtifact setVersion( String version )
    {
        this.version = version;
        return this;
    }

    @Override
    public String toString()
    {
        return this.groupId + ":" + this.artifactId + ":" + this.packaging
                + ( this.classifier == null ? "" : ":" + this.classifier ) + ":" + this.version;
    }
}