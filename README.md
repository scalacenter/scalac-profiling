# Providing Better Compilation Performance Information

When compile times become a problem, how can Scala developers reason about
the relation between their code and compile times?

## Installation 

Add the `scalac-profiling` compiler plugin into your project:

```scala
addCompilerPlugin("ch.epfl.scala" %% "scalac-profiling" % "<version>" cross CrossVersion.full)
```

Note that in Scala 2.13, the preferred form of the compiler option to enable statistics is `-Vstatistics`. It is part of the family of `-V` flags that enable various "verbose" behaviors (in 2.12, the flag is called `-Ystatistics`).

Learn more at https://scalacenter.github.io/scalac-profiling.

Also, you may wish to read the [Speeding Up Compilation Time with `scalac-profiling`](https://www.scala-lang.org/blog/2018/06/04/scalac-profiling.html) in the scala-lang blog. Worth noting that the article is 5+ years old, and hasn't been updated. But still, you may gather a lot of ideas while reading it.

## Maintenance status

This tool was created at the [Scala Center](http://scala.epfl.ch) in 2017 and 2018 as the result of the proposal [SCP-10](https://github.com/scalacenter/advisoryboard/blob/main/proposals/010-compiler-profiling.md), submitted by a [corporate member](https://scala.epfl.ch/corporate-membership.html) of the board. The Center is seeking new corporate members to fund activities such as these, to benefit the entire Scala community.

The plugin is now community-maintained, with maintenance overseen by the Center. Thanks to volunteer contributors, the latest release 1.1.0 supports both Scala 2.13 and 2.12.

## Historical context

The historical context of this project is quite interesting. For those wondering about the details, see the [dedicated section](HISTORICALCONTEXT.md).
