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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Base64;

public class CentralPortalClient {

    static final String CENTRAL_PORTAL_URL = "https://central.sonatype.com/api/v1";

    private final String username;
    private final String password;
    private final String publishUrl;

    public CentralPortalClient(String username, String password, String publishUrl) {
        this.username = username;
        this.password = password;
        this.publishUrl = (publishUrl != null && !publishUrl.isBlank()) ? publishUrl : CENTRAL_PORTAL_URL;
    }

    public String upload(File bundle) throws IOException {
        String boundary = "----MavenCentralBoundary" + System.currentTimeMillis();
        URL url = new URL(publishUrl + "/publisher/upload?publishingType=AUTOMATIC");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", authHeader());
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream out = conn.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(out))) {
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"bundle\"; filename=\"")
                    .append(bundle.getName())
                    .append("\"\r\n");
            writer.append("Content-Type: application/zip\r\n\r\n").flush();
            Files.copy(bundle.toPath(), out);
            out.flush();
            writer.append("\r\n--").append(boundary).append("--\r\n").flush();
        }

        int status = conn.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to upload: HTTP " + status);
        }

        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes());
        }
    }

    public String getStatus(String deploymentId) throws IOException {
        URL url = new URL(publishUrl + "/publisher/status?id=" + deploymentId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", authHeader());

        int status = conn.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to get status: HTTP " + status);
        }

        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes());
        }
    }

    private String authHeader() {
        return "Bearer " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }
}
