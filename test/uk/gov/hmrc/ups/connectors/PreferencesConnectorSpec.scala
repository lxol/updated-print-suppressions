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

import org.joda.time.Duration
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpPost, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.model.{Filters, PulledItem, WorkItemRequest}
import uk.gov.hmrc.ups.utils.Generate

class PreferencesConnectorSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  implicit val hc = HeaderCarrier()

  "pull next work item from preferences" should {
    "return the next work item that was at todo status" in new TestCase {
      val pulledItem = PulledItem(randomEntityId, true, DateTimeUtils.now, "someUrl")
      when(connector.httpWrapper.postF[PulledItem](any())).thenReturn(HttpResponse(Status.OK, Some(Json.toJson(pulledItem))))

      connector.pullWorkItem().futureValue should be(Right(Some(pulledItem)))

      verify(connector.httpWrapper).postF[PulledItem](any())
    }

    "return None if there no work item" in new TestCase {
      when(connector.httpWrapper.postF[PulledItem](any())).thenReturn(HttpResponse(Status.NO_CONTENT, None))

      connector.pullWorkItem().futureValue should be(Right(None))

      verify(connector.httpWrapper).postF[PulledItem](any())
    }

    "handle unexpected response from preferences" in new TestCase {
      val expectedStatus: Int = Status.NOT_FOUND
      when(connector.httpWrapper.postF[PulledItem](any())).thenReturn(HttpResponse(expectedStatus, None))

      connector.pullWorkItem().futureValue should be(Left(expectedStatus))

      verify(connector.httpWrapper).postF[PulledItem](any())
    }
  }

  "change status" should {
    "return OK if the succeeded status is updated successfully" in new TestCase {
      val callbackUrl = "serviceUrl/updated-print-suppression/entityId/status"

      when(connector.httpWrapper.postF[JsValue](any())).thenReturn(HttpResponse(Status.OK, None))

      connector.changeStatus(callbackUrl, "succeeded").futureValue should be(Status.OK)

      verify(connector.httpWrapper).postF[JsValue](any())
    }
  }

  trait TestCase {
    val httpMock = mock[HttpPost]
    val connector = new HttpPreferencesConnector
    val randomEntityId = Generate.entityId
  }
}

class HttpPreferencesConnector extends PreferencesConnector with MockHttpPost {

  override def serviceUrl: String = "preferences-url"

  def retryFailedUpdatesAfter: Duration = new Duration(1000)
}

