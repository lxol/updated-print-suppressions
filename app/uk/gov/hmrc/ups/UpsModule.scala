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

package uk.gov.hmrc.ups

import com.google.inject.{AbstractModule, Provides}
import javax.inject.Singleton
import net.codingwell.scalaguice.ScalaModule
import uk.gov.hmrc.play.scheduling.ScheduledJob
import uk.gov.hmrc.ups.scheduled.jobs.{RemoveOlderCollectionsJob, UpdatedPrintSuppressionJob}

class UpsModule extends AbstractModule with ScalaModule {

  override def configure(): Unit =
    bind[UpsMain].asEagerSingleton()

  @Provides
  @Singleton
  def scheduledJobsProvider(
               removeOlderCollections: RemoveOlderCollectionsJob,
               updatedPrintSuppressionJob: UpdatedPrintSuppressionJob
                           ): Seq[ScheduledJob] =
    Seq(
      removeOlderCollections,
      updatedPrintSuppressionJob
    )
}
