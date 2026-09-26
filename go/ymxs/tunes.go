package ymxs

import "fmt"

// What is read off a structure, read rather than stored. Every function
// here is pure: it reads a structure and returns a value, leaving its
// argument unchanged.

// ------------------------------------------------------------- a table

// Size is how many rows the table has.
func Size[T any](table Table[T]) int {
	return len(table.Rows)
}

// NewRow is a row that sets these registers and leaves every effect alone.
func NewRow(registers map[Register]int) Row {
	return Row{Registers: registers, Effects: map[Timer]Effect{}}
}

// Effects is the effects this row sets, in the order the timers are
// declared, as pairs.
//
// A row is built on a plain map, whose iteration order belongs to the
// caller rather than the row. A form that writes one row twice must write
// it identically both times, and reads the row through this.
func Effects(row Row) []TimerEffect {
	var out []TimerEffect
	for _, timer := range Timers {
		if effect, ok := row.Effects[timer]; ok {
			out = append(out, TimerEffect{Timer: timer, Effect: effect})
		}
	}
	return out
}

// TimerEffect is one timer and the effect a row sets on it.
type TimerEffect struct {
	Timer  Timer
	Effect Effect
}

// RegisterValue is one register and the value a row sets it to.
type RegisterValue struct {
	Register Register
	Value    int
}

// Registered is the registers this row sets, in the order they are
// declared, as pairs.
func Registered(row Row) []RegisterValue {
	var out []RegisterValue
	for _, register := range Registers {
		if value, ok := row.Registers[register]; ok {
			out = append(out, RegisterValue{Register: register, Value: value})
		}
	}
	return out
}

// IsEmpty is whether the row sets no register and leaves every effect
// alone.
func IsEmpty(row Row) bool {
	return len(row.Registers) == 0 && len(row.Effects) == 0
}

// -------------------------------------------------------------- a tune

// Rows is the rows of the tune, one a frame.
func Rows(tune Tune) []Row {
	return tune.Table.Rows
}

// Sources is the sources this tune runs: the ones its rows start, in
// first-start order. Only a source started by some row belongs to the
// tune, so this is the complete list.
func Sources(tune Tune) []Source {
	var out []Source
	for _, row := range Rows(tune) {
		for _, one := range Effects(row) {
			start, ok := one.Effect.(Start)
			if !ok {
				continue
			}
			source := StartSource(start)
			if indexOf(out, source) < 0 {
				out = append(out, source)
			}
		}
	}
	return out
}

// SourceNumber is the number a form writes for the source, 1 upward. The
// error says where no row of the tune starts it.
func SourceNumber(tune Tune, source Source) (int, error) {
	at := indexOf(Sources(tune), source)
	if at < 0 {
		return 0, fmt.Errorf("no row of this tune starts that source")
	}
	return at + 1, nil
}

// SourceAt is source number, 1 upward. The error says where the tune runs
// no such source.
func SourceAt(tune Tune, number int) (Source, error) {
	all := Sources(tune)
	if number < 1 || number > len(all) {
		return nil, fmt.Errorf("no source %d: the tune runs %d", number, len(all))
	}
	return all[number-1], nil
}

// Timers claimed is every timer a row starts a source on. A player claims
// them before the first row, and a tune that leaves Timer C alone can be
// hosted from the operating system's 200 Hz clock.
func Claimed(tune Tune) []Timer {
	seen := map[Timer]bool{}
	for _, row := range Rows(tune) {
		for _, one := range Effects(row) {
			if _, ok := one.Effect.(Start); ok {
				seen[one.Timer] = true
			}
		}
	}
	var out []Timer
	for _, timer := range Timers {
		if seen[timer] {
			out = append(out, timer)
		}
	}
	return out
}

// ------------------------------------------------------------- a multi

// NewMulti is a multi of one tune.
func NewMulti(tune Tune) Multi {
	return Multi{Tunes: []Tune{tune}}
}

