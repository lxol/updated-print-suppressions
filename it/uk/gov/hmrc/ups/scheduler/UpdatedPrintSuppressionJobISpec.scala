/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.ups.scheduler

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.Scenario
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.test.Helpers._
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.ispec.UpdatedPrintSuppressionTestServer
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressions
import uk.gov.hmrc.ups.scheduled.jobs.UpdatedPrintSuppressionJob
import uk.gov.hmrc.ups.utils.Generate
import uk.gov.hmrc.workitem.{PermanentlyFailed, Succeeded}

import scala.concurrent.ExecutionContext.Implicits.global

class UpdatedPrintSuppressionJobISpec extends UpdatedPrintSuppressionTestServer {

  private val updatedPrintSuppressionJob = app.injector.instanceOf[UpdatedPrintSuppressionJob]

  "UpdatedPrintSuppression job" should {

    "process and save a preference associated with a valid utr" in {
      val entityId = Generate.entityId
      val utr = Generate.utr
      val updatedAt = DateTimeUtils.now
      val expectedStatusOnPreference = Succeeded

      stubFirstPullUpdatedPrintSuppression(entityId, updatedAt)
      stubGetEntity(entityId, utr)
      stubSetStatus(entityId, expectedStatusOnPreference, Status.OK)
      stubPullUpdatedPrintSuppressionWithNoResponseBody(expectedStatusOnPreference)

      await(updatedPrintSuppressionJob.executeInLock)

      val ups = await(upsCollection.find(Json.obj("printPreference.id" -> utr.value)).one[UpdatedPrintSuppressions]).get

      ups mustBe UpdatedPrintSuppressions(ups._id, 1, PrintPreference(utr.value, "utr", List("ABC", "DEF")), updatedAt)

    }

    "process and skip a preference associated with a valid nino" in {
      val entityId = Generate.entityId
      val nino = Generate.nino
      val updatedAt = DateTimeUtils.now
      val expectedStatusOnPreference = Succeeded

      stubFirstPullUpdatedPrintSuppression(entityId, updatedAt)
      stubGetEntity(entityId, nino)
      stubSetStatus(entityId, expectedStatusOnPreference, Status.OK)
      stubPullUpdatedPrintSuppressionWithNoResponseBody(expectedStatusOnPreference)

      await(updatedPrintSuppressionJob.executeInLock)

      await(upsCollection.count()) mustBe 0
    }

    "process and permanently fail orphan preferences" in {
      val entityId = Generate.entityId
      val updatedAt = DateTimeUtils.now
      val expectedStatusOnPreference = PermanentlyFailed

      stubFirstPullUpdatedPrintSuppression(entityId, updatedAt)
      stubGetEntityWithStatus(entityId, 404)
      stubSetStatus(entityId, expectedStatusOnPreference, 200)
      stubPullUpdatedPrintSuppressionWithNoResponseBody(expectedStatusOnPreference)

      await(updatedPrintSuppressionJob.executeInLock)

      await(upsCollection.count()) mustBe 0
    }

    "terminate in case of errors pulling from preferences" in {
      stubFor(
        post(urlMatching("/preferences/updated-print-suppression/pull-work-item"))
          .inScenario("ALL")
          .willReturn(aResponse().withStatus(500))
      )

      await(updatedPrintSuppressionJob.executeInLock)

      await(upsCollection.count()) mustBe 0
    }

    "terminate gracefully when an illegal state propagates from pulling preferences" in {
      stubFor(
        post(urlMatching("/preferences/updated-print-suppression/pull-work-item"))
          .inScenario("ALL")
          .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE))
      )

      await(updatedPrintSuppressionJob.executeInLock)

      await(upsCollection.count()) mustBe 0
    }

    "terminate gracefully when an illegal state propagates from calling the entity resolver" in {
      val entityId = Generate.entityId
      val updatedAt = DateTimeUtils.now

      stubFirstPullUpdatedPrintSuppression(entityId, updatedAt)
      stubExceptionOnGetEntity(entityId)

      await(updatedPrintSuppressionJob.executeInLock)

      await(upsCollection.count()) mustBe 0
    }

    "continue in case of errors setting the state on preferences" in {
      val entityId = Generate.entityId
      val utr = Generate.utr
      val updatedAt = DateTimeUtils.now
      val expectedStatusOnPreference = Succeeded

      stubFirstPullUpdatedPrintSuppression(entityId, updatedAt)
      stubGetEntity(entityId, utr)
      stubSetStatus(entityId, expectedStatusOnPreference, Status.INTERNAL_SERVER_ERROR)
      stubPullUpdatedPrintSuppressionWithNoResponseBody(expectedStatusOnPreference)

      await(updatedPrintSuppressionJob.executeInLock)

      await(upsCollection.count()) mustBe 1
    }
  }

//  override def externalServices: Seq[String] = Seq(
//    "preferences"
//  )
  override def externalServices: Seq[String] = Seq()
}
