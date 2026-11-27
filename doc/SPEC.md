# The YMXS format

Version 12 defines tune data and playback on the Atari ST's YM2149 and
MC68901: structure (1), chip values (2, 3), frames (4), ticks (5), writer
rules and checks (6), recorder output (7), and later versions (8).

A form is an encoding of the structure: [json.md](json.md),
[csv.md](csv.md), or a player's binary layout, defined by that player. A
file includes its version beside the structure: a writer writes 12, and a
reader reads 12 and the nine versions before it, 11, 10, 9, 8, 7, 6, 5, 4
and 3 (json.md 2.4). A reader reads the version before the structure;
another version is an error of the form.

**Conventions.** A clause is cited by number, 4.3 or 3.2.1; a rule of
section 6 as rule 1, a condition of one as rule 1(a). `Note:` begins an
informative sentence. A range includes both ends. Integer division
discards the remainder. In a reported text, `Rn` is a register, `setRn`
and `setRm` are two targets by their names of 3.1.2, `setToneA` among
them, `NAME` is a text, a capital letter after `Timer` is a timer's
letter, one after `voice` a voice's letter, and each other capital letter,
and `i`, is a decimal figure, each defined beside the text.

**Roles.** A player performs sections 4 and 5 on the two chips. A recorder
performs section 4 with every register a row sets and every timer
operation recorded as an entry of section 7 in place of the chip. A check
reports the errors of 1.11 in the order of 1.12 and the warnings of 6.5 in
the order of 6.3. A writer produces a structure free of the conditions of
1.11 that satisfies the rules of section 6. A reader reads a file of a
form into the structure, and an emitter emits one from it. The host
selects a tune (1.2), calls the player once a frame, and owns bits 7 and 6
of R7 and every timer outside the tune's (1.10).

**Terms.** A *frame* is one call of the player (4.2); a *tick* one
interrupt of a timer (5.1); a *row* one entry of a table (1.6). The rate
of a tune is in frames a second (1.3), the rate of an effect in ticks a
second (3.3.1). An *error of the structure* is a condition of 1.11, an
*error of the form* a condition of json.md 8.1 or csv.md 5.1, and a
*warning* a breach of rule 1, 2, 3, 5, 6 or 7 (6.5). A *pass* is the
frames from the one that reads row 0 to the one that reads the last row; a
*loop* the frames from the one that reads the repeat row to the one that
reads the last row (4.5). Section 6.2 defines *runs*, *idle* and *run
out*; 6.3 defines a *run*. Section 3.8 defines *copy from frame* and the
*frame value* of a register, and 3.9 a *sync*, a *step* and a *sync
length*.

---

## 1. The tune data structure

**1.1** The block is the structure; every declaration is normative, and its
names are the names of this document. Read as Java: a `record` is a tuple of
named fields in declared order; a `sealed interface` with `permits` is a
choice among the records listed; an `enum` is a choice among its constants,
numbered from 0 in declared order; `List<T>` is a sequence numbered from 0;
`Map<K, V>` is a set of pairs, one at most under each key; `Set<T>` is a
set of values, each at most once; `OptionalInt` is an integer, present or
absent; `int` and `Integer` are 32-bit signed integers; `boolean` is true
or false; `String` is text, the empty text included.

```java
package org.ymxs;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

public interface YMXS {

    record Multi(List<Tune> tunes) { }

    record Tune(String title, String composer, String writer, int rate,
                Table<Row> table) { }

    record Table<T>(List<T> rows, OptionalInt repeat) { }

    record Row(Map<Register, Integer> registers, Map<Timer, Effect> effects,
               Set<Voice> syncs, Map<Voice, Integer> syncSteps) { }

    sealed interface Effect permits Start, Retune, Stop, Remap, Recut { }

    record Start(Connection connection, Timing timing, boolean deferred,
                 Mapping mapping, Cut cut) implements Effect { }

    sealed interface Connection permits OneConnection, TwoConnection, ThreeConnection,
            CopyFromFrame { }

    record OneConnection(OneTarget target, Single source) implements Connection { }

    record TwoConnection(TwoTarget target, Pair source) implements Connection { }

    record ThreeConnection(ThreeTarget target, Triple source) implements Connection { }

    record CopyFromFrame(Target target) implements Connection { }

    record Timing(Prescaler prescaler, int count, boolean timerReset,
                  boolean placeReset) { }

    record Retune(Timing timing) implements Effect { }

    record Stop() implements Effect { }

    record Remap(Mapping mapping) implements Effect { }

    record Recut(Cut cut) implements Effect { }

    record Cut(int first, int last) { }

    sealed interface Target permits OneTarget, TwoTarget, ThreeTarget { }

    sealed interface OneTarget extends Target permits SetRegister { }

    sealed interface TwoTarget extends Target permits SetTone, SyncTone, SetNoise,
            SetEnvelope { }

    sealed interface ThreeTarget extends Target permits SetVoice, SetBuzzer { }

    record SetRegister(Register register) implements OneTarget { }

    record SetTone(Voice voice) implements TwoTarget { }

    record SyncTone(Voice voice) implements TwoTarget { }

    record SetNoise(Voice voice) implements TwoTarget { }

    record SetEnvelope() implements TwoTarget { }

    record SetVoice(Voice voice) implements ThreeTarget { }

    record SetBuzzer() implements ThreeTarget { }

    sealed interface Source permits Single, Pair, Triple { }

    record Single(String name, Table<Integer> table) implements Source { }

    record Pair(String name, Table<Two> table) implements Source { }

    record Triple(String name, Table<Three> table) implements Source { }

    record Two(int first, int second) { }

    record Three(int first, int second, int third) { }

    record Mapping(List<Part> parts) { }

    sealed interface Part permits Identity, Transformation { }

    record Identity() implements Part { }

    record Transformation(String name, List<Integer> entries) implements Part { }

    enum Register {
        R0, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13
    }

    enum Voice { A, B, C }

    enum Timer { A, B, C, D }

    enum Prescaler { BY_4, BY_10, BY_16, BY_50, BY_64, BY_100, BY_200 }
}
```

**1.2 A multi.** A nonempty list of tunes, numbered from 1 in list order.
The host selects a tune by number. An error of the structure (1.11) in
any tune excludes the multi from playback.

**1.3 A tune.** A title, a composer, a writer, a rate and a table of rows.
The three texts are for display alone. The rate is the frames a second at
which the host calls the player, 1 to 2,147,483,647; below 1 is an error of
the structure (1.11).

**1.4 A table.** A list of rows, numbered from 0, one at least (1.11),
and a repeat row, present or absent. A present repeat row is 0 to R - 1
for R rows; outside that it is an error of the structure (1.11). A table
whose repeat row is absent plays once. A tune's table and a source's are
one kind: the player reads a tune's rows one a frame (4) and a source's
one a tick (5).

**1.5 The row after a row.** In a table of R rows, the row after row n
is n + 1 for n below R - 1; for n = R - 1, the repeat row where present,
and where absent the table has ended.

**1.6 A row.** `registers` maps each register to the value the row sets;
`effects` maps each timer to its operation; `syncs` is the voices whose
square the row syncs at step 4 of its frame (3.9); `syncSteps` maps a
voice to its sync length, 1 to 8 steps (3.9.1), for the sync of its frame
and the syncs of a sync target at the ticks 3.9.4 defines. An absent
register keeps its value; an absent timer continues unchanged; an absent
voice has a sync length of 1. A row sets each register at most once and
performs at most one operation on each timer.

**1.7 An effect and the five operations.** The effect of a timer is the
source connected to a target on it through a mapping, under a cut, at the
rate of its prescaler and count (3.3, 3.6, 3.7, 3.8). Four timers, each
running one effect at most; the timer identifies the effect. An operation
is a value of the type `Effect` (1.1): a `Start` connects the source of
`connection` to its target through `mapping` under `cut` on the timer and
sets `prescaler` and `count`; a `Retune` sets `prescaler` and `count` of
the effect the timer runs; a `Stop` stops the timer; a `Remap` sets the
mapping of the effect the timer runs; a `Recut` sets its cut. A `Start`
and a `Retune` have the resets `timerReset` and `placeReset` (3.4), and a
`Start` has `deferred` (3.5). A `Start` on a timer running an effect ends
it and begins another: at its frame where `deferred` is clear, and where
it is set at the tick that reads the last row of the cut of that effect.
Section 4 defines each operation as steps.

**1.8 Targets, sources and mappings.** Targets write one, two or three
registers (3.1). `SetRegister` writes one; `SetTone`, `SetNoise` and
`SetEnvelope` write two; `SetVoice` and `SetBuzzer` write three.

