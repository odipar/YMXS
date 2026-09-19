# The conformance kit

**1.** The kit is nine tunes under `tunes/`, each a structure in the JSON
form of [json.md](../json.md), and the record of one tune of each beside
it in `records/`. The kit tests the two
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
nine reach the four timers, the three operations of 6.2, a source that
repeats to row 0, one that repeats to a row above it, one that plays once,
every register column, R7's six bits, a row that sets a register an effect
runs on, a tune that wraps, a tune that plays once, a multi whose second
tune runs at a rate of its, and the targets of two and three registers
with the sources of two and three values a row they run.

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

**7. The runs.**

**The first run**, 2026-09-19, against the kit at eight tunes. The
implementer wrote a recorder of 360 lines from SPEC.md and json.md alone
and produced all eight records line for line, the multi's second tune and
the `-1` line of the tune that plays once among them. Its notes had 15
entries with 7 marked *decides output*, and six places changed, three of
them outright contradictions.

- json.md 7.1 step 3 read "Read `version`, a whole number equal to 3",
  where 2.4 reads version 4 and the version before it and every tune of
  this kit is version 4: a reader that followed the step stopped on every
  tune of it. The step reads 3 or 4.
- SPEC.md 1.8 read that this version defines one kind of target and one
  kind of source, which 1.1, 3.1.1 and 3.2.1 contradict. It names the
  three of each.
- json.md 7.1 step 6 read `values` as an array of whole numbers, where
  4.1 has a row of two or three as an array. The step reads 4.1.
- 7.4's `source` is the number in first-start order (1.9) where a file
  numbers the sources in it in file order (json.md 1.7), and no clause
  bound a reader to the first: four lines of `four-timers` turn on it. 7.4 and
  json.md 3.4 name the order now.
- 7.5 left the count where a host names none to the arithmetic of the
  sentence after it. The clause reads the count.
- 7.3 left the shape of a source of several values a row: one list, the
  values of row 0 then those of row 1 and so on.

3.1.1 cited 2.5 for the envelope period, which stands in 2.2, and a 2.6
that no document has for the shape that every write restarts, which is
2.5; R6 stands in 2.1's table rather than 2.2. The kit gained a ninth
tune for what the run could not reach, `several`, whose three sources of
two and three values a row run on targets of two and three registers.

**8. How the kit is kept true.** `ConformanceTest` reads every tune under
`mvn test` and records it again, and compares the record with the file
line for line; a record the tree does not have is written, and
`SOURCES.generated.md` beside the kit lists what SOURCES.md then has to
say. So the reader writes the kit, and a change that moved a line of it
fails there.
