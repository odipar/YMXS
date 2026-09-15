# CSV

CSV encodes version 3 of the structure in [SPEC.md](SPEC.md) 1 as
blocks of comma-separated values for reading in a spreadsheet. Section
7 maps [JSON](json.md) values to cells. Every clause is normative
except sentences beginning `Note:` and section 8.

---

## 1. Terms

**1.1** The *structure*, a *reader*, an *emitter*, a *writer* and a
*player* are as json.md 1.1 and 1.2 define them, with this form in place
of that one; the terms of SPEC.md have its meanings; a *block* is
defined in 1.5.

**1.2** A *file* is UTF-8 text. A byte order mark, U+FEFF, at its start is a
character of the first line, which is then a line before the first heading
line (2.2), an error of the form (5.1). A *line* is the text up to a line
feed, U+000A, or up to the end of the text after the last one; a carriage
return, U+000D, is a character of its line. Note: where lines end in a
carriage return and a line feed, the last column name of every block ends in
a carriage return, is outside the column names of section 3, and reads as
absent (2.4).

**1.3** A *blank line* is empty or white space alone.

**1.4** A *heading line* begins with `###`; its *name* is the text after
`###` with leading and trailing white space removed.

**1.5** A *block* is a heading line, the *column line* after it, and the
*row lines* after that (2.2); its name is the heading line's, and the
cells of the column line are the *column names*.

**1.6** A *cell* is one value of a line as 2.3 splits it; an *empty cell* is
the empty text; a *quoted cell* begins with `"`.

**1.7** A *whole number* in a cell is an optional `+` or `-` and decimal
digits, -2,147,483,648 to 2,147,483,647, with any white space before and
after it, which a reader removes.

**1.8** Rows are numbered from 0 in row order; tunes from 1 in the order
of their `tune` blocks; sources from 1 in the order of their `source`
blocks within the tune; the rows of a source from 0 in the order of its
values in its `value` blocks.

---

## 2. Lines and cells

**2.1** A reader reads line by line, skipping blank lines.

**2.2** After skipping blank lines, the file begins with a heading line.
Each heading opens a block: the next line is its column line, followed
by row lines up to the next heading or the end of the text.

**2.3** A line is split into cells at each `,` outside a quoted cell. A cell
beginning with a character other than `"` is the characters up to the next
`,` or the end of the line, a `"` among them a character of the cell. A
quoted cell's content is the characters after the opening `"` up to the
closing `"`, a `"` followed by a character other than `"` or by the end of
the line, each `""` before it one `"`, with the characters after the closing
`"` up to the next `,` or the end of the line appended; where the closing
`"` is absent, the content is the characters to the end of the line, each
`""` one `"` and each `,` a character of it. A line whose every `,` is
inside a quoted cell is one cell.

**2.4** A row cell belongs to the column at the same index in the column
line. A reader finds columns by name, using the first occurrence of a
duplicate name. Missing cells and absent columns read as empty cells.
The reader skips cells beyond the last column name and columns outside
the block's list in section 3.

**2.5** An emitter emits a cell as its characters where every character is
other than `,` and `"`, the first character is other than `#`, and the first
and the last character are other than white space, and otherwise as a quoted
cell: `"`, the characters with each `"` doubled, `"`. Note: a row line whose
first cell begins `###` would read as a heading line.

**2.6** A value of the structure containing a line feed or a carriage return
is an error of the emitter (5.4). A reader reads a line feed as the end of a
line in every position, a quoted cell included.

---

## 3. The blocks

**3.1** In the order an emitter emits them: the `multi` block, first and
once; then for each tune in tune order its `tune` block, for each source
in first-start order (SPEC.md 1.9) a `source` block then a `value`
block, a `registers` block, and a `timerA`, `timerB`, `timerC` and
`timerD` block for each timer some row acts on, in that order.

**3.2** A tune's blocks follow its `tune` block up to the next `tune`
block or the end of the file. Within a tune, blocks may appear in any
order, except that a `value` block requires a preceding `source` block
and belongs to the last such source. Note: a reader reads timer blocks
after the tune's other blocks (4.1 step 6).

**3.3** The `multi` block has one row line:

| column | cell | meaning |
|---|---|---|
| `format` | `ymxs` | this form |
| `version` | the whole number 3 | the version of the structure |
| `tunes` | a whole number | the number of tunes; an emitter emits it, and a reader does not read it |

**3.4** A `tune` block has one row line; R is the value of `rows`:

| column | cell | meaning |
|---|---|---|
| `title` | text | the tune's title |
| `composer` | text | the tune's composer |
| `writer` | text | the program or the person that produced the tune |
| `rate` | a whole number, 1 upward | the tune's rate: the frames a second the player is called at |
| `rows` | a whole number R, 1 upward | the row count |
| `repeat` | a whole number from 0 to R minus 1, or empty | the repeat row; an empty cell marks a tune that plays once |

