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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.ReaderFactory;
import org.codehaus.plexus.util.xml.WriterFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * Installs the artifact in the remote repository.
 *
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@Mojo(name = "deploy-file", requiresProject = false, threadSafe = true)
public class DeployFileMojo extends AbstractDeployMojo {
    /**
     * GroupId of the artifact to be deployed. Retrieved from POM file if specified.
     */
    @Parameter(property = "groupId")
    private String groupId;

    /**
     * ArtifactId of the artifact to be deployed. Retrieved from POM file if specified.
     */
    @Parameter(property = "artifactId")
    private String artifactId;

    /**
     * Version of the artifact to be deployed. Retrieved from POM file if specified.
     */
    @Parameter(property = "version")
    private String version;

    /**
     * Type of the artifact to be deployed. Retrieved from the &lt;packaging&gt element of the POM file if a POM file
     * specified. Defaults to the file extension if it is not specified via command line or POM.<br/>
     * Maven uses two terms to refer to this datum: the &lt;packaging&gt; element for the entire POM, and the
     * &lt;type&gt; element in a dependency specification.
     */
    @Parameter(property = "packaging")
    private String packaging;

    /**
     * Description passed to a generated POM file (in case of generatePom=true)
     */
    @Parameter(property = "generatePom.description")
    private String description;

    /**
     * File to be deployed.
     */
    @Parameter(property = "file", required = true)
    private File file;

    /**
     * The bundled API docs for the artifact.
     *
     * @since 2.6
     */
    @Parameter(property = "javadoc")
    private File javadoc;

    /**
     * The bundled sources for the artifact.
     *
     * @since 2.6
     */
    @Parameter(property = "sources")
    private File sources;

    /**
     * Server Id to map on the &lt;id&gt; under &lt;server&gt; section of settings.xml In most cases, this parameter
     * will be required for authentication.
     */
    @Parameter(property = "repositoryId", defaultValue = "remote-repository", required = true)
    private String repositoryId;

    /**
     * URL where the artifact will be deployed. <br/>
     * ie ( file:///C:/m2-repo or scp://host.com/path/to/repo )
     */
    @Parameter(property = "url", required = true)
    private String url;

    /**
     * Location of an existing POM file to be deployed alongside the main artifact, given by the ${file} parameter.
     */
    @Parameter(property = "pomFile")
    private File pomFile;

    /**
     * Upload a POM for this artifact. Will generate a default POM if none is supplied with the pomFile argument.
     */
    @Parameter(property = "generatePom", defaultValue = "true")
    private boolean generatePom;

    /**
     * Add classifier to the artifact
     */
    @Parameter(property = "classifier")
    private String classifier;

    /**
     * A comma separated list of types for each of the extra side artifacts to deploy. If there is a mis-match in the
     * number of entries in {@link #files} or {@link #classifiers}, then an error will be raised.
     */
    @Parameter(property = "types")
    private String types;

    /**
     * A comma separated list of classifiers for each of the extra side artifacts to deploy. If there is a mis-match in
     * the number of entries in {@link #files} or {@link #types}, then an error will be raised.
     */
    @Parameter(property = "classifiers")
    private String classifiers;

    /**
     * A comma separated list of files for each of the extra side artifacts to deploy. If there is a mis-match in the
     * number of entries in {@link #types} or {@link #classifiers}, then an error will be raised.
     */
    @Parameter(property = "files")
    private String files;

    /**
     * Set this to 'true' to bypass artifact deploy
     * It's not a real boolean as it can have more than 2 values:
     * <ul>
     *     <li><code>true</code>: will skip as usual</li>
     *     <li><code>releases</code>: will skip if current version of the project is a release</li>
     *     <li><code>snapshots</code>: will skip if current version of the project is a snapshot</li>
     *     <li>any other values will be considered as <code>false</code></li>
     * </ul>
     * @since 3.1.0
     */
    @Parameter(property = "maven.deploy.file.skip", defaultValue = "false")
    private String skip = Boolean.FALSE.toString();

