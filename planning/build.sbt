libraryDependencies ++= Seq(
  "com.martiansoftware" % "jsap" % "2.1",
  "de.sciss" % "prefuse-core" % "1.0.0",
  "org.projectlombok" % "lombok" % "1.18.2" % Provided
)


mainClass := Some("fr.laas.fape.planning.Planning")

exportJars := true



