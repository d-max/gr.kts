#!/usr/bin/env kscript
@file:MavenRepository("jitpack", "https://jitpack.io" )
@file:DependsOn("com.github.kotlin.kotlinx~cli:kotlinx-cli-jvm:-SNAPSHOT")

import Gr.Task.*
import Gr.Variant.*
import kotlinx.cli.*
import java.lang.ProcessBuilder.Redirect
import kotlin.system.exitProcess

//~ script



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
    "Gradle set of tasks to perform [build, check, install]",
    mapping = {
      when (it) {
        "clean" -> CLEAN
        "build" -> BUILD
        "check" -> CHECK
        "install" -> INSTALL
        "run" -> LAUNCH
        else -> error("Unknown tasks set. Use [build|check|install|run]")
      }
    }
  )

  init {
    cli.parse(args)
  }
}

class Counter(private val title: String) {
  fun go() {

  }
}

class Command(
  private val command: String,
  private vararg val args: String
) {

  var redirect: Boolean = true
  var name: String = command

  fun run() {
    ProcessBuilder()
      .command(command, *args)
      .apply {
        if (redirect) redirectOutput(Redirect.INHERIT)
      }
      .start()
      .waitFor()
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
