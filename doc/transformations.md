# Transformations: a plan for issue 104

Issue 104 asks for a mapping applied to a source on its way to the target:
a source of volumes A and sixteen tables V0 to V15, each mapping a volume
to another, so that an effect writes V5(A(row)) where it writes A(row)
today. This document is the plan: the structure YMXS gains, and what each
way of encoding it changes in YMXR. A version of the structure that
defines it replaces this document.

## The structure

A *transformation* is a name and a list of entries: entry k is the row the
target writes where the source's value is k, the key. A transformation of
one value an entry maps a volume to a volume; one of two or three values
an entry maps a key to a row of a target of two or three registers, such
as a note number to a period:

```java
sealed interface Transformation permits MapOne, MapTwo, MapThree { }

record MapOne(String name, List<Integer> entries) implements Transformation { }

record MapTwo(String name, List<Two> entries) implements Transformation { }

record MapThree(String name, List<Three> entries) implements Transformation { }

record StartMapped(Target target, Single source, Transformation map,
                   Timing timing, boolean deferred) implements Start { }
```

`StartMapped` connects a source of one value a row, its keys, through the
transformation to the target: a tick reads the key at the place and calls
the target with entry key of the transformation (SPEC.md 5.2 step 2). The
transformation has as many values an entry as the target reads, and the
source one value a row, the key. The three starts of today are a start
through the identity.

The operations are those of today. A `StartMapped` of the source the timer
runs, through another transformation, with both resets clear and the
running rate, changes the mapping alone: the source has its row count, so
the place is the row of its number (SPEC.md 3.4.3), and the timer
continues its period (SPEC.md 3.4.1). A fade of V15 to V0 over sixteen
frames is sixteen such starts.

The transformations of a tune are numbered from 1 in first-start order, as
its sources are (SPEC.md 1.9), and two are one where their names and their
entries are equal.

### What a check reports

Three errors of the structure join SPEC.md 1.11:

| condition | reported as |
|---|---|
| a transformation has no entries | `the transformation has no entries: a key reads one` |
| row N of the source of a `StartMapped` is a key K at or past the E entries of its transformation | `row N is key K, and the transformation has E entries` |
| entry K of a transformation is a value above the most of the register its target writes | `a transformation on setRn whose entry K is V, and the target is 0 to M` |

The width of an entry against the target's is the error of today's start
(`a source of W values a row on setRn, which reads U`), read on the
transformation.

### The forms

- JSON: a tune has `transformations`, an array of
  `{"name":N,"entries":[...]}` beside `sources`, and a timer a
  `transformation` column: 1 upward at a start through that
  transformation, -1 at any other row.
- CSV: a `transformation` block beside the `source` blocks, and a
  `transformation` cell in a timer's row lines.
- The record: a start's object gains `"transformation":T`, the number of
  its transformation, or 0 for a start through the identity (SPEC.md
  7.4).

### The version

The structure's version is 7. A file of version 6 or before has no
transformations, and a reader reads its starts through the identity.

## What it changes in YMXR

