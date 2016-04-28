package uk.gov.hmrc.ups.model

import uk.gov.hmrc.domain.TaxIds._

case class Entity(id: EntityId, taxIdentifiers: Set[TaxIdWithName]) {
  require(taxIdentifiers.nonEmpty, "Entity does need at least one tax identifier")
}

object Entity {
  def apply(entityId: EntityId, taxIdentifiers: TaxIdWithName*): Entity = Entity(entityId, taxIdentifiers.toSet)

  val toStrings: (Set[TaxIdWithName]) => Set[String] = _.map(taxId => s"${taxId.name}: ${taxId.value}")
}
