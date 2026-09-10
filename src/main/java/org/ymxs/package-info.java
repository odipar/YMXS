/**
 * The tune data structure, as records. This package is the specification:
 * what a tune is, in terms an implementer can check. doc/SPEC.md defines
 * the rest, which is what a player or an emulator does with it on an Atari
 * ST's YM2149 and MC68901.
 *
 * <p>How a tune is written down is a form. doc/json.md is one; a player's
 * own is another.
 *
 * <p>{@link org.ymxs.YMXS} wraps the whole of it, nested:
 *
 * <pre>
 *  Multi   one Tune or more
 *  Tune    a title, a composer, a writer, a rate, and its Table of Rows
 *  Table   rows, and the row they repeat to: a tune's and a source's
 *  Row     the Registers it sets, and the Effect it puts on each Timer
 *  Effect  Start, Retune or Stop: the three things a row does to one
 *  Target  what a tick calls with a source's row, sealed on SetRegister
 *  Source  what a tick advances a Table of, sealed on Single
 * </pre>
 *
 * <p>Those records have no method of their own beyond the accessors a
 * record gives. A reading of a structure is taken by a function outside
 * it: {@link org.ymxs.Chip} for what the two chips give,
 * {@link org.ymxs.Tunes} for the readings taken off a structure, and
 * {@link org.ymxs.Check} for the rules a structure must satisfy. Each
 * reads by pattern matching over every shape, so a shape added stops them
 * compiling until they read it.
 *
 * <p>A tune's sources are the ones its rows start, in the order a row
 * first starts each. A source no row starts is outside the tune.
 *
 * <p>An effect is a source connected to a target on one timer, so a row
 * sets one on a {@link org.ymxs.YMXS.Timer} and the timer is the effect.
 *
 * <p>A row lists what it sets, and the rest is left alone. A register no
 * row has set keeps what the chip was left at; a register a row set keeps
 * that value until another row sets it, or until an effect's ticks write
 * it. So {@link org.ymxs.YMXS.Row} takes maps rather than a value a
 * register: a key that is absent is a value the row does not set.
 *
 * <p>{@link org.ymxs.Check} gives what is wrong with a structure rather
 * than throwing at the first of it, so one call gives everything a writer
 * has to mend. What SPEC.md 6 asks of a writer reads across rows rather
 * than within one, and it does not read that yet.
 *
 * <p><b>A form's convenience shaped none of this.</b> The records give the
 * music; turning it into something small to store and cheap to play is the
 * work of the program that writes that form. A tune gives its rate where a
 * host is told one rate for a whole file; a Start gives its target and its
 * rate where a form need not write them again; a source keeps its own
 * values where a form packs them. Each is a writer reading the structure
 * and working out what to put in its own bytes.
 *
 * <p>Every limit here comes from the two chips and the music, and none
 * from a form. What a form can write is that form's own business, and a
 * structure it cannot write is still a tune. These records are bound by
 * what a register takes, what a timer counts, that an effect hands a
 * source's row to a target that takes it, and that a tune runs the sources
 * its rows start.
 *
 * <p>One field is Optional: the row a {@link org.ymxs.YMXS.Table} repeats to,
 * where empty is a table that plays once. A name, a title and a composer
 * are text, where empty and absent are the same. Everywhere else absence
 * is a key that is not in a map, or a shape of {@link org.ymxs.YMXS.Effect}
 * without that part.
 *
 * <p>{@link org.ymxs.YMXS.Target} and {@link org.ymxs.YMXS.Source} are sealed, and
 * a later version adding a kind of either adds it there. Every switch
 * that has not read the new one then stops compiling, so an added kind is
 * a change to this specification.
 */
package org.ymxs;
