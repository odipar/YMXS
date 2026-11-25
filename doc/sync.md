# Sync squares: a plan

A voice of the YM2149 counts its tone period and flips its square at the
end of each count. A *sync* restarts that count from the player, so the
square's next edge falls one period after a moment the player sets: a
frame, or the tick of a timer. A sync at each tick of a timer is the hard
sync of the sync square, which gwEm describes in
[Alive 9](https://alive.atari.org/alive9/ymsamp.php) (2005) and Steven
Tattersall in
[YM2149 Sync-Square](https://clarets.org/steve/projects/2021_ym2149_sync_square.html)
(2021): the timer sets the rate the square restarts at, and the rows its
pitch. This document is the plan: the structure YMXS gains at version 10,
what a check reports, the forms, and what YMXR encodes. A version of the
structure that defines it replaces this document.

## What the chip does

In Tattersall's model, reached by experiment, the tone generator of a
voice steps once every 8 cycles of the chip's clock: it adds 1 to a count,
and where the count is at or above the period it flips the square and sets
the count to 0. A period of 0 steps as a period of 1. A period written
below the count leaves the count as it is, so the next step flips the
square and restarts the count. A sync of voice A is three steps:

1. 0 to R0 and to R1;
2. a wait of at least one step, 8 cycles of the chip's clock: 32 cycles of
   an 8 MHz 68000 on an ST, whose chip runs at 2 MHz;
3. the period to R0 and R1, fine then coarse.

R2 and R3 sync voice B, and R4 and R5 voice C. The square flips at each
step of the wait and its count is 0 after the last of them, so its next
edge falls one period after that step. The level of the square after a
sync, high or low, is the chip's: gwEm hears the two at random. A sync sets
when the square flips, and the chip sets which way.

On an STE the CPU's clock is 4.0053 times the chip's, so 32 cycles of the
CPU are 7.98 of the chip, and a wait of 32 misses a step now and then,
about one in 95 by Tattersall's count; 36 cycles, 9 of the chip, reach a
step on either machine. Tattersall finds Hatari close to the chip; gwEm
finds that STeem and SainT restart the square at one level, where the chip
restarts it at either.

## The structure

Two additions, both of version 10:

- a row syncs voices: `syncs`, the voices whose square the row syncs at
  step 4 of its frame;
- three targets, `syncToneA`, `syncToneB` and `syncToneC`, each of which
  syncs its voice at each tick before writing the period.

```java
record Row(Map<Register, Integer> registers, Map<Timer, Effect> effects,
           Set<Voice> syncs) { }

sealed interface TwoTarget extends Target permits SetTone, SyncTone, SetNoise,
        SetEnvelope { }

record SyncTone(Voice voice) implements TwoTarget { }
```

`Set<T>` joins the reading of SPEC.md 1.1: a set of values, each at most
once.

A sync is a value of the row beside its registers. R1, R3 and R5 keep the
four bits of the period (SPEC.md 2.1), and each form and each encoding
defines where it writes the sync (The forms; What it changes in YMXR).

### The wait

A sync waits at least 9 cycles of the chip's clock between the zeros and
the period: one step of the tone generator and a cycle more, so the wait
reaches a step where the clocks of the CPU and the chip drift apart, as on
the STE. On an 8 MHz 68000 that is 36 cycles.

### A sync at a frame

Step 4 of a frame (SPEC.md 4.4) writes R0 to R5 at its first step. Before
that step, for each voice the row syncs on whose period no effect runs at
step 4 (SPEC.md 6.2 d): write 0 to both registers of its period, wait
(The wait), then write the frame values of the two registers (SPEC.md
3.8.1), fine then coarse. The frame value is the row's value where the row
sets the register and the value the register last had where it leaves it,
so both registers are set again on every sync. A row that syncs two or
three voices writes the zeros of each, waits once, and writes their
periods. A row that syncs a voice and leaves its period as it is restarts
the square at the period it has.

Where an effect runs on either register of the voice's period at step 4,
copy from frame included, the frame leaves the sync out: the sync of that
voice is the effect's, made at its ticks where its target is a sync target
(A sync at a tick). Rule 1(f) reports such a row (What a check reports).

The wait at a frame keeps the frame's interrupt level: a tick inside it
lengthens the wait and moves that one restart, where level 7 over it would
delay the ticks of every running effect.

### A sync at a tick

At a tick, `syncToneA` syncs voice A and writes the row's two values to R0
and R1, fine then coarse, through the mapping (SPEC.md 3.1.1, 3.6.4). Its
row is the row of `setToneA`: two values, the fine byte 0 to 255 and the
coarse 0 to 15, and every source that runs on a tone target runs on it.
The targets are numbered 25 to 27 (SPEC.md 3.1.2): `syncToneA` 25,
`syncToneB` 26 and `syncToneC` 27.

A sync target makes its sync at level 7, from the zeros to the period's
writes, so the restart falls at the handler's instructions, a fixed time
after the tick's interrupt. A tick of another timer due inside that window
runs after it, late by up to the window's length: the two zero writes, the
wait and the period's two writes, about 130 cycles.

With copy from frame as its source at the rate of a timer, `syncToneA`
restarts voice A's square at each tick at the period the rows set: the
hard sync of the two articles. A source of two rows or more changes the
period at each restart instead.

Uses (informative):

- a sync sweep: copy from frame on `syncToneA` at a fixed rate, and rows
  that move R0 and R1, so the pitch between restarts moves under a fixed
  restart rate;
- a pulse: a timer period a little longer than the tone period, so the
  square flips at the end of its period and again at the restart, a pulse
  whose width is the difference (Tattersall's 6,000 cycles of a timer
  against a period of 5,000);
- a bass on the envelope: a row that syncs the voice on each note's first
  row, so the square restarts at the frame that restarts the envelope.

### What a check reports

A sync target reads the row of a tone target, so the errors of a tone
target read it: a source of other than two values a row (SPEC.md 3.2.1),
and a value outside its register through the identity or a transformation
(SPEC.md 3.2.2). A row may sync any voice, with its period set or unset.

Rule 1 gains a clause and a warning:

- 1(f) A row breaks the rule where it syncs a voice while an effect runs
  on a register of the voice's period at step 4 of its frame: the frame
  leaves that sync out (A sync at a frame). The effect may be copy from
  frame, whose registers 1(e) excludes from 1(a) alone.

| rule | condition | reported as |
|---|---|---|
| 1(f) | row N syncs voice V, and the effect of Timer X runs on a register of that voice's period at step 4 | `row N: syncs voice V, whose period Timer X runs on: the frame leaves the sync to the effect` |

### The forms

| form | what it gains |
|---|---|
| JSON (json.md 5) | `syncs`, an object whose keys are among `a`, `b` and `c`, each a column of 0 and 1 a row, 1 where the row syncs the voice; an emitter emits the key of each voice some row syncs |
| JSON (json.md 6) | the targets `syncToneA`, `syncToneB` and `syncToneC` |
| CSV (csv.md 3) | `syncA`, `syncB` and `syncC` cells in the `registers` block, `1` or empty |
| CSV (csv.md 3) | the targets `syncToneA`, `syncToneB` and `syncToneC` |
| the record (SPEC.md 7.4) | `"s":["A"]` after `w`, the voices the row syncs in order, where the row syncs one |

A record of a tune of version 9 reads as before. `s` lists the voices of
the row, a voice whose sync the frame leaves out among them, as `w` lists
the registers of the row.

### The version

The structure's version is 10: a writer writes 10, and a reader reads 10
and 9 to 3. In a file of version 9 or before, `syncs` and the three targets
are errors of the form. A voice target with a sync, of a period and a
volume, joins the targets a later version defines (SPEC.md 8.2).

## What it changes in YMXR

The encoding:

- The sync at a frame is bit 5 of the voice's coarse column, 1, 3 or 5
  (YMXR's SPEC.md 1.2.2), where bits 5 and 4 are unassigned; bit 6 there
  marks the fine column's 0 as a value (1.1.2). A writer sets bit 5 where
  the frame syncs the voice, and leaves it clear where an effect runs on
  the voice's period (1(f)). On a row with bit 5 set it sets both columns
  of the voice, a register's frame value where the YMXS row leaves the
  register unset, so a player writes the period from the row alone. A
  player reads bit 5 where the row sets the coarse column.
- The targets are 25, 26 and 27 in a start's target column, whose bits 6
  to 0 reach 127 (YMXR's SPEC.md 1.8.2), and the map of targets gains
  their three rows (2.1).
- The tune file is version 13, with a bit of its header set for a tune
  that syncs a voice, at a frame or through a target. Init aims the frame
  and the starts at a block `sync` for such a tune alone, as it aims them
  at the block `copy`, so every other tune runs the frame and the ticks it
  ran in version 12, at the same cycles, and a song of such tunes links the
  same player.

The player:

- The frame: the register steps of a tune that syncs read bit 5 of each
  coarse column the row sets, write the zeros of each voice it marks, wait
  36 cycles, and run the steps of R0 to R5. The wait is the steps'
  instructions where they reach 36 cycles, and a delay otherwise. The
  register steps of the block `copy` gain the same test, for a tune that
  syncs and copies from frame.
- The ticks: a sync target runs the counted handler of two columns at every
  shape of source, as copy from frame runs the counted handlers, with the
  two zero writes at its head and its row's reads between them and the
  writes, at level 7 from the first zero to the period's writes; through a
  transformation, the counted lookup instance of two columns with the same
  head. A zero write is a `move.l` of the select and the data at once, 20
  cycles, so a sync tick is about 40 cycles more than the counted tick of
  `setToneA`, 218 at the end of a pass; the rig counts it once the handler
  is built.
- The rig: the model writes the zeros and the period, and the rig compares
  each write; under Hatari the rig reads the cycles from the zeros to the
  period's first write and fails a wait below 36.

## The plan

- [x] The plan, this document
- [ ] YMXS: the structure, `syncs` and the three targets, in sections 1,
      3.1, 4.4, 5, 6, 7 and 8 of SPEC.md, with a section on the sync, 3.9,
      and the chip's steps in 2
- [ ] YMXS: JSON, CSV and the record; the check and rule 1(f); both trees;
      version 10
- [ ] YMXS: a tune of the conformance kit that syncs at frames and through
      each target, on copy from frame
- [ ] YMXR: the encoding in both trees, version 13; the frame's sync and
      the sync handler; the rig against the model and the wait under Hatari
- [ ] YMXR: the cycles of a sync at a frame and at a tick in performance.md
- [ ] The specifications define what landed; this document goes

## Settled

- The sync at a frame is a value of the row in YMXS and a bit of the
  coarse column in YMXR alone: YMXS defines the structure, and YMXR the
  bytes.
- A sync writes 0 to both registers of the voice's period and then both
  again: R0 and R1 for voice A, R2 and R3 for B, R4 and R5 for C.
- A sync at a tick is a separate target, beside the tone targets, whose
  writes are as they were.
- Copy from frame runs on a sync target as on any target of two registers.
- The frame syncs a voice on whose period no effect runs; where one runs,
  copy from frame included, the sync is the effect's, and the frame leaves
  the row's sync out (1(f)).
- A sync target's sync runs at level 7 from the zeros to the period's
  writes.
- The wait is 9 cycles of the chip's clock, 36 of an 8 MHz 68000, so it
  reaches a step on the STE.
- A voice target with a sync, `syncVoiceA` of R0, R1 and R8, is left to a
  later version.
