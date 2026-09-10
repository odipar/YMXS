# CSV

A tune as tables, for a reader who would rather open one in a spreadsheet
than in an editor.

```
### multi,format,version,tunes
ymxs,1,1

### tune,title,composer,writer,rate,frames,repeat
Synthetic,Test,ym-to-ymxs,50,400,0

### source,name,repeat
square 13,0

### value,row,value
0,13
1,0

### source,name,repeat
recording 0,

### value,row,value
0,8
1,9

### rows,row,r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13
0,64,1,48,2,32,3,0,56,,12,11,0,0,
1,65,,49,,33,,1,,,,,3,,

### timerA,row,shape,target,source,prescaler,count,timerReset,placeReset
0,0,8,1,50,60,1,1
1,1,,,50,61,0,0
59,2,,,,,,
```

## How it reads

- A line beginning `###` opens a table and names its columns.
- Every line after it is one row of that table, in ordinary
  comma-separated values, until the next such line.
- A blank line is skipped.
- A cell is quoted where it has a comma or a quote, and two quotes
  inside a quoted cell stand for one.
- A column is found by its name, so the order the columns come in is the
  file's own.

**These are ordinary tables.** A row of a tune is a row here, with a
column a register. [JSON](json.md) writes the same tune the other way
round, a column at a time, and a filled cell reads the same in both: the
same numbers, and a shape, a target and a timer as the same enumerations.

**Where a table stands says what it belongs to.** A tune opens with its
own table, and the tables after it are that tune's until the next tune
opens. A source does the same for the values after it. So a table needs
no column for its tune or its source, and a tune's tables stand
together.

## The tables

| table | its rows |
|---|---|
| `multi` | one row: what the file is, its version, and the number of tunes |
| `tune` | one row, and it opens a tune |
| `source` | one row, and it opens a source, in the order a row first starts it |
| `value` | one row a value of the source it comes after |
| `rows` | one row a row of the tune that sets a register |
| `timerA` to `timerD` | one row an effect on that timer |

**An empty cell in `rows`** is a register that row does not set. Only a
row that sets a register is a line of the table: the `row` column says
which row a line is, and `frames` in the `tune` table says how many there
are.

**A `source` table and a `value` table** stand for each source a tune
runs.

**A timer any row uses opens a table of its own**, `timerA` through
`timerD`. Its cells read as [JSON](json.md) reads them: `shape` 0 a
start, 1 a retune, 2 a stop; `target` 0 to 13 for `setR0` to `setR13`;
`source` 1 upward into the tune's sources; `timerReset` and `placeReset`
1 and 0.

**An empty cell there** is a part that shape leaves out, where JSON says
-1. A retune leaves `target` and `source` empty; a stop leaves all six.

**`repeat`** is the row a tune or a source repeats to, and an empty cell
is one that plays once.

## What is an error

A cell with a line feed in it. Quoting covers a comma and a quote; a line
feed would read as the end of a row, so a title with one is an error
rather than written and misread.

## Between the two forms

```bash
bin/ymxs-json-to-csv < tune.json > tune.csv
bin/ymxs-csv-to-json < tune.csv > tune.json
```

`doc/tunes/circus.csv` is one tune in this form and
`doc/tunes/circus.json` the same tune in the other, and a test reads the
two into the same structure.
