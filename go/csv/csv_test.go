package csv_test

import (
	"os"
	"testing"

	"github.com/odipar/ymxs/csv"
	"github.com/odipar/ymxs/text"
)

var tunes = []string{"circus", "digidrum", "retrigger", "turrican-2", "two-tunes"}

// The two forms write the same tune, so a tune that crosses to the tables
// and back is the JSON it was.
func TestATuneCrossesToTheTablesAndBack(t *testing.T) {
	for _, named := range tunes {
		said, err := os.ReadFile("../../doc/tunes/" + named + ".json")
		if err != nil {
			t.Fatal(err)
		}
		multi, err := text.Read(string(said))
		if err != nil {
			t.Fatalf("%s: %v", named, err)
		}
		back, err := csv.Read(csv.Write(multi))
		if err != nil {
			t.Fatalf("%s: %v", named, err)
		}
		if again := text.Write(back); again != string(said) {
			t.Errorf("%s: the tune moved crossing to the tables and back", named)
		}
	}
}

// The tables of doc/tunes/circus.csv are the tables this writes.
func TestTheDocumentsTablesAreTheTablesThisWrites(t *testing.T) {
	said, err := os.ReadFile("../../doc/tunes/circus.json")
	if err != nil {
		t.Fatal(err)
	}
	tables, err := os.ReadFile("../../doc/tunes/circus.csv")
	if err != nil {
		t.Fatal(err)
	}
	multi, err := text.Read(string(said))
	if err != nil {
		t.Fatal(err)
	}
	if written := csv.Write(multi); written != string(tables) {
		t.Errorf("the tables moved:\n%s", written)
	}
}

func TestALineOfCellsReadsBackTheCellsItHasInIt(t *testing.T) {
	for _, one := range []struct {
		line  string
		cells []string
	}{
		{"a,b,c", []string{"a", "b", "c"}},
		{`a,"b,c",d`, []string{"a", "b,c", "d"}},
		{`"he said ""no"""`, []string{`he said "no"`}},
		{"a,,c", []string{"a", "", "c"}},
	} {
		cells := csv.Cells(one.line)
		if len(cells) != len(one.cells) {
			t.Errorf("%s reads as %d cells, and %d are in it", one.line, len(cells),
				len(one.cells))
			continue
		}
		for at, said := range one.cells {
			if cells[at] != said {
				t.Errorf("%s: cell %d is %q, and %q is in it", one.line, at,
					cells[at], said)
			}
		}
	}
}