// TuneAt is tune number, 1 upward, the number a host selects. The error
// says where the multi has no such tune.
func TuneAt(multi Multi, number int) (Tune, error) {
	if number < 1 || number > len(multi.Tunes) {
		return Tune{}, fmt.Errorf("no tune %d: the multi has %d", number, len(multi.Tunes))
	}
	return multi.Tunes[number-1], nil
}

// ------------------------------------------------------------ a target

// Setting is the target that writes the register.
func Setting(register Register) OneTarget {
	return SetRegister{Register: register}
}

// TargetRegisters is the registers this target writes, value i of a row
// to register i. Its length is the target's width (TargetColumns).
func TargetRegisters(target Target) []Register {
	switch t := target.(type) {
	case SetRegister:
		return []Register{t.Register}
	case SetTone:
		return []Register{Fine(t.Voice), Coarse(t.Voice)}
	case SetNoise:
		return []Register{R6, Volume(t.Voice)}
	case SetEnvelope:
		return []Register{R11, R12}
	case SetVoice:
		return []Register{Fine(t.Voice), Coarse(t.Voice), Volume(t.Voice)}
	case SetBuzzer:
		return []Register{R11, R12, R13}
	}
	panic(fmt.Sprintf("no target %T", target))
}

// TargetAt is the target numbered, and an error for a number outside 0 to
// 24.
func TargetAt(number int) (Target, error) {
	if number >= 0 && number <= 13 {
		register, err := RegisterAt(number)
		if err != nil {
			return nil, err
		}
		return SetRegister{Register: register}, nil
	}
	switch number {
	case 14, 15, 16:
		return SetTone{Voice: Voice(number - 14)}, nil
	case 17, 18, 19:
		return SetVoice{Voice: Voice(number - 17)}, nil
	case 20:
		return SetEnvelope{}, nil
	case 21:
		return SetBuzzer{}, nil
	case 22, 23, 24:
		return SetNoise{Voice: Voice(number - 22)}, nil
	}
	return nil, fmt.Errorf("no target %d: a tune reaches 0 to 24", number)
}

// TargetNumber is the number of this target, within the 0 to 127 the
// format reserves.
func TargetNumber(target Target) int {
	switch t := target.(type) {
	case SetRegister:
		return Number(t.Register)
	case SetTone:
		return 14 + int(t.Voice)
	case SetVoice:
		return 17 + int(t.Voice)
	case SetEnvelope:
		return 20
	case SetBuzzer:
		return 21
	case SetNoise:
		return 22 + int(t.Voice)
	}
	panic(fmt.Sprintf("no target %T", target))
}

// TargetName is the name of this target: setR0 to setR13, and setToneA to
// setNoiseC for the targets of several registers.
func TargetName(target Target) string {
	switch t := target.(type) {
	case SetRegister:
		return "set" + t.Register.String()
	case SetTone:
		return "setTone" + t.Voice.String()
	case SetVoice:
		return "setVoice" + t.Voice.String()
	case SetEnvelope:
		return "setEnvelope"
	case SetBuzzer:
		return "setBuzzer"
	case SetNoise:
		return "setNoise" + t.Voice.String()
	}
	panic(fmt.Sprintf("no target %T", target))
}

// TargetColumns is the values one row of a source has for this target.
func TargetColumns(target Target) int {
	return len(TargetRegisters(target))
}

// TargetMosts is the largest value that fits value i of a row, register by
// register. A source run by this target stays within them.
func TargetMosts(target Target) []int {
	var out []int
	for _, register := range TargetRegisters(target) {
		out = append(out, Most(register))
	}
	return out
}

// TargetEqual is whether two targets are the same target.
func TargetEqual(a, b Target) bool {
	if a == nil || b == nil {
		return a == b
	}
	return a == b
}

// ------------------------------------------------------------ a source

