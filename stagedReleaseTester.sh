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
paths=( $(find -mindepth 4 -maxdepth 5 -name "Dockerfile" | sed -e 's/\.\///' | sed -e 's/\/Dockerfile//' | sort -nr) )
paths=( "${paths[@]%/}" )

for path in "${paths[@]}"; do
	imageTag="${path##*/}"
	remainingPath="${path%/*}"
	jettyVersion="${remainingPath##*/}" # "9.2"
	baseImage="${remainingPath%/*}"

	jettyHomeUrl="${jettyHomeFromPartialVersion[$jettyVersion]}"
	if [ -z "$jettyHomeUrl" ]; then
		echo "Did not Update: $path"
		continue
	fi

	fullVersion="${fullVersionFromPartialVersion[$jettyVersion]}"
	if [ -z "$jettyHomeUrl" ]; then
		echo "Did not Update: $path"
		continue
	fi

	# Select the variant of the baseDockerfile to use.
	if [[ $imageTag == *"alpine"* ]]; then
		variant="alpine"
	elif [[ $imageTag == *"slim"* ]]; then
		variant="slim"
	elif [[ $baseImage == "eclipse-temurin" ]]; then
		variant="slim"
	elif [[ $baseImage == "amazoncorretto" ]]; then
		variant="amazoncorretto"
	elif [[ $baseImage == "azul/zulu-openjdk" ]]; then
		variant="slim"
	elif [[ $baseImage == *"alpine"* ]]; then
		variant="alpine"
	else
		variant=""
	fi

	if greaterThanOrEqualTo9.4 "${jettyVersion}"; then
		# Maintain the existing base image tag.
		prevTag=$(cat "$path"/Dockerfile | egrep "FROM $baseImage" | sed "s|.*FROM $baseImage:\([^ ]\+\)|\1|")

		# Generate the Dockerfile in the directory for this jettyVersion.
		echo "# DO NOT EDIT. Edit baseDockerfile${variant:+-$variant} and use update.sh" >"$path"/Dockerfile
		cat "baseDockerfile${variant:+-$variant}" >>"$path"/Dockerfile

		# Set the Jetty and JDK/JRE versions in the generated Dockerfile.
		sed -ri 's/^(ENV JETTY_VERSION) .*/\1 '"$fullVersion"'/; ' "$path/Dockerfile"
		sed -ri 's|^FROM IMAGE:TAG|'"FROM $baseImage:$prevTag"'|; ' "$path/Dockerfile"

		# Set the URL of jetty-home.
		sed -ri 's|^(ENV JETTY_TGZ_URL) .*|\1 '"$jettyHomeUrl"'|; ' "$path/Dockerfile"

		echo "Successfully Updated: $path to $fullVersion"
	else
		echo "Did not Update: $path"
	fi
done
