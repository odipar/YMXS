package org.ymxs.ym;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.ymxs.Text;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Tune;

/**
 * A {@code .ym} as distributed: an archive containing the dump.
 *
 * <p>{@code src/test/resources/packed.ym} is 162 bytes of {@code -lh5-},
 * and the tune inside it is the one in {@code doc/tunes/circus.json}, so
 * the result of the unpacking is compared against a tune read another way.
 */
final class PackedTest {

    private static final Path PACKED = Path.of("src/test/resources/packed.ym");

    @Test
    void anArchiveReadsAsTheDumpInsideIt() throws IOException {
        byte[] archive = Files.readAllBytes(PACKED);
        assertTrue(Lha.isArchive(archive), "the fixture is an archive");
        Dump.Song song = Dump.read(archive);
        assertEquals("YM5!", song.format());
        assertEquals(4, song.frames());
        assertEquals(50, song.playerHz());
    }

    @Test
    void theTuneInsideIsTheOneReadAnotherWay() throws IOException {
        Tune packed = Read.of(Dump.read(Files.readAllBytes(PACKED)), "ym-to-ymxs",
                OptionalInt.of(0)).tune();
        Tune read = Text.read(Files.readString(Path.of("doc/tunes/circus.json")))
                .tunes().get(0);
        assertEquals(read, packed, "the archive has the tune doc/tunes/circus.json does");
        assertEquals(4, Tunes.size(packed.table()));
    }

    @Test
    void aDumpThatIsNotPackedReadsToo() throws IOException {
        byte[] dump = Lha.unpack(Files.readAllBytes(PACKED));
        assertTrue(!Lha.isArchive(dump), "what comes out is not an archive");
        assertEquals(4, Dump.read(dump).frames(), "and it reads as it stands");
    }

    @Test
    void somethingThatIsNeitherIsSaidToBeNeither() {
        Dump.Unreadable no = assertThrows(Dump.Unreadable.class,
                () -> Dump.read("not a dump and not an archive".getBytes(
                        java.nio.charset.StandardCharsets.US_ASCII)));
        assertTrue(String.valueOf(no.getMessage()).contains("not a YM5! or YM6! dump"),
                String.valueOf(no.getMessage()));
    }
}
