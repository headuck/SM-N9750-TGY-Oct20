package com.samsung.android.server.wifi.bigdata;

import android.app.ActivityManager;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SemHqmManager;
import android.util.Log;
import com.samsung.android.feature.SemFloatingFeature;
import java.util.HashMap;
import java.util.List;

public class WifiBigDataLogManager {
    private static final String ARGS_APP_ID_STR = "app_id";
    private static final String ARGS_BIGDATA_FLAG_STR = "bigdata";
    private static final String ARGS_DATA_STR = "data";
    private static final String ARGS_EXTRA_STR = "extra";
    private static final String ARGS_FEATURE_NAME = "feature";
    private static final String ARGS_VALUE_STR = "value";
    private static final int CMD_INSERT_LOG_FOR_ONOF = 1;
    private static boolean DBG = Debug.semIsProductDev();
    public static final boolean ENABLE_SURVEY_MODE = "TRUE".equals(SemFloatingFeature.getInstance().getString("SEC_FLOATING_FEATURE_CONTEXTSERVICE_ENABLE_SURVEY_MODE"));
    public static final boolean ENABLE_UNIFIED_HQM_SERVER = true;
    public static final String FEATURE_24HR = "W24H";
    public static final String FEATURE_ASSOC = "ASSO";
    public static final String FEATURE_BROADCOM_COUNTER_INFO = "CNTS";
    public static final String FEATURE_BROADCOM_COUNTER_INFO2 = "ECNT";
    public static final String FEATURE_CRASH = "CRSH";
    public static final String FEATURE_DISC = "DISC";
    public static final String FEATURE_EAP_INFO = "EAPT";
    public static final String FEATURE_GO_TO_WEBPAGE = "GOWP";
    public static final String FEATURE_HANG = "HANG";
    public static final String FEATURE_ISSUE_DETECTOR_DISC1 = "PDC1";
    public static final String FEATURE_ISSUE_DETECTOR_DISC2 = "PDC2";
    public static final String FEATURE_MHS_DISCONNECTION = "MHDC";
    public static final String FEATURE_MHS_INFO = "MHSI";
    public static final String FEATURE_MHS_ON_OF = "MHOF";
    public static final String FEATURE_MHS_POWERSAVEMODE = "MHPS";
    public static final String FEATURE_MHS_POWERSAVEMODE_TIME = "MHPT";
    public static final String FEATURE_MHS_SETTING = "MHSS";
    public static final String FEATURE_ON_OFF = "ONOF";
    public static final String FEATURE_SCAN = "SCAN";
    public static final String FEATURE_SI5G = "SI5G";
    public static final int LOGGING_TYPE_ADPS_STATE = 13;
    public static final int LOGGING_TYPE_BLUETOOTH_CONNECTION = 10;
    public static final int LOGGING_TYPE_CONFIG_NETWORK_TYPE = 11;
    public static final int LOGGING_TYPE_LOCAL_DISCONNECT_REASON = 8;
    public static final int LOGGING_TYPE_ROAM_TRIGGER = 7;
    public static final int LOGGING_TYPE_SET_CONNECTION_START_TIME = 12;
    public static final int LOGGING_TYPE_UPDATE_DATA_RATE = 9;
    private static final String PACKAGE_NAME_SURVEY = "com.samsung.android.providers.context";
    private static final String PROVIDER_NAME_SURVEY = "com.samsung.android.providers.context.log.action.USE_APP_FEATURE_SURVEY";
    private static final String TAG = "WifiBigDataLogManager";
    public final String APP_ID = "android.net.wifi";
    private final WifiBigDataLogAdapter mAdapter;
    private final HashMap<String, BaseBigDataItem> mBigDataItems = new HashMap<>();
    private final Context mContext;
    private final MainHandler mHandler;
    private int mLastUpdatedInternalReason = 0;
    private boolean mLogMessages = DBG;
    private SemHqmManager mSemHqmManager;

    public interface WifiBigDataLogAdapter {
        String getChipsetOuis();

        List<WifiConfiguration> getSavedNetworks();
    }

    public WifiBigDataLogManager(Context context, Looper workerLooper, WifiBigDataLogAdapter adapter) {
        this.mHandler = new MainHandler(workerLooper);
        this.mContext = context;
        this.mAdapter = adapter;
        initialize();
    }

