package com.shutup.dailyearth;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
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
        if (BuildConfig.DEBUG) Log.d(TAG, "onCreate");
        mWallpaperManager = WallpaperManager.getInstance(this.getApplicationContext());
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        screenH = displayMetrics.heightPixels;
        screenW = displayMetrics.widthPixels;
        if (BuildConfig.DEBUG) Log.d(TAG, "screenH:" + screenH);
        if (BuildConfig.DEBUG) Log.d(TAG, "screenW:" + screenW);

        HandlerThread handlerThread = new HandlerThread("MyDaemonServiceThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper(), new MyHandlerCallback());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onStartCommand");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this.getApplicationContext());
        Intent nfIntent = new Intent(this, MainActivity.class);
        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0))
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.earth))
                .setSmallIcon(R.drawable.earth)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("")
                .setWhen(System.currentTimeMillis());
        Notification notification = builder.build();
        startForeground(NotificationId, notification);
        if (handler != null) {
            Message message = handler.obtainMessage();
            message.obj = UpdateWallPaperAction;
            handler.sendMessage(message);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onBind");
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
                        String lastTime = getPreference(LastUpdateTime, "");
                        if (time.equalsIgnoreCase(lastTime)) {
                            if (BuildConfig.DEBUG) Log.d(TAG, "no need to update");
                        } else {
                            if (BuildConfig.DEBUG) Log.d(TAG, "need to update");
                            savePreference(LastUpdateTime, time);
                            for (int row = 0; row < scale; row++) {
                                for (int col = 0; col < scale; col++) {
                                    Observable<ResponseBody> observable = himawari8API.getImage(scale, time, row, col).asObservable();
                                    return observable;
                                }
                            }
                        }
                        return Observable.empty();
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
                            mWallpaperManager.suggestDesiredDimensions(screenW, screenH);
                            mWallpaperManager.setBitmap(bitmaps.get(1));
                            saveLatestImage2Local(bitmaps.get(0));
                            if (BuildConfig.DEBUG)
                                Log.d(TAG, "setWallPaperSuccess");
//                            SetLockWallPaper(mWallpaperManager, bitmaps.get(1));
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

    /*
        目前没有通用的方式来修改锁屏图片
     */
    private void SetLockWallPaper(WallpaperManager wallpaperManager, Bitmap bitmap) {
        try {
            Class class1 = wallpaperManager.getClass();//获取类名
            Method setWallPaperMethod = class1.getMethod("setBitmapToLockWallpaper", Bitmap.class);
            //获取设置锁屏壁纸的函数
            setWallPaperMethod.invoke(wallpaperManager, bitmap);
            //调用锁屏壁纸的函数，并指定壁纸的路径imageFilesPath
            if (BuildConfig.DEBUG) Log.d(TAG, "setLockScreenPaperSuccess");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private String getPreference(String key, String value) {
        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return mSharedPreferences.getString(key, value);
    }

    private void savePreference(String key, String value) {
        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(key, value);
        editor.commit();
    }

    private void saveLatestImage2Local(Bitmap bitmap) {
        if (BuildConfig.DEBUG) Log.d(TAG, "saveLatestImage2Local");
        File file = new File(getDir("image", 0), LastLatestImageName);
        FileOutputStream fileOutputStream = null;
        if (file.exists()) {
            file.delete();
        }
        try {
            fileOutputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
