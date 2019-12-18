package com.experieco.plugin;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginEntry;
import org.apache.cordova.PluginManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;

@SuppressWarnings("deprecation")
public class CordovaArcGISMap extends CordovaPlugin implements ViewTreeObserver.OnScrollChangedListener{
  private Activity activity;
  public ViewGroup root;
  public MyPluginLayout mPluginLayout = null;
  public boolean initialized = false;
  public PluginManager pluginManager;
  private static final Object timerLock = new Object();

  @SuppressLint("NewApi") @Override
  public void initialize(final CordovaInterface cordova, final CordovaWebView webView) {
    super.initialize(cordova, webView);
    if (root != null) {
      return;
    }
    LOG.setLogLevel(LOG.ERROR);

    activity = cordova.getActivity();
    final View view = webView.getView();
    view.getViewTreeObserver().addOnScrollChangedListener(CordovaArcGISMap.this);
    root = (ViewGroup) view.getParent();

    pluginManager = webView.getPluginManager();

    cordova.getActivity().runOnUiThread(new Runnable() {
      @SuppressLint("NewApi")
      public void run() {
        webView.getView().setBackgroundColor(Color.TRANSPARENT);
        webView.getView().setOverScrollMode(View.OVER_SCROLL_NEVER);
        mPluginLayout = new MyPluginLayout(webView, activity);
        mPluginLayout.stopTimer();
      }
    });
  }

  @Override
  public boolean onOverrideUrlLoading(String url) {
    mPluginLayout.stopTimer();
    return false;
  }

  @Override
  public void onScrollChanged() {
    if (mPluginLayout == null) {
      return;
    }
    View view = webView.getView();
    int scrollX = view.getScrollX();
    int scrollY = view.getScrollY();
    mPluginLayout.scrollTo(scrollX, scrollY);
  }

  @Override
  public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    cordova.getThreadPool().submit(new Runnable() {
      @Override
      public void run() {
        try {
          if (action.equals("putHtmlElements")) {
            CordovaArcGISMap.this.putHtmlElements(args, callbackContext);
          } else if ("clearHtmlElements".equals(action)) {
            CordovaArcGISMap.this.clearHtmlElements(args, callbackContext);
          } else if ("pause".equals(action)) {
            CordovaArcGISMap.this.pause(args, callbackContext);
          } else if ("resume".equals(action)) {
            CordovaArcGISMap.this.resume(args, callbackContext);
          } else if ("getMap".equals(action)) {
            CordovaArcGISMap.this.getMap(args, callbackContext);
          } else if ("removeMap".equals(action)) {
            CordovaArcGISMap.this.removeMap(args, callbackContext);
          } else if ("updateMapPositionOnly".equals(action)) {
            CordovaArcGISMap.this.updateMapPositionOnly(args, callbackContext);
          }
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }
    });
    return true;
  }

  public void updateMapPositionOnly(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    final JSONObject elements = args.getJSONObject(0);

    Bundle elementsBundle = PluginUtil.Json2Bundle(elements);
    float zoomScale = Resources.getSystem().getDisplayMetrics().density;

    Iterator<String> domIDs = elementsBundle.keySet().iterator();
    String domId;
    Bundle domInfo, size, currentDomInfo;
    while (domIDs.hasNext()) {
      domId = domIDs.next();
      domInfo = elementsBundle.getBundle(domId);

      size = domInfo.getBundle("size");
      RectF rectF = new RectF();
      rectF.left = (float)(Double.parseDouble(size.get("left") + "") * zoomScale);
      rectF.top = (float)(Double.parseDouble(size.get("top") + "") * zoomScale);
      rectF.right = rectF.left  + (float)(Double.parseDouble(size.get("width") + "") * zoomScale);
      rectF.bottom = rectF.top  + (float)(Double.parseDouble(size.get("height") + "") * zoomScale);

      mPluginLayout.HTMLNodeRectFs.put(domId, rectF);
    }

    if (mPluginLayout.isSuspended) {
      mPluginLayout.updateMapPositions();
    }
    callbackContext.success();
  }

  public synchronized void pause(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    synchronized (timerLock) {
      if (mPluginLayout == null) {
        callbackContext.success();
        return;
      }
      mPluginLayout.stopTimer();
      callbackContext.success();
    }
  }
  public synchronized void resume(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    synchronized (timerLock) {
      if (mPluginLayout == null) {
        callbackContext.success();
        return;
      }
      if (mPluginLayout.isSuspended) {
        mPluginLayout.startTimer();
      }
      callbackContext.success();

      //On resume reapply background because it might have been changed by some other plugin
      webView.getView().setBackgroundColor(Color.TRANSPARENT);
    }
  }

  public void clearHtmlElements(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (mPluginLayout == null) {
      callbackContext.success();
      return;
    }
    mPluginLayout.clearHtmlElements();
    callbackContext.success();
  }