Sources match those widths: `Single`, `Pair` and `Triple` each have a
name and a table of one, two or three values per row (3.2). A
`OneConnection`, `TwoConnection` or `ThreeConnection` pairs a source
with a target of the same width. A `CopyFromFrame` pairs a target of any
width with copy from frame, a source of one row whose values are the
frame values of the registers the target writes (3.8).

A `Start` supplies that connection, a `Mapping` and a `Cut`. An empty
mapping leaves every value unchanged. Otherwise, each value has an identity
part or an optional transformation lookup (3.6). A cut is the first row and
the last row of the source that the ticks of the effect read (3.7).

**1.9 The sources and the transformations of a tune.** The sources are
the list this procedure produces from an empty list: read the rows from
row 0 to the last, within a row the timers A, B, C, D; where the operation
is a `Start` whose source is new to the list, append it, copy from frame
excepted (3.8). The order is the *first-start order*; the sources are
numbered from 1 in it, and a source is selected by number. Two sources are
equal where their names are equal, their values equal in number and order
a row at a time, and their repeat rows both absent or both present and
equal; two `Start`s of equal sources start one source of the tune, and
equal values under two names are two sources.

To list transformations, scan rows from first to last, timers A to D
within each row, and parts from part 0 within each `Start` or `Remap`.
Append each transformation at its first use, comparing as 3.6.2 defines.
Number the list from 1. Identity is 0 and is excluded from the list.

**1.10 The timers of a tune.** The timers on which some row performs a
`Start`, in the order A, B, C, D; a timer with only a `Retune`, a `Stop`, a
`Remap` or a `Recut` is outside them.

**1.11 Errors of the structure.** A structure with a condition below is
outside what a player plays, and a check reports each condition present as
its line, in the order of 1.12; 1.12 omits a condition that rests on
another condition present. H is a rate in frames a second, R a row count,
X a repeat row, V a value, M the most of a register (2.1), C a count, T a
rate in ticks a second, U the values a target reads, N a row number, E an
entry count, K a key, P the parts of a mapping, F and L the first and the
last row of a cut, D a sync length, and i the number of a value in a row
and of its part, from 0.

| condition | reported as |
|---|---|
| the multi is empty | `a multi of no tunes: a host plays one` |
| the rate of a tune is below 1 | `a rate of H: a player is called at least once a second` |
| the table of a tune is empty | `the tune has no rows: a clock reads one` |
| the repeat row of a tune of R rows is present and outside 0 to R - 1 | `the tune has R rows and repeats to row X` |
| a transformation has E entries, E outside 1 to 256 | `the transformation has E entries, and a transformation has 1 to 256` |
| entry K of a transformation is V, outside 0 to 255 | `entry K is V, and an entry is 0 to 255` |
| a row sets a register to a value outside 0 to its most | `Rn is 0 to M, and this row sets it to V` |
| a `Start` or a `Retune` has a count outside 0 to 255 | `a count of C: a timer's data register is a byte, 0 to 255, and 0 counts 256` |
| a `Start` or a `Retune` with a count of 0 to 255 has a rate above 125,000 (3.3.4) | `a rate of T ticks a second: a 68000 at 8 MHz enters an interrupt and leaves it in 64 cycles, so 125000 a second is every cycle it has` |
| the table of the source of a `Start` is empty | `the source has no rows: a clock reads one` |
| the repeat row of the source of a `Start`, of R rows, is present and outside 0 to R - 1 | `the source has R rows and repeats to row X` |
| row N of the source of a `Start` is a value below 0 | `row N is V, and a register is 0 upward` |
| value i of row N of the source of a `Start` is V, above the most M of register i of its target, where part i of its mapping is the identity (3.6.3) | `a source on setRn whose row N is V, and the target is 0 to M` |
| value i of row N of the source of a `Start` is V, above the last key K of the transformation of 1 to 256 entries that is part i of its mapping (3.6.5) | `a source on setRn whose row N is V, and a key is 0 to K` |
| the mapping of a `Start` has P parts, P of 1 or more and other than the U values its target reads | `a mapping of P parts on setRn, which reads U` |
| value i of row N of the source of a `Start`, through a transformation that is part i of its mapping, maps to V, above the most M of register i of its target | `value i of row N maps to V on setRn, and the target is 0 to M` |
| row N sets Rn to V, value i of copy from frame (3.8.4) on setRm, above the last key K of the transformation of 1 to 256 entries that is part i of the mapping of a `Start` of copy from frame on setRm, or of a `Remap` on its timer | `row N sets Rn to V, which copy from frame on setRm reads, and a key is 0 to K` |
| row N sets Rn to K, value i of copy from frame on setRm, and the transformation that is part i of the mapping of a `Start` of copy from frame on setRm, or of a `Remap` on its timer, maps K to V, above the most M of Rn | `row N sets Rn to K, which maps to V on setRm, and the target is 0 to M` |
| the first row of the cut of a `Start` is F, outside 0 to R - 1, R the rows of its source, 1 or more | `the source has R rows and the cut begins at row F` |
| the last row of the cut of a `Start` is L, outside 0 to R - 1, R the rows of its source, 1 or more | `the source has R rows and the cut ends at row L` |
| the last row L of the cut of a `Start` of a source of 1 row or more is below its first row F | `a cut of rows F to L: a cut ends at or after the row it begins at` |
| the mapping of a `Remap` has P parts, P of 1 or more and other than the U values the target of a `Start` on its timer reads | `a mapping of P parts on setRn, which reads U` |
| value i of row N of the source of a `Start` on the timer of a `Remap` is V, above the last key K of the transformation of 1 to 256 entries that is part i of the mapping of the `Remap` | `a source on setRn whose row N is V, and a key is 0 to K` |
| value i of row N of the source of a `Start` on the timer of a `Remap`, through part i of the mapping of the `Remap`, maps to V, above the most M of register i of the target of the `Start` | `value i of row N maps to V on setRn, and the target is 0 to M` |
| the last row L of the cut of a `Recut` is below its first row F | `a cut of rows F to L: a cut ends at or after the row it begins at` |
| the first row of the cut of a `Recut` is F, outside 0 to R - 1, R the rows, 1 or more, of the source of a `Start` on its timer | `the source has R rows and the cut begins at row F` |
| the last row of the cut of a `Recut` is L, outside 0 to R - 1, R the rows, 1 or more, of the source of a `Start` on its timer | `the source has R rows and the cut ends at row L` |
| a row's sync length of voice V is D, outside 1 to 8 | `a sync length of D for voice V: a sync is 1 to 8 steps` |

The mapping of a `Remap` is read against each `Start` on its timer at any
row of the tune, before or after the `Remap`, with the target and the source
of that `Start` (3.6.5). The cut of a `Recut` is read against the source of
each `Start` on its timer in the same way (3.7.1). Copy from frame is read
as a source of one row, with the values of 3.8.4.

**1.12 The order of the report.**

Report each condition present, in this order:

1. The multi.
2. Each tune in number order, prefixed `tune N: ` when checking a multi
   and unprefixed when checking one tune:
   1. The rate.
   2. An empty table, then the repeat row.
   3. Each transformation in number order (1.9), prefixed
      `transformation F, NAME: `: the number of its entries, then
      entries outside 0 to 255 in key order.
   4. Each row in order, prefixed `row N: `:
      1. Registers, R0 to R13.
      2. Each timer with a `Start`, a `Retune`, a `Remap` or a `Recut`,
         A to D, prefixed `Timer X: `:
         1. The count of a `Start` or a `Retune`.
         2. Its rate, omitted for a count outside 0 to 255.
         3. For a `Start`: an empty source table, its repeat row, values
            below 0 in row order. If the mapping has no parts or a part
            for each value, values above the register's most through the
            identity, or above the last key of a transformation of 1 to
            256 entries, in row order. Then the number of parts of the
            mapping, and if that is absent, values mapped through a
            transformation above the register's most in row order. Then,
            where the source table has a row, the first row of the cut
            outside 0 to R - 1, the last row outside it, and a last row
            below the first. Of copy from frame, the rows and their order
            are those of 3.8.4.
         4. For a `Remap`: for each target a `Start` on the timer
            connects, in the row order of the first `Start` that connects
            it, the number of parts of the mapping; then for each target
            and source a `Start` on the timer connects, in the row order
            of the first `Start` that connects the two, prefixed `start
            of row S: `, where the number of parts on that target is
            absent, values above the last key of a transformation of 1 to
            256 entries or mapped above the register's most, in row
            order.
         5. For a `Recut`: a last row below the first; then for each
            source a `Start` on the timer connects, in the row order of
            the first `Start` that connects it, prefixed `start of row
            S: `, where that source has a row, the first row outside 0
            to R - 1, then the last row outside it.
      3. Sync lengths outside 1 to 8, voices A to C.

