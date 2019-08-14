inThisBuild(List(
  scmInfo := Some(ScmInfo(
    browseUrl = url("https://github.com/scalacenter/bsp"),
    connection = "scm:git:git@github.com:scalacenter/bsp.git"
  )),
  bloopExportJarClassifiers := Some(Set("sources")),
  Keys.resolvers := {
    val oldResolvers = Keys.resolvers.value
    val scalacenterResolver = Resolver.bintrayRepo("scalacenter", "releases")
    (oldResolvers :+ scalacenterResolver).distinct
  },
))
import java.io.File
import org.eclipse.xtend.core.XtendInjectorSingleton
import org.eclipse.xtend.core.compiler.batch.XtendBatchCompiler

// force javac to fork by setting javaHome to get error messages during compilation,
// see https://github.com/sbt/zinc/issues/520
def inferJavaHome() =
  Some(file(System.getProperty("java.home")).getParentFile)

cancelable.in(Global) := true

lazy val bsp = project
  .in(file("."))
  .aggregate(bsp4s, bsp4j, tests, `bsp-testkit`)
  .settings(
    skip in publish := true,
  )

lazy val bsp4s = project
  .in(file("bsp4s"))
  .settings(
    publishArtifact in Test := false,
    sources in (Compile, doc) := Nil,
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
    libraryDependencies ++= List(
      "io.circe" %% "circe-core" % "0.9.0",
      "io.circe" %% "circe-derivation" % "0.9.0-M4",
      "org.scalameta" %% "lsp4s" % "0.2.0"
    )
  )

lazy val bsp4j = project
  .in(file("bsp4j"))
  .settings(
    crossVersion := CrossVersion.disabled,
    autoScalaLibrary := false,
    javacOptions.in(Compile) ++= List(
      "-Xlint:all",
      "-Werror"
    ),
    javacOptions.in(Compile, doc) := List("-Xdoclint:none"),
    javaHome.in(Compile) := inferJavaHome(),
    javaHome.in(Compile, doc) := inferJavaHome(),
    TaskKey[Unit]("xtend") := {
      val compiler = XtendInjectorSingleton.INJECTOR.getInstance(classOf[XtendBatchCompiler])
      val classpath = dependencyClasspath.in(Compile).value.map(_.data).mkString(File.pathSeparator)
      compiler.setClassPath(classpath)
      val sourceDir = sourceDirectory.in(Compile).value / "java"
      compiler.setSourcePath(sourceDir.getCanonicalPath)
      val outDir = sourceDirectory.in(Compile).value / "xtend-gen"
      IO.delete(outDir)
      compiler.setOutputPath(outDir.getCanonicalPath)
      object XtendError
          extends Exception(s"Compilation of Xtend files in $sourceDir failed.")
          with sbt.internal.util.FeedbackProvidedException
      if (!compiler.compile())
        throw XtendError
    },
    unmanagedSourceDirectories.in(Compile) += sourceDirectory.in(Compile).value / "xtend-gen",
    libraryDependencies ++= List(
      "org.eclipse.lsp4j" % "org.eclipse.lsp4j.generator" % "0.8.0",
      "org.eclipse.lsp4j" % "org.eclipse.lsp4j.jsonrpc" % "0.8.0"
    )
  )

lazy val tests = project
  .in(file("tests"))
  .settings(
    skip.in(publish) := true,
    libraryDependencies ++= List(
      "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0",
      "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0",
      "org.scala-sbt.ipcsocket" % "ipcsocket" % "1.0.0",
      "org.scalatest" %% "scalatest" % "3.0.5"
    )
  )
  .dependsOn(bsp4s, bsp4j, `bsp-testkit`)

lazy val `bsp-testkit` = project
  .in(file("testkit"))
  .settings(
    mainClass in Compile := Some("ch.epfl.scala.bsp.mock.MockServer"),
    executableScriptName := "mockbsp",
    bashScriptExtraDefines += """addJava "-Dscript.path=${app_home}/"""" + executableScriptName.value,
    batScriptExtraDefines += """call :add_java "-Dscript.path=%APP_HOME%\\"""" + executableScriptName.value + ".bat",
    libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0"
  )
  .dependsOn(bsp4s)
  .dependsOn(bsp4j)
  .enablePlugins(JavaAppPackaging)