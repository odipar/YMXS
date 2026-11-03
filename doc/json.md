# JSON

JSON encodes version 7 of the structure in [SPEC.md](SPEC.md) 1.
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

**1.5** A *column* is an array of exactly R elements, R the row count of
its tune (3.1), each a whole number, except in `mapping`, where each is a
whole number or an array of whole numbers (6.7); index i, from 0, is row i.

**1.6** -1 in a register column marks a row that leaves the register
unchanged (5.3); in a timer column, a column the row's shape does not
select (6.2). Note: -1 is outside the range of every register and every
field of an operation, so it is distinct from every value.

**1.7** Rows are numbered from 0 in row order; tunes from 1 in `tunes`
order; sources from 1 in `sources` order; the rows of a source from 0
in `values` order; transformations from 1 in `transformations` order; the
entries of a transformation from 0 in `entries` order, each number the key
of its entry.

**1.8** A *key* is a member name of an object, *present* where the
object has that member and *absent* otherwise.

---

## 2. The file

**2.1** One object:

| key | value | meaning |
|---|---|---|
| `format` | the text `ymxs` | this form |
| `version` | the whole number 7, or 6, 5, 4 or 3 for a file of a version before it (2.4) | the version of the structure |
| `tunes` | an array of tune objects (section 3); an empty array is an error of the structure (2.3) | the tunes of the multi; tune n is the element at index n minus 1 |

**2.2** A reader reads the listed keys of each object of this form and
skips unknown keys. An emitter
emits only the listed keys, each once, in the order of its section.

**2.3** An empty `tunes` array is an empty multi, an error of the structure
(8.3).

**2.4** A writer writes version 7. A reader reads version 7 and the four
versions before it. In a file of version 6 or before a reader skips a
`mapping` column (6.1) and a `transformations` key (3.1), and reads every
start with the mapping of no parts, the identity in every part (SPEC.md
3.6.3); `shape` 3 there is an error of the form (8.1). A file of version 5
reads as version 6: the two differ in the row a deferred start with
`placeReset` clear begins its source at (SPEC.md 3.5.3), which version 5
put at R - 1, the last row's number, modulo the row count of that source.
In a file of version 4 or 3 a reader skips a `deferred` column (6.1) and
reads every start with `deferred` clear. A file of version 3 has one value
a row in every source and a `target` of 0 to 13, the shapes that version
defines, and reads as the structure those make. A row of several values or
a `target` above 13 in a file of version 3 is an error of the form (8.1),
as is any other version (SPEC.md 8).

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
| `transformations` | an array of transformation objects (3.7) | the transformations the mappings of the tune's rows use; an emitter emits them in the number order of SPEC.md 1.9 (3.8) |
| `registers` | an object (section 5) | the registers the rows set, a column a register |
| `timerA`, `timerB`, `timerC`, `timerD` | an object each (section 6) | the operations the rows perform on the effect of that timer, a column a field |

**3.2** Every tune requires `title`, `composer`, `writer`, `rate`, `rows`
and `sources`, and in a file of version 7 `transformations`. An absent
`repeat` reads as `null`; absent `registers` read as an empty object; an
absent timer object leaves that timer unchanged on every row. `null` for
`registers` or `timerT` is an error (8.1). An emitter emits `registers`
and `transformations` in every tune and a timer object for each timer a
row acts on.

**3.3** The empty text is a value of `title`, `composer` and `writer`.

**3.4** A reader reads sources in file order, source n at index n - 1.
An emitter emits them in first-start order (SPEC.md 1.9), so the two
numberings are one in a file an emitter wrote; the record of a recorder
reads the first-start number whichever order a file has (SPEC.md 7.4).

**3.5** Two sources are one source where equal as SPEC.md 1.9 defines.

**3.6** Row i of the tune is index i of every column of the tune.

**3.7 A transformation.** An object, E the length of `entries`:

| key | value | meaning |
|---|---|---|
| `name` | a text | the transformation's name, text for display and for reports; a player skips it |
| `entries` | an array of E whole numbers | the entries of the transformation (SPEC.md 3.6.1): entry k, the value written for key k, is the element at index k |

`name` and `entries` are present in every transformation. An element of
`entries` other than a whole number is an error (8.1). An E outside 1 to
256, and an entry outside 0 to 255, are errors of the structure (SPEC.md
1.11, 8.3).

