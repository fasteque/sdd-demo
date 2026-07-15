plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.openapi.generator") version "7.23.0"
}

group = "ch.fasteque"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val openApiGenDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
	generatorName.set("kotlin-spring")
	inputSpec.set("$rootDir/openapi/openapi.yaml")
	outputDir.set(openApiGenDir.get().asFile.path)
	apiPackage.set("ch.fasteque.sdd_demo.generated.api")
	modelPackage.set("ch.fasteque.sdd_demo.generated.model")
	configOptions.set(
		mapOf(
			"interfaceOnly" to "true",
			"useSpringBoot4" to "true",
			"useJackson3" to "true",
			"useTags" to "true",
			"documentationProvider" to "none",
			"annotationLibrary" to "none",
		)
	)
	cleanupOutput.set(true)
}

sourceSets {
	main {
		kotlin.srcDir(openApiGenDir.map { it.dir("src/main/kotlin") })
	}
}

tasks.named("compileKotlin") {
	dependsOn("openApiGenerate")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
