# The conformance kit

**1.** The kit is eight tunes under `tunes/`, each a structure in the JSON
form of [json.md](../json.md), and the record of one tune of each beside
it in `records/`: 64 lines over the eight. The kit tests the two
documents, [SPEC.md](../SPEC.md) and [json.md](../json.md): an implementer
who has read them alone writes a recorder, and a record equal to the
reference line for line shows that the documents define it.

**2. What the kit tests.** The recorder of SPEC.md 7 reads a tune and
reports the figures of the tune once and one line a frame, each register
write and each timer operation recorded in place of the chip; the ticks
are outside the record (7.1), so a recorder runs on any machine.

**3. The files.**

| file | what it is |
|---|---|
| `TASK.md` | the task an implementer receives, with the line count of each tune |
| `tunes/X.json` | tune file X, one or two tunes in the form json.md defines |
| `records/X.jsonl` | the record of the tune `SOURCES.md` names, outside a run |
| `SOURCES.md` | a row a tune: which tune of the file the record is of, the lines of it, and what the tune reaches |

**4. What each tune reaches.** SOURCES.md has the table. Between them the
eight reach the four timers, the three operations of 6.2, a source that
repeats to row 0, one that repeats to a row above it, one that plays once,
every register column, R7's six bits, a row that sets a register an effect
runs on, a tune that wraps, a tune that plays once, and a multi whose
second tune runs at a rate of its.

**5. The exercise.** Initial condition: an implementer whose reading
excludes this repository and every implementation of the structure.

1. Copy `TASK.md`, `tunes/`, `../SPEC.md` and `../json.md` into a fresh
   directory. `SOURCES.md` and `records/` stay behind: an implementer who
   can read the answer is outside the exercise.
2. The implementer produces `record.py`, `READ.md` and `NOTES.md` as
   `TASK.md` defines.
3. For each tune, run the recorder at the line count of `TASK.md` and
   compare its output with `records/X.jsonl` byte for byte.

**6. What passes.** All three:

1. Output: every record line for line the reference.
2. Sources: `READ.md` names documents alone; an implementation named
   there, in this repository or on the web, fails the run.
3. Notes: every entry of `NOTES.md` is marked *leaves output as it is*. An
   entry marked *decides output* is a sentence the documents lack, a guess
   that matches the reference included.

**7. The runs.** No run stands here yet.

**8. How the kit is kept true.** `ConformanceTest` reads every tune under
`mvn test` and records it again, and compares the record with the file
line for line; a record the tree does not have is written, and
`SOURCES.generated.md` beside the kit lists what SOURCES.md then has to
say. So the reader writes the kit, and a change that moved a line of it
fails there.
