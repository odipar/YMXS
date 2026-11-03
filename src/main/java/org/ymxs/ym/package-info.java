/**
 * Imports YM3!/YM3b/YM5!/YM6! register dumps into YMXS tunes.
 *
 * <p>Frame register values become tune rows. Effect slots become
 * starts, retunes and stops; distinct slot sounds become sources.
 * Imported starts use identity for every source value and introduce no
 * transformations.
 *
 * <p>doc/ym.md defines the conversion and archive handling. This
 * package is separate from the tune data structure and provides the
 * dump data used by its measurements and tests.
 */
package org.ymxs.ym;
