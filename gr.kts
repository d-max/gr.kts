#!/usr/bin/env kscript
@file:MavenRepository("jitpack", "https://jitpack.io" )
@file:DependsOn("com.github.kotlin.kotlinx~cli:kotlinx-cli-jvm:-SNAPSHOT")

import kotlinx.cli.*

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

