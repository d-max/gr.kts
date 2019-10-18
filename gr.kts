#!/usr/bin/env kscript
@file:MavenRepository("jitpack", "https://jitpack.io")
@file:DependsOn("com.github.kotlin.kotlinx~cli:kotlinx-cli-jvm:-SNAPSHOT")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")

import Gr.Task.*
import Gr.Variant.DEBUG
import Gr.Variant.RELEASE
import kotlinx.cli.CommandLineInterface
import kotlinx.cli.flagArgument
import kotlinx.cli.parse
import kotlinx.cli.positionalArgumentsList
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.system.exitProcess

//~ script

try {
    Cli(args)
        .createCommands()
        .forEach(Command::run)
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
    val task = Task.values().firstOrNull { it.name.toLowerCase() == arg }
    when (task) {
        CLEAN -> Gradle.clean()
        BUILD -> Gradle.build(variant)
        CHECK -> Gradle.check()
        INSTALL -> Adb.install()
        RUN -> Adb.launch()
        else -> throw Exception("Unknown tasks set. Use [clean|build|check|install|run]")
    }
}

//~ classes

enum class Variant {
    DEBUG, RELEASE
}

enum class Task {
    CLEAN, BUILD, CHECK, INSTALL, RUN
}

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
        "Set of tasks to perform [clean|build|check|install|run]"
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
            return String.format("%02d:%02d", min, this)
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
        val string = "[$bar]  $jobName  $duration"
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

class Command(
    private val task: Task,
    private val command: String
) {

    companion object {

        private fun String.execute() = ProcessBuilder()
            .command(split(' '))
            .start()

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

    fun run() {
        runBlocking {
            val reporter = Reporter(task.name.toLowerCase())
            val bar = displayBar(reporter)
            val time = displayTime(reporter)
            val process = launch {
                val result = command.execute().waitFor()
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

object Adb {

    fun install() =
        Command(INSTALL, "adb install") // todo add apk name

    fun launch() =
        Command(RUN, "adb am") // tood activity name
}

object Gradle {

    fun clean() =
        Command(CLEAN, "./gradlew clean")

    fun check() =
        Command(CHECK, "./gradlew ktlint detekt")

    fun build(variant: Variant) =
        Command(BUILD, "./gradlew assemble${variant.name.toLowerCase().capitalize()}")
}
