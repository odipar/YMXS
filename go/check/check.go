// Package check validates YMXS structures and writer rules.
//
// Tune checks transformations, sources, operations and register values,
// reporting every error in SPEC.md 1.12 order. Optional lookups require
// valid entry counts, keys and mapped register values. Remaps are
// checked against every start on their timer, including earlier and
// later starts. Tune reads the starts on a timer once, at the first remap
// on it, and each mapping of a remap once a timer.
//
// Row and Effect lack tune context and leave remap checks to Tune or
// Remapping. Writing separately reports breaches of the writer rules in
// SPEC.md 6. Form-specific parsing belongs to the text and csv
// packages.
package check

import (
	"errors"
	"fmt"
	"math"
	"strconv"
	"strings"

	"github.com/odipar/ymxs/go/ymxs"
)

// Multi lists what is wrong with the multi, or an empty list.
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

// Tune lists what is wrong with the tune, or an empty list, in the order
// of SPEC.md 1.12: the rate, the table, each transformation in number
// order under its number and its name, then each row.
func Tune(tune ymxs.Tune) []string {
	return tuneOf(tune, &work{})
}

// work counts what a tune check reads, for a test of its cost: the rows of
// the tune read for starts, the starts read into the targets and the
// connections of their timers, the source rows read for the key of a
// connection, the connections compared by ymxs.SourceEqual, and the source
// rows read through the mapping of a remap.
type work struct {
	rows     int
	starts   int
	keyed    int
	compared int
	sources  int
}

// tuneOf is Tune, counting what it reads in w. The starts on a timer are
// read once, at the first remap on it, and each mapping once a timer.
func tuneOf(tune ymxs.Tune, w *work) []string {
	var said []string
	if tune.Rate < 1 {
		said = append(said, fmt.Sprintf("a rate of %d: a player is called at least once"+
			" a second", tune.Rate))
	}
	said = append(said, table(len(tune.Table.Rows), tune.Table.Repeat, "the tune")...)
	for at, transformation := range ymxs.Transformations(tune) {
		for _, one := range Transformation(transformation) {
			said = append(said, fmt.Sprintf("transformation %d, %s: %s", at+1,
				ymxs.Reported(transformation.Name), one))
		}
	}
	starts := &remapping{tune: tune}
	for at, row := range tune.Table.Rows {
		for _, one := range rowOf(starts, row, w) {
			said = append(said, fmt.Sprintf("row %d: %s", at, one))
		}
	}
	return said
}

// Transformation lists what is wrong with a transformation, or an empty
// list: the number of its entries, where it is outside 1 to 256, then each
// entry outside 0 to 255 in key order.
func Transformation(transformation ymxs.Transformation) []string {
	var said []string
	if _, counted := ymxs.LastKey(transformation); !counted {
		said = append(said, fmt.Sprintf("the transformation has %d entries, and a"+
			" transformation has 1 to %d", len(transformation.Entries), ymxs.MostEntries))
	}
	for key, entry := range transformation.Entries {
		if entry < 0 || entry > ymxs.MostEntry {
			said = append(said, fmt.Sprintf("entry %d is %d, and an entry is 0 to %d", key,
				entry, ymxs.MostEntry))
		}
	}
	return said
}

// Row checks one row without tune context. It cannot validate remaps
// against starts on their timers; call Tune for those checks.
func Row(row ymxs.Row) []string {
	return rowOf(&remapping{}, row, &work{})
}

// rowOf lists what is wrong with a row of the tune: the registers, then each
// timer with a start, a retune or a remap, A to D. A remap is read against
// the starts on its timer in the tune of starts.
func rowOf(starts *remapping, row ymxs.Row, w *work) []string {
	var said []string
	for _, one := range ymxs.Registered(row) {
		most := ymxs.Most(one.Register)
		if one.Value < 0 || one.Value > most {
			said = append(said, fmt.Sprintf("%s is 0 to %d, and this row sets it to %d",
				one.Register, most, one.Value))
		}
	}
	for _, one := range ymxs.Effects(row) {
		wrong := Effect(one.Effect)
		if remap, ok := one.Effect.(ymxs.Remap); ok {
			wrong = starts.on(one.Timer, w).remapped(remap.Mapping, w)
		}
		for _, line := range wrong {
			said = append(said, fmt.Sprintf("Timer %s: %s", one.Timer, line))
		}
	}
	return said
}

