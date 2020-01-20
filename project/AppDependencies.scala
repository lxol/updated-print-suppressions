/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import play.core.PlayVersion
import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"             %% "bootstrap-play-26"      % "1.1.0",
    "uk.gov.hmrc"             %% "simple-reactivemongo"   % "7.20.0-play-26",
    "uk.gov.hmrc"             %% "play-scheduling"        % "7.1.0-play-26",
    "uk.gov.hmrc"             %% "domain"                 % "5.6.0-play-26",
    "uk.gov.hmrc"             %% "work-item-repo"         % "6.8.0-play-26",
    "net.codingwell"         %% "scala-guice"              % "4.2.6",
    "com.typesafe.play"       %% "play-json-joda"         % "2.6.14"
  )

  val test = Set(
    "uk.gov.hmrc"             %% "reactivemongo-test"       % "4.15.0-play-26"    % "test,it",
    "org.mockito"             %  "mockito-all"              % "1.9.5"             % "test,it",
    "com.github.tomakehurst"  % "wiremock-jre8"             % "2.25.1"            % "test,it",
    "org.scalatest"           %% "scalatest"                % "3.0.8"             % "test,it",
    "org.pegdown"             %  "pegdown"                  % "1.6.0"             % "test,it",
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "3.1.2"             % "test,it",
    "uk.gov.hmrc"             %% "service-integration-test" % "0.9.0-play-26"     % "test,it",
    "com.typesafe.play"       %% "play-test"                % PlayVersion.current % "test,it",
    "org.skyscreamer"         % "jsonassert"                % "1.5.0"             % "it"
  )

  val overrides = Set()

}
