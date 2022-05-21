package matt.kjcl

import matt.auto.desktop
import matt.auto.openInIntelliJ
import matt.exec.cmd.CommandLineApp
import matt.kbuild.ismac
import matt.kjcl.ModType.ABSTRACT
import matt.kjcl.ModType.APP
import matt.kjcl.ModType.APPLIB
import matt.kjcl.ModType.CLAPP
import matt.kjcl.ModType.LIB
import matt.kjlib.commons.USER_DIR
import matt.kjlib.file.get
import matt.kjlib.log.NEVER
import matt.kjlib.log.err
import matt.kjlib.recurse.chain
import matt.kjlib.shell.execReturn
import matt.kjlib.str.cap
import matt.kjlib.str.hasWhiteSpace
import matt.kjlib.str.lower
import matt.klib.Command
import matt.klib.CommandWithExitStatus
import matt.klib.ExitStatus
import matt.klib.ExitStatus.CONTINUE
import matt.klib.ExitStatus.EXIT
import matt.klib.log.warn
import matt.klibexport.tfx.isInt
import java.net.URI
import kotlin.system.exitProcess

const val JIGSAW = false

val KJ_Fold = USER_DIR.chain { it.parentFile }.first { it.name == "KJ" }

fun main() = CommandLineApp(mainPrompt = "Hello KJ (KJ_Fold=${KJ_Fold.absolutePath})\n") {
  acceptAny { command ->
	val coms = Commands.values().map { it.name }
	when {
	  command.hasWhiteSpace || ":" !in command -> err("commands should be separated by \":\"")
	}
	val argv = command.split(":")
	val comString = argv[0]
	when {
	  comString !in coms -> err("possible commands: $coms")
	  argv.size != 2     -> err("please specify new module name")
	}
	val com = Commands.valueOf(argv[0])
	val exitStatus = com.run(argv[1])
	warn(
	  "new module created (TODO: PLEASE MAKE IT SO KJCL WONT CRASH IF I PRESS ENTER WHILE PREVIOUS COMMAND IS RUNNING)"
	)
	when (exitStatus) {
	  CONTINUE -> {
		/*do nothing*/
	  }
	  EXIT     -> {
		println("exiting")
		exitProcess(0)
	  }
	}
  }
}.start()


