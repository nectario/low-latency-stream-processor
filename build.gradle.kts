plugins {
    java
    application
    id("com.diffplug.spotless") version "7.1.0"
}

group = "com.ubs.trading"
version = "1.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
}

spotless {
    java {
        googleJavaFormat("1.19.2")
        removeUnusedImports()
    }

    format("misc") {
        target("*.md", "*.txt")
        endWithNewline()
        trimTrailingWhitespace()
    }
}

dependencies {

    // Core
    implementation("com.lmax:disruptor:3.4.4")
    implementation("io.micrometer:micrometer-core:1.13.2")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // --- unit & integration test helpers ---
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.awaitility:awaitility:4.2.0")

    testImplementation("org.quickfixj:quickfixj-core:2.3.2")
    testImplementation("org.slf4j:slf4j-simple:1.6.1")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("com.ubs.trading.Main")
}

