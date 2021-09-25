package io.github.javasemantic;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.function.UnaryOperator;

import io.github.javasemantic.domain.model.DirtyCommit;
import io.github.javasemantic.utility.ValidConventionalCommitUtil;
import io.github.javasemantic.version.updater.VersionUpdaterFactory;

import static io.github.javasemantic.PathFinder.MAVEN_HOME_PROP;
import static io.github.javasemantic.PathFinder.findMavenToolPath;

@Mojo(name = "determine-version")
public class DetermineVersionMojo extends AbstractMojo {

    private final UnaryOperator<String> systemProperties = System::getProperty;
    @Parameter(defaultValue = "${git.commit.message}", required = true)
    private String commitMessage;
    @Parameter(defaultValue = "${git.commit.body}", required = true)
    private String commitBody;
    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject currentProject;

    @Override
    public void execute() throws MojoExecutionException {
        try {

            executeCommitMessageValidation();
            var version = executeVersionCalculation();
            //updateVersionOfPom(version);
            updateVersionOfPom("0.0.3-SNAPSHOT");

        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private String executeVersionCalculation() {
        getLog().info("Last commit message: " + commitMessage);
        getLog().info("Last commit body: " + commitBody);
        var service = JavaSemanticServiceFactory.get(getLog(), buildLastCommit());
        var version = service.execute();
        getLog().info("Version calculated for project: " + version.toString());
        return version.toString();

    }

    private void updateVersionOfPom(String version) {
        var versionUpdater = VersionUpdaterFactory.getMavenVersionUpdater();
        var mavenBin = findMavenToolPath(systemProperties.apply(MAVEN_HOME_PROP), getClass());
        getLog().info("Update Project Version: " + commitBody);
        versionUpdater.updateVersion(mavenBin, currentProject.getBasedir().toPath(), version);
    }


    private DirtyCommit buildLastCommit() {
        return DirtyCommit.builder()
            .message(commitMessage)
            .footers(List.of(commitBody))
            .build();
    }

    private void executeCommitMessageValidation() throws MojoExecutionException {
        var result = ValidConventionalCommitUtil.isValid(commitMessage);

        if (result) {
            getLog().info(ValidConventionalCommitUtil.VALID_LOG_MESSAGE);
        } else {
            throw new MojoExecutionException(
                commitMessage + ValidConventionalCommitUtil.INVALID_LOG_MESSAGE
            );
        }
    }

}
