/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.ups.config

import play.api.{Logger, Play}
import uk.gov.hmrc.play.config.RunMode
import uk.gov.hmrc.play.scheduling.{LockedScheduledJob, ExclusiveScheduledJob, ScheduledJob}
import uk.gov.hmrc.ups.scheduled.{PreferencesProcessor, RemoveOlderCollections}
import uk.gov.hmrc.ups.scheduled._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier
import org.joda.time.{Duration => JodaDuration}

object Jobs {

  import uk.gov.hmrc.play.config.RunMode.env

  object RemoveOlderCollectionsJob extends ExclusiveScheduledJob {

    override def executeInMutex(implicit ec: ExecutionContext): Future[RemoveOlderCollectionsJob.Result] =
      RemoveOlderCollections.removeOlderThan(durationInDays).map { totals =>
        (totals.failures ++ totals.successes).foreach {
          case Succeeded(collectionName) =>
            Logger.info(s"successfully removed collection $collectionName older than $durationInDays in $name job")

          case Failed(collectionName, ex) =>
            val msg = s"attempted to removed collection $collectionName and failed in $name job"
            ex.fold(Logger.error(msg)) { Logger.error(msg, _) }

        }
        Result(
          s"""$name completed with:
             |- failures on collections [${totals.failures.map(_.collectionName).mkString(",")}]
             |- collections [${totals.successes.map(_.collectionName).sorted.mkString(",")}] successfully removed
             |""".stripMargin
        )
      }

    private lazy val durationInDays =
      Play.current.configuration.getInt(s"$env.$name.durationInDays")
        .getOrElse(throw new IllegalStateException(s"Config key $env.$name.durationInDays missing"))
        .days


    override def name: String = "removeOlderCollections"

    private def durationFromConfig(propertyKey: String) = {
      Play.current.configuration.getMilliseconds(s"$env.scheduling.$name.$propertyKey")
        .getOrElse(throw new IllegalStateException(s"Config key $env.scheduling.$name.$propertyKey missing"))
        .milliseconds
    }

    lazy val initialDelay = durationFromConfig("initialDelay")
    lazy val interval = durationFromConfig("interval")
  }

  object UpdatedPrintSuppressionJob extends LockedScheduledJob with DurationFromConfig {

    override lazy val releaseLockAfter = JodaDuration.standardMinutes(10)

    def executeInLock(implicit ec: ExecutionContext): Future[UpdatedPrintSuppressionJob.Result] = {
      Logger.info(s"Start UpdatedPrintSuppressionJob")
      PreferencesProcessor.run(HeaderCarrier()).
        map { totals =>
          Result(
            s"UpdatedPrintSuppressions: ${totals.processed} items processed with ${totals.failed} failures"
          )
        }
    }

    override val name: String = "updatedPrintSuppressions"
  }

  trait DurationFromConfig { self: LockedScheduledJob =>
    private def durationFromConfig(propertyKey: String) = {
      Play.current.configuration.getMilliseconds(s"$env.scheduling.$name.$propertyKey")
        .getOrElse(throw new IllegalStateException(s"Config key $env.scheduling.$name.$propertyKey missing"))
        .milliseconds
    }

    lazy val initialDelay = durationFromConfig("initialDelay")

    lazy val interval = durationFromConfig("interval")
  }
}
