// Command ymxs-csv-to-json reads CSV on standard input and writes JSON on
// standard output (doc/json.md).
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
	t, _ := tool.Of("ymxs-csv-to-json", os.Args[1:])
	said := t.Text()
	multi, err := csv.Read(said)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	json := text.Write(multi)
	t.Warnings(multi)
	t.Write(json)
	rows := 0
	for _, tune := range multi.Tunes {
		rows += len(ymxs.Rows(tune))
	}
	t.Report(fmt.Sprintf("%s, %s, %s in and %d out",
		tool.Count(len(multi.Tunes), "tune", "tunes"), tool.Count(rows, "row", "rows"),
		tool.Count(utf8.RuneCountInString(said), "character", "characters"),
		utf8.RuneCountInString(json)))
}
