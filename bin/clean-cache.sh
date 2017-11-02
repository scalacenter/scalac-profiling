#!/usr/bin/env bash
set -eu
set -o nounset

find "/drone/.ivy2/cache"     -name "ivydata-*.properties" -print -delete
find "$HOME/.coursier/cache"  -name "ivydata-*.properties" -print -delete
find "/drone/.sbt"            -name "*.lock"               -print -delete
