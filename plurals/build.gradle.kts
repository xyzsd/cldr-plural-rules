plugins {
    id("java-library")
}


// pull in default source tree AND generated source
sourceSets {
    test {
        resources {
            srcDir("${project(":maker").buildDir}/generated-source/test")
        }
    }
    main {
        java.srcDir("${project(":maker").buildDir}/generated-source/main")
    }
}


// required ... otherwise tests will not run
tasks.named<Test>("test") {
    useJUnitPlatform()
    dependsOn(":maker:executeMaker")
}


dependencies {
    implementation(project(":shared"))
    implementation(project(":maker"))
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    //
    testImplementation("com.squareup.moshi:moshi-adapters:1.11.0")
    testImplementation("com.squareup.moshi:moshi:1.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}