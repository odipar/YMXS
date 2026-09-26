# The tools

Input, output, flags, exit codes and diagnostics for the five tools.
Section 11 covers the Java and Go implementations and their differences;
section 12 covers releases. Terms follow [SPEC.md](SPEC.md), with forms
defined in [json.md](json.md) and [csv.md](csv.md).

---

## 1. Definitions

**1.1** A *tool* reads one input from standard input, writes one output
to standard output, and writes lines to standard error.

**1.2** An *invocation* is one execution of a tool with a list of
arguments and an input; it ends with an exit code (3.6).

**1.3** A *line* on standard error ends with a line feed and begins with
the tool's name and `: `, except the second and later lines of an error
of several lines (4.1), the lines of `bin/run` (11.2), which begin
`run: `, and the stack trace of an invocation ending in an uncaught
exception or a panic (8.5, 9.3, ym.md 2.5).

**1.4** A *progress line* reports the figures of an invocation; sections
6 to 9 define each tool's. A count in it is followed by its noun, singular
for a count of 1 and plural for any other: `1 row`, `0 rows`, `2 rows`.

**1.5** An *error* is a fault of the input, of the call, or of a read or
a write, and ends the invocation; after an error of the input, standard
output is empty.

**1.6** A *warning* is a finding of the check of section 5 in a tune the
tool read, a rule of SPEC.md 6 the tune breaks; the invocation continues and
the output is that of a tune free of warnings.

**1.7** The *text* of an input is its bytes decoded as UTF-8, and the
bytes of an output are its text encoded as UTF-8. A *character count*
is in UTF-16 code units (11.5 for the Go tree).

---

## 2. The five tools

| tool | reads | writes | flags |
|---|---|---|---|
| `ym-to-ymxs` | a YM3!, YM3b, YM5! or YM6! register dump, in an LHA archive or not ([ym.md](ym.md)) | JSON, a multi of one tune | `-r`, `-rROW`, `-silent` |
| `ymxs-check` | JSON | the text read | `-silent` |
| `ymxs-json-to-csv` | JSON | CSV | `-silent` |
| `ymxs-csv-to-json` | CSV | JSON | `-silent` |
| `ymxs-merge` | several multis as JSON, one after another | JSON, one multi | `-silent` |

**2.1** Each tool has a Java script, `bin/<tool>`, that runs the class
below, and a Go executable built from the command directory (section 11).

| tool | Java class | Go command |
|---|---|---|
| `ym-to-ymxs` | `org.ymxs.ym.Main` | `go/cmd/ym-to-ymxs` |
| `ymxs-check` | `org.ymxs.tool.Checking` | `go/cmd/ymxs-check` |
| `ymxs-json-to-csv` | `org.ymxs.tool.ToCsv` | `go/cmd/ymxs-json-to-csv` |
| `ymxs-csv-to-json` | `org.ymxs.tool.ToJson` | `go/cmd/ymxs-csv-to-json` |
| `ymxs-merge` | `org.ymxs.tool.Merge` | `go/cmd/ymxs-merge` |

---

## 3. An invocation

**3.1 The arguments.** Every argument is a flag. All tools accept and
remove `-silent` at any position. Each remaining argument must be a
flag of that tool (9.2 for `ym-to-ymxs`); otherwise the call is wrong.

**3.2 A wrong call.** An invocation of `ymxs-check`, `ymxs-json-to-csv`,
`ymxs-csv-to-json` or `ymxs-merge` with an argument other than `-silent`
writes this line and exits with 2 before reading standard input; 9.2
defines a wrong call of `ym-to-ymxs`:

    <tool>: <tool> reads its input on standard input and writes it on standard output. Its one flag is -silent.

**3.3 `-silent`.** With `-silent` a tool omits its progress lines; an error
and a warning are written as before.

**3.4 Reading.** A tool reads standard input to its end before writing
to standard output or standard error, a wrong call excepted; where the
read fails it writes `cannot read standard input: ` and the message of
the failure, and exits with 2.

