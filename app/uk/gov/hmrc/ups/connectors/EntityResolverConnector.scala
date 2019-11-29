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
import play.api.http.Status._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.ups.model.{Entity, EntityId}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EntityResolverConnector @Inject()(httpClient: HttpClient, servicesConfig: ServicesConfig)(implicit ec: ExecutionContext) {

  def getTaxIdentifiers(entityId: EntityId)(implicit hc: HeaderCarrier): Future[Either[Int,Option[Entity]]] = {
    httpClient.GET[HttpResponse](s"$serviceUrl/entity-resolver/$entityId").map { response =>
      response.status match {
        case NOT_FOUND => Right(None)
        case OK => Right(Some(response.json.as[Entity]))
        case _ => Left(response.status)
      }
    }
  }

  def serviceUrl: String = servicesConfig.baseUrl("entity-resolver")

}