// Effect checks one operation without tune context. It cannot validate
// a remap against starts on that timer; call Tune or Remapping for
// those checks.
func Effect(effect ymxs.Effect) []string {
	switch e := effect.(type) {
	case ymxs.Start:
		said := count(e.Timing.Count)
		said = append(said, rate(e.Timing.Prescaler, e.Timing.Count)...)
		return append(said, runs(e)...)
	case ymxs.Retune:
		return append(count(e.Timing.Count), rate(e.Timing.Prescaler, e.Timing.Count)...)
	case ymxs.Stop, ymxs.Remap:
		return nil
	}
	panic(fmt.Sprintf("no effect %T", effect))
}

// Source checks the source table and negative values. Register limits
// and lookup-key limits depend on a start's target and mapping; use
// Tune for those checks.
func Source(source ymxs.Source) []string {
	rows := ymxs.SourceRows(source)
	said := table(len(rows.Rows), rows.Repeat, "the source")
	for at, row := range rows.Rows {
		for _, value := range row {
			if value < 0 {
				said = append(said, fmt.Sprintf("row %d is %d, and a register is 0 upward",
					at, value))
			}
		}
	}
	return said
}

// runs validates the source, then the mapping's part count. With a
// valid part count, it checks identity values against register limits
// and lookup keys against transformation lengths, then checks selected
// entries against register limits. Invalid transformation lengths and
// entries are reported separately (SPEC.md 1.12).
func runs(start ymxs.Start) []string {
	target := ymxs.ConnectionTarget(start.Connection)
	source := ymxs.ConnectionSource(start.Connection)
	mapping := start.Mapping
	named := ymxs.TargetName(target)
	said := Source(source)
	if reads := ymxs.TargetColumns(target); len(mapping.Parts) != 0 &&
		len(mapping.Parts) != reads {
		return append(said, fmt.Sprintf("a mapping of %d parts on %s, which reads %d",
			len(mapping.Parts), named, reads))
	}
	mosts := ymxs.TargetMosts(target)
	rows := ymxs.SourceRows(source).Rows
	for at, row := range rows {
		for i, most := range mosts {
			part := ymxs.PartAt(mapping, i)
			if _, identity := part.(ymxs.Identity); identity {
				if row[i] > most {
					said = append(said, fmt.Sprintf("a source on %s whose row %d is %d,"+
						" and the target is 0 to %d", named, at, row[i], most))
				}
			} else if last, above := aboveKeys(part, row[i]); above {
				said = append(said, keyed(at, row[i], named, last))
			}
		}
	}
	for at, row := range rows {
		for i, most := range mosts {
			part := ymxs.PartAt(mapping, i)
			if _, identity := part.(ymxs.Identity); identity {
				continue
			}
			if written, read := ymxs.Through(part, row[i]); read && written > most {
				said = append(said, mapped(i, at, written, named, most))
			}
		}
	}
	return said
}

// Remapping checks the mapping against every start on the timer,
// including earlier and later starts. First report incorrect part
// counts once per target. Then check each distinct target/source
// connection in first-start order, prefixing its errors with "start of
// row S: ". Check keys and mapped register values in source-row and
// value order. Through skips lookups with invalid counts, keys or
// selected entries (SPEC.md 1.12).
func Remapping(tune ymxs.Tune, timer ymxs.Timer, remap ymxs.Remap) []string {
	w := &work{}
	return startsOn(tune, timer, w).remapped(remap.Mapping, w)
}

// remapping is the starts of a tune that its remaps are read against, the
// starts on a timer read at the first remap on it. A timer without a remap
// has its starts left unread.
type remapping struct {
	tune   ymxs.Tune
	timers map[ymxs.Timer]*started
}

// on is the starts on the timer, read at the first call for it.
func (r *remapping) on(timer ymxs.Timer, w *work) *started {
	if on, read := r.timers[timer]; read {
		return on
	}
	if r.timers == nil {
		r.timers = map[ymxs.Timer]*started{}
	}
	on := startsOn(r.tune, timer, w)
	r.timers[timer] = on
	return on
}