**3.8** A reader reads transformations in file order, transformation n at
index n - 1. An emitter lists each once, in order of first use (SPEC.md 1.9).
Transformation equality includes both name and entries (SPEC.md 3.6.2).
A recorder numbers transformations in order of first use, even when their
file order differs (SPEC.md 7.4).

**3.9** Every transformation in `transformations` is a part of the mapping
of a row (6.7); an unused transformation is an error (8.1). Note: the
structure reaches a transformation through the mapping that uses it, so
such a transformation is lost where the file is read.

---

## 4. A source

**4.1** An object, V the length of `values`:

| key | value | meaning |
|---|---|---|
| `name` | a text | the source's name; a report names a source by it, and a player skips it |
| `repeat` | a whole number from 0 to V minus 1, or `null` | the row the source repeats to; `null` marks a source that plays once |
| `values` | an array of V rows, V at least 1: a row is a whole number where the source has one value a row, and an array of two or three whole numbers where it has more | the rows of the source; row j is the element at index j, and a row of several values is a value a register of the target that runs it (SPEC.md 3.1.1) |

**4.2** `name` and `values` are present in every source; an absent `repeat`
reads as `null`. Every row of one source has one shape: a whole number in
each, or an array of one length, two or three, in each. An element of
`values` other than a whole number or such an array, and an array of another
length or of an element other than a whole number, are errors (8.1). An
empty `values` is a source of no rows, read as one value a row: on a target
of one register an error of the structure (SPEC.md 1.11, 8.3), and on a
target of two or three registers the line `a source of 1 values a row on
setRn, which reads U` of 8.1, which ends the reading (7.1 step 8).

**4.3** Validate source values through each start's mapping: identity
values must fit the target register; transformation keys must fit the
entry list, and the selected entries must fit the register. Repeat this
for every remap on that timer (SPEC.md 3.6.5; ranges in 5.1). Failures are
errors of the structure (8.3).

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

**6.1** `timerA` to `timerD` are each an object of the nine keys below,
each a column; T is the timer's letter, S the length of `sources`, M the
length of `transformations`. `shape` has a value at every row; each other
column has a value where the shape selects it (6.2) and -1 elsewhere.

| key | value where the shape selects it | meaning |
|---|---|---|
| `shape` | -1, 0, 1, 2 or 3 | the operation the row performs on the effect of Timer T (6.2) |
| `target` | 0 to 24 | the target of that number (SPEC.md 3.1.2): 0 to 13 is `setRn`, and 14 to 24 the targets of two and three registers |
| `source` | 1 to S | the number of the source (1.7) |
| `prescaler` | 4, 10, 16, 50, 64, 100 or 200 | the divisor of the prescaler |
| `count` | 0 to 255 | the count, the value of the timer's data register |
| `timerReset` | 1 or 0 | `timerReset` of the operation: 1 is true and 0 is false |
| `placeReset` | 1 or 0 | `placeReset` of the operation: 1 is true and 0 is false |
| `deferred` | 1 or 0 | `deferred` of a start (SPEC.md 3.5): 1 is true and 0 is false |
| `mapping` | 0, or an array of one whole number or more from 0 to M, one a part of the mapping, an element other than 0 among them | the mapping of a start or a remap (6.7) |

**6.2** `shape` at index i selects the operation of row i on Timer T,
the columns with a value at index i, in read order, and the columns that
are -1 there:

| `shape` | operation | the columns it selects, in read order | the columns that are -1 |
|---|---|---|---|
| -1 | none: the row leaves the effect of Timer T as it was | none | the other eight |
| 0 | a start | `source`, `target`, `prescaler`, `count`, `timerReset`, `placeReset`, `deferred`, `mapping` | none |
| 1 | a retune | `prescaler`, `count`, `timerReset`, `placeReset` | `source`, `target`, `deferred`, `mapping` |
| 2 | a stop | none | the other eight |
| 3 | a remap | `mapping` | `source`, `target`, `prescaler`, `count`, `timerReset`, `placeReset`, `deferred` |

**6.3** At each row a reader reads `shape`, then the columns 6.2 lists in
that order, verifying each as read (6.6, 6.7), those columns alone. An
emitter emits -1 in every column 6.2 lists as -1.

