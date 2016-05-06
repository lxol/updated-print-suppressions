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

  "processWorkItem" should {
    "always indicate that the pull failed when presented with a non-200 level status code" in new TestCase {
      preferencesProcessor.processWorkItem.
        apply(Left(400)).futureValue shouldBe Failed(
          "Pull from preferences failed with status code = 400", None
      )
    }
  }

  "Process outstanding updates" should {
    "succeed when an updated preference is resolved and stored" in new TestCase {
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).
        thenReturn(Future.successful(Right(Some(entity))))
      when(mockRepo.insert(argEq(printPreference))(any())).
        thenReturn(Future.successful(()))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, succeeded)).
        thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates(pulledItem).futureValue shouldBe Succeeded(
        s"copied data from preferences with utr = ${randomUtr.value}"
      )

      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verify(mockRepo).insert(argEq(printPreference))(any())
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, succeeded)
    }

    "succeed when the user opted out digital" in new TestCase {
      val optedOut = printPreference.copy(formIds = List.empty)

      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).
        thenReturn(Future.successful(Right(Some(entity))))
      when(mockRepo.insert(argEq(optedOut))(any())).
        thenReturn(Future.successful(()))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, succeeded)).thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates(pulledItem.copy(paperless = false)).
        futureValue shouldBe Succeeded(
          s"copied data from preferences with utr = ${randomUtr.value}"
        )

      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verify(mockRepo).insert(argEq(optedOut))(any())
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, succeeded)
    }

    "mark preferences status as permanently failed when no entity found for the given entityId" in new TestCase {
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).
        thenReturn(Future.successful(Right(None)))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, permanentlyFailed)).
        thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates(pulledItem.copy(paperless = false)).
        futureValue shouldBe Failed(
          s"marked preference with entity id [${pulledItem.entityId} ] as permanently-failed"
        )

      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verifyZeroInteractions(mockRepo)
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, permanentlyFailed)
    }

    "mark preferences status as failed if there is any error communicated with entity resolver" in new TestCase {
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).
        thenReturn(Future.successful(Left(BAD_GATEWAY)))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, failed)).thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates(pulledItem.copy(paperless = false)).
        futureValue shouldBe Failed(
          s"marked preference with entity id [${pulledItem.entityId} ] as failed"
        )

      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verifyZeroInteractions(mockRepo)
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, failed)
    }

    "mark preference status as succeeded when nino-only user is found in entity-resolver" in new TestCase {
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).thenReturn(Future.successful(Right(Some(ninoOnlyEntity))))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, succeeded)).thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates(pulledItem).futureValue shouldBe Succeeded(
        s"copied data from preferences with utr = []"
      )

      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verifyZeroInteractions(mockRepo)
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, succeeded)
    }
  }

  "insertAndUpdate" should {

    // POSSIBLE DUPLICATE -> only conflict status code differs really
    "succeed when the record is inserted into UPS repo and the status of the preference has been updated externally" in new TestCase {
      when(mockRepo.insert(argEq(printPreference))(any())).thenReturn(Future.successful(()))
      when(mockPreferencesConnector.changeStatus(argEq(callbackUrl), argEq(succeeded))(any())).
        thenReturn(Future.successful(CONFLICT))

      preferencesProcessor.insertAndUpdate(randomUtr, forms, callbackUrl).
        futureValue shouldBe Succeeded(s"copied data from preferences with utr = ${randomUtr.value}")

      verify(mockRepo).insert(argEq(printPreference))(any())
      verify(mockPreferencesConnector).changeStatus(argEq(callbackUrl), argEq(succeeded))(any())
    }

    // NO TEST ABOVE
    "be considered failed when the record is inserted into UPS repo and the status of the updated preference is not ok" in new TestCase {
      when(mockRepo.insert(argEq(printPreference))(any())).
        thenReturn(Future.successful(()))
      when(mockPreferencesConnector.changeStatus(argEq(callbackUrl), argEq(succeeded))(any())).
        thenReturn(Future.successful(BAD_REQUEST))
      when(mockRepo.removeByUtr(printPreference.id)).thenReturn(Future.successful(true))

      preferencesProcessor.insertAndUpdate(randomUtr, forms, callbackUrl).
        futureValue shouldBe Failed(
          s"failed to update status in preferences for utr = ${randomUtr.value}"
        )

      verify(mockRepo).insert(argEq(printPreference))(any())
      verify(mockPreferencesConnector).changeStatus(argEq(callbackUrl), argEq(succeeded))(any())
      verify(mockRepo).removeByUtr(printPreference.id)
    }


    "be considered failed when the record cannot be inserted into the UPS repo" in new TestCase {
      when(mockRepo.insert(argEq(printPreference))(any())).thenReturn(Future.failed(new RuntimeException()))
      when(mockPreferencesConnector.changeStatus(argEq(callbackUrl), argEq(failed))(any())).thenReturn(Future.successful(OK))

      preferencesProcessor.insertAndUpdate(randomUtr, forms, callbackUrl).futureValue match {
        case Failed(msg, _) =>
          msg shouldBe s"failed to include ${randomUtr.value} in updated print suppressions"
        case _ => fail("should never happen")
      }

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
    val ninoOnlyEntity = Entity(randomEntityId, randomNino)
  }


}
