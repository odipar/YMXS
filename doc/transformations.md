# Transformations: a plan for issue 104

Issue 104 asks for a mapping applied to a source on its way to the target:
a source of volumes A and sixteen tables V0 to V15, each mapping a volume
to another, so that an effect writes V5(A(row)) where it writes A(row)
today. This document is the plan: the structure YMXS gains, and how YMXR
encodes and plays it, the value looked up at the tick. A version of the
structure that defines it replaces this document.

## The structure

A *transformation* is a name and 256 entries, each 0 to 255: entry k is
the value written where the value read is k, the key. One kind serves
every target. The name is text for display and for reports. The identity
writes each value as it is, and a transformation is distinct from the
identity, an entry k of value k at every key included; equal entries under
two names are two transformations.

A *mapping* is, for each value of a row of the target, a transformation or
the identity. A `Start` connects its source through a mapping, and a tick
writes value i of the row it reads through part i of the mapping (SPEC.md
5.2 step 2). A fade of a voice maps its volume alone: on `setVoiceA` the
mapping is the identity, the identity and V5.

```java
sealed interface Part permits Identity, Transformation { }

record Identity() implements Part { }

record Transformation(String name, List<Integer> entries) implements Part { }

record Mapping(List<Part> parts) { }

record StartOne(OneTarget target, Single source, Timing timing,
                boolean deferred, Mapping mapping) implements Start { }

record StartPair(TwoTarget target, Pair source, Timing timing,
                 boolean deferred, Mapping mapping) implements Start { }

record StartTriple(ThreeTarget target, Triple source, Timing timing,
                   boolean deferred, Mapping mapping) implements Start { }

record Remap(Mapping mapping) implements Effect { }
```

A mapping has as many parts as its target reads values (SPEC.md 3.1.1).

### A remap

`Effect` permits `Remap` beside `Start`, `Retune` and `Stop`. A `Remap`
sets the mapping of the effect the timer runs, as a `Retune` sets its rate
(SPEC.md 4.3): the timer, the source, the rate, the place and a pending
`Start` are as they were, and the next tick writes through the new
mapping. A pending `Start` keeps the mapping of its row. On an idle timer
a `Remap` leaves the timer as it is, and a check reports it, as it reports
a `Retune` on one (rule 5). The mapping is part of a timer's condition:
the wrap and the end keep it (SPEC.md 4.5, 4.6). A `Remap` neither ends a
run nor begins one (SPEC.md 6.2, 6.3), and at the repeat row it leaves the
line of rule 1(d) to be reported. A fade of V15 to V0 over sixteen frames
is sixteen `Remap`s.

### The keys and the numbering

A value through a transformation is a key, 0 to 255, on any target, and
the entries it reaches are within the most of its register; a value
through the identity is within the most of its register, as SPEC.md 3.2.2
defines it today. A source of 8-bit samples written to a volume register
through a table of the chip's levels is one use.

The transformations of a tune are numbered from 1: read the rows from row
0 to the last, within a row the timers A, B, C and D, and where a `Start`
or a `Remap` maps through a transformation new to the list, append it. The
identity is 0, outside the list. The forms write the transformations in
that order, and a transformation of a file that no row uses is an error of
the form.

### What a check reports

The errors of SPEC.md 1.11 gain three lines, and SPEC.md 1.12 reports each
tune's transformations after its repeat row and before its rows, each in
number order and prefixed `transformation T: `:

| condition | reported as |
|---|---|
| a transformation has other than 256 entries | `the transformation has E entries, and it maps 256 keys, 0 to 255` |
| entry K is outside 0 to 255 | `entry K is V, and an entry is 0 to 255` |
| value i of row N of the source of a `Start`, through part i of its mapping, is above the most M of register i of its target | `value i of row N maps to V on setRn, and the target is 0 to M` |

The third line comes under the `Start`'s timer, after the lines of the
source. A `Remap` is checked against the source the check's model has its
timer run at that row (SPEC.md 6.2), with the same line, under the
`Remap`'s timer; where the model has the timer idle, rule 5 reports the
`Remap`. Rules 1 and 2 read the registers of the target through any
mapping, `setR7` writes bits 5 to 0 of its entry, and every write of R13,
mapped or not, restarts the envelope (SPEC.md 2.5).

### The forms

- JSON: a tune has `transformations`, an array, after `sources`, of
  `{"name":N,"entries":[...]}`, the entries 20 a line. A timer has a
  `mapping` column after `deferred`: at a start, and as the one part of
  `shape` 3, a remap, the part's number for a target of one value and an
  array of the parts' numbers for a wider one, 0 the identity; -1 at
  `shape` 1 and 2. A file of version 7 has the column wherever a shape
  selects it.
- CSV: a `### transformation` block, its `name` cell, and an `### entry`
  block after it of 256 row lines, `key,entry`, written after the tune's
  sources in number order; a `mapping` cell after `deferred`, its parts
  separated by a space; `shape` 3 in csv.md 3.9, 4.1 step 6 and 5.1.
