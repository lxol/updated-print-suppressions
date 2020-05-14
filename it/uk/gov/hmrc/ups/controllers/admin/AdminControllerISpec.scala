/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.ups.controllers.admin

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsArray, JsValue, Json }
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.ups.controllers.{ TestServer, TestSetup }
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.{ MongoCounterRepository, UpdatedPrintSuppressions }

import scala.concurrent.Future

class AdminControllerISpec extends PlaySpec with TestServer with BeforeAndAfterEach {

  "AdminController" should {

    "insert a new PrintPreference" in new TestSetup {
      lazy override val reactiveMongoComponent: ReactiveMongoComponent = testMongoComponent
      lazy override val mongoCounterRepository: MongoCounterRepository = testCounterRepository
      private val preference = PrintPreference("someId", "someType", List("f1"))
      await(
        wsUrl("/preferences/sa/individual/print-suppression")
          .withQueryStringParameters("date" -> yesterdayAsString)
          .post(Json.toJson(preference)))

      private val all: Future[List[UpdatedPrintSuppressions]] = repoYesterday.findAll()

      await(all).map { x =>
        (x.counter, preference)
      } mustBe List((1, preference))
      dropTestCollection(repoYesterday.collection.name)
    }

    "fetch a new PrintPreference created today using admin end-point" in new TestSetup {
      lazy override val reactiveMongoComponent: ReactiveMongoComponent = testMongoComponent
      lazy override val mongoCounterRepository: MongoCounterRepository = testCounterRepository

      private val ppOne = PrintPreference("11111111", "someType", List("f1", "f2"))
      await(repoToday.insert(ppOne, today.toDateTimeAtStartOfDay))

      private val response: WSResponse =
        get(preferencesSaIndividualPrintSuppression(Some(todayString), None, None, isAdmin = true))
      private val jsonBody: JsValue = Json.parse(response.body)
      Json.prettyPrint(jsonBody)
      (jsonBody \ "pages").as[Int] mustBe 1
      (jsonBody \ "updates").as[JsArray].value.size mustBe 1
      (jsonBody \ "updates")(0).as[PrintPreference] mustBe ppOne
      ((jsonBody \ "updates")(0) \ "id").as[String] mustBe "11111111"
      ((jsonBody \ "updates")(0) \ "idType").as[String] mustBe "someType"
      ((jsonBody \ "updates")(0) \ "formIds")(0).as[String] mustBe "f1"
      ((jsonBody \ "updates")(0) \ "formIds")(1).as[String] mustBe "f2"
      dropTestCollection(repoToday.collection.name)
    }
  }

}
