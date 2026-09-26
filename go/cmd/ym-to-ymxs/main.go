// Command ym-to-ymxs reads a YM3!/YM3b/YM5!/YM6! register dump on standard
// input and writes JSON on standard output. A distributed .ym is usually an
// archive containing the dump, and both forms read.
//
// One dump is one tune, so the output is a multi of one. ymxs-merge
// combines several.
//
// -r produces a tune that plays once, and -rROW one that repeats to that
// row; without either, a tune repeats to the frame the dump marks.
package main

import (
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/odipar/ymxs/go/text"
	"github.com/odipar/ymxs/go/tool"
	"github.com/odipar/ymxs/go/ym"
	"github.com/odipar/ymxs/go/ymxs"
)

func main() {
	t, rest := tool.Of("ym-to-ymxs", os.Args[1:], "-r")
	repeat := 0
	repeats := true
	chosen := false
	for _, arg := range rest {
		switch {
		case arg == "-r":
			chosen = true
			repeats = false
		case strings.HasPrefix(arg, "-r"):
			chosen = true
			at, err := strconv.Atoi(arg[2:])
			if err != nil {
				t.Usage(arg + " is not a row number")
			}
			repeat = at
		default:
			t.Usage("ym-to-ymxs reads a dump on standard input and writes JSON on" +
				" standard output. Its flags are -rROW, -r and -silent, and \"" +
				arg + "\" is none of them.")
		}
	}
	song, err := ym.Read(t.Bytes())
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	if !chosen {
		repeat, repeats = ym.Row(song), true
	}
	reading := ym.AtRow(song, "ym-to-ymxs", repeat, repeats)
	written := text.Write(ymxs.NewMulti(reading.Tune))
	t.Write(written)
	said(t, song, reading)
}

// said reports what the dump came to, on standard error.
func said(t *tool.Tool, song ym.Song, reading ym.Reading) {
	if !t.Reports() {
		return
	}
	tune := reading.Tune
	var timers []string
	for _, timer := range ymxs.Claimed(tune) {
		timers = append(timers, timer.String())
	}
	out := fmt.Sprintf("%s %q by %q, %s at %d Hz, %s, timers [%s]",
		song.Format, song.Name, song.Author, tool.Count(len(ymxs.Rows(tune)), "row", "rows"),
		tune.Rate, tool.Count(len(ymxs.Sources(tune)), "source", "sources"),
		strings.Join(timers, ", "))
	if reading.Said.Dropped > 0 {
		out += ", " + tool.Count(reading.Said.Dropped, "slot", "slots") +
			" this does not read"
	}
	if reading.Said.Preempted > 0 {
		out += ", " + tool.Count(reading.Said.Preempted, "frame", "frames") +
			" a recording kept a square wave off its voice"
	}
	if reading.Said.CutAtRepeat > 0 {
		out += ", " + tool.Count(reading.Said.CutAtRepeat, "recording", "recordings") +
			" cut at the row the tune repeats to"
	}
	t.Report(out)
}
