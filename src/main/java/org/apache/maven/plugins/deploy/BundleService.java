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
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.MavenExecutionException;
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
     * This requires the "install" phase has been executed and gpg signing has been configured.
     * e.g. mvn install deploy:bundle
     *
     * @param bundleFile the zip file to create
     * @throws IOException if creating the zip file failed
     * @throws NoSuchAlgorithmException if md5, sha-1 or sha-256 algorithms are not available in
     * the environment.
     */
    public void createZipBundle(File bundleFile) throws IOException, NoSuchAlgorithmException, MavenExecutionException {
        bundleFile.getParentFile().mkdirs();
        bundleFile.createNewFile();
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId();
        String version = project.getVersion();
        String groupPath = groupId.replace('.', '/');
        String mavenPathPrefix = String.join("/", groupPath, artifactId, version) + "/";
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(bundleFile.toPath()))) {

            File artifactDir = new File(project.getBuild().getDirectory());
            File[] files = artifactDir.listFiles(
                    (dir, name) -> name.endsWith(".jar") || name.endsWith(".pom") || name.endsWith(".asc"));

            int ascCount = 0;
            if (files != null) {
                for (File file : files) {
                    zipOut.putNextEntry(new ZipEntry(mavenPathPrefix + file.getName()));
                    Files.copy(file.toPath(), zipOut);
                    zipOut.closeEntry();
                    if (file.getName().endsWith(".asc")) {
                        ascCount++;
                        continue; // No checksums for asc files
                    }
                    generateChecksumsAndAddToZip(file, mavenPathPrefix, zipOut);
                }
            }
            // This is a bit crude, but there should be sign files for pom, jar, sourceJar, javadocJar
            // unless the project is an aggregator
            int expectedArtifactCount = 4;
            if (project.getPackaging().equals("pom")) {
                expectedArtifactCount = 1;
            }
            if (ascCount != expectedArtifactCount) {
                log.warn("Expected " + expectedArtifactCount + " asc file(s) but found " + ascCount);
                if (ascCount == 0) {
                    log.error("The artifacts were not signed!");
                } else {
                    if (expectedArtifactCount == 1) {
                        log.error("There should only be one asc file for the pom");
                    } else {
                        log.error("There should be 4 signed artifacts (pom, jar, sourceJar, javadocJar)");
                    }
                }
                log.error("This bundle will not be deployable!");
                throw new MavenExecutionException(
                        "Missing sign files (asc files) detected, bundle is NOT valid", project.getFile());
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
        if (checksumFile.exists()) {
            return checksumFile;
        }

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
