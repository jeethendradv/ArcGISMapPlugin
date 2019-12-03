package com.experieco.plugin;

// The native Toast API
import android.app.Activity;
import android.graphics.Color;
import android.graphics.RectF;
import android.widget.Toast;

// Cordova-required packages
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;

import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.MapView;

/**
 * This class echoes a string called from JavaScript.
 */
public class ArcGISMapPlugin extends CordovaPlugin {
    protected ViewGroup root;
    private Activity activity;
    private android.graphics.Point mClickPoint;
    protected MapView mapView;
    private CallbackContext cCtx;
    private com.experieco.plugin.MyPluginLayout mPluginLayout;

    @Override
    public void initialize(CordovaInterface cordova, final CordovaWebView webView) {
        super.initialize(cordova, webView);
        if (root != null) {
            return;
        }

        activity = cordova.getActivity();
        final View view = webView.getView();
        root = (ViewGroup) view.getParent();

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                webView.getView().setBackgroundColor(Color.TRANSPARENT);
                webView.getView().setOverScrollMode(View.OVER_SCROLL_NEVER);
                mPluginLayout = new com.experieco.plugin.MyPluginLayout(webView, activity);
            }
        });
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        // Verify that the user sent a 'show' action
        if (!action.equals("show")) {
            callbackContext.error("\"" + action + "\" is not a recognized action.");
            return false;
        }
        cCtx = callbackContext;
        showMap(args.getJSONObject(0));

        return true;
    }

    public void showMap(final JSONObject options) {
        try {
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject mapRect = options.getJSONObject("mapRect");
                        RectF mapSize = new RectF();
                        mapSize.left = (float)(Double.parseDouble(mapRect.get("left") + "") * mPluginLayout.zoomScale);
                        mapSize.top = (float)(Double.parseDouble(mapRect.get("top") + "") * mPluginLayout.zoomScale);
                        mapSize.right = (float)(Double.parseDouble(mapRect.get("right") + "") * mPluginLayout.zoomScale);
                        mapSize.bottom =  (float)(Double.parseDouble(mapRect.get("bottom") + "") * mPluginLayout.zoomScale);

                        mPluginLayout.mapSize = mapSize;
                    } catch (JSONException e) {
                        e.printStackTrace();
                        cCtx.error("MapKitPlugin::showMap(): An exception occured");
                        return;
                    }

                    mapView = new MapView(activity);
                    ArcGISMap map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC, -27.514975, 153.010068, 13);

                    ServiceFeatureTable serviceFeatureTable1 = new ServiceFeatureTable("https://maps.treescape.co.nz/server/rest/services/Ergon/ErgonMapExplorer/FeatureServer/1");
                    FeatureLayer featureLayer1 = new FeatureLayer(serviceFeatureTable1);

                    ServiceFeatureTable serviceFeatureTable2 = new ServiceFeatureTable("https://maps.treescape.co.nz/server/rest/services/Ergon/ErgonMapExplorer/FeatureServer/2");
                    FeatureLayer featureLayer2 = new FeatureLayer(serviceFeatureTable2);

                    ServiceFeatureTable serviceFeatureTable3 = new ServiceFeatureTable("https://maps.treescape.co.nz/server/rest/services/Ergon/ErgonMapExplorer/FeatureServer/3");
                    FeatureLayer featureLayer3 = new FeatureLayer(serviceFeatureTable3);

                    map.getOperationalLayers().add(featureLayer1);
                    map.getOperationalLayers().add(featureLayer2);
                    map.getOperationalLayers().add(featureLayer3);

                    mapView.setMap(map);
                    mapView.setVisibility(View.VISIBLE);
                    mPluginLayout.addMap(mapView);


                    mapView.setOnTouchListener(new DefaultMapViewOnTouchListener(mapView.getContext(), mapView){
                        @Override
                        public boolean onSingleTapConfirmed(MotionEvent e) {
                            // get the point that was clicked and convert it to a point in map coordinates
                            mClickPoint = new android.graphics.Point((int) e.getX(), (int) e.getY());
                            Point mapPoint = mapView.screenToLocation(mClickPoint);
                            Point wgs84Point = (Point) GeometryEngine.project(mapPoint, SpatialReferences.getWgs84());
                            String msg = "Lat: " +  String.format("%.4f", wgs84Point.getY()) + ", Lon: " + String.format("%.4f", wgs84Point.getX());

                            Toast toast = Toast.makeText(cordova.getActivity().getApplicationContext(), msg, Toast.LENGTH_LONG);
                            toast.show();
                            return super.onSingleTapConfirmed(e);
                        }
                    });

                    cCtx.success();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            cCtx.error("MapKitPlugin::showMap(): An exception occured");
        }
    }

    @Override
    public void onPause(boolean multitasking){
        mapView.pause();
        super.onPause(multitasking);
    }

    @Override
    public void onResume(boolean multitasking){
        super.onResume(multitasking);
        mapView.resume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.dispose();
    }
}
