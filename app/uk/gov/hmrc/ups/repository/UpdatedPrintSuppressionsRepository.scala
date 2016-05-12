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

import org.joda.time.{DateTime, LocalDate}
import play.api.Logger
import play.api.libs.json.Json
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.ups.model.PrintPreference

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

case class UpdatedPrintSuppressions(_id: BSONObjectID,
                                    counter: Int,
                                    printPreference: PrintPreference,
                                    updatedAt: DateTime)

object UpdatedPrintSuppressions {
  implicit val idf = ReactiveMongoFormats.objectIdFormats
  implicit val pp = PrintPreference.formats
  implicit val dtf = ReactiveMongoFormats.dateTimeFormats

  implicit val formats = Json.format[UpdatedPrintSuppressions]

  val datePattern = "yyyyMMdd"

  def toString(date: LocalDate) = date.toString(datePattern)

  def repoNameTemplate(date: LocalDate) = s"updated_print_suppressions_${toString(date)}"
}

class UpdatedPrintSuppressionsRepository(date: LocalDate, repoCreator: String => CounterRepository)
                                        (implicit mongo: () => DB, ec: ExecutionContext)
  extends ReactiveRepository[UpdatedPrintSuppressions, BSONObjectID](UpdatedPrintSuppressions.repoNameTemplate(date), mongo, UpdatedPrintSuppressions.formats) {

  val counterRepo = repoCreator(UpdatedPrintSuppressions.toString(date))

  override def indexes: Seq[Index] =
    Seq(
      Index(Seq("counter" -> IndexType.Ascending), name = Some("counterIdx"), unique = true, sparse = false),
      Index(Seq("printPreference.id" -> IndexType.Ascending, "printPreference.idType" -> IndexType.Ascending), name = Some("uniquePreferenceId"), unique = true, sparse = false)
    )

  def find(offset: Long, limit: Int): Future[List[PrintPreference]] = {

    import play.api.libs.json.Json._

    val from = offset
    val to = offset + limit
    val query = Json.obj("counter" -> Json.obj("$gte" -> from), "counter" -> Json.obj("$lt" -> to))
    val e: Future[List[UpdatedPrintSuppressions]] = collection.find(query)
      .cursor[UpdatedPrintSuppressions](ReadPreference.primaryPreferred)
      .collect[List]()
    e.map(_.map(ups => ups.printPreference))
  }

  def insert(printPreference: PrintPreference, updatedAt: DateTime)(implicit ec: ExecutionContext): Future[Unit] = {
    val selector = BSONDocument("printPreference.id" -> printPreference.id, "printPreference.idType" -> printPreference.idType)
    val updatedAtJson = ReactiveMongoFormats.dateTimeWrite.writes(updatedAt)
    val updatedAtSelector = BSONDocument("updatedAt" -> Json.obj("$lte" -> updatedAtJson))

    collection.find(selector).one[UpdatedPrintSuppressions].
      flatMap {
        case Some(ups) =>
          collection.update(
            selector = BSONDocument("_id" -> ups._id) ++ updatedAtSelector,
            update = UpdatedPrintSuppressions.formats.writes(
              ups.copy(printPreference = printPreference, updatedAt = updatedAt)
            )
          ).map { _ => () }

        case None =>
          counterRepo.next.flatMap { counter =>
            collection.findAndUpdate(
              selector = selector ++ updatedAtSelector,
              update = BSONDocument(
                "$setOnInsert" -> Json.obj(
                  "_id" -> BSONObjectIDFormat.writes(BSONObjectID.generate),
                  "counter" -> counter
                ),
                "$set" -> Json.obj(
                  "updatedAt" -> updatedAtJson,
                  "printPreference" -> Json.toJson(printPreference)
                )
              ),
              upsert = true,
              fetchNewObject = false
            )
          }.
          map { _ => () }.
          recover {
            case e: RuntimeException if e.getMessage.contains("11000") =>
              Logger.warn(s"failed to insert print preference $printPreference updated at ${updatedAt.getMillis}", e)
              ()
          }
      }

  }
}

case class Counter(_id: BSONObjectID, name: String, value: Int)

object Counter {
  val formats = {
    implicit val idf = ReactiveMongoFormats.objectIdFormats
    Json.format[Counter]
  }
}

trait CounterRepository {
  def next(implicit ec: ExecutionContext): Future[Int]
}

class MongoCounterRepository private(counterName: String)(implicit mongo: () => DB)
  extends ReactiveRepository[Counter, BSONObjectID]("counters", mongo, Counter.formats, ReactiveMongoFormats.objectIdFormats) with CounterRepository {

  override def indexes: Seq[Index] =
    Seq(Index(Seq("name" -> IndexType.Ascending), name = Some("nameIdx"), unique = true, sparse = false))

  def next(implicit ec: ExecutionContext): Future[Int] = {
    collection.findAndUpdate(
      selector = BSONDocument("name" -> counterName),
      update = BSONDocument("$inc" -> BSONDocument("value" -> 1)),
      fetchNewObject = false
    ).map(_.result[Counter].getOrElse(throw new RuntimeException("No initialised counter found")).value)
  }

  private[MongoCounterRepository] def initialise(implicit ec: ExecutionContext): Future[Boolean] = {
    collection.update(
      BSONDocument("name" -> counterName),
      BSONDocument("$setOnInsert" -> Counter.formats.writes(Counter(BSONObjectID.generate, counterName, 0))),
      upsert = true
    ).map(_.ok)
  }
}

object MongoCounterRepository {
  def apply(counterName: String)(implicit ec: ExecutionContext, mongo: () => DB): MongoCounterRepository = {
    val result = new MongoCounterRepository(counterName)
    Await.result(result.initialise, 5 seconds)
    result
  }

}