- The record: the first line has `"transformations"` after `"sources"`, an
  array of the entries of each; a start's object ends with `"mapping":M`,
  and a remap is `{"remap":{"mapping":M}}`, M a number for a target of one
  value and an array otherwise (SPEC.md 7.3, 7.4). The kit's records are
  written again.

### The version

The structure's version is 7. A writer writes 7, and a reader reads 7 and
the four versions before it, 6, 5, 4 and 3. In a file of version 6 or
before a reader skips a `mapping` column and a `transformations` key, and
reads every start through the identity; `shape` 3 there is the error of
the form `a shape is -1, 0, 1 or 2`. SPEC.md 3.3.6 and 8.5 name "the
mapping of the steps of 4.3 to the registers of the MC68901", and are
reworded in the same change, so that *mapping* has one meaning.

## What it changes in YMXR

YMXR encodes an effect as four columns of the tune file, the target, the
source, the control and the count (YMXR's SPEC.md, 1.8), and the eight
bits of the control column are all assigned (YMXR's SPEC.md, 1.9.2). A
table has 128 columns at most (YMXR's requirements.md, R3.4, raised from
32 by odipar/YMXR#319). The
general tick handler moves its row with one instruction from the source to
the chip's data port, through a displacement patched into it, and reads
and writes memory alone: a tick that writes a row costs 96 cycles (YMXR's
performance.md, A tick). The encoding below looks the value up at the tick
of an effect that maps it, and leaves every other tick as it is.

### What it costs where no value is mapped

- An effect through the identity runs the handler of today: a start or a
  remap aims the timer at it. Its ticks cost what they cost today, in a
  tune that maps another effect too.
- A tune that maps no value is the tune file of today, version 9, which a
  player of 0.7.0 plays as well: a writer writes version 10 only where a
  row maps a value, and bit 5 of the effects used marks it (YMXR's
  SPEC.md, 3.3).
- The tools put a core with the lookup in, a switch `YMXR_MAP` beside
  `YMXR_DEFER`, under a file only where a subtune sets bit 5 (YMXR's
  BINARIES.md, 2.1); the cores are 32, the flags word has bit 4 for it,
  and init of a core without it reports -1 for a tune that sets bit 5. A
  file whose subtunes map no value runs the core of today.
- In a file where one subtune maps and another does not, the other runs
  the core with the lookup in: init patches a branch over the frame's
  reading of the mapping columns, 10 cycles a frame, and the binder packs
  its table in an image of 30 columns apart from the 34 of the other. Its
  ticks cost what they cost today.

### The columns

A tune of version 10 has 34 columns: column 30 + i is the mapping of
effect i, bit 7 the set bit and bits 6 to 0 the number of a
transformation, 1 to 127, or 0 for the identity. A row that sets it and
the source column starts the source through that mapping; a row that sets
it and leaves the source column unset is a remap. A start that leaves it
unset keeps the effect's mapping, as it keeps the rate where it leaves the
rate columns unset (YMXR's SPEC.md, 1.8.4), and a remap on an idle timer
sets the mapping the next start keeps. The encoding maps one value a row:
the one value of a target of one register, and the last value YMXS lists
for a wider one, the volume of `setVoiceX` and `setNoiseX`, the shape of
`setBuzzer` and the high byte of `setToneX` and `setEnvelope`.

Two limits of the encoding, each a line a writer reports in place of a
tune file: a tune of more than 127 transformations, and a mapping of
another value of a wider target, or of two values.

### What the columns cost

Measured with four constant columns added to the tables of the eleven
dumps under YMXR's `ym/test`, against their 30 columns of today:

| | moves by |
|---|---|
| the costliest play call | -138 to +1,344 cycles; Turrican - world 4-3 reaches 5,706, 950 under YMXR's R4.5 of 6,656 |
| the mean play call | -31 to +86 cycles |
| the costliest advance | +60 to +566 cycles |
| the workspace | -34 to +352 bytes |

The cost is DTX's period: its packager sets the period at the column
count or above (DTX, abi.md 4), so a refill at unit 2 is 17 units where
it is 15, and the writer's rings are multiples of 34. The frame reads
each mapping column at the head of its effect, 8 cycles a constant one
and 12 a live one, and a live mapping column has its ring in the
workspace, N + 48 bytes. A packager whose period is at least the live
columns, a change of DTX's abi.md 4 and of its three packagers, leaves
the costliest refill of a tune whose added columns are constant as it is:
DTX's reader read every row of four tunes at such a period.

### The tables

A tune file of version 10 has a long at 16, the offset of the
transformation index, a word T and T offsets of 256 bytes each, entry k
the value of key k; the source index follows at 20, and at 28 in the bound
tune (YMXR's BINARIES.md, 1). The tables are read at the frame alone, so
their place in the file has no reach to respect.

### The tick

The key is the byte the tick reads at the place, and the entry is the byte
it writes. The tick writes the key into the low byte of the displacement
of the read that follows it, `move.b slot(pc),$FFFF8802.w`, whose high
byte selects one of two slots of 256 bytes at 256 times that byte from the
displacement: each slot is a copy of the transformation, and the read
writes the entry. Counted with the rig's counter, which agrees with the
68000 manual's timings instruction by instruction:

| the row's read and the marker's branch | cycles | bytes | the handler |
|---|---|---|---|
| today: `move.b place(pc),port`, `bmi.s` | 28 | 8 | 96 |
| the key into the read: `move.b place(pc),patch.l`, then `move.b slot(pc),port`, `bmi.s` | 52 | 16 | 120 |
| for comparison: `lea` the table, an indexed read, d0 and a0 saved | 102 | 26 | 170 |
| for comparison: an indexed read through the program counter, d0 saved | 62 | 18 | 130 |

The first way costs 24 cycles a tick more and uses no register. The
indexed read through the program counter reaches one slot from the read,
so it has no second slot to switch to at a remap; the `lea` costs three
times as much. A source of 8,192 rows a second through a transformation
spends 3,932 cycles a frame more at 50 Hz.

The write of the key comes just before the read it patches, and the 68000
fetches ahead: a `move.b` to an absolute long writes before its final
prefetch, which reads the next instruction's first extension word, the
displacement. On Hatari's cycle-exact 68000 a program read the patched
displacement in 50,000 iterations of 50,000, adjacent and with one
instruction between, and the stale one with a `-(An)` destination, whose
final prefetch comes before its write. A real ST confirms it before the
player relies on it.

The handler of today writes the select, then reads the row; the lookup
handler writes the select, the key and the entry, and tests the marker in
the entry it wrote: a slot of a marked source has entry k + 128 equal to
entry k with bit 7 set, since the register of a marked column reads seven
bits or fewer (YMXR's SPEC.md, 2.1.1). A counted source, whose end is its
counter, has the slot as the table is. The key's read keeps its
displacement at the offset of the place in today's handler, which the
start, the arming and the widening read.

A source whose keys reach 128 or more on a marked target is written
counted, as a source of whole bytes is today, its ticks 20 cycles more
(YMXR's performance.md, A counted tick of two columns); a target of
several registers has a counted shape for `setEnvelope` alone, so such
keys in the marked column of another are a line of the writer.

The core whose ticks read an absolute address reads the key from an
absolute address, 4 cycles more, and the slot through the program counter
as above: the slots are in the core.

### The frame

| a change of mapping | cycles, counted |
|---|---|
| the table's address and the slot's | 26 |
| the copy of 256 bytes into the slot not in use, 8 registers unrolled | 1,260 |
| the same for a marked source: 128 bytes read once and written twice, bit 7 set the second time | 956 |
| the switch of the slot, one byte | 20 |
| a square: its level and its mask, written from two entries | 64 |
| a source of one row: its one entry | 26 |

- The copy runs with interrupts on, into the slot the tick does not read,
  and the switch of one byte makes it the slot the next tick reads.
- A change between the identity and a transformation aims the timer at the
  other handler: the place, the loop cell, the counter and the select are
  copied at level 7, as a start writes its three, and where a start is
  pending its hook is written into the new handler.
- A square keeps bit 7 of its immediate as the half it is in (YMXR's
  68k/YMXR.S, SQUARE): its level is the entry of the key with bit 7 clear,
  with bit 7 of the key, and its mask the difference of its two levels, so
  its ticks cost what they cost today. The place's reset and the arming
  write entries there too.
- A deferred start keeps its mapping: the frame fills the slot of the bank
  the timer does not run before the part of the arming at level 7, and the
  record's copy of the armed start keeps the mapping for the switch
  (YMXR's SPEC.md, 4.8).

### The player's size

Five shapes step a place: the general, the counted, and the wide ones of
two, of three and of `setEnvelope`. Each has a lookup instance beside it,
an effect, in each bank, and an effect has two slots a bank, 512 bytes,
the key's write of each lookup instance placed on one address modulo 256
so that the instances share them. Today's instances of those shapes are
576 bytes an effect a bank; the lookup instances and the slots add about
4,400 bytes to a core, and 8,800 to one with the deferred start in, which
the implementation counts.

### What else changes

- The tools: both trees write version 10 where a row maps a value and 9
  otherwise, read 7 to 10, and write bit 5, the four columns and the index;
  the binder groups images by their column count.
- The model in the rig maps the value each tick writes, and plays a fade,
  a mapped square, a mapped deferred start, a remap of a source the timer
  runs and a remap on an idle timer against the player, under unicorn and
  on Hatari's MFP.
- The record of YMXR's SPEC.md 7 gains the effect's mapping.

## The plan

- [ ] The plan, this document
- [ ] YMXS: the structure, the remap, the keys, the numbering and the
      errors, in SPEC.md 1 to 6, and 3.3.6 and 8.5 reworded
- [ ] YMXS: JSON, CSV and the record; the check; both trees; version 7
- [ ] YMXS: the examples and the conformance kit gain a tune through
      transformations, a fade among them
- [ ] YMXR: version 10, bit 5, the four mapping columns and the index;
      the readers, the binder and the converter
- [ ] YMXR: the read of the patched displacement confirmed on a real ST
- [ ] YMXR: the lookup instances, the slots, the remap and the
      `YMXR_MAP` cores; the model and the rig's tunes
- [ ] YMXR: the cycles and the sizes counted into performance.md
- [ ] The specifications define what landed; this document goes
