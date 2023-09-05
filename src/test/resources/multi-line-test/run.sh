#!/bin/sh

set -e

JAVA_OPTIONS="${JAVA_OPTIONS}
              -Dlogback.configurationFile=${JETTY_BASE}/conf/logback.xml
              -Dfile.encoding=ISO-8859-1
              -Duser.country=NL
              -Duser.language=nl
              -Ddatabase.type=postgres
              -Dwicket.configuration=deployment
              -Dlogback.statusListenerClass=ch.qos.logback.core.status.OnConsoleStatusListener
              -Dorg.eclipse.jetty.server.Request.maxFormContentSize=2000000"

export JAVA_OPTIONS
/docker-entrypoint.sh --list-config
