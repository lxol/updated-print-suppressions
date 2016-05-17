package uk.gov.hmrc.ups.controllers.admin

import play.api.libs.json.Json
import play.api.libs.ws.WS
import uk.gov.hmrc.ups.controllers.{TestServer, TestSetup}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressions

class AdminControllerISpec extends UnitSpec with TestServer {

  import play.api.Play.current

  override protected def additionalConfig: Map[String, Any] = super.additionalConfig ++ Map("application.router" -> "testOnlyDoNotUseInAppConf.Routes")


  "AdminController" should {

    "insert a new PrintPreference" in new TestSetup {
      private val preference = PrintPreference("someId", "someType", List("f1"))
      await(WS.url(resource("/preferences/sa/individual/print-suppression")).withQueryString("date" -> yesterdayAsString).post(Json.toJson(preference)))

      val all = repoYesterday.findAll()

      await(all).map { x => (x.counter, preference) } shouldBe List((1, preference))
    }
  }
}

