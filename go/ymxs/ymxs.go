// Package ymxs is the tune data structure. These declarations are its
// specification, and doc/SPEC.md defines what a player or an emulator does
// with them on an Atari ST's YM2149 and MC68901.
//
// The types here have no methods beyond their fields. A structure is read
// by a function outside it: the chip figures in chip.go, what is read off a
// structure in tunes.go, and the rules a structure must satisfy in the
// check package.
//
// A form writes a tune down. doc/json.md defines one; a player's binary
// layout is another. No part of this is arranged for a form, and every
// limit here follows from the two chips.
package ymxs

// Multi is several tunes, which a host plays as a tune with subtunes. A
// multi of one tune is a single tune.
type Multi struct {
	Tunes []Tune
}

// Tune is one tune: its title, and its rows, one a frame at the tune's
// rate. Its sources are the ones its rows start (Sources).
type Tune struct {
	Title    string
	Composer string
	Writer   string
	Rate     int
	Table    Table[Row]
}

// Table is rows, and the row they repeat to after the last row, or none
// for a table that plays once. A tune's rows are one of these and so are a
// source's: the tune's advance one a frame, the source's one a tick, and
// that is the whole difference.
//
// The repeat row is read through Repeat, so a table is either repeating or
// playing once and nothing else.
type Table[T any] struct {
	Rows   []T
	repeat int // the row it repeats to, or None
}

// None is the repeat row of a table that plays once.
const None = -1

// Repeating is a table that repeats to at.
func Repeating[T any](rows []T, at int) Table[T] {
	return Table[T]{Rows: rows, repeat: at}
}

// Once is a table that plays once.
func Once[T any](rows []T) Table[T] {
	return Table[T]{Rows: rows, repeat: None}
}

// Repeat is the row the table repeats to, and whether it repeats at all.
func (t Table[T]) Repeat() (int, bool) {
	return t.repeat, t.repeat != None
}

// Row is one row: the registers it sets, and its operation on the effect
// of each timer. A register absent from the first map is one the row does
// not write; a timer absent from the second is one the row leaves running.
type Row struct {
	Registers map[Register]int
	Effects   map[Timer]Effect
}

// Effect is one row's operation on the effect of one timer. An effect is a
// source connected to a target on one timer, at the rate its prescaler and
// count come to, and a row performs one of three operations on it.
type Effect interface {
	effect()
}

// Start runs Source on Target at that rate, from this row on. Every start
// records the target and the rate, changed or not, since a start defines
// the effect rather than the parts of it that differ.
type Start struct {
	Target     Target
	Source     Source
	Prescaler  Prescaler
	Count      int
	TimerReset bool
	PlaceReset bool
}

// Retune leaves the effect on its source and target, at the rate in this
// value. A bend changes the count; a note struck again at the rate already
// running changes the place alone.
type Retune struct {
	Prescaler  Prescaler
	Count      int
	TimerReset bool
	PlaceReset bool
}

// Stop stops the effect: its timer stops, and the effect is idle until a
// later row starts a source on it.
type Stop struct{}

func (Start) effect()  {}
func (Retune) effect() {}
func (Stop) effect()   {}

// Target is what a timer's tick calls with a source's row: a procedure
// that reads one row and writes it. A later version defines targets
// reaching the MC68901 registers, and targets that read a row of more than
// one value.
type Target interface {
	target()
}

// SetRegister is the target of this version, setR0 to setR13, which writes
// a source's row to one YM2149 register.
type SetRegister struct {
	Register Register
}

func (SetRegister) target() {}

// Source is a table a tick advances a row at a time, its target writing
// each row. A later version defines sources of more than one value a row.
type Source interface {
	source()
}

// Single is the source of this version: one value a row, the row shape
// every target of this version reads.
type Single struct {
	Name  string
	Table Table[int]
}

func (Single) source() {}

// Register is one of the fourteen YM2149 registers a row sets and a target
// writes. Two of the chip's sixteen are its I/O ports, outside a tune.
type Register int

// The fourteen registers, R0 to R13.
const (
	R0 Register = iota
	R1
	R2
	R3
	R4
	R5
	R6
	R7
	R8
	R9
	R10
	R11
	R12
	R13
)

// Registers is every register, in the order they are declared.
var Registers = []Register{R0, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13}

// String is the register's name, R0 to R13.
func (r Register) String() string {
	return "R" + itoa(int(r))
}

// Timer is one of the MC68901's four timers. A row sets an effect on one,
// and the timer is the effect.
type Timer int

// The four timers.
const (
	TimerA Timer = iota
	TimerB
	TimerC
	TimerD
)

// Timers is every timer, in the order they are declared.
var Timers = []Timer{TimerA, TimerB, TimerC, TimerD}

// String is the timer's letter, A to D.
func (t Timer) String() string {
	return string(rune('A' + int(t)))
}

// Prescaler is a timer's first divisor. Its rate is the clock divided by
// the prescaler times the count.
type Prescaler int

// The seven prescalers the MC68901 has.
const (
	By4 Prescaler = iota
	By10
	By16
	By50
	By64
	By100
	By200
)

// Prescalers is every prescaler, in the order they are declared.
var Prescalers = []Prescaler{By4, By10, By16, By50, By64, By100, By200}

// itoa is strconv.Itoa for a small number, so that this file imports
// nothing.
func itoa(n int) string {
	if n < 10 {
		return string(rune('0' + n))
	}
	return string(rune('0'+n/10)) + string(rune('0'+n%10))
}
