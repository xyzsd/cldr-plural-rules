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

import net.xyzsd.plurals.maker.PluralMaker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;


public class PluralRuleTest {


    // basic tests: cardinal
    @Test
    void selectCardinal() {
        Assertions.assertThrows( NullPointerException.class, () -> PluralRules.selectCardinal( null, null ) );
        Assertions.assertNotNull( PluralRules.selectCardinal( "root", null ) );
        Assertions.assertNotNull( PluralRules.selectCardinal( "", null ) ); // root alias
        Assertions.assertNotNull( PluralRules.selectCardinal( "en", null ) );
        Assertions.assertNotNull( PluralRules.selectCardinal( "en", "invalid" ) );
        Assertions.assertNotNull( PluralRules.selectCardinal( "pt", null ) );
        Assertions.assertNotNull( PluralRules.selectCardinal( "pt", "invalid" ) );
        Assertions.assertNotNull( PluralRules.selectCardinal( "pt", "PT" ) );

        Assertions.assertNotEquals( PluralRules.selectCardinal( "pt", "" ),
                PluralRules.selectCardinal( "pt", "PT" ) );

    }


    // basic tests: ordinal
    @Test
    void selectOrdinal() {
        Assertions.assertThrows( NullPointerException.class, () -> PluralRules.selectOrdinal( null, null ) );
        Assertions.assertNotNull( PluralRules.selectOrdinal( "root", null ) );
        Assertions.assertNotNull( PluralRules.selectOrdinal( "", null ) ); // root alias
        Assertions.assertNotNull( PluralRules.selectOrdinal( "en", null ) );
        Assertions.assertNotNull( PluralRules.selectOrdinal( "en", "invalid" ) );
        Assertions.assertNotNull( PluralRules.selectOrdinal( "pt", null ) );
        Assertions.assertNotNull( PluralRules.selectOrdinal( "pt", "invalid" ) );
        Assertions.assertNotNull( PluralRules.selectOrdinal( "pt", "PT" ) );

        // for ordinals, there is no 'pt-PT'; same form as 'pt'
        Assertions.assertEquals( PluralRules.selectOrdinal( "pt", "" ),
                PluralRules.selectOrdinal( "pt", "PT" ) );

        // no 'ak' ordinal (CLDR 37), though there is an 'ak' cardinal; just one example of many
        Assertions.assertNull( PluralRules.selectOrdinal( "ak", "" ) );

    }



    @Test
    void testDefaultRule() {
        PluralRule rule = PluralRule.createDefault( PluralRuleType.CARDINAL );
        Assertions.assertNotNull( rule );
        Assertions.assertEquals( PluralCategory.OTHER, rule.select( 0 ) );
        Assertions.assertEquals( PluralCategory.OTHER, rule.select( 1 ) );
        Assertions.assertEquals( PluralCategory.OTHER, rule.select( 11 ) );
        Assertions.assertEquals( PluralCategory.OTHER, rule.select( 15 ) );
        Assertions.assertEquals( PluralCategory.OTHER, rule.select( 734823 ) );
    }


    @Test
    void testCardinalSamples() throws IOException, URISyntaxException {
        final URI resource = this.getClass().getResource( "/cardinal_samples.json" ).toURI();
        Map<String, Map<PluralCategory, List<String>>> cardinalSamples = PluralMaker.readTestJSON(
                Path.of( resource ) );
        System.out.printf("Cardinals: %d\n", cardinalSamples.size());
        testSamples( PluralRuleType.CARDINAL, cardinalSamples );
    }

    @Test
    void testOrdinalSamples() throws IOException, URISyntaxException {
        final URI resource = this.getClass().getResource( "/ordinal_samples.json" ).toURI();
        Map<String, Map<PluralCategory, List<String>>> ordinalSamples = PluralMaker.readTestJSON(
                Path.of( resource ) );
        System.out.printf("Ordinals: %d\n", ordinalSamples.size());
        testSamples( PluralRuleType.ORDINAL, ordinalSamples );
    }

