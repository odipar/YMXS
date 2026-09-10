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
 * The table form against the text form. Both write the same structure, so
 * a tune written one way and read the other is the tune it was.
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
    void theTableFormReadsBackAsItStands() throws IOException {
        Path at = Path.of("doc/tunes/circus.csv");
        String csv = Files.readString(at);
        assertEquals(csv, Csv.write(Csv.read(csv)), at + " does not write back as it reads");
        assertEquals(Text.read(Files.readString(Path.of("doc/tunes/circus.json"))),
                Csv.read(csv), "the two forms of one tune hold one structure");
    }

    @Test
    void aClassLineNamesTheStructureWhoseRowsComeNext() throws IOException {
        List<String> named = Files.readString(Path.of("doc/tunes/circus.csv")).lines()
                .filter(one -> one.startsWith(Csv.CLASS))
                .map(one -> one.substring(Csv.CLASS.length()))
                .toList();
        assertEquals(List.of("multi", "tune", "source", "run", "start", "retune", "stop"),
                named);
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
                Tunes.NOTHING), 1));
        Multi multi = Tunes.multi(tune);
        assertEquals(multi, Csv.read(Csv.write(multi)));
        assertEquals(multi, Text.read(Text.write(multi)), "and the text form holds it too");
    }

    @Test
    void aRunStatesTheRowsSinceTheOneBeforeIt() {
        // R7 on rows 0, 1 and 3: two runs, the second a gap of one past the
        // end of the first
        Tune tune = new Tune("", "", "", 50, Tunes.repeating(List.of(
                Tunes.row(Map.of(Register.R7, 56)), Tunes.row(Map.of(Register.R7, 49)),
                Tunes.NOTHING, Tunes.row(Map.of(Register.R7, 56))), 0));
        String csv = Csv.write(Tunes.multi(tune));
        assertTrue(csv.contains("1###r7###0###56###49\n1###r7###1###56\n"), csv);
        assertEquals(Tunes.multi(tune), Csv.read(csv), "and it reads back to the same rows");
    }

    @Test
    void aValueHoldingTheDelimiterIsTurnedAway() {
        Tune tune = new Tune("a ### title", "", "", 50,
                Tunes.repeating(List.of(Tunes.NOTHING), 0));
        IllegalArgumentException no = assertThrows(IllegalArgumentException.class,
                () -> Csv.write(Tunes.multi(tune)));
        assertTrue(String.valueOf(no.getMessage()).contains("a value this form cannot hold"),
                String.valueOf(no.getMessage()));
    }

    @Test
    void aTextOfAnotherFormatOrVersionIsTurnedAway() {
        assertThrows(IllegalArgumentException.class,
                () -> Csv.read("class;multi\nformat###version###tunes\nymxr###1###0\n"));
        assertThrows(IllegalArgumentException.class,
                () -> Csv.read("class;multi\nformat###version###tunes\nymxs###9###0\n"));
    }

    @Test
    void aTextWithNoMultiBlockIsTurnedAway() {
        IllegalArgumentException no = assertThrows(IllegalArgumentException.class,
                () -> Csv.read("class;tune\ntune\n1\n"));
        assertTrue(String.valueOf(no.getMessage()).contains("class;multi"),
                String.valueOf(no.getMessage()));
    }
}
