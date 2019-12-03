// Empty constructor
function ArcGISMapPlugin() {}

function getDivRect(div) {
  if (!div) {
    return;
  }
  var rect;
  if (div === document.body) {
    rect = div.getBoundingClientRect();
    rect.left = Math.max(rect.left, window.pageOffsetX);
    rect.top = Math.max(rect.top, window.pageOffsetY);
    rect.width = Math.max(rect.width, window.innerWidth);
    rect.height = Math.max(rect.height, window.innerHeight);
    rect.right = rect.left + rect.width;
    rect.bottom = rect.top + rect.height;
  } else {
    rect = div.getBoundingClientRect();
    if ('right' in rect === false) {
      rect.right = rect.left + rect.width;
    }
    if ('bottom' in rect === false) {
      rect.bottom = rect.top + rect.height;
    }
  }
  return {
    left: rect.left,
    top: rect.top,
    width: rect.width,
    height: rect.height,
    right: rect.right,
    bottom: rect.bottom
  };
}

// The function that passes work along to native shells
// Message is a string, duration may be 'long' or 'short'
ArcGISMapPlugin.prototype.show = function(divId, successCallback, errorCallback) {
    var div = document.getElementById(divId);
    if (div) {        
        var rect = getDivRect(div);
        var options = {};
        options.mapRect = rect;
        cordova.exec(successCallback, errorCallback, 'ArcGISMapPlugin', 'show', [options]);
    }
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
