// Package text writes a multi as JSON and reads it back (doc/json.md). It
// is one encoding of the structure rather than the structure
// itself, and so stays outside the ymxs package.
//
// A tune is written column by column. A register's column is one value a
// row, and a timer's columns one value a row of the operation on that
// effect, None where a row leaves it alone. Every column is as long as the
// tune, so a row is one index across every column. A mapping column has
// an array at a row whose mapping maps a value through a transformation.
//
// Where the line breaks is fixed here: a tune's figures are at the top
// of the tune and each column is on one line, so a register's values
// are read across a line and the columns are compared down the file. A
// long column wraps at Wrap values, within the width of a screen. The rule
// is depth: the outer structures break their lines, and what is inside
// those is one item a line.
package text

import (
	"strconv"
	"strings"

	"github.com/odipar/ymxs/go/ymxs"
)

// Format is the name the tree calls itself by.
const Format = "ymxs"

// Version is the version of the structure this maps, and the version a
// writer writes.
const Version = 7

// Mapped is the first version with transformations, mappings and a remap:
// in a file of a version before it every start reads with the mapping of
// no parts, and shape 3 is an error of the form (json.md 2.4).
const Mapped = 7

// Deferred is the first version with a start's deferred: in a file of a
// version before it every start reads with it clear (json.md 2.4).
const Deferred = 5

// Several is the first version with sources of several values a row and
// targets of several registers: a file of a version before it has one value
// a row in every source and a target of 0 to 13 (json.md 2.4).
const Several = 4

// Oldest is the oldest version a reader reads; it reads every version from
// this one to Version (json.md 2.4).
const Oldest = 3

// Reads is the versions a reader reads, as its report lists them: Oldest
// to Version, "3, 4, 5, 6 or 7".
var Reads = func() string {
	var said []string
	for version := Oldest; version < Version; version++ {
		said = append(said, strconv.Itoa(version))
	}
	return strings.Join(said, ", ") + " or " + strconv.Itoa(Version)
}()

// None marks a column where the row left that value alone. It
// is free for this, since it fits neither a register nor a part of an
// effect.
const None = -1

// The value each shape is written as.
const (
	Start  = 0
	Retune = 1
	Stop   = 2
	Remap  = 3
)

// Arrays this deep and shallower put each value on a separate line: the
// tunes, the sources, the transformations.
const arrays = 4

// Objects this deep and shallower put each field on a separate line: the
// file, a tune, and a tune's columns.
const objects = 4

// Wrap is the values on one line of a column before it wraps.
const Wrap = 20

const indent = "  "

// Write is the multi as JSON text, ending in a newline.
func Write(multi ymxs.Multi) string {
	var w writer
	w.object(1, func(o *entries) {
		o.text("format", Format)
		o.number("version", Version)
		o.field("tunes", func() {
			w.array(2, len(multi.Tunes), func(at int) {
				w.tune(multi.Tunes[at], 3)
			})
		})
	})
	w.b.WriteByte('\n')
	return w.b.String()
}

// tune writes one tune, its object at that depth.
func (w *writer) tune(tune ymxs.Tune, depth int) {
	rows := ymxs.Rows(tune)
	sources := ymxs.Sources(tune)
	transformations := ymxs.Transformations(tune)
	w.object(depth, func(o *entries) {
		o.text("title", tune.Title)
		o.text("composer", tune.Composer)
		o.text("writer", tune.Writer)
		o.number("rate", tune.Rate)
		o.number("rows", len(rows))
		o.repeat("repeat", tune.Table)
		o.field("sources", func() {
			w.array(depth+1, len(sources), func(at int) {
				source := sources[at]
				w.object(depth+2, func(s *entries) {
					s.text("name", ymxs.SourceName(source))
					s.repeat("repeat", ymxs.SourceRows(source))
					s.field("values", func() {
						w.rows(depth+3, ymxs.SourceRows(source).Rows)
					})
				})
			})
		})
		// json.md 3.7: name, then entries, 20 a line
		o.field("transformations", func() {
			w.array(depth+1, len(transformations), func(at int) {
				transformation := transformations[at]
				w.object(depth+2, func(t *entries) {
					t.text("name", transformation.Name)
					t.field("entries", func() {
						w.values(depth+3, transformation.Entries)
					})
				})
			})
		})
		o.field("registers", func() {
			w.object(depth+1, func(sets *entries) {
				for _, register := range ymxs.Registers {
					column, any := registerColumn(rows, register)
					if any {
						sets.field(Name(register), func() {
							w.values(depth+2, column)
						})
					}
				}
			})
		})
		for _, timer := range ymxs.Timers {
			if !acts(rows, timer) {
				continue
			}
			o.field("timer"+timer.String(), func() {
				w.timer(rows, sources, transformations, timer, depth+1)
			})
		}
	})
}

