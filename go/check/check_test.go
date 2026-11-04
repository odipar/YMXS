package check_test

import (
	"fmt"
	"strings"
	"testing"

	"github.com/odipar/ymxs/go/check"
	"github.com/odipar/ymxs/go/ymxs"
)

func tune(rows []ymxs.Row, rate int) ymxs.Tune {
	return ymxs.Tune{Title: "built", Writer: "check_test", Rate: rate,
		Table: ymxs.Repeating(rows, 0)}
}

// What is outside the two chips is a fault, and every fault is reported
// rather than the first alone.
func TestAStructureOutsideTheTwoChipsIsAFault(t *testing.T) {
	square := ymxs.RepeatingSource("square 12", []int{12, 0}, 0)
	rows := []ymxs.Row{
		{Registers: map[ymxs.Register]int{ymxs.R1: 99}, Effects: map[ymxs.Timer]ymxs.Effect{
			ymxs.TimerA: ymxs.Start{
				Connection: ymxs.OneConnection{Target: ymxs.Setting(ymxs.R8), Source: square},
				Timing:     ymxs.Timing{Prescaler: ymxs.By4, Count: 256}}}},
	}
	said := check.Tune(tune(rows, 50))
	if len(said) != 2 {
		t.Fatalf("%d faults, and two are in it: %s", len(said), strings.Join(said, "; "))
	}
	if !strings.Contains(said[0], "R1 is 0 to 15") {
		t.Errorf("the register is not read: %s", said[0])
	}
	if !strings.Contains(said[1], "a count of 256") {
		t.Errorf("the count is not read: %s", said[1])
	}
}

func TestAStructureTheChipsAllowIsNoFault(t *testing.T) {
	square := ymxs.RepeatingSource("square 12", []int{12, 0}, 0)
	rows := []ymxs.Row{
		{Registers: map[ymxs.Register]int{ymxs.R1: 15}, Effects: map[ymxs.Timer]ymxs.Effect{
			ymxs.TimerA: ymxs.Struck(ymxs.Setting(ymxs.R8), square, ymxs.By4, 100)}},
	}
	if said := check.Tune(tune(rows, 50)); len(said) != 0 {
		t.Errorf("a tune the chips allow is called wrong: %s", strings.Join(said, "; "))
	}
}

// Rule 1 of SPEC.md 6: while an effect runs on a register, a row does not
// set that register.
func TestARowThatSetsARegisterAnEffectRunsOnIsAWarning(t *testing.T) {
	square := ymxs.RepeatingSource("square 12", []int{12, 0}, 0)
	rows := []ymxs.Row{
		{Registers: map[ymxs.Register]int{}, Effects: map[ymxs.Timer]ymxs.Effect{
			ymxs.TimerA: ymxs.Struck(ymxs.Setting(ymxs.R8), square, ymxs.By4, 100)}},
		{Registers: map[ymxs.Register]int{ymxs.R8: 7}, Effects: map[ymxs.Timer]ymxs.Effect{}},
	}
	said := check.Writing(tune(rows, 50))
	if len(said) != 1 || !strings.Contains(said[0], "runs on R8, and this row sets it") {
		t.Errorf("rule 1 is not read: %v", said)
	}
}

// reckoned is the suffix of a line that rests on the reckoning (SPEC.md 6.4).
const reckoned = ", which rests on how long a source that plays once runs, reckoned" +
	" from its rate"

// slow is a start of the source on the register on Timer A, at 2 ticks a
// frame and both resets set: a source of two rows runs out in two frames
// by the reckoning (SPEC.md 6.4).
func slow(register ymxs.Register, source ymxs.Single) ymxs.Row {
	return ymxs.Row{Effects: map[ymxs.Timer]ymxs.Effect{
		ymxs.TimerA: ymxs.Start{
			Connection: ymxs.OneConnection{Target: ymxs.Setting(register), Source: source},
			Timing: ymxs.Timing{Prescaler: ymxs.By200, Count: 200, TimerReset: true,
				PlaceReset: true}}}}
}

// deferring is a deferred start of the source on the register on Timer A,
// at one frame for every source of fifteen rows or fewer.
func deferring(register ymxs.Register, source ymxs.Single) ymxs.Row {
	return ymxs.Row{Effects: map[ymxs.Timer]ymxs.Effect{
		ymxs.TimerA: ymxs.Start{
			Connection: ymxs.OneConnection{Target: ymxs.Setting(register), Source: source},
			Timing: ymxs.Timing{Prescaler: ymxs.By4, Count: 100, TimerReset: true,
				PlaceReset: true}, Deferred: true}}}
}

func sets(register ymxs.Register) ymxs.Row {
	return ymxs.Row{Registers: map[ymxs.Register]int{register: 12}}
}

