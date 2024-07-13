---
id: installation
title: Installation
---

### Pick the right version

| Scala series | Supported versions  | `scalac-profiling` |
|:-------------|:--------------------|:-------------------|
| 2.12.x       | @SCALA212_VERSIONS@ | `@VERSION@`        |
| 2.13.x       | @SCALA213_VERSIONS@ | `@VERSION@`        |

### Add the dependency 

Add the scalac compiler plugin into your build:

```scala
addCompilerPlugin("ch.epfl.scala" %% "scalac-profiling" % "@VERSION@" cross CrossVersion.full)
```

Also, it's required to enable compiler statistics — for Scala 2.13 the needed compiler 
flag is `-Vstatistics`, and for Scala 2.12 is `-Ystatistics`.

For example, for the SBT build tool, add the following settings to `build.sbt`:

```diff
+ inThisBuild(
+   List(
+     addCompilerPlugin("ch.epfl.scala" %% "scalac-profiling" % "@VERSION@" cross CrossVersion.full),
+     ThisBuild / scalacOptions += "-Vstatistics",
+   )
+ )
```

You can also use project-scoped settings if you want to profile a particular project:

```diff
lazy val myproject = project
  .settings(
+   addCompilerPlugin("ch.epfl.scala" %% "scalac-profiling" % "@VERSION@" cross CrossVersion.full),
+   ThisBuild / scalacOptions += "-Vstatistics",
  )
```

### Extend the configuration 

There are several compiler plugin options to enable to enrichment of analysis capabilities.
All the following options are prepended by the `-P:scalac-profiling:`.

| Name                                     | Description                                                                                                                                                                                                                                         |
|:-----------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `generate-global-flamegraph`             | Creates a global flamegraph of implicit searches for all compilation units. Use the `-P:scalac-profiling:cross-target` option to manage the target directory for the resulting flamegraph file, otherwise, the SBT target directory will be picked. |
| `generate-macro-flamegraph`              | Generate a flamegraph for macro expansions. The flamegraph for implicit searches is enabled by default.                                                                                                                                             |
| `generate-profiledb`                     | Generate profiledb.                                                                                                                                                                                                                                 |
| `print-failed-implicit-macro-candidates` | Print trees of all failed implicit searches that triggered a macro expansion.                                                                                                                                                                       |
| `print-search-result`                    | Print the result retrieved by an implicit search. Example: `-P:scalac-profiling:print-search-result:$MACRO_ID`.                                                                                                                                     |
| `show-concrete-implicit-tparams`         | Use more concrete type parameters in the implicit search flamegraph. Note that it may change the shape of the flamegraph.                                                                                                                           |
| `show-profiles`                          | Show implicit searches and macro expansions by type and call-site.                                                                                                                                                                                  |
| `sourceroot`                             | Tell the plugin what is the source directory of the project. Example: `-P:scalac-profiling:sourceroot:$PROJECT_BASE_DIR`.                                                                                                                           |
| `cross-target`                           | Tell the plugin what is the cross target directory of the project. Example: `-P:scalac-profiling:cross-target:$PROJECT_TARGET`.                                                                                                                     |
