package uk.gov.hmrc.jobs

import org.joda.time.LocalDate
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.Eventually

import play.api.http.Status._
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.json.Json._
import play.api.libs.ws.{WS, WSRequestHolder}

import reactivemongo.json.collection.JSONCollection
import reactivemongo.api.ReadPreference
import reactivemongo.json.ImplicitBSONHandlers._

import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressions._
import uk.gov.hmrc.ups.utils.Generate
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.it.{ExternalService, MicroServiceEmbeddedServer, ServiceSpec}
import uk.gov.hmrc.ups.config.Jobs
import uk.gov.hmrc.ups.model.EntityId
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressions
import com.github.tomakehurst.wiremock.client.WireMock._

import scala.concurrent.ExecutionContext.Implicits.global

class UpdatedPrintSuppressionJobISpec extends EndpointSupport
  with UpdatedPrintSuppressionServer
  with AssertResultInPreferencesDatabase
  with Eventually
  with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(mongo().drop())
  }

  "UpdatedPrintSuppression job" should {
    "only store records for which there is a preference associated with a valid utr" in new TestCase {

      val entityIdForUtr  = createValidRecord
      val orphanedEntity  = createOrphanPreference
      val entityIdForNino = createNinoOnlyRecord

      await(Jobs.UpdatedPrintSuppressionJob.executeInMutex)

      val upsCollection = mongo().collection[JSONCollection](
        UpdatedPrintSuppressions.repoNameTemplate(LocalDate.now)
      )

      await(
        for {
          utrRecords  <- upsCollection.find(upsSelector(utr.value)).
            cursor[UpdatedPrintSuppressions](ReadPreference.primaryPreferred).
            collect[List]()
          ninoRecords <- upsCollection.find(upsSelector(nino.value)).
            cursor[UpdatedPrintSuppressions](ReadPreference.primaryPreferred).
            collect[List]()
        } yield (utrRecords.size, ninoRecords.size)
      ) should be((1, 0))

      await(
        preferencesConnector.db().
          collection[JSONCollection]("saIndividualPreferences").
          find(
            Json.obj("$or" -> Seq(
              Json.obj("entityId" -> entityIdForUtr),
              Json.obj("entityId" -> entityIdForNino))
            )
          ).
          cursor[JsObject](ReadPreference.primaryPreferred).
          collect[List]()
      ).map { json =>
        (json \ "ups" \ "status").as[String]
      } shouldBe List("succeeded", "succeeded")
    }

  }


  trait PreferencesStub {

    def stubUpdatedPrintSuppression() = {
      stubFor(post(urlMatching("/preferences/updated-print-suppression/pull-work-item"))
        .withRequestBody(matching(
          """
            |{
            |  filters: {
            |    failedBefore: "2114-12-24T01:01:01.000Z",
            |    receivedBefore: "2114-12-24T01:01:01.000Z"
            |  }
            |}
          """.stripMargin))
          .whenScenarioStateIs("TODO")
           .willReturn(aResponse()
          .withStatus(200)
          .withBody(
            """
              |{
              |  "entityId" : "bbc14eef-97d3-435e-975a-f2ab069af000",
              |  "paperless" : true,
              |  "updatedAt" : "2014-10-08T13:10:50.122Z",
              |  "callbackUrl" : "/preferences/updated-print-suppression/543537da4d00004d00af5f9f/status"
              |}
            | """.
              stripMargin)))
    }
  }



  trait TestCase {
    val utr = Generate.utr
    val nino = Generate.nino
    val testEmail = "test@test.com"

    def createValidRecord(): EntityId = {
      implicit val authHeader: (String, String) = createGGAuthorisationHeader(utr, Generate.nino)

      doPost(`/preferences/terms-and-conditions`, Json.parse(s"""{"generic":{"accepted":true}, "email": "$testEmail"}""")).status should be(CREATED)

      val entityIdResponse = doGet(`/entity-resolver-admin/sa/:utr`(utr))
      val entityId: EntityId = EntityId(entityIdResponse.body)
      val verificationToken = verificationTokenFor(entityId)
      put(`/portal/preferences/email`, Json.parse( s"""{"token":"$verificationToken"}""")).status should be(NO_CONTENT)
      entityId
    }

    def createOrphanPreference(): EntityId = {
      implicit val authHeader: (String, String) = createGGAuthorisationHeader(Generate.utr, Generate.nino)

      val entityId = Generate.entityId
      doPost(`/preferences/:entityId/terms-and-conditions`(entityId), Json.parse(s"""{"generic":{"accepted":true}, "email": "$testEmail"}""")).status should be(CREATED)
      entityId
    }

    def createNinoOnlyRecord(): EntityId = {
      implicit val authHeader: (String, String) = createGGAuthorisationHeader(nino)

      doPost(
        `/preferences/terms-and-conditions`,
        Json.parse(s"""{"generic":{"accepted":true}, "email": "$testEmail"}""")
      ).status should be(CREATED)

      val entityIdResponse = doGet(`/entity-resolver-admin/paye/:nino`(nino))
      val entityId: EntityId = EntityId(entityIdResponse.body)
      val verificationToken = verificationTokenFor(entityId)
      put(
        `/portal/preferences/email`,
        Json.parse( s"""{"token":"$verificationToken"}""")
      ).status should be(NO_CONTENT)
      entityId
    }

    def upsSelector(id: String) = Json.obj("printPreference.id" -> id)
  }

}

