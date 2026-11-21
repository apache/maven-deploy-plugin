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

import org.apache.maven.api.Artifact;

/**
 * Selects attached artifacts of a project by their coordinates. Every element that is set must be equal to the
 * corresponding coordinate of the attached artifact; an element that is not set matches any value. At least one
 * element must be set.
 *
 * @since 4.0.0-beta-4
 */
public class AttachedArtifact {

    private String groupId;

    private String artifactId;

    private String classifier;

    private String extension;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    /**
     * @return {@code true} if no element is set, in which case the selector would match every attached artifact
     */
    boolean isEmpty() {
        return isBlank(groupId) && isBlank(artifactId) && isBlank(classifier) && isBlank(extension);
    }

    /**
     * The consumer POM that Maven 4 attaches to every project is part of the POM, not an attachment of the build:
     * it is never selected.
     */
    static boolean isConsumerPom(Artifact artifact) {
        return "consumer".equals(artifact.getClassifier()) && "pom".equals(artifact.getExtension());
    }

    boolean matches(Artifact artifact) {
        return !isConsumerPom(artifact)
                && matches(groupId, artifact.getGroupId())
                && matches(artifactId, artifact.getArtifactId())
                && matches(classifier, artifact.getClassifier())
                && matches(extension, artifact.getExtension());
    }

    private static boolean matches(String expected, String actual) {
        return isBlank(expected) || expected.trim().equals(actual);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "groupId=" + any(groupId) + ", artifactId=" + any(artifactId) + ", classifier=" + any(classifier)
                + ", extension=" + any(extension);
    }

    private static String any(String value) {
        return isBlank(value) ? "*" : value.trim();
    }
}
