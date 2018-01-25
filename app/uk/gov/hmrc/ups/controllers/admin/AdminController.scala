/*
 * Copyright 2018 HM Revenue & Customs
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


import org.joda.time.LocalDate
import play.api.mvc.{Action, QueryStringBindable}
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.controllers.UpdatedPrintSuppressionsController
import uk.gov.hmrc.ups.controllers.bind.PastLocalDateBindable
import uk.gov.hmrc.ups.model.{PastLocalDate, PrintPreference}
import uk.gov.hmrc.ups.repository.{MongoCounterRepository, UpdatedPrintSuppressionsRepository}
import uk.gov.hmrc.ups.scheduled.PreferencesProcessor

trait AdminController extends UpdatedPrintSuppressionsController {

  implicit val ppf = PrintPreference.formats

  val dtf = org.joda.time.format.DateTimeFormat.forPattern("yyyy-MM-dd")

  def insert(date: String) = Action.async(parse.json) {
    implicit request =>
      withJsonBody[PrintPreference] { body =>
        new UpdatedPrintSuppressionsRepository(
          LocalDate.parse(date, dtf),
            MongoCounterRepository()
          ).
          insert(body, DateTimeUtils.now).
          map { _ => Ok("Record inserted") }.
          recover { case _ => InternalServerError("Failed to insert the record") }
      }
  }

  def processPrintSuppressions() = Action.async {
    implicit request =>
      PreferencesProcessor.run(HeaderCarrier()).map { totals =>
        Ok(
          s"UpdatedPrintSuppressions: ${totals.processed} item(s) processed with ${totals.failed} failure(s)"
        )
      }
  }
}

object NoPastDateValidationBindable extends PastLocalDateBindable {
  override val shouldValidatePastDate = false
}

object AdminController extends AdminController {
  override def localDateBinder: QueryStringBindable[PastLocalDate] = NoPastDateValidationBindable
}
