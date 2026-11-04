package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import org.ymxs.YMXS.Timing;
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.OneConnection;
import org.ymxs.YMXS.OneTarget;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Remap;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Transformation;
import org.ymxs.YMXS.Tune;

/**
 * The rules a structure must satisfy, and the fault reported where it does
 * not.
 *
 * <p>The records perform no check, so a structure is as it was built and
 * this reads it. One call reports every fault in a tune rather than the
 * first.
 */
final class CheckTest {

    /** A tune with four things wrong: the rate, a register value, a count,
     *  and a source whose values are past what fits the target. */
    private static Tune broken() {
        Single loud = Tunes.repeating("loud", List.of(200, 0), 0);
        return new Tune("", "", "", 0, Tunes.repeating(List.of(
                Tunes.row(Map.of(Register.R8, 99)),
                new Row(Map.of(), Map.of(Timer.A,
                        new Start(new OneConnection(Tunes.setting(Register.R8), loud), new Timing(Prescaler.BY_4, 400, true, true), false, Tunes.NO_PARTS)))), 0));
    }

    @Test
    void oneCallNamesEverythingThatIsWrong() {
        assertEquals(List.of(
                "a rate of 0: a player is called at least once a second",
                "row 0: R8 is 0 to 31, and this row sets it to 99",
                "row 1: Timer A: a count of 400: a timer's data register is a byte,"
                        + " 0 to 255, and 0 counts 256",
                "row 1: Timer A: a source on setR8 whose row 0 is 200,"
                        + " and the target is 0 to 31"),
                Check.of(broken()));
    }

    @Test
    void aTuneWithNoFaultInItIsPassedInSilence() {
        Tune tune = new Tune("a tune", "", "", 50, Tunes.repeating(List.of(
                Tunes.row(Map.of(Register.R8, 15)), Tunes.EMPTY), 0));
        assertEquals(List.of(), Check.of(tune));
        assertEquals(List.of(), Check.of(Tunes.multi(tune)));
    }

    @Test
    void mustThrowsWithAllOfItRatherThanTheFirst() {
        IllegalArgumentException no = assertThrows(IllegalArgumentException.class,
                () -> Check.must(broken()));
        String said = String.valueOf(no.getMessage());
        assertEquals(4, said.lines().count(), said);
        assertTrue(said.contains("a rate of 0") && said.contains("a count of 400"), said);
    }

    @Test
    void aMultiNamesWhichTuneIsWrong() {
        List<String> said = Check.of(new YMXS.Multi(List.of(broken())));
        assertTrue(said.get(0).startsWith("tune 1: "), said.toString());
        assertEquals(4, said.size(), said.toString());
    }

    @Test
    void aMultiOfNoTunesIsSaidToBeOne() {
        assertEquals(List.of("a multi of no tunes: a host plays one"),
                Check.of(new YMXS.Multi(List.of())));
    }

    // ------------------------------------- the rules of SPEC.md 6

    private static final Single SQUARE = Tunes.repeating("square", List.of(15, 0), 0);
    private static final Single OTHER = Tunes.repeating("other", List.of(12, 0), 0);
    private static final Single LONGER = Tunes.repeating("longer", List.of(15, 8, 0), 0);
    private static final Single DRUM = Tunes.once("drum", List.of(8, 12, 15, 13));

    /** The suffix of a line that rests on the reckoning (SPEC.md 6.4). */
    private static final String RECKONED = ", which rests on how long a source that plays"
            + " once runs, reckoned from its rate";

    /** A tune of these rows, at 50 Hz, repeating to row 0. */
    private static Tune of(Row... rows) {
        return new Tune("", "", "", 50, Tunes.repeating(List.of(rows), 0));
    }

    private static Row starts(Single source, boolean placeReset) {
        return new Row(Map.of(), Map.of(Timer.A, new Start(new OneConnection(Tunes.setting(Register.R8), source), new Timing(Prescaler.BY_4, 100, true, placeReset), false, Tunes.NO_PARTS)));
    }

    @Test
    void aRowThatSetsARegisterAnEffectRunsOnIsSaid() {
        Tune tune = of(starts(SQUARE, true), Tunes.row(Map.of(Register.R8, 12)));
        assertEquals(List.of("row 1: Timer A runs on R8, and this row sets it"),
                Check.writing(tune));
    }

    @Test
    void theRowThatStopsTheEffectMaySetIt() {
        Tune tune = of(starts(SQUARE, true),
                new Row(Map.of(Register.R8, 12), Map.of(Timer.A, Tunes.STOP)));
        assertEquals(List.of(), Check.writing(tune),
                "the row that stops it sets the register back");
    }

    @Test
    void anEffectOnTheEnvelopeShapeLeavesTheRowFree() {
        Single buzzer = Tunes.repeating("buzzer", List.of(10), 0);
        Tune tune = of(new Row(Map.of(), Map.of(Timer.A, new Start(new OneConnection(Tunes.setting(Register.R13), buzzer), new Timing(Prescaler.BY_4, 100, true, true), false, Tunes.NO_PARTS))),
                Tunes.row(Map.of(Register.R13, 9)));
        assertEquals(List.of(), Check.writing(tune),
                "the frame's write to R13 restarts the envelope alongside the ticks'");
    }

    @Test
    void aStartWithoutThePlaceResetOnATimerThatHasRunNoSourceIsSaid() {
        assertEquals(List.of("row 0: Timer A starts a source without the place's reset,"
                + " and this timer has run none: the place is where the player left it"),
                Check.writing(of(starts(SQUARE, false))));
    }

    @Test
    void aSourceOfTheRowCountBeforeItMayLeaveThePlaceWhereItIs() {
        Tune tune = of(starts(SQUARE, true), starts(OTHER, false));
        assertEquals(List.of(), Check.writing(tune),
                "two sources of two rows on one target: the wave keeps its phase");
    }

    @Test
    void aSourceOfAnotherRowCountTakesThePlaceModuloItsRows() {
        Tune tune = of(starts(SQUARE, true), starts(LONGER, false));
        assertEquals(List.of(), Check.writing(tune),
                "place 1 of two rows is place 1 of three (SPEC.md 3.4.3)");
    }

    @Test
    void aStartOnAnotherTargetKeepsThePlaceToo() {
        Row elsewhere = new Row(Map.of(), Map.of(Timer.A, new Start(new OneConnection(Tunes.setting(Register.R9), OTHER), new Timing(Prescaler.BY_4, 100, true, false), false, Tunes.NO_PARTS)));
        Tune tune = of(starts(SQUARE, true), elsewhere);
        assertEquals(List.of(), Check.writing(tune),
                "the place is a row number of the source, on any target");
    }

    /** A start of {@code source} on {@code register} on Timer A, at 2
     *  ticks a frame and both resets set: a source of two rows runs out in
     *  two frames by the reckoning (SPEC.md 6.4). */
    private static Row slow(Register register, Single source, boolean deferred) {
        return new Row(Map.of(), Map.of(Timer.A, new Start(
                new OneConnection(Tunes.setting(register), source),
                new Timing(Prescaler.BY_200, 200, true, true), deferred, Tunes.NO_PARTS)));
    }

