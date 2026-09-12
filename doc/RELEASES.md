# Releases

What a release contains stands here, and each one published is listed
below it.

## What a release contains

`release/publish.sh` writes `dist/release` (tools.md), and
`release/manifest.sh` the manifest in it:

- one zip a platform, over six: Windows, macOS and Linux, each on x64 and
  arm64. A zip contains the five tools as executables, and one runs with
  no Java installed
- `MANIFEST.txt`: every zip's size and sha256, what it contains, and the
  source commit the release was built from

The version names every file. It is read out of `pom.xml`, or stands as
the script's one argument.

The Go tree is a module of its own, `github.com/odipar/ymxs/go`, and a
version of it is a tag of that directory: `go/v0.1.0` beside `v0.1.0`.

A player pins a version of this format: the structure's version is 1 and
stands in every file this writes, and a release's number names the tools
rather than the format.

## Published

### 0.3.1, 2026-09-12

<https://github.com/odipar/YMXS/releases/tag/v0.3.1>, built from the commit
tagged `v0.3.1`.

Every tool reads a tune the way `ymxs-check` does. The structure's version
is 3, as 0.3.0 set it, so a file of this release reads under that one and
the other way round.

- **Every tool that reads a tune reports the warnings of SPEC.md 6**, not
  `ymxs-check` alone: a fault a writer left in is named where the tune is
  used rather than only where it is checked. A warning stands whether or
  not `-silent` was passed, since that flag quiets what a tool reports of
  its work.
- **The rows of one run of rule 1 are one line.** A row that sets a
  register an effect runs on reported a line a row, so a run of two
  hundred rows reported two hundred warnings. It names the first row, the
  last and the count.
- **Rule 2 is read.** Where a second timer starts on a register another
  runs on, the row it starts at is named. The order is the writer's to
  fix, and the check does not judge it.
- **Rule 1 reaches the wrap.** An effect running when the last row has
  played runs on through the row the tune repeats to, and that row stops
  it or starts a second effect there.
- **A new rule 6: a row does not `Stop` a timer this tune has not
  started.** The row a tune repeats to is the exception, since it stops
  every effect the tune runs so that the wrap resumes from a known
  setting.
- **A rate no 68000 services is an error.** A 68000 at 8 MHz spends 44
  cycles entering an interrupt and 20 leaving it, so at more than 125,000
  ticks a second it spends every cycle it has on those two. Prescaler 4
  with a count of 1 is 614,400.
- **A source no row starts, and two sources under one name, are errors of
  the form.** A form numbers its sources and names them, where the
  structure reaches one through the row that starts it, so a source a tune
  does not run was dropped where the form was read.
- A JSON number past what an int reads is no value of this form. It read
  as a truncated value in the Java tree and as itself in the Go tree, and
  both report it now, in one wording.

The new rules are silent across the 543 tunes of the corpus and the four
`.ymx` files: what a writer of this repository writes keeps them.

### 0.3.0, 2026-09-11

<https://github.com/odipar/YMXS/releases/tag/v0.3.0>, built from the commit
tagged `v0.3.0`.

**The structure's version is 3, and a file of version 2 is read by no tool
here.** The count is the timer's data register.

- A timer's data register is a byte and every value of it is a count: the
  timer loads the value and counts it down through zero, so 1 counts one
  tick and 0 counts 256. The structure recorded the ticks, 1 to 256, which
  made 0 a fault and 256 a value. `count` is the register now, 0 to 255.
- `-1` marks a value a row does not set, as it did, so 0 needed no number
  outside the byte to stand clear of it.
- `Chip.ticks` reads the ticks off the count for the two figures that need
  them: the rate, and the frames a source runs for.

**No file this writes moves.** A YM dump's slot is empty where its count
register is 0, so the reader never wrote a count of 256, and no tune of the
corpus or the five under `doc/tunes` has a count outside 31 to 255.

No file of either version is misread: 1 to 255 mean the same in both, and
the two values that moved are each outside the other version's range, so a
reader rejects rather than reading them wrong.

### 0.2.0, 2026-09-11

<https://github.com/odipar/YMXS/releases/tag/v0.2.0>, built from the commit
tagged `v0.2.0`.

**The structure's version is 2, and a file of version 1 is read by no tool
here.** One word a thing for a row and a frame.

- A frame is one call of the player and a row is one entry of a table. The
  tune's table advances one row a frame, so the two counts part at the end,
  where a tune that plays once runs a frame past its last row. A file
  records rows and a run counts frames.
- The tune key `frames` was the row count under a frame's name, and it is
  `rows`. The key `rows` was the columns a register, which are not rows,
  and it is `registers`, which is what the record names them.
- CSV follows: the `tune` table's `frames` column is `rows`, and the
  `### rows` table is `### registers`.
- SPEC.md, json.md, csv.md and ym.md define the two words once and use
  them. A dump's frames keep the name, since a frame is what a dump
  records.

**Section 1 of SPEC.md is Java that compiles.** It wrote a record with no
body, an enum with an ellipsis for its constants and no imports, so a
reader who pasted it read compiler errors. It is
`src/main/java/org/ymxs/YMXS.java` with the javadoc off now, and `SpecTest`
reads the two against each other.

### 0.1.1, 2026-09-11

<https://github.com/odipar/YMXS/releases/tag/v0.1.1>, built from the commit
tagged `v0.1.1`.

The columns of a table stand over the cells they name.

- A table opened with its name and its column names on one line, so the
  name took the first column's place and every name after it stood one
  cell to the left of the cells it names. The name stands alone on the
  heading line now and the column names on the line after it. The two
  tools between the forms write it and read it, doc/csv.md defines it,
  and doc/tunes/circus.csv is one tune in it.
- A column is still found by name, so column order follows the file as it
  did.
- Two lines a reader turns away rather than reading as something else: a
  heading with a comma in it, which is a file of the form this replaced,
  and a heading with no line after it, which is a table whose name is its
  last line. Each is reported for what it is.

**A CSV file 0.1.0 wrote is read by no version of this.** The line naming
the shape is what it gets, and `ymxs-csv-to-json` from 0.1.0 rewrites one
as JSON for reading here.

JSON is unchanged, and so is the structure's version: it is 1 in both
forms, and every JSON file 0.1.0 wrote reads here.

### 0.1.0, 2026-09-10

<https://github.com/odipar/YMXS/releases/tag/v0.1.0>, built from the commit
tagged `v0.1.0`.

The first release: the structure, the two forms, the checks and the five
tools.

- The tune data structure of SPEC.md, defined once in records and read by
  functions outside it.
- JSON (doc/json.md) and the tables (doc/csv.md), which write the same
  tune and read into the same structure.
- The rules a structure must satisfy, and the rules of SPEC.md 6 read
  across a tune's rows.
- `ym-to-ymxs`, `ymxs-check`, `ymxs-csv-to-json`, `ymxs-json-to-csv` and
  `ymxs-merge`: each reads standard input and writes standard output, so
  a conversion composes in a pipe.
- The same five in Go under `go/`, which `ParityTest` runs against the
  Java tree byte for byte. A release ships those, one zip a platform.
