# Providing Better Compilation Performance Information

[![Build Status](https://platform-ci.scala-lang.org/api/badges/scalacenter/scalac-profiling/status.svg)](https://platform-ci.scala-lang.org/scalacenter/scalac-profiling)  (:warning: This repository is **heavy work-in-progress**. :warning:)

When compile times become a problem, how can Scala developers reason about their code and how much time it takes to compile?

## Goal of the project

The goal of this proposal is to allow Scala developers to optimize their
codebase to reduce compile times, spotting inefficient implicit searches,
expanded macro code, and other culprits that slow down compile times and
decrease developer productivity.

This repository holds the compiler plugin and a fork of mainstream scalac that
will be eventually be merged upstream. This work is prompted by [Morgan Stanley's proposal](PROPOSAL.md) and was approved in our last advisory board.

## Collected data

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

(My hunch feeling is that we can, but this requires testing and a deeper investigation.)