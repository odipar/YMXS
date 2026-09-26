# The tunes

Each tune of the kit, which tune of its file the record is of, and the
lines of that record. `ConformanceTest` reads every tune and records it
again under `mvn test`, and compares the record line for line with the
file in `records/`, so a change that moves a line fails there. The test
wrote those files: it writes a record missing from `records/`, and fails.

The lines are the first line of SPEC.md 7.3 and the frames 7.5 defines
where a host omits the count: R + (R - RR) for a tune that repeats, and
R + 1 for one that plays once, whose last line is `{"result":-1}`.

`ymxs-check` reports a warning on `four-timers`, `registers` and
`several`, and every other tune of this kit satisfies every rule of
SPEC.md 6. `registers` sets R8 on the row after the one that starts an
effect on it (rule 1), and the frame's line records the write (7.4);
`four-timers` and `several` wrap with timers running (rule 1(d)).

| tune | of the file | lines | what it reaches |
|---|---|---|---|
| `four-rows` | 1 | 9 | the example of SPEC.md 7.6: a square on Timer A started, retuned and stopped |
| `one-row` | 1 | 3 | one row, and the register columns it sets |
| `plays-once` | 1 | 6 | a tune that plays once: the record ends with its -1 line (7.5) |
| `four-timers` | 1 | 13 | the four timers, each started, one retuned and one stopped; two run through the wrap |
| `sources` | 1 | 11 | three sources: one repeating to row 0, one to a row above it, one that plays once |
| `registers` | 1 | 7 | every register column, R7's six bits, and a row that sets a register an effect runs on (6.1) |
| `wrap` | 1 | 9 | six rows repeating to row 4, so the record wraps |
| `several` | 1 | 9 | targets of two and three registers, and the sources of two and three values a row they run (3.1.1, 3.2.1) |
| `multi` | 2 | 6 | a multi of two tunes: the record is of the second, at 60 Hz where the first is at 50 |
