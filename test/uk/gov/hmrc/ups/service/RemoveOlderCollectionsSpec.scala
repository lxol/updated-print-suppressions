package uk.gov.hmrc.ups.service

import org.joda.time.LocalDate
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressions

import scala.concurrent.Future
import scala.concurrent.duration._

class RemoveOlderCollectionsSpec extends UnitSpec with ScalaFutures {
  val now = LocalDate.now()

  "remove older collections" should {
    "remove collection older than n days" in new SetUp {
      service.removeOlderThan(expirationPeriod).futureValue shouldBe 3
    }

    trait SetUp {
      val expirationPeriod = 2 days

      val service = RemoveOlderCollections(
        () => Future.successful((0 to 4).map { increment =>
          UpdatedPrintSuppressions.repoNameTemplate(now.minusDays(increment))
        }.toList),
        (value: String) => Future.successful(
          if ((2 to 4).map { x => UpdatedPrintSuppressions.repoNameTemplate(now.minusDays(x)) }.contains(value)) 1
          else 0
        )
      )


    }
  }
}
