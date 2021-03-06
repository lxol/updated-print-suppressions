package uk.gov.hmrc.ups.scheduler

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.Scenario

import play.api.libs.json.Json
import play.api.libs.json.Json._

import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.config.Jobs
import uk.gov.hmrc.ups.ispec.UpdatedPrintSuppressionTestServer
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressions
import uk.gov.hmrc.ups.utils.Generate
import uk.gov.hmrc.workitem.{PermanentlyFailed, Succeeded}
import reactivemongo.play.json.ImplicitBSONHandlers._
import scala.concurrent.ExecutionContext.Implicits.global

class UpdatedPrintSuppressionJobISpec extends UpdatedPrintSuppressionTestServer {

  "UpdatedPrintSuppression job" should {

    "process and save a preference associated with a valid utr" in {
      val entityId = Generate.entityId
      val utr = Generate.utr
      val updatedAt = DateTimeUtils.now
      val expectedStatusOnPreference = Succeeded

      stubFirstPullUpdatedPrintSuppression(entityId, updatedAt)
      stubGetEntity(entityId, utr)
      stubSetStatus(entityId, expectedStatusOnPreference, 200)
      stubPullUpdatedPrintSuppressionWithNoResponseBody(expectedStatusOnPreference)

      await(Jobs.UpdatedPrintSuppressionJob.executeInMutex)

      val ups = await(upsCollection.find(Json.obj("printPreference.id" -> utr.value)).one[UpdatedPrintSuppressions]).get

      ups shouldBe UpdatedPrintSuppressions(ups._id, 1, PrintPreference(utr.value, "utr", List("ABC", "DEF")), updatedAt)

    }

    "process and skip a preference associated with a valid nino" in {
      val entityId = Generate.entityId
      val nino = Generate.nino
      val updatedAt = DateTimeUtils.now
      val expectedStatusOnPreference = Succeeded

      stubFirstPullUpdatedPrintSuppression(entityId, updatedAt)
      stubGetEntity(entityId, nino)
      stubSetStatus(entityId, expectedStatusOnPreference, 200)
      stubPullUpdatedPrintSuppressionWithNoResponseBody(expectedStatusOnPreference)

      await(Jobs.UpdatedPrintSuppressionJob.executeInMutex)

      await(upsCollection.count()) shouldBe 0
    }

    "process and permanently fail orphan preferences" in {
      val entityId = Generate.entityId
      val updatedAt = DateTimeUtils.now
      val expectedStatusOnPreference = PermanentlyFailed

      stubFirstPullUpdatedPrintSuppression(entityId, updatedAt)
      stubGetEntityWithStatus(entityId, 404)
      stubSetStatus(entityId, expectedStatusOnPreference, 200)
      stubPullUpdatedPrintSuppressionWithNoResponseBody(expectedStatusOnPreference)

      await(Jobs.UpdatedPrintSuppressionJob.executeInMutex)

      await(upsCollection.count()) shouldBe 0
    }

    "terminate in case of errors pulling from preferences" in {
      stubFor(
        post(urlMatching("/preferences/updated-print-suppression/pull-work-item"))
          .inScenario("ALL")
          .willReturn(aResponse().withStatus(500))
      )

      await(Jobs.UpdatedPrintSuppressionJob.executeInMutex)

      await(upsCollection.count()) shouldBe 0
    }

    "terminate gracefully when an illegal state propagates from pulling preferences" in {
      stubFor(
        post(urlMatching("/preferences/updated-print-suppression/pull-work-item"))
          .inScenario("ALL")
          .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE))
      )

      await(Jobs.UpdatedPrintSuppressionJob.executeInMutex)

      await(upsCollection.count()) shouldBe 0
    }

    "terminate gracefully when an illegal state propagates from calling the entity resolver" in {
      val entityId = Generate.entityId
      val updatedAt = DateTimeUtils.now

      stubFirstPullUpdatedPrintSuppression(entityId, updatedAt)
      stubExceptionOnGetEntity(entityId)

      await(Jobs.UpdatedPrintSuppressionJob.executeInMutex)

      await(upsCollection.count()) shouldBe 0
    }

    "continue in case of errors setting the state on preferences" in {
      val entityId = Generate.entityId
      val utr = Generate.utr
      val updatedAt = DateTimeUtils.now
      val expectedStatusOnPreference = Succeeded

      stubFirstPullUpdatedPrintSuppression(entityId, updatedAt)
      stubGetEntity(entityId, utr)
      stubSetStatus(entityId, expectedStatusOnPreference, 500)
      stubPullUpdatedPrintSuppressionWithNoResponseBody(expectedStatusOnPreference)

      await(Jobs.UpdatedPrintSuppressionJob.executeInMutex)

      await(upsCollection.count()) shouldBe 1
    }
  }
}
