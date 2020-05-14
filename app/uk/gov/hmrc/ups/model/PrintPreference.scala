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

package uk.gov.hmrc.ups.model

import play.api.libs.json.Json

case class UpdatedPrintPreferences(pages: Long, next: Option[String], updates: List[PrintPreference])

object UpdatedPrintPreferences {

  val formats = {
    implicit val ppf = PrintPreference.formats
    Json.format[UpdatedPrintPreferences]
  }
}

case class PrintPreference(id: String, idType: String, formIds: List[String]) {
  def convertIdType: PrintPreference = idType match {
    case "sautr" => this.copy(idType = "utr")
    case _       => this
  }
}

object PrintPreference {
  implicit val formats = Json.format[PrintPreference]
}
