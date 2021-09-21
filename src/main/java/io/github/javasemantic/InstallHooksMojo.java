package io.github.javasemantic;

import io.github.javasemantic.executables.Extension;
import io.github.javasemantic.executables.NewExecutable;
import io.github.javasemantic.install.hooks.InstallHookFactory;
import io.github.javasemantic.install.hooks.model.InstallHookArguments;
import io.github.javasemantic.logging.Log;
import org.apache.commons.exec.OS;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

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

    // ?
    private static final String MAVEN_HOME_PROP = "maven.home";

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

    private final UnaryOperator<String> systemProperties = System::getProperty;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            var installHook = InstallHookFactory.get();

            installHook.execute(
                    InstallHookArguments
                            .builder()
                            .projectBuildFile(getPomFile())
                            .artifactId(getArtifactId())
                            .isExecutionRoot(isExecutionRoot())
                            .projectBaseDirectory(getBaseDirectory())
                            .propertiesToPropagate(getMavenPropertiesToPropagate())
                            .propertiesToAdd(getMavenPropertiesToAdd())
                            .gitLifeCycle(getGitLifeCycle())
                            .buildToolAbsolutePath(getMavenExecutable().toAbsolutePath())
                            .build()
            );
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    public Path getMavenExecutable() {
        Path mavenHome = Paths.get(systemProperties.apply(MAVEN_HOME_PROP));

        Log.info(this.getClass(), "maven.home=" + mavenHome);

        Path mavenBinDirectory = mavenHome.resolve("bin");

        Log.info(this.getClass(), mavenBinDirectory.toString());

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

    private Path getPomFile() {
        return currentProject.getFile().toPath();
    }

    private String getArtifactId() {
        return currentProject.getArtifactId();
    }

    private boolean isExecutionRoot() {
        return currentProject.isExecutionRoot();
    }

    private Path getBaseDirectory() {
        return currentProject.getBasedir().toPath();
    }

    private String[] getMavenPropertiesToPropagate() {
        return propertiesToPropagate;
    }

    private String[] getMavenPropertiesToAdd() {
        return propertiesToAdd;
    }

    private String getGitLifeCycle() {
        return preCommitHookPipeline;
    }









}
