package uk.gov.hmrc.jobs

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WS, WSRequestHolder}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.it.{ExternalService, MicroServiceEmbeddedServer, ServiceSpec}
import uk.gov.hmrc.test.it.AuthorisationProvider
import uk.gov.hmrc.ups.config.Jobs
import uk.gov.hmrc.ups.model.EntityId
import uk.gov.hmrc.ups.utils.Generate

import scala.concurrent.ExecutionContext.Implicits.global

class UpdatedPrintSuppressionJobISpec extends EndpointSupport
  with UpdatedPrintSuppressionServer
  with Eventually
  with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(mongo().drop())
  }

  "UpdatedPrintSuppression job" should {
    """
       |copy valid updated preferences with a utr to the ups storage,
       |mark invalid preferences as permanently-failed,
       |and ignore nino only preferences
     """.stripMargin in new TestCase {

      createValidRecord

      await {
        val msg = Jobs.UpdatedPrintSuppressionJob.executeInMutex.futureValue.message
        msg
      } shouldBe s"UpdatedPrintSuppressions: 1 items processed with 0 failures"

    }

    """
      |pull workitem from preferences,
      |but not entity found for the entityId,
      |mark invalid preferences as permanently-failed,
      |and ignore nino only preferences
    """.stripMargin in new TestCase {

      createValidRecord
      createOrphanPreference
      createNinoOnlyRecord

      await {
        val msg = Jobs.UpdatedPrintSuppressionJob.executeInMutex.futureValue.message
        msg
      } shouldBe s"UpdatedPrintSuppressions: 3 items processed with 1 failures and 1 record without a UTR"


      // TODO: check the local repo only has 1 record
    }
      // create preference without a value in entity resolver
      // create preference with a valid nino in entity resolver


  }

  trait TestCase {
    val utr = Generate.utr
    val nino = Generate.nino
    val testEmail = "test@test.com"

    def createValidRecord() = {
      implicit val authHeader : (String, String) = createGGAuthorisationHeader(utr)

      `/preferences/activate`.put(Json.parse(s"""{"active": true}""")).futureValue.status shouldBe PRECONDITION_FAILED
      post(`/preferences/terms-and-conditions`, Json.parse(s"""{"generic":{"accepted":true}, "email": "$testEmail"}""")).status should be(CREATED)

      val entityIdResponse = get(`/entity-resolver-admin/sa/:utr`(utr))
      val entityId: EntityId = EntityId(entityIdResponse.body)
      val verificationToken = verificationTokenFor(entityId)
      put(`/portal/preferences/email`, Json.parse( s"""{"token":"$verificationToken"}""")).status should be(NO_CONTENT)
    }
    def createOrphanPreference() = {
      implicit val authHeader : (String, String) = createGGAuthorisationHeader(Generate.utr)

      `/preferences/activate`.put(Json.parse(s"""{"active": true}""")).futureValue.status shouldBe PRECONDITION_FAILED
      post(`/preferences/terms-and-conditions`, Json.parse(s"""{"generic":{"accepted":true}, "email": "$testEmail"}""")).status should be(CREATED)
    }
    def createNinoOnlyRecord() = {
      implicit val authHeader : (String, String) = createGGAuthorisationHeader(nino)

      `/preferences/activate`.put(Json.parse(s"""{"active": true}""")).futureValue.status shouldBe PRECONDITION_FAILED
      post(`/preferences/terms-and-conditions`, Json.parse(s"""{"generic":{"accepted":true}, "email": "$testEmail"}""")).status should be(CREATED)

      val entityIdResponse = get(`/entity-resolver-admin/paye/:nino`(nino))
      val entityId: EntityId = EntityId(entityIdResponse.body)
      val verificationToken = verificationTokenFor(entityId)
      put(`/portal/preferences/email`, Json.parse( s"""{"token":"$verificationToken"}""")).status should be(NO_CONTENT)
    }

  }
}

import scala.concurrent.duration._

trait UpdatedPrintSuppressionServer extends ServiceSpec with MongoSpecSupport {

  protected val server = new UpdatedPrintSuppressionIntegrationServer("UpdatedPrintSuppressionServer")

  class UpdatedPrintSuppressionIntegrationServer(override val testName: String) extends MicroServiceEmbeddedServer {
    protected lazy val externalServices: Seq[ExternalService] = externalServiceNames.map(ExternalService.runFromJar(_))

    override protected def additionalConfig = {
      println(s"databaseName ==> $databaseName")
      Map(
        "Dev.auditing.consumer.baseUri.port" -> externalServicePorts("datastream"),
        s"mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
        s"Dev.mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
        s"Test.mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
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

  private lazy val ggAuthorisationHeader = AuthorisationProvider.forGovernmentGateway(authResource)

  def createGGAuthorisationHeader(utr: SaUtr): (String, String) = ggAuthorisationHeader.create(utr).futureValue

  def createGGAuthorisationHeader(nino: Nino): (String, String) = ggAuthorisationHeader.create(nino).futureValue

  def createGGAuthorisationHeader(utr: SaUtr, nino: Nino): (String, String) = ggAuthorisationHeader.create(utr, nino).futureValue

  def preferencesResource(path: String) = server.externalResource("preferences", path)

  def entityResource(path: String) = server.externalResource("entity-resolver", path)

  protected def mkResource(url: String, headers: Option[(String, String)] = None) =
    headers.fold(WS.url(url))(WS.url(url).withHeaders(_))


  def `/preferences/terms-and-conditions`(implicit authHeader: (String, String)) = {
    mkResource(entityResource("/preferences/terms-and-conditions"), Some(authHeader))
  }

  def `/preferences/activate`(implicit authHeader: (String, String)) = mkResource(entityResource(s"/preferences/activate"), Some(authHeader)).withQueryString("returnUrl" -> "/test/url", "returnLinkText" -> "test")

  def `/portal/preferences/email` = mkResource(entityResource("/portal/preferences/email"))

  def `/entity-resolver-admin/sa/:utr`(utr: SaUtr) = mkResource(entityResource(s"/entity-resolver-admin/sa/$utr"))

  def `/entity-resolver-admin/paye/:nino`(nino: Nino) = mkResource(entityResource(s"/entity-resolver-admin/paye/$nino"))

  def `/preferences-admin/:entityId/verification-token`(entityId: EntityId) = WS.url(preferencesResource(s"/preferences-admin/$entityId/verification-token"))

  def `/updated-print-suppression/pull-work-item` = WS.url(preferencesResource("/updated-print-suppression/pull-work-item"))


  def post(url: => WSRequestHolder, body: JsValue) = url.post(body).futureValue

  def put(url: => WSRequestHolder, body: JsValue) = url.put(body).futureValue

  def get(url: => WSRequestHolder) = url.get().futureValue

  def verificationTokenFor(entityId: EntityId) = {
    val tokenResponse = get(`/preferences-admin/:entityId/verification-token`(entityId))
    tokenResponse.status should be(200)
    tokenResponse.body
  }

}
