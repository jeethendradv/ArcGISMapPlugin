if (!cordova) {
  document.addEventListener('deviceready', function () {
    require('cordova/exec')(null, null, 'CordovaArcGISMap', 'pause', []);
  }, {
    once: true
  });
} else {
  var common = require('./Common');

  cordova.addConstructor(function () {
    if (!window.Cordova) {
      window.Cordova = cordova;
    }
    window.plugin = window.plugin || {};
    window.plugin.arcgis = window.plugin.arcgis || {};
    window.plugin.arcgis.maps = window.plugin.arcgis.maps || module.exports;

    document.addEventListener('deviceready', function () {
      // workaround for issue on android-19: Cannot read property 'maps' of undefined
      if (!window.plugin) {
        console.warn('re-init window.plugin');
        window.plugin = window.plugin || {};
      }
      if (!window.plugin.arcgis) {
        console.warn('re-init window.plugin.arcgis');
        window.plugin.arcgis = window.plugin.arcgis || {};
      }
      if (!window.plugin.arcgis.maps) {
        console.warn('re-init window.plugin.arcgis.maps');
        window.plugin.arcgis.maps = window.plugin.arcgis.maps || module.exports;
      }

      cordova.exec(null, function (message) {
        alert(message);
      }, 'PluginEnvironment', 'isAvailable', ['']);
    }, {
      once: true
    });
  });

  var execCmd = require('./commandQueueExecutor');
  var cordovaArcGISMap = new(require('./js_CordovaArcGISMap'))(execCmd);

  (new Promise(function (resolve) {
    var wait = function () {
      if (document.body) {
        wait = undefined;
        cordovaArcGISMap.trigger('start');
        resolve();
      } else {
        setTimeout(wait, 50);
      }
    };
    wait();
  })).then(function () {
    // The pluginInit.js must execute before loading HTML is completed.
    require('./pluginInit')();

    common.nextTick(function () {
      // If the developer needs to recalculate the DOM tree graph,
      // use `cordova.fireDocumentEvent('plugin_touch')`
      document.addEventListener('plugin_touch', cordovaArcGISMap.invalidate.bind(cordovaArcGISMap));

      // Repositioning 30 times when the device orientaion is changed.
      window.addEventListener('orientationchange', cordovaArcGISMap.followMaps.bind(cordovaArcGISMap, {
        target: document.body
      }));

      document.addEventListener('transitionstart', cordovaArcGISMap.followMaps.bind(cordovaArcGISMap), {
        capture: true
      });
      document.body.parentNode.addEventListener('transitionend', cordovaArcGISMap.onTransitionEnd.bind(cordovaArcGISMap), {
        capture: true
      });

      // If the `scroll` event is ocurred on the observed element,
      // adjust the position and size of the map view
      document.body.parentNode.addEventListener('scroll', cordovaArcGISMap.followMaps.bind(cordovaArcGISMap), true);
      window.addEventListener('resize', function () {
        cordovaArcGISMap.transforming = true;
        cordovaArcGISMap.onTransitionFinish.call(cordovaArcGISMap);
      }, true);

    });
  });

  /*****************************************************************************
   * Name space
   *****************************************************************************/
  module.exports = {
    event: require('./event'),
    Animation: {
      BOUNCE: 'BOUNCE',
      DROP: 'DROP'
    },

    BaseClass: require('./BaseClass'),
    BaseArrayClass: require('./BaseArrayClass'),

    /** @namespace plugin.google.maps.Map */
    Map: {
      getMap: cordovaArcGISMap.getMap.bind(cordovaArcGISMap)
    }
  };
}
