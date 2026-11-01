package text

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"strings"

	"github.com/odipar/ymxs/go/check"
	"github.com/odipar/ymxs/go/ymxs"
)

// ReadAll is every multi in the text, one after another. JSON puts no
// count in front of a stream of values, so a reader reads this
// where several files are handed to it as one.
func ReadAll(said string) ([]ymxs.Multi, error) {
	var out []ymxs.Multi
	decoder := json.NewDecoder(strings.NewReader(said))
	decoder.UseNumber()
	for {
		tree, err := parse(decoder)
		if err == io.EOF {
			break
		} else if err != nil {
			return nil, fmt.Errorf("this is not JSON: %w", err)
		}
		multi, err := multiOf(tree)
		if err != nil {
			return nil, err
		}
		out = append(out, multi)
	}
	return out, nil
}

// Read is the first multi in the text, and what follows it is left where
// it is.
func Read(said string) (ymxs.Multi, error) {
	decoder := json.NewDecoder(strings.NewReader(said))
	decoder.UseNumber()
	tree, err := parse(decoder)
	if err != nil {
		return ymxs.Multi{}, fmt.Errorf("this is not JSON: %w", err)
	}
	return multiOf(tree)
}

// members is a JSON object: its members by name, and the names in the
// order the file first has them. Of two members of one name the last is
// read (json.md 9.1), at the place of the first.
type members struct {
	names  []string
	values map[string]any
}

func (m *members) get(key string) (any, bool) {
	value, set := m.values[key]
	return value, set
}

// parse is the next JSON value of the decoder: an object as members, an
// array as []any, a number as json.Number written as the file has it, and
// a text, a boolean or null as encoding/json reads them. io.EOF is the end
// of the text before a value.
func parse(decoder *json.Decoder) (any, error) {
	token, err := decoder.Token()
	if err != nil {
		return nil, err
	}
	return value(decoder, token)
}

// value is the value that begins with the token.
func value(decoder *json.Decoder, token json.Token) (any, error) {
	delim, ok := token.(json.Delim)
	if !ok {
		return token, nil
	}
	switch delim {
	case '{':
		out := &members{values: map[string]any{}}
		for decoder.More() {
			key, err := inside(decoder)
			if err != nil {
				return nil, err
			}
			name, ok := key.(string)
			if !ok {
				return nil, fmt.Errorf("a member name of %v", key)
			}
			next, err := inside(decoder)
			if err != nil {
				return nil, err
			}
			member, err := value(decoder, next)
			if err != nil {
				return nil, err
			}
			if _, seen := out.values[name]; !seen {
				out.names = append(out.names, name)
			}
			out.values[name] = member
		}
		if _, err := inside(decoder); err != nil {
			return nil, err
		}
		return out, nil
	case '[':
		out := []any{}
		for decoder.More() {
			next, err := inside(decoder)
			if err != nil {
				return nil, err
			}
			element, err := value(decoder, next)
			if err != nil {
				return nil, err
			}
			out = append(out, element)
		}
		if _, err := inside(decoder); err != nil {
			return nil, err
		}
		return out, nil
	}
	return nil, fmt.Errorf("a %v before any value", delim)
}

// inside is the next token within a value, where the end of the text is
// an error rather than the end of the stream.
func inside(decoder *json.Decoder) (json.Token, error) {
	token, err := decoder.Token()
	if err == io.EOF {
		return nil, io.ErrUnexpectedEOF
	}
	return token, err
}

// multiOf is the multi in a JSON tree.
func multiOf(tree any) (ymxs.Multi, error) {
	at := object(tree)
	format, err := text(at, "format")
	if err != nil {
		return ymxs.Multi{}, err
	}
	if format != Format {
		return ymxs.Multi{}, fmt.Errorf("a tree of %s, and this reads %s", format, Format)
	}
	version, err := number(at, "version")
	if err != nil {
		return ymxs.Multi{}, err
	}
	if version < Oldest || version > Version {
		return ymxs.Multi{}, fmt.Errorf("version %d, and this reads %s", version, Reads)
	}
	written, err := array(at, "tunes")
	if err != nil {
		return ymxs.Multi{}, err
	}
	var tunes []ymxs.Tune
	for _, one := range written {
		tune, err := tuneOf(one, version)
		if err != nil {
			return ymxs.Multi{}, err
		}
		tunes = append(tunes, tune)
	}
	return check.MustMulti(ymxs.Multi{Tunes: tunes})
}