N is the tune or row number; X is the timer's letter; F is the
transformation's number (1.9) and NAME its name, each line feed in it
written as the two characters `\n` and each carriage return as `\r`; S is
the row of the first `Start` that connects the target and the source, and
for a `Recut` the source. Prefixes accumulate. Within each item, the lines
of a row of a source come in value order, from value 0. A value through a
transformation of 1 to 256 entries is read against its last key; where the
value is a key, its entry is compared with the register's most where that
entry is 0 to 255, and a value through a transformation of another number of
entries is skipped.

---

## 2. The registers

**2.1** A row sets a register to 0 to its most; outside that is an error
of the structure (1.11). R14 and R15 of the YM2149 are its I/O ports and
belong to the host; a tune reaches these fourteen:

| register | reaches | most |
|---|---|---|
| R0 | voice A's tone period, the low byte | 255 |
| R1 | voice A's tone period, the high four bits | 15 |
| R2 | voice B's tone period, the low byte | 255 |
| R3 | voice B's tone period, the high four bits | 15 |
| R4 | voice C's tone period, the low byte | 255 |
| R5 | voice C's tone period, the high four bits | 15 |
| R6 | the noise period | 31 |
| R7 | mixing, six bits | 63 |
| R8 | voice A's volume | 31 |
| R9 | voice B's volume | 31 |
| R10 | voice C's volume | 31 |
| R11 | the envelope period, the low byte | 255 |
| R12 | the envelope period, the high byte | 255 |
| R13 | the envelope shape | 15 |

**2.2 A period over two registers.** A voice's tone period is twelve bits,
the low byte in R0, R2 or R4 and the high four bits in R1, R3 or R5; the
envelope period is sixteen, the low byte in R11 and the high in R12. A row
sets either register of a pair alone (1.6).

**2.3 Volume.** In R8, R9 and R10, bits 3 to 0 are the level; bit 4 set
selects the level of the envelope generator instead, so 16 to 31 follow
the envelope.

**2.4 Mixing.** R7 is eight bits, of which a tune sets six:

| bit | selects |
|---|---|
| 0 | the tone of voice A |
| 1 | the tone of voice B |
| 2 | the tone of voice C |
| 3 | the noise of voice A |
| 4 | the noise of voice B |
| 5 | the noise of voice C |
| 6 and 7 | the directions of the two I/O ports, which belong to the host |

A set bit among bits 5 to 0 silences what it selects. A tune's value for
R7 is bits 5 to 0. Every write of R7, at a frame (4.4) or by a target at
a tick (3.1.1), writes bits 5 to 0 and leaves bits 7 and 6 as they were.

**2.5 The envelope shape.** Every write to R13 restarts the envelope,
the value changed or the same. A player writes R13 on every row that
sets it, the value R13 already has included, except where the frame leaves
R13 to the ticks of copy from frame (3.8.3); every tick of an effect whose
target is `setR13` writes it, the value written through its mapping
(3.1.1, 3.6.4).

---

## 3. The effects

### 3.1 The targets

**3.1.1** At a tick, a target writes one source row to the registers
below. Source value i passes through mapping part i before reaching
register i. An empty mapping uses identity for every value (3.6).
Source width must match target width (3.2.1); a nonempty mapping has
that many parts (3.6.3).

| target | registers, value i of the row to register i | values a row |
|---|---|---|
| `setR0` to `setR13` | `Rn` | 1 |
| `setToneA`, `setToneB`, `setToneC` | R0 R1, R2 R3, R4 R5 | 2 |
| `setVoiceA`, `setVoiceB`, `setVoiceC` | R0 R1 R8, R2 R3 R9, R4 R5 R10 | 3 |
| `setEnvelope` | R11 R12 | 2 |
| `setBuzzer` | R11 R12 R13 | 3 |
| `setNoiseA`, `setNoiseB`, `setNoiseC` | R6 R8, R6 R9, R6 R10 | 2 |
| `syncToneA`, `syncToneB`, `syncToneC` | R0 R1, R2 R3, R4 R5, through a sync of the voice (3.9) | 2 |

`setR7` writes bits 5 to 0 of the value written (3.6.4) and leaves 7 and 6
(2.4). A tone target writes the twelve bits of a voice's period, fine then
coarse (2.2); a voice target writes those and the volume, whose bit 4
selects the envelope (2.3); an envelope target writes the sixteen bits of
the envelope period (2.2), and a buzzer target those and the shape, which
every write restarts (2.5). A noise target writes the noise period and one
voice's volume; R6 is one register for the three voices (2.1), so two noise
targets running at once write one period and the later tick's write
remains. A sync target writes the two values of a tone target as the
writes of a sync of its voice (3.9.1).

The order of the writes is the player's, except the writes of a sync
(3.9.1), with the two the sound depends on fixed here: a voice's fine byte
before its coarse nibble, so the period in effect between the two writes
is the new fine with the old coarse rather than the other way round; and a
buzzer's shape after its period, since writing the shape restarts the
generator on the period in effect.

**3.1.2** A target of one register is numbered by its register, 0 to 13, and
named `set` followed by the register's name. The targets of several
registers are numbered 14 to 27, in the order of the table above:
`setToneA` 14, `setToneB` 15, `setToneC` 16, `setVoiceA` 17, `setVoiceB`
18, `setVoiceC` 19, `setEnvelope` 20, `setBuzzer` 21, `setNoiseA` 22,
`setNoiseB` 23, `setNoiseC` 24, `syncToneA` 25, `syncToneB` 26 and
`syncToneC` 27. A later version numbers further targets 28 to 127 (8.2).

### 3.2 The sources

**3.2.1** A source is a name and a table (1.4) of one, two or three values a
row, the values a row the target of every effect that runs it reads (3.1.1).
The `Connection` of every `Start` of the structure pairs a target with such
a source (1.1), and a reader reports a `Start` of a source of other values a
row than its target reads as an error of the form (json.md 8.1, csv.md 5.1).
The name is text for display and for reports, and distinguishes sources
(1.9); a player reads the table alone.

**3.2.2** Validate every source row through each start's mapping and
every remap on that start's timer. Identity values must fit their target
registers. Lookup keys must fit their transformations, and selected
entries must fit the registers. Violations are errors of the structure
(1.11); 3.6.5 defines these checks.

**3.2.3** Two effects run one source where two rows start it on two timers;
it is one source of the tune (1.9). Each timer has a separate place (3.4.2)
and a separate cut (3.7.1), and an operation on one timer moves that place
alone.

### 3.3 The rate

**3.3.1** The clock of the timers is 2,457,600 a second. A prescaler
divides it by its divisor; a timer counts the divided clock down from its
count through zero and raises a tick at each zero. The rate of an effect
is 2,457,600 divided by the divisor times counted(C), C the count and
counted as 3.3.3 defines.

**3.3.2** The seven prescalers, each with its divisor and the code that
selects it in the control register of a timer of the MC68901; code 0 stops
the timer.

| prescaler | divides by | control code |
|---|---|---|
| `BY_4` | 4 | 1 |
| `BY_10` | 10 | 2 |
| `BY_16` | 16 | 3 |
| `BY_50` | 50 | 4 |
| `BY_64` | 64 | 5 |
| `BY_100` | 100 | 6 |
| `BY_200` | 200 | 7 |

**3.3.3** The count is the value of the data register of the timer, 0
to 255; outside that is an error of the structure (1.11). The timer
loads the count and counts it down through zero, so counted(C), the
steps of the divided clock between two ticks, is C for 1 to 255 and 256
for 0.

**3.3.4** A rate above 125,000 ticks a second is an error of the
structure (1.11). The slowest rate, a count of 0 at a divisor of 200, is
48; a count of 1 at a divisor of 4 is 614,400, an error.

Note: a 68000 at 8 MHz spends 44 cycles entering an interrupt and 20
leaving it, and 8,000,000 divided by 64 is 125,000.

**3.3.5 A rate written to a running timer.** A count loads when the
running count reaches zero: the period in progress ends at the old
count, the next begins at the new. A prescaler divides the clock from
the write on while the running count continues: the period in progress
is counted partly at each prescaler. With `timerReset` set the timer is
stopped before the write (3.4.1), so both apply from it.

**3.3.6** How the steps of 4.3 are written to the registers of the MC68901,
the order of the count and the prescaler within one step, and how a player
claims a timer from the host are left to a later version (8.5).

### 3.4 The two resets

**3.4.1 `timerReset`.** Set: the player stops the timer before writing
the count and the prescaler (4.3); the timer starts at that write and
begins a whole period at the count, the steps already counted discarded.
Clear: a running timer continues its period, and the writes apply as 3.3.5
defines. On a stopped timer the outcome is the same either way.

**3.4.2 The place.** Each timer has a place: the number of the row of its
source that the next tick reads. A tick (5.2), step 4 of a `Start` or a
`Retune` (4.3) and a `Recut` (4.3) move it, those three alone. Before the
first `Start` or `Retune` with `placeReset` set on a timer, its place is
left to a later version (8.4).