// timer writes one timer's columns: the operation on that effect at every
// row, and None where the row leaves it alone or the shape selects
// another column (json.md 6.2).
func (w *writer) timer(rows []ymxs.Row, sources []ymxs.Source,
	transformations []ymxs.Transformation, timer ymxs.Timer, depth int) {
	shape := make([]int, len(rows))
	target := make([]int, len(rows))
	source := make([]int, len(rows))
	prescaler := make([]int, len(rows))
	count := make([]int, len(rows))
	timerReset := make([]int, len(rows))
	placeReset := make([]int, len(rows))
	deferred := make([]int, len(rows))
	mapping := make([]cell, len(rows))
	for at, row := range rows {
		shape[at], target[at], source[at] = None, None, None
		prescaler[at], count[at] = None, None
		timerReset[at], placeReset[at], deferred[at] = None, None, None
		mapping[at] = cell{number: None}
		effect, on := row.Effects[timer]
		if !on {
			continue
		}
		switch e := effect.(type) {
		case ymxs.Start:
			shape[at] = Start
			target[at] = ymxs.TargetNumber(ymxs.ConnectionTarget(e.Connection))
			source[at] = indexOf(sources, ymxs.ConnectionSource(e.Connection)) + 1
			prescaler[at] = ymxs.Divides(e.Timing.Prescaler)
			count[at] = e.Timing.Count
			timerReset[at] = flag(e.Timing.TimerReset)
			placeReset[at] = flag(e.Timing.PlaceReset)
			deferred[at] = flag(e.Deferred)
			mapping[at] = mappingCell(e.Mapping, transformations)
		case ymxs.Retune:
			shape[at] = Retune
			prescaler[at] = ymxs.Divides(e.Timing.Prescaler)
			count[at] = e.Timing.Count
			timerReset[at] = flag(e.Timing.TimerReset)
			placeReset[at] = flag(e.Timing.PlaceReset)
		case ymxs.Stop:
			shape[at] = Stop
		case ymxs.Remap:
			shape[at] = Remap
			mapping[at] = mappingCell(e.Mapping, transformations)
		}
	}
	w.object(depth, func(o *entries) {
		for _, one := range []struct {
			named  string
			column []int
		}{
			{"shape", shape}, {"target", target}, {"source", source},
			{"prescaler", prescaler}, {"count", count},
			{"timerReset", timerReset}, {"placeReset", placeReset},
			{"deferred", deferred},
		} {
			o.field(one.named, func() {
				w.values(depth+1, one.column)
			})
		}
		o.field("mapping", func() {
			w.mapping(depth+1, mapping)
		})
	})
}

// cell is one value of a mapping column: a whole number, or an array of
// the numbers of the parts where parts is set (json.md 6.7).
type cell struct {
	number int
	parts  []int
}

// mappingCell is a mapping as its column has it: 0 where every part is
// the identity, the mapping of no parts included, and otherwise the
// number of each part in part order, 0 the identity and n transformation
// n (json.md 6.7). The list is the tune's (ymxs.Transformations), which has
// every transformation of every mapping of the tune in it, so an error of
// ymxs.PartNumbers is a fault of this package and panics.
func mappingCell(mapping ymxs.Mapping, transformations []ymxs.Transformation) cell {
	if ymxs.IsIdentity(mapping) {
		return cell{}
	}
	parts, err := ymxs.PartNumbers(mapping, transformations)
	if err != nil {
		panic(err)
	}
	return cell{parts: parts}
}

// Name is the name of a register in a form: r0 to r13.
func Name(register ymxs.Register) string {
	return "r" + strconv.Itoa(ymxs.Number(register))
}

// registerColumn is one register's value on every row, and None where the
// row does not set it, with whether any row sets it at all.
func registerColumn(rows []ymxs.Row, register ymxs.Register) ([]int, bool) {
	column := make([]int, len(rows))
	any := false
	for at, row := range rows {
		value, set := row.Registers[register]
		if set {
			column[at] = value
			any = true
		} else {
			column[at] = None
		}
	}
	return column, any
}

func acts(rows []ymxs.Row, timer ymxs.Timer) bool {
	for _, row := range rows {
		if _, on := row.Effects[timer]; on {
			return true
		}
	}
	return false
}

func indexOf(sources []ymxs.Source, source ymxs.Source) int {
	for i, one := range sources {
		if ymxs.SourceEqual(one, source) {
			return i
		}
	}
	return -1
}

func flag(set bool) int {
	if set {
		return 1
	}
	return 0
}

// ---------------------------------------------------------- the layout

type writer struct {
	b strings.Builder
}

// entries writes an object's fields, breaking the lines where the depth
// asks for it.
type entries struct {
	w     *writer
	depth int
	at    int
}

