package csv_test

import (
	"fmt"
	"os"
	"regexp"
	"strings"
	"testing"

	"github.com/odipar/ymxs/go/csv"
	"github.com/odipar/ymxs/go/text"
	"github.com/odipar/ymxs/go/ymxs"
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
		written, err := csv.Write(multi)
		if err != nil {
			t.Fatalf("%s: %v", named, err)
		}
		back, err := csv.Read(written)
		if err != nil {
			t.Fatalf("%s: %v", named, err)
		}
		again := text.Write(back)
		if again != string(said) {
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
	written, err := csv.Write(multi)
	if err != nil {
		t.Fatal(err)
	}
	if written != string(tables) {
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
			t.Errorf("%q reports %q, and the fault is %q", one.said,
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
	made, err := csv.Write(multi)
	if err != nil {
		t.Fatal(err)
	}
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
		{"a tone,0\n\n### value\nrow,value\n0,13\n1,0",
			"a\rtone,0\n\n### value\nrow,value1,value2\n0,13,1\n1,0,1",
			`source a\rtone has a row of several values, and version 3 has one value a row`},
	} {
		_, err := csv.Read(strings.Replace(three, one.from, one.to, 1))
		if err == nil || err.Error() != one.line {
			t.Errorf("%q reports %v, and csv.md 5.1 has %q", one.to, err, one.line)
		}
	}
}

// A text with a line feed or a carriage return in it is an error of the
// writing, and the error is the one line of tools.md 7.4: the text with
// each line feed as the two characters \n and each carriage return as \r.
func TestATextWithALineFeedIsAnErrorOfTheWriting(t *testing.T) {
	said, err := os.ReadFile("../../doc/tunes/example.json")
	if err != nil {
		t.Fatal(err)
	}
	multi, err := text.Read(string(said))
	if err != nil {
		t.Fatal(err)
	}
	for _, one := range []struct{ title, reported string }{
		{"a\nb", `a\nb`}, {"a\rb", `a\rb`}, {"a\r\nb", `a\r\nb`},
	} {
		multi.Tunes[0].Title = one.title
		written, err := csv.Write(multi)
		line := "a value with a line feed or a carriage return in it, which this form " +
			"cannot write: " + one.reported
		if err == nil || err.Error() != line || written != "" {
			t.Errorf("a title of %q writes %q and reports %v, and tools.md 7.4 has %q",
				one.title, written, err, line)
		}
	}
}

// example is a block of a document under its clause, after the fence that
// opens it.
func example(t *testing.T, named, clause, fence string) string {
	t.Helper()
	said, err := os.ReadFile("../../doc/" + named)
	if err != nil {
		t.Fatal(err)
	}
	found := regexp.MustCompile("(?s)\\*\\*" + regexp.QuoteMeta(clause) + "\\*\\*.*?" +
		regexp.QuoteMeta(fence) + "\n(.*?)```").FindStringSubmatch(string(said))
	if found == nil {
		t.Fatalf("%s has no example under %s", named, clause)
	}
	return found[1]
}

// The example of csv.md 8 is the tables this writes for the example of
// json.md 10, of version 7, and they read back to that JSON.
func TestTheExampleOfTheDocumentIsTheTablesThisWrites(t *testing.T) {
	json := example(t, "json.md", "10.1", "```json")
	tables := example(t, "csv.md", "8.1", "```")
	if !strings.Contains(tables, "ymxs,7,1") || !strings.Contains(tables,
		",deferred,mapping\n0,0,8,1,50,60,1,1,0,0\n1,1,,,50,61,0,0,,\n3,2,,,,,,,,\n") {
		t.Fatalf("the example is of version 7 with a mapping cell:\n%s", tables)
	}
	multi, err := text.Read(json)
	if err != nil {
		t.Fatal(err)
	}
	written, err := csv.Write(multi)
	if err != nil {
		t.Fatal(err)
	}
	if written != tables {
		t.Errorf("the tables moved:\n%s", written)
	}
	back, err := csv.Read(tables)
	if err != nil {
		t.Fatal(err)
	}
	if text.Write(back) != json {
		t.Errorf("the tables read as another tune:\n%s", text.Write(back))
	}
}

// volume is the transformation Vn of SPEC.md 3.6.5: 16 entries, entry k is
// k × n divided by 15, for the keys 0 to 15.
func volume(n int) ymxs.Transformation {
	entries := make([]int, 16)
	for k := range entries {
		entries[k] = k * n / 15
	}
	return ymxs.Transformation{Name: fmt.Sprintf("V%02d", n), Entries: entries}
}

// fade is a note on setVoiceA through V15, then a retune, two remaps of
// its volume, V05 then the identity in every part, then a stop.
func fade() ymxs.Multi {
	note := ymxs.Triple{Name: "a note", Table: ymxs.Repeating([]ymxs.Three{
		{First: 163, Second: 2, Third: 15}, {First: 142, Second: 12, Third: 12}}, 0)}
	effect := func(effect ymxs.Effect) ymxs.Row {
		return ymxs.Row{Registers: map[ymxs.Register]int{},
			Effects: map[ymxs.Timer]ymxs.Effect{ymxs.TimerB: effect}}
	}
	rows := []ymxs.Row{
		effect(ymxs.StartTriple{Target: ymxs.SetVoice{Voice: ymxs.VoiceA}, Source: note,
			Timing: ymxs.Timing{Prescaler: ymxs.By50, Count: 60, TimerReset: true,
				PlaceReset: true}, Deferred: true,
			Mapping: ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{}, volume(15))}),
		effect(ymxs.Bend(ymxs.By50, 61)),
		effect(ymxs.Remap{Mapping: ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{}, volume(5))}),
		effect(ymxs.Remap{}),
		effect(ymxs.Stop{}),
	}
	return ymxs.NewMulti(ymxs.Tune{Title: "a fade", Writer: "by hand", Rate: 50,
		Table: ymxs.Repeating(rows, 0)})
}

