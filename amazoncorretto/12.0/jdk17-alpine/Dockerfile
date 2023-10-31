# DO NOT EDIT. Edit baseDockerfile-alpine and use update.sh
FROM amazoncorretto:17-alpine

ENV JETTY_VERSION 12.0.3
ENV JETTY_HOME /usr/local/jetty
ENV JETTY_BASE /var/lib/jetty
ENV TMPDIR /tmp/jetty
ENV PATH $JETTY_HOME/bin:$PATH
ENV JETTY_TGZ_URL https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/$JETTY_VERSION/jetty-home-$JETTY_VERSION.tar.gz

# GPG Keys are personal keys of Jetty committers (see https://github.com/eclipse/jetty.project/blob/0607c0e66e44b9c12a62b85551da3a0edce0281e/KEYS.txt)
ENV JETTY_GPG_KEYS \
	# Jan Bartel      <janb@mortbay.com>
	AED5EE6C45D0FE8D5D1B164F27DED4BF6216DB8F \
	# Jesse McConnell <jesse.mcconnell@gmail.com>
	2A684B57436A81FA8706B53C61C3351A438A3B7D \
	# Joakim Erdfelt  <joakim.erdfelt@gmail.com>
	5989BAF76217B843D66BE55B2D0E1FB8FE4B68B4 \
	# Joakim Erdfelt  <joakime@apache.org>
	B59B67FD7904984367F931800818D9D68FB67BAC \
	# Joakim Erdfelt  <joakim@erdfelt.com>
	BFBB21C246D7776836287A48A04E0C74ABB35FEA \
	# Simone Bordet   <simone.bordet@gmail.com>
	8B096546B1A8F02656B15D3B1677D141BCF3584D \
	# Olivier Lamy    <olamy@apache.org>
	F254B35617DC255D9344BCFA873A8E86B4372146 \
	# Ludovic Orban   <lorban@bitronix.be>
	E22488CC94F63E3FC928536C4241C08270D999C3

RUN set -xe ; \
	mkdir -p $TMPDIR ; \
	#
	# Install utils needed to verify keys
	apk add --no-cache gnupg curl ; \
	#
	# fetch GPG keys
	export GNUPGHOME=/jetty-keys ; \
	mkdir -p "$GNUPGHOME" ; \
	for key in $JETTY_GPG_KEYS; do \
		gpg --batch --keyserver "hkps://keyserver.ubuntu.com" --recv-keys "$key"; \
	done ; \
	#
	# Fetch jetty release into JETTY_HOME
	mkdir -p "$JETTY_HOME" ; \
	cd $JETTY_HOME ; \
	curl -SL "$JETTY_TGZ_URL" -o jetty.tar.gz ; \
	curl -SL "$JETTY_TGZ_URL.asc" -o jetty.tar.gz.asc ; \
	#
	# Verify GPG signatures
	gpg --batch --verify jetty.tar.gz.asc jetty.tar.gz ; \
	#
	# Unpack jetty
	tar -xvf jetty.tar.gz --strip-components=1 ; \
	sed -i '/jetty-logging/d' etc/jetty.conf ; \
	#
	# Create and configure the JETTY_HOME directory
	mkdir -p "$JETTY_BASE" ; \
	cd $JETTY_BASE ; \
	case "$JETTY_VERSION" in \
		"12."*) START_MODULES="server,http,ext,resources" ;; \
		*) START_MODULES="server,http,deploy,ext,resources,jsp,jstl,websocket" ;; \
	esac ; \
	java -jar "$JETTY_HOME/start.jar" --create-startd \
		--add-to-start="$START_MODULES" ; \
	addgroup -S jetty && adduser -h $JETTY_BASE -S jetty -G jetty; \
	chown -R jetty:jetty "$JETTY_HOME" "$JETTY_BASE" "$TMPDIR" ; \
	#
	# Cleanup
	rm -rf /tmp/hsperfdata_root ; \
	rm -fr $JETTY_HOME/jetty.tar.gz* ; \
	rm -fr /jetty-keys $GNUPGHOME ; \
	rm -rf /tmp/hsperfdata_root ; \
	#
	# Basic smoke test
	java -jar "$JETTY_HOME/start.jar" --list-config ;

WORKDIR $JETTY_BASE
COPY docker-entrypoint.sh generate-jetty-start.sh /

USER jetty
EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["java","-jar","/usr/local/jetty/start.jar"]
