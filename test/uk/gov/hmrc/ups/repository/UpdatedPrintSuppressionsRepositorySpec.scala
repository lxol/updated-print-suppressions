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
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.ups.model.PrintPreference

import scala.concurrent.{Future, ExecutionContext}

class UpdatedPrintSuppressionsRepositorySpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures {
  implicit val hc = HeaderCarrier()

  private val TODAY: LocalDate = new LocalDate()

  def counterRepoStub = new CounterRepository {
    var counter: Int = -1

    override def next(implicit ec: ExecutionContext): Future[Int] = {
      counter = counter + 1
      Future(counter)(ec)
    }
  }

  override def beforeEach(): Unit = {
    new UpdatedPrintSuppressionsRepository(TODAY, _ => counterRepoStub).collection.drop()
    new MongoCounterRepository(TODAY.toString("yyyyMMdd")).removeAll()
  }

  "UpdatedPrintSuppressionsRepository" should {

    "increment the counter and save the updated print suppression" in {
      val repository = new UpdatedPrintSuppressionsRepository(TODAY, _ => counterRepoStub)

      val ppOne = PrintPreference("11111111", "someType", List.empty)
      val ppTwo = PrintPreference("22222222", "someType", List.empty)

      await(repository.insert(ppOne))
      await(repository.insert(ppTwo))

      val all = repository.findAll()
      await(all) shouldBe List(UpdatedPrintSuppressions(all.head._id, 0, ppOne), UpdatedPrintSuppressions(all.last._id, 1, ppTwo))
    }

    "find and return all records within a range" in {

      val repository = new UpdatedPrintSuppressionsRepository(TODAY, _ => counterRepoStub)
      0 to 9 foreach(n => await(repository.insert(PrintPreference(s"id_$n","a type", List.empty))))
      repository.find(0, 2).futureValue shouldBe List(
        PrintPreference("id_0","a type", List.empty),
        PrintPreference("id_1","a type", List.empty)
      )
    }
  }

  "The counter repository" should {

    "initialise to zero" in {
      val repository = new MongoCounterRepository("test-counter")
      val counter: Counter = repository.findAll().head
      counter.value shouldBe 0
      counter.name shouldBe "test-counter"
    }

    "initialise to zero only if the value doesnt exist already" in {
      val repositoryT0 = new MongoCounterRepository("test-counter")
      await(repositoryT0.next)
      val repositoryT1 = new MongoCounterRepository("test-counter")
      val counter: Counter = repositoryT1.findAll().head
      counter.value shouldBe 1
      counter.name shouldBe "test-counter"
    }

    "increment and return the next value" in {
      val repository = new MongoCounterRepository("test-counter")
      await(repository.next)
      await(repository.next)

      repository.count.futureValue shouldBe 1
      val counter: Counter = repository.findAll().head
      counter.value shouldBe 2
      counter.name shouldBe "test-counter"
    }
  }

}
