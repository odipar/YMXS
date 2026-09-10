// Command ymxs-check reads a tune on standard input, writes the same text
// on standard output, and puts the faults on standard error. It stands in
// a pipe and leaves what passes through it unchanged.
//
// It separates two kinds of fault.
//
// An error is a tune no player plays: text outside this form, or a
// structure outside the two chips. Standard output stays empty and the
// exit is 1, so the pipe stops rather than passing broken data on.
//
// A warning is a tune that plays, but not as written: it breaks a rule of
// doc/SPEC.md 6. The tune passes through and the exit is 0, since a player
// plays it and only the writer can judge the result.
package main

import (
	"fmt"
	"os"

	"github.com/odipar/ymxs/go/check"
	"github.com/odipar/ymxs/go/text"
	"github.com/odipar/ymxs/go/tool"
	"github.com/odipar/ymxs/go/ymxs"
)

func main() {
	t, _ := tool.Of("ymxs-check", os.Args[1:])
	said := t.Text()
	multi, err := text.Read(said)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	t.Write(said)
	rows := 0
	sources := 0
	for _, tune := range multi.Tunes {
		rows += len(ymxs.Rows(tune))
		sources += len(ymxs.Sources(tune))
	}
	t.Report(fmt.Sprintf("%d %s %d rows, %d %s", len(multi.Tunes),
		plural(len(multi.Tunes), "tune,", "tunes,"), rows, sources,
		plural(sources, "source", "sources")))
	warnings := 0
	for at, tune := range multi.Tunes {
		for _, one := range check.Writing(tune) {
			named := ""
			if len(multi.Tunes) != 1 {
				named = fmt.Sprintf("tune %d: ", at+1)
			}
			fmt.Fprintln(os.Stderr, "ymxs-check: warning: "+named+one)
			warnings++
		}
	}
	if warnings == 0 {
		t.Report("every rule of SPEC.md 6 is satisfied")
	} else {
		t.Report(fmt.Sprintf("%d %s", warnings, plural(warnings, "warning", "warnings")))
	}
}

func plural(count int, one, many string) string {
	if count == 1 {
		return one
	}
	return many
}
