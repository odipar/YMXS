// Package csv is the other form: a tune as tables, for reading in a
// spreadsheet (doc/csv.md). It writes what the text package writes, and
// both read into the same structure.
//
// A line beginning "### " opens a table and names its columns. Every line
// after it is one row of that table, in ordinary comma-separated values,
// until the next such line.
//
//	### tune,tune,title,composer,writer,rate,rows,repeat
//	1,Circus Attractions #2,Mad Max,ym-to-ymxs,50,4,0
//
// These are ordinary tables, and they are the tables the JSON form writes:
// one form follows from the other. A row of a tune is a row here, with a
// column a register, and an empty cell is a register the row does not set.
//
// A tune opens with a tune table, and the tables after it belong to that
// tune until the next tune table; a source opens with a source table, and
// the values after it belong to that source. A table therefore needs no
// column for its tune or its source: position determines that.
package csv

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/odipar/ymxs/go/check"
	"github.com/odipar/ymxs/go/text"
	"github.com/odipar/ymxs/go/ymxs"
)

// Table is what a line opening a table begins with.
const Table = "### "

// --------------------------------------------------------------- out

// Write is the multi as tables.
func Write(multi ymxs.Multi) string {
	var out strings.Builder
	table(&out, "multi", "format", "version", "tunes")
	row(&out, text.Format, text.Version, len(multi.Tunes))
	for _, tune := range multi.Tunes {
		written(&out, tune)
	}
	return out.String()
}

// written puts one tune down: its tune table, then the tables that belong
// to it.
func written(out *strings.Builder, tune ymxs.Tune) {
	table(out, "tune", "title", "composer", "writer", "rate", "frames", "repeat")
	row(out, tune.Title, tune.Composer, tune.Writer, tune.Rate, len(tune.Table.Rows),
		repeatOf(tune.Table.Repeat))

	sources := ymxs.Sources(tune)
	for _, source := range sources {
		table(out, "source", "name", "repeat")
		row(out, ymxs.SourceName(source), repeatOf(ymxs.SourceTable(source).Repeat))
		table(out, "value", "row", "value")
		for line, value := range ymxs.Values(source) {
			row(out, line, value)
		}
	}

	named := []any{"rows", "row"}
	for _, register := range ymxs.Registers {
		named = append(named, text.Name(register))
	}
	table(out, named...)
	rows := ymxs.Rows(tune)
	for line, one := range rows {
		if len(one.Registers) == 0 {
			continue
		}
		said := []any{line}
		for _, register := range ymxs.Registers {
			if value, set := one.Registers[register]; set {
				said = append(said, value)
			} else {
				said = append(said, "")
			}
		}
		row(out, said...)
	}

	for _, timer := range ymxs.Timers {
		acts(out, timer, rows, sources)
	}
}

// acts puts one timer's table down, where some row acts on that timer. The
// values are the ones the JSON form writes, and a cell is empty where that
// form writes -1.
func acts(out *strings.Builder, timer ymxs.Timer, rows []ymxs.Row, sources []ymxs.Source) {
	any := false
	for _, row := range rows {
		if _, on := row.Effects[timer]; on {
			any = true
			break
		}
	}
	if !any {
		return
	}
	table(out, "timer"+timer.String(), "row", "shape", "target", "source",
		"prescaler", "count", "timerReset", "placeReset")
	for line, one := range rows {
		effect, on := one.Effects[timer]
		if !on {
			continue
		}
		switch e := effect.(type) {
		case ymxs.Start:
			row(out, line, text.Start, ymxs.TargetNumber(e.Target),
				indexOf(sources, e.Source)+1, ymxs.Divides(e.Prescaler), e.Count,
				flagOf(e.TimerReset), flagOf(e.PlaceReset))
		case ymxs.Retune:
			row(out, line, text.Retune, "", "", ymxs.Divides(e.Prescaler), e.Count,
				flagOf(e.TimerReset), flagOf(e.PlaceReset))
		case ymxs.Stop:
			row(out, line, text.Stop, "", "", "", "", "", "")
		}
	}
}

// table opens a table and names its columns, after a blank line.
func table(out *strings.Builder, named ...any) {
	if out.Len() > 0 {
		out.WriteByte('\n')
	}
	out.WriteString(strings.TrimSpace(Table))
	out.WriteByte(' ')
	row(out, named...)
}

