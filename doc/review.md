# A review of the structure at version 11

Versions 4 to 11 each added to the structure of version 3: targets of
several registers, a deferred start, a mapping, a cut, copy from frame, a
sync and its parity. Each version reads the files of every version before
it back to 3, so each addition came as a field, an operation or a target
beside the ones before it. This document reads the structure of version
11 as one design, measures it on twelve tunes converted from YM dumps, and
proposes changes for a version 12: for each, what goes, what it costs and
what it changes in YMXR. A version of the structure that defines one
replaces this document.

## Summary

| proposal | what goes | cost |
|---|---|---|
| three operations: a start, a change and a stop | `Retune`, `Remap`, `Recut`, `shape` and rule 5 | a field a start leaves is the value of the rows before it |
| sources and transformations listed by the tune and selected by number | first-start order, the equality of sources and of transformations, and two errors of the form | a number outside the list is an error of the structure |
| a source of rows of one to three values | 11 of the 42 declarations of SPEC.md 1.1 | the width of a source is a check of the structure |
| a mapping of a part a register | the part count, and the second encoding of the identity | a part for a register no target on the timer writes |
| the place at row 0 before the first frame | rule 3 and SPEC.md 8.4 | a write of 0 a timer before the first frame |
| rule 4 dropped | rule 4, which a check skips | none |
| `Operation` in place of `Effect` | two meanings of *effect* | a rename in both trees |
| a sync at a tick as a field `sync` of `SetTone` and `SetVoice` | `SyncTone` | targets 28 to 30 in the forms, for `SetVoice` with `sync` set |
| the parity of a sync kept a voice, and at a tick the parity of the tick before | a voice listed on each row of a held note, and the parity of the next interval read by the first tick after a frame | at the repeat row the parity of the pass before |

Three questions are open: which proposals version 12 defines, the layout
of a timer in JSON, and which versions a reader of version 12 reads.

## How it grew

| version | what it added | SPEC.md |
|---|---|---|
| 4 | targets of two and three registers, 14 to 24, and a type a width for sources, connections and targets | 3.1, 3.2 |
| 5 | `deferred` on a `Start` | 3.5 |
| 6 | the place of a deferred start at the row count of the source that ended | 3.5.3 |
| 7 | transformations, a mapping on a `Start`, and a fourth operation, `Remap` | 3.6 |
| 8 | a cut on a `Start`, and a fifth operation, `Recut` | 3.7 |
| 9 | copy from frame, a fourth `Connection` | 3.8 |
| 10 | `syncs` on a row, and targets 25 to 27 | 3.9 |
| 11 | `evenSyncs` on a row | 3.9.2 |

The block of SPEC.md 1.1 has 42 declarations at version 11, and the
sketch of version 12 below has 22.

## What was measured

*The dumps* are the eleven YM dumps under YMXR's `ym/test` and
`src/test/resources/packed.ym` here, each converted by `ym-to-ymxs` of
0.11.0: 12 tunes, 30,971 rows. Five of them are register dumps with no
timer operation. `ym-to-ymxs` writes starts, retunes and stops alone, each
start under the whole table of its source, through the identity, with
`deferred` clear, so the dumps measure those three operations.

*The examples* are the tunes of `doc/tunes` and of the conformance kit:
27 tunes, 3,415 rows, each written to show an addition.

A count of bytes is of compact JSON, without white space between tokens;
the layout of json.md 9 adds line feeds and indents to each. The figures
were counted once for this review; a version that defines a proposal
counts again, in a test, each figure its documents cite.

## The five operations

SPEC.md 4.3 defines a `Start` as four steps: stop the timer where
`timerReset` is set; connect the source to the target through the mapping
under the cut; write the prescaler and the count; and set the place, to
the first row of the cut where `placeReset` is set, and otherwise to its
number modulo the row count of the source, into the cut. Three of the
other four operations are those steps over fewer fields:

| operation | the steps of a `Start` | the fields it sets |
|---|---|---|
| `Retune` | 1, 3 and 4 | the prescaler and the count |
| `Remap` | 2, for the mapping | the mapping |
| `Recut` | 2, for the cut, then 4 | the cut |

On the source that runs, step 4 with `placeReset` clear moves a place
outside the cut to the first row of the cut and leaves every other place
as it was, since a place is a row of the source (SPEC.md 3.7.3): the
`Recut` of SPEC.md 4.3, and the place as it was for a `Remap`.

