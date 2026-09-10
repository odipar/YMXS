package org.ymxs.ym;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A YM6 dump written here, so that a frame's bytes stand in the test
 * rather than in a file.
 *
 * <p>A dump files its two effect slots in the bits the chip does not use:
 * slot 0's kind and voice in R1's top four, its prescaler in R6's top
 * three and its count in R14; slot 1's in R3, R8 and R15. {@link #slot0}
 * and {@link #slot1} write those.
 */
final class Dumps {

    private final byte[][] frames;
    private final List<byte[]> samples;
    private final int loop;

    Dumps(int frames, int loop, List<byte[]> samples) {
        this.frames = new byte[frames][16];
        this.loop = loop;
        this.samples = samples;
        for (byte[] one : this.frames) {
            one[13] = (byte) 0xFF;              // R13 unwritten
        }
    }

    Dumps set(int frame, int register, int value) {
        frames[frame][register] = (byte) value;
        return this;
    }

    /** Slot 0: a kind and a voice in R1, a prescaler in R6, a count in
     *  R14. Kind 1 is a square wave, 2 a recording, 4 a buzzer. */
    Dumps slot0(int frame, int kind, int voice, int select, int count) {
        set(frame, 1, (kind - 1) << 6 | (voice + 1) << 4 | frames[frame][1] & 0x0F);
        set(frame, 6, select << 5 | frames[frame][6] & 0x1F);
        return set(frame, 14, count);
    }

    /** Slot 1: the same in R3, R8 and R15. */
    Dumps slot1(int frame, int kind, int voice, int select, int count) {
        set(frame, 3, (kind - 1) << 6 | (voice + 1) << 4 | frames[frame][3] & 0x0F);
        set(frame, 8, select << 5 | frames[frame][8] & 0x1F);
        return set(frame, 15, count);
    }

    byte[] bytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("YM6!LeOnArD!".getBytes(StandardCharsets.US_ASCII));
        long32(out, frames.length);
        long32(out, 0);                          // one record a frame
        word(out, samples.size());
        long32(out, 2000000);
        word(out, 50);
        long32(out, loop);
        word(out, 0);
        for (byte[] one : samples) {
            long32(out, one.length);
            out.writeBytes(one);
        }
        text(out, "a tune");
        text(out, "a composer");
        text(out, "");
        for (byte[] one : frames) {
            out.writeBytes(one);
        }
        out.writeBytes("End!".getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private static void word(ByteArrayOutputStream out, int value) {
        out.write(value >> 8);
        out.write(value);
    }

    private static void long32(ByteArrayOutputStream out, long value) {
        word(out, (int) (value >> 16));
        word(out, (int) value);
    }

    private static void text(ByteArrayOutputStream out, String said) {
        out.writeBytes(said.getBytes(StandardCharsets.ISO_8859_1));
        out.write(0);
    }
    }
