# plan

What a later version of the structure would define, and what each piece
costs. [SPEC.md](SPEC.md) 8 lists the items left open; a piece is worked
out here before it becomes a clause.

---

## A target that writes a voice

SPEC.md 8.2 leaves targets 14 to 127 to a later version and 8.3 sources
of more than one value a row. The two are one piece of work: a target
that writes several registers reads several values a row, and a source
of several values a row runs on such a target alone.

**Why.** A player has four timers (SPEC.md 3.3.6), and an effect runs
one source on one target. A voice whose pitch and volume both move needs
two effects, so it needs two of the four timers; a tune moving two voices
that way has none left for a drum. One target that writes a voice's
registers runs that voice on one timer.

A tick of the second timer also costs the interrupt's entry and its
`rte`, 64 cycles on a 68000 at 8 MHz (1.11), where a target writing three
registers pays two more register writes inside one tick.

### The targets

Each target writes its registers in the order below, one value of the
row each.

| number | name | registers | values a row |
|---|---|---|---|
| 14 | `setToneA` | R0, R1 | 2 |
| 15 | `setToneB` | R2, R3 | 2 |
| 16 | `setToneC` | R4, R5 | 2 |
| 17 | `setVoiceA` | R0, R1, R8 | 3 |
| 18 | `setVoiceB` | R2, R3, R9 | 3 |
| 19 | `setVoiceC` | R4, R5, R10 | 3 |
| 20 | `setEnvelope` | R11, R12 | 2 |
| 21 | `setBuzzer` | R11, R12, R13 | 3 |
| 22 | `setNoiseA` | R6, R8 | 2 |
| 23 | `setNoiseB` | R6, R9 | 2 |
| 24 | `setNoiseC` | R6, R10 | 2 |

A tone target writes the twelve bits of a voice's period, fine then
coarse (2.2). A voice target writes those and the volume, whose bit 4
selects the envelope (2.3), so one source moves a voice's pitch and
volume together and hands the voice to the envelope generator on the row
it sets that bit. An envelope target writes the sixteen bits of the
envelope period (2.5); a buzzer target writes those and the shape, and
every write of R13 restarts the envelope.

A noise target writes the noise period and one voice's volume, which is a
drum swept by hand: the period moves the pitch of the noise and the
volume its shape, on one timer, where a tune moving both today spends
two. R6 is one register for the three voices (2.2), so two noise targets
running at once write one period and the later tick stands; a writer runs
one of the three at a time, as it runs one source on a register under
rule 1. The mixer stays a row's column: R7's six bits are the tone and
the noise switches of all three voices, so a target writing it would
write the switches of the other two voices at every tick of the effect.

### The source

A source of a target that reads U values a row has U values a row, in
the target's register order. SPEC.md 3.2.1 reads "a table of one value a
row" and would read "a table of the values its target reads"; 3.2.2
bounds each value by "the register written by the target of every effect
that runs it" and would bound value i by register i of those targets.
3.1.1 ends with "Every target of this version reads a row of one
value", and a later version replaces that sentence.

Two effects may run one source (3.2.3), so a source of U values a row
runs on targets of U registers alone, and the registers of those targets
line up value by value: `setToneA` and `setToneB` share a source, and
`setToneA` and `setVoiceA` do not.

### What the check already reads

1.11 defines W, the values a row of a source, and U, the values a target
reads, and reports `a source of W values a row on setRn, which reads U`
where the two differ. The condition below it, `a source on setRn whose
row N is V, and the target is 0 to M`, reads one register and would read
the register of value i. So the structure's check has the rule in it
already, and a version that assigns the targets above is where W and U
first differ.

### The Java shapes

`org.ymxs.YMXS` is the structure's specification, and `Target` and
`Source` are sealed interfaces of one record each, each with a javadoc
line naming this extension:

```java
sealed interface Target permits SetRegister { }
record SetRegister(Register register) implements Target { }

sealed interface Source permits Single { }
record Single(String name, Table<Integer> table) implements Source { }
```

A version that assigns the targets groups both by the values a row, and
pairs them in a start a width, so a source that fits its target is a
shape rather than a rule:

