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

import play.api.Logger
import play.api.http.Status._
import play.api.libs.iteratee.{ Enumerator, Iteratee }
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.ups.connectors.{EntityResolverConnector, PreferencesConnector}
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressionsRepository

import scala.concurrent.Future

import scala.concurrent.ExecutionContext

trait PreferencesProcessor {

  def formIds: List[String]

  def entityResolverConnector: EntityResolverConnector

  def preferencesConnector: PreferencesConnector

  def repo: UpdatedPrintSuppressionsRepository

  // TODO: pass in header carrier or ec?
  def run(implicit ec: ExecutionContext): Future[TotalCounts] =
    Enumerator.generateM(pull(HeaderCarrier())).
      run(
        Iteratee.foldM(TotalCounts(0, 0)) { (totals, result) =>
          Future.successful(
            result match {
              case Succeeded(_) =>
                totals.copy(processed = totals.processed + 1)
                
              case Failed(_,_) =>
                totals.copy(failed = totals.failed + 1)
            }
          )
        }
      )

  def pull(implicit hc: HeaderCarrier): Future[Option[ProcessingState]] = ???
    

  def processUpdates(implicit hc: HeaderCarrier): Future[Boolean] = {
    preferencesConnector.pullWorkItem() flatMap {
      case Right(Some(item)) => {
        entityResolverConnector.getTaxIdentifiers(item.entityId) flatMap {
          case Right(Some(entity)) => {
            entity.taxIdentifiers.collectFirst[SaUtr]{case utr: SaUtr => utr} match {
              case Some(utr) => insertAndUpdate(
                utr, if (item.paperless) formIds else List.empty, item.callbackUrl
              )
              case None => ???
            }
          }
          case Right(None) =>
            Logger.warn(s"failed to retrieve ${item.entityId} from entity resolver")
            preferencesConnector.changeStatus(item.callbackUrl, "permanently-failed").map {
              case OK => true
              case x =>
                Logger.info(
                  s"""
                     |could not change status to permanently-failed for entity id = ${item.entityId}
                     |response status code was $x"
                       """.stripMargin
                )
                true
            }
          case Left(status) =>
            Logger.warn(s"failed to connect entity-resolver [status = $status] with [entityId = ${item.entityId}]")
            preferencesConnector.changeStatus(item.callbackUrl, "failed").map {
              case OK => true
              case x =>
                Logger.info(
                  s"""
                     |could not change status to failed for entity id = ${item.entityId}
                     |response status code was $x"
                       """.stripMargin
                )
                true
            }
        }
      }
      case Right(None) => Future.successful(true)
      case _ => throw new RuntimeException("Fail with preference connector")
    }
  }

  def insertAndUpdate(utr: SaUtr, formIds: List[String], callbackUrl: String)
                     (implicit hc: HeaderCarrier): Future[Boolean] =
    repo.insert(PrintPreference(utr.value, "sautr", formIds)).
      flatMap { result =>
        if (result) preferencesConnector.changeStatus(callbackUrl, "succeeded")
        else Future.failed(new RuntimeException(s"Failed to insert utr $utr.value"))
      }.
      flatMap {
        case status @ (OK | CONFLICT) => Future.successful(true)
        case _ => repo.removeByUtr(utr.value).map(_ => false)
      }.
      recoverWith { case ex =>
        Logger.warn(s"failed to include $utr in updated print suppressions", ex)
        // TODO: change this when boolean is refactored
        preferencesConnector.changeStatus(callbackUrl, "failed").map { _ => false }
      }
}

object PreferencesProcessor extends PreferencesProcessor {
  def formIds: List[String] = ???

  def preferencesConnector: PreferencesConnector = ???

  def repo: UpdatedPrintSuppressionsRepository = ???

  def entityResolverConnector: EntityResolverConnector = ???
}

sealed trait ProcessingState extends Product with Serializable
final case class Succeeded(msg: String) extends ProcessingState
final case class Failed(msg: String, ex: Exception) extends ProcessingState
final case class TotalCounts(processed: Int, failed: Int)
