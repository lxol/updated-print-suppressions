/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.ups.connectors

import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Writes
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost, HttpResponse}

import scala.concurrent.Future

trait MockHttpGet extends MockitoSugar {
  val httpWrapper = mock[HttpWrapper]

  val http = new HttpGet {
    override protected def doGet(url: String)(implicit hc: HeaderCarrier) =
      Future.successful(httpWrapper.getF(url))

    override val hooks = NoneRequired
  }

  class HttpWrapper {
    def getF[T](uri: String): HttpResponse = HttpResponse.apply(200, None)

  }
}

trait MockHttpPost extends MockitoSugar {
  val httpWrapper = mock[HttpWrapper]

  val http = new HttpPost {
    override protected def doPost[A](url: String, body: A, headers: Seq[(String,String)])(implicit wts: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = Future.successful(httpWrapper.postF(url))
    override val hooks = NoneRequired

    protected def doPostString(url: String, body: String, headers: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[HttpResponse] = ???

    protected def doFormPost(url: String, body: Map[String, Seq[String]])(implicit hc: HeaderCarrier): Future[HttpResponse] = ???

    protected def doEmptyPost[A](url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = ???
  }

  class HttpWrapper {
    def postF[T](uri: String): HttpResponse = ???
  }
}
