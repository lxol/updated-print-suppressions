import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport._
import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.ServiceManagerPlugin.Keys.itDependenciesList
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.{ExternalService, SbtArtifactory, ServiceManagerPlugin}


val appName = "updated-print-suppressions"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory, SbtDistributablesPlugin)
  .settings(majorVersion := 3 )
  .settings(publishingSettings: _*)
  .settings(scalaSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(
    resolvers ++= Seq(
      Resolver.jcenterRepo,
      Resolver.bintrayRepo("hmrc","releases"),
      Resolver.bintrayRepo("jetbrains", "markdown"),
      "bintray-djspiewak-maven" at "https://dl.bintray.com/djspiewak/maven"
    )
  )
  .settings(
    targetJvm := "jvm-1.8",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    dependencyOverrides ++= AppDependencies.overrides,
    parallelExecution in Test := false,
    fork in Test := false,
    scalaVersion := "2.11.11",
    retrieveManaged := true,
    scalacOptions ++= List(
      "-feature",
      "-Xlint"),
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction( warnScalaVersionEviction= false)
  )
  .settings(RoutesKeys.routesImport ++= Seq("uk.gov.hmrc.ups.model._"))
  .settings(ServiceManagerPlugin.serviceManagerSettings)
  .settings(itDependenciesList := List(
      ExternalService("AUTH"),
      ExternalService("IDENTITY_VERIFICATION"),
      ExternalService("USER_DETAILS")
  ))
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest) (base => Seq(base / "it")).value,
    addTestReportOption(IntegrationTest, "int-test-reports"),
    parallelExecution in IntegrationTest := false,
    routesGenerator := InjectedRoutesGenerator,
    inConfig(IntegrationTest)(
      scalafmtCoreSettings ++
        Seq(
          compileInputs in compile := Def.taskDyn {
            val task = test in (resolvedScoped.value.scope in scalafmt.key)
            val previousInputs = (compileInputs in compile).value
            task.map(_ => previousInputs)
          }.value
        )
    )
  )

