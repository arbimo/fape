name := "planning"

libraryDependencies ++= Seq(
  "com.martiansoftware" % "jsap" % "2.1",
  "junit" % "junit" % "4.12" % "test",
  "de.sciss" % "prefuse-core" % "1.0.0",
  "org.projectlombok" % "lombok" % "1.16.6"
)

mainClass := Some("fape.Planning")

com.github.retronym.SbtOneJar.oneJarSettings

exportJars := true

mainClass in oneJar := Some("fape.Planning")


