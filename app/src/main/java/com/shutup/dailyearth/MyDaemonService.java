package com.shutup.dailyearth;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MyDaemonService extends Service implements Constants {

    private String TAG = this.getClass().getSimpleName();
    
    private Handler handler = null;
    private WallpaperManager mWallpaperManager = null;
    private Himawari8API himawari8API = null;
    private Subscription mSubscription = null;
    private int scale = 1;
    private int screenW = 0;
    private int screenH = 0;
    
    public MyDaemonService() {
        if (BuildConfig.DEBUG) Log.d(TAG, "MySetWallPaperIntentService");


        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Himawari8URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();

        himawari8API = retrofit.create(Himawari8API.class);


    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) Log.d("MyDaemonService", "onCreate");
        mWallpaperManager = WallpaperManager.getInstance(this.getApplicationContext());
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        screenH = displayMetrics.heightPixels;
        screenW = displayMetrics.widthPixels;
        if (BuildConfig.DEBUG) Log.d(TAG, "screenH:" + screenH);
        if (BuildConfig.DEBUG) Log.d(TAG, "screenW:" + screenW);

        HandlerThread handlerThread = new HandlerThread("MyDaemonServiceThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper(),new MyHandlerCallback());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (BuildConfig.DEBUG) Log.d("MyDaemonService", "onStartCommand");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this.getApplicationContext());
        Intent nfIntent = new Intent(this, MainActivity.class);
        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0))
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.earth))
                .setContentTitle(getString(R.string.app_name))
                .setSmallIcon(R.drawable.earth)
                .setContentText("")
                .setWhen(System.currentTimeMillis());
        Notification notification = builder.build();
        startForeground(NotificationId, notification);
        if (handler!=null) {
            Message message = handler.obtainMessage();
            message.obj = UpdateWallPaperAction;
            handler.sendMessage(message);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (BuildConfig.DEBUG) Log.d("MyDaemonService", "onBind");
        return null;
    }

    class MyHandlerCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            if (BuildConfig.DEBUG) Log.d("MyHandlerCallback", "handleMessage");
            String str = (String) msg.obj;
            if (str.equalsIgnoreCase(UpdateWallPaperAction)) {
                if (BuildConfig.DEBUG) Log.d("MyHandlerCallback", "enter");
                setLatestImage(himawari8API);
            }
            return true;
        }
    }

    private void setLatestImage(final Himawari8API himawari8API) {
        mSubscription = himawari8API.getLatestTime(LatestStr)
                .subscribeOn(Schedulers.io())
                .asObservable()
                .map(new Func1<LatestHimawari8ImageInfo, String>() {
                    @Override
                    public String call(LatestHimawari8ImageInfo latestHimawari8ImageInfo) {
                        if (BuildConfig.DEBUG)
                            Log.d(TAG, "latestHimawari8ImageInfo:" + latestHimawari8ImageInfo);
                        return latestHimawari8ImageInfo.date;
                    }
                })
                .concatMap(new Func1<String, Observable<ResponseBody>>() {
                    @Override
                    public Observable<ResponseBody> call(String s) {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getString(R.string.originTimePattern));
                        Date date = new Date();
                        try {
                            date = simpleDateFormat.parse(s);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        simpleDateFormat = new SimpleDateFormat(getString(R.string.dstTimePatttern));
                        String time = simpleDateFormat.format(date);
                        for (int row = 0; row < scale; row++) {
                            for (int col = 0; col < scale; col++) {
                                Observable<ResponseBody> observable = himawari8API.getImage(scale, time, row, col).asObservable();
                                return observable;
                            }
                        }
                        return null;
                    }
                })
                .map(new Func1<ResponseBody, List<Bitmap>>() {
                    @Override
                    public List<Bitmap> call(ResponseBody responseBody) {
                        Bitmap bitmap = BitmapFactory.decodeStream(responseBody.byteStream());
                        Bitmap bitmapResized = Bitmap.createScaledBitmap(bitmap, screenW - WallPaperPadding, screenW - WallPaperPadding, true);
                        int width = bitmapResized.getWidth();
                        int height = bitmapResized.getHeight();
                        int left = (screenW - width) / 2;
                        int top = (screenH - height) / 2;
                        if (BuildConfig.DEBUG) Log.d(TAG, "left:" + left);
                        if (BuildConfig.DEBUG) Log.d(TAG, "top:" + top);
                        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        Bitmap bitmapNew = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.RGB_565);
                        Canvas canvas = new Canvas(bitmapNew);
                        canvas.drawColor(Color.BLACK);
                        canvas.drawBitmap(bitmapResized, left, top, paint);
                        List<Bitmap> bitmaps = new ArrayList<>();
                        bitmaps.add(bitmapResized);
                        bitmaps.add(bitmapNew);
                        return bitmaps;
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<Bitmap>>() {
                    @Override
                    public void call(List<Bitmap> bitmaps) {
                        try {
//                            mWallpaperManager.clear();
                            mWallpaperManager.suggestDesiredDimensions(screenW, screenH);
                            mWallpaperManager.setBitmap(bitmaps.get(1));
                            if (BuildConfig.DEBUG)
                                Log.d(TAG, "setWallPaperSuccess");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "throwable:" + throwable);
                    }
                });
    }

}
