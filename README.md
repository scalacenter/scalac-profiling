# Providing Better Compilation Performance Information

(This repository is **heavy work-in-progress**.)

When compile times become a problem, there is no easy way to debug the compiler
to find the places where most of the compile time is spent. This prevents Scala
developers from becoming familiar with the compile-time cost of their code, and
optimizing for it when it becomes critical.

This compiler plugin, along with changes to mainstream scalac, hopes to provide
tools to Scala developers to detect inefficient implicit searches, expanded code
from macros, and other reasons that slow down compiles.

This is still an early prototype of [SCP-010](PROPOSAL.md), the Scala Center Advisory Board proposal that prompted this work.

## What do we want to report?

### Information about macros

Per call-site, file and total:
  * How many macros are expanded?
  * How long do they take to run?
  * How many tree nodes do macros create?
  * How many of these tree nodes are discarded?

### Information about implicit search

Getting hold of this information requires changes in mainstream scalac.

Per call-site, file and total:
  * How many implicit searches are triggered by user-defined code?
  * How many implicit searches are triggered my macro code?
  * How long implicit searches take to run?
  * How many implicit search failures are?
  * How many implicit search hits are?
  * What's the ration of search failures/hits?

### Ideas to be considered

#### Tell users how to organize their code to maximize implicit search hits

Based on all the implicit search information that we collect from typer, is
it possible to advice Scala developers how to organize to optimize implicit search hits?

For instance, if we detect that typer is continuosly testing implicit candidates that fail but have higher precedence (because of implicit search priorities or implicit names in scope), can we develop an algorithm that understands priorities and is able to tell users "remove that wildcard import" or "move that implicit definition to a higher priority scope, like X"?

My hunch feeling is that we can, but this requires testing and a deeper investigation.