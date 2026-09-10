# The tools

Every tool reads its one input on standard input, writes its one output on
standard output, and says what it did and what is wrong on standard error.
So each stands in a pipe, and a run read into a file holds what the tool is
for and nothing else.

```bash
bin/ym-to-ymxs < tune.ym | bin/ymxs-check | bin/ymxs-json-to-csv > tune.csv
```

| tool | reads | writes |
|---|---|---|
| `ym-to-ymxs` | a YM register dump, packed or not | the text form |
| `ymxs-check` | the text form | the same text, unchanged |
| `ymxs-json-to-csv` | the text form | the table form |
| `ymxs-csv-to-json` | the table form | the text form |
| `ymxs-merge` | several tunes, one file after another | one multi |

Each is a shell script wrapping a Java class. The script builds where a
source is newer than the last build and runs the tool; everything the tool
does is Java's.

## What a tool exits with

| | |
|---|---|
| 0 | it did what it was asked |
| 1 | what it read is wrong, and it says how |
| 2 | the call is wrong, or reading or writing failed |

Where what it read is wrong, nothing goes to standard output, so a pipe
stops rather than carrying something broken further.

## What a tool says

What it did, and what it found, on standard error. `-silent` leaves a
tool saying nothing but what is wrong.

```
ym-to-ymxs: YM5! "Turrican" by "Jochen Hippel", 1920 rows at 50 Hz, 3 sources, timers [D]
ymxs-check: 1 tune, 1920 rows, 3 sources
ymxs-check: every rule a writer keeps to is kept
```

## Errors and warnings

`ymxs-check` holds the two apart, because they are not the same fault.

An **error** is a tune no player plays: text that is not this form, or a
structure the two chips do not take. Nothing goes to standard output and
the exit is 1.

A **warning** is a tune that plays, and plays as something other than what
it states: one of the rules [SPEC.md 6](SPEC.md) asks of a writer. The
tune goes through and the exit is 0, since a player takes it and only the
writer can say whether it is what was meant.

```
ymxs-check: warning: row 1: Timer A runs on R8, and this row sets it
```

## Several tunes

A multi holds several tunes, and every tool reads one thing. JSON puts no
count in front of a stream of values, so several of these files handed
over as one read as several multis, and `ymxs-merge` puts their tunes into
one:

```bash
{ bin/ym-to-ymxs < one.ym; bin/ym-to-ymxs < two.ym; } | bin/ymxs-merge > both.json
```
