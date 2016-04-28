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
      when(httpMock.POST[WorkItemRequest, Int Either Option[PulledItem]](argEq(pullWorkItemUrl), argEq(sampleWorkItemRequest), any())(any(), any(), argEq(hc))).thenReturn(Future.successful(Right(Some(pulledItem))))

      val result = connector.pullWorkItem()
      result.futureValue should be(Right(Some(pulledItem)))

      verify(httpMock).POST(argEq(pullWorkItemUrl), argEq(sampleWorkItemRequest), any())(any(), any(), argEq(hc))
    }
  }

  trait TestCase {
    implicit val hc = HeaderCarrier()
    val httpMock = mock[HttpPost]
    val connector = new PreferencesConnector {
      val http = httpMock

      def retryFailedUpdatesAfter = new Duration(1000)

      def serviceUrl: String = "serviceUrl"

      override def workItemRequest = sampleWorkItemRequest
    }

    val randomEntityId = Generate.entityId

    val pullWorkItemUrl: String = "serviceUrl/updated-print-suppression/pull-work-item"

    lazy val sampleWorkItemRequest = WorkItemRequest(Filters(failedBefore = DateTimeUtils.now, availableBefore = DateTimeUtils.now))
  }

}
