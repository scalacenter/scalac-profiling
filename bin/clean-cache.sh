#!/usr/bin/env bash
set -eu
set -o nounset

echo "DRONE"
ls -al /drone
echo "DRONE IVY"
ls -al /drone/.ivy2
echo "DRONE COURSIER"
ls -al /drone/.coursier
echo "DRONE SBT"
ls -al /drone/.sbt

echo "ROOT"
ls -al /root
echo "ROOT IVY"
ls -al /root/.ivy2
echo "ROOT COURSIER"
ls -al /root/.coursier
echo "ROOT SBT"
ls -al /root/.sbt

find "/root/.ivy2/cache"     -name "ivydata-*.properties" -print -delete
find "/root/.sbt"            -name "*.lock"               -print -delete
