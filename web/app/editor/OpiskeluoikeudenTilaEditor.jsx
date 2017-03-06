import React from 'react'
import {childContext, contextualizeModel, modelItems, modelLookup} from './EditorModel.js'
import {ArrayEditor} from './ArrayEditor.jsx'
import {OpiskeluoikeusjaksoEditor} from './OpiskeluoikeusjaksoEditor.jsx'
import {PropertyEditor} from './PropertyEditor.jsx'

export const OpiskeluoikeudenTilaEditor = React.createClass({
  render() {
    let {model} = this.props
    let adding = this.state && this.state.adding || []

    let opiskeluoikeusjaksot = modelLookup(model, 'opiskeluoikeusjaksot')
    let items = (this.state && this.state.items) || modelItems(opiskeluoikeusjaksot)

    let addItem = () => {
       this.setState({adding: true})
    }

    return (
      model.context.edit ?
        <div>
          <ul ref="ul" className="array">
          {
            items.map((item, i) => {
              return <li key={i}><OpiskeluoikeusjaksoEditor model={item}/></li>
            })
          }
          {
            <li className="add-item"><a onClick={addItem}>Lisää opiskeluoikeuden tila</a></li>
          }
          </ul>
          {
            adding && (<div className="lisaa-opiskeluoikeusjakso">
              <PropertyEditor model={contextualizeModel(opiskeluoikeusjaksot, childContext(model.context, items.length + adding.length))}/>
            </div>)
          }
        </div> :
        <div><ArrayEditor reverse={true} model={opiskeluoikeusjaksot}/></div>
    )
  }
})
