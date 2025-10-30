import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.allopen") version "2.0.0"
    id("io.quarkus")
    id("org.openapi.generator") version "7.2.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val jaxrsFunctionalTestBuilderVersion: String by project
val okhttpVersion: String by project
val jtsVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-undertow")
    implementation("io.quarkus:quarkus-resteasy-reactive")
    implementation("io.quarkus:quarkus-resteasy-reactive-kotlin")
    implementation("io.quarkus:quarkus-resteasy-reactive-jackson")
    implementation("io.quarkus:quarkus-oidc")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-hibernate-reactive-panache")
    implementation("io.quarkus:quarkus-liquibase")
    implementation("io.quarkus:quarkus-jdbc-mysql")
    implementation("io.quarkus:quarkus-reactive-mysql-client")
    implementation("io.quarkus:quarkus-messaging-rabbitmq")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-scheduler")

    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("io.vertx:vertx-lang-kotlin-coroutines")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.apache.camel.quarkus:camel-quarkus-ftp:3.20.0")
    implementation("io.quarkiverse.amazonservices:quarkus-amazon-s3:3.4.0")
    implementation("software.amazon.awssdk:url-connection-client:2.31.18")

    configurations.all {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    implementation("org.jboss.logging:commons-logging-jboss-logging")

    testImplementation("io.rest-assured:kotlin-extensions")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:localstack")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("fi.metatavu.jaxrs.testbuilder:jaxrs-functional-test-builder:$jaxrsFunctionalTestBuilderVersion")
}

group = "fi.metatavu.vp"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

sourceSets["main"].java {
    srcDir("build/generated/api-spec/src/main/kotlin")
    srcDir("build/generated/keycloak-admin-client/src/main/kotlin")
    srcDir("vp-kuljetus-messaging-service/src/main/kotlin")
}
sourceSets["test"].java {
    srcDir("build/generated/api-client/src/main/kotlin")
    srcDir("quarkus-invalid-param-test/src/main/kotlin")
    srcDir("vp-kuljetus-messaging-service/src/test/kotlin")
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.enterprise.context.RequestScoped")
    annotation("io.quarkus.test.junit.QuarkusTest")
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

val generateApiSpec = tasks.register("generateApiSpec",GenerateTask::class){
    setProperty("generatorName", "kotlin-server")
    setProperty("inputSpec",  "$rootDir/vp-kuljetus-transport-management-specs/services/user-management-services.yaml")
    setProperty("outputDir", "$buildDir/generated/api-spec")
    setProperty("apiPackage", "${project.group}.usermanagement.spec")
    setProperty("invokerPackage", "${project.group}.usermanagement.invoker")
    setProperty("modelPackage", "${project.group}.usermanagement.model")
    setProperty("templateDir", "$rootDir/openapi/api-spec")
    setProperty("validateSpec", false)

    this.configOptions.put("library", "jaxrs-spec")
    this.configOptions.put("dateLibrary", "java8")
    this.configOptions.put("enumPropertyNaming", "UPPERCASE")
    this.configOptions.put("interfaceOnly", "true")
    this.configOptions.put("useMutiny", "true")
    this.configOptions.put("returnResponse", "true")
    this.configOptions.put("useSwaggerAnnotations", "false")
    this.configOptions.put("additionalModelTypeAnnotations", "@io.quarkus.runtime.annotations.RegisterForReflection")
}

val generateApiClient = tasks.register("generateApiClient",GenerateTask::class){
    setProperty("generatorName", "kotlin")
    setProperty("library", "jvm-okhttp3")
    setProperty("inputSpec",  "$rootDir/vp-kuljetus-transport-management-specs/services/user-management-services.yaml")
    setProperty("outputDir", "$buildDir/generated/api-client")
    setProperty("packageName", "${project.group}.test.client")
    setProperty("validateSpec", false)

    this.configOptions.put("dateLibrary", "string")
    this.configOptions.put("collectionType", "array")
    this.configOptions.put("serializationLibrary", "jackson")
    this.configOptions.put("enumPropertyNaming", "UPPERCASE")
}

val generateKeycloakClient = tasks.register("generateKeycloakAdminClient",GenerateTask::class){
    setProperty("generatorName", "kotlin")
    setProperty("library", "jvm-vertx")
    setProperty("inputSpec",  "$rootDir/src/main/resources/kc-admin.json")
    setProperty("outputDir", "$buildDir/generated/keycloak-admin-client")
    setProperty("packageName", "fi.metatavu.keycloak.adminclient")

    this.configOptions.put("useCoroutines", "true")
    this.configOptions.put("dateLibrary", "string")
    this.configOptions.put("collectionType", "array")
    this.configOptions.put("serializationLibrary", "jackson")
    this.configOptions.put("enumPropertyNaming", "UPPERCASE")
    this.configOptions.put("useCoroutines", "true")
    this.configOptions.put("additionalModelTypeAnnotations", "@io.quarkus.runtime.annotations.RegisterForReflection")
}

tasks.named("compileKotlin") {
    dependsOn(generateApiSpec)
    dependsOn(generateKeycloakClient)
}

tasks.named("compileTestKotlin") {
    dependsOn(generateApiClient)
}

tasks.named<Test>("test") {
    testLogging.showStandardStreams = true
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    jvmArgs("-Xmx24g")
}

tasks.named<Test>("testNative") {
    testLogging.showStandardStreams = true
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    jvmArgs("-Xmx24g")
}

