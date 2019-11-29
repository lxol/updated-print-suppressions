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

package uk.gov.hmrc.ups.connectors

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.ups.model.{Entity, EntityId}
import uk.gov.hmrc.ups.utils.Generate

import scala.concurrent.{ExecutionContext, Future}

class EntityResolverConnectorSpec extends PlaySpec with MockitoSugar with ScalaFutures with IntegrationPatience
  with GuiceOneAppPerSuite with BeforeAndAfterEach {

  val mockHttpClient: HttpClient = mock[HttpClient]

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .overrides(bind[HttpClient].to(mockHttpClient)).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockHttpClient)
  }

  "calling entity resolver get entity endpoint" should {
    "return an entity matching the given entityId" in new TestCase {
      when(mockHttpClient.GET[HttpResponse](any())(any(),any(),any())).thenReturn(Future.successful(HttpResponse(Status.OK, Some(Json.toJson(entity)))))

      connector.getTaxIdentifiers(randomEntityId).futureValue must be (Right(Some(entity)))

      verify(mockHttpClient).GET[HttpResponse](any())(any(),any(),any())
    }

    "return None if no entry matching entityId" in new TestCase {
      when(mockHttpClient.GET[HttpResponse](any())(any(),any(),any())).thenReturn(Future.successful(HttpResponse(Status.NOT_FOUND)))

      connector.getTaxIdentifiers(randomEntityId).futureValue must be (Right(None))

      verify(mockHttpClient).GET[HttpResponse](any())(any(),any(),any())
    }

    "handle unexpected response from preferences" in new TestCase {
      val expectedStatus: Int = Status.INTERNAL_SERVER_ERROR
      when(mockHttpClient.GET[HttpResponse](any())(any(),any(),any())).thenReturn(Future.successful(HttpResponse(expectedStatus)))

      connector.getTaxIdentifiers(randomEntityId).futureValue must be (Left(expectedStatus))

      verify(mockHttpClient).GET[HttpResponse](any())(any(),any(),any())
    }
  }

  trait TestCase {
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val connector: EntityResolverConnector = app.injector.instanceOf[EntityResolverConnector]
    val randomEntityId: EntityId = Generate.entityId
    val randomUtr: SaUtr = Generate.utr
    val randomNino: Nino = Generate.nino
    val entity = Entity(randomEntityId, randomUtr, randomNino)
    val url = "serviceUrl/someUrl"

  }

}
