package fi.oph.tor.api

import fi.oph.tor.jettylauncher.SharedJetty
import fi.oph.tor.json.Json
import fi.oph.tor.oppija.MockOppijat
import fi.oph.tor.schema.TorOppijaExamples
import org.scalatest.{FreeSpec, Matchers}

class ApiSmokeTest extends FreeSpec with Matchers with HttpSpecification {
  "/api/oppija/" - {
    SharedJetty.start
    "GET" - {
      "with valid oid" in {
        get("api/oppija/" + MockOppijat.eero.oid, headers = authHeaders()) {
          verifyResponseStatus()
        }
      }
      "with invalid oid" in {
        get("api/oppija/blerg", headers = authHeaders()) {
          verifyResponseStatus(400)
        }
      }
    }
  }
}
