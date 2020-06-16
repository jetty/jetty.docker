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

paths=( "$@" )
if [ ${#paths[@]} -eq 0 ]; then
	paths=( */ )
fi
paths=( "${paths[@]%/}" )
paths=($(echo "${paths[@]}" | sed 's/ /\n/g' | grep -v '^[^0-9]'))

MAVEN_METADATA_URL='https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/maven-metadata.xml'
available=( $( curl -sSL "$MAVEN_METADATA_URL" | grep -Eo '<(version)>[^<]*</\1>' | awk -F'[<>]' '{ print $3 }' | sort -Vr ) )

for path in "${paths[@]}"; do
	version="${path%%-*}" # "9.2"
	jvm="${path#*-}" # "jre11-slim"
	disto=$(expr "$jvm" : '\(j..\)[0-9].*') # jre
	variant=$(expr "$jvm" : '.*-\(.*\)') # slim
	release=$(expr "$jvm" : 'j..\([0-9][0-9]*\).*') # 11
	label=${release}-${disto}${variant:+-$variant} # 11-jre-slim

	milestones=()
	releaseCandidates=()
	alphaReleases=()
	betaReleases=()
	fullReleases=()
	for candidate in "${available[@]}"; do
		if [[ "$candidate" == "$version".* ]]; then
			if [[ "$candidate" == *.M* ]]; then
				milestones+=("$candidate")
			elif [[ "$candidate" == *.RC* ]]; then
				releaseCandidates+=("$candidate")
			elif [[ "$candidate" == *.v* ]]; then
				fullReleases+=("$candidate")
			elif [[ "$candidate" == *alpha* ]]; then
				alphaReleases+=("$candidate")
			elif [[ "$candidate" == *beta* ]]; then
				betaReleases+=("$candidate")
			fi
		fi
	done

	fullVersion=
	if [ -n "${fullReleases-}" ]; then
		fullVersion="$fullReleases"
	elif [ -n "${releaseCandidates-}" ]; then
		fullVersion="$releaseCandidates"
	elif [ -n "${milestones-}" ]; then
		fullVersion="$milestones"
	elif [ -n "${betaReleases-}" ]; then
		fullVersion="$betaReleases"
	elif [ -n "${alphaReleases-}" ]; then
		fullVersion="$alphaReleases"
	fi

	if [ -z "$fullVersion" ]; then
		echo >&2 "Unable to find Jetty package for $path"
		exit 1
	fi

	echo Full Version "${fullVersion}"

	if [ -d "$path" ]; then
		cp docker-entrypoint.sh generate-jetty-start.sh "$path"

		# Only generate docker file for versions past 9.4, otherwise just update existing Dockerfile.
		if greaterThanOrEqualTo9.4 "${version}"; then
			# Generate the Dockerfile in the directory for this version.
			echo "# DO NOT EDIT. Edit baseDockerfile${variant:+-$variant} and use update.sh" >"$path"/Dockerfile
			cat "baseDockerfile${variant:+-$variant}" >>"$path"/Dockerfile

			# Set the Jetty and JDK/JRE versions in the generated Dockerfile.
			sed -ri 's/^(ENV JETTY_VERSION) .*/\1 '"$fullVersion"'/; ' "$path/Dockerfile"
			sed -ri 's/^(FROM openjdk:)LABEL/\1'"$label"'/; ' "$path/Dockerfile"
		else
			sed -ri 's/^(ENV JETTY_VERSION) .*/\1 '"$fullVersion"'/; ' "$path/Dockerfile"
		fi
	fi
done