// A tune through transformations crosses to the tables and back: a
// transformation block and an entry block of a row line an entry for each
// transformation, 16 for each volume here, in number order after the
// sources; the mapping cell 0 or the numbers of the parts separated by one
// space, and empty at a retune and a stop (csv.md 3.1, 3.9, 6.3).
func TestATuneThroughTransformationsCrossesToTheTablesAndBack(t *testing.T) {
	multi := fade()
	written, err := csv.Write(multi)
	if err != nil {
		t.Fatal(err)
	}
	for _, each := range []string{
		"\n### value\nrow,value1,value2,value3\n0,163,2,15\n1,142,12,12\n\n" +
			"### transformation\nname\nV15\n\n### entry\nkey,entry\n0,0\n1,1\n",
		"\n15,15\n\n### transformation\nname\nV05\n\n### entry\nkey,entry\n0,0\n",
		"\n15,5\n\n### registers\n",
		"### timerB\nrow,shape,target,source,prescaler,count,timerReset,placeReset," +
			"deferred,mapping\n0,0,17,1,50,60,1,1,1,0 0 1\n1,1,,,50,61,0,0,,\n" +
			"2,3,,,,,,,,0 0 2\n3,3,,,,,,,,0\n4,2,,,,,,,,\n",
	} {
		if !strings.Contains(written, each) {
			t.Errorf("the tables have no %q:\n%s", each, written)
		}
	}
	back, err := csv.Read(written)
	if err != nil {
		t.Fatal(err)
	}
	if text.Write(back) != text.Write(multi) {
		t.Errorf("the tune moved crossing to the tables and back:\n%s", text.Write(back))
	}
	again, err := csv.Write(back)
	if err != nil || again != written {
		t.Errorf("the tables moved: %v", err)
	}
}

