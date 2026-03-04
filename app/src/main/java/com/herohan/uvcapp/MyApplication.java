package com.herohan.uvcapp;

import android.app.Application;
import android.content.Context;

public class MyApplication extends Application {
    private static Context sContext;

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = this;
//        Takt.stock(this)
//                .seat(Seat.BOTTOM_LEFT)
//                .interval(250)
//                .color(Color.WHITE)
//                .size(14f)
//                .alpha(0.5f)
//                .listener(fps -> {
////                    Log.d("uvcdemo", (int) fps + " fps");
//                });
    }


    public static Context getContext(){
        return sContext;
    }
}
