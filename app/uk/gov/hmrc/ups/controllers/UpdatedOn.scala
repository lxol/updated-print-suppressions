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

package uk.gov.hmrc.ups.controllers

import play.api.Logger
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{QueryStringBindable, Result}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.ups.model.{Limit, PastLocalDate, UpdatedPrintPreferences}
import uk.gov.hmrc.ups.repository.{MongoCounterRepository, UpdatedPrintSuppressionsRepository}

import scala.concurrent.{ExecutionContext, Future}
import scala.math.BigDecimal.RoundingMode
import play.api.mvc.Results._

trait UpdatedOn {

  val reactiveMongoComponent: ReactiveMongoComponent
  val counterRepository: MongoCounterRepository
  val localDateBinder: QueryStringBindable[PastLocalDate]
  implicit val executionContext: ExecutionContext
  implicit val uppf: OFormat[UpdatedPrintPreferences] = UpdatedPrintPreferences.formats

  protected def processUpdatedOn(optOffset: Option[Int], optLimit: Option[Limit], maybeUpdatedOn: Option[Either[String,PastLocalDate]]): Future[Result] = {
    maybeUpdatedOn match {
      case Some(Right(updatedOn)) =>
        val upsRepository = new UpdatedPrintSuppressionsRepository(reactiveMongoComponent, updatedOn.value, counterRepository)
        val limit = optLimit.getOrElse(Limit.max)
        val offset = optOffset.getOrElse(1)
        for {
          count <- upsRepository.count
          updates <- upsRepository.find(offset, limit.value)
        } yield {
          val pages: Int = (BigDecimal(count) / BigDecimal(limit.value)).setScale(0, RoundingMode.UP).intValue()
          Ok(
            Json.toJson(
              UpdatedPrintPreferences(
                pages = pages,
                next = nextPageURL(updatedOn, limit, count, offset),
                updates = updates.map(_.convertIdType)
              )
            )
          )
        }
      case None => throw new BadRequestException("updated-on is a mandatory parameter")
      case Some(Left(message)) => throw new BadRequestException(message)
    }
  }

  private def nextPageURL(updatedOn: PastLocalDate, limit: Limit, count: Int, offset: Int): Option[String] = {
    if (count > offset + limit.value) {
      Some(routes.UpdatedPrintSuppressionsController.list(
        offset = Some(offset + limit.value),
        limit = Some(limit)
      ).url + s"&${localDateBinder.unbind("updated-on", updatedOn)}")
    } else {
      None
    }
  }

}
