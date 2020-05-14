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

import javax.inject.{ Inject, Singleton }
import play.api.Logger
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{ Index, IndexType }
import reactivemongo.bson.{ BSONDocument, BSONDocumentReader, BSONObjectID, Macros }
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class MongoCounterRepository @Inject()(mongoComponent: ReactiveMongoComponent)
    extends ReactiveRepository[Counter, BSONObjectID]("counters", mongoComponent.mongoConnector.db, Counter.formats, ReactiveMongoFormats.objectIdFormats)
    with CounterRepository {

  override def indexes: Seq[Index] =
    Seq(Index(Seq("name" -> IndexType.Ascending), name = Some("nameIdx"), unique = true, sparse = false))

  def next(counterName: String)(implicit ec: ExecutionContext): Future[Int] = {

    val update = collection.updateModifier(
      update = BSONDocument("$setOnInsert" -> BSONDocument("name" -> counterName), "$inc" -> BSONDocument("value" -> 1)),
      fetchNewObject = true,
      upsert = true
    )

    import collection.BatchCommands.FindAndModifyCommand.FindAndModifyResult
    implicit val reader: BSONDocumentReader[Counter] = Macros.reader[Counter]

    val result: Future[FindAndModifyResult] =
      collection.findAndModify(selector = BSONDocument("name" -> counterName), modifier = update)

    result.map(_.result[Counter]).map(opt => Json.toJson(opt.get).as[Counter].value)
  }
}
