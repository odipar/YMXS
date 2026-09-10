# YMXS

The format. It states what a tune holds and how a player or an emulator
maps it to an Atari ST's YM2149 and MC68901, and it states nothing about
how a tune is written down.

`src/main/java/org/ymxs/` is the specification: records that state the
structure, in terms a compiler checks. [doc/SPEC.md](doc/SPEC.md) states
what a player or an emulator does with them, and lists the records once,
where a test reads that listing back against the records themselves.

A form is that structure written down, and no form is the format. This
repository holds the text form, JSON, which a tracker writes and a
player's tools read.

This repository depends on nothing but Java, and names no player. A
player names the version of this it reads.

## What is here

```
src/main/java/org/ymxs/      the records, and the text form
src/main/java/org/ymxs/ym/   an example: a YM register dump read in
doc/SPEC.md                  what a player does with a structure
doc/text.md                  the text form, written out
doc/ym.md                    the example, written out
doc/tunes/                   tunes in that form, which the tests read back
bin/ym-to-ymxs               a dump into the text form
```

## Building

Java 23 and Maven, and nothing else. `mvn test` reads every tune under
`doc/tunes` back, holds SPEC.md's listing to the records, and holds the
documents to the house style.

## Where the figures come from

Every measurement in the javadoc and in the documents was read off 49
tunes converted from register dumps of the scene's own music: 331,376
rows and 27,004 statements about an effect. What each record holds, and
the measurement behind each choice, stand in the javadoc beside it.
