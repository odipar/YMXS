package check

import (
	"strconv"
	"testing"

	"github.com/odipar/ymxs/go/ymxs"
)

// song is a tune of 16,384 rows, 128 patterns of 128 rows. Timers A, B and
// D start a source every 8 rows, A and B on a voice and D on R10, and each
// remaps it three times in each 8 rows through one of four transformations,
// 18,432 remaps. The tune has no fault.
func song() ymxs.Tune {
	note := func(named string, rows, step int) ymxs.Triple {
		values := make([]ymxs.Three, rows)
		for at := range values {
			values[at] = ymxs.Three{First: at * step % 256, Second: at % 16, Third: at % 16}
		}
		return ymxs.Triple{Name: named, Table: ymxs.Repeating(values, 0)}
	}
	lead, pad, bass := note("lead", 512, 3), note("pad", 384, 5), note("bass", 640, 7)
	levels := make([]int, 256)
	for at := range levels {
		levels[at] = at % 16
	}
	drum := ymxs.RepeatingSource("drum", levels, 0)
	var volumes []ymxs.Transformation
	for _, n := range []int{15, 11, 7, 3} {
		entries := make([]int, 16)
		for k := range entries {
			entries[k] = k * n / 15
		}
		volumes = append(volumes, ymxs.Transformation{Name: "V" + strconv.Itoa(n),
			Entries: entries})
	}
	timing := ymxs.Timing{Prescaler: ymxs.By50, Count: 60, TimerReset: true,
		PlaceReset: true}
	rows := make([]ymxs.Row, 16384)
	for at := range rows {
		rows[at] = ymxs.Row{Registers: map[ymxs.Register]int{},
			Effects: map[ymxs.Timer]ymxs.Effect{}}
	}
	for block := 0; block < len(rows)/8; block++ {
		at := block * 8
		voices := [][]ymxs.Triple{{lead, pad}, {bass, lead}}
		for i, timer := range []ymxs.Timer{ymxs.TimerA, ymxs.TimerB} {
			rows[at].Effects[timer] = ymxs.Start{Connection: ymxs.ThreeConnection{
				Target: ymxs.SetVoice{Voice: ymxs.Voice(i)},
				Source: voices[i][block%2]}, Timing: timing}
		}
		rows[at].Effects[ymxs.TimerD] = ymxs.Start{Connection: ymxs.OneConnection{
			Target: ymxs.Setting(ymxs.R10), Source: drum}, Timing: timing}
		for k, off := range []int{2, 4, 6} {
			volume := volumes[(block*3+k)%len(volumes)]
			voice := ymxs.Remap{Mapping: ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{},
				volume)}
			rows[at+off].Effects[ymxs.TimerA] = voice
			rows[at+off].Effects[ymxs.TimerB] = voice
			rows[at+off].Effects[ymxs.TimerD] = ymxs.Remap{Mapping: ymxs.Mapped(volume)}
		}
	}
	return ymxs.Tune{Rate: 50, Table: ymxs.Repeating(rows, 0)}
}

// A tune check reads the rows of the tune for starts once a timer with a
// remap, and each start on it once. It reads every row of a source started
// for the key of its connection, and compares a start with the connection
// before it of one key: lead and pad on Timer A, bass and lead on Timer B
// and drum on Timer D are five connections over 6,144 starts. It reads each
// mapping of the remaps on a timer once against the connections of the
// timer: four mappings, each over lead and pad on Timer A, 896 rows, over
// bass and lead on Timer B, 1,152 rows, and over drum on Timer D, 256 rows.
// A check that read the starts at every remap would read 18,432 × 16,384
// rows of the tune, 18,432 × 2,048 starts and 6,144 × 2,304 source rows.
func TestATuneCheckReadsEachStartOnceAndEachMappingOnceATimer(t *testing.T) {
	w := &work{}
	if said := tuneOf(song(), w); len(said) != 0 {
		t.Fatalf("the song reads clean, and the check reports %q", said[0])
	}
	keyed := 1024*(512+384) + 1024*(640+512) + 2048*256
	if want := (work{rows: 3 * 16384, starts: 6144, keyed: keyed, compared: 6144 - 5,
		sources: 4 * (896 + 1152 + 256)}); *w != want {
		t.Errorf("the check reads %+v, and %+v is each start once and each mapping"+
			" once a timer", *w, want)
	}
}

// distinct is a tune of 4,096 starts on Timer A of 64-row sources on R8, all
// named s and each of other rows, and a remap after every 64th start where
// remaps is set.
func distinct(remaps bool) ymxs.Tune {
	rows := make([]ymxs.Row, 2*4096)
	for at := range rows {
		rows[at] = ymxs.Row{Registers: map[ymxs.Register]int{},
			Effects: map[ymxs.Timer]ymxs.Effect{}}
	}
	for i := 0; i < 4096; i++ {
		values := make([]int, 64)
		values[61], values[62], values[63] = i/256%16, i/16%16, i%16
		rows[2*i].Effects[ymxs.TimerA] = ymxs.Start{Connection: ymxs.OneConnection{
			Target: ymxs.Setting(ymxs.R8), Source: ymxs.RepeatingSource("s", values, 0)},
			Timing: ymxs.Timing{Prescaler: ymxs.By50, Count: 60, TimerReset: true,
				PlaceReset: true}}
		if remaps && i%64 == 0 {
			rows[2*i+1].Effects[ymxs.TimerA] = ymxs.Remap{}
		}
	}
	return ymxs.Tune{Rate: 50, Table: ymxs.Repeating(rows, 0)}
}

// Without a remap a tune check leaves the starts on a timer unread. With
// one, each of 4,096 sources of one name and one row count has a separate
// key, so a start is compared with no connection before it, and the
// mapping of no parts is read once over the 4,096 connections.
func TestDistinctSourcesOfOneNameAndRowCountAreKeyedApart(t *testing.T) {
	w := &work{}
	tuneOf(distinct(false), w)
	if *w != (work{}) {
		t.Errorf("the check reads %+v of a tune without a remap", *w)
	}
	w = &work{}
	if said := tuneOf(distinct(true), w); len(said) != 0 {
		t.Fatalf("the tune reads clean, and the check reports %q", said[0])
	}
	if want := (work{rows: 8192, starts: 4096, keyed: 4096 * 64,
		sources: 4096 * 64}); *w != want {
		t.Errorf("the check reads %+v, and %+v is each start once and each key"+
			" separate", *w, want)
	}
}