**3.5** A `source` block has one row line and opens a source, the n-th
`source` block of a tune opening source n; V is the number of values of
the source (3.6):

| column | cell | meaning |
|---|---|---|
| `name` | text | the source's name; a report names a source by it, and a player does not read it |
| `repeat` | a whole number from 0 to V minus 1, or empty | the row the source repeats to; an empty cell marks a source that plays once |

**3.6** A `value` block has one row line a value of the source it
belongs to, its `value` cells being rows 0 upward of that source; where
several `value` blocks belong to one source, their values continue in
file order, and V counts all of them:

| column | cell | meaning |
|---|---|---|
| `row` | a whole number | the row number of the value within the source; an emitter emits it, and a reader does not read it |
| `value` | a whole number, from 0 to the largest value that fits the register of every target the tune's rows start the source on (json.md 4.3) | the value of that row of the source |

**3.7** A `registers` block has a column a register and a row line a row
of the tune; an emitter emits one row line for each row that sets a
register (6.2). The ranges are those of json.md 5.1:

| column | cell | meaning |
|---|---|---|
| `row` | a whole number from 0 to R minus 1 | the row of the tune |
| `r0` to `r13` | empty, or a whole number in the range of the register | empty: the row leaves that register unchanged; a number: the value the row sets that register to |

**3.8** A reader reads each row line of a `registers` block in file order; a
row line sets, for the row its `row` cell names, each register whose cell is
filled. Where two row lines of the `registers` blocks of one tune name one
row, the later in file order sets a register both fill.

**3.9** A `timerA`, `timerB`, `timerC` or `timerD` block has a row line
a row of the tune that acts on that timer, T the timer's letter, S the
number of `source` blocks of the tune; an emitter emits one row line for
each row that acts on the timer (6.2):

| column | cell | meaning |
|---|---|---|
| `row` | a whole number from 0 to R minus 1 | the row of the tune |
| `shape` | 0, 1 or 2 | the operation the row performs on the effect of Timer T: 0 a start, 1 a retune, 2 a stop |
| `target` | a whole number from 0 to 13 | the target: n is `setRn`, the target that writes register Rn |
| `source` | a whole number from 1 to S | the number of the source |
| `prescaler` | 4, 10, 16, 50, 64, 100 or 200 | the divisor of the prescaler |
| `count` | a whole number from 0 to 255 | the count, the value of the timer's data register |
| `timerReset` | 1 or 0 | `timerReset` of the operation: 1 is true and 0 is false |
| `placeReset` | 1 or 0 | `placeReset` of the operation: 1 is true and 0 is false |

The `shape` cell selects the cells with a part of the operation, in read
order, and the cells that are empty:

| `shape` | operation | the cells with a part of it, in read order | the cells that are empty |
|---|---|---|---|
| 0 | a start | `source`, `target`, `prescaler`, `count`, `timerReset`, `placeReset` | none |
| 1 | a retune | `prescaler`, `count`, `timerReset`, `placeReset` | `source`, `target` |
| 2 | a stop | none | the other six |

**3.10** A reader reads the `shape` cell of a row line and then the cells
the shape selects, in the order 3.9 lists them, each verified as read (4.1
step 6), those cells alone; it reads 1 in `timerReset` and `placeReset` as
true and every other whole number as false. Where two row lines of the
blocks of one timer name one row, the later in file order replaces the
earlier.

---

## 4. Reading

**4.1** In order; the reader stops at the first error of the form (5.1),
reports every error of the structure at once (5.3), and ends with the multi
where every step passes.

1. Split the text into lines and blocks (2.1, 2.2), reporting a heading line
   lacking a name, a name with a `,` in it, a heading line followed by a
   heading line or by the end of the text, and a line other than a blank
   line before the first heading line.
2. Verify that the first block is named `multi` and has one row line; read
   its `format` cell, equal to `ymxs`, and its `version` cell, a whole
   number equal to 3.
3. Verify that the block after `multi` is named `tune`. Each `tune` block
   opens a tune, numbered from 1 in file order, with the blocks after it up
   to the next `tune` block; for each tune, steps 4 to 8.
4. Verify that the `tune` block has one row line; read its `rows` cell, a
   whole number R.
5. Read the blocks of the tune in file order: a `source` block has one row
   line and opens a source with its `name` and `repeat` cells; a `value`
   block follows a `source` block of the tune, each `value` cell of its row
   lines, a whole number, the next value of the last source opened; a
   `registers` block is read as 3.7 and 3.8 define, its `row` cell a whole
   number 0 to R - 1 and each filled `r0` to `r13` cell a whole number; a
   block whose name begins `timer` is set aside for step 6; a block of any
   other name is an error.
