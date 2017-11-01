#!/bin/bash - 
set -eou

find "$HOME/.ivy2/cache"     -name "ivydata-*.properties" -print -delete
find "$HOME/.coursier/cache" -name "ivydata-*.properties" -print -delete
find "$HOME/.sbt"            -name "*.lock"               -print -delete
