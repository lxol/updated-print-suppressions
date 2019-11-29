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
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.test.Helpers._
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.MongoCounterRepository

class UpdatedPrintSuppressionsControllerISpec extends PlaySpec with TestServer with IntegrationPatience {

  "list" should {

    "return an empty list when there are no print suppression change events for that day" in new TestSetup {
      lazy override val reactiveMongoComponent: ReactiveMongoComponent = testMongoComponent
      lazy override val mongoCounterRepository: MongoCounterRepository = testCounterRepository

      private val response = get(preferencesSaIndividualPrintSuppression(Some(yesterdayAsString), None, None))
      private val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] mustBe 0
      (jsonBody \ "updates").as[JsArray].value.size mustBe 0
    }

    "return all available print suppression change events occurred that day" in new TestSetup {
      lazy override val reactiveMongoComponent: ReactiveMongoComponent = testMongoComponent
      lazy override val mongoCounterRepository: MongoCounterRepository = testCounterRepository


      private val ppOne = PrintPreference("11111111", "someType", List.empty)
      private val ppTwo = PrintPreference("22222222", "someType", List("f1", "f2"))

      await(
        repoYesterday.insert(ppOne, yesterday.toDateTimeAtStartOfDay).flatMap { _ =>
          repoYesterday.insert(ppTwo, yesterday.toDateTimeAtStartOfDay)
        }
      )

      private val response = get(preferencesSaIndividualPrintSuppression(Some(yesterdayAsString), None, None))
      response.status mustBe OK

      private val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] mustBe 1
      (jsonBody \ "updates").as[JsArray].value.size mustBe 2
      (jsonBody \ "updates") (0).as[PrintPreference] mustBe ppOne
      (jsonBody \ "updates") (1).as[PrintPreference] mustBe ppTwo
    }

    "return 'utr' instead of 'sautr' as idType for all available print suppression change events occurred that day" in new TestSetup {
      lazy override val reactiveMongoComponent: ReactiveMongoComponent = testMongoComponent
      lazy override val mongoCounterRepository: MongoCounterRepository = testCounterRepository

      private val pp1 = PrintPreference("11", "sautr", List("ABC"))
      private val pp2 = PrintPreference("22", "utr", List("f1", "f2"))
      private val pp3 = PrintPreference("33", "someType", List("f1", "f2"))

      await(
        repoYesterday.insert(pp1, yesterday.toDateTimeAtStartOfDay).flatMap { _ =>
          repoYesterday.insert(pp2, yesterday.toDateTimeAtStartOfDay).flatMap { _ =>
            repoYesterday.insert(pp3, yesterday.toDateTimeAtStartOfDay)
          }
        }
      )

      private val response = get(preferencesSaIndividualPrintSuppression(Some(yesterdayAsString), None, None))
      response.status mustBe OK

      private val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] mustBe 1
      (jsonBody \ "updates").as[JsArray].value.size mustBe 3
      ((jsonBody \ "updates") (0) \ "idType").as[String] mustBe "utr"
      ((jsonBody \ "updates") (1) \ "idType").as[String] mustBe "utr"
      ((jsonBody \ "updates") (2) \ "idType").as[String] mustBe "someType"
    }

    "not return print suppression change events occurred on another day" in new TestSetup {
      lazy override val reactiveMongoComponent: ReactiveMongoComponent = testMongoComponent
      lazy override val mongoCounterRepository: MongoCounterRepository = testCounterRepository


      private val ppOne = PrintPreference("11111111", "someType", List.empty)
      private val ppTwo = PrintPreference("22222222", "someType", List("f1", "f2"))
      await(
        repoToday.insert(ppOne, today.toDateTimeAtStartOfDay).flatMap { _ =>
          repoYesterday.insert(ppTwo, yesterday.toDateTimeAtStartOfDay)
        }
      )

      private val response = get(preferencesSaIndividualPrintSuppression(Some(yesterdayAsString), None, None))
      private val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] mustBe 1
      (jsonBody \ "updates").as[JsArray].value.size mustBe 1
      (jsonBody \ "updates") (0).as[PrintPreference] mustBe ppTwo
      ((jsonBody \ "updates") (0) \ "id").as[String] mustBe "22222222"
      ((jsonBody \ "updates") (0) \ "idType").as[String] mustBe "someType"
      ((jsonBody \ "updates") (0) \ "formIds") (0).as[String] mustBe "f1"
      ((jsonBody \ "updates") (0) \ "formIds") (1).as[String] mustBe "f2"
    }

    "limit the number of events returned and a the path to next batch of events" in new TestSetup {
      lazy override val reactiveMongoComponent: ReactiveMongoComponent = testMongoComponent
      lazy override val mongoCounterRepository: MongoCounterRepository = testCounterRepository


      0 to 9 foreach { n =>
        await(repoYesterday.insert(PrintPreference(s"id_$n", "someType", List.empty), yesterday.toDateTimeAtStartOfDay))
      }

      private val response = get(preferencesSaIndividualPrintSuppression(Some(yesterdayAsString), None, Some("6")))

      private val jsonBody: JsValue = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] mustBe 2
      (jsonBody \ "next").as[String] mustBe s"/preferences/sa/individual/print-suppression?offset=7&limit=6&updated-on=$yesterdayAsString"
      (jsonBody \ "updates").as[JsArray].value.size mustBe 6
    }

    "honor the offset when another batch of events is requested" in new TestSetup {
      lazy override val reactiveMongoComponent: ReactiveMongoComponent = testMongoComponent
      lazy override val mongoCounterRepository: MongoCounterRepository = testCounterRepository


      0 to 9 foreach(n => await(repoYesterday.insert(PrintPreference(s"id_$n", "someType", List.empty), yesterday.toDateTimeAtStartOfDay)))
      private val response = get(preferencesSaIndividualPrintSuppression(Some(yesterdayAsString), Some("7"), Some("6")))

      private val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] mustBe 2
      (jsonBody \ "next").asOpt[String] must not be defined
      (jsonBody \ "updates").as[JsArray].value.size mustBe 4
    }
