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

package uk.gov.hmrc.ups.scheduled.jobs

import java.util.concurrent.TimeUnit

import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.play.bootstrap.config.RunMode
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import uk.gov.hmrc.ups.repository.UpdatedPrintSuppressionsDatabase
import uk.gov.hmrc.ups.scheduled.{Failed, RemoveOlderCollections, Succeeded}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RemoveOlderCollectionsJob @Inject()(runMode: RunMode, configuration: Configuration, updatedPrintSuppressionsDatabase: UpdatedPrintSuppressionsDatabase)
  extends ExclusiveScheduledJob with RemoveOlderCollections {

  override def executeInMutex(implicit ec: ExecutionContext): Future[Result] =
    removeOlderThan(durationInDays).map { totals =>
      (totals.failures ++ totals.successes).foreach {
        case Succeeded(collectionName) =>
          Logger.info(s"successfully removed collection $collectionName older than $durationInDays in $name job")

        case Failed(collectionName, ex) =>
          val msg = s"attempted to removed collection $collectionName and failed in $name job"
          ex.fold(Logger.error(msg)) { Logger.error(msg, _) }

      }
      val text = s"""$name completed with:
                    |- failures on collections [${totals.failures.map(_.collectionName).mkString(",")}]
                    |- collections [${totals.successes.map(_.collectionName).sorted.mkString(",")}] successfully removed
                    |""".stripMargin
      Logger.error(text)
      Result(text)
    }

  private lazy val durationInDays = {
    val days = configuration.getOptional[Int](s"${runMode.env}.$name.durationInDays")
      .getOrElse(throw new IllegalStateException(s"Config key ${runMode.env}.$name.durationInDays missing"))
    FiniteDuration(days, TimeUnit.DAYS)
  }

  override def name: String = "removeOlderCollections"

  private def durationFromConfig(propertyKey: String): FiniteDuration = {
    val millis = configuration.getMillis(s"${runMode.env}.scheduling.$name.$propertyKey")
    FiniteDuration(millis, TimeUnit.MILLISECONDS)
  }

  override def initialDelay: FiniteDuration = durationFromConfig("initialDelay")

  override def interval: FiniteDuration = durationFromConfig("interval")

  override def repository: UpdatedPrintSuppressionsDatabase = updatedPrintSuppressionsDatabase
}
