# Providing Better Compilation Performance Information

## Proposer

Proposed by James Belsey, Morgan Stanley, May 2017

## Abstract

As a user of the Scala compiler investigating slow build times is difficult.
We propose enhancing the current flags and tooling within the Scala compiler to
identify hotspots in or caused by users' code.   For example a macro may be
significant or called more often than expected (e.g. in implicit resolution).
Providing tooling that generates user-understandable reports e.g. per macro
times called and total times or identifying poor implicit resolution allows
users to tune their builds for optimal performance.

This can be broken into three distinct areas:

- data generation and capture
- data visualisation and comparison
- reproducibility

One important consideration is that the Instrumentation must be carefully
engineered to ensure that when not in use it does not adversely affect
compilation time when disabled.

## Proposal

Generation includes capturing information such as:

- Compilation time per file
  - Total
  - Broken down by phase
- Times per macro
  - Per file
  - Per macro
    - Invocations
    - Total time
- implicit search details (time and number)
  - By type
  - By invocation
  - By file
- User time, kernel time, wall clock, I/O time
- Time for flagged features (for certain features – e.g. optimisation)
- Time resolving types from classpath
  - Total
  - by jar
- Imports – unused/wildcard timings?

Other avenues might include providing information about file dependencies, for
example those that cause the incremental compiler to fail and fall back to full
compilation.  Or islands of compilation which could benefit from being split
into separate modules.  These features may come out of the semantic database
work by Eugene Burmako et al.

The generated data should be generated in both machine and human consumable
form.

Human-readable reports (e.g. files sorted by compile time) are a key artifact
produced by this work.  This could for example be HTML reports summarizing and
organising the data along various axes.  For example for macros it would be
useful to see both by macro and by file location when they are used.

Machine readable data should allow both external tools to support investigation
and allow investigation between different runs (not just the investigation of a
single run).  This data allows it to be integrated into CI and regression
testing.

The generated profiling numbers should have high reproducibility and reflect
the real behaviour – this may include warming the compiler and other profiling
techniques to ensure consistency between runs, i.e. if I make a change it is
important that you have high confidence the build is faster or better.

## Cost

Unknown at this stage.

## Timescales

Unknown at this stage.
