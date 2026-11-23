# Cuts: a plan for YMX issue 180

YMX issue 180 asks for an effect that reads part of its source: a first
row above row 0, a last row below the source's last, and both set from a
row of the tune while the effect runs, which moves a window over a wave
table. This document is the plan: the structure YMXS gains at version 8,
what a check reports, the forms, and what each way of encoding it changes
in YMXR. A version of the structure that defines it replaces this
document.

The issue's start offset is the first row of a *cut*, and its cut offset
the last row.

## The structure

A cut is two rows of the source of an effect, the first and the last of
the rows that effect reads: each 0 to R - 1 of a source of R rows, the
first at or below the last. Each `Start` has a cut, and a fifth
operation, `Recut`, sets the cut of the effect a timer runs, as a `Remap`
sets its mapping.

```java
record Start(Connection connection, Timing timing, boolean deferred,
             Mapping mapping, Cut cut) implements Effect { }

record Recut(Cut cut) implements Effect { }

record Cut(int first, int last) { }
```

`Effect` permits `Recut` beside `Start`, `Retune`, `Stop` and `Remap`
(SPEC.md 1.1).

A cut is a value of an effect, as a mapping is: two effects run one
source (SPEC.md 3.2.3) under two cuts, and the equality of two sources
(SPEC.md 1.9) compares the rows and the repeat row as it does today.

### What a tick reads

A cut replaces the row count of a source in these clauses of SPEC.md:

| clause | today | under the cut `first` to `last` |
|---|---|---|
| the row after a row (1.5) | the row after row R - 1 is the repeat row RR | the row after `last` is RR where `first` to `last` includes RR, and `first` where it does not |
| the end of a source that plays once (1.5) | row R - 1 is its last row | `last` is its last row |
| the place at `placeReset` set (3.4.3) | row 0 | `first` |
| the place at `placeReset` clear (3.4.3) | its number modulo R | its number modulo R, and `first` where that row is outside `first` to `last` |
| the place at the end of a source, which a deferred start reads (3.5.3) | R | `last` + 1 |

The cut of rows 0 to R - 1 is the reading of today: it includes every
repeat row, so the row after its last row is RR; `placeReset` set puts
the place at row 0; and `placeReset` clear puts it at its number modulo
R, a row of the cut. A reader of a file of version 7 or before reads
every start with that cut, so a tune of version 7 plays as it did.

A cut whose first row is above the repeat row of its source repeats to
its first row: the wave table of the issue is a source of R rows
repeating to row 0, and an effect under the cut 64 to 95 reads those 32
rows over and over. A cut over a source that plays once ends at the cut's
last row: a sample cut short. A cut that includes the repeat row of its
source repeats to that row, so a source of an attack and a loop keeps
both under a cut of its tail.

### A `Recut`

A `Recut` sets the cut of the effect the timer runs. The source, the
target, the rate and the mapping are as they were; where the place is
outside the new cut, the place is the cut's first row, so that each tick
reads a row of the cut. On an idle timer a `Recut` leaves the timer as it
is and breaks rule 5, as a `Remap` does.

A `Start` a frame is the alternative to the fifth operation: on the
source, the target and the rate the timer runs, with both resets clear, a
`Start` of another cut changes the cut alone, since the place is its
number modulo R placed into the cut. Such a row sets the source, the
target, the rate and the mapping again, where a `Recut` sets two rows.

### The reckoning

A check reckons from the rate how long a source that plays once runs
(SPEC.md 6.4), over the R rows of the source. Under a cut it reckons over
the rows of the cut, `last` - `first` + 1, which is R for the cut of rows
0 to R - 1. The reckoning reads the cut of the `Start`; a `Recut` leaves
it as it is, as a `Retune` does.

### What a check reports

Three errors of the structure (SPEC.md 1.11) read the cut of a `Start`
against the source that `Start` connects. F is the first row of the cut,
L its last row, R the rows of the source:

