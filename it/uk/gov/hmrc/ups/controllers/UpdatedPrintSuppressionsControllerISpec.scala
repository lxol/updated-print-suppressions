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

package uk.gov.hmrc.ups.controllers

import org.joda.time.LocalDate
import org.scalatest.concurrent.IntegrationPatience
import play.api.libs.json.{JsArray, Json}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.ups.model.PrintPreference

class UpdatedPrintSuppressionsControllerISpec extends UnitSpec with TestServer with IntegrationPatience {

  "list" should {

    "return an empty list when there are no print suppression change events for that day" in new TestSetup {
      val response = get(`/preferences/sa/individual/print-suppression`(Some(yesterdayAsString), None, None))
      private val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] shouldBe 0
      (jsonBody \ "updates").as[JsArray].value.size shouldBe 0
    }

    "return all available print suppression change events occurred that day" in new TestSetup {
      val ppOne = PrintPreference("11111111", "someType", List.empty)
      val ppTwo = PrintPreference("22222222", "someType", List("f1", "f2"))

      await(
        repoYesterday.insert(ppOne, yesterday.toDateTimeAtStartOfDay).flatMap { _ =>
          repoYesterday.insert(ppTwo, yesterday.toDateTimeAtStartOfDay)
        }
      )

      val response = get(`/preferences/sa/individual/print-suppression`(Some(yesterdayAsString), None, None))
      response.status shouldBe 200

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] shouldBe 1
      (jsonBody \ "updates").as[JsArray].value.size shouldBe 2
      (jsonBody \ "updates") (0).as[PrintPreference] shouldBe ppOne
      (jsonBody \ "updates") (1).as[PrintPreference] shouldBe ppTwo
    }

    "return 'utr' instead of 'sautr' as idType for all available print suppression change events occurred that day" in new TestSetup {
      val pp1 = PrintPreference("11", "sautr", List("ABC"))
      val pp2 = PrintPreference("22", "utr", List("f1", "f2"))
      val pp3 = PrintPreference("33", "someType", List("f1", "f2"))

      await(
        repoYesterday.insert(pp1, yesterday.toDateTimeAtStartOfDay).flatMap { _ =>
          repoYesterday.insert(pp2, yesterday.toDateTimeAtStartOfDay).flatMap { _ =>
            repoYesterday.insert(pp3, yesterday.toDateTimeAtStartOfDay)
          }
        }
      )

      val response = get(`/preferences/sa/individual/print-suppression`(Some(yesterdayAsString), None, None))
      response.status shouldBe 200

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] shouldBe 1
      (jsonBody \ "updates").as[JsArray].value.size shouldBe 3
      ((jsonBody \ "updates") (0) \ "idType").as[String] shouldBe "utr"
      ((jsonBody \ "updates") (1) \ "idType").as[String] shouldBe "utr"
      ((jsonBody \ "updates") (2) \ "idType").as[String] shouldBe "someType"
    }

    "not return print suppression change events occurred on another day" in new TestSetup {

      val ppOne = PrintPreference("11111111", "someType", List.empty)
      val ppTwo = PrintPreference("22222222", "someType", List("f1", "f2"))
      await(
        repoToday.insert(ppOne, today.toDateTimeAtStartOfDay).flatMap { _ =>
          repoYesterday.insert(ppTwo, yesterday.toDateTimeAtStartOfDay)
        }
      )

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
      0 to 9 foreach { n =>
        await(repoYesterday.insert(PrintPreference(s"id_$n", "someType", List.empty), yesterday.toDateTimeAtStartOfDay))
      }

      val response = get(`/preferences/sa/individual/print-suppression`(Some(yesterdayAsString), None, Some("6")))

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] shouldBe 2
      (jsonBody \ "next").as[String] shouldBe s"/preferences/sa/individual/print-suppression?offset=7&limit=6&updated-on=$yesterdayAsString"
      (jsonBody \ "updates").as[JsArray].value.size shouldBe 6
    }

    "honor the offset when another batch of events is requested" in new TestSetup {
      0 to 9 foreach(n => await(repoYesterday.insert(PrintPreference(s"id_$n", "someType", List.empty), yesterday.toDateTimeAtStartOfDay)))
      val response = get(`/preferences/sa/individual/print-suppression`(Some(yesterdayAsString), Some("7"), Some("6")))

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
      (jsonBody \ "updates").as[JsArray].value.size shouldBe 0
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


