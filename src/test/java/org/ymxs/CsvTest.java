package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.ymxs.YMXS.Timing;
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.OneConnection;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

/**
 * CSV against JSON. Both write the same structure, so a tune written in
 * one form and read from the other is an equal tune.
 */
final class CsvTest {

    private static List<Path> tunes() throws IOException {
        try (Stream<Path> at = Files.list(Path.of("doc/tunes"))) {
            return at.filter(one -> one.toString().endsWith(".json")).sorted().toList();
        }
    }

    @Test
    void everyTuneCrossesFromOneFormToTheOther() throws IOException {
        List<Path> tunes = tunes();
        assertTrue(tunes.size() >= 4, () -> "only " + tunes.size() + " tunes read");
        for (Path at : tunes) {
            Multi text = Text.read(Files.readString(at));
            assertEquals(text, Csv.read(Csv.write(text)),
                    at + " is not the multi it was, written as a table and read back");
        }
    }

    @Test
    void theCsvFormReadsBackAsItStands() throws IOException {
        Path at = Path.of("doc/tunes/circus.csv");
        String csv = Files.readString(at);
        assertEquals(csv, Csv.write(Csv.read(csv)), at + " does not write back as it reads");
        assertEquals(Text.read(Files.readString(Path.of("doc/tunes/circus.json"))),
                Csv.read(csv), "the two forms of one tune read into one structure");
    }

    @Test
    void aHashLineNamesATableAndTheLineAfterItNamesTheColumns() throws IOException {
        List<String> lines = Files.readString(Path.of("doc/tunes/circus.csv")).lines()
                .toList();
        List<String> named = lines.stream().filter(one -> one.startsWith("###"))
                .map(one -> one.substring(3).strip()).toList();
        assertEquals(List.of("multi", "tune", "registers"), named,
                "circus is rows alone, so those are the three tables it opens");
        int rows = lines.indexOf("### registers");
        assertEquals(List.of("row", "r0", "r1", "r2", "r3", "r4", "r5", "r6",
                "r7", "r8", "r9", "r10", "r11", "r12", "r13"),
                Csv.cells(lines.get(rows + 1)),
                "a row of a tune is a row here, with a column a register");
    }

    /** The point of the two lines: a column name is over the cells it
     *  names, so a reader counts the columns of a table by reading down. */
    @Test
    void aColumnNameStandsOverTheCellsItNames() throws IOException {
        List<String> lines = Files.readString(Path.of("doc/tunes/circus.csv")).lines()
                .toList();
        for (int at = 0; at < lines.size(); at++) {
            if (!lines.get(at).startsWith("###")) {
                continue;
            }
            String named = lines.get(at).substring(3).strip();
            assertTrue(!named.contains(","), "a heading line names the table alone: " + named);
            int columns = Csv.cells(lines.get(at + 1)).size();
            for (int row = at + 2; row < lines.size() && !lines.get(row).isBlank(); row++) {
                assertEquals(columns, Csv.cells(lines.get(row)).size(),
                        named + ": a row has a cell a column, under the name of it");
            }
        }
    }

    @Test
    void aRowIsARowAndAnEmptyCellIsARegisterItDoesNotSet() throws IOException {
        List<String> rows = Files.readString(Path.of("doc/tunes/circus.csv")).lines()
                .dropWhile(one -> !one.startsWith("### registers"))
                .skip(2).takeWhile(one -> !one.isBlank()).toList();
        assertEquals(4, rows.size(), "one line a row that sets something");
        List<String> first = Csv.cells(rows.get(0));
        assertEquals("0", first.get(0), "the row");
        assertEquals("163", first.get(1), "R0");
        List<String> second = Csv.cells(rows.get(1));
        assertEquals("", second.get(3), "row 1 does not set R2, so its cell is empty");
        assertEquals("12", second.get(2), "and it does set R1");
    }

    @Test
    void eachSourceOpensATableOfItsOwn() {
        Tune tune = new Tune("", "", "", 50, Tunes.repeating(List.of(
                new Row(Map.of(), Map.of(Timer.A, Tunes.struck(Tunes.setting(Register.R8),
                        Tunes.repeating("first", List.of(15, 0), 0), Prescaler.BY_4, 100))),
                new Row(Map.of(), Map.of(Timer.D, Tunes.struck(Tunes.setting(Register.R9),
                        Tunes.once("second", List.of(1, 2, 3)), Prescaler.BY_4, 100)))), 0));
        List<String> named = Csv.write(Tunes.multi(tune)).lines()
                .filter(one -> one.startsWith("###"))
                .map(one -> one.substring(3).strip()).toList();
        assertEquals(List.of("multi", "tune", "source", "value", "source", "value",
                "registers", "timerA", "timerD"), named,
                "each source opens a table, and so does each timer a row uses");
        assertEquals(Tunes.multi(tune), Csv.read(Csv.write(Tunes.multi(tune))));
    }

