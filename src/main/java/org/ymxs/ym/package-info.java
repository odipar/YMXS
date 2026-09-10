/**
 * An example: a YM5!/YM6! register dump read into the structure.
 *
 * <p>{@code org.ymxs} compiles without this. It is here to show how
 * another format maps onto a {@link org.ymxs.YMXS.Tune}, and because the
 * figures in the records and in the documents were read off dumps, so a
 * reader can run them again.
 *
 * <p>A dump gives one frame of the YM2149's registers at a time, with two
 * effect slots filed in the bits the chip does not use. Reading it is
 * three steps: a frame's registers become a row's, a slot becomes a
 * {@link org.ymxs.YMXS.Start}, a {@link org.ymxs.YMXS.Retune} or a
 * {@link org.ymxs.YMXS.Stop}, and what a slot sounds becomes a
 * {@link org.ymxs.YMXS.Source} of its own.
 *
 * <p>The dump format is described at
 * <a href="http://leonard.oxg.free.fr/ymformat.html">the YM format page</a>.
 * Its own words are used here where they name what is read: digidrums,
 * effect slots, the player frequency. The structure's words begin at
 * {@link org.ymxs.ym.Read}.
 *
 * <p>A distributed {@code .ym} is usually an archive with the dump inside.
 * This reads the dump, and says so where it is handed an archive.
 */
package org.ymxs.ym;
