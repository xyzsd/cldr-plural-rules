import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("com.vanniktech.maven.publish") version "0.36.0"
    id("signing")
    id("java-library")
}

allprojects {
    group = "net.xyzsd.plurals"
    version = "48.2" // version should be == CLDR version for clarity

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

    tasks.withType<JavaCompile>().configureEach {
        options.isIncremental = false
        options.encoding = "UTF-8"
        options.release.set(11)
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
    }
}

sourceSets {
    main {
        java {
            srcDir(project(":shared").file("src/main/java"))
            srcDir(project(":plurals").file("src/main/java"))
            srcDir(project(":maker").layout.buildDirectory.dir("generated-source/main"))
        }
    }
}

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(":maker:executeMaker")
}

tasks.named<Javadoc>("javadoc") {
    dependsOn(":maker:executeMaker")
    val javadocOptions = options as CoreJavadocOptions
    javadocOptions.addStringOption("Xdoclint:none", "-quiet")
    options.encoding = "UTF-8"
}

tasks.matching { it.name == "sourcesJar" || it.name == "javadocJar" }.configureEach {
    dependsOn(":maker:executeMaker")
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes["Automatic-Module-Name"] = "net.xyzsd.plurals"
    }
    from(projectDir.resolve("LICENSE"))
}

mavenPublishing {
    project.logger.lifecycle("Publishing: Coordinates: " + project.group + ":" + project.name + ":" + project.version)

    // for now, we will disable automatic release.
    publishToMavenCentral(automaticRelease = false)
    signAllPublications()

    configure(JavaLibrary(
        javadocJar = JavadocJar.Javadoc(),
        sourcesJar = SourcesJar.Sources()
    ))

    coordinates(groupId = project.group as String, project.name, project.version as String)

    pom {
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
            url.set("https://github.com/xyzsd/cldr-plural-rules")
        }
    }
}

signing {
    val githubCI: Boolean = "true".equals(System.getenv("CI"))
    if (githubCI) {
        project.logger.lifecycle("Signing: Using Github CI environment.")
        val signingKey: String? = System.getenv("SIGNING_KEY_PRIVATE")
        val signingKeyPassphrase: String? = System.getenv("SIGNING_KEY_PASSPHRASE")
        useInMemoryPgpKeys(signingKey, signingKeyPassphrase)
    } else {
        project.logger.lifecycle("Signing: Using local credentials.")
        useGpgCmd()
    }
}

