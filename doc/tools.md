# The tools

Every tool reads one input on standard input, writes one output on
standard output, and reports progress and faults on standard error. A
tool therefore composes in a pipe, and a redirected run contains the
output alone.

```bash
bin/ym-to-ymxs < tune.ym | bin/ymxs-check | bin/ymxs-json-to-csv > tune.csv
```

| tool | reads | writes | flags |
|---|---|---|---|
| `ym-to-ymxs` | a YM register dump, packed or not ([ym.md](ym.md)) | JSON | `-rROW`, `-r`, `-silent` |
| `ymxs-check` | JSON | the same text | `-silent` |
| `ymxs-json-to-csv` | JSON | CSV | `-silent` |
| `ymxs-csv-to-json` | CSV | JSON | `-silent` |
| `ymxs-merge` | several tunes, one file after another | one multi | `-silent` |

`-rROW` produces a tune that repeats to that row and `-r` one that plays
once; without either, a tune repeats to the row of the frame the dump
marks. `-silent` reduces the report to faults.

## The two trees

The tools are written twice: in Java under `src/`, and in Go under `go/`.
Java is the reference, and `ParityTest` runs the two against each other
on the dumps and the tunes of `doc/tunes`, so one input has one output in
both.

A Java tool is a shell script naming a class:

```sh
#!/bin/sh
# JSON into CSV. doc/csv.md.
exec "$(dirname -- "$0")/run" org.ymxs.tool.ToCsv "$@"
```

Every one runs through `bin/run`, which builds where a source or the pom
is newer than the last build, then runs the class named on its command
line. A pipe starts every tool at once, so two may require the same
build; `bin/run` locks by creating a directory, an atomic operation, so
the first process builds and the rest wait.

A Go tool is an executable and runs as it stands. `release/publish.sh`
builds the five for six platforms, one zip each, at the version the pom
names:

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

Progress and findings, on standard error:

```
ym-to-ymxs: YM5! "Circus Attractions #2" by "Mad Max", 4 rows at 50 Hz, 0 sources, timers []
ymxs-check: 1 tune, 4 rows, 0 sources
ymxs-check: every rule of SPEC.md 6 is satisfied
ymxs-json-to-csv: 1 tune, 4 rows, 649 characters in and 316 out
ymxs-merge: 2 files with 2 tunes
```

## Errors and warnings

Every tool that reads a tune reports both kinds, so a fault a writer left
in is named where the tune is used as well as where it is checked.

An **error** is a tune no player plays: text outside the form, or a
structure outside the two chips. Standard output stays empty and the exit
is 1, so a pipe stops rather than passing broken data on.

A **warning** is a tune that plays, but not as written: it breaks a rule
of [SPEC.md 6](SPEC.md). The tune passes through and the exit is 0, since
a player plays it and the writer corrects it. A warning is printed with
`-silent` as well: that flag quiets what a tool reports of its work, and
a warning is what the tune gets wrong.

```
ymxs-check: 1 tune, 8 rows, 2 sources
ymxs-check: warning: row 2: Timer B starts on R8, where Timer A runs: rule 2 leaves the order of two timers writing one register to the writer
ymxs-check: warning: row 6: Timer C stops an effect this timer has not started
ymxs-check: warning: rows 1 to 4: Timer A runs on R8 from row 0, and 4 of them set it
ymxs-check: 6 warnings
```

`ymxs-check` reads a tune and writes it back unchanged, for a caller that
requires the reading and no conversion, and it reports how many warnings
it found. `doc/tunes/warnings.json` is a tune written to break the rules,
and the lines above are three of its six.

## Several tunes

A multi contains several tunes, and each tool reads one input. JSON puts
no count in front of a stream of values, so several such files
concatenated read as several multis, and `ymxs-merge` combines their
tunes into one, in input order:

```bash
{ bin/ym-to-ymxs < one.ym; bin/ym-to-ymxs < two.ym; } | bin/ymxs-merge > both.json
```
