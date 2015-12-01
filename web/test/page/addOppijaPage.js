function AddOppijaPage() {
  function form() { return S('form.oppija') }
  function button() { return form().find('button') }
  function selectedOppilaitos() { return form().find('.oppilaitos .selected') }
  function selectedTutkinto() { return form().find('.tutkinto .selected') }
  var pageApi = Page(form)
  var api = {
    isVisible: function() {
      return form().is(':visible') && !TorPage().isLoading()
    },
    isEnabled: function() {
      return !button().is(':disabled')
    },
    tutkintoIsEnabled: function() {
      return !S('.tutkinto input').is(':disabled')
    },
    enterValidData: function(params) {
      var self = this
      params = _.merge({ etunimet: 'Ossi Olavi', kutsumanimi: 'Ossi', sukunimi: 'Oppija', hetu: '300994-9694', oppilaitos: 'Helsingin', tutkinto: 'auto'}, {}, params)
      return function() {
        return pageApi.setInputValue('.etunimet input', params.etunimet)()
          .then(pageApi.setInputValue('.kutsumanimi input', params.kutsumanimi))
          .then(pageApi.setInputValue('.sukunimi input', params.sukunimi))
          .then(pageApi.setInputValue('.hetu input', params.hetu))
          .then(self.selectOppilaitos(params.oppilaitos))
          .then(self.selectTutkinto(params.tutkinto))
      }
    },
    enterTutkinto: function(name) {
      return function() {
        return pageApi.setInputValue('.tutkinto input', name)()
      }
    },
    enterOppilaitos: function(name) {
      return function() {
        return pageApi.setInputValue('.oppilaitos input', name)().then(wait.forAjax())
      }
    },
    selectOppilaitos: function(name) {
      return function() {
        return pageApi.setInputValue('.oppilaitos input', name)()
          .then(wait.until(function() { return selectedOppilaitos().is(':visible') }))
          .then(function() {triggerEvent(selectedOppilaitos(), 'click')})
      }
    },
    oppilaitokset: function() {
      return textsOf(form().find('.oppilaitos .results li'))
    },
    selectTutkinto: function(name) {
      return function() {
        return pageApi.setInputValue('.tutkinto input', name)()
          .then(wait.until(function() { return selectedTutkinto().is(':visible') }))
          .then(function() {triggerEvent(selectedTutkinto(), 'click')})
      }
    },
    submit: function() {
      triggerEvent(button(), 'click')
    },
    submitAndExpectSuccess: function(oppija) {
      return function() {
        api.submit()
        return wait.until(function() {
          // TODO, fix tutkinto check
          return TorPage().getSelectedOppija().indexOf(oppija) >= 0 && (OpinnotPage().getTutkinto().indexOf("Autoalan") >= 0)
        })()
      }
    },
    isErrorShown: function(field) {
      return function() {
        return form().find('.error-messages .' + field).is(':visible')
      }
    },
    putTutkinnonOsaSuoritusAjax: function(suoritus) {
      return function() {
        suoritus = _.merge(defaultSuoritus(), suoritus)
        var opiskeluOikeus = defaultOpiskeluOikeus();
        opiskeluOikeus.suoritus.osasuoritukset = [
          suoritus
        ]
        return api.putOpiskeluOikeusAjax(opiskeluOikeus)()
      }
    },

    putOpiskeluOikeusAjax: function(opiskeluOikeus) {
      return function() {
        opiskeluOikeus = _.merge(defaultOpiskeluOikeus(), opiskeluOikeus)
        data = makeOppija({}, [opiskeluOikeus])
        return api.putOppijaAjax(data)()
      }
    },
    putOppijaAjax: function(oppija) {
      return function() {
        var defaults = makeOppija(defaultHenkilo(), [defaultOpiskeluOikeus()])
        oppija = _.merge(defaults, {}, oppija)
        return putJson(
          'http://localhost:7021/tor/api/oppija', oppija
        )
      }
    }
  }

  function defaultSuoritus() {
    return {
      "koulutusmoduulitoteutus": {
        "koulutusmoduuli": {
          "tunniste": {"koodiarvo": "100023", "nimi": "Markkinointi ja asiakaspalvelu", "koodistoUri": "tutkinnonosat", "koodistoVersio": 1},
          "pakollinen": true,
          "laajuus": {"arvo": 11, "yksikkö": {"koodiarvo": "6", "koodistoUri": "opintojenlaajuusyksikko"}}
        }
      },
      toimipiste: {oid: "1.2.246.562.10.42456023292", nimi: "Stadin ammattiopisto, Lehtikuusentien toimipaikka"}
    }
  }

  function defaultOpiskeluOikeus() { return {
    oppilaitos: { oid: '1' },
    suoritus: {
      koulutusmoduulitoteutus: {
        koulutusmoduuli: {
          tunniste: {
            "koodiarvo": "351301",
            "nimi": "Autoalan perustutkinto",
            "koodistoUri": "koulutus"
          },
          perusteenDiaarinumero: '39/011/2014'
        }
      },
      "toimipiste": {
        "oid": "1.2.246.562.10.42456023292",
        "nimi": "Stadin ammattiopisto, Lehtikuusentien toimipaikka"
      }
    },
  }}
  function defaultHenkilo() {return {
    'etunimet':'Testi',
    'sukunimi':'Toivola',
    'kutsumanimi':'Testi',
    'hetu':'010101-123N'
  }}
  function makeOppija(henkilö, opiskeluoikeudet) { return _.cloneDeep({
    henkilö: henkilö || defaultHenkilo(),
    opiskeluoikeudet: opiskeluoikeudet || [defaultOpiskeluOikeus()]
  })}
  return api
}