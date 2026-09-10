# YMXS

The format: the tune data structure, and its mapping to the Atari ST's
YM2149 and MC68901.

`src/main/java/org/ymxs/` is the specification. The records there define
the structure in terms a compiler checks. [doc/SPEC.md](doc/SPEC.md)
defines what a player or an emulator does with them, and lists the records
once, where a test reads that listing back against the records.

A form is the structure written down. This repository defines two, JSON
and CSV.

A player is a separate program, and pins a version of this format.

The one dependency is a JSON library, for the tree that form maps to. The
structure compiles without it.

## What is here

| | |
|---|---|
| [`YMXS.java`](src/main/java/org/ymxs/YMXS.java) | the structure, defined for a player in [SPEC.md](doc/SPEC.md) |
| [`Chip`](src/main/java/org/ymxs/Chip.java) | the figures of the two chips |
| [`Tunes`](src/main/java/org/ymxs/Tunes.java) | what is read off a structure |
| [`Check`](src/main/java/org/ymxs/Check.java) | the rules a structure must satisfy |
| [`Json`](src/main/java/org/ymxs/Json.java) [`Text`](src/main/java/org/ymxs/Text.java) | JSON, defined in [json.md](doc/json.md) |
| [`Csv`](src/main/java/org/ymxs/Csv.java) | CSV, defined in [csv.md](doc/csv.md) |
| [`tool/`](src/main/java/org/ymxs/tool) [`bin/`](bin) | the tools, each a filter, defined in [tools.md](doc/tools.md) |
| [`ym/`](src/main/java/org/ymxs/ym) | an example: a YM register dump read in, [ym.md](doc/ym.md) |
| [`doc/tunes/`](doc/tunes) | six tunes in both forms, read back by the tests |
| [`go/`](go) | the same tools in Go, executables a release ships ([tools.md](doc/tools.md)) |

`YMXS.java` is records and sealed interfaces, with no methods beyond their
accessors. A structure is read by a function outside it, by pattern
matching: a shape added to `YMXS` stops those functions compiling until
they read it.

## Building

Java 23 and Maven. `mvn test` reads every tune under `doc/tunes` back in
both forms, checks SPEC.md's listing against the records, reads a dump
written by the test and an archive containing another, runs the tools in a
pipe, runs the Go tools against the Java ones byte for byte, and checks
the documents against the house style.

```bash
bin/ym-to-ymxs < tune.ym | bin/ymxs-check | bin/ymxs-json-to-csv > tune.csv
```

Every tool reads standard input and writes standard output, so the tools
compose in a pipe. [doc/tools.md](doc/tools.md) defines them.

The same tools are written in Go under [`go/`](go), and
`release/publish.sh` builds those for Windows, macOS and Linux, x64 and
arm64: one executable a tool, which runs with no Java installed.

```bash
cd go && go test ./... && go build ./cmd/...
release/publish.sh
```

## Where the figures come from

Every measurement in the javadoc and in the documents was read off 49
tunes converted from register dumps: 331,376 rows and 27,004 effect
entries. Each record's fields, and the measurement behind each choice, are
documented in the javadoc beside it.
