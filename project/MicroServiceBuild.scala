import play.PlayImport.PlayKeys._
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

  import play.PlayImport._
  import play.core.PlayVersion

  val compile = Seq(
    "uk.gov.hmrc" %% "play-reactivemongo" % "4.8.0",
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % "4.4.0",
    "uk.gov.hmrc" %% "play-authorisation" % "3.3.0",
    "uk.gov.hmrc" %% "play-scheduling"  % "3.0.0",
    "uk.gov.hmrc" %% "play-health" % "1.1.0",
    "uk.gov.hmrc" %% "play-url-binders" % "1.0.0",
    "uk.gov.hmrc" %% "play-config" % "2.0.1",
    "uk.gov.hmrc" %% "play-json-logger" % "2.1.1",
    "uk.gov.hmrc" %% "domain" % "3.7.0",
    "uk.gov.hmrc" %% "work-item-repo" % "3.1.0"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % "1.6.0",
        "org.scalatest" %% "scalatest" % "2.2.6",
        "org.pegdown" % "pegdown" % "1.6.0",
        "com.typesafe.play" %% "play-test" % PlayVersion.current,
        "com.github.tomakehurst" % "wiremock" % "1.56",
        "org.scalatestplus" %% "play" % "1.2.0",
        "uk.gov.hmrc" %% "reactivemongo-test" % "1.6.0"
      ).map (_ % scope)
    }.test
  }

  object IntegrationTest {
    def apply() = new TestDependencies {

      override lazy val scope: String = "it"

      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % "1.6.0",
        "org.scalatest" %% "scalatest" % "2.2.6",
        "org.pegdown" % "pegdown" % "1.6.0",
        "com.typesafe.play" %% "play-test" % PlayVersion.current,
        "com.github.tomakehurst" % "wiremock" % "1.56",
        "org.scalatestplus" %% "play" % "1.2.0",
        "uk.gov.hmrc" %% "reactivemongo-test" % "1.6.0"
      ).map(_  % scope)
    }.test
  }

  def apply() = compile ++ Test() ++ IntegrationTest()
}

