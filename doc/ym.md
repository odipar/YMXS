# Reading a YM dump

`ym-to-ymxs` ([tools.md](tools.md) 9) converts a YM3!, YM3b, YM5! or YM6!
register dump into a tune. This document defines the dump layout, errors and
mapping from frames to rows, sources and effects. Terms follow
[SPEC.md](SPEC.md). Note: its figures were measured on dumps read this way.

---

## 1. Definitions

**1.1** A *dump* is F frames and the format its first four bytes name: a
YM5! or YM6! dump has a header, D samples and three strings before the
frames (2.1), and the frames of a YM3! or YM3b dump follow those four bytes
(2.6). Every field of more than one byte is most significant byte first,
except in an archive header (2.5).

**1.2** A *frame* of a dump is one call of the dump's player: sixteen
bytes, one a register R0 to R15 of the YM2149; R14 and R15, the I/O
ports, are each the count of one effect (section 4). A frame of a YM3
dump is fourteen bytes, R0 to R13, and its R14 and R15 are 0. The reading
produces one row a frame.

**1.3** A *slot* is one of two fields of a frame, in the high bits of
four registers and the two I/O ports (section 4), *on* or *off* in a
frame; a slot that is on has a kind, a voice, a target, a data value, a
prescaler and a count.

**1.4** A *sample* is one of the D digidrum samples, S bytes of one level
each.

**1.5** A *level* is 0 to 15.

**1.6** The *repeat row* RR is the row the tune repeats to (8.2); for a
tune that plays once RR is F, past the last row.

---

## 2. The layout of a dump

**2.1 The header.**

| offset | bytes | value |
|---|---|---|
| 0 | 4 | `YM5!` or `YM6!` |
| 4 | 8 | `LeOnArD!` |
| 12 | 4 | F, the frame count |
| 16 | 4 | the attributes: bit 0 marks frames laid out one register at a time (2.4), and bit 2 marks samples of one level a byte (2.2); the other bits are not read |
| 20 | 2 | D, the sample count |
| 22 | 4 | the master clock; not read |
| 26 | 2 | H, the frequency the dump's player was called at, in Hz |
| 28 | 4 | L, the loop frame |
| 32 | 2 | E, the byte count of the extra data |
| 34 | E | the extra data; not read |

**2.2 The samples** follow the extra data: for each of the D samples in
order, 4 bytes S then S bytes. With attribute bit 2 set, a byte is one
level, its low four bits; otherwise its high four bits are the level and its
low four are skipped.

**2.3 The strings** follow the samples: the name, the author and the
comment, each bytes ended by a zero byte, decoded as ISO 8859-1. The
comment's text is dropped; its zero byte is required as the others' are.

**2.4 The frames** follow the strings, 16 times F bytes: with attribute bit
0 set, sixteen vectors of F bytes, vector r being register r of frames 0 to
F - 1; otherwise F records of sixteen bytes, byte r of record f being
register r of frame f. Bytes after the frames are skipped.

**2.5 An archive.** An input of 22 bytes or more whose byte 0 is other than
0, bytes 2 to 4 `-lh` and byte 6 `-` is an LHA archive, and the dump is its
first member. The header is level 0: byte 0 the header size, byte 1 the
checksum, the sum of bytes 2 to the header size plus 1 modulo 256, bytes 2
to 6 the method, 7 to 10 the packed size and 11 to 14 the unpacked size,
both least significant byte first, byte 20 zero; the member's data begins at
the header size plus 2. Method `-lh0-` is a stored member, the unpacked size
in bytes copied from the data. Method `-lh5-` is LHA's `-lh5-` coding,
defined by LHA: a dictionary of 8,192 bytes, a longest match of 256 bytes
and a shortest of 3, and Huffman-coded blocks, each opening with a 16-bit
code count and three code-length tables of 19, 510 and 14 symbols; the
header's unpacked size is the byte count decoded. Bytes after the first
member are skipped. For a stored member the unpacked size is trusted; where
it exceeds the bytes after the header, the reading is left to the
implementation (tools.md 11.5).

**2.6 A YM3 dump.** Bytes 0 to 3 are `YM3!` or `YM3b`, and fourteen vectors
of F bytes follow, vector r being register r of frames 0 to F - 1; under
`YM3b` the 4 bytes after the vectors are L. F is the bytes after byte 3, less
those 4 under `YM3b`, divided by 14, and a remainder is an error (section 3).
Bytes after the frames, and after L under `YM3b`, are skipped. The name and
the author are empty, D is 0, H is 50, L is 0 under `YM3!`, and R14 and R15
of every frame are 0, so every slot of every frame is off (4.2).

---

## 3. What is an error of a dump

An error ends `ym-to-ymxs` with exit 1 and an empty output, reported by the
line below (tools.md 4.1). The errors are found in this order, the first
found reported: an archive that fails to unpack (2.5); the fields of 2.1 in
offset order, the samples (2.2) and the strings (2.3), each where the input
ends inside it; F and H, after the strings; the frames (2.4). For a YM3 dump
the frames (2.6) are the one error after the format.

