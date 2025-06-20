/*
 * Copyright 2013-2015 Keisuke Kobayashi
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alexto.rateapp2021;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.StringRes;

import com.google.android.gms.tasks.Task;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * RateThisApp<br>
 * A library to show the app rate dialog
 * @author Keisuke Kobayashi (k.kobayashi.122@gmail.com)
 *
 */
public class RateThisApp {

    private static final String TAG = RateThisApp.class.getSimpleName();

    private static final String PREF_NAME = "RateThisApp";
    private static final String KEY_INSTALL_DATE = "rta_install_date";
    private static final String KEY_LAUNCH_TIMES = "rta_launch_times";
    private static final String KEY_OPT_OUT = "rta_opt_out";
    private static final String KEY_ASK_LATER_DATE = "rta_ask_later_date";

    private static Date mInstallDate = new Date();
    private static int mLaunchTimes = 0;
    private static boolean mOptOut = false;
    private static Date mAskLaterDate = new Date();

    private static Config sConfig = new Config();
    private static Callback sCallback = null;
    // Weak ref to avoid leaking the context
    private static WeakReference<AlertDialog> sDialogRef = null;
    private static ReviewManager reviewManager;
    /**
     * If true, print LogCat
     */
    public static final boolean DEBUG = false;

    /**
     * Initialize RateThisApp configuration.
     * @param config Configuration object.
     */
    public static void init(Config config) {
        sConfig = config;
    }

    /**
     * Set callback instance.
     * The callback will receive yes/no/later events.
     */
    public static void setCallback(Callback callback) {
        sCallback = callback;
    }

    /**
     * Call this API when the launcher activity is launched.<br>
     * It is better to call this API in onCreate() of the launcher activity.
     * @param context Context
     */
    public static void onCreate(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Editor editor = pref.edit();
        // If it is the first launch, save the date in shared preference.
        if (pref.getLong(KEY_INSTALL_DATE, 0) == 0L) {
            storeInstallDate(context, editor);
        }
        // Increment launch times
        int launchTimes = pref.getInt(KEY_LAUNCH_TIMES, 0);
        launchTimes++;
        editor.putInt(KEY_LAUNCH_TIMES, launchTimes);
        log("Launch times; " + launchTimes);

        editor.apply();
        reviewManager = ReviewManagerFactory.create(context);
        mInstallDate = new Date(pref.getLong(KEY_INSTALL_DATE, 0));
        mLaunchTimes = pref.getInt(KEY_LAUNCH_TIMES, 0);
        mOptOut = pref.getBoolean(KEY_OPT_OUT, false);
        mAskLaterDate = new Date(pref.getLong(KEY_ASK_LATER_DATE, 0));

        printStatus(context);
    }

    /**
     * This API is deprecated.
     * You should call onCreate instead of this API in Activity's onCreate().
     */
    @Deprecated
    public static void onStart(Context context) {
        onCreate(context);
    }

