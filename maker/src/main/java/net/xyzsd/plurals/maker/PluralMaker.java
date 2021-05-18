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
package net.xyzsd.plurals.maker;

import com.squareup.javapoet.*;
import com.squareup.moshi.*;
import com.squareup.moshi.adapters.EnumJsonAdapter;
import net.xyzsd.plurals.PluralCategory;
import net.xyzsd.plurals.PluralOperand;

import javax.annotation.CheckReturnValue;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

/**
 * Create CLDR PluralRules as Java source code from CLDR ordinal and cardinal plural definition JSON rule specifications.
 * <p>
 * This generates rules as efficient, compilable code. Test data (from the CLDR Samples) are also generated.
 * </p>
 * <p>
 * Generation approach is via parsing/regex, rather than AST + transformation, for better or worse.
 * </p>
 *
 */
public class PluralMaker {

    // prefixes for rule names
    static final String CARDINAL_RULE_PREFIX = "_C_RULE_";
    static final String ORDINAL_RULE_PREFIX = "_O_RULE_";



    static List<String> ruleToSamples(String ruleIn) {
        final int idx = ruleIn.indexOf( '@' );
        if (idx < 0) {
            return List.of();
        }

        return Arrays.stream( ruleIn.substring( idx ).split( "@integer|@decimal|," ) )
                .map( String::trim )
                .filter( Predicate.not( String::isEmpty ) )
                .filter( Predicate.not( "\u2026"::equals ) )         // \u2026 : "…" horizontal ellipsis
                .filter( Predicate.not( PluralMaker::containsSuppressedExponent ) )
                .flatMap( PluralMaker::expand )
                .collect( toList() );
    }

    static List<String> ruleToCompactSamples(String ruleIn) {
        final int idx = ruleIn.indexOf( '@' );
        if (idx < 0) {
            return List.of();
        }

        final List<String> collect = Arrays.stream( ruleIn.substring( idx ).split( "@integer|@decimal|," ) )
                .map( String::trim )
                .filter( PluralMaker::containsSuppressedExponent )
                .map( s -> s.replace( 'e', 'c' ) )
                .collect( toList() );

        return collect;
    }



    // compact form can be 'e' (per spec) or 'c' (actual) .... currently 'c' is used
    static boolean containsSuppressedExponent(final String in) {
        return (in.indexOf( 'e' ) > 0 || in.indexOf( 'c' ) > 0);
    }

    static Stream<String> expand(String s) {
        if (s.indexOf( '~' ) == -1) {
            return Stream.of( s );
        }

        // assumes # decimal places (fractional component) same for start and stop
        final String[] split = s.split( "~" );
        final BigDecimal start = new BigDecimal( split[0] );
        final BigDecimal stop = new BigDecimal( split[1] );
        final BigDecimal step = start.ulp();

        assert !BigDecimal.ZERO.equals( step );
        assert start.ulp().equals(stop.ulp());

        return Stream.iterate(
                start,
                d -> d.compareTo( stop ) < 0,
                next -> next.add( step )
        ).map( BigDecimal::toPlainString );
    }


    static Map<PluralCategory, List<String>> rulesetToSamples(Ruleset ruleset,
                                                              Function<String, List<String>> function) {
        return ruleset.rawRules.entrySet().stream()
                .collect( toMap(
                        Map.Entry::getKey,
                        entry -> function.apply( entry.getValue() )
                ) );
    }

    // pass this method a: final Map<String, Ruleset> ordinals = cldrSupplemental.supplemental.ordinals
    // the resulting structure is the test data for ordinals or cardinals
    static Map<String, Map<PluralCategory, List<String>>> makeTests(final Map<String, Ruleset> rulesetMap) {


        return rulesetMap.entrySet().stream()
                .collect( toMap(
                        Map.Entry::getKey,
                        entry -> rulesetToSamples(
                                entry.getValue(),
                                PluralMaker::ruleToSamples
                        )
                ) );
    }

