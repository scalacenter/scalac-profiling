<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Scalac-profiling: profile Scala compile times</title>
<style type="text/css">
  body          { font-family: Helvetica Neue, Helvetica, Times New Roman; font-size: 16px;
                  line-height: 125%; width: 40em; margin: 2em auto; }
  code, pre     { font-family: Input Mono Sans, Courier New, Courier, monospace; }
  blockquote    { font-size: 14px; line-height: 130%; }
  pre           { font-size: 14px; line-height: 120%; }
  code          { font-size: 15px; }
  h1            { font-size: 24px; }
  h2            { font-size: 20px; }
  h3            { font: inherit; font-weight:bold; font-size: 18px; }
  pre           { padding: 1ex; background: #eee; width: 40em; }
  h3            { margin: 1.5em 0 0; }
  ol, ul, pre   { margin: 1em 1ex; }
  ul ul, ol ol  { margin: 0; }
  blockquote    { margin: 1em 4ex; }
  p             { margin: .5em 0 .5em 0; }
  h4            { margin: 0; }
  a             { text-decoration: none; }
  div.smaller   { font-size: 85%; }
  span.smaller  { font-size: 95%; }
  .sourceCode   { overflow: auto; }
</style>
</head>
<body>

# Scalac-profiling

#### Jorge Vicente Cantero ([jvican][]), [Scala Center][]

`scalac-profiling` is a tool to profile the compilation of your Scala
projects and identify bottlenecks with implicit search and macro expansion.

## Motivation

Scala codebases grow over time, and so do compile times. Some of them are
inherent to the size of your application, but some of them are not, and they
they can affect both your team's productivity.

How to battle slow compile times? The first and most important step is to
measure them.

A common cause of slow compile times is an abuse of macros or misuse of
implicits. `scalac-profiling` helps you chase down compilations bottlenecks,
with a focus on macro expansions and implicit search.

In this guide, we go on a journey to analyze slow compile times in several
real-world Scala codebases. In every case study, I walk you through an array
of techniques you can apply to speed up the compilation of your projects.

At the end of this document, you will be able to replicate some of my
experiments in your own projects and find solutions to increase the
productivity of your team.

There are many other ways of scaling up _big_ codebases, but in this document
we only look into ways to alleviate the price of expensive features in the
Scala language.

**Note** 
<span class="smaller">
All the numbers in this guide have not been obtained directly from the
build tool but from a JMH benchmark battery. This benchmarking facility
enables us to get stabler results and does not take into account potential
regressions that your build tool could introduce. Study those separately.
</div>

## Case study: [scalatest/scalatest](https://github.com/scalatest/scalatest)

Scalatest is the most popular testing framework in the Scala community. It is
known by its extensibility, easiness of use and great error reporting
facilities.

In this case study, we're going to profile the compilation of Scalatest's
test suite. This test suite is **300.000 LOC** and makes a heavy use of
implicits and Scalatest macros (for example, to get the position of a certain
assertion, or to make typesafe comparisons between numeric values).

This test suite is an _extreme_ case of how your project's test suite would
look like, but it's a good example to test our skills identifying
bottlenecks in implicit search and macro expansions.

Let's first learn more about the project we're about to profile with [cloc][].

```sh
jvican in /data/rw/code/scala/scalatest                                                 [10:51:09]
> $ loc scalatest-test                                                              [±3.1.x-stats]
--------------------------------------------------------------------------------
 Language             Files        Lines        Blank      Comment         Code
--------------------------------------------------------------------------------
 Scala                  775       380316        50629        29014       300673
 Java                    13          332           38          195           99
 XML                      1            8            0            0            8
 Plain Text               1            9            5            0            4
--------------------------------------------------------------------------------
 Total                  790       380665        50672        29209       300784
--------------------------------------------------------------------------------
```

Scalatest's test suite has about 300.000 LOC of pure Scala code.

These lines do not take into account generated code;
the code that gets compiled by scalac is _much bigger_ after code
generation and macros are expanded. But, how big?

Before we proceed 

#### Taking into consideration expanded code

When analyzing compile times, we must be aware of the _"hidden"_ cost that
we pay to compile the expanded and generated code. At first glance,

Sometimes the compiler may spend more time compiling that code may be suboptimal. For example, if the macro generates
high-level, poorly optimized code

### Compilation without any changes

| Time in typer | Total compilation time |
| ------------- | ---------------------- |
| asdf          | asdf                   |

### Compilation without the position macro

### Compilation without the position macro and caching implicits

## Case study: [circe/circe](https://github.com/circe/circe)

## Case study: [guardian/frontend](https://github.com/guardian/frontend)

## Profiling implicit search

Profiling implicit search is useful to answer the following questions:

1. What implicit searches happen in my project?
1. How do all implicit searches interact with each other?
1. Are implicit searches the main bottleneck of my compile times?

Incidentally, implicit searches also help you to assess how expensive the use of
a macro library is, specifically if the macro library relies heavily on implicit
search. Libraries that provide generic typeclass derivation are an example.

### Graphs

`scalac-profiling` generates several graph representations of all the implicit
searches happened during compilation. The supported graph representations are
[graphviz][] (aka dot) and [flamegraph][].

The compiler plugin does not generate the graphs for you; instead, it persists
the graph data in a format that allows you to generate the graphs yourself
without touching or transforming the data.

These graphs are present under the _profiledb META-INF directory_, located in
your classes directory. For example, a flamegraph data file can be located at
`target/scala-2.12/classes/META-INF/profiledb/graphs/$name.flamegraph`.

### Flamegraphs

If this is the first time you hear about flamegraphs, have a look at the
[official website][flamegraph] and the [ACM article][flamegraph-acm].

Flamegraphs are graphs that allow you to see the stack of all the implicit
search calls that have happened during a concrete compilation. They are
intuitive to inspect and to browse, and stand out because:

* They allow you to selectively choose what things to profile. Click on every
  stack to zoom in, and reset by clicking "Reset zoom" on the bottom left.
* They allow you to search via regexes and those matching stacks are
  highlighted. Check the search button on the top right.

#### Flamegraph generation

In order to generate flamegraphs, clone [brendangregg/FlameGraph][flamegraph]
GitHub repository. The repository provides the tools to generate the svg files
that you will later on use.

Next, compile your project with `scalac-profiling` as a compiler plugin, and
statistics enabled (`-Ystatistics`). `scalac-profiling` will generate the
flamegraph data in the profiledb `graphs` directory explained in [the Graphs
section](#graphs).

Run the following command in the Flamegraph project's root directory:

    ./flamegraph.pl --countname="ms" \
        $PATH_TO_FLAMEGRAPH_FILE > graph.svg

And it will generate something like [this](circe-integration-flamegraph.svg).

#### Examples

* [circe website example flamegraph](circe-integration-flamegraph.svg)
* [circe test suite flamegraph](circe-test-suite-flamegraph.svg)
* [scalac flamegraph](scalac-flamegraph.svg)
* [monocle example flamegraph](monocle-example-flamegraph.svg)
* [monocle test suite flamegraph](monocle-test-suite-flamegraph.svg)
* [scalatest core flamegraph](scalatest-core-flamegraph.svg)
* [scalatest tests flamegraph](scalatest-tests-flamegraph.svg)

### Dot graphs

The process to generate dot graphs is similar to Flamegraph. Dot graph files are
files that declare a graph and tell Graphviz how it should be rendered. They
can then be visualized in several ways: a `png` file, a pdf, a svg, et cetera.

#### Dot graph generation

Install [graphviz][].

Read [the previous Flamegraph generation](#flamegraph-generation) first.

When you compile your project with `scalac-profiling`, the plugin creates dot
files along with the flamegraph data files. You can use these dot files to
generate a graph with the following command:

    dot -Tsvg -o graph.svg $PATH_TO_DOT_FILE

After that, you can open the resulting svg with your favorite web browser. For
example, by running `firefox graph.svg`.

However, you will quickly realize that exploring larger graphs is difficult with
a normal svg. I recommend using [jquery.graphviz.svg][] to have a nicer browsing
experience: zoom in and out, reset zoom and the killer feature: only highlight
the edges for a given node (by clicking on the node).

#### Reading the generated graphs

A graph is a set of nodes and edges. A node represent an implicit search for a
given type. Every node tells you how many implicit searches have been triggered
in total, and how much time they took in total. An edge represents the
dependency between an implicit search and another one.

> <span class="smaller">
Note that every node can be depended upon by others and be the start of the
implicit search in the program. That means that often the amount of times a node
has been searched for will not be equal to the sum of the nodes that depend on
it.

#### Dot graph examples

* [circe website example dot graph](circe-integration.html)
* [circe test suite dot graph](circe-test-suite.html)
* [scalac dot graph](scalac.html)
* [monocle example dot graph](monocle-example.html)
* [monocle test suite dot graph](monocle-test-suite.html)
* [scalatest dot graph](scalatest-core.html)

# Appendix

## A: Notes on lines of code in Scala

Scala's LOC do not have a direct translation to other programming languages
because of the conciseness of Scala syntax and its metaprogramming
facilities. A one-to-one comparison to other programming languages is most
likely going to be misleading.

The best way to _compare compiler speed_ across different programming
languages is to compare end applications or libraries with feature parity and
implemented in the same programming paradigm.

## B: Metaprogramming facilities in Scala

Using macros is easy, and it won't be the first time developers use them
without noticing. It is important that, if you want to reduce compile times,
you carefully have a look at the scalac profiles and your dependency graph.

The cost of macros depend on how important the use case they solve is to you.
Consider this when optimizing your compile times.

Sometimes, the cost of macros can be reduced by optimizing the implementation.
But the first step is to know, in advance, that a macro is expensive. Thus,
bring the numbers up with the macro implementors so that they think of ways to
make macros more lightweight.

[graphviz]: http://www.graphviz.org/doc/info/command.html
[flamegraph]: https://github.com/brendangregg/FlameGraph
[flamegraph-acm]: http://queue.acm.org/detail.cfm?id=2927301
[jvican]: https://github.com/jvican
[scala center]: https://scala.epfl.ch
[jquery.graphviz.svg]: https://github.com/jvican/jquery.graphviz.svg
[cloc]: https://github.com/cloc/cloc
