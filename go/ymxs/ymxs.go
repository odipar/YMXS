// Package ymxs is the tune data structure. These declarations are its
// specification, and doc/SPEC.md defines what a player or an emulator does
// with them on an Atari ST's YM2149 and MC68901.
//
// The types here have no methods beyond their fields. A structure is read
// by a function outside it: the chip figures in chip.go, what is read off a
// structure in tunes.go, and the rules a structure must satisfy in the
// check package.
//
// A form encodes a tune. doc/json.md defines one; a player's binary
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
// playing once alone.
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

// Timing is the rate a row writes the timer, and the two resets it
// performs with it: the prescaler and the count are the rate (SPEC.md
// 3.3), and a start and a retune each record one of these.
type Timing struct {
	Prescaler  Prescaler
	Count      int
	TimerReset bool
	PlaceReset bool
}

// Start runs a source on a target at that rate, from this row on. Every
// start records the target and the rate, changed or not, since a start
// defines the effect rather than the parts of it that differ.
//
// A start is a shape a width: each pairs a target with a source of the
// values a row that target reads, so a source that fits its target is a
// shape here rather than a rule a check reads.
type Start interface {
	Effect
	start()
}

// StartOne is a start of one value a row.
type StartOne struct {
	Target OneTarget
	Source Single
	Timing Timing
}

// StartPair is a start of two values a row.
type StartPair struct {
	Target TwoTarget
	Source Pair
	Timing Timing
}

// StartTriple is a start of three values a row.
type StartTriple struct {
	Target ThreeTarget
	Source Triple
	Timing Timing
}

// Retune leaves the effect on its source and target, at the rate in this
// value. A bend changes the count; a note struck again at the rate already
// running changes the place alone.
type Retune struct {
	Timing Timing
}

// Stop stops the effect: its timer stops, and the effect is idle until a
// later row starts a source on it.
type Stop struct{}

func (StartOne) effect()    {}
func (StartPair) effect()   {}
func (StartTriple) effect() {}
func (Retune) effect()      {}
func (Stop) effect()        {}

func (StartOne) start()    {}
func (StartPair) start()   {}
func (StartTriple) start() {}

// Target is the procedure a timer's tick calls with a source's row: it
// that reads one row and writes it to the registers of the target, value i
// to register i. A target is grouped by the values a row it reads, so a
// start pairs it with a source of that width. A later version defines
// targets reaching the MC68901 registers.
type Target interface {
	target()
}

// OneTarget is a target of one value a row.
type OneTarget interface {
	Target
	oneTarget()
}

// TwoTarget is a target of two values a row, the first value to the first
// register it writes.
type TwoTarget interface {
	Target
	twoTarget()
}

// ThreeTarget is a target of three values a row, in the order of the
// registers it writes.
type ThreeTarget interface {
	Target
	threeTarget()
}

// SetRegister is setR0 to setR13, which write a source's row to one
// YM2149 register.
type SetRegister struct {
	Register Register
}

// SetTone is the tone period of one voice, fine then coarse.
type SetTone struct {
	Voice Voice
}

// SetNoise is the noise period and the volume of one voice. R6 is one
// register for the three voices, so two of these running at once write one
// period and the later tick's write remains.
type SetNoise struct {
	Voice Voice
}

// SetEnvelope is the envelope period, fine then coarse.
type SetEnvelope struct{}

// SetVoice is the tone period of one voice and its volume, whose bit 4
// selects the envelope, so one source moves a voice's pitch and volume and
// hands the voice to the envelope generator on a row.
type SetVoice struct {
	Voice Voice
}

// SetBuzzer is the envelope period and the shape, which every write
// restarts.
type SetBuzzer struct{}

func (SetRegister) target()    {}
func (SetTone) target()        {}
func (SetNoise) target()       {}
func (SetEnvelope) target()    {}
func (SetVoice) target()       {}
func (SetBuzzer) target()      {}
func (SetRegister) oneTarget() {}
func (SetTone) twoTarget()     {}
func (SetNoise) twoTarget()    {}
func (SetEnvelope) twoTarget() {}
func (SetVoice) threeTarget()  {}
func (SetBuzzer) threeTarget() {}

// Source is a table a tick advances a row at a time, its target writing
// each row. A source is grouped by the values a row, as a target is by the
// values it reads.
type Source interface {
	source()
}

// Single is one value a row, the row a target of one register reads.
type Single struct {
	Name  string
	Table Table[int]
}

// Pair is two values a row, the row a target of two registers reads.
type Pair struct {
	Name  string
	Table Table[Two]
}

// Triple is three values a row, the row a target of three registers reads.
type Triple struct {
	Name  string
	Table Table[Three]
}

// Two is one row of a source of two values, a value a register of the
// target that runs it.
type Two struct {
	First  int
	Second int
}

// Three is one row of a source of three values, a value a register of the
// target that runs it.
type Three struct {
	First  int
	Second int
	Third  int
}

func (Single) source() {}
func (Pair) source()   {}
func (Triple) source() {}

// Voice is one of the three voices of the YM2149, which a target names
// where it writes the registers of one.
type Voice int

// The three voices, A to C.
const (
	VoiceA Voice = iota
	VoiceB
	VoiceC
)

// String is the voice's letter, A to C.
func (v Voice) String() string {
	return string(rune('A' + int(v)))
}

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

// itoa is strconv.Itoa for a small number, so that this file needs
// the standard library alone.
func itoa(n int) string {
	if n < 10 {
		return string(rune('0' + n))
	}
	return string(rune('0'+n/10)) + string(rune('0'+n%10))
}