**6.4** A column absent where the shape of some row selects it is an
error (8.1). Note: a timer object whose every `shape` is -1 or 2 needs
`shape` alone.

**6.5** `timerReset`, `placeReset` and `deferred`: an emitter emits 1 for
true and 0 for false; a reader reads 1 as true and every other whole
number as false.

**6.6** `target` outside 0 to 24, `source` outside 1 to S, and
`prescaler` other than the seven divisors are errors (8.1). `count`
outside 0 to 255, and a prescaler and count whose rate (SPEC.md 3.3)
exceeds 125,000 ticks a second, are errors of the structure (8.3).

**6.7 Mappings.** At a start (`shape` 0) or remap (`shape` 3), read
`mapping` as follows, with M transformations in file order:

| value | meaning |
|---|---|
| `0` | identity for every value; the structure has no parts |
| an array | one part per element, in target register order: 0 is identity, n from 1 to M is transformation n |

Resolve transformation numbers when reading the operation (7.1 step 8).
A mapping other than `0` or an array of whole numbers is an error of
the form. An array containing only zeros, including `[]`, or an element
outside 0 to M is also an error of the form (8.1). A part count that
does not fit the target is an error of the structure (8.3).

An emitter writes `0` when every part is identity, including an empty
mapping, and an array otherwise. Note: this removes explicit identity
parts on reading back, including an incorrect part count. A recorder
also writes `0` for both representations (SPEC.md 7.4).

---

## 7. Reading

**7.1** In order; the reader stops at the first error of the form (8.1),
reports every error of the structure at once (8.3), and ends with the multi
where every step passes.

1. Parse the text as JSON; the first value is the file (1.3).
2. Read `format`, a text equal to `ymxs`.
3. Read `version`, a whole number equal to 3, 4, 5, 6 or 7 (2.4).
4. Read `tunes`, an array; for each element in order, tune n at index
   n - 1, steps 5 to 11.
5. Read `rows`, a whole number R. An R below 1 is a tune of no rows, the
   empty table of SPEC.md 1.11, and a line of 8.1 reports R as read.
6. Read `sources`, an array; for each element read `values` (an array),
   `name` (a text), each row of `values` in order, and `repeat` (a whole
   number, `null` or absent), in that order. Of a row, the first of these
   met is its line of 8.1: an array in a file of version 3; an array of a
   length other than 2 and 3; an element of an array other than a whole
   number; a row other than an array or a whole number. After the last
   row, a row whose count of values is other than row 0's is the shape
   line of 8.1, N the count of row 0 and K that of the first row that
   differs. Then, in a file of version 7, read `transformations`, an
   array; for each element read `entries` (an array), `name` (a text) and
   each entry in order (a whole number), in that order.
7. Read `registers` where present, an object; for each key `r0` to
   `r13` present, in that order, verify an array of length R and read
   index 0 to R - 1, each a whole number, each other than -1 the value
   the row sets.
8. Read `timerA` to `timerD` where present, in that order, an object
   each; for each row i from 0 to R - 1 read `shape` at index i, one of
   -1, 0, 1, 2, and 3 in a file of version 7, then for a start `source`
   (1 to S), `target` (0 to 24), `prescaler` (one of the seven divisors),
   `count`, `timerReset`, `placeReset`, in a file of version 5, 6 or 7
   `deferred`, and in a file of version 7 `mapping`; for a retune
   `prescaler`, `count`, `timerReset` and `placeReset`; and for a remap
   `mapping`. A column is verified present, an array of length R and a
   whole number at index i as it is read, before its value, except
   `mapping` at index i (6.7), verified in this order, each a line of 8.1:
   0 or an array of whole numbers; of an array, an element other than 0
   among them; each element in order, 0 to M. After the columns of a
   start, verify that its source has the values a row its target reads
   (SPEC.md 3.1.1, 3.2.1), a line of 8.1.
9. Read `title`, `composer`, `writer` (a text each), `rate` (a whole
   number), `repeat` (a whole number, `null` or absent), in that order.
10. Verify that every source is started by some row (4.4), and that every
    transformation is used by the mapping of some row (3.9).
11. The tune is the rows, one an index, each with the registers it sets
    and its operation on each timer.
12. After the last tune, verify the multi against SPEC.md 2 and 3
    (8.3).

