package com.samsung.android.server.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Log;
import com.android.server.wifi.ClientModeImpl;
import com.android.server.wifi.Clock;
import com.android.server.wifi.WifiInjector;

public final class SwitchBoardService {
    private static final boolean DBG = true;
    private static final int EVENT_BOOT_COMPLETED = 3;
    private static final int EVENT_ENABLE_DEBUG = 2;
    private static final int EVENT_ENABLE_OR_DISABLE_SWITCHBOARD = 1;
    private static final int EVENT_GET_WIFIINFO_POLL = 4;
    private static final int INVALID_RSSI = -127;
    private static final String LOG_TAG = SwitchBoardService.class.getSimpleName();
    private static final int NORMAL_WIFI_POLLING_INTERVAL = 3000;
    private static final String SWITCHBOARD_INTENT_ENABLE_DEBUG = "com.samsung.android.SwitchBoard.ENABLE_DEBUG";
    private static final String SWITCHBOARD_INTENT_START = "com.samsung.android.SwitchBoard.START";
    private static final String SWITCHBOARD_INTENT_STATE = "com.samsung.android.SwitchBoard.STATE";
    private static final String SWITCHBOARD_INTENT_STOP = "com.samsung.android.SwitchBoard.STOP";
    private static final String SWITCHBOARD_INTENT_SWITCH_INTERVAL = "com.samsung.android.SwitchBoard.SWITCH_INTERVAL";
    private static final String SWITCHBOARD_INTENT_WIFI_PREFERENCE_VALUE = "com.samsung.android.SwitchBoard.WIFI_PREFERENCE_VALUE";
    private static final String SWITCHBOARD_STATE = "switchboard_state";
    private static final int SWITCHBOARD_WIFI_POLLING_INTERVAL = 1000;
    private static boolean VDBG = false;
    private static volatile SwitchBoardService mInstance = null;
    private static final boolean mIsEngBuild = "eng".equals(SystemProperties.get("ro.build.type"));
    private static final boolean mIsShipBuild = "true".equals(SystemProperties.get("ro.product_ship"));
    private final Clock mClock;
    private ClientModeImpl mCmi;
    private Context mContext;
    private SwitchBoardHandler mHandler;
    private boolean mIsBootCompleted = false;
    private boolean mIsEnableRequestBeforeBootComplete = false;
    private int mIsSwitchBoardPerferDataPathWifi = 1;
    private long mLastUpdatedTimeMs = 0;
    private int mLteToWifiDelayMs = 5000;
    private int mOldWifiRssi;
    private long mOldWifiTxBad;
    private long mOldWifiTxRetries;
    private long mOldWifiTxSuccess;
    private boolean mSwitchBoardEnabled = false;
    private String mWifiIface = SystemProperties.get("wifi.interface");
    private WifiInfo mWifiInfo;
    private boolean mWifiInfoPollingEnabled = false;
    private boolean mWifiInfoPollingEnabledAlways = false;
    private int mWifiInfoPollingInterval = -1;
    private int mWifiToLteDelayMs = 0;

    public static SwitchBoardService getInstance(Context ctx, Looper looper, ClientModeImpl clientmodeimpl) {
        if (mInstance == null) {
            synchronized (SwitchBoardService.class) {
                if (mInstance == null) {
                    mInstance = new SwitchBoardService(ctx, looper, clientmodeimpl);
                }
            }
        }
        return mInstance;
    }

    private SwitchBoardService(Context context, Looper looper, ClientModeImpl Cmi) {
        this.mContext = context;
        this.mCmi = Cmi;
        this.mWifiInfo = this.mCmi.getWifiInfo();
        this.mClock = WifiInjector.getInstance().getClock();
        if (looper != null) {
            this.mHandler = new SwitchBoardHandler(looper);
            IntentFilter switchboardIntentFilter = new IntentFilter();
            switchboardIntentFilter.addAction(SWITCHBOARD_INTENT_START);
            switchboardIntentFilter.addAction(SWITCHBOARD_INTENT_STOP);
            switchboardIntentFilter.addAction(SWITCHBOARD_INTENT_ENABLE_DEBUG);
            switchboardIntentFilter.addAction(SWITCHBOARD_INTENT_SWITCH_INTERVAL);
            switchboardIntentFilter.addAction("android.intent.action.BOOT_COMPLETED");
            this.mContext.registerReceiver(new SwitchBoardReceiver(), switchboardIntentFilter);
            return;
        }
        loge("handlerThread.getLooper() returned null");
    }

    class SwitchBoardReceiver extends BroadcastReceiver {
        SwitchBoardReceiver() {
        }

