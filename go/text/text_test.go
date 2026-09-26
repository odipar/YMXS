package text_test

import (
	"os"
	"strings"
	"testing"

	"github.com/odipar/ymxs/go/text"
)

// The tunes of doc/tunes, the examples the documents carry.
var tunes = []string{"circus", "digidrum", "example", "retrigger", "turrican-2", "two-tunes"}

func read(t *testing.T, named string) string {
	t.Helper()
	said, err := os.ReadFile("../../doc/tunes/" + named + ".json")
	if err != nil {
		t.Fatal(err)
	}
	return string(said)
}

// A tune written as text and read back is the text it was: the mapping loses
// no part of a tune, and the layout is one text.
func TestTheTextATuneWasWrittenAsReadsBackToThatText(t *testing.T) {
	for _, named := range tunes {
		said := read(t, named)
		multi, err := text.Read(said)
		if err != nil {
			t.Fatalf("%s: %v", named, err)
		}
		if back := text.Write(multi); back != said {
			t.Errorf("%s: the text moved: %d characters in, %d out", named,
				len(said), len(back))
		}
	}
}

func TestATextThatIsNotThisFormIsRefused(t *testing.T) {
	for _, said := range []string{"", "nope", "{}", `{"format":"nope","version":1}`,
		`{"format":"ymxs","version":2,"tunes":[]}`} {
		if _, err := text.Read(said); err == nil {
			t.Errorf("%q reads as a multi", said)
		}
	}
}

// A file of version 3 reaches the targets 0 to 13 (json.md 2.4): such a
// file reads, and a target above 13 in one is an error of the form, one
// line of json.md 8.1.
func TestAFileOfTheVersionBeforeThisOneReachesTargets0To13(t *testing.T) {
	three := `{"format":"ymxs","version":3,"tunes":[{"title":"","composer":"",
		"writer":"t","rate":50,"rows":2,"repeat":0,
		"sources":[{"name":"a tone","repeat":0,"values":[13,0]}],
		"timerA":{"shape":[0,-1],"target":[8,-1],"source":[1,-1],
		"prescaler":[50,-1],"count":[60,-1],"timerReset":[1,-1],"placeReset":[1,-1]}}]}`
	if _, err := text.Read(three); err != nil {
		t.Fatalf("a file of version 3 reads: %v", err)
	}
	line := "target 14, and version 3 reaches 0 to 13"
	_, err := text.Read(strings.Replace(three, `"target":[8,-1]`, `"target":[14,-1]`, 1))
	if err == nil || err.Error() != line {
		t.Errorf("a target of 14 in version 3 reports %v, and json.md 8.1 has %q", err,
			line)
	}
}
