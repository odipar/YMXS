package org.ymxs.tool;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * What every tool here shares: it reads its one input on standard input,
 * writes its one output on standard output, and says what it did and what
 * went wrong on standard error. So a tool stands in a pipe, and a run read
 * into a file is the tool's output alone.
 *
 * <p>What it exits with:
 *
 * <table><caption>exits</caption>
 * <tr><td>0</td><td>it did what it was asked</td></tr>
 * <tr><td>1</td><td>what it read is wrong, and the tool says how</td></tr>
 * <tr><td>2</td><td>the call is wrong, or reading or writing failed</td></tr>
 * </table>
 *
 * <p>{@code -silent} cuts what a tool says down to what is wrong.
 */
public final class Tool {

    /** It did what it was asked. */
    public static final int DONE = 0;

    /** What it read is wrong. */
    public static final int WRONG = 1;

    /** The call is wrong, or reading or writing failed. */
    public static final int FAILED = 2;

    /** The flag that cuts what a tool says down to what is wrong. */
    public static final String SILENT = "-silent";

    private final String named;
    private boolean says = true;

    private Tool(String named) {
        this.named = named;
    }

    /** A tool of this name. The flags every tool reads come off {@code
     *  args}, and the rest stay there. */
    public static Tool of(String named, List<String> args, String... flags) {
        Tool tool = new Tool(named);
        List<String> rest = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals(SILENT)) {
                tool.says = false;
            } else {
                rest.add(arg);
            }
        }
        args.clear();
        args.addAll(rest);
        if (flags.length == 0 && !args.isEmpty()) {
            tool.wrong(FAILED, named + " reads its input on standard input and writes it on"
                    + " standard output. Its one flag is " + SILENT + ".");
        }
        return tool;
    }

    /** Everything on standard input, as text. */
    public String text() {
        return new String(bytes(), StandardCharsets.UTF_8);
    }

    /** Everything on standard input, as bytes. */
    public byte[] bytes() {
        try {
            return System.in.readAllBytes();
        } catch (IOException failed) {
            throw wrong(FAILED, "cannot read standard input: " + failed.getMessage());
        }
    }

    /** {@code said} on standard output, which is what the tool is for. */
    public void write(String said) {
        PrintStream out = System.out;
        out.print(said);
        out.flush();
        if (out.checkError()) {
            throw wrong(FAILED, "cannot write standard output");
        }
    }

    /** What the tool did, on standard error, unless it was asked to be
     *  silent. */
    public void say(String said) {
        if (says) {
            System.err.println(named + ": " + said);
        }
    }

    /** Whether the tool says what it did. */
    public boolean says() {
        return says;
    }

    /**
     * What is wrong, on standard error, and an exit of {@code with}. This
     * never returns; it is written as though it did so that a caller can
     * throw it and a compiler can see the path ends.
     */
    public RuntimeException wrong(int with, String said) {
        System.err.println(named + ": " + said);
        System.exit(with);
        throw new UncheckedIOException(new IOException(said));
    }

    /** How the tool is called, on standard error, and an exit of 2. */
    public RuntimeException usage(String said) {
        return wrong(FAILED, said);
    }
}