// A deferred start waits for the source it follows to play once: the square
// runs on R8 to row 3 by the reckoning, and the drum on R9 from there
// (SPEC.md 6.3, 6.4). Every pending start of the Java tree's CheckTest reads
// the same here.
func TestADeferredStartBeginsWhereTheEffectBeforeItRunsOut(t *testing.T) {
	square := ymxs.RepeatingSource("square", []int{15, 0}, 0)
	other := ymxs.RepeatingSource("other", []int{12, 0}, 0)
	longer := ymxs.RepeatingSource("longer", []int{15, 8, 0}, 0)
	drum := ymxs.OnceSource("drum", []int{8, 12, 15, 13})
	stop := ymxs.Row{Effects: map[ymxs.Timer]ymxs.Effect{ymxs.TimerA: ymxs.Stop{}}}
	retune := ymxs.Row{Effects: map[ymxs.Timer]ymxs.Effect{
		ymxs.TimerA: ymxs.Bend(ymxs.By200, 190)}}
	both := ymxs.Row{Registers: map[ymxs.Register]int{ymxs.R9: 12, ymxs.R10: 12}}
	for _, one := range []struct {
		named string
		rows  []ymxs.Row
		said  []string
	}{
		{"the drum follows the square",
			[]ymxs.Row{slow(ymxs.R8, square), deferring(ymxs.R9, drum), sets(ymxs.R8),
				sets(ymxs.R9), {}},
			[]string{"row 2: Timer A runs on R8, and this row sets it" + reckoned,
				"row 3: Timer A runs on R9, and this row sets it" + reckoned}},
		{"an idle timer starts it at its row",
			[]ymxs.Row{deferring(ymxs.R8, square), sets(ymxs.R8)},
			[]string{"row 1: Timer A runs on R8, and this row sets it"}},
		{"a stop discards it",
			[]ymxs.Row{slow(ymxs.R8, square), deferring(ymxs.R9, drum), stop, sets(ymxs.R9)},
			nil},
		{"a start discards it",
			[]ymxs.Row{slow(ymxs.R8, square), deferring(ymxs.R9, longer), slow(ymxs.R10, drum),
				sets(ymxs.R9)},
			nil},
		{"a second replaces the first",
			[]ymxs.Row{slow(ymxs.R8, square), deferring(ymxs.R9, drum),
				deferring(ymxs.R10, drum), both, {}},
			[]string{"row 3: Timer A runs on R10, and this row sets it" + reckoned}},
		{"it meets another timer where it begins",
			[]ymxs.Row{{Effects: map[ymxs.Timer]ymxs.Effect{
				ymxs.TimerA: slow(ymxs.R9, other).Effects[ymxs.TimerA],
				ymxs.TimerB: ymxs.Struck(ymxs.Setting(ymxs.R8), square, ymxs.By4, 100)}},
				deferring(ymxs.R8, drum), {}, {}},
			[]string{"row 3: Timer A starts on R8, where Timer B runs: rule 2 leaves the" +
				" order of two timers writing one register to the writer" + reckoned}},
		{"a retune leaves it pending",
			[]ymxs.Row{slow(ymxs.R8, square), deferring(ymxs.R9, drum), retune, sets(ymxs.R9),
				{}},
			[]string{"row 3: Timer A runs on R9, and this row sets it" + reckoned}},
	} {
		said := check.Writing(tune(one.rows, 50))
		if strings.Join(said, "\n") != strings.Join(one.said, "\n") {
			t.Errorf("%s: %q, and SPEC.md 6.3 reads %q", one.named, said, one.said)
		}
	}
}

// volume is the transformation Vn of SPEC.md 3.6.5: 16 entries, entry k is
// k × n divided by 15, for the keys 0 to 15.
func volume(n int) ymxs.Transformation {
	entries := make([]int, 16)
	for k := range entries {
		entries[k] = k * n / 15
	}
	return ymxs.Transformation{Name: fmt.Sprintf("V%d", n), Entries: entries}
}

// wide is a transformation of 256 entries under a name, a key for each
// value of a byte: entry k is k × n divided by 15.
func wide(named string, n int) ymxs.Transformation {
	entries := make([]int, 256)
	for k := range entries {
		entries[k] = k * n / 15
	}
	return ymxs.Transformation{Name: named, Entries: entries}
}

// first is the transformation cut to its first E entries, keys 0 to E - 1.
func first(transformation ymxs.Transformation, e int) ymxs.Transformation {
	transformation.Entries = transformation.Entries[:e]
	return transformation
}

// on is a row of one operation on Timer A, and of no register.
func on(effect ymxs.Effect) ymxs.Row {
	return ymxs.Row{Registers: map[ymxs.Register]int{},
		Effects: map[ymxs.Timer]ymxs.Effect{ymxs.TimerA: effect}}
}

