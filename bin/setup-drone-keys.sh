#!/bin/bash - 
set -o nounset

# This script is only meant for maintainers that have the drone CLI installed locally.

# $1 -> Sonatype user
# $2 -> Sonatype password
# $3 -> Bintray user
# $4 -> Bintray password
# $5 -> PGP password

drone secret add  --event=push --event=tag --event=deployment --image=scalacenter/scala-publish:1.3 scalacenter/scalac-profiling SONATYPE_USER "$1"
drone secret add  --event=push --event=tag --event=deployment --image=scalacenter/scala-publish:1.3 scalacenter/scalac-profiling SONATYPE_PASSWORD "$2"

drone secret add  --event=push --event=tag --event=deployment --image=scalacenter/scala-publish:1.3 scalacenter/scalac-profiling BINTRAY_USER "$3"
drone secret add  --event=push --event=tag --event=deployment --image=scalacenter/scala-publish:1.3 scalacenter/scalac-profiling BINTRAY_PASSWORD "$4"

drone secret add  --event=push --event=tag --event=deployment --image=scalacenter/scala-publish:1.3 scalacenter/scalac-profiling PGP_PASSWORD "$5"
