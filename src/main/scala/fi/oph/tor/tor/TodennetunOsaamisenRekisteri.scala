package fi.oph.tor.tor

import fi.oph.tor.arvosana.ArviointiasteikkoRepository
import fi.oph.tor.http.HttpStatus
import fi.oph.tor.koodisto.KoodistoPalvelu
import fi.oph.tor.opiskeluoikeus._
import fi.oph.tor.oppija._
import fi.oph.tor.schema.Henkilö.Oid
import fi.oph.tor.schema._
import fi.oph.tor.toruser.TorUser
import fi.oph.tor.tutkinto.{TutkintoRakenneValidator, TutkintoRepository}
import fi.vm.sade.utils.slf4j.Logging
import rx.lang.scala.Observable

class TodennetunOsaamisenRekisteri(oppijaRepository: OppijaRepository,
                                   opiskeluOikeusRepository: OpiskeluOikeusRepository,
                                   tutkintoRepository: TutkintoRepository,
                                   arviointiAsteikot: ArviointiasteikkoRepository,
                                   koodistoPalvelu: KoodistoPalvelu) extends Logging {


  def findOppijat(filters: List[QueryFilter])(implicit userContext: TorUser): Observable[TorOppija] = {
    val opiskeluoikeudet: Observable[(Oid, List[OpiskeluOikeus])] = opiskeluOikeusRepository.query(filters)

    opiskeluoikeudet.flatMap {
      case (oid, oikeudet) => oppijaRepository.findByOid(oid) match {
        case Some(oppija) =>
          Observable.just(TorOppija(oppija, oikeudet))
        case None =>
          logger.warn("Oppija with oid: " + oid + " not found")
          Observable.empty
      }
    }
  }


  def findOppijat(query: String)(implicit userContext: TorUser): Seq[FullHenkilö] = {
    val oppijat: List[FullHenkilö] = oppijaRepository.findOppijat(query)
    val filtered = opiskeluOikeusRepository.filterOppijat(oppijat)
    filtered
  }

  def createOrUpdate(oppija: TorOppija)(implicit userContext: TorUser): Either[HttpStatus, Henkilö.Oid] = {
    validate(oppija) match {
      case status if (status.isOk) =>
        val oppijaOid: Either[HttpStatus, PossiblyUnverifiedOppijaOid] = oppija.henkilö match {
          case h:NewHenkilö => oppijaRepository.findOrCreate(oppija.henkilö).right.map(VerifiedOppijaOid(_))
          case h:HenkilöWithOid => Right(UnverifiedOppijaOid(h.oid, oppijaRepository))
        }
        oppijaOid.right.flatMap { oppijaOid: PossiblyUnverifiedOppijaOid =>
          val opiskeluOikeusCreationResults: Seq[Either[HttpStatus, CreateOrUpdateResult]] = oppija.opiskeluoikeudet.map { opiskeluOikeus =>
            val result = opiskeluOikeusRepository.createOrUpdate(oppijaOid, opiskeluOikeus)
            result match {
              case Right(result) =>
                val (verb, id) = result match {
                  case Updated(id) => ("Päivitetty", id)
                  case Created(id) => ("Luotu", id)
                }
                logger.info(verb + " opiskeluoikeus " + id + " oppijalle " + oppijaOid + " tutkintoon " + opiskeluOikeus.suoritus.koulutusmoduulitoteutus.koulutusmoduuli.tunniste + " oppilaitoksessa " + opiskeluOikeus.oppilaitos.oid)
              case _ =>
            }
            result
          }
          opiskeluOikeusCreationResults.find(_.isLeft) match {
            case Some(Left(error)) => Left(error)
            case _ => Right(oppijaOid.oppijaOid)
          }
        }
      case notOk => Left(notOk)
    }
  }

  private def validate(oppija: TorOppija)(implicit userContext: TorUser): HttpStatus = {
    if (oppija.opiskeluoikeudet.length == 0) {
      HttpStatus.badRequest("At least one OpiskeluOikeus required")
    }
    else {
      HttpStatus.each(oppija.opiskeluoikeudet) { opiskeluOikeus =>
        HttpStatus.validate(userContext.userOrganisations.hasReadAccess(opiskeluOikeus.oppilaitos)) { HttpStatus.forbidden("Ei oikeuksia organisatioon " + opiskeluOikeus.oppilaitos.oid) }
          .then { TutkintoRakenneValidator(tutkintoRepository).validateTutkintoRakenne(opiskeluOikeus)}
      }
    }
  }

  def findTorOppija(oid: String)(implicit userContext: TorUser): Either[HttpStatus, TorOppija] = {
    oppijaRepository.findByOid(oid) match {
      case Some(oppija) =>
        opiskeluOikeusRepository.findByOppijaOid(oppija.oid) match {
          case Nil => notFound(oid)
          case opiskeluoikeudet => Right(TorOppija(oppija, opiskeluoikeudet))
        }
      case None => notFound(oid)
    }
  }

  private def notFound(oid: String): Left[HttpStatus, Nothing] = {
    Left(HttpStatus.notFound(s"Oppija with oid: $oid not found"))
  }
}
