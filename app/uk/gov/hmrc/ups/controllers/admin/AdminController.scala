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

package uk.gov.hmrc.ups.controllers.admin


import javax.inject.{Inject, Singleton}
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormatter
import play.api.libs.json.{JsValue, OFormat}
import play.api.mvc.{Action, AnyContent, ControllerComponents, QueryStringBindable}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.{BackendController, WithJsonBody}
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.controllers.bind.PastLocalDateBindable
import uk.gov.hmrc.ups.controllers.{UpdatedOn, UpdatedPrintSuppressionsController}
import uk.gov.hmrc.ups.model.{Limit, PastLocalDate, PrintPreference}
import uk.gov.hmrc.ups.repository.{MongoCounterRepository, UpdatedPrintSuppressionsRepository}
import uk.gov.hmrc.ups.scheduled.PreferencesProcessor

import scala.concurrent.ExecutionContext

@Singleton
class AdminController @Inject()(mongoComponent: ReactiveMongoComponent, mongoCounterRepository: MongoCounterRepository, cc: ControllerComponents,
                                preferencesProcessor: PreferencesProcessor, upsController: UpdatedPrintSuppressionsController)
                               (implicit ec: ExecutionContext) extends BackendController(cc) with WithJsonBody with UpdatedOn {

  override val reactiveMongoComponent: ReactiveMongoComponent = mongoComponent
  override val counterRepository: MongoCounterRepository = mongoCounterRepository
  override val executionContext: ExecutionContext = ec
  override val localDateBinder: QueryStringBindable[PastLocalDate] = PastLocalDateBindable(false)

  implicit val ppf: OFormat[PrintPreference] = PrintPreference.formats

  val dtf: DateTimeFormatter = org.joda.time.format.DateTimeFormat.forPattern("yyyy-MM-dd")

  def list(optOffset: Option[Int], optLimit: Option[Limit]): Action[AnyContent] = {
    Action.async { implicit request =>
        processUpdatedOn(
        optOffset,
        optLimit,
        localDateBinder.bind("updated-on", request.queryString)
      )
    }
  }

  def insert(date: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PrintPreference] { body =>
        new UpdatedPrintSuppressionsRepository(
          mongoComponent,
          LocalDate.parse(date, dtf),
          mongoCounterRepository
          ).
          insert(body, DateTimeUtils.now).
          map { _ => Ok("Record inserted") }.
          recover { case _ => InternalServerError("Failed to insert the record") }
      }
  }

  def processPrintSuppressions(): Action[AnyContent] = Action.async {
    implicit request =>
      preferencesProcessor.run(HeaderCarrier()).map { totals =>
        Ok(
          s"UpdatedPrintSuppressions: ${totals.processed} item(s) processed with ${totals.failed} failure(s)"
        )
      }
  }

}