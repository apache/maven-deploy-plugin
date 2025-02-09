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

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.MojoExecution;
import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.model.DistributionManagement;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.PluginExecution;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactDeployer;
import org.apache.maven.api.services.ArtifactDeployerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectManager;

/**
 * Deploys an artifact to remote repository.
 *
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 * @author <a href="mailto:jdcasey@apache.org">John Casey (refactoring only)</a>
 */
@Mojo(name = "deploy", defaultPhase = "deploy")
public class DeployMojo extends AbstractDeployMojo {
    private static final Pattern ALT_LEGACY_REPO_SYNTAX_PATTERN = Pattern.compile("(.+?)::(.+?)::(.+)");

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+?)::(.+)");

    @Inject
    private Project project;

    @Inject
    private MojoExecution mojoExecution;

    /**
     * Whether every project should be deployed during its own deploy-phase or at the end of the multimodule build. If
     * set to {@code true} and the build fails, none of the reactor projects is deployed.
     * <strong>(experimental)</strong>
     *
     * @since 2.8
     */
    @Parameter(defaultValue = "true", property = "deployAtEnd")
    private boolean deployAtEnd;

    /**
     * Specifies an alternative repository to which the project artifacts should be deployed (other than those specified
     * in &lt;distributionManagement&gt;). <br/>
     * Format: <code>id::url</code>
     * <dl>
     * <dt>id</dt>
     * <dd>The id can be used to pick up the correct credentials from the settings.xml</dd>
     * <dt>url</dt>
     * <dd>The location of the repository</dd>
     * </dl>
     * <b>Note:</b> In version 2.x, the format was <code>id::<i>layout</i>::url</code> where <code><i>layout</i></code>
     * could be <code>default</code> (ie. Maven 2) or <code>legacy</code> (ie. Maven 1), but since 3.0.0 the layout part
     * has been removed because Maven 3 only supports Maven 2 repository layout.
     */
    @Parameter(property = "altDeploymentRepository")
    private String altDeploymentRepository;

    /**
     * The alternative repository to use when the project has a snapshot version.
     *
     * <b>Note:</b> In version 2.x, the format was <code>id::<i>layout</i>::url</code> where <code><i>layout</i></code>
     * could be <code>default</code> (ie. Maven 2) or <code>legacy</code> (ie. Maven 1), but since 3.0.0 the layout part
     * has been removed because Maven 3 only supports Maven 2 repository layout.
     * @since 2.8
     * @see DeployMojo#altDeploymentRepository
     */
    @Parameter(property = "altSnapshotDeploymentRepository")
    private String altSnapshotDeploymentRepository;

    /**
     * The alternative repository to use when the project has a final version.
     *
     * <b>Note:</b> In version 2.x, the format was <code>id::<i>layout</i>::url</code> where <code><i>layout</i></code>
     * could be <code>default</code> (ie. Maven 2) or <code>legacy</code> (ie. Maven 1), but since 3.0.0 the layout part
     * has been removed because Maven 3 only supports Maven 2 repository layout.
     * @since 2.8
     * @see DeployMojo#altDeploymentRepository
     */
    @Parameter(property = "altReleaseDeploymentRepository")
    private String altReleaseDeploymentRepository;

    /**
     * Set this to 'true' to bypass artifact deploy
     * Since 3.0.0-M2 it's not anymore a real boolean as it can have more than 2 values:
     * <ul>
     *     <li><code>true</code>: will skip as usual</li>
     *     <li><code>releases</code>: will skip if current version of the project is a release</li>
     *     <li><code>snapshots</code>: will skip if current version of the project is a snapshot</li>
     *     <li>any other values will be considered as <code>false</code></li>
     * </ul>
     * @since 2.4
     */
    @Parameter(property = "maven.deploy.skip", defaultValue = "false")
    private String skip = Boolean.FALSE.toString();

    /**
     * Set this to <code>true</code> to allow incomplete project processing. By default, such projects are forbidden
     * and Mojo will fail to process them. Incomplete project is a Maven Project that has any other packaging than
     * "pom" and has no main artifact packaged. In the majority of cases, what user really wants here is a project
     * with "pom" packaging and some classified artifact attached (typical example is some assembly being packaged
     * and attached with classifier).
     *
     * @since 3.1.1
     */
    @Parameter(defaultValue = "false", property = "allowIncompleteProjects")
    private boolean allowIncompleteProjects;

    private enum State {
        SKIPPED,
        DEPLOYED,
        TO_BE_DEPLOYED
    }

    public DeployMojo() {}

    private void putState(State state) {
        session.getPluginContext(project).put(State.class.getName(), state);
    }

    private void putState(ArtifactDeployerRequest request) {
        session.getPluginContext(project).put(ArtifactDeployerRequest.class.getName(), request);
    }

    private State getState(Project project) {
        return (State) session.getPluginContext(project).get(State.class.getName());
    }

    private boolean hasState(Project project) {
        return session.getPluginContext(project).containsKey(State.class.getName());
    }

    public void execute() {
        if (Boolean.parseBoolean(skip)
                || ("releases".equals(skip) && !session.isVersionSnapshot(project.getVersion()))
                || ("snapshots".equals(skip) && session.isVersionSnapshot(project.getVersion()))) {
            getLog().info("Skipping artifact deployment");
            putState(State.SKIPPED);
        } else {
            failIfOffline();
            warnIfAffectedPackagingAndMaven(project.getPackaging().id());

            if (!deployAtEnd) {
                getLog().info("Deploying deploy for " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                        + project.getVersion() + " at end");
                deploy(createDeployerRequest());
                putState(State.DEPLOYED);
            } else {
                // compute the request
                putState(State.TO_BE_DEPLOYED);
                putState(createDeployerRequest());
                if (!allProjectsMarked()) {
                    getLog().info("Deferring deploy for " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                            + project.getVersion() + " at end");
                }
            }
        }

        if (allProjectsMarked()) {
            deployAllAtOnce();
        }
    }

    private boolean allProjectsMarked() {
        return session.getProjects().stream().allMatch(p -> hasState(p) || !hasDeployExecution(p));
    }

    private boolean hasDeployExecution(Project p) {
        String key = mojoExecution.getPlugin().getModel().getKey();
        Plugin plugin = p.getBuild().getPluginsAsMap().get(key);
        if (plugin != null) {
            for (PluginExecution execution : plugin.getExecutions()) {
                if (!execution.getGoals().isEmpty() && !"none".equalsIgnoreCase(execution.getPhase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void deployAllAtOnce() {
        Map<RemoteRepository, Map<Integer, List<Artifact>>> flattenedRequests = new LinkedHashMap<>();
        // flatten requests, grouping by remote repository and number of retries
        for (Project reactorProject : session.getProjects()) {
            State state = getState(reactorProject);
            if (state == State.TO_BE_DEPLOYED) {
                ArtifactDeployerRequest request = (ArtifactDeployerRequest)
                        session.getPluginContext(reactorProject).get(ArtifactDeployerRequest.class.getName());
                flattenedRequests
                        .computeIfAbsent(request.getRepository(), r -> new LinkedHashMap<>())
                        .computeIfAbsent(request.getRetryFailedDeploymentCount(), i -> new ArrayList<>())
                        .addAll(request.getArtifacts());
            }
        }
        // Re-group all requests
        List<ArtifactDeployerRequest> requests = new ArrayList<>();
        for (Map.Entry<RemoteRepository, Map<Integer, List<Artifact>>> entry1 : flattenedRequests.entrySet()) {
            for (Map.Entry<Integer, List<Artifact>> entry2 : entry1.getValue().entrySet()) {
                requests.add(ArtifactDeployerRequest.builder()
                        .session(session)
                        .repository(entry1.getKey())
                        .retryFailedDeploymentCount(entry2.getKey())
                        .artifacts(entry2.getValue())
                        .build());
            }
        }
        // Deploy
        if (!requests.isEmpty()) {
            requests.forEach(this::deploy);
        } else {
            getLog().info("No actual deploy requests");
        }
    }

    private void deploy(ArtifactDeployerRequest request) {
        try {
            getLog().info("Deploying artifacts " + request.getArtifacts().toString() + " to repository "
                    + request.getRepository());
            getArtifactDeployer().deploy(request);
        } catch (MojoException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoException(e.getMessage(), e);
        }
    }

    private ArtifactDeployerRequest createDeployerRequest() {
        ProjectManager projectManager = getProjectManager();
        Collection<ProducedArtifact> deployables = projectManager.getAllArtifacts(project);
        Collection<ProducedArtifact> attachedArtifacts = projectManager.getAttachedArtifacts(project);

        ArtifactManager artifactManager = getArtifactManager();
        if (artifactManager.getPath(project.getPomArtifact()).isEmpty()) {
            artifactManager.setPath(project.getPomArtifact(), project.getPomPath());
        }

        for (Artifact deployable : deployables) {
            if (!isValidPath(deployable)) {
                if (deployable == project.getMainArtifact().orElse(null)) {
                    if (attachedArtifacts.isEmpty()) {
                        throw new MojoException(
                                "The packaging for this project did not assign a file to the build artifact");
                    } else {
                        if (allowIncompleteProjects) {
                            getLog().warn("");
                            getLog().warn("The packaging plugin for this project did not assign");
                            getLog().warn(
                                            "a main file to the project but it has attachments. Change packaging to 'pom'.");
                            getLog().warn("");
                            getLog().warn("Incomplete projects like this will fail in future Maven versions!");
                            getLog().warn("");
                        } else {
                            throw new MojoException("The packaging plugin for this project did not assign "
                                    + "a main file to the project but it has attachments. Change packaging to 'pom'.");
                        }
                    }
                } else {
                    throw new MojoException("The packaging for this project did not assign "
                            + "a file to the attached artifact: " + deployable);
                }
            }
        }

        ArtifactDeployerRequest request = ArtifactDeployerRequest.builder()
                .session(session)
                .repository(getDeploymentRepository(session.isVersionSnapshot(project.getVersion())))
                .artifacts((Collection) deployables)
                .retryFailedDeploymentCount(Math.max(1, Math.min(10, getRetryFailedDeploymentCount())))
                .build();

        return request;
    }

    /**
     * Visible for testing.
     */
    RemoteRepository getDeploymentRepository(boolean isSnapshot) throws MojoException {
        RemoteRepository repo = null;

        String altDeploymentRepo;
        if (isSnapshot && altSnapshotDeploymentRepository != null) {
            altDeploymentRepo = altSnapshotDeploymentRepository;
        } else if (!isSnapshot && altReleaseDeploymentRepository != null) {
            altDeploymentRepo = altReleaseDeploymentRepository;
        } else {
            altDeploymentRepo = altDeploymentRepository;
        }

        if (altDeploymentRepo != null) {
            getLog().info("Using alternate deployment repository " + altDeploymentRepo);

            Matcher matcher = ALT_LEGACY_REPO_SYNTAX_PATTERN.matcher(altDeploymentRepo);

            if (matcher.matches()) {
                String id = matcher.group(1).trim();
                String layout = matcher.group(2).trim();
                String url = matcher.group(3).trim();

                if ("default".equals(layout)) {
                    getLog().warn("Using legacy syntax for alternative repository. " + "Use \"" + id + "::" + url
                            + "\" instead.");
                    repo = createDeploymentArtifactRepository(id, url);
                } else {
                    throw new MojoException(
                            altDeploymentRepo,
                            "Invalid legacy syntax and layout for repository.",
                            "Invalid legacy syntax and layout for alternative repository. Use \"" + id + "::" + url
                                    + "\" instead, and only default layout is supported.");
                }
            } else {
                matcher = ALT_REPO_SYNTAX_PATTERN.matcher(altDeploymentRepo);

                if (!matcher.matches()) {
                    throw new MojoException(
                            altDeploymentRepo,
                            "Invalid syntax for repository.",
                            "Invalid syntax for alternative repository. Use \"id::url\".");
                } else {
                    String id = matcher.group(1).trim();
                    String url = matcher.group(2).trim();

                    repo = createDeploymentArtifactRepository(id, url);
                }
            }
        }

        if (repo == null) {
            DistributionManagement dm = project.getModel().getDistributionManagement();
            if (dm != null) {
                if (isSnapshot
                        && dm.getSnapshotRepository() != null
                        && isNotEmpty(dm.getSnapshotRepository().getId())
                        && isNotEmpty(dm.getSnapshotRepository().getUrl())) {
                    repo = session.createRemoteRepository(dm.getSnapshotRepository());
                } else if (dm.getRepository() != null
                        && isNotEmpty(dm.getRepository().getId())
                        && isNotEmpty(dm.getRepository().getUrl())) {
                    repo = session.createRemoteRepository(dm.getRepository());
                }
            }
        }

        if (repo == null) {
            String msg = "Deployment failed: repository element was not specified in the POM inside"
                    + " distributionManagement element or in -DaltDeploymentRepository=id::url parameter";

            throw new MojoException(msg);
        }

        return repo;
    }

    private boolean isValidPath(Artifact a) {
        return getArtifactManager().getPath(a).filter(Files::isRegularFile).isPresent();
    }

    private static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    private ArtifactDeployer getArtifactDeployer() {
        return session.getService(ArtifactDeployer.class);
    }

    private ArtifactManager getArtifactManager() {
        return session.getService(ArtifactManager.class);
    }

    private ProjectManager getProjectManager() {
        return session.getService(ProjectManager.class);
    }
}