// row puts one row down, each cell quoted where it has a comma, a quote or
// a leading or trailing space.
func row(out *strings.Builder, cells ...any) {
	for at, one := range cells {
		if at > 0 {
			out.WriteByte(',')
		}
		out.WriteString(cell(said(one)))
	}
	out.WriteByte('\n')
}

func said(value any) string {
	switch v := value.(type) {
	case string:
		return v
	case int:
		return strconv.Itoa(v)
	}
	return fmt.Sprint(value)
}

func cell(value string) string {
	if strings.ContainsAny(value, "\n\r") {
		panic("a value with a line feed in it, which this form cannot write: " + value)
	}
	quote := strings.ContainsAny(value, ",\"") || value != strings.TrimSpace(value) ||
		strings.HasPrefix(value, "#")
	if quote {
		return `"` + strings.ReplaceAll(value, `"`, `""`) + `"`
	}
	return value
}

func repeatOf(repeat func() (int, bool)) string {
	if at, repeats := repeat(); repeats {
		return strconv.Itoa(at)
	}
	return ""
}

func indexOf(sources []ymxs.Source, source ymxs.Source) int {
	for i, one := range sources {
		if ymxs.SourceEqual(one, source) {
			return i
		}
	}
	return -1
}

func flagOf(set bool) int {
	if set {
		return 1
	}
	return 0
}

// ---------------------------------------------------------------- in

// block is one table: its name, its column names, and its rows.
type block struct {
	name    string
	columns []string
	rows    [][]string
}

// of is the cell named of a row, or empty text where the table defines no
// such column.
func (b *block) of(row []string, named string) string {
	at := -1
	for i, one := range b.columns {
		if one == named {
			at = i
			break
		}
	}
	if at < 0 || at >= len(row) {
		return ""
	}
	return row[at]
}

// Read is the multi in the text.
func Read(said string) (ymxs.Multi, error) {
	sections, err := sections(said)
	if err != nil {
		return ymxs.Multi{}, err
	}
	if len(sections) == 0 || sections[0].name != "multi" {
		return ymxs.Multi{}, fmt.Errorf("the first table is not %q", Table+"multi")
	}
	multi := sections[0]
	if len(multi.rows) != 1 {
		return ymxs.Multi{}, fmt.Errorf("the multi table has %d rows, and one row opens it",
			len(multi.rows))
	}
	format := multi.of(multi.rows[0], "format")
	if format != text.Format {
		return ymxs.Multi{}, fmt.Errorf("a text of %s, and this reads %s", format,
			text.Format)
	}
	version, err := number(multi.of(multi.rows[0], "version"), "version")
	if err != nil {
		return ymxs.Multi{}, err
	}
	if version != text.Version {
		return ymxs.Multi{}, fmt.Errorf("version %d, and this reads %d", version,
			text.Version)
	}
	var tunes []ymxs.Tune
	at := 1
	for at < len(sections) {
		if sections[at].name != "tune" {
			return ymxs.Multi{}, fmt.Errorf("a %q table before any tune opens",
				Table+sections[at].name)
		}
		from := at
		at++
		var mine []*block
		for at < len(sections) && sections[at].name != "tune" {
			mine = append(mine, sections[at])
			at++
		}
		tune, err := tuneOf(sections[from], mine, len(tunes)+1)
		if err != nil {
			return ymxs.Multi{}, err
		}
		tunes = append(tunes, tune)
	}
	return check.MustMulti(ymxs.Multi{Tunes: tunes})
}

