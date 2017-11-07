#!/bin/bash - 
set -o nounset                              # Treat unset variables as an error
git subtree push --prefix docs origin gh-pages