    void initProperties() throws MojoExecutionException {
        if (pomFile == null) {
            boolean foundPom = false;
            try (JarFile jarFile = new JarFile(file)) {
                Pattern pomEntry = Pattern.compile("META-INF/maven/.*/pom\\.xml");
                Enumeration<JarEntry> jarEntries = jarFile.entries();

                while (jarEntries.hasMoreElements()) {
                    JarEntry entry = jarEntries.nextElement();

                    if (pomEntry.matcher(entry.getName()).matches()) {
                        getLog().debug("Using " + entry.getName() + " as pomFile");
                        foundPom = true;
                        String base = file.getName();
                        if (base.indexOf('.') > 0) {
                            base = base.substring(0, base.lastIndexOf('.'));
                        }
                        pomFile = new File(file.getParentFile(), base + ".pom");

                        try (InputStream pomInputStream = jarFile.getInputStream(entry)) {
                            try (OutputStream pomOutputStream = Files.newOutputStream(pomFile.toPath())) {
                                IOUtil.copy(pomInputStream, pomOutputStream);
                            }
                            processModel(readModel(pomFile));
                            break;
                        }
                    }
                }

                if (!foundPom) {
                    getLog().info("pom.xml not found in " + file.getName());
                }
            } catch (IOException e) {
                // ignore, artifact not packaged by Maven
            }
        } else {
            processModel(readModel(pomFile));
        }

        if (packaging == null && file != null) {
            packaging = getExtension(file);
        }
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (Boolean.parseBoolean(skip)
                || ("releases".equals(skip) && !ArtifactUtils.isSnapshot(version))
                || ("snapshots".equals(skip) && ArtifactUtils.isSnapshot(version))) {
            getLog().info("Skipping artifact deployment");
            return;
        }

        if (!file.exists()) {
            throw new MojoExecutionException(file.getPath() + " not found.");
        }

        initProperties();

        RemoteRepository remoteRepository = getRemoteRepository(repositoryId, url);

        if (StringUtils.isEmpty(remoteRepository.getProtocol())) {
            throw new MojoExecutionException("No transfer protocol found.");
        }

        if (groupId == null || artifactId == null || version == null || packaging == null) {
            throw new MojoExecutionException("The artifact information is incomplete: 'groupId', 'artifactId', "
                    + "'version' and 'packaging' are required.");
        }

        if (!isValidId(groupId) || !isValidId(artifactId) || !isValidVersion(version)) {
            throw new MojoExecutionException("The artifact information is not valid: uses invalid characters.");
        }

        failIfOffline();
        warnIfAffectedPackagingAndMaven(packaging);

        DeployRequest deployRequest = new DeployRequest();
        deployRequest.setRepository(remoteRepository);

        boolean isFilePom = classifier == null && "pom".equals(packaging);
        if (!isFilePom) {
            ArtifactType artifactType =
                    session.getRepositorySession().getArtifactTypeRegistry().get(packaging);
            if (artifactType != null
                    && (classifier == null || classifier.isEmpty())
                    && !StringUtils.isEmpty(artifactType.getClassifier())) {
                classifier = artifactType.getClassifier();
            }
        }
        Artifact mainArtifact = new DefaultArtifact(
                        groupId, artifactId, classifier, isFilePom ? "pom" : getExtension(file), version)
                .setFile(file);
        deployRequest.addArtifact(mainArtifact);

        File artifactLocalFile = getLocalRepositoryFile(session.getRepositorySession(), mainArtifact);

        if (file.equals(artifactLocalFile)) {
            throw new MojoFailureException("Cannot deploy artifact from the local repository: " + file);
        }

        File temporaryPom = null;
        if (!"pom".equals(packaging)) {
            if (pomFile != null) {
                deployRequest.addArtifact(new SubArtifact(mainArtifact, "", "pom", pomFile));
            } else if (generatePom) {
                temporaryPom = generatePomFile();
                getLog().debug("Deploying generated POM");
                deployRequest.addArtifact(new SubArtifact(mainArtifact, "", "pom", temporaryPom));
            } else {
                getLog().debug("Skipping deploying POM");
            }
        }

        if (sources != null) {
            deployRequest.addArtifact(new SubArtifact(mainArtifact, "sources", "jar", sources));
        }

        if (javadoc != null) {
            deployRequest.addArtifact(new SubArtifact(mainArtifact, "javadoc", "jar", javadoc));
        }

        if (files != null) {
            if (types == null) {
                throw new MojoExecutionException("You must specify 'types' if you specify 'files'");
            }
            if (classifiers == null) {
                throw new MojoExecutionException("You must specify 'classifiers' if you specify 'files'");
            }
            int filesLength = StringUtils.countMatches(files, ",");
            int typesLength = StringUtils.countMatches(types, ",");
            int classifiersLength = StringUtils.countMatches(classifiers, ",");
            if (typesLength != filesLength) {
                throw new MojoExecutionException("You must specify the same number of entries in 'files' and "
                        + "'types' (respectively " + filesLength + " and " + typesLength + " entries )");
            }
            if (classifiersLength != filesLength) {
                throw new MojoExecutionException("You must specify the same number of entries in 'files' and "
                        + "'classifiers' (respectively " + filesLength + " and " + classifiersLength + " entries )");
            }
            int fi = 0;
            int ti = 0;
            int ci = 0;
            for (int i = 0; i <= filesLength; i++) {
                int nfi = files.indexOf(',', fi);
                if (nfi == -1) {
                    nfi = files.length();
                }
                int nti = types.indexOf(',', ti);
                if (nti == -1) {
                    nti = types.length();
                }
                int nci = classifiers.indexOf(',', ci);
                if (nci == -1) {
                    nci = classifiers.length();
                }
                File file = new File(files.substring(fi, nfi));
                if (!file.isFile()) {
                    // try relative to the project basedir just in case
                    file = new File(files.substring(fi, nfi));
                }
                if (file.isFile()) {
                    String extension = getExtension(file);
                    ArtifactType artifactType = session.getRepositorySession()
                            .getArtifactTypeRegistry()
                            .get(types.substring(ti, nti).trim());
                    if (artifactType != null && !Objects.equals(extension, artifactType.getExtension())) {
                        extension = artifactType.getExtension();
                    }

                    deployRequest.addArtifact(new SubArtifact(
                            mainArtifact, classifiers.substring(ci, nci).trim(), extension, file));
                } else {
                    throw new MojoExecutionException("Specified side artifact " + file + " does not exist");
                }
                fi = nfi + 1;
                ti = nti + 1;
                ci = nci + 1;
            }
        } else {
            if (types != null) {
                throw new MojoExecutionException("You must specify 'files' if you specify 'types'");
            }
            if (classifiers != null) {
                throw new MojoExecutionException("You must specify 'files' if you specify 'classifiers'");
            }
        }

        try {
            repositorySystem.deploy(session.getRepositorySession(), deployRequest);
        } catch (DeploymentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            if (temporaryPom != null) {
                // noinspection ResultOfMethodCallIgnored
                temporaryPom.delete();
            }
        }
    }

