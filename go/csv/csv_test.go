package csv_test

import (
	"os"
	"strings"
	"testing"

	"github.com/odipar/ymxs/go/csv"
	"github.com/odipar/ymxs/go/text"
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

// A heading line names the table alone, and the line after it names the
// columns, so a column name stands over the cells it names.
func TestAHeadingNamesTheTableAndTheLineAfterItTheColumns(t *testing.T) {
	tables, err := os.ReadFile("../../doc/tunes/circus.csv")
	if err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimRight(string(tables), "\n"), "\n")
	opened := 0
	for at, line := range lines {
		if !strings.HasPrefix(line, "###") {
			continue
		}
		opened++
		named := strings.TrimSpace(line[3:])
		if strings.Contains(named, ",") {
			t.Errorf("the heading %q names more than the table", line)
			continue
		}
		columns := len(csv.Cells(lines[at+1]))
		for row := at + 2; row < len(lines) && strings.TrimSpace(lines[row]) != ""; row++ {
			if cells := len(csv.Cells(lines[row])); cells != columns {
				t.Errorf("%s: row %d has %d cells under %d columns", named, row,
					cells, columns)
			}
		}
	}
	if opened != 3 {
		t.Errorf("circus is rows alone, so it opens 3 tables and %d were read", opened)
	}
}

// A file of the form this replaced, and a table whose name is its last
// line, are both named rather than read as something else.
func TestAHeadingThisDoesNotReadIsNamed(t *testing.T) {
	for _, one := range []struct {
		said string
		of   string
	}{
		{"### multi,format,version,tunes\nymxs,1,1\n", "has a comma in it"},
		{"### multi\n", "names no columns"},
		{"### multi\nformat,version,tunes\nymxs,1,1\n\n### tune\n", "names no columns"},
	} {
		_, err := csv.Read(one.said)
		if err == nil {
			t.Errorf("%q reads, and it is not this form", one.said)
			continue
		}
		if !strings.Contains(err.Error(), one.of) {
			t.Errorf("%q reports %q, and %q is what is wrong with it", one.said,
				err, one.of)
		}
	}
}
