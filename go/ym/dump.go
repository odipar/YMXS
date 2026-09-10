// Package ym reads a YM5!/YM6! register dump into the tune data
// structure: one row a frame, and a source for each distinct sound the
// dump's effect slots produce.
//
// The layout is a fixed header, extra data, the digidrum samples, three
// strings ended by a zero, and then the frames: either sixteen vectors of
// one register each, or one record of sixteen bytes a frame. Both come out
// as sixteen register vectors.
//
// A distributed .ym is usually an archive containing the dump, and Unpack
// unpacks one, so both forms read here.
package ym

import (
	"errors"
	"fmt"
)

// Registers is the registers in the file, R0 to R15.
const Registers = 16

// DrumsAre4Bit is attribute bit 2: the samples are one four-bit value a
// byte.
const DrumsAre4Bit = 4

// Song is the header fields, the frames as read, and the samples as
// stored.
//
// Values[r][frame] is R(r) as the file has it, all sixteen: the two I/O
// ports are where this format files an effect's timer count.
type Song struct {
	Format    string   // YM5! or YM6!
	Frames    int      // how many frames the dump runs
	PlayerHz  int      // how often the dump's player was called
	LoopFrame int64    // the frame the dump repeats to
	Attribute int64    // the header's flag bits
	Drums     [][]byte // the digidrum samples as stored
	Name      string   // what the dump calls the tune
	Author    string   // the author the dump records
	Values    [][]byte // the frames, a vector a register
}

// Unreadable is what this reader cannot read.
type Unreadable struct {
	Said string
}

func (u *Unreadable) Error() string {
	return u.Said
}

func unreadable(said string, args ...any) error {
	return &Unreadable{Said: fmt.Sprintf(said, args...)}
}

type dump struct {
	data []byte
	at   int
}

// Read is the song in the data. The error is an Unreadable where it is not
// a YM5! or YM6! dump.
func Read(data []byte) (Song, error) {
	if IsArchive(data) {
		out, err := Unpack(data)
		if err != nil {
			return Song{}, unreadable("this is an archive with a dump inside, and it"+
				" does not unpack: %s", err.Error())
		}
		data = out
	}
	d := &dump{data: data}
	return d.run()
}

func (d *dump) run() (Song, error) {
	format, err := d.ascii(4)
	if err != nil {
		return Song{}, err
	}
	if format != "YM6!" && format != "YM5!" {
		return Song{}, unreadable("not a YM5! or YM6! dump: it opens with %q", format)
	}
	check, err := d.ascii(8)
	if err != nil {
		return Song{}, err
	}
	if check != "LeOnArD!" {
		return Song{}, unreadable("the check string after %s is not there", format)
	}
	frames, err := d.u32()
	if err != nil {
		return Song{}, err
	}
	attributes, err := d.u32()
	if err != nil {
		return Song{}, err
	}
	digidrums, err := d.u16()
	if err != nil {
		return Song{}, err
	}
	if _, err := d.u32(); err != nil { // the master clock, which no one reads
		return Song{}, err
	}
	playerHz, err := d.u16()
	if err != nil {
		return Song{}, err
	}
	loopFrame, err := d.u32()
	if err != nil {
		return Song{}, err
	}
	extra, err := d.u16()
	if err != nil {
		return Song{}, err
	}
	if err := d.skip(extra, "the extra data"); err != nil {
		return Song{}, err
	}
	drums := make([][]byte, digidrums)
	for i := 0; i < digidrums; i++ {
		size, err := d.u32()
		if err != nil {
			return Song{}, err
		}
		if size < 0 || size > int64(len(d.data)-d.at) {
			return Song{}, unreadable("digidrum %d declares %d bytes and the file is"+
				" shorter than that", i, size)
		}
		drums[i] = make([]byte, size)
		copy(drums[i], d.data[d.at:])
		d.at += int(size)
	}
	name, err := d.string()
	if err != nil {
		return Song{}, err
	}
	author, err := d.string()
	if err != nil {
		return Song{}, err
	}
	if _, err := d.string(); err != nil { // the comment, which no one reads
		return Song{}, err
	}
	if frames <= 0 || frames > 0x7FFFFFFF {
		return Song{}, unreadable("a frame count of %d", frames)
	}
	if playerHz <= 0 {
		return Song{}, unreadable("a player frequency of %d Hz", playerHz)
	}
	count := int(frames)
	var values [][]byte
	if attributes&1 != 0 {
		values, err = d.vectors(count)
	} else {
		values, err = d.records(count)
	}
	if err != nil {
		return Song{}, err
	}
	return Song{Format: format, Frames: count, PlayerHz: playerHz, LoopFrame: loopFrame,
		Attribute: attributes, Drums: drums, Name: name, Author: author,
		Values: values}, nil
}

// vectors reads sixteen vectors of one register each.
func (d *dump) vectors(frames int) ([][]byte, error) {
	if err := d.need(int64(frames)*Registers, "the frames"); err != nil {
		return nil, err
	}
	values := make([][]byte, Registers)
	for r := 0; r < Registers; r++ {
		values[r] = make([]byte, frames)
		copy(values[r], d.data[d.at:])
		d.at += frames
	}
	return values, nil
}

// records reads one record of sixteen bytes a frame.
func (d *dump) records(frames int) ([][]byte, error) {
	if err := d.need(int64(frames)*Registers, "the frames"); err != nil {
		return nil, err
	}
	values := make([][]byte, Registers)
	for r := 0; r < Registers; r++ {
		values[r] = make([]byte, frames)
	}
	for frame := 0; frame < frames; frame++ {
		for r := 0; r < Registers; r++ {
			values[r][frame] = d.data[d.at]
			d.at++
		}
	}
	return values, nil
}

func (d *dump) need(bytes int64, what string) error {
	if bytes > int64(len(d.data)-d.at) {
		return unreadable("%s declares %d bytes and %d are left", what, bytes,
			len(d.data)-d.at)
	}
	return nil
}

func (d *dump) skip(bytes int, what string) error {
	if bytes < 0 {
		return unreadable("a size of %d for %s", bytes, what)
	}
	if err := d.need(int64(bytes), what); err != nil {
		return err
	}
	d.at += bytes
	return nil
}

func (d *dump) ascii(bytes int) (string, error) {
	if err := d.need(int64(bytes), "a header field"); err != nil {
		return "", err
	}
	said := string(d.data[d.at : d.at+bytes])
	d.at += bytes
	return said, nil
}

// string reads a header string, which a zero byte ends. Its bytes are one
// character each, as the dumps have them.
func (d *dump) string() (string, error) {
	end := d.at
	for end < len(d.data) && d.data[end] != 0 {
		end++
	}
	if end == len(d.data) {
		return "", errors.New("a header string with no zero after it")
	}
	runes := make([]rune, end-d.at)
	for i, b := range d.data[d.at:end] {
		runes[i] = rune(b)
	}
	d.at = end + 1
	return string(runes), nil
}

func (d *dump) u16() (int, error) {
	if err := d.need(2, "a header field"); err != nil {
		return 0, err
	}
	value := int(d.data[d.at])<<8 | int(d.data[d.at+1])
	d.at += 2
	return value, nil
}

func (d *dump) u32() (int64, error) {
	high, err := d.u16()
	if err != nil {
		return 0, err
	}
	low, err := d.u16()
	if err != nil {
		return 0, err
	}
	return int64(high)<<16 | int64(low), nil
}
