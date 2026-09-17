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

A tone target writes the twelve bits of a voice's period, fine then
coarse (2.2). A voice target writes those and the volume, whose bit 4
selects the envelope (2.3), so one source moves a voice's pitch and
volume together and hands the voice to the envelope generator on the row
it sets that bit. An envelope target writes the sixteen bits of the
envelope period (2.5); a buzzer target writes those and the shape, and
every write of R13 restarts the envelope.

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

`org.ymxs.YMXS` is the structure's specification, and the two shapes this
needs are sealed interfaces of one record each, each with a javadoc line
naming this extension:

```java
sealed interface Target permits SetRegister { }
record SetRegister(Register register) implements Target { }

sealed interface Source permits Single { }
record Single(String name, Table<Integer> table) implements Source { }
```

A version that assigns the targets adds a record to each:

```java
sealed interface Target permits SetRegister, SetRegisters { }

/** A target that writes a source's row to several registers, value i of
 *  the row to register i of this list. */
record SetRegisters(List<Register> registers) implements Target { }

sealed interface Source permits Single, Several { }

/** A source of several values a row, one a register of the target that
 *  runs it. */
record Several(String name, Table<List<Integer>> table) implements Source { }
```

A record added rather than a record widened leaves every structure of
version 3 the shape it is, and the sealed interfaces stop `Chip`, `Tunes`
and `Check` compiling until each reads the new shape, which the package's
javadoc names as the mechanism.

An effect pairing a source with a target it fits is a rule rather than a
shape: no record expresses "this source has a value a register of that
target". `Check` reads it, and 1.11 has the line already.

```java
new Start(new SetRegisters(List.of(R0, R1, R8)),
          new Several("a sweep", new Table<>(List.of(
                  List.of(0x2E, 0x01, 0x0F),
                  List.of(0x20, 0x01, 0x0D)), OptionalInt.of(0))),
          Prescaler.BY_64, 40, true, true);
```

### What names a target

Two ways, and the choice decides where the names and the numbers of the
table above live.

- **The registers.** `SetRegisters(List.of(R0, R1, R8))`. The structure
  models any tuple of registers, and YMXR assigns numbers to the eight
  worth an encoding; a structure of another tuple is one YMXR leaves
  unencoded, as a rate above 65,535 is (YMXR, tools.md 3.6). This follows
  the rule `YMXS.java` opens with: no part of the structure is arranged
  for a form, and every limit in it follows from the two chips.
- **A named group.** An enum of the eight and `SetVoice(Voice.A)`. The
  names and the numbers then stand in the structure, a tuple outside them
  cannot be expressed, and every reader reads one enum rather than a
  list.

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
| `table(Source)` | `Table<Integer>` | `rows(Source)`, a `Table<List<Integer>>` whose row is a list |
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
- whether a target of the noise period and a voice's mixing bits is
  worth a number beside these
