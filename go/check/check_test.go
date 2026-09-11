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
			ymxs.TimerA: ymxs.Start{Target: ymxs.Setting(ymxs.R8), Source: square,
				Prescaler: ymxs.By4, Count: 256}}},
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
