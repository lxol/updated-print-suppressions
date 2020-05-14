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
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.ups.controllers.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UpdatedPrintSuppressionsDatabaseSpec extends PlaySpec with MongoSupport with ScalaFutures with BeforeAndAfterAll with GuiceOneAppPerSuite {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(s"mongodb.uri" -> s"mongodb://localhost:27017/$databaseName", "play.http.router" -> "testOnlyDoNotUseInAppConf.Routes", "metrics.jvm" -> false)
      .overrides(play.api.inject.bind[ReactiveMongoComponent].to(testMongoComponent))
      .build()

  private val updatedPrintSuppressionsDatabase = app.injector.instanceOf[UpdatedPrintSuppressionsDatabase]
  private val today = LocalDate.now()
  private val upsCollectionName1 = UpdatedPrintSuppressions.repoNameTemplate(today)
  private val upsCollectionName2 = UpdatedPrintSuppressions.repoNameTemplate(today.minusDays(1))
  private val counters = "counters"

  override def beforeAll(): Unit = {
    super.afterAll()
    dropTestCollection(upsCollectionName1)
    dropTestCollection(upsCollectionName2)
    dropTestCollection(counters)
  }

  "collections list repo" should {
    "return a list of UPS collections" in {
      val document = BSONDocument("test" -> "1")
      await(
        Future.sequence(
          List(
            bsonCollection(upsCollectionName1)().insert(false).one(document),
            bsonCollection(upsCollectionName2)().insert(false).one(document),
            bsonCollection(counters)().insert(false).one(document)
          )
        )
      )
      updatedPrintSuppressionsDatabase.upsCollectionNames.futureValue must contain only (upsCollectionName1, upsCollectionName2)
    }
  }

  "drop collection and return true if successful" in {
    val document = BSONDocument("test" -> "1")
    await(bsonCollection("db-1")().insert(false).one(document))
    await(updatedPrintSuppressionsDatabase.dropCollection("db-1"))

    mongo().collectionNames.futureValue must not contain "db-1"
  }

  override def afterAll(): Unit = {
    super.afterAll()
    dropTestCollection(upsCollectionName1)
    dropTestCollection(upsCollectionName2)
    dropTestCollection(counters)
  }

}