**3.4.3 `placeReset`.** Set: the player sets the place to the first row of
the cut (4.3, 3.7.1), and the next tick reads that row; under the whole
table it is row 0. Clear: the player sets the place to its number modulo the
row count of the source connected, and to the first row of the cut where
that row is outside the cut (4.3), and the next tick reads the row of that
number. Where that source has the row count of the one before it and its cut
includes the place, the place keeps its number: a `Retune` leaves it as it
was, and two ticks of a two-row source are one period apart across such a
`Start`. Across a `Start` of a source of four rows under the whole table,
place 5 becomes place 1. A `Stop` leaves the place unchanged.

### 3.5 A deferred start

**3.5.1** A `Start` with `deferred` set is *deferred*. A deferred `Start`
on a timer running with its source connected is *pending* on that timer
from its frame (4.3) until a tick performs it (5.2) or an operation
discards it (4.3). One `Start` at most is pending on a timer.

**3.5.2** While a `Start` is pending on a timer, the last row of the cut of
the effect on that timer has no row after it (3.7.2): the source ends at
that row, a source that repeats included, and the tick that reads that row
performs the pending `Start` (5.2 step 4). An effect whose place is at row P
under a cut whose last row is L plays the rows P to L before the pending
`Start` begins.

**3.5.3** The tick performs the pending `Start` as a `Start` with `deferred`
clear (4.3), with its resets as 3.4 defines them: with `placeReset` clear,
the place, at L + 1, L the last row of the cut that ended, becomes L + 1
modulo the row count of the source it connects, and the first row of its cut
where that row is outside it (3.4.3). Under the whole table L + 1 is R, the
row count of the source that ended, which continues the count of the rows
the source before it played.

Note: the row the source a `Start` connects begins at, each source under the
whole table, with R the row count of the source before it, R' that of the
source it connects, and P the place a `Start` at its frame finds (3.4.3):

| R | R' | at its frame, `placeReset` clear | deferred, `placeReset` clear | either, `placeReset` set |
|---|---|---|---|---|
| 2 | 2 | P | 0 | 0 |
| 4 | 4 | P | 0 | 0 |
| 16 | 4 | P modulo 4 | 0 | 0 |
| 4 | 3 | P modulo 3 | 1 | 0 |
| 2 | 4 | P | 2 | 0 |
| 4 | 16 | P | 4 | 0 |

**3.5.4** A deferred `Start` on a timer that is stopped, or running with
its source disconnected (4.3), is performed at its frame as a `Start`
with `deferred` clear.

**3.5.5** At a frame, a `Stop` and a `Start` performed at it discard the
pending `Start`, and a deferred `Start` that becomes pending replaces it. A
`Retune` sets the rate of the effect the timer runs, and the `Start` stays
pending. A `Remap` sets the mapping of the effect the timer runs, and the
`Start` stays pending with the mapping of its row. A `Recut` sets the cut of
the effect the timer runs, and the `Start` stays pending with the cut of its
row until the tick that reads the last row of the new cut. The wrap (4.5)
and the end (4.6) leave it pending.

### 3.6 Transformations and mappings

**3.6.1 Lookup tables.** A *transformation* is a name and a list of E
entries, numbered from 0. A source value k is a *key*: the value written
is `entries[k]`. E is 1 to 256, each entry is 0 to 255, and keys are 0 to
E - 1, the *last key*. An entry count or entry outside these ranges is
an error of the structure (1.11). The name is for display, reports and
equality; playback uses the entries.

**3.6.2 Comparing parts.** The *identity* writes the source value
unchanged. A transformation whose entry k equals k remains a
transformation, with its key range limited by its entry count.

Two transformations are equal when their names and entries are equal,
including entry count and order. Equal transformations are listed once
per tune (1.9). Equal entries under different names are separate
transformations.

**3.6.3 Mapping a source row.** Transformation lookups are optional.
A *mapping* with no parts uses identity for every source value. Otherwise,
supply one part per value in target register order: identity for an
unchanged value or a transformation for a lookup. A different part count
is an error of the structure (1.11).

Each effect has a mapping. A `Start` installs its mapping when it connects
the source and target. A `Remap` replaces the running effect's mapping for
subsequent ticks, preserving its source, target, rate, cut and place. A
`Retune` and a `Recut` preserve the mapping. A deferred `Start` keeps its
mapping while pending; a `Remap` changes only the running effect (4.3).

**3.6.4 Applying a mapping at a tick.** Read the source row at the
place. For each value i:

1. Read value k and mapping part i, using the identity if the mapping
   has no parts.
2. Through the identity, output i is k. Through a transformation,
   output i is `entries[k]`.

Pass the mapped row to the target. Output i goes to register i, with
the write order and register rules of 3.1.1.

The lookup leaves source rows and transformation entries unchanged.
Frame register writes use their row values directly. Target rules also
apply to mapped writes: R7 preserves bits 7 and 6, and each R13 write
restarts the envelope, even if the output is unchanged (2.4, 2.5).

**3.6.5 Checking mappings.** Validate a `Start` against its source and
target. For a `Remap`, repeat the checks against every `Start` on the
same timer, including starts before and after the remap:

1. The mapping has no parts, or one part per target register.
2. Through the identity, each source value fits its target register.
3. Through a transformation, each source value is a key from 0 to the
   last key, and the entry at that key fits its target register.

These checks apply to every source row. Register limits constrain the
entries read by those rows; a key may exceed the register's maximum.
Failures are errors of the structure (1.11); 1.12 defines report order
and the checks omitted after another error.

Note: to scale fixed volume levels, define `Vn` as the transformation
whose entry k is k × n divided by 15, for keys 0 to 15. `V15` preserves
these levels; `V0` writes silence. Integer division discards the remainder.

| part | key 12 | key 15 |
|---|---|---|
| the identity | 12 | 15 |
| `V15` | 12 | 15 |
| `V10` | 8 | 10 |
| `V5` | 4 | 5 |
| `V0` | 0 | 0 |

**3.6.6 Uses (informative).**

- **Sample volume.** Run one sample source on `setR8`, `setR9` or
  `setR10` through different scaling transformations. Each effect can
  select a volume without a separate copy of the sample. For a sample
  with keys across a byte, a transformation can convert those keys to
  the register's fixed volume levels.
- **Fades.** Remap a running sample or waveform through successive volume
  transformations, one per frame. The sample continues from its place
  at the same tick rate; new ticks use the new levels. To control fixed
  volume, use entries with the envelope bit clear (2.3).
- **Volume independent of pitch.** On `setVoiceA`, map the fine and
  coarse tone values through the identity and the volume through `Vn`.
  A remap can fade the voice while its source continues to change pitch.
  The mapping has a separate part for each register; each lookup reads
  that part's source value alone.

### 3.7 Cuts

**3.7.1 A cut.** The rows of its source that an effect reads: a first row F
and a last row L, each 0 to R - 1 of a source of R rows, F at or below L;
outside that is an error of the structure (1.11). The cut of rows 0 to R - 1
is the *whole table*. Each `Start` has a cut, and a `Recut` sets the cut of
the effect a timer runs (4.3). Two effects run one source under two cuts
(3.2.3), and the equality of two sources (1.9) compares their tables alone.

**3.7.2 The row after a row under a cut.** Under the cut F to L of a source,
the row after row n is n + 1 for n below L. For n = L, where the source
repeats, it is the repeat row where F to L includes that row, and F where F
to L excludes it; where the source plays once, the source has ended. Under
the whole table this is 1.5, since the whole table includes every repeat
row.

**3.7.3 The place under a cut.** From step 4 of the first `Start` on a
timer, the place is a row of the cut of its effect: step 4 sets it there
(3.4.3), a `Recut` sets it to the first row of the new cut where it is
outside that cut (4.3), and a tick moves it to the row after it under the
cut (3.7.2).

**3.7.4 Uses (informative).**

- **A window over a wave table.** A source of 256 rows that repeats to row
  0, under the cut 64 to 95, reads those 32 rows over and over. A `Recut` a
  frame moves or narrows the window, and the effect keeps its rate, its
  mapping, and its place where the new window includes it.
- **A sample cut short.** A source that plays once, under the cut 0 to L,
  ends at row L, and a `Recut` sets where it ends while it plays.
- **An attack and a loop.** A cut that includes the repeat row of its source
  repeats to that row, so a source of an attack and a loop keeps both under
  a cut that ends before its last row.

`doc/tunes/cut.json` is a window over a wave table: a triangle of 32 rows
on `setR8`, under the cut 0 to 15 at row 0, moved two rows a frame by a
`Recut` to the cut 16 to 31, then narrowed to 16 to 23 and to 20 to 23.

### 3.8 Copy from frame

