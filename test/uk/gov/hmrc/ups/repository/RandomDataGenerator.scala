/*
 * Copyright 2020 HM Revenue & Customs
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

import org.joda.time.LocalDate
import org.scalatest.DoNotDiscover
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.model.PrintPreference
import play.api.test.Helpers._
import play.modules.reactivemongo.ReactiveMongoComponent

import scala.concurrent.ExecutionContextExecutor

@DoNotDiscover
class RandomDataGenerator extends PlaySpec with GuiceOneAppPerSuite with MongoSpecSupport {

  val mongoCounterRepository = app.injector.instanceOf[MongoCounterRepository]
  val mongoComponent = app.injector.instanceOf[ReactiveMongoComponent]

  implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.Implicits.global

  val BATCH_SIZE: Int = 100000

  "RandomDataGenerator" should {

    "create 3M random records in one day" in new TestSetup {
      val repository: UpdatedPrintSuppressionsRepository =
        new UpdatedPrintSuppressionsRepository(mongoComponent, new LocalDate().minusDays(1), mongoCounterRepository)
      await(repository.removeAll())
      0 to 29 foreach { i =>
        {
          println(s"Generating records from ${i * BATCH_SIZE} to ${(i * BATCH_SIZE) + BATCH_SIZE}")
          await(repository.bulkInsert(generateBATCH_SIZEEntries(i * BATCH_SIZE)))
        }
      }
    }

    def generateBATCH_SIZEEntries(offset: Int): List[UpdatedPrintSuppressions] =
      for (n <- List.range(offset, offset + BATCH_SIZE))
        yield
          UpdatedPrintSuppressions(
            BSONObjectID.generate,
            n,
            PrintPreference(s"anId_$n", "anId", List("f1", "f2")),
            DateTimeUtils.now
          )

  }

  class TestSetup(override val databaseName: String = "updated-print-suppressions") extends MongoSpecSupport

}
