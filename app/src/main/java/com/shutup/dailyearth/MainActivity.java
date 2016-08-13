package com.shutup.dailyearth;

import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.Observable;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);

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

        final Himawari8API himawari8API = retrofit.create(Himawari8API.class);

        himawari8API.getLatestTime(LatestStr)
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
                            mWallpaperManager.clear();
                            mWallpaperManager.suggestDesiredDimensions(screenW, screenH);
                            mWallpaperManager.setBitmap(bitmaps.get(1));
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
