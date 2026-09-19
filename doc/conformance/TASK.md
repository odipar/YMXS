# The task

Implement a recorder of a structure from the specification.

**1. The documents.** `SPEC.md` in this directory defines the structure:
what a tune is, what each register reaches, how a rate is reckoned, what a
frame does with a row, and the record of section 7. `json.md` beside it
defines the form the tunes of this kit are written in. Read both.

**2. What to produce.** `record.py` in this directory, run as

    python3 record.py tunes/NAME.json T

It prints the record `SPEC.md` 7 defines for tune T of that file, T being
1 for the first tune: the first line of 7.3, then one line a frame (7.4),
ending with a line feed. The lines are as many as the table below has,
the count 7.5 defines where a host names none.

The output is compared with the reference byte for byte: each line as 7.2
lays it out, integers in decimal, the keys of each object in the order
section 7 lists them, and a line feed ending each line. In Python that is
`json.dumps(entry, separators=(",", ":"))` over dicts filled in that
order, `sort_keys` left at its default.

**3. The tunes.** Each is a `.json` under `tunes/`; `SOURCES.md` lists
what each reaches and stands outside a run against this kit, since the
record of a tune beside it has the lines a recorder produces. A tune of
this kit satisfies every rule of `SPEC.md` 6, and two raise a warning a
checker reports: one sets a register an effect runs on, which 6.1 records
in the frame, and one wraps with two timers running.

| file | the tune of it | lines |
|---|---|---:|
| `four-rows.json` | 1 | 9 |
| `one-row.json` | 1 | 3 |
| `plays-once.json` | 1 | 6 |
| `four-timers.json` | 1 | 13 |
| `sources.json` | 1 | 11 |
| `registers.json` | 1 | 7 |
| `wrap.json` | 1 | 9 |
| `several.json` | 1 | 9 |
| `multi.json` | 2 | 6 |

**4. The rules.**

- `SPEC.md` and `json.md` define the format. A reader of this kit reads
  the tunes those two define and produces the record of section 7.
- The lines a reader writes are compared with the reference whole. A line
  that differs in one byte fails the tune.
- How fast a recorder runs, and how it is called, stand outside this kit.
