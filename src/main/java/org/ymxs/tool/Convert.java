package org.ymxs.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.ymxs.Csv;
import org.ymxs.Text;
import org.ymxs.YMXS.Multi;

/**
 * {@code ymxs-convert in out}: one form into the other, by what the names
 * end in. A {@code .json} is the text form and a {@code .csv} the table
 * form, and either reads into the same structure.
 *
 * <p>A file that does not read gets a line on standard error beginning
 * {@code ymxs-convert: } and an exit of 1; one that does not read or write
 * at all gets an exit of 2, as does a wrong call.
 */
public final class Convert {

    private Convert() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("ymxs-convert in.json|in.csv out.json|out.csv");
            System.exit(2);
            return;
        }
        Multi multi;
        try {
            String text = Files.readString(Path.of(args[0]));
            multi = args[0].endsWith(".csv") ? Csv.read(text) : Text.read(text);
        } catch (IllegalArgumentException no) {
            System.err.println("ymxs-convert: " + args[0] + ": " + no.getMessage());
            System.exit(1);
            return;
        } catch (IOException failed) {
            System.err.println("ymxs-convert: " + failed);
            System.exit(2);
            return;
        }
        try {
            Files.writeString(Path.of(args[1]),
                    args[1].endsWith(".csv") ? Csv.write(multi) : Text.write(multi));
        } catch (IOException failed) {
            System.err.println("ymxs-convert: " + failed);
            System.exit(2);
            return;
        }
        System.out.println(args[1] + ": " + multi.tunes().size()
                + (multi.tunes().size() == 1 ? " tune" : " tunes"));
    }
}
