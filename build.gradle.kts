// NEW build file
// a mix of various kotlin conventions and styles
// some dependencies are implicit, unfortunately, and that will have to be remedied
// ISSUE: POM not created in root project...but may be after maven-local-publish.....

plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
}

allprojects {
    group = "net.xyzsd.plurals"
    version = "48"     // version should be == CLDR version for clarity
    // use 'rootProject.name' (from settings.gradle.kts) for base name

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    // to better create reproducible builds ...
    tasks.withType<AbstractArchiveTask>().configureEach {
        isReproducibleFileOrder = true
        isPreserveFileTimestamps = false
        archiveVersion.set("${project.version}")
    }
}


configure(subprojects) {

    apply<MavenPublishPlugin>()
    apply<JavaLibraryPlugin>()
    apply<SigningPlugin>()

    pluginManager.withPlugin("java-library") {
        configure<JavaPluginExtension> {
            withSourcesJar()
            targetCompatibility = JavaVersion.VERSION_11
            sourceCompatibility = JavaVersion.VERSION_11
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.isIncremental = false
        options.encoding = "UTF-8"  // this is important, particularly for tests
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
    }
}

// todo : put below in tasks block, with artifacts {} block

// create aggregate artifacts:
//  1) source (w/o tests)
//  2) jar  (w/o generator)
//  3) javadocs

// create aggregated javadocs
val agroDoc by tasks.registering(Javadoc::class) {
    dependsOn(":shared:build")
    dependsOn(":plurals:build")
    dependsOn(":maker:executeMaker")

    description = "Aggregated Javadocs"
    group = JavaBasePlugin.DOCUMENTATION_GROUP

    title = "${rootProject.name} API (complete) version ${project.version}"

    val javadocOptions = options as CoreJavadocOptions
    javadocOptions.addStringOption("Xdoclint:none", "-quiet")
    options.encoding = "UTF-8"

    val sourceSets = allprojects
        .mapNotNull { it.extensions.findByType<SourceSetContainer>() }
        .map { it.named("main") }

    classpath = files(sourceSets.map { set -> set.map { it.output + it.compileClasspath } })
    setSource(sourceSets.map { set -> set.map { it.allJava } })
    setDestinationDir(file("$buildDir/javadoc"))
}

//  archive name structure: [archiveBaseName]-[archiveAppendix]-[archiveVersion]-[archiveClassifier].[archiveExtension]
val agroDocJar by tasks.registering(Jar::class) {
    dependsOn("agroDoc")
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    archiveBaseName.set(rootProject.name)
    archiveVersion.set("${project.version}")
    archiveClassifier.set("javadoc")
    from(agroDoc)
}

// see:
// https://stackoverflow.com/questions/52596968/build-source-jar-with-gradle-kotlin-dsl

val agroSourcesJar by tasks.creating(Jar::class) {
    description = "Aggregated Source"
    archiveClassifier.set("sources")
    dependsOn(":maker:executeMaker")
    dependsOn(":plurals:compileJava")

    // brittle... but works
    from("${project.rootDir}/${project(":shared").name}/src/main/java")
    from("${project.rootDir}/${project(":plurals").name}/src/main/java")
    from("${project.rootDir}/${project(":maker").name}/src/main/java")
    from("${project.rootDir}/${project(":maker").name}/build/generated-source/main")
}


tasks.withType<Jar>().configureEach {
    // using automatic modules for now
    manifest {
        val moduleName = "${rootProject.group}.${project.name}";
        attributes.set("Automatic-Module-Name", moduleName)
    }

    includeEmptyDirs = false



    // we only want classes from 'plurals' and 'shared'
    from("${project(":shared").buildDir}/classes/java/main/")
    from("${project(":plurals").buildDir}/classes/java/main/")
    from("$projectDir/LICENSE")
}


// put in tasks block
artifacts {
    archives(agroDocJar)
    archives(agroSourcesJar)
    archives(tasks.jar)
}

// if not working ('no actions') try publish to maven local for testing
configure<PublishingExtension> {
    publications {
        val main by creating(MavenPublication::class) {
            artifact(agroDocJar)
            artifact(agroSourcesJar)
            from(components["java"])

            pom {
                artifactId = project.name
                name.set("CLDR Plural Rules")
                description.set("CLDR-based pluralization handling for Java")
                url.set("https://github.com/xyzsd/cldr-plural-rules")
                inceptionYear.set("2020")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        comments.set("A business-friendly OSS license")
                    }
                    license {
                        name.set("The MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        comments.set("A GPL/LGPL compatible OSS license")
                    }
                }
                developers {
                    developer {
                        id.set("xyzsd")
                        name.set("Zach Del")
                        email.set("xyzsd@xyzsd.net")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/xyzsd/cldr-plural-rules.git")
                    developerConnection.set("scm:git:ssh://git@github.com:xyzsd/cldr-plural-rules.git")
                    url.set("")
                }
            }

        }
    }

    repositories {
        maven {
            name = "OSSRH"
            setUrl("https://oss.sonatype.org/service/local/staging/deploy/maven2")

            credentials {
                // obtained from gradle.properties in GRADLE_USER_HOME
                credentials(PasswordCredentials::class)
            }
        }
    }
}


// if 'no signatory' error, make sure GRADLE_USER_HOME is set
// to point to the gradle.properties file containing keyID/password/etc.
configure<SigningExtension> {
    val publishing: PublishingExtension by project
    sign(publishing.publications)
}

/*
tasks.withType<PublishToMavenLocal>().configureEach {
    doLast {
        copy {
           // from("${buildDir}/publications/main/pom-default.xml")
            to("${buildDir}/libs/${project.name}-${version}.pom")
        }
    }
}

 */

