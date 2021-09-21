package io.github.javasemantic.executables;





import io.github.javasemantic.install.hooks.other.CommandRunner;
import io.github.javasemantic.install.hooks.other.DefaultCommandRunner;

import java.nio.file.Path;
import java.nio.file.Paths;

public class NewExecutable {

    private final Path path;

    public NewExecutable(Path prefix, Extension extension) {
        String name = "mvn";

        if (extension != Extension.NONE) {
            name += "." + extension.getValue();
        }
        if (prefix != null) {
            path = prefix.resolve(name);
        } else {
            path = Paths.get(name);
        }
    }

    public Path path() {
        return path;
    }

    public boolean isValid() {
        CommandRunner commandRunner = new DefaultCommandRunner();
        commandRunner.run(null, path.toString(), "--version");
        return true;
    }
}
