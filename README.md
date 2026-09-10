# YMXS

The format. It states what a tune holds and how a player or an emulator
maps it to an Atari ST's YM2149 and MC68901, and it states nothing about
how a tune is written down.

`src/main/java/org/ymxs/` is the specification: records that state the
structure, in terms a compiler checks. A form is that structure written
down, and no form is the format. This repository holds the text form,
JSON, which a tracker writes and a player's tools read.

This repository depends on nothing but Java, and names no player. A
player names the version of this it reads.

## What is here

```
src/main/java/org/ymxs/   the records, and the text form
doc/text.md               the text form, written out
doc/tunes/                tunes in that form, which the tests read back
```

## The structure

```java
record Multi(List<Tune> tunes)

record Tune(String title, String composer, String writer, int rate,
            List<Source> sources, Table<Row> table)

record Table<T>(List<T> rows, OptionalInt repeat)

record Row(Map<Register, Integer> registers, Map<Timer, Effect> effects)

sealed interface Effect permits Start, Retune, Stop
record Start(Target target, Source source, Prescaler prescaler, int count,
             boolean timerReset, boolean placeReset) implements Effect
record Retune(Prescaler prescaler, int count,
              boolean timerReset, boolean placeReset) implements Effect
record Stop() implements Effect

sealed interface Target permits SetRegister
record SetRegister(Register register) implements Target

sealed interface Source permits Bytes
record Bytes(String name, Table<Integer> table) implements Source

enum Register  { R0 ... R13 }
enum Timer     { A, B, C, D }
enum Prescaler { BY_4 ... BY_200 }
```

A row states what it sets and says nothing about the rest, so a register
absent from its map is one the row does not write and a timer absent is
one it leaves running. Nothing here is arranged for a form's benefit:
what a file can hold is that form's to say.

## Building

Java 23 and Maven. `mvn test` runs the round trip over every tune under
`doc/tunes` and the documents against the house style.

## Where the figures come from

Every measurement in the javadoc and in the documents was read off 49
tunes converted from register dumps of the scene's own music: 331,376
rows and 27,004 statements about an effect. What each record holds, and
the measurement behind each choice, stand in the javadoc beside it.
