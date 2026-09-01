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

import javax.inject.Inject;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

/**
 * Deploys an artifact to remote repository.
 *
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 * @author <a href="mailto:jdcasey@apache.org">John Casey (refactoring only)</a>
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.DEPLOY, threadSafe = true)
public class DeployMojo extends AbstractDeployMojo {
    private static final Pattern ALT_LEGACY_REPO_SYNTAX_PATTERN = Pattern.compile("(.+?)::(.+?)::(.+)");

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+?)::(.+)");

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
    private List<MavenProject> reactorProjects;

    @Parameter(defaultValue = "${plugin}", required = true, readonly = true)
    private PluginDescriptor pluginDescriptor;

    /**
     * Whether every project should be deployed during its own deploy-phase or at the end of the multimodule build.
     * <p>
     * <b>Important:</b> {@code deployAtEnd} defers deployment, it does <em>not</em> make the whole batch
     * atomic. If a mid-batch failure occurs some repositories may have already received artifacts.
     * Repositories that were fully published <em>before</em> the failure remain published; there is no
     * automatic rollback.
     * <ul>
     *     <li>Projects configured with {@code deployAtEnd=false} deploy immediately during their own deploy
     *     phase and cannot be recalled by a later build failure.</li>
     * </ul>
     *
     * @since 2.8
     */
    @Parameter(defaultValue = "false", property = "deployAtEnd")
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
     * Since since 3.0.0-M2 it's not anymore a real boolean as it can have more than 2 values:
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

    @Inject
    protected DeployMojo(RuntimeInformation runtimeInformation, RepositorySystem repositorySystem) {
        super(runtimeInformation, repositorySystem);
    }

    /**
     * Monitor for deployAtEnd state operations. All check-then-fire sequences on per-project
     * state ({@code putState}, {@code allProjectsMarked}, deploy-batch trigger) must hold this
     * lock so that exactly one thread fires the batch under {@code -T}.
     */
    private static final Object DEPLOY_AT_END_LOCK = new Object();

    private enum State {
        SKIPPED,
        DEPLOYED,
        TO_BE_DEPLOYED
    }

    /**
     * Recognised values for the {@code skip} parameter. Parsed once, fail-closed on unknown input.
     */
    enum SkipMode {
        NONE,
        ALL,
        RELEASES,
        SNAPSHOTS
    }

    private static final String DEPLOY_PROCESSED_MARKER = DeployMojo.class.getName() + ".processed";

    private static final String DEPLOY_ALT_RELEASE_DEPLOYMENT_REPOSITORY =
            DeployMojo.class.getName() + ".altReleaseDeploymentRepository";

    private static final String DEPLOY_ALT_SNAPSHOT_DEPLOYMENT_REPOSITORY =
            DeployMojo.class.getName() + ".altSnapshotDeploymentRepository";

    private static final String DEPLOY_ALT_DEPLOYMENT_REPOSITORY =
            DeployMojo.class.getName() + ".altDeploymentRepository";

    private static final String PROJECTS_WITH_DEPLOY_KEY = DeployMojo.class.getName() + ".projectsWithDeploy";

    private void putState(State state) {
        getPluginContext().put(DEPLOY_PROCESSED_MARKER, state.name());
    }

    private void putPluginContextValue(String key, String value) {
        if (value != null) {
            getPluginContext().put(key, value);
        }
    }

    private String getPluginContextValue(Map<String, Object> pluginContext, String key) {
        return (String) pluginContext.get(key);
    }

    private State getState(Map<String, Object> pluginContext) {
        return State.valueOf(getPluginContextValue(pluginContext, DEPLOY_PROCESSED_MARKER));
    }

    private boolean hasState(MavenProject project) {
        Map<String, Object> pluginContext = session.getPluginContext(pluginDescriptor, project);
        return pluginContext.containsKey(DEPLOY_PROCESSED_MARKER);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        SkipMode skipMode = parseSkipMode(skip, "maven.deploy.skip");
        State state;
        if (skipMode == SkipMode.ALL
                || (skipMode == SkipMode.RELEASES && !ArtifactUtils.isSnapshot(project.getVersion()))
                || (skipMode == SkipMode.SNAPSHOTS && ArtifactUtils.isSnapshot(project.getVersion()))) {
            getLog().info("Skipping artifact deployment");
            state = State.SKIPPED;
        } else {
            failIfOffline();
            warnIfAffectedPackagingAndMaven(project.getPackaging());

            if (!deployAtEnd) {

                RemoteRepository deploymentRepository = getDeploymentRepository(
                        project,
                        altSnapshotDeploymentRepository,
                        altReleaseDeploymentRepository,
                        altDeploymentRepository);

                DeployRequest request = new DeployRequest();
                request.setRepository(deploymentRepository);
                processProject(project, request);
                deploy(request);
                state = State.DEPLOYED;
            } else {
                putPluginContextValue(DEPLOY_ALT_SNAPSHOT_DEPLOYMENT_REPOSITORY, altSnapshotDeploymentRepository);
                putPluginContextValue(DEPLOY_ALT_RELEASE_DEPLOYMENT_REPOSITORY, altReleaseDeploymentRepository);
                putPluginContextValue(DEPLOY_ALT_DEPLOYMENT_REPOSITORY, altDeploymentRepository);
                state = State.TO_BE_DEPLOYED;
            }
        }

        synchronized (DEPLOY_AT_END_LOCK) {
            putState(state);

            List<MavenProject> allProjectsUsingPlugin = getAllProjectsUsingPlugin();

            if (allProjectsMarked(allProjectsUsingPlugin)) {
                deployAllAtOnce(allProjectsUsingPlugin);
            } else if (state == State.TO_BE_DEPLOYED) {
                getLog().info("Deferring deploy for " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                        + project.getVersion() + " at end");
            }
        }
    }

    private void deployAllAtOnce(List<MavenProject> allProjectsUsingPlugin) throws MojoExecutionException {
        Map<RemoteRepository, DeployRequest> requests = new LinkedHashMap<>();

        // collect all artifacts from all modules to deploy
        // requests are grouped by used remote repository
        for (MavenProject reactorProject : allProjectsUsingPlugin) {
            Map<String, Object> pluginContext = session.getPluginContext(pluginDescriptor, reactorProject);
            State state = getState(pluginContext);
            if (state == State.DEPLOYED) {
                // already deployed (e.g. by a parallel thread under -T or by a previous
                // reactor pass) — do not re-deploy
                getLog().info("Skipping already-deployed " + reactorProject.getGroupId() + ":"
                        + reactorProject.getArtifactId() + ":" + reactorProject.getVersion());
                continue;
            }
            if (state == State.TO_BE_DEPLOYED) {

                RemoteRepository deploymentRepository = getDeploymentRepository(
                        reactorProject,
                        getPluginContextValue(pluginContext, DEPLOY_ALT_SNAPSHOT_DEPLOYMENT_REPOSITORY),
                        getPluginContextValue(pluginContext, DEPLOY_ALT_RELEASE_DEPLOYMENT_REPOSITORY),
                        getPluginContextValue(pluginContext, DEPLOY_ALT_DEPLOYMENT_REPOSITORY));

                DeployRequest request = requests.computeIfAbsent(deploymentRepository, repo -> {
                    DeployRequest newRequest = new DeployRequest();
                    newRequest.setRepository(repo);
                    return newRequest;
                });
                processProject(reactorProject, request);
            }
        }
        // finally execute all deployments request, lets resolver to optimize deployment
        for (DeployRequest request : requests.values()) {
            deploy(request);
        }
        // mark all deployed projects to prevent re-deploy on reactor re-entry
        for (MavenProject reactorProject : allProjectsUsingPlugin) {
            Map<String, Object> pluginContext = session.getPluginContext(pluginDescriptor, reactorProject);
            State state = getState(pluginContext);
            if (state == State.TO_BE_DEPLOYED) {
                pluginContext.put(DEPLOY_PROCESSED_MARKER, State.DEPLOYED.name());
            }
        }
    }

    /**
     * Returns the list of reactor projects that have a deploy execution, cached on first call.
     * The list is invariant during a build and is stored in the first reactor project's plugin
     * context to avoid recomputing it on every module invocation (O(N) total instead of O(N²)).
     */
    @SuppressWarnings("unchecked")
    private List<MavenProject> getProjectsWithDeployExecution() {
        if (reactorProjects.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> pluginContext = session.getPluginContext(pluginDescriptor, reactorProjects.get(0));
        return (List<MavenProject>) pluginContext.computeIfAbsent(
                PROJECTS_WITH_DEPLOY_KEY,
                k -> reactorProjects.stream().filter(this::hasDeployExecution).collect(Collectors.toList()));
    }

    private boolean allProjectsMarked(List<MavenProject> allProjectsUsingPlugin) {
        for (MavenProject reactorProject : allProjectsUsingPlugin) {
            if (!hasState(reactorProject)) {
                return false;
            }
        }
        return true;
    }

    private List<MavenProject> getAllProjectsUsingPlugin() {
        return getProjectsWithDeployExecution();
    }

    private boolean hasDeployExecution(MavenProject project) {
        return hasExecution(project.getPlugin("org.apache.maven.plugins:maven-deploy-plugin"));
    }

    private boolean hasExecution(Plugin plugin) {
        if (plugin == null) {
            return false;
        }

        for (PluginExecution execution : plugin.getExecutions()) {
            if (!execution.getGoals().isEmpty() && !"none".equalsIgnoreCase(execution.getPhase())) {
                return true;
            }
        }
        return false;
    }

    private void processProject(final MavenProject project, DeployRequest request) throws MojoExecutionException {
        // always exists, as project exists
        Artifact pomArtifact = RepositoryUtils.toArtifact(new ProjectArtifact(project));
        // always exists, but at "init" is w/o file (packaging plugin assigns file to this when packaged)
        Artifact projectArtifact = RepositoryUtils.toArtifact(project.getArtifact());

        // pom project: pomArtifact and projectArtifact are SAME
        // jar project: pomArtifact and projectArtifact are DIFFERENT
        // incomplete project: is not pom project and projectArtifact has no file

        // we must compare coordinates ONLY (as projectArtifact may not have file, and Artifact.equals factors it in)
        // BUT if projectArtifact has file set, use that one
        if (ArtifactIdUtils.equalsId(pomArtifact, projectArtifact)) {
            if (isFile(projectArtifact.getFile())) {
                pomArtifact = projectArtifact;
            }
            projectArtifact = null;
        }

        if (isFile(pomArtifact.getFile())) {
            request.addArtifact(pomArtifact);
        } else {
            throw new MojoExecutionException(
                    "The POM for project " + project.getArtifactId() + " could not be attached");
        }

        // is not packaged, is "incomplete"
        boolean isIncomplete = projectArtifact != null && !isFile(projectArtifact.getFile());
        if (projectArtifact != null) {
            if (!isIncomplete) {
                request.addArtifact(projectArtifact);
            } else if (!project.getAttachedArtifacts().isEmpty()) {
                if (allowIncompleteProjects) {
                    getLog().warn("");
                    getLog().warn("The packaging plugin for project " + project.getArtifactId() + " did not assign");
                    getLog().warn("a main file to the project but it has attachments. Change packaging to 'pom'.");
                    getLog().warn("");
                    getLog().warn("Incomplete projects like this will fail in future Maven versions!");
                    getLog().warn("");
                } else {
                    throw new MojoExecutionException("The packaging plugin for project " + project.getArtifactId()
                            + " did not assign a main file to the project but it has attachments. Change packaging"
                            + " to 'pom'.");
                }
            } else {
                throw new MojoExecutionException("The packaging plugin for project " + project.getArtifactId()
                        + " did not assign a file to the build artifact");
            }
        }

        for (org.apache.maven.artifact.Artifact attached : project.getAttachedArtifacts()) {
            getLog().debug("Attaching for deploy: " + attached.getId());
            request.addArtifact(RepositoryUtils.toArtifact(attached));
        }
    }

    private boolean isFile(File file) {
        return file != null && file.isFile();
    }

    /**
     * Visible for testing.
     */
    RemoteRepository getDeploymentRepository(
            final MavenProject project,
            final String altSnapshotDeploymentRepository,
            final String altReleaseDeploymentRepository,
            final String altDeploymentRepository)
            throws MojoExecutionException {
        RemoteRepository repo = null;

        String altDeploymentRepo;
        if (ArtifactUtils.isSnapshot(project.getVersion()) && altSnapshotDeploymentRepository != null) {
            altDeploymentRepo = altSnapshotDeploymentRepository;
        } else if (!ArtifactUtils.isSnapshot(project.getVersion()) && altReleaseDeploymentRepository != null) {
            altDeploymentRepo = altReleaseDeploymentRepository;
        } else if (!ArtifactUtils.isSnapshot(project.getVersion())
                && altSnapshotDeploymentRepository != null
                && altDeploymentRepository == null) {
            // f008: release project, only snapshotRepo configured, no fallback → warn
            getLog().warn("Project " + project.getArtifactId()
                    + " is a release but only altSnapshotDeploymentRepository is configured ("
                    + redactUrlUserInfo(altSnapshotDeploymentRepository) + "); it will not be used."
                    + " Configure altReleaseDeploymentRepository or altDeploymentRepository.");
            altDeploymentRepo = null;
        } else {
            altDeploymentRepo = altDeploymentRepository;
        }

        if (altDeploymentRepo != null) {
            getLog().info("Using alternate deployment repository " + redactUrlUserInfo(altDeploymentRepo));

            Matcher matcher = ALT_LEGACY_REPO_SYNTAX_PATTERN.matcher(altDeploymentRepo);

            if (matcher.matches()) {
                String id = matcher.group(1).trim();
                String layout = matcher.group(2).trim();
                String url = matcher.group(3).trim();

                if (id.isEmpty() || url.isEmpty()) {
                    throw new MojoExecutionException("Invalid alternative repository: id and url must not be empty"
                            + " in \"" + redactUrlUserInfo(altDeploymentRepo) + "\". Use \"id::url\".");
                }

                if ("default".equals(layout)) {
                    getLog().warn("Using legacy syntax for alternative repository. " + "Use \"" + id + "::" + url
                            + "\" instead.");
                    repo = getRemoteRepository(id, url);
                } else {
                    throw new MojoExecutionException("Invalid legacy syntax and layout for alternative repository: \""
                            + redactUrlUserInfo(altDeploymentRepo) + "\". Use \"" + id + "::" + url
                            + "\" instead, and only default layout is supported.");
                }
            } else {
                matcher = ALT_REPO_SYNTAX_PATTERN.matcher(altDeploymentRepo);

                if (!matcher.matches()) {
                    throw new MojoExecutionException("Invalid syntax for alternative repository: \""
                            + redactUrlUserInfo(altDeploymentRepo)
                            + "\". Use \"id::url\".");
                } else {
                    String id = matcher.group(1).trim();
                    String url = matcher.group(2).trim();

                    if (id.isEmpty() || url.isEmpty()) {
                        throw new MojoExecutionException("Invalid alternative repository: id and url must not be empty"
                                + " in \"" + redactUrlUserInfo(altDeploymentRepo) + "\". Use \"id::url\".");
                    }

                    repo = getRemoteRepository(id, url);
                }
            }
        }

        if (repo == null) {
            repo = RepositoryUtils.toRepo(project.getDistributionManagementArtifactRepository());
        }

        if (repo == null) {
            String msg = "Deployment failed: repository element was not specified in the POM inside"
                    + " distributionManagement element or in -DaltDeploymentRepository=id::url parameter";

            throw new MojoExecutionException(msg);
        }

        return repo;
    }

    /**
     * Parses the {@code skip} parameter into a {@link SkipMode}, fail-closed: unrecognised values are refused
     * rather than silently treated as "false" (which would deploy when the operator intended to suppress).
     */
    static SkipMode parseSkipMode(String skip, String propertyName) throws MojoExecutionException {
        if (skip == null) {
            return SkipMode.NONE;
        }
        String v = skip.trim();
        if (v.isEmpty() || "false".equalsIgnoreCase(v)) {
            return SkipMode.NONE;
        }
        if ("true".equalsIgnoreCase(v)) {
            return SkipMode.ALL;
        }
        if ("releases".equalsIgnoreCase(v)) {
            return SkipMode.RELEASES;
        }
        if ("snapshots".equalsIgnoreCase(v)) {
            return SkipMode.SNAPSHOTS;
        }
        throw new MojoExecutionException("Unrecognized value '" + v + "' for " + propertyName
                + ". Accepted values: true, false, releases, snapshots.");
    }

    /**
     * Replaces any {@code userinfo@} section in URLs embedded in the string with {@code ***@}.
     */
    static String redactUrlUserInfo(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("(://)[^/@]+@", "$1***@");
    }
}
