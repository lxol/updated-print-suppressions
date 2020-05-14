/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.ups.controllers

import com.codahale.metrics.SharedMetricRegistries
import org.scalatest.{ BeforeAndAfterEach, WordSpec }
import org.scalatestplus.play.WsScalaTestClient
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{ WSClient, WSRequest }
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.integration.ServiceSpec
import uk.gov.hmrc.ups.repository.MongoCounterRepository

trait TestServer extends WordSpec with ServiceSpec with WsScalaTestClient with BeforeAndAfterEach with MongoSupport {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(s"mongodb.uri" -> s"mongodb://localhost:27017/$databaseName", "play.http.router" -> "testOnlyDoNotUseInAppConf.Routes", "metrics.jvm" -> false)
      .overrides(play.api.inject.bind[ReactiveMongoComponent].to(testMongoComponent))
      .build()

  implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

  val testCounterRepository = app.injector.instanceOf[MongoCounterRepository]

  override def beforeEach(): Unit =
    SharedMetricRegistries.clear()

  def preferencesSaIndividualPrintSuppression(updatedOn: Option[String], offset: Option[String], limit: Option[String], isAdmin: Boolean = false) = {

    val queryString = Seq(
      updatedOn.map(value => "updated-on" -> value),
      offset.map(value => "offset"        -> value),
      limit.map(value => "limit"          -> value)
    ).flatten

    if (isAdmin) {
      wsUrl("/test-only/preferences/sa/individual/print-suppression").withQueryStringParameters(queryString: _*)
    } else {
      wsUrl("/preferences/sa/individual/print-suppression").withQueryStringParameters(queryString: _*)
    }
  }

  def get(url: WSRequest) = url.get().futureValue

  override def externalServices: Seq[String] = Seq(
    "preferences"
  )

}
