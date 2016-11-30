package uk.gov.hmrc.ups.controllers

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.{OneServerPerSuite, WsScalaTestClient}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSRequest
import uk.gov.hmrc.play.test.UnitSpec

trait DatabaseName {
  val testName: String = "updated-print-suppressions"
}

trait TestServer
  extends ScalaFutures
    with DatabaseName
    with UnitSpec
    with BeforeAndAfterAll
    with OneServerPerSuite
    with WsScalaTestClient {

  override implicit lazy val app = new GuiceApplicationBuilder()
    .configure(Map("auditing.enabled" -> false,
      "mongodb.uri" -> "mongodb://localhost:27017/updated-print-suppressions",
      "application.router" -> "testOnlyDoNotUseInAppConf.Routes"
    ))
    .build()


  def `/preferences/sa/individual/print-suppression`(updatedOn: Option[String], offset: Option[String], limit: Option[String], isAdmin: Boolean = false) = {

    val queryString = List(
      updatedOn.map(value => "updated-on" -> value),
      offset.map(value => "offset" -> value),
      limit.map(value => "limit" -> value)
    ).flatten

    if (isAdmin)
      wsUrl("/test-only/preferences/sa/individual/print-suppression").withQueryString(queryString: _*)
    else
      wsUrl("/preferences/sa/individual/print-suppression").withQueryString(queryString: _*)
  }

  def get(url: WSRequest) = url.get().futureValue

}
