package uk.gov.hmrc.ups.controllers

import org.joda.time.LocalDate
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.{UpdatedPrintSuppressionsRepository, MongoCounterRepository}


class TestSetup(override val databaseName: String = "updated-print-suppressions") extends MongoSpecSupport with ScalaFutures with UnitSpec {

  implicit val ppFormats = PrintPreference.formats
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  val today = LocalDate.now
  val yesterday = today.minusDays(1)


  val todayString = today.toString("yyyy-MM-dd")
  val yesterdayAsString = yesterday.toString("yyyy-MM-dd")

  // Reset the counters
  await(MongoCounterRepository().removeAll())

  val repoToday = new UpdatedPrintSuppressionsRepository(today, MongoCounterRepository())
  await(repoToday.removeAll())

  val repoYesterday = new UpdatedPrintSuppressionsRepository(yesterday, MongoCounterRepository())
  await(repoYesterday.removeAll())
}
