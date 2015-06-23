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

    Response:function(ok,data,endPointId,exception){
      this.ok = ok;
      this.data = data;
      this.endPointId = endPointId;
      this.exception = exception;
    },

    Request:function(id,data,senderId,receiverId,forTags){
      this.id = id;
      this.data =data;
      this.senderId = senderId;
      this.receiverId = receiverId;
      this.forTags = forTags;
    },

    Message:function(data,senderId,receiverId,forTags){
      this.data = data;
      this.senderId = senderId;
      this.receiverId = receiverId;
      this.forTags = forTags;
    },

    ClientEndPoint:function(webSocket){
      var requestMap = { };
      var defaultTimeout = 10000;
      var self = this;
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
            self.runMessageReceived(json);

          }else if (json.type===namespace.TransportPackage.Request ||
                    json.type===namespace.TransportPackage.RouteRequest ||
                    json.type===namespace.TransportPackage.RouteRequestAny
          ){
            self.runRequestReceived(json);
          }else if (json.type===namespace.TransportPackage.Response ||
                    json.type===namespace.TransportPackage.RouteResponse ||
                    json.type===namespace.TransportPackage.RouteResponseAny
          ){
            self.runResponseReceived(json);
          }
        }
      };
      //------------------------------------------------------------------------
      this.runMessageReceived = function(transportPackage){
        if (namespace.isFunction(this.onMessageReceived)){
          var message = new namespace.Message(
            transportPackage.data,transportPackage.from
            ,transportPackage.to,transportPackage.tags);
          this.onMessageReceived(message);
        }
      };
      //------------------------------------------------------------------------
      this.runRequestReceived=function(transportPackage){
        if (namespace.isFunction(this.onRequestReceived)){
          var request = new namespace.Request(
            transportPackage.id,transportPackage.data
            ,transportPackage.from,transportPackage.to,transportPackage.tags);
          var response = this.onRequestReceived(request);
          var responseType = namespace.TransportPackage.Response;
          if (transportPackage.type == namespace.TransportPackage.RouteRequestAny)
            responseType = namespace.TransportPackage.RouteRequestAny;
          else if (transportPackage.type == namespace.TransportPackage.RouteRequest)
            responseType = namespace.TransportPackage.RouteRequest;
          else responseType = namespace.TransportPackage.Response;
          if (response!=null){
            var responseTransportPackage = {
              from:request.receiverId,
              to:request.senderId,
              tags:request.forTags,
              id : request.id,
              data:response,
              type:responseType
            };
            var json = JSON.stringify(responseTransportPackage);
            webSocket.send(json);
          }
        }
      };
      //------------------------------------------------------------------------
      this.runResponseReceived = function(transportPackage){
        var requestId = transportPackage.id;
        if (requestId in requestMap) {
          var defer = requestMap[requestId];
          var ok = true;
          if (transportPackage.error)
            ok = false;
          var response = new namespace.Response(
            ok,transportPackage.data,transportPackage.from,transportPackage.error);
          if (response.ok)
            defer.resolve(response);
          else
            defer.reject(response);
          delete requestMap[requestId];
        }
      };
      //------------------------------------------------------------------------
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

    },

    isFunction:function(functionToCheck) {
      var getType = {};
      return functionToCheck && getType.toString.call(functionToCheck) === '[object Function]';
    }

  };
  return namespace;
});