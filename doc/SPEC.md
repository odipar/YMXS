# The YMXS format

Version 3 of the tune data structure, and what a player does with it on
the YM2149 and the MC68901 of an Atari ST: the structure (1), what each
value reaches on the two chips (2, 3), the procedure of a frame (4) and
of a tick (5), the rules a writer satisfies and what a check reports (6),
the record a recorder produces (7), and what a later version defines (8).

A form is an encoding of the structure: [json.md](json.md),
[csv.md](csv.md), or a player's binary layout, defined by that player.
A file of a form has the version, 3, beside the structure; a reader
reads it first, and another version is an error of the form.

**Conventions.** A clause is cited by number, 4.3 or 3.2.1; a rule of
section 6 as rule 1, a condition of one as rule 1(a). `Note:` begins an
informative sentence. A range includes both ends. Integer division
discards the remainder. In a reported text, `Rn`, `setRn` and `setRm`
are a register and two targets, and each other capital letter is a
decimal figure defined beside the text.

**Roles.** A player performs sections 4 and 5 on the two chips. A
recorder performs section 4 on no chip and produces the record of
section 7. A check reports the errors of 1.11 in the order of 1.12 and
the warnings of 6.5 in the order of 6.3. A writer produces a structure
with no error of 1.11 that satisfies the rules of section 6. A reader
reads a file of a form into the structure, and an emitter emits one
from it. The host selects a tune (1.2), calls the player once a frame,
and owns bits 7 and 6 of R7 and every timer outside the tune's (1.10).

**Terms.** A *frame* is one call of the player (4.2); a *tick* one
interrupt of a timer (5.1); a *row* one entry of a table (1.6). The rate
of a tune is in frames a second (1.3), the rate of an effect in ticks a
second (3.3.1). An *error of the structure* is a condition of 1.11, an
*error of the form* a condition of json.md 8.1 or csv.md 5.1, and a
*warning* a breach of rule 1, 2, 3, 5 or 6 (6.5). A *pass* is the frames
from the one that reads row 0 to the one that reads the last row; a
*loop* the frames from the one that reads the repeat row to the one that
reads the last row (4.5). Section 6.2 defines *runs*, *idle* and *run
out*; 6.3 defines a *run*.

---

## 1. The tune data structure

**1.1** The block is the structure; every declaration is normative, and
its names are the names of this document. Read as Java: a `record` is a
tuple of named fields in declared order; a `sealed interface` with
`permits` is a choice among the records listed; an `enum` is a choice
among its constants, numbered from 0 in declared order; `List<T>` is a
sequence numbered from 0; `Map<K, V>` is a set of pairs with no two under
one key; `OptionalInt` is an integer, present or absent; `int` and
`Integer` are 32-bit signed integers; `boolean` is true or false;
`String` is text, the empty text included.

```java
package org.ymxs;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public interface YMXS {

    record Multi(List<Tune> tunes) { }

    record Tune(String title, String composer, String writer, int rate,
                Table<Row> table) { }

    record Table<T>(List<T> rows, OptionalInt repeat) { }

    record Row(Map<Register, Integer> registers, Map<Timer, Effect> effects) { }

    sealed interface Effect permits Start, Retune, Stop { }

    record Start(Target target, Source source, Prescaler prescaler, int count,
                 boolean timerReset, boolean placeReset) implements Effect { }

    record Retune(Prescaler prescaler, int count, boolean timerReset,
                  boolean placeReset) implements Effect { }

    record Stop() implements Effect { }

    sealed interface Target permits SetRegister { }

    record SetRegister(Register register) implements Target { }

    sealed interface Source permits Single { }

    record Single(String name, Table<Integer> table) implements Source { }

    enum Register {
        R0, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13
    }

    enum Timer { A, B, C, D }

    enum Prescaler { BY_4, BY_10, BY_16, BY_50, BY_64, BY_100, BY_200 }
}
```

