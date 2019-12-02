// Empty constructor
function ArcGISMapPlugin() {}

// The function that passes work along to native shells
// Message is a string, duration may be 'long' or 'short'
ArcGISMapPlugin.prototype.show = function(message, duration, successCallback, errorCallback) {
  var options = {};
  options.message = message;
  options.duration = duration;
  cordova.exec(successCallback, errorCallback, 'ArcGISMapPlugin', 'show', [options]);
}

// Installation constructor that binds ArcGISMapPlugin to window
ArcGISMapPlugin.install = function() {
  if (!window.plugins) {
    window.plugins = {};
  }
  window.plugins.ArcGISMapPlugin = new ArcGISMapPlugin();
  return window.plugins.ArcGISMapPlugin;
};
cordova.addConstructor(ArcGISMapPlugin.install);