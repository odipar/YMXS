package csv_test

import (
	"os"
	"strings"
	"testing"

	"github.com/odipar/ymxs/go/csv"
	"github.com/odipar/ymxs/go/text"
)

var tunes = []string{"circus", "digidrum", "example", "retrigger", "turrican-2", "two-tunes"}

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
// columns, so a column name is over the cells it names.
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

// TestASourceOfSeveralValuesCrossesTheForms is a tune with a source of
// three values a row: it crosses to the tables and back, and the value
// block names value1, value2 and value3 (csv.md 3.6).
func TestASourceOfSeveralValuesCrossesTheForms(t *testing.T) {
	said := `{"format":"ymxs","version":4,"tunes":[{"title":"","composer":"",
		"writer":"t","rate":50,"rows":2,"repeat":0,
		"sources":[{"name":"a sweep","repeat":0,"values":[[46,1,15],[32,1,13]]}],
		"registers":{"r7":[56,-1]},
		"timerA":{"shape":[0,-1],"target":[17,-1],"source":[1,-1],
		"prescaler":[50,-1],"count":[60,-1],"timerReset":[1,-1],"placeReset":[1,-1]}}]}`
	multi, err := text.Read(said)
	if err != nil {
		t.Fatal(err)
	}
	made := csv.Write(multi)
	if !strings.Contains(made, "row,value1,value2,value3") {
		t.Errorf("the block names the cells it has:\n%s", made)
	}
	if !strings.Contains(made, "0,46,1,15") {
		t.Errorf("and the row is its values:\n%s", made)
	}
	back, err := csv.Read(made)
	if err != nil {
		t.Fatal(err)
	}
	if text.Write(back) != text.Write(multi) {
		t.Errorf("the tune moved crossing to the tables and back:\n%s\n%s",
			text.Write(multi), text.Write(back))
	}
}

// A file of version 3 has one value a row in every source and a target of
// 0 to 13 (json.md 2.4): such a file reads, and a row of several values or
// a target above 13 in one is an error of the form, one line of csv.md 5.1.
func TestAFileOfTheVersionBeforeThisOneReadsItsShapes(t *testing.T) {
	three := `### multi
format,version,tunes
ymxs,3,1

### tune
title,composer,writer,rate,rows,repeat
,,t,50,2,0

### source
name,repeat
a tone,0

### value
row,value
0,13
1,0

### timerA
row,shape,source,target,prescaler,count,timerReset,placeReset
0,0,1,8,50,60,1,1
`
	if _, err := csv.Read(three); err != nil {
		t.Fatalf("a file of version 3 reads: %v", err)
	}
	for _, one := range []struct{ from, to, line string }{
		{"row,value\n0,13\n1,0", "row,value1,value2\n0,13,1\n1,0,1",
			"source a tone has a row of several values, and version 3 has one value a row"},
		{"0,0,1,8,", "0,0,1,14,", "target 14, and version 3 reaches 0 to 13"},
	} {
		_, err := csv.Read(strings.Replace(three, one.from, one.to, 1))
		if err == nil || err.Error() != one.line {
			t.Errorf("%q reports %v, and csv.md 5.1 has %q", one.to, err, one.line)
		}
	}
}
