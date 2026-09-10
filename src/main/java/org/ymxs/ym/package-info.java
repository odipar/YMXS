/**
 * An example: a YM5!/YM6! register dump read into the structure.
 *
 * <p>Nothing in {@code org.ymxs} depends on this. It is here to show how a
 * format that is not this one maps onto a {@link org.ymxs.Tune}, and
 * because the figures the records and the documents state were read off
 * dumps, so a reader can run them again.
 *
 * <p>A dump holds one frame of the YM2149's registers at a time, with two
 * effect slots filed in the bits the chip does not use. Reading it is
 * three steps: a frame's registers become a row's, a slot becomes a
 * {@link org.ymxs.Start}, a {@link org.ymxs.Retune} or a
 * {@link org.ymxs.Stop}, and what a slot sounds becomes a
 * {@link org.ymxs.Source} of its own.
 *
 * <p>The dump format is described at
 * <a href="http://leonard.oxg.free.fr/ymformat.html">the YM format page</a>.
 * Its own words are used here where they name what is read: digidrums,
 * effect slots, the player frequency. The structure's words begin at
 * {@link org.ymxs.ym.Read}.
 *
 * <p>A distributed {@code .ym} is usually an archive holding the dump.
 * This reads the dump, and says so where it is handed an archive.
 */
package org.ymxs.ym;
