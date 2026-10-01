# nat2j

Takes a file written in **Natural** (the old Software AG 4GL that still runs a
lot of bank and insurance backends on top of Adabas) and spits out **Java**.

It is a toy, but an honest one: a real pipeline rather than regexes over source
text, and the places where it lies about Natural are written down at the bottom
instead of being discovered later.

```
foo.nsn
  -> ANTLR4 lexer/parser     Natural.g4
  -> parse tree
  -> AST                     AstBuilder, Ast
  -> IR                      AstToIr, Ir
  -> IR passes               PassPipeline
  -> Java source             JavaEmitter (JavaPoet)
```

Two trees instead of one because they do different jobs. The AST is just the
grammar written down, so `MOVE`, `COMPUTE` and `:=` stay three separate things.
The IR has flattened all that into one assignment node, one expression tree, and
types already worked out - so the emitter only has one case per concept to deal
with. Each stage is reachable on its own (`toAst`, `toIr`, `emit`), which means
when the output is wrong you can find out *where* it went wrong.

It handles `DEFINE DATA LOCAL`, `MOVE`, `COMPUTE`, `:=`, arithmetic, `IF`,
`FOR`, `REPEAT ... UNTIL`, `WRITE`, `PERFORM` and subroutines, over the `A`, `N`,
`P`, `I`, `F` and `L` formats. Source is case-insensitive, `*` in column 1 starts
a comment and `/*` runs to end of line.

## Running it

Needs JDK 21 (pattern switches over a sealed IR). The Java it writes out is 17,
and a test proves it by compiling the output with `--release 17`.

```sh
mvn test
java -cp target/classes:... dev.lgonnet.nat2j.Nat2J src/test/resources/fixtures/greeting.nsn
```

## Tests

`FixtureTest` is the one that counts. For each `.nsn` file it transpiles the
source, **compiles the generated Java in memory** with `-Xlint:all -Werror`,
runs it, and diffs what it printed against a `.expected` file.

I deliberately do not assert on the generated source text. Those assertions pass
while the program is still wrong, and you end up rewriting them every time the
emitter changes its formatting. Compiling it tells you the Java is valid; running
it tells you the translation actually kept the behaviour.

Generated classes write to a `PrintWriter` you pass in rather than `System.out`.
Mostly so tests can read the output without messing with global streams, but it
also means the thing is one edit away from being a Spring `@Service`.

## Things that are easy to get wrong

**Money.** Natural's `N` and `P` are decimal types. `N7.2` holds exactly two
decimal places and `0.1 + 0.2` is exactly `0.30`. Map those to `double` and you
get a different number - which in a bank is the only kind of bug anyone cares
about. So everything decimal becomes `BigDecimal`, scale 0 included, which also
means there is only one numeric type floating around the IR.

**Assignment truncates.** Natural only rounds if you write `ROUNDED`. So
`MOVE 12.349 TO #AMOUNT` where `#AMOUNT` is `N7,2` gives `12.34`, not `12.35`.

**Variables start initialised.** Natural sets up local data on entry: an `A10` is
ten blanks, an `N7.2` is `0.00`. Java would leave the String null and the first
`WRITE` would print "null", so the emitter writes initialisers out.

**Division scale.** Add and subtract take `max(left, right)`, multiply takes
`left + right`, but division gets its scale from whatever you are assigning
*into*. So it has to come down from the assignment, not up from the operands -
which is why the IR's arithmetic node carries a type at all.

**Comparing text.** Natural pads both sides out to the longer one first, so
`'AB'` and `'AB   '` are equal. `String.equals` is just wrong here, hence the
`compareAlpha` helper.

**`FOR` loops.** Natural reads the `TO` bound once on entry, so it gets hoisted
into a local - a body that changes the bound must not change the trip count. And
the loop variable is still live after the loop, so it can't be a for-init local.

## Where it is not really Natural

- no database at all - no `READ`, `FIND`, `STORE`, no Adabas
- no `MAP` or `INPUT`, so `WRITE` is the only way anything comes out
- `DEFINE DATA LOCAL` only, no `GLOBAL`, no `PARAMETER`, no groups or
  redefinitions. The unused-variable pass is only safe *because* of that - a
  redefinition aliasing two fields would make "nobody reads it" the wrong
  question
- `WRITE` does not do Natural's column layout, it just puts one space between
  operands. Field widths are honoured though, so a padded `A12` prints its
  padding, and the fixture comparison strips trailing spaces - expected-output
  files that end in whitespace don't survive being edited
- cross-format `MOVE` is an error here. Natural allows some of it with conversion
- no overflow check on an `I` target. Natural errors, `intValue()` just wraps
- `B`, `D` and `T` formats parse but are rejected on the way out

## Todo

- `ESCAPE` for early exit out of loops
- `DECIDE FOR` / `DECIDE ON`
- `MOVE ROUNDED`, and the `ADD` / `SUBTRACT` / `MULTIPLY` statement forms
- group structures and redefinitions, which also means revisiting the
  unused-variable pass
- overflow checks on `I` targets
- constant folding, then scale normalisation, in that order
- only emit the helper methods a program actually uses
- emit a Spring `@Service` and a REST endpoint instead of a `main`, which is
  the shape this would need to be useful
