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

package uk.gov.hmrc.ups.controllers

import org.joda.time.LocalDate
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.OFormat
import play.api.test.Helpers._
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.{ MongoCounterRepository, UpdatedPrintSuppressionsRepository }

import scala.concurrent.ExecutionContextExecutor

trait TestSetup extends PlaySpec with ScalaFutures with BeforeAndAfterEach {

  val reactiveMongoComponent: ReactiveMongoComponent
  val mongoCounterRepository: MongoCounterRepository

  val today: LocalDate = LocalDate.now
  val yesterday: LocalDate = today.minusDays(1)

  val todayString: String = today.toString("yyyy-MM-dd")
  val yesterdayAsString: String = yesterday.toString("yyyy-MM-dd")

  implicit val ppFormats: OFormat[PrintPreference] = PrintPreference.formats
  implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.Implicits.global

  // Reset the counters
  await(mongoCounterRepository.removeAll())

  val repoToday = new UpdatedPrintSuppressionsRepository(reactiveMongoComponent, today, mongoCounterRepository)
  await(repoToday.removeAll())

  val repoYesterday = new UpdatedPrintSuppressionsRepository(reactiveMongoComponent, yesterday, mongoCounterRepository)
  await(repoYesterday.removeAll())

}
