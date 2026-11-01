package ym_test

import (
	"encoding/binary"
	"os"
	"strings"
	"testing"

	"github.com/odipar/ymxs/go/text"
	"github.com/odipar/ymxs/go/ym"
	"github.com/odipar/ymxs/go/ymxs"
)

// The packed dump of the test resources is the tune doc/tunes/circus.json
// records: the archive unpacks, the dump reads, and the reading is the
// tune the documents carry.
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
	written := text.Write(ymxs.NewMulti(tune))
	if written != string(said) {
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

// ym5 is a YM5! dump of these frames, a record of sixteen registers a
// frame.
func ym5(frames [][16]byte) []byte {
	out := []byte("YM5!LeOnArD!")
	out = binary.BigEndian.AppendUint32(out, uint32(len(frames)))
	out = binary.BigEndian.AppendUint32(out, 0) // records, not vectors
	out = binary.BigEndian.AppendUint16(out, 0) // no sample
	out = binary.BigEndian.AppendUint32(out, 2000000)
	out = binary.BigEndian.AppendUint16(out, 50)
	out = binary.BigEndian.AppendUint32(out, 0) // the loop frame
	out = binary.BigEndian.AppendUint16(out, 0) // no extra data
	out = append(out, "a square\x00by hand\x00\x00"...)
	for _, frame := range frames {
		out = append(out, frame[:]...)
	}
	return append(out, "End!"...)
}

// ym-to-ymxs writes version 7, every start through the mapping of no parts
// (tools.md 9.1, ym.md 7.4): a square wave of a YM5! dump is a start whose
// mapping column is 0, and the tune has no transformations.
func TestADumpIsWrittenAsVersion7ThroughTheMappingOfNoParts(t *testing.T) {
	var square [16]byte
	square[1] = 0x10 // the first slot on voice A
	square[6] = 3 << 5
	square[7] = 0x3E
	square[8] = 13
	square[14] = 60
	song, err := ym.Read(ym5([][16]byte{square, square}))
	if err != nil {
		t.Fatal(err)
	}
	tune := ym.Of(song, "ym-to-ymxs")
	start, ok := tune.Table.Rows[0].Effects[ymxs.TimerA].(ymxs.Start)
	if !ok {
		t.Fatalf("frame 0 reads as %#v", tune.Table.Rows[0].Effects)
	}
	if parts := ymxs.StartMapping(start).Parts; len(parts) != 0 {
		t.Errorf("the start maps through %d parts", len(parts))
	}
	written := text.Write(ymxs.NewMulti(tune))
	for _, each := range []string{`"version": 7,`, `"transformations": [],`,
		`"mapping": [0,-1]`} {
		if !strings.Contains(written, each) {
			t.Errorf("the tune has no %s:\n%s", each, written)
		}
	}
	if _, err := text.Read(written); err != nil {
		t.Errorf("the tune reads back: %v", err)
	}
}
