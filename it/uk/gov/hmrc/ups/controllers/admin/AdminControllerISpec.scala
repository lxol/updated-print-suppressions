package uk.gov.hmrc.ups.controllers.admin

import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.WS
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.ups.controllers.{TestServer, TestSetup}
import uk.gov.hmrc.ups.model.PrintPreference

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

    "fetch a new PrintPreference created today using admin end-point" in new TestSetup {
      val ppOne = PrintPreference("11111111", "someType", List("f1", "f2"))
      await(repoToday.insert(ppOne, today.toDateTimeAtStartOfDay))

      val response = get(`/preferences/sa/individual/print-suppression`(Some(todayString), None, None, isAdmin = true))
      val jsonBody = Json.parse(response.body)
      Json.prettyPrint(jsonBody)
      (jsonBody \ "pages").as[Int] shouldBe 1
      (jsonBody \ "updates").as[JsArray].value.size shouldBe 1
      (jsonBody \ "updates") (0).as[PrintPreference] shouldBe ppOne
      ((jsonBody \ "updates") (0) \ "id").as[String] shouldBe "11111111"
      ((jsonBody \ "updates") (0) \ "idType").as[String] shouldBe "someType"
      ((jsonBody \ "updates") (0) \ "formIds") (0).as[String] shouldBe "f1"
      ((jsonBody \ "updates") (0) \ "formIds") (1).as[String] shouldBe "f2"
    }
  }
}

