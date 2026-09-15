# YMXS

## Read this first

**AI wrote most of YMXS.** Claude (Anthropic's Claude Code) wrote the
structure, the two trees of tools, the tests and most of what is written
here, under Robbert van Dalen's direction: he requested, read and merged
every change. [LICENSE](LICENSE) is the terms, and its attribution records
who did what. Whether to use software written that way is the reader's
decision, and this section is here so that the decision is informed.

What it is built on is older than it. The YM5 and YM6 register-dump
formats are Arnaud Carré's, and the depacker that opens a distributed
`.ym` is a port of the LZH code of his ST-Sound library.

## What YMXS is

The tune data structure for the Atari ST's YM2149 and MC68901, and what a
player does with it: a tune is a table of rows, one row a frame; a row
sets registers and performs operations on the effects of the four
timers; an effect is a source connected to a target on one timer, at a
rate. [YMXR](https://github.com/odipar/YMXR) is a player of it, in a
separate repository.

[SPEC.md](doc/SPEC.md) 1 lists the structure as Java records, which
[`YMXS.java`](src/main/java/org/ymxs/YMXS.java) is as source, a test
requiring the two equal; SPEC.md defines the rest: what each value
reaches on the two chips (2, 3), what a frame does (4) and a tick (5),
the rules a writer satisfies (6), what a recorder reports (7), and what
a later version defines (8).

A form is an encoding of the structure: JSON ([json.md](doc/json.md)), which
encodes every tune, CSV ([csv.md](doc/csv.md)), which encodes every tune
whose texts are free of line feeds and carriage returns (csv.md 5.4), and a
player's binary layout, defined by that player.

## Implementing a player or a reader

Read in this order: SPEC.md 1 to 3, the structure and the figures of
the two chips; 4 and 5, the two procedures; 6, the rules a writer
satisfies and a player assumes; 7, the record by which two recorders
are compared line for line; then json.md and csv.md, the forms, and
[tools.md](doc/tools.md), the five tools: one reads a YM dump into JSON,
one checks a tune, two convert between the forms, one merges multis.
[`doc/tunes/example.json`](doc/tunes/example.json) is one tune of four
rows and one effect, shown as JSON in json.md, as CSV in csv.md, and as
a recorder's record in SPEC.md 7.

## What is here

| | |
|---|---|
| [`YMXS.java`](src/main/java/org/ymxs/YMXS.java) | the structure, listed in [SPEC.md](doc/SPEC.md) 1 |
| [`Chip`](src/main/java/org/ymxs/Chip.java) | the figures of the two chips |
| [`Tunes`](src/main/java/org/ymxs/Tunes.java) | the functions over a structure: its sources, its timers, its effects in order |
| [`Check`](src/main/java/org/ymxs/Check.java) | the errors of a structure, and the warnings of SPEC.md 6 |
| [`Json`](src/main/java/org/ymxs/Json.java) [`Text`](src/main/java/org/ymxs/Text.java) | JSON, defined in [json.md](doc/json.md) |
| [`Csv`](src/main/java/org/ymxs/Csv.java) | CSV, defined in [csv.md](doc/csv.md) |
| [`tool/`](src/main/java/org/ymxs/tool) [`bin/`](bin) | the five tools, defined in [tools.md](doc/tools.md) |
| [`ym/`](src/main/java/org/ymxs/ym) | a YM register dump read into the structure, defined in [ym.md](doc/ym.md) |
| [`doc/tunes/`](doc/tunes) | seven files of tunes as JSON, two as CSV as well; the tests read every one back |
| [`go/`](go) | the same five tools in Go, the executables of a release |

The records have their accessors alone; a function outside the structure
reads it by pattern matching over every shape, so a shape added to `YMXS`
stops those functions compiling until they read it.

## Building

Java 23 and Maven. `mvn test` reads every tune under `doc/tunes` back in
both forms, the listing of SPEC.md 1 against the records, a dump built in
the test and an archive with another inside, and the documents against
the house style, and, where the tools are built (tools.md 11.2) and Go
is on the path, runs the tools in a pipe and the Go tools against the
Java ones byte for byte.

```bash
bin/ym-to-ymxs < tune.ym | bin/ymxs-check | bin/ymxs-json-to-csv > tune.csv
```

The same five tools are written in Go under [`go/`](go);
`release/publish.sh` builds them for Windows, macOS and Linux on x64 and
arm64, one executable a tool, each running by itself. The Go tree is a
separate module, from which another module reads the structure and the two
forms.

```bash
cd go && go test ./... && go build ./cmd/...
release/publish.sh
go get github.com/odipar/ymxs/go@v0.3.3
```

## Where the figures come from

Every limit of a value follows from the two chips and the 68000 (SPEC.md
2 and 3). The choices of the structure rest on measurements of register
dumps, read as [ym.md](doc/ym.md) defines; the dumps are outside this
repository.

## License

The format may be implemented freely; `doc/SPEC.md` is the
specification, and an independent reader, player or writer of it owes
only the acknowledgement of condition 2 of [LICENSE](LICENSE):
documentation that indicates YMXS was used. The structure, the tools and
the tests under `src/` and `go/` may be used freely within your programs,
for any platform, commercial releases included, on that same condition.
LICENSE is the whole of the terms, with the notices that cover the LHA
depacker.

## Attribution

YMXS, its specification, its tools and its tests are © 2026 Robbert van
Dalen. Claude (Anthropic's Claude Code) wrote the structure, the two
trees of tools, the tests and most of the documents, under Robbert van
Dalen's direction: he requested, read and merged every change.

The YM5 and YM6 register-dump formats are by Arnaud Carré
(Leonard/Oxygene); `ym-to-ymxs` reads them, and its LHA depacker is a
port of the LZH code of his ST-Sound library, itself based on LZH code by
Haruhiko Okumura (1991) and Kerwin F. Medina (1996).