// voice is a start on setVoiceA of a note of these rows, through the
// mapping.
func voice(rows []ymxs.Three, mapping ymxs.Mapping) ymxs.Start {
	return ymxs.Start{Connection: ymxs.ThreeConnection{
		Target: ymxs.SetVoice{Voice: ymxs.VoiceA},
		Source: ymxs.Triple{Name: "a note", Table: ymxs.Repeating(rows, 0)}},
		Timing: ymxs.Timing{Prescaler: ymxs.By50, Count: 60, TimerReset: true,
			PlaceReset: true}, Mapping: mapping}
}

func lines(t *testing.T, named string, said, want []string) {
	t.Helper()
	if strings.Join(said, "\n") != strings.Join(want, "\n") {
		t.Errorf("%s:\n%s\nand SPEC.md 1.12 reads\n%s", named, strings.Join(said, "\n"),
			strings.Join(want, "\n"))
	}
}

// The errors of a transformation are reported under its number in the
// order the rows first map through it and its name (SPEC.md 1.9, 1.12 item
// 2.3): the number of its entries where it is outside 1 to 256, then each
// entry outside 0 to 255 in key order, before the lines of the rows.
func TestTheErrorsOfATransformationComeBeforeTheRows(t *testing.T) {
	none := ymxs.Transformation{Name: "none", Entries: []int{}}
	long := wide("long", 15)
	long.Entries = append(long.Entries, 300)
	outside := wide("wide", 15)
	outside.Entries[3], outside.Entries[1] = 256, -1
	id := []ymxs.Part{ymxs.Identity{}, ymxs.Identity{}}
	note := []ymxs.Three{{First: 1, Second: 1, Third: 15}}
	rows := []ymxs.Row{
		on(voice(note, ymxs.Mapped(append(id, outside)...))),
		on(ymxs.Remap{Mapping: ymxs.Mapped(append(id, none)...)}),
		on(ymxs.Remap{Mapping: ymxs.Mapped(append(id, long)...)}),
		on(ymxs.Remap{Mapping: ymxs.Mapped(append(id, outside)...)}),
	}
	rows[0].Registers[ymxs.R1] = 16
	lines(t, "three transformations", check.Tune(tune(rows, 50)), []string{
		"transformation 1, wide: entry 1 is -1, and an entry is 0 to 255",
		"transformation 1, wide: entry 3 is 256, and an entry is 0 to 255",
		"transformation 2, none: the transformation has 0 entries, and a transformation has" +
			" 1 to 256",
		"transformation 3, long: the transformation has 257 entries, and a transformation" +
			" has 1 to 256",
		"transformation 3, long: entry 256 is 300, and an entry is 0 to 255",
		"row 0: R1 is 0 to 15, and this row sets it to 16",
	})
	for _, e := range []int{1, 16, 255, 256} {
		if said := check.Transformation(first(wide("V15", 15), e)); len(said) != 0 {
			t.Errorf("V15 of %d entries is free of the errors: %v", e, said)
		}
	}
}

// A value through a transformation of E entries, E from 1 to 256, is a key,
// 0 to its last key E - 1 (SPEC.md 3.6.1, 3.6.5): above it, a start reports
// the line of 1.11 with that last key, and so does a remap against each
// start on its timer (1.12 items 2.4.2.3 and 2.4.2.4). A transformation of
// 0 or 257 entries has the line of its count, and a value through it has
// no key line, since that line rests on the count.
func TestAValueIsAKeyOfTheTransformationItIsReadThrough(t *testing.T) {
	id := []ymxs.Part{ymxs.Identity{}, ymxs.Identity{}}
	volumes := volume(15)
	one := ymxs.Transformation{Name: "one", Entries: []int{7}}
	sixteen := []ymxs.Three{{First: 1, Second: 1, Third: 15}, {First: 1, Second: 1,
		Third: 16}}
	for _, each := range []struct {
		named   string
		rows    []ymxs.Three
		through ymxs.Transformation
		want    []string
	}{
		{"16 entries", sixteen, volumes, []string{
			"a source on setVoiceA whose row 1 is 16, and a key is 0 to 15"}},
		{"one entry", []ymxs.Three{{First: 1, Second: 1, Third: 0}, {First: 1, Second: 1,
			Third: 1}}, one, []string{
			"a source on setVoiceA whose row 1 is 1, and a key is 0 to 0"}},
	} {
		lines(t, each.named, check.Effect(voice(each.rows, ymxs.Mapped(append(id,
			each.through)...))), each.want)
	}

	remapped := []ymxs.Row{
		on(voice(sixteen, ymxs.Mapping{})),
		on(ymxs.Remap{Mapping: ymxs.Mapped(append(id, volumes)...)}),
		on(ymxs.Remap{Mapping: ymxs.Mapped(append(id, wide("V15", 15))...)}),
	}
	lines(t, "a remap through 16 entries", check.Tune(tune(remapped, 50)), []string{
		"row 1: Timer A: start of row 0: a source on setVoiceA whose row 1 is 16, and a key" +
			" is 0 to 15"})

	none := ymxs.Transformation{Name: "none", Entries: []int{}}
	long := wide("long", 15)
	long.Entries = append(long.Entries, 0)
	high := []ymxs.Three{{First: 1, Second: 1, Third: 16}, {First: 1, Second: 1,
		Third: 300}}
	counted := []ymxs.Row{
		on(voice(high, ymxs.Mapped(append(id, long)...))),
		on(ymxs.Remap{Mapping: ymxs.Mapped(append(id, none)...)}),
		on(ymxs.Remap{Mapping: ymxs.Mapped(append(id, long)...)}),
		{Effects: map[ymxs.Timer]ymxs.Effect{
			ymxs.TimerB: voice(high, ymxs.Mapped(append(id, none)...))}},
	}
	lines(t, "0 and 257 entries", check.Tune(tune(counted, 50)), []string{
		"transformation 1, long: the transformation has 257 entries, and a transformation" +
			" has 1 to 256",
		"transformation 2, none: the transformation has 0 entries, and a transformation has" +
			" 1 to 256",
	})
}

