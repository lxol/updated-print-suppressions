/*
 * Copyright 2019 HM Revenue & Customs
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
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.ups.controllers.MongoSupport
import uk.gov.hmrc.ups.model.PrintPreference

import scala.concurrent.{ExecutionContext, Future}

class UpdatedPrintSuppressionsRepositorySpec extends PlaySpec with MongoSupport with BeforeAndAfterEach with ScalaFutures
  with IntegrationPatience with GuiceOneAppPerSuite {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private val TODAY: LocalDate = new LocalDate()

  def counterRepoStub: MongoCounterRepository = new MongoCounterRepository(testMongoComponent){
    var counter: Int = -1

    override def next(counterName:String)(implicit ec: ExecutionContext): Future[Int] = {
      counter = counter + 1
      Future(counter)(ec)
    }
  }

  override def beforeEach(): Unit = {
    await(new UpdatedPrintSuppressionsRepository(testMongoComponent, TODAY, counterRepoStub).remove())
    await(new MongoCounterRepository(testMongoComponent).removeAll())
  }

  def toCounterAndPreference(ups: UpdatedPrintSuppressions): (Int, PrintPreference, DateTime) = (ups.counter, ups.printPreference, ups.updatedAt)

  "UpdatedPrintSuppressionsRepository" should {

    val now = DateTimeUtils.now

    "increment the counter and save the updated print suppression" in {

      val repository = new UpdatedPrintSuppressionsRepository(testMongoComponent, TODAY, counterRepoStub)

      val ppOne = PrintPreference("11111111", "someType", List.empty)
      val ppTwo = PrintPreference("22222222", "someType", List.empty)

      await(repository.insert(ppOne, now))
      await(repository.insert(ppTwo, now))

      val all = repository.findAll()
      await(all).map { toCounterAndPreference } mustBe List((0, ppOne, now), (1, ppTwo, now))
    }

    "find and return all records within a range" in {

      val repository = new UpdatedPrintSuppressionsRepository(testMongoComponent, TODAY, counterRepoStub)
      0 to 9 foreach(n => await(repository.insert(PrintPreference(s"id_$n","a type", List.empty), now)))
      repository.find(0, 2).futureValue mustBe List(
        PrintPreference("id_0","a type", List.empty),
        PrintPreference("id_1","a type", List.empty)
      )
    }

    "override previous update with same utr" in {
      val pp = PrintPreference("11111111", "someType", List.empty)
      val preferenceWithSameId: PrintPreference = pp.copy(formIds = List("SomeId"))

      val repository = new UpdatedPrintSuppressionsRepository(testMongoComponent, TODAY, counterRepoStub)

      await(repository.insert(pp, now))
      await(repository.insert(preferenceWithSameId, now.plusMillis(1)))

      val all = repository.findAll()
      await(all).map { toCounterAndPreference } mustBe List((0, preferenceWithSameId, now.plusMillis(1)))
    }

    "duplicate keys due to race conditions are recoverable" in {
      val utr: String = "11111111"

      val repository = new UpdatedPrintSuppressionsRepository(testMongoComponent, TODAY, counterRepoStub)

     await(
       Future.sequence(
         List(
           repository.insert(PrintPreference(utr, "someType", List.empty), now),
           repository.insert(PrintPreference(utr, "someType", List("something")), now.plusMillis(1))
         )
       )
     )

      repository.findAll().map(
        _.find(_.printPreference.id == utr).map(_.printPreference.formIds)
      ).futureValue mustBe Some(List("something"))
    }

    "not throw an duplicate key error with near simultaneous confirms" in {
      val utr: String = "11111111"
      val list = 1 until 10

      val repository = new UpdatedPrintSuppressionsRepository(testMongoComponent, TODAY, counterRepoStub)

      await(
       Future.sequence(list.map(_ =>
         repository.insert(PrintPreference(utr, "someType", List("1")), now))
       )
      )
      repository.findAll().map(
        _.find(_.printPreference.id == utr).map(_.printPreference.formIds)
      ).futureValue mustBe Some(List("1"))
    }
  }

  "The counter repository" should {

    "initialise to zero" in {
      val repository = new MongoCounterRepository(testMongoComponent)
      repository.next("test-counter").futureValue mustBe 1
      val counter: Counter = await(repository.findAll()).head
      counter.value mustBe 1
      counter.name mustBe "test-counter"
    }

    "initialise to zero only if the value doesnt exist already" in {
      val repositoryT0 = new MongoCounterRepository(testMongoComponent)
      await(repositoryT0.next("test-counter"))
      val repositoryT1 = new MongoCounterRepository(testMongoComponent)
      repositoryT1.findAll().map {
        _.headOption.map(head => (head.value, head.name))
      }.futureValue mustBe Some((1, "test-counter"))
    }

    "increment and return the next value" in {
      val repository = new MongoCounterRepository(testMongoComponent)
      await(repository.next("test-counter"))
      await(repository.next("test-counter"))

      repository.count.futureValue mustBe 1
      repository.findAll().map {
        _.headOption.map(head => (head.value, head.name))
      }.futureValue mustBe Some((2, "test-counter"))
    }
  }

}
