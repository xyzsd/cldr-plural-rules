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
import net.xyzsd.plurals.PluralRule;
import net.xyzsd.plurals.PluralRules;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;


class PluralRuleTest {


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
        System.out.printf("Cardinals: %d", cardinalSamples.size());
        testSamples( PluralRuleType.CARDINAL, cardinalSamples );
    }

    @Test
    void testOrdinalSamples() throws IOException, URISyntaxException {
        final URI resource = this.getClass().getResource( "/ordinal_samples.json" ).toURI();
        Map<String, Map<PluralCategory, List<String>>> ordinalSamples = PluralMaker.readTestJSON(
                Path.of( resource ) );
        System.out.printf("Ordinals: %d", ordinalSamples.size());
        testSamples( PluralRuleType.ORDINAL, ordinalSamples );
    }


    void testSamples(PluralRuleType type, Map<String, Map<PluralCategory, List<String>>> allSamples) {
        // this is written imperatively... because of a casting issue encountered during
        // JSON deserialization and this was the (arguably) more straightforward  workaround

        StringBuilder errors = new StringBuilder();
        for( Map.Entry<String, Map<PluralCategory, List<String>>> langEntry : allSamples.entrySet()) {
            final String lang = langEntry.getKey();
            // <?,..> because Moshi is not reading in PluralCategory enums correctly.. (why?)
            for(Map.Entry<?, List<String>> entry : langEntry.getValue().entrySet()) {
                //final PluralCategory category = entry.getKey();     // issue: fails w/ClassCastException: JSON reader issue (?)
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


}