/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.ups.scheduled

import org.mockito.Mockito._
import org.mockito.Matchers.{eq => argEq, _}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.http.Status._
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.connectors.{EntityResolverConnector, PreferencesConnector}
import uk.gov.hmrc.ups.model.{Entity, PrintPreference, PulledItem}
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressionsRepository
import uk.gov.hmrc.ups.utils.Generate

import scala.concurrent.Future


class PreferencesProcessorSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  implicit val hc = HeaderCarrier()

  "Process outstanding updates" should {
    "return true when an updated preference is resolved and stored" in new TestCase {
      when(mockPreferencesConnector.pullWorkItem()(any())).thenReturn(Future.successful(Right(Some(pulledItem))))
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).thenReturn(Future.successful(Right(Some(entity))))
      when(mockRepo.insert(argEq(printPreference))(any())).thenReturn(Future.successful(true))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, succeeded)).thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates().futureValue should be(true)

      verify(mockPreferencesConnector).pullWorkItem()(any())
      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verify(mockRepo).insert(argEq(printPreference))(any())
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, succeeded)
    }

    "return true if no more pulled item from preference" in new TestCase {
      when(mockPreferencesConnector.pullWorkItem()(any())).thenReturn(Future.successful(Right(None)))

      preferencesProcessor.processUpdates().futureValue should be(true)

      verify(mockPreferencesConnector).pullWorkItem()(any())
    }

    "return true the user opted out digital" in new TestCase {
      val optedOut = printPreference.copy(formIds = List.empty)

      when(mockPreferencesConnector.pullWorkItem()(any())).thenReturn(Future.successful(Right(Some(pulledItem.copy(paperless = false)))))
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).thenReturn(Future.successful(Right(Some(entity))))
      when(mockRepo.insert(argEq(optedOut))(any())).thenReturn(Future.successful(true))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, succeeded)).thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates().futureValue should be(true)

      verify(mockPreferencesConnector).pullWorkItem()(any())
      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verify(mockRepo).insert(argEq(optedOut))(any())
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, succeeded)
    }

    "return true if no entry found for the given utr" in new TestCase {

      when(mockPreferencesConnector.pullWorkItem()(any())).thenReturn(Future.successful(Right(Some(pulledItem.copy(paperless = false)))))
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).thenReturn(Future.successful(Right(None)))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, permanentlyFailed)).thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates().futureValue should be(true)

      verify(mockPreferencesConnector).pullWorkItem()(any())
      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verifyZeroInteractions(mockRepo)
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, permanentlyFailed)
    }

    "return true if there is any error comunicated with entity resolver" in new TestCase {

      when(mockPreferencesConnector.pullWorkItem()(any())).thenReturn(Future.successful(Right(Some(pulledItem.copy(paperless = false)))))
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).thenReturn(Future.successful(Left(BAD_GATEWAY)))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, failed)).thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates().futureValue should be(true)

      verify(mockPreferencesConnector).pullWorkItem()(any())
      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verifyZeroInteractions(mockRepo)
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, failed)
    }
  }

  "insertAndUpdate" should {

    "return true when the record is inserted into UPS repo and the status of the preference has been updated externally" in new TestCase {
      when(mockRepo.insert(argEq(printPreference))(any())).thenReturn(Future.successful(true))
      when(mockPreferencesConnector.changeStatus(argEq(callbackUrl), argEq(succeeded))(any())).
        thenReturn(Future.successful(CONFLICT))

      preferencesProcessor.insertAndUpdate(randomUtr, forms, callbackUrl).futureValue shouldBe true

      verify(mockRepo).insert(argEq(printPreference))(any())
      verify(mockPreferencesConnector).changeStatus(argEq(callbackUrl), argEq(succeeded))(any())
    }

    "return false when the record is inserted into UPS repo and the status of the updated preference is not not ok" in new TestCase {
      when(mockRepo.insert(argEq(printPreference))(any())).thenReturn(Future.successful(true))
      when(mockPreferencesConnector.changeStatus(argEq(callbackUrl), argEq(succeeded))(any())).thenReturn(Future.successful(BAD_REQUEST))
      when(mockRepo.removeByUtr(printPreference.id)).thenReturn(Future.successful(true))

      preferencesProcessor.insertAndUpdate(randomUtr, forms, callbackUrl).futureValue shouldBe false

      verify(mockRepo).insert(argEq(printPreference))(any())
      verify(mockPreferencesConnector).changeStatus(argEq(callbackUrl), argEq(succeeded))(any())
      verify(mockRepo).removeByUtr(printPreference.id)
    }

    "return false when the record cannot be inserted into the UPS repo" in new TestCase {
      when(mockRepo.insert(argEq(printPreference))(any())).thenReturn(Future.successful(false))
      when(mockPreferencesConnector.changeStatus(argEq(callbackUrl), argEq(failed))(any())).thenReturn(Future.successful(OK))

      preferencesProcessor.insertAndUpdate(randomUtr, forms, callbackUrl).futureValue shouldBe false

      verify(mockRepo).insert(argEq(printPreference))(any())
      verify(mockPreferencesConnector).changeStatus(argEq(callbackUrl), argEq(failed))(any())
      verifyNoMoreInteractions(mockRepo)
    }

    "return false when something exceptional occurs while inserting into the UPS repo" in new TestCase {
      when(mockRepo.insert(argEq(printPreference))(any())).
        thenReturn(Future.failed(new RuntimeException("fail")))
      when(mockPreferencesConnector.changeStatus(argEq(callbackUrl), argEq(failed))(any())).thenReturn(Future.successful(OK))

      preferencesProcessor.insertAndUpdate(randomUtr, forms, callbackUrl).futureValue shouldBe false

      verify(mockRepo).insert(argEq(printPreference))(any())
      verify(mockPreferencesConnector).changeStatus(argEq(callbackUrl), argEq(failed))(any())
      verifyNoMoreInteractions(mockRepo)
    }


  }


  trait TestCase {
    val succeeded = "succeeded"
    val failed = "failed"
    val permanentlyFailed = "permanently-failed"

    val forms = List("formId")

    val callbackUrl = "someUrl"

    val mockPreferencesConnector = mock[PreferencesConnector]
    val mockEntityResolverConnector = mock[EntityResolverConnector]
    val mockRepo = mock[UpdatedPrintSuppressionsRepository]

    val preferencesProcessor = new PreferencesProcessor {
      val preferencesConnector: PreferencesConnector = mockPreferencesConnector
      val entityResolverConnector: EntityResolverConnector = mockEntityResolverConnector
      val repo = mockRepo

      def formIds: List[String] = forms
    }

    val randomUtr = Generate.utr
    val randomNino = Generate.nino
    val randomEntityId = Generate.entityId

    val pulledItem = PulledItem(randomEntityId, true, DateTimeUtils.now.minusMinutes(10), "someUrl")
    val printPreference = PrintPreference(randomUtr.value, "sautr", forms)

    val entity = Entity(randomEntityId, randomUtr)
  }


}
