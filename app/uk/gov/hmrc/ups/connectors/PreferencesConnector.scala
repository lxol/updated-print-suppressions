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

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.http.Status._
import play.api.libs.json.JodaWrites.{JodaDateTimeWrites => _}
import play.api.libs.json.{Format, JodaReads, JodaWrites, JsResult, JsValue, Json}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.model.{Filters, PulledItem, WorkItemRequest}
import uk.gov.hmrc.workitem.ProcessingStatus

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PreferencesConnector @Inject()(httpClient: HttpClient, runMode: RunMode, configuration: Configuration, servicesConfig: ServicesConfig)
                                    (implicit ec: ExecutionContext) {

  implicit val dateFormatDefault: Format[DateTime] = new Format[DateTime] {
    override def reads(json: JsValue): JsResult[DateTime] = JodaReads.DefaultJodaDateTimeReads.reads(json)
    override def writes(o: DateTime): JsValue = JodaWrites.JodaDateTimeNumberWrites.writes(o)
  }

  implicit val optionalPullItemReads: HttpReads[Option[PulledItem]] = new HttpReads[Option[PulledItem]] {
    override def read(method: String, url: String, response: HttpResponse): Option[PulledItem] =
      response.status match {
        case NO_CONTENT => None
        case OK => Some(response.json.as[PulledItem])
        case unexpectedStatus =>
          Logger.error(s"Call to $url failed with status $unexpectedStatus")
          None
      }
  }

  implicit val statusReads: HttpReads[Int] = new HttpReads[Int] {
    def read(method: String, url: String, response: HttpResponse): Int = response.status
  }

  def pullWorkItem(implicit hc: HeaderCarrier): Future[Option[PulledItem]] = {
    httpClient.POST[WorkItemRequest, Option[PulledItem]](
      s"$serviceUrl/preferences/updated-print-suppression/pull-work-item",
      workItemRequest
    ).recover { case ex =>
      Logger.error(s"Call to $serviceUrl/preferences/updated-print-suppression/pull-work-item failed unexpectedly", ex)
      None
    }
  }

  def changeStatus(callbackUrl: String, status: ProcessingStatus)(implicit hc: HeaderCarrier): Future[Int] =
    httpClient.POST[JsValue, Int](s"$serviceUrl$callbackUrl", Json.obj("status" -> status.name))

  def dateTimeFor(duration: Duration): DateTime = DateTimeUtils.now.minus(duration.toMillis)

  def workItemRequest: WorkItemRequest =
    WorkItemRequest(Filters(dateTimeFor(retryFailedUpdatesAfter), DateTimeUtils.now))

  lazy val retryFailedUpdatesAfter: Duration = {
    configuration.getOptional[Duration](s"${runMode.env}.updatedPrintSuppressions.retryFailedUpdatesAfter").
      getOrElse(throw new IllegalStateException(s"${runMode.env}.updatedPrintSuppressions.retryFailedUpdatesAfter config value not set"))
  }

  lazy val serviceUrl: String = servicesConfig.baseUrl("preferences")

}
