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

package uk.gov.hmrc.ups.scheduled

import org.joda.time.LocalDate
import play.api.{Play, Logger}
import play.api.http.Status._
import play.api.libs.iteratee.{ Enumerator, Iteratee }
import play.modules.reactivemongo.ReactiveMongoPlugin
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.ups.connectors.{EntityResolverConnector, PreferencesConnector}
import uk.gov.hmrc.ups.model.{PulledItem, PrintPreference}
import uk.gov.hmrc.ups.repository.{MongoCounterRepository, UpdatedPrintSuppressionsRepository}

import scala.concurrent.Future



trait PreferencesProcessor {

  def formIds: List[String]

  def entityResolverConnector: EntityResolverConnector

  def preferencesConnector: PreferencesConnector

  def repo: UpdatedPrintSuppressionsRepository

  def run(implicit hc: HeaderCarrier): Future[TotalCounts] =
    Enumerator.generateM(preferencesConnector.pullWorkItem).
      run(
        Iteratee.foldM(TotalCounts(0, 0)) { (accumulator, pulledWorkItemResult) =>
          processWorkItem.apply(pulledWorkItemResult).map {
            case Succeeded(_) =>
              accumulator.copy(processed = accumulator.processed + 1)

            case Failed(_,_) =>
              accumulator.copy(failed = accumulator.failed + 1)
          }
        }
      )

  type PulledWorkItemResult = Either[Int, PulledItem]

  def processWorkItem(implicit hc: HeaderCarrier): PulledWorkItemResult => Future[ProcessingState] = {
    // TODO: for now continue when we get a non-200 level error from prefs, but is this what we really want to do?
    case Left(error) => Future.successful(
      Failed(s"Pull from preferences failed with status code = $error")
    )

    case Right(item) => processUpdates(item).map { _ =>
      Succeeded(s"copied data from preferences with ${item.entityId}")
    }
  }

  def processUpdates(item: PulledItem)(implicit hc: HeaderCarrier): Future[Boolean] =
    entityResolverConnector.getTaxIdentifiers(item.entityId).flatMap {
      case Right(Some(entity)) =>
        entity.taxIdentifiers.
          collectFirst[SaUtr] { case utr: SaUtr => utr}.
          fold(updatePreference(item.callbackUrl, None)) { utr =>
            insertAndUpdate(
              utr, if (item.paperless) formIds else List.empty, item.callbackUrl
            )
          }

      case x =>
        Logger.warn(s"failed to process ${item.entityId} from entity resolver")
        val status = if (x.isRight) "permanently-failed" else "failed"
        preferencesConnector.changeStatus(item.callbackUrl, status).map {
          case OK => true
          case x =>
            Logger.info(
              s"""could not change status to $status for entity id = ${item.entityId}
                 |response status code was $x"""".stripMargin
            )
            true
        }
    }


  def insertAndUpdate(utr: SaUtr, forms: List[String], callbackUrl: String)
                     (implicit hc: HeaderCarrier): Future[Boolean] =
    repo.insert(PrintPreference(utr.value, "sautr", forms)).
      flatMap { result =>
        if (result) updatePreference(callbackUrl, Some(utr))
        else Future.failed(new RuntimeException(s"Failed to insert utr $utr.value"))
      }.
      recoverWith { case ex =>
        Logger.warn(s"failed to include $utr in updated print suppressions", ex)
        // TODO: change this when boolean is refactored
        preferencesConnector.changeStatus(callbackUrl, "failed").map { _ => false }
      }

  def updatePreference(callbackUrl: String, utr: Option[SaUtr])
                      (implicit hc: HeaderCarrier): Future[Boolean] =
    preferencesConnector.changeStatus(callbackUrl, "succeeded").
      flatMap {
        case status @ (OK | CONFLICT) => Future.successful(true)
        case _ => utr.map { u =>
          repo.removeByUtr(u.value).map(_ => false)
        }.getOrElse(Future.successful(false))
      }
}

object PreferencesProcessor extends PreferencesProcessor {
  private implicit val connection = {
    import play.api.Play.current
    ReactiveMongoPlugin.mongoConnector.db
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val formIds: List[String] =
    Play.current.configuration.getStringSeq("form-types.saAll").
      getOrElse(throw new RuntimeException(s"configuration property form-types is not set")).
      toList

  def preferencesConnector: PreferencesConnector = PreferencesConnector

  def repo: UpdatedPrintSuppressionsRepository =
    new UpdatedPrintSuppressionsRepository(
      LocalDate.now(),
      counterName => MongoCounterRepository(counterName)
    )

  def entityResolverConnector: EntityResolverConnector = EntityResolverConnector
}

sealed trait ProcessingState extends Product with Serializable
final case class Succeeded(msg: String) extends ProcessingState
final case class Failed(msg: String, ex: Option[Throwable] = None) extends ProcessingState
final case class TotalCounts(processed: Int, failed: Int)
