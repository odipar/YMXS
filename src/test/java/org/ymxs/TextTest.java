package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

/**
 * JSON against the structure: every tune under {@code doc/tunes} read and
 * written back, and a tune built here containing all three shapes of
 * effect.
 */
final class TextTest {

    /** What the tunes under {@code doc/tunes} come to, read back so that
     *  a tune added or a form changed fails here. */
    private static final int STARTS = 177;
    private static final int RETUNES = 76;
    private static final int STOPS = 165;

    private static List<Path> tunes() throws IOException {
        try (Stream<Path> at = Files.list(Path.of("doc/tunes"))) {
            return at.filter(one -> one.toString().endsWith(".json")).sorted().toList();
        }
    }

    @Test
    void everyTuneReadsBackAsItStands() throws IOException {
        List<Path> tunes = tunes();
        assertTrue(tunes.size() >= 4, () -> "only " + tunes.size()
                + " tunes read; the check is asleep");
        for (Path at : tunes) {
            String text = Files.readString(at);
            Multi multi = Text.read(text);
            assertEquals(text, Text.write(multi), at + " does not write back as it reads");
            assertEquals(multi, Text.read(Text.write(multi)), at + " is not the multi it was");
        }
    }

    @Test
    void theTunesHoldWhatTheirTextStates() throws IOException {
        long rows = 0;
        long starts = 0;
        long retunes = 0;
        long stops = 0;
        for (Path at : tunes()) {
            for (Tune tune : Text.read(Files.readString(at)).tunes()) {
                rows += Tunes.size(tune.table());
                for (Row row : Tunes.rows(tune)) {
                    for (Effect effect : row.effects().values()) {
                        if (effect instanceof Start) {
                            starts++;
                        } else if (effect instanceof Retune) {
                            retunes++;
                        } else {
                            stops++;
                        }
                    }
                }
            }
        }
        assertEquals(3280, rows, "the rows of the tunes under doc/tunes");
        assertEquals(STARTS, starts, "their starts");
        assertEquals(RETUNES, retunes, "their retunes");
        assertEquals(STOPS, stops, "their stops");
    }

    /** A tune with all three shapes in it, written and read back. */
    @Test
    void everyShapeSurvivesTheRoundTrip() {
        Source square = Tunes.repeating("square 15", List.of(15, 0), 0);
        Source drum = Tunes.once("drum", List.of(8, 12, 15, 13, 5));
        Source buzzer = Tunes.repeating("buzzer", List.of(10), 0);
        List<Row> rows = new ArrayList<>();
        rows.add(Tunes.EMPTY);
        // every register a row sets, at the largest value that fits it
        Map<Register, Integer> all = new java.util.EnumMap<>(Register.class);
        for (Register register : Register.values()) {
            all.put(register, Chip.most(register));
        }
        rows.add(Tunes.row(all));
        // and at 0, which is a value like any other
        Map<Register, Integer> none = new java.util.EnumMap<>(Register.class);
        for (Register register : Register.values()) {
            none.put(register, 0);
        }
        rows.add(Tunes.row(none));
        rows.add(new Row(Map.of(Register.R8, 15), Map.of(
                Timer.A, Tunes.struck(Tunes.setting(Register.R8), square,
                        Prescaler.BY_4, 122),
                Timer.D, new Start(Tunes.setting(Register.R10), drum,
                        Prescaler.BY_200, 0, false, true))));
        rows.add(new Row(Map.of(), Map.of(
                Timer.B, new Start(Tunes.setting(Register.R13), buzzer,
                        Prescaler.BY_50, 1, true, false),
                Timer.C, new Start(Tunes.setting(Register.R9), square,
                        Prescaler.BY_10, 3, false, false))));
        rows.add(new Row(Map.of(), Map.of(
                Timer.A, Tunes.bend(Prescaler.BY_4, 118),
                Timer.D, new Retune(Prescaler.BY_100, 7, true, true))));
        rows.add(new Row(Map.of(Register.R8, 12), Map.of(
                Timer.A, Tunes.STOP, Timer.B, Tunes.STOP,
                Timer.C, Tunes.STOP, Timer.D, Tunes.STOP)));
        Tune tune = new Tune("every shape", "a test", "TextTest", 50,
                Tunes.repeating(rows, 1));
        Tune once = new Tune("plays once", "", "", 60,
                Tunes.once(List.of(Tunes.row(Map.of(Register.R7, 63)), Tunes.EMPTY)));
        Multi multi = new Multi(List.of(tune, once));

        String text = Text.write(multi);
        assertEquals(multi, Text.read(text), "the multi does not read back");
        assertEquals(text, Text.write(Text.read(text)), "the text does not write back");
    }

