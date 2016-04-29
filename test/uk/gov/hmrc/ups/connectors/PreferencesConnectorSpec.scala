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
import org.mockito.Matchers.{eq => argEq, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpPost, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.model.{Filters, PulledItem, WorkItemRequest}
import uk.gov.hmrc.ups.utils.Generate

import scala.concurrent.Future


class PreferencesConnectorSpec extends UnitSpec with ScalaFutures with MockitoSugar {
  "pull next work item from preferences" should {
    "return the next work item that was at todo status" in new TestCase {
      val pulledItem = PulledItem(randomEntityId, true, DateTimeUtils.now, "someUrl")
      val httpResponse = HttpResponse(Status.OK, Some(Json.toJson(pulledItem)))
      when(httpMock.POST[WorkItemRequest, Int Either Option[PulledItem]](argEq(pullWorkItemUrl), argEq(sampleWorkItemRequest), any())(any(), any(), any())).thenReturn(Future.successful(Right(Some(pulledItem))))

      val result = connector.pullWorkItem()
      result.futureValue should be(Right(Some(pulledItem)))

      verify(httpMock).POST(argEq(pullWorkItemUrl), argEq(sampleWorkItemRequest), any())(any(), any(), any())
    }

    "return None if there no work item" in new TestCase {
      val httpResponse = HttpResponse(Status.NO_CONTENT, None)
      when(httpMock.POST[WorkItemRequest, Int Either Option[PulledItem]](argEq(pullWorkItemUrl), argEq(sampleWorkItemRequest), any())(any(), any(), any())).thenReturn(Future.successful(Right(None)))

      val result = connector.pullWorkItem()
      result.futureValue should be(Right(None))

      verify(httpMock).POST(argEq(pullWorkItemUrl), argEq(sampleWorkItemRequest), any())(any(), any(), any())
    }

    "handle unexpected response from preferences" in new TestCase {
      val expectedStatus: Int = Status.NOT_FOUND
      val httpResponse = HttpResponse(expectedStatus, None)
      when(httpMock.POST[WorkItemRequest, Int Either Option[PulledItem]](argEq(pullWorkItemUrl), argEq(sampleWorkItemRequest), any())(any(), any(), any())).thenReturn(Future.successful(Left(expectedStatus)))

      val result = connector.pullWorkItem()
      result.futureValue should be(Left(expectedStatus))

      verify(httpMock).POST(argEq(pullWorkItemUrl), argEq(sampleWorkItemRequest), any())(any(), any(), any())
    }
  }

  trait TestCase {
    implicit val hc = HeaderCarrier()
    val httpMock = mock[HttpPost]
    val connector = new PreferencesConnector {
      val http = httpMock

      def retryFailedUpdatesAfter = new Duration(1000)

      def serviceUrl: String = "serviceUrl"

      override val workItemRequest = sampleWorkItemRequest
    }

    val randomEntityId = Generate.entityId

    val pullWorkItemUrl: String = "serviceUrl/updated-print-suppression/pull-work-item"

    lazy val sampleWorkItemRequest = WorkItemRequest(Filters(failedBefore = DateTimeUtils.now, availableBefore = DateTimeUtils.now))
  }

}