**3.8.1 The frame value.** The frame value of a register is the value the
last row that set it set, from step 4 of the frame of that row (4.4). A
row that leaves the register unset leaves its frame value as it is, and
the wrap (4.5) leaves every frame value as it is. Before the first row
that sets a register, its frame value is 0 (rule 7).

**3.8.2 The source.** *Copy from frame* is a source of one row that
repeats to row 0, the source of every `CopyFromFrame` (1.8). A
`CopyFromFrame` connects it to a target of any width, and its row has the
values a row of that target reads (3.2.1): at a tick, value i is the frame
value of register i of the target (3.1.1). On `setR8` a tick writes the
frame value of R8 to R8; on `setVoiceA` the frame values of R0, R1 and R8
to R0, R1 and R8. As for any source of one row:

- the place is row 0 at every tick, a place modulo 1 (3.4.3);
- its cut is rows 0 to 0, the whole table (3.7.1);
- each tick reads the last row of the cut, so a deferred `Start` pending on
  its timer is performed at the next tick (3.5.2);
- each value passes through its part of the mapping (3.6.4), and a `Remap`
  replaces the mapping (4.3).

**3.8.3 The frame.** A frame leaves a register a copy-from-frame effect
runs on at its step 4 to the ticks: the row sets the frame value of the
register (3.8.1), the register on the chip keeps its value, and the next
tick writes the frame value through the mapping (3.6.4). The register then
has the values the ticks write, through a transformation its entries. An
effect started on the row runs at step 4, so the first tick writes the
register; an effect stopped on the row has ended by step 4, so the frame
writes it (6.2 d). Rule 1(e) excludes such a register.

**3.8.4 The checks.** For the conditions of 1.11 and the order of 1.12,
copy from frame is a source of one row, R = 1, and its values are the
values the rows of the tune set on the registers of its target: value i
of row N where row N sets register i to a value 0 to its most, in row
order and within a row in the order of 3.1.1. A value outside 0 to its
most is an error of its row (1.11). Through the identity a value fits its
register, and through a transformation it is a key: 1.11 has two
conditions of copy from frame, a key above the last key of its
transformation and a key whose entry is above the most of the register.

**3.8.5 Uses (informative).** On `setR13` at the rate of a timer, copy
from frame writes R13 at each tick and restarts the envelope with the
shape the rows set (2.5): a sync buzzer whose shape follows the rows. On
`setBuzzer` the envelope period follows them as well. A source of one row
on `setR13` has the shape in its row, and a change of shape is a `Start`
of another source. In `doc/conformance/tunes/copy-from-frame.json` copy
from frame runs on `setR13`, then on `setBuzzer`, and on `setVoiceB`
through a mapping.

### 3.9 A sync

**3.9.1 The writes.** A *sync* of a voice restarts the square of its tone
generator at a period: the frame's at a frame (3.9.3) and the row's of a
source at a tick (3.9.4). A *step* of the tone generator is 8 cycles of
the chip's clock, 32 cycles of an 8 MHz 68000 on an ST, and n is the
voice's *sync length*, 1 to 8 (1.6). A sync is four writes, in this order
and at these times:

1. 0 to the coarse register of the voice's period: R1 for voice A, R3 for
   voice B, R5 for voice C.
2. Exactly one step after write 1, 0 to its fine register: R0, R2 or R4.
3. Exactly n steps after write 2, the fine value of the period.
4. Exactly one step after write 3, the coarse value of the period.

The writes and their times are the same where a value of the two
registers before the sync, or of the period, is 0.

