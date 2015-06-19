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
  var clientEndPoint = new websocket4s.ClientEndPoint();
  console.log(websocket4s.newID());
  clientEndPoint.askTags(["UI","Client2"],"test message");
})