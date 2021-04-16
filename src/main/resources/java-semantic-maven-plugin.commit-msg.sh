#!/bin/bash

commit_message=$(cat "$1")

%s -f %s com.github.java.semantic:java-semantic-maven-plugin:commit-validation -Dgit.commit.message="$commit_message"