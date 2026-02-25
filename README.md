# cldr-plural-rules
CLDR pluralization handling for Java.

This project extracts plural rules from CLDR data sets (as defined by Unicode [TR35][tr35]), generates the rules as Java code, 
and creates a library for using these rules which is independent from the [ICU][icu] library, specifically [ICU4J][icu4j].
This project is compliant with the [CLDR Language Plural Rules][cldrPlurals].

The [generated library][jar_dl] is:
* Current with [CLDR 48][cldr_rel] (Novembert 2025) plural supplemental
* Contains all languages and regions as defined in CLDR 48
* Simple, lightweight (~ 27 kB .jar)
* Self-contained; no additional dependencies or data files

## Versioning
As of CLDR 41, the version number of this library will be synchronized with the CLDR versions.


Usage
-----

Get the plural rule for a given locale and number type ([cardinal][cardinal] or [ordinal][ordinal]):
```java
PluralRule rule = PluralRule.createOrDefault( Locale.ENGLISH, PluralRuleType.CARDINAL );
```

The rule can then be used for plural selection:
```java
PluralRule rule = PluralRule.createOrDefault( Locale.ENGLISH, PluralRuleType.CARDINAL );
PluralCategory category = rule.select( 2 );

// for illustration...  
String msg;
switch (category) {
    case ZERO:
        msg = "No files are selected.";
        break;
    case ONE:
        msg = "Only a single file is selected.";
        break;
    ...
    default:
        msg = category + " files selected";
}

```


Rules can also be obtained from their language and (optional) region Strings:
```java
// English, no region specified
PluralRule englishCardinal = PluralRule.createOrDefault( "en", "", PluralRuleType.CARDINAL );

// British English
PluralRule brCardinal = PluralRule.createOrDefault( "en", "GB", PluralRuleType.CARDINAL );
```
Note that for most languages, the region does not change the plural rules. In the above example,
```englishCardinal``` is equivalent to ```brCardinal```. If a region is not present, or invalid,
it is ignored and the language tag used to select the rule. However, if the language is not 
available in the CLDR, the match will fail. For example:

```java
// valid
Optional<PluralRule> rule = PluralRule.create( "en", "", PluralRuleType.CARDINAL );
Optional<PluralRule> rule = PluralRule.create( "en", "GB", PluralRuleType.CARDINAL );

// invalid region, however, this will return a rule for "en" as above
Optional<PluralRule> rule = PluralRule.create( "en", "totally-made-up", PluralRuleType.CARDINAL );  
assert rule.isPresent();
    
// invalid language; no rule exists.
Optional<PluralRule> rule = PluralRule.create( "mylang", "", PluralRuleType.CARDINAL );
assert rule.isEmpty();

// if no rule exists, we could return the rule for the root locale, which is what 
// PluralRule.createOrDefault() does in a single step. Or, we could do this:
PluralRule knownRule = rule.orElse(PluralRule.createDefault(PluralRuleType.CARDINAL));
assert (knownRule != null);

```
Caveat: the empty String (`""`) and String `"root"` are equivalent to `Locale.ROOT`. 
As of CLDR 39, the `"root"` locale has been renamed to `"und"`. Therefore, for versions of
this library supporting CLDR versions of 39 or higher, `""`, `"root"` and `"und"` are 
equivalent.

If localization information cannot be obtained for a given language, ```createOrDefault()``` will return 
the root Locale `Locale.ROOT`. 

For the root locale, `select()` always returns `PluralCategory.OTHER`,
for both cardinals and ordinals. 

If using Java 9+ Modules,
```java
module MyModule {
    requires net.xyzsd.plurals;
}
```

Example
-------

A simple, working example:

```java
import net.xyzsd.plurals.PluralCategory;
import net.xyzsd.plurals.PluralRule;
import net.xyzsd.plurals.PluralRuleType;

public class prtest {

    // first argument: language
    // second argument: numeric value
    public static void main(String[] args) {

        if(args.length == 2) {
            final String language = args[0];
            final String value = args[1];

            PluralRule rule = PluralRule.createOrDefault(language, "", PluralRuleType.CARDINAL);
            System.out.printf("rule: '%s'\n", PluralRule.locale(rule));
            System.out.printf("  rule type: %s\n", rule.pluralRuleType());
            System.out.printf("  results of select(%s): %s\n",
                    value,
                    rule.select(value )
                        .map( PluralCategory::name)
                        .orElse( "Cannot parse value" )
            );
        } else {
            System.err.println("Two arguments required!");
            System.out.println("USAGE:");
            System.out.println("  prtest [language] [value]");
        }
    }
}

```


Download
--------
Download [the JAR][jar_dl] or use via Maven:

```xml
<dependency>
   <groupId>net.xyzsd.plurals</groupId>
   <artifactId>cldr-plural-rules</artifactId>
   <version>41</version>
</dependency>

```
or Gradle:
```kotlin
implementation("net.xyzsd.plurals:cldr-plural-rules:41")
```

Documentation
-------------
[Download][docs_dl] or [view online][docs].

Building
--------

The included Gradle build file will download the current (JSON) CLDR definitions,
generate the rules from the CLDR definitions, compile, and create the release package (including source and 
documentation).

Tests data is generated from the CLDR sample data.

The build has several external dependencies, however the release has none.

Internals
---------
The generated source file `PluralRules.java` contains the CDLR rules as Java source,
and methods for selecting the correct rule for a given language. This generated class
is fully exposed, and can be used directly instead of the `PluralRule` wrapper class.
  

License
-------
Copyright 2020-2026, xyzsd

Licensed under either of

 * Apache License, Version 2.0
   (see LICENSE-APACHE or http://www.apache.org/licenses/LICENSE-2.0)
 * MIT license
   (see LICENSE-MIT) or http://opensource.org/licenses/MIT)

at your option.



[jar_dl]: https://oss.sonatype.org/service/local/repositories/releases/content/net/xyzsd/plurals/cldr-plural-rules/41/cldr-plural-rules-41.jar
[docs_dl]: https://oss.sonatype.org/service/local/repositories/releases/content/net/xyzsd/plurals/cldr-plural-rules/41/cldr-plural-rules-41-javadoc.jar
[docs]: https://javadoc.io/doc/net.xyzsd.plurals/cldr-plural-rules/latest/index.html
[tr35]: https://unicode.org/reports/tr35/tr35-numbers.html
[cldrPlurals]: https://unicode.org/reports/tr35/tr35-numbers.html#Language_Plural_Rules
[icu]: https://site.icu-project.org/
[icu4j]: https://github.com/unicode-org/icu
[cldr_rel]: http://cldr.unicode.org/index/downloads/cldr-48
[cardinal]: https://www.dictionary.com/browse/cardinal-number
[ordinal]: https://www.dictionary.com/browse/ordinal-number

[latest]: https://github.com/xyzsd/cldr-plural-rules/releases/latest
