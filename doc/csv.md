# CSV

The structure encoded as tables, for reading in a spreadsheet. The
two forms write the same tune, [JSON](json.md) by columns and this by
rows, and a filled cell is identical in both.

`doc/tunes/example.csv`, the tune json.md shows:

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

## How a file reads

- A line beginning `###` opens a table and names it, and the name stands
  alone on that line.
- The line after it names the columns, so each name stands over the
  cells it names.
- Every line after that is one row of the table, in ordinary
  comma-separated values, until the next `###` line.
- A blank line is skipped.
- A cell is quoted where it has a comma or a quote in it, and two quotes
  inside a quoted cell are one.
- A column is found by its name, so the columns of a table may stand in
  any order.

**Position determines what a table belongs to.** A tune opens with a
`tune` table, and the tables after it belong to that tune until the next
`tune` table. A source opens with a `source` table, and the `value` table
after it belongs to that source. A table therefore needs no column for
its tune or its source.

## The tables

| table | its rows |
|---|---|
| `multi` | one: `format`, `version` and the number of tunes |
| `tune` | one: `title`, `composer`, `writer`, `rate`, `rows` and `repeat`; it opens a tune |
| `source` | one: `name` and `repeat`; it opens a source, the sources in first-start order |
| `value` | one a value of the source before it: `row` and `value` |
| `registers` | one a row of the tune that sets a register: `row`, then `r0` to `r13` |
| `timerA` to `timerD` | one a row of the tune that acts on that timer: `row`, then the seven columns of [json.md](json.md) |

**An empty cell is what -1 is in JSON.** In `registers` it is a register
the row leaves alone; in a timer's table, a part absent from the shape,
`target` and `source` of a retune and every part of a stop; in `repeat`,
a table that plays once. The `row` column is the row number, and a row
that sets no register or acts on no timer is absent from the table, so
`rows` in the `tune` table is the count.

**Each source a tune runs opens a `source` table and a `value` table**,
in first-start order, so the `source` cells of the timer tables number
the `source` tables from 1.

**Each timer some row acts on opens a table**, `timerA` to `timerD`,
with the cells json.md defines: `shape` 0 a start, 1 a retune, 2 a stop;
`target` 0 to 13; `source` 1 upward; `prescaler` the divisor; `count` 0
to 255; `timerReset` and `placeReset` 1 and 0.

## What is an error

| the text | why |
|---|---|
| a cell with a line feed in it | quoting covers a comma and a quote, and a line feed reads as the end of a row |
| a `###` line with a comma in it | the form this replaced, which put the name and the columns on one line |
| a `###` line with no line after it | a table whose name is its last line |
| a first table other than `multi` | the file is another form |
| a `format` other than `ymxs`, or a `version` other than 3 | another form, or another version |
| a `###` name outside the tables above | such a table is absent from this form |
| a `value` table with no `source` table before it, or a timer table before the tune's `source` tables | position determines what a table belongs to |
| a cell outside its column's values | the cell fits no field |

The remaining errors are those of the structure, as in
[json.md](json.md).

## Between the two forms

```bash
bin/ymxs-json-to-csv < tune.json > tune.csv
bin/ymxs-csv-to-json < tune.csv > tune.json
```

A tune crosses from either form to the other and back to the text it
was. `doc/tunes/example.json` and `doc/tunes/example.csv` are one tune,
and `circus.json` and `circus.csv` another; the tests read each pair into
one structure.
