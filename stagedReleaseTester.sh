#!/bin/bash

export AVAILABLE_JETTY_VERSIONS="$*"
export REPOSITORY_URL="http://localhost:8081/repository/maven-public/org/eclipse/jetty"
./update.sh