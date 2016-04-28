package uk.gov.hmrc.ups.model

import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.play.controllers.RestFormats

case class PulledItem(entityId: EntityId, paperless: Boolean, updatedAt: DateTime, callbackUrl: String)

object PulledItem {

  implicit val formats = {
    implicit val entityIdWrites = EntityId.write
    implicit val dateWrites = RestFormats.dateTimeWrite
    Json.format[PulledItem]
  }

}
