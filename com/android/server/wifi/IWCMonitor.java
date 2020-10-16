package com.android.server.wifi;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.IProcessObserver;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.wifi.hotspot2.SystemInfo;
import com.android.server.wifi.iwc.IWCDataRateTraceData;
import com.android.server.wifi.iwc.IWCDataUsagePatternTracker;
import com.android.server.wifi.iwc.IWCEventManager;
import com.android.server.wifi.iwc.IWCJsonFile;
import com.android.server.wifi.iwc.IWCLogFile;
import com.android.server.wifi.iwc.IWCPolicy;
import com.android.server.wifi.iwc.RewardEvent;
import com.android.server.wifi.iwc.rlengine.QTable;
import com.android.server.wifi.iwc.rlengine.QTableContainer;
import com.android.server.wifi.iwc.rlengine.QTableContainerBuilder;
import com.android.server.wifi.iwc.rlengine.RFLearningTop;
import com.android.server.wifi.tcp.WifiTransportLayerUtils;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.server.wifi.iwc.IwcBigDataMIWC;
import com.samsung.android.server.wifi.iwc.IwcBigDataManager;
import com.sec.android.app.CscFeatureTagWifi;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.json.JSONException;

public class IWCMonitor extends StateMachine {
    public static final int AGGRESSIVE_SETTINGS_CHANGED = 552984;
    private static final int BASE = 552960;
    private static final String CONFIG_PATH = "/data/misc/wifi/";
    private static final int CONNECTION_HISTORY_SIZE = 2;
    private static final String CSC_WIFI_DEFAULTAP_DONE = "com.samsung.intent.action.CSC_WIFI_DEFAULTAP_DONE";
    private static final String DATA_LIMIT_INTENT = "com.android.intent.action.DATAUSAGE_REACH_TO_LIMIT";
    private static boolean DBG = Debug.semIsProductDev();
    public static final int DONGLE_ROAMING_ESTABLISHED = 552986;
    public static final String EMERGENCY_MODE_PACKAGE_NAME = "emergency";
    private static final int EVENT_BIGDATA_UPDATE = 552966;
    private static final int EVENT_INET_CONDITION_ACTION = 553037;
    private static final int EVENT_LOAD_START = 552964;
    private static final int EVENT_NETWORK_REMOVED = 553036;
    private static final int EVENT_NETWORK_STATE_CHANGE = 552961;
    private static final int EVENT_SCREEN_OFF = 552963;
    private static final int EVENT_SCREEN_ON = 552962;
    private static final int EVENT_WIFI_STATE_CHANGED = 552965;
    private static final int EVENT_WIFI_TOGGLED = 553035;
    private static final double EXP_COEFFICIENT_MONITOR = 0.5d;
    public static final int FACTORY_RESET_REQUIRED = 552987;
    public static final int FORCE_ACTION = 552993;
    private static final int GOOD_LINK_SAMPLE_COUNT = 3;
    private static final String HQM_UPDATE_REQ = "com.sec.android.intent.action.HQM_UPDATE_REQ";
    public static final String INVALID_STATE_TEST_ACTION = "com.android.server.wifi.iwc.INVALID_STATE_TEST";
    private static final String IWCD_FILE = "/data/misc/wifi/.iwcd";
    private static final int IWC_DISC_REASON_BY_NONE = 0;
    private static final int IWC_DISC_REASON_BY_PHONE = 2;
    private static final int IWC_DISC_REASON_BY_USER = 1;
    private static final int IWC_SWITCH_REASON_ELE_DETECTED = 4;
    private static final int IWC_SWITCH_REASON_POORLINK_DETECTED = 5;
    private static final int IWC_SWITCH_REASON_QC_FAILED_BY_IWC = 2;
    private static final int IWC_SWITCH_REASON_QC_FAILED_BY_WCM = 3;
    private static final int IWC_SWITCH_REASON_QC_SUCCESS_BY_IWC = 0;
    private static final int IWC_SWITCH_REASON_QC_SUCCESS_BY_WCM = 1;
    public static final int IWC_WIFI_DISCONNECTED = 552985;
    private static long LINK_SAMPLING_INTERVAL_MS = 1000;
    private static final String LOG_NAME = "iwc_dump.txt";
    private static final String LOG_PATH = "/data/log/wifi/iwc/";
    private static final int[] MHS_PRIVATE_NETWORK_MASK = {2861248, 660652};
    private static boolean MISC_DBG = false;
    private static final String PACKAGE_SERVICE_MODE_APP = "com.sec.android.app.servicemodeapp";
    public static final String POORLINK_STATE_TEST_ACTION = "com.android.server.wifi.iwc.POOR_LINK_TEST";
    private static final String PREFERENCE_NAME = "qtables.json";
    public static final int RESET_LEARNING_DATA = 552991;
    public static final int RESTORE_USER_PREFERENCE = 552990;
    public static final int REWARD_EVENT_AUTO_DISCONNECTION = 553017;
    public static final int REWARD_EVENT_MANUAL_DISCONNECT = 553018;
    public static final int REWARD_EVENT_MANUAL_RECONNECT = 553016;
    public static final int REWARD_EVENT_MANUAL_SWITCH = 553010;
    public static final int REWARD_EVENT_MANUAL_SWITCH_G = 553011;
    public static final int REWARD_EVENT_MANUAL_SWITCH_L = 553012;
    public static final int REWARD_EVENT_MOBILE_DATA_DISABLE = 553015;
    public static final int REWARD_EVENT_SNS_DISABLE = 553019;
    public static final int REWARD_EVENT_SNS_ENABLE = 553020;
    public static final int REWARD_EVENT_SWITCHED_TOO_SHORT = 553013;
    public static final int REWARD_EVENT_WIFI_DISABLE = 553014;
    private static boolean RSSI_DBG = false;
    public static final int SIM_SLOT_1 = 0;
    public static final int SIM_SLOT_2 = 1;
    public static final int SNS_SETTINGS_CHANGED = 552983;
    public static final int START_FROM_BEGINNING = 552988;
    private static final String STR_CMD_IWC_CURRENT_QAI = "IWC-CURRENT-QAI ";
    private static final String STR_CMD_IWC_REQUEST_INTERNET_CHECK = "REQUEST-INTERNET-CHECK ";
    private static final String STR_CMD_IWC_REQUEST_NETWORK_SWITCH_TO_MOBILE = "REQUEST-NETWORK-SWITCH-TO-MOBILE ";
    private static final String STR_CMD_IWC_REQUEST_NETWORK_SWITCH_TO_WIFI = "REQUEST-NETWORK-SWITCH-TO-WIFI ";
    private static final String TAG = "IWCMonitor";
    private static final String TEMP_BACKUP_PATH = "/data/misc/wifi_share_profile/";
    private static final String TEMP_IWC_QTABLE_RESTORE_FILE_PATH = "/data/misc/wifi_share_profile/qtables_restore.json";
    public static final int TEST_START = 1;
    public static final int TEST_STOP = 2;
    public static final int TRANSIT_TO_INVALID = 552982;
    public static final int TRANSIT_TO_VALID = 552981;
    public static final int WIFI_INTERNET_SERVICE_CHECK_DISABLED_AIRPLANE_MODE = 3;
    public static final int WIFI_INTERNET_SERVICE_CHECK_DISABLED_BLOCKED_BY_DATA_ROAMING = 5;
    public static final int WIFI_INTERNET_SERVICE_CHECK_DISABLED_MOBILE_DATA_DISABLED = 4;
    public static final int WIFI_INTERNET_SERVICE_CHECK_DISABLED_NO_SIM = 2;
    public static final int WIFI_INTERNET_SERVICE_CHECK_ENABLED = 1;
    public static final int WIFI_OFF_EVENT_PENDED = 552989;
    private static final WifiInfo sDummyWifiInfo = new WifiInfo();
    private static boolean sWifiOnly = false;
    public static final long serialVersionUID = 20181101;
    public final String APP_ID = "android.net.wifi";
    private final ActivityManager mActivityManager;
    private final AlarmManager mAlarmManager;
    private RewardEventDetectionPolicy mAutoDisconnectionPolicy;
    private int mAutoReconnectNetworkId = -1;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        /* class com.android.server.wifi.IWCMonitor.C03976 */

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                IWCMonitor.this.sendMessage(IWCMonitor.EVENT_WIFI_STATE_CHANGED, intent);
            } else if (action.equals("android.net.wifi.STATE_CHANGE")) {
                IWCMonitor.this.sendMessage(IWCMonitor.EVENT_NETWORK_STATE_CHANGE, intent);
            } else if (action.equals("android.intent.action.SCREEN_ON")) {
                IWCMonitor.this.sendMessage(IWCMonitor.EVENT_SCREEN_ON);
            } else if (action.equals("android.intent.action.SCREEN_OFF")) {
                IWCMonitor.this.sendMessage(IWCMonitor.EVENT_SCREEN_OFF);
            } else {
                boolean setting_sns = false;
                if (IWCMonitor.DBG && action.equals(IWCMonitor.POORLINK_STATE_TEST_ACTION)) {
                    boolean test = intent.getBooleanExtra("test", false);
                    Log.d(IWCMonitor.TAG, "com.android.server.wifi.iwc.POOR_LINK_TEST test: " + test);
                    if (test) {
                        IWCMonitor.this.mPoorLinkStateTesting = true;
                        IWCMonitor.this.mLinkLossOccurred = 1;
                        IWCMonitor.this.mPreviousLinkLoss = 0;
                        IWCMonitor iWCMonitor = IWCMonitor.this;
                        iWCMonitor.transitionTo(iWCMonitor.mPoorLinkState);
                        return;
                    }
                    IWCMonitor.this.mPoorLinkStateTesting = false;
                    IWCMonitor.this.mLinkLossOccurred = 0;
                    IWCMonitor.this.mPreviousLinkLoss = 0;
                } else if (IWCMonitor.DBG && action.equals(IWCMonitor.INVALID_STATE_TEST_ACTION)) {
                    boolean test2 = intent.getBooleanExtra("test", false);
                    Log.d(IWCMonitor.TAG, "com.android.server.wifi.iwc.INVALID_STATE_TEST test: " + test2);
                    if (test2) {
                        IWCMonitor.this.mLastDisconnectedReason = 2;
                        IWCMonitor.this.mPoorLinkStateTesting = true;
                        IWCMonitor.this.mLinkLossOccurred = 1;
                        IWCMonitor.this.mPreviousLinkLoss = 0;
                        IWCMonitor.this.mWcmChannel.sendMessage(135473, 1);
                        return;
                    }
                    IWCMonitor.this.mWcmChannel.sendMessage(135472, 2);
                } else if (IWCMonitor.MISC_DBG && action.equals("IWCTEST.NID.CONNECT")) {
                    int nid = intent.getIntExtra("nid", -1);
                    Log.e(IWCMonitor.TAG, "====IWCTEST.NID.CONNECT!!!==== nid=" + nid);
                    if (nid != -1) {
                        IWCMonitor.this.mWifiManager.connect(nid, null);
                    }
                } else if (action.equals(IWCMonitor.DATA_LIMIT_INTENT)) {
                    IWCMonitor.this.mIsPolicyMobileData = intent.getBooleanExtra("policyData", false);
                    Log.d(IWCMonitor.TAG, "DATA_LIMIT_INTENT " + IWCMonitor.this.mIsPolicyMobileData);
                    if (IWCMonitor.this.mIsPolicyMobileData && IWCMonitor.this.mMobileDataDisablePolicy.isValid()) {
                        IWCMonitor.this.detectRewardEventMobileDataDisable();
                    }
                } else if (action.equals(IWCMonitor.CSC_WIFI_DEFAULTAP_DONE)) {
                    boolean backup_agg = Settings.Global.getInt(IWCMonitor.this.mContext.getContentResolver(), "wifi_iwc_backup_aggressive_mode_on", 0) != 0;
                    if (Settings.Global.getInt(IWCMonitor.this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 0) != 0) {
                        setting_sns = true;
                    }
                    Log.d(IWCMonitor.TAG, "CSC_WIFI_DEFAULTAP_DONE, sns - " + setting_sns + ", backup_agg - " + backup_agg);
                    if (setting_sns && !backup_agg) {
                        Log.d(IWCMonitor.TAG, "Set default QAI to 2 from previous sns mode");
                        IWCMonitor.this.mRLEngine.removeNonSSQtables();
                        IWCMonitor.this.mRLEngine.setDefaultQAI(2);
                        if (IWCMonitor.this.isConnectedNetworkState()) {
                            IWCMonitor.this.sendDebugIntent(true);
                            IWCMonitor.this.mRLEngine.algorithmStep();
                        }
                        IWCMonitor.this.save_model_obj();
                        Settings.Global.putInt(context.getContentResolver(), "wifi_iwc_backup_aggressive_mode_on", 1);
                    }
                } else if (action.equals(IWCMonitor.HQM_UPDATE_REQ)) {
                    Log.d(IWCMonitor.TAG, "HQM_UPDATE_REQ is called");
                    IWCMonitor.this.sendMessage(IWCMonitor.EVENT_BIGDATA_UPDATE);
                }
            }
        }
    };
    private final ClientModeImpl mClientModeImpl;
    @VisibleForTesting
    AsyncChannel mClientModeImplChannel;
    private int mConnectNetworkId = -1;
    private String mConnectNetworkPackageName;
    private boolean mConnectNetworkTriggeredByUser = false;
    private int mConnectNetworkUid;
    private ConnectedState mConnectedState = new ConnectedState();
    private ConnectionHistory mConnectionHistory;
    private ConnectivityManager mConnectivityManager = null;
    private final Context mContext;
    private boolean mCurAGG;
    private boolean mCurSNS;
    private String mCurrentBssid;
    private int mCurrentNetworkId = -1;
    private String mCurrentPackageName;
    private int mCurrentQAI = 0;
    private int mCurrentQAIDbg = 0;
    private int mCurrentRssi;
    private ArrayList<String> mCurrentServicePackageNameList;
    private int mCurrentSignalLevel;
    private int mCurrentUid;
    private WifiInfo mCurrentWifiInfo;
    private long mDQAIDuration = 0;
    private DefaultState mDefaultState = new DefaultState();
    private boolean mDisconnectToConnectNewNetwork = false;
    private DisconnectedState mDisconnectedState = new DisconnectedState();
    private boolean mDoingRestore = false;
    private FileObserver mFileObserver;
    private GoodLinkState mGoodLinkState = new GoodLinkState();
    private int mGoodLinkTargetCount = 0;
    private int mGoodLinkTargetRssi = 0;
    private long mHDRThreshold = 0;
    private final IntentFilter mIntentFilter;
    private int mInternetCheckId = 0;
    private InvalidState mInvalidState = new InvalidState();
    private boolean mIsDebugMode = false;
    private boolean mIsInRoamSession;
    private boolean mIsMobileDataEnabled;
    private boolean mIsPolicyMobileData = false;
    private boolean mIsQCRequested = false;
    private boolean mIsSaver = false;
    private boolean mIsSaverDbg = false;
    private boolean mIsScanning = false;
    private boolean mIsScreenOn;
    private boolean mIsSteadyState = false;
    private boolean mIsSteadyStateDbg = false;
    private boolean mIsWifiDisabledByUser = false;
    private boolean mIsWifiEnabled;
    private IwcBigDataManager mIwcBigDataManager;
    private IWCDataRateTraceData mIwcDataRateTraceData;
    private IWCDataUsagePatternTracker mIwcDataUsagePatternTracker;
    private long mLDRThreshold = 0;
    private int mLastCheckedMobileHotspotNetworkId = -1;
    private boolean mLastCheckedMobileHotspotValue = false;
    private String mLastConnectedBssid;
    private int mLastConnectedNetworkId = -1;
    private int mLastDisconnectedReason = 0;
    private long mLastInvalidEnterTimestamp;
    private long mLastPoorLinkTimestamp;
    private long mLastPoorLinkTimestampBeforeDisc;
    private long mLastTimeSample = 0;
    private long mLastTimeSampleForTraffic = 0;
    private int mLastTxBad = 0;
    private int mLastTxGood = 0;
    private int mLinkLossOccurred = 0;
    private IWCLogFile mLogFile;
    private int mLossHasGone = 0;
    private int mLossSampleCount = 0;
    private RewardEventDetectionPolicy mManualDisconnectPolicy;
    private RewardEventDetectionPolicy mManualReconnectPolicy;
    private RewardEventDetectionPolicy mManualSwitchPolicy;
    private RewardEventDetectionPolicy mMobileDataDisablePolicy;
    private boolean mNetworkDisabledByAutoReconnect = false;
    private boolean mPingEnabled = false;
    private PoorLinkState mPoorLinkState = new PoorLinkState();
    private boolean mPoorLinkStateTesting = false;
    private IWCJsonFile<QTableContainer.SavedMembers> mPreferenceFile;
    private String mPreviousBssid = null;
    private int mPreviousLinkLoss = 0;
    private double mPreviousLoss = 0.0d;
    private int mPreviousRssi;
    private final IProcessObserver mProcessObserver = new IProcessObserver.Stub() {
        /* class com.android.server.wifi.IWCMonitor.IProcessObserver$StubC03965 */

        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            List<ActivityManager.RunningAppProcessInfo> curProcessStack;
            try {
                if ((uid == IWCMonitor.this.mCurrentUid || foregroundActivities) && (curProcessStack = IWCMonitor.this.mActivityManager.getRunningAppProcesses()) != null) {
                    ArrayList<String> prevPackageList = IWCMonitor.this.mCurrentServicePackageNameList;
                    ArrayList<String> currentPackageList = new ArrayList<>();
                    boolean topFound = false;
                    for (ActivityManager.RunningAppProcessInfo rap : curProcessStack) {
                        if (rap != null) {
                            if (topFound || rap.importance != 100) {
                                if (rap.importance == 125 || rap.importance == 300) {
                                    boolean found = false;
                                    Iterator<String> it = prevPackageList.iterator();
                                    while (true) {
                                        if (!it.hasNext()) {
                                            break;
                                        }
                                        String service = it.next();
                                        if (service != null && service.equals(rap.processName)) {
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found && IWCMonitor.MISC_DBG) {
                                        Log.i(IWCMonitor.TAG, rap.processName + " (" + rap.uid + ") is working as service");
                                    }
                                    currentPackageList.add(rap.processName);
                                }
                            } else if (IWCMonitor.this.mCurrentUid != rap.uid) {
                                topFound = true;
                                IWCMonitor.this.mCurrentUid = rap.uid;
                                IWCMonitor.this.mCurrentPackageName = rap.processName;
                                if (IWCMonitor.DBG) {
                                    Log.i(IWCMonitor.TAG, IWCMonitor.this.mCurrentPackageName + " (" + IWCMonitor.this.mCurrentUid + ") has came on foreground");
                                }
                                if (IWCMonitor.PACKAGE_SERVICE_MODE_APP.equals(IWCMonitor.this.mCurrentPackageName)) {
                                    Log.w(IWCMonitor.TAG, "UpdateIWCSystemProp");
                                    IWCMonitor.this.UpdateIWCSystemProp();
                                }
                            }
                        }
                    }
                    synchronized (IWCMonitor.this.mCurrentServicePackageNameList) {
                        IWCMonitor.this.mCurrentServicePackageNameList = currentPackageList;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void onProcessDied(int pid, int uid) {
            if (uid == IWCMonitor.this.mCurrentUid) {
                onForegroundActivitiesChanged(pid, uid, false);
            }
        }

        public void onForegroundServicesChanged(int arg0, int arg1, int arg2) throws RemoteException {
        }
    };
    private RFLearningTop mRLEngine;
    private int mRemoveNetworkId = -1;
    private String mRemoveNetworkPackageName;
    private int mRemoveNetworkUid;
    private boolean mRemoveUnwantedNetworkToGoBack = false;
    public final String[] mRewardEventWhiteList = {WifiConfigManager.SYSUI_PACKAGE_NAME, "com.android.settings"};
    private int mRssiFetchTk = 0;
    private long mRxBytes = 0;
    private long mRxBytesInDQAI = 0;
    private boolean mSNSAvailability;
    private RewardEventDetectionPolicy mSNSDisablePolicy;
    private RewardEventDetectionPolicy mSNSEnablePolicy;
    private long mSavedBytes = 0;
    private int mSaverState = 0;
    private RewardEventDetectionPolicy mSwitchedTooShortPolicy;
    private long mTxBytes = 0;
    private long mTxBytesInDQAI = 0;
    private int mValidLastRssi = 0;
    private ValidState mValidState = new ValidState();
    private AsyncChannel mWcmChannel;
    private final WifiConfigManager mWifiConfigManager;
    private String mWifiDisablePackage;
    private RewardEventDetectionPolicy mWifiDisablePolicy;
    private final WifiManager mWifiManager;
    private long timeStampConnected = 0;
    private long timeStampDisconnected = 0;
    private long timeStampPoorLinkTrig = 0;

    static /* synthetic */ int access$2808(IWCMonitor x0) {
        int i = x0.mLinkLossOccurred;
        x0.mLinkLossOccurred = i + 1;
        return i;
    }

    static /* synthetic */ int access$3104(IWCMonitor x0) {
        int i = x0.mLossHasGone + 1;
        x0.mLossHasGone = i;
        return i;
    }

    private IWCMonitor(Context context, ClientModeImpl cmi, WifiConfigManager wcm) {
        super(TAG);
        this.mContext = context;
        this.mWifiManager = (WifiManager) this.mContext.getSystemService("wifi");
        this.mActivityManager = (ActivityManager) this.mContext.getSystemService("activity");
        this.mAlarmManager = (AlarmManager) this.mContext.getSystemService("alarm");
        this.mClientModeImplChannel = new AsyncChannel();
        this.mClientModeImplChannel.connectSync(this.mContext, getHandler(), cmi.getMessenger());
        this.mClientModeImpl = cmi;
        this.mCurrentWifiInfo = new WifiInfo();
        this.mConnectionHistory = new ConnectionHistory();
        this.mWifiConfigManager = wcm;
        this.mLogFile = new IWCLogFile("/data/log/wifi/iwc/iwc_dump.txt", TAG);
        this.mIwcDataRateTraceData = new IWCDataRateTraceData();
        this.mIwcDataUsagePatternTracker = new IWCDataUsagePatternTracker(this.mContext, this.mLogFile);
        this.mIwcBigDataManager = new IwcBigDataManager(this.mContext);
        if (DBG) {
            setDebugParams(loadIWCDbgFile());
            this.mFileObserver = new FileObserver(IWCD_FILE, 8) {
                /* class com.android.server.wifi.IWCMonitor.FileObserverC03921 */

                public void onEvent(int event, String path) {
                    Log.e(IWCMonitor.TAG, "FileObserver event received - " + event);
                    IWCMonitor iWCMonitor = IWCMonitor.this;
                    iWCMonitor.setDebugParams(iWCMonitor.loadIWCDbgFile());
                }
            };
            this.mFileObserver.startWatching();
        }
        addState(this.mDefaultState);
        addState(this.mDisconnectedState, this.mDefaultState);
        addState(this.mConnectedState, this.mDefaultState);
        addState(this.mInvalidState, this.mConnectedState);
        addState(this.mValidState, this.mConnectedState);
        addState(this.mGoodLinkState, this.mValidState);
        addState(this.mPoorLinkState, this.mValidState);
        setInitialState(this.mDisconnectedState);
        this.mIntentFilter = new IntentFilter();
        this.mIntentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        this.mIntentFilter.addAction("android.net.wifi.STATE_CHANGE");
        this.mIntentFilter.addAction("android.intent.action.SCREEN_ON");
        this.mIntentFilter.addAction("android.intent.action.SCREEN_OFF");
        this.mIntentFilter.addAction(POORLINK_STATE_TEST_ACTION);
        this.mIntentFilter.addAction(INVALID_STATE_TEST_ACTION);
        this.mIntentFilter.addAction(DATA_LIMIT_INTENT);
        this.mIntentFilter.addAction(CSC_WIFI_DEFAULTAP_DONE);
        this.mIntentFilter.addAction(HQM_UPDATE_REQ);
        boolean z = true;
        if (MISC_DBG) {
            this.mIntentFilter.addAction("IWCTEST.NID.CONNECT");
        }
        this.mCurSNS = Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 0) != 0;
        this.mCurAGG = this.mCurSNS;
        this.mIsMobileDataEnabled = Settings.Global.getInt(this.mContext.getContentResolver(), "mobile_data", 1) == 0 ? false : z;
        this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("mobile_data"), false, new ContentObserver(getHandler()) {
            /* class com.android.server.wifi.IWCMonitor.C03932 */

            public void onChange(boolean selfChange) {
                IWCMonitor.this.updateSettings();
            }
        });
        try {
            this.mCurrentUid = -1;
            this.mCurrentPackageName = "default";
            ActivityManager.getService().registerProcessObserver(this.mProcessObserver);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException - registerProcessObserver");
            e.printStackTrace();
        }
        this.mManualSwitchPolicy = new RewardEventDetectionPolicy(getHandler(), RewardEvent.MANUAL_SWITCH, REWARD_EVENT_MANUAL_SWITCH, 0);
        this.mManualDisconnectPolicy = new RewardEventDetectionPolicy(getHandler(), RewardEvent.MANUAL_DISCONNECT, REWARD_EVENT_MANUAL_DISCONNECT, 0);
        this.mSwitchedTooShortPolicy = new RewardEventDetectionPolicy(getHandler(), RewardEvent.CONNECTION_SWITCHED_TOO_SHORT, REWARD_EVENT_SWITCHED_TOO_SHORT, 30000);
        this.mWifiDisablePolicy = new RewardEventDetectionPolicy(getHandler(), RewardEvent.WIFI_OFF, REWARD_EVENT_WIFI_DISABLE, 0);
        this.mMobileDataDisablePolicy = new RewardEventDetectionPolicy(getHandler(), RewardEvent.CELLULAR_DATA_OFF, REWARD_EVENT_MOBILE_DATA_DISABLE, 0);
        this.mManualReconnectPolicy = new RewardEventDetectionPolicy(getHandler(), RewardEvent.MANUAL_RECONNECTION, REWARD_EVENT_MANUAL_RECONNECT, IWCEventManager.reconTimeThreshold);
        this.mAutoDisconnectionPolicy = new RewardEventDetectionPolicy(getHandler(), RewardEvent.AUTO_DISCONNECTION, REWARD_EVENT_AUTO_DISCONNECTION, 0);
        this.mSNSDisablePolicy = new RewardEventDetectionPolicy(getHandler(), RewardEvent.SNS_OFF, REWARD_EVENT_SNS_DISABLE, 0);
        this.mSNSEnablePolicy = new RewardEventDetectionPolicy(getHandler(), RewardEvent.SNS_ON, REWARD_EVENT_SNS_ENABLE, 0);
        this.mMobileDataDisablePolicy.adopt();
        this.mSNSDisablePolicy.adopt();
        this.mSNSEnablePolicy.adopt();
        this.mSwitchedTooShortPolicy.setAlarmListener(new AlarmManager.OnAlarmListener() {
            /* class com.android.server.wifi.IWCMonitor.C03943 */

            public void onAlarm() {
                if (IWCMonitor.DBG) {
                    String tag = IWCMonitor.this.mSwitchedTooShortPolicy.getTag();
                    Log.w(tag, "Timer( " + IWCMonitor.this.mSwitchedTooShortPolicy.getTimer() + ") is expired");
                }
                IWCMonitor.this.mSwitchedTooShortPolicy.discard();
                IWCMonitor.this.mConnectNetworkPackageName = null;
                IWCMonitor.this.mAutoDisconnectionPolicy.adopt();
            }
        });
        this.mManualReconnectPolicy.setAlarmListener(new AlarmManager.OnAlarmListener() {
            /* class com.android.server.wifi.IWCMonitor.C03954 */

            public void onAlarm() {
                if (IWCMonitor.DBG) {
                    String tag = IWCMonitor.this.mManualReconnectPolicy.getTag();
                    Log.w(tag, "Timer( " + IWCMonitor.this.mManualReconnectPolicy.getTimer() + ") is expired");
                }
                IWCMonitor.this.mManualReconnectPolicy.discard();
                IWCMonitor.this.mRemoveUnwantedNetworkToGoBack = false;
            }
        });
        this.mPreferenceFile = new IWCJsonFile<>("/data/misc/wifi/qtables.json", QTableContainer.SavedMembers.class, null, null, true);
        this.mRLEngine = new RFLearningTop(this.mLogFile);
        this.mRLEngine.intf.mContext = context;
        setIntfSnsFlag();
        this.mCurrentServicePackageNameList = new ArrayList<>();
        writeToLogFile("Installed IT(" + isPackageInstalled("com.salab.issuetracker") + ") STC(" + Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_num_of_switch_to_mobile_data_toggle", 0) + ") ", "SW Version : " + SystemProperties.get("ro.build.version.incremental", SystemInfo.UNKNOWN_INFO));
        if (this.mConnectivityManager == null) {
            this.mConnectivityManager = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        }
        if (this.mRLEngine.intf.mBdTracking != null) {
            String defaultOfSns = "DEFAULT_ON".equals(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGSNSSTATUS)) ? "1" : "0";
            this.mRLEngine.intf.mBdTracking.setDefaultSns(defaultOfSns);
            writeToLogFile("Default value of ", "defaultOfSns: " + defaultOfSns);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setBroadcastReceiver() {
        this.mContext.registerReceiver(this.mBroadcastReceiver, this.mIntentFilter);
    }

    private void unregisterBroadcastReceiver() {
        BroadcastReceiver broadcastReceiver = this.mBroadcastReceiver;
        if (broadcastReceiver != null) {
            try {
                this.mContext.unregisterReceiver(broadcastReceiver);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "mBroadcastReceiver : Already unregistered");
            }
        }
    }

    /* access modifiers changed from: private */
    public static class Connection {
        public String bssid;
        public long connectedTime;
        public int networkId;
        public boolean triggeredByUser;

        Connection(String bssid2, int id, long now, boolean triggeredByUser2) {
            this.bssid = bssid2;
            this.networkId = id;
            this.connectedTime = now;
            this.triggeredByUser = triggeredByUser2;
        }
    }

    /* access modifiers changed from: private */
    public static class ConnectionHistory {
        private LinkedList<Connection> history = new LinkedList<>();

        ConnectionHistory() {
        }

        public void addLastConnection(Connection c) {
            if (IWCMonitor.DBG) {
                Log.i(IWCMonitor.TAG, "Size: " + this.history.size() + " Network ID: " + c.networkId + " ByUser: " + c.triggeredByUser);
            }
            this.history.addFirst(c);
            if (this.history.size() > 2) {
                this.history.removeLast();
            }
        }

        public Connection getLastConnection() {
            if (this.history.size() > 0) {
                return this.history.getFirst();
            }
            Log.w(IWCMonitor.TAG, "Connection history is empty");
            return null;
        }

        public boolean findConnectionIdWithin(long diff, long current, int nid) {
            Iterator<Connection> it = this.history.iterator();
            while (it.hasNext()) {
                Connection c = it.next();
                Log.w(IWCMonitor.TAG, "NetworkID: " + c.networkId + " connected TIME: " + c.connectedTime + " Duration: " + (current - c.connectedTime) + " ByUser: " + c.triggeredByUser);
                if (c.networkId == nid && current - c.connectedTime < diff) {
                    Log.w(IWCMonitor.TAG, "ConnectionHistory Found at " + c.connectedTime + " ByUser: " + c.triggeredByUser);
                    return true;
                }
            }
            return false;
        }

        public String getBssidList() {
            String msg = new String();
            Iterator<Connection> it = this.history.iterator();
            while (it.hasNext()) {
                msg = msg + it.next().bssid + " ";
            }
            return msg;
        }
    }

    public static IWCMonitor initIWCMonitor(Context context, WifiInjector wi) {
        Log.i(TAG, "IWCMonitor starting up... serialVersionUID = 20181101");
        sWifiOnly = SystemProperties.getBoolean("ro.radio.noril", false);
        IWCMonitor iwcm = new IWCMonitor(context, wi.getClientModeImpl(), wi.getWifiConfigManager());
        iwcm.setLogOnlyTransitions(false);
        iwcm.start();
        iwcm.sendMessage(EVENT_LOAD_START);
        File restoredSource = new File(TEMP_IWC_QTABLE_RESTORE_FILE_PATH);
        if (restoredSource.exists()) {
            restoredSource.delete();
        }
        return iwcm;
    }

    private ConnectivityManager getCm() {
        if (this.mConnectivityManager == null) {
            this.mConnectivityManager = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        }
        return this.mConnectivityManager;
    }

    private void logStateAndMessage(Message message, State state) {
        if (DBG) {
            Log.i(TAG, " " + state.getClass().getSimpleName() + " " + getLogRecString(message));
        }
    }

    /* access modifiers changed from: protected */
    public String getLogRecString(Message msg) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("(" + getKernelTime() + ")");
            StringBuilder sb2 = new StringBuilder();
            sb2.append(" ");
            sb2.append(Integer.valueOf(smToString(msg.what).substring(5)).intValue() - BASE);
            sb.append(sb2.toString());
        } catch (NumberFormatException e) {
            sb.append(smToString(msg.what));
        }
        int i = msg.what;
        sb.append(" ");
        sb.append(Integer.toString(msg.arg1));
        sb.append(" ");
        sb.append(Integer.toString(msg.arg2));
        return sb.toString();
    }

    private String smToString(int what) {
        return "what:" + Integer.toString(what);
    }

    private String getKernelTime() {
        return Double.toString(((double) (System.nanoTime() / 1000000)) / 1000.0d);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateSettings() {
        boolean prev = this.mIsMobileDataEnabled;
        this.mIsMobileDataEnabled = Settings.Global.getInt(this.mContext.getContentResolver(), "mobile_data", 1) != 0;
        if (prev && !this.mIsMobileDataEnabled) {
            State state = getCurrentState();
            if (!this.mMobileDataDisablePolicy.isValid() || (state != this.mInvalidState && state != this.mPoorLinkState && getDiffFromLastPoorLink() >= 20000 && (state != this.mDisconnectedState || getTimeStamp() - this.mLastPoorLinkTimestampBeforeDisc >= 20000))) {
                synchronized (this) {
                    this.mRLEngine.intf.edgeFlag = false;
                    writeToLogFile("Mobile Data Off, timestamp =", String.valueOf(getTimeStamp()) + ", edgeflag = " + this.mRLEngine.intf.edgeFlag);
                }
            } else {
                detectRewardEventMobileDataDisable();
            }
        }
        if (DBG) {
            Log.d(TAG, "updateSettings: " + this.mIsMobileDataEnabled);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isUserInteraction(String callingPackage) {
        if (this.mCurrentPackageName.equals(callingPackage)) {
            return true;
        }
        for (String packageName : this.mRewardEventWhiteList) {
            if (packageName.equals(callingPackage)) {
                return true;
            }
        }
        return false;
    }

    public void sendUserSelection(boolean keepConnection) {
        if (DBG) {
            StringBuilder sb = new StringBuilder();
            sb.append("User wants to ");
            sb.append(keepConnection ? "" : "not");
            sb.append("keep connection BSSID: ");
            sb.append(this.mCurrentBssid);
            Log.d(TAG, sb.toString());
        }
        if (keepConnection) {
            sendMessageToWCM(WifiConnectivityMonitor.CMD_IWC_CURRENT_QAI, -1);
        } else if (this.mSwitchedTooShortPolicy.isValid()) {
            this.mSwitchedTooShortPolicy.discard();
        }
    }

    public static boolean isHomeDefault(String pkg, PackageManager pm) {
        ComponentName def = pm.getHomeActivities(new ArrayList<>());
        return def == null || def.getPackageName().equals(pkg);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isService(String packageName) {
        if (packageName == null) {
            return false;
        }
        synchronized (this.mCurrentServicePackageNameList) {
            Iterator<String> it = this.mCurrentServicePackageNameList.iterator();
            while (it.hasNext()) {
                if (packageName.equals(it.next())) {
                    return true;
                }
            }
            return false;
        }
    }

    class DefaultState extends State {
        DefaultState() {
        }

        public void enter() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " enter\n");
            }
        }

        public void exit() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " exit\n");
            }
        }

        /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
        public boolean processMessage(Message msg) {
            long connectionTime;
            int originalQai;
            String type;
            int i = msg.what;
            boolean enable = false;
            if (i != 71) {
                if (i != 135376) {
                    if (i == 155656) {
                        boolean triggeredByUser = msg.arg1 == 1;
                        if (msg.arg2 == 1) {
                            enable = true;
                        }
                        Bundle args = (Bundle) msg.obj;
                        String packageName = args.getString("package");
                        int callingUid = args.getInt("calling_uid");
                        Log.d(IWCMonitor.TAG, "CMD_WIFI_TOGGLED: triggeredByUser: " + triggeredByUser + " enable: " + enable + " package: " + packageName);
                        if (enable) {
                            IWCMonitor.this.mWifiDisablePolicy.adopt();
                            if (triggeredByUser || (packageName != null && packageName.equals(IWCMonitor.this.mCurrentPackageName))) {
                                IWCMonitor iWCMonitor = IWCMonitor.this;
                                iWCMonitor.mConnectNetworkPackageName = iWCMonitor.mCurrentPackageName;
                            }
                            IWCMonitor.this.mPreviousBssid = null;
                        } else {
                            PackageManager packageManager = IWCMonitor.this.mContext.getPackageManager();
                            AppWidgetManager awm = (AppWidgetManager) IWCMonitor.this.mContext.getSystemService("appwidget");
                            IWCMonitor.this.mWifiDisablePackage = packageName;
                            if (triggeredByUser || (packageName != null && packageName.equals(IWCMonitor.this.mCurrentPackageName))) {
                                IWCMonitor.this.mIsWifiDisabledByUser = true;
                            } else if (awm.isBoundWidgetPackage(IWCMonitor.this.mWifiDisablePackage, UserHandle.getUserId(callingUid)) && IWCMonitor.isHomeDefault(IWCMonitor.this.mCurrentPackageName, packageManager)) {
                                if (IWCMonitor.DBG) {
                                    Log.i(IWCMonitor.TAG, "Triggered by widget and current foreground package is default home launcher");
                                }
                                IWCMonitor.this.mIsWifiDisabledByUser = true;
                            } else if (!IWCMonitor.this.isService(packageName)) {
                                if (IWCMonitor.DBG) {
                                    Log.i(IWCMonitor.TAG, "Triggered by visible process(not service)");
                                }
                                IWCMonitor.this.mIsWifiDisabledByUser = true;
                            }
                            if (IWCMonitor.this.mIsWifiDisabledByUser && IWCMonitor.this.mAutoDisconnectionPolicy.isValid()) {
                                IWCMonitor.this.mAutoDisconnectionPolicy.discard();
                            }
                        }
                    } else if (i != 552993) {
                        switch (i) {
                            case 151752:
                                IWCMonitor.this.mRemoveNetworkUid = msg.arg1;
                                IWCMonitor.this.mRemoveNetworkId = msg.arg2;
                                IWCMonitor.this.mRemoveNetworkPackageName = ((Bundle) msg.obj).getString("package");
                                IWCMonitor.this.writeToLogFile("EVENT_REMOVE_NETWORK_START", ", nid: " + msg.arg2 + ", uid: " + msg.arg1 + ", pName: " + IWCMonitor.this.mRemoveNetworkPackageName);
                                IWCMonitor iWCMonitor2 = IWCMonitor.this;
                                if (iWCMonitor2.isUserInteraction(iWCMonitor2.mRemoveNetworkPackageName)) {
                                    IWCMonitor.this.mManualDisconnectPolicy.adopt();
                                    IWCMonitor iWCMonitor3 = IWCMonitor.this;
                                    WifiConfiguration config = iWCMonitor3.getWifiConfiguration(iWCMonitor3.mRemoveNetworkId);
                                    synchronized (IWCMonitor.this) {
                                        connectionTime = IWCMonitor.this.getTimeStamp() - IWCMonitor.this.timeStampConnected;
                                        if (config == null) {
                                            IWCMonitor.this.writeToLogFile("EVENT_REMOVE_NETWORK_START", "getWifiConfiguration() failed");
                                        } else {
                                            IWCMonitor.this.mRLEngine.removeConfigKey(config.configKey());
                                        }
                                    }
                                    IWCMonitor.this.save_model_obj();
                                    IWCMonitor iWCMonitor4 = IWCMonitor.this;
                                    if (!iWCMonitor4.isUserInteraction(iWCMonitor4.mConnectNetworkPackageName) && connectionTime < 20000) {
                                        IWCMonitor.this.writeToLogFile("Connection time is short", "(" + connectionTime + "ms) the user may want to go back to previous network id: " + IWCMonitor.this.mConnectionHistory.getBssidList());
                                        IWCMonitor.this.mManualReconnectPolicy.adopt();
                                        IWCMonitor.this.mRemoveUnwantedNetworkToGoBack = true;
                                    }
                                }
                                return true;
                            case 151753:
                                IWCMonitor.this.mConnectNetworkUid = msg.arg1;
                                IWCMonitor.this.mConnectNetworkId = msg.arg2;
                                IWCMonitor.this.mConnectNetworkPackageName = ((Bundle) msg.obj).getString("package");
                                NetworkInfo connectNetworkInfo = IWCMonitor.this.mClientModeImpl.syncGetNetworkInfo();
                                IWCMonitor.this.writeToLogFile("EVENT_CONNECT_NETWORK_START", ", nid: " + msg.arg2 + ", uid: " + msg.arg1 + ", pName: " + IWCMonitor.this.mConnectNetworkPackageName + ", current network state: " + connectNetworkInfo.getDetailedState());
                                if (IWCMonitor.this.isConnectedNetworkState()) {
                                    IWCMonitor.this.mDisconnectToConnectNewNetwork = true;
                                }
                                IWCMonitor iWCMonitor5 = IWCMonitor.this;
                                if (iWCMonitor5.isUserInteraction(iWCMonitor5.mConnectNetworkPackageName) && ((IWCMonitor.this.mLastDisconnectedReason != 0 || IWCMonitor.this.mDisconnectToConnectNewNetwork) && IWCMonitor.this.mConnectNetworkId != IWCMonitor.this.mCurrentNetworkId)) {
                                    IWCMonitor.this.mConnectNetworkTriggeredByUser = true;
                                    IWCMonitor.this.mManualSwitchPolicy.adopt();
                                    IWCMonitor.this.detectRewardEventWhenConnectStart();
                                }
                                return true;
                            default:
                                int i2 = -1;
                                switch (i) {
                                    case IWCMonitor.EVENT_NETWORK_STATE_CHANGE /*{ENCODED_INT: 552961}*/:
                                        NetworkInfo.DetailedState state = ((NetworkInfo) ((Intent) msg.obj).getParcelableExtra("networkInfo")).getDetailedState();
                                        WifiInfo info = IWCMonitor.this.mClientModeImpl.getWifiInfo();
                                        String log = "WifiManager.NETWORK_STATE_CHANGED_ACTION state: " + state;
                                        if (info != null) {
                                            Log.d(IWCMonitor.TAG, log + " nid: " + info.getNetworkId() + " BSSID: " + info.getBSSID());
                                            IWCMonitor.this.networkStateChanged(state, info);
                                            break;
                                        } else {
                                            Log.e(IWCMonitor.TAG, log + " EXTRA_WIFI_INFO is null");
                                            break;
                                        }
                                    case IWCMonitor.EVENT_SCREEN_ON /*{ENCODED_INT: 552962}*/:
                                        IWCMonitor.this.mIsScreenOn = true;
                                        if (IWCMonitor.DBG) {
                                            Log.d(IWCMonitor.TAG, "EVENT_SCREEN_ON");
                                            break;
                                        }
                                        break;
                                    case IWCMonitor.EVENT_SCREEN_OFF /*{ENCODED_INT: 552963}*/:
                                        IWCMonitor.this.mIsScreenOn = false;
                                        synchronized (IWCMonitor.this) {
                                            IWCMonitor.this.mValidLastRssi = (IWCMonitor.this.mCurrentWifiInfo == null || IWCMonitor.this.mCurrentWifiInfo.getNetworkId() == -1) ? -64 : IWCMonitor.this.mCurrentWifiInfo.getRssi();
                                        }
                                        IWCMonitor.this.mLinkLossOccurred = 0;
                                        IWCMonitor.this.mPreviousLinkLoss = 0;
                                        IWCMonitor.this.mLossSampleCount = 0;
                                        IWCMonitor.this.mLossHasGone = 0;
                                        IWCMonitor.this.mPreviousLoss = 0.0d;
                                        IWCMonitor.this.mGoodLinkTargetCount = 0;
                                        if (IWCMonitor.this.mIwcDataRateTraceData != null) {
                                            IWCMonitor.this.mIwcDataRateTraceData.resetBufsAndCnts();
                                        }
                                        if (IWCMonitor.this.mIsSaver && IWCMonitor.this.mSaverState != 0) {
                                            IWCMonitor.this.unsetDQAI();
                                        }
                                        if (IWCMonitor.DBG) {
                                            Log.d(IWCMonitor.TAG, "EVENT_SCREEN_OFF");
                                            break;
                                        }
                                        break;
                                    case IWCMonitor.EVENT_LOAD_START /*{ENCODED_INT: 552964}*/:
                                        IWCMonitor.this.load_model_obj();
                                        IWCMonitor.this.setBroadcastReceiver();
                                        IWCMonitor.this.setIntfSnsFlag();
                                        IWCMonitor.this.mRLEngine.setDefaultQAI();
                                        break;
                                    case IWCMonitor.EVENT_WIFI_STATE_CHANGED /*{ENCODED_INT: 552965}*/:
                                        Intent wifiStateIntent = (Intent) msg.obj;
                                        int wifiState = wifiStateIntent.getIntExtra("wifi_state", 4);
                                        int previousWifiState = wifiStateIntent.getIntExtra("previous_wifi_state", 4);
                                        if (IWCMonitor.DBG) {
                                            Log.d(IWCMonitor.TAG, "EVENT_WIFI_STATE_CHANGED: " + previousWifiState + " -> " + wifiState);
                                        }
                                        if (wifiState != previousWifiState) {
                                            IWCMonitor.this.wifiStateChanged(wifiState);
                                            break;
                                        }
                                        break;
                                    case IWCMonitor.EVENT_BIGDATA_UPDATE /*{ENCODED_INT: 552966}*/:
                                        IWCMonitor.this.setBigDataMIWC();
                                        break;
                                    default:
                                        switch (i) {
                                            case IWCMonitor.TRANSIT_TO_VALID /*{ENCODED_INT: 552981}*/:
                                                if (IWCMonitor.DBG) {
                                                    Log.d(IWCMonitor.TAG, " DefaultState: TRANSIT_TO_VALID");
                                                    break;
                                                }
                                                break;
                                            case IWCMonitor.TRANSIT_TO_INVALID /*{ENCODED_INT: 552982}*/:
                                                if (IWCMonitor.DBG) {
                                                    Log.d(IWCMonitor.TAG, " DefaultState: TRANSIT_TO_INVALID");
                                                    break;
                                                }
                                                break;
                                            case IWCMonitor.SNS_SETTINGS_CHANGED /*{ENCODED_INT: 552983}*/:
                                                if (IWCMonitor.DBG) {
                                                    Log.d(IWCMonitor.TAG, "SNS_SETTINGS_CHANGED SNS Prev=" + msg.arg1 + " AGG Prev=" + msg.arg2);
                                                }
                                                boolean newsns = Settings.Global.getInt(IWCMonitor.this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 0) != 0;
                                                if (Settings.Global.getInt(IWCMonitor.this.mContext.getContentResolver(), "wifi_watchdog_poor_network_aggressive_mode_on", 0) != 0) {
                                                }
                                                boolean isDetected = false;
                                                boolean isNeedCheckToggleCnt = false;
                                                boolean isPrevCurSameValue = false;
                                                if (IWCMonitor.this.mCurSNS && IWCMonitor.this.mCurAGG) {
                                                    if (newsns && newsns) {
                                                        Log.d(IWCMonitor.TAG, "SNS Option changed 1->1");
                                                        isPrevCurSameValue = true;
                                                    } else if (!newsns || newsns) {
                                                        Log.d(IWCMonitor.TAG, "SNS Option changed 1->3 mDoingRestore: " + IWCMonitor.this.mDoingRestore);
                                                        if (!IWCMonitor.this.mDoingRestore && IWCMonitor.this.mSNSDisablePolicy.isValid() && IWCMonitor.this.isConnectedNetworkState()) {
                                                            IWCMonitor.this.mSNSDisablePolicy.detect();
                                                            isDetected = true;
                                                        }
                                                    } else {
                                                        Log.d(IWCMonitor.TAG, "SNS Option changed 1->2");
                                                    }
                                                } else if (!IWCMonitor.this.mCurSNS || IWCMonitor.this.mCurAGG) {
                                                    if (newsns && newsns) {
                                                        Log.d(IWCMonitor.TAG, "SNS Option changed 3->1 mDoingRestore: " + IWCMonitor.this.mDoingRestore);
                                                        if (!IWCMonitor.this.mDoingRestore && IWCMonitor.this.mSNSEnablePolicy.isValid() && IWCMonitor.this.isConnectedNetworkState()) {
                                                            IWCMonitor.this.mSNSEnablePolicy.detect();
                                                            isDetected = true;
                                                        }
                                                        isNeedCheckToggleCnt = true;
                                                    } else if (!newsns || newsns) {
                                                        Log.d(IWCMonitor.TAG, "SNS Option changed 3->3");
                                                        isPrevCurSameValue = true;
                                                    } else {
                                                        Log.d(IWCMonitor.TAG, "SNS Option changed 3->2 mDoingRestore: " + IWCMonitor.this.mDoingRestore);
                                                        if (!IWCMonitor.this.mDoingRestore && IWCMonitor.this.mSNSEnablePolicy.isValid() && IWCMonitor.this.isConnectedNetworkState()) {
                                                            IWCMonitor.this.mSNSEnablePolicy.detect();
                                                            isDetected = true;
                                                        }
                                                    }
                                                } else if (newsns && newsns) {
                                                    Log.d(IWCMonitor.TAG, "SNS Option changed 2->1");
                                                } else if (!newsns || newsns) {
                                                    Log.d(IWCMonitor.TAG, "SNS Option changed 2->3 mDoingRestore: " + IWCMonitor.this.mDoingRestore);
                                                    if (!IWCMonitor.this.mDoingRestore && IWCMonitor.this.mSNSDisablePolicy.isValid() && IWCMonitor.this.isConnectedNetworkState()) {
                                                        IWCMonitor.this.mSNSDisablePolicy.detect();
                                                        isDetected = true;
                                                    }
                                                } else {
                                                    Log.d(IWCMonitor.TAG, "SNS Option changed 2->2");
                                                    isPrevCurSameValue = true;
                                                }
                                                IWCMonitor.this.mCurSNS = newsns;
                                                IWCMonitor.this.mCurAGG = newsns;
                                                IWCMonitor iWCMonitor6 = IWCMonitor.this;
                                                iWCMonitor6.mCurAGG = iWCMonitor6.mCurSNS;
                                                synchronized (IWCMonitor.this) {
                                                    IWCMonitor.this.mRLEngine.intf.snsFlag = IWCMonitor.this.mCurSNS;
                                                    IWCMonitor.this.mRLEngine.intf.aggSnsFlag = IWCMonitor.this.mCurAGG;
                                                }
                                                if (!isDetected && !isPrevCurSameValue && !IWCMonitor.this.mDoingRestore) {
                                                    IWCMonitor.this.mRLEngine.setDefaultQAI();
                                                }
                                                if (isNeedCheckToggleCnt && IWCMonitor.this.mCurSNS && !IWCMonitor.this.mDoingRestore) {
                                                    IWCMonitor.this.writeToLogFile("SNS option was enabled by user. reset training data", " NoVal");
                                                    Message.obtain(IWCMonitor.this.getHandler(), (int) IWCMonitor.FACTORY_RESET_REQUIRED).sendToTarget();
                                                    long lastTimeOn = System.currentTimeMillis();
                                                    Settings.Global.putLong(IWCMonitor.this.mContext.getContentResolver(), "wifi_iwc_last_time_switch_to_mobile_on", lastTimeOn);
                                                    Log.d(IWCMonitor.TAG, "WIFI_IWC_LAST_TIME_SWITCH_TO_MOBILE_ON is " + lastTimeOn);
                                                }
                                                IWCMonitor iWCMonitor7 = IWCMonitor.this;
                                                iWCMonitor7.mCurrentQAI = iWCMonitor7.getCurrentQAI();
                                                IWCMonitor iWCMonitor8 = IWCMonitor.this;
                                                iWCMonitor8.mIsSaver = iWCMonitor8.isSaver(iWCMonitor8.mCurrentQAI);
                                                if (IWCMonitor.this.isExcludedBssid()) {
                                                    IWCMonitor.this.sendMessageToWCM(WifiConnectivityMonitor.CMD_IWC_CURRENT_QAI, -1);
                                                } else {
                                                    IWCMonitor iWCMonitor9 = IWCMonitor.this;
                                                    if (iWCMonitor9.mCurSNS && IWCMonitor.this.isConnectedNetworkState()) {
                                                        i2 = IWCMonitor.this.mCurrentQAI;
                                                    }
                                                    iWCMonitor9.sendMessageToWCM(WifiConnectivityMonitor.CMD_IWC_CURRENT_QAI, i2);
                                                }
                                                Log.d(IWCMonitor.TAG, "Current sns button toggle cnt = " + Settings.Global.getInt(IWCMonitor.this.mContext.getContentResolver(), "wifi_num_of_switch_to_mobile_data_toggle", 0));
                                                if (IWCMonitor.this.mDoingRestore) {
                                                    IWCMonitor.this.mDoingRestore = false;
                                                    break;
                                                }
                                                break;
                                            case IWCMonitor.AGGRESSIVE_SETTINGS_CHANGED /*{ENCODED_INT: 552984}*/:
                                                if (IWCMonitor.DBG) {
                                                    Log.d(IWCMonitor.TAG, "AGGRESSIVE_SETTINGS_CHANGED " + msg.arg1);
                                                    break;
                                                }
                                                break;
                                            case IWCMonitor.IWC_WIFI_DISCONNECTED /*{ENCODED_INT: 552985}*/:
                                                int reason = msg.arg1;
                                                Log.e(IWCMonitor.TAG, "WiFi disconnected [reason = " + reason + "]");
                                                synchronized (IWCMonitor.this) {
                                                    IWCMonitor.this.mRLEngine.intf.switchFlag = false;
                                                }
                                                if (reason != 3) {
                                                    if (!IWCMonitor.this.isUnstableApReason(reason)) {
                                                        IWCMonitor.this.mLastDisconnectedReason = 1;
                                                        break;
                                                    } else {
                                                        IWCMonitor.this.mLastDisconnectedReason = 2;
                                                        break;
                                                    }
                                                } else {
                                                    IWCMonitor.this.mLastDisconnectedReason = 1;
                                                    break;
                                                }
                                            default:
                                                switch (i) {
                                                    case IWCMonitor.FACTORY_RESET_REQUIRED /*{ENCODED_INT: 552987}*/:
                                                        Log.e(IWCMonitor.TAG, "sync factory reset");
                                                        IWCMonitor.this.factoryReset();
                                                        if (IWCMonitor.this.isConnectedNetworkState()) {
                                                            IWCMonitor.this.sendDebugIntent(true);
                                                            IWCMonitor.this.mRLEngine.algorithmStep();
                                                            IWCMonitor.this.save_model_obj();
                                                            break;
                                                        }
                                                        break;
                                                    case IWCMonitor.START_FROM_BEGINNING /*{ENCODED_INT: 552988}*/:
                                                        long ts = ((Bundle) msg.obj).getLong("timestamp", 0);
                                                        Log.d(IWCMonitor.TAG, "Created time: " + ts);
                                                        Long tipsShowingDuration = Long.valueOf(IWCMonitor.this.getTimeStamp() - ts);
                                                        IWCMonitor.this.mRLEngine.intf.mBdTracking.setIdInfo(4);
                                                        IWCMonitor.this.mRLEngine.intf.mBdTracking.setTipsShowingDuration(tipsShowingDuration.longValue());
                                                        IWCMonitor.this.mRLEngine.intf.mBdTracking.setTipsClick(true);
                                                        IWCMonitor.this.setBigDataMIWC();
                                                        break;
                                                    case IWCMonitor.WIFI_OFF_EVENT_PENDED /*{ENCODED_INT: 552989}*/:
                                                        IWCMonitor.this.updateTableWifiOff();
                                                        IWCMonitor.this.setBigDataMIWC();
                                                        break;
                                                    case IWCMonitor.RESTORE_USER_PREFERENCE /*{ENCODED_INT: 552990}*/:
                                                        if (IWCMonitor.DBG) {
                                                            String type2 = new String();
                                                            if (msg.arg1 == 1) {
                                                                if (msg.arg2 <= 1) {
                                                                    if (msg.arg2 == 1) {
                                                                        type = "SNS" + " ON";
                                                                    } else {
                                                                        type = "SNS" + " OFF";
                                                                    }
                                                                    type2 = type + " (from old device)";
                                                                } else if (msg.arg2 == 3) {
                                                                    type2 = "SNS" + " ON";
                                                                } else {
                                                                    type2 = "SNS" + " OFF";
                                                                }
                                                            } else if (msg.arg1 == 2) {
                                                                if (msg.arg2 == 1) {
                                                                    type2 = "AGG";
                                                                } else {
                                                                    type2 = "NOT AGG";
                                                                }
                                                            } else if (msg.arg1 == 3) {
                                                                type2 = "Restoring learning file is required";
                                                            } else if (msg.arg1 == 4) {
                                                                type2 = "Switch to mobile data will be enabled after restore";
                                                            } else if (msg.arg1 == 5) {
                                                                type2 = "Toggled " + msg.arg2 + " time(s)";
                                                            }
                                                            Log.d(IWCMonitor.TAG, "RESTORE_USER_PREFERENCE: " + type2);
                                                        }
                                                        if (msg.arg1 != 1) {
                                                            if (msg.arg1 != 2) {
                                                                if (msg.arg1 != 3) {
                                                                    if (msg.arg1 == 4) {
                                                                        IWCMonitor.this.mDoingRestore = true;
                                                                        break;
                                                                    }
                                                                } else {
                                                                    IWCMonitor iWCMonitor10 = IWCMonitor.this;
                                                                    iWCMonitor10.setQtables(iWCMonitor10.readQtableFile(IWCMonitor.TEMP_IWC_QTABLE_RESTORE_FILE_PATH), true);
                                                                    break;
                                                                }
                                                            } else {
                                                                int previousSwitchToMobileAggValue = msg.arg2;
                                                                synchronized (IWCMonitor.this) {
                                                                    originalQai = IWCMonitor.this.mRLEngine.getQtables().mDefaultQAI;
                                                                }
                                                                if (originalQai == 2 && previousSwitchToMobileAggValue == 1) {
                                                                    IWCMonitor.this.writeToLogFile("WIFI_IWC_USER_DATA_PREFERENCE", String.valueOf(3));
                                                                    Message.obtain(IWCMonitor.this.getHandler(), IWCMonitor.RESET_LEARNING_DATA, 1, 0).sendToTarget();
                                                                    break;
                                                                }
                                                            }
                                                        } else {
                                                            int previousSwitchToMobileValue = msg.arg2;
                                                            if (previousSwitchToMobileValue != 1) {
                                                                if (previousSwitchToMobileValue == 0) {
                                                                    IWCMonitor.this.writeToLogFile("WIFI_IWC_USER_DATA_PREFERENCE", String.valueOf(0));
                                                                    Message.obtain(IWCMonitor.this.getHandler(), IWCMonitor.RESET_LEARNING_DATA, 3, 0).sendToTarget();
                                                                    break;
                                                                }
                                                            } else {
                                                                IWCMonitor.this.writeToLogFile("WIFI_IWC_USER_DATA_PREFERENCE", String.valueOf(1));
                                                                Message.obtain(IWCMonitor.this.getHandler(), IWCMonitor.RESET_LEARNING_DATA, 2, 0).sendToTarget();
                                                                break;
                                                            }
                                                        }
                                                        break;
                                                    case IWCMonitor.RESET_LEARNING_DATA /*{ENCODED_INT: 552991}*/:
                                                        if (msg.arg1 < 1 || 3 < msg.arg1) {
                                                            IWCMonitor.this.resetLearningData(-1);
                                                        } else {
                                                            IWCMonitor.this.resetLearningData(msg.arg1);
                                                        }
                                                        if (IWCMonitor.this.isConnectedNetworkState()) {
                                                            IWCMonitor.this.mRLEngine.algorithmStep();
                                                            IWCMonitor.this.updateDebugIntent();
                                                        }
                                                        IWCMonitor.this.save_model_obj();
                                                        break;
                                                    default:
                                                        switch (i) {
                                                            case IWCMonitor.REWARD_EVENT_MANUAL_SWITCH /*{ENCODED_INT: 553010}*/:
                                                                Log.d(IWCMonitor.TAG, "REWARD_EVENT_MANUAL_SWITCH_M +++");
                                                                IWCMonitor.this.updateTableManualSwitch(RewardEvent.MANUAL_SWITCH);
                                                                IWCMonitor.this.setBigDataMIWC();
                                                                return true;
                                                            case IWCMonitor.REWARD_EVENT_MANUAL_SWITCH_G /*{ENCODED_INT: 553011}*/:
                                                                Log.d(IWCMonitor.TAG, "REWARD_EVENT_MANUAL_SWITCH_G +");
                                                                IWCMonitor.this.updateTableManualSwitch(RewardEvent.MANUAL_SWITCH_G);
                                                                IWCMonitor.this.setBigDataMIWC();
                                                                return true;
                                                            case IWCMonitor.REWARD_EVENT_MANUAL_SWITCH_L /*{ENCODED_INT: 553012}*/:
                                                                Log.d(IWCMonitor.TAG, "REWARD_EVENT_MANUAL_SWITCH_L -");
                                                                IWCMonitor.this.updateTableManualSwitch(RewardEvent.MANUAL_SWITCH_L);
                                                                IWCMonitor.this.setBigDataMIWC();
                                                                return true;
                                                            case IWCMonitor.REWARD_EVENT_SWITCHED_TOO_SHORT /*{ENCODED_INT: 553013}*/:
                                                                Log.d(IWCMonitor.TAG, "REWARD_EVENT_SWITCHED_TOO_SHORT ---");
                                                                IWCMonitor.this.updateTableSwitchedTooShort();
                                                                IWCMonitor.this.setBigDataMIWC();
                                                                return true;
                                                            case IWCMonitor.REWARD_EVENT_WIFI_DISABLE /*{ENCODED_INT: 553014}*/:
                                                                Log.d(IWCMonitor.TAG, "REWARD_EVENT_WIFI_DISABLE (Rewarding Pending) +++");
                                                                IWCMonitor.this.sendMessageDelayed(IWCMonitor.WIFI_OFF_EVENT_PENDED, 3000);
                                                                return true;
                                                            case IWCMonitor.REWARD_EVENT_MOBILE_DATA_DISABLE /*{ENCODED_INT: 553015}*/:
                                                                Log.d(IWCMonitor.TAG, "REWARD_EVENT_MOBILE_DATA_DISABLE ---");
                                                                IWCMonitor.this.updateTableMobileDataChanged();
                                                                IWCMonitor.this.setBigDataMIWC();
                                                                return true;
                                                            case IWCMonitor.REWARD_EVENT_MANUAL_RECONNECT /*{ENCODED_INT: 553016}*/:
                                                                Log.d(IWCMonitor.TAG, "REWARD_EVENT_MANUAL_RECONNECT ---");
                                                                IWCMonitor.this.updateTableManualReconnect();
                                                                IWCMonitor.this.setBigDataMIWC();
                                                                return true;
                                                            case IWCMonitor.REWARD_EVENT_AUTO_DISCONNECTION /*{ENCODED_INT: 553017}*/:
                                                                Log.d(IWCMonitor.TAG, "REWARD_EVENT_AUTO_DISCONNECTION ===");
                                                                IWCMonitor.this.updateTableAutoDisconnection();
                                                                return true;
                                                            case IWCMonitor.REWARD_EVENT_MANUAL_DISCONNECT /*{ENCODED_INT: 553018}*/:
                                                                Log.d(IWCMonitor.TAG, "REWARD_EVENT_MANUAL_DISCONNECT +++");
                                                                IWCMonitor.this.updateTableManualDisc();
                                                                IWCMonitor.this.setBigDataMIWC();
                                                                return true;
                                                            case IWCMonitor.REWARD_EVENT_SNS_DISABLE /*{ENCODED_INT: 553019}*/:
                                                                Log.d(IWCMonitor.TAG, "REWARD_EVENT_SNS_DISABLE --");
                                                                IWCMonitor.this.updateTableSNSDisable();
                                                                IWCMonitor.this.setBigDataMIWC();
                                                                return true;
                                                            case IWCMonitor.REWARD_EVENT_SNS_ENABLE /*{ENCODED_INT: 553020}*/:
                                                                Log.d(IWCMonitor.TAG, "REWARD_EVENT_SNS_ENABLE +");
                                                                IWCMonitor.this.updateTableSNSEnable();
                                                                IWCMonitor.this.setBigDataMIWC();
                                                                return true;
                                                            default:
                                                                switch (i) {
                                                                    case IWCMonitor.EVENT_NETWORK_REMOVED /*{ENCODED_INT: 553036}*/:
                                                                        WifiConfiguration wifiConfiguration = (WifiConfiguration) msg.obj;
                                                                        break;
                                                                    case IWCMonitor.EVENT_INET_CONDITION_ACTION /*{ENCODED_INT: 553037}*/:
                                                                        if (IWCMonitor.DBG) {
                                                                            Log.d(IWCMonitor.TAG, "EVENT_INET_CONDITION_ACTION, NetworkType : " + msg.arg1 + ", valid : " + msg.arg2);
                                                                        }
                                                                        return true;
                                                                    default:
                                                                        Log.e(IWCMonitor.TAG, "Unhandled message " + msg + " in state " + IWCMonitor.this.getCurrentState().getName());
                                                                        break;
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                    } else {
                        IWCMonitor.this.writeToLogFile("FORCE_ACTION", String.valueOf(msg.arg1));
                        IWCMonitor.this.updateTableForceAction(msg.arg1);
                    }
                }
                return true;
            }
            Bundle args2 = (Bundle) msg.obj;
            IWCMonitor.this.mAutoReconnectNetworkId = args2.getInt("netId");
            if (args2.getInt("autoReconnect") == 1) {
                enable = true;
            }
            IWCMonitor.this.writeToLogFile("SEC_COMMAND_ID_SET_AUTO_RECONNECT", " " + enable + ", nid: " + IWCMonitor.this.mAutoReconnectNetworkId);
            if (!enable) {
                IWCMonitor.this.mManualDisconnectPolicy.adopt();
                IWCMonitor.this.mSwitchedTooShortPolicy.discard();
                IWCMonitor.this.mAutoDisconnectionPolicy.discard();
                IWCMonitor.this.mNetworkDisabledByAutoReconnect = true;
            }
            return true;
        }
    }

    /* access modifiers changed from: package-private */
    public class DisconnectedState extends State {
        DisconnectedState() {
        }

        public void enter() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " enter\n");
            }
            IWCMonitor iWCMonitor = IWCMonitor.this;
            iWCMonitor.mLastPoorLinkTimestampBeforeDisc = iWCMonitor.mLastPoorLinkTimestamp;
            IWCMonitor.this.mLastPoorLinkTimestamp = 0;
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, "mLastPoorLinkTimestampBeforeDisc: " + IWCMonitor.this.mLastPoorLinkTimestampBeforeDisc + " mLastDisconnectedReason: " + IWCMonitor.this.mLastDisconnectedReason + " mDisconnectToConnectNewNetwork: " + IWCMonitor.this.mDisconnectToConnectNewNetwork + " mLastConnectedBssid: " + IWCMonitor.this.mLastConnectedBssid);
            }
            if (IWCMonitor.this.mLastPoorLinkTimestampBeforeDisc > 0 && IWCMonitor.this.getTimeStamp() - IWCMonitor.this.mLastPoorLinkTimestampBeforeDisc < 20000 && (IWCMonitor.this.mLastDisconnectedReason == 2 || IWCMonitor.this.mDisconnectToConnectNewNetwork)) {
                IWCMonitor.this.mManualReconnectPolicy.adopt();
            }
            IWCMonitor.this.mAutoDisconnectionPolicy.discard();
            IWCMonitor.this.mSwitchedTooShortPolicy.discard();
        }

        public void exit() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " exit\n");
            }
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case WifiConnectivityMonitor.CMD_IWC_RSSI_FETCH_RESULT:
                    if (IWCMonitor.DBG) {
                        Log.d(IWCMonitor.TAG, " DisconnectedState: CMD_IWC_RSSI_FETCH_RESULT");
                    }
                    return true;
                case 151574:
                    if (IWCMonitor.DBG) {
                        Log.d(IWCMonitor.TAG, " DisconnectedState: RSSI_FETCH_FAILED");
                    }
                    return true;
                case IWCMonitor.TRANSIT_TO_VALID /*{ENCODED_INT: 552981}*/:
                    if (IWCMonitor.DBG) {
                        Log.d(IWCMonitor.TAG, " DisconnectedState: TRANSIT_TO_VALID");
                    }
                    IWCMonitor iWCMonitor = IWCMonitor.this;
                    iWCMonitor.transitionTo(iWCMonitor.mGoodLinkState);
                    return true;
                case IWCMonitor.TRANSIT_TO_INVALID /*{ENCODED_INT: 552982}*/:
                    if (IWCMonitor.DBG) {
                        Log.d(IWCMonitor.TAG, " DisconnectedState: TRANSIT_TO_INVALID");
                    }
                    IWCMonitor iWCMonitor2 = IWCMonitor.this;
                    iWCMonitor2.transitionTo(iWCMonitor2.mInvalidState);
                    return true;
                default:
                    return false;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public class ConnectedState extends State {
        ConnectedState() {
        }

        public void enter() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " enter\n");
            }
            if (IWCMonitor.this.mCurrentWifiInfo == null) {
                IWCMonitor iWCMonitor = IWCMonitor.this;
                iWCMonitor.mCurrentWifiInfo = iWCMonitor.syncGetCurrentWifiInfo();
            }
            IWCMonitor.this.mGoodLinkTargetCount = 0;
            IWCMonitor.this.mGoodLinkTargetRssi = 0;
            synchronized (IWCMonitor.this) {
                IWCMonitor.this.mRLEngine.intf.switchFlag = false;
            }
        }

        public void exit() {
            IWCMonitor.this.mPoorLinkStateTesting = false;
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " exit\n");
            }
        }

        public boolean processMessage(Message msg) {
            boolean z = false;
            switch (msg.what) {
                case WifiConnectivityMonitor.CMD_IWC_RSSI_FETCH_RESULT:
                    if (IWCMonitor.DBG) {
                        Log.d(IWCMonitor.TAG, " ConnectedState: CMD_IWC_RSSI_FETCH_RESULT");
                    }
                    return true;
                case 151574:
                    if (IWCMonitor.DBG) {
                        Log.d(IWCMonitor.TAG, " ConnectedState: RSSI_FETCH_FAILED");
                    }
                    return true;
                case IWCMonitor.TRANSIT_TO_VALID /*{ENCODED_INT: 552981}*/:
                    if (IWCMonitor.DBG) {
                        Log.d(IWCMonitor.TAG, " ConnectedState: TRANSIT_TO_VALID");
                    }
                    IWCMonitor iWCMonitor = IWCMonitor.this;
                    iWCMonitor.transitionTo(iWCMonitor.mGoodLinkState);
                    return true;
                case IWCMonitor.TRANSIT_TO_INVALID /*{ENCODED_INT: 552982}*/:
                    if (IWCMonitor.DBG) {
                        Log.d(IWCMonitor.TAG, " ConnectedState: TRANSIT_TO_INVALID");
                    }
                    IWCMonitor iWCMonitor2 = IWCMonitor.this;
                    iWCMonitor2.transitionTo(iWCMonitor2.mInvalidState);
                    return true;
                case IWCMonitor.EVENT_WIFI_TOGGLED /*{ENCODED_INT: 553035}*/:
                    IWCMonitor iWCMonitor3 = IWCMonitor.this;
                    if (msg.arg1 == 1) {
                        z = true;
                    }
                    iWCMonitor3.mIsWifiEnabled = z;
                    if (!IWCMonitor.this.mIsWifiEnabled) {
                        IWCMonitor iWCMonitor4 = IWCMonitor.this;
                        iWCMonitor4.transitionTo(iWCMonitor4.mDisconnectedState);
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    class ValidState extends State {
        ValidState() {
        }

        public void enter() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " enter\n");
            }
            if (IWCMonitor.this.mCurrentWifiInfo == null) {
                IWCMonitor iWCMonitor = IWCMonitor.this;
                iWCMonitor.mCurrentWifiInfo = iWCMonitor.syncGetCurrentWifiInfo();
            }
            synchronized (IWCMonitor.this) {
                IWCMonitor.this.mValidLastRssi = IWCMonitor.this.mCurrentWifiInfo.getNetworkId() != -1 ? IWCMonitor.this.mCurrentWifiInfo.getRssi() : -64;
            }
            IWCMonitor.this.mLinkLossOccurred = 0;
            IWCMonitor.this.mPreviousLinkLoss = 0;
            IWCMonitor.this.mLossSampleCount = 0;
            IWCMonitor.this.mLossHasGone = 0;
            IWCMonitor.this.mPreviousLoss = 0.0d;
            if (IWCMonitor.this.mIwcDataRateTraceData != null) {
                IWCMonitor.this.mIwcDataRateTraceData.resetBufsAndCnts();
            }
            IWCMonitor.this.mSaverState = 0;
            IWCMonitor iWCMonitor2 = IWCMonitor.this;
            iWCMonitor2.sendDebugIntent(iWCMonitor2.mIsSaver, IWCMonitor.this.mSaverState, IWCMonitor.this.mCurrentQAI);
        }

        public void exit() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " exit\n");
            }
            if (IWCMonitor.this.mIwcDataRateTraceData != null) {
                IWCMonitor.this.mIwcDataRateTraceData.resetBufsAndCnts();
            }
            if (IWCMonitor.this.mIsSaver && IWCMonitor.this.mSaverState != 0) {
                IWCMonitor.this.unsetDQAI();
            }
        }

        public boolean processMessage(Message msg) {
            int mrssi;
            int i = msg.what;
            if (i == 135373) {
                IWCMonitor iWCMonitor = IWCMonitor.this;
                iWCMonitor.mCurrentWifiInfo = iWCMonitor.syncGetCurrentWifiInfo();
                int rssi = IWCMonitor.this.mCurrentWifiInfo.getRssi();
                synchronized (IWCMonitor.this) {
                    mrssi = (IWCMonitor.this.mValidLastRssi + rssi) / 2;
                }
                int txbad = msg.arg1;
                int txgood = msg.arg2;
                TrafficStats.getRxPackets("wlan0");
                IWCMonitor iWCMonitor2 = IWCMonitor.this;
                iWCMonitor2.mPreviousRssi = iWCMonitor2.mCurrentRssi;
                IWCMonitor.this.mCurrentRssi = rssi;
                if (IWCMonitor.RSSI_DBG) {
                    Log.d(IWCMonitor.TAG, "Fetch RSSI succeed, rssi=" + rssi + " mrssi=" + mrssi + " txbad=" + txbad + " txgood=" + txgood);
                }
                long now = SystemClock.elapsedRealtime();
                if (now - IWCMonitor.this.mLastTimeSample < IWCMonitor.LINK_SAMPLING_INTERVAL_MS * 2) {
                    int dbad = txbad - IWCMonitor.this.mLastTxBad;
                    int dtotal = dbad + (txgood - IWCMonitor.this.mLastTxGood);
                    if (IWCMonitor.this.mPingEnabled && rssi < -65) {
                        if (IWCMonitor.DBG) {
                            Log.d(IWCMonitor.TAG, "Start ping to gateway");
                        }
                        IWCMonitor.this.pingToGateway(true);
                    }
                    if (dtotal > 0) {
                        double loss = ((double) dbad) / ((double) dtotal);
                        IWCMonitor iWCMonitor3 = IWCMonitor.this;
                        iWCMonitor3.mPreviousLinkLoss = iWCMonitor3.mLinkLossOccurred;
                        if (IWCMonitor.this.mPoorLinkStateTesting) {
                            IWCMonitor.access$2808(IWCMonitor.this);
                        } else if (IWCMonitor.this.mIsScanning) {
                            if (IWCMonitor.RSSI_DBG) {
                                Log.i(IWCMonitor.TAG, "@IWC - Scanning");
                            }
                            IWCMonitor.this.mLinkLossOccurred = 0;
                        } else if ((!IWCMonitor.this.mCurrentWifiInfo.is5GHz() || IWCMonitor.this.mCurrentWifiInfo.getRssi() <= -70) && IWCMonitor.this.mCurrentWifiInfo.getRssi() <= -64) {
                            if (dbad >= 30 || loss >= IWCMonitor.EXP_COEFFICIENT_MONITOR) {
                                if (IWCMonitor.RSSI_DBG) {
                                    Log.e(IWCMonitor.TAG, "@IWC - (dbad >= 30) || (loss >= 0.5)");
                                }
                                IWCMonitor.access$2808(IWCMonitor.this);
                                if (loss >= IWCMonitor.EXP_COEFFICIENT_MONITOR && dbad >= 5) {
                                    IWCMonitor.access$2808(IWCMonitor.this);
                                    if (IWCMonitor.RSSI_DBG) {
                                        Log.e(IWCMonitor.TAG, "@IWC - (loss >= 0.5) && (dbad >= 5)");
                                    }
                                }
                            } else if (dbad > 4 && loss >= 0.1d) {
                                if (IWCMonitor.RSSI_DBG) {
                                    Log.e(IWCMonitor.TAG, "@IWC - (dbad > 4)&&(loss >= 0.1)");
                                }
                                IWCMonitor.access$2808(IWCMonitor.this);
                            } else if (mrssi < -65 && IWCMonitor.this.mCurrentWifiInfo.is24GHz() && (dbad > 4 || loss >= 0.1d)) {
                                if (IWCMonitor.RSSI_DBG) {
                                    Log.e(IWCMonitor.TAG, "@IWC - rssi < -65) && (mCurrentWifiInfo.is24GHz()) && ((dbad > 4)||(loss >= 0.1))");
                                }
                                IWCMonitor.access$2808(IWCMonitor.this);
                            } else if (IWCMonitor.this.mCurrentWifiInfo.getLinkSpeed() <= 6 && loss >= 0.1d) {
                                if (IWCMonitor.RSSI_DBG) {
                                    Log.e(IWCMonitor.TAG, "@IWC - (mCurrentWifiInfo.getLinkSpeed() <= 6) && (loss >= 0.1)");
                                }
                                IWCMonitor.access$2808(IWCMonitor.this);
                            } else if (IWCMonitor.this.mLossHasGone == 0 && loss > IWCMonitor.this.mPreviousLoss && IWCMonitor.this.mPreviousLoss >= 0.1d) {
                                if (IWCMonitor.RSSI_DBG) {
                                    Log.e(IWCMonitor.TAG, "@IWC - loss increasing");
                                }
                                IWCMonitor.access$2808(IWCMonitor.this);
                            } else if (dbad > 0) {
                                if (IWCMonitor.this.mLinkLossOccurred == 0) {
                                    if (IWCMonitor.RSSI_DBG) {
                                        Log.e(IWCMonitor.TAG, "@IWC - loss begin occurring");
                                    }
                                    IWCMonitor.access$2808(IWCMonitor.this);
                                } else if (IWCMonitor.RSSI_DBG) {
                                    Log.i(IWCMonitor.TAG, "@IWC - loss still can be seen, keep the value!");
                                }
                            }
                            if (dbad != 0) {
                                IWCMonitor.this.mLossHasGone = 0;
                                IWCMonitor.this.mPreviousLoss = loss;
                            } else if (IWCMonitor.access$3104(IWCMonitor.this) > 1) {
                                if (IWCMonitor.RSSI_DBG) {
                                    Log.i(IWCMonitor.TAG, "@IWC - loss has gone");
                                }
                                IWCMonitor.this.mLinkLossOccurred = 0;
                                IWCMonitor.this.mLossHasGone = 0;
                                IWCMonitor.this.mPreviousLoss = 0.0d;
                            }
                        } else {
                            if (IWCMonitor.RSSI_DBG) {
                                Log.i(IWCMonitor.TAG, "@IWC - Good Rx state");
                            }
                            IWCMonitor.this.mLinkLossOccurred = 0;
                            IWCMonitor.this.mLossHasGone = 0;
                        }
                        if (IWCMonitor.RSSI_DBG) {
                            Log.i(IWCMonitor.TAG, "@IWC - mLinkLossOccurred-" + IWCMonitor.this.mLinkLossOccurred + " mLossHasGone-" + IWCMonitor.this.mLossHasGone + " mPreviousLoss-" + IWCMonitor.this.mPreviousLoss);
                        }
                        if (IWCMonitor.this.mPreviousLinkLoss != IWCMonitor.this.mLinkLossOccurred || (IWCMonitor.this.getCurrentState() == IWCMonitor.this.mPoorLinkState && IWCMonitor.this.mLinkLossOccurred == 0)) {
                            IWCMonitor.this.updateLinkLossNotification();
                        }
                    }
                }
                IWCMonitor.this.mLastTimeSample = now;
                IWCMonitor.this.mLastTxBad = txbad;
                IWCMonitor.this.mLastTxGood = txgood;
                synchronized (IWCMonitor.this) {
                    IWCMonitor.this.mValidLastRssi = rssi;
                }
                return true;
            } else if (i != 135376) {
                return false;
            } else {
                if (IWCMonitor.this.mIsSaver && IWCMonitor.this.mIwcDataRateTraceData != null) {
                    long preTxBytes = IWCMonitor.this.mTxBytes;
                    long preRxbyte = IWCMonitor.this.mRxBytes;
                    long now2 = SystemClock.elapsedRealtime();
                    IWCMonitor.this.mTxBytes = TrafficStats.getTxBytes("wlan0");
                    IWCMonitor.this.mRxBytes = TrafficStats.getRxBytes("wlan0");
                    if (now2 - IWCMonitor.this.mLastTimeSampleForTraffic < IWCMonitor.LINK_SAMPLING_INTERVAL_MS * 2) {
                        long diffRxBytes = IWCMonitor.this.mRxBytes - preRxbyte;
                        long diffTxBytes = IWCMonitor.this.mTxBytes - preTxBytes;
                        if (IWCMonitor.RSSI_DBG || IWCMonitor.this.mSaverState != 0) {
                            Log.d(IWCMonitor.TAG, "diffTxBytes : " + diffTxBytes + ", diffRxBytes : " + diffRxBytes);
                        }
                        int newState = IWCMonitor.this.mIwcDataRateTraceData.getNextState(IWCMonitor.this.mSaverState, diffTxBytes, diffRxBytes);
                        if (IWCMonitor.this.mSaverState != newState) {
                            if (newState > 0 && IWCMonitor.this.mSaverState == 0) {
                                IWCMonitor.this.setDQAI(newState);
                            } else if (newState == 0) {
                                IWCMonitor.this.unsetDQAI();
                            }
                        }
                    } else {
                        Log.d(IWCMonitor.TAG, "resetBufsAndCnts");
                        IWCMonitor.this.mIwcDataRateTraceData.resetBufsAndCnts();
                        if (IWCMonitor.this.mSaverState != 0) {
                            IWCMonitor.this.unsetDQAI();
                        }
                    }
                    IWCMonitor.this.mLastTimeSampleForTraffic = now2;
                }
                return true;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public class InvalidState extends State {
        InvalidState() {
        }

        public void enter() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " enter\n");
            }
            if (IWCMonitor.this.mCurrentWifiInfo == null) {
                IWCMonitor iWCMonitor = IWCMonitor.this;
                iWCMonitor.mCurrentWifiInfo = iWCMonitor.syncGetCurrentWifiInfo();
            }
            synchronized (IWCMonitor.this) {
                IWCMonitor.this.mValidLastRssi = IWCMonitor.this.mCurrentWifiInfo.getNetworkId() != -1 ? IWCMonitor.this.mCurrentWifiInfo.getRssi() : -83;
            }
            IWCMonitor.this.mLastTxBad = -1;
            IWCMonitor.this.mLastTxGood = -1;
            IWCMonitor.this.mGoodLinkTargetCount = 0;
            synchronized (IWCMonitor.this) {
                IWCMonitor.this.mRLEngine.intf.switchFlag = true;
            }
            if (IWCMonitor.this.mSwitchedTooShortPolicy.isValid()) {
                IWCMonitor iWCMonitor2 = IWCMonitor.this;
                if (iWCMonitor2.isUserInteraction(iWCMonitor2.mConnectNetworkPackageName)) {
                    IWCMonitor iWCMonitor3 = IWCMonitor.this;
                    if (!iWCMonitor3.isUserInteraction(iWCMonitor3.mRemoveNetworkPackageName)) {
                        IWCMonitor iWCMonitor4 = IWCMonitor.this;
                        boolean vendor2 = iWCMonitor4.isVendorAp(iWCMonitor4.mCurrentNetworkId);
                        IWCMonitor iWCMonitor5 = IWCMonitor.this;
                        boolean samsung = iWCMonitor5.isSamsungSpecificAp(iWCMonitor5.mCurrentNetworkId, IWCMonitor.this.mCurrentBssid);
                        IWCMonitor iWCMonitor6 = IWCMonitor.this;
                        iWCMonitor6.writeToLogFile("InvalidState", "mDisconnectToConnectNewNetwork: " + IWCMonitor.this.mDisconnectToConnectNewNetwork + " vendor: " + vendor2 + " samsung: " + samsung);
                        if (!IWCMonitor.this.mDisconnectToConnectNewNetwork && !vendor2 && !samsung) {
                            IWCMonitor.this.mConnectNetworkPackageName = null;
                            IWCMonitor.this.mSwitchedTooShortPolicy.detect();
                            IWCMonitor.this.mSwitchedTooShortPolicy.discard();
                        }
                        IWCMonitor iWCMonitor7 = IWCMonitor.this;
                        iWCMonitor7.mLastInvalidEnterTimestamp = iWCMonitor7.getTimeStamp();
                    }
                }
            }
            IWCMonitor.this.detectRewardEventAutoDisconnection();
            IWCMonitor iWCMonitor72 = IWCMonitor.this;
            iWCMonitor72.mLastInvalidEnterTimestamp = iWCMonitor72.getTimeStamp();
        }

        public void exit() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " exit\n");
            }
            IWCMonitor iWCMonitor = IWCMonitor.this;
            iWCMonitor.mLastPoorLinkTimestamp = iWCMonitor.getTimeStamp();
        }

        public boolean processMessage(Message msg) {
            int mrssi;
            if (msg.what != 135373) {
                return false;
            }
            int rssi = IWCMonitor.this.mCurrentWifiInfo.getRssi();
            synchronized (IWCMonitor.this) {
                mrssi = (IWCMonitor.this.mValidLastRssi + rssi) / 2;
            }
            int txbad = msg.arg1;
            int txgood = msg.arg2;
            IWCMonitor iWCMonitor = IWCMonitor.this;
            iWCMonitor.mPreviousRssi = iWCMonitor.mCurrentRssi;
            IWCMonitor.this.mCurrentRssi = rssi;
            if (IWCMonitor.RSSI_DBG) {
                Log.d(IWCMonitor.TAG, "[Invalid] Fetch RSSI succeed, rssi=" + rssi + " mrssi=" + mrssi + " txbad=" + txbad + " txgood=" + txgood);
            }
            if (IWCMonitor.this.mPingEnabled && rssi < -65) {
                if (IWCMonitor.DBG) {
                    Log.d(IWCMonitor.TAG, "Start ping to gateway");
                }
                IWCMonitor.this.pingToGateway(true);
            }
            IWCMonitor.this.mLastTimeSample = SystemClock.elapsedRealtime();
            IWCMonitor.this.mLastTxBad = txbad;
            IWCMonitor.this.mLastTxGood = txgood;
            synchronized (IWCMonitor.this) {
                IWCMonitor.this.mValidLastRssi = rssi;
            }
            return true;
        }
    }

    /* access modifiers changed from: package-private */
    public class GoodLinkState extends State {
        GoodLinkState() {
        }

        public void enter() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " enter\n");
            }
            if (IWCMonitor.MISC_DBG) {
                Toast.makeText(IWCMonitor.this.mContext, "IWC enter GoodLink State", 0).show();
            }
            synchronized (IWCMonitor.this) {
                IWCMonitor.this.timeStampPoorLinkTrig = IWCMonitor.this.getTimeStamp();
                IWCMonitor.this.mRLEngine.intf.switchFlag = false;
                IWCMonitor.this.mRLEngine.intf.currentApBssid_IN = IWCMonitor.this.mCurrentBssid;
                IWCMonitor.this.mRLEngine.intf.capRSSI = IWCMonitor.this.mCurrentRssi;
                IWCMonitor iWCMonitor = IWCMonitor.this;
                iWCMonitor.writeToLogFile("Good Link, timestamp =", String.valueOf(IWCMonitor.this.timeStampPoorLinkTrig) + ", RSSI:" + String.valueOf(IWCMonitor.this.mRLEngine.intf.capRSSI) + ", AP:" + IWCMonitor.this.mRLEngine.intf.currentApBssid_IN);
                IWCMonitor.this.mRLEngine.updateQAI();
            }
            IWCMonitor.this.mManualSwitchPolicy.discard();
            IWCMonitor.this.mManualDisconnectPolicy.discard();
            IWCMonitor.this.mLastInvalidEnterTimestamp = 0;
            IWCMonitor.this.sendDebugIntent(false);
        }

        public void exit() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " exit\n");
            }
        }

        public boolean processMessage(Message msg) {
            return false;
        }
    }

    /* access modifiers changed from: package-private */
    public class PoorLinkState extends State {
        PoorLinkState() {
        }

        public void enter() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " enter\n");
            }
            if (IWCMonitor.MISC_DBG) {
                Toast.makeText(IWCMonitor.this.mContext, "IWC enter PoorLink State", 0).show();
            }
            IWCMonitor.this.mLastInvalidEnterTimestamp = 0;
            if (IWCMonitor.this.mSaverState != 0) {
                IWCMonitor iWCMonitor = IWCMonitor.this;
                iWCMonitor.mSavedBytes = iWCMonitor.mTxBytes + IWCMonitor.this.mRxBytes;
                return;
            }
            IWCMonitor.this.mSavedBytes = 0;
        }

        public void exit() {
            if (IWCMonitor.DBG) {
                Log.d(IWCMonitor.TAG, getName() + " exit\n");
            }
            IWCMonitor iWCMonitor = IWCMonitor.this;
            iWCMonitor.mLastPoorLinkTimestamp = iWCMonitor.getTimeStamp();
            if (IWCMonitor.this.mSaverState != 0 && IWCMonitor.this.mSavedBytes > 0 && IWCMonitor.this.mSavedBytes < IWCMonitor.this.mTxBytes + IWCMonitor.this.mRxBytes) {
                IWCMonitor iWCMonitor2 = IWCMonitor.this;
                iWCMonitor2.mSavedBytes = (iWCMonitor2.mTxBytes + IWCMonitor.this.mRxBytes) - IWCMonitor.this.mSavedBytes;
                synchronized (IWCMonitor.this) {
                    if (IWCMonitor.this.mRLEngine.intf.mBdTracking != null) {
                        IWCMonitor.this.mRLEngine.intf.mBdTracking.setSavedBytes(IWCMonitor.this.mSavedBytes);
                        IWCMonitor iWCMonitor3 = IWCMonitor.this;
                        iWCMonitor3.writeToLogFile("DYNAMIC-QAI: ", "Saved data - " + IWCMonitor.this.mSavedBytes + "Bytes");
                    }
                }
            }
            IWCMonitor.this.mSavedBytes = 0;
        }

        public boolean processMessage(Message msg) {
            int i = msg.what;
            return false;
        }
    }

    public Messenger getIWCMessenger() {
        Log.d(TAG, "getIWCMessenger");
        return new Messenger(getHandler());
    }

    public void setWcmAsyncChannel(Handler dst) {
        if (this.mWcmChannel == null) {
            if (DBG) {
                Log.i(TAG, "New mWcmChannel created");
            }
            this.mWcmChannel = new AsyncChannel();
        }
        this.mWcmChannel.connectSync(this.mContext, getHandler(), dst);
        if (DBG) {
            Log.i(TAG, "mWcmChannel connected");
        }
    }

    /* access modifiers changed from: package-private */
    public class RewardEventDetectionPolicy implements IWCPolicy {
        private Handler handler;
        private boolean isValid;
        private AlarmManager.OnAlarmListener mAlarmListener;
        private RewardEvent name;
        private long timer;
        private int type;

        private RewardEventDetectionPolicy(Handler h, RewardEvent name2, int type2, long timerInMiliseconds) {
            this.name = name2;
            this.type = type2;
            this.timer = timerInMiliseconds;
            this.handler = h;
            this.mAlarmListener = new AlarmManager.OnAlarmListener(IWCMonitor.this) {
                /* class com.android.server.wifi.IWCMonitor.RewardEventDetectionPolicy.C03991 */

                public void onAlarm() {
                    if (IWCMonitor.DBG) {
                        String tag = RewardEventDetectionPolicy.this.getTag();
                        Log.w(tag, "Timer( " + RewardEventDetectionPolicy.this.timer + ") is expired");
                    }
                    RewardEventDetectionPolicy.this.discard();
                }
            };
        }

        public void setAlarmListener(AlarmManager.OnAlarmListener listener) {
            this.mAlarmListener = listener;
        }

        public String getTag() {
            return "IWCMonitor." + this.name.name();
        }

        public void setTimer(int timerInMiliseconds) {
            this.timer = (long) timerInMiliseconds;
        }

        public long getTimer() {
            return this.timer;
        }

        @Override // com.android.server.wifi.iwc.IWCPolicy
        public void adopt() {
            if (this.isValid) {
                IWCMonitor.this.mAlarmManager.cancel(this.mAlarmListener);
            }
            if (IWCMonitor.DBG) {
                String tag = getTag();
                Log.i(tag, "adopted (timer will be expired in " + this.timer + ")");
            }
            if (this.timer != 0) {
                IWCMonitor.this.mAlarmManager.set(2, this.timer + SystemClock.elapsedRealtime(), this.name.name(), this.mAlarmListener, this.handler);
            }
            this.isValid = true;
        }

        public void adopt(int timerInMiliseconds) {
            this.timer = (long) timerInMiliseconds;
            adopt();
        }

        @Override // com.android.server.wifi.iwc.IWCPolicy
        public void discard() {
            this.isValid = false;
            if (this.timer != 0) {
                IWCMonitor.this.mAlarmManager.cancel(this.mAlarmListener);
            }
            if (IWCMonitor.DBG) {
                Log.d(getTag(), "discard");
            }
        }

        private boolean skipDetection() {
            if (IWCMonitor.this.isExcludedBssid()) {
                return true;
            }
            if (!IWCMonitor.DBG) {
                return false;
            }
            Log.e(getTag(), "detected");
            return false;
        }

        @Override // com.android.server.wifi.iwc.IWCPolicy
        public void detect() {
            if (!skipDetection() && this.isValid) {
                Log.d(IWCMonitor.TAG, "switchflag=" + IWCMonitor.this.mRLEngine.intf.switchFlag);
                Message.obtain(this.handler, this.type).sendToTarget();
                if (IWCMonitor.MISC_DBG) {
                    Context context = IWCMonitor.this.mContext;
                    Toast.makeText(context, "IWC Action Detected - " + this.name.name() + " switchflag=" + IWCMonitor.this.mRLEngine.intf.switchFlag, 0).show();
                }
            }
        }

        public void detect(RewardEvent event) {
            String msg;
            int manualSwitchMsgType;
            if (!skipDetection() && this.isValid) {
                Log.d(IWCMonitor.TAG, "switchflag=" + IWCMonitor.this.mRLEngine.intf.switchFlag);
                new String();
                int i = C03987.$SwitchMap$com$android$server$wifi$iwc$RewardEvent[event.ordinal()];
                if (i == 1) {
                    manualSwitchMsgType = IWCMonitor.REWARD_EVENT_MANUAL_SWITCH;
                    msg = "MANUAL_SWITCH";
                } else if (i == 2) {
                    manualSwitchMsgType = IWCMonitor.REWARD_EVENT_MANUAL_SWITCH_G;
                    msg = "MANUAL_SWITCH_G";
                } else if (i != 3) {
                    Log.e(IWCMonitor.TAG, "Undefined event: " + event.name());
                    return;
                } else {
                    manualSwitchMsgType = IWCMonitor.REWARD_EVENT_MANUAL_SWITCH_L;
                    msg = "MANUAL_SWITCH_L";
                }
                Message.obtain(this.handler, manualSwitchMsgType).sendToTarget();
                IWCMonitor.this.mLastInvalidEnterTimestamp = 0;
                if (IWCMonitor.MISC_DBG) {
                    Context context = IWCMonitor.this.mContext;
                    Toast.makeText(context, "IWC Action Detected - " + msg + " switchflag=" + IWCMonitor.this.mRLEngine.intf.switchFlag, 0).show();
                }
            }
        }

        @Override // com.android.server.wifi.iwc.IWCPolicy
        public boolean isValid() {
            return this.isValid;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private WifiInfo syncGetCurrentWifiInfo() {
        WifiInfo wifiInfo = this.mClientModeImpl.getWifiInfo();
        WifiInfo info = null;
        if (wifiInfo != null) {
            info = new WifiInfo(wifiInfo);
        }
        if (info != null) {
            return info;
        }
        Log.e(TAG, "WifiInfo is null");
        WifiInfo info2 = sDummyWifiInfo;
        this.mCurrentRssi = -100;
        return info2;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isConnectedNetworkState() {
        NetworkInfo networkInfo = this.mClientModeImpl.syncGetNetworkInfo();
        if (networkInfo.getDetailedState() == NetworkInfo.DetailedState.VERIFYING_POOR_LINK || networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
            return true;
        }
        return false;
    }

    private long getDiffFromLastPoorLink() {
        return getTimeStamp() - this.mLastPoorLinkTimestamp;
    }

    public long getTimeStamp() {
        return System.currentTimeMillis();
    }

    public void writeToLogFile(String valueName, String value) {
        IWCLogFile iWCLogFile = this.mLogFile;
        if (iWCLogFile != null) {
            iWCLogFile.writeToLogFile(valueName, value);
        }
    }

    public String intToIp(int i) {
        return (i & 255) + "." + ((i >> 8) & 255) + "." + ((i >> 16) & 255) + "." + ((i >> 24) & 255);
    }

    /* access modifiers changed from: package-private */
    /* JADX WARNING: Code restructure failed: missing block: B:8:?, code lost:
        return;
     */
    /* JADX WARNING: Exception block dominator not found, dom blocks: [] */
    public void pingToGateway(boolean start) {
        if (start) {
            DhcpInfo d = this.mWifiManager.getDhcpInfo();
            Runtime runtime = Runtime.getRuntime();
            runtime.exec("/system/bin/ping -c 10 -s 1500 " + intToIp(d.gateway));
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isUnstableApReason(int disconnectReason) {
        if (disconnectReason == 0 || disconnectReason == 2 || disconnectReason == 4 || disconnectReason == 5 || disconnectReason == 6 || disconnectReason == 7 || disconnectReason == 10 || disconnectReason == 11 || disconnectReason == 13 || disconnectReason == 14 || disconnectReason == 15 || disconnectReason == 16 || disconnectReason == 17 || disconnectReason == 18 || disconnectReason == 19 || disconnectReason == 20 || disconnectReason == 21 || disconnectReason == 22 || disconnectReason == 34 || disconnectReason == 100 || disconnectReason == 193) {
            return true;
        }
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void load_model_obj() {
        long start = getTimeStamp();
        QTableContainerBuilder builder = new QTableContainerBuilder();
        try {
            builder.setIWCJson(this.mPreferenceFile.readFile()).setIWCLogFile(this.mLogFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.mRLEngine.setQtables(builder.create());
        writeToLogFile("load_model_obj ", "success to load_model_obj(org.json) in " + Double.toString(((double) (getTimeStamp() - start)) / 1000.0d));
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void save_model_obj() {
        long start = getTimeStamp();
        try {
            this.mPreferenceFile.writeData(new QTableContainerBuilder().toJsonString(this.mRLEngine.getQtables()));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        writeToLogFile("save_model_obj ", "success to save_model_obj(org.json) in " + Double.toString(((double) (getTimeStamp() - start)) / 1000.0d));
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateDebugIntent() {
        String str = this.mCurrentBssid;
        if (str != null && str.equals(this.mRLEngine.intf.currentApBssid_IN)) {
            this.mRLEngine.updateDebugIntent(RewardEvent.NO_EVENT, this.mCurrentBssid, false);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendDebugIntent(boolean isPoor) {
        Intent intent = new Intent("com.sec.android.IWC_REWARD_EVENT_DEBUG");
        intent.putExtra("kind", 2);
        intent.putExtra("event", WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE);
        intent.putExtra("bssid", WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE);
        intent.putExtra("tableindex", -1);
        intent.putExtra("lastvalue1", -1);
        intent.putExtra("lastvalue2", -1);
        intent.putExtra("lastvalue3", -1);
        intent.putExtra("ss_poor", isPoor);
        intent.putExtra("qai", -1);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendDebugIntent(boolean isSaver, int state, int qai) {
        Intent intent = new Intent("com.sec.android.IWC_REWARD_EVENT_DEBUG");
        intent.putExtra("kind", 3);
        intent.putExtra("saver", isSaver);
        intent.putExtra("dqai", state);
        intent.putExtra("qai", qai);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void updateLinkLossNotification() {
        Log.e(TAG, "poor link count is " + this.mLinkLossOccurred);
        if (this.mLinkLossOccurred == 0 && (this.mPreviousLinkLoss > 0 || getCurrentState() == this.mPoorLinkState)) {
            writeToLogFile("Good Link, timestamp =", String.valueOf(this.timeStampPoorLinkTrig) + "  RSSI:" + String.valueOf(this.mRLEngine.intf.capRSSI) + "  AP:" + this.mRLEngine.intf.currentApBssid_IN + String.format(" Direction: %d Direction (soft): %d", -1, -1));
            sendDebugIntent(false);
            transitionTo(this.mGoodLinkState);
        } else if (this.mLinkLossOccurred != 0) {
            Log.e(TAG, String.format("[POOR_LINK_DETECTED] %d -> %d", Integer.valueOf(this.mPreviousLinkLoss), Integer.valueOf(this.mLinkLossOccurred)));
            this.mLastPoorLinkTimestamp = getTimeStamp();
            this.timeStampPoorLinkTrig = getTimeStamp();
            this.mRLEngine.intf.capRSSI = this.mValidLastRssi;
            this.mRLEngine.intf.currentApBssid_IN = this.mCurrentBssid;
            this.mRLEngine.setCurrentAP(this.mCurrentBssid);
            writeToLogFile("Poor Link, timestamp =", String.valueOf(this.timeStampPoorLinkTrig) + "  RSSI:" + String.valueOf(this.mRLEngine.intf.capRSSI) + "  AP:" + this.mRLEngine.intf.currentApBssid_IN);
            this.mRLEngine.intf.mBdTracking.setPoorLinkCountInfo(this.mLinkLossOccurred);
            sendDebugIntent(true);
            this.mRLEngine.algorithmStep();
            if (getCurrentState() != this.mPoorLinkState) {
                transitionTo(this.mPoorLinkState);
            }
        }
    }

    private void detectRewardEventWifiDisable() {
        if (this.mWifiDisablePolicy.isValid()) {
            this.mWifiDisablePolicy.detect();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean detectRewardEventMobileDataDisable() {
        this.mMobileDataDisablePolicy.detect();
        return true;
    }

    private boolean isCurrentTimeWithinSinceEnteringInvalidState(int interval) {
        if (this.mLastInvalidEnterTimestamp <= 0 || getTimeStamp() - this.mLastInvalidEnterTimestamp <= 0 || getTimeStamp() - this.mLastInvalidEnterTimestamp >= ((long) interval)) {
            return false;
        }
        return true;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void detectRewardEventWhenConnectStart() {
        if (this.mManualSwitchPolicy.isValid()) {
            Log.d(TAG, "detectRewardEventConnectStart mLastInvalidEnterTimestamp=" + this.mLastInvalidEnterTimestamp + " cur=" + getTimeStamp() + " diff=" + (getTimeStamp() - this.mLastInvalidEnterTimestamp));
            if (getDiffFromLastPoorLink() >= 23000 || (this.mLastInvalidEnterTimestamp != 0 && !isCurrentTimeWithinSinceEnteringInvalidState(1000))) {
                if (getCurrentState() == this.mInvalidState) {
                    if (isCurrentTimeWithinSinceEnteringInvalidState(5000)) {
                        this.mManualSwitchPolicy.detect(RewardEvent.MANUAL_SWITCH_G);
                    } else if (this.mLastInvalidEnterTimestamp > 0) {
                        this.mManualSwitchPolicy.detect(RewardEvent.MANUAL_SWITCH_L);
                    }
                }
                this.mManualSwitchPolicy.discard();
                return;
            }
            this.mManualSwitchPolicy.detect(RewardEvent.MANUAL_SWITCH);
            this.mManualSwitchPolicy.discard();
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:12:0x0045, code lost:
        if (r8.mLastConnectedBssid == null) goto L_0x009a;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:14:0x004b, code lost:
        if (r8.mLastConnectedNetworkId != r8.mCurrentNetworkId) goto L_0x009a;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:15:0x004d, code lost:
        android.util.Log.d(com.android.server.wifi.IWCMonitor.TAG, "Conncted to (" + r8.mCurrentBssid + ") same as previous one. nid:" + r8.mCurrentNetworkId);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:16:0x0071, code lost:
        if (r8.mNetworkDisabledByAutoReconnect != false) goto L_0x007b;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:18:0x0079, code lost:
        if (isUserInteraction(r8.mConnectNetworkPackageName) != false) goto L_0x007f;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:20:0x007d, code lost:
        if (r8.mRemoveUnwantedNetworkToGoBack == false) goto L_0x0087;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:21:0x007f, code lost:
        r8.mManualReconnectPolicy.detect();
        r8.mRemoveUnwantedNetworkToGoBack = false;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:22:0x0087, code lost:
        r8.mManualReconnectPolicy.discard();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:23:0x0092, code lost:
        if (r8.mManualSwitchPolicy.isValid() == false) goto L_?;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:24:0x0094, code lost:
        r8.mManualSwitchPolicy.discard();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:26:0x00a0, code lost:
        if (isUserInteraction(r8.mConnectNetworkPackageName) == false) goto L_?;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:28:0x00ac, code lost:
        if (r8.mConnectionHistory.findConnectionIdWithin(31000, r5, r8.mLastConnectedNetworkId) == false) goto L_?;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:29:0x00ae, code lost:
        r0 = r8.mConnectionHistory.getLastConnection();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:30:0x00b4, code lost:
        if (r0 == null) goto L_?;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:32:0x00ba, code lost:
        if (r0.networkId == r8.mCurrentNetworkId) goto L_0x00c0;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:34:0x00be, code lost:
        if (r0.triggeredByUser != false) goto L_?;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:35:0x00c0, code lost:
        r8.mManualReconnectPolicy.detect();
        r8.mManualReconnectPolicy.discard();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:42:?, code lost:
        return;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:43:?, code lost:
        return;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:44:?, code lost:
        return;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:45:?, code lost:
        return;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:46:?, code lost:
        return;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:47:?, code lost:
        return;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:48:?, code lost:
        return;
     */
    private void detectRewardEventWhenNetworkConnected() {
        Throwable th;
        if (DBG) {
            Log.d(TAG, "Current PN (" + this.mCurrentPackageName + ") Remove PN (" + this.mRemoveNetworkPackageName + ") Connect PN (" + this.mConnectNetworkPackageName + ")");
        }
        if (this.mManualReconnectPolicy.isValid()) {
            synchronized (this) {
                try {
                    long tsConnected = this.timeStampConnected;
                    try {
                    } catch (Throwable th2) {
                        th = th2;
                        throw th;
                    }
                } catch (Throwable th3) {
                    th = th3;
                    throw th;
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void detectRewardEventAutoDisconnection() {
        if (!isUserInteraction(this.mConnectNetworkPackageName) && !isUserInteraction(this.mRemoveNetworkPackageName) && !this.mIsWifiDisabledByUser) {
            if (isAirplaneModeEnabled()) {
                writeToLogFile("Airplain mode", " Network ID: " + this.mCurrentWifiInfo.getNetworkId());
                return;
            }
            String str = this.mCurrentPackageName;
            if (str == null || !str.contains(EMERGENCY_MODE_PACKAGE_NAME)) {
                this.mAutoDisconnectionPolicy.detect();
                this.mAutoDisconnectionPolicy.discard();
                return;
            }
            writeToLogFile("Emergency mode", " Network ID: " + this.mCurrentWifiInfo.getNetworkId());
        }
    }

    private void detectRewardEventWhenNetworkDisconnected() {
        int i;
        if (DBG) {
            Log.d(TAG, "Current PN (" + this.mCurrentPackageName + ") Remove PN (" + this.mRemoveNetworkPackageName + ") Connect PN (" + this.mConnectNetworkPackageName + ")");
        }
        if (DBG) {
            Log.d(TAG, "ManualSwitchPolicy: " + this.mManualSwitchPolicy.isValid() + " SwitchedTooShortPolicy: " + this.mSwitchedTooShortPolicy.isValid() + " AutoDisconnectionPolicy: " + this.mAutoDisconnectionPolicy.isValid() + " ManualDisconnectPolicy: " + this.mManualDisconnectPolicy.isValid());
        }
        if (this.mSwitchedTooShortPolicy.isValid() && isUserInteraction(this.mConnectNetworkPackageName) && !isUserInteraction(this.mRemoveNetworkPackageName) && !this.mIsWifiDisabledByUser) {
            boolean vendor2 = isVendorAp(this.mCurrentNetworkId);
            boolean samsung = isSamsungSpecificAp(this.mCurrentNetworkId, this.mCurrentBssid);
            writeToLogFile("mDisconnectToConnectNewNetwork: ", this.mDisconnectToConnectNewNetwork + " vendor: " + vendor2 + " samsung: " + samsung);
            if (!this.mDisconnectToConnectNewNetwork && !vendor2 && !samsung) {
                this.mConnectNetworkPackageName = null;
                this.mSwitchedTooShortPolicy.detect();
                this.mSwitchedTooShortPolicy.discard();
            }
        } else if (!this.mManualDisconnectPolicy.isValid() || !(this.mRemoveNetworkId == (i = this.mCurrentNetworkId) || this.mAutoReconnectNetworkId == i)) {
            if (this.mAutoDisconnectionPolicy.isValid()) {
                detectRewardEventAutoDisconnection();
            }
        } else if (getCurrentState() == this.mInvalidState || getCurrentState() == this.mPoorLinkState || getDiffFromLastPoorLink() < 23000) {
            this.mManualDisconnectPolicy.detect();
            this.mManualDisconnectPolicy.discard();
        }
    }

    private synchronized void updateHintList() {
        List<String> hintList;
        String dbString = Settings.Global.getString(this.mContext.getContentResolver(), "sem_what_hintcard_have_to_be_shown");
        if (dbString != null) {
            if (!dbString.isEmpty()) {
                hintList = new ArrayList<>(Arrays.asList(dbString.split(",")));
                hintList.add("1");
                Settings.Global.putString(this.mContext.getContentResolver(), "sem_what_hintcard_have_to_be_shown", hintList.toString().replace("[", "").replace("]", "").replace(" ", ""));
            }
        }
        hintList = new ArrayList<>();
        hintList.add("1");
        Settings.Global.putString(this.mContext.getContentResolver(), "sem_what_hintcard_have_to_be_shown", hintList.toString().replace("[", "").replace("]", "").replace(" ", ""));
    }

    private synchronized int getSteadyStateNum() {
        return this.mRLEngine.getSteadyStateNum();
    }

    private synchronized void updateTable(RewardEvent re, long now) {
        int steadyStateNumBefore = getSteadyStateNum();
        if (this.mRLEngine.updateTable(re, now)) {
            if (isVendorAp(this.mCurrentNetworkId)) {
                writeToLogFile("Vendor AP", " Rebase is not triggered");
            } else {
                this.mRLEngine.rebase();
            }
        }
        int qai = this.mRLEngine.updateQAI() + 1;
        int steadyStateNum = getSteadyStateNum();
        this.mSNSAvailability = getSmartNetworkSwitchAvailability() == 1;
        writeToLogFile("mSNSAvailability", " " + this.mSNSAvailability + ", #SteadyState before: " + steadyStateNumBefore + " after: " + steadyStateNum + ", qai: " + qai + ", mCurSNS: " + this.mCurSNS);
        if (!this.mCurSNS && this.mSNSAvailability && steadyStateNumBefore == 0 && steadyStateNum == 1 && (qai == 1 || qai == 2)) {
            updateHintList();
            this.mRLEngine.intf.mBdTracking.setIdInfo(4);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void updateTableWifiOff() {
        long now = getTimeStamp();
        this.mRLEngine.intf.currentApBssid_IN = this.mCurrentBssid == null ? this.mPreviousBssid : this.mCurrentBssid;
        this.mRLEngine.intf.capRSSI = this.mPreviousRssi;
        this.mRLEngine.intf.edgeFlag = true;
        writeToLogFile("WIFI OFF event, timestamp =" + String.valueOf(now), ", edgeflag = " + String.valueOf(this.mRLEngine.intf.edgeFlag) + ", old AP=" + this.mRLEngine.intf.currentApBssid_IN + ", PN: " + this.mWifiDisablePackage);
        this.mWifiDisablePackage = "";
        if (this.mRLEngine.intf.currentApBssid_IN != null) {
            updateTable(RewardEvent.WIFI_OFF, now);
            save_model_obj();
        }
    }

    private synchronized void updateTableMobileDataChanged(long time) {
        this.mRLEngine.intf.currentApBssid_IN = this.mCurrentBssid == null ? this.mPreviousBssid : this.mCurrentBssid;
        synchronized (this) {
            this.mRLEngine.intf.capRSSI = this.mValidLastRssi;
        }
        this.mRLEngine.intf.edgeFlag = true;
        updateTable(RewardEvent.CELLULAR_DATA_OFF, getTimeStamp());
        save_model_obj();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void updateTableMobileDataChanged() {
        long now = getTimeStamp();
        updateTableMobileDataChanged(now);
        writeToLogFile("Mobile Data Off, timestamp =", String.valueOf(now) + ", RSSI:" + String.valueOf(this.mRLEngine.intf.capRSSI) + ", AP:" + this.mRLEngine.intf.currentApBssid_IN);
        int temp = this.mCurrentQAI;
        this.mCurrentQAI = getCurrentQAI();
        this.mIsSaver = isSaver(this.mCurrentQAI);
        if (temp != this.mCurrentQAI) {
            int i = -1;
            if (isExcludedBssid()) {
                sendMessageToWCM(WifiConnectivityMonitor.CMD_IWC_CURRENT_QAI, -1);
            } else {
                if (this.mCurSNS && isConnectedNetworkState()) {
                    i = this.mCurrentQAI;
                }
                sendMessageToWCM(WifiConnectivityMonitor.CMD_IWC_CURRENT_QAI, i);
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void updateTableManualReconnect() {
        this.mRLEngine.intf.currentApBssid_IN = this.mCurrentBssid;
        synchronized (this) {
            this.mRLEngine.intf.capRSSI = this.mValidLastRssi;
            writeToLogFile("Manual Reconnection event, timestamp =" + String.valueOf(this.timeStampConnected) + ", edgeflag = " + String.valueOf(this.mRLEngine.intf.edgeFlag), ", RSSI=" + String.valueOf(this.mRLEngine.intf.capRSSI) + ", new AP:" + this.mRLEngine.intf.currentApBssid_IN);
            updateTable(RewardEvent.MANUAL_RECONNECTION, this.timeStampConnected);
        }
        save_model_obj();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void updateTableManualSwitch(RewardEvent msi) {
        this.mRLEngine.intf.edgeFlag = true;
        this.mRLEngine.intf.currentApBssid_IN = this.mCurrentBssid;
        synchronized (this) {
            this.mRLEngine.intf.capRSSI = this.mValidLastRssi;
        }
        save_model_obj();
        String Rew = new String();
        if (msi == RewardEvent.MANUAL_SWITCH) {
            Rew = "halfM";
        } else if (msi == RewardEvent.MANUAL_SWITCH_G) {
            Rew = "G";
        } else if (msi == RewardEvent.MANUAL_SWITCH_L) {
            Rew = "L";
        }
        synchronized (this) {
            writeToLogFile("Manual_swith:" + Rew + ", timestamp =" + String.valueOf(this.timeStampConnected) + ", edgeflag = " + String.valueOf(this.mRLEngine.intf.edgeFlag), ", new AP = null, old AP = " + this.mPreviousBssid);
            updateTable(msi, this.timeStampConnected);
            save_model_obj();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void updateTableManualDisc() {
        this.mRLEngine.intf.edgeFlag = true;
        this.mRLEngine.intf.currentApBssid_IN = this.mPreviousBssid;
        synchronized (this) {
            this.mRLEngine.intf.capRSSI = this.mValidLastRssi;
            writeToLogFile("Manual disconnect, timestamp =" + String.valueOf(this.timeStampConnected) + ", edgeflag = " + String.valueOf(this.mRLEngine.intf.edgeFlag), ", new AP =" + this.mRLEngine.intf.currentApBssid_IN + ", old AP =" + this.mPreviousBssid);
            updateTable(RewardEvent.MANUAL_DISCONNECT, this.timeStampConnected);
        }
        save_model_obj();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void updateTableSwitchedTooShort() {
        long diff;
        synchronized (this) {
            diff = getTimeStamp() - this.timeStampConnected;
        }
        if (diff > 30000) {
            if (!this.mAutoDisconnectionPolicy.isValid()) {
                this.mAutoDisconnectionPolicy.adopt();
            }
            writeToLogFile("Connection time is", Long.toString(diff) + ", CONNECTION_SWITCHED_TOO_SHORT is ignored");
            detectRewardEventAutoDisconnection();
            return;
        }
        this.mRLEngine.intf.edgeFlag = true;
        this.mRLEngine.intf.currentApBssid_IN = this.mCurrentBssid == null ? this.mPreviousBssid : this.mCurrentBssid;
        this.mRLEngine.intf.capRSSI = this.mPreviousRssi;
        this.mRLEngine.intf.connectionMaintainedTime = diff;
        synchronized (this) {
            writeToLogFile("Switched too short event, timestamp =" + String.valueOf(this.timeStampDisconnected) + ", edgeflag = " + String.valueOf(this.mRLEngine.intf.edgeFlag), ", RSSI=" + String.valueOf(this.mRLEngine.intf.capRSSI) + ", Connection Maintained time = " + String.valueOf(this.mRLEngine.intf.connectionMaintainedTime) + ", old AP=" + this.mRLEngine.intf.currentApBssid_IN);
            updateTable(RewardEvent.CONNECTION_SWITCHED_TOO_SHORT, this.timeStampDisconnected);
            save_model_obj();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void updateTableAutoDisconnection() {
        long now = getTimeStamp();
        this.mRLEngine.intf.edgeFlag = true;
        this.mRLEngine.intf.capRSSI = this.mPreviousRssi;
        writeToLogFile("Auto Disconnection Event, timestamp = " + String.valueOf(now), ", RSSI=" + String.valueOf(this.mRLEngine.intf.capRSSI) + ", Old AP=" + this.mRLEngine.intf.currentApBssid_IN);
        updateTable(RewardEvent.AUTO_DISCONNECTION, now);
        save_model_obj();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void updateTableForceAction(int action) {
        boolean old_swflag = this.mRLEngine.intf.edgeFlag;
        if (action == -1) {
            this.mRLEngine.intf.edgeFlag = true;
            updateTable(RewardEvent.CONNECTION_SWITCHED_TOO_SHORT, getTimeStamp());
            this.mRLEngine.intf.edgeFlag = old_swflag;
        } else if (action == 0) {
            this.mRLEngine.intf.edgeFlag = true;
            updateTable(RewardEvent.AUTO_DISCONNECTION, getTimeStamp());
            this.mRLEngine.intf.edgeFlag = old_swflag;
        } else if (action == 1) {
            this.mRLEngine.intf.edgeFlag = true;
            updateTable(RewardEvent.WIFI_OFF, getTimeStamp());
            this.mRLEngine.intf.edgeFlag = old_swflag;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void updateTableSNSDisable() {
        this.mRLEngine.intf.snsFlag = false;
        this.mRLEngine.intf.aggSnsFlag = false;
        this.mRLEngine.intf.snsOptionChanged = true;
        updateTable(RewardEvent.SNS_OFF, getTimeStamp());
        writeToLogFile("SNS TURN OFF", "");
        this.mRLEngine.setDefaultQAI();
        this.mRLEngine.intf.snsOptionChanged = false;
        save_model_obj();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void updateTableSNSEnable() {
        if (Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_num_of_switch_to_mobile_data_toggle", 0) == 1) {
            Log.d(TAG, "updateTableSNSEnable is called at first");
            return;
        }
        this.mRLEngine.intf.snsFlag = true;
        this.mRLEngine.intf.aggSnsFlag = true;
        this.mRLEngine.intf.snsOptionChanged = true;
        updateTable(RewardEvent.SNS_ON, getTimeStamp());
        writeToLogFile("SNS TURN ON", "");
        this.mRLEngine.setDefaultQAI();
        this.mRLEngine.intf.snsOptionChanged = false;
        save_model_obj();
    }

    private synchronized void updateTableIntermediateNetworkConnected() {
        this.mRLEngine.intf.currentApBssid_IN = this.mCurrentBssid;
        WifiConfiguration config = getWifiConfiguration(this.mCurrentNetworkId);
        synchronized (this) {
            try {
                this.mRLEngine.intf.capRSSI = this.mValidLastRssi;
                writeToLogFile("Network connected event(intermediate), timestamp =" + String.valueOf(this.timeStampConnected), ", New AP:" + this.mRLEngine.intf.currentApBssid_IN + ", SS:" + getIsSteadyState(this.mCurrentBssid));
                this.mRLEngine.intf.snsOptionChanged = true;
                updateTable(RewardEvent.NETWORK_CONNECTED, this.timeStampConnected);
                if (config != null) {
                    try {
                        this.mRLEngine.putBssidToConfigKey(config.configKey(), this.mCurrentBssid);
                    } catch (Throwable th) {
                        th = th;
                    }
                }
                this.mRLEngine.intf.snsOptionChanged = false;
                save_model_obj();
            } catch (Throwable th2) {
                th = th2;
                throw th;
            }
        }
    }

    private boolean isAirplaneModeEnabled() {
        return Settings.System.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) != 0;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private WifiConfiguration getWifiConfiguration(int netID) {
        WifiConfigManager wifiConfigManager;
        if (netID == -1 || (wifiConfigManager = this.mWifiConfigManager) == null) {
            return null;
        }
        return wifiConfigManager.getConfiguredNetwork(netID);
    }

    private boolean isSkipInternetCheck() {
        int networkId;
        WifiInfo wifiInfo = this.mCurrentWifiInfo;
        if (wifiInfo == null || (networkId = wifiInfo.getNetworkId()) == -1) {
            return false;
        }
        WifiConfiguration wifiConfiguration = getWifiConfiguration(networkId);
        if (wifiConfiguration != null) {
            return wifiConfiguration.isNoInternetAccessExpected();
        }
        Log.d(TAG, "isSkipInternetCheck - config == null");
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isExcludedBssid() {
        boolean isMHS = isMobileHotspot(this.mCurrentNetworkId);
        boolean isStayConnected = isSkipInternetCheck();
        String reason = new String();
        if (isMHS) {
            reason = ") is MHS";
        } else if (isStayConnected) {
            reason = ") skips internet check";
        }
        if (!reason.isEmpty()) {
            writeToLogFile("Excluded network from IWC", this.mCurrentWifiInfo.getBSSID() + "(" + this.mCurrentWifiInfo.getNetworkId() + reason);
        }
        return isMHS || isStayConnected;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void wifiStateChanged(int wifiState) {
        if (wifiState == 1) {
            Log.d(TAG, "WIFI_STATE_DISABLED mLastPoorLinkTimestamp=" + this.mLastPoorLinkTimestamp + " mLastPoorLinkTimestampBeforeDisc=" + this.mLastPoorLinkTimestampBeforeDisc);
            Long now = Long.valueOf(getTimeStamp());
            if ((this.mLastPoorLinkTimestampBeforeDisc <= 0 || now.longValue() - this.mLastPoorLinkTimestampBeforeDisc >= 20000) && !((this.mLastPoorLinkTimestamp > 0 && now.longValue() - this.mLastPoorLinkTimestamp < 20000) || getCurrentState() == this.mInvalidState || getCurrentState() == this.mPoorLinkState)) {
                synchronized (this) {
                    this.mRLEngine.intf.edgeFlag = false;
                    writeToLogFile("WIFI OFF event, timestamp =" + String.valueOf(now), ", edgeflag = " + String.valueOf(this.mRLEngine.intf.edgeFlag) + ", old AP=" + this.mRLEngine.intf.currentApBssid_IN + ", PN = " + this.mWifiDisablePackage);
                    this.mWifiDisablePackage = "";
                }
            } else {
                String header = "WIFI OFF event, timestamp =" + now;
                String notDetectedReason = "";
                String oldAp = ", old AP=" + this.mRLEngine.intf.currentApBssid_IN;
                if (isAirplaneModeEnabled()) {
                    notDetectedReason = ", Airplain mode";
                } else {
                    String str = this.mCurrentPackageName;
                    if (str != null && str.contains(EMERGENCY_MODE_PACKAGE_NAME)) {
                        notDetectedReason = ", Emergency mode";
                    } else if (this.mIsWifiDisabledByUser) {
                        detectRewardEventWifiDisable();
                    } else {
                        notDetectedReason = ", PN: " + this.mWifiDisablePackage;
                        this.mWifiDisablePackage = "";
                    }
                }
                if (!notDetectedReason.isEmpty()) {
                    writeToLogFile(header, notDetectedReason + oldAp);
                }
            }
            this.mWifiDisablePolicy.discard();
            this.mAutoDisconnectionPolicy.discard();
            this.mSwitchedTooShortPolicy.discard();
            this.mManualReconnectPolicy.discard();
            this.mLastPoorLinkTimestampBeforeDisc = 0;
            this.mCurrentBssid = null;
            this.mCurrentNetworkId = -1;
            this.mLastConnectedBssid = null;
            this.mLastConnectedNetworkId = -1;
            this.mLastPoorLinkTimestamp = 0;
            this.mRemoveNetworkId = -1;
            this.mConnectNetworkId = -1;
            this.mRemoveUnwantedNetworkToGoBack = false;
            this.mNetworkDisabledByAutoReconnect = false;
            this.mIsWifiDisabledByUser = false;
        } else if (wifiState == 2) {
            Handler handler = getHandler();
            if (handler != null && handler.hasMessages(WIFI_OFF_EVENT_PENDED)) {
                removeMessages(WIFI_OFF_EVENT_PENDED);
                writeToLogFile("Pending Wi-Fi Off event has been removed", "");
            }
        } else if (wifiState == 3) {
            if (!this.mWifiDisablePolicy.isValid()) {
                this.mWifiDisablePolicy.adopt();
            }
            this.timeStampPoorLinkTrig = 0;
            synchronized (this) {
                this.timeStampConnected = 0;
                this.timeStampDisconnected = 0;
            }
        }
    }

    /* access modifiers changed from: package-private */
    /* renamed from: com.android.server.wifi.IWCMonitor$7 */
    public static /* synthetic */ class C03987 {
        static final /* synthetic */ int[] $SwitchMap$android$net$NetworkInfo$DetailedState = new int[NetworkInfo.DetailedState.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$server$wifi$iwc$RewardEvent = new int[RewardEvent.values().length];

        static {
            try {
                $SwitchMap$android$net$NetworkInfo$DetailedState[NetworkInfo.DetailedState.CONNECTING.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$android$net$NetworkInfo$DetailedState[NetworkInfo.DetailedState.CONNECTED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$android$net$NetworkInfo$DetailedState[NetworkInfo.DetailedState.DISCONNECTING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$android$net$NetworkInfo$DetailedState[NetworkInfo.DetailedState.DISCONNECTED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$server$wifi$iwc$RewardEvent[RewardEvent.MANUAL_SWITCH.ordinal()] = 1;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$server$wifi$iwc$RewardEvent[RewardEvent.MANUAL_SWITCH_G.ordinal()] = 2;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$server$wifi$iwc$RewardEvent[RewardEvent.MANUAL_SWITCH_L.ordinal()] = 3;
            } catch (NoSuchFieldError e7) {
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void networkStateChanged(NetworkInfo.DetailedState state, WifiInfo info) {
        long now = getTimeStamp();
        int i = C03987.$SwitchMap$android$net$NetworkInfo$DetailedState[state.ordinal()];
        if (i == 1) {
            this.mRLEngine.intf.currentApBssid_IN = info.getBSSID();
            writeToLogFile("Connecting event, timestamp =" + String.valueOf(now), ", new AP:" + this.mRLEngine.intf.currentApBssid_IN);
        } else if (i == 2) {
            String tempBssid = info.getBSSID();
            if (this.mCurrentBssid == null || (tempBssid != null && !this.mCurrentBssid.equals(tempBssid))) {
                if (this.mCurrentBssid != null) {
                    Log.d(TAG, "Connected event comes up without disconnected event.. Discard related policies..");
                    if (this.mAutoDisconnectionPolicy.isValid()) {
                        this.mAutoDisconnectionPolicy.discard();
                    }
                    if (this.mManualDisconnectPolicy.isValid()) {
                        this.mManualDisconnectPolicy.discard();
                    }
                    if (this.mManualReconnectPolicy.isValid()) {
                        this.mManualReconnectPolicy.discard();
                    }
                    if (this.mIwcDataRateTraceData != null) {
                        this.mIwcDataRateTraceData.resetBufsAndCnts();
                    }
                    if (this.mIsSaver && this.mSaverState != 0) {
                        unsetDQAI();
                    }
                }
                this.mPreviousBssid = this.mCurrentBssid;
                this.mCurrentBssid = tempBssid;
                this.mCurrentWifiInfo = new WifiInfo(info);
                this.mCurrentNetworkId = info.getNetworkId();
                this.mCurrentRssi = info.getRssi();
                this.mRLEngine.setCurrentAP(this.mCurrentBssid);
                this.mRLEngine.updateQAI();
                synchronized (this) {
                    this.timeStampConnected = now;
                    if (this.mLinkLossOccurred > 0) {
                        this.timeStampPoorLinkTrig = now;
                        writeToLogFile("Network connected while poor link. Ignore previous poor link, timestamp =", String.valueOf(this.timeStampPoorLinkTrig) + ", RSSI:" + String.valueOf(this.mRLEngine.intf.capRSSI) + ", AP:" + this.mRLEngine.intf.currentApBssid_IN);
                        this.mLinkLossOccurred = 0;
                        this.mPreviousLinkLoss = 0;
                    }
                    writeToLogFile("SNS state, " + String.valueOf(this.mRLEngine.intf.snsFlag) + ", timestamp =" + String.valueOf(now), ", edgeflag = " + String.valueOf(this.mRLEngine.intf.edgeFlag) + ", NEW AP:" + this.mRLEngine.intf.currentApBssid_IN);
                    updateTableIntermediateNetworkConnected();
                    detectRewardEventWhenNetworkConnected();
                    this.mSwitchedTooShortPolicy.adopt();
                    this.mConnectNetworkId = -1;
                    this.mConnectNetworkUid = 0;
                    this.mDisconnectToConnectNewNetwork = false;
                    this.mNetworkDisabledByAutoReconnect = false;
                    this.mCurrentQAI = getCurrentQAI();
                    this.mIsSaver = isSaver(this.mCurrentQAI);
                    if (isExcludedBssid()) {
                        sendMessageToWCM(WifiConnectivityMonitor.CMD_IWC_CURRENT_QAI, -1);
                    } else {
                        sendMessageToWCM(WifiConnectivityMonitor.CMD_IWC_CURRENT_QAI, this.mCurSNS ? this.mCurrentQAI : -1);
                    }
                    if (!(this.mCurrentBssid == null || this.mCurrentNetworkId == -1)) {
                        this.mConnectionHistory.addLastConnection(new Connection(this.mCurrentBssid, this.mCurrentNetworkId, now, this.mConnectNetworkTriggeredByUser));
                        this.mConnectNetworkTriggeredByUser = false;
                    }
                    if (getCurrentState() == this.mDisconnectedState) {
                        transitionTo(this.mConnectedState);
                    }
                }
            } else if (!(tempBssid == null || this.mCurrentBssid == null || this.mCurrentBssid.equals(tempBssid))) {
                Log.d(TAG, "Ignore duplicated event. bssid - " + this.mCurrentBssid);
            }
        } else if (i == 3) {
            writeToLogFile("Disconnecting event, timestamp =" + String.valueOf(now), ", new AP:" + this.mRLEngine.intf.currentApBssid_IN);
        } else if (i == 4) {
            if (this.mCurrentBssid != null) {
                synchronized (this) {
                    this.timeStampDisconnected = now;
                    this.mPreviousBssid = this.mCurrentBssid;
                    this.mRLEngine.intf.currentApBssid_IN = this.mPreviousBssid;
                    this.mRLEngine.intf.capRSSI = this.mPreviousRssi;
                    writeToLogFile("Network disconnected event(intermediate), timestamp =" + String.valueOf(now), ", RSSI=" + String.valueOf(this.mRLEngine.intf.capRSSI));
                    writeToLogFile("Foreground package,", " " + this.mCurrentPackageName + "(" + this.mCurrentUid + ")");
                    updateTable(RewardEvent.NETWORK_DISCONNECTED, now);
                    save_model_obj();
                    if (this.mRemoveNetworkId != this.mCurrentNetworkId) {
                        this.mLastConnectedBssid = this.mCurrentBssid;
                        this.mLastConnectedNetworkId = this.mCurrentNetworkId;
                        if (DBG) {
                            Log.w(TAG, "UPDATED mLastConnectedBssid: " + this.mLastConnectedBssid + " mLastConnectedNetworkId: " + this.mLastConnectedNetworkId);
                        }
                    }
                    detectRewardEventWhenNetworkDisconnected();
                    this.mCurrentBssid = null;
                    this.mRemoveNetworkPackageName = null;
                    this.mRemoveNetworkId = -1;
                    this.mRemoveNetworkUid = 0;
                    transitionTo(this.mDisconnectedState);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void setIntfSnsFlag() {
        Log.d(TAG, "setIntfSnsFlag mCurSNS=" + this.mCurSNS + " mCurAGG=" + this.mCurAGG);
        if (this.mCurSNS && this.mCurAGG) {
            this.mRLEngine.intf.snsFlag = true;
            this.mRLEngine.intf.aggSnsFlag = true;
        } else if (!this.mCurSNS || this.mCurAGG) {
            this.mRLEngine.intf.snsFlag = false;
            this.mRLEngine.intf.aggSnsFlag = false;
        } else {
            this.mRLEngine.intf.snsFlag = true;
            this.mRLEngine.intf.aggSnsFlag = false;
        }
    }

    public synchronized boolean getIsSteadyState(String bssid) {
        boolean bRet = this.mRLEngine.getIsSteadyState(bssid);
        if (!this.mIsDebugMode) {
            return bRet;
        }
        return this.mIsSteadyStateDbg;
    }

    public int getCurrentQAI() {
        if (!this.mIsDebugMode) {
            return this.mRLEngine.getCurrentState() + 1;
        }
        Log.d(TAG, "mCurrentQAIDbg - " + this.mCurrentQAIDbg);
        return this.mCurrentQAIDbg;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isSaver(int currentQAI) {
        boolean saver;
        if (this.mIwcDataUsagePatternTracker == null || (!(currentQAI == 1 || currentQAI == 2) || !this.mCurSNS || !isConnectedNetworkState() || isExcludedBssid())) {
            saver = false;
        } else {
            saver = this.mIwcDataUsagePatternTracker.getDataUsagePattern();
        }
        if (this.mIsSaver && !saver && this.mSaverState != 0) {
            unsetDQAI();
        }
        Log.d(TAG, "Previous Saver: " + this.mIsSaver + ", Current Saver: " + saver);
        sendDebugIntent(saver, this.mSaverState, this.mCurrentQAI);
        if (!this.mIsSaverDbg) {
            return saver;
        }
        Log.e(TAG, "Change the saver as true for debugging");
        return true;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void unsetDQAI() {
        long now = SystemClock.elapsedRealtime();
        this.mSaverState = 0;
        sendMessageToWCM(WifiConnectivityMonitor.CMD_IWC_CURRENT_QAI, this.mCurrentQAI);
        writeToLogFile("DYNAMIC-QAI: ", "Restore current QAI to " + this.mCurrentQAI);
        sendDebugIntent(this.mIsSaver, this.mSaverState, this.mCurrentQAI);
        if (this.mRLEngine.intf.mBdTracking != null) {
            this.mTxBytesInDQAI = this.mTxBytes - this.mTxBytesInDQAI;
            this.mRxBytesInDQAI = this.mRxBytes - this.mRxBytesInDQAI;
            this.mRLEngine.intf.mBdTracking.setAvgDataRateOfDQAI(this.mTxBytesInDQAI > this.mRxBytesInDQAI ? this.mTxBytesInDQAI : this.mRxBytesInDQAI);
            this.mRLEngine.intf.mBdTracking.setAvgDurOfDQAI(now - this.mDQAIDuration);
            writeToLogFile("DYNAMIC-QAI: ", "Duration - " + (now - this.mDQAIDuration));
            writeToLogFile("DYNAMIC-QAI: ", "TxBytesInDQAI - " + this.mTxBytesInDQAI + ", RxBytesInDQAI - " + this.mRxBytesInDQAI);
            if (getCurrentState() == this.mPoorLinkState && this.mSavedBytes > 0 && this.mSavedBytes < this.mTxBytes + this.mRxBytes) {
                this.mSavedBytes = (this.mTxBytes + this.mRxBytes) - this.mSavedBytes;
                this.mRLEngine.intf.mBdTracking.setSavedBytes(this.mSavedBytes);
                writeToLogFile("DYNAMIC-QAI: ", "Saved data - " + this.mSavedBytes + "Bytes");
            }
            this.mSavedBytes = 0;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void setDQAI(int newState) {
        long now = SystemClock.elapsedRealtime();
        this.mSaverState = newState;
        sendMessageToWCM(WifiConnectivityMonitor.CMD_IWC_CURRENT_QAI, 3);
        writeToLogFile("DYNAMIC-QAI: ", "Set current QAI to 3 from " + this.mCurrentQAI + ", State - " + newState);
        sendDebugIntent(this.mIsSaver, newState, 3);
        this.mTxBytesInDQAI = this.mTxBytes;
        this.mRxBytesInDQAI = this.mRxBytes;
        this.mDQAIDuration = now;
        if (this.mRLEngine.intf.mBdTracking != null) {
            this.mRLEngine.intf.mBdTracking.setCntOfDQAI();
            this.mRLEngine.intf.mBdTracking.setAppNameOfDQAI(this.mCurrentPackageName);
            writeToLogFile("DYNAMIC-QAI: ", "Foreground package - " + this.mCurrentPackageName);
        }
        if (getCurrentState() == this.mPoorLinkState && this.mSavedBytes == 0) {
            this.mSavedBytes = this.mTxBytes + this.mRxBytes;
            Log.d(TAG, "DYNAMIC-QAI: Start to check saved data");
        }
    }

    private boolean isPackageInstalled(String packagename) {
        try {
            this.mContext.getPackageManager().getPackageInfo(packagename, 1);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean sendMessageToWCM(int msgType, int param) {
        String str;
        Message msg = Message.obtain();
        Bundle bundle = new Bundle();
        if (DBG) {
            Log.d(TAG, "sendMessageToWCM, Type: " + msgType);
        }
        if (this.mWcmChannel == null) {
            Log.e(TAG, "mWcmChannel is null");
            return false;
        }
        switch (msgType) {
            case WifiConnectivityMonitor.CMD_IWC_CURRENT_QAI:
                str = "IWC-CURRENT-QAI QAI: " + param;
                bundle.putString("bssid", this.mCurrentBssid);
                bundle.putInt("qai", param);
                getCm().notifyCurrentQAI(param);
                break;
            case WifiConnectivityMonitor.CMD_IWC_REQUEST_NETWORK_SWITCH_TO_MOBILE:
                str = "REQUEST-NETWORK-SWITCH-TO-MOBILE reason: " + param;
                bundle.putString("bssid", this.mCurrentBssid);
                break;
            case WifiConnectivityMonitor.CMD_IWC_REQUEST_NETWORK_SWITCH_TO_WIFI:
                str = "REQUEST-NETWORK-SWITCH-TO-WIFI reason: " + param;
                bundle.putString("bssid", this.mCurrentBssid);
                break;
            case WifiConnectivityMonitor.CMD_IWC_REQUEST_INTERNET_CHECK:
                str = "REQUEST-INTERNET-CHECK CID: " + param;
                bundle.putInt("cid", param);
                break;
            default:
                Log.e(TAG, "Undefined Message Type : " + msgType);
                return false;
        }
        msg.what = msgType;
        msg.setData(bundle);
        this.mWcmChannel.sendMessage(msg);
        if (DBG) {
            Log.d(TAG, "Send " + str);
        }
        if (MISC_DBG) {
            Toast.makeText(this.mContext, str, 0).show();
        }
        writeToLogFile(TAG, "Send " + str);
        return true;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("IWC dump ----- start -----");
        pw.println("mCurrentBssid - " + this.mCurrentBssid);
        pw.println("mCurrentNetworkId - " + this.mCurrentNetworkId);
        pw.println("mLastConnectedBssid - " + this.mLastConnectedBssid);
        pw.println("mLastConnectedNetworkId - " + this.mLastConnectedNetworkId);
        pw.println("mIsSteadyState - " + this.mIsSteadyState);
        pw.println("mCurrentQAI - " + this.mCurrentQAI);
        this.mRLEngine.printTable();
        this.mRLEngine.printApLists();
        this.mRLEngine.printCurrentTable(this.mCurrentBssid);
        IWCLogFile iWCLogFile = this.mLogFile;
        if (iWCLogFile != null) {
            iWCLogFile.dumpLocalLog(fd, pw, args);
        }
        pw.println("IWC dump ----- end -----\n");
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void resetLearningData(int qai) {
        File qtables = new File("/data/misc/wifi/qtables.json");
        if (this.mRLEngine != null) {
            Log.d(TAG, "Initialize Qtables");
            this.mRLEngine.setCurrentAP(null);
            this.mRLEngine.setQtables(new QTableContainer(this.mLogFile));
        }
        if (qtables.exists()) {
            Log.d(TAG, "Delete success : /data/misc/wifi/qtables.json");
            qtables.delete();
        } else {
            Log.d(TAG, "no file");
        }
        if (qai == -1) {
            this.mRLEngine.setDefaultQAI();
        } else {
            this.mRLEngine.setDefaultQAI(qai);
        }
    }

    public void factoryReset() {
        long start = getTimeStamp();
        writeToLogFile("factoryReset()", "Start");
        unregisterBroadcastReceiver();
        resetLearningData(-1);
        this.mCurrentBssid = null;
        this.mRemoveNetworkPackageName = null;
        setIntfSnsFlag();
        setBroadcastReceiver();
        writeToLogFile("factoryReset()", "Finish, time: " + Long.toString(getTimeStamp() - start) + "ms");
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String loadIWCDbgFile() {
        FileReader reader = null;
        BufferedReader br = null;
        String data = "";
        try {
            FileReader reader2 = new FileReader(IWCD_FILE);
            BufferedReader br2 = new BufferedReader(reader2);
            while (true) {
                String line = br2.readLine();
                if (line != null) {
                    data = data + line + "\n";
                } else {
                    try {
                        br2.close();
                        reader2.close();
                        return data;
                    } catch (IOException e) {
                        Log.e(TAG, "IOException closing file");
                        return null;
                    }
                }
            }
        } catch (Exception e2) {
            Log.d(TAG, "no file");
            if (0 != 0) {
                br.close();
            }
            if (0 == 0) {
                return null;
            }
            reader.close();
            return null;
        } catch (Throwable th) {
            if (0 != 0) {
                try {
                    br.close();
                } catch (IOException e3) {
                    Log.e(TAG, "IOException closing file");
                    throw th;
                }
            }
            if (0 != 0) {
                reader.close();
            }
            throw th;
        }
    }

    public void setDebugParams(String data) {
        String[] line;
        if (!(data == null || (line = data.split("\n")) == null)) {
            for (String str : line) {
                if (str != null) {
                    if (str.startsWith("qai=")) {
                        this.mCurrentQAIDbg = getIntValue(str, 1);
                        Log.d(TAG, "mCurrentQAIDbg : " + this.mCurrentQAIDbg);
                        int i = this.mCurrentQAIDbg;
                        if (i == 1 || i == 2 || i == 3) {
                            this.mIsDebugMode = true;
                            this.mIsSteadyStateDbg = true;
                        } else {
                            this.mIsDebugMode = false;
                        }
                    } else if (str.startsWith("saver=")) {
                        this.mIsSaverDbg = getStringValue(str, "0").equals("1");
                        Log.d(TAG, "mIsSaverDbg : " + this.mIsSaverDbg);
                    } else if (str.startsWith("rssi_dbg=")) {
                        RSSI_DBG = getStringValue(str, "0").equals("1");
                        IWCDataRateTraceData iWCDataRateTraceData = this.mIwcDataRateTraceData;
                        if (iWCDataRateTraceData != null) {
                            iWCDataRateTraceData.setDebugMode(RSSI_DBG);
                        }
                        Log.d(TAG, "rssi_dbg : " + RSSI_DBG);
                    } else if (str.startsWith("misc_dbg=")) {
                        MISC_DBG = getStringValue(str, "0").equals("1");
                        Log.d(TAG, "misc_dbg : " + MISC_DBG);
                    } else if (str.startsWith("ping_enable=")) {
                        this.mPingEnabled = getStringValue(str, "0").equals("1");
                        Log.d(TAG, "ping_enable : " + this.mPingEnabled);
                    }
                }
            }
        }
    }

    private String getStringValue(String str, String defalutValue) {
        int idx;
        if (str != null && (idx = str.indexOf("=") + 1) <= str.length()) {
            return str.substring(idx, str.length());
        }
        return defalutValue;
    }

    private int getIntValue(String str, int defaultValue) {
        try {
            return Integer.parseInt(getStringValue(str, String.valueOf(defaultValue)));
        } catch (Exception e) {
            Log.e(TAG, "wrong int:" + str);
            return defaultValue;
        }
    }

    public synchronized void UpdateIWCSystemProp() {
        String iwcAgr2;
        SystemProperties.set("iwc.arg1", Integer.toString(this.mCurSNS ? this.mRLEngine.getCurrentState() + 1 : -1));
        if (this.mRLEngine.getCurrentAP() == null) {
            iwcAgr2 = "false";
        } else {
            iwcAgr2 = String.valueOf(getIsSteadyState(this.mRLEngine.getCurrentAP()));
        }
        SystemProperties.set("iwc.arg2", iwcAgr2);
        SystemProperties.set("iwc.arg3", this.mRLEngine.getQTableStr());
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setBigDataMIWC() {
        if (this.mRLEngine.intf.mBdTracking == null || this.mRLEngine.intf.mBdTracking.getIdInfo() > 0) {
            QTableContainer qTablesBigdata = this.mRLEngine.getQtables();
            if (qTablesBigdata == null) {
                Log.e(TAG, "No Qtables. Nothing to send BigData");
                this.mRLEngine.intf.mBdTracking.cleanBD();
                return;
            }
            this.mSNSAvailability = getSmartNetworkSwitchAvailability() == 1;
            this.mRLEngine.intf.mBdTracking.setSNSUIStateInfo(this.mSNSAvailability ? Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 0) : 2, Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_num_of_switch_to_mobile_data_toggle", 0));
            this.mRLEngine.intf.mBdTracking.setDefaultQaiInfo(qTablesBigdata.mDefaultQAI);
            int qtn = qTablesBigdata.qTableList.size();
            int ssn = 0;
            int ss1 = 0;
            int ss2 = 0;
            int ss3 = 0;
            for (int i = 0; i < qtn; i++) {
                QTable tempTable = qTablesBigdata.qTableList.get(i);
                if (tempTable.getSteadyState()) {
                    ssn++;
                    int state = tempTable.getState();
                    if (state == 0) {
                        ss1++;
                    } else if (state == 1) {
                        ss2++;
                    } else if (state == 2) {
                        ss3++;
                    }
                }
            }
            this.mRLEngine.intf.mBdTracking.setQTCountInfo(qtn, ssn);
            this.mRLEngine.intf.mBdTracking.setListCountInfo(qTablesBigdata.candidateApList.size(), qTablesBigdata.coreApList.size(), qTablesBigdata.probationApList.size());
            this.mRLEngine.intf.mBdTracking.setSSCountInfo(ss1, ss2, ss3);
            if (this.mIwcDataUsagePatternTracker != null) {
                this.mRLEngine.intf.mBdTracking.setIsSaverOfDQAI(this.mIwcDataUsagePatternTracker.getDataUsagePattern());
            }
            String toString = (((((((((((((((((((((((((((((((((((((((("setBigDataMIWC - " + "ID: " + this.mRLEngine.intf.mBdTracking.getIdInfo()) + ", AP OUI: " + this.mRLEngine.intf.mBdTracking.getOUIInfo()) + ", CurrentState: " + this.mRLEngine.intf.mBdTracking.getStateInfo()) + ", PrevQAI: " + this.mRLEngine.intf.mBdTracking.getQAIInfo(0)) + ", CurrentQAI: " + this.mRLEngine.intf.mBdTracking.getQAIInfo(1)) + ", Event List: " + this.mRLEngine.intf.mBdTracking.getEVInfo()) + ", Qtable: " + this.mRLEngine.intf.mBdTracking.getQTableValueInfo()) + ", Poorlink Count: " + this.mRLEngine.intf.mBdTracking.getPoorLinkCountInfo()) + ", SNS UI State: " + this.mRLEngine.intf.mBdTracking.getSNSUIStateInfo()) + ", SNS Toggle Count: " + this.mRLEngine.intf.mBdTracking.getSNSToggleInfo()) + ", QAI1 SS Qtable Count: " + this.mRLEngine.intf.mBdTracking.getSSCountInfo(1)) + ", QAI2 SS Qtable Count: " + this.mRLEngine.intf.mBdTracking.getSSCountInfo(2)) + ", QAI3 SS Qtable Count: " + this.mRLEngine.intf.mBdTracking.getSSCountInfo(3)) + ", Default QAI: " + this.mRLEngine.intf.mBdTracking.getDefaultQaiInfo()) + ", SS Time: " + this.mRLEngine.intf.mBdTracking.getSSTakenTimeInfo()) + ", Qtable Count: " + this.mRLEngine.intf.mBdTracking.getQTCountInfo(0)) + ", SS Qtable Count: " + this.mRLEngine.intf.mBdTracking.getQTCountInfo(1)) + ", Candidate List Count: " + this.mRLEngine.intf.mBdTracking.getListCountInfo(0)) + ", Core List Count: " + this.mRLEngine.intf.mBdTracking.getListCountInfo(1)) + ", Probation List Count: " + this.mRLEngine.intf.mBdTracking.getListCountInfo(2)) + ", Event1 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(1)) + ", Event2 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(2)) + ", Event3 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(3)) + ", Event4 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(4)) + ", Event5 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(5)) + ", Event6 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(6)) + ", Event7 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(7)) + ", Event8 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(8)) + ", Event9 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(9)) + ", Event10 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(10)) + ", Event11 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(11)) + ", Event12 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(12)) + ", Event13 Count: " + this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(13)) + ", Tips Showing Duration: " + this.mRLEngine.intf.mBdTracking.getTipsShowingDuration()) + ", Tips clicked: " + this.mRLEngine.intf.mBdTracking.getTipsClick()) + ", D-QAI Count: " + this.mRLEngine.intf.mBdTracking.getCntOfDQAI()) + ", D-QAI Avg duration: " + this.mRLEngine.intf.mBdTracking.getAvgDurOfDQAI()) + ", D-QAI Avg data rate: " + this.mRLEngine.intf.mBdTracking.getAvgDataRateOfDQAI()) + ", D-QAI App Name: " + this.mRLEngine.intf.mBdTracking.getAppNameOfDQAI()) + ", D-QAI Saved bytes: " + this.mRLEngine.intf.mBdTracking.getSavedBytes()) + ", D-QAI Saver: " + this.mRLEngine.intf.mBdTracking.getIsSaverOfDQAI();
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_ID, this.mRLEngine.intf.mBdTracking.getIdInfo());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_AP_OUI, this.mRLEngine.intf.mBdTracking.getOUIInfo());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_GET_CURRENT_STATE, this.mRLEngine.intf.mBdTracking.getStateInfo());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_PREV_QAI, this.mRLEngine.intf.mBdTracking.getQAIInfo(0));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_NEW_QAI, this.mRLEngine.intf.mBdTracking.getQAIInfo(1));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT_LIST, this.mRLEngine.intf.mBdTracking.getEVInfo());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_QTABLE, this.mRLEngine.intf.mBdTracking.getQTableValueInfo());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_POORLINK_COUNT, this.mRLEngine.intf.mBdTracking.getPoorLinkCountInfo());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_SNS_UI_STATE, this.mRLEngine.intf.mBdTracking.getSNSUIStateInfo());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_SNS_TOGGLE_COUNT, this.mRLEngine.intf.mBdTracking.getSNSToggleInfo());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_QAI1_SS_QTABLE, this.mRLEngine.intf.mBdTracking.getSSCountInfo(1));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_QAI2_SS_QTABLE, this.mRLEngine.intf.mBdTracking.getSSCountInfo(2));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_QAI3_SS_QTABLE, this.mRLEngine.intf.mBdTracking.getSSCountInfo(3));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_DEFAULT_QAI, this.mRLEngine.intf.mBdTracking.getDefaultQaiInfo());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_SS_TIME, this.mRLEngine.intf.mBdTracking.getSSTakenTimeInfo());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_QTALBE_COUNT, this.mRLEngine.intf.mBdTracking.getQTCountInfo(0));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_SS_QTALBE_COUNT, this.mRLEngine.intf.mBdTracking.getQTCountInfo(1));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_CANDIDATE_LIST_COUNT, this.mRLEngine.intf.mBdTracking.getListCountInfo(0));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_CORE_LIST_COUNT, this.mRLEngine.intf.mBdTracking.getListCountInfo(1));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_PROBATION_LIST_COUNT, this.mRLEngine.intf.mBdTracking.getListCountInfo(2));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT1_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(1));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT2_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(2));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT3_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(3));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT4_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(4));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT5_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(5));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT6_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(6));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT7_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(7));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT8_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(8));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT9_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(9));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT10_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(10));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT11_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(11));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT12_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(12));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_EVENT13_COUNT, this.mRLEngine.intf.mBdTracking.get24HEventAccWithIdx(13));
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_TST, this.mRLEngine.intf.mBdTracking.getTipsShowingDuration());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_TCL, this.mRLEngine.intf.mBdTracking.getTipsClick());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_DQC, this.mRLEngine.intf.mBdTracking.getCntOfDQAI());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_DQD, this.mRLEngine.intf.mBdTracking.getAvgDurOfDQAI());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_DQT, this.mRLEngine.intf.mBdTracking.getAvgDataRateOfDQAI());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_DQA, this.mRLEngine.intf.mBdTracking.getAppNameOfDQAI());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_SAV, this.mRLEngine.intf.mBdTracking.getIsSaverOfDQAI());
            this.mIwcBigDataManager.addOrUpdateFeatureValue(IwcBigDataManager.FEATURE_MIWC, IwcBigDataMIWC.KEY_IWC_SAD, this.mRLEngine.intf.mBdTracking.getSavedBytes());
            this.mIwcBigDataManager.insertLog(IwcBigDataManager.FEATURE_MIWC, (long) this.mRLEngine.intf.mBdTracking.getIdInfo());
            this.mIwcBigDataManager.clearFeature(IwcBigDataManager.FEATURE_MIWC);
            if (DBG) {
                Log.i(TAG, toString);
            }
            this.mRLEngine.intf.mBdTracking.cleanBD();
            return;
        }
        Log.e(TAG, "No User-Event. Nothing to send BigData");
        this.mRLEngine.intf.mBdTracking.cleanBD();
    }

    public synchronized String getQtables() {
        try {
        } catch (JSONException e) {
            e.printStackTrace();
            return new String();
        }
        return new QTableContainerBuilder().toJsonString(this.mRLEngine.getQtables());
    }

    public synchronized void setQtables(String qtProxy) {
        QTableContainerBuilder builder = new QTableContainerBuilder();
        builder.setIWCJson(qtProxy).setIWCLogFile(this.mLogFile);
        QTableContainer container = builder.create();
        Log.d(TAG, "setQtables() Number of Q tables: " + container.qTableList.size());
        this.mRLEngine.setQtables(container);
        save_model_obj();
    }

    public synchronized void setQtables(String qtProxy, boolean updateQtables) {
        if (qtProxy != null) {
            if (qtProxy.length() != 0) {
                setQtables(qtProxy);
                if (updateQtables) {
                    this.mRLEngine.removeNonSSQtables();
                    if (isConnectedNetworkState()) {
                        this.mRLEngine.algorithmStep();
                        updateDebugIntent();
                    }
                    save_model_obj();
                }
            }
        }
    }

    private boolean isMobileHotspot(int networkId) {
        if (networkId == this.mLastCheckedMobileHotspotNetworkId) {
            return this.mLastCheckedMobileHotspotValue;
        }
        WifiConfiguration config = this.mClientModeImpl.syncGetSpecificNetwork(this.mClientModeImplChannel, networkId);
        if (!(networkId == -1 || config == null)) {
            this.mLastCheckedMobileHotspotNetworkId = networkId;
            this.mLastCheckedMobileHotspotValue = false;
            if (config.semSamsungSpecificFlags.get(1)) {
                if (DBG) {
                    Log.d(TAG, "Samsung mobile hotspot");
                }
                this.mLastCheckedMobileHotspotValue = true;
                return true;
            }
        }
        if (this.mCurrentWifiInfo != null) {
            for (int mask : MHS_PRIVATE_NETWORK_MASK) {
                if ((this.mCurrentWifiInfo.getIpAddress() & 16777215) == mask) {
                    if (DBG) {
                        Log.d(TAG, "This network IP is for Mobile hotspot");
                    }
                    this.mLastCheckedMobileHotspotNetworkId = networkId;
                    this.mLastCheckedMobileHotspotValue = true;
                    return true;
                }
            }
        }
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isVendorAp(int networkId) {
        WifiConfiguration config;
        if (networkId == -1 || (config = this.mClientModeImpl.syncGetSpecificNetwork(this.mClientModeImplChannel, networkId)) == null) {
            return false;
        }
        return config.semIsVendorSpecificSsid;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isSamsungSpecificAp(int networkId, String bssid) {
        ScanDetailCache cache;
        if (networkId == -1 || (cache = this.mWifiConfigManager.getScanDetailCacheForNetwork(networkId)) == null) {
            return false;
        }
        ScanResult result = cache.getScanResult(bssid);
        if (result != null && DBG) {
            Log.d(TAG, result.capabilities);
        }
        if (result == null || !result.capabilities.contains("[SEC")) {
            return false;
        }
        return true;
    }

    public int getSmartNetworkSwitchAvailability() {
        int simState;
        ServiceState ss;
        Context context = this.mContext;
        if (context == null) {
            Log.e(TAG, "context is null.");
            return -1;
        }
        int result = 1;
        boolean mMobilePolicyDataEnable = ((ConnectivityManager) context.getSystemService("connectivity")).semIsMobilePolicyDataEnabled();
        boolean z = false;
        boolean isAirplaneMode = Settings.Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) != 0;
        boolean isMobileDataEnabled = Settings.Global.getInt(this.mContext.getContentResolver(), "mobile_data", 1) != 0;
        TelephonyManager telephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
        if (telephonyManager == null) {
            simState = 0;
            Log.e(TAG, "TelephonyManager is null.");
        } else if (TelephonyManager.getDefault().getPhoneCount() > 1) {
            int simState1 = telephonyManager.getSimState(0);
            int simState2 = telephonyManager.getSimState(1);
            if (simState1 == 5 || simState2 == 5) {
                simState = 5;
            } else {
                simState = 0;
            }
            if (DBG) {
                Log.d(TAG, "simState1 = " + simState1 + ", simState2 = " + simState2 + ", simState = " + simState);
            }
        } else {
            simState = telephonyManager.getSimState();
        }
        boolean mobileDataBlockedByRoaming = false;
        if (!(telephonyManager == null || (ss = telephonyManager.getServiceState()) == null)) {
            mobileDataBlockedByRoaming = ss.getDataRoaming();
        }
        if (simState != 5 && (!DBG || !SystemProperties.get("SimCheck.disable").equals("1"))) {
            result = 2;
        } else if (isAirplaneMode) {
            result = 3;
        } else if (!isMobileDataEnabled || !mMobilePolicyDataEnable) {
            result = 4;
        } else if (mobileDataBlockedByRoaming) {
            result = 5;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Airplane Mode Off / Mobile Data Enabled / SIM State-Ready / isMobilePolicyDataEnable / !mobileDataBlockedByRoaming / result : ");
        sb.append(!isAirplaneMode);
        sb.append(" / ");
        sb.append(isMobileDataEnabled);
        sb.append(" / ");
        sb.append(simState == 5);
        sb.append(" / ");
        sb.append(mMobilePolicyDataEnable);
        sb.append(" / ");
        if (!mobileDataBlockedByRoaming) {
            z = true;
        }
        sb.append(z);
        sb.append(" / ");
        sb.append(result);
        Log.d(TAG, sb.toString());
        return result;
    }

    /* JADX WARNING: Code restructure failed: missing block: B:12:0x0035, code lost:
        r4 = move-exception;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:13:0x0036, code lost:
        if (r2 != null) goto L_0x0038;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:15:?, code lost:
        r2.close();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:16:0x003c, code lost:
        r5 = move-exception;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:17:0x003d, code lost:
        r3.addSuppressed(r5);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:18:0x0040, code lost:
        throw r4;
     */
    public String readQtableFile(String fileNamePath) {
        String res = new String();
        try {
            BufferedReader br = Files.newBufferedReader(Paths.get(fileNamePath, new String[0]), Charset.forName("UTF-8"));
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    br.close();
                    break;
                }
                res = res + line;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }
}
