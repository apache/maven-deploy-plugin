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
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

public class CentralPortalClient {

    static final String CENTRAL_PORTAL_URL = "https://central.sonatype.com/api/v1";

    private String username;
    private String password;
    private String publishUrl;
    private Log log;

    public CentralPortalClient() {
        this.publishUrl = CENTRAL_PORTAL_URL;
    }

    public void setVariables(String username, String password, String publishUrl, Log log) {
        this.username = username;
        this.password = password;
        this.publishUrl = (publishUrl != null && !publishUrl.trim().isEmpty()) ? publishUrl : CENTRAL_PORTAL_URL;
        this.log = log;
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
            log.error(deployUrl + " returned HTTP error code : " + status);
            try (InputStream in = conn.getErrorStream()) {
                String body = readFully(in);
                log.error("Response body: " + body);
            } catch (IOException e) {
                log.error("Failed to read response body", e);
            }
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
            log.error(url + " returned HTTP error code : " + status);
            try (InputStream in = conn.getErrorStream()) {
                if (in != null) {
                    String body = readFully(in);
                    log.error("Response body: " + body);
                }
            } catch (IOException e) {
                log.error("Failed to read response body", e);
            }
            throw new IOException("Failed to get status: HTTP " + status);
        }
        try (InputStream in = conn.getInputStream()) {
            if (in == null) {
                return "no response body";
            }
            String responseBody = readFully(in);
            Pattern pattern = Pattern.compile("\"deploymentState\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(responseBody);
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                return "deploymentState not found in $responseBody";
            }
        }
    }

    private String authHeader() {
        return "Bearer " + Base64.getEncoder().encodeToString((getUsername() + ":" + getPassword()).getBytes());
    }

    private String readFully(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toString("UTF-8").trim();
    }

    public void uploadAndCheck(File zipBundle, boolean autoDeploy) throws MojoExecutionException {
        String deploymentId;
        try {
            deploymentId = upload(zipBundle, autoDeploy);
            if (deploymentId == null) {
                throw new MojoExecutionException("Failed to upload bundle, no deployment id found");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to upload bundle", e);
        }

        log.info("Deployment ID: " + deploymentId);
        log.info("Waiting 10 seconds before checking status...");
        try {
            Thread.sleep(10000);
            String status = getStatus(deploymentId);

            int retries = 10;
            while (!Arrays.asList("VALIDATED", "PUBLISHING", "PUBLISHED", "FAILED")
                            .contains(status)
                    && retries-- > 0) {
                log.info("Deploy status is " + status);
                Thread.sleep(5000);
                status = getStatus(deploymentId);
            }
            switch (status) {
                case "VALIDATED":
                    log.info("Validated: the project is ready for publishing!");
                    log.info("See https://central.sonatype.com/publishing/deployments for more info");
                case "PUBLISHING":
                    log.info("Published: Project is publishing on Central!");
                    break;
                case "PUBLISHED":
                    log.info("Published successfully!");
                    break;
                default:
                    throw new MojoExecutionException("Release failed with status: " + status);
            }
        } catch (InterruptedException | IOException e) {
            throw new MojoExecutionException("Failed to check status", e);
        }
    }

    public Log getLog() {
        return log;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getPublishUrl() {
        return publishUrl;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setPublishUrl(String publishUrl) {
        this.publishUrl = publishUrl;
    }

    public void setLog(Log log) {
        this.log = log;
    }
}
