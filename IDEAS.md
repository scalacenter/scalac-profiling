# Ideas to be considered

These are out of the scope of this project for now.

### Tell users how to organize their code to maximize implicit search hits

Based on all the implicit search information that we collect from typer, is
it possible to advice Scala developers how to organize to optimize implicit
search hits?

For instance, if we detect that typer is continuosly testing implicit
candidates that fail but have higher precedence (because of implicit search
priorities or implicit names in scope), can we develop an algorithm that
understands priorities and is able to tell users "remove that wildcard
import" or "move that implicit definition to a higher priority scope, like
X"?

(My hunch feeling is that we can, but this requires testing and a deeper
investigation.)

### Report on concrete, inefficient macros

Macro-generated code is usually inefficient because macro authors do not
optimize for compactness and compile times and express the macro logic with
high-level Scala.

Instead, if they really become a bottleneck, they could use low-level constructs that
spare work to the compiler (manually generating getters and setters, code-generating
shorter fresh names, spare use of `final` and `private[this]` flags, explicitly typing
all the members, avoiding the use of traits, et cetera).

(The compile time difference between an optimized macro and an unoptimized one has
yet to be measured, but it could be significant under concrete scenarios).

A well-known problem of macros is that different call-sites that invoke a
macro with the same inputs will generate different trees with identical
semantics. This lack of caching at the macro level is one of the main
problems affecting compile times, especially when it comes to typeclass
derivation.

Ideally, this plugin would be able to:

1. Identify inefficient expanded code with tree-size heuristics and the use
   of particular features that could be expressed in a more low-level manner.
1. Tell users if there's any repetition in the expanded code.
1. Let users inspect the macro generated code to manually investigate inefficient
   macro expansions. The generated code could be written in a directory passed in
   via compiler plugin settings, and would be disabled by default.

As a side note, repetitions in expanded code can only be addressed by the user.

* Create a cache of expanded code in the compiler macro infrastructure.
* Create a cache of expanded code in the macro implementation.

Both alternatives are **challenging**, if not impossible. The easiest way to
cache implicits is that the
Developers of `implicit` and `macro`-intensive codebases can cache the macro
results in values of objects for all the target types at the same definition
site (so that implicit values can be reused instead of triggering a macro call).

