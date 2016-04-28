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

package uk.gov.hmrc.ups.config

import play.api.Play
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import uk.gov.hmrc.ups.service.{RemoveOlderCollections, SelectAndRemove}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object Jobs {

  import uk.gov.hmrc.play.config.RunMode.env

  object RemoveOlderCollectionsJob extends ExclusiveScheduledJob with SelectAndRemove {

    override def executeInMutex(implicit ec: ExecutionContext): Future[RemoveOlderCollectionsJob.Result] = {
      RemoveOlderCollections.removeOlderThan(durationInDays).map { totals =>
        Result(
          s"""$name completed with failures on collections [${totals.failures.mkString(",")}];
             |${totals.successes.mkString(",")} were removed""".stripMargin
        )
      }
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

}
