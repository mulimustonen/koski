import React from 'react'
import Bacon from 'baconjs'
import Http from './http'
import { modelTitle, modelLookup, modelSet, objectLookup } from './editor/EditorModel'
import { editorMapping } from './editor/Editors.jsx'
import { Editor } from './editor/GenericEditor.jsx'
import R from 'ramda'
import {modelData} from './editor/EditorModel.js'
import {currentLocation} from './location.js'
import { oppijaHakuElementP } from './OppijaHaku.jsx'
import Link from './Link.jsx'
import { increaseLoading, decreaseLoading } from './loadingFlag'

Bacon.Observable.prototype.flatScan = function(seed, f) {
  let current = seed
  return this.flatMapConcat(next =>
    f(current, next).doAction(updated => current = updated)
  ).toProperty(seed)
}

export const saveBus = Bacon.Bus()

export const oppijaContentP = (oppijaOid) => {
  const changeBus = Bacon.Bus()
  const doneEditingBus = Bacon.Bus()

  const queryString = currentLocation().filterQueryParams(key => ['opiskeluoikeus', 'versionumero'].includes(key)).queryString

  const oppijaEditorUri = `/koski/api/editor/${oppijaOid}${queryString}`

  const loadOppijaE = Bacon.once().map(() => () => Http.cachedGet(oppijaEditorUri))

  const saveE = doneEditingBus

  const changeSetE = Bacon.repeat(() => changeBus.takeUntil(saveE).fold([], '.concat'))

  const savedOppijaE = changeSetE.map(contextValuePairs => oppijaBeforeChange => {
    increaseLoading()
    if (contextValuePairs.length == 0) {
      return Bacon.once(oppijaBeforeChange).doAction(decreaseLoading)
    }
    let locallyModifiedOppija = R.splitEvery(2, contextValuePairs).reduce((acc, [context, value]) => modelSet(acc, context.path, value), oppijaBeforeChange)
    let firstPath = contextValuePairs[0].path
    let opiskeluoikeusPath = firstPath.split('.').slice(0, 6)
    var oppijaData = locallyModifiedOppija.value.data
    let opiskeluoikeus = objectLookup(oppijaData, opiskeluoikeusPath.join('.'))
    let oppijaUpdate = {
      henkilö: {oid: oppijaData.henkilö.oid},
      opiskeluoikeudet: [opiskeluoikeus]
    }

    return Http.put('/koski/api/oppija', oppijaUpdate)
      .flatMap(() => Http.cachedGet(oppijaEditorUri, { force: true }))
      .doAction(decreaseLoading)
  })

  let allUpdatesE = Bacon.mergeAll(loadOppijaE, savedOppijaE) // :: EventStream [Model -> EventStream[Model]]

  let oppijaP = allUpdatesE.flatScan({ loading: true }, (currentOppija, updateF) => updateF(currentOppija))
  oppijaP.onValue()

  saveBus.plug(savedOppijaE.map(true))

  return Bacon.combineWith(oppijaP, oppijaHakuElementP, (oppija, haku) => {
    return {
      content: (<div className='content-area'><div className="main-content oppija">
        { haku }
        <Link className="back-link" href="/koski/">Opiskelijat</Link>
        <ExistingOppija {...{oppija, changeBus, doneEditingBus}}/>
        </div></div>),
      title: modelData(oppija, 'henkilö') ? 'Oppijan tiedot' : ''
    }
  })
}

export const ExistingOppija = React.createClass({
  render() {
    let {oppija, changeBus, doneEditingBus} = this.props
    let henkilö = modelLookup(oppija, 'henkilö')
    return oppija.loading
      ? <div className="loading"/>
      : (
        <div>
          <h2>{modelTitle(henkilö, 'sukunimi')}, {modelTitle(henkilö, 'etunimet')} <span
            className='hetu'>({modelTitle(henkilö, 'hetu')})</span>
            <a className="json" href={`/koski/api/oppija/${modelData(henkilö, 'oid')}`}>JSON</a>
          </h2>
          {
            oppija
              ? <Editor model={oppija} {... {doneEditingBus, changeBus, editorMapping}}/>
              : null
          }
        </div>
    )
  }
})