| line | where |
|---|---|
| `not a YM3!, YM3b, YM5! or YM6! dump: it opens with "XXXX"` | bytes 0 to 3 are none of the four, XXXX those bytes |
| `the frames of a YM3! dump are B bytes, and a frame is 14 bytes` | the bytes of a YM3 dump after byte 3, less the 4 of `YM3b`, are 0 or leave a remainder on 14, B being those bytes; `YM3b` in place of `YM3!` for that format |
| `the check string after YM5! is not there` | bytes 4 to 11 are not `LeOnArD!`; `YM6!` in place of `YM5!` for that format |
| `a header field declares B bytes and L are left` | the input ends inside a header field: B is 4 for the field at offset 0, 8 for the field at offset 4, and 2 for every other field of 2.1 and for the size field of a sample (2.2), each of which is read two bytes at a time; L is the bytes left before the end of the input |
| `the extra data declares E bytes and L are left` | the input ends inside the extra data |
| `digidrum N declares S bytes and the file is shorter than that` | sample N, numbered from 0, declares more bytes than are left |
| `a header string with no zero after it` | the input ends inside one of the three strings |
| `a frame count of F` | F is 0 or above 2,147,483,647 |
| `a player frequency of H Hz` | H is 0 |
| `the frames declares B bytes and L are left` | B, 16 times F, is above the L bytes left |
| `this is an archive with a dump inside, and it does not unpack: <reason>` | the input is an archive (2.5), and the reason is one of 3.1 |

