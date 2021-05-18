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

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;


/**
 * <p>
 * Wrapper class for PluralRules, designed for ease-of-use and safety.
 * </p>
 * <p>
 * A PluralRule is created by using a {@code create()} method. Create methods will return empty Optionals
 * if a no plural rule is found for a given language. Optional method chaining can be used to return a default rule,
 * or {@code createOrDefault()} can be used (less error-prone).
 * </p>
 * <p>
 * For example:
 * {@code
 * PluralRule rule = PluralRule.createOrDefault(Locale.ENGLISH, PluralRuleType.ORDINAL);
 * assert (rule.select(1) == PluralCategory.ONE);                  // e.g., "1 day"
 * assert (rule.select(10) == PluralCategory.OTHER);               // e.g., "10 days"
 * assert (rule.select("1100.00") == PluralCategory.OTHER);        // e.g., "1100.00 days"
 * <p>
 * PluralRule rule = PluralRule.createOrDefault(Locale.ENGLISH, PluralRuleType.CARDINAL);
 * assert (rule.select(1) == PluralCategory.ONE);              // e.g, "1st"   use 'st' suffix
 * assert (rule.select(2) == PluralCategory.TWO);              // e.g., "2nd"  use 'nd' suffix
 * assert (rule.select(3) == PluralCategory.FEW);              // e.g., "3rd"  use 'rd' suffix
 * assert (rule.select(4) == PluralCategory.OTHER);            // e.g., "4th"  use 'th' suffix
 * assert (rule.select(43) == PluralCategory.FEW);             // e.g., "43rd"
 * assert (rule.select(50) == PluralCategory.OTHER);           // e.g., "50th"
 * }
 * </p>
 * <p>
 * The PluralCategory returned determines how subsequent localization logic then handles the number, ranking,
 * or quantity, which is locale-dependent.
 * </p>
 * <p>
 * When matching a language, the empty String "" and String "root" are equivalent to Locale.ROOT.
 * The Locale.ROOT rule can be used if there is no match for a language. This is particularly important when
 * using cardinal rules; many languages do not have specific cardinal rules.
 * </p>
 */

public final class PluralRule {

    /**
     * Locale.ROOT (default rule) for ordinals
     */
    public final static PluralRule ROOT_ORDINAL = createRoot( PluralRuleType.ORDINAL );

    /**
     * Locale.ROOT (default rule) for cardinals
     */
    public final static PluralRule ROOT_CARDINAL = createRoot( PluralRuleType.CARDINAL );

    private final PluralRuleType type;
    private final Function<PluralOperand, PluralCategory> rule;
    private final String lang;      // nonnull, but may be empty
    private final String region;    // nullable


    private PluralRule(PluralRuleType type, Function<PluralOperand, PluralCategory> rule, String lang, String region) {
        this.type = type;
        this.rule = rule;
        this.lang = lang;
        this.region = region;
    }


    /**
     * The Locale for this plural rule.
     * <p>
     * This may not be the same Locale as supplied to the {@link PluralRule#create} supplied Locale.
     * For example, both "en-US" and "en-GB" will return a Locale with only the language "en" specified,
     * since the region (in the case of English) is not relevant to plural selection.
     * </p>
     *
     * @param pr PluralRule for which we should determine the Locale. Null not allowed.
     * @return Locale for this Plural rule. Never null.
     */
    @CheckReturnValue
    @Nonnull
    public static Locale locale(final PluralRule pr) {
        Objects.requireNonNull( pr );
        assert (pr.lang != null);
        if (pr == ROOT_ORDINAL || pr == ROOT_CARDINAL || "".equals( pr.lang )) {
            return Locale.ROOT;
        } else {
            return new Locale.Builder().setLanguage( pr.lang ).setRegion( pr.region ).build();
        }
    }


    /**
     * Returns the PluralRuleType corresponding to this PluralRule. Never null.
     *
     * @return PluralRuleType corresponding to this PluralRule.
     */
    @CheckReturnValue
    @Nonnull
    public PluralRuleType pluralRuleType() {
        return type;
    }


    /**
     * Returns the rule as a Function. Never null.
     *
     * @return PluralRule (as a Function)
     */
    @CheckReturnValue
    @Nonnull
    public Function<PluralOperand, PluralCategory> rule() {
        return rule;
    }


