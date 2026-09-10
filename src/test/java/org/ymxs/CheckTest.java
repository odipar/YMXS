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

    @Test
    void aTableRepeatingPastItsLastRowIsSaid() {
        Tune tune = new Tune("", "", "", 50,
                new YMXS.Table<>(List.of(Tunes.NOTHING), java.util.OptionalInt.of(4)));
        assertEquals(List.of("the tune holds 1 rows and repeats to row 4"), Check.of(tune));
    }
}
