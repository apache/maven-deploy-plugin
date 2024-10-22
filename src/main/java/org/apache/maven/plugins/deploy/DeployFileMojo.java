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
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Parent;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactDeployer;
import org.apache.maven.api.services.ArtifactDeployerException;
import org.apache.maven.api.services.ArtifactDeployerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.api.services.xml.XmlReaderException;

/**
 * Installs the artifact in the remote repository.
 *
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@Mojo(name = "deploy-file", projectRequired = false)
@SuppressWarnings("unused")
public class DeployFileMojo extends AbstractDeployMojo {
    private static final String TAR = "tar.";
    private static final String ILLEGAL_VERSION_CHARS = "\\/:\"<>|?*[](){},";

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
    Path file;

    /**
     * The bundled API docs for the artifact.
     *
     * @since 2.6
     */
    @Parameter(property = "javadoc")
    private Path javadoc;

    /**
     * The bundled sources for the artifact.
     *
     * @since 2.6
     */
    @Parameter(property = "sources")
    private Path sources;

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
    private Path pomFile;

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

    void initProperties() throws MojoException {
        Path deployedPom;
        if (pomFile != null) {
            deployedPom = pomFile;
            processModel(readModel(deployedPom));
        } else {
            deployedPom = readingPomFromJarFile();
            if (deployedPom != null) {
                pomFile = deployedPom;
            }
        }

        if (packaging == null && file != null) {
            packaging = getExtension(file);
        }
    }

    private Path readingPomFromJarFile() {
        Pattern pomEntry = Pattern.compile("META-INF/maven/.*/pom\\.xml");
        try {
            try (JarFile jarFile = new JarFile(file.toFile())) {
                JarEntry entry = jarFile.stream()
                        .filter(e -> pomEntry.matcher(e.getName()).matches())
                        .findFirst()
                        .orElse(null);
                if (entry != null) {
                    getLog().debug("Using " + entry.getName() + " as pomFile");

                    try (InputStream pomInputStream = jarFile.getInputStream(entry)) {
                        String base = file.getFileName().toString();
                        if (base.indexOf('.') > 0) {
                            base = base.substring(0, base.lastIndexOf('.'));
                        }
                        Path pomFile = File.createTempFile(base, ".pom").toPath();

                        Files.copy(pomInputStream, pomFile, StandardCopyOption.REPLACE_EXISTING);

                        processModel(readModel(pomFile));

                        return pomFile;
                    }
                } else {
                    getLog().info("pom.xml not found in " + file.getFileName());
                }
            }
        } catch (IOException e) {
            // ignore, artifact not packaged by Maven
        }
        return null;
    }

    public void execute() throws MojoException {
        if (Boolean.parseBoolean(skip)
                || ("releases".equals(skip) && !session.isVersionSnapshot(version))
                || ("snapshots".equals(skip) && session.isVersionSnapshot(version))) {
            getLog().info("Skipping artifact deployment");
            return;
        }

        if (!Files.exists(file)) {
            String message = "The specified file '" + file + "' does not exist";
            getLog().error(message);
            throw new MojoException(message);
        }

        initProperties();

        RemoteRepository deploymentRepository =
                createDeploymentArtifactRepository(repositoryId, url.replace(File.separator, "/"));

        if (deploymentRepository.getProtocol().isEmpty()) {
            throw new MojoException("No transfer protocol found.");
        }

        Path deployedPom;
        if (pomFile != null) {
            deployedPom = pomFile;
            processModel(readModel(deployedPom));
        } else {
            deployedPom = readingPomFromJarFile();
        }

        if (groupId == null || artifactId == null || version == null || packaging == null) {
            throw new MojoException("The artifact information is incomplete: 'groupId', 'artifactId', "
                    + "'version' and 'packaging' are required.");
        }

        if (!isValidId(groupId) || !isValidId(artifactId) || !isValidVersion(version)) {
            throw new MojoException("The artifact information is not valid: uses invalid characters.");
        }

        failIfOffline();
        warnIfAffectedPackagingAndMaven(packaging);

        List<Artifact> deployables = new ArrayList<>();

        boolean isFilePom = classifier == null && "pom".equals(packaging);
        ProducedArtifact artifact = session.createProducedArtifact(
                groupId, artifactId, version, classifier, isFilePom ? "pom" : getExtension(file), packaging);

        if (file.equals(getLocalRepositoryFile(artifact))) {
            throw new MojoException("Cannot deploy artifact from the local repository: " + file);
        }

        ArtifactManager artifactManager = session.getService(ArtifactManager.class);
        artifactManager.setPath(artifact, file);
        deployables.add(artifact);

        if (!isFilePom) {
            ProducedArtifact pomArtifact =
                    session.createProducedArtifact(groupId, artifactId, version, "", "pom", null);
            if (deployedPom != null) {
                artifactManager.setPath(pomArtifact, deployedPom);
                deployables.add(pomArtifact);
            } else {
                deployedPom = generatePomFile();
                artifactManager.setPath(pomArtifact, deployedPom);
                if (generatePom) {
                    getLog().debug("Deploying generated POM");
                    deployables.add(pomArtifact);
                } else {
                    getLog().debug("Skipping deploying POM");
                }
            }
        }

        if (sources != null) {
            ProducedArtifact sourcesArtifact =
                    session.createProducedArtifact(groupId, artifactId, version, "sources", "jar", null);
            artifactManager.setPath(sourcesArtifact, sources);
            deployables.add(sourcesArtifact);
        }

        if (javadoc != null) {
            ProducedArtifact javadocArtifact =
                    session.createProducedArtifact(groupId, artifactId, version, "javadoc", "jar", null);
            artifactManager.setPath(javadocArtifact, javadoc);
            deployables.add(javadocArtifact);
        }

        if (files != null) {
            if (types == null) {
                throw new MojoException("You must specify 'types' if you specify 'files'");
            }
            if (classifiers == null) {
                throw new MojoException("You must specify 'classifiers' if you specify 'files'");
            }
            int filesLength = countCommas(files);
            int typesLength = countCommas(types);
            int classifiersLength = countCommas(classifiers);
            if (typesLength != filesLength) {
                throw new MojoException("You must specify the same number of entries in 'files' and "
                        + "'types' (respectively " + filesLength + " and " + typesLength + " entries )");
            }
            if (classifiersLength != filesLength) {
                throw new MojoException("You must specify the same number of entries in 'files' and "
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
                Path file = Paths.get(files.substring(fi, nfi).replace("/", File.separator));
                if (!Files.isRegularFile(file)) {
                    // try relative to the project basedir just in case
                    file = Paths.get(files.substring(fi, nfi));
                }
                if (Files.isRegularFile(file)) {
                    String extension = getExtension(file);
                    String type = types.substring(ti, nti).trim();

                    ProducedArtifact deployable = session.createProducedArtifact(
                            artifact.getGroupId(),
                            artifact.getArtifactId(),
                            artifact.getVersion().asString(),
                            classifiers.substring(ci, nci).trim(),
                            extension,
                            type);
                    artifactManager.setPath(deployable, file);
                    deployables.add(deployable);
                } else {
                    throw new MojoException("Specified side artifact " + file + " does not exist");
                }
                fi = nfi + 1;
                ti = nti + 1;
                ci = nci + 1;
            }
        } else {
            if (types != null) {
                throw new MojoException("You must specify 'files' if you specify 'types'");
            }
            if (classifiers != null) {
                throw new MojoException("You must specify 'files' if you specify 'classifiers'");
            }
        }

        try {
            ArtifactDeployerRequest deployRequest = ArtifactDeployerRequest.builder()
                    .session(session)
                    .repository(deploymentRepository)
                    .artifacts(deployables)
                    .retryFailedDeploymentCount(Math.max(1, Math.min(10, getRetryFailedDeploymentCount())))
                    .build();

            getLog().info("Deploying artifacts " + deployables + " to repository " + deploymentRepository);
            ArtifactDeployer artifactDeployer = session.getService(ArtifactDeployer.class);
            artifactDeployer.deploy(deployRequest);
        } catch (ArtifactDeployerException e) {
            throw new MojoException(e.getMessage(), e);
        } finally {
            if (pomFile == null && deployedPom != null) {
                try {
                    Files.deleteIfExists(deployedPom);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Gets the path of the specified artifact within the local repository. Note that the returned path need not exist
     * (yet).
     */
    private Path getLocalRepositoryFile(Artifact artifact) {
        return session.getPathForLocalArtifact(artifact);
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
     * @throws MojoException If the file doesn't exist of cannot be read.
     */
    Model readModel(Path pomFile) throws MojoException {
        try (InputStream is = Files.newInputStream(pomFile)) {
            ModelXmlFactory modelXmlFactory = session.getService(ModelXmlFactory.class);
            return modelXmlFactory.read(is);
        } catch (FileNotFoundException e) {
            throw new MojoException("POM not found " + pomFile, e);
        } catch (IOException e) {
            throw new MojoException("Error reading POM " + pomFile, e);
        } catch (XmlReaderException e) {
            throw new MojoException("Error parsing POM " + pomFile, e);
        }
    }

    /**
     * Generates a minimal POM from the user-supplied artifact information.
     *
     * @return The path to the generated POM file, never <code>null</code>.
     * @throws MojoException If the generation failed.
     */
    private Path generatePomFile() throws MojoException {
        Model model = generateModel();
        try {
            Path pomFile = File.createTempFile("mvndeploy", ".pom").toPath();
            try (Writer writer = Files.newBufferedWriter(pomFile)) {
                ModelXmlFactory modelXmlFactory = session.getService(ModelXmlFactory.class);
                modelXmlFactory.write(model, writer);
            }
            return pomFile;
        } catch (IOException e) {
            throw new MojoException("Error writing temporary POM file: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a minimal model from the user-supplied artifact information.
     *
     * @return The generated model, never <code>null</code>.
     */
    private Model generateModel() {
        return Model.newBuilder()
                .modelVersion("4.0.0")
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .packaging(packaging)
                .description(description)
                .build();
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

    void setPomFile(Path pomFile) {
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

    Path getFile() {
        return file;
    }

    String getClassifier() {
        return classifier;
    }

    void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    // these below should be shared (duplicated in m-install-p, m-deploy-p)

    private static int countCommas(String str) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(',', idx)) != -1) {
            count++;
            idx++;
        }
        return count;
    }

    /**
     * Get file extension, honoring various {@code tar.xxx} combinations.
     */
    private String getExtension(final Path file) {
        String filename = file.getFileName().toString();
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            String ext = filename.substring(lastDot + 1);
            return filename.regionMatches(lastDot + 1 - TAR.length(), TAR, 0, TAR.length()) ? TAR + ext : ext;
        }
        return "";
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