**3.5 Writing.** Where a write to standard output fails, the tool writes
`cannot write standard output` and exits with 2.

**3.6 The exit code.**

| exit | the invocation |
|---|---|
| 0 | completed: the output is written, and every warning found is reported |
| 1 | ended with an error of the input: the error is reported, and standard output is empty |
| 2 | ended with a wrong call, or with a failed read or write |

**3.7 The order.** The output is one write; each tool's section defines
which lines it writes before that write and which after.

---

## 4. The errors

**4.1 The report.** A tool that finds an error writes its name, `: `, the
text of the error and a line feed, and exits with the code of 3.6. An error
of the structure (4.3) or of an unstarted source (4.4) is one text of one or
more lines, each ending in a line feed, the name before the first alone:

    ymxs-check: tune 1: the tune has 4 rows and repeats to row 4
    tune 1: row 0: Timer A: the source has 2 rows and repeats to row 2

**4.2 Errors of the form.** A tool that reads JSON reports the errors of
json.md 8.1, each by its line there; a tool that reads CSV those of
csv.md 5.1. The reading ends at the first error found.

**4.2.1 JSON.** The reading performs the steps of json.md 7.1 in order, so
the first error found is the first in that order: the root's `format`,
`version` and `tunes`; for each tune, its `rows`, `sources`, `registers`,
`timerA` to `timerD`, then `title`, `composer`, `writer`, `rate` and
`repeat`, then the unstarted sources (4.4); after the last tune, the
structure (4.3).

**4.2.2 CSV.** The reading performs the steps of csv.md 4.1 in order: the
text split into blocks; the `multi` block; for each `tune` block, every
block after it other than a timer block, in file order, then the timer
blocks in file order, then the tune's `rate` and `repeat`, then the
unstarted sources (4.4); after the last tune, the structure (4.3).

**4.3 Errors of the structure.** After the form is read, the structure
is checked as json.md 8.3 and csv.md 5.3 define: every condition of
SPEC.md 1.11 present is one line of one error, in the order of SPEC.md
1.12, every line of a tune prefixed `tune N: `, N the tune number from
1.

**4.4 An unstarted source.** A source listed in JSON `sources` or a CSV
`source` block must be started by a tune row. Otherwise, reading the tune
produces an error of the form before the structure check: one line per
source in list order, using the last line of json.md 8.1 or csv.md 5.1.

---

## 5. The warnings

**5.1 The line.** `ymxs-check`, `ymxs-json-to-csv`, `ymxs-csv-to-json` and
`ymxs-merge` run the check of 5.2 on every tune of the multi read, in tune
order, and write one line a finding on standard error, with `-silent` as
before (3.3); where the multi has more than one tune, `tune N: ` comes
between `warning: ` and the finding. `ym-to-ymxs` omits the check (9.5).

    <tool>: warning: <finding>

**5.2 The check** is that of SPEC.md 6.3, performed on a structure free of
the errors of 4.3; a finding is one line of SPEC.md 6.5, the lines in the
order of SPEC.md 6.3.

**5.3** `doc/tunes/warnings.json` is the tune of SPEC.md 6.6, and its
six lines begin:

```
ymxs-check: warning: row 2: Timer B starts on R8, where Timer A runs: rule 2 leaves the order of two timers writing one register to the writer
ymxs-check: warning: row 6: Timer C stops an effect this timer has not started
```

---

## 6. ymxs-check

**6.1** The input is JSON, read as json.md defines and checked (4.3).

**6.2** The output is the text read (1.7), encoded as UTF-8; the reading
ends at the end of the first JSON value, and the text after it is skipped
and written with the rest.

**6.3** After the output, on standard error in this order: the progress
line of 6.4, the warning lines of section 5, the count line of 6.5.

**6.4** The progress line is `N tunes, R rows, S sources`, N the number of
tunes, R the sum of their row counts, S the sum of the counts of the
sources their rows start.