// A reader reads a transformation of any number of entries, 0 included:
// an entry block of E row lines is E entries, and a count outside 1 to 256
// is an error of the structure, the line the check reports after the
// reading, under the number of the transformation (csv.md 3.12, SPEC.md
// 1.11, 1.12 item 2.3).
func TestAReaderReadsATransformationOfAnyNumberOfEntries(t *testing.T) {
	for _, e := range []int{0, 16, 256, 257} {
		multi := fade()
		zeros := ymxs.Transformation{Name: "zeros", Entries: make([]int, e)}
		multi.Tunes[0].Table.Rows[2].Effects[ymxs.TimerB] = ymxs.Remap{Mapping: ymxs.Mapped(
			ymxs.Identity{}, ymxs.Identity{}, zeros)}
		written, err := csv.Write(multi)
		if err != nil {
			t.Fatal(err)
		}
		multi, err = csv.Read(written)
		if len(multi.Tunes) != 1 {
			t.Fatalf("%d entries report %v", e, err)
		}
		remap := multi.Tunes[0].Table.Rows[2].Effects[ymxs.TimerB].(ymxs.Remap)
		if read := remap.Mapping.Parts[2].(ymxs.Transformation); !ymxs.TransformationEqual(read,
			zeros) {
			t.Errorf("%d entries read as %d", e, len(read.Entries))
		}
		want := ""
		if e == 0 || e == 257 {
			want = fmt.Sprintf("tune 1: transformation 2, zeros: the transformation has"+
				" %d entries, and a transformation has 1 to 256", e)
		}
		said := ""
		if err != nil {
			said = err.Error()
		}
		if said != want {
			t.Errorf("%d entries report %q, and SPEC.md 1.11 reads %q", e, said, want)
		}
	}
}

// In a file of version 6 or before a reader skips a mapping column and
// reads every start with the mapping of no parts; a transformation or an
// entry block there is a block of any other name, its cells unread, and
// shape 3 is an error of the form (csv.md 3.10, 4.1 step 5, 5.1).
func TestAFileOfVersion6SkipsTheMappingAndHasNoTransformationBlock(t *testing.T) {
	written, err := csv.Write(fade())
	if err != nil {
		t.Fatal(err)
	}
	six := strings.Replace(written, "ymxs,7,1", "ymxs,6,1", 1)
	from := strings.Index(six, "### transformation\n")
	to := strings.Index(six, "### registers\n")
	if from < 0 || to < from {
		t.Fatal("the tables have transformation blocks before the registers")
	}
	// the blocks of the transformations cut, then an entry block in their place
	cut := six[:from] + six[to:]
	entry := six[:from] + "### entry\nkey,entry\n0,x\n\n" + six[to:]
	remapless := strings.Replace(cut, "2,3,,,,,,,,0 0 2\n3,3,,,,,,,,0\n", "", 1)
	if remapless == cut {
		t.Fatal("the tables have no remaps")
	}
	multi, err := csv.Read(remapless)
	if err != nil {
		t.Fatalf("a file of version 6 reads: %v", err)
	}
	start := multi.Tunes[0].Table.Rows[0].Effects[ymxs.TimerB].(ymxs.Start)
	if parts := ymxs.StartMapping(start).Parts; len(parts) != 0 {
		t.Errorf("the start reads with %d parts", len(parts))
	}
	for _, one := range []struct{ named, tables, line string }{
		{"a transformation block", six, "tune 1 opens a \"### transformation\" table, which" +
			" this form does not have"},
		{"an entry block", entry, "tune 1 opens a \"### entry\" table, which this form" +
			" does not have"},
		{"shape 3", cut, "row 2 sets shape 3 of an effect, and a shape is 0, 1 or 2"},
	} {
		_, err := csv.Read(one.tables)
		if err == nil || err.Error() != one.line {
			t.Errorf("%s in version 6 reports %v, and csv.md 5.1 has %q", one.named, err,
				one.line)
		}
	}
}

