# JSON

JSON encodes version 4 of the structure in [SPEC.md](SPEC.md) 1.
[csv.md](csv.md) 7 maps its values to CSV cells. Every clause is
normative except sentences beginning `Note:` and section 10.

---

## 1. Terms

**1.1** The *structure* and its terms are defined in SPEC.md 1.

**1.2** A *reader* reads a file of this form into the structure; an
*emitter* emits a file from the structure. A *writer* is the role of
SPEC.md; the key `writer` (3.1) is text of the structure. A *player* reads
the structure alone.

**1.3** A *file* is UTF-8 text whose first JSON value (RFC 8259) is an
object (section 2); a reader reads that value alone. Note: tools.md 8
defines a tool that reads each of several values.

**1.4** An *object* and an *array* are JSON's; a *text* is a JSON string; a
*whole number* is a JSON number written as an optional `-` and digits,
-2,147,483,648 to 2,147,483,647. `null` is the value of one key, `repeat`
(3.1, 4.1).

**1.5** A *column* is an array of exactly R whole numbers, R the row
count of its tune (3.1); index i, from 0, is row i.

**1.6** -1 in a register column marks a row that leaves the register
unchanged (5.3); in a timer column, a part absent from the row's operation
(6.2). Note: -1 is outside the range of every register and every part, so it
is distinct from every value.

**1.7** Rows are numbered from 0 in row order; tunes from 1 in `tunes`
order; sources from 1 in `sources` order; the rows of a source from 0
in `values` order.

**1.8** A *key* is a member name of an object, *present* where the
object has that member and *absent* otherwise.

---

## 2. The file

**2.1** One object:

| key | value | meaning |
|---|---|---|
| `format` | the text `ymxs` | this form |
| `version` | the whole number 4, or 3 for a file of the version before it (2.4) | the version of the structure |
| `tunes` | an array of tune objects (section 3); an empty array is an error of the structure (2.3) | the tunes of the multi; tune n is the element at index n minus 1 |

**2.2** A reader reads the listed keys and skips unknown keys. An emitter
emits only the listed keys, each once, in the order of its section.

**2.3** An empty `tunes` array is an empty multi, an error of the structure
(8.3).

**2.4** A writer writes version 4. A reader reads version 4 and the version
before it: a file of version 3 has one value a row in every source and a
`target` of 0 to 13, the shapes that version defines, and reads as the
structure those make. A row of several values or a `target` above 13 in a
file of version 3 is an error of the form (8.1), as is any other version
(SPEC.md 8).

---

## 3. A tune

**3.1** An object, R the value of `rows`:

| key | value | meaning |
|---|---|---|
| `title` | a text | the tune's title |
| `composer` | a text | the tune's composer |
| `writer` | a text | the program or the person that produced the tune |
| `rate` | a whole number, 1 upward | the tune's rate: the frames a second the player is called at |
| `rows` | a whole number R, 1 upward | the row count: the length of every column of the tune |
| `repeat` | a whole number from 0 to R minus 1, or `null` | the repeat row; `null` marks a tune that plays once |
| `sources` | an array of source objects (section 4) | the sources the tune's rows start; an emitter emits them in the first-start order of SPEC.md 1.9 (3.4) |
| `registers` | an object (section 5) | the registers the rows set, a column a register |
| `timerA`, `timerB`, `timerC`, `timerD` | an object each (section 6) | the operations the rows perform on the effect of that timer, a column a part |

**3.2** Every tune requires `title`, `composer`, `writer`, `rate`, `rows`
and `sources`. An absent `repeat` reads as `null`; absent `registers`
read as an empty object; an absent timer object leaves that timer
unchanged on every row. `null` for `registers` or `timerT` is an error
(8.1). An emitter emits `registers` in every tune and a timer object
for each timer a row acts on.

**3.3** The empty text is a value of `title`, `composer` and `writer`.

**3.4** A reader reads sources in file order, source n at index n - 1.
An emitter emits them in first-start order (SPEC.md 1.9).

**3.5** Two sources are one source where equal as SPEC.md 1.9 defines.

**3.6** Row i of the tune is index i of every column of the tune.

---

## 4. A source

**4.1** An object, V the length of `values`:

| key | value | meaning |
|---|---|---|
| `name` | a text | the source's name; a report names a source by it, and a player does not read it |
| `repeat` | a whole number from 0 to V minus 1, or `null` | the row the source repeats to; `null` marks a source that plays once |
| `values` | an array of V rows, V at least 1: a row is a whole number where the source has one value a row, and an array of two or three whole numbers where it has more | the rows of the source; row j is the element at index j, and a row of several values is a value a register of the target that runs it (SPEC.md 3.1.1) |

