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

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import org.mockito.Matchers.any
import org.mockito.Mockito.{ times, verify, when }
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito._
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.scheduling.ScheduledJob

import scala.concurrent.duration._

class UpsMainTest extends PlaySpec with GuiceOneAppPerTest with MockitoSugar with Eventually {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(5, Millis)))

  val mockJob: ScheduledJob = mock[ScheduledJob]

  when(mockJob.interval).thenReturn(3.seconds)
  when(mockJob.initialDelay).thenReturn(1.seconds)

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(new AbstractModule with ScalaModule {
        override def configure(): Unit = {
          bind[Seq[ScheduledJob]].toInstance(Seq(mockJob))
          bind[UpsMain].asEagerSingleton()
        }
      })
      .configure(
        "metrics.enabled" -> "false"
      )
      .build()

  "assert that a job is called after one second" in {
    eventually {
      verify(mockJob, times(1)).execute(any())
    }
  }
}