    /**
     * Gets the path of the specified artifact within the local repository. Note that the returned path need not exist
     * (yet).
     */
    private File getLocalRepositoryFile(RepositorySystemSession session, Artifact artifact) {
        String path = session.getLocalRepositoryManager().getPathForLocalArtifact(artifact);
        return new File(session.getLocalRepository().getBasedir(), path);
    }

    /**
     * Process the supplied pomFile to get groupId, artifactId, version, and packaging
     *
     * @param model The POM to extract missing artifact coordinates from, must not be <code>null</code>.
     */
    private void processModel(Model model) {
        Parent parent = model.getParent();

        if (this.groupId == null) {
            this.groupId = model.getGroupId();
            if (this.groupId == null && parent != null) {
                this.groupId = parent.getGroupId();
            }
        }
        if (this.artifactId == null) {
            this.artifactId = model.getArtifactId();
        }
        if (this.version == null) {
            this.version = model.getVersion();
            if (this.version == null && parent != null) {
                this.version = parent.getVersion();
            }
        }
        if (this.packaging == null) {
            this.packaging = model.getPackaging();
        }
    }

    /**
     * Extract the model from the specified POM file.
     *
     * @param pomFile The path of the POM file to parse, must not be <code>null</code>.
     * @return The model from the POM file, never <code>null</code>.
     * @throws MojoExecutionException If the file doesn't exist or cannot be read.
     */
    Model readModel(File pomFile) throws MojoExecutionException {
        try (Reader reader = ReaderFactory.newXmlReader(pomFile)) {
            return new MavenXpp3Reader().read(reader);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("POM not found " + pomFile, e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading POM " + pomFile, e);
        } catch (XmlPullParserException e) {
            throw new MojoExecutionException("Error parsing POM " + pomFile, e);
        }
    }

    /**
     * Generates a minimal POM from the user-supplied artifact information.
     *
     * @return The path to the generated POM file, never <code>null</code>.
     * @throws MojoExecutionException If the generation failed.
     */
    private File generatePomFile() throws MojoExecutionException {
        Model model = generateModel();

        try {
            File tempFile = File.createTempFile("mvndeploy", ".pom");
            tempFile.deleteOnExit();

            try (Writer fw = WriterFactory.newXmlWriter(tempFile)) {
                new MavenXpp3Writer().write(fw, model);
            }

            return tempFile;
        } catch (IOException e) {
            throw new MojoExecutionException("Error writing temporary pom file: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a minimal model from the user-supplied artifact information.
     *
     * @return The generated model, never <code>null</code>.
     */
    private Model generateModel() {
        Model model = new Model();

        model.setModelVersion("4.0.0");

        model.setGroupId(groupId);
        model.setArtifactId(artifactId);
        model.setVersion(version);
        model.setPackaging(packaging);

        model.setDescription(description);

        return model;
    }

    void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    void setVersion(String version) {
        this.version = version;
    }

    void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    void setPomFile(File pomFile) {
        this.pomFile = pomFile;
    }

    String getGroupId() {
        return groupId;
    }

    String getArtifactId() {
        return artifactId;
    }

    String getVersion() {
        return version;
    }

    String getPackaging() {
        return packaging;
    }

    File getFile() {
        return file;
    }

    String getClassifier() {
        return classifier;
    }

    void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    // these below should be shared (duplicated in m-install-p, m-deploy-p)

    /**
     * Specialization of {@link FileUtils#getExtension(String)} that honors various {@code tar.xxx} combinations.
     */
    private String getExtension(final File file) {
        String filename = file.getName();
        if (filename.contains(".tar.")) {
            return "tar." + FileUtils.getExtension(filename);
        } else {
            return FileUtils.getExtension(filename);
        }
    }

    /**
     * Returns {@code true} if passed in string is "valid Maven ID" (groupId or artifactId).
     */
    private boolean isValidId(String id) {
        if (id == null) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z'
                    || c >= 'A' && c <= 'Z'
                    || c >= '0' && c <= '9'
                    || c == '-'
                    || c == '_'
                    || c == '.')) {
                return false;
            }
        }
        return true;
    }

    private static final String ILLEGAL_VERSION_CHARS = "\\/:\"<>|?*[](){},";

    /**
     * Returns {@code true} if passed in string is "valid Maven (simple. non range, expression, etc) version".
     */
    private boolean isValidVersion(String version) {
        if (version == null) {
            return false;
        }
        for (int i = version.length() - 1; i >= 0; i--) {
            if (ILLEGAL_VERSION_CHARS.indexOf(version.charAt(i)) >= 0) {
                return false;
            }
        }
        return true;
    }
}
