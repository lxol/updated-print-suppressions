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

package uk.gov.hmrc.ups.model

import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import uk.gov.hmrc.domain.{SerialisableTaxId, TaxIds}
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import play.api.libs.functional.syntax._

case class Entity(id: EntityId, taxIdentifiers: Set[TaxIdWithName]) {
  require(taxIdentifiers.nonEmpty, "Entity does need at least one tax identifier")
}

object Entity {
  def apply(entityId: EntityId, taxIdentifiers: TaxIdWithName*): Entity = Entity(entityId, taxIdentifiers.toSet)

  val toStrings: (Set[TaxIdWithName]) => Set[String] = _.map(taxId => s"${taxId.name}: ${taxId.value}")

  val reads = (
    (__ \ "_id").read[EntityId] and
      TaxIds.reads(TaxIds.defaultSerialisableIds.toSet[SerialisableTaxId])
    ) ((entityId, taxIds) => Entity.apply(entityId, taxIds.values))

  val writes = new Writes[Entity] {
    override def writes(entity: Entity): JsValue = {
      val jsonEntries = entity.taxIdentifiers.foldLeft(Seq[(String, JsValueWrapper)]("_id" -> entity.id)) { (entries, taxId) =>
        entries :+ Tuple2[String, JsValueWrapper](taxId.name, taxId.value)
      }
      Json.obj(jsonEntries: _*)
    }
  }

  implicit val formats = Format(reads, writes)
}
