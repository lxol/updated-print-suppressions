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
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.model.{Filters, PulledItem, WorkItemRequest}


trait PreferencesConnector {

  implicit val optionalPullItemReads = new HttpReads[Int Either Option[PulledItem]] {
    override def read(method: String, url: String, response: HttpResponse): Int Either Option[PulledItem] = response.status match {
      case NO_CONTENT => Right(None)
      case OK => Right(Some(response.json.as[PulledItem]))
      case _ => Left(response.status)
    }
  }

  implicit val statusReads = new HttpReads[Int] {
    def read(method: String, url: String, response: HttpResponse): Int = response.status
  }

  def pullWorkItem()(implicit hc: HeaderCarrier) = {
    http.POST[WorkItemRequest, Int Either Option[PulledItem]](s"$serviceUrl/updated-print-suppression/pull-work-item", workItemRequest)
  }

  def changeStatus(callbackUrl: String, status: String)(implicit hc: HeaderCarrier) =
    http.POST[JsValue, Int](callbackUrl, Json.obj("status" -> status))

  def retryFailedUpdatesAfter: Duration

  def dateTimeFor(duration: Duration): DateTime = DateTimeUtils.now.minus(duration)

  def workItemRequest: WorkItemRequest =  WorkItemRequest(Filters(dateTimeFor(retryFailedUpdatesAfter), DateTimeUtils.now))

  def http: HttpPost

  def serviceUrl: String

}