# Reading a YM dump

An example. `src/main/java/org/ymxs/ym/` reads a YM5!/YM6! register dump
into a [Tune](../src/main/java/org/ymxs/YMXS.java), and
`bin/ym-to-ymxs` writes that as JSON.

The format itself is free of it. It is here to show how another format
maps onto the structure, and because the figures in the records and in
[SPEC.md](SPEC.md) were read off dumps, so a reader can run them again.

```bash
bin/ym-to-ymxs < tune.ym > tune.json
```

A dump on standard input, JSON on standard output. What it came to goes
to standard error. `-rROW` makes a tune that repeats to that row and `-r`
one that plays once; without either, a tune repeats to the frame the dump
marks.

A distributed `.ym` is usually an archive with the dump inside, and
either reads: `Lha` unpacks one where it is handed one. That is plumbing,
outside the format; it is here so that a reader hands over the file as it
was distributed rather than unpacking it first with a tool of their own.

One dump is one tune, so what comes out is a multi of one.
`bin/ymxs-merge` puts several together:

```bash
{ bin/ym-to-ymxs < one.ym; bin/ym-to-ymxs < two.ym; } | bin/ymxs-merge > both.json
```

## What a dump is

A dump is one frame of the YM2149's registers at a time, sixteen of them,
with two effect slots filed in the bits the chip does not use:

| a slot's | slot 0 | slot 1 |
|---|---|---|
| kind and voice | R1's top four bits | R3's top four |
| prescaler | R6's top three | R8's top three |
| count | R14 | R15 |
| level, or which sample | the voice's volume register, low five bits | the same |

R14 and R15 are the chip's I/O ports, which no tune sounds, so this
format uses them for the counts.

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

A source is built once for each distinct thing a slot sounds, so two
square waves at one level are one source and at two levels are two.

## What it works out

A dump has less in it than a tune, so three things are reckoned rather
than read:

- **How long a recording runs.** A dump marks the start alone. The frames
  its rows run to at its rate are how long it runs, and that settles
  whether the timer is stopped by a later row.
- **Which slot has a voice.** The player a dump was written for runs one
  thing a voice, so a recording on a voice keeps a square wave off it.
- **What the row a tune repeats to sets.** It sets every register but the
  ones an effect is running on, and sets every effect that ran up to it
  or runs into the wrap, so the wrap lands where the rows have already put
  the chip.

The first is a reckoning and not a reading, and a tune whose recording is
still sounding at the row it repeats to loses the rest of it. The tool
says how many of those there were.

## What it does not read

A slot sounding a shape the player a dump was written for runs an empty
handler for, and a recording whose sample is absent from the file. Both
are counted and said.

## Reading it back

`doc/tunes/` has five tunes this made, and the tests read every one of
them back. `ReadTest` builds a dump in the test rather than reading a
file, so a frame's bytes stand where they are read.

`src/test/resources/packed.ym` is 162 bytes of `-lh5-`, and the tune
inside it is the one in `doc/tunes/circus.json`, so `PackedTest` reads
what comes out of the unpacking against a tune that was read another way.
`PipeTest` runs the tools the way a reader runs them, in a pipe.
