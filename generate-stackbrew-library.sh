#!/bin/bash
set -ueo pipefail
shopt -s globstar

defaultJdk="jdk17"
defaultVersions=("12.0" "11.0" "10.0" "9.4")
defaultImage="eclipse-temurin"
excludedBases=("azul")

isDefaultVersion() {
	for v in "${defaultVersions[@]}"; do
		if [[ "$1" =~ ^"$v" ]]; then
			return 0
		fi
	done

	return 1
}

declare -A aliases
aliases=(
	[eclipse-temurin-11.0-jdk17]='latest jdk17'
)

cd "$(dirname "$(readlink -f "$BASH_SOURCE")")"
paths=( $(find -mindepth 4 -maxdepth 5 -name "Dockerfile" | sed -e 's/\.\///' | sed -e 's/\/Dockerfile//' | sort -nr) )
url='https://github.com/eclipse/jetty.docker.git'

cat <<-EOH
	Maintainers: Greg Wilkins <gregw@webtide.com> (@gregw),
	             Lachlan Roberts <lachlan@webtide.com> (@lachlan-roberts),
	             Olivier Lamy <olamy@webtide.com> (@olamy),
	             Joakim Erdfelt <joakim@webtide.com> (@joakime)
	GitRepo: $url
EOH

declare -a tags
declare -A tagsSeen=()
addTag() {
	local tag="$1"

	if [ ${#tagsSeen[$tag]} -gt 0 ]; then
		return
	fi

	tags+=("$tag")
	tagsSeen[$tag]=1
}

for path in "${paths[@]}"; do

	# Skip if this image has been excluded.
	for excluded in "${excludedBases[@]}"; do
		if [[ "$path" =~ ^$excluded.* ]]; then
        continue 2
    fi
	done

	tags=()

	directory="$path"
	commit="$(git log -1 --format='format:%H' -- "$directory")"
	fullVersion="$(grep -m1 'ENV JETTY_VERSION ' "$directory/Dockerfile" | cut -d' ' -f3)"

	imageTag="${path##*/}"
	remainingPath="${path%/*}"
	jettyVersion="${remainingPath##*/}" # "9.2"
	baseImage="${remainingPath%/*}"

	# We can't add a / in a tag so we must replace it.
	baseImage="$(echo $baseImage | sed -r 's/\//-/g')"

	# Collect the potential fullVersion aliases
	declare -a versionAliases
	versionAliases=()
	if [[ "$fullVersion" != *.v* ]]; then
		# From Jetty 10 we no longer use the *.v* fullVersion format.
		versionAliases+=("$fullVersion")
	fi

	partialVersion="$fullVersion"
	while [[ "$partialVersion" == *.* ]]; do
		partialVersion="${partialVersion%.*}"
		versionAliases+=("$partialVersion")
	done

	# If this is the default base image we don't need to include the base image name in the tag.
	if [ "$baseImage" = "$defaultImage" ]; then
		# Output ${versionAliases[@]} without JDK.
		# e.g. 9.2.10, 9.2
		if [ "$imageTag" = "$defaultJdk" ]; then
			for va in "${versionAliases[@]}"; do
				if [[ "$va" == *.* ]] || isDefaultVersion "$fullVersion"; then
					addTag "$va"
				fi
			done
		fi

		# Output ${versionAliases[@]} with JDK suffixes.
		# e.g. 9.2.10-jre7, 9.2-jre7, 9-jre7, 9-jre11-slim
		for va in "${versionAliases[@]}"; do
			if [[ "$va" == *.* ]] || isDefaultVersion "$fullVersion"; then
				addTag "$va-$imageTag"
			fi
		done
	fi

	# Output ${versionAliases[@]} without JDK, with the base image name.
	# e.g. 9.2.10-openjdk, 9.2-openjdk
	if [ "$imageTag" = "$defaultJdk" ]; then
		for va in "${versionAliases[@]}"; do
			if [[ "$va" == *.* ]] || isDefaultVersion "$fullVersion"; then
				addTag "$va-$baseImage"
			fi
		done
	fi

	# Output ${versionAliases[@]} with JDK suffixes and baseImage.
	# e.g. 9.2.10-jre7-openjdk, 9.2-jre7-openjdk, 9-jre7-openjdk, 9-jre11-slim-openjdk
	for va in "${versionAliases[@]}"; do
		if [[ "$va" == *.* ]] || isDefaultVersion "$fullVersion"; then
			addTag "$va-$imageTag-$baseImage"
		fi
	done

	# Output custom aliases
	# e.g. latest, jre7, jre8
	reference="$baseImage-$jettyVersion-$imageTag"
	if [ ${#aliases[$reference]} -gt 0 ]; then
		for va in ${aliases[$reference]}; do
			addTag "$va"
		done
	fi

	echo
	echo "Tags:$(IFS=, ; echo "${tags[*]/#/ }")"
	if [ -f "$directory/arches" ]; then
		echo "Architectures: $(< "$directory/arches")"
	else
		echo "Architectures: amd64, arm64v8"
	fi
	echo "Directory: $directory"
	echo "GitCommit: $commit"
done
