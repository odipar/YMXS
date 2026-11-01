package text_test

import (
	"fmt"
	"os"
	"regexp"
	"strings"
	"testing"

	"github.com/odipar/ymxs/go/text"
	"github.com/odipar/ymxs/go/ymxs"
)

// The tunes of doc/tunes, the examples the documents carry.
var tunes = []string{"circus", "digidrum", "example", "retrigger", "turrican-2", "two-tunes"}

func read(t *testing.T, named string) string {
	t.Helper()
	said, err := os.ReadFile("../../doc/tunes/" + named + ".json")
	if err != nil {
		t.Fatal(err)
	}
	return string(said)
}

// A tune written as text and read back is the text it was: the mapping loses
// no part of a tune, and the layout is one text.
func TestTheTextATuneWasWrittenAsReadsBackToThatText(t *testing.T) {
	for _, named := range tunes {
		said := read(t, named)
		multi, err := text.Read(said)
		if err != nil {
			t.Fatalf("%s: %v", named, err)
		}
		if back := text.Write(multi); back != said {
			t.Errorf("%s: the text moved: %d characters in, %d out", named,
				len(said), len(back))
		}
	}
}

// example is the tune of json.md 10, the example as the document has it.
func example(t *testing.T) string {
	t.Helper()
	said, err := os.ReadFile("../../doc/json.md")
	if err != nil {
		t.Fatal(err)
	}
	found := regexp.MustCompile("(?s)\\*\\*10\\.1\\*\\*.*?```json\n(.*?)```").
		FindStringSubmatch(string(said))
	if found == nil {
		t.Fatal("json.md has no example under 10.1")
	}
	return found[1]
}

