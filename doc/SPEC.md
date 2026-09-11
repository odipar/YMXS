# The YMXS format

A working draft. The tune data structure, and what a player or an
emulator does with it on an Atari ST's YM2149 and MC68901.

The records under `src/main/java/org/ymxs/` define that structure, in
terms a compiler checks, and they are the specification of it. This
document defines the rest: what each value reaches on the two chips, what
a frame does with a row, what a tick does with a source's row, the rules
that bind a writer, and what a reader reports.

The block in section 1 is `src/main/java/org/ymxs/YMXS.java` with the
javadoc off: the declarations as they compile, and `SpecTest` reads the
two against each other.

A form writes a tune down. [json.md](json.md) is one form; a player's
binary layout is another.

---

## 1. The tune data structure

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

**A row is one entry of a table, and a frame is one call of the player.**
A tune's rows advance one a frame at the tune's rate; a source's rows
advance one a tick at the rate of its effect's timer. That is the whole
difference between the two tables.

The two counts part at the end. A tune of R rows that repeats plays R
frames and begins again; one that plays once runs R frames and a frame
after them, which advances no row and reports -1 (section 4). So a file
records rows and a run counts frames: [json.md](json.md) and
[csv.md](csv.md) write `rows`, and neither writes a frame count.

**The records above have no methods beyond their accessors.** A structure
is read by a function outside it: `Chip` for the figures of the two chips,
`Tunes` for what is read off a structure, and `Check` for the rules a
structure must satisfy. Each reads by pattern matching over every shape,
so a shape added here stops them compiling until they read it.

**A tune's sources are the ones its rows start** (`Tunes.sources`), in
first-start order. Only a source started by some row belongs to the tune.

**A row lists what it sets, and the rest is left alone.** A register
absent from `registers` is one the row does not write; a timer absent from
`effects` is one the row leaves running. A register no row has set keeps
the value the chip was left at, and one a row set keeps that value until
another row sets it or an effect's ticks write it.

**A multi is a list of tunes.** The title, the composer, the writer and
the rate belong to the tune. A file with one rate for a set of subtunes
records the rate its writer measured.

---

## 2. The registers

A row sets a register to a value that fits it, 0 to `Register.most()`.
Two of the chip's sixteen are its I/O ports and are no tune's.

| register | reaches | range |
|---|---|---|
| R0, R1 | voice A's tone period, its low byte and its high four bits | 0 to 255, 0 to 15 |
| R2, R3 | voice B's, the same | 0 to 255, 0 to 15 |
| R4, R5 | voice C's, the same | 0 to 255, 0 to 15 |
| R6 | the noise period | 0 to 31 |
| R7 | mixing | 0 to 63 |
| R8, R9, R10 | a voice's volume | 0 to 31 |
| R11, R12 | the envelope period, its low byte and its high | 0 to 255 each |
| R13 | the envelope shape | 0 to 15 |

**A period is two registers because a row may set one and not the other.**
A voice's tone divider is twelve bits over R0 and R1, and the envelope's
is sixteen over R11 and R12. A row that moves a note by one step sets one
of the two.

**Volume.** Bits 3 to 0 are the level, and bit 4 reads the level from the
envelope generator instead, so the value a row sets is 0 to 31 and 16 to
31 follow the envelope.

**Mixing.** Bits 2 to 0 silence the tone of voices A, B and C, and bits 5
to 3 silence the noise. Bits 7 and 6 are the host's I/O port directions: a
player writes them back unchanged, and no tune moves them.

**The envelope shape.** Four bits. Any write to R13 restarts the
envelope, at any value, so a row that writes the value already in R13
restarts it. A restart of the shape already sounding is more frequent than
a change of shape.

**The envelope period's 0.** The envelope's rate is 2,000,000 divided by
256 times the period, so period 1 sounds at 7,812.5 Hz, the fastest sweep
the generator has. The divider reads 0 as 1, so the two values are one
pitch.

---

## 3. The effects

An effect is a source connected to a target on one timer, at the rate its
prescaler and count come to. A row sets one effect on a timer, and the
timer is the effect: four timers, four effects. A tune requiring a fifth
stops one of the four on the row that starts it.

