package com.experieco.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import com.esri.arcgisruntime.mapping.view.MapView;

import org.apache.cordova.CordovaWebView;

public class MyPluginLayout extends FrameLayout {
    private View browserView;
    private Context context;
    private FrontLayerLayout frontLayer;
    private ScrollView scrollView;
    public FrameLayout scrollFrameLayout;
    private Activity mActivity;
    public RectF mapSize = null;
    private CordovaWebView webView;
    public float zoomScale;

    /*@Override
    public void onGlobalLayout() {
        ViewTreeObserver observer = browserView.getViewTreeObserver();
        //observer.removeGlobalOnLayoutListener(this);
        observer.addOnScrollChangedListener(this);
    }*/

    public MyPluginLayout(CordovaWebView webView, Activity activity) {
        super(webView.getView().getContext());
        this.webView = webView;
        this.browserView = webView.getView();
        //browserView.getViewTreeObserver().addOnGlobalLayoutListener(this);
        mActivity = activity;
        ViewGroup root = (ViewGroup) browserView.getParent();
        this.context = browserView.getContext();

        zoomScale = Resources.getSystem().getDisplayMetrics().density;
        frontLayer = new FrontLayerLayout(this.context);

        scrollView = new ScrollView(this.context);
        scrollView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        root.removeView(browserView);
        frontLayer.addView(browserView);

        scrollFrameLayout = new FrameLayout(this.context);
        scrollFrameLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));


        View dummyView = new View(this.context);
        dummyView.setLayoutParams(new LayoutParams(1, 99999));
        scrollFrameLayout.addView(dummyView);

        scrollView.setHorizontalScrollBarEnabled(true);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.addView(scrollFrameLayout);

        browserView.setDrawingCacheEnabled(false);


        this.addView(scrollView);
        this.addView(frontLayer);
        root.addView(this);
        browserView.setBackgroundColor(Color.TRANSPARENT);

        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setVerticalScrollBarEnabled(false);
    }

    /*@Override
    public void onScrollChanged() {
        scrollView.scrollTo(browserView.getScrollX(), browserView.getScrollY());
    }*/

    public void addMap(MapView mapView) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mapSize != null) {
                    int width = (int) mapSize.width();
                    int height = (int) mapSize.height();
                    int x = (int) mapSize.left;
                    int y = (int) mapSize.top;

                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height);
                    params.height = height;
                    params.width = width;
                    params.leftMargin = x;
                    params.topMargin = y;

                    mapView.setLayoutParams(params);
                    scrollFrameLayout.addView(mapView, 0);
                }
            }
        });
    }

    private class FrontLayerLayout extends FrameLayout {
        public FrontLayerLayout(Context context) {
            super(context);
            this.setWillNotDraw(false);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            PointF clickPoint = new PointF(event.getX(), event.getY());
            return mapSize.contains(clickPoint.x, clickPoint.y);
        }
    }
}