// Two transformations are one where their names are equal and their entries
// equal in number and order (SPEC.md 3.6.2): a table of 16 entries and one of
// 256 under one name are two transformations of the tune, numbered in the
// order the rows first map through them (1.9).
func TestTablesOf16And256EntriesUnderOneNameAreTwoTransformations(t *testing.T) {
	short, whole := volume(15), wide("V15", 15)
	if ymxs.TransformationEqual(short, whole) {
		t.Error("V15 of 16 entries and V15 of 256 are one transformation")
	}
	id := []ymxs.Part{ymxs.Identity{}, ymxs.Identity{}}
	rows := []ymxs.Row{
		on(voice([]ymxs.Three{{First: 1, Second: 1, Third: 15}},
			ymxs.Mapped(append(id, short)...))),
		on(ymxs.Remap{Mapping: ymxs.Mapped(append(id, whole)...)}),
		on(ymxs.Remap{Mapping: ymxs.Mapped(append(id, volume(15))...)}),
	}
	built := tune(rows, 50)
	listed := ymxs.Transformations(built)
	if len(listed) != 2 || len(listed[0].Entries) != 16 || len(listed[1].Entries) != 256 {
		t.Errorf("the tune maps through %d transformations, and SPEC.md 1.9 lists two: 16"+
			" entries, then 256", len(listed))
	}
	lines(t, "two tables of V15", check.Tune(built), nil)
}

// A start reports identity values above register limits and lookup keys
// above the last entry, in row and value order. It then reports mapped
// values above register limits. Missing keys and selected entries
// outside the byte range skip dependent output checks; other entries
// remain usable (SPEC.md 1.12).
func TestTheSourceOfAStartIsReadThroughItsMapping(t *testing.T) {
	loud := wide("loud", 15)
	loud.Entries[15], loud.Entries[12] = 40, 33
	broken := wide("broken", 15)
	broken.Entries[15], broken.Entries[12] = 300, 33
	rows := []ymxs.Three{{First: 300, Second: 16, Third: 15}, {First: 0, Second: 0,
		Third: 256}, {First: 1, Second: 1, Third: 12}}
	for _, one := range []struct {
		named   string
		mapping ymxs.Mapping
		want    []string
	}{
		{"the identity", ymxs.Mapping{}, []string{
			"a source on setVoiceA whose row 0 is 300, and the target is 0 to 255",
			"a source on setVoiceA whose row 0 is 16, and the target is 0 to 15",
			"a source on setVoiceA whose row 1 is 256, and the target is 0 to 31"}},
		{"a transformation of the volume",
			ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{}, loud), []string{
				"a source on setVoiceA whose row 0 is 300, and the target is 0 to 255",
				"a source on setVoiceA whose row 0 is 16, and the target is 0 to 15",
				"a source on setVoiceA whose row 1 is 256, and a key is 0 to 255",
				"value 2 of row 0 maps to 40 on setVoiceA, and the target is 0 to 31",
				"value 2 of row 2 maps to 33 on setVoiceA, and the target is 0 to 31"}},
		{"a transformation of each value",
			ymxs.Mapped(wide("half", 7), wide("half", 7), loud), []string{
				"a source on setVoiceA whose row 0 is 300, and a key is 0 to 255",
				"a source on setVoiceA whose row 1 is 256, and a key is 0 to 255",
				"value 2 of row 0 maps to 40 on setVoiceA, and the target is 0 to 31",
				"value 2 of row 2 maps to 33 on setVoiceA, and the target is 0 to 31"}},
		{"a transformation with an error",
			ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{}, broken), []string{
				"a source on setVoiceA whose row 0 is 300, and the target is 0 to 255",
				"a source on setVoiceA whose row 0 is 16, and the target is 0 to 15",
				"a source on setVoiceA whose row 1 is 256, and a key is 0 to 255",
				"value 2 of row 2 maps to 33 on setVoiceA, and the target is 0 to 31"}},
		{"two parts", ymxs.Mapped(ymxs.Identity{}, loud), []string{
			"a mapping of 2 parts on setVoiceA, which reads 3"}},
	} {
		lines(t, one.named, check.Effect(voice(rows, one.mapping)), one.want)
	}
}

