#!/bin/bash

export AVAILABLE_JETTY_VERSIONS="$*"
export REPOSITORY_URL="http://10.0.0.15:8081/repository/maven-public/org/eclipse/jetty"
./update.sh