### 3.1 The targets

A target is a procedure: it reads one row of a source and writes it. This
version defines fourteen, one a register, all of them `SetRegister`.
`setR7` writes bits 5 to 0 and leaves bits 7 and 6 as the host set them.

A target fixes the shape of source row it reads: one value of one byte in
this version. An effect connects a source to a target of that same shape,
and a source's values fit the target's register.

### 3.2 The sources

A source is a table a tick advances a row at a time, its target writing
each row. The sound follows from the shape of that table, and the format
defines no kind of source:

| the source | what it sounds |
|---|---|
| one row repeating | R13 rewritten at the timer's rate, restarting the envelope: a sync buzzer |
| two rows repeating to row 0 | a volume flipped between a level and 0: a SID voice |
| many rows played once | a recording through a volume register: a digidrum |

The values belong to the source, so two square waves at two levels are
two sources, and a row starts one of them.

Two effects may run one source. Each timer advancing it has a separate
place, so one starting or stopping leaves the other where it was.

A source's rows are values that fit the register its target writes. A
recording's linear amplitudes convert to the logarithmic levels of a
volume register, and that conversion is the writer's.

### 3.3 The rate

The rate is 2,457,600 divided by the prescaler times the ticks the count
counts: seven prescalers, 4 to 200, and a count of 0 to 255.

**The count is the timer's data register.** The register is a byte and
every value of it is a count: a timer loads the value and counts it down
through zero, so 1 counts one tick and 0 counts 256. Every value of the
register is a count and every count is a value of it, so this records the
register and not the ticks. A row that sets no effect on a timer has no
count, which is a row absent from the effects rather than a count of 0.

A count written while the timer runs loads when the running count reaches
zero, which moves the pitch without a break. A prescaler written
while it runs neither stops nor reloads the running count, and the new
prescaler divides the clock from the write on, so the period the timer is
counting is part at the old prescaler and part at the new.

### 3.4 The two resets

**`timerReset`** stops the timer, writes the count, and starts it, so the
timer begins a whole period at that count and loses the part of the last
one it had run. It affects a running timer; a stopped timer begins a whole
period either way, so where a writer cannot determine which, either value
is correct.

**`placeReset`** puts the place at the source's first row, which the next
tick reads. Without it the place stays at the row number the last tick
read, and that number counts into the rows of the source that runs next,
through a change of rate and through a start. So two ticks of a square
wave are a whole period apart across a start, and the level the second
writes is the new source's.

A bend is a `Retune` with a changed count. A struck note is a `Start`
with both resets. A drum struck again is a `Start` with `placeReset` at
the rate already running.

---

## 4. What a frame does

A player advances the tune's table one row a frame and writes that row.

**The effects go first, then the registers.** That order preserves a
restore: the row that stops an effect sets the register the effect was
writing, and if the register were written first, a tick of the
still-running effect would overwrite it. A start requires no such order,
since a row that starts an effect leaves that register alone (section
6).

For each timer the row acts on:

1. Where the row sets `timerReset`, the player stops the timer before
   it writes anything else to it, so that no tick of the old rate reads
   the new source.
2. A `Stop` stops the timer. The effect is idle until a later row starts
   a source on it, and the place stays at the row number the last tick
   read.
3. A `Start` resolves its source on its target, and the timer's ticks
   advance that source from here on.
4. A `Start` or a `Retune` writes the count and the prescaler.
5. Where the row sets `placeReset`, the place goes to the source's
   first row, which the next tick reads.

Then the registers the row sets, in this order:

1. R0 to R5, the tone periods.
2. R6, and R11 and R12, the noise and envelope periods.
3. R8, R9 and R10, the volumes.
4. R7, whose bits 7 and 6 the player writes as the host left them.
5. R13, and any write to it restarts the envelope.

A row writes the registers it sets and moves the timers it acts on.

**What a frame reports.** A tune whose table repeats has a row after its
last, and every frame reports 0. A tune that plays once ends with its last
row: the frame after the one that read it advances no row, writes no
register and reports -1, as does every frame after it.

---

## 5. What a tick does

A tick advances its source one row and calls its target with that row.

