package org.ymxs.ym;

import org.ymxs.Prescaler;
import org.ymxs.Register;

/**
 * One of the two effect slots a dump's frame holds, read as what it
 * sounds: a kind, the voice it sounds on, the register its ticks write,
 * the value its source is built from, and the timer's rate.
 *
 * <p>YM6 gives each slot a kind in bits 7 and 6 of the register it is
 * filed in. YM5 has no kind bits: its first slot is a square wave and its
 * second a recording. A slot whose prescaler or count is 0 is one no
 * player runs.
 *
 * @param kind {@link #NONE}, {@link #SQUARE}, {@link #RECORDING},
 *     {@link #SINUS} or {@link #BUZZER}
 * @param voice 0, 1 or 2, the voice it sounds on
 * @param target the register its ticks write
 * @param data the value its source is built from: a level, or a sample
 *     number, or a shape
 * @param prescaler the timer's first divisor
 * @param count the timer's second divisor
 */
public record Slot(int kind, int voice, Register target, int data, Prescaler prescaler,
                   int count) {

    /** A slot no player runs. */
    public static final int NONE = 0;

    /** A square wave on a volume register: a level and a silence at the
     *  timer's rate. The scene calls it a SID voice. */
    public static final int SQUARE = 1;

    /** A recording through a volume register. The scene calls it a
     *  digidrum. */
    public static final int RECORDING = 2;

    /** A shape this reader does not take: the player it was written for
     *  runs an empty handler for it. */
    public static final int SINUS = 3;

    /** The envelope restarted at the timer's rate. The scene calls it a
     *  sync buzzer. */
    public static final int BUZZER = 4;

    /** A slot no player runs. */
    public static final Slot EMPTY = new Slot(NONE, 0, Register.R0, 0, Prescaler.BY_4, 1);

    /** The registers a slot is filed in: its code, its prescaler and its
     *  count. */
    private static final int[][] FILED = {{1, 6, 14}, {3, 8, 15}};

    /** Whether a player runs this slot. */
    public boolean on() {
        return kind != NONE;
    }

    /** The two slots of one frame. */
    public static Slot[] of(Dump.Song song, int frame) {
        boolean ym6 = song.format().equals("YM6!");
        byte[][] r = song.registers();
        Slot[] out = new Slot[2];
        for (int slot = 0; slot < 2; slot++) {
            int code = r[FILED[slot][0]][frame] & 0xF0;
            int voice = ((code >> 4) & 3) - 1;
            int select = (r[FILED[slot][1]][frame] & 0xFF) >> 5;
            int count = r[FILED[slot][2]][frame] & 0xFF;
            if (voice < 0 || select == 0 || count == 0) {
                out[slot] = EMPTY;
                continue;
            }
            int kind = ym6 ? (code >> 6) + 1 : slot == 0 ? SQUARE : RECORDING;
            Register target = kind == BUZZER ? Register.R13 : Register.at(8 + voice);
            out[slot] = new Slot(kind, voice, target, r[8 + voice][frame] & 0x1F,
                    Prescaler.values()[select - 1], count);
        }
        return out;
    }
}
