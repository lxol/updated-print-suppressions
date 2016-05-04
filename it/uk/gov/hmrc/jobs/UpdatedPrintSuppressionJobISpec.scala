package uk.gov.hmrc.jobs

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WS, WSRequestHolder}
import play.api.test.FakeApplication
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.it.{ExternalService, MicroServiceEmbeddedServer, ServiceSpec}
import uk.gov.hmrc.play.test.WithFakeApplication
import uk.gov.hmrc.test.it.{AuthorisationProvider, AuthorisationHeader}
import uk.gov.hmrc.ups.config.Jobs
import uk.gov.hmrc.ups.model.EntityId
import uk.gov.hmrc.ups.utils.Generate

import scala.concurrent.ExecutionContext.Implicits.global

class UpdatedPrintSuppressionJobISpec extends EndpointSupport
  with UpdatedPrintSuppressionServer
  with WithFakeApplication
  with MongoSpecSupport
  with Eventually
  with BeforeAndAfterEach {

  // TODO: why is the environment defaulting to dev instead of Test here?
  override lazy val fakeApplication = FakeApplication(
    additionalConfiguration = Map(
      s"mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
      s"Dev.scheduling.updatedPrintSuppressions.initialDelay" -> "10 days",
      s"Dev.scheduling.updatedPrintSuppressions.interval" -> "24 hours"
    )
  )
  override def beforeEach(): Unit = {
    super.beforeEach()
    await(mongo().drop())
  }

  "UpdatedPrintSuppression job" should {
    s"""
       |copy valid updated preferences with a utr to the ups storage,
       |mark invalid preferences as permanently-failed,
       |and ignore nino only preferences
     """.stripMargin in new TestCaseWithUtrOnly {
      // create preference with a valid utr in entity resolver

      `/preferences/activate`.put(Json.parse(s"""{"active": true}""")).futureValue.status shouldBe PRECONDITION_FAILED
      post(`/preferences/terms-and-conditions`, Json.parse(s"""{"generic":{"accepted":true}, "email": "$testEmail"}""")).status should be(CREATED)

      val entityIdResponse = get(`/entity-resolver-admin/sa/:utr`(randomUtr))
      val verificationToken = verificationTokenFor(EntityId(entityIdResponse.body))
      put(`/portal/preferences/email`, Json.parse( s"""{"token":"$verificationToken"}""")).status should be(NO_CONTENT)

      eventually {
        val msg = Jobs.UpdatedPrintSuppressionJob.executeInMutex.futureValue.message
        msg shouldBe "processed: 1, successful: 1"
      }


      // create preference without a value in entity resolver
      // create preference with a valid nino in entity resolver
    }


  }

  trait TestCaseWithNinoOnly {
    val randomNino = Generate.nino
    implicit val authHeader : (String, String) = createGGAuthorisationHeader(randomNino)
  }

  trait TestCaseWithUtrOnly {
    val testEmail = "test@test.com"
    val randomUtr = Generate.utr
    implicit val authHeader : (String, String) = createGGAuthorisationHeader(randomUtr)
  }
}

import scala.concurrent.duration._

trait UpdatedPrintSuppressionServer extends ServiceSpec {

  protected val server = new UpdatedPrintSuppressionIntegrationServer("UpdatedPrintSuppressionServer")

  class UpdatedPrintSuppressionIntegrationServer(override val testName: String) extends MicroServiceEmbeddedServer {
    protected lazy val externalServices: Seq[ExternalService] = externalServiceNames.map(ExternalService.runFromJar(_))

    override protected def additionalConfig = Map(
      "application.router" -> "testOnlyDoNotUseInAppConf.Routes",
      "Dev.auditing.consumer.baseUri.port" -> externalServicePorts("datastream"),
      "Dev.microservice.services.preferences.circuitBreaker.numberOfCallsToTriggerStateChange" -> 5
    )

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
