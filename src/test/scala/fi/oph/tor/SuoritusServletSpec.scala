package fi.oph.tor

import fi.oph.tor.fixture.SuoritusTestData.{tutkintosuoritus1, vainKomo112, vainKomo111}
import fi.oph.tor.json.Json
import fi.oph.tor.model.Identified.withoutId
import fi.oph.tor.model.Suoritus
import org.scalatest.FreeSpec
import org.scalatra.test.scalatest.ScalatraSuite

class SuoritusServletSpec extends FreeSpec with ScalatraSuite with TorTest {
  lazy val tor: TodennetunOsaamisenRekisteri = initLocalRekisteri
  lazy val suoritusServlet = new SuoritusServlet(tor)
  addServlet(suoritusServlet, "/*")

  "Yksi tutkintosuoritus tallennetaan kantaan" - {
    "POST / -> tallennus onnistuu" in {
      post("/", Json.write(tutkintosuoritus1), Map("Content-type" -> "application/json")) {
        status should equal (200)
      }
    }
    "GET / -> kaikki suoritukset" in {
      verifySuoritukset("/", List(tutkintosuoritus1))
    }
    "GET /?personOid=x -> henkilön suoritukset" in {
      verifySuoritukset("/?personOid=person1", List(tutkintosuoritus1))
      verifySuoritukset("/?personOid=wrongPerson", List())
    }
    "GET /?organizationOid=x -> organisaation suoritukset" in {
      verifySuoritukset("/?organizationOid=org1", List(tutkintosuoritus1))
      verifySuoritukset("/?organizationOid=wrongOrg", List())
    }
    "GET /?komoOid=x -> koulutusmoduulin suoritukset parentteineen" in {
      verifySuoritukset("/?komoOid=kurssi-1.1.2", List(vainKomo112))
    }
    "GET /?completedAfter=x -> suoritukset annetun päivämäärän jälkeen" in {
      verifySuoritukset("/?completedAfter=1.6.2014", List(vainKomo111))
      get("/?completedAfter=qwer") {
        status should equal(400)
      }
    }
    "GET /?asdf=qwer -> bad request" in {
      get("/?asdf=qwer") {
        status should equal(400)
      }
    }
  }

  private def verifySuoritukset(path: String, expectedSuoritukset: List[Suoritus]) = {
    get(path) {
      status should equal (200)
      response.getContentType() should equal ("application/json;charset=utf-8")
      val suoritukset: List[Suoritus] = Json.read[List[Suoritus]](body)
      suoritukset.map(_.id) should not contain None
      Json.writePretty(suoritukset.map(withoutId)) should be (Json.writePretty(expectedSuoritukset.map(withoutId)))
    }
  }
}
