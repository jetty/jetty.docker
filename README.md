# About this Repo

[![Build Status](https://jenkins.webtide.net/job/jetty.docker/job/master/badge/icon)](https://jenkins.webtide.net/job/jetty.docker/job/master/)

This is the Git repo for the official images of [Jetty on Docker Hub](https://registry.hub.docker.com/_/jetty/).
See the Hub page for the full readme on how to use the Docker image and for information regarding contributing and issues.

The full readme is generated over in [docker-library/docs](https://github.com/docker-library/docs),
specifically in [docker-library/docs/jetty](https://github.com/docker-library/docs/tree/master/jetty).

The library definition files for the official jetty docker images can be found at [docker-library/official-images/library/jetty](https://github.com/docker-library/official-images/blob/master/library/jetty)

## Jetty 12 Migration Guide

Jetty 12 can run with various different EE Environments.
- EE8 (Servlet 4.0) in the java.* namespace,
- EE9 (Servlet 5.0) in the jakarta.* namespace with deprecated features
- EE10 (Servlet 6.0) in the jakarta.* namespace without deprecated features.
- Jetty Core Environment with no Servlet support or overhead.

In the docker images prior to Jetty 12, certain Jetty Modules were enabled by default (server,http,deploy,ext,resources,jsp,jstl,websocket). 
However, in the Jetty 12 images we do not assume which environment will be used, therefore we only add the following modules (server,http,ext,resources).
If you are migrating to use the Jetty 12 docker images you will need to enable the Jetty modules that you require.

For example, to use EE10 you could include the following line in your Dockerfile:
```Dockerfile
RUN java -jar "$JETTY_HOME/start.jar" --add-modules=ee10-webapp,ee10-deploy,ee10-jsp,ee10-jstl,ee10-websocket-jetty,ee10-websocket-jakarta
```

For a full list of the available Jetty Modules, run `java -jar "$JETTY_HOME/start.jar" --list-modules`.

## History
This project was imported from [appropriate/docker-jetty](https://github.com/appropriate/docker-jetty), 
thanks to the efforts from [Appropriate](https://github.com/appropriate) and all other contributors who worked on this project.
