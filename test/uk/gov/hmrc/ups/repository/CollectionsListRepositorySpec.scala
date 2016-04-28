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

import org.joda.time.LocalDate
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CollectionsListRepositorySpec extends UnitSpec with MongoSpecSupport with ScalaFutures with BeforeAndAfterAll {

  override def beforeAll() = {
    super.beforeAll()
    await(mongo().drop())
  }

  "collections list repo" should {
    "return a list of UPS collections" in {
      val today = LocalDate.now()

      val upsCollectionName1 = UpdatedPrintSuppressions.repoNameTemplate(today)
      val upsCollectionName2 = UpdatedPrintSuppressions.repoNameTemplate(today.minusDays(1))
      val counters = "counters"

      val document = BSONDocument("test" -> "1")
      await(
        Future.sequence(
          List(
            bsonCollection(upsCollectionName1)().insert(document),
            bsonCollection(upsCollectionName2)().insert(document),
            bsonCollection(counters)().insert(document)
          )
        )
      )

      new CollectionsListRepository().upsCollectionNames.futureValue should contain only(upsCollectionName1, upsCollectionName2)
    }
  }

  "drop collection and return true if successful" in {
    val document = BSONDocument("test" -> "1")
    await(
      Future.sequence(
        List(
          bsonCollection("db-1")().insert(document)
        )
      )
    )
    await(new CollectionsListRepository().dropCollection("db-1"))

    mongo().collectionNames.futureValue should not contain ("db-1")

  }
}
