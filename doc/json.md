# JSON

One way of writing the structure down. The structure is the records under
`src/main/java/org/ymxs/`; this document defines a serialisation of them
and no part of the structure itself.

[CSV](csv.md) writes the same tune as rows rather than columns, for
reading in a spreadsheet.

```json
{
  "format": "ymxs",
  "version": 1,
  "tunes": [
    {
      "title": "Circus Attractions #2",
      "composer": "Mad Max",
      "writer": "ym-to-ymxs",
      "rate": 50,
      "frames": 4,
      "repeat": 0,
      "sources": [
        {"name": "square 13", "repeat": 0, "values": [13,0]}
      ],
      "rows": {
        "r0": [163,142,251,89],
        "r1": [2,12,4,2],
        "r2": [238,-1,-1,-1],
        "r7": [56,49,-1,56]
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

**A tune is written column by column, and every column is as long as the
tune.** A row is one index across every column, so no row number appears
in the file.

A column appears only where some row fills it: a register some row sets, a
timer some row acts on.

## A tune

| key | what it is |
|---|---|
| `title`, `composer`, `writer` | text, empty where absent |
| `rate` | how often the player is called for this tune, in Hz |
| `frames` | how many rows the tune has |
| `repeat` | the row it repeats to, or `null` for a tune that plays once |
| `sources` | the sources its rows start, in first-start order |
| `rows` | a column a register |
| `timerA` to `timerD` | a column a part of the effect on that timer |

`frames` is the length of every column, and a reader verifies each column
against it.

## The registers

`rows` is a column a register, `r0` to `r13`, each one value a row.
**-1 stands where the row does not set that register.** No register value
is -1, so -1 is unambiguous in a column.

| key | sets | range |
|---|---|---|
| `r0`, `r2`, `r4` | a voice's tone period, fine | 0 to 255 |
| `r1`, `r3`, `r5` | a voice's tone period, coarse | 0 to 15 |
| `r6` | the noise period | 0 to 31 |
| `r7` | mixing | 0 to 63 |
| `r8`, `r9`, `r10` | a voice's volume | 0 to 31 |
| `r11` | the envelope period, fine | 0 to 255 |
| `r12` | the envelope period, coarse | 0 to 255 |
| `r13` | the envelope shape | 0 to 15 |

## The effects

Each timer is a separate object, `timerA` through `timerD`, since one row
may act on all four. Each has seven columns, one value a row, and **-1
stands where a row leaves that timer alone**, as in a register's
column.

| column | what it is |
|---|---|
| `shape` | 0 a start, 1 a retune, 2 a stop, -1 none |
| `target` | 0 to 13, which is `setR0` to `setR13` |
| `source` | 1 upward into the tune's `sources` |
| `prescaler` | one of the seven a timer divides by: 4, 10, 16, 50, 64, 100, 200 |
| `count` | 1 to 256 |
| `timerReset`, `placeReset` | 1 true, 0 false |

A part absent from a shape is -1: `target` and `source` for a retune, all
six for a stop.

## A source

| key | what it is |
|---|---|
| `name` | the source name, empty where absent |
| `repeat` | the row it repeats to, or `null` for one that plays once |
| `values` | its rows, one value a row |

## The layout

One column a line, wrapped at twenty values. `Json` maps the structure to
a JSON tree and back, `Layout` fixes the line breaks, and a JSON library
performs the escaping, the parsing and the writing. Any JSON of this shape
is valid input.

## What is an error

| the JSON | why |
|---|---|
| a `format` that is not `ymxs` | it is another form |
| a `version` this does not read | it is another version |
| a column whose length is not `frames` | a column is one value a row |
| a `shape` outside 0, 1 and 2 | there are three shapes |
| a source number outside the tune's `sources` | there is no such source |

The remaining errors are rejected where the record is constructed: a
register value outside its range, a count outside 1 to 256, a source value
outside its target's range, a repeat row past the last row.
