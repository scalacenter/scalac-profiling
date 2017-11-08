<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"><!--*-markdown-*-->
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Minimal Value Types (Shady Edition)</title>
<style type="text/css">
  body          { font-family: Times New Roman; font-size: 16px;
                  line-height: 125%; width: 36em; margin: 2em; }
  code, pre     { font-family: Courier New; }
  blockquote    { font-size: 14px; line-height: 130%; }
  pre           { font-size: 14px; line-height: 120%; }
  h1            { font-size: 24px; }
  h3            { font: inherit; font-weight:bold; }
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

<!-- $ pandoc --smart home.md -o index.html -->

# Scalac-profiling
#### Jorge Vicente Cantero ([jvican]), [Scala Center]

## Implicit search graphs with dot

`scalac-profiling` generates dot graph of all the implicit searches happened
during compilation. These graphs will help you to reason about your code better
and profile slow compile times in big applications.

### How should you read the graphs?

A graph is a set of nodes and edges. A node represent an implicit search for a
given type. Every node tells you how many implicit searches have been triggered
in total, and how much time they took in total.  An edge represents the
dependency between an implicit search and another one.

> <span class="smaller">
Note that every node can be depended upon by others and be the start of the
implicit search in the program. That means that often the amount of times a node
has been searched for will not be equal to the sum of the nodes that depend on
it.

### Examples

* [circe website example implicit search graph](circe-integration.html)

[jvican]: https://github.com/jvican
[Scala Center]: https://scala.epfl.ch
