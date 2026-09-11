package text

import (
	"encoding/json"
	"fmt"
	"io"
	"strings"

	"github.com/odipar/ymxs/go/check"
	"github.com/odipar/ymxs/go/ymxs"
)

// ReadAll is every multi in the text, one after another. JSON puts no
// count in front of a stream of values, so this is what a reader gets
// where several files are handed to it as one.
func ReadAll(said string) ([]ymxs.Multi, error) {
	var out []ymxs.Multi
	decoder := json.NewDecoder(strings.NewReader(said))
	decoder.UseNumber()
	for {
		var tree any
		if err := decoder.Decode(&tree); err == io.EOF {
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
// it stands.
func Read(said string) (ymxs.Multi, error) {
	decoder := json.NewDecoder(strings.NewReader(said))
	decoder.UseNumber()
	var tree any
	if err := decoder.Decode(&tree); err != nil {
		return ymxs.Multi{}, fmt.Errorf("this is not JSON: %w", err)
	}
	return multiOf(tree)
}

// multiOf is the multi in a JSON tree.
func multiOf(tree any) (ymxs.Multi, error) {
	at, ok := tree.(map[string]any)
	if !ok {
		return ymxs.Multi{}, fmt.Errorf("this is %s, and an object is asked", kind(tree))
	}
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
	if version != Version {
		return ymxs.Multi{}, fmt.Errorf("version %d, and this reads %d", version, Version)
	}
	written, err := array(at, "tunes")
	if err != nil {
		return ymxs.Multi{}, err
	}
	var tunes []ymxs.Tune
	for _, one := range written {
		tune, err := tuneOf(one)
		if err != nil {
			return ymxs.Multi{}, err
		}
		tunes = append(tunes, tune)
	}
	return check.MustMulti(ymxs.Multi{Tunes: tunes})
}

// tuneOf is one tune out of a JSON tree.
func tuneOf(tree any) (ymxs.Tune, error) {
	at, ok := tree.(map[string]any)
	if !ok {
		return ymxs.Tune{}, fmt.Errorf("a tune is %s, and an object is asked", kind(tree))
	}
	rows, err := number(at, "rows")
	if err != nil {
		return ymxs.Tune{}, err
	}
	sources, err := sourcesOf(at)
	if err != nil {
		return ymxs.Tune{}, err
	}
	registers := make([]map[ymxs.Register]int, rows)
	effects := make([]map[ymxs.Timer]ymxs.Effect, rows)
	for i := 0; i < rows; i++ {
		registers[i] = map[ymxs.Register]int{}
		effects[i] = map[ymxs.Timer]ymxs.Effect{}
	}
	if written, set := at["registers"]; set && written != nil {
		sets, ok := written.(map[string]any)
		if !ok {
			return ymxs.Tune{}, fmt.Errorf("registers is %s, and a column a register is"+
				" asked", kind(written))
		}
		for _, register := range ymxs.Registers {
			column, set := sets[Name(register)]
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
	for _, timer := range ymxs.Timers {
		written, set := at["timer"+timer.String()]
		if !set || written == nil {
			continue
		}
		columns, ok := written.(map[string]any)
		if !ok {
			return ymxs.Tune{}, fmt.Errorf("timer%s is %s, and a column a part of an"+
				" effect is asked", timer, kind(written))
		}
		for row := 0; row < rows; row++ {
			effect, on, err := effectOf(columns, row, sources, timer, rows)
			if err != nil {
				return ymxs.Tune{}, err
			}
			if on {
				effects[row][timer] = effect
			}
		}
	}
	built := make([]ymxs.Row, rows)
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
	return ymxs.Tune{Title: title, Composer: composer, Writer: writer, Rate: rate,
		Table: table}, nil
}

func sourcesOf(at map[string]any) ([]ymxs.Source, error) {
	written, err := array(at, "sources")
	if err != nil {
		return nil, err
	}
	var out []ymxs.Source
	for _, one := range written {
		source, ok := one.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("a source is %s, and an object is asked", kind(one))
		}
		name, err := text(source, "name")
		if err != nil {
			return nil, err
		}
		written, err := array(source, "values")
		if err != nil {
			return nil, err
		}
		values := make([]int, len(written))
		for at, value := range written {
			values[at], err = whole(value, fmt.Sprintf("%s at row %d", name, at))
			if err != nil {
				return nil, err
			}
		}
		repeat, repeats, err := repeatOf(source)
		if err != nil {
			return nil, err
		}
		if repeats {
			out = append(out, ymxs.RepeatingSource(name, values, repeat))
		} else {
			out = append(out, ymxs.OnceSource(name, values))
		}
	}
	return out, nil
}

// effectOf is one row's operation on the effect of one timer, and whether
// the row acts on it at all.
func effectOf(columns map[string]any, at int, sources []ymxs.Source, timer ymxs.Timer,
	rows int) (ymxs.Effect, bool, error) {
	shape, err := column(columns, "shape", at, timer, rows)
	if err != nil || shape == None {
		return nil, false, err
	}
	read := func(named string) int {
		if err != nil {
			return 0
		}
		var value int
		value, err = column(columns, named, at, timer, rows)
		return value
	}
	switch shape {
	case Start:
		source := read("source")
		target := read("target")
		prescaler := read("prescaler")
		count := read("count")
		timerReset := read("timerReset")
		placeReset := read("placeReset")
		if err != nil {
			return nil, false, err
		}
		if source < 1 || source > len(sources) {
			return nil, false, fmt.Errorf("row %d starts source %d, and the tune runs %d",
				at, source, len(sources))
		}
		run, err := ymxs.TargetAt(target)
		if err != nil {
			return nil, false, err
		}
		by, err := ymxs.PrescalerBy(prescaler)
		if err != nil {
			return nil, false, err
		}
		return ymxs.Start{Target: run, Source: sources[source-1], Prescaler: by,
				Count: count, TimerReset: timerReset == 1, PlaceReset: placeReset == 1},
			true, nil
	case Retune:
		prescaler := read("prescaler")
		count := read("count")
		timerReset := read("timerReset")
		placeReset := read("placeReset")
		if err != nil {
			return nil, false, err
		}
		by, err := ymxs.PrescalerBy(prescaler)
		if err != nil {
			return nil, false, err
		}
		return ymxs.Retune{Prescaler: by, Count: count, TimerReset: timerReset == 1,
			PlaceReset: placeReset == 1}, true, nil
	case Stop:
		return ymxs.Stop{}, true, nil
	}
	return nil, false, fmt.Errorf("row %d sets shape %d on Timer %s, and a shape is %d,"+
		" %d or %d", at, shape, timer, Start, Retune, Stop)
}

// column is one value of one of a timer's columns.
func column(columns map[string]any, named string, at int, timer ymxs.Timer,
	rows int) (int, error) {
	written, set := columns[named]
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
		return nil, fmt.Errorf("%s is %s, and a column is asked", named, kind(written))
	}
	if len(values) != rows {
		return nil, fmt.Errorf("%s is %d values long, and the tune has %d rows",
			named, len(values), rows)
	}
	return values, nil
}

func whole(value any, named string) (int, error) {
	said, ok := value.(json.Number)
	if !ok {
		return 0, fmt.Errorf("%s is %s, and a whole number is asked", named, kind(value))
	}
	at, err := said.Int64()
	if err != nil {
		return 0, fmt.Errorf("%s is %s, and a whole number is asked", named, said)
	}
	return int(at), nil
}

func text(tree map[string]any, key string) (string, error) {
	value, set := tree[key]
	if !set {
		return "", fmt.Errorf("%s is null, and a text is asked", key)
	}
	said, ok := value.(string)
	if !ok {
		return "", fmt.Errorf("%s is %s, and a text is asked", key, kind(value))
	}
	return said, nil
}

func number(tree map[string]any, key string) (int, error) {
	value, set := tree[key]
	if !set {
		return 0, fmt.Errorf("%s is null, and a whole number is asked", key)
	}
	return whole(value, key)
}

func array(tree map[string]any, key string) ([]any, error) {
	value, set := tree[key]
	if !set {
		return nil, fmt.Errorf("%s is null, and an array is asked", key)
	}
	values, ok := value.([]any)
	if !ok {
		return nil, fmt.Errorf("%s is %s, and an array is asked", key, kind(value))
	}
	return values, nil
}

// repeatOf is the row a table repeats to, and whether it repeats.
func repeatOf(tree map[string]any) (int, bool, error) {
	value, set := tree["repeat"]
	if !set || value == nil {
		return 0, false, nil
	}
	at, err := whole(value, "repeat")
	if err != nil {
		return 0, false, fmt.Errorf("repeat is %s, and a row number or null is asked",
			kind(value))
	}
	return at, true, nil
}

// kind is the kind of a value, for a fault message.
func kind(value any) string {
	switch v := value.(type) {
	case nil:
		return "null"
	case []any:
		return "an array"
	case map[string]any:
		return "an object"
	case string:
		return fmt.Sprintf("%q", v)
	case json.Number:
		return v.String()
	case bool:
		return fmt.Sprintf("%t", v)
	}
	return fmt.Sprintf("%v", value)
}