**4.2** `name` and `values` are present in every source; an absent `repeat`
reads as `null`. Every row of one source has one shape: a whole number in
each, or an array of one length, two or three, in each. An element of
`values` other than a whole number or such an array, and an array of another
length or of an element other than a whole number, are errors (8.1).

**4.3** Value i of every row is 0 to the most of register i of every target
the rows start the source on (SPEC.md 3.2.2; ranges in 5.1); outside that is
an error of the structure (8.3).

**4.4** Every source in `sources` is started by a row; an unstarted source
is an error (8.1). Note: the structure reaches a source through the row that
starts it, so such a source is lost where the file is read.

**4.5** An emitter emits each source of the tune once (3.5).

---

## 5. The registers

**5.1** `registers` is an object whose keys are among `r0` to `r13`,
each with a column; `rn` is Rn of SPEC.md 2, its values in the
register's range:

| key | register | range |
|---|---|---|
| `r0`, `r2`, `r4` | R0, R2, R4: the low byte of the tone period of voice A, B and C | 0 to 255 |
| `r1`, `r3`, `r5` | R1, R3, R5: the high four bits of the tone period of voice A, B and C | 0 to 15 |
| `r6` | R6: the noise period | 0 to 31 |
| `r7` | R7: mixing, six bits | 0 to 63 |
| `r8`, `r9`, `r10` | R8, R9, R10: the volume of voice A, B and C | 0 to 31 |
| `r11`, `r12` | R11, R12: the low byte and the high byte of the envelope period | 0 to 255 |
| `r13` | R13: the envelope shape | 0 to 15 |

**5.2** An emitter emits the column of each register some row sets, those
alone; a reader reads an absent column as -1 at every row.

**5.3** Index i of `rn` is -1 where row i leaves Rn unchanged, otherwise the
value row i sets. A reader reads -1 as absence and every other whole number
as a value, so one below -1 or above the range is an error of the structure
(8.3).

---

## 6. The effects

**6.1** `timerA` to `timerD` are each an object of the seven keys below,
each a column; T is the timer's letter, S the length of `sources`.
`shape` has a value at every row; each other column has a value where
the row's operation has that part (6.2) and -1 elsewhere.

| key | value where the row has the part | meaning |
|---|---|---|
| `shape` | -1, 0, 1 or 2 | the operation the row performs on the effect of Timer T (6.2) |
| `target` | 0 to 24 | the target of that number (SPEC.md 3.1.2): 0 to 13 is `setRn`, and 14 to 24 the targets of two and three registers |
| `source` | 1 to S | the number of the source (1.7) |
| `prescaler` | 4, 10, 16, 50, 64, 100 or 200 | the divisor of the prescaler |
| `count` | 0 to 255 | the count, the value of the timer's data register |
| `timerReset` | 1 or 0 | `timerReset` of the operation: 1 is true and 0 is false |
| `placeReset` | 1 or 0 | `placeReset` of the operation: 1 is true and 0 is false |

**6.2** `shape` at index i selects the operation of row i on Timer T,
the columns with a part of it at index i, in read order, and the columns
that are -1 there:

| `shape` | operation | the columns with a part of it, in read order | the columns that are -1 |
|---|---|---|---|
| -1 | none: the row leaves the effect of Timer T as it was | none | the other six |
| 0 | a start | `source`, `target`, `prescaler`, `count`, `timerReset`, `placeReset` | none |
| 1 | a retune | `prescaler`, `count`, `timerReset`, `placeReset` | `source`, `target` |
| 2 | a stop | none | the other six |

**6.3** At each row a reader reads `shape`, then the columns 6.2 lists in
that order, verifying each as read (6.6), those columns alone. An emitter
emits -1 in every column 6.2 lists as -1.

**6.4** A column absent where the shape of some row selects it is an
error (8.1). Note: a timer object whose every `shape` is -1 or 2 needs
`shape` alone.

**6.5** `timerReset` and `placeReset`: an emitter emits 1 for true and 0
for false; a reader reads 1 as true and every other whole number as
false.

**6.6** `target` outside 0 to 24, `source` outside 1 to S, and
`prescaler` other than the seven divisors are errors (8.1). `count`
outside 0 to 255, and a prescaler and count whose rate (SPEC.md 3.3)
exceeds 125,000 ticks a second, are errors of the structure (8.3).

---

## 7. Reading

**7.1** In order; the reader stops at the first error of the form (8.1),
reports every error of the structure at once (8.3), and ends with the multi
where every step passes.

