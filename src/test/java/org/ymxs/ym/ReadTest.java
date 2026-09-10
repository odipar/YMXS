package org.ymxs.ym;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Target;
import org.ymxs.Text;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

/**
 * The example against a dump built here, so that what it reads is stated
 * in the test rather than taken from a file.
 *
 * <p>A dump files its two effect slots in the bits the chip does not use:
 * slot 0's kind and voice in R1's top four, its prescaler in R6's top
 * three and its count in R14; slot 1's in R3, R8 and R15. The builder
 * below writes those, so the test states what a frame holds and reads
 * back what it sounds.
 */
final class ReadTest {

    /** Sixteen registers a frame, and the header in front of them. */
    private static final class Dumped {

        private final byte[][] frames;
        private final List<byte[]> samples;
        private final int loop;

        Dumped(int frames, int loop, List<byte[]> samples) {
            this.frames = new byte[frames][16];
            this.loop = loop;
            this.samples = samples;
            for (byte[] one : this.frames) {
                one[13] = (byte) 0xFF;              // R13 unwritten
            }
        }

        Dumped set(int frame, int register, int value) {
            frames[frame][register] = (byte) value;
            return this;
        }

        /** Slot 0: a kind and a voice in R1, a prescaler in R6, a count in
         *  R14. Kind 1 is a square wave, 2 a recording, 4 a buzzer. */
        Dumped slot0(int frame, int kind, int voice, int select, int count) {
            set(frame, 1, (kind - 1) << 6 | (voice + 1) << 4 | frames[frame][1] & 0x0F);
            set(frame, 6, select << 5 | frames[frame][6] & 0x1F);
            return set(frame, 14, count);
        }

        /** Slot 1: the same in R3, R8 and R15. */
        Dumped slot1(int frame, int kind, int voice, int select, int count) {
            set(frame, 3, (kind - 1) << 6 | (voice + 1) << 4 | frames[frame][3] & 0x0F);
            set(frame, 8, select << 5 | frames[frame][8] & 0x1F);
            return set(frame, 15, count);
        }

        byte[] bytes() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes("YM6!LeOnArD!".getBytes(StandardCharsets.US_ASCII));
            long32(out, frames.length);
            long32(out, 0);                          // one record a frame
            word(out, samples.size());
            long32(out, 2000000);
            word(out, 50);
            long32(out, loop);
            word(out, 0);
            for (byte[] one : samples) {
                long32(out, one.length);
                out.writeBytes(one);
            }
            text(out, "a tune");
            text(out, "a composer");
            text(out, "");
            for (byte[] one : frames) {
                out.writeBytes(one);
            }
            out.writeBytes("End!".getBytes(StandardCharsets.US_ASCII));
            return out.toByteArray();
        }

        private static void word(ByteArrayOutputStream out, int value) {
            out.write(value >> 8);
            out.write(value);
        }

        private static void long32(ByteArrayOutputStream out, long value) {
            word(out, (int) (value >> 16));
            word(out, (int) value);
        }

