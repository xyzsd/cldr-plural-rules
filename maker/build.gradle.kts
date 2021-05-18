plugins {
    id("java")
    id("de.undercouch.download") version "4.1.1"
}


dependencies {
    implementation(project(":shared"))
    implementation("com.squareup:javapoet:1.13.0")
    implementation("com.squareup.moshi:moshi-adapters:1.11.0")
    implementation("com.squareup.moshi:moshi:1.11.0")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}


val generatedPluralSrc = "$buildDir/generated-source/main"
val generatedPluralTests = "$buildDir/generated-source/test"

// see : https://docs.gradle.org/current/dsl/org.gradle.api.tasks.SourceSetOutput.html#org.gradle.api.tasks.SourceSetOutput
val createDirs by tasks.creating(DefaultTask::class) {
    doLast {
        mkdir(generatedPluralSrc)
        mkdir(generatedPluralTests)
    }
}


val downloadJSON by tasks.creating(de.undercouch.gradle.tasks.download.Download::class) {
    setDescription("Download CLDR data files")
    setGroup("other")
    src(listOf(
        "https://raw.githubusercontent.com/unicode-org/cldr-json/master/cldr-json/cldr-core/supplemental/plurals.json",
        "https://raw.githubusercontent.com/unicode-org/cldr-json/master/cldr-json/cldr-core/supplemental/ordinals.json")
    )
    dest("$buildDir/CLDR")
    tempAndMove(true)
    overwrite(false)
}


// https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html#org.gradle.api.tasks.JavaExec
// or: task("execute", JavaExec::class) {
val executeMaker by tasks.creating(JavaExec::class) {
    setDescription("Generate code from CLDR data files")
    setGroup("other")

    // task dependencies
    dependsOn(downloadJSON)
    dependsOn(createDirs)

    doFirst {
        delete("${generatedPluralTests}/cardinal_samples.json")
        delete("${generatedPluralTests}/ordinal_samples.json")
        delete("${generatedPluralTests}/compact_cardinal_samples.json")
    }

    // set output; then gradle will know if we are up to date
    // otherwise it will execute the PluralMaker again, which
    // will fail (because 'file already exists')
    outputs.dirs(generatedPluralSrc, generatedPluralTests)

    // execution parameters
    setMain("net.xyzsd.plurals.maker.PluralMaker")
    classpath = sourceSets["main"].runtimeClasspath

    setArgs(listOf(
        // input:
        "$buildDir/CLDR/plurals.json",     // downloaded cardinals
        "$buildDir/CLDR/ordinals.json",     // downloaded ordinals
        // output:
        generatedPluralTests,     // generated tests (src)
        generatedPluralSrc      // generated implementation (src)
    ))
}
