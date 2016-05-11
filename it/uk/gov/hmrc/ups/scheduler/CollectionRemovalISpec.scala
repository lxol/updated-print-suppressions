package uk.gov.hmrc.ups.scheduler

import org.joda.time.LocalDate
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import play.api.test.FakeApplication
import uk.gov.hmrc.ups.scheduled.RemoveOlderCollections
// DO NOT DELETE reactivemongo.json.ImplicitBSONHandlers._ even if your IDE tells you it is unnecessary
import reactivemongo.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.ups.config.Jobs
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressions

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CollectionRemovalISpec extends UnitSpec
    with WithFakeApplication
    with ScalaFutures
    with MongoSpecSupport
    with IntegrationPatience
    with Eventually
    with BeforeAndAfterEach {

  val expirationPeriod = 3

  override lazy val fakeApplication = FakeApplication(additionalConfiguration = Map(
    s"mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
    s"Test.removeOlderCollections.durationInDays" -> expirationPeriod,
    s"Test.scheduling.removeOlderCollections.initialDelay" -> "10 days",
    s"Test.scheduling.removeOlderCollections.interval" -> "24 hours"
  ))
  override def beforeEach(): Unit = {
    super.beforeEach()
    await(mongo().drop())
  }

  "removeOlderThan" should {
    "delete only those collections in the database that are older than the provided duration" in new SetUp {
      await {
        Future.sequence(
          (0 to 4).toList.map { days => bsonCollection(repoName(days))().insert(PrintPreference.formats.writes(pref)) }
        )
      }

      eventually {
        val msg = Jobs.RemoveOlderCollectionsJob.executeInMutex.futureValue.message
        (3 to 4).map { repoName } forall { msg.contains(_) } shouldBe true
        (0 to 2).map { x => msg.contains(repoName(x)) }.fold(false)( _ || _) shouldBe false
      }

      eventually {
        RemoveOlderCollections.repository.upsCollectionNames.futureValue should contain only ((0 to 2).map { repoName }: _*)
      }
    }
  }

  trait SetUp {
    val pref = PrintPreference("11111111", "someType", List.empty)

    def daysFromToday(count: Int) = LocalDate.now().minusDays(count)

    def repoName(daysToDecrement : Int): String =
      UpdatedPrintSuppressions.repoNameTemplate(daysFromToday(daysToDecrement))
  }
}