// tuneOf is one tune out of a JSON tree, in the steps of json.md 7.1.
func tuneOf(tree any, version int) (ymxs.Tune, error) {
	at := object(tree)
	rows, err := number(at, "rows")
	if err != nil {
		return ymxs.Tune{}, err
	}
	sources, err := sourcesOf(at, version)
	if err != nil {
		return ymxs.Tune{}, err
	}
	// a file of version 6 or before has no transformations, and every
	// start of it reads with the mapping of no parts (json.md 2.4)
	var transformations []ymxs.Transformation
	if version >= Mapped {
		transformations, err = transformationsOf(at)
		if err != nil {
			return ymxs.Tune{}, err
		}
	}
	// A count below 1 is a tune of no rows, an error of the structure
	// (SPEC.md 1.11), and a line of the form reports the count as read.
	registers := make([]map[ymxs.Register]int, max(rows, 0))
	effects := make([]map[ymxs.Timer]ymxs.Effect, max(rows, 0))
	for i := 0; i < rows; i++ {
		registers[i] = map[ymxs.Register]int{}
		effects[i] = map[ymxs.Timer]ymxs.Effect{}
	}
	if written, set := at.get("registers"); set {
		sets, ok := written.(*members)
		if !ok {
			return ymxs.Tune{}, fmt.Errorf("registers is %s, and this form requires a"+
				" column a register", other(written))
		}
		for _, register := range ymxs.Registers {
			column, set := sets.get(Name(register))
			if !set {
				continue
			}
			values, err := sized(column, rows, Name(register))
			if err != nil {
				return ymxs.Tune{}, err
			}
			for row := 0; row < rows; row++ {
				value, err := whole(values[row], fmt.Sprintf("%s at row %d",
					Name(register), row))
				if err != nil {
					return ymxs.Tune{}, err
				}
				if value != None {
					registers[row][register] = value
				}
			}
		}
	}
	read := reading{sources: sources, transformations: transformations, rows: rows,
		version: version}
	for _, timer := range ymxs.Timers {
		written, set := at.get("timer" + timer.String())
		if !set {
			continue
		}
		columns, ok := written.(*members)
		if !ok {
			return ymxs.Tune{}, fmt.Errorf("timer%s is %s, and this form requires a"+
				" column a part of an effect", timer, other(written))
		}
		for row := 0; row < rows; row++ {
			effect, on, err := read.effectOf(columns, row, timer)
			if err != nil {
				return ymxs.Tune{}, err
			}
			if on {
				effects[row][timer] = effect
			}
		}
	}
	built := make([]ymxs.Row, max(rows, 0))
	for row := 0; row < rows; row++ {
		built[row] = ymxs.Row{Registers: registers[row], Effects: effects[row]}
	}
	title, err := text(at, "title")
	if err != nil {
		return ymxs.Tune{}, err
	}
	composer, err := text(at, "composer")
	if err != nil {
		return ymxs.Tune{}, err
	}
	writer, err := text(at, "writer")
	if err != nil {
		return ymxs.Tune{}, err
	}
	rate, err := number(at, "rate")
	if err != nil {
		return ymxs.Tune{}, err
	}
	repeat, repeats, err := repeatOf(at)
	if err != nil {
		return ymxs.Tune{}, err
	}
	table := ymxs.Once(built)
	if repeats {
		table = ymxs.Repeating(built, repeat)
	}
	tune := ymxs.Tune{Title: title, Composer: composer, Writer: writer, Rate: rate,
		Table: table}
	wrong := append(check.Declared(sources, tune), check.Used(transformations, tune)...)
	if len(wrong) != 0 {
		return ymxs.Tune{}, errors.New(strings.Join(wrong, "\n"))
	}
	return tune, nil
}

