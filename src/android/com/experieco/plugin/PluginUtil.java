package com.experieco.plugin;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class PluginUtil {
  // Get resource id
  // http://stackoverflow.com/a/37840674
  public static int getAppResource(Activity activity, String name, String type) {
    return activity.getResources().getIdentifier(name, type, activity.getPackageName());
  }

  public static abstract class MyCallbackContext extends CallbackContext {

    public MyCallbackContext(String callbackId, CordovaWebView webView) {
      super(callbackId, webView);
    }
    @Override
    public void sendPluginResult(PluginResult pluginResult) {
      this.onResult(pluginResult);
    }

    abstract public void onResult(PluginResult pluginResult);
  }

  public static boolean isNumeric(String str)
  {
    for (char c : str.toCharArray()) {
      if (!Character.isDigit(c)) {
        return false;
      }
    }
    return true;
  }

  public static String getAbsolutePathFromCDVFilePath(CordovaResourceApi resourceApi, String cdvFilePath) {
    if (cdvFilePath.indexOf("cdvfile://") != 0) {
      return null;
    }

    //CordovaResourceApi resourceApi = webView.getResourceApi();
    Uri fileURL = resourceApi.remapUri(Uri.parse(cdvFilePath));
    File file = resourceApi.mapUriToFile(fileURL);
    if (file == null) {
      return null;
    }

    try {
      return file.getCanonicalPath();
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
  public static JSONObject location2Json(Location location) throws JSONException {
    JSONObject latLng = new JSONObject();
    latLng.put("lat", location.getLatitude());
    latLng.put("lng", location.getLongitude());

    JSONObject params = new JSONObject();
    params.put("latLng", latLng);

    if (VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      params.put("elapsedRealtimeNanos", location.getElapsedRealtimeNanos());
    } else {
      params.put("elapsedRealtimeNanos", 0);
    }
    params.put("time", location.getTime());
    /*
    SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    Date date = new Date(location.getTime());
    params.put("timeFormatted", format.format(date));
    */
    if (location.hasAccuracy()) {
      params.put("accuracy", location.getAccuracy());
    }
    if (location.hasBearing()) {
      params.put("bearing", location.getBearing());
    }
    if (location.hasAltitude()) {
      params.put("altitude", location.getAltitude());
    }
    if (location.hasSpeed()) {
      params.put("speed", location.getSpeed());
    }
    params.put("provider", location.getProvider());
    params.put("hashCode", location.hashCode());
    return params;
  }

  /**
   * return color integer value
   * @param arrayRGBA
   * @throws JSONException
   */
  public static int parsePluginColor(JSONArray arrayRGBA) throws JSONException {
    return Color.argb(arrayRGBA.getInt(3), arrayRGBA.getInt(0), arrayRGBA.getInt(1), arrayRGBA.getInt(2));
  }

  public static Bundle Json2Bundle(JSONObject json) {
    Bundle mBundle = new Bundle();
    @SuppressWarnings("unchecked")
    Iterator<String> iter = json.keys();
    Object value;
    while (iter.hasNext()) {
      String key = iter.next();
      try {
        value = json.get(key);
        if (Boolean.class.isInstance(value)) {
          mBundle.putBoolean(key, (Boolean)value);
        } else if (Integer.class.isInstance(value)) {
          mBundle.putInt(key, (Integer)value);
        } else if (Long.class.isInstance(value)) {
          mBundle.putLong(key, (Long)value);
        } else if (Double.class.isInstance(value)) {
          mBundle.putDouble(key, (Double)value);
        } else if (JSONObject.class.isInstance(value)) {
          mBundle.putBundle(key, Json2Bundle((JSONObject)value));
        } else if (JSONArray.class.isInstance(value)) {
          JSONArray values = (JSONArray)value;
          ArrayList<String> strings = new ArrayList<String>();
          for (int i = 0; i < values.length(); i++) {
            strings.add(values.get(i) + "");
          }
          mBundle.putStringArrayList(key, strings);
        } else {
          mBundle.putString(key, json.getString(key));
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
    return mBundle;
  }

  public static JSONObject Bundle2Json(Bundle bundle) {
    JSONObject json = new JSONObject();
    Set<String> keys = bundle.keySet();
    Iterator<String> iter = keys.iterator();
    while (iter.hasNext()) {
      String key = iter.next();
      try {
        Object value = bundle.get(key);
        if (Bundle.class.isInstance(value)) {
          value = Bundle2Json((Bundle)value);
        }
        if (value.getClass().isArray()) {
          JSONArray values = new JSONArray();
          Object[] objects = (Object[])value;
          int i = 0;
          for (i = 0; i < objects.length; i++) {
            if (Bundle.class.isInstance(objects[i])) {
              objects[i] = Bundle2Json((Bundle)objects[i]);
            }
            values.put(objects[i]);
          }
          json.put(key, values);
        } else if (value.getClass() == ArrayList.class) {
          JSONArray values = new JSONArray();
          Iterator<?> listIterator = ((ArrayList<?>)value).iterator();
          while(listIterator.hasNext()) {
            value = listIterator.next();
            if (Bundle.class.isInstance(value)) {
              value = Bundle2Json((Bundle)value);
            }
            values.put(value);
          }
          json.put(key, values);
        } else {
          json.put(key, value);
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
    return json;
  }
}
