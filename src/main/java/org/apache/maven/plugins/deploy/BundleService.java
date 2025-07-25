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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

public class BundleService {

    MavenProject project;
    Log log;

    public BundleService(MavenProject project, Log log) {
        this.project = project;
        this.log = log;
    }

    static final List<String> CHECKSUM_ALGOS = Arrays.asList("MD5", "SHA-1", "SHA-256");

    /**
     * This method requires that the "verify" phase has been executed and gpg signing has been configured.
     * e.g. mvn install deploy:bundle
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
        }

        // pom is not in getAttachedArtifacts so add it explicitly
        File pomFile = new File(project.getBuild().getDirectory(), String.join("-", artifactId, version) + ".pom");
        if (pomFile.exists()) {
            artifactFiles.add(pomFile);
        } else {
            log.error("POM file " + pomFile + " does not exist (verify phase not reached)!");
            // throw new MojoExecutionException("POM file " + pomFile + " does not exist!");
        }
        for (Artifact artifact : project.getAttachedArtifacts()) {
            File file = artifact.getFile();
            if (file.exists()) {
                artifactFiles.add(artifact.getFile());
            } else {
                log.error("Artifact " + artifact.getId() + " does not exist!");
            }
        }

        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(bundleFile.toPath()))) {

            for (File file : artifactFiles) {
                zipOut.putNextEntry(new ZipEntry(mavenPathPrefix + file.getName()));
                Files.copy(file.toPath(), zipOut);
                zipOut.closeEntry();
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

    private void generateChecksumsAndAddToZip(File sourceFile, String prefix, ZipOutputStream zipOut)
            throws NoSuchAlgorithmException, IOException {
        for (String algo : CHECKSUM_ALGOS) {
            File checksumFile = generateChecksum(sourceFile, algo);
            zipOut.putNextEntry(new ZipEntry(prefix + checksumFile.getName()));
            Files.copy(checksumFile.toPath(), zipOut);
            zipOut.closeEntry();
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
}
