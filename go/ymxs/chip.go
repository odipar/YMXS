package ymxs

import "fmt"

// The figures of the two chips. Every value here is the YM2149's or the
// MC68901's, and none of it belongs to a form.

// Clock is the MC68901's clock, in ticks a second.
const Clock = 2457600

// MostCount is the largest value a timer's data register reads. The
// register is a byte and every value of it is a count, 0 among them.
const MostCount = 255

// ZeroCounts is the ticks a count of 0 counts. A timer counts the
// register's value down through zero, so 0 counts a whole byte round.
const ZeroCounts = 256

// Ticks is the ticks count counts: the value itself, and ZeroCounts where
// it is 0.
func Ticks(count int) int {
	if count == 0 {
		return ZeroCounts
	}
	return count
}

// CpuClock is a 68000 on an Atari ST, in cycles a second.
const CpuClock = 8_000_000

// TickCycles is the cycles a 68000 spends entering an interrupt and
// leaving it: 44 and 20 from the manual, with no cycle of a handler
// between them.
const TickCycles = 64

// MostTicks is the ticks a second past which a 68000 spends every cycle it
// has entering interrupts and leaving them, with none for a handler and
// none for the frame. A tune below this may still be more than a machine
// plays, which a player measures of itself.
const MostTicks = CpuClock / TickCycles

// Most is the largest value that fits the register. The smallest is 0.
//
// A voice's tone period and the envelope period each run over two
// registers, one the divider's low bits and one its high, because a row
// may set one and not the other. A volume is five bits: four a level, and
// one that reads the level from the envelope generator instead. In R7,
// bits 7 and 6 are the host's I/O port directions, which no tune moves, so
// a row sets six bits.
func Most(register Register) int {
	switch register {
	case R0, R2, R4, R11, R12:
		return 255
	case R1, R3, R5, R13:
		return 15
	case R6, R8, R9, R10:
		return 31
	case R7:
		return 63
	}
	panic(fmt.Sprintf("no register %d", int(register)))
}

// Reaches is what the register reaches, as text.
func Reaches(register Register) string {
	switch register {
	case R0, R1:
		return "voice A's tone period"
	case R2, R3:
		return "voice B's tone period"
	case R4, R5:
		return "voice C's tone period"
	case R6:
		return "the noise period"
	case R7:
		return "mixing"
	case R8:
		return "voice A's volume"
	case R9:
		return "voice B's volume"
	case R10:
		return "voice C's volume"
	case R11, R12:
		return "the envelope period"
	case R13:
		return "the envelope shape"
	}
	panic(fmt.Sprintf("no register %d", int(register)))
}

// Number is the register number, 0 to 13, as the chip numbers them.
func Number(register Register) int {
	return int(register)
}

// RegisterAt is the register numbered at. The error names what a tune
// reaches where the chip defines no such register.
func RegisterAt(at int) (Register, error) {
	if at < 0 || at >= len(Registers) {
		return 0, fmt.Errorf("no register %d: a tune reaches R0 to R13", at)
	}
	return Registers[at], nil
}

// Divides is what the prescaler divides the clock by.
func Divides(prescaler Prescaler) int {
	switch prescaler {
	case By4:
		return 4
	case By10:
		return 10
	case By16:
		return 16
	case By50:
		return 50
	case By64:
		return 64
	case By100:
		return 100
	case By200:
		return 200
	}
	panic(fmt.Sprintf("no prescaler %d", int(prescaler)))
}

// PrescalerBy is the prescaler that divides by that number.
func PrescalerBy(by int) (Prescaler, error) {
	for _, one := range Prescalers {
		if Divides(one) == by {
			return one, nil
		}
	}
	return 0, fmt.Errorf("no prescaler divides by %d: a timer's are 4, 10, 16, 50,"+
		" 64, 100 and 200", by)
}

// Rate is the rate a timer runs at with this prescaler and this count, in
// ticks a second.
func Rate(prescaler Prescaler, count int) int {
	return Clock / (Divides(prescaler) * Ticks(count))
}

// Frames is the frames a source of that many rows runs for at this rate,
// where the player is called that many times a second: rounded up, with a
// sixteenth of a frame for a start that falls inside the frame it begins
// in.
//
// This is a reckoning and not a reading. A tune marks the row a source
// starts on and no row for its end, so the duration of one that plays once
// follows from its rate, and any reading resting on that is reported as
// such.
func Frames(rows int, prescaler Prescaler, count, called int) int {
	divisor := int64(Divides(prescaler)) * int64(Ticks(count))
	scaled := int64(rows)*divisor*int64(called) + Clock/16
	return int((scaled + Clock - 1) / Clock)
}