    /** A deferred start of {@code source} on {@code register} on Timer A, at
     *  one frame for every source of fifteen rows or fewer. */
    private static Row deferring(Register register, Single source) {
        return new Row(Map.of(), Map.of(Timer.A, new Start(
                new OneConnection(Tunes.setting(register), source),
                new Timing(Prescaler.BY_4, 100, true, true), true, Tunes.NO_PARTS)));
    }

    /** A deferred start waits for the source it follows to play once: the
     *  square runs on R8 to row 3 by the reckoning, and the drum on R9 from
     *  there (SPEC.md 6.3, 6.4). */
    @Test
    void aDeferredStartBeginsWhereTheEffectBeforeItRunsOut() {
        Tune tune = of(slow(Register.R8, SQUARE, false), deferring(Register.R9, DRUM),
                Tunes.row(Map.of(Register.R8, 12)), Tunes.row(Map.of(Register.R9, 12)),
                Tunes.EMPTY);
        assertEquals(List.of(
                "row 2: Timer A runs on R8, and this row sets it" + RECKONED,
                "row 3: Timer A runs on R9, and this row sets it" + RECKONED),
                Check.writing(tune));
    }

    @Test
    void aDeferredStartOnAnIdleTimerStartsAtItsRow() {
        Tune tune = of(deferring(Register.R8, SQUARE), Tunes.row(Map.of(Register.R8, 12)));
        assertEquals(List.of("row 1: Timer A runs on R8, and this row sets it"),
                Check.writing(tune), "no effect runs to defer it to");
    }

    @Test
    void aStopDiscardsThePendingStart() {
        Tune tune = of(slow(Register.R8, SQUARE, false), deferring(Register.R9, DRUM),
                new Row(Map.of(), Map.of(Timer.A, Tunes.STOP)),
                Tunes.row(Map.of(Register.R9, 12)));
        assertEquals(List.of(), Check.writing(tune), "the drum never begins");
    }

    @Test
    void aStartDiscardsThePendingStart() {
        Tune tune = of(slow(Register.R8, SQUARE, false), deferring(Register.R9, LONGER),
                slow(Register.R10, DRUM, false), Tunes.row(Map.of(Register.R9, 12)));
        assertEquals(List.of(), Check.writing(tune),
                "the drum replaces the square, and the longer wave never begins");
    }

    @Test
    void aSecondDeferredStartReplacesTheFirst() {
        Tune tune = of(slow(Register.R8, SQUARE, false), deferring(Register.R9, DRUM),
                deferring(Register.R10, DRUM),
                Tunes.row(Map.of(Register.R9, 12, Register.R10, 12)), Tunes.EMPTY);
        assertEquals(List.of("row 3: Timer A runs on R10, and this row sets it" + RECKONED),
                Check.writing(tune));
    }

    @Test
    void aRetuneLeavesTheStartPending() {
        Tune tune = of(slow(Register.R8, SQUARE, false), deferring(Register.R9, DRUM),
                new Row(Map.of(), Map.of(Timer.A, Tunes.bend(Prescaler.BY_200, 190))),
                Tunes.row(Map.of(Register.R9, 12)), Tunes.EMPTY);
        assertEquals(List.of("row 3: Timer A runs on R9, and this row sets it" + RECKONED),
                Check.writing(tune), "the retune is of the square, which still runs");
    }

    /** Two timers on one register meet where the deferred start begins, a
     *  row reckoned from the rate of the effect it follows. */
    @Test
    void aDeferredStartMeetsAnotherTimerWhereItBegins() {
        Row first = new Row(Map.of(), Map.of(
                Timer.A, new Start(new OneConnection(Tunes.setting(Register.R9), OTHER),
                        new Timing(Prescaler.BY_200, 200, true, true), false, Tunes.NO_PARTS),
                Timer.B, new Start(new OneConnection(Tunes.setting(Register.R8), SQUARE),
                        new Timing(Prescaler.BY_4, 100, true, true), false, Tunes.NO_PARTS)));
        Tune tune = of(first, deferring(Register.R8, DRUM), Tunes.EMPTY, Tunes.EMPTY);
        assertEquals(List.of("row 3: Timer A starts on R8, where Timer B runs: rule 2 leaves"
                + " the order of two timers writing one register to the writer" + RECKONED),
                Check.writing(tune));
    }

    @Test
    void aRetuneOfAnIdleEffectIsSaid() {
        Tune tune = of(new Row(Map.of(), Map.of(Timer.A, Tunes.bend(Prescaler.BY_4, 90))));
        assertEquals(List.of("row 0: Timer A retunes an effect that is idle: a rate"
                + " written to a timer with no source on it starts that timer with no"
                + " source to run"), Check.writing(tune));
    }

    @Test
    void aPlayOnceSourceThatIsOverLeavesTheRegisterToTheRows() {
        // four rows at 4 x 100 are over inside one frame of a 50 Hz tune
        Row start = new Row(Map.of(), Map.of(Timer.A, new Start(new OneConnection(Tunes.setting(Register.R8), DRUM), new Timing(Prescaler.BY_4, 100, true, true), false, Tunes.NO_PARTS)));
        assertEquals(1, Chip.frames(4, Prescaler.BY_4, 100, 50));
        assertEquals(List.of(), Check.writing(of(start, Tunes.row(Map.of(Register.R8, 12)))),
                "the source has run out, so the register is the rows' again");
    }

    @Test
    void whatRestsOnHowLongAPlayOnceSourceRunsSaysSo() {
        // four rows at 200 x 200 run about four frames of a 50 Hz tune
        Row start = new Row(Map.of(), Map.of(Timer.A, new Start(new OneConnection(Tunes.setting(Register.R8), DRUM), new Timing(Prescaler.BY_200, 200, true, true), false, Tunes.NO_PARTS)));
        Tune tune = of(start, Tunes.row(Map.of(Register.R8, 12)));
        List<String> said = Check.writing(tune);
        assertEquals(1, said.size(), said.toString());
        assertTrue(said.get(0).endsWith("reckoned from its rate"), said.get(0));
        assertEquals(4, Chip.frames(4, Prescaler.BY_200, 200, 50),
                "the frames the reckoning comes to");
    }

    @Test
    void aTuneThatKeepsTheRulesIsPassedInSilence() {
        Tune tune = of(starts(SQUARE, true), Tunes.EMPTY,
                new Row(Map.of(), Map.of(Timer.A, Tunes.bend(Prescaler.BY_4, 90))),
                new Row(Map.of(Register.R8, 12), Map.of(Timer.A, Tunes.STOP)));
        assertEquals(List.of(), Check.writing(tune));
    }

