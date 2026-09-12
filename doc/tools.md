# The tools

Every tool reads one input on standard input, writes one output on
standard output, and reports progress and faults on standard error. Each
tool therefore composes in a pipe, and a redirected run contains the
output alone.

```bash
bin/ym-to-ymxs < tune.ym | bin/ymxs-check | bin/ymxs-json-to-csv > tune.csv
```

| tool | reads | writes | Java | Go |
|---|---|---|---|---|
| a YM register dump, packed or not, into JSON | a dump | JSON | `bin/ym-to-ymxs` | `ym-to-ymxs` |
| a tune checked, and passed on unchanged | JSON | the same text | `bin/ymxs-check` | `ymxs-check` |
| JSON into the tables | JSON | CSV | `bin/ymxs-json-to-csv` | `ymxs-json-to-csv` |
| the tables into JSON | CSV | JSON | `bin/ymxs-csv-to-json` | `ymxs-csv-to-json` |
| several tunes into one multi | several tunes, one file after another | one multi | `bin/ymxs-merge` | `ymxs-merge` |

## The two trees

The tools are written twice: in Java under `src/`, and in Go under `go/`.
Java is the reference, and `ParityTest` runs the two against each other on
the dumps and the tunes of `doc/tunes`, so one input has one output in
both.

A Java tool is a shell script naming a class:

```sh
#!/bin/sh
# JSON into CSV. doc/csv.md.
exec "$(dirname -- "$0")/run" org.ymxs.tool.ToCsv "$@"
```

Every one runs through `bin/run`. It builds where a source or the pom is
newer than the last build, then runs the class named on its command line.
A pipe starts every tool at once, so two may require the same build;
`bin/run` locks by creating a directory, an atomic operation, so the first
process builds and the rest wait.

A Go tool is an executable: it runs as it stands, where a Java tool runs
through `bin/run`. `release/publish.sh` builds them for six platforms, one
zip each, from a version the pom names:

```bash
release/publish.sh                    # win, osx and linux, x64 and arm64
TARGETS="linux-x64" release/publish.sh
go build ./cmd/...                    # from go/, for this machine alone
```

## What a tool exits with

| | |
|---|---|
| 0 | the tool completed |
| 1 | the input is wrong, and the fault is reported |
| 2 | the call is wrong, or reading or writing failed |

On a wrong input, standard output stays empty, so the pipe stops rather
than passing broken data on.

## What a tool reports

Progress and findings, on standard error. `-silent` reduces this to
faults.

```
ym-to-ymxs: YM5! "Turrican" by "Jochen Hippel", 1920 rows at 50 Hz, 3 sources, timers [D]
ymxs-check: 1 tune, 1920 rows, 3 sources
ymxs-check: every rule of SPEC.md 6 is satisfied
```

## Errors and warnings

Every tool separates the two, and every tool that reads a tune reports
both: a fault a writer left in is named where the tune is used rather than
only where it is checked.

An **error** is a tune no player plays: text outside this form, or a
structure outside the two chips. Standard output stays empty and the exit
is 1, so a pipe stops rather than passing broken data on.

A **warning** is a tune that plays, but not as written: it breaks a rule
of [SPEC.md 6](SPEC.md). The tune passes through and the exit is 0, since
a player plays it and only the writer can judge the result. A warning
stands whether or not `-silent` was passed: that flag quiets what a tool
reports of its work, and a warning is what the tune gets wrong.

```
ymxs-check: warning: row 1: Timer A runs on R8, and this row sets it
ymxs-json-to-csv: warning: rows 7 to 206: Timer A runs on R8 from row 7, and 200 of them set it
```

`ymxs-check` is the tool that reads a tune and writes it back unchanged,
for a caller that wants the reading and no conversion. It reports how many
warnings it found.

## Several tunes

A multi contains several tunes, and each tool reads one input. JSON puts
no count in front of a stream of values, so several such files
concatenated read as several multis. `ymxs-merge` combines their tunes
into one:

```bash
{ bin/ym-to-ymxs < one.ym; bin/ym-to-ymxs < two.ym; } | bin/ymxs-merge > both.json
```
