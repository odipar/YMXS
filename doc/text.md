# The text form

JSON, and one way of writing the structure down. The structure is the
records under `src/main/java/org/ymxs/`; nothing here is in them, and a
second form would change none of them.

```json
{
  "format": "ymxs",
  "version": 1,
  "tunes": [
    {
      "title": "world completed 1",
      "composer": "Jochen Hippel",
      "writer": "ym-to-ymxs",
      "rate": 50,
      "rows": 179,
      "repeat": 0,
      "sources": [
        {"name": "drum 0", "repeat": null, "rows": [8, 12, 15, 13]}
      ],
      "r0": [
        [0, 123],
        [3, [119, 115]],
        [1, [119, 169]]
      ],
      "r8": [
        [0, [14, 13]],
        [4, [12, 11, 14, 13]]
      ],
      "effects": [
        [0, {"D": {"start": {"target": "setR10", "source": 1, "prescaler": 4,
                             "count": 102, "timerReset": true,
                             "placeReset": true}}}],
        [3, {"D": {"stop": {}}}]
      ]
    }
  ]
}
```

## A tune

| key | gives |
|---|---|
| `title`, `composer`, `writer` | text, empty where none is given |
| `rate` | how often the player is called for this tune, in Hz |
| `rows` | how many rows the tune has |
| `repeat` | the row it repeats to, or `null` for a tune that plays once |
| `sources` | the sources, numbered 1 upward in this order |
| `r0` to `r13` | one register's stream, absent where no row sets it |
| `effects` | what the rows state of the effects |

`rows` is stated because a tune is written stream by stream, and a tune
whose last rows set nothing still plays them.

## A stream

`r0` to `r13`, each a list of runs. A run is a stretch of rows that all
set that register:

| a run | gives |
|---|---|
| `[3, 119]` | one row, three rows past the end of the run before it |
| `[3, [119, 115]]` | two rows, the first three rows past that end |

The number before the values is the rows between the end of the last run
and the start of this one, so the first run's number is its own row. The
values of a run are consecutive rows, one a row.

A register absent from the row a run reaches is one that row does not
write, which is the structure's own rule and needs nothing of this form
to state it.

| stream | sets | takes |
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

`effects` is a list of events, each the row it stands on and the timers
that row states an effect against. The keys are `A`, `B`, `C` and `D`,
the timers themselves. The rows ascend, and a row stating no effect has
no entry.

An event states its own row rather than a gap. A stream is read for its
shape and an event for where it falls, and there are few events beside
the rows.

Each timer's value states one of three things:

| the shape | holds |
|---|---|
| `start` | `target`, `source`, `prescaler`, `count`, `timerReset`, `placeReset` |
| `retune` | `prescaler`, `count`, `timerReset`, `placeReset` |
| `stop` | nothing |

`target` is the target's own name, `setR0` to `setR13`. `source` is a
number, 1 upward into the tune's `sources`. `prescaler` is one of the
seven a timer divides by: 4, 10, 16, 50, 64, 100 or 200. `count` is 1 to
256.

A shape states the whole of what the effect is from that row on. A
`start` states its target and its rate where the effect already ran at
them, and a `retune` the rate where only the count moved. Writing only
the parts that moved is a form's work, and this form does not do it.

## A source

| key | gives |
|---|---|
| `name` | what a writer calls it, empty where it calls it nothing |
| `repeat` | the row it repeats to, or `null` for one that plays once |
| `rows` | the values, one a tick |

## The layout

A form writes one run a line and one event a line, and wraps a run's
values at twenty. That is what `Text` writes and what the tunes under
`doc/tunes` hold; a reader takes any JSON of this shape.

## What is turned away

| the text | why |
|---|---|
| a `format` that is not `ymxs` | it is another form |
| a `version` this does not read | R6.1 |
| a run or an event past `rows` | the tune holds no such row |
| an effect stating other than one of the three shapes | there are three |
| a source number the tune does not hold | it names nothing |

Everything else a record turns away where it is made: a register value
past what the register takes, a count outside 1 to 256, a source value
past what its target takes, a repeat row past the last row.