    @Test
    void aRateNo68000ServicesIsAnError() {
        // 2,457,600 over 4 x 1 is 614,400 ticks a second
        Tune tune = of(new Row(Map.of(), Map.of(Timer.A,
                new Start(new OneConnection(Tunes.setting(Register.R8), SQUARE), new Timing(Prescaler.BY_4, 1, true, true), false, Tunes.NO_PARTS))));
        assertEquals(List.of("row 0: Timer A: a rate of 614400 ticks a second: a 68000 at"
                + " 8 MHz enters an interrupt and leaves it in 64 cycles, so 125000 a"
                + " second is every cycle it has"), Check.of(tune));
        assertEquals(125000, Chip.MOST_TICKS, "the ticks a second the cycles come to");
    }

    @Test
    void theSlowestRateAnEffectRunsAtIsNoError() {
        Tune tune = of(new Row(Map.of(), Map.of(Timer.A,
                new Start(new OneConnection(Tunes.setting(Register.R8), SQUARE), new Timing(Prescaler.BY_200, 0, true, true), false, Tunes.NO_PARTS))));
        assertEquals(List.of(), Check.of(tune),
                "prescaler 200 with a count of 0 is 48 ticks a second");
    }

    @Test
    void theRowsOfOneRunAreReportedAsOneLine() {
        Row[] rows = new Row[12];
        rows[0] = starts(SQUARE, true);
        for (int at = 1; at < rows.length; at++) {
            rows[at] = Tunes.row(Map.of(Register.R8, 12));
        }
        assertEquals(List.of("rows 1 to 11: Timer A runs on R8 from row 0, and 11 of them"
                + " set it"), Check.writing(of(rows)));
    }

    @Test
    void aSecondTimerOnOneRegisterIsSaidWhereItStarts() {
        Row second = new Row(Map.of(), Map.of(Timer.B, new Start(new OneConnection(Tunes.setting(Register.R8), OTHER), new Timing(Prescaler.BY_4, 100, true, true), false, Tunes.NO_PARTS)));
        assertEquals("row 1: Timer B starts on R8, where Timer A runs: rule 2 leaves the"
                + " order of two timers writing one register to the writer",
                Check.writing(of(starts(SQUARE, true), second)).get(0));
    }

    @Test
    void anEffectRunningAtTheWrapIsSaid() {
        assertEquals(List.of("the tune repeats to row 1, and Timer A runs on R8 when its"
                + " last row has played: the wrap resumes with the timer running from the"
                + " pass before"),
                Check.writing(new Tune("", "", "", 50,
                        Tunes.repeating(List.of(starts(SQUARE, true), Tunes.EMPTY), 1))));
        assertEquals(List.of(), Check.writing(of(starts(SQUARE, true))),
                "the row the tune repeats to starts it again");
    }

    @Test
    void aStopOfATimerThisTuneHasNotStartedIsSaid() {
        Row stop = new Row(Map.of(), Map.of(Timer.A, Tunes.STOP));
        assertEquals(List.of("row 1: Timer A stops an effect this timer has not started"),
                Check.writing(of(Tunes.EMPTY, stop)));
        assertEquals(List.of(), Check.writing(of(stop, Tunes.EMPTY)),
                "the row the tune repeats to stops every effect, started or not");
        assertEquals(List.of(), Check.writing(of(Tunes.EMPTY,
                new Row(Map.of(), Map.of(Timer.A, new Start(new OneConnection(Tunes.setting(Register.R8), DRUM), new Timing(Prescaler.BY_4, 100, true, true), false, Tunes.NO_PARTS))), Tunes.EMPTY, stop)),
                "a source that has run out by the reckoning is stopped by rule 4");
    }

    @Test
    void aSourceNoRowStartsIsSaidOfTheFormThatDeclaredIt() {
        Tune tune = of(starts(SQUARE, true));
        assertEquals(List.of(), Check.declared(List.of(SQUARE), tune));
        assertEquals(List.of("source 2, other, is started by no row, and a source a tune"
                + " does not run is dropped where this form is read"),
                Check.declared(List.of(SQUARE, OTHER), tune));
    }

    @Test
    void twoSourcesUnderOneNameAreNoFault() {
        Single twin = Tunes.repeating("square", List.of(12, 0), 0);
        Tune tune = of(starts(SQUARE, true),
                new Row(Map.of(), Map.of(Timer.B, new Start(new OneConnection(Tunes.setting(Register.R9), twin), new Timing(Chip.prescaler(4), 100, true, true), false, Tunes.NO_PARTS))));
        assertEquals(List.of(), Check.declared(List.of(SQUARE, twin), tune),
                "an effect names its source by a number, so two sources may share a name");
    }

    /** The lines SPEC.md 6.6 quotes of the check of that tune, in the
     *  order the clause reports them in. */
    private static List<String> quotedBySpec() throws IOException {
        List<String> lines = java.nio.file.Files.readAllLines(Path.of("doc/SPEC.md"));
        int from = -1;
        for (int at = 0; at < lines.size(); at++) {
            if (lines.get(at).startsWith("**6.6 ")) {
                from = at;
            }
        }
        assertTrue(from >= 0, "SPEC.md has no clause 6.6");
        List<String> said = new ArrayList<>();
        for (int at = from; at < lines.size(); at++) {
            String line = lines.get(at);
            if (line.startsWith("    ") && !line.isBlank()) {
                said.add(line.strip());
            } else if (!said.isEmpty()) {
                break;
            }
        }
        return said;
    }

    @Test
    void everyTuneOfTheDocumentsKeepsTheRulesButTheOneThatDoesNot() throws IOException {
        int read = 0;
        for (Path at : java.nio.file.Files.list(Path.of("doc", "tunes")).sorted().toList()) {
            if (!at.toString().endsWith(".json")) {
                continue;
            }
            read++;
            YMXS.Multi multi = Text.read(java.nio.file.Files.readString(at));
            List<String> said = new ArrayList<>();
            for (Tune tune : multi.tunes()) {
                said.addAll(Check.writing(tune));
            }
            if (at.getFileName().toString().equals("warnings.json")) {
                // four rules broken, six lines, and SPEC.md 6.6 quotes them
                assertEquals(quotedBySpec(), said,
                        at + " reports other lines than SPEC.md 6.6 quotes");
                continue;
            }
            assertEquals(List.of(), said, at + " keeps every rule");
        }
        final int all = read;
        assertTrue(all >= 6, () -> "only " + all + " tunes read; the check is asleep");
    }

    @Test
    void aTableRepeatingPastItsLastRowIsSaid() {
        Tune tune = new Tune("", "", "", 50,
                new YMXS.Table<>(List.of(Tunes.EMPTY), java.util.OptionalInt.of(4)));
        assertEquals(List.of("the tune has 1 rows and repeats to row 4"), Check.of(tune));
    }

    // ------------------------------------- transformations and remaps

    /** A transformation of {@code entries}. */
    private static Transformation transformation(String name, IntUnaryOperator entry,
                                                 int entries) {
        List<Integer> out = new ArrayList<>();
        for (int key = 0; key < entries; key++) {
            out.add(entry.applyAsInt(key));
        }
        return new Transformation(name, out);
    }

