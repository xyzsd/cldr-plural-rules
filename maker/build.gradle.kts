plugins {
    id("java")
    id("de.undercouch.download") version "5.6.0"
}


dependencies {
    implementation(project(":shared"))
    implementation("com.palantir.javapoet:javapoet:0.11.0")
    implementation("com.squareup.moshi:moshi-adapters:1.15.2")
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.12.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


val generatedPluralSrc = layout.buildDirectory.dir("generated-source/main")
val generatedPluralTests = layout.buildDirectory.dir("generated-source/test")

// see : https://docs.gradle.org/current/dsl/org.gradle.api.tasks.SourceSetOutput.html#org.gradle.api.tasks.SourceSetOutput
val createDirs = tasks.register("createDirs") {
    doLast {
        mkdir(generatedPluralSrc)
        mkdir(generatedPluralTests)
    }
}


val downloadJSON = tasks.register<de.undercouch.gradle.tasks.download.Download>("downloadJSON") {
    description = "Download CLDR data files"
    group = "other"
    src(listOf(
        "https://raw.githubusercontent.com/unicode-org/cldr-json/master/cldr-json/cldr-core/supplemental/plurals.json",
        "https://raw.githubusercontent.com/unicode-org/cldr-json/master/cldr-json/cldr-core/supplemental/ordinals.json")
    )
    dest(layout.buildDirectory.dir("CLDR"))
    tempAndMove(true)
    overwrite(false)
}


// https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html#org.gradle.api.tasks.JavaExec
// or: task("execute", JavaExec::class) {
val executeMaker = tasks.register<JavaExec>("executeMaker") {
    description = "Generate code from CLDR data files"
    group = "other"

    // task dependencies
    dependsOn(downloadJSON)
    dependsOn(createDirs)

    doFirst {
        delete(generatedPluralTests.get().file("cardinal_samples.json"))
        delete(generatedPluralTests.get().file("ordinal_samples.json"))
        delete(generatedPluralTests.get().file("compact_cardinal_samples.json"))
    }

    // set output; then gradle will know if we are up to date
    // otherwise it will execute the PluralMaker again, which
    // will fail (because 'file already exists')
    outputs.dirs(generatedPluralSrc, generatedPluralTests)

    // execution parameters
    mainClass.set("net.xyzsd.plurals.maker.PluralMaker")
    classpath = sourceSets["main"].runtimeClasspath

    args(
        layout.buildDirectory.file("CLDR/plurals.json").get().asFile.absolutePath,
        layout.buildDirectory.file("CLDR/ordinals.json").get().asFile.absolutePath,
        generatedPluralTests.get().asFile.absolutePath,
        generatedPluralSrc.get().asFile.absolutePath
    )
}
