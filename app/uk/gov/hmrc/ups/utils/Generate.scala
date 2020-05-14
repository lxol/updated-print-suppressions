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

package uk.gov.hmrc.ups.utils

import java.util.UUID

import uk.gov.hmrc.domain.{ Nino, SaUtr }
import uk.gov.hmrc.ups.model.EntityId

import scala.util.Random

object Generate {
  private val random = new Random()

  def nino = Nino(f"CE${random.nextInt(100000)}%06dD")
  def utr = SaUtr(UUID.randomUUID.toString)
  def entityId =
    EntityId(UUID.randomUUID.toString)
}
