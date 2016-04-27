package uk.gov.hmrc.ups.service

import org.joda.time.LocalDate
import uk.gov.hmrc.ups.repository.{UpdatedPrintSuppressions, CollectionsListRepository, UpdatedPrintSuppressionsRepository}

import scala.concurrent.Future
import scala.concurrent.duration._

final case class RemoveOlderCollections(listCollections: () => Future[List[String]],
                                        expireCollection: String => Future[Int]) {

  def removeOlderThan(days: Duration): Future[Int] = {
    def extractDateFromCollection(value: String):
    val now = LocalDate.now()
    listCollections().map(
      _.filter (_.endsWith(UpdatedPrintSuppressions.toString(now)))
    )
  }


}


