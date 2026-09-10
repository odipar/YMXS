package org.ymxs.ym;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;
import org.ymxs.Chip;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Target;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

/**
 * A dump read into a {@link Tune}: one row a frame, and a source for each
 * distinct thing the dump's effect slots sound.
 *
 * <p>A dump holds every register of every frame, so a row here sets a
 * register where the dump's value moved. A register an effect is running
 * on is the effect's, and no row sets it until the row that stops it,
 * which is what SPEC.md 6 asks of a writer.
 *
 * <p>The two slots run on Timers A and D. A slot that sounds a square
 * wave becomes a source of a level and a silence; one that restarts the
 * envelope, a source of the shape; one that plays a recording, a source
 * of the sample's levels and a closing row at mid-scale, which owns the
 * voice's volume for as long as its rows take at its rate and silences
 * the voice's tone and noise meanwhile.
 *
 * <p>A recording on a voice keeps a square wave off it: the dump's player
 * runs one thing a voice, and the recording is the one it runs.
 *
 * <p>The row a tune repeats to sets every register but the ones an effect
 * is running on, and states every effect that ran up to it or runs into
 * the wrap, so the wrap lands on a state a player has been told.
 */
public final class Read {

    /** The bits a dump keeps for its own flags, which no register takes. */
    private static final int[] TAKES = {0xFF, 0x0F, 0xFF, 0x0F, 0xFF, 0x0F, 0x1F, 0x3F,
                                        0x1F, 0x1F, 0x1F, 0xFF, 0xFF, 0x0F};

    /** The level a recording's last row leaves its register at: mid-scale,
     *  so the frame's write that takes the register back does not click. */
    private static final int PARK = 13;

    /** The timer each of the dump's two slots runs on. */
    private static final Timer[] TIMER_OF = {Timer.A, Timer.D};

    /** What a reading came to, beside the tune. */
    public record Said(int dropped, int preempted, int cutAtRepeat) { }

    private final Dump.Song song;
    private final int repeat;
    private final List<Source> sources = new ArrayList<>();
    private final Map<Integer, Source> known = new LinkedHashMap<>();
    private final byte[][] samples;
    private int dropped;
    private int preempted;
    private int cutAtRepeat;

