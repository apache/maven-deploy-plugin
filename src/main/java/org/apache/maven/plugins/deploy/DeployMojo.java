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
import java.util.stream.Collectors;

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
     * Whether every project should be deployed during its own deploy-phase or at the end of the multimodule build.
     * When set to {@code true}, the deploy requests of all projects with a bound deploy execution are collected and
     * executed together once the last such project has reached its deploy phase, which reduces the chance of
     * publishing artifacts from a build that subsequently fails.
     * <p>
     * <strong>This is not an atomic, all-or-nothing guarantee.</strong> In particular:
     * <ul>
     *     <li>The batch fires when the last project <em>with a deploy execution</em> reaches its deploy phase.
     *     Reactor projects built after that point (for example trailing modules that skip or do not bind the
     *     deploy goal, such as integration-test aggregators) can still fail <em>after</em> all artifacts have
     *     been published.</li>
     *     <li>When the batch spans several repositories or retry configurations, the resulting requests are
     *     deployed sequentially: a failure part-way through leaves the repositories already deployed to
     *     published, with no rollback. The build log reports which repositories had already been deployed
     *     when this happens.</li>
     *     <li>Projects configured with {@code deployAtEnd=false} deploy immediately during their own deploy
     *     phase and cannot be recalled by a later build failure.</li>
     * </ul>
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
     * <p>
     * <b>Security note:</b> the credentials looked up in <code>settings.xml</code> are selected purely by the
     * <code>id</code> part, so this parameter can point credentials kept for one server at a different URL.
     * The provenance of the value matters: when the id matches a <code>settings.xml</code> server entry (stored
     * credentials) and <em>no</em> URL is on record for that id in this build, a value set from the POM (a pom
     * property or plugin configuration) is refused, while a value supplied on the command line
     * (<code>-DaltDeploymentRepository=...</code>) proceeds with a warning naming the URL the credentials will
     * be sent to.
     * When the id matches a <code>settings.xml</code> server entry and the URL differs from every URL this build
     * associates with that id, the deployment is refused unless
     * <code>-Dmaven.deploy.allowCredentialReuse=true</code> is given on the command line.
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
     *     <li>values are matched case-insensitively; any other value fails the build (fail-closed:
     *     a typo in a publish-suppression control must not silently publish)</li>
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

    private static final String PROJECTS_WITH_DEPLOY_KEY = DeployMojo.class.getName() + ".projectsWithDeploy";

    /**
     * Serializes the deploy-at-end mark-then-check-then-fire sequence across reactor threads.
     * With {@code -T}, two reactor leaves can reach their deploy phase concurrently, both record
     * their state, both see {@link #allProjectsMarked()} true, and both fire
     * {@link #deployAllAtOnce()} - double-publishing every batched module (MDEPLOY-169 ships
     * {@code -T2} + deployAtEnd as a supported configuration). All state reads/writes and the
     * batch trigger below take this monitor, so exactly one thread fires the batch; the loser
     * then observes the {@code DEPLOYED} states written by the winner and no-ops. This is a
     * constant lock object, not mutable static state; the batch state itself stays in the
     * per-project session plugin contexts.
     */
    private static final Object DEPLOY_AT_END_LOCK = new Object();

    public DeployMojo() {}

    private void putState(State state) {
        putState(project, state);
    }

    private void putState(Project project, State state) {
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
        synchronized (DEPLOY_AT_END_LOCK) {
            if (getState(project) == State.DEPLOYED) {
                // Terminal state: the project was already deployed in this session, either individually
                // or as part of an earlier deploy-at-end batch. Re-entering (a second bound deploy
                // execution, or a direct deploy:deploy invocation) must not publish it a second time.
                getLog().info("Skipping deploy for " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                        + project.getVersion() + ": it has already been deployed in this session");
                return;
            }
        }
        SkipMode skipMode = parseSkipMode(skip, "maven.deploy.skip");
        if (skipMode == SkipMode.ALL
                || (skipMode == SkipMode.RELEASES && !session.isVersionSnapshot(project.getVersion()))
                || (skipMode == SkipMode.SNAPSHOTS && session.isVersionSnapshot(project.getVersion()))) {
            getLog().info("Skipping artifact deployment");
            synchronized (DEPLOY_AT_END_LOCK) {
                putState(State.SKIPPED);
            }
        } else {
            failIfOffline();
            warnIfAffectedPackagingAndMaven(project.getPackaging().id());

            if (!deployAtEnd) {
                getLog().info("Deploying deploy for " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                        + project.getVersion() + " at end");
                deploy(createDeployerRequest());
                synchronized (DEPLOY_AT_END_LOCK) {
                    putState(State.DEPLOYED);
                }
            } else {
                // compute the request outside the lock; only the state mark-and-check is serialized
                ArtifactDeployerRequest request = createDeployerRequest();
                synchronized (DEPLOY_AT_END_LOCK) {
                    putState(State.TO_BE_DEPLOYED);
                    putState(request);
                    if (!allProjectsMarked()) {
                        getLog().info("Deferring deploy for " + project.getGroupId() + ":" + project.getArtifactId()
                                + ":" + project.getVersion() + " at end");
                    }
                }
            }
        }

        synchronized (DEPLOY_AT_END_LOCK) {
            // check-then-act must be atomic: without the lock two -T threads can both observe
            // allProjectsMarked() == true and both fire the batch. Holding the lock across
            // deployAllAtOnce() is intentional - a concurrent second trigger waits, then finds
            // every batched project already DEPLOYED and no-ops.
            if (allProjectsMarked()) {
                deployAllAtOnce();
            }
        }
    }

    private boolean allProjectsMarked() {
        return getProjectsWithDeployExecution().stream().allMatch(this::hasState);
    }

    /**
     * Returns the list of reactor projects that have a deploy execution, cached on first call.
     * The list is invariant during a build and is stored in the first reactor project's plugin
     * context to avoid recomputing it on every module invocation (O(N) total instead of O(N²)).
     */
    @SuppressWarnings("unchecked")
    private List<Project> getProjectsWithDeployExecution() {
        List<Project> allProjects = session.getProjects();
        if (allProjects.isEmpty()) {
            return List.of();
        }
        Map<String, Object> ctx = session.getPluginContext(allProjects.get(0));
        return (List<Project>) ctx.computeIfAbsent(
                PROJECTS_WITH_DEPLOY_KEY,
                k -> allProjects.stream().filter(this::hasDeployExecution).collect(Collectors.toList()));
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
        Map<RemoteRepository, Map<Integer, List<ProducedArtifact>>> flattenedRequests = new LinkedHashMap<>();
        List<Project> batchedProjects = new ArrayList<>();
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
                batchedProjects.add(reactorProject);
            }
        }
        // Re-group all requests
        List<ArtifactDeployerRequest> requests = new ArrayList<>();
        for (Map.Entry<RemoteRepository, Map<Integer, List<ProducedArtifact>>> entry1 : flattenedRequests.entrySet()) {
            for (Map.Entry<Integer, List<ProducedArtifact>> entry2 :
                    entry1.getValue().entrySet()) {
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
            // Requests are deployed sequentially and there is no rollback: if one fails, make the
            // partial-publication state explicit instead of only surfacing the failing module.
            List<String> deployedRepositoryIds = new ArrayList<>();
            for (ArtifactDeployerRequest request : requests) {
                try {
                    deploy(request);
                } catch (RuntimeException e) {
                    if (!deployedRepositoryIds.isEmpty()) {
                        getLog().error("Deploy-at-end batch failed after " + deployedRepositoryIds.size() + " of "
                                + requests.size() + " deploy request(s) had already completed. Artifacts already"
                                + " published to repository id(s) " + String.join(", ", deployedRepositoryIds)
                                + " remain published: there is no rollback.");
                    }
                    throw e;
                }
                deployedRepositoryIds.add(request.getRepository().getId());
            }
        } else {
            getLog().info("No actual deploy requests");
        }
        // Mark every batched project DEPLOYED so a re-triggered batch (second bound deploy
        // execution, or a direct deploy:deploy invocation walking the reactor) cannot publish
        // the same artifacts a second time. Only reached when all requests deployed successfully.
        for (Project reactorProject : batchedProjects) {
            putState(reactorProject, State.DEPLOYED);
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

        if (!isValidId(project.getGroupId())
                || !isValidId(project.getArtifactId())
                || !isValidVersion(project.getVersion())) {
            throw new MojoException("The project coordinates " + project.getGroupId() + ":" + project.getArtifactId()
                    + ":" + project.getVersion() + " are not valid: they use invalid characters.");
        }

        for (Artifact deployable : deployables) {
            if (!isValidClassifier(deployable.getClassifier())) {
                throw new MojoException("The classifier of attached artifact " + deployable
                        + " is not valid: uses invalid characters.");
            }
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
                .artifacts(deployables)
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
            altRepositoryFromUserProperty = isFromUserProperty("altSnapshotDeploymentRepository", altDeploymentRepo);
        } else if (!isSnapshot && altReleaseDeploymentRepository != null) {
            altDeploymentRepo = altReleaseDeploymentRepository;
            altRepositoryFromUserProperty = isFromUserProperty("altReleaseDeploymentRepository", altDeploymentRepo);
        } else {
            altDeploymentRepo = altDeploymentRepository;
            altRepositoryFromUserProperty = isFromUserProperty("altDeploymentRepository", altDeploymentRepo);
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
                    repo = createAltDeploymentRepository(id, url);
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

                    repo = createAltDeploymentRepository(id, url);
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
                    validateTransportSecurity(
                            dm.getSnapshotRepository().getId(),
                            dm.getSnapshotRepository().getUrl());
                    repo = session.createRemoteRepository(dm.getSnapshotRepository());
                } else if (dm.getRepository() != null
                        && isNotEmpty(dm.getRepository().getId())
                        && isNotEmpty(dm.getRepository().getUrl())) {
                    validateTransportSecurity(
                            dm.getRepository().getId(), dm.getRepository().getUrl());
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

    /**
     * Creates the repository for an alternative deployment target: warns when it overrides the
     * project's declared {@code distributionManagement} (naming the server id whose settings.xml
     * credentials will be used) and guards the credential binding with the provenance of the
     * alternative-repository value (see {@link #validateCredentialBinding(String, String, boolean)}):
     * a mismatch against the URLs on record for the id is refused regardless of provenance, and a
     * credentials-bearing id with no URL on record is refused when the value came from the POM but
     * proceeds with a warning when the operator typed it on the command line.
     */
    private RemoteRepository createAltDeploymentRepository(String id, String url) {
        DistributionManagement dm = project.getModel().getDistributionManagement();
        if (dm != null && (dm.getRepository() != null || dm.getSnapshotRepository() != null)) {
            getLog().warn("Alternative deployment repository overrides the distributionManagement declared by"
                    + " the project: credentials of server id '" + id
                    + "' from settings.xml (if any) will be used for " + url);
        }
        validateCredentialBinding(id, url, altRepositoryFromUserProperty);
        return createDeploymentArtifactRepository(id, url);
    }

    /**
     * Whether the alternative-repository value selected by {@link #getDeploymentRepository(boolean)}
     * was supplied as a {@code -D} session user property (operator-typed on the command line) rather
     * than resolved from the POM (a pom property or plugin configuration). POM-sourced values are
     * attacker-writable in the malicious-POM model, so they get the fail-closed treatment in
     * {@link #validateCredentialBinding(String, String, boolean)}.
     */
    private boolean altRepositoryFromUserProperty;

    /**
     * Returns {@code true} when the given user property is present in the session <em>and</em>
     * carries the value actually in use: Maven lets an explicit {@code <configuration>} entry in the
     * POM win over a {@code -D} property of the same name, so presence of the property alone does
     * not prove the value's provenance.
     */
    private boolean isFromUserProperty(String propertyName, String value) {
        if (value == null) {
            return false;
        }
        java.util.Map<String, String> userProperties = session.getUserProperties();
        return userProperties != null && value.equals(userProperties.get(propertyName));
    }

    @Override
    protected java.util.Collection<String> getKnownRepositoryUrls(String id) {
        java.util.Collection<String> urls = super.getKnownRepositoryUrls(id);
        DistributionManagement dm = project.getModel().getDistributionManagement();
        if (dm != null) {
            if (dm.getRepository() != null
                    && id.equals(dm.getRepository().getId())
                    && isNotEmpty(dm.getRepository().getUrl())) {
                urls.add(dm.getRepository().getUrl());
            }
            if (dm.getSnapshotRepository() != null
                    && id.equals(dm.getSnapshotRepository().getId())
                    && isNotEmpty(dm.getSnapshotRepository().getUrl())) {
                urls.add(dm.getSnapshotRepository().getUrl());
            }
        }
        return urls;
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
