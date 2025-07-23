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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.Log;

public class BundleService {

    Project project;
    Session session;
    Log log;

    public BundleService(Project project, Session session, Log log) {
        this.project = project;
        this.session = session;
        this.log = log;
    }

    static final List<String> CHECKSUM_ALGOS = List.of("MD5", "SHA-1", "SHA-256");

    public void createZipBundle(File bundleFile) throws IOException, NoSuchAlgorithmException {
        List<Project> projects = session.getProjects();
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(bundleFile))) {
            for (Project subproject : projects) {
                File artifactDir = new File(subproject.getBuild().getDirectory());
                File[] files = artifactDir.listFiles(
                        (dir, name) -> name.endsWith(".jar") || name.endsWith(".pom") || name.endsWith(".asc"));

                if (files != null) {
                    for (File file : files) {
                        zipOut.putNextEntry(new ZipEntry(subproject.getArtifactId() + "/" + file.getName()));
                        Files.copy(file.toPath(), zipOut);
                        zipOut.closeEntry();

                        for (String algo : CHECKSUM_ALGOS) {
                            File checksumFile = generateChecksum(file, algo);
                            zipOut.putNextEntry(
                                    new ZipEntry(subproject.getArtifactId() + "/" + checksumFile.getName()));
                            Files.copy(checksumFile.toPath(), zipOut);
                            zipOut.closeEntry();
                        }
                    }
                }
            }
        }
        log.info("Created bundle at: " + bundleFile.getAbsolutePath());
    }

    private File generateChecksum(File file, String algo) throws NoSuchAlgorithmException, IOException {
        String extension = algo.toLowerCase().replace("-", "");
        File checksumFile = new File(file.getAbsolutePath() + "." + extension);
        if (checksumFile.exists()) {
            return checksumFile;
        }

        MessageDigest digest = MessageDigest.getInstance(algo);
        try (InputStream is = new FileInputStream(file);
                DigestOutputStream dos = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
            is.transferTo(dos);
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        Files.writeString(checksumFile.toPath(), sb.toString());
        return checksumFile;
    }
}