// A remap checks every start on its timer, before or after it. Part
// counts are reported once per target in first-start order, then key
// and output errors once per connection, prefixed with its first start
// row. A timer without starts has no connections to check (SPEC.md
// 1.12).
func TestARemapIsReadAgainstEachStartOnItsTimer(t *testing.T) {
	loud := wide("loud", 15)
	loud.Entries[15] = 40
	quiet := []ymxs.Three{{First: 1, Second: 1, Third: 12}}
	high := []ymxs.Three{{First: 1, Second: 1, Third: 15}, {First: 1, Second: 1,
		Third: 256}}
	tone := ymxs.Start{Connection: ymxs.TwoConnection{
		Target: ymxs.SetTone{Voice: ymxs.VoiceA},
		Source: ymxs.Pair{Name: "a tone", Table: ymxs.Repeating([]ymxs.Two{
			{First: 200, Second: 15}}, 0)}},
		Timing: ymxs.Timing{Prescaler: ymxs.By50, Count: 60, TimerReset: true,
			PlaceReset: true}}
	through := ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{}, loud)
	rows := []ymxs.Row{
		on(voice(quiet, ymxs.Mapping{})),
		on(ymxs.Remap{Mapping: through}),
		on(tone),
		on(voice(high, ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{},
			wide("V0", 0)))),
		on(ymxs.Remap{Mapping: ymxs.Mapping{}}),
		on(voice(quiet, ymxs.Mapping{})),
	}
	lines(t, "two remaps", check.Tune(tune(rows, 50)), []string{
		"row 1: Timer A: a mapping of 3 parts on setToneA, which reads 2",
		"row 1: Timer A: start of row 3: value 2 of row 0 maps to 40 on setVoiceA, and the" +
			" target is 0 to 31",
		"row 1: Timer A: start of row 3: a source on setVoiceA whose row 1 is 256, and a key" +
			" is 0 to 255",
		"row 3: Timer A: a source on setVoiceA whose row 1 is 256, and a key is 0 to 255",
		"row 4: Timer A: start of row 3: value 2 of row 1 maps to 256 on setVoiceA, and the" +
			" target is 0 to 31",
	})
	idle := []ymxs.Row{on(ymxs.Remap{Mapping: ymxs.Mapped(loud)}), {}}
	lines(t, "a remap of no start", check.Tune(tune(idle, 50)), nil)
}

// level is a start on setR8 on Timer A of a source of these values, one a
// row, through the mapping.
func level(named string, values []int, mapping ymxs.Mapping) ymxs.Start {
	return ymxs.Start{Connection: ymxs.OneConnection{Target: ymxs.Setting(ymxs.R8),
		Source: ymxs.RepeatingSource(named, values, 0)},
		Timing: ymxs.Timing{Prescaler: ymxs.By50, Count: 60, TimerReset: true,
			PlaceReset: true}, Mapping: mapping}
}

// Two sources of equal rows under two names are two sources (SPEC.md 1.9),
// so a remap reads each, the lines of each prefixed with the row of the
// first start that connects it (1.12 item 2.4.2.4); a later start of the
// first source is the same connection, read once.
func TestARemapReadsTwoSourcesOfEqualRowsUnderTheRowsOfTheirStarts(t *testing.T) {
	volumes := volume(15)
	rows := []ymxs.Row{
		on(level("a", []int{16}, ymxs.Mapping{})),
		on(level("b", []int{16}, ymxs.Mapping{})),
		on(ymxs.Remap{Mapping: ymxs.Mapped(volumes)}),
		on(level("a", []int{16}, ymxs.Mapping{})),
	}
	lines(t, "two sources of 16", check.Tune(tune(rows, 50)), []string{
		"row 2: Timer A: start of row 0: a source on setR8 whose row 0 is 16, and a key is" +
			" 0 to 15",
		"row 2: Timer A: start of row 1: a source on setR8 whose row 0 is 16, and a key is" +
			" 0 to 15",
	})
	if said := check.Multi(ymxs.NewMulti(tune(rows, 50))); len(said) != 2 ||
		said[0] != "tune 1: row 2: Timer A: start of row 0: a source on setR8 whose row 0"+
			" is 16, and a key is 0 to 15" {
		t.Errorf("a multi reports %q", said)
	}
}