    /**
     * Determine the PluralCategory for the given PluralOperand.
     * <p>
     * Base convenience method, equivalent to {@code rule().apply(op)}.
     * Null values are not allowed.
     * </p>
     *
     * @param op PluralOperand
     * @return PluralCategory for the given value.
     */
    @CheckReturnValue
    @Nonnull
    public PluralCategory select(PluralOperand op) {
        Objects.requireNonNull( op );
        return rule.apply( op );
    }


    /**
     * Determine the PluralCategory for the given numeric String.
     * <p>
     * Using String or BigDecimal PluralOperands permits the retention of
     * precision (trailing zeros), which can affect localization.
     * </p>
     * <p>
     * This will return an empty Optional if the String cannot be
     * successfully parsed.
     * </p>
     *
     * @param value Numeric value, as a String
     * @return Optional containing the PluralCategory. Empty if String parsing fails.
     */
    @CheckReturnValue
    @Nonnull
    public Optional<PluralCategory> select(String value) {
        Objects.requireNonNull( value );
        return PluralOperand.from( value ).map( rule );
    }

    /**
     * Determine the PluralCategory for the given BigDecimal.
     * <p>
     * Using String or BigDecimal PluralOperands permits the retention of
     * precision (trailing zeros), which can affect localization.
     * </p>
     *
     * @param value value as a BigDecimal
     * @return PluralCategory for the given value.
     */
    @CheckReturnValue
    @Nonnull
    public PluralCategory select(BigDecimal value) {
        return rule.apply( PluralOperand.from( value ) );
    }


    /**
     * Determine the PluralCategory for the given Number.
     *
     * <p>
     * BigIntegers and BigDecimal types are handled specially.
     * All other Number types will be handled as a double.
     * </p>
     *
     * @param input value as a Number
     * @return PluralCategory for the given value.
     */
    @CheckReturnValue
    @Nonnull
    public PluralCategory selectNumber(final Number input) {
        if (input instanceof BigDecimal) {
            return rule.apply( PluralOperand.from( (BigDecimal) input ) );
        } else if (input instanceof BigInteger) {
            return rule.apply( PluralOperand.from(
                    new BigDecimal( (BigInteger) input ) )
            );
        } else {
            return rule.apply( PluralOperand.from( input.doubleValue() ) );
        }
    }

    /**
     * Determine the PluralCategory for the given "compact" BigDecimal
     *
     * @param value              input value
     * @param suppressedExponent suppressed exponent (range: 0-21)
     * @return PluralOperand
     */
    @CheckReturnValue
    @Nonnull
    public PluralCategory selectCompact(final BigDecimal value, final int suppressedExponent) {
        return rule.apply( PluralOperand.from( value, suppressedExponent ) );
    }


    /**
     * Determine the PluralCategory for the given "compact" long
     *
     * @param value              input value
     * @param suppressedExponent suppressed exponent (range: 0-21)
     * @return PluralOperand
     */
    @CheckReturnValue
    @Nonnull
    public PluralCategory selectCompact(final long value, final int suppressedExponent) {
        return rule.apply( PluralOperand.from( value, suppressedExponent ) );
    }

    /**
     * Determine the PluralCategory for the given "compact" double
     *
     * @param value              input value
     * @param suppressedExponent suppressed exponent (range: 0-21)
     * @return PluralOperand
     */
    @CheckReturnValue
    @Nonnull
    public PluralCategory selectCompact(final double value, final int suppressedExponent) {
        return rule.apply( PluralOperand.from( value, suppressedExponent ) );
    }


    /**
     * Determine the PluralCategory for the given {@code long} value.
     *
     * @param value value as a double
     * @return PluralCategory for the given value.
     */
    @CheckReturnValue
    @Nonnull
    public PluralCategory select(double value) {
        if (Double.isFinite( value )) {
            return rule.apply( PluralOperand.from( value ) );
        }
        return PluralCategory.OTHER;
    }


    /**
     * Create the PluralRule for a given language and (optionally) region.
     * <p>
     * Note that while the language must match, the region is optional.
     * Unspecified or invalid regions will match the language type.
     * Empty String values are permitted; null values are not.
     * </p>
     *
     * @param language BCP 47 language code (lower case), "root", or empty String ""
     * @param region   two-letter ISO 3166 region <b>normalized to upper case</b>, or empty String ""
     * @param type     PluralRuleType (ORDINAL or CARDINAL)
     * @return PluralRule for given language and PluralRuleType. Empty if unmatched language or no PluralType for the
     * given language.
     */
    @CheckReturnValue
    public static Optional<PluralRule> create(String language, String region, PluralRuleType type) {
        Objects.requireNonNull( language );
        Objects.requireNonNull( region );
        Objects.requireNonNull( type );
        return Optional.ofNullable( ruleByType( language, region, type ) )
                .map(
                        rFn -> new PluralRule( type, rFn, language, region )
                );
    }

