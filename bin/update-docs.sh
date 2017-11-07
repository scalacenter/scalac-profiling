#!/bin/bash - 
set -o nounset                              # Treat unset variables as an error
git subtree push -f --prefix docs origin gh-pages
