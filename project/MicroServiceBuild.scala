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

  private val microserviceBootstrapVersion = "4.2.1"
  private val playAuthVersion = "3.1.0"
  private val playHealthVersion = "1.1.0"
  private val playJsonLoggerVersion = "2.1.1"
  private val playUrlBindersVersion = "1.0.0"
  private val playConfigVersion = "2.0.1"
  private val domainVersion = "3.6.0"
  private val hmrcTestVersion = "1.6.0"
  private val playReactivemongoVersion = "4.8.0"
  private val playScheduleVersion = "3.0.0"
  private val workItemRepoVersion = "3.1.0"

  val compile = Seq(
    "uk.gov.hmrc" %% "play-reactivemongo" % playReactivemongoVersion,
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "play-authorisation" % playAuthVersion,
    "uk.gov.hmrc" %% "play-scheduling"  % playScheduleVersion,
    "uk.gov.hmrc" %% "play-health" % playHealthVersion,
    "uk.gov.hmrc" %% "play-url-binders" % playUrlBindersVersion,
    "uk.gov.hmrc" %% "play-config" % playConfigVersion,
    "uk.gov.hmrc" %% "play-json-logger" % playJsonLoggerVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "uk.gov.hmrc" %% "work-item-repo" % workItemRepoVersion
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion,
        "org.scalatest" %% "scalatest" % "2.2.6",
        "org.pegdown" % "pegdown" % "1.5.0",
        "com.typesafe.play" %% "play-test" % PlayVersion.current,
        "uk.gov.hmrc" %% "reactivemongo-test" % "1.5.0"
      ).map (_ % scope)
    }.test
  }

  object IntegrationTest {
    def apply() = new TestDependencies {

      override lazy val scope: String = "it"

      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion,
        "org.scalatest" %% "scalatest" % "2.2.6",
        "org.pegdown" % "pegdown" % "1.5.0",
        "com.typesafe.play" %% "play-test" % PlayVersion.current,
        "uk.gov.hmrc" %% "reactivemongo-test" % "1.5.0",
        "uk.gov.hmrc" %% "auth-test" % "2.2.0"
      ).map(_  % scope)
    }.test
  }

  def apply() = compile ++ Test() ++ IntegrationTest()
}

