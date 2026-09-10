package org.ymxs.ym;

import java.nio.charset.StandardCharsets;

/**
 * A YM5!/YM6! register dump, read in the file's own terms.
 *
 * <p>The layout is a fixed header, extra data, the digidrum samples, three
 * strings ended by a zero, and then the frames: either sixteen vectors of
 * one register each, or one record of sixteen bytes a frame. Both come out
 * as sixteen register vectors.
 *
 * <p>A distributed {@code .ym} is usually an archive with that inside, and
 * {@link Lha} unpacks one, so what this is handed reads either way.
 */
public final class Dump {

    /** What the header said, the frames as read, and the samples as
     *  stored.
     *
     *  <p>{@code registers[r][frame]} is R{@code r} as the file gives it,
     *  all sixteen: the two I/O ports are where this format files an
     *  effect's timer count.
     *
     * @param format YM5! or YM6!
     * @param frames how many frames the dump runs
     * @param playerHz how often the dump's player was called
     * @param loopFrame the frame the dump repeats to
     * @param attributes the header's flag bits
     * @param drums the digidrum samples as stored
     * @param name what the dump calls the tune
     * @param author who the dump says wrote it
     * @param registers the frames, a vector a register
     */
    public record Song(String format, int frames, int playerHz, long loopFrame,
                       long attributes, byte[][] drums, String name, String author,
                       byte[][] registers) {

        /** The registers the file gives, R0 to R15. */
        public static final int REGISTERS = 16;

        /** Attribute bit 2: the samples give one four-bit value a byte. */
        public static final int DRUMS_ARE_4_BIT = 4;
    }

    /** What this reader will not take. */
    public static final class Unreadable extends RuntimeException {
        public Unreadable(String said) {
            super(said);
        }
    }

    private final byte[] data;
    private int at;

    private Dump(byte[] data) {
        this.data = data;
    }

    /** The song in {@code data}.
     *
     * @throws Unreadable where it is not a YM5! or YM6! dump
     */
    public static Song read(byte[] data) {
        if (Lha.isArchive(data)) {
            try {
                data = Lha.unpack(data);
            } catch (IllegalArgumentException no) {
                throw new Unreadable("this is an archive with a dump inside, and it does"
                        + " not unpack: " + no.getMessage());
            }
        }
        return new Dump(data).run();
    }

    private Song run() {
        String format = ascii(4);
        if (!format.equals("YM6!") && !format.equals("YM5!")) {
            throw new Unreadable("not a YM5! or YM6! dump: it opens with \"" + format + "\"");
        }
        if (!ascii(8).equals("LeOnArD!")) {
            throw new Unreadable("the check string after " + format + " is not there");
        }
        long frames = u32();
        long attributes = u32();
        int digidrums = u16();
        u32();                                      // the master clock, which no one reads
        int playerHz = u16();
        long loopFrame = u32();
        skip(u16(), "the extra data");
        byte[][] drums = new byte[digidrums][];
        for (int i = 0; i < digidrums; i++) {
            long size = u32();
            if (size < 0 || size > data.length - at) {
                throw new Unreadable("digidrum " + i + " asks for " + size + " bytes and the"
                        + " file is shorter than that");
            }
            drums[i] = new byte[(int) size];
            System.arraycopy(data, at, drums[i], 0, (int) size);
            at += size;
        }
        String name = string();
        String author = string();
        string();                                   // the comment, which no one reads
        if (frames <= 0 || frames > Integer.MAX_VALUE) {
            throw new Unreadable("a frame count of " + frames);
        }
        if (playerHz <= 0) {
            throw new Unreadable("a player frequency of " + playerHz + " Hz");
        }
        int count = (int) frames;
        byte[][] registers = (attributes & 1) != 0 ? vectors(count) : records(count);
        return new Song(format, count, playerHz, loopFrame, attributes, drums, name, author,
                registers);
    }

    /** Sixteen vectors of one register each. */
    private byte[][] vectors(int frames) {
        need((long) frames * Song.REGISTERS, "the frames");
        byte[][] registers = new byte[Song.REGISTERS][];
        for (int r = 0; r < Song.REGISTERS; r++) {
            registers[r] = new byte[frames];
            System.arraycopy(data, at, registers[r], 0, frames);
            at += frames;
        }
        return registers;
    }

    /** One record of sixteen bytes a frame. */
    private byte[][] records(int frames) {
        need((long) frames * Song.REGISTERS, "the frames");
        byte[][] registers = new byte[Song.REGISTERS][frames];
        for (int frame = 0; frame < frames; frame++) {
            for (int r = 0; r < Song.REGISTERS; r++) {
                registers[r][frame] = data[at++];
            }
        }
        return registers;
    }

    private void need(long bytes, String what) {
        if (bytes > data.length - at) {
            throw new Unreadable(what + " asks for " + bytes + " bytes and "
                    + (data.length - at) + " are left");
        }
    }

    private void skip(int bytes, String what) {
        if (bytes < 0) {
            throw new Unreadable("a size of " + bytes + " for " + what);
        }
        need(bytes, what);
        at += bytes;
    }

    private String ascii(int bytes) {
        need(bytes, "a header field");
        String text = new String(data, at, bytes, StandardCharsets.US_ASCII);
        at += bytes;
        return text;
    }

    private String string() {
        int end = at;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        if (end == data.length) {
            throw new Unreadable("a header string with no zero after it");
        }
        String text = new String(data, at, end - at, StandardCharsets.ISO_8859_1);
        at = end + 1;
        return text;
    }

    private int u16() {
        need(2, "a header field");
        return ((data[at++] & 0xFF) << 8) | (data[at++] & 0xFF);
    }

    private long u32() {
        return ((long) u16() << 16) | u16();
    }
}