// The example of json.md 10 is the text this writes for it: version 7, an
// empty transformations array and the mapping column after deferred.
func TestTheExampleOfTheDocumentIsTheTextThisWrites(t *testing.T) {
	said := example(t)
	for _, each := range []string{`"version": 7,`, `"transformations": [],`,
		`"mapping": [0,-1,-1,-1]`} {
		if !strings.Contains(said, each) {
			t.Fatalf("the example has no %s", each)
		}
	}
	multi, err := text.Read(said)
	if err != nil {
		t.Fatal(err)
	}
	if back := text.Write(multi); back != said {
		t.Errorf("the example moved:\n%s", back)
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

// fade is a note on setVoiceA through the identity, then two remaps of its
// volume, V5 then V10, then a stop: the fade of SPEC.md 3.6.5.
func fade() ymxs.Tune {
	note := ymxs.Triple{Name: "a note", Table: ymxs.Repeating([]ymxs.Three{
		{First: 163, Second: 2, Third: 15}, {First: 142, Second: 12, Third: 12}}, 0)}
	start := ymxs.StartTriple{Target: ymxs.SetVoice{Voice: ymxs.VoiceA}, Source: note,
		Timing: ymxs.Timing{Prescaler: ymxs.By50, Count: 60, TimerReset: true,
			PlaceReset: true}}
	remap := func(n int) ymxs.Row {
		return ymxs.Row{Registers: map[ymxs.Register]int{}, Effects: map[ymxs.Timer]ymxs.Effect{
			ymxs.TimerA: ymxs.Remap{Mapping: ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{},
				volume(n))}}}
	}
	rows := []ymxs.Row{
		{Registers: map[ymxs.Register]int{ymxs.R7: 56},
			Effects: map[ymxs.Timer]ymxs.Effect{ymxs.TimerA: start}},
		remap(5), remap(10),
		{Registers: map[ymxs.Register]int{}, Effects: map[ymxs.Timer]ymxs.Effect{
			ymxs.TimerA: ymxs.Stop{}}},
	}
	return ymxs.Tune{Title: "a fade", Writer: "by hand", Rate: 50,
		Table: ymxs.Repeating(rows, 0)}
}

// A tune through transformations is written as json.md 3.7 and 6.7 define:
// the transformations in the order the rows first map through them, name
// then entries; the mapping 0 at the start, an array of a number a part at
// each remap, and -1 at the stop. It reads back to the text it was.
func TestATuneThroughTransformationsIsWrittenAsTheFormDefines(t *testing.T) {
	said := text.Write(ymxs.NewMulti(fade()))
	for _, each := range []string{
		"      \"transformations\": [\n        {\"name\": \"V05\", \"entries\": " +
			"[0,0,0,1,1,1,2,2,2,3,3,3,4,4,4,5]},\n",
		"        {\"name\": \"V10\", \"entries\": [0,0,1,2,2,3,4,4,5,6,6,7,8,8,9,10]}\n" +
			"      ],\n      \"registers\": {",
		"        \"deferred\": [0,-1,-1,-1],\n        \"mapping\": [0,[0,0,1],[0,0,2],-1]\n",
		"        \"shape\": [0,3,3,2],\n        \"target\": [17,-1,-1,-1],\n",
		"        \"prescaler\": [50,-1,-1,-1],\n",
	} {
		if !strings.Contains(said, each) {
			t.Errorf("the text has no %q:\n%s", each, said)
		}
	}
	multi, err := text.Read(said)
	if err != nil {
		t.Fatal(err)
	}
	if back := text.Write(multi); back != said {
		t.Errorf("the tune moved:\n%s", back)
	}
	remap, ok := multi.Tunes[0].Table.Rows[1].Effects[ymxs.TimerA].(ymxs.Remap)
	if !ok || len(remap.Mapping.Parts) != 3 ||
		!ymxs.TransformationEqual(remap.Mapping.Parts[2].(ymxs.Transformation), volume(5)) {
		t.Errorf("row 1 reads as %#v", multi.Tunes[0].Table.Rows[1].Effects[ymxs.TimerA])
	}
}

// The entries of a transformation are 20 values a line, each line after
// the first at the indent of depth 6 (json.md 9.6): 256 entries are 13
// lines, the last of 16 values. They read back to the text they were.
func TestTheEntriesOfATransformationAre20ValuesALine(t *testing.T) {
	tune := fade()
	levels := ymxs.Transformation{Name: "levels", Entries: make([]int, 256)}
	for k := range levels.Entries {
		levels.Entries[k] = k / 16
	}
	tune.Table.Rows[1].Effects[ymxs.TimerA] = ymxs.Remap{Mapping: ymxs.Mapped(
		ymxs.Identity{}, ymxs.Identity{}, levels)}
	said := text.Write(ymxs.NewMulti(tune))
	first := "        {\"name\": \"levels\", \"entries\": [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0," +
		"1,1,1,1,\n            1,1,1,1,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,\n"
	last := "\n            " + strings.Repeat("15,", 15) + "15]},\n"
	from, to := strings.Index(said, first), strings.Index(said, last)
	if from < 0 || to < from {
		t.Fatalf("the entries are not 20 a line:\n%s", said)
	}
	if lines := strings.Count(said[from:to+len(last)], "\n"); lines != 13 {
		t.Errorf("256 entries are %d lines:\n%s", lines, said)
	}
	multi, err := text.Read(said)
	if err != nil {
		t.Fatal(err)
	}
	if back := text.Write(multi); back != said {
		t.Errorf("the tune moved:\n%s", back)
	}
}

// A reader reads a transformation of any number of entries, 0 included,
// and a count outside 1 to 256 is an error of the structure: the line the
// check reports after the reading, under the number of the transformation
// (json.md 3.7, SPEC.md 1.11, 1.12 item 2.3).
func TestAReaderReadsATransformationOfAnyNumberOfEntries(t *testing.T) {
	for _, e := range []int{0, 16, 256, 257} {
		tune := fade()
		zeros := ymxs.Transformation{Name: "zeros", Entries: make([]int, e)}
		tune.Table.Rows[1].Effects[ymxs.TimerA] = ymxs.Remap{Mapping: ymxs.Mapped(
			ymxs.Identity{}, ymxs.Identity{}, zeros)}
		multi, err := text.Read(text.Write(ymxs.NewMulti(tune)))
		if len(multi.Tunes) != 1 {
			t.Fatalf("%d entries report %v", e, err)
		}
		remap := multi.Tunes[0].Table.Rows[1].Effects[ymxs.TimerA].(ymxs.Remap)
		if read := remap.Mapping.Parts[2].(ymxs.Transformation); !ymxs.TransformationEqual(read,
			zeros) {
			t.Errorf("%d entries read as %d", e, len(read.Entries))
		}
		want := ""
		if e == 0 || e == 257 {
			want = fmt.Sprintf("tune 1: transformation 1, zeros: the transformation has"+
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

// The numbers of the transformations in a file are its order (json.md 3.8):
// a file whose transformations come in another order than the rows first
// map through them reads, and is emitted in the order of SPEC.md 1.9, its
// mapping column numbered again.
func TestTheTransformationsOfAFileAreEmittedInTheOrderOfFirstUse(t *testing.T) {
	said := text.Write(ymxs.NewMulti(fade()))
	five := regexp.MustCompile(`(?s)        \{"name": "V05".*?\]\},\n`).FindString(said)
	ten := regexp.MustCompile(`(?s)        \{"name": "V10".*?\]\}\n`).FindString(said)
	if five == "" || ten == "" {
		t.Fatal("the fade has V05 and V10")
	}
	swapped := strings.Replace(said, five+ten, strings.TrimSuffix(ten, "\n")+",\n"+
		strings.TrimSuffix(five, ",\n")+"\n", 1)
	swapped = strings.Replace(swapped, `[0,[0,0,1],[0,0,2],-1]`, `[0,[0,0,2],[0,0,1],-1]`, 1)
	if swapped == said {
		t.Fatal("the swap moved no line")
	}
	multi, err := text.Read(swapped)
	if err != nil {
		t.Fatal(err)
	}
	if back := text.Write(multi); back != said {
		t.Errorf("the file read as another tune:\n%s", back)
	}
}

// A mapping of every part the identity is written as 0, and reads back as
// the mapping of no parts (json.md 6.7).
func TestAMappingOfTheIdentityInEveryPartIsWrittenAs0(t *testing.T) {
	tune := fade()
	tune.Table.Rows[1].Effects[ymxs.TimerA] = ymxs.Remap{Mapping: ymxs.Mapped(
		ymxs.Identity{}, ymxs.Identity{}, ymxs.Identity{})}
	said := text.Write(ymxs.NewMulti(tune))
	if !strings.Contains(said, `"mapping": [0,0,[0,0,1],-1]`) {
		t.Fatalf("the identity in every part is not 0:\n%s", said)
	}
	multi, err := text.Read(said)
	if err != nil {
		t.Fatal(err)
	}
	if parts := multi.Tunes[0].Table.Rows[1].Effects[ymxs.TimerA].(ymxs.Remap).Mapping.Parts; len(parts) != 0 {
		t.Errorf("0 reads as %d parts", len(parts))
	}
}

func TestATextThatIsNotThisFormIsRefused(t *testing.T) {
	for _, said := range []string{"", "nope", "{}", `{"format":"nope","version":1}`,
		`{"format":"ymxs","version":2,"tunes":[]}`} {
		if _, err := text.Read(said); err == nil {
			t.Errorf("%q reads as a multi", said)
		}
	}
}

// three is a file of version 3, one value a row and R8 its target.
const three = `{"format":"ymxs","version":3,"tunes":[{"title":"","composer":"",
		"writer":"t","rate":50,"rows":2,"repeat":0,
		"sources":[{"name":"a tone","repeat":0,"values":[13,0]}],
		"timerA":{"shape":[0,-1],"target":[8,-1],"source":[1,-1],
		"prescaler":[50,-1],"count":[60,-1],"timerReset":[1,-1],"placeReset":[1,-1]}}]}`

// A file of version 3 reaches the targets 0 to 13 (json.md 2.4): such a
// file reads, and a target above 13 in one is an error of the form, one
// line of json.md 8.1.
func TestAFileOfTheVersionBeforeThisOneReachesTargets0To13(t *testing.T) {
	if _, err := text.Read(three); err != nil {
		t.Fatalf("a file of version 3 reads: %v", err)
	}
	line := "target 14, and version 3 reaches 0 to 13"
	_, err := text.Read(strings.Replace(three, `"target":[8,-1]`, `"target":[14,-1]`, 1))
	if err == nil || err.Error() != line {
		t.Errorf("a target of 14 in version 3 reports %v, and json.md 8.1 has %q", err,
			line)
	}
}

// In a file of version 6 or before a reader skips a mapping column and a
// transformations key, and reads every start with the mapping of no parts;
// shape 3 there is an error of the form (json.md 2.4).
func TestAFileOfVersion6ReadsEveryStartWithTheMappingOfNoParts(t *testing.T) {
	six := strings.Replace(three, `"version":3`, `"version":6`, 1)
	skipped := strings.Replace(six, `"placeReset":[1,-1]`,
		`"placeReset":[1,-1],"deferred":[0,-1],"mapping":[[9,9],"x"]`, 1)
	skipped = strings.Replace(skipped, `"sources":`, `"transformations":5,"sources":`, 1)
	multi, err := text.Read(skipped)
	if err != nil {
		t.Fatalf("a file of version 6 with a mapping column reads: %v", err)
	}
	start := multi.Tunes[0].Table.Rows[0].Effects[ymxs.TimerA].(ymxs.Start)
	if parts := ymxs.StartMapping(start).Parts; len(parts) != 0 {
		t.Errorf("the start reads with %d parts", len(parts))
	}
	written := text.Write(multi)
	if !strings.Contains(written, `"version": 7,`) || !strings.Contains(written,
		`"mapping": [0,-1]`) {
		t.Errorf("a file of version 6 is written as version 7:\n%s", written)
	}
	line := "row 1 sets shape 3 on Timer A, and a shape is -1, 0, 1 or 2"
	_, err = text.Read(strings.Replace(skipped, `"shape":[0,-1]`, `"shape":[0,3]`, 1))
	if err == nil || err.Error() != line {
		t.Errorf("shape 3 in version 6 reports %v, and json.md 8.1 has %q", err, line)
	}
}

// seven is the fade as a file of version 7.
func seven(t *testing.T) string {
	t.Helper()
	return text.Write(ymxs.NewMulti(fade()))
}

// Each error of the form of a transformation and a mapping is its line of
// json.md 8.1, read in the order of json.md 7.1: the mapping verified as 0
// or an array of whole numbers, then an element other than 0 among them,
// then each element 0 to M; the X of a line the value as JSON text with no
// white space, each number as the file writes it and the members of an
// object in file order.
func TestAnErrorOfTheFormOfAMappingIsItsLine(t *testing.T) {
	said := seven(t)
	column := `"mapping": [0,[0,0,1],[0,0,2],-1]`
	entries := regexp.MustCompile(`(?s)"name": "V05", "entries": \[.*?\]`).FindString(said)
	if !strings.Contains(said, column) || entries == "" {
		t.Fatal("the fade has a mapping column and V05")
	}
	with := func(mapping string) string {
		return strings.Replace(said, column, `"mapping": `+mapping, 1)
	}
	requires := ", and this form requires 0 or an array of whole numbers"
	identity := ", and a mapping of the identity in every part is 0"
	for _, one := range []struct{ in, line string }{
		{with(`[0,5,[0,0,2],-1]`), "Timer A's mapping at row 1 is 5" + requires},
		{with(`[0,-1,[0,0,2],-1]`), "Timer A's mapping at row 1 is -1" + requires},
		{with(`[0,"x",[0,0,2],-1]`), `Timer A's mapping at row 1 is "x"` + requires},
		{with(`[0, [0, 1.50, 1], [0,0,2], -1]`),
			"Timer A's mapping at row 1 is [0,1.50,1]" + requires},
		{with(`[0,[0,0,4294967296],[0,0,2],-1]`),
			"Timer A's mapping at row 1 is [0,0,4294967296]" + requires},
		{with(`[0,{"b": [1, 2e1], "a": "\u0001\n", "b": null},[0,0,2],-1]`),
			`Timer A's mapping at row 1 is {"b":null,"a":"\u0001\n"}` + requires},
		{with(`[0,[0,0,"y"],[0,0,9],-1]`), `Timer A's mapping at row 1 is [0,0,"y"]` + requires},
		{with(`[null,[0,0,1],[0,0,2],-1]`), "Timer A's mapping at row 0 is null" + requires},
		{with(`[0,[0,0],[0,0,2],-1]`), "Timer A's mapping at row 1 is [0,0]" + identity},
		{with(`[[],[0,0,1],[0,0,2],-1]`), "Timer A's mapping at row 0 is []" + identity},
		{with(`[0,[0,0,3],[0,0,2],-1]`), "row 1 maps through transformation 3, and the" +
			" tune has 2"},
		{with(`[0,[0,-1,9],[0,0,2],-1]`), "row 1 maps through transformation -1, and the" +
			" tune has 2"},
		{with(`[0,[0,0,1],[0,0,2]]`), "Timer A's mapping is 3 values long, and the tune" +
			" has 4 rows"},
		{with(`{"a": 1}`), `Timer A's mapping is {"a":1}, and this form requires a column`},
		{strings.Replace(said, ",\n        "+column, "", 1),
			`Timer A has no "mapping" column`},
		{strings.Replace(said, `"shape": [0,3,3,2]`, `"shape": [0,3,4,2]`, 1),
			"row 2 sets shape 4 on Timer A, and a shape is -1, 0, 1, 2 or 3"},
		{strings.Replace(said, entries, `"name": "V05", "entries": [0, "z"]`, 1),
			`V05 at entry 1 is "z", and this form requires a whole number`},
		{strings.Replace(said, entries, `"name": "V05", "entries": [0, [1]]`, 1),
			`V05 at entry 1 is [1], and this form requires a whole number`},
		{strings.Replace(said, entries, `"name": "V05", "entries": {"k": 1}`, 1),
			`entries is {"k":1}, and this form requires an array`},
		{strings.Replace(said, entries, `"name": 5, "entries": [0]`, 1),
			`name is 5, and this form requires a text`},
		{strings.Replace(said, `{`+entries, `7, {`+entries, 1),
			"entries is null, and this form requires an array"},
		{strings.Replace(said, `"transformations": [`, `"transformationz": [`, 1),
			"transformations is null, and this form requires an array"},
	} {
		_, err := text.Read(one.in)
		if err == nil || err.Error() != one.line {
			t.Errorf("reports %v, and json.md 8.1 has %q", err, one.line)
		}
	}
}

// A transformation no row maps through is an error of the form, a line
// for each, after the lines of the unstarted sources (json.md 8.1, tools.md
// 4.5).
func TestATransformationNoRowUsesIsAnErrorOfTheForm(t *testing.T) {
	said := seven(t)
	unused := `{"name": "V01", "entries": [0]}`
	in := strings.Replace(said, `"transformations": [`, `"transformations": [`+unused+`, `,
		1)
	in = strings.Replace(in, `"mapping": [0,[0,0,1],[0,0,2],-1]`,
		`"mapping": [0,[0,0,2],[0,0,3],-1]`, 1)
	in = strings.Replace(in, `"sources": [`, `"sources": [{"name": "x", "values": [1]}, `, 1)
	in = strings.Replace(in, `"source": [1,-1,-1,-1]`, `"source": [2,-1,-1,-1]`, 1)
	line := "source 1, x, is started by no row, and a source a tune does not run is" +
		" dropped where this form is read\ntransformation 1, V01, is used by no row, and" +
		" a transformation a tune does not map through is dropped where this form is read"
	if _, err := text.Read(in); err == nil || err.Error() != line {
		t.Errorf("reports %v, and json.md 8.1 has %q", err, line)
	}
}

// The name of a source or of a transformation in a reported line has each
// line feed as the two characters \n and each carriage return as \r, so
// the line is one line (SPEC.md 1.12, json.md 8.1): in the prefix of a
// transformation, in the lines of an unstarted source and an unused
// transformation, and in each line of the form that writes a name.
func TestANameInAReportedLineIsOneLine(t *testing.T) {
	said := seven(t)
	entries := `"name": "V05", "entries": [0,0,0,1,1,1,2,2,2,3,3,3,4,4,4,5]`
	note := `{"name": "a note", "repeat": 0, "values": [[163,2,15],[142,12,12]]}`
	if !strings.Contains(said, entries) || !strings.Contains(said, note) {
		t.Fatal("the fade has V05 and a note")
	}
	unused := strings.Replace(said, `"transformations": [`,
		`"transformations": [{"name": "V\r\n01", "entries": [0]}, `, 1)
	unused = strings.Replace(unused, `"mapping": [0,[0,0,1],[0,0,2],-1]`,
		`"mapping": [0,[0,0,2],[0,0,3],-1]`, 1)
	unused = strings.Replace(unused, `"sources": [`,
		`"sources": [{"name": "x\ry\nz", "values": [1]}, `, 1)
	unused = strings.Replace(unused, `"source": [1,-1,-1,-1]`, `"source": [2,-1,-1,-1]`, 1)
	for _, one := range []struct{ in, line string }{
		{strings.Replace(said, entries, `"name": "V\n05", "entries": []`, 1),
			`tune 1: transformation 1, V\n05: the transformation has 0 entries, and a` +
				` transformation has 1 to 256`},
		{strings.Replace(said, entries, strings.Replace(strings.Replace(entries, "V05",
			`V\r05`, 1), "[0,", "[300,", 1), 1),
			`tune 1: transformation 1, V\r05: entry 0 is 300, and an entry is 0 to 255`},
		{unused, `source 1, x\ry\nz, is started by no row, and a source a tune does not` +
			" run is dropped where this form is read\n" + `transformation 1, V\r\n01, is` +
			` used by no row, and a transformation a tune does not map through is dropped` +
			` where this form is read`},
		{strings.Replace(said, entries, `"name": "V\n05", "entries": [0, "z"]`, 1),
			`V\n05 at entry 1 is "z", and this form requires a whole number`},
		{strings.Replace(said, note, `{"name": "a\rnote", "values": [[163,2,"z"]]}`, 1),
			`a\rnote at row 0 is "z", and this form requires a whole number`},
		{strings.Replace(said, note, `{"name": "a\nnote", "values": [[163,2,15,1]]}`, 1),
			`a row of 4 values in source a\nnote, and a row is a number or two or three` +
				` values`},
		{strings.Replace(said, note, `{"name": "a\r\nnote", "values": [[163,2],[1,2,3]]}`, 1),
			`source a\r\nnote has rows of 2 and of 3 values, and a source has one shape`},
		{strings.Replace(three, `"name":"a tone","repeat":0,"values":[13,0]`,
			`"name":"a\ntone","repeat":0,"values":[[13,1],0]`, 1),
			`source a\ntone has a row of several values, and version 3 has one value a row`},
	} {
		if one.in == said || one.in == three {
			t.Fatalf("the replacement moved no text for %q", one.line)
		}
		_, err := text.Read(one.in)
		if err == nil || err.Error() != one.line {
			t.Errorf("reports %q, and json.md 8.1 has %q", err, one.line)
		}
	}
}

// The X of a line of json.md 8.1 is the value as JSON text: a text as
// json.md 9.7 escapes it, and an array or an object in place of "an
// array" and "an object", except where registers or a timer object is an
// array.
func TestTheValueOfALineIsTheValueAsJSONText(t *testing.T) {
	said := example(t)
	for _, one := range []struct{ from, to, line string }{
		{`"format": "ymxs"`, `"format": [1, {"a": true}]`,
			`format is [1,{"a":true}], and this form requires a text`},
		{`"title": "Four rows, one square"`, `"title": ["a\u0007\"\\"]`,
			`title is ["a\u0007\"\\"], and this form requires a text`},
		{`"registers": {`, `"registers": [1], "x": {`,
			"registers is an array, and this form requires a column a register"},
		{`"timerA": {`, `"timerA": [], "x": {`,
			"timerA is an array, and this form requires a column a part of an effect"},
		{`"timerA": {`, `"timerA": "a", "x": {`,
			`timerA is "a", and this form requires a column a part of an effect`},
		{`"r1": [2,12,4,2]`, `"r1": {"x": -0.0}`,
			`r1 is {"x":-0.0}, and this form requires a column`},
	} {
		_, err := text.Read(strings.Replace(said, one.from, one.to, 1))
		if err == nil || err.Error() != one.line {
			t.Errorf("reports %v, and json.md 8.1 has %q", err, one.line)
		}
	}
}
