#!/usr/bin/env kscript
@file:MavenRepository("jitpack", "https://jitpack.io")
@file:DependsOn("com.github.kotlin.kotlinx~cli:kotlinx-cli-jvm:-SNAPSHOT")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")

import Gr.Command
import Gr.LazyArg
import Gr.Task.*
import Gr.Variant.DEBUG
import Gr.Variant.RELEASE
import kotlinx.cli.CommandLineInterface
import kotlinx.cli.flagArgument
import kotlinx.cli.parse
import kotlinx.cli.positionalArgumentsList
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.system.exitProcess

//~ script

try {
    Cli(args)
        .createCommands()
        .forEach(Command::invoke)
} catch (e: Exception) {
    Reporter.cleanln()
    e.message?.let(::println)
    exitProcess(1)
}

val Cli.variant: Variant
    get() = when {
        debug -> DEBUG
        release -> RELEASE
        else -> throw Exception("Use -r or -d to choose build variant")
    }

fun Cli.createCommands() = tasks.map { arg ->
    val task = Task.values().firstOrNull { it.tag == arg }
    when (task) {
        CLEAN -> Tool.clear()
        BUILD -> Gradle.build(variant)
        CHECK -> Gradle.check()
        INSTALL -> Adb.install(variant)
        DIST -> Tool.dist(variant)
        LOG -> Tool.log()
        else -> throw Exception("Unknown tasks set. Use [clean|build|check|install|run]")
    }
}

//~ configuration params

enum class Variant {
    DEBUG, RELEASE;

    val tag: String = name.toLowerCase()
}

enum class Task {
    CLEAN, BUILD, CHECK, INSTALL, LOG, DIST;

    val tag: String = name.toLowerCase()
}

//~ operational classes

typealias Command = () -> Unit
typealias LazyArg = () -> String

class Operation(
        private val task: Task,
        private val command: String,
        private val arg: LazyArg? = null
) : Command {

    companion object {

        @Deprecated("For testing purposes")
        private suspend fun randomResult(): Boolean = Random(System.currentTimeMillis()).let {
            delay(1_000 * it.nextLong(10) + 1)
            it.nextBoolean()
        }
    }

    private fun CoroutineScope.displayBar(reporter: Reporter) =
            launch(Dispatchers.Default) {
                reporter.displayBar()
            }

    private fun CoroutineScope.displayTime(reporter: Reporter) =
            launch(Dispatchers.Default) {
                Timer(reporter).start()
            }

    override fun invoke() {
        runBlocking {
            Log.append(task.name)
            val reporter = Reporter(task.tag)
            val bar = displayBar(reporter)
            val time = displayTime(reporter)
            val process = launch {
                val result = Process(command, arg).execute().waitFor()
                val success = result == 0
                bar.cancelAndJoin()
                time.cancelAndJoin()
                reporter.displayResult(success)
                if (!success) throw Exception()
            }
            process.join()
        }
    }
}

//~ utilities

class Cli(args: Array<String>) {

    private val cli = CommandLineInterface("./gr.kts")

    val debug by cli.flagArgument(
        "-d",
        "Debug build variant",
        false,
        true
    )

    val release by cli.flagArgument(
        "-r",
        "Release build variant",
        false,
        true
    )

    val tasks by cli.positionalArgumentsList(
        "T...",
        "Set of tasks to perform [clean|build|check|install|dist]"
    )

    init {
        cli.parse(args)
    }
}

class Reporter(job: String) {

    companion object {
        private const val RED = "\u001B[31m"
        private const val GREEN = "\u001B[32m"
        private const val WHITE = "\u001B[0m"
        private const val MOVE = "\u001B[30D"
        private const val CLEAN = "\u001B[K"

        private const val FAIL = 'x'
        private const val SUCCESS = '✓'
        private const val INDICATOR = '•'
        private const val SPACE = ' '

        private const val WIDTH = 3
        private const val PADDING = 10

        fun cleanln() {
            print(MOVE)
            print(CLEAN)
        }

        private fun Long.format(): String {
            val min = TimeUnit.SECONDS.toMinutes(this)
            val sec = TimeUnit.MINUTES.toSeconds(min)
            return String.format("%02d:%02d", min, this - sec)
        }
    }

    private var index = 0
    private var inc = true
    private val chars = CharArray(WIDTH) { SPACE }
    private val jobName = job.padEnd(PADDING, SPACE)

    var time: Long = 0

    private fun generateBar() {
        if (inc && index == chars.size - 1) {
            inc = false
        }
        if (!inc && index == 0) {
            inc = true
        }
        chars.set(index, SPACE)
        index = if (inc) index.inc() else index.dec()
        chars.set(index, INDICATOR)
    }

    private fun printResult(success: Boolean) {
        val result = if (success) {
            "$GREEN$SUCCESS$WHITE"
        } else {
            "$RED$FAIL$WHITE"
        }
        val duration = time.format()
        val string = "[ $result ]  $jobName  $duration"
        println(string)
    }

    private fun printBar() {
        val bar = String(chars)
        val duration = time.format()
        val string = "[$bar]  $jobName  $duration "
        print(string)
    }

    suspend fun displayBar() {
        while (true) {
            cleanln()
            printBar()
            generateBar()
            delay(150)
        }
    }

    fun displayResult(success: Boolean) {
        cleanln()
        printResult(success)
    }
}

class Timer(private val reporter: Reporter) {

    suspend fun start() {
        var seconds: Long = 0
        while (true) {
            reporter.time = seconds++
            delay(1_000)
        }
    }
}

class Finder(private val variant: Variant) {

    fun findApk(): String {
        val cmd = Process(
                command = "find . -name *${variant.tag}.apk",
                redirect = false
        )
        val reader = cmd.execute().inputStream.buffered().reader()
        return reader.use {
            it.readLines().firstOrNull() ?: throw Exception("No apk found")
        }
    }
}

class Process(
        private val command: String,
        private val arg: LazyArg? = null,
        private val redirect: Boolean = true
) {

    private operator fun List<String>.plus(optional: String?): List<String> =
            if (optional == null) this else toMutableList().plusElement(optional)

    private fun ProcessBuilder.redirect() = apply {
        if (redirect) {
            redirectOutput(Log.redirect)
            redirectError(Log.redirect)
        }
    }

    fun execute(): java.lang.Process {
        val args = arg?.invoke()?.split(' ') ?: emptyList()
        val cmd = command.split(' ') + args
        return ProcessBuilder()
                .redirect()
                .command(cmd)
                .start()
    }
}

object Log {

    private val file = File("${System.getenv("TMPDIR")}/gr.append")

    val redirect: ProcessBuilder.Redirect = ProcessBuilder.Redirect.appendTo(Log.file)

    fun reset() = file.writeText("")

    fun load() = file.readLines().forEach(::println)

    fun append(message: String) = file.appendText("\n##### $message #####\n\n")
}

//~ factories of commands

object Tool {

    fun log() = {
        Log.load()
    }

    fun clear() = {
        Log.reset()
        Gradle.clean().invoke()
    }

    fun dist(variant: Variant) =
        Operation(DIST, "cp") { Finder(variant).findApk() + " ." }
}

object Adb {

    fun install(variant: Variant) =
        Operation(INSTALL, "adb install -r") { Finder(variant).findApk() }
}

object Gradle {

    fun clean() =
        Operation(CLEAN, "./gradlew clean")

    fun check() =
        Operation(CHECK, "./gradlew ktlint detekt")

    fun build(variant: Variant) =
        Operation(BUILD, "./gradlew assemble${variant.tag.capitalize()}")
}
