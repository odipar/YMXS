# The text form

JSON, and one way of writing the structure down. The structure is the
records under `src/main/java/org/ymxs/`; nothing here is in them, and a
second form would change none of them.

It holds the same tables [the table form](csv.md) holds, so a reader that
has one has the other.

```json
{
  "format": "ymxs",
  "version": 1,
  "tunes": [
    {
      "title": "Synthetic",
      "composer": "Test",
      "writer": "ym-to-ymxs",
      "rate": 50,
      "frames": 400,
      "repeat": 0,
      "sources": [
        {"name": "square 13", "repeat": 0, "values": [13,0]},
        {"name": "recording 0", "repeat": null, "values": [8,9,10,11]}
      ],
      "rows": [
        {"row": 0, "r0": 64, "r1": 1, "r7": 56, "r9": 12},
        {"row": 1, "r0": 65, "r2": 49, "r6": 1}
      ],
      "effects": [
        {"row": 0, "timer": "A", "shape": "start", "target": "setR8",
         "source": 1, "prescaler": 50, "count": 60,
         "timerReset": true, "placeReset": true},
        {"row": 1, "timer": "A", "shape": "retune", "prescaler": 50,
         "count": 61, "timerReset": false, "placeReset": false}
      ]
    }
  ]
}
```

**Everything states where it stands.** A row says which row it is and an
effect says which row it is on, so nothing is folded into runs and no
count is carried from one entry to the next. What a reader has to do to
make one of these is put down what it knows, one entry at a time.

## A tune

| key | gives |
|---|---|
| `title`, `composer`, `writer` | text, empty where none is given |
| `rate` | how often the player is called for this tune, in Hz |
| `frames` | how many rows the tune has |
| `repeat` | the row it repeats to, or `null` for a tune that plays once |
| `sources` | the sources its rows start, in the order a row first starts each |
| `rows` | the rows that set a register |
| `effects` | what its rows state of the effects |

`frames` is stated because a row that sets nothing is no entry in `rows`.

## A row

`row` is which row it is, and every key after it is a register that row
sets: `r0` to `r13`, each holding the value that register takes.

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

A register with no key is one that row does not write.

## An effect

`row` is which row states it and `timer` which of `A`, `B`, `C` and `D`
it states it against. `shape` is one of three, and what it holds after
that is that shape's:

| shape | holds |
|---|---|
| `start` | `target`, `source`, `prescaler`, `count`, `timerReset`, `placeReset` |
| `retune` | `prescaler`, `count`, `timerReset`, `placeReset` |
| `stop` | nothing |

`target` is the target's own name, `setR0` to `setR13`. `source` is a
number, 1 upward into the tune's `sources`. `prescaler` is one of the
seven a timer divides by: 4, 10, 16, 50, 64, 100 or 200. `count` is 1 to
256.

## A source

| key | gives |
|---|---|
| `name` | what a writer calls it, empty where it calls it nothing |
| `repeat` | the row it repeats to, or `null` for one that plays once |
| `values` | its rows, one value a row |

## The layout

One row a line, one effect a line, and a source's values wrapped at
twenty. `Json` maps the structure to a JSON tree and back, `Layout` says
where the lines break, and a JSON library does the escaping, the parsing
and the writing. A reader takes any JSON of this shape.

## What is turned away

| the text | why |
|---|---|
| a `format` that is not `ymxs` | it is another form |
| a `version` this does not read | R6.1 |
| a row or an effect past `frames` | the tune holds no such row |
| a `shape` that is none of the three | there are three |
| a source number the tune does not hold | it names nothing |

Everything else a record turns away where it is made: a register value
past what the register takes, a count outside 1 to 256, a source value
past what its target takes, a repeat row past the last row.