    static Map<String, Map<PluralCategory, List<String>>> makeCompactTests(final Map<String, Ruleset> rulesetMap) {
        // this should be refactored likely using map.merge() instead
        Map<String, Map<PluralCategory, List<String>>> compactTests = rulesetMap.entrySet().stream()
                .collect( toMap(
                        Map.Entry::getKey,
                        entry -> rulesetToSamples(
                                entry.getValue(),
                                PluralMaker::ruleToCompactSamples
                        )
                ) );

        // compact forms are uncommon. if an entry has no compact forms,
        // eliminate it from the map.
        // e.g.: "ksh":{"ZERO":[],"ONE":[],"OTHER":[]}
        //  "fr":{"ONE":[],"MANY":["1c6","2c6","3c6","4c6","5c6","6c6","1.0000001c6","1.1c6","2.0000001c6","2.1c6","3.0000001c6","3.1c6"],"OTHER":["1c3","2c3","3c3","4c3","5c3","6c3","1.0001c3","1.1c3","2.0001c3","2.1c3","3.0001c3","3.1c3"]}
        // e.g.: we would eliminate 'ksh' entirely, and fr:"ONE' entry
        Map<String, Map<PluralCategory, List<String>>> filtered = new HashMap<>();
        for(Map.Entry<String, Map<PluralCategory, List<String>>> entry : compactTests.entrySet()) {
            final Map<PluralCategory, List<String>> pcMap = entry.getValue();

            if(!pcMap.values().stream().allMatch( List::isEmpty )) {
                // at least one nonempty compact value
                final Map<PluralCategory, List<String>> collect = pcMap.entrySet().stream()
                        .filter( x -> !x.getValue().isEmpty() )
                        .collect( toMap( Map.Entry::getKey, Map.Entry::getValue ) );
                filtered.put(entry.getKey(), collect);
            }
        }
        return filtered;
    }

    // for json
    static class CLDRSupplemental {
        CLDRPlurals supplemental;


        int version() {
            return Integer.parseInt( supplemental.version.get( "_cldrVersion" ) );
        }

        @Override
        public String toString() {
            return "CLDRSupplemental{" +
                    "supplemental=" + supplemental +
                    '}';
        }
    }

    // for json
    static class CLDRPlurals {
        Map<String, String> version;

        // only one of these is read at a time ... the other will be null
        @Json(name = "plurals-type-cardinal")
        Map<String, Ruleset> cardinals;
        @Json(name = "plurals-type-ordinal")
        Map<String, Ruleset> ordinals;

        @Override
        public String toString() {
            return "CLDRPlurals{" +
                    "version=" + version +
                    ", cardinals=" + cardinals +
                    ", ordinals=" + ordinals +
                    '}';
        }
    }

    static class PluralCategoryAdapter {

        @ToJson
        String toJson(PluralCategory c) {
            return c.name();
        }

        @FromJson
        PluralCategory fromJson(String s) {
            // try general case (needed for test data), then CLDR-specific
            return PluralCategory.ifPresentIgnoreCase( s )
                    .or( () -> PluralCategory.ifPresentIgnoreCase(
                            s.substring( s.lastIndexOf( '-' ) + 1 ) )
                    )
                    .orElseThrow();
        }
    }


    static class RulesetAdapter {
        @ToJson
        String toJson(Ruleset r) {
            throw new UnsupportedOperationException();
        }

        @FromJson
        Ruleset fromJson(Map<PluralCategory, String> map) {
            return new Ruleset( map );
        }
    }


    // NOTE: ruleset ONLY depends upon rawRules for equality/hashcode
    static class Ruleset {
        // TreeMap : used to ensure ordering (by natural order of PluralCategory)
        final TreeMap<PluralCategory, String> rawRules;  // must be sorted..

        // nothing below here matters for object equality, and may be null
        FieldSpec spec;         // lambda spec (javapoet)
        String ruleName;        // lambda name


        Ruleset(Map<PluralCategory, String> map) {
            this.rawRules = new TreeMap<>( map );
        }

