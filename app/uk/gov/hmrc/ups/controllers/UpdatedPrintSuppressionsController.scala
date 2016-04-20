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

package uk.gov.hmrc.ups.controllers

import play.api.libs.json.Json
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoPlugin
import uk.gov.hmrc.play.http.BadRequestException
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.ups.model.{Limit, PastLocalDate, UpdatedPrintPreferences}
import uk.gov.hmrc.ups.pastLocalDateBinder
import uk.gov.hmrc.ups.repository.{MongoCounterRepository, UpdatedPrintSuppressionsRepository}

import scala.math.BigDecimal.RoundingMode

trait UpdatedPrintSuppressionsController extends BaseController {

  import play.api.Play.current

  implicit val mongo = ReactiveMongoPlugin.mongoConnector.db
  implicit val uppf = UpdatedPrintPreferences.formats

  def list(optOffset: Option[Int], optLimit: Option[Limit]): Action[AnyContent] =
    Action.async { implicit request =>
      pastLocalDateBinder.bind("updated-on", request.queryString) match {
        case Some(Right(updatedOn)) =>

          val repository = new UpdatedPrintSuppressionsRepository(updatedOn.value, counterName => new MongoCounterRepository(counterName))
          val limit = optLimit.getOrElse(Limit.max)
          val offset = optOffset.getOrElse(0)
          for {
            count <- repository.count
            updates <- repository
              .find(offset, limit.value)
          } yield {
            val pages: Int = (BigDecimal(count) / BigDecimal(limit.value)).setScale(0, RoundingMode.UP).intValue()
            Ok(Json.toJson(UpdatedPrintPreferences(
              pages = pages,
              next = nextPageURL(updatedOn, limit, count, offset),
              updates = updates))
            )
          }
        case None => throw new BadRequestException("updated-on is a mandatory parameter")
        case Some(Left(message)) => throw new BadRequestException(message)
      }
    }

  private def nextPageURL(updatedOn: PastLocalDate, limit: Limit, count: Int, offset: Int): Option[String] = {
    if (count > offset + limit.value)
      Some(routes.UpdatedPrintSuppressionsController.list(
        offset = Some(offset + limit.value),
        limit = Some(limit)
      ).url + s"&${pastLocalDateBinder.unbind("updated-on", updatedOn)}")
    else None
  }
}

object UpdatedPrintSuppressionsController extends UpdatedPrintSuppressionsController