**1.2 A multi.** A list of tunes, numbered from 1 in list order, one at
least (1.11). A host selects a tune by number. A player does not play a
multi with an error of the structure in any tune.

**1.3 A tune.** A title, a composer, a writer, a rate and a table of
rows. The texts reach no chip. The rate is the frames a second at which
the host calls the player, 1 to 2,147,483,647; below 1 is an error of the
structure (1.11).

**1.4 A table.** A list of rows, numbered from 0, one at least (1.11),
and a repeat row, present or absent. A present repeat row is 0 to R - 1
for R rows; outside that it is an error of the structure (1.11). A table
whose repeat row is absent plays once. A tune's table and a source's are
one kind: the player reads a tune's rows one a frame (4) and a source's
one a tick (5).

**1.5 The row after a row.** In a table of R rows, the row after row n
is n + 1 for n below R - 1; for n = R - 1, the repeat row where present,
and where absent the table has ended.

**1.6 A row.** `registers`, the registers the row sets with the value
each is set to, and `effects`, the operation it performs on each timer
it acts on. A register absent from `registers` keeps its value; a timer
absent from `effects` has no operation. A row sets each register at most
once and performs at most one operation on each timer.

**1.7 An effect and the three operations.** The effect of a timer is
the source connected to a target on it, at the rate of its prescaler and
count (3.3). Four timers, each running one effect at most; the timer
identifies the effect. An operation is a value of the type `Effect`
(1.1): a `Start` connects `source` to `target` on the timer and sets
`prescaler` and `count`; a `Retune` sets `prescaler` and `count` of the
effect the timer runs; a `Stop` stops the timer. A `Start` and a
`Retune` have the resets `timerReset` and `placeReset` (3.4). A `Start`
on a timer running an effect ends it and begins another. Section 4
defines each operation as steps.

**1.8 Targets and sources.** This version defines one kind of target,
`SetRegister`, one a register, named `setR0` to `setR13` (3.1), and one
kind of source, `Single`: a text `name` and a `table` of one integer a
row (3.2).

**1.9 The sources of a tune.** The list this procedure produces from an
empty list: read the rows from row 0 to the last, within a row the
timers A, B, C, D; where the operation is a `Start` whose source is not
in the list, append it. The order is the *first-start order*; the
sources are numbered from 1 in it, and a source is selected by number.
Two sources are equal where their names are equal, their values equal in
number and order, and their repeat rows both absent or both present and
equal; two `Start`s of equal sources start one source of the tune, and
equal values under two names are two sources.

**1.10 The timers of a tune.** The timers on which some row performs a
`Start`, in the order A, B, C, D; a timer with only a `Retune` or a
`Stop` is outside them.

**1.11 Errors of the structure.** A player does not play a structure
with a condition below, and a check reports each present as its line,
in the order of 1.12. H is a rate in frames a second, R a row count, X a
repeat row, V a value, M the most of a register (2.1), C a count, T a
rate in ticks a second, W the values a row of a source, U the values a
target reads, N a row number.

| condition | reported as |
|---|---|
| the multi has no tune | `a multi of no tunes: a host plays one` |
| the rate of a tune is below 1 | `a rate of H: a player is called at least once a second` |
| the table of a tune has no row | `the tune has no rows: a clock reads one` |
| the repeat row of a tune of R rows is present and outside 0 to R - 1 | `the tune has R rows and repeats to row X` |
| a row sets a register to a value outside 0 to its most | `Rn is 0 to M, and this row sets it to V` |
| a `Start` or a `Retune` has a count outside 0 to 255 | `a count of C: a timer's data register is a byte, 0 to 255, and 0 counts 256` |
| a `Start` or a `Retune` with a count of 0 to 255 has a rate above 125,000 (3.3.4) | `a rate of T ticks a second: a 68000 at 8 MHz enters an interrupt and leaves it in 64 cycles, so 125000 a second is every cycle it has` |
| the table of the source of a `Start` has no row | `the source has no rows: a clock reads one` |
| the repeat row of the source of a `Start`, of R rows, is present and outside 0 to R - 1 | `the source has R rows and repeats to row X` |
| row N of the source of a `Start` is a value below 0 | `row N is V, and a register is 0 upward` |
| the rows of the source of a `Start` have a number of values other than the number its target reads | `a source of W values a row on setRn, which reads U` |
| row N of the source of a `Start` is a value above the most of the register its target writes | `a source on setRn whose row N is V, and the target is 0 to M` |