// The name of a source or of a transformation in a reported line has each
// line feed as the two characters \n and each carriage return as \r, so
// the line is one line (SPEC.md 1.12): in the prefix of a transformation,
// and in the lines of an unstarted source and an unused transformation
// (json.md 8.1, csv.md 5.1).
func TestANameInAReportedLineIsOneLine(t *testing.T) {
	for _, one := range []struct{ name, reported string }{
		{"a\nb", `a\nb`}, {"a\rb", `a\rb`}, {"\r\n", `\r\n`}, {`a\nb`, `a\nb`},
	} {
		none := ymxs.Transformation{Name: one.name, Entries: []int{}}
		built := tune([]ymxs.Row{on(level("s", []int{1}, ymxs.Mapped(none)))}, 50)
		lines(t, one.reported, check.Tune(built), []string{"transformation 1, " +
			one.reported + ": the transformation has 0 entries, and a transformation has 1" +
			" to 256"})
		source := ymxs.RepeatingSource(one.name, []int{1}, 0)
		lines(t, one.reported, check.Declared([]ymxs.Source{source}, built), []string{
			"source 1, " + one.reported + ", is started by no row, and a source a tune does" +
				" not run is dropped where this form is read"})
		unused := ymxs.Transformation{Name: one.name, Entries: []int{0}}
		lines(t, one.reported, check.Used([]ymxs.Transformation{none, unused}, built),
			[]string{"transformation 2, " + one.reported + ", is used by no row, and a" +
				" transformation a tune does not map through is dropped where this form is" +
				" read"})
		if ymxs.Reported(one.name) != one.reported {
			t.Errorf("%q is reported as %q", one.name, ymxs.Reported(one.name))
		}
	}
}

// An entry outside 0 to 255 leaves the other entries of its transformation
// read (SPEC.md 1.12): through 16 entries of which entry 1 is 300, key 0
// reaches entry 0, 40, above the most of R8, by a start and by a remap; key
// 1 is not read, and value 20 is above the last key, 15.
func TestAnEntryOutside0To255LeavesTheOtherEntriesRead(t *testing.T) {
	v := ymxs.Transformation{Name: "v", Entries: []int{40, 300, 0, 1, 2, 3, 4, 5, 6, 7,
		8, 9, 10, 11, 12, 13}}
	rows := []ymxs.Row{
		on(level("s", []int{0, 1, 20}, ymxs.Mapped(v))),
		on(ymxs.Remap{Mapping: ymxs.Mapped(v)}),
	}
	lines(t, "one entry of 300", check.Tune(tune(rows, 50)), []string{
		"transformation 1, v: entry 1 is 300, and an entry is 0 to 255",
		"row 0: Timer A: a source on setR8 whose row 2 is 20, and a key is 0 to 15",
		"row 0: Timer A: value 0 of row 0 maps to 40 on setR8, and the target is 0 to 31",
		"row 1: Timer A: start of row 0: value 0 of row 0 maps to 40 on setR8, and the" +
			" target is 0 to 31",
		"row 1: Timer A: start of row 0: a source on setR8 whose row 2 is 20, and a key is" +
			" 0 to 15",
	})
	for _, one := range []struct{ key, written int }{{0, 40}, {2, 0}, {15, 13}} {
		if written, read := ymxs.Through(v, one.key); !read || written != one.written {
			t.Errorf("key %d reaches %d, %v, and entry %d is %d", one.key, written, read,
				one.key, one.written)
		}
	}
	for _, key := range []int{-1, 1, 16} {
		if written, read := ymxs.Through(v, key); read {
			t.Errorf("key %d is read as %d", key, written)
		}
	}
}