---

## 8. Errors

**8.1** An error of the form is a condition of the text that ends the
reading (7.1), reported as one line, except the last two conditions below,
reported once for each unstarted source, in `sources` order, then once for
each unused transformation, in `transformations` order, one line each.
KEY is the key; N, L, S and R numbers, R as read, 0 or below included; K
the key of an entry, and a number in the line of a source's rows of N and
of K values; T a timer letter; COL a column name; I a row number; J a row
number within a source; M the length of `transformations`; W the values a
row of a source, 1 for a source of no rows (4.2), and U the values its
target reads; `setRn` the target's name (SPEC.md 3.1.2); NAME a source's or
a transformation's name, each line feed in it written as the two characters
`\n` and each carriage return as `\r`. X is the value read as JSON text, an
array or an object written with no white space between tokens, each number
in it as written in the file and each text in it as 9.7 defines; `null`
where the key is absent; `an array` in place of the array where the value of
`registers` or of a `timerT` is an array; and the text with its quotation
marks removed in the `a tree of X` line.

| condition | the line |
|---|---|
| the text is not JSON | `this is not JSON: ` followed by the parser's report |
| `format` absent or not a text | `format is X, and this form requires a text` |
| `format` a text other than `ymxs` | `a tree of X, and this reads ymxs` |
| `version` absent or not a whole number | `version is X, and this form requires a whole number` |
| `version` other than 3, 4, 5, 6 and 7 | `version N, and this reads 3, 4, 5, 6 or 7` |
| a row of `values` that is an array, in a file of version 3 | `source NAME has a row of several values, and version 3 has one value a row` |
| `tunes`, `sources` or `values` absent or not an array | `KEY is X, and this form requires an array` |
| `transformations` or `entries` absent or not an array, in a file of version 7 | `KEY is X, and this form requires an array` |
| `rows` or `rate` absent or not a whole number | `KEY is X, and this form requires a whole number` |
| `title`, `composer`, `writer` or a source's `name` absent or not a text | `KEY is X, and this form requires a text` |
| a transformation's `name` absent or not a text, in a file of version 7 | `KEY is X, and this form requires a text` |
| `repeat` present, not `null` and not a whole number | `repeat is X, and this form requires a row number or null` |
| an element of `values`, or an element of an array in it, not a whole number | `NAME at row J is X, and this form requires a whole number`, X that element |
| an element of `entries` not a whole number, in a file of version 7 | `NAME at entry K is X, and this form requires a whole number`, X that element |
| a row of `values` an array of N elements, N other than 2 and 3 | `a row of N values in source NAME, and a row is a number or two or three values` |
| rows of one source of N values and of K, N other than K | `source NAME has rows of N and of K values, and a source has one shape` |
| `registers` present and not an object | `registers is X, and this form requires a column a register` |
| `timerT` present and not an object | `timerT is X, and this form requires a column a part of an effect` |
| register column `rN` not an array | `rN is X, and this form requires a column` |
| register column `rN` of a length L other than R | `rN is L values long, and the tune has R rows` |
| a value of register column `rN` not a whole number | `rN at row I is X, and this form requires a whole number` |
| `shape` absent, or a timer column absent where a shape selects it | `Timer T has no "COL" column` |
| a timer column not an array | `Timer T's COL is X, and this form requires a column` |
| a timer column of a length L other than R | `Timer T's COL is L values long, and the tune has R rows` |
| a value of a timer column other than `mapping` not a whole number | `Timer T's COL at row I is X, and this form requires a whole number` |
| `shape` other than -1, 0, 1 and 2 in a file of version 6 or before | `row I sets shape N on Timer T, and a shape is -1, 0, 1 or 2` |
| any other `shape` outside -1 to 3 | `row I sets shape N on Timer T, and a shape is -1, 0, 1, 2 or 3` |
| `source` outside 1 to S | `row I starts source N, and the tune runs S` |
| `mapping` at a row of shape 0 or 3 other than 0 and an array of whole numbers, in a file of version 7 | `Timer T's mapping at row I is X, and this form requires 0 or an array of whole numbers` |
| `mapping` at a row of shape 0 or 3 an array whose every element is 0, the empty array included, in a file of version 7 | `Timer T's mapping at row I is X, and a mapping of the identity in every part is 0` |
| an element of `mapping` outside 0 to M, in a file of version 7 | `row I maps through transformation N, and the tune has M` |
| `target` above 13 in a file of version 3 | `target N, and version 3 reaches 0 to 13` |
| any other `target` outside 0 to 24 | `no target N: a tune reaches 0 to 24` |
| `prescaler` other than the seven divisors | `no prescaler divides by N: a timer's are 4, 10, 16, 50, 64, 100 and 200` |
| a start whose source has W values a row on a target that reads U, U other than W | `a source of W values a row on setRn, which reads U` |
| a source of `sources` that no row starts | `source N, NAME, is started by no row, and a source a tune does not run is dropped where this form is read` |
| a transformation of `transformations` that no row uses, in a file of version 7 | `transformation N, NAME, is used by no row, and a transformation a tune does not map through is dropped where this form is read` |