import scala.concurrent.duration._

trait AssertResultInPreferencesDatabase extends BeforeAndAfterAll {
  self: UpdatedPrintSuppressionServer =>

  lazy val preferencesConnector = MongoConnector("mongodb://127.0.0.1:27017/preferences")

  override def afterAll() = {
    super.afterAll()
    preferencesConnector.close()
  }

}

trait UpdatedPrintSuppressionServer extends ServiceSpec with MongoSpecSupport {

  protected val server = new UpdatedPrintSuppressionIntegrationServer("UpdatedPrintSuppressionServer")

  class UpdatedPrintSuppressionIntegrationServer(override val testName: String) extends MicroServiceEmbeddedServer {
    protected lazy val externalServices: Seq[ExternalService] = externalServiceNames.map(ExternalService.runFromJar(_))

    override protected def additionalConfig = {
      Map(
        "Dev.auditing.consumer.baseUri.port" -> externalServicePorts("datastream"),
        s"mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
        "Dev.scheduling.updatedPrintSuppressions.initialDelay" -> "10 days",
        "Dev.scheduling.updatedPrintSuppressions.interval" -> "24 hours",
        "Dev.ups.retryFailedUpdatesAfter" -> "24 hours"
      )
    }

    override protected def startTimeout: Duration = 300.seconds
  }

  def externalServiceNames: Seq[String] = {
    Seq(
      "auth",
      "datastream",
      "entity-resolver",
      "preferences"
    )
  }

}


trait EndpointSupport {
  self: UpdatedPrintSuppressionServer =>

  implicit val hc = HeaderCarrier()

  import play.api.Play.current

  def authResource(path: String) = server.externalResource("auth", path)

  private lazy val ggAuthorisationHeader =
    AuthorisationProvider.forGovernmentGateway(authResource)

  def createGGAuthorisationHeader(utr: SaUtr): (String, String) =
    ggAuthorisationHeader.create(utr).futureValue

  def createGGAuthorisationHeader(nino: Nino): (String, String) =
    ggAuthorisationHeader.create(nino).futureValue

  def createGGAuthorisationHeader(utr: SaUtr, nino: Nino): (String, String) =
    ggAuthorisationHeader.create(utr, nino).futureValue

  def preferencesResource(path: String) =
    server.externalResource("preferences", path)

  def entityResource(path: String) =
    server.externalResource("entity-resolver", path)

  protected def mkResource(url: String, headers: Option[(String, String)] = None) =
    headers.fold(WS.url(url))(WS.url(url).withHeaders(_))


  def `/preferences/terms-and-conditions`(implicit authHeader: (String, String)) =
    mkResource(entityResource("/preferences/terms-and-conditions"), Some(authHeader))

  def `/preferences/:entityId/terms-and-conditions`(entityId: EntityId)(implicit authHeader: (String, String)) =
    mkResource(preferencesResource(s"/preferences/$entityId/terms-and-conditions"), Some(authHeader))

  def `/preferences/activate`(implicit authHeader: (String, String)) =
    mkResource(entityResource("/preferences/activate"), Some(authHeader)).
      withQueryString("returnUrl" -> "/test/url", "returnLinkText" -> "test")

  def `/portal/preferences/email` =
    mkResource(entityResource("/portal/preferences/email"))

  def `/entity-resolver-admin/sa/:utr`(utr: SaUtr) =
    mkResource(entityResource(s"/entity-resolver-admin/sa/$utr"))

  def `/entity-resolver-admin/paye/:nino`(nino: Nino) =
    mkResource(entityResource(s"/entity-resolver-admin/paye/$nino"))

  def `/preferences-admin/:entityId/verification-token`(entityId: EntityId) =
    WS.url(preferencesResource(s"/preferences-admin/$entityId/verification-token"))

  def `/updated-print-suppression/pull-work-item` =
    WS.url(preferencesResource("/updated-print-suppression/pull-work-item"))


  def doPost(url: => WSRequestHolder, body: JsValue) = url.post(body).futureValue

  def put(url: => WSRequestHolder, body: JsValue) = url.put(body).futureValue

  def doGet(url: => WSRequestHolder) = url.get().futureValue

  def verificationTokenFor(entityId: EntityId) = {
    val tokenResponse = doGet(`/preferences-admin/:entityId/verification-token`(entityId))
    tokenResponse.status should be(200)
    tokenResponse.body
  }

}
