package ym_test

import (
	"encoding/binary"
	"testing"

	"github.com/odipar/ymxs/go/ym"
	"github.com/odipar/ymxs/go/ymxs"
)

// ym3 is a dump of frames frames, register r of frame f being r*16+f with
// the bits outside the register still in it, and under YM3b loop after the
// vectors (ym.md 2.6).
func ym3(format string, frames int, loop int) []byte {
	trailing := 0
	if format == "YM3b" {
		trailing = 4
	}
	data := make([]byte, 4+14*frames+trailing)
	copy(data, format)
	for r := 0; r < 14; r++ {
		for f := 0; f < frames; f++ {
			data[4+r*frames+f] = byte(r*16 + f)
		}
	}
	if trailing > 0 {
		binary.BigEndian.PutUint32(data[len(data)-4:], uint32(loop))
	}
	return data
}

func TestAYm3DumpIsFourteenVectorsAndNoHeader(t *testing.T) {
	song, err := ym.Read(ym3("YM3!", 8, 0))
	if err != nil {
		t.Fatal(err)
	}
	if song.Format != "YM3!" || song.Frames != 8 || song.PlayerHz != 50 {
		t.Fatalf("%s, %d frames at %d Hz", song.Format, song.Frames, song.PlayerHz)
	}
	if song.LoopFrame != 0 || song.Name != "" || song.Author != "" || len(song.Drums) != 0 {
		t.Fatalf("loop %d, name %q, author %q, %d samples", song.LoopFrame, song.Name,
			song.Author, len(song.Drums))
	}
	for r := 0; r < 14; r++ {
		for f := 0; f < 8; f++ {
			if song.Values[r][f] != byte(r*16+f) {
				t.Errorf("R%d frame %d is %d", r, f, song.Values[r][f])
			}
		}
	}
	for r := 14; r < 16; r++ {
		for f := 0; f < 8; f++ {
			if song.Values[r][f] != 0 {
				t.Errorf("R%d stands outside YM3 and frame %d is %d", r, f, song.Values[r][f])
			}
		}
	}
}

func TestYm3bNamesTheFrameTheDumpRepeatsTo(t *testing.T) {
	song, err := ym.Read(ym3("YM3b", 8, 3))
	if err != nil {
		t.Fatal(err)
	}
	if song.Format != "YM3b" || song.Frames != 8 || song.LoopFrame != 3 {
		t.Fatalf("%s, %d frames, loop %d", song.Format, song.Frames, song.LoopFrame)
	}
	if at, does := ym.Of(song, "a test").Table.Repeat(); !does || at != 3 {
		t.Errorf("the tune repeats to %d, %v", at, does)
	}
}

func TestALoopFramePastTheLastRowRepeatsToRowZero(t *testing.T) {
	song, err := ym.Read(ym3("YM3b", 8, 8))
	if err != nil {
		t.Fatal(err)
	}
	if at, does := ym.Of(song, "a test").Table.Repeat(); !does || at != 0 {
		t.Errorf("the rule of ym.md 8.3 gives %d, %v", at, does)
	}
}

func TestFrameBytesThatAreNotAWholeFrameAreAnError(t *testing.T) {
	whole := ym3("YM3!", 8, 0)
	for _, one := range []struct {
		data []byte
		said string
	}{
		{whole[:len(whole)-3],
			"the frames of a YM3! dump are 109 bytes, and a frame is 14 bytes"},
		{[]byte("YM3!"),
			"the frames of a YM3! dump are 0 bytes, and a frame is 14 bytes"},
	} {
		_, err := ym.Read(one.data)
		if err == nil || err.Error() != one.said {
			t.Errorf("%d bytes read as %v", len(one.data), err)
		}
	}
}

func TestAYm3DumpRunsNoEffect(t *testing.T) {
	song, err := ym.Read(ym3("YM3!", 8, 0))
	if err != nil {
		t.Fatal(err)
	}
	tune := ym.Of(song, "a test")
	if tune.Rate != 50 || ymxs.Size(tune.Table) != 8 {
		t.Fatalf("%d rows at %d Hz", ymxs.Size(tune.Table), tune.Rate)
	}
	if sources := ymxs.Sources(tune); len(sources) != 0 {
		t.Errorf("R14 and R15 are zero, so every slot is off, and %d sources stand",
			len(sources))
	}
	if value := ymxs.Rows(tune)[1].Registers[ymxs.R0]; value != 1 {
		t.Errorf("R0 of frame 1 is %d", value)
	}
}