func sourcesOf(at *members, version int) ([]ymxs.Source, error) {
	written, err := array(at, "sources")
	if err != nil {
		return nil, err
	}
	var out []ymxs.Source
	for _, one := range written {
		source := object(one)
		written, err := array(source, "values")
		if err != nil {
			return nil, err
		}
		name, err := text(source, "name")
		if err != nil {
			return nil, err
		}
		reported := ymxs.Reported(name)
		// json.md 4.1: a row is a number where the source has one value a
		// row, and an array of two or three where it has more, one shape
		// through the source
		rows := make([][]int, len(written))
		for at, row := range written {
			named := fmt.Sprintf("%s at row %d", reported, at)
			if made, ok := row.([]any); ok {
				if version < Several {
					return nil, fmt.Errorf("source %s has a row of several values, and"+
						" version %d has one value a row", reported, version)
				}
				if len(made) < 2 || len(made) > 3 {
					return nil, fmt.Errorf("a row of %d values in source %s, and a row"+
						" is a number or two or three values", len(made), reported)
				}
				rows[at] = make([]int, len(made))
				for value, one := range made {
					rows[at][value], err = whole(one, named)
					if err != nil {
						return nil, err
					}
				}
				continue
			}
			value, err := whole(row, named)
			if err != nil {
				return nil, err
			}
			rows[at] = []int{value}
		}
		for _, row := range rows {
			if len(row) != len(rows[0]) {
				return nil, fmt.Errorf("source %s has rows of %d and of %d values, and a"+
					" source has one shape", reported, len(rows[0]), len(row))
			}
		}
		repeat, repeats, err := repeatOf(source)
		if err != nil {
			return nil, err
		}
		made, err := ymxs.SourceOf(name, rows, repeat, repeats)
		if err != nil {
			return nil, err
		}
		out = append(out, made)
	}
	return out, nil
}

// transformationsOf is the transformations of a tune, in file order: for
// each, entries, then name, then each entry (json.md 7.1 step 6). The
// count of entries and their range are errors of the structure (SPEC.md
// 1.11), which the check reports after the reading.
func transformationsOf(at *members) ([]ymxs.Transformation, error) {
	written, err := array(at, "transformations")
	if err != nil {
		return nil, err
	}
	var out []ymxs.Transformation
	for _, one := range written {
		transformation := object(one)
		entries, err := array(transformation, "entries")
		if err != nil {
			return nil, err
		}
		name, err := text(transformation, "name")
		if err != nil {
			return nil, err
		}
		made := make([]int, len(entries))
		for key, entry := range entries {
			made[key], err = whole(entry, fmt.Sprintf("%s at entry %d", ymxs.Reported(name),
				key))
			if err != nil {
				return nil, err
			}
		}
		out = append(out, ymxs.Transformation{Name: name, Entries: made})
	}
	return out, nil
}

// reading is the figures the columns of a timer are read against: the
// sources and the transformations of the tune, its row count and the
// version of the file.
type reading struct {
	sources         []ymxs.Source
	transformations []ymxs.Transformation
	rows            int
	version         int
}

// effectOf is one row's operation on the effect of one timer, and whether
// the row acts on it. Each column is read and verified before the next, in
// the order of json.md 7.1 step 8: for a start source, target, prescaler,
// count, timerReset, placeReset, in a file of version 5 or later deferred,
// and in a file of version 7 mapping; for a remap mapping. A start then
// has its source verified against its target, for the values a row the
// target reads (ymxs.Starting), an error of the form.
func (r reading) effectOf(columns *members, at int, timer ymxs.Timer) (ymxs.Effect, bool,
	error) {
	shape, err := column(columns, "shape", at, timer, r.rows)
	if err != nil || shape == None {
		return nil, false, err
	}
	switch {
	case shape == Start:
		source, err := column(columns, "source", at, timer, r.rows)
		if err != nil {
			return nil, false, err
		}
		if source < 1 || source > len(r.sources) {
			return nil, false, fmt.Errorf("row %d starts source %d, and the tune runs %d",
				at, source, len(r.sources))
		}
		target, err := column(columns, "target", at, timer, r.rows)
		if err != nil {
			return nil, false, err
		}
		run, err := TargetOf(target, r.version)
		if err != nil {
			return nil, false, err
		}
		timing, err := timingOf(columns, at, timer, r.rows)
		if err != nil {
			return nil, false, err
		}
		deferred := None
		if r.version >= Deferred {
			deferred, err = column(columns, "deferred", at, timer, r.rows)
			if err != nil {
				return nil, false, err
			}
		}
		var mapping ymxs.Mapping
		if r.version >= Mapped {
			mapping, err = r.mappingOf(columns, at, timer)
			if err != nil {
				return nil, false, err
			}
		}
		made, err := ymxs.Starting(run, r.sources[source-1], timing, deferred == 1,
			mapping)
		if err != nil {
			return nil, false, err
		}
		return made, true, nil
	case shape == Retune:
		timing, err := timingOf(columns, at, timer, r.rows)
		if err != nil {
			return nil, false, err
		}
		return ymxs.Retune{Timing: timing}, true, nil
	case shape == Stop:
		return ymxs.Stop{}, true, nil
	case shape == Remap && r.version >= Mapped:
		mapping, err := r.mappingOf(columns, at, timer)
		if err != nil {
			return nil, false, err
		}
		return ymxs.Remap{Mapping: mapping}, true, nil
	}
	if r.version < Mapped {
		return nil, false, fmt.Errorf("row %d sets shape %d on Timer %s, and a shape is"+
			" %d, %d, %d or %d", at, shape, timer, None, Start, Retune, Stop)
	}
	return nil, false, fmt.Errorf("row %d sets shape %d on Timer %s, and a shape is"+
		" %d, %d, %d, %d or %d", at, shape, timer, None, Start, Retune, Stop, Remap)
}

