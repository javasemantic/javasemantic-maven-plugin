package com.github.java.semantic;


import com.github.java.semantic.install.hooks.CommandRunner;
import com.github.java.semantic.install.hooks.DefaultCommandRunner;
import com.github.java.semantic.install.hooks.DefaultExecutable;
import com.github.java.semantic.install.hooks.Executable;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.exec.OS;

import static java.util.Optional.ofNullable;

/**
 * Installs git hooks on each initialization. Hooks are always overridden in case of changes in:
 *
 * <ul>
 *   <li>maven installation
 *   <li>plugin structure
 * </ul>
 */

@Mojo(name = "install-hooks", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class InstallHooksMojo extends AbstractMojo {

    private static final String BASE_PLUGIN_PRE_COMMIT_HOOK = "java-semantic-maven-plugin.commit-msg.sh";
    private static final String COMMIT_MSG_HOOK_BASE_SCRIPT = "commit-msg";
    private static final String HOOKS_DIR = "hooks";

    private static final String MAVEN_HOME_PROP = "maven.home";

    private final UnaryOperator<String> systemProperties = System::getProperty;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject currentProject;

    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    private String sourceEncoding;

    /** Skip execution of this goal */
    @Parameter(property = "gcf.skip", defaultValue = "false")
    private boolean skip;

    /** Skip execution of this specific goal */
    @Parameter(property = "gcf.skipInstallHooks", defaultValue = "false")
    private boolean skipInstallHooks;

    /**
     * True to truncate hooks base scripts before each install. <br>
     * Do not use this option if any other system or human manipulate the hooks
     */
    @Parameter(property = "gcf.truncateHooksBaseScripts", defaultValue = "false")
    private boolean truncateHooksBaseScripts;

    /** The list of properties to propagate to the hooks */
    @Parameter(property = "gcf.propertiesToPropagate")
    private String[] propertiesToPropagate;

    /** The list of properties to add to the hooks */
    @Parameter(property = "gcf.propertiesToAdd")
    private String[] propertiesToAdd;

    /**
     * Add pipeline to process the results of the pre-commit hook. Exit non-zero to prevent the commit
     */
    @Parameter(property = "gcf.preCommitHookPipeline", defaultValue = "")
    private String preCommitHookPipeline;

    private Executable getOrCreateExecutableScript(Path file) throws IOException {
        return new DefaultExecutable(getLog(), file);
    }

    public void execute() throws MojoExecutionException {
        if (!isExecutionRoot()) {
            getLog().debug("Not in execution root. Do not execute.");
            return;
        }
        if (skip || skipInstallHooks) {
            if (getLog().isInfoEnabled()) {
                getLog().info("Install git hook was skipped by configuration.");
            }
            return;
        }
        try {
            getLog().info("Installing git hooks.");
            doExecute();
            getLog().info("Installed git hooks.");
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void doExecute() throws IOException {
        Path hooksDirectory = prepareHooksDirectory();
        writePluginHooks(hooksDirectory, pluginPreCommitHookFileName());
        writePluginHooks(hooksDirectory, COMMIT_MSG_HOOK_BASE_SCRIPT);
        configureHookBaseScripts(hooksDirectory);
    }

    private void configureHookBaseScripts(Path hooksDirectory) {
        getLog().info("Commit message script: " + hooksDirectory
                .resolve(pluginPreCommitHookFileName()).
                        toFile());
    }

    private void writePluginHooks(Path hooksDirectory, String fileName) throws IOException {
        getLog().debug("Writing plugin pre commit hook file");
        this
                .getOrCreateExecutableScript(hooksDirectory.resolve(fileName))
                .truncateWithTemplate(
                        () -> getClass().getResourceAsStream("/" + BASE_PLUGIN_PRE_COMMIT_HOOK),
                        StandardCharsets.UTF_8.toString(),
                        this.getMavenExecutable().toAbsolutePath(),
                        pomFile().toAbsolutePath(),
                        mavenCliArguments());
        getLog().debug("Written plugin pre commit hook file: " + fileName);
    }

    private String mavenCliArguments() {
        Stream<String> propagatedProperties =
                ofNullable(propertiesToPropagate).map(Arrays::asList).orElse(Collections.emptyList())
                        .stream()
                        .filter(prop -> System.getProperty(prop) != null)
                        .map(prop -> "-D" + prop + "=" + System.getProperty(prop));

        Stream<String> properties = Stream.concat(propagatedProperties, Stream.of(propertiesToAdd));
        if (preCommitHookPipeline != null && !preCommitHookPipeline.isEmpty()) {
            properties = Stream.concat(properties, Stream.of(preCommitHookPipeline));
        }
        return properties.collect(Collectors.joining(" "));
    }

    private Path prepareHooksDirectory() {
        getLog().debug("Preparing git hook directory");
        Path hooksDirectory;
        hooksDirectory = getOrCreateHooksDirectory();
        getLog().debug("Prepared git hook directory");
        return hooksDirectory;
    }

    private String pluginPreCommitHookFileName() {
        return artifactId() + "." + BASE_PLUGIN_PRE_COMMIT_HOOK;
    }

    private Path pomFile() {
        return currentProject.getFile().toPath();
    }

    private Path getOrCreateHooksDirectory() {
        Path hooksDirectory = gitRepository().getDirectory().toPath().resolve(HOOKS_DIR);
        if (!Files.exists(hooksDirectory)) {
            getLog().debug("Creating directory " + hooksDirectory);
            try {
                Files.createDirectories(hooksDirectory);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            getLog().debug(hooksDirectory + " already exists");
        }
        return hooksDirectory;
    }

    protected final Repository gitRepository() {
        Repository gitRepository;
        try {
            FileRepositoryBuilder repositoryBuilder =
                    new FileRepositoryBuilder().findGitDir(currentProject.getBasedir());
            String gitIndexFileEnvVariable = System.getenv("GIT_INDEX_FILE");
            if (StringUtils.isNotBlank(gitIndexFileEnvVariable)) {
                repositoryBuilder = repositoryBuilder.setIndexFile(new File(gitIndexFileEnvVariable));
            }
            gitRepository = repositoryBuilder.build();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not find the git repository. Run 'git init' if you did not.", e);
        }
        return gitRepository;
    }

    private String artifactId() {
        return currentProject.getArtifactId();
    }

    public Path getMavenExecutable() {
        Path mavenHome = Paths.get(systemProperties.apply(MAVEN_HOME_PROP));
        getLog().info("maven.home=" + mavenHome);
        Path mavenBinDirectory = mavenHome.resolve("bin");
        getLog().info(mavenBinDirectory.toString());
        List<List<NewExecutable>> executableCandidates =
                Arrays.asList(
                        Arrays.asList(
                                new NewExecutable(mavenBinDirectory, Extension.NONE),
                                new NewExecutable(null, Extension.NONE)),
                        Arrays.asList(
                                new NewExecutable(mavenBinDirectory, Extension.CMD),
                                new NewExecutable(null, Extension.CMD)));

        if (OS.isFamilyWindows()) {
            Collections.reverse(executableCandidates);
        }

        return executableCandidates.stream()
                .flatMap(Collection::stream)
                .filter(NewExecutable::isValid)
                .findFirst()
                .map(NewExecutable::path)
                .orElseThrow(() -> new RuntimeException("No valid maven executable found !"));
    }

    private enum Extension {
        NONE(null),
        CMD("cmd");
        private final String value;

        Extension(String value) {
            this.value = value;
        }
    }

    private class NewExecutable {

        private final Path path;

        private NewExecutable(Path prefix, Extension extension) {
            String name = "mvn";

            if (extension != Extension.NONE) {
                name += "." + extension.value;
            }
            if (prefix != null) {
                path = prefix.resolve(name);
            } else {
                path = Paths.get(name);
            }
        }

        Path path() {
            return path;
        }

        boolean isValid() {
            try {
                getLog().info("Checking if maven is valid: " + path.toString());
                CommandRunner commandRunner = new DefaultCommandRunner(getLog());
                commandRunner.run(null, path.toString(), "--version");
                return true;
            } catch (Exception e) {
                getLog().debug(e.getMessage());
            }
            return false;
        }
    }

    private boolean isExecutionRoot() {
        return currentProject.isExecutionRoot();
    }

}
