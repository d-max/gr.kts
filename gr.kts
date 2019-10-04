#!/usr/bin/env kscript
@file:MavenRepository("jitpack", "https://jitpack.io" )
@file:DependsOn("com.github.kotlin.kotlinx~cli:kotlinx-cli-jvm:-SNAPSHOT")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")

import Gr.Task.*
import Gr.Variant.*
import java.lang.ProcessBuilder.Redirect
import kotlin.system.exitProcess
import kotlinx.cli.*
import kotlinx.coroutines.*

//~ script

try {
  val cli = Cli(args)
  val counter = Counter().apply { start() }
  createCommands(cli).forEach loop@ {
    counter.jobName = it.name
    val success = it.run()
    counter.next(success)
    if (!success) return@loop
  }
  counter.stop()
} catch(e: Exception) {
  println(e.message)
  exitProcess(1)
}

fun createCommands(cli: Cli) = cli.tasks.map {
  val command = when(it) {
    CLEAN -> Gradle.clean()
    BUILD -> Gradle.build(cli.variant)
    CHECK -> Gradle.check()
    INSTALL -> Adb.install()
    LAUNCH -> Adb.launch()
  }
  command.apply {
    redirect = !cli.quite
    name = it.name.toLowerCase()
  }
}

//~ classes

enum class Variant {
  DEBUG, RELEASE
}

enum class Task {
  CLEAN, BUILD, CHECK, INSTALL, LAUNCH
}

class Cli(args: Array<String>) {

  private val cli = CommandLineInterface("./gr.kts")

  val quite by cli.flagArgument(
    "-q",
    "Silent mode"
  )

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
        "clean" -> CLEAN
        "build" -> BUILD
        "check" -> CHECK
        "install" -> INSTALL
        "run" -> LAUNCH
        else -> error("Unknown tasks set. Use [clean|build|check|install|run]")
      }
    }
  )

  init {
    cli.parse(args)
  }
}

class Counter {

  private var job: Job? = null

  private var index = 0
  private var inc = true
  private val chars = Array<Char>(4) { ' ' }

  var jobName: String = ""

  private fun cleanln() {
    print("\u001B[100D")
    print("\u001B[K")
  }

  private fun generate() {
    if (inc && index == chars.size - 1) {
      inc = false
    }
    if (!inc && index == 0) {
      inc = true
    }
    chars.set(index, ' ')
    index = if (inc) index.inc() else index.dec()
    chars.set(index, '•')
  }

  private fun print() {
    print('[')
    chars.forEach { print(it) }
    print(']')
    print(' ')
    print(jobName)
  }

  fun start() {
    job = GlobalScope.launch {
      while(true) {
        cleanln()
        print()
        generate()
        delay(100)
      }
    }
  }

  fun stop() {
    job?.cancel()
    cleanln()
  }

  fun next(success: Boolean) {
    val status = if (success) "ok" else "^^"
    cleanln()
    print("[ $status ]")
    print(' ')
    print(jobName)
    println()
  }
}

class Command(
  private val command: String,
  private vararg val args: String
) {

  var redirect: Boolean = true
  var name: String = command

  fun run(): Boolean {
    Thread.sleep(3_000)
    return false
//    ProcessBuilder()
//      .command(command, *args)
//      .apply {
//        if (redirect) redirectOutput(Redirect.INHERIT)
//      }
//      .start()
//      .waitFor()
  }
}

object Adb {

  fun install() =
    Command("adb", "install") // todo add apk name

  fun launch() =
    Command("adb", "am") // tood activity name
}

object Gradle {

  fun clean() =
    Command("./gradlew", "clean")

  fun check() =
    Command("./gradlew", "ktlint", "detekt")

  fun build(variant: Variant) =
    Command("./gradlew", "assemble${variant.name.toLowerCase().capitalize()}")
}
