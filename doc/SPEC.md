# The YMXS format

Version 3 of the tune data structure, and what a player or an emulator
does with it on an Atari ST's YM2149 and MC68901.

The records under `src/main/java/org/ymxs/` define the structure in terms
a compiler checks, and they are its specification. This document lists
them once (section 1) and defines the rest: what each value reaches on
the two chips (2 and 3), what a frame does with a row (4), what a tick
does with a source's row (5), the rules that bind a writer (6), and what
a reader reports (7). A player is a separate program and pins a version
of this format; [json.md](json.md) writes the version in every file.

A form is the structure written down. [json.md](json.md) and
[csv.md](csv.md) each define one, and a player's binary layout is a
third, defined by that player.

Two words recur, and each means one thing. A **row** is one entry of a
table. A **frame** is one call of the player. A tune's rows advance one a
frame at the tune's rate, and a source's rows advance one a tick at the
rate of its effect's timer.

---

## 1. The tune data structure

The block is `src/main/java/org/ymxs/YMXS.java` with the javadoc off,
the declarations as they compile, and `SpecTest` reads the two against
each other.

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

**A row is one frame's change.** A row lists the registers it sets and
the operation it performs on the effect of each timer, and every part
absent from a row is left as it was: a register absent from `registers`
keeps its value, and a timer absent from `effects` runs on as it was,
running or idle.

**A table repeats or plays once.** `repeat` is the row the table goes
back to after its last row, one of its rows; empty marks a table that
plays once. A tune of `R` rows that repeats plays `R` frames a pass. One
that plays once runs `R` frames and a frame after them, which advances
no row and reports -1 (section 4). A file records rows, and a run counts
frames:

```
  repeats to RR    frames  0  1  2 ... R-1 | R    R+1  ...   every frame reports 0
                   rows    0  1  2 ... R-1 | RR   RR+1 ...

  plays once       frames  0  1  2 ... R-1 | R
                   rows    0  1  2 ... R-1 | -    reports -1, as does every frame after
```

**An effect is a source connected to a target on one timer, at a rate.**
A row performs one of three operations on the effect of a timer. A
`Start` connects a source to a target and sets the rate; a `Retune` sets
the rate of the effect as it runs; a `Stop` stops it. The timer is the
effect: four timers, four effects, and a tune requiring a fifth stops one
of the four on the row that starts it.

**A tune's sources are the ones its rows start**, in first-start order,
numbered from 1 in that order (`Tunes.sources`). The rows reach every
source a tune runs.

**A multi is a list of tunes**, which a host plays as one tune with
subtunes, numbered from 1; a multi of one tune is that tune. The title,
the composer, the writer and the rate belong to each tune.

**A structure is read by functions outside it.** The records have their
accessors alone. `Chip` reads the figures of the two chips, `Tunes` what
is read off a structure, and `Check` the rules a structure must satisfy,
each by pattern matching over every shape, so a shape added here stops
them compiling until they read it. A shape added is a change to this
specification.

---

## 2. The registers

A row sets a register to a value that fits it, 0 to the most the
register reads. Two of the chip's sixteen registers are its I/O ports and
belong to the host; a tune reaches the fourteen below.

| register | reaches | range |
|---|---|---|
| R0, R1 | voice A's tone period: the low byte, then the high four bits | 0 to 255, 0 to 15 |
| R2, R3 | voice B's tone period, the same | 0 to 255, 0 to 15 |
| R4, R5 | voice C's tone period, the same | 0 to 255, 0 to 15 |
| R6 | the noise period | 0 to 31 |
| R7 | mixing | 0 to 63 |
| R8, R9, R10 | the volume of voice A, B and C | 0 to 31 |
| R11, R12 | the envelope period: the low byte, then the high byte | 0 to 255 each |
| R13 | the envelope shape | 0 to 15 |

**A period is two registers because a row sets one without the other.**
A voice's tone divider is twelve bits over two registers and the
envelope's sixteen over two, and a row that moves a note by one step sets
one of them.

**Volume.** Bits 3 to 0 are the level. Bit 4 selects the envelope
generator's level in its place, so 16 to 31 follow the envelope.

**Mixing.** Bits 2 to 0 silence the tone of voices A, B and C, and bits
5 to 3 silence their noise; a set bit silences. Bits 7 and 6 are the
host's I/O port directions: a player writes them as the host left them,
and a tune's value for R7 is six bits.

```
  R7            bit   7   6 | 5   4   3 | 2   1   0
                     host   | noise     | tone            a set bit silences
                            | C   B   A | C   B   A

  R8, R9, R10   bit   4  | 3   2   1   0
                     env | level                          bit 4: the envelope's level
```

**The envelope shape.** Every write to R13 restarts the envelope, the
value changed or the same. A row that writes the value already in R13
restarts the shape sounding, which is the frequent case, so a player
writes R13 on every row that sets it.

**The envelope period.** The envelope steps at 2,000,000 divided by 256
times the period. Period 1 steps at 7,812.5 Hz, the fastest the
generator has, and the divider reads 0 as 1, so 0 and 1 are one rate.

