package uk.gov.hmrc.ups.scheduler

import play.api.libs.json.Json
import play.api.libs.json.Json._
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.config.Jobs
import uk.gov.hmrc.ups.ispec.UpdatedPrintSuppressionTestServer
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressions
import uk.gov.hmrc.ups.utils.Generate

import scala.concurrent.ExecutionContext.Implicits.global

class UpdatedPrintSuppressionJobISpec extends UpdatedPrintSuppressionTestServer {

  import reactivemongo.json.ImplicitBSONHandlers._

  "UpdatedPrintSuppression job" should {

    "process and save a preference associated with a valid utr" in {
      val entityId = Generate.entityId
      val utr = Generate.utr
      val updatedAt = DateTimeUtils.now

      stubPullUpdatedPrintSuppression(entityId, updatedAt)
      stubGetEntity(entityId, utr)

      await(Jobs.UpdatedPrintSuppressionJob.executeInMutex)

      val ups = await(upsCollection.find(Json.obj("printPreference.id" -> utr.value)).one[UpdatedPrintSuppressions]).get

      ups shouldBe UpdatedPrintSuppressions(ups._id, 0, PrintPreference(utr.value, utr.name, List("ABC", "DEF")), updatedAt)

    }

    "process and skip a preference associated with a valid nino" in {
      val entityId = Generate.entityId
      val nino = Generate.nino
      val updatedAt = DateTimeUtils.now

      stubPullUpdatedPrintSuppression(entityId, updatedAt)
      stubGetEntity(entityId, nino)

      await(Jobs.UpdatedPrintSuppressionJob.executeInMutex)

      await(upsCollection.find(
        Json.obj("printPreference.id" -> nino.value)
      ).one[UpdatedPrintSuppressions]) shouldBe None
    }

    "process and permanently fail orphan preferences" in {
      pending
    }
  }
}
