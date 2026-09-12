// Command ymxs-merge reads several tunes on standard input and writes one
// multi on standard output.
//
// Every other tool reads one input and writes one output. A multi contains
// several tunes, and this tool combines them: JSON puts no count in front
// of a stream of values, so several such files concatenated read as
// several multis, and their tunes combine into one in input order.
//
//	cat one.json two.json | ymxs-merge > both.json
package main

import (
	"fmt"
	"os"

	"github.com/odipar/ymxs/go/text"
	"github.com/odipar/ymxs/go/tool"
	"github.com/odipar/ymxs/go/ymxs"
)

func main() {
	t, _ := tool.Of("ymxs-merge", os.Args[1:])
	read, err := text.ReadAll(t.Text())
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	var tunes []ymxs.Tune
	for _, one := range read {
		tunes = append(tunes, one.Tunes...)
	}
	if len(tunes) == 0 {
		t.Wrong(tool.Wrong, "no tune to merge: a multi is one tune at least")
	}
	multi := ymxs.Multi{Tunes: tunes}
	t.Warnings(multi)
	t.Write(text.Write(multi))
	files := "files with "
	if len(read) == 1 {
		files = "file with "
	}
	named := "tunes"
	if len(tunes) == 1 {
		named = "tune"
	}
	t.Report(fmt.Sprintf("%d %s%d %s", len(read), files, len(tunes), named))
}
