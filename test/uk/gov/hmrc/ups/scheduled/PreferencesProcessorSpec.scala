/*
 * Copyright 2017 HM Revenue & Customs
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

import org.joda.time.DateTime
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

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.workitem

class PreferencesProcessorSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  implicit val hc = HeaderCarrier()

  "Process outstanding updates" should {
    "succeed when an updated preference is resolved and stored" in new TestCase {
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).
        thenReturn(Future.successful(Right(Some(entity))))
      when(mockRepo.insert(argEq(printPreference), any())(any())).
        thenReturn(Future.successful(()))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, workitem.Succeeded)).
        thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates(pulledItem).futureValue shouldBe Succeeded(s"updated preference: $callbackUrl")

      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verify(mockRepo).insert(argEq(printPreference), any())(any())
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, workitem.Succeeded)
    }

    "succeed when the user opted out digital" in new TestCase {
      val optedOut = printPreference.copy(formIds = List.empty)

      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).
        thenReturn(Future.successful(Right(Some(entity))))
      when(mockRepo.insert(argEq(optedOut), any())(any())).
        thenReturn(Future.successful(()))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, workitem.Succeeded)).thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates(pulledItem.copy(paperless = false)).
        futureValue shouldBe Succeeded(s"updated preference: $callbackUrl")

      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verify(mockRepo).insert(argEq(optedOut), any())(any())
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, workitem.Succeeded)
    }

    "mark preferences status as permanently failed when no entity found for the given entityId" in new TestCase {
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).
        thenReturn(Future.successful(Right(None)))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, workitem.PermanentlyFailed)).
        thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates(pulledItem.copy(paperless = false)).
        futureValue shouldBe Failed(
          s"marked preference with entity id [${pulledItem.entityId} ] as permanently-failed"
        )

      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verifyZeroInteractions(mockRepo)
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, workitem.PermanentlyFailed)
    }

    "mark preferences status as failed if there is any error communicated with entity resolver" in new TestCase {
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).
        thenReturn(Future.successful(Left(BAD_GATEWAY)))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, workitem.Failed)).thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates(pulledItem.copy(paperless = false)).
        futureValue shouldBe Failed(
          s"marked preference with entity id [${pulledItem.entityId} ] as failed"
        )

      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verifyZeroInteractions(mockRepo)
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, workitem.Failed)
    }

    "mark preference status as succeeded when nino-only user is found in entity-resolver" in new TestCase {
      when(mockEntityResolverConnector.getTaxIdentifiers(pulledItem.entityId)).thenReturn(Future.successful(Right(Some(ninoOnlyEntity))))
      when(mockPreferencesConnector.changeStatus(pulledItem.callbackUrl, workitem.Succeeded)).thenReturn(Future.successful(OK))

      preferencesProcessor.processUpdates(pulledItem).futureValue shouldBe Succeeded(s"updated preference: $callbackUrl")

      verify(mockEntityResolverConnector).getTaxIdentifiers(pulledItem.entityId)
      verifyZeroInteractions(mockRepo)
      verify(mockPreferencesConnector).changeStatus(pulledItem.callbackUrl, workitem.Succeeded)
    }
  }

  "insertAndUpdate" should {

    "be considered failed when the record is inserted into UPS repo and the status of the updated preference is not ok" in new TestCase {
      when(mockRepo.insert(argEq(printPreference), any())(any())).
        thenReturn(Future.successful(()))
      when(mockPreferencesConnector.changeStatus(argEq(callbackUrl), argEq(workitem.Succeeded))(any())).
        thenReturn(Future.successful(BAD_REQUEST))

      preferencesProcessor.insertAndUpdate(printPreference, callbackUrl, DateTime.now()).
        futureValue shouldBe Failed(s"failed to update preference: url: $callbackUrl status: ${BAD_REQUEST}")

      verify(mockRepo).insert(argEq(printPreference), any())(any())
      verify(mockPreferencesConnector).changeStatus(argEq(callbackUrl), argEq(workitem.Succeeded))(any())
    }


    "be considered failed when the record cannot be inserted into the UPS repo" in new TestCase {
      when(mockRepo.insert(argEq(printPreference), any())(any())).thenReturn(Future.failed(new RuntimeException()))
      when(mockPreferencesConnector.changeStatus(argEq(callbackUrl), argEq(workitem.Failed))(any())).thenReturn(Future.successful(OK))

      preferencesProcessor.insertAndUpdate(printPreference, callbackUrl, DateTime.now()).
        futureValue match {
          case Failed(msg, _) =>
            msg shouldBe s"failed to include $printPreference in updated print suppressions"
          case _ => fail("should never happen")
        }

      verify(mockRepo).insert(argEq(printPreference), any())(any())
      verify(mockPreferencesConnector).changeStatus(argEq(callbackUrl), argEq(workitem.Failed))(any())
      verifyNoMoreInteractions(mockRepo)
    }
  }


  trait TestCase {

    val forms = List("formId")

    val callbackUrl = "someUrl"

    val mockPreferencesConnector = mock[PreferencesConnector]
    val mockEntityResolverConnector = mock[EntityResolverConnector]
    val mockRepo = mock[UpdatedPrintSuppressionsRepository]

    val preferencesProcessor = new PreferencesProcessor {
      val preferencesConnector: PreferencesConnector = mockPreferencesConnector
      val entityResolverConnector: EntityResolverConnector = mockEntityResolverConnector
      def repo(implicit executionContext: ExecutionContext) = mockRepo

      def formIds: List[String] = forms
    }

    val randomUtr = Generate.utr
    val randomNino = Generate.nino
    val randomEntityId = Generate.entityId

    val pulledItem = PulledItem(randomEntityId, true, DateTimeUtils.now.minusMinutes(10), "someUrl")
    val printPreference = PrintPreference(randomUtr.value, "utr", forms)

    val entity = Entity(randomEntityId, randomUtr)
    val ninoOnlyEntity = Entity(randomEntityId, randomNino)
  }


}