// started is the starts on one timer of a tune, read once for every remap
// on it: each target once, in first-start order, and each connection once,
// in first-start order with the row of its first start. remaps maps the key
// of each mapping read against them to its lines.
type started struct {
	targets     []ymxs.Target
	connections []connectedAt
	seen        map[ymxs.Target]bool
	connected   map[connectionKey][]int
	remaps      map[string][]string
	key         []byte
}

// connectedAt is a connection and the row of its first start on a timer.
type connectedAt struct {
	row        int
	connection ymxs.Connection
}

// connectionKey is the target of a connection, and the name, the row count
// and a hash of the rows and the repeat row of its source. Two equal
// connections have one key. The connections of one key are compared by
// ymxs.SourceEqual, and two of one key differ only where the hash collides.
type connectionKey struct {
	target ymxs.Target
	name   string
	rows   int
	hash   uint64
}

// startsOn reads the starts on the timer in one pass over the rows of the
// tune.
func startsOn(tune ymxs.Tune, timer ymxs.Timer, w *work) *started {
	on := &started{seen: map[ymxs.Target]bool{}, connected: map[connectionKey][]int{},
		remaps: map[string][]string{}}
	for at, row := range ymxs.Rows(tune) {
		w.rows++
		if start, ok := row.Effects[timer].(ymxs.Start); ok {
			w.starts++
			on.add(at, start.Connection, w)
		}
	}
	return on
}

// add reads the connection of a start at row at into the targets and the
// connections.
func (s *started) add(at int, connection ymxs.Connection, w *work) {
	target, source := ymxs.ConnectionTarget(connection), ymxs.ConnectionSource(connection)
	if !s.seen[target] {
		s.seen[target] = true
		s.targets = append(s.targets, target)
	}
	rows, hash := hashed(source)
	w.keyed += rows
	key := connectionKey{target: target, name: ymxs.SourceName(source), rows: rows,
		hash: hash}
	for _, before := range s.connected[key] {
		w.compared++
		if ymxs.SourceEqual(ymxs.ConnectionSource(s.connections[before].connection), source) {
			return
		}
	}
	s.connected[key] = append(s.connected[key], len(s.connections))
	s.connections = append(s.connections, connectedAt{row: at, connection: connection})
}

// remapped lists what is wrong with a remap through the mapping on this
// timer. Equal mappings have equal lines, so each is read once.
func (s *started) remapped(mapping ymxs.Mapping, w *work) []string {
	s.key = keyOf(s.key[:0], mapping)
	if said, ok := s.remaps[string(s.key)]; ok {
		return said
	}
	said := s.read(mapping, w)
	s.remaps[string(s.key)] = said
	return said
}

// read checks the mapping against the starts: the part count once a
// target, then each connection, its lines prefixed with "start of row S: ".
func (s *started) read(mapping ymxs.Mapping, w *work) []string {
	parts := len(mapping.Parts)
	var said []string
	for _, target := range s.targets {
		if reads := ymxs.TargetColumns(target); parts != 0 && parts != reads {
			said = append(said, fmt.Sprintf("a mapping of %d parts on %s, which reads %d",
				parts, ymxs.TargetName(target), reads))
		}
	}
	for _, one := range s.connections {
		target := ymxs.ConnectionTarget(one.connection)
		if reads := ymxs.TargetColumns(target); parts != 0 && parts != reads {
			continue
		}
		mosts, named := ymxs.TargetMosts(target), ymxs.TargetName(target)
		from := fmt.Sprintf("start of row %d: ", one.row)
		for at, row := range ymxs.SourceRows(ymxs.ConnectionSource(one.connection)).Rows {
			w.sources++
			for i, most := range mosts {
				part := ymxs.PartAt(mapping, i)
				if last, above := aboveKeys(part, row[i]); above {
					said = append(said, from+keyed(at, row[i], named, last))
				} else if written, read := ymxs.Through(part, row[i]); read && written > most {
					said = append(said, from+mapped(i, at, written, named, most))
				}
			}
		}
	}
	return said
}

