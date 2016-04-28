package uk.gov.hmrc.ups.model

import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.play.controllers.RestFormats

object Filters {
  implicit val format = {
    implicit val dateTimeReader = RestFormats.dateTimeFormats
    Json.format[Filters]
  }
}

case class Filters(failedBefore: DateTime, availableBefore: DateTime)

object WorkItemRequest {
  implicit val format = Json.format[WorkItemRequest]
}

case class WorkItemRequest(filters: Filters)