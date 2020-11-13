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
 *
 */
public enum PluralCategory {
    ZERO,
    ONE,
    TWO,
    FEW,
    MANY,
    OTHER;


    private static final Map<String,PluralCategory> map = Arrays.stream(values())
            .collect( Collectors.toMap(Enum::toString, Function.identity()));

    /**
     * Return the constant that (exactly) matches the input String.
     * Does not throw exceptions, as valueOf() does.
     * <p>
     *     To perform a case-insensitive match, use {@code toUpperCase(Locale.ENGLISH)} as the input.
     * </p>
     *
     * @param s case-sensitive input to match
     * @return {@code Optional<PluralCategory>} or an empty {@code Optional}
     */
    public static Optional<PluralCategory> from(final String s) {
        return Optional.ofNullable( map.get(s) );
    }

}
