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

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.ups.model.Limit

import scala.util.Try

trait LimitBinder extends QueryStringBindable[Limit] {

  def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Limit]] = {
    params.get(key).flatMap(_.headOption).map { l: String => Try {
      l.toInt match {
        case limit if limit < 0 => Left("limit parameter is less than zero")
        case limit if limit > Limit.max.value => Left(s"limit parameter cannot be bigger than ${Limit.max.value}")
        case limit => Right(Limit(limit))
      }
    } recover {
      case _: Exception => Left("Cannot parse parameter limit as Int")
    } get
    }
  }

  def unbind(key: String, limit: Limit): String = QueryStringBindable.bindableInt.unbind(key, limit.value)
}
