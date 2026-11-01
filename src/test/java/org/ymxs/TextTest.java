package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.ymxs.YMXS.Mapping;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.OneConnection;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Remap;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Transformation;
import org.ymxs.YMXS.Tune;

/**
 * JSON against the structure: every tune under {@code doc/tunes} read and
 * written back, and a tune built here containing all three shapes of
 * effect.
 */
final class TextTest {

    /** What the tunes under {@code doc/tunes} come to, read back so that
     *  a tune added or a form changed fails here. */
    private static final int STARTS = 184;
    private static final int RETUNES = 77;
    private static final int STOPS = 169;
    private static final int REMAPS = 15;

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
        long remaps = 0;
        for (Path at : tunes()) {
            for (Tune tune : Text.read(Files.readString(at)).tunes()) {
                rows += Tunes.size(tune.table());
                for (Row row : Tunes.rows(tune)) {
                    for (Effect effect : row.effects().values()) {
                        if (effect instanceof Start) {
                            starts++;
                        } else if (effect instanceof Retune) {
                            retunes++;
                        } else if (effect instanceof Remap) {
                            remaps++;
                        } else {
                            stops++;
                        }
                    }
                }
            }
        }
        assertEquals(3324, rows, "the rows of the tunes under doc/tunes");
        assertEquals(STARTS, starts, "their starts");
        assertEquals(RETUNES, retunes, "their retunes");
        assertEquals(STOPS, stops, "their stops");
        assertEquals(REMAPS, remaps, "their remaps");
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
                Timer.D, new Start(new OneConnection(Tunes.setting(Register.R10), drum), new Timing(Prescaler.BY_200, 0, false, true), false, Tunes.NO_PARTS))));
        rows.add(new Row(Map.of(), Map.of(
                Timer.B, new Start(new OneConnection(Tunes.setting(Register.R13), buzzer), new Timing(Prescaler.BY_50, 1, true, false), true, Tunes.NO_PARTS),
                Timer.C, new Start(new OneConnection(Tunes.setting(Register.R9), square), new Timing(Prescaler.BY_10, 3, false, false), false, Tunes.NO_PARTS))));
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
        assertTrue(Text.write(read).contains("\"version\": 7"),
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
        String later = three.replace("\"version\":3", "\"version\":8");
        String other = String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Text.read(later)).getMessage());
        assertEquals("version 8, and this reads 3, 4, 5, 6 or 7", other);
    }

    /** A start's deferral is a column of version 5: a reader of a file of
     *  version 4 skips the column and reads every start with it clear, and
     *  in a file of version 5 a start requires it (json.md 2.4, 6.2). */
    @Test
    void theDeferralIsAColumnOfThisVersion() {
        String four = """
                {"format":"ymxs","version":4,"tunes":[{"title":"","composer":"",
                "writer":"t","rate":50,"rows":2,"repeat":0,
                "sources":[{"name":"square","repeat":0,"values":[13,0]}],
                "registers":{"r7":[56,-1]},
                "timerA":{"shape":[0,0],"target":[8,8],"source":[1,1],
                "prescaler":[50,50],"count":[60,60],"timerReset":[1,0],
                "placeReset":[1,0],"deferred":[1,1]}}]}""";
        Tune tune = Text.read(four).tunes().get(0);
        assertFalse(deferred(tune, 0) || deferred(tune, 1),
                "a start of version 4 reads with its deferral clear");
        Tune five = Text.read(four.replace("\"version\":4", "\"version\":5")).tunes().get(0);
        assertTrue(deferred(five, 1), "a start of version 5 reads its deferral");
        String without = four.replace("\"version\":4", "\"version\":5")
                .replace(",\"deferred\":[1,1]", "");
        String said = String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Text.read(without)).getMessage());
        assertTrue(said.contains("Timer A has no \"deferred\" column"), said);
    }

    /** The deferral of the start on Timer A at row {@code at} of the
     *  tune. */
    private static boolean deferred(Tune tune, int at) {
        return start(tune, at).deferred();
    }

    /** The start on Timer A at row {@code at} of the tune. */
    private static Start start(Tune tune, int at) {
        if (!(tune.table().rows().get(at).effects().get(Timer.A) instanceof Start start)) {
            throw new AssertionError("row " + at + " starts Timer A");
        }
        return start;
    }

    /** A source whose rows differ in width, and one whose width differs
     *  from its target's registers, each an error of the form (json.md
     *  4.2, 8.1): the second is one unprefixed line, raised as the start
     *  is read, since the connection of a start pairs a source with a
     *  target of its width (SPEC.md 1.1). */
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
        assertEquals("a source of 2 values a row on setVoiceA, which reads 3", pair);
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
                "a column is one value a row, and -1 where the row sets none");
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

    // ----------------------------------------- transformations, version 7

    /** The transformations of a tune in the order of SPEC.md 1.9: the
     *  rows from row 0, the timers A to D within a row, the parts of a
     *  mapping from part 0, each new one appended; equal entries under
     *  another name are another transformation, and JSON lists them in
     *  that order (json.md 3.8). */
    @Test
    void theTransformationsAreNumberedInTheOrderTheRowsMapThrough() {
        Transformation ten = new Transformation("ten", Fade.volumes(10).entries());
        Single square = Tunes.repeating("square", List.of(15, 0), 0);
        Tune tune = new Tune("", "", "t", 50, Tunes.repeating(List.of(
                new Row(Map.of(), Map.of(
                        Timer.B, new Start(new OneConnection(Tunes.setting(Register.R9), square),
                                new Timing(Prescaler.BY_200, 192, true, true), false,
                                Tunes.mapping(Fade.volumes(5))),
                        Timer.A, Fade.voice(Fade.NOTE, Fade.volume(10)))),
                new Row(Map.of(), Map.of(Timer.A, new Remap(Tunes.mapping(Tunes.IDENTITY,
                        Fade.volumes(3), Fade.volumes(5))))),
                new Row(Map.of(), Map.of(Timer.A, new Remap(Tunes.mapping(Tunes.IDENTITY,
                        Tunes.IDENTITY, ten)))),
                new Row(Map.of(), Map.of(Timer.A, new Remap(Fade.volume(10))))), 0));
        assertEquals(List.of(Fade.volumes(10), Fade.volumes(5), Fade.volumes(3), ten),
                Tunes.transformations(tune));
        assertEquals(0, Tunes.number(Tunes.transformations(tune), Tunes.IDENTITY),
                "the identity is 0, outside the list");
        String text = Text.write(Tunes.multi(tune));
        String flat = text.replaceAll("\\s+", " ");
        List<String> names = new ArrayList<>();
        java.util.regex.Matcher name = java.util.regex.Pattern
                .compile("\\{\"name\": \"([^\"]*)\", \"entries\"").matcher(text);
        while (name.find()) {
            names.add(name.group(1));
        }
        assertEquals(List.of("V10", "V5", "V3", "ten"), names, text);
        assertTrue(flat.contains("\"mapping\": [[0,0,1],[0,3,2],[0,0,4],[0,0,1]]"), flat);
        assertTrue(flat.contains("\"mapping\": [[2],-1,-1,-1]"), flat);
        assertEquals(Tunes.multi(tune), Text.read(text), "the form crosses both ways");
    }

    /** A table of 16 entries and one of 256 under one name are two
     *  transformations, and JSON lists both, 1 and 2 (SPEC.md 3.6.2,
     *  json.md 3.8). */
    @Test
    void aTableOf16EntriesAndOneOf256OfOneNameAreTwoTransformations() {
        List<Integer> entries = new ArrayList<>();
        for (int key = 0; key < 256; key++) {
            entries.add(key / 16);
        }
        Transformation long256 = new Transformation("V15", entries);
        Transformation short16 = Fade.volumes(15);
        Single square = Tunes.repeating("square", List.of(15, 0), 0);
        Tune tune = new Tune("", "", "t", 50, Tunes.repeating(List.of(
                new Row(Map.of(), Map.of(Timer.A, new Start(
                        new OneConnection(Tunes.setting(Register.R8), square),
                        new Timing(Prescaler.BY_200, 192, true, true), false,
                        Tunes.mapping(short16)))),
                new Row(Map.of(), Map.of(Timer.A, new Remap(Tunes.mapping(long256))))), 0));
        assertEquals(List.of(short16, long256), Tunes.transformations(tune));
        String text = Text.write(Tunes.multi(tune));
        assertTrue(text.replaceAll("\\s+", " ").contains("\"mapping\": [[1],[2]]"), text);
        assertEquals(Tunes.multi(tune), Text.read(text), "the two cross both ways");
    }

    /** The fade of SPEC.md 3.6.5: sixteen remaps through V15 to V0, the
     *  mapping column 20 values a line and its arrays values of it. */
    @Test
    void aFadeOfSixteenRemapsCrossesTheForm() {
        Tune fade = Fade.tune();
        String text = Text.write(Tunes.multi(fade));
        assertEquals(Tunes.multi(fade), Text.read(text), "the fade reads back");
        assertEquals(text, Text.write(Text.read(text)), "and writes back as it was");
        List<Transformation> listed = new ArrayList<>();
        for (int n = 15; n >= 0; n--) {
            listed.add(Fade.volumes(n));
        }
        assertEquals(listed, Tunes.transformations(fade), "V15 is 1, and V0 is 16");
        assertTrue(text.contains("\"shape\": [0,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,-1,-1,-1,\n"
                + "          -1,-1,2],"), text);
        StringBuilder mapping = new StringBuilder("\"mapping\": [0");
        for (int n = 1; n <= 16; n++) {
            mapping.append(",[0,0,").append(n).append(']');
        }
        mapping.append(",-1,-1,-1,\n          -1,-1,-1]\n");
        assertTrue(text.contains(mapping), text);
        assertTrue(text.contains("{\"name\": \"V15\", \"entries\": [0,1,2,3,"), text);
    }

    /** A start of setVoiceA that maps its volume alone: the identity, the
     *  identity and V5 (SPEC.md 3.6.5). */
    @Test
    void aStartMapsTheVolumeOfAVoiceAlone() {
        Tune wide = Fade.wide();
        String flat = Text.write(Tunes.multi(wide)).replaceAll("\\s+", " ");
        assertTrue(flat.contains("\"target\": [17,-1]"), flat);
        assertTrue(flat.contains("\"mapping\": [[0,0,1],-1]"), flat);
        assertTrue(flat.contains("\"values\": [[28,1,15],[214,0,12]]"), flat);
        assertEquals(Tunes.multi(wide), Text.read(Text.write(Tunes.multi(wide))));
    }

    /** The layout of json.md 9 for a tune with a transformation, a start
     *  through it and a remap: the entries 20 a line at the indent of
     *  depth 6, the transformation one line, the mapping column 0 for the
     *  mapping of no parts and an array otherwise, and shape 3. */
    @Test
    void theEmitterLaysATransformationOutTwentyEntriesALine() {
        List<Integer> entries = new ArrayList<>();
        for (int key = 0; key < 256; key++) {
            entries.add(key / 16);
        }
        Transformation levels = new Transformation("levels", entries);
        Tune tune = new Tune("keys", "", "TextTest", 50, Tunes.once(List.of(
                new Row(Map.of(), Map.of(Timer.A, new Start(
                        new OneConnection(Tunes.setting(Register.R8),
                                Tunes.repeating("keys", List.of(0, 31), 0)),
                        new Timing(Prescaler.BY_200, 192, true, true), false,
                        Tunes.mapping(levels)))),
                new Row(Map.of(), Map.of(Timer.A, new Remap(Tunes.NO_PARTS))),
                new Row(Map.of(), Map.of(Timer.A, Tunes.STOP)))));
        String expected = """
                {
                  "format": "ymxs",
                  "version": 7,
                  "tunes": [
                    {
                      "title": "keys",
                      "composer": "",
                      "writer": "TextTest",
                      "rate": 50,
                      "rows": 3,
                      "repeat": null,
                      "sources": [
                        {"name": "keys", "repeat": 0, "values": [0,31]}
                      ],
                      "transformations": [
                        {"name": "levels", "entries": [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,
                            1,1,1,1,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,
                            2,2,2,2,2,2,2,2,3,3,3,3,3,3,3,3,3,3,3,3,
                            3,3,3,3,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,
                            5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,6,6,6,6,
                            6,6,6,6,6,6,6,6,6,6,6,6,7,7,7,7,7,7,7,7,
                            7,7,7,7,7,7,7,7,8,8,8,8,8,8,8,8,8,8,8,8,
                            8,8,8,8,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,9,
                            10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,11,11,11,11,
                            11,11,11,11,11,11,11,11,11,11,11,11,12,12,12,12,12,12,12,12,
                            12,12,12,12,12,12,12,12,13,13,13,13,13,13,13,13,13,13,13,13,
                            13,13,13,13,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,14,
                            15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15]}
                      ],
                      "registers": {},
                      "timerA": {
                        "shape": [0,3,2],
                        "target": [8,-1,-1],
                        "source": [1,-1,-1],
                        "prescaler": [200,-1,-1],
                        "count": [192,-1,-1],
                        "timerReset": [1,-1,-1],
                        "placeReset": [1,-1,-1],
                        "deferred": [0,-1,-1],
                        "mapping": [[1],0,-1]
                      }
                    }
                  ]
                }
                """;
        assertEquals(expected, Text.write(Tunes.multi(tune)));
        assertEquals(Tunes.multi(tune), Text.read(expected), "the text reads back");
    }

    /** A mapping whose every part is the identity is written 0, and reads
     *  back as the mapping of no parts (json.md 6.7). */
    @Test
    void aMappingOfTheIdentityInEveryPartReadsBackAsNoParts() {
        Single square = Tunes.repeating("square", List.of(15, 0), 0);
        Tune tune = new Tune("", "", "t", 50, Tunes.repeating(List.of(new Row(Map.of(),
                Map.of(Timer.A, new Start(new OneConnection(Tunes.setting(Register.R8), square),
                        new Timing(Prescaler.BY_200, 192, true, true), false,
                        Tunes.mapping(Tunes.IDENTITY))))), 0));
        String text = Text.write(Tunes.multi(tune));
        assertTrue(text.replaceAll("\\s+", " ").contains("\"mapping\": [0]"), text);
        assertTrue(text.contains("\"transformations\": []"), text);
        assertEquals(Tunes.NO_PARTS, start(Text.read(text).tunes().get(0), 0).mapping());
    }

    /** 256 entries of key divided by 2, as a JSON array. */
    private static String half() {
        List<String> entries = new ArrayList<>();
        for (int key = 0; key < 256; key++) {
            entries.add(String.valueOf(key / 2));
        }
        return "[" + String.join(",", entries) + "]";
    }

    /** A tune of version 7: a start on setR8 through transformation 1 and
     *  a remap of the mapping of no parts. */
    private static final String SEVEN = """
            {"format":"ymxs","version":7,"tunes":[{"title":"","composer":"",
            "writer":"t","rate":50,"rows":2,"repeat":0,
            "sources":[{"name":"square","repeat":0,"values":[13,0]}],
            "transformations":[{"name":"half","entries":HALF}],
            "registers":{"r7":[56,-1]},
            "timerA":{"shape":[0,3],"target":[8,-1],"source":[1,-1],
            "prescaler":[50,-1],"count":[60,-1],"timerReset":[1,-1],
            "placeReset":[1,-1],"deferred":[0,-1],"mapping":[[1],0]}}]}"""
            .replace("HALF", half());

    /** The line {@code text} is turned away with. */
    private static String wrong(String text) {
        return String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Text.read(text)).getMessage());
    }

    @Test
    void aTuneOfVersion7ReadsItsMappings() {
        Tune tune = Text.read(SEVEN).tunes().get(0);
        Transformation half = Tunes.transformations(tune).get(0);
        assertEquals("half", half.name());
        assertEquals(Tunes.mapping(half), start(tune, 0).mapping());
        assertEquals(new Remap(Tunes.NO_PARTS), Tunes.rows(tune).get(1).effects().get(Timer.A));
        String timerB = SEVEN.replace("}}]}", "},\"timerB\":{\"shape\":[-1,3],"
                + "\"mapping\":[-1,[1]]}}]}");
        assertEquals(new Remap(Tunes.mapping(half)),
                Tunes.rows(Text.read(timerB).tunes().get(0)).get(1).effects().get(Timer.B),
                "a remap reads its mapping column alone");
    }

    /** Each condition of json.md 8.1 that version 7 adds, as its line, in
     *  the order of json.md 7.1 steps 6 and 8. */
    @Test
    void eachErrorOfATransformationOrAMappingIsItsLine() {
        assertEquals("transformations is null, and this form requires an array",
                wrong(SEVEN.replaceAll("\"transformations\":\\[.*?\\]}\\],", "")));
        assertEquals("transformations is {}, and this form requires an array",
                wrong(SEVEN.replaceAll("\"transformations\":\\[.*?\\]}\\]",
                        "\"transformations\":{}")));
        assertEquals("entries is null, and this form requires an array",
                wrong(SEVEN.replaceAll("\\[\\{\"name\":\"half\",\"entries\":\\[.*?\\]}\\]",
                        "[5]")));
        assertEquals("name is null, and this form requires a text",
                wrong(SEVEN.replace("{\"name\":\"half\",\"entries\":",
                        "{\"entries\":").replace("[0,0,1,1,", "[0,\"x\",1,1,")));
        assertEquals("half at entry 1 is \"x\", and this form requires a whole number",
                wrong(SEVEN.replace("[0,0,1,1,", "[0,\"x\",1,1,")));
        assertEquals("half at entry 2 is 1.5, and this form requires a whole number",
                wrong(SEVEN.replace("[0,0,1,1,", "[0,0,1.5,1,")));
        assertEquals("square at row 1 is \"x\", and this form requires a whole number",
                wrong(SEVEN.replace("[13,0]", "[13,\"x\"]")
                        .replace("[0,0,1,1,", "[0,\"x\",1,1,")),
                "the sources are read before the transformations");
        assertEquals("Timer A has no \"mapping\" column",
                wrong(SEVEN.replace(",\"mapping\":[[1],0]", "")));
        assertEquals("Timer A has no \"deferred\" column",
                wrong(SEVEN.replace(",\"deferred\":[0,-1]", "")
                        .replace("[[1],0]", "[5,0]")),
                "a start reads deferred before mapping");
        assertEquals("Timer A's mapping is 5, and this form requires a column",
                wrong(SEVEN.replace("[[1],0]", "5")));
        assertEquals("Timer A's mapping is 1 values long, and the tune has 2 rows",
                wrong(SEVEN.replace("[[1],0]", "[[1]]")));
        assertEquals("Timer A's mapping at row 0 is 5, and this form requires 0 or an"
                + " array of whole numbers", wrong(SEVEN.replace("[[1],0]", "[5,0]")));
        assertEquals("Timer A's mapping at row 1 is -1, and this form requires 0 or an"
                + " array of whole numbers", wrong(SEVEN.replace("[[1],0]", "[[1],-1]")));
        assertEquals("Timer A's mapping at row 0 is [0,\"x\"], and this form requires 0 or"
                + " an array of whole numbers",
                wrong(SEVEN.replace("[[1],0]", "[[0,\"x\"],0]")));
        assertEquals("Timer A's mapping at row 0 is [0,0], and a mapping of the identity in"
                + " every part is 0", wrong(SEVEN.replace("[[1],0]", "[[0,0],0]")));
        assertEquals("Timer A's mapping at row 0 is [], and a mapping of the identity in"
                + " every part is 0", wrong(SEVEN.replace("[[1],0]", "[[],0]")));
        assertEquals("Timer A's mapping at row 1 is [0], and a mapping of the identity in"
                + " every part is 0", wrong(SEVEN.replace("[[1],0]", "[[1],[0]]")),
                "a remap reads its mapping as a start does");
        assertEquals("row 0 maps through transformation 2, and the tune has 1",
                wrong(SEVEN.replace("[[1],0]", "[[0,2,-1],0]")));
        assertEquals("row 0 maps through transformation -1, and the tune has 1",
                wrong(SEVEN.replace("[[1],0]", "[[0,-1,2],0]")),
                "the elements are read in order");
        assertEquals("row 1 sets shape 4 on Timer A, and a shape is -1, 0, 1, 2 or 3",
                wrong(SEVEN.replace("\"shape\":[0,3]", "\"shape\":[0,4]")));
    }

    /** A transformation the mapping of no row uses is an error of the
     *  form, after the line of an unstarted source (json.md 3.9, 8.1). */
    @Test
    void anUnusedTransformationIsAnErrorAfterTheUnstartedSources() {
        String unused = SEVEN.replace("{\"name\":\"half\",\"entries\":" + half() + "}",
                "{\"name\":\"half\",\"entries\":" + half() + "},"
                        + "{\"name\":\"spare\",\"entries\":" + half() + "}");
        assertEquals("transformation 2, spare, is used by no row, and a transformation a"
                + " tune does not map through is dropped where this form is read",
                wrong(unused));
        String both = unused.replace("\"values\":[13,0]}",
                "\"values\":[13,0]},{\"name\":\"rest\",\"values\":[0]}");
        assertEquals("source 2, rest, is started by no row, and a source a tune does not run"
                + " is dropped where this form is read\n"
                + "transformation 2, spare, is used by no row, and a transformation a tune"
                + " does not map through is dropped where this form is read", wrong(both));
    }

    /**
     * A name with a line feed or a carriage return in it is in a line with
     * each as the two characters {@code \n} or {@code \r}, so the line is
     * one line (json.md 8.1, 8.3). The file has the name with the same two
     * characters, the escape of JSON, so {@code name} below is the name in
     * the file and in the line.
     */
    @Test
    void aNameWithALineFeedOrACarriageReturnIsOneLine() {
        for (String name : List.of("a\\nb", "a\\rb")) {
            String named = SEVEN.replace("\"name\":\"half\"", "\"name\":\"" + name + "\"");
            assertEquals(name.replace("\\n", "\n").replace("\\r", "\r"),
                    Tunes.transformations(Text.read(named).tunes().get(0)).get(0).name(),
                    "the name read has the character itself");
            assertEquals("tune 1: transformation 1, " + name + ": the transformation has 0"
                    + " entries, and a transformation has 1 to 256",
                    wrong(named.replace("\"entries\":" + half(), "\"entries\":[]")));
            assertEquals(name + " at entry 0 is \"x\", and this form requires a whole number",
                    wrong(named.replace("\"entries\":" + half(), "\"entries\":[\"x\"]")));
            assertEquals("source 2, " + name + ", is started by no row, and a source a tune"
                    + " does not run is dropped where this form is read",
                    wrong(SEVEN.replace("\"values\":[13,0]}", "\"values\":[13,0]},{\"name\":\""
                            + name + "\",\"values\":[0]}")));
            assertEquals("transformation 2, " + name + ", is used by no row, and a"
                    + " transformation a tune does not map through is dropped where this form"
                    + " is read", wrong(SEVEN.replace("\"entries\":" + half() + "}",
                            "\"entries\":" + half() + "},{\"name\":\"" + name
                                    + "\",\"entries\":[0]}")));
            String source = SEVEN.replace("\"name\":\"square\"", "\"name\":\"" + name + "\"");
            assertEquals(name + " at row 1 is \"x\", and this form requires a whole number",
                    wrong(source.replace("[13,0]", "[13,\"x\"]")));
            assertEquals("a row of 4 values in source " + name + ", and a row is a number or"
                    + " two or three values", wrong(source.replace("[13,0]", "[13,[0,1,2,3]]")));
            assertEquals("source " + name + " has rows of 1 and of 2 values, and a source has"
                    + " one shape", wrong(source.replace("[13,0]", "[13,[0,1]]")));
            assertEquals("source " + name + " has a row of several values, and version 3 has"
                    + " one value a row", wrong(source.replace("\"version\":7", "\"version\":3")
                            .replace("[13,0]", "[13,[0,1]]")));
        }
    }

    /** X of a line is the value as JSON text, each number in it as
     *  written in the file (json.md 8.1): the parser's node writes
     *  {@code 1e1} as {@code 10.0}, {@code 1.50} as {@code 1.5} and
     *  {@code -0} as {@code 0}. An object's members are in file order, the
     *  last of two of one name at the place of the first. */
    @Test
    void aValueInALineIsWrittenAsTheFileWritesIt() {
        String requires = ", and this form requires 0 or an array of whole numbers";
        assertEquals("Timer A's mapping at row 0 is [1,1e1,1.50,2E-3,-0.0]" + requires,
                wrong(SEVEN.replace("[[1],0]", "[[1, 1e1, 1.50, 2E-3, -0.0],0]")));
        assertEquals("Timer A's mapping at row 1 is [-0,0], and a mapping of the identity in"
                + " every part is 0", wrong(SEVEN.replace("[[1],0]", "[[1],[-0,0]]")));
        assertEquals("Timer A's mapping at row 0 is {\"b\":2.0,\"a\":[-0]}" + requires,
                wrong(SEVEN.replace("[[1],0]", "[{\"b\":1,\"a\":[-0],\"b\":2.0},0]")));
        assertEquals("Timer A's count at row 0 is 6e1, and this form requires a whole"
                + " number", wrong(SEVEN.replace("\"count\":[60,-1]", "\"count\":[6e1,-1]")));
        assertEquals("repeat is 1E+0, and this form requires a row number or null",
                wrong(SEVEN.replace("\"repeat\":0,", "\"repeat\":1E+0,")));
        assertEquals(Text.read(SEVEN), Text.read(SEVEN.replace("\"repeat\":0,",
                "\"repeat\":-0,")), "-0 is the whole number 0 (json.md 1.4)");
    }

    /** An error of a transformation is an error of the structure, under
     *  the number of the transformation, and a reader reads any number of
     *  entries, 0 included (SPEC.md 1.12, json.md 3.7, 8.3). */
    @Test
    void aTransformationOf0Or257EntriesIsAnErrorOfTheStructure() {
        assertEquals("tune 1: transformation 1, half: the transformation has 257 entries, and a"
                + " transformation has 1 to 256",
                wrong(SEVEN.replace("[0,0,1,1,", "[0,0,0,1,1,")));
        assertEquals("tune 1: transformation 1, half: the transformation has 0 entries, and a"
                + " transformation has 1 to 256",
                wrong(SEVEN.replace("\"entries\":" + half(), "\"entries\":[]")));
        Tune short255 = Text.read(SEVEN.replace("[0,0,1,1,", "[0,1,1,")).tunes().get(0);
        assertEquals(255, Tunes.transformations(short255).get(0).entries().size(),
                "255 entries is a transformation");
    }

    /** A source value above the last key of a table of 16 entries is an
     *  error of the structure: the key 16 through entries of keys 0 to 15
     *  (SPEC.md 3.6.5). */
    @Test
    void aKeyAboveTheLastKeyOfATableOf16EntriesIsAnErrorOfTheStructure() {
        String sixteen = SEVEN.replace("\"entries\":" + half(),
                "\"entries\":[0,0,1,1,2,2,3,3,4,4,5,5,6,6,7,7]");
        assertEquals(16, Tunes.transformations(Text.read(sixteen).tunes().get(0)).get(0)
                .entries().size(), "13 and 0 are keys of 0 to 15");
        assertEquals("tune 1: row 0: Timer A: a source on setR8 whose row 0 is 16, and a key"
                + " is 0 to 15", wrong(sixteen.replace("[13,0]", "[16,0]")));
    }

    /** A file of version 6 reads as before: its transformations and its
     *  mapping column are skipped, every start has the mapping of no
     *  parts, and shape 3 is an error of the form (json.md 2.4). */
    @Test
    void aFileOfVersion6ReadsAsBefore() {
        String six = SEVEN.replace("\"version\":7", "\"version\":6")
                .replace("\"shape\":[0,3]", "\"shape\":[0,-1]");
        Tune tune = Text.read(six).tunes().get(0);
        assertEquals(Tunes.NO_PARTS, start(tune, 0).mapping());
        assertEquals(List.of(), Tunes.transformations(tune));
        String bare = six.replaceAll("\"transformations\":\\[.*?\\]}\\],", "")
                .replace(",\"mapping\":[[1],0]", "");
        assertEquals(Text.read(bare), Text.read(six), "the two keys are skipped");
        assertEquals(Text.read(six), Text.read(six.replace("[[1],0]", "\"x\"")
                .replaceAll("\"transformations\":\\[.*?\\]}\\]", "\"transformations\":5")),
                "a mapping of text and transformations of a number are skipped too");
        assertEquals("row 1 sets shape 3 on Timer A, and a shape is -1, 0, 1 or 2",
                wrong(six.replace("\"shape\":[0,-1]", "\"shape\":[0,3]")));
        assertEquals("row 1 sets shape 4 on Timer A, and a shape is -1, 0, 1 or 2",
                wrong(six.replace("\"shape\":[0,-1]", "\"shape\":[0,4]")));
    }
}
