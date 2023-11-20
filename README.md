# Providing Better Compilation Performance Information

When compile times become a problem, how can Scala developers reason about
the relation between their code and compile times?

## Maintenance status

This plugin was created at the [Scala Center](http://scala.epfl.ch) in 2017 and 2018 as the result of the proposal [SCP-10](https://github.com/scalacenter/advisoryboard/blob/main/proposals/010-compiler-profiling.md), submitted by a [corporate member](https://scala.epfl.ch/corporate-membership.html) of the board. The Center is seeking new corporate members to fund activities such as these, to benefit the entire Scala community.

[Version 1.0](https://github.com/scalacenter/scalac-profiling/releases/tag/v1.0.0) of the plugin supports Scala 2.12.

The plugin is now community-maintained, with maintenance overseen by the Center. Thanks to volunteer contributors, there is now a 1.1.0-RC1 [release candidate](https://github.com/scalacenter/scalac-profiling/releases/tag/v1.1.0-RC1) targeting Scala 2.13 (in addition to 2.12). We invite interested users to test the release candidate and submit further improvements.

## Install

Add `scalac-profiling` in any sbt project by specifying the following project
setting.

```scala
addCompilerPlugin("ch.epfl.scala" %% "scalac-profiling" % "<version>" cross CrossVersion.full)
```

## How to use

To learn how to use the plugin, read [Speeding Up Compilation Time with `scalac-profiling`](https://www.scala-lang.org/blog/2018/06/04/scalac-profiling.html) in the scala-lang blog.

Note that in Scala 2.13, the preferred form of the compiler option to enable statistics is `-Vstatistics`. It is part of the family of `-V` flags that enable various "verbose" behaviors. (In 2.12, the flag is called `-Ystatistics`.)

### Compiler plugin options

All the compiler plugin options are **prepended by `-P:scalac-profiling:`**.

* `show-profiles`: Show implicit searches and macro expansions by type and
  call-site.
* `sourceroot`: Tell the plugin what is the source directory of the project.
  Example: `-P:scalac-profiling:sourceroot:$PROJECT_BASE_DIR`.
* `print-search-result`: Print the result retrieved by an implicit search.
  Example: `-P:scalac-profiling:print-search-result:$MACRO_ID`.
* `generate-macro-flamegraph`: Generate a flamegraph for macro expansions. The
  flamegraph for implicit searches is enabled by default.
* `print-failed-implicit-macro-candidates`: Print trees of all failed implicit
  searches that triggered a macro expansion.
* `no-profiledb`: Recommended. Don't generate profiledb (will be on by default
  in a future release).
* `show-concrete-implicit-tparams`: Use more concrete type parameters in the
  implicit search flamegraph. Note that it may change the shape of the
  flamegraph.

## Historical context

The historical context of this project is quite interesting. For those wondering about the details, see the [dedicated section](HISTORICALCONTEXT.md).