    /**
     * Create the PluralRule for a given Locale.
     *
     * @param locale Locale to match language and (possibly) country. Null not permitted.
     * @param type   PluralRuleType (ORDINAL or CARDINAL). Null not permitted.
     * @return PluralRule for given language and PluralRuleType. Empty if unmatched language or no PluralType for the
     * given language.
     */
    @CheckReturnValue
    public static Optional<PluralRule> create(Locale locale, PluralRuleType type) {
        Objects.requireNonNull( locale );
        Objects.requireNonNull( type );
        return create( locale.getLanguage(), locale.getCountry(), type );
    }


    /**
     * Create the PluralRule for a given language and (optionally) region.
     * <p>
     * If the rule cannot be created, or no rule exists, the default (Locale.ROOT)
     * rule is returned.
     * </p>
     *
     * @param locale Locale to match language and (possibly) country
     * @param type   PluralRuleType (ORDINAL or CARDINAL)
     * @return PluralRule for matching language and PluralRuleType, or the Locale.ROOT rule if unmatched
     */
    @CheckReturnValue
    @Nonnull
    public static PluralRule createOrDefault(Locale locale, PluralRuleType type) {
        return create( locale, type ).orElse( createDefault( type ) );
    }


    /**
     * Create the PluralRule for a given language and (optionally) region.
     * <p>
     * If the rule cannot be created, or no rule exists, the default (Locale.ROOT)
     * rule is returned.
     * </p>
     *
     * @param language BCP 47 language code (lower case), "root", or empty String ""
     *                 Null is not permitted.
     * @param region   two-letter ISO 3166 region <b>normalized to upper case</b>, or empty String "".
     *                 Null is not permitted
     * @param type     PluralRuleType (ORDINAL or CARDINAL). Null is not permitted.
     * @return PluralRule for given language and PluralRuleType. Empty if unmatched language or no PluralType for the
     * given language.
     */
    @CheckReturnValue
    @Nonnull
    public static PluralRule createOrDefault(String language, String region, PluralRuleType type) {
        return create( language, region, type ).orElse( createDefault( type ) );
    }


    /**
     * The 'default' PluralRule, equivalent to the rule for Locale.ROOT
     * <p>
     * The default PluralRule always returns {@code PluralCategory.OTHER} for {@code select()}.
     * This can be used to guarantee a result:
     * {@code PluralRule myRule = create(Locale.EXAMPLE, PluralRule.ORDINAL).orElse(createDefault(PluralRule.ORDINAL)}
     * </p>
     *
     * @param type PluralRuleType
     * @return default PluralRule
     */
    @CheckReturnValue
    public static PluralRule createDefault(PluralRuleType type) {
        Objects.requireNonNull( type );
        switch (type) {
            case CARDINAL:
                return ROOT_CARDINAL;
            case ORDINAL:
                return ROOT_ORDINAL;
            default:
                throw new IllegalArgumentException( type.toString() );
        }
    }


    // can return null..
    private static Function<PluralOperand, PluralCategory> ruleByType(String language, String region, PluralRuleType type) {
        switch (type) {
            case CARDINAL:
                return PluralRules.selectCardinal( language, region );
            case ORDINAL:
                return PluralRules.selectOrdinal( language, region );
            default:
                throw new IllegalArgumentException( type.toString() );
        }
    }

    // create Locale.ROOT 'default' rule
    private static PluralRule createRoot(PluralRuleType type) {
        Function<PluralOperand, PluralCategory> rFn = ruleByType( "", "", type );
        assert (rFn != null);
        PluralRule pluralRule = new PluralRule(
                type,
                (x) -> PluralCategory.OTHER,
                "",
                ""
        );
        return pluralRule;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PluralRule that = (PluralRule) o;
        return type == that.type &&
                rule.equals( that.rule ) &&
                Objects.equals( lang, that.lang ) &&
                Objects.equals( region, that.region );
    }

    @Override
    public int hashCode() {
        return Objects.hash( type, rule, lang, region );
    }

    @Override
    public String toString() {
        return "PluralRule{" +
                "type=" + type +
                ", rule=" + rule +
                ", lang='" + lang + '\'' +
                ", region='" + region + '\'' +
                '}';
    }

}
