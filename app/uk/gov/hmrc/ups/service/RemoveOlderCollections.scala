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

package uk.gov.hmrc.ups.service

import org.joda.time.format.DateTimeFormat
import org.joda.time.{Duration => jDuration, _}
import play.api.Logger
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressions

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


final case class RemoveOlderCollections(listCollections: () => Future[List[String]],
                                        expireCollection: String => Future[Boolean]) extends DeleteCollectionFilter {

  def removeOlderThan(days: Duration)(implicit ec: ExecutionContext): Future[Int] = {
    listCollections().flatMap { collectionNames =>
      Future.sequence(
        collectionNames.
          filter ( filterUpsCollectionsOnly(_, days) )
          map { collectionName =>
          val result = expireCollection(collectionName)
          result.onComplete {
            case Success(_) => Logger.info(s"Successfully deleted $collectionName")
            case Failure(ex) => Logger.info(s"Failed to delete $collectionName with error", ex)
          }
          result
        }
      ).map(_.foldLeft(0)((count, value) => if (value) (count + 1) else count))
    }
  }
}


trait DeleteCollectionFilter {
  private def today: LocalDate = LocalDate.now()

  def filterUpsCollectionsOnly(collectionName: String, days: Duration): Boolean = {

    val formatter = DateTimeFormat.forPattern(UpdatedPrintSuppressions.datePattern)
    def extractDateFromCollection(value: String) = formatter.parseLocalDate(value.dropWhile(!_.isDigit))

    new jDuration(
      extractDateFromCollection(collectionName).toDateTimeAtStartOfDay,
      today.toDateTimeAtStartOfDay
    ).getStandardDays.days >= days

  }
}
