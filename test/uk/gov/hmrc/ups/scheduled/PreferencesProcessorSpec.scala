package uk.gov.hmrc.ups.scheduled

import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.connectors.{EntityResolverConnector, PreferencesConnector}
import uk.gov.hmrc.ups.model.{Entity, PrintPreference, PulledItem}
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressionsRepository
import uk.gov.hmrc.ups.utils.Generate

import scala.concurrent.Future


class PreferencesProcessorSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  "Process outstanding updates" should {
    //    "return result with zero if there are no updates to process" in new TestCase {
    //      when(mockPreferencesConnector.pullWorkItem()).thenReturn(Future.successful(None))
    ////      when(mockEntityResolverConnector.getTaxIdentifiers(entityId)).
    //      preferencesProcessor.processUpdates().futureValue should be ()
    //    }

    "return result with zero if there are no updates to process" in new TestCase {
      when(mockPreferencesConnector.pullWorkItem()(any())).thenReturn(Future.successful(Right(Some(pulledItem))))
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).thenReturn(Future.successful(Some(entity)))
      when(mockRepo.insert(same(printPreference))(any())).thenReturn(Future.successful(true))
      when(mockPreferencesConnector.changeStatus(randomEntityId, pulledItem.callbackUrl, "succeeded")).thenReturn(Future.successful(true))

      preferencesProcessor.processUpdates().futureValue should be(true)

      verify(mockPreferencesConnector).pullWorkItem()(any())
      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verify(mockRepo).insert(same(printPreference))(any())
      verify(mockPreferencesConnector).changeStatus(randomEntityId, pulledItem.callbackUrl, "succeeded")
    }
  }


  trait TestCase {
    val succeeded = "succeeded"
    val failed = "failed"
    val permanentlyFailed = "permanently-failed"

    val mockPreferencesConnector = mock[PreferencesConnector]
    val mockEntityResolverConnector = mock[EntityResolverConnector]
    val mockRepo = mock[UpdatedPrintSuppressionsRepository]

    implicit val hc = HeaderCarrier()

    val preferencesProcessor = new PreferencesProcessor {
      val preferencesConnector: PreferencesConnector = mockPreferencesConnector

      val entityResolverConnector: EntityResolverConnector = mockEntityResolverConnector
    }

    val randomUtr = Generate.utr
    val randomNino = Generate.nino
    val randomEntityId = Generate.entityId

    val pulledItem = PulledItem(randomEntityId, true, DateTimeUtils.now.minusMinutes(10), "someUrl")
    val printPreference = PrintPreference(randomUtr.value, "sautr", List("formId"))

    val entity = Entity(randomEntityId, randomUtr)
  }


}