// Each error of the form of a transformation and a mapping is its line of
// csv.md 5.1: a mapping cell verified as whole numbers, one or more, then
// of several a number other than 0 among them, then each 0 to M; CELL the
// cell as read.
func TestAnErrorOfTheFormOfAMappingIsItsLine(t *testing.T) {
	written, err := csv.Write(fade())
	if err != nil {
		t.Fatal(err)
	}
	remap := "2,3,,,,,,,,0 0 2\n"
	whole := ", and this form requires a whole number a part"
	identity := ", and a mapping of the identity in every part is 0"
	for _, one := range []struct{ from, to, line string }{
		{remap, "2,3,,,,,,,,0 0 3\n", "row 2 maps through transformation 3, and the tune" +
			" has 2"},
		{remap, "2,3,,,,,,,,0 -1 x\n", `mapping is "0 -1 x"` + whole},
		{remap, "2,3,,,,,,,,0 -1 1\n", "row 2 maps through transformation -1, and the" +
			" tune has 2"},
		{remap, "2,3,,,,,,,, \t \n", `mapping is " ` + "\t" + ` "` + whole},
		{remap, "2,3,,,,,,,,\n", `mapping is ""` + whole},
		{remap, "2,3,,,,,,,,0 2147483648 1\n", `mapping is "0 2147483648 1"` + whole},
		{remap, "2,3,,,,,,,,\" 0  +0 \"\n", `mapping is " 0  +0 "` + identity},
		{remap, "2,3,,,,,,,,\" 0 +2 0 \"\n", ""},
		{remap, "2,3,,,,,,,,5\n", "row 2 maps through transformation 5, and the tune has 2"},
		{remap, "2,4,,,,,,,,0\n", "row 2 sets shape 4 of an effect, and a shape is 0, 1, 2" +
			" or 3"},
		{remap, "2,-1,,,,,,,,0\n", "row 2 sets shape -1 of an effect, and a shape is 0, 1," +
			" 2 or 3"},
		{",deferred,mapping\n", ",deferred\n", `mapping is ""` + whole},
		{"### transformation\nname\nV05\n", "### transformation\nname\nV05\nV06\n",
			"tune 1 opens a transformation with 2 rows, and one row opens it"},
		{"### transformation\nname\nV15\n\n", "### entry\nkey,entry\n0,1\n\n" +
			"### transformation\nname\nV15\n\n",
			"tune 1 opens entries before any transformation"},
		{"key,entry\n0,0\n1,1\n", "key,entry\n0,0\n1,one\n",
			`entry is "one", and this form requires a whole number`},
		{"key,entry\n0,0\n1,1\n", "key\n0,0\n1,1\n",
			`entry is "", and this form requires a whole number`},
		{"### registers", "### transformation\nname\nV01\n\n### entry\nkey,entry\n" +
			"0,0\n\n### registers", "transformation 3, V01, is used by no row, and a" +
			" transformation a tune does not map through is dropped where this form is read"},
		{"### registers", "### transformations\nname\n", "tune 1 opens a" +
			" \"### transformations\" table, which this form does not have"},
	} {
		if !strings.Contains(written, one.from) {
			t.Fatalf("the tables have no %q", one.from)
		}
		_, err := csv.Read(strings.Replace(written, one.from, one.to, 1))
		if one.line == "" {
			if err != nil {
				t.Errorf("%q reports %v", one.to, err)
			}
			continue
		}
		if err == nil || err.Error() != one.line {
			t.Errorf("%q reports %v, and csv.md 5.1 has %q", one.to, err, one.line)
		}
	}
}

// A transformation name with a line feed in it is an error of the
// writing, as a source name is (tools.md 7.4).
func TestATransformationNameWithALineFeedIsAnErrorOfTheWriting(t *testing.T) {
	multi := fade()
	start := multi.Tunes[0].Table.Rows[0].Effects[ymxs.TimerB].(ymxs.StartTriple)
	named := volume(15)
	named.Name = "V\n15"
	start.Mapping = ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{}, named)
	multi.Tunes[0].Table.Rows[0].Effects[ymxs.TimerB] = start
	line := "a value with a line feed or a carriage return in it, which this form " +
		`cannot write: V\n15`
	if written, err := csv.Write(multi); err == nil || err.Error() != line || written != "" {
		t.Errorf("writes %q and reports %v, and tools.md 7.4 has %q", written, err, line)
	}
}