    @Test
    void testCompactSamples() throws IOException, URISyntaxException {
        final URI resource = this.getClass().getResource( "/compact_cardinal_samples.json" ).toURI();
        Map<String, Map<PluralCategory, List<String>>> cardinalCompactSamples = PluralMaker.readTestJSON(
                Path.of( resource ) );
        System.out.printf("Compact Cardinals: %d\n", cardinalCompactSamples.size());
        testCompactSamples( PluralRuleType.CARDINAL, cardinalCompactSamples );
    }


    void testSamples(PluralRuleType type, Map<String, Map<PluralCategory, List<String>>> allSamples) {
        // this is written imperatively... because of a casting issue encountered during
        // JSON deserialization and this was the (arguably) more straightforward  workaround

        StringBuilder errors = new StringBuilder();
        for( Map.Entry<String, Map<PluralCategory, List<String>>> langEntry : allSamples.entrySet()) {
            final String lang = langEntry.getKey();

            // <?,..> because we are not reading in PluralCategory enums correctly.. (why?)
            // they are ending up as Strings, and not converted to PluralCategory
            for(Map.Entry<?, List<String>> entry : langEntry.getValue().entrySet()) {
                // assuming Map.Entry<PluralCategory, List<String>> ... :
                //   final PluralCategory category = entry.getKey();     // issue: fails w/ClassCastException
                final PluralCategory category = PluralCategory.valueOf( (String) entry.getKey() );

                final List<String> samples = entry.getValue();

                // we only test languages that have rules
                PluralRule pluralRule = PluralRule.create(
                        PluralMaker.baseLanguage( lang ),
                        PluralMaker.extractRegion( lang ), type ).orElseThrow(
                        () -> new IllegalStateException("No rule for: "+lang));

                for(String sample : samples) {
                    final PluralCategory result = pluralRule.select( sample ).orElseThrow();
                    if(!category.equals(result)) {
                        errors.append(String.format("Failure for '%s': '%s' sample '%s'\n",
                                lang, category, sample));
                    }
                }
            }
        }

        Assertions.assertEquals( "" , errors.toString());
    }

    // compact cardinals
    void testCompactSamples(PluralRuleType type, Map<String, Map<PluralCategory, List<String>>> allSamples) {
        // copypasta of above... with same issues (see above!)

        StringBuilder errors = new StringBuilder();
        for( Map.Entry<String, Map<PluralCategory, List<String>>> langEntry : allSamples.entrySet()) {
            final String lang = langEntry.getKey();
            for(Map.Entry<?, List<String>> entry : langEntry.getValue().entrySet()) {
                final PluralCategory category = PluralCategory.valueOf( (String) entry.getKey() );

                final List<String> samples = entry.getValue();

                // we only test languages that have rules
                PluralRule pluralRule = PluralRule.create(
                        PluralMaker.baseLanguage( lang ),
                        PluralMaker.extractRegion( lang ), type ).orElseThrow(
                        () -> new IllegalStateException("No rule for: "+lang));

                for(String sample : samples) {
                    final PluralOperand operand = parseCompact(sample);
                    final PluralCategory result = pluralRule.select( operand );
                    if(!category.equals(result)) {
                        errors.append(String.format("Failure for '%s': '%s' sample '%s' (error value:'%s','%s')\n",
                                lang, category, sample, result, operand));

                    }
                }
            }
        }

        Assertions.assertEquals( "" , errors.toString());
    }


    private PluralOperand parseCompact(final String in) {
        int split = in.indexOf( 'c' );
        if(split <= 0) {
            throw new IllegalStateException("Invalid compact form : "+in);
        }
        String left = in.substring( 0, split );
        final int exp = Integer.parseInt( in.substring( split+1 ) );

        if(left.indexOf( '.' ) >= 0) {
            return PluralOperand.from( Double.parseDouble( left ), exp );
        } else {
            return PluralOperand.from( Integer.parseInt(left), exp );
        }
    }

}