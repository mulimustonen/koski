package fi.oph.koski.api

import fi.oph.koski.henkilo.MockOppijat
import fi.oph.koski.http.KoskiErrorCategory
import fi.oph.koski.log.AuditLogTester
import org.scalatest.{FreeSpec, Matchers}

class OppijaEditorSpec extends FreeSpec with Matchers with LocalJettyHttpSpecification with OpiskeluoikeusTestMethods {
  "/api/editor/:oid" - {
    "GET" - {
      "with valid oid" in {
        AuditLogTester.clearMessages
        get("api/editor/" + MockOppijat.eero.oid, headers = authHeaders()) {
          verifyResponseStatusOk()
          AuditLogTester.verifyAuditLogMessage(Map("operaatio" -> "OPISKELUOIKEUS_KATSOMINEN"))
        }
      }
      "with version number" in {
        val opiskeluoikeusOid = lastOpiskeluoikeus(MockOppijat.eero.oid).oid.get
        AuditLogTester.clearMessages
        get("api/editor/" + MockOppijat.eero.oid, params = List("opiskeluoikeus" -> opiskeluoikeusOid, "versionumero" -> "1"), headers = authHeaders()) {
          verifyResponseStatusOk()
          AuditLogTester.verifyAuditLogMessage(Map("operaatio" -> "OPISKELUOIKEUS_KATSOMINEN"))
        }
      }
      "with invalid oid" in {
        get("api/editor/blerg", headers = authHeaders()) {
          verifyResponseStatus(400, KoskiErrorCategory.badRequest.queryParam.virheellinenHenkilöOid("Virheellinen oid: blerg. Esimerkki oikeasta muodosta: 1.2.246.562.24.00000000001."))
        }
      }
      "with unknown oid" in {
        get("api/editor/1.2.246.562.24.90000000001", headers = authHeaders()) {
          verifyResponseStatus(404, KoskiErrorCategory.notFound.oppijaaEiLöydyTaiEiOikeuksia("Oppijaa 1.2.246.562.24.90000000001 ei löydy tai käyttäjällä ei ole oikeuksia tietojen katseluun."))
        }
      }
    }
  }

  "/api/editor/omattiedot" - {
    "GET" in {
      AuditLogTester.clearMessages
      get("api/editor/" + MockOppijat.eero.oid, headers = authHeaders()) {
        verifyResponseStatusOk()
        AuditLogTester.verifyAuditLogMessage(Map("operaatio" -> "OPISKELUOIKEUS_KATSOMINEN"))
      }
    }
  }
}

