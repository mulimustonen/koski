package fi.oph.koski.koodisto

import java.time.LocalDate

import com.typesafe.config.Config
import fi.oph.koski.json.JsonDiff.objectDiff
import fi.oph.koski.json.JsonSerializer
import fi.oph.koski.log.Logging
import org.json4s.jackson.JsonMethods

case class KoodistoCreator(config: Config) extends Logging {
  private val kp = KoodistoPalvelu.withoutCache(config)
  private val kmp = KoodistoMuokkausPalvelu(config)

  private val createMissingStr = config.getString("koodisto.create")
  private val updateExistingStr = config.getString("koodisto.update")
  private def updateExisting(koodistoUri: String) = updateExistingStr match {
    case "all" => true
    case "koskiKoodistot" => Koodistot.koskiKoodistot.contains(koodistoUri)
    case "muutKoodistot" => Koodistot.muutKoodistot.contains(koodistoUri)
    case _ => updateExistingStr.split(",").contains(koodistoUri)
  }
  private def shouldCreateMissing(koodistoUri: String) = createMissingStr match {
    case "all" => true
    case "koskiKoodistot" => Koodistot.koskiKoodistot.contains(koodistoUri)
    case "muutKoodistot" => Koodistot.muutKoodistot.contains(koodistoUri)
    case "true" => Koodistot.koskiKoodistot.contains(koodistoUri) // the former default case
    case _ => createMissingStr.split(",").contains(koodistoUri)
  }

  def createAndUpdateCodesBasedOnMockData {
    luoPuuttuvatKoodistot
    päivitäOlemassaOlevatKoodistot

    Koodistot.koodistot.par.foreach { koodistoUri =>
      def sortListsInside(k: KoodistoKoodi) = k.copy(metadata = k.metadata.sortBy(_.kieli), withinCodeElements = k.withinCodeElements.map(_.sortBy(_.codeElementUri)))

      val koodistoViite: KoodistoViite = kp.getLatestVersion(koodistoUri).getOrElse(throw new Exception("Koodistoa ei löydy: " + koodistoUri))
      val olemassaOlevatKoodit: List[KoodistoKoodi] = kp.getKoodistoKoodit(koodistoViite).toList.flatten.map(sortListsInside)
      val mockKoodit: List[KoodistoKoodi] = MockKoodistoPalvelu().getKoodistoKoodit(koodistoViite).toList.flatten.map(sortListsInside)

      päivitäOlemassaOlevatKoodi(koodistoUri, olemassaOlevatKoodit, mockKoodit)

      luoPuuttuvatKoodit(koodistoUri, olemassaOlevatKoodit, mockKoodit)
    }
  }

  private def luoPuuttuvatKoodit(koodistoUri: String, olemassaOlevatKoodit: List[KoodistoKoodi], mockKoodit: List[KoodistoKoodi]) = {
    if (shouldCreateMissing(koodistoUri)) {
      val luotavatKoodit: List[KoodistoKoodi] = mockKoodit.filter { koodi: KoodistoKoodi => !olemassaOlevatKoodit.find(_.koodiArvo == koodi.koodiArvo).isDefined }

      luotavatKoodit.zipWithIndex.foreach { case (koodi, index) =>
        logger.info("Luodaan koodi (" + (index + 1) + "/" + (luotavatKoodit.length) + ") " + koodi.koodiUri)
        kmp.createKoodi(koodistoUri, koodi.copy(voimassaAlkuPvm = Some(LocalDate.now)))
      }
    }
  }

  private def päivitäOlemassaOlevatKoodi(koodistoUri: String, olemassaOlevatKoodit: List[KoodistoKoodi], mockKoodit: List[KoodistoKoodi]) = {
    if (updateExisting(koodistoUri)) {
      val päivitettävätKoodit = olemassaOlevatKoodit.flatMap { vanhaKoodi =>
        mockKoodit.find(_.koodiArvo == vanhaKoodi.koodiArvo).flatMap { uusiKoodi =>
          val uusiKoodiSamallaKoodiUrilla = uusiKoodi.copy(
            koodiUri = vanhaKoodi.koodiUri
          )

          if (uusiKoodiSamallaKoodiUrilla != vanhaKoodi) {
            Some(vanhaKoodi, uusiKoodi)
          } else {
            None
          }
        }
      }

      päivitettävätKoodit.zipWithIndex.foreach { case ((vanhaKoodi, uusiKoodi), index) =>
        logger.info("Päivitetään koodi (" + (index + 1) + "/" + (päivitettävätKoodit.length) + ") " + uusiKoodi.koodiUri + " diff " + JsonMethods.compact(objectDiff(vanhaKoodi, uusiKoodi)) + " original " + JsonSerializer.writeWithRoot(vanhaKoodi))
        kmp.updateKoodi(koodistoUri, uusiKoodi.copy(
          voimassaAlkuPvm = Some(LocalDate.now),
          tila = uusiKoodi.tila.orElse(vanhaKoodi.tila),
          version = uusiKoodi.version.orElse(vanhaKoodi.version)
        ))
      }
    }
  }

  private def päivitäOlemassaOlevatKoodistot = {
    // update existing
    val olemassaOlevatKoodistot = Koodistot.koodistot.filter(updateExisting).par.filter(!kp.getLatestVersion(_).isEmpty).toList
    val päivitettävätKoodistot = olemassaOlevatKoodistot.flatMap { koodistoUri =>
      val existing: Koodisto = kp.getLatestVersion(koodistoUri).flatMap(kp.getKoodisto).get
      val mock: Koodisto = MockKoodistoPalvelu().getKoodisto(KoodistoViite(koodistoUri, 1)).get.copy(version = existing.version)

      if (existing.withinCodes.map(_.sortBy(_.codesUri)) != mock.withinCodes.map(_.sortBy(_.codesUri))) {
        logger.info("Päivitetään koodisto " + existing.koodistoUri + " diff " + JsonMethods.compact(objectDiff(existing, mock)) + " original " + JsonSerializer.writeWithRoot(existing))
        Some(mock)
      } else {
        None
      }
    }
    päivitettävätKoodistot.foreach { koodisto =>
      kmp.updateKoodisto(koodisto)
    }
  }

  private def luoPuuttuvatKoodistot {
    // Create missing
    val luotavatKoodistot = Koodistot.koodistot.filter(shouldCreateMissing).par.filter(kp.getLatestVersion(_).isEmpty).toList
    luotavatKoodistot.foreach { koodistoUri =>
      MockKoodistoPalvelu().getKoodisto(KoodistoViite(koodistoUri, 1)) match {
        case None =>
          throw new IllegalStateException("Mock not found: " + koodistoUri)
        case Some(koodisto) =>
          kmp.createKoodisto(koodisto)
      }
    }
  }
}