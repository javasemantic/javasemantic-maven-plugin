package com.github.javasemantic.install.hooks;

import java.nio.file.Path;

public interface CommandRunner {
    void run(Path workingDir, String... command);
}
