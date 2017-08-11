# Providing Better Compilation Performance Information
[![Build
Status](https://platform-ci.scala-lang.org/api/badges/scalacenter/scalac-profiling/status.svg)](https://platform-ci.scala-lang.org/scalacenter/scalac-profiling)

When compile times become a problem, how can Scala developers reason about
the relation between their code and compile times?

## Goal of the project
The goal of this proposal is to allow Scala developers to optimize their
codebase to reduce compile times, spotting inefficient implicit searches,
expanded macro code, and other culprits that slow down compile times and
decrease developer productivity.

This repository holds the compiler plugin and a fork of mainstream scalac
that will be eventually be merged upstream. This work is prompted by [Morgan
Stanley's proposal](PROPOSAL.md) and was approved in our last advisory board.

## Information about the setup

The project uses a forked scalac version that is used to compile both the compiler plugin
and several OSS projects from the community. The integration tests are for now [Circe](https://github.com/circe/circe) and [Monocle](https://github.com/julien-truffaut/Monocle),
and they help us look into big profiling numbers and detect hot spots and misbehaviours.

If you think a particular codebase is a good candidate to become an integration test, please [open an issue](https://github.com/scalacenter/scalac-profiling/issues/new).

### Structure

1. [A forked scalac](scalac/) with patches to collect profiling information.
   All changes are expected to be ported upstream.
2. [A compiler plugin](plugin/) to get information from the macro infrastructure independently
   of the used Scalac version.

## Collected data

In the following secions, I elaborate on the collected data that we want to extract from the compiler as well as technical details for every section in the [original proposal](PROPOSAL.md).

(This repository is **heavy work-in-progress**. Expect things to change.)

### Information about macros
Per call-site, file and total:
* How many macros are expanded? :heavy_check_mark:
* How long do they take to run?
* How many tree nodes do macros create? :heavy_check_mark:
* How many of these tree nodes are discarded?
* What's the ratio of generated code/user-defined code?

### Information about implicit search
Getting hold of this information requires changes in mainstream scalac.

Per call-site, file and total:
* How many implicit searches are triggered by user-defined code?
* How many implicit searches are triggered my macro code?
* How long implicit searches take to run?
* How many implicit search failures are?
* How many implicit search hits are?
* What's the ratio of search failures/hits?

### 

### Ideas to be considered

#### Tell users how to organize their code to maximize implicit search hits
Based on all the implicit search information that we collect from typer, is
it possible to advice Scala developers how to organize to optimize implicit
search hits?

For instance, if we detect that typer is continuosly testing implicit
candidates that fail but have higher precedence (because of implicit search
priorities or implicit names in scope), can we develop an algorithm that
understands priorities and is able to tell users "remove that wildcard
import" or "move that implicit definition to a higher priority scope, like
X"?

(My hunch feeling is that we can, but this requires testing and a deeper
investigation.)
#### Report on concrete, inefficient macros
Macro-generated code is usually inefficient because macro authors do not
optimize for compactness and compile times and express the macro logic with
Scala.

Instead, they could use low-level constructs that spare work to the compiler
(manually generating getters and setters, code-generating shorter fresh
names, spare use of `final` flags, explicitly typing all the members,
avoiding the use of traits, et cetera).

Another efficiency of macros is that different call-sites that invoke a macro
with the same inputs generate different trees with identical semantics. This
lack of caching at the macro level is one of the main sources for inefficient
code.

Ideally, this plugin would be able to:
1. Identify inefficient expanded code with tree-size heuristics and the use
   of particular features that could be expressed in a more low-level manner.
2. Tell users if there's any repetition in the expanded code.
3. Let users inspect the macro generated code to manually investigate inefficient
   macro expansions. The generated code could be written in a directory passed in
   via compiler plugin settings, and would be disabled by default.

As a side note, repetitions in expanded code can be addressed in two ways:
* Create a cache of expanded code in the compiler macro infrastructure.
* Create a cache of expanded code in the macro implementation.

Both alternatives are **challenging**, if not impossible. The easiest way to
cache implicits is that the developers of implicit-intensive codebases create
their own objects storing implicit values for all the target types and
imports them in all the use sites.



#### More to come...