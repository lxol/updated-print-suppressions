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

package uk.gov.hmrc.ups.ispec

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.{ Scenario, StubMapping }
import org.joda.time.DateTime
import org.skyscreamer.jsonassert.JSONCompareMode
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.http.controllers.RestFormats
import uk.gov.hmrc.ups.model.EntityId
import uk.gov.hmrc.workitem.ProcessingStatus

trait PreferencesStub {

  def stubFirstPullUpdatedPrintSuppression(entityId: EntityId, updatedAt: DateTime): StubMapping =
    stubFor(
      post(urlMatching("/preferences/updated-print-suppression/pull-work-item"))
        .inScenario("ALL")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |  "entityId" : "${entityId.value}",
                         |  "paperless" : true,
                         |  "updatedAt" : "${RestFormats.dateTimeWrite.writes(updatedAt).as[String]}",
                         |  "callbackUrl" : "/preferences/updated-print-suppression/${entityId.value}/status"
                         |}
                         | """.stripMargin)
        )
    )

  def stubSetStatus(entityId: EntityId, expectedStatus: ProcessingStatus, httpStatusCode: Int): StubMapping =
    stubFor(
      post(urlMatching(s"/preferences/updated-print-suppression/${entityId.value}/status"))
        .inScenario("ALL")
        .withRequestBody(
          equalToJson(
            Json.stringify(Json.obj("status" -> expectedStatus.name))
            //JSONCompareMode.LENIENT
          )
        )
        .willReturn(aResponse().withStatus(httpStatusCode))
        .willSetStateTo(expectedStatus.name)
    )

  def stubPullUpdatedPrintSuppressionWithNoResponseBody(expectedStatus: ProcessingStatus, expectedHttpStatus: Int = Status.NO_CONTENT): StubMapping =
    stubFor(
      post(urlMatching("/preferences/updated-print-suppression/pull-work-item"))
        .inScenario("ALL")
        .whenScenarioStateIs(expectedStatus.name)
        .willReturn(aResponse().withStatus(expectedHttpStatus))
    )

}
