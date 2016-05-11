package uk.gov.hmrc.ups.ispec

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.joda.time.DateTime
import uk.gov.hmrc.play.controllers.RestFormats
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
                stripMargin))
        .willSetStateTo(InProgress.name)
    )
  }

  def stubPullUpdatedPrintSuppressionWithNoResponseBody(expectedStatus: ProcessingStatus, expectedHttpStatus: Int = 204) =
    stubFor(
      post(urlMatching("/preferences/updated-print-suppression/pull-work-item"))
        .inScenario("ALL")
        .whenScenarioStateIs(expectedStatus.name)
        .willReturn(aResponse().withStatus(expectedHttpStatus))
    )

  def stubSetStatus(entityId: EntityId, expectedStatus: ProcessingStatus) =
    stubFor(
      post(urlMatching(s"/preferences/updated-print-suppression/${entityId.value}/status"))
        .inScenario("ALL")
        .whenScenarioStateIs(InProgress.name)
        .withRequestBody(matching(
          s"""
             |"status":"${expectedStatus.name}"
          """.stripMargin))
        .willReturn(aResponse().withStatus(200))
        .willSetStateTo(expectedStatus.name)
    )

  def stubSetStatusToFail(entityId: EntityId, expectedHttpStatus: Int = 500) =
    stubFor(
      post(urlMatching(s"/preferences/updated-print-suppression/${entityId.value}/status"))
        .willReturn(aResponse().withStatus(expectedHttpStatus))
    )



}
