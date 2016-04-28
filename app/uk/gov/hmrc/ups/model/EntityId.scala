package uk.gov.hmrc.ups.model

import play.api.libs.json._

case class EntityId(value: String) {
  override def toString = value
}

object EntityId {

  implicit val read = new Reads[EntityId] {
    override def reads(json: JsValue): JsResult[EntityId] = json match {
      case JsString(s) => JsSuccess(EntityId(s))
      case _           => JsError("No entityId")
    }
  }

  implicit val write = new Writes[EntityId] {
    override def writes(e: EntityId): JsValue = JsString(e.value)
  }
}
