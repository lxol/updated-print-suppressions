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

import play.api.{Configuration, Play}
import play.api.Mode.Mode
import play.api.http.Status._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.ups.config.WSHttp
import uk.gov.hmrc.ups.model.{Entity, EntityId}
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet, HttpReads, HttpResponse}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._


trait EntityResolverConnector {

  implicit object optionalEntityReads extends HttpReads[Int Either Option[Entity]] {
    override def read(method: String, url: String, response: HttpResponse): Int Either Option[Entity] = response.status match {
      case NOT_FOUND => Right(None)
      case OK => Right(Some(response.json.as[Entity]))
      case _ => Left(response.status)
    }
  }

  def getTaxIdentifiers(entityId: EntityId)(implicit hc: HeaderCarrier) =
    http.GET[Int Either Option[Entity]](s"$serviceUrl/entity-resolver/$entityId")


  def http: HttpGet

  def serviceUrl: String

}

object EntityResolverConnector extends EntityResolverConnector with ServicesConfig {
  lazy val http: HttpGet = WSHttp

  def serviceUrl: String = baseUrl("entity-resolver")

  override protected def mode: Mode = Play.current.mode

  override protected def runModeConfiguration: Configuration = Play.current.configuration
}
