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
			if indexOf(out, start.Source) < 0 {
				out = append(out, start.Source)
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
func Setting(register Register) Target {
	return SetRegister{Register: register}
}

// TargetAt is the target numbered. The error says where this version
// defines none.
func TargetAt(number int) (Target, error) {
	register, err := RegisterAt(number)
	if err != nil {
		return nil, err
	}
	return SetRegister{Register: register}, nil
}

// TargetNumber is the number of this target, within the 0 to 127 the
// format reserves.
func TargetNumber(target Target) int {
	switch t := target.(type) {
	case SetRegister:
		return Number(t.Register)
	}
	panic(fmt.Sprintf("no target %T", target))
}

// TargetName is the name of this target: setR0 to setR13.
func TargetName(target Target) string {
	switch t := target.(type) {
	case SetRegister:
		return "set" + t.Register.String()
	}
	panic(fmt.Sprintf("no target %T", target))
}

// TargetColumns is the values one row of a source has for this target.
func TargetColumns(target Target) int {
	switch target.(type) {
	case SetRegister:
		return 1
	}
	panic(fmt.Sprintf("no target %T", target))
}

// TargetMost is the largest value that fits one of those values. A source
// run by this target stays within it.
func TargetMost(target Target) int {
	switch t := target.(type) {
	case SetRegister:
		return Most(t.Register)
	}
	panic(fmt.Sprintf("no target %T", target))
}

// TargetEqual is whether two targets are the same target.
func TargetEqual(a, b Target) bool {
	one, first := a.(SetRegister)
	two, second := b.(SetRegister)
	return first && second && one.Register == two.Register
}

// ------------------------------------------------------------ a source

// SourceName is the source name. It appears in the tools' reports, and in
// no part of what a player reads.
func SourceName(source Source) string {
	switch s := source.(type) {
	case Single:
		return s.Name
	}
	panic(fmt.Sprintf("no source %T", source))
}

// SourceTable is the rows of the source, and the row they repeat to.
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
	}
	panic(fmt.Sprintf("no source %T", source))
}

// RepeatingSource is a source of one value a row that repeats to that row.
func RepeatingSource(name string, values []int, repeat int) Source {
	return Single{Name: name, Table: Repeating(values, repeat)}
}

// OnceSource is a source of one value a row that plays once and stops its
// timer.
func OnceSource(name string, values []int) Source {
	return Single{Name: name, Table: Once(values)}
}

// SourceEqual is whether two sources are the same source: the same name
// over the same values, repeating at the same row. A source is a value,
// and a tune that starts the same source twice runs one source.
func SourceEqual(a, b Source) bool {
	one, first := a.(Single)
	two, second := b.(Single)
	if !first || !second {
		return false
	}
	if one.Name != two.Name || len(one.Table.Rows) != len(two.Table.Rows) {
		return false
	}
	if one.Table.repeat != two.Table.repeat {
		return false
	}
	for i, value := range one.Table.Rows {
		if two.Table.Rows[i] != value {
			return false
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

// TimerReset is the timer's reset: the timer stops, loads the count and
// starts, so it begins a whole period at that count.
func TimerReset(effect Effect) bool {
	switch e := effect.(type) {
	case Start:
		return e.TimerReset
	case Retune:
		return e.TimerReset
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
		return e.PlaceReset
	case Retune:
		return e.PlaceReset
	case Stop:
		return false
	}
	panic(fmt.Sprintf("no effect %T", effect))
}

// Struck is a struck note: the source from its first row, the timer from a
// whole period.
func Struck(target Target, source Source, prescaler Prescaler, count int) Start {
	return Start{Target: target, Source: source, Prescaler: prescaler, Count: count,
		TimerReset: true, PlaceReset: true}
}

// Bend is a bend: the count changes and both resets stay clear.
func Bend(prescaler Prescaler, count int) Retune {
	return Retune{Prescaler: prescaler, Count: count}
}
