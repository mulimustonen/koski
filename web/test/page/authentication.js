function Authentication() {
  return {
    login: function(username) {
      if (!username) username = 'kalle'
      return function() {
        return postJson('/tor/user/login', {username: username, password: 'asdf'})
      }
    },
    logout: function() {
      return Q($.ajax('/tor/user/logout'))
    }
  }
}