---

## 3. The effects

### 3.1 The targets

A target is a procedure a tick calls with one row of a source: it reads
the row and writes it. This version defines fourteen, `setR0` to
`setR13`, one a register, each writing its row's value to that register.
`setR7` writes bits 5 to 0 and leaves bits 7 and 6 as the host set them.

A target fixes the shape of the row it reads, one value of one byte in
this version. An effect connects a source to a target of that shape, and
every value of the source fits the target's register.

### 3.2 The sources

A source is a table a tick advances one row at a time, its target
writing each row. Its rows are values that fit the register its target
writes. A recording's linear amplitudes convert to the logarithmic
levels of a volume register, and that conversion is the writer's.

This version defines one kind of source, `Single`, one value a row, and
the sound follows from the shape of the table:

| the table | the sound |
|---|---|
| one row, repeating | R13 written at the timer's rate, restarting the envelope each tick: a sync buzzer |
| two rows repeating to row 0 | a volume alternating between a level and 0: a SID voice |
| many rows played once | a recording through a volume register: a digidrum |

The values belong to the source, so two square waves at two levels are
two sources.

Two effects may run one source. Each timer advancing it has a separate
place, so a start or a stop on one timer leaves the other timer's place
where it was.

### 3.3 The rate

A timer ticks at 2,457,600 divided by the prescaler times the ticks its
count counts: seven prescalers, 4, 10, 16, 50, 64, 100 and 200, and a
count of 0 to 255.

**The count is the timer's data register.** The register is a byte, and
the timer loads it and counts down through zero, so 1 counts one tick and
0 counts 256. Every value of the register is a count and every count is a
value of it. A timer a row leaves alone has no count in that row: the
timer is absent from `effects`.

**The rate a 68000 services.** A 68000 at 8 MHz spends 44 cycles entering
an interrupt and 20 leaving it, so 125,000 ticks a second is every cycle
it has, with a handler and the frame still to run. A rate above 125,000
is an error. A rate below it may still be more than a machine plays, with
three other timers and the frame beside it, and a player measures that of
itself.

**A count written to a running timer** loads when the running count
reaches zero, so the rate moves at the end of a period. A prescaler
written to a running timer divides the clock from the write on while the
running count continues, so the period in progress is part at the old
prescaler and part at the new.

### 3.4 The two resets

**`timerReset`** stops the timer, writes the count and starts it, so the
timer begins a whole period at that count and the part of the period it
had run is lost. On a stopped timer the effect is the same with the reset
or without it.

**`placeReset`** puts the place at the source's first row, which the next
tick reads. Without it the place stays at the row number the last tick
read, and that number counts on into the rows of the source that runs
next, through a retune and through a start. So two ticks of a square wave
are one period apart across a start, and the second writes the new
source's level.

A bend is a `Retune` with a changed count and both resets clear. A
struck note is a `Start` with both resets set. A drum struck again at the
rate already running is a `Start` with `placeReset` set and `timerReset`
clear.

---

## 4. What a frame does

A player advances the tune's table one row a frame and writes that row.

**Before the first frame**, a player claims every timer some row starts a
source on (`Tunes.timers`). The registers keep the values the chip was
left at until a row sets them. A tune whose rows leave Timer C alone can
be hosted from the operating system's 200 Hz clock, which runs on Timer
C.

**The effects go first, then the registers.** The row that stops an
effect sets the register the effect was writing (section 6, rule 1), and
with the effects first that write lands after the effect's last tick. A
start fits the same order, since the row that starts an effect leaves
that register alone.

For each timer the row acts on, in the order A, B, C, D:

1. Where the operation sets `timerReset`, the player stops the timer
   first, so that the ticks of the old rate end before the new source or
   rate is in place.
2. A `Stop` stops the timer. The effect is idle until a later row starts
   a source on it, and the place stays at the row number the last tick
   read.
3. A `Start` connects its source to its target, and the timer's ticks
   advance that source from here on.
4. A `Start` or a `Retune` writes the count and the prescaler.
5. Where the operation sets `placeReset`, the place goes to the source's
   first row, which the next tick reads.

Then the registers the row sets, in this order:

1. R0 to R5, the tone periods.
2. R6, and R11 and R12, the noise and envelope periods.
3. R8, R9 and R10, the volumes.
4. R7, its bits 7 and 6 as the host left them.
5. R13, which restarts the envelope.

**What a frame reports.** A frame of a tune whose table repeats reports 0,
every frame. A tune that plays once ends with its last row: the frame
after the one that read it advances no row, writes no register and
reports -1, as does every frame after it.

---

## 5. What a tick does

A tick advances its source one row and calls its target with that row.

The tick that writes the source's last row ends a cycle. Where the source
repeats, the next tick reads the row it repeats to. Where it plays once,
the timer stops at that tick, and the register keeps the last row's value
until a row of the tune sets it.

A tick reads its source alone. The tune's table is read by the frame.

