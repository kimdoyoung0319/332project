ThisBuild / scalaVersion := "2.13.15"
ThisBuild / version := "0.1.0-SNAPSHOT"

libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
libraryDependencies += "io.grpc" % "grpc-stub" % scalapb.compiler.Version.grpcJavaVersion
libraryDependencies += "io.grpc" % "grpc-protobuf" % scalapb.compiler.Version.grpcJavaVersion
libraryDependencies += "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion

lazy val root = (project in file("."))
  .settings(
    name := "distrobuted-sorting"
  )

Compile / PB.targets := Seq(
  scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
)
