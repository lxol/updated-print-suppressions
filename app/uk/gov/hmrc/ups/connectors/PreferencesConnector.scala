package uk.gov.hmrc.ups.connectors

import org.joda.time.{Duration, DateTime}
import play.api.http.Status._
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.model.{EntityId, Filters, PulledItem, WorkItemRequest}

import scala.concurrent.Future


trait PreferencesConnector {
  def changeStatus(entityId: EntityId, callBackUrl: String, status: String): Future[Boolean] = ???

  implicit object optionalPullItemReads extends HttpReads[Int Either Option[PulledItem]] {
    override def read(method: String, url: String, response: HttpResponse): Int Either Option[PulledItem] = response.status match {
      case NOT_FOUND => Right(None)
      case OK => Right(Some(response.json.as[PulledItem]))
      case _ => Left(response.status)
    }
  }

  def pullWorkItem()(implicit hc: HeaderCarrier): Future[Int Either Option[PulledItem]] =
    http.POST[WorkItemRequest, Int Either Option[PulledItem]](s"$serviceUrl/updated-print-suppression/pull-work-item", workItemRequest)

  def retryFailedUpdatesAfter: Duration


  def dateTimeFor(duration: Duration): DateTime = {
    DateTimeUtils.now.minus(duration)
  }


  def workItemRequest =  WorkItemRequest(Filters(dateTimeFor(retryFailedUpdatesAfter), DateTimeUtils.now))

  def http: HttpPost

  def serviceUrl: String
}
