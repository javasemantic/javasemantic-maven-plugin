package com.github.java.semantic;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.util.regex.Pattern;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "commit-validation")
public class ValidateCommitMessageMojo extends AbstractMojo {

    @Parameter(defaultValue = "${git.commit.message}", required = true )
    private String commitMessage;

    public void execute() throws MojoExecutionException {
        var result = Pattern.matches("^(build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test)(\\([a-z \\-]+\\))?!?: .+$", this.commitMessage);

        if (result) {
            getLog().info("The commit message meets the Conventional Commit standard.");
        } else {
            var errorMessage = commitMessage +  "\nThe commit message does not meet the Conventional Commit standard\nAn example of a valid message is:\n\tfeat(login): add the 'remember me' button\nMore details at: https://www.conventionalcommits.org/en/v1.0.0/#summary";
            throw new MojoExecutionException(errorMessage);
        }
    }
}
