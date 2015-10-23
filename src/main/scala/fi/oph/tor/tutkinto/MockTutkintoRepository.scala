package fi.oph.tor.tutkinto

import fi.oph.tor.eperusteet.{EPerusteetTutkintoRakenne, EPerusteRakenne}
import fi.oph.tor.json.Json
import org.json4s.jackson.JsonMethods._
import Json._

class MockTutkintoRepository extends TutkintoRepository {
  def tutkinnot = List(
    Tutkinto("Autoalan perustutkinto", ePerusteetDiaarinumero =  "39/011/2014", tutkintoKoodi =  "351301")
  )

  override def findTutkinnot(oppilaitosId: String, query: String) = {
    tutkinnot.filter(_.toString.toLowerCase.contains(query.toLowerCase))
  }

  override def findByEPerusteDiaarinumero(id: String) = tutkinnot.filter(_.ePerusteetDiaarinumero == id).headOption

  override def findPerusteRakenne(diaariNumero: String) = {
    val string = scala.io.Source.fromFile("src/main/resources/mockdata/eperusteet/612.json").mkString
    val rakenne = parse(string).extract[EPerusteRakenne]
    Some(EPerusteetTutkintoRakenne.convertRakenne(rakenne))
  }
}
