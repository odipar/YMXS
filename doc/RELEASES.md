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

### 0.1.1, 2026-09-11

<https://github.com/odipar/YMXS/releases/tag/v0.1.1>, built from the commit
tagged `v0.1.1`.

The columns of a table stand over the cells they name.

- A table opened with its name and its column names on one line, so the
  name took the first column's place and every name after it stood one
  cell to the left of the cells it names. The name stands alone on the
  heading line now and the column names on the line after it. `ymxs-json-
  to-csv` writes that and `ymxs-csv-to-json` reads it, doc/csv.md defines
  it, and doc/tunes/circus.csv is one tune in it.
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
