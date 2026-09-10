# YMXS

The format: the tune data structure, and how a player or an emulator maps
it to an Atari ST's YM2149 and MC68901.

`src/main/java/org/ymxs/` is the specification: records that define the
structure, in terms a compiler checks. [doc/SPEC.md](doc/SPEC.md) defines
what a player or an emulator does with them, and lists the records once,
where a test reads that listing back against the records themselves.

A form is that structure written down. This repository has JSON, which a
tracker writes and a player's tools read.

A player is a separate program. Each one says which version of this
format it reads.

The one dependency is a JSON library, which reads and writes the tree a
tune maps to. The structure itself compiles without it.

## What is here

| | |
|---|---|
| [`YMXS.java`](src/main/java/org/ymxs/YMXS.java) | the structure, defined for a player in [SPEC.md](doc/SPEC.md) |
| [`Chip`](src/main/java/org/ymxs/Chip.java) | the two chips' own figures |
| [`Tunes`](src/main/java/org/ymxs/Tunes.java) | what is read off a structure |
| [`Check`](src/main/java/org/ymxs/Check.java) | the rules a structure must satisfy |
| [`Json`](src/main/java/org/ymxs/Json.java) [`Text`](src/main/java/org/ymxs/Text.java) | JSON, written out in [json.md](doc/json.md) |
| [`Csv`](src/main/java/org/ymxs/Csv.java) | CSV, in [csv.md](doc/csv.md) |
| [`tool/`](src/main/java/org/ymxs/tool) [`bin/`](bin) | the tools, each a filter, in [tools.md](doc/tools.md) |
| [`ym/`](src/main/java/org/ymxs/ym) | an example: a YM register dump read in, in [ym.md](doc/ym.md) |
| [`doc/tunes/`](doc/tunes) | six tunes in both forms, which the tests read back |

`YMXS.java` is bare records and sealed interfaces, whose only methods are
their accessors. A structure is read by a function outside it, by pattern
matching, so a shape added to `YMXS` stops those functions compiling until
they read it.

## Building

Java 23 and Maven. `mvn test` reads every tune under `doc/tunes` back in
both forms, checks SPEC.md's listing against the records, reads a dump the
test writes itself and an archive with another inside, runs the tools in a
pipe, and checks the documents against the house style.

```bash
bin/ym-to-ymxs < tune.ym | bin/ymxs-check | bin/ymxs-json-to-csv > tune.csv
```

Every tool reads standard input and writes standard output, so they stand
in a pipe. [doc/tools.md](doc/tools.md) has them.

## Where the figures come from

Every measurement in the javadoc and in the documents was read off 49
tunes converted from register dumps of the scene's own music: 331,376
rows and 27,004 effect entries. Each record's fields, and the measurement
behind each choice, stand beside it in the javadoc.