```java
sealed interface Target permits OneTarget, TwoTarget, ThreeTarget { }
sealed interface OneTarget extends Target permits SetRegister { }
sealed interface TwoTarget extends Target permits SetTone, SetNoise, SetEnvelope { }
sealed interface ThreeTarget extends Target permits SetVoice, SetBuzzer { }

record SetRegister(Register register) implements OneTarget { }
record SetTone(Voice voice) implements TwoTarget { }      // R0 R1, R2 R3, R4 R5
record SetNoise(Voice voice) implements TwoTarget { }     // R6 and a volume
record SetEnvelope() implements TwoTarget { }             // R11 R12
record SetVoice(Voice voice) implements ThreeTarget { }   // a tone pair and its volume
record SetBuzzer() implements ThreeTarget { }             // R11 R12 R13

/** One of the three voices of the YM2149. */
enum Voice { A, B, C }

sealed interface Source permits Single, Pair, Triple { }
record Single(String name, Table<Integer> table) implements Source { }
record Pair(String name, Table<Two> table) implements Source { }
record Triple(String name, Table<Three> table) implements Source { }

/** One row of a source, a value a register of the target that runs it. */
record Two(int first, int second) { }
record Three(int first, int second, int third) { }

/** A start a width, which Effect permits beside Retune and Stop. */
record Start(OneTarget target, Single source, Prescaler prescaler, int count,
             boolean timerReset, boolean placeReset) implements Effect { }
record StartPair(TwoTarget target, Pair source, Prescaler prescaler, int count,
                 boolean timerReset, boolean placeReset) implements Effect { }
record StartTriple(ThreeTarget target, Triple source, Prescaler prescaler,
                   int count, boolean timerReset, boolean placeReset) implements Effect { }
```

```java
new StartTriple(new SetVoice(Voice.A),
                new Triple("a sweep", new Table<>(List.of(
                        new Three(0x2E, 0x01, 0x0F),
                        new Three(0x20, 0x01, 0x0D)), OptionalInt.of(0))),
                Prescaler.BY_64, 40, true, true);
```

**What the shapes read.** A start of a source of two values on a target
of three registers has no record to stand in, and a row of four values
has none either, since a row is a record a width rather than a list of
values. So the condition 1.11 reports, a source of W values a row on a
target that reads U, is a rule of the forms alone, where a row comes off
text: `Json` and `Csv` read the width and report a mismatch, and code
that builds a structure has the compiler read it.

**What they cost.** `Start`'s two components narrow from `Target` and
`Source` to `OneTarget` and `Single`, which every caller with a `Target`
in hand follows; a reader of an effect gains two arms and a reader of a
target five. The package's javadoc names that as the mechanism: a shape
added to a sealed interface stops `Chip`, `Tunes` and `Check` compiling
until each reads it. Every structure of version 3 keeps the shape it has,
since the records of this version are added to rather than widened.

### Why a record a kind

A target could be a list of registers, `SetRegisters(List.of(R0, R1,
R8))`, and the structure would then model any tuple, with YMXR assigning
numbers to the ones worth an encoding. A record a kind is the narrower
shape and the one this design uses: a reader reads a target by its kind
rather than by reading a list back, and a tuple the table leaves out is
unwritable rather than unencodable. The voice inside a record is the one
thing that varies within a kind, so eleven records, one a row of the
table, would say the same thing three times over for the tone, the voice
and the noise.

The width stands in the interfaces above the records rather than in the
records, so a target added to a kind reaches the effect of that width at
once: a target of the noise period and a voice's tone period, were it
worth a number, is one more record under `ThreeTarget`.

### The accessors

`Tunes` has the two figures the check compares already: `columns(Target)`
is U, the values a row of a source this target reads, and
`columns(Source)` is W, the values a row the source has. Both return 1
today and the check compares them, so that condition reads as it stands.

Three accessors change shape, and twenty-five call sites in the two trees
and the tests read them:

| accessor | today | with the shape |
|---|---|---|
| `most(Target)` | the most of one register | a most a value: `mosts(Target)`, or `most(Target, int value)` |
| `table(Source)` | `Table<Integer>` | `rows(Source)`, a `Table<List<Integer>>`, the three shapes read into one list a row for the check and the forms |
| `values(Source)` | `List<Integer>`, one a row | `values(Source, int column)`, one a row of that column |

`Chip.most(Register)` and `Chip.number(Register)` read one register and
stand as they are; `Tunes.number(Target)` and `Tunes.name(Target)` gain
the arm that reads the new record, and the numbers they return for it are
the table above.

### What a version of this costs

- SPEC.md: 3.1.1 and 3.1.2 (the targets and their numbers), 3.2.1 and
  3.2.2 (the source's shape and its bounds), 1.11's two conditions read
  by value, 2.4's version, and 8.2 and 8.3 closed
- json.md and csv.md: a source's rows of U values in each form, and the
  version each reads
- the check and both trees: a target of several registers, a source of
  several values, and the two conditions by value
- the tests of each: a structure of each new target, and one of a source
  whose values a row differ from its target's registers

### What YMXR would need after it

The encoding and the player are a separate step, and larger: a source
becomes a DTX1 table of U columns, the end marker moves from bit 7 of
every row to one column a target names, which is what lets a source run
on R0, R2 and R4 at last (YMXR, SPEC.md 8.2), and a tick handler writes
two or three registers where every handler today writes one. The cost of
that tick is measured on YMXR's rig before the encoding fixes.

### Open

- the names and the numbers above
- whether a buzzer target writes R13 every tick, which restarts the
  envelope every tick, or the shape belongs to a separate effect
- whether a noise target reads a third value for the voice's tone
  period, so one source runs a drum on a voice that keeps its pitch
