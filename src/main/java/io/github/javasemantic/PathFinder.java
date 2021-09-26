package io.github.javasemantic;

import org.apache.commons.exec.OS;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.github.javasemantic.executables.Extension;
import io.github.javasemantic.executables.NewExecutable;
import io.github.javasemantic.logging.Log;

public class PathFinder {

    public static final String MAVEN_HOME_PROP = "maven.home";

    private PathFinder() {
    }

    public static Path findMavenToolPath(String buildToolDirectory, Class<?> clazz) {
        Path directory = Paths.get(buildToolDirectory);
        Log.debug(clazz, "Maven home:" + directory);
        Path mavenBinDirectory = directory.resolve("bin");
        
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

}
