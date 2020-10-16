package com.android.server.wifi.iwc;

import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.NetworkTemplate;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.server.LocalServices;
import com.android.server.net.NetworkStatsManagerInternal;
import com.android.server.wifi.WifiConfigManager;
import java.util.List;

public class IWCDataUsagePatternTracker {
    private static final double IWC_DATA_USAGE_RATIO = 2.0d;
    private static final long IWC_MOBILE_DATA_USAGE_THRESHOLD_MAX = 5000000000L;
    private static final long IWC_MOBILE_DATA_USAGE_THRESHOLD_MIN = 1000000000;
    private static final String TAG = "IWCMonitor.IWCDataUsagePatternTracker";
    private final Context mContext;
    private final IWCLogFile mIWCLog;
    private NetworkTemplate mMobileTemplate;
    private final NetworkStatsManager mNetworkStatsManager;
    private int mSubId = -1;
    private String mSubscriberId = "";
    private NetworkTemplate mWifiTemplate;

    public IWCDataUsagePatternTracker(Context context, IWCLogFile logFile) {
        this.mContext = context;
        this.mIWCLog = logFile;
        this.mNetworkStatsManager = (NetworkStatsManager) this.mContext.getSystemService(NetworkStatsManager.class);
    }

    private long getWifiDataUsage(long start, long end) {
        this.mWifiTemplate = NetworkTemplate.buildTemplateWifiWildcard();
        try {
            return ((NetworkStatsManagerInternal) LocalServices.getService(NetworkStatsManagerInternal.class)).getNetworkTotalBytes(this.mWifiTemplate, start, end);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to get Wifi data usage, remote call failed");
            return 0;
        }
    }

    private long getMobileDataUsage(long start, long end) {
        this.mSubId = getDefaultSubscriptionId(this.mContext);
        TelephonyManager tele = TelephonyManager.from(this.mContext);
        this.mSubscriberId = tele.getSubscriberId(this.mSubId);
        this.mMobileTemplate = NetworkTemplate.buildTemplateMobileAll(tele.getSubscriberId(this.mSubId));
        Log.d(TAG, "mSubId - " + this.mSubId + ", mSubscriberId - " + this.mSubscriberId);
        try {
            return ((NetworkStatsManagerInternal) LocalServices.getService(NetworkStatsManagerInternal.class)).getNetworkTotalBytes(this.mMobileTemplate, start, end);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to get Mobile data usage, remote call failed");
            return 0;
        }
    }

    public boolean getDataUsagePattern() {
        long start;
        boolean isSaver;
        long end = System.currentTimeMillis();
        long lastTimeOn = Settings.Global.getLong(this.mContext.getContentResolver(), "wifi_iwc_last_time_switch_to_mobile_on", 0);
        long temp = end - lastTimeOn;
        if (temp >= WifiConfigManager.MAX_PNO_SCAN_FREQUENCY_AGE_MS || temp <= 0) {
            start = end - WifiConfigManager.MAX_PNO_SCAN_FREQUENCY_AGE_MS;
        } else {
            start = lastTimeOn;
        }
        Log.d(TAG, "start: " + start + ", end: " + end + ", lastTimeOn: " + lastTimeOn);
        long bytesOfMobile = getMobileDataUsage(start, end);
        long bytesOfWifi = getWifiDataUsage(start, end);
        double ratioData = (((double) bytesOfWifi) / 1048576.0d) / (((double) bytesOfMobile) / 1048576.0d);
        if (bytesOfMobile <= IWC_MOBILE_DATA_USAGE_THRESHOLD_MIN || bytesOfMobile >= IWC_MOBILE_DATA_USAGE_THRESHOLD_MAX || ratioData <= IWC_DATA_USAGE_RATIO) {
            isSaver = false;
        } else {
            isSaver = true;
        }
        writeLog("DYNAMIC-QAI: ", "bytesOfMobile - " + bytesOfMobile + ", bytesOfWifi - " + bytesOfWifi + ", isSaver - " + isSaver);
        return isSaver;
    }

    private int getDefaultSubscriptionId(Context context) {
        SubscriptionManager subManager = SubscriptionManager.from(context);
        if (subManager == null) {
            return -1;
        }
        SubscriptionInfo subscriptionInfo = subManager.getDefaultDataSubscriptionInfo();
        if (subscriptionInfo == null) {
            List<SubscriptionInfo> list = subManager.getAllSubscriptionInfoList();
            if (list.size() == 0) {
                return -1;
            }
            subscriptionInfo = list.get(0);
        }
        return subscriptionInfo.getSubscriptionId();
    }

    private void writeLog(String valueName, String value) {
        IWCLogFile iWCLogFile = this.mIWCLog;
        if (iWCLogFile != null) {
            iWCLogFile.writeToLogFile(valueName, value);
        }
    }
}