// tuneOf is one tune, from the table that opens it and the tables after
// it. A source opens a source table, and the values after it belong to
// that source.
func tuneOf(told *block, mine []*block, number int) (ymxs.Tune, error) {
	if len(told.rows) != 1 {
		return ymxs.Tune{}, fmt.Errorf("tune %d is opened by %d rows, and one row opens it",
			number, len(told.rows))
	}
	one := told.rows[0]
	count, err := whole(told.of(one, "frames"), "frames")
	if err != nil {
		return ymxs.Tune{}, err
	}
	var names []string
	var repeats []string
	var values [][]int
	registers := make([]map[ymxs.Register]int, count)
	effects := make([]map[ymxs.Timer]ymxs.Effect, count)
	for at := 0; at < count; at++ {
		registers[at] = map[ymxs.Register]int{}
		effects[at] = map[ymxs.Timer]ymxs.Effect{}
	}
	var acts []*block
	for _, one := range mine {
		switch one.name {
		case "source":
			if len(one.rows) != 1 {
				return ymxs.Tune{}, fmt.Errorf("tune %d opens a source with %d rows, and"+
					" one row opens it", number, len(one.rows))
			}
			names = append(names, one.of(one.rows[0], "name"))
			repeats = append(repeats, one.of(one.rows[0], "repeat"))
			values = append(values, []int{})
		case "value":
			if len(values) == 0 {
				return ymxs.Tune{}, fmt.Errorf("tune %d opens values before any source",
					number)
			}
			for _, line := range one.rows {
				value, err := whole(one.of(line, "value"), "value")
				if err != nil {
					return ymxs.Tune{}, err
				}
				values[len(values)-1] = append(values[len(values)-1], value)
			}
		case "rows":
			for _, line := range one.rows {
				at, err := rowAt(count, one.of(line, "row"),
					fmt.Sprintf("tune %d sets a row", number))
				if err != nil {
					return ymxs.Tune{}, err
				}
				for _, register := range ymxs.Registers {
					said := one.of(line, text.Name(register))
					if said == "" {
						continue
					}
					value, err := whole(said, text.Name(register))
					if err != nil {
						return ymxs.Tune{}, err
					}
					registers[at][register] = value
				}
			}
		default:
			if !strings.HasPrefix(one.name, "timer") {
				return ymxs.Tune{}, fmt.Errorf("tune %d opens a %q table, which this form"+
					" does not have", number, Table+one.name)
			}
			acts = append(acts, one)
		}
	}
	var sources []ymxs.Source
	for at, name := range names {
		if repeats[at] == "" {
			sources = append(sources, ymxs.OnceSource(name, values[at]))
		} else {
			repeat, err := whole(repeats[at], "repeat")
			if err != nil {
				return ymxs.Tune{}, err
			}
			sources = append(sources, ymxs.RepeatingSource(name, values[at], repeat))
		}
	}
	for _, one := range acts {
		timer, err := timerOf(one.name, number)
		if err != nil {
			return ymxs.Tune{}, err
		}
		for _, line := range one.rows {
			at, err := rowAt(count, one.of(line, "row"),
				fmt.Sprintf("tune %d sets an effect", number))
			if err != nil {
				return ymxs.Tune{}, err
			}
			effect, err := effectOf(one, line, sources, at)
			if err != nil {
				return ymxs.Tune{}, err
			}
			effects[at][timer] = effect
		}
	}
	rows := make([]ymxs.Row, count)
	for at := 0; at < count; at++ {
		rows[at] = ymxs.Row{Registers: registers[at], Effects: effects[at]}
	}
	rate, err := whole(told.of(one, "rate"), "rate")
	if err != nil {
		return ymxs.Tune{}, err
	}
	table := ymxs.Once(rows)
	if said := told.of(one, "repeat"); said != "" {
		repeat, err := whole(said, "repeat")
		if err != nil {
			return ymxs.Tune{}, err
		}
		table = ymxs.Repeating(rows, repeat)
	}
	return ymxs.Tune{Title: told.of(one, "title"), Composer: told.of(one, "composer"),
		Writer: told.of(one, "writer"), Rate: rate, Table: table}, nil
}

// timerOf is the timer a table of that name is for.
func timerOf(table string, tune int) (ymxs.Timer, error) {
	said := strings.TrimPrefix(table, "timer")
	for _, timer := range ymxs.Timers {
		if timer.String() == said {
			return timer, nil
		}
	}
	return 0, fmt.Errorf("tune %d opens a %q table, and a timer is timerA to timer%s",
		tune, Table+table, ymxs.Timers[len(ymxs.Timers)-1])
}

