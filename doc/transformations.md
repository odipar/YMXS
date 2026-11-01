# Transformations in YMXR: a plan for issue 104

The subject of issue 104 is a mapping applied to a source on its way to the
target: a source of volumes A and sixteen tables V0 to V15, each mapping a
volume to another, so that an effect writes V5(A(row)) where version 6 of
the structure writes A(row). Version 7 of the structure defines the
transformations and the mappings, and this document is the plan for YMXR:
how YMXR encodes and plays them, the value looked up at the tick.

## Where version 7 defines it

| piece | clauses |
|---|---|
| a transformation and the identity | SPEC.md 3.6.1 and 3.6.2 |
| a mapping | SPEC.md 1.1, 1.8, 3.6.3 and 3.6.4 |
| a remap | SPEC.md 1.7, 3.5.5, 4.3 and rule 5 |
| the keys | SPEC.md 3.2.2 and 3.6.5 |
| the numbering of the transformations | SPEC.md 1.9 |
| the errors of the structure a check reports | SPEC.md 1.11 and 1.12 |
| the warning of a remap on an idle timer | SPEC.md 6.3 and 6.5 |
| JSON | json.md 3.7 to 3.9 and 6.7 |
| CSV | csv.md 3.9 to 3.12 |
| the record | SPEC.md 7.3 and 7.4 |
| the version | the opening of SPEC.md, and json.md 2.4 |

V0 to V15 are the transformations `Vn` of SPEC.md 3.6.5, 16 entries each
for the volumes 0 to 15, and a table for 8-bit samples has 256.

## What it changes in YMXR

YMXR encodes an effect as four columns of the tune file, the target, the
source, the control and the count (YMXR's SPEC.md, 1.8), and the eight
bits of the control column are all assigned (YMXR's SPEC.md, 1.9.2). A
table has 128 columns at most (YMXR's requirements.md, R3.4, raised from
32 by odipar/YMXR#319). The general tick handler moves its row with one
instruction from the source to the chip's data port, through a
displacement patched into it, and reads and writes memory alone: a tick
that writes a row costs 96 cycles (YMXR's performance.md, A tick). The
encoding below looks the value up at the tick of an effect that maps it,
and leaves every other tick as it is.

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
transformation index, a word T and T offsets, each of a table: a word E
and E bytes, entry k the value of key k, padded to a multiple of 4; the
source index follows at 20, and at 28 in the bound tune (YMXR's
BINARIES.md, 1). The tables are read at the frame alone, so their place
in the file has no reach to respect.

### The tick

The key is the byte the tick reads at the place, and the entry is the byte
it writes. The tick writes the key into the low byte of the displacement
of the read that follows it, `move.b slot(pc),$FFFF8802.w`, whose high
byte selects one of two slots of 256 bytes at 256 times that byte from the
displacement: each slot is a copy of the transformation, and the read
writes the entry. A slot is 256 bytes for a table of any length, since the
key is a byte. The tick reads keys below E. A marked source has keys below
128, since one with a key of 128 or more on a marked target is written
counted (below); at its marked row the tick reads keys 128 to 127 + E', E'
the lesser of E and 128. A copy fills E bytes of a slot, and 2E' for a
marked source. Counted with the rig's counter, which agrees with the 68000
manual's timings instruction by instruction:

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
| the copy of 256 entries into the slot not in use, 8 registers unrolled, two saved | 1,260 |
| the copy of 16 entries, a table of volumes, 4 free registers, no save | 84 |
| the same for a marked source: 128 entries read once and written twice, bit 7 set the second time, unrolled through 7 data registers, d0, d1 and d3 to d7, d6 and d7 saved | 1,504 |
| the same for a marked source of 16 entries, 4 free data registers, d0, d1, d3 and d4, no save | 192 |
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

- [x] The plan, this document
- [x] YMXS: the structure, the remap, the keys, the numbering and the
      errors, in SPEC.md 1 to 6, and 3.3.6 and 8.5 reworded
- [x] YMXS: JSON, CSV and the record; the check; both trees; version 7
- [x] YMXS: the examples and the conformance kit gain a tune through
      transformations, a fade among them
- [ ] YMXR: version 10, bit 5, the four mapping columns and the index;
      the readers, the binder and the converter
- [ ] YMXR: the read of the patched displacement confirmed on a real ST
- [ ] YMXR: the lookup instances, the slots, the remap and the
      `YMXR_MAP` cores; the model and the rig's tunes
- [ ] YMXR: the cycles and the sizes counted into performance.md
- [ ] YMXR's specifications define what landed; this document goes
