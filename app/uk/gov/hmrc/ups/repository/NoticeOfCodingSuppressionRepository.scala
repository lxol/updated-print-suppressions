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

import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.objectIdFormats
import uk.gov.hmrc.play.config.RunMode
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.workitem._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.{ExecutionContext, Future}

case class NoticeOfCodingSuppression(nino: Nino, preferenceLastUpdated: DateTime)

object NoticeOfCodingSuppression {
  implicit val formatObjectId = reactivemongo.json.BSONFormats.BSONObjectIDFormat
  implicit val dateFormats = ReactiveMongoFormats.dateTimeFormats

  implicit val payeSuppressionItemFormat: Format[NoticeOfCodingSuppression] = Json.format[NoticeOfCodingSuppression]
}

object NoticeOfCodingSuppressionRepository extends MongoDbConnection {
  def apply(): NoticeOfCodingSuppressionRepository = new NoticeOfCodingSuppressionRepository {
    override def now: DateTime = DateTimeUtils.now
  }
}

abstract class NoticeOfCodingSuppressionRepository(implicit mongo: () => DB)
  extends WorkItemRepository[NoticeOfCodingSuppression, BSONObjectID]("noticeOfCodingSuppression", mongo, WorkItem.workItemMongoFormat[NoticeOfCodingSuppression])
  with PayeSuppressibleStatistics
  with RunMode {

  lazy val inProgressRetryAfterProperty = s"$env.suppressions.retryInProgressAfter"

  lazy val workItemFields = new WorkItemFieldNames {
    val receivedAt = "receivedAt"
    val updatedAt = "updatedAt"
    val availableAt = "availableAt"
    val status = "status"
    val id = "_id"
    val failureCount = "failureCount"
  }

  implicit val bsonObjectFormat = reactivemongo.json.BSONFormats.BSONObjectIDFormat
  implicit val dateFormats = ReactiveMongoFormats.dateTimeFormats

  override def indexes: Seq[Index] = super.indexes ++
       Seq(Index(Seq("item.preferenceId" -> IndexType.Ascending), unique = true, background = true))

  def deleteNoticeOfCodingSuppressible(noticeOfCodingSuppression: NoticeOfCodingSuppression)(implicit ec: ExecutionContext): Future[Boolean] =
    collection.remove(Json.obj(
        "item.nino" -> noticeOfCodingSuppression.nino,
        "item.preferenceLastUpdated" -> Json.obj("$lt" -> noticeOfCodingSuppression.preferenceLastUpdated))
    ).map(_.n > 0)

  def setNoticeOfCodingSuppressible(noticeOfCodingSuppression: NoticeOfCodingSuppression)(implicit ec: ExecutionContext): Future[Boolean] = {

    def updateFields : JsValueWrapper = Json.obj("item" -> noticeOfCodingSuppression, "updatedAt" -> now)

    def insertFields : JsValueWrapper = Json.obj("receivedAt" -> now, "availableAt" -> now, "failureCount" -> 0, "status" -> ToDo)

    def suppressibleUpdate() = Json.obj("$set" -> updateFields, "$setOnInsert" -> insertFields)

    collection.update(
      selector = Json.obj("item.nino" -> noticeOfCodingSuppression.nino, "item.preferenceLastUpdated" -> Json.obj("$lt" -> noticeOfCodingSuppression.preferenceLastUpdated)),
      update = suppressibleUpdate(),
      upsert = true
    ).map( updateWriteResult => updateWriteResult.n > 0).recover {
      case e => false
    }
  }


  //TODO: Do we need this here?
  def pullOutstandingItems(failedBefore : DateTime, availableBefore : DateTime) (implicit ec: HeaderCarrier): Future[Option[WorkItem[NoticeOfCodingSuppression]]] =  {
    super.pullOutstanding(failedBefore,availableBefore)
  }

  def initialState: NoticeOfCodingSuppression => ProcessingStatus = _ => ToDo
}


trait PayeSuppressibleStatistics {
  self: NoticeOfCodingSuppressionRepository =>

  val StatusZeroCounts = ProcessingStatus.processingStatuses.map(_ -> 0).toMap

  val processingStatusMap: Reads[(ProcessingStatus, Int)] = (
    (__ \ "_id").read[ProcessingStatus] and
    (__ \ "count").read[Int]
  ).tupled

  def countByStatus(implicit hc: HeaderCarrier): Future[Map[ProcessingStatus, Int]] = {
    import reactivemongo.json.collection.JSONBatchCommands._
    collection.runCommand(
      AggregationFramework.Aggregate(
        Seq(AggregationFramework.GroupField("status")("count" -> AggregationFramework.SumValue(1)))
      )
    ).map(result => StatusZeroCounts ++ result.documents.toSeq.map(Json.toJson(_).as(processingStatusMap)))
  }
}