6. Read the blocks set aside in file order, named `timerA`, `timerB`,
   `timerC` or `timerD`, each row line as 3.9 and 3.10 define: `row` a whole
   number 0 to R - 1, `shape` 0, 1 or 2, then the cells the shape selects,
   each a whole number as read: for a start `source` (1 to S), `target` (0
   to 13), `prescaler` (one of the seven divisors), `count`, `timerReset`,
   `placeReset`; for a retune the last four.
7. Read the `title`, `composer` and `writer` cells of the `tune` block as
   text, `rate` as a whole number, `repeat` as empty or a whole number.
8. Verify that every source the `source` blocks open is started by some row
   (json.md 4.4).
9. After the last tune, verify the multi against SPEC.md 2 and 3 (5.3).

**4.2** A `title`, `composer`, `writer` or `name` column absent from its
block reads as an empty text; a `repeat` column absent reads as an empty
cell, a table that plays once.

---

## 5. Errors

**5.1** An error of the form is a condition of the text that ends the
reading (4.1), reported as one line, except the last condition below,
reported once for each unstarted source, in `source` block order, one line
each. LINE is the line read, NAME a block's name, CELL the characters of a
cell as 2.3 reads them, a `"` among them as it is, WHAT a column name, N and
K numbers, I and X row numbers, R the row count and S the number of sources.

| condition | the line |
|---|---|
| a heading line with no name | `a table with no name: LINE` |
| a heading line whose name has a `,` in it | `the table name "NAME" has a comma in it: a name stands alone on its line, and the column names on the line after it` |
| a heading line followed by a heading line or by the end of the text | `the "### NAME" table names no columns: the line after the name is the column names` |
| a line that is not blank before the first heading line | `a row before any table opens: LINE` |
| no block, or a first block not named `multi` | `the first table is not "### multi"` |
| a `multi` block of K row lines other than 1 | `the multi table has K rows, and one row opens it` |
| a `format` cell other than `ymxs` | `a text of CELL, and this reads ymxs` |
| a `version` cell other than 3 | `version N, and this reads 3` |
| a block after `multi` and before the first `tune` block | `a "### NAME" table before any tune opens` |
| a `tune` block of K row lines other than 1 | `tune N is opened by K rows, and one row opens it` |
| a `source` block of K row lines other than 1 | `tune N opens a source with K rows, and one row opens it` |
| a `value` block before any `source` block of its tune | `tune N opens values before any source` |
| a `row` cell of a `registers` block outside 0 to R minus 1 | `tune N sets a row at row X, and the tune runs R rows` |
| a block of a tune whose name is none of `source`, `value`, `registers` and a name beginning `timer` | `tune N opens a "### NAME" table, which this form does not have` |
| a block whose name begins `timer` and is not `timerA` to `timerD` | `tune N opens a "### NAME" table, and a timer is timerA to timerD` |
| a `row` cell of a timer block outside 0 to R minus 1 | `tune N sets an effect at row X, and the tune runs R rows` |
| a `shape` cell other than 0, 1 and 2 | `row I sets shape N of an effect, and a shape is 0, 1 or 2` |
| a `source` cell outside 1 to S | `row I starts source N, and the tune runs S` |
| a `target` cell outside 0 to 13 | `no register N: a tune reaches R0 to R13` |
| a `prescaler` cell other than the seven divisors | `no prescaler divides by N: a timer's are 4, 10, 16, 50, 64, 100 and 200` |
| a cell read as a whole number that is not one | `WHAT is "CELL", and this form requires a whole number` |
| a source no row starts | `source N, NAME, is started by no row, and a source a tune does not run is dropped where this form is read` |

**5.2** WHAT in the whole-number line is the column name, `version`, `rows`,
`rate`, `repeat`, `value`, `row`, `r0` to `r13`, `shape`, `source`,
`target`, `prescaler` or `count`, and `true or false` for `timerReset` and
`placeReset`. A reader reads as a whole number: `version`; `rows` and `rate`
of the `tune` block; `repeat` of the `tune` block and of a `source` block
where filled; `value`; `row` of every block that has it; each filled `r0` to
`r13` cell; `shape`; and the cells `shape` selects (3.9). An empty cell read
as a whole number is such an error; an empty `repeat` is a table that plays
once, an empty `r0` to `r13` a register the row leaves unchanged. A
`version`, `rows` or `rate` column absent from its block (2.4) is such an
error at the block's one row line; a `row`, `value` or `shape` column
absent, at the first row line; a `source`, `target`, `prescaler`, `count`,
`timerReset` or `placeReset` column absent from a timer block, at the first
row line whose shape selects it.

