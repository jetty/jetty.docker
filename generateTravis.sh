#!/bin/bash

# Generate Travis CI Build Directories
buildDirs=( $(ls | egrep '^[0-9]' | sort -nr) )

cat <<-EOH
---
language: bash

dist: trusty

env:
`printf '  - VERSION=%s\n' "${buildDirs[@]}"`

install:
  - git clone https://github.com/docker-library/official-images.git ~/official-images

before_script:
  - env | sort
  - wget -qO- 'https://github.com/tianon/pgp-happy-eyeballs/raw/master/hack-my-builds.sh' | bash
  - cd "\${VERSION}"
  - image="jetty:\${VERSION}"

script:
  - docker build --pull -t "\$image" .
  - ~/official-images/test/run.sh "\$image"
EOH