Note: every source and target of this version has one value a row (1.8),
so the line `a source of W values a row on setRn, which reads U` arises
in a later version alone (8.2, 8.3).

**1.12 The order of the report.**

1. The line of the multi.
2. For each tune in number order, each line prefixed `tune N: `, N the
   tune number, where the check reads a multi, and unprefixed where it
   reads one tune:
   1. the line of the rate;
   2. the line for no rows, then the line for the repeat row, each where
      present;
   3. for each row in order, each line prefixed `row N: `, N the row
      number: the lines of the registers, R0 to R13; then for each timer
      the row acts on, A to D, each line prefixed `Timer X: `, X its
      letter: the line of the count; the line of the rate, omitted where
      the count is outside 0 to 255; for a `Start`, the line for no rows
      and the line for the repeat row of the source, each where present,
      then the lines of its values below 0 in row order, then the line
      for the values a row, and, where that line is absent, the lines of
      its values above the most in row order. A `Stop` yields no line.

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

**2.2 A period over two registers.** A voice's tone period is twelve
bits, the low byte in R0, R2 or R4 and the high four bits in R1, R3 or
R5; the envelope period is sixteen, the low byte in R11 and the high in
R12. A row sets either register of a pair without the other (1.6).

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
sets it, the value R13 already has included; every tick of an effect
whose target is `setR13` writes it (3.1.1).

---

## 3. The effects

### 3.1 The targets

**3.1.1** A target is a procedure the player calls at a tick with one
row of a source; it writes the row to a register. This version defines
`setR0` to `setR13`: `setRn` writes the row's one value to `Rn`, and
`setR7` writes bits 5 to 0 and leaves 7 and 6 (2.4). Every target of
this version reads a row of one value.

**3.1.2** A target is numbered by its register, 0 to 13, and named `set`
followed by the register's name; a later version numbers further targets
14 to 127 (8.2).

### 3.2 The sources

**3.2.1** A source is a name and a table (1.4) of one value a row. The
name is text, reaches no chip and distinguishes sources (1.9); a player
reads the table alone.

**3.2.2** Every value of a source is 0 to the most of the register
written by the target of every effect that runs it; outside that is an
error of the structure (1.11).

**3.2.3** Two effects run one source where two rows start it on two
timers; it is one source of the tune (1.9). Each timer has a separate
place (3.4.2), and an operation on one timer moves that place alone.

### 3.3 The rate

**3.3.1** The clock of the timers is 2,457,600 a second. A prescaler
divides it by its divisor; a timer counts the divided clock down from its
count through zero and raises a tick at each zero. The rate of an effect
is 2,457,600 divided by the divisor times counted(C), C the count and
counted as 3.3.3 defines.

**3.3.2** The seven prescalers, each with its divisor and the code that
selects it in the control register of a timer of the MC68901; code 0
selects no prescaler and stops the timer.

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
is part at each prescaler. With `timerReset` set the timer is stopped
before the write (3.4.1), so both apply from it.

**3.3.6** The mapping of the steps of 4.3 to the registers of the
MC68901, the order of the count and the prescaler within one step, and
how a player claims a timer from the host are not defined by this
version (8.5).

### 3.4 The two resets