    private void initialize() {
        this.mBigDataItems.clear();
        this.mBigDataItems.put(FEATURE_DISC, new BigDataItemDISC(FEATURE_DISC));
        this.mBigDataItems.put(FEATURE_HANG, new BigDataItemHANG(FEATURE_HANG));
        this.mBigDataItems.put("MHSI", new BigDataItemMHSI("MHSI"));
        this.mBigDataItems.put(FEATURE_MHS_POWERSAVEMODE, new BigDataItemMHPS(FEATURE_MHS_POWERSAVEMODE));
        this.mBigDataItems.put(FEATURE_MHS_POWERSAVEMODE_TIME, new BigDataItemMHPT(FEATURE_MHS_POWERSAVEMODE_TIME));
        this.mBigDataItems.put("MHDC", new BigDataItemMHDC("MHDC"));
        this.mBigDataItems.put(FEATURE_MHS_ON_OF, new BigDataItemMHOF(FEATURE_MHS_ON_OF));
        this.mBigDataItems.put(FEATURE_MHS_SETTING, new BigDataItemMHSS(FEATURE_MHS_SETTING));
        this.mBigDataItems.put(FEATURE_ON_OFF, new BigDataItemONOF(FEATURE_ON_OFF));
        this.mBigDataItems.put(FEATURE_24HR, new BigDataItemW24H(FEATURE_24HR));
        this.mBigDataItems.put(FEATURE_BROADCOM_COUNTER_INFO2, new BigDataItemECNT(FEATURE_BROADCOM_COUNTER_INFO2));
        this.mBigDataItems.put(FEATURE_ISSUE_DETECTOR_DISC1, new BigDataItemPDC1(FEATURE_ISSUE_DETECTOR_DISC1));
        this.mBigDataItems.put(FEATURE_ISSUE_DETECTOR_DISC2, new BigDataItemPDC2(FEATURE_ISSUE_DETECTOR_DISC2));
    }

    private String getJsonFormat(BaseBigDataItem item, int type) {
        if (item == null) {
            return null;
        }
        if (!item.isAvailableLogging(type)) {
            if (DBG) {
                Log.i(TAG, "not supported logging feature:" + item.getFeatureName() + " for type:" + type);
            }
            return null;
        }
        if (DBG) {
            Log.i(TAG, "getJsonFormat - feature : " + item.getFeatureName() + ", type : " + type);
        }
        return item.getJsonFormatFor(type);
    }

    public void setLogVisible(boolean visible) {
        this.mLogMessages = visible;
        for (BaseBigDataItem item : this.mBigDataItems.values()) {
            item.setLogVisible(visible);
        }
    }

    public void clearData(String feature) {
        BaseBigDataItem item = getBigDataItem(feature);
        if (item != null) {
            item.clearData();
        }
    }

    private synchronized BaseBigDataItem getBigDataItem(String feature) {
        return this.mBigDataItems.get(feature);
    }

    public static Bundle getBigDataBundle(String feature, String data) {
        Bundle args = new Bundle();
        args.putBoolean(ARGS_BIGDATA_FLAG_STR, true);
        args.putString(ARGS_FEATURE_NAME, feature);
        args.putString(ARGS_DATA_STR, data);
        return args;
    }

    public static Bundle getGSIMBundle(String feature, String extra, int value) {
        Bundle args = getGSIMBundle(feature, extra);
        args.putLong(ARGS_VALUE_STR, (long) value);
        return args;
    }

