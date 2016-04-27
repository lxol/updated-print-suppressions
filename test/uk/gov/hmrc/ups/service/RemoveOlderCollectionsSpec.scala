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

package uk.gov.hmrc.ups.service

import org.joda.time.LocalDate
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressions

import scala.concurrent.Future
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

class RemoveOlderCollectionsSpec extends UnitSpec with ScalaFutures {
  val now = LocalDate.now()

  //TODO: re-look
  "remove older collections" should {
    "remove collection older than n days" in new SetUp {
      service.removeOlderThan(expirationPeriod).futureValue shouldBe (3)
    }

    trait SetUp {
      val expirationPeriod = 2 days

      val service = RemoveOlderCollections(
        () => Future.successful((0 to 4).map { increment =>
          UpdatedPrintSuppressions.repoNameTemplate(now.minusDays(increment))
        }.toList),
        (value: String) => Future.successful(
          if ((2 to 4).map { x => UpdatedPrintSuppressions.repoNameTemplate(now.minusDays(x)) }.contains(value)) true
          else false
        )
      )
    }
  }
}
