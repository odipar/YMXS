// Package ymxs defines tune data for the Atari ST's YM2149 and MC68901.
// doc/SPEC.md defines playback and validation.
//
// A frame applies a tune row's register writes and timer operations. A
// tick reads a source row, applies the effect's mapping, then writes
// through its target. Transformation lookups are optional: the zero
// Mapping uses identity for every value.
//
// Start supplies a connection, timing and mapping. Remap changes the
// running effect's mapping without restarting its source; a pending
// Start retains its separate mapping. Volume transformations can scale
// a sample or fade it while playback continues from its current place.
//
// Sources and Transformations collect data in order of first use. The
// check package validates lookup sizes, keys, mapped values and mapping
// widths. JSON, CSV and player binary layouts are separate encodings of
// these types.
package ymxs

// Multi is several tunes, which a host plays as a tune with subtunes. A
// multi of one tune is a single tune.
type Multi struct {
	Tunes []Tune
}

// Tune contains metadata, a frame rate and tune rows. Sources are
// collected from its starts (Sources), and transformations from its
// starts and remaps (Transformations). A tune with only identity
// mappings uses no transformations.
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
// playing once alone. A repeat row is any int, -1 included: a check
// reports one outside the rows (SPEC.md 1.11).
type Table[T any] struct {
	Rows    []T
	repeat  int  // the row it repeats to, where repeats is set
	repeats bool // whether the table repeats
}

// None is the repeat row Repeat returns for a table that plays once.
const None = -1

// Repeating is a table that repeats to at.
func Repeating[T any](rows []T, at int) Table[T] {
	return Table[T]{Rows: rows, repeat: at, repeats: true}
}

// Once is a table that plays once.
func Once[T any](rows []T) Table[T] {
	return Table[T]{Rows: rows, repeat: None}
}

// Repeat is the row the table repeats to, and whether it repeats at all.
func (t Table[T]) Repeat() (int, bool) {
	return t.repeat, t.repeats
}

// Row is one row: the registers it sets, and its operation on the effect
// of each timer. A register absent from the first map is one the row does
// not write; a timer absent from the second is one the row leaves running.
type Row struct {
	Registers map[Register]int
	Effects   map[Timer]Effect
}

// Effect is one row's operation on the effect of one timer. An effect is a
// source connected to a target on one timer through a mapping, at the rate
// its prescaler and count come to, and a row performs one of four
// operations on it.
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

// Start connects a source and target with the supplied timing and
// mapping. The zero Mapping uses identity for every source value;
// transformation lookups are optional.
//
// With Deferred set on a running effect, the start remains pending
// until the current source's last row. It keeps its mapping while
// pending, even if a Remap changes the running effect (SPEC.md 3.5).
type Start struct {
	Connection Connection
	Timing     Timing
	Deferred   bool
	Mapping    Mapping
}

// Connection is the target a start runs its source on, and that source. A
// connection is a shape a width: each pairs a target with a source of the
// values a row that target reads, so a source that fits its target is a
// shape here rather than a rule a check reads.
type Connection interface {
	connection()
}

// OneConnection is a target of one value a row and a source of one.
type OneConnection struct {
	Target OneTarget
	Source Single
}

// TwoConnection is a target of two values a row and a source of two.
type TwoConnection struct {
	Target TwoTarget
	Source Pair
}

// ThreeConnection is a target of three values a row and a source of three.
type ThreeConnection struct {
	Target ThreeTarget
	Source Triple
}

// Retune changes timing and resets while preserving the source, target
// and mapping. A bend changes the count; a retrigger can reset the
// source place at the same rate.
type Retune struct {
	Timing Timing
}

// Stop stops the effect: its timer stops, and the effect is idle until a
// later row starts a source on it.
type Stop struct{}

// Remap replaces the running effect's mapping for subsequent ticks. Its
// source, target, timing and place are unchanged. A pending Start
// retains its separate mapping; an idle timer stays idle.
//
// Use successive volume transformations for a fade without restarting
// the sample, or the zero Mapping to restore identity.
type Remap struct {
	Mapping Mapping
}

func (Start) effect()  {}
func (Retune) effect() {}
func (Stop) effect()   {}
func (Remap) effect()  {}

func (OneConnection) connection()   {}
func (TwoConnection) connection()   {}
func (ThreeConnection) connection() {}

// Target writes mapped source values to YM2149 registers in target
// order. The effect's mapping supplies either each source value
// unchanged or an optional transformation lookup. Target and source
// widths must match through their Connection.
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

// SetRegister writes one mapped source value to a YM2149 register:
// setR0 to setR13.
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

// SetVoice writes tone fine, tone coarse and volume for one voice.
// Identity parts preserve pitch while an optional transformation on the
// volume scales volume levels. Volume bit 4 selects the envelope
// generator.
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

// Source contains rows read one per tick. Each value passes through its
// mapping part before the target writes it: unchanged through identity,
// or as a lookup key through a transformation. The row width matches
// the target through its Connection.
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

// Mapping contains parts in the order of source values and target
// registers. The zero Mapping means identity for every value.
// Otherwise, supply one part per value: Identity for an unchanged value
// or Transformation for a lookup.
//
// For SetVoice, identity on tone fine and coarse plus a volume
// transformation changes volume alone (SPEC.md 3.6.3).
type Mapping struct {
	Parts []Part
}

// Part maps one source value: Identity leaves it unchanged;
// Transformation selects a lookup entry.
type Part interface {
	part()
}

// Identity leaves a source value unchanged, without a lookup. A
// transformation whose entries equal their keys remains a separate part
// with a bounded key range.
type Identity struct{}

// Transformation is an optional lookup table: source value k selects
// Entries[k] as the value written. The list has 1 to MostEntries
// entries, each 0 to MostEntry; keys run from 0 to the entry count
// minus one.
//
// A table can scale sample levels without copying the source. The
// mapping selects which source values use it. Names are for display,
// reports and equality; TransformationEqual compares names and entries
// in number and order (SPEC.md 3.6).
type Transformation struct {
	Name    string
	Entries []int
}

func (Identity) part()       {}
func (Transformation) part() {}

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
