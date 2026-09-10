package ym

import "github.com/odipar/ymxs/go/ymxs"

// Slot is one of the two effect slots in a dump's frame, read as the sound
// it produces: a kind, the voice it sounds on, the register its ticks
// write, the value its source is built from, and the timer's rate.
//
// YM6 files each slot's kind in bits 7 and 6 of the register it is filed
// in. YM5 defines no kind bits: its first slot is a square wave and its
// second a recording. A slot whose prescaler or count is 0 is one no
// player runs.
type Slot struct {
	Kind      int            // None, Square, Recording, Sinus or Buzzer
	Voice     int            // 0, 1 or 2, the voice it sounds on
	Target    ymxs.Register  // the register its ticks write
	Data      int            // a level, or a sample number, or a shape
	Prescaler ymxs.Prescaler // the timer's first divisor
	Count     int            // the timer's second divisor
}

// None is a slot no player runs.
const None = 0

// Square is a square wave on a volume register: a level and a silence at
// the timer's rate, known as a SID voice.
const Square = 1

// Recording is a recording through a volume register, known as a
// digidrum.
const Recording = 2

// Sinus is a shape this reader cannot read: the player it was written for
// runs an empty handler for it.
const Sinus = 3

// Buzzer is the envelope restarted at the timer's rate, known as a sync
// buzzer.
const Buzzer = 4

// Empty is a slot no player runs.
var Empty = Slot{Kind: None, Voice: 0, Target: ymxs.R0, Data: 0,
	Prescaler: ymxs.By4, Count: 1}

// On is whether a player runs this slot.
func (s Slot) On() bool {
	return s.Kind != None
}

// filed is the registers a slot is filed in: its code, its prescaler and
// its count.
var filed = [2][3]int{{1, 6, 14}, {3, 8, 15}}

// Slots is the two slots of one frame.
func Slots(song Song, frame int) [2]Slot {
	ym6 := song.Format == "YM6!"
	r := song.Values
	var out [2]Slot
	for slot := 0; slot < 2; slot++ {
		code := int(r[filed[slot][0]][frame]) & 0xF0
		voice := ((code >> 4) & 3) - 1
		selects := int(r[filed[slot][1]][frame]) >> 5
		count := int(r[filed[slot][2]][frame])
		if voice < 0 || selects == 0 || count == 0 {
			out[slot] = Empty
			continue
		}
		kind := Recording
		if ym6 {
			kind = (code >> 6) + 1
		} else if slot == 0 {
			kind = Square
		}
		target := ymxs.R13
		if kind != Buzzer {
			target = ymxs.Registers[8+voice]
		}
		out[slot] = Slot{Kind: kind, Voice: voice, Target: target,
			Data:      int(r[8+voice][frame]) & 0x1F,
			Prescaler: ymxs.Prescalers[selects-1], Count: count}
	}
	return out
}
