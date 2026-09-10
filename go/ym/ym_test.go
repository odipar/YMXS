package ym_test

import (
	"os"
	"testing"

	"github.com/odipar/ymxs/text"
	"github.com/odipar/ymxs/ym"
	"github.com/odipar/ymxs/ymxs"
)

// The packed dump of the test resources is the tune doc/tunes/circus.json
// records: the archive unpacks, the dump reads, and the reading is the
// documents' own.
func TestAPackedDumpReadsToTheTuneTheDocumentsRecord(t *testing.T) {
	packed, err := os.ReadFile("../../src/test/resources/packed.ym")
	if err != nil {
		t.Fatal(err)
	}
	if !ym.IsArchive(packed) {
		t.Fatal("the test resource is not an archive")
	}
	song, err := ym.Read(packed)
	if err != nil {
		t.Fatal(err)
	}
	if song.Format != "YM5!" || song.Name != "Circus Attractions #2" {
		t.Fatalf("the dump reads as %s %q", song.Format, song.Name)
	}
	said, err := os.ReadFile("../../doc/tunes/circus.json")
	if err != nil {
		t.Fatal(err)
	}
	tune := ym.Of(song, "ym-to-ymxs")
	if written := text.Write(ymxs.NewMulti(tune)); written != string(said) {
		t.Errorf("the reading is not the tune the documents record:\n%s", written)
	}
}

func TestWhatIsNotADumpIsRefused(t *testing.T) {
	for _, said := range []string{"", "nope", "YM4!LeOnArD!"} {
		if _, err := ym.Read([]byte(said)); err == nil {
			t.Errorf("%q reads as a dump", said)
		}
	}
}