**3.1 The reasons an archive fails to unpack:** `LHA header extends beyond
the file`, the header size plus 2 above the input's length; `LHA header
level N; YM archives use level 0`, byte 20 being N; `LHA header checksum
mismatch`; `LHA member is truncated`, the packed size below 0 or above the
bytes after the header; `LHA member claims a negative size`; `unsupported
LHA method <method>`, other than `-lh0-` and `-lh5-`.

---

## 4. The slots

**4.1** Each slot is in three registers of the frame and runs on one
timer:

| slot | code | prescaler | count | timer |
|---|---|---|---|---|
| 0 | R1, bits 7 to 4 | R6, bits 7 to 5 | R14 | A |
| 1 | R3, bits 7 to 4 | R8, bits 7 to 5 | R15 | D |

**4.2** A slot is read as follows, the *code* being the code register's
byte with its low four bits cleared:

1. The voice is bits 5 and 4 of the code: 0 marks the slot off; 1, 2 and
   3 are voices A, B and C, voice number v 0, 1 and 2.
2. The prescaler is the top three bits of the prescaler register: 1 to 7
   are 4, 10, 16, 50, 64, 100 and 200; 0 marks the slot off.
3. The count is the count register's byte, 1 to 255; 0 marks the slot
   off.
4. The kind, in a YM6! dump, is bits 7 and 6 of the code: 0 a square
   wave, 1 a recording, 2 a sinus, 3 a buzzer. In a YM5! dump slot 0 is
   a square wave and slot 1 a recording.
5. The target is `setR13` for a buzzer, else `setR8`, `setR9` or
   `setR10` for voice A, B or C.
6. The data value is the low five bits of the voice's volume register.

---

## 5. The sources

**5.1** A source is identified by a kind and a data value, the slot's
data value with bit 4 cleared for a square wave or a buzzer and all five
bits for a recording; it is built at the first frame a slot of that kind
and value is on, and every later such slot starts the same source. The
sources of the tune are those its rows start, in first-start order
(SPEC.md 1.9).

| kind, data N | name | values | repeat |
|---|---|---|---|
| a square wave | `square N` | N, 0 | row 0 |
| a buzzer | `buzzer N` | N | row 0 |
| a recording | `recording N` | the levels of sample N in order (2.2), then 13 | plays once |

Note: the closing 13 of a recording leaves the volume register at mid-scale,
so the step to the value the row sets it back to is small.

**5.2 A slot that is dropped.** A sinus slot, and a recording slot whose
data value is D or above, are skipped: the slot is off for the frame, and
the figure of dropped slots (section 9) rises by one.

---

## 6. The registers of a row

**6.1** The value of register r, 0 to 13, in frame f is the frame's byte for
r with the bits outside the register cleared: eight bits for R0, R2, R4, R11
and R12, four for R1, R3, R5 and R13, five for R6, R8, R9 and R10, six for
R7. A byte of 255 for R13 marks a frame that leaves R13 alone.

**6.2** Where a recording runs on voice v after the effects of frame f
are resolved (7.5), bits v and v + 3 of the frame's value of R7 are set:
bits 0 and 3 for voice A, 1 and 4 for B, 2 and 5 for C. Note: they mute
the tone and the noise of voice v.

**6.3** A register is *owned* in frame f where a square wave or a
recording runs on a slot after the effects of frame f are resolved and
the register is that slot's target.

**6.4** For each register r, R0 to R12, the reading keeps the *value last
set*: absent before frame 0 and absent at the repeat row before its
registers are read. In frame f: where r is owned, the value last set becomes
absent and the row leaves r alone; otherwise, where the frame's value
differs from the value last set, the row sets r to it, and it is the value
last set. Note: row 0 and the repeat row set every unowned register, and the
first row in which a register is unowned again sets it.

**6.5** The row sets R13 to the frame's value in every frame that writes
R13 (6.1).

---

## 7. The effects of a row

The reading keeps, for each slot i, six values, each with its value before
frame 0 in brackets: the slot *running* on its timer (off); the source it
runs (absent); the prescaler and count last written (4 and 0); the end frame
of a recording (absent); the kind and target of the last start (absent);
whether the slot was running when the repeat row was reached (false). For
each frame f from 0 to F - 1:

**7.1 At the repeat row.** Where f is RR: the value last set of every
register becomes absent (6.4), the kind and target of the last start of each
slot become absent, and the reading records, for each slot, whether it is
running.

**7.2 The slots** of frame f are read (section 4); for a recording slot
that is on, its source is resolved first (section 5), a dropped one
making the slot off.

**7.3 A square wave is preempted.** For slot 0 then slot 1, where i is that
slot, on and other than a recording, and o the other slot as it is at that
point (for i = 0, slot 1 as read in 7.2; for i = 1, slot 0 as these steps
left it):

1. o *starts a recording* on the voice where o is on, a recording, and on
   the voice of i.
2. o *replaces* where o is on and step 1 is false.
3. a recording *runs* on the voice where the slot running on o is a
   recording on the voice of i, f is before its end frame, f is other than
   RR, and step 2 is false.
4. Where i is a square wave and o starts a recording on the voice or a
   recording runs on it, i is off for this frame and the figure of preempted
   frames (section 9) rises by one; otherwise the source of i is resolved, a
   dropped one making the slot off.

**7.4 The operation** on the timer of each slot i, slot 0 then slot 1:

1. Where i is off, f is RR, the slot running is a recording and f is before
   its end frame: the figure of recordings cut at the repeat row (section 9)
   rises by one.
2. Where i is off, the slot running is a recording, f is before its end
   frame and f is other than RR: the row leaves the timer alone, and the
   recording runs on.
3. Where i is off, step 2 is false, and the slot running is on or f is RR: a
   `Stop`, and the slot running becomes off.
4. Where i is on, it *starts* where f is RR, or i is a recording, or its
   kind, its target or its source differs from the slot running's. A start
   is a `Start` of the target, source, prescaler and count of i,
   `timerReset` set where f is RR, the slot running is off, or the slot
   running is a recording and f is at or after its end frame; `placeReset`
   clear where f is other than RR, i is a square wave, the kind of the last
   start is a square wave and its target is the target of i, and set
   otherwise. The slot running becomes i, the source running its source, the
   kind and target of the last start those of i; for a recording, its end
   frame is f plus the frames of its source at its prescaler, its count and
   H (SPEC.md 6.4).
5. Where i is on, step 4 is false, and its prescaler or count differs from
   the one last written: a `Retune` of its prescaler and count with both
   resets clear.
6. Where i is on, its prescaler and count are the ones last written.

**7.5 Ownership.** After 7.4 for slot i: where the slot running is a
square wave or a recording, its target's register is owned in frame f
(6.3); where a recording, R7 has the bits of 6.2 set.

**7.6 After the last frame**, where RR is below F: for each slot whose
operation at row RR is a `Stop`, where the slot was stopped when the repeat
row was reached and is stopped after the last frame, the `Stop` is removed
from row RR.

---

## 8. The tune

**8.1** The title is the dump's name, the composer its author, the
writer `ym-to-ymxs`, the rate H; F rows, row f the registers of section
6 and the operations of section 7 for frame f.

**8.2** With `-r` the tune plays once; with `-rROW` it repeats to row
ROW, unbounded (tools.md 9.3).

**8.3** With both flags absent the tune repeats to row L, the loop frame,
where L is 0 to F - 1, and to row 0 otherwise.

---

## 9. The three figures of the progress line

The progress line of `ym-to-ymxs` (tools.md 9.4) reports three figures
where each is above 0:

| figure | rises by one |
|---|---|
| slots this does not read | for each frame and slot dropped (5.2) |
| frames a recording kept a square wave off its voice | for each frame and slot preempted (7.3) |
| recordings cut at the row the tune repeats to | for each slot whose recording runs into the repeat row (7.4, step 1) |

The end frame of a recording is reckoned (7.4 step 4): the dump marks the
start of a recording alone, and a recording whose reckoned end is past the
repeat row is stopped at that row (7.4 step 3), the rows of its source after
that point unplayed.

---

## 10. What is here

The Java reading is `src/main/java/org/ymxs/ym/`, the Go reading
`go/ym/`; the depacker of 2.5 is a port of the LZH code of Arnaud
Carré's ST-Sound library. Five tunes under `doc/tunes` were written by
`ym-to-ymxs`, `circus`, `digidrum`, `retrigger`, `turrican-2` and
`two-tunes`, and the tests read every one back. `src/test/resources/packed.ym`
is 162 bytes of `-lh5-` archive whose dump is the tune of
`doc/tunes/circus.json`. `ReadTest` builds a dump in the test, so the
bytes of each frame are in the test; `PackedTest` compares the unpacked
dump against a tune read another way; `PipeTest` runs the tools in a
pipe.
