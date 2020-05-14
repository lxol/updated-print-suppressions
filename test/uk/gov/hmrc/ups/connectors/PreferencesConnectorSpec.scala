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

package uk.gov.hmrc.ups.connectors

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.model.{ EntityId, PulledItem, WorkItemRequest }
import uk.gov.hmrc.ups.utils.Generate
import uk.gov.hmrc.workitem.Succeeded

import scala.concurrent.{ ExecutionContext, Future }

class PreferencesConnectorSpec extends PlaySpec with ScalaFutures with MockitoSugar with GuiceOneAppPerSuite with MongoSpecSupport with BeforeAndAfterEach {

  val mockHttpClient: HttpClient = mock[HttpClient]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[HttpClient].to(mockHttpClient))
      .build()

  override def beforeEach(): Unit =
    reset(mockHttpClient)

  "pull next work item from preferences" should {
    "return the next work item that was at todo status" in new TestCase {
      val pulledItem = PulledItem(randomEntityId, paperless = true, DateTimeUtils.now, "someUrl")
      when(mockHttpClient.POST[WorkItemRequest, Option[PulledItem]](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(Some(pulledItem)))

      connector.pullWorkItem.futureValue must be(Some(pulledItem))

      verify(mockHttpClient).POST[WorkItemRequest, Option[PulledItem]](any(), any(), any())(any(), any(), any(), any())
    }

    "return None if there no work item" in new TestCase {
      when(mockHttpClient.POST[WorkItemRequest, Option[PulledItem]](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(None))

      connector.pullWorkItem.futureValue must be(None)

      verify(mockHttpClient).POST[WorkItemRequest, Option[PulledItem]](any(), any(), any())(any(), any(), any(), any())
    }

    "return none on an unexpected response from preferences" in new TestCase {
      val expectedStatus: Int = Status.NOT_FOUND
      when(mockHttpClient.POST[WorkItemRequest, Option[PulledItem]](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(None))

      connector.pullWorkItem.futureValue mustBe None

      verify(mockHttpClient).POST[WorkItemRequest, Option[PulledItem]](any(), any(), any())(any(), any(), any(), any())
    }
  }

  "change status" should {
    "return OK if the succeeded status is updated successfully" in new TestCase {
      val callbackUrl = "serviceUrl/updated-print-suppression/entityId/status"

      when(mockHttpClient.POST[JsValue, Int](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(Status.OK))

      connector.changeStatus(callbackUrl, Succeeded).futureValue must be(Status.OK)

      //verify(mockHttpClient).POST(any(),any(),any())
    }
  }

  trait TestCase {
    implicit val mockEc: ExecutionContext = mock[ExecutionContext]
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val connector: PreferencesConnector = app.injector.instanceOf[PreferencesConnector]
    val randomEntityId: EntityId = Generate.entityId
  }

}
