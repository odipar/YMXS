// Package check reads the rules a structure must satisfy for a player to
// play it, off the structure rather than out of it.
//
// Every function reports every fault rather than stopping at the first, so
// one call reports every fault a writer must correct. Must is the inverse,
// for a caller that stops at the first.
//
// A structure is bound by the two chips and the music: what fits a
// register, what a timer counts, and that an effect hands a source's row
// to a target of that same shape. What a form can write belongs to that
// form, and no limit of one appears here.
//
// Writing is the other half: the rules of SPEC.md 6 read across rows
// rather than within one, and a tune that breaks one of them plays, but
// not as written, rather than failing to play.
package check

import (
	"errors"
	"fmt"
	"math"
	"strings"

	"github.com/odipar/ymxs/go/ymxs"
)

// Multi is what is wrong with the multi, or an empty list.
func Multi(multi ymxs.Multi) []string {
	var said []string
	if len(multi.Tunes) == 0 {
		said = append(said, "a multi of no tunes: a host plays one")
	}
	for at, tune := range multi.Tunes {
		for _, one := range Tune(tune) {
			said = append(said, fmt.Sprintf("tune %d: %s", at+1, one))
		}
	}
	return said
}

// Tune is what is wrong with the tune, or an empty list.
func Tune(tune ymxs.Tune) []string {
	var said []string
	if tune.Rate < 1 {
		said = append(said, fmt.Sprintf("a rate of %d: a player is called at least once"+
			" a second", tune.Rate))
	}
	said = append(said, table(len(tune.Table.Rows), tune.Table.Repeat, "the tune")...)
	for at, row := range tune.Table.Rows {
		for _, one := range Row(row) {
			said = append(said, fmt.Sprintf("row %d: %s", at, one))
		}
	}
	return said
}

// Row is what is wrong with the row, or an empty list.
func Row(row ymxs.Row) []string {
	var said []string
	for _, one := range ymxs.Registered(row) {
		most := ymxs.Most(one.Register)
		if one.Value < 0 || one.Value > most {
			said = append(said, fmt.Sprintf("%s is 0 to %d, and this row sets it to %d",
				one.Register, most, one.Value))
		}
	}
	for _, one := range ymxs.Effects(row) {
		for _, wrong := range Effect(one.Effect) {
			said = append(said, fmt.Sprintf("Timer %s: %s", one.Timer, wrong))
		}
	}
	return said
}

// Effect is what is wrong with one row's operation on one effect, or an
// empty list.
func Effect(effect ymxs.Effect) []string {
	switch e := effect.(type) {
	case ymxs.Start:
		said := count(e.Count)
		return append(said, runs(e)...)
	case ymxs.Retune:
		return count(e.Count)
	case ymxs.Stop:
		return nil
	}
	panic(fmt.Sprintf("no effect %T", effect))
}

// Source is what is wrong with a source, or an empty list. A source's
// values are read against the target that runs it, so this reads only what
// is decidable without one.
func Source(source ymxs.Source) []string {
	said := table(len(ymxs.Values(source)), ymxs.SourceTable(source).Repeat, "the source")
	for at, value := range ymxs.Values(source) {
		if value < 0 {
			said = append(said, fmt.Sprintf("row %d is %d, and a register is 0 upward",
				at, value))
		}
	}
	return said
}

// runs is what a start must satisfy: the target reads the row shape the
// source writes, and the source's values fit that register.
func runs(start ymxs.Start) []string {
	said := Source(start.Source)
	if ymxs.TargetColumns(start.Target) != ymxs.SourceColumns(start.Source) {
		return append(said, fmt.Sprintf("a source of %d values a row on %s, which reads %d",
			ymxs.SourceColumns(start.Source), ymxs.TargetName(start.Target),
			ymxs.TargetColumns(start.Target)))
	}
	most := ymxs.TargetMost(start.Target)
	for at, value := range ymxs.Values(start.Source) {
		if value > most {
			said = append(said, fmt.Sprintf("a source on %s whose row %d is %d, and the"+
				" target is 0 to %d", ymxs.TargetName(start.Target), at, value, most))
		}
	}
	return said
}

func count(at int) []string {
	if at < 0 || at > ymxs.MostCount {
		return []string{fmt.Sprintf("a count of %d: a timer's data register is a"+
			" byte, 0 to %d, and 0 counts %d", at, ymxs.MostCount, ymxs.ZeroCounts)}
	}
	return nil
}

func table(rows int, repeat func() (int, bool), what string) []string {
	var said []string
	if rows == 0 {
		said = append(said, what+" has no rows: a clock reads one")
	}
	if at, repeats := repeat(); repeats && (at < 0 || at >= rows) {
		said = append(said, fmt.Sprintf("%s has %d rows and repeats to row %d",
			what, rows, at))
	}
	return said
}

// MustMulti is the multi itself, where the check finds no fault, and
// otherwise an error reporting everything that is.
func MustMulti(multi ymxs.Multi) (ymxs.Multi, error) {
	if said := Multi(multi); len(said) > 0 {
		return multi, errors.New(strings.Join(said, "\n"))
	}
	return multi, nil
}

// MustTune is the tune itself, where the check finds no fault, and
// otherwise an error reporting everything that is.
func MustTune(tune ymxs.Tune) (ymxs.Tune, error) {
	if said := Tune(tune); len(said) > 0 {
		return tune, errors.New(strings.Join(said, "\n"))
	}
	return tune, nil
}

