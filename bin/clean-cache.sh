#!/usr/bin/env bash
set -eu
set -o nounset

find "/drone/.ivy2/cache"     -name "ivydata-*.properties" -print -delete
find "/drone/.sbt"            -name "*.lock"               -print -delete

if [[ -d "/drone/.coursier/cache" ]]; then
  find "/drone/.coursier/cache" -name "ivydata-*.properties" -print -delete
fi

if [[ -d "$HOME/.coursier/cache" ]]; then
  find "$HOME/.coursier/cache" -name "ivydata-*.properties" -print -delete
fi
