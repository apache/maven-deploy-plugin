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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactDeployer;
import org.apache.maven.api.services.ArtifactDeployerException;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectBuilder;
import org.apache.maven.api.services.ProjectBuilderException;
import org.apache.maven.api.services.ProjectBuilderRequest;
import org.apache.maven.api.services.ProjectBuilderResult;
import org.apache.maven.api.services.ProjectBuilderSource;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Installs the artifact in the remote repository.
 * 
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@Mojo( name = "deploy-file", requiresProject = false )
public class DeployFileMojo
    extends AbstractDeployMojo
{
    /**
     * GroupId of the artifact to be deployed. Retrieved from POM file if specified.
     */
    @Parameter( property = "groupId" )
    private String groupId;

    /**
     * ArtifactId of the artifact to be deployed. Retrieved from POM file if specified.
     */
    @Parameter( property = "artifactId" )
    private String artifactId;

    /**
     * Version of the artifact to be deployed. Retrieved from POM file if specified.
     */
    @Parameter( property = "version" )
    private String version;

    /**
     * Type of the artifact to be deployed. Retrieved from the &lt;packaging&gt element of the POM file if a POM file
     * specified. Defaults to the file extension if it is not specified via command line or POM.<br/>
     * Maven uses two terms to refer to this datum: the &lt;packaging&gt; element for the entire POM, and the
     * &lt;type&gt; element in a dependency specification.
     */
    @Parameter( property = "packaging" )
    private String packaging;

    /**
     * Description passed to a generated POM file (in case of generatePom=true)
     */
    @Parameter( property = "generatePom.description" )
    private String description;

    /**
     * File to be deployed.
     */
    @Parameter( property = "file", required = true )
    private File file;

    /**
     * The bundled API docs for the artifact.
     * 
     * @since 2.6
     */
    @Parameter( property = "javadoc" )
    private File javadoc;

    /**
     * The bundled sources for the artifact.
     * 
     * @since 2.6
     */
    @Parameter( property = "sources" )
    private File sources;

    /**
     * Server Id to map on the &lt;id&gt; under &lt;server&gt; section of settings.xml In most cases, this parameter
     * will be required for authentication.
     */
    @Parameter( property = "repositoryId", defaultValue = "remote-repository", required = true )
    private String repositoryId;

    /**
     * URL where the artifact will be deployed. <br/>
     * ie ( file:///C:/m2-repo or scp://host.com/path/to/repo )
     */
    @Parameter( property = "url", required = true )
    private String url;

    /**
     * Location of an existing POM file to be deployed alongside the main artifact, given by the ${file} parameter.
     */
    @Parameter( property = "pomFile" )
    private File pomFile;

    /**
     * Upload a POM for this artifact. Will generate a default POM if none is supplied with the pomFile argument.
     */
    @Parameter( property = "generatePom", defaultValue = "true" )
    private boolean generatePom;

    /**
     * Add classifier to the artifact
     */
    @Parameter( property = "classifier" )
    private String classifier;

    /**
     * Whether to deploy snapshots with a unique version or not.
     * 
     * @deprecated As of Maven 3, this isn't supported anymore and this parameter is only present to break the build if
     *             you use it!
     */
    @Parameter( property = "uniqueVersion" )
    @Deprecated
    private Boolean uniqueVersion;

    /**
     * A comma separated list of types for each of the extra side artifacts to deploy. If there is a mis-match in the
     * number of entries in {@link #files} or {@link #classifiers}, then an error will be raised.
     */
    @Parameter( property = "types" )
    private String types;

    /**
     * A comma separated list of classifiers for each of the extra side artifacts to deploy. If there is a mis-match in
     * the number of entries in {@link #files} or {@link #types}, then an error will be raised.
     */
    @Parameter( property = "classifiers" )
    private String classifiers;

    /**
     * A comma separated list of files for each of the extra side artifacts to deploy. If there is a mis-match in the
     * number of entries in {@link #types} or {@link #classifiers}, then an error will be raised.
     */
    @Parameter( property = "files" )
    private String files;

    void initProperties()
        throws MojoException
    {
        if ( pomFile == null )
        {
            boolean foundPom = false;

            JarFile jarFile = null;
            try
            {
                Pattern pomEntry = Pattern.compile( "META-INF/maven/.*/pom\\.xml" );

                jarFile = new JarFile( file );

                Enumeration<JarEntry> jarEntries = jarFile.entries();

                while ( jarEntries.hasMoreElements() )
                {
                    JarEntry entry = jarEntries.nextElement();

                    if ( pomEntry.matcher( entry.getName() ).matches() )
                    {
                        logger.debug( "Using " + entry.getName() + " as pomFile" );

                        foundPom = true;

                        InputStream pomInputStream = null;
                        OutputStream pomOutputStream = null;

                        try
                        {
                            pomInputStream = jarFile.getInputStream( entry );

                            String base = file.getName();
                            if ( base.indexOf( '.' ) > 0 )
                            {
                                base = base.substring( 0, base.lastIndexOf( '.' ) );
                            }
                            pomFile = new File( file.getParentFile(), base + ".pom" );

                            pomOutputStream = new FileOutputStream( pomFile );

                            IOUtil.copy( pomInputStream, pomOutputStream );

                            pomOutputStream.close();
                            pomOutputStream = null;
                            pomInputStream.close();
                            pomInputStream = null;

                            processModel( readModel( pomFile ) );

                            break;
                        }
                        finally
                        {
                            IOUtil.close( pomInputStream );
                            IOUtil.close( pomOutputStream );
                        }
                    }
                }

                if ( !foundPom )
                {
                    logger.info( "pom.xml not found in " + file.getName() );
                }
            }
            catch ( IOException e )
            {
                // ignore, artifact not packaged by Maven
            }
            finally
            {
                if ( jarFile != null )
                {
                    try
                    {
                        jarFile.close();
                    }
                    catch ( IOException e )
                    {
                        // we did our best
                    }
                }
            }
        }
        else
        {
            processModel( readModel( pomFile ) );
        }

        if ( packaging == null && file != null )
        {
            packaging = FileUtils.getExtension( file.getName() );
        }
    }

    public void execute()
        throws MojoException
    {
        if ( uniqueVersion != null )
        {
            throw new MojoException( "You are using 'uniqueVersion' which has been removed"
                + " from the maven-deploy-plugin. "
                + "Please see the >>Major Version Upgrade to version 3.0.0<< on the plugin site." );
        }

        failIfOffline();

        if ( !file.exists() )
        {
            throw new MojoException( file.getPath() + " not found." );
        }

        initProperties();

        RemoteRepository deploymentRepository = createDeploymentArtifactRepository( repositoryId, url );

        String protocol = deploymentRepository.getProtocol();

        if ( StringUtils.isEmpty( protocol ) )
        {
            throw new MojoException( "No transfer protocol found." );
        }

        ArtifactManager artifactManager = getSession().getService( ArtifactManager.class );
        ProjectManager projectManager = getSession().getService( ProjectManager.class );
        ArtifactDeployer artifactDeployer = getSession().getService( ArtifactDeployer.class );

        Project project = createMavenProject();
        Artifact artifact = project.getArtifact();

        if ( file.equals( getLocalRepoFile().toFile() ) )
        {
            throw new MojoException( "Cannot deploy artifact from the local repository: " + file );
        }

        List<Artifact> deployableArtifacts = new ArrayList<>();

        if ( classifier == null )
        {
            artifactManager.setPath( artifact, file.toPath() );
            deployableArtifacts.add( artifact );
        }
        else
        {
            projectManager.attachArtifact( getSession(), project, packaging, classifier, file.toPath() );
        }

        // Upload the POM if requested, generating one if need be
        if ( !"pom".equals( packaging ) )
        {
            File pom = pomFile;
            if ( pom == null && generatePom )
            {
                pom = generatePomFile();
            }
            if ( pom != null )
            {
                if ( classifier == null )
                {
                    Artifact pomArtifact = getSession().createArtifact(
                            groupId, artifactId, "", version, "pom"
                    );
                    artifactManager.setPath( pomArtifact, pom.toPath() );
                    deployableArtifacts.add( pomArtifact );
                }
                else
                {
                    artifactManager.setPath( artifact, pom.toPath() );
                    deployableArtifacts.add( artifact );
                }
            }
        }

        if ( sources != null )
        {
            projectManager.attachArtifact( getSession(), project, "jar", "sources", sources.toPath() );
        }

        if ( javadoc != null )
        {
            projectManager.attachArtifact( getSession(), project, "jar", "javadoc", javadoc.toPath() );
        }

        if ( files != null )
        {
            if ( types == null )
            {
                throw new MojoException( "You must specify 'types' if you specify 'files'" );
            }
            if ( classifiers == null )
            {
                throw new MojoException( "You must specify 'classifiers' if you specify 'files'" );
            }
            int filesLength = StringUtils.countMatches( files, "," );
            int typesLength = StringUtils.countMatches( types, "," );
            int classifiersLength = StringUtils.countMatches( classifiers, "," );
            if ( typesLength != filesLength )
            {
                throw new MojoException( "You must specify the same number of entries in 'files' and "
                    + "'types' (respectively " + filesLength + " and " + typesLength + " entries )" );
            }
            if ( classifiersLength != filesLength )
            {
                throw new MojoException( "You must specify the same number of entries in 'files' and "
                    + "'classifiers' (respectively " + filesLength + " and " + classifiersLength + " entries )" );
            }
            int fi = 0;
            int ti = 0;
            int ci = 0;
            for ( int i = 0; i <= filesLength; i++ )
            {
                int nfi = files.indexOf( ',', fi );
                if ( nfi == -1 )
                {
                    nfi = files.length();
                }
                int nti = types.indexOf( ',', ti );
                if ( nti == -1 )
                {
                    nti = types.length();
                }
                int nci = classifiers.indexOf( ',', ci );
                if ( nci == -1 )
                {
                    nci = classifiers.length();
                }
                File file = new File( files.substring( fi, nfi ) );
                if ( !file.isFile() )
                {
                    // try relative to the project basedir just in case
                    file = new File( project.getPomPath().getParent().toFile(), files.substring( fi, nfi ) );
                }
                if ( file.isFile() )
                {
                    String classifier = classifiers.substring( ci, nci ).trim();
                    String type = types.substring( ti, nti ).trim();
                    projectManager.attachArtifact( getSession(), project, type, classifier, file.toPath() );
                }
                else
                {
                    throw new MojoException( "Specified side artifact " + file + " does not exist" );
                }
                fi = nfi + 1;
                ti = nti + 1;
                ci = nci + 1;
            }
        }
        else
        {
            if ( types != null )
            {
                throw new MojoException( "You must specify 'files' if you specify 'types'" );
            }
            if ( classifiers != null )
            {
                throw new MojoException( "You must specify 'files' if you specify 'classifiers'" );
            }
        }

        Collection<Artifact> attachedArtifacts = projectManager.getAttachedArtifacts( project );

        deployableArtifacts.addAll( attachedArtifacts );

        try
        {
            artifactDeployer.deploy( getSession(), deploymentRepository, deployableArtifacts );
        }
        catch ( ArtifactDeployerException e )
        {
            throw new MojoException( e.getMessage(), e );
        }
    }

    /**
     * Creates a Maven project in-memory from the user-supplied groupId, artifactId and version. When a classifier is
     * supplied, the packaging must be POM because the project with only have attachments. This project serves as basis
     * to attach the artifacts to deploy to.
     * 
     * @return The created Maven project, never <code>null</code>.
     * @throws MojoException When the model of the project could not be built.
     */
    private Project createMavenProject()
        throws MojoException
    {
        if ( groupId == null || artifactId == null || version == null || packaging == null )
        {
            throw new MojoException( "The artifact information is incomplete: 'groupId', 'artifactId', "
                + "'version' and 'packaging' are required." );
        }
        try
        {
            String prj = "<project>"
                    + "<modelVersion>4.0.0</modelVersion>"
                    + "<groupId>" + groupId + "</groupId>"
                    + "<artifactId>" + artifactId + "</artifactId>"
                    + "<version>" + version + "</version>"
                    + "<packaging>" + ( classifier == null ? packaging : "pom" ) + "</packaging>"
                    + "</project>";
            ProjectBuilderResult result = getSession().getService( ProjectBuilder.class )
                    .build( ProjectBuilderRequest.builder()
                            .session( getSession() )
                            .source( new StringSource( prj ) )
                            .processPlugins( false )
                            .resolveDependencies( false )
                            .build() );

            return result.getProject().get();
        }
        catch ( ProjectBuilderException e )
        {
            throw new MojoException( "Unable to create the project.", e );
        }
    }

    /**
     * Gets the path of the artifact constructed from the supplied groupId, artifactId, version, classifier and
     * packaging within the local repository. Note that the returned path need not exist (yet).
     * 
     * @return The absolute path to the artifact when installed, never <code>null</code>.
     */
    private Path getLocalRepoFile()
    {
        Artifact artifact = getSession().createArtifact( groupId, artifactId, classifier, version, packaging );
        return getSession().getPathForLocalArtifact( artifact );
    }

    /**
     * Process the supplied pomFile to get groupId, artifactId, version, and packaging
     * 
     * @param model The POM to extract missing artifact coordinates from, must not be <code>null</code>.
     */
    private void processModel( Model model )
    {
        Parent parent = model.getParent();

        if ( this.groupId == null )
        {
            this.groupId = model.getGroupId();
            if ( this.groupId == null && parent != null )
            {
                this.groupId = parent.getGroupId();
            }
        }
        if ( this.artifactId == null )
        {
            this.artifactId = model.getArtifactId();
        }
        if ( this.version == null )
        {
            this.version = model.getVersion();
            if ( this.version == null && parent != null )
            {
                this.version = parent.getVersion();
            }
        }
        if ( this.packaging == null )
        {
            this.packaging = model.getPackaging();
        }
    }

    /**
     * Extract the model from the specified POM file.
     * 
     * @param pomFile The path of the POM file to parse, must not be <code>null</code>.
     * @return The model from the POM file, never <code>null</code>.
     * @throws MojoException If the file doesn't exist of cannot be read.
     */
    Model readModel( File pomFile )
        throws MojoException
    {
        Reader reader = null;
        try
        {
            reader = ReaderFactory.newXmlReader( pomFile );
            final Model model = new MavenXpp3Reader().read( reader );
            reader.close();
            reader = null;
            return model;
        }
        catch ( FileNotFoundException e )
        {
            throw new MojoException( "POM not found " + pomFile, e );
        }
        catch ( IOException e )
        {
            throw new MojoException( "Error reading POM " + pomFile, e );
        }
        catch ( XmlPullParserException e )
        {
            throw new MojoException( "Error parsing POM " + pomFile, e );
        }
        finally
        {
            IOUtil.close( reader );
        }
    }

    /**
     * Generates a minimal POM from the user-supplied artifact information.
     * 
     * @return The path to the generated POM file, never <code>null</code>.
     * @throws MojoException If the generation failed.
     */
    private File generatePomFile()
        throws MojoException
    {
        Model model = generateModel();

        Writer fw = null;
        try
        {
            File tempFile = File.createTempFile( "mvndeploy", ".pom" );
            tempFile.deleteOnExit();

            fw = WriterFactory.newXmlWriter( tempFile );

            new MavenXpp3Writer().write( fw, model );

            fw.close();
            fw = null;

            return tempFile;
        }
        catch ( IOException e )
        {
            throw new MojoException( "Error writing temporary pom file: " + e.getMessage(), e );
        }
        finally
        {
            IOUtil.close( fw );
        }
    }

    /**
     * Generates a minimal model from the user-supplied artifact information.
     * 
     * @return The generated model, never <code>null</code>.
     */
    private Model generateModel()
    {
        Model model = new Model();

        model.setModelVersion( "4.0.0" );

        model.setGroupId( groupId );
        model.setArtifactId( artifactId );
        model.setVersion( version );
        model.setPackaging( packaging );

        model.setDescription( description );

        return model;
    }

    void setGroupId( String groupId )
    {
        this.groupId = groupId;
    }

    void setArtifactId( String artifactId )
    {
        this.artifactId = artifactId;
    }

    void setVersion( String version )
    {
        this.version = version;
    }

    void setPackaging( String packaging )
    {
        this.packaging = packaging;
    }

    void setPomFile( File pomFile )
    {
        this.pomFile = pomFile;
    }

    String getGroupId()
    {
        return groupId;
    }

    String getArtifactId()
    {
        return artifactId;
    }

    String getVersion()
    {
        return version;
    }

    String getPackaging()
    {
        return packaging;
    }

    File getFile()
    {
        return file;
    }

    String getClassifier()
    {
        return classifier;
    }

    void setClassifier( String classifier )
    {
        this.classifier = classifier;
    }

    private static class StringSource implements ProjectBuilderSource
    {
        private final String prj;

        StringSource( String prj )
        {
            this.prj = prj;
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            return new ByteArrayInputStream( prj.getBytes( StandardCharsets.UTF_8 ) );
        }

        @Override
        public String getLocation()
        {
            return null;
        }
    }

}
