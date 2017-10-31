import React from 'baret'
import Bacon from 'baconjs'
import Atom from 'bacon.atom'
import TutkintoAutocomplete from '../TutkintoAutocomplete.jsx'
import {ift} from '../util'
import Suoritustyyppi from './Suoritustyyppi.jsx'
import {koodiarvoMatch, koodistoValues} from './koodisto'
import SuoritustapaDropdown from './SuoritustapaDropdown.jsx'
import Text from '../Text.jsx'
import {setPeruste} from '../editor/PerusteDropdown.jsx'

export default ({suoritusAtom, oppilaitosAtom, suorituskieliAtom}) => {
  const suoritustyypitP = koodistoValues('suorituksentyyppi/ammatillinentutkinto,nayttotutkintoonvalmistavakoulutus,ammatillinentutkintoosittainen,valma')
  const tutkintoAtom = Atom()
  const suoritustyyppiAtom = Atom()
  const suoritustapaAtom = Atom()
  const perusteAtom = Atom()
  suoritustyypitP.onValue(tyypit => suoritustyyppiAtom.set(tyypit.find(koodiarvoMatch('ammatillinentutkinto', 'ammatillinentutkintoosittainen', 'valma'))))
  oppilaitosAtom.changes().onValue(() => tutkintoAtom.set(undefined))

  const makeSuoritus = (oppilaitos, suoritustyyppi, tutkinto, suorituskieli, suoritustapa, peruste) => {
    let tutkintoData = tutkinto && {
        tunniste: {
          koodiarvo: tutkinto.tutkintoKoodi,
          koodistoUri: 'koulutus'
        },
        perusteenDiaarinumero: tutkinto.diaarinumero
      }
    if (koodiarvoMatch('ammatillinentutkinto')(suoritustyyppi) && tutkinto && oppilaitos && suoritustapa) {
      return {
        koulutusmoduuli: tutkintoData,
        toimipiste : oppilaitos,
        tyyppi: { koodistoUri: 'suorituksentyyppi', koodiarvo: 'ammatillinentutkinto'},
        suoritustapa: suoritustapa,
        suorituskieli : suorituskieli
      }
    }
    if (koodiarvoMatch('ammatillinentutkintoosittainen')(suoritustyyppi) && tutkinto && oppilaitos) {
      return {
        koulutusmoduuli: tutkintoData,
        toimipiste : oppilaitos,
        tyyppi: { koodistoUri: 'suorituksentyyppi', koodiarvo: 'ammatillinentutkintoosittainen'},
        suorituskieli : suorituskieli
      }
    }
    if (koodiarvoMatch('nayttotutkintoonvalmistavakoulutus')(suoritustyyppi) && tutkinto && oppilaitos) {
      return {
        koulutusmoduuli: {
          tunniste: {
            koodiarvo: '999904',
            koodistoUri: 'koulutus'
          }
        },
        tutkinto: tutkintoData,
        toimipiste : oppilaitos,
        tyyppi: { koodistoUri: 'suorituksentyyppi', koodiarvo: 'nayttotutkintoonvalmistavakoulutus'},
        suorituskieli : suorituskieli
      }
    }
    if (koodiarvoMatch('valma')(suoritustyyppi) && oppilaitos && peruste) {
      return {
        koulutusmoduuli: {
          tunniste: {
            koodiarvo: '999901',
            koodistoUri: 'koulutus'
          },
          perusteenDiaarinumero: peruste
        },
        toimipiste : oppilaitos,
        tyyppi: { koodistoUri: 'suorituksentyyppi', koodiarvo: 'valma'},
        suorituskieli : suorituskieli
      }
    }
  }

  ift(suoritustyyppiAtom.map(koodiarvoMatch('valma')), setPeruste(perusteAtom, {koodiarvo: 'valma'}))
  Bacon.combineWith(oppilaitosAtom, suoritustyyppiAtom, tutkintoAtom, suorituskieliAtom, suoritustapaAtom, perusteAtom, makeSuoritus).onValue(suoritus => suoritusAtom.set(suoritus))
  return (<div>
    <Suoritustyyppi suoritustyyppiAtom={suoritustyyppiAtom} suoritustyypitP={suoritustyypitP} title="Suoritustyyppi"/>

    <div className="tutkinto-autocomplete">
      {
        ift(oppilaitosAtom.and(suoritustyyppiAtom.map(koodiarvoMatch('valma')).not()), <TutkintoAutocomplete tutkintoAtom={tutkintoAtom} oppilaitosP={oppilaitosAtom} title={<Text name="Tutkinto"/>}/>)
      }
    </div>

    {
      ift(suoritustyyppiAtom.map(koodiarvoMatch('ammatillinentutkinto')), <SuoritustapaDropdown diaarinumero={tutkintoAtom.map('.diaarinumero')} suoritustapaAtom={suoritustapaAtom} title="Suoritustapa"/>)
    }
  </div>)
}