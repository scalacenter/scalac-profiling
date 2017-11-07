#!/bin/bash - 
set -o nounset                              # Treat unset variables as an error

cp "$1" demo.dot
dot -Tsvg -o demo.svg demo.dot