        @Override
        public String toString() {
            return "Ruleset{" +
                    ", rawRules=" + rawRules.size() +
                    ", ruleName='" + ruleName + '\'' +
                    ", spec=" + spec +
                    '}';
        }

        /**
         * ONLY depends upon rawRules
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Ruleset ruleset = (Ruleset) o;
            return rawRules.equals( ruleset.rawRules );
        }

        /**
         * ONLY depends upon rawRules
         */
        @Override
        public int hashCode() {
            return Objects.hash( rawRules );
        }
    }


    static CLDRSupplemental readJSON(Path path) throws IOException {
        Moshi moshi = new Moshi.Builder()
                .add( new PluralCategoryAdapter() )
                .add( new RulesetAdapter() )
                .build();
        return moshi.adapter( CLDRSupplemental.class ).fromJson( Files.readString( path ) );
    }


    static Map<Ruleset, Set<String>> createRules(final Map<String, Ruleset> inputMap, final String prefix) {
        Map<String, Ruleset> rulesetMap = new HashMap<>( inputMap );

        // alias empty string "" to "root" locale
        if (rulesetMap.containsKey( "root" )) {
            rulesetMap.put( "", rulesetMap.get( "root" ) );
        }

        // group similar
        final Map<Ruleset, Set<String>> map = rulesetMap.entrySet().stream().collect( groupingBy(
                Map.Entry::getValue,
                mapping( Map.Entry::getKey, toSet() )
        ) );

        // name the rules, and create lambdas. !! we can do this because key equality/hashcode is not changed !!
        AtomicInteger count = new AtomicInteger( 0 );
        map.forEach( (ruleset, v) -> {
            ruleset.ruleName = prefix + count.getAndIncrement();
            createFunctions( ruleset );
        } );

        return map;
    }