**3.4.1 `timerReset`.** Set: the player stops the timer before writing
the count and the prescaler (4.3); the timer starts at that write and
begins a whole period at the count, the part counted discarded. Clear:
a running timer continues its period, and the writes apply as 3.3.5
defines. On a stopped timer the outcome is the same either way.

**3.4.2 The place.** Each timer has a place: the number of the row of
its source that the next tick reads. A tick moves it (5.2) and a reset
of it (3.4.3); no other step does. Before the first `Start` or `Retune`
with `placeReset` set on a timer, its place is not defined (8.4).

**3.4.3 `placeReset`.** Set: the player sets the place to 0 (4.3), and
the next tick reads the first row of the source. Clear: the place keeps
its row number through a `Retune` and a `Start`, and the next tick reads
the row of that number in the source then connected; two ticks of a
two-row source are one period apart across such a `Start`. A `Stop`
leaves the place unchanged. Where the place is not the number of a row
of the source connected, what the next tick reads is not defined (8.4);
a `Start` under rule 3 leaves the place at a row of the source it
starts.

---

## 4. What a frame does

**4.1 Before the first frame**, in order: claim the timers of the tune
(1.10) from the host, and no other; stop each, so that each is idle; set
the current row to row 0; write no register, so that each keeps the
value the chip has until a row sets it.

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

**4.3 The three operations.** An operation is performed on a timer of
the tune (1.10) alone: on another timer the player performs no step and
writes no register of that timer. A `Retune` on such a timer breaks
rule 5, a `Stop` rule 6, except a `Stop` at the repeat row (rule 6(a)).

A `Stop`: stop the timer. It is idle, no source is connected, and it
raises no tick until a later `Start` or `Retune`; the place is unchanged
(3.4.3).

A `Start`: 1. Where `timerReset` is set, stop the timer. 2. Connect the
source to the target on this timer; from this step a tick of this timer
reads this source and calls this target (5). 3. Write the prescaler and
the count: where the timer is stopped, by step 1 or before the row, this
write starts it and it begins a whole period at the count; where it
runs, its period continues (3.3.5). 4. Where `placeReset` is set, set
the place to 0.

A `Retune`: steps 1, 3 and 4 of a `Start`. On an idle timer step 3
starts the timer with no source connected (rule 5), and what the player
performs at its ticks is not defined (5.4).

Whether a tick occurs between two steps of an operation, or between an
operation and 4.4, is not defined (8.6).

**4.4 The registers.** The registers the row sets are written in five
steps, in an order within a step this version does not define: R0 to
R5; R6, R11 and R12; R8, R9 and R10; R7, bits 5 to 0 from the row and
bits 7 and 6 as they were (2.4); R13, which restarts the envelope (2.5).
A register the row does not set is not written. The player performs no
test against the effects: a register an effect runs on is written as
any other, and a tune satisfying rule 1 sets no such register.

**4.5 The wrap.** Where a tune repeats, the row after its last row is
its repeat row (1.5), performed as any row. A timer running when the
last row is read runs on: the wrap stops no timer and moves no place.
Rule 1(d) and rule 6(a) define what the repeat row performs on it.

**4.6 The end.** A tune that plays once ends with its last row: the
frame that reads it reports 0, and every frame after reads no row,
performs no operation, writes no register and reports -1; R rows are
read in frames 0 to R - 1, and frame R on reports -1. Each timer keeps
the condition the last row left it in; what a host does with a timer
running after the end is not defined (8.7). A tune of R rows repeating
to RR reads R rows in its first pass and R - RR in every loop.

---

## 5. What a tick does

**5.1** A tick is one interrupt of a timer, raised once a period at the
rate of its prescaler and count (3.3.1). Between ticks the player
performs no step of this section.

**5.2 A tick** of a timer running an effect, in order:

1. Read the row of the source at the place (3.4.2).
2. Call the target with it; the target writes the value to its register
   (3.1.1).
3. Where the row is not the last of the source, the place is its number
   plus 1.
