package com.github.java.semantic.install.hooks;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class DefaultExecutable implements Executable {

    private static final String SHIBANG = "#!/bin/bash";

    private final Log log;
    private final Path file;

    public DefaultExecutable(Log log, Path file) throws IOException {
        requireNonNull(log);
        requireNonNull(file);
        this.log = log;

        this.file = file;

        if (!Files.exists(file)) {
            this.log.info("Creating " + file);
            Files.createFile(file);
            truncate();
        } else {
            this.log.info(file + " already exists");
        }

        this.log.info("Marking '" + file + "' as executable");
        Set<PosixFilePermission> permissions;
        try {
            permissions = Files.getPosixFilePermissions(file);
        } catch (UnsupportedOperationException ignored) {
            return;
        }

        permissions.add(PosixFilePermission.OWNER_EXECUTE);
        permissions.add(PosixFilePermission.GROUP_EXECUTE);
        permissions.add(PosixFilePermission.OTHERS_EXECUTE);

        Files.setPosixFilePermissions(file, permissions);
    }

    @Override
    public Executable truncate() throws IOException {
        log.info("Truncating '" + file + "'");
        Files.write(file, Collections.singleton(SHIBANG), StandardOpenOption.TRUNCATE_EXISTING);
        return this;
    }

    @Override
    public Executable truncateWithTemplate(
            Supplier<InputStream> template, String sourceEncoding, Object... values) throws IOException {
        try (InputStream inputStream = template.get()) {
            String rawContent = IOUtils.toString(inputStream, sourceEncoding);
            Object[] refinedValues = Stream.of(values).map(this::unixifyPath).toArray();
            String content = String.format(rawContent, refinedValues);
            Files.write(file, content.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
        }
        return this;
    }

    @Override
    public Executable appendCommandCall(String commandCall) throws IOException {
        String unixCommandCall = unixifyPath(commandCall, true);
        boolean callExists =
                Files.readAllLines(file).stream().anyMatch(s -> s.contains(unixCommandCall));
        if (callExists) {
            log.info("Command call already exists in " + file);
        } else {
            log.info("No command call found in " + file);
            log.info("Appending the command call to " + file);
            Files.write(file, Collections.singletonList(unixCommandCall), StandardOpenOption.APPEND);
            log.info("Appended the command call to " + file);
        }
        return this;
    }

    @Override
    public Executable removeCommandCall(String commandCall) {
        String unixCommandCall = unixifyPath(commandCall, true);
        try {
            List<String> linesToKeep =
                    Files.readAllLines(file).stream()
                            .filter(line -> !unixCommandCall.equals(line))
                            .collect(Collectors.toList());
            Files.write(file, linesToKeep, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    private String unixifyPath(Object o) {
        return unixifyPath(o, false);
    }

    private String unixifyPath(Object o, boolean force) {
        if (!force && !(o instanceof Path)) {
            return String.valueOf(o);
        }

        String result;
        if (o instanceof Path) {
            Path path = (Path) o;
            result = path.toAbsolutePath().toString();
        } else {
            result = String.valueOf(o);
        }
        return "\"" + StringUtils.replace(result, "\\", "/") + "\"";
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
