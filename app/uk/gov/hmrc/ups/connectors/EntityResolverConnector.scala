package uk.gov.hmrc.ups.connectors

import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.ws.WSGet
import uk.gov.hmrc.ups.model.{Entity, EntityId}

import scala.concurrent.Future

trait EntityResolverConnector {
  def getTaxIdentifiers(entityId: EntityId): Future[Option[Entity]] = ???


  def http: WSGet

  def baseUrl: String

}
