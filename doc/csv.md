# The table form

A tune as tables, for a reader who would rather open one in a spreadsheet
than in an editor. It holds what [the text form](text.md) holds, and
either reads into the same structure.

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

### row,row,r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13
0,64,1,48,2,32,3,0,56,,12,11,0,0,
1,65,,49,,33,,1,,,,,3,,

### timer0,row,shape,target,source,prescaler,count,timerReset,placeReset
0,0,8,1,50,60,1,1
1,1,,,50,61,0,0
59,2,,,,,,
```

## How it reads

- A line beginning `###` names a table and then its columns.
- Every line after it is one row of that table, in ordinary
  comma-separated values, until the next such line.
- A blank line is nothing.
- A cell is quoted where it holds a comma or a quote, and two quotes
  inside a quoted cell stand for one.
- A column is found by its name, so the order the columns come in is the
  file's own.

**These are ordinary tables.** A row of a tune is a row here, with a
column a register. [The text form](text.md) holds the same tune the other
way round, a column at a time, and what a cell holds where it holds
something is the same in both: the same numbers, and a shape, a target and
a timer as the same enumerations.

**Where a table stands says what it belongs to.** A tune opens with its
own table, and the tables after it are that tune's until the next tune
opens. A source does the same for the values after it. So no table names
which tune or which source a row belongs to, and a tune's tables read as
one run of them.

## What each table holds

| table | holds |
|---|---|
| `multi` | one row: what the file is, its version, and how many tunes it holds |
| `tune` | one row, and it opens a tune |
| `source` | one row, and it opens a source, in the order a row first starts it |
| `value` | one row a value of the source it comes after |
| `row` | one row a row of the tune that sets a register |
| `timer0` to `timer3` | one row an effect a row states on that timer |

**An empty cell in `row`** is a register that row does not set. A row that
sets none is no row of the table: the `row` column says which row a line
is, and `frames` in the `tune` table says how many the tune has.

**A tune that runs no source** opens no `source` table and no `value`
table.

**A timer opens a table of its own**, `timer0` for Timer A through
`timer3` for Timer D, and a timer no row states opens none. What its cells
hold is what [the text form](text.md) holds: `shape` 0 a start, 1 a
retune, 2 a stop; `target` 0 to 13 for `setR0` to `setR13`; `source` 1
upward into the tune's sources; `timerReset` and `placeReset` 1 and 0.

**An empty cell there** is a part that shape does not hold, where the text
form says -1. A retune holds no target and no source; a stop holds none of
the six.

**`repeat`** is the row a tune or a source repeats to, and an empty cell
is one that plays once.

## What it will not hold

A cell holding a line feed. A comma and a quote are held by quoting; a
line feed would read as the end of a row, so a title with one is turned
away rather than written and misread.

## Between the two forms

```bash
bin/ymxs-json-to-csv < tune.json > tune.csv
bin/ymxs-csv-to-json < tune.csv > tune.json
```

`doc/tunes/circus.csv` is one tune in this form and
`doc/tunes/circus.json` the same tune in the other, and a test holds the
two to one structure.
