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

The Go tree is a separate module, `github.com/odipar/ymxs/go`, and a
version of it is a tag of that directory: `go/v0.4.2` beside `v0.4.2`.

A player pins a version of this format: the structure's version is 4, a
writer writes 4 and a reader reads 4 and 3, and a release's number names
the tools rather than the format.

## Published

### 0.4.2, 2026-09-18

<https://github.com/odipar/YMXS/releases/tag/v0.4.2>, built from the commit
tagged `v0.4.2`.

The style check, two calls a reader outside this package reads a dump
through, and a plan that shipped. The tools write the bytes 0.4.1 wrote:
the ten dumps under YMXR's `ym/test` convert to the structures a build of
the tag before writes, measured.

- **The struck list is a document.** The check was a list of 88 phrases in
  a test class, matched as substrings over the documents alone.
  `org.ymxs.style` reads `STRUCK.md` - a section a rule of AGENTS.md, an
  entry a name, a pattern and the samples the pattern is and is not in -
  and runs over every document and every code comment. The package is
  carried from DTX, which wrote it, and the four repositories of the family
  run the same 370 lines. Reading comments, which this check never did,
  found 51 lines to reword, and the cleft four more: AGENTS.md has struck
  the cleft since it was written and no list encoded it.
- **`Lha.isArchive` and `Lha.unpack` are public.** They were
  package-private, so YMXR carried a copy of the whole decoder to read a
  packed dump - 351 lines of code identical to these but for the class
  modifier - beside a Go tree that imports this module and calls the
  exported pair. YMXR's copy goes when it reads this release.
- **`doc/plan.md` goes.** It worked the voice target out before it became a
  clause, and 0.4.0 made it SPEC.md 3.1.1.

### 0.4.1, 2026-09-17

<https://github.com/odipar/YMXS/releases/tag/v0.4.1>, built from the commit
tagged `v0.4.1`.

The order a target writes its registers is the player's. One clause moves
and every other file stands as 0.4.0 has it, so every file of this release
reads under 0.4.0 and the other way round, and the JSON and CSV the tools
write are the bytes 0.4.0 wrote.

- `3.1.1`'s table read "registers, in the order it writes them", which
  fixed an order for every target. The sound depends on two of those
  orderings alone, and the text under the table already named both: a
  voice's fine byte before its coarse nibble, so the period standing
  between the two writes is the new fine with the old coarse; and a
  buzzer's shape after its period, since writing the shape restarts the
  generator on the period standing. Where a volume register stands among
  them is unheard.
- The table is the map from a value of the row to a register now, as its
  column says, and those two orderings stand written out below it. A
  player that writes the rest in another order plays the tune this
  structure defines: YMXR's tick writes the column the marker stands in
  last, which is R8 between R0 and R1 for a voice and R6 after R8 for a
  noise.

### 0.4.0, 2026-09-17

<https://github.com/odipar/YMXS/releases/tag/v0.4.0>, built from the commit
tagged `v0.4.0`.

A target that writes several registers, and a source of several values a
row. The structure's version is 4: a writer writes 4, a reader reads 4 and
3, and a 0.3.6 reader reports a file this writes as an error of the form.
A version-3 file reads here as it read there.

- targets 14 to 24, each writing its registers in one order and reading one
  value of the row each: `setToneA/B/C` writes R0 R1, R2 R3, R4 R5;
  `setVoiceA/B/C` those and the voice's volume; `setEnvelope` R11 R12;
  `setBuzzer` those and the shape; `setNoiseA/B/C` the noise period and one
  voice's volume. An effect runs a voice's period and its volume on one
  timer where it needed two
- a source of two or three values a row, whose table is one width from top
  to bottom (SPEC.md 3.2.1)
- a source fits its target by shape and not by a check: `Target` is sealed
  over `OneTarget`, `TwoTarget` and `ThreeTarget`, a source over `Single`,
  `Pair` and `Triple`, and a start over `StartOne`, `StartPair` and
  `StartTriple`, each pairing a target with the source of that width.
  `Timing` is the prescaler, the count and the two resets, which `Retune`
  and every start share
- in JSON a row is a number where the source is one value a row and an
  array of two or three where it is more (json.md 2.4); in CSV a value
  block names its cells `value` for one and `value1`, `value2`, `value3`
  for more (csv.md 3.6)
- the Go tree mirrors the Java tree interface for interface, and
  `ParityTest` reads the same files through both

### 0.3.6, 2026-09-17

<https://github.com/odipar/YMXS/releases/tag/v0.3.6>, built from the commit
tagged `v0.3.6`.

A check reads one citation more. The structure, the tools and the two forms
are 0.3.5's, so every file of this release reads under 0.3.5 and the other
way round, and the JSON and CSV the tools write are the bytes 0.3.5 wrote.

- `everyClauseCitedInAnotherDocumentIsDefined` read a citation written as
  `SPEC.md 7` and passed over one written through a link, the form
  README.md uses, so the clauses a reader follows from there were read by
  no check. The closing bracket is part of the pattern now: 75 citations
  are read where 67 were, and each resolves.
- ST4, DTX and YMXR read a citation the same way, so one check stands in
  the four repositories of the family.

### 0.3.5, 2026-09-16

<https://github.com/odipar/YMXS/releases/tag/v0.3.5>, built from the commit
tagged `v0.3.5`.

The documents, and the checks that read their figures back. The structure,
the tools and the two forms are 0.3.4's, so every file of this release reads
under 0.3.4 and the other way round, and the JSON and CSV the tools write
are the bytes 0.3.4 wrote.

- **Four checks read a document against the thing it describes.** Every line
  tools.md quotes of a run is a line a run writes, figures and all; the tune
  json.md 10.1 and csv.md 8.1 quote is the file beside it, byte for byte;
  the release the documents name is the one `pom.xml` names; and every
  clause one document cites in another is one that document defines. Each
  failed on the text as it stood.
