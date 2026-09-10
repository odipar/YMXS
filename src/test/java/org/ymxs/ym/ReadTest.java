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
 * The example against a dump built here, so that what it reads stands in
 * the test rather than in a file. {@link Dumps} writes the dump.
 */
final class ReadTest {

    private static Tune read(Dumps dumped) {
        return Read.of(Dump.read(dumped.bytes()), "a test", OptionalInt.of(0)).tune();
    }

    @Test
    void theHeaderReachesTheTune() {
        Tune tune = read(new Dumps(2, 0, List.of()));
        assertEquals("a tune", tune.title());
        assertEquals("a composer", tune.composer());
        assertEquals("a test", tune.writer());
        assertEquals(50, tune.rate());
        assertEquals(2, Tunes.size(tune.table()));
        assertEquals(OptionalInt.of(0), tune.table().repeat());
    }

    @Test
    void aRegisterIsSetWhereTheDumpMovesIt() {
        Dumps dumped = new Dumps(4, 0, List.of())
                .set(0, 0, 200).set(1, 0, 200).set(2, 0, 201).set(3, 0, 201);
        Tune tune = read(dumped);
        assertEquals(200, Tunes.rows(tune).get(0).registers().get(Register.R0),
                "the row the tune repeats to sets every register");
        assertTrue(!Tunes.rows(tune).get(1).registers().containsKey(Register.R0),
                "a row the dump does not move sets no register");
        assertEquals(201, Tunes.rows(tune).get(2).registers().get(Register.R0));
        assertTrue(!Tunes.rows(tune).get(3).registers().containsKey(Register.R0));
    }

    @Test
    void aSquareWaveBecomesASourceOfALevelAndASilence() {
        Dumps dumped = new Dumps(8, 0, List.of()).set(2, 8, 12).set(3, 8, 12)
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
        assertTrue(start.timerReset(), "the timer was stopped, so it runs a whole period");
        assertTrue(start.placeReset(), "no wave ran before it, so the place goes to row 0");

        for (int f = 3; f <= 5; f++) {
            assertTrue(Tunes.rows(tune).get(f).effects().isEmpty(),
                    "row " + f + " leaves the timer alone: the wave runs on unchanged");
        }
        assertInstanceOf(Stop.class, Tunes.rows(tune).get(6).effects().get(Timer.A));
        assertTrue(!Tunes.rows(tune).get(2).registers().containsKey(Register.R8),
                "the wave owns the volume, so no row sets it while it runs");
        assertEquals(9, Tunes.rows(tune).get(6).registers().get(Register.R8),
                "the row that stops it sets the register back, to the value the dump"
                        + " has there");
    }

    @Test
    void aRecordingBecomesASourceOfItsLevelsAndAClosingRow() {
        byte[] sample = {(byte) 0x00, (byte) 0x40, (byte) 0xF0, (byte) 0x80};
        Dumps dumped = new Dumps(6, 0, List.of(sample));
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
        Dumps dumped = new Dumps(4, 0, List.of()).set(0, 0, 100).set(2, 0, 101);
        dumped.slot0(1, 1, 0, 2, 50).set(1, 8, 9);
        Multi multi = Tunes.multi(read(dumped));
        String text = Text.write(multi);
        assertEquals(multi, Text.read(text));
        assertEquals(text, Text.write(Text.read(text)));
    }

    @Test
    void anArchiveThatWillNotUnpackIsSaidToBeOne() {
        // an -lh5- header long enough to read as an archive, with no
        // member behind it
        byte[] archive = new byte[22];
        archive[0] = 0x22;
        archive[2] = '-';
        archive[3] = 'l';
        archive[4] = 'h';
        archive[5] = '5';
        archive[6] = '-';
        Dump.Unreadable no = assertThrows(Dump.Unreadable.class, () -> Dump.read(archive));
        assertTrue(String.valueOf(no.getMessage()).contains("does not unpack"),
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
