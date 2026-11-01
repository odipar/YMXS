package org.ymxs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.ymxs.YMXS.Mapping;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Remap;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.SetVoice;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Three;
import org.ymxs.YMXS.ThreeConnection;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Timing;
import org.ymxs.YMXS.Transformation;
import org.ymxs.YMXS.Triple;
import org.ymxs.YMXS.Tune;
import org.ymxs.YMXS.Voice;

/**
 * The tunes of the note of SPEC.md 3.6.5, built for the tests of each
 * form, of the check and of the record: a fade of voice A, a remap a
 * frame through {@code V15} to {@code V0}, and a start that maps the
 * volume of {@code setVoiceA} alone.
 */
final class Fade {

    private Fade() {}

    /** The number of entries of {@code Vn}, one a volume of a voice: its
     *  keys are 0 to 15. */
    static final int VOLUMES = 16;

    /** {@code Vn} of SPEC.md 3.6.5: 16 entries, entry k k × n divided by
     *  15, for the keys 0 to 15. */
    static Transformation volumes(int n) {
        List<Integer> entries = new ArrayList<>();
        for (int key = 0; key < VOLUMES; key++) {
            entries.add(key * n / 15);
        }
        return new Transformation("V" + n, entries);
    }

    /** A note on voice A: a tone period of 284 and a volume of 15, one
     *  row repeating to row 0. */
    static final Triple NOTE = new Triple("note", Tunes.repeating(
            List.of(new Three(28, 1, 15)), 0));

    /** A chord of two rows on voice A, its volumes 15 and 12. */
    static final Triple CHORD = new Triple("chord", Tunes.repeating(
            List.of(new Three(28, 1, 15), new Three(214, 0, 12)), 0));

    /** A start of {@code source} on {@code setVoiceA} through
     *  {@code mapping}, at 64 ticks a second, both resets set. */
    static Start voice(Triple source, Mapping mapping) {
        return new Start(new ThreeConnection(new SetVoice(Voice.A), source),
                new Timing(Prescaler.BY_200, 192, true, true), false, mapping);
    }

    /** The mapping of {@code setVoiceA} that maps its volume alone,
     *  through {@code Vn}. */
    static Mapping volume(int n) {
        return Tunes.mapping(Tunes.IDENTITY, Tunes.IDENTITY, volumes(n));
    }

    /** The rows the fade has. */
    static final int ROWS = 23;

    /**
     * The fade: row 0 starts the note through the mapping of no parts,
     * rows 1 to 16 remap it through {@code V15} down to {@code V0}, rows
     * 17 to 21 leave it at {@code V0}, and row 22 stops it and sets the
     * volume to 0. It plays once, at 50 Hz.
     */
    static Tune tune() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Map.of(Register.R7, 62),
                Map.of(Timer.A, voice(NOTE, Tunes.NO_PARTS))));
        for (int n = 15; n >= 0; n--) {
            rows.add(new Row(Map.of(), Map.of(Timer.A, new Remap(volume(n)))));
        }
        while (rows.size() < ROWS - 1) {
            rows.add(Tunes.EMPTY);
        }
        rows.add(new Row(Map.of(Register.R8, 0), Map.of(Timer.A, Tunes.STOP)));
        return new Tune("a fade", "", "Fade", 50, Tunes.once(rows));
    }

    /** The chord through {@code V5} on its volume alone, started at row
     *  0 of two rows that repeat to row 0. */
    static Tune wide() {
        return new Tune("a quieter chord", "", "Fade", 50, Tunes.repeating(List.of(
                new Row(Map.of(Register.R7, 62), Map.of(Timer.A, voice(CHORD, volume(5)))),
                Tunes.EMPTY), 0));
    }
}
