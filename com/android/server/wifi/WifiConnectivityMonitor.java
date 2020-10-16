package com.android.server.wifi;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.usage.IUsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.icu.text.SimpleDateFormat;
import android.net.ConnectivityManager;
import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.util.InterfaceParams;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.LocalLog;
import android.util.Log;
import android.util.LruCache;
import android.widget.Toast;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.wifi.ClientModeImpl;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.hotspot2.SystemInfo;
import com.android.server.wifi.rtt.RttServiceImpl;
import com.android.server.wifi.tcp.WifiSwitchForIndividualAppsService;
import com.android.server.wifi.tcp.WifiTransportLayerMonitor;
import com.android.server.wifi.util.TelephonyUtil;
import com.samsung.android.app.usage.IUsageStatsWatcher;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.net.wifi.OpBrandingLoader;
import com.samsung.android.server.wifi.SemWifiConstants;
import com.samsung.android.server.wifi.Stopwatch;
import com.samsung.android.server.wifi.mobilewips.framework.MobileWipsFrameworkService;
import com.samsung.android.server.wifi.sns.SnsBigDataManager;
import com.samsung.android.server.wifi.sns.SnsBigDataSCNT;
import com.samsung.android.server.wifi.sns.SnsBigDataSSIV;
import com.samsung.android.server.wifi.sns.SnsBigDataSSVI;
import com.samsung.android.server.wifi.sns.SnsBigDataWFQC;
import com.samsung.android.service.reactive.ReactiveServiceManager;
import com.sec.android.app.C0852CscFeatureTagCommon;
import com.sec.android.app.CscFeatureTagRIL;
import com.sec.android.app.CscFeatureTagWifi;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WifiConnectivityMonitor extends StateMachine {
    private static final int ACTIVITY_CHECK_POLL = 135221;
    private static final int ACTIVITY_CHECK_START = 135219;
    private static final int ACTIVITY_CHECK_STOP = 135220;
    private static final int ACTIVITY_RESTART_AGGRESSIVE_MODE = 135222;
    public static final int ANALYTICS_DISCONNECT_REASON_ARP_NO_RESPONSE = 3;
    public static final int ANALYTICS_DISCONNECT_REASON_DNS_DNS_REFUSED = 2;
    public static final int ANALYTICS_DISCONNECT_REASON_DNS_PRIVATE_IP = 1;
    public static final int ANALYTICS_DISCONNECT_REASON_RESERVED = 0;
    public static final long AUTO_NETWORK_SWITCH_TURNED_ON_SCAN_DEFER_DURATION = 12000;
    private static final int BASE = 135168;
    private static final String BIG_DATA_SNS_SCNT_INTENT = "com.samsung.android.server.wifi.SCNT";
    public static final int BRCM_BIGDATA_ECNT_NOINTERNET_ARP = 1;
    public static final int BRCM_BIGDATA_ECNT_NOINTERNET_GENERAL = 0;
    public static final int BRCM_BIGDATA_ECNT_NOINTERNET_TXBAD = 2;
    private static final int BSSID_STAT_CACHE_SIZE = 20;
    private static final int BSSID_STAT_EMPTY_COUNT = 3;
    private static final int BSSID_STAT_RANGE_HIGH_DBM = -45;
    private static final int BSSID_STAT_RANGE_LOW_DBM = -105;
    static final int CAPTIVE_PORTAL_DETECTED = 135471;
    public static final int CHECK_ALTERNATIVE_NETWORKS = 135288;
    private static final String CHN_PUBLIC_DNS_IP = "114.114.114.114";
    static final int CMD_CHANGE_WIFI_ICON_VISIBILITY = 135476;
    static final int CMD_CHECK_ALTERNATIVE_FOR_CAPTIVE_PORTAL = 135481;
    public static final int CMD_CONFIG_SET_CAPTIVE_PORTAL = 135289;
    public static final int CMD_CONFIG_UPDATE_NETWORK_SELECTION = 135290;
    private static final int CMD_DELAY_NETSTATS_SESSION_INIT = 135225;
    static final int CMD_ELE_BY_GEO_DETECTED = 135284;
    static final int CMD_ELE_DETECTED = 135283;
    public static final int CMD_IWC_ACTIVITY_CHECK_POLL = 135376;
    public static final int CMD_IWC_CURRENT_QAI = 135368;
    public static final int CMD_IWC_ELE_DETECTED = 135374;
    public static final int CMD_IWC_QC_RESULT = 135372;
    private static final int CMD_IWC_QC_RESULT_TIMEOUT = 135375;
    public static final int CMD_IWC_REQUEST_INTERNET_CHECK = 135371;
    public static final int CMD_IWC_REQUEST_NETWORK_SWITCH_TO_MOBILE = 135369;
    public static final int CMD_IWC_REQUEST_NETWORK_SWITCH_TO_WIFI = 135370;
    public static final int CMD_IWC_RSSI_FETCH_RESULT = 135373;
    static final int CMD_LINK_GOOD_ENTERED = 135398;
    static final int CMD_LINK_POOR_ENTERED = 135399;
    static final int CMD_NETWORK_PROPERTIES_UPDATED = 135478;
    static final int CMD_PROVISIONING_FAIL = 135401;
    static final int CMD_QUALITY_CHECK_BY_SCORE = 135388;
    static final int CMD_REACHABILITY_LOST = 135400;
    static final int CMD_RECOVERY_TO_HIGH_QUALITY_FROM_ELE = 135390;
    static final int CMD_RECOVERY_TO_HIGH_QUALITY_FROM_LOW_LINK = 135389;
    static final int CMD_ROAM_START_COMPLETE = 135477;
    private static final int CMD_RSSI_FETCH = 135188;
    static final int CMD_TRAFFIC_POLL = 135193;
    private static final int CMD_TRANSIT_ON_SWITCHABLE = 135300;
    private static final int CMD_TRANSIT_ON_VALID = 135299;
    static final int CMD_UPDATE_CURRENT_BSSID_ON_DNS_RESULT = 135489;
    static final int CMD_UPDATE_CURRENT_BSSID_ON_DNS_RESULT_TYPE = 135490;
    static final int CMD_UPDATE_CURRENT_BSSID_ON_QC_RESULT = 135491;
    static final int CMD_UPDATE_CURRENT_BSSID_ON_THROUGHPUT_UPDATE = 135488;
    private static final String CONFIG_SECURE_SVC_INTEGRATION = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGSECURESVCINTEGRATION);
    private static final int CONNECTIVITY_VALIDATION_BLOCK = 135205;
    private static final int CONNECTIVITY_VALIDATION_RESULT = 135206;
    private static final String CSC_VENDOR_NOTI_STYLE = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGWIFINOTIFICATIONSTYLE);
    private static boolean DBG = Debug.semIsProductDev();
    private static final int DNS_CHECK_RESULT_DISCONNECTED = 9;
    private static final int DNS_CHECK_RESULT_NO_INTERNET = 1;
    private static final int DNS_CHECK_RESULT_NULLPOINTEREXCEPTION = 8;
    private static final int DNS_CHECK_RESULT_PRIVATE_IP = 2;
    private static final int DNS_CHECK_RESULT_PROXY_IGNORE = 11;
    private static final int DNS_CHECK_RESULT_REMAINED = 10;
    private static final int DNS_CHECK_RESULT_SECURITYEXCEPTION = 7;
    private static final int DNS_CHECK_RESULT_SUCCESS = 0;
    private static final int DNS_CHECK_RESULT_TIMEOUT = 3;
    private static final int DNS_CHECK_RESULT_TIMEOUT_ICMP_OK = 4;
    private static final int DNS_CHECK_RESULT_TIMEOUT_RSSI_OK = 5;
    private static final int DNS_CHECK_RESULT_UNKNOWNHOSTEXCEPTION = 6;
    private static final int EVENT_BSSID_CHANGE = 135175;
    private static final int EVENT_CONNECTIVITY_ACTION_CHANGE = 135179;
    private static final int EVENT_DHCP_SESSION_COMPLETE = 135237;
    private static final int EVENT_DHCP_SESSION_STARTED = 135236;
    private static final int EVENT_INET_CONDITION_ACTION = 135245;
    private static final int EVENT_INET_CONDITION_CHANGE = 135180;
    private static final int EVENT_LINK_DETECTION_DISABLED = 135247;
    private static final int EVENT_MOBILE_CONNECTED = 135232;
    private static final int EVENT_NETWORK_PROPERTIES_CHANGED = 135235;
    private static final int EVENT_NETWORK_REMOVED = 135244;
    private static final int EVENT_NETWORK_STATE_CHANGE = 135170;
    private static final int EVENT_PARALLEL_CP_CHECK_RESULT = 135246;
    private static final int EVENT_ROAM_COMPLETE = 135242;
    private static final int EVENT_ROAM_STARTED = 135241;
    private static final int EVENT_ROAM_TIMEOUT = 135249;
    private static final int EVENT_RSSI_CHANGE = 135171;
    private static final int EVENT_SCAN_COMPLETE = 135230;
    private static final int EVENT_SCAN_STARTED = 135229;
    private static final int EVENT_SCAN_TIMEOUT = 135231;
    private static final int EVENT_SCREEN_OFF = 135177;
    private static final int EVENT_SCREEN_ON = 135176;
    private static final int EVENT_SUPPLICANT_STATE_CHANGE = 135172;
    private static final int EVENT_USER_SELECTION = 135264;
    private static final int EVENT_WATCHDOG_SETTINGS_CHANGE = 135174;
    private static final int EVENT_WATCHDOG_TOGGLED = 135169;
    private static final int EVENT_WIFI_ICON_VSB = 135265;
    private static final int EVENT_WIFI_RADIO_STATE_CHANGE = 135173;
    private static final int EVENT_WIFI_TOGGLED = 135243;
    private static final double EXP_COEFFICIENT_MONITOR = 0.5d;
    private static final double EXP_COEFFICIENT_RECORD = 0.1d;
    static final int GOOD_LINK_DETECTED = 135190;
    private static final double GOOD_LINK_LOSS_THRESHOLD = 0.05d;
    private static final int GOOD_LINK_RSSI_RANGE_MAX = 20;
    private static final int GOOD_LINK_RSSI_RANGE_MIN = 5;
    static final int HANDLE_ON_AVAILABLE = 135470;
    static final int HANDLE_ON_LOST = 135469;
    static final int HANDLE_RESULT_ROAM_IN_HIGH_QUALITY = 135479;
    private static final int HIDE_ICON_HISTORY_COUNT_MAX = 100;
    private static final String IMS_REGISTRATION = "com.samsung.ims.action.IMS_REGISTRATION";
    private static final String INTERFACENAMEOFWLAN = "wlan0";
    static final int INVALIDATED_DETECTED = 135473;
    static final int INVALIDATED_DETECTED_AGAIN = 135475;
    private static final boolean INVALID_ICON_INVISIBILE = true;
    private static final int INVALID_OVERCOME_COUNT = 8;
    static final int LINK_DETECTION_DISABLED = 135191;
    private static final long LINK_MONITORING_SAMPLING_INTERVAL_MS = 500;
    private static long LINK_SAMPLING_INTERVAL_MS = 1000;
    public static final int LINK_STATUS_EXTRA_INFO_NONE = 0;
    public static final int LINK_STATUS_EXTRA_INFO_NO_INTERNET = 2;
    public static final int LINK_STATUS_EXTRA_INFO_POOR_LINK = 1;
    private static final int MAX_DNS_RESULTS_COUNT = 50;
    private static final int MAX_PACKET_RECORDS = 500;
    private static final int MAX_SCAN_COUNTRY_CODE_LOG_COUNT = 10;
    private static final int MAX_TIME_AVOID_LIMIT = 10;
    private static final int MESSAGE_NOT_TRIGGERED_FROM_WCM = 11111;
    private static final long MIN_MAINTAIN_CHINA_TIME = 86400000;
    private static final int MIN_SAMPLE_SIZE_TO_UPDATE_SCAN_COUNTRY_CODE = 5;
    private static final int MODE_AGG = 3;
    private static final int MODE_INVALID_NONSWITCHABLE = 1;
    private static final int MODE_INVALID_SWITCHABLE = 2;
    private static final int MODE_LOW_QUALITY = 3;
    private static final int MODE_NON_SWITCHABLE = 1;
    private static final int MODE_NORMAL = 2;
    private static final int MODE_NO_CHECK = 0;
    private static final int MODE_VALID = 0;
    private static final int MONITORING_TIMEOUT = 30;
    static final int NEED_FETCH_RSSI_AND_LINKSPEED = 135192;
    private static final String NETWISE_CONTENT_URL = "content://com.smithmicro.netwise.director.comcast.oem.apiprovider/managed_networks";
    private static final int NETWORK_STAT_CHECK_DNS = 135223;
    private static int NETWORK_STAT_HISTORY_COUNT_MAX = 10;
    private static final int NETWORK_STAT_SET_GOOD_RX_STATE_NOW = 135224;
    public static final int NUM_LOG_RECS_DBG = 1000;
    public static final int NUM_LOG_RECS_NORMAL = 500;
    static final int POOR_LINK_DETECTED = 135189;
    private static final double POOR_LINK_LOSS_THRESHOLD = 0.5d;
    private static final double POOR_LINK_MIN_VOLUME = ((((double) LINK_SAMPLING_INTERVAL_MS) * 2.0d) / 1000.0d);
    private static final int POOR_LINK_SAMPLE_COUNT = 3;
    private static final int QC_FAIL_DNS_CHECK_FAIL = 3;
    private static final int QC_FAIL_DNS_NO_DNS_LIST = 2;
    private static final int QC_FAIL_DNS_PRIVATE_IP = 1;
    private static final int QC_FAIL_DNS_TIMEOUT = 4;
    private static final int QC_FAIL_ELE_BY_GEO_DETECTED = 22;
    private static final int QC_FAIL_ELE_DETECTED = 21;
    private static final int QC_FAIL_FAST_DISCONNECTION = 14;
    private static final int QC_FAIL_FAST_DISCONNECTION_ROAM = 15;
    private static final int QC_FAIL_INVALIDATED_DETECTED = 17;
    private static final int QC_FAIL_POOR_LINK_LOSS = 12;
    private static final int QC_FAIL_POOR_LINK_LOSS_ROAM = 13;
    private static final int QC_FAIL_PROVISIONING_FAIL = 16;
    private static final int QC_FAIL_ROAM_FAIL_HIGH_QUALITY = 18;
    private static final int QC_FAIL_TRAFFIC_HIGH_LOSS = 11;
    private static final int QC_HISTORY_COUNT_MAX = 30;
    private static final int QC_RESET_204_CHECK_INTERVAL = 135208;
    static final int QC_RESULT_NOT_RECEIVED = 135480;
    private static final int QC_STEP_FIRST_QC_AT_CONNECTION = 3;
    private static final int QC_STEP_GOOGLE_DNS = 1;
    private static final int QC_STEP_RSSI_FETCH = 4;
    private static final int QC_STEP_VALIDATION_CHECK = 5;
    private static final int QC_TRIGGER_AGG_CONTINUOUS_LOSS_VALID = 23;
    private static final int QC_TRIGGER_AGG_POOR_RX = 38;
    private static final int QC_TRIGGER_AT_EVENT_DHCP_COMPLETE = 53;
    private static final int QC_TRIGGER_AT_EVENT_ROAM_COMPLETE = 52;
    private static final int QC_TRIGGER_AT_NETWORK_PROPERTIES_UPDATE = 51;
    private static final int QC_TRIGGER_BY_RECOVERY_FROM_ELE = 17;
    private static final int QC_TRIGGER_BY_SCORE_INVALID = 16;
    private static final int QC_TRIGGER_BY_SCORE_VALID = 27;
    private static final int QC_TRIGGER_CONTINUOUS_POOR_RX = 36;
    private static final int QC_TRIGGER_DNS_ABNORMAL_RESPONSE = 32;
    private static final int QC_TRIGGER_ELE_CHECK = 61;
    private static final int QC_TRIGGER_FAST_DISCONNECTION = 18;
    private static final int QC_TRIGGER_FIRST_QC_AT_CONNECTION = 1;
    private static final int QC_TRIGGER_GOOD_RSSI_INVALID = 12;
    private static final int QC_TRIGGER_IWC_REQUEST_INTERNET_CHECK = 2;
    private static final int QC_TRIGGER_LOSS_VALID = 24;
    private static final int QC_TRIGGER_LOSS_WEAK_SIGNAL_VALID = 25;
    private static final int QC_TRIGGER_LOSS_WEAK_SIGNAL_VALID_ROAM = 26;
    private static final int QC_TRIGGER_MAX_AVOID_TIME_INVALID = 13;
    private static final int QC_TRIGGER_NO_RX_DURING_STREAMING = 35;
    private static final int QC_TRIGGER_NO_SYNACK = 34;
    private static final int QC_TRIGGER_PERIODIC_DNS_CHECK_FAIL = 44;
    private static final int QC_TRIGGER_PROVISIONING_FAIL = 3;
    private static final int QC_TRIGGER_PULL_OUT_LINE = 31;
    private static final int QC_TRIGGER_REACHABILITY_LOST = 4;
    private static final int QC_TRIGGER_RECOVER_TO_VALID = 19;
    private static final int QC_TRIGGER_RESET_WATCHDOG = 5;
    private static final int QC_TRIGGER_RSSI_LEVEL0_VALID = 21;
    private static final int QC_TRIGGER_RX_VISIBLE_INVALID = 11;
    private static final int QC_TRIGGER_SCREEN_ON_GOOD_RSSI = 28;
    private static final int QC_TRIGGER_SING_DNS_CHECK_FAILURE = 45;
    private static final int QC_TRIGGER_STAYING_LAST_POOR_RSSI_VALID = 22;
    private static final int QC_TRIGGER_STAYING_LOW_MCS_DURING_STREAMING = 40;
    private static final int QC_TRIGGER_STOPPED_CONTINUOUS_STREAMING = 39;
    private static final int QC_TRIGGER_SUSPICIOUS_NO_RX_STATE = 33;
    private static final int QC_TRIGGER_SUSPICIOUS_POOR_RX_STATE = 37;
    private static final int QC_TRIGGER_TCP_BACKHAUL_DETECTION = 128;
    private static final int QC_TRIGGER_VALIDATION_CHECK_FORCE = 20;
    private static final int REPORT_NETWORK_CONNECTIVITY = 135204;
    private static final int REPORT_QC_RESULT = 135203;
    private static final int RESULT_DNS_CHECK = 135202;
    private static final int ROAM_TIMEOUT = 30000;
    private static final int RSSI_FETCH_DISCONNECTED_MODE = 2;
    private static final int RSSI_FETCH_GOOD_LINK_DETECT_MODE = 0;
    private static final int RSSI_FETCH_POOR_LINK_DETECT_MODE = 1;
    private static final int RSSI_PATCH_HISTORY_COUNT_MAX = 18000;
    private static final int SCAN_TIMEOUT = 5000;
    private static final int SCORE_QUALITY_CHECK_STATE_NONE = 0;
    private static final int SCORE_QUALITY_CHECK_STATE_POOR_CHECK = 3;
    private static final int SCORE_QUALITY_CHECK_STATE_POOR_MONITOR = 2;
    private static final int SCORE_QUALITY_CHECK_STATE_RECOVERY = 4;
    private static final int SCORE_QUALITY_CHECK_STATE_VALID_CHECK = 1;
    private static final int SCORE_TXBAD_RATIO_THRESHOLD = 15;
    static final int SECURITY_EAP = 3;
    static final int SECURITY_NONE = 0;
    static final int SECURITY_PSK = 2;
    static final int SECURITY_SAE = 4;
    static final int SECURITY_WEP = 1;
    private static boolean SMARTCM_DBG = false;
    public static final int SNS_FW_VERSION = 1;
    public static int SNS_VERSION = 1;
    static final int STOP_BLINKING_WIFI_ICON = 135468;
    private static final boolean SUPPORT_WPA3_SAE = (!"0".equals("2"));
    private static final String TAG = "WifiConnectivityMonitor";
    private static final int TCP_BACKHAUL_DETECTION_START = 135226;
    private static int TCP_STAT_LOGGING_FIRST = 1;
    private static int TCP_STAT_LOGGING_RESET = 0;
    private static int TCP_STAT_LOGGING_SECOND = 2;
    private static final int TRAFFIC_POLL_HISTORY_COUNT_MAX = 3000;
    static final int VALIDATED_DETECTED = 135472;
    static final int VALIDATED_DETECTED_AGAIN = 135474;
    private static int VALIDATION_CHECK_COUNT = 32;
    private static final int VALIDATION_CHECK_FORCE = 135207;
    private static int VALIDATION_CHECK_MAX_COUNT = 4;
    private static int VALIDATION_CHECK_TIMEOUT = WifiConnectivityManager.BSSID_BLACKLIST_EXPIRE_TIME_MS;
    private static final int VERSION = 3;
    private static Object lock = new Object();
    private static long mBigDataQualityCheckCycle = 86400000;
    private static int mCPCheckTriggeredByRoam = 0;
    private static boolean mCurrentApDefault = false;
    private static Object mCurrentBssidLock = new Object();
    private static boolean mInitialResultSentToSystemUi = false;
    private static boolean mIsComcastWifiSupported = SemCscFeature.getInstance().getBoolean(C0852CscFeatureTagCommon.TAG_CSCFEATURE_COMMON_SUPPORTCOMCASTWIFI);
    private static boolean mIsECNTReportedConnection = false;
    private static boolean mIsNoCheck = false;
    private static boolean mIsPassedLevel1State = false;
    private static boolean mIsValidState = false;
    private static int mLinkDetectMode = 2;
    private static int mMaxBigDataQualityCheckLogging = 10;
    private static final OpBrandingLoader.Vendor mOpBranding = OpBrandingLoader.getInstance().getOpBranding();
    public static long mPoorNetworkAvoidanceEnabledTime = 0;
    private static double mRawRssiEMA = -200.0d;
    private static boolean mUserSelectionConfirmed = false;
    private static final WifiInfo sDummyWifiInfo = new WifiInfo();
    private static final ConcurrentHashMap<String, LocalLog> sPktLogsWlan = new ConcurrentHashMap<>();
    private static double[] sPresetLoss;
    private static boolean sWifiOnly = false;
    private final long ACTIVE_THROUGHPUT_THRESHOLD = 500000;
    private int BSSID_DNS_RESULT_NO_INTERNET = 2;
    private int BSSID_DNS_RESULT_POOR_CONNECTION = 1;
    private int BSSID_DNS_RESULT_SUCCESS = 0;
    private int BSSID_DNS_RESULT_UNKNOWN = -1;
    private final int BUCKET_END = -20;
    private final int BUCKET_START = -90;
    private final int BUCKET_WIDTH = 10;
    private final boolean CSC_WIFI_SUPPORT_VZW_EAP_AKA = OpBrandingLoader.getInstance().isSupportEapAka();
    private final int[] INDEX_TO_SCORE = {0, 5, 10, 20, 30};
    private final String[] INDEX_TO_STRING = {SystemInfo.UNKNOWN_INFO, "Slow", "Okay", "Fast", "Very Fast"};
    private final String[] INDEX_TO_STRING_SHORT = {"UN", "SL", "OK", "FA", "VF"};
    private final int INITIAL_VALIDATION_TIME_OUT = 7000;
    private final int LEVEL_VALUE_MAX = 3;
    final int MAX_NETSTATTHREAD = 10;
    private final int MAX_THROUGHPUT_DECAY_RATE = 200000;
    private final int NM_QUALITY_CHECK_TIME_OUT = 20000;
    private final String OPEN_NETWORK_QOS_SHARING_VERSION = "1.72";
    public int QUALITY_INDEX_FAST = 3;
    public int QUALITY_INDEX_OKAY = 2;
    public int QUALITY_INDEX_SLOW = 1;
    public int QUALITY_INDEX_UNKNOWN = 0;
    public int QUALITY_INDEX_VERY_FAST = 4;
    private final long QUALITY_MIN_ACTIVE_TIME = 30000;
    private final long QUALITY_MIN_DWELL_TIME = 60000;
    private final long QUALITY_MIN_RX_TOTAL_BYTES = 1000000;
    private final long QUALITY_MIN_TX_TOTAL_PACKETS = 1500;
    private final int TOAST_INTERVAL = 30;
    private final int WEIGHT_ACTIVE_TPUT = 34;
    private final int WEIGHT_MAX_TPUT = 33;
    private final int WEIGHT_PER = 33;
    private boolean bSetQcResult = false;
    private int incrDnsResults = 0;
    private int incrScanResult = 0;
    private boolean isNetstatLoggingHangs = false;
    private String isQCExceptionSummary = "";
    private boolean m407ResponseReceived = false;
    private ActivityManager mActivityManager;
    private boolean mAggressiveModeEnabled;
    private boolean mAirPlaneMode = false;
    private final AlarmManager mAlarmMgr;
    private int mAnalyticsDisconnectReason = 0;
    private String mApOui = null;
    private int mBigDataQualityCheckLoggingCount = 0;
    private long mBigDataQualityCheckStartOfCycle = -1;
    private BroadcastReceiver mBroadcastReceiver;
    private LruCache<String, BssidStatistics> mBssidCache = new LruCache<>(20);
    private CaptivePortalState mCaptivePortalState = new CaptivePortalState();
    private boolean mCheckRoamedNetwork = false;
    private final ClientModeImpl mClientModeImpl;
    private boolean mCmccAlertSupport = false;
    private ConnectedState mConnectedState = new ConnectedState();
    private ConnectivityManager mConnectivityManager = null;
    private LocalLog mConnectivityPacketLogForWlan0;
    private ContentResolver mContentResolver = null;
    private Context mContext;
    private String mCountryCodeFromScanResult = null;
    private String mCountryIso = null;
    private BssidStatistics mCurrentBssid;
    private int mCurrentLinkSpeed;
    private VolumeWeightedEMA mCurrentLoss = null;
    private int mCurrentMode = 1;
    int mCurrentNetstatThread = 0;
    private QcFailHistory mCurrentQcFail = new QcFailHistory();
    private int mCurrentRssi;
    private int mCurrentSignalLevel;
    private boolean mDataRoamingSetting = false;
    private boolean mDataRoamingState = false;
    private DefaultState mDefaultState = new DefaultState();
    private String mDeviceCountryCode = null;
    private long mDnsThreadID = 0;
    private int mEleGoodScoreCnt = 0;
    private final BssidStatistics mEmptyBssid = new BssidStatistics("00:00:00:00:00:00", -1);
    private EvaluatedState mEvaluatedState = new EvaluatedState();
    private String mFirstLogged = "";
    private String mFirstTCPLoggedTime = "";
    private long mFrontAppAppearedTime;
    private int mGoodScoreCount = 0;
    private int mGoodScoreTotal = 0;
    private int mGoodTargetCount;
    private String[] mHideIconHistory = new String[100];
    private int mHideIconHistoryHead = 0;
    private int mHideIconHistoryTotal = 0;
    private AsyncChannel mIWCChannel = null;
    private boolean mImsRegistered = false;
    private boolean mInAggGoodStateNow = false;
    private IntentFilter mIntentFilter;
    private long mInvalidStartTime = 0;
    private InvalidState mInvalidState = new InvalidState();
    private boolean mInvalidStateTesting = false;
    private int mInvalidatedTime = 0;
    private QcFailHistory mInvalidationFailHistory = new QcFailHistory();
    private int mInvalidationRssi;
    private boolean mIs204CheckInterval = false;
    private boolean mIsFmcNetwork = false;
    private boolean mIsIcmpPingWaiting = false;
    private boolean mIsInDhcpSession = false;
    private boolean mIsInRoamSession = false;
    private boolean mIsMobileActiveNetwork = false;
    private boolean mIsRoamingNetwork = false;
    private boolean mIsScanning = false;
    private boolean mIsScreenOn = true;
    private boolean mIsUsingProxy = false;
    private boolean mIsWifiEnabled;
    private int mIwcCurrentQai = -1;
    private int mIwcRequestQcId = -1;
    private int mLastCheckedMobileHotspotNetworkId = -1;
    private boolean mLastCheckedMobileHotspotValue = false;
    private long mLastChinaConfirmedTime = 0;
    private int mLastGoodScore = 1000;
    private String mLastLogged = "";
    private int mLastPoorScore = 100;
    private String mLastTCPLoggedTime = "";
    private int mLastTxBad;
    private int mLastTxGood;
    int mLastVisibilityOfWifiIcon = 0;
    private int mLatestIcmpPingRtt = -1;
    private Level1State mLevel1State = new Level1State();
    private Level2State mLevel2State = new Level2State();
    private boolean mLevel2StateTransitionPending = false;
    private int mLinkLossOccurred = 0;
    private LinkProperties mLinkProperties;
    private int mLossHasGone = 0;
    private int mLossSampleCount;
    private QcFailHistory mLowQualityFailHistory = new QcFailHistory();
    private int mMaxAvoidCount = 0;
    private boolean mMobilePolicyDataEnable = true;
    private boolean mMptcpEnabled = false;
    int mNetstatBufferCount = 0;
    ArrayList<String> mNetstatTestBuffer = new ArrayList<>();
    boolean[] mNetstatThreadInUse;
    private ExecutorService mNetstatThreadPool = null;
    private Network mNetwork;
    private NetworkCallbackController mNetworkCallbackController = null;
    private String[] mNetworkStatHistory;
    private int mNetworkStatHistoryIndex;
    private boolean mNetworkStatHistoryUpdate;
    private NetworkStatsAnalyzer mNetworkStatsAnalyzer;
    private NotConnectedState mNotConnectedState = new NotConnectedState();
    private WifiConnectivityNotificationManager mNotifier;
    private int mNumOfToggled;
    private ArrayList<OpenNetworkQosCallback> mOpenNetworkQosCallbackList = null;
    private int mOvercomingCount = 0;
    private WcmConnectivityPacketTracker mPacketTrackerForWlan0;
    private ParameterManager mParam;
    private boolean mPoorCheckInProgress = false;
    private int mPoorConnectionDisconnectedNetId = -1;
    private String mPoorNetworkAvoidanceSummary = null;
    private boolean mPoorNetworkDetectionEnabled;
    private String mPoorNetworkDetectionSummary = null;
    private long mPrevAppAppearedTime;
    private double mPreviousLoss = POOR_LINK_MIN_VOLUME;
    private String mPreviousPackageName = "default";
    private int[] mPreviousScore = new int[3];
    private long mPreviousTxBad = 0;
    private long mPreviousTxBadTxGoodRatio = 0;
    private long mPreviousTxSuccess = 0;
    private int mPreviousUid = -1;
    private int mPrevoiusScoreAverage = 0;
    private String mProxyAddress = null;
    private int mProxyPort = -1;
    private String[] mQcDumpHistory = new String[30];
    private String mQcDumpVer = "2.1";
    private QcFailHistory[] mQcHistory = new QcFailHistory[30];
    private int mQcHistoryHead = -1;
    private int mQcHistoryTotal = 0;
    LinkedList<Long> mRawRssi = new LinkedList<>();
    private boolean mReportedPoorNetworkDetectionEnabled = false;
    private int mReportedQai = -1;
    private int mRssiFetchToken = 0;
    private String[] mRssiPatchHistory = new String[RSSI_PATCH_HISTORY_COUNT_MAX];
    private int mRssiPatchHistoryHead = -1;
    private int mRssiPatchHistoryTotal = 0;
    private long mRxPkts;
    private long mSamplingIntervalMS = LINK_SAMPLING_INTERVAL_MS;
    private List<ScanResult> mScanResults = new ArrayList();
    private final Object mScanResultsLock = new Object();
    private int mScoreIntervalCnt = 0;
    private int mScoreQualityCheckMode = 0;
    private boolean mScoreSkipModeEnalbed = false;
    private boolean mScoreSkipPolling = false;
    private SnsBigDataManager mSnsBigDataManager;
    private SnsBigDataSCNT mSnsBigDataSCNT;
    private INetworkStatsService mStatsService = null;
    private INetworkStatsSession mStatsSession = null;
    private int mStayingPoorRssi = 0;
    private TelephonyManager mTelephonyManager = null;
    private String mTempLogged = "";
    private String mTempTCPLoggedTime = "";
    private String[] mTrafficPollHistory = new String[3000];
    private int mTrafficPollHistoryHead = -1;
    private int mTrafficPollHistoryTotal = 0;
    private String mTrafficPollPackageName = "";
    private int mTrafficPollToken = 0;
    private boolean mTransitionPendingByIwc = false;
    private int mTransitionScore = 0;
    private long mTxPkts;
    private boolean mUIEnabled;
    private boolean mUsagePackageChanged = false;
    private IUsageStatsManager mUsageStatsManager;
    private String mUsageStatsPackageName;
    private int mUsageStatsUid;
    private final IUsageStatsWatcher.Stub mUsageStatsWatcher = new IUsageStatsWatcher.Stub() {
        /* class com.android.server.wifi.WifiConnectivityMonitor.C04425 */

        public void noteResumeComponent(ComponentName resumeComponentName, Intent intent) {
            if (resumeComponentName == null) {
                try {
                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "resumeComponentName is null");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                String packagName = resumeComponentName.getPackageName();
                if (!WifiConnectivityMonitor.this.mUsageStatsPackageName.equals(packagName)) {
                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "IUsageStatsWatcher noteResumeComponent: " + packagName);
                    }
                    WifiConnectivityMonitor.this.mPreviousUid = WifiConnectivityMonitor.this.mUsageStatsUid;
                    WifiConnectivityMonitor.this.mPreviousPackageName = WifiConnectivityMonitor.this.mUsageStatsPackageName;
                    WifiConnectivityMonitor.this.mPrevAppAppearedTime = WifiConnectivityMonitor.this.mFrontAppAppearedTime;
                    ApplicationInfo appInfo = WifiConnectivityMonitor.this.mContext.getPackageManager().getApplicationInfo(packagName, 128);
                    WifiConnectivityMonitor.this.mUsageStatsUid = appInfo.uid;
                    WifiConnectivityMonitor.this.mUsageStatsPackageName = packagName;
                    WifiConnectivityMonitor.this.mUsagePackageChanged = true;
                    WifiConnectivityMonitor.this.mFrontAppAppearedTime = System.currentTimeMillis();
                } else if (WifiConnectivityMonitor.SMARTCM_DBG) {
                    Log.d(WifiConnectivityMonitor.TAG, "resumeComponentName same package: " + WifiConnectivityMonitor.this.mUsageStatsPackageName);
                }
            }
        }

        public void notePauseComponent(ComponentName pauseComponentName) {
        }

        public void noteStopComponent(ComponentName stopComponentName) {
        }
    };
    private boolean mUserOwner = true;
    private ValidNoCheckState mValidNoCheckState = new ValidNoCheckState();
    private ValidNonSwitchableState mValidNonSwitchableState = new ValidNonSwitchableState();
    private long mValidStartTime = 0;
    private ValidState mValidState = new ValidState();
    private ValidSwitchableState mValidSwitchableState = new ValidSwitchableState();
    private int mValidatedTime = 0;
    private boolean mValidationBlock = false;
    private int mValidationCheckCount = 0;
    private long mValidationCheckEnabledTime = 0;
    private boolean mValidationCheckMode = false;
    private int mValidationCheckTime = 32;
    private int mValidationResultCount = 0;
    Message mWCMQCResult = null;
    private ClientModeImpl.WifiClientModeChannel mWifiClientModeChannel;
    private final WifiConfigManager mWifiConfigManager;
    private WifiEleStateTracker mWifiEleStateTracker = null;
    private WifiInfo mWifiInfo;
    private WifiManager mWifiManager;
    private final WifiNative mWifiNative;
    private boolean mWifiNeedRecoveryFromEle = false;
    private WifiSwitchForIndividualAppsService mWifiSwitchForIndividualAppsService;
    private WifiTransportLayerMonitor mWifiTransportLayerMonitor;
    private String[] summaryCountryCodeFromScanResults = new String[10];
    private String[] summaryDnsResults = new String[50];
    private int toastCount = 0;

    static /* synthetic */ int access$10208(WifiConnectivityMonitor x0) {
        int i = x0.mStayingPoorRssi;
        x0.mStayingPoorRssi = i + 1;
        return i;
    }

    static /* synthetic */ int access$10304(WifiConnectivityMonitor x0) {
        int i = x0.mLossSampleCount + 1;
        x0.mLossSampleCount = i;
        return i;
    }

    static /* synthetic */ int access$10404(WifiConnectivityMonitor x0) {
        int i = x0.mOvercomingCount + 1;
        x0.mOvercomingCount = i;
        return i;
    }

    static /* synthetic */ int access$10508(WifiConnectivityMonitor x0) {
        int i = x0.mLinkLossOccurred;
        x0.mLinkLossOccurred = i + 1;
        return i;
    }

    static /* synthetic */ int access$10604(WifiConnectivityMonitor x0) {
        int i = x0.mLossHasGone + 1;
        x0.mLossHasGone = i;
        return i;
    }

    static /* synthetic */ int access$13604(WifiConnectivityMonitor x0) {
        int i = x0.mRssiFetchToken + 1;
        x0.mRssiFetchToken = i;
        return i;
    }

    static /* synthetic */ int access$13608(WifiConnectivityMonitor x0) {
        int i = x0.mRssiFetchToken;
        x0.mRssiFetchToken = i + 1;
        return i;
    }

    static /* synthetic */ int access$16304(WifiConnectivityMonitor x0) {
        int i = x0.mGoodTargetCount + 1;
        x0.mGoodTargetCount = i;
        return i;
    }

    static /* synthetic */ int access$16604(WifiConnectivityMonitor x0) {
        int i = x0.mTrafficPollToken + 1;
        x0.mTrafficPollToken = i;
        return i;
    }

    static /* synthetic */ int access$23508(WifiConnectivityMonitor x0) {
        int i = x0.toastCount;
        x0.toastCount = i + 1;
        return i;
    }

    static /* synthetic */ int access$5508(WifiConnectivityMonitor x0) {
        int i = x0.mValidationResultCount;
        x0.mValidationResultCount = i + 1;
        return i;
    }

    static /* synthetic */ int access$5608(WifiConnectivityMonitor x0) {
        int i = x0.mValidationCheckCount;
        x0.mValidationCheckCount = i + 1;
        return i;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private ConnectivityManager getCm() {
        if (this.mConnectivityManager == null) {
            this.mConnectivityManager = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        }
        return this.mConnectivityManager;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private ClientModeImpl.WifiClientModeChannel getClientModeChannel() {
        if (this.mWifiClientModeChannel == null) {
            this.mWifiClientModeChannel = this.mClientModeImpl.makeWifiClientModeChannel();
        }
        return this.mWifiClientModeChannel;
    }

    /* access modifiers changed from: package-private */
    public class ParameterManager {
        public final int AggressiveModeHigherPassBytes = 0;
        public final int AggressiveModeMonitorLinkLoss = 2;
        public final int AggressiveModeQCTriggerByRssi = 1;
        public final int DEFAULT_ENHANCED_TARGET_RSSI = 5;
        public final int DEFAULT_GOOD_RX_PACKETS_BASE = 30;
        public final int DEFAULT_MIN_DNS_RESPONSES = 1;
        public final int DEFAULT_MSS = 1430;
        public final int DEFAULT_NO_RX_PACKETS_LIMIT = 2;
        public final int DEFAULT_NUM_DNS_PINGS = 2;
        public final int DEFAULT_PACKET_SIZE = 1484;
        public final int DEFAULT_POOR_RX_PACKETS_LIMIT = 15;
        public final int DEFAULT_QC_TIMEOUT_SEC = 1;
        public final int DEFAULT_RESTORE_TARGET_RSSI_SEC = 30;
        public final int DEFAULT_SINGLE_DNS_PING_TIMEOUT_MS = 10000;
        public final String DEFAULT_URL = "http://www.google.com";
        public final String DEFAULT_URL_CHINA = "http://www.qq.com";
        public String DEFAULT_URL_STRING = "www.google.com";
        public final String DEFAULT_URL_STRING_CHINA = "www.qq.com";
        public final int DNS_INTRATEST_PING_INTERVAL_MS = 0;
        public final int DNS_START_DELAY_MS = 100;
        public final double FD_DISCONNECT_DEVIATION_EMA_THRESHOLD = 4.0d;
        public double FD_DISCONNECT_THRESHOLD = 8.0d;
        public int FD_EMA_ALPHA = 9;
        public int FD_EVALUATE_COUNT = 6;
        public int FD_EVAL_LEAD_TIME = 2;
        public int FD_MA_UNIT = 3;
        public int FD_MA_UNIT_SAMPLE_COUNT = 6;
        public final int FD_RAW_RSSI_SIZE = (this.FD_MA_UNIT_SAMPLE_COUNT + this.FD_EVALUATE_COUNT);
        public int FD_RSSI_LOW_THRESHOLD = -80;
        public final double FD_RSSI_SLOPE_EXP_COEFFICIENT = 0.2d;
        public final String PATH_OF_RESULT = "/data/log/";
        public final int QC_INIT_ID = 1;
        public final int RSSI_THRESHOLD_AGG_MODE_2G = -70;
        public final int RSSI_THRESHOLD_AGG_MODE_5G = -75;
        public final String SMARTCM_VALUE_FILE = "/data/misc/wifi/.smartCM";
        public final int TCP_HEADER_SIZE = 54;
        public final int VERIFYING_STATE_PASS_PACKETS_AGGRESSIVE_MODE = 75;
        public int WEAK_SIGNAL_FREQUENT_QC_CYCLE_SEC = 30;
        public int WEAK_SIGNAL_POOR_DETECTED_RSSI_MIN = -89;
        public final String WLANQCPATH_PROP_NAME = "wlan.qc.path";
        public boolean[] mAggressiveModeFeatureEnabled = {true, true, true};
        public int mGoodRxPacketsBase;
        public long mLastPoorDetectedTime = 0;
        public int mNoRxPacketsLimit;
        public int mPassBytes;
        public int mPassBytesAggressiveMode;
        public int mPoorRxPacketsLimit;
        public int mRssiThresholdAggMode2G;
        public int mRssiThresholdAggMode5G;
        public int mRssiThresholdAggModeCurrentAP;
        public long mWeakSignalQCStartTime = 0;

        public ParameterManager() {
            resetParameters();
        }

        public void resetParameters() {
            boolean unused = WifiConnectivityMonitor.SMARTCM_DBG = false;
            this.mRssiThresholdAggMode2G = -70;
            this.mRssiThresholdAggMode5G = -75;
            this.mNoRxPacketsLimit = 2;
            this.mPoorRxPacketsLimit = 15;
            this.mGoodRxPacketsBase = 30;
            this.mPassBytesAggressiveMode = 111300;
        }

        public void setEvaluationParameters() {
            if (WifiConnectivityMonitor.DBG) {
                setEvaluationParameters(loadSmartCMFile());
            }
        }

        public void setEvaluationParameters(String data) {
            String[] line;
            if (!(data == null || (line = data.split("\n")) == null)) {
                for (String str : line) {
                    if (str != null && str.startsWith("dbg=")) {
                        boolean unused = WifiConnectivityMonitor.SMARTCM_DBG = getStringValue(str, WifiConnectivityMonitor.SMARTCM_DBG ? "1" : "0").equals("1");
                        Log.i(WifiConnectivityMonitor.TAG, "SMARTCM_DBG : " + WifiConnectivityMonitor.SMARTCM_DBG);
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
                Log.e(WifiConnectivityMonitor.TAG, "wrong int:" + str);
                return defaultValue;
            }
        }

        private long getLongValue(String str, long defaultValue) {
            try {
                return Long.parseLong(getStringValue(str, String.valueOf(defaultValue)));
            } catch (Exception e) {
                Log.e(WifiConnectivityMonitor.TAG, "wrong double:" + str);
                return defaultValue;
            }
        }

        private double getDoubleValue(String str, double defaultValue) {
            try {
                return Double.parseDouble(getStringValue(str, String.valueOf(defaultValue)));
            } catch (Exception e) {
                Log.e(WifiConnectivityMonitor.TAG, "wrong double:" + str);
                return defaultValue;
            }
        }

        private String loadSmartCMFile() {
            FileReader reader = null;
            BufferedReader br = null;
            String data = "";
            try {
                FileReader reader2 = new FileReader("/data/misc/wifi/.smartCM");
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
                            Log.e(WifiConnectivityMonitor.TAG, "IOException closing file");
                            return null;
                        }
                    }
                }
            } catch (Exception e2) {
                Log.d(WifiConnectivityMonitor.TAG, "no file");
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
                        Log.e(WifiConnectivityMonitor.TAG, "IOException closing file");
                        throw th;
                    }
                }
                if (0 != 0) {
                    reader.close();
                }
                throw th;
            }
        }

        public int createSmartCMFile(String data) {
            FileWriter out = null;
            int ret = -1;
            try {
                File file = new File("/data/misc/wifi/.smartCM");
                if (file.exists()) {
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "removed smartCM");
                    }
                    file.delete();
                }
                file.createNewFile();
                FileWriter out2 = new FileWriter("/data/misc/wifi/.smartCM");
                if (data != null) {
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "created smartCM");
                    }
                    out2.write(data);
                }
                ret = 1;
                try {
                    out2.close();
                } catch (IOException e) {
                    Log.e(WifiConnectivityMonitor.TAG, "IOException closing file");
                }
            } catch (Exception e2) {
                Log.e(WifiConnectivityMonitor.TAG, "Exception creating file");
                if (0 != 0) {
                    out.close();
                }
            } catch (Throwable th) {
                if (0 != 0) {
                    try {
                        out.close();
                    } catch (IOException e3) {
                        Log.e(WifiConnectivityMonitor.TAG, "IOException closing file");
                    }
                }
                throw th;
            }
            return ret;
        }

        public String getAggressiveModeFeatureStatus() {
            return "(" + this.mAggressiveModeFeatureEnabled[0] + "/" + this.mAggressiveModeFeatureEnabled[1] + "/" + this.mAggressiveModeFeatureEnabled[2] + ")";
        }
    }

    /* access modifiers changed from: private */
    public static class QcFailHistory {
        int apIndex = -1;
        boolean avoidance = false;
        String bssid = "";
        int bytes = -1;
        List<InetAddress> currentDnsList = null;
        int dataRate = -1;
        boolean detection = false;
        int error = -1;
        int line = -1;
        String netstat = "";
        int qcStep = -1;
        int qcStepTemp = -1;
        int qcTrigger = -1;
        int qcTriggerTemp = -1;
        int qcType = -1;
        int qcUrlIndex = -1;
        int rssi = -1;
        String ssid = "";
        String state = "";
        Date time;

        QcFailHistory() {
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void initNetworkStatHistory() {
        this.mNetworkStatHistory = new String[NETWORK_STAT_HISTORY_COUNT_MAX];
        this.mNetworkStatHistoryIndex = 0;
        this.mNetworkStatHistoryUpdate = true;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void addNetworkStatHistory(String log) {
        String[] strArr = this.mNetworkStatHistory;
        if (strArr != null) {
            int i = this.mNetworkStatHistoryIndex;
            strArr[i] = log;
            int i2 = i + 1;
            this.mNetworkStatHistoryIndex = i2;
            if (i2 >= NETWORK_STAT_HISTORY_COUNT_MAX) {
                this.mNetworkStatHistoryIndex = 0;
            }
        }
    }

    private String getNetworkStatHistory() {
        if (this.mNetworkStatHistory == null) {
            return null;
        }
        String str = "";
        int idx = this.mNetworkStatHistoryIndex;
        for (int count = 0; count < NETWORK_STAT_HISTORY_COUNT_MAX; count++) {
            if (this.mNetworkStatHistory[idx] != null) {
                str = str + this.mNetworkStatHistory[idx] + "/";
            }
            idx = (idx + 1) % NETWORK_STAT_HISTORY_COUNT_MAX;
        }
        return str;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private WifiInfo syncGetCurrentWifiInfo() {
        WifiNative.SignalPollResult pollResult;
        if (this.mWifiManager == null) {
            this.mWifiManager = (WifiManager) this.mContext.getSystemService("wifi");
        }
        WifiInfo wifiInfo = this.mWifiManager.getConnectionInfo();
        if (wifiInfo != null) {
            this.mCurrentLinkSpeed = wifiInfo.getLinkSpeed();
            this.mCurrentRssi = wifiInfo.getRssi();
            if (!this.mIsScreenOn && (pollResult = this.mWifiNative.signalPoll(INTERFACENAMEOFWLAN)) != null) {
                wifiInfo.setRssi(pollResult.currentRssi);
            }
            return wifiInfo;
        }
        Log.e(TAG, "WifiInfo is null");
        this.mCurrentLinkSpeed = 0;
        this.mCurrentRssi = -100;
        return sDummyWifiInfo;
    }

    private WifiConnectivityMonitor(Context context, ClientModeImpl clientModeImpl, WifiInjector wifiInjector) {
        super(TAG);
        this.mContext = context;
        this.mContentResolver = context.getContentResolver();
        this.mWifiManager = (WifiManager) context.getSystemService("wifi");
        this.mClientModeImpl = clientModeImpl;
        this.mWifiConfigManager = wifiInjector.getWifiConfigManager();
        this.mWifiNative = wifiInjector.getWifiNative();
        this.mNotifier = new WifiConnectivityNotificationManager(this.mContext);
        getClientModeChannel();
        try {
            this.mClientModeImpl.registerWifiNetworkCallbacks(new WCMCallbacks());
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        getCm();
        this.mNetworkCallbackController = new NetworkCallbackController();
        addState(this.mDefaultState);
        addState(this.mNotConnectedState, this.mDefaultState);
        addState(this.mConnectedState, this.mDefaultState);
        addState(this.mCaptivePortalState, this.mConnectedState);
        addState(this.mEvaluatedState, this.mConnectedState);
        addState(this.mInvalidState, this.mEvaluatedState);
        addState(this.mValidState, this.mEvaluatedState);
        addState(this.mValidNonSwitchableState, this.mValidState);
        addState(this.mValidSwitchableState, this.mValidState);
        addState(this.mLevel1State, this.mValidSwitchableState);
        addState(this.mLevel2State, this.mValidSwitchableState);
        addState(this.mValidNoCheckState, this.mValidState);
        setInitialState(this.mNotConnectedState);
        setLogRecSize(DBG ? 1000 : 500);
        updateCountryIsoCode();
        this.mParam = new ParameterManager();
        this.mWifiInfo = sDummyWifiInfo;
        HandlerThread networkStatsThread = new HandlerThread("NetworkStatsThread");
        networkStatsThread.start();
        this.mNetworkStatsAnalyzer = new NetworkStatsAnalyzer(networkStatsThread.getLooper());
        this.mPreviousUid = -1;
        this.mPreviousPackageName = "default";
        this.mSnsBigDataManager = new SnsBigDataManager(this.mContext);
        this.mAlarmMgr = (AlarmManager) this.mContext.getSystemService("alarm");
        this.mSnsBigDataSCNT = (SnsBigDataSCNT) this.mSnsBigDataManager.getBigDataFeature("SSMA");
        PendingIntent alarmIntentForScnt = PendingIntent.getBroadcast(this.mContext, 0, new Intent(BIG_DATA_SNS_SCNT_INTENT), 0);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(11, 11);
        calendar.set(12, 55);
        this.mAlarmMgr.setInexactRepeating(1, calendar.getTimeInMillis(), 86400000, alarmIntentForScnt);
        if (!"FINISH".equalsIgnoreCase(SystemProperties.get("persist.sys.setupwizard", "NOTSET"))) {
            if (Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", -1) == -1) {
                if ("DEFAULT_ON".equals(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGSNSSTATUS))) {
                    Settings.Global.putInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 1);
                } else {
                    Settings.Global.putInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 0);
                }
            }
            if (!"".equals(CONFIG_SECURE_SVC_INTEGRATION) || SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEMWIPS) || SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_DISABLEDEFAULTMWIPS)) {
                Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_mwips", 0);
            } else {
                Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_mwips", 1);
            }
        }
        HandlerThread tcpMonitorThread = new HandlerThread("WifiTransportLayerMonitor");
        tcpMonitorThread.start();
        this.mWifiTransportLayerMonitor = new WifiTransportLayerMonitor(tcpMonitorThread.getLooper(), this, clientModeImpl, context);
        HandlerThread tcpHandlerThread = new HandlerThread("WifiSwitchForIndividualAppsService");
        tcpHandlerThread.start();
        this.mWifiSwitchForIndividualAppsService = new WifiSwitchForIndividualAppsService(tcpHandlerThread.getLooper(), clientModeImpl, this.mWifiTransportLayerMonitor, this, context);
        try {
            this.mUsageStatsUid = -1;
            this.mUsageStatsPackageName = "default";
            this.mUsageStatsManager = IUsageStatsManager.Stub.asInterface(ServiceManager.getService("usagestats"));
            this.mUsageStatsManager.registerUsageStatsWatcher(this.mUsageStatsWatcher);
        } catch (Exception e3) {
            Log.w(TAG, "Exception occured while register UsageStatWatcher " + e3);
            e3.printStackTrace();
        }
        this.mNumOfToggled = Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_num_of_switch_to_mobile_data_toggle", 0);
        this.mLastVisibilityOfWifiIcon = -1;
        Settings.Global.putInt(this.mContentResolver, "check_private_ip_mode", 1);
        this.mCountryCodeFromScanResult = Settings.Global.getString(this.mContentResolver, "wifi_wcm_country_code_from_scan_result");
        Log.d(TAG, "Initial WIFI_WCM_COUNTRY_CODE_FROM_SCAN_RESULT: " + this.mCountryCodeFromScanResult);
    }

    public static WifiConnectivityMonitor makeWifiConnectivityMonitor(Context context, ClientModeImpl clientModeImpl, WifiInjector wifiInjector) {
        context.getContentResolver();
        sWifiOnly = "wifi-only".equalsIgnoreCase(SystemProperties.get("ro.carrier", SystemInfo.UNKNOWN_INFO).trim()) || "yes".equalsIgnoreCase(SystemProperties.get("ro.radio.noril", "no").trim());
        WifiConnectivityMonitor wcm = new WifiConnectivityMonitor(context, clientModeImpl, wifiInjector);
        wcm.start();
        checkVersion(context);
        return wcm;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setupNetworkReceiver() {
        this.mBroadcastReceiver = new BroadcastReceiver() {
            /* class com.android.server.wifi.WifiConnectivityMonitor.C04381 */

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals("android.net.wifi.RSSI_CHANGED")) {
                    WifiConnectivityMonitor.this.obtainMessage(WifiConnectivityMonitor.EVENT_RSSI_CHANGE, intent.getIntExtra("newRssi", -200), 0).sendToTarget();
                } else if (action.equals("android.net.wifi.STATE_CHANGE")) {
                    WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_NETWORK_STATE_CHANGE, intent);
                } else if (action.equals("android.intent.action.SCREEN_ON")) {
                    WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_SCREEN_ON);
                } else if (action.equals("android.intent.action.SCREEN_OFF")) {
                    WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_SCREEN_OFF);
                } else if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                    WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_WIFI_RADIO_STATE_CHANGE, intent.getIntExtra("wifi_state", 4));
                } else if (action.equals("ACTION_WCM_CONFIGURATION_CHANGED")) {
                    WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_WATCHDOG_SETTINGS_CHANGE);
                } else if (action.equals("android.intent.action.SIM_STATE_CHANGED")) {
                    WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_WATCHDOG_SETTINGS_CHANGE);
                } else if (action.equals("com.android.intent.action.DATAUSAGE_REACH_TO_LIMIT")) {
                    WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_WATCHDOG_SETTINGS_CHANGE);
                } else if (SemCscFeature.getInstance().getBoolean(CscFeatureTagRIL.TAG_CSCFEATURE_RIL_SHOWDATASELECTPOPUPONBOOTUP) && action.equals("android.intent.action.ACTION_DATA_SELECTION_POPUP_PRESSED")) {
                    WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_WATCHDOG_SETTINGS_CHANGE);
                } else if (action.equals("android.net.conn.CONNECTIVITY_CHANGE")) {
                    WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_CONNECTIVITY_ACTION_CHANGE);
                } else if (action.equals("android.intent.action.SERVICE_STATE") && WifiConnectivityMonitor.this.mTelephonyManager != null) {
                    String networkCountyIso = WifiConnectivityMonitor.this.mTelephonyManager.getNetworkCountryIso();
                    Log.i(WifiConnectivityMonitor.TAG, "ACTION_SERVICE_STATE_CHANGED: " + networkCountyIso);
                    if (networkCountyIso != null && !networkCountyIso.isEmpty() && !networkCountyIso.equals(WifiConnectivityMonitor.this.mCountryIso)) {
                        if (WifiConnectivityMonitor.DBG) {
                            Log.d(WifiConnectivityMonitor.TAG, "Network country change is detected.");
                        }
                        WifiConnectivityMonitor.this.updateCountryIsoCode();
                    }
                } else if (action.equals("android.intent.action.USER_BACKGROUND")) {
                    Log.d(WifiConnectivityMonitor.TAG, "OWNER is background");
                    WifiConnectivityMonitor.this.mUserOwner = false;
                    WifiConnectivityMonitor.this.updatePoorNetworkParameters();
                } else if (action.equals("android.intent.action.USER_FOREGROUND")) {
                    Log.d(WifiConnectivityMonitor.TAG, "OWNER is foreground");
                    WifiConnectivityMonitor.this.mUserOwner = true;
                    WifiConnectivityMonitor.this.updatePoorNetworkParameters();
                } else if (action.equals("android.intent.action.BOOT_COMPLETED")) {
                    Log.d(WifiConnectivityMonitor.TAG, "ACTION_BOOT_COMPLETED");
                } else if (action.equals("android.net.conn.INET_CONDITION_ACTION")) {
                    WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_INET_CONDITION_CHANGE, intent);
                } else if (action.equals(WifiConnectivityMonitor.IMS_REGISTRATION)) {
                    boolean prevImsRegistered = WifiConnectivityMonitor.this.mImsRegistered;
                    WifiConnectivityMonitor.this.mImsRegistered = intent.getBooleanExtra("VOWIFI", false);
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "IMS_REGISTRATION - " + WifiConnectivityMonitor.this.mImsRegistered);
                    }
                    if (prevImsRegistered != WifiConnectivityMonitor.this.mImsRegistered) {
                        WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_WATCHDOG_SETTINGS_CHANGE);
                    }
                } else if (action.equals("com.samsung.android.server.wifi.WCM_SCAN_STARTED")) {
                    Log.d(WifiConnectivityMonitor.TAG, "com.samsung.android.server.wifi.WCM_SCAN_STARTED");
                    WifiConnectivityMonitor.this.scanStarted();
                } else if (action.equals("android.net.wifi.LINK_CONFIGURATION_CHANGED")) {
                    LinkProperties lp = (LinkProperties) intent.getParcelableExtra("linkProperties");
                    if (lp != null) {
                        WifiConnectivityMonitor.this.mLinkProperties = lp;
                    }
                } else if (action.equals("android.net.wifi.SCAN_RESULTS")) {
                    WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.EVENT_SCAN_TIMEOUT);
                    boolean booleanExtra = intent.getBooleanExtra("isPartialScan", false);
                    WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor.sendMessage(wifiConnectivityMonitor.obtainMessage(WifiConnectivityMonitor.EVENT_SCAN_COMPLETE, booleanExtra ? 1 : 0, 0));
                    WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.EVENT_SCAN_COMPLETE);
                } else if (action.equals(WifiConnectivityMonitor.BIG_DATA_SNS_SCNT_INTENT)) {
                    WifiConnectivityMonitor.this.sendBigDataFeatureForSCNT();
                } else if (action.equals("android.intent.action.AIRPLANE_MODE")) {
                    Log.d(WifiConnectivityMonitor.TAG, "AIRPLANE_MODE_CHANGED");
                    WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_WATCHDOG_SETTINGS_CHANGE);
                }
            }
        };
        this.mIntentFilter = new IntentFilter();
        this.mIntentFilter.addAction("android.net.wifi.STATE_CHANGE");
        this.mIntentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        this.mIntentFilter.addAction("android.net.wifi.RSSI_CHANGED");
        this.mIntentFilter.addAction("android.net.wifi.supplicant.STATE_CHANGE");
        this.mIntentFilter.addAction("ACTION_WCM_CONFIGURATION_CHANGED");
        this.mIntentFilter.addAction("android.intent.action.SCREEN_ON");
        this.mIntentFilter.addAction("android.intent.action.SCREEN_OFF");
        this.mIntentFilter.addAction("android.intent.action.SIM_STATE_CHANGED");
        this.mIntentFilter.addAction("android.intent.action.SERVICE_STATE");
        this.mIntentFilter.addAction("android.intent.action.ANY_DATA_STATE");
        this.mIntentFilter.addAction("com.android.intent.action.DATAUSAGE_REACH_TO_LIMIT");
        this.mIntentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        this.mIntentFilter.addAction("android.intent.action.USER_FOREGROUND");
        this.mIntentFilter.addAction("android.intent.action.USER_BACKGROUND");
        if (SemCscFeature.getInstance().getBoolean(CscFeatureTagRIL.TAG_CSCFEATURE_RIL_SHOWDATASELECTPOPUPONBOOTUP)) {
            this.mIntentFilter.addAction("android.intent.action.ACTION_DATA_SELECTION_POPUP_PRESSED");
        }
        this.mIntentFilter.addAction("android.net.wifi.SCAN_RESULTS");
        this.mIntentFilter.addAction("android.intent.action.BOOT_COMPLETED");
        this.mIntentFilter.addAction(BIG_DATA_SNS_SCNT_INTENT);
        this.mIntentFilter.addAction("android.net.conn.INET_CONDITION_ACTION");
        this.mIntentFilter.addAction(IMS_REGISTRATION);
        this.mIntentFilter.addAction("com.samsung.android.server.wifi.WCM_SCAN_STARTED");
        this.mIntentFilter.addAction("android.net.wifi.LINK_CONFIGURATION_CHANGED");
        this.mIntentFilter.addAction("android.intent.action.AIRPLANE_MODE");
        this.mContext.registerReceiver(this.mBroadcastReceiver, this.mIntentFilter);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void registerForWatchdogToggle() {
        this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("wifi_watchdog_on"), false, new ContentObserver(getHandler()) {
            /* class com.android.server.wifi.WifiConnectivityMonitor.C04392 */

            public void onChange(boolean selfChange) {
                WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_WATCHDOG_TOGGLED);
            }
        });
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void registerForSettingsChanges() {
        ContentObserver contentObserver = new ContentObserver(getHandler()) {
            /* class com.android.server.wifi.WifiConnectivityMonitor.C04403 */

            public void onChange(boolean selfChange) {
                WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                int i = 0;
                wifiConnectivityMonitor.mNumOfToggled = Settings.Global.getInt(wifiConnectivityMonitor.mContext.getContentResolver(), "wifi_num_of_switch_to_mobile_data_toggle", 0);
                WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_WATCHDOG_SETTINGS_CHANGE);
                if (WifiConnectivityMonitor.this.mIWCChannel != null) {
                    AsyncChannel asyncChannel = WifiConnectivityMonitor.this.mIWCChannel;
                    int i2 = WifiConnectivityMonitor.this.mUIEnabled ? 1 : 0;
                    if (WifiConnectivityMonitor.this.mAggressiveModeEnabled) {
                        i = 1;
                    }
                    asyncChannel.sendMessage((int) IWCMonitor.SNS_SETTINGS_CHANGED, i2, i);
                }
            }
        };
        this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("wifi_watchdog_poor_network_test_enabled"), false, contentObserver);
        this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("wifi_watchdog_poor_network_aggressive_mode_on"), false, contentObserver);
        this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("mobile_data"), false, contentObserver);
        this.mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor("ultra_powersaving_mode"), false, contentObserver);
        this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("data_roaming"), false, contentObserver);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void registerForMptcpChange() {
        this.mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor("mptcp_value_internal"), false, new ContentObserver(getHandler()) {
            /* class com.android.server.wifi.WifiConnectivityMonitor.C04414 */

            public void onChange(boolean selfChange) {
                try {
                    WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                    boolean z = true;
                    if (Settings.System.getInt(WifiConnectivityMonitor.this.mContext.getContentResolver(), "mptcp_value_internal") != 1) {
                        z = false;
                    }
                    wifiConnectivityMonitor.mMptcpEnabled = z;
                    Log.d(WifiConnectivityMonitor.TAG, "MPTCP mode changed, enabled ? = " + WifiConnectivityMonitor.this.mMptcpEnabled);
                } catch (Settings.SettingNotFoundException e) {
                    Log.e(WifiConnectivityMonitor.TAG, "Exception in getting 'MPTCP mode' setting " + e);
                }
            }
        });
    }

    class DefaultState extends State {
        DefaultState() {
        }

        public void enter() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName());
            }
            WifiConnectivityMonitor.this.setupNetworkReceiver();
            WifiConnectivityMonitor.this.registerForSettingsChanges();
            WifiConnectivityMonitor.this.registerForWatchdogToggle();
            WifiConnectivityMonitor.this.registerForMptcpChange();
            WifiConnectivityMonitor.this.updateSettings();
        }

        public void exit() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName() + " exit\n");
            }
        }

        /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
        public boolean processMessage(Message msg) {
            Map<String, BssidStatistics> mBssidCacheMap;
            WifiConnectivityMonitor.this.logStateAndMessage(msg, this);
            boolean z = false;
            WifiConnectivityMonitor.this.setLogOnlyTransitions(false);
            int i = msg.what;
            switch (i) {
                case WifiConnectivityMonitor.EVENT_NETWORK_STATE_CHANGE /*{ENCODED_INT: 135170}*/:
                    Log.d(WifiConnectivityMonitor.TAG, "EVENT_NETWORK_STATE_CHANGE");
                    NetworkInfo networkInfo = (NetworkInfo) ((Intent) msg.obj).getParcelableExtra("networkInfo");
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "Network state change " + networkInfo.getDetailedState());
                    }
                    if (networkInfo.getDetailedState().equals(NetworkInfo.DetailedState.CONNECTED)) {
                        WifiInfo info = WifiConnectivityMonitor.this.syncGetCurrentWifiInfo();
                        if (WifiConnectivityMonitor.this.mCurrentBssid != null) {
                            if (info == null || WifiConnectivityMonitor.this.mCurrentBssid.mBssid == null || info.getBSSID() == null) {
                                WifiConnectivityMonitor.this.updateCurrentBssid(null, -1);
                            } else if (!info.getBSSID().equals(WifiConnectivityMonitor.this.mCurrentBssid.mBssid)) {
                                WifiConnectivityMonitor.this.updateCurrentBssid(info.getBSSID(), info.getNetworkId());
                            }
                        } else if (info != null) {
                            WifiConnectivityMonitor.this.updateCurrentBssid(info.getBSSID(), info.getNetworkId());
                        }
                    } else if (networkInfo.getDetailedState().equals(NetworkInfo.DetailedState.DISCONNECTED)) {
                        WifiConnectivityMonitor.this.setCaptivePortalMode(1);
                        WifiConnectivityMonitor.this.stopPacketTracker();
                    } else if (networkInfo.getDetailedState().equals(NetworkInfo.DetailedState.OBTAINING_IPADDR)) {
                        WifiConnectivityMonitor.this.startPacketTracker();
                    }
                    return true;
                case WifiConnectivityMonitor.EVENT_RSSI_CHANGE /*{ENCODED_INT: 135171}*/:
                    WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor.mCurrentSignalLevel = wifiConnectivityMonitor.calculateSignalLevel(msg.arg1);
                    WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor2.mWifiInfo = wifiConnectivityMonitor2.syncGetCurrentWifiInfo();
                    return true;
                case WifiConnectivityMonitor.EVENT_SUPPLICANT_STATE_CHANGE /*{ENCODED_INT: 135172}*/:
                    return true;
                case WifiConnectivityMonitor.EVENT_WIFI_RADIO_STATE_CHANGE /*{ENCODED_INT: 135173}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "Wi-Fi Radio state change : " + msg.arg1);
                    }
                    if (msg.arg1 == 1 && WifiConnectivityMonitor.this.isConnectedState()) {
                        WifiConnectivityMonitor wifiConnectivityMonitor3 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor3.transitionTo(wifiConnectivityMonitor3.mNotConnectedState);
                    }
                    return true;
                case WifiConnectivityMonitor.EVENT_WATCHDOG_SETTINGS_CHANGE /*{ENCODED_INT: 135174}*/:
                    WifiConnectivityMonitor.this.updateSettings();
                    return true;
                case WifiConnectivityMonitor.EVENT_BSSID_CHANGE /*{ENCODED_INT: 135175}*/:
                    break;
                case WifiConnectivityMonitor.EVENT_SCREEN_ON /*{ENCODED_INT: 135176}*/:
                    WifiConnectivityMonitor.this.mIsScreenOn = true;
                    return true;
                case WifiConnectivityMonitor.EVENT_SCREEN_OFF /*{ENCODED_INT: 135177}*/:
                    WifiConnectivityMonitor.this.mIsScreenOn = false;
                    WifiConnectivityMonitor.this.screenOffEleInitialize();
                    return true;
                default:
                    switch (i) {
                        case WifiConnectivityMonitor.EVENT_CONNECTIVITY_ACTION_CHANGE /*{ENCODED_INT: 135179}*/:
                            NetworkInfo networkInfo_connectivity = WifiConnectivityMonitor.this.mConnectivityManager.getActiveNetworkInfo();
                            if (networkInfo_connectivity != null) {
                                if (networkInfo_connectivity.getType() == 0) {
                                    Log.d(WifiConnectivityMonitor.TAG, "TYPE_MOBILE received. isConnected=" + networkInfo_connectivity.isConnected());
                                    if (networkInfo_connectivity.isConnected()) {
                                        boolean previousState = WifiConnectivityMonitor.this.mIsMobileActiveNetwork;
                                        WifiConnectivityMonitor.this.mIsMobileActiveNetwork = true;
                                        if (previousState != WifiConnectivityMonitor.this.mIsMobileActiveNetwork) {
                                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                Toast.makeText(WifiConnectivityMonitor.this.mContext, "EVENT_MOBILE_CONNECTED", 0).show();
                                            }
                                            WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_MOBILE_CONNECTED);
                                            break;
                                        }
                                    } else {
                                        WifiConnectivityMonitor.this.mIsMobileActiveNetwork = false;
                                        break;
                                    }
                                } else {
                                    WifiConnectivityMonitor.this.mIsMobileActiveNetwork = false;
                                    break;
                                }
                            }
                            break;
                        case WifiConnectivityMonitor.EVENT_INET_CONDITION_CHANGE /*{ENCODED_INT: 135180}*/:
                            NetworkInfo networkInfo_inet = (NetworkInfo) ((Intent) msg.obj).getParcelableExtra("networkInfo");
                            if (networkInfo_inet != null) {
                                int networkType = networkInfo_inet.getType();
                                NetworkCapabilities nc = WifiConnectivityMonitor.this.getCm().getNetworkCapabilities(WifiConnectivityMonitor.this.getCm().getNetworkForType(networkType));
                                int isValid = 1;
                                if (nc != null && !nc.hasCapability(16)) {
                                    isValid = 0;
                                }
                                WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_INET_CONDITION_ACTION, networkType, isValid);
                                break;
                            }
                            break;
                        default:
                            switch (i) {
                                case WifiConnectivityMonitor.CMD_RSSI_FETCH /*{ENCODED_INT: 135188}*/:
                                case WifiConnectivityMonitor.CMD_TRAFFIC_POLL /*{ENCODED_INT: 135193}*/:
                                    WifiConnectivityMonitor.this.setLogOnlyTransitions(true);
                                    return true;
                                case WifiConnectivityMonitor.EVENT_ROAM_TIMEOUT /*{ENCODED_INT: 135249}*/:
                                    if (WifiConnectivityMonitor.DBG) {
                                        Log.d(WifiConnectivityMonitor.TAG, "EVENT_ROAM_TIMEOUT");
                                    }
                                    WifiConnectivityMonitor.this.mIsInRoamSession = false;
                                    break;
                                case WifiConnectivityMonitor.CMD_IWC_CURRENT_QAI /*{ENCODED_INT: 135368}*/:
                                    Bundle bund_qai = msg.getData();
                                    WifiConnectivityMonitor.this.mIwcCurrentQai = bund_qai.getInt("qai");
                                    String bssid = bund_qai.getString("bssid");
                                    if (WifiConnectivityMonitor.DBG) {
                                        Log.d(WifiConnectivityMonitor.TAG, "CMD_IWC_CURRENT_QAI: " + WifiConnectivityMonitor.this.mIwcCurrentQai + ", " + bssid);
                                    }
                                    WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_WATCHDOG_SETTINGS_CHANGE);
                                    break;
                                case WifiConnectivityMonitor.CMD_IWC_REQUEST_INTERNET_CHECK /*{ENCODED_INT: 135371}*/:
                                    WifiConnectivityMonitor.this.mIwcRequestQcId = msg.getData().getInt("cid");
                                    if (WifiConnectivityMonitor.DBG) {
                                        Log.d(WifiConnectivityMonitor.TAG, "CMD_IWC_REQUEST_INTERNET_CHECK is not received in ConnectedState.");
                                    }
                                    WifiConnectivityMonitor.this.mIWCChannel.sendMessage((int) WifiConnectivityMonitor.CMD_IWC_QC_RESULT, -1, WifiConnectivityMonitor.this.mIwcRequestQcId);
                                    break;
                                case WifiConnectivityMonitor.CMD_IWC_QC_RESULT_TIMEOUT /*{ENCODED_INT: 135375}*/:
                                    if (WifiConnectivityMonitor.DBG) {
                                        Log.d(WifiConnectivityMonitor.TAG, "QC did not start, or is not concluded within 15 seconds.");
                                    }
                                    if (WifiConnectivityMonitor.this.mIwcRequestQcId != -1) {
                                        WifiConnectivityMonitor.this.mIWCChannel.sendMessage((int) WifiConnectivityMonitor.CMD_IWC_QC_RESULT, -1, WifiConnectivityMonitor.this.mIwcRequestQcId);
                                        WifiConnectivityMonitor.this.mIwcRequestQcId = -1;
                                        break;
                                    }
                                    break;
                                case WifiConnectivityMonitor.INVALIDATED_DETECTED /*{ENCODED_INT: 135473}*/:
                                    if (WifiConnectivityMonitor.DBG) {
                                        Log.d(WifiConnectivityMonitor.TAG, "INVALIDATED_DETECTED");
                                    }
                                    if (msg.arg1 != 1) {
                                        if (msg.arg1 == 2) {
                                            WifiConnectivityMonitor.this.mInvalidStateTesting = false;
                                            break;
                                        }
                                    } else {
                                        WifiConnectivityMonitor.this.mInvalidStateTesting = true;
                                        break;
                                    }
                                    break;
                                case WifiConnectivityMonitor.CMD_NETWORK_PROPERTIES_UPDATED /*{ENCODED_INT: 135478}*/:
                                    LinkProperties lp = (LinkProperties) msg.obj;
                                    if (lp != null) {
                                        WifiConnectivityMonitor.this.mLinkProperties = lp;
                                        break;
                                    }
                                    break;
                                case WifiConnectivityMonitor.QC_RESULT_NOT_RECEIVED /*{ENCODED_INT: 135480}*/:
                                    if (WifiConnectivityMonitor.this.mWCMQCResult != null) {
                                        WifiConnectivityMonitor.this.mWCMQCResult.recycle();
                                        WifiConnectivityMonitor.this.mWCMQCResult = null;
                                    }
                                    if (WifiConnectivityMonitor.this.isValidState()) {
                                        WifiConnectivityMonitor.this.setLoggingForTCPStat(WifiConnectivityMonitor.TCP_STAT_LOGGING_SECOND);
                                    }
                                    return true;
                                default:
                                    switch (i) {
                                        case WifiConnectivityMonitor.EVENT_SCAN_STARTED /*{ENCODED_INT: 135229}*/:
                                            if (WifiConnectivityMonitor.DBG) {
                                                Log.d(WifiConnectivityMonitor.TAG, "EVENT_SCAN_STARTED");
                                            }
                                            return true;
                                        case WifiConnectivityMonitor.EVENT_SCAN_COMPLETE /*{ENCODED_INT: 135230}*/:
                                            if (!WifiConnectivityMonitor.this.isConnectedState()) {
                                                WifiConnectivityMonitor.this.mIsScanning = false;
                                            }
                                            if (WifiConnectivityMonitor.DBG) {
                                                Log.d(WifiConnectivityMonitor.TAG, "EVENT_SCAN_COMPLETE");
                                            }
                                            WifiConnectivityMonitor wifiConnectivityMonitor4 = WifiConnectivityMonitor.this;
                                            if (msg.arg1 == 1) {
                                                z = true;
                                            }
                                            wifiConnectivityMonitor4.scanCompleted(z);
                                            if (!WifiConnectivityMonitor.this.mIsRoamingNetwork) {
                                                WifiConnectivityMonitor wifiConnectivityMonitor5 = WifiConnectivityMonitor.this;
                                                wifiConnectivityMonitor5.mIsRoamingNetwork = wifiConnectivityMonitor5.isRoamingNetwork();
                                            }
                                            return true;
                                        case WifiConnectivityMonitor.EVENT_SCAN_TIMEOUT /*{ENCODED_INT: 135231}*/:
                                        case WifiConnectivityMonitor.EVENT_MOBILE_CONNECTED /*{ENCODED_INT: 135232}*/:
                                            break;
                                        default:
                                            switch (i) {
                                                case WifiConnectivityMonitor.EVENT_NETWORK_REMOVED /*{ENCODED_INT: 135244}*/:
                                                    int removedNetId = msg.arg1;
                                                    if (!(removedNetId == -1 || (mBssidCacheMap = WifiConnectivityMonitor.this.mBssidCache.snapshot()) == null)) {
                                                        for (String s : mBssidCacheMap.keySet()) {
                                                            BssidStatistics bs = mBssidCacheMap.get(s);
                                                            if (bs.netId == removedNetId) {
                                                                Log.d(WifiConnectivityMonitor.TAG, "BssidStatistics removed - " + bs.mBssid.substring(9));
                                                                WifiConnectivityMonitor.this.mBssidCache.remove(bs.mBssid);
                                                            }
                                                        }
                                                    }
                                                    return true;
                                                case WifiConnectivityMonitor.EVENT_INET_CONDITION_ACTION /*{ENCODED_INT: 135245}*/:
                                                case WifiConnectivityMonitor.EVENT_LINK_DETECTION_DISABLED /*{ENCODED_INT: 135247}*/:
                                                    break;
                                                case WifiConnectivityMonitor.EVENT_PARALLEL_CP_CHECK_RESULT /*{ENCODED_INT: 135246}*/:
                                                    if (WifiConnectivityMonitor.DBG) {
                                                        Log.d(WifiConnectivityMonitor.TAG, "EVENT_PARALLEL_CP_CHECK_RESULT");
                                                    }
                                                    return true;
                                                default:
                                                    Log.e(WifiConnectivityMonitor.TAG, "Unhandled message " + msg.what);
                                                    break;
                                            }
                                    }
                            }
                    }
                    return true;
            }
            return true;
        }
    }

    /* access modifiers changed from: package-private */
    public class NotConnectedState extends State {
        NotConnectedState() {
        }

        public void enter() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName());
            }
            boolean unused = WifiConnectivityMonitor.mInitialResultSentToSystemUi = false;
            WifiConnectivityMonitor.this.setCaptivePortalMode(1);
            boolean unused2 = WifiConnectivityMonitor.mIsNoCheck = false;
            WifiConnectivityMonitor.this.mValidationResultCount = 0;
            WifiConnectivityMonitor.this.mValidationCheckCount = 0;
            WifiConnectivityMonitor.this.mValidationCheckMode = false;
            WifiConnectivityMonitor.this.showNetworkSwitchedNotification(false);
            WifiConnectivityMonitor.this.mIsRoamingNetwork = false;
            Log.i(WifiConnectivityMonitor.TAG, "SCORE_QUALITY_CHECK_STATE_NONE");
            WifiConnectivityMonitor.this.mScoreQualityCheckMode = 0;
            if (!WifiConnectivityMonitor.sWifiOnly && WifiConnectivityMonitor.this.mWifiEleStateTracker != null) {
                WifiConnectivityMonitor.this.mWifiEleStateTracker.enableEleMobileRssiPolling(false, false);
                WifiConnectivityMonitor.this.mWifiEleStateTracker.unregisterElePedometer();
                WifiConnectivityMonitor.this.mWifiEleStateTracker.unregisterEleGeomagneticListener();
                WifiConnectivityMonitor.this.mWifiEleStateTracker.clearEleValidBlockFlag();
                WifiConnectivityMonitor.this.mWifiNeedRecoveryFromEle = false;
            }
            WifiConnectivityMonitor.this.mApOui = null;
            WifiConnectivityMonitor.this.mValidStartTime = 0;
            WifiConnectivityMonitor.this.mValidatedTime = 0;
            WifiConnectivityMonitor.this.mInvalidStartTime = 0;
            WifiConnectivityMonitor.this.mInvalidatedTime = 0;
            WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.STOP_BLINKING_WIFI_ICON);
            WifiConnectivityMonitor.this.setRoamEventToNM(0);
            WifiConnectivityMonitor.this.updateCurrentBssid(null, -1);
        }

        public void exit() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName() + " exit\n");
            }
        }

        public boolean processMessage(Message msg) {
            WifiConnectivityMonitor.this.logStateAndMessage(msg, this);
            WifiConnectivityMonitor.this.setLogOnlyTransitions(false);
            int i = msg.what;
            if (i == WifiConnectivityMonitor.EVENT_WIFI_TOGGLED) {
                boolean on = msg.arg1 == 1;
                if (on) {
                    if (!WifiConnectivityMonitor.this.mIsWifiEnabled) {
                        WifiConnectivityMonitor.this.showNetworkSwitchedNotification(false);
                    }
                } else if (WifiConnectivityMonitor.this.mIsWifiEnabled) {
                    WifiConnectivityMonitor.this.showNetworkSwitchedNotification(false);
                }
                WifiConnectivityMonitor.this.mIsWifiEnabled = on;
                return true;
            } else if (i != WifiConnectivityMonitor.HANDLE_ON_AVAILABLE) {
                return false;
            } else {
                boolean unused = WifiConnectivityMonitor.mInitialResultSentToSystemUi = false;
                boolean unused2 = WifiConnectivityMonitor.mUserSelectionConfirmed = false;
                WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                wifiConnectivityMonitor.transitionTo(wifiConnectivityMonitor.mConnectedState);
                WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.STOP_BLINKING_WIFI_ICON);
                WifiConnectivityMonitor.this.sendMessageDelayed(WifiConnectivityMonitor.STOP_BLINKING_WIFI_ICON, 7000);
                WifiConnectivityMonitor.this.setLoggingForTCPStat(WifiConnectivityMonitor.TCP_STAT_LOGGING_FIRST);
                WifiConnectivityMonitor.this.mNetworkCallbackController.handleConnected();
                WifiConnectivityMonitor.this.mInvalidationFailHistory.qcStepTemp = 3;
                WifiConnectivityMonitor.this.mInvalidationFailHistory.qcTriggerTemp = 1;
                return true;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public class ConnectedState extends State {
        ConnectedState() {
        }

        public void enter() {
            int message;
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName());
            }
            WifiConnectivityMonitor.this.setForceWifiIcon(1);
            if (WifiConnectivityMonitor.this.mCurrentBssid != null) {
                WifiConnectivityMonitor.this.mCurrentBssid.newLinkDetected();
            }
            if (WifiConnectivityMonitor.this.mCurrentLoss == null) {
                WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                wifiConnectivityMonitor.mCurrentLoss = new VolumeWeightedEMA(0.5d);
            }
            WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
            wifiConnectivityMonitor2.mWifiInfo = wifiConnectivityMonitor2.syncGetCurrentWifiInfo();
            WifiConnectivityMonitor wifiConnectivityMonitor3 = WifiConnectivityMonitor.this;
            wifiConnectivityMonitor3.mInvalidationRssi = wifiConnectivityMonitor3.mWifiInfo.getRssi();
            WifiConnectivityMonitor.this.updateSettings();
            WifiConnectivityMonitor.this.determineMode();
            if (WifiConnectivityMonitor.this.mLinkProperties != null) {
                WifiConnectivityMonitor wifiConnectivityMonitor4 = WifiConnectivityMonitor.this;
                wifiConnectivityMonitor4.mIsUsingProxy = (wifiConnectivityMonitor4.mLinkProperties.getHttpProxy() == null || WifiConnectivityMonitor.this.mLinkProperties.getHttpProxy().getHost() == null || WifiConnectivityMonitor.this.mLinkProperties.getHttpProxy().getPort() == -1) ? false : true;
                if (WifiConnectivityMonitor.this.mIsUsingProxy) {
                    WifiConnectivityMonitor wifiConnectivityMonitor5 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor5.mProxyAddress = wifiConnectivityMonitor5.mLinkProperties.getHttpProxy().getHost();
                    WifiConnectivityMonitor wifiConnectivityMonitor6 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor6.mProxyPort = wifiConnectivityMonitor6.mLinkProperties.getHttpProxy().getPort();
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "HTTP Proxy is in use. Proxy: " + WifiConnectivityMonitor.this.mProxyAddress + ":" + WifiConnectivityMonitor.this.mProxyPort);
                    }
                }
            } else {
                WifiConnectivityMonitor.this.mIsUsingProxy = false;
            }
            if (WifiConnectivityMonitor.DBG) {
                WifiConnectivityMonitor.this.mParam.setEvaluationParameters();
            }
            WifiConnectivityMonitor wifiConnectivityMonitor7 = WifiConnectivityMonitor.this;
            WifiConfiguration config = wifiConnectivityMonitor7.getWifiConfiguration(wifiConnectivityMonitor7.mWifiInfo.getNetworkId());
            if (config != null) {
                boolean unused = WifiConnectivityMonitor.mCurrentApDefault = config.semIsVendorSpecificSsid;
            }
            boolean isManualSelection = WifiConnectivityMonitor.this.getClientModeChannel().getManualSelection();
            Log.d(WifiConnectivityMonitor.TAG, "network manually connect : " + isManualSelection);
            if (!isManualSelection) {
                boolean unused2 = WifiConnectivityMonitor.mUserSelectionConfirmed = true;
            }
            if (WifiConnectivityMonitor.this.mCurrentBssid == null) {
                WifiConnectivityMonitor wifiConnectivityMonitor8 = WifiConnectivityMonitor.this;
                wifiConnectivityMonitor8.updateCurrentBssid(wifiConnectivityMonitor8.mWifiInfo.getBSSID(), WifiConnectivityMonitor.this.mWifiInfo.getNetworkId());
            } else if (WifiConnectivityMonitor.this.mCurrentBssid.mBssid == null || WifiConnectivityMonitor.this.mWifiInfo.getBSSID() == null) {
                WifiConnectivityMonitor.this.updateCurrentBssid(null, -1);
            } else if (!WifiConnectivityMonitor.this.mWifiInfo.getBSSID().equals(WifiConnectivityMonitor.this.mCurrentBssid.mBssid)) {
                WifiConnectivityMonitor wifiConnectivityMonitor9 = WifiConnectivityMonitor.this;
                wifiConnectivityMonitor9.updateCurrentBssid(wifiConnectivityMonitor9.mWifiInfo.getBSSID(), WifiConnectivityMonitor.this.mWifiInfo.getNetworkId());
            }
            if (!WifiConnectivityMonitor.sWifiOnly) {
                try {
                    WifiConnectivityMonitor.this.createEleObjects();
                } catch (Exception e) {
                    Log.e(WifiConnectivityMonitor.TAG, "createEleObjects exception happend! " + e.toString());
                }
            }
            if ((WifiConnectivityMonitor.this.mCurrentMode == 2 || WifiConnectivityMonitor.this.mCurrentMode == 3) && WifiConnectivityMonitor.this.mWifiEleStateTracker != null) {
                WifiConnectivityMonitor.this.mWifiEleStateTracker.registerElePedometer();
                WifiConnectivityMonitor.this.mWifiEleStateTracker.clearEleValidBlockFlag();
            }
            WifiConnectivityMonitor.this.mIsInDhcpSession = false;
            WifiConnectivityMonitor.this.mIsScanning = false;
            WifiConnectivityMonitor.this.mIsInRoamSession = false;
            WifiConnectivityMonitor.this.mCheckRoamedNetwork = false;
            int unused3 = WifiConnectivityMonitor.mCPCheckTriggeredByRoam = 0;
            WifiConnectivityMonitor.this.setValidationBlock(false);
            WifiConnectivityMonitor.this.mValidationResultCount = 0;
            WifiConnectivityMonitor.this.mValidationCheckCount = 0;
            WifiConnectivityMonitor.this.mValidationCheckMode = false;
            WifiConnectivityMonitor.this.mIs204CheckInterval = false;
            WifiConnectivityMonitor.this.mAnalyticsDisconnectReason = 0;
            WifiConnectivityMonitor.this.showNetworkSwitchedNotification(false);
            String macAddress = WifiConnectivityMonitor.this.mWifiInfo.getBSSID();
            if (macAddress != null && macAddress.length() > 8) {
                WifiConnectivityMonitor.this.mApOui = macAddress.substring(0, 8);
            }
            if (WifiConnectivityMonitor.this.mWifiSwitchForIndividualAppsService != null) {
                WifiConnectivityMonitor wifiConnectivityMonitor10 = WifiConnectivityMonitor.this;
                wifiConnectivityMonitor10.mReportedPoorNetworkDetectionEnabled = wifiConnectivityMonitor10.mPoorNetworkDetectionEnabled;
                if (WifiConnectivityMonitor.this.mReportedPoorNetworkDetectionEnabled) {
                    message = WifiSwitchForIndividualAppsService.SWITCH_TO_MOBILE_DATA_ENABLED;
                } else {
                    message = WifiSwitchForIndividualAppsService.SWITCH_TO_MOBILE_DATA_DISABLED;
                }
                WifiConnectivityMonitor.this.mWifiSwitchForIndividualAppsService.sendEmptyMessage(message);
                WifiConnectivityMonitor wifiConnectivityMonitor11 = WifiConnectivityMonitor.this;
                wifiConnectivityMonitor11.mReportedQai = wifiConnectivityMonitor11.mIwcCurrentQai;
                WifiSwitchForIndividualAppsService wifiSwitchForIndividualAppsService = WifiConnectivityMonitor.this.mWifiSwitchForIndividualAppsService;
                WifiConnectivityMonitor wifiConnectivityMonitor12 = WifiConnectivityMonitor.this;
                wifiSwitchForIndividualAppsService.sendMessage(wifiConnectivityMonitor12.obtainMessage(WifiSwitchForIndividualAppsService.SWITCH_TO_MOBILE_DATA_QAI, wifiConnectivityMonitor12.mReportedQai));
            }
            if (WifiConnectivityMonitor.this.mWifiManager != null && WifiConnectivityMonitor.this.isNetworkNeedChnPublicDns()) {
                Message msg = new Message();
                msg.what = 330;
                Bundle args = new Bundle();
                args.putString("publicDnsServer", WifiConnectivityMonitor.CHN_PUBLIC_DNS_IP);
                msg.obj = args;
                WifiConnectivityMonitor.this.mWifiManager.callSECApi(msg);
            }
            boolean unused4 = WifiConnectivityMonitor.mIsECNTReportedConnection = false;
            boolean unused5 = WifiConnectivityMonitor.mIsPassedLevel1State = false;
            WifiConnectivityMonitor.this.mStayingPoorRssi = 0;
            WifiConnectivityMonitor.this.mLossSampleCount = 0;
            WifiConnectivityMonitor.this.mOvercomingCount = 0;
            WifiConnectivityMonitor.this.mLinkLossOccurred = 0;
            WifiConnectivityMonitor.this.mLossHasGone = 0;
            WifiConnectivityMonitor.this.mPreviousLoss = WifiConnectivityMonitor.POOR_LINK_MIN_VOLUME;
        }

        public void exit() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName() + " exit\n");
            }
            WifiConnectivityMonitor.this.changeWifiIcon(1);
            WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.EVENT_ROAM_TIMEOUT);
            WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.EVENT_SCAN_TIMEOUT);
            WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_STOP);
        }

        public boolean processMessage(Message msg) {
            String str;
            WifiConnectivityMonitor.this.logStateAndMessage(msg, this);
            int i = 0;
            boolean z = false;
            WifiConnectivityMonitor.this.setLogOnlyTransitions(false);
            switch (msg.what) {
                case WifiConnectivityMonitor.EVENT_WATCHDOG_SETTINGS_CHANGE /*{ENCODED_INT: 135174}*/:
                    WifiConnectivityMonitor.this.updateSettings();
                    WifiConnectivityMonitor.this.determineMode();
                    return true;
                case WifiConnectivityMonitor.EVENT_SCREEN_ON /*{ENCODED_INT: 135176}*/:
                    WifiConnectivityMonitor.this.mIsScreenOn = true;
                    return true;
                case WifiConnectivityMonitor.EVENT_SCREEN_OFF /*{ENCODED_INT: 135177}*/:
                    WifiConnectivityMonitor.this.mIsScreenOn = false;
                    WifiConnectivityMonitor.this.screenOffEleInitialize();
                    return true;
                case WifiConnectivityMonitor.EVENT_DHCP_SESSION_STARTED /*{ENCODED_INT: 135236}*/:
                    WifiConnectivityMonitor.this.mIsInDhcpSession = true;
                    return true;
                case WifiConnectivityMonitor.EVENT_DHCP_SESSION_COMPLETE /*{ENCODED_INT: 135237}*/:
                    if (WifiConnectivityMonitor.this.mIsInDhcpSession) {
                        WifiConnectivityMonitor.this.mIsInDhcpSession = false;
                        if (WifiConnectivityMonitor.this.isInvalidState() && !WifiConnectivityMonitor.this.mIsInRoamSession) {
                            WifiConnectivityMonitor.this.setValidationBlock(false);
                            WifiConnectivityMonitor.this.requestInternetCheck(53);
                        }
                    }
                    return true;
                case WifiConnectivityMonitor.EVENT_WIFI_TOGGLED /*{ENCODED_INT: 135243}*/:
                    boolean on = msg.arg1 == 1;
                    if (!on) {
                        WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor.transitionTo(wifiConnectivityMonitor.mNotConnectedState);
                        if (WifiConnectivityMonitor.this.mIsWifiEnabled) {
                            WifiConnectivityMonitor.this.showNetworkSwitchedNotification(false);
                        }
                    }
                    WifiConnectivityMonitor.this.mIsWifiEnabled = on;
                    return true;
                case WifiConnectivityMonitor.CMD_IWC_REQUEST_NETWORK_SWITCH_TO_MOBILE /*{ENCODED_INT: 135369}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "CMD_IWC_REQUEST_NETWORK_SWITCH_TO_MOBILE");
                    }
                    WifiConnectivityMonitor.this.getCurrentState();
                    if (WifiConnectivityMonitor.this.isIwcModeEnabled()) {
                        WifiConnectivityMonitor.this.setBigDataValidationChanged();
                        if (WifiConnectivityMonitor.this.isValidState()) {
                            WifiConnectivityMonitor.this.mSnsBigDataSCNT.mIwcWM++;
                            WifiConnectivityMonitor.this.mInvalidStartTime = SystemClock.elapsedRealtime();
                        }
                        WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor2.transitionTo(wifiConnectivityMonitor2.mInvalidState);
                    }
                    return true;
                case WifiConnectivityMonitor.CMD_IWC_REQUEST_NETWORK_SWITCH_TO_WIFI /*{ENCODED_INT: 135370}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "CMD_IWC_REQUEST_NETWORK_SWITCH_TO_WIFI");
                    }
                    WifiConnectivityMonitor.this.getCurrentState();
                    if (WifiConnectivityMonitor.this.isIwcModeEnabled()) {
                        WifiConnectivityMonitor.this.setBigDataValidationChanged();
                        if (!WifiConnectivityMonitor.this.isValidState()) {
                            WifiConnectivityMonitor.this.mSnsBigDataSCNT.mIwcMW++;
                            WifiConnectivityMonitor.this.mValidStartTime = SystemClock.elapsedRealtime();
                        }
                        WifiConnectivityMonitor wifiConnectivityMonitor3 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor3.transitionTo(wifiConnectivityMonitor3.mValidState);
                    }
                    return true;
                case WifiConnectivityMonitor.CMD_IWC_REQUEST_INTERNET_CHECK /*{ENCODED_INT: 135371}*/:
                    WifiConnectivityMonitor.this.mIwcRequestQcId = msg.getData().getInt("cid");
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "CMD_IWC_REQUEST_INTERNET_CHECK, reqId: " + WifiConnectivityMonitor.this.mIwcRequestQcId);
                    }
                    if (WifiConnectivityMonitor.this.mCurrentMode != 1) {
                        WifiConnectivityMonitor.this.isInvalidState();
                    }
                    WifiConnectivityMonitor.this.mInvalidationFailHistory.qcTriggerTemp = 2;
                    WifiConnectivityMonitor.this.sendMessageDelayed(WifiConnectivityMonitor.CMD_IWC_QC_RESULT_TIMEOUT, 15000);
                    return true;
                case WifiConnectivityMonitor.CMD_REACHABILITY_LOST /*{ENCODED_INT: 135400}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "CMD_REACHABILITY_LOST");
                    }
                    if (WifiConnectivityMonitor.this.mCurrentBssid != null) {
                        WifiConnectivityMonitor.this.mCurrentBssid.updateBssidQosMapOnReachabilityLost();
                    }
                    return true;
                case WifiConnectivityMonitor.STOP_BLINKING_WIFI_ICON /*{ENCODED_INT: 135468}*/:
                    if (!WifiConnectivityMonitor.mInitialResultSentToSystemUi) {
                        Log.d(WifiConnectivityMonitor.TAG, "Send initial result to System UI - Invalid or CaptivePortal");
                        boolean unused = WifiConnectivityMonitor.mInitialResultSentToSystemUi = true;
                        WifiConnectivityMonitor.this.sendBroadcastWCMTestResult(false);
                    }
                    WifiConnectivityMonitor.this.setBigDataValidationChanged();
                    if (WifiConnectivityMonitor.this.isSkipInternetCheck()) {
                        boolean unused2 = WifiConnectivityMonitor.mUserSelectionConfirmed = true;
                    }
                    WifiConnectivityMonitor wifiConnectivityMonitor4 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor4.mWifiInfo = wifiConnectivityMonitor4.syncGetCurrentWifiInfo();
                    WifiConnectivityMonitor wifiConnectivityMonitor5 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor5.mInvalidationRssi = wifiConnectivityMonitor5.mWifiInfo.getRssi();
                    WifiConnectivityMonitor.this.mInvalidStartTime = SystemClock.elapsedRealtime();
                    WifiConnectivityMonitor.this.mInvalidationFailHistory.error = 17;
                    WifiConnectivityMonitor.this.mInvalidationFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                    WifiConnectivityMonitor wifiConnectivityMonitor6 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor6.setQcFailHistory(wifiConnectivityMonitor6.mInvalidationFailHistory);
                    WifiConnectivityMonitor.this.setLoggingForTCPStat(WifiConnectivityMonitor.TCP_STAT_LOGGING_SECOND);
                    WifiConnectivityMonitor wifiConnectivityMonitor7 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor7.transitionTo(wifiConnectivityMonitor7.mInvalidState);
                    return true;
                case WifiConnectivityMonitor.HANDLE_ON_LOST /*{ENCODED_INT: 135469}*/:
                    boolean unused3 = WifiConnectivityMonitor.mInitialResultSentToSystemUi = false;
                    WifiConnectivityMonitor wifiConnectivityMonitor8 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor8.transitionTo(wifiConnectivityMonitor8.mNotConnectedState);
                    return true;
                case WifiConnectivityMonitor.CAPTIVE_PORTAL_DETECTED /*{ENCODED_INT: 135471}*/:
                    WifiConnectivityMonitor.this.setValidationBlock(false);
                    if (!WifiConnectivityMonitor.mInitialResultSentToSystemUi) {
                        boolean unused4 = WifiConnectivityMonitor.mInitialResultSentToSystemUi = true;
                        Log.d(WifiConnectivityMonitor.TAG, "Send initial result to System UI - CaptivePortal");
                        WifiConnectivityMonitor.this.sendBroadcastWCMTestResult(false);
                    }
                    boolean unused5 = WifiConnectivityMonitor.mUserSelectionConfirmed = true;
                    MobileWipsFrameworkService mwfs = MobileWipsFrameworkService.getInstance();
                    if (mwfs != null) {
                        Message msgWips = Message.obtain();
                        msgWips.what = 14;
                        msgWips.arg1 = 1;
                        if (WifiConnectivityMonitor.this.isCaptivePortalExceptionOnly(null) || WifiConnectivityMonitor.this.isIgnorableNetwork(null)) {
                            i = 1;
                        }
                        msgWips.arg2 = i;
                        mwfs.sendMessage(msgWips);
                    }
                    if (WifiConnectivityMonitor.this.getCurrentState() != WifiConnectivityMonitor.this.mCaptivePortalState) {
                        WifiConnectivityMonitor wifiConnectivityMonitor9 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor9.transitionTo(wifiConnectivityMonitor9.mCaptivePortalState);
                    }
                    return true;
                case WifiConnectivityMonitor.VALIDATED_DETECTED /*{ENCODED_INT: 135472}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "VALIDATED_DETECTED");
                    }
                    if (msg.arg1 == 2) {
                        WifiConnectivityMonitor.this.mInvalidStateTesting = false;
                    }
                    if (!WifiConnectivityMonitor.mInitialResultSentToSystemUi) {
                        WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.STOP_BLINKING_WIFI_ICON);
                        Log.d(WifiConnectivityMonitor.TAG, "Send initial result to System UI - Valid");
                        boolean unused6 = WifiConnectivityMonitor.mInitialResultSentToSystemUi = true;
                        WifiConnectivityMonitor.this.sendBroadcastWCMTestResult(true);
                    }
                    boolean unused7 = WifiConnectivityMonitor.mUserSelectionConfirmed = true;
                    if (WifiConnectivityMonitor.this.isValidState()) {
                        WifiConnectivityMonitor.this.setBigDataQualityCheck(true);
                    } else if (WifiConnectivityMonitor.mIsNoCheck) {
                        WifiConnectivityMonitor.this.setCaptivePortalMode(1);
                        WifiConnectivityMonitor wifiConnectivityMonitor10 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor10.transitionTo(wifiConnectivityMonitor10.mValidNoCheckState);
                    } else {
                        WifiConnectivityMonitor.this.setBigDataValidationChanged();
                        if (WifiConnectivityMonitor.this.getLinkDetectMode() == 1) {
                            if (WifiConnectivityMonitor.this.mCurrentMode == 1) {
                                WifiConnectivityMonitor.this.mSnsBigDataSCNT.mInvGqNon++;
                            } else if (WifiConnectivityMonitor.this.mCurrentMode == 2) {
                                WifiConnectivityMonitor.this.mSnsBigDataSCNT.mInvGqNormal++;
                            } else if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                                WifiConnectivityMonitor.this.mSnsBigDataSCNT.mInvGqAgg++;
                            }
                        } else if (WifiConnectivityMonitor.this.mCurrentMode == 1) {
                            WifiConnectivityMonitor.this.mSnsBigDataSCNT.mInvPqNon++;
                        } else if (WifiConnectivityMonitor.this.mCurrentMode == 2) {
                            WifiConnectivityMonitor.this.mSnsBigDataSCNT.mInvPqNormal++;
                        } else if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                            WifiConnectivityMonitor.this.mSnsBigDataSCNT.mInvPqAgg++;
                        }
                        WifiConnectivityMonitor wifiConnectivityMonitor11 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor11.transitionTo(wifiConnectivityMonitor11.mValidState);
                        WifiConnectivityMonitor.this.setBigDataQCandNS(true);
                        WifiConnectivityMonitor.this.mValidStartTime = SystemClock.elapsedRealtime();
                    }
                    return true;
                case WifiConnectivityMonitor.INVALIDATED_DETECTED /*{ENCODED_INT: 135473}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "INVALIDATED_DETECTED");
                    }
                    if (msg.arg1 == 1) {
                        WifiConnectivityMonitor.this.mInvalidStateTesting = true;
                    }
                    WifiConnectivityMonitor wifiConnectivityMonitor12 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor12.mWifiInfo = wifiConnectivityMonitor12.syncGetCurrentWifiInfo();
                    WifiConnectivityMonitor wifiConnectivityMonitor13 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor13.mInvalidationRssi = wifiConnectivityMonitor13.mWifiInfo.getRssi();
                    WifiConnectivityMonitor.this.mInvalidationFailHistory.error = 17;
                    WifiConnectivityMonitor.this.mInvalidationFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                    WifiConnectivityMonitor wifiConnectivityMonitor14 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor14.setQcFailHistory(wifiConnectivityMonitor14.mInvalidationFailHistory);
                    WifiConnectivityMonitor.this.bSetQcResult = false;
                    if (!WifiConnectivityMonitor.this.isInvalidState()) {
                        WifiConnectivityMonitor.this.setBigDataValidationChanged();
                        if (WifiConnectivityMonitor.this.isSkipInternetCheck()) {
                            boolean unused8 = WifiConnectivityMonitor.mUserSelectionConfirmed = true;
                        }
                        WifiConnectivityMonitor.this.setBigDataQCandNS(false);
                        WifiConnectivityMonitor.this.mInvalidStartTime = SystemClock.elapsedRealtime();
                        WifiConnectivityMonitor.this.setLoggingForTCPStat(WifiConnectivityMonitor.TCP_STAT_LOGGING_SECOND);
                        WifiConnectivityMonitor wifiConnectivityMonitor15 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor15.transitionTo(wifiConnectivityMonitor15.mInvalidState);
                    } else {
                        WifiConnectivityMonitor.this.setBigDataQualityCheck(false);
                    }
                    return true;
                case WifiConnectivityMonitor.CMD_CHANGE_WIFI_ICON_VISIBILITY /*{ENCODED_INT: 135476}*/:
                    WifiConnectivityMonitor.this.changeWifiIcon(0);
                    return true;
                case WifiConnectivityMonitor.CMD_ROAM_START_COMPLETE /*{ENCODED_INT: 135477}*/:
                    String mRoamSessionState = (String) msg.obj;
                    if (mRoamSessionState != null) {
                        if ("start".equals(mRoamSessionState)) {
                            WifiConnectivityMonitor.this.mIsInRoamSession = true;
                            WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.EVENT_ROAM_TIMEOUT);
                            WifiConnectivityMonitor.this.sendMessageDelayed(WifiConnectivityMonitor.EVENT_ROAM_TIMEOUT, 30000);
                        } else if ("complete".equals(mRoamSessionState) && WifiConnectivityMonitor.this.mIsInRoamSession) {
                            WifiConnectivityMonitor.this.mIsInRoamSession = false;
                            WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.EVENT_ROAM_TIMEOUT);
                            if (WifiConnectivityMonitor.this.getCurrentState() == WifiConnectivityMonitor.this.mInvalidState || WifiConnectivityMonitor.this.getCurrentState() == WifiConnectivityMonitor.this.mLevel2State || WifiConnectivityMonitor.this.mNetworkCallbackController.isCaptivePortal()) {
                                if (WifiConnectivityMonitor.this.getCurrentState() == WifiConnectivityMonitor.this.mLevel2State) {
                                    WifiConnectivityMonitor.this.mCheckRoamedNetwork = true;
                                }
                                WifiConnectivityMonitor.this.setValidationBlock(false);
                                if (WifiConnectivityMonitor.this.mNetworkCallbackController.isCaptivePortal()) {
                                    WifiConnectivityMonitor.this.setRoamEventToNM(1);
                                }
                                WifiConnectivityMonitor.this.requestInternetCheck(52);
                            }
                        }
                    }
                    return true;
                case WifiConnectivityMonitor.CMD_NETWORK_PROPERTIES_UPDATED /*{ENCODED_INT: 135478}*/:
                    LinkProperties lp = (LinkProperties) msg.obj;
                    if (lp != null) {
                        WifiConnectivityMonitor.this.mLinkProperties = lp;
                    }
                    return true;
                case WifiConnectivityMonitor.CMD_UPDATE_CURRENT_BSSID_ON_THROUGHPUT_UPDATE /*{ENCODED_INT: 135488}*/:
                    WifiConnectivityMonitor.this.setLogOnlyTransitions(true);
                    if (WifiConnectivityMonitor.this.mCurrentBssid != null) {
                        Bundle bund = msg.getData();
                        WifiConnectivityMonitor.this.mCurrentBssid.updateBssidQosMapOnTputUpdate(Long.valueOf(bund.getLong("timeDelta", 0)).longValue(), Long.valueOf(bund.getLong("diffTxBytes", 0)).longValue(), Long.valueOf(bund.getLong("diffRxBytes", 0)).longValue());
                    }
                    return true;
                case WifiConnectivityMonitor.CMD_UPDATE_CURRENT_BSSID_ON_DNS_RESULT /*{ENCODED_INT: 135489}*/:
                    WifiConnectivityMonitor.this.setLogOnlyTransitions(true);
                    if (WifiConnectivityMonitor.this.mCurrentBssid != null) {
                        WifiConnectivityMonitor.this.mCurrentBssid.updateBssidQosMapOnDnsResult(msg.arg1, msg.arg2);
                    }
                    return true;
                case WifiConnectivityMonitor.CMD_UPDATE_CURRENT_BSSID_ON_DNS_RESULT_TYPE /*{ENCODED_INT: 135490}*/:
                    WifiConnectivityMonitor.this.setLogOnlyTransitions(true);
                    if (WifiConnectivityMonitor.this.mCurrentBssid != null) {
                        WifiConnectivityMonitor.this.mCurrentBssid.updateBssidLatestDnsResultType(msg.arg1);
                    }
                    WifiConnectivityMonitor wifiConnectivityMonitor16 = WifiConnectivityMonitor.this;
                    StringBuilder sb = new StringBuilder();
                    sb.append(msg.arg1);
                    if (msg.arg1 == 0) {
                        str = " / " + msg.arg2;
                    } else {
                        str = "";
                    }
                    sb.append(str);
                    wifiConnectivityMonitor16.setDnsResultHistory(sb.toString());
                    return true;
                case WifiConnectivityMonitor.CMD_UPDATE_CURRENT_BSSID_ON_QC_RESULT /*{ENCODED_INT: 135491}*/:
                    if (WifiConnectivityMonitor.this.mCurrentBssid != null) {
                        BssidStatistics bssidStatistics = WifiConnectivityMonitor.this.mCurrentBssid;
                        if (msg.arg1 == 1) {
                            z = true;
                        }
                        bssidStatistics.updateBssidQosMapOnQcResult(z);
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public class CaptivePortalState extends State {
        CaptivePortalState() {
        }

        public void enter() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName());
            }
            WifiConnectivityMonitor.this.changeWifiIcon(1);
            WifiConnectivityMonitor.this.mCurrentBssid.mIsCaptivePortal = true;
        }

        public void exit() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName() + " exit\n");
            }
        }

        public boolean processMessage(Message msg) {
            return false;
        }
    }

    class EvaluatedState extends State {
        private static final int TIME_204_CHECK_INTERVAL = 30000;
        private int mCheckFastDisconnection = 0;
        private int mGoodLinkLastRssi = 0;
        private long mLastRxGood;
        private int mPoorLinkLastRssi = -200;

        EvaluatedState() {
        }

        public void enter() {
            WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
            wifiConnectivityMonitor.mWifiInfo = wifiConnectivityMonitor.syncGetCurrentWifiInfo();
            if (WifiConnectivityMonitor.this.mCurrentBssid.mGoodLinkTargetRssi <= WifiConnectivityMonitor.this.mWifiInfo.getRssi() || WifiConnectivityMonitor.this.mCurrentBssid.mBssidAvoidTimeMax <= SystemClock.elapsedRealtime() || WifiConnectivityMonitor.this.mCurrentBssid.mLastPoorReason != 1) {
                WifiConnectivityMonitor.this.setLinkDetectMode(1);
            } else {
                Log.i(WifiConnectivityMonitor.TAG, "Connedted But link might be still poor, " + WifiConnectivityMonitor.this.mCurrentBssid.mGoodLinkTargetRssi);
                WifiConnectivityMonitor.this.setLinkDetectMode(0);
            }
            this.mGoodLinkLastRssi = WifiConnectivityMonitor.this.mWifiInfo.getRssi();
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName());
            }
        }

        public void exit() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName() + " exit\n");
            }
        }

        /* JADX INFO: Multiple debug info for r3v12 'mrssi'  int: [D('rssi' int), D('mrssi' int)] */
        /* JADX WARNING: Removed duplicated region for block: B:120:0x02e3  */
        /* JADX WARNING: Removed duplicated region for block: B:127:0x0326  */
        /* JADX WARNING: Removed duplicated region for block: B:134:0x037f  */
        /* JADX WARNING: Removed duplicated region for block: B:155:0x0444 A[RETURN] */
        /* JADX WARNING: Removed duplicated region for block: B:156:0x0445  */
        public boolean processMessage(Message msg) {
            TxPacketInfo txPacketInfo;
            long now;
            int txgood;
            long rxgood;
            long now2;
            int i;
            String str;
            boolean z;
            boolean z2;
            long now3;
            int txgood2;
            int txbad;
            long rxgood2;
            int rssi;
            long now4;
            int mrssi;
            WifiConnectivityMonitor.this.logStateAndMessage(msg, this);
            WifiConnectivityMonitor.this.setLogOnlyTransitions(false);
            switch (msg.what) {
                case WifiConnectivityMonitor.EVENT_SCREEN_ON /*{ENCODED_INT: 135176}*/:
                    if (WifiConnectivityMonitor.this.mCurrentMode != 0) {
                        WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor.sendMessage(wifiConnectivityMonitor.obtainMessage(WifiConnectivityMonitor.CMD_RSSI_FETCH, WifiConnectivityMonitor.access$13604(wifiConnectivityMonitor), 0));
                        if (WifiConnectivityMonitor.this.isValidState() && WifiConnectivityMonitor.this.getCurrentState() != WifiConnectivityMonitor.this.mLevel2State) {
                            if (WifiConnectivityMonitor.this.mNetworkStatsAnalyzer != null) {
                                WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_START);
                            }
                            WifiConnectivityMonitor.this.startEleCheck();
                        }
                        if (WifiConnectivityMonitor.this.mCurrentMode == 1 || WifiConnectivityMonitor.mLinkDetectMode == 1) {
                            WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor2.sendMessage(wifiConnectivityMonitor2.obtainMessage(WifiConnectivityMonitor.CMD_TRAFFIC_POLL, WifiConnectivityMonitor.access$16604(wifiConnectivityMonitor2), 0));
                        }
                    }
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "Fetch Detect Mode : " + WifiConnectivityMonitor.mLinkDetectMode);
                    }
                    if (WifiConnectivityMonitor.mLinkDetectMode == 1) {
                        WifiConnectivityMonitor.this.mLossSampleCount = 0;
                        WifiConnectivityMonitor.this.mOvercomingCount = 0;
                    } else {
                        WifiConnectivityMonitor.this.mGoodTargetCount = 0;
                        if (!WifiConnectivityMonitor.this.mIsScreenOn && WifiConnectivityMonitor.this.getCurrentState() == WifiConnectivityMonitor.this.mInvalidState) {
                            WifiConnectivityMonitor wifiConnectivityMonitor3 = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor3.mWifiInfo = wifiConnectivityMonitor3.syncGetCurrentWifiInfo();
                            if (WifiConnectivityMonitor.this.mWifiInfo.getRssi() >= -66) {
                                WifiConnectivityMonitor.this.setValidationBlock(false);
                                WifiConnectivityMonitor.this.requestInternetCheck(28);
                            }
                        }
                    }
                    WifiConnectivityMonitor.this.mIsScreenOn = true;
                    return true;
                case WifiConnectivityMonitor.EVENT_SCREEN_OFF /*{ENCODED_INT: 135177}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "Fetch Detect Mode : " + WifiConnectivityMonitor.this.getLinkDetectMode());
                    }
                    WifiConnectivityMonitor.this.mIsScreenOn = false;
                    WifiConnectivityMonitor.this.screenOffEleInitialize();
                    WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.CMD_RSSI_FETCH);
                    WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.CMD_TRAFFIC_POLL);
                    WifiConnectivityMonitor.access$13608(WifiConnectivityMonitor.this);
                    if (WifiConnectivityMonitor.this.mNetworkStatsAnalyzer == null) {
                        return true;
                    }
                    WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_STOP);
                    return true;
                case WifiConnectivityMonitor.CMD_RSSI_FETCH /*{ENCODED_INT: 135188}*/:
                    if (!WifiConnectivityMonitor.this.isConnectedState()) {
                        return true;
                    }
                    if (WifiConnectivityMonitor.this.isMobileHotspot() && WifiConnectivityMonitor.mInitialResultSentToSystemUi) {
                        return true;
                    }
                    WifiConnectivityMonitor.this.setLogOnlyTransitions(true);
                    TxPacketInfo txPacketInfo2 = null;
                    if (msg.arg1 == WifiConnectivityMonitor.this.mRssiFetchToken && (WifiConnectivityMonitor.this.mIsScreenOn || !WifiConnectivityMonitor.mInitialResultSentToSystemUi)) {
                        txPacketInfo2 = WifiConnectivityMonitor.this.fetchPacketCount();
                        WifiConnectivityMonitor wifiConnectivityMonitor4 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor4.sendMessageDelayed(wifiConnectivityMonitor4.obtainMessage(WifiConnectivityMonitor.CMD_RSSI_FETCH, WifiConnectivityMonitor.access$13604(wifiConnectivityMonitor4), 0), WifiConnectivityMonitor.this.mSamplingIntervalMS);
                    } else if (msg.arg1 != WifiConnectivityMonitor.this.mRssiFetchToken && (WifiConnectivityMonitor.this.mIsScreenOn || !WifiConnectivityMonitor.mInitialResultSentToSystemUi)) {
                        Log.e(WifiConnectivityMonitor.TAG, "msg.arg1 != mRssiFetchToken");
                        WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.CMD_RSSI_FETCH);
                        txPacketInfo2 = WifiConnectivityMonitor.this.fetchPacketCount();
                        WifiConnectivityMonitor wifiConnectivityMonitor5 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor5.sendMessageDelayed(wifiConnectivityMonitor5.obtainMessage(WifiConnectivityMonitor.CMD_RSSI_FETCH, WifiConnectivityMonitor.access$13604(wifiConnectivityMonitor5), 0), WifiConnectivityMonitor.this.mSamplingIntervalMS);
                    }
                    if (txPacketInfo2 == null) {
                        return true;
                    }
                    if (txPacketInfo2.result == 2) {
                        txPacketInfo = txPacketInfo2;
                    } else if (txPacketInfo2.result == 3) {
                        txPacketInfo = txPacketInfo2;
                    } else if (WifiConnectivityMonitor.this.mCurrentBssid == null) {
                        return true;
                    } else {
                        WifiConnectivityMonitor.this.mInvalidationFailHistory.qcStepTemp = 4;
                        WifiConnectivityMonitor.this.mLowQualityFailHistory.qcStep = 4;
                        WifiConnectivityMonitor wifiConnectivityMonitor6 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor6.mWifiInfo = wifiConnectivityMonitor6.syncGetCurrentWifiInfo();
                        int rssi2 = WifiConnectivityMonitor.this.mWifiInfo.getRssi();
                        int txbad2 = txPacketInfo2.mTxbad;
                        int txgood3 = txPacketInfo2.mTxgood;
                        long rxgood3 = TrafficStats.getRxPackets(WifiConnectivityMonitor.INTERFACENAMEOFWLAN);
                        if (WifiConnectivityMonitor.this.mIsScreenOn && WifiConnectivityMonitor.this.getCurrentState() == WifiConnectivityMonitor.this.mInvalidState && WifiConnectivityMonitor.this.mCurrentMode == 1 && this.mLastRxGood > 0 && !WifiConnectivityMonitor.this.mIs204CheckInterval && ((int) (rxgood3 - this.mLastRxGood)) >= 10) {
                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                Log.d(WifiConnectivityMonitor.TAG, "Rx packets are visible");
                            }
                            if (WifiConnectivityMonitor.DBG) {
                                Log.d(WifiConnectivityMonitor.TAG, "check Internet connectivity - reportNetworkConnectivity");
                            }
                            WifiConnectivityMonitor.this.requestInternetCheck(11);
                            WifiConnectivityMonitor.this.mIs204CheckInterval = true;
                            WifiConnectivityMonitor.this.sendMessageDelayed(WifiConnectivityMonitor.QC_RESET_204_CHECK_INTERVAL, 30000);
                        }
                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                            Log.d(WifiConnectivityMonitor.TAG, "Fetch Detect Mode : " + WifiConnectivityMonitor.this.getLinkDetectMode());
                        }
                        if (WifiConnectivityMonitor.mLinkDetectMode == 1) {
                            int mrssi2 = (this.mGoodLinkLastRssi + rssi2) / 2;
                            if (WifiConnectivityMonitor.this.mIwcCurrentQai == 3) {
                                str = "#.##";
                            } else if (WifiConnectivityMonitor.this.mCurrentMode == 2 || WifiConnectivityMonitor.this.mCurrentMode == 3) {
                                str = "#.##";
                                this.mCheckFastDisconnection = this.mCheckFastDisconnection == 1 ? 0 : 1;
                                if (WifiConnectivityMonitor.this.mCurrentMode != 0 && this.mCheckFastDisconnection == 0) {
                                    WifiConnectivityMonitor.this.mIWCChannel.sendMessage((int) WifiConnectivityMonitor.CMD_IWC_RSSI_FETCH_RESULT, txPacketInfo2.mTxbad, txPacketInfo2.mTxgood);
                                }
                                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append(this.mCheckFastDisconnection == 1 ? "[FD]" : "");
                                    sb.append("Fetch RSSI succeed, rssi=");
                                    sb.append(mrssi2);
                                    sb.append(" mrssi=");
                                    sb.append(mrssi2);
                                    sb.append(" txbad=");
                                    sb.append(txbad2);
                                    sb.append(" txgood=");
                                    sb.append(txgood3);
                                    Log.d(WifiConnectivityMonitor.TAG, sb.toString());
                                }
                                if (mrssi2 > WifiConnectivityMonitor.this.mParam.WEAK_SIGNAL_POOR_DETECTED_RSSI_MIN) {
                                    Log.d(WifiConnectivityMonitor.TAG, "RSSI is under than level 0 - rssi:" + mrssi2);
                                    long intervalTimeSec = (SystemClock.elapsedRealtime() - WifiConnectivityMonitor.this.mParam.mWeakSignalQCStartTime) / 1000;
                                    if (WifiConnectivityMonitor.this.mParam.mWeakSignalQCStartTime == 0 || intervalTimeSec > ((long) WifiConnectivityMonitor.this.mParam.WEAK_SIGNAL_FREQUENT_QC_CYCLE_SEC)) {
                                        WifiConnectivityMonitor.this.requestInternetCheck(21);
                                        WifiConnectivityMonitor.this.mParam.mWeakSignalQCStartTime = SystemClock.elapsedRealtime();
                                        return true;
                                    }
                                    z = true;
                                } else {
                                    z = true;
                                }
                                if (WifiConnectivityMonitor.mCurrentApDefault || WifiConnectivityMonitor.this.mCurrentMode == z) {
                                    z2 = z;
                                } else {
                                    WifiConnectivityMonitor.this.fastDisconnectUpdateRssi(mrssi2);
                                    if (mrssi2 >= WifiConnectivityMonitor.this.mParam.FD_RSSI_LOW_THRESHOLD) {
                                        z2 = true;
                                        WifiConnectivityMonitor.this.setBigDataQualityCheck(true);
                                    } else if (WifiConnectivityMonitor.this.fastDisconnectEvaluate()) {
                                        WifiConnectivityMonitor.this.bSetQcResult = false;
                                        WifiConnectivityMonitor.this.mCurrentBssid.poorLinkDetected(mrssi2, 1);
                                        this.mPoorLinkLastRssi = rssi2;
                                        if (WifiConnectivityMonitor.this.getCurrentState() == WifiConnectivityMonitor.this.mLevel1State) {
                                            if (!WifiConnectivityMonitor.this.skipPoorConnectionReport()) {
                                                WifiConnectivityMonitor.this.mLowQualityFailHistory.error = 14;
                                                WifiConnectivityMonitor.this.mLowQualityFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                                                WifiConnectivityMonitor.this.mInvalidationFailHistory.qcTriggerTemp = 18;
                                                WifiConnectivityMonitor.this.requestInternetCheck(18);
                                                WifiConnectivityMonitor.this.checkTransitionToLevel2State();
                                            } else {
                                                WifiConnectivityMonitor.this.mLowQualityFailHistory.error = 15;
                                                WifiConnectivityMonitor.this.mLowQualityFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                                                WifiConnectivityMonitor.this.handleNeedToRoamInLevel1State();
                                            }
                                        }
                                        WifiConnectivityMonitor wifiConnectivityMonitor7 = WifiConnectivityMonitor.this;
                                        wifiConnectivityMonitor7.setQcFailHistory(wifiConnectivityMonitor7.mLowQualityFailHistory);
                                        z2 = true;
                                    } else {
                                        z2 = true;
                                    }
                                }
                                if (this.mCheckFastDisconnection != z2) {
                                    return z2;
                                }
                                long now5 = SystemClock.elapsedRealtime();
                                if (now5 - WifiConnectivityMonitor.this.mCurrentBssid.mLastTimeSample < WifiConnectivityMonitor.LINK_SAMPLING_INTERVAL_MS * 2) {
                                    rxgood2 = rxgood3;
                                    if (WifiConnectivityMonitor.this.mCurrentLoss == null) {
                                        WifiConnectivityMonitor wifiConnectivityMonitor8 = WifiConnectivityMonitor.this;
                                        wifiConnectivityMonitor8.mCurrentLoss = new VolumeWeightedEMA(0.5d);
                                    }
                                    int dbad = txbad2 - WifiConnectivityMonitor.this.mLastTxBad;
                                    int dgood = txgood3 - WifiConnectivityMonitor.this.mLastTxGood;
                                    int dtotal = dbad + dgood;
                                    if (dtotal <= 0 || WifiConnectivityMonitor.this.mCurrentLoss == null) {
                                        now3 = now5;
                                        txbad = txbad2;
                                        txgood2 = txgood3;
                                        rssi = rssi2;
                                        if (WifiConnectivityMonitor.this.mCurrentLoss != null && WifiConnectivityMonitor.SMARTCM_DBG) {
                                            DecimalFormat df = new DecimalFormat(str);
                                            Log.d(WifiConnectivityMonitor.TAG, " rssi=" + mrssi2 + " [V]Incremental loss=0/0 cumulative loss=" + df.format(WifiConnectivityMonitor.this.mCurrentLoss.mValue * 100.0d));
                                        }
                                    } else {
                                        txbad = txbad2;
                                        txgood2 = txgood3;
                                        double loss = ((double) dbad) / ((double) dtotal);
                                        WifiConnectivityMonitor.this.mCurrentLoss.update(loss, dtotal);
                                        WifiConnectivityMonitor.this.mCurrentBssid.updateLoss(mrssi2, loss, dtotal);
                                        WifiConnectivityMonitor.this.mCurrentBssid.updateBssidQosMapOnPerUpdate(mrssi2, dbad, dgood);
                                        if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                                            if (WifiConnectivityMonitor.this.mStayingPoorRssi > 4) {
                                                WifiConnectivityMonitor.this.mLinkLossOccurred = 3;
                                                now3 = now5;
                                                mrssi = mrssi2;
                                            } else {
                                                if (WifiConnectivityMonitor.this.mInAggGoodStateNow) {
                                                    now3 = now5;
                                                    mrssi = mrssi2;
                                                } else if ((WifiConnectivityMonitor.this.mWifiInfo.is5GHz() && WifiConnectivityMonitor.this.mWifiInfo.getRssi() > -64) || WifiConnectivityMonitor.this.mWifiInfo.getRssi() > -55) {
                                                    now3 = now5;
                                                    mrssi = mrssi2;
                                                } else if (WifiConnectivityMonitor.this.mCurrentBssid.mLastGoodRxRssi >= 0 || WifiConnectivityMonitor.this.mCurrentBssid.mLastGoodRxRssi > mrssi2) {
                                                    now3 = now5;
                                                    if (dbad >= 30 || loss >= 0.5d) {
                                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                            Log.e(WifiConnectivityMonitor.TAG, "@L - (dbad >= 30) || (loss >= 0.5)");
                                                        }
                                                        WifiConnectivityMonitor.access$10508(WifiConnectivityMonitor.this);
                                                        if (loss >= 0.5d && dbad >= 5) {
                                                            WifiConnectivityMonitor.access$10508(WifiConnectivityMonitor.this);
                                                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                Log.e(WifiConnectivityMonitor.TAG, "@L - (loss >= 0.5) && (dbad >= 5)");
                                                            }
                                                        }
                                                    } else if (dbad > 4 && loss >= WifiConnectivityMonitor.EXP_COEFFICIENT_RECORD) {
                                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                            Log.e(WifiConnectivityMonitor.TAG, "@L - (dbad > 4)&&(loss >= 0.1)");
                                                        }
                                                        WifiConnectivityMonitor.access$10508(WifiConnectivityMonitor.this);
                                                    } else if (mrssi2 < -65 && WifiConnectivityMonitor.this.mWifiInfo.is24GHz() && (dbad > 4 || loss >= WifiConnectivityMonitor.EXP_COEFFICIENT_RECORD)) {
                                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                            Log.e(WifiConnectivityMonitor.TAG, "@L - rssi < -65) && (mWifiInfo.is24GHz()) && ((dbad > 4)||(loss >= 0.1))");
                                                        }
                                                        WifiConnectivityMonitor.access$10508(WifiConnectivityMonitor.this);
                                                    } else if (WifiConnectivityMonitor.this.mWifiInfo.getLinkSpeed() <= 6 && loss >= WifiConnectivityMonitor.EXP_COEFFICIENT_RECORD) {
                                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                            Log.e(WifiConnectivityMonitor.TAG, "@L - (mWifiInfo.getLinkSpeed() <= 6) && (loss >= 0.1)");
                                                        }
                                                        WifiConnectivityMonitor.access$10508(WifiConnectivityMonitor.this);
                                                    } else if (WifiConnectivityMonitor.this.mLossHasGone == 0 && loss > WifiConnectivityMonitor.this.mPreviousLoss && WifiConnectivityMonitor.this.mPreviousLoss >= WifiConnectivityMonitor.EXP_COEFFICIENT_RECORD) {
                                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                            Log.e(WifiConnectivityMonitor.TAG, "@L - loss increasing");
                                                        }
                                                        WifiConnectivityMonitor.access$10508(WifiConnectivityMonitor.this);
                                                    } else if (dbad > 0) {
                                                        if (WifiConnectivityMonitor.this.mLinkLossOccurred == 0) {
                                                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                Log.e(WifiConnectivityMonitor.TAG, "@L - loss begin occurring");
                                                            }
                                                            WifiConnectivityMonitor.access$10508(WifiConnectivityMonitor.this);
                                                        } else if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                            Log.i(WifiConnectivityMonitor.TAG, "@L - loss still can be seen, keep the value!");
                                                        }
                                                    }
                                                    if (dbad != 0) {
                                                        mrssi = mrssi2;
                                                        WifiConnectivityMonitor.this.mLossHasGone = 0;
                                                        WifiConnectivityMonitor.this.mPreviousLoss = loss;
                                                    } else if (WifiConnectivityMonitor.access$10604(WifiConnectivityMonitor.this) > 1) {
                                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                            Log.i(WifiConnectivityMonitor.TAG, "@L - loss has gone");
                                                        }
                                                        WifiConnectivityMonitor.this.mLinkLossOccurred = 0;
                                                        WifiConnectivityMonitor.this.mLossHasGone = 0;
                                                        mrssi = mrssi2;
                                                        WifiConnectivityMonitor.this.mPreviousLoss = WifiConnectivityMonitor.POOR_LINK_MIN_VOLUME;
                                                    } else {
                                                        mrssi = mrssi2;
                                                    }
                                                } else {
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                        Log.i(WifiConnectivityMonitor.TAG, "@L - beyond Last good rssi");
                                                    }
                                                    if (dbad > 0) {
                                                        if (WifiConnectivityMonitor.this.mLinkLossOccurred == 0) {
                                                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                Log.e(WifiConnectivityMonitor.TAG, "@L - loss begin occurring");
                                                            }
                                                            WifiConnectivityMonitor.this.mLinkLossOccurred = 1;
                                                        }
                                                        WifiConnectivityMonitor.this.mLossHasGone = 0;
                                                        WifiConnectivityMonitor.this.mPreviousLoss = loss;
                                                        now3 = now5;
                                                        mrssi = mrssi2;
                                                    } else {
                                                        WifiConnectivityMonitor.this.mLinkLossOccurred = 0;
                                                        WifiConnectivityMonitor.this.mLossHasGone = 0;
                                                        now3 = now5;
                                                        WifiConnectivityMonitor.this.mPreviousLoss = WifiConnectivityMonitor.POOR_LINK_MIN_VOLUME;
                                                        mrssi = mrssi2;
                                                    }
                                                }
                                                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                    Log.i(WifiConnectivityMonitor.TAG, "@L - In Agg good Rx state");
                                                }
                                                WifiConnectivityMonitor.this.mLinkLossOccurred = 0;
                                            }
                                            DecimalFormat df2 = new DecimalFormat(str);
                                            String dumpString = " rssi=" + mrssi + " [V]Incremental loss=" + dbad + "/" + dtotal + " cumulative loss=" + df2.format(WifiConnectivityMonitor.this.mCurrentLoss.mValue * 100.0d) + "% loss=" + df2.format(loss * 100.0d) + "% volume=" + df2.format(WifiConnectivityMonitor.this.mCurrentLoss.mVolume) + " mLinkLossOccurred=" + WifiConnectivityMonitor.this.mLinkLossOccurred + " linkspeed=" + WifiConnectivityMonitor.this.mWifiInfo.getLinkSpeed();
                                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                Log.d(WifiConnectivityMonitor.TAG, dumpString);
                                            }
                                            if (WifiConnectivityMonitor.this.mLinkLossOccurred >= 3) {
                                                String dumpString2 = "@L - dbad : " + dbad + " loss : " + loss + " mLinkLossOccurred : " + WifiConnectivityMonitor.this.mLinkLossOccurred + dumpString;
                                                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                    Log.e(WifiConnectivityMonitor.TAG, dumpString2);
                                                }
                                                if (WifiConnectivityMonitor.this.mStayingPoorRssi > 4) {
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                        Toast.makeText(WifiConnectivityMonitor.this.mContext, "@L - Staying under last Poor link, r=" + mrssi, 0).show();
                                                    }
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                        Log.d(WifiConnectivityMonitor.TAG, "@L - Staying under last Poor link, r=" + mrssi);
                                                    }
                                                    WifiConnectivityMonitor.this.mStayingPoorRssi = 0;
                                                    WifiConnectivityMonitor.this.mLowQualityFailHistory.qcTrigger = 22;
                                                } else {
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                        Toast.makeText(WifiConnectivityMonitor.this.mContext, "HIT @L!!!", 0).show();
                                                    }
                                                    WifiConnectivityMonitor.this.mLowQualityFailHistory.qcTrigger = 23;
                                                }
                                                WifiConnectivityMonitor.this.mLinkLossOccurred = 0;
                                                WifiConnectivityMonitor.this.mPreviousLoss = WifiConnectivityMonitor.POOR_LINK_MIN_VOLUME;
                                                WifiConnectivityMonitor.this.mLossHasGone = 0;
                                                WifiConnectivityMonitor.this.bSetQcResult = false;
                                                WifiConnectivityMonitor.this.mCurrentBssid.poorLinkDetected(mrssi, 1);
                                                rssi = rssi2;
                                                this.mPoorLinkLastRssi = rssi;
                                                if (WifiConnectivityMonitor.this.getCurrentState() == WifiConnectivityMonitor.this.mLevel1State) {
                                                    if (!WifiConnectivityMonitor.this.skipPoorConnectionReport()) {
                                                        WifiConnectivityMonitor.this.mLowQualityFailHistory.error = 12;
                                                        WifiConnectivityMonitor.this.mLowQualityFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                                                        WifiConnectivityMonitor.this.mInvalidationFailHistory.qcTriggerTemp = WifiConnectivityMonitor.this.mLowQualityFailHistory.qcTrigger;
                                                        WifiConnectivityMonitor wifiConnectivityMonitor9 = WifiConnectivityMonitor.this;
                                                        wifiConnectivityMonitor9.requestInternetCheck(wifiConnectivityMonitor9.mLowQualityFailHistory.qcTrigger);
                                                        WifiConnectivityMonitor.this.checkTransitionToLevel2State();
                                                    } else {
                                                        WifiConnectivityMonitor.this.mLowQualityFailHistory.error = 13;
                                                        WifiConnectivityMonitor.this.mLowQualityFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                                                        WifiConnectivityMonitor.this.handleNeedToRoamInLevel1State();
                                                    }
                                                }
                                                WifiConnectivityMonitor wifiConnectivityMonitor10 = WifiConnectivityMonitor.this;
                                                wifiConnectivityMonitor10.setQcFailHistory(wifiConnectivityMonitor10.mLowQualityFailHistory);
                                            } else {
                                                rssi = rssi2;
                                            }
                                        } else {
                                            now3 = now5;
                                            rssi = rssi2;
                                            if (WifiConnectivityMonitor.this.mCurrentLoss.mValue <= 0.5d || WifiConnectivityMonitor.this.mCurrentLoss.mVolume <= WifiConnectivityMonitor.POOR_LINK_MIN_VOLUME) {
                                                WifiConnectivityMonitor.this.mLossSampleCount = 0;
                                            } else {
                                                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                    Log.d(WifiConnectivityMonitor.TAG, "Poor link for link sample count, rssi=" + mrssi2);
                                                }
                                                WifiConnectivityMonitor.this.mLowQualityFailHistory.error = 11;
                                                if (WifiConnectivityMonitor.access$10304(WifiConnectivityMonitor.this) >= 3) {
                                                    WifiConnectivityMonitor.this.mLowQualityFailHistory.qcTrigger = 24;
                                                    if (mrssi2 < -80) {
                                                        WifiConnectivityMonitor.this.bSetQcResult = false;
                                                        WifiConnectivityMonitor.this.mCurrentBssid.poorLinkDetected(mrssi2, 1);
                                                        this.mPoorLinkLastRssi = rssi;
                                                        if (WifiConnectivityMonitor.this.mCurrentMode == 1) {
                                                            WifiConnectivityMonitor.this.setLinkDetectMode(0);
                                                            WifiConnectivityMonitor.this.mSnsBigDataSCNT.mGqPqNon++;
                                                        } else if (WifiConnectivityMonitor.this.mCurrentMode == 2 && WifiConnectivityMonitor.this.getCurrentState() == WifiConnectivityMonitor.this.mLevel1State) {
                                                            if (!WifiConnectivityMonitor.this.skipPoorConnectionReport()) {
                                                                WifiConnectivityMonitor.this.mLowQualityFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                                                                WifiConnectivityMonitor.this.mLowQualityFailHistory.qcTrigger = 25;
                                                                WifiConnectivityMonitor.this.mInvalidationFailHistory.qcTriggerTemp = WifiConnectivityMonitor.this.mLowQualityFailHistory.qcTrigger;
                                                                WifiConnectivityMonitor wifiConnectivityMonitor11 = WifiConnectivityMonitor.this;
                                                                wifiConnectivityMonitor11.requestInternetCheck(wifiConnectivityMonitor11.mLowQualityFailHistory.qcTrigger);
                                                                WifiConnectivityMonitor.this.checkTransitionToLevel2State();
                                                            } else {
                                                                WifiConnectivityMonitor.this.mLowQualityFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                                                                WifiConnectivityMonitor.this.mLowQualityFailHistory.qcTrigger = 26;
                                                                WifiConnectivityMonitor.this.handleNeedToRoamInLevel1State();
                                                            }
                                                        }
                                                        WifiConnectivityMonitor wifiConnectivityMonitor12 = WifiConnectivityMonitor.this;
                                                        wifiConnectivityMonitor12.setQcFailHistory(wifiConnectivityMonitor12.mLowQualityFailHistory);
                                                    } else if (mrssi2 < -75 || WifiConnectivityMonitor.this.mWifiInfo.getLinkSpeed() <= 6) {
                                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                            Log.e(WifiConnectivityMonitor.TAG, "from LinkMonitoring");
                                                        }
                                                        WifiConnectivityMonitor.this.mMaxAvoidCount = 0;
                                                        WifiConnectivityMonitor wifiConnectivityMonitor13 = WifiConnectivityMonitor.this;
                                                        wifiConnectivityMonitor13.requestInternetCheck(wifiConnectivityMonitor13.mLowQualityFailHistory.qcTrigger);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    now3 = now5;
                                    txbad = txbad2;
                                    txgood2 = txgood3;
                                    rxgood2 = rxgood3;
                                    rssi = rssi2;
                                }
                                if (WifiConnectivityMonitor.this.getCurrentState() != WifiConnectivityMonitor.this.mInvalidState) {
                                    now4 = now3;
                                } else if (WifiConnectivityMonitor.this.mCurrentMode != 2 && WifiConnectivityMonitor.this.mCurrentMode != 3) {
                                    now4 = now3;
                                } else if (rssi < WifiConnectivityMonitor.this.mInvalidationRssi + 5) {
                                    WifiConnectivityMonitor.this.mOvercomingCount = 0;
                                    if (WifiConnectivityMonitor.this.mConnectivityManager.getMultiNetwork()) {
                                        now4 = now3;
                                    } else if (now3 - WifiConnectivityMonitor.this.mValidationCheckEnabledTime > ((long) WifiConnectivityMonitor.VALIDATION_CHECK_TIMEOUT)) {
                                        WifiConnectivityMonitor.this.enableValidationCheck();
                                        now4 = now3;
                                        WifiConnectivityMonitor.this.mValidationCheckEnabledTime = now4;
                                    } else {
                                        now4 = now3;
                                    }
                                } else if (WifiConnectivityMonitor.access$10404(WifiConnectivityMonitor.this) >= 8) {
                                    Log.d(WifiConnectivityMonitor.TAG, "enable to get validation result.");
                                    WifiConnectivityMonitor.this.setValidationBlock(false);
                                    WifiConnectivityMonitor.this.requestInternetCheck(19);
                                    WifiConnectivityMonitor.this.mOvercomingCount = 0;
                                    now4 = now3;
                                } else {
                                    now4 = now3;
                                }
                                WifiConnectivityMonitor.this.mCurrentBssid.mLastTimeSample = now4;
                                WifiConnectivityMonitor.this.mLastTxBad = txbad;
                                WifiConnectivityMonitor.this.mLastTxGood = txgood2;
                                this.mLastRxGood = rxgood2;
                                this.mGoodLinkLastRssi = rssi;
                                WifiConnectivityMonitor wifiConnectivityMonitor14 = WifiConnectivityMonitor.this;
                                wifiConnectivityMonitor14.setRssiPatchHistory(wifiConnectivityMonitor14.mLastTxBad, WifiConnectivityMonitor.this.mLastTxGood, rxgood2);
                                return true;
                            } else {
                                str = "#.##";
                            }
                            this.mCheckFastDisconnection = 0;
                            WifiConnectivityMonitor.this.mIWCChannel.sendMessage((int) WifiConnectivityMonitor.CMD_IWC_RSSI_FETCH_RESULT, txPacketInfo2.mTxbad, txPacketInfo2.mTxgood);
                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                            }
                            if (mrssi2 > WifiConnectivityMonitor.this.mParam.WEAK_SIGNAL_POOR_DETECTED_RSSI_MIN) {
                            }
                            if (WifiConnectivityMonitor.mCurrentApDefault) {
                            }
                            z2 = z;
                            if (this.mCheckFastDisconnection != z2) {
                            }
                        } else {
                            if (WifiConnectivityMonitor.this.mCurrentMode != 0) {
                                WifiConnectivityMonitor.this.mIWCChannel.sendMessage((int) WifiConnectivityMonitor.CMD_IWC_RSSI_FETCH_RESULT, txPacketInfo2.mTxbad, txPacketInfo2.mTxgood);
                            }
                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                Log.d(WifiConnectivityMonitor.TAG, "Fetch RSSI succeed, rssi=" + rssi2);
                            }
                            int mrssi3 = (this.mPoorLinkLastRssi + rssi2) / 2;
                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                Log.d(WifiConnectivityMonitor.TAG, "[Invalid]Fetch RSSI succeed, rssi=" + rssi2 + " mrssi=" + mrssi3 + " txbad=" + txbad2 + " txgood=" + txgood3 + " rxgood=" + rxgood3);
                            }
                            long now6 = SystemClock.elapsedRealtime();
                            if (now6 - WifiConnectivityMonitor.this.mCurrentBssid.mLastTimeSample < 2000) {
                                int dbad2 = txbad2 - WifiConnectivityMonitor.this.mLastTxBad;
                                int dgood2 = txgood3 - WifiConnectivityMonitor.this.mLastTxGood;
                                int dtotal2 = dbad2 + dgood2;
                                if (dtotal2 <= 0 || WifiConnectivityMonitor.this.mCurrentLoss == null) {
                                    txgood = txgood3;
                                    rxgood = rxgood3;
                                    now = now6;
                                    if (WifiConnectivityMonitor.this.mCurrentLoss != null && WifiConnectivityMonitor.SMARTCM_DBG) {
                                        DecimalFormat df3 = new DecimalFormat("#.##");
                                        Log.d(WifiConnectivityMonitor.TAG, " rssi=" + rssi2 + " [I]Incremental loss=0/0 cumulative loss=" + df3.format(WifiConnectivityMonitor.this.mCurrentLoss.mValue * 100.0d));
                                    }
                                } else {
                                    txgood = txgood3;
                                    rxgood = rxgood3;
                                    double loss2 = ((double) dbad2) / ((double) dtotal2);
                                    WifiConnectivityMonitor.this.mCurrentLoss.update(loss2, dtotal2);
                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        DecimalFormat df4 = new DecimalFormat("#.##");
                                        StringBuilder sb2 = new StringBuilder();
                                        sb2.append(" rssi=");
                                        sb2.append(rssi2);
                                        sb2.append(" [I]Incremental loss=");
                                        sb2.append(dbad2);
                                        sb2.append("/");
                                        sb2.append(dtotal2);
                                        sb2.append(" Current loss=");
                                        now = now6;
                                        sb2.append(df4.format(WifiConnectivityMonitor.this.mCurrentLoss.mValue * 100.0d));
                                        sb2.append("% volume=");
                                        sb2.append(df4.format(WifiConnectivityMonitor.this.mCurrentLoss.mVolume));
                                        sb2.append(" linkspeed=");
                                        sb2.append(WifiConnectivityMonitor.this.mWifiInfo.getLinkSpeed());
                                        Log.d(WifiConnectivityMonitor.TAG, sb2.toString());
                                    } else {
                                        now = now6;
                                    }
                                    WifiConnectivityMonitor.this.mCurrentBssid.updateLoss(mrssi3, loss2, dtotal2);
                                    WifiConnectivityMonitor.this.mCurrentBssid.updateBssidQosMapOnPerUpdate(mrssi3, dbad2, dgood2);
                                }
                            } else {
                                txgood = txgood3;
                                rxgood = rxgood3;
                                now = now6;
                            }
                            long time = WifiConnectivityMonitor.this.mCurrentBssid.mBssidAvoidTimeMax - now;
                            Objects.requireNonNull(WifiConnectivityMonitor.this.mParam);
                            if (SystemClock.elapsedRealtime() - WifiConnectivityMonitor.this.mParam.mLastPoorDetectedTime > ((long) 30000) && WifiConnectivityMonitor.this.mCurrentBssid.mEnhancedTargetRssi != 0) {
                                BssidStatistics.access$13120(WifiConnectivityMonitor.this.mCurrentBssid, WifiConnectivityMonitor.this.mCurrentBssid.mEnhancedTargetRssi);
                                WifiConnectivityMonitor.this.mCurrentBssid.mEnhancedTargetRssi = 0;
                                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                    Log.d(WifiConnectivityMonitor.TAG, "restore target rssi");
                                }
                            }
                            if (WifiConnectivityMonitor.this.mIsScreenOn || !WifiConnectivityMonitor.mInitialResultSentToSystemUi || time >= 30000) {
                                if (WifiConnectivityMonitor.this.mCurrentMode == 1) {
                                    if (!WifiConnectivityMonitor.this.mIsScreenOn) {
                                        now2 = now;
                                    } else if (mrssi3 < WifiConnectivityMonitor.this.mCurrentBssid.mGoodLinkTargetRssi) {
                                        WifiConnectivityMonitor.this.mGoodTargetCount = 0;
                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                            Log.d(WifiConnectivityMonitor.TAG, "Link is still poor, " + WifiConnectivityMonitor.this.mCurrentBssid.mGoodLinkTargetRssi);
                                        }
                                        now2 = now;
                                    } else if (WifiConnectivityMonitor.access$16304(WifiConnectivityMonitor.this) >= WifiConnectivityMonitor.this.mCurrentBssid.mGoodLinkTargetCount) {
                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                            Log.d(WifiConnectivityMonitor.TAG, "Good link detected, rssi=" + mrssi3);
                                        }
                                        if (WifiConnectivityMonitor.DBG) {
                                            Log.d(WifiConnectivityMonitor.TAG, "check Internet connectivity - reportNetworkConnectivity");
                                        }
                                        WifiConnectivityMonitor.this.setLinkDetectMode(1);
                                        WifiConnectivityMonitor.this.mSnsBigDataSCNT.mPqGqNon++;
                                        this.mGoodLinkLastRssi = rssi2;
                                        if (WifiConnectivityMonitor.this.getCurrentState() == WifiConnectivityMonitor.this.mInvalidState) {
                                            WifiConnectivityMonitor.this.requestInternetCheck(12);
                                        }
                                        WifiConnectivityMonitor.this.mIs204CheckInterval = true;
                                        WifiConnectivityMonitor.this.sendMessageDelayed(WifiConnectivityMonitor.QC_RESET_204_CHECK_INTERVAL, 30000);
                                        now2 = now;
                                    } else {
                                        now2 = now;
                                    }
                                } else if (rssi2 < WifiConnectivityMonitor.this.mCurrentBssid.mGoodLinkTargetRssi) {
                                    WifiConnectivityMonitor.this.mGoodTargetCount = 0;
                                    if (WifiConnectivityMonitor.this.getCurrentState() != WifiConnectivityMonitor.this.mInvalidState || WifiConnectivityMonitor.this.mConnectivityManager.getMultiNetwork()) {
                                        now2 = now;
                                    } else if (now - WifiConnectivityMonitor.this.mValidationCheckEnabledTime > ((long) WifiConnectivityMonitor.VALIDATION_CHECK_TIMEOUT)) {
                                        WifiConnectivityMonitor.this.enableValidationCheck();
                                        now2 = now;
                                        WifiConnectivityMonitor.this.mValidationCheckEnabledTime = now2;
                                    } else {
                                        now2 = now;
                                    }
                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        Log.d(WifiConnectivityMonitor.TAG, "Link is still poor, time left=" + time + ", " + WifiConnectivityMonitor.this.mCurrentBssid.mGoodLinkTargetRssi);
                                    }
                                } else if (WifiConnectivityMonitor.access$16304(WifiConnectivityMonitor.this) >= WifiConnectivityMonitor.this.mCurrentBssid.mGoodLinkTargetCount) {
                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        Log.d(WifiConnectivityMonitor.TAG, "Good link detected, rssi=" + rssi2);
                                    }
                                    this.mGoodLinkLastRssi = rssi2;
                                    if (WifiConnectivityMonitor.this.getCurrentState() == WifiConnectivityMonitor.this.mLevel2State) {
                                        WifiConnectivityMonitor.this.checkTransitionToLevel1StateState();
                                        i = 0;
                                    } else {
                                        WifiConnectivityMonitor.this.setLinkDetectMode(1);
                                        if (WifiConnectivityMonitor.this.getCurrentState() == WifiConnectivityMonitor.this.mInvalidState) {
                                            Log.d(WifiConnectivityMonitor.TAG, "enable to get validation result.");
                                            i = 0;
                                            WifiConnectivityMonitor.this.setValidationBlock(false);
                                            WifiConnectivityMonitor.this.requestInternetCheck(12);
                                        } else {
                                            i = 0;
                                        }
                                    }
                                    WifiConnectivityMonitor.this.mGoodTargetCount = i;
                                    WifiConnectivityMonitor.this.mMaxAvoidCount = i;
                                    now2 = now;
                                } else {
                                    now2 = now;
                                }
                                WifiConnectivityMonitor.this.mCurrentBssid.mLastTimeSample = now2;
                                this.mLastRxGood = rxgood;
                                WifiConnectivityMonitor.this.mLastTxBad = txbad2;
                                WifiConnectivityMonitor.this.mLastTxGood = txgood;
                                this.mPoorLinkLastRssi = rssi2;
                                WifiConnectivityMonitor wifiConnectivityMonitor15 = WifiConnectivityMonitor.this;
                                wifiConnectivityMonitor15.setRssiPatchHistory(wifiConnectivityMonitor15.mLastTxBad, WifiConnectivityMonitor.this.mLastTxGood, this.mLastRxGood);
                                return true;
                            }
                            WifiConnectivityMonitor.this.mCurrentBssid.mBssidAvoidTimeMax = SystemClock.elapsedRealtime() + 30000;
                            WifiConnectivityMonitor.this.mGoodTargetCount = 0;
                            long elapsedRealtime = WifiConnectivityMonitor.this.mCurrentBssid.mBssidAvoidTimeMax - SystemClock.elapsedRealtime();
                            return true;
                        }
                    }
                    if (!WifiConnectivityMonitor.DBG) {
                        return true;
                    }
                    Log.d(WifiConnectivityMonitor.TAG, "RSSI_FETCH_FAILED reason : " + txPacketInfo.result);
                    return true;
                case WifiConnectivityMonitor.CMD_TRAFFIC_POLL /*{ENCODED_INT: 135193}*/:
                    if (!WifiConnectivityMonitor.this.isConnectedState() || WifiConnectivityMonitor.this.mCurrentBssid == null || WifiConnectivityMonitor.this.mCurrentMode == 0) {
                        return true;
                    }
                    if ((WifiConnectivityMonitor.this.mCurrentMode == 2 || WifiConnectivityMonitor.this.mCurrentMode == 3) && WifiConnectivityMonitor.mLinkDetectMode == 0) {
                        return true;
                    }
                    WifiConnectivityMonitor.this.setLogOnlyTransitions(true);
                    if (msg.arg1 == WifiConnectivityMonitor.this.mTrafficPollToken && (WifiConnectivityMonitor.this.mIsScreenOn || !WifiConnectivityMonitor.mInitialResultSentToSystemUi)) {
                        WifiConnectivityMonitor wifiConnectivityMonitor16 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor16.sendMessageDelayed(wifiConnectivityMonitor16.obtainMessage(WifiConnectivityMonitor.CMD_TRAFFIC_POLL, WifiConnectivityMonitor.access$16604(wifiConnectivityMonitor16), 0), 3000);
                    } else if (msg.arg1 != WifiConnectivityMonitor.this.mTrafficPollToken && (WifiConnectivityMonitor.this.mIsScreenOn || !WifiConnectivityMonitor.mInitialResultSentToSystemUi)) {
                        Log.d(WifiConnectivityMonitor.TAG, "mTrafficPollToken MisMatch!!!");
                        WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.CMD_TRAFFIC_POLL);
                        WifiConnectivityMonitor wifiConnectivityMonitor17 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor17.sendMessageDelayed(wifiConnectivityMonitor17.obtainMessage(WifiConnectivityMonitor.CMD_TRAFFIC_POLL, WifiConnectivityMonitor.access$16604(wifiConnectivityMonitor17), 0), 3000);
                    }
                    long preTxPkts = WifiConnectivityMonitor.this.mTxPkts;
                    long preRxPkts = WifiConnectivityMonitor.this.mRxPkts;
                    WifiConnectivityMonitor.this.mTxPkts = TrafficStats.getTxPackets(WifiConnectivityMonitor.INTERFACENAMEOFWLAN);
                    WifiConnectivityMonitor.this.mRxPkts = TrafficStats.getRxPackets(WifiConnectivityMonitor.INTERFACENAMEOFWLAN);
                    if (preTxPkts == WifiConnectivityMonitor.this.mTxPkts && preRxPkts == WifiConnectivityMonitor.this.mRxPkts) {
                        return true;
                    }
                    if (WifiConnectivityMonitor.this.mActivityManager == null) {
                        WifiConnectivityMonitor wifiConnectivityMonitor18 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor18.mActivityManager = (ActivityManager) wifiConnectivityMonitor18.mContext.getSystemService("activity");
                    }
                    WifiConnectivityMonitor.this.setTrafficPollHistory(TrafficStats.getTxBytes(WifiConnectivityMonitor.INTERFACENAMEOFWLAN), TrafficStats.getRxBytes(WifiConnectivityMonitor.INTERFACENAMEOFWLAN), TrafficStats.getUidTxBytes(WifiConnectivityMonitor.this.mUsageStatsUid), TrafficStats.getUidRxBytes(WifiConnectivityMonitor.this.mUsageStatsUid));
                    return true;
                default:
                    return false;
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int getLinkDetectMode() {
        return mLinkDetectMode;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setLinkDetectMode(int mode) {
        if (this.mCurrentMode != 0) {
            Log.d(TAG, "setLinkDetectMode : " + mode);
            if (mLinkDetectMode != mode) {
                if (mode == 0) {
                    sendMessage(CMD_LINK_POOR_ENTERED);
                } else if (mode == 1) {
                    this.mLinkLossOccurred = 0;
                    this.mLossSampleCount = 0;
                    this.mOvercomingCount = 0;
                    sendMessage(CMD_LINK_GOOD_ENTERED);
                }
            }
            mLinkDetectMode = mode;
        }
    }

    /* access modifiers changed from: package-private */
    public class InvalidState extends State {
        private static final int TIME_204_CHECK_INTERVAL = 30000;
        private long mLastRxGood;

        InvalidState() {
        }

        public void enter() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName());
            }
            WifiConnectivityMonitor.this.bSetQcResult = false;
            WifiConnectivityMonitor.this.determineMode();
            WifiConnectivityMonitor.this.mIs204CheckInterval = false;
            WifiConnectivityMonitor.this.mIWCChannel.sendMessage((int) IWCMonitor.TRANSIT_TO_INVALID);
            WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
            wifiConnectivityMonitor.sendMessage(wifiConnectivityMonitor.obtainMessage(WifiConnectivityMonitor.CMD_RSSI_FETCH, WifiConnectivityMonitor.access$13604(wifiConnectivityMonitor), 0));
            WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
            wifiConnectivityMonitor2.sendMessage(wifiConnectivityMonitor2.obtainMessage(WifiConnectivityMonitor.CMD_TRAFFIC_POLL, WifiConnectivityMonitor.access$16604(wifiConnectivityMonitor2), 0));
            WifiConnectivityMonitor.this.stopScoreQualityCheck();
            WifiConnectivityMonitor.this.mWifiNeedRecoveryFromEle = false;
            if (WifiConnectivityMonitor.this.mWifiEleStateTracker != null) {
                WifiConnectivityMonitor.this.mWifiEleStateTracker.clearEleValidBlockFlag();
            }
            if (WifiConnectivityMonitor.this.mCurrentMode == 1) {
                WifiConnectivityMonitor.this.setValidationBlock(false);
                WifiConnectivityMonitor.this.changeWifiIcon(1);
            } else if (WifiConnectivityMonitor.mUserSelectionConfirmed) {
                Log.d(WifiConnectivityMonitor.TAG, "mUserSelectionConfirmed : " + WifiConnectivityMonitor.mUserSelectionConfirmed);
                WifiConnectivityMonitor.this.changeWifiIcon(0);
            }
            if (WifiConnectivityMonitor.this.mCurrentMode == 2 || WifiConnectivityMonitor.this.mCurrentMode == 3) {
                WifiConnectivityMonitor.this.mValidationCheckEnabledTime = SystemClock.elapsedRealtime();
                WifiConnectivityMonitor.this.mClientModeImpl.startScanFromWcm();
                if (WifiConnectivityMonitor.DBG) {
                    Log.d(WifiConnectivityMonitor.TAG, "Start scan to find alternative networks. " + WifiConnectivityMonitor.this.getCurrentState() + WifiConnectivityMonitor.this.getCurrentMode());
                }
            }
            WifiConnectivityMonitor wifiConnectivityMonitor3 = WifiConnectivityMonitor.this;
            wifiConnectivityMonitor3.mWifiInfo = wifiConnectivityMonitor3.syncGetCurrentWifiInfo();
            int currentRssi = WifiConnectivityMonitor.this.mWifiInfo.getNetworkId() != -1 ? WifiConnectivityMonitor.this.mWifiInfo.getRssi() : -61;
            if (!WifiConnectivityMonitor.mIsECNTReportedConnection && WifiConnectivityMonitor.mIsPassedLevel1State && currentRssi > -60) {
                WifiConnectivityMonitor.this.uploadNoInternetECNTBigData();
            }
            if (currentRssi > -64) {
                WifiConnectivityMonitor.this.mContext.sendBroadcastAsUser(new Intent("com.sec.android.HEAT_WIFI_UNWANTED"), UserHandle.ALL);
            }
            WifiConnectivityMonitor.this.getClientModeChannel().unwantedMoreDump();
            WifiConnectivityMonitor.this.sendBroadcastWCMStatusChanged();
            WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.NETWORK_STAT_CHECK_DNS);
            WifiConnectivityMonitor.this.mCurrentBssid.updateBssidNoInternet();
        }

        public void exit() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName() + " exit\n");
            }
            WifiConnectivityMonitor.this.mTransitionPendingByIwc = false;
            WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.CMD_CHECK_ALTERNATIVE_FOR_CAPTIVE_PORTAL);
        }

        public boolean processMessage(Message msg) {
            WifiConnectivityMonitor.this.logStateAndMessage(msg, this);
            WifiConnectivityMonitor.this.setLogOnlyTransitions(false);
            switch (msg.what) {
                case WifiConnectivityMonitor.EVENT_WATCHDOG_SETTINGS_CHANGE /*{ENCODED_INT: 135174}*/:
                    WifiConnectivityMonitor.this.updateSettings();
                    int previousMode = WifiConnectivityMonitor.this.mCurrentMode;
                    WifiConnectivityMonitor.this.determineMode();
                    if (previousMode != WifiConnectivityMonitor.this.mCurrentMode) {
                        if (WifiConnectivityMonitor.this.mCurrentMode == 1) {
                            WifiConnectivityMonitor.this.setValidationBlock(false);
                            WifiConnectivityMonitor.this.changeWifiIcon(1);
                            if (!WifiConnectivityMonitor.this.mAirPlaneMode) {
                                WifiConnectivityMonitor.this.getCm().setWcmAcceptUnvalidated(WifiConnectivityMonitor.this.mNetwork, !WifiConnectivityMonitor.this.mPoorNetworkDetectionEnabled);
                            }
                            if (WifiConnectivityMonitor.mInitialResultSentToSystemUi) {
                                WifiConnectivityMonitor.this.showNetworkSwitchedNotification(false);
                            }
                        } else if (WifiConnectivityMonitor.this.mCurrentMode == 2) {
                            if (previousMode == 1) {
                                WifiConnectivityMonitor.this.setValidationBlock(true);
                                WifiConnectivityMonitor.this.changeWifiIcon(0);
                                WifiConnectivityMonitor.this.getCm().setWcmAcceptUnvalidated(WifiConnectivityMonitor.this.mNetwork, !WifiConnectivityMonitor.this.mPoorNetworkDetectionEnabled);
                                if (WifiConnectivityMonitor.mInitialResultSentToSystemUi) {
                                    WifiConnectivityMonitor.this.mAnalyticsDisconnectReason = 0;
                                    Log.d(WifiConnectivityMonitor.TAG, "POOR_LINK_DETECT_sent");
                                }
                            }
                        } else if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                            if (previousMode == 1) {
                                WifiConnectivityMonitor.this.setValidationBlock(true);
                                WifiConnectivityMonitor.this.changeWifiIcon(0);
                                WifiConnectivityMonitor.this.getCm().setWcmAcceptUnvalidated(WifiConnectivityMonitor.this.mNetwork, !WifiConnectivityMonitor.this.mPoorNetworkDetectionEnabled);
                                if (WifiConnectivityMonitor.mInitialResultSentToSystemUi) {
                                    WifiConnectivityMonitor.this.mAnalyticsDisconnectReason = 0;
                                }
                            }
                            WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor.sendMessage(wifiConnectivityMonitor.obtainMessage(WifiConnectivityMonitor.CMD_RSSI_FETCH, WifiConnectivityMonitor.access$13604(wifiConnectivityMonitor), 0));
                        } else {
                            WifiConnectivityMonitor.this.showNetworkSwitchedNotification(false);
                            WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor2.transitionTo(wifiConnectivityMonitor2.mValidState);
                        }
                    }
                    return true;
                case WifiConnectivityMonitor.REPORT_NETWORK_CONNECTIVITY /*{ENCODED_INT: 135204}*/:
                    WifiConnectivityMonitor.this.reportNetworkConnectivityToNM(msg.arg1, msg.arg2);
                    return true;
                case WifiConnectivityMonitor.CONNECTIVITY_VALIDATION_RESULT /*{ENCODED_INT: 135206}*/:
                    boolean valid = msg.arg1 == 1;
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "CONNECTIVITY_VALIDATION_RESULT : " + valid);
                    }
                    if (!valid) {
                        WifiConnectivityMonitor.this.mValidationCheckMode = false;
                        WifiConnectivityMonitor.this.mValidationResultCount = 0;
                        WifiConnectivityMonitor.this.mValidationCheckCount = 0;
                        WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.VALIDATION_CHECK_FORCE);
                        return true;
                    }
                    WifiConnectivityMonitor.access$5508(WifiConnectivityMonitor.this);
                    return true;
                case WifiConnectivityMonitor.VALIDATION_CHECK_FORCE /*{ENCODED_INT: 135207}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "VALIDATION_CHECK_FORCE");
                    }
                    WifiConnectivityMonitor.this.mValidationCheckEnabledTime = SystemClock.elapsedRealtime();
                    WifiConnectivityMonitor.this.mValidationCheckTime /= 2;
                    WifiConnectivityMonitor.access$5608(WifiConnectivityMonitor.this);
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "mValidationCheckCount : " + WifiConnectivityMonitor.this.mValidationCheckCount);
                    }
                    if (WifiConnectivityMonitor.this.mWCMQCResult != null) {
                        WifiConnectivityMonitor.this.mWCMQCResult.recycle();
                        WifiConnectivityMonitor.this.mWCMQCResult = null;
                    }
                    WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.QC_RESULT_NOT_RECEIVED);
                    boolean queried = WifiConnectivityMonitor.this.reportNetworkConnectivityToNM(true, 5, 20);
                    if (WifiConnectivityMonitor.this.mValidationCheckCount > WifiConnectivityMonitor.VALIDATION_CHECK_MAX_COUNT) {
                        WifiConnectivityMonitor.this.mValidationCheckMode = false;
                        WifiConnectivityMonitor.this.setValidationBlock(false);
                        if (WifiConnectivityMonitor.DBG) {
                            Log.d(WifiConnectivityMonitor.TAG, "mValidationCheckCount expired");
                        }
                        if (queried) {
                            WifiConnectivityMonitor.this.mValidationResultCount = 0;
                            WifiConnectivityMonitor.this.mValidationCheckCount = 0;
                        } else {
                            WifiConnectivityMonitor.this.sendMessageDelayed(WifiConnectivityMonitor.VALIDATION_CHECK_FORCE, 10000);
                        }
                        return true;
                    }
                    WifiConnectivityMonitor wifiConnectivityMonitor3 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor3.sendMessageDelayed(WifiConnectivityMonitor.VALIDATION_CHECK_FORCE, (long) (wifiConnectivityMonitor3.mValidationCheckTime * 1000));
                    return true;
                case WifiConnectivityMonitor.QC_RESET_204_CHECK_INTERVAL /*{ENCODED_INT: 135208}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "QC_RESET_204_CHECK_INTERVAL");
                    }
                    WifiConnectivityMonitor.this.mIs204CheckInterval = false;
                    return true;
                case WifiConnectivityMonitor.EVENT_MOBILE_CONNECTED /*{ENCODED_INT: 135232}*/:
                    if (WifiConnectivityMonitor.mInitialResultSentToSystemUi && WifiConnectivityMonitor.mUserSelectionConfirmed && (WifiConnectivityMonitor.this.mCurrentMode == 2 || WifiConnectivityMonitor.this.mCurrentMode == 3)) {
                        WifiConnectivityMonitor wifiConnectivityMonitor4 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor4.mWifiInfo = wifiConnectivityMonitor4.syncGetCurrentWifiInfo();
                        WifiConnectivityMonitor wifiConnectivityMonitor5 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor5.mPoorConnectionDisconnectedNetId = wifiConnectivityMonitor5.mWifiInfo.getNetworkId();
                        if (WifiConnectivityMonitor.this.mNumOfToggled < 1) {
                            WifiConnectivityMonitor.this.showNetworkSwitchedNotification(true);
                        }
                        WifiConnectivityMonitor.this.sendMessageDelayed(WifiConnectivityMonitor.CMD_CHANGE_WIFI_ICON_VISIBILITY, 0);
                    }
                    return true;
                case WifiConnectivityMonitor.EVENT_WIFI_TOGGLED /*{ENCODED_INT: 135243}*/:
                    boolean on = msg.arg1 == 1;
                    String bssid = (String) msg.obj;
                    if (!on && bssid != null) {
                        if (WifiConnectivityMonitor.this.mBssidCache.get(bssid) != null) {
                            Log.d(WifiConnectivityMonitor.TAG, "BssidStatistics removed - " + bssid.substring(9));
                            WifiConnectivityMonitor.this.mBssidCache.remove(bssid);
                        }
                        WifiConnectivityMonitor.this.updateCurrentBssid(null, -1);
                        WifiConnectivityMonitor wifiConnectivityMonitor6 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor6.transitionTo(wifiConnectivityMonitor6.mNotConnectedState);
                    }
                    if (!on && WifiConnectivityMonitor.this.mIsWifiEnabled) {
                        WifiConnectivityMonitor.this.showNetworkSwitchedNotification(false);
                    }
                    WifiConnectivityMonitor.this.mIsWifiEnabled = on;
                    return true;
                case WifiConnectivityMonitor.EVENT_USER_SELECTION /*{ENCODED_INT: 135264}*/:
                    boolean keepConnection = msg.arg1 == 1;
                    Log.d(WifiConnectivityMonitor.TAG, getName() + " EVENT_USER_SELECTION : " + keepConnection);
                    if (!keepConnection) {
                        WifiConnectivityMonitor.this.changeWifiIcon(0);
                    }
                    boolean unused = WifiConnectivityMonitor.mUserSelectionConfirmed = true;
                    WifiConnectivityMonitor.this.determineMode();
                    return true;
                case WifiConnectivityMonitor.CMD_QUALITY_CHECK_BY_SCORE /*{ENCODED_INT: 135388}*/:
                    WifiConnectivityMonitor.this.requestInternetCheck(16);
                    return true;
                case WifiConnectivityMonitor.VALIDATED_DETECTED /*{ENCODED_INT: 135472}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "VALIDATED_DETECTED");
                    }
                    boolean unused2 = WifiConnectivityMonitor.mUserSelectionConfirmed = true;
                    if (WifiConnectivityMonitor.this.getLinkDetectMode() == 1) {
                        if (WifiConnectivityMonitor.this.mCurrentMode == 1) {
                            WifiConnectivityMonitor.this.mSnsBigDataSCNT.mInvGqNon++;
                        } else if (WifiConnectivityMonitor.this.mCurrentMode == 2) {
                            WifiConnectivityMonitor.this.mSnsBigDataSCNT.mInvGqNormal++;
                        } else if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                            WifiConnectivityMonitor.this.mSnsBigDataSCNT.mInvGqAgg++;
                        }
                    } else if (WifiConnectivityMonitor.this.mCurrentMode == 1) {
                        WifiConnectivityMonitor.this.mSnsBigDataSCNT.mInvPqNon++;
                    } else if (WifiConnectivityMonitor.this.mCurrentMode == 2) {
                        WifiConnectivityMonitor.this.mSnsBigDataSCNT.mInvPqNormal++;
                    } else if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                        WifiConnectivityMonitor.this.mSnsBigDataSCNT.mInvPqAgg++;
                    }
                    WifiConnectivityMonitor.this.setBigDataQCandNS(true);
                    WifiConnectivityMonitor.this.mValidStartTime = SystemClock.elapsedRealtime();
                    WifiConnectivityMonitor wifiConnectivityMonitor7 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor7.transitionTo(wifiConnectivityMonitor7.mValidState);
                    return true;
                case WifiConnectivityMonitor.INVALIDATED_DETECTED /*{ENCODED_INT: 135473}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "INVALIDATED_DETECTED");
                    }
                    WifiConnectivityMonitor.this.mGoodTargetCount = 0;
                    if (!WifiConnectivityMonitor.mInitialResultSentToSystemUi) {
                        WifiConnectivityMonitor.this.mAnalyticsDisconnectReason = 0;
                    }
                    WifiConnectivityMonitor.this.mInvalidationFailHistory.error = 17;
                    WifiConnectivityMonitor.this.mInvalidationFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                    WifiConnectivityMonitor wifiConnectivityMonitor8 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor8.setQcFailHistory(wifiConnectivityMonitor8.mInvalidationFailHistory);
                    WifiConnectivityMonitor.this.bSetQcResult = false;
                    WifiConnectivityMonitor.this.setBigDataQualityCheck(false);
                    return true;
                case WifiConnectivityMonitor.CMD_CHECK_ALTERNATIVE_FOR_CAPTIVE_PORTAL /*{ENCODED_INT: 135481}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "CHECK_ALTERNATIVE_NETWORKS - mInvalidState && SNS ON");
                    }
                    WifiConnectivityMonitor.this.mClientModeImpl.checkAlternativeNetworksForWmc(false);
                    return true;
                default:
                    return false;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public class ValidState extends State {
        ValidState() {
        }

        public void enter() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName());
            }
            if (WifiConnectivityMonitor.this.mCurrentMode != 0) {
                WifiConnectivityMonitor.this.mIWCChannel.sendMessage((int) IWCMonitor.TRANSIT_TO_VALID);
            }
            WifiConnectivityMonitor.this.mCurrentBssid.mBssidAvoidTimeMax = SystemClock.elapsedRealtime();
            WifiConnectivityMonitor.this.updateTargetRssiForCurrentAP(false);
            WifiConnectivityMonitor.this.determineMode();
            WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.CMD_TRANSIT_ON_VALID);
            WifiConnectivityMonitor.this.setValidationBlock(false);
            WifiConnectivityMonitor.this.mValidationResultCount = 0;
            WifiConnectivityMonitor.this.mValidationCheckCount = 0;
            WifiConnectivityMonitor.this.mValidationCheckMode = false;
            if (WifiConnectivityMonitor.this.mWifiEleStateTracker != null) {
                WifiConnectivityMonitor.this.mWifiEleStateTracker.clearEleValidBlockFlag();
            }
            if (WifiConnectivityMonitor.this.mCurrentMode != 0) {
                WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                wifiConnectivityMonitor.sendMessage(wifiConnectivityMonitor.obtainMessage(WifiConnectivityMonitor.CMD_RSSI_FETCH, WifiConnectivityMonitor.access$13604(wifiConnectivityMonitor), 0));
                WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
                wifiConnectivityMonitor2.sendMessage(wifiConnectivityMonitor2.obtainMessage(WifiConnectivityMonitor.CMD_TRAFFIC_POLL, WifiConnectivityMonitor.access$16604(wifiConnectivityMonitor2), 0));
                WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_START);
                WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.NETWORK_STAT_CHECK_DNS);
                WifiConnectivityMonitor.this.startPoorQualityScoreCheck();
            }
            WifiConnectivityMonitor.this.sendBroadcastWCMStatusChanged();
            WifiConnectivityMonitor.this.setLoggingForTCPStat(WifiConnectivityMonitor.TCP_STAT_LOGGING_RESET);
            WifiConnectivityMonitor.this.mCurrentBssid.updateBssidNoInternet();
        }

        public void exit() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName() + " exit\n");
            }
            WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_STOP);
        }

        public boolean processMessage(Message msg) {
            WifiConnectivityMonitor.this.logStateAndMessage(msg, this);
            WifiConnectivityMonitor.this.setLogOnlyTransitions(false);
            switch (msg.what) {
                case WifiConnectivityMonitor.EVENT_WATCHDOG_SETTINGS_CHANGE /*{ENCODED_INT: 135174}*/:
                    WifiConnectivityMonitor.this.updateSettings();
                    int previousMode = WifiConnectivityMonitor.this.mCurrentMode;
                    WifiConnectivityMonitor.this.determineMode();
                    if (previousMode != WifiConnectivityMonitor.this.mCurrentMode) {
                        if (previousMode == 0) {
                            WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor.sendMessage(wifiConnectivityMonitor.obtainMessage(WifiConnectivityMonitor.CMD_RSSI_FETCH, WifiConnectivityMonitor.access$13604(wifiConnectivityMonitor), 0));
                            WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor2.sendMessage(wifiConnectivityMonitor2.obtainMessage(WifiConnectivityMonitor.CMD_TRAFFIC_POLL, WifiConnectivityMonitor.access$16604(wifiConnectivityMonitor2), 0));
                            WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_START);
                            WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.NETWORK_STAT_CHECK_DNS);
                        } else if (WifiConnectivityMonitor.this.mCurrentMode != 0 && (OpBrandingLoader.Vendor.SKT == WifiConnectivityMonitor.mOpBranding || OpBrandingLoader.Vendor.KTT == WifiConnectivityMonitor.mOpBranding || OpBrandingLoader.Vendor.LGU == WifiConnectivityMonitor.mOpBranding)) {
                            WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_STOP);
                            WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_START);
                        }
                        if (WifiConnectivityMonitor.this.mCurrentMode == 1) {
                            if (!WifiConnectivityMonitor.this.mAirPlaneMode) {
                                WifiConnectivityMonitor.this.getCm().setWcmAcceptUnvalidated(WifiConnectivityMonitor.this.mNetwork, !WifiConnectivityMonitor.this.mPoorNetworkDetectionEnabled);
                            }
                            WifiConnectivityMonitor.this.setWifiNetworkEnabled(true);
                            WifiConnectivityMonitor wifiConnectivityMonitor3 = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor3.transitionTo(wifiConnectivityMonitor3.mValidNonSwitchableState);
                        } else if (previousMode == 1 && (WifiConnectivityMonitor.this.mCurrentMode == 2 || WifiConnectivityMonitor.this.mCurrentMode == 3)) {
                            WifiConnectivityMonitor.this.getCm().setWcmAcceptUnvalidated(WifiConnectivityMonitor.this.mNetwork, false);
                            WifiConnectivityMonitor wifiConnectivityMonitor4 = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor4.transitionTo(wifiConnectivityMonitor4.mValidSwitchableState);
                        } else if (WifiConnectivityMonitor.this.mCurrentMode == 0) {
                            WifiConnectivityMonitor.this.mNetworkStatsAnalyzer.sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_STOP);
                            WifiConnectivityMonitor.access$13604(WifiConnectivityMonitor.this);
                            WifiConnectivityMonitor wifiConnectivityMonitor5 = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor5.transitionTo(wifiConnectivityMonitor5.mValidNoCheckState);
                        }
                    }
                    return true;
                case WifiConnectivityMonitor.REPORT_NETWORK_CONNECTIVITY /*{ENCODED_INT: 135204}*/:
                    return true;
                case WifiConnectivityMonitor.CMD_TRANSIT_ON_VALID /*{ENCODED_INT: 135299}*/:
                    if (WifiConnectivityMonitor.this.mCurrentMode == 0) {
                        WifiConnectivityMonitor wifiConnectivityMonitor6 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor6.transitionTo(wifiConnectivityMonitor6.mValidNoCheckState);
                    } else if (WifiConnectivityMonitor.this.mCurrentMode == 1) {
                        WifiConnectivityMonitor wifiConnectivityMonitor7 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor7.transitionTo(wifiConnectivityMonitor7.mValidNonSwitchableState);
                    } else {
                        WifiConnectivityMonitor wifiConnectivityMonitor8 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor8.transitionTo(wifiConnectivityMonitor8.mValidSwitchableState);
                    }
                    return true;
                case WifiConnectivityMonitor.CMD_QUALITY_CHECK_BY_SCORE /*{ENCODED_INT: 135388}*/:
                    WifiConnectivityMonitor.this.requestInternetCheck(27);
                    return true;
                case WifiConnectivityMonitor.CMD_REACHABILITY_LOST /*{ENCODED_INT: 135400}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "CMD_REACHABILITY_LOST");
                    }
                    WifiConnectivityMonitor.this.requestInternetCheck(4);
                    if (WifiConnectivityMonitor.this.mCurrentBssid != null) {
                        WifiConnectivityMonitor.this.mCurrentBssid.updateBssidQosMapOnReachabilityLost();
                    }
                    return true;
                case WifiConnectivityMonitor.CMD_PROVISIONING_FAIL /*{ENCODED_INT: 135401}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "CMD_PROVISIONING_FAIL");
                    }
                    WifiConnectivityMonitor wifiConnectivityMonitor9 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor9.reportNetworkConnectivityToNM(true, wifiConnectivityMonitor9.mInvalidationFailHistory.qcStepTemp, 3);
                    return true;
                default:
                    return false;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public class ValidNonSwitchableState extends State {
        public int mMinQualifiedRssi = 0;

        ValidNonSwitchableState() {
        }

        public void enter() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName());
            }
            WifiConnectivityMonitor.this.changeWifiIcon(1);
            WifiConnectivityMonitor.this.showNetworkSwitchedNotification(false);
        }

        public void exit() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName() + " exit\n");
            }
        }

        public boolean processMessage(Message msg) {
            WifiConnectivityMonitor.this.logStateAndMessage(msg, this);
            WifiConnectivityMonitor.this.setLogOnlyTransitions(false);
            WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
            wifiConnectivityMonitor.mWifiInfo = wifiConnectivityMonitor.syncGetCurrentWifiInfo();
            WifiConnectivityMonitor.this.mWifiInfo.getRssi();
            switch (msg.what) {
                case WifiConnectivityMonitor.EVENT_SCREEN_OFF /*{ENCODED_INT: 135177}*/:
                    WifiConnectivityMonitor.this.mLossSampleCount = 0;
                    return false;
                case WifiConnectivityMonitor.REPORT_NETWORK_CONNECTIVITY /*{ENCODED_INT: 135204}*/:
                    WifiConnectivityMonitor.this.reportNetworkConnectivityToNM(msg.arg1, msg.arg2);
                    return true;
                case WifiConnectivityMonitor.VALIDATED_DETECTED /*{ENCODED_INT: 135472}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "VALIDATED_DETECTED");
                    }
                    WifiConnectivityMonitor.this.setBigDataQualityCheck(true);
                    WifiConnectivityMonitor.this.setLoggingForTCPStat(WifiConnectivityMonitor.TCP_STAT_LOGGING_RESET);
                    return true;
                case WifiConnectivityMonitor.INVALIDATED_DETECTED /*{ENCODED_INT: 135473}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "INVALIDATED_DETECTED");
                    }
                    WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor2.mWifiInfo = wifiConnectivityMonitor2.syncGetCurrentWifiInfo();
                    WifiConnectivityMonitor wifiConnectivityMonitor3 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor3.mInvalidationRssi = wifiConnectivityMonitor3.mWifiInfo.getRssi();
                    if (WifiConnectivityMonitor.this.mInvalidationRssi >= -55 && WifiConnectivityMonitor.this.mWifiInfo.getLinkSpeed() >= 54) {
                        WifiConnectivityMonitor.this.setValidationBlock(false);
                    }
                    WifiConnectivityMonitor.this.setBigDataValidationChanged();
                    WifiConnectivityMonitor.this.mInvalidationFailHistory.error = 17;
                    WifiConnectivityMonitor.this.mInvalidationFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                    WifiConnectivityMonitor wifiConnectivityMonitor4 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor4.setQcFailHistory(wifiConnectivityMonitor4.mInvalidationFailHistory);
                    WifiConnectivityMonitor.this.bSetQcResult = false;
                    if (WifiConnectivityMonitor.this.getLinkDetectMode() == 1) {
                        WifiConnectivityMonitor.this.mSnsBigDataSCNT.mGqInvNon++;
                    } else {
                        WifiConnectivityMonitor.this.mSnsBigDataSCNT.mPqInvNon++;
                    }
                    WifiConnectivityMonitor.this.setBigDataQCandNS(false);
                    WifiConnectivityMonitor.this.setLoggingForTCPStat(WifiConnectivityMonitor.TCP_STAT_LOGGING_SECOND);
                    WifiConnectivityMonitor wifiConnectivityMonitor5 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor5.transitionTo(wifiConnectivityMonitor5.mInvalidState);
                    WifiConnectivityMonitor.this.mInvalidStartTime = SystemClock.elapsedRealtime();
                    return true;
                default:
                    return false;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public class ValidSwitchableState extends State {
        ValidSwitchableState() {
        }

        public void enter() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName());
            }
            WifiConnectivityMonitor.this.bSetQcResult = false;
            WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.CMD_TRANSIT_ON_SWITCHABLE);
        }

        public void exit() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName() + " exit\n");
            }
        }

        public boolean processMessage(Message msg) {
            WifiConnectivityMonitor.this.logStateAndMessage(msg, this);
            WifiConnectivityMonitor.this.setLogOnlyTransitions(false);
            SystemClock.elapsedRealtime();
            int i = msg.what;
            if (i != WifiConnectivityMonitor.REPORT_NETWORK_CONNECTIVITY) {
                if (i != WifiConnectivityMonitor.CMD_TRANSIT_ON_SWITCHABLE) {
                    switch (i) {
                        case WifiConnectivityMonitor.VALIDATED_DETECTED /*{ENCODED_INT: 135472}*/:
                        case WifiConnectivityMonitor.VALIDATED_DETECTED_AGAIN /*{ENCODED_INT: 135474}*/:
                            WifiConnectivityMonitor.this.setLoggingForTCPStat(WifiConnectivityMonitor.TCP_STAT_LOGGING_RESET);
                            if (WifiConnectivityMonitor.DBG) {
                                if (msg.what == WifiConnectivityMonitor.VALIDATED_DETECTED) {
                                    Log.d(WifiConnectivityMonitor.TAG, "VALIDATED_DETECTED");
                                }
                                if (msg.what == WifiConnectivityMonitor.VALIDATED_DETECTED_AGAIN) {
                                    Log.d(WifiConnectivityMonitor.TAG, "VALIDATED_DETECTED_AGAIN");
                                }
                            }
                            if (WifiConnectivityMonitor.this.mCheckRoamedNetwork) {
                                WifiConnectivityMonitor.this.mCheckRoamedNetwork = false;
                                if (WifiConnectivityMonitor.this.getCurrentState() == WifiConnectivityMonitor.this.mLevel2State) {
                                    if (WifiConnectivityMonitor.this.mCurrentMode == 2) {
                                        WifiConnectivityMonitor.this.mSnsBigDataSCNT.mPqGqNormal++;
                                    } else {
                                        WifiConnectivityMonitor.this.mSnsBigDataSCNT.mPqGqAgg++;
                                    }
                                    WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                                    wifiConnectivityMonitor.transitionTo(wifiConnectivityMonitor.mLevel1State);
                                }
                            }
                            WifiConnectivityMonitor.this.setBigDataQualityCheck(true);
                            return true;
                        case WifiConnectivityMonitor.INVALIDATED_DETECTED /*{ENCODED_INT: 135473}*/:
                        case WifiConnectivityMonitor.INVALIDATED_DETECTED_AGAIN /*{ENCODED_INT: 135475}*/:
                            if (WifiConnectivityMonitor.DBG) {
                                if (msg.what == WifiConnectivityMonitor.INVALIDATED_DETECTED) {
                                    Log.d(WifiConnectivityMonitor.TAG, "INVALIDATED_DETECTED");
                                }
                                if (msg.what == WifiConnectivityMonitor.INVALIDATED_DETECTED_AGAIN) {
                                    Log.d(WifiConnectivityMonitor.TAG, "INVALIDATED_DETECTED_AGAIN");
                                }
                            }
                            if (WifiConnectivityMonitor.this.mCheckRoamedNetwork) {
                                WifiConnectivityMonitor.this.mCheckRoamedNetwork = false;
                            }
                            WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor2.mWifiInfo = wifiConnectivityMonitor2.syncGetCurrentWifiInfo();
                            WifiConnectivityMonitor wifiConnectivityMonitor3 = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor3.mInvalidationRssi = wifiConnectivityMonitor3.mWifiInfo.getRssi();
                            if (WifiConnectivityMonitor.this.mInvalidationRssi < -55 || WifiConnectivityMonitor.this.mWifiInfo.getLinkSpeed() < 54) {
                                WifiConnectivityMonitor.this.mOvercomingCount = 0;
                                WifiConnectivityMonitor.this.setValidationBlock(true);
                            } else {
                                WifiConnectivityMonitor.this.setValidationBlock(false);
                            }
                            if (msg.arg1 == 1) {
                                WifiConnectivityMonitor.this.mInvalidStateTesting = true;
                            }
                            WifiConnectivityMonitor.this.mInvalidationFailHistory.error = 17;
                            WifiConnectivityMonitor.this.mInvalidationFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                            WifiConnectivityMonitor wifiConnectivityMonitor4 = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor4.setQcFailHistory(wifiConnectivityMonitor4.mInvalidationFailHistory);
                            WifiConnectivityMonitor.this.bSetQcResult = false;
                            if (WifiConnectivityMonitor.this.getLinkDetectMode() == 1) {
                                if (WifiConnectivityMonitor.this.mCurrentMode == 2) {
                                    WifiConnectivityMonitor.this.mSnsBigDataSCNT.mGqInvNormal++;
                                } else if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                                    WifiConnectivityMonitor.this.mSnsBigDataSCNT.mGqInvAgg++;
                                }
                            }
                            WifiConnectivityMonitor.this.setBigDataQCandNS(false);
                            WifiConnectivityMonitor.this.setLoggingForTCPStat(WifiConnectivityMonitor.TCP_STAT_LOGGING_SECOND);
                            WifiConnectivityMonitor wifiConnectivityMonitor5 = WifiConnectivityMonitor.this;
                            wifiConnectivityMonitor5.transitionTo(wifiConnectivityMonitor5.mInvalidState);
                            WifiConnectivityMonitor.this.mInvalidStartTime = SystemClock.elapsedRealtime();
                            return true;
                        default:
                            return false;
                    }
                } else {
                    if (WifiConnectivityMonitor.mLinkDetectMode == 1) {
                        WifiConnectivityMonitor wifiConnectivityMonitor6 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor6.sendMessage(wifiConnectivityMonitor6.obtainMessage(WifiConnectivityMonitor.CMD_TRAFFIC_POLL, WifiConnectivityMonitor.access$16604(wifiConnectivityMonitor6), 0));
                        WifiConnectivityMonitor wifiConnectivityMonitor7 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor7.transitionTo(wifiConnectivityMonitor7.mLevel1State);
                    } else {
                        WifiConnectivityMonitor.this.checkTransitionToLevel2State();
                    }
                    return true;
                }
            } else if (WifiConnectivityMonitor.this.isMobileHotspot()) {
                return true;
            } else {
                if (WifiConnectivityMonitor.DBG) {
                    Log.i(WifiConnectivityMonitor.TAG, "[ValidSwitchable] REPORT_NETWORK_CONNECTIVITY");
                }
                WifiConnectivityMonitor.this.reportNetworkConnectivityToNM(msg.arg1, msg.arg2);
                return true;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public class Level1State extends State {
        Level1State() {
        }

        public void enter() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName());
            }
            WifiConnectivityMonitor.this.setLinkDetectMode(1);
            if (WifiConnectivityMonitor.this.mCheckRoamedNetwork) {
                WifiConnectivityMonitor.this.mCheckRoamedNetwork = false;
            }
            boolean unused = WifiConnectivityMonitor.mIsPassedLevel1State = true;
            WifiConnectivityMonitor.this.setWifiNetworkEnabled(true);
            WifiConnectivityMonitor.this.changeWifiIcon(1);
            WifiConnectivityMonitor.this.startPoorQualityScoreCheck();
            WifiConnectivityMonitor.this.showNetworkSwitchedNotification(false);
            WifiConnectivityMonitor.this.determineMode();
            if (WifiConnectivityMonitor.this.mWifiEleStateTracker != null) {
                WifiConnectivityMonitor.this.mWifiNeedRecoveryFromEle = false;
                WifiConnectivityMonitor.this.mWifiEleStateTracker.enableEleMobileRssiPolling(true, false);
                WifiConnectivityMonitor.this.mWifiEleStateTracker.registerElePedometer();
            }
            if (WifiConnectivityMonitor.this.mCurrentMode != 0) {
                WifiConnectivityMonitor.this.mIWCChannel.sendMessage((int) IWCMonitor.TRANSIT_TO_VALID);
            }
            WifiConnectivityMonitor.this.sendBroadcastWCMStatusChanged();
        }

        public void exit() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName() + " exit\n");
            }
            WifiConnectivityMonitor.this.mLevel2StateTransitionPending = false;
        }

        public boolean processMessage(Message msg) {
            WifiConnectivityMonitor.this.logStateAndMessage(msg, this);
            boolean roamFound = false;
            WifiConnectivityMonitor.this.setLogOnlyTransitions(false);
            switch (msg.what) {
                case WifiConnectivityMonitor.CMD_ELE_DETECTED /*{ENCODED_INT: 135283}*/:
                case WifiConnectivityMonitor.CMD_ELE_BY_GEO_DETECTED /*{ENCODED_INT: 135284}*/:
                    WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor.mTransitionScore = wifiConnectivityMonitor.mClientModeImpl.getWifiScoreReport().getCurrentScore();
                    if (msg.what == WifiConnectivityMonitor.CMD_ELE_DETECTED) {
                        WifiConnectivityMonitor.this.mLowQualityFailHistory.error = 21;
                    } else {
                        WifiConnectivityMonitor.this.mLowQualityFailHistory.error = 22;
                    }
                    WifiConnectivityMonitor.this.bSetQcResult = false;
                    WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor2.mWifiInfo = wifiConnectivityMonitor2.syncGetCurrentWifiInfo();
                    WifiConnectivityMonitor.this.mCurrentBssid.poorLinkDetected(WifiConnectivityMonitor.this.mWifiInfo.getRssi(), 1);
                    WifiConnectivityMonitor.this.mLowQualityFailHistory.qcTrigger = 61;
                    WifiConnectivityMonitor.this.mLowQualityFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                    WifiConnectivityMonitor wifiConnectivityMonitor3 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor3.setQcFailHistory(wifiConnectivityMonitor3.mLowQualityFailHistory);
                    WifiConnectivityMonitor.this.mSnsBigDataSCNT.mEleGP++;
                    WifiConnectivityMonitor wifiConnectivityMonitor4 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor4.transitionTo(wifiConnectivityMonitor4.mLevel2State);
                    return true;
                case WifiConnectivityMonitor.HANDLE_RESULT_ROAM_IN_HIGH_QUALITY /*{ENCODED_INT: 135479}*/:
                    WifiConnectivityMonitor.this.mLevel2StateTransitionPending = false;
                    if (msg.arg1 == 1) {
                        roamFound = true;
                    }
                    if (roamFound) {
                        Log.d(WifiConnectivityMonitor.TAG, "HANDLE_RESULT_ROAM_IN_HIGH_QUALITY - Roam target found");
                    } else {
                        Log.d(WifiConnectivityMonitor.TAG, "HANDLE_RESULT_ROAM_IN_HIGH_QUALITY - Roam target not found");
                        WifiConnectivityMonitor wifiConnectivityMonitor5 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor5.mWifiInfo = wifiConnectivityMonitor5.syncGetCurrentWifiInfo();
                        WifiConnectivityMonitor.this.mCurrentBssid.poorLinkDetected(WifiConnectivityMonitor.this.mWifiInfo.getRssi(), 1);
                        WifiConnectivityMonitor.this.mLowQualityFailHistory.error = 18;
                        WifiConnectivityMonitor.this.mLowQualityFailHistory.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                        WifiConnectivityMonitor wifiConnectivityMonitor6 = WifiConnectivityMonitor.this;
                        wifiConnectivityMonitor6.setQcFailHistory(wifiConnectivityMonitor6.mLowQualityFailHistory);
                        WifiConnectivityMonitor.this.checkTransitionToLevel2State();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public class Level2State extends State {
        Level2State() {
        }

        public void enter() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName());
            }
            WifiConnectivityMonitor.this.setLinkDetectMode(0);
            WifiConnectivityMonitor.this.setWifiNetworkEnabled(false);
            WifiConnectivityMonitor.this.changeWifiIcon(0);
            if (WifiConnectivityMonitor.this.mWifiEleStateTracker == null || !WifiConnectivityMonitor.this.mWifiEleStateTracker.checkEleValidBlockState()) {
                WifiConnectivityMonitor.this.stopScoreQualityCheck();
            } else {
                WifiConnectivityMonitor.this.startRecoveryScoreCheck();
            }
            if (WifiConnectivityMonitor.this.mWifiEleStateTracker != null && !WifiConnectivityMonitor.this.mWifiEleStateTracker.checkEleValidBlockState()) {
                WifiConnectivityMonitor.this.mWifiEleStateTracker.delayedEleCheckDisable();
            }
            WifiConnectivityMonitor.this.mClientModeImpl.startScanFromWcm();
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, "Start scan to find alternative networks. " + WifiConnectivityMonitor.this.getCurrentState() + WifiConnectivityMonitor.this.getCurrentMode());
            }
            if (!WifiConnectivityMonitor.mIsECNTReportedConnection && WifiConnectivityMonitor.mIsPassedLevel1State) {
                WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                wifiConnectivityMonitor.mWifiInfo = wifiConnectivityMonitor.syncGetCurrentWifiInfo();
                if ((WifiConnectivityMonitor.this.mWifiInfo.getNetworkId() != -1 ? WifiConnectivityMonitor.this.mWifiInfo.getRssi() : -61) > -60) {
                    WifiConnectivityMonitor.this.uploadNoInternetECNTBigData();
                }
            }
            WifiConnectivityMonitor.this.mIWCChannel.sendMessage((int) IWCMonitor.TRANSIT_TO_INVALID);
            WifiConnectivityMonitor.this.sendBroadcastWCMStatusChanged();
            WifiConnectivityMonitor.this.getClientModeChannel().unwantedMoreDump();
            WifiConnectivityMonitor.this.mCurrentBssid.updateBssidQosMapOnLevel2State(true);
        }

        public void exit() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName() + " exit\n");
            }
            WifiConnectivityMonitor.this.mCurrentBssid.updateBssidQosMapOnLevel2State(false);
        }

        public boolean processMessage(Message msg) {
            WifiConnectivityMonitor.this.logStateAndMessage(msg, this);
            WifiConnectivityMonitor.this.setLogOnlyTransitions(false);
            int i = msg.what;
            if (i == WifiConnectivityMonitor.EVENT_MOBILE_CONNECTED) {
                if (WifiConnectivityMonitor.mInitialResultSentToSystemUi && (WifiConnectivityMonitor.this.mCurrentMode == 2 || WifiConnectivityMonitor.this.mCurrentMode == 3)) {
                    WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor.mWifiInfo = wifiConnectivityMonitor.syncGetCurrentWifiInfo();
                    WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
                    wifiConnectivityMonitor2.mPoorConnectionDisconnectedNetId = wifiConnectivityMonitor2.mWifiInfo.getNetworkId();
                    if (WifiConnectivityMonitor.this.mNumOfToggled < 1) {
                        WifiConnectivityMonitor.this.showNetworkSwitchedNotification(true);
                    }
                    WifiConnectivityMonitor.this.sendMessageDelayed(WifiConnectivityMonitor.CMD_CHANGE_WIFI_ICON_VISIBILITY, 0);
                }
                return true;
            } else if (i != WifiConnectivityMonitor.CMD_RECOVERY_TO_HIGH_QUALITY_FROM_ELE) {
                return false;
            } else {
                WifiConnectivityMonitor wifiConnectivityMonitor3 = WifiConnectivityMonitor.this;
                wifiConnectivityMonitor3.sendMessage(wifiConnectivityMonitor3.obtainMessage(WifiConnectivityMonitor.CMD_TRAFFIC_POLL, WifiConnectivityMonitor.access$16604(wifiConnectivityMonitor3), 0));
                WifiConnectivityMonitor.this.mSnsBigDataSCNT.mElePG++;
                WifiConnectivityMonitor wifiConnectivityMonitor4 = WifiConnectivityMonitor.this;
                wifiConnectivityMonitor4.transitionTo(wifiConnectivityMonitor4.mLevel1State);
                return true;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public class ValidNoCheckState extends State {
        ValidNoCheckState() {
        }

        public void enter() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName());
            }
            WifiConnectivityMonitor.this.changeWifiIcon(1);
        }

        public void exit() {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, getName() + " exit\n");
            }
        }

        public boolean processMessage(Message msg) {
            WifiConnectivityMonitor.this.logStateAndMessage(msg, this);
            WifiConnectivityMonitor.this.setLogOnlyTransitions(false);
            switch (msg.what) {
                case WifiConnectivityMonitor.VALIDATED_DETECTED /*{ENCODED_INT: 135472}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "VALIDATED_DETECTED");
                    }
                    return true;
                case WifiConnectivityMonitor.INVALIDATED_DETECTED /*{ENCODED_INT: 135473}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "INVALIDATED_DETECTED");
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateSettings() {
        WifiInfo info;
        if (DBG) {
            Log.d(TAG, "Updating secure settings");
        }
        if (sWifiOnly) {
            Log.d(TAG, "Disabling poor network avoidance for wi-fi only device");
            this.mPoorNetworkDetectionEnabled = false;
        } else {
            updatePoorNetworkParameters();
            if (!isConnectedState()) {
                info = null;
            } else {
                info = syncGetCurrentWifiInfo();
            }
            loge("current state: " + getCurrentState());
            if (info != null && info.getNetworkId() != -1 && this.mPoorNetworkDetectionEnabled && isQCExceptionOnly()) {
                if (DBG) {
                    Log.e(TAG, "updatePoorNetworkDetection = false because it is an QCExceptionOnly");
                }
                this.mPoorNetworkDetectionEnabled = false;
            }
            if (!this.mPoorNetworkDetectionEnabled || !this.mUIEnabled) {
                sendMessage(EVENT_LINK_DETECTION_DISABLED);
            } else if (DBG) {
                this.mParam.setEvaluationParameters();
            }
        }
        if (!DBG) {
            return;
        }
        if (isAggressiveModeSupported()) {
            Log.d(TAG, "Updating secure settings - mPoorNetworkDetectionEnabled/mUIEnabled/mAggressiveModeEnabled : " + this.mPoorNetworkDetectionEnabled + "/" + this.mUIEnabled + "/" + this.mAggressiveModeEnabled + this.mParam.getAggressiveModeFeatureStatus());
            return;
        }
        Log.d(TAG, "Updating secure settings - mPoorNetworkDetectionEnabled/mUIEnabled : " + this.mPoorNetworkDetectionEnabled + "/" + this.mUIEnabled);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void determineMode() {
        int i;
        String ssid = this.mWifiInfo.getSSID();
        if (this.mCurrentMode != 0) {
            if (isIgnorableNetwork(ssid)) {
                setCurrentMode(0);
            } else if (!this.mPoorNetworkDetectionEnabled) {
                setCurrentMode(1);
            } else if (isQCExceptionOnly()) {
                if (SMARTCM_DBG) {
                    logi("isQCExceptionOnly");
                }
                setCurrentMode(1);
            } else if (isAggressiveModeEnabled()) {
                if (SMARTCM_DBG) {
                    logi("mAggressiveModeEnabled");
                }
                setCurrentMode(3);
            } else {
                setCurrentMode(2);
            }
        }
        String currentMode = null;
        int i2 = this.mCurrentMode;
        if (i2 == 2 || i2 == 3) {
            currentMode = "1";
        }
        boolean networkAvoidBadWifi = "1".equals(Settings.Global.getString(this.mContext.getContentResolver(), "network_avoid_bad_wifi"));
        if ((!networkAvoidBadWifi && ((i = this.mCurrentMode) == 2 || i == 3)) || (networkAvoidBadWifi && this.mCurrentMode == 1)) {
            Settings.Global.putString(this.mContext.getContentResolver(), "network_avoid_bad_wifi", currentMode);
        }
        if (DBG) {
            logi("current mode : " + this.mCurrentMode);
        }
    }

    private void setCurrentMode(int setMode) {
        this.mCurrentMode = setMode;
        int i = this.mCurrentMode;
        if ((i == 2 || i == 3) && isValidState() && getCurrentState() != this.mLevel2State) {
            this.mSamplingIntervalMS = LINK_MONITORING_SAMPLING_INTERVAL_MS;
            WifiEleStateTracker wifiEleStateTracker = this.mWifiEleStateTracker;
            if ((wifiEleStateTracker == null || !wifiEleStateTracker.getEleCheckEnabled()) && this.mIwcCurrentQai == 3) {
                this.mSamplingIntervalMS = LINK_SAMPLING_INTERVAL_MS;
            }
            this.mScoreSkipModeEnalbed = true;
        } else {
            this.mSamplingIntervalMS = LINK_SAMPLING_INTERVAL_MS;
            this.mScoreSkipModeEnalbed = false;
        }
        WifiEleStateTracker wifiEleStateTracker2 = this.mWifiEleStateTracker;
        if (wifiEleStateTracker2 == null) {
            return;
        }
        if (this.mCurrentMode == 3) {
            wifiEleStateTracker2.setEleAggTxBadDetection(true);
        } else {
            wifiEleStateTracker2.setEleAggTxBadDetection(false);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    /* JADX WARNING: Removed duplicated region for block: B:160:0x02c6  */
    /* JADX WARNING: Removed duplicated region for block: B:179:? A[RETURN, SYNTHETIC] */
    private void updatePoorNetworkParameters() {
        int simState;
        String str;
        int message;
        if (this.mTelephonyManager == null) {
            this.mTelephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
        }
        this.mMobilePolicyDataEnable = getCm().semIsMobilePolicyDataEnabled();
        boolean mPreviousUIEnabled = this.mUIEnabled;
        boolean mPreviousAggressiveModeEnabled = this.mAggressiveModeEnabled;
        boolean isMultiSim = false;
        TelephonyManager telephonyManager = this.mTelephonyManager;
        if (telephonyManager != null) {
            isMultiSim = telephonyManager.getPhoneCount() > 1;
        }
        if (this.mTelephonyManager == null) {
            simState = 0;
        } else if (TelephonyManager.getDefault().getPhoneCount() > 1 || isMultiSim) {
            int multiSimState = TelephonyUtil.semGetMultiSimState(this.mTelephonyManager);
            if (multiSimState == 1 || multiSimState == 2 || multiSimState == 3) {
                simState = 5;
            } else {
                simState = 0;
            }
            if (DBG) {
                Log.d(TAG, "multiSimState : " + multiSimState + "simState = " + simState);
            }
        } else {
            simState = this.mTelephonyManager.getSimState();
        }
        this.mUIEnabled = Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 0) != 0;
        if (isAggressiveModeSupported()) {
            this.mAggressiveModeEnabled = Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_aggressive_mode_on", 0) != 0;
            if (this.mAggressiveModeEnabled != mPreviousAggressiveModeEnabled) {
                if (SMARTCM_DBG) {
                    Context context = this.mContext;
                    Toast.makeText(context, "[@_ON] : " + this.mAggressiveModeEnabled, 0).show();
                }
                Objects.requireNonNull(this.mParam);
                if (isAggressiveModeEnabled(1)) {
                    updateTargetRssiForCurrentAP(true);
                }
            }
        }
        boolean mobile_data = Settings.Global.getInt(this.mContext.getContentResolver(), "mobile_data", 1) != 0;
        boolean isEnabledUltraSaving = false;
        if (Settings.System.getInt(this.mContext.getContentResolver(), "ultra_powersaving_mode", 0) == 1) {
            isEnabledUltraSaving = true;
        }
        this.mPoorNetworkDetectionEnabled = Settings.Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) == 0 && mobile_data && !this.mIsFmcNetwork && !isEnabledUltraSaving;
        if (isSimCheck()) {
            this.mPoorNetworkDetectionEnabled = this.mPoorNetworkDetectionEnabled && simState == 5 && this.mMobilePolicyDataEnable;
        }
        boolean snsDisabled = Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_wwsm_patch_remove_sns_menu_from_settings", 0) == 0;
        this.mPoorNetworkDetectionEnabled = this.mPoorNetworkDetectionEnabled && this.mUserOwner && !this.mImsRegistered && snsDisabled;
        this.mPoorNetworkDetectionEnabled = this.mPoorNetworkDetectionEnabled && this.mUIEnabled;
        this.mDataRoamingSetting = Settings.Global.getInt(this.mContext.getContentResolver(), "data_roaming", 0) == 1;
        boolean mobileDataBlockedByRoaming = this.mDataRoamingState;
        this.mPoorNetworkDetectionEnabled = this.mPoorNetworkDetectionEnabled && !mobileDataBlockedByRoaming;
        if (DBG || mobileDataBlockedByRoaming) {
            StringBuilder sb = new StringBuilder();
            sb.append("mDataRoamingState / !mDataRoamingSetting : ");
            sb.append(this.mDataRoamingState);
            sb.append("/");
            sb.append(!this.mDataRoamingSetting);
            sb.append(mobileDataBlockedByRoaming ? "     (Mobile data blocked by Data roaming condition)" : "");
            Log.d(TAG, sb.toString());
        }
        if (!mPreviousUIEnabled && this.mUIEnabled && this.mPoorNetworkDetectionEnabled) {
            mPoorNetworkAvoidanceEnabledTime = SystemClock.elapsedRealtime();
            Log.d(TAG, "SNS turned on. Do not start scan for a while.");
        }
        if (!this.mPoorNetworkDetectionEnabled) {
            this.mTransitionPendingByIwc = false;
        }
        if (this.mWifiSwitchForIndividualAppsService != null) {
            boolean z = this.mReportedPoorNetworkDetectionEnabled;
            boolean z2 = this.mPoorNetworkDetectionEnabled;
            if (z != z2) {
                this.mReportedPoorNetworkDetectionEnabled = z2;
                if (this.mReportedPoorNetworkDetectionEnabled) {
                    message = WifiSwitchForIndividualAppsService.SWITCH_TO_MOBILE_DATA_ENABLED;
                } else {
                    message = WifiSwitchForIndividualAppsService.SWITCH_TO_MOBILE_DATA_DISABLED;
                }
                this.mWifiSwitchForIndividualAppsService.sendEmptyMessage(message);
            }
            int message2 = this.mReportedQai;
            int i = this.mIwcCurrentQai;
            if (message2 != i) {
                this.mReportedQai = i;
                this.mWifiSwitchForIndividualAppsService.sendMessage(obtainMessage(WifiSwitchForIndividualAppsService.SWITCH_TO_MOBILE_DATA_QAI, this.mReportedQai));
            }
        }
        this.mAirPlaneMode = Settings.Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) != 0;
        StringBuilder sb2 = new StringBuilder();
        sb2.append(Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 0) != 0);
        sb2.append("/");
        sb2.append(!this.mIsFmcNetwork);
        this.mPoorNetworkAvoidanceSummary = sb2.toString();
        StringBuilder sb3 = new StringBuilder();
        sb3.append(!this.mAirPlaneMode);
        sb3.append("/");
        sb3.append(mobile_data);
        sb3.append("/");
        sb3.append(simState == 5);
        sb3.append("/");
        sb3.append(this.mMobilePolicyDataEnable);
        sb3.append("/");
        sb3.append(!this.mIsFmcNetwork);
        sb3.append("/");
        sb3.append(this.mUserOwner);
        sb3.append("/");
        sb3.append(!this.mImsRegistered);
        sb3.append("/");
        sb3.append(!isEnabledUltraSaving);
        sb3.append("/");
        sb3.append(!mobileDataBlockedByRoaming);
        sb3.append("/");
        sb3.append(snsDisabled);
        this.mPoorNetworkDetectionSummary = sb3.toString();
        if (!this.mAirPlaneMode && simState == 5 && mobile_data) {
            if ("CMCC".equals(CSC_VENDOR_NOTI_STYLE)) {
                if (DBG) {
                    Log.d(TAG, "CMCC Alert is support");
                }
                this.mCmccAlertSupport = true;
                if (!DBG) {
                    StringBuilder sb4 = new StringBuilder();
                    sb4.append("updatePoorNetworkAvoidance - Poor Network Test Enabled / !mIsFmcNetwork : ");
                    sb4.append(this.mPoorNetworkAvoidanceSummary);
                    sb4.append(" - mUIEnabled:");
                    String str2 = "disabled";
                    sb4.append(this.mUIEnabled ? "enabled" : str2);
                    Log.d(TAG, sb4.toString());
                    StringBuilder sb5 = new StringBuilder();
                    sb5.append("updatePoorNetworkDetection - Airplane Mode Off / Mobile Data Enabled / SIM State-Ready / MobilePolicyDataDisabled / !mIsFmcNetwork / mUserOwner / !mImsRegistered / isEnabledUltraSaving / !mobileDataBlockedByRoaming /snsDisabled: ");
                    sb5.append(this.mPoorNetworkDetectionSummary);
                    sb5.append(" - mPoorNetworkDetectionEnabled: ");
                    if (this.mPoorNetworkDetectionEnabled) {
                        str = "enabled";
                    } else {
                        str = str2;
                    }
                    sb5.append(str);
                    Log.d(TAG, sb5.toString());
                    if (isAggressiveModeSupported()) {
                        StringBuilder sb6 = new StringBuilder();
                        sb6.append("WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED: ");
                        boolean z3 = false;
                        if (Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_watchdog_poor_network_test_enabled", 0) != 0) {
                            z3 = true;
                        }
                        sb6.append(z3);
                        sb6.append(" - mAggressiveModeEnabled:");
                        if (this.mAggressiveModeEnabled) {
                            str2 = "enabled";
                        }
                        sb6.append(str2);
                        sb6.append(this.mParam.getAggressiveModeFeatureStatus());
                        Log.d(TAG, sb6.toString());
                    }
                    Log.d(TAG, "mIwcCurrentQai: " + this.mIwcCurrentQai);
                    return;
                }
                return;
            }
        }
        this.mCmccAlertSupport = false;
        if (!DBG) {
        }
    }

    public void resetWatchdogSettings() {
        sendMessage(EVENT_WATCHDOG_SETTINGS_CHANGE);
        requestInternetCheck(5);
    }

    private boolean isSimCheck() {
        if (!DBG || !SystemProperties.get("SimCheck.disable").equals("1")) {
            return true;
        }
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void setQcFailHistory(QcFailHistory history) {
        setQcFailHistory(history, null);
    }

    private synchronized void setQcFailHistory(QcFailHistory history, String dumpLog) {
        StringBuilder builder = new StringBuilder();
        if (!this.bSetQcResult) {
            if (this.mQcHistoryHead == -1) {
                this.mQcHistoryHead++;
            } else {
                this.mQcHistoryHead %= 30;
            }
            String currentTime = "" + (System.currentTimeMillis() / 1000);
            try {
                currentTime = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            } catch (RuntimeException e) {
            }
            try {
                builder.append(currentTime);
                builder.append(", " + getCurrentState().getName());
                if (dumpLog == null) {
                    builder.append(", [s]" + history.qcStep);
                    builder.append(", [t]" + history.qcTrigger);
                    builder.append(", [e]" + history.error);
                    builder.append(", [i]" + this.mInvalidationRssi);
                    builder.append(", [v]" + SNS_VERSION);
                    builder.append(", " + this.mCurrentRssi);
                    builder.append(", " + this.mCurrentLinkSpeed);
                    builder.append(", " + this.mCurrentBssid.mGoodLinkTargetRssi);
                    builder.append(", " + this.mPoorNetworkDetectionEnabled + "(" + this.mPoorNetworkDetectionSummary + ")");
                    builder.append(", " + this.mUIEnabled + "(" + this.mPoorNetworkAvoidanceSummary + ")");
                    if (isAggressiveModeSupported()) {
                        builder.append(", " + this.mAggressiveModeEnabled + this.mParam.getAggressiveModeFeatureStatus());
                    }
                    WifiInfo wifiInfo = syncGetCurrentWifiInfo();
                    String ssid = wifiInfo.getSSID();
                    String bssid = wifiInfo.getBSSID();
                    builder.append(", " + wifiInfo.getNetworkId());
                    String hexSsid = "";
                    for (int j = 0; j < ssid.length(); j++) {
                        hexSsid = hexSsid + Integer.toHexString(ssid.charAt(j));
                    }
                    builder.append(", " + hexSsid);
                    if (bssid.length() > 16) {
                        StringBuilder patchedBssid = new StringBuilder();
                        patchedBssid.append(bssid.charAt(0));
                        patchedBssid.append(bssid.charAt(1));
                        patchedBssid.append(bssid.charAt(12));
                        patchedBssid.append(bssid.charAt(13));
                        patchedBssid.append(bssid.charAt(15));
                        patchedBssid.append(bssid.charAt(16));
                        builder.append(", " + patchedBssid.toString());
                    }
                    builder.append(", " + history.line);
                    if (getCurrentState() == this.mValidSwitchableState) {
                        builder.append(", [ns]" + getNetworkStatHistory());
                    }
                } else {
                    builder.append(", " + dumpLog);
                }
            } catch (RuntimeException e2) {
                builder.append(currentTime + ", ex");
            }
            Log.i(TAG, builder.toString());
            this.mQcDumpHistory[this.mQcHistoryHead] = builder.toString();
            this.bSetQcResult = true;
            this.mQcHistoryHead++;
            this.mQcHistoryTotal++;
        }
    }

    private boolean isAggressiveModeEnabled() {
        int i = this.mIwcCurrentQai;
        if (i == 1) {
            return true;
        }
        if (i == 2 || i == 3) {
            return false;
        }
        if (OpBrandingLoader.Vendor.KTT == mOpBranding) {
            if (!isAggressiveModeSupported() || !this.mPoorNetworkDetectionEnabled || (!this.mAggressiveModeEnabled && !this.mMptcpEnabled)) {
                return false;
            }
            return true;
        } else if (!isAggressiveModeSupported() || !this.mPoorNetworkDetectionEnabled || !this.mAggressiveModeEnabled) {
            return false;
        } else {
            return true;
        }
    }

    private boolean isAggressiveModeEnabled(int feature) {
        return isAggressiveModeEnabled() && this.mParam.mAggressiveModeFeatureEnabled[feature];
    }

    private boolean isAggressiveModeSupported() {
        return true;
    }

    private boolean isQCExceptionOnly() {
        String ssid = syncGetCurrentWifiInfo().getSSID();
        int result = -1;
        if (isSpecificPackageOnScreen(this.mContext)) {
            result = 1;
        } else if (isSkipInternetCheck()) {
            result = 2;
        } else if ("\"gogoinflight\"".equals(ssid) || "\"Carnival-WiFi\"".equals(ssid) || "\"orange\"".equals(ssid) || "\"ChinaNet\"".equals(ssid)) {
            result = 3;
        } else if (this.m407ResponseReceived) {
            result = 4;
        } else if (this.mIsFmcNetwork) {
            result = 5;
        }
        if (result != -1) {
            this.isQCExceptionSummary = "" + result;
            StringBuilder sb = new StringBuilder();
            sb.append(DBG ? "isQCExceptionOnly - reason #" : "QCEO #");
            sb.append(result);
            logd(sb.toString());
            return true;
        }
        this.isQCExceptionSummary = "None";
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isSkipInternetCheck() {
        int networkId;
        WifiInfo wifiInfo = this.mWifiInfo;
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
    private int calculateSignalLevel(int rssi) {
        int signalLevel = WifiManager.calculateSignalLevel(rssi, 5);
        if (DBG) {
            Log.d(TAG, "RSSI current: " + this.mCurrentSignalLevel + " new: " + rssi + ", " + signalLevel);
        }
        return signalLevel;
    }

    private boolean isSpecificPackageOnScreen(Context context) {
        for (ActivityManager.RunningTaskInfo runningTaskInfo : ((ActivityManager) context.getSystemService("activity")).getRunningTasks(1)) {
            if (DBG) {
                Log.d(TAG, "isSpecificPackageOnScreen: top:" + runningTaskInfo.topActivity.getClassName());
            }
            if (runningTaskInfo.topActivity.getPackageName().equals("com.akazam.android.wlandialer")) {
                if (DBG) {
                    Log.i(TAG, " Specific Package(com.akazam.android.wlandialer) is on SCREEN! ");
                }
                return true;
            }
        }
        return false;
    }

    private boolean isSpecificPackageOnScreen(Context context, String specificPakageName) {
        if (specificPakageName == null || specificPakageName.isEmpty()) {
            return false;
        }
        for (ActivityManager.RunningTaskInfo runningTaskInfo : ((ActivityManager) context.getSystemService("activity")).getRunningTasks(1)) {
            if (DBG) {
                Log.d(TAG, "isSpecificPackageOnScreen: top:" + runningTaskInfo.topActivity.getClassName());
            }
            if (runningTaskInfo.topActivity.getClassName().equals(specificPakageName)) {
                if (DBG) {
                    Log.i(TAG, " Specific Package(" + specificPakageName + ") is on SCREEN! ");
                }
                return true;
            }
        }
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isValidState() {
        IState state = getCurrentState();
        if (state == this.mValidState || state == this.mValidNonSwitchableState || state == this.mValidSwitchableState || state == this.mValidNoCheckState || state == this.mLevel1State || state == this.mLevel2State) {
            if (!DBG) {
                return true;
            }
            Log.d(TAG, "In Valid state");
            return true;
        } else if (!DBG) {
            return false;
        } else {
            Log.d(TAG, "Not in Valid state");
            return false;
        }
    }

    private boolean isLevel1StateState() {
        if (getCurrentState() == this.mLevel1State) {
            if (!DBG) {
                return true;
            }
            Log.d(TAG, "In Level1State state");
            return true;
        } else if (!DBG) {
            return false;
        } else {
            Log.d(TAG, "Not in Level1State state");
            return false;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isInvalidState() {
        if (getCurrentState() == this.mInvalidState) {
            if (!DBG) {
                return true;
            }
            Log.d(TAG, "In Invalid state");
            return true;
        } else if (!DBG) {
            return false;
        } else {
            Log.d(TAG, "Not in Invalid state");
            return false;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateCurrentBssid(String bssid, int netId) {
        if (DBG) {
            StringBuilder sb = new StringBuilder();
            sb.append("Update current BSSID to ");
            sb.append(bssid != null ? bssid : "null");
            Log.d(TAG, sb.toString());
        }
        synchronized (mCurrentBssidLock) {
            if (bssid == null) {
                this.mCurrentBssid = this.mEmptyBssid;
                if (DBG) {
                    Log.d(TAG, "BSSID changed");
                }
                this.mValidNonSwitchableState.mMinQualifiedRssi = 0;
                sendMessage(EVENT_BSSID_CHANGE);
            } else if (this.mCurrentBssid == null || !bssid.equals(this.mCurrentBssid.mBssid)) {
                BssidStatistics currentBssid = this.mBssidCache.get(bssid);
                if (currentBssid == null) {
                    currentBssid = new BssidStatistics(bssid, netId);
                    this.mBssidCache.put(bssid, currentBssid);
                }
                this.mCurrentBssid = currentBssid;
                this.mCurrentBssid.initOnConnect();
                updateOpenNetworkQosScoreSummary();
                if (DBG) {
                    Log.d(TAG, "BSSID changed");
                }
                this.mValidNonSwitchableState.mMinQualifiedRssi = 0;
                sendMessage(EVENT_BSSID_CHANGE);
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void requestInternetCheck(int trigger) {
        requestInternetCheck(this.mInvalidationFailHistory.qcStepTemp, trigger);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void requestInternetCheck(int step, int trigger) {
        WifiEleStateTracker wifiEleStateTracker = this.mWifiEleStateTracker;
        if (wifiEleStateTracker != null && wifiEleStateTracker.checkEleValidBlockState()) {
            Log.d(TAG, "REPORT_NETWORK_CONNECTIVITY ignored by ele block");
        } else if (this.mIsInRoamSession || this.mIsInDhcpSession) {
            Log.d(TAG, "REPORT_NETWORK_CONNECTIVITY ignored In Roam Session");
        } else if (syncGetCurrentWifiInfo().getRssi() >= -55 || !this.mIsRoamingNetwork) {
            sendMessage(REPORT_NETWORK_CONNECTIVITY, step, trigger);
        } else {
            Log.d(TAG, "REPORT_NETWORK_CONNECTIVITY ignored by possible roaming");
        }
    }

    /* access modifiers changed from: private */
    public class NetworkStatsAnalyzer extends Handler {
        private static final int ACTIVITY_POLLING_INTERVAL = 1000;
        private static final int BACKHAUL_DETECTION_REASON_DNS_CHECK_INTERVAL = 16;
        private static final int BACKHAUL_DETECTION_REASON_IN_ERROR_SEG_FAIL = 4;
        private static final int BACKHAUL_DETECTION_REASON_NO_RESPONSE_FAIL = 8;
        private static final int BACKHAUL_DETECTION_REASON_RETRANS_SEG_FAIL = 2;
        private static final int BACKHAUL_DETECTION_REASON_TCP_SOCKET_FAIL = 1;
        private static final int GOOD_RX_VALID_DURATION = 300000;
        private static final int LEAST_AGGRESSIVE_MODE_HIGH_THRESHOLD_INTERVAL = 300000;
        private static final int LEAST_AGGRESSIVE_MODE_QC_INTERVAL = 20000;
        private static final int MAX_OPTION_TARGET_RSSI_DELTA = 5;
        private static final int NETSTATS_INIT_DELAY_TIME = 20000;
        private static final int OPTION_RSSI_INCREMENT_INTERVAL = 60000;
        public static final int QC_PASS_INCREASE_WAITINGCYCLE = 3;
        private static final int RSSI_AVERAGE_WINDOW_SIZE = 3;
        private static final int RSSI_THRESHOLD_LOW_HIGH_DELTA = 3;
        private static final String TAG = "WifiConnectivityMonitor.NetworkStatsAnalyzer";
        private final String FILE_NAME_SNMP = "/proc/net/snmp";
        private final String FILE_NAME_SOCKSTAT_IPV4 = "/proc/net/sockstat";
        private final String FILE_NAME_SOCKSTAT_IPV6 = "/proc/net/sockstat6";
        private final int RSSI_LOW_SIGNAL_THRESHOLD = -70;
        private final int RSSI_POOR_SIGNAL_THRESHOLD = -83;
        private final int RSSI_QC_TRIGGER_INTERVAL = -3;
        private final int SNMP_TCP_COUNT_ROW = 6;
        private final int SNMP_TCP_ESTABLISH_COUNT_COLOUMN = 9;
        private final int SNMP_TCP_IN_SEG_COUNT_COLOUMN = 10;
        private final int SNMP_TCP_IN_SEG_ERROR_COUNT_COLOUMN = 13;
        private final int SNMP_TCP_OUT_SEG_COUNT_COLOUMN = 11;
        private final int SNMP_TCP_RETRANS_SEG_COUNT_COLOUMN = 14;
        private final int SOCKSTAT6_SOCK_COUNT_ROW = 1;
        private final int SOCKSTAT6_TCP_INUSE_COUNT_COLOUMN = 2;
        private final int SOCKSTAT_ORPHAN_COUNT_COLOUMN = 4;
        private final int SOCKSTAT_TCP_COUNT_ROW = 2;
        private final int SOCKSTAT_TCP_INUSE_COUNT_COLOUMN = 2;
        private final int SOCKSTAT_TIMEWAIT_COUNT_COLOUMN = 6;
        private final int TCP_POOR_SEG_RX = 0;
        private final int THRESHOLD_BACKHAUL_CONNECTIVITY_CHECK_HIGH = 5;
        private final int THRESHOLD_BACKHAUL_CONNECTIVITY_CHECK_LOW = 2;
        private final int THRESHOLD_BACKHAUL_CONNECTIVITY_CHECK_POOR = 2;
        private final int THRESHOLD_MAX_WAITING_CYCLE = 60;
        private final int THRESHOLD_TCP_POOR_SEG_RX_TX = 15;
        private final int THRESHOLD_WAITING_CYCLE_CHECK_HIGH = 5;
        private final int THRESHOLD_WAITING_CYCLE_CHECK_LOW = 3;
        private final int THRESHOLD_WAITING_CYCLE_CHECK_POOR = 2;
        private final int THRESHOLD_ZERO = 0;
        private final int TIME_QC_TRIGGER_INTERVAL = 10000;
        long lastPollTime = 0;
        int mBackhaulDetectionReason = 0;
        private long mBackhaulLastDnsCheckTime = 0;
        private int mBackhaulQcGoodRssi = 0;
        private long mBackhaulQcResultTime = 0;
        private int mBackhaulQcTriggeredRssi = 0;
        private ArrayList<Integer> mCumulativePoorRx = new ArrayList<>();
        private int mCurrentRxStats = 0;
        private boolean mDnsInterrupted = false;
        private boolean mDnsQueried = false;
        private int mGoodRxRate = 0;
        private int mGoodRxRssi = -200;
        private long mGoodRxTime = 0;
        int mInErrorSegWaitingCycle = 0;
        int mInternetConnectivityCounter = 0;
        int mInternetConnectivityWaitingCylce = 0;
        private long mLastDnsCheckTime = 0;
        private long mLastNeedCheckByPoorRxTime = 0;
        private int mLastRssi = 0;
        private int mMaybeUseStreaming = 0;
        private int mNsaQcStep = 0;
        private int mNsaQcTrigger = 0;
        private boolean mPollingStarted = false;
        int mPrevInSegCount = 0;
        int mPrevInSegErrorCount = 0;
        int mPrevOutSegCount = 0;
        private boolean mPrevQcPassed = false;
        int mPrevRetranSegCount = 0;
        int mPrevTcpEstablishedCount = 0;
        int mPrevTcpInUseCount = 0;
        int mPrevTimeWaitCount = 0;
        long mPreviousRx = 0;
        long mPreviousTx = 0;
        private boolean mPublicDnsCheckProcess = false;
        int mRetransSegWaitingCycle = 0;
        private int[] mRssiAverageWindow = new int[3];
        private int mRssiIndex = 0;
        private long mRxBytes = 0;
        private ArrayList<Integer> mRxHistory = new ArrayList<>();
        private long mRxPackets = 0;
        private boolean mSYNPacketOnly = false;
        private boolean mSkipRemainingDnsResults = false;
        private int mStayingLowMCS = 0;
        private long mTxBytes = 0;
        private long mTxPackets = 0;

        private boolean isBackhaulDetectionEnabled() {
            if (WifiConnectivityMonitor.this.mCurrentMode == 2 || WifiConnectivityMonitor.this.mCurrentMode == 3) {
                return true;
            }
            return false;
        }

        public NetworkStatsAnalyzer(Looper looper) {
            super(looper);
            sendEmptyMessageDelayed(WifiConnectivityMonitor.CMD_DELAY_NETSTATS_SESSION_INIT, 20000);
        }

        /* access modifiers changed from: package-private */
        public void checkPublicDns() {
            if (WifiConnectivityMonitor.this.inChinaNetwork()) {
                this.mPublicDnsCheckProcess = false;
                return;
            }
            this.mPublicDnsCheckProcess = true;
            this.mNsaQcStep = 1;
            WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
            String str = wifiConnectivityMonitor.mParam.DEFAULT_URL_STRING;
            Objects.requireNonNull(WifiConnectivityMonitor.this.mParam);
            DnsThread mDnsThread = new DnsThread(true, str, this, 10000);
            mDnsThread.start();
            WifiConnectivityMonitor.this.mDnsThreadID = mDnsThread.getId();
            if (WifiConnectivityMonitor.DBG) {
                Log.d(TAG, "wait publicDnsThread results [" + WifiConnectivityMonitor.this.mDnsThreadID + "]");
            }
        }

        /* access modifiers changed from: package-private */
        public void setGoodRxStateNow(long now) {
            if (now == 0) {
                this.mGoodRxTime = 0;
                this.mGoodRxRssi = -200;
                this.mGoodRxRate = 0;
                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                    Log.w(TAG, "lose Good Rx status.");
                    return;
                }
                return;
            }
            WifiInfo info = WifiConnectivityMonitor.this.syncGetCurrentWifiInfo();
            int i = this.mGoodRxRate;
            if (i == 0 || now - this.mGoodRxTime >= 300000 || i > info.getLinkSpeed() || (this.mGoodRxRate == info.getLinkSpeed() && this.mGoodRxRssi > info.getRssi())) {
                this.mGoodRxRssi = info.getRssi();
                this.mGoodRxRate = info.getLinkSpeed();
            }
            this.mGoodRxTime = now;
            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                Log.i(TAG, String.format("obtain Good Rx status [rssi : %ddbm, rate : %dMbps]", Integer.valueOf(this.mGoodRxRssi), Integer.valueOf(this.mGoodRxRate)));
            }
        }

        /* JADX INFO: Multiple debug info for r3v30 long: [D('preTxBytes' long), D('preTxPkts' long)] */
        /* JADX INFO: Multiple debug info for r10v10 'diffTxBytes'  long: [D('diffTxBytes' long), D('now' long)] */
        /* JADX INFO: Multiple debug info for r4v76 long: [D('diffRxCurr' long), D('readerSockstat6' java.io.FileReader)] */
        /* JADX INFO: Multiple debug info for r12v55 long: [D('wifiInfo' android.net.wifi.WifiInfo), D('diffTxCurr' long)] */
        /* JADX WARNING: Code restructure failed: missing block: B:415:0x0ba6, code lost:
            if (r11 < ((long) (r13.mParam.mPassBytesAggressiveMode * 2))) goto L_0x0bad;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:626:0x0fca, code lost:
            if (r45 < ((long) 7420)) goto L_0x0fcc;
         */
        /* JADX WARNING: Removed duplicated region for block: B:18:0x006e  */
        /* JADX WARNING: Removed duplicated region for block: B:22:0x0096  */
        /* JADX WARNING: Removed duplicated region for block: B:265:0x06f7 A[SYNTHETIC, Splitter:B:265:0x06f7] */
        /* JADX WARNING: Removed duplicated region for block: B:270:0x06ff A[Catch:{ IOException -> 0x06fb }] */
        /* JADX WARNING: Removed duplicated region for block: B:272:0x0704 A[Catch:{ IOException -> 0x06fb }] */
        /* JADX WARNING: Removed duplicated region for block: B:274:0x0709 A[Catch:{ IOException -> 0x06fb }] */
        /* JADX WARNING: Removed duplicated region for block: B:276:0x070e A[Catch:{ IOException -> 0x06fb }] */
        /* JADX WARNING: Removed duplicated region for block: B:278:0x0713 A[Catch:{ IOException -> 0x06fb }] */
        /* JADX WARNING: Removed duplicated region for block: B:280:0x0718 A[Catch:{ IOException -> 0x06fb }] */
        /* JADX WARNING: Removed duplicated region for block: B:285:0x081c  */
        /* JADX WARNING: Removed duplicated region for block: B:291:0x0860  */
        /* JADX WARNING: Removed duplicated region for block: B:292:0x0863  */
        /* JADX WARNING: Removed duplicated region for block: B:297:0x088f A[SYNTHETIC, Splitter:B:297:0x088f] */
        /* JADX WARNING: Removed duplicated region for block: B:302:0x0897 A[Catch:{ IOException -> 0x0893 }] */
        /* JADX WARNING: Removed duplicated region for block: B:304:0x089c A[Catch:{ IOException -> 0x0893 }] */
        /* JADX WARNING: Removed duplicated region for block: B:306:0x08a1 A[Catch:{ IOException -> 0x0893 }] */
        /* JADX WARNING: Removed duplicated region for block: B:308:0x08a6 A[Catch:{ IOException -> 0x0893 }] */
        /* JADX WARNING: Removed duplicated region for block: B:310:0x08ab A[Catch:{ IOException -> 0x0893 }] */
        /* JADX WARNING: Removed duplicated region for block: B:312:0x08b0 A[Catch:{ IOException -> 0x0893 }] */
        /* JADX WARNING: Removed duplicated region for block: B:347:0x09ad  */
        /* JADX WARNING: Removed duplicated region for block: B:350:0x09c7  */
        /* JADX WARNING: Removed duplicated region for block: B:353:0x09e7  */
        /* JADX WARNING: Removed duplicated region for block: B:354:0x09ef  */
        /* JADX WARNING: Removed duplicated region for block: B:378:0x0a64  */
        /* JADX WARNING: Removed duplicated region for block: B:381:0x0a7e  */
        /* JADX WARNING: Removed duplicated region for block: B:384:0x0ab8  */
        /* JADX WARNING: Removed duplicated region for block: B:387:0x0ad2  */
        /* JADX WARNING: Removed duplicated region for block: B:390:0x0aec  */
        /* JADX WARNING: Removed duplicated region for block: B:391:0x0af5  */
        /* JADX WARNING: Removed duplicated region for block: B:394:0x0afe  */
        /* JADX WARNING: Removed duplicated region for block: B:395:0x0b01  */
        /* JADX WARNING: Removed duplicated region for block: B:398:0x0b0a  */
        /* JADX WARNING: Removed duplicated region for block: B:401:0x0b24  */
        /* JADX WARNING: Removed duplicated region for block: B:404:0x0b40  */
        /* JADX WARNING: Removed duplicated region for block: B:407:0x0b68  */
        /* JADX WARNING: Removed duplicated region for block: B:410:0x0b84  */
        /* JADX WARNING: Removed duplicated region for block: B:411:0x0b86  */
        /* JADX WARNING: Removed duplicated region for block: B:414:0x0b95  */
        /* JADX WARNING: Removed duplicated region for block: B:416:0x0ba9  */
        /* JADX WARNING: Removed duplicated region for block: B:420:0x0bbe  */
        /* JADX WARNING: Removed duplicated region for block: B:423:0x0bc8  */
        /* JADX WARNING: Removed duplicated region for block: B:431:0x0bfc  */
        /* JADX WARNING: Removed duplicated region for block: B:434:0x0c1a  */
        /* JADX WARNING: Removed duplicated region for block: B:533:0x0e18  */
        /* JADX WARNING: Removed duplicated region for block: B:643:0x1007  */
        /* JADX WARNING: Removed duplicated region for block: B:645:0x100e  */
        /* JADX WARNING: Removed duplicated region for block: B:652:0x1032  */
        /* JADX WARNING: Removed duplicated region for block: B:655:0x103f  */
        /* JADX WARNING: Removed duplicated region for block: B:668:0x10f7  */
        /* JADX WARNING: Removed duplicated region for block: B:674:0x1146  */
        /* JADX WARNING: Removed duplicated region for block: B:704:0x11e3  */
        /* JADX WARNING: Removed duplicated region for block: B:707:0x11ed  */
        /* JADX WARNING: Removed duplicated region for block: B:708:0x121b  */
        public void handleMessage(Message msg) {
            Message message;
            int rssi;
            boolean isEvaluationLevel;
            boolean isEvaluationLevel2;
            long diffRxBytes;
            boolean isStreaming;
            long diffTxBytes;
            boolean isStreaming2;
            boolean z;
            long diffTxBytes2;
            long diffRxBytes2;
            BssidStatistics currentBssid;
            long j;
            long now;
            int mrssi;
            BssidStatistics currentBssid2;
            long now2;
            String dnsTargetUrl;
            boolean needCheckByPoorRx;
            boolean needCheckByPoorRx2;
            boolean needCheckByNoRx;
            int txProportionToRx;
            int i;
            BufferedReader bufferSockstat4;
            int internetConnectivityCounterThreshold;
            int currRssi;
            int waitingCycleThreshold;
            FileReader readerSockstat;
            String str;
            BufferedReader bufferSNMP;
            FileReader readerSockstat6;
            Throwable th;
            long diffTxCurr;
            long diffRxCurr;
            long rxCurr;
            long now3;
            int outSegCount;
            int currTcpEstablishedCount;
            int retransSegCount;
            int inSegErrorCount;
            int inSegCount;
            int diffInSegErrorCount;
            int tcpInUseCount;
            int diffRetransSegCount;
            boolean checkBackhaul;
            int diffInSegCount;
            int diffOutSegCount;
            int timeWaitCount;
            FileReader readerSockstat62;
            FileReader readerSockstat2;
            int currTcpEstablishedCount2;
            int lineNumber;
            int i2;
            int timeWaitCount2;
            long now4 = SystemClock.elapsedRealtime();
            WifiInfo wifiInfo = WifiConnectivityMonitor.this.syncGetCurrentWifiInfo();
            int i3 = msg.what;
            switch (i3) {
                case WifiConnectivityMonitor.RESULT_DNS_CHECK /*{ENCODED_INT: 135202}*/:
                    if (WifiConnectivityMonitor.DBG) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("[RESULT_DNS_CHECK] : ");
                        message = msg;
                        sb.append(message.arg1);
                        sb.append(", from DnsThread id(");
                        sb.append(WifiConnectivityMonitor.this.mDnsThreadID);
                        sb.append(")");
                        Log.i(TAG, sb.toString());
                    } else {
                        message = msg;
                    }
                    int mDnsResult = WifiConnectivityMonitor.this.checkDnsThreadResult(message.arg1, message.arg2);
                    this.mDnsQueried = false;
                    if (this.mDnsInterrupted) {
                        this.mDnsInterrupted = false;
                        if (WifiConnectivityMonitor.DBG) {
                            Log.d(TAG, "Result: " + mDnsResult + " - This DNS query is interrupted.");
                        }
                    } else if (WifiConnectivityMonitor.this.mIsInDhcpSession || WifiConnectivityMonitor.this.mIsScanning || WifiConnectivityMonitor.this.mIsInRoamSession) {
                        if (WifiConnectivityMonitor.DBG) {
                            Log.d(TAG, "Result: " + mDnsResult + " - This DNS query is interrupted by DHCP session or Scanning.");
                        }
                    } else if (mDnsResult != 0) {
                        if (WifiConnectivityMonitor.DBG) {
                            Log.e(TAG, "single DNS Checking FAILURE");
                        }
                        if (WifiConnectivityMonitor.this.mCurrentMode != 3 || !WifiConnectivityMonitor.this.mInAggGoodStateNow) {
                            if (isBackhaulDetectionEnabled()) {
                                this.mBackhaulQcTriggeredRssi = wifiInfo.getRssi();
                                this.mNsaQcTrigger = this.mBackhaulDetectionReason + 128;
                            } else {
                                this.mNsaQcTrigger = 45;
                            }
                            if (!WifiConnectivityMonitor.this.isInvalidState()) {
                                WifiConnectivityMonitor.this.requestInternetCheck(this.mNsaQcStep, this.mNsaQcTrigger);
                            }
                        } else {
                            if (WifiConnectivityMonitor.DBG) {
                                Log.e(TAG, "But, do not check the quality in AGG good rx state");
                            }
                            this.mSkipRemainingDnsResults = true;
                        }
                    }
                    this.mPublicDnsCheckProcess = false;
                    return;
                case WifiConnectivityMonitor.REPORT_QC_RESULT /*{ENCODED_INT: 135203}*/:
                    return;
                default:
                    switch (i3) {
                        case WifiConnectivityMonitor.ACTIVITY_CHECK_START /*{ENCODED_INT: 135219}*/:
                            if (!this.mPollingStarted) {
                                if (!WifiConnectivityMonitor.this.isMobileHotspot()) {
                                    if (isBackhaulDetectionEnabled()) {
                                        sendEmptyMessage(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                    }
                                    sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL);
                                    WifiConnectivityMonitor.this.initNetworkStatHistory();
                                    this.mLastRssi = wifiInfo.getRssi();
                                    this.mPollingStarted = true;
                                    return;
                                }
                                return;
                            }
                            return;
                        case WifiConnectivityMonitor.ACTIVITY_CHECK_STOP /*{ENCODED_INT: 135220}*/:
                            removeMessages(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL);
                            removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                            this.mPollingStarted = false;
                            this.mPublicDnsCheckProcess = false;
                            setGoodRxStateNow(0);
                            this.mCumulativePoorRx.clear();
                            this.mRxHistory.clear();
                            this.mDnsQueried = false;
                            this.mDnsInterrupted = false;
                            this.mMaybeUseStreaming = 0;
                            this.mSYNPacketOnly = false;
                            this.mStayingLowMCS = 0;
                            this.mNsaQcStep = 0;
                            this.mNsaQcTrigger = 0;
                            this.lastPollTime = 0;
                            return;
                        case WifiConnectivityMonitor.ACTIVITY_CHECK_POLL /*{ENCODED_INT: 135221}*/:
                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                Log.i(TAG, "mPollingStarted : " + this.mPollingStarted);
                            }
                            if (this.mPollingStarted) {
                                BssidStatistics currentBssid3 = WifiConnectivityMonitor.this.mCurrentBssid;
                                if (currentBssid3 != null) {
                                    if (currentBssid3 != WifiConnectivityMonitor.this.mEmptyBssid) {
                                        WifiConnectivityMonitor.this.mIWCChannel.sendMessage((int) WifiConnectivityMonitor.CMD_IWC_ACTIVITY_CHECK_POLL);
                                        int rssi2 = wifiInfo.getRssi();
                                        if (rssi2 < -90) {
                                            if (!WifiConnectivityMonitor.this.mClientModeImpl.isConnected()) {
                                                Log.i(TAG, "already disconnected : " + rssi2);
                                                removeMessages(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL);
                                                sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_STOP);
                                                return;
                                            } else if (rssi2 < -95) {
                                                if (rssi2 != -127) {
                                                    rssi = -95;
                                                    int mrssi2 = (this.mLastRssi + rssi) / 2;
                                                    this.mLastRssi = rssi;
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                        Log.d(TAG, "rssi : " + mrssi2);
                                                    }
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                        Log.d(TAG, "linkspeed : " + wifiInfo.getLinkSpeed());
                                                    }
                                                    isEvaluationLevel = false;
                                                    if (wifiInfo.getLinkSpeed() > 6) {
                                                        this.mStayingLowMCS++;
                                                        isEvaluationLevel = true;
                                                    } else {
                                                        this.mStayingLowMCS = 0;
                                                    }
                                                    if ((this.mLastRssi < -75 || WifiConnectivityMonitor.this.mCurrentMode != 2) && (this.mLastRssi >= -83 || WifiConnectivityMonitor.this.mCurrentMode != 1)) {
                                                        isEvaluationLevel2 = isEvaluationLevel;
                                                    } else {
                                                        isEvaluationLevel2 = true;
                                                    }
                                                    if (this.mGoodRxRate > 0 && (now4 - this.mGoodRxTime >= 300000 || wifiInfo.getLinkSpeed() < this.mGoodRxRate || (wifiInfo.getLinkSpeed() == this.mGoodRxRate && wifiInfo.getRssi() < this.mGoodRxRssi - 5))) {
                                                        setGoodRxStateNow(0);
                                                    }
                                                    long preTxPkts = this.mTxPackets;
                                                    long preRxPkts = this.mRxPackets;
                                                    this.mTxPackets = TrafficStats.getTxPackets(WifiConnectivityMonitor.INTERFACENAMEOFWLAN);
                                                    this.mRxPackets = TrafficStats.getRxPackets(WifiConnectivityMonitor.INTERFACENAMEOFWLAN);
                                                    long diffRx = this.mRxPackets - preRxPkts;
                                                    long diffTx = this.mTxPackets - preTxPkts;
                                                    this.mCurrentRxStats = (int) diffRx;
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                        Log.d(TAG, "diffRx : " + diffRx);
                                                    }
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                        Log.d(TAG, "diffTx : " + diffTx);
                                                    }
                                                    long preTxPkts2 = this.mTxBytes;
                                                    long preRxbyte = this.mRxBytes;
                                                    this.mTxBytes = TrafficStats.getTxBytes(WifiConnectivityMonitor.INTERFACENAMEOFWLAN);
                                                    this.mRxBytes = TrafficStats.getRxBytes(WifiConnectivityMonitor.INTERFACENAMEOFWLAN);
                                                    diffRxBytes = this.mRxBytes - preRxbyte;
                                                    long diffTxBytes3 = this.mTxBytes - preTxPkts2;
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                        Log.d(TAG, "diffRxBytes : " + diffRxBytes);
                                                    }
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                        Log.d(TAG, "diffTxBytes : " + diffTxBytes3);
                                                    }
                                                    int rxBytesPerPacket = (int) (diffRx <= 0 ? diffRxBytes / diffRx : 0);
                                                    int txBytesPerPacket = (int) (diffTx <= 0 ? diffTxBytes3 / diffTx : 0);
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                        Log.d(TAG, "rxBytesPerPacket : " + rxBytesPerPacket);
                                                    }
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                        Log.d(TAG, "txBytesPerPacket : " + txBytesPerPacket);
                                                    }
                                                    if (WifiConnectivityMonitor.this.mNetworkStatHistoryUpdate) {
                                                        WifiConnectivityMonitor.this.addNetworkStatHistory(diffRx + "," + diffTx);
                                                    }
                                                    boolean needCheckInternetIsAlive = false;
                                                    boolean needCheckByNoRx2 = false;
                                                    if (this.mRxHistory.size() == 6) {
                                                        this.mRxHistory.remove(0);
                                                        this.mRxHistory.trimToSize();
                                                    }
                                                    boolean needCheckByPoorRx3 = false;
                                                    this.mRxHistory.add(new Integer((int) diffRx));
                                                    isStreaming = rxBytesPerPacket <= 1430;
                                                    currentBssid3.updateMaxThroughput(mrssi2, diffRxBytes, isStreaming);
                                                    WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
                                                    if (isStreaming) {
                                                        isStreaming2 = isStreaming;
                                                        diffTxBytes = diffTxBytes3;
                                                        break;
                                                    } else {
                                                        isStreaming2 = isStreaming;
                                                        diffTxBytes = diffTxBytes3;
                                                    }
                                                    if (diffRxBytes < ((long) (WifiConnectivityMonitor.this.mParam.mPassBytesAggressiveMode * 20))) {
                                                        z = false;
                                                        wifiConnectivityMonitor.mInAggGoodStateNow = z;
                                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                            Log.d(TAG, "mInAggGoodStateNow : " + WifiConnectivityMonitor.this.mInAggGoodStateNow);
                                                        }
                                                        if (WifiConnectivityMonitor.this.mCurrentMode == 3 && WifiConnectivityMonitor.this.mInAggGoodStateNow) {
                                                            currentBssid3.updateGoodRssi(mrssi2);
                                                        }
                                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                            Log.d(TAG, "mMaybeUseStreaming : " + this.mMaybeUseStreaming);
                                                        }
                                                        if (!WifiConnectivityMonitor.this.mIsScanning || WifiConnectivityMonitor.this.mIsInRoamSession || WifiConnectivityMonitor.this.mIsInDhcpSession) {
                                                            mrssi = mrssi2;
                                                            currentBssid = currentBssid3;
                                                            diffRxBytes2 = diffRxBytes;
                                                            now = now4;
                                                            diffTxBytes2 = diffTxBytes;
                                                            j = 1000;
                                                        } else if (!WifiConnectivityMonitor.this.mIsScreenOn) {
                                                            mrssi = mrssi2;
                                                            currentBssid = currentBssid3;
                                                            diffRxBytes2 = diffRxBytes;
                                                            now = now4;
                                                            diffTxBytes2 = diffTxBytes;
                                                            j = 1000;
                                                        } else if (this.mPublicDnsCheckProcess || WifiConnectivityMonitor.this.mCurrentMode == 0) {
                                                            mrssi = mrssi2;
                                                            currentBssid = currentBssid3;
                                                            diffRxBytes2 = diffRxBytes;
                                                            now = now4;
                                                            diffTxBytes2 = diffTxBytes;
                                                            j = 1000;
                                                            removeMessages(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL);
                                                            sendEmptyMessageDelayed(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL, j);
                                                            if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                                                                if (mrssi < -99) {
                                                                    mrssi = -99;
                                                                }
                                                                int mrssi3 = mrssi > 0 ? 0 : mrssi;
                                                                if (mrssi3 > currentBssid.mLastPoorRssi) {
                                                                    WifiConnectivityMonitor.this.mStayingPoorRssi = 0;
                                                                } else if (diffRxBytes2 >= ((long) (WifiConnectivityMonitor.this.mParam.mPassBytesAggressiveMode / 2)) || diffTxBytes2 >= ((long) (WifiConnectivityMonitor.this.mParam.mPassBytesAggressiveMode / 2)) || currentBssid.mMaxThroughput[-mrssi3] >= ((long) (WifiConnectivityMonitor.this.mParam.mPassBytesAggressiveMode / 2)) || ((!wifiInfo.is24GHz() || mrssi3 > -75) && mrssi3 > -80)) {
                                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                        Log.d(TAG, "reset poor rssi");
                                                                    }
                                                                    WifiConnectivityMonitor.this.mStayingPoorRssi = 0;
                                                                    BssidStatistics.access$20720(currentBssid, 5);
                                                                } else {
                                                                    WifiConnectivityMonitor.access$10208(WifiConnectivityMonitor.this);
                                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                        Log.d(TAG, "mStayingPoorRssi : " + WifiConnectivityMonitor.this.mStayingPoorRssi);
                                                                    }
                                                                }
                                                            }
                                                            if (this.lastPollTime != 0) {
                                                                Message message2 = Message.obtain();
                                                                Bundle args = new Bundle();
                                                                args.putLong("timeDelta", now - this.lastPollTime);
                                                                args.putLong("diffTxBytes", diffTxBytes2);
                                                                args.putLong("diffRxBytes", diffRxBytes2);
                                                                message2.what = WifiConnectivityMonitor.CMD_UPDATE_CURRENT_BSSID_ON_THROUGHPUT_UPDATE;
                                                                message2.setData(args);
                                                                WifiConnectivityMonitor.this.sendMessageToWcmStateMachine(message2);
                                                            }
                                                            this.lastPollTime = now;
                                                            return;
                                                        } else {
                                                            if (this.mDnsQueried) {
                                                                StringBuilder sb2 = new StringBuilder();
                                                                sb2.append(diffRx);
                                                                sb2.append(" ");
                                                                sb2.append(diffTx);
                                                                sb2.append(" ");
                                                                sb2.append(diffRxBytes);
                                                                sb2.append(" ");
                                                                diffRxBytes2 = diffRxBytes;
                                                                sb2.append(diffTxBytes);
                                                                sb2.append(" ");
                                                                sb2.append(rxBytesPerPacket);
                                                                sb2.append(" ");
                                                                sb2.append(txBytesPerPacket);
                                                                Log.d(TAG, sb2.toString());
                                                                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                    Log.i(TAG, "waiting dns responses or the quality result now!");
                                                                }
                                                                boolean stopQC = false;
                                                                if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                                                                    if (WifiConnectivityMonitor.this.mInAggGoodStateNow) {
                                                                        stopQC = true;
                                                                    }
                                                                } else if (diffRx >= ((long) WifiConnectivityMonitor.this.mParam.mGoodRxPacketsBase) && rxBytesPerPacket > 500) {
                                                                    stopQC = true;
                                                                } else if (diffTxBytes >= 100000) {
                                                                    stopQC = true;
                                                                }
                                                                if (stopQC) {
                                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                        Log.i(TAG, "Good Rx!, don't need to keep evaluating quality!");
                                                                    }
                                                                    if (this.mDnsQueried) {
                                                                        this.mSkipRemainingDnsResults = true;
                                                                        this.mDnsQueried = false;
                                                                        this.mDnsInterrupted = false;
                                                                    }
                                                                }
                                                                mrssi = mrssi2;
                                                                currentBssid = currentBssid3;
                                                                diffTxBytes2 = diffTxBytes;
                                                                now = now4;
                                                                j = 1000;
                                                            } else {
                                                                mrssi = mrssi2;
                                                                diffRxBytes2 = diffRxBytes;
                                                                if (WifiConnectivityMonitor.this.mCurrentMode != 3 || !WifiConnectivityMonitor.this.mInAggGoodStateNow) {
                                                                    if (diffRx > 0 || diffTx > 0) {
                                                                        currentBssid2 = currentBssid3;
                                                                        if (now4 - this.mLastDnsCheckTime > ((long) (WifiConnectivityMonitor.this.mCurrentMode == 3 ? 30000 : 60000))) {
                                                                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                                Log.d(TAG, "PERIODIC DNS CHECK TRIGGER (SIMPLE CONNECTION TEST) - Last DNS check was " + ((now4 - this.mLastDnsCheckTime) / 1000) + " seconds ago.");
                                                                            }
                                                                            this.mNsaQcTrigger = 44;
                                                                            needCheckInternetIsAlive = true;
                                                                        }
                                                                    } else {
                                                                        currentBssid2 = currentBssid3;
                                                                    }
                                                                    if (diffRx <= 2 && diffTx >= 10 && txBytesPerPacket < 1000) {
                                                                        Log.i(TAG, "pull out the line???");
                                                                        this.mNsaQcTrigger = 31;
                                                                        needCheckInternetIsAlive = true;
                                                                    }
                                                                    if (diffRx > ((long) WifiConnectivityMonitor.this.mParam.mNoRxPacketsLimit)) {
                                                                        if (now4 - this.mLastNeedCheckByPoorRxTime > 30000) {
                                                                            Objects.requireNonNull(WifiConnectivityMonitor.this.mParam);
                                                                            if (diffRxBytes2 < ((long) 22260) && diffRx < ((long) WifiConnectivityMonitor.this.mParam.mGoodRxPacketsBase) && diffTx > 0 && mrssi < -70) {
                                                                                this.mCumulativePoorRx.add(Integer.valueOf((int) diffRx));
                                                                                if (56 < txBytesPerPacket && txBytesPerPacket < 73 && 90 < (txProportionToRx = (int) ((100 * diffTx) / diffRx)) && txProportionToRx < 110 && txBytesPerPacket - 10 < rxBytesPerPacket && rxBytesPerPacket <= txBytesPerPacket) {
                                                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                                        Log.w(TAG, "DNS queries and abnormal responses");
                                                                                    }
                                                                                    this.mNsaQcTrigger = 32;
                                                                                    needCheckInternetIsAlive = true;
                                                                                }
                                                                            }
                                                                        }
                                                                        this.mCumulativePoorRx.clear();
                                                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                        }
                                                                        this.mNsaQcTrigger = 32;
                                                                        needCheckInternetIsAlive = true;
                                                                    } else {
                                                                        if (this.mSYNPacketOnly) {
                                                                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                                Log.w(TAG, "No [SYN,ACK] or No subsequent transaction");
                                                                            }
                                                                            if (this.mGoodRxTime > 0) {
                                                                                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                                    Log.w(TAG, "suspicious No Rx state but staying in good Rx state now");
                                                                                }
                                                                                this.mNsaQcTrigger = 33;
                                                                                needCheckInternetIsAlive = true;
                                                                                needCheckByNoRx = false;
                                                                            } else if (isEvaluationLevel2) {
                                                                                this.mNsaQcTrigger = 34;
                                                                                needCheckByNoRx = true;
                                                                            } else {
                                                                                needCheckByNoRx = false;
                                                                            }
                                                                            this.mSYNPacketOnly = false;
                                                                        } else {
                                                                            if (diffTx <= 0) {
                                                                                this.mCumulativePoorRx.clear();
                                                                            } else if (diffRx == 0) {
                                                                                this.mCumulativePoorRx.clear();
                                                                            } else {
                                                                                this.mCumulativePoorRx.add(Integer.valueOf((int) diffRx));
                                                                            }
                                                                            needCheckByNoRx = false;
                                                                        }
                                                                        if (this.mMaybeUseStreaming < 3 || !isEvaluationLevel2) {
                                                                            needCheckByNoRx2 = needCheckByNoRx;
                                                                        } else {
                                                                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                                Log.w(TAG, "could be in No service state during streaming!");
                                                                            }
                                                                            this.mNsaQcTrigger = 35;
                                                                            needCheckInternetIsAlive = true;
                                                                            needCheckByNoRx2 = needCheckByNoRx;
                                                                        }
                                                                    }
                                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                        Log.i(TAG, "mCumulativePoorRx.size : " + this.mCumulativePoorRx.size());
                                                                    }
                                                                    if (this.mCumulativePoorRx.size() == 3) {
                                                                        int sum = 0;
                                                                        Iterator<Integer> it = this.mCumulativePoorRx.iterator();
                                                                        while (it.hasNext()) {
                                                                            sum += it.next().intValue();
                                                                        }
                                                                        if (sum < WifiConnectivityMonitor.this.mParam.mPoorRxPacketsLimit * 3) {
                                                                            now2 = now4;
                                                                            this.mLastNeedCheckByPoorRxTime = now2;
                                                                            if (isEvaluationLevel2) {
                                                                                needCheckByPoorRx = true;
                                                                                this.mNsaQcTrigger = 36;
                                                                            } else {
                                                                                needCheckByPoorRx = false;
                                                                            }
                                                                            int i4 = 0;
                                                                            while (true) {
                                                                                if (i4 >= 3) {
                                                                                    needCheckByPoorRx2 = needCheckByPoorRx;
                                                                                } else if (this.mRxHistory.get(i4).intValue() >= WifiConnectivityMonitor.this.mParam.mGoodRxPacketsBase) {
                                                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                                        Log.i(TAG, "It's hard to say poor rx");
                                                                                    }
                                                                                    needCheckByPoorRx2 = false;
                                                                                } else {
                                                                                    i4++;
                                                                                    sum = sum;
                                                                                }
                                                                            }
                                                                            if (!needCheckByPoorRx2) {
                                                                                this.mCumulativePoorRx.remove(0);
                                                                                this.mCumulativePoorRx.trimToSize();
                                                                            } else if (this.mGoodRxTime > 0) {
                                                                                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                                    Log.w(TAG, "suspicious Poor Rx state but staying in good Rx state now");
                                                                                }
                                                                                this.mNsaQcTrigger = 37;
                                                                                needCheckInternetIsAlive = true;
                                                                                needCheckByPoorRx3 = false;
                                                                            } else {
                                                                                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                                    Log.w(TAG, "Cumulative Rx is in poor status!");
                                                                                }
                                                                                if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                                                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                                        Log.i(TAG, "check dns in poor rx status of AGG");
                                                                                    }
                                                                                    this.mNsaQcTrigger = 38;
                                                                                    needCheckInternetIsAlive = true;
                                                                                    needCheckByPoorRx3 = false;
                                                                                }
                                                                            }
                                                                            needCheckByPoorRx3 = needCheckByPoorRx2;
                                                                        } else {
                                                                            now2 = now4;
                                                                            this.mCumulativePoorRx.remove(0);
                                                                            this.mCumulativePoorRx.trimToSize();
                                                                        }
                                                                    } else {
                                                                        now2 = now4;
                                                                    }
                                                                    if (now2 - this.mLastDnsCheckTime > 1500 && diffTx >= 2 && 59 <= txBytesPerPacket && txBytesPerPacket <= 62) {
                                                                        if (diffRxBytes2 > diffTxBytes) {
                                                                            if (rxBytesPerPacket < 500) {
                                                                                Objects.requireNonNull(WifiConnectivityMonitor.this.mParam);
                                                                                break;
                                                                            }
                                                                        }
                                                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                            Log.i(TAG, "SYN packets might be transmitted");
                                                                        }
                                                                        this.mSYNPacketOnly = true;
                                                                        int prevStreaming = this.mMaybeUseStreaming;
                                                                        this.mMaybeUseStreaming = (diffRx > ((long) WifiConnectivityMonitor.this.mParam.mPoorRxPacketsLimit) || !isStreaming2) ? 0 : this.mMaybeUseStreaming + 1;
                                                                        if (prevStreaming >= 5 && this.mMaybeUseStreaming == 0) {
                                                                            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                                Log.w(TAG, "need to check if there are problems on streaming service");
                                                                            }
                                                                            if (isEvaluationLevel2) {
                                                                                this.mNsaQcTrigger = 39;
                                                                                needCheckInternetIsAlive = true;
                                                                            }
                                                                        }
                                                                        if ((needCheckByPoorRx3 || needCheckByNoRx2) && now2 - this.mLastDnsCheckTime >= 20000) {
                                                                            this.mCumulativePoorRx.clear();
                                                                            this.mLastDnsCheckTime = now2;
                                                                            if (!WifiConnectivityMonitor.this.isInvalidState()) {
                                                                                WifiConnectivityMonitor.this.requestInternetCheck(this.mNsaQcStep, this.mNsaQcTrigger);
                                                                            }
                                                                            needCheckInternetIsAlive = false;
                                                                        }
                                                                        if (needCheckInternetIsAlive) {
                                                                            diffTxBytes2 = diffTxBytes;
                                                                            currentBssid = currentBssid2;
                                                                            j = 1000;
                                                                            now = now2;
                                                                        } else if (now2 - this.mLastDnsCheckTime >= 20000) {
                                                                            this.mCumulativePoorRx.clear();
                                                                            this.mSkipRemainingDnsResults = false;
                                                                            this.mDnsQueried = true;
                                                                            this.mNsaQcStep = 1;
                                                                            Objects.requireNonNull(WifiConnectivityMonitor.this.mParam);
                                                                            int timeoutMS = 10000;
                                                                            if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                                                                                timeoutMS = 5000;
                                                                            }
                                                                            String dnsTargetUrl2 = WifiConnectivityMonitor.this.mParam.DEFAULT_URL_STRING;
                                                                            if (WifiConnectivityMonitor.this.inChinaNetwork()) {
                                                                                Objects.requireNonNull(WifiConnectivityMonitor.this.mParam);
                                                                                dnsTargetUrl = "www.qq.com";
                                                                            } else {
                                                                                dnsTargetUrl = dnsTargetUrl2;
                                                                            }
                                                                            diffTxBytes2 = diffTxBytes;
                                                                            now = now2;
                                                                            currentBssid = currentBssid2;
                                                                            j = 1000;
                                                                            DnsThread mDnsThread = new DnsThread(true, dnsTargetUrl, this, (long) timeoutMS);
                                                                            mDnsThread.start();
                                                                            this.mLastDnsCheckTime = now;
                                                                            WifiConnectivityMonitor.this.mDnsThreadID = mDnsThread.getId();
                                                                            if (WifiConnectivityMonitor.DBG) {
                                                                                Log.d(TAG, "wait needCheck DnsThread results [" + WifiConnectivityMonitor.this.mDnsThreadID + "]");
                                                                            }
                                                                        } else {
                                                                            diffTxBytes2 = diffTxBytes;
                                                                            currentBssid = currentBssid2;
                                                                            j = 1000;
                                                                            now = now2;
                                                                        }
                                                                    }
                                                                    this.mSYNPacketOnly = false;
                                                                    int prevStreaming2 = this.mMaybeUseStreaming;
                                                                    this.mMaybeUseStreaming = (diffRx > ((long) WifiConnectivityMonitor.this.mParam.mPoorRxPacketsLimit) || !isStreaming2) ? 0 : this.mMaybeUseStreaming + 1;
                                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                                    }
                                                                    if (isEvaluationLevel2) {
                                                                    }
                                                                    this.mCumulativePoorRx.clear();
                                                                    this.mLastDnsCheckTime = now2;
                                                                    if (!WifiConnectivityMonitor.this.isInvalidState()) {
                                                                    }
                                                                    needCheckInternetIsAlive = false;
                                                                    if (needCheckInternetIsAlive) {
                                                                    }
                                                                } else {
                                                                    this.mCumulativePoorRx.clear();
                                                                    this.mSYNPacketOnly = false;
                                                                    if (isStreaming2) {
                                                                        this.mMaybeUseStreaming++;
                                                                    }
                                                                    if (now4 - this.mLastDnsCheckTime > 7000) {
                                                                        this.mLastDnsCheckTime = now4 - 7000;
                                                                    }
                                                                    currentBssid = currentBssid3;
                                                                    diffTxBytes2 = diffTxBytes;
                                                                    now = now4;
                                                                    j = 1000;
                                                                }
                                                            }
                                                            removeMessages(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL);
                                                            sendEmptyMessageDelayed(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL, j);
                                                            if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                                                            }
                                                            if (this.lastPollTime != 0) {
                                                            }
                                                            this.lastPollTime = now;
                                                            return;
                                                        }
                                                        this.mSYNPacketOnly = false;
                                                        this.mCumulativePoorRx.clear();
                                                        this.mMaybeUseStreaming = 0;
                                                        this.mStayingLowMCS = 0;
                                                        removeMessages(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL);
                                                        sendEmptyMessageDelayed(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL, j);
                                                        if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                                                        }
                                                        if (this.lastPollTime != 0) {
                                                        }
                                                        this.lastPollTime = now;
                                                        return;
                                                    }
                                                    z = true;
                                                    wifiConnectivityMonitor.mInAggGoodStateNow = z;
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                    }
                                                    currentBssid3.updateGoodRssi(mrssi2);
                                                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                                    }
                                                    if (!WifiConnectivityMonitor.this.mIsScanning) {
                                                    }
                                                    mrssi = mrssi2;
                                                    currentBssid = currentBssid3;
                                                    diffRxBytes2 = diffRxBytes;
                                                    now = now4;
                                                    diffTxBytes2 = diffTxBytes;
                                                    j = 1000;
                                                    this.mSYNPacketOnly = false;
                                                    this.mCumulativePoorRx.clear();
                                                    this.mMaybeUseStreaming = 0;
                                                    this.mStayingLowMCS = 0;
                                                    removeMessages(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL);
                                                    sendEmptyMessageDelayed(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL, j);
                                                    if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                                                    }
                                                    if (this.lastPollTime != 0) {
                                                    }
                                                    this.lastPollTime = now;
                                                    return;
                                                }
                                                return;
                                            }
                                        }
                                        rssi = rssi2;
                                        int mrssi22 = (this.mLastRssi + rssi) / 2;
                                        this.mLastRssi = rssi;
                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        }
                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        }
                                        isEvaluationLevel = false;
                                        if (wifiInfo.getLinkSpeed() > 6) {
                                        }
                                        if (this.mLastRssi < -75) {
                                        }
                                        isEvaluationLevel2 = isEvaluationLevel;
                                        setGoodRxStateNow(0);
                                        long preTxPkts3 = this.mTxPackets;
                                        long preRxPkts2 = this.mRxPackets;
                                        this.mTxPackets = TrafficStats.getTxPackets(WifiConnectivityMonitor.INTERFACENAMEOFWLAN);
                                        this.mRxPackets = TrafficStats.getRxPackets(WifiConnectivityMonitor.INTERFACENAMEOFWLAN);
                                        long diffRx2 = this.mRxPackets - preRxPkts2;
                                        long diffTx2 = this.mTxPackets - preTxPkts3;
                                        this.mCurrentRxStats = (int) diffRx2;
                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        }
                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        }
                                        long preTxPkts22 = this.mTxBytes;
                                        long preRxbyte2 = this.mRxBytes;
                                        this.mTxBytes = TrafficStats.getTxBytes(WifiConnectivityMonitor.INTERFACENAMEOFWLAN);
                                        this.mRxBytes = TrafficStats.getRxBytes(WifiConnectivityMonitor.INTERFACENAMEOFWLAN);
                                        diffRxBytes = this.mRxBytes - preRxbyte2;
                                        long diffTxBytes32 = this.mTxBytes - preTxPkts22;
                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        }
                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        }
                                        int rxBytesPerPacket2 = (int) (diffRx2 <= 0 ? diffRxBytes / diffRx2 : 0);
                                        int txBytesPerPacket2 = (int) (diffTx2 <= 0 ? diffTxBytes32 / diffTx2 : 0);
                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        }
                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        }
                                        if (WifiConnectivityMonitor.this.mNetworkStatHistoryUpdate) {
                                        }
                                        boolean needCheckInternetIsAlive2 = false;
                                        boolean needCheckByNoRx22 = false;
                                        if (this.mRxHistory.size() == 6) {
                                        }
                                        boolean needCheckByPoorRx32 = false;
                                        this.mRxHistory.add(new Integer((int) diffRx2));
                                        if (rxBytesPerPacket2 <= 1430) {
                                        }
                                        currentBssid3.updateMaxThroughput(mrssi22, diffRxBytes, isStreaming);
                                        WifiConnectivityMonitor wifiConnectivityMonitor2 = WifiConnectivityMonitor.this;
                                        if (isStreaming) {
                                        }
                                        if (diffRxBytes < ((long) (WifiConnectivityMonitor.this.mParam.mPassBytesAggressiveMode * 20))) {
                                        }
                                        z = true;
                                        wifiConnectivityMonitor2.mInAggGoodStateNow = z;
                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        }
                                        currentBssid3.updateGoodRssi(mrssi22);
                                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                        }
                                        if (!WifiConnectivityMonitor.this.mIsScanning) {
                                        }
                                        mrssi = mrssi22;
                                        currentBssid = currentBssid3;
                                        diffRxBytes2 = diffRxBytes;
                                        now = now4;
                                        diffTxBytes2 = diffTxBytes;
                                        j = 1000;
                                        this.mSYNPacketOnly = false;
                                        this.mCumulativePoorRx.clear();
                                        this.mMaybeUseStreaming = 0;
                                        this.mStayingLowMCS = 0;
                                        removeMessages(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL);
                                        sendEmptyMessageDelayed(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL, j);
                                        if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                                        }
                                        if (this.lastPollTime != 0) {
                                        }
                                        this.lastPollTime = now;
                                        return;
                                    }
                                }
                                Log.e(TAG, "currentBssid is null.");
                                removeMessages(WifiConnectivityMonitor.ACTIVITY_CHECK_POLL);
                                sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_STOP);
                                return;
                            }
                            return;
                        case WifiConnectivityMonitor.ACTIVITY_RESTART_AGGRESSIVE_MODE /*{ENCODED_INT: 135222}*/:
                            return;
                        case WifiConnectivityMonitor.NETWORK_STAT_CHECK_DNS /*{ENCODED_INT: 135223}*/:
                            if (!WifiConnectivityMonitor.this.isMobileHotspot()) {
                                checkPublicDns();
                                return;
                            }
                            return;
                        case WifiConnectivityMonitor.NETWORK_STAT_SET_GOOD_RX_STATE_NOW /*{ENCODED_INT: 135224}*/:
                            setGoodRxStateNow(((Long) msg.obj).longValue());
                            return;
                        case WifiConnectivityMonitor.CMD_DELAY_NETSTATS_SESSION_INIT /*{ENCODED_INT: 135225}*/:
                            WifiConnectivityMonitor.this.initDataUsage();
                            return;
                        case WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START /*{ENCODED_INT: 135226}*/:
                            long j2 = this.mBackhaulQcResultTime;
                            if ((j2 > 0 && j2 + 10000 > now4) || ((i = this.mBackhaulQcGoodRssi) < 0 && i - 3 < wifiInfo.getRssi())) {
                                if (WifiConnectivityMonitor.DBG) {
                                    Log.d(TAG, "TCP_BACKHAUL_DETECTION_START skipped - mQcResultTime:" + this.mBackhaulQcResultTime + ", now:" + now4 + ", mQcGoodRssi:" + this.mBackhaulQcGoodRssi + ", rssi:" + wifiInfo.getRssi());
                                }
                                removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, 1000);
                                return;
                            } else if (this.mPollingStarted) {
                                BufferedReader bufferSockstat6 = null;
                                int currTcpEstablishedCount3 = 0;
                                int tcpInUseCount2 = 0;
                                int orphanCount = 0;
                                int timeWaitCount3 = 0;
                                int inSegCount2 = 0;
                                int outSegCount2 = 0;
                                int inSegErrorCount2 = 0;
                                int retransSegCount2 = 0;
                                int diffRetransSegCount2 = 0;
                                int diffInSegErrorCount2 = 0;
                                int diffInSegCount2 = 0;
                                int diffOutSegCount2 = 0;
                                int lineNumber2 = 0;
                                boolean checkBackhaul2 = false;
                                int waitingCycleThreshold2 = 0;
                                long txCurr = TrafficStats.getTotalTxPackets();
                                long rxCurr2 = TrafficStats.getTotalRxPackets();
                                FileReader readerSockstat63 = null;
                                BufferedReader bufferSNMP2 = null;
                                long diffRxCurr2 = rxCurr2 - this.mPreviousRx;
                                long diffTxCurr2 = txCurr - this.mPreviousTx;
                                if (wifiInfo != null) {
                                    int currRssi2 = wifiInfo.getRssi();
                                    if (currRssi2 >= -70) {
                                        waitingCycleThreshold2 = 5;
                                        currRssi = currRssi2;
                                        bufferSockstat4 = null;
                                        internetConnectivityCounterThreshold = 5;
                                    } else if (currRssi2 >= -70 || currRssi2 <= -83) {
                                        waitingCycleThreshold2 = 2;
                                        currRssi = currRssi2;
                                        bufferSockstat4 = null;
                                        internetConnectivityCounterThreshold = 2;
                                    } else {
                                        waitingCycleThreshold2 = 3;
                                        currRssi = currRssi2;
                                        bufferSockstat4 = null;
                                        internetConnectivityCounterThreshold = 2;
                                    }
                                } else {
                                    currRssi = 0;
                                    bufferSockstat4 = null;
                                    internetConnectivityCounterThreshold = 0;
                                }
                                long diffTxCurr3 = diffTxCurr2;
                                int i5 = this.mBackhaulQcGoodRssi;
                                if (i5 >= 0 || currRssi < i5) {
                                    waitingCycleThreshold = waitingCycleThreshold2;
                                } else {
                                    waitingCycleThreshold = waitingCycleThreshold2 + 3;
                                }
                                try {
                                    FileReader readerSNMP = new FileReader("/proc/net/snmp");
                                    try {
                                        readerSockstat = new FileReader("/proc/net/sockstat");
                                        try {
                                            rxCurr = rxCurr2;
                                        } catch (Exception e) {
                                            e = e;
                                            rxCurr = rxCurr2;
                                            diffRxCurr = diffRxCurr2;
                                            diffTxCurr = diffTxCurr3;
                                            str = "Exception: ";
                                            readerSockstat2 = readerSockstat;
                                            readerSockstat6 = readerSNMP;
                                            readerSockstat62 = null;
                                            try {
                                                Log.w(TAG, str + e);
                                                e.printStackTrace();
                                                if (readerSockstat6 != null) {
                                                }
                                                if (readerSockstat2 != null) {
                                                }
                                                if (readerSockstat62 != null) {
                                                }
                                                if (readerSockstat2 != null) {
                                                }
                                                if (bufferSNMP2 != null) {
                                                }
                                                if (bufferSockstat4 != null) {
                                                }
                                                if (bufferSockstat6 != null) {
                                                }
                                                now3 = now4;
                                                currTcpEstablishedCount = currTcpEstablishedCount3;
                                                tcpInUseCount = tcpInUseCount2;
                                                timeWaitCount = timeWaitCount3;
                                                inSegCount = inSegCount2;
                                                inSegErrorCount = inSegErrorCount2;
                                                retransSegCount = retransSegCount2;
                                                diffRetransSegCount = diffRetransSegCount2;
                                                diffInSegErrorCount = diffInSegErrorCount2;
                                                diffOutSegCount = diffOutSegCount2;
                                                diffInSegCount = diffInSegCount2;
                                                checkBackhaul = false;
                                                outSegCount = outSegCount2;
                                                String log = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                if (!checkBackhaul) {
                                                }
                                                Log.d(TAG, "Backhaul result - " + log);
                                                this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                this.mPrevTimeWaitCount = timeWaitCount;
                                                this.mPrevTcpInUseCount = tcpInUseCount;
                                                this.mPrevRetranSegCount = retransSegCount;
                                                this.mPrevInSegErrorCount = inSegErrorCount;
                                                this.mPreviousRx = rxCurr;
                                                this.mPreviousTx = txCurr;
                                                this.mPrevInSegCount = inSegCount;
                                                this.mPrevOutSegCount = outSegCount;
                                                if (checkBackhaul) {
                                                }
                                                removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                return;
                                            } catch (Throwable th2) {
                                                readerSockstat = readerSockstat2;
                                                readerSockstat63 = readerSockstat62;
                                                bufferSNMP = bufferSNMP2;
                                                th = th2;
                                                if (readerSockstat6 != null) {
                                                }
                                                if (readerSockstat != null) {
                                                }
                                                if (readerSockstat63 != null) {
                                                }
                                                if (readerSockstat != null) {
                                                }
                                                if (bufferSNMP != null) {
                                                }
                                                if (bufferSockstat4 != null) {
                                                }
                                                if (bufferSockstat6 != null) {
                                                }
                                                throw th;
                                            }
                                        } catch (Throwable th3) {
                                            str = "Exception: ";
                                            readerSockstat6 = readerSNMP;
                                            bufferSNMP = null;
                                            th = th3;
                                            if (readerSockstat6 != null) {
                                            }
                                            if (readerSockstat != null) {
                                            }
                                            if (readerSockstat63 != null) {
                                            }
                                            if (readerSockstat != null) {
                                            }
                                            if (bufferSNMP != null) {
                                            }
                                            if (bufferSockstat4 != null) {
                                            }
                                            if (bufferSockstat6 != null) {
                                            }
                                            throw th;
                                        }
                                    } catch (Exception e2) {
                                        e = e2;
                                        rxCurr = rxCurr2;
                                        diffRxCurr = diffRxCurr2;
                                        diffTxCurr = diffTxCurr3;
                                        str = "Exception: ";
                                        readerSockstat6 = readerSNMP;
                                        readerSockstat2 = null;
                                        readerSockstat62 = null;
                                        Log.w(TAG, str + e);
                                        e.printStackTrace();
                                        if (readerSockstat6 != null) {
                                            try {
                                                readerSockstat6.close();
                                            } catch (IOException e3) {
                                                Log.w(TAG, str + e3);
                                                e3.printStackTrace();
                                                now3 = now4;
                                                currTcpEstablishedCount = currTcpEstablishedCount3;
                                                tcpInUseCount = tcpInUseCount2;
                                                timeWaitCount = timeWaitCount3;
                                                inSegCount = inSegCount2;
                                                inSegErrorCount = inSegErrorCount2;
                                                retransSegCount = retransSegCount2;
                                                diffRetransSegCount = diffRetransSegCount2;
                                                diffInSegErrorCount = diffInSegErrorCount2;
                                                diffOutSegCount = diffOutSegCount2;
                                                diffInSegCount = diffInSegCount2;
                                                checkBackhaul = false;
                                                outSegCount = outSegCount2;
                                                String log2 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                if (!checkBackhaul) {
                                                }
                                                Log.d(TAG, "Backhaul result - " + log2);
                                                this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                this.mPrevTimeWaitCount = timeWaitCount;
                                                this.mPrevTcpInUseCount = tcpInUseCount;
                                                this.mPrevRetranSegCount = retransSegCount;
                                                this.mPrevInSegErrorCount = inSegErrorCount;
                                                this.mPreviousRx = rxCurr;
                                                this.mPreviousTx = txCurr;
                                                this.mPrevInSegCount = inSegCount;
                                                this.mPrevOutSegCount = outSegCount;
                                                if (checkBackhaul) {
                                                }
                                                removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                return;
                                            }
                                        }
                                        if (readerSockstat2 != null) {
                                            readerSockstat2.close();
                                        }
                                        if (readerSockstat62 != null) {
                                            readerSockstat62.close();
                                        }
                                        if (readerSockstat2 != null) {
                                            readerSockstat2.close();
                                        }
                                        if (bufferSNMP2 != null) {
                                            bufferSNMP2.close();
                                        }
                                        if (bufferSockstat4 != null) {
                                            bufferSockstat4.close();
                                        }
                                        if (bufferSockstat6 != null) {
                                            bufferSockstat6.close();
                                        }
                                        now3 = now4;
                                        currTcpEstablishedCount = currTcpEstablishedCount3;
                                        tcpInUseCount = tcpInUseCount2;
                                        timeWaitCount = timeWaitCount3;
                                        inSegCount = inSegCount2;
                                        inSegErrorCount = inSegErrorCount2;
                                        retransSegCount = retransSegCount2;
                                        diffRetransSegCount = diffRetransSegCount2;
                                        diffInSegErrorCount = diffInSegErrorCount2;
                                        diffOutSegCount = diffOutSegCount2;
                                        diffInSegCount = diffInSegCount2;
                                        checkBackhaul = false;
                                        outSegCount = outSegCount2;
                                        String log22 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                        if (!checkBackhaul) {
                                        }
                                        Log.d(TAG, "Backhaul result - " + log22);
                                        this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                        this.mPrevTimeWaitCount = timeWaitCount;
                                        this.mPrevTcpInUseCount = tcpInUseCount;
                                        this.mPrevRetranSegCount = retransSegCount;
                                        this.mPrevInSegErrorCount = inSegErrorCount;
                                        this.mPreviousRx = rxCurr;
                                        this.mPreviousTx = txCurr;
                                        this.mPrevInSegCount = inSegCount;
                                        this.mPrevOutSegCount = outSegCount;
                                        if (checkBackhaul) {
                                        }
                                        removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                        sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                        return;
                                    } catch (Throwable th4) {
                                        str = "Exception: ";
                                        readerSockstat6 = readerSNMP;
                                        readerSockstat = null;
                                        bufferSNMP = null;
                                        th = th4;
                                        if (readerSockstat6 != null) {
                                            try {
                                                readerSockstat6.close();
                                            } catch (IOException e4) {
                                                Log.w(TAG, str + e4);
                                                e4.printStackTrace();
                                                throw th;
                                            }
                                        }
                                        if (readerSockstat != null) {
                                            readerSockstat.close();
                                        }
                                        if (readerSockstat63 != null) {
                                            readerSockstat63.close();
                                        }
                                        if (readerSockstat != null) {
                                            readerSockstat.close();
                                        }
                                        if (bufferSNMP != null) {
                                            bufferSNMP.close();
                                        }
                                        if (bufferSockstat4 != null) {
                                            bufferSockstat4.close();
                                        }
                                        if (bufferSockstat6 != null) {
                                            bufferSockstat6.close();
                                        }
                                        throw th;
                                    }
                                    try {
                                        FileReader readerSockstat64 = new FileReader("/proc/net/sockstat6");
                                        try {
                                            bufferSNMP = new BufferedReader(readerSNMP);
                                            try {
                                                bufferSockstat4 = new BufferedReader(readerSockstat);
                                                bufferSockstat6 = new BufferedReader(readerSockstat64);
                                                int tcpRow = 6;
                                                while (true) {
                                                    try {
                                                        String line = bufferSNMP.readLine();
                                                        if (line != null) {
                                                            int lineNumber3 = lineNumber2 + 1;
                                                            if (lineNumber3 == tcpRow) {
                                                                lineNumber2 = lineNumber3;
                                                                diffRxCurr = diffRxCurr2;
                                                                try {
                                                                    String[] columns = line.split(" +");
                                                                    try {
                                                                        if (columns[0].contains("Icmp")) {
                                                                            if (WifiConnectivityMonitor.DBG) {
                                                                                StringBuilder sb3 = new StringBuilder();
                                                                                sb3.append("checkBackhaulConnection: ");
                                                                                sb3.append(tcpRow);
                                                                                sb3.append(", ");
                                                                                diffTxCurr = diffTxCurr3;
                                                                                try {
                                                                                    sb3.append(columns[0]);
                                                                                    Log.d(TAG, sb3.toString());
                                                                                } catch (Exception e5) {
                                                                                    e = e5;
                                                                                    readerSockstat62 = readerSockstat64;
                                                                                    bufferSNMP2 = bufferSNMP;
                                                                                    readerSockstat2 = readerSockstat;
                                                                                    readerSockstat6 = readerSNMP;
                                                                                    str = "Exception: ";
                                                                                    Log.w(TAG, str + e);
                                                                                    e.printStackTrace();
                                                                                    if (readerSockstat6 != null) {
                                                                                    }
                                                                                    if (readerSockstat2 != null) {
                                                                                    }
                                                                                    if (readerSockstat62 != null) {
                                                                                    }
                                                                                    if (readerSockstat2 != null) {
                                                                                    }
                                                                                    if (bufferSNMP2 != null) {
                                                                                    }
                                                                                    if (bufferSockstat4 != null) {
                                                                                    }
                                                                                    if (bufferSockstat6 != null) {
                                                                                    }
                                                                                    now3 = now4;
                                                                                    currTcpEstablishedCount = currTcpEstablishedCount3;
                                                                                    tcpInUseCount = tcpInUseCount2;
                                                                                    timeWaitCount = timeWaitCount3;
                                                                                    inSegCount = inSegCount2;
                                                                                    inSegErrorCount = inSegErrorCount2;
                                                                                    retransSegCount = retransSegCount2;
                                                                                    diffRetransSegCount = diffRetransSegCount2;
                                                                                    diffInSegErrorCount = diffInSegErrorCount2;
                                                                                    diffOutSegCount = diffOutSegCount2;
                                                                                    diffInSegCount = diffInSegCount2;
                                                                                    checkBackhaul = false;
                                                                                    outSegCount = outSegCount2;
                                                                                    String log222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                                                    if (!checkBackhaul) {
                                                                                    }
                                                                                    Log.d(TAG, "Backhaul result - " + log222);
                                                                                    this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                                                    this.mPrevTimeWaitCount = timeWaitCount;
                                                                                    this.mPrevTcpInUseCount = tcpInUseCount;
                                                                                    this.mPrevRetranSegCount = retransSegCount;
                                                                                    this.mPrevInSegErrorCount = inSegErrorCount;
                                                                                    this.mPreviousRx = rxCurr;
                                                                                    this.mPreviousTx = txCurr;
                                                                                    this.mPrevInSegCount = inSegCount;
                                                                                    this.mPrevOutSegCount = outSegCount;
                                                                                    if (checkBackhaul) {
                                                                                    }
                                                                                    removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                                                    sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                                                    return;
                                                                                } catch (Throwable th5) {
                                                                                    readerSockstat63 = readerSockstat64;
                                                                                    readerSockstat6 = readerSNMP;
                                                                                    str = "Exception: ";
                                                                                    th = th5;
                                                                                    if (readerSockstat6 != null) {
                                                                                    }
                                                                                    if (readerSockstat != null) {
                                                                                    }
                                                                                    if (readerSockstat63 != null) {
                                                                                    }
                                                                                    if (readerSockstat != null) {
                                                                                    }
                                                                                    if (bufferSNMP != null) {
                                                                                    }
                                                                                    if (bufferSockstat4 != null) {
                                                                                    }
                                                                                    if (bufferSockstat6 != null) {
                                                                                    }
                                                                                    throw th;
                                                                                }
                                                                            } else {
                                                                                diffTxCurr = diffTxCurr3;
                                                                            }
                                                                            tcpRow += 2;
                                                                            diffRxCurr2 = diffRxCurr;
                                                                            diffTxCurr3 = diffTxCurr;
                                                                        } else {
                                                                            diffTxCurr = diffTxCurr3;
                                                                            int currTcpEstablishedCount4 = Integer.parseInt(columns[9]);
                                                                            retransSegCount2 = Integer.parseInt(columns[14]);
                                                                            inSegErrorCount2 = Integer.parseInt(columns[13]);
                                                                            inSegCount2 = Integer.parseInt(columns[10]);
                                                                            outSegCount2 = Integer.parseInt(columns[11]);
                                                                            currTcpEstablishedCount2 = currTcpEstablishedCount4;
                                                                        }
                                                                    } catch (Exception e6) {
                                                                        e = e6;
                                                                        diffTxCurr = diffTxCurr3;
                                                                        readerSockstat62 = readerSockstat64;
                                                                        bufferSNMP2 = bufferSNMP;
                                                                        readerSockstat2 = readerSockstat;
                                                                        readerSockstat6 = readerSNMP;
                                                                        str = "Exception: ";
                                                                        Log.w(TAG, str + e);
                                                                        e.printStackTrace();
                                                                        if (readerSockstat6 != null) {
                                                                        }
                                                                        if (readerSockstat2 != null) {
                                                                        }
                                                                        if (readerSockstat62 != null) {
                                                                        }
                                                                        if (readerSockstat2 != null) {
                                                                        }
                                                                        if (bufferSNMP2 != null) {
                                                                        }
                                                                        if (bufferSockstat4 != null) {
                                                                        }
                                                                        if (bufferSockstat6 != null) {
                                                                        }
                                                                        now3 = now4;
                                                                        currTcpEstablishedCount = currTcpEstablishedCount3;
                                                                        tcpInUseCount = tcpInUseCount2;
                                                                        timeWaitCount = timeWaitCount3;
                                                                        inSegCount = inSegCount2;
                                                                        inSegErrorCount = inSegErrorCount2;
                                                                        retransSegCount = retransSegCount2;
                                                                        diffRetransSegCount = diffRetransSegCount2;
                                                                        diffInSegErrorCount = diffInSegErrorCount2;
                                                                        diffOutSegCount = diffOutSegCount2;
                                                                        diffInSegCount = diffInSegCount2;
                                                                        checkBackhaul = false;
                                                                        outSegCount = outSegCount2;
                                                                        String log2222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                                        if (!checkBackhaul) {
                                                                        }
                                                                        Log.d(TAG, "Backhaul result - " + log2222);
                                                                        this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                                        this.mPrevTimeWaitCount = timeWaitCount;
                                                                        this.mPrevTcpInUseCount = tcpInUseCount;
                                                                        this.mPrevRetranSegCount = retransSegCount;
                                                                        this.mPrevInSegErrorCount = inSegErrorCount;
                                                                        this.mPreviousRx = rxCurr;
                                                                        this.mPreviousTx = txCurr;
                                                                        this.mPrevInSegCount = inSegCount;
                                                                        this.mPrevOutSegCount = outSegCount;
                                                                        if (checkBackhaul) {
                                                                        }
                                                                        removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                                        sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                                        return;
                                                                    } catch (Throwable th6) {
                                                                        readerSockstat63 = readerSockstat64;
                                                                        readerSockstat6 = readerSNMP;
                                                                        str = "Exception: ";
                                                                        th = th6;
                                                                        if (readerSockstat6 != null) {
                                                                        }
                                                                        if (readerSockstat != null) {
                                                                        }
                                                                        if (readerSockstat63 != null) {
                                                                        }
                                                                        if (readerSockstat != null) {
                                                                        }
                                                                        if (bufferSNMP != null) {
                                                                        }
                                                                        if (bufferSockstat4 != null) {
                                                                        }
                                                                        if (bufferSockstat6 != null) {
                                                                        }
                                                                        throw th;
                                                                    }
                                                                } catch (Exception e7) {
                                                                    e = e7;
                                                                    diffTxCurr = diffTxCurr3;
                                                                    readerSockstat62 = readerSockstat64;
                                                                    bufferSNMP2 = bufferSNMP;
                                                                    readerSockstat2 = readerSockstat;
                                                                    readerSockstat6 = readerSNMP;
                                                                    str = "Exception: ";
                                                                    Log.w(TAG, str + e);
                                                                    e.printStackTrace();
                                                                    if (readerSockstat6 != null) {
                                                                    }
                                                                    if (readerSockstat2 != null) {
                                                                    }
                                                                    if (readerSockstat62 != null) {
                                                                    }
                                                                    if (readerSockstat2 != null) {
                                                                    }
                                                                    if (bufferSNMP2 != null) {
                                                                    }
                                                                    if (bufferSockstat4 != null) {
                                                                    }
                                                                    if (bufferSockstat6 != null) {
                                                                    }
                                                                    now3 = now4;
                                                                    currTcpEstablishedCount = currTcpEstablishedCount3;
                                                                    tcpInUseCount = tcpInUseCount2;
                                                                    timeWaitCount = timeWaitCount3;
                                                                    inSegCount = inSegCount2;
                                                                    inSegErrorCount = inSegErrorCount2;
                                                                    retransSegCount = retransSegCount2;
                                                                    diffRetransSegCount = diffRetransSegCount2;
                                                                    diffInSegErrorCount = diffInSegErrorCount2;
                                                                    diffOutSegCount = diffOutSegCount2;
                                                                    diffInSegCount = diffInSegCount2;
                                                                    checkBackhaul = false;
                                                                    outSegCount = outSegCount2;
                                                                    String log22222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                                    if (!checkBackhaul) {
                                                                    }
                                                                    Log.d(TAG, "Backhaul result - " + log22222);
                                                                    this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                                    this.mPrevTimeWaitCount = timeWaitCount;
                                                                    this.mPrevTcpInUseCount = tcpInUseCount;
                                                                    this.mPrevRetranSegCount = retransSegCount;
                                                                    this.mPrevInSegErrorCount = inSegErrorCount;
                                                                    this.mPreviousRx = rxCurr;
                                                                    this.mPreviousTx = txCurr;
                                                                    this.mPrevInSegCount = inSegCount;
                                                                    this.mPrevOutSegCount = outSegCount;
                                                                    if (checkBackhaul) {
                                                                    }
                                                                    removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                                    sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                                    return;
                                                                } catch (Throwable th7) {
                                                                    readerSockstat63 = readerSockstat64;
                                                                    readerSockstat6 = readerSNMP;
                                                                    str = "Exception: ";
                                                                    th = th7;
                                                                    if (readerSockstat6 != null) {
                                                                    }
                                                                    if (readerSockstat != null) {
                                                                    }
                                                                    if (readerSockstat63 != null) {
                                                                    }
                                                                    if (readerSockstat != null) {
                                                                    }
                                                                    if (bufferSNMP != null) {
                                                                    }
                                                                    if (bufferSockstat4 != null) {
                                                                    }
                                                                    if (bufferSockstat6 != null) {
                                                                    }
                                                                    throw th;
                                                                }
                                                            } else {
                                                                lineNumber2 = lineNumber3;
                                                            }
                                                        } else {
                                                            diffRxCurr = diffRxCurr2;
                                                            diffTxCurr = diffTxCurr3;
                                                            currTcpEstablishedCount2 = 0;
                                                        }
                                                    } catch (Exception e8) {
                                                        e = e8;
                                                        diffRxCurr = diffRxCurr2;
                                                        diffTxCurr = diffTxCurr3;
                                                        str = "Exception: ";
                                                        readerSockstat62 = readerSockstat64;
                                                        bufferSNMP2 = bufferSNMP;
                                                        readerSockstat2 = readerSockstat;
                                                        readerSockstat6 = readerSNMP;
                                                        Log.w(TAG, str + e);
                                                        e.printStackTrace();
                                                        if (readerSockstat6 != null) {
                                                        }
                                                        if (readerSockstat2 != null) {
                                                        }
                                                        if (readerSockstat62 != null) {
                                                        }
                                                        if (readerSockstat2 != null) {
                                                        }
                                                        if (bufferSNMP2 != null) {
                                                        }
                                                        if (bufferSockstat4 != null) {
                                                        }
                                                        if (bufferSockstat6 != null) {
                                                        }
                                                        now3 = now4;
                                                        currTcpEstablishedCount = currTcpEstablishedCount3;
                                                        tcpInUseCount = tcpInUseCount2;
                                                        timeWaitCount = timeWaitCount3;
                                                        inSegCount = inSegCount2;
                                                        inSegErrorCount = inSegErrorCount2;
                                                        retransSegCount = retransSegCount2;
                                                        diffRetransSegCount = diffRetransSegCount2;
                                                        diffInSegErrorCount = diffInSegErrorCount2;
                                                        diffOutSegCount = diffOutSegCount2;
                                                        diffInSegCount = diffInSegCount2;
                                                        checkBackhaul = false;
                                                        outSegCount = outSegCount2;
                                                        String log222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                        if (!checkBackhaul) {
                                                        }
                                                        Log.d(TAG, "Backhaul result - " + log222222);
                                                        this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                        this.mPrevTimeWaitCount = timeWaitCount;
                                                        this.mPrevTcpInUseCount = tcpInUseCount;
                                                        this.mPrevRetranSegCount = retransSegCount;
                                                        this.mPrevInSegErrorCount = inSegErrorCount;
                                                        this.mPreviousRx = rxCurr;
                                                        this.mPreviousTx = txCurr;
                                                        this.mPrevInSegCount = inSegCount;
                                                        this.mPrevOutSegCount = outSegCount;
                                                        if (checkBackhaul) {
                                                        }
                                                        removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                        sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                        return;
                                                    } catch (Throwable th8) {
                                                        str = "Exception: ";
                                                        readerSockstat63 = readerSockstat64;
                                                        readerSockstat6 = readerSNMP;
                                                        th = th8;
                                                        if (readerSockstat6 != null) {
                                                        }
                                                        if (readerSockstat != null) {
                                                        }
                                                        if (readerSockstat63 != null) {
                                                        }
                                                        if (readerSockstat != null) {
                                                        }
                                                        if (bufferSNMP != null) {
                                                        }
                                                        if (bufferSockstat4 != null) {
                                                        }
                                                        if (bufferSockstat6 != null) {
                                                        }
                                                        throw th;
                                                    }
                                                }
                                                int lineNumber4 = 0;
                                                while (true) {
                                                    try {
                                                        String line2 = bufferSockstat4.readLine();
                                                        if (line2 != null) {
                                                            int lineNumber5 = lineNumber4 + 1;
                                                            try {
                                                                String[] columns2 = line2.split(" +");
                                                                if (lineNumber5 == 2) {
                                                                    tcpInUseCount2 = Integer.parseInt(columns2[2]);
                                                                    orphanCount = Integer.parseInt(columns2[4]);
                                                                    lineNumber = Integer.parseInt(columns2[6]);
                                                                } else {
                                                                    lineNumber4 = lineNumber5;
                                                                }
                                                            } catch (Exception e9) {
                                                                e = e9;
                                                                bufferSNMP2 = bufferSNMP;
                                                                currTcpEstablishedCount3 = currTcpEstablishedCount2;
                                                                readerSockstat2 = readerSockstat;
                                                                str = "Exception: ";
                                                                readerSockstat62 = readerSockstat64;
                                                                readerSockstat6 = readerSNMP;
                                                                Log.w(TAG, str + e);
                                                                e.printStackTrace();
                                                                if (readerSockstat6 != null) {
                                                                }
                                                                if (readerSockstat2 != null) {
                                                                }
                                                                if (readerSockstat62 != null) {
                                                                }
                                                                if (readerSockstat2 != null) {
                                                                }
                                                                if (bufferSNMP2 != null) {
                                                                }
                                                                if (bufferSockstat4 != null) {
                                                                }
                                                                if (bufferSockstat6 != null) {
                                                                }
                                                                now3 = now4;
                                                                currTcpEstablishedCount = currTcpEstablishedCount3;
                                                                tcpInUseCount = tcpInUseCount2;
                                                                timeWaitCount = timeWaitCount3;
                                                                inSegCount = inSegCount2;
                                                                inSegErrorCount = inSegErrorCount2;
                                                                retransSegCount = retransSegCount2;
                                                                diffRetransSegCount = diffRetransSegCount2;
                                                                diffInSegErrorCount = diffInSegErrorCount2;
                                                                diffOutSegCount = diffOutSegCount2;
                                                                diffInSegCount = diffInSegCount2;
                                                                checkBackhaul = false;
                                                                outSegCount = outSegCount2;
                                                                String log2222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                                if (!checkBackhaul) {
                                                                }
                                                                Log.d(TAG, "Backhaul result - " + log2222222);
                                                                this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                                this.mPrevTimeWaitCount = timeWaitCount;
                                                                this.mPrevTcpInUseCount = tcpInUseCount;
                                                                this.mPrevRetranSegCount = retransSegCount;
                                                                this.mPrevInSegErrorCount = inSegErrorCount;
                                                                this.mPreviousRx = rxCurr;
                                                                this.mPreviousTx = txCurr;
                                                                this.mPrevInSegCount = inSegCount;
                                                                this.mPrevOutSegCount = outSegCount;
                                                                if (checkBackhaul) {
                                                                }
                                                                removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                                sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                                return;
                                                            } catch (Throwable th9) {
                                                                readerSockstat63 = readerSockstat64;
                                                                readerSockstat6 = readerSNMP;
                                                                str = "Exception: ";
                                                                th = th9;
                                                                if (readerSockstat6 != null) {
                                                                }
                                                                if (readerSockstat != null) {
                                                                }
                                                                if (readerSockstat63 != null) {
                                                                }
                                                                if (readerSockstat != null) {
                                                                }
                                                                if (bufferSNMP != null) {
                                                                }
                                                                if (bufferSockstat4 != null) {
                                                                }
                                                                if (bufferSockstat6 != null) {
                                                                }
                                                                throw th;
                                                            }
                                                        } else {
                                                            lineNumber = 0;
                                                        }
                                                    } catch (Exception e10) {
                                                        e = e10;
                                                        str = "Exception: ";
                                                        readerSockstat62 = readerSockstat64;
                                                        bufferSNMP2 = bufferSNMP;
                                                        readerSockstat2 = readerSockstat;
                                                        currTcpEstablishedCount3 = currTcpEstablishedCount2;
                                                        readerSockstat6 = readerSNMP;
                                                        Log.w(TAG, str + e);
                                                        e.printStackTrace();
                                                        if (readerSockstat6 != null) {
                                                        }
                                                        if (readerSockstat2 != null) {
                                                        }
                                                        if (readerSockstat62 != null) {
                                                        }
                                                        if (readerSockstat2 != null) {
                                                        }
                                                        if (bufferSNMP2 != null) {
                                                        }
                                                        if (bufferSockstat4 != null) {
                                                        }
                                                        if (bufferSockstat6 != null) {
                                                        }
                                                        now3 = now4;
                                                        currTcpEstablishedCount = currTcpEstablishedCount3;
                                                        tcpInUseCount = tcpInUseCount2;
                                                        timeWaitCount = timeWaitCount3;
                                                        inSegCount = inSegCount2;
                                                        inSegErrorCount = inSegErrorCount2;
                                                        retransSegCount = retransSegCount2;
                                                        diffRetransSegCount = diffRetransSegCount2;
                                                        diffInSegErrorCount = diffInSegErrorCount2;
                                                        diffOutSegCount = diffOutSegCount2;
                                                        diffInSegCount = diffInSegCount2;
                                                        checkBackhaul = false;
                                                        outSegCount = outSegCount2;
                                                        String log22222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                        if (!checkBackhaul) {
                                                        }
                                                        Log.d(TAG, "Backhaul result - " + log22222222);
                                                        this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                        this.mPrevTimeWaitCount = timeWaitCount;
                                                        this.mPrevTcpInUseCount = tcpInUseCount;
                                                        this.mPrevRetranSegCount = retransSegCount;
                                                        this.mPrevInSegErrorCount = inSegErrorCount;
                                                        this.mPreviousRx = rxCurr;
                                                        this.mPreviousTx = txCurr;
                                                        this.mPrevInSegCount = inSegCount;
                                                        this.mPrevOutSegCount = outSegCount;
                                                        if (checkBackhaul) {
                                                        }
                                                        removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                        sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                        return;
                                                    } catch (Throwable th10) {
                                                        str = "Exception: ";
                                                        readerSockstat63 = readerSockstat64;
                                                        readerSockstat6 = readerSNMP;
                                                        th = th10;
                                                        if (readerSockstat6 != null) {
                                                        }
                                                        if (readerSockstat != null) {
                                                        }
                                                        if (readerSockstat63 != null) {
                                                        }
                                                        if (readerSockstat != null) {
                                                        }
                                                        if (bufferSNMP != null) {
                                                        }
                                                        if (bufferSockstat4 != null) {
                                                        }
                                                        if (bufferSockstat6 != null) {
                                                        }
                                                        throw th;
                                                    }
                                                }
                                                int lineNumber6 = 0;
                                                while (true) {
                                                    try {
                                                        String line3 = bufferSockstat6.readLine();
                                                        if (line3 != null) {
                                                            int lineNumber7 = lineNumber6 + 1;
                                                            try {
                                                                String[] columns3 = line3.split(" +");
                                                                if (lineNumber7 == 1) {
                                                                    tcpInUseCount2 += Integer.parseInt(columns3[2]);
                                                                } else {
                                                                    lineNumber6 = lineNumber7;
                                                                    tcpRow = tcpRow;
                                                                }
                                                            } catch (Exception e11) {
                                                                e = e11;
                                                                bufferSNMP2 = bufferSNMP;
                                                                currTcpEstablishedCount3 = currTcpEstablishedCount2;
                                                                timeWaitCount3 = lineNumber;
                                                                readerSockstat2 = readerSockstat;
                                                                str = "Exception: ";
                                                                readerSockstat62 = readerSockstat64;
                                                                readerSockstat6 = readerSNMP;
                                                                Log.w(TAG, str + e);
                                                                e.printStackTrace();
                                                                if (readerSockstat6 != null) {
                                                                }
                                                                if (readerSockstat2 != null) {
                                                                }
                                                                if (readerSockstat62 != null) {
                                                                }
                                                                if (readerSockstat2 != null) {
                                                                }
                                                                if (bufferSNMP2 != null) {
                                                                }
                                                                if (bufferSockstat4 != null) {
                                                                }
                                                                if (bufferSockstat6 != null) {
                                                                }
                                                                now3 = now4;
                                                                currTcpEstablishedCount = currTcpEstablishedCount3;
                                                                tcpInUseCount = tcpInUseCount2;
                                                                timeWaitCount = timeWaitCount3;
                                                                inSegCount = inSegCount2;
                                                                inSegErrorCount = inSegErrorCount2;
                                                                retransSegCount = retransSegCount2;
                                                                diffRetransSegCount = diffRetransSegCount2;
                                                                diffInSegErrorCount = diffInSegErrorCount2;
                                                                diffOutSegCount = diffOutSegCount2;
                                                                diffInSegCount = diffInSegCount2;
                                                                checkBackhaul = false;
                                                                outSegCount = outSegCount2;
                                                                String log222222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                                if (!checkBackhaul) {
                                                                }
                                                                Log.d(TAG, "Backhaul result - " + log222222222);
                                                                this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                                this.mPrevTimeWaitCount = timeWaitCount;
                                                                this.mPrevTcpInUseCount = tcpInUseCount;
                                                                this.mPrevRetranSegCount = retransSegCount;
                                                                this.mPrevInSegErrorCount = inSegErrorCount;
                                                                this.mPreviousRx = rxCurr;
                                                                this.mPreviousTx = txCurr;
                                                                this.mPrevInSegCount = inSegCount;
                                                                this.mPrevOutSegCount = outSegCount;
                                                                if (checkBackhaul) {
                                                                }
                                                                removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                                sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                                return;
                                                            } catch (Throwable th11) {
                                                                readerSockstat63 = readerSockstat64;
                                                                readerSockstat6 = readerSNMP;
                                                                str = "Exception: ";
                                                                th = th11;
                                                                if (readerSockstat6 != null) {
                                                                }
                                                                if (readerSockstat != null) {
                                                                }
                                                                if (readerSockstat63 != null) {
                                                                }
                                                                if (readerSockstat != null) {
                                                                }
                                                                if (bufferSNMP != null) {
                                                                }
                                                                if (bufferSockstat4 != null) {
                                                                }
                                                                if (bufferSockstat6 != null) {
                                                                }
                                                                throw th;
                                                            }
                                                        }
                                                    } catch (Exception e12) {
                                                        e = e12;
                                                        str = "Exception: ";
                                                        readerSockstat62 = readerSockstat64;
                                                        bufferSNMP2 = bufferSNMP;
                                                        readerSockstat2 = readerSockstat;
                                                        currTcpEstablishedCount3 = currTcpEstablishedCount2;
                                                        timeWaitCount3 = lineNumber;
                                                        readerSockstat6 = readerSNMP;
                                                        Log.w(TAG, str + e);
                                                        e.printStackTrace();
                                                        if (readerSockstat6 != null) {
                                                        }
                                                        if (readerSockstat2 != null) {
                                                        }
                                                        if (readerSockstat62 != null) {
                                                        }
                                                        if (readerSockstat2 != null) {
                                                        }
                                                        if (bufferSNMP2 != null) {
                                                        }
                                                        if (bufferSockstat4 != null) {
                                                        }
                                                        if (bufferSockstat6 != null) {
                                                        }
                                                        now3 = now4;
                                                        currTcpEstablishedCount = currTcpEstablishedCount3;
                                                        tcpInUseCount = tcpInUseCount2;
                                                        timeWaitCount = timeWaitCount3;
                                                        inSegCount = inSegCount2;
                                                        inSegErrorCount = inSegErrorCount2;
                                                        retransSegCount = retransSegCount2;
                                                        diffRetransSegCount = diffRetransSegCount2;
                                                        diffInSegErrorCount = diffInSegErrorCount2;
                                                        diffOutSegCount = diffOutSegCount2;
                                                        diffInSegCount = diffInSegCount2;
                                                        checkBackhaul = false;
                                                        outSegCount = outSegCount2;
                                                        String log2222222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                        if (!checkBackhaul) {
                                                        }
                                                        Log.d(TAG, "Backhaul result - " + log2222222222);
                                                        this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                        this.mPrevTimeWaitCount = timeWaitCount;
                                                        this.mPrevTcpInUseCount = tcpInUseCount;
                                                        this.mPrevRetranSegCount = retransSegCount;
                                                        this.mPrevInSegErrorCount = inSegErrorCount;
                                                        this.mPreviousRx = rxCurr;
                                                        this.mPreviousTx = txCurr;
                                                        this.mPrevInSegCount = inSegCount;
                                                        this.mPrevOutSegCount = outSegCount;
                                                        if (checkBackhaul) {
                                                        }
                                                        removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                        sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                        return;
                                                    } catch (Throwable th12) {
                                                        str = "Exception: ";
                                                        readerSockstat63 = readerSockstat64;
                                                        readerSockstat6 = readerSNMP;
                                                        th = th12;
                                                        if (readerSockstat6 != null) {
                                                        }
                                                        if (readerSockstat != null) {
                                                        }
                                                        if (readerSockstat63 != null) {
                                                        }
                                                        if (readerSockstat != null) {
                                                        }
                                                        if (bufferSNMP != null) {
                                                        }
                                                        if (bufferSockstat4 != null) {
                                                        }
                                                        if (bufferSockstat6 != null) {
                                                        }
                                                        throw th;
                                                    }
                                                }
                                                tcpInUseCount = tcpInUseCount2 - orphanCount;
                                                try {
                                                    diffInSegCount2 = inSegCount2 - this.mPrevInSegCount;
                                                    diffOutSegCount2 = outSegCount2 - this.mPrevOutSegCount;
                                                    diffInSegErrorCount2 = inSegErrorCount2 - this.mPrevInSegErrorCount;
                                                    diffRetransSegCount2 = retransSegCount2 - this.mPrevRetranSegCount;
                                                    if ((diffInSegErrorCount2 > 0 && diffInSegCount2 + diffOutSegCount2 < 15) || this.mInErrorSegWaitingCycle > 0) {
                                                        try {
                                                            this.mInErrorSegWaitingCycle++;
                                                        } catch (Exception e13) {
                                                            e = e13;
                                                            bufferSNMP2 = bufferSNMP;
                                                            currTcpEstablishedCount3 = currTcpEstablishedCount2;
                                                            timeWaitCount3 = lineNumber;
                                                            tcpInUseCount2 = tcpInUseCount;
                                                            readerSockstat2 = readerSockstat;
                                                            str = "Exception: ";
                                                            readerSockstat62 = readerSockstat64;
                                                            readerSockstat6 = readerSNMP;
                                                        } catch (Throwable th13) {
                                                            readerSockstat63 = readerSockstat64;
                                                            readerSockstat6 = readerSNMP;
                                                            str = "Exception: ";
                                                            th = th13;
                                                            if (readerSockstat6 != null) {
                                                            }
                                                            if (readerSockstat != null) {
                                                            }
                                                            if (readerSockstat63 != null) {
                                                            }
                                                            if (readerSockstat != null) {
                                                            }
                                                            if (bufferSNMP != null) {
                                                            }
                                                            if (bufferSockstat4 != null) {
                                                            }
                                                            if (bufferSockstat6 != null) {
                                                            }
                                                            throw th;
                                                        }
                                                    }
                                                    if ((diffRetransSegCount2 > 0 && diffInSegCount2 == 0) || this.mRetransSegWaitingCycle > 0) {
                                                        this.mRetransSegWaitingCycle++;
                                                    }
                                                    if (currTcpEstablishedCount2 > this.mPrevTcpEstablishedCount || this.mPrevQcPassed) {
                                                        this.mInternetConnectivityCounter = 0;
                                                        this.mInternetConnectivityWaitingCylce = 0;
                                                        this.mInErrorSegWaitingCycle = 0;
                                                        this.mRetransSegWaitingCycle = 0;
                                                    } else if (lineNumber > this.mPrevTimeWaitCount && waitingCycleThreshold > 2) {
                                                        this.mInternetConnectivityCounter = 0;
                                                        this.mInternetConnectivityWaitingCylce = 0;
                                                    } else if (tcpInUseCount > this.mPrevTcpInUseCount) {
                                                        this.mInternetConnectivityCounter += tcpInUseCount - this.mPrevTcpInUseCount;
                                                    }
                                                    if (this.mInternetConnectivityCounter > 0) {
                                                        this.mInternetConnectivityWaitingCylce++;
                                                    }
                                                    if (this.mPrevQcPassed) {
                                                        i2 = 0;
                                                        this.mPrevQcPassed = false;
                                                    } else {
                                                        i2 = 0;
                                                    }
                                                    this.mBackhaulDetectionReason = i2;
                                                    if (this.mInternetConnectivityCounter > internetConnectivityCounterThreshold && this.mInternetConnectivityWaitingCylce > waitingCycleThreshold) {
                                                        Log.d(TAG, "Backhaul Disconnection due to TCP Sockets");
                                                        this.mBackhaulDetectionReason++;
                                                    }
                                                    if (this.mRetransSegWaitingCycle > waitingCycleThreshold) {
                                                        Log.d(TAG, "Backhaul Disconnection due to RetransSeg");
                                                        this.mBackhaulDetectionReason += 2;
                                                    }
                                                    if (this.mInErrorSegWaitingCycle > waitingCycleThreshold) {
                                                        Log.d(TAG, "Backhaul Disconnection due to InErrorSeg");
                                                        this.mBackhaulDetectionReason += 4;
                                                    }
                                                    if (this.mInternetConnectivityWaitingCylce > 60) {
                                                        Log.d(TAG, "Backhaul Disconnection due to no response from network");
                                                        this.mBackhaulDetectionReason += 8;
                                                    }
                                                    if (this.mBackhaulDetectionReason > 0) {
                                                        checkBackhaul2 = true;
                                                        currTcpEstablishedCount = currTcpEstablishedCount2;
                                                        timeWaitCount2 = lineNumber;
                                                    } else {
                                                        currTcpEstablishedCount = currTcpEstablishedCount2;
                                                        timeWaitCount2 = lineNumber;
                                                        try {
                                                            if (now4 - this.mBackhaulLastDnsCheckTime > 60000) {
                                                                try {
                                                                    if (WifiConnectivityMonitor.DBG) {
                                                                        Log.d(TAG, "Do 1 min DNS check");
                                                                    }
                                                                    checkBackhaul2 = true;
                                                                    this.mBackhaulDetectionReason = 16;
                                                                } catch (Exception e14) {
                                                                    e = e14;
                                                                    readerSockstat62 = readerSockstat64;
                                                                    bufferSNMP2 = bufferSNMP;
                                                                    tcpInUseCount2 = tcpInUseCount;
                                                                    readerSockstat2 = readerSockstat;
                                                                    currTcpEstablishedCount3 = currTcpEstablishedCount;
                                                                    timeWaitCount3 = timeWaitCount2;
                                                                    readerSockstat6 = readerSNMP;
                                                                    str = "Exception: ";
                                                                    Log.w(TAG, str + e);
                                                                    e.printStackTrace();
                                                                    if (readerSockstat6 != null) {
                                                                    }
                                                                    if (readerSockstat2 != null) {
                                                                    }
                                                                    if (readerSockstat62 != null) {
                                                                    }
                                                                    if (readerSockstat2 != null) {
                                                                    }
                                                                    if (bufferSNMP2 != null) {
                                                                    }
                                                                    if (bufferSockstat4 != null) {
                                                                    }
                                                                    if (bufferSockstat6 != null) {
                                                                    }
                                                                    now3 = now4;
                                                                    currTcpEstablishedCount = currTcpEstablishedCount3;
                                                                    tcpInUseCount = tcpInUseCount2;
                                                                    timeWaitCount = timeWaitCount3;
                                                                    inSegCount = inSegCount2;
                                                                    inSegErrorCount = inSegErrorCount2;
                                                                    retransSegCount = retransSegCount2;
                                                                    diffRetransSegCount = diffRetransSegCount2;
                                                                    diffInSegErrorCount = diffInSegErrorCount2;
                                                                    diffOutSegCount = diffOutSegCount2;
                                                                    diffInSegCount = diffInSegCount2;
                                                                    checkBackhaul = false;
                                                                    outSegCount = outSegCount2;
                                                                    String log22222222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                                    if (!checkBackhaul) {
                                                                    }
                                                                    Log.d(TAG, "Backhaul result - " + log22222222222);
                                                                    this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                                    this.mPrevTimeWaitCount = timeWaitCount;
                                                                    this.mPrevTcpInUseCount = tcpInUseCount;
                                                                    this.mPrevRetranSegCount = retransSegCount;
                                                                    this.mPrevInSegErrorCount = inSegErrorCount;
                                                                    this.mPreviousRx = rxCurr;
                                                                    this.mPreviousTx = txCurr;
                                                                    this.mPrevInSegCount = inSegCount;
                                                                    this.mPrevOutSegCount = outSegCount;
                                                                    if (checkBackhaul) {
                                                                    }
                                                                    removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                                    sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                                    return;
                                                                } catch (Throwable th14) {
                                                                    readerSockstat63 = readerSockstat64;
                                                                    readerSockstat6 = readerSNMP;
                                                                    str = "Exception: ";
                                                                    th = th14;
                                                                    if (readerSockstat6 != null) {
                                                                    }
                                                                    if (readerSockstat != null) {
                                                                    }
                                                                    if (readerSockstat63 != null) {
                                                                    }
                                                                    if (readerSockstat != null) {
                                                                    }
                                                                    if (bufferSNMP != null) {
                                                                    }
                                                                    if (bufferSockstat4 != null) {
                                                                    }
                                                                    if (bufferSockstat6 != null) {
                                                                    }
                                                                    throw th;
                                                                }
                                                            }
                                                        } catch (Exception e15) {
                                                            e = e15;
                                                            str = "Exception: ";
                                                            readerSockstat62 = readerSockstat64;
                                                            bufferSNMP2 = bufferSNMP;
                                                            tcpInUseCount2 = tcpInUseCount;
                                                            readerSockstat2 = readerSockstat;
                                                            currTcpEstablishedCount3 = currTcpEstablishedCount;
                                                            timeWaitCount3 = timeWaitCount2;
                                                            readerSockstat6 = readerSNMP;
                                                            Log.w(TAG, str + e);
                                                            e.printStackTrace();
                                                            if (readerSockstat6 != null) {
                                                            }
                                                            if (readerSockstat2 != null) {
                                                            }
                                                            if (readerSockstat62 != null) {
                                                            }
                                                            if (readerSockstat2 != null) {
                                                            }
                                                            if (bufferSNMP2 != null) {
                                                            }
                                                            if (bufferSockstat4 != null) {
                                                            }
                                                            if (bufferSockstat6 != null) {
                                                            }
                                                            now3 = now4;
                                                            currTcpEstablishedCount = currTcpEstablishedCount3;
                                                            tcpInUseCount = tcpInUseCount2;
                                                            timeWaitCount = timeWaitCount3;
                                                            inSegCount = inSegCount2;
                                                            inSegErrorCount = inSegErrorCount2;
                                                            retransSegCount = retransSegCount2;
                                                            diffRetransSegCount = diffRetransSegCount2;
                                                            diffInSegErrorCount = diffInSegErrorCount2;
                                                            diffOutSegCount = diffOutSegCount2;
                                                            diffInSegCount = diffInSegCount2;
                                                            checkBackhaul = false;
                                                            outSegCount = outSegCount2;
                                                            String log222222222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                            if (!checkBackhaul) {
                                                            }
                                                            Log.d(TAG, "Backhaul result - " + log222222222222);
                                                            this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                            this.mPrevTimeWaitCount = timeWaitCount;
                                                            this.mPrevTcpInUseCount = tcpInUseCount;
                                                            this.mPrevRetranSegCount = retransSegCount;
                                                            this.mPrevInSegErrorCount = inSegErrorCount;
                                                            this.mPreviousRx = rxCurr;
                                                            this.mPreviousTx = txCurr;
                                                            this.mPrevInSegCount = inSegCount;
                                                            this.mPrevOutSegCount = outSegCount;
                                                            if (checkBackhaul) {
                                                            }
                                                            removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                            sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                            return;
                                                        } catch (Throwable th15) {
                                                            str = "Exception: ";
                                                            readerSockstat63 = readerSockstat64;
                                                            readerSockstat6 = readerSNMP;
                                                            th = th15;
                                                            if (readerSockstat6 != null) {
                                                            }
                                                            if (readerSockstat != null) {
                                                            }
                                                            if (readerSockstat63 != null) {
                                                            }
                                                            if (readerSockstat != null) {
                                                            }
                                                            if (bufferSNMP != null) {
                                                            }
                                                            if (bufferSockstat4 != null) {
                                                            }
                                                            if (bufferSockstat6 != null) {
                                                            }
                                                            throw th;
                                                        }
                                                    }
                                                    try {
                                                        readerSNMP.close();
                                                        readerSockstat.close();
                                                        readerSockstat64.close();
                                                        readerSockstat.close();
                                                        bufferSNMP.close();
                                                        bufferSockstat4.close();
                                                        bufferSockstat6.close();
                                                    } catch (IOException e16) {
                                                        Log.w(TAG, "Exception: " + e16);
                                                        e16.printStackTrace();
                                                    }
                                                    timeWaitCount = timeWaitCount2;
                                                    retransSegCount = retransSegCount2;
                                                    diffRetransSegCount = diffRetransSegCount2;
                                                    diffInSegErrorCount = diffInSegErrorCount2;
                                                    diffInSegCount = diffInSegCount2;
                                                    diffOutSegCount = diffOutSegCount2;
                                                    checkBackhaul = checkBackhaul2;
                                                    now3 = now4;
                                                    inSegCount = inSegCount2;
                                                    outSegCount = outSegCount2;
                                                    inSegErrorCount = inSegErrorCount2;
                                                } catch (Exception e17) {
                                                    e = e17;
                                                    str = "Exception: ";
                                                    readerSockstat62 = readerSockstat64;
                                                    bufferSNMP2 = bufferSNMP;
                                                    tcpInUseCount2 = tcpInUseCount;
                                                    readerSockstat2 = readerSockstat;
                                                    currTcpEstablishedCount3 = currTcpEstablishedCount2;
                                                    timeWaitCount3 = lineNumber;
                                                    readerSockstat6 = readerSNMP;
                                                    Log.w(TAG, str + e);
                                                    e.printStackTrace();
                                                    if (readerSockstat6 != null) {
                                                    }
                                                    if (readerSockstat2 != null) {
                                                    }
                                                    if (readerSockstat62 != null) {
                                                    }
                                                    if (readerSockstat2 != null) {
                                                    }
                                                    if (bufferSNMP2 != null) {
                                                    }
                                                    if (bufferSockstat4 != null) {
                                                    }
                                                    if (bufferSockstat6 != null) {
                                                    }
                                                    now3 = now4;
                                                    currTcpEstablishedCount = currTcpEstablishedCount3;
                                                    tcpInUseCount = tcpInUseCount2;
                                                    timeWaitCount = timeWaitCount3;
                                                    inSegCount = inSegCount2;
                                                    inSegErrorCount = inSegErrorCount2;
                                                    retransSegCount = retransSegCount2;
                                                    diffRetransSegCount = diffRetransSegCount2;
                                                    diffInSegErrorCount = diffInSegErrorCount2;
                                                    diffOutSegCount = diffOutSegCount2;
                                                    diffInSegCount = diffInSegCount2;
                                                    checkBackhaul = false;
                                                    outSegCount = outSegCount2;
                                                    String log2222222222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                    if (!checkBackhaul) {
                                                    }
                                                    Log.d(TAG, "Backhaul result - " + log2222222222222);
                                                    this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                    this.mPrevTimeWaitCount = timeWaitCount;
                                                    this.mPrevTcpInUseCount = tcpInUseCount;
                                                    this.mPrevRetranSegCount = retransSegCount;
                                                    this.mPrevInSegErrorCount = inSegErrorCount;
                                                    this.mPreviousRx = rxCurr;
                                                    this.mPreviousTx = txCurr;
                                                    this.mPrevInSegCount = inSegCount;
                                                    this.mPrevOutSegCount = outSegCount;
                                                    if (checkBackhaul) {
                                                    }
                                                    removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                    sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                    return;
                                                } catch (Throwable th16) {
                                                    str = "Exception: ";
                                                    readerSockstat63 = readerSockstat64;
                                                    readerSockstat6 = readerSNMP;
                                                    th = th16;
                                                    if (readerSockstat6 != null) {
                                                    }
                                                    if (readerSockstat != null) {
                                                    }
                                                    if (readerSockstat63 != null) {
                                                    }
                                                    if (readerSockstat != null) {
                                                    }
                                                    if (bufferSNMP != null) {
                                                    }
                                                    if (bufferSockstat4 != null) {
                                                    }
                                                    if (bufferSockstat6 != null) {
                                                    }
                                                    throw th;
                                                }
                                            } catch (Exception e18) {
                                                e = e18;
                                                diffRxCurr = diffRxCurr2;
                                                diffTxCurr = diffTxCurr3;
                                                str = "Exception: ";
                                                readerSockstat62 = readerSockstat64;
                                                bufferSNMP2 = bufferSNMP;
                                                readerSockstat2 = readerSockstat;
                                                readerSockstat6 = readerSNMP;
                                                Log.w(TAG, str + e);
                                                e.printStackTrace();
                                                if (readerSockstat6 != null) {
                                                }
                                                if (readerSockstat2 != null) {
                                                }
                                                if (readerSockstat62 != null) {
                                                }
                                                if (readerSockstat2 != null) {
                                                }
                                                if (bufferSNMP2 != null) {
                                                }
                                                if (bufferSockstat4 != null) {
                                                }
                                                if (bufferSockstat6 != null) {
                                                }
                                                now3 = now4;
                                                currTcpEstablishedCount = currTcpEstablishedCount3;
                                                tcpInUseCount = tcpInUseCount2;
                                                timeWaitCount = timeWaitCount3;
                                                inSegCount = inSegCount2;
                                                inSegErrorCount = inSegErrorCount2;
                                                retransSegCount = retransSegCount2;
                                                diffRetransSegCount = diffRetransSegCount2;
                                                diffInSegErrorCount = diffInSegErrorCount2;
                                                diffOutSegCount = diffOutSegCount2;
                                                diffInSegCount = diffInSegCount2;
                                                checkBackhaul = false;
                                                outSegCount = outSegCount2;
                                                String log22222222222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                                if (!checkBackhaul) {
                                                }
                                                Log.d(TAG, "Backhaul result - " + log22222222222222);
                                                this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                                this.mPrevTimeWaitCount = timeWaitCount;
                                                this.mPrevTcpInUseCount = tcpInUseCount;
                                                this.mPrevRetranSegCount = retransSegCount;
                                                this.mPrevInSegErrorCount = inSegErrorCount;
                                                this.mPreviousRx = rxCurr;
                                                this.mPreviousTx = txCurr;
                                                this.mPrevInSegCount = inSegCount;
                                                this.mPrevOutSegCount = outSegCount;
                                                if (checkBackhaul) {
                                                }
                                                removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                                sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                                return;
                                            } catch (Throwable th17) {
                                                str = "Exception: ";
                                                readerSockstat63 = readerSockstat64;
                                                readerSockstat6 = readerSNMP;
                                                th = th17;
                                                if (readerSockstat6 != null) {
                                                }
                                                if (readerSockstat != null) {
                                                }
                                                if (readerSockstat63 != null) {
                                                }
                                                if (readerSockstat != null) {
                                                }
                                                if (bufferSNMP != null) {
                                                }
                                                if (bufferSockstat4 != null) {
                                                }
                                                if (bufferSockstat6 != null) {
                                                }
                                                throw th;
                                            }
                                        } catch (Exception e19) {
                                            e = e19;
                                            diffRxCurr = diffRxCurr2;
                                            diffTxCurr = diffTxCurr3;
                                            str = "Exception: ";
                                            readerSockstat62 = readerSockstat64;
                                            readerSockstat2 = readerSockstat;
                                            readerSockstat6 = readerSNMP;
                                            Log.w(TAG, str + e);
                                            e.printStackTrace();
                                            if (readerSockstat6 != null) {
                                            }
                                            if (readerSockstat2 != null) {
                                            }
                                            if (readerSockstat62 != null) {
                                            }
                                            if (readerSockstat2 != null) {
                                            }
                                            if (bufferSNMP2 != null) {
                                            }
                                            if (bufferSockstat4 != null) {
                                            }
                                            if (bufferSockstat6 != null) {
                                            }
                                            now3 = now4;
                                            currTcpEstablishedCount = currTcpEstablishedCount3;
                                            tcpInUseCount = tcpInUseCount2;
                                            timeWaitCount = timeWaitCount3;
                                            inSegCount = inSegCount2;
                                            inSegErrorCount = inSegErrorCount2;
                                            retransSegCount = retransSegCount2;
                                            diffRetransSegCount = diffRetransSegCount2;
                                            diffInSegErrorCount = diffInSegErrorCount2;
                                            diffOutSegCount = diffOutSegCount2;
                                            diffInSegCount = diffInSegCount2;
                                            checkBackhaul = false;
                                            outSegCount = outSegCount2;
                                            String log222222222222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                            if (!checkBackhaul) {
                                            }
                                            Log.d(TAG, "Backhaul result - " + log222222222222222);
                                            this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                            this.mPrevTimeWaitCount = timeWaitCount;
                                            this.mPrevTcpInUseCount = tcpInUseCount;
                                            this.mPrevRetranSegCount = retransSegCount;
                                            this.mPrevInSegErrorCount = inSegErrorCount;
                                            this.mPreviousRx = rxCurr;
                                            this.mPreviousTx = txCurr;
                                            this.mPrevInSegCount = inSegCount;
                                            this.mPrevOutSegCount = outSegCount;
                                            if (checkBackhaul) {
                                            }
                                            removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                            sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                            return;
                                        } catch (Throwable th18) {
                                            str = "Exception: ";
                                            readerSockstat63 = readerSockstat64;
                                            readerSockstat6 = readerSNMP;
                                            bufferSNMP = null;
                                            th = th18;
                                            if (readerSockstat6 != null) {
                                            }
                                            if (readerSockstat != null) {
                                            }
                                            if (readerSockstat63 != null) {
                                            }
                                            if (readerSockstat != null) {
                                            }
                                            if (bufferSNMP != null) {
                                            }
                                            if (bufferSockstat4 != null) {
                                            }
                                            if (bufferSockstat6 != null) {
                                            }
                                            throw th;
                                        }
                                    } catch (Exception e20) {
                                        e = e20;
                                        diffRxCurr = diffRxCurr2;
                                        diffTxCurr = diffTxCurr3;
                                        str = "Exception: ";
                                        readerSockstat2 = readerSockstat;
                                        readerSockstat6 = readerSNMP;
                                        readerSockstat62 = null;
                                        Log.w(TAG, str + e);
                                        e.printStackTrace();
                                        if (readerSockstat6 != null) {
                                        }
                                        if (readerSockstat2 != null) {
                                        }
                                        if (readerSockstat62 != null) {
                                        }
                                        if (readerSockstat2 != null) {
                                        }
                                        if (bufferSNMP2 != null) {
                                        }
                                        if (bufferSockstat4 != null) {
                                        }
                                        if (bufferSockstat6 != null) {
                                        }
                                        now3 = now4;
                                        currTcpEstablishedCount = currTcpEstablishedCount3;
                                        tcpInUseCount = tcpInUseCount2;
                                        timeWaitCount = timeWaitCount3;
                                        inSegCount = inSegCount2;
                                        inSegErrorCount = inSegErrorCount2;
                                        retransSegCount = retransSegCount2;
                                        diffRetransSegCount = diffRetransSegCount2;
                                        diffInSegErrorCount = diffInSegErrorCount2;
                                        diffOutSegCount = diffOutSegCount2;
                                        diffInSegCount = diffInSegCount2;
                                        checkBackhaul = false;
                                        outSegCount = outSegCount2;
                                        String log2222222222222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                        if (!checkBackhaul) {
                                        }
                                        Log.d(TAG, "Backhaul result - " + log2222222222222222);
                                        this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                        this.mPrevTimeWaitCount = timeWaitCount;
                                        this.mPrevTcpInUseCount = tcpInUseCount;
                                        this.mPrevRetranSegCount = retransSegCount;
                                        this.mPrevInSegErrorCount = inSegErrorCount;
                                        this.mPreviousRx = rxCurr;
                                        this.mPreviousTx = txCurr;
                                        this.mPrevInSegCount = inSegCount;
                                        this.mPrevOutSegCount = outSegCount;
                                        if (checkBackhaul) {
                                        }
                                        removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                        sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                        return;
                                    } catch (Throwable th19) {
                                        str = "Exception: ";
                                        readerSockstat6 = readerSNMP;
                                        bufferSNMP = null;
                                        th = th19;
                                        if (readerSockstat6 != null) {
                                        }
                                        if (readerSockstat != null) {
                                        }
                                        if (readerSockstat63 != null) {
                                        }
                                        if (readerSockstat != null) {
                                        }
                                        if (bufferSNMP != null) {
                                        }
                                        if (bufferSockstat4 != null) {
                                        }
                                        if (bufferSockstat6 != null) {
                                        }
                                        throw th;
                                    }
                                } catch (Exception e21) {
                                    e = e21;
                                    rxCurr = rxCurr2;
                                    diffRxCurr = diffRxCurr2;
                                    diffTxCurr = diffTxCurr3;
                                    str = "Exception: ";
                                    readerSockstat6 = null;
                                    readerSockstat2 = null;
                                    readerSockstat62 = null;
                                    Log.w(TAG, str + e);
                                    e.printStackTrace();
                                    if (readerSockstat6 != null) {
                                    }
                                    if (readerSockstat2 != null) {
                                    }
                                    if (readerSockstat62 != null) {
                                    }
                                    if (readerSockstat2 != null) {
                                    }
                                    if (bufferSNMP2 != null) {
                                    }
                                    if (bufferSockstat4 != null) {
                                    }
                                    if (bufferSockstat6 != null) {
                                    }
                                    now3 = now4;
                                    currTcpEstablishedCount = currTcpEstablishedCount3;
                                    tcpInUseCount = tcpInUseCount2;
                                    timeWaitCount = timeWaitCount3;
                                    inSegCount = inSegCount2;
                                    inSegErrorCount = inSegErrorCount2;
                                    retransSegCount = retransSegCount2;
                                    diffRetransSegCount = diffRetransSegCount2;
                                    diffInSegErrorCount = diffInSegErrorCount2;
                                    diffOutSegCount = diffOutSegCount2;
                                    diffInSegCount = diffInSegCount2;
                                    checkBackhaul = false;
                                    outSegCount = outSegCount2;
                                    String log22222222222222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                    if (!checkBackhaul) {
                                    }
                                    Log.d(TAG, "Backhaul result - " + log22222222222222222);
                                    this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                    this.mPrevTimeWaitCount = timeWaitCount;
                                    this.mPrevTcpInUseCount = tcpInUseCount;
                                    this.mPrevRetranSegCount = retransSegCount;
                                    this.mPrevInSegErrorCount = inSegErrorCount;
                                    this.mPreviousRx = rxCurr;
                                    this.mPreviousTx = txCurr;
                                    this.mPrevInSegCount = inSegCount;
                                    this.mPrevOutSegCount = outSegCount;
                                    if (checkBackhaul) {
                                    }
                                    removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                    sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                    return;
                                } catch (Throwable th20) {
                                    str = "Exception: ";
                                    readerSockstat6 = null;
                                    readerSockstat = null;
                                    bufferSNMP = null;
                                    th = th20;
                                    if (readerSockstat6 != null) {
                                    }
                                    if (readerSockstat != null) {
                                    }
                                    if (readerSockstat63 != null) {
                                    }
                                    if (readerSockstat != null) {
                                    }
                                    if (bufferSNMP != null) {
                                    }
                                    if (bufferSockstat4 != null) {
                                    }
                                    if (bufferSockstat6 != null) {
                                    }
                                    throw th;
                                }
                                String log222222222222222222 = "RSSI:" + currRssi + ", CE:" + currTcpEstablishedCount + ", PE:" + this.mPrevTcpEstablishedCount + ", TI:" + tcpInUseCount + ", PTI:" + this.mPrevTcpInUseCount + ", TW:" + timeWaitCount + ", PTW:" + this.mPrevTimeWaitCount + ", Tx:" + diffTxCurr + ", Rx:" + diffRxCurr + ", TxS:" + diffOutSegCount + ", RxS:" + diffInSegCount + ", RESULT:" + checkBackhaul + ", IC:" + this.mInternetConnectivityCounter + ", ICT:" + internetConnectivityCounterThreshold + ", WC:" + this.mInternetConnectivityWaitingCylce + ", WCT:" + waitingCycleThreshold + ", R:" + diffRetransSegCount + ", RC:" + this.mRetransSegWaitingCycle + ", IE:" + diffInSegErrorCount + ", EC:" + this.mInErrorSegWaitingCycle;
                                if (!checkBackhaul || WifiConnectivityMonitor.DBG) {
                                    Log.d(TAG, "Backhaul result - " + log222222222222222222);
                                }
                                this.mPrevTcpEstablishedCount = currTcpEstablishedCount;
                                this.mPrevTimeWaitCount = timeWaitCount;
                                this.mPrevTcpInUseCount = tcpInUseCount;
                                this.mPrevRetranSegCount = retransSegCount;
                                this.mPrevInSegErrorCount = inSegErrorCount;
                                this.mPreviousRx = rxCurr;
                                this.mPreviousTx = txCurr;
                                this.mPrevInSegCount = inSegCount;
                                this.mPrevOutSegCount = outSegCount;
                                int interval = checkBackhaul ? 10000 : 1000;
                                removeMessages(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START);
                                sendEmptyMessageDelayed(WifiConnectivityMonitor.TCP_BACKHAUL_DETECTION_START, (long) interval);
                                return;
                            } else {
                                return;
                            }
                        default:
                            switch (i3) {
                                case WifiConnectivityMonitor.EVENT_SCAN_STARTED /*{ENCODED_INT: 135229}*/:
                                case WifiConnectivityMonitor.EVENT_SCAN_TIMEOUT /*{ENCODED_INT: 135231}*/:
                                    if (!WifiConnectivityMonitor.this.isConnectedState() && (this.mPollingStarted || this.mDnsQueried)) {
                                        sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_STOP);
                                        return;
                                    }
                                    removeMessages(msg.what);
                                    if (!this.mDnsQueried) {
                                        if (WifiConnectivityMonitor.DBG) {
                                            Log.d(TAG, "[" + msg.what + "] DNS query ongoing. -> Pass the next result");
                                        }
                                        this.mDnsInterrupted = true;
                                        return;
                                    }
                                    return;
                                case WifiConnectivityMonitor.EVENT_SCAN_COMPLETE /*{ENCODED_INT: 135230}*/:
                                    removeMessages(WifiConnectivityMonitor.EVENT_SCAN_TIMEOUT);
                                    sendEmptyMessage(WifiConnectivityMonitor.ACTIVITY_CHECK_STOP);
                                    return;
                                default:
                                    switch (i3) {
                                        case WifiConnectivityMonitor.EVENT_NETWORK_PROPERTIES_CHANGED /*{ENCODED_INT: 135235}*/:
                                        case WifiConnectivityMonitor.EVENT_DHCP_SESSION_STARTED /*{ENCODED_INT: 135236}*/:
                                        case WifiConnectivityMonitor.EVENT_DHCP_SESSION_COMPLETE /*{ENCODED_INT: 135237}*/:
                                            break;
                                        default:
                                            switch (i3) {
                                                case WifiConnectivityMonitor.EVENT_ROAM_STARTED /*{ENCODED_INT: 135241}*/:
                                                case WifiConnectivityMonitor.EVENT_ROAM_COMPLETE /*{ENCODED_INT: 135242}*/:
                                                    break;
                                                default:
                                                    Log.e(TAG, "Ignore msg id : " + msg.what);
                                                    return;
                                            }
                                    }
                                    removeMessages(msg.what);
                                    if (!this.mDnsQueried) {
                                    }
                                    break;
                            }
                    }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendMessageToWcmStateMachine(Message msg) {
        sendMessage(msg);
    }

    /* access modifiers changed from: package-private */
    public final class DnsThread extends Thread {
        private static final int DNS_DEFAULT_TIMEOUT_MS = 3000;
        private static final String TAG = "WifiConnectivityMonitor.DnsThread";
        private final int DNS_TIMEOUT = -1;
        private final CountDownLatch latch = new CountDownLatch(1);
        private boolean mAlreadyFinished = false;
        private final Handler mCallBackHandler;
        private DnsPingerHandler mDnsPingerHandler = null;
        private final boolean mForce;
        private InetAddress mForcedCheckAddress = null;
        private int mForcedCheckResult = 3;
        private int mForcedCheckRtt = -1;
        private final InetAddressThread mInetAddressThread;
        private long mTimeout = 3000;
        private String mUrl;

        public DnsThread(boolean force, String url, Handler handler, long timeout) {
            this.mInetAddressThread = new InetAddressThread(url);
            this.mCallBackHandler = handler;
            if (timeout >= 1000) {
                this.mTimeout = timeout;
            }
            this.mForce = force;
            this.mUrl = url;
        }

        /* JADX WARNING: Code restructure failed: missing block: B:12:0x0088, code lost:
            if (r1 != null) goto L_0x008a;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:13:0x008a, code lost:
            r1.finish();
            r14.mDnsPingerHandler = null;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:14:0x008f, code lost:
            r0.quit();
            r0.interrupt();
         */
        /* JADX WARNING: Code restructure failed: missing block: B:15:0x0096, code lost:
            return;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:24:0x00cc, code lost:
            if (r1 == null) goto L_0x008f;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:26:0x00d1, code lost:
            if (r1 == null) goto L_0x008f;
         */
        public void run() {
            DnsPingerHandler dnsPingerHandler;
            WifiConnectivityMonitor.this.mAnalyticsDisconnectReason = 0;
            if (this.mForce) {
                HandlerThread dnsPingerThread = new HandlerThread("dnsPingerThread");
                dnsPingerThread.start();
                try {
                    this.mDnsPingerHandler = new DnsPingerHandler(dnsPingerThread.getLooper(), this.mCallBackHandler, getId());
                    this.mDnsPingerHandler.sendDnsPing(this.mUrl, this.mTimeout);
                    if (!this.latch.await(this.mTimeout, TimeUnit.MILLISECONDS)) {
                        if (WifiConnectivityMonitor.DBG) {
                            Log.d(TAG, "DNS_CHECK_TIMEOUT [" + getId() + "-F] - latch timeout");
                        }
                        this.mCallBackHandler.sendMessage(WifiConnectivityMonitor.this.obtainMessage(WifiConnectivityMonitor.RESULT_DNS_CHECK, 3, -1, null));
                    } else {
                        this.mCallBackHandler.sendMessage(WifiConnectivityMonitor.this.obtainMessage(WifiConnectivityMonitor.RESULT_DNS_CHECK, this.mForcedCheckResult, this.mForcedCheckRtt, this.mForcedCheckAddress));
                    }
                    dnsPingerHandler = this.mDnsPingerHandler;
                } catch (Exception e) {
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(TAG, "DNS_CHECK_TIMEOUT [" + getId() + "-F] " + e);
                    }
                    this.mCallBackHandler.sendMessage(WifiConnectivityMonitor.this.obtainMessage(WifiConnectivityMonitor.RESULT_DNS_CHECK, 3, -1, null));
                    dnsPingerHandler = this.mDnsPingerHandler;
                } catch (Throwable th) {
                    dnsPingerHandler = this.mDnsPingerHandler;
                }
            } else {
                Stopwatch dnsTimer = new Stopwatch().start();
                try {
                    this.mInetAddressThread.start();
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(TAG, "wait mInetAddress result [" + this.mInetAddressThread.getId() + "]");
                    }
                    boolean result = this.latch.await(this.mTimeout, TimeUnit.MILLISECONDS);
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(TAG, "latch result : " + result);
                    }
                    if (!result) {
                        if (WifiConnectivityMonitor.DBG) {
                            Log.d(TAG, "DNS_CHECK_TIMEOUT [" + getId() + "]");
                        }
                        this.mAlreadyFinished = true;
                        this.mCallBackHandler.sendMessage(WifiConnectivityMonitor.this.obtainMessage(WifiConnectivityMonitor.RESULT_DNS_CHECK, 3, -1, null));
                        dnsTimer.stop();
                        return;
                    }
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(TAG, "send DNS CHECK Result [" + getId() + "]");
                    }
                    Handler handler = this.mCallBackHandler;
                    if (handler != null) {
                        handler.sendMessage(WifiConnectivityMonitor.this.obtainMessage(WifiConnectivityMonitor.RESULT_DNS_CHECK, this.mInetAddressThread.getType(), (int) dnsTimer.stop(), this.mInetAddressThread.getResultIp()));
                    } else {
                        Log.d(TAG, "There is no callback handler");
                    }
                } catch (InterruptedException e2) {
                    Log.d(TAG, "InterruptedException [" + getId() + "]");
                    if (!this.mAlreadyFinished) {
                        this.mAlreadyFinished = true;
                        this.mCallBackHandler.sendMessage(WifiConnectivityMonitor.this.obtainMessage(WifiConnectivityMonitor.RESULT_DNS_CHECK, 3, -1, null));
                        dnsTimer.stop();
                    }
                }
            }
        }

        final class InetAddressThread extends Thread {
            private static final String TAG = "WifiConnectivityMonitor.InetAddressThread";
            private final String mHostToResolve;
            private volatile InetAddress mResultIp = null;
            private volatile int mResultType = 0;

            public InetAddressThread(String url) {
                this.mHostToResolve = url;
            }

            public InetAddress getResultIp() {
                return this.mResultIp;
            }

            public int getType() {
                return this.mResultType;
            }

            public void run() {
                char c = 2;
                int i = 1;
                try {
                    if (WifiConnectivityMonitor.this.mNetwork == null) {
                        this.mResultType = 9;
                        if (WifiConnectivityMonitor.DBG) {
                            Log.d(TAG, "already disconnected!");
                        }
                        DnsThread.this.latch.countDown();
                        return;
                    }
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(TAG, "DNS requested, Host : " + this.mHostToResolve);
                    }
                    InetAddress[] addresses = WifiConnectivityMonitor.this.mNetwork.getAllByName(this.mHostToResolve);
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(TAG, "DNS response arrived from InetThread [" + getId() + "]");
                    }
                    if (!DnsThread.this.mAlreadyFinished) {
                        DnsThread.this.mAlreadyFinished = true;
                        int length = addresses.length;
                        InetAddress resultIpv6 = null;
                        int i2 = 0;
                        while (i2 < length) {
                            InetAddress address = addresses[i2];
                            if (address instanceof Inet4Address) {
                                int ipByte_1st = address.getAddress()[0] & 255;
                                int ipByte_2nd = address.getAddress()[i] & 255;
                                int ipByte_3rd = address.getAddress()[c] & 255;
                                int ipByte_4th = address.getAddress()[3] & 255;
                                if (ipByte_1st == 10 || ((ipByte_1st == 192 && ipByte_2nd == 168) || ((ipByte_1st == 172 && ipByte_2nd >= 16 && ipByte_2nd <= 31) || (ipByte_1st == i && ipByte_2nd == 33 && ipByte_3rd == 203 && ipByte_4th == 39)))) {
                                    if (WifiConnectivityMonitor.DBG) {
                                        WifiConnectivityMonitor.this.log(this.mHostToResolve + " - Dns Response with private Network IP Address !!! - " + ipByte_1st + "." + ipByte_2nd + "." + ipByte_3rd + "." + ipByte_4th);
                                    }
                                    this.mResultIp = address;
                                    this.mResultType = 2;
                                    if (WifiConnectivityMonitor.DBG) {
                                        Log.d(TAG, "DNS_CHECK_RESULT_PRIVATE_IP: " + this.mResultIp.toString());
                                    }
                                } else {
                                    this.mResultIp = address;
                                    this.mResultType = 0;
                                    if (WifiConnectivityMonitor.DBG) {
                                        Log.d(TAG, "DNS_CHECK_RESULT_SUCCESS: " + this.mResultIp.toString());
                                    }
                                    DnsThread.this.latch.countDown();
                                    return;
                                }
                            } else {
                                resultIpv6 = address;
                            }
                            i2++;
                            c = 2;
                            i = 1;
                        }
                        if (this.mResultIp == null && resultIpv6 != null) {
                            if (WifiConnectivityMonitor.DBG) {
                                Log.d(TAG, "Dns Response with IPv6");
                            }
                            this.mResultIp = resultIpv6;
                            this.mResultType = 0;
                            if (WifiConnectivityMonitor.DBG) {
                                Log.d(TAG, "DNS_CHECK_RESULT_SUCCESS: " + this.mResultIp.toString());
                            }
                        }
                        DnsThread.this.latch.countDown();
                    } else if (WifiConnectivityMonitor.DBG) {
                        Log.d(TAG, "already finished");
                    }
                } catch (UnknownHostException uhe) {
                    String message = uhe.getLocalizedMessage();
                    if (message == null || !message.contains("DNS service refused")) {
                        if (WifiConnectivityMonitor.DBG) {
                            Log.d(TAG, "DNS_CHECK_RESULT_UNKNOWNHOSTEXCEPTION");
                        }
                        this.mResultType = 6;
                    } else {
                        if (WifiConnectivityMonitor.DBG) {
                            Log.d(TAG, "DNS_CHECK_RESULT_NO_INTERNET");
                        }
                        WifiConnectivityMonitor.this.mAnalyticsDisconnectReason = 2;
                        this.mResultType = 1;
                    }
                    DnsThread.this.latch.countDown();
                } catch (SecurityException se) {
                    if (WifiConnectivityMonitor.DBG) {
                        Log.e(TAG, "SecurityException : " + se);
                    }
                    this.mResultType = 7;
                    DnsThread.this.latch.countDown();
                } catch (NullPointerException ne) {
                    if (WifiConnectivityMonitor.DBG) {
                        Log.e(TAG, "NullPointerException : " + ne);
                    }
                    this.mResultType = 8;
                    DnsThread.this.latch.countDown();
                }
            }
        }

        private class DnsPingerHandler extends Handler {
            Handler mCallbackHandler;
            private DnsCheck mDnsPingerCheck;
            long mId;

            public DnsPingerHandler(Looper looper, Handler callbackHandler, long id) {
                super(looper);
                this.mDnsPingerCheck = new DnsCheck(this, "WifiConnectivityMonitor.DnsPingerHandler");
                this.mCallbackHandler = callbackHandler;
                this.mId = id;
            }

            public void sendDnsPing(String url, long timeout) {
                if (!this.mDnsPingerCheck.requestDnsQuerying(1, (int) timeout, url)) {
                    if (WifiConnectivityMonitor.DBG) {
                        Log.e(DnsThread.TAG, "DNS List is empty, need to check quality");
                    }
                    if (DnsThread.this.mCallBackHandler != null) {
                        DnsThread.this.mCallBackHandler.sendMessage(obtainMessage(WifiConnectivityMonitor.RESULT_DNS_CHECK, 3, -1, null));
                        DnsThread.this.latch.countDown();
                    }
                }
            }

            /* access modifiers changed from: private */
            /* access modifiers changed from: public */
            private void finish() {
                this.mDnsPingerCheck.quit();
                this.mDnsPingerCheck = null;
            }

            public void handleMessage(Message msg) {
                int i = msg.what;
                if (i == 593920 || i == 593925) {
                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                        Log.i(DnsThread.TAG, "[DNS_PING_RESULT_SPECIFIC]");
                    }
                    DnsCheck dnsCheck = this.mDnsPingerCheck;
                    if (dnsCheck != null) {
                        try {
                            int dnsResult = dnsCheck.checkDnsResult(msg.arg1, msg.arg2, 1);
                            if (dnsResult != 10) {
                                if (WifiConnectivityMonitor.DBG) {
                                    Log.d(DnsThread.TAG, "send DNS CHECK Result [" + this.mId + "]");
                                }
                                DnsThread.this.mForcedCheckResult = dnsResult;
                                DnsThread.this.mForcedCheckRtt = msg.arg2;
                                DnsThread.this.mForcedCheckAddress = (InetAddress) msg.obj;
                                DnsThread.this.latch.countDown();
                            } else if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                Log.d(DnsThread.TAG, "wait until the responses about remained DNS Request arrive!");
                            }
                        } catch (NullPointerException ne) {
                            Log.e(DnsThread.TAG, "DnsPingerHandler - " + ne);
                        }
                    }
                } else {
                    Log.e(DnsThread.TAG, "Ignore msg id : " + msg.what);
                }
            }
        }

        /* access modifiers changed from: package-private */
        public class DnsCheck {
            private int[] mDnsCheckSuccesses;
            private String mDnsCheckTAG = null;
            private List<InetAddress> mDnsList;
            private DnsPinger mDnsPinger;
            private String[] mDnsResponseStrs;
            private List<InetAddress> mDnsServerList = null;
            private HashMap<Integer, Integer> mIdDnsMap = new HashMap<>();

            public DnsCheck(Handler handler, String tag) {
                this.mDnsPinger = new DnsPinger(WifiConnectivityMonitor.this.mContext, tag, handler.getLooper(), handler, 1);
                this.mDnsCheckTAG = tag;
                this.mDnsPinger.setCurrentLinkProperties(WifiConnectivityMonitor.this.mLinkProperties);
            }

            public boolean requestDnsQuerying(int num, int timeoutMS, String url) {
                List<InetAddress> dnses;
                boolean requested = false;
                this.mDnsList = new ArrayList();
                if (!(WifiConnectivityMonitor.this.mLinkProperties == null || (dnses = WifiConnectivityMonitor.this.mLinkProperties.getDnsServers()) == null || dnses.size() == 0)) {
                    this.mDnsServerList = new ArrayList(dnses);
                }
                List<InetAddress> dnses2 = this.mDnsServerList;
                if (dnses2 != null) {
                    this.mDnsList.addAll(dnses2);
                }
                int numDnses = this.mDnsList.size();
                this.mDnsCheckSuccesses = new int[numDnses];
                this.mDnsResponseStrs = new String[numDnses];
                for (int i = 0; i < numDnses; i++) {
                    this.mDnsResponseStrs[i] = "";
                }
                WifiInfo info = WifiConnectivityMonitor.this.syncGetCurrentWifiInfo();
                if (WifiConnectivityMonitor.DBG) {
                    try {
                        Log.d(DnsThread.TAG, String.format("Pinging %s on ssid [%s]: ", this.mDnsList, info.getSSID()));
                    } catch (Exception e) {
                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                            e.printStackTrace();
                        }
                    }
                }
                this.mIdDnsMap.clear();
                for (int i2 = 0; i2 < num; i2++) {
                    for (int j = 0; j < numDnses; j++) {
                        try {
                            if (this.mDnsList.get(j) == null || this.mDnsList.get(j).isLoopbackAddress()) {
                                Log.d(DnsThread.TAG, "Loopback address (::1) is detected at DNS" + j);
                            } else {
                                if (url == null) {
                                    HashMap<Integer, Integer> hashMap = this.mIdDnsMap;
                                    Objects.requireNonNull(WifiConnectivityMonitor.this.mParam);
                                    Objects.requireNonNull(WifiConnectivityMonitor.this.mParam);
                                    hashMap.put(Integer.valueOf(this.mDnsPinger.pingDnsAsync(this.mDnsList.get(j), timeoutMS, (i2 * 0) + 100)), Integer.valueOf(j));
                                } else {
                                    HashMap<Integer, Integer> hashMap2 = this.mIdDnsMap;
                                    Objects.requireNonNull(WifiConnectivityMonitor.this.mParam);
                                    Objects.requireNonNull(WifiConnectivityMonitor.this.mParam);
                                    hashMap2.put(Integer.valueOf(this.mDnsPinger.pingDnsAsyncSpecificForce(this.mDnsList.get(j), timeoutMS, (i2 * 0) + 100, url)), Integer.valueOf(j));
                                }
                                requested = true;
                            }
                        } catch (IndexOutOfBoundsException e2) {
                            if (WifiConnectivityMonitor.DBG) {
                                Log.i(DnsThread.TAG, "IndexOutOfBoundsException");
                            }
                        }
                    }
                }
                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                    Log.i(DnsThread.TAG, "[REQUEST] " + this.mDnsCheckTAG + " : " + this.mIdDnsMap);
                }
                return requested;
            }

            public int checkDnsResult(int pingID, int pingResponseTime, int minDnsResponse) {
                int rssi;
                int result = checkDnsResultCore(pingID, pingResponseTime, minDnsResponse);
                if (result == 10) {
                    return result;
                }
                if (result == 3 && WifiConnectivityMonitor.this.mWifiInfo != null && (rssi = WifiConnectivityMonitor.this.mWifiInfo.getRssi()) >= -50) {
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(DnsThread.TAG, "Dns Timeout but RSSI high : " + rssi + " dBm. Link is okay and DNS service is not responsive. -> NO_INTERNET");
                    }
                    result = 5;
                }
                WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.CMD_UPDATE_CURRENT_BSSID_ON_DNS_RESULT, result, pingResponseTime);
                return result;
            }

            public int checkDnsResultCore(int pingID, int pingResponseTime, int minDnsResponse) {
                Integer dnsServerId = this.mIdDnsMap.get(Integer.valueOf(pingID));
                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                    Log.i(DnsThread.TAG, "[RESPONSE] " + this.mDnsCheckTAG + " : " + this.mIdDnsMap);
                }
                if (dnsServerId == null) {
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(DnsThread.TAG, "Skip a Dns response with ID - " + pingID);
                    }
                    return 10;
                }
                int[] iArr = this.mDnsCheckSuccesses;
                if (iArr == null || iArr.length <= dnsServerId.intValue()) {
                    Log.e(DnsThread.TAG, "Not available to check dns results");
                    quit();
                    WifiConnectivityMonitor.this.mCurrentQcFail.error = 3;
                    WifiConnectivityMonitor.this.mCurrentQcFail.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                    return 3;
                }
                this.mIdDnsMap.remove(Integer.valueOf(pingID));
                if (pingResponseTime >= 0) {
                    int[] iArr2 = this.mDnsCheckSuccesses;
                    int intValue = dnsServerId.intValue();
                    iArr2[intValue] = iArr2[intValue] + 1;
                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                        Log.e(DnsThread.TAG, "mDnsCheckSuccesses[" + dnsServerId + "] " + this.mDnsCheckSuccesses[dnsServerId.intValue()]);
                    }
                }
                try {
                    if (this.mDnsResponseStrs == null) {
                        Log.e(DnsThread.TAG, "mDnsResponseStrs is null");
                    } else if (pingResponseTime >= 0) {
                        StringBuilder sb = new StringBuilder();
                        String[] strArr = this.mDnsResponseStrs;
                        int intValue2 = dnsServerId.intValue();
                        sb.append(strArr[intValue2]);
                        sb.append("|");
                        sb.append(pingResponseTime);
                        strArr[intValue2] = sb.toString();
                    } else {
                        StringBuilder sb2 = new StringBuilder();
                        String[] strArr2 = this.mDnsResponseStrs;
                        int intValue3 = dnsServerId.intValue();
                        sb2.append(strArr2[intValue3]);
                        sb2.append("|x");
                        strArr2[intValue3] = sb2.toString();
                    }
                    if (this.mDnsCheckSuccesses[dnsServerId.intValue()] >= minDnsResponse) {
                        if (WifiConnectivityMonitor.DBG) {
                            Log.d(DnsThread.TAG, makeLogString() + "  SUCCESS");
                        } else {
                            Log.d(DnsThread.TAG, makeLogString());
                        }
                        quit();
                        if (pingResponseTime != 2) {
                            return 0;
                        }
                        WifiConnectivityMonitor.this.mCurrentQcFail.error = 1;
                        WifiConnectivityMonitor.this.mCurrentQcFail.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                        WifiConnectivityMonitor.this.mAnalyticsDisconnectReason = 1;
                        return 2;
                    } else if (pingResponseTime == -3) {
                        List<Integer> removePingIdList = new ArrayList<>();
                        for (Map.Entry<Integer, Integer> ent : this.mIdDnsMap.entrySet()) {
                            if (dnsServerId.equals(ent.getValue())) {
                                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                    Log.d(DnsThread.TAG, "checkDnsResult - Ping# " + ent.getKey() + " to DnsServer " + ent.getValue() + " (removed)");
                                }
                                removePingIdList.add(ent.getKey());
                            } else if (WifiConnectivityMonitor.SMARTCM_DBG) {
                                Log.d(DnsThread.TAG, "checkDnsResult - Ping# " + ent.getKey() + " to DnsServer# " + ent.getValue());
                            }
                        }
                        for (Integer removeId : removePingIdList) {
                            this.mIdDnsMap.remove(removeId);
                        }
                        if (!this.mIdDnsMap.isEmpty()) {
                            return 10;
                        }
                        if (WifiConnectivityMonitor.DBG) {
                            Log.e(DnsThread.TAG, "DNS gets no results");
                        }
                        if (WifiConnectivityMonitor.DBG) {
                            Log.d(DnsThread.TAG, makeLogString() + "  FAILURE ");
                        } else {
                            Log.d(DnsThread.TAG, makeLogString());
                        }
                        quit();
                        WifiConnectivityMonitor.this.mCurrentQcFail.error = 2;
                        WifiConnectivityMonitor.this.mCurrentQcFail.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                        return 1;
                    } else if (!this.mIdDnsMap.isEmpty()) {
                        return 10;
                    } else {
                        if (WifiConnectivityMonitor.DBG) {
                            Log.e(DnsThread.TAG, "DNS Checking FAILURE");
                        }
                        if (WifiConnectivityMonitor.DBG) {
                            Log.d(DnsThread.TAG, makeLogString() + "  FAILURE");
                        } else {
                            Log.d(DnsThread.TAG, makeLogString());
                        }
                        quit();
                        WifiConnectivityMonitor.this.mCurrentQcFail.error = 4;
                        WifiConnectivityMonitor.this.mCurrentQcFail.line = Thread.currentThread().getStackTrace()[2].getLineNumber();
                        WifiConnectivityMonitor.this.mCurrentQcFail.currentDnsList = this.mDnsServerList;
                        return 3;
                    }
                } catch (IndexOutOfBoundsException e) {
                    Log.i(DnsThread.TAG, "mDnsResponseStrs IndexOutOfBoundsException");
                    return 3;
                }
            }

            public void quit() {
                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                    Log.i(DnsThread.TAG, "[quit] " + this.mDnsCheckTAG);
                }
                this.mIdDnsMap.clear();
                this.mDnsPinger.cancelPings();
            }

            private void clear() {
                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                    Log.i(DnsThread.TAG, "[clear] " + this.mDnsCheckTAG);
                }
                this.mDnsPinger.clear();
            }

            public boolean isDnsCheckOngoing() {
                HashMap<Integer, Integer> hashMap = this.mIdDnsMap;
                if (hashMap == null || hashMap.isEmpty()) {
                    return false;
                }
                return true;
            }

            private String makeLogString() {
                String logStr = "";
                String[] strArr = this.mDnsResponseStrs;
                if (strArr != null) {
                    for (String respStr : strArr) {
                        logStr = logStr + " [" + respStr + "]";
                    }
                }
                return logStr;
            }
        }
    }

    public int checkDnsThreadResult(int resultType, int responseTime) {
        WifiInfo wifiInfo;
        int rssi;
        Log.d(TAG, "DNS resultType : " + resultType + ", responseTime : " + responseTime);
        if (!this.mIsUsingProxy || resultType == 0) {
            if (resultType == 3 && (wifiInfo = this.mWifiInfo) != null && (rssi = wifiInfo.getRssi()) >= -50) {
                if (DBG) {
                    Log.d(TAG, "Dns Timeout but RSSI high : " + rssi + " dBm. Link is okay and DNG service is not responsive. -> NO_INTERNET");
                }
                resultType = 5;
            }
            sendMessage(CMD_UPDATE_CURRENT_BSSID_ON_DNS_RESULT_TYPE, resultType, responseTime);
            return resultType;
        }
        Log.d(TAG, "DNS check result is not successful. TYPE: " + resultType + " Proxy is being used. Ignore the result.");
        return 11;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean initDataUsage() {
        try {
            NetworkInfo activeNetwork = getCm().getActiveNetworkInfo();
            boolean isMobile = false;
            if (activeNetwork != null) {
                boolean z = activeNetwork.isConnectedOrConnecting();
                isMobile = activeNetwork.getType() == 0;
            }
            State currentState = getCurrentState();
            if ((!isValidState() || currentState == this.mValidNoCheckState) && !isMobile && this.mStatsService != null && this.mStatsSession != null) {
                return true;
            }
            if (this.mStatsService == null) {
                this.mStatsService = INetworkStatsService.Stub.asInterface(ServiceManager.getService("netstats"));
            }
            if (this.mStatsSession == null) {
                try {
                    this.mStatsSession = this.mStatsService.openSession();
                } catch (Exception e) {
                    e.printStackTrace();
                    this.mStatsSession = null;
                    return false;
                }
            }
            return true;
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    private long requestDataUsage(int networkType, int uid) {
        NetworkTemplate mNetworkTemplate;
        if (networkType == 0) {
            mNetworkTemplate = NetworkTemplate.buildTemplateMobileAll(getSubscriberId(networkType));
        } else if (networkType != 1) {
            return -1;
        } else {
            mNetworkTemplate = NetworkTemplate.buildTemplateWifiWildcard();
        }
        try {
            NetworkStatsHistory networkStatsHistory = collectHistoryForUid(mNetworkTemplate, uid, -1);
            if (DBG) {
                Log.i(TAG, "load:: " + networkType + " :: [uid-" + uid + "] getTotalBytes : " + Formatter.formatFileSize(this.mContext, networkStatsHistory.getTotalBytes()));
            }
            return networkStatsHistory.getTotalBytes();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private String getSubscriberId(int networkType) {
        if (networkType == 0) {
            return ((TelephonyManager) this.mContext.getSystemService("phone")).getSubscriberId();
        }
        return "";
    }

    private NetworkStatsHistory collectHistoryForUid(NetworkTemplate template, int uid, int set) throws RemoteException {
        initDataUsage();
        return this.mStatsSession.getHistoryForUid(template, uid, set, 0, 10);
    }

    /* access modifiers changed from: private */
    public class VolumeWeightedEMA {
        private final double mAlpha;
        private double mProduct = WifiConnectivityMonitor.POOR_LINK_MIN_VOLUME;
        private double mValue = WifiConnectivityMonitor.POOR_LINK_MIN_VOLUME;
        private double mVolume = WifiConnectivityMonitor.POOR_LINK_MIN_VOLUME;

        public VolumeWeightedEMA(double coefficient) {
            this.mAlpha = coefficient;
        }

        public void update(double newValue, int newVolume) {
            if (newVolume > 0) {
                double d = this.mAlpha;
                this.mProduct = (d * ((double) newVolume) * newValue) + ((1.0d - d) * this.mProduct);
                this.mVolume = (((double) newVolume) * d) + ((1.0d - d) * this.mVolume);
                this.mValue = this.mProduct / this.mVolume;
            }
        }
    }

    /* access modifiers changed from: private */
    public class BssidStatistics {
        private final String mBssid;
        private long mBssidAvoidTimeMax;
        public boolean mBssidNoInternet = false;
        public HashMap<Integer, RssiLevelQosInfo> mBssidQosMap = new HashMap<>();
        public ScanResult mCurrentBssidScanInfo = null;
        private boolean mDnsAvailable;
        public int mEnhancedTargetRssi = 0;
        private VolumeWeightedEMA[] mEntries;
        private int mEntriesSize;
        private int mGoodLinkTargetCount;
        private int mGoodLinkTargetIndex;
        private int mGoodLinkTargetRssi;
        public boolean mIsCaptivePortal = false;
        private int mLastGoodRxRssi;
        private int mLastPoorReason;
        private int mLastPoorRssi;
        private long mLastTimeGood;
        private long mLastTimePoor;
        private long mLastTimeSample;
        public int mLatestDnsResult = WifiConnectivityMonitor.this.BSSID_DNS_RESULT_UNKNOWN;
        public int mLatestLevel2Rssi = -99;
        public int mLatestQcFailRssi = -99;
        private long[] mMaxStreamTP;
        private long[] mMaxThroughput;
        public int mNumberOfConnections;
        private int mRssiBase;
        public String mSsid;
        private int netId;

        static /* synthetic */ int access$13120(BssidStatistics x0, int x1) {
            int i = x0.mGoodLinkTargetRssi - x1;
            x0.mGoodLinkTargetRssi = i;
            return i;
        }

        static /* synthetic */ int access$20720(BssidStatistics x0, int x1) {
            int i = x0.mLastPoorRssi - x1;
            x0.mLastPoorRssi = i;
            return i;
        }

        public BssidStatistics(String bssid, int netId2) {
            this.mBssid = bssid;
            this.netId = netId2;
            this.mRssiBase = WifiConnectivityMonitor.BSSID_STAT_RANGE_LOW_DBM;
            this.mEntriesSize = 61;
            this.mEntries = new VolumeWeightedEMA[this.mEntriesSize];
            for (int i = 0; i < this.mEntriesSize; i++) {
                this.mEntries[i] = new VolumeWeightedEMA(WifiConnectivityMonitor.EXP_COEFFICIENT_RECORD);
            }
            this.mMaxThroughput = new long[100];
            this.mMaxStreamTP = new long[100];
            for (int i2 = 0; i2 < 100; i2++) {
                this.mMaxThroughput[i2] = 0;
                this.mMaxStreamTP[i2] = 0;
            }
            this.mGoodLinkTargetRssi = -200;
            this.mBssidAvoidTimeMax = 0;
            this.mLastGoodRxRssi = 0;
            this.mLastPoorRssi = -200;
            this.mLastPoorReason = 1;
            this.mDnsAvailable = false;
            this.mNumberOfConnections = 0;
        }

        public void updateLoss(int rssi, double value, int volume) {
            int index;
            if (volume > 0 && (index = rssi - this.mRssiBase) >= 0 && index < this.mEntriesSize) {
                this.mEntries[index].update(value, volume);
                if (rssi >= this.mLastGoodRxRssi && value >= 0.2d && this.mEntries[index].mValue >= WifiConnectivityMonitor.EXP_COEFFICIENT_RECORD) {
                    this.mLastGoodRxRssi = 0;
                    Log.d(WifiConnectivityMonitor.TAG, "lose good rx position : " + rssi + " loss=" + value);
                }
                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                    DecimalFormat df = new DecimalFormat("#.##");
                    Log.d(WifiConnectivityMonitor.TAG, "Cache updated: loss[" + rssi + "]=" + df.format(this.mEntries[index].mValue * 100.0d) + "% volume=" + df.format(this.mEntries[index].mVolume));
                }
            }
        }

        public void updateGoodRssi(int rssi) {
            if (rssi < this.mLastGoodRxRssi) {
                this.mLastGoodRxRssi = rssi;
                int i = this.mGoodLinkTargetRssi;
                int i2 = this.mLastGoodRxRssi;
                if (i > i2) {
                    this.mGoodLinkTargetRssi = i2;
                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                        Log.i(WifiConnectivityMonitor.TAG, "lower mGoodLinkTargetRssi : " + this.mLastPoorRssi);
                    }
                }
                int i3 = this.mLastPoorRssi;
                if (i3 >= this.mLastGoodRxRssi) {
                    this.mLastPoorRssi = i3 - 3;
                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                        Log.i(WifiConnectivityMonitor.TAG, "lower mLastPoorRssi : " + this.mLastPoorRssi);
                    }
                }
                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                    Log.i(WifiConnectivityMonitor.TAG, "new good RSSI : " + rssi);
                }
                if (WifiConnectivityMonitor.SMARTCM_DBG) {
                    Context context = WifiConnectivityMonitor.this.mContext;
                    Toast.makeText(context, "new good RSSI : " + rssi, 0).show();
                }
            }
        }

        public void updateMaxThroughput(int rssi, long tput, boolean isStreaming) {
            if (-100 < rssi && rssi < 0) {
                if (isStreaming) {
                    long[] jArr = this.mMaxStreamTP;
                    if (jArr[-rssi] < tput) {
                        jArr[-rssi] = tput;
                        if (WifiConnectivityMonitor.SMARTCM_DBG) {
                            Log.i(WifiConnectivityMonitor.TAG, "new Max stream TP[" + rssi + "] : " + tput);
                            return;
                        }
                        return;
                    }
                    return;
                }
                long[] jArr2 = this.mMaxThroughput;
                if (jArr2[-rssi] < tput) {
                    jArr2[-rssi] = tput;
                    if (WifiConnectivityMonitor.SMARTCM_DBG) {
                        Log.i(WifiConnectivityMonitor.TAG, "new Max TP[" + rssi + "] : " + tput);
                    }
                }
            }
        }

        public double presetLoss(int rssi) {
            if (rssi <= -90) {
                return 1.0d;
            }
            if (rssi > 0) {
                return WifiConnectivityMonitor.POOR_LINK_MIN_VOLUME;
            }
            if (WifiConnectivityMonitor.sPresetLoss == null) {
                double[] unused = WifiConnectivityMonitor.sPresetLoss = new double[90];
                for (int i = 0; i < 90; i++) {
                    WifiConnectivityMonitor.sPresetLoss[i] = 1.0d / Math.pow((double) (90 - i), 1.5d);
                }
            }
            return WifiConnectivityMonitor.sPresetLoss[-rssi];
        }

        public boolean poorLinkDetected(int rssi, int extraInfo) {
            if (!WifiConnectivityMonitor.this.mClientModeImpl.isConnected()) {
                Log.i(WifiConnectivityMonitor.TAG, "already disconnected");
                return true;
            } else if (extraInfo == 2) {
                return true;
            } else {
                this.mLastPoorReason = extraInfo;
                this.mLastPoorRssi = rssi;
                poorLinkDetected(rssi);
                if (WifiConnectivityMonitor.SMARTCM_DBG && rssi <= WifiConnectivityMonitor.BSSID_STAT_RANGE_HIGH_DBM && rssi > -100 && rssi <= 0) {
                    Log.d(WifiConnectivityMonitor.TAG, "[" + rssi + "] loss=" + this.mEntries[rssi - this.mRssiBase].mValue + ", maxTP=" + this.mMaxThroughput[-rssi] + ", maxStream=" + this.mMaxStreamTP[-rssi]);
                }
                this.mBssidAvoidTimeMax = SystemClock.elapsedRealtime() + 300000;
                if (WifiConnectivityMonitor.DBG) {
                    Log.d(WifiConnectivityMonitor.TAG, "Poor link detected enhanced recovery, avoidMax=" + 300000L + ", mBssidAvoidTimeMax=" + this.mBssidAvoidTimeMax);
                }
                if (this.mGoodLinkTargetRssi < -82) {
                    this.mGoodLinkTargetRssi = -82;
                }
                if (!WifiConnectivityMonitor.this.isValidState() || this.mGoodLinkTargetRssi - rssi >= 10) {
                    int i = this.mEnhancedTargetRssi;
                    Objects.requireNonNull(WifiConnectivityMonitor.this.mParam);
                    if (i != 5) {
                        this.mEnhancedTargetRssi = 0;
                    }
                } else {
                    Objects.requireNonNull(WifiConnectivityMonitor.this.mParam);
                    this.mEnhancedTargetRssi = 5;
                    this.mGoodLinkTargetRssi += this.mEnhancedTargetRssi;
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "mGoodLinkTargetRssi is updated : " + this.mGoodLinkTargetRssi);
                    }
                }
                return true;
            }
        }

        public boolean poorLinkDetected(int rssi) {
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, "Poor link detected, rssi=" + rssi);
            }
            long now = SystemClock.elapsedRealtime();
            long lastGood = now - this.mLastTimeGood;
            long lastPoor = now - this.mLastTimePoor;
            int from = (WifiConnectivityMonitor.this.mCurrentMode == 3 ? 3 : 5) + rssi;
            int i = WifiConnectivityMonitor.this.mCurrentMode == 3 ? 15 : 20;
            WifiConnectivityMonitor.this.mParam.mLastPoorDetectedTime = now;
            int newRssiTarget = findRssiTarget(from, i + rssi, WifiConnectivityMonitor.GOOD_LINK_LOSS_THRESHOLD);
            if (newRssiTarget > this.mGoodLinkTargetRssi) {
                this.mGoodLinkTargetRssi = newRssiTarget;
            }
            this.mGoodLinkTargetCount = 8;
            this.mBssidAvoidTimeMax = now + 30000;
            Log.d(WifiConnectivityMonitor.TAG, "goodRssi=" + this.mGoodLinkTargetRssi + " goodCount=" + this.mGoodLinkTargetCount + " lastGood=" + lastGood + " lastPoor=" + lastPoor + " avoidMax=" + 30000L);
            return true;
        }

        public void newLinkDetected() {
            long now = SystemClock.elapsedRealtime();
            WifiInfo info = WifiConnectivityMonitor.this.mWifiInfo;
            if (this.mBssidAvoidTimeMax > now) {
                if (WifiConnectivityMonitor.DBG) {
                    Log.d(WifiConnectivityMonitor.TAG, "Previous avoidance still in effect, rssi=" + this.mGoodLinkTargetRssi + " count=" + this.mGoodLinkTargetCount);
                }
                if (this.mBssidAvoidTimeMax >= now + 30000) {
                    return;
                }
                if (info == null || info.getRssi() <= -64) {
                    this.mBssidAvoidTimeMax = 120000 + now;
                } else {
                    this.mBssidAvoidTimeMax = 30000 + now;
                }
            } else {
                this.mDnsAvailable = false;
                if (this.mGoodLinkTargetRssi > -200) {
                    this.mGoodLinkTargetCount = 5;
                } else {
                    this.mGoodLinkTargetRssi = findRssiTarget(WifiConnectivityMonitor.BSSID_STAT_RANGE_LOW_DBM, WifiConnectivityMonitor.BSSID_STAT_RANGE_HIGH_DBM, WifiConnectivityMonitor.GOOD_LINK_LOSS_THRESHOLD);
                    this.mGoodLinkTargetCount = 0;
                }
                this.mBssidAvoidTimeMax = now;
                if (WifiConnectivityMonitor.DBG) {
                    Log.d(WifiConnectivityMonitor.TAG, "New link verifying target set, rssi=" + this.mGoodLinkTargetRssi + " count=" + this.mGoodLinkTargetCount);
                }
            }
        }

        public int findRssiTarget(int from, int to, double threshold) {
            if (this.mGoodLinkTargetRssi == -200) {
                Log.d(WifiConnectivityMonitor.TAG, "Scan target found: initial rssi=-90");
                return -90;
            } else if (WifiConnectivityMonitor.this.mCurrentMode == 3) {
                return findAGGRssiTarget(from, to, WifiConnectivityMonitor.this.mParam.mPassBytesAggressiveMode);
            } else {
                int i = this.mRssiBase;
                int from2 = from - i;
                int to2 = to - i;
                int emptyCount = 0;
                int d = from2 < to2 ? 1 : -1;
                for (int i2 = from2; i2 != to2; i2 += d) {
                    if (i2 < 0 || i2 >= this.mEntriesSize || this.mEntries[i2].mVolume <= 1.0d) {
                        emptyCount++;
                        if (emptyCount >= 3) {
                            int rssi = this.mRssiBase + i2;
                            double lossPreset = presetLoss(rssi);
                            if (lossPreset < threshold) {
                                if (WifiConnectivityMonitor.DBG) {
                                    DecimalFormat df = new DecimalFormat("#.##");
                                    Log.d(WifiConnectivityMonitor.TAG, "Scan target found: rssi=" + rssi + " threshold=" + df.format(threshold * 100.0d) + "% value=" + df.format(100.0d * lossPreset) + "% volume=preset");
                                }
                                return rssi;
                            }
                        } else {
                            continue;
                        }
                    } else {
                        emptyCount = 0;
                        if (this.mEntries[i2].mValue < threshold) {
                            int rssi2 = this.mRssiBase + i2;
                            if (WifiConnectivityMonitor.DBG) {
                                DecimalFormat df2 = new DecimalFormat("#.##");
                                Log.d(WifiConnectivityMonitor.TAG, "Scan target found: rssi=" + rssi2 + " threshold=" + df2.format(threshold * 100.0d) + "% value=" + df2.format(this.mEntries[i2].mValue * 100.0d) + "% volume=" + df2.format(this.mEntries[i2].mVolume));
                            }
                            return rssi2;
                        }
                    }
                }
                return this.mRssiBase + to2;
            }
        }

        /* JADX WARNING: Code restructure failed: missing block: B:33:0x0096, code lost:
            if (com.android.server.wifi.WifiConnectivityMonitor.SMARTCM_DBG == false) goto L_0x00ac;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:34:0x0098, code lost:
            android.util.Log.i(com.android.server.wifi.WifiConnectivityMonitor.TAG, "found max TP RSSI : " + r6);
         */
        /* JADX WARNING: Code restructure failed: missing block: B:35:0x00ac, code lost:
            r2 = r6;
         */
        private int findAGGRssiTarget(int from, int to, int threshold) {
            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                for (int i = from; i <= to; i++) {
                    if (i > -100) {
                        Log.i(WifiConnectivityMonitor.TAG, "W[" + i + "] : " + this.mMaxThroughput[-i]);
                        Log.i(WifiConnectivityMonitor.TAG, "S[" + i + "] : " + this.mMaxStreamTP[-i]);
                    }
                }
            }
            if (from >= WifiConnectivityMonitor.BSSID_STAT_RANGE_HIGH_DBM) {
                return from;
            }
            if (to > WifiConnectivityMonitor.BSSID_STAT_RANGE_HIGH_DBM) {
                to = WifiConnectivityMonitor.BSSID_STAT_RANGE_HIGH_DBM;
            }
            if (from < -99) {
                from = -99;
            }
            int target = from + 2;
            int streamThreshold = threshold * 10;
            int i2 = this.mRssiBase;
            int from2 = from - i2;
            int to2 = to - i2;
            int d = from2 < to2 ? 1 : -1;
            int i3 = from2;
            while (true) {
                if (i3 == to2) {
                    break;
                }
                int rssi = this.mRssiBase + i3;
                if (rssi < -99) {
                    rssi = -99;
                }
                if (rssi > 0) {
                    rssi = 0;
                }
                if (((long) threshold) > this.mMaxThroughput[-rssi] && ((long) streamThreshold) > this.mMaxStreamTP[-rssi]) {
                    i3 += d;
                }
            }
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, "Scan target found: rssi=" + target + " threshold=" + threshold + " maxTP=" + this.mMaxThroughput[-target] + " strema threshold=" + streamThreshold + " maxTP=" + this.mMaxStreamTP[-target]);
            } else {
                Log.d(WifiConnectivityMonitor.TAG, "Scan target found: rssi=" + target);
            }
            return target;
        }

        public void updateBssidQosMapOnScan() {
            int currFreqPrimary;
            int scanFreqPrimary;
            if (WifiConnectivityMonitor.this.mWifiInfo != null) {
                int rssi = WifiConnectivityMonitor.this.mWifiInfo.getRssi();
                int levelValue = WifiConnectivityMonitor.this.getLevelValue(rssi);
                if (!this.mBssidQosMap.containsKey(Integer.valueOf(levelValue))) {
                    this.mBssidQosMap.put(Integer.valueOf(levelValue), new RssiLevelQosInfo(levelValue));
                }
                int currChannelWidth = 0;
                int currFreqSecondary = 0;
                int apCountsOnChannel = 0;
                synchronized (WifiConnectivityMonitor.this.mScanResultsLock) {
                    if (this.mCurrentBssidScanInfo == null) {
                        Iterator it = WifiConnectivityMonitor.this.mScanResults.iterator();
                        while (true) {
                            if (!it.hasNext()) {
                                break;
                            }
                            ScanResult scanResult = (ScanResult) it.next();
                            if (this.mBssid.equalsIgnoreCase(scanResult.BSSID)) {
                                this.mCurrentBssidScanInfo = scanResult;
                                break;
                            }
                        }
                    }
                    if (this.mCurrentBssidScanInfo == null) {
                        currFreqPrimary = WifiConnectivityMonitor.this.mWifiInfo.getFrequency();
                    } else {
                        currChannelWidth = this.mCurrentBssidScanInfo.channelWidth;
                        if (currChannelWidth == 0) {
                            currFreqPrimary = this.mCurrentBssidScanInfo.frequency;
                        } else {
                            currFreqPrimary = this.mCurrentBssidScanInfo.centerFreq0;
                            currFreqSecondary = this.mCurrentBssidScanInfo.centerFreq1;
                        }
                    }
                    for (ScanResult scanResult2 : WifiConnectivityMonitor.this.mScanResults) {
                        if (!this.mBssid.equalsIgnoreCase(scanResult2.BSSID)) {
                            int currBandwidth = WifiConnectivityMonitor.this.getBandwidth(currChannelWidth);
                            int scanBandwidth = WifiConnectivityMonitor.this.getBandwidth(scanResult2.channelWidth);
                            int scanFreqSecondary = 0;
                            if (scanResult2.channelWidth == 0) {
                                scanFreqPrimary = scanResult2.frequency;
                            } else {
                                scanFreqPrimary = scanResult2.centerFreq0;
                                scanFreqSecondary = scanResult2.centerFreq1;
                            }
                            if (currFreqPrimary - (currBandwidth / 2) < (scanBandwidth / 2) + scanFreqPrimary && (currBandwidth / 2) + currFreqPrimary > scanFreqPrimary - (scanBandwidth / 2)) {
                                apCountsOnChannel++;
                            } else if (currFreqSecondary != 0 && currFreqSecondary - (currBandwidth / 2) < (scanBandwidth / 2) + scanFreqPrimary && (currBandwidth / 2) + currFreqSecondary > scanFreqPrimary - (scanBandwidth / 2)) {
                                apCountsOnChannel++;
                            } else if (scanFreqSecondary != 0 && currFreqPrimary - (currBandwidth / 2) < (scanBandwidth / 2) + scanFreqSecondary && (currBandwidth / 2) + currFreqPrimary > scanFreqSecondary - (scanBandwidth / 2)) {
                                apCountsOnChannel++;
                            } else if (currFreqSecondary != 0 && scanFreqSecondary != 0 && currFreqSecondary - (currBandwidth / 2) < (scanBandwidth / 2) + scanFreqSecondary && (currBandwidth / 2) + currFreqSecondary > scanFreqSecondary - (scanBandwidth / 2)) {
                                apCountsOnChannel++;
                            }
                        }
                    }
                }
                this.mBssidQosMap.get(Integer.valueOf(levelValue)).mApCountOnChannel = ((double) apCountsOnChannel) / ((double) WifiConnectivityMonitor.this.getBandwidthIn20MhzChannels(currChannelWidth));
                this.mBssidQosMap.get(Integer.valueOf(levelValue)).updateQualityScore(rssi);
            }
        }

        public void updateBssidQosMapOnQcResult(boolean pass) {
            if (WifiConnectivityMonitor.this.mWifiInfo != null) {
                int rssi = WifiConnectivityMonitor.this.mWifiInfo.getRssi();
                int levelValue = WifiConnectivityMonitor.this.getLevelValue(rssi);
                if (!this.mBssidQosMap.containsKey(Integer.valueOf(levelValue))) {
                    this.mBssidQosMap.put(Integer.valueOf(levelValue), new RssiLevelQosInfo(levelValue));
                }
                if (pass) {
                    this.mBssidQosMap.get(Integer.valueOf(levelValue)).mQcPassCount++;
                    this.mLatestQcFailRssi = -99;
                } else {
                    this.mBssidQosMap.get(Integer.valueOf(levelValue)).mQcFailCount++;
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "QC Failure occured at RSSI value " + rssi + " dBm.");
                    }
                    if (rssi > this.mLatestQcFailRssi) {
                        this.mLatestQcFailRssi = rssi;
                    }
                }
                updateBssidNoInternet();
                updateBssidPoorConnection();
                this.mBssidQosMap.get(Integer.valueOf(levelValue)).updateQualityScore(rssi);
            }
        }

        public void updateBssidQosMapOnLevel2State(boolean enter) {
            if (WifiConnectivityMonitor.this.mWifiInfo != null) {
                int rssi = WifiConnectivityMonitor.this.mWifiInfo.getRssi();
                int levelValue = WifiConnectivityMonitor.this.getLevelValue(rssi);
                if (!this.mBssidQosMap.containsKey(Integer.valueOf(levelValue))) {
                    this.mBssidQosMap.put(Integer.valueOf(levelValue), new RssiLevelQosInfo(levelValue));
                }
                if (enter) {
                    if (WifiConnectivityMonitor.DBG) {
                        Log.d(WifiConnectivityMonitor.TAG, "Level2State entered at RSSI value " + rssi + " dBm.");
                    }
                    this.mLatestLevel2Rssi = rssi;
                } else {
                    this.mLatestLevel2Rssi = -99;
                }
                updateBssidPoorConnection();
                this.mBssidQosMap.get(Integer.valueOf(levelValue)).updateQualityScore(rssi);
            }
        }

        public void updateBssidQosMapOnDnsResult(int result, int pingResponseTime) {
            if (WifiConnectivityMonitor.this.mWifiInfo != null) {
                int rssi = WifiConnectivityMonitor.this.mWifiInfo.getRssi();
                int levelValue = WifiConnectivityMonitor.this.getLevelValue(rssi);
                if (!this.mBssidQosMap.containsKey(Integer.valueOf(levelValue))) {
                    this.mBssidQosMap.put(Integer.valueOf(levelValue), new RssiLevelQosInfo(levelValue));
                }
                if (result == 0) {
                    this.mBssidQosMap.get(Integer.valueOf(levelValue)).mCumulativeDnsResponseTime += (long) pingResponseTime;
                    this.mBssidQosMap.get(Integer.valueOf(levelValue)).mDnsPassCount++;
                } else {
                    this.mBssidQosMap.get(Integer.valueOf(levelValue)).mDnsFailCount++;
                }
                this.mBssidQosMap.get(Integer.valueOf(levelValue)).updateQualityScore(rssi);
            }
        }

        public void updateBssidQosMapOnTputUpdate(long pollInterval, long diffTxByte, long diffRxByte) {
            if (WifiConnectivityMonitor.this.mWifiInfo != null) {
                int rssi = WifiConnectivityMonitor.this.mWifiInfo.getRssi();
                int levelValue = WifiConnectivityMonitor.this.getLevelValue(rssi);
                if (!this.mBssidQosMap.containsKey(Integer.valueOf(levelValue))) {
                    this.mBssidQosMap.put(Integer.valueOf(levelValue), new RssiLevelQosInfo(levelValue));
                }
                if (pollInterval > 0 && pollInterval < RttServiceImpl.HAL_RANGING_TIMEOUT_MS) {
                    this.mBssidQosMap.get(Integer.valueOf(levelValue)).mDwellTime += pollInterval;
                    this.mBssidQosMap.get(Integer.valueOf(levelValue)).mTotalTxBytes += diffTxByte;
                    this.mBssidQosMap.get(Integer.valueOf(levelValue)).mTotalRxBytes += diffRxByte;
                    long intervalThroughput = ((diffRxByte * 1000) * 8) / pollInterval;
                    this.mBssidQosMap.get(Integer.valueOf(levelValue)).mCurrentThroughput = intervalThroughput;
                    if (intervalThroughput > 500000) {
                        this.mBssidQosMap.get(Integer.valueOf(levelValue)).mActiveTime += pollInterval;
                        this.mBssidQosMap.get(Integer.valueOf(levelValue)).mActiveTxBytes += diffTxByte;
                        this.mBssidQosMap.get(Integer.valueOf(levelValue)).mActiveRxBytes += diffRxByte;
                        this.mBssidQosMap.get(Integer.valueOf(levelValue)).mActiveThroughput = ((this.mBssidQosMap.get(Integer.valueOf(levelValue)).mActiveRxBytes * 1000) * 8) / this.mBssidQosMap.get(Integer.valueOf(levelValue)).mActiveTime;
                    }
                    this.mBssidQosMap.get(Integer.valueOf(levelValue)).mAverageThroughput = ((this.mBssidQosMap.get(Integer.valueOf(levelValue)).mTotalRxBytes * 1000) * 8) / this.mBssidQosMap.get(Integer.valueOf(levelValue)).mDwellTime;
                    long targetMaxThroughput = this.mBssidQosMap.get(Integer.valueOf(levelValue)).mMaximumThroughput - ((200000 * pollInterval) / 60000);
                    if (targetMaxThroughput < intervalThroughput) {
                        this.mBssidQosMap.get(Integer.valueOf(levelValue)).mMaximumThroughput = intervalThroughput;
                    } else if (targetMaxThroughput > this.mBssidQosMap.get(Integer.valueOf(levelValue)).mActiveThroughput) {
                        this.mBssidQosMap.get(Integer.valueOf(levelValue)).mMaximumThroughput = targetMaxThroughput;
                    } else {
                        this.mBssidQosMap.get(Integer.valueOf(levelValue)).mMaximumThroughput = this.mBssidQosMap.get(Integer.valueOf(levelValue)).mActiveThroughput;
                    }
                }
                this.mBssidQosMap.get(Integer.valueOf(levelValue)).updateQualityScore(rssi);
            }
        }

        public void updateBssidQosMapOnPerUpdate(int rssi, int diffTxBad, int difTxGood) {
            int levelValue = WifiConnectivityMonitor.this.getLevelValue(rssi);
            if (!this.mBssidQosMap.containsKey(Integer.valueOf(levelValue))) {
                this.mBssidQosMap.put(Integer.valueOf(levelValue), new RssiLevelQosInfo(levelValue));
            }
            this.mBssidQosMap.get(Integer.valueOf(levelValue)).mTxBad += diffTxBad;
            this.mBssidQosMap.get(Integer.valueOf(levelValue)).mTxGood += difTxGood;
            int totalTxBad = this.mBssidQosMap.get(Integer.valueOf(levelValue)).mTxBad;
            int totalTxGood = this.mBssidQosMap.get(Integer.valueOf(levelValue)).mTxGood;
            if (totalTxBad + totalTxGood != 0) {
                this.mBssidQosMap.get(Integer.valueOf(levelValue)).mPer = (((double) totalTxBad) / (((double) totalTxBad) + ((double) totalTxGood))) * 100.0d;
            }
            this.mBssidQosMap.get(Integer.valueOf(levelValue)).updateQualityScore(rssi);
        }

        public void updateBssidQosMapOnReachabilityLost() {
            if (WifiConnectivityMonitor.this.mWifiInfo != null) {
                int rssi = WifiConnectivityMonitor.this.mWifiInfo.getRssi();
                int levelValue = WifiConnectivityMonitor.this.getLevelValue(rssi);
                if (!this.mBssidQosMap.containsKey(Integer.valueOf(levelValue))) {
                    this.mBssidQosMap.put(Integer.valueOf(levelValue), new RssiLevelQosInfo(levelValue));
                }
                this.mBssidQosMap.get(Integer.valueOf(levelValue)).mIpReachabilityLostCount++;
                this.mBssidQosMap.get(Integer.valueOf(levelValue)).updateQualityScore(rssi);
            }
        }

        public void updateBssidLatestDnsResultType(int result) {
            int res;
            if (WifiConnectivityMonitor.this.isConnectedState()) {
                Log.d(WifiConnectivityMonitor.TAG, "updateBssidLatestDnsResultType - result: " + result);
                if (result == 0 || result == 11) {
                    res = WifiConnectivityMonitor.this.BSSID_DNS_RESULT_SUCCESS;
                } else if (result == 1 || result == 2 || result == 5) {
                    res = WifiConnectivityMonitor.this.BSSID_DNS_RESULT_NO_INTERNET;
                } else {
                    res = WifiConnectivityMonitor.this.BSSID_DNS_RESULT_POOR_CONNECTION;
                }
                if (res == WifiConnectivityMonitor.this.BSSID_DNS_RESULT_SUCCESS || res == WifiConnectivityMonitor.this.BSSID_DNS_RESULT_NO_INTERNET) {
                    this.mLatestDnsResult = res;
                } else if (this.mLatestDnsResult != WifiConnectivityMonitor.this.BSSID_DNS_RESULT_NO_INTERNET) {
                    this.mLatestDnsResult = res;
                }
                updateBssidNoInternet();
            }
        }

        public void updateBssidNoInternet() {
            boolean prev = this.mBssidNoInternet;
            this.mBssidNoInternet = WifiConnectivityMonitor.this.isInvalidState() && (this.mLatestDnsResult == WifiConnectivityMonitor.this.BSSID_DNS_RESULT_NO_INTERNET || WifiConnectivityMonitor.this.getLevelValue(this.mLatestQcFailRssi) == 3);
            if (prev != this.mBssidNoInternet) {
                Log.d(WifiConnectivityMonitor.TAG, "updateBssidNoInternet: mBssidNoInternet = " + this.mBssidNoInternet);
                WifiConnectivityMonitor.this.reportOpenNetworkQosNoInternetStatus();
            }
        }

        public void updateBssidPoorConnection() {
            int poorRssi = this.mLatestQcFailRssi;
            int i = this.mLatestLevel2Rssi;
            if (poorRssi <= i) {
                poorRssi = i;
            }
            int poorLevel = WifiConnectivityMonitor.this.getLevelValue(poorRssi);
            for (int level = 1; level <= 3; level++) {
                if (!this.mBssidQosMap.containsKey(Integer.valueOf(level))) {
                    this.mBssidQosMap.put(Integer.valueOf(level), new RssiLevelQosInfo(level));
                }
                if (level <= poorLevel) {
                    this.mBssidQosMap.get(Integer.valueOf(level)).mForcedSetScore = WifiConnectivityMonitor.this.INDEX_TO_SCORE[WifiConnectivityMonitor.this.QUALITY_INDEX_SLOW];
                } else {
                    this.mBssidQosMap.get(Integer.valueOf(level)).mForcedSetScore = WifiConnectivityMonitor.this.INDEX_TO_SCORE[WifiConnectivityMonitor.this.QUALITY_INDEX_UNKNOWN];
                }
                this.mBssidQosMap.get(Integer.valueOf(level)).updateQualityScore();
            }
        }

        public void initOnConnect() {
            this.mNumberOfConnections++;
            this.mCurrentBssidScanInfo = null;
            this.mLatestDnsResult = WifiConnectivityMonitor.this.BSSID_DNS_RESULT_UNKNOWN;
            this.mBssidNoInternet = false;
            WifiConfiguration config = WifiConnectivityMonitor.this.mWifiManager.getSpecificNetwork(this.netId);
            if (config != null) {
                this.mSsid = config.SSID;
                if (config.isCaptivePortal) {
                    this.mIsCaptivePortal = true;
                }
            }
        }

        public void clearBssidQosMap() {
            this.mBssidQosMap.clear();
            this.mNumberOfConnections = 0;
            this.mSsid = null;
            this.mCurrentBssidScanInfo = null;
            this.mIsCaptivePortal = false;
            this.mCurrentBssidScanInfo = null;
            this.mLatestDnsResult = WifiConnectivityMonitor.this.BSSID_DNS_RESULT_UNKNOWN;
        }
    }

    public int maxTputToIndex(long maxTput) {
        if (maxTput < 1000000) {
            return this.QUALITY_INDEX_SLOW;
        }
        if (maxTput < 5000000) {
            return this.QUALITY_INDEX_OKAY;
        }
        if (maxTput < 20000000) {
            return this.QUALITY_INDEX_FAST;
        }
        return this.QUALITY_INDEX_VERY_FAST;
    }

    public int activeTputToIndex(long activeTput) {
        if (activeTput < 1000000) {
            return this.QUALITY_INDEX_SLOW;
        }
        if (activeTput < 3000000) {
            return this.QUALITY_INDEX_OKAY;
        }
        if (activeTput < 10000000) {
            return this.QUALITY_INDEX_FAST;
        }
        return this.QUALITY_INDEX_VERY_FAST;
    }

    public int perToIndex(double per) {
        if (per < 2.0d) {
            return this.QUALITY_INDEX_VERY_FAST;
        }
        if (per < 5.0d) {
            return this.QUALITY_INDEX_FAST;
        }
        if (per < 12.0d) {
            return this.QUALITY_INDEX_OKAY;
        }
        return this.QUALITY_INDEX_SLOW;
    }

    public int calculateScore(int indexByMaxTput, int indexByActiveTput, int indexByPer) {
        int totalScore = 0;
        int totalWeight = 0;
        if (indexByMaxTput != this.QUALITY_INDEX_UNKNOWN) {
            totalScore = 0 + (this.INDEX_TO_SCORE[indexByMaxTput] * 33);
            totalWeight = 0 + 33;
        }
        if (indexByActiveTput != this.QUALITY_INDEX_UNKNOWN) {
            totalScore += this.INDEX_TO_SCORE[indexByActiveTput] * 34;
            totalWeight += 34;
        }
        if (indexByPer != this.QUALITY_INDEX_UNKNOWN) {
            totalScore += this.INDEX_TO_SCORE[indexByPer] * 33;
            totalWeight += 33;
        }
        if (totalWeight != 0) {
            return totalScore / totalWeight;
        }
        return 0;
    }

    /* access modifiers changed from: private */
    public class RssiLevelQosInfo {
        public long mActiveRxBytes = 0;
        public long mActiveThroughput = 0;
        public long mActiveTime = 0;
        public long mActiveTxBytes = 0;
        public double mApCountOnChannel = -1.0d;
        public long mAverageThroughput = 0;
        public int mCalculatedScore;
        public long mCumulativeDnsResponseTime = 0;
        public long mCurrentThroughput = 0;
        public int mDnsFailCount = 0;
        public int mDnsPassCount = 0;
        public long mDwellTime = 0;
        public int mForcedSetScore;
        public int mIpReachabilityLostCount = 0;
        private long mLastActiveRxBytes = this.mActiveRxBytes;
        private long mLastActiveTime = this.mActiveTime;
        private long mLastCalculatedScore = ((long) this.mCalculatedScore);
        private int mLastTxBad = this.mTxBad;
        private int mLastTxGood = this.mTxGood;
        public String mLatestCloudScoreSummary = "";
        public int mLevelValue;
        public long mMaximumThroughput = 0;
        public double mPer = WifiConnectivityMonitor.POOR_LINK_MIN_VOLUME;
        public int mQcFailCount = 0;
        public int mQcPassCount = 0;
        public int mScore;
        public long mTotalRxBytes = 0;
        public long mTotalTxBytes = 0;
        public int mTxBad = 0;
        public int mTxGood = 0;

        public RssiLevelQosInfo(int levelValue) {
            this.mLevelValue = levelValue;
            this.mScore = WifiConnectivityMonitor.this.INDEX_TO_SCORE[WifiConnectivityMonitor.this.QUALITY_INDEX_UNKNOWN];
            this.mCalculatedScore = WifiConnectivityMonitor.this.INDEX_TO_SCORE[WifiConnectivityMonitor.this.QUALITY_INDEX_UNKNOWN];
            this.mForcedSetScore = WifiConnectivityMonitor.this.INDEX_TO_SCORE[WifiConnectivityMonitor.this.QUALITY_INDEX_UNKNOWN];
        }

        public int getQualityIndexByMaxTput() {
            if (this.mDwellTime < 60000 || this.mTotalRxBytes < 1000000) {
                return WifiConnectivityMonitor.this.QUALITY_INDEX_UNKNOWN;
            }
            return WifiConnectivityMonitor.this.maxTputToIndex(this.mMaximumThroughput);
        }

        public int getQualityIndexByActiveTput() {
            if (this.mActiveTime < 30000 || this.mTotalRxBytes < 1000000) {
                return WifiConnectivityMonitor.this.QUALITY_INDEX_UNKNOWN;
            }
            return WifiConnectivityMonitor.this.activeTputToIndex(this.mActiveThroughput);
        }

        public int getQualityIndexByPer() {
            if (this.mCalculatedScore == WifiConnectivityMonitor.this.INDEX_TO_SCORE[WifiConnectivityMonitor.this.QUALITY_INDEX_UNKNOWN]) {
                return WifiConnectivityMonitor.this.QUALITY_INDEX_UNKNOWN;
            }
            if (((long) (this.mTxBad + this.mTxGood)) < 1500) {
                return WifiConnectivityMonitor.this.QUALITY_INDEX_UNKNOWN;
            }
            return WifiConnectivityMonitor.this.perToIndex(this.mPer);
        }

        public int getQualityIndex() {
            return WifiConnectivityMonitor.this.getQualityIndexFromScore(this.mScore);
        }

        public void updateQualityScore() {
            updateQualityScore(-100);
        }

        public void updateQualityScore(int rssi) {
            int resultScore;
            int score = WifiConnectivityMonitor.this.calculateScore(getQualityIndexByMaxTput(), getQualityIndexByActiveTput(), getQualityIndexByPer());
            if (score != 0) {
                this.mCalculatedScore = score;
            }
            if (this.mForcedSetScore != WifiConnectivityMonitor.this.INDEX_TO_SCORE[WifiConnectivityMonitor.this.QUALITY_INDEX_UNKNOWN]) {
                resultScore = this.mForcedSetScore;
            } else {
                resultScore = this.mCalculatedScore;
            }
            if (this.mScore != resultScore) {
                this.mScore = resultScore;
                WifiConnectivityMonitor.this.reportOpenNetworkQosQualityScoreChange();
            }
            if (WifiConnectivityMonitor.DBG || WifiConnectivityMonitor.SMARTCM_DBG) {
                StringBuilder sb = new StringBuilder();
                sb.append("updateQualityScore - ");
                sb.append(WifiConnectivityMonitor.this.mCurrentBssid.mSsid);
                sb.append(" [");
                sb.append(WifiConnectivityMonitor.this.mCurrentBssid.mBssid);
                sb.append("], rssi: ");
                sb.append(rssi == -100 ? "N/A" : Integer.valueOf(rssi));
                sb.append(", #Conn: ");
                sb.append(WifiConnectivityMonitor.this.mCurrentBssid.mNumberOfConnections);
                sb.append(" - ");
                sb.append(toString());
                Log.d(WifiConnectivityMonitor.TAG, sb.toString());
            }
            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                showToastBssidQosMapInfo(rssi);
            }
        }

        public Bundle getScoreForCloud() {
            long activeTimeDelta = this.mActiveTime - this.mLastActiveTime;
            Bundle bund = new Bundle();
            if (activeTimeDelta < 30000) {
                bund.putInt("score", WifiConnectivityMonitor.this.INDEX_TO_SCORE[WifiConnectivityMonitor.this.QUALITY_INDEX_UNKNOWN]);
                bund.putLong("weight", 0);
                return bund;
            }
            long activeTroughputDelta = (((this.mActiveRxBytes - this.mLastActiveRxBytes) * 1000) * 8) / activeTimeDelta;
            int txBadDelta = this.mTxBad - this.mLastTxBad;
            int txGoodDelta = this.mTxGood - this.mLastTxGood;
            double perDelta = (((double) txBadDelta) / (((double) txBadDelta) + ((double) txGoodDelta))) * 100.0d;
            WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
            int calculatedScoreFromDelta = wifiConnectivityMonitor.calculateScore(wifiConnectivityMonitor.maxTputToIndex(this.mMaximumThroughput), WifiConnectivityMonitor.this.activeTputToIndex(activeTroughputDelta), WifiConnectivityMonitor.this.perToIndex(perDelta));
            updateQualityScore();
            DecimalFormat df = new DecimalFormat("#.##");
            String currentTime = "" + (System.currentTimeMillis() / 1000);
            try {
                currentTime = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            } catch (RuntimeException e) {
            }
            this.mLatestCloudScoreSummary = "getScoreForCloud[" + currentTime + "]: LastScore: " + this.mLastCalculatedScore + " @ " + this.mLastActiveTime + ", CurrentScore: " + this.mCalculatedScore + " @ " + this.mActiveTime + ", calculatedScoreFromDelta: " + calculatedScoreFromDelta + " @ " + activeTimeDelta + ", Mx/dAc: " + (this.mMaximumThroughput / 1000) + "/" + (activeTroughputDelta / 1000) + ", dTB/dTG: " + txBadDelta + "/" + txGoodDelta + " [" + df.format(perDelta) + "%]";
            if (WifiConnectivityMonitor.DBG) {
                Log.d(WifiConnectivityMonitor.TAG, this.mLatestCloudScoreSummary);
            }
            this.mLastCalculatedScore = (long) this.mCalculatedScore;
            this.mLastActiveRxBytes = this.mActiveRxBytes;
            this.mLastActiveTime = this.mActiveTime;
            this.mLastTxBad = this.mTxBad;
            this.mLastTxGood = this.mTxGood;
            bund.putInt("score", calculatedScoreFromDelta);
            bund.putLong("weight", activeTimeDelta);
            return bund;
        }

        public String toString() {
            String str;
            DecimalFormat df = new DecimalFormat("#.##");
            StringBuilder sb = new StringBuilder();
            long j = 0;
            if (WifiConnectivityMonitor.SMARTCM_DBG) {
                sb.append("RSSI Level[" + this.mLevelValue + "] - ");
                sb.append("Quality: " + WifiConnectivityMonitor.this.INDEX_TO_STRING[getQualityIndex()] + "[" + this.mScore + "(" + this.mForcedSetScore + "/" + this.mCalculatedScore + ")-" + WifiConnectivityMonitor.this.INDEX_TO_STRING[getQualityIndexByMaxTput()] + "/" + WifiConnectivityMonitor.this.INDEX_TO_STRING[getQualityIndexByActiveTput()] + "/" + WifiConnectivityMonitor.this.INDEX_TO_STRING[getQualityIndexByPer()] + "] ");
                StringBuilder sb2 = new StringBuilder();
                sb2.append("DwellTime/ActiveTime: ");
                sb2.append(this.mDwellTime / 1000);
                sb2.append("/");
                sb2.append(this.mActiveTime / 1000);
                sb2.append(" sec, ");
                sb.append(sb2.toString());
                sb.append("Max/Ave/Active/Curr Tput " + (this.mMaximumThroughput / 1000) + "/" + (this.mAverageThroughput / 1000) + "/" + (this.mActiveThroughput / 1000) + "/" + (this.mCurrentThroughput / 1000) + " kbps, ");
                StringBuilder sb3 = new StringBuilder();
                sb3.append("TxBad/TxGood: ");
                sb3.append(this.mTxBad);
                sb3.append("/");
                sb3.append(this.mTxGood);
                sb3.append(" [");
                sb3.append(df.format(this.mPer));
                sb3.append("%], ");
                sb.append(sb3.toString());
                sb.append("TxBytes: " + Formatter.formatFileSize(WifiConnectivityMonitor.this.mContext, this.mTotalTxBytes) + ", ");
                sb.append("RxBytes: " + Formatter.formatFileSize(WifiConnectivityMonitor.this.mContext, this.mTotalRxBytes) + ", ");
                sb.append("QC P/F: " + this.mQcPassCount + "/" + this.mQcFailCount + ", ");
                sb.append("DNS P/F: " + this.mDnsPassCount + "/" + this.mQcFailCount + ", ");
                StringBuilder sb4 = new StringBuilder();
                sb4.append("DNS RTT: ");
                int i = this.mDnsPassCount;
                if (i != 0) {
                    j = this.mCumulativeDnsResponseTime / ((long) i);
                }
                sb4.append(j);
                sb4.append("msec, ");
                sb.append(sb4.toString());
                sb.append("ApCount: " + df.format(this.mApCountOnChannel) + ", ");
                if (this.mIpReachabilityLostCount == 0) {
                    str = "";
                } else {
                    str = "mIpReachabilityLostCount: " + this.mIpReachabilityLostCount;
                }
                sb.append(str);
            } else {
                sb.append("Lev[" + this.mLevelValue + "] - ");
                sb.append("Q: " + WifiConnectivityMonitor.this.INDEX_TO_STRING_SHORT[getQualityIndex()] + "[" + this.mScore + "(" + this.mForcedSetScore + "/" + this.mCalculatedScore + ")-" + WifiConnectivityMonitor.this.INDEX_TO_STRING_SHORT[getQualityIndexByMaxTput()] + "/" + WifiConnectivityMonitor.this.INDEX_TO_STRING_SHORT[getQualityIndexByActiveTput()] + "/" + WifiConnectivityMonitor.this.INDEX_TO_STRING_SHORT[getQualityIndexByPer()] + "]");
                StringBuilder sb5 = new StringBuilder();
                sb5.append(", DT/AT: ");
                sb5.append(this.mDwellTime / 1000);
                sb5.append("/");
                sb5.append(this.mActiveTime / 1000);
                sb.append(sb5.toString());
                StringBuilder sb6 = new StringBuilder();
                sb6.append(", Mx/Av/Ac/Cr: ");
                sb6.append(this.mMaximumThroughput / 1000);
                sb6.append("/");
                sb6.append(this.mAverageThroughput / 1000);
                sb6.append("/");
                sb6.append(this.mActiveThroughput / 1000);
                sb6.append("/");
                sb6.append(this.mCurrentThroughput / 1000);
                sb.append(sb6.toString());
                sb.append(", TB/TG: " + this.mTxBad + "/" + this.mTxGood + " [" + df.format(this.mPer) + "%]");
                StringBuilder sb7 = new StringBuilder();
                sb7.append(", Tx/Rx: ");
                sb7.append(Formatter.formatFileSize(WifiConnectivityMonitor.this.mContext, this.mTotalTxBytes));
                sb7.append("/");
                sb7.append(Formatter.formatFileSize(WifiConnectivityMonitor.this.mContext, this.mTotalRxBytes));
                sb.append(sb7.toString());
                sb.append(", Q P/F: " + this.mQcPassCount + "/" + this.mQcFailCount);
                sb.append(", D P/F: " + this.mDnsPassCount + "/" + this.mQcFailCount);
                StringBuilder sb8 = new StringBuilder();
                sb8.append(", D RTT: ");
                int i2 = this.mDnsPassCount;
                if (i2 != 0) {
                    j = this.mCumulativeDnsResponseTime / ((long) i2);
                }
                sb8.append(j);
                sb.append(sb8.toString());
            }
            return sb.toString();
        }

        private void showToastBssidQosMapInfo(int rssi) {
            WifiConnectivityMonitor.access$23508(WifiConnectivityMonitor.this);
            if (WifiConnectivityMonitor.this.toastCount % 30 == 0) {
                int levelValue = WifiConnectivityMonitor.this.getLevelValue(rssi);
                DecimalFormat df = new DecimalFormat("#.##");
                StringBuilder sb = new StringBuilder();
                sb.append("Level[");
                sb.append(rssi == -100 ? "N/A" : Integer.valueOf(levelValue));
                sb.append("]  ");
                sb.append(rssi == -100 ? "N/A" : rssi + " dBm ");
                sb.append(this.mCurrentThroughput / 1000);
                sb.append(" kbps\nMax Tput: ");
                sb.append(this.mMaximumThroughput / 1000000);
                sb.append(" Mbps - ");
                sb.append(WifiConnectivityMonitor.this.INDEX_TO_STRING[getQualityIndexByMaxTput()]);
                sb.append("\nActive Tput: ");
                sb.append(this.mActiveThroughput / 1000000);
                sb.append(" Mbps - ");
                sb.append(WifiConnectivityMonitor.this.INDEX_TO_STRING[getQualityIndexByActiveTput()]);
                sb.append("\nPER: ");
                sb.append(df.format(this.mPer));
                sb.append("% - ");
                sb.append(WifiConnectivityMonitor.this.INDEX_TO_STRING[getQualityIndexByPer()]);
                sb.append("\nRESULT: ");
                sb.append(WifiConnectivityMonitor.this.INDEX_TO_STRING[getQualityIndex()]);
                Toast.makeText(WifiConnectivityMonitor.this.mContext, sb.toString(), 1).show();
            } else if (WifiConnectivityMonitor.this.toastCount % 30 == 15) {
                StringBuilder sb2 = new StringBuilder();
                synchronized (WifiConnectivityMonitor.mCurrentBssidLock) {
                    sb2.append(WifiConnectivityMonitor.this.mCurrentBssid.mSsid + " [" + WifiConnectivityMonitor.this.mCurrentBssid.mBssid + "] - #Conn: " + WifiConnectivityMonitor.this.mCurrentBssid.mNumberOfConnections + ", isCaptivePortal: " + WifiConnectivityMonitor.this.mCurrentBssid.mIsCaptivePortal + ", Latest DNS Result: " + WifiConnectivityMonitor.this.mCurrentBssid.mLatestDnsResult + "\n");
                    for (RssiLevelQosInfo levelInfo : WifiConnectivityMonitor.this.mCurrentBssid.mBssidQosMap.values()) {
                        sb2.append("Level[" + levelInfo.mLevelValue + "]");
                        sb2.append(" - Quality: " + WifiConnectivityMonitor.this.INDEX_TO_STRING[levelInfo.getQualityIndex()] + "[" + WifiConnectivityMonitor.this.INDEX_TO_STRING_SHORT[levelInfo.getQualityIndexByMaxTput()] + "/" + WifiConnectivityMonitor.this.INDEX_TO_STRING_SHORT[levelInfo.getQualityIndexByActiveTput()] + "/" + WifiConnectivityMonitor.this.INDEX_TO_STRING_SHORT[levelInfo.getQualityIndexByPer()] + "] ");
                        sb2.append("\n");
                    }
                }
                Toast.makeText(WifiConnectivityMonitor.this.mContext, sb2.toString(), 1).show();
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int getLevelValue(int rssi) {
        if (rssi < -75) {
            return 0;
        }
        if (rssi < -65) {
            return 1;
        }
        if (rssi < -55) {
            return 2;
        }
        return 3;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int getQualityIndexFromScore(int score) {
        int i = this.QUALITY_INDEX_UNKNOWN;
        if (score == i) {
            return i;
        }
        int[] iArr = this.INDEX_TO_SCORE;
        int i2 = this.QUALITY_INDEX_SLOW;
        int i3 = iArr[i2];
        int i4 = this.QUALITY_INDEX_OKAY;
        if (score < (i3 + iArr[i4]) / 2) {
            return i2;
        }
        int i5 = iArr[i4];
        int i6 = this.QUALITY_INDEX_FAST;
        if (score < (i5 + iArr[i6]) / 2) {
            return i4;
        }
        int i7 = iArr[i6];
        int i8 = this.QUALITY_INDEX_VERY_FAST;
        if (score < (i7 + iArr[i8]) / 2) {
            return i6;
        }
        return i8;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int getBandwidth(int channelWidth) {
        if (channelWidth == 0) {
            return 20;
        }
        if (channelWidth == 1) {
            return 40;
        }
        if (channelWidth == 2) {
            return 80;
        }
        if (channelWidth == 3) {
            return SemWifiConstants.ROUTER_OUI_TYPE;
        }
        if (channelWidth == 4) {
            return 80;
        }
        return 20;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private int getBandwidthIn20MhzChannels(int channelWidth) {
        if (channelWidth == 0) {
            return 1;
        }
        if (channelWidth == 1) {
            return 2;
        }
        if (channelWidth == 2) {
            return 4;
        }
        if (channelWidth == 3 || channelWidth == 4) {
            return 8;
        }
        return 1;
    }

    private void updateOpenNetworkQosScoreSummary() {
        String s = null;
        boolean noInternet = getOpenNetworkQosNoInternetStatus();
        int[] scores = getOpenNetworkQosScores();
        if (scores != null) {
            String s2 = noInternet ? " [ No Internet - " : " [ ";
            for (int i : scores) {
                s2 = s2 + this.INDEX_TO_STRING_SHORT[getQualityIndexFromScore(i)] + " ";
            }
            s = s2 + "] ";
        }
        if (DBG) {
            Log.d(TAG, "updateOpenNetworkQosScoreSummary: " + s);
        }
        Settings.Global.putString(this.mContentResolver, "wifi_wcm_qos_sharing_score_summary", s);
    }

    public boolean getOpenNetworkQosNoInternetStatus() {
        BssidStatistics bssidStatistics = this.mCurrentBssid;
        if (bssidStatistics == null || bssidStatistics == this.mEmptyBssid || bssidStatistics.netId == -1) {
            return false;
        }
        Log.d(TAG, "getOpenNetworkQosNoInternetStatus: " + this.mCurrentBssid.mBssidNoInternet);
        return this.mCurrentBssid.mBssidNoInternet;
    }

    public int[] getOpenNetworkQosScores() {
        BssidStatistics bssidStatistics = this.mCurrentBssid;
        if (bssidStatistics == null || bssidStatistics == this.mEmptyBssid || bssidStatistics.netId == -1) {
            return null;
        }
        int[] retScores = new int[3];
        if (this.mCurrentBssid.mBssidQosMap.containsKey(3)) {
            retScores[0] = this.mCurrentBssid.mBssidQosMap.get(3).mScore;
        } else {
            retScores[0] = this.INDEX_TO_SCORE[this.QUALITY_INDEX_UNKNOWN];
        }
        if (this.mCurrentBssid.mBssidQosMap.containsKey(2)) {
            retScores[1] = this.mCurrentBssid.mBssidQosMap.get(2).mScore;
        } else {
            retScores[1] = this.INDEX_TO_SCORE[this.QUALITY_INDEX_UNKNOWN];
        }
        if (this.mCurrentBssid.mBssidQosMap.containsKey(1)) {
            retScores[2] = this.mCurrentBssid.mBssidQosMap.get(1).mScore;
        } else {
            retScores[2] = this.INDEX_TO_SCORE[this.QUALITY_INDEX_UNKNOWN];
        }
        String s = "";
        for (int i : retScores) {
            s = s + i + " ";
        }
        Log.d(TAG, "getOpenNetworkQosScores: " + s);
        return retScores;
    }

    public int[] getOpenNetworkQosScores(String bssid) {
        BssidStatistics bssidStat;
        if (bssid == null) {
            return getOpenNetworkQosScores();
        }
        if (!this.mBssidCache.snapshot().containsKey(bssid) || (bssidStat = this.mBssidCache.get(bssid)) == null || bssidStat.netId == -1) {
            return null;
        }
        int[] retScores = new int[3];
        if (bssidStat.mBssidQosMap.containsKey(3)) {
            retScores[0] = bssidStat.mBssidQosMap.get(3).mScore;
        } else {
            retScores[0] = this.INDEX_TO_SCORE[this.QUALITY_INDEX_UNKNOWN];
        }
        if (bssidStat.mBssidQosMap.containsKey(2)) {
            retScores[1] = bssidStat.mBssidQosMap.get(2).mScore;
        } else {
            retScores[1] = this.INDEX_TO_SCORE[this.QUALITY_INDEX_UNKNOWN];
        }
        if (bssidStat.mBssidQosMap.containsKey(1)) {
            retScores[2] = bssidStat.mBssidQosMap.get(1).mScore;
        } else {
            retScores[2] = this.INDEX_TO_SCORE[this.QUALITY_INDEX_UNKNOWN];
        }
        String s = "";
        for (int i : retScores) {
            s = s + i + " ";
        }
        Log.d(TAG, "getOpenNetworkQosScores[" + bssid + "]: " + s);
        return retScores;
    }

    public static class OpenNetworkQosCallback {
        /* access modifiers changed from: package-private */
        public void onNoInternetStatusChange(boolean valid) {
        }

        /* access modifiers changed from: package-private */
        public void onQualityScoreChanged() {
        }
    }

    public void registerOpenNetworkQosCallback(OpenNetworkQosCallback callback) {
        if (this.mOpenNetworkQosCallbackList == null) {
            this.mOpenNetworkQosCallbackList = new ArrayList<>();
        }
        this.mOpenNetworkQosCallbackList.add(callback);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void reportOpenNetworkQosNoInternetStatus() {
        if (this.mCurrentBssid != null) {
            if (DBG) {
                Log.d(TAG, "reportOpenNetworkQosNoInternetStatus");
            }
            updateOpenNetworkQosScoreSummary();
            ArrayList<OpenNetworkQosCallback> arrayList = this.mOpenNetworkQosCallbackList;
            if (arrayList != null) {
                Iterator<OpenNetworkQosCallback> it = arrayList.iterator();
                while (it.hasNext()) {
                    it.next().onNoInternetStatusChange(this.mCurrentBssid.mBssidNoInternet);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void reportOpenNetworkQosQualityScoreChange() {
        if (this.mCurrentBssid != null) {
            if (DBG) {
                Log.d(TAG, "reportOpenNetworkQosQualityScoreChange");
            }
            updateOpenNetworkQosScoreSummary();
            ArrayList<OpenNetworkQosCallback> arrayList = this.mOpenNetworkQosCallbackList;
            if (arrayList != null) {
                Iterator<OpenNetworkQosCallback> it = arrayList.iterator();
                while (it.hasNext()) {
                    it.next().onQualityScoreChanged();
                }
            }
        }
    }

    private String dumpBssidQosMap() {
        StringBuilder sb = new StringBuilder();
        synchronized (mCurrentBssidLock) {
            for (BssidStatistics bssidStat : this.mBssidCache.snapshot().values()) {
                sb.append(bssidStat.mSsid + " [" + bssidStat.mBssid + "] - #Conn: " + bssidStat.mNumberOfConnections + ", CP: " + bssidStat.mIsCaptivePortal + ", L_Dns: " + bssidStat.mLatestDnsResult + ", L_F_R: " + bssidStat.mLatestQcFailRssi + ", L_2_R: " + bssidStat.mLatestLevel2Rssi + "\n");
                for (RssiLevelQosInfo levelInfo : bssidStat.mBssidQosMap.values()) {
                    sb.append("    ");
                    sb.append(levelInfo.toString());
                    sb.append("\n");
                    if (!levelInfo.mLatestCloudScoreSummary.isEmpty()) {
                        sb.append("        ");
                        sb.append(levelInfo.mLatestCloudScoreSummary);
                        sb.append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private void clearAllBssidQosMaps() {
        synchronized (mCurrentBssidLock) {
            for (BssidStatistics bssidStat : this.mBssidCache.snapshot().values()) {
                bssidStat.clearBssidQosMap();
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void scanStarted() {
        if (!this.mIsScanning) {
            this.mIsScanning = true;
            removeMessages(EVENT_SCAN_TIMEOUT);
            sendMessage(EVENT_SCAN_STARTED);
            sendMessageDelayed(EVENT_SCAN_TIMEOUT, RttServiceImpl.HAL_RANGING_TIMEOUT_MS);
            this.mNetworkStatsAnalyzer.sendEmptyMessage(EVENT_SCAN_STARTED);
            this.mNetworkStatsAnalyzer.sendEmptyMessageDelayed(EVENT_SCAN_TIMEOUT, RttServiceImpl.HAL_RANGING_TIMEOUT_MS);
        } else if (DBG) {
            Log.d(TAG, "startScan but already in scanning state");
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void scanCompleted(boolean partialScan) {
        int i;
        BssidStatistics bssidStatistics;
        this.mIsScanning = false;
        checkDisabledNetworks();
        if (!partialScan) {
            synchronized (this.mScanResultsLock) {
                List<ScanResult> scanResults = this.mWifiManager.getScanResults();
                if (scanResults.isEmpty()) {
                    this.mScanResults = new ArrayList();
                } else {
                    this.mScanResults = scanResults;
                }
            }
            checkCountryCodeFromScanResults();
            if (isConnectedState() && (bssidStatistics = this.mCurrentBssid) != null) {
                bssidStatistics.updateBssidQosMapOnScan();
            }
        }
        if (mUserSelectionConfirmed && !this.mConnectivityManager.getMultiNetwork()) {
            if (getCurrentState() == this.mLevel2State) {
                if (DBG) {
                    Log.d(TAG, "CHECK_ALTERNATIVE_NETWORKS - mLevel2State");
                }
                this.mClientModeImpl.checkAlternativeNetworksForWmc(false);
            } else if (getCurrentState() == this.mInvalidState && ((i = this.mCurrentMode) == 2 || i == 3)) {
                if (!this.mNetworkCallbackController.isCaptivePortal()) {
                    if (DBG) {
                        Log.d(TAG, "CHECK_ALTERNATIVE_NETWORKS - mInvalidState && SNS ON");
                    }
                    this.mClientModeImpl.checkAlternativeNetworksForWmc(false);
                    return;
                }
                removeMessages(CMD_CHECK_ALTERNATIVE_FOR_CAPTIVE_PORTAL);
                sendMessageDelayed(CMD_CHECK_ALTERNATIVE_FOR_CAPTIVE_PORTAL, 3000);
            } else if (!this.mLevel2StateTransitionPending) {
            } else {
                if (getCurrentState() == this.mLevel1State) {
                    if (DBG) {
                        Log.d(TAG, "CHECK_ALTERNATIVE_NETWORKS - mLevel1State - Need to roam in Level1State");
                    }
                    this.mClientModeImpl.checkAlternativeNetworksForWmc(true);
                    return;
                }
                this.mLevel2StateTransitionPending = false;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public void checkCountryCodeFromScanResults() {
        int scanCount = 0;
        int bssidWithCountryInfo = 0;
        HashMap<String, Integer> countryCodeMap = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        synchronized (this.mScanResultsLock) {
            for (ScanResult scanResult : this.mScanResults) {
                scanCount++;
                ScanResult.InformationElement[] informationElementArr = scanResult.informationElements;
                int length = informationElementArr.length;
                int i = 0;
                while (true) {
                    if (i >= length) {
                        break;
                    }
                    ScanResult.InformationElement ie = informationElementArr[i];
                    if (ie.id == 7) {
                        bssidWithCountryInfo++;
                        if (ie.bytes.length >= 2) {
                            String country = new String(ie.bytes, 0, 2, StandardCharsets.UTF_8).toUpperCase();
                            if ("HK".equals(country) || "MO".equals(country)) {
                                country = "CN";
                            }
                            if (countryCodeMap.containsKey(country)) {
                                countryCodeMap.put(country, Integer.valueOf(countryCodeMap.get(country).intValue() + 1));
                            } else {
                                countryCodeMap.put(country, 1);
                            }
                        }
                    } else {
                        i++;
                    }
                }
            }
        }
        if (bssidWithCountryInfo != 0) {
            int winnerCount = 0;
            if (countryCodeMap.containsKey("CN")) {
                countryCodeMap.get("CN").intValue();
            }
            String pollWinner = "";
            String stat = "";
            for (String c : countryCodeMap.keySet()) {
                stat = (stat + c + ": " + countryCodeMap.get(c)) + "  ";
                if (countryCodeMap.get(c).intValue() > winnerCount || (countryCodeMap.get(c).intValue() >= winnerCount && "CN".equals(c))) {
                    pollWinner = c;
                    winnerCount = countryCodeMap.get(c).intValue();
                }
            }
            String currentTime = "" + (System.currentTimeMillis() / 1000);
            try {
                currentTime = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            } catch (RuntimeException e) {
            }
            sb.append(currentTime + "  |  ");
            sb.append("available data: " + bssidWithCountryInfo + "/" + scanCount + "  |  ");
            sb.append("Win: " + pollWinner + " - " + winnerCount + "[" + ((winnerCount * 100) / bssidWithCountryInfo) + "%]  |  ");
            StringBuilder sb2 = new StringBuilder();
            sb2.append("Stat - ");
            sb2.append(stat);
            sb2.append("  |  ");
            sb.append(sb2.toString());
            if (winnerCount >= 5) {
                if ("CN".equals(pollWinner)) {
                    this.mLastChinaConfirmedTime = System.currentTimeMillis();
                }
                if ("CN".equals(this.mCountryCodeFromScanResult) && !"CN".equals(pollWinner)) {
                    long remainingTime = 86400000 - (System.currentTimeMillis() - this.mLastChinaConfirmedTime);
                    if (remainingTime < 0) {
                        sb.append("  |  CISO Updated [24h expired] - CN -> " + pollWinner);
                        this.mCountryCodeFromScanResult = pollWinner;
                        Settings.Global.putString(this.mContext.getContentResolver(), "wifi_wcm_country_code_from_scan_result", this.mCountryCodeFromScanResult);
                    } else {
                        sb.append("  |  CISO changed but not updated - CN -X-> " + pollWinner + " , maintain CN for next " + (remainingTime / 1000) + " seconds");
                    }
                } else if (!pollWinner.equals(this.mCountryCodeFromScanResult)) {
                    sb.append("  |  Updated - " + this.mCountryCodeFromScanResult + "->" + pollWinner);
                    this.mCountryCodeFromScanResult = pollWinner;
                    Settings.Global.putString(this.mContext.getContentResolver(), "wifi_wcm_country_code_from_scan_result", this.mCountryCodeFromScanResult);
                }
            }
            String[] strArr = this.summaryCountryCodeFromScanResults;
            int i2 = this.incrScanResult;
            this.incrScanResult = i2 + 1;
            strArr[i2 % 10] = sb.toString();
            if (DBG) {
                Log.d(TAG, sb.toString());
            }
        }
    }

    private void checkDisabledNetworks() {
        WifiManager wifiManager = this.mWifiManager;
        if (wifiManager != null) {
            for (WifiConfiguration config : wifiManager.getConfiguredNetworks()) {
                WifiConfiguration.NetworkSelectionStatus netStatus = config.getNetworkSelectionStatus();
                int disableReason = netStatus.getNetworkSelectionDisableReason();
                if (!netStatus.isNetworkEnabled() && ((disableReason == 6 || disableReason == 16 || disableReason == 17) && this.mWifiConfigManager.tryEnableNetwork(config.networkId))) {
                    Log.d(TAG, config.SSID + " network enabled.");
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void handleNeedToRoamInLevel1State() {
        if (!this.mLevel2StateTransitionPending) {
            this.mLevel2StateTransitionPending = true;
            if (DBG) {
                Log.d(TAG, "Start scan to find possible roaming networks. " + getCurrentState() + getCurrentMode());
            }
            this.mClientModeImpl.startScanFromWcm();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isMobileHotspot() {
        WifiInfo wifiInfo;
        if (!"vzw".equalsIgnoreCase(SemCscFeature.getInstance().getString("SalesCode")) && !mCurrentApDefault && (wifiInfo = syncGetCurrentWifiInfo()) != null) {
            int networkId = wifiInfo.getNetworkId();
            if (networkId == this.mLastCheckedMobileHotspotNetworkId) {
                return this.mLastCheckedMobileHotspotValue;
            }
            this.mLastCheckedMobileHotspotNetworkId = networkId;
            this.mLastCheckedMobileHotspotValue = false;
        }
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isConnectedState() {
        if (getCurrentState() == this.mNotConnectedState) {
            return false;
        }
        return true;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isNetworkNeedChnPublicDns() {
        try {
            String countryCode = SemCscFeature.getInstance().getString("CountryISO").toLowerCase();
            if (DBG) {
                Log.d(TAG, "Public DNS via Property(CSC) : countryCode: " + countryCode);
            }
            if (countryCode == null || countryCode.length() != 2 || !"cn".equalsIgnoreCase(countryCode)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean inChinaNetwork() {
        String str = this.mCountryIso;
        if (str == null || str.length() != 2) {
            updateCountryIsoCode();
        }
        if (!isChineseIso(this.mCountryIso)) {
            return false;
        }
        if (!DBG) {
            return true;
        }
        Log.d(TAG, "Need to skip captive portal check. CISO: " + this.mCountryIso);
        return true;
    }

    private boolean isChineseIso(String countryIso) {
        return "cn".equalsIgnoreCase(countryIso) || "hk".equalsIgnoreCase(countryIso) || "mo".equalsIgnoreCase(countryIso);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateCountryIsoCode() {
        if (this.mTelephonyManager == null) {
            try {
                this.mTelephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
            } catch (Exception e) {
                Log.e(TAG, "Exception occured at updateCountryIsoCode(), while retrieving Context.TELEPHONY_SERVICE");
            }
        }
        TelephonyManager telephonyManager = this.mTelephonyManager;
        if (telephonyManager != null) {
            this.mCountryIso = telephonyManager.getNetworkCountryIso();
            Log.i(TAG, "updateCountryIsoCode() via TelephonyManager : mCountryIso: " + this.mCountryIso);
        }
        String str = this.mCountryIso;
        if (str == null || str.length() != 2) {
            try {
                String countryCode = SemCscFeature.getInstance().getString("CountryISO").toLowerCase();
                if (countryCode == null || countryCode.length() != 2) {
                    this.mCountryIso = " ";
                } else {
                    this.mCountryIso = countryCode;
                }
            } catch (Exception e2) {
            }
            if (DBG) {
                Log.d(TAG, "updateCountryIsoCode() via Property(CSC) : mCountryIso: " + this.mCountryIso);
            }
        } else {
            if (Settings.Secure.getString(this.mContext.getContentResolver(), "wifi_sns_visited_country_iso") == null) {
                Settings.Secure.putString(this.mContext.getContentResolver(), "wifi_sns_visited_country_iso", this.mCountryIso);
                Log.e(TAG, "WIFI_SNS_VISITED_COUNTRY_ISO is null, putString:" + this.mCountryIso);
            }
            if ("LGU".equals(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGOPBRANDING)) && !Settings.Secure.getString(this.mContext.getContentResolver(), "wifi_sns_visited_country_iso").equals(this.mCountryIso)) {
                Log.e(TAG, "WIFI_SNS_VISITED_COUNTRY_ISO need to be updated from/to : " + Settings.Secure.getString(this.mContext.getContentResolver(), "wifi_sns_visited_country_iso") + "/" + this.mCountryIso + " Initialize WIFI_POOR_CONNECTION_WARNING to 0");
                Settings.Secure.putInt(this.mContext.getContentResolver(), "wifi_poor_connection_warning", 0);
                Settings.Secure.putString(this.mContext.getContentResolver(), "wifi_sns_visited_country_iso", this.mCountryIso);
            }
        }
        try {
            String deviceCountryCode = SemCscFeature.getInstance().getString("CountryISO");
            if (deviceCountryCode != null) {
                this.mDeviceCountryCode = deviceCountryCode;
            } else {
                this.mDeviceCountryCode = " ";
            }
        } catch (Exception e3) {
        }
    }

    private static void checkVersion(Context context) {
        try {
            int i = 0;
            int mWatchdogVersionFromSettings = Settings.Global.getInt(context.getContentResolver(), "wifi_watchdog_version", 0);
            int storedOSver = (-65536 & mWatchdogVersionFromSettings) >>> 16;
            int updatingOSver = 0;
            String propertyOsVersion = SystemProperties.get("ro.build.version.release");
            for (int i2 = 0; i2 < propertyOsVersion.length(); i2++) {
                if (Character.isDigit(propertyOsVersion.charAt(i2))) {
                    updatingOSver = (updatingOSver << 4) + Character.getNumericValue(propertyOsVersion.charAt(i2));
                }
            }
            if (updatingOSver == 0) {
                Log.e(TAG, "Cannot retrieve version info from SystemProperties.");
                return;
            }
            if (DBG) {
                Log.d(TAG, "checkVersion - Current version: 0x" + Integer.toHexString(mWatchdogVersionFromSettings) + ", New version: 0x" + Integer.toHexString((updatingOSver << 16) + 3));
            }
            boolean backupAgg = Settings.Global.getInt(context.getContentResolver(), "wifi_watchdog_poor_network_aggressive_mode_on", 1) != 0;
            ContentResolver contentResolver = context.getContentResolver();
            if (backupAgg) {
                i = 1;
            }
            Settings.Global.putInt(contentResolver, "wifi_iwc_backup_aggressive_mode_on", i);
            Settings.Global.putInt(context.getContentResolver(), "wifi_watchdog_poor_network_aggressive_mode_on", 1);
            if (storedOSver != 0) {
                if (storedOSver != 1058) {
                    if (storedOSver != 67) {
                        if (storedOSver != 68) {
                        }
                    }
                }
            }
            if (mWatchdogVersionFromSettings != (updatingOSver << 16) + 3) {
                Log.d(TAG, "Version chaged. Updating the version...");
                Settings.Global.putInt(context.getContentResolver(), "wifi_watchdog_version", (updatingOSver << 16) + 3);
            }
        } catch (Exception e) {
            Log.e(TAG, "checkVersion - failed.");
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void updateTargetRssiForCurrentAP(boolean resetAggressiveMode) {
        ParameterManager parameterManager = this.mParam;
        parameterManager.mRssiThresholdAggModeCurrentAP = parameterManager.mRssiThresholdAggMode2G + 3;
        WifiInfo info = syncGetCurrentWifiInfo();
        WifiConfiguration config = getWifiConfiguration(info.getNetworkId());
        if (config == null) {
            Log.e(TAG, "updateTargetRssiForCurrentAP - config == null");
            return;
        }
        int targetRssi = config.nextTargetRssi;
        boolean is5G = info.getFrequency() > 4000;
        ParameterManager parameterManager2 = this.mParam;
        int defaultThreshold = (is5G ? parameterManager2.mRssiThresholdAggMode5G : parameterManager2.mRssiThresholdAggMode2G) + 3;
        if (targetRssi < defaultThreshold) {
            this.mParam.mRssiThresholdAggModeCurrentAP = targetRssi;
        } else {
            this.mParam.mRssiThresholdAggModeCurrentAP = defaultThreshold;
        }
        if (resetAggressiveMode) {
            this.mNetworkStatsAnalyzer.sendEmptyMessage(ACTIVITY_RESTART_AGGRESSIVE_MODE);
        }
        if (DBG) {
            Log.i(TAG, "updateTargetRssiForCurrentAP - SSID: " + config.SSID + ", frequency: " + info.getFrequency() + ", is5G: " + is5G + ", mParam.mRssiThreshold@CurrentAP: " + this.mParam.mRssiThresholdAggModeCurrentAP);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendBroadcastWCMTestResult(boolean valid) {
        Intent intent = new Intent("com.sec.android.WIFI_CONNECTIVITY_ACTION");
        intent.putExtra("valid", valid);
        try {
            this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Send broadcast WCM initial test result - action:" + intent.getAction());
        }
    }

    private void sendBroadCastWCMHideIcon(int visible) {
        if (DBG) {
            Log.d(TAG, "WCM vsb : " + visible);
        }
        sendMessage(EVENT_WIFI_ICON_VSB, visible);
        Intent intent = new Intent("com.sec.android.WIFI_ICON_HIDE_ACTION");
        intent.putExtra("visible", visible);
        try {
            this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Send broadcast WCM Hide wifi icon result - action:" + intent.getAction());
        }
        setWifiHideIconHistory(visible);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendBroadcastWCMStatusChanged() {
        Intent intent = new Intent("com.sec.android.WIFI_WCM_STATE_CHANGED_ACTION");
        try {
            this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Send broadcast WCM status changed - action:" + intent.getAction());
        }
    }

    /* access modifiers changed from: package-private */
    public void changeWifiIcon(int visible) {
        if (visible <= 1 && visible >= 0 && visible != this.mLastVisibilityOfWifiIcon) {
            if (visible != 0 || (!this.mConnectivityManager.getMultiNetwork() && isMobileDataConnected())) {
                sendBroadCastWCMHideIcon(visible);
                this.mLastVisibilityOfWifiIcon = visible;
            }
        }
    }

    /* access modifiers changed from: package-private */
    public void setForceWifiIcon(int visible) {
        sendBroadCastWCMHideIcon(visible);
        this.mLastVisibilityOfWifiIcon = visible;
    }

    private boolean isMobileDataConnected() {
        if (this.mTelephonyManager == null) {
            this.mTelephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
        }
        if (this.mTelephonyManager.getDataState() == 2) {
            if (!DBG) {
                return true;
            }
            Log.d(TAG, "isMobileDataConnected: true");
            return true;
        } else if (!DBG) {
            return false;
        } else {
            Log.d(TAG, "isMobileDataConnected: false");
            return false;
        }
    }

    public int getCurrentMode() {
        return this.mCurrentMode;
    }

    public int getCurrentStatusMode() {
        if (getCurrentState() == this.mLevel2State) {
            return 3;
        }
        if (getCurrentState() != this.mInvalidState) {
            return 0;
        }
        if (this.mPoorNetworkDetectionEnabled) {
            return 2;
        }
        return 1;
    }

    public boolean getValidState() {
        if (!mInitialResultSentToSystemUi || !isValidState() || getCurrentState() == this.mLevel2State) {
            return false;
        }
        return true;
    }

    public static int getEverQualityTested() {
        if (!mInitialResultSentToSystemUi || !mUserSelectionConfirmed) {
            return 0;
        }
        return 1;
    }

    public void setWifiEnabled(boolean enable, String bssid) {
        sendMessage(EVENT_WIFI_TOGGLED, enable ? 1 : 0, 0, bssid);
    }

    public void networkRemoved(int netId) {
        sendMessage(EVENT_NETWORK_REMOVED, netId, 0, null);
    }

    public boolean reportNetworkConnectivityToNM(int step, int trigger) {
        return reportNetworkConnectivityToNM(false, step, trigger);
    }

    public boolean reportNetworkConnectivityToNM(boolean force, int step, int trigger) {
        if ((!force && trigger != 52 && ((!this.mIsScreenOn && mInitialResultSentToSystemUi) || isMobileHotspot())) || this.mIsInRoamSession || this.mIsInDhcpSession) {
            return false;
        }
        if (this.mNetwork != null) {
            if (this.mWCMQCResult != null) {
                Log.d(TAG, "QC is already queried to NM");
                return true;
            }
            Log.d(TAG, "QC is queried to NM. Waiting for result");
            removeMessages(QC_RESULT_NOT_RECEIVED);
            this.mWCMQCResult = obtainMessage();
            getCm().reportNetworkConnectivityForResult(this.mNetwork, this.mWCMQCResult);
            QcFailHistory qcFailHistory = this.mInvalidationFailHistory;
            qcFailHistory.qcStep = step;
            qcFailHistory.qcTrigger = trigger;
            qcFailHistory.qcStepTemp = -1;
            sendMessageDelayed(QC_RESULT_NOT_RECEIVED, 20000);
            if (isValidState()) {
                setLoggingForTCPStat(TCP_STAT_LOGGING_FIRST);
            }
        }
        return true;
    }

    /* access modifiers changed from: private */
    public class NetworkCallbackController {
        private static final String TAG = "WifiConnectivityMonitor.NetworkCallbackController";
        private ConnectivityManager.NetworkCallback mCaptivePortalCallback;
        private ConnectivityManager.NetworkCallback mConnectionDetector;
        private ConnectivityManager.NetworkCallback mDefaultNetworkCallback;
        public boolean mDisableRequired = false;
        private boolean mHasCaptivePortalCapa = false;
        private boolean mHasValidatedCapa = false;
        private boolean mIsScoreChangedForCaptivePortal = false;
        public int mNetId = -1;
        private ConnectivityManager.NetworkCallback mNetworkCallback;
        private ConnectivityManager.NetworkCallback mNetworkCallbackDummy;

        public NetworkCallbackController() {
            init();
            this.mDisableRequired = false;
        }

        /* JADX WARNING: Code restructure failed: missing block: B:15:0x0056, code lost:
            if (r4.this$0.mWCMQCResult != null) goto L_0x0077;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:21:0x0075, code lost:
            if (r4.this$0.mWCMQCResult == null) goto L_0x007e;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:22:0x0077, code lost:
            r4.this$0.mWCMQCResult.recycle();
         */
        /* JADX WARNING: Code restructure failed: missing block: B:23:0x007e, code lost:
            r4.this$0.mWCMQCResult = null;
            r4.mHasCaptivePortalCapa = false;
            r4.mIsScoreChangedForCaptivePortal = false;
            r4.mHasValidatedCapa = false;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:24:0x008c, code lost:
            if (r4.mConnectionDetector != null) goto L_?;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:25:0x008e, code lost:
            registerConnectionDetector();
         */
        /* JADX WARNING: Code restructure failed: missing block: B:31:?, code lost:
            return;
         */
        /* JADX WARNING: Code restructure failed: missing block: B:32:?, code lost:
            return;
         */
        public void init() {
            try {
                if (this.mNetworkCallback != null) {
                    WifiConnectivityMonitor.this.getCm().unregisterNetworkCallback(this.mNetworkCallback);
                }
                if (this.mNetworkCallbackDummy != null) {
                    WifiConnectivityMonitor.this.getCm().unregisterNetworkCallback(this.mNetworkCallbackDummy);
                }
                if (this.mCaptivePortalCallback != null) {
                    WifiConnectivityMonitor.this.getCm().unregisterNetworkCallback(this.mCaptivePortalCallback);
                }
                if (this.mDefaultNetworkCallback != null) {
                    WifiConnectivityMonitor.this.getCm().unregisterNetworkCallback(this.mDefaultNetworkCallback);
                }
                this.mNetworkCallback = null;
                this.mNetworkCallbackDummy = null;
                this.mCaptivePortalCallback = null;
                this.mDefaultNetworkCallback = null;
                WifiConnectivityMonitor.this.mNetwork = null;
                WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.QC_RESULT_NOT_RECEIVED);
            } catch (Exception e) {
                e.printStackTrace();
                this.mNetworkCallback = null;
                this.mNetworkCallbackDummy = null;
                this.mCaptivePortalCallback = null;
                this.mDefaultNetworkCallback = null;
                WifiConnectivityMonitor.this.mNetwork = null;
                WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.QC_RESULT_NOT_RECEIVED);
            } catch (Throwable th) {
                this.mNetworkCallback = null;
                this.mNetworkCallbackDummy = null;
                this.mCaptivePortalCallback = null;
                this.mDefaultNetworkCallback = null;
                WifiConnectivityMonitor.this.mNetwork = null;
                WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.QC_RESULT_NOT_RECEIVED);
                if (WifiConnectivityMonitor.this.mWCMQCResult != null) {
                    WifiConnectivityMonitor.this.mWCMQCResult.recycle();
                }
                WifiConnectivityMonitor.this.mWCMQCResult = null;
                throw th;
            }
        }

        public void handleConnected() {
            registerNetworkCallbacks();
        }

        public boolean isCaptivePortal() {
            boolean z = this.mHasCaptivePortalCapa;
            if (z) {
                return z;
            }
            WifiConnectivityMonitor wifiConnectivityMonitor = WifiConnectivityMonitor.this;
            wifiConnectivityMonitor.mWifiInfo = wifiConnectivityMonitor.syncGetCurrentWifiInfo();
            int networkId = -1;
            if (WifiConnectivityMonitor.this.mWifiInfo != null) {
                networkId = WifiConnectivityMonitor.this.mWifiInfo.getNetworkId();
            }
            WifiConfiguration wifiConfiguration = null;
            if (networkId != -1) {
                wifiConfiguration = WifiConnectivityMonitor.this.getWifiConfiguration(networkId);
            }
            return wifiConfiguration != null && wifiConfiguration.isCaptivePortal;
        }

        private void registerConnectionDetector() {
            NetworkRequest.Builder req = new NetworkRequest.Builder();
            req.addTransportType(1);
            req.removeCapability(6);
            this.mConnectionDetector = new ConnectivityManager.NetworkCallback() {
                /* class com.android.server.wifi.WifiConnectivityMonitor.NetworkCallbackController.C04431 */

                public void onAvailable(Network network) {
                    Log.i(NetworkCallbackController.TAG, "mConnectionDetector: " + network.toString());
                    NetworkCapabilities nc = WifiConnectivityMonitor.this.getCm().getNetworkCapabilities(network);
                    if (nc == null) {
                        Log.e(NetworkCallbackController.TAG, "mConnectionDetector ignore this network. NetworkCapabilities instance is null");
                    } else if (nc.hasCapability(6)) {
                        Log.i(NetworkCallbackController.TAG, "mConnectionDetector ignore this network. It is Wifi_p2p");
                    } else {
                        if (WifiConnectivityMonitor.this.isConnectedState() && WifiConnectivityMonitor.this.mNetwork != network) {
                            try {
                                Log.d(NetworkCallbackController.TAG, "onAvailable called on different network instance");
                                if (WifiConnectivityMonitor.this.mNetwork != null) {
                                    Log.d(NetworkCallbackController.TAG, "OLD NETWORK : " + WifiConnectivityMonitor.this.mNetwork);
                                }
                                Log.d(NetworkCallbackController.TAG, "NEW NETWORK : " + network);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        if (!WifiConnectivityMonitor.this.isConnectedState()) {
                            WifiConnectivityMonitor.this.mNetwork = network;
                            WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.HANDLE_ON_AVAILABLE);
                        }
                    }
                }

                public void onLost(Network network) {
                    Log.i(NetworkCallbackController.TAG, "mConnectionDetector onLost : " + network.toString());
                    if (WifiConnectivityMonitor.this.mNetwork == network) {
                        WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.HANDLE_ON_LOST);
                        NetworkCallbackController.this.init();
                        return;
                    }
                    try {
                        Log.d(NetworkCallbackController.TAG, "onLost called on different network instance (ignore)");
                        if (WifiConnectivityMonitor.this.mNetwork != null) {
                            Log.d(NetworkCallbackController.TAG, "OLD NETWORK : " + WifiConnectivityMonitor.this.mNetwork);
                        }
                        Log.d(NetworkCallbackController.TAG, "NEW NETWORK : " + network);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            WifiConnectivityMonitor.this.getCm().registerNetworkCallback(req.build(), this.mConnectionDetector);
        }

        private void registerNetworkCallbacks() {
            NetworkRequest.Builder req = new NetworkRequest.Builder();
            req.addTransportType(1);
            this.mNetworkCallback = getCallback();
            WifiConnectivityMonitor.this.getCm().registerNetworkCallback(req.build(), this.mNetworkCallback);
            new NetworkRequest.Builder();
            req.addTransportType(1);
            this.mNetworkCallbackDummy = getDummyCallback();
            WifiConnectivityMonitor.this.getCm().requestNetwork(req.build(), this.mNetworkCallbackDummy);
            NetworkRequest.Builder req2 = new NetworkRequest.Builder();
            req2.addTransportType(1);
            req2.addCapability(17);
            this.mCaptivePortalCallback = getCaptivePortalCallback();
            WifiConnectivityMonitor.this.getCm().registerNetworkCallback(req2.build(), this.mCaptivePortalCallback);
            this.mDefaultNetworkCallback = getDefaultNetworkCallback();
            WifiConnectivityMonitor.this.getCm().registerDefaultNetworkCallback(this.mDefaultNetworkCallback);
        }

        private ConnectivityManager.NetworkCallback getDefaultNetworkCallback() {
            return new ConnectivityManager.NetworkCallback() {
                /* class com.android.server.wifi.WifiConnectivityMonitor.NetworkCallbackController.C04442 */

                public void onAvailable(Network network) {
                    NetworkCapabilities np = WifiConnectivityMonitor.this.getCm().getNetworkCapabilities(network);
                    if (np != null && np.hasTransport(3)) {
                        try {
                            if (NetworkCallbackController.this.mNetworkCallbackDummy != null) {
                                WifiConnectivityMonitor.this.getCm().unregisterNetworkCallback(NetworkCallbackController.this.mNetworkCallbackDummy);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } catch (Throwable th) {
                            NetworkCallbackController.this.mNetworkCallbackDummy = null;
                            throw th;
                        }
                        NetworkCallbackController.this.mNetworkCallbackDummy = null;
                    }
                }

                public void onLost(Network network) {
                }
            };
        }

        private ConnectivityManager.NetworkCallback getCallback() {
            return new ConnectivityManager.NetworkCallback() {
                /* class com.android.server.wifi.WifiConnectivityMonitor.NetworkCallbackController.C04453 */

                public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
                    Log.i(NetworkCallbackController.TAG, "mNetworkCallback onCapabilitiesChanged: " + network.toString() + nc.toString());
                    if (nc.hasCapability(16)) {
                        if (!NetworkCallbackController.this.mHasValidatedCapa) {
                            NetworkCallbackController.this.mHasValidatedCapa = true;
                            WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.VALIDATED_DETECTED);
                        }
                        if (NetworkCallbackController.this.mHasCaptivePortalCapa) {
                            WifiConnectivityMonitor.this.getClientModeChannel().updateNetworkSelectionStatus(NetworkCallbackController.this.mNetId, 0);
                        }
                    } else if (NetworkCallbackController.this.mHasValidatedCapa && WifiConnectivityMonitor.mInitialResultSentToSystemUi) {
                        NetworkCallbackController.this.mHasValidatedCapa = false;
                        WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.INVALIDATED_DETECTED);
                    }
                }
            };
        }

        private ConnectivityManager.NetworkCallback getDummyCallback() {
            return new ConnectivityManager.NetworkCallback() {
                /* class com.android.server.wifi.WifiConnectivityMonitor.NetworkCallbackController.C04464 */

                public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
                }

                public void onLost(Network network) {
                }
            };
        }

        private ConnectivityManager.NetworkCallback getCaptivePortalCallback() {
            return new ConnectivityManager.NetworkCallback() {
                /* class com.android.server.wifi.WifiConnectivityMonitor.NetworkCallbackController.C04475 */

                public void onAvailable(Network network) {
                    Log.i(NetworkCallbackController.TAG, "mCaptivePortalCallback: " + network.toString());
                }

                public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
                    Log.i(NetworkCallbackController.TAG, "mCaptivePortalCallback onCapabilitiesChanged: " + network.toString() + nc.toString());
                    if (nc.hasCapability(17)) {
                        WifiConnectivityMonitor.this.mWifiInfo = WifiConnectivityMonitor.this.syncGetCurrentWifiInfo();
                        if (!NetworkCallbackController.this.mHasCaptivePortalCapa) {
                            if (WifiConnectivityMonitor.this.mCurrentMode == 1 && !WifiConnectivityMonitor.this.mAirPlaneMode) {
                                WifiConnectivityMonitor.this.getCm().setWcmAcceptUnvalidated(WifiConnectivityMonitor.this.mNetwork, false);
                            }
                            NetworkCallbackController.this.mHasCaptivePortalCapa = true;
                            NetworkCallbackController networkCallbackController = NetworkCallbackController.this;
                            networkCallbackController.mNetId = WifiConnectivityMonitor.this.mWifiInfo.getNetworkId();
                            Log.i(NetworkCallbackController.TAG, "Disable an unauthenticated Captive Portal AP");
                            WifiConnectivityMonitor.this.getClientModeChannel().updateNetworkSelectionStatus(NetworkCallbackController.this.mNetId, 15);
                            WifiConnectivityMonitor.this.getClientModeChannel().setCaptivePortal(NetworkCallbackController.this.mNetId, true);
                            WifiConnectivityMonitor.this.removeMessages(WifiConnectivityMonitor.STOP_BLINKING_WIFI_ICON);
                        }
                        if (WifiConnectivityMonitor.getEverQualityTested() == 1) {
                            int networkId = -1;
                            if (WifiConnectivityMonitor.this.mWifiInfo != null) {
                                networkId = WifiConnectivityMonitor.this.mWifiInfo.getNetworkId();
                            }
                            WifiConfiguration wifiConfiguration = null;
                            if (networkId != -1) {
                                wifiConfiguration = WifiConnectivityMonitor.this.getWifiConfiguration(networkId);
                            }
                            if (wifiConfiguration != null && wifiConfiguration.isCaptivePortal && !NetworkCallbackController.this.mIsScoreChangedForCaptivePortal) {
                                WifiConnectivityMonitor.this.setWifiNetworkEnabled(false);
                                NetworkCallbackController.this.mIsScoreChangedForCaptivePortal = true;
                            }
                        }
                        if (WifiConnectivityMonitor.this.getCurrentState() != WifiConnectivityMonitor.this.mCaptivePortalState) {
                            WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.CAPTIVE_PORTAL_DETECTED);
                        }
                    }
                }

                public void onLost(Network network) {
                    Log.i(NetworkCallbackController.TAG, "mCaptivePortalCallback onLost : " + network.toString());
                    if (NetworkCallbackController.this.mIsScoreChangedForCaptivePortal) {
                        NetworkCallbackController.this.mIsScoreChangedForCaptivePortal = false;
                        WifiConnectivityMonitor.this.setWifiNetworkEnabled(true);
                    }
                }
            };
        }
    }

    public void setCaptivePortalMode(int enabled) {
        Settings.Global.putInt(this.mContext.getContentResolver(), WCMCallbacks.CAPTIVE_PORTAL_MODE, enabled);
    }

    public class WCMCallbacks implements ClientModeImpl.ClientModeChannel.WifiNetworkCallback {
        public static final String CAPTIVE_PORTAL_MODE = "captive_portal_mode";
        public static final String TAG = "WifiConnectivityMonitor.WCMCallbacks";

        public WCMCallbacks() {
            Log.i(TAG, "WCMCallbacks created");
        }

        @Override // com.android.server.wifi.ClientModeImpl.ClientModeChannel.WifiNetworkCallback
        public void checkIsCaptivePortalException(String ssid) {
            ContentResolver contentResolver = WifiConnectivityMonitor.this.mContext.getContentResolver();
            boolean isCaptivePortalCheckEnabled = !WifiConnectivityMonitor.this.isCaptivePortalExceptionOnly(ssid) && !WifiConnectivityMonitor.this.isIgnorableNetwork(ssid);
            Log.i(TAG, "isCaptivePortalCheckEnabled result on " + ssid + ": " + isCaptivePortalCheckEnabled);
            boolean unused = WifiConnectivityMonitor.mIsNoCheck = !isCaptivePortalCheckEnabled;
            if (isCaptivePortalCheckEnabled) {
                WifiConnectivityMonitor.this.setCaptivePortalMode(1);
                return;
            }
            WifiConnectivityMonitor.this.setCaptivePortalMode(0);
            int ret = Settings.Global.getInt(contentResolver, CAPTIVE_PORTAL_MODE, 1);
            Log.i(TAG, "It is CaptivePortalCheck Enabled - : " + ret);
            if (ret != 0) {
                for (int idx = 0; idx < 10; idx++) {
                    WifiConnectivityMonitor.this.setCaptivePortalMode(0);
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    ret = Settings.Global.getInt(contentResolver, CAPTIVE_PORTAL_MODE, 1);
                    if (ret == 0) {
                        break;
                    }
                }
                Log.i(TAG, "It is CaptivePortalCheck Enabled(1000 later) - : " + ret);
            }
        }

        @Override // com.android.server.wifi.ClientModeImpl.ClientModeChannel.WifiNetworkCallback
        public void notifyRoamSession(String startComplete) {
            WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.CMD_ROAM_START_COMPLETE, 0, 0, startComplete);
        }

        @Override // com.android.server.wifi.ClientModeImpl.ClientModeChannel.WifiNetworkCallback
        public void notifyDhcpSession(String startComplete) {
            if ("start".equals(startComplete)) {
                WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_DHCP_SESSION_STARTED);
            } else if ("complete".equals(startComplete)) {
                WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.EVENT_DHCP_SESSION_COMPLETE);
            }
        }

        @Override // com.android.server.wifi.ClientModeImpl.ClientModeChannel.WifiNetworkCallback
        public void notifyLinkPropertiesUpdated(LinkProperties lp) {
            WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.CMD_NETWORK_PROPERTIES_UPDATED, 0, 0, lp);
        }

        @Override // com.android.server.wifi.ClientModeImpl.ClientModeChannel.WifiNetworkCallback
        public void notifyReachabilityLost() {
            WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.CMD_REACHABILITY_LOST);
        }

        @Override // com.android.server.wifi.ClientModeImpl.ClientModeChannel.WifiNetworkCallback
        public void notifyProvisioningFail() {
            WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.CMD_PROVISIONING_FAIL);
        }

        @Override // com.android.server.wifi.ClientModeImpl.ClientModeChannel.WifiNetworkCallback
        public void handleResultRoamInLevel1State(boolean roamFound) {
            Log.d(TAG, "HANDLE_RESULT_ROAM_IN_HIGH_QUALITY - roamFound: " + roamFound);
            WifiConnectivityMonitor.this.sendMessage(WifiConnectivityMonitor.HANDLE_RESULT_ROAM_IN_HIGH_QUALITY, roamFound ? 1 : 0, 0, null);
        }
    }

    public void setUserSelection(boolean keepConnection) {
        if (DBG) {
            Log.e(TAG, "setUserSelect : " + keepConnection);
        }
        sendMessage(EVENT_USER_SELECTION, keepConnection ? 1 : 0);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean skipPoorConnectionReport() {
        if (this.mWifiInfo == null || !this.mClientModeImpl.isConnected() || getCurrentState() != this.mLevel1State || this.mWifiInfo.getRssi() <= -80 || !this.mIsRoamingNetwork) {
            return false;
        }
        Log.d(TAG, "skipPoorConnectionReport - Condition satisfied.");
        return true;
    }

    public WifiConfiguration getWifiConfiguration(int netID) {
        if (netID == -1) {
            return null;
        }
        return this.mWifiConfigManager.getConfiguredNetwork(netID);
    }

    public boolean isCaptivePortalExceptionOnly(String _ssid) {
        int reason = -1;
        String ssid = null;
        int networkId = -1;
        WifiInfo wifiInfo = this.mWifiInfo;
        if (wifiInfo != null && _ssid == null) {
            ssid = wifiInfo.getSSID();
            networkId = this.mWifiInfo.getNetworkId();
        }
        if (ssid == null && _ssid != null) {
            ssid = _ssid;
        }
        WifiConfiguration wifiConfiguration = null;
        if (networkId != -1) {
            wifiConfiguration = getWifiConfiguration(networkId);
        }
        if (!(!"WeChatWiFi".equals(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGSOCIALSVCINTEGRATIONN)) || networkId == -1 || this.mWifiManager == null || wifiConfiguration == null)) {
            Log.d(TAG, "isCaptivePortalExceptionOnly, isWeChatAp: " + wifiConfiguration.semIsWeChatAp);
        }
        if ("CCT".equals(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CAPTIVEPORTALEXCEPTION)) && isPackageExists("com.smithmicro.netwise.director.comcast.oem") && isComcastSsid(ssid)) {
            reason = 1;
        } else if ("\"attwifi\"".equals(ssid) && "FINISH".equals(SystemProperties.get("persist.sys.setupwizard"))) {
            reason = 2;
        } else if ("\"SFR WiFi\"".equals(ssid) || "\"SFR WiFi Public\"".equals(ssid) || "\"SFR WiFi Gares\"".equals(ssid) || "\"SFR WiFi FON\"".equals(ssid) || "\"WiFi Partenaires\"".equals(ssid)) {
            reason = 4;
        } else if (!"FINISH".equals(SystemProperties.get("persist.sys.setupwizard")) && readReactiveLockFlag(this.mContext)) {
            reason = 5;
        } else if ("\"CelcomWifi\"".equals(ssid)) {
            reason = 6;
        } else if ("\"O2 Wifi\"".equals(ssid)) {
            reason = 7;
        } else if ("\"UL Mobile\"".equals(ssid)) {
            reason = 8;
        } else if (this.CSC_WIFI_SUPPORT_VZW_EAP_AKA && wifiConfiguration != null && wifiConfiguration.semIsVendorSpecificSsid) {
            reason = 9;
        } else if (this.mIsFmcNetwork) {
            reason = 10;
        } else if (("WeChatWiFi".equals(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGSOCIALSVCINTEGRATIONN)) && isPackageRunning(this.mContext, "com.tencent.mm")) || (wifiConfiguration != null && wifiConfiguration.semIsWeChatAp)) {
            reason = 11;
        }
        if (reason == -1) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(DBG ? "isCaptivePortalExceptionOnly - reason #" : "CPEO #");
        sb.append(reason);
        logd(sb.toString());
        return true;
    }

    public boolean isIgnorableNetwork(String _ssid) {
        int reason = -1;
        String ssid = null;
        int networkId = -1;
        WifiInfo wifiInfo = this.mWifiInfo;
        if (wifiInfo != null && _ssid == null) {
            ssid = wifiInfo.getSSID();
            networkId = this.mWifiInfo.getNetworkId();
        }
        if (ssid == null && _ssid != null) {
            ssid = _ssid;
        }
        WifiConfiguration wifiConfiguration = null;
        if (networkId != -1) {
            wifiConfiguration = getWifiConfiguration(networkId);
        }
        if ("ATT".equals(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CAPTIVEPORTALEXCEPTION)) && isPackageRunning(this.mContext, "com.synchronoss.dcs.att.r2g")) {
            reason = 1;
        } else if (ssid != null && ssid.contains("DIRECT-") && ssid.contains(":NEX-")) {
            reason = 2;
        } else if (isPackageRunning(this.mContext, "de.telekom.hotspotlogin")) {
            reason = 3;
        } else if (isPackageRunning(this.mContext, "com.belgacom.fon")) {
            reason = 4;
        } else if ("CHM".equals(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CAPTIVEPORTALEXCEPTION)) && (isPackageRunning(this.mContext, "com.chinamobile.cmccwifi") || isPackageRunning(this.mContext, "com.chinamobile.cmccwifi.WelcomeActivity") || isPackageRunning(this.mContext, "com.chinamobile.cmccwifi.MainActivity") || isPackageRunning(this.mContext, "com.android.settings.wifi.CMCCChargeWarningDialog"))) {
            reason = 6;
        } else if (("\"au_Wi-Fi\"".equals(ssid) || "\"Wi2\"".equals(ssid) || "\"Wi2premium\"".equals(ssid) || "\"Wi2premium_club\"".equals(ssid) || "\"UQ_Wi-Fi\"".equals(ssid) || "\"wifi_square\"".equals(ssid)) && (isPackageExists("com.kddi.android.au_wifi_connect") || isPackageExists("com.kddi.android.au_wifi_connect2"))) {
            reason = 7;
        } else if (FactoryTest.isFactoryBinary()) {
            reason = 8;
        } else if ("\"mailsky\"".equals(ssid) && this.mIsUsingProxy) {
            reason = 9;
        } else if ("\"COPconnect\"".equals(ssid) && wifiConfiguration.allowedKeyManagement.get(2)) {
            reason = 10;
        } else if ("\"SpirentATTEVSAP\"".equals(ssid)) {
            reason = 11;
        }
        if (reason == -1) {
            return false;
        }
        Log.d(TAG, "isIgnorableNetwork - No need to check connectivity: " + ssid + ", reason: " + reason);
        return true;
    }

    private boolean isPackageExists(String targetPackage) {
        try {
            PackageInfo info = this.mContext.getPackageManager().getPackageInfo(targetPackage, 0);
            if (info != null) {
                Log.d(TAG, "isPackageExists - matched: " + info.packageName);
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            if (DBG) {
                Log.w(TAG, "NameNotFoundException + " + e.toString());
            }
        }
        return false;
    }

    private boolean isPackageRunning(Context context, String packageName) {
        if (context == null) {
            return false;
        }
        for (ActivityManager.RunningTaskInfo runningTaskInfo : ((ActivityManager) context.getSystemService("activity")).getRunningTasks(1)) {
            Log.d(TAG, "isPackageRunning - top:" + runningTaskInfo.topActivity.getClassName());
            if (runningTaskInfo.topActivity.getPackageName().contains(packageName)) {
                return true;
            }
        }
        return false;
    }

    private static String getAccountEmail(Context context, String account_type) {
        String accountEmail = null;
        Account[] accountArray = AccountManager.get(context).getAccountsByType(account_type);
        if (accountArray.length > 0) {
            accountEmail = accountArray[0].name;
        }
        if (DBG) {
            Log.d(TAG, "getAccountEmail : " + accountEmail);
        }
        return accountEmail;
    }

    private static boolean readReactiveLockFlag(Context context) {
        boolean value = false;
        int flagResult = new ReactiveServiceManager(context).getStatus();
        if (flagResult < 0 || flagResult > 1) {
            Log.e(TAG, "readReactiveLockFlag - exception occured:" + flagResult);
        } else if (flagResult == 1 && getAccountEmail(context, "com.google") == null) {
            Log.d(TAG, "readReactiveLockFlag : Activated - " + flagResult);
            value = true;
        }
        if (DBG) {
            Log.d(TAG, "readReactiveLockFlag - result: " + value);
        }
        return value;
    }

    /* access modifiers changed from: private */
    public class TxPacketInfo {
        private static final int DISCONNECTED = 3;
        private static final int FAILED = 2;
        private static final int SUCCESS = 1;
        private int mTxbad;
        private int mTxgood;
        private int result;

        private TxPacketInfo() {
        }
    }

    private void eleCheck(int txBad) {
        try {
            if (this.mWifiEleStateTracker == null) {
                return;
            }
            if (this.mWifiEleStateTracker.getEleCheckDoorOpenState()) {
                this.mWifiEleStateTracker.checkEleDoorOpen(this.mTelephonyManager.getSignalStrength().getDbm(), getWifiEleBeaconStats(), getCurrentRssi());
            } else if (!this.mWifiEleStateTracker.getEleCheckEnabled()) {
            } else {
                if (this.mWifiEleStateTracker.getElePollingEnabled()) {
                    int retVal = this.mWifiEleStateTracker.checkEleEnvironment(this.mTelephonyManager.getSignalStrength().getDbm(), getWifiEleBeaconStats(), getCurrentRssi(), txBad);
                    if (retVal != 0) {
                        if (retVal == 2) {
                            sendMessage(CMD_ELE_DETECTED);
                        } else {
                            sendMessage(CMD_ELE_BY_GEO_DETECTED);
                        }
                        if (getClientModeChannel().eleDetectedDebug()) {
                            Toast.makeText(this.mContext, "Ele!", 0).show();
                        }
                    } else {
                        this.mWifiEleStateTracker.setElePollingSkip(false);
                    }
                    return;
                }
                this.mWifiEleStateTracker.setElePollingSkip(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "mWifiEleStateTracker exception happend : " + e.toString());
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private TxPacketInfo fetchPacketCount() {
        Message msg = getClientModeChannel().fetchPacketCountNative();
        TxPacketInfo txPacketInfo = new TxPacketInfo();
        int i = msg.what;
        if (i == 2) {
            txPacketInfo.result = 1;
            txPacketInfo.mTxgood = msg.arg1;
            txPacketInfo.mTxbad = msg.arg2;
            eleCheck(txPacketInfo.mTxbad);
            if (this.mScoreQualityCheckMode != 0) {
                boolean goPolling = false;
                if (!this.mScoreSkipModeEnalbed) {
                    goPolling = true;
                } else if (this.mScoreSkipPolling) {
                    this.mScoreSkipPolling = false;
                } else {
                    this.mScoreSkipPolling = true;
                    goPolling = true;
                }
                if (goPolling) {
                    int i2 = this.mScoreIntervalCnt;
                    this.mScoreIntervalCnt = i2 + 1;
                    if (i2 == 0) {
                        checkScoreBasedQuality(this.mClientModeImpl.getWifiScoreReport().getCurrentScore(), txPacketInfo.mTxgood, txPacketInfo.mTxbad);
                    }
                    if (this.mScoreIntervalCnt >= 3) {
                        this.mScoreIntervalCnt = 0;
                    }
                }
            }
        } else if (i == 3) {
            txPacketInfo.result = 2;
        } else if (i != 4) {
            txPacketInfo.result = 2;
        }
        msg.recycle();
        return txPacketInfo;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setWifiNetworkEnabled(boolean valid) {
        Log.d(TAG, "set Wifi Network Enabled : " + valid);
        getClientModeChannel().setWifiNetworkEnabled(valid);
    }

    public double getCurrentLoss() {
        VolumeWeightedEMA volumeWeightedEMA = this.mCurrentLoss;
        if (volumeWeightedEMA != null) {
            return volumeWeightedEMA.mValue;
        }
        return POOR_LINK_MIN_VOLUME;
    }

    public void fastDisconnectUpdateRssi(int rssi) {
        if (DBG) {
            Log.d(TAG, "fastDisconnectUpdateRssi: Enter. " + rssi);
        }
        if (this.mRawRssi.size() >= this.mParam.FD_RAW_RSSI_SIZE) {
            this.mRawRssi.removeLast();
        }
        this.mRawRssi.addFirst(Long.valueOf((long) rssi));
    }

    /* JADX INFO: Multiple debug info for r8v2 double: [D('currentMARssi' double), D('diffMARssi' double)] */
    public boolean fastDisconnectEvaluate() {
        int count = 0;
        double total = -0.0d;
        double oldestMARssi = -200.0d;
        double latestMARssi = -200.0d;
        double currentMARssi = -200.0d;
        if (DBG) {
            Log.d(TAG, "fastDisconnectEvaluate: Enter.");
        }
        if (this.mRawRssi.size() < this.mParam.FD_RAW_RSSI_SIZE) {
            if (DBG) {
                Log.d(TAG, "Not enough data to evaluate FD.");
            }
            return false;
        }
        for (int i = 0; i < this.mParam.FD_EVALUATE_COUNT; i++) {
            int j = 0;
            while (j < this.mParam.FD_MA_UNIT_SAMPLE_COUNT) {
                total += (double) this.mRawRssi.get(j + count).longValue();
                j++;
                currentMARssi = currentMARssi;
            }
            currentMARssi = total / ((double) this.mParam.FD_MA_UNIT_SAMPLE_COUNT);
            if (i == 0) {
                latestMARssi = currentMARssi;
            } else if (i == this.mParam.FD_EVALUATE_COUNT - 1) {
                oldestMARssi = currentMARssi;
            }
            count++;
            total = POOR_LINK_MIN_VOLUME;
        }
        double diffMARssi = oldestMARssi - latestMARssi;
        if (DBG) {
            Log.d(TAG, "fastDisconnectEvaluate: oldest=" + oldestMARssi + ", latest=" + latestMARssi + ", diff=" + (oldestMARssi - latestMARssi));
        }
        if (mRawRssiEMA == -200.0d) {
            mRawRssiEMA = diffMARssi;
        } else {
            Objects.requireNonNull(this.mParam);
            Objects.requireNonNull(this.mParam);
            mRawRssiEMA = (0.2d * diffMARssi) + (0.8d * mRawRssiEMA);
        }
        if (diffMARssi > this.mParam.FD_DISCONNECT_THRESHOLD) {
            if (DBG) {
                Log.d(TAG, "A sharp fall! Disconnect!");
            }
            return true;
        }
        double d = mRawRssiEMA;
        Objects.requireNonNull(this.mParam);
        if (d < 4.0d) {
            return false;
        }
        if (DBG) {
            Log.d(TAG, "A sharp fall trend! Disconnect!");
        }
        return true;
    }

    public void fastDisconnectClear() {
        for (int i = 0; i < this.mRawRssi.size(); i++) {
            this.mRawRssi.remove();
        }
        mRawRssiEMA = -200.0d;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setRssiPatchHistory(int txbad, int txgood, long rxgood) {
        setRssiPatchHistory(null, txbad, txgood, rxgood);
    }

    private void setRssiPatchHistory(String dumpLog, int txbad, int txgood, long rxgood) {
        StringBuilder builder = new StringBuilder();
        int i = this.mRssiPatchHistoryHead;
        if (i == -1) {
            this.mRssiPatchHistoryHead = i + 1;
        } else {
            this.mRssiPatchHistoryHead = i % RSSI_PATCH_HISTORY_COUNT_MAX;
        }
        String currentTime = "" + (System.currentTimeMillis() / 1000);
        try {
            currentTime = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        } catch (RuntimeException e) {
        }
        if (dumpLog == null) {
            try {
                builder.append(currentTime);
                builder.append(": " + txbad);
                builder.append(", " + txgood);
                builder.append(", " + rxgood);
                builder.append(", " + this.mWifiInfo.getRxLinkSpeedMbps());
            } catch (RuntimeException e2) {
                builder.append(currentTime + ", ex");
            }
        } else {
            builder.append(", " + dumpLog);
        }
        this.mRssiPatchHistory[this.mRssiPatchHistoryHead] = builder.toString();
        this.mRssiPatchHistoryHead++;
        this.mRssiPatchHistoryTotal++;
    }

    private void setWifiHideIconHistory(int visible) {
        StringBuilder builder = new StringBuilder();
        this.mHideIconHistoryHead %= 100;
        String currentTime = "" + (System.currentTimeMillis() / 1000);
        try {
            currentTime = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        } catch (RuntimeException e) {
        }
        try {
            builder.append(currentTime);
            builder.append(": " + visible);
        } catch (RuntimeException e2) {
            builder.append(currentTime + ", ex");
        }
        this.mHideIconHistory[this.mHideIconHistoryHead] = builder.toString();
        this.mHideIconHistoryHead++;
        this.mHideIconHistoryTotal++;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void uploadNoInternetECNTBigData() {
        int reason;
        Log.d(TAG, "uploadNoInternetECNTBigData");
        mIsECNTReportedConnection = true;
        if (this.mPreviousTxBadTxGoodRatio > 100) {
            reason = 2;
        } else {
            reason = 0;
        }
        this.mWifiNative.requestFwBigDataParams(INTERFACENAMEOFWLAN, 0, reason, 0);
        mIsECNTReportedConnection = true;
        mIsPassedLevel1State = false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void createEleObjects() {
        Log.d(TAG, "createEleObjects");
        if (this.mWifiEleStateTracker != null || !WifiEleStateTracker.checkPedometerSensorAvailable(this.mContext)) {
            Log.d(TAG, "createEleObjects ignored due to not available condition");
            return;
        }
        this.mWifiEleStateTracker = new WifiEleStateTracker(this.mContext, this.mWifiNative, this);
        Log.d(TAG, "createEleObjects done");
    }

    private int getWifiEleBeaconStats() {
        WifiLinkLayerStats stats = this.mWifiNative.getWifiLinkLayerStats(INTERFACENAMEOFWLAN);
        if (stats != null) {
            return stats.beacon_rx;
        }
        return 0;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void startEleCheck() {
        if (isLevel1StateState()) {
            int i = this.mCurrentMode;
            if ((i == 2 || i == 3) && this.mWifiEleStateTracker != null) {
                Log.i(TAG, "enableEleMobileRssiPolling true and enable WifiPedometerChecker by EVENT_SCREEN_ON");
                this.mWifiEleStateTracker.enableEleMobileRssiPolling(true, true);
                this.mWifiEleStateTracker.checkStepCntChangeForGeoMagneticSensor();
                this.mWifiEleStateTracker.screenSet(true);
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void screenOffEleInitialize() {
        WifiEleStateTracker wifiEleStateTracker = this.mWifiEleStateTracker;
        if (wifiEleStateTracker != null && wifiEleStateTracker.getScreenOffResetRequired()) {
            Log.i(TAG, "disable eleEleMobileRssiPolling and disable WifiPedometerChecker by EVENT_SCREEN_OFF");
            this.mWifiEleStateTracker.enableEleMobileRssiPolling(false, false);
            this.mWifiEleStateTracker.getCurrentStepCnt();
            if (getCurrentState() == this.mLevel2State && (this.mWifiEleStateTracker.checkEleValidBlockState() || this.mWifiNeedRecoveryFromEle)) {
                int i = this.mTrafficPollToken + 1;
                this.mTrafficPollToken = i;
                sendMessage(obtainMessage(CMD_TRAFFIC_POLL, i, 0));
                if (this.mCurrentMode == 2) {
                    this.mSnsBigDataSCNT.mPqGqNormal++;
                } else {
                    this.mSnsBigDataSCNT.mPqGqAgg++;
                }
                transitionTo(this.mLevel1State);
            }
            this.mWifiNeedRecoveryFromEle = false;
            this.mWifiEleStateTracker.clearEleValidBlockFlag();
            this.mWifiEleStateTracker.resetEleParameters(0, true, true);
            this.mWifiEleStateTracker.screenSet(false);
        }
    }

    private int getCurrentRssi() {
        WifiNative.SignalPollResult pollResult = this.mWifiNative.signalPoll(INTERFACENAMEOFWLAN);
        if (pollResult == null) {
            return -1;
        }
        return pollResult.currentRssi;
    }

    public void enableRecoveryFromEle() {
        if (getCurrentState() == this.mLevel2State && this.mCurrentMode == 2) {
            sendMessage(CMD_RECOVERY_TO_HIGH_QUALITY_FROM_ELE);
        } else {
            this.mWifiNeedRecoveryFromEle = true;
        }
    }

    public void eleCheckFinished() {
        determineMode();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void startPoorQualityScoreCheck() {
        Log.i(TAG, "SCORE_QUALITY_CHECK_STATE_POOR_MONITOR");
        this.mScoreQualityCheckMode = 2;
        int[] iArr = this.mPreviousScore;
        iArr[0] = 0;
        iArr[1] = 0;
        iArr[2] = 0;
        this.mPrevoiusScoreAverage = 0;
        this.mPreviousTxBad = 0;
        this.mPreviousTxSuccess = 0;
        this.mPreviousTxBadTxGoodRatio = 0;
        this.mLastGoodScore = 1000;
        this.mLastPoorScore = 100;
        this.mPoorCheckInProgress = false;
        this.mScoreIntervalCnt = 0;
        this.mWifiNeedRecoveryFromEle = false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void startRecoveryScoreCheck() {
        Log.i(TAG, "SCORE_QUALITY_CHECK_STATE_RECOVERY");
        this.mScoreQualityCheckMode = 4;
        this.mPreviousTxBad = 0;
        this.mPreviousTxSuccess = 0;
        this.mPreviousTxBadTxGoodRatio = 0;
        this.mEleGoodScoreCnt = 0;
    }

    private void startGoodQualityScoreCheck() {
        Log.i(TAG, "SCORE_QUALITY_CHECK_STATE_VALID_CHECK");
        this.mScoreQualityCheckMode = 1;
        this.mGoodScoreCount = 0;
        this.mGoodScoreTotal = 0;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void stopScoreQualityCheck() {
        Log.i(TAG, "SCORE_QUALITY_CHECK_STATE_NONE");
        this.mScoreQualityCheckMode = 0;
    }

    private void checkScoreBasedQuality(int s2Score, int txGood, int txBad) {
        long currentTxBadRatio;
        int i = this.mScoreQualityCheckMode;
        if (i >= 2) {
            long txBadDiff = ((long) txBad) - this.mPreviousTxBad;
            long TxGoodDiff = ((long) txGood) - this.mPreviousTxSuccess;
            int validScoreCnt = 0;
            this.mPreviousTxBad = (long) txBad;
            this.mPreviousTxSuccess = (long) txGood;
            if (i != 4) {
                if (s2Score <= 50) {
                    Log.i(TAG, "checkScoreBasedQuality - less than 50 : s2Score : " + s2Score);
                    if (s2Score < this.mLastPoorScore) {
                        this.mLastPoorScore = s2Score;
                    }
                }
                for (int x = 0; x < 3; x++) {
                    if (this.mPreviousScore[x] != 0) {
                        validScoreCnt++;
                    }
                }
                int[] iArr = this.mPreviousScore;
                int scoreTotal = iArr[0] + iArr[1] + iArr[2];
                if (validScoreCnt > 0) {
                    this.mPrevoiusScoreAverage = scoreTotal / validScoreCnt;
                } else {
                    this.mPrevoiusScoreAverage = s2Score;
                }
                if (this.mScoreQualityCheckMode != 3 || this.mLastGoodScore <= s2Score) {
                    if (txBadDiff > 5) {
                        if (TxGoodDiff > 0) {
                            currentTxBadRatio = (long) ((int) ((((float) txBadDiff) * 100.0f) / ((float) TxGoodDiff)));
                        } else {
                            currentTxBadRatio = txBadDiff + 100;
                        }
                        Log.i(TAG, "checkScoreBasedQuality -  currentTxBadRatio:" + currentTxBadRatio);
                        if (currentTxBadRatio > 15) {
                            if (this.mScoreQualityCheckMode != 2) {
                                Log.i(TAG, "SCORE_QUALITY_CHECK_STATE_POOR_CHECK:  mPreviousTxBadTxGoodRatio:" + this.mPreviousTxBadTxGoodRatio);
                                long j = this.mPreviousTxBadTxGoodRatio;
                                if (j != 0 && currentTxBadRatio > j) {
                                    if (this.mPoorCheckInProgress) {
                                        this.mPoorCheckInProgress = false;
                                    } else {
                                        Log.i(TAG, "checkScoreBasedQuality - Score Quality Check by txBadRatio increase");
                                        sendMessage(CMD_QUALITY_CHECK_BY_SCORE);
                                        this.mPoorCheckInProgress = true;
                                    }
                                }
                            } else if (s2Score * validScoreCnt < scoreTotal) {
                                Log.i(TAG, "SCORE_QUALITY_CHECK_STATE_POOR_CHECK");
                                this.mScoreQualityCheckMode = 3;
                                if (this.mPoorCheckInProgress) {
                                    this.mPoorCheckInProgress = false;
                                } else {
                                    this.mPoorCheckInProgress = true;
                                    Log.i(TAG, "checkScoreBasedQuality - Score Quality Check by averageScore decrease");
                                    sendMessage(CMD_QUALITY_CHECK_BY_SCORE);
                                }
                            }
                            if (this.mPreviousTxBadTxGoodRatio < currentTxBadRatio) {
                                this.mPreviousTxBadTxGoodRatio = currentTxBadRatio;
                            }
                        }
                    }
                } else if (this.mPoorCheckInProgress) {
                    this.mPoorCheckInProgress = false;
                } else {
                    this.mPoorCheckInProgress = true;
                    Log.i(TAG, "checkScoreBasedQuality - Score Quality Check by score decrease");
                    sendMessage(CMD_QUALITY_CHECK_BY_SCORE);
                }
                int[] iArr2 = this.mPreviousScore;
                iArr2[0] = iArr2[1];
                iArr2[1] = iArr2[2];
                if (!this.mPoorCheckInProgress && this.mLastGoodScore > iArr2[2]) {
                    this.mLastGoodScore = iArr2[2];
                }
                this.mPreviousScore[2] = s2Score;
            } else if (this.mWifiNeedRecoveryFromEle) {
                Log.i(TAG, "checkScoreBasedQuality recovery condition check from Ele");
                if (s2Score <= 50 || txBadDiff != 0 || TxGoodDiff <= 0) {
                    this.mEleGoodScoreCnt = 0;
                } else {
                    this.mEleGoodScoreCnt++;
                }
                if (this.mEleGoodScoreCnt >= 2 || (s2Score >= this.mTransitionScore + 3 && txBadDiff == 0)) {
                    sendMessage(CMD_RECOVERY_TO_HIGH_QUALITY_FROM_ELE);
                }
            }
        } else {
            this.mGoodScoreCount++;
            this.mGoodScoreTotal += s2Score;
            if (this.mGoodScoreCount >= 3) {
                int newAverage = this.mGoodScoreTotal / 3;
                Log.i(TAG, "checkScoreBasedQuality - newAverage: " + newAverage);
                if (newAverage > this.mPrevoiusScoreAverage) {
                    Log.i(TAG, "checkScoreBasedQuality - Score Quality Check by score increase");
                    sendMessage(CMD_QUALITY_CHECK_BY_SCORE);
                    this.mPrevoiusScoreAverage = newAverage;
                }
                this.mGoodScoreTotal = 0;
                this.mGoodScoreCount = 0;
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void showNetworkSwitchedNotification(boolean visible) {
        String ssid = this.mWifiInfo.getSSID();
        int networkId = this.mWifiInfo.getNetworkId();
        if ("DEFAULT_ON".equals(SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGSNSSTATUS))) {
            if (!visible || Settings.Secure.getInt(this.mContext.getContentResolver(), "wifi_poor_connection_warning", 0) != 1) {
                if (!visible) {
                    this.mPoorConnectionDisconnectedNetId = -1;
                }
                this.mNotifier.showWifiPoorConnectionNotification(ssid, networkId, visible);
                return;
            }
            Log.e(TAG, "Ignore msg from WCM because of WIFI_POOR_CONNECTION_WARNING(DoNotShow flag is true)");
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void logStateAndMessage(Message message, State state) {
        if (SMARTCM_DBG) {
            Log.i(TAG, " " + state.getClass().getSimpleName() + " " + getLogRecString(message));
        }
    }

    private boolean isComcastSsid(String ssid) {
        if (!mIsComcastWifiSupported || TextUtils.isEmpty(ssid)) {
            return false;
        }
        String ssid2 = ssid.replace("\"", "");
        Cursor cursor = this.mContext.getContentResolver().query(Uri.parse(NETWISE_CONTENT_URL), null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    do {
                        String netwiseSsid = cursor.getString(cursor.getColumnIndex("network"));
                        Log.d(TAG, "netwiseSsid = " + netwiseSsid);
                        if (ssid2.equals(netwiseSsid)) {
                            return true;
                        }
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        return false;
    }

    /* access modifiers changed from: protected */
    public String getLogRecString(Message msg) {
        StringBuilder sb = new StringBuilder();
        if (this.mPoorNetworkDetectionEnabled) {
            sb.append("!");
        }
        sb.append(smToString(msg.what));
        switch (msg.what) {
            case REPORT_NETWORK_CONNECTIVITY /*{ENCODED_INT: 135204}*/:
                sb.append(" step :");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" trigger :");
                sb.append(Integer.toString(msg.arg2));
                break;
            case EVENT_INET_CONDITION_ACTION /*{ENCODED_INT: 135245}*/:
                sb.append(" networkType :");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" valid :");
                sb.append(Integer.toString(msg.arg2));
                break;
            case EVENT_USER_SELECTION /*{ENCODED_INT: 135264}*/:
                sb.append(" keepConnection:");
                sb.append(Integer.toString(msg.arg1));
                break;
            case CMD_IWC_CURRENT_QAI /*{ENCODED_INT: 135368}*/:
                int qai = msg.getData().getInt("qai");
                sb.append(" ");
                sb.append(Integer.toString(qai));
                break;
            case CMD_ROAM_START_COMPLETE /*{ENCODED_INT: 135477}*/:
                sb.append(" ");
                sb.append((String) msg.obj);
                break;
            default:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                break;
        }
        return sb.toString();
    }

    /* access modifiers changed from: package-private */
    public String smToString(int what) {
        String s;
        switch (what) {
            case EVENT_NETWORK_STATE_CHANGE /*{ENCODED_INT: 135170}*/:
                s = "EVENT_NETWORK_STATE_CHANGE";
                break;
            case EVENT_RSSI_CHANGE /*{ENCODED_INT: 135171}*/:
                s = "EVENT_RSSI_CHANGE";
                break;
            case EVENT_WIFI_RADIO_STATE_CHANGE /*{ENCODED_INT: 135173}*/:
                s = "EVENT_WIFI_RADIO_STATE_CHANGE";
                break;
            case EVENT_WATCHDOG_SETTINGS_CHANGE /*{ENCODED_INT: 135174}*/:
                s = "EVENT_WATCHDOG_SETTINGS_CHANGE";
                break;
            case EVENT_BSSID_CHANGE /*{ENCODED_INT: 135175}*/:
                s = "EVENT_BSSID_CHANGE";
                break;
            case EVENT_SCREEN_ON /*{ENCODED_INT: 135176}*/:
                s = "EVENT_SCREEN_ON";
                break;
            case EVENT_SCREEN_OFF /*{ENCODED_INT: 135177}*/:
                s = "EVENT_SCREEN_OFF";
                break;
            case CMD_RSSI_FETCH /*{ENCODED_INT: 135188}*/:
                s = "CMD_RSSI_FETCH";
                break;
            case CMD_TRAFFIC_POLL /*{ENCODED_INT: 135193}*/:
                s = "CMD_TRAFFIC_POLL";
                break;
            case REPORT_QC_RESULT /*{ENCODED_INT: 135203}*/:
                s = "REPORT_QC_RESULT";
                break;
            case REPORT_NETWORK_CONNECTIVITY /*{ENCODED_INT: 135204}*/:
                s = "REPORT_NETWORK_CONNECTIVITY";
                break;
            case CONNECTIVITY_VALIDATION_BLOCK /*{ENCODED_INT: 135205}*/:
                s = "CONNECTIVITY_VALIDATION_BLOCK";
                break;
            case CONNECTIVITY_VALIDATION_RESULT /*{ENCODED_INT: 135206}*/:
                s = "CONNECTIVITY_VALIDATION_RESULT";
                break;
            case VALIDATION_CHECK_FORCE /*{ENCODED_INT: 135207}*/:
                s = "VALIDATION_CHECK_FORCE";
                break;
            case EVENT_SCAN_STARTED /*{ENCODED_INT: 135229}*/:
                s = "EVENT_SCAN_STARTED";
                break;
            case EVENT_SCAN_COMPLETE /*{ENCODED_INT: 135230}*/:
                s = "EVENT_SCAN_COMPLETE";
                break;
            case EVENT_SCAN_TIMEOUT /*{ENCODED_INT: 135231}*/:
                s = "EVENT_SCAN_TIMEOUT";
                break;
            case EVENT_MOBILE_CONNECTED /*{ENCODED_INT: 135232}*/:
                s = "EVENT_MOBILE_CONNECTED";
                break;
            case EVENT_NETWORK_PROPERTIES_CHANGED /*{ENCODED_INT: 135235}*/:
                s = "EVENT_NETWORK_PROPERTIES_CHANGED";
                break;
            case EVENT_DHCP_SESSION_STARTED /*{ENCODED_INT: 135236}*/:
                s = "EVENT_DHCP_SESSION_STARTED";
                break;
            case EVENT_DHCP_SESSION_COMPLETE /*{ENCODED_INT: 135237}*/:
                s = "EVENT_DHCP_SESSION_COMPLETE";
                break;
            case EVENT_NETWORK_REMOVED /*{ENCODED_INT: 135244}*/:
                s = "EVENT_NETWORK_REMOVED";
                break;
            case EVENT_INET_CONDITION_ACTION /*{ENCODED_INT: 135245}*/:
                s = "EVENT_INET_CONDITION_ACTION";
                break;
            case EVENT_ROAM_TIMEOUT /*{ENCODED_INT: 135249}*/:
                s = "EVENT_ROAM_TIMEOUT";
                break;
            case EVENT_USER_SELECTION /*{ENCODED_INT: 135264}*/:
                s = "EVENT_USER_SELECTION";
                break;
            case EVENT_WIFI_ICON_VSB /*{ENCODED_INT: 135265}*/:
                s = "EVENT_WIFI_ICON_VSB";
                break;
            case CMD_ELE_DETECTED /*{ENCODED_INT: 135283}*/:
                s = "CMD_ELE_DETECTED";
                break;
            case CMD_ELE_BY_GEO_DETECTED /*{ENCODED_INT: 135284}*/:
                s = "CMD_ELE_BY_GEO_DETECTED";
                break;
            case CMD_TRANSIT_ON_VALID /*{ENCODED_INT: 135299}*/:
                s = "CMD_TRANSIT_ON_VALID";
                break;
            case CMD_TRANSIT_ON_SWITCHABLE /*{ENCODED_INT: 135300}*/:
                s = "CMD_TRANSIT_ON_SWITCHABLE";
                break;
            case CMD_IWC_CURRENT_QAI /*{ENCODED_INT: 135368}*/:
                s = "CMD_IWC_CURRENT_QAI";
                break;
            case CMD_QUALITY_CHECK_BY_SCORE /*{ENCODED_INT: 135388}*/:
                s = "CMD_QUALITY_CHECK_BY_SCORE";
                break;
            case CMD_RECOVERY_TO_HIGH_QUALITY_FROM_LOW_LINK /*{ENCODED_INT: 135389}*/:
                s = "CMD_RECOVERY_TO_HIGH_QUALITY_FROM_LOW_LINK";
                break;
            case CMD_RECOVERY_TO_HIGH_QUALITY_FROM_ELE /*{ENCODED_INT: 135390}*/:
                s = "CMD_RECOVERY_TO_HIGH_QUALITY_FROM_ELE";
                break;
            case CMD_LINK_GOOD_ENTERED /*{ENCODED_INT: 135398}*/:
                s = "CMD_LINK_GOOD_ENTERED";
                break;
            case CMD_LINK_POOR_ENTERED /*{ENCODED_INT: 135399}*/:
                s = "CMD_LINK_POOR_ENTERED";
                break;
            case CMD_REACHABILITY_LOST /*{ENCODED_INT: 135400}*/:
                s = "CMD_REACHABILITY_LOST";
                break;
            case CMD_PROVISIONING_FAIL /*{ENCODED_INT: 135401}*/:
                s = "CMD_PROVISIONING_FAIL";
                break;
            case STOP_BLINKING_WIFI_ICON /*{ENCODED_INT: 135468}*/:
                s = "STOP_BLINKING_WIFI_ICON";
                break;
            case HANDLE_ON_LOST /*{ENCODED_INT: 135469}*/:
                s = "HANDLE_ON_LOST";
                break;
            case HANDLE_ON_AVAILABLE /*{ENCODED_INT: 135470}*/:
                s = "HANDLE_ON_AVAILABLE";
                break;
            case CAPTIVE_PORTAL_DETECTED /*{ENCODED_INT: 135471}*/:
                s = "CAPTIVE_PORTAL_DETECTED";
                break;
            case VALIDATED_DETECTED /*{ENCODED_INT: 135472}*/:
                s = "VALIDATED_DETECTED";
                break;
            case INVALIDATED_DETECTED /*{ENCODED_INT: 135473}*/:
                s = "INVALIDATED_DETECTED";
                break;
            case VALIDATED_DETECTED_AGAIN /*{ENCODED_INT: 135474}*/:
                s = "VALIDATED_DETECTED_AGAIN";
                break;
            case INVALIDATED_DETECTED_AGAIN /*{ENCODED_INT: 135475}*/:
                s = "INVALIDATED_DETECTED_AGAIN";
                break;
            case CMD_ROAM_START_COMPLETE /*{ENCODED_INT: 135477}*/:
                s = "CMD_ROAM_START_COMPLETE";
                break;
            case CMD_NETWORK_PROPERTIES_UPDATED /*{ENCODED_INT: 135478}*/:
                s = "CMD_NETWORK_PROPERTIES_UPDATED";
                break;
            case CMD_CHECK_ALTERNATIVE_FOR_CAPTIVE_PORTAL /*{ENCODED_INT: 135481}*/:
                s = "CMD_CHECK_ALTERNATIVE_FOR_CAPTIVE_PORTAL";
                break;
            case CMD_UPDATE_CURRENT_BSSID_ON_THROUGHPUT_UPDATE /*{ENCODED_INT: 135488}*/:
                s = "CMD_UPDATE_CURRENT_BSSID_ON_THROUGHPUT_UPDATE";
                break;
            case CMD_UPDATE_CURRENT_BSSID_ON_DNS_RESULT /*{ENCODED_INT: 135489}*/:
                s = "CMD_UPDATE_CURRENT_BSSID_ON_DNS_RESULT";
                break;
            case CMD_UPDATE_CURRENT_BSSID_ON_DNS_RESULT_TYPE /*{ENCODED_INT: 135490}*/:
                s = "CMD_UPDATE_CURRENT_BSSID_ON_DNS_RESULT_TYPE";
                break;
            case CMD_UPDATE_CURRENT_BSSID_ON_QC_RESULT /*{ENCODED_INT: 135491}*/:
                s = "CMD_UPDATE_CURRENT_BSSID_ON_QC_RESULT";
                break;
            default:
                s = "what:" + Integer.toString(what);
                break;
        }
        return "(" + getKernelTime() + ") " + s;
    }

    private String getKernelTime() {
        return Double.toString(((double) (System.nanoTime() / 1000000)) / 1000.0d);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        int cCount;
        int cCount2;
        int dCount;
        int dCount2;
        WifiConnectivityMonitor.super.dump(fd, pw, args);
        pw.println("mWifiInfo: [" + this.mWifiInfo + "]");
        pw.println("mLinkProperties: [" + this.mLinkProperties + "]");
        pw.println("mCurrentSignalLevel: [" + this.mCurrentSignalLevel + "]");
        pw.println("mPoorNetworkDetectionEnabled: [" + this.mPoorNetworkDetectionEnabled + "(" + this.mPoorNetworkDetectionSummary + ")]");
        pw.println("mUIEnabled: [" + this.mUIEnabled + "(" + this.mPoorNetworkAvoidanceSummary + ")]");
        if (isAggressiveModeSupported()) {
            pw.println("mAggressiveModeEnabled: [" + this.mAggressiveModeEnabled + this.mParam.getAggressiveModeFeatureStatus() + "]");
        }
        pw.println("InvalidIconVisibility : [invisible]");
        pw.println("mLastVisibilityOfWifiIcon : " + this.mLastVisibilityOfWifiIcon);
        pw.println("mIwcCurrentQai: " + this.mIwcCurrentQai);
        pw.println("mMptcpEnabled: [" + this.mMptcpEnabled + "]");
        if (this.isQCExceptionSummary != null) {
            pw.println("isQCExceptionSummary: " + this.isQCExceptionSummary);
        }
        pw.println("mQcHistoryTotal: [" + this.mQcHistoryTotal + "], mQcDumpVer: [" + this.mQcDumpVer + "]");
        pw.println("info: l2");
        pw.println("info: fd");
        ArrayList<String> arrayList = this.mNetstatTestBuffer;
        if (arrayList != null && arrayList.size() > 0) {
            pw.println("========NetStat history=======");
            Iterator<String> iterNetstat = this.mNetstatTestBuffer.iterator();
            while (iterNetstat.hasNext()) {
                pw.println(iterNetstat.next());
            }
            pw.println(" ");
        }
        pw.println(" ");
        if (this.mQcHistoryTotal > 0) {
            synchronized (lock) {
                int i = this.mQcHistoryTotal < 30 ? 0 : this.mQcHistoryHead % 30;
                for (int count = 0; count < 30; count++) {
                    try {
                        if (this.mQcDumpHistory[i] != null) {
                            pw.println("[" + count + "]: " + this.mQcDumpHistory[i]);
                            i = (i + 1) % 30;
                        }
                    } catch (RuntimeException e) {
                        pw.println("[" + count + "]: ex");
                    }
                }
            }
            pw.println(" ");
        }
        pw.println("========TCP STAT========");
        pw.println("[1] " + this.mFirstTCPLoggedTime);
        pw.println(this.mFirstLogged);
        pw.println("[2] " + this.mLastTCPLoggedTime);
        pw.println(this.mLastLogged);
        if (this.mWifiSwitchForIndividualAppsService != null) {
            pw.println("========SWITCH FOR INDIVIDUAL APPS========");
            pw.println(this.mWifiSwitchForIndividualAppsService.dump());
        }
        pw.println(" ");
        pw.println("[CISO history]");
        pw.println("CISO from Scan: " + this.mCountryCodeFromScanResult);
        int i2 = this.incrScanResult;
        if (i2 < 10) {
            cCount = this.incrScanResult;
            cCount2 = 0;
        } else {
            int cStart = i2 % 10;
            cCount = 10;
            cCount2 = cStart;
        }
        for (int i3 = cCount2; i3 < cCount2 + cCount; i3++) {
            pw.println(this.summaryCountryCodeFromScanResults[i3 % 10]);
        }
        pw.println(" ");
        pw.println("[summary D History]");
        int i4 = this.incrDnsResults;
        if (i4 < 50) {
            dCount = this.incrDnsResults;
            dCount2 = 0;
        } else {
            int dStart = i4 % 50;
            dCount = 50;
            dCount2 = dStart;
        }
        for (int i5 = dCount2; i5 < dCount2 + dCount; i5++) {
            pw.println(this.summaryDnsResults[i5 % 50]);
        }
        pw.println(" ");
        pw.println("========HIDE ICON========");
        int hide_index = this.mHideIconHistoryTotal < 100 ? 0 : this.mHideIconHistoryHead % 100;
        for (int count2 = 0; count2 < 100; count2++) {
            try {
                if (this.mHideIconHistory[hide_index] != null) {
                    pw.println("[" + hide_index + "]: " + this.mHideIconHistory[hide_index]);
                    hide_index = (hide_index + 1) % 100;
                }
            } catch (RuntimeException e2) {
                pw.println("[" + hide_index + "]: pre");
            }
        }
        if (this.mConnectivityPacketLogForWlan0 != null) {
            pw.println(" ");
            pw.println("[connectivity frame log]");
            pw.println("WifiConnectivityMonitor Name of interface : wlan0");
            pw.println();
            this.mConnectivityPacketLogForWlan0.readOnlyLocalLog().dump(fd, pw, args);
            pw.println();
        }
        pw.println("[BSSID QoS Map] - Ver: 1.72");
        pw.println(dumpBssidQosMap());
        if (getOpenNetworkQosScores() != null) {
            pw.println("getOpenNetworkQosNoInternetStatus: " + getOpenNetworkQosNoInternetStatus());
            String s = "";
            for (int i6 : getOpenNetworkQosScores()) {
                s = s + i6 + " ";
            }
            pw.println("getOpenNetworkQosScores: " + s);
            pw.println(" ");
        }
        pw.println("========PKTCNT_POLL========");
        int j = this.mRssiPatchHistoryTotal < RSSI_PATCH_HISTORY_COUNT_MAX ? 0 : this.mRssiPatchHistoryHead % RSSI_PATCH_HISTORY_COUNT_MAX;
        for (int count3 = 0; count3 < RSSI_PATCH_HISTORY_COUNT_MAX; count3++) {
            try {
                if (this.mRssiPatchHistory[j] != null) {
                    pw.println("[" + count3 + "]: " + this.mRssiPatchHistory[j]);
                    j = (j + 1) % RSSI_PATCH_HISTORY_COUNT_MAX;
                }
            } catch (RuntimeException e3) {
                pw.println("[" + count3 + "]: ex");
            }
        }
        pw.println(" ");
        pw.println("========TRAFFIC_POLL========");
        int poll_index = this.mTrafficPollHistoryTotal < 3000 ? 0 : this.mTrafficPollHistoryHead % 3000;
        for (int count4 = 0; count4 < 3000; count4++) {
            try {
                if (this.mTrafficPollHistory[poll_index] != null) {
                    pw.println("[" + count4 + "]: " + this.mTrafficPollHistory[poll_index]);
                    poll_index = (poll_index + 1) % 3000;
                }
            } catch (RuntimeException e4) {
                pw.println("[" + count4 + "]: pre");
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setTrafficPollHistory(long txBytesLogging, long rxBytesLogging, long fgrndTxBytes, long fgrndRxBytes) {
        StringBuilder builder = new StringBuilder();
        int i = this.mTrafficPollHistoryHead;
        if (i == -1) {
            this.mTrafficPollHistoryHead = i + 1;
        } else {
            this.mTrafficPollHistoryHead = i % 3000;
        }
        String currentTime = "" + (System.currentTimeMillis() / 1000);
        try {
            currentTime = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        } catch (RuntimeException e) {
        }
        try {
            builder.append(currentTime);
            builder.append(": " + this.mUsageStatsUid);
            builder.append(", " + txBytesLogging);
            builder.append(", " + rxBytesLogging);
            builder.append(", " + fgrndTxBytes);
            builder.append(", " + fgrndRxBytes);
            if (this.mUsagePackageChanged) {
                this.mTrafficPollPackageName = this.mUsageStatsPackageName;
                builder.append(", " + this.mTrafficPollPackageName);
                this.mUsagePackageChanged = false;
            }
        } catch (RuntimeException e2) {
            builder.append(currentTime + ", ex");
        }
        this.mTrafficPollHistory[this.mTrafficPollHistoryHead] = builder.toString();
        this.mTrafficPollHistoryHead++;
        this.mTrafficPollHistoryTotal++;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setLoggingForTCPStat(int mCheckId) {
        if (!this.isNetstatLoggingHangs) {
            if (mCheckId == TCP_STAT_LOGGING_FIRST) {
                this.mNetstatTestBuffer.add("#" + this.mNetstatBufferCount + ") Netstat TCP_STAT_LOGGING_FIRST with thread " + this.mCurrentNetstatThread);
            } else if (mCheckId == TCP_STAT_LOGGING_SECOND) {
                this.mNetstatTestBuffer.add("#" + this.mNetstatBufferCount + ") Netstat TCP_STAT_LOGGING_SECOND with thread " + this.mCurrentNetstatThread);
                this.mNetstatBufferCount = (this.mNetstatBufferCount + 1) % 50;
            }
            ExecutorService executorService = this.mNetstatThreadPool;
            if (executorService != null) {
                int numOfThreadsInUse = 0;
                for (boolean z : this.mNetstatThreadInUse) {
                    numOfThreadsInUse += z ? 1 : 0;
                }
                if (numOfThreadsInUse >= 10) {
                    this.isNetstatLoggingHangs = true;
                    this.mNetstatTestBuffer.add("#" + this.mNetstatBufferCount + ") Netstat thread hangs detected on " + this.mCurrentNetstatThread);
                }
            } else if (executorService == null) {
                this.mNetstatThreadPool = Executors.newFixedThreadPool(10);
                this.mNetstatThreadInUse = new boolean[10];
                this.mCurrentNetstatThread = 0;
            }
            try {
                this.mNetstatThreadPool.submit(runNetstat(mCheckId));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (DBG) {
            Log.i(TAG, "Netstat is not available because of exec command hang");
        }
    }

    private Runnable runNetstat(int mCheckId) {
        return new Runnable(mCheckId) {
            /* class com.android.server.wifi.$$Lambda$WifiConnectivityMonitor$RRHhg8OwlwIjlmFmASZCK9RPI */
            private final /* synthetic */ int f$1;

            {
                this.f$1 = r2;
            }

            public final void run() {
                WifiConnectivityMonitor.this.lambda$runNetstat$0$WifiConnectivityMonitor(this.f$1);
            }
        };
    }

    /* JADX WARNING: Code restructure failed: missing block: B:41:0x00ee, code lost:
        r0 = move-exception;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:42:0x00ef, code lost:
        r11.isNetstatLoggingHangs = false;
        r11.mNetstatThreadInUse[r1] = false;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:43:0x00f5, code lost:
        throw r0;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:45:0x00f7, code lost:
        r11.isNetstatLoggingHangs = false;
        r11.mNetstatThreadInUse[r1] = false;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:47:?, code lost:
        return;
     */
    /* JADX WARNING: Failed to process nested try/catch */
    /* JADX WARNING: Removed duplicated region for block: B:41:0x00ee A[ExcHandler: all (r0v3 'th' java.lang.Throwable A[CUSTOM_DECLARE]), Splitter:B:10:0x004a] */
    public /* synthetic */ void lambda$runNetstat$0$WifiConnectivityMonitor(int mCheckId) {
        int currentThread = this.mCurrentNetstatThread;
        this.mCurrentNetstatThread = (this.mCurrentNetstatThread + 1) % 10;
        this.mNetstatThreadInUse[currentThread] = true;
        String currentTime = "" + (System.currentTimeMillis() / 1000);
        try {
            currentTime = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        } catch (RuntimeException e) {
        }
        if (mCheckId == TCP_STAT_LOGGING_RESET) {
            this.mTempLogged = "";
            this.mTempTCPLoggedTime = "";
            return;
        }
        String netResult = "";
        try {
            BufferedReader in = null;
            try {
                BufferedReader in2 = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("netstat -tlpanW").getInputStream()));
                while (true) {
                    String inputLine = in2.readLine();
                    if (inputLine == null) {
                        break;
                    }
                    netResult = netResult + inputLine + "\n";
                }
                if (mCheckId == TCP_STAT_LOGGING_FIRST) {
                    this.mTempTCPLoggedTime = currentTime;
                    this.mTempLogged = netResult;
                } else if (mCheckId == TCP_STAT_LOGGING_SECOND) {
                    this.mFirstTCPLoggedTime = this.mTempTCPLoggedTime;
                    this.mLastTCPLoggedTime = currentTime;
                    this.mFirstLogged = this.mTempLogged;
                    this.mLastLogged = netResult;
                    if (DBG) {
                        Log.d(TAG, "1.TCP stat\n" + this.mFirstLogged);
                        Log.d(TAG, "2.TCP stat\n" + this.mLastLogged);
                    }
                }
                in2.close();
            } catch (Exception e2) {
                if (0 != 0) {
                    in.close();
                }
            } catch (Throwable th) {
                if (0 != 0) {
                    in.close();
                }
                throw th;
            }
        } catch (Exception e3) {
        } catch (Throwable th2) {
        }
        this.isNetstatLoggingHangs = false;
        this.mNetstatThreadInUse[currentThread] = false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void checkTransitionToLevel1StateState() {
        WifiEleStateTracker wifiEleStateTracker = this.mWifiEleStateTracker;
        if (wifiEleStateTracker == null || !wifiEleStateTracker.checkEleValidBlockState()) {
            int i = this.mTrafficPollToken + 1;
            this.mTrafficPollToken = i;
            sendMessage(obtainMessage(CMD_TRAFFIC_POLL, i, 0));
            if (this.mCurrentMode == 2) {
                this.mSnsBigDataSCNT.mPqGqNormal++;
            } else {
                this.mSnsBigDataSCNT.mPqGqAgg++;
            }
            setBigDataQCandNS(true);
            transitionTo(this.mLevel1State);
            return;
        }
        Log.i(TAG, "Tansition to Level1State blocked by EleBlock");
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void checkTransitionToLevel2State() {
        if (this.mIwcCurrentQai != 3) {
            if (this.mCurrentMode == 2) {
                this.mSnsBigDataSCNT.mGqPqNormal++;
            } else {
                this.mSnsBigDataSCNT.mGqPqAgg++;
            }
            setBigDataQCandNS(false);
            transitionTo(this.mLevel2State);
            return;
        }
        Log.i(TAG, "Tansition to Level2State blocked by QAI 3");
        setLinkDetectMode(0);
    }

    private void checkTransitionToValidState() {
        transitionTo(this.mValidState);
    }

    public boolean isRoamingNetwork() {
        int configuredSecurity;
        if (!isConnectedState()) {
            return false;
        }
        WifiConfiguration currConfig = null;
        int candidateCount = 0;
        WifiInfo wifiInfo = this.mWifiInfo;
        if (wifiInfo != null) {
            currConfig = getSpecificNetwork(wifiInfo.getNetworkId());
        }
        if (currConfig == null) {
            return false;
        }
        String configSsid = currConfig.SSID;
        if (currConfig.allowedKeyManagement.get(1) || currConfig.allowedKeyManagement.get(4) || currConfig.allowedKeyManagement.get(22)) {
            configuredSecurity = 2;
        } else if (currConfig.allowedKeyManagement.get(8)) {
            configuredSecurity = 4;
        } else if (currConfig.allowedKeyManagement.get(2) || currConfig.allowedKeyManagement.get(3) || currConfig.allowedKeyManagement.get(24)) {
            configuredSecurity = 3;
        } else {
            configuredSecurity = currConfig.wepKeys[0] != null ? 1 : 0;
        }
        synchronized (this.mScanResultsLock) {
            for (ScanResult scanResult : this.mScanResults) {
                int scanedSecurity = 0;
                if (scanResult != null) {
                    if (scanResult.capabilities.contains("WEP")) {
                        scanedSecurity = 1;
                    } else if (SUPPORT_WPA3_SAE && scanResult.capabilities.contains("SAE")) {
                        scanedSecurity = 4;
                    } else if (scanResult.capabilities.contains("PSK")) {
                        scanedSecurity = 2;
                    } else if (scanResult.capabilities.contains("EAP")) {
                        scanedSecurity = 3;
                    }
                    if (scanResult.SSID != null && configSsid != null && configSsid.length() > 2 && scanResult.SSID.equals(configSsid.substring(1, configSsid.length() - 1)) && configuredSecurity == scanedSecurity && !currConfig.isCaptivePortal && scanResult.BSSID != null && !scanResult.BSSID.equals(this.mWifiInfo.getBSSID())) {
                        candidateCount++;
                    }
                }
            }
        }
        if (candidateCount <= 0) {
            return false;
        }
        Log.d(TAG, "isRoamingNetwork: " + candidateCount + " additional BSSID(s) found for network " + this.mWifiInfo.getSSID());
        return true;
    }

    public WifiConfiguration getSpecificNetwork(int netID) {
        if (netID == -1) {
            return null;
        }
        return this.mWifiConfigManager.getConfiguredNetwork(netID);
    }

    private void resetBigDataFeatureForSCNT() {
        this.mSnsBigDataSCNT.initialize();
        this.mSnsBigDataManager.clearFeature("SSMA");
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendBigDataFeatureForSCNT() {
        Log.d(TAG, "Sns Big Data SCNT logging");
        if (this.mSnsBigDataManager.addOrUpdateFeatureAllValue("SSMA")) {
            this.mSnsBigDataManager.insertLog("SSMA", -1);
        } else {
            Log.e(TAG, "error on Loging Big Data for SCNT");
        }
        resetBigDataFeatureForSCNT();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setBigDataQualityCheck(boolean pass) {
        setBigDataQualityCheck(pass, false);
    }

    private void setBigDataQualityCheck(boolean pass, boolean doNotReset) {
        long currentTime = System.currentTimeMillis();
        long bootingElapsedTime = SystemClock.elapsedRealtime();
        if (this.mBigDataQualityCheckStartOfCycle == -1) {
            this.mBigDataQualityCheckStartOfCycle = currentTime - ((long) new Random(currentTime).nextInt((int) mBigDataQualityCheckCycle));
        }
        long j = mBigDataQualityCheckCycle;
        if (currentTime - this.mBigDataQualityCheckStartOfCycle > j) {
            this.mBigDataQualityCheckStartOfCycle = (currentTime / j) * j;
            this.mBigDataQualityCheckLoggingCount = 0;
        }
        int trigger = this.mInvalidationFailHistory.qcTrigger;
        String toString = (((((((((((("setBigDataQualityCheck - " + getCurrentState().getName()) + ", COUNT: " + this.mBigDataQualityCheckLoggingCount) + ", pass: " + pass) + ", time: " + bootingElapsedTime) + ", [type]" + this.mInvalidationFailHistory.qcType) + ", [s]" + this.mInvalidationFailHistory.qcStep) + ", [t]" + this.mInvalidationFailHistory.qcTrigger) + ", [e]" + this.mInvalidationFailHistory.error) + ", RSSI: " + this.mCurrentRssi) + ", SPEED: " + this.mCurrentLinkSpeed) + ", " + this.mPoorNetworkDetectionEnabled) + "/" + this.mUIEnabled) + "/" + this.mAggressiveModeEnabled;
        if (this.mBigDataQualityCheckLoggingCount < mMaxBigDataQualityCheckLogging) {
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_RESULT, pass ? 1 : 0);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_TYPE, this.mInvalidationFailHistory.qcType);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_STEP, this.mInvalidationFailHistory.qcStep);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_TRIGGER, trigger);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_FAIL_REASON, this.mInvalidationFailHistory.error);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_BOOTING_ELAPSED_TIME, bootingElapsedTime);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_TOP_PACKAGE_DURATION, convertMiliSecondToSecond(System.currentTimeMillis() - this.mFrontAppAppearedTime, true));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_SECOND_PACKAGE, this.mPreviousPackageName);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_SECOND_PACKAGE_DURATION, convertMiliSecondToSecond(this.mFrontAppAppearedTime - this.mPrevAppAppearedTime, true));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_SECOND_PACKAGE_MOBILEDATA_USAGE, requestDataUsage(0, this.mPreviousUid));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_SECOND_PACKAGE_WIFI_USAGE, requestDataUsage(1, this.mPreviousUid));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_STATE, getCurrentState().getName());
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_RSSI, this.mCurrentRssi);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_LINK_SPEED, this.mCurrentLinkSpeed);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_POOR_DETECTION_ENABLED, this.mPoorNetworkDetectionEnabled ? 1 : 0);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_QC_UI_ENABLED, this.mUIEnabled ? 1 : 0);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_WFQC, SnsBigDataWFQC.KEY_QC_AGGRESSIVE_MODE_ENABLED, this.mAggressiveModeEnabled ? 1 : 0);
            this.mSnsBigDataManager.insertLog(SnsBigDataManager.FEATURE_WFQC);
            this.mSnsBigDataManager.clearFeature(SnsBigDataManager.FEATURE_WFQC);
        } else {
            toString = "**" + toString;
        }
        this.mBigDataQualityCheckLoggingCount++;
        if (DBG) {
            Log.i(TAG, toString);
        }
        if (!doNotReset) {
            initCurrentQcFailRecord();
        }
    }

    private void initCurrentQcFailRecord() {
        if (DBG) {
            Log.d(TAG, "initCurrentQcFailRecord");
        }
        QcFailHistory qcFailHistory = this.mInvalidationFailHistory;
        qcFailHistory.qcType = -1;
        qcFailHistory.qcStep = -1;
        qcFailHistory.qcTrigger = -1;
        qcFailHistory.error = -1;
        qcFailHistory.line = -1;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setBigDataValidationChanged() {
        long bootingElapsedTime = SystemClock.elapsedRealtime();
        if (isValidState()) {
            if (SMARTCM_DBG) {
                Log.d(TAG, "BigData Validation changed, Valid > Invalid");
            }
            this.mValidatedTime = (int) ((SystemClock.elapsedRealtime() - this.mValidStartTime) / 1000);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_BOOTING_ELAPSED_TIME, bootingElapsedTime);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_TOP_PACKAGE, this.mUsageStatsPackageName);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_TOP_PACKAGE_DURATION, convertMiliSecondToSecond(System.currentTimeMillis() - this.mFrontAppAppearedTime, true));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_TOP_PACKAGE_MOBILEDATA_USAGE, requestDataUsage(0, this.mUsageStatsUid));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_TOP_PACKAGE_WIFI_USAGE, requestDataUsage(1, this.mUsageStatsUid));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_SECOND_PACKAGE, this.mPreviousPackageName);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_SECOND_PACKAGE_DURATION, convertMiliSecondToSecond(this.mFrontAppAppearedTime - this.mPrevAppAppearedTime, true));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_SECOND_PACKAGE_MOBILEDATA_USAGE, requestDataUsage(0, this.mPreviousUid));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_SECOND_PACKAGE_WIFI_USAGE, requestDataUsage(1, this.mPreviousUid));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_STATE, getCurrentState().getName());
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_RSSI, this.mCurrentRssi);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_LINK_SPEED, this.mCurrentLinkSpeed);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_POOR_DETECTION_ENABLED, this.mPoorNetworkDetectionEnabled ? 1 : 0);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_QC_UI_ENABLED, this.mUIEnabled ? 1 : 0);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_QC_AGGRESSIVE_MODE_ENABLED, this.mAggressiveModeEnabled ? 1 : 0);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_SNS_GOOD_LINK_TARGET_RSSI, this.mCurrentBssid.mGoodLinkTargetRssi);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_SNS_CONNECTED_STAY_TIME, this.mValidatedTime);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSVI, SnsBigDataSSVI.KEY_AP_OUI, this.mApOui);
            this.mSnsBigDataManager.insertLog(SnsBigDataManager.FEATURE_SSVI);
            this.mSnsBigDataManager.clearFeature(SnsBigDataManager.FEATURE_SSVI);
            this.mInvalidStartTime = SystemClock.elapsedRealtime();
        } else {
            if (SMARTCM_DBG) {
                Log.d(TAG, "BigData Validation Switch, Invalid > Valid");
            }
            this.mInvalidatedTime = (int) ((SystemClock.elapsedRealtime() - this.mInvalidStartTime) / 1000);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_BOOTING_ELAPSED_TIME, bootingElapsedTime);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_TOP_PACKAGE, this.mUsageStatsPackageName);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_TOP_PACKAGE_DURATION, convertMiliSecondToSecond(System.currentTimeMillis() - this.mFrontAppAppearedTime, true));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_TOP_PACKAGE_MOBILEDATA_USAGE, requestDataUsage(0, this.mUsageStatsUid));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_TOP_PACKAGE_WIFI_USAGE, requestDataUsage(1, this.mUsageStatsUid));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_SECOND_PACKAGE, this.mPreviousPackageName);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_SECOND_PACKAGE_DURATION, convertMiliSecondToSecond(this.mFrontAppAppearedTime - this.mPrevAppAppearedTime, true));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_SECOND_PACKAGE_MOBILEDATA_USAGE, requestDataUsage(0, this.mPreviousUid));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_SECOND_PACKAGE_WIFI_USAGE, requestDataUsage(1, this.mPreviousUid));
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_STATE, getCurrentState().getName());
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_RSSI, this.mCurrentRssi);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_LINK_SPEED, this.mCurrentLinkSpeed);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_POOR_DETECTION_ENABLED, this.mPoorNetworkDetectionEnabled ? 1 : 0);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_QC_UI_ENABLED, this.mUIEnabled ? 1 : 0);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_QC_AGGRESSIVE_MODE_ENABLED, this.mAggressiveModeEnabled ? 1 : 0);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_SNS_GOOD_LINK_TARGET_RSSI, this.mCurrentBssid.mGoodLinkTargetRssi);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_SNS_L2_CONNECTED_STAY_TIME, this.mInvalidatedTime);
            this.mSnsBigDataManager.addOrUpdateFeatureValue(SnsBigDataManager.FEATURE_SSIV, SnsBigDataSSIV.KEY_AP_OUI, this.mApOui);
            this.mSnsBigDataManager.insertLog(SnsBigDataManager.FEATURE_SSIV);
            this.mSnsBigDataManager.clearFeature(SnsBigDataManager.FEATURE_SSIV);
            this.mValidStartTime = SystemClock.elapsedRealtime();
        }
        this.mValidatedTime = 0;
        this.mInvalidatedTime = 0;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setBigDataQCandNS(boolean pass) {
        setBigDataQualityCheck(pass, true);
        setBigDataValidationChanged();
        initCurrentQcFailRecord();
    }

    private int convertMiliSecondToSecond(long miliSecond, boolean aDayLimit) {
        int resultValue = Long.valueOf(miliSecond / 1000).intValue();
        if (!aDayLimit || resultValue <= 86400) {
            return resultValue;
        }
        return 86400;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isIwcModeEnabled() {
        int i = this.mIwcCurrentQai;
        if (i == 1 || i == 2 || !this.mPoorNetworkDetectionEnabled) {
            return false;
        }
        return i != -1;
    }

    public void setIWCMonitorAsyncChannel(Handler dst) {
        if (this.mIWCChannel == null) {
            if (DBG) {
                Log.i(TAG, "New mIWCChannel created");
            }
            this.mIWCChannel = new AsyncChannel();
        }
        this.mIWCChannel.connectSync(this.mContext, getHandler(), dst);
        if (DBG) {
            Log.i(TAG, "mIWCChannel connected");
        }
    }

    public void sendQCResultToWCM(Message msg) {
        boolean isNotRequestedFromWCM = false;
        isNotRequestedFromWCM = false;
        if (!(msg == null || msg.getData() == null)) {
            isNotRequestedFromWCM = msg.what == MESSAGE_NOT_TRIGGERED_FROM_WCM;
            boolean isQCResultValid = msg.getData().getBoolean("valid");
            boolean captivePortalDetected = msg.getData().getBoolean("captivePortalDetected");
            StringBuilder sb = new StringBuilder();
            sb.append(isNotRequestedFromWCM ? " [isNotRequestedFromWCM] " : "");
            sb.append("QC Result = ");
            sb.append(isQCResultValid);
            sb.append(", captivePortalDetected: ");
            sb.append(captivePortalDetected);
            Log.i(TAG, sb.toString());
            if (!this.mClientModeImpl.isConnected()) {
                Log.d(TAG, "Disconnected. Do not update BssidQosMap.");
            } else if (!captivePortalDetected) {
                sendMessage(CMD_UPDATE_CURRENT_BSSID_ON_QC_RESULT, isQCResultValid ? 1 : 0);
            }
        }
        if (!isNotRequestedFromWCM) {
            if (!(msg == null || msg.getData() == null || this.mWCMQCResult == null)) {
                boolean isQCResultValid2 = msg.getData().getBoolean("valid");
                if (this.mCheckRoamedNetwork) {
                    if (isQCResultValid2) {
                        sendMessage(VALIDATED_DETECTED_AGAIN);
                    } else {
                        sendMessage(INVALIDATED_DETECTED_AGAIN);
                    }
                }
            }
            Message message = this.mWCMQCResult;
            if (message != null) {
                message.recycle();
                this.mWCMQCResult = null;
            }
            removeMessages(QC_RESULT_NOT_RECEIVED);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setValidationBlock(boolean block) {
        if (!this.mConnectivityManager.getMultiNetwork()) {
            if (DBG) {
                Log.d(TAG, "validationBlock : " + block);
            }
            this.mValidationBlock = block;
            getCm().setWifiValidationBlock(block);
            sendMessage(CONNECTIVITY_VALIDATION_BLOCK, block ? 1 : 0);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void enableValidationCheck() {
        if (DBG) {
            Log.d(TAG, "ValidationCheckMode : " + this.mValidationCheckMode + ", ValidationCheckCount : " + this.mValidationCheckCount + ", ValidationBlock : " + this.mValidationBlock);
        }
        if (!this.mValidationCheckMode || this.mValidationCheckCount <= 0) {
            if (DBG) {
                Log.d(TAG, "Validation Check enabled.");
            }
            this.mValidationResultCount = 0;
            this.mValidationCheckCount = 0;
            this.mValidationCheckMode = true;
            this.mValidationCheckTime = VALIDATION_CHECK_COUNT;
            Message message = this.mWCMQCResult;
            if (message != null) {
                message.recycle();
                this.mWCMQCResult = null;
            }
            removeMessages(QC_RESULT_NOT_RECEIVED);
            boolean queried = reportNetworkConnectivityToNM(true, 5, 20);
            if (!this.mValidationBlock) {
                return;
            }
            if (queried) {
                this.mValidationCheckCount = 1;
                if (DBG) {
                    Log.d(TAG, "mValidationCheckCount : " + this.mValidationCheckCount);
                }
                sendMessageDelayed(VALIDATION_CHECK_FORCE, (long) (this.mValidationCheckTime * 1000));
                return;
            }
            if (DBG) {
                Log.d(TAG, "Starting to check VALIDATION_CHECK_FORCE is delayed.");
            }
            this.mValidationCheckTime = VALIDATION_CHECK_COUNT * 2;
            sendMessageDelayed(VALIDATION_CHECK_FORCE, 10000);
        } else if (DBG) {
            Log.d(TAG, "Validation Check was already enabled.");
        }
    }

    public void setValidationCheckStart() {
        if (!this.mConnectivityManager.getMultiNetwork()) {
            if (DBG) {
                Log.d(TAG, "request to check validation from CS");
            }
            enableValidationCheck();
            this.mValidationCheckEnabledTime = SystemClock.elapsedRealtime();
        }
    }

    public void sendValidationCheckModeResult(boolean valid) {
        if (DBG) {
            Log.d(TAG, "sendValidationCheckModeResult : " + valid);
        }
        sendMessage(CONNECTIVITY_VALIDATION_RESULT, valid ? 1 : 0);
    }

    private WcmConnectivityPacketTracker createPacketTracker(InterfaceParams mInterfaceParams, LocalLog mLog) {
        try {
            return new WcmConnectivityPacketTracker(getHandler(), mInterfaceParams, mLog);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to get ConnectivityPacketTracker object: " + e);
            return null;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void startPacketTracker() {
        if (this.mPacketTrackerForWlan0 == null) {
            sPktLogsWlan.putIfAbsent(INTERFACENAMEOFWLAN, new LocalLog(500));
            this.mConnectivityPacketLogForWlan0 = sPktLogsWlan.get(INTERFACENAMEOFWLAN);
            this.mPacketTrackerForWlan0 = createPacketTracker(InterfaceParams.getByName(INTERFACENAMEOFWLAN), this.mConnectivityPacketLogForWlan0);
            if (this.mPacketTrackerForWlan0 != null) {
                Log.d(TAG, "mPacketTrackerForwlan0 start");
                try {
                    this.mPacketTrackerForWlan0.start(INTERFACENAMEOFWLAN);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Failed to start tracking interface : " + e);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void stopPacketTracker() {
        if (this.mPacketTrackerForWlan0 != null) {
            Log.d(TAG, "mPacketTrackerForwlan0 stop");
            this.mPacketTrackerForWlan0.stop();
            this.mPacketTrackerForWlan0 = null;
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setRoamEventToNM(int enable) {
        Settings.Global.putInt(this.mContentResolver, "wifi_wcm_event_roam_complete", enable);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setDnsResultHistory(String s) {
        String currentTime = "" + (System.currentTimeMillis() / 1000);
        try {
            currentTime = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        } catch (RuntimeException e) {
        }
        Network network = this.mNetwork;
        int netId = network != null ? network.netId : -1;
        synchronized (lock) {
            String[] strArr = this.summaryDnsResults;
            int i = this.incrDnsResults;
            this.incrDnsResults = i + 1;
            strArr[i % 50] = currentTime + " - " + netId + " - " + s;
        }
    }
}