// object writes an object at that depth, its fields from write.
func (w *writer) object(depth int, write func(*entries)) {
	w.b.WriteByte('{')
	o := &entries{w: w, depth: depth}
	write(o)
	if depth <= objects && o.at > 0 {
		w.newline(depth - 1)
	}
	w.b.WriteByte('}')
}

// array writes an array of that many values at that depth, each from
// write.
func (w *writer) array(depth, values int, write func(int)) {
	w.b.WriteByte('[')
	for at := 0; at < values; at++ {
		if at == 0 {
			if depth <= arrays {
				w.newline(depth)
			}
		} else {
			w.b.WriteByte(',')
			if depth <= arrays {
				w.newline(depth)
			}
		}
		write(at)
	}
	if depth <= arrays && values > 0 {
		w.newline(depth - 1)
	}
	w.b.WriteByte(']')
}

// rows writes a source's rows: a number where the source has one value a
// row, and an array of its values where it has more (json.md 4.1).
func (w *writer) rows(depth int, rows [][]int) {
	w.array(depth, len(rows), func(at int) {
		if depth > arrays && at > 0 && at%Wrap == 0 {
			w.newline(depth)
		}
		if len(rows[at]) == 1 {
			w.b.WriteString(strconv.Itoa(rows[at][0]))
			return
		}
		w.array(depth+1, len(rows[at]), func(value int) {
			w.b.WriteString(strconv.Itoa(rows[at][value]))
		})
	})
}

// mapping writes a mapping column at that depth: 20 values a line, an
// array among them 20 numbers a line at the depth below (json.md 9.6).
func (w *writer) mapping(depth int, column []cell) {
	w.array(depth, len(column), func(at int) {
		if depth > arrays && at > 0 && at%Wrap == 0 {
			w.newline(depth)
		}
		if column[at].parts == nil {
			w.b.WriteString(strconv.Itoa(column[at].number))
			return
		}
		w.values(depth+1, column[at].parts)
	})
}

// values writes a column at that depth: one line, wrapping at Wrap.
func (w *writer) values(depth int, column []int) {
	w.array(depth, len(column), func(at int) {
		if depth > arrays && at > 0 && at%Wrap == 0 {
			w.newline(depth)
		}
		w.b.WriteString(strconv.Itoa(column[at]))
	})
}

func (w *writer) newline(at int) {
	w.b.WriteByte('\n')
	for i := 0; i < at; i++ {
		w.b.WriteString(indent)
	}
}

// before opens a field: the separator, the line break the depth asks for,
// and the name.
func (o *entries) before(named string) {
	if o.at == 0 {
		if o.depth <= objects {
			o.w.newline(o.depth)
		}
	} else {
		o.w.b.WriteByte(',')
		if o.depth <= objects {
			o.w.newline(o.depth)
		} else {
			o.w.b.WriteByte(' ')
		}
	}
	o.at++
	o.w.b.WriteString(quoted(named))
	o.w.b.WriteString(": ")
}

// field writes one field whose value write emits.
func (o *entries) field(named string, write func()) {
	o.before(named)
	write()
}

func (o *entries) text(named, said string) {
	o.before(named)
	o.w.b.WriteString(quoted(said))
}

func (o *entries) number(named string, value int) {
	o.before(named)
	o.w.b.WriteString(strconv.Itoa(value))
}

// repeat writes the row a table repeats to, null where it plays once.
func (o *entries) repeat(named string, table any) {
	o.before(named)
	at, repeats := tableRepeat(table)
	if repeats {
		o.w.b.WriteString(strconv.Itoa(at))
	} else {
		o.w.b.WriteString("null")
	}
}

// tableRepeat is the row a table repeats to, and whether it repeats.
func tableRepeat(table any) (int, bool) {
	switch t := table.(type) {
	case ymxs.Table[ymxs.Row]:
		return t.Repeat()
	case ymxs.Table[int]:
		return t.Repeat()
	case ymxs.Table[[]int]:
		return t.Repeat()
	}
	return 0, false
}

// quoted is a string as JSON: the quote, the backslash and the control
// characters escaped, and every other byte as it is.
func quoted(said string) string {
	var b strings.Builder
	b.WriteByte('"')
	for _, r := range said {
		switch r {
		case '"':
			b.WriteString("\\\"")
		case '\\':
			b.WriteString("\\\\")
		case '\b':
			b.WriteString("\\b")
		case '\f':
			b.WriteString("\\f")
		case '\n':
			b.WriteString("\\n")
		case '\r':
			b.WriteString("\\r")
		case '\t':
			b.WriteString("\\t")
		default:
			if r < 0x20 {
				b.WriteString("\\u")
				const hex = "0123456789ABCDEF"
				b.WriteString("00")
				b.WriteByte(hex[(r>>4)&0xF])
				b.WriteByte(hex[r&0xF])
			} else {
				b.WriteRune(r)
			}
		}
	}
	b.WriteByte('"')
	return b.String()
}
