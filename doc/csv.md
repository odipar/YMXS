# CSV

A tune as tables, for reading in a spreadsheet.

```
### multi
format,version,tunes
ymxs,2,1

### tune
title,composer,writer,rate,rows,repeat
Synthetic,Test,ym-to-ymxs,50,400,0

### source
name,repeat
square 13,0

### value
row,value
0,13
1,0

### source
name,repeat
recording 0,

### value
row,value
0,8
1,9

### registers
row,r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13
0,64,1,48,2,32,3,0,56,,12,11,0,0,
1,65,,49,,33,,1,,,,,3,,

### timerA
row,shape,target,source,prescaler,count,timerReset,placeReset
0,0,8,1,50,60,1,1
1,1,,,50,61,0,0
59,2,,,,,,
```

## How it reads

- A line beginning `###` opens a table and names it, and the name stands
  alone on that line.
- The line after it names the columns, so a column name stands over the
  cells it names and a reader counts the columns by reading down.
- Every line after that is one row of the table, in ordinary
  comma-separated values, until the next `###` line.
- A blank line is skipped.
- A cell is quoted where it has a comma or a quote, and two quotes
  inside a quoted cell stand for one.
- A column is found by name, so column order follows the file.

**These are ordinary tables.** A row of a tune is a row here, with a
column a register. [JSON](json.md) writes the same tune the other way
round, a column at a time, and a filled cell is identical in both: the
same numbers, and the same enumerations for a shape, a target and a timer.

**A row is one entry of the tune's table, and a frame is one call of the
player.** The table advances one row a frame, so this file records rows
and counts no frames ([SPEC.md](SPEC.md) 4).

**Position determines what a table belongs to.** A tune opens with a
`tune` table, and the tables after it belong to that tune until the next
`tune` table. A source opens with a `source` table, and the values after
it belong to that source. A table therefore needs no column for its tune
or its source, and the tables of one tune stand together.

## The tables

| table | its rows |
|---|---|
| `multi` | one row: what the file is, its version, and the number of tunes |
| `tune` | one row, and it opens a tune |
| `source` | one row, and it opens a source, in first-start order |
| `value` | one row a value of the source it comes after |
| `registers` | one row a row of the tune that sets a register |
| `timerA` to `timerD` | one row an effect on that timer |

**An empty cell in `registers`** is a register that row does not set.
Only a row that sets a register is a line of the table: the `row` column
is the row number, and `rows` in the `tune` table is the count.

**Each source a tune runs opens a `source` table and a `value` table.**

**Each timer a row uses opens a table**, `timerA` through `timerD`. The
cells are the values [JSON](json.md) defines: `shape` 0 a start, 1 a
retune, 2 a stop; `target` 0 to 13 for `setR0` to `setR13`; `source` 1
upward into the tune's sources; `timerReset` and `placeReset` 1 and 0.

**An empty cell there** is a part absent from that shape, written as -1 in
JSON: `target` and `source` for a retune, all six for a stop.

**`repeat`** is the row a tune or a source repeats to, and an empty cell
marks one that plays once.

## What is an error

A cell containing a line feed. Quoting covers a comma and a quote; a line
feed would read as the end of a row, so a title containing one is rejected
rather than written and misread.

A `###` line with a comma in it, and a `###` line with no line after it.
The first is a file of the form this replaced, where the name and the
columns stood on one line; the second is a table whose name is its last
line.

## Between the two forms

```bash
bin/ymxs-json-to-csv < tune.json > tune.csv
bin/ymxs-csv-to-json < tune.csv > tune.json
```

`doc/tunes/circus.csv` is one tune in this form and
`doc/tunes/circus.json` the same tune in JSON. A test reads the two into
the same structure.
