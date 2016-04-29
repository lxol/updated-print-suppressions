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

import org.joda.time.{DateTime, Duration}
import play.api.http.Status._
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.ups.model.{EntityId, PulledItem, WorkItemRequest}

import scala.concurrent.Future


trait PreferencesConnector {
  def changeStatus(entityId: EntityId, callBackUrl: String, status: String): Future[Boolean] = ???

  implicit object optionalPullItemReads extends HttpReads[Int Either Option[PulledItem]] {
    override def read(method: String, url: String, response: HttpResponse): Int Either Option[PulledItem] = response.status match {
      case NO_CONTENT => Right(None)
      case OK => Right(Some(response.json.as[PulledItem]))
      case _ => Left(response.status)
    }
  }

  def pullWorkItem()(implicit hc: HeaderCarrier): Future[Int Either Option[PulledItem]] =
    http.POST[WorkItemRequest, Int Either Option[PulledItem]](s"$serviceUrl/updated-print-suppression/pull-work-item", workItemRequest)

  def retryFailedUpdatesAfter: Duration


  // Uncomment when IT tests fails
  def dateTimeFor(duration: Duration): DateTime = ???
  //{
//    DateTimeUtils.now.minus(duration)
//  }

  // WorkItemRequest(Filters(dateTimeFor(retryFailedUpdatesAfter), DateTimeUtils.now))
  def workItemRequest: WorkItemRequest =  ???

  def http: HttpPost

  def serviceUrl: String
}
