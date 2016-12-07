import React from 'react'
import Bacon from 'baconjs'
import Pager from './Pager'
import { navigateTo, navigateToOppija } from './location'
import { ISO2FinnishDate } from './date'
import { oppijaHakuElementP } from './OppijaHaku.jsx'
import PaginationLink from './PaginationLink.jsx'
import R from 'ramda'

export const Oppijataulukko = React.createClass({
  render() {
    let { rivit, edellisetRivit, pager, params } = this.props
    let [ sortBy, sortOrder ] = params.sort ? params.sort.split(':') : ['nimi', 'asc']
    let näytettävätRivit = rivit || edellisetRivit

    return (<div className="oppijataulukko">{ näytettävätRivit ? (
      <table>
        <thead>
          <tr>
            <th className={sortBy == 'nimi' ? 'nimi sorted' : 'nimi'}>
              <Sorter field='nimi' sortBus={this.sortBus} sortBy={sortBy} sortOrder={sortOrder}>Nimi</Sorter>
              <TextFilter field='nimi' filterBus={this.filterBus} params={params}/>
            </th>
            <th className="tyyppi">Opiskeluoikeuden tyyppi</th>
            <th className="koulutus">Koulutus</th>
            <th className="tutkinto">Tutkinto / osaamisala / nimike</th>
            <th className="tila">Tila</th>
            <th className="oppilaitos">Oppilaitos</th>
            <th className={sortBy == 'alkamispäivä' ? 'aloitus sorted': 'aloitus'}>
              <Sorter field='alkamispäivä' sortBus={this.sortBus} sortBy={sortBy} sortOrder={sortOrder}>Aloitus pvm</Sorter>
            </th>
            <th className={sortBy == 'luokka' ? 'luokka sorted': 'luokka'}>
              <Sorter field='luokka' sortBus={this.sortBus} sortBy={sortBy} sortOrder={sortOrder}>Luokka / ryhmä</Sorter>
            </th>
          </tr>
        </thead>
        <tbody className={rivit ? '' : 'loading'}>
          {
            näytettävätRivit.map( (opiskeluoikeus, i) => <tr key={i}>
              <td className="nimi"><a href={`/koski/oppija/${opiskeluoikeus.henkilö.oid}`} onClick={(e) => navigateToOppija(opiskeluoikeus.henkilö, e)}>{ opiskeluoikeus.henkilö.sukunimi + ', ' + opiskeluoikeus.henkilö.etunimet}</a></td>
              <td className="tyyppi">{ opiskeluoikeus.tyyppi.nimi.fi }</td>
              <td className="koulutus">{ opiskeluoikeus.suoritukset.map((suoritus, j) => <span key={j}>{suoritus.tyyppi.nimi.fi}</span>) } </td>
              <td className="tutkinto">{ opiskeluoikeus.suoritukset.map((suoritus, j) =>
                <span key={j}>
                  {
                    <span className="koulutusmoduuli">{suoritus.koulutusmoduuli.tunniste.nimi.fi}</span>
                  }
                  {
                    (suoritus.osaamisala || []).map((osaamisala, k) => <span className="osaamisala" key={k}>{osaamisala.nimi.fi}</span>)
                  }
                  {
                    (suoritus.tutkintonimike || []).map((nimike, k) => <span className="tutkintonimike" key={k}>{nimike.nimi.fi}</span>)
                  }
                </span>
              )}
              </td>
              <td className="tila">{ opiskeluoikeus.tila.nimi.fi }</td>
              <td className="oppilaitos">{ opiskeluoikeus.oppilaitos.nimi.fi }</td>
              <td className="aloitus pvm">{ ISO2FinnishDate(opiskeluoikeus.alkamispäivä) }</td>
              <td className="luokka">{ opiskeluoikeus.luokka }</td>
            </tr>)
          }
          </tbody>
        </table>) : <div className="ajax-indicator-bg">Ladataan...</div> }
      <PaginationLink pager={pager}/>
    </div>)
  },
  componentWillMount() {
    this.sortBus = Bacon.Bus()
    this.filterBus = Bacon.Bus()
  },
  componentDidMount() {
    const toParameterPairs = params => R.filter(([, value]) => !!value, R.toPairs(R.merge(this.props.params, params)))

    this.sortBus.merge(this.filterBus.throttle(500))
      .map(param => R.join('&', R.map(R.join('='), toParameterPairs(param))))
      .onValue(query => navigateTo(`/koski/?${query}`))
  }
})

const Sorter = React.createClass({
  render() {
    let { field, sortBus, sortBy, sortOrder } = this.props
    let selected = sortBy == field

    return (<div className="sorting" onClick={() => sortBus.push({ sort: field + ':' + (selected ? (sortOrder == 'asc' ? 'desc' : 'asc') : 'asc')})}>
      <div className="title">{this.props.children}</div>
      <div className="sort-indicator">
        <div className={selected && sortOrder == 'asc' ? 'asc selected' : 'asc'}></div>
        <div className={selected && sortOrder == 'desc' ? 'desc selected' : 'desc'}></div>
      </div>
    </div>)
  }
})

const TextFilter = React.createClass({
  render() {
    let { field, filterBus, params} = this.props
    return <input type="text" defaultValue={params[field]} onChange={e => filterBus.push(R.objOf(field, e.target.value))}/>
  }
})

var edellisetRivit = null

export const oppijataulukkoContentP = (query, params) => {
  let pager = Pager('/koski/api/opiskeluoikeus/perustiedot' + query)
  let taulukkoContentP = pager.rowsP.doAction((rivit) => edellisetRivit = rivit).startWith(null).map((rivit) => <Oppijataulukko rivit={rivit} edellisetRivit={edellisetRivit} pager={pager} params={params}/>)
  return Bacon.combineWith(taulukkoContentP, oppijaHakuElementP, (taulukko, hakuElement) => ({
    content: (<div className='content-area'>
      { hakuElement }
      <div className="main-content">
      { taulukko }
      </div>
    </div>),
    title: ''
  }))
}