func effectOf(acts *block, one []string, sources []ymxs.Source, at int) (ymxs.Effect, error) {
	shape, err := whole(acts.of(one, "shape"), "shape")
	if err != nil {
		return nil, err
	}
	switch shape {
	case text.Start:
		number, err := whole(acts.of(one, "source"), "source")
		if err != nil {
			return nil, err
		}
		if number < 1 || number > len(sources) {
			return nil, fmt.Errorf("row %d starts source %d, and the tune runs %d",
				at, number, len(sources))
		}
		target, err := whole(acts.of(one, "target"), "target")
		if err != nil {
			return nil, err
		}
		run, err := ymxs.TargetAt(target)
		if err != nil {
			return nil, err
		}
		by, err := prescaler(acts, one)
		if err != nil {
			return nil, err
		}
		count, err := whole(acts.of(one, "count"), "count")
		if err != nil {
			return nil, err
		}
		return ymxs.Start{Target: run, Source: sources[number-1], Prescaler: by,
			Count: count, TimerReset: flag(acts.of(one, "timerReset")),
			PlaceReset: flag(acts.of(one, "placeReset"))}, nil
	case text.Retune:
		by, err := prescaler(acts, one)
		if err != nil {
			return nil, err
		}
		count, err := whole(acts.of(one, "count"), "count")
		if err != nil {
			return nil, err
		}
		return ymxs.Retune{Prescaler: by, Count: count,
			TimerReset: flag(acts.of(one, "timerReset")),
			PlaceReset: flag(acts.of(one, "placeReset"))}, nil
	case text.Stop:
		return ymxs.Stop{}, nil
	}
	return nil, fmt.Errorf("row %d sets shape %d of an effect, and a shape is %d, %d or %d",
		at, shape, text.Start, text.Retune, text.Stop)
}

func prescaler(acts *block, one []string) (ymxs.Prescaler, error) {
	by, err := whole(acts.of(one, "prescaler"), "prescaler")
	if err != nil {
		return 0, err
	}
	return ymxs.PrescalerBy(by)
}

func rowAt(rows int, said, what string) (int, error) {
	at, err := whole(said, "row")
	if err != nil {
		return 0, err
	}
	if at < 0 || at >= rows {
		return 0, fmt.Errorf("%s at row %d, and the tune runs %d rows", what, at, rows)
	}
	return at, nil
}

func whole(said, what string) (int, error) {
	at, err := strconv.Atoi(strings.TrimSpace(said))
	if err != nil {
		return 0, fmt.Errorf("%s is %q, and a whole number is asked", what, said)
	}
	return at, nil
}

func number(said, what string) (int, error) {
	return whole(said, what)
}

// flag reads a cell of 1 or 0, as the JSON form writes one.
func flag(said string) bool {
	at, err := strconv.Atoi(strings.TrimSpace(said))
	return err == nil && at == 1
}

// sections is the tables in the text, in file order.
func sections(said string) ([]*block, error) {
	var out []*block
	var here *block
	for _, line := range strings.Split(said, "\n") {
		if strings.TrimSpace(line) == "" {
			continue
		}
		if strings.HasPrefix(line, "###") {
			heading := Cells(strings.TrimSpace(line[3:]))
			if len(heading) == 0 || strings.TrimSpace(heading[0]) == "" {
				return nil, fmt.Errorf("a table with no name: %s", line)
			}
			here = &block{name: heading[0], columns: heading[1:]}
			out = append(out, here)
			continue
		}
		if here == nil {
			return nil, fmt.Errorf("a row before any table names its columns: %s", line)
		}
		here.rows = append(here.rows, Cells(line))
	}
	return out, nil
}

// Cells is one line's cells; a quoted cell is its content, and two quotes
// inside one stand for a single quote.
func Cells(line string) []string {
	var out []string
	var one strings.Builder
	quoted := false
	runes := []rune(line)
	for at := 0; at < len(runes); at++ {
		c := runes[at]
		switch {
		case quoted:
			if c == '"' {
				if at+1 < len(runes) && runes[at+1] == '"' {
					one.WriteByte('"')
					at++
				} else {
					quoted = false
				}
			} else {
				one.WriteRune(c)
			}
		case c == '"' && one.Len() == 0:
			quoted = true
		case c == ',':
			out = append(out, one.String())
			one.Reset()
		default:
			one.WriteRune(c)
		}
	}
	out = append(out, one.String())
	return out
}
