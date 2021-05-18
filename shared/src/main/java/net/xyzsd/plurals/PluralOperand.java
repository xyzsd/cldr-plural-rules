// Copyright © 2020 xyzsd
//
// See the COPYRIGHT file at the top-level directory of this
// distribution.
//
// Licensed under the Apache License, Version 2.0 <LICENSE-APACHE or
// http://www.apache.org/licenses/LICENSE-2.0> or the MIT license
// <LICENSE-MIT or http://opensource.org/licenses/MIT>, at your
// option. This file may not be copied, modified, or distributed
// except according to those terms.
package net.xyzsd.plurals;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Wrapper for numbers
 * <p>
 *      PluralOperands contain information about the structure of a number, which can
 *      change plural conventions depending upon the language/locale.
 * </p>
 *
 * <p>
 * A BigDecimal or String can be used instead of numeric types to better establish precision (for
 * example, trailing zeros), for which the handling of plural forms can be locale-dependent.
 * </p>
 *
 * <p>
 * If a number is expressed in compact form, the exponent is suppressed.
 * For example, "2.3 Million" the exponent is 6. This can change pluralization
 * for certain languages. Use fromCompact() methods to explicitly suppress an exponent.
 * </p>
 */
public final class PluralOperand {

    // see: http://unicode.org/reports/tr35/tr35-numbers.html#Language_Plural_Rules
    // deliberately package-private for now
    final double n; // absolute value of input (integer and decimals)
    final long i;   // integer digits of n (also always >= 0)

    final int v;    // count of visible fraction digits WITH trailing zeros
    final int w;    // count of visible fraction digits WITHOUT trailing zeros

    final int f;    // visible fraction digits WITH trailing zeros
    final int t;    // visible fraction digits WITHOUT trailing zeros

    final int e;    // suppressed exponent [added in CLDR 38], synonym for 'c' (!)


    private PluralOperand(double n, long i, int v, int w, int f, int t, int e) {
        this.n = n;
        this.i = i;
        this.v = v;
        this.w = w;
        this.f = f;
        this.t = t;
        this.e = e;
    }


    /**
     * For debugging, not display.
     */
    @Override
    public String toString() {
        return "PluralOperand{" +
                "n=" + n +
                ", i=" + i +
                ", v=" + v +
                ", w=" + w +
                ", f=" + f +
                ", t=" + t +
                ", e=" + e +
                '}';
    }

