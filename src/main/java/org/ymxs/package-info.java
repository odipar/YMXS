/**
 * The structure a tune holds, as records. This package is the
 * specification: what a tune is, in terms an implementer can check.
 * doc/SPEC.md states the rest, which is what a player or an emulator does
 * with it on an Atari ST's YM2149 and MC68901.
 *
 * <p>How a tune is written down is a form, and no form is the format.
 * doc/text.md is one; a player's own is another.
 *
 * <p>{@link org.ymxs.YMXS} holds the whole of it, nested:
 *
 * <pre>
 *  Multi   one Tune or more
 *  Tune    a title, a composer, a writer, a rate, and its Table of Rows
 *  Table   rows, and the row they repeat to: a tune's and a source's
 *  Row     the Registers it sets, and the Effect it states on each Timer
 *  Effect  Start, Retune or Stop: the three things a row does to one
 *  Target  what a tick calls with a source's row, sealed on SetRegister
 *  Source  what a tick advances a Table of, sealed on Single
 * </pre>
 *
 * <p>Nothing there holds a method of its own beyond the accessors a record
 * gives. What is read off a structure is read by a function outside it:
 * {@link org.ymxs.Chip} for what the two chips give, {@link org.ymxs.Tunes}
 * for what a structure holds, and {@link org.ymxs.Check} for what a
 * structure has to satisfy. Each reads by pattern matching and states
 * every shape, so a shape added stops them compiling until they read it.
 *
 * <p>A tune's sources are the ones its rows start, in the order a row
 * first starts each. A source no row starts is not one the tune holds.
 *
 * <p>An effect is a source connected to a target on one timer, so a row
 * states one against a {@link org.ymxs.YMXS.Timer} and the timer is the
 * effect.
 *
 * <p>A row states what it sets and says nothing about the rest. A
 * register no row has set holds what the chip was left at; a register a
 * row set holds that value until another row sets it, or until an
 * effect's ticks write it. That is why {@link org.ymxs.YMXS.Row} holds maps
 * rather than a value a register: a key that is absent is a value the row
 * does not set.
 *
 * <p>{@link org.ymxs.Check} gives what is wrong with a structure rather
 * than throwing at the first of it, so one call names everything a writer
 * has to mend. What SPEC.md 6 asks of a writer reads across rows rather
 * than within one, and it does not read that yet.
 *
 * <p><b>Nothing here is arranged for a form's benefit.</b> The records
 * state the music; turning it into something small to store and cheap to
 * play is the work of whatever writes that form. A tune states its rate
 * where a host is told one rate for a whole file; a Start states its
 * target and its rate where a form need not write them again; a source
 * holds its values where a form packs them. Each is a writer reading what
 * the structure says and working out what to put in its own bytes.
 *
 * <p>No limit here is a form's. What a form can hold is that form's to
 * say, and a structure it cannot hold is one it turns away; it is still a
 * tune. What these records hold to is the two chips and the music: what a
 * register takes, what a timer counts, that an effect hands a source's
 * row to a target that takes it, and that a tune holds the sources its
 * rows start.
 *
 * <p>One field is Optional: the row a {@link org.ymxs.YMXS.Table} repeats to,
 * where empty is a table that plays once. A name, a title and a composer
 * are text, where empty and absent are one state. Everywhere else absence
 * is a key that is not in a map, or a shape of {@link org.ymxs.YMXS.Effect}
 * that does not hold the part.
 *
 * <p>{@link org.ymxs.YMXS.Target} and {@link org.ymxs.YMXS.Source} are sealed, and
 * a later version adding a kind of either adds it there. Every switch
 * that has not read the new one then stops compiling, which is what makes
 * an added kind a change to this specification.
 */
package org.ymxs;
