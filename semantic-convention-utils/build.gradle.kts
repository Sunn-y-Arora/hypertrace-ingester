plugins {
    `java-library`
    id("org.hypertrace.publish-plugin")
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":span-normalizer:raw-span-constants"))
    implementation(project(":span-normalizer:span-normalizer-constants"))

    implementation("org.hypertrace.core.datamodel:data-model:0.1.18")
    implementation("org.apache.commons:commons-lang3:3.12.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testImplementation("org.mockito:mockito-core:3.8.0")
}