        private static void text(ByteArrayOutputStream out, String said) {
            out.writeBytes(said.getBytes(StandardCharsets.ISO_8859_1));
            out.write(0);
        }
    }

    private static Tune read(Dumped dumped) {
        return Read.of(Dump.read(dumped.bytes()), "a test", OptionalInt.of(0)).tune();
    }

    @Test
    void theHeaderReachesTheTune() {
        Tune tune = read(new Dumped(2, 0, List.of()));
        assertEquals("a tune", tune.title());
        assertEquals("a composer", tune.composer());
        assertEquals("a test", tune.writer());
        assertEquals(50, tune.rate());
        assertEquals(2, Tunes.size(tune.table()));
        assertEquals(OptionalInt.of(0), tune.table().repeat());
    }

    @Test
    void aRegisterIsSetWhereTheDumpMovesIt() {
        Dumped dumped = new Dumped(4, 0, List.of())
                .set(0, 0, 200).set(1, 0, 200).set(2, 0, 201).set(3, 0, 201);
        Tune tune = read(dumped);
        assertEquals(200, Tunes.rows(tune).get(0).registers().get(Register.R0),
                "the row the tune repeats to sets every register");
        assertTrue(!Tunes.rows(tune).get(1).registers().containsKey(Register.R0),
                "a row the dump does not move sets nothing");
        assertEquals(201, Tunes.rows(tune).get(2).registers().get(Register.R0));
        assertTrue(!Tunes.rows(tune).get(3).registers().containsKey(Register.R0));
    }

    @Test
    void aSquareWaveBecomesASourceOfALevelAndASilence() {
        Dumped dumped = new Dumped(8, 0, List.of()).set(2, 8, 12).set(3, 8, 12)
                .set(4, 8, 12).set(5, 8, 12).set(6, 8, 9).set(7, 8, 9);
        for (int f = 2; f <= 5; f++) {
            dumped.slot0(f, 1, 0, 1, 100);
        }
        Tune tune = read(dumped);

        assertEquals(1, Tunes.sources(tune).size());
        Single source = assertInstanceOf(Single.class, Tunes.sources(tune).get(0));
        assertEquals(List.of(12, 0), Tunes.values(source));
        assertEquals(OptionalInt.of(0), Tunes.table(source).repeat());

        assertEquals(java.util.EnumSet.of(Timer.A), Tunes.timers(tune));
        Start start = assertInstanceOf(Start.class,
                Tunes.rows(tune).get(2).effects().get(Timer.A));
        assertEquals(Tunes.setting(Register.R8), start.target());
        assertEquals(Prescaler.BY_4, start.prescaler());
        assertEquals(100, start.count());
        assertTrue(start.timerReset(), "the timer was stopped, so it takes a whole period");
        assertTrue(start.placeReset(), "no wave ran before it, so the place goes to row 0");

        for (int f = 3; f <= 5; f++) {
            assertTrue(Tunes.rows(tune).get(f).effects().isEmpty(),
                    "row " + f + " states nothing: the wave runs on unchanged");
        }
        assertInstanceOf(Stop.class, Tunes.rows(tune).get(6).effects().get(Timer.A));
        assertTrue(!Tunes.rows(tune).get(2).registers().containsKey(Register.R8),
                "the wave owns the volume, so no row sets it while it runs");
        assertEquals(9, Tunes.rows(tune).get(6).registers().get(Register.R8),
                "the row that stops it takes the register back, to the value the dump"
                        + " holds there");
    }

    @Test
    void aRecordingBecomesASourceOfItsLevelsAndAClosingRow() {
        byte[] sample = {(byte) 0x00, (byte) 0x40, (byte) 0xF0, (byte) 0x80};
        Dumped dumped = new Dumped(6, 0, List.of(sample));
        dumped.slot1(1, 2, 1, 1, 100).set(1, 9, 0);
        Tune tune = read(dumped);

        assertEquals(1, Tunes.sources(tune).size());
        Single source = assertInstanceOf(Single.class, Tunes.sources(tune).get(0));
        assertEquals(List.of(0, 4, 15, 8, 13), Tunes.values(source),
                "the sample's high four bits a row, and a closing row at mid-scale");
        assertEquals(OptionalInt.empty(), Tunes.table(source).repeat(), "a recording plays once");

        Start start = assertInstanceOf(Start.class,
                Tunes.rows(tune).get(1).effects().get(Timer.D));
        assertEquals(Tunes.setting(Register.R9), start.target());
        assertEquals(0b010010, Tunes.rows(tune).get(1).registers().get(Register.R7),
                "a recording silences its own voice's tone and noise while it runs, which"
                        + " for voice B is bits 1 and 4");
    }

    @Test
    void aTuneReadFromADumpWritesAsTheTextForm() {
        Dumped dumped = new Dumped(4, 0, List.of()).set(0, 0, 100).set(2, 0, 101);
        dumped.slot0(1, 1, 0, 2, 50).set(1, 8, 9);
        Multi multi = Tunes.multi(read(dumped));
        String text = Text.write(multi);
        assertEquals(multi, Text.read(text));
        assertEquals(text, Text.write(Text.read(text)));
    }

    @Test
    void anArchiveIsSaidToBeOneRatherThanRead() {
        byte[] archive = {0x22, 0x2D, '-', 'l', 'h', '5', '-', 0x00};
        Dump.Unreadable no = assertThrows(Dump.Unreadable.class, () -> Dump.read(archive));
        assertTrue(String.valueOf(no.getMessage()).contains("unpack it first"),
                String.valueOf(no.getMessage()));
    }

    @Test
    void aFileThatIsNotADumpIsSaidToBeNone() {
        Dump.Unreadable no = assertThrows(Dump.Unreadable.class,
                () -> Dump.read("not a dump at all".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(String.valueOf(no.getMessage()).contains("not a YM5! or YM6! dump"),
                String.valueOf(no.getMessage()));
    }
}
