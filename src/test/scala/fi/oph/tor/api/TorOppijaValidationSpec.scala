package fi.oph.tor.api

import fi.oph.tor.http.TorErrorCategory
import fi.oph.tor.json.Json
import fi.oph.tor.oppija.MockOppijat
import fi.oph.tor.schema._
import fi.oph.tor.toruser.MockUsers
import org.json4s.JsonAST.JObject
import org.scalatest.FunSpec

class TorOppijaValidationSpec extends FunSpec with OpiskeluOikeusTestMethods {
  describe("Opiskeluoikeuden lisääminen") {
    describe("Valideilla tiedoilla") {
      it("palautetaan HTTP 200") {
        putOpiskeluOikeus(JObject()) {
          verifyResponseStatus(200)
        }
      }
    }

    describe("Ilman tunnistautumista") {
      it("palautetaan HTTP 401") {
        putOpiskeluOikeus(JObject(), headers = jsonContent) {
          verifyResponseStatus(401, TorErrorCategory.unauthorized("Käyttäjä ei ole tunnistautunut."))
        }
      }
    }

    describe("Epäkelpo JSON-dokumentti") {
      it("palautetaan HTTP 400 virhe" ) (request("api/oppija", "application/json", "not json", "put")
        (verifyResponseStatus(400, TorErrorCategory.badRequest.format.json("Invalid JSON"))))
    }

    describe("Väärä Content-Type") {
      it("palautetaan HTTP 415") {

        put("api/oppija", body = Json.write(makeOppija(defaultHenkilö, List(opiskeluoikeus()))), headers = authHeaders() ++ Map(("Content-type" -> "text/plain"))) {
          verifyResponseStatus(415, TorErrorCategory.unsupportedMediaType.jsonOnly("Wrong content type: only application/json content type allowed"))
        }
      }
    }

    describe("Henkilötiedot") {
      describe("Nimenä tyhjä merkkijono") {
        it("palautetaan HTTP 400 virhe" ) (putHenkilö(defaultHenkilö.copy(sukunimi = "")) (verifyResponseStatus(400)))
      }


      describe("Hetun ollessa") {
        describe("muodoltaan virheellinen") {
          it("palautetaan HTTP 400 virhe" ) (putHenkilö(defaultHenkilö.copy(hetu = "010101-123123"))
            (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.henkilötiedot.hetu("Virheellinen muoto hetulla: 010101-123123"))))
        }
        describe("muodoltaan oikea, mutta väärä tarkistusmerkki") {
          it("palautetaan HTTP 400 virhe" ) (putHenkilö(defaultHenkilö.copy(hetu = "010101-123P"))
            (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.henkilötiedot.hetu("Virheellinen tarkistusmerkki hetussa: 010101-123P"))))
        }
        describe("päivämäärältään tulevaisuudessa") {
          it("palautetaan HTTP 400 virhe" ) (putHenkilö(defaultHenkilö.copy(hetu = "141299A903C"))
            (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.henkilötiedot.hetu("Syntymäpäivä hetussa: 141299A903C on tulevaisuudessa"))))
        }
        describe("päivämäärältään virheellinen") {
          it("palautetaan HTTP 400 virhe" ) (putHenkilö(defaultHenkilö.copy(hetu = "300215-123T"))
            (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.henkilötiedot.hetu("Virheellinen syntymäpäivä hetulla: 300215-123T"))))
        }
        describe("validi") {
          it("palautetaan HTTP 200" ) (putHenkilö(defaultHenkilö.copy(hetu = "010101-123N"))
            (verifyResponseStatus(200)))
        }
      }

      describe("Käytettäessä oppijan oidia") {
        describe("Oid ok") {
          it("palautetaan HTTP 200" ) (putHenkilö(OidHenkilö(MockOppijat.eero.oid)) (verifyResponseStatus(200)))
        }

        describe("Oid virheellinen") {
          it("palautetaan HTTP 400" ) (putHenkilö(OidHenkilö("123.123.123")) (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.jsonSchema("ECMA 262 regex"))))
        }
      }

      describe("Käytettäessä oppijan kaikkia tietoja") {
        describe("Oid ok") {
          it("palautetaan HTTP 200" ) (putHenkilö(FullHenkilö(MockOppijat.eero.oid, "010101-123N", "Testi", "Testi", "Toivola", None, None)) (verifyResponseStatus(200)))
        }

        describe("Oid virheellinen") {
          it("palautetaan HTTP 400" ) (putHenkilö(FullHenkilö("123.123.123", "010101-123N", "Testi", "Testi", "Toivola", None, None)) (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.jsonSchema("ECMA 262 regex"))))
        }
      }
    }

    describe("Opiskeluoikeudet") {
      describe("Jos lähetetään 0 opiskeluoikeutta") {
        it("palautetaan HTTP 400") {
          putOppija(TorOppija(defaultHenkilö, List())) {
            verifyResponseStatus(400, TorErrorCategory.badRequest.validation.tyhjäOpiskeluoikeusLista("Annettiin tyhjä lista opiskeluoikeuksia."))
          }
        }
      }
    }

    describe("Oppilaitos") {
      def oppilaitoksella(oid: String) = Json.toJValue(opiskeluoikeus().copy(oppilaitos = Oppilaitos(oid)))

      describe("Kun opinto-oikeutta yritetään lisätä oppilaitokseen, johon käyttäjällä ei ole pääsyä") {
        it("palautetaan HTTP 403 virhe" ) { putOpiskeluOikeus(oppilaitoksella("1.2.246.562.10.93135224694"), headers = authHeaders(MockUsers.hiiri) ++ jsonContent) (
          verifyResponseStatus(403, TorErrorCategory.forbidden.organisaatio("Ei oikeuksia organisatioon 1.2.246.562.10.93135224694")))
        }
      }

      describe("Kun opinto-oikeutta yritetään lisätä oppilaitokseen, joka ei ole oppilaitos") {
        it("palautetaan HTTP 400 virhe" ) { putOpiskeluOikeus(oppilaitoksella("1.2.246.562.10.346830761110")) (
          verifyResponseStatus(400, TorErrorCategory.badRequest.validation.organisaatio.vääränTyyppinen("Organisaatio 1.2.246.562.10.346830761110 ei ole Oppilaitos")))
        }
      }

      describe("Kun opinto-oikeutta yritetään lisätä oppilaitokseen, jota ei löydy organisaatiopalvelusta") {
        it("palautetaan HTTP 400 virhe" ) { putOpiskeluOikeus(oppilaitoksella("1.2.246.562.10.146810761111")) (
          verifyResponseStatus(400, TorErrorCategory.badRequest.validation.organisaatio.tuntematon("Organisaatiota 1.2.246.562.10.146810761111 ei löydy organisaatiopalvelusta")))
        }
      }

      describe("Kun oppilaitoksen oid on virheellistä muotoa") {
        it("palautetaan HTTP 400 virhe" ) { putOpiskeluOikeus(oppilaitoksella("asdf")) (
          verifyResponseStatus(400, TorErrorCategory.badRequest.validation.jsonSchema("ECMA 262 regex")))
        }
      }
    }

    describe("Tutkinnon perusteet ja rakenne") {
      describe("Kun yritetään lisätä opinto-oikeus virheelliseen perusteeseen") {
        it("palautetaan HTTP 400 virhe" ) {
          putOpiskeluOikeus(Map(
            "suoritus" -> Map("koulutusmoduulitoteutus" -> Map("koulutusmoduuli" -> Map("perusteenDiaarinumero" -> "39/xxx/2014")))
          )) (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.rakenne.tuntematonDiaari("Tutkinnon perustetta ei löydy diaarinumerolla 39/xxx/2014")))
        }
      }

      describe("Kun yritetään lisätä opinto-oikeus ilman perustetta") {
        it("palautetaan HTTP 400 virhe" ) {
          putOpiskeluOikeus(Map(
            "suoritus" -> Map("koulutusmoduulitoteutus" -> Map("koulutusmoduuli" -> Map("perusteenDiaarinumero"-> "")))
          )) (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.jsonSchema("perusteenDiaarinumero")))
        }
      }

      describe("Osaamisala ja suoritustapa") {
        describe("Osaamisala ja suoritustapa ok") {
          it("palautetaan HTTP 200") (putOpiskeluOikeus(Map("suoritus" -> Map("koulutusmoduulitoteutus" -> Map(
            "suoritustapa" -> Map("tunniste" -> Map("koodiarvo" -> "ops", "koodistoUri" -> "suoritustapa")),
            "osaamisala" -> List(Map("koodiarvo" -> "1527", "koodistoUri" -> "osaamisala"))
          ))))(verifyResponseStatus(200)))
        }
        describe("Suoritustapa virheellinen") {
          it("palautetaan HTTP 400") (putOpiskeluOikeus(Map("suoritus" -> Map("koulutusmoduulitoteutus" -> Map(
            "suoritustapa" -> Map("tunniste" -> Map("koodiarvo" -> "blahblahtest", "koodistoUri" -> "suoritustapa")),
            "osaamisala" -> List(Map("koodiarvo" -> "1527", "koodistoUri" -> "osaamisala"))
          ))))(verifyResponseStatus(400, TorErrorCategory.badRequest.validation.koodisto.tuntematonKoodi("Koodia suoritustapa/blahblahtest ei löydy koodistosta"))))
        }
        describe("Osaamisala ei löydy tutkintorakenteesta") {
          it("palautetaan HTTP 400") (putOpiskeluOikeus(Map("suoritus" -> Map("koulutusmoduulitoteutus" -> Map(
            "suoritustapa" -> Map("tunniste" -> Map("koodiarvo" -> "ops", "koodistoUri" -> "suoritustapa")),
            "osaamisala" -> List(Map("koodiarvo" -> "3053", "koodistoUri" -> "osaamisala"))
          )))) (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.rakenne.tuntematonOsaamisala("Osaamisala 3053 ei löydy tutkintorakenteesta perusteelle 39/011/2014"))))
        }
        describe("Osaamisala virheellinen") {
          it("palautetaan HTTP 400")(putOpiskeluOikeus(Map("suoritus" -> Map("koulutusmoduulitoteutus" -> Map(
            "suoritustapa" -> Map("tunniste" -> Map("koodiarvo" -> "ops", "koodistoUri" -> "suoritustapa")),
            "osaamisala" -> List(Map("koodiarvo" -> "0", "koodistoUri" -> "osaamisala"))
          ))))(verifyResponseStatus(400, TorErrorCategory.badRequest.validation.koodisto.tuntematonKoodi("Koodia osaamisala/0 ei löydy koodistosta"))))
        }
      }

      describe("Tutkinnon osat ja arvionnit") {
        val tutkinnonSuoritustapaNäyttönä = Some(DefaultSuoritustapa(KoodistoKoodiViite("naytto", "suoritustapa")))

        describe("OPS-perusteinen tutkinnonosa") {
          describe("Tutkinnon osa ja arviointi ok") {
            it("palautetaan HTTP 200") (putTutkinnonOsaSuoritus(tutkinnonOsaSuoritus, tutkinnonSuoritustapaNäyttönä) (verifyResponseStatus(200)))
          }

          describe("Tutkinnon osa ei kuulu tutkintorakenteeseen") {
            val tutkinnonosatoteutus: OpsTutkinnonosatoteutus = OpsTutkinnonosatoteutus(OpsTutkinnonosa(KoodistoKoodiViite("103135", "tutkinnonosat"), true, Some(Laajuus(11, KoodistoKoodiViite("6", "opintojenlaajuusyksikko"))), None, None), None, None)
            it("palautetaan HTTP 400") (putTutkinnonOsaSuoritus(tutkinnonOsaSuoritus.copy(koulutusmoduulitoteutus = tutkinnonosatoteutus), tutkinnonSuoritustapaNäyttönä)(
              verifyResponseStatus(400, TorErrorCategory.badRequest.validation.rakenne.tuntematonTutkinnonOsa("Tutkinnon osa tutkinnonosat/103135 ei löydy tutkintorakenteesta perusteelle 39/011/2014 - suoritustapa naytto"))))
          }

          describe("Tutkinnon osaa ei ei löydy koodistosta") {
            it("palautetaan HTTP 400") (putTutkinnonOsaSuoritus(tutkinnonOsaSuoritus.copy(
              koulutusmoduulitoteutus = tutkinnonOsaToteutus.copy(
                koulutusmoduuli = OpsTutkinnonosa(KoodistoKoodiViite("9923123", "tutkinnonosat"), true, None, None, None))), tutkinnonSuoritustapaNäyttönä)
              (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.koodisto.tuntematonKoodi("Koodia tutkinnonosat/9923123 ei löydy koodistosta"))))
          }

          describe("Arviointiasteikko on tuntematon") {
            it("palautetaan HTTP 400") (putTutkinnonOsaSuoritus(tutkinnonOsaSuoritus.copy(arviointi = Some(List(Arviointi(KoodistoKoodiViite("2", "vääräasteikko"), None)))), tutkinnonSuoritustapaNäyttönä)
              (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.jsonSchema("not found in enum"))))
          }

          describe("Arvosana ei kuulu perusteiden mukaiseen arviointiasteikkoon") {
            it("palautetaan HTTP 400") (putTutkinnonOsaSuoritus(tutkinnonOsaSuoritus.copy(arviointi = Some(List(Arviointi(KoodistoKoodiViite("x", "arviointiasteikkoammatillinent1k3"), None)))), tutkinnonSuoritustapaNäyttönä)
              (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.koodisto.tuntematonKoodi("Koodia arviointiasteikkoammatillinent1k3/x ei löydy koodistosta"))))
          }
        }

        describe("Paikallinen tutkinnonosa") {
          val paikallinenTutkinnonOsa = PaikallinenTutkinnonosa(
            Paikallinenkoodi("1", "paikallinen osa", "paikallinenkoodisto"), "Paikallinen tutkinnon osa", false, Some(laajuus)
          )
          describe("Tutkinnon osa ja arviointi ok") {
            it("palautetaan HTTP 200") (putTutkinnonOsaSuoritus(tutkinnonOsaSuoritus.copy(koulutusmoduulitoteutus = PaikallinenTutkinnonosatoteutus(paikallinenTutkinnonOsa)), tutkinnonSuoritustapaNäyttönä) (verifyResponseStatus(200)))
          }

          describe("Laajuus negatiivinen") {
            it("palautetaan HTTP 400") (putTutkinnonOsaSuoritus(tutkinnonOsaSuoritus.copy(koulutusmoduulitoteutus = PaikallinenTutkinnonosatoteutus(paikallinenTutkinnonOsa.copy(laajuus = Some(laajuus.copy(arvo = -1))))), tutkinnonSuoritustapaNäyttönä) (
              verifyResponseStatus(400, TorErrorCategory.badRequest.validation.jsonSchema("numeric instance is lower than the required minimum")))
            )
          }
        }

        describe("Suoritustapa puuttuu") {
          it("palautetaan HTTP 400") (putTutkinnonOsaSuoritus(tutkinnonOsaSuoritus, None) {
            verifyResponseStatus(400, TorErrorCategory.badRequest.validation.rakenne.suoritustapaPuuttuu("Tutkinnolta puuttuu suoritustapa. Tutkinnon osasuorituksia ei hyväksytä."))
          })
        }
      }
    }

    describe("Oppisopimus") {
      def toteutusOppisopimuksella(yTunnus: String): TutkintoKoulutustoteutus = {
        tutkintototeutus.copy(järjestämismuoto = Some(OppisopimuksellinenJärjestämismuoto(KoodistoKoodiViite("20", "jarjestamismuoto"), Oppisopimus(Yritys("Reaktor", yTunnus)))))
      }

      describe("Kun ok") {
        it("palautetaan HTTP 200") (
          putOpiskeluOikeus(opiskeluoikeus(toteutusOppisopimuksella("1629284-5")))
            (verifyResponseStatus(200))
        )
      }

      describe("Virheellinen y-tunnus") {
        it("palautetaan HTTP 400") (
          putOpiskeluOikeus(opiskeluoikeus(toteutusOppisopimuksella("1629284x5")))
            (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.jsonSchema("ECMA 262 regex")))
        )
      }
    }

    describe("Päivämäärät") {
      describe("Opiskeluoikeuden päivämäärät") {
        describe("Päivämäärät kunnossa") {
          it("palautetaan HTTP 200" ) (putOpiskeluOikeus(Map(
            "alkamispäivä" -> "2015-08-01",
            "päättymispäivä" -> "2016-05-31",
            "arvioituPäättymispäivä" -> "2018-05-31"
          ))(verifyResponseStatus(200)))
        }
        describe("Päivämääräformaatti virheellinen") {
          it("palautetaan HTTP 400" ) (putOpiskeluOikeus(Map(
            "alkamispäivä" -> "2015.01-12"
          ))(verifyResponseStatus(400, TorErrorCategory.badRequest.format.pvm("Virheellinen päivämäärä: 2015.01-12"))))
        }
        describe("Päivämäärä virheellinen") {
          it("palautetaan HTTP 400" ) (putOpiskeluOikeus(Map(
            "alkamispäivä" -> "2015-01-32"
          ))(verifyResponseStatus(400, TorErrorCategory.badRequest.format.pvm("Virheellinen päivämäärä: 2015-01-32"))))
        }
        describe("Väärä päivämääräjärjestys") {
          it("alkamispäivä > päättymispäivä" ) (putOpiskeluOikeus(Map(
            "alkamispäivä" -> "2015-08-01",
            "päättymispäivä" -> "2014-05-31"
          ))(verifyResponseStatus(400, TorErrorCategory.badRequest.validation.date.loppuEnnenAlkua("alkamispäivä (2015-08-01) oltava sama tai aiempi kuin päättymispäivä(2014-05-31)"))))

          it("alkamispäivä > arvioituPäättymispäivä" ) (putOpiskeluOikeus(Map(
            "alkamispäivä" -> "2015-08-01",
            "arvioituPäättymispäivä" -> "2014-05-31"
          ))(verifyResponseStatus(400, TorErrorCategory.badRequest.validation.date.loppuEnnenAlkua("alkamispäivä (2015-08-01) oltava sama tai aiempi kuin arvioituPäättymispäivä(2014-05-31)"))))
        }
      }

      describe("Suorituksen päivämäärät") {
        describe("Päivämäärät kunnossa") {
          it("palautetaan HTTP 200" ) (putOpiskeluOikeus(Map("suoritus" -> Map(
            "alkamispäivä" -> "2015-08-01",
            "arviointi" -> List(Map(
              "päivä" -> "2016-05-31",
              "arvosana" -> Map("koodiarvo" -> "2", "koodistoUri" -> "arviointiasteikkoammatillinent1k3"))),
            "vahvistus" -> Map("päivä" -> "2016-05-31")
          )))(verifyResponseStatus(200)))
        }

        describe("alkamispäivä > arviointi.päivä") {
          it("palautetaan HTTP 200" ) (putOpiskeluOikeus(Map("suoritus" -> Map(
            "alkamispäivä" -> "2017-08-01",
            "arviointi" -> List(Map(
              "päivä" -> "2016-05-31",
              "arvosana" -> Map("koodiarvo" -> "2", "koodistoUri" -> "arviointiasteikkoammatillinent1k3"))
            )
          )))(verifyResponseStatus(400, TorErrorCategory.badRequest.validation.date.loppuEnnenAlkua("suoritus.alkamispäivä (2017-08-01) oltava sama tai aiempi kuin suoritus.arviointi.päivä(2016-05-31)"))))
        }

        describe("arviointi.päivä > vahvistus.päivä") {
          it("palautetaan HTTP 200" ) (putOpiskeluOikeus(Map("suoritus" -> Map(
            "arviointi" -> List(Map(
              "päivä" -> "2016-05-31",
              "arvosana" -> Map("koodiarvo" -> "2", "koodistoUri" -> "arviointiasteikkoammatillinent1k3"))
            ),
            "vahvistus" -> Map("päivä" -> "2016-05-30")
          )))(verifyResponseStatus(400, TorErrorCategory.badRequest.validation.date.loppuEnnenAlkua("suoritus.arviointi.päivä (2016-05-31) oltava sama tai aiempi kuin suoritus.vahvistus.päivä(2016-05-30)"))))
        }
      }

      describe("opiskeluoikeusjaksot"){
        describe("Päivämäärät kunnossa") {
          it("palautetaan HTTP 200") (putOpiskeluOikeus(Map("opiskeluoikeudenTila" -> Map("opiskeluoikeusjaksot" -> List(
            Map( "alku" -> "2015-08-01", "loppu" -> "2015-12-31", "tila" -> Map("koodiarvo" -> "aktiivinen", "koodistoUri" -> "opiskeluoikeudentila")),
            Map( "alku" -> "2016-01-01", "loppu" -> "2016-05-31", "tila" -> Map("koodiarvo" -> "keskeyttanyt", "koodistoUri" -> "opiskeluoikeudentila")),
            Map( "alku" -> "2016-06-01", "tila" -> Map("koodiarvo" -> "paattynyt", "koodistoUri" -> "opiskeluoikeudentila"))
          )))) (verifyResponseStatus(200)))
        }
        describe("alku > loppu") {
          it("palautetaan HTTP 400") (putOpiskeluOikeus(Map("opiskeluoikeudenTila" -> Map("opiskeluoikeusjaksot" -> List(
            Map( "alku" -> "2016-08-01", "loppu" -> "2015-12-31", "tila" -> Map("koodiarvo" -> "aktiivinen", "koodistoUri" -> "opiskeluoikeudentila"))
          )))) (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.date.loppuEnnenAlkua("opiskeluoikeudenTila.opiskeluoikeusjaksot.alku (2016-08-01) oltava sama tai aiempi kuin opiskeluoikeudenTila.opiskeluoikeusjaksot.loppu(2015-12-31)"))))
        }
        describe("ei-viimeiseltä jaksolta puuttuu loppupäivä") {
          it("palautetaan HTTP 400") (putOpiskeluOikeus(Map("opiskeluoikeudenTila" -> Map("opiskeluoikeusjaksot" -> List(
            Map("alku" -> "2015-08-01", "tila" -> Map("koodiarvo" -> "aktiivinen", "koodistoUri" -> "opiskeluoikeudentila")),
            Map("alku" -> "2016-01-01", "loppu" -> "2016-05-31", "tila" -> Map("koodiarvo" -> "keskeyttanyt", "koodistoUri" -> "opiskeluoikeudentila"))
          )))) (verifyResponseStatus(400, TorErrorCategory.badRequest.validation.date.jaksonLoppupäiväPuuttuu("opiskeluoikeudenTila.opiskeluoikeusjaksot: ei-viimeiseltä jaksolta puuttuu loppupäivä")))) }
        describe("jaksot ovat päällekkäiset") {
          it("palautetaan HTTP 400") (putOpiskeluOikeus(Map("opiskeluoikeudenTila" -> Map("opiskeluoikeusjaksot" -> List(
            Map( "alku" -> "2015-08-01", "loppu" -> "2016-01-01", "tila" -> Map("koodiarvo" -> "aktiivinen", "koodistoUri" -> "opiskeluoikeudentila")),
            Map( "alku" -> "2016-01-01", "loppu" -> "2016-05-31", "tila" -> Map("koodiarvo" -> "keskeyttanyt", "koodistoUri" -> "opiskeluoikeudentila"))
          ))))(verifyResponseStatus(400, TorErrorCategory.badRequest.validation.date.jaksotEivätMuodostaJatkumoa("opiskeluoikeudenTila.opiskeluoikeusjaksot: jaksot eivät muodosta jatkumoa"))))
        }
        describe("jaksojen väliin jää tyhjää") {
          it("palautetaan HTTP 400") (putOpiskeluOikeus(Map("opiskeluoikeudenTila" -> Map("opiskeluoikeusjaksot" -> List(
            Map( "alku" -> "2015-08-01", "loppu" -> "2015-10-01", "tila" -> Map("koodiarvo" -> "aktiivinen", "koodistoUri" -> "opiskeluoikeudentila")),
            Map( "alku" -> "2016-01-01", "loppu" -> "2016-05-31", "tila" -> Map("koodiarvo" -> "keskeyttanyt", "koodistoUri" -> "opiskeluoikeudentila"))
          ))))(verifyResponseStatus(400, TorErrorCategory.badRequest.validation.date.jaksotEivätMuodostaJatkumoa("opiskeluoikeudenTila.opiskeluoikeusjaksot: jaksot eivät muodosta jatkumoa"))))
        }
      }

      describe("Läsnäolojaksot") {
        describe("Päivämäärät kunnossa") {
          it("palautetaan HTTP 200") (putOpiskeluOikeus(Map("läsnäolotiedot" -> Map("läsnäolojaksot" -> List(
            Map( "alku" -> "2015-08-01", "loppu" -> "2015-12-31", "tila" -> Map("koodiarvo" -> "lasna", "koodistoUri" -> "lasnaolotila")),
            Map( "alku" -> "2016-01-01", "loppu" -> "2016-05-31", "tila" -> Map("koodiarvo" -> "poissa", "koodistoUri" -> "lasnaolotila")),
            Map( "alku" -> "2016-06-01", "tila" -> Map("koodiarvo" -> "lasna", "koodistoUri" -> "lasnaolotila"))
          ))))(verifyResponseStatus(200)))
        }
        describe("alku > loppu") {
          it("palautetaan HTTP 400") (putOpiskeluOikeus(Map("läsnäolotiedot" -> Map("läsnäolojaksot" -> List(
            Map( "alku" -> "2016-08-01", "loppu" -> "2015-12-31", "tila" -> Map("koodiarvo" -> "lasna", "koodistoUri" -> "lasnaolotila"))
          ))))(verifyResponseStatus(400, TorErrorCategory.badRequest.validation.date.loppuEnnenAlkua("läsnäolotiedot.läsnäolojaksot.alku (2016-08-01) oltava sama tai aiempi kuin läsnäolotiedot.läsnäolojaksot.loppu(2015-12-31)"))))
        }
        describe("ei-viimeiseltä jaksolta puuttuu loppupäivä") {
          it("palautetaan HTTP 400") (putOpiskeluOikeus(Map("läsnäolotiedot" -> Map("läsnäolojaksot" -> List(
            Map( "alku" -> "2015-08-01", "tila" -> Map("koodiarvo" -> "lasna", "koodistoUri" -> "lasnaolotila")),
            Map( "alku" -> "2016-01-01", "loppu" -> "2016-05-31", "tila" -> Map("koodiarvo" -> "poissa", "koodistoUri" -> "lasnaolotila"))
          ))))(verifyResponseStatus(400, TorErrorCategory.badRequest.validation.date.jaksonLoppupäiväPuuttuu("läsnäolotiedot.läsnäolojaksot: ei-viimeiseltä jaksolta puuttuu loppupäivä"))))
        }
        describe("jaksot ovat päällekkäiset") {
          it("palautetaan HTTP 400") (putOpiskeluOikeus(Map("läsnäolotiedot" -> Map("läsnäolojaksot" -> List(
            Map( "alku" -> "2015-08-01", "loppu" -> "2016-01-01", "tila" -> Map("koodiarvo" -> "lasna", "koodistoUri" -> "lasnaolotila")),
            Map( "alku" -> "2016-01-01", "loppu" -> "2016-05-31", "tila" -> Map("koodiarvo" -> "poissa", "koodistoUri" -> "lasnaolotila"))
          ))))(verifyResponseStatus(400, TorErrorCategory.badRequest.validation.date.jaksotEivätMuodostaJatkumoa("läsnäolotiedot.läsnäolojaksot: jaksot eivät muodosta jatkumoa"))))
        }
        describe("jaksojen väliin jää tyhjää") {
          it("palautetaan HTTP 400") (putOpiskeluOikeus(Map("läsnäolotiedot" -> Map("läsnäolojaksot" -> List(
            Map( "alku" -> "2015-08-01", "loppu" -> "2015-10-01", "tila" -> Map("koodiarvo" -> "lasna", "koodistoUri" -> "lasnaolotila")),
            Map( "alku" -> "2016-01-01", "loppu" -> "2016-05-31", "tila" -> Map("koodiarvo" -> "poissa", "koodistoUri" -> "lasnaolotila"))
          ))))(verifyResponseStatus(400, TorErrorCategory.badRequest.validation.date.jaksotEivätMuodostaJatkumoa("läsnäolotiedot.läsnäolojaksot: jaksot eivät muodosta jatkumoa"))))
        }
      }
    }

  }
}