    /**
     * Create a PluralOperand from a String.
     * <p>
     *     Using a String or BigDecimal instead of a {@code float} or {@code double} allows for
     *     the determination of trailing zeros and visible fraction digits, which can have an impact
     *     on number format selection depending upon the locale.
     * </p>
     *
     * @param s a Number, as a String. Null-safe.
     * @return PluralOperand, or empty Optional if the String cannot be parsed into a BigDecimal
     */
    public static Optional<PluralOperand> from(final String s) {
        if (s == null || s.length() == 0) {
            return Optional.empty();
        }

        try {
            return Optional.of(PluralOperand.from(new BigDecimal( s )));
        } catch(NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Create a PluralOperand from a BigDecimal.
     * <p>
     *     Using a String or BigDecimal instead of a {@code float} or {@code double} allows for
     *     the determination of trailing zeros and visible fraction digits, which can have an impact
     *     on number format selection depending upon the locale.
     * </p>
     *
     * @param input a non-null BigDecimal
     * @return PluralOperand
     */
    public static PluralOperand from(final BigDecimal input) {
        Objects.requireNonNull(input);
        return from(input, 0);
    }


    /**
     * Create a PluralOperand from a {@code double}.
     *
     * <p>
     * It is not possible to determine precision (trailing zeros) from double input.
     * </p>
     *
     * @param input decimal value
     * @return PluralOperand
     */
    public static PluralOperand from(final double input) {
        return from(input, 0);
    }


    /**
     * Explicitly set suppressedExponent for Compact format numbers.
     *
     * The exponent must be explicitly denoted; e.g., fromCompact(1L, 6) is equivalent to "1 Million"
     * However, fromCompact(1000000L, 6) will result in "1000000 million"
     *
     * Note that for {@code fromCompact(1.2, 6)} the operands are (n = 1.2, i = 1200000, c = 6)
     *
     * @param input input as a double
     * @param suppressedExponent (must be between 0 and 21)
     * @return PluralOperand
     */
    public static PluralOperand from(final double input, final int suppressedExponent) {
        checkSE(suppressedExponent);
        return from(BigDecimal.valueOf( input ), suppressedExponent);
    }

    /**
     * Explicitly set suppressedExponent for Compact format numbers.
     * The exponent is explicitly denoted; e.g., fromCompact(1L, 6) is equivalent to "1 Million"
     * However, fromCompact(1000000L, 6) will result in "1000000 million"
     *
     *
     * @param input input as a long
     * @param suppressedExponent (must be between 0 and 21)
     * @return PluralOperand
     */
    public static PluralOperand from(final long input, final int suppressedExponent) {
        checkSE(suppressedExponent);
        long expanded = input;
        if(suppressedExponent > 0) {
            expanded = input * suppressedExponent;
            final long ax = Math.abs( expanded );
            final long ay = Math.abs( suppressedExponent );

            // see: Math.multiplyExact()
            if (((ax | ay) >>> 31 != 0)) {
                if (expanded / suppressedExponent != input) {
                        return from(BigDecimal.valueOf( input ), suppressedExponent);
                }
            }
        }

        expanded = (expanded != Long.MIN_VALUE) ? Math.abs(expanded) : Long.MAX_VALUE;

        return new PluralOperand(
                (double) expanded,
                expanded,
                0, 0, 0, 0,
                suppressedExponent
        );
    }


    /**
     * Explicitly set suppressedExponent for Compact format numbers.
     *
     *
     * @param input input as a BigDecimal
     * @param suppressedExponent (must be between 0 and 21)
     * @return PluralOperand
     */
    public static PluralOperand from(final BigDecimal input, final int suppressedExponent) {
        checkSE(suppressedExponent);

        // TODO: this may not always handle exponential correctly....
        //        since f, t must fit into an int: use 9 decimal places max
        //        so we should set the MathContext appropriately and adjust precision
        //        as needed

        // NOTE:
        //       Converting to a Double/Long may lose precision.
        //       But, these values are *not* used for display, just plural selection.
        //       But we will try to calculate fractional digits as appropriate.
        //
        final BigDecimal absIn = input.movePointRight( suppressedExponent ).abs();

        // these will not be accurate for (very) large numbers. In most cases that will be OK.
        final double n = Math.min(absIn.doubleValue(), Double.MAX_VALUE);  // disallow +infinity
        final long i = absIn.longValue();

        // digits to the right of the decimal point
        final BigDecimal fractional = absIn.remainder( BigDecimal.ONE );

        if(BigDecimal.ZERO.equals( fractional )) {
            return new PluralOperand( n, i, 0, 0, 0, 0, suppressedExponent );
        } else {
            // visible fraction digit count: WITH trailing zeros
            final int v = Math.max(0, absIn.scale());

            // visible fraction digit count: WITHOUT trailing zeros
            final int w = Math.max(0, absIn.stripTrailingZeros().scale());

            // visible fraction digits WITH trailing zeros
            final int f = fractional.movePointRight( fractional.scale() ).intValueExact();

            // visible fraction digits WITHOUT trailing zeros
            final BigDecimal fractionalStripped = fractional.stripTrailingZeros();
            final int t = fractionalStripped.movePointRight( fractionalStripped.scale() ).intValueExact();

            return new PluralOperand( n, i, v, w, f, t , suppressedExponent);
        }
    }

    /*
    notes: visible fraction digits (with/without) trailing zeroes must fit into an int
    therefore: 2 147 483 647
        we can therefore restrict to 9 decimal places


     */


    // suppressed exponent check. Acceptable values: [0,21]
    private static void checkSE(final int i) {
        if(i < 0 || i > 21) {
            throw new IllegalArgumentException("Exponent out of range [0,21]: "+i);
        }
    }
}