YMXR encodes an effect as four columns of the tune file, the target, the
source, the control and the count (YMXR's SPEC.md, 1.8), and the eight
bits of the control column are all assigned (YMXR's SPEC.md, 1.9.2). A
source is a DTX1 table of raw rows (YMXR's SPEC.md, 3.1.2), and the
general tick handler moves its row with one instruction from the table to
the chip's data port through a displacement patched into it: the handler
reads and writes memory alone, and the tick that writes a row costs 96
cycles (YMXR's performance.md, A tick). Three encodings follow, each
complete; the figures marked as estimated are from the 68000's instruction
timings and are counted in the implementation.

### A: the writer materializes each pair

The converter writes each pair of a source and a transformation that a
start uses as a separate source, its rows the entries at the keys.
`StartMapped` becomes a start of that source, and a change of mapping a
row that sets the source column alone: a start that connects a second
source at the running rate, the place kept since the row count is the same
(YMXR's SPEC.md, 1.8.4).

- Columns: none new. The source column numbers the pairs among the
  sources, 1 to 127.
- Ticks: 0 cycles more. A pair is a source like any other, a square
  and a source of one row included.
- Frame: a change of mapping is a start, at the cost of a start.
- File and memory: each pair's rows, raw. Source A of 8,192 rows under
  V0 to V15 is 16 copies, 131,072 bytes; the sixteen tables themselves
  are 256 bytes.
- Limit: 127 sources, pairs counted; eight drums at sixteen levels are 128.
- Reach: the rows past 32,767 bytes of the core's handlers need the core
  whose ticks read an absolute address, 12 cycles more a tick that writes
  a row (YMXR's BINARIES.md, 2.3 and 5.5).
- Format, player, model, readers: unchanged. YMXR writes this version 9.

### B: init materializes each pair

The tune file has the transformations as tables, and an index entry of a
further kind is a source and a transformation; init writes the pair's rows
into the workspace, and a tick reads them there.

- Columns: none new, as in A.
- Ticks: 0 cycles more.
- File: the tables, 256 bytes for V0 to V15; memory as in A, in the
  workspace, which grows by the pairs' rows.
- Init: about 36 cycles a byte written, estimated: A under V0 to V15 is
  4.7 million, six tenths of a second at 8 MHz.
- Reach: the workspace follows the tunes in an SNDH file (YMXR's
  BINARIES.md, 3.1), so its rows are the furthest from the handlers, and
  the absolute core is the likelier.
- Format: tune file version 10, an index entry kind and a table of
  transformations; the binder, the readers and the model read both.

### C: the tick looks the value up

The tune file has the transformations as tables, a fifth column an effect
is the number of its transformation, and a tick reads the key and writes
the entry.

- Columns: 34 where there are 30, column 30 + i the transformation of
  effect i, 0 for the identity. An unset column costs its frame little
  (DTX3 packs it), and a constant one is an immediate.
- Ticks: the general handler reads the key into a register and writes
  the entry through an indexed read: the register saved and restored,
  cleared, the key read and the entry moved, 54 cycles where the move is
  20 today, about 34 more on a tick that writes a row, estimated, 130
  against 96. A source of 8,192 rows a second is 278,528 cycles a second
  more, 5,571 a frame at 50 Hz.
- A square and a source of one row: the start writes the entries into
  the handler's immediates, so their ticks cost what they cost today.
- Handlers: a lookup shape beside each shape that steps a place, the
  general, the counted and the two of several columns, in each bank of
  the cores with the deferred start in; the code grows by that many
  instances.
- Reach: an indexed read with a byte displacement reaches 128 bytes, so
  a table of up to that many entries is copied into the handler at the
  start, and a longer one needs an address register, saved and restored
  too.
- File and memory: the tables alone.
- Format: tune file version 10, the column and the table; the player,
  the model and the readers read both.

### The comparison

| | A, writer | B, init | C, tick |
|---|---|---|---|
| columns | 30 | 30 | 34 |
| a tick that writes a row | 96 | 96 | about 130 |
| A of 8,192 rows under V0 to V15 | 131,072 bytes in the file | 256 in the file, 131,072 in memory | 256 |
| sources, pairs counted | 127 | 127 | 127 |
| tune file | version 9 | version 10 | version 10 |

## The plan

The recommendation: YMXS defines the structure of version 7; YMXR encodes
it as A first, which leaves the tune file, the player and the model as
they are, and measures the memory real tunes spend. C follows where that
memory is the constraint, for the sources whose copies cost more memory
than 34 cycles a tick cost time; B is between the two and waits until A's
file sizes rule A out.

- [ ] The plan, this document
- [ ] YMXS: the structure, the operations and the errors, sections 1, 3
      and 5 of SPEC.md, with the transformation's section
- [ ] YMXS: JSON, CSV and the record; the check; both trees; version 7
- [ ] YMXS: the examples and the conformance kit gain a tune through
      transformations, a fade among them
- [ ] YMXR: encoding A in the converter of both trees, the pairs
      numbered among the sources; the rig plays a fade against the model
- [ ] YMXR: the memory of A over the tunes that use transformations,
      and the decision on C
- [ ] The specifications define what landed; this document goes

## Questions

- An entry of a key to a row, against a table a column: a table a column
  maps a voice's volume beside its period, and leaves an octave out,
  since the period's two bytes carry between them. The plan maps a key
  to a row, and an octave is a transformation of note numbers to
  periods.
- The keys: a transformation of up to 256 entries, or of the values a
  register reaches, 16 for a volume.
- A change of mapping as a `StartMapped`, or a further operation that
  sets the transformation alone, as a `Retune` sets the rate alone.
