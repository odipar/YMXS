# JSON

The structure written down as JSON. The structure is the records
[SPEC.md](SPEC.md) 1 lists; this document defines a serialisation of
them. [CSV](csv.md) writes the same tune as tables, for reading in a
spreadsheet, and a filled cell is identical in both forms.

`doc/tunes/example.json`, a tune of four rows and one effect:

```json
{
  "format": "ymxs",
  "version": 3,
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

**A tune is written column by column, and every column is as long as
the tune.** A row is one index across every column, so a file lists
`rows` once and a row number appears in no column.

**-1 marks a value the row leaves alone.** A register's column is -1
where the row leaves that register as it was, and a timer's columns are
-1 where the row leaves that timer as it was. -1 fits neither a register
nor a part of an effect, so it is unambiguous.

**A column appears where some row fills it**: a register some row sets, a
timer some row acts on. The example has five register columns of the
fourteen and one timer of the four.

## The file

| key | value |
|---|---|
| `format` | `ymxs` |
| `version` | 3, the version of the structure |
| `tunes` | the tunes, one object each, numbered from 1 in this order |

## A tune

| key | value |
|---|---|
| `title`, `composer`, `writer` | text, empty where absent |
| `rate` | the frames a second the player is called at |
| `rows` | the row count, the length of every column |
| `repeat` | the row the tune repeats to, or `null` for a tune that plays once |
| `sources` | the sources its rows start, in first-start order, numbered from 1 in this order |
| `registers` | a column a register |
| `timerA` to `timerD` | an object a timer, a column a part of the operation |

## The registers

`registers` is a column a register, `r0` to `r13`, one value a row: a
value in the register's range, or -1.

| key | sets | range |
|---|---|---|
| `r0`, `r2`, `r4` | a voice's tone period, the low byte | 0 to 255 |
| `r1`, `r3`, `r5` | a voice's tone period, the high four bits | 0 to 15 |
| `r6` | the noise period | 0 to 31 |
| `r7` | mixing, six bits | 0 to 63 |
| `r8`, `r9`, `r10` | a voice's volume | 0 to 31 |
| `r11`, `r12` | the envelope period, the low byte and the high | 0 to 255 |
| `r13` | the envelope shape | 0 to 15 |

## The effects

Each timer is a separate object, `timerA` to `timerD`, since one row may
act on all four. Each has seven columns, one value a row:

| column | value |
|---|---|
| `shape` | 0 a start, 1 a retune, 2 a stop, -1 where the row leaves the timer alone |
| `target` | 0 to 13, the target `setR0` to `setR13` |
| `source` | 1 upward, into the tune's `sources` |
| `prescaler` | the divisor: 4, 10, 16, 50, 64, 100 or 200 |
| `count` | 0 to 255, the timer's data register, where 0 counts 256 |
| `timerReset`, `placeReset` | 1 true, 0 false |

A part absent from the shape is -1: `target` and `source` for a retune,
all six for a stop. In the example, row 1 is a retune and row 3 a stop.

## A source

| key | value |
|---|---|
| `name` | text, empty where absent; it appears in the tools' reports alone |
| `repeat` | the row the source repeats to, or `null` for one that plays once |
| `values` | its rows, one value a row, each within the range of its target's register |

## The layout

The layout is for reading, and a reader reads any layout of this shape.
A tune's figures stand at the top of the tune, each column stands on one
line, and a column of more than twenty values wraps after every
twentieth. The tools write this layout, so a file written and read back
is the text it was.

## What is an error

| the JSON | why |
|---|---|
| a `format` other than `ymxs` | another form |
| a `version` other than 3 | another version |
| a column whose length differs from `rows` | a column is one value a row |
| a `shape` outside 0, 1 and 2 | three shapes exist |
| a `source` outside the tune's `sources` | such a source is absent |
| a source in `sources` that no row starts | the structure reaches a source through the row that starts it, so the source would be lost where the file is read |
| a number past what a 32-bit integer reads | such a value fits no field |

The remaining errors are those of the structure, reported where the
record is constructed: a register value outside its range, a count
outside 0 to 255, a rate above 125,000 ticks a second, a source value
outside its target's range, a repeat row past the last row.