    /** A start of {@code source} on {@code target} through {@code mapping},
     *  at 64 ticks a second and both resets set. */
    private static Start mapped(OneTarget target, Single source, YMXS.Mapping mapping) {
        return new Start(new OneConnection(target, source),
                new Timing(Prescaler.BY_200, 192, true, true), false, mapping);
    }

    /** Keys of a byte through a transformation of 256 entries, on a
     *  register of five bits: a key is 0 to the last key of the
     *  transformation on every target, and through the identity the value
     *  is 0 to the register's most (SPEC.md 3.6.5). */
    @Test
    void aKeyAboveTheRegistersMostIsWrittenThroughATransformation() {
        Single keys = Tunes.repeating("keys", List.of(0, 64, 128, 255), 0);
        Transformation levels = transformation("levels", key -> key / 16, 256);
        Tune through = of(new Row(Map.of(), Map.of(Timer.A,
                mapped(Tunes.setting(Register.R8), keys, Tunes.mapping(levels)))));
        assertEquals(List.of(), Check.of(through), "every key reaches an entry of 0 to 15");
        Tune bare = of(new Row(Map.of(), Map.of(Timer.A,
                mapped(Tunes.setting(Register.R8), keys, Tunes.NO_PARTS))));
        assertEquals(List.of(
                "row 0: Timer A: a source on setR8 whose row 1 is 64, and the target is 0 to 31",
                "row 0: Timer A: a source on setR8 whose row 2 is 128, and the target is 0 to"
                        + " 31",
                "row 0: Timer A: a source on setR8 whose row 3 is 255, and the target is 0 to"
                        + " 31"), Check.of(bare), "through the identity a value is a volume");
        Tune past = of(new Row(Map.of(), Map.of(Timer.A, mapped(Tunes.setting(Register.R8),
                Tunes.repeating("keys", List.of(0, 256), 0), Tunes.mapping(levels)))));
        assertEquals(List.of("row 0: Timer A: a source on setR8 whose row 1 is 256, and a key"
                + " is 0 to 255"), Check.of(past));
        Tune loud = of(new Row(Map.of(), Map.of(Timer.A, mapped(Tunes.setting(Register.R8),
                keys, Tunes.mapping(transformation("same", key -> key, 256))))));
        assertEquals(List.of(
                "row 0: Timer A: value 0 of row 1 maps to 64 on setR8, and the target is 0 to"
                        + " 31",
                "row 0: Timer A: value 0 of row 2 maps to 128 on setR8, and the target is 0 to"
                        + " 31",
                "row 0: Timer A: value 0 of row 3 maps to 255 on setR8, and the target is 0 to"
                        + " 31"), Check.of(loud), "an entry is 0 to the register's most");
    }

    /** Through {@code V15} of 16 entries a key is 0 to 15, its last key,
     *  where through the identity setR8 reads 0 to 31 (SPEC.md 3.6.1,
     *  3.6.5). */
    @Test
    void aKeyAboveTheLastKeyOfATableOf16EntriesIsAnErrorOfAStart() {
        Single keys = Tunes.repeating("keys", List.of(0, 15, 16), 0);
        assertEquals(16, Fade.volumes(15).entries().size());
        assertEquals(List.of("row 0: Timer A: a source on setR8 whose row 2 is 16, and a key is"
                + " 0 to 15"), Check.of(of(new Row(Map.of(), Map.of(Timer.A,
                mapped(Tunes.setting(Register.R8), keys, Tunes.mapping(Fade.volumes(15))))))));
        assertEquals(List.of(), Check.of(of(new Row(Map.of(), Map.of(Timer.A,
                mapped(Tunes.setting(Register.R8), keys, Tunes.NO_PARTS))))),
                "16 is a volume of setR8 through the identity");
    }

    /**
     * A remap through tables of 16 entries against the start on its
     * timer, at a row before it and at a row after it: value 1 through
     * {@code up}, entry k k + 16, maps above the 15 of R1, and value 2,
     * 16, is above the last key of {@code V10}. The lines come in row
     * order and within a row in value order, prefixed with the row of the
     * start (SPEC.md 1.12 item 2.4.2.4).
     */
    @Test
    void aKeyAboveTheLastKeyOfATableOf16EntriesIsAnErrorOfARemap() {
        Transformation up = transformation("up", key -> key + 16, 16);
        YMXS.Triple chord = new YMXS.Triple("chord", Tunes.repeating(List.of(
                new YMXS.Three(28, 1, 16), new YMXS.Three(28, 1, 12)), 0));
        Row start = new Row(Map.of(), Map.of(Timer.A, Fade.voice(chord, Tunes.NO_PARTS)));
        Row remap = new Row(Map.of(), Map.of(Timer.A, new Remap(Tunes.mapping(Tunes.IDENTITY,
                up, Fade.volumes(10)))));
        List<String> lines = List.of(
                "value 1 of row 0 maps to 17 on setVoiceA, and the target is 0 to 15",
                "a source on setVoiceA whose row 0 is 16, and a key is 0 to 15",
                "value 1 of row 1 maps to 17 on setVoiceA, and the target is 0 to 15");
        assertEquals(lines.stream().map(line -> "row 1: Timer A: start of row 0: " + line)
                .toList(), Check.of(of(start, remap)), "after the start");
        assertEquals(lines.stream().map(line -> "row 0: Timer A: start of row 1: " + line)
                .toList(), Check.of(of(remap, start)), "before the start");
    }

    /**
     * Two sources of equal rows under two names are two sources (SPEC.md
     * 1.9), so a remap reads its mapping against each, and the lines of
     * each are prefixed with the row of the first start that connects it
     * (SPEC.md 1.12 item 2.4.2.4). A connection is read once, at that
     * first start.
     */
    @Test
    void aRemapAgainstTwoSourcesOfEqualRowsPrefixesTheRowOfEachStart() {
        Single a = Tunes.repeating("a", List.of(16), 0);
        Single b = Tunes.repeating("b", List.of(16), 0);
        Row remap = new Row(Map.of(), Map.of(Timer.A, new Remap(Tunes.mapping(
                Fade.volumes(15)))));
        Tune tune = of(new Row(Map.of(), Map.of(Timer.A, mapped(Tunes.setting(Register.R8), a,
                        Tunes.NO_PARTS))),
                new Row(Map.of(), Map.of(Timer.A, mapped(Tunes.setting(Register.R8), b,
                        Tunes.NO_PARTS))),
                remap,
                new Row(Map.of(), Map.of(Timer.A, mapped(Tunes.setting(Register.R8), a,
                        Tunes.NO_PARTS))));
        assertEquals(List.of(
                "row 2: Timer A: start of row 0: a source on setR8 whose row 0 is 16, and a key"
                        + " is 0 to 15",
                "row 2: Timer A: start of row 1: a source on setR8 whose row 0 is 16, and a key"
                        + " is 0 to 15"), Check.of(tune));
        assertEquals("tune 1: row 2: Timer A: start of row 0: a source on setR8 whose row 0"
                + " is 16, and a key is 0 to 15", Check.of(Tunes.multi(tune)).get(0),
                "the prefixes accumulate");
    }