enum class Commands: CommandWithExitStatus {
  @Suppress("EnumEntryName")
  newmod {

	override fun run(arg: String): ExitStatus {
	  val subProj = SubProject(arg)
	  subProj.apply {


		if (fold.exists()) err("$path already exists")

		println("type?" + ModType.values().mapIndexed { index, modType -> "$index=$modType" }.joinToString(","))
		val response = readLine()!!.run {
		  when {
			!isInt()                                        -> err("must be integer")
			toInt() < 0 || toInt() >= ModType.values().size -> err("must use valid index")
		  }
		  toInt()
		}

		val type = ModType.values()[response]

		if (JIGSAW && type != ABSTRACT) {
		  java.mkdirs()
		  java["module-info.java"].writeText(
			"""
    module $modname {
    
    }
  """.trimIndent()
		  )
		}

		buildGradleKts.parentFile.mkdirs()
		buildGradleKts.writeText(gradleTemplate(type))
		if (type != ABSTRACT) kotlin[packpath].mkdirs()

		fold["modtype.txt"].writeText(type.name)

		val mainKT = kotlin[packpath][nameLast.cap() + "Main.kt"].takeIf { type in listOf(APP, CLAPP) }

		when (type) {
		  APP      -> {
			mainKT!!.writeText(
			  """
        package $modname
      """.trimIndent()
			)
		  }
		  CLAPP    -> {
			mainKT!!.writeText(
			  """
        package $modname
      """.trimIndent()
			)
		  }
		  APPLIB   -> {
			kotlin[packpath]["$nameLast.kt"].writeText(
			  """
        package $modname
      """.trimIndent()
			)
		  }
		  LIB      -> {
			kotlin[packpath]["$nameLast.kt"].writeText(
			  """
        package $modname
      """.trimIndent()
			)
		  }
		  ABSTRACT -> {
		  }
		}
		if (ismac) {
		  mainKT?.openInIntelliJ() ?: buildGradleKts.openInIntelliJ()
		}
	  }
	  return CONTINUE
	}
  },
  tosubmod {
	override fun run(arg: String): ExitStatus {
	  val subProj = SubProject(arg)

	  /*dont worry if repo already exists. it wont create another. safe to run over again.*/
	  println(
		execReturn(
		  wd = subProj.fold,
		  "/bin/zsh",
		  "-c",
		  listOf(
			"/opt/homebrew/bin/gh",
			"repo",
			"create",
			"--private",
			subProj.nameLast
		  ).joinToString(separator = " "),
		  verbose = true
		)
	  )

	  println(execReturn(wd = subProj.fold, "/usr/bin/git", "init", verbose = true))
	  println(execReturn(wd = subProj.fold, "/usr/bin/git", "add", "--all", verbose = true))
	  println(execReturn(wd = subProj.fold, "/usr/bin/git", "commit", "-m", "first commit", verbose = true))
	  println(execReturn(wd = subProj.fold, "/usr/bin/git", "status", verbose = true))

	  val repoURL = "https://github.com/mgroth0/${subProj.nameLast}"

	  println(
		execReturn(
		  wd = subProj.fold,
		  "/usr/bin/git",
		  "remote",
		  "add",
		  "origin",
		  repoURL,
		  verbose = true
		)
	  )
	  println(
		execReturn(
		  wd = subProj.fold, "/usr/bin/git", "push", "--set-upstream", "origin", "master", verbose = true
		)
	  )
	  desktop.browse(URI(repoURL))
	  println(
		execReturn(
		  wd = KJ_Fold.parentFile, "rm", "-rf", subProj.fold.absolutePath, verbose = true
		)
	  )
	  println(
		execReturn(
		  wd = KJ_Fold.parentFile, "/usr/bin/git", "add", "--all", verbose = true
		)
	  )
	  println(
		execReturn(
		  wd = KJ_Fold.parentFile,
		  "/usr/bin/git",
		  "commit",
		  "-m",
		  "remove ${subProj.nameLast} which is to become submodule",
		  verbose = true
		)
	  )
	  println(
		execReturn(
		  wd = KJ_Fold.parentFile,
		  "/usr/bin/git",
		  "submodule",
		  "add",
		  repoURL,
		  "KJ/${subProj.nameLast}",
		  verbose = true
		)
	  )
	  println(
		execReturn(
		  wd = KJ_Fold.parentFile, "/usr/bin/git", "add", "--all", verbose = true
		)
	  )
	  println(
		execReturn(
		  wd = KJ_Fold.parentFile,
		  "/usr/bin/git",
		  "commit",
		  "-m",
		  "add ${subProj.nameLast} submodule",
		  verbose = true
		)
	  )
	  println(
		execReturn(
		  wd = KJ_Fold.parentFile, "/usr/bin/git", "push", verbose = true
		)
	  )
	  return CONTINUE
	}
  },
  addsubmod {
	override fun run(arg: String): ExitStatus {
	  val subProj = SubProject(arg)
	  execReturn(
		wd = KJ_Fold.parentFile,

		"/usr/bin/git",
		"submodule",
		"add",
		"https://github.com/mgroth0/${subProj.path}",
		"kj/${subProj.path}",

		verbose = true
	  )
	  return CONTINUE
	}
  },
  exit {
	override fun run(arg: String): ExitStatus {
	  return EXIT
	}
  }
}


enum class ModType { APP, CLAPP, APPLIB, LIB, ABSTRACT }

@Suppress("KotlinConstantConditions")
fun gradleTemplate(type: ModType) = when (type) {
  ABSTRACT -> ""
  else     -> """
		dependencies {
			implementation(projects.kj.${
	when (type) {
	  APP      -> "gui"
	  CLAPP    -> "exec"
	  APPLIB   -> "kjlib"
	  LIB      -> "kjlib"
	  ABSTRACT -> NEVER
	}
  })
		}""".trimIndent()
}


class SubProject(arg: String) {
  val nameLast = arg.substringAfterLast(".")
  val modname = "matt." + arg.lower()
  val path = arg.replace(".", "/")
  val packpath = modname.replace(".", "/")
  val fold = KJ_Fold[path]
  val kotlin = fold["src/main/kotlin"]
  val java = fold["src/main/java"]
  val buildGradleKts = fold["build.gradle.kts"]
}