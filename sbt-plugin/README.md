## `sbt-profiling`: profiling compilations within sbt

Sbt plugin that allows users to warm up the compiler before measuring compilation times
and analyzing statistics. This plugin is simple on its goal, and bundles a set of tips
that users of this plugin must take into account to get reliable data.

### Installation

```scala
// project/plugins.sbt
addSbtPlugin("ch.epfl.scala" % "sbt-profiling" % "0.1")
```

### Use

Run `profilingWarmupCompiler` in your CI / local machine before actually compiling.

The default warmup time is 60 seconds (scoped `in Global`). Modify it with:
```scala
// setting the warmup duration to 30 globally
profilingWarmupDuration in Global := 30
// or setting the warmup duration to 50 in one project
val myProject = project.settings(
  profilingWarmupDuration := 50
)
```

### How to run 

To get reliable and predictable data, your infrastructure must be stable.

These are some encouraged practices:

1. The cpu load of the running machine must be kept low. Remove unnecessary processes and cron
   jobs that may be running on the background.
2. If you want to measure `-Ystatistics`, the compiler has to be warmed up with that flag.
   Otherwise, enabling `-Ystatistics` after the warm-up will trigger JVM to decompile code and
   throw away optimized code. The same applies for other flags/build changes that affect compilation.
3. Disable Intel Turbo Boost or similar depending on your CPU architecture. This technology
   throttles the microprocessor when the load is high and makes results unpredictable.
4. Run on a Linux/OSX machine preferably since the JVM is more efficient in these platforms.
5. Do not run the profiling warmup in a virtual machine. The performance of virtual machines
   are too tight to the host machine and depends on too many factors.
   These factors make results unpredictable.
6. If you aim for high predictability, you can pin the compiler threads to one concrete CPU.
   This should most of the times not be necessary.

### Future features

If you want them, open a pull-request or ask for them explicitly in Gitter or the issue tracker.

1. Don't show compilation output for warmup compiler iterations.
