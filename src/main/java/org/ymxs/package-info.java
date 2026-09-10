/**
 * The tune data structure, as records. This package is the specification:
 * what a tune is, in terms an implementer can check. doc/SPEC.md defines
 * the rest, which is what a player or an emulator does with it on an Atari
 * ST's YM2149 and MC68901.
 *
 * <p>A form writes a tune down. doc/json.md defines one; a player's binary
 * layout is another.
 *
 * <p>{@link org.ymxs.YMXS} wraps the whole of it, nested:
 *
 * <pre>
 *  Multi   one Tune or more
 *  Tune    a title, a composer, a writer, a rate, and its Table of Rows
 *  Table   rows, and the row they repeat to: a tune's and a source's
 *  Row     the Registers it sets, and the Effect it puts on each Timer
 *  Effect  Start, Retune or Stop: the three operations on one
 *  Target  what a tick calls with a source's row, sealed on SetRegister
 *  Source  what a tick advances a Table of, sealed on Single
 * </pre>
 *
 * <p>Those records have no methods beyond their accessors. A structure is
 * read by a function outside it: {@link org.ymxs.Chip} for the figures of
 * the two chips, {@link org.ymxs.Tunes} for what is read off a structure,
 * and {@link org.ymxs.Check} for the rules a structure must satisfy. Each
 * reads by pattern matching over every shape, so a shape added stops them
 * compiling until they read it.
 *
 * <p>A tune's sources are the ones its rows start, in first-start order.
 * Only a source started by some row belongs to the tune.
 *
 * <p>An effect is a source connected to a target on one timer, so a row
 * sets one on a {@link org.ymxs.YMXS.Timer} and the timer is the effect.
 *
 * <p>A row lists what it sets, and the rest is left alone. A register no
 * row has set keeps the value the chip was left at; a register a row set
 * keeps that value until another row sets it, or until an effect's ticks
 * write it. {@link org.ymxs.YMXS.Row} is therefore built on maps rather
 * than a value a register: an absent key is a register the row does not
 * set.
 *
 * <p>{@link org.ymxs.Check} reports what is wrong with a structure rather
 * than throwing at the first fault, so one call reports every fault a
 * writer must correct. The rules of SPEC.md 6 read across rows rather than
 * within one, and are read separately.
 *
 * <p><b>No part of this is arranged for a form.</b> The records are the
 * music; compressing it for storage and for cheap playback belongs to the
 * program that writes the form. A tune has its rate where a host is told
 * one rate a file; a Start names its target and its rate where a form need
 * not repeat them; a source keeps its values where a form packs them. In
 * each case the writer of the form reads the structure and decides what
 * its bytes contain.
 *
 * <p>Every limit here follows from the two chips and the music, and none
 * from a form. What a form can write belongs to that form, and a structure
 * it cannot write is still a tune. These records are bound by what fits a
 * register, what a timer counts, that an effect hands a source's row to a
 * target of that same shape, and that a tune runs the sources its rows
 * start.
 *
 * <p>One field is Optional: the row a {@link org.ymxs.YMXS.Table} repeats
 * to, where empty marks a table that plays once. A name, a title and a
 * composer are text, where empty and absent are equivalent. Elsewhere
 * absence is an absent map key, or a shape of
 * {@link org.ymxs.YMXS.Effect} without that part.
 *
 * <p>{@link org.ymxs.YMXS.Target} and {@link org.ymxs.YMXS.Source} are
 * sealed, and a later version adding a kind of either adds it there. Every
 * switch that has not read the new kind then stops compiling, so an added
 * kind is a change to this specification.
 */
package org.ymxs;
