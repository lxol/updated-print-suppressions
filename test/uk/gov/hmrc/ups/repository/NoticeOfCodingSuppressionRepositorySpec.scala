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

package uk.gov.hmrc.ups.repository

import org.joda.time.{DateTime, Duration}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, LoneElement, OptionValues}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.utils.Generate
import uk.gov.hmrc.workitem._

import scala.concurrent.ExecutionContext.Implicits.global

class NoticeOfCodingSuppressionRepositorySpec extends UnitSpec
   with LoneElement
   with MongoSpecSupport
   with ScalaFutures
   with OptionValues
   with IntegrationPatience
   with BeforeAndAfterAll {

  spec =>

    trait Setup {

        val nino = Generate.nino

        val currentTime = DateTimeUtils.now

        val preferenceUpdatedAt = DateTimeUtils.now.minusHours(1)

        val suppressibleNotice = NoticeOfCodingSuppression(nino, preferenceUpdatedAt)

        val individualRepo = TestRepo()

        await(individualRepo.removeAll())

        implicit val hc = HeaderCarrier()

        case class TestRepo() extends NoticeOfCodingSuppressionRepository {
          def mongo = spec.mongo

          override lazy val inProgressRetryAfter = Duration.standardMinutes(59)

          override def withCurrentTime[A](f: (DateTime) => A) = f(currentTime)

          override def now: DateTime = currentTime
        }
    }

  "set Suppressible" should {

    "be created with ToDo status" in new Setup {
      individualRepo.setNoticeOfCodingSuppressible(suppressibleNotice).futureValue should be(true)
      val saved = individualRepo.findAll().futureValue.loneElement

      saved.status should be(ToDo)
      saved.failureCount should be (0)
      saved.receivedAt should be (currentTime)
      saved.updatedAt should be (currentTime)
      saved.item.nino should be (nino)
      saved.item.preferenceLastUpdated should be (preferenceUpdatedAt)
    }

    "only be created once per preferenceId" in new Setup {
      individualRepo.setNoticeOfCodingSuppressible(suppressibleNotice).futureValue should be(true)

      val updatedSuppressible = suppressibleNotice.copy(preferenceLastUpdated = currentTime)
      individualRepo.setNoticeOfCodingSuppressible(updatedSuppressible).futureValue should be(true)

      val saved = individualRepo.findAll().futureValue.loneElement

      saved.status should be(ToDo)
      saved.updatedAt should be(currentTime)
      saved.item should be(updatedSuppressible)
    }

    "not update the status when change to suppressible has already been processed" in new Setup {
      individualRepo.setNoticeOfCodingSuppressible(suppressibleNotice).futureValue should be(true)

      val id = individualRepo.findAll().futureValue.loneElement.id
      individualRepo.markAs(id, Succeeded)

      val updatedSuppressible = suppressibleNotice.copy(preferenceLastUpdated = currentTime)
      individualRepo.setNoticeOfCodingSuppressible(updatedSuppressible).futureValue should be(true)

      val saved = individualRepo.findAll().futureValue.loneElement

      saved.status should be(Succeeded)
      saved.updatedAt should be(currentTime)
      saved.item should be(updatedSuppressible)
    }

    "not update the suppressible notification if the preference lastUpdated is after the recorder one" in new Setup {
      individualRepo.setNoticeOfCodingSuppressible(suppressibleNotice).futureValue should be(true)

      val updatedSuppressible = suppressibleNotice.copy(preferenceLastUpdated = preferenceUpdatedAt.minusHours(1))
      individualRepo.setNoticeOfCodingSuppressible(updatedSuppressible).futureValue should be(false)
    }

  }

  "delete Suppressible" should {

    "delete when preference lastUpdated is before the recorded one" in new Setup {
      individualRepo.setNoticeOfCodingSuppressible(suppressibleNotice).futureValue should be(true)

      individualRepo.findAll().futureValue.loneElement.item.nino should be (suppressibleNotice.nino)

      individualRepo.deleteNoticeOfCodingSuppressible(suppressibleNotice.copy(preferenceLastUpdated = currentTime)).futureValue should be (true)

      individualRepo.findAll().futureValue should be (empty)
    }

    "not delete when preference lastUpdated is after the recorded one" in new Setup {
      individualRepo.setNoticeOfCodingSuppressible(suppressibleNotice).futureValue should be(true)

      individualRepo.findAll().futureValue.loneElement.item.nino should be (suppressibleNotice.nino)

      individualRepo.deleteNoticeOfCodingSuppressible(suppressibleNotice.copy(preferenceLastUpdated = suppressibleNotice.preferenceLastUpdated.minusHours(1))).futureValue should be (false)
    }
  }

  "indexes" should {
    "be created by ensureIndexes" in new Setup {
      individualRepo.collection.indexesManager.dropAll().futureValue
      individualRepo.ensureIndexes.futureValue
      individualRepo.collection.indexesManager.list().futureValue.size should be (4)
    }
  }


}
