# The tools

Every tool reads its one input on standard input, writes its one output on
standard output, and says what it did and what is wrong on standard error.
So each stands in a pipe, and a run read into a file is the tool's output
alone.

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

Each is a shell script with a Java class in it:

```sh
#!/bin/sh
# JSON into CSV. doc/csv.md.
exec "$(dirname -- "$0")/run" org.ymxs.tool.ToCsv "$@"
```

`bin/run` is what they all go through. It builds where a source or the pom
is newer than the last build, then runs the class named on its command
line. Everything a tool does is Java's.

A pipe starts every tool in it at once, so two of them can need the same
build. `bin/run` locks by making a directory, which either happens
or does not: the first one through builds and the rest wait on it.

## What a tool exits with

| | |
|---|---|
| 0 | it did what it was asked |
| 1 | what it read is wrong, and it says how |
| 2 | the call is wrong, or reading or writing failed |

Where what it read is wrong, standard output stays empty, so a pipe stops
rather than passing something broken further.

## What a tool says

What it did, and what it found, on standard error. `-silent` cuts that
down to what is wrong.

```
ym-to-ymxs: YM5! "Turrican" by "Jochen Hippel", 1920 rows at 50 Hz, 3 sources, timers [D]
ymxs-check: 1 tune, 1920 rows, 3 sources
ymxs-check: every rule a writer keeps to is kept
```

## Errors and warnings

`ymxs-check` keeps the two apart, because they are not the same fault.

An **error** is a tune no player plays: text that is not this form, or a
structure the two chips cannot play. Standard output stays empty and the
exit is 1.

A **warning** is a tune that plays, but not as written: it breaks one of
the rules [SPEC.md 6](SPEC.md) asks of a writer. The
tune goes through and the exit is 0, since a player plays it and only its
writer can tell whether it is what was meant.

```
ymxs-check: warning: row 1: Timer A runs on R8, and this row sets it
```

## Several tunes

A multi has several tunes in it, and every tool reads one thing. JSON puts no
count in front of a stream of values, so several of these files handed
over as one read as several multis, and `ymxs-merge` puts their tunes into
one:

```bash
{ bin/ym-to-ymxs < one.ym; bin/ym-to-ymxs < two.ym; } | bin/ymxs-merge > both.json
```
