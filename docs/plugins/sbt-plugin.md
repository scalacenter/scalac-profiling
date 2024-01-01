---
id: sbt-plugin
title: SBT Plugin
---

The SBT plugin allows users to warm up the compiler before measuring compilation times 
and analyzing statistics. This plugin is simple in its goals and bundles a set 
of tips that users of this plugin must take into account to get reliable data.

### Installation

Add the plugin into `project/plugins.sbt`:

```scala
addSbtPlugin("ch.epfl.scala" % "sbt-scalac-profiling" % "@SBT_PLUGIN_VERSION@")
```

### Usage

Run the `profilingWarmupCompiler` task in SBT in your CI / local machine 
before actually performing compilation to gather the data to build graphs. 
The default warmup duration is 60 seconds. You can modify it like this:

```diff
// setting the warmup duration to 30 globally
+ Global / profilingWarmupDuration := 30
// or setting the warmup duration to 50 in one project
val myProject = project.settings(
+ profilingWarmupDuration := 50
)
```

### Several tips

To get reliable and predictable data, your infrastructure needs to be stable.
These are some encouraged practices:

1. The cpu load of the running machine must be kept low. Remove unnecessary processes and cron
   jobs that may be running on the background.
2. Enable the `-Vstatistics` option before warming up the compiler. 
   Otherwise, the warm-up will trigger JVM to decompile code and throw away optimized code. 
   The same applies for other flags/build changes that affect compilation.
3. **Do not reload** the SBT shell, or you'll need to warm up the compiler again.
