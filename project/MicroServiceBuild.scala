import play.sbt.routes.RoutesKeys._
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

object MicroServiceBuild extends Build with MicroService {

  val appName = "updated-print-suppressions"

  override lazy val plugins: Seq[Plugins] = Seq(
    SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin
  )

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()

  override lazy val playSettings = Seq(routesImport ++= Seq(
    "uk.gov.hmrc.ups.model._"
  ))
}

private object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  def apply() = Seq(
    ws,

    "uk.gov.hmrc"             %% "play-reactivemongo"     % "5.2.0",
    "uk.gov.hmrc"             %% "microservice-bootstrap" % "5.13.0",
    "uk.gov.hmrc"             %% "play-authorisation"     % "4.3.0",
    "uk.gov.hmrc"             %% "play-config"            % "4.3.0",
    "uk.gov.hmrc"             %% "play-scheduling"        % "4.1.0",
    "uk.gov.hmrc"             %% "play-health"            % "2.1.0",
    "uk.gov.hmrc"             %% "logback-json-logger"    % "3.1.0",
    "uk.gov.hmrc"             %% "domain"                 % "4.1.0",
    "uk.gov.hmrc"             %% "work-item-repo"         % "4.1.0",

    "uk.gov.hmrc"             %% "hmrctest"               % "2.3.0"             % "test, it",
    "uk.gov.hmrc"             %% "reactivemongo-test"     % "2.0.0"             % "test, it",
    "org.mockito"             %  "mockito-all"            % "1.9.5"             % "test, it",
    "com.github.tomakehurst"  %  "wiremock"               % "1.56"              % "test, it",
    "org.scalatest"           %% "scalatest"              % "2.2.6"             % "test, it",
    "org.pegdown"             %  "pegdown"                % "1.6.0"             % "test, it",
    "org.scalatestplus.play"  %% "scalatestplus-play"     % "1.5.1"             % "test, it",
    "com.typesafe.play"       %% "play-test"              % PlayVersion.current % "test, it"
  )
}