    static void createFunctions(final Ruleset r) {
        CodeBlock.Builder cbb = CodeBlock.builder();

        int count = 0;
        Map<String, String> modMap = new HashMap<>();
        for (Map.Entry<PluralCategory, String> entry : r.rawRules.entrySet()) {
            String rule = entry.getValue().split( "@", 2 )[0].trim();

            // modulo extractor. Replace with variable name; we can re-substitute the name later OR define as a
            // constant expression prior to using the value.
            rule = Pattern.compile( "([nivwfte]) % (\\d+)" ).matcher( rule ).replaceAll( o -> {
                String name = "mod_" + o.group( 1 ) + o.group( 2 );
                String expr = "(op." + o.group( 1 ) + " % " + o.group( 2 ) + ")";
                modMap.put( name, expr );
                return name;
            } );

            // extract any 'complex' rule where:
            //      left hand side (LHS) is a variable OR modulo temp name (see above: e.g., 'mod_i1000')
            //      right hand side (RHS) is a set, range, or mix of sets and ranges
            // this pattern is currently dependent on specific whitespace
            rule = Pattern.compile( "([nivwfte]|mod_[nivwfte]\\d+) (=|!=) (\\d+[.,][.,\\d]+)" ).matcher( rule ).replaceAll( m -> {
                final String lhs = m.group( 1 ).startsWith( "mod_" ) ? m.group( 1 ) : "op." + m.group( 1 );
                final String eq = m.group( 2 ).startsWith( "!" ) ? "!=" : "==";
                final String[] rhs = m.group( 3 ).split( "," );
                final String negate = eq.startsWith( "!" ) ? "!" : "";

                // "==" or "!=" will always apply to the entire RHS
                // So, negate prior to grouping; if there are multiple expressions in the RHS
                // we can then join with '||' for both negated and non-negated cases.
                String result = negate + "(";

                int exprCount = 0;
                for (String s : rhs) {
                    final String[] split = s.split( Pattern.quote( ".." ) );
                    if (split.length == 2) {
                        // range of items
                        if (exprCount > 0) {
                            result += " || ";
                        }
                        result += "contains(" + lhs + ", " + split[0] + ", " + split[1] + ")";
                    } else if (split.length == 1) {
                        // explicit item in a set
                        if (exprCount > 0) {
                            result += " || ";
                        }
                        result += "(" + lhs + " == " + split[0] + ")";
                    } else {
                        throw new IllegalStateException( s );
                    }
                    exprCount++;
                }

                result += ")";
                return result;
            } );

            // fixup variables.
            rule = rule.replaceAll( "^([nivwfte])", "op.$1" );
            rule = rule.replaceAll( "\\s([nivwfte])", " op.$1" );

            rule = rule.replace( " = ", " == " );
            rule = rule.replace( " or ", " || " );
            rule = rule.replace( " and ", " && " );

            if (count == 0) {
                cbb.beginControlFlow( "if ($L)", rule );
            } else if (count == r.rawRules.size() - 1) {
                cbb.nextControlFlow( "else" );
            } else {
                cbb.nextControlFlow( "else if ($L)", rule );
            }

            cbb.addStatement( "return PluralCategory.$L", entry.getKey().name() );

            count++;
        }

        cbb.endControlFlow();

        // simple case : no control flow:  (op) -> PluralCategory.OTHER;
        if (r.rawRules.size() == 1) {
            assert r.rawRules.containsKey( PluralCategory.OTHER );
            cbb.clear();
            cbb.addStatement( "return PluralCategory.OTHER" );
        }

        // encapsulate in lambda definition; adding modulo defs (if any)
        CodeBlock.Builder lambdaBuilder = CodeBlock.builder();
        lambdaBuilder.add( "(op) -> { $W$>" );

        modMap.forEach( (name, expr) -> {
            char typeChar = name.charAt( 4 );
            String type = (typeChar == 'n') ? "double" : (typeChar == 'i' ? "long" : "int");
            lambdaBuilder.addStatement( "final $L $L = $L", type, name, expr );
        } );

        lambdaBuilder.add( cbb.build() );
        lambdaBuilder.add( "$<}" );


        r.spec = FieldSpec.builder(
                ParameterizedTypeName.get( Function.class,
                        PluralOperand.class, PluralCategory.class ),
                r.ruleName
        )
                .addModifiers( Modifier.FINAL, Modifier.PRIVATE, Modifier.STATIC )
                .initializer( lambdaBuilder.build() )
                .build();
    }


    // extract base language; "pt-PT" => "pt"
    public static String baseLanguage(String in) {
        int idx = in.indexOf( '-' );
        if (idx > 0) {
            return in.substring( 0, idx );
        }
        return in;
    }

    // extract region; e.g., "pt-PT" => "PT"; null if no region, exception if ends in hyphen...
    public static String extractRegion(String in) {
        int idx = in.indexOf( '-' );
        if (idx > 0) {
            return in.substring( in.indexOf( '-' ) + 1 );
        }
        return in;
    }

