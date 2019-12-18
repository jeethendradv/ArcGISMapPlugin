package com.experieco.plugin;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.Job;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.ArcGISFeature;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.layers.Layer;
import com.esri.arcgisruntime.mapping.MobileMapPackage;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalItem;
import com.esri.arcgisruntime.security.UserCredential;
import com.esri.arcgisruntime.tasks.geodatabase.SyncGeodatabaseParameters;
import com.esri.arcgisruntime.tasks.offlinemap.DownloadPreplannedOfflineMapJob;
import com.esri.arcgisruntime.tasks.offlinemap.DownloadPreplannedOfflineMapResult;
import com.esri.arcgisruntime.tasks.offlinemap.OfflineMapSyncJob;
import com.esri.arcgisruntime.tasks.offlinemap.OfflineMapSyncLayerResult;
import com.esri.arcgisruntime.tasks.offlinemap.OfflineMapSyncParameters;
import com.esri.arcgisruntime.tasks.offlinemap.OfflineMapSyncResult;
import com.esri.arcgisruntime.tasks.offlinemap.OfflineMapSyncTask;
import com.esri.arcgisruntime.tasks.offlinemap.OfflineMapTask;
import com.esri.arcgisruntime.tasks.offlinemap.OfflineMapUpdatesInfo;
import com.esri.arcgisruntime.tasks.offlinemap.OfflineUpdateAvailability;
import com.esri.arcgisruntime.tasks.offlinemap.PreplannedMapArea;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginEntry;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class PluginArcGISMap extends MyPluginArcGISMap implements IPluginView {
    private Activity activity;
    public ArcGISMap map;
    private MapView mapView;
    private String mapId;
    private boolean isVisible = true;
    private boolean isClickable = true;
    private String mapDivId;
    private int viewDepth = 0;
    private Handler mainHandler;
    private Context context;
    private boolean isOffline;

    public Map<String, PluginEntry> plugins = new ConcurrentHashMap<String, PluginEntry>();
    public final ObjectCache objects = new ObjectCache();
    public static final Object semaphore = new Object();

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

    public PluginArcGISMap(Context context)
    {
        this.context = context;
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

                String downloadPath = context.getFilesDir().getAbsolutePath() + "/arcgisOffline";
                String itemId = "4d3a0a585aac411786d9771fb391febf";

                UserCredential credential = new UserCredential("jeethendradv", "Standard13*");
                Portal portal = new Portal("https://jeethendradv.maps.arcgis.com/");
                portal.setCredential(credential);
                PortalItem portalItem = new PortalItem(portal, itemId);

                //clearFolder(new File(downloadPath));
                if (isNetworkAvailable()) {
                    map = new ArcGISMap(portalItem);
                    displayMap(args, callbackContext);
                    isOffline = false;
                } else {
                    isOffline = true;
                    File downloadPathDirectory = new File(downloadPath);
                    if (downloadPathDirectory.exists()) {
                        // Create a MobileMapPackage from the offline map directory path
                        final MobileMapPackage offlineMapPackage = new MobileMapPackage(downloadPath);
                        offlineMapPackage.loadAsync();
                        offlineMapPackage.addDoneLoadingListener(new Runnable() {
                            @Override
                            public void run() {
                                // Get the title from the package metadata
                                Toast toast = Toast.makeText(cordova.getActivity().getApplicationContext(), "Showing offline map with title" + offlineMapPackage.getItem().getTitle(), Toast.LENGTH_LONG);
                                toast.show();

                                // Get the map from the package and set it to the MapView
                                map = offlineMapPackage.getMaps().get(0);
                                displayMap(args, callbackContext);
                            }
                        });
                    } else {
                        // Download the map for offline use.
                        OfflineMapTask offlineMapTask = new OfflineMapTask(portalItem);

                        //get all of the preplanned map areas in the web map
                        ListenableFuture<List<PreplannedMapArea>> mapAreasFuture = offlineMapTask.getPreplannedMapAreasAsync();
                        mapAreasFuture.addDoneListener(() -> {
                            try {
                                // get the list of areas
                                List<PreplannedMapArea> mapAreas = mapAreasFuture.get();

                                if (mapAreas.size() > 0) {
                                    PreplannedMapArea mapArea = mapAreas.get(0);
                                    // load each map area
                                    mapArea.loadAsync();
                                    mapArea.addDoneLoadingListener(() -> {
                                        // get the area title so it can be used in a UI component
                                        String areaTitle = mapArea.getPortalItem().getTitle();

                                        clearFolder(new File(downloadPath));

                                        // UI code for showing map areas goes here:
                                        DownloadPreplannedOfflineMapJob downloadPreplannedOfflineMapJob =
                                                offlineMapTask.downloadPreplannedOfflineMap(mapArea, downloadPath);

                                        // listen for job completion
                                        downloadPreplannedOfflineMapJob.addJobDoneListener(() -> {
                                            if (downloadPreplannedOfflineMapJob.getStatus() == Job.Status.SUCCEEDED) {
                                                // get the download result
                                                DownloadPreplannedOfflineMapResult downloadResult = downloadPreplannedOfflineMapJob.getResult();
                                                map = downloadResult.getOfflineMap();
                                                displayMap(args, callbackContext);

                                                Toast toast = Toast.makeText(cordova.getActivity().getApplicationContext(), "Map is now offline", Toast.LENGTH_LONG);
                                                toast.show();
                                            } else {
                                                String message = downloadPreplannedOfflineMapJob.getError().getMessage();
                                            }
                                        });

                                        //start the job
                                        downloadPreplannedOfflineMapJob.start();
                                    });
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
            }
        });
    }

    private void clearFolder(File fileOrFolder) {
        if (fileOrFolder.exists()) {
            if (fileOrFolder.isDirectory()) {
                File[] children = fileOrFolder.listFiles();
                for (int i = 0; i < children.length; i++) {
                    clearFolder(children[i]);
                }
            }
            fileOrFolder.delete();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void displayMap(final JSONArray args, final CallbackContext callbackContext) {
        mapView.setMap(map);
        mapView.setVisibility(View.VISIBLE);
        mapView.setOnTouchListener(new DefaultMapViewOnTouchListener(mapView.getContext(), mapView){
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // get the point that was clicked and convert it to a point in map coordinates
                android.graphics.Point mClickPoint = new android.graphics.Point((int) e.getX(), (int) e.getY());
                Point mapPoint = mapView.screenToLocation(mClickPoint);

                /*Point wgs84Point = (Point) GeometryEngine.project(mapPoint, SpatialReferences.getWgs84());
                String msg = "Lat: " +  String.format("%.4f", wgs84Point.getY()) + ", Lon: " + String.format("%.4f", wgs84Point.getX());

                Toast toast = Toast.makeText(cordova.getActivity().getApplicationContext(), msg, Toast.LENGTH_LONG);
                toast.show();*/

                selectFeaturesAt(mapPoint, 1);
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

    private void selectFeaturesAt(Point point, int tolerance) {
        // define the tolerance for identifying the feature
        final double mapTolerance = tolerance * mapView.getUnitsPerDensityIndependentPixel();
        // create objects required to do a selection with a query
        Envelope envelope = new Envelope(
                point.getX() - mapTolerance,
                point.getY() - mapTolerance,
                point.getX() + mapTolerance,
                point.getY() + mapTolerance,
                mapView.getSpatialReference());
        QueryParameters query = new QueryParameters();
        query.setGeometry(envelope);
        StringBuilder featureAttr = new StringBuilder();
        // select features within the envelope for all features on the map
        for (Layer layer : mapView.getMap().getOperationalLayers()) {
            final FeatureLayer featureLayer = (FeatureLayer) layer;
            final ListenableFuture<FeatureQueryResult> featureQueryResultFuture = featureLayer.selectFeaturesAsync(query, FeatureLayer.SelectionMode.NEW);

            // add done loading listener to fire when the selection returns
            featureQueryResultFuture.addDoneListener(() -> {
                try {
                    FeatureQueryResult layerFeatures = featureQueryResultFuture.get();
                    for (Feature feature : layerFeatures) {
                        ArcGISFeature agsFeature = (ArcGISFeature) feature;
                        agsFeature.loadAsync();
                        agsFeature.addDoneLoadingListener(() -> {
                            Map<String, Object> attr = agsFeature.getAttributes();
                            Set<String> keys = attr.keySet();
                            for (String key: keys) {
                                Object value = attr.get(key);
                                featureAttr.append(key + ": " + value.toString() + "\n");
                            }

                            try {
                                if (!isNetworkAvailable() && isOffline) {
                                    // Update attribute
                                    agsFeature.getAttributes().put("name", "Updated name");
                                    featureLayer.getFeatureTable().updateFeatureAsync(agsFeature).get();
                                } else {
                                    if (isOffline) {
                                        syncMap();
                                    }
                                }
                            } catch(ExecutionException | InterruptedException e) {

                            }

                            Toast toast = Toast.makeText(cordova.getActivity().getApplicationContext(), featureAttr.toString(), Toast.LENGTH_LONG);
                            toast.show();
                            featureLayer.clearSelection();
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Select feature failed: " + e.getMessage());
                }
            });
        }
    }

    private void syncMap() {
        OfflineMapSyncTask offlineMapSyncTask = new OfflineMapSyncTask(map);
        ListenableFuture<OfflineMapUpdatesInfo> updateInfo = offlineMapSyncTask.checkForUpdatesAsync();
        updateInfo.addDoneListener(new Runnable() {
            @Override
            public void run() {
                if (updateInfo.isDone()) {
                    try {
                        // Check if the map has any updates
                        if (updateInfo.get().getUploadAvailability() == OfflineUpdateAvailability.AVAILABLE) {
                            syncOfflineMap(offlineMapSyncTask);
                        }
                    } catch (ExecutionException | InterruptedException e) {

                    }
                }
            }
        });
    }

    private void syncOfflineMap(OfflineMapSyncTask offlineMapSyncTask) {
        //create the offline map sync parameters
        OfflineMapSyncParameters parameters = new OfflineMapSyncParameters();
        parameters.setRollbackOnFailure(true);
        parameters.setSyncDirection(SyncGeodatabaseParameters.SyncDirection.BIDIRECTIONAL);

        //instantiate the sync job using the synchronization parameters
        final OfflineMapSyncJob offlineMapSyncJob = offlineMapSyncTask.syncOfflineMap(parameters);

        // Add listener to deal with the completed job
        offlineMapSyncJob.addJobDoneListener(new Runnable() {
            @Override
            public void run() {
                // Check the job state is complete, deal with any errors
                if ((offlineMapSyncJob.getStatus() != Job.Status.SUCCEEDED) || (offlineMapSyncJob.getError() != null)) {
                    Toast toast = Toast.makeText(cordova.getActivity().getApplicationContext(), "Error occured while map sync", Toast.LENGTH_LONG);
                    toast.show();
                } else {
                    // Get the OfflineMapSyncResult returned from the sync
                    OfflineMapSyncResult result = offlineMapSyncJob.getResult();
                    if (result != null) {
                        String message = "";
                        if (result.hasErrors()) {
                            for (OfflineMapSyncLayerResult res : result.getLayerResults().values()) {
                                if (res.hasErrors()) {

                                }
                            }
                            message = "Sync has errors";
                        } else {
                            // Check sync results, for example update the UI, deal with specific layer errors, etc
                            message = "Map sync successful";
                        }
                        Toast toast = Toast.makeText(cordova.getActivity().getApplicationContext(), message, Toast.LENGTH_LONG);
                        toast.show();
                    }
                }
            }
        });

        //start the job
        offlineMapSyncJob.start();
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