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
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class BundleService {

    MavenProject project;
    Log log;

    public BundleService(MavenProject project, Log log) {
        this.project = project;
        this.log = log;
    }

    static final List<String> CHECKSUM_ALGOS = Arrays.asList("MD5", "SHA-1", "SHA-256");

    /**
     * Create a mega bundle zip with the artifacts for all projects in this build..
     * This method requires that the "verify" phase has been executed and gpg signing has been configured.
     *
     * @param bundleFile the zip file to create
     * @throws IOException if creating the zip file failed
     * @throws NoSuchAlgorithmException if md5, sha-1 or sha-256 algorithms are not available in
     * the environment.
     */
    public void createZipBundle(File bundleFile, List<MavenProject> allProjectsUsingPlugin)
            throws IOException, NoSuchAlgorithmException, MojoExecutionException {
        bundleFile.getParentFile().mkdirs();
        bundleFile.createNewFile();
        log.info("Creating zip bundle at " + bundleFile.getAbsolutePath());

        Map<String, List<File>> artifactFiles = new HashMap<>();
        for (MavenProject project : allProjectsUsingPlugin) {
            String groupId = project.getGroupId();
            String artifactId = project.getArtifactId();
            String version = project.getVersion();
            String groupPath = groupId.replace('.', '/');
            String mavenPathPrefix = String.join("/", groupPath, artifactId, version) + "/";
            artifactFiles.computeIfAbsent(mavenPathPrefix, k -> new ArrayList<>());
            File artifactFile = project.getArtifact().getFile();
            // Will be null for e.g., an aggregator project
            if (artifactFile != null && artifactFile.exists()) {
                artifactFiles.get(mavenPathPrefix).add(artifactFile);
                File signFile = new File(artifactFile.getAbsolutePath() + ".asc");
                if (!signFile.exists()) {
                    throw new MojoExecutionException(artifactFile + " is not signed, " + signFile + " is missing");
                }
                artifactFiles.get(mavenPathPrefix).add(signFile);
            }
            // pom is not in getAttachedArtifacts so add it explicitly
            File pomFile = new File(project.getBuild().getDirectory(), String.join("-", artifactId, version) + ".pom");
            if (pomFile.exists()) {
                // Since it is the "raw" pom file that is published, not the effective pom, we must check the file,
                // not the project. Also since the pom file is the signed one, we cannot change it to the effective pom.
                validateForPublishing(pomFile);

                artifactFiles.get(mavenPathPrefix).add(pomFile);
                File signFile = new File(pomFile.getAbsolutePath() + ".asc");
                if (!signFile.exists()) {
                    throw new MojoExecutionException(
                            "POM file " + pomFile + " is not signed, " + signFile + " is missing");
                }
                artifactFiles.get(mavenPathPrefix).add(signFile);

            } else {
                log.error("POM file " + pomFile + " does not exist (verify phase not reached)!");
                throw new MojoExecutionException("POM file " + pomFile + " does not exist!");
            }
            log.info("**********************************");
            log.info("Artifacts are:");
            project.getArtifacts().forEach(artifact -> {
                log.info(artifact.getFile().getAbsolutePath());
            });
            log.info("Attached artifacts are:");
            project.getAttachedArtifacts().forEach(artifact -> {
                log.info(artifact.getFile().getAbsolutePath());
            });
            log.info("**********************************");
            for (Artifact artifact : project.getAttachedArtifacts()) {
                File file = artifact.getFile();
                if (file.exists()) {
                    artifactFiles.get(mavenPathPrefix).add(artifact.getFile());
                } else {
                    log.error("Artifact " + artifact.getId() + " does not exist!");
                }
                File signFile = new File(file.getAbsolutePath() + ".asc");
                if (!signFile.exists()) {
                    throw new MojoExecutionException("File " + file + " is not signed, " + signFile + " is missing");
                }
                artifactFiles.get(mavenPathPrefix).add(signFile);
            }
        }

        log.info("Adding the following entries to the zip file");
        log.info("********************************************");

        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(bundleFile.toPath()))) {
            for (Map.Entry<String, List<File>> entry : artifactFiles.entrySet()) {
                String mavenPathPrefix = entry.getKey();
                for (File file : entry.getValue()) {
                    addToZip(file, mavenPathPrefix, zipOut);
                    if (file.getName().endsWith(".asc")) {
                        continue; // asc files has no checksums
                    }
                    // Ensure the artifact is signed before continuing to create and add checksums
                    File signFile = new File(file.getAbsolutePath() + ".asc");
                    if (!signFile.exists()) {
                        throw new MojoExecutionException(
                                "The artifact " + file + " was not signed! " + signFile + " does not exists");
                    }
                    generateChecksumsAndAddToZip(file, mavenPathPrefix, zipOut);
                }
            }
        }
    }

    /**
     * Create a bundle zip with the artifacts for the current project only.
     * This method requires that the "verify" phase has been executed and gpg signing has been configured.
     * e.g. mvn verify deploy:bundle
     *
     * @param bundleFile the zip file to create
     * @throws IOException if creating the zip file failed
     * @throws NoSuchAlgorithmException if md5, sha-1 or sha-256 algorithms are not available in
     * the environment.
     */
    public void createZipBundle(File bundleFile) throws IOException, NoSuchAlgorithmException, MojoExecutionException {
        bundleFile.getParentFile().mkdirs();
        bundleFile.createNewFile();
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId();
        String version = project.getVersion();
        String groupPath = groupId.replace('.', '/');
        String mavenPathPrefix = String.join("/", groupPath, artifactId, version) + "/";

        List<File> artifactFiles = new ArrayList<>();
        File artifactFile = project.getArtifact().getFile();
        // Will be null for e.g., an aggregator project
        if (artifactFile != null && artifactFile.exists()) {
            artifactFiles.add(artifactFile);
            File signFile = new File(artifactFile.getAbsolutePath() + ".asc");
            if (!signFile.exists()) {
                throw new MojoExecutionException(artifactFile + " is not signed, " + signFile + " is missing");
            }
            artifactFiles.add(signFile);
        }

        // pom is not in getAttachedArtifacts so add it explicitly
        File pomFile = new File(project.getBuild().getDirectory(), String.join("-", artifactId, version) + ".pom");
        if (pomFile.exists()) {
            // Since it is the "raw" pom file that is published, not the effective pom, we must check the file,
            // not the project. Also since the pom file is the signed one, we cannot change it to the effective pom.
            validateForPublishing(pomFile);
            artifactFiles.add(pomFile);
            File signFile = new File(pomFile.getAbsolutePath() + ".asc");
            if (!signFile.exists()) {
                throw new MojoExecutionException("POM file " + pomFile + " is not signed, " + signFile + " is missing");
            }
            artifactFiles.add(signFile);
        } else {
            log.error("POM file " + pomFile + " does not exist (verify phase not reached)!");
            throw new MojoExecutionException("POM file " + pomFile + " does not exist!");
        }
        for (Artifact artifact : project.getAttachedArtifacts()) {
            File file = artifact.getFile();
            if (file.exists()) {
                artifactFiles.add(artifact.getFile());
                File signFile = new File(file.getAbsolutePath() + ".asc");
                if (!signFile.exists()) {
                    throw new MojoExecutionException(file + " is not signed, " + signFile + " is missing");
                }
                artifactFiles.add(signFile);
            } else {
                log.error("Artifact " + artifact.getId() + " does not exist!");
            }
        }

        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(bundleFile.toPath()))) {

            for (File file : artifactFiles) {
                addToZip(file, mavenPathPrefix, zipOut);
                if (file.getName().endsWith(".asc")) {
                    continue; // asc files has no checksums
                }
                File signFile = new File(file.getAbsolutePath() + ".asc");
                if (!signFile.exists()) {
                    throw new MojoExecutionException(
                            "The artifact " + file + " was not signed! " + signFile + " does not exists");
                }
                generateChecksumsAndAddToZip(file, mavenPathPrefix, zipOut);
            }
        }
        log.info("Created bundle at: " + bundleFile.getAbsolutePath());
    }

    /**
     * Validates that the following elements are present (required for publishing to central):
     * <ul>
     *  <li>Project description</li>
     *  <li>License information</li>
     *  <li>SCM URL</li>
     *  <li>Developers information</li>
     * </ul>
     */
    private void validateForPublishing(File pomFile) throws MojoExecutionException {
        Model model = readPomFile(pomFile);
        List<String> errs = new ArrayList<>();
        if (model.getDescription() == null || model.getDescription().trim().isEmpty()) {
            errs.add("description is missing");
        }
        if (model.getLicenses() == null || model.getLicenses().isEmpty()) {
            errs.add("license is missing");
        }
        if (model.getScm() == null) {
            errs.add("scm is missing");
        } else if (model.getScm().getUrl() == null) {
            errs.add("scm url is missing");
        }
        if (model.getDevelopers() == null || model.getDevelopers().isEmpty()) {
            errs.add("developers is missing");
        }

        if (!errs.isEmpty()) {
            throw new MojoExecutionException(pomFile + " is not valid for publishing: " + String.join(", ", errs));
        }
    }

    private void addToZip(File file, String prefix, ZipOutputStream zipOut) throws IOException {
        log.info("addToZip  - " + file.getAbsolutePath());
        zipOut.putNextEntry(new ZipEntry(prefix + file.getName()));
        Files.copy(file.toPath(), zipOut);
        zipOut.closeEntry();
    }

    private void generateChecksumsAndAddToZip(File sourceFile, String prefix, ZipOutputStream zipOut)
            throws NoSuchAlgorithmException, IOException {
        for (String algo : CHECKSUM_ALGOS) {
            File checksumFile = generateChecksum(sourceFile, algo);
            addToZip(checksumFile, prefix, zipOut);
        }
    }

    public File generateChecksum(File file, String algo) throws NoSuchAlgorithmException, IOException {
        String extension = algo.toLowerCase().replace("-", "");
        File checksumFile = new File(file.getAbsolutePath() + "." + extension);
        // It might have been generated externally. In that case, use that.
        if (checksumFile.exists()) {
            return checksumFile;
        }

        // Create the checksum file
        MessageDigest digest = MessageDigest.getInstance(algo);
        try (InputStream is = Files.newInputStream(file.toPath());
                OutputStream nullOut = new OutputStream() {
                    @Override
                    public void write(int b) {}
                };
                DigestOutputStream dos = new DigestOutputStream(nullOut, digest)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }

        Files.write(checksumFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        return checksumFile;
    }

    Model readPomFile(File pomFile) throws MojoExecutionException {
        try {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            try (Reader fileReader = new FileReader(pomFile)) {
                return reader.read(fileReader);
            }
        } catch (XmlPullParserException | IOException e) {
            throw new MojoExecutionException("Failed to parse POM file.", e);
        }
    }
}