4. Where the row is the last and the source repeats, the place is the
   source's repeat row.
5. Where the row is the last and the source plays once, stop the timer:
   the source has run out, the timer is idle, the place stays at the
   last row's number, and the register keeps the last value until a row
   of the tune sets it. A later `Start` on the timer with `placeReset`
   clear, under rule 3, connects a source of that row count, and the
   next tick reads its last row.

**5.3** A tick reads its source alone; the tune's table is read at a
frame (4). A tick writes no register of the timer: the timer counts the
next period from the count it has, and a count written by a `Retune`
applies as 3.3.5 defines.

**5.4** What the player performs at a tick of a timer with no source
connected (4.3) is not defined (8.6). A tune satisfying rule 5 has no
such tick.

---

## 6. The rules a writer satisfies

**6.1** A player assumes the six rules: it performs no test of them and
writes each value as the row or the tick has it, so a tune that breaks
one plays. A breach is a warning (6.5); a breach of rule 4 changes no
write (4(a)). A check reads rules 1, 2, 3, 5 and 6 (6.3) of a structure
with no error of 1.11, and no rule of one with an error.

**6.2 Terms of the rules.**

- (a) An effect runs from step 2 of its `Start` (4.3) until the first
  of: a `Stop` on that timer; a later `Start` on that timer, which
  replaces it; for a source that plays once, the tick that reads its
  last row (5.2 step 5), after which the source has run out.
- (b) An effect runs on the register its target writes.
- (c) A timer on which no effect runs is idle.
- (d) An effect started on a row runs at step 4 of that row's frame
  (4.2); an effect stopped on a row does not.
- (e) The row of a `Start` is the start row of the effect, the row of a
  `Stop` its stop row.

**Rule 1: while an effect runs on a register, every row leaves that
register alone.**

- 1(a) A row breaks the rule where it sets a register on which an
  effect runs at step 4 of its frame: the start row where it sets the
  register (6.2 d); the stop row does not, since the effect has stopped
  by step 4, and its write sets the register to the value the rows
  define after the effect.
- 1(b) Where the stop row of one effect on a register is the start row
  of another on it, the row leaves the register alone: it belongs to
  the second effect from that row on.
- 1(c) R13 is excluded: a row may set R13 while an effect runs on it,
  and each write restarts the envelope beside the ticks' restarts.
- 1(d) An effect running when the last row is read runs on through the
  repeat row (4.5), and the rows of the next pass are bound by 1(a). The
  repeat row performs an operation on such a timer: a `Stop` (rule
  6(a)), a `Start`, or a `Retune` (rule 5).

**Rule 2: where two timers write one register, the writer fixes the
order.**

- 2(a) Two effects run on one register where a `Start` on one timer has
  a target writing the register an effect of another timer runs on. Each
  tick writes the register at its rate; a player writes each tick's
  value at that tick and imposes no order.
- 2(b) The writer settles what the two write and in what order. A check
  reports the row where the second starts (6.5) and no order.

**Rule 3: a `Start` sets `placeReset`, with one exception.**

- 3(a) A `Start` may clear `placeReset` where a `Start` has been
  performed on the timer before, the last had the same target, and the
  source of the last has the row count of the source this one starts;
  the place then keeps its number (3.4.3).
- 3(b) The last `Start` is the last in row order on the timer, its
  effect running or stopped; a `Stop` between leaves the place unchanged
  (3.4.3), so the exception applies across a `Stop`.
- 3(c) A `Start` with `placeReset` clear outside the exception leaves
  the place at a number no row has set (3.4.2), or one the source may
  have no row for, and what the next tick reads is not defined (3.4.3).

**Rule 4: a `Start` on a stopped timer sets `timerReset`.**

- 4(a) A timer is stopped where idle (6.2 c). Where the rows do not fix
  whether a source that plays once has run out at the start row, either
  value is correct: a stopped timer begins a whole period with the reset
  or without it (3.4.1).
