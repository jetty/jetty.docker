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

getFullVersion()
{
	version=$1
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
			elif [[ "$candidate" == *.v* || "$candidate" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
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

	echo $fullVersion
}

# Update the docker files and scripts for every directory in paths.
paths=( "$@" )
if [ ${#paths[@]} -eq 0 ]; then
	paths=( $(find -mindepth 4 -maxdepth 4 -name "Dockerfile" | sed -e 's/\.\///' | sed -e 's/\/Dockerfile//' | sort -nr) )
fi
paths=( "${paths[@]%/}" )

MAVEN_METADATA_URL='https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-server/maven-metadata.xml'
available=( $( curl -sSL "$MAVEN_METADATA_URL" | grep -Eo '<(version)>[^<]*</\1>' | awk -F'[<>]' '{ print $3 }' | sort -Vr ) )

for path in "${paths[@]}"; do
	baseImage="${path%%/*}" # first segment of path will be base image.
	remainingPath="${path#*/}"
	version="${remainingPath%%/*}" # "9.2"
	imageTag="${remainingPath#*/}"
	variant=$( [[ $imageTag == *"slim"* ]] && echo "slim" )

	fullVersion=$(getFullVersion $version)
	if [ -z "$fullVersion" ]; then
		echo >&2 "Unable to find Jetty package for $path"
		exit 1
	else
		echo Full Version "${fullVersion}"
	fi

	if [ -d "$path" ]; then
		# Exclude 9.2 from updated script files.
		if [[ "$version" != "9.2" ]]; then
			cp docker-entrypoint.sh generate-jetty-start.sh "$path"
		fi

		# Only generate docker file for versions past 9.4, otherwise just update existing Dockerfile.
		if greaterThanOrEqualTo9.4 "${version}"; then

			# Maintain the existing base image tag.
			prevTag=$(cat "$path"/Dockerfile | egrep "FROM $baseImage" | sed "s/.*FROM $baseImage:\([^ ]\+\)/\1/")

			# Generate the Dockerfile in the directory for this version.
			echo "# DO NOT EDIT. Edit baseDockerfile${variant:+-$variant} and use update.sh" >"$path"/Dockerfile
			cat "baseDockerfile${variant:+-$variant}" >>"$path"/Dockerfile

			# Set the Jetty and JDK/JRE versions in the generated Dockerfile.
			sed -ri 's/^(ENV JETTY_VERSION) .*/\1 '"$fullVersion"'/; ' "$path/Dockerfile"
			sed -ri 's/^FROM IMAGE:TAG/'"FROM $baseImage:$prevTag"'/; ' "$path/Dockerfile"
		else
			sed -ri 's/^(ENV JETTY_VERSION) .*/\1 '"$fullVersion"'/; ' "$path/Dockerfile"
		fi
	fi
done