    @Test
    void aTuneOpensItsOwnTablesAndTheNextTuneOpensTheNext() {
        Tune one = new Tune("one", "", "", 50,
                Tunes.repeating(List.of(Tunes.row(Map.of(Register.R0, 1))), 0));
        Tune two = new Tune("two", "", "", 60,
                Tunes.repeating(List.of(Tunes.row(Map.of(Register.R0, 2))), 0));
        Multi multi = new Multi(List.of(one, two));
        String csv = Csv.write(multi);
        assertEquals(2, csv.lines().filter(said -> said.equals("### tune")).count(),
                "one table a tune opens it");
        assertTrue(!csv.contains(",tune,"), "and a table needs no column for its tune");
        assertEquals(multi, Csv.read(csv));
    }

    @Test
    void aMultiOfSeveralTunesCrossesOverTuneByTune() throws IOException {
        Multi multi = Text.read(Files.readString(Path.of("doc/tunes/two-tunes.json")));
        assertEquals(2, multi.tunes().size());
        Multi back = Csv.read(Csv.write(multi));
        assertEquals(multi, back, "the multi is the multi it was");
        for (int at = 0; at < multi.tunes().size(); at++) {
            assertEquals(multi.tunes().get(at), back.tunes().get(at),
                    "tune " + (at + 1) + " is the tune it was");
        }
        assertTrue(!multi.tunes().get(0).equals(multi.tunes().get(1)),
                "and the two are not one tune written twice");
    }

    @Test
    void twoTunesMayNameOneSourceAndTheyStayApart() throws IOException {
        Multi multi = Text.read(Files.readString(Path.of("doc/tunes/two-tunes.json")));
        Source one = Tunes.sources(multi.tunes().get(0)).get(0);
        Source two = Tunes.sources(multi.tunes().get(1)).get(0);
        assertEquals(Tunes.name(one), Tunes.name(two), "both tunes call a source this");
        assertTrue(!one.equals(two), "and the two are not one source");
        assertEquals(225, Tunes.values(one).size());
        assertEquals(293, Tunes.values(two).size());

        Multi back = Csv.read(Csv.write(multi));
        assertEquals(one, Tunes.sources(back.tunes().get(0)).get(0),
                "a source belongs to the tune whose tables it is under, not to its name");
        assertEquals(two, Tunes.sources(back.tunes().get(1)).get(0));
    }

    @Test
    void theTablesOfOneTuneStandTogether() throws IOException {
        Multi multi = Text.read(Files.readString(Path.of("doc/tunes/two-tunes.json")));
        List<String> named = Csv.write(multi).lines().filter(one -> one.startsWith("###"))
                .map(one -> one.substring(3).strip()).toList();
        assertEquals(List.of("multi",
                "tune", "source", "value", "source", "value", "source", "value",
                        "registers", "timerD",
                "tune", "source", "value", "registers", "timerD"), named,
                "a tune opens its tables and the next tune opens the next");
    }

    @Test
    void aTimerHoldsWhatTheTextFormHoldsOfIt() {
        Single square = Tunes.repeating("square", List.of(15, 0), 0);
        Tune tune = new Tune("", "", "", 50, Tunes.repeating(List.of(
                new Row(Map.of(), Map.of(Timer.A, Tunes.struck(
                        Tunes.setting(Register.R8), square, Prescaler.BY_4, 100))),
                new Row(Map.of(), Map.of(Timer.A, Tunes.bend(Prescaler.BY_4, 90))),
                new Row(Map.of(), Map.of(Timer.A, Tunes.STOP))), 0));
        List<String> rows = Csv.write(Tunes.multi(tune)).lines()
                .dropWhile(one -> !one.startsWith("### timerA"))
                .skip(2).takeWhile(one -> !one.isBlank()).toList();
        assertEquals(List.of(
                "0,0,8,1,4,100,1,1,0,0",
                "1,1,,,4,90,0,0,,",
                "2,2,,,,,,,,"),
                rows,
                "a shape, a target, the two resets, the deferral and the mapping are the"
                        + " numbers JSON writes, and a cell is empty where that form writes"
                        + " -1");
        assertEquals(Tunes.multi(tune), Csv.read(Csv.write(Tunes.multi(tune))));
    }