**3.9.2 The steps (informative).** In Steven Tattersall's model of the
chip, reached by experiment ([YM2149
Sync-Square](https://clarets.org/steve/projects/2021_ym2149_sync_square.html),
2021), the tone generator of a voice steps once every 8 cycles of the
chip's clock: it adds 1 to a count, and where the count is at or above the
period, a period of 0 counting as 1, it flips the square and sets the
count to 0. Across the writes of 3.9.1 the period is:

| from | to | the period | steps |
|---|---|---|---|
| write 1 | write 2 | the fine value before the sync, below 256 | 1 |
| write 2 | write 3 | 0, so the square flips at each step | n |
| write 3 | write 4 | the new fine value | 1 |

Where the fine value before the sync is 0, the period is 0 from write 1,
and where the new fine value is 0, it is 0 to write 4.

Note: on an STE the CPU's clock is 4.0053 times the chip's: 32 cycles of
the CPU are 7.98 cycles of the chip there, and a sync timed as 32 cycles a
step misses a step now and then: about one in 95 by Tattersall's count.

**3.9.3 At a frame.** Before step 1 of 4.4, for each voice of the row's
`syncs` on whose period no effect runs at step 4 (6.2 d): a sync at the
row's sync length of the voice (1.6) to its frame values (3.8.1), the
row's value where the row sets the register, which step 1 then leaves
written, and the value it last had where the row leaves it. The syncs of
a frame begin in the order A, B, C, and a tick due during them occurs
after them. Where an effect runs on a register of the voice's period at
step 4, copy from frame included, the frame leaves the sync out: the sync
of that voice is the effect's, through a sync target (3.9.4), and the row
breaks rule 1(f).

**3.9.4 At a tick.** A sync target syncs its voice at each tick to the
source row's two values through the mapping (3.1.1), at the sync length of
the voice in a row of the tune (1.6): at the first tick after step 4 of a
frame, the row the frame before it read; at each later tick until the next
frame, the row the latest frame read. Where the frame's row starts the
effect (6.2 e), or a `Start` is pending on the timer at step 4 of the frame
(3.5), the first tick after it syncs at the sync length of the frame's row.
The writes of a sync at a tick are step 2 of the tick (5.2): a tick of
another timer due during them occurs after them. Every source of two
values a row runs on a sync target, copy from frame among them (3.8.2).

Note: under copy from frame the voice has the period of the row before
until the first tick after a frame, which writes the new one (3.8.3), so
that tick ends a stretch at the period of the row before.

Note: copy from frame on `syncToneA` at the rate of a timer restarts voice
A's square at each tick at the period the rows set: a hard sync, whose
restarts the timer sets and whose pitch between restarts the rows set.
Between two syncs the square flips at each count that reaches the period:
the rows' period after the sync before, the two fine values of 3.9.2 for
a step each, and 0 for the n steps between them, a flip a step. Where the
flips from one sync to the next are even in number, the square restarts
at the level it had after the sync before. The sync length of a row
applies to the ticks after the first until the next frame and to the
first tick after the next frame, each the end of a stretch at the row's
period. A writer sets on each row the sync length that restarts the
square at that level at those ticks, with a margin for the jitter of the
timer: the steps between two ticks are a fraction, and the whole steps
between two syncs are its floor or its ceiling.

**3.9.5 Uses (informative).** In `doc/conformance/tunes/sync.json` a row
syncs the three voices; copy from frame runs on `syncToneA`, and on
`syncToneC` through a mapping; a source of two rows runs on `syncToneB`;
and a row syncs voice A while Timer A runs on it, which rule 1(f) reports.
In `doc/conformance/tunes/sync-steps.json` copy from frame on `syncToneA`
syncs at 2, 8, 1 and 3 steps on rows 1 to 4; rows 0 and 4 sync voice B at
a frame at 3 and at 8 steps, row 4 voice C at 1; and row 5 has a sync
length of voice C without a sync. `doc/conformance/tunes/even-syncs.json`
is a file of version 11, whose even syncs read as sync lengths of 2
(json.md 2.4). A sync of 1 step is the technique of gwEm ([Alive
9](https://alive.atari.org/alive9/ymsamp.php), 2005) and of Tattersall,
and the hard sync of a sync target the sync square gwEm describes there.

---

## 4. What a frame does

**4.1 Before the first frame**, in order: claim the timers of the tune
(1.10) from the host, those alone; stop each, so that each is idle with no
`Start` pending on it (3.5); set the current row to row 0; leave every
register at the value the chip has, until a row sets it.

Note: a tune whose timers exclude Timer C can be hosted from the 200 Hz
clock of the operating system, which runs on Timer C.

**4.2 A frame**, one call of the player by the host, in order:

1. Where the tune has ended (4.6), report -1 and stop.
2. Read the current row.
3. For each timer the row acts on, in the order A, B, C, D, perform its
   operation (4.3).
4. Write the registers the row sets (4.4).
5. Where the row is the last row of a tune that plays once, the tune has
   ended (4.6); otherwise the current row is the row after it (1.5).
6. Report 0.

**4.3 The five operations.** An operation is performed on a timer of the
tune (1.10) alone: an operation on another timer leaves that timer and its
registers as they are. A `Retune`, a `Remap` or a `Recut` on such a timer
breaks rule 5, a `Stop` rule 6, except a `Stop` at the repeat row (rule
6(a)).

A `Stop`: stop the timer, and discard the `Start` pending on it (3.5). It
is idle, its source disconnected, and it raises its next tick after a later
`Start` or `Retune`; the place is unchanged (3.4.3).

A deferred `Start` on a timer running with its source connected: the `Start`
is pending on the timer (3.5), and replaces the `Start` pending there, where
one is. The operation leaves the timer, its source, its mapping, its cut,
its rate and its place as they were; a tick performs the `Start` (5.2 step
4).

Every other `Start`, and a pending `Start` a tick performs: discard the
`Start` pending on the timer, where one is, then in order:

1. If `timerReset` is set, stop the timer.
2. Connect the source to the target on this timer through the mapping and
   under the cut of the `Start` (3.6, 3.7). From this step, its ticks read
   this source under this cut and call this target through this mapping (5).
3. Write the prescaler and count. A stopped timer starts a whole period
   at the count; a running timer continues its period (3.3.5).
4. If `placeReset` is set, set the place to the first row of the cut;
   otherwise set it to its number modulo the row count of the source
   connected, and to the first row of the cut where that row is outside the
   cut (3.4.3).

A `Retune`: steps 1, 3 and 4 of a `Start`, and a `Start` pending on the
timer stays pending (3.5.5). On an idle timer step 3 starts the timer with
its source disconnected (rule 5), and what the player performs at its
ticks is left to a later version (5.4).

A `Remap`: replace the running effect's mapping. The next tick uses it;
source, target, timing, cut and place are unchanged. A pending `Start`
retains its separate mapping (3.5.5). On an idle timer, leave the timer
unchanged (rule 5).

A `Recut`: replace the running effect's cut; where the place is outside the
new cut, set it to the first row of the new cut. The next tick reads the row
at the place; source, target, timing and mapping are unchanged. A pending
`Start` retains its separate cut (3.5.5). On an idle timer, leave the timer
unchanged (rule 5).

Whether a tick occurs between two steps of an operation, or between an
operation and 4.4, is left to a later version (8.6).

**4.4 The registers.** Sync the voices the row syncs as 3.9.3 defines,
then write the registers the row sets in this order, in any order within
a step:

1. R0 to R5.
2. R6, R11 and R12.
3. R8, R9 and R10.
4. R7: bits 5 to 0 from the row, bits 7 and 6 unchanged (2.4).
5. R13, restarting the envelope even if its value is unchanged (2.5).

An absent register keeps its value. The player also writes registers
with running effects (rule 1), and leaves a register a copy-from-frame
effect runs on to the ticks (3.8.3). Each register the row sets has the
row's value as its frame value (3.8.1).

**4.5 The wrap.** Where a tune repeats, the row after its last row is its
repeat row (1.5), performed as any row. A timer running when the last row is
read runs on: the wrap leaves every timer, every place, every mapping, every
cut and every pending `Start` as they are. Rule 1(d) and rule 6(a) define
what the repeat row performs on it.

**4.6 The end.** A tune that plays once ends with its last row: the frame
that reads it reports 0, and every frame after leaves the row, the timers
and the registers as they are and reports -1; R rows are read in frames 0 to
R - 1, and frame R on reports -1. Each timer keeps the condition the last
row left it in, its mapping, its cut and a pending `Start` included; what a
host does with a timer running after the end is left to a later version
(8.7). A tune of R rows repeating to RR reads R rows in its first pass and R
- RR in every loop.

---

## 5. What a tick does

**5.1** A tick is one interrupt of a timer, raised once a period at the rate
of its prescaler and count (3.3.1). The player performs this section at a
tick alone.

**5.2 A tick** of a timer running an effect, in order:

1. Read the row of the source at the place (3.4.2).
2. Apply the effect's mapping to the row, then call the target with the
   mapped values. The target writes its registers as 3.1.1 defines
   (3.6.4).
3. Where the source has a row after the row read under the cut (3.7.2), the
   place is that row. While a `Start` is pending on the timer, the last row
   of the cut has no row after it (3.5.2).
4. Where the source has ended (3.7.2) and a `Start` is pending on the timer,
   perform it (4.3), the place at L + 1, L the last row of the cut, until
   its step 4.
   Where the source has ended and none is pending, stop the timer: the
   source has run out, the timer is idle, the place stays at L, and the
   register keeps the last value written (3.6.4) until a row of the tune
   sets it. A later `Start` on the timer with `placeReset` clear sets the
   place to that number modulo the row count of the source it connects, and
   into its cut (3.4.3).

**5.3** A tick reads its source alone; the tune's table is read at a frame
(4). A tick leaves the registers of the timer as they are: the timer counts
the next period from the count it has, and a count written by a `Retune`
applies as 3.3.5 defines.

**5.4** What the player performs at a tick of a timer with its source
disconnected (4.3) is left to a later version (8.6). Under rule 5 every tick
has a source connected.

---

## 6. The rules a writer satisfies

**6.1** A player assumes the seven rules and writes each value as the row
or the tick has it, so a tune that breaks one plays. A breach is a warning
(6.5); a breach of rule 4 leaves every write as it is (4(a)). A check reads
rules 1, 2, 3, 5, 6 and 7 (6.3) of a structure free of the conditions of
1.11; a structure with one is outside the check.

**6.2 Terms of the rules.**

- (a) An effect runs from step 2 of its `Start` (4.3) until the first of: a
      `Stop` on that timer; step 2 of a later `Start` on that timer, which
      replaces it; for a source that plays once, the tick that reads the
      last row of its cut (5.2 step 4), after which the source has run out.
      While a `Start` is pending on the timer, its source plays once
      (3.5.2). A `Remap` or a `Recut` on the timer leaves its effect running
      (4.3).
- (b) An effect runs on the register its target writes (3.1.1); its
      mapping changes the values written (3.6.4).
- (c) A timer is idle from the end of its effect (a) to its next `Start`.
- (d) An effect started on a row runs at step 4 of that row's frame (4.2);
      an effect stopped on a row has ended by step 4 of that frame. A
      pending `Start` begins its effect at a tick (5.2 step 4).
- (e) The row of a `Start` is the start row of the effect, the row of a
      `Stop` its stop row. The start row of the effect of a pending
      `Start` is the first row read after the tick that performs it.

**Rule 1: while an effect runs on a register its target writes, every
row leaves that register alone.**

- 1(a) A row breaks the rule where it sets a register on which an effect
  runs at step 4 of its frame: the start row where it sets the register (6.2
  d); the stop row is within the rule, since the effect has ended by step 4,
  and its write sets the register to the value the rows define after the
  effect.
- 1(b) Where the stop row of one effect on a register is the start row of
  another on it, the row leaves the register alone: it belongs to the second
  effect from that row on.
- 1(c) R13 is excluded: a row may set R13 while an effect runs on it, and
  each write restarts the envelope beside the ticks' restarts.
- 1(d) An effect running when the last row is read runs on through the
  repeat row (4.5), and the rows of the next pass are bound by 1(a). The
  repeat row performs an operation on such a timer: a `Stop` (rule 6(a)), a
  `Start`, or a `Retune` (rule 5). A `Remap` or a `Recut` at the repeat row
  leaves the effect running from the pass before, and the line of 1(d) is
  reported as where the row leaves the timer alone (6.3).
- 1(e) A register a copy-from-frame effect runs on is excluded: the rows
  set it, and copy from frame reads its frame value (3.8).
- 1(f) A row breaks the rule where it syncs a voice while an effect runs
  on a register of the voice's period at step 4 of its frame, copy from
  frame included: the frame leaves that sync out (3.9.3).

**Rule 2: where two timers write one register, the writer fixes the
order.**

- 2(a) Two effects run on one register where a `Start` on one timer has a
  target writing the register an effect of another timer runs on. Each tick
  writes the register at its rate; a player writes each tick's value at that
  tick, in the order the ticks arrive.
- 2(b) The writer settles what the two write and in what order. A check
  reports the row where the second starts (6.5); the order is the writer's.

**Rule 3: a `Start` on a timer that has run no `Start` sets `placeReset`.**

- 3(a) Before the first `Start` or `Retune` with `placeReset` set on a
  timer, its place is left to a later version (3.4.2), and so is the row
  the next tick reads after a `Start` with it clear.
- 3(b) After a `Start` in row order on the timer, its effect running or
  stopped, a `Start` may clear `placeReset`: the place keeps its number
  modulo the row count of the source this one starts (3.4.3), with any
  target, and a `Stop` between leaves the place unchanged.

**Rule 4: a `Start` on a stopped timer sets `timerReset`.**

- 4(a) A timer is stopped where idle (6.2 c). Where the rows leave open
  whether a source that plays once has run out at the start row, either
  value is correct: a stopped timer begins a whole period with the reset set
  or clear (3.4.1).
- 4(b) A check skips this rule (6.3).

**Rule 5: a `Retune`, a `Remap` or a `Recut` on a timer follows a `Start` on
it whose effect runs.**

- 5(a) A `Retune`, a `Remap` or a `Recut` on an idle timer breaks the rule:
  a timer before its first `Start`, after a `Stop`, or after its source has
  run out (5.2 step 4). A `Retune` starts the timer with its source
  disconnected, and a `Remap` or a `Recut` leaves the timer as it is (4.3).

**Rule 6: a `Stop` on a timer is the first `Stop` after a `Start` on it,
with one exception.**

- 6(a) The repeat row may perform a `Stop` on any timer, one the tune has
  yet to start included.
- 6(b) A `Stop` of a source that plays once and has run out (5.2 step 4) is
  within the rule.

Note: a writer stops every effect at the repeat row so that the wrap
resumes from a setting that row defines.

**Rule 7: a `Start` of copy from frame follows a row that sets each
register its target writes.**

- 7(a) The row that sets the register is the start row or a row before it.
  Before the first row that sets a register, its frame value is 0 (3.8.1),
  and a tick writes 0 through the mapping (3.6.4).

**6.3 The check.** A check reads rules 1, 2, 3, 5, 6 and 7 across the
rows in one pass with a model of each timer: the effect in the model,
present or absent; whether a `Start` has been performed on the timer; and
the pending `Start` (3.5), present or absent. The effect in the model is
its target, its source, its start row S, and the row U from which its
source has run out by the reckoning (6.4), unbounded for a source that
repeats until a deferred `Start` bounds it. At row N the effect in the
model runs where N is below U; otherwise the timer is idle. An effect is
*reckoned* where its U is bounded or step 1 set its S. A *run* is one
effect of the model from its start row to the row of the `Stop` or `Start`
that removes it, or to the last row; the rows of a run that set its
register are those step 3 counts. Closing a run reports its rule 1 line
(6.5) where step 3 counted one row or more.

Initial condition: the model of every timer is empty, its effect and its
pending `Start` absent, and no `Start` performed on it. For each row N
from 0 to the last:

1. For each timer, A to D, with a pending `Start` whose effect in the
   model has run out at N: remove the pending `Start`, and perform step
   2(a) on it from its rule 2 lines on, S = N.
2. For each timer the row acts on, in the order A, B, C, D:
   - (a) a `Start`: report the rule 3 line where the `Start` meets the
     condition of rule 3 (6.5); for a `Start` of copy from frame, report
     the rule 7 line of each register its target writes, in the order of
     3.1.1, where rows 0 to N leave it unset. Where it is deferred and the
     effect of the timer in the model runs at N, it is the pending `Start`
     of the timer now, in place of the one before it, the effect's U is
     bounded as 6.4 defines, and this step ends there. Otherwise: remove
     the pending `Start`; report a rule 2 line for each other timer, A to
     D, whose effect in the model runs at N on the register the target
     writes; where the timer has an effect in the model, close its run and
     remove it; put the effect of the `Start` in the model, S = N, U as 6.4
     defines; record that a `Start` has been performed on the timer.
   - (b) a `Retune`: report the rule 5 line where the effect of the
     timer is absent from the model or has run out at N.
   - (c) a `Stop`: remove the pending `Start`; report the rule 6 line
     where the effect of the timer is absent from the model and N is
     other than the repeat row; where it is present, close its run and
     remove it.
   - (d) a `Remap` or a `Recut`: report the rule 5 line of that operation
     where the effect of the timer is absent from the model or has run out
     at N. The effect in the model, its run and the pending `Start` are as
     they were.
3. For each timer with an effect in the model running at N, A to D, other
   than an effect of copy from frame (rule 1(e)): where the target's
   register is among R0 to R12 and row N sets it, count row N among the
   run's rows. Then for each voice of the row's `syncs`, A to C, and each
   timer, A to D, whose effect in the model runs at N on a register of the
   voice's period, copy from frame included: report the rule 1(f) line.

After the last row:

4. For each timer with an effect in the model, A to D: close its run; the
   effect stays for step 5, and a pending `Start` stays pending.
5. Where the tune repeats: for each timer with an effect in the model
   running at the last row, A to D, other than an effect of copy from frame
   (rule 1(e)), report the rule 1(d) line where the repeat row leaves the
   timer alone or performs a `Remap` or a `Recut` on it.

The report is the lines in the order reported.

**6.4 The reckoning.** A source that plays once has a start row alone; a
check reckons its end row from the rate. For a `Start` at row S under a cut
of W rows, its last row less its first plus 1, divisor D, count C, tune rate
H: U = S + F, F = (W × D × counted(C) × H + 153,600 + 2,457,599) divided by
2,457,600, the arithmetic exact. The reckoning reads the prescaler, the
count and the cut of the `Start`; a `Retune` and a `Recut` leave U
unchanged. A cut of four rows at a divisor of 4, a count of 100 and 50 Hz
yields F = 1. A warning that rests on the reckoning ends with the suffix
`, which rests on how long a source that plays once runs, reckoned from its
rate`.

A deferred `Start` at row N on a timer whose effect in the model runs at N
bounds that effect: its source plays once from N (3.5.2), and U becomes the
lesser of U and N + F, F reckoned as above for the W rows of the cut of the
effect's `Start`, at its prescaler and count.

Note: 153,600 is a sixteenth of 2,457,600, allowed for a start inside its
frame; the division rounds a partial frame up. The place at row N is a row
of the cut, so the source plays W rows at most from it, and the reckoning
reads W.

**6.5 What a check reports.** Each warning is one line of the table. X
and Y are timer letters, V a voice's letter; N the row of the operation, S
the start row, F and L the first and last row of a run that set the
register, K how many did, T the repeat row. Where the third column names
the suffix, the line ends with the suffix of 6.4 under the condition
named.

| rule | condition | reported as |
|---|---|---|
| 1 | one row of the run, row F, sets the register while the effect runs (6.3), alone among the rows of the run | `row F: Timer X runs on Rn, and this row sets it`, with the suffix where the effect is reckoned |
| 1 | several rows of the run set the register while the effect runs (6.3), the first at row F, the last at row L, K of them | `rows F to L: Timer X runs on Rn from row S, and K of them set it`, with the suffix where the effect is reckoned |
| 1(f) | row N syncs voice V, and the effect of Timer X runs at step 4 on Rn, the first register of the voice's period its target writes | `row N: Timer X runs on Rn, and this row syncs voice V: the frame leaves the sync to the effect`, with the suffix where the effect is reckoned |
| 1(d) | the tune repeats to row T, the effect of Timer X runs when the last row has been read, and row T leaves Timer X alone or performs a `Remap` or a `Recut` on it | `the tune repeats to row T, and Timer X runs on Rn when its last row has played: the wrap resumes with the timer running from the pass before`, with the suffix where the effect is reckoned |
| 2 | a `Start` on Timer X at row N has a target writing Rn, on which the effect of Timer Y runs | `row N: Timer X starts on Rn, where Timer Y runs: rule 2 leaves the order of two timers writing one register to the writer`, with the suffix where the effect of Timer Y is reckoned or step 1 of 6.3 performs the `Start` |
| 3 | a `Start` at row N with `placeReset` clear on a timer before any `Start` on it | `row N: Timer X starts a source without the place's reset, and this timer has run none: the place is where the player left it` |
| 5 | a `Retune` at row N on an idle timer | `row N: Timer X retunes an effect that is idle: a rate written to a timer with no source on it starts that timer with no source to run`, with the suffix where the effect in the model for the timer, one a `Stop` has yet to remove, has a bounded U and N is at or above U (6.3), run out by the reckoning |
| 5 | a `Remap` at row N on an idle timer | `row N: Timer X remaps an effect that is idle: a mapping is set on a running effect, and this row leaves the timer as it is`, with the suffix where the effect in the model for the timer, one a `Stop` has yet to remove, has a bounded U and N is at or above U (6.3), run out by the reckoning |
| 5 | a `Recut` at row N on an idle timer | `row N: Timer X recuts an effect that is idle: a cut is set on a running effect, and this row leaves the timer as it is`, with the suffix where the effect in the model for the timer, one a `Stop` has yet to remove, has a bounded U and N is at or above U (6.3), run out by the reckoning |
| 6 | a `Stop` at row N on a timer whose effect is absent from the model, N other than the repeat row | `row N: Timer X stops an effect this timer has not started` |
| 7 | a `Start` of copy from frame on Timer X at row N on setRm, which writes Rn, and rows 0 to N leave Rn unset | `row N: Timer X starts copy from frame on setRm before a row sets Rn: its frame value is 0` |

Rules 1, 2 and 5 are read with the effect running until U (6.3); rule
6 with the effect in the model until a `Stop` or `Start` removes it, so a
`Stop` of a source run out by the reckoning is within rule 6 (6(b)). The
rows of one run are one line, reported where the run closes, so after lines
of later rows; the start row of a replacing `Start` counts for the new
effect's run (1(a)). Of a multi, a check reports each tune's lines in number
order, prefixed `tune N: ` where the multi has more than one tune. Note: an
error of a multi of one tune has the prefix (1.12); a warning of it is
unprefixed.

**6.6 The example.** `doc/tunes/warnings.json`: eight rows at 50 Hz
repeating to row 1; source 1 `square 15`, values 15 and 0 repeating to
row 0, and source 2 `square 12`, values 12 and 0 repeating to row 0. Row
0 sets R7 to 56 and starts source 1 on Timer A on `setR8`, divisor 200,
count 100, both resets set; rows 1 to 4 set R8 to 12; row 2 starts
source 2 on Timer B on `setR8`, divisor 200, count 120, both resets set;
row 6 performs a `Stop` on Timer C. The check reports, in this order:

    row 2: Timer B starts on R8, where Timer A runs: rule 2 leaves the order of two timers writing one register to the writer
    row 6: Timer C stops an effect this timer has not started
    rows 1 to 4: Timer A runs on R8 from row 0, and 4 of them set it
    rows 2 to 4: Timer B runs on R8 from row 2, and 3 of them set it
    the tune repeats to row 1, and Timer A runs on R8 when its last row has played: the wrap resumes with the timer running from the pass before
    the tune repeats to row 1, and Timer B runs on R8 when its last row has played: the wrap resumes with the timer running from the pass before

The rule 1 lines follow the rule 2 and rule 6 lines since their runs close
after the last row, Timer A before Timer B; the start row 2 of Timer B
counts among its rows; row 1 leaves both timers alone, so both rule 1(d)
lines are reported.

---

## 7. What a recorder reports

**7.1** A recorder performs the frames of section 4 of one tune with every
register a row sets and every timer operation recorded in place of the
chip, and produces the record: the figures of the tune once, then one
entry a frame in order. The ticks and their writes are outside the record.
Of a multi, it performs the tune the host selects (1.2).

**7.2 The record.** Lines of JSON, one entry a line: spaces inside strings
alone, integers in decimal, the keys of each object in the order this
section lists them, `true`, `false` and `null` as JSON defines them, a line
feed, byte 10, ending every line. The first line is the figures of the tune
(7.3), each line after it one frame (7.4).

**7.3 The first line** is
`{"rate":H,"timers":[...],"sources":[...],"transformations":[...]}`:
`rate` the rate of the tune H; `timers` the timers of the tune (1.10),
each as its letter `"A"` to `"D"`, in that order, `[]` where empty;
`sources` the sources of the tune (1.9) in number order, each
`{"rows":[...],"repeat":RR}` with `rows` one flat list of its values in
row order, the values of row 0 then those of row 1 and so on, a row being
one, two or three values as the target that runs the source reads (3.1.1):
a source of the two rows (200, 1) and (100, 2) has `"rows":[200,1,100,2]`;
and RR its repeat row, or `null` for a source that plays once; `[]` where
the tune has no sources; `transformations` the transformations of the tune
(1.9) in number order, each the list of its entries in key order, `[]`
where the tune has none.

**7.4 A frame's line.** For a frame that reads a row,
`{"result":0,"w":{...},"e":{...}}`, with `"s":[...]` after `w` where the
row syncs a voice and `"lengths":{...}` after `w` and `s` where the sync
length of a voice at the row is other than 1: `w` the registers the row
sets (4.4), keyed by number as text, `"0"` to `"13"`, in ascending numeric
order, `"2"` before `"10"`, each with the row's value, R7's its six bits
(2.4), `{}` where empty, a register an effect runs on included (6.1), and
a register the frame leaves to the ticks of copy from frame, whose frame
value the row sets (3.8.3); `s` the voices the row syncs (1.6), each as
its letter `"A"` to `"C"`, in that order, a voice whose sync the frame
leaves out among them (3.9.3); `lengths` each voice of a sync length other
than 1 at the row (1.6), keyed by its letter, in that order, with its sync
length; `e` the timers the row acts on, as the row has them, whether or
not the timer is one of the tune (4.3), keyed by letter `"A"` to `"D"`, in
that order, each with the operation as one object of the table, `{}` where
empty. For the first frame after the end (4.6), `{"result":-1}`.

Note: a deferred `Start` begins its effect at a tick (5.2), so whether copy
from frame runs at step 4 of a frame can rest on the ticks; `w` reads the
row, so a record rests on the rows alone.

| operation | object |
|---|---|
| a `Start` | `{"start":{"target":"setRn","source":S,"prescaler":P,"count":C,"timerReset":B,"placeReset":B,"deferred":B,"mapping":M,"first":F,"last":L}}` |
| a `Retune` | `{"retune":{"prescaler":P,"count":C,"timerReset":B,"placeReset":B}}` |
| a `Stop` | `{"stop":{}}` |
| a `Remap` | `{"remap":{"mapping":M}}` |
| a `Recut` | `{"recut":{"first":F,"last":L}}` |

`target` is the name of the target (3.1.2), `source` the number S of the
source in first-start order (1.9), which the numbering of a file matches
where a writer emitted it (json.md 3.4), and 0 for copy from frame (3.8),
`prescaler` the divisor P (3.3.2), `count` the count C (3.3.3),
`timerReset` and `placeReset` the resets (3.4), `deferred` the deferral
(3.5), each B `true` or `false`, `mapping` the mapping M (3.6): 0 for a
mapping whose every part is the identity, the mapping of no parts included
(3.6.3), and otherwise an array of a number a part in part order, 0 the
identity and n transformation n (1.9); and `first` and `last` the first
row F and the last row L of the cut (3.7). A deferred `Start` is recorded
at the frame of its row, as every operation is; the tick that performs it
is outside the record (7.1).

**7.5 The length.** The first line and the lines of as many frames as the
host requires, from frame 0, except that the record of a tune that plays
once ends with its `{"result":-1}` line, produced for the first frame after
the end, where the record ends. Where the host omits the count, it is one
pass and one loop of a tune that repeats and one pass and the frame after
it of a tune that plays once. One pass and one loop of a tune of R rows
repeating to RR is R + (R - RR) frames; one pass of a tune of R rows that
plays once and the frame after it is R + 1, the whole record for any number
of frames at or above R + 1. Two recorders of one tune over one number of
frames produce one record, line for line.

**7.6 The example.** One pass of `doc/tunes/example.json` (json.md 10,
csv.md 8): four rows at 50 Hz repeating to row 0, one source `square
13`, started on Timer A on `setR8` at row 0, retuned at row 1, stopped
at row 3, whose stop row sets R8 to 12 within rule 1(a):

    {"rate":50,"timers":["A"],"sources":[{"rows":[13,0],"repeat":0}],"transformations":[]}
    {"result":0,"w":{"0":163,"1":2,"2":238,"7":56},"e":{"A":{"start":{"target":"setR8","source":1,"prescaler":50,"count":60,"timerReset":true,"placeReset":true,"deferred":false,"mapping":0,"first":0,"last":1}}}}
    {"result":0,"w":{"0":142,"1":12,"7":49},"e":{"A":{"retune":{"prescaler":50,"count":61,"timerReset":false,"placeReset":false}}}}
    {"result":0,"w":{"0":251,"1":4},"e":{}}
    {"result":0,"w":{"0":89,"1":2,"7":56,"8":12},"e":{"A":{"stop":{}}}}

Frame 4 reads row 0 again, so its line is the second line; one pass and
one loop is eight frames (7.5).

---

## 8. What a later version defines

A player of this version is unconstrained in each item below.

**8.1** What a reader does with a file whose version is other than 3 to 12,
beyond reporting the error of the form (json.md 8.1, csv.md 5.1).

**8.2** Targets past the twenty-eight: a procedure writing a register of
the MC68901, one writing other registers of the YM2149 together, a voice's
period and its volume after a sync among them, and one reading a row wider
than a byte, numbered 28 to 127 (3.1.2).

**8.3** Sources of more than three values a row (3.2.1).

**8.4** The place of a timer before the first `Start` or `Retune` with
`placeReset` set on it (3.4.2).

**8.5** How the steps of 4.3 are written to the registers of the MC68901,
the order of the count and the prescaler within one step, and what
claiming a timer from the host comprises (3.3.6, 4.1).

**8.6** What a player performs at a tick of a timer with its source
disconnected (5.4), and whether a tick occurs between two steps of an
operation or between an operation and the register writes of a frame (4.3).

**8.7** What a host does with a timer running after the end of a tune
that plays once (4.6), and what a player performs between two tunes of
a multi (1.2).

**8.8** How a player obtains bits 7 and 6 of R7 for a write of R7 (2.4).