- 4(b) No check reads this rule.

**Rule 5: a `Retune` on a timer follows a `Start` on it whose effect
runs.**

- 5(a) A `Retune` on an idle timer breaks the rule: no `Start`, a `Stop`
  since the last `Start`, or a source that plays once and has run out
  (5.2 step 5). It starts the timer with no source connected (4.3).

**Rule 6: a `Stop` on a timer follows a `Start` on it with no other
`Stop` between, with one exception.**

- 6(a) The repeat row may perform a `Stop` on any timer, started or not.
- 6(b) A `Stop` of a source that plays once and has run out (5.2 step
  5) is within the rule.

Note: a writer stops every effect at the repeat row so that the wrap
resumes from a setting that row defines.

**6.3 The check.** A check reads rules 1, 2, 3, 5 and 6 across the rows
in one pass with a model of each timer: the effect in the model, present
or absent, and the last `Start` on the timer, present or absent. The
effect in the model is its target, its source, the row S of its `Start`,
and the row U from which its source has run out by the reckoning (6.4),
unbounded for a source that repeats. At row N the effect in the model
runs where N is below U; otherwise the timer is idle. A *run* is one
effect of the model from its `Start` row to the row of the `Stop` or
`Start` that removes it, or to the last row; the rows of a run that set
its register are those step 2 counts. Closing a run reports its rule 1
line (6.5) where step 2 counted one row or more.

Initial condition: no timer has an effect in the model or a last
`Start`. For each row N from 0 to the last:

1. For each timer the row acts on, in the order A, B, C, D:
   - (a) a `Start`: report the rule 3 line where the `Start` meets a
     condition of rule 3 (6.5); report a rule 2 line for each other
     timer, A to D, whose effect in the model runs at N on the register
     the target writes; where the timer has an effect in the model,
     close its run and remove it; put the effect of the `Start` in the
     model, S = N, U as 6.4 defines; set the timer's last `Start` to it.
   - (b) a `Retune`: report the rule 5 line where the timer has no
     effect in the model running at N.
   - (c) a `Stop`: report the rule 6 line where the timer has no effect
     in the model and N is not the repeat row; where it has one, close
     its run and remove it.
2. For each timer with an effect in the model running at N, A to D:
   where the target's register is not R13 and row N sets it, count row
   N among the run's rows.

After the last row:

3. For each timer with an effect in the model, A to D: close its run;
   the effect stays for step 4.
4. Where the tune repeats: for each timer with an effect in the model
   running at the last row, A to D, report the rule 1(d) line where the
   repeat row performs no operation on the timer.

The report is the lines in the order reported.

**6.4 The reckoning.** A source that plays once has a start row and no
row for its end; a check reckons the end from the rate. For a `Start`
at row S of a source of R rows, divisor D, count C, tune rate H: U = S
+ F, F = (R × D × counted(C) × H + 153,600 + 2,457,599) divided by
2,457,600, the arithmetic exact. The reckoning reads the prescaler and
count of the `Start`; a `Retune` leaves U unchanged. Four rows at a
divisor of 4, a count of 100 and 50 Hz yield F = 1. A warning that rests
on the reckoning ends with the suffix `, which rests on how long a
source that plays once runs, reckoned from its rate`.

Note: 153,600 is a sixteenth of 2,457,600, allowed for a start inside
its frame; the division rounds a partial frame up.

**6.5 What a check reports.** Each warning is one line of the table. X
and Y are timer letters; N the row of the operation, S the start row, F
and L the first and last row of a run that set the register, K how many
did, T the repeat row, J the row count of the source started, I that of
the source of the last `Start` on the timer. Where the third column
names the suffix, the line ends with the suffix of 6.4 under the
condition named.