    /** A start's deferral is a cell of version 5: a reader of a file of
     *  version 4 skips the column and reads every start with it clear
     *  (csv.md 3.10). */
    @Test
    void theDeferralIsACellOfThisVersion() {
        String five = """
                ### multi
                format,version,tunes
                ymxs,5,1

                ### tune
                title,composer,writer,rate,rows,repeat
                ,,t,50,2,0

                ### source
                name,repeat
                a tone,0

                ### value
                row,value
                0,13
                1,0

                ### timerA
                row,shape,source,target,prescaler,count,timerReset,placeReset,deferred
                0,0,1,8,50,60,1,1,0
                1,0,1,8,50,60,0,1,1
                """;
        Tune tune = Csv.read(five).tunes().get(0);
        assertTrue(deferred(tune, 1), "a start of version 5 reads its deferral");
        String written = Csv.write(Csv.read(five));
        assertTrue(written.contains("1,0,8,1,50,60,0,1,1,0\n"),
                "the form writes the cell back: " + written);
        assertEquals(Csv.read(five), Csv.read(written), "the form crosses both ways");
        Tune four = Csv.read(five.replace("ymxs,5,1", "ymxs,4,1")).tunes().get(0);
        assertFalse(deferred(four, 1), "a start of version 4 reads with its deferral clear");
    }

    /** The deferral of the start on Timer A at row {@code at} of the
     *  tune. */
    private static boolean deferred(Tune tune, int at) {
        if (!(tune.table().rows().get(at).effects().get(Timer.A) instanceof Start start)) {
            throw new AssertionError("row " + at + " starts Timer A");
        }
        return start.deferred();
    }

    @Test
    void aTimerNoRowStatesOpensNoTable() {
        Tune tune = new Tune("", "", "", 50,
                Tunes.repeating(List.of(Tunes.row(Map.of(Register.R0, 1))), 0));
        assertTrue(!Csv.write(Tunes.multi(tune)).contains("### timer"),
                Csv.write(Tunes.multi(tune)));
    }

    @Test
    void everyShapeCrossesOver() {
        Single square = Tunes.repeating("square 15", List.of(15, 0), 0);
        Single drum = Tunes.once("drum", List.of(8, 12, 15, 13, 5));
        Map<Register, Integer> all = new java.util.EnumMap<>(Register.class);
        for (Register register : Register.values()) {
            all.put(register, Chip.most(register));
        }
        Tune tune = new Tune("a tune", "a composer", "CsvTest", 50, Tunes.repeating(List.of(
                Tunes.row(all),
                new Row(Map.of(), Map.of(
                        Timer.A, Tunes.struck(Tunes.setting(Register.R8), square,
                                Prescaler.BY_4, 122),
                        Timer.D, new Start(new OneConnection(Tunes.setting(Register.R10), drum), new Timing(Prescaler.BY_200, 0, false, true), false, Tunes.NO_PARTS))),
                new Row(Map.of(), Map.of(Timer.A, Tunes.bend(Prescaler.BY_4, 118))),
                new Row(Map.of(Register.R8, 12), Map.of(Timer.A, Tunes.STOP)),
                Tunes.EMPTY), 1));
        Multi multi = Tunes.multi(tune);
        assertEquals(multi, Csv.read(Csv.write(multi)));
        assertEquals(multi, Text.read(Text.write(multi)), "and JSON reads it back too");
    }

    @Test
    void aRowThatSetsNoRegisterIsLeftOutOfTheTable() {
        Tune tune = new Tune("", "", "", 50, Tunes.repeating(List.of(
                Tunes.row(Map.of(Register.R7, 56)), Tunes.EMPTY,
                Tunes.row(Map.of(Register.R7, 49))), 0));
        String csv = Csv.write(Tunes.multi(tune));
        List<String> rows = csv.lines().dropWhile(one -> !one.startsWith("### registers"))
                .skip(2).takeWhile(one -> !one.isBlank()).toList();
        assertEquals(List.of("0,,,,,,,,56,,,,,,", "2,,,,,,,,49,,,,,,"), rows,
                "the row column is the row number, so a row that sets none is left out");
        assertEquals(Tunes.multi(tune), Csv.read(csv), "and it reads back to three rows");
    }

    @Test
    void aCellWithACommaOrAQuoteInItIsQuoted() {
        Tune tune = new Tune("a, \"quoted\", title", "", "", 50,
                Tunes.repeating(List.of(Tunes.EMPTY), 0));
        String csv = Csv.write(Tunes.multi(tune));
        assertTrue(csv.contains("\"a, \"\"quoted\"\", title\""), csv);
        assertEquals(tune.title(), Csv.read(csv).tunes().get(0).title());
    }

    /** A cell with a line feed or a carriage return in it is the one line
     *  of 5.4, each line feed in the text as the two characters {@code \n}
     *  and each carriage return as {@code \r}. */
    @Test
    void aCellWithALineFeedOrACarriageReturnInItIsOneLine() {
        String line = "a value with a line feed or a carriage return in it, which this form"
                + " cannot write: ";
        for (String[] one : new String[][] {{"a\ntitle", "a\\ntitle"},
                {"a\rtitle", "a\\rtitle"}, {"a\r\ntitle", "a\\r\\ntitle"}}) {
            Tune tune = new Tune(one[0], "", "", 50,
                    Tunes.repeating(List.of(Tunes.EMPTY), 0));
            assertEquals(line + one[1], assertThrows(IllegalArgumentException.class,
                    () -> Csv.write(Tunes.multi(tune))).getMessage(), one[1]);
        }
    }

