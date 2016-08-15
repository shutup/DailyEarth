package com.shutup.dailyearth;

import android.content.Intent;

/**
 * Created by shutup on 16/8/15.
 */
public class MenuItem {
    private String menuTitle;
    private Intent mIntent;

    public String getMenuTitle() {
        return menuTitle;
    }

    public void setMenuTitle(String menuTitle) {
        this.menuTitle = menuTitle;
    }

    public Intent getIntent() {
        return mIntent;
    }

    public void setIntent(Intent intent) {
        mIntent = intent;
    }

    public MenuItem(String menuTitle, Intent intent) {
        this.menuTitle = menuTitle;
        mIntent = intent;
    }
}