    /** A start of {@code source} on {@code target} through the mapping of
     *  no parts, at 819 ticks a second and both resets set. */
    private static Start starting(YMXS.Target target, Source source) {
        return Tunes.starting(target, source, Prescaler.BY_50, 60, true, true, false,
                Tunes.NO_PARTS);
    }

    /** A tune of these effects, one map a row, at 50 Hz, repeating to row
     *  0. */
    private static Tune effects(List<Map<Timer, YMXS.Effect>> effects) {
        return of(effects.stream().map(one -> new Row(Map.of(), one)).toArray(Row[]::new));
    }

    /** A tune of 12 rows with errors in remaps on Timers A, B and D: several
     *  targets and connections on each timer, a connection started at two
     *  rows, a remap before a later start, and equal mappings at several
     *  rows. */
    private static Tune remapped() {
        Single lo = Tunes.repeating("lo", List.of(3, 20), 0);
        Single hi = Tunes.repeating("hi", List.of(40, 5), 0);
        YMXS.Pair tone = new YMXS.Pair("tone", Tunes.repeating(List.of(new YMXS.Two(300, 16),
                new YMXS.Two(10, 2)), 0));
        YMXS.Triple note = new YMXS.Triple("note", Tunes.repeating(List.of(
                new YMXS.Three(1, 1, 12), new YMXS.Three(1, 1, 20)), 0));
        YMXS.Pair envelope = new YMXS.Pair("env", Tunes.repeating(List.of(
                new YMXS.Two(255, 0)), 0));
        Remap up = new Remap(Tunes.mapping(transformation("up", key -> key + 20, 16)));
        Remap big = new Remap(Tunes.mapping(transformation("big", key -> key, 256)));
        Remap v15 = new Remap(Tunes.mapping(Fade.volumes(15)));
        Remap voice = new Remap(Tunes.mapping(Tunes.IDENTITY, Tunes.IDENTITY,
                Fade.volumes(15)));
        OneTarget r8 = Tunes.setting(Register.R8);
        OneTarget r10 = Tunes.setting(Register.R10);
        Map<Timer, Map<Integer, YMXS.Effect>> plan = Map.of(
                Timer.A, Map.of(0, starting(r8, lo), 1, up,
                        2, starting(new YMXS.SetTone(YMXS.Voice.A), tone), 3, up, 5, up,
                        6, new Remap(Tunes.mapping(Tunes.IDENTITY, Fade.volumes(15))), 8, up),
                Timer.B, Map.of(0, starting(new YMXS.SetVoice(YMXS.Voice.B), note),
                        4, voice, 7, starting(r8, hi), 8, big, 9, voice, 11, big),
                Timer.D, Map.of(0, starting(r10, lo), 1, starting(r8, lo),
                        2, new Remap(Tunes.NO_PARTS), 3, up, 4, starting(r10, lo), 5, v15,
                        6, starting(new YMXS.SetEnvelope(), envelope), 7, up, 10, v15));
        List<Map<Timer, YMXS.Effect>> rows = new ArrayList<>();
        for (int at = 0; at < 12; at++) {
            rows.add(new EnumMap<>(Timer.class));
        }
        plan.forEach((timer, row) -> row.forEach((at, effect) -> rows.get(at).put(timer,
                effect)));
        return effects(rows);
    }

    /**
     * A remap is read against every start on its timer at any row: the part
     * counts once a target, then each connection once, under the row of its
     * first start, in source-row and value order (SPEC.md 1.12). A tune
     * check reads each mapping once a timer.
     */
    @Test
    void remapsOnThreeTimersReadEveryTargetAndConnectionOnTheirTimer() {
        String toneA = "a mapping of 1 parts on setToneA, which reads 2";
        String envelope = "a mapping of 1 parts on setEnvelope, which reads 2";
        String twenty = " whose row 1 is 20, and a key is 0 to 15";
        String a0 = "Timer A: start of row 0: a source on setR8" + twenty;
        String d0 = "Timer D: start of row 0: a source on setR10" + twenty;
        String d1 = "Timer D: start of row 1: a source on setR8" + twenty;
        String b0 = "Timer B: start of row 0: a source on setVoiceB" + twenty;
        String b7 = "Timer B: start of row 7: value 0 of row 0 maps to 40 on setR8, and the"
                + " target is 0 to 31";
        assertEquals(List.of(
                "row 1: Timer A: " + toneA,
                "row 1: " + a0,
                "row 2: Timer A: a source on setToneA whose row 0 is 300, and the target is 0"
                        + " to 255",
                "row 2: Timer A: a source on setToneA whose row 0 is 16, and the target is 0"
                        + " to 15",
                "row 3: Timer A: " + toneA,
                "row 3: " + a0,
                "row 3: Timer D: " + envelope,
                "row 3: " + d0,
                "row 3: " + d1,
                "row 4: Timer B: a mapping of 3 parts on setR8, which reads 1",
                "row 4: " + b0,
                "row 5: Timer A: " + toneA,
                "row 5: " + a0,
                "row 5: Timer D: " + envelope,
                "row 5: " + d0,
                "row 5: " + d1,
                "row 6: Timer A: a mapping of 2 parts on setR8, which reads 1",
                "row 6: Timer A: start of row 2: value 0 of row 0 maps to 300 on setToneA, and"
                        + " the target is 0 to 255",
                "row 6: Timer A: start of row 2: a source on setToneA whose row 0 is 16, and a"
                        + " key is 0 to 15",
                "row 7: Timer B: a source on setR8 whose row 0 is 40, and the target is 0 to"
                        + " 31",
                "row 7: Timer D: " + envelope,
                "row 7: " + d0,
                "row 7: " + d1,
                "row 8: Timer A: " + toneA,
                "row 8: " + a0,
                "row 8: Timer B: a mapping of 1 parts on setVoiceB, which reads 3",
                "row 8: " + b7,
                "row 9: Timer B: a mapping of 3 parts on setR8, which reads 1",
                "row 9: " + b0,
                "row 10: Timer D: " + envelope,
                "row 10: " + d0,
                "row 10: " + d1,
                "row 11: Timer B: a mapping of 1 parts on setVoiceB, which reads 3",
                "row 11: " + b7), Check.of(remapped()));
    }

    /** A note of {@code rows} rows on a voice: tone fine {@code step} a row
     *  modulo 256, then tone coarse and volume the row modulo 16. */
    private static YMXS.Triple note(String name, int rows, int step) {
        List<YMXS.Three> values = new ArrayList<>();
        for (int at = 0; at < rows; at++) {
            values.add(new YMXS.Three(at * step % 256, at % 16, at % 16));
        }
        return new YMXS.Triple(name, Tunes.repeating(values, 0));
    }

