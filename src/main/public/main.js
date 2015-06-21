require.config({
  paths: {
    "websocket4s":"js/websocket4s"
  },
  packages: [
    { name: "rest", location: "bower_components/rest", main: "browser"},
    { name: "when", location: "bower_components/when", main: "when"}
  ]
})

require(["websocket4s"],function(websocket4s){
  var webSocket = new WebSocket("ws://localhost:8080/");
  var clientEndPoint = new websocket4s.ClientEndPoint(webSocket);
  clientEndPoint.onmessage = function(event){
    console.log(event.data)
  };
  //console.log(websocket4s.newID());
  setTimeout(function() {
      var r = clientEndPoint.askServer("test message");
      r.then(function(a){console.log(a)},function(){});
    },
    3000)
});