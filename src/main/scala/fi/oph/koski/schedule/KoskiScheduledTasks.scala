package fi.oph.koski.schedule

import java.lang.System.currentTimeMillis

import fi.oph.koski.config.KoskiApplication
import fi.oph.koski.henkilo.AuthenticationServiceClient.OppijaHenkilö
import fi.oph.koski.http.HttpStatus
import fi.oph.koski.json.Json
import fi.oph.koski.opiskeluoikeus.Henkilötiedot
import fi.oph.koski.schema.Henkilö.Oid
import fi.oph.koski.util.Timing
import org.json4s.JValue

class KoskiScheduledTasks(application: KoskiApplication) {
  val updateHenkilötScheduler: Scheduler = new UpdateHenkilot(application).scheduler
}

class UpdateHenkilot(application: KoskiApplication) extends Timing {
  def scheduler = new Scheduler(application.database.db, "henkilötiedot-update", new IntervalSchedule(henkilötiedotUpdateInterval), henkilöUpdateContext(currentTimeMillis), updateHenkilöt)

  def updateHenkilöt(context: Option[JValue]): Option[JValue] = timed("scheduledHenkilötiedotUpdate") {
    implicit val formats = Json.jsonFormats
    try {
      val oldContext = context.get.extract[HenkilöUpdateContext]
      val startMillis = currentTimeMillis
      val kaikkiMuuttuneet = application.authenticationServiceClient.findChangedOppijaOids(oldContext.lastRun)
      val initial: Either[HenkilöUpdateContext, HenkilöUpdateContext] = Right(oldContext)
      val newContext = kaikkiMuuttuneet.sliding(1000, 1000).foldLeft(initial) { (ctx, oids) =>
        ctx.right.flatMap(ctx => runUpdate(oids, startMillis, ctx)) // stops updating if "left" occurs
      }.fold(identity, identity)
      Some(Json.toJValue(newContext))
    } catch {
      case e: Exception =>
        logger.error(e)
        context
    }
  }

  private def runUpdate(oids: List[Oid], startMillis: Long, lastContext: HenkilöUpdateContext): Either[HenkilöUpdateContext, HenkilöUpdateContext] = try {
    updateKoskiHenkilöt(oids, startMillis)
  } catch {
    case e: Exception =>
      logger.error(e)("Problem running scheduledHenkilötiedotUpdate")
      Left(lastContext)
  }

  private def updateKoskiHenkilöt(oids: List[Oid], startMillis: Long) = {
    val oppijat: List[OppijaHenkilö] = application.authenticationServiceClient.findOppijatByOids(oids).sortBy(_.modified) // TODO: sorting should be unnecessary, verify
    val oppijatByOid: Map[Oid, OppijaHenkilö] = oppijat.groupBy(_.oidHenkilo).mapValues(_.head)
    val koskeenPäivitetyt: List[Oid] = oppijat.map(_.toNimitiedotJaOid).filter(o => application.henkilöCacheUpdater.updateHenkilöAction(o) > 0).map(_.oid)
    val lastModified = oppijat.lastOption.map(_.modified).getOrElse(startMillis)

    if (koskeenPäivitetyt.isEmpty) {
      Right(HenkilöUpdateContext(lastModified))
    } else {
      val muuttuneidenHenkilötiedot: List[Henkilötiedot] = application.perustiedotRepository.findHenkiloPerustiedotByOids(koskeenPäivitetyt).map(p => Henkilötiedot(p.id, oppijatByOid(p.henkilö.oid).toNimitiedotJaOid))
      application.perustiedotRepository.updateBulk(muuttuneidenHenkilötiedot, insertMissing = false) match {
        case Right(updatedCount) => updatedCount
          logger.info(s"Updated ${koskeenPäivitetyt.length} entries to henkilö table and $updatedCount to elasticsearch, latest oppija modified timestamp: $lastModified")
          Right(HenkilöUpdateContext(lastModified))
        case Left(HttpStatus(_, errors)) =>
          logger.error(s"Couldn't update data to elasticsearch ${errors.mkString}")
          Left(HenkilöUpdateContext(oppijatByOid(koskeenPäivitetyt.head).modified - 1000))
      }
    }
  }

  private def henkilöUpdateContext(lastRun: Long) = Some(Json.toJValue(HenkilöUpdateContext(lastRun)))
  private def henkilötiedotUpdateInterval = application.config.getDuration("schedule.henkilötiedotUpdateInterval")
}

case class HenkilöUpdateContext(lastRun: Long)