// A transformation of the empty name is a row line of "", since a reader
// skips a blank line, and crosses back (csv.md 2.5, 6.4).
func TestATransformationOfTheEmptyNameCrossesBack(t *testing.T) {
	multi := fade()
	start := multi.Tunes[0].Table.Rows[0].Effects[ymxs.TimerB].(ymxs.StartTriple)
	nameless := volume(15)
	nameless.Name = ""
	start.Mapping = ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{}, nameless)
	multi.Tunes[0].Table.Rows[0].Effects[ymxs.TimerB] = start
	written, err := csv.Write(multi)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(written, "### transformation\nname\n\"\"\n") {
		t.Errorf("the empty name is no row line of \"\":\n%s", written)
	}
	back, err := csv.Read(written)
	if err != nil {
		t.Fatalf("the tables read: %v", err)
	}
	if again, err := csv.Write(back); err != nil || again != written {
		t.Errorf("the tables moved: %v", err)
	}
}

// The name of a source or of a transformation in a reported line has each
// carriage return as the two characters \r, so the line is one line
// (SPEC.md 1.12, csv.md 5.1): in the prefix of a transformation, in the
// lines of an unstarted source and an unused transformation, and in the
// line of a source of two shapes. A line feed ends a line of this form
// (csv.md 1.2), so a name read from one has none in it.
func TestANameInAReportedLineIsOneLine(t *testing.T) {
	written, err := csv.Write(fade())
	if err != nil {
		t.Fatal(err)
	}
	unused := strings.Replace(written, "\n\n### transformation\nname\nV15\n",
		"\n\n### source\nname,repeat\nx\ry,0\n\n### value\nrow,value\n0,1\n\n"+
			"### transformation\nname\nV15\n", 1)
	unused = strings.Replace(unused, "### registers", "### transformation\nname\nV\r01\n\n"+
		"### entry\nkey,entry\n0,0\n\n### registers", 1)
	for _, one := range []struct{ in, line string }{
		{strings.Replace(written, "name\nV05\n\n### entry\nkey,entry\n0,0\n",
			"name\nV\r05\n\n### entry\nkey,entry\n0,300\n", 1),
			`tune 1: transformation 2, V\r05: entry 0 is 300, and an entry is 0 to 255`},
		{unused, `source 2, x\ry, is started by no row, and a source a tune does not run` +
			" is dropped where this form is read\n" + `transformation 3, V\r01, is used by` +
			` no row, and a transformation a tune does not map through is dropped where` +
			` this form is read`},
		{strings.Replace(strings.Replace(written, "a note,0", "a\r\rnote,0", 1),
			"0,163,2,15\n", "0,163,2\n", 1),
			`source a\r\rnote has rows of 2 and of 3 values, and a source has one shape`},
	} {
		if one.in == written {
			t.Fatalf("the replacement moved no text for %q", one.line)
		}
		_, err := csv.Read(one.in)
		if err == nil || err.Error() != one.line {
			t.Errorf("reports %q, and csv.md 5.1 has %q", err, one.line)
		}
	}
}

// White space is the Unicode property White_Space (csv.md 1.3), and a
// whole number is of the digits 0 to 9 (1.7).
func TestWhiteSpaceIsThePropertyWhiteSpaceAndADigitIs0To9(t *testing.T) {
	written, err := csv.Write(fade())
	if err != nil {
		t.Fatal(err)
	}
	value := "\n0,163,2,15\n"
	for _, white := range []string{" ", "\u0085", " ", " ", "　"} {
		if _, err := csv.Read(strings.Replace(written, value, "\n0,163"+white+",2,15\n",
			1)); err != nil {
			t.Errorf("U+%04X is white space: %v", []rune(white)[0], err)
		}
	}
	for _, one := range []struct{ to, line string }{
		{"\n0,163\u001c,2,15\n", "value1 is \"163\u001c\", and this form requires a" +
			" whole number"},
		{"\n0,١٦٣,2,15\n", "value1 is \"١٦٣\", and this" +
			" form requires a whole number"},
	} {
		_, err := csv.Read(strings.Replace(written, value, one.to, 1))
		if err == nil || err.Error() != one.line {
			t.Errorf("%q reports %v, and csv.md 5.1 has %q", one.to, err, one.line)
		}
	}
}
