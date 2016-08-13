package com.shutup.dailyearth;

/**
 * Created by shutup on 16/8/13.
 */
public class LatestHimawari8ImageInfo {
    public String date;
    public String file;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public LatestHimawari8ImageInfo(String date, String file) {
        this.date = date;
        this.file = file;
    }
}