// mappingOf is the mapping at a row of a start or a remap (json.md 6.7):
// the column verified present and of length R, then the value at the row
// verified as 0 or an array of whole numbers, of an array an element other
// than 0 among them, and each element 0 to M, in that order. 0 is the
// mapping of no parts; an array is a part an element, 0 the identity and n
// transformation n.
func (r reading) mappingOf(columns *members, at int, timer ymxs.Timer) (ymxs.Mapping,
	error) {
	written, set := columns.get("mapping")
	if !set {
		return ymxs.Mapping{}, fmt.Errorf("Timer %s has no %q column", timer, "mapping")
	}
	values, err := sized(written, r.rows, fmt.Sprintf("Timer %s's mapping", timer))
	if err != nil {
		return ymxs.Mapping{}, err
	}
	value := values[at]
	if number, ok := wholeOf(value); ok && number == 0 {
		return ymxs.Mapping{}, nil
	}
	elements, ok := value.([]any)
	numbers := make([]int, len(elements))
	for i, element := range elements {
		numbers[i], ok = wholeOf(element)
		if !ok {
			break
		}
	}
	if !ok {
		return ymxs.Mapping{}, fmt.Errorf("Timer %s's mapping at row %d is %s, and this"+
			" form requires 0 or an array of whole numbers", timer, at, rendered(value))
	}
	identity := true
	for _, number := range numbers {
		if number != 0 {
			identity = false
		}
	}
	if identity {
		return ymxs.Mapping{}, fmt.Errorf("Timer %s's mapping at row %d is %s, and a"+
			" mapping of the identity in every part is 0", timer, at, rendered(value))
	}
	return Resolved(numbers, r.transformations, at)
}

// Resolved is the mapping of the part numbers at a row: 0 the identity
// and n transformation n of the tune, 1 to M. A number outside 0 to M is
// the error of json.md 8.1 and csv.md 5.1. The CSV reader calls it too.
func Resolved(numbers []int, transformations []ymxs.Transformation, at int) (ymxs.Mapping,
	error) {
	parts := make([]ymxs.Part, len(numbers))
	for i, number := range numbers {
		if number < 0 || number > len(transformations) {
			return ymxs.Mapping{}, fmt.Errorf("row %d maps through transformation %d, and"+
				" the tune has %d", at, number, len(transformations))
		}
		if number == 0 {
			parts[i] = ymxs.Identity{}
		} else {
			parts[i] = transformations[number-1]
		}
	}
	return ymxs.Mapping{Parts: parts}, nil
}

// timingOf is the timing of a start or a retune: prescaler, count,
// timerReset and placeReset, each read and verified before the next.
func timingOf(columns *members, at int, timer ymxs.Timer, rows int) (ymxs.Timing,
	error) {
	prescaler, err := column(columns, "prescaler", at, timer, rows)
	if err != nil {
		return ymxs.Timing{}, err
	}
	by, err := ymxs.PrescalerBy(prescaler)
	if err != nil {
		return ymxs.Timing{}, err
	}
	count, err := column(columns, "count", at, timer, rows)
	if err != nil {
		return ymxs.Timing{}, err
	}
	timerReset, err := column(columns, "timerReset", at, timer, rows)
	if err != nil {
		return ymxs.Timing{}, err
	}
	placeReset, err := column(columns, "placeReset", at, timer, rows)
	if err != nil {
		return ymxs.Timing{}, err
	}
	return ymxs.Timing{Prescaler: by, Count: count, TimerReset: timerReset == 1,
		PlaceReset: placeReset == 1}, nil
}

// TargetOf is the target of a number, read against the version: a file of
// version 3 reaches 0 to 13 (json.md 2.4). The CSV reader calls it too.
func TargetOf(number, version int) (ymxs.Target, error) {
	if version < Several && number > 13 {
		return nil, fmt.Errorf("target %d, and version %d reaches 0 to 13", number,
			version)
	}
	return ymxs.TargetAt(number)
}

