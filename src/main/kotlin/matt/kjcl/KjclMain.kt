package matt.kjcl

import matt.auto.desktop
import matt.auto.openInIntelliJ
import matt.exec.cmd.CommandLineApp
import matt.kbuild.cap
import matt.kbuild.git.SimpleGit
import matt.kjcl.ModType.ABSTRACT
import matt.kjcl.ModType.APP
import matt.kjcl.ModType.APPLIB
import matt.kjcl.ModType.CLAPP
import matt.kjcl.ModType.LIB
import matt.kjlib.commons.USER_DIR
import matt.kjlib.file.get
import matt.kjlib.lang.NEVER
import matt.kjlib.lang.err
import matt.kjlib.lang.jlang.ismac
import matt.kjlib.stream.recurse.chain
import matt.kjlib.shell.execReturn
import matt.kjlib.str.hasWhiteSpace
import matt.kjlib.str.lower
import matt.klib.CommandWithExitStatus
import matt.klib.ExitStatus
import matt.klib.ExitStatus.CONTINUE
import matt.klib.ExitStatus.EXIT
import matt.klib.log.warn
import matt.klibexport.tfx.isInt
import java.io.File
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

		val fileToOpen = when (type) {
		  APP, CLAPP  -> kotlin[packpath][nameLast.cap() + "Main.kt"].takeIf { type in listOf(APP, CLAPP) }!!.apply {
			writeText("""package $modname""".trimIndent())
		  }
		  APPLIB, LIB -> kotlin[packpath]["$nameLast.kt"].apply {
			writeText("""package $modname""".trimIndent())
		  }
		  ABSTRACT    -> null
		}
		if (ismac) {
		  fileToOpen?.openInIntelliJ() ?: buildGradleKts.openInIntelliJ()
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

	  subProj.git.apply {
		init()
		addAll()
		commit("first commit")
		status()
		remoteAddOrigin(subProj.url)
		push(setUpstream = true)
		desktop.browse(URI(subProj.url))
		execReturn(wd = KJ_Fold.parentFile, "rm", "-rf", subProj.fold.absolutePath, verbose = true, printResult = true)
	  }
	  val rootGit = SimpleGit(projectDir = KJ_Fold.parentFile, debug = true)
	  rootGit.apply {
		addAll()
		commit("remove ${subProj.path} which is to become submodule")
		submoduleAdd(url = subProj.url, path = subProj.pathRelativeToRoot)
		addAll()
		commit("add ${subProj.path} submodule")
		push()
	  }
	  return CONTINUE
	}
  },
  addsubmod {
	override fun run(arg: String): ExitStatus {
	  val subProj = SubProject(arg)
	  val rootGit = SimpleGit(projectDir = KJ_Fold.parentFile, debug = true)
	  rootGit.submoduleAdd(url = subProj.url, path = subProj.pathRelativeToRoot)
	  return CONTINUE
	}
  },
  removesubmod {
	override fun run(arg: String): ExitStatus {
	  val subProj = SubProject(arg)
	  val rootGit = SimpleGit(projectDir = KJ_Fold.parentFile, debug = true)

	  /*https://stackoverflow.com/questions/1260748/how-do-i-remove-a-submodule*/
	  rootGit.gitRm(subProj.pathRelativeToRoot)
	  execReturn(
		wd = KJ_Fold.parentFile,
		"rm",
		"-rf",
		KJ_Fold.parentFile[".git"]["modules"][subProj.pathRelativeToRoot].absolutePath,
		verbose = true,
		printResult = true
	  )

	  /*this part results in fatal error, so I made on comment in stackoverflow...*/
	  rootGit.gitConfigRemoveSection("submodule.${subProj.pathRelativeToRoot.replace(File.separatorChar, '.')}")


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
	  LIB      -> "kjlib.lang"
	  ABSTRACT -> NEVER
	}
  })
		}""".trimIndent()
}


class SubProject(arg: String) {
  val nameLast = arg.substringAfterLast(".")
  val modname = "matt." + arg.lower()
  val path = arg.replace(".", "/")
  val pathRelativeToRoot = "KJ/${path}"
  val url = "https://github.com/mgroth0/${path}"
  val packpath = modname.replace(".", "/")
  val fold = KJ_Fold[path]
  val kotlin = fold["src/main/kotlin"]
  val java = fold["src/main/java"]
  val buildGradleKts = fold["build.gradle.kts"]
  val git by lazy {
	SimpleGit(projectDir = fold, debug = true)
  }
}


