plugins {
	java
	id("org.springframework.boot") version "4.0.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "scala"
version = "0.0.1-SNAPSHOT"
description = "Scala Parser"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(24)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.0")
	implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.21.0")
	testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
	testImplementation("net.jqwik:jqwik:1.9.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.register<JavaExec>("runScalingBenchmarks") {
	mainClass.set("scala.parser.ScalingBenchmarks")
	classpath = sourceSets.main.get().runtimeClasspath
}

tasks.register<JavaExec>("runTestParser") {
	mainClass.set("scala.parser.TestParser")
	classpath = sourceSets.main.get().runtimeClasspath
}

tasks.register<JavaExec>("runMutationAnalysis") {
	mainClass.set("scala.parser.MutationAnalysis")
	classpath = sourceSets.main.get().runtimeClasspath
}

tasks.named<Jar>("jar") {
	manifest {
		attributes("Main-Class" to "scala.parser.ParserApplication")
	}
	from(configurations.runtimeClasspath.map { config ->
		config.map { if (it.isDirectory) it else zipTree(it) }
	})
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Copy>("copyJarToExtension") {
	dependsOn("jar")
	from(layout.buildDirectory.file("libs/${project.name}-${project.version}.jar"))
	into(layout.projectDirectory.dir("scala-parser-vscode/server"))
	rename(".*", "scala-parser-lsp.jar")
}