    // must sort ordinals & cardinals first
    private static CodeBlock createSwitchCase(final Map<Ruleset, Set<String>> map) {
        // most keys are by language ONLY. However some (well... one as of CLDR 37)
        // keys are also dependent upon region ("pt" vs. "pt-PT"). Find these.
        final Set<String> specialLocales = map.values().stream()
                .flatMap( Collection::stream )
                .filter( s -> s.indexOf( '-' ) >= 2 )
                .flatMap( s -> Stream.of( s, baseLanguage( s ) ) )
                .collect( toSet() );

        // the set-asides for special handling
        // key == base_language (e.g., for "pt-PT" this would be "pt");
        // value == Map.Entry<full-language (e.g., "pt-PT"), ruleset>
        // e.g., specialMap.get("pt") => {"pt",RULESET0},{"pt-PT",RULESET1}
        // (ab)use Map.Entry because it an easy-to-use and available tuple
        final Map<String, List<Map.Entry<String, Ruleset>>> specialMap = new HashMap<>();

        // create a switch, based on String, taking into account ONLY the language.
        // We could do fancier switch-case mapping (e.g., mapping language to 2 or 3 bytes of an int, and switch on that),
        // or character-by-character (indexed with 'a' == 0; so a TABLESWITCH would more likely be used); it is possible
        // these approaches could be more performant. However, switch-case with String has performance similar to a HashMap.
        CodeBlock.Builder cbb = CodeBlock.builder();
        cbb.beginControlFlow( "switch (language)" );

        // write out the typical, set aside the special
        map.forEach( (ruleset, langSet) -> {
            for (String lang : langSet) {
                if (specialLocales.contains( lang )) {
                    final String baseLang = baseLanguage( lang );
                    final List<Map.Entry<String, Ruleset>> val = specialMap.getOrDefault( baseLang, new ArrayList<>() );
                    val.add( Map.entry( lang, ruleset ) );
                    specialMap.put( baseLang, val );
                } else {
                    cbb.add( "case $S:$W", lang );
                    cbb.addStatement( "$> return $L$<", ruleset.ruleName );
                }
            }
        } );

        // write out the special...  lookin' at *you*, pt-PT
        specialMap.forEach( (baseLang, list) -> {
            assert list.size() >= 2;     // ... otherwise why are we here?

            // sort the list so that the base language comes LAST, since it must be the 'else' case
            list.sort( Map.Entry.comparingByKey( Comparator.reverseOrder() ) );

            cbb.add( "case $S: {\n$>", baseLang );

            // first: 'if ()'
            cbb.beginControlFlow( "if ($S.equals(region))", extractRegion( list.get( 0 ).getKey() ) );
            cbb.addStatement( "$> return $L$<", list.get( 0 ).getValue().ruleName );

            // any in between: 'else if ()' [future proofing]
            list.subList( 1, (list.size() - 1) ).forEach( item -> {
                cbb.nextControlFlow( "else if ($S.equals(region))", extractRegion( item.getKey() ) );
                cbb.addStatement( "$> return $L$<", item.getValue().ruleName );
            } );

            // last: 'else' [catch-all]
            cbb.nextControlFlow( "else" );
            cbb.addStatement( "$> return $L$<", list.get( list.size() - 1 ).getValue().ruleName );
            cbb.endControlFlow();

            cbb.add( "$<}\n" ); // end case block
        } );

        cbb.add( "default:\n" );
        cbb.addStatement( "$>return null$<" );
        cbb.endControlFlow();   // end switch block

        return cbb.build();
    }


    // find ordinal rules that are equivalent to cardinal rules, if any
    // change the spec of those methods (just assign to cardinal rule)
    // this modifies the ordMap Ruleset(s) if duplicates are found
    static void findDuplicateRules(Map<Ruleset, Set<String>> ordMap, Map<Ruleset, Set<String>> cardMap) {
        final Map<CodeBlock, String> cbMap = cardMap.keySet().stream().collect( toMap(
                r -> r.spec.initializer,
                r -> r.ruleName
        ) );

        final Map<Ruleset, String> collect = ordMap.keySet().stream()
                .filter( r -> cbMap.containsKey( r.spec.initializer ) )
                .collect( toMap(
                        r -> r,
                        r -> cbMap.get( r.spec.initializer )
                ) );

        // mutate
        for (Map.Entry<Ruleset, String> entry : collect.entrySet()) {
            final Ruleset r = entry.getKey();
            final String alias = entry.getValue();
            r.spec = FieldSpec.builder(
                    ParameterizedTypeName.get( Function.class,
                            PluralOperand.class, PluralCategory.class ),
                    r.ruleName )
                    .addModifiers( Modifier.FINAL, Modifier.PRIVATE, Modifier.STATIC )
                    .initializer( "$L", alias )
                    .build();
        }
    }


