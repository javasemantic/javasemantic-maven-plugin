package com.github.java.semantic.install.hooks;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class DefaultCommandRunner implements CommandRunner {
    private final Log log;

    public DefaultCommandRunner(Log log) {
        this.log = log;
    }

    @Override
    public String run(Path workingDir, String... command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (workingDir != null) {
                processBuilder.directory(workingDir.toFile());
            }
            processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);

            log.debug("Executing '" + StringUtils.join(command, StringUtils.SPACE) + "'");
            Process process = processBuilder.start();

            String output =
                    IOUtils.toString(process.getInputStream(), String.valueOf(StandardCharsets.UTF_8)).trim()
                            + IOUtils.toString(process.getErrorStream(), String.valueOf(StandardCharsets.UTF_8)).trim();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(String.valueOf(exitCode));
            }

            log.debug(output);
            return StringUtils.defaultIfBlank(output, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