// SourceName is the source name. It appears in the tools' reports, and in
// no part of what a player reads.
func SourceName(source Source) string {
	switch s := source.(type) {
	case Single:
		return s.Name
	case Pair:
		return s.Name
	case Triple:
		return s.Name
	}
	panic(fmt.Sprintf("no source %T", source))
}

// SourceRows is the rows of the source, a value a register of the target
// that runs it, and the row they repeat to. A source of one value a row
// reads as a row of one.
func SourceRows(source Source) Table[[]int] {
	switch s := source.(type) {
	case Single:
		var rows [][]int
		for _, value := range s.Table.Rows {
			rows = append(rows, []int{value})
		}
		return Table[[]int]{Rows: rows, repeat: s.Table.repeat}
	case Pair:
		var rows [][]int
		for _, row := range s.Table.Rows {
			rows = append(rows, []int{row.First, row.Second})
		}
		return Table[[]int]{Rows: rows, repeat: s.Table.repeat}
	case Triple:
		var rows [][]int
		for _, row := range s.Table.Rows {
			rows = append(rows, []int{row.First, row.Second, row.Third})
		}
		return Table[[]int]{Rows: rows, repeat: s.Table.repeat}
	}
	panic(fmt.Sprintf("no source %T", source))
}

// SourceTable is the rows of a source of one value a row, and the row they
// repeat to.
func SourceTable(source Source) Table[int] {
	switch s := source.(type) {
	case Single:
		return s.Table
	}
	panic(fmt.Sprintf("no source %T", source))
}

// Values is the values of the source, one a row.
func Values(source Source) []int {
	return SourceTable(source).Rows
}

// SourceColumns is the values in one row of the source.
func SourceColumns(source Source) int {
	switch source.(type) {
	case Single:
		return 1
	case Pair:
		return 2
	case Triple:
		return 3
	}
	panic(fmt.Sprintf("no source %T", source))
}

// SourceOf is the source of that name and those rows: a Single where the
// rows are one value, a Pair where they are two and a Triple where they
// are three. A reader of a form builds a source this way, where the rows
// come off text.
func SourceOf(name string, rows [][]int, repeat int, repeats bool) (Source, error) {
	width := 1
	if len(rows) > 0 {
		width = len(rows[0])
	}
	for _, row := range rows {
		if len(row) != width {
			return nil, fmt.Errorf("source %s has rows of %d and of %d values, and a"+
				" source has one shape", name, width, len(row))
		}
	}
	switch width {
	case 1:
		values := make([]int, len(rows))
		for at, row := range rows {
			values[at] = row[0]
		}
		if repeats {
			return RepeatingSource(name, values, repeat), nil
		}
		return OnceSource(name, values), nil
	case 2:
		values := make([]Two, len(rows))
		for at, row := range rows {
			values[at] = Two{First: row[0], Second: row[1]}
		}
		return Pair{Name: name, Table: tableOf(values, repeat, repeats)}, nil
	case 3:
		values := make([]Three, len(rows))
		for at, row := range rows {
			values[at] = Three{First: row[0], Second: row[1], Third: row[2]}
		}
		return Triple{Name: name, Table: tableOf(values, repeat, repeats)}, nil
	}
	return nil, fmt.Errorf("source %s has %d values a row, and a source has one, two"+
		" or three", name, width)
}

func tableOf[T any](rows []T, repeat int, repeats bool) Table[T] {
	if repeats {
		return Repeating(rows, repeat)
	}
	return Once(rows)
}

// RepeatingSource is a source of one value a row that repeats to that row.
func RepeatingSource(name string, values []int, repeat int) Single {
	return Single{Name: name, Table: Repeating(values, repeat)}
}

// OnceSource is a source of one value a row that plays once and stops its
// timer.
func OnceSource(name string, values []int) Single {
	return Single{Name: name, Table: Once(values)}
}

