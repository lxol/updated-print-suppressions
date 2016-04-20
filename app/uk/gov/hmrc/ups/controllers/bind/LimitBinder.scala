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

package uk.gov.hmrc.ups.controllers.bind

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.ups.Limit

import scala.util.Try

trait LimitBinder extends QueryStringBindable[Limit] {
  val maximumRecordsPerPage: Int = 20000

  def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Limit]] = {
    params.get(key).flatMap(_.headOption).map { l: String => Try {
      l.toInt match {
        case limit if limit < 0 => Left("limit parameter is less than zero")
        case limit if limit > maximumRecordsPerPage => Left(s"limit parameter cannot be bigger than $maximumRecordsPerPage")
        case limit => Right(limit)
      }
    } recover {
      case e: Exception => Left("Cannot parse parameter limit as Int")
    } get
    }
  }

  def unbind(key: String, value: Limit): String = QueryStringBindable.bindableInt.unbind(key, value)
}