//
    "allow a big number as an offset" in new TestSetup {
      lazy override val reactiveMongoComponent: ReactiveMongoComponent = testMongoComponent
      lazy override val mongoCounterRepository: MongoCounterRepository = testCounterRepository


      private val response = get(preferencesSaIndividualPrintSuppression(Some(yesterdayAsString), Some("50000"), None))
      response.status mustBe OK

      private val jsonBody = Json.parse(response.body)
      (jsonBody \ "pages").as[Int] mustBe 0
      (jsonBody \ "updates").as[JsArray].value.size mustBe 0
    }

    "return 400 when the limit is not a number between 1 and 20,000" in {
      val response = get(preferencesSaIndividualPrintSuppression(Some("2014-01-22"), None, Some("99999999")))
      response.status mustBe BAD_REQUEST

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] mustBe BAD_REQUEST
      (jsonBody \ "message").as[String] mustBe "limit parameter cannot be bigger than 20000"
    }

    "return 400 when the limit is negative" in {
      val response = get(preferencesSaIndividualPrintSuppression(Some("2014-01-22"), None, Some("-1")))
      response.status mustBe BAD_REQUEST

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] mustBe BAD_REQUEST
      (jsonBody \ "message").as[String] mustBe "limit parameter is less than zero"
    }

    "return 400 when the limit is not a number" in {
      val response = get(preferencesSaIndividualPrintSuppression(Some("2014-01-22"), None, Some("not-a-number")))
      response.status mustBe BAD_REQUEST

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] mustBe BAD_REQUEST
      (jsonBody \ "message").as[String] mustBe "Cannot parse parameter limit as Int"
    }

    "return 400 when the offset is not a number" in {

      val response = get(preferencesSaIndividualPrintSuppression(Some("2014-01-22"), Some("not-a-number"), None))
      response.status mustBe BAD_REQUEST

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] mustBe BAD_REQUEST
      (jsonBody \ "message").as[String] mustBe "Cannot parse parameter offset as Int: For input string: \"not-a-number\""
    }

    "return 400 when the updated-on parameter is malformed" in {
      val response = get(preferencesSaIndividualPrintSuppression(Some("not-a-date"), None, None))
      response.status mustBe BAD_REQUEST

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] mustBe BAD_REQUEST
      (jsonBody \ "message").as[String] mustBe "updated-on parameter is in the wrong format. Should be (yyyy-MM-dd)"
    }

    "return 400 when the updated-on parameter is not a date in the past" in {
      val response = get(preferencesSaIndividualPrintSuppression(Some(LocalDate.now.toString("yyyy-MM-dd")), None, None))
      response.status mustBe BAD_REQUEST

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] mustBe BAD_REQUEST
      (jsonBody \ "message").as[String] mustBe "updated-on parameter can only be used with dates in the past"
    }

    "return 400 when the updated-on parameter is missing" in {
      val response = get(preferencesSaIndividualPrintSuppression(None, None, None))
      response.status mustBe BAD_REQUEST

      val jsonBody = Json.parse(response.body)
      (jsonBody \ "statusCode").as[Int] mustBe BAD_REQUEST
      (jsonBody \ "message").as[String] mustBe "updated-on is a mandatory parameter"
    }
  }

}


