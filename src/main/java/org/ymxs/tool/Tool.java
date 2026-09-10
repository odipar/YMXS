package org.ymxs.tool;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * What every tool here shares: it reads one input on standard input,
 * writes one output on standard output, and reports progress and faults on
 * standard error. A tool therefore composes in a pipe, and a redirected
 * run contains the output alone.
 *
 * <p>The exit codes:
 *
 * <table><caption>exits</caption>
 * <tr><td>0</td><td>the tool completed</td></tr>
 * <tr><td>1</td><td>the input is wrong, and the fault is reported</td></tr>
 * <tr><td>2</td><td>the call is wrong, or reading or writing failed</td></tr>
 * </table>
 *
 * <p>{@code -silent} reduces the report to faults.
 */
public final class Tool {

    /** The tool completed. */
    public static final int DONE = 0;

    /** The input is wrong. */
    public static final int WRONG = 1;

    /** The call is wrong, or reading or writing failed. */
    public static final int FAILED = 2;

    /** The flag that reduces the report to faults. */
    public static final String SILENT = "-silent";

    private final String named;
    private boolean reports = true;

    private Tool(String named) {
        this.named = named;
    }

    /** A tool of this name. The flags every tool reads are removed from
     *  {@code args}; the rest remain. */
    public static Tool of(String named, List<String> args, String... flags) {
        Tool tool = new Tool(named);
        List<String> rest = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals(SILENT)) {
                tool.reports = false;
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

    /** Progress, on standard error, unless {@code -silent} was passed. */
    public void report(String said) {
        if (reports) {
            System.err.println(named + ": " + said);
        }
    }

    /** Whether the tool reports progress. */
    public boolean reports() {
        return reports;
    }

    /**
     * A fault, on standard error, and an exit of {@code with}. This never
     * returns; it is declared as though it did, so that a caller can throw
     * it and a compiler can see the path ends.
     */
    public RuntimeException wrong(int with, String said) {
        System.err.println(named + ": " + said);
        System.exit(with);
        throw new UncheckedIOException(new IOException(said));
    }

    /** The calling convention, on standard error, and an exit of 2. */
    public RuntimeException usage(String said) {
        return wrong(FAILED, said);
    }
}
