# The tools

Every tool reads one input on standard input, writes one output on
standard output, and reports progress and faults on standard error. Each
tool therefore composes in a pipe, and a redirected run contains the
output alone.

```bash
bin/ym-to-ymxs < tune.ym | bin/ymxs-check | bin/ymxs-json-to-csv > tune.csv
```

| tool | reads | writes |
|---|---|---|
| `ym-to-ymxs` | a YM register dump, packed or not | JSON |
| `ymxs-check` | JSON | the same text, unchanged |
| `ymxs-json-to-csv` | JSON | CSV |
| `ymxs-csv-to-json` | CSV | JSON |
| `ymxs-merge` | several tunes, one file after another | one multi |

Each tool is a shell script naming a Java class:

```sh
#!/bin/sh
# JSON into CSV. doc/csv.md.
exec "$(dirname -- "$0")/run" org.ymxs.tool.ToCsv "$@"
```

Every tool runs through `bin/run`. It builds where a source or the pom is
newer than the last build, then runs the class named on its command line.
All tool behaviour is Java.

A pipe starts every tool at once, so two may require the same build.
`bin/run` locks by creating a directory, an atomic operation: the first
process builds and the rest wait.

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

`ymxs-check` separates the two.

An **error** is a tune no player plays: text outside this form, or a
structure outside the two chips. Standard output stays empty and the exit
is 1.

A **warning** is a tune that plays, but not as written: it breaks a rule
of [SPEC.md 6](SPEC.md). The tune passes through and the exit is 0, since
a player plays it and only the writer can judge the result.

```
ymxs-check: warning: row 1: Timer A runs on R8, and this row sets it
```

## Several tunes

A multi contains several tunes, and each tool reads one input. JSON puts
no count in front of a stream of values, so several such files
concatenated read as several multis. `ymxs-merge` combines their tunes
into one:

```bash
{ bin/ym-to-ymxs < one.ym; bin/ym-to-ymxs < two.ym; } | bin/ymxs-merge > both.json
```
