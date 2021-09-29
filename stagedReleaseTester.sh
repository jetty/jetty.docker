#!/bin/bash

greaterThanOrEqualTo9.4 ()
{
	# If version is not numerical it cannot be compared properly.
	if [[ ! $1 =~ ^[0-9]+\.?[0-9]*$ ]]; then
		echo "Invalid version $1"
		exit 1
	fi

	# Compare version numerically using awk.
	if awk 'BEGIN{exit ARGV[1]>=ARGV[2]}' "$1" "9.4"; then
		return 1
	else
		return 0
	fi
}

if [ $# -lt 1 ]; then
	echo "Error: provide a list of staging numbers as arguments"
	exit 1
fi
stagingNumbers=( $@ )

declare -A jettyHomeFromPartialVersion
declare -A fullVersionFromPartialVersion
for stagingNumber in "${stagingNumbers[@]}"; do
	stagingRepo="https://oss.sonatype.org/content/repositories/jetty-$stagingNumber/org/eclipse/jetty"
	jettyVersion=$( curl -s $stagingRepo/jetty-home/maven-metadata.xml | egrep '<version>.*</version>' | sed -e 's/.*<version>\(.*\)<\/version>.*/\1/g' )
	if [ -z "$jettyVersion" ]; then
		echo "Error: No Jetty Version for staging number $stagingNumber"
		exit 1
	fi

	# Index in hashmap by the partial version.
	partialVersion=$(echo "$jettyVersion" | sed -e 's/^\([0-9]\+\.[0-9]\+\).*$/\1/g')
	fullVersionFromPartialVersion["$partialVersion"]="$jettyVersion"
	jettyHomeFromPartialVersion["$partialVersion"]="$stagingRepo/jetty-home/$jettyVersion/jetty-home-$jettyVersion.tar.gz"
done

# Update the docker files and scripts for every directory in paths.
paths=( $(ls | egrep '^[0-9]' | sort -nr) )
paths=( "${paths[@]%/}" )

for path in "${paths[@]}"; do
	version="${path%%-*}" # "9.2"
	jvm="${path#*-}" # "jre11-slim"
	disto=$(expr "$jvm" : '\(j..\)[0-9].*') # jre
	variant=$(expr "$jvm" : '.*-\(.*\)') # slim
	release=$(expr "$jvm" : 'j..\([0-9][0-9]*\).*') # 11
	label=${release}-${disto}${variant:+-$variant} # 11-jre-slim

	jettyHomeUrl="${jettyHomeFromPartialVersion[$version]}"
	if [ -z "$jettyHomeUrl" ]; then
		echo "Did not Update: $path"
		continue
	fi

	fullVersion="${fullVersionFromPartialVersion[$version]}"
	if [ -z "$jettyHomeUrl" ]; then
		echo "Did not Update: $path"
		continue
	fi

	if greaterThanOrEqualTo9.4 "${version}"; then
		cp docker-entrypoint.sh generate-jetty-start.sh "$path"

		# Generate the Dockerfile in the directory for this version.
		echo "# DO NOT EDIT. Edit baseDockerfile${variant:+-$variant} and use update.sh" >"$path"/Dockerfile
		cat "baseDockerfile${variant:+-$variant}" >>"$path"/Dockerfile

		# Set the Jetty and JDK/JRE versions in the generated Dockerfile.
		sed -ri 's/^(ENV JETTY_VERSION) .*/\1 '"$fullVersion"'/; ' "$path/Dockerfile"
		sed -ri 's|^(ENV JETTY_TGZ_URL) .*|\1 '"$jettyHomeUrl"'|; ' "$path/Dockerfile"
		sed -ri 's/^(FROM openjdk:)LABEL/\1'"$label"'/; ' "$path/Dockerfile"

		echo "Successfully Updated: $path"
	else
		echo "Did not Update: $path"
	fi
done
