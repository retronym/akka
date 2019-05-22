package io.github.retronym

import sbt._
import Keys._

object SbtArgsFilePlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = sbt.plugins.JvmPlugin
  import java.io.File.pathSeparator
  val argsFileContents = taskKey[String]("Contents of file suitable for `scalac @args.txt`")
  val argsFile = taskKey[Unit]("Write compiler command line into an args file suitable for `scalac @target/compile.args`")
  val argsFileMacroClasspath = settingKey[Option[File => Boolean]]("If defined, pass file names in the classpath matching the provided function to -Ymacro-classpath")
  override lazy val projectSettings = List(Compile, Test).flatMap(c => inConfig(c)(Seq(
    argsFileContents := {
      val sourcesValue = sources.value
      val depClassPathValue = dependencyClasspath.value
      val cp = if (depClassPathValue.isEmpty) Nil else ("-classpath" :: depClassPathValue.map(_.data.toString).mkString(pathSeparator) :: Nil)
      val macroCp = argsFileMacroClasspath.value match {
        case Some(f) =>
          depClassPathValue.map(_.data).filter(f) match {
            case Nil => Nil
            case xs => ("-Ymacro-classpath" :: xs.mkString(pathSeparator) :: Nil)
          }
        case None => Nil
      }
      val result = (scalacOptions.value.toList ::: List("-d", classDirectory.value) ::: cp ::: macroCp ::: sourcesValue.distinct.toList).mkString("\n")
      java.nio.file.Files.createDirectories(classDirectory.value.toPath)

      if (sourcesValue.isEmpty) "" else result
    },
    argsFile := {
      val f = target.value / (normalizedName.value + "-" + c.name + ".args")
      val contents = argsFileContents.value
      val log = streams.value.log
      if (!contents.isEmpty) {
        IO.write(f, contents)
        log.info("Wrote compiler comand line to: " + f.getAbsolutePath)
      }
    },
    argsFileMacroClasspath := None
  )))
}