| rule | condition | reported as |
|---|---|---|
| 1 | one row of the run, row F, sets the register while the effect runs (6.3), and no other row of the run does | `row F: Timer X runs on Rn, and this row sets it`, with the suffix where the source plays once |
| 1 | several rows of the run set the register while the effect runs (6.3), the first at row F, the last at row L, K of them | `rows F to L: Timer X runs on Rn from row S, and K of them set it`, with the suffix where the source plays once |
| 1(d) | the tune repeats to row T, the effect of Timer X runs when the last row has been read, and row T performs no operation on Timer X | `the tune repeats to row T, and Timer X runs on Rn when its last row has played: the wrap resumes with the timer running from the pass before`, with the suffix where the source plays once |
| 2 | a `Start` on Timer X at row N has a target writing Rn, on which the effect of Timer Y runs | `row N: Timer X starts on Rn, where Timer Y runs: rule 2 leaves the order of two timers writing one register to the writer`, with the suffix where the source of Timer Y plays once |
| 3 | a `Start` at row N with `placeReset` clear on a timer with no `Start` before it | `row N: Timer X starts a source without the place's reset, and this timer has run none: the place is where the player left it` |
| 3 | a `Start` at row N with `placeReset` clear on target `setRn`, where the last `Start` on the timer had target `setRm`, m other than n | `row N: Timer X starts a source on setRn without the place's reset, and this timer last ran on setRm` |
| 3 | a `Start` at row N with `placeReset` clear of a source of J rows on the target of the last `Start` on the timer, whose source has I rows, I other than J | `row N: Timer X starts a source of J rows without the place's reset, and the one before it had I` |
| 5 | a `Retune` at row N on an idle timer | `row N: Timer X retunes an effect that is idle: a rate written to a timer with no source on it starts that timer with no source to run`, with the suffix where an effect no `Stop` has removed is in the model for the timer, its source plays once, and N is at or above U (6.3), so the source has run out by the reckoning |
| 6 | a `Stop` at row N on a timer with no effect in the model, where row N is not the row the tune repeats to | `row N: Timer X stops an effect this timer has not started` |

A `Start` yields at most one rule 3 line, the first condition met in
table order. Rules 1, 2 and 5 are read with the effect running until U
(6.3); rule 6 with the effect in the model until a `Stop` or `Start`
removes it, so a `Stop` of a source run out by the reckoning yields no
line (6(b)). The rows of one run are one line, reported where the run
closes, so after lines of later rows; the start row of a replacing
`Start` counts for the new effect's run (1(a)). Of a multi, a check
reports each tune's lines in number order, prefixed `tune N: ` where the
multi has more than one tune. Note: an error of a multi of one tune has
the prefix (1.12); a warning of it does not.

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

The rule 1 lines follow the rule 2 and rule 6 lines since their runs
close after the last row, Timer A before Timer B; the start row 2 of
Timer B counts among its rows; row 1 performs no operation on either
timer, so both rule 1(d) lines are reported.

---

## 7. What a recorder reports

**7.1** A recorder performs the frames of section 4 of one tune on no
chip and produces the record: the figures of the tune once, then one
entry a frame in order. It performs no tick. Of a multi, it performs the
tune the host selects (1.2).

**7.2 The record.** Lines of JSON, one entry a line: no space outside a
string, integers in decimal, the keys of each object in the order this
section lists them, `true`, `false` and `null` as JSON defines them, a
line feed, byte 10, ending every line. The first line is the figures of
the tune (7.3), each line after it one frame (7.4).

**7.3 The first line** is `{"rate":H,"timers":[...],"sources":[...]}`:
`rate` the rate of the tune H; `timers` the timers of the tune (1.10),
each as its letter `"A"` to `"D"`, in that order, `[]` for none;
`sources` the sources of the tune (1.9) in number order, each
`{"rows":[...],"repeat":RR}` with its values in row order and RR its
repeat row, or `null` for a source that plays once, `[]` for none.