        /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
        public void onReceive(Context context, Intent intent) {
            char c;
            String action = intent.getAction();
            switch (action.hashCode()) {
                case -1897205914:
                    if (action.equals(SwitchBoardService.SWITCHBOARD_INTENT_START)) {
                        c = 0;
                        break;
                    }
                    c = 65535;
                    break;
                case -1727841388:
                    if (action.equals(SwitchBoardService.SWITCHBOARD_INTENT_SWITCH_INTERVAL)) {
                        c = 3;
                        break;
                    }
                    c = 65535;
                    break;
                case -837322541:
                    if (action.equals(SwitchBoardService.SWITCHBOARD_INTENT_ENABLE_DEBUG)) {
                        c = 2;
                        break;
                    }
                    c = 65535;
                    break;
                case -615389090:
                    if (action.equals(SwitchBoardService.SWITCHBOARD_INTENT_STOP)) {
                        c = 1;
                        break;
                    }
                    c = 65535;
                    break;
                case 798292259:
                    if (action.equals("android.intent.action.BOOT_COMPLETED")) {
                        c = 4;
                        break;
                    }
                    c = 65535;
                    break;
                default:
                    c = 65535;
                    break;
            }
            if (c == 0) {
                SwitchBoardService.logd("SwitchBoardReceiver.onReceive: action=" + action + "AlwaysPolling" + intent.getBooleanExtra("AlwaysPolling", false));
                SwitchBoardService.this.mWifiInfoPollingEnabledAlways = intent.getBooleanExtra("AlwaysPolling", false);
                SwitchBoardService.this.mHandler.sendMessage(SwitchBoardService.this.mHandler.obtainMessage(1, true));
            } else if (c == 1) {
                SwitchBoardService.logd("SwitchBoardReceiver.onReceive: action=" + action);
                SwitchBoardService.this.mHandler.sendMessage(SwitchBoardService.this.mHandler.obtainMessage(1, false));
            } else if (c == 2) {
                SwitchBoardService.this.mHandler.sendMessage(SwitchBoardService.this.mHandler.obtainMessage(2, Boolean.valueOf(intent.getBooleanExtra("DEBUG", false))));
            } else if (c == 3) {
                SwitchBoardService.this.mWifiToLteDelayMs = intent.getIntExtra("WifiToLteDelayMs", 0);
                SwitchBoardService.this.mLteToWifiDelayMs = intent.getIntExtra("LteToWifiDelayMs", 5000);
                SwitchBoardService.logd("SwitchBoardReceiver.onReceive: action=" + action + ", mWifiToLteDelayMs: " + SwitchBoardService.this.mWifiToLteDelayMs + ", mLteToWifiDelayMs: " + SwitchBoardService.this.mLteToWifiDelayMs);
            } else if (c != 4) {
                SwitchBoardService.logd("SwitchBoardReceiver.onReceive: undefined case: action=" + action);
            } else {
                SwitchBoardService.logv("SwitchBoardReceiver.onReceive: action=" + action);
                SwitchBoardService.this.mHandler.sendMessage(SwitchBoardService.this.mHandler.obtainMessage(3));
            }
        }
    }