| condition | reported as |
|---|---|
| the first row of the cut is outside 0 to R - 1 | `the source has R rows and the cut begins at row F` |
| the last row of the cut is outside 0 to R - 1 | `the source has R rows and the cut ends at row L` |
| the last row of the cut is below its first row | `a cut of rows F to L: a cut ends at or after the row it begins at` |

The cut of a `Recut` is read against each `Start` on its timer, at any row
of the tune, as the mapping of a `Remap` is (SPEC.md 3.6.5): the first two
lines, each prefixed `start of row S: `, S the row of the first `Start`
that connects the source; and the third line once, since the two rows of
the cut are read against each other. Report order (SPEC.md 1.12) gains
an item for a `Recut` after the `Remap`'s, and the lines of a `Start`'s
cut follow those of its mapping.

Two warnings read a `Recut`, each the line of a `Remap` with `recuts` in
place of `remaps`:

| rule | condition | reported as |
|---|---|---|
| 5 | a `Recut` at row N on an idle timer | `row N: Timer X recuts an effect that is idle: a cut is set on a running effect, and this row leaves the timer as it is` |
| 1(d) | the tune repeats to row T, and row T performs a `Recut` on a timer running when the last row has been read | the 1(d) line of a `Remap` at the repeat row |

The check of SPEC.md 6.3 reads a `Recut` at step 2(d), where it reads a
`Remap`: the effect in the model, its run and the pending `Start` are as
they were.

### The forms

| form | what it gains |
|---|---|
| JSON (json.md 6.1) | `first` and `last` columns in a timer object, 0 upward where the shape selects them and -1 elsewhere |
| JSON (json.md 6.2) | `shape` 4, a recut, which selects `first` and `last` |
| CSV (csv.md 3.9) | `first` and `last` cells in a timer's row lines, and `shape` 4 |
| the record (SPEC.md 7.4) | `"first":F,"last":L` in a start's object, after `mapping` |
| the record (SPEC.md 7.4) | `{"recut":{"first":F,"last":L}}`, the operation of a recut |

A start and a recut select the two columns; a retune and a stop leave
both at -1.

### The version

The structure's version is 8: a writer writes 8, and a reader reads 8, 7,
6, 5, 4 and 3. In a file of version 7 or before a reader reads every
start with the cut of rows 0 to R - 1, and `shape` 4 there is an error of
the form, as `shape` 3 is in a file of version 6 (json.md 2.4).

## What it changes in YMXR