    private Read(Dump.Song song, int repeat) {
        this.song = song;
        this.repeat = repeat;
        boolean fourBit = (song.attributes() & Dump.Song.DRUMS_ARE_4_BIT) != 0;
        samples = new byte[song.drums().length][];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = new byte[song.drums()[i].length];
            for (int j = 0; j < samples[i].length; j++) {
                samples[i][j] = (byte) (fourBit ? song.drums()[i][j] & 15
                        : (song.drums()[i][j] & 0xFF) >> 4);
            }
        }
    }

    /** The tune {@code song} holds, repeating to the frame the dump names.
     *  {@code writer} is what the tune says made it. */
    public static Tune of(Dump.Song song, String writer) {
        long loop = song.loopFrame();
        int repeat = loop >= 0 && loop < song.frames() ? (int) loop : 0;
        return of(song, writer, OptionalInt.of(repeat)).tune();
    }

    /** The same, with the row to repeat to given, and what the reading
     *  came to beside the tune. */
    public static Reading of(Dump.Song song, String writer, OptionalInt repeat) {
        Read read = new Read(song, repeat.orElse(song.frames()));
        List<Map<Register, Integer>> registers = new ArrayList<>();
        List<Map<Timer, Effect>> effects = new ArrayList<>();
        read.run(registers, effects);
        List<Row> rows = new ArrayList<>();
        for (int f = 0; f < song.frames(); f++) {
            rows.add(new Row(registers.get(f), effects.get(f)));
        }
        Tune tune = new Tune(song.name(), song.author(), writer, song.playerHz(),
                new Table<>(rows, repeat));
        return new Reading(tune, new Said(read.dropped, read.preempted, read.cutAtRepeat));
    }

    /** A tune, and what the reading came to. */
    public record Reading(Tune tune, Said said) { }

    private void run(List<Map<Register, Integer>> registers, List<Map<Timer, Effect>> effects) {
        int frames = song.frames();
        int[] held = new int[14];
        Arrays.fill(held, -1);
        Slot[] running = {Slot.EMPTY, Slot.EMPTY};
        @Nullable Source[] runs = new Source[2];
        Prescaler[] prescalerHeld = {Prescaler.BY_4, Prescaler.BY_4};
        int[] countHeld = {0, 0};
        int[] drumEnd = {-1, -1};
        boolean[] stopAtRepeat = {false, false};
        int[] lastKind = {Slot.NONE, Slot.NONE};
        @Nullable Register[] lastTarget = new Register[2];
        for (int f = 0; f < frames; f++) {
            boolean keyframe = f == repeat;
            if (keyframe) {
                Arrays.fill(held, -1);
                Arrays.fill(lastKind, Slot.NONE);
                Arrays.fill(lastTarget, null);
                for (int i = 0; i < 2; i++) {
                    stopAtRepeat[i] = running[i].on();
                }
            }
            int[] reg = registers(f);
            Slot[] slot = Slot.of(song, f).clone();
            @Nullable Source[] source = new Source[2];
            // The recordings first: one keeps a square wave off its voice.
            for (int i = 0; i < 2; i++) {
                if (slot[i].on() && slot[i].kind() == Slot.RECORDING) {
                    source[i] = source(slot[i]);
                    if (source[i] == null) {
                        slot[i] = Slot.EMPTY;
                    }
                }
            }
            for (int i = 0; i < 2; i++) {
                if (!slot[i].on() || slot[i].kind() == Slot.RECORDING) {
                    continue;
                }
                int other = 1 - i;
                boolean drumStarts = slot[other].on() && slot[other].kind() == Slot.RECORDING
                        && slot[other].voice() == slot[i].voice();
                boolean replaced = slot[other].on() && !drumStarts;
                boolean drumRuns = running[other].kind() == Slot.RECORDING
                        && running[other].voice() == slot[i].voice() && f < drumEnd[other]
                        && !keyframe && !replaced;
                if (slot[i].kind() == Slot.SQUARE && (drumRuns || drumStarts)) {
                    slot[i] = Slot.EMPTY;
                    preempted++;
                    continue;
                }
                source[i] = source(slot[i]);
                if (source[i] == null) {
                    slot[i] = Slot.EMPTY;
                }
            }
            Map<Timer, Effect> here = new EnumMap<>(Timer.class);
            int owned = 0;
            for (int i = 0; i < 2; i++) {
                boolean drum = running[i].kind() == Slot.RECORDING;
                if (!slot[i].on()) {
                    if (keyframe && drum && f < drumEnd[i]) {
                        cutAtRepeat++;
                    }
                    if (drum && f < drumEnd[i] && !keyframe) {
                        // the recording plays on: the dump states only its start
                    } else if (running[i].on() || keyframe) {
                        here.put(TIMER_OF[i], Tunes.STOP);
                        running[i] = Slot.EMPTY;
                        runs[i] = null;
                    }
                } else {
                    Source names = java.util.Objects.requireNonNull(source[i]);
                    boolean starting = keyframe || slot[i].kind() == Slot.RECORDING
                            || running[i].kind() != slot[i].kind()
                            || running[i].target() != slot[i].target()
                            || runs[i] != names;
                    if (starting) {
                        // A stopped timer begins a whole period either way,
                        // and a running one takes the new count at its next
                        // zero, so the timer's reset is stated where the
                        // timer is stopped. A square wave replacing a square
                        // wave on the same register has the row count of the
                        // one before it, so the place stands and the wave
                        // keeps its phase.
                        boolean stopped = keyframe || !running[i].on()
                                || running[i].kind() == Slot.RECORDING && f >= drumEnd[i];
                        boolean unmoved = !keyframe && slot[i].kind() == Slot.SQUARE
                                && lastKind[i] == Slot.SQUARE
                                && lastTarget[i] == slot[i].target();
                        here.put(TIMER_OF[i], new Start(Tunes.setting(slot[i].target()),
                                names, slot[i].prescaler(), slot[i].count(), stopped,
                                !unmoved));
                        running[i] = slot[i];
                        runs[i] = names;
                        lastKind[i] = slot[i].kind();
                        lastTarget[i] = slot[i].target();
                        if (slot[i].kind() == Slot.RECORDING) {
                            drumEnd[i] = f + Chip.frames(Tunes.size(Tunes.table(names)),
                                    slot[i].prescaler(), slot[i].count(), song.playerHz());
                        }
                    } else if (slot[i].prescaler() != prescalerHeld[i]
                            || slot[i].count() != countHeld[i]) {
                        here.put(TIMER_OF[i], new Retune(slot[i].prescaler(), slot[i].count(),
                                false, false));
                    }
                    prescalerHeld[i] = slot[i].prescaler();
                    countHeld[i] = slot[i].count();
                }
                if (running[i].kind() == Slot.SQUARE
                        || running[i].kind() == Slot.RECORDING) {
                    owned |= 1 << Chip.number(running[i].target());
                }
                if (running[i].kind() == Slot.RECORDING) {
                    reg[7] |= 0x09 << running[i].voice();
                }
            }
            Map<Register, Integer> sets = new EnumMap<>(Register.class);
            for (int c = 0; c < 13; c++) {
                if ((owned & 1 << c) != 0) {
                    held[c] = -1;                   // the effect's register, and no row's
                } else if (reg[c] != held[c]) {
                    sets.put(Chip.register(c), reg[c]);
                    held[c] = reg[c];
                }
            }
            if (reg[13] >= 0) {
                sets.put(Register.R13, reg[13]);    // a write to it restarts the envelope
            }
            registers.add(sets);
            effects.add(here);
        }
        // The row the tune repeats to states a stop for an effect that ran
        // up to it or runs into the wrap. One that did neither is left
        // alone.
        if (repeat < frames) {
            for (int i = 0; i < 2; i++) {
                if (effects.get(repeat).get(TIMER_OF[i]) instanceof Stop
                        && !stopAtRepeat[i] && !running[i].on()) {
                    effects.get(repeat).remove(TIMER_OF[i]);
                }
            }
        }
    }

    /** R0 to R13 of one frame with the dump's flag bits off, and -1 for
     *  R13 where the dump does not write it. */
    private int[] registers(int frame) {
        byte[][] r = song.registers();
        int[] out = new int[14];
        for (int i = 0; i < 14; i++) {
            out[i] = r[i][frame] & TAKES[i];
        }
        if ((r[13][frame] & 0xFF) == 0xFF) {
            out[13] = -1;
        }
        return out;
    }

    /** The source a slot sounds, built on first use, or null where the
     *  dump names none this reads. */
    private @Nullable Source source(Slot slot) {
        int data = slot.kind() == Slot.RECORDING ? slot.data() & 31 : slot.data() & 15;
        if (slot.kind() == Slot.SINUS) {
            dropped++;
            return null;
        }
        if (slot.kind() == Slot.RECORDING && data >= samples.length) {
            dropped++;
            return null;
        }
        int key = slot.kind() << 8 | data;
        Source found = known.get(key);
        if (found != null) {
            return found;
        }
        Source built = build(slot.kind(), data);
        known.put(key, built);
        sources.add(built);
        return built;
    }

    private Source build(int kind, int data) {
        switch (kind) {
            case Slot.SQUARE:
                // The level then the silence. The row that starts the wave
                // states no level of its own, so the voice holds what the
                // last row set for a timer's period and the first tick
                // opens the loud half.
                return Tunes.repeating("square " + data, List.of(data, 0), 0);
            case Slot.BUZZER:
                return Tunes.repeating("buzzer " + data, List.of(data), 0);
            default:
                List<Integer> rows = new ArrayList<>();
                for (byte one : samples[data]) {
                    rows.add((int) one);
                }
                rows.add(PARK);
                return Tunes.once("recording " + data, rows);
        }
    }

}