    /* access modifiers changed from: private */
    public class SwitchBoardHandler extends Handler {
        SwitchBoardHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i == 1) {
                boolean enable = ((Boolean) msg.obj).booleanValue();
                SwitchBoardService.logv("EVENT_ENABLE_OR_DISABLE_SWITCHBOARD: " + enable);
                SwitchBoardService.this.setSwitchBoardState(enable, "AppsRequest");
            } else if (i == 2) {
                boolean unused = SwitchBoardService.VDBG = ((Boolean) msg.obj).booleanValue();
                SwitchBoardService.logv("EVENT_ENABLE_DEBUG: VDBG=" + SwitchBoardService.VDBG);
            } else if (i == 3) {
                SwitchBoardService.logd("EVENT_BOOT_COMPLETED");
                SwitchBoardService.this.mIsBootCompleted = true;
                if (SwitchBoardService.this.mIsEnableRequestBeforeBootComplete) {
                    SwitchBoardService switchBoardService = SwitchBoardService.this;
                    switchBoardService.setSwitchBoardState(switchBoardService.mIsEnableRequestBeforeBootComplete, "EnableRequestBeforeBootComplete");
                }
            } else if (i != 4) {
                SwitchBoardService.logd("SwitchBoardHandler.handleMessage: undefined case: msg=" + msg.what);
            } else {
                SwitchBoardService.logv("EVENT_GET_WIFIINFO_POLL");
                SwitchBoardService.this.determineDataPathPriority();
                if (SwitchBoardService.this.mWifiInfoPollingEnabled) {
                    SwitchBoardService.this.mHandler.sendMessageDelayed(SwitchBoardService.this.mHandler.obtainMessage(4), (long) SwitchBoardService.this.mWifiInfoPollingInterval);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setSwitchBoardState(boolean enable, String reason) {
        if (!this.mIsBootCompleted) {
            logd("setSwitchBoardState: pending a request before boot completed [enable = " + enable + "]");
            this.mIsEnableRequestBeforeBootComplete = enable;
            return;
        }
        logd("setSwitchBoardState: add a new request [enable=" + enable + ", reason=" + reason + ", Always Polling=" + this.mWifiInfoPollingEnabledAlways + "]");
        if (enable) {
            this.mLastUpdatedTimeMs = this.mClock.getWallClockMillis();
            enablePollingRssiForSwitchboard(this.mWifiInfoPollingEnabledAlways, 1000);
            enableWifiInfoPolling(true);
            setSBInternalState(true);
        } else {
            enablePollingRssiForSwitchboard(false, 3000);
            enableWifiInfoPolling(false);
            setSBInternalState(false);
        }
        broadcastSBStatus(getSBInternalState());
    }

    private void broadcastSBStatus(boolean state) {
        Intent intent = new Intent();
        intent.setAction(SWITCHBOARD_INTENT_STATE);
        intent.putExtra(SWITCHBOARD_STATE, state);
        logi("broadcastSBStatus: SwitchBoard state changed(" + state + "), so send broadcast=" + intent);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
    }

    private boolean getSBInternalState() {
        return this.mSwitchBoardEnabled;
    }

    private void setSBInternalState(boolean enable) {
        logv("setSBInternalState(" + enable + ")");
        this.mSwitchBoardEnabled = enable;
    }

    private void enablePollingRssiForSwitchboard(boolean enable, int interval) {
        this.mWifiInfoPollingInterval = interval;
        this.mCmi.enablePollingRssiForSwitchboard(enable, this.mWifiInfoPollingInterval);
    }

    private void enableWifiInfoPolling(boolean enable) {
        if (enable != this.mWifiInfoPollingEnabled) {
            this.mWifiInfoPollingEnabled = enable;
            if (this.mWifiInfoPollingEnabled) {
                SwitchBoardHandler switchBoardHandler = this.mHandler;
                switchBoardHandler.sendMessageDelayed(switchBoardHandler.obtainMessage(4), (long) this.mWifiInfoPollingInterval);
                return;
            }
            this.mHandler.removeMessages(4);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void determineDataPathPriority() {
        long calculatedTxBad;
        if (this.mCmi.isConnected() && this.mWifiInfo.getRssi() != -127 && this.mOldWifiRssi != -127) {
            logv("determineSubflowPriority: mIsSwitchBoardPerferDataPathWifi =" + this.mIsSwitchBoardPerferDataPathWifi);
            long calculatedTxBad2 = this.mWifiInfo.txBad - this.mOldWifiTxBad;
            long calculatedTxRetriesRate = 0;
            long txFrames = (this.mWifiInfo.txSuccess + this.mWifiInfo.txBad) - (this.mOldWifiTxSuccess + this.mOldWifiTxBad);
            if (txFrames > 0) {
                calculatedTxRetriesRate = (this.mWifiInfo.txRetries - this.mOldWifiTxRetries) / txFrames;
            }
            logv("wifiMetric New [" + String.format("%4d, ", Integer.valueOf(this.mWifiInfo.getRssi())) + String.format("%4d, ", Long.valueOf(this.mWifiInfo.txRetries)) + String.format("%4d, ", Long.valueOf(this.mWifiInfo.txSuccess)) + String.format("%4d, ", Long.valueOf(this.mWifiInfo.txBad)) + "]");
            logv("wifiMetric Old [" + String.format("%4d, ", Integer.valueOf(this.mOldWifiRssi)) + String.format("%4d, ", Long.valueOf(this.mOldWifiTxRetries)) + String.format("%4d, ", Long.valueOf(this.mOldWifiTxSuccess)) + String.format("%4d, ", Long.valueOf(this.mOldWifiTxBad)) + "]");
            logv("wifiMetric [" + String.format("RSSI: %4d, ", Integer.valueOf(this.mWifiInfo.getRssi())) + String.format("Retry: %4d, ", Long.valueOf(this.mWifiInfo.txRetries - this.mOldWifiTxRetries)) + String.format("TXGood: %4d, ", Long.valueOf(txFrames)) + String.format("TXBad: %4d, ", Long.valueOf(calculatedTxBad2)) + String.format("RetryRate%4d", Long.valueOf(calculatedTxRetriesRate)) + "]");
            int i = this.mIsSwitchBoardPerferDataPathWifi;
            if (i == 1) {
                if (calculatedTxRetriesRate <= 1 && calculatedTxBad2 <= 2) {
                    calculatedTxBad = calculatedTxBad2;
                } else if (this.mWifiInfo.getRssi() >= -70 || this.mOldWifiRssi >= -70) {
                    calculatedTxBad = calculatedTxBad2;
                } else {
                    logd("Case0, triggered - txRetriesRate(" + calculatedTxRetriesRate + "), txBad(" + calculatedTxBad2 + ")");
                    setWifiDataPathPriority(0);
                }
                if (this.mWifiInfo.getRssi() < -85 && this.mOldWifiRssi < -85) {
                    logd("Case1, triggered");
                    setWifiDataPathPriority(0);
                }
            } else if (i == 0) {
                if (txFrames > 0 && calculatedTxRetriesRate < 1 && calculatedTxBad2 < 1 && this.mWifiInfo.getRssi() > -75 && this.mOldWifiRssi > -75) {
                    logd("Case2, triggered - txRetriesRate(" + calculatedTxRetriesRate + "), txBad(" + calculatedTxBad2 + ")");
                    setWifiDataPathPriority(1);
                } else if (txFrames > 0 && calculatedTxRetriesRate < 2 && calculatedTxBad2 < 1 && this.mWifiInfo.getRssi() > -70 && this.mOldWifiRssi > -70) {
                    logd("Case3, triggered - txRetriesRate(" + calculatedTxRetriesRate + "), txBad(" + calculatedTxBad2 + ")");
                    setWifiDataPathPriority(1);
                } else if (this.mWifiInfo.getRssi() > -60 && this.mOldWifiRssi > -60) {
                    logd("Case4, triggered RSSI is higher than -60dBm");
                    setWifiDataPathPriority(1);
                }
            }
            this.mOldWifiRssi = this.mWifiInfo.getRssi();
            this.mOldWifiTxSuccess = this.mWifiInfo.txSuccess;
            this.mOldWifiTxBad = this.mWifiInfo.txBad;
            this.mOldWifiTxRetries = this.mWifiInfo.txRetries;
        } else if (this.mCmi.isConnected() && (this.mWifiInfo.getRssi() != -127 || this.mOldWifiRssi != -127)) {
            this.mOldWifiRssi = this.mWifiInfo.getRssi();
            this.mOldWifiTxSuccess = this.mWifiInfo.txSuccess;
            this.mOldWifiTxBad = this.mWifiInfo.txBad;
            this.mOldWifiTxRetries = this.mWifiInfo.txRetries;
        } else if (!this.mCmi.isConnected() && this.mOldWifiRssi != -127) {
            this.mOldWifiRssi = -127;
            this.mOldWifiTxSuccess = 0;
            this.mOldWifiTxBad = 0;
            this.mOldWifiTxRetries = 0;
        }
    }

    private void setWifiDataPathPriority(int preferValue) {
        long timeStamp = this.mClock.getWallClockMillis();
        if ((preferValue != 1 || timeStamp - this.mLastUpdatedTimeMs < ((long) this.mLteToWifiDelayMs)) && (preferValue != 0 || timeStamp - this.mLastUpdatedTimeMs < ((long) this.mWifiToLteDelayMs))) {
            logd("setWifiDataPathPriority: , mIsSwitchBoardPerferDataPathWifi: " + this.mIsSwitchBoardPerferDataPathWifi + "dalayed");
            return;
        }
        this.mLastUpdatedTimeMs = timeStamp;
        this.mIsSwitchBoardPerferDataPathWifi = preferValue;
        logd("setWifiDataPathPriority: , mIsSwitchBoardPerferDataPathWifi: " + this.mIsSwitchBoardPerferDataPathWifi);
        Intent intent = new Intent();
        intent.setAction(SWITCHBOARD_INTENT_WIFI_PREFERENCE_VALUE);
        intent.putExtra("Preference", this.mIsSwitchBoardPerferDataPathWifi);
        logv("Send broadcast=" + intent);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
    }

    protected static void loge(String str) {
        Log.e(LOG_TAG, str);
    }

    protected static void logw(String str) {
        Log.w(LOG_TAG, str);
    }

    protected static void logi(String str) {
        Log.i(LOG_TAG, str);
    }

    protected static void logd(String str) {
        if (!mIsShipBuild || VDBG) {
            Log.d(LOG_TAG, str);
        }
    }

    protected static void logv(String str) {
        if (VDBG) {
            Log.v(LOG_TAG, str);
        }
    }
}
