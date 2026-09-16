plugins {
    id("java-library")
}


// pull in default source tree AND generated source
sourceSets {
    test {
        resources {
            srcDir(project(":maker").layout.buildDirectory.dir("generated-source/test"))
        }
    }
    main {
        java.srcDir(project(":maker").layout.buildDirectory.dir("generated-source/main"))
    }
}


tasks.named<ProcessResources>("processTestResources") {
    dependsOn(":maker:executeMaker")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    dependsOn(":maker:executeMaker")
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(":maker:executeMaker")
}

tasks.named<JavaCompile>("compileTestJava") {
    dependsOn(":maker:executeMaker")
}


dependencies {
    implementation(project(":shared"))
    implementation(project(":maker"))
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    //
    testImplementation("com.squareup.moshi:moshi-adapters:1.15.2")
    testImplementation("com.squareup.moshi:moshi:1.15.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.12.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}