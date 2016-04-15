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

import org.scalatest.concurrent.ScalaFutures
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.http.BadRequestException
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class UpdatedPrintSuppressionsControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures {

  "list" should {
    "return the empty json until it is properly implemented" in {
      contentAsString(
        call(
          UpdatedPrintSuppressionsController.list(None, None),
          FakeRequest("GET", "/preferences/sa/individual/print-suppression?updated-on=2014-01-22")
        ).futureValue
      ) shouldBe """{"pages":0,"updates":[]}"""
    }

    "return 400 when the updated-on parameter is missing" in {
      intercept [BadRequestException] {
        await(
          UpdatedPrintSuppressionsController
            .list(None, None)(FakeRequest("GET", "/preferences/sa/individual/print-suppression"))
        )
      }.getMessage shouldBe "updated-on is a mandatory parameter"
    }
  }
}
