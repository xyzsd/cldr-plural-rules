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

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Language-dependent plural forms, per CLDR specifications.
 */
public enum PluralCategory {
    ZERO,
    ONE,
    TWO,
    FEW,
    MANY,
    OTHER;


    private static final Map<String, PluralCategory> map = Arrays.stream( values() )
            .collect( Collectors.toMap( Enum::toString, Function.identity() ) );

    /**
     * Return the constant that (exactly) matches the input String.
     * Does not throw exceptions, as valueOf() does.
     *
     * @param s case-sensitive input to match
     * @return {@code Optional<PluralCategory>} or an empty {@code Optional}
     */
    @SuppressWarnings("unused")
    public static Optional<PluralCategory> ifPresent(final String s) {
        return Optional.ofNullable( map.get( s ) );
    }


    /**
     * Return the constant that matches the input String, via a case-insensitive comparison.
     * Does not throw exceptions, as valueOf() does.
     *
     * @param s case-sensitive input to match
     * @return {@code Optional<PluralCategory>} or an empty {@code Optional}
     */
    @SuppressWarnings("unused")
    public static Optional<PluralCategory> ifPresentIgnoreCase(final String s) {
        final PluralCategory pc = map.get( s );
        if (pc != null) {
            return Optional.of(pc);
        } else {
            for (final PluralCategory iter : values()) {
                if (iter.name().equalsIgnoreCase( s )) {
                    return Optional.of( iter );
                }
            }
        }
        return Optional.empty();
    }
}
