name := "load-balancer"

version := "1.0"

scalaVersion := "2.12.8"

organization := "op.assignment"

libraryDependencies ++= Seq(
	"com.typesafe.akka" %% "akka-actor-typed" 			    % "2.6.9",
  "com.typesafe.akka" %% "akka-slf4j"                 % "2.6.9",
  "ch.qos.logback"     % "logback-classic"            % "1.2.3",
 	"com.typesafe.akka" %% "akka-actor-testkit-typed"   % "2.6.9"	% Test,
  "org.scalatest" 	  %% "scalatest"                  % "3.0.5"	% Test
)

// see https://tpolecat.github.io/2017/04/25/scalac-flags.html for scalacOptions descriptions
scalacOptions ++= Seq(
    "-deprecation",     //emit warning and location for usages of deprecated APIs
    "-unchecked",       //enable additional warnings where generated code depends on assumptions
    "-explaintypes",    //explain type errors in more detail
    "-Ywarn-dead-code", //warn when dead code is identified
    "-Xfatal-warnings"  //fail the compilation if there are any warnings
)


