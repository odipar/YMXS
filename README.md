# YMXS

The tune data structure for the Atari ST's YM2149 and MC68901, and what a
player does with it. A tune is rows, one a frame; a row sets registers and
runs effects on the four timers; an effect is a source, a target and a
rate.

The structure is defined once, as records a compiler checks:
[`YMXS.java`](src/main/java/org/ymxs/YMXS.java). [SPEC.md](doc/SPEC.md)
lists those records and defines the rest: what each value reaches on the
two chips, what a frame does with a row, what a tick does with a source's
row, the rules that bind a writer, and what a reader reports.

A form is the structure written down. This repository defines two, JSON
([json.md](doc/json.md)) and CSV ([csv.md](doc/csv.md)), and both write the
same tune. A player's binary layout is a third, defined by that player.

## Read this first

**AI wrote most of YMXS.** Claude (Anthropic's Claude Code) wrote the
structure, the two trees of tools, the tests and most of what is written
here, under Robbert van Dalen's direction: every change was asked for,
read and merged by him. The attribution section below records who did
what, and [LICENSE](LICENSE) the terms. Whether to use software written
that way is the reader's decision, and this section is here so that the
decision is informed.

What it is built on is older than it. The YM5 and YM6 register-dump
formats are Arnaud Carré's, the depacker that opens a distributed `.ym`
is a port of his ST-Sound library's, and the 68000's interrupt cost that
bounds a timer's rate is from Motorola's manual.

## Implementing a player or a reader

Read in this order:

1. [SPEC.md](doc/SPEC.md) section 1, the structure, and sections 2 and 3,
   the figures of the two chips: what a register reaches, what a timer
   counts, and the rate a prescaler and a count come to.
2. Sections 4 and 5, the two procedures: what a frame does with a row, in
   what order, and what a tick does with a source's row.
3. Section 6, the rules a writer satisfies, which a player assumes.
4. Section 7, the record a reader produces, one line a frame, so that two
   readers are compared line for line.
5. [json.md](doc/json.md), the form a tune arrives in.

[`doc/tunes/example.json`](doc/tunes/example.json) is a tune of four rows
and one effect. json.md shows it as JSON, csv.md as tables, and SPEC.md 7
as the record a reader produces of it.

## What is here

| | |
|---|---|
| [`YMXS.java`](src/main/java/org/ymxs/YMXS.java) | the structure, listed in [SPEC.md](doc/SPEC.md) 1 |
| [`Chip`](src/main/java/org/ymxs/Chip.java) | the figures of the two chips |
| [`Tunes`](src/main/java/org/ymxs/Tunes.java) | what is read off a structure |
| [`Check`](src/main/java/org/ymxs/Check.java) | the rules a structure must satisfy, and the rules of SPEC.md 6 |
| [`Json`](src/main/java/org/ymxs/Json.java) [`Text`](src/main/java/org/ymxs/Text.java) | JSON, defined in [json.md](doc/json.md) |
| [`Csv`](src/main/java/org/ymxs/Csv.java) | CSV, defined in [csv.md](doc/csv.md) |
| [`tool/`](src/main/java/org/ymxs/tool) [`bin/`](bin) | the five tools, each a filter, defined in [tools.md](doc/tools.md) |
| [`ym/`](src/main/java/org/ymxs/ym) | an example: a YM register dump read in, [ym.md](doc/ym.md) |
| [`doc/tunes/`](doc/tunes) | seven files of tunes as JSON, two of them as CSV as well; the tests read every one back |
| [`go/`](go) | the same five tools in Go, the executables a release ships |

The records have no methods beyond their accessors. A structure is read by
a function outside it, by pattern matching over every shape, so a shape
added to `YMXS` stops those functions compiling until they read it.

## Building

Java 23 and Maven. `mvn test` reads every tune under `doc/tunes` back in
both forms, reads SPEC.md's listing against the records, reads a dump
built in the test and an archive containing another, runs the tools in a
pipe, runs the Go tools against the Java ones byte for byte, and reads the
documents against the house style.

```bash
bin/ym-to-ymxs < tune.ym | bin/ymxs-check | bin/ymxs-json-to-csv > tune.csv
```

Every tool reads standard input and writes standard output, so the tools
compose in a pipe. [tools.md](doc/tools.md) defines them.

The same tools are written in Go under [`go/`](go), and
`release/publish.sh` builds those for Windows, macOS and Linux, x64 and
arm64: one executable a tool, which runs with no Java installed.

```bash
cd go && go test ./... && go build ./cmd/...
release/publish.sh
```

The Go tree is a separate module, so another module reads the structure
and the two forms from it:

```bash
go get github.com/odipar/ymxs/go@v0.3.2
```

## Where the figures come from

Every measurement in the javadoc and in the documents was read off 49
tunes converted from register dumps: 331,376 rows and 27,004 effect
entries. Each record's fields, and the measurement behind each choice, are
documented in the javadoc beside it.

## License and attribution

The format may be implemented freely. `doc/SPEC.md` is the contract, and
an independent reader, player or writer owes only the acknowledgement.

The structure, the tools and the tests under `src/` and `go/` can be used
freely within your programs, for any platform, including commercial
releases, on the one condition that your documentation indicates
somewhere that you have used YMXS. [LICENSE](LICENSE) is the whole of it,
with the notices that cover the LHA depacker.

YMXS, its specification, its tools and its tests are © 2026 Robbert van
Dalen, written by Claude (Anthropic's Claude Code) under Robbert's
direction.

The YM5 and YM6 register-dump formats are by Arnaud Carré
(Leonard/Oxygene); `ym-to-ymxs` reads them, and its LHA depacker is a port
of his ST-Sound library's LZH code, itself based on LZH code by Haruhiko
Okumura (1991) and Kerwin F. Medina (1996).
[YMXR](https://github.com/odipar/YMXR) is a player of this structure, in
a separate repository.
