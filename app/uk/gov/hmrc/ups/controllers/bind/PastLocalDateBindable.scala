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

package uk.gov.hmrc.ups.controllers.bind

import org.joda.time.LocalDate
import play.api.Logger
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.time.DateConverter
import uk.gov.hmrc.ups.model.PastLocalDate

import scala.util.Try

case class PastLocalDateBindable(shouldValidatePastDate: Boolean) extends QueryStringBindable[PastLocalDate] {

  def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, PastLocalDate]] =
    params.get(key).flatMap(_.headOption).map { date: String =>
      Try {
        DateConverter.parseToLocalDate(date) match {
          case aDate if !shouldValidatePastDate                                 => Right(PastLocalDate(aDate))
          case aDate if shouldValidatePastDate && aDate.isBefore(LocalDate.now) => Right(PastLocalDate(aDate))
          case _                                                                => Left("updated-on parameter can only be used with dates in the past")
        }
      } recover {
        case e: Exception => Left("updated-on parameter is in the wrong format. Should be (yyyy-MM-dd)")
      } get
    }

  def unbind(key: String, date: PastLocalDate): String =
    QueryStringBindable.bindableString.unbind(key, DateConverter.formatToString(date.value))
}
