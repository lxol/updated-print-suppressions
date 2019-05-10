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

    "uk.gov.hmrc"             %% "play-reactivemongo"     % "6.7.0",
    "uk.gov.hmrc"             %% "microservice-bootstrap" % "10.4.0",
    "uk.gov.hmrc"             %% "play-scheduling"        % "5.4.0",
    "uk.gov.hmrc"             %% "domain"                 % "5.1.0",
    "uk.gov.hmrc"             %% "work-item-repo"         % "5.1.0",
    "uk.gov.hmrc"             %% "hmrctest"               % "3.5.0-play-25"     % "test, it",
    "uk.gov.hmrc"             %% "reactivemongo-test"     % "3.1.0"             % "test, it",
    "org.mockito"             %  "mockito-all"            % "1.9.5"             % "test, it",
    "com.github.tomakehurst"  %  "wiremock"               % "1.56"              % "test, it",
    "org.scalatest"           %% "scalatest"              % "3.0.5"             % "test, it",
    "org.pegdown"             %  "pegdown"                % "1.6.0"             % "test, it",
    "org.scalatestplus.play"  %% "scalatestplus-play"     % "2.0.1"             % "test, it",
    "com.typesafe.play"       %% "play-test"              % PlayVersion.current % "test, it"
  )
}
