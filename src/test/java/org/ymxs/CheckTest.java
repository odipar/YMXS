package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * What a structure has to satisfy, and what is said where it does not.
 *
 * <p>The records hold no check of their own, so a structure states what it
 * states and this reads it. One call names everything wrong with a tune
 * rather than the first of it, which is what a writer mending one wants.
 */
final class CheckTest {

    /** A tune with four things wrong: the rate, a register value, a count,
     *  and a source whose values the target does not take. */
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
                "row 0: R8 takes 0 to 31, and this row sets it to 99",
                "row 1: Timer A: a count of 400: a timer counts 1 to 256",
                "row 1: Timer A: a source on setR8 whose row 0 is 200,"
                        + " and the target takes 0 to 31"),
                Check.of(broken()));
    }

    @Test
    void aTuneWithNothingWrongSaysNothing() {
        Tune tune = new Tune("a tune", "", "", 50, Tunes.repeating(List.of(
                Tunes.row(Map.of(Register.R8, 15)), Tunes.NOTHING), 0));
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

    // ------------------------------ what SPEC.md 6 asks of a writer

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
                "the row that stops it takes the register back");
    }

    @Test
    void anEffectOnTheEnvelopeShapeHoldsNothingAgainstTheRow() {
        Source buzzer = Tunes.repeating("buzzer", List.of(10), 0);
        Tune tune = of(new Row(Map.of(), Map.of(Timer.A, new Start(
                        Tunes.setting(Register.R13), buzzer, Prescaler.BY_4, 100, true, true))),
                Tunes.row(Map.of(Register.R13, 9)));
        assertEquals(List.of(), Check.writing(tune),
                "the frame's own write to R13 restarts the envelope beside the ticks'");
    }

    @Test
    void aStartWithoutThePlaceResetOnATimerThatHasRunNothingIsSaid() {
        assertEquals(List.of("row 0: Timer A starts a source without the place's reset,"
                + " and this timer has run none: the place stands where nothing put it"),
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
                + " reset, and the one before it held 2"), Check.writing(tune));
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
    void aRetuneOfAnEffectThatRunsNothingIsSaid() {
        Tune tune = of(new Row(Map.of(), Map.of(Timer.A, Tunes.bend(Prescaler.BY_4, 90))));
        assertEquals(List.of("row 0: Timer A retunes an effect that runs nothing: a rate"
                + " written to a timer with nothing on it starts that timer with nothing"
                + " to run"), Check.writing(tune));
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
        // four rows at 200 x 200 take about four frames of a 50 Hz tune
        Row start = new Row(Map.of(), Map.of(Timer.A, new Start(Tunes.setting(Register.R8),
                DRUM, Prescaler.BY_200, 200, true, true)));
        Tune tune = of(start, Tunes.row(Map.of(Register.R8, 12)));
        List<String> said = Check.writing(tune);
        assertEquals(1, said.size(), said.toString());
        assertTrue(said.get(0).endsWith("reckoned from its rate"), said.get(0));
        assertEquals(4, Chip.frames(4, Prescaler.BY_200, 200, 50),
                "the frames the reckoning gives it");
    }

    @Test
    void aTuneThatKeepsTheRulesSaysNothing() {
        Tune tune = of(starts(SQUARE, true), Tunes.NOTHING,
                new Row(Map.of(), Map.of(Timer.A, Tunes.bend(Prescaler.BY_4, 90))),
                new Row(Map.of(Register.R8, 12), Map.of(Timer.A, Tunes.STOP)));
        assertEquals(List.of(), Check.writing(tune));
    }

    @Test
    void aTableRepeatingPastItsLastRowIsSaid() {
        Tune tune = new Tune("", "", "", 50,
                new YMXS.Table<>(List.of(Tunes.NOTHING), java.util.OptionalInt.of(4)));
        assertEquals(List.of("the tune holds 1 rows and repeats to row 4"), Check.of(tune));
    }
}