// SourceEqual is whether two sources are the same source: the same name
// over the same values, repeating at the same row. A source is a value,
// and a tune that starts the same source twice runs one source.
func SourceEqual(a, b Source) bool {
	if a == nil || b == nil {
		return a == b
	}
	if SourceName(a) != SourceName(b) || SourceColumns(a) != SourceColumns(b) {
		return false
	}
	one, two := SourceRows(a), SourceRows(b)
	if len(one.Rows) != len(two.Rows) || one.repeat != two.repeat {
		return false
	}
	for i, row := range one.Rows {
		for at, value := range row {
			if two.Rows[i][at] != value {
				return false
			}
		}
	}
	return true
}

func indexOf(sources []Source, source Source) int {
	for i, one := range sources {
		if SourceEqual(one, source) {
			return i
		}
	}
	return -1
}

// ----------------------------------------------------------- an effect

// StartTarget is the target a start runs its source on.
func StartTarget(start Start) Target {
	switch s := start.(type) {
	case StartOne:
		return s.Target
	case StartPair:
		return s.Target
	case StartTriple:
		return s.Target
	}
	panic(fmt.Sprintf("no start %T", start))
}

// StartSource is the source a start runs.
func StartSource(start Start) Source {
	switch s := start.(type) {
	case StartOne:
		return s.Source
	case StartPair:
		return s.Source
	case StartTriple:
		return s.Source
	}
	panic(fmt.Sprintf("no start %T", start))
}

// StartTiming is the rate a start writes, and the resets it performs with
// it.
func StartTiming(start Start) Timing {
	switch s := start.(type) {
	case StartOne:
		return s.Timing
	case StartPair:
		return s.Timing
	case StartTriple:
		return s.Timing
	}
	panic(fmt.Sprintf("no start %T", start))
}

// Starting is the start of the source on the target, the shape of the
// width they share. A reader of a form builds a start this way, where the
// two come off text; code that names the shapes builds the value. The
// error says where the source has other values a row than the target
// reads.
func Starting(target Target, source Source, timing Timing) (Start, error) {
	one, okTarget := target.(OneTarget)
	single, okSource := source.(Single)
	if okTarget && okSource {
		return StartOne{Target: one, Source: single, Timing: timing}, nil
	}
	two, okTarget := target.(TwoTarget)
	pair, okSource := source.(Pair)
	if okTarget && okSource {
		return StartPair{Target: two, Source: pair, Timing: timing}, nil
	}
	three, okTarget := target.(ThreeTarget)
	triple, okSource := source.(Triple)
	if okTarget && okSource {
		return StartTriple{Target: three, Source: triple, Timing: timing}, nil
	}
	return nil, fmt.Errorf("a source of %d values a row on %s, which reads %d",
		SourceColumns(source), TargetName(target), TargetColumns(target))
}

// TimerReset is the timer's reset: the timer stops, loads the count and
// starts, so it begins a whole period at that count.
func TimerReset(effect Effect) bool {
	switch e := effect.(type) {
	case Start:
		return StartTiming(e).TimerReset
	case Retune:
		return e.Timing.TimerReset
	case Stop:
		return false
	}
	panic(fmt.Sprintf("no effect %T", effect))
}

// PlaceReset is the place's reset: the next tick reads the source's first
// row.
func PlaceReset(effect Effect) bool {
	switch e := effect.(type) {
	case Start:
		return StartTiming(e).PlaceReset
	case Retune:
		return e.Timing.PlaceReset
	case Stop:
		return false
	}
	panic(fmt.Sprintf("no effect %T", effect))
}

// Struck is a struck note: the source from its first row, the timer from a
// whole period.
func Struck(target OneTarget, source Single, prescaler Prescaler, count int) Start {
	return StartOne{Target: target, Source: source, Timing: Timing{
		Prescaler: prescaler, Count: count, TimerReset: true, PlaceReset: true}}
}

// Bend is a bend: the count changes and both resets stay clear.
func Bend(prescaler Prescaler, count int) Retune {
	return Retune{Timing: Timing{Prescaler: prescaler, Count: count}}
}
