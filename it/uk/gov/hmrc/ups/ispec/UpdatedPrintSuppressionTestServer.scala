package uk.gov.hmrc.ups.ispec

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.joda.time.LocalDate
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.OneServerPerSuite
import play.api.test.FakeApplication
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.ups.repository.{MongoCounterRepository, UpdatedPrintSuppressions}

import scala.concurrent.ExecutionContext.Implicits.global

abstract class UpdatedPrintSuppressionTestServer(override val databaseName: String = "updated-print-suppression-ispec")
  extends UnitSpec
  with OneServerPerSuite
  with MongoSpecSupport
  with Eventually
  with BeforeAndAfterEach
  with PreferencesStub
  with EntityResolverStub
  with ScalaFutures
  with BeforeAndAfterAll {

  lazy val upsCollection = mongo().collection[JSONCollection](
    UpdatedPrintSuppressions.repoNameTemplate(LocalDate.now)
  )

  override lazy val port = 9000

  val stubPort = 11111
  val stubHost = "localhost"

  override lazy val app = FakeApplication(additionalConfiguration = Map[String, Any](
    "auditing.consumer.baseUri.host" -> stubHost,
    "auditing.consumer.baseUri.port" -> stubPort,
    "microservice.services.preferences.host" -> stubHost,
    "microservice.services.preferences.port" -> stubPort,
    "microservice.services.entity-resolver.host" -> stubHost,
    "microservice.services.entity-resolver.port" -> stubPort,
    "Test.scheduling.updatedPrintSuppressions.initialDelay" -> "10 hours",
    "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName"
  ))

  val wireMockServer: WireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  override def beforeAll() = {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterAll() = {
    wireMockServer.stop()
  }

  override def beforeEach(): Unit = {
    WireMock.reset()
    await(
      upsCollection.drop().flatMap { _ =>
        MongoCounterRepository().removeAll()
      }
    )
  }

}
