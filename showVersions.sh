#!/bin/bash

paths=( "$@" )
if [ ${#paths[@]} -eq 0 ]; then
	paths=( $(find -mindepth 4 -maxdepth 5 -name "Dockerfile" | sed -e 's/\.\///' | sed -e 's/\/Dockerfile//' | sort -nr) )
fi
paths=( "${paths[@]%/}" )

declare -A versionToPaths
for path in "${paths[@]}"; do
	version=$(cat "$path"/Dockerfile | egrep "ENV JETTY_VERSION" | sed "s|.*ENV JETTY_VERSION \(.*\)|\1|")
	versionToPaths["$version"]+=" $path"
done

for version in "${!versionToPaths[@]}"; do
	echo "Version: $version"
	paths=( "${versionToPaths[$version]}" )
	for path in $paths; do
		echo "    - $path"
	done
done
