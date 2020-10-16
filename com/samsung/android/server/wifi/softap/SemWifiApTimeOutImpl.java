package com.samsung.android.server.wifi.softap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.util.WakeupMessage;
import com.android.server.wifi.FrameworkFacade;
import com.android.server.wifi.WifiInjector;
import com.samsung.android.feature.SemCscFeature;
import com.sec.android.app.CscFeatureTagWifi;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SemWifiApTimeOutImpl {
    public static final int CMD_NO_ASSOCIATED_STATIONS_TIMEOUT = 1;
    public static final String CONFIGOPBRANDINGFORMOBILEAP = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGOPBRANDINGFORMOBILEAP, "ALL");
    private static final boolean DBG = true;
    public static final int DEFAULT_TIMEOUT_MOBILEAP = SemCscFeature.getInstance().getInteger(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGMOBILEAPDEFAULTTIMEOUT, 1200);
    public static final String SOFT_AP_SEND_MESSAGE_TIMEOUT_TAG = "SemWifiApTimeOutImpl Soft AP Send Message Timeout";
    private static final String TAG = "SemWifiApTimeOutImpl";
    private static final int TURNOFF_HOTSPOT = 17042606;
    public static final String TURNOFF_HOTSPOT_ACTION = "com.samsung.settings.wifi.mobileap.TURNOFF_HOTSPOT";
    public static String mDeviceType = null;
    private int NumOfClientsConnected = 0;
    private Context mContext;
    private FrameworkFacade mFrameworkFacade;
    private Handler mHandler;
    private List<String> mMHSDumpLogs = new ArrayList();
    private Notification.Builder mNotificationBuilder = null;
    private NotificationManager mNotificationManager = null;
    private boolean mScheduled = false;
    private SoftApCallback mSoftApCallback = new SoftApCallback();
    private final BroadcastReceiver mSoftApReceiver;
    private final IntentFilter mSoftApReceiverFilter;
    private SoftApTimeoutEnabledSettingObserver mSoftApTimeoutEnabledSettingObserver;
    private WakeupMessage mSoftApTimeoutMessage;
    private int mTimeoutvalue = (DEFAULT_TIMEOUT_MOBILEAP / 60);
    private boolean mUSBpuggedin = false;
    private int mWifiApState = 11;
    private WifiManager mWifiManager;

    public SemWifiApTimeOutImpl(Context context, FrameworkFacade mFrameworkFacade2) {
        this.mContext = context;
        this.mSoftApReceiverFilter = new IntentFilter("android.intent.action.ACTION_POWER_CONNECTED");
        this.mSoftApReceiverFilter.addAction("android.intent.action.ACTION_POWER_DISCONNECTED");
        this.mSoftApReceiverFilter.addAction(TURNOFF_HOTSPOT_ACTION);
        this.mSoftApReceiverFilter.addAction("com.samsung.android.net.wifi.WIFI_AP_STA_STATUS_CHANGED");
        this.mSoftApReceiver = new BroadcastReceiver() {
            /* class com.samsung.android.server.wifi.softap.SemWifiApTimeOutImpl.C07961 */

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals("com.samsung.android.net.wifi.WIFI_AP_STA_STATUS_CHANGED")) {
                    int ClientNum = intent.getIntExtra("NUM", 0);
                    Log.d(SemWifiApTimeOutImpl.TAG, "onNumClientsChanged:" + ClientNum);
                    SemWifiApTimeOutImpl.this.NumOfClientsConnected = ClientNum;
                    if (SemWifiApTimeOutImpl.this.NumOfClientsConnected > 0) {
                        SemWifiApTimeOutImpl.this.cancelTimeoutMessage();
                    } else {
                        SemWifiApTimeOutImpl.this.scheduleTimeoutMessage();
                    }
                } else if (action.equals("android.intent.action.ACTION_POWER_CONNECTED")) {
                    Log.d(SemWifiApTimeOutImpl.TAG, "unplugged --> plugged");
                    SemWifiApTimeOutImpl.this.mUSBpuggedin = true;
                    SemWifiApTimeOutImpl.this.cancelTimeoutMessage();
                } else if (action.equals("android.intent.action.ACTION_POWER_DISCONNECTED")) {
                    Log.d(SemWifiApTimeOutImpl.TAG, "plugged --> Unplugged");
                    SemWifiApTimeOutImpl.this.mUSBpuggedin = false;
                    SemWifiApTimeOutImpl.this.scheduleTimeoutMessage();
                } else if (SemWifiApTimeOutImpl.TURNOFF_HOTSPOT_ACTION.equals(action)) {
                    SemWifiApTimeOutImpl semWifiApTimeOutImpl = SemWifiApTimeOutImpl.this;
                    semWifiApTimeOutImpl.clearTimeoutNotification(semWifiApTimeOutImpl.mContext);
                }
            }
        };
        this.mFrameworkFacade = mFrameworkFacade2;
    }

    /* access modifiers changed from: private */
    public class SoftApCallback implements WifiManager.SoftApCallback {
        private SoftApCallback() {
        }

        public void onStateChanged(int state, int failureReason) {
            Log.d(SemWifiApTimeOutImpl.TAG, "onStateChanged:" + state);
            SemWifiApTimeOutImpl.this.mWifiApState = state;
            if (SemWifiApTimeOutImpl.this.mWifiApState == 11 || SemWifiApTimeOutImpl.this.mWifiApState == 14) {
                SemWifiApTimeOutImpl.this.NumOfClientsConnected = 0;
                SemWifiApTimeOutImpl.this.cancelTimeoutMessage();
                return;
            }
            SemWifiApTimeOutImpl.this.scheduleTimeoutMessage();
        }

        public void onNumClientsChanged(int numClients) {
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void scheduleTimeoutMessage() {
        if (this.mSoftApTimeoutMessage != null) {
            if (this.mScheduled) {
                cancelTimeoutMessage();
            }
            if (this.NumOfClientsConnected == 0) {
                this.NumOfClientsConnected = getConnectedDevicesNum();
            }
            if (this.mTimeoutvalue != 0 && this.NumOfClientsConnected <= 0 && this.mWifiApState == 13) {
                if (isTablet() && "ATT".equals(CONFIGOPBRANDINGFORMOBILEAP)) {
                    return;
                }
                if ((!"TMO".equals(CONFIGOPBRANDINGFORMOBILEAP) && !"NEWCO".equals(CONFIGOPBRANDINGFORMOBILEAP)) || !this.mUSBpuggedin) {
                    this.mScheduled = true;
                    this.mSoftApTimeoutMessage.schedule(SystemClock.elapsedRealtime() + ((long) (this.mTimeoutvalue * 60 * 1000)));
                    Log.e(TAG, "Timeout message scheduled for " + this.mTimeoutvalue + "minutes");
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void cancelTimeoutMessage() {
        WakeupMessage wakeupMessage = this.mSoftApTimeoutMessage;
        if (wakeupMessage != null && this.mScheduled) {
            this.mScheduled = false;
            wakeupMessage.cancel();
            Log.e(TAG, "Timeout message canceled");
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int getConnectedDevicesNum() {
        int res = 0;
        SemWifiApClientInfo mSemWifiApClientInfo = WifiInjector.getInstance().getSemWifiApClientInfo();
        if (mSemWifiApClientInfo != null) {
            res = mSemWifiApClientInfo.getConnectedDeviceLength();
        }
        Log.e(TAG, "Get connected devices num from hal " + res);
        return res;
    }

    /* access modifiers changed from: private */
    public class SoftApTimeoutEnabledSettingObserver extends ContentObserver {
        SoftApTimeoutEnabledSettingObserver(Handler handler) {
            super(handler);
        }

        public void register() {
            SemWifiApTimeOutImpl.this.mFrameworkFacade.registerContentObserver(SemWifiApTimeOutImpl.this.mContext, Settings.Secure.getUriFor("wifi_ap_timeout_setting"), true, this);
            SemWifiApTimeOutImpl.this.mTimeoutvalue = getValue();
            SemWifiApTimeOutImpl semWifiApTimeOutImpl = SemWifiApTimeOutImpl.this;
            semWifiApTimeOutImpl.addMHSDumpLog("mTimeoutValue: " + SemWifiApTimeOutImpl.this.mTimeoutvalue);
        }

        public void unregister() {
            SemWifiApTimeOutImpl.this.mFrameworkFacade.unregisterContentObserver(SemWifiApTimeOutImpl.this.mContext, this);
        }

        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            SemWifiApTimeOutImpl.this.mTimeoutvalue = getValue();
            SemWifiApTimeOutImpl semWifiApTimeOutImpl = SemWifiApTimeOutImpl.this;
            semWifiApTimeOutImpl.addMHSDumpLog("mTimeoutValue: " + SemWifiApTimeOutImpl.this.mTimeoutvalue);
            SemWifiApTimeOutImpl.this.cancelTimeoutMessage();
            Log.e(SemWifiApTimeOutImpl.TAG, "onChange=" + SemWifiApTimeOutImpl.this.mTimeoutvalue);
            SemWifiApTimeOutImpl.this.scheduleTimeoutMessage();
        }

        private int getValue() {
            return Settings.Secure.getInt(SemWifiApTimeOutImpl.this.mContext.getContentResolver(), "wifi_ap_timeout_setting", SemWifiApTimeOutImpl.DEFAULT_TIMEOUT_MOBILEAP / 60);
        }
    }

    public void registerSoftApCallback() {
        Log.e(TAG, "registerSoftApCallback");
        this.mUSBpuggedin = isPlugged(this.mContext);
        Log.d(TAG, "mUSBpuggedin:" + this.mUSBpuggedin);
        this.mScheduled = false;
        this.mHandler = new Handler(Looper.getMainLooper()) {
            /* class com.samsung.android.server.wifi.softap.SemWifiApTimeOutImpl.HandlerC07972 */

            public void handleMessage(Message inputMessage) {
                Log.e(SemWifiApTimeOutImpl.TAG, "Received timeout");
                if (inputMessage.what == 1) {
                    PowerManager.WakeLock mStopService = ((PowerManager) SemWifiApTimeOutImpl.this.mContext.getSystemService("power")).newWakeLock(1, "MobileAPCloseService");
                    if (mStopService != null) {
                        try {
                            mStopService.acquire();
                        } catch (Exception e) {
                            Log.i(SemWifiApTimeOutImpl.TAG, "Cannot acquire wake lock ~~ " + e);
                        }
                    }
                    if (SemWifiApTimeOutImpl.this.NumOfClientsConnected == 0) {
                        SemWifiApTimeOutImpl semWifiApTimeOutImpl = SemWifiApTimeOutImpl.this;
                        semWifiApTimeOutImpl.NumOfClientsConnected = semWifiApTimeOutImpl.getConnectedDevicesNum();
                    }
                    if (((!"TMO".equals(SemWifiApTimeOutImpl.CONFIGOPBRANDINGFORMOBILEAP) && !"NEWCO".equals(SemWifiApTimeOutImpl.CONFIGOPBRANDINGFORMOBILEAP)) || !SemWifiApTimeOutImpl.this.mUSBpuggedin) && SemWifiApTimeOutImpl.this.mTimeoutvalue != 0 && SemWifiApTimeOutImpl.this.NumOfClientsConnected == 0 && SemWifiApTimeOutImpl.this.mWifiApState == 13) {
                        Log.e(SemWifiApTimeOutImpl.TAG, "Received timeout event,disabling hotapot");
                        SemWifiApTimeOutImpl.this.mWifiManager.semSetWifiApEnabled(null, false);
                    }
                    if ("VZW".equals(SemWifiApTimeOutImpl.CONFIGOPBRANDINGFORMOBILEAP)) {
                        Toast.makeText(SemWifiApTimeOutImpl.this.mContext, 17042609, 0).show();
                    }
                    if ("ATT".equals(SemWifiApTimeOutImpl.CONFIGOPBRANDINGFORMOBILEAP)) {
                        SemWifiApTimeOutImpl semWifiApTimeOutImpl2 = SemWifiApTimeOutImpl.this;
                        semWifiApTimeOutImpl2.showTimeoutNotification(semWifiApTimeOutImpl2.mContext);
                    }
                    if (mStopService != null) {
                        try {
                            mStopService.release();
                        } catch (Exception e2) {
                            Log.i(SemWifiApTimeOutImpl.TAG, "Cannot release wake lock ~~ " + e2);
                        }
                    }
                }
            }
        };
        this.mWifiManager = (WifiManager) this.mContext.getSystemService("wifi");
        this.mWifiManager.registerSoftApCallback(this.mSoftApCallback, null);
        if (this.mSoftApTimeoutEnabledSettingObserver == null) {
            this.mSoftApTimeoutEnabledSettingObserver = new SoftApTimeoutEnabledSettingObserver(this.mHandler);
        }
        this.mSoftApTimeoutEnabledSettingObserver.register();
        this.mSoftApTimeoutMessage = new WakeupMessage(this.mContext, this.mHandler, SOFT_AP_SEND_MESSAGE_TIMEOUT_TAG, 1);
        Context context = this.mContext;
        if (context != null) {
            context.registerReceiver(this.mSoftApReceiver, this.mSoftApReceiverFilter);
        }
    }

    public void unRegisterSoftApCallback() {
        Log.e(TAG, "unregisterSoftApCallback");
        this.mHandler = null;
        this.mScheduled = false;
        this.mWifiManager = (WifiManager) this.mContext.getSystemService("wifi");
        try {
            this.mWifiManager.unregisterSoftApCallback(this.mSoftApCallback);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error : " + e);
        }
        this.mSoftApTimeoutEnabledSettingObserver.unregister();
        this.mSoftApTimeoutEnabledSettingObserver = null;
        this.mSoftApTimeoutMessage = null;
        Context context = this.mContext;
        if (context != null) {
            context.unregisterReceiver(this.mSoftApReceiver);
        }
    }

    public boolean isTablet() {
        mDeviceType = SystemProperties.get("ro.build.characteristics");
        String str = mDeviceType;
        if (str == null || str.length() <= 0) {
            return false;
        }
        return mDeviceType.contains("tablet");
    }

    private boolean isPlugged(Context context) {
        int plugged = context.registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED")).getIntExtra("plugged", -1);
        boolean iisPlugged = true;
        if (!(plugged == 1 || plugged == 2 || plugged == 4)) {
            iisPlugged = false;
        }
        Log.d(TAG, "iisPlugged:" + iisPlugged);
        return iisPlugged;
    }

    public String readSalesCode() {
        try {
            String sales_code = SystemProperties.get("ro.csc.sales_code");
            if (TextUtils.isEmpty(sales_code)) {
                return SystemProperties.get("ril.sales_code");
            }
            return sales_code;
        } catch (Exception e) {
            Log.e(TAG, "readSalesCode failed");
            return "";
        }
    }

    /* access modifiers changed from: package-private */
    public void showTimeoutNotification(Context context) {
        Log.e(TAG, "showing timeout notification for ATT");
        String title = this.mContext.getResources().getString(TURNOFF_HOTSPOT);
        if (this.mNotificationManager == null) {
            this.mNotificationManager = (NotificationManager) context.getSystemService("notification");
        }
        NotificationChannel mNotiChannel = new NotificationChannel("wifiap_timeout_notification", title, 4);
        PendingIntent contentIntent = PendingIntent.getBroadcast(context, 0, new Intent(TURNOFF_HOTSPOT_ACTION), 0);
        this.mNotificationManager.createNotificationChannel(mNotiChannel);
        if (this.mNotificationBuilder == null) {
            this.mNotificationBuilder = new Notification.Builder(context, "wifiap_timeout_notification");
        }
        this.mNotificationBuilder.setWhen(System.currentTimeMillis()).setShowWhen(true).setContentTitle(title).setSmallIcon(17301642).setContentIntent(contentIntent);
        this.mNotificationManager.notify(TURNOFF_HOTSPOT, this.mNotificationBuilder.build());
    }

    /* access modifiers changed from: package-private */
    public void clearTimeoutNotification(Context context) {
        if (this.mNotificationManager == null) {
            this.mNotificationManager = (NotificationManager) context.getSystemService("notification");
        }
        this.mNotificationManager.cancel(TURNOFF_HOTSPOT);
    }

    public void addMHSDumpLog(String log) {
        StringBuffer value = new StringBuffer();
        Log.i(TAG, log + " mhs: " + this.mMHSDumpLogs.size());
        value.append(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Long.valueOf(System.currentTimeMillis())) + " " + log + " ");
        if (this.mMHSDumpLogs.size() > 100) {
            this.mMHSDumpLogs.remove(0);
        }
        value.append(" mScheduled:" + this.mScheduled + " NumOfClientsConnected:" + this.NumOfClientsConnected + " mUSBpuggedin:" + this.mUSBpuggedin + " mWifiApState:" + this.mWifiApState + "\n");
        this.mMHSDumpLogs.add(value.toString());
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("====== SemWifiApTimeOutImpl dump ======= ");
        pw.println(this.mMHSDumpLogs.toString());
        pw.println("mScheduled:" + this.mScheduled);
        pw.println("NumOfClientsConnected:" + this.NumOfClientsConnected);
        pw.println("mUSBpuggedin:" + this.mUSBpuggedin);
        pw.println("mWifiApState:" + this.mWifiApState);
        pw.println("mTimeoutvalue:" + this.mTimeoutvalue);
    }
}
