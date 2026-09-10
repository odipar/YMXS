/**
 * An example: a YM5!/YM6! register dump read into the structure.
 *
 * <p>{@code org.ymxs} compiles without this package. It is here as a
 * worked mapping from another format onto a {@link org.ymxs.YMXS.Tune},
 * and because the figures in the records and in the documents were
 * measured on dumps, which this makes repeatable.
 *
 * <p>A dump is one frame of the YM2149's registers at a time, with two
 * effect slots filed in the bits the chip leaves unused. Reading it is
 * three steps: a frame's registers become a row's, a slot becomes a
 * {@link org.ymxs.YMXS.Start}, a {@link org.ymxs.YMXS.Retune} or a
 * {@link org.ymxs.YMXS.Stop}, and each distinct sound a slot produces
 * becomes a {@link org.ymxs.YMXS.Source}.
 *
 * <p>The dump format is described at
 * <a href="http://leonard.oxg.free.fr/ymformat.html">the YM format page</a>.
 * Its terms are used here for what is read: digidrums, effect slots, the
 * player frequency. The terms of the structure begin at
 * {@link org.ymxs.ym.Read}.
 *
 * <p>A distributed {@code .ym} is usually an archive containing the dump.
 * This package reads the dump, and reports an archive as such.
 */
package org.ymxs.ym;
