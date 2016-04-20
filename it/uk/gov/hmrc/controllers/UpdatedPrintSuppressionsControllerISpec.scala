/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.controllers

import org.joda.time.LocalDate
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.{WS, WSRequestHolder}
import play.api.test.Helpers._
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.it.{ExternalService, MongoMicroServiceEmbeddedServer}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.{MongoCounterRepository, UpdatedPrintSuppressionsRepository}

class UpdatedPrintSuppressionsControllerISpec extends UnitSpec with WithFakeApplication with TestServer with IntegrationPatience {


  "list" should {
    "return an empty list when there are no print suppression change events for that day" in new TestSetup {


      val response = get(`/preferences/sa/individual/print-suppression`(Some(yesterdayAsString), None, None))
      private val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] shouldBe 0
      jsonBody \ "updates" shouldBe JsArray()
    }

    "return all available print suppression change events occurred that day" in new TestSetup {

      val ppOne = PrintPreference("11111111", "someType", List.empty)
      val ppTwo = PrintPreference("22222222", "someType", List("f1", "f2"))
      await(repoYesterday.insert(ppOne))
      await(repoYesterday.insert(ppTwo))

      val response = get(`/preferences/sa/individual/print-suppression`(Some(yesterdayAsString), None, None))
      response.status shouldBe 200

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] shouldBe 1
      (jsonBody \ "updates").as[JsArray].value.size shouldBe 2
      (jsonBody \ "updates") (0).as[PrintPreference] shouldBe ppOne
      (jsonBody \ "updates") (1).as[PrintPreference] shouldBe ppTwo
    }

    "not return print suppression change events occurred on another day" in new TestSetup {

      val ppOne = PrintPreference("11111111", "someType", List.empty)
      val ppTwo = PrintPreference("22222222", "someType", List("f1", "f2"))
      await(repoToday.insert(ppOne))
      await(repoYesterday.insert(ppTwo))

      val response = get(`/preferences/sa/individual/print-suppression`(Some(yesterdayAsString), None, None))
      val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] shouldBe 1
      (jsonBody \ "updates").as[JsArray].value.size shouldBe 1
      (jsonBody \ "updates") (0).as[PrintPreference] shouldBe ppTwo
      ((jsonBody \ "updates") (0) \ "id").as[String] shouldBe "22222222"
      ((jsonBody \ "updates") (0) \ "idType").as[String] shouldBe "someType"
      ((jsonBody \ "updates") (0) \ "formIds") (0).as[String] shouldBe "f1"
      ((jsonBody \ "updates") (0) \ "formIds") (1).as[String] shouldBe "f2"
    }

    "limit the number of events returned and a the path to next batch of events" in new TestSetup {
      0 to 9 foreach(n => await(repoYesterday.insert(PrintPreference(s"id_$n", "someType", List.empty))))
      val response = get(`/preferences/sa/individual/print-suppression`(Some(yesterdayAsString), None, Some("6")))

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] shouldBe 2
      (jsonBody \ "next").as[String] shouldBe s"/preferences/sa/individual/print-suppression?offset=6&limit=6&updated-on=$yesterdayAsString"
      (jsonBody \ "updates").as[JsArray].value.size shouldBe 6
    }

    "honor the offset when another batch of events is requested" in new TestSetup {
      0 to 9 foreach(n => await(repoYesterday.insert(PrintPreference(s"id_$n", "someType", List.empty))))
      val response = get(`/preferences/sa/individual/print-suppression`(Some(yesterdayAsString), Some("6"), Some("6")))

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] shouldBe 2
      (jsonBody \ "next").asOpt[String] should not be defined
      (jsonBody \ "updates").as[JsArray].value.size shouldBe 4
    }

    "allow a big number as an offset" in new TestSetup {
      val response = get(`/preferences/sa/individual/print-suppression`(Some(yesterdayAsString), Some("50000"), None))
      response.status shouldBe 200

      private val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] shouldBe 0
      jsonBody \ "updates" shouldBe JsArray()
    }

    "return 400 when the limit is not a number between 1 and 20,000" in {
      val response = get(`/preferences/sa/individual/print-suppression`(Some("2014-01-22"), None, Some("99999999")))
      response.status shouldBe 400

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] shouldBe 400
      (jsonBody \ "message").as[String] shouldBe "limit parameter cannot be bigger than 20000"
    }

    "return 400 when the limit is negative" in {
      val response = get(`/preferences/sa/individual/print-suppression`(Some("2014-01-22"), None, Some("-1")))
      response.status shouldBe 400

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] shouldBe 400
      (jsonBody \ "message").as[String] shouldBe "limit parameter is less than zero"
    }

    "return 400 when the limit is not a number" in {
      val response = get(`/preferences/sa/individual/print-suppression`(Some("2014-01-22"), None, Some("not-a-number")))
      response.status shouldBe 400

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] shouldBe 400
      (jsonBody \ "message").as[String] shouldBe "Cannot parse parameter limit as Int"
    }

    "return 400 when the offset is not a number" in {

      val response = get(`/preferences/sa/individual/print-suppression`(Some("2014-01-22"), Some("not-a-number"), None))
      response.status shouldBe 400

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] shouldBe 400
      (jsonBody \ "message").as[String] shouldBe "Cannot parse parameter offset as Int: For input string: \"not-a-number\""
    }

    "return 400 when the updated-on parameter is malformed" in {
      val response = get(`/preferences/sa/individual/print-suppression`(Some("not-a-date"), None, None))
      response.status shouldBe 400

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] shouldBe 400
      (jsonBody \ "message").as[String] shouldBe "updated-on parameter is in the wrong format. Should be (yyyy-MM-dd)"
    }

    "return 400 when the updated-on parameter is not a date in the past" in {
      val response = get(`/preferences/sa/individual/print-suppression`(Some(LocalDate.now.toString("yyyy-MM-dd")), None, None))
      response.status shouldBe 400

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] shouldBe 400
      (jsonBody \ "message").as[String] shouldBe "updated-on parameter can only be used with dates in the past"
    }

    "return 400 when the updated-on parameter is missing" in {
      val response = get(`/preferences/sa/individual/print-suppression`(None, None, None))
      response.status shouldBe 400

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] shouldBe 400
      (jsonBody \ "message").as[String] shouldBe "updated-on is a mandatory parameter"
    }
  }

}


class TestSetup(override val databaseName: String = "updated-print-suppressions") extends MongoSpecSupport {

  implicit val ppFormats = PrintPreference.formats
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  val today = LocalDate.now
  private val yesterday = today.minusDays(1)


  val todayString = today.toString("yyyy-MM-dd")
  val yesterdayAsString = yesterday.toString("yyyy-MM-dd")

  // Reset the counters
  await(new MongoCounterRepository("-").removeAll())

  val repoToday = new UpdatedPrintSuppressionsRepository(today, counterName => new MongoCounterRepository(counterName))
  await(repoToday.removeAll())

  val repoYesterday = new UpdatedPrintSuppressionsRepository(yesterday, counterName => new MongoCounterRepository(counterName))
  await(repoYesterday.removeAll())
}

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