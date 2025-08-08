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
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.apache.maven.settings.Server;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
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
     * Whether every project should be deployed during its own deploy-phase or at the end of the multimodule build. If
     * set to {@code true} and the build fails, none of the reactor projects is deployed.
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
     * Since since 3.0.0-M2 it's not anymore a real boolean as it can have more than 2 values:
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
     * If <code>false</code>, the deploy plugin will use the legacy deployment api.
     * If <code>true</code>, the new central portal api will be used.
     * Default is <code>false</code>.
     * @since 3.1.5
     */
    @Parameter(property = "useCentralPortalApi", defaultValue = "false")
    private boolean useCentralPortalApi;

    /**
     * If this is set to false, the bundle will be uploaded to central but not released (published).
     * You can release it manually at <a href="https://central.sonatype.com/publishing/deployments">
     * central deployments</a>. If true, the bundle will be uploaded, validated and then
     * automatically released if it is a valid deployment bundle.
     * Default is <code>true</code> i.e. upload and release automatically.
     * @since 3.1.5
     */
    @Parameter(defaultValue = "true", property = "autoDeploy")
    private boolean autoDeploy;

    /**
     * Set this to <code>false</code> to create the bundle but not upload it to central.
     * This is useful if e.g. you want to check it and then manually upload the bundle.
     * Default is <code>true</code> (i.e. upload it).
     * @since 3.1.5
     */
    @Parameter(defaultValue = "true", property = "uploadToCentral")
    private boolean uploadToCentral;

    @Inject
    private SettingsDecrypter settingsDecrypter;

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

    private static final String DEPLOY_PROCESSED_MARKER = DeployMojo.class.getName() + ".processed";

    private static final String DEPLOY_ALT_RELEASE_DEPLOYMENT_REPOSITORY =
            DeployMojo.class.getName() + ".altReleaseDeploymentRepository";

    private static final String DEPLOY_ALT_SNAPSHOT_DEPLOYMENT_REPOSITORY =
            DeployMojo.class.getName() + ".altSnapshotDeploymentRepository";

    private static final String DEPLOY_ALT_DEPLOYMENT_REPOSITORY =
            DeployMojo.class.getName() + ".altDeploymentRepository";

    // Make it a member variable to allow test to mock central portal client
    private CentralPortalClient centralPortalClient = new CentralPortalClient();

    private void putState(State state) {
        getLog().info("putState: pluginContext@" + getPluginContext().hashCode() + " putting " + DEPLOY_PROCESSED_MARKER
                + "=" + state.name());
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
        State state;
        if (Boolean.parseBoolean(skip)
                || ("releases".equals(skip) && !ArtifactUtils.isSnapshot(project.getVersion()))
                || ("snapshots".equals(skip) && ArtifactUtils.isSnapshot(project.getVersion()))) {
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

                if (useCentralPortalApi) {
                    createAndDeploySingleProjectBundle(deploymentRepository);
                } else {
                    DeployRequest request = new DeployRequest();
                    request.setRepository(deploymentRepository);
                    processProject(project, request);
                    deploy(request);
                }
                state = State.DEPLOYED;
            } else {
                putPluginContextValue(DEPLOY_ALT_SNAPSHOT_DEPLOYMENT_REPOSITORY, altSnapshotDeploymentRepository);
                putPluginContextValue(DEPLOY_ALT_RELEASE_DEPLOYMENT_REPOSITORY, altReleaseDeploymentRepository);
                putPluginContextValue(DEPLOY_ALT_DEPLOYMENT_REPOSITORY, altDeploymentRepository);
                state = State.TO_BE_DEPLOYED;
            }
        }

        getLog().info("Setting state to " + state.name() + " for " + project.getArtifactId());
        putState(state);

        List<MavenProject> allProjectsUsingPlugin = getAllProjectsUsingPlugin();

        if (allProjectsMarked(allProjectsUsingPlugin)) {
            deployAllAtOnce(allProjectsUsingPlugin);
        } else if (state == State.TO_BE_DEPLOYED) {
            getLog().info("Deferring deploy for " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                    + project.getVersion() + " at end");
        }
    }

    private void deployAllAtOnce(List<MavenProject> allProjectsUsingPlugin) throws MojoExecutionException {
        Map<RemoteRepository, DeployRequest> requests = new LinkedHashMap<>();

        // collect all artifacts from all modules to deploy
        // requests are grouped by used remote repository
        for (MavenProject reactorProject : allProjectsUsingPlugin) {
            Map<String, Object> pluginContext = session.getPluginContext(pluginDescriptor, reactorProject);
            State state = getState(pluginContext);
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
        if (useCentralPortalApi && deployAtEnd) {
            File zipBundle = createBundle(allProjectsUsingPlugin);
            if (uploadToCentral) {
                deployBundle(requests.keySet(), zipBundle);
            }
        } else {
            // finally execute all deployments request, lets resolver to optimize deployment
            for (DeployRequest request : requests.values()) {
                deploy(request);
            }
        }
    }

    private boolean allProjectsMarked(List<MavenProject> allProjectsUsingPlugin) {
        for (MavenProject reactorProject : allProjectsUsingPlugin) {
            if (!hasState(reactorProject)) {
                getLog().info(reactorProject.getArtifactId() + " not marked for deploy");
                return false;
            }
        }
        getLog().info("All projects marked for deploy");
        return true;
    }

    private List<MavenProject> getAllProjectsUsingPlugin() {
        ArrayList<MavenProject> result = new ArrayList<>();
        for (MavenProject reactorProject : reactorProjects) {
            if (hasExecution(reactorProject.getPlugin("org.apache.maven.plugins:maven-deploy-plugin"))) {
                result.add(reactorProject);
                getLog().info(reactorProject.getArtifactId() + " added to All projects using plugin");
            }
        }
        return result;
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
                    repo = getRemoteRepository(id, url);
                } else {
                    throw new MojoExecutionException("Invalid legacy syntax and layout for alternative repository: \""
                            + altDeploymentRepo + "\". Use \"" + id + "::" + url
                            + "\" instead, and only default layout is supported.");
                }
            } else {
                matcher = ALT_REPO_SYNTAX_PATTERN.matcher(altDeploymentRepo);

                if (!matcher.matches()) {
                    throw new MojoExecutionException("Invalid syntax for alternative repository: \"" + altDeploymentRepo
                            + "\". Use \"id::url\".");
                } else {
                    String id = matcher.group(1).trim();
                    String url = matcher.group(2).trim();

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

    protected File createBundle(List<MavenProject> allProjectsUsingPlugin) throws MojoExecutionException {
        if (allProjectsUsingPlugin.isEmpty()) {
            throw new MojoExecutionException("There are no deployments to process so no bundle to create");
        }
        // Locate the mega bundle in the top-level directory of the project
        // If we use project, it will be the last module built which is semi-random.
        MavenProject rootProject = project;
        while (rootProject.getParent() != null) {
            if (rootProject.getParent().getBasedir().exists()) {
                rootProject = rootProject.getParent();
            }
        }
        // Since it is a mega bundle (containing all sub projects),
        // name the zip using groupId and version.
        File targetDir = new File(rootProject.getBuild().getDirectory());
        File bundleFile =
                new File(targetDir, rootProject.getGroupId() + "-" + rootProject.getVersion() + "-bundle.zip");

        try {
            Bundler bundler = new Bundler(rootProject, getLog());
            bundler.createZipBundle(bundleFile, allProjectsUsingPlugin);
            getLog().info("Bundle created successfully: " + bundleFile);
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create bundle", e);
        }
        return bundleFile;
    }

    private void createAndDeploySingleProjectBundle(RemoteRepository deploymentRepository)
            throws MojoExecutionException {
        Bundler bundler = new Bundler(project, getLog());
        File targetDir = new File(project.getBuild().getDirectory());
        File bundleFile = new File(targetDir, project.getArtifactId() + "-" + project.getVersion() + "-bundle.zip");
        try {
            bundler.createZipBundle(bundleFile);
            getLog().info("Bundle created successfully: " + bundleFile);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new MojoExecutionException("Failed to create zip bundle", e);
        }
        if (uploadToCentral) {
            deployBundle(Collections.singleton(deploymentRepository), bundleFile);
        }
    }

    protected void deployBundle(Set<RemoteRepository> repos, File zipBundle) throws MojoExecutionException {
        for (RemoteRepository repo : repos) {
            String[] credentials = resolveCredentials(repo.getId());
            String username = credentials[0];
            String password = credentials[1];
            String deployUrl = repo.getUrl();
            centralPortalClient.setVariables(username, password, deployUrl, getLog());
            getLog().info("Deploying " + zipBundle + " to " + repo.getId() + " at "
                    + centralPortalClient.getPublishUrl());
            centralPortalClient.uploadAndCheck(zipBundle, autoDeploy);
        }
    }

    private String[] resolveCredentials(String serverId) throws MojoExecutionException {
        Server server = session.getSettings().getServer(serverId);
        if (server == null) {
            throw new MojoExecutionException("No <server> entry with id '" + serverId + "' in settings.xml");
        }

        SettingsDecryptionRequest decryptRequest = new DefaultSettingsDecryptionRequest(server);
        SettingsDecryptionResult decryptResult = settingsDecrypter.decrypt(decryptRequest);
        Server decryptedServer = decryptResult.getServer();

        String username = decryptedServer.getUsername();
        String password = decryptedServer.getPassword();

        if (username == null || password == null) {
            throw new MojoExecutionException("Missing credentials for server '" + serverId + "' in settings.xml");
        }

        return new String[] {username, password};
    }

    // Allow mockito to mock the centralPortalClient
    void setCentralPortalClient(CentralPortalClient centralPortalClient) {
        this.centralPortalClient = centralPortalClient;
    }
}