**6.5** The count line is a second progress line: `every rule of SPEC.md 6
is satisfied` where the check found zero warnings, and `W warnings`
otherwise.

For `doc/tunes/two-tunes.json`:

```
ymxs-check: 2 tunes, 2098 rows, 4 sources
ymxs-check: every rule of SPEC.md 6 is satisfied
```

For `doc/conformance/tunes/one-row.json`, the tune of one row in the
conformance kit:

```
ymxs-check: 1 tune, 1 row, 0 sources
ymxs-check: every rule of SPEC.md 6 is satisfied
```

---

## 7. ymxs-json-to-csv and ymxs-csv-to-json

**7.1** `ymxs-json-to-csv` reads JSON, checks the structure (4.3), and
writes the multi as CSV as csv.md defines the writing;
`ymxs-csv-to-json` reads CSV, checks the structure, and writes the multi
as JSON as json.md defines the writing.

**7.2** On standard error, in this order: the warning lines of section
5, then, after the output, the progress line `N tunes, R rows, C
characters in and D out`, N and R as in 6.4, C the character count of
the input text and D that of the output. For `doc/tunes/example.json`
and `doc/tunes/example.csv`:

```
ymxs-json-to-csv: 1 tune, 4 rows, 732 characters in and 469 out
ymxs-csv-to-json: 1 tune, 4 rows, 469 characters in and 732 out
```

**7.3** The multi `ymxs-json-to-csv` writes, read by `ymxs-csv-to-json`,
equals the multi read, and the same with the two exchanged. A JSON text
one of these tools wrote is written back byte for byte through the two
in that order, and a CSV text through the two in the other order.

**7.4** A multi whose title, composer, writer or source name has a line
feed or a carriage return in it is an error of `ymxs-json-to-csv`, found
after the reading and before any warning line, exit 1, standard output
empty (the Go tree: a panic, exit 2, 11.5):

    ymxs-json-to-csv: a value with a line feed in it, which this form cannot write: <value>

---

## 8. ymxs-merge

**8.1** The input is a sequence of JSON values, with optional white space
between them, each a multi as json.md defines it, read and checked as
`ymxs-check` reads one, with one difference: where a value fails to parse as
JSON, the Java tree's `this is not JSON: ` line (json.md 8.1) has a second
line, beginning ` at [Source: ` and ending `; line: L, column: C]`, L and C
where the parser stopped, which `ymxs-check` omits (11.5). The reading ends
at the first error found.

**8.2** The output is one multi as JSON, its tunes the tunes of the
input multis in input order, each as read.

**8.3** For an input in which zero values were read, the tool writes `no
tune to merge: a multi is one tune at least` and exits with 1. An input
value whose multi is empty is an error of the structure (4.3).

**8.4** On standard error, in this order: the warning lines of section 5
for the merged multi, with `tune N: ` where the input had more than one
tune in total; then, after the output, the progress line `N files with T
tunes`, N the number of values read and T the number of tunes written.

```
ymxs-merge: 2 files with 2 tunes
```

**8.5** Text after a value that fails to parse as JSON ends the invocation
in the Java tree with an uncaught exception, exit 1, and its stack trace as
the lines (1.3, 11.5).

---

## 9. ym-to-ymxs

**9.1** The input is a YM3!, YM3b, YM5! or YM6! register dump, in an LHA
archive or bare, and the output is JSON of a multi of one tune.
[ym.md](ym.md) defines the reading and the errors of a dump.

**9.2 The flags.** `-r`, `-rROW` and `-silent`. `-r` writes a tune that
plays once. `-rROW`, ROW a decimal integer with an optional `-` or `+`,
writes a tune that repeats to row ROW, the last ROW where several are passed;
with both `-r` and `-rROW`, in either order, the tune repeats to ROW (the Go
tree: plays once, 11.5). With both flags absent, the tune repeats to the row
of ym.md 8.3. A call is wrong, exit 2, before standard input is read, where
an argument begins `-r` and the rest is other than a decimal integer that
fits 32 bits, or where an argument is outside the three flags, X the
argument:

    ym-to-ymxs: X is not a row number
    ym-to-ymxs: ym-to-ymxs reads a dump on standard input and writes JSON on standard output. Its flags are -rROW, -r and -silent, and "X" is none of them.