1. Parse the text as JSON; the first value is the file (1.3).
2. Read `format`, a text equal to `ymxs`.
3. Read `version`, a whole number equal to 3.
4. Read `tunes`, an array; for each element in order, tune n at index
   n - 1, steps 5 to 11.
5. Read `rows`, a whole number R.
6. Read `sources`, an array; for each element read `values` (an array
   of whole numbers), `name` (a text), `repeat` (a whole number, `null`
   or absent), in that order.
7. Read `registers` where present, an object; for each key `r0` to
   `r13` present, in that order, verify an array of length R and read
   index 0 to R - 1, each a whole number, each other than -1 the value
   the row sets.
8. Read `timerA` to `timerD` where present, in that order, an object
   each; for each row i from 0 to R - 1 read `shape` at index i, one of
   -1, 0, 1, 2, then for a start `source` (1 to S), `target` (0 to 24),
   `prescaler` (one of the seven divisors), `count`, `timerReset`,
   `placeReset`, and for a retune the last four. A column is verified
   present, an array of length R and a whole number at index i as it is
   read, before its value.
9. Read `title`, `composer`, `writer` (a text each), `rate` (a whole
   number), `repeat` (a whole number, `null` or absent), in that order.
10. Verify that every source is started by some row (4.4).
11. The tune is the rows, one an index, each with the registers it sets
    and its operation on each timer.
12. After the last tune, verify the multi against SPEC.md 2 and 3
    (8.3).

---

## 8. Errors

**8.1** An error of the form is a condition of the text that ends the
reading (7.1), reported as one line, except the last condition below,
reported once for each unstarted source, in `sources` order, one line each.
KEY is the key; N, L, S and R numbers, R as read, 0 or below included; T a
timer letter; COL a column name; I a row number; J a row number within a
source; NAME a source's name; X the value read as JSON text, or `null` where
the key is absent, except `an array` or `an object` in the `registers is X`
and `timerT is X` lines, and the text with its quotation marks removed in
the `a tree of X` line.

| condition | the line |
|---|---|
| the text is not JSON | `this is not JSON: ` followed by the parser's report |
| `format` absent or not a text | `format is X, and this form requires a text` |
| `format` a text other than `ymxs` | `a tree of X, and this reads ymxs` |
| `version` absent or not a whole number | `version is X, and this form requires a whole number` |
| `version` other than 3 or 4 | `version N, and this reads 3 or 4` |
| a row of several values, or a `target` above 13, in a file of version 3 | `source X has a row of several values, and version 3 has one value a row`, `target N, and version 3 reaches 0 to 13` |
| `tunes`, `sources` or `values` absent or not an array | `KEY is X, and this form requires an array` |
| `rows` or `rate` absent or not a whole number | `KEY is X, and this form requires a whole number` |
| `title`, `composer`, `writer` or `name` absent or not a text | `KEY is X, and this form requires a text` |
| `repeat` present, not `null` and not a whole number | `repeat is X, and this form requires a row number or null` |
| an element of `values` not a whole number | `NAME at row J is X, and this form requires a whole number` |
| `registers` present and not an object | `registers is X, and this form requires a column a register` |
| `timerT` present and not an object | `timerT is X, and this form requires a column a part of an effect` |
| register column `rN` not an array | `rN is X, and this form requires a column` |
| register column `rN` of a length L other than R | `rN is L values long, and the tune has R rows` |
| a value of register column `rN` not a whole number | `rN at row I is X, and this form requires a whole number` |
| `shape` absent, or a timer column absent where a shape selects it | `Timer T has no "COL" column` |
| a timer column not an array | `Timer T's COL is X, and this form requires a column` |
| a timer column of a length L other than R | `Timer T's COL is L values long, and the tune has R rows` |
| a value of a timer column not a whole number | `Timer T's COL at row I is X, and this form requires a whole number` |
| `shape` other than -1, 0, 1 and 2 | `row I sets shape N on Timer T, and a shape is 0, 1 or 2` |
| `source` outside 1 to S | `row I starts source N, and the tune runs S` |
| `target` outside 0 to 24 | `no target N: a tune reaches 0 to 24` |
| `prescaler` other than the seven divisors | `no prescaler divides by N: a timer's are 4, 10, 16, 50, 64, 100 and 200` |
| a source of `sources` that no row starts | `source N, NAME, is started by no row, and a source a tune does not run is dropped where this form is read` |