// A remap on an idle timer is the rule 5 line of a remap (SPEC.md 6.5),
// with the suffix of the reckoning where the source has run out by it; a
// remap leaves the effect running, its run and a pending start as they
// were, and at the repeat row it leaves the line of rule 1(d) to be
// reported (6.3).
func TestARemapOnAnIdleTimerIsAWarning(t *testing.T) {
	square := ymxs.RepeatingSource("square", []int{15, 0}, 0)
	drum := ymxs.OnceSource("drum", []int{8, 12, 15, 13})
	remap := ymxs.Row{Effects: map[ymxs.Timer]ymxs.Effect{ymxs.TimerA: ymxs.Remap{}}}
	idle := "remaps an effect that is idle: a mapping is set on a running effect, and" +
		" this row leaves the timer as it is"
	repeat := func(rows []ymxs.Row, at int) ymxs.Tune {
		return ymxs.Tune{Title: "built", Writer: "check_test", Rate: 50,
			Table: ymxs.Repeating(rows, at)}
	}
	for _, one := range []struct {
		named string
		tune  ymxs.Tune
		said  []string
	}{
		{"before any start", repeat([]ymxs.Row{remap, slow(ymxs.R8, square)}, 1),
			[]string{"row 0: Timer A " + idle}},
		{"after a stop", repeat([]ymxs.Row{slow(ymxs.R8, square),
			{Effects: map[ymxs.Timer]ymxs.Effect{ymxs.TimerA: ymxs.Stop{}}}, remap}, 0),
			[]string{"row 2: Timer A " + idle}},
		{"after the source has run out, in four frames", repeat([]ymxs.Row{
			slow(ymxs.R8, drum), {}, {}, remap, remap, {}}, 0),
			[]string{"row 4: Timer A " + idle + reckoned}},
		{"on a running effect", repeat([]ymxs.Row{slow(ymxs.R8, square), remap,
			sets(ymxs.R8), remap, sets(ymxs.R8)}, 1), []string{
			"rows 2 to 4: Timer A runs on R8 from row 0, and 2 of them set it",
			"the tune repeats to row 1, and Timer A runs on R8 when its last row has" +
				" played: the wrap resumes with the timer running from the pass before"}},
		{"with a start pending", repeat([]ymxs.Row{slow(ymxs.R8, square),
			deferring(ymxs.R9, drum), remap, sets(ymxs.R9), {}}, 0),
			[]string{"row 3: Timer A runs on R9, and this row sets it" + reckoned}},
	} {
		said := check.Writing(one.tune)
		if strings.Join(said, "\n") != strings.Join(one.said, "\n") {
			t.Errorf("%s: %q, and SPEC.md 6.3 reads %q", one.named, said, one.said)
		}
	}
}

// remapped is a tune of 12 rows with errors in remaps on Timers A, B and D:
// several targets and connections on each timer, a connection started at
// two rows, a remap before a later start, and equal mappings at several
// rows.
func remapped(t *testing.T) ymxs.Tune {
	t.Helper()
	lo := ymxs.RepeatingSource("lo", []int{3, 20}, 0)
	hi := ymxs.RepeatingSource("hi", []int{40, 5}, 0)
	tone := ymxs.Pair{Name: "tone", Table: ymxs.Repeating([]ymxs.Two{{First: 300,
		Second: 16}, {First: 10, Second: 2}}, 0)}
	note := ymxs.Triple{Name: "note", Table: ymxs.Repeating([]ymxs.Three{{First: 1,
		Second: 1, Third: 12}, {First: 1, Second: 1, Third: 20}}, 0)}
	envelope := ymxs.Pair{Name: "env", Table: ymxs.Repeating([]ymxs.Two{{First: 255}}, 0)}
	up := volume(15)
	up.Name = "up"
	for k := range up.Entries {
		up.Entries[k] += 20
	}
	v15, big := volume(15), wide("big", 15)
	start := func(target ymxs.Target, source ymxs.Source) ymxs.Start {
		start, err := ymxs.Starting(target, source, ymxs.Timing{Prescaler: ymxs.By50,
			Count: 60, TimerReset: true, PlaceReset: true}, false, ymxs.Mapping{})
		if err != nil {
			t.Fatal(err)
		}
		return start
	}
	remap := func(parts ...ymxs.Part) ymxs.Remap {
		return ymxs.Remap{Mapping: ymxs.Mapped(parts...)}
	}
	r8, r10 := ymxs.Setting(ymxs.R8), ymxs.Setting(ymxs.R10)
	i := ymxs.Identity{}
	plan := map[ymxs.Timer]map[int]ymxs.Effect{
		ymxs.TimerA: {0: start(r8, lo), 1: remap(up), 2: start(ymxs.SetTone{}, tone),
			3: remap(up), 5: remap(up), 6: remap(i, v15), 8: remap(up)},
		ymxs.TimerB: {0: start(ymxs.SetVoice{Voice: ymxs.VoiceB}, note),
			4: remap(i, i, v15), 7: start(r8, hi), 8: remap(big), 9: remap(i, i, v15),
			11: remap(big)},
		ymxs.TimerD: {0: start(r10, lo), 1: start(r8, lo), 2: ymxs.Remap{},
			3: remap(up), 4: start(r10, lo), 5: remap(v15),
			6: start(ymxs.SetEnvelope{}, envelope), 7: remap(up), 10: remap(v15)},
	}
	rows := make([]ymxs.Row, 12)
	for at := range rows {
		rows[at] = ymxs.Row{Registers: map[ymxs.Register]int{},
			Effects: map[ymxs.Timer]ymxs.Effect{}}
	}
	for timer, effects := range plan {
		for at, effect := range effects {
			rows[at].Effects[timer] = effect
		}
	}
	return tune(rows, 50)
}