    static void writeTestJSON(Map<String, Map<PluralCategory, List<String>>> data, Path path) throws IOException {
        Moshi moshi = new Moshi.Builder()
                .add( new PluralCategoryAdapter() )
                .build();

        final ParameterizedType parameterizedType = Types.newParameterizedType(
                Map.class, String.class,
                Map.class, PluralCategory.class, List.class, String.class
        );

        // easier than dealing with OKIO BufferedSource/Sink
        Files.writeString(
                path,
                moshi.adapter( parameterizedType ).toJson( data ),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW
        );
    }


    public static Map<String, Map<PluralCategory, List<String>>> readTestJSON(Path path) throws IOException {
        Moshi moshi = new Moshi.Builder()
                .add( PluralCategory.class, EnumJsonAdapter.create( PluralCategory.class )
                        .withUnknownFallback( null ) )
                .build();

        // issue: map keys (for embedded map) NOT read in as PluralCategory, but as String....
        final ParameterizedType parameterizedType = Types.newParameterizedType(
                Map.class, String.class,
                        Map.class, PluralCategory.class, List.class, String.class );

        JsonAdapter<Map<String, Map<PluralCategory, List<String>>>> adapter = moshi.adapter( parameterizedType );

        return adapter.fromJson( Files.readString( path ) );
    }

