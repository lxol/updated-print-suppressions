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

package uk.gov.hmrc.ups.scheduled

import org.joda.time.LocalDate
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressions

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class RemoveOlderCollectionsSpec extends PlaySpec with ScalaFutures {

  "remove older collections" should {
    "remove collection older than n days" in new SetUp {
      private val expectedResults = (2 to 4).map { x =>
        repoName(x)
      }.toList

      private val totals =
        compose(listRepoNames, removeRepoColls(expectedResults, _), filterUpsCollectionsOnly(_, expirationPeriod)).futureValue

      totals.failures mustBe List.empty
      totals.successes.map { _.collectionName } must contain only (expectedResults: _*)
    }

    "perform no deletions when provided an empty list of names" in new SetUp {
      compose(
        () => Future.successful(List.empty[String]),
        value => Future { () },
        filterUpsCollectionsOnly(_, 0 days)
      ).futureValue mustBe Totals(List.empty, List.empty)
    }

    "removes collections independently, allowing for partial success" in new SetUp {
      val (failures, successes) =
        compose(listRepoNames, removeRepoColls(List(repoName(3)), _), filterUpsCollectionsOnly(_, expirationPeriod)).map { totals =>
          (totals.failures.map(_.collectionName), totals.successes.map(_.collectionName))
        }.futureValue

      failures must contain only (repoName(2), repoName(4))
      successes must contain only repoName(3)
    }

  }

  trait SetUp extends SelectAndRemove with FilterSetUp {
    def listRepoNames(): Future[List[String]] =
      Future.successful((0 to 4).map { increment =>
        repoName(increment)
      }.toList)

    def removeRepoColls(successfulNames: List[String], valueToCheck: String) =
      if (successfulNames.contains(valueToCheck)) Future { () } else
        Future.failed(new RuntimeException(s"unexpected value $valueToCheck"))
  }
}

class DeleteCollectionFilterSpec extends PlaySpec {
  "filter for UPS collection names" should {
    "return true if name matches updated_print_suppressions and is older than the expected duration" in new FilterSetUp {
      filterUpsCollectionsOnly(repoName(3), expirationPeriod) mustBe true
    }

    "return false if name matches updated_print_suppressions and is less than the expected duration" in new FilterSetUp {
      filterUpsCollectionsOnly(repoName(1), expirationPeriod) mustBe false
    }

    "throw exception if name does not match updated_print_suppressions" in new FilterSetUp {
      an[Exception] should be thrownBy filterUpsCollectionsOnly("randomCollectionName", expirationPeriod)
    }
  }
}
trait FilterSetUp extends DeleteCollectionFilter {
  val expirationPeriod: FiniteDuration = 2 days
  def repoName(daysToDecrement: Int): String =
    UpdatedPrintSuppressions.repoNameTemplate(LocalDate.now().minusDays(daysToDecrement))
}