**9.3 The range of ROW.** Every decimal integer that fits 32 bits is
accepted. ROW at or above the row count is written as the tune's `repeat`,
exit 0, and a tool reading the output reports the line of SPEC.md 1.11 for
the repeat row. ROW below 0 ends the invocation in the Java tree with an
uncaught exception and exit 1, in the Go tree with a panic and exit 2, in
both with a stack trace in place of an error line.

**9.4 The progress line**, written after the output:

    <format> "<name>" by "<author>", <R> rows at <H> Hz, <S> sources, timers [<timers>]

`<format>` is `YM3!`, `YM3b`, `YM5!` or `YM6!`; `<name>` and `<author>` the
dump's name and author as they are, each empty for a YM3 dump (ym.md 2.6);
R the row count, H the rate, S the number of sources the rows start,
`<timers>` the timers some row starts a source on, A to D, separated by
`, `, `[]` where empty. Three parts follow, each where its figure is above
0, in this order: `, N slots this does not read`, `, N frames a recording
kept a square wave off its voice`, `, N recordings cut at the row the tune
repeats to` (ym.md 9).

```
ym-to-ymxs: YM5! "Circus Attractions #2" by "Mad Max", 4 rows at 50 Hz, 0 sources, timers []
```

**9.5** The tool writes the tune before any check, the check of 4.3 and of
section 5 being the reading tools'; a dump whose slot rate is above 125,000
ticks a second (SPEC.md 3.3.4) is written, and `ymxs-check` reports the
error on reading it.

---

## 10. The pipe

**10.1** The output of one tool is the input of the next:

```bash
bin/ym-to-ymxs < tune.ym | bin/ymxs-check | bin/ymxs-json-to-csv > tune.csv
```

**10.2** A tool that ends with an error of the input leaves standard
output empty, so the next tool reads an empty input and reports its error:
`ymxs-check` and `ymxs-json-to-csv` `this is not JSON: EOF`,
`ymxs-csv-to-json` `the first table is not "### multi"`, `ymxs-merge` `no
tune to merge: a multi is one tune at least`, `ym-to-ymxs` `a header field
declares 4 bytes and 0 are left`. The exit code of a pipe is the shell's.

**10.3** Several JSON files concatenated are one input of `ymxs-merge`
(8.1):

```bash
{ bin/ym-to-ymxs < one.ym; bin/ym-to-ymxs < two.ym; } | bin/ymxs-merge > both.json
```

---

## 11. The two trees

**11.1** The tools are written in Java under `src/main/java/org/ymxs/`
and in Go under `go/`. json.md and csv.md define the reading; 11.5 lists
each input on which a tree departs from them.

**11.2 A Java tool** is the script `bin/<tool>`, whose one command runs
`bin/run` with the class of 2.1 and the arguments:

```sh
#!/bin/sh
# The errors of a tune, and the rules of SPEC.md 6 it breaks. doc/tools.md.
exec "$(dirname -- "$0")/run" org.ymxs.tool.Checking "$@"
```

`bin/run` builds the tree where `target/classes/.built` or
`target/classpath.txt` is absent or a file under `src/main/java` or
`pom.xml` is newer than `target/classes/.built`: `mvn -q compile`, then
the classpath written to `target/classpath.txt`, under the lock
`target/.building`, a directory one process creates and the others wait
for, one second at a time, up to 180 seconds, after which a waiting
process writes `run: a build has held <lock> for three minutes` and
exits with 2. Then it runs the class with `java -ea` on the classpath of
`target/classes` and `target/classpath.txt`.

**11.3 A Go tool** is one executable, built from `go/` by `go build
./cmd/...`, and runs by itself.