- **The release the documents named was the one before this.** README.md
  fetched `github.com/odipar/ymxs/go@v0.3.3` of a module the pom had at
  0.3.4, and tools.md 12.5 and this document showed that tag beside it.
- **A list of tunes became a search.** tools.md 11.4 read "each of the seven
  files of `doc/tunes`" for a list `ParityTest` wrote out by name, so a tune
  added later crossed neither way; the test finds them now.
- **The check of SPEC.md 6.6 is read line by line.** `CheckTest` counted six
  warnings of `doc/tunes/warnings.json`; it reads the six lines the clause
  quotes.
- **ym.md 1.4, 1.5 and 1.6 stand in a paragraph each**, as every clause
  beside them, and README.md opens its second paragraph without a cleft.

### 0.3.4, 2026-09-15

<https://github.com/odipar/YMXS/releases/tag/v0.3.4>, built from the commit
tagged `v0.3.4`.

The documents alone. The structure, the tools and the two forms are
0.3.3's, so every file of this release reads under 0.3.3 and the other way
round, and the JSON and CSV the tools write are the bytes 0.3.3 wrote.

- **YMX is the family this repository belongs to.** README.md names it: a
  design document defining how YMXS, YMXR, DTX and ST4 fit together, where
  YMX was a format and a player until 0.10.1.
- **Read this first**, at the top of README.md, in the words the three
  repositories share: who wrote what, and that the decision to use software
  written that way is the reader's.
- **The prose is shorter and playback is explained where a reader meets
  it**: a frame at the tune's rate and a tick at each effect's, the order
  of the two, and which documents to read for a player or a reader.
- **A negation reads as what is there.** The rule is in AGENTS.md and its
  patterns in `HouseStyleTest`, and the documents define what is there
  rather than what is absent.

### 0.3.3, 2026-09-15

<https://github.com/odipar/YMXS/releases/tag/v0.3.3>, built from the commit
tagged `v0.3.3`.

The documents changed, and the lines the tools write for an error of a
file. The structure's version is 3, as 0.3.0 set it, so every file of
this release reads under 0.3.2 and the other way round, and the JSON and
CSV the tools write are the bytes 0.3.2 wrote.

- **The documents are a specification.** SPEC.md is numbered, citable
  clauses: conventions and roles; the structure, with its Java block
  read as a reader of another language needs it; every error of the
  structure with its text and the order of a report; the rate, the
  count and the two resets with the MC68901's control codes; a frame
  and a tick as ordered steps with their initial conditions, end and
  wrap; the six rules with their conditions; the check as a procedure
  with its model and its reckoning, and every warning text; the record
  a recorder produces, with its three operation entries, held to an
  example by a test; and what a later version defines. json.md and
  csv.md define every key and cell with its range, reading as steps,
  every error with its line, and the layout an emitter emits. tools.md
  defines each tool's flags, exit codes and lines, the pipe, and where
  the Java and Go trees differ. ym.md defines a dump's layout, its
  errors and the mapping frame by frame.
- **One example tune in every form.** `doc/tunes/example.json` and
  `example.csv` are new, four rows and one effect: a start, a retune
  and a stop. json.md shows the file, csv.md the same tune as CSV, and
  SPEC.md 7 the record a recorder produces of it.
- **The lines for an error of a file changed.** Where a value is of the
  wrong kind, both trees write `KEY is X, and this form requires a
  whole number` (an array, an object, a text, a column, a row number
  or null) in place of `..., and a whole number is asked`. json.md 8.1
  and csv.md 5.1 list every line.
- **The Go reader agrees with the Java reader in two more places.** A
  `registers` or timer object whose value is `null` is an error in both
  trees; it read as absent in Go. A source's `values` are read before
  its `name` in both, so a source with neither reports `values` first.
- **The license describes this repository.** LICENSE was YMX's file,
  naming a compressor, 68000 players and a C# tree this repository does
  not have. It now lists what is here, on the same terms, with the
  notices of the LHA depacker kept as they were.
- **Two constructs are struck from the house style:** a metaphor for
  encoded, and a human act for satisfy. AGENTS.md logs both and the
  test strikes them.

### 0.3.2, 2026-09-13

<https://github.com/odipar/YMXS/releases/tag/v0.3.2>, built from the commit
tagged `v0.3.2`.

A rule 0.3.1 added is gone: a tune it rejected is read here. The
structure's version is 3, as 0.3.0 set it, so a file of this release reads
under 0.3.0 and 0.3.1 and the other way round.

- **Two sources under one name are no fault.** 0.3.1 made them an error of
  the form, on the reasoning that a reader of the form could not tell them
  apart. An effect names its source by the number of the table it stands
  in, in JSON and in CSV alike, and neither tree looks a source up by
  name: every use of a name writes it out or puts it in a message.
  A tune with two sources named `square`, one of `[12, 0]` and one of
  `[6, 0]`, both started by a row, reads in both trees and comes back
  through `ymxs-json-to-csv | ymxs-csv-to-json` with both names and both
  sets of values.
- **A source no row starts is still an error.** The structure reaches a
  source through the row that starts it, so a form that declares one no
  row starts loses it where the form is read, and that is data lost in
  silence.

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
  and it is `registers`, the name the record uses for them.
- CSV follows: the `tune` table's `frames` column is `rows`, and the
  `### rows` table is `### registers`.
- SPEC.md, json.md, csv.md and ym.md define the two words once and use
  them. A dump's frames keep the name, since a dump records frames.

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
the shape reads as the shape it names, and `ymxs-csv-to-json` from 0.1.0
rewrites one as JSON for reading here.

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
