package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

/**
 * The rules a structure must satisfy, and the fault reported where it does
 * not.
 *
 * <p>The records perform no check, so a structure is whatever it is and
 * this reads it. One call reports every fault in a tune rather than the
 * first.
 */
final class CheckTest {

    /** A tune with four things wrong: the rate, a register value, a count,
     *  and a source whose values are past what fits the target. */
    private static Tune broken() {
        Source loud = Tunes.repeating("loud", List.of(200, 0), 0);
        return new Tune("", "", "", 0, Tunes.repeating(List.of(
                Tunes.row(Map.of(Register.R8, 99)),
                new Row(Map.of(), Map.of(Timer.A,
                        new Start(Tunes.setting(Register.R8), loud, Prescaler.BY_4, 400,
                                true, true)))), 0));
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

    private static final Source SQUARE = Tunes.repeating("square", List.of(15, 0), 0);
    private static final Source OTHER = Tunes.repeating("other", List.of(12, 0), 0);
    private static final Source LONGER = Tunes.repeating("longer", List.of(15, 8, 0), 0);
    private static final Source DRUM = Tunes.once("drum", List.of(8, 12, 15, 13));

    /** A tune of these rows, at 50 Hz, repeating to row 0. */
    private static Tune of(Row... rows) {
        return new Tune("", "", "", 50, Tunes.repeating(List.of(rows), 0));
    }

    private static Row starts(Source source, boolean placeReset) {
        return new Row(Map.of(), Map.of(Timer.A, new Start(Tunes.setting(Register.R8),
                source, Prescaler.BY_4, 100, true, placeReset)));
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
        Source buzzer = Tunes.repeating("buzzer", List.of(10), 0);
        Tune tune = of(new Row(Map.of(), Map.of(Timer.A, new Start(
                        Tunes.setting(Register.R13), buzzer, Prescaler.BY_4, 100, true, true))),
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
    void aSourceOfAnotherRowCountMayNot() {
        Tune tune = of(starts(SQUARE, true), starts(LONGER, false));
        assertEquals(List.of("row 1: Timer A starts a source of 3 rows without the place's"
                + " reset, and the one before it had 2"), Check.writing(tune));
    }

    @Test
    void aStartOnAnotherTargetMayNot() {
        Row elsewhere = new Row(Map.of(), Map.of(Timer.A, new Start(
                Tunes.setting(Register.R9), OTHER, Prescaler.BY_4, 100, true, false)));
        Tune tune = of(starts(SQUARE, true), elsewhere);
        assertEquals(List.of("row 1: Timer A starts a source on setR9 without the place's"
                + " reset, and this timer last ran on setR8"), Check.writing(tune));
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
        Row start = new Row(Map.of(), Map.of(Timer.A, new Start(Tunes.setting(Register.R8),
                DRUM, Prescaler.BY_4, 100, true, true)));
        assertEquals(1, Chip.frames(4, Prescaler.BY_4, 100, 50));
        assertEquals(List.of(), Check.writing(of(start, Tunes.row(Map.of(Register.R8, 12)))),
                "the source has run out, so the register is the rows' again");
    }

    @Test
    void whatRestsOnHowLongAPlayOnceSourceRunsSaysSo() {
        // four rows at 200 x 200 run about four frames of a 50 Hz tune
        Row start = new Row(Map.of(), Map.of(Timer.A, new Start(Tunes.setting(Register.R8),
                DRUM, Prescaler.BY_200, 200, true, true)));
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
                new Start(Tunes.setting(Register.R8), SQUARE, Prescaler.BY_4, 1,
                        true, true))));
        assertEquals(List.of("row 0: Timer A: a rate of 614400 ticks a second: a 68000 at"
                + " 8 MHz enters an interrupt and leaves it in 64 cycles, so 125000 a"
                + " second is every cycle it has"), Check.of(tune));
        assertEquals(125000, Chip.MOST_TICKS, "the ticks a second the cycles come to");
    }

    @Test
    void theSlowestRateAnEffectRunsAtIsNoError() {
        Tune tune = of(new Row(Map.of(), Map.of(Timer.A,
                new Start(Tunes.setting(Register.R8), SQUARE, Prescaler.BY_200, 0,
                        true, true))));
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
        Row second = new Row(Map.of(), Map.of(Timer.B, new Start(Tunes.setting(Register.R8),
                OTHER, Prescaler.BY_4, 100, true, true)));
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
                new Row(Map.of(), Map.of(Timer.A, new Start(Tunes.setting(Register.R8),
                        DRUM, Prescaler.BY_4, 100, true, true))), Tunes.EMPTY, stop)),
                "a source that has run out by the reckoning is stopped where rule 4 stands");
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
    void twoSourcesUnderOneNameAreSaidOfTheFormThatDeclaredThem() {
        Source twin = Tunes.repeating("square", List.of(12, 0), 0);
        assertEquals(List.of("sources 1 and 2 are both named square, and their rows"
                + " differ"),
                Check.declared(List.of(SQUARE, twin), of(starts(SQUARE, true),
                        new Row(Map.of(), Map.of(Timer.B, new Start(
                                Tunes.setting(Register.R9), twin, Prescaler.BY_4, 100,
                                true, true))))));
    }

    /** The tunes of doc/tunes, which a writer of this repository wrote, and
     *  the one written to break the rules. A rule that fired on the rest
     *  would be one no writer can keep. */
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
                assertEquals(6, said.size(), at + " breaks four rules: " + said);
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
}
