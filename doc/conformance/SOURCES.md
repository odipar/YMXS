# The tunes

Each tune of the kit, which tune of its file the record is of, and the
lines of that record. `ConformanceTest` reads every tune and records it
again under `mvn test`, and compares the record line for line with the
file in `records/`, so a change that moves a line fails there. The test
wrote those files: it writes a record missing from `records/`, and fails.

The lines are the first line of SPEC.md 7.3 and the frames 7.5 defines
where a host omits the count: R + (R - RR) for a tune that repeats, and
R + 1 for one that plays once, whose last line is `{"result":-1}`.

`ymxs-check` reports a warning on `four-timers`, `registers`, `several`
and `transformations`, and every other tune of this kit satisfies every
rule of SPEC.md 6. `registers` sets R8 on the row after the one that
starts an effect on it (rule 1), and the frame's line records the write
(7.4); `four-timers` and `several` wrap with timers running, and
`transformations` with a timer running that its repeat row remaps (rule
1(d)). `transformations` also remaps Timer D, a timer outside the tune
(1.10, rule 5): the frame's line records the remap, the first line
leaves Timer D out of `timers`, and 1.9 numbers the transformation of
its mapping (4.3, 7.3, 7.4).

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
| `deferred` | 1 | 17 | a deferred start, recorded at the frame of its row (3.5, 7.4) |
| `transformations` | 1 | 12 | a start through a transformation, a fade of remaps, a remap at the repeat row, a target of three registers through a mapping, transformations of 16 entries and of 256, keys above the most of a register, two transformations of one name, equal entries under two names (3.6.2), and a remap on a timer outside the tune (4.3); the file lists the transformations in another order than 1.9 numbers them (3.6, 7.4) |