    private static Path toPath(String s) throws IOException {
        Path path = Path.of( s );
        if (!Files.isDirectory( path )) {
            System.err.printf( "ERROR: '%s'; not a valid output directory.", path );
            throw new IOException();
        }
        return path;
    }


    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.printf( "ERROR: 4 arguments required (%d supplied)\n", args.length );
            System.err.println( "Required arguments:" );
            System.err.println( "  (1) CLDR cardinal plural JSON" );
            System.err.println( "  (2) CLDR ordinal plural JSON" );
            System.err.println( "  (3) test data output path (directory)" );
            System.err.println( "  (3) generated java source output path (directory)" );
            System.exit( 1 );
        }

        final Path testPath = toPath(args[2]);
        final Path genPath = toPath( args[3]);

        System.out.printf("IN: Cardinals (JSON): '%s'\n", args[0]);
        System.out.printf("IN: Ordinals (JSON): '%s'\n", args[1]);
        System.out.printf("OUT: test data path: '%s'\n", args[2]);
        System.out.printf("OUT: generated code path: '%s'\n", args[3]);

        final CLDRSupplemental cldrSupplementalCardinal = readJSON( Path.of( args[0] ) );
        final int version = cldrSupplementalCardinal.version();

        final CLDRSupplemental cldrSupplementalOrdinal = readJSON( Path.of( args[1] ) );
        final int cldrOrdinalVersion = cldrSupplementalOrdinal.version();

        if (cldrSupplementalCardinal.supplemental.cardinals == null || cldrSupplementalOrdinal.supplemental.ordinals == null) {
            System.err.println( "Both cardinal and ordinal input required; verify arguments and file contents" );
            System.exit( 1 );
        }

        if (version != cldrOrdinalVersion) {
            System.err.printf( "cardinal/ordinal CLDR version mismatch! (%d != %d)", version, cldrOrdinalVersion );
            System.exit( 1 );
        }

        final Map<Ruleset, Set<String>> cardinalRules = createRules( cldrSupplementalCardinal.supplemental.cardinals,
                CARDINAL_RULE_PREFIX );

        final Map<Ruleset, Set<String>> ordinalRules = createRules( cldrSupplementalOrdinal.supplemental.ordinals,
                ORDINAL_RULE_PREFIX );

        findDuplicateRules( ordinalRules, cardinalRules );


        writeTestJSON( makeTests( cldrSupplementalCardinal.supplemental.cardinals ),
                testPath.resolve( Path.of( "cardinal_samples.json" ) ) );

        writeTestJSON( makeTests( cldrSupplementalOrdinal.supplemental.ordinals ),
                testPath.resolve( Path.of( "ordinal_samples.json" ) ) );

        writeTestJSON( makeCompactTests( cldrSupplementalCardinal.supplemental.cardinals ),
                testPath.resolve( Path.of( "compact_cardinal_samples.json" ) ) );


        MethodSpec selectCardinal = MethodSpec.methodBuilder( "selectCardinal" )
                .addModifiers( Modifier.PUBLIC, Modifier.STATIC )
                .addAnnotation( CheckReturnValue.class )
                .returns( ParameterizedTypeName.get( Function.class,
                        PluralOperand.class, PluralCategory.class ) )
                .addParameter( String.class, "language", Modifier.FINAL )
                .addParameter( String.class, "region", Modifier.FINAL )
                .addCode( createSwitchCase( cardinalRules ) )
                .build();

        MethodSpec selectOrdinal = MethodSpec.methodBuilder( "selectOrdinal" )
                .addModifiers( Modifier.PUBLIC, Modifier.STATIC )
                .addAnnotation( CheckReturnValue.class )
                .returns( ParameterizedTypeName.get( Function.class,
                        PluralOperand.class, PluralCategory.class ) )
                .addParameter( String.class, "language", Modifier.FINAL )
                .addParameter( String.class, "region", Modifier.FINAL )
                .addCode( createSwitchCase( ordinalRules ) )
                .build();

        // general case: double
        // NOTE: per section 5.1.2 (http://unicode.org/reports/tr35/tr35-numbers.html#Relations):
        // The range value (a..b) is equivalent to listing all the INTEGERS [emphasis mine] between a and b, inclusive
        // Thus the modulo operation for this method.
        MethodSpec containsDbl = MethodSpec.methodBuilder( "contains" )
                .addModifiers( Modifier.PRIVATE, Modifier.STATIC )
                .returns( boolean.class )
                .addParameter( double.class, "val", Modifier.FINAL )
                .addParameter( double.class, "min", Modifier.FINAL )
                .addParameter( double.class, "max", Modifier.FINAL )
                .addStatement( "return (val >= min) && (val <= max) && ((val % 1) == 0)" )
                .build();

        // specialization: int
        MethodSpec containsInt = MethodSpec.methodBuilder( "contains" )
                .addModifiers( Modifier.PRIVATE, Modifier.STATIC )
                .returns( boolean.class )
                .addParameter( int.class, "val", Modifier.FINAL )
                .addParameter( int.class, "min", Modifier.FINAL )
                .addParameter( int.class, "max", Modifier.FINAL )
                .addStatement( "return (val >= min) && (val <= max)" )
                .build();

        // specialization: long
        MethodSpec containsLong = MethodSpec.methodBuilder( "contains" )
                .addModifiers( Modifier.PRIVATE, Modifier.STATIC )
                .returns( boolean.class )
                .addParameter( long.class, "val", Modifier.FINAL )
                .addParameter( long.class, "min", Modifier.FINAL )
                .addParameter( long.class, "max", Modifier.FINAL )
                .addStatement( "return (val >= min) && (val <= max)" )
                .build();

        // version constant
        FieldSpec cldrVersion = FieldSpec.builder( int.class, "CLDR_VERSION" )
                .addModifiers( Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL )
                .initializer( "$L", version )
                .addJavadoc( "The CLDR version from which this was generated." )
                .build();

        TypeSpec plurals = TypeSpec.classBuilder( "PluralRules" )
                .addModifiers( Modifier.PUBLIC, Modifier.FINAL )
                // base internal fields & methods
                .addField( cldrVersion )
                .addFields( cardinalRules.keySet().stream().map( r -> r.spec ).collect( toList() ) )
                .addFields( ordinalRules.keySet().stream().map( r -> r.spec ).collect( toList() ) )
                .addMethod( containsDbl )
                .addMethod( containsInt )
                .addMethod( containsLong )
                // rule base selection method
                .addMethod( selectCardinal )
                .addMethod( selectOrdinal )
                // simple documentation
                .addJavadoc( "<p>CLDR Plural rules. This is an automatically generated file.</p>" )
                .build();

        JavaFile javaFile = JavaFile.builder( "net.xyzsd.plurals", plurals )
                .build();

        //javaFile.writeTo( System.out );
        javaFile.writeToPath( genPath );

    }

}
