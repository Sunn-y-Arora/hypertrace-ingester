plugins {
  `java-library`
  jacoco
  id("org.hypertrace.jacoco-report-plugin")
  id("org.hypertrace.publish-plugin")
}

dependencies {
  api("org.hypertrace.core.attribute.service:attribute-service-api:0.12.3")
  api("org.hypertrace.core.attribute.service:caching-attribute-service-client:0.12.3")
  api("org.hypertrace.entity.service:entity-type-service-rx-client:0.8.0")
  api("org.hypertrace.entity.service:entity-data-service-rx-client:0.8.0")
  api("org.hypertrace.core.datamodel:data-model:0.1.17")
  implementation("org.hypertrace.core.attribute.service:attribute-projection-registry:0.12.3")
  implementation("org.hypertrace.core.grpcutils:grpc-client-rx-utils:0.5.2")
  implementation("org.hypertrace.core.grpcutils:grpc-context-utils:0.5.2")
  implementation("io.reactivex.rxjava3:rxjava:3.0.11")

  annotationProcessor("org.projectlombok:lombok:1.18.20")
  compileOnly("org.projectlombok:lombok:1.18.20")

  testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
  testImplementation("org.mockito:mockito-core:3.8.0")
  testImplementation("org.mockito:mockito-junit-jupiter:3.8.0")
  testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.14.1")

  tasks.test {
    useJUnitPlatform()
  }
}
