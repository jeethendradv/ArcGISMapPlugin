var utils = require('cordova/utils'),
  cordova_exec = require('cordova/exec'),  
  common = require('./Common'),
  Overlay = require('./Overlay'),
  BaseClass = require('./BaseClass'),
  BaseArrayClass = require('./BaseArrayClass'),  
  event = require('./event');

/**
 * Google Maps model.
 */
var exec;
var Map = function(__pgmId, _exec) {
  var self = this;
  exec = _exec;
  Overlay.call(self, self, {}, 'Map', _exec, {
    __pgmId: __pgmId
  });
  delete self.map;


  self.set('myLocation', false);
  self.set('myLocationButton', false);

  self.MARKERS = {};
  self.OVERLAYS = {};
};
utils.extend(Map, Overlay);

/**
 * @desc Recalculate the position of HTML elements
 */
Map.prototype.refreshLayout = function() {
  // Webkit redraw mandatory
  // http://stackoverflow.com/a/3485654/697856
  document.body.style.display = 'none';
  document.body.offsetHeight;
  document.body.style.display = '';

  this.exec.call(this, null, null, this.__pgmId, 'resizeMap', []);
};

Map.prototype.getMap = function(meta, div, options) {
  var self = this,
    args = [meta];
  options = options || {};

  self.set('clickable', options.clickable === false ? false : true);
  self.set('visible', options.visible === false ? false : true);

  if (!common.isDom(div)) {
    self.set('visible', false);
    options = div;
    options = options || {};
  } else {
    var positionCSS = common.getStyle(div, 'position');
    if (!positionCSS || positionCSS === 'static') {
      // important for HtmlInfoWindow
      div.style.position = 'relative';
    }
    options = options || {};
    
    args.push(options);
    div.style.overflow = 'hidden';
    self.set('div', div);

    if (div.offsetWidth < 100 || div.offsetHeight < 100) {
      // If the map Div is too small, wait a little.
      var callee = arguments.callee;
      setTimeout(function() {
        callee.call(self, meta, div, options);
      }, 250 + Math.random() * 100);
      return;
    }

    // Gets the map div size.
    // The plugin needs to consider the viewport zoom ratio
    // for the case window.innerHTML > body.offsetWidth.
    var elemId = common.getPluginDomId(div);
    args.push(elemId);
  }

  exec.call({
    _isReady: true
  }, function() {
    //------------------------------------------------------------------------
    // Clear background colors of map div parents after the map is created
    //------------------------------------------------------------------------
    var div = self.get('div');
    if (common.isDom(div)) {
      var positionCSS;
      for (var i = 0; i < div.children.length; i++) {
        positionCSS = common.getStyle(div.children[i], 'position');
        if (positionCSS === 'static') {
          div.children[i].style.position = 'relative';
        }
      }

      while (div.parentNode) {
        div.style.backgroundColor = 'rgba(0,0,0,0) !important';
        // Add _gmaps_cdv_ class
        common.attachTransparentClass(div);
        div = div.parentNode;
      }
    }
    cordova.fireDocumentEvent('plugin_touch', {
      force: true
    });
  }, self.errorHandler, 'CordovaArcGISMap', 'getMap', args, {
    sync: true
  });
};

/**
 * Remove the map completely.
 */
Map.prototype.remove = function(callback) {
  var self = this;
  if (self._isRemoved) {
    return;
  }
  Object.defineProperty(self, '_isRemoved', {
    value: true,
    writable: false
  });

  self.trigger('remove');

  // Close the active infoWindow
  var active_marker = self.get('active_marker');
  if (active_marker) {
    active_marker.trigger(event.INFO_CLOSE);
  }

  var clearObj = function(obj) {
    var ids = Object.keys(obj);
    var id, instance;
    for (var i = 0; i < ids.length; i++) {
      id = ids[i];
      instance = obj[id];
      if (instance) {
        if (typeof instance.remove === 'function') {
          instance.remove();
        }
        instance.off();
        delete obj[id];
      }
    }
    obj = {};
  };

  clearObj(self.OVERLAYS);
  clearObj(self.MARKERS);


  var resolver = function(resolve, reject) {
    self.exec.call(self,
      resolve.bind(self),
      reject.bind(self),
      'CordovaArcGISMap', 'removeMap', [self.__pgmId],
      {
        sync: true,
        remove: true
      });
  };

  if (typeof callback === 'function') {
    resolver(callback, self.errorHandler);
  } else {
    return new Promise(resolver);
  }
};

Map.prototype.getVisible = function() {
  return this.get('visible');
};

/**
 * Show the map into the specified div.
 */
Map.prototype.getDiv = function() {
  return this.get('div');
};

module.exports = Map;
