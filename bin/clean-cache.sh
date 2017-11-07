#!/usr/bin/env bash
set -eu
set -o nounset

ls -al /drone

ls -al /root/.ivy2
ls -al /root/.coursier
ls -al /root/.sbt

find "/root/.ivy2/cache"     -name "ivydata-*.properties" -print -delete
find "/root/.sbt"            -name "*.lock"               -print -delete
