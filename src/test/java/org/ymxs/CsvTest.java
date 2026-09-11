package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.ymxs.YMXS.Multi;
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
        assertEquals(List.of("multi", "tune", "rows"), named,
                "circus is rows alone, so those are the three tables it opens");
        int rows = lines.indexOf("### rows");
        assertEquals(List.of("row", "r0", "r1", "r2", "r3", "r4", "r5", "r6",
                "r7", "r8", "r9", "r10", "r11", "r12", "r13"),
                Csv.cells(lines.get(rows + 1)),
                "a row of a tune is a row here, with a column a register");
    }

    /** The point of the two lines: a column name stands over the cells it
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
                .dropWhile(one -> !one.startsWith("### rows"))
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
                "rows", "timerA", "timerD"), named,
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
                "a source belongs to the tune whose tables it stands under, not to its name");
        assertEquals(two, Tunes.sources(back.tunes().get(1)).get(0));
    }

    @Test
    void theTablesOfOneTuneStandTogether() throws IOException {
        Multi multi = Text.read(Files.readString(Path.of("doc/tunes/two-tunes.json")));
        List<String> named = Csv.write(multi).lines().filter(one -> one.startsWith("###"))
                .map(one -> one.substring(3).strip()).toList();
        assertEquals(List.of("multi",
                "tune", "source", "value", "source", "value", "source", "value",
                        "rows", "timerD",
                "tune", "source", "value", "rows", "timerD"), named,
                "a tune opens its tables and the next tune opens the next");
    }

    @Test
    void aTimerHoldsWhatTheTextFormHoldsOfIt() {
        Source square = Tunes.repeating("square", List.of(15, 0), 0);
        Tune tune = new Tune("", "", "", 50, Tunes.repeating(List.of(
                new Row(Map.of(), Map.of(Timer.A, Tunes.struck(
                        Tunes.setting(Register.R8), square, Prescaler.BY_4, 100))),
                new Row(Map.of(), Map.of(Timer.A, Tunes.bend(Prescaler.BY_4, 90))),
                new Row(Map.of(), Map.of(Timer.A, Tunes.STOP))), 0));
        List<String> rows = Csv.write(Tunes.multi(tune)).lines()
                .dropWhile(one -> !one.startsWith("### timerA"))
                .skip(2).takeWhile(one -> !one.isBlank()).toList();
        assertEquals(List.of(
                "0,0,8,1,4,100,1,1",
                "1,1,,,4,90,0,0",
                "2,2,,,,,,"),
                rows,
                "a shape, a target and the two resets are the numbers JSON writes,"
                        + " and a cell is empty where that form writes -1");
        assertEquals(Tunes.multi(tune), Csv.read(Csv.write(Tunes.multi(tune))));
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
        Source square = Tunes.repeating("square 15", List.of(15, 0), 0);
        Source drum = Tunes.once("drum", List.of(8, 12, 15, 13, 5));
        Map<Register, Integer> all = new java.util.EnumMap<>(Register.class);
        for (Register register : Register.values()) {
            all.put(register, Chip.most(register));
        }
        Tune tune = new Tune("a tune", "a composer", "CsvTest", 50, Tunes.repeating(List.of(
                Tunes.row(all),
                new Row(Map.of(), Map.of(
                        Timer.A, Tunes.struck(Tunes.setting(Register.R8), square,
                                Prescaler.BY_4, 122),
                        Timer.D, new Start(Tunes.setting(Register.R10), drum,
                                Prescaler.BY_200, 256, false, true))),
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
        List<String> rows = csv.lines().dropWhile(one -> !one.startsWith("### rows"))
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

    @Test
    void aCellWithALineFeedInItIsAnError() {
        Tune tune = new Tune("a\ntitle", "", "", 50,
                Tunes.repeating(List.of(Tunes.EMPTY), 0));
        IllegalArgumentException no = assertThrows(IllegalArgumentException.class,
                () -> Csv.write(Tunes.multi(tune)));
        assertTrue(String.valueOf(no.getMessage()).contains("a line feed"),
                String.valueOf(no.getMessage()));
    }

    @Test
    void aTextOfAnotherFormatOrVersionIsTurnedAway() {
        assertThrows(IllegalArgumentException.class,
                () -> Csv.read("### multi\nformat,version,tunes\nymxr,1,0\n"));
        assertThrows(IllegalArgumentException.class,
                () -> Csv.read("### multi\nformat,version,tunes\nymxs,9,0\n"));
    }

    /** A file of the form this replaced, where the name and the columns
     *  stood on one line, and a table whose name is its last line. */
    @Test
    void aHeadingThisDoesNotReadIsNamed() {
        IllegalArgumentException old = assertThrows(IllegalArgumentException.class,
                () -> Csv.read("### multi,format,version,tunes\nymxs,1,1\n"));
        assertTrue(String.valueOf(old.getMessage()).contains("has a comma in it"),
                String.valueOf(old.getMessage()));
        IllegalArgumentException alone = assertThrows(IllegalArgumentException.class,
                () -> Csv.read("### multi\n"));
        assertTrue(String.valueOf(alone.getMessage()).contains("names no columns"),
                String.valueOf(alone.getMessage()));
        IllegalArgumentException last = assertThrows(IllegalArgumentException.class,
                () -> Csv.read("### multi\nformat,version,tunes\nymxs,1,1\n\n### tune\n"));
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

    @Test
    void aColumnIsFoundByItsNameAndNotItsPlace() {
        String csv = """
                ### multi
                version,tunes,format
                1,1,ymxs

                ### tune
                frames,rate,repeat,writer,composer,title
                1,50,0,a writer,a composer,a title

                ### rows
                r0,row
                200,0
                """;
        Tune tune = Csv.read(csv).tunes().get(0);
        assertEquals("a title", tune.title());
        assertEquals(50, tune.rate());
        assertEquals(200, Tunes.rows(tune).get(0).registers().get(Register.R0));
    }
}