    /**
     * Show the rate dialog if the criteria is satisfied.
     * @param context Context
     * @return true if shown, false otherwise.
     */
    public static boolean showRateDialogIfNeeded(final Context context) {
        if (shouldShowRateDialog()) {
            showGoogleReview(context, 0, false);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Show the rate dialog if the criteria is satisfied.
     * @param context Context
     * @param themeId Theme ID
     * @return true if shown, false otherwise.
     */
    public static boolean showRateDialogIfNeeded(final Context context, int themeId) {
        if (shouldShowRateDialog()) {
            showGoogleReview(context, themeId, true);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check whether the rate dialog should be shown or not.
     * Developers may call this method directly if they want to show their own view instead of
     * dialog provided by this library.
     */
    public static boolean shouldShowRateDialog() {
        if (mOptOut) {
            return false;
        } else {
            if (mLaunchTimes >= sConfig.mCriteriaLaunchTimes) {
                return true;
            }
            long threshold = TimeUnit.DAYS.toMillis(sConfig.mCriteriaInstallDays);   // msec
            if (new Date().getTime() - mInstallDate.getTime() >= threshold &&
                    new Date().getTime() - mAskLaterDate.getTime() >= threshold) {
                return true;
            }
            return false;
        }
    }

    /**
     * Show the rate dialog
     */
    public static void showRateDialog(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        showRateDialog(context, builder);
    }

    /**
     * Show the rate dialog
     */
    public static void showRateDialog(final Context context, int themeId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, themeId);
        showRateDialog(context, builder);
    }

    /**
     * Show the rate dialog
     */
    public static void showRateAppInReviewDialog(final Context context) {
        showGoogleReview(context, 0, false);
    }

    /**
     * Show the rate dialog
     */
    public static void showRateAppInReviewDialog(final Context context, int themeId) {
        showGoogleReview(context, themeId, true);
    }

    public static void showGoogleReview(final Context context, final int themeId, final boolean isThemeNeed) {
        reviewManager = ReviewManagerFactory.create(context);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP){
            Task<ReviewInfo> request = reviewManager.requestReviewFlow();
            request.addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // We can get the ReviewInfo object
                    ReviewInfo reviewInfo = task.getResult();
                    Task<Void> flow = reviewManager.launchReviewFlow((Activity) context, reviewInfo);
                    flow.addOnCompleteListener(task1 -> {

                    });
                    flow.addOnFailureListener(e -> setAlertDialog(context, themeId, isThemeNeed));
                } else {
                    // There was some problem, continue regardless of the result.
                    // show native rate app dialog on error
                    setAlertDialog(context, themeId, isThemeNeed);
                }
            });
        } else{
            setAlertDialog(context, themeId, isThemeNeed);
        }
    }

    private static void setAlertDialog(final Context context, final int themeId, final boolean isThemeNeed) {
        AlertDialog.Builder builder;
        if (isThemeNeed) {
            builder = new AlertDialog.Builder(context, themeId);
        } else {
            builder = new AlertDialog.Builder(context);
        }
        showRateDialog(context, builder);
    }

    /**
     * Stop showing the rate dialog
     */
    public static void stopRateDialog(final Context context){
        setOptOut(context, true);
    }

    /**
     * Get count number of the rate dialog launches
     */
    public static int getLaunchCount(final Context context){
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return pref.getInt(KEY_LAUNCH_TIMES, 0);
    }

    private static void showRateDialog(final Context context, AlertDialog.Builder builder) {
        if (sDialogRef != null && sDialogRef.get() != null) {
            // Dialog is already present
            return;
        }

        int titleId = sConfig.mTitleId != 0 ? sConfig.mTitleId : R.string.rta_dialog_title;
        int messageId = sConfig.mMessageId != 0 ? sConfig.mMessageId : R.string.rta_dialog_message;
        int cancelButtonID = sConfig.mCancelButton != 0 ? sConfig.mCancelButton : R.string.rta_dialog_cancel;
        int thanksButtonID = sConfig.mNoButtonId != 0 ? sConfig.mNoButtonId : R.string.rta_dialog_no;
        int rateButtonID = sConfig.mYesButtonId != 0 ? sConfig.mYesButtonId : R.string.rta_dialog_ok;
        builder.setTitle(titleId);
        builder.setMessage(messageId);
        switch (sConfig.mCancelMode) {
            case Config.CANCEL_MODE_BACK_KEY_OR_TOUCH_OUTSIDE:
                builder.setCancelable(true); // It's the default anyway
                break;
            case Config.CANCEL_MODE_BACK_KEY:
                builder.setCancelable(false);
                builder.setOnKeyListener((dialog, keyCode, event) -> {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        dialog.cancel();
                        return true;
                    } else {
                        return false;
                    }
                });
                break;
            case Config.CANCEL_MODE_NONE:
                builder.setCancelable(false);
                break;
        }
        builder.setPositiveButton(rateButtonID, (dialog, which) -> {
            if (sCallback != null) {
                sCallback.onYesClicked();
            }
            String appPackage = context.getPackageName();
            String url = "market://details?id=" + appPackage;
            if (!TextUtils.isEmpty(sConfig.mUrl)) {
                url = sConfig.mUrl;
            }
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (android.content.ActivityNotFoundException anfe) {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + context.getPackageName())));
            }
            setOptOut(context, true);
        });
        builder.setNeutralButton(cancelButtonID, (dialog, which) -> {
            if (sCallback != null) {
                sCallback.onCancelClicked();
            }
            clearSharedPreferences(context);
            storeAskLaterDate(context);
        });
        builder.setNegativeButton(thanksButtonID, (dialog, which) -> {
            if (sCallback != null) {
                sCallback.onNoClicked();
            }
            setOptOut(context, true);
        });
        builder.setOnCancelListener(dialog -> {
            if (sCallback != null) {
                sCallback.onCancelClicked();
            }
            clearSharedPreferences(context);
            storeAskLaterDate(context);
        });
        builder.setOnDismissListener(dialog -> sDialogRef.clear());
        sDialogRef = new WeakReference<>(builder.show());
    }

    /**
     * Clear data in shared preferences.<br>
     * This API is called when the "Later" is pressed or canceled.
     */
    private static void clearSharedPreferences(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Editor editor = pref.edit();
        editor.remove(KEY_INSTALL_DATE);
        editor.remove(KEY_LAUNCH_TIMES);
        editor.apply();
    }

    /**
     * Set opt out flag.
     * If it is true, the rate dialog will never shown unless app data is cleared.
     * This method is called when Yes or No is pressed.
     */
    private static void setOptOut(final Context context, boolean optOut) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Editor editor = pref.edit();
        editor.putBoolean(KEY_OPT_OUT, optOut);
        editor.apply();
        mOptOut = optOut;
    }

    /**
     * Store install date.
     * Install date is retrieved from package manager if possible.
     */
    private static void storeInstallDate(final Context context, SharedPreferences.Editor editor) {
        Date installDate = new Date();
        PackageManager packMan = context.getPackageManager();
        try {
            PackageInfo pkgInfo = packMan.getPackageInfo(context.getPackageName(), 0);
            installDate = new Date(pkgInfo.firstInstallTime);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        editor.putLong(KEY_INSTALL_DATE, installDate.getTime());
        log("First install: " + installDate);
    }

    /**
     * Store the date the user asked for being asked again later.
     */
    private static void storeAskLaterDate(final Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Editor editor = pref.edit();
        editor.putLong(KEY_ASK_LATER_DATE, System.currentTimeMillis());
        editor.apply();
    }

    /**
     * Print values in SharedPreferences (used for debug)
     */
    private static void printStatus(final Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        log("*** RateThisApp Status ***");
        log("Install Date: " + new Date(pref.getLong(KEY_INSTALL_DATE, 0)));
        log("Launch Times: " + pref.getInt(KEY_LAUNCH_TIMES, 0));
        log("Opt out: " + pref.getBoolean(KEY_OPT_OUT, false));
    }

    /**
     * Print log if enabled
     */
    private static void log(String message) {
        if (DEBUG) {
            Log.v(TAG, message);
        }
    }

    /**
     * RateThisApp configuration.
     */
    public static class Config {
        public static final int CANCEL_MODE_BACK_KEY_OR_TOUCH_OUTSIDE = 0;
        public static final int CANCEL_MODE_BACK_KEY                  = 1;
        public static final int CANCEL_MODE_NONE                      = 2;

        private String mUrl = null;
        private int mCriteriaInstallDays;
        private int mCriteriaLaunchTimes;
        private int mTitleId = 0;
        private int mMessageId = 0;
        private int mYesButtonId = 0;
        private int mNoButtonId = 0;
        private int mCancelButton = 0;
        private int mCancelMode = CANCEL_MODE_BACK_KEY_OR_TOUCH_OUTSIDE;

        /**
         * Constructor with default criteria.
         */
        public Config() {
            this(7, 10);
        }

        /**
         * Constructor.
         */
        public Config(int criteriaInstallDays, int criteriaLaunchTimes) {
            this.mCriteriaInstallDays = criteriaInstallDays;
            this.mCriteriaLaunchTimes = criteriaLaunchTimes;
        }

        /**
         * Set title string ID.
         */
        public void setTitle(@StringRes int stringId) {
            this.mTitleId = stringId;
        }

        /**
         * Set message string ID.
         */
        public void setMessage(@StringRes int stringId) {
            this.mMessageId = stringId;
        }

        /**
         * Set rate now string ID.
         */
        public void setYesButtonText(@StringRes int stringId) {
            this.mYesButtonId = stringId;
        }

        /**
         * Set no thanks string ID.
         */
        public void setNoButtonText(@StringRes int stringId) {
            this.mNoButtonId = stringId;
        }

        /**
         * Set cancel string ID.
         */
        public void setCancelButtonText(@StringRes int stringId) {
            this.mCancelButton = stringId;
        }

        /**
         * Set navigation url when user clicks rate button.
         * Typically, url will be <a href="https://play.google.com/store/apps/details?id=PACKAGE_NAME">...</a> for Google Play.
         */
        public void setUrl(String url) {
            this.mUrl = url;
        }

        /**
         * Set the cancel mode; namely, which ways the user can cancel the dialog.
         */
        public void setCancelMode(int cancelMode) {
            this.mCancelMode = cancelMode;
        }
    }

    /**
     * Callback of dialog click event
     */
    public interface Callback {
        /**
         * "Rate now" event
         */
        void onYesClicked();

        /**
         * "No, thanks" event
         */
        void onNoClicked();

        /**
         * "Later" event
         */
        void onCancelClicked();
    }
}