Each version that let a row change one field of a running effect added
an operation: `Remap` at version 7, `Recut` at version 8. A row performs
one operation on a timer (SPEC.md 1.6), so a row that changes two fields
of a running effect, the mapping and the rate, performs a `Start` and sets
all six: the target, the source, the prescaler, the count, the mapping and
the cut.

**Proposal.** Three operations: a *start*, a *change* and a stop.

- A start sets the source, and each of the target, the prescaler, the
  count, the mapping and the cut it has. A field it leaves has the value
  the timer keeps, except the cut: a start without a cut reads the whole
  table of its source. `deferred` is a field of a start alone.
- A change sets each of the prescaler, the count, the mapping and the cut
  it has on the effect that runs, and leaves the source and the target as
  they were: the `Retune`, the `Remap` and the `Recut` of version 11, and
  two or three of them in one row.
- Both have `timerReset` and `placeReset`, and a player performs the four
  steps of a `Start` for both, over the fields each sets.

The values a timer keeps are set by the rows alone: each start and each
change sets its fields at its frame, deferred or not, and a stop and the
wrap leave them as they are. At a start the timer keeps a target, a
prescaler and a count, set by the start or by an operation before it on
that timer; otherwise the structure has an error. A deferred start that
becomes pending has the values the timer keeps after its row, as a pending
`Start` of version 11 has the fields of its row (SPEC.md 3.5.5): a change
while it is pending sets the effect that runs, and the pending start keeps
its values. A change on an idle timer sets the values the timer keeps and
leaves the timer idle, as a remap on a stopped timer sets the kept mapping
in YMXR today (YMXR's SPEC.md, 1.10.2).

What goes:

- `Retune`, `Remap` and `Recut`, and `shape` in JSON and CSV (json.md
  6.2, csv.md 3.9);
- rule 5, its three lines of SPEC.md 6.5, SPEC.md 5.4 and the first item
  of SPEC.md 8.6: a change on an idle timer leaves it idle, so a timer
  runs with a source connected;
- three cases of SPEC.md 3.5.5, a `Retune`, a `Remap` and a `Recut` while
  a `Start` is pending, become the one sentence above.

What it adds:

- a row that changes the rate and the mapping of a running effect performs
  one change of three fields, where version 11 performs a `Start` of six;
- a change of the cut with `placeReset` set reads the new cut from its
  first row, where a `Recut` leaves a place inside the new cut as it was;
- a deferred start of the source that runs, with a new cut, moves a
  window at the end of the old one: a deferred `Recut`, one field more
  than a change.

What it costs:

- A field a start leaves is the value of the rows before it, so a reader
  of one row reads the rows before it to learn the target or the rate of a
  start. A check reads the rows in order already (SPEC.md 6.3).
- At the repeat row, a field a start leaves is the value of the pass
  before: in the first pass that of the rows before the repeat row, in a
  loop that of the last row. YMXR's schema marks such values unknown at
  the repeat row today (YMXR's `doc/ymxs.md`, "Kept values"). A writer
  that sets every field of the first start on each timer from the repeat
  row on plays the same in every pass, and a check can warn where it does
  not.

## What a start repeats

Of the 11,054 starts of the dumps, 9 are the first start on their timer
and 11,045 follow a start on it. Against the values the timer keeps from
the start and the retunes before it, the fields each start changes:

| fields that change | starts |
|---|---|
| the source | 7,474 |
| the source, the prescaler and the count | 1,904 |
| the source and the count | 1,255 |
| the source and the prescaler | 216 |
| the source and the cut | 86 |
| none of the six, a reset set | 55 |
| the prescaler and the count | 30 |
| the count | 25 |
| all six, the first start on a timer | 9 |

The 86 changes of the cut are the whole table of a source of another row
count, which a start without a cut reads under the proposal. Of the 5,974
retunes, 5,497 change the count alone and 477 the count and the
prescaler; a `Retune` of version 11 sets four fields, the prescaler, the
count and the two resets.

`DBA 2` runs a SID voice on `setR9` on Timer A through ten sources,
`square 5` and `square 7` to `square 15`, each the two rows n and 0
repeating to row 0. A start of one of them with both resets clear changes
the volume of the voice and leaves its phase: a start of ten fields for a
change of one.

In JSON a timer column has a cell a row, -1 where the row leaves it. The
timer columns of the dumps have 151,692 cells other than -1 of 386,144,
`shape` included. Under the proposal they have 23,512: a start writes its
source, each field that changes, a cut other than the whole table, and
each reset and deferral that is set; a change writes its fields.

With transformations (SPEC.md 3.6) the ten squares are one source through
ten transformations, and a change of volume is a change of the mapping;
`ym-to-ymxs` predates them. Under version 11 a change of volume and of
rate in one row remains a `Start`, 3,375 of the starts above; under the
proposal it is one change.

## Sources and transformations

A `Start` of version 11 has its source and the transformations of its
mapping as values (SPEC.md 1.1). The lists of a tune are produced from the
rows in first-start order: two sources are one where their names, values
and repeat rows are equal, and two transformations are one where their
names and entries are equal (SPEC.md 1.9, 3.6.2). Every form lists them
and selects one by number: `sources` and `transformations` in JSON,
blocks in CSV, numbers in the record (SPEC.md 7.3). In the forms:

- a source of a file that no row starts, and a transformation no row
  uses, is an error of the form, since the structure has no place for it
  (json.md 3.9, 4.4);
- an emitter orders sources by first start (json.md 3.4), so the numbers
  of a file written by hand in another order differ from the record's;
- equality compares the names of two sources, and a player skips the name
  (SPEC.md 3.2.1).

**Proposal.** `Tune` lists its sources and its transformations; a start
selects a source by number, 0 for copy from frame and 1 upward for the
tune's sources, and a mapping selects a transformation by number. The
structure is then the one every form encodes.

What goes: the procedure and the equality of SPEC.md 1.9 and 3.6.2;
json.md 3.4, 3.5, 3.8, 3.9, 4.4 and 4.5 and their CSV counterparts; the
two lines of json.md 8.1 on a source no row starts and a transformation
no row uses; `CopyFromFrame` and the four connections, since a start has
a source number and a target.

What it costs: a number outside the list is an error of the structure,
where JSON reports it as an error of the form today (json.md 6.6). A
source no row starts is legal, and a check can warn of it.

## A type a width

Version 4 fitted a source to its target by type (the notes of 0.4.0 in
[RELEASES.md](RELEASES.md)): `Single`, `Pair` and `Triple`, the rows `Two`
and `Three`, three connections and three target interfaces, 11 of the 42
declarations of SPEC.md 1.1. The type fits the width of a source and
leaves the mapping out: the part count of a mapping is a condition of
SPEC.md 1.11 since version 7, read against every `Start` on the timer of a
`Remap`. JSON reports a source of another width than its target reads as
an error of the form (json.md 8.1), since the structure has no value for
it.

**Proposal.** A source is a name and a table of rows of one, two or three
values, `Table<List<Integer>>`, every row of one length. A start of a
source of another length than its target reads is a condition of SPEC.md
1.11: the check JSON performs today, in the structure.

## The mapping

A mapping of version 11 is a list of parts in the register order of its
target, part i for value i of the row (SPEC.md 3.6.3). A transformation
of volume is part 0 on `setR8`, part 1 on `setNoiseA` and part 2 on
`setVoiceA`. A `Remap` is read against every `Start` on its timer, so a
fade through mappings of one part on a timer that runs `setVoiceA` is an
error, and `fade.json` runs its chord on a second timer for that reason
(json.md 10.3).

**Proposal.** A mapping is a part a register, `Map<Register, Integer>`:
the values a target writes to a register in the map pass through the
transformation of that number, 0 the identity, and a register absent from
the map keeps its part. A part for a register the target leaves alone is
skipped. A change of the mapping sets the parts it lists, so a fade of R8
leaves the parts of R0 and R1 as they were, on `setR8` and on `setVoiceA`
alike.

What goes: the mapping of no parts beside the mapping of identity parts,
two encodings of one mapping (SPEC.md 3.6.3, json.md 6.7); the two
conditions of SPEC.md 1.11 on a part count, of a `Start` and of a
`Remap`; the error of the form of an array of zeros (json.md 8.1). In
JSON `mapping` becomes an object of columns `r0` to `r13`, as `registers`
is, each cell a transformation number and -1 where a row leaves the part.

What it costs: a part can name a register no target on the timer writes,
and a check can warn of it.

YMXR's mapping column maps the last value of a target alone: R8 on
`setR8`, `setVoiceA` and `setNoiseA` (YMXR's SPEC.md, 1.10.1). A mapping
by register is that column where the targets on a timer have one last
register, and an error of the writer where a part maps another register,
as a part on an earlier value is today.

## Rules 3 and 4

Rule 4 requires a `Start` on a stopped timer to set `timerReset`. On a
stopped timer the outcome is the same with the reset set or clear
(SPEC.md 3.4.1), rule 4(a) accepts either value, and a check skips the
rule (4(b)). **Proposal:** drop rule 4.

Rule 3 requires the first `Start` on a timer to set `placeReset`, since
the place before it is left to a later version (SPEC.md 3.4.2, 8.4).
**Proposal:** every place is row 0 before the first frame (SPEC.md 4.1).
Rule 3, its line of SPEC.md 6.5 and SPEC.md 8.4 go, and a first start
with `placeReset` clear reads row 0, or the first row of its cut where row
0 is outside it. YMXR's player sets each place to 0 before the first
frame already (YMXR's SPEC.md, 4.1).

## Names

- `Effect` is the type of an operation: an operation is a value of the
  type `Effect` (SPEC.md 1.7), and `Row.effects` maps a timer to its
  operation. An *effect* in SPEC.md 1.7 and in the README is the source
  connected to a target on a timer. One vocabulary: `Operation`, and
  `Row.operations`.
- `shape` in JSON and CSV is the kind of the operation (json.md 6.2).
  Under the proposal a row starts where it sets `source`, stops where it
  sets `stop` and changes where it sets another column, so the key goes;
  kept as it is, `operation` is the plain word.

## JSON

The dumps are 2,364,779 bytes: register columns 1,300,037, timer objects
1,058,537 and sources 4,238. A timer object is eleven columns of R cells
(json.md 6.1).

| the timer objects of the dumps | bytes |
|---|---|
| version 11, eleven columns | 1,058,537 |
| the proposal, a column a field some row sets | 734,264 |
| the proposal, a list of operations, an object a row that acts | 500,497 |

With columns the dumps are 2,040,506 bytes, 13.7% less; with a list,
1,806,739, 23.6% less. A list reads as CSV reads a timer, a row line a
row that acts on it (csv.md 3.9); columns read as the rest of JSON, row i
at index i of every column (json.md 3.6). The proposal uses columns, one
layout for a tune; the list is open.

## Syncs

A sync of a voice is a write of its period: 0 to both registers, n steps
of the tone generator, then the period (SPEC.md 3.9.1). Version 10 added
it in two places, and version 11 its parity in a third:

| part | version 11 | SPEC.md |
|---|---|---|
| a sync at a frame | `syncs` on a row, a set of voices | 3.9.3 |
| a sync at a tick | `SyncTone`, targets 25 to 27 | 3.9.4 |
| the parity of both | `evenSyncs` on a row, a set of voices | 3.9.2 |

A sync at a frame is an event of its row, as a write of R13 restarts the
envelope, and rule 1(f) reports a row that syncs a voice while an effect
runs on its period. The other two were each added beside a part before
them: `SyncTone` beside `SetTone`, `evenSyncs` beside `syncs`.

`SyncTone` is `SetTone` with a sync before each write. Each target that
writes a period can have a counterpart with a sync, and SPEC.md 8.2 lists
one for a later version: a voice's period and its volume after a sync.

`evenSyncs` sets the parity of the sync of its row's frame and of the
ticks of a sync target until the next frame (SPEC.md 1.6, 3.9.4). By the
note of SPEC.md 3.9.4 a sync target restarts the square at one level at
every tick where the parity of each sync is that of k, the flips of the
square between that sync and the one before, at the period on the chip
between them. `evenSyncs` has two faults:

- The first tick after a frame ends an interval begun in the row before:
  at the old period where the row sets a new one, since copy from frame
  leaves R0 and R1 to the ticks (SPEC.md 3.8.3), and at the old count
  where the row writes one to a running timer with `timerReset` clear
  (SPEC.md 3.3.5). That tick reads the bit of the new row, so where k of
  the two intervals differ in parity, it restarts the square at the other
  level.
- The bit lasts one row, so a note of one period held over many rows lists
  the voice in `evenSyncs` on each of them.

**Proposal.** A sync at a tick is a field of a target, and the parity a
value of a voice kept until a row sets it, as a register is.

- `SetTone(Voice voice, boolean sync)` and `SetVoice(Voice voice, boolean
  sync)`: with `sync` set, a tick syncs the voice before it writes the
  period (SPEC.md 3.9.4). `SyncTone` goes, and the target of SPEC.md 8.2
  is `SetVoice` with `sync` set.
- `evenSyncs` maps a voice to `true` for even and `false` for odd; a voice
  absent keeps its parity, and each voice is odd before the first row that
  sets it. In JSON its columns `a` to `c` are -1, 0 or 1, -1 where the row
  leaves the parity.
- A sync at a frame reads the parity of the voice after its row. A sync at
  a tick reads the *tick parity* of its voice, and the tick then sets the
  tick parity to the parity of the voice; a start on a target with `sync`
  set sets it to the parity after the start's row. The first tick after a
  frame then reads the parity of the interval it ends.

In the forms a target number selects one routine of a player, as today:
25 to 27 are `SetTone` with `sync` set, and 28 to 30, of the numbers
SPEC.md 3.1.2 leaves to later targets, `SetVoice` with `sync` set.

What goes: `SyncTone`, the voice of a held note listed on each row, and
the parity of the next interval read by the first tick after a frame.

What it costs:

- At the repeat row the parity is that of the pass before, as a field a
  start leaves (The five operations, above): a writer sets the parity of
  a voice at the repeat row where the loop differs from the first pass.
- A sync at a tick of a file of version 11 reads differently at the first
  tick after a frame whose bit differs from the row before (The versions a
  reader reads, below).

Two limits remain under this shape and under version 11:

- A source of two rows or more on a target with `sync` set has one parity
  a voice for intervals at each of its periods.
- A prescaler written to a running timer, or a rate with `timerReset` set,
  splits or cuts the interval in progress at the frame (SPEC.md 3.3.5,
  3.4.1), so the parity of the first sync after it depends on where the
  frame falls between two ticks.

Two tunes of the examples have syncs, `sync.json` and `even-syncs.json` of
the conformance kit, and the dumps have none. As a parity set where it
changes, the `evenSyncs` of the examples are 6 cells, against 4 today:
each even sync there lasts one or two rows, and neither tune has a held
note. This section follows from the text of SPEC.md 3.3.5, 3.8.3 and 3.9,
and is measured neither on the chip nor under Hatari.

## The versions a reader reads

A reader reads versions 3 to 11 (json.md 2.4). Each version added a
sentence to json.md 2.4, conditions to steps 3, 6, 7 and 8 of json.md
7.1 and lines to json.md 8.1, and the same to CSV.

A reader of version 12 reads a file of version 3 to 11 into the proposed
structure exactly: a `Start` is a start that sets every field, its mapping
a part for each register of its target, with its flags; a `Retune` a
change of the prescaler and the count with its resets; a `Remap` a change
of the mapping, part i for register i of each target a `Start` on its
timer connects; a `Recut` a change of the cut. A part for a register the
target leaves alone is skipped, so a `Remap` on a timer of two targets
reads as it did. Each timer of the examples with a `Remap` runs one
target. `SyncTone` reads as `SetTone` with `sync` set, and `evenSyncs` as
a parity set on every row, even for a voice listed and odd for the
others.

Two readings differ. A `Retune` on an idle timer breaks rule 5: version 11
starts the timer and leaves its ticks to a later version (SPEC.md 5.4),
and a change leaves it idle. The first sync at a tick after a frame whose
bit differs from the row before reads the bit of the row before (Syncs,
above): in `even-syncs.json` the first tick of Timer A after the frames of
rows 2 and 4.

The other way is a reader of version 12 alone, and a tool that converts a
file of version 3 to 11; json.md 2.4 and its counterparts then go.

## What stays

- `Table<T>` with an optional repeat row, one kind for a tune and a
  source (SPEC.md 1.4).
- The repeat row with the source and the cut with the effect: a loop is a
  property of a sample, a window of how an effect reads it (SPEC.md
  3.7.4).
- The rate as a prescaler and a count, the values of the MC68901: two
  pairs of one rate differ in how a write to a running timer applies
  (SPEC.md 3.3.5).
- The registers of a row as a map, an absent register keeping its value
  (SPEC.md 1.6).
- The target numbers 0 to 27 of the forms as a closed list, one routine a
  number in a player, with the numbers above them for later ones (SPEC.md
  3.1.2); the width interfaces go.
- `syncs` on a row, a set of voices, and rule 1(f) (SPEC.md 3.9.3).
- Copy from frame as a source, source 0 in the structure as in the forms.

## A sketch of version 12

The declarations that change; SPEC.md 1.1 lists the structure.

```java
record Tune(String title, String composer, String writer, int rate,
            List<Source> sources, List<Transformation> transformations,
            Table<Row> table) { }

sealed interface Operation permits Start, Change, Stop { }

record Start(int source, Optional<Target> target, boolean deferred,
             Change change) implements Operation { }

record Change(Optional<Prescaler> prescaler, OptionalInt count,
              Map<Register, Integer> mapping, Optional<Cut> cut,
              boolean timerReset, boolean placeReset)
        implements Operation { }

record Source(String name, Table<List<Integer>> table) { }

sealed interface Target permits SetRegister, SetTone, SetNoise,
        SetEnvelope, SetVoice, SetBuzzer { }

record SetTone(Voice voice, boolean sync) implements Target { }

record SetVoice(Voice voice, boolean sync) implements Target { }
```

`Row` maps each timer to an `Operation`, in a field `operations` in place
of `effects`, and `evenSyncs` is a `Map<Voice, Boolean>`. `Multi`,
`Table`, `Stop`, `Cut`, `Transformation`, the other four targets, each
implementing `Target`, and the four enums are as at version 11: 22
declarations. The 22 that go are `Effect`, `Retune`, `Remap`, `Recut`,
`Timing`, `Connection` and its four records, the three width interfaces
of `Target`, `Single`, `Pair`, `Triple`, `Two`, `Three`, `Mapping`,
`Part`, `Identity` and `SyncTone`.

The conventions of SPEC.md 1.1 gain `Optional<T>`, a value present or
absent. `source` is 0 for copy from frame and n for source n of the tune;
a value of `mapping` is 0 for the identity and n for transformation n.
The `change` of a start has its prescaler, count, mapping, cut and resets;
an absent cut there is the whole table of the source. `sync` set syncs the
voice before each write of a tick, and `evenSyncs` maps a voice to `true`
for even (Syncs, above).

- A player performs a start at its frame where `deferred` is clear or the
  timer is idle; otherwise the start is pending with the values the timer
  keeps after its row until the tick that reads the last row of the cut
  (SPEC.md 3.5). It performs the four steps of SPEC.md 4.3 with the
  target, the source, the prescaler, the count and the mapping the timer
  keeps, and the cut of the start.
- On a running timer a player performs steps 1, 3 and 4 of SPEC.md 4.3
  for a change, step 3 where the change sets the prescaler or the count,
  with the fields it sets on the effect that runs. On an idle timer a
  change sets the values the timer keeps.
- A stop is as SPEC.md 4.3 defines it.

## What it changes in YMXR

- The converter: YMXR's schema encodes a start as the columns that differ
  from the values it keeps (YMXR's `doc/ymxs.md`, "Kept values"), so a
  field of a start or a change is a column where it differs, as a
  `Retune`, a `Remap` and a `Recut` are columns today.
- The player: the kept target, source, rate, mapping and cut of each
  effect, and a place of 0 before the first frame, are YMXR's already
  (YMXR's SPEC.md, 4.1). A change of the rate and the mapping in one row
  is the rate columns and the mapping column with the source column unset,
  which a player reads today (YMXR's SPEC.md, 1.10.2).
- The mapping by register: the mapping column where the targets on a
  timer have one last register, as above.
- The sources: the converter numbers the source index in first-start
  order, as today, or in the order of the tune's list.
- The syncs: bit 5 and bit 4 of coarse column 1, 3 or 5 are the sync of a
  frame and the parity, read on every row (YMXR's SPEC.md, 1.12), so the
  converter writes bit 4 from the parity each voice keeps. The player
  keeps the tick parity, one bit a voice, and targets 28 to 30 are three
  routines more.

## The plan

- [x] The review, this document
- [ ] The open questions settled
- [ ] YMXS: version 12, the three operations, sources and transformations
      by number, a source of rows of one to three values, the mapping by
      register, rules 3 and 4, the names and the syncs; SPEC.md, JSON,
      CSV, the record, the check, both trees
- [ ] YMXS: `ym-to-ymxs` writes the volume of a SID voice through
      transformations, one source a waveform
- [ ] YMXR: the converter reads version 12
- [ ] The specifications define what landed; this document goes

## Open questions

1. Which proposals version 12 defines: all of them, or the operations,
   the names and rules 3 and 4 first, with sources by number and the
   mapping by register in a version after it.
2. A timer in JSON: a column a field, or a list of operations.
3. The versions a reader of version 12 reads: 3 to 12, or 12 alone with a
   tool that converts the files of 3 to 11.
