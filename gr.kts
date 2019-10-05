#!/usr/bin/env kscript
@file:MavenRepository("jitpack", "https://jitpack.io")
@file:DependsOn("com.github.kotlin.kotlinx~cli:kotlinx-cli-jvm:-SNAPSHOT")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")

import Gr.Task.*
import Gr.Variant.*
import kotlinx.cli.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

//~ script

try {
    Cli(args)
        .createCommands()
        .forEach(Command::run)
} catch (e: Exception) {
    Reporter.cleanln()
    println(e.message)
    exitProcess(1)
}

fun Cli.createCommands() = tasks.map {
    when (it) {
        CLEAN -> Gradle.clean()
        BUILD -> Gradle.build(variant)
        CHECK -> Gradle.check()
        INSTALL -> Adb.install()
        RUN -> Adb.launch()
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

    val variant by cli.flagValueArgument(
        "-v",
        "variant",
        "Set build variant [debug|release]",
        DEBUG,
        mapping = {
            when (it) {
                "r" -> RELEASE
                "d" -> DEBUG
                else -> error("Unknown build variant. Use [r|d]")
            }
        }
    )

    val tasks by cli.positionalArgumentsList(
        "T...",
        "Set of tasks to perform [clean|build|check|install|run]",
        mapping = {
            when (it) {
                "run" -> RUN
                "clean" -> CLEAN
                "build" -> BUILD
                "check" -> CHECK
                "install" -> INSTALL
                else -> error("Unknown tasks set. Use [clean|build|check|install|run]")
            }
        }
    )

    init {
        cli.parse(args)
    }
}

class Reporter(job: String) {

    companion object {
        private const val FORMAT = "mm:ss"

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
            val min = TimeUnit.MILLISECONDS.toMinutes(this)
            val sec = TimeUnit.MILLISECONDS.toSeconds(this)
            return String.format(
                "%02d:%02d",
                min, sec - TimeUnit.MINUTES.toSeconds(min)
            )
        }
    }

    private var index = 0
    private var inc = true
    private val chars = CharArray(WIDTH) { SPACE }
    private val jobName = job.padEnd(PADDING, SPACE)

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

    private fun printResult(time: Long, success: Boolean) {
        val result = if (success) {
            "$GREEN$SUCCESS$WHITE"
        } else {
            "$RED$FAIL$WHITE"
        }
        val duration = time.format()
        val string = "[ $result ]  $jobName  $duration"
        println(string)
    }

    fun printBar() {
        val bar = String(chars)
        val string = "[$bar]  $jobName  "
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

    fun displayResult(time: Long, success: Boolean) {
        cleanln()
        printResult(time, success)
    }
}

class Command(
    private val task: Task,
    private val command: String,
    private vararg val args: String
) {

    fun run() {
        runBlocking {
            val reporter = Reporter(task.name.toLowerCase())
            val bar = launch(Dispatchers.Default) {
                reporter.displayBar()
            }
            val process = launch {
                val begin = System.currentTimeMillis()
//                val success = Random(System.currentTimeMillis()).let {
//                    delay(1_000 * it.nextLong(10))
//                    it.nextBoolean()
//                }
                val result = ProcessBuilder()
                    .command(command, *args)
                    .start()
                    .waitFor()
                val success = result == 0
                val time = System.currentTimeMillis() - begin
                bar.cancelAndJoin()
                reporter.displayResult(time, success)
                if (!success) throw Exception()
            }
            process.join()
        }
    }
}

object Adb {

    fun install() =
        Command(INSTALL, "adb", "install") // todo add apk name

    fun launch() =
        Command(RUN, "adb", "am") // tood activity name
}

object Gradle {

    fun clean() =
        Command(CLEAN, "./gradlew", "clean")

    fun check() =
        Command(CHECK, "./gradlew", "ktlint", "detekt")

    fun build(variant: Variant) =
        Command(BUILD, "./gradlew", "assemble${variant.name.toLowerCase().capitalize()}")
}