---

## 6. The rules a writer satisfies

A player assumes these rules: it writes each value once and performs no
test.

1. **While an effect runs on a register, the rows leave that register
   alone.** One row sets it: the row that stops the effect, whose write
   sets the register back. Where the row that stops one effect starts
   another on the same register, the row leaves the register alone,
   since it belongs to the second effect from that row on. An effect on
   R13 leaves the rows free to set R13, and each such write restarts the
   envelope beside the restarts the ticks perform. The rule reaches the
   wrap: an effect running when the last row has played runs on through
   the row the tune repeats to, and that row stops it or starts a second
   effect there.
2. **Where two timers write one register, the writer fixes the order.**
   A player writes each tick's value as it comes.
3. **A `Start` sets `placeReset`**, with one exception: where the source
   it starts has the row count of the one this timer last ran on the
   same target, `placeReset` clear preserves the place and the wave's
   phase. A `Stop` between the two leaves the place where it was, since
   `placeReset` and a tick alone move it.
4. **A `Start` on a stopped timer sets `timerReset`.** Where a writer
   cannot determine whether a source that plays once has run out by this
   row, either value is correct, since a stopped timer begins a whole
   period with the reset or without it.
5. **A `Retune` follows a `Start` on that timer.** A rate written to a
   timer with no source connected starts that timer with no source to
   run.
6. **A `Stop` follows a `Start` on that timer**, with one exception: the
   row a tune repeats to stops every effect the tune runs, started on
   this pass or on the one before, so that the wrap resumes from a known
   setting.

**What a check reports.** Rules 1, 3, 5 and 6 are read off the rows, and
a check reports the row that breaks one; the rows of one run of rule 1
are one line, so a run of a hundred rows is one fault. Rule 2 is the
writer's to settle, and a check names the row where the second timer
starts. Rule 4 admits either value and is read by no check. A source that
plays once has a start in the rows and no end, so its duration is
reckoned from its rate, and a check marks a finding that rests on that
reckoning.

---

## 7. What a reader reports

A reader is the role beside the player: it reads a tune, performs the
frames, and reports the result, writing to no chip. It reports the
tune's figures once, then one entry a frame in order. A tick's rate is a
property of the machine, and a reader runs on none, so what a timer
writes between frames is outside the record.

The record is lines of JSON: one line an entry, without spaces, integers
in decimal, names in the order below, `true` and `false` as JSON defines
them, and a line feed ending each line. The first line is the tune's
figures, and each line after it one frame. One pass of
`doc/tunes/example.json`, the tune json.md shows:

    {"rate":50,"timers":["A"],"sources":[{"rows":[13,0],"repeat":0}]}
    {"result":0,"w":{"0":163,"1":2,"2":238,"7":56},"e":{"A":{"start":{"target":"setR8","source":1,"prescaler":50,"count":60,"timerReset":true,"placeReset":true}}}}
    {"result":0,"w":{"0":142,"1":12,"7":49},"e":{"A":{"retune":{"prescaler":50,"count":61,"timerReset":false,"placeReset":false}}}}
    {"result":0,"w":{"0":251,"1":4},"e":{}}
    {"result":0,"w":{"0":89,"1":2,"7":56,"8":12},"e":{"A":{"stop":{}}}}

- `rate` is the tune's rate; `timers` the timers some row starts a
  source on, in the order A, B, C, D; `sources` the sources the rows
  start, numbered 1 upward in first-start order, each with its `rows`
  and the row it repeats to, `null` where it plays once.
- `result` is what the frame reports (section 4): 0, or -1 for the frame
  after the last row of a tune that plays once. An entry of -1 is
  `{"result":-1}`, and the record ends with it.
- `w` lists the registers the frame writes, by number in ascending
  numeric order, `"2"` before `"10"`, each with the value written. A row
  that sets no register is `{}`. R7's value is its six bits.
- `e` lists the timers the row acts on, by letter in the order A, B, C,
  D, each with the operation as one of three entries. A row that acts on
  no timer is `{}`.

| operation | entry |
|---|---|
| a start | `{"start":{"target":"setR8","source":1,"prescaler":50,"count":60,"timerReset":true,"placeReset":true}}` |
| a retune | `{"retune":{"prescaler":50,"count":61,"timerReset":false,"placeReset":false}}` |
| a stop | `{"stop":{}}` |

`target` is the name, `setR0` to `setR13`; `source` the number;
`prescaler` its divisor; `count` the register's value.

The record of a tune is its first line and the entries of its frames from
the first, as many as the caller requires. One pass and one loop is `R`
plus `R` less the repeat row for a tune that repeats; for one that plays
once it is `R` plus 1, the pass and the frame after it. Two readers of
one tune produce one record, line for line.

---

## 8. What a later version defines

- What a player does with a tune of a version it was built without,
  beyond rejecting it.
- Targets past the fourteen: a procedure reaching the MC68901's
  registers, and one that reads a row wider than a byte or of more than
  one value.
- Sources of more than one value a row.