// keyOf appends the key of the mapping to buf. Two mappings have one key
// where they have as many parts, and each part of one is the part of the
// other at its place, a transformation by ymxs.TransformationEqual: the
// name and every entry. Any other part, nil among them, has the key n, and
// Through panics on it.
func keyOf(buf []byte, mapping ymxs.Mapping) []byte {
	for _, part := range mapping.Parts {
		switch p := part.(type) {
		case ymxs.Identity:
			buf = append(buf, 'i')
		case ymxs.Transformation:
			buf = append(buf, 't')
			buf = strconv.AppendInt(buf, int64(len(p.Name)), 10)
			buf = append(buf, ':')
			buf = append(buf, p.Name...)
			for _, entry := range p.Entries {
				buf = append(buf, ',')
				buf = strconv.AppendInt(buf, int64(entry), 10)
			}
		default:
			buf = append(buf, 'n')
		}
		buf = append(buf, ';')
	}
	return buf
}

// hashed is the row count of a source, and an FNV-1a hash of its values
// in row order and of its repeat row.
func hashed(source ymxs.Source) (int, uint64) {
	hash := uint64(14695981039346656037)
	mix := func(value int) {
		hash = (hash ^ uint64(value)) * 1099511628211
	}
	var rows, repeat int
	var repeats bool
	switch s := source.(type) {
	case ymxs.Single:
		for _, value := range s.Table.Rows {
			mix(value)
		}
		rows = len(s.Table.Rows)
		repeat, repeats = s.Table.Repeat()
	case ymxs.Pair:
		for _, row := range s.Table.Rows {
			mix(row.First)
			mix(row.Second)
		}
		rows = len(s.Table.Rows)
		repeat, repeats = s.Table.Repeat()
	case ymxs.Triple:
		for _, row := range s.Table.Rows {
			mix(row.First)
			mix(row.Second)
			mix(row.Third)
		}
		rows = len(s.Table.Rows)
		repeat, repeats = s.Table.Repeat()
	default:
		panic(fmt.Sprintf("no source %T", source))
	}
	mix(repeat)
	if repeats {
		mix(1)
	}
	return rows, hash
}

// aboveKeys is the last key K of the part, and whether the value is above
// it, where the part is a transformation of 1 to 256 entries (SPEC.md
// 3.6.5). The second result is false through the identity and through a
// transformation of another number of entries.
func aboveKeys(part ymxs.Part, value int) (int, bool) {
	transformation, ok := part.(ymxs.Transformation)
	if !ok {
		return 0, false
	}
	last, counted := ymxs.LastKey(transformation)
	return last, counted && value > last
}

// keyed is the line of row N of a source on a target, whose value V is
// above the last key K of the transformation it is read through.
func keyed(at, value int, target string, last int) string {
	return fmt.Sprintf("a source on %s whose row %d is %d, and a key is 0 to %d", target, at,
		value, last)
}

// mapped is the line of value i of row N of a source, written through a
// mapping as a value above the most of register i of its target.
func mapped(i, at, written int, target string, most int) string {
	return fmt.Sprintf("value %d of row %d maps to %d on %s, and the target is 0 to %d", i,
		at, written, target, most)
}

// rate reads what a rate must satisfy: a 68000 services the ticks it comes
// to. The count is read first, since a count outside the register makes no
// rate.
func rate(prescaler ymxs.Prescaler, at int) []string {
	if at < 0 || at > ymxs.MostCount {
		return nil
	}
	ticks := ymxs.Rate(prescaler, at)
	if ticks > ymxs.MostTicks {
		return []string{fmt.Sprintf("a rate of %d ticks a second: a 68000 at %d MHz"+
			" enters an interrupt and leaves it in %d cycles, so %d a second is every"+
			" cycle it has", ticks, ymxs.CpuClock/1000000, ymxs.TickCycles,
			ymxs.MostTicks)}
	}
	return nil
}

