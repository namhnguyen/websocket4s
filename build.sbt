name := "websocket4s"

version := "1.0"

scalaVersion := "2.11.6"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

scalacOptions ++= Seq(
  "-deprecation"
  , "-encoding" , "UTF-8"
  , "-feature"
  , "-target:jvm-1.8"
  , "-unchecked"
  , "-Ywarn-adapted-args"
  , "-Xlint")


libraryDependencies ++= {
  val akkaV             =   "2.3.11"
  val json4sV           =   "3.2.11"
  val typesafeConfig    =   "1.3.0"
  Seq(
    "org.json4s"                    %% "json4s-jackson"       %   json4sV ,
    "com.typesafe.akka"             %% "akka-actor"           %   akkaV   ,
    "com.typesafe.akka"             %% "akka-remote"          %   akkaV   ,
    "com.typesafe"                  %  "config"               %   typesafeConfig,
    "com.wandoulabs.akka"           %% "spray-websocket"      %   "0.1.4",
    "com.typesafe.scala-logging"    %% "scala-logging"        %   "3.1.0" ,
    //"com.typesafe.scala-logging"    %% "scala-logging-slf4j"  %   "2.1.2",
    "ch.qos.logback"                % "logback-classic"       %   "1.1.3",
    "org.scalatest"                 %% "scalatest"            %   "2.2.4"   % "test",
    "org.slf4j"                     % "jul-to-slf4j"          %   "1.7.12"  % "test",
    "io.spray"                      %% "spray-testkit"        %   "1.3.2"   % "test",
    "com.typesafe.akka"             %% "akka-slf4j"           %   "2.3.9"   % "test",
    "com.typesafe.akka"             %% "akka-testkit"         %   "2.3.9"   % "test"
  )
}