    /** A tune of 16,384 rows, 128 patterns of 128 rows. Timers A, B and D
     *  start a source every 8 rows, A and B on a voice and D on R10, and
     *  each remaps it three times in each 8 rows through one of four
     *  transformations, 18,432 remaps. The tune has no fault. */
    private static Tune song() {
        YMXS.Triple lead = note("lead", 512, 3);
        List<List<YMXS.Triple>> voices = List.of(List.of(lead, note("pad", 384, 5)),
                List.of(note("bass", 640, 7), lead));
        List<Integer> levels = new ArrayList<>();
        for (int at = 0; at < 256; at++) {
            levels.add(at % 16);
        }
        Single drum = Tunes.repeating("drum", levels, 0);
        List<Transformation> volumes = List.of(Fade.volumes(15), Fade.volumes(11),
                Fade.volumes(7), Fade.volumes(3));
        List<Map<Timer, YMXS.Effect>> rows = new ArrayList<>();
        for (int at = 0; at < 16384; at++) {
            rows.add(new EnumMap<>(Timer.class));
        }
        for (int block = 0; block < rows.size() / 8; block++) {
            Map<Timer, YMXS.Effect> starts = rows.get(block * 8);
            starts.put(Timer.A, starting(new YMXS.SetVoice(YMXS.Voice.A),
                    voices.get(0).get(block % 2)));
            starts.put(Timer.B, starting(new YMXS.SetVoice(YMXS.Voice.B),
                    voices.get(1).get(block % 2)));
            starts.put(Timer.D, starting(Tunes.setting(Register.R10), drum));
            for (int k = 0; k < 3; k++) {
                Transformation volume = volumes.get((block * 3 + k) % volumes.size());
                Map<Timer, YMXS.Effect> remaps = rows.get(block * 8 + 2 + 2 * k);
                Remap voice = new Remap(Tunes.mapping(Tunes.IDENTITY, Tunes.IDENTITY,
                        volume));
                remaps.put(Timer.A, voice);
                remaps.put(Timer.B, voice);
                remaps.put(Timer.D, new Remap(Tunes.mapping(volume)));
            }
        }
        return effects(rows);
    }

    /**
     * A tune check reads the rows of the tune for starts once a timer with
     * a remap, and each start on it once. It reads each mapping of the
     * remaps on a timer once against the connections of the timer: four
     * mappings, each over lead and pad on Timer A, 896 rows, over bass and
     * lead on Timer B, 1,152 rows, and over drum on Timer D, 256 rows. A
     * check that read the starts at every remap would read 18,432 × 2,048
     * starts and 6,144 × 2,304 source rows.
     */
    @Test
    void aTuneCheckReadsEachStartOnceAndEachMappingOnceATimer() {
        Check.Work work = new Check.Work();
        assertEquals(List.of(), Check.of(song(), work), "the song reads clean");
        assertEquals(List.of(3 * 16384, 6144, 4 * (896 + 1152 + 256)),
                List.of(work.rows, work.starts, work.sources),
                "the rows, the starts and the source rows read");
    }

    /** A tune of 4,096 starts on Timer A of 64-row sources on R8, all named
     *  s and each of other rows, and a remap after every 64th start where
     *  {@code remaps} is set. */
    private static Tune distinct(boolean remaps) {
        List<Map<Timer, YMXS.Effect>> rows = new ArrayList<>();
        for (int i = 0; i < 4096; i++) {
            List<Integer> values = new ArrayList<>(java.util.Collections.nCopies(61, 0));
            values.addAll(List.of(i / 256 % 16, i / 16 % 16, i % 16));
            rows.add(new EnumMap<>(Map.of(Timer.A, starting(Tunes.setting(Register.R8),
                    Tunes.repeating("s", values, 0)))));
            rows.add(new EnumMap<>(Timer.class));
            if (remaps && i % 64 == 0) {
                rows.getLast().put(Timer.A, new Remap(Tunes.NO_PARTS));
            }
        }
        return effects(rows);
    }

    /** Without a remap a tune check leaves the starts on a timer unread.
     *  With one, it reads each of 4,096 starts once, and the mapping of no
     *  parts once over the 4,096 connections. */
    @Test
    void distinctSourcesOfOneNameAndRowCountAreReadOnce() {
        Check.Work work = new Check.Work();
        Check.of(distinct(false), work);
        assertEquals(List.of(0, 0, 0), List.of(work.rows, work.starts, work.sources),
                "a tune without a remap");
        work = new Check.Work();
        assertEquals(List.of(), Check.of(distinct(true), work), "the tune reads clean");
        assertEquals(List.of(8192, 4096, 4096 * 64),
                List.of(work.rows, work.starts, work.sources),
                "the rows, the starts and the source rows read");
    }

    /**
     * A transformation of 0 entries and one of 257 have the line of their
     * count under their number, and a value through either has no key
     * line, through a start or a remap (SPEC.md 1.11, 1.12 item 2.3); 300
     * is above 256, the entries of the second less one.
     */
    @Test
    void aTransformationOf0Or257EntriesHasTheLineOfItsCountAlone() {
        Single keys = Tunes.repeating("keys", List.of(0, 300), 0);
        for (int entries : List.of(0, 257)) {
            Transformation odd = transformation("odd", key -> key % 256, entries);
            Tune tune = of(new Row(Map.of(), Map.of(Timer.A,
                            mapped(Tunes.setting(Register.R8), keys, Tunes.mapping(odd)))),
                    new Row(Map.of(), Map.of(Timer.A, new Remap(Tunes.mapping(odd)))));
            assertEquals(List.of("transformation 1, odd: the transformation has " + entries
                    + " entries, and a transformation has 1 to 256"), Check.of(tune));
            assertEquals(List.of("the transformation has " + entries + " entries, and a"
                    + " transformation has 1 to 256"), Check.of(odd));
        }
        assertEquals(List.of(), Check.of(transformation("", key -> key, 255)),
                "255 entries is a transformation");
        assertEquals(List.of(), Check.of(transformation("", key -> key, 200)),
                "and 200 is one too");
    }

    /**
     * A name with a line feed or a carriage return in it is in a line with
     * each as the two characters {@code \n} or {@code \r}, so the line is
     * one line: in the prefix of a transformation, and in the line of a
     * source and of a transformation a form declares and no row uses
     * (SPEC.md 1.12, json.md 8.1, csv.md 5.1).
     */
    @Test
    void aNameWithALineFeedOrACarriageReturnIsOneLine() {
        assertEquals("a\\nb\\rc\\n", Check.named("a\nb\rc\n"));
        assertEquals("a \"b\" \\ c", Check.named("a \"b\" \\ c"),
                "a quotation mark and a backslash are as they are");
        for (String[] one : new String[][] {{"a\nb", "a\\nb"}, {"a\rb", "a\\rb"}}) {
            Transformation odd = new Transformation(one[0], List.of());
            assertEquals(List.of("transformation 1, " + one[1] + ": the transformation has 0"
                    + " entries, and a transformation has 1 to 256"),
                    Check.of(of(new Row(Map.of(), Map.of(Timer.A,
                            mapped(Tunes.setting(Register.R8), SQUARE, Tunes.mapping(odd)))))));
            assertEquals(List.of("source 2, " + one[1] + ", is started by no row, and a"
                    + " source a tune does not run is dropped where this form is read",
                    "transformation 1, " + one[1] + ", is used by no row, and a transformation"
                    + " a tune does not map through is dropped where this form is read"),
                    Check.declared(List.of(SQUARE, Tunes.repeating(one[0], List.of(1), 0)),
                            List.of(new Transformation(one[0], List.of(0))),
                            of(starts(SQUARE, true))));
        }
    }