**8.2** A value other than an object where one is required reads as an empty
object, so the line is that of the first key read from it: `format is null,
and this form requires a text` for the file, `rows is null, and this form
requires a whole number` for a tune, `values is null, and this form requires
an array` for a source, `entries is null, and this form requires an array`
for a transformation. An empty text is not JSON, and its line is the first
of 8.1. A `registers` or a `timerT` other than an object has its line in
8.1.

**8.3** An error of the structure is a condition of SPEC.md 1.11. A reader
reports every one present after step 12 of 7.1, one line each, the lines of
SPEC.md 1.11 in the order of 1.12, every line of a tune prefixed `tune N: `
as 1.12 defines for a multi, a source's lines at every row that starts it.
The first condition of SPEC.md 1.11 is, in this form, an empty `tunes` array
(2.3). The number F of a line prefixed `transformation F, NAME: ` is the
number of SPEC.md 1.9, which is the number of the transformation in a file
an emitter wrote (3.8).

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
| `transformations` | 4 | one transformation a line (9.5) |
| `registers`, `timerA` to `timerD` | 4 | one key a line (9.3) |
| a source | 5 | one line (9.4) |
| a transformation | 5 | one line (9.4) |
| a column | 5 | 20 values a line (9.6) |
| `values` | 6 | 20 values a line (9.6) |
| `entries` | 6 | 20 values a line (9.6) |
| an array of a `mapping` column | 6 | 20 values a line (9.6) |

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

**9.9** A file an emitter emits from a structure free of the conditions of
SPEC.md 1.11 reads into a structure that is emitted again as identical
text.

---

## 10. The example

**10.1** `doc/tunes/example.json`, informative: four rows and one
effect, a square wave on voice A's volume started on Timer A at row 0,
retuned at row 1, stopped at row 3.

```json
{
  "format": "ymxs",
  "version": 7,
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
      "transformations": [],
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
        "placeReset": [1,0,-1,-1],
        "deferred": [0,-1,-1,-1],
        "mapping": [0,-1,-1,-1]
      }
    }
  ]
}
```

**10.2** Five register columns of fourteen, one timer object of four.
Row 0 is a start with `deferred` clear and the mapping 0, the identity in
every part, so `transformations` is empty. Row 1 is a retune, so `target`,
`source`, `deferred` and `mapping` are -1 there; row 2 leaves Timer A
alone, so every column of `timerA` is -1 there; row 3 is a stop, so every
column but `shape` is -1 there. `doc/tunes/example.csv` is the same tune
in the CSV form (csv.md 8).

**10.3 A fade.** `doc/tunes/fade.json`, informative, uses the `Vn` volume
transformations of SPEC.md 3.6.5. Its `transformations` array begins with
`V15` and ends with `V0`, in order of first use, each entry list on one line.

| tune rows | operation | mapping |
|---|---|---|
| 0 | start `square 15` on `setR8`, Timer A | `[1]`, through `V15` |
| 1 to 15 | remap Timer A, leaving the source running | `[2]` through `[16]`, selecting `V14` down to `V0` |
| 16 | stop Timer A | unused (`-1`) |
| 16 | start `chord` on `setVoiceA`, Timer B | `[0,0,6]`: identity for tone fine and coarse, `V10` for volume |

The chord uses another timer because every remap must fit every start
on its timer. The fade's one-part mappings do not fit `setVoiceA`'s
three registers (SPEC.md 3.6.5).
