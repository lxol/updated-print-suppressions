package uk.gov.hmrc.ups.ispec

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.joda.time.DateTime
import uk.gov.hmrc.play.controllers.RestFormats
import uk.gov.hmrc.ups.model.EntityId

trait PreferencesStub {

  def stubPullUpdatedPrintSuppression(entityId: EntityId, updatedAt: DateTime) = {
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
    )


    stubFor(
      post(urlMatching(s"/preferences/updated-print-suppression/${entityId.value}/status"))
        .inScenario("ALL")
        .willReturn(aResponse().withStatus(200))
        .willSetStateTo("SUCCEEDED")
    )

    stubFor(
      post(urlMatching("/preferences/updated-print-suppression/pull-work-item"))
        .inScenario("ALL")
        .whenScenarioStateIs("SUCCEEDED")
        .willReturn(aResponse().withStatus(204))
    )
  }
}