// A remap is read against every start on its timer at any row: the part
// counts once a target, then each connection once, under the row of its
// first start, in source-row and value order (SPEC.md 1.12). A tune check
// reads each mapping once a timer, and its lines are those of Remapping
// called for the remap alone; Row, without the tune, reports none.
func TestRemapsOnThreeTimersReadEveryTargetAndConnectionOnTheirTimer(t *testing.T) {
	key := func(row, value, last int, target string) string {
		return fmt.Sprintf("a source on %s whose row %d is %d, and a key is 0 to %d",
			target, row, value, last)
	}
	parts := func(n int, target string, reads int) string {
		return fmt.Sprintf("a mapping of %d parts on %s, which reads %d", n, target, reads)
	}
	toneA := parts(1, "setToneA", 2)
	envelope := parts(1, "setEnvelope", 2)
	a0 := "Timer A: start of row 0: " + key(1, 20, 15, "setR8")
	d0 := "Timer D: start of row 0: " + key(1, 20, 15, "setR10")
	d1 := "Timer D: start of row 1: " + key(1, 20, 15, "setR8")
	b0 := "Timer B: start of row 0: " + key(1, 20, 15, "setVoiceB")
	b7 := "Timer B: start of row 7: value 0 of row 0 maps to 40 on setR8, and the target" +
		" is 0 to 31"
	tune := remapped(t)
	lines(t, "remaps on three timers", check.Tune(tune), []string{
		"row 1: Timer A: " + toneA,
		"row 1: " + a0,
		"row 2: Timer A: a source on setToneA whose row 0 is 300, and the target is 0 to 255",
		"row 2: Timer A: a source on setToneA whose row 0 is 16, and the target is 0 to 15",
		"row 3: Timer A: " + toneA,
		"row 3: " + a0,
		"row 3: Timer D: " + envelope,
		"row 3: " + d0,
		"row 3: " + d1,
		"row 4: Timer B: " + parts(3, "setR8", 1),
		"row 4: " + b0,
		"row 5: Timer A: " + toneA,
		"row 5: " + a0,
		"row 5: Timer D: " + envelope,
		"row 5: " + d0,
		"row 5: " + d1,
		"row 6: Timer A: " + parts(2, "setR8", 1),
		"row 6: Timer A: start of row 2: value 0 of row 0 maps to 300 on setToneA, and the" +
			" target is 0 to 255",
		"row 6: Timer A: start of row 2: " + key(0, 16, 15, "setToneA"),
		"row 7: Timer B: a source on setR8 whose row 0 is 40, and the target is 0 to 31",
		"row 7: Timer D: " + envelope,
		"row 7: " + d0,
		"row 7: " + d1,
		"row 8: Timer A: " + toneA,
		"row 8: " + a0,
		"row 8: Timer B: " + parts(1, "setVoiceB", 3),
		"row 8: " + b7,
		"row 9: Timer B: " + parts(3, "setR8", 1),
		"row 9: " + b0,
		"row 10: Timer D: " + envelope,
		"row 10: " + d0,
		"row 10: " + d1,
		"row 11: Timer B: " + parts(1, "setVoiceB", 3),
		"row 11: " + b7,
	})
	var alone []string
	for at, row := range ymxs.Rows(tune) {
		for _, one := range ymxs.Effects(row) {
			said := check.Effect(one.Effect)
			if remap, ok := one.Effect.(ymxs.Remap); ok {
				said = check.Remapping(tune, one.Timer, remap)
				without := ymxs.Row{Effects: map[ymxs.Timer]ymxs.Effect{one.Timer: remap}}
				if len(check.Effect(remap))+len(check.Row(without)) != 0 {
					t.Errorf("the remap at row %d reports a line without the tune", at)
				}
			}
			for _, line := range said {
				alone = append(alone, fmt.Sprintf("row %d: Timer %s: %s", at, one.Timer, line))
			}
		}
	}
	lines(t, "Effect and Remapping of each operation", alone, check.Tune(tune))
}

// Remapping reads the starts on the timer it is called for, a timer outside
// A to D among them.
func TestRemappingReadsTheTimerItIsCalledFor(t *testing.T) {
	timer := ymxs.Timer(5)
	rows := []ymxs.Row{{Effects: map[ymxs.Timer]ymxs.Effect{timer: ymxs.Start{
		Connection: ymxs.OneConnection{Target: ymxs.Setting(ymxs.R8),
			Source: ymxs.RepeatingSource("q", []int{20}, 0)}}}}}
	lines(t, "a remap on Timer 5", check.Remapping(tune(rows, 50), timer,
		ymxs.Remap{Mapping: ymxs.Mapped(volume(15))}), []string{
		"start of row 0: a source on setR8 whose row 0 is 20, and a key is 0 to 15"})
}
