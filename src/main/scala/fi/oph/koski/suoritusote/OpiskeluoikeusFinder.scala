package fi.oph.koski.suoritusote

import fi.oph.koski.http.{HttpStatus, KoskiErrorCategory}
import fi.oph.koski.koskiuser.KoskiSession
import fi.oph.koski.oppija.KoskiOppijaFacade
import fi.oph.koski.schema._

case class OpiskeluoikeusFinder(koski: KoskiOppijaFacade) {
  def opiskeluoikeudet(oppijaOid: String, params: Map[String, String])(implicit user: KoskiSession): Either[HttpStatus, Oppija] with Product with Serializable = {
    val filters: List[(Opiskeluoikeus => Boolean)] = params.toList.flatMap {
      case ("oppilaitos", oppilaitosOid: String) => Some({ oo: Opiskeluoikeus => oo.getOppilaitos.oid == oppilaitosOid })
      case ("opiskeluoikeus", ooOid: String) => Some({ oo: Opiskeluoikeus => oo.oid.contains(ooOid) })
      case (_, _) => None
    }
    koski.findOppija(oppijaOid) match {
      case Right(Oppija(henkilö: TäydellisetHenkilötiedot, opiskeluoikeudet)) =>
        Right(Oppija(henkilö, opiskeluoikeudet.filter{ oo: Opiskeluoikeus => filters.forall { f => f(oo)} }.toList))
      case _ =>
        Left(KoskiErrorCategory.notFound.oppijaaEiLöydy())
    }
  }
}
