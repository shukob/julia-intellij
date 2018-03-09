import groovy.lang.Closure
import org.gradle.api.internal.HasConvention
import org.gradle.language.base.internal.plugins.CleanRule
import org.jetbrains.grammarkit.tasks.BaseTask
import org.jetbrains.grammarkit.tasks.GenerateLexer
import org.jetbrains.grammarkit.tasks.GenerateParser
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.utils.sure
import java.io.*
import java.nio.file.*
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

val isCI = !System.getenv("CI").isNullOrBlank()
val commitHash = kotlin.run {
	val process: Process = Runtime.getRuntime().exec("git rev-parse --short HEAD")
	process.waitFor(2000L, TimeUnit.MILLISECONDS)
	@Suppress("RemoveExplicitTypeArguments")
	val output = process.inputStream.use {
		process.inputStream.use {
			// TODO workaround for KT-23156
			it.readBytes().let<ByteArray, String>(::String)
		}
	}
	process.destroy()
	output.trim()
}

val pluginComingVersion = "0.1.7"
val pluginVersion = if (isCI) "$pluginComingVersion-$commitHash" else pluginComingVersion
val packageName = "org.ice1000.julia"
val kotlinVersion: String by extra

group = packageName
version = pluginVersion

buildscript {
	var kotlinVersion: String by extra
	var grammarKitVersion: String by extra

	grammarKitVersion = "2017.1.1"
	kotlinVersion = "1.2.30"

	repositories {
		mavenCentral()
		maven("https://jitpack.io")
	}

	dependencies {
		classpath(kotlin("gradle-plugin", kotlinVersion))
		classpath("com.github.hurricup:gradle-grammar-kit-plugin:$grammarKitVersion")
	}
}

plugins {
	idea
	java
	id("org.jetbrains.intellij") version "0.2.18"
	kotlin("jvm") version "1.2.30"
}

idea {
	module {
		// https://github.com/gradle/kotlin-dsl/issues/537/
		excludeDirs.add(file("pinpoint-piggy"))
	}
}

allprojects {
	apply {
		plugin("org.jetbrains.grammarkit")
	}

	intellij {
		updateSinceUntilBuild = false
		instrumentCode = true
		when (System.getProperty("user.name")) {
			"ice1000" -> localPath = "/home/ice1000/.local/share/JetBrains/Toolbox/apps/IDEA-U/ch-0/173.4548.28"
			"hoshino" -> localPath = "/home/hoshino/Documents/IntelliJ"
			"zh" -> version = "2017.3"
		}
	}
}

java {
	sourceCompatibility = JavaVersion.VERSION_1_8
	targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<PatchPluginXmlTask> {
	changeNotes(file("res/META-INF/change-notes.html").readText())
	pluginDescription(file("res/META-INF/description.html").readText())
	version(pluginComingVersion)
	pluginId(packageName)
}

val SourceSet.kotlin
	get() = (this as HasConvention)
		.convention
		.getPlugin(KotlinSourceSet::class.java)
		.kotlin

java.sourceSets {
	"main" {
		listOf(java, kotlin).forEach {
			it.srcDirs("src", "gen")
		}
		resources.srcDirs("res")
	}

	"test" {
		listOf(java, kotlin).forEach {
			it.srcDirs("test")
		}
		resources.srcDirs("testData")
	}
}

// TODO workaround for KT-23077
inline fun <reified TheTask : BaseTask>
	Project.genTask(name: String, noinline configuration: TheTask.() -> Unit) =
	task(name, TheTask::class, configuration)

repositories {
	mavenCentral()
}

dependencies {
	compile(kotlin("stdlib-jdk8", kotlinVersion).toString()) {
		exclude(module = "kotlin-runtime")
		exclude(module = "kotlin-reflect")
	}
	compileOnly("org.commonjava.googlecode.markdown4j", "markdown4j", "2.2-cj-1.1")
	implementation("org.eclipse.mylyn.github","org.eclipse.egit.github.core","2.1.5"){
		exclude(module = "gson")
	}
	testCompile(kotlin("test-junit", kotlinVersion))
	testCompile("junit", "junit", "4.12")
}

task("displayCommitHash") {
	group = "help"
	description = "Display the newest commit hash"
	doFirst {
		println("Commit hash: $commitHash")
	}
}

task("isCI") {
	group = "help"
	description = "Check if it's running in a continuous-integration"
	doFirst {
		println(if (isCI) "Yes, I'm on a CI." else "No, I'm not on CI.")
	}
}

val parserRoot: Path = Paths.get("org", "ice1000", "julia", "lang")
val lexerRoot: Path = Paths.get("gen", "org", "ice1000", "julia", "lang")
fun path(more: Iterable<*>) = more.joinToString(File.separator)
fun bnf(name: String) = Paths.get("grammar", "$name-grammar.bnf").toString()
fun flex(name: String) = Paths.get("grammar", "$name-lexer.flex").toString()

val genParser = genTask<GenerateParser>("genParser") {
	group = "build setup"
	description = "Generate the Parser and PsiElement classes"
	source = bnf("julia")
	targetRoot = "gen/"
	pathToParser = path(parserRoot + "JuliaParser.java")
	pathToPsiRoot = path(parserRoot + "psi")
	purgeOldFiles = true
}

val genLexer = genTask<GenerateLexer>("genLexer") {
	group = "build setup"
	description = "Generate the Lexer"
	source = flex("julia")
	targetDir = path(lexerRoot)
	targetClass = "JuliaLexer"
	purgeOldFiles = true
}

val genDocfmtParser = genTask<GenerateParser>("genDocfmtParser") {
	group = "build setup"
	description = "Generate the Parser for DocumentFormat.jl"
	source = bnf("docfmt")
	targetRoot = "gen/"
	val root = parserRoot + "docfmt"
	pathToParser = path(root + "DocfmtParser.java")
	pathToPsiRoot = path(root + "psi")
	purgeOldFiles = true
}

val genDocfmtLexer = genTask<GenerateLexer>("genDocfmtLexer") {
	group = "build setup"
	description = "Generate the Lexer for DocumentFormat.jl"
	source = flex("docfmt")
	targetDir = path(lexerRoot + "docfmt")
	targetClass = "DocfmtLexer"
	purgeOldFiles = true
}

val cleanGenerated = task("cleanGenerated") {
	group = "build"
	description = "Remove all generated codes"
	doFirst {
		delete("gen", "pinpoint-piggy")
	}
}

tasks.withType<KotlinCompile> {
	dependsOn(
		genParser,
		genLexer,
		genDocfmtParser,
		genDocfmtLexer
	)
	kotlinOptions {
		jvmTarget = "1.8"
		languageVersion = "1.2"
		apiVersion = "1.2"
	}
}

tasks.withType<Delete> {
	dependsOn(cleanGenerated)
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
	jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
	jvmTarget = "1.8"
}