**11.4 Parity.** `ParityTest` runs the two trees on one input and
requires the same exit code, the same bytes on standard output and the
same text on standard error. It skips where Go is absent, so
`.github/workflows/test.yml`, which runs `bin/suite` (11.6) on a GitHub
runner where a caller starts it, puts Go on the path:

| input | tools |
|---|---|
| `src/test/resources/packed.ym` | `ym-to-ymxs` with no flag, with `-r`, and with `-r2` |
| each JSON file of `doc/tunes` | `ymxs-json-to-csv`; `ymxs-csv-to-json` on that output; `ymxs-check` |
| `doc/tunes/circus.json` and `doc/tunes/digidrum.json` concatenated | `ymxs-merge` |
| `doc/conformance/tunes/one-row.json`, a tune of one row | `ymxs-check`; `ymxs-json-to-csv`; `ymxs-csv-to-json` on that output; each report reads `1 row` (1.4) |
| a YM3! dump of one frame | `ym-to-ymxs`, whose report reads `1 row` |
| a tune of version 3 in CSV and in JSON | `ymxs-csv-to-json` on the CSV; `ymxs-check` and `ymxs-json-to-csv` on the JSON |
| that tune with a row of several values, and with a target above 13, in CSV and in JSON | `ymxs-csv-to-json` on the CSV; `ymxs-check` on the JSON; exit 1 and the line of csv.md 5.1 or json.md 8.1 |
| a tune of version 4 in CSV and in JSON with a target of 30 | `ymxs-csv-to-json` on the CSV; `ymxs-check` on the JSON; exit 1 and `no target 30: a tune reaches 0 to 24` |
| the text `not a file of any of these` | each of the five: the Java tree exits with 1, the Go tree with the same code, and both write the same bytes; standard error is not compared |
| `doc/tunes/example.json` and `doc/tunes/example.csv` with `rows` of -1 and of -2,147,483,648, and a tune of no columns with `rows` of -1 in each form | `ymxs-check` on the JSON, `ymxs-csv-to-json` on the CSV; exit 1 and the line 8.1 or 5.1 reports for R as read, or `the tune has no rows: a clock reads one` |

**11.5 Where the trees differ.** Each row is one input on which the two
trees differ, read off invocations of both.