**5.3** An error of the structure is a condition of SPEC.md 1.11. A reader
reports every one present after step 9 of 4.1, one line each, the lines of
SPEC.md 1.11 in the order of 1.12, every line of a tune prefixed `tune N: `
as 1.12 defines for a multi, a source's lines at every row that starts it.
The first condition of SPEC.md 1.11 is, in this form, a file whose `tune`
blocks number zero.

**5.4** An emitter stops at the first text of the structure, in the order of
3.1, with a line feed or a carriage return in it, and reports one line: `a
value with a line feed in it, which this form cannot write: ` followed by
the text.

**5.5** A file that breaks a rule of SPEC.md 6 reads into the structure as
any other (json.md 8.4).

---

## 6. Writing

**6.1** An emitter emits the blocks in the order of 3.1, each as its heading
line, `### ` and the name; its column line, the column names of section 3 in
the order listed; and its row lines. A blank line precedes every block but
the first. Every line ends with a line feed, U+000A, the last of the file
included, and the last row line is the last line of the file.

**6.2** An emitter emits a `registers` block in every tune, with a row line
for each row that sets a register, a tune whose rows leave every register
alone having a `registers` block of its column line alone, and a timer
block, with a row line for each row that acts on the timer, where some row
acts on it.

**6.3** An emitter emits a number in decimal, `-` before one below 0 and the
one sign; an empty cell for the `repeat` of a table that plays once, for a
register the row leaves unchanged, and for a part outside the shape's
selection (3.9); and 1 or 0 in `timerReset` and `placeReset`. A cell is
quoted as 2.5 defines.

**6.4** A file an emitter emits reads into the structure and is emitted
again as identical text.

---

## 7. The two forms

**7.1** The two forms encode one structure, and a value present in both
is the same number or text in both:

| json.md | this form |
|---|---|
| `format`, `version` of the file | the `format` and `version` cells of `multi` |
| the length of `tunes` | the `tunes` cell of `multi` |
| the tune at index n minus 1 of `tunes` | the n-th `tune` block and the blocks after it |
| `title`, `composer`, `writer`, `rate`, `rows` of a tune | the cells of those columns in the `tune` block |
| `repeat` of a tune, a number | the `repeat` cell of the `tune` block |
| `repeat` of a tune, `null` | an empty `repeat` cell of the `tune` block |
| the source at index n minus 1 of `sources` | the n-th `source` block of the tune and the `value` block after it |
| `name` and `repeat` of a source | the cells of those columns in the `source` block; `null` is an empty cell |
| the value at index j of `values` | the row line of the `value` block with `row` j and `value` that value |
| the value v at index i of register column `rn`, v other than -1 | the cell `rn` of the row line of `registers` with `row` i |
| -1 at index i of register column `rn` | an empty cell `rn` of the row line with `row` i, or no row line with `row` i where every register column is -1 at i |
| a register column absent from `registers` | an empty cell `rn` in every row line |
| the value v at index i of a column of `timerT`, v other than -1 | the cell of that column of the row line of the `timerT` block with `row` i |
| -1 at index i of a column of `timerT`, `shape` other than -1 at i | an empty cell of that column of the row line with `row` i |
| -1 at index i of `shape` of `timerT` | no row line with `row` i in the `timerT` block |
| `timerT` absent | no `timerT` block |

**7.2** A file of either form, read and emitted in the other, then read
and emitted in the first, is the text it was, except a file whose title,
composer, writer or source name has a line feed or a carriage return in
it, which the emitter of this form reports (5.4).

---

## 8. The example

**8.1** `doc/tunes/example.csv`, informative: the tune of json.md 10 in
this form.

```
### multi
format,version,tunes
ymxs,3,1

### tune
title,composer,writer,rate,rows,repeat
"Four rows, one square",,by hand,50,4,0

### source
name,repeat
square 13,0

### value
row,value
0,13
1,0

### registers
row,r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13
0,163,2,238,,,,,56,,,,,,
1,142,12,,,,,,49,,,,,,
2,251,4,,,,,,,,,,,,
3,89,2,,,,,,56,12,,,,,

### timerA
row,shape,target,source,prescaler,count,timerReset,placeReset
0,0,8,1,50,60,1,1
1,1,,,50,61,0,0
3,2,,,,,,
```

**8.2** The `title` cell is quoted for the `,` in it. Row 2 sets R0 and R1
and leaves every timer alone, so it has a row line in `registers` and its
row number is absent from `timerA`. Row 1 is a retune, so its `target` and
`source` cells are empty; row 3 is a stop, so every cell but `row` and
`shape` is empty.
