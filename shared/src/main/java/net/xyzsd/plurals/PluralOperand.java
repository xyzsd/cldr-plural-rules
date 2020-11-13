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
 */
public final class PluralOperand {

    // see: http://unicode.org/reports/tr35/tr35-numbers.html#Language_Plural_Rules
    // deliberately package-private for now
    //
    final double n; // absolute value of input (integer and decimals)
    final long i;   // integer digits of n

    final int v;    // count of visible fraction digits WITH trailing zeros
    final int w;    // count of visible fraction digits WITHOUT trailing zeros

    final int f;    // visible fraction digits WITH trailing zeros
    final int t;    // visible fraction digits WITHOUT trailing zeros



    private PluralOperand(double n, long i, int v, int w, int f, int t) {
        this.n = n;
        this.i = i;
        this.v = v;
        this.w = w;
        this.f = f;
        this.t = t;
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

        // TODO: exponentials are not handled correctly (need to rescale... correctly)
        //       can test to see if scale() < 0; then use toPlainString().... but not very mathy
        //       However... scaling may not be sufficient, since an exponential value may not fit in
        //                  a double or long, and truncation may alter plural selection depending upon
        //                  the locale. Further contemplation & testing is needed
        final BigDecimal absIn = input.abs();
        final double n = absIn.doubleValue();
        final long i = absIn.intValue();
        final BigDecimal fractional = absIn.remainder( BigDecimal.ONE );

        if(BigDecimal.ZERO.equals( fractional )) {
            return new PluralOperand( n, i, 0, 0, 0, 0 );
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

            return new PluralOperand( n, i, v, w, f, t );
        }
    }

    /**
     * Create a PluralOperand from a {@code long}.
     *
     * @param input integral value
     * @return PluralOperand
     */
    public static PluralOperand from(final long input) {
        final long absIn = (input != Long.MIN_VALUE) ? Math.abs(input) : Long.MAX_VALUE;
        return new PluralOperand(
                (double) absIn,
                absIn,
                0, 0, 0, 0
        );
    }

    /**
     * Create a PluralOperand from a {@code double}.
     *
     * <p>
     * NOTE: It is not possible to determine precision (trailing zeros) from double input.
     * </p>
     *
     * @param input decimal value
     * @return PluralOperand
     */
    public static PluralOperand from(final double input) {
        final double absIn = (input != Double.MIN_VALUE) ? Math.abs(input) : Double.MAX_VALUE;
        return new PluralOperand(
                absIn,
                (long) absIn,
                0, 0, 0, 0
        );
    }

}
