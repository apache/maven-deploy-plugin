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

import java.io.ByteArrayOutputStream;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CentralPortalClient {

    static final String CENTRAL_PORTAL_URL = "https://central.sonatype.com/api/v1";

    private final String username;
    private final String password;
    private final String publishUrl;

    public CentralPortalClient(String username, String password, String publishUrl) {
        this.username = username;
        this.password = password;
        this.publishUrl = (publishUrl != null && !publishUrl.trim().isEmpty()) ? publishUrl : CENTRAL_PORTAL_URL;
        // System.out.println("Publish to Central Portal using url: " + publishUrl);
    }

    public String upload(File bundle, Boolean autoDeploy) throws IOException {
        String boundary = "----MavenCentralBoundary" + System.currentTimeMillis();
        String deployUrl = publishUrl + "/publisher/upload?name=" + bundle.getName();
        if (autoDeploy) {
            deployUrl += "&publishingType=AUTOMATIC";
        } else {
            deployUrl += "&publishingType=USER_MANAGED";
        }
        URL url = new URL(deployUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(true);
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
        if (status >= 400) {
            throw new IOException("Failed to upload: HTTP " + status);
        }

        try (InputStream in = conn.getInputStream()) {
            return readFully(in);
        }
    }

    /**
     * Query central for the deployment status of the deployment identified with the deploymentId
     * that was sent when the bundle was uploaded.
     * Example response:
     * <pre>{@code
     * {
     *   "deploymentId": "28570f16-da32-4c14-bd2e-c1acc0782365",
     *   "deploymentName": "central-bundle.zip",
     *   "deploymentState": "PUBLISHED",
     *   "purls": [
     *     "pkg:maven/com.sonatype.central.example/example_java_project@0.0.7"
     *   ]
     * }
     * }</pre>
     * @param deploymentId the identifier from the upload step
     * @return the deploymentState part of the response
     * @throws IOException if the connection could not be established
     */
    public String getStatus(String deploymentId) throws IOException {
        URL url = new URL(publishUrl + "/publisher/status?id=" + deploymentId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", authHeader());
        int status = conn.getResponseCode();
        if (status >= 400) {
            throw new IOException("Failed to get status: HTTP " + status);
        }
        try (InputStream in = conn.getInputStream()) {
            String responseBody = readFully(in);
            Pattern pattern = Pattern.compile("\"deploymentState\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(responseBody);
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                return "deploymentState not found";
            }
        }
    }

    private String authHeader() {
        return "Bearer " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    private String readFully(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toString("UTF-8");
    }
}