    @Test
    void aTextOfAnotherFormatOrVersionIsTurnedAway() {
        assertThrows(IllegalArgumentException.class,
                () -> Text.read("{\"format\":\"ymxr\",\"version\":1,\"tunes\":[]}"));
        assertThrows(IllegalArgumentException.class,
                () -> Text.read("{\"format\":\"ymxs\",\"version\":99,\"tunes\":[]}"));
    }

    @Test
    void aColumnShorterThanTheTuneIsTurnedAway() {
        String text = """
                {"format":"ymxs","version":3,"tunes":[{"title":"","composer":"",
                 "writer":"","rate":50,"rows":4,"repeat":null,"sources":[],
                 "registers":{"r0":[1,2]}}]}""";
        IllegalArgumentException no = assertThrows(IllegalArgumentException.class,
                () -> Text.read(text));
        String said = String.valueOf(no.getMessage());
        assertTrue(said.contains("r0 is 2 values long, and the tune has 4 rows"), said);
    }

    @Test
    void aTuneIsWrittenColumnByColumn() {
        Tune tune = new Tune("a tune", "", "", 50, Tunes.repeating(List.of(
                Tunes.row(Map.of(Register.R0, 1)), Tunes.EMPTY,
                Tunes.row(Map.of(Register.R0, 3))), 0));
        String text = Text.write(Tunes.multi(tune));
        assertTrue(text.contains("\"rows\": 3"), text);
        assertTrue(text.contains("\"r0\": [1,-1,3]"),
                "a column stands one value a row, and -1 where the row sets none");
        assertTrue(!text.contains("\"r1\""), "a register no row sets has no column");
        assertEquals(Tunes.multi(tune), Text.read(text));
    }

    @Test
    void aTimerIsAStructureOfItsOwn() {
        Source square = Tunes.repeating("square", List.of(15, 0), 0);
        Tune tune = new Tune("", "", "", 50, Tunes.repeating(List.of(
                new Row(Map.of(), Map.of(
                        Timer.A, Tunes.struck(Tunes.setting(Register.R8), square,
                                Prescaler.BY_4, 100),
                        Timer.D, Tunes.struck(Tunes.setting(Register.R9), square,
                                Prescaler.BY_4, 100))),
                new Row(Map.of(), Map.of(Timer.A, Tunes.STOP))), 0));
        String text = Text.write(Tunes.multi(tune));
        assertTrue(text.contains("\"timerA\""), "Timer A is timerA");
        assertTrue(text.contains("\"timerD\""), "Timer D is timerD");
        assertTrue(!text.contains("\"timerB\""), "a timer no row uses has no columns");
        assertTrue(text.contains("\"shape\": [0,2]"), text);
        assertEquals(Tunes.multi(tune), Text.read(text),
                "and one row may act on more than one timer");
    }

    @Test
    void aTextOfTheShapeThisNoLongerWritesIsAnError() {
        String text = """
                {"format":"ymxs","version":3,"tunes":[{"title":"","composer":"",
                 "writer":"","rate":50,"rows":1,"repeat":null,"sources":[],
                 "registers":[{"row":0,"r0":1}],"effects":[]}]}""";
        IllegalArgumentException no = assertThrows(IllegalArgumentException.class,
                () -> Text.read(text));
        assertTrue(String.valueOf(no.getMessage()).contains("registers is an array"),
                String.valueOf(no.getMessage()));
    }

    @Test
    void aTuneOfOneRowStatesItsRepeat() {
        Tune tune = new Tune("", "", "", 50,
                new Table<>(List.of(Tunes.EMPTY), OptionalInt.of(0)));
        assertEquals(OptionalInt.of(0),
                Tunes.tune(Text.read(Text.write(Tunes.multi(tune))), 1).table().repeat());
    }
}
