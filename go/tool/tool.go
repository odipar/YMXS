// Package tool is what every tool here shares: it reads one input on
// standard input, writes one output on standard output, and reports
// progress and faults on standard error. A tool therefore composes in a
// pipe, and a redirected run contains the output alone.
//
// The exit codes:
//
//	0  the tool completed
//	1  the input is wrong, and the fault is reported
//	2  the call is wrong, or reading or writing failed
//
// -silent reduces the report to faults.
package tool

import (
	"fmt"
	"io"
	"os"

	"github.com/odipar/ymxs/go/check"
	"github.com/odipar/ymxs/go/ymxs"
)

// Done is the exit of a tool that completed.
const Done = 0

// Wrong is the exit of a tool whose input is wrong.
const Wrong = 1

// Failed is the exit of a tool whose call is wrong, or that could not read
// or write.
const Failed = 2

// Silent is the flag that reduces the report to faults.
const Silent = "-silent"

// Tool is one tool's name and whether it reports progress.
type Tool struct {
	named   string
	reports bool
	in      io.Reader
	out     io.Writer
	err     io.Writer
	exit    func(int)
}

// Of is a tool of this name, and the arguments that are left. The flags
// every tool reads are taken out of args; the rest are returned. A tool
// that reads no flag of its own is called with none here, and an argument
// left over is then a wrong call.
func Of(named string, args []string, flags ...string) (*Tool, []string) {
	t := &Tool{named: named, reports: true,
		in: os.Stdin, out: os.Stdout, err: os.Stderr, exit: os.Exit}
	var rest []string
	for _, arg := range args {
		if arg == Silent {
			t.reports = false
		} else {
			rest = append(rest, arg)
		}
	}
	if len(flags) == 0 && len(rest) > 0 {
		t.Wrong(Failed, named+" reads its input on standard input and writes it on"+
			" standard output. Its one flag is "+Silent+".")
	}
	return t, rest
}

// Text is everything on standard input, as text.
func (t *Tool) Text() string {
	return string(t.Bytes())
}

// Bytes is everything on standard input.
func (t *Tool) Bytes() []byte {
	read, err := io.ReadAll(t.in)
	if err != nil {
		t.Wrong(Failed, "cannot read standard input: "+err.Error())
	}
	return read
}

// Write puts said on standard output, which is what the tool is for.
func (t *Tool) Write(said string) {
	t.WriteBytes([]byte(said))
}

// WriteBytes puts a file on standard output.
func (t *Tool) WriteBytes(file []byte) {
	if _, err := t.out.Write(file); err != nil {
		t.Wrong(Failed, "cannot write standard output")
	}
}

// Report puts progress on standard error, unless -silent was passed.
func (t *Tool) Report(said string) {
	if t.reports {
		fmt.Fprintln(t.err, t.named+": "+said)
	}
}

// Note puts a note on standard error, indented, which stands whether the
// report is on or off.
func (t *Tool) Note(said string) {
	fmt.Fprintln(t.err, "  "+said)
}

// Reports is whether the tool reports progress.
func (t *Tool) Reports() bool {
	return t.reports
}

// Warnings is the warnings of SPEC.md 6 for every tune of multi, one line
// each on standard error, and how many there were.
//
// Every tool that reads a tune reports these, so a fault a writer left in
// is named where the tune is used rather than only where it is checked. A
// warning is a tune that plays, but not as written, so it stands whether
// or not -silent was passed: that flag quiets what a tool reports of its
// work, and this is what the tune gets wrong.
func (t *Tool) Warnings(multi ymxs.Multi) int {
	count := 0
	for at, tune := range multi.Tunes {
		for _, one := range check.Writing(tune) {
			named := ""
			if len(multi.Tunes) != 1 {
				named = fmt.Sprintf("tune %d: ", at+1)
			}
			fmt.Fprintln(t.err, t.named+": warning: "+named+one)
			count++
		}
	}
	return count
}

// Named is the name the tool reports under.
func (t *Tool) Named() string {
	return t.named
}

// Wrong puts a fault on standard error and exits with that code. It does
// not return.
func (t *Tool) Wrong(with int, said string) {
	fmt.Fprintln(t.err, t.named+": "+said)
	t.exit(with)
	panic(said) // an exit that returns is a fault of its own
}

// Usage puts the calling convention on standard error and exits 2.
func (t *Tool) Usage(said string) {
	t.Wrong(Failed, said)
}
