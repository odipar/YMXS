# The text form

JSON, and one way of writing the structure down. The structure is the
records under `src/main/java/org/ymxs/`; nothing here is in them, and a
second form would change none of them.

[The table form](csv.md) holds the same tune as rows rather than columns,
for a reader who would rather open one in a spreadsheet.

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
tune.** A row is what every column holds at that place, so nothing has to
be counted to find one and no entry holds a number for where it stands.

A column that no row fills is left out: a register no row sets has no
column, and a timer no row states has none.

## A tune

| key | gives |
|---|---|
| `title`, `composer`, `writer` | text, empty where none is given |
| `rate` | how often the player is called for this tune, in Hz |
| `frames` | how many rows the tune has |
| `repeat` | the row it repeats to, or `null` for a tune that plays once |
| `sources` | the sources its rows start, in the order a row first starts each |
| `rows` | a column a register |
| `timerA` to `timerD` | a column a part of the effect on that timer |

`frames` says how long every column is, and a reader holds them to it.

## The registers

`rows` holds a column a register, `r0` to `r13`, each one value a row.
**-1 stands where the row does not set that register**, and no register
takes -1 as a value.

| key | sets | takes |
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

A timer is a structure of its own, `timerA` through `timerD`, since a row
may state an effect on all four. Each holds seven columns, one value a
row, and **-1 stands where a row states nothing**, as it does in a
register's column.

| column | holds |
|---|---|
| `shape` | 0 a start, 1 a retune, 2 a stop, -1 nothing |
| `target` | 0 to 13, which is `setR0` to `setR13` |
| `source` | 1 upward into the tune's `sources` |
| `prescaler` | one of the seven a timer divides by: 4, 10, 16, 50, 64, 100, 200 |
| `count` | 1 to 256 |
| `timerReset`, `placeReset` | 1 true, 0 false |

A part a shape does not hold is -1 too: a retune holds no target and no
source, and a stop holds none of the six.

## A source

| key | gives |
|---|---|
| `name` | what a writer calls it, empty where none is given |
| `repeat` | the row it repeats to, or `null` for one that plays once |
| `values` | its rows, one value a row |

## The layout

One column a line, wrapped at twenty values. `Json` maps the structure to
a JSON tree and back, `Layout` says where the lines break, and a JSON
library does the escaping, the parsing and the writing. A reader takes any
JSON of this shape.

## What is turned away

| the text | why |
|---|---|
| a `format` that is not `ymxs` | it is another form |
| a `version` this does not read | it is another version |
| a column that is not as long as `frames` | a column stands one value a row |
| a `shape` that is none of the three | there are three |
| a source number the tune does not hold | it reaches no source |

Everything else a record turns away where it is made: a register value
past what the register takes, a count outside 1 to 256, a source value
past what its target takes, a repeat row past the last row.
