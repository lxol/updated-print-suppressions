package uk.gov.hmrc.ups.controllers

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.ws.{WSRequestHolder, WS}
import uk.gov.hmrc.play.it.{ExternalService, MongoMicroServiceEmbeddedServer}
import uk.gov.hmrc.play.test.UnitSpec

trait DatabaseName {
  val testName: String = "updated-print-suppressions"
}

trait TestServer extends ScalaFutures with DatabaseName with MongoMicroServiceEmbeddedServer with UnitSpec with BeforeAndAfterAll {
  import play.api.Play.current

  override val dbName = "updated-print-suppressions"
  override protected val externalServices: Seq[ExternalService] = Seq.empty


  override protected def additionalConfig: Map[String, Any] = Map("auditing.enabled" -> false)

  def `/preferences/sa/individual/print-suppression`(updatedOn: Option[String], offset: Option[String], limit: Option[String]) = {

    val queryString = List(
      updatedOn.map(value => "updated-on" -> value),
      offset.map(value => "offset" -> value),
      limit.map(value => "limit" -> value)
    ).flatten

    WS.url(resource("/preferences/sa/individual/print-suppression")).withQueryString(queryString: _*)
  }


  def get(url: WSRequestHolder) = url.get().futureValue

  override def beforeAll(): Unit = start()

  override def afterAll(): Unit = stop()
}
