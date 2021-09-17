package io.github.javasemantic.executables;

public enum Extension {

    NONE(null),
    CMD("cmd");
    private final String value;

    Extension(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
