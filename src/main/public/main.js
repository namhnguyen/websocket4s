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
  clientEndPoint.onMessageReceived = function(message){
    console.log(message.data);
  };
  clientEndPoint.onRequestReceived = function(request){
    console.log(request.data);
    return "response for ["+request.data+"]";
  };
  //console.log(websocket4s.newID());
  //setTimeout(function() {
  //    for (var i = 1 ;i< 20;i++) {
  //      var r = clientEndPoint.askServer("test message "+i);
  //      r.then(function (a) {
  //        console.log(a)
  //      }, function () {
  //      });
  //    }
  //  },
  //  3000)
});