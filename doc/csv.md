# The table form

A tune written as a table a structure, for a reader who would rather open
one in a spreadsheet than in an editor. It holds what
[the text form](text.md) holds, and either reads into the same structure.

```
class;multi
format###version###tunes
ymxs###1###1

class;tune
tune###title###composer###writer###rate###rows###repeat
1###Circus Attractions #2###Mad Max###ym-to-ymxs###50###4###0

class;source
tune###source###name###repeat###values

class;run
tune###register###gap###values
1###r0###0###163###142###251###89
1###r7###0###56###49
1###r7###1###56

class;start
tune###row###timer###target###source###prescaler###count###timerReset###placeReset

class;retune
tune###row###timer###prescaler###count###timerReset###placeReset

class;stop
tune###row###timer
```

## How it reads

- Fields are held apart by `###`.
- A line beginning `class;` names the structure whose rows come next.
- The line after it names the fields.
- Every line after that is one row of that structure, until the next
  `class;` line.
- A blank line is nothing.

The blocks come in the order above, and every one of them is written even
where it holds no rows.

`tune` is the tune's number, 1 upward, so a multi of several tunes holds
one block of each structure with a column saying which tune a row belongs
to.

## What a block holds

| block | one row a |
|---|---|
| `multi` | file: what it is, its version, and how many tunes it holds |
| `tune` | tune |
| `source` | source, in the order a row first starts it |
| `run` | stretch of rows that all set one register |
| `start`, `retune`, `stop` | effect a row states, one block a shape |

**A run** is a stretch of rows that all set one register. `gap` is the rows
between the end of the run before it and its own first row, so the first
run of a register states the row it begins on and every run after it
states a gap. `values` is one value a row from there on.

**A source's `values`** are its rows, one a field.

Both take as many fields as they have values, so the last field a header
names takes the rest of the line.

**`repeat`** is the row a tune or a source repeats to, and an empty field
is one that plays once.

## What it will not hold

A value holding `###` or a line feed, since either would read as
something else. A title like that is turned away rather than written and
misread.

## Between the two forms

```bash
bin/ymxs-json-to-csv < tune.json > tune.csv
bin/ymxs-csv-to-json < tune.csv > tune.json
```

`doc/tunes/circus.csv` is one tune in this form and
`doc/tunes/circus.json` the same tune in the other, and a test holds the
two to one structure.