| input | the Java tree | the Go tree |
|---|---|---|
| a JSON source value that is not a whole number, such as `13.5` or a text | `source NAME has the value X, and this form requires a whole number`, exit 1 | `NAME at row J is X, and this form requires a whole number`, exit 1, the line json.md 8.1 defines |
| a JSON `version`, `rows` or `rate` outside 32 bits | reads the low 32 bits of the number | `KEY is X, and this form requires a whole number`, exit 1 |
| a JSON start whose `source` is outside 1 to S and whose `target` column is absent | `row I starts source N, and the tune runs S`, exit 1 | `Timer T has no "target" column`, exit 1: the six columns are read before a value is verified |
| a CSV `source` block whose `repeat` cell is not a whole number, in a tune with a later `value` cell that is not one | `repeat is "CELL", and this form requires a whole number`, exit 1, at the `source` block | `value is "CELL", and this form requires a whole number`, exit 1: the `repeat` cell is read after every block of the tune |
| a CSV cell reported in the whole-number line with a `"`, a `\` or a control character in it | the characters of the cell as read (csv.md 5.1) | the characters with a `\` before each such character |
| a CSV whole number outside 32 bits | `WHAT is "CELL", and this form requires a whole number`, exit 1 | reads the number; a `rows` cell of that size ends the process where the rows are allocated |
| a JSON `repeat` outside 32 bits, on a tune or a source | reads the low 32 bits of the number | `repeat is X, and this form requires a row number or null`, exit 1 |
| a JSON `repeat` of `-1`, on a tune or a source | `the tune has R rows and repeats to row -1`, or for a source `the source has R rows and repeats to row -1` prefixed as 4.3 defines, an error of the structure | reads a table that plays once |
| a CSV `timerReset` or `placeReset` cell that is not a whole number | `true or false is "x", and this form requires a whole number`, exit 1 | reads the cell as 0 |
| a title, composer, writer or source name with a line feed in it, through `ymxs-json-to-csv` | `a value with a line feed in it, which this form cannot write: <value>`, exit 1 (7.4) | a panic, exit 2 |
| text after a valid multi that is not JSON, through `ymxs-merge` | an uncaught exception, exit 1, no error line | `this is not JSON: ` and the parser's message, exit 1 |
| an input that is not JSON | `this is not JSON: ` and the message of the Java parser | `this is not JSON: ` and the message of the Go parser |
| an input that is not JSON, through `ymxs-merge` | the line above and a second line with the parser's location (8.1) | one line |
| a JSON root, tune or source that is not an object | the fault of the first key read, such as `format is null, and this form requires a text` | `this is X, and this form requires an object`, `a tune is X, and this form requires an object`, `a source is X, and this form requires an object` |
| a fault line that prints a value's kind | the value's JSON | `null`, `an array`, `an object`, the text in quotes, the number, or `true` |
| a text with a character outside the Basic Multilingual Plane | counts it as two characters | counts it as one |
| a dump whose name or author has a quote, a backslash or a control character in it | the progress line prints the string as it is | the progress line prints it with a backslash before each such character |
| an archive with a stored member whose unpacked size exceeds the bytes after the header (ym.md 2.5) | an uncaught exception, exit 1, no error line | reads the bytes present followed by zero bytes to the unpacked size |
| `ym-to-ymxs -r -rROW`, in either order | repeats to ROW | plays once |
| `ym-to-ymxs -r-1` | an uncaught exception, exit 1 | a panic, exit 2 |
| `ym-to-ymxs -rROW`, ROW outside 32 bits | `-rROW is not a row number`, exit 2 | writes ROW as the tune's `repeat` |

**11.6 The whole suite.** `bin/suite [maven argument ...]` runs the Java
suite as `.github/workflows/test.yml` runs it. It writes
`target/classpath.txt`, which `PipeTest` and `ParityTest` read and the
test phase alone leaves unwritten, runs `mvn test`, and reads the count of
skipped tests off the run. A skipped test is a check that did not run, so
a count above 0 ends the script with exit 1 and the lines that report it,
and go absent from the path ends it with exit 2 before the suite starts. A
bare `mvn test` skips every test of `PipeTest` and `ParityTest` and
reports a green run.

---

## 12. A release

**12.1** `release/publish.sh` builds the five tools from the Go tree for
`win-x64`, `win-arm64`, `osx-x64`, `osx-arm64`, `linux-x64` and
`linux-arm64`, one zip a platform in `dist/release`, named
`ymxs-tools-<platform>-v<version>.zip`, each of the five executables, the
Windows ones with `.exe`, each running by itself.

**12.2** The version is the script's one argument, or the first
`<version>` of `pom.xml`.

**12.3** `TARGETS`, a space-separated list of platforms of 12.1, limits
the build to those; a platform outside 12.1 ends the script with
`publish: <target> is not a platform this builds` and exit 1. `OUT`, a
directory, replaces `dist`.

**12.4** After the zips, `release/manifest.sh` writes
`dist/release/MANIFEST.txt`: each zip's name, size and sha256, what it
contains, and the source commit. Where the host is macOS or Linux on x64
or arm64 and its platform was built, the script runs the host's
executables from a directory outside the repository,
`src/test/resources/packed.ym` through `ym-to-ymxs`, `ymxs-check`,
`ymxs-json-to-csv` and `ymxs-csv-to-json`, and reports the byte count of
the JSON.

**12.5** [RELEASES.md](RELEASES.md) lists the releases. The Go tree is
the module `github.com/odipar/ymxs/go`, a version of it a tag of that
directory, `go/v0.4.7` beside `v0.4.7`.