    @Test
    void aTextOfAnotherFormatOrVersionIsTurnedAway() {
        assertThrows(IllegalArgumentException.class,
                () -> Csv.read("### multi\nformat,version,tunes\nymxr,2,0\n"));
        assertThrows(IllegalArgumentException.class,
                () -> Csv.read("### multi\nformat,version,tunes\nymxs,9,0\n"));
    }

    /** A file of the form this replaced, where the name and the columns
     *  were on one line, and a table whose name is its last line. */
    @Test
    void aHeadingThisDoesNotReadIsNamed() {
        IllegalArgumentException old = assertThrows(IllegalArgumentException.class,
                () -> Csv.read("### multi,format,version,tunes\nymxs,4,1\n"));
        assertTrue(String.valueOf(old.getMessage()).contains("has a comma in it"),
                String.valueOf(old.getMessage()));
        IllegalArgumentException alone = assertThrows(IllegalArgumentException.class,
                () -> Csv.read("### multi\n"));
        assertTrue(String.valueOf(alone.getMessage()).contains("names no columns"),
                String.valueOf(alone.getMessage()));
        IllegalArgumentException last = assertThrows(IllegalArgumentException.class,
                () -> Csv.read("### multi\nformat,version,tunes\nymxs,4,1\n\n### tune\n"));
        assertTrue(String.valueOf(last.getMessage()).contains("names no columns"),
                String.valueOf(last.getMessage()));
    }

    @Test
    void aTextWithNoMultiTableIsTurnedAway() {
        IllegalArgumentException no = assertThrows(IllegalArgumentException.class,
                () -> Csv.read("### tune\ntune\n1\n"));
        assertTrue(String.valueOf(no.getMessage()).contains("multi"),
                String.valueOf(no.getMessage()));
    }

