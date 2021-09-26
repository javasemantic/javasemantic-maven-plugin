package io.github.javasemantic;


import org.apache.commons.lang3.StringUtils;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.function.UnaryOperator;

import io.github.javasemantic.domain.model.DirtyCommit;
import io.github.javasemantic.logging.Log;
import io.github.javasemantic.utility.GitRepositoryFactory;
import io.github.javasemantic.utility.ValidConventionalCommitUtil;
import io.github.javasemantic.version.updater.VersionUpdaterFactory;

import static io.github.javasemantic.PathFinder.MAVEN_HOME_PROP;
import static io.github.javasemantic.PathFinder.findMavenToolPath;

@Mojo(name = "determine-version")
public class DetermineVersionMojo extends AbstractMojo {

    private final String POM_FILE = "pom.xml";
    private final UnaryOperator<String> systemProperties = System::getProperty;

    @Parameter(defaultValue = "${git.commit.message}", required = true)
    private String commitMessage;

    @Parameter(defaultValue = "${git.commit.body}", required = true)
    private String commitBody;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject currentProject;

    @Parameter(property = "version.override.activate", defaultValue = "false", name = "activate-override-version")
    private boolean activateOverrideVersion;

    @Parameter(property = "version.override", defaultValue = "0.0.1-SNAPSHOT", name = "override-static-version")
    private String overrideStaticVersion;

    @Parameter(property = "override.version.branch.blacklist.items", name = "override-version-branch-blacklist-items")
    private List<String> overrideVersionBranchBlacklistItems;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            executeCommitMessageValidation();
            executeUpdateVersion();
            // Hook: post-commit will add amended build file to commit.
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void executeUpdateVersion() {

        if (isOverrideVersion()) {
            executePomVersionUpdate(overrideStaticVersion);
        } else {
            executePomVersionUpdate(
                executeVersionCalculation()
            );
        }
    }

    private boolean isOverrideVersion() {

        var currentWorkingBranch = GitRepositoryFactory.currentWorkingBranch();
        Log.info(this.getClass(), "Current working branch: " + currentWorkingBranch);
        Log.info(this.getClass(), "Black list branches: " + overrideVersionBranchBlacklistItems);
        var overrideBranch = overrideVersionBranchBlacklistItems.stream().noneMatch(currentWorkingBranch::contains);

        return activateOverrideVersion && overrideBranch;
    }

    private String executeVersionCalculation() {
        Log.info(this.getClass(), "Last commit message: " + commitMessage);

        if (StringUtils.isNotBlank(commitBody))
            Log.info(this.getClass(), "Last commit body: " + commitBody);

        var service = JavaSemanticServiceFactory.get(getLog(), buildLastCommit());
        var version = service.execute();
        return version.toString();
    }

    private void executePomVersionUpdate(String version) {
        var versionUpdater = VersionUpdaterFactory.getMavenVersionUpdater();
        var mavenBin = findMavenToolPath(systemProperties.apply(MAVEN_HOME_PROP), getClass());
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
            Log.info(this.getClass(), ValidConventionalCommitUtil.VALID_LOG_MESSAGE);
        } else {
            throw new MojoExecutionException(
                commitMessage + ValidConventionalCommitUtil.INVALID_LOG_MESSAGE
            );
        }
    }

}
