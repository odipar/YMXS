// Package text writes a multi as JSON and reads it back (doc/json.md). It
// is one way of writing the structure down rather than the structure
// itself, and so stays outside the ymxs package.
//
// A tune is written column by column. A register's column is one value a
// row, and a timer's columns one value a row of the operation on that
// effect, None where a row leaves it alone. Every column is as long as the
// tune, so a row is one index across every column.
//
// Where the line breaks is fixed here: a tune's figures stand at the top
// of the tune and each column stands on one line, so a register's values
// are read across a line and the columns are compared down the file. A
// long column wraps at Wrap values, within the width of a screen. The rule
// is depth: the outer structures break their lines, and what stands inside
// those is one item a line.
package text

import (
	"strconv"
	"strings"

	"github.com/odipar/ymxs/go/ymxs"
)

// Format is what the tree calls itself.
const Format = "ymxs"

// Version is the version of the structure this maps.
const Version = 2

// None is what stands in a column where the row left that value alone. It
// is free for this, since it fits neither a register nor a part of an
// effect.
const None = -1

// The value each shape is written as.
const (
	Start  = 0
	Retune = 1
	Stop   = 2
)

// Arrays this deep and shallower put each value on a separate line: the
// tunes, the sources.
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

// tune writes one tune, its object standing at that depth.
func (w *writer) tune(tune ymxs.Tune, depth int) {
	rows := ymxs.Rows(tune)
	sources := ymxs.Sources(tune)
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
					s.repeat("repeat", ymxs.SourceTable(source))
					s.field("values", func() {
						w.values(depth+3, ymxs.Values(source))
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
				w.timer(rows, sources, timer, depth+1)
			})
		}
	})
}

// timer writes one timer's columns: the operation on that effect at every
// row, and None where the row leaves it alone.
func (w *writer) timer(rows []ymxs.Row, sources []ymxs.Source, timer ymxs.Timer, depth int) {
	shape := make([]int, len(rows))
	target := make([]int, len(rows))
	source := make([]int, len(rows))
	prescaler := make([]int, len(rows))
	count := make([]int, len(rows))
	timerReset := make([]int, len(rows))
	placeReset := make([]int, len(rows))
	for at, row := range rows {
		effect, on := row.Effects[timer]
		if !on {
			shape[at], target[at], source[at] = None, None, None
			prescaler[at], count[at] = None, None
			timerReset[at], placeReset[at] = None, None
			continue
		}
		switch e := effect.(type) {
		case ymxs.Start:
			shape[at] = Start
			target[at] = ymxs.TargetNumber(e.Target)
			source[at] = indexOf(sources, e.Source) + 1
			prescaler[at] = ymxs.Divides(e.Prescaler)
			count[at] = e.Count
			timerReset[at] = flag(e.TimerReset)
			placeReset[at] = flag(e.PlaceReset)
		case ymxs.Retune:
			shape[at] = Retune
			target[at], source[at] = None, None
			prescaler[at] = ymxs.Divides(e.Prescaler)
			count[at] = e.Count
			timerReset[at] = flag(e.TimerReset)
			placeReset[at] = flag(e.PlaceReset)
		case ymxs.Stop:
			shape[at] = Stop
			target[at], source[at] = None, None
			prescaler[at], count[at] = None, None
			timerReset[at], placeReset[at] = None, None
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
		} {
			o.field(one.named, func() {
				w.values(depth+1, one.column)
			})
		}
	})
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

// field writes one field whose value write puts down.
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
	}
	return 0, false
}

// quoted is a string as JSON: the quote, the backslash and the control
// characters escaped, and every other byte as it stands.
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