    /** A source of several values a row: csv.md 3.6 names its value block
     *  the cells `value2` and `value3`, and a source of one value a row
     *  has the `value` cell alone. */
    @Test
    void aValueBlockCarriesTheValuesOfItsRow() {
        String csv = """
                ### multi
                format,version,tunes
                ymxs,4,1

                ### tune
                title,composer,writer,rate,rows,repeat
                ,,t,50,2,0

                ### source
                name,repeat
                a sweep,0

                ### value
                row,value1,value2,value3
                0,46,1,15
                1,32,1,13

                ### registers
                row,r7
                0,56

                ### timerA
                row,shape,source,target,prescaler,count,timerReset,placeReset
                0,0,1,17,50,60,1,1
                """;
        Multi read = Csv.read(csv);
        Source source = Tunes.sources(read.tunes().get(0)).get(0);
        assertEquals(3, Tunes.columns(source), "the source is three values a row");
        assertEquals(List.of(List.of(46, 1, 15), List.of(32, 1, 13)),
                Tunes.rows(source).rows(), "the rows read as the cells are");
        String written = Csv.write(read);
        assertTrue(written.contains("row,value1,value2,value3"),
                "the block names the cells it has: " + written);
        assertTrue(written.contains("0,46,1,15"), "and the row is its values: " + written);
        assertEquals(read, Csv.read(written), "the form crosses both ways");
        String three = csv.replace("ymxs,4,1", "ymxs,3,1");
        String said = String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Csv.read(three)).getMessage());
        assertTrue(said.contains("version 3 has one value a row"), said);
    }

    /** A file of version 3 reaches the targets 0 to 13, and a target above
     *  13 in one is an error of the form (json.md 2.4, csv.md 5.1). */
    @Test
    void aFileOfVersion3ReachesTargets0To13() {
        String three = """
                ### multi
                format,version,tunes
                ymxs,3,1

                ### tune
                title,composer,writer,rate,rows,repeat
                ,,t,50,2,0

                ### source
                name,repeat
                a tone,0

                ### value
                row,value
                0,13
                1,0

                ### timerA
                row,shape,source,target,prescaler,count,timerReset,placeReset
                0,0,1,8,50,60,1,1
                """;
        assertEquals(1, Csv.read(three).tunes().size(), "a file of version 3 reads");
        String wide = three.replace("0,0,1,8,", "0,0,1,14,");
        assertEquals("target 14, and version 3 reaches 0 to 13",
                assertThrows(IllegalArgumentException.class, () -> Csv.read(wide))
                        .getMessage());
    }

    @Test
    void aColumnIsFoundByItsNameAndNotItsPlace() {
        String csv = """
                ### multi
                version,tunes,format
                4,1,ymxs

                ### tune
                rows,rate,repeat,writer,composer,title
                1,50,0,a writer,a composer,a title

                ### registers
                r0,row
                200,0
                """;
        Tune tune = Csv.read(csv).tunes().get(0);
        assertEquals("a title", tune.title());
        assertEquals(50, tune.rate());
        assertEquals(200, Tunes.rows(tune).get(0).registers().get(Register.R0));
    }

    // ----------------------------------------- transformations, version 7

    /** The fade of SPEC.md 3.6.5 in this form: a transformation block and
     *  an entry block of 16 row lines for each of V15 to V0 in number
     *  order, after the source and before the registers, and a remap a
     *  row line of shape 3 with its mapping cell alone (csv.md 3.1, 3.9,
     *  6.3). */
    @Test
    void aFadeOfSixteenRemapsCrossesTheForm() {
        Multi fade = Tunes.multi(Fade.tune());
        String csv = Csv.write(fade);
        assertEquals(fade, Csv.read(csv), "the fade reads back");
        assertEquals(csv, Csv.write(Csv.read(csv)), "and writes back as it was");
        List<String> named = csv.lines().filter(one -> one.startsWith("###"))
                .map(one -> one.substring(3).strip()).toList();
        List<String> blocks = new java.util.ArrayList<>(List.of("multi", "tune", "source",
                "value"));
        for (int n = 15; n >= 0; n--) {
            blocks.add("transformation");
            blocks.add("entry");
        }
        blocks.addAll(List.of("registers", "timerA"));
        assertEquals(blocks, named);
        List<String> first = csv.lines().dropWhile(one -> !one.equals("### transformation"))
                .takeWhile(one -> !one.isBlank()).toList();
        assertEquals(List.of("### transformation", "name", "V15"), first);
        List<String> entries = csv.lines().dropWhile(one -> !one.equals("### entry"))
                .takeWhile(one -> !one.isBlank()).toList();
        assertEquals(2 + Fade.VOLUMES, entries.size(), "a row line a key");
        assertEquals("key,entry", entries.get(1));
        assertEquals("0,0", entries.get(2));
        assertEquals("15,15", entries.get(17), "V15 writes each key as it is");
        List<String> rows = csv.lines().dropWhile(one -> !one.startsWith("### timerA"))
                .skip(1).takeWhile(one -> !one.isBlank()).toList();
        assertEquals("row,shape,target,source,prescaler,count,timerReset,placeReset,"
                + "deferred,mapping", rows.get(0));
        assertEquals("0,0,17,1,200,192,1,1,0,0", rows.get(1));
        assertEquals("1,3,,,,,,,,0 0 1", rows.get(2));
        assertEquals("16,3,,,,,,,,0 0 16", rows.get(17));
        assertEquals("22,2,,,,,,,,", rows.get(18));
        assertEquals(19, rows.size());
        assertEquals(fade, Text.read(Text.write(Csv.read(csv))), "and crosses to JSON");
    }

    /** 256 row lines of an entry block, entry k the key divided by 2. */
    private static String half() {
        StringBuilder out = new StringBuilder();
        for (int key = 0; key < 256; key++) {
            out.append(key).append(',').append(key / 2).append('\n');
        }
        return out.toString();
    }

    /** A tune of version 7 in this form: a start on setR8 through
     *  transformation 1 and a remap of the mapping of no parts. */
    private static final String SEVEN = """
            ### multi
            format,version,tunes
            ymxs,7,1

            ### tune
            title,composer,writer,rate,rows,repeat
            ,,t,50,2,0

            ### source
            name,repeat
            square,0

            ### value
            row,value
            0,13
            1,0

            ### transformation
            name
            half

            ### entry
            key,entry
            HALF
            ### registers
            row,r7
            0,56

            ### timerA
            row,shape,target,source,prescaler,count,timerReset,placeReset,deferred,mapping
            0,0,8,1,50,60,1,1,0,1
            1,3,,,,,,,,0
            """.replace("HALF\n", half());

    /** The line {@code text} is turned away with. */
    private static String wrong(String text) {
        return String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Csv.read(text)).getMessage());
    }

    @Test
    void aTuneOfVersion7ReadsItsMappings() {
        Tune tune = Csv.read(SEVEN).tunes().get(0);
        YMXS.Transformation half = Tunes.transformations(tune).get(0);
        assertEquals("half", half.name());
        assertEquals(64, half.entries().get(128));
        assertEquals(Tunes.mapping(half), start(tune, 0).mapping());
        assertEquals(new YMXS.Remap(Tunes.NO_PARTS),
                Tunes.rows(tune).get(1).effects().get(Timer.A));
        assertEquals(Csv.read(SEVEN), Csv.read(SEVEN.replace("1,3,,,,,,,,0", "1,3,,,,,,,, 0 ")),
                "white space around the one number is removed");
        assertEquals(Csv.read(SEVEN), Csv.read(SEVEN.replace("0,0,8,1,50,60,1,1,0,1",
                "0,0,8,1,50,60,1,1,0,+1")), "a part is a whole number of 1.7");
        String split = SEVEN.replace("### entry\nkey,entry\n", "### entry\nkey,entry\n")
                .replace("127,63\n", "127,63\n\n### entry\nkey,entry\n");
        assertEquals(Csv.read(SEVEN), Csv.read(split),
                "the entries of two entry blocks continue in file order");
        assertEquals(Text.read(Text.write(Csv.read(SEVEN))), Csv.read(SEVEN),
                "and the tune crosses to JSON");
    }

    /** Each condition of csv.md 5.1 that version 7 adds, as its line, in
     *  the order of csv.md 4.1 steps 5 and 6. */
    @Test
    void eachErrorOfATransformationOrAMappingIsItsLine() {
        assertEquals("tune 1 opens a transformation with 2 rows, and one row opens it",
                wrong(SEVEN.replace("name\nhalf\n", "name\nhalf\nquarter\n")));
        assertEquals("tune 1 opens entries before any transformation",
                wrong(SEVEN.replace("### transformation\nname\nhalf\n\n", "")));
        assertEquals("entry is \"x\", and this form requires a whole number",
                wrong(SEVEN.replace("\n3,1\n", "\n3,x\n")));
        assertEquals("entry is \"\", and this form requires a whole number",
                wrong(SEVEN.replace("key,entry", "key,value")),
                "an entry column absent is the line at the first row line");
        assertEquals("mapping is \"\", and this form requires a whole number a part",
                wrong(SEVEN.replace(",deferred,mapping", ",deferred")),
                "a mapping column absent is an empty cell");
        assertEquals("mapping is \"1 x\", and this form requires a whole number a part",
                wrong(SEVEN.replace("1,1,0,1\n", "1,1,0,1 x\n")));
        assertEquals("mapping is \" \", and this form requires a whole number a part",
                wrong(SEVEN.replace("1,3,,,,,,,,0", "1,3,,,,,,,, ")));
        assertEquals("mapping is \"1.5\", and this form requires a whole number a part",
                wrong(SEVEN.replace("1,1,0,1\n", "1,1,0,1.5\n")));
        assertEquals("mapping is \" 0  0 \", and a mapping of the identity in every part"
                + " is 0", wrong(SEVEN.replace("1,1,0,1\n", "1,1,0, 0  0 \n")));
        assertEquals("row 0 maps through transformation 2, and the tune has 1",
                wrong(SEVEN.replace("1,1,0,1\n", "1,1,0,0 2 -1\n")));
        assertEquals("row 0 maps through transformation -1, and the tune has 1",
                wrong(SEVEN.replace("1,1,0,1\n", "1,1,0,0 -1 2\n")),
                "the numbers are read in order");
        assertEquals("true or false is \"x\", and this form requires a whole number",
                wrong(SEVEN.replace("1,1,0,1\n", "1,1,x,x\n")),
                "a start reads deferred before mapping");
        assertEquals("row 1 sets shape 4 of an effect, and a shape is 0, 1, 2 or 3",
                wrong(SEVEN.replace("1,3,,,,,,,,0", "1,4,,,,,,,,0")));
        assertEquals("tune 1: transformation 1, half: the transformation has 257 entries, and a"
                + " transformation has 1 to 256",
                wrong(SEVEN.replace("\n255,127\n", "\n255,127\n256,128\n")),
                "an error of a transformation is an error of the structure");
        assertEquals("tune 1: transformation 1, half: the transformation has 0 entries, and a"
                + " transformation has 1 to 256",
                wrong(SEVEN.replace("### entry\nkey,entry\n" + half(), "")),
                "a transformation block without an entry block has 0 entries");
        assertEquals(255, Tunes.transformations(Csv.read(SEVEN.replace("\n255,127\n", "\n"))
                .tunes().get(0)).get(0).entries().size(), "255 entries is a transformation");
    }

    /** A source value above the last key of a table of 16 entries is an
     *  error of the structure: the key 16 through entries of keys 0 to 15
     *  (SPEC.md 3.6.5, csv.md 3.12). */
    @Test
    void aKeyAboveTheLastKeyOfATableOf16EntriesIsAnErrorOfTheStructure() {
        StringBuilder sixteen = new StringBuilder();
        for (int key = 0; key < 16; key++) {
            sixteen.append(key).append(',').append(key / 2).append('\n');
        }
        String short16 = SEVEN.replace(half(), sixteen.toString());
        assertEquals(16, Tunes.transformations(Csv.read(short16).tunes().get(0)).get(0)
                .entries().size(), "13 and 0 are keys of 0 to 15");
        assertEquals("tune 1: row 0: Timer A: a source on setR8 whose row 0 is 16, and a key"
                + " is 0 to 15", wrong(short16.replace("\n0,13\n", "\n0,16\n")));
    }

    /** A transformation no row uses is an error of the form, after the
     *  line of an unstarted source (csv.md 4.1 step 8, 5.1). */
    @Test
    void anUnusedTransformationIsAnErrorAfterTheUnstartedSources() {
        String unused = SEVEN.replace("### registers", """
                ### transformation
                name
                spare

                ### registers""");
        assertEquals("transformation 2, spare, is used by no row, and a transformation a"
                + " tune does not map through is dropped where this form is read",
                wrong(unused));
        String both = unused.replace("### transformation\nname\nhalf", """
                ### source
                name,repeat
                rest,

                ### transformation
                name
                half""");
        assertEquals("source 2, rest, is started by no row, and a source a tune does not run"
                + " is dropped where this form is read\n"
                + "transformation 2, spare, is used by no row, and a transformation a tune"
                + " does not map through is dropped where this form is read", wrong(both));
    }

    /**
     * A name with a carriage return in it is in a line with it as the two
     * characters {@code \r}, so the line is one line (csv.md 5.1, 5.3). A
     * reader reads a line feed as the end of a line (2.6), so a name read
     * from this form has none, and CheckTest reads a line feed in a name.
     */
    @Test
    void aNameWithACarriageReturnIsOneLine() {
        String named = SEVEN.replace("name\nhalf\n", "name\na\rb\n");
        assertEquals("a\rb", Tunes.transformations(Csv.read(named).tunes().get(0)).get(0)
                .name(), "the name read has the character itself");
        assertEquals("tune 1: transformation 1, a\\rb: the transformation has 0 entries, and a"
                + " transformation has 1 to 256",
                wrong(named.replace("### entry\nkey,entry\n" + half(), "")));
        assertEquals("source 2, a\\rb, is started by no row, and a source a tune does not run"
                + " is dropped where this form is read",
                wrong(SEVEN.replace("### transformation\nname\nhalf", """
                        ### source
                        name,repeat
                        a\rb,

                        ### transformation
                        name
                        half""")));
        assertEquals("transformation 2, a\\rb, is used by no row, and a transformation a tune"
                + " does not map through is dropped where this form is read",
                wrong(SEVEN.replace("### registers", """
                        ### transformation
                        name
                        a\rb

                        ### registers""")));
        assertEquals("source a\\rb has rows of 1 and of 2 values, and a source has one shape",
                wrong(SEVEN.replace("name,repeat\nsquare,0\n", "name,repeat\na\rb,0\n")
                        .replace("row,value\n0,13\n1,0\n", "row,value,value2\n0,13,\n1,0,1\n")));
    }

    /** A source of 2 values a row on a target that reads 1 is an error of
     *  the form, one unprefixed line raised as the start is read, since
     *  the connection of a start pairs a source with a target of its width
     *  (SPEC.md 1.1, csv.md 5.1). */
    @Test
    void aSourceOfAnotherWidthThanItsTargetIsAnErrorOfTheForm() {
        assertEquals("a source of 2 values a row on setR8, which reads 1",
                wrong(SEVEN.replace("row,value\n0,13\n1,0\n",
                        "row,value1,value2\n0,13,1\n1,0,1\n")));
    }

    /** The blocks of the transformation of {@link #SEVEN}. */
    private static final String BLOCKS = "### transformation\nname\nhalf\n\n### entry\n"
            + "key,entry\n" + half();

    /** A file of version 6 reads as before: its mapping cells are skipped,
     *  every start has the mapping of no parts, and shape 3 is an error of
     *  the form (csv.md 3.10). */
    @Test
    void aFileOfVersion6ReadsAsBefore() {
        String six = SEVEN.replace("ymxs,7,1", "ymxs,6,1").replace("1,3,,,,,,,,0\n", "")
                .replace(BLOCKS, "");
        Tune tune = Csv.read(six).tunes().get(0);
        assertEquals(Tunes.NO_PARTS, start(tune, 0).mapping());
        assertEquals(List.of(), Tunes.transformations(tune));
        assertEquals(Csv.read(six), Csv.read(six.replace("1,1,0,1\n", "1,1,0,x\n")),
                "a malformed mapping cell is skipped");
        assertEquals(Csv.read(six), Csv.read(six.replace(",deferred,mapping\n",
                ",deferred\n").replace("1,1,0,1\n", "1,1,0\n")), "and so is the column");
        assertEquals("row 1 sets shape 3 of an effect, and a shape is 0, 1 or 2",
                wrong(six + "1,3,,,,,,,,0\n"));
        assertEquals("row 1 sets shape 4 of an effect, and a shape is 0, 1 or 2",
                wrong(six + "1,4,,,,,,,,0\n"));
    }

    /** In a file of version 6 a transformation or an entry block is a
     *  block of any other name, an error of the form, as 0.6.0 reported
     *  it (csv.md 3.10, 4.1 step 5, 5.1). */
    @Test
    void aTransformationOrAnEntryBlockIsAnErrorOfAFileOfVersion6() {
        String six = SEVEN.replace("ymxs,7,1", "ymxs,6,1").replace("1,3,,,,,,,,0\n", "");
        assertTrue(six.contains(BLOCKS), six);
        assertEquals("tune 1 opens a \"### transformation\" table, which this form does not"
                + " have", wrong(six));
        assertEquals("tune 1 opens a \"### entry\" table, which this form does not have",
                wrong(six.replace(BLOCKS, "### entry\nkey,entry\n0,x\n\n")),
                "an entry block is the error before its cells are read");
        assertEquals("tune 1 opens a \"### entry\" table, which this form does not have",
                wrong(six.replace(BLOCKS, "### entry\nkey,entry\n0,0\n\n"
                        + "### transformation\nname\nhalf\n\n")),
                "the first in file order is the error");
        assertEquals("tune 1 opens a \"### transformation\" table, which this form does not"
                + " have", wrong(six.replace("ymxs,6,1", "ymxs,5,1")),
                "and in a file of version 5");
    }

    /** A transformation name with a line feed in it is an error of the
     *  emitter (csv.md 5.4). */
    @Test
    void aTransformationNameWithALineFeedInItIsAnError() {
        YMXS.Transformation two = new YMXS.Transformation("two\nlines",
                Fade.volumes(5).entries());
        Tune tune = new Tune("", "", "t", 50, Tunes.repeating(List.of(new Row(Map.of(),
                Map.of(Timer.A, Fade.voice(Fade.NOTE, Tunes.mapping(Tunes.IDENTITY,
                        Tunes.IDENTITY, two))))), 0));
        assertEquals("a value with a line feed or a carriage return in it, which this form"
                + " cannot write: two\\nlines", assertThrows(IllegalArgumentException.class,
                        () -> Csv.write(Tunes.multi(tune))).getMessage());
    }

    /** A transformation of the empty name is a row line of {@code ""},
     *  since a reader skips a blank line, and crosses back (csv.md 2.5,
     *  6.4). */
    @Test
    void aTransformationOfTheEmptyNameCrossesBack() {
        Multi nameless = Csv.read(SEVEN.replace("name\nhalf\n", "name\n\"\"\n"));
        assertEquals("", Tunes.transformations(nameless.tunes().get(0)).get(0).name());
        String written = Csv.write(nameless);
        assertTrue(written.contains("### transformation\nname\n\"\"\n"), written);
        assertEquals(nameless, Csv.read(written));
        assertEquals(written, Csv.write(Csv.read(written)), "and is written again as it was");
    }

    /** White space is the Unicode property White_Space (csv.md 1.3), and a
     *  whole number is of the digits 0 to 9 (1.7). */
    @Test
    void whiteSpaceIsThePropertyWhiteSpaceAndADigitIs0To9() {
        Multi seven = Csv.read(SEVEN);
        for (String white : List.of("\u00a0", "\u0085", "\u2007", "\u202f", "\u3000")) {
            assertEquals(seven, Csv.read(SEVEN.replace("0,13\n", "0,13" + white + "\n")
                    .replace("### source", "### source" + white)
                    .replace("\n\n### value", "\n" + white + "\n### value")),
                    "U+" + Integer.toHexString(white.charAt(0)) + " is white space");
        }
        assertEquals("tune 1: row 0: Timer A: a mapping of 2 parts on setR8, which reads 1",
                wrong(SEVEN.replace("1,1,0,1\n", "1,1,0,1\u00a01\n")),
                "U+00A0 splits a mapping cell");
        assertEquals("value is \"13\u001c\", and this form requires a whole number",
                wrong(SEVEN.replace("0,13\n", "0,13\u001c\n")),
                "U+001C is outside White_Space");
        assertEquals("value is \"\u0661\u0663\", and this form requires a whole number",
                wrong(SEVEN.replace("0,13\n", "0,\u0661\u0663\n")));
        assertEquals("mapping is \"\uff11\", and this form requires a whole number a part",
                wrong(SEVEN.replace("1,1,0,1\n", "1,1,0,\uff11\n")));
        String named = Csv.write(Csv.read(SEVEN.replace("square,0", "\u00a0square,0")));
        assertTrue(named.contains("\n\"\u00a0square\",0\n"), "a name that opens with white"
                + " space is quoted: " + named);
        assertTrue(Csv.write(Csv.read(SEVEN.replace("square,0", "\u001csquare,0")))
                .contains("\n\u001csquare,0\n"), "and one that opens with U+001C is not");
    }

    /** The start on Timer A at row {@code at} of the tune. */
    private static Start start(Tune tune, int at) {
        if (!(tune.table().rows().get(at).effects().get(Timer.A) instanceof Start start)) {
            throw new AssertionError("row " + at + " starts Timer A");
        }
        return start;
    }
}
