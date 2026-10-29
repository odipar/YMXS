package check_test

import (
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
			ymxs.TimerA: ymxs.StartOne{Target: ymxs.Setting(ymxs.R8), Source: square,
				Timing: ymxs.Timing{Prescaler: ymxs.By4, Count: 256}}}},
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
		ymxs.TimerA: ymxs.StartOne{Target: ymxs.Setting(register), Source: source,
			Timing: ymxs.Timing{Prescaler: ymxs.By200, Count: 200, TimerReset: true,
				PlaceReset: true}}}}
}

// deferring is a deferred start of the source on the register on Timer A,
// at one frame for every source of fifteen rows or fewer.
func deferring(register ymxs.Register, source ymxs.Single) ymxs.Row {
	return ymxs.Row{Effects: map[ymxs.Timer]ymxs.Effect{
		ymxs.TimerA: ymxs.StartOne{Target: ymxs.Setting(register), Source: source,
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