// Declared lists what is wrong with the sources a form declares beside the
// tune its rows make, or nil.
//
// A form numbers its sources and names them, where the structure reaches a
// source through the row that starts it (ymxs.Sources). So a form may
// declare one no row starts, which is dropped where the form is read.
//
// A name reaches a reader of the form and no more: an effect names
// its source by the number of the table it is in, in JSON and in CSV
// alike, so two sources under one name are told apart by those numbers
// and are no fault.
func Declared(declared []ymxs.Source, tune ymxs.Tune) []string {
	var said []string
	run := ymxs.Sources(tune)
	for at, one := range declared {
		found := false
		for _, and := range run {
			if ymxs.SourceEqual(one, and) {
				found = true
				break
			}
		}
		if !found {
			said = append(said, fmt.Sprintf("source %d, %s, is started by no row, and a"+
				" source a tune does not run is dropped where this form is read",
				at+1, ymxs.Reported(ymxs.SourceName(one))))
		}
	}
	return said
}

// Used reports declared transformations unused by any start or remap,
// in declared order. Such declarations would be lost when the file is
// read, because the structure reaches a transformation only through an
// operation's mapping.
func Used(declared []ymxs.Transformation, tune ymxs.Tune) []string {
	var said []string
	used := ymxs.Transformations(tune)
	for at, one := range declared {
		found := false
		for _, and := range used {
			if ymxs.TransformationEqual(one, and) {
				found = true
				break
			}
		}
		if !found {
			said = append(said, fmt.Sprintf("transformation %d, %s, is used by no row, and"+
				" a transformation a tune does not map through is dropped where this form"+
				" is read", at+1, ymxs.Reported(one.Name)))
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

// running records one timer's run: the start, the row its effect begins
// on, the row its source ends on, and the rows that set the target's
// register while it ran. begun is set where the effect begins at a row
// reckoned for the end of the one before it, a deferred start's.
type running struct {
	start      ymxs.Start
	from       int
	until      int
	begun      bool
	first      int
	last       int
	collisions int
}

func (r *running) target() ymxs.Target {
	return ymxs.ConnectionTarget(r.start.Connection)
}

// reckoned is whether a reading of this run rests on the reckoning: its end
// is reckoned, or its first row is (SPEC.md 6.3).
func (r *running) reckoned() bool {
	return r.until != math.MaxInt32 || r.begun
}

// Writing reads the rules of SPEC.md 6 across the tune's rows. A tune that
// breaks one of them plays, but not as written.
//
// Rules 1, 2, 3, 5 and 6 are read here (SPEC.md 6.3). Rule 4 requires a
// start to set the timer's reset where the timer is stopped, and a stopped
// timer begins a whole period with the value or without it, so either
// value is correct and a check skips it. A remap neither ends a run nor
// begins one: its mapping changes the values written, and the registers
// an effect runs on are those of its target (SPEC.md 6.2).
//
// A source that plays once has a start in the rows and no end, so its
// duration is reckoned from its rate (ymxs.Frames). Every reading resting
// on that reckoning is reported as such.
func Writing(tune ymxs.Tune) []string {
	w := &writing{tune: tune,
		running: map[ymxs.Timer]*running{},
		pending: map[ymxs.Timer]ymxs.Start{},
		started: map[ymxs.Timer]bool{}}
	return w.run()
}

type writing struct {
	tune    ymxs.Tune
	said    []string
	running map[ymxs.Timer]*running
	pending map[ymxs.Timer]ymxs.Start
	started map[ymxs.Timer]bool
}

func (w *writing) run() []string {
	for at, row := range w.tune.Table.Rows {
		w.begin(at)
		// The effects come first, in the order a frame writes them.
		for _, one := range ymxs.Effects(row) {
			w.effect(at, one.Timer, one.Effect)
		}
		w.registers(at, row)
	}
	for _, timer := range ymxs.Timers {
		if runs, on := w.running[timer]; on {
			w.collided(timer, runs)
		}
	}
	w.wrap()
	return w.said
}

// begin starts each pending start at the row the effect before it has run
// out by, ahead of the row's operations (SPEC.md 6.3 step 1).
func (w *writing) begin(at int) {
	for _, timer := range ymxs.Timers {
		start, on := w.pending[timer]
		if !on {
			continue
		}
		if _, runs := w.runs(timer, at); runs {
			continue
		}
		delete(w.pending, timer)
		w.starts(at, timer, start, true)
	}
}

// repeats is whether this is the row the tune repeats to, where a writer
// stops every effect so that the wrap resumes from a known setting.
func (w *writing) repeats(at int) bool {
	to, on := w.tune.Table.Repeat()
	return on && to == at
}

// shared reads rule 2: where two timers write one register, the writer
// fixes the order. The rule is the writer's to settle, and the row where
// the second starts is where it arises.
func (w *writing) shared(at int, timer ymxs.Timer, target ymxs.Target, begun bool) {
	registers := written(target)
	for _, one := range ymxs.Timers {
		runs, on := w.running[one]
		if one == timer || !on || at >= runs.until {
			continue
		}
		for _, register := range registers {
			if !has(written(runs.target()), register) {
				continue
			}
			w.say(at, timer, fmt.Sprintf("starts on %s, where Timer %s runs: rule 2"+
				" leaves the order of two timers writing one register to the writer",
				register, one), runs.reckoned() || begun)
			break
		}
	}
}

// wrap reads rule 1 at the wrap: an effect running when the last row has
// played runs on through the row the tune repeats to, which no row of the
// next pass started.
func (w *writing) wrap() {
	to, on := w.tune.Table.Repeat()
	if !on {
		return
	}
	last := len(w.tune.Table.Rows) - 1
	for _, timer := range ymxs.Timers {
		runs, running := w.running[timer]
		if !running || last >= runs.until {
			continue
		}
		// a remap at the repeat row leaves the effect running from the pass
		// before, as a row that leaves the timer alone does (rule 1(d))
		set := false
		for _, one := range ymxs.Effects(w.tune.Table.Rows[to]) {
			if _, remap := one.Effect.(ymxs.Remap); one.Timer == timer && !remap {
				set = true
			}
		}
		if set {
			continue
		}
		said := fmt.Sprintf("the tune repeats to row %d, and Timer %s runs on %s when its"+
			" last row has played: the wrap resumes with the timer running from the pass"+
			" before", to, timer, written(runs.target())[0])
		if runs.reckoned() {
			said += reckoned
		}
		w.said = append(w.said, said)
	}
}

// collided is one run of an effect, with the rows that set its register
// reported as one line.
func (w *writing) collided(timer ymxs.Timer, runs *running) {
	if runs.collisions == 0 {
		return
	}
	register := written(runs.target())[0]
	if runs.collisions == 1 {
		w.say(runs.first, timer, fmt.Sprintf("runs on %s, and this row sets it", register),
			runs.reckoned())
		runs.collisions = 0
		return
	}
	said := fmt.Sprintf("rows %d to %d: Timer %s runs on %s from row %d, and %d of them"+
		" set it", runs.first, runs.last, timer, register, runs.from, runs.collisions)
	if runs.reckoned() {
		said += reckoned
	}
	w.said = append(w.said, said)
	runs.collisions = 0
}

func (w *writing) effect(at int, timer ymxs.Timer, effect ymxs.Effect) {
	switch e := effect.(type) {
	case ymxs.Start:
		w.place(at, timer, e)
		if runs, on := w.runs(timer, at); e.Deferred && on {
			// the effect plays its source once from here, and the start
			// follows it
			runs.until = min(runs.until, at+w.frames(runs.start))
			w.pending[timer] = e
			return
		}
		delete(w.pending, timer)
		w.starts(at, timer, e, false)
	case ymxs.Retune:
		if _, on := w.runs(timer, at); !on {
			w.say(at, timer, "retunes an effect that is idle: a rate written to a timer"+
				" with no source on it starts that timer with no source to run",
				w.reckoned(timer, at))
		}
	case ymxs.Remap:
		// the effect in the model, its run and the pending start are as
		// they were (SPEC.md 6.3 step 2(d))
		if _, on := w.runs(timer, at); !on {
			w.say(at, timer, "remaps an effect that is idle: a mapping is set on a running"+
				" effect, and this row leaves the timer as it is", w.reckoned(timer, at))
		}
	case ymxs.Stop:
		delete(w.pending, timer)
		runs, on := w.running[timer]
		delete(w.running, timer)
		// A stop of a source that has run out by the reckoning is a writer
		// settling rule 4, and the row a tune repeats to stops every
		// effect so that the wrap resumes from a known setting. Neither is
		// a slip. A stop where this timer has run no source is one.
		if !on && !w.repeats(at) {
			w.say(at, timer, "stops an effect this timer has not started", false)
		}
		if on {
			w.collided(timer, runs)
		}
	}
}

// starts puts the effect of a start in the model from this row, replacing
// the one before it; begun where a pending start begins at a reckoned row.
func (w *writing) starts(at int, timer ymxs.Timer, start ymxs.Start, begun bool) {
	w.shared(at, timer, ymxs.ConnectionTarget(start.Connection), begun)
	if before, on := w.running[timer]; on {
		w.collided(timer, before)
	}
	w.running[timer] = &running{start: start, from: at, until: w.until(at, start),
		begun: begun, first: -1, last: -1}
	w.started[timer] = true
}

// place reads rule 3: a start on a timer that has run none sets the place's
// reset. After a start, the place keeps its number modulo the row count of
// the source a start connects (SPEC.md 3.4.3).
func (w *writing) place(at int, timer ymxs.Timer, start ymxs.Start) {
	if start.Timing.PlaceReset || w.started[timer] {
		return
	}
	w.say(at, timer, "starts a source without the place's reset, and this timer has"+
		" run none: the place is where the player left it", false)
}

// registers reads rule 1: while an effect runs on a register, a row does
// not set that register.
func (w *writing) registers(at int, row ymxs.Row) {
	for _, timer := range ymxs.Timers {
		runs, on := w.running[timer]
		if !on || at >= runs.until {
			continue
		}
		hit := false
		for _, register := range written(runs.target()) {
			if register == ymxs.R13 {
				continue
			}
			if _, set := row.Registers[register]; set {
				hit = true
				break
			}
		}
		if !hit {
			continue
		}
		if runs.first < 0 {
			runs.first = at
		}
		runs.last = at
		runs.collisions++
	}
}

func (w *writing) runs(timer ymxs.Timer, at int) (*running, bool) {
	runs, on := w.running[timer]
	if on && at < runs.until {
		return runs, true
	}
	return nil, false
}

// reckoned is whether a reading of this timer at this row rests on the
// reckoning of a source that plays once.
func (w *writing) reckoned(timer ymxs.Timer, at int) bool {
	runs, on := w.running[timer]
	return on && runs.until != math.MaxInt32 && at >= runs.until
}

// reckoned records what a reading rests on where the source is one that plays
// once: its end is reckoned from its rate rather than read off a row.
const reckoned = ", which rests on how long a source that plays once runs, reckoned" +
	" from its rate"

// until is the row a start's source ends on, or no row where it repeats.
func (w *writing) until(at int, start ymxs.Start) int {
	rows := ymxs.SourceRows(ymxs.ConnectionSource(start.Connection))
	if _, repeats := rows.Repeat(); repeats {
		return math.MaxInt32
	}
	return at + w.frames(start)
}

// frames is the frames a start's source runs for, played once at its rate
// (SPEC.md 6.4).
func (w *writing) frames(start ymxs.Start) int {
	return ymxs.Frames(len(ymxs.SourceRows(ymxs.ConnectionSource(start.Connection)).Rows),
		start.Timing.Prescaler, start.Timing.Count, w.tune.Rate)
}

// written is the registers a target writes, value i of a row to register
// i (ymxs.TargetRegisters).
func written(target ymxs.Target) []ymxs.Register {
	return ymxs.TargetRegisters(target)
}

// has is whether the registers have that one in them.
func has(registers []ymxs.Register, one ymxs.Register) bool {
	for _, register := range registers {
		if register == one {
			return true
		}
	}
	return false
}

func (w *writing) say(at int, timer ymxs.Timer, what string, rests bool) {
	said := ""
	if rests {
		said = reckoned
	}
	w.said = append(w.said, fmt.Sprintf("row %d: Timer %s %s%s", at, timer, what, said))
}
