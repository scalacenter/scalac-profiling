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
</style>
</head>
<body>

# Scalac-profiling

#### Jorge Vicente Cantero ([jvican][]), [Scala Center][]

`scalac-profiling` is a tool to profile the compilation of your Scala
projects and identify bottlenecks with implicit search and macro expansion.

## Motivation

Scala codebases grow over time, and so do compile times. When compile times
become slow, they can affect both your team's productivity.

How to battle slow compile times? The first and most important step is to
measure them.

A common cause of slow compile times is a misuse of macros or implicits.
`scalac-profiling` helps you chase down compilations bottlenecks, with a
special focus on macro expansions and implicit search.

In this guide, we go on a journey to analyze slow compile times in several
real-world Scala codebases. In every case study, I walk you through an array
of techniques you can apply to speed up the compilation of your projects.

At the end of this document, you will be able to replicate some of my
experiments in your own projects and find solutions to increase the
productivity of your team. There are many other ways of scaling up big
codebases, but in this document we only look into ways to alleviate the price
of expensive features in the Scala language.

## Case studies

### [scalatest/scalatest](https://github.com/scalatest/scalatest)

Scalatest is the most popular testing framework in the Scala community and is
widely used in the communities of other JVM-based programming languages like
Java.

In this case study, we're going to compile the test suite of Scalatest. This
test suite is **300.000 LOC** and makes a heavy use of Scalatest macros (for
example, to get the position of a certain assertion, or to make typesafe
comparisons between numeric values) and implicits.

This test suite is an extreme case of what your project's test suite could
look like , but it's a good example to test our skills identifying
bottlenecks in implicit search and macro expansions.

#### First compilation

| Total typer time | Total compilation time |
| ---------------- | ---------------------- |
| asdf | asdf |

### [circe/circe](https://github.com/circe/circe)

### [guardian/frontend](https://github.com/guardian/frontend)

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

[graphviz]: http://www.graphviz.org/doc/info/command.html
[flamegraph]: https://github.com/brendangregg/FlameGraph
[flamegraph-acm]: http://queue.acm.org/detail.cfm?id=2927301
[jvican]: https://github.com/jvican
[scala center]: https://scala.epfl.ch
[jquery.graphviz.svg]: https://github.com/jvican/jquery.graphviz.svg
