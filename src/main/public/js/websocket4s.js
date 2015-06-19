define(function(require){
  var when = require("when");

  var namespace = {
    timeOut:"Request TimeOut!",

    newID: function(){
      var text = "";
      var possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

      for( var i=0; i < 15; i++ )
        text += possible.charAt(Math.floor(Math.random() * possible.length));

      return text;
    },

    TransportPackage:{
      Message:"M",
      Request:"R",
      Response : "P",
      RouteRequest : "RR",
      RouteRequestAny : "RRA",
      RouteResponse : "RP",
      RouteResponseAny : "RPA",
      RouteMessage :"RM"
    },

    ClientEndPoint:function(webSocket){
      var requestMap = { };
      var defaultTimeout = 10000;
      //------------------------------------------------------------------------
      //public interface

      this.onMessageReceived = null;
      this.onRequestReceived = null;

      this.tellServer = function(message) {
        var obj = { data:message, type: namespace.TransportPackage.Message };
        console.log(JSON.stringify(obj))
      };

      this.tellClient = function(id,message){
        var obj = { to:id,data:message,type:namespace.TransportPackage.RouteMessage };
        console.log(JSON.stringify(obj));
      };

      this.tellTags = function(tags,message){
        var obj = { tags:tags,data:message,type:namespace.TransportPackage.RouteMessage };
        console.log(JSON.stringify(obj));
      };

      this.askServer = function(request,timeout) {
        var toTimeOut = defaultTimeout;
        if(typeof timeout !== "undefined") { toTimeOut = timeout; }
        var requestId =namespace.newID();
        var obj = { id:requestId,data:request,type:namespace.TransportPackage.Request };
        var msg = JSON.stringify(obj);
        console.log(msg);
        var d = when.defer();
        requestMap[requestId] = d;
        setTimeout(function(){
          if (requestId in requestMap) {
            delete requestMap[requestId];
            d.reject(namespace.timeOut);
          }
        },toTimeOut);
        webSocket.send(msg);
        return d.promise;
      };

      this.askClient = function(id,request,timeout){
        var toTimeOut = defaultTimeout;
        if(typeof timeout !== "undefined") { toTimeOut = timeout; }
        var requestId =namespace.newID();
        var obj = { id:requestId,to:id,data:request,type:namespace.TransportPackage.RouteRequest };
        var msg = JSON.stringify(obj);
        console.log(msg);
        var d = when.defer();
        requestMap[requestId] = d;
        setTimeout(function(){
          if (requestId in requestMap) {
            delete requestMap[requestId];
            d.reject(namespace.timeOut);
          }
        },toTimeOut);
        webSocket.send(msg);
        return d.promise;
      };

      this.askTags = function(tags,request,timeout){
        var toTimeOut = defaultTimeout;
        if(typeof timeout !== "undefined") { toTimeOut = timeout; }
        var requestId =namespace.newID();
        var obj = { id:requestId,tags:tags,data:request,type:namespace.TransportPackage.RouteRequestAny };
        var msg = JSON.stringify(obj);
        console.log(msg);
        var d = when.defer();
        requestMap[requestId] = d;
        setTimeout(function(){
          if (requestId in requestMap) {
            delete requestMap[requestId];
            d.reject(namespace.timeOut);
          }
        },toTimeOut);
        webSocket.send(msg);
        return d.promise;
      };

      //------------------------------------------------------------------------
      //private
      webSocket.onmessage = function(event){
        var data = event.data;
        var json = JSON.parse(data);
        if (json.type){
          if (json.type===namespace.TransportPackage.Message||
            json.type===namespace.TransportPackage.RouteMessage){
            
          }
        }
      };
      //
      //webSocket.onclose = function(event){
      //
      //};
      //
      //webSocket.onerror = function(event){
      //
      //};
      //
      //webSocket.onopen = function(event){
      //
      //};

    }
  };
  return namespace;
});