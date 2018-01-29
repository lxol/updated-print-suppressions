/*
 * Copyright 2018 HM Revenue & Customs
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

import org.joda.time.{DateTime, LocalDate}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.libs.json.{JsObject, JsValue}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.model.PrintPreference

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

class UpdatedPrintSuppressionsRepositorySpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with IntegrationPatience {
  implicit val hc = HeaderCarrier()

  private val TODAY: LocalDate = new LocalDate()

  def counterRepoStub = new CounterRepository {
    var counter: Int = -1

    override def next(counterName:String)(implicit ec: ExecutionContext): Future[Int] = {
      counter = counter + 1
      Future(counter)(ec)
    }
  }

  override def beforeEach(): Unit = {
    await(new UpdatedPrintSuppressionsRepository(TODAY, counterRepoStub).collection.remove(JsObject(Map.empty[String,JsValue])))
    await(new MongoCounterRepository().removeAll())
  }

  def toCounterAndPreference(ups: UpdatedPrintSuppressions): (Int, PrintPreference, DateTime) = (ups.counter, ups.printPreference, ups.updatedAt)

  "UpdatedPrintSuppressionsRepository" should {

    val now = DateTimeUtils.now

    "increment the counter and save the updated print suppression" in {

      val repository = new UpdatedPrintSuppressionsRepository(TODAY, counterRepoStub)

      val ppOne = PrintPreference("11111111", "someType", List.empty)
      val ppTwo = PrintPreference("22222222", "someType", List.empty)

      await(repository.insert(ppOne, now))
      await(repository.insert(ppTwo, now))

      val all = repository.findAll()
      await(all).map { toCounterAndPreference } shouldBe List((0, ppOne, now), (1, ppTwo, now))
    }

    "find and return all records within a range" in {

      val repository = new UpdatedPrintSuppressionsRepository(TODAY, counterRepoStub)
      0 to 9 foreach(n => await(repository.insert(PrintPreference(s"id_$n","a type", List.empty), now)))
      repository.find(0, 2).futureValue shouldBe List(
        PrintPreference("id_0","a type", List.empty),
        PrintPreference("id_1","a type", List.empty)
      )
    }

    "override previous update with same utr" in {
      val pp = PrintPreference("11111111", "someType", List.empty)
      val preferenceWithSameId: PrintPreference = pp.copy(formIds = List("SomeId"))

      val repository = new UpdatedPrintSuppressionsRepository(TODAY, counterRepoStub)

      await(repository.insert(pp, now))
      await(repository.insert(preferenceWithSameId, now.plusMillis(1)))

      val all = repository.findAll()
      await(all).map { toCounterAndPreference } shouldBe List((0, preferenceWithSameId, now.plusMillis(1)))
    }

    "duplicate keys due to race conditions are recoverable" in {
      val utr: String = "11111111"

      val repository = new UpdatedPrintSuppressionsRepository(TODAY, counterRepoStub)

     await(
       Future.sequence(
         List(
           repository.insert(PrintPreference(utr, "someType", List.empty), now),
           repository.insert(PrintPreference(utr, "someType", List("something")), now.plusMillis(1))
         )
       ).size
     )

      repository.findAll().map(
        _.find(_.printPreference.id == utr).map(_.printPreference.formIds)
      ).futureValue shouldBe Some(List("something"))
    }

    "not throw an duplicate key error with near simultaneous confirms" in {
      val utr: String = "11111111"
      val list = 1 until 10

      val repository = new UpdatedPrintSuppressionsRepository(TODAY, counterRepoStub)

      await(
       Future.sequence(list.map(_ =>
         repository.insert(PrintPreference(utr, "someType", List("1")), now))
       ).size
     )
      repository.findAll().map(
        _.find(_.printPreference.id == utr).map(_.printPreference.formIds)
      ).futureValue shouldBe Some(List("1"))
    }
  }

  "The counter repository" should {

    "initialise to zero" in {
      val repository = new MongoCounterRepository()
      repository.next("test-counter").futureValue shouldBe 1
      val counter: Counter = repository.findAll().head
      counter.value shouldBe 1
      counter.name shouldBe "test-counter"
    }

    "initialise to zero only if the value doesnt exist already" in {
      val repositoryT0 = new MongoCounterRepository()
      await(repositoryT0.next("test-counter"))
      val repositoryT1 = new MongoCounterRepository()
      repositoryT1.findAll().map {
        _.headOption.map(head => (head.value, head.name))
      }.futureValue shouldBe Some((1, "test-counter"))
    }

    "increment and return the next value" in {
      val repository = new MongoCounterRepository()
      await(repository.next("test-counter"))
      await(repository.next("test-counter"))

      repository.count.futureValue shouldBe 1
      repository.findAll().map {
        _.headOption.map(head => (head.value, head.name))
      }.futureValue shouldBe Some((2, "test-counter"))
    }
  }

}
