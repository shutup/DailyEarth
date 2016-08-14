package com.shutup.dailyearth;

import android.app.IntentService;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

/**
 * Created by shutup on 16/8/14.
 */
public class MySetWallPaperIntentService extends IntentService implements Constants {

    private WallpaperManager mWallpaperManager = null;
    private Himawari8API himawari8API = null;
    private Subscription mSubscription = null;
    private String TAG = this.getClass().getSimpleName();
    private int scale = 1;
    private int screenW = 0;
    private int screenH = 0;

    public MySetWallPaperIntentService() {
        super("MySetWallPaperIntentService");
        if (BuildConfig.DEBUG) Log.d("MySetWallPaperIntentSer", "MySetWallPaperIntentService");
        mWallpaperManager = WallpaperManager.getInstance(this.getApplicationContext());

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Himawari8URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build();

        himawari8API = retrofit.create(Himawari8API.class);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        screenH = displayMetrics.heightPixels;
        screenW = displayMetrics.widthPixels;
        if (BuildConfig.DEBUG) Log.d("MySetWallPaperIntentSer", "screenH:" + screenH);
        if (BuildConfig.DEBUG) Log.d("MySetWallPaperIntentSer", "screenW:" + screenW);
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        if (BuildConfig.DEBUG) Log.d("MySetWallPaperIntentSer", "onHandleIntent");
        if (intent.getAction().equalsIgnoreCase(UpdateWallPaperAction)) {
            setLatestImage(himawari8API);
            if (BuildConfig.DEBUG) Log.d("MySetWallPaperIntentSer", "onHandleIntent");
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
                            mWallpaperManager.clear();
                            mWallpaperManager.suggestDesiredDimensions(screenW, screenH);
                            mWallpaperManager.setBitmap(bitmaps.get(1));
                            if (BuildConfig.DEBUG)
                                Log.d("MySetWallPaperIntentSer", "setWallPaperSuccess");
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
