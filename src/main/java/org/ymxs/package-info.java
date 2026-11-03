/**
 * Tune data for the Atari ST's YM2149 and MC68901. {@link org.ymxs.YMXS}
 * defines the records; doc/SPEC.md defines playback and validation.
 *
 * <p>A frame reads a tune row and applies its register writes and timer
 * operations. A tick reads a source row, applies the effect's mapping,
 * then writes through its target. Transformation lookups are optional:
 * an empty mapping uses identity for every value.
 *
 * <pre>
 * Multi          tunes selected by the host
 * Tune           metadata, frame rate and a Table of Rows
 * Table          rows and an optional repeat row
 * Row            register writes and timer operations
 * Effect         Start, Retune, Stop or Remap
 * Connection     source and target of matching width
 * Source         Single, Pair or Triple: values or lookup keys
 * Mapping        identity or transformation for each value
 * Target         writes one, two or three mapped values
 * Transformation name and lookup entries
 * </pre>
 *
 * <p>A {@link org.ymxs.YMXS.Start} supplies a connection, timing and
 * mapping. A {@link org.ymxs.YMXS.Remap} changes the running effect's
 * mapping without restarting its source. A deferred start retains its
 * separate mapping while pending. Volume transformations can scale a
 * sample or fade it while playback continues from the current place.
 *
 * <p>{@link org.ymxs.Tunes} provides constructors and access functions.
 * It lists sources and transformations in the order their operations
 * first use them. Names and data determine equality; transformations
 * with equal entries under different names are separate values.
 *
 * <p>{@link org.ymxs.Check} validates lookup sizes, keys, mapped values
 * and mapping widths. A remap must fit every start on its timer,
 * including starts before and after it. Writer-rule warnings are
 * checked separately from errors of the structure.
 *
 * <p>JSON, CSV and player binary layouts encode the same structure.
 * Identity-only mappings serialize as 0 in JSON and CSV; transformation
 * parts refer to numbered entries in the tune's transformation list.
 * The records contain data; functions outside them perform validation
 * and conversion using exhaustive pattern matching.
 */
package org.ymxs;
