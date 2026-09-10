package text_test

import (
	"os"
	"testing"

	"github.com/odipar/ymxs/go/text"
)

// The tunes of doc/tunes, which are the documents' own examples.
var tunes = []string{"circus", "digidrum", "retrigger", "turrican-2", "two-tunes"}

func read(t *testing.T, named string) string {
	t.Helper()
	said, err := os.ReadFile("../../doc/tunes/" + named + ".json")
	if err != nil {
		t.Fatal(err)
	}
	return string(said)
}

// A tune written down and read back is the text it was: the mapping loses
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
