package ymxs_test

import (
	"testing"

	"github.com/odipar/ymxs/go/ymxs"
)

// PartNumbers numbers each part of a mapping against a list, 0 the
// identity and n transformation n of the list; a transformation the list
// lacks is an error, reported by its name, and a transformation of equal
// entries under another name is a separate transformation (SPEC.md 3.6.2).
func TestPartNumbersReportsATransformationTheListLacks(t *testing.T) {
	half := ymxs.Transformation{Name: "half", Entries: []int{0, 0, 1, 1}}
	other := ymxs.Transformation{Name: "other", Entries: []int{3, 3}}
	numbers, err := ymxs.PartNumbers(ymxs.Mapped(ymxs.Identity{}, half, other),
		[]ymxs.Transformation{other, half})
	if err != nil || len(numbers) != 3 || numbers[0] != 0 || numbers[1] != 2 ||
		numbers[2] != 1 {
		t.Errorf("the parts are numbered %v, %v", numbers, err)
	}
	renamed := ymxs.Transformation{Name: "halved", Entries: half.Entries}
	for _, one := range []struct {
		mapping ymxs.Mapping
		line    string
	}{
		{ymxs.Mapped(ymxs.Identity{}, other), "no row of this tune maps through other"},
		{ymxs.Mapped(half, renamed), "no row of this tune maps through halved"},
	} {
		numbers, err := ymxs.PartNumbers(one.mapping, []ymxs.Transformation{half})
		if err == nil || err.Error() != one.line || numbers != nil {
			t.Errorf("the parts are numbered %v, %v, and the line is %q", numbers, err,
				one.line)
		}
	}
}

// SourceOf reports a source of rows of two widths and a source of 4 values
// a row by its name, each line feed in it as the two characters \n, so the
// line is one line (SPEC.md 1.12). A reader of a form verifies the shape
// of a source before it calls SourceOf, so a direct call reaches these
// lines.
func TestSourceOfReportsTheNameOnOneLine(t *testing.T) {
	for _, one := range []struct {
		rows [][]int
		line string
	}{
		{[][]int{{1}, {2, 3}},
			`source a\nb has rows of 1 and of 2 values, and a source has one shape`},
		{[][]int{{1, 2, 3, 4}},
			`source a\nb has 4 values a row, and a source has one, two or three`},
	} {
		source, err := ymxs.SourceOf("a\nb", one.rows, 0, false)
		if err == nil || err.Error() != one.line || source != nil {
			t.Errorf("rows of %v are %v, %v, and the line is %q", one.rows, source, err,
				one.line)
		}
	}
}
