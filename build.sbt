val sharedSettings = Seq(
  scalaVersion := "3.4.0",
  name := "micro-routing",
  version := "0.0.1"
)



lazy val app =
  // select supported platforms
  crossProject(JSPlatform, JVMPlatform, NativePlatform)
    .crossType(CrossType.Full) // [Pure, Full, Dummy], default: CrossType.Full
    .withoutSuffixFor(JVMPlatform)
    .settings(sharedSettings)
    .jsSettings(/* ... */) // defined in sbt-scalajs-crossproject
    .jvmSettings(
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.17" % "test"
    )
    // configure Scala-Native settings
    .nativeSettings(/* ... */) // defined in sbt-scala-native

lazy val root = project.in(file(".")).
  aggregate(app.js, app.jvm, app.native).
  settings(
    publish := {},
    publishLocal := {},
  )

