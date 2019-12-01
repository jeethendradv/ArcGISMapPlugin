package com.experieco.plugin;

// The native Toast API
import android.widget.Toast;

// Cordova-required packages
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.widget.Toast;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Context;

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

    private static final String DURATION_LONG = "long";
    protected ViewGroup root; // original Cordova layout
    protected RelativeLayout main; // new layout to support map
    private android.graphics.Point mClickPoint;
    protected MapView mapView;
    private CallbackContext cCtx;
    private String TAG = "ArcGISMapPlugin";

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        main = new RelativeLayout(cordova.getActivity());
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
                    double latitude = 0, longitude = 0;
                    int height = 460;
                    boolean atBottom = false;
                    WindowManager mW = (WindowManager)cordova.getActivity().getSystemService(Context.WINDOW_SERVICE);
                    int screenWidth = mW.getDefaultDisplay().getWidth();
                    int screenHeight = mW.getDefaultDisplay().getHeight();

                    //try {
                        height = (int)(screenHeight * .90); //options.getInt("height");
                        //latitude = options.getDouble("lat");
                        //longitude = options.getDouble("lon");
                        atBottom = true;//options.getBoolean("atBottom");
                    //} catch (JSONException e) {
                        //LOG.e(TAG, "Error reading options");
                    //}

                    mapView = new MapView(cordova.getActivity());
                    View view = webView.getView();
                    root = (ViewGroup)view.getParent();
                    root.removeView(view);
                    main.addView(view);

                    cordova.getActivity().setContentView(main);

                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, height);
                    if (atBottom) {
                        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                    } else {
                        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
                    }
                    params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);

                    mapView.setLayoutParams(params);
                    ArcGISMap map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC, -27.514975, 153.010068, 13);

                    ServiceFeatureTable serviceFeatureTable = new ServiceFeatureTable("https://maps.treescape.co.nz/server/rest/services/Ergon/ErgonMapExplorer/FeatureServer/1");
                    FeatureLayer featureLayer = new FeatureLayer(serviceFeatureTable);
                    map.getOperationalLayers().add(featureLayer);

                    mapView.setMap(map);
                    main.addView(mapView);

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
