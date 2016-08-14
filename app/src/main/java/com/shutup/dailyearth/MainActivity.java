package com.shutup.dailyearth;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
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

public class MainActivity extends AppCompatActivity implements Constants {

    @InjectView(R.id.previewImage)
    ImageView mPreviewImage;
    @InjectView(R.id.swipeRefresh)
    SwipeRefreshLayout mSwipeRefresh;

    private String TAG = this.getClass().getSimpleName();
    private int scale = 1;
    private WallpaperManager mWallpaperManager = null;
    private int padding = 50;
    private int screenW = 0;
    private int screenH = 0;

    Himawari8API himawari8API = null;
    private Subscription mSubscription = null;

    static boolean isRequest = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);


        /*
            start daemon service
         */
        startService(new Intent(this, MyDaemonService.class));

        /*
            AlarmManager.ELAPSED_REALTIME_WAKEUP
         */
//        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE) ;
//        Intent serviceIntent = new Intent(this, MySetWallPaperIntentService.class) ; //启动service
//        PendingIntent pendingIntent = PendingIntent.getService(this,0,serviceIntent,0) ;
//        Intent broadIntent = new Intent(this, AlarmListener.class) ; //启动service
//        PendingIntent pendingIntent = PendingIntent.getService(this,0,broadIntent,PendingIntent.FLAG_UPDATE_CURRENT) ;
//        long triggerTime =  System.currentTimeMillis() ; //每隔50秒触发一次
//        long interval = 5* 1000;
//        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,triggerTime,interval,pendingIntent);

        /*
            AlarmManager.RTC_WAKEUP
         */
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        //get a Calendar object with current time
        Calendar cal = Calendar.getInstance();
        // add 30 seconds to the calendar object
        cal.add(Calendar.SECOND, 10);
        long interval = 10 * 60 * 1000;
        Intent intent = new Intent(this, MyDaemonService.class);
        intent.setAction(UpdateWallPaperAction);
        PendingIntent sender = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), interval, sender);


        mSwipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!isRequest) {
                    setLatestImage(himawari8API);
                }
            }
        });

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenW = displayMetrics.widthPixels;
        screenH = displayMetrics.heightPixels;

        if (BuildConfig.DEBUG) Log.d(TAG, "screenW:" + screenW);
        if (BuildConfig.DEBUG) Log.d(TAG, "screenH:" + screenH);

        mWallpaperManager = WallpaperManager.getInstance(this.getApplicationContext());

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Himawari8URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();

        himawari8API = retrofit.create(Himawari8API.class);

        setLatestImage(himawari8API);
    }

    private void setLatestImage(final Himawari8API himawari8API) {
        isRequest = true;
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
                        }else {
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
                        Bitmap bitmapResized = Bitmap.createScaledBitmap(bitmap, screenW - padding, screenW - padding, true);
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
                            mPreviewImage.setImageBitmap(bitmaps.get(0));
//                            mWallpaperManager.clear();
                            mWallpaperManager.suggestDesiredDimensions(screenW, screenH);
                            mWallpaperManager.setBitmap(bitmaps.get(1));
                            reset();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        reset();
                        if (BuildConfig.DEBUG) Log.d(TAG, "throwable:" + throwable);
                    }
                });
    }

    private void reset() {
        mSwipeRefresh.setRefreshing(false);
        isRequest = false;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (BuildConfig.DEBUG) Log.d(TAG, "onDestroy");
        if (mSubscription != null && !mSubscription.isUnsubscribed()) {
            mSubscription.unsubscribe();
        }
    }

    private String getPreference(String key,String value) {
        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return mSharedPreferences.getString(key, value);
    }

    private void savePreference(String key, String value) {
        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(key, value);
        editor.commit();
    }
}
