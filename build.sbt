ThisBuild / scalaVersion := "2.13.15"

ThisBuild / libraryDependencies := Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
  "io.grpc" % "grpc-stub" % scalapb.compiler.Version.grpcJavaVersion,
  "io.grpc" % "grpc-protobuf" % scalapb.compiler.Version.grpcJavaVersion,
  "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
  "org.scalactic" %% "scalactic" % "3.2.19",
  "org.scalatest" %% "scalatest" % "3.2.19" % "test",
  "com.lihaoyi" %% "os-lib" % "0.11.3",
  "ch.qos.logback" % "logback-classic" % "1.2.10",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
  "org.slf4j" % "jul-to-slf4j" % "1.7.36"
)

ThisBuild / scalacOptions += "-deprecation"

lazy val root = (project in file("."))
  .dependsOn(common, master, worker)
  .settings(name := "distrobuted-sorting")

lazy val common = (project in file("common"))
  .settings(
    name := "common",
    Compile / PB.protoSources := Seq(
      baseDirectory.value / "src" / "main" / "protobuf"
    ),
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    )
  )

/* TODO: Add dependencies for sbt-assembly. */
lazy val master = (project in file("master"))
  .dependsOn(common)
  .settings(
    name := "master",
    run / assembly / mainClass := Some("master.Main"),
    assembly / assemblyJarName := "master.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", _*) => MergeStrategy.discard
      case _                        => MergeStrategy.first
    }
  )

lazy val worker = (project in file("worker"))
  .dependsOn(common)
  .settings(
    name := "worker",
    run / assembly / mainClass := Some("worker.Main"),
    assembly / assemblyJarName := "worker.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", _*) => MergeStrategy.discard
      case _                        => MergeStrategy.first
    },
    run / fork := true,
    run / javaOptions ++= Seq(
      "-Xmx5G",
      "-Xms5G",
      "-XX:+HeapDumpOnOutOfMemoryError",
      "-XX:+UseG1GC"
    )
  )
