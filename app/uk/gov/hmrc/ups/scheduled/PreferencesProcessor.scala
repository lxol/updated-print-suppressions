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

import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.ups.connectors.{PreferencesConnector, EntityResolverConnector}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.ups.model.PrintPreference
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressionsRepository

import scala.concurrent.Future

trait PreferencesProcessor {

  def entityResolverConnector: EntityResolverConnector

  def preferencesConnector: PreferencesConnector

  def repo: UpdatedPrintSuppressionsRepository


  def processUpdates()(implicit hc: HeaderCarrier): Future[Boolean] = {
    preferencesConnector.pullWorkItem() map {
      case Right(Some(item)) => {
        entityResolverConnector.getTaxIdentifiers(item.entityId) map {
          case Right(Some(entity)) => {
            entity.taxIdentifiers.collectFirst[SaUtr]{case utr: SaUtr => utr} match {
              case Some(utr) => {
                repo.insert(PrintPreference(utr.value, "sautr", List("formId"))) map {
                  case true => preferencesConnector.changeStatus(item.callbackUrl, "succeeded")
                  case false => throw new RuntimeException("insert into repo failed")
                }
              }
              case None => throw new RuntimeException("no utr found from the entity")
            }

          }
          case _ => throw new RuntimeException("Fail with entity resolver connector")
        }
      }
      case _ => throw new RuntimeException("Fail with preference connector")
    }
    Future.successful(true)
  }
}
