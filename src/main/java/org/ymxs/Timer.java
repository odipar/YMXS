package org.ymxs;

/** One of the MC68901's four timers, counting down at the divided rate
 *  and raising an interrupt at zero. A row states an effect against one,
 *  and the timer is the effect (SPEC.md 3). */
public enum Timer {
    A, B, C, D
}
