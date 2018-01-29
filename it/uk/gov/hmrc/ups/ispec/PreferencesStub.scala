package uk.gov.hmrc.ups.ispec

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.joda.time.DateTime
import org.skyscreamer.jsonassert.JSONCompareMode
import play.api.libs.json.Json
import uk.gov.hmrc.http.controllers.RestFormats
import uk.gov.hmrc.ups.model.EntityId
import uk.gov.hmrc.workitem.{InProgress, ProcessingStatus}

trait PreferencesStub {

  def stubFirstPullUpdatedPrintSuppression(entityId: EntityId, updatedAt: DateTime) = {
    stubFor(
      post(urlMatching("/preferences/updated-print-suppression/pull-work-item"))
        .inScenario("ALL")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              s"""
                 |{
                 |  "entityId" : "${entityId.value}",
                 |  "paperless" : true,
                 |  "updatedAt" : "${RestFormats.dateTimeWrite.writes(updatedAt).as[String]}",
                 |  "callbackUrl" : "/preferences/updated-print-suppression/${entityId.value}/status"
                 |}
                 | """.
                stripMargin)
        )
    )
  }

  def stubSetStatus(entityId: EntityId, expectedStatus: ProcessingStatus, httpStatusCode: Int) =
    stubFor(
      post(urlMatching(s"/preferences/updated-print-suppression/${entityId.value}/status"))
        .inScenario("ALL")
        .withRequestBody(
          equalToJson(
            Json.stringify(Json.obj("status" -> expectedStatus.name)),
            JSONCompareMode.LENIENT
          )
        )
        .willReturn(aResponse().withStatus(httpStatusCode))
        .willSetStateTo(expectedStatus.name)
    )

  def stubPullUpdatedPrintSuppressionWithNoResponseBody(expectedStatus: ProcessingStatus, expectedHttpStatus: Int = 204) =
    stubFor(
      post(urlMatching("/preferences/updated-print-suppression/pull-work-item"))
        .inScenario("ALL")
        .whenScenarioStateIs(expectedStatus.name)
        .willReturn(aResponse().withStatus(expectedHttpStatus))
    )


}
