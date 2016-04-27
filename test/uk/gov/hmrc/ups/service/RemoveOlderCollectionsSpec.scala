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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class RemoveOlderCollectionsSpec extends UnitSpec with ScalaFutures {
  val now = LocalDate.now()

  "remove older collections" should {
    "remove collection older than n days" in {
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
      service.removeOlderThan(expirationPeriod).futureValue shouldBe (3)
    }
  }
}

class DeleteCollectionFilterSpec extends UnitSpec {
  "filter for UPS collection names" should {
    "return true if name matches updated_print_suppressions and is older than the expected duration" in new SetUp {
      filter.filterUpsCollectionsOnly(UpdatedPrintSuppressions.repoNameTemplate(now.minusDays(3)), expirationPeriod) shouldBe true
    }

    "return false if name matches updated_print_suppressions and is less than the expected duration" in new SetUp {
      filter.filterUpsCollectionsOnly(UpdatedPrintSuppressions.repoNameTemplate(now.minusDays(1)), expirationPeriod) shouldBe false
    }

    "throw exception if name does not match updated_print_suppressions" in new SetUp {
      an [Exception] should be thrownBy filter.filterUpsCollectionsOnly("randomCollectionName", expirationPeriod)
    }
  }

  trait SetUp {
    val expirationPeriod = 2 days

    val filter = new DeleteCollectionFilter{}
    val now = LocalDate.now()
  }
}
