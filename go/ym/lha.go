package ym

import (
	"errors"
	"fmt"
)

// Unpacks the LHA archives that distributed .ym files come wrapped in,
// entirely in memory.
//
// A port of the ST-Sound library's LZH depacker by Arnaud Carré, which is
// based on LZH code by Haruhiko Okumura (1991) and Kerwin F. Medina
// (1996). It reads a level-0 header, the kind in every distributed YM
// archive, verifies its checksum, and inflates the -lh5- method; an -lh0-
// member is stored uncompressed and copied out.
//
// This is plumbing and no part of the format. It is here so that a .ym
// reads as distributed, without a separate unpacking step.
//
// YM archives contain exactly one member, so Unpack returns the first
// member's data and ignores anything after it.

const (
	bufsize   = 4096
	bitbufsiz = 16
	dicbit    = 13 // -lh5-
	dicsiz    = 1 << dicbit
	maxmatch  = 256
	threshold = 3
	nc        = 255 + maxmatch + 2 - threshold
	cbit      = 9
	np        = dicbit + 1
	nt        = 16 + 3
	pbit      = 4
	tbit      = 5
	npt       = nt // the larger of nt and np
)

type lha struct {
	source     []byte
	sourceAt   int
	sourceLeft int

	bitbuf    int
	subbitbuf int
	bitcount  int
	pending   int
	pendingAt int
	window    [bufsize]byte

	left      [2*nc - 1]int
	right     [2*nc - 1]int
	cLen      [nc]byte
	ptLen     [npt]byte
	blocksize int
	cTable    [4096]int
	ptTable   [256]int

	matchLeft int
	matchAt   int
}

// IsArchive is whether this is an LHA archive: any -lh?- method at offset
// 2.
func IsArchive(data []byte) bool {
	return len(data) >= 22 && data[0] != 0 &&
		data[2] == '-' && data[3] == 'l' && data[4] == 'h' && data[6] == '-'
}

// Unpack unpacks the archive's first member. The error names the reason
// where the header is damaged or the method is one no YM archive uses.
func Unpack(archive []byte) ([]byte, error) {
	if !IsArchive(archive) {
		return nil, errors.New("not an LHA archive")
	}
	headerSize := int(archive[0])
	dataAt := headerSize + 2
	if dataAt > len(archive) {
		return nil, errors.New("LHA header extends beyond the file")
	}
	level := int(archive[20])
	if level != 0 {
		return nil, fmt.Errorf("LHA header level %d; YM archives use level 0", level)
	}
	sum := 0
	for i := 2; i < dataAt; i++ {
		sum += int(archive[i])
	}
	if sum&0xFF != int(archive[1]) {
		return nil, errors.New("LHA header checksum mismatch")
	}

	compressedSize := little32(archive, 7)
	originalSize := little32(archive, 11)
	if compressedSize < 0 || compressedSize > len(archive)-dataAt {
		return nil, errors.New("LHA member is truncated")
	}
	if originalSize < 0 {
		return nil, errors.New("LHA member claims a negative size")
	}

	method := string(archive[2:7])
	if method == "-lh0-" { // stored, not compressed
		data := make([]byte, originalSize)
		copy(data, archive[dataAt:])
		return data, nil
	}
	if method != "-lh5-" {
		return nil, fmt.Errorf("unsupported LHA method %s", method)
	}
	l := &lha{source: archive, sourceAt: dataAt, sourceLeft: compressedSize}
	return l.inflate(originalSize), nil
}

func little32(data []byte, at int) int {
	return int(int32(uint32(data[at]) | uint32(data[at+1])<<8 |
		uint32(data[at+2])<<16 | uint32(data[at+3])<<24))
}

// -------------------------------------------------------------- inflate

func (l *lha) inflate(originalSize int) []byte {
	result := make([]byte, originalSize)
	slice := make([]byte, dicsiz)
	l.pending = 0
	l.initGetbits()
	l.blocksize = 0
	l.matchLeft = 0

	at := 0
	for at < originalSize {
		n := originalSize - at
		if n > dicsiz {
			n = dicsiz
		}
		l.decode(n, slice)
		copy(result[at:], slice[:n])
		at += n
	}
	return result
}

