package uk.gov.hmrc.ups.ispec

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault

import uk.gov.hmrc.domain.TaxIds._
import uk.gov.hmrc.ups.model.EntityId

trait EntityResolverStub {
  def stubGetEntity(entityId: EntityId, taxId: TaxIdWithName) =
    stubFor(
      get(urlMatching(s"/entity-resolver/${entityId.value}"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |"_id": "${entityId.value}",
                         |"${taxId.name}": "${taxId.value}"
                         |}
                         |""".stripMargin))
    )

  def stubGetEntityWithStatus(entityId: EntityId, status: Int) =
    stubFor(
      get(urlMatching(s"/entity-resolver/${entityId.value}"))
        .willReturn(aResponse().withStatus(status))
    )

  def stubExceptionOnGetEntity(entityId: EntityId) =
    stubFor(
      get(urlMatching(s"/entity-resolver/${entityId.value}")).willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE))
    )
}