YMXR reads the row count R and the repeat row RR of a source out of the
header of the DTX1 table of that source (YMXR's SPEC.md, 3.1.2), one table
a source. A tick tests the row it read for the last of the source: of a
counted source, row R - 1; of any other, a row whose written byte has bit
7 set, the marker (YMXR's SPEC.md, 5.1 step 3). The encoding follows two
conditions:

- the end of a cut is a value of an effect, and the marker is a bit of
  the table, one table a source, so the marker of a table two effects
  read cannot end the cut of one of them;
- a column of the tune file is one byte, bit 7 the set bit and bits 6 to
  0 the value (YMXR's SPEC.md, 1.1.1), so a column numbers 128 rows,
  where 5 of the 34 sources of `doc/tunes` and the conformance kit have
  more rows than that and the longest has 293.

Three encodings follow, each complete. The cycles and the sizes are
counted in YMXR's implementation.

### A: the writer copies the table

The converter writes each cut a start or a recut uses as a source of the
file: the rows `first` to `last` of the table, the marker on the last of
them, and the repeat row of the copy the row the cut repeats to. A
`Recut` is a row that sets the source column alone, which connects the
second cut at the rate the timer counts (YMXR's SPEC.md, 1.8.4).

- Columns: 34, as today.
- Ticks: as today. A cut is a source like any other.
- The source index, the player, the model and the readers: as they are.
- File: the rows of each cut, one copy a cut. A wave table of 256 rows
  under 64 cuts is 16,384 bytes where the table is 256.
- Limit: 127 sources, cuts counted, so a tune that moves a window a
  frame runs out of sources.
- The place: a row of the copy is a row of the source less the cut's
  first row, so a row that sets the source column alone leaves the place
  at its row of the copy, where a `Recut` leaves it at its row of the
  source. A moved window needs the converter to number the place in the
  copy, which a start places modulo the copy's row count.
- Tune file: version 10, as today.

### B: an index of cuts

The tune file gains an index of cuts, each a first row and a last row,
and a column an effect selects one, as the mapping column selects a
transformation (YMXR's SPEC.md, 1.10). A tick reads the two rows of the
cut from the workspace of the effect, tests the place against the last
row, and leaves the marker of the row unread.

- Columns: 38, column 34 + i the cut of effect i.
- Ticks: a compare and a branch where the marker test is today, on the
  handler of a source under a cut.
- File: 8 bytes a cut, and the index.
- Limit: 127 cuts a tune, so a window that moves through more positions
  needs cuts the index cannot number.
- The place: a row of the index is a row of the source, as a `Recut`
  reads it.
- Tune file: version 11, the index and the column; the binder, the
  readers and the model read both.

### C: two columns an effect

Two cut columns an effect, the first row and the last, each bits 6 to 0
of its byte, so a cut covers rows 0 to 127 of its source.

- Columns: 42, two an effect.
- Ticks: as B.
- File: no index to write.
- Limit: a cut of a source of more than 128 rows is an error of the
  writer, and five of the sources above have more rows than that.
- The place: as B.
- Tune file: version 11, two columns an effect.

### The comparison

| | A, copies | B, an index | C, columns |
|---|---|---|---|
| columns | 34 | 38 | 42 |
| a tick of a source under a cut | as today | a compare | a compare |
| a wave table of 256 rows under 64 cuts | 16,384 bytes | 768 bytes | 256 bytes |
| cuts a tune | 127 sources, cuts counted | 127 | one a start or a recut |
| rows a cut reaches | every row | every row | rows 0 to 127 |
| the place of a `Recut` | the copy's numbering | the source's | the source's |
| tune file | version 10 | version 11 | version 11 |

## The plan

The recommendation: YMXS defines the structure of version 8, the cut on a
`Start` and the `Recut`; YMXR encodes it as B, which reaches every row of
every source, numbers the place as a `Recut` reads it, and leaves the
sources of a tune for the sources. A is the first step where the cuts of
real tunes are few and fixed, since it leaves the player as it is. C is B
without the index, for tunes whose sources are 128 rows at most.

- [x] The plan, this document
- [x] YMXS: the structure, the operation and the errors, sections 1, 3,
      4, 5 and 6 of SPEC.md, with the cut's section, 3.7
- [x] YMXS: JSON, CSV and the record; the check; both trees; version 8
- [x] YMXS: the examples and the conformance kit gain a tune under a cut
      and a tune that moves one, `doc/tunes/cut.json` and the kit's
      `cuts.json`
- [x] YMXR: encoding B in both trees, the index and the column; the rig
      plays a moved window against the model (YMXR pull request 363)
- [x] YMXR: the cycles of a tick under a cut counted into
      performance.md (YMXR pull request 363)
- [ ] The specifications define what landed; this document goes

## Settled

- The name: a `Cut` of a first row and a last row, and a `Recut` beside the
  `Remap`.
- The fifth operation, which sets the cut alone; the cut stays off the
  `Retune`.
- The row after the last row of a cut that excludes the repeat row of its
  source: the cut's first row (SPEC.md 3.7.2).
- The cut of a `Recut` is read against the source of every `Start` on its
  timer, as the mapping of a `Remap` is, so a window moved over a wave table
  fits every source a later row starts on that timer (SPEC.md 1.11).