**8.2** A value other than an object where one is required reads as an empty
object, so the line is that of the first key read from it: `format is null,
and this form requires a text` for the file, `rows is null, and this form
requires a whole number` for a tune, `values is null, and this form requires
an array` for a source. An empty text reads as an empty file object. A
`registers` or a `timerT` other than an object has its line in 8.1.

**8.3** An error of the structure is a condition of SPEC.md 1.11. A reader
reports every one present after step 12 of 7.1, one line each, the lines of
SPEC.md 1.11 in the order of 1.12, every line of a tune prefixed `tune N: `
as 1.12 defines for a multi, a source's lines at every row that starts it.
The first condition of SPEC.md 1.11 is, in this form, an empty `tunes` array
(2.3).

**8.4** A breach of a rule of SPEC.md 6 is a warning (SPEC.md 6.5), reported
by a check, and a reader reads such a tune as any other; tools.md 5 defines
the tools that run the check after reading.

---

## 9. Layout

**9.1** A reader reads any white space between the tokens of the JSON value
and the members of an object in any order, the last of two members of one
name: it reads any layout.

**9.2** An emitter emits this layout. The *depth* of a value is 1 for
the file's object and d + 1 inside an object or array at depth d; an
*indent* of d is 2d spaces.

| value | depth | layout |
|---|---|---|
| the file's object | 1 | one key a line (9.3) |
| `tunes` | 2 | one tune a line (9.5) |
| a tune | 3 | one key a line (9.3) |
| `sources` | 4 | one source a line (9.5) |
| `registers`, `timerA` to `timerD` | 4 | one key a line (9.3) |
| a source | 5 | one line (9.4) |
| a column | 5 | 20 values a line (9.6) |
| `values` | 6 | 20 values a line (9.6) |

**9.3** An object at depth 1 to 4 with a key: `{`, a line feed, each member
on a line as the indent of d, the key in quotation marks, `: ` and the
value, `,` after every member but the last, then a line feed, the indent of
d - 1 and `}`; empty, `{}`.

**9.4** An object at depth 5: one line, `{`, the members as
`"key": value` separated by `, `, and `}`, as
`{"name": "square 13", "repeat": 0, "values": [13,0]}` in section 10.

**9.5** An array at depth 2 to 4 with an element: `[`, a line feed, each
element on a line as the indent of d and the element, `,` after every
element but the last, then a line feed, the indent of d - 1 and `]`; empty,
`[]`.

**9.6** An array at depth 5 or 6: `[`, the values separated by `,` alone,
`]`; after every twentieth value the `,` is followed by a line feed and the
indent of d; empty, `[]`.

**9.7** A number is decimal, `-` before one below 0 and the one sign. `null`
is the `repeat` of a table that plays once. A text is in quotation marks
with `"` as `\"`, `\` as `\\`, U+0008 as `\b`, U+000C as `\f`, U+000A as
`\n`, U+000D as `\r`, U+0009 as `\t`, every other U+0000 to U+001F as
`\u00XX`, XX two upper-case hexadecimal digits, and every other character as
it is.

**9.8** The file ends with one line feed, U+000A, after the closing
`}`.

**9.9** A file an emitter emits reads into the structure and is emitted
again as identical text.

---

## 10. The example

**10.1** `doc/tunes/example.json`, informative: four rows and one
effect, a square wave on voice A's volume started on Timer A at row 0,
retuned at row 1, stopped at row 3.

```json
{
  "format": "ymxs",
  "version": 4,
  "tunes": [
    {
      "title": "Four rows, one square",
      "composer": "",
      "writer": "by hand",
      "rate": 50,
      "rows": 4,
      "repeat": 0,
      "sources": [
        {"name": "square 13", "repeat": 0, "values": [13,0]}
      ],
      "registers": {
        "r0": [163,142,251,89],
        "r1": [2,12,4,2],
        "r2": [238,-1,-1,-1],
        "r7": [56,49,-1,56],
        "r8": [-1,-1,-1,12]
      },
      "timerA": {
        "shape": [0,1,-1,2],
        "target": [8,-1,-1,-1],
        "source": [1,-1,-1,-1],
        "prescaler": [50,50,-1,-1],
        "count": [60,61,-1,-1],
        "timerReset": [1,0,-1,-1],
        "placeReset": [1,0,-1,-1]
      }
    }
  ]
}
```

**10.2** Five register columns of fourteen, one timer object of four.
Row 1 is a retune, so `target` and `source` are -1 there; row 2 leaves
Timer A alone, so every column of `timerA` is -1 there; row 3 is a stop,
so every column but `shape` is -1 there. `doc/tunes/example.csv` is the
same tune in the CSV form (csv.md 8).
