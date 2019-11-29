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

package uk.gov.hmrc.ups.utils

import javax.inject.Inject
import play.api.Configuration
import play.api.http.Status._
import play.api.libs.json.Json.toJson
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent
import uk.gov.hmrc.play.bootstrap.http.{ErrorResponse, JsonErrorHandler}

import scala.concurrent.{ExecutionContext, Future}

class ErrorHandler @Inject()(
                              auditConnector: AuditConnector,
                              httpAuditEvent: HttpAuditEvent,
                              configuration: Configuration)(implicit ec: ExecutionContext) extends JsonErrorHandler(auditConnector,httpAuditEvent,configuration) {

  import httpAuditEvent.dataEvent

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    statusCode match {
      case BAD_REQUEST =>
        auditConnector.sendEvent(
          dataEvent(
            eventType = "ServerValidationError",
            transactionName = "Request bad format exception",
            request = request,
            detail = Map.empty
          )
        )
        Future.successful(BadRequest(toJson(ErrorResponse(BAD_REQUEST, message))))
      case _ => super.onClientError(request, statusCode, message)
    }
  }

}

