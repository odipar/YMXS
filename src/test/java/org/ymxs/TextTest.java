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
import org.ymxs.YMXS.Timing;
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.StartOne;
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
    private static final int STARTS = 180;
    private static final int RETUNES = 77;
    private static final int STOPS = 167;

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
        assertEquals(3292, rows, "the rows of the tunes under doc/tunes");
        assertEquals(STARTS, starts, "their starts");
        assertEquals(RETUNES, retunes, "their retunes");
        assertEquals(STOPS, stops, "their stops");
    }

    /** A tune with all three shapes in it, written and read back. */
    @Test
    void everyShapeSurvivesTheRoundTrip() {
        Single square = Tunes.repeating("square 15", List.of(15, 0), 0);
        Single drum = Tunes.once("drum", List.of(8, 12, 15, 13, 5));
        Single buzzer = Tunes.repeating("buzzer", List.of(10), 0);
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
                Timer.D, new StartOne(Tunes.setting(Register.R10), drum, new Timing(Prescaler.BY_200, 0, false, true)))));
        rows.add(new Row(Map.of(), Map.of(
                Timer.B, new StartOne(Tunes.setting(Register.R13), buzzer, new Timing(Prescaler.BY_50, 1, true, false)),
                Timer.C, new StartOne(Tunes.setting(Register.R9), square, new Timing(Prescaler.BY_10, 3, false, false)))));
        rows.add(new Row(Map.of(), Map.of(
                Timer.A, Tunes.bend(Prescaler.BY_4, 118),
                Timer.D, new Retune(new Timing(Prescaler.BY_100, 7, true, true)))));
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

    /** A source of several values a row, the shape json.md 4.1 defines
     *  for a target that writes several registers: the rows are arrays
     *  where the source has two or three values, a bare number where it
     *  has one, and the two read back as the records they were. */
    @Test
    void aSourceOfSeveralValuesARowCrossesTheForm() {
        String text = """
                {"format":"ymxs","version":4,"tunes":[{"title":"","composer":"",
                "writer":"t","rate":50,"rows":2,"repeat":0,
                "sources":[{"name":"a sweep","repeat":0,
                "values":[[46,1,15],[32,1,13]]},
                {"name":"square","repeat":0,"values":[13,0]}],
                "registers":{"r7":[56,-1]},
                "timerA":{"shape":[0,-1],"target":[17,-1],"source":[1,-1],
                "prescaler":[50,-1],"count":[60,-1],"timerReset":[1,-1],
                "placeReset":[1,-1]},
                "timerB":{"shape":[0,-1],"target":[9,-1],"source":[2,-1],
                "prescaler":[50,-1],"count":[80,-1],"timerReset":[1,-1],
                "placeReset":[1,-1]}}]}""";
        Multi multi = Text.read(text);
        Tune tune = multi.tunes().get(0);
        List<Source> sources = Tunes.sources(tune);
        assertEquals(3, Tunes.columns(sources.get(0)), "the sweep is three values a row");
        assertEquals(List.of(List.of(46, 1, 15), List.of(32, 1, 13)),
                Tunes.rows(sources.get(0)).rows(), "the rows read as they are written");
        assertEquals(1, Tunes.columns(sources.get(1)), "the square is one value a row");
        assertEquals(multi, Text.read(Text.write(multi)), "the form crosses both ways");
        String written = Text.write(multi).replaceAll("\\s+", " ");
        assertTrue(written.contains("[[46,1,15],[32,1,13]]"),
                "a row of several values writes as an array: " + written);
        assertTrue(written.contains("\"values\": [13,0]"),
                "a row of one value writes as a number: " + written);
    }

    /** A file of the version before this one, which a reader reads
     *  beside it: the shapes of version 3 alone, and a row of several
     *  values or a target above 13 in one is an error of the form
     *  (json.md 2.4). */
    @Test
    void theVersionBeforeThisOneIsRead() {
        String three = """
                {"format":"ymxs","version":3,"tunes":[{"title":"","composer":"",
                "writer":"t","rate":50,"rows":2,"repeat":0,
                "sources":[{"name":"square","repeat":0,"values":[13,0]}],
                "registers":{"r7":[56,-1]},
                "timerA":{"shape":[0,-1],"target":[8,-1],"source":[1,-1],
                "prescaler":[50,-1],"count":[60,-1],"timerReset":[1,-1],
                "placeReset":[1,-1]}}]}""";
        Multi read = Text.read(three);
        assertEquals(1, Tunes.columns(Tunes.sources(read.tunes().get(0)).get(0)),
                "a source of version 3 is one value a row");
        assertTrue(Text.write(read).contains("\"version\": 4"),
                "a writer writes this version: " + Text.write(read));
        String wide = three.replace("\"values\":[13,0]", "\"values\":[[13,1],[0,1]]")
                .replace("\"target\":[8,-1]", "\"target\":[14,-1]");
        String said = String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Text.read(wide)).getMessage());
        assertTrue(said.contains("version 3 has one value a row"), said);
        String target = three.replace("\"target\":[8,-1]", "\"target\":[14,-1]");
        String on = String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Text.read(target)).getMessage());
        assertTrue(on.contains("target 14, and version 3 reaches 0 to 13"), on);
        String later = three.replace("\"version\":3", "\"version\":5");
        String other = String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Text.read(later)).getMessage());
        assertTrue(other.contains("version 5, and this reads 3 or 4"), other);
    }

    /** A source whose rows differ in width, and one whose width differs
     *  from its target's registers: json.md 4.2 has the first and
     *  SPEC.md 1.11 the second. */
    @Test
    void aSourceOfTwoShapesIsTurnedAway() {
        String mixed = """
                {"format":"ymxs","version":4,"tunes":[{"title":"","composer":"",
                "writer":"t","rate":50,"rows":1,"repeat":0,
                "sources":[{"name":"mixed","repeat":0,"values":[13,[12,1]]}],
                "registers":{"r0":[1]}}]}""";
        String said = String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Text.read(mixed)).getMessage());
        assertTrue(said.contains("has rows of 1 and of 2 values"), said);
        String wide = """
                {"format":"ymxs","version":4,"tunes":[{"title":"","composer":"",
                "writer":"t","rate":50,"rows":1,"repeat":0,
                "sources":[{"name":"pair","repeat":0,"values":[[12,1]]}],
                "registers":{"r7":[56]},
                "timerA":{"shape":[0],"target":[17],"source":[1],
                "prescaler":[50],"count":[60],"timerReset":[1],"placeReset":[1]}}]}""";
        String pair = String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Text.read(wide)).getMessage());
        assertTrue(pair.contains("a source of 2 values a row on setVoiceA, which reads 3"),
                pair);
    }

    @Test
    void aColumnShorterThanTheTuneIsTurnedAway() {
        String text = """
                {"format":"ymxs","version":4,"tunes":[{"title":"","composer":"",
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
        Single square = Tunes.repeating("square", List.of(15, 0), 0);
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
                {"format":"ymxs","version":4,"tunes":[{"title":"","composer":"",
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
