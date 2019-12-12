package com.experieco.plugin;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.util.Log;

//import com.google.android.gms.maps.GoogleMap;

import com.esri.arcgisruntime.mapping.ArcGISMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginEntry;
import org.json.JSONArray;
import org.json.JSONException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyPluginArcGISMap extends CordovaPlugin implements MyPluginInterface {
    public MyPluginArcGISMap self = null;
    public final Map<String, Method> methods = new ConcurrentHashMap<String, Method>();
    protected static ExecutorService executorService = null;

    public CordovaArcGISMap mapCtrl = null;
    public ArcGISMap map = null;
    public PluginArcGISMap pluginMap = null;
    protected boolean isRemoved = false;
    protected static float density = Resources.getSystem().getDisplayMetrics().density;

    public void setPluginMap(PluginArcGISMap pluginMap) {
        this.pluginMap = pluginMap;
        this.mapCtrl = pluginMap.mapCtrl;
        this.map = pluginMap.map;
    }
    protected String TAG = "";

    @SuppressLint("UseSparseArrays")
    @Override
    public void initialize(CordovaInterface cordova, final CordovaWebView webView) {
        super.initialize(cordova, webView);
        TAG = this.getServiceName();
        if (executorService == null) {
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    executorService = Executors.newCachedThreadPool();
                }
            });
        }
    }

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext)  {
        self = this;
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                if (isRemoved) {
                    // Ignore every execute calls.
                    return;
                }

                synchronized (methods) {
                    if (methods.size() == 0) {
                        TAG = MyPluginArcGISMap.this.getServiceName();
                        if (!TAG.contains("-")) {
                            if (TAG.startsWith("map")) {
                                mapCtrl.mPluginLayout.pluginOverlays.put(TAG, (PluginArcGISMap) MyPluginArcGISMap.this);
                            }
                        } else {
                            PluginEntry pluginEntry = new PluginEntry(TAG, MyPluginArcGISMap.this);
                            pluginMap.plugins.put(TAG, pluginEntry);
                        }
                        Method[] classMethods = self.getClass().getMethods();
                        for (Method classMethod : classMethods) {
                            methods.put(classMethod.getName(), classMethod);
                        }
                    }
                    //  return true;
                    if (methods.containsKey(action)) {
                        if (self.mapCtrl.mPluginLayout.isDebug) {
                            try {
                                if (args != null && args.length() > 0) {
                                    Log.d(TAG, "(debug)action=" + action + " args[0]=" + args.getString(0));
                                } else {
                                    Log.d(TAG, "(debug)action=" + action);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        Method method = methods.get(action);
                        try {
                            if (isRemoved) {
                                // Ignore every execute calls.
                                return;
                            }
                            method.invoke(self, args, callbackContext);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                            callbackContext.error("Cannot access to the '" + action + "' method.");
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                            callbackContext.error("Cannot access to the '" + action + "' method.");
                        }
                    }
                }
            }
        });
        return true;
    }

    protected void create(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        // dummy
    }

    private void setValue(String methodName, Class<?> methodClass, String id, final Object value, final CallbackContext callbackContext) throws JSONException {
        if (!pluginMap.objects.containsKey(id)) {
            return;
        }
        final Object object = pluginMap.objects.get(id);
        try {
            final Method method = object.getClass().getDeclaredMethod(methodName, methodClass);
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        method.invoke(object, value);
                        callbackContext.success();
                    } catch (Exception e) {
                        e.printStackTrace();
                        callbackContext.error(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            callbackContext.error(e.getMessage());
        }
    }
    protected void clear() {
        String[] keys = pluginMap.objects.keys.toArray(new String[pluginMap.objects.size()]);
        Object object;
        for (String key : keys) {
            object = pluginMap.objects.remove(key);
            object = null;
        }
        pluginMap.objects.clear();
    }
}