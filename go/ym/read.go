package ym

import (
	"strconv"

	"github.com/odipar/ymxs/go/ymxs"
)

// A dump read into a tune: one row a frame, and a source for each distinct
// sound the dump's effect slots produce.
//
// A dump contains every register of every frame, so a row here sets a
// register where the dump's value changed. A register an effect is running
// on belongs to that effect, and no row sets it before the row that stops
// it, as rule 1 of SPEC.md 6 requires.
//
// The two slots run on Timers A and D. A slot sounding a square wave
// becomes a source of a level and a silence; one restarting the envelope,
// a source of the shape; one playing a recording, a source of the sample's
// levels and a closing row at mid-scale. A recording owns the voice's
// volume for its duration at its rate, and silences that voice's tone and
// noise meanwhile.
//
// A recording on a voice excludes a square wave on it: the dump's player
// runs one effect a voice, and the recording is that effect.
//
// The row a tune repeats to sets every register except those an effect is
// running on, and sets every effect that ran up to it or runs into the
// wrap, so the wrap resumes from the chip as the rows left it.

// fits is the bits that fit each register. A dump uses the rest for its
// flags.
var fits = [14]int{0xFF, 0x0F, 0xFF, 0x0F, 0xFF, 0x0F, 0x1F, 0x3F,
	0x1F, 0x1F, 0x1F, 0xFF, 0xFF, 0x0F}

// park is the level a recording's last row leaves its register at:
// mid-scale, so the frame's write that sets the register back does not
// click.
const park = 13

// timerOf is the timer each of the dump's two slots runs on.
var timerOf = [2]ymxs.Timer{ymxs.TimerA, ymxs.TimerD}

// Said is the counts a reading produces, beside the tune.
type Said struct {
	Dropped     int
	Preempted   int
	CutAtRepeat int
}

// Reading is a tune, and the counts of the reading.
type Reading struct {
	Tune ymxs.Tune
	Said Said
}

type reader struct {
	song        Song
	repeat      int
	known       map[int]ymxs.Source
	samples     [][]byte
	dropped     int
	preempted   int
	cutAtRepeat int
}

// Of is the tune in the song, repeating to the frame the dump marks.
// writer becomes the tune's writer field.
func Of(song Song, writer string) ymxs.Tune {
	return AtRow(song, writer, Row(song), true).Tune
}

// Row is the row a dump repeats to, or 0 where it marks none this reads.
func Row(song Song) int {
	loop := song.LoopFrame
	if loop >= 0 && loop < int64(song.Frames) {
		return int(loop)
	}
	return 0
}

// AtRow is the same as Of, for a caller that fixes the row to repeat to,
// with the counts of the reading beside the tune. A tune that repeats to
// no row plays once.
func AtRow(song Song, writer string, repeat int, repeats bool) Reading {
	at := repeat
	if !repeats {
		at = song.Frames
	}
	r := &reader{song: song, repeat: at, known: map[int]ymxs.Source{}}
	fourBit := song.Attribute&DrumsAre4Bit != 0
	r.samples = make([][]byte, len(song.Drums))
	for i, drum := range song.Drums {
		r.samples[i] = make([]byte, len(drum))
		for j, one := range drum {
			if fourBit {
				r.samples[i][j] = one & 15
			} else {
				r.samples[i][j] = one >> 4
			}
		}
	}
	registers, effects := r.run()
	rows := make([]ymxs.Row, song.Frames)
	for f := 0; f < song.Frames; f++ {
		rows[f] = ymxs.Row{Registers: registers[f], Effects: effects[f]}
	}
	table := ymxs.Once(rows)
	if repeats {
		table = ymxs.Repeating(rows, repeat)
	}
	tune := ymxs.Tune{Title: song.Name, Composer: song.Author, Writer: writer,
		Rate: song.PlayerHz, Table: table}
	return Reading{Tune: tune,
		Said: Said{Dropped: r.dropped, Preempted: r.preempted, CutAtRepeat: r.cutAtRepeat}}
}

func (r *reader) run() ([]map[ymxs.Register]int, []map[ymxs.Timer]ymxs.Effect) {
	frames := r.song.Frames
	var registers []map[ymxs.Register]int
	var effects []map[ymxs.Timer]ymxs.Effect
	var wrote [14]int
	for i := range wrote {
		wrote[i] = -1
	}
	running := [2]Slot{Empty, Empty}
	var runs [2]ymxs.Source
	prescalerNow := [2]ymxs.Prescaler{ymxs.By4, ymxs.By4}
	countNow := [2]int{0, 0}
	drumEnd := [2]int{-1, -1}
	stopAtRepeat := [2]bool{false, false}
	lastKind := [2]int{None, None}
	var lastTarget [2]ymxs.Register
	lastRan := [2]bool{false, false}
	for f := 0; f < frames; f++ {
		keyframe := f == r.repeat
		if keyframe {
			for i := range wrote {
				wrote[i] = -1
			}
			lastKind = [2]int{None, None}
			lastRan = [2]bool{false, false}
			for i := 0; i < 2; i++ {
				stopAtRepeat[i] = running[i].On()
			}
		}
		reg := r.registers(f)
		slot := Slots(r.song, f)
		var source [2]ymxs.Source
		// The recordings first: one keeps a square wave off its voice.
		for i := 0; i < 2; i++ {
			if slot[i].On() && slot[i].Kind == Recording {
				source[i] = r.source(slot[i])
				if source[i] == nil {
					slot[i] = Empty
				}
			}
		}
		for i := 0; i < 2; i++ {
			if !slot[i].On() || slot[i].Kind == Recording {
				continue
			}
			other := 1 - i
			drumStarts := slot[other].On() && slot[other].Kind == Recording &&
				slot[other].Voice == slot[i].Voice
			replaced := slot[other].On() && !drumStarts
			drumRuns := running[other].Kind == Recording &&
				running[other].Voice == slot[i].Voice && f < drumEnd[other] &&
				!keyframe && !replaced
			if slot[i].Kind == Square && (drumRuns || drumStarts) {
				slot[i] = Empty
				r.preempted++
				continue
			}
			source[i] = r.source(slot[i])
			if source[i] == nil {
				slot[i] = Empty
			}
		}
		here := map[ymxs.Timer]ymxs.Effect{}
		owned := 0
		for i := 0; i < 2; i++ {
			drum := running[i].Kind == Recording
			if !slot[i].On() {
				if keyframe && drum && f < drumEnd[i] {
					r.cutAtRepeat++
				}
				if drum && f < drumEnd[i] && !keyframe {
					// the recording plays on: the dump marks only its start
				} else if running[i].On() || keyframe {
					here[timerOf[i]] = ymxs.Stop{}
					running[i] = Empty
					runs[i] = nil
				}
			} else {
				sounds := source[i]
				starting := keyframe || slot[i].Kind == Recording ||
					running[i].Kind != slot[i].Kind ||
					running[i].Target != slot[i].Target ||
					runs[i] == nil || !ymxs.SourceEqual(runs[i], sounds)
				if starting {
					// A stopped timer begins a whole period either way,
					// and a running one loads the new count at its next
					// zero, so the timer's reset is set where the timer
					// is stopped. A square wave replacing a square wave
					// on the same register has the row count of the one
					// before it, so the place stands and the wave keeps
					// its phase.
					stopped := keyframe || !running[i].On() ||
						running[i].Kind == Recording && f >= drumEnd[i]
					unmoved := !keyframe && slot[i].Kind == Square &&
						lastKind[i] == Square && lastRan[i] &&
						lastTarget[i] == slot[i].Target
					here[timerOf[i]] = ymxs.Start{
						Target: ymxs.Setting(slot[i].Target), Source: sounds,
						Prescaler: slot[i].Prescaler, Count: slot[i].Count,
						TimerReset: stopped, PlaceReset: !unmoved}
					running[i] = slot[i]
					runs[i] = sounds
					lastKind[i] = slot[i].Kind
					lastTarget[i] = slot[i].Target
					lastRan[i] = true
					if slot[i].Kind == Recording {
						drumEnd[i] = f + ymxs.Frames(len(ymxs.Values(sounds)),
							slot[i].Prescaler, slot[i].Count, r.song.PlayerHz)
					}
				} else if slot[i].Prescaler != prescalerNow[i] ||
					slot[i].Count != countNow[i] {
					here[timerOf[i]] = ymxs.Retune{Prescaler: slot[i].Prescaler,
						Count: slot[i].Count}
				}
				prescalerNow[i] = slot[i].Prescaler
				countNow[i] = slot[i].Count
			}
			if running[i].Kind == Square || running[i].Kind == Recording {
				owned |= 1 << ymxs.Number(running[i].Target)
			}
			if running[i].Kind == Recording {
				reg[7] |= 0x09 << running[i].Voice
			}
		}
		sets := map[ymxs.Register]int{}
		for c := 0; c < 13; c++ {
			if owned&(1<<c) != 0 {
				wrote[c] = -1 // the effect's register, and no row's
			} else if reg[c] != wrote[c] {
				sets[ymxs.Registers[c]] = reg[c]
				wrote[c] = reg[c]
			}
		}
		if reg[13] >= 0 {
			sets[ymxs.R13] = reg[13] // a write to it restarts the envelope
		}
		registers = append(registers, sets)
		effects = append(effects, here)
	}
	// The row the tune repeats to stops an effect that ran up to it or
	// runs into the wrap. One that did neither is left alone.
	if r.repeat < frames {
		for i := 0; i < 2; i++ {
			if _, stops := effects[r.repeat][timerOf[i]].(ymxs.Stop); stops &&
				!stopAtRepeat[i] && !running[i].On() {
				delete(effects[r.repeat], timerOf[i])
			}
		}
	}
	return registers, effects
}

// registers is R0 to R13 of one frame with the dump's flag bits off, and
// -1 for R13 where the dump does not write it.
func (r *reader) registers(frame int) [14]int {
	values := r.song.Values
	var out [14]int
	for i := 0; i < 14; i++ {
		out[i] = int(values[i][frame]) & fits[i]
	}
	if values[13][frame] == 0xFF {
		out[13] = -1
	}
	return out
}

// source is the source a slot sounds, built on first use, or nil where the
// dump marks a sound this does not read.
func (r *reader) source(slot Slot) ymxs.Source {
	data := slot.Data & 15
	if slot.Kind == Recording {
		data = slot.Data & 31
	}
	if slot.Kind == Sinus {
		r.dropped++
		return nil
	}
	if slot.Kind == Recording && data >= len(r.samples) {
		r.dropped++
		return nil
	}
	key := slot.Kind<<8 | data
	if found, known := r.known[key]; known {
		return found
	}
	built := r.build(slot.Kind, data)
	r.known[key] = built
	return built
}

func (r *reader) build(kind, data int) ymxs.Source {
	switch kind {
	case Square:
		// The level then the silence. The row that starts the wave sets
		// no level, so the voice keeps the value the last row set for
		// one timer period, and the first tick opens the loud half.
		return ymxs.RepeatingSource("square "+strconv.Itoa(data), []int{data, 0}, 0)
	case Buzzer:
		return ymxs.RepeatingSource("buzzer "+strconv.Itoa(data), []int{data}, 0)
	default:
		rows := make([]int, 0, len(r.samples[data])+1)
		for _, one := range r.samples[data] {
			rows = append(rows, int(one))
		}
		rows = append(rows, park)
		return ymxs.OnceSource("recording "+strconv.Itoa(data), rows)
	}
}