    /** A table of one entry has the key 0 alone: a value of 1 is above its
     *  last key, and the entry 40 of key 0 is above the 31 of R8. A start
     *  reports the key line in the pass before the line of the value
     *  mapped (SPEC.md 1.12 item 2.4.2.3). */
    @Test
    void aTableOfOneEntryHasTheKey0Alone() {
        Transformation one = new Transformation("one", List.of(40));
        assertEquals(List.of(), Check.of(one));
        assertEquals(40, Tunes.written(one, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> Tunes.written(one, 1));
        assertEquals(List.of(
                "row 0: Timer A: a source on setR8 whose row 1 is 1, and a key is 0 to 0",
                "row 0: Timer A: value 0 of row 0 maps to 40 on setR8, and the target is 0 to"
                        + " 31"), Check.of(of(new Row(Map.of(), Map.of(Timer.A,
                mapped(Tunes.setting(Register.R8), Tunes.repeating("keys", List.of(0, 1), 0),
                        Tunes.mapping(one)))))));
    }

    /**
     * A table of 16 entries whose entry 1 is 300 has the line of that
     * entry under its number, and a value through it is still read: key 0
     * reaches the entry 40, above the 31 of R8, key 1 reaches the entry
     * outside 0 to 255 and is skipped, and 20 is above the last key, 15.
     * A start and a remap read it alike (SPEC.md 1.12).
     */
    @Test
    void aTableOfOneEntryOutsideAByteReadsTheEntriesOfTheOtherKeys() {
        Transformation bad = new Transformation("v", new ArrayList<>(
                transformation("", key -> key - 2, 16).entries()));
        bad.entries().set(0, 40);
        bad.entries().set(1, 300);
        Single keys = Tunes.repeating("s", List.of(0, 1, 20), 0);
        Tune tune = of(new Row(Map.of(), Map.of(Timer.A, mapped(Tunes.setting(Register.R8),
                        keys, Tunes.mapping(bad)))),
                new Row(Map.of(), Map.of(Timer.A, new Remap(Tunes.mapping(bad)))));
        assertEquals(List.of(
                "transformation 1, v: entry 1 is 300, and an entry is 0 to 255",
                "row 0: Timer A: a source on setR8 whose row 2 is 20, and a key is 0 to 15",
                "row 0: Timer A: value 0 of row 0 maps to 40 on setR8, and the target is 0 to"
                        + " 31",
                "row 1: Timer A: start of row 0: value 0 of row 0 maps to 40 on setR8, and the"
                        + " target is 0 to 31",
                "row 1: Timer A: start of row 0: a source on setR8 whose row 2 is 20, and a key"
                        + " is 0 to 15"), Check.of(tune));
    }

    /** A table of 16 entries and one of 256 under one name, the first 16
     *  entries of the two equal, are two transformations of the tune
     *  (SPEC.md 3.6.2). */
    @Test
    void aTableOf16EntriesAndOneOf256OfOneNameAreTwoTransformations() {
        Transformation short16 = transformation("V15", key -> key, 16);
        Transformation long256 = transformation("V15", key -> key, 256);
        assertEquals(short16.entries(), long256.entries().subList(0, 16));
        assertTrue(!short16.equals(long256), "the entries differ in number");
        Tune tune = of(new Row(Map.of(), Map.of(Timer.A,
                        mapped(Tunes.setting(Register.R8), SQUARE, Tunes.mapping(short16)))),
                new Row(Map.of(), Map.of(Timer.A, new Remap(Tunes.mapping(long256)))),
                new Row(Map.of(), Map.of(Timer.A, new Remap(Tunes.mapping(short16)))));
        assertEquals(List.of(short16, long256), Tunes.transformations(tune));
        assertEquals(1, Tunes.number(Tunes.transformations(tune), short16));
        assertEquals(2, Tunes.number(Tunes.transformations(tune), long256));
        assertEquals(List.of(), Check.of(tune));
    }

    /**
     * Checks report order (SPEC.md 1.12): transformation errors before
     * row errors; source and key checks before mapped-value checks at a
     * start; part counts per target before value checks per connection
     * at a remap. Remap errors include the start-row prefix. The
     * invalid entry count of {@code broken} suppresses dependent key
     * checks.
     */
    @Test
    void theErrorsOfTransformationsAndMappingsComeInTheOrderOfOneTwelve() {
        Transformation broken = new Transformation("broken", new ArrayList<>(
                transformation("", key -> key % 256, 257).entries()));
        broken.entries().set(3, 300);
        broken.entries().set(7, -1);
        Transformation same = transformation("same", key -> key, 256);
        Transformation sixteen = transformation("sixteen", key -> key, 16);
        Single loud = Tunes.repeating("loud", List.of(5, 300), 0);
        Single under = Tunes.repeating("under", List.of(-3), 0);
        Single high = Tunes.repeating("high", List.of(40, 2), 0);
        YMXS.Triple chord = new YMXS.Triple("chord", Tunes.repeating(List.of(
                new YMXS.Three(300, 1, 40), new YMXS.Three(10, 20, 20)), 0));
        YMXS.Mapping two = Tunes.mapping(Tunes.IDENTITY, Tunes.IDENTITY);
        Tune tune = new Tune("", "", "", 0, Tunes.repeating(List.of(
                new Row(Map.of(Register.R8, 99), Map.of(
                        Timer.A, new Start(
                                new YMXS.ThreeConnection(new YMXS.SetVoice(YMXS.Voice.A), chord),
                                new Timing(Prescaler.BY_200, 400, true, true), false,
                                Tunes.mapping(Tunes.IDENTITY, same, same)),
                        Timer.B, mapped(Tunes.setting(Register.R8), loud,
                                Tunes.mapping(broken)),
                        Timer.C, mapped(Tunes.setting(Register.R9), under, two))),
                new Row(Map.of(), Map.of(Timer.A, new Remap(two),
                        Timer.D, new Remap(Tunes.NO_PARTS))),
                new Row(Map.of(), Map.of(Timer.A,
                        mapped(Tunes.setting(Register.R10), high, Tunes.NO_PARTS),
                        Timer.B, mapped(Tunes.setting(Register.R9),
                                Tunes.repeating("keys", List.of(16), 0),
                                Tunes.mapping(sixteen)))),
                new Row(Map.of(), Map.of(Timer.A, new Remap(Tunes.mapping(Tunes.IDENTITY,
                        Tunes.IDENTITY, sixteen))))), 0));
        assertEquals(List.of(same, broken, sixteen), Tunes.transformations(tune),
                "Timer A maps through same before Timer B through broken, and sixteen"
                        + " comes at row 2");
        assertEquals(List.of(
                "a rate of 0: a player is called at least once a second",
                "transformation 2, broken: the transformation has 257 entries, and a"
                        + " transformation has 1 to 256",
                "transformation 2, broken: entry 3 is 300, and an entry is 0 to 255",
                "transformation 2, broken: entry 7 is -1, and an entry is 0 to 255",
                "row 0: R8 is 0 to 31, and this row sets it to 99",
                "row 0: Timer A: a count of 400: a timer's data register is a byte, 0 to 255,"
                        + " and 0 counts 256",
                "row 0: Timer A: a source on setVoiceA whose row 0 is 300, and the target is 0"
                        + " to 255",
                "row 0: Timer A: value 2 of row 0 maps to 40 on setVoiceA, and the target is 0"
                        + " to 31",
                "row 0: Timer A: value 1 of row 1 maps to 20 on setVoiceA, and the target is 0"
                        + " to 15",
                "row 0: Timer C: row 0 is -3, and a register is 0 upward",
                "row 0: Timer C: a mapping of 2 parts on setR9, which reads 1",
                "row 1: Timer A: a mapping of 2 parts on setVoiceA, which reads 3",
                "row 1: Timer A: a mapping of 2 parts on setR10, which reads 1",
                "row 2: Timer A: a source on setR10 whose row 0 is 40, and the target is 0 to"
                        + " 31",
                "row 2: Timer B: a source on setR9 whose row 0 is 16, and a key is 0 to 15",
                "row 3: Timer A: a mapping of 3 parts on setR10, which reads 1",
                "row 3: Timer A: start of row 0: value 0 of row 0 maps to 300 on setVoiceA,"
                        + " and the target is 0 to 255",
                "row 3: Timer A: start of row 0: a source on setVoiceA whose row 0 is 40, and a"
                        + " key is 0 to 15",
                "row 3: Timer A: start of row 0: value 1 of row 1 maps to 20 on setVoiceA, and"
                        + " the target is 0 to 15",
                "row 3: Timer A: start of row 0: a source on setVoiceA whose row 1 is 20, and a"
                        + " key is 0 to 15"),
                Check.of(tune));
    }

    /** The fade of SPEC.md 3.6.5 and the start that maps the volume of
     *  setVoiceA alone keep every condition and every rule. */
    @Test
    void aFadeKeepsEveryConditionAndEveryRule() {
        for (Tune tune : List.of(Fade.tune(), Fade.wide())) {
            assertEquals(List.of(), Check.of(tune), tune.title());
            assertEquals(List.of(), Check.writing(tune), tune.title());
        }
    }

    /** A remap of SQUARE on setR8 through the identity, a row of the
     *  remap on Timer A. */
    private static Row remaps() {
        return new Row(Map.of(), Map.of(Timer.A, new Remap(Tunes.NO_PARTS)));
    }

    /** The rule 5 line of a remap (SPEC.md 6.5). */
    private static final String IDLE = "remaps an effect that is idle: a mapping is set on a"
            + " running effect, and this row leaves the timer as it is";

    @Test
    void aRemapOfAnIdleEffectIsSaid() {
        assertEquals(List.of("row 0: Timer A " + IDLE), Check.writing(of(remaps())),
                "before the first start");
        assertEquals(List.of("row 2: Timer A " + IDLE), Check.writing(of(starts(SQUARE, true),
                new Row(Map.of(), Map.of(Timer.A, Tunes.STOP)), remaps())), "after a stop");
        Row drum = new Row(Map.of(), Map.of(Timer.A, new Start(
                new OneConnection(Tunes.setting(Register.R8), DRUM),
                new Timing(Prescaler.BY_4, 100, true, true), false, Tunes.NO_PARTS)));
        assertEquals(List.of("row 2: Timer A " + IDLE + RECKONED),
                Check.writing(of(drum, Tunes.EMPTY, remaps())),
                "after its source has run out by the reckoning");
    }

    /** A remap leaves the run of the effect as it was: the rows that set
     *  its register before it and after it are one line (SPEC.md 6.3
     *  step 2(d)). */
    @Test
    void aRemapLeavesTheRunAsItWas() {
        Row sets = Tunes.row(Map.of(Register.R8, 12));
        assertEquals(List.of("rows 1 to 3: Timer A runs on R8 from row 0, and 2 of them set"
                + " it"), Check.writing(of(starts(SQUARE, true), sets, remaps(), sets)));
    }

    @Test
    void aRemapLeavesTheStartPending() {
        Tune tune = of(slow(Register.R8, SQUARE, false), deferring(Register.R9, DRUM),
                remaps(), Tunes.row(Map.of(Register.R9, 12)), Tunes.EMPTY);
        assertEquals(List.of("row 3: Timer A runs on R9, and this row sets it" + RECKONED),
                Check.writing(tune), "the remap is of the square, which still runs");
    }

    /** Rule 1(d): a remap at the repeat row leaves the effect running from
     *  the pass before, so its line is reported, where a retune there is
     *  an operation on the timer (SPEC.md 6.3 step 5). */
    @Test
    void aRemapAtTheRepeatRowLeavesTheWrapToTheRule() {
        assertEquals(List.of("the tune repeats to row 1, and Timer A runs on R8 when its"
                + " last row has played: the wrap resumes with the timer running from the"
                + " pass before"),
                Check.writing(new Tune("", "", "", 50,
                        Tunes.repeating(List.of(starts(SQUARE, true), remaps()), 1))));
        assertEquals(List.of(), Check.writing(new Tune("", "", "", 50,
                Tunes.repeating(List.of(starts(SQUARE, true), new Row(Map.of(),
                        Map.of(Timer.A, Tunes.bend(Prescaler.BY_4, 90)))), 1))));
    }

    /** An effect runs on every register its target writes, its mapping
     *  changing the values written alone (SPEC.md 6.2(b)): a start that
     *  maps the volume of setVoiceA runs on R8 for rules 1 and 2. */
    @Test
    void anEffectThroughAMappingRunsOnTheRegistersOfItsTarget() {
        Tune one = of(new Row(Map.of(), Map.of(Timer.A, Fade.voice(Fade.NOTE, Fade.volume(5)))),
                Tunes.row(Map.of(Register.R8, 12)));
        List<String> said = Check.writing(one);
        assertEquals(1, said.size(), said.toString());
        assertTrue(said.get(0).startsWith("row 1: Timer A runs on R")
                && said.get(0).endsWith(", and this row sets it"), said.get(0));
        Tune two = of(new Row(Map.of(), Map.of(Timer.A, Fade.voice(Fade.NOTE, Fade.volume(5)))),
                new Row(Map.of(), Map.of(Timer.B, mapped(Tunes.setting(Register.R8), SQUARE,
                        Tunes.mapping(Fade.volumes(10))))),
                new Row(Map.of(), Map.of(Timer.B, Tunes.STOP)));
        assertEquals(List.of("row 1: Timer B starts on R8, where Timer A runs: rule 2 leaves"
                + " the order of two timers writing one register to the writer"),
                Check.writing(two));
    }
}
