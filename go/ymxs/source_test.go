package ymxs

import "testing"

// Two sources are equal where they have one name, one width, equal rows and
// one repeat row. Two tables of one length and repeat whose rows begin at
// one element of one array are equal without a read of the rows; any other
// pair of tables of one length and repeat is read row by row up to the first
// row that differs.
func TestSourceEqualReadsTheRowsOfTwoArraysAlone(t *testing.T) {
	x := []int{1, 2, 3, 4}
	pairs := []Two{{First: 1, Second: 2}, {First: 3, Second: 4}}
	triples := []Three{{First: 1, Second: 2, Third: 3}}
	single := func(named string, table Table[int]) Source {
		return Single{Name: named, Table: table}
	}
	for _, one := range []struct {
		named string
		a, b  Source
		equal bool
		read  int
	}{
		{"one array at one length", single("s", Repeating(x[:3], 0)),
			single("s", Repeating(x[:3], 0)), true, 0},
		{"one array cut to two lengths", single("s", Repeating(x[:3], 0)),
			single("s", Repeating(x[:2], 0)), false, 0},
		{"one array from offset 1", single("s", Repeating(x[1:], 0)),
			single("s", Repeating(x[:3], 0)), false, 1},
		{"a copy of the array from offset 1", single("s", Repeating(x[1:], 0)),
			single("s", Repeating([]int{2, 3, 4}, 0)), true, 3},
		{"a copy that differs in the last row", single("s", Repeating(x, 0)),
			single("s", Repeating([]int{1, 2, 3, 5}, 0)), false, 4},
		{"once against repeating", single("s", Once(x)), single("s", Repeating(x, 0)),
			false, 0},
		{"repeating to row 0 against row 1", single("s", Repeating(x, 0)),
			single("s", Repeating(x, 1)), false, 0},
		{"two names over one array", single("s", Repeating(x, 0)),
			single("t", Repeating(x, 0)), false, 0},
		{"a single against a pair under one name", single("s", Repeating(x[:2], 0)),
			Pair{Name: "s", Table: Repeating(pairs, 0)}, false, 0},
		{"a pair over one array", Pair{Name: "p", Table: Repeating(pairs, 0)},
			Pair{Name: "p", Table: Repeating(pairs, 0)}, true, 0},
		{"a triple and a copy", Triple{Name: "t", Table: Once(triples)},
			Triple{Name: "t", Table: Once([]Three{{First: 1, Second: 2, Third: 3}})},
			true, 1},
		{"two empty tables", single("s", Once([]int{})), single("s", Once[int](nil)),
			true, 0},
	} {
		for _, order := range [][2]Source{{one.a, one.b}, {one.b, one.a}} {
			equal, read := sourceEqual(order[0], order[1])
			if equal != one.equal || read != one.read ||
				SourceEqual(order[0], order[1]) != one.equal {
				t.Errorf("%s: equal %v after %d rows read, and the sources are equal %v"+
					" after %d", one.named, equal, read, one.equal, one.read)
			}
		}
	}
	if !SourceEqual(nil, nil) || SourceEqual(nil, single("s", Once(x))) {
		t.Errorf("a nil source is equal to a nil source alone")
	}
}