The tick that writes the source's last row is the last of its cycle.
Where the source repeats, the next tick reads the row it repeats to.
Where it does not, the timer stops, and the register keeps the last row's
value until a row of the tune's table sets it.

A player does not read the tune's table between ticks.

---

## 6. The rules a writer satisfies

These rules bind a writer. A player assumes them: it writes a value once
and performs no test.

1. **While an effect runs on a register, a row does not set that
   register.** One row does: the row that stops the effect, whose write
   sets the register back. Where the row that stops one effect starts
   another on the same register, the row leaves it alone, since the
   register is the second effect's from that row. An effect on R13 leaves
   the row free, and the frame's write to R13 restarts the envelope
   alongside the restarts from the ticks.
2. **Where two timers write one register, the writer fixes the order.** A
   player writes each tick's value.
3. **A `Start` sets `placeReset`**, unless the source it starts has the
   row count of the one this effect last ran on the same target, where
   leaving it clear preserves the place and the wave's phase. A `Stop`
   between them makes no difference, since only `placeReset` and a tick
   move the place.
4. **A `Start` sets `timerReset` where the timer is stopped.** Where a
   writer cannot determine whether a source that plays once has run out by
   this row, either value is correct, since a stopped timer begins a whole
   period with it or without it.
5. **A row does not `Retune` an effect that has never started.** A rate
   written to a timer with no source on it starts that timer with no
   source to run.

Four rules that bound a writer of an earlier draft are gone, since the
structure settles them where a row is constructed: an effect a row starts
is one the tune runs, a source and a target a row uses are ones this
document defines, a row that starts an effect for the first time names its
target, and a row that stops an effect has no rate in it.

Three of the five above are read off a tune's rows, and a check reports
the row that breaks one. Rule 2 leaves the order to the writer and is not
checked. Rule 4 admits either value, and is not checked either.

A source that plays once has a start in the rows and no end, so its
duration is reckoned from its rate, and a check marks a reading that rests
on that reckoning.

---

## 7. What a reader reports

A reader is the role beside the player: it reads a tune and reports the
result, and writes to no chip. It reports the tune's fixed figures once,
then the result of each frame, one entry a frame in order and one for the
frame after the last row. What a timer writes between frames is omitted: a
tick's rate is a property of the machine, and a reader runs on none.

The report is lines of JSON, one entry a line, without spaces, its
integers in decimal, its names in the order below, and `true` and `false`
as JSON defines them; each line ends with a line feed. The first line is
the tune's fixed figures, and each line after it one frame:

    {"rate":50,"timers":["D"],"sources":[{"rows":[13,0],"repeat":0}]}
    {"result":0,"w":{"0":251,"1":4,"7":49},"e":{"D":{"start":{"target":"setR10","source":1,"prescaler":4,"count":122,"timerReset":true,"placeReset":true}}}}
    {"result":0,"w":{},"e":{}}
    {"result":-1}

- `rate` is the tune's rate, `timers` the timers it uses in the order A,
  B, C, D, and `sources` the sources the rows start, 1 upward, each with
  its rows and the row it repeats to, or `null` where it plays once.
- `result` is what the frame reports: 0, or -1 for the frame after the
  last row of a tune that plays once. That entry has `result` alone, and
  the record ends with it.
- `w` lists the registers the frame writes, by number in ascending
  numeric order, `"2"` before `"10"`, and only those: a register the row
  does not set is absent, and a row that sets none is `{}`. R7's value is
  its six bits, since bits 7 and 6 are the host's.
- `e` lists the timers the row acts on, by name in the order A, B, C, D.
  Each entry is the shape the row sets and the parts of that shape, as
  [json.md](json.md) writes it. A row without an effect is `{}`.

The record of a tune is its first line and the entries of its frames from
the first, as many as the caller requires. One pass plus one loop is the
row count plus the row count less the repeat row, for a tune that repeats;
for one that plays once it is the pass and the frame after it, the row
count plus 1.

---

## 8. Not yet written

- What a player does with a tune of a version it was not built for,
  beyond rejecting it.
- The targets numbered past the fourteen: a procedure reaching the MFP
  registers, and one that reads a row wider than a byte or of more than
  one value.
