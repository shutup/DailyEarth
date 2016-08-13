package com.shutup.dailyearth;

import okhttp3.ResponseBody;
import retrofit2.http.GET;
import retrofit2.http.Path;
import rx.Observable;

/**
 * Created by shutup on 16/8/13.
 */
public interface Himawari8API {
    @GET("{param}.json")
    Observable<LatestHimawari8ImageInfo> getLatestTime(@Path("param") String param);

    @GET("{scale}d/550/{time}_{row}_{col}.png")
    Observable<ResponseBody> getImage(@Path("scale") int scale, @Path(value = "time",encoded = true) String time, @Path("row") int row, @Path("col") int col);
}
