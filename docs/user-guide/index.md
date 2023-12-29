---
id: index
title: What is scalac-profiling?
---


`scalac-profiling` is a compilation profiling tool for Scala 2 projects 
which aims to help you better understand what is slowing down compilation in your project. 
As of the `@VERSION@` version, it's built for Scala 2.12 and 2.13.

### When to use scalac-profiling?

Using implicits and macros can significantly increase compilation time, 
based on their usage and your codebase organization. Suppose your project 
heavily depends on automatic code generation tools powered with macros, 
like type-classes derivation, while it's a super powerful and user-friendly technology, 
it's likely to materialize implicits for the same type many times across the project 
resulting in excessive compilation time. Given all of that, although the `scalac-profiling` 
can be used for general compilation analysis, it is best for chasing down bottlenecks 
with a focus on implicit searches and macro expansions.

### Why scalac-profiling?

With `scalac-profiling`, you can easily generate advantageous [flamegraphs][flamegraph] 
that provide next-level profiling of compilation times. Explore the following flamegraph 
of the implicit searches in the [Scala Steward][scala-steward] project 
we've built by literally adding 5 LoC to the build file and running one script 
from the FlameGraph project. Note that the following graph is clickable.

<p>
  <object data="/scalac-profiling/img/scala-steward-implicit-searches-flamegraph.svg" type="image/svg+xml" width="100%" height="100%">
  <img src="/scalac-profiling/img/scala-steward-implicit-searches-flamegraph.svg" width="100%" height="100%">
  </object>
<p>

### Maintenance status

This tool originated at the [Scala Center][scala-center] in 2017-2018
as the result of the proposal [SCP-10][scp-10], submitted by a [corporate member][corporate-membership]
of the board. The Center is seeking new corporate members to fund activities such as these,
to benefit the entire Scala community.

The `scalac-profiling` is now community-maintained, with maintenance overseen by the Center.
We invite interested users to participate and submit further improvements.



[corporate-membership]: https://scala.epfl.ch/corporate-membership.html
[flamegraph]: https://github.com/brendangregg/FlameGraph
[scala-center]: http://scala.epfl.ch
[scala-steward]: https://github.com/scala-steward-org/scala-steward
[scp-10]: https://github.com/scalacenter/advisoryboard/blob/main/proposals/010-compiler-profiling.md