    public static Bundle getGSIMBundle(String feature, String extra) {
        Bundle args = new Bundle();
        args.putString(ARGS_FEATURE_NAME, feature);
        args.putString(ARGS_EXTRA_STR, extra);
        return args;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateTime(String feature) {
        BaseBigDataItem item = getBigDataItem(feature);
        if (item != null) {
            item.updateTime();
        }
    }

    private void addOrUpdateValueInternal(String feature, String key, String value) {
        BaseBigDataItem item = getBigDataItem(feature);
        if (item != null) {
            item.addOrUpdateValue(key, value);
        }
    }

    private void addOrUpdateValueInternal(String feature, String key, int value) {
        BaseBigDataItem item = getBigDataItem(feature);
        if (item != null) {
            item.addOrUpdateValue(key, value);
        }
    }

    public int getAndResetLastInternalReason() {
        int ret = this.mLastUpdatedInternalReason;
        this.mLastUpdatedInternalReason = 0;
        return ret;
    }

    public boolean addOrUpdateValue(int loggingType, int value) {
        switch (loggingType) {
            case 7:
                addOrUpdateValueInternal(FEATURE_DISC, "cn_rom", value);
                return true;
            case 8:
                addOrUpdateValueInternal(FEATURE_DISC, "cn_irs", value);
                this.mLastUpdatedInternalReason = value;
                return true;
            case 9:
                addOrUpdateValueInternal(FEATURE_DISC, "max_drt", value);
                return true;
            case 10:
                addOrUpdateValueInternal(FEATURE_DISC, "bt_cnt", value);
                return true;
            case 11:
                addOrUpdateValueInternal(FEATURE_DISC, "apwe", value);
                return true;
            case 12:
                updateTime(FEATURE_DISC);
                return false;
            case 13:
                addOrUpdateValueInternal(FEATURE_DISC, "adps", value);
                return true;
            default:
                return false;
        }
    }

    public boolean addOrUpdateValue(int loggingType, String value) {
        return false;
    }

    public void insertLog(Bundle args) {
        boolean isBigDataLog = args.getBoolean(ARGS_BIGDATA_FLAG_STR, false);
        String feature = args.getString(ARGS_FEATURE_NAME, null);
        if (this.mLogMessages) {
            Log.i(TAG, "insertLog bigData:" + isBigDataLog + " feature:" + feature);
        }
        if (isBigDataLog) {
            processBigDataLog(feature, args.getString(ARGS_DATA_STR, null));
            clearData(feature);
        }
    }

    private void checkAndGetHqmManager() {
        if (this.mSemHqmManager == null) {
            this.mSemHqmManager = (SemHqmManager) this.mContext.getSystemService("HqmManagerService");
        }
    }

    private void sendHWParamToHQM(String feature, String devCustomDataSet) {
        if (this.mSemHqmManager == null) {
            return;
        }
        if (feature != null) {
            if (DBG) {
                Log.d(TAG, "send H/W Parameters to HQM - feature : " + feature + ", logmaps : " + devCustomDataSet);
            }
            WifiChipInfo wifiChipInfo = WifiChipInfo.getInstance();
            String compManufacture = WifiChipInfo.getChipsetName();
            this.mSemHqmManager.sendHWParamToHQM(0, BaseBigDataItem.COMPONENT_ID, feature, BaseBigDataItem.HIT_TYPE_IMMEDIATLY, wifiChipInfo.getCidInfo(), compManufacture, devCustomDataSet, "", "");
        }
    }

    private void sendHWParamToHQMwithAppId(String feature, String hitType, String basicCustomDataSet, String devCustomDataSet) {
        if (this.mSemHqmManager != null && feature != null) {
            if (DBG) {
                Log.d(TAG, "send H/W Parameters to HQM with appid - feature : " + feature + ", logmaps: " + basicCustomDataSet + " private: " + devCustomDataSet);
                StringBuilder sb = new StringBuilder();
                sb.append("basic data size : ");
                Object obj = "0";
                sb.append(basicCustomDataSet == null ? obj : Integer.valueOf(basicCustomDataSet.length()));
                sb.append(", custom data size : ");
                if (devCustomDataSet != null) {
                    obj = Integer.valueOf(devCustomDataSet.length());
                }
                sb.append(obj);
                Log.d(TAG, sb.toString());
            }
            WifiChipInfo wifiChipInfo = WifiChipInfo.getInstance();
            this.mSemHqmManager.sendHWParamToHQMwithAppId(0, BaseBigDataItem.COMPONENT_ID, feature, hitType, wifiChipInfo.getCidInfo(), WifiChipInfo.getChipsetName(), devCustomDataSet == null ? "" : devCustomDataSet, basicCustomDataSet, "", "android.net.wifi");
        }
    }

    private void processBigDataLog(String feature, String data) {
        if (DBG) {
            Log.d(TAG, "insertLog - feature : " + feature + ", data : " + data);
        }
        if (feature != null && data != null) {
            int msgId = -1;
            long delayTimeMillis = 0;
            if (FEATURE_ON_OFF.equals(feature)) {
                msgId = 1;
                delayTimeMillis = 30000;
            } else if (FEATURE_DISC.equals(feature)) {
                data = data + " " + this.mAdapter.getChipsetOuis();
            }
            if (msgId == -1) {
                parseAndSendData(feature, data);
                return;
            }
            Bundle obj = new Bundle();
            obj.putString(ARGS_FEATURE_NAME, feature);
            obj.putString(ARGS_DATA_STR, data);
            MainHandler mainHandler = this.mHandler;
            mainHandler.sendMessageDelayed(mainHandler.obtainMessage(msgId, obj), delayTimeMillis);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void parseAndSendData(String feature, String data) {
        if (feature != null && data != null) {
            BaseBigDataItem item = getBigDataItem(feature);
            if (item == null) {
                if (DBG) {
                    Log.d(TAG, "feature " + feature + " is disabled");
                }
            } else if (item.parseData(data)) {
                boolean isSentLogs = false;
                checkAndGetHqmManager();
                String extra = getJsonFormat(item, 2);
                if (extra != null) {
                    sendHWParamToHQMwithAppId(feature, item.getHitType(), extra, getJsonFormat(item, 3));
                    isSentLogs = true;
                }
                String privateExtra = this.mSemHqmManager;
                String extra2 = getJsonFormat(item, 0);
                if (extra2 != null) {
                    sendHWParamToHQMwithAppId(feature, BaseBigDataItem.HIT_TYPE_ONCE_A_DAY, extra2, null);
                    isSentLogs = true;
                }
                if (!isSentLogs) {
                    Log.e(TAG, "parse error - extra is null");
                }
            } else {
                Log.e(TAG, "parse error - can't parse feature:" + feature + " data:" + data);
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String getForegroundPackageName() {
        List<ActivityManager.RunningTaskInfo> tasks = ((ActivityManager) this.mContext.getSystemService("activity")).getRunningTasks(1);
        if (!tasks.isEmpty()) {
            return new String(tasks.get(0).topActivity.getPackageName());
        }
        return null;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String getConfiguredNetworksSize() {
        int allNetworkCount = 0;
        int openNetworkCount = 0;
        int favoriteApCount = 0;
        List<WifiConfiguration> configs = this.mAdapter.getSavedNetworks();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                if (config.allowedKeyManagement.get(0)) {
                    openNetworkCount++;
                }
                if (config.semAutoWifiScore > 4) {
                    favoriteApCount++;
                }
                allNetworkCount++;
            }
        }
        return String.valueOf(allNetworkCount) + " " + String.valueOf(openNetworkCount) + " " + String.valueOf(favoriteApCount);
    }

    /* access modifiers changed from: private */
    public final class MainHandler extends Handler {
        public MainHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            Bundle args;
            String feature;
            String data;
            if (msg.obj != null && (feature = (args = (Bundle) msg.obj).getString(WifiBigDataLogManager.ARGS_FEATURE_NAME, null)) != null && (data = args.getString(WifiBigDataLogManager.ARGS_DATA_STR, null)) != null) {
                if (WifiBigDataLogManager.this.mLogMessages) {
                    Log.d(WifiBigDataLogManager.TAG, "handleMessage what=" + msg.what + " feature:" + feature);
                }
                if (msg.what == 1) {
                    String fgPackageName = WifiBigDataLogManager.this.getForegroundPackageName();
                    if (fgPackageName == null) {
                        fgPackageName = "x";
                    }
                    StringBuffer sb = new StringBuffer();
                    sb.append(data);
                    sb.append(" ");
                    sb.append(WifiBigDataLogManager.this.getConfiguredNetworksSize());
                    sb.append(" ");
                    sb.append(fgPackageName);
                    WifiBigDataLogManager.this.parseAndSendData(feature, sb.toString());
                    WifiBigDataLogManager.this.updateTime(WifiBigDataLogManager.FEATURE_ON_OFF);
                }
            }
        }
    }
}
