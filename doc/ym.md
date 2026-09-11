# Reading a YM dump

An example. `src/main/java/org/ymxs/ym/` reads a YM5!/YM6! register dump
into a [Tune](../src/main/java/org/ymxs/YMXS.java), and
`bin/ym-to-ymxs` writes that as JSON.

The format does not depend on this package. It is here as a worked
mapping from another format onto the structure, and because the figures in
the records and in [SPEC.md](SPEC.md) were measured on dumps, which this
makes repeatable.

```bash
bin/ym-to-ymxs < tune.ym > tune.json
```

A dump on standard input, JSON on standard output, the result on standard
error. `-rROW` produces a tune that repeats to that row and `-r` one that
plays once; without either, a tune repeats to the row of the frame the
dump marks.

A distributed `.ym` is usually an archive containing the dump, and both
forms read: `Lha` unpacks an archive. That is plumbing, outside the
format, and it is here so that a `.ym` reads as distributed, without a
separate unpacking step.

One dump is one tune, so the output is a multi of one. `bin/ymxs-merge`
combines several:

```bash
{ bin/ym-to-ymxs < one.ym; bin/ym-to-ymxs < two.ym; } | bin/ymxs-merge > both.json
```

## What a dump is

A dump is one frame of the YM2149's registers at a time, sixteen of them,
with two effect slots filed in the bits the chip leaves unused:

| a slot's part | slot 0 | slot 1 |
|---|---|---|
| kind and voice | R1's top four bits | R3's top four |
| prescaler | R6's top three | R8's top three |
| count | R14 | R15 |
| level, or which sample | the voice's volume register, low five bits | the same |

R14 and R15 are the chip's I/O ports, which no tune sounds, so this
format uses them for the two counts.

## What it becomes

| the dump | the structure |
|---|---|
| a frame | a row, setting the registers whose value moved |
| a slot turning on, or changing | a `Start` on Timer A for slot 0, Timer D for slot 1 |
| a slot's rate moving under it | a `Retune` |
| a slot turning off | a `Stop` |
| a square wave at level `n` | a source of `n` and 0, repeating to row 0 |
| the envelope restarted | a source of the shape, repeating to row 0 |
| a recording | a source of the sample's levels and a closing row at mid-scale, playing once |
| the frame the dump marks | the row the tune repeats to |

A source is built once for each distinct sound a slot produces: two
square waves at one level are one source, at two levels two sources.

## What is reckoned

A dump contains less than a tune, so three things are reckoned rather than
read:

- **How long a recording runs.** A dump marks the start alone. Its
  duration is its row count at its rate, and that determines whether a
  later row stops the timer.
- **Which slot owns a voice.** The player a dump was written for runs one
  effect a voice, so a recording on a voice excludes a square wave on it.
- **What the row a tune repeats to sets.** It sets every register except
  those an effect is running on, and sets every effect that ran up to it
  or runs into the wrap, so the wrap resumes from the chip as the rows
  left it.

The first is a reckoning and not a reading: a tune whose recording still
sounds at the row it repeats to loses the remainder. The tool reports how
many.

## What is not read

A slot sounding a shape for which the dump's player runs an empty handler,
and a recording whose sample is absent from the file. Both are counted and
reported.

## Reading it back

`doc/tunes/` contains five tunes produced here, and the tests read every
one of them back. `ReadTest` builds a dump in the test rather than reading
a file, so a frame's bytes stand in the test.

`src/test/resources/packed.ym` is 162 bytes of `-lh5-`, and the tune
inside it is the one in `doc/tunes/circus.json`. `PackedTest` compares the
result of the unpacking against a tune read another way. `PipeTest` runs
the tools in a pipe.