**7.4 A frame's line.** For a frame that reads a row,
`{"result":0,"w":{...},"e":{...}}`: `w` the registers the frame writes
(4.4), keyed by number as text, `"0"` to `"13"`, in ascending numeric
order, `"2"` before `"10"`, each with the value written, R7's its six
bits (2.4), `{}` for a row that sets none, a register an effect runs on
included where the row sets it (6.1); `e` the timers the row acts on,
keyed by letter `"A"` to `"D"`, in that order, each with the operation
as one object of the table, `{}` for a row that acts on none. For the
first frame after the end (4.6), `{"result":-1}`.

| operation | object |
|---|---|
| a `Start` | `{"start":{"target":"setRn","source":S,"prescaler":P,"count":C,"timerReset":B,"placeReset":B}}` |
| a `Retune` | `{"retune":{"prescaler":P,"count":C,"timerReset":B,"placeReset":B}}` |
| a `Stop` | `{"stop":{}}` |

`target` is the name of the target (3.1.2), `source` the number S of the
source (1.9), `prescaler` the divisor P (3.3.2), `count` the count C
(3.3.3), `timerReset` and `placeReset` the resets (3.4), each B `true`
or `false`.

**7.5 The length.** The first line and the lines of as many frames as
the host requires, from frame 0, except that the record of a tune that
plays once ends with its `{"result":-1}` line, produced for the first
frame after the end and for no frame after it. One pass and one loop of
a tune of R rows repeating to RR is R + (R - RR) frames; one pass of a
tune of R rows that plays once and the frame after it is R + 1, the
whole record for any number of frames at or above R + 1. Two recorders
of one tune over one number of frames produce one record, line for
line.

**7.6 The example.** One pass of `doc/tunes/example.json` (json.md 10,
csv.md 8): four rows at 50 Hz repeating to row 0, one source `square
13`, started on Timer A on `setR8` at row 0, retuned at row 1, stopped
at row 3, whose stop row sets R8 to 12 within rule 1(a):

    {"rate":50,"timers":["A"],"sources":[{"rows":[13,0],"repeat":0}]}
    {"result":0,"w":{"0":163,"1":2,"2":238,"7":56},"e":{"A":{"start":{"target":"setR8","source":1,"prescaler":50,"count":60,"timerReset":true,"placeReset":true}}}}
    {"result":0,"w":{"0":142,"1":12,"7":49},"e":{"A":{"retune":{"prescaler":50,"count":61,"timerReset":false,"placeReset":false}}}}
    {"result":0,"w":{"0":251,"1":4},"e":{}}
    {"result":0,"w":{"0":89,"1":2,"7":56,"8":12},"e":{"A":{"stop":{}}}}

Frame 4 reads row 0 again, so its line is the second line; one pass and
one loop is eight frames (7.5).

---

## 8. What a later version defines

A player of this version treats each item as not defined.

**8.1** What a reader does with a file whose version is other than 3,
beyond reporting the error of the form (json.md 8.1, csv.md 5.1).

**8.2** Targets past the fourteen: a procedure writing a register of the
MC68901, and one reading a row wider than a byte or of more than one
value, numbered 14 to 127 (3.1.2).

**8.3** Sources of more than one value a row (1.11).

**8.4** The place of a timer before the first `Start` or `Retune` with
`placeReset` set on it, and the row a tick reads where the place is not
the number of a row of the source connected (3.4.2, 3.4.3).

**8.5** The mapping of the steps of 4.3 to the registers of the MC68901,
the order of the count and the prescaler within one step, and what
claiming a timer from the host comprises (3.3.6, 4.1).

**8.6** What a player performs at a tick of a timer with no source
connected (5.4), and whether a tick occurs between two steps of an
operation or between an operation and the register writes of a frame
(4.3).

**8.7** What a host does with a timer running after the end of a tune
that plays once (4.6), and what a player performs between two tunes of
a multi (1.2).

**8.8** How a player obtains bits 7 and 6 of R7 for a write of R7 (2.4).
