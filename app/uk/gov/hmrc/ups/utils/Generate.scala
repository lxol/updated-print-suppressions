package uk.gov.hmrc.ups.utils

import java.util.UUID

import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.ups.model.EntityId

import scala.util.Random

object Generate {
  private val random = new Random()

  def nino = Nino(f"CE${random.nextInt(100000)}%06dD")
  def utr = SaUtr(UUID.randomUUID.toString)
  def entityId = {
    EntityId(UUID.randomUUID.toString)
  }
}
