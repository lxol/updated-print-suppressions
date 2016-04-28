package uk.gov.hmrc.ups.scheduled

import uk.gov.hmrc.ups.connectors.{PreferencesConnector, EntityResolverConnector}

import scala.concurrent.Future

trait PreferencesProcessor {

  def entityResolverConnector: EntityResolverConnector

  def preferencesConnector: PreferencesConnector


  def processUpdates(): Future[Boolean] = Future.successful(true)
}
