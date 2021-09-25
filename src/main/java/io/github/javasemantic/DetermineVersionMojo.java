package io.github.javasemantic;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.List;

import io.github.javasemantic.domain.model.DirtyCommit;
import io.github.javasemantic.utility.ValidConventionalCommitUtil;

@Mojo(name = "determine-version")
public class DetermineVersionMojo extends AbstractMojo {

    @Parameter(defaultValue = "${git.commit.message}", required = true)
    private String commitMessage;

    @Parameter(defaultValue = "${git.commit.body}", required = true)
    private String commitBody;

    @Override
    public void execute() throws MojoExecutionException {
        executeCommitMessageValidation();
        executeVersionCalculation();
    }

    private void executeVersionCalculation() {
        getLog().info("Last commit message: " + commitMessage);
        getLog().info("Last commit body: " + commitBody);
        var service = JavaSemanticServiceFactory.get(getLog(), buildLastCommit());
        var version = service.execute();
        System.out.println(version);
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
