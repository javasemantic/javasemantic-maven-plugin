package com.github.java.semantic.install.hooks;

import java.nio.file.Path;

public interface CommandRunner {

    String run(Path workingDir, String... command);

}
