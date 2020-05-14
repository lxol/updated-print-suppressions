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

package uk.gov.hmrc.ups.ispec

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.joda.time.LocalDate
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.play.{ OneServerPerSuite, PlaySpec }
import play.api.{ Configuration, Mode }
import play.api.libs.json.{ JsObject, JsValue }
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.mongo.MongoSpecSupport
import play.api.test.Helpers._
import uk.gov.hmrc.ups.repository.{ MongoCounterRepository, UpdatedPrintSuppressions }
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.integration.ServiceSpec
import uk.gov.hmrc.play.bootstrap.config.{ RunMode, ServicesConfig }

import scala.concurrent.ExecutionContext.Implicits.global

abstract class UpdatedPrintSuppressionTestServer(override val databaseName: String = "updated-print-suppression-ispec")
    extends PlaySpec with ServiceSpec with MongoSpecSupport with Eventually with BeforeAndAfterEach with PreferencesStub with EntityResolverStub
    with ScalaFutures with BeforeAndAfterAll {

  lazy val upsCollection = mongo().collection[JSONCollection](
    UpdatedPrintSuppressions.repoNameTemplate(LocalDate.now)
  )

  private val mongoCounterRepository = app.injector.instanceOf[MongoCounterRepository]

  override lazy val port = 9000

  lazy val stubPort = 11111
  lazy val stubHost = "localhost"

  override def additionalConfig: Map[String, _] =
    Map[String, Any](
      "auditing.consumer.baseUri.host"                   -> stubHost,
      "auditing.consumer.baseUri.port"                   -> stubPort,
      "microservice.services.preferences.host"           -> stubHost,
      "microservice.services.preferences.port"           -> stubPort,
      "microservice.services.entity-resolver.host"       -> stubHost,
      "microservice.services.entity-resolver.port"       -> stubPort,
      "scheduling.updatedPrintSuppressions.initialDelay" -> "10 hours",
      "mongodb.uri"                                      -> s"mongodb://localhost:27017/$databaseName"
    )

  val wireMockServer: WireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  override def beforeAll(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterAll(): Unit =
    wireMockServer.stop()

  override def beforeEach(): Unit = {
    WireMock.reset()
    await(upsCollection.remove(JsObject(Map.empty[String, JsValue])))
    await(mongoCounterRepository.removeAll())
  }

}