  public void putHtmlElements(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    final JSONObject elements = args.getJSONObject(0);
    if (mPluginLayout == null) {
      callbackContext.success();
      return;
    }

    if (!mPluginLayout.stopFlag || mPluginLayout.needUpdatePosition) {
      mPluginLayout.putHTMLElements(elements);
    }
    callbackContext.success();
  }

  @Override
  public void onReset() {
    super.onReset();
    if (mPluginLayout == null || mPluginLayout.pluginOverlays == null) {
      return;
    }

    cordova.getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mPluginLayout.setBackgroundColor(Color.WHITE);
        Set<String> mapIds = mPluginLayout.pluginOverlays.keySet();
        IPluginView pluginOverlay;

        // prevent the ConcurrentModificationException error.
        String[] mapIdArray= mapIds.toArray(new String[mapIds.size()]);
        for (String mapId : mapIdArray) {
          if (mPluginLayout.pluginOverlays.containsKey(mapId)) {
            pluginOverlay = mPluginLayout.removePluginOverlay(mapId);
            pluginOverlay.remove(null, null);
            pluginOverlay.onDestroy();
            mPluginLayout.HTMLNodes.remove(mapId);
          }
        }
        mPluginLayout.HTMLNodes.clear();
        mPluginLayout.pluginOverlays.clear();

        System.gc();
        Runtime.getRuntime().gc();
      }
    });
  }

  public void removeMap(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String mapId = args.getString(0);
    if (mPluginLayout.pluginOverlays.containsKey(mapId)) {
      IPluginView pluginOverlay = mPluginLayout.removePluginOverlay(mapId);
      if (pluginOverlay != null) {
        pluginOverlay.remove(null, null);
        pluginOverlay.onDestroy();
        mPluginLayout.HTMLNodes.remove(mapId);
        pluginOverlay = null;
      }

      try {
        Field pluginMapField = pluginManager.getClass().getDeclaredField("pluginMap");
        pluginMapField.setAccessible(true);
        LinkedHashMap<String, CordovaPlugin> pluginMapInstance = (LinkedHashMap<String, CordovaPlugin>) pluginMapField.get(pluginManager);
        pluginMapInstance.remove(mapId);
        Field entryMapField = pluginManager.getClass().getDeclaredField("entryMap");
        entryMapField.setAccessible(true);
        LinkedHashMap<String, PluginEntry> entryMapInstance = (LinkedHashMap<String, PluginEntry>) entryMapField.get(pluginManager);
        entryMapInstance.remove(mapId);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    System.gc();
    Runtime.getRuntime().gc();
    callbackContext.success();
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public void getMap(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    JSONObject meta = args.getJSONObject(0);
    String mapId = meta.getString("__pgmId");
    PluginArcGISMap pluginMap = new PluginArcGISMap(cordova.getContext());
    pluginMap.privateInitialize(mapId, cordova, webView, null);
    pluginMap.initialize(cordova, webView);
    pluginMap.mapCtrl = CordovaArcGISMap.this;
    pluginMap.self = pluginMap;

    PluginEntry pluginEntry = new PluginEntry(mapId, pluginMap);
    pluginManager.addService(pluginEntry);

    pluginMap.getMap(args, callbackContext);
  }

  @Override
  public void onStart() {
    super.onStart();
    Collection<PluginEntry>pluginEntries = pluginManager.getPluginEntries();
    for (PluginEntry pluginEntry: pluginEntries) {
      if (pluginEntry.service.startsWith("map_")) {
        pluginEntry.plugin.onStart();
      }
    }
  }

  @Override
  public void onStop() {
    super.onStop();

    Collection<PluginEntry>pluginEntries = pluginManager.getPluginEntries();
    for (PluginEntry pluginEntry: pluginEntries) {
      if (pluginEntry.service.startsWith("map_")) {
        pluginEntry.plugin.onStop();
      }
    }
  }

  @Override
  public void onPause(boolean multitasking) {
    super.onPause(multitasking);
    if (mPluginLayout != null) {
      mPluginLayout.stopTimer();
    }

    Collection<PluginEntry>pluginEntries = pluginManager.getPluginEntries();
    for (PluginEntry pluginEntry: pluginEntries) {
      if (pluginEntry.service.startsWith("map_")) {
        pluginEntry.plugin.onPause(multitasking);
      }
    }
  }

  @Override
  public void onResume(boolean multitasking) {
    Collection<PluginEntry>pluginEntries = pluginManager.getPluginEntries();
    for (PluginEntry pluginEntry: pluginEntries) {
      if (pluginEntry.service.startsWith("map_")) {
        pluginEntry.plugin.onResume(multitasking);
      }
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    Collection<PluginEntry>pluginEntries = pluginManager.getPluginEntries();
    for (PluginEntry pluginEntry: pluginEntries) {
      if (pluginEntry.service.startsWith("map_")) {
        pluginEntry.plugin.onDestroy();
      }
    }
  }
}
