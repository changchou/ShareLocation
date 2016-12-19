package com.jikexueyuan.sharelocation;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.model.LatLng;

public class MainActivity extends AppCompatActivity implements BDLocationListener, ServiceConnection, View.OnClickListener {

    private MapView mMapView = null;
    private BaiduMap mBaiduMap = null;
    private BitmapDescriptor mCurrentMarker = null;
    private LocationClient mLocationClient = null;
    private LatLng latLng;
    private BitmapDescriptor bitmapDescriptor;
    private OverlayOptions overlayOptions;

    private FloatingActionButton fab;

    private boolean shareable = true;//判断开始或停止服务
    private boolean isBound = false;

    private ShareService.Binder binder;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //在使用SDK各组件之前初始化context信息，传入ApplicationContext
        //注意该方法要再setContentView方法之前实现
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);
        //获取地图控件引用
        mMapView = (MapView) findViewById(R.id.bmapView);
        mBaiduMap = mMapView.getMap();

        // 开启定位图层
        mBaiduMap.setMyLocationEnabled(true);

        // 设置定位图层的配置（定位模式，是否允许方向信息，用户自定义定位图标）
        MyLocationConfiguration.LocationMode mCurrentMode = MyLocationConfiguration.LocationMode.FOLLOWING;
        MyLocationConfiguration config = new MyLocationConfiguration(mCurrentMode, true, mCurrentMarker);
        mBaiduMap.setMyLocationConfigeration(config);

        //触摸地图时，取消中心固定在定位坐标
        mBaiduMap.setOnMapTouchListener(new BaiduMap.OnMapTouchListener() {
            @Override
            public void onTouch(MotionEvent motionEvent) {
                MyLocationConfiguration config = new MyLocationConfiguration(null, true, mCurrentMarker);
                mBaiduMap.setMyLocationConfigeration(config);
            }
        });

        mLocationClient = new LocationClient(getApplicationContext());     //声明LocationClient类
        mLocationClient.registerLocationListener(this);    //注册监听函数

        //设置定位参数
        LocationClientOption option = new LocationClientOption();
        option.setOpenGps(true);
        option.setCoorType("bd09ll");//返回的定位结果是百度经纬度,默认值gcj02
        option.setScanSpan(1000);
        mLocationClient.setLocOption(option);

        mLocationClient.start();

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(this);

    }

    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        mMapView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        mMapView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        // 退出时销毁定位
        mLocationClient.stop();
        // 关闭定位图层
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        mMapView = null;

        //当客户端退出程序时 通知其他客户端 用于其他客户端刷新mark
        if (binder != null) {
            binder.writeLoc("End");
        }
        //关闭程序是停止共享及服务
        stopService(new Intent(MainActivity.this, ShareService.class));
        //解绑服务  如果没解绑会报错崩溃  若为绑定就解绑也会崩溃
        if (isBound) {
            unbindService(this);
        }
    }


    @Override
    public void onReceiveLocation(BDLocation bdLocation) {

//        //测试客户端位置移动时其他客户端该客户标记是否移动
//        a += 0.1;
//        locInfo = (bdLocation.getLatitude() + a) + "@" + bdLocation.getLongitude();

        //获取自己的位置信息
        String locInfo = (bdLocation.getLatitude()) + "@" + bdLocation.getLongitude();

        //向服务器传递自己的位置信息
        if (binder != null) {
            binder.writeLoc(locInfo);
        }

        // 构造定位数据
        MyLocationData locData = new MyLocationData.Builder()
                .accuracy(bdLocation.getRadius())
                // 此处设置开发者获取到的方向信息，顺时针0-360
                .direction(100).latitude(bdLocation.getLatitude())
                .longitude(bdLocation.getLongitude()).build();
        // 设置定位数据
        mBaiduMap.setMyLocationData(locData);


        /**
         * 清除mark的方法从onServiceConnected改到这里，是因为如果客户端在共享时，服务器突然关闭，数据不再刷新
         * 最后的mark点不会消失，改到这里如果服务器关闭时，就可以清除mark
         */

    }

    @Override
    public void onClick(View v) {

        //开始共享  开启服务
        if (shareable) {

            Intent i = new Intent(MainActivity.this, ShareService.class);

            //启动service
            startService(i);
            //绑定service，利用onServiceConnected来获取socket传回的数据
            isBound = bindService(i, this, Context.BIND_AUTO_CREATE);

            shareable = false;//开启后 才可停止
            fab.setImageResource(R.drawable.stop);
            Snackbar.make(v, "您已经开始位置共享！", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();

        } else {

            //停止共享  停止服务
            //通知其他客户端 用于其他客户端刷新mark
            if (binder != null) {
                binder.writeLoc("End");
            }
            stopService(new Intent(MainActivity.this, ShareService.class));
            if (isBound) {
                unbindService(this);
                isBound = false;
            }
            shareable = true;
            fab.setImageResource(R.drawable.begin);
            Snackbar.make(v, "您停止了位置共享！", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            mBaiduMap.clear();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = (ShareService.Binder) service;
        binder.getService().setCallback(new ShareService.Callback() {
            @Override
            public void onDataChange(String data) {
                mBaiduMap.clear();
//                mBaiduMap.clear();
                String locInfoRec = data;
                System.out.println(locInfoRec + "---------------------------------");

                if (!locInfoRec.equals("End")) {
                    String[] locInfoData = locInfoRec.split("@");
                    if (locInfoData.length == 2) {
                        double mLat = Double.parseDouble(locInfoData[0]);
                        double mLon = Double.parseDouble(locInfoData[1]);
                        latLng = new LatLng(mLat, mLon);
                    }

                    bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.mark);
                    overlayOptions = new MarkerOptions().position(latLng).icon(bitmapDescriptor);
                    mBaiduMap.addOverlay(overlayOptions);

                }
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }


}