// -------------------------------------------------------------- bit I/O

func (l *lha) fillbuf(n int) {
	l.bitbuf = (l.bitbuf << n) & 0xFFFF
	for n > l.bitcount {
		n -= l.bitcount
		l.bitbuf |= (l.subbitbuf << n) & 0xFFFF
		if l.pending == 0 {
			l.pendingAt = 0
			l.pending = l.sourceLeft
			if l.pending > bufsize-32 {
				l.pending = bufsize - 32
			}
			if l.pending > 0 {
				copy(l.window[:l.pending], l.source[l.sourceAt:])
				l.sourceAt += l.pending
				l.sourceLeft -= l.pending
			}
		}
		if l.pending > 0 {
			l.pending--
			l.subbitbuf = int(l.window[l.pendingAt])
			l.pendingAt++
		} else {
			l.subbitbuf = 0 // ran dry: the sizes bound the read
		}
		l.bitcount = 8
	}
	l.bitcount -= n
	l.bitbuf |= (l.subbitbuf >> l.bitcount) & 0xFFFF
	l.bitbuf &= 0xFFFF
}

func (l *lha) getbits(n int) int {
	bits := (l.bitbuf >> (bitbufsiz - n)) & 0xFFFF
	l.fillbuf(n)
	return bits
}

func (l *lha) initGetbits() {
	l.bitbuf = 0
	l.subbitbuf = 0
	l.bitcount = 0
	l.fillbuf(bitbufsiz)
}

// ----------------------------------------------- Huffman table build

func (l *lha) makeTable(nchar int, bitlen []byte, tablebits int, table []int) {
	var count [17]int
	var weight [17]int
	var start [18]int

	for i := 0; i < nchar; i++ {
		count[bitlen[i]]++
	}
	start[1] = 0
	for i := 1; i <= 16; i++ {
		start[i+1] = start[i] + (count[i] << (16 - i))
	}

	jutbits := 16 - tablebits
	for i := 1; i <= tablebits; i++ {
		start[i] >>= jutbits
		weight[i] = 1 << (tablebits - i)
	}
	for i := tablebits + 1; i <= 16; i++ {
		weight[i] = 1 << (16 - i)
	}

	at := (start[tablebits+1] >> jutbits) & 0xFFFF
	end := 1 << tablebits
	for at < end {
		table[at] = 0
		at++
	}

	avail := nchar
	mask := 1 << (15 - tablebits)
	for ch := 0; ch < nchar; ch++ {
		length := int(bitlen[ch])
		if length == 0 {
			continue
		}
		nextcode := start[length] + weight[length]
		if length <= tablebits {
			for i := start[length]; i < nextcode; i++ {
				table[i] = ch
			}
		} else {
			// The code is longer than the table indexes: the tail bits
			// walk a tree spliced into left/right. Which array a node
			// lives in is part of the walk, so track it explicitly.
			code := start[length]
			array := 0 // 0 table, 1 left, 2 right
			index := code >> jutbits
			for bits := length - tablebits; bits != 0; bits-- {
				var node int
				switch array {
				case 0:
					node = table[index]
				case 1:
					node = l.left[index]
				default:
					node = l.right[index]
				}
				if node == 0 {
					l.left[avail] = 0
					l.right[avail] = 0
					node = avail
					avail++
					switch array {
					case 0:
						table[index] = node
					case 1:
						l.left[index] = node
					default:
						l.right[index] = node
					}
				}
				if code&mask != 0 {
					array = 2
				} else {
					array = 1
				}
				index = node
				code <<= 1
			}
			switch array {
			case 0:
				table[index] = ch
			case 1:
				l.left[index] = ch
			default:
				l.right[index] = ch
			}
		}
		start[length] = nextcode
	}
}

// ------------------------------------------------- Huffman decoding

