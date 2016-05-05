/*
 * Copyright 2016 HM Revenue & Customs
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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.ups.model.Entity
import uk.gov.hmrc.ups.utils.Generate


class EntityResolverConnectorSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  implicit val hc = HeaderCarrier()

  "calling entity resolver get entity endpoint" should {
    "return an entity matching the given entityId" in new TestCase {
      when(connector.httpWrapper.getF[Entity](any())).thenReturn(HttpResponse(Status.OK, Some(Json.toJson(entity))))

      connector.getTaxIdentifiers(randomEntityId).futureValue should be (Right(Some(entity)))

      verify(connector.httpWrapper).getF[Entity](any())
    }

    "return None if no entry matching entityId" in new TestCase {
      when(connector.httpWrapper.getF[Entity](any())).thenReturn(HttpResponse(Status.NOT_FOUND, None))

      connector.getTaxIdentifiers(randomEntityId).futureValue should be (Right(None))

      verify(connector.httpWrapper).getF[Entity](any())
    }

    "handle unexpected response from preferences" in new TestCase {
      val expectedStatus: Int = Status.INTERNAL_SERVER_ERROR
      when(connector.httpWrapper.getF[Entity](any())).thenReturn(HttpResponse(expectedStatus, None))

      connector.getTaxIdentifiers(randomEntityId).futureValue should be (Left(expectedStatus))

      verify(connector.httpWrapper).getF[Entity](any())
    }
  }

  trait TestCase {

    val httpMock = mock[HttpGet]
    val connector = new HttpEntityResolverConnector

    val randomEntityId = Generate.entityId
    val randomUtr = Generate.utr
    val randomNino = Generate.nino

    val entity = Entity(randomEntityId, randomUtr, randomNino)
    val url = "serviceUrl/someUrl"
  }

}

class HttpEntityResolverConnector extends EntityResolverConnector with MockHttpGet {
  def serviceUrl = "baseUrl"
}