// column is one value of one of a timer's columns.
func column(columns *members, named string, at int, timer ymxs.Timer,
	rows int) (int, error) {
	written, set := columns.get(named)
	if !set {
		return 0, fmt.Errorf("Timer %s has no %q column", timer, named)
	}
	values, err := sized(written, rows, fmt.Sprintf("Timer %s's %s", timer, named))
	if err != nil {
		return 0, err
	}
	return whole(values[at], fmt.Sprintf("Timer %s's %s at row %d", timer, named, at))
}

// sized reads a column, which is one value a row, so its length is the
// tune's row count.
func sized(written any, rows int, named string) ([]any, error) {
	values, ok := written.([]any)
	if !ok {
		return nil, fmt.Errorf("%s is %s, and this form requires a column", named,
			rendered(written))
	}
	if len(values) != rows {
		return nil, fmt.Errorf("%s is %d values long, and the tune has %d rows",
			named, len(values), rows)
	}
	return values, nil
}

// wholeOf is the value of a whole number (json.md 1.4): a JSON number of
// an optional - and digits, -2,147,483,648 to 2,147,483,647. A number past
// an int is no value of this form, as one that is not whole is no value of
// it.
func wholeOf(value any) (int, bool) {
	said, ok := value.(json.Number)
	if !ok {
		return 0, false
	}
	at, err := said.Int64()
	if err != nil || at < math.MinInt32 || at > math.MaxInt32 {
		return 0, false
	}
	return int(at), true
}

func whole(value any, named string) (int, error) {
	at, ok := wholeOf(value)
	if !ok {
		return 0, fmt.Errorf("%s is %s, and this form requires a whole number", named,
			rendered(value))
	}
	return at, nil
}

func text(tree *members, key string) (string, error) {
	value, set := tree.get(key)
	if !set {
		return "", fmt.Errorf("%s is null, and this form requires a text", key)
	}
	said, ok := value.(string)
	if !ok {
		return "", fmt.Errorf("%s is %s, and this form requires a text", key,
			rendered(value))
	}
	return said, nil
}

func number(tree *members, key string) (int, error) {
	value, set := tree.get(key)
	if !set {
		return 0, fmt.Errorf("%s is null, and this form requires a whole number", key)
	}
	return whole(value, key)
}

func array(tree *members, key string) ([]any, error) {
	value, set := tree.get(key)
	if !set {
		return nil, fmt.Errorf("%s is null, and this form requires an array", key)
	}
	values, ok := value.([]any)
	if !ok {
		return nil, fmt.Errorf("%s is %s, and this form requires an array", key,
			rendered(value))
	}
	return values, nil
}

// repeatOf is the row a table repeats to, and whether it repeats.
func repeatOf(tree *members) (int, bool, error) {
	value, set := tree.get("repeat")
	if !set || value == nil {
		return 0, false, nil
	}
	at, ok := wholeOf(value)
	if !ok {
		return 0, false, fmt.Errorf("repeat is %s, and this form requires a row number or"+
			" null", rendered(value))
	}
	return at, true, nil
}

// object is the object a value is, and an empty one where the value is
// another kind: its line is then that of the first key read (json.md 8.2).
func object(value any) *members {
	if at, ok := value.(*members); ok {
		return at
	}
	return &members{values: map[string]any{}}
}

// other is a value of registers or of a timer object that is no object,
// for its line of json.md 8.1: an array as "an array", and another value as
// rendered writes it.
func other(value any) string {
	if _, ok := value.([]any); ok {
		return "an array"
	}
	return rendered(value)
}

// rendered is a value as the X of json.md 8.1 writes it: JSON text with no
// white space between tokens, each number as the file writes it, each text
// as json.md 9.7 defines, and the members of an object in file order.
func rendered(value any) string {
	switch v := value.(type) {
	case nil:
		return "null"
	case bool:
		if v {
			return "true"
		}
		return "false"
	case json.Number:
		return string(v)
	case string:
		return quoted(v)
	case []any:
		var b strings.Builder
		b.WriteByte('[')
		for at, element := range v {
			if at > 0 {
				b.WriteByte(',')
			}
			b.WriteString(rendered(element))
		}
		b.WriteByte(']')
		return b.String()
	case *members:
		var b strings.Builder
		b.WriteByte('{')
		for at, name := range v.names {
			if at > 0 {
				b.WriteByte(',')
			}
			b.WriteString(quoted(name))
			b.WriteByte(':')
			b.WriteString(rendered(v.values[name]))
		}
		b.WriteByte('}')
		return b.String()
	}
	return fmt.Sprintf("%v", value)
}
