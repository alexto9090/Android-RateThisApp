package com.alexto.rateapp2021;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

import java.util.Date;

/**
 * Unit test for RateThisApp class
 * Created by keisuke on 16/03/09.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, shadows = {ShadowSystemClock.class})
public class RateThisAppTest {

    private static final String PREF_NAME = "RateThisApp";
    private static final String KEY_INSTALL_DATE = "rta_install_date";
    private static final String KEY_LAUNCH_TIMES = "rta_launch_times";
    private static final String KEY_OPT_OUT = "rta_opt_out";
    private static final String KEY_ASK_LATER_DATE = "rta_ask_later_date";


    @Before
    public void setUp() throws PackageManager.NameNotFoundException {

        Context context = ApplicationProvider.getApplicationContext();
        PackageManager packageManager = context.getPackageManager();

        // Shadow the PackageManager to simulate packages if needed
        PackageInfo pkgInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
        pkgInfo.firstInstallTime = System.currentTimeMillis();
    }

    @Test
    public void onStart_isSuccess() {
        Context context = ApplicationProvider.getApplicationContext();

        RateThisApp.onCreate(context);

        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREF_NAME, Context.MODE_PRIVATE);

        // check if install date is stored
        long expectedInstallDate = 0L;
        PackageManager packMan = context.getPackageManager();
        try {
            PackageInfo pkgInfo = packMan.getPackageInfo(context.getPackageName(), 0);
            expectedInstallDate = new Date(pkgInfo.firstInstallTime).getTime();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        Assert.assertEquals(expectedInstallDate, sharedPreferences.getLong(KEY_INSTALL_DATE, 0L));

        // check if launch time is incremented
        Assert.assertEquals(1, sharedPreferences.getInt(KEY_LAUNCH_TIMES, 0));
    }

    @Test
    public void shouldRateDialogIfNeeded_LaunchTimeIsCorrect() {
        Context context = ApplicationProvider.getApplicationContext();

        RateThisApp.init(new RateThisApp.Config(1, 3));

        RateThisApp.onCreate(context);
       /* Assert.assertFalse(RateThisApp.shouldShowRateDialog());
        RateThisApp.onCreate(context);
        Assert.assertFalse(RateThisApp.shouldShowRateDialog());
        RateThisApp.onCreate(context);
        Assert.assertTrue(RateThisApp.shouldShowRateDialog());
        RateThisApp.onCreate(context);
        Assert.assertTrue(RateThisApp.shouldShowRateDialog());*/
    }

    @Test
    public void getLaunchCount_IsCorrect() {
        Context context = ApplicationProvider.getApplicationContext();

        Assert.assertEquals(0, RateThisApp.getLaunchCount(context));
        RateThisApp.onCreate(context);
        Assert.assertEquals(1, RateThisApp.getLaunchCount(context));
        RateThisApp.onCreate(context);
        Assert.assertEquals(2, RateThisApp.getLaunchCount(context));
    }

    @Test
    public void stopRateDialog_IsSuccess() {
        Context context = ApplicationProvider.getApplicationContext();
        RateThisApp.stopRateDialog(context);

        // check shared pref
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREF_NAME, Context.MODE_PRIVATE);
        Assert.assertTrue(sharedPreferences.getBoolean(KEY_OPT_OUT, false));
    }
}
