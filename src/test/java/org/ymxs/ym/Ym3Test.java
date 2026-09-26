package org.ymxs.ym;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Tune;

/**
 * A YM3 dump built here, so that its bytes are in the test rather than
 * in a file: the format, fourteen vectors of one register each, and under
 * YM3b a long after them (ym.md 2.6).
 */
final class Ym3Test {

    /** A dump of {@code frames} frames, register r of frame f being
     *  {@code r * 16 + f} with the bits outside the register still in it,
     *  and under YM3b {@code loop} after the vectors. */
    private static byte[] ym3(String format, int frames, int loop) {
        byte[] data = new byte[4 + 14 * frames + (format.equals("YM3b") ? 4 : 0)];
        System.arraycopy(format.getBytes(StandardCharsets.US_ASCII), 0, data, 0, 4);
        for (int r = 0; r < 14; r++) {
            for (int f = 0; f < frames; f++) {
                data[4 + r * frames + f] = (byte) (r * 16 + f);
            }
        }
        if (format.equals("YM3b")) {
            int at = data.length - 4;
            data[at] = (byte) (loop >> 24);
            data[at + 1] = (byte) (loop >> 16);
            data[at + 2] = (byte) (loop >> 8);
            data[at + 3] = (byte) loop;
        }
        return data;
    }

    @Test
    void aYm3DumpIsFourteenVectorsAndNoHeader() {
        Dump.Song song = Dump.read(ym3("YM3!", 8, 0));
        assertEquals("YM3!", song.format());
        assertEquals(8, song.frames());
        assertEquals(50, song.playerHz(), "a YM3 dump leaves the rate unsaid");
        assertEquals(0, song.loopFrame());
        assertEquals("", song.name());
        assertEquals("", song.author());
        assertEquals(0, song.drums().length);
        for (int r = 0; r < 14; r++) {
            for (int f = 0; f < 8; f++) {
                assertEquals((byte) (r * 16 + f), song.registers()[r][f], "R" + r + " frame " + f);
            }
        }
        for (int r = 14; r < 16; r++) {
            for (int f = 0; f < 8; f++) {
                assertEquals(0, song.registers()[r][f], "R" + r + " stands outside YM3");
            }
        }
    }

    @Test
    void ym3bNamesTheFrameTheDumpRepeatsTo() {
        Dump.Song song = Dump.read(ym3("YM3b", 8, 3));
        assertEquals("YM3b", song.format());
        assertEquals(8, song.frames());
        assertEquals(3, song.loopFrame());
        assertEquals(OptionalInt.of(3), Read.of(song, "a test").table().repeat());
    }

    @Test
    void aLoopFramePastTheLastRowRepeatsToRowZero() {
        assertEquals(OptionalInt.of(0), Read.of(Dump.read(ym3("YM3b", 8, 8)), "a test")
                .table().repeat(), "the rule of ym.md 8.3, as for a YM5 dump");
    }

    @Test
    void frameBytesThatAreNotAWholeFrameAreAnError() {
        byte[] data = java.util.Arrays.copyOf(ym3("YM3!", 8, 0), 4 + 14 * 8 - 3);
        Dump.Unreadable no = assertThrows(Dump.Unreadable.class, () -> Dump.read(data));
        assertEquals("the frames of a YM3! dump are 109 bytes, and a frame is 14 bytes",
                no.getMessage());
        Dump.Unreadable none = assertThrows(Dump.Unreadable.class,
                () -> Dump.read("YM3!".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("the frames of a YM3! dump are 0 bytes, and a frame is 14 bytes",
                none.getMessage());
    }

    @Test
    void aYm3DumpRunsNoEffect() {
        Tune tune = Read.of(Dump.read(ym3("YM3!", 8, 0)), "a test");
        assertEquals(50, tune.rate());
        assertTrue(Tunes.sources(tune).isEmpty(), "R14 and R15 are zero, so every slot is off");
        assertTrue(Tunes.timers(tune).isEmpty());
        assertEquals(8, Tunes.size(tune.table()));
        assertEquals(0, Tunes.rows(tune).get(0).registers().get(Register.R0),
                "R0 of frame 0 is the first byte of the first vector");
        assertEquals(1, Tunes.rows(tune).get(1).registers().get(Register.R0));
    }
}
