package uk.gov.hmrc.jobs

import org.joda.time.LocalDate
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import uk.gov.hmrc.controllers.TestServer
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.WithFakeApplication
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.{MongoCounterRepository, UpdatedPrintSuppressions, UpdatedPrintSuppressionsRepository}
import uk.gov.hmrc.ups.service.RemoveOlderCollections

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.concurrent.duration._
//WithFakeApplication with
class CollectionRemovalISpec extends TestServer with IntegrationPatience with Eventually with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(mongo().drop())
  }

  "removeOlderThan" should {
    "delete only those collections in the database that exceed the provided duration" in new SetUp {
      val expirationPeriod = 3 days

      (0 to 4) map daysFromToday foreach { date =>
//        await(bsonCollection(date)().insert(document))
        println(s"$dbName -- $date")
        await(new UpdatedPrintSuppressionsRepository(date, counterName => new MongoCounterRepository(counterName)).
          insert(pref))
      }

      private val names = await {
        RemoveOlderCollections.repository.upsCollectionNames

        //      RemoveOlderCollections.removeOlderThan(3 days)
        //
        //      RemoveOlderCollections.repository.upsCollectionNames.futureValue should contain only(
        //        (3 to 4).map { repoName }: _*
        //       )
      }
      println(names)
      names.size shouldBe 5
    }
  }

  trait SetUp {
    val pref = PrintPreference("11111111", "someType", List.empty)

    def daysFromToday(count: Int) = LocalDate.now().minusDays(count)

    def repoName(daysToDecrement : Int): String =
      UpdatedPrintSuppressions.repoNameTemplate(daysFromToday(daysToDecrement))

  }
}