// ------------------------------------------------------- SPEC.md 6

// running is what one timer runs, and the row its source ends on.
type running struct {
	target ymxs.Target
	source ymxs.Source
	until  int
}

// Writing reads the rules of SPEC.md 6 across the tune's rows. A tune that
// breaks one of them plays, but not as written.
//
// Three of the five are read here. Rule 2 leaves the order of two timers
// writing one register to the writer, and is not checked. Rule 4 requires
// a start to set the timer's reset where the timer is stopped, and a
// stopped timer begins a whole period with the value or without it, so
// either value is correct and it is not checked either.
//
// A source that plays once has a start in the rows and no end, so its
// duration is reckoned from its rate (ymxs.Frames). Every reading resting
// on that reckoning is reported as such.
func Writing(tune ymxs.Tune) []string {
	w := &writing{tune: tune,
		running:    map[ymxs.Timer]running{},
		lastSource: map[ymxs.Timer]ymxs.Source{},
		lastTarget: map[ymxs.Timer]ymxs.Target{}}
	return w.run()
}

type writing struct {
	tune       ymxs.Tune
	said       []string
	running    map[ymxs.Timer]running
	lastSource map[ymxs.Timer]ymxs.Source
	lastTarget map[ymxs.Timer]ymxs.Target
}

func (w *writing) run() []string {
	for at, row := range w.tune.Table.Rows {
		// The effects come first, in the order a frame writes them.
		for _, one := range ymxs.Effects(row) {
			w.effect(at, one.Timer, one.Effect)
		}
		w.registers(at, row)
	}
	return w.said
}

func (w *writing) effect(at int, timer ymxs.Timer, effect ymxs.Effect) {
	switch e := effect.(type) {
	case ymxs.Start:
		w.place(at, timer, e)
		w.running[timer] = running{target: e.Target, source: e.Source, until: w.until(at, e)}
		w.lastSource[timer] = e.Source
		w.lastTarget[timer] = e.Target
	case ymxs.Retune:
		if _, on := w.runs(timer, at); !on {
			w.say(at, timer, "retunes an effect that is idle: a rate written to a timer"+
				" with no source on it starts that timer with no source to run",
				w.reckoned(timer, at))
		}
	case ymxs.Stop:
		delete(w.running, timer)
	}
}

// place reads rule 3: a start sets the place's reset, unless the source it
// starts has the row count of the one this effect last ran on the same
// target.
func (w *writing) place(at int, timer ymxs.Timer, start ymxs.Start) {
	if start.PlaceReset {
		return
	}
	before, ran := w.lastSource[timer]
	if !ran {
		w.say(at, timer, "starts a source without the place's reset, and this timer has"+
			" run none: the place is where the player left it", false)
		return
	}
	last, on := w.lastTarget[timer]
	if !on || !ymxs.TargetEqual(start.Target, last) {
		named := "no target"
		if on {
			named = ymxs.TargetName(last)
		}
		w.say(at, timer, "starts a source on "+ymxs.TargetName(start.Target)+
			" without the place's reset, and this timer last ran on "+named, false)
		return
	}
	now := len(ymxs.Values(start.Source))
	then := len(ymxs.Values(before))
	if now != then {
		w.say(at, timer, fmt.Sprintf("starts a source of %d rows without the place's"+
			" reset, and the one before it had %d", now, then), false)
	}
}

// registers reads rule 1: while an effect runs on a register, a row does
// not set that register.
func (w *writing) registers(at int, row ymxs.Row) {
	for _, timer := range ymxs.Timers {
		runs, on := w.running[timer]
		if !on || at >= runs.until {
			continue
		}
		register := written(runs.target)
		if register == ymxs.R13 {
			continue
		}
		if _, set := row.Registers[register]; !set {
			continue
		}
		w.say(at, timer, fmt.Sprintf("runs on %s, and this row sets it", register),
			runs.until != math.MaxInt32)
	}
}

func (w *writing) runs(timer ymxs.Timer, at int) (running, bool) {
	runs, on := w.running[timer]
	if on && at < runs.until {
		return runs, true
	}
	return running{}, false
}

// reckoned is whether a reading of this timer at this row rests on the
// reckoning of a source that plays once.
func (w *writing) reckoned(timer ymxs.Timer, at int) bool {
	runs, on := w.running[timer]
	return on && runs.until != math.MaxInt32 && at >= runs.until
}

// until is the row a start's source ends on, or no row where it repeats.
func (w *writing) until(at int, start ymxs.Start) int {
	if _, repeats := ymxs.SourceTable(start.Source).Repeat(); repeats {
		return math.MaxInt32
	}
	return at + ymxs.Frames(len(ymxs.Values(start.Source)), start.Prescaler,
		start.Count, w.tune.Rate)
}

func written(target ymxs.Target) ymxs.Register {
	switch t := target.(type) {
	case ymxs.SetRegister:
		return t.Register
	}
	panic(fmt.Sprintf("no target %T", target))
}

func (w *writing) say(at int, timer ymxs.Timer, what string, reckoned bool) {
	rests := ""
	if reckoned {
		rests = ", which rests on how long a source that plays once runs, reckoned from" +
			" its rate"
	}
	w.said = append(w.said, fmt.Sprintf("row %d: Timer %s %s%s", at, timer, what, rests))
}
