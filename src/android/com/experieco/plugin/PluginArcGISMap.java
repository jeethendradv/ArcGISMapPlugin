package com.experieco.plugin;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

/*import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;*/

import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.mapping.ArcGISMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginEntry;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PluginArcGISMap extends MyPluginArcGISMap implements IPluginView {
    private Activity activity;
    public ArcGISMap map;
    private MapView mapView;
    private String mapId;
    private boolean isVisible = true;
    private boolean isClickable = true;
    private String mapDivId;
    public Map<String, PluginEntry> plugins = new ConcurrentHashMap<String, PluginEntry>();
    public final ObjectCache objects = new ObjectCache();
    public static final Object semaphore = new Object();
    private int viewDepth = 0;

    private Handler mainHandler;

    public int getViewDepth() {
        return viewDepth;
    }
    public String getDivId() {
        return this.mapDivId;
    }
    public String getOverlayId() {
        return this.mapId;
    }
    public ViewGroup getView() {
        return this.mapView;
    }
    public boolean getVisible() {
        return isVisible;
    }
    public boolean getClickable() {
        return isClickable;
    }

    @Override
    public void initialize(CordovaInterface cordova, final CordovaWebView webView) {
        super.initialize(cordova, webView);
        activity = cordova.getActivity();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void dispatchTouchEvent(MotionEvent event)
    {
        mapView.onTouchEvent(event);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void getMap(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        JSONObject meta = args.getJSONObject(0);
        mapId = meta.getString("__pgmId");
        viewDepth = meta.getInt("depth");

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mapView = new MapView(activity);
                mapView.setTag(getViewDepth());

                ArcGISMap map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC, -27.514975, 153.010068, 13);

                mapView.setMap(map);
                mapView.setVisibility(View.VISIBLE);
                mapView.setOnTouchListener(new DefaultMapViewOnTouchListener(mapView.getContext(), mapView){
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        // get the point that was clicked and convert it to a point in map coordinates
                        android.graphics.Point mClickPoint = new android.graphics.Point((int) e.getX(), (int) e.getY());
                        Point mapPoint = mapView.screenToLocation(mClickPoint);
                        Point wgs84Point = (Point) GeometryEngine.project(mapPoint, SpatialReferences.getWgs84());
                        String msg = "Lat: " +  String.format("%.4f", wgs84Point.getY()) + ", Lon: " + String.format("%.4f", wgs84Point.getX());

                        Toast toast = Toast.makeText(cordova.getActivity().getApplicationContext(), msg, Toast.LENGTH_LONG);
                        toast.show();
                        return super.onSingleTapConfirmed(e);
                    }
                });

                try {
                    // ------------------------------
                    // Embed the map if a container is specified.
                    // ------------------------------
                    if (args.length() == 3) {
                        mapDivId = args.getString(2);

                        mapCtrl.mPluginLayout.addPluginOverlay(PluginArcGISMap.this);
                        PluginArcGISMap.this.resizeMap(args, new PluginUtil.MyCallbackContext("dummy-" + map.hashCode(), webView) {
                            @Override
                            public void onResult(PluginResult pluginResult) {
                                mapView.setVisibility(View.VISIBLE);
                                callbackContext.success();
                            }
                        });
                    } else {
                        mapView.setVisibility(View.VISIBLE);
                        callbackContext.success();
                    }
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    public void attachToWebView(JSONArray args, final CallbackContext callbackContext) {
        mapCtrl.mPluginLayout.addPluginOverlay(this);
        callbackContext.success();
    }
    public void detachFromWebView(JSONArray args, final CallbackContext callbackContext)  {
        mapCtrl.mPluginLayout.removePluginOverlay(this.mapId);
        callbackContext.success();
    }

    public void resizeMap(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (mapCtrl.mPluginLayout == null || mapDivId == null) {
            callbackContext.success();
            return;
        }
        mapCtrl.mPluginLayout.needUpdatePosition = true;

        if (!mapCtrl.mPluginLayout.HTMLNodes.containsKey(mapDivId)) {
            Bundle dummyInfo = new Bundle();
            dummyInfo.putBoolean("isDummy", true);
            dummyInfo.putDouble("offsetX", 0);
            dummyInfo.putDouble("offsetY", 3000);

            Bundle dummySize = new Bundle();
            dummySize.putDouble("left", 0);
            dummySize.putDouble("top", 3000);
            dummySize.putDouble("width", 200);
            dummySize.putDouble("height", 200);
            dummyInfo.putBundle("size", dummySize);
            dummySize.putDouble("depth", -999);
            mapCtrl.mPluginLayout.HTMLNodes.put(mapDivId, dummyInfo);
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RectF drawRect = mapCtrl.mPluginLayout.HTMLNodeRectFs.get(mapDivId);
                if (drawRect != null) {
                    final int scrollY = webView.getView().getScrollY();

                    int width = (int) drawRect.width();
                    int height = (int) drawRect.height();
                    int x = (int) drawRect.left;
                    int y = (int) drawRect.top + scrollY;
                    ViewGroup.LayoutParams lParams = mapView.getLayoutParams();
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) lParams;

                    params.width = width;
                    params.height = height;
                    params.leftMargin = x;
                    params.topMargin = y;
                    mapView.setLayoutParams(params);

                    callbackContext.success();
                }
            }
        });
    }

    /**
     * Set clickable of the map
     * @param args Parameters given from JavaScript side
     * @param callbackContext Callback contect for sending back the result.
     * @throws JSONException
     */
    public void setClickable(JSONArray args, CallbackContext callbackContext) throws JSONException {
        boolean clickable = args.getBoolean(0);
        this.isClickable = clickable;
        callbackContext.success();
    }

    /**
     * Set visibility of the map
     * @param args Parameters given from JavaScript side
     * @param callbackContext Callback contect for sending back the result.
     * @throws JSONException
     */
    public void setVisible(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final boolean visible = args.getBoolean(0);
        this.isVisible = visible;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (visible) {
                    mapView.setVisibility(View.VISIBLE);
                } else {
                    mapView.setVisibility(View.INVISIBLE);
                }
                callbackContext.success();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.objects.clear();
        this.objects.destroy();
    }

    /**
     * Destroy the map completely
     * @param args Parameters given from JavaScript side
     * @param callbackContext Callback contect for sending back the result.
     */
    public void remove(JSONArray args, final CallbackContext callbackContext) {
        this.isClickable = false;
        this.isRemoved = true;

        try {
            PluginArcGISMap.this.clear(null, new PluginUtil.MyCallbackContext(mapId + "_remove", webView) {

                @Override
                public void onResult(PluginResult pluginResult) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mapCtrl.mPluginLayout.removePluginOverlay(mapId);
                            if (map != null) {
                                try {

                                } catch (SecurityException e) {
                                    e.printStackTrace();
                                }
                            }
                            if (mapView != null) {
                                try {
                                    mapView.clearAnimation();
                                    //mapView.onPause();
                                    //mapView.onDestroy();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            if (plugins.size() > 0) {
                                String[] pluginNames = plugins.keySet().toArray(new String[plugins.size()]);
                                PluginEntry pluginEntry;
                                for (int i = 0; i < pluginNames.length; i++) {
                                    pluginEntry = plugins.remove(pluginNames[i]);
                                    if (pluginEntry == null) {
                                        continue;
                                    }
                                    pluginEntry.plugin.onDestroy();
                                    ((MyPluginArcGISMap)pluginEntry.plugin).map = null;
                                    ((MyPluginArcGISMap)pluginEntry.plugin).mapCtrl = null;
                                    pluginEntry = null;
                                }
                            }
                            plugins = null;
                            map = null;
                            mapView = null;
                            activity = null;
                            mapId = null;
                            mapDivId = null;

                            System.gc();
                            Runtime.getRuntime().gc();
                            if (callbackContext != null) {
                                callbackContext.success();
                            }
                            PluginArcGISMap.this.onDestroy();
                        }
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Clear all markups
     * @param args Parameters given from JavaScript side
     * @param callbackContext Callback contect for sending back the result.
     * @throws JSONException
     */
    @SuppressWarnings("unused")
    public void clear(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Set<String> pluginNames = plugins.keySet();
        Iterator<String> iterator = pluginNames.iterator();
        String pluginName;
        PluginEntry pluginEntry;
        while(iterator.hasNext()) {
            pluginName = iterator.next();
            if (!"Map".equals(pluginName)) {
                pluginEntry = plugins.get(pluginName);
                ((MyPluginArcGISMap) pluginEntry.plugin).clear();
            }
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean isSuccess = false;
                while (!isSuccess) {
                    try {
                        //map.clear();
                        isSuccess = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        isSuccess = false;
                    }
                }
                if (callbackContext != null) {
                    callbackContext.success();
                }
            }
        });
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        synchronized (semaphore) {
            semaphore.notify();
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        if (mapView != null && mapView.isActivated()) {
            mapView.pause();
        }
        //mapCtrl.mPluginLayout.removePluginOverlay(this.mapId);
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        if (mapView != null && mapView.isActivated()) {
            mapView.resume();
        }
        //mapCtrl.mPluginLayout.addPluginOverlay(PluginMap.this);
    }
}