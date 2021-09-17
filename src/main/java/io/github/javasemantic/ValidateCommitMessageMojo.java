package io.github.javasemantic;

import io.github.javasemantic.utility.ValidConventionalCommitUtil;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "commit-validation")
public class ValidateCommitMessageMojo extends AbstractMojo {

    @Parameter(defaultValue = "${git.commit.message}", required = true )
    private String commitMessage;

    public void execute() throws MojoExecutionException {
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