func (l *lha) readPtLen(nn, nbit, special int) {
	n := l.getbits(nbit)
	if n == 0 {
		c := l.getbits(nbit)
		for i := 0; i < nn; i++ {
			l.ptLen[i] = 0
		}
		for i := 0; i < 256; i++ {
			l.ptTable[i] = c
		}
		return
	}
	i := 0
	for i < n {
		c := l.bitbuf >> (bitbufsiz - 3)
		if c == 7 {
			mask := 1 << (bitbufsiz - 4)
			for mask&l.bitbuf != 0 {
				mask >>= 1
				c++
			}
		}
		if c < 7 {
			l.fillbuf(3)
		} else {
			l.fillbuf(c - 3)
		}
		l.ptLen[i] = byte(c)
		i++
		if i == special {
			skip := l.getbits(2)
			for skip > 0 {
				skip--
				l.ptLen[i] = 0
				i++
			}
		}
	}
	for i < nn {
		l.ptLen[i] = 0
		i++
	}
	l.makeTable(nn, l.ptLen[:], 8, l.ptTable[:])
}

func (l *lha) readCLen() {
	n := l.getbits(cbit)
	if n == 0 {
		c := l.getbits(cbit)
		for i := 0; i < nc; i++ {
			l.cLen[i] = 0
		}
		for i := 0; i < 4096; i++ {
			l.cTable[i] = c
		}
		return
	}
	i := 0
	for i < n {
		c := l.ptTable[(l.bitbuf>>(bitbufsiz-8))&0xFF]
		if c >= nt {
			mask := 1 << (bitbufsiz - 9)
			for c >= nt {
				if l.bitbuf&mask != 0 {
					c = l.right[c]
				} else {
					c = l.left[c]
				}
				mask >>= 1
			}
		}
		l.fillbuf(int(l.ptLen[c]))
		if c <= 2 {
			switch c {
			case 0:
				c = 1
			case 1:
				c = l.getbits(4) + 3
			default:
				c = l.getbits(cbit) + 20
			}
			for c > 0 {
				c--
				l.cLen[i] = 0
				i++
			}
		} else {
			l.cLen[i] = byte(c - 2)
			i++
		}
	}
	for i < nc {
		l.cLen[i] = 0
		i++
	}
	l.makeTable(nc, l.cLen[:], 12, l.cTable[:])
}

func (l *lha) decodeC() int {
	if l.blocksize == 0 {
		l.blocksize = l.getbits(16)
		l.readPtLen(nt, tbit, 3)
		l.readCLen()
		l.readPtLen(np, pbit, -1)
	}
	l.blocksize--
	j := l.cTable[(l.bitbuf>>(bitbufsiz-12))&0xFFF]
	if j >= nc {
		mask := 1 << (bitbufsiz - 13)
		for j >= nc {
			if l.bitbuf&mask != 0 {
				j = l.right[j]
			} else {
				j = l.left[j]
			}
			mask >>= 1
		}
	}
	l.fillbuf(int(l.cLen[j]))
	return j
}

func (l *lha) decodeP() int {
	j := l.ptTable[(l.bitbuf>>(bitbufsiz-8))&0xFF]
	if j >= np {
		mask := 1 << (bitbufsiz - 9)
		for j >= np {
			if l.bitbuf&mask != 0 {
				j = l.right[j]
			} else {
				j = l.left[j]
			}
			mask >>= 1
		}
	}
	l.fillbuf(int(l.ptLen[j]))
	if j != 0 {
		j = (1 << (j - 1)) + l.getbits(j-1)
	}
	return j
}

// decode is one dictionary-sized slice of output; matches may carry over.
func (l *lha) decode(count int, buffer []byte) {
	at := 0
	for l.matchLeft > 0 {
		l.matchLeft--
		buffer[at] = buffer[l.matchAt]
		l.matchAt = (l.matchAt + 1) & (dicsiz - 1)
		at++
		if at == count {
			return
		}
	}
	l.matchLeft = 0
	for {
		c := l.decodeC()
		if c <= 255 {
			buffer[at] = byte(c)
			at++
			if at == count {
				return
			}
		} else {
			l.matchLeft = c - (255 + 1 - threshold)
			l.matchAt = (at - l.decodeP() - 1) & (dicsiz - 1)
			for l.matchLeft > 0 {
				l.matchLeft--
				buffer[at] = buffer[l.matchAt]
				l.matchAt = (l.matchAt + 1) & (dicsiz - 1)
				at++
				if at == count {
					return
				}
			}
		}
	}
}
