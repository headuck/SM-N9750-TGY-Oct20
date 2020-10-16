package com.samsung.android.server.wifi.softap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.server.wifi.WifiInjector;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.server.wifi.bigdata.WifiBigDataLogManager;
import com.sec.android.app.CscFeatureTagSetting;
import com.sec.android.app.CscFeatureTagWifi;

public class SemWifiApBroadcastReceiver {
    public static final String ADVANCED_WIFI_SHARING_NOTI = "com.samsung.intent.action.ADVANCED_WIFI_SHARING_NOTIFICATION";
    public static final String AP_STA_24GHZ_DISCONNECTED = "com.samsung.actoin.24GHZ_AP_STA_DISCONNECTED";
    private static final String CONFIGOPBRANDINGFORMOBILEAP = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGOPBRANDINGFORMOBILEAP, "ALL");
    private static final int DATA_REACH = 17042599;
    public static final int DIALOG_HOTSPOT_24GHZ_AP_STA_DISCONNECT = 51;
    public static final int DIALOG_HOTSPOT_NO_DATA = 1;
    public static final int DIALOG_HOTSPOT_PROVISIONING_REQUEST = 6;
    public static final int DIALOG_NAI_MISMATCH = 2;
    public static final int DIALOG_TETHERING_DENIED = 3;
    public static final int DIALOG_WIFI_AP_ENABLE_WARNING = 5;
    public static final int DIALOG_WIFI_ENABLE_WARNING = 4;
    public static final int DIALOG_WIFI_P2P_ENABLE_WARNING = 50;
    static final String INTENT_KEY_ICC_STATE = "ss";
    static final String INTENT_VALUE_ICC_IMSI = "IMSI";
    public static final String START_PROVISIONING = "com.samsung.intent.action.START_PROVISIONING";
    private static final String TAG = "SemWifiApBroadcastReceiver";
    public static final String WIFIAP_MODEMNAI_MISSMATH = "com.samsung.intent.action.MIP_ERROR";
    public static final String WIFIAP_PROVISION_DIALOG_TYPE = "wifiap_provision_dialog_type";
    public static final String WIFIAP_TETHERING_DENIED = "com.samsung.android.intent.action.TETHERING_DENIED";
    public static final String WIFIAP_TETHERING_FAILED = "com.samsung.android.intent.action.TETHERING_FAILED";
    public static final String WIFIAP_WARNING_DIALOG = "com.samsung.android.settings.wifi.mobileap.wifiapwarning";
    public static final String WIFIAP_WARNING_DIALOG_TYPE = "wifiap_warning_dialog_type";
    static String currentMccMnc = "";
    private static boolean isRegistered = false;
    private static long mBaseTxBytes = 0;
    private static final String mHotspotActionForSimStatus = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGHOTSPOTACTIONFORSIMSTATUS);
    private boolean bIsFirstCall = false;
    private boolean bUseMobileData = false;
    private long mAmountMobileRxBytes;
    private long mAmountMobileTxBytes;
    private long mAmountTimeOfMobileData;
    private long mBaseRxBytes;
    private Context mContext = null;
    private final IntentFilter mFilter;
    private NotificationManager mNotificationManager = null;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        /* class com.samsung.android.server.wifi.softap.SemWifiApBroadcastReceiver.C07911 */

        /* JADX INFO: Multiple debug info for r6v12 long: [D('mTx' long), D('mTxRx' long)] */
        /* JADX INFO: Multiple debug info for r12v8 long: [D('mMobileData' long), D('mRx' long)] */
        /* JADX WARNING: Removed duplicated region for block: B:74:0x02af  */
        /* JADX WARNING: Removed duplicated region for block: B:75:0x02b2  */
        /* JADX WARNING: Removed duplicated region for block: B:78:0x02c2  */
        /* JADX WARNING: Removed duplicated region for block: B:79:0x02c5  */
        public void onReceive(Context context, Intent intent) {
            Context context2;
            Intent intent2;
            String action;
            String str;
            WifiManager mWifiManager;
            String mApIface;
            String action2;
            String mhsData;
            long usedTimeOfMobileAp;
            long amountUsedTimeOfMobileData;
            long mTxRx;
            long mRx;
            String mTxRxMB;
            String mAmountMobileMB;
            String mDevideTimeOfMobileAp;
            String str2;
            String mhsData2;
            StringBuilder sb;
            SemWifiApBroadcastReceiver semWifiApBroadcastReceiver;
            String mIface;
            String mWifiSharing;
            WifiConfiguration mWifiConfig;
            String nameOfHotspot;
            String action3 = intent.getAction();
            Log.d(SemWifiApBroadcastReceiver.TAG, "Received : " + action3);
            if (action3.equals(SemWifiApBroadcastReceiver.WIFIAP_MODEMNAI_MISSMATH) || action3.equals(SemWifiApBroadcastReceiver.WIFIAP_TETHERING_DENIED)) {
                context2 = context;
                str = SemWifiApBroadcastReceiver.TAG;
                intent2 = intent;
                action = action3;
            } else if (action3.equals(SemWifiApBroadcastReceiver.WIFIAP_TETHERING_FAILED)) {
                context2 = context;
                str = SemWifiApBroadcastReceiver.TAG;
                intent2 = intent;
                action = action3;
            } else if (action3.equals(SemWifiApBroadcastReceiver.AP_STA_24GHZ_DISCONNECTED)) {
                Log.d(SemWifiApBroadcastReceiver.TAG, "Sending the dialog type51");
                SemWifiApBroadcastReceiver.this.showHotspotErrorDialog(context, 51, intent);
                return;
            } else if (action3.equals("com.nttdocomo.intent.action.SHOW_WPSDIALOG")) {
                SemWifiApBroadcastReceiver.this.startWifiApSettings(context);
                return;
            } else if (action3.equals(SemWifiApBroadcastReceiver.START_PROVISIONING)) {
                WifiManager mWifiManager2 = (WifiManager) context.getSystemService("wifi");
                if (mWifiManager2.getWifiApState() == 13) {
                    int provisionSuccess = mWifiManager2.getProvisionSuccess();
                    boolean isProvisioningEnabled = SemWifiApBroadcastReceiver.this.isProvisioningNeeded(context);
                    Log.d(SemWifiApBroadcastReceiver.TAG, "check Start provisioning as wifi disconnected getProvisionSuccess " + provisionSuccess + "isProvisioningNeeded " + isProvisioningEnabled + "wifisharing " + SemWifiApBroadcastReceiver.this.isWifiSharingEnabled(context));
                    if (isProvisioningEnabled && provisionSuccess != 1 && !"VZW".equals(SemWifiApBroadcastReceiver.CONFIGOPBRANDINGFORMOBILEAP)) {
                        SemWifiApBroadcastReceiver.this.startHotspotProvisioningRequestWifiSharing(context, 6);
                    }
                }
                return;
            } else if (SemWifiApBroadcastReceiver.ADVANCED_WIFI_SHARING_NOTI.equals(action3)) {
                int notification_task = intent.getIntExtra("NOTIFICATION_TASK", 0);
                if (notification_task == 0) {
                    SemWifiApBroadcastReceiver.this.clearWifiScanListNotification(context);
                } else if (notification_task == 1) {
                    SemWifiApBroadcastReceiver.this.showWifiScanListNotification(context, context.getResources().getString(17039545));
                }
                return;
            } else if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action3)) {
                int apState = intent.getIntExtra("wifi_state", 14);
                Log.d(SemWifiApBroadcastReceiver.TAG, "onreceive WIFI_AP_STATE_CHANGED_ACTION] apState : " + apState);
                if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing()) {
                    mApIface = "swlan0";
                } else {
                    mApIface = "wlan0";
                }
                switch (apState) {
                    case 10:
                        try {
                            if (SemWifiApBroadcastReceiver.mBaseTxBytes != 0) {
                                try {
                                    long endTime = System.currentTimeMillis();
                                    usedTimeOfMobileAp = (endTime - SemWifiApBroadcastReceiver.this.mTimeOfStartMobileAp) / 60000;
                                    if (SemWifiApBroadcastReceiver.this.bUseMobileData) {
                                        try {
                                            amountUsedTimeOfMobileData = SemWifiApBroadcastReceiver.this.mAmountTimeOfMobileData + ((endTime - SemWifiApBroadcastReceiver.this.mStartTimeOfMobileData) / 60000);
                                            long mEndTempMobileTxBytes = TrafficStats.getTxBytes(mApIface);
                                            long mEndTempMobileRxBytes = TrafficStats.getRxBytes(mApIface);
                                            try {
                                                long mTempAmountTxBytes = SemWifiApBroadcastReceiver.this.mAmountMobileTxBytes;
                                                long mTempAmountRxBytes = SemWifiApBroadcastReceiver.this.mAmountMobileRxBytes;
                                                SemWifiApBroadcastReceiver.this.mAmountMobileTxBytes = mTempAmountTxBytes + (mEndTempMobileTxBytes - SemWifiApBroadcastReceiver.this.mTempMobileTxBytes);
                                                SemWifiApBroadcastReceiver.this.mAmountMobileRxBytes = mTempAmountRxBytes + (mEndTempMobileRxBytes - SemWifiApBroadcastReceiver.this.mTempMobileRxBytes);
                                            } catch (Exception e) {
                                                e = e;
                                                action2 = action3;
                                                mhsData = SemWifiApBroadcastReceiver.TAG;
                                            }
                                        } catch (Exception e2) {
                                            e = e2;
                                            action2 = action3;
                                            mhsData = SemWifiApBroadcastReceiver.TAG;
                                            Log.e(mhsData, "Error in getting wlan0 interface config:" + e);
                                            return;
                                        }
                                    } else {
                                        try {
                                            amountUsedTimeOfMobileData = SemWifiApBroadcastReceiver.this.mAmountTimeOfMobileData;
                                        } catch (Exception e3) {
                                            e = e3;
                                            action2 = action3;
                                            mhsData = SemWifiApBroadcastReceiver.TAG;
                                            Log.e(mhsData, "Error in getting wlan0 interface config:" + e);
                                            return;
                                        }
                                    }
                                    mTxRx = (TrafficStats.getTxBytes(mApIface) - SemWifiApBroadcastReceiver.mBaseTxBytes) + (TrafficStats.getRxBytes(mApIface) - SemWifiApBroadcastReceiver.this.mBaseRxBytes);
                                    mRx = mTxRx - (SemWifiApBroadcastReceiver.this.mAmountMobileTxBytes + SemWifiApBroadcastReceiver.this.mAmountMobileRxBytes);
                                    mTxRxMB = SemWifiApBroadcastReceiver.this.convertBytesToMegaByte(mTxRx);
                                } catch (Exception e4) {
                                    e = e4;
                                    action2 = action3;
                                    mhsData = SemWifiApBroadcastReceiver.TAG;
                                    Log.e(mhsData, "Error in getting wlan0 interface config:" + e);
                                    return;
                                }
                                try {
                                    mAmountMobileMB = SemWifiApBroadcastReceiver.this.convertBytesToMegaByte(mRx);
                                    mDevideTimeOfMobileAp = SemWifiApBroadcastReceiver.this.convertMinute(usedTimeOfMobileAp);
                                    action2 = action3;
                                } catch (Exception e5) {
                                    e = e5;
                                    action2 = action3;
                                    mhsData = SemWifiApBroadcastReceiver.TAG;
                                    Log.e(mhsData, "Error in getting wlan0 interface config:" + e);
                                    return;
                                }
                                try {
                                    String mDevideTimeOfMobileData = SemWifiApBroadcastReceiver.this.convertMinute(amountUsedTimeOfMobileData);
                                    if (usedTimeOfMobileAp - amountUsedTimeOfMobileData >= 0) {
                                        try {
                                            sb = new StringBuilder();
                                            sb.append(mDevideTimeOfMobileAp);
                                            sb.append(" ");
                                            sb.append(mDevideTimeOfMobileData);
                                            sb.append(" ");
                                            semWifiApBroadcastReceiver = SemWifiApBroadcastReceiver.this;
                                            str2 = SemWifiApBroadcastReceiver.TAG;
                                        } catch (Exception e6) {
                                            e = e6;
                                            mhsData = SemWifiApBroadcastReceiver.TAG;
                                            Log.e(mhsData, "Error in getting wlan0 interface config:" + e);
                                            return;
                                        }
                                        try {
                                            sb.append(semWifiApBroadcastReceiver.convertMinute(usedTimeOfMobileAp - amountUsedTimeOfMobileData));
                                            sb.append(" ");
                                            sb.append(usedTimeOfMobileAp);
                                            sb.append(" ");
                                            sb.append(amountUsedTimeOfMobileData);
                                            sb.append(" ");
                                            sb.append(usedTimeOfMobileAp - amountUsedTimeOfMobileData);
                                            sb.append(" ");
                                            sb.append(mTxRxMB);
                                            sb.append(" ");
                                            sb.append(mAmountMobileMB);
                                            sb.append(" ");
                                            sb.append(SemWifiApBroadcastReceiver.this.convertBytesToMegaByte(mTxRx - mRx));
                                            sb.append(" ");
                                            sb.append(SemWifiApBroadcastReceiver.this.convertBytesToMegaByteForLogging(mTxRx));
                                            sb.append(" ");
                                            sb.append(SemWifiApBroadcastReceiver.this.convertBytesToMegaByteForLogging(mRx));
                                            sb.append(" ");
                                            sb.append(SemWifiApBroadcastReceiver.this.convertBytesToMegaByteForLogging(mTxRx - mRx));
                                            mhsData2 = sb.toString();
                                        } catch (Exception e7) {
                                            e = e7;
                                            mhsData = str2;
                                            Log.e(mhsData, "Error in getting wlan0 interface config:" + e);
                                            return;
                                        }
                                    } else {
                                        str2 = SemWifiApBroadcastReceiver.TAG;
                                        mhsData2 = null;
                                    }
                                    if (mhsData2 != null) {
                                        try {
                                            SemWifiApBroadcastReceiver.this.callSecBigdataApi(WifiBigDataLogManager.FEATURE_MHS_SETTING, mhsData2);
                                        } catch (Exception e8) {
                                            e = e8;
                                            mhsData = str2;
                                            Log.e(mhsData, "Error in getting wlan0 interface config:" + e);
                                            return;
                                        }
                                    }
                                    if (SemWifiApBroadcastReceiver.this.mTelephonyPhoneStateListener != null) {
                                        SemWifiApBroadcastReceiver.this.mTelephonyManagerForHotspot.listen(SemWifiApBroadcastReceiver.this.mTelephonyPhoneStateListener, 0);
                                    }
                                    SemWifiApBroadcastReceiver.this.resetParameterForHotspotLogging();
                                } catch (Exception e9) {
                                    e = e9;
                                    mhsData = SemWifiApBroadcastReceiver.TAG;
                                    Log.e(mhsData, "Error in getting wlan0 interface config:" + e);
                                    return;
                                }
                            } else {
                                action2 = action3;
                                mhsData = SemWifiApBroadcastReceiver.TAG;
                                try {
                                    Log.d(mhsData, "unnormal status of interface");
                                } catch (Exception e10) {
                                    e = e10;
                                    Log.e(mhsData, "Error in getting wlan0 interface config:" + e);
                                    return;
                                }
                            }
                        } catch (Exception e11) {
                            e = e11;
                            action2 = action3;
                            mhsData = SemWifiApBroadcastReceiver.TAG;
                            Log.e(mhsData, "Error in getting wlan0 interface config:" + e);
                            return;
                        }
                    case 11:
                        WifiManager wm = (WifiManager) context.getSystemService("wifi");
                        if (wm != null) {
                            if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing()) {
                                mIface = "swlan0";
                            } else {
                                mIface = "wlan0";
                            }
                            int mTimeoutSetting = Settings.Secure.getInt(context.getContentResolver(), "wifi_ap_timeout_setting", SemCscFeature.getInstance().getInteger(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGMOBILEAPDEFAULTTIMEOUT, 1200) / 60);
                            ContentResolver cr = context.getContentResolver();
                            if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing()) {
                                if (Settings.Secure.getInt(cr, "wifi_ap_wifi_sharing", 10) == 10) {
                                    Log.d(SemWifiApBroadcastReceiver.TAG, "Wifi Sharing first time provider value " + Settings.Secure.getInt(cr, "wifi_ap_wifi_sharing", 10));
                                    mWifiSharing = "-1";
                                    String mPMF = Settings.Secure.getInt(context.getContentResolver(), "wifi_ap_pmf_checked", 0) != 1 ? "pmf_on" : "pmf_off";
                                    String mPowerSaveMode = Settings.Secure.getInt(context.getContentResolver(), "wifi_ap_powersave_mode_checked", 0) != 1 ? "power_save_mode_on" : "power_save_mode_off";
                                    int mMclient = Settings.Secure.getInt(SemWifiApBroadcastReceiver.this.mContext.getContentResolver(), "wifi_ap_max_client_number", 0);
                                    mWifiConfig = wm.getWifiApConfiguration();
                                    if (mWifiConfig == null && mWifiConfig.SSID != null) {
                                        String allowedDevice = "All";
                                        if (mWifiConfig.macaddrAcl != 3) {
                                            allowedDevice = "Only";
                                        }
                                        if (mWifiConfig.SSID.startsWith("Android") || mWifiConfig.SSID.startsWith("Verizon") || mWifiConfig.SSID.startsWith("Samsung") || mWifiConfig.SSID.startsWith("Galaxy") || mWifiConfig.SSID.startsWith("SM-")) {
                                            nameOfHotspot = "DefaultSSID";
                                        } else {
                                            nameOfHotspot = "CustomSSID";
                                        }
                                        SemWifiApBroadcastReceiver.this.callSecBigdataApi("MHSI", mIface + " " + nameOfHotspot + " " + mWifiConfig.hiddenSSID + " " + mWifiConfig.apChannel + " " + allowedDevice + " " + mMclient + " " + mWifiSharing + " " + mTimeoutSetting + " " + mPMF + " " + mPowerSaveMode);
                                    }
                                }
                            }
                            if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing()) {
                                mWifiSharing = Settings.Secure.getInt(context.getContentResolver(), "wifi_ap_wifi_sharing", 0) == 1 ? "sharing_on" : "sharing_off";
                            } else {
                                mWifiSharing = "not_support";
                            }
                            if (Settings.Secure.getInt(context.getContentResolver(), "wifi_ap_pmf_checked", 0) != 1) {
                            }
                            if (Settings.Secure.getInt(context.getContentResolver(), "wifi_ap_powersave_mode_checked", 0) != 1) {
                            }
                            int mMclient2 = Settings.Secure.getInt(SemWifiApBroadcastReceiver.this.mContext.getContentResolver(), "wifi_ap_max_client_number", 0);
                            mWifiConfig = wm.getWifiApConfiguration();
                            if (mWifiConfig == null) {
                            }
                        }
                        SemWifiApBroadcastReceiver.this.callInitBigdataVariables();
                        action2 = action3;
                        break;
                    case 12:
                    default:
                        action2 = action3;
                        Log.d(SemWifiApBroadcastReceiver.TAG, "unhandled apState : " + apState);
                        break;
                    case 13:
                        try {
                            if (SemWifiApBroadcastReceiver.mBaseTxBytes == 0) {
                                SemWifiApBroadcastReceiver.this.resetParameterForHotspotLogging();
                                System.currentTimeMillis();
                                SemWifiApBroadcastReceiver.this.mTimeOfStartMobileAp = System.currentTimeMillis();
                                long unused = SemWifiApBroadcastReceiver.mBaseTxBytes = TrafficStats.getTxBytes(mApIface);
                                SemWifiApBroadcastReceiver.this.mBaseRxBytes = TrafficStats.getRxBytes(mApIface);
                                if (SemWifiApBroadcastReceiver.this.mTelephonyManagerForHotspot == null) {
                                    SemWifiApBroadcastReceiver semWifiApBroadcastReceiver2 = SemWifiApBroadcastReceiver.this;
                                    Context context3 = SemWifiApBroadcastReceiver.this.mContext;
                                    Context unused2 = SemWifiApBroadcastReceiver.this.mContext;
                                    semWifiApBroadcastReceiver2.mTelephonyManagerForHotspot = (TelephonyManager) context3.getSystemService("phone");
                                }
                                SemWifiApBroadcastReceiver.this.mTelephonyPhoneStateListener = new PhoneStateListener() {
                                    /* class com.samsung.android.server.wifi.softap.SemWifiApBroadcastReceiver.C07911.C07921 */

                                    public void onDataConnectionStateChanged(int state, int networktype) {
                                        String mIface;
                                        if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing()) {
                                            mIface = "swlan0";
                                        } else {
                                            mIface = "wlan0";
                                        }
                                        Log.d(SemWifiApBroadcastReceiver.TAG, "onDataConnectionStateChanged state : " + state + ", networktype : " + networktype);
                                        if (state == 2) {
                                            SemWifiApBroadcastReceiver.this.mTempMobileTxBytes = TrafficStats.getTxBytes(mIface);
                                            SemWifiApBroadcastReceiver.this.mTempMobileRxBytes = TrafficStats.getRxBytes(mIface);
                                            SemWifiApBroadcastReceiver.this.mStartTimeOfMobileData = System.currentTimeMillis();
                                            SemWifiApBroadcastReceiver.this.bUseMobileData = true;
                                            SemWifiApBroadcastReceiver.this.bIsFirstCall = true;
                                        } else if (state != 0) {
                                        } else {
                                            if (SemWifiApBroadcastReceiver.this.bIsFirstCall) {
                                                long tempAmountTimeOfMobileData = SemWifiApBroadcastReceiver.this.mAmountTimeOfMobileData;
                                                long tempAmountTx = SemWifiApBroadcastReceiver.this.mAmountMobileTxBytes;
                                                long tempAmountRx = SemWifiApBroadcastReceiver.this.mAmountMobileRxBytes;
                                                SemWifiApBroadcastReceiver.this.mAmountMobileTxBytes = tempAmountTx + (TrafficStats.getTxBytes(mIface) - SemWifiApBroadcastReceiver.this.mTempMobileTxBytes);
                                                SemWifiApBroadcastReceiver.this.mAmountMobileRxBytes = tempAmountRx + (TrafficStats.getRxBytes(mIface) - SemWifiApBroadcastReceiver.this.mTempMobileRxBytes);
                                                SemWifiApBroadcastReceiver.this.mAmountTimeOfMobileData = tempAmountTimeOfMobileData + ((System.currentTimeMillis() - SemWifiApBroadcastReceiver.this.mStartTimeOfMobileData) / 60000);
                                                SemWifiApBroadcastReceiver.this.bUseMobileData = false;
                                                SemWifiApBroadcastReceiver.this.bIsFirstCall = false;
                                            }
                                        }
                                    }
                                };
                                SemWifiApBroadcastReceiver.this.mTelephonyManagerForHotspot.listen(SemWifiApBroadcastReceiver.this.mTelephonyPhoneStateListener, 64);
                            }
                            action2 = action3;
                            break;
                        } catch (Exception e12) {
                            Log.e(SemWifiApBroadcastReceiver.TAG, "Error in getting wlan0 interface config:" + e12);
                            action2 = action3;
                            break;
                        }
                    case 14:
                        SemWifiApBroadcastReceiver.this.setRvfMode(context, 0);
                        action2 = action3;
                        break;
                }
                return;
            } else if ("com.samsung.android.net.wifi.WIFI_AP_STA_STATUS_CHANGED".equals(action3)) {
                int ClientNum = intent.getIntExtra("NUM", 0);
                int mMaxCli = Settings.Secure.getInt(SemWifiApBroadcastReceiver.this.mContext.getContentResolver(), "wifi_ap_max_client_number", 0);
                Log.i(SemWifiApBroadcastReceiver.TAG, "ClientNum from WIFI_AP_STA_STATUS_CHANGED_ACTION = " + ClientNum);
                if (ClientNum < 0) {
                    ClientNum = 0;
                }
                if (ClientNum > mMaxCli) {
                    Settings.Secure.putInt(SemWifiApBroadcastReceiver.this.mContext.getContentResolver(), "wifi_ap_max_client_number", ClientNum);
                }
                return;
            } else if (!action3.equals("android.intent.action.SIM_STATE_CHANGED")) {
                return;
            } else {
                if (!SystemProperties.get("SimCheck.disable").equals("1") && (mWifiManager = (WifiManager) context.getSystemService("wifi")) != null) {
                    int wifiApState = mWifiManager.getWifiApState();
                    String state = intent.getStringExtra(SemWifiApBroadcastReceiver.INTENT_KEY_ICC_STATE);
                    Log.i(SemWifiApBroadcastReceiver.TAG, " INTENT_KEY_ICC_STATE state : " + state);
                    if ("ABSENT".equals(state)) {
                        if (wifiApState != 11) {
                            Log.i(SemWifiApBroadcastReceiver.TAG, "INTENT_VALUE_ICC_ABSENT received, disable wifi hotspot");
                            mWifiManager.semSetWifiApEnabled(null, false);
                            return;
                        }
                        return;
                    } else if ("LOADED".equals(state) && SemWifiApBroadcastReceiver.mHotspotActionForSimStatus != null && SemWifiApBroadcastReceiver.mHotspotActionForSimStatus.equals("turn off") && wifiApState != 11) {
                        Log.e(SemWifiApBroadcastReceiver.TAG, "INTENT_VALUE_ICC_LOADED received, disable wifi hotspot");
                        mWifiManager.semSetWifiApEnabled(null, false);
                        return;
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            }
            String salesCode = SystemProperties.get("ro.csc.sales_code");
            if ((!"SPRINT".equals(SemWifiApBroadcastReceiver.CONFIGOPBRANDINGFORMOBILEAP) && !"SPRINT".equals(salesCode) && !"SPR".equals(salesCode) && !"XAS".equals(salesCode) && !"VMU".equals(salesCode) && !"BST".equals(salesCode)) || SemWifiApBroadcastReceiver.this.getRvfMode(context2) == 1) {
                return;
            }
            if ("ALL".equals(SemWifiApBroadcastReceiver.CONFIGOPBRANDINGFORMOBILEAP)) {
                WifiManager wm2 = (WifiManager) context2.getSystemService("wifi");
                int wifiApState2 = wm2.getWifiApState();
                if (wifiApState2 == 12 || wifiApState2 == 13) {
                    Log.i(str, "Mobile AP is disabled by [USA OPEN (SPR)] don't : " + wifiApState2);
                    wm2.semSetWifiApEnabled(null, false);
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException e13) {
                        Log.i(str, "Error InterruptedException " + e13);
                    }
                    if (!WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing() || (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing() && !SemWifiApBroadcastReceiver.this.isWifiSharingEnabled(context2))) {
                        int wifiSavedState = 0;
                        try {
                            wifiSavedState = Settings.Secure.getInt(context.getContentResolver(), "wifi_saved_state");
                        } catch (Settings.SettingNotFoundException e14) {
                            Log.i(str, "SettingNotFoundException");
                        }
                        if (wifiSavedState == 1) {
                            Log.d(str, "Need to enabled Wifi since provision dialog got dismissed in onPause");
                            wm2.setWifiEnabled(true);
                            Settings.Secure.putInt(context.getContentResolver(), "wifi_saved_state", 0);
                        }
                    }
                }
            } else if (action.equals(SemWifiApBroadcastReceiver.WIFIAP_MODEMNAI_MISSMATH)) {
                String mipErrorCode = intent2.getStringExtra("CODE");
                Log.i(str, "mipErrorCode : " + mipErrorCode);
                if (mipErrorCode != null && mipErrorCode.equals("67")) {
                    SemWifiApBroadcastReceiver.this.showHotspotErrorDialog(context2, 2, intent2);
                }
            } else if (action.equals(SemWifiApBroadcastReceiver.WIFIAP_TETHERING_DENIED)) {
                SemWifiApBroadcastReceiver.this.showHotspotErrorDialog(context2, 3, intent2);
            } else {
                Log.i(str, "do NOT turn off MHS when DIALOG_HOTSPOT_NO_DATA , spr new requirement!!!!");
            }
        }
    };
    private long mStartTimeOfMobileData;
    private TelephonyManager mTelephonyManagerForHotspot = null;
    private PhoneStateListener mTelephonyPhoneStateListener = null;
    private long mTempMobileRxBytes;
    private long mTempMobileTxBytes;
    private long mTimeOfStartMobileAp;

    public SemWifiApBroadcastReceiver(Context context) {
        this.mContext = context;
        this.mFilter = new IntentFilter();
        this.mFilter.addAction(WIFIAP_MODEMNAI_MISSMATH);
        this.mFilter.addAction(WIFIAP_TETHERING_DENIED);
        this.mFilter.addAction(START_PROVISIONING);
        this.mFilter.addAction(WIFIAP_TETHERING_FAILED);
        this.mFilter.addAction(AP_STA_24GHZ_DISCONNECTED);
        this.mFilter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        this.mFilter.addAction("com.nttdocomo.intent.action.SHOW_WPSDIALOG");
        this.mFilter.addAction(ADVANCED_WIFI_SHARING_NOTI);
        this.mFilter.addAction("android.intent.action.SIM_STATE_CHANGED");
        this.mFilter.addAction("com.samsung.android.net.wifi.WIFI_AP_STA_STATUS_CHANGED");
        Log.d(TAG, " SemWifiApBroadcastReceiver intialized");
    }

    public void startTracking() {
        if (!isRegistered) {
            this.mContext.registerReceiver(this.mReceiver, this.mFilter);
            Log.d(TAG, " SemWifiApBroadcastReceiver startTracking");
        }
    }

    public void stopTracking() {
        isRegistered = false;
        Log.d(TAG, " SemWifiApBroadcastReceiver stopTracking");
        callInitBigdataVariables();
        this.mContext.unregisterReceiver(this.mReceiver);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int getRvfMode(Context context) {
        Log.i(TAG, "getRvfMode");
        WifiManager mWifiManager = (WifiManager) context.getSystemService("wifi");
        if (mWifiManager == null) {
            return 0;
        }
        Message msg = new Message();
        msg.what = 28;
        return mWifiManager.callSECApi(msg);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isWifiSharingEnabled(Context context) {
        try {
            if (Settings.Secure.getInt(context.getContentResolver(), "wifi_ap_wifi_sharing") == 1) {
                Log.i(TAG, "Returning true");
                return true;
            }
            if (Settings.Secure.getInt(context.getContentResolver(), "wifi_ap_wifi_sharing") == 0) {
                Log.i(TAG, "Returning false");
                return false;
            }
            return false;
        } catch (Settings.SettingNotFoundException e1) {
            Log.i(TAG, "Error in getting provider value" + e1);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void startWifiApSettings(Context context) {
        Intent wifiApIntent = new Intent();
        wifiApIntent.setAction("com.samsung.settings.WIFI_AP_SETTINGS");
        wifiApIntent.setFlags(276824064);
        context.startActivity(wifiApIntent);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void showHotspotErrorDialog(Context context, int DialogType, Intent intent) {
        Log.i(TAG, "[showHotspotErrorDialog] DialogType : " + DialogType);
        WifiManager mWifiManager = (WifiManager) context.getSystemService("wifi");
        int extra_type = intent.getIntExtra("extra_type", -1);
        int req_type = intent.getIntExtra("req_type", -1);
        if (mWifiManager != null) {
            int wifiApState = mWifiManager.getWifiApState();
            if (DialogType == 4) {
                if (req_type == 0 && extra_type == 1 && WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing() && isWifiSharingEnabled(context)) {
                    return;
                }
                if (!(wifiApState == 12 || wifiApState == 13 || extra_type + req_type == 3 || extra_type == 4)) {
                    return;
                }
            } else if (DialogType == 5) {
                if (wifiApState == 12 || wifiApState == 13) {
                    return;
                }
            } else if (DialogType != 50 || !WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing() || !isWifiSharingEnabled(context)) {
                if (DialogType == 51) {
                    if (!(wifiApState == 12 || wifiApState == 13)) {
                        Log.i(TAG, "Wifi AP is not enabled during DIALOG_HOTSPOT_24GHZ_AP_STA_DISCONNECT");
                        return;
                    }
                } else if (wifiApState == 12 || wifiApState == 13) {
                    Log.i(TAG, "Mobile AP is disabled by [showHotspotErrorDialog] : " + wifiApState);
                    mWifiManager.semSetWifiApEnabled(null, false);
                } else {
                    return;
                }
            } else if (!(wifiApState == 12 || wifiApState == 13)) {
                Log.i(TAG, "Wifi AP is not enabled");
                return;
            }
            StatusBarManager statusBar = (StatusBarManager) context.getSystemService("statusbar");
            if (statusBar != null) {
                statusBar.collapsePanels();
            }
            Intent startDialogIntent = new Intent();
            startDialogIntent.setClassName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApWarning");
            startDialogIntent.setFlags(268435456);
            startDialogIntent.setAction(WIFIAP_WARNING_DIALOG);
            startDialogIntent.putExtra(WIFIAP_WARNING_DIALOG_TYPE, DialogType);
            startDialogIntent.putExtra("req_type", req_type);
            startDialogIntent.putExtra("extra_type", extra_type);
            context.startActivity(startDialogIntent);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isProvisioningNeeded(Context context) {
        String[] mProvisionApp = context.getResources().getStringArray(17236154);
        if ("ATT".equals(CONFIGOPBRANDINGFORMOBILEAP) || "VZW".equals(CONFIGOPBRANDINGFORMOBILEAP) || "TMO".equals(CONFIGOPBRANDINGFORMOBILEAP) || "NEWCO".equals(CONFIGOPBRANDINGFORMOBILEAP)) {
            String tetheringProvisionApp = SemCscFeature.getInstance().getString(CscFeatureTagSetting.TAG_CSCFEATURE_SETTING_CONFIGMOBILEHOTSPOTPROVISIONAPP);
            if (SystemProperties.getBoolean("net.tethering.noprovisioning", false) || mProvisionApp == null || mProvisionApp.length != 2 || TextUtils.isEmpty(tetheringProvisionApp)) {
                return false;
            }
        }
        if (isWifiSharingEnabled(context)) {
            if (isWifiConnected(context)) {
                Log.d(TAG, "Wifi is connected so skip provisioning for Wifi Sharing");
                return false;
            }
            Log.d(TAG, "Wifi is not connected so dont skip provisioning for Wifi Sharing");
        }
        if (mProvisionApp.length == 2) {
            return true;
        }
        return false;
    }

    private boolean isWifiConnected(Context context) {
        boolean isWifiConnected = false;
        NetworkInfo activeNetworkInfo = ((ConnectivityManager) context.getSystemService("connectivity")).getActiveNetworkInfo();
        if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
            boolean z = true;
            if (activeNetworkInfo.getType() != 1) {
                z = false;
            }
            isWifiConnected = z;
        }
        Log.d(TAG, "isWifiConnected : " + isWifiConnected);
        return isWifiConnected;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void startHotspotProvisioningRequestWifiSharing(Context context, int DialogType) {
        Log.d(TAG, "startHotspotProvisioningRequest for Wifi Sharing");
        Intent startDialogIntent = new Intent();
        startDialogIntent.setClassName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApWarning");
        startDialogIntent.setFlags(268435456);
        startDialogIntent.setAction(WIFIAP_WARNING_DIALOG);
        startDialogIntent.putExtra(WIFIAP_WARNING_DIALOG_TYPE, DialogType);
        startDialogIntent.putExtra(WIFIAP_PROVISION_DIALOG_TYPE, DialogType);
        context.startActivity(startDialogIntent);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void callSecBigdataApi(String feature, String data) {
        Message msg = new Message();
        msg.what = 77;
        Bundle args = new Bundle();
        args.putBoolean("bigdata", true);
        args.putString("feature", feature);
        Log.d(TAG, "Bigdata logging " + data);
        args.putString("data", data);
        msg.obj = args;
        ((WifiManager) this.mContext.getSystemService("wifi")).callSECApi(msg);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void resetParameterForHotspotLogging() {
        this.mTelephonyPhoneStateListener = null;
        this.mAmountMobileTxBytes = 0;
        this.mAmountMobileRxBytes = 0;
        this.mTempMobileRxBytes = 0;
        this.mTempMobileTxBytes = 0;
        this.mAmountTimeOfMobileData = 0;
        this.mTempMobileTxBytes = 0;
        this.mTempMobileRxBytes = 0;
        this.bIsFirstCall = false;
        mBaseTxBytes = 0;
        this.mBaseRxBytes = 0;
        this.mTelephonyManagerForHotspot = null;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void callInitBigdataVariables() {
        Log.d(TAG, "callInitBigdataVariables() ");
        Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_ap_max_client_number", 0);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String convertBytesToMegaByte(long tempValue) {
        long valueOfDevided = tempValue / 1048576;
        if (valueOfDevided >= ((long) 500)) {
            return "over" + 500 + "MB";
        } else if (((double) valueOfDevided) >= ((double) 500) * 0.9d) {
            return (((double) 500) * 0.9d) + "~" + 500 + "MB";
        } else if (((double) valueOfDevided) >= ((double) 500) * 0.8d) {
            return (((double) 500) * 0.8d) + "~" + (((double) 500) * 0.9d) + "MB";
        } else if (((double) valueOfDevided) >= ((double) 500) * 0.7d) {
            return (((double) 500) * 0.7d) + "~" + (((double) 500) * 0.8d) + "MB";
        } else if (((double) valueOfDevided) >= ((double) 500) * 0.6d) {
            return (((double) 500) * 0.6d) + "~" + (((double) 500) * 0.7d) + "MB";
        } else if (((double) valueOfDevided) >= ((double) 500) * 0.5d) {
            return (((double) 500) * 0.5d) + "~" + (((double) 500) * 0.6d) + "MB";
        } else if (((double) valueOfDevided) >= ((double) 500) * 0.4d) {
            return (((double) 500) * 0.4d) + "~" + (((double) 500) * 0.5d) + "MB";
        } else if (((double) valueOfDevided) >= ((double) 500) * 0.3d) {
            return (((double) 500) * 0.3d) + "~" + (((double) 500) * 0.4d) + "MB";
        } else if (((double) valueOfDevided) >= ((double) 500) * 0.2d) {
            return (((double) 500) * 0.2d) + "~" + (((double) 500) * 0.3d) + "MB";
        } else if (((double) valueOfDevided) >= ((double) 500) * 0.1d) {
            return (((double) 500) * 0.1d) + "~" + (((double) 500) * 0.2d) + "MB";
        } else {
            return "0~" + (((double) 500) * 0.1d) + "MB";
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String convertBytesToMegaByteForLogging(long tempValue) {
        return "" + (tempValue / 1048576);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String convertMinute(long tempValue) {
        if (tempValue >= 120) {
            return (tempValue / 60) + "hour";
        } else if (tempValue >= 100) {
            return "100~120";
        } else {
            if (tempValue >= 80) {
                return "80~100";
            }
            if (tempValue >= 60) {
                return "60~80";
            }
            if (tempValue >= 40) {
                return "40~60";
            }
            if (tempValue >= 20) {
                return "20~40";
            }
            return "0~20";
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setRvfMode(Context context, int mode) {
        Message msg = new Message();
        msg.what = 27;
        Bundle b = new Bundle();
        b.putInt("mode", mode);
        msg.obj = b;
        ((WifiManager) context.getSystemService("wifi")).callSECApi(msg);
    }

    /* access modifiers changed from: package-private */
    public void showLimitDataReachNotification(Context context) {
        String title = context.getResources().getString(17042609);
        String message = context.getResources().getString(17040046);
        Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.wifi.mobileap.WifiApSettings");
        intent.setFlags(335544320);
        PendingIntent pi = PendingIntent.getActivityAsUser(context, 0, intent, 0, null, UserHandle.CURRENT);
        Notification.Builder mNotiBuilder = new Notification.Builder(context);
        mNotiBuilder.setWhen(0).setOngoing(false).setAutoCancel(true).setColor(context.getColor(17170460)).setVisibility(1).setCategory("status").setSmallIcon(17304239).setContentTitle(title).setContentText(message).setContentIntent(pi);
        if (this.mNotificationManager == null) {
            this.mNotificationManager = (NotificationManager) context.getSystemService("notification");
        }
        this.mNotificationManager.notifyAsUser(null, DATA_REACH, mNotiBuilder.build(), UserHandle.CURRENT);
    }

    /* access modifiers changed from: package-private */
    public void clearLimitDataReachNotification(Context context) {
        if (this.mNotificationManager == null) {
            this.mNotificationManager = (NotificationManager) context.getSystemService("notification");
        }
        this.mNotificationManager.cancelAsUser(null, DATA_REACH, UserHandle.ALL);
    }

    /* access modifiers changed from: package-private */
    public void showWifiScanListNotification(Context context, String message) {
        String title = context.getResources().getString(17042593);
        NotificationChannel mChannel = new NotificationChannel("wifi_sharing_channel", title, 4);
        mChannel.setDescription(title);
        mChannel.enableLights(true);
        mChannel.setLightColor(-65536);
        mChannel.enableVibration(true);
        Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.wifi.WifiSettings");
        intent.setFlags(335544320);
        PendingIntent pi = PendingIntent.getActivityAsUser(context, 0, intent, 0, null, UserHandle.CURRENT);
        Intent advWSIntent = new Intent(ADVANCED_WIFI_SHARING_NOTI);
        advWSIntent.setPackage("com.android.settings");
        advWSIntent.putExtra("NOTIFICATION_TASK", 0);
        PendingIntent piDismiss = PendingIntent.getBroadcast(context, 0, advWSIntent, 0);
        Intent wifiSettingsIntent = new Intent();
        wifiSettingsIntent.setPackage("com.android.settings");
        wifiSettingsIntent.setClassName("com.android.settings", "com.android.settings.wifi.WifiSettings");
        wifiSettingsIntent.setFlags(335544320);
        PendingIntent piWifiSettings = PendingIntent.getActivity(context, 0, wifiSettingsIntent, 0);
        Notification.Builder mNotiBuilder = new Notification.Builder(context);
        mNotiBuilder.setWhen(0).setOngoing(false).setAutoCancel(true).setColor(context.getColor(17170460)).setVisibility(1).setCategory("status").setSmallIcon(17301642).setContentTitle(title).setContentText(message).setChannelId("wifi_sharing_channel").setPriority(2).addAction(17301642, context.getResources().getString(17039360), piDismiss).addAction(17301642, context.getResources().getString(17039767), piWifiSettings).setStyle(new Notification.BigTextStyle().bigText(message)).setTimeoutAfter(20000).setContentIntent(pi);
        if (this.mNotificationManager == null) {
            this.mNotificationManager = (NotificationManager) context.getSystemService("notification");
        }
        this.mNotificationManager.createNotificationChannel(mChannel);
        this.mNotificationManager.notifyAsUser(null, 17042593, mNotiBuilder.build(), UserHandle.CURRENT);
        Log.d(TAG, "showWifiScanListNotification");
    }

    /* access modifiers changed from: package-private */
    public void clearWifiScanListNotification(Context context) {
        if (this.mNotificationManager == null) {
            this.mNotificationManager = (NotificationManager) context.getSystemService("notification");
        }
        this.mNotificationManager.cancelAsUser(null, 17042593, UserHandle.ALL);
        Log.d(TAG, "clearWifiScanListNotification");
    }
}
