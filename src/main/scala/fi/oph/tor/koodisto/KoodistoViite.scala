package fi.oph.tor.koodisto

import fi.oph.tor.log.Loggable

case class KoodistoViite(koodistoUri: String, versio: Int) extends Loggable {
  override def toString = koodistoUri + "/" + versio
}

