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

package uk.gov.hmrc.ups.scheduled

import org.joda.time.{DateTime, LocalDate}
import play.api.http.Status._
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.{Logger, Play}
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.ups.connectors.{EntityResolverConnector, PreferencesConnector}
import uk.gov.hmrc.ups.model.{PrintPreference, PulledItem}
import uk.gov.hmrc.ups.repository.{MongoCounterRepository, UpdatedPrintSuppressions, UpdatedPrintSuppressionsRepository}
import uk.gov.hmrc.workitem
import uk.gov.hmrc.workitem.ProcessingStatus

import scala.concurrent.{ExecutionContext, Future}
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.http.HeaderCarrier

trait PreferencesProcessor {

  def formIds: List[String]

  def entityResolverConnector: EntityResolverConnector

  def preferencesConnector: PreferencesConnector

  def repo(implicit executionContext: ExecutionContext): UpdatedPrintSuppressionsRepository

  def run(implicit hc: HeaderCarrier): Future[TotalCounts] = {
    def incrementOnFailure(totals: TotalCounts): TotalCounts =
      totals.copy(
        processed = totals.processed + 1,
        failed = totals.failed + 1
      )

    Enumerator.generateM(preferencesConnector.pullWorkItem).
      run(
        Iteratee.foldM(TotalCounts(0, 0)) { (accumulator, item) =>
          processUpdates(item).map {
              case Succeeded(_) =>
                accumulator.copy(processed = accumulator.processed + 1)

              case Failed(msg, ex) =>
                ex.fold(Logger.warn(msg)) { Logger.warn(msg, _) }
                incrementOnFailure(accumulator)
            }.
            recoverWith { case ex =>
              Logger.error(s"terminating early due to unexpected failure", ex)
              Future.failed(EarlyTermination(incrementOnFailure(accumulator)))
            }
        }
      ).recover { case EarlyTermination(totals) => totals}
  }

  def processUpdates(item: PulledItem)(implicit hc: HeaderCarrier): Future[ProcessingResult] =
    entityResolverConnector.getTaxIdentifiers(item.entityId).flatMap {
      case Right(Some(entity)) =>
        entity.taxIdentifiers.
          collectFirst[SaUtr] { case utr: SaUtr => utr }.
          fold(updatePreference(item.callbackUrl, workitem.Succeeded)) { utr =>
            insertAndUpdate(createPrintPreference(utr, item), item.callbackUrl, item.updatedAt)
          }

      case x =>
        val status = if (x.isRight) workitem.PermanentlyFailed else workitem.Failed
        preferencesConnector.changeStatus(item.callbackUrl, status).map {
          case OK => Failed(
            s"marked preference with entity id [${item.entityId} ] as ${status.name}"
          )
          case notOk =>
            val msg =
              s"""could not change status to $status for entity id = ${item.entityId}
                  |response status code was $notOk"""".stripMargin

            Failed(msg)
        }
    }

  def insertAndUpdate(printPreference: PrintPreference, callbackUrl: String, updatedAt: DateTime)
                     (implicit hc: HeaderCarrier): Future[ProcessingResult] =
    repo.insert(printPreference, updatedAt).
      flatMap { _ => updatePreference(callbackUrl, workitem.Succeeded) }.
      recoverWith { case ex =>
        preferencesConnector.changeStatus(callbackUrl, workitem.Failed).map { _ =>
          Failed(s"failed to include $printPreference in updated print suppressions", Some(ex))
        }
      }

  def updatePreference(callbackUrl: String, status: ProcessingStatus)(implicit hc: HeaderCarrier): Future[ProcessingResult] =
    preferencesConnector.changeStatus(callbackUrl, status).
      map {
        case status @ (OK | CONFLICT) =>
          Succeeded(s"updated preference: $callbackUrl")

        case status =>
          Failed(s"failed to update preference: url: $callbackUrl status: $status")
      }

  def createPrintPreference(utr: SaUtr, item: PulledItem) =
    PrintPreference(utr.value, "utr", if (item.paperless) formIds else List.empty)
}

object PreferencesProcessor extends PreferencesProcessor with MongoDbConnection {
  val connectionOnce = db()

  lazy val formIds: List[String] =
    Play.current.configuration.getStringSeq("form-types.saAll").
      getOrElse(throw new RuntimeException(s"configuration property form-types is not set")).
      toList

  def preferencesConnector: PreferencesConnector = PreferencesConnector


  def repo(implicit executionContext: ExecutionContext): UpdatedPrintSuppressionsRepository = {
    val collection: JSONCollection = connectionOnce.collection[JSONCollection](UpdatedPrintSuppressions.repoNameTemplate( LocalDate.now()))

    new UpdatedPrintSuppressionsRepository(
      LocalDate.now(),
      MongoCounterRepository(),
      Some(collection)
    )
  }

  def entityResolverConnector: EntityResolverConnector = EntityResolverConnector
}

sealed trait UpsResult extends Product with Serializable
final case class TotalCounts(processed: Int, failed: Int) extends UpsResult
final case class EarlyTermination(totals: TotalCounts) extends RuntimeException with UpsResult
