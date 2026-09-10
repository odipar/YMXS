# Reading a YM dump

An example. `src/main/java/org/ymxs/ym/` reads a YM5!/YM6! register dump
into a [Tune](../src/main/java/org/ymxs/Tune.java), and
`bin/ym-to-ymxs` writes that as the text form.

Nothing in the format depends on it. It is here to show how a format that
is not this one maps onto the structure, and because the figures the
records and [SPEC.md](SPEC.md) state were read off dumps, so a reader can
run them again.

```
bin/ym-to-ymxs in.ym [more.ym ...] out.json [-rROW | -r]
```

Each dump named becomes a tune, and the tunes go into one multi in the
order named. `-rROW` makes a tune that repeats to that row and `-r` one
that plays once; without either, a tune repeats to the frame the dump
names. What each dump came to goes to standard error and the file written
to standard output.

A distributed `.ym` is usually an archive holding the dump. This reads the
dump; handed an archive, it names it as one and asks for it unpacked.

## What a dump holds

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
| the frame the dump names | the row the tune repeats to |

A source is built once for each distinct thing a slot sounds, so two
square waves at one level are one source and at two levels are two.

## What it works out

A dump states less than a tune does, so three things are reckoned rather
than read:

- **How long a recording runs.** A dump states its start and not its end.
  The frames its rows take at its rate say when it is over, which is what
  tells a later row whether the timer is stopped.
- **Which slot wins a voice.** The player a dump was written for runs one
  thing a voice, so a recording on a voice keeps a square wave off it.
- **What the row a tune repeats to states.** It sets every register but
  the ones an effect is running on, and states every effect that ran up to
  it or runs into the wrap, so the wrap lands on a state a player has been
  told.

The first is a reckoning and not a reading, and a tune whose recording is
still sounding at the row it repeats to loses the rest of it. The tool
says how many of those there were.

## What it does not read

A slot sounding a shape whose player runs an empty handler for it, and a
recording whose sample the file does not hold. Both are counted and said.

## Reading it back

`doc/tunes/` holds five tunes this made, and the tests read every one of
them back. `ReadTest` builds a dump in the test rather than reading a
file, so what a frame holds is stated where it is read.
