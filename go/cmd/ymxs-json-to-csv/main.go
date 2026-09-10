// Command ymxs-json-to-csv reads JSON on standard input and writes CSV on
// standard output (doc/csv.md).
package main

import (
	"fmt"
	"os"
	"unicode/utf8"

	"github.com/odipar/ymxs/go/csv"
	"github.com/odipar/ymxs/go/text"
	"github.com/odipar/ymxs/go/tool"
	"github.com/odipar/ymxs/go/ymxs"
)

func main() {
	t, _ := tool.Of("ymxs-json-to-csv", os.Args[1:])
	said := t.Text()
	multi, err := text.Read(said)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	written := csv.Write(multi)
	t.Write(written)
	rows := 0
	for _, tune := range multi.Tunes {
		rows += len(ymxs.Rows(tune))
	}
	tunes := "tunes, "
	if len(multi.Tunes) == 1 {
		tunes = "tune, "
	}
	t.Report(fmt.Sprintf("%d %s%d rows, %d characters in and %d out", len(multi.Tunes),
		tunes, rows, utf8.RuneCountInString(said), utf8.RuneCountInString(written)))
}
