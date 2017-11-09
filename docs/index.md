<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"><!--*-markdown-*-->
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Scalac-profiling: profile Scala compile times</title>
<style type="text/css">
  body          { font-family: Times New Roman; font-size: 16px;
                  line-height: 125%; width: 36em; margin: 2em; }
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

`scalac-profiling` aims at providing the tools to understand and profile your
Scala projects. In this document, I dive into how you can use `scalac-profiling`
to profile implicit search.

**Note** that `scalac-profiling` is not released yet, so the installation steps
in this document are shallow on purpose.

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
searches happened during compilation.  The supported graph representations are
[graphviz][] (aka dot) and [flamegraph][].

The compiler plugin does not generate the graphs for you; instead, it persists
the graph data in a format that allows you to generate the graphs yourself
without touching or transforming the data.

These graphs are present under the *profiledb META-INF directory*, located in
your classes directory. For example, a flamegraph data file can be located at
`target/scala-2.12/classes/META-INF/profiledb/graphs/$name.flamegraph`.

### Flamegraphs

If this is the first time you hear about flamegraphs, have a look at the
[official website][flamegraph] and the [ACM article][flamegraph-acm].

Flamegraphs are graphs that allow you to see the stack of all the implicit
search calls that have happened during a concrete compilation.  They are
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

### Dot graphs

The process to generate dot graphs is similar to Flamegraph. Dot graph files are
files that declare a graph and tell Graphviz how it should be rendered.  They
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
in total, and how much time they took in total.  An edge represents the
dependency between an implicit search and another one.

> <span class="smaller">
Note that every node can be depended upon by others and be the start of the
implicit search in the program. That means that often the amount of times a node
has been searched for will not be equal to the sum of the nodes that depend on
it.

#### Examples

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
[Scala Center]: https://scala.epfl.ch
[jquery.graphviz.svg]: https://github.com/jvican/jquery.graphviz.svg
