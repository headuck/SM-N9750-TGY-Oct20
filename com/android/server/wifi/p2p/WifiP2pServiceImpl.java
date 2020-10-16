package com.android.server.wifi.p2p;

import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.hardware.display.DisplayManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.DhcpResults;
import android.net.InterfaceConfiguration;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.ip.IIpClient;
import android.net.ip.IpClientCallbacks;
import android.net.ip.IpClientUtil;
import android.net.shared.ProvisioningConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.p2p.IWifiP2pManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pConfigList;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pProvDiscEvent;
import android.net.wifi.p2p.WifiP2pStaticIpConfig;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceResponse;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.SemHqmManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.wifi.FrameworkFacade;
import com.android.server.wifi.HalDeviceManager;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiLog;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.iwc.IWCEventManager;
import com.android.server.wifi.p2p.WifiP2pServiceImpl;
import com.android.server.wifi.p2p.common.GUIUtil;
import com.android.server.wifi.p2p.common.Hash;
import com.android.server.wifi.p2p.common.Util;
import com.android.server.wifi.rtt.RttServiceImpl;
import com.android.server.wifi.util.WifiAsyncChannel;
import com.android.server.wifi.util.WifiHandler;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.samsung.android.desktopmode.SemDesktopModeManager;
import com.samsung.android.desktopmode.SemDesktopModeState;
import com.samsung.android.feature.SemFloatingFeature;
import com.samsung.android.server.wifi.bigdata.BaseBigDataItem;
import com.samsung.android.server.wifi.dqa.ReportIdKey;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import libcore.util.HexEncoding;

public class WifiP2pServiceImpl extends IWifiP2pManager.Stub {
    private static final String ACTION_CHECK_SIOP_LEVEL = "com.samsung.intent.action.CHECK_SIOP_LEVEL";
    private static final String ACTION_P2P_LO_TIMER_EXPIRED = "com.samsung.android.net.wifi.p2p.LO_TIMER_EXPIRED";
    private static final String ACTION_P2P_STOPFIND_TIMER_EXPIRED = "android.net.wifi.p2p.STOPFIND_TIMER_EXPIRED";
    private static final String ACTION_SMARTSWITCH_TRANSFER = "com.samsung.android.intent.SMARTSWITCH_TRANSFER";
    private static final int ADVANCED_OPP_MAX_SCAN_RETRY = 8;
    private static final String ANONYMIZED_DEVICE_ADDRESS = "02:00:00:00:00:00";
    private static final int BASE = 143360;
    public static final int BLOCK_DISCOVERY = 143375;
    private static final int CMD_BOOT_COMPLETED = 143415;
    public static final int CMD_SEC_LOGGING = 143420;
    private static final int CONNECTION_TIMED_OUT = 30;
    private static final int CONTACT_CRC_LENGTH = 4;
    private static final int CONTACT_HASH_LENGTH = 6;
    private static final int DEFAULT_GROUP_OWNER_INTENT = 6;
    private static final int DEFAULT_POLL_TRAFFIC_INTERVAL_MSECS = 3000;
    private static final String DEFAULT_STATIC_IP = "192.168.49.10";
    private static final String[] DHCP_RANGE = {"192.168.49.100", "192.168.49.199"};
    public static final int DISABLED = 0;
    public static final int DISABLE_P2P = 143377;
    public static final int DISABLE_P2P_RSP = 143395;
    public static final int DISABLE_P2P_TIMED_OUT = 143366;
    private static final int DISABLE_P2P_WAIT_TIME_MS = 5000;
    public static final int DISCONNECT_WIFI_REQUEST = 143372;
    public static final int DISCONNECT_WIFI_RESPONSE = 143373;
    private static final int DISCOVER_TIMEOUT_S = 120;
    private static final int DROP_WIFI_USER_ACCEPT = 143364;
    private static final int DROP_WIFI_USER_REJECT = 143365;
    private static final String EMPTY_DEVICE_ADDRESS = "00:00:00:00:00:00";
    public static final int ENABLED = 1;
    public static final int ENABLE_P2P = 143376;
    public static final String ENABLE_SURVEY_MODE = SemFloatingFeature.getInstance().getString("SEC_FLOATING_FEATURE_CONTEXTSERVICE_ENABLE_SURVEY_MODE");
    public static final boolean ENABLE_UNIFIED_HQM_SERVER = true;
    private static final Boolean FORM_GROUP = false;
    public static final int GROUP_CREATING_TIMED_OUT = 143361;
    private static final int GROUP_CREATING_WAIT_TIME_MS = 120000;
    private static final int GROUP_IDLE_TIME_S = 10;
    private static final int IDX_PHONE = 256;
    private static final int IDX_TABLET = 512;
    private static final int INVITATION_PROCEDURE_TIMED_OUT = 143411;
    private static final int INVITATION_WAIT_TIME_MS = 30000;
    private static final int IPC_DHCP_RESULTS = 143392;
    private static final int IPC_POST_DHCP_ACTION = 143391;
    private static final int IPC_PRE_DHCP_ACTION = 143390;
    private static final int IPC_PROVISIONING_FAILURE = 143394;
    private static final int IPC_PROVISIONING_SUCCESS = 143393;
    private static final Boolean JOIN_GROUP = true;
    private static final int MAX_DEVICE_NAME_LENGTH = 32;
    private static final int MODE_FORCE_GC = 4;
    private static final int MODE_PERSISTENT = 8;
    private static final int MODE_RETRY_COUNT = 3;
    private static final String NETWORKTYPE = "WIFI_P2P";
    private static final int NFC_REQUEST_TIMED_OUT = 143410;
    private static final Boolean NO_RELOAD = false;
    private static final int P2P_ADVOPP_DELAYED_DISCOVER_PEER = 143461;
    private static final int P2P_ADVOPP_DISCOVER_PEER = 143460;
    private static final int P2P_ADVOPP_LISTEN_TIMEOUT = 143462;
    public static final int P2P_CONNECTION_CHANGED = 143371;
    public static final int P2P_ENABLE_PENDING = 143370;
    private static final int P2P_EXPIRATION_TIME = 5;
    private static final int P2P_GROUP_STARTED_TIMED_OUT = 143412;
    private static String P2P_HW_MAC_ADDRESS = EMPTY_DEVICE_ADDRESS;
    private static final int P2P_INVITATION_WAKELOCK_DURATION = 30000;
    private static final int P2P_LISTEN_EXPIRATION_TIME = 2;
    private static final int P2P_LISTEN_OFFLOADING_CHAN_NUM = 99999;
    private static final int P2P_LISTEN_OFFLOADING_COUNT = 4;
    private static final int P2P_LISTEN_OFFLOADING_FIND_TIMEOUT = 1;
    private static final int P2P_LISTEN_OFFLOADING_INTERVAL = 31000;
    private static final int P2P_TRAFFIC_POLL = 143480;
    private static final int PEER_CONNECTION_USER_ACCEPT = 143362;
    public static final int PEER_CONNECTION_USER_CONFIRM = 143367;
    private static final int PEER_CONNECTION_USER_REJECT = 143363;
    private static String RANDOM_MAC_ADDRESS = EMPTY_DEVICE_ADDRESS;
    private static final String[] RECEIVER_PERMISSIONS_FOR_BROADCAST = {"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_WIFI_STATE"};
    private static final Boolean RELOAD = true;
    public static final int REMOVE_CLIENT_INFO = 143378;
    private static final String SERVER_ADDRESS = "192.168.49.1";
    public static final int SET_COUNTRY_CODE = 143379;
    public static final int SET_MIRACAST_MODE = 143374;
    private static final String SIDESYNC_ACTION_SINK_CONNECTED = "com.sec.android.sidesync.sink.SIDESYNC_CONNECTED";
    private static final String SIDESYNC_ACTION_SINK_DESTROYED = "com.sec.android.sidesync.sink.SERVICE_DESTROY";
    private static final String SIDESYNC_ACTION_SOURCE_CONNECTED = "com.sec.android.sidesync.source.SIDESYNC_CONNECTED";
    private static final String SIDESYNC_ACTION_SOURCE_DESTROYED = "com.sec.android.sidesync.source.SERVICE_DESTROY";
    private static final String SSRM_NOTIFICATION_PERMISSION = "com.samsung.android.permission.SSRM_NOTIFICATION_PERMISSION";
    private static final String TAG = "WifiP2pService";
    private static final int TIME_ELAPSED_AFTER_CONNECTED = 143413;
    private static final String WIFI_DIRECT_SETTINGS_PKGNAME = "com.android.settings";
    public static final int WIFI_ENABLE_PROCEED = 143380;
    private static String WIFI_HW_MAC_ADDRESS = EMPTY_DEVICE_ADDRESS;
    private static final String WIFI_P2P_NOTIFICATION_CHANNEL = "wifi_p2p_notification_channel";
    private static String chkWfdStatus = "disconnected";
    private static byte[] hash2_byte = {0, 0};
    private static byte[] hash_byte = {0, 0, 0};
    private static int intentValue = 0;
    private static final int mAdvancedOppDelayedDiscoverTime = 10000;
    private static final int mAdvancedOppListenTimeout = 20;
    private static final int mAdvancedOppScanIntervalTime = 3000;
    private static int mDurationForNoa = 0;
    private static long mStartTimeForNoa = 0;
    private static boolean mVerboseLoggingEnabled = true;
    private static long mWorkingTimeForNoa = 0;
    private static boolean mWpsSkip;
    private static int numofclients = 0;
    private static int sDisableP2pTimeoutIndex = 0;
    private static int sGroupCreatingTimeoutIndex = 0;
    private static int siopLevel = -3;
    private final String APP_ID = "android.net.wifi.p2p";
    private int idxIcon = 256;
    private InputMethodManager imm;
    private boolean isNightMode;
    private ActivityManager mActivityMgr;
    private boolean mAdvancedOppInProgress = false;
    private boolean mAdvancedOppReceiver = false;
    private boolean mAdvancedOppRemoveGroupAndJoin = false;
    private boolean mAdvancedOppRemoveGroupAndListen = false;
    private int mAdvancedOppScanRetryCount = 0;
    private boolean mAdvancedOppSender = false;
    private AlarmManager mAlarmManager;
    private boolean mAutonomousGroup;
    public P2pBigDataLog mBigData;
    private boolean mBleLatency = false;
    private Map<IBinder, Messenger> mClientChannelList = new HashMap();
    private ClientHandler mClientHandler;
    private HashMap<Messenger, ClientInfo> mClientInfoList = new HashMap<>();
    private ConnectivityManager mCm;
    private WifiP2pConnectReqInfo mConnReqInfo = new WifiP2pConnectReqInfo();
    private HashMap<String, WifiP2pConnectReqInfo> mConnReqInfoList = new HashMap<>();
    private int mConnectedDevicesCnt;
    private Notification mConnectedNotification;
    private HashMap<String, WifiP2pConnectedPeriodInfo> mConnectedPeriodInfoList = new HashMap<>();
    private String mConnectedPkgName = null;
    private Context mContext;
    private int mCountWifiAntenna = 1;
    private final Map<IBinder, DeathHandlerData> mDeathDataByBinder = new HashMap();
    private boolean mDelayedDiscoverPeers = false;
    private int mDelayedDiscoverPeersArg;
    private int mDelayedDiscoverPeersCmd;
    private String mDeviceNameInSettings = null;
    private DhcpResults mDhcpResults;
    private PowerManager.WakeLock mDialogWakeLock;
    private boolean mDiscoveryBlocked = false;
    private boolean mDiscoveryPostponed = false;
    private boolean mDiscoveryStarted = false;
    private Messenger mForegroundAppMessenger;
    private String mForegroundAppPkgName;
    private FrameworkFacade mFrameworkFacade;
    private String mInterface;
    private AlertDialog mInvitationDialog = null;
    private TextView mInvitationMsg;
    private IIpClient mIpClient;
    private int mIpClientStartIndex = 0;
    private boolean mIsBootComplete = false;
    private boolean mJoinExistingGroup;
    private int mLOCount = 0;
    private PendingIntent mLOTimerIntent;
    private int mLapseTime;
    private String mLastSetCountryCode;
    private boolean mListenOffloading = false;
    private LocationManager mLocationManager;
    private Object mLock = new Object();
    private int mMaxClientCnt = 0;
    private AlertDialog mMaximumConnectionDialog = null;
    private HashMap<String, Integer> mModeChange = new HashMap<>();
    private NetworkInfo mNetworkInfo;
    private Notification mNotification;
    INetworkManagementService mNwService;
    private P2pStateMachine mP2pStateMachine;
    private final boolean mP2pSupported;
    private boolean mPersistentGroup = false;
    private volatile int mPollTrafficIntervalMsecs = IWCEventManager.wifiOFFPending_MS;
    private PowerManager mPowerManager;
    private boolean mReceivedEnableP2p = false;
    private String mReinvokePersistent = null;
    private int mReinvokePersistentNetId = -1;
    private AsyncChannel mReplyChannel = new WifiAsyncChannel(TAG);
    private SemHqmManager mSemHqmManager;
    private String mServiceDiscReqId;
    private byte mServiceTransactionId = 0;
    private boolean mSetupInterfaceIsRunnging = false;
    private Notification mSoundNotification;
    private WifiP2pDevice mTempDevice = new WifiP2pDevice();
    private boolean mTemporarilyDisconnectedWifi = false;
    private WifiP2pDevice mThisDevice = new WifiP2pDevice();
    private long mTimeForGopsReceiver = 0;
    private PendingIntent mTimerIntent;
    private int mTrafficPollToken = 0;
    private Context mUiContext;
    private Context mUiContextDay;
    private Context mUiContextNight;
    private boolean mValidFreqConflict = false;
    private PowerManager.WakeLock mWakeLock;
    private boolean mWfdConnected = false;
    private boolean mWfdDialog = false;
    private WifiManager.WifiLock mWiFiLock = null;
    private int mWifiApState = 11;
    private WifiAwareManager mWifiAwareManager = null;
    private AsyncChannel mWifiChannel;
    private WifiInjector mWifiInjector;
    private WifiP2pMetrics mWifiP2pMetrics;
    private WifiPermissionsUtil mWifiPermissionsUtil;
    private int mWifiState = 1;
    private CountDownTimer mWpsTimer;
    EditText pin = null;
    private EditText pinConn;
    AlertDialog t_dialog = null;
    private boolean userRejected = false;

    static /* synthetic */ int access$1608(WifiP2pServiceImpl x0) {
        int i = x0.mLOCount;
        x0.mLOCount = i + 1;
        return i;
    }

    static /* synthetic */ int access$16204() {
        int i = sGroupCreatingTimeoutIndex + 1;
        sGroupCreatingTimeoutIndex = i;
        return i;
    }

    static /* synthetic */ int access$18508(WifiP2pServiceImpl x0) {
        int i = x0.mAdvancedOppScanRetryCount;
        x0.mAdvancedOppScanRetryCount = i + 1;
        return i;
    }

    static /* synthetic */ int access$19608(WifiP2pServiceImpl x0) {
        int i = x0.mTrafficPollToken;
        x0.mTrafficPollToken = i + 1;
        return i;
    }

    static /* synthetic */ int access$24410(WifiP2pServiceImpl x0) {
        int i = x0.mLapseTime;
        x0.mLapseTime = i - 1;
        return i;
    }

    static /* synthetic */ byte access$25404(WifiP2pServiceImpl x0) {
        byte b = (byte) (x0.mServiceTransactionId + 1);
        x0.mServiceTransactionId = b;
        return b;
    }

    static /* synthetic */ int access$8604() {
        int i = sDisableP2pTimeoutIndex + 1;
        sDisableP2pTimeoutIndex = i;
        return i;
    }

    public enum P2pStatus {
        SUCCESS,
        INFORMATION_IS_CURRENTLY_UNAVAILABLE,
        INCOMPATIBLE_PARAMETERS,
        LIMIT_REACHED,
        INVALID_PARAMETER,
        UNABLE_TO_ACCOMMODATE_REQUEST,
        PREVIOUS_PROTOCOL_ERROR,
        NO_COMMON_CHANNEL,
        UNKNOWN_P2P_GROUP,
        BOTH_GO_INTENT_15,
        INCOMPATIBLE_PROVISIONING_METHOD,
        REJECTED_BY_USER,
        UNKNOWN;

        public static P2pStatus valueOf(int error) {
            switch (error) {
                case 0:
                    return SUCCESS;
                case 1:
                    return INFORMATION_IS_CURRENTLY_UNAVAILABLE;
                case 2:
                    return INCOMPATIBLE_PARAMETERS;
                case 3:
                    return LIMIT_REACHED;
                case 4:
                    return INVALID_PARAMETER;
                case 5:
                    return UNABLE_TO_ACCOMMODATE_REQUEST;
                case 6:
                    return PREVIOUS_PROTOCOL_ERROR;
                case 7:
                    return NO_COMMON_CHANNEL;
                case 8:
                    return UNKNOWN_P2P_GROUP;
                case 9:
                    return BOTH_GO_INTENT_15;
                case 10:
                    return INCOMPATIBLE_PROVISIONING_METHOD;
                case 11:
                    return REJECTED_BY_USER;
                default:
                    return UNKNOWN;
            }
        }
    }

    /* access modifiers changed from: private */
    public class ClientHandler extends WifiHandler {
        ClientHandler(String tag, Looper looper) {
            super(tag, looper);
        }

        @Override // com.android.server.wifi.util.WifiHandler
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 139265:
                case 139268:
                case 139271:
                case 139274:
                case 139277:
                case 139280:
                case 139283:
                case 139285:
                case 139287:
                case 139292:
                case 139295:
                case 139298:
                case 139301:
                case 139304:
                case 139307:
                case 139310:
                case 139315:
                case 139318:
                case 139321:
                case 139323:
                case 139326:
                case 139329:
                case 139332:
                case 139335:
                case 139346:
                case 139349:
                case 139351:
                case 139354:
                case 139356:
                case 139358:
                case 139360:
                case 139361:
                case 139365:
                case 139368:
                case 139371:
                case 139372:
                case 139374:
                case 139375:
                case 139376:
                case 139377:
                case 139378:
                case 139380:
                case 139405:
                case 139406:
                case 139407:
                case 139408:
                case 139412:
                case 139414:
                case 139415:
                case 139419:
                case 139420:
                    WifiP2pServiceImpl.this.mP2pStateMachine.sendMessage(Message.obtain(msg));
                    return;
                default:
                    Slog.d(WifiP2pServiceImpl.TAG, "ClientHandler.handleMessage ignoring msg=" + msg);
                    return;
            }
        }
    }

    /* access modifiers changed from: package-private */
    @VisibleForTesting
    public void setWifiHandlerLogForTest(WifiLog log) {
        this.mClientHandler.setWifiLog(log);
    }

    /* access modifiers changed from: package-private */
    @VisibleForTesting
    public void setWifiLogForReplyChannel(WifiLog log) {
        this.mReplyChannel.setWifiLog(log);
    }

    /* access modifiers changed from: private */
    public class DeathHandlerData {
        IBinder.DeathRecipient mDeathRecipient;
        Messenger mMessenger;

        DeathHandlerData(IBinder.DeathRecipient dr, Messenger m) {
            this.mDeathRecipient = dr;
            this.mMessenger = m;
        }

        public String toString() {
            return "deathRecipient=" + this.mDeathRecipient + ", messenger=" + this.mMessenger;
        }
    }

    public WifiP2pServiceImpl(Context context, WifiInjector wifiInjector) {
        this.mContext = new ContextThemeWrapper(context, context.getResources().getIdentifier("@android:style/Theme.DeviceDefault.Light", null, null));
        this.mWifiInjector = wifiInjector;
        this.mWifiPermissionsUtil = this.mWifiInjector.getWifiPermissionsUtil();
        this.mFrameworkFacade = this.mWifiInjector.getFrameworkFacade();
        this.mWifiP2pMetrics = this.mWifiInjector.getWifiP2pMetrics();
        Context uiContext = ActivityThread.currentActivityThread().getSystemUiContext();
        this.mUiContextDay = new ContextThemeWrapper(uiContext, 16974123);
        this.mUiContextNight = new ContextThemeWrapper(uiContext, 16974120);
        this.mUiContext = this.mUiContextDay;
        this.mInterface = "p2p0";
        this.mActivityMgr = (ActivityManager) context.getSystemService("activity");
        this.mP2pSupported = this.mContext.getPackageManager().hasSystemFeature("android.hardware.wifi.direct");
        this.mPowerManager = (PowerManager) this.mContext.getSystemService("power");
        this.mWakeLock = this.mPowerManager.newWakeLock(1, TAG);
        this.mDialogWakeLock = this.mPowerManager.newWakeLock(268435482, TAG);
        this.mDialogWakeLock.setReferenceCounted(false);
        if (this.mContext.getPackageManager().hasSystemFeature("android.hardware.wifi.aware")) {
            this.mWifiAwareManager = (WifiAwareManager) this.mContext.getSystemService("wifiaware");
        }
        this.mAlarmManager = (AlarmManager) this.mContext.getSystemService("alarm");
        this.mTimerIntent = PendingIntent.getBroadcast(this.mContext, 0, new Intent(ACTION_P2P_STOPFIND_TIMER_EXPIRED, (Uri) null), 0);
        this.mLOTimerIntent = PendingIntent.getBroadcast(this.mContext, 0, new Intent(ACTION_P2P_LO_TIMER_EXPIRED, (Uri) null), 0);
        this.mThisDevice.primaryDeviceType = this.mContext.getResources().getString(17040004);
        HandlerThread wifiP2pThread = this.mWifiInjector.getWifiP2pServiceHandlerThread();
        this.mClientHandler = new ClientHandler(TAG, wifiP2pThread.getLooper());
        this.mP2pStateMachine = new P2pStateMachine(TAG, wifiP2pThread.getLooper(), this.mP2pSupported);
        this.mP2pStateMachine.start();
        this.mNetworkInfo = new NetworkInfo(13, 0, NETWORKTYPE, "");
        this.mBigData = new P2pBigDataLog();
        if (this.mPowerManager.isScreenOn()) {
            setProp("lcdon");
        } else {
            setProp("lcdoff");
        }
        this.mCountWifiAntenna = 2;
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        filter.addAction(ACTION_P2P_STOPFIND_TIMER_EXPIRED);
        filter.addAction(ACTION_P2P_LO_TIMER_EXPIRED);
        filter.addAction("com.samsung.android.knox.intent.action.RESTRICTION_DISABLE_WFD_INTERNAL");
        this.mContext.registerReceiver(new WifiStateReceiver(), filter);
        IntentFilter gopsFilter = new IntentFilter();
        gopsFilter.addAction(SIDESYNC_ACTION_SOURCE_CONNECTED);
        gopsFilter.addAction(SIDESYNC_ACTION_SINK_CONNECTED);
        gopsFilter.addAction(SIDESYNC_ACTION_SOURCE_DESTROYED);
        gopsFilter.addAction(SIDESYNC_ACTION_SINK_DESTROYED);
        gopsFilter.addAction("android.intent.action.SCREEN_ON");
        gopsFilter.addAction("android.intent.action.SCREEN_OFF");
        gopsFilter.addAction(ACTION_SMARTSWITCH_TRANSFER);
        gopsFilter.addAction(ACTION_CHECK_SIOP_LEVEL);
        gopsFilter.addAction("android.hardware.display.action.WIFI_DISPLAY_STATUS_CHANGED");
        this.mContext.registerReceiver(new GopsReceiver(), gopsFilter);
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.C05251 */

            public void onReceive(Context context, Intent intent) {
                WifiP2pServiceImpl.this.sendSetDeviceName();
            }
        }, new IntentFilter("com.android.settings.DEVICE_NAME_CHANGED"));
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.C05262 */

            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra("started", false)) {
                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                        Log.d(WifiP2pServiceImpl.TAG, "OnReceive - com.android.settings.wifi.p2p.SETTINGS_STRATED");
                    }
                    WifiP2pServiceImpl.this.sendSetDeviceName();
                    if (Build.VERSION.SDK_INT < 29) {
                        WifiP2pServiceImpl.this.mP2pStateMachine.setPhoneNumberIntoProbeResp();
                    }
                }
            }
        }, new IntentFilter("com.android.settings.wifi.p2p.SETTINGS_STRATED"));
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.C05273 */

            public void onReceive(Context context, Intent intent) {
                String simState = intent.getStringExtra("ss");
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    Log.d(WifiP2pServiceImpl.TAG, "OnReceive - android.intent.action.SIM_STATE_CHANGED, state : " + simState);
                }
                if ("LOADED".equals(simState) && Build.VERSION.SDK_INT < 29) {
                    WifiP2pServiceImpl.this.mP2pStateMachine.setPhoneNumberIntoProbeResp();
                }
            }
        }, new IntentFilter("android.intent.action.SIM_STATE_CHANGED"));
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.C05284 */

            public void onReceive(Context context, Intent intent) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    Log.d(WifiP2pServiceImpl.TAG, "OnReceive - ACTION_BOOT_COMPLETED");
                }
                WifiP2pServiceImpl.this.sendSetDeviceName();
                WifiP2pServiceImpl.this.mIsBootComplete = true;
                WifiP2pServiceImpl.this.mP2pStateMachine.sendMessage(WifiP2pServiceImpl.CMD_BOOT_COMPLETED);
            }
        }, new IntentFilter("android.intent.action.BOOT_COMPLETED"));
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.C05295 */

            public void onReceive(Context context, Intent intent) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    Log.d(WifiP2pServiceImpl.TAG, "OnReceive - ACTION_CONFIGURATION_CHANGED");
                }
                WifiP2pServiceImpl.this.isNightMode = (context.getResources().getConfiguration().uiMode & 48) == 32;
                if (WifiP2pServiceImpl.this.isNightMode && WifiP2pServiceImpl.this.mUiContext == WifiP2pServiceImpl.this.mUiContextDay) {
                    WifiP2pServiceImpl wifiP2pServiceImpl = WifiP2pServiceImpl.this;
                    wifiP2pServiceImpl.mUiContext = wifiP2pServiceImpl.mUiContextNight;
                    if (WifiP2pServiceImpl.this.mInvitationDialog != null && WifiP2pServiceImpl.this.mInvitationDialog.isShowing()) {
                        WifiP2pServiceImpl.this.dialogRejectForThemeChanging();
                    }
                } else if (!WifiP2pServiceImpl.this.isNightMode && WifiP2pServiceImpl.this.mUiContext == WifiP2pServiceImpl.this.mUiContextNight) {
                    WifiP2pServiceImpl wifiP2pServiceImpl2 = WifiP2pServiceImpl.this;
                    wifiP2pServiceImpl2.mUiContext = wifiP2pServiceImpl2.mUiContextDay;
                    if (WifiP2pServiceImpl.this.mInvitationDialog != null && WifiP2pServiceImpl.this.mInvitationDialog.isShowing()) {
                        WifiP2pServiceImpl.this.dialogRejectForThemeChanging();
                    }
                }
            }
        }, new IntentFilter("android.intent.action.CONFIGURATION_CHANGED"));
    }

    public void connectivityServiceReady() {
        this.mNwService = INetworkManagementService.Stub.asInterface(ServiceManager.getService("network_management"));
    }

    public void handleUserSwitch(int userId) {
        Log.d(TAG, "Removing p2p group when user switching");
        this.mP2pStateMachine.sendMessage(139280);
    }

    public void checkDeviceNameInSettings() {
        this.mDeviceNameInSettings = Settings.System.getString(this.mContext.getContentResolver(), "device_name");
        if (TextUtils.isEmpty(this.mDeviceNameInSettings)) {
            this.mDeviceNameInSettings = Settings.Global.getString(this.mContext.getContentResolver(), "device_name");
        }
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "checkDeviceNameInSettings: " + this.mDeviceNameInSettings);
        }
    }

    public void sendSetDeviceName() {
        checkDeviceNameInSettings();
        if (!TextUtils.isEmpty(this.mDeviceNameInSettings) && !this.mDeviceNameInSettings.equals(this.mP2pStateMachine.getPersistedDeviceName())) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "sendSetDeviceName: this will be set only in P2pEnabledState");
            }
            WifiP2pDevice wifiP2pDevice = this.mTempDevice;
            wifiP2pDevice.deviceName = this.mDeviceNameInSettings;
            this.mP2pStateMachine.sendMessage(139315, 0, 0, wifiP2pDevice);
        }
    }

    private class WifiStateReceiver extends BroadcastReceiver {
        private WifiStateReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
                WifiP2pServiceImpl.this.mWifiState = intent.getIntExtra("wifi_state", 1);
            } else if (action.equals("android.net.wifi.WIFI_AP_STATE_CHANGED")) {
                WifiP2pServiceImpl.this.mWifiApState = intent.getIntExtra("wifi_state", 11);
            } else if (action.equals("com.samsung.android.knox.intent.action.RESTRICTION_DISABLE_WFD_INTERNAL")) {
                if (!WifiP2pServiceImpl.this.isAllowWifiDirectByEDM()) {
                    WifiP2pServiceImpl.this.mP2pStateMachine.sendMessage(139280);
                }
            } else if (action.equals(WifiP2pServiceImpl.ACTION_P2P_STOPFIND_TIMER_EXPIRED)) {
                Log.d(WifiP2pServiceImpl.TAG, "ACTION_P2P_STOPFIND_TIMER_EXPIRED");
                WifiP2pServiceImpl.this.mP2pStateMachine.sendMessage(139268);
            } else if (action.equals(WifiP2pServiceImpl.ACTION_P2P_LO_TIMER_EXPIRED)) {
                Log.d(WifiP2pServiceImpl.TAG, "ACTION_P2P_LO_TIMER_EXPIRED");
                if (WifiP2pServiceImpl.this.mLOCount < 4) {
                    WifiP2pServiceImpl.this.mP2pStateMachine.sendMessage(139265, WifiP2pServiceImpl.P2P_LISTEN_OFFLOADING_CHAN_NUM);
                    return;
                }
                Log.d(WifiP2pServiceImpl.TAG, " Reset listen offloading count to 0! LO ended!");
                WifiP2pServiceImpl.this.mLOCount = 0;
                WifiP2pServiceImpl.this.mListenOffloading = false;
            }
        }
    }

    private class GopsReceiver extends BroadcastReceiver {
        private GopsReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            boolean chkSmswTransfer;
            if (intent != null && intent.getAction() != null) {
                String action = intent.getAction();
                WifiP2pServiceImpl.this.mTimeForGopsReceiver = System.currentTimeMillis();
                Log.d(WifiP2pServiceImpl.TAG, "GopsReceiver : received : " + action);
                if (WifiP2pServiceImpl.SIDESYNC_ACTION_SOURCE_CONNECTED.equals(action)) {
                    WifiP2pServiceImpl.this.setProp("sscon");
                } else if (WifiP2pServiceImpl.SIDESYNC_ACTION_SOURCE_DESTROYED.equals(action)) {
                    WifiP2pServiceImpl.this.setProp("ssdis");
                } else if (WifiP2pServiceImpl.SIDESYNC_ACTION_SINK_CONNECTED.equals(action)) {
                    WifiP2pServiceImpl.this.setProp("sicon");
                } else if (WifiP2pServiceImpl.SIDESYNC_ACTION_SINK_DESTROYED.equals(action)) {
                    WifiP2pServiceImpl.this.setProp("sidis");
                } else if ("android.intent.action.SCREEN_ON".equals(action)) {
                    WifiP2pServiceImpl.this.setProp("lcdon");
                } else if ("android.intent.action.SCREEN_OFF".equals(action)) {
                    WifiP2pServiceImpl.this.setProp("lcdoff");
                } else if (WifiP2pServiceImpl.ACTION_SMARTSWITCH_TRANSFER.equals(action)) {
                    try {
                        chkSmswTransfer = intent.getBooleanExtra("smartswitch_transfer", false);
                    } catch (Exception e) {
                        chkSmswTransfer = false;
                        Log.e(WifiP2pServiceImpl.TAG, "smartswitch_transfer is not set. because exception is occured.");
                    }
                    Log.i(WifiP2pServiceImpl.TAG, "smartswitch_transfer = " + chkSmswTransfer);
                    if (chkSmswTransfer) {
                        WifiP2pServiceImpl.this.setProp("smswon");
                    } else {
                        WifiP2pServiceImpl.this.setProp("smswoff");
                    }
                } else if (WifiP2pServiceImpl.ACTION_CHECK_SIOP_LEVEL.equals(action)) {
                    try {
                        int unused = WifiP2pServiceImpl.siopLevel = intent.getIntExtra("siop_level_broadcast", -3);
                    } catch (Exception e2) {
                        int unused2 = WifiP2pServiceImpl.siopLevel = -3;
                        Log.e(WifiP2pServiceImpl.TAG, "siop_level was set to the default value. because exception is occured.");
                    }
                    Log.i(WifiP2pServiceImpl.TAG, "siop_level = " + WifiP2pServiceImpl.siopLevel);
                    WifiP2pServiceImpl.this.setProp("siopLevCha");
                } else if ("android.hardware.display.action.WIFI_DISPLAY_STATUS_CHANGED".equals(action)) {
                    try {
                        int wfdStatus = intent.getParcelableExtra("android.hardware.display.extra.WIFI_DISPLAY_STATUS").getActiveDisplayState();
                        if (wfdStatus == 2) {
                            String unused3 = WifiP2pServiceImpl.chkWfdStatus = "connected";
                        } else if (wfdStatus == 1) {
                            String unused4 = WifiP2pServiceImpl.chkWfdStatus = "connecting";
                        } else {
                            String unused5 = WifiP2pServiceImpl.chkWfdStatus = "disconnected";
                        }
                    } catch (Exception e3) {
                        String unused6 = WifiP2pServiceImpl.chkWfdStatus = "disconnected";
                        Log.e(WifiP2pServiceImpl.TAG, "chkWfdStatus was set to the default value. because exception is occured.");
                    }
                    Log.i(WifiP2pServiceImpl.TAG, "chkWfdStatus = " + WifiP2pServiceImpl.chkWfdStatus);
                    WifiP2pServiceImpl.this.setProp("wfdSta");
                }
                Log.d(WifiP2pServiceImpl.TAG, "GopsReceiver : received : " + action + " time : " + (System.currentTimeMillis() - WifiP2pServiceImpl.this.mTimeForGopsReceiver) + "ms");
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void setProp(String name) {
        char c = 65535;
        try {
            switch (name.hashCode()) {
                case -2080821647:
                    if (name.equals("smswoff")) {
                        c = 7;
                        break;
                    }
                    break;
                case -1482467299:
                    if (name.equals("groupexit")) {
                        c = 14;
                        break;
                    }
                    break;
                case -1108501374:
                    if (name.equals("lcdoff")) {
                        c = 5;
                        break;
                    }
                    break;
                case -898407267:
                    if (name.equals("smswon")) {
                        c = 6;
                        break;
                    }
                    break;
                case -790836629:
                    if (name.equals("wfdSta")) {
                        c = '\r';
                        break;
                    }
                    break;
                case 102789228:
                    if (name.equals("lcdon")) {
                        c = 4;
                        break;
                    }
                    break;
                case 109431660:
                    if (name.equals("sicon")) {
                        c = 2;
                        break;
                    }
                    break;
                case 109432440:
                    if (name.equals("sidis")) {
                        c = 3;
                        break;
                    }
                    break;
                case 109729570:
                    if (name.equals("sscon")) {
                        c = 0;
                        break;
                    }
                    break;
                case 109730350:
                    if (name.equals("ssdis")) {
                        c = 1;
                        break;
                    }
                    break;
                case 657006763:
                    if (name.equals("openInvitationDialog")) {
                        c = '\b';
                        break;
                    }
                    break;
                case 1138883286:
                    if (name.equals("siopLevCha")) {
                        c = '\f';
                        break;
                    }
                    break;
                case 1272544337:
                    if (name.equals("apstacon")) {
                        c = '\n';
                        break;
                    }
                    break;
                case 1272545117:
                    if (name.equals("apstadis")) {
                        c = 11;
                        break;
                    }
                    break;
                case 1807858777:
                    if (name.equals("closeInvitationDialog")) {
                        c = '\t';
                        break;
                    }
                    break;
            }
            switch (c) {
                case 0:
                    intentValue |= 2;
                    SystemProperties.set("wlan.p2p.chkintent", Integer.toString(intentValue));
                    return;
                case 1:
                    intentValue &= -3;
                    SystemProperties.set("wlan.p2p.chkintent", Integer.toString(intentValue));
                    return;
                case 2:
                    intentValue |= 4;
                    SystemProperties.set("wlan.p2p.chkintent", Integer.toString(intentValue));
                    return;
                case 3:
                    intentValue &= -5;
                    SystemProperties.set("wlan.p2p.chkintent", Integer.toString(intentValue));
                    return;
                case 4:
                    intentValue |= 8;
                    SystemProperties.set("wlan.p2p.chkintent", Integer.toString(intentValue));
                    return;
                case 5:
                    intentValue &= -9;
                    SystemProperties.set("wlan.p2p.chkintent", Integer.toString(intentValue));
                    return;
                case 6:
                    intentValue |= 16;
                    SystemProperties.set("wlan.p2p.chkintent", Integer.toString(intentValue));
                    return;
                case 7:
                    intentValue &= -17;
                    SystemProperties.set("wlan.p2p.chkintent", Integer.toString(intentValue));
                    return;
                case '\b':
                    intentValue |= 32;
                    SystemProperties.set("wlan.p2p.chkintent", Integer.toString(intentValue));
                    return;
                case '\t':
                    intentValue &= -33;
                    SystemProperties.set("wlan.p2p.chkintent", Integer.toString(intentValue));
                    return;
                case '\n':
                    if (numofclients >= 0) {
                        numofclients++;
                    }
                    SystemProperties.set("wlan.p2p.numclient", Integer.toString(numofclients));
                    return;
                case 11:
                    if (numofclients > 0) {
                        numofclients--;
                    }
                    SystemProperties.set("wlan.p2p.numclient", Integer.toString(numofclients));
                    return;
                case '\f':
                    SystemProperties.set("wlan.p2p.temp", Integer.toString(siopLevel));
                    return;
                case '\r':
                    SystemProperties.set("wlan.p2p.wfdsta", chkWfdStatus);
                    return;
                default:
                    numofclients = 0;
                    SystemProperties.set("wlan.p2p.numclient", Integer.toString(numofclients));
                    intentValue = 0;
                    SystemProperties.set("wlan.p2p.chkintent", Integer.toString(intentValue));
                    chkWfdStatus = "disconnected";
                    SystemProperties.set("wlan.p2p.wfdsta", chkWfdStatus);
                    if (this.mPowerManager.isScreenOn()) {
                        setProp("lcdon");
                        return;
                    } else {
                        setProp("lcdoff");
                        return;
                    }
            }
        } catch (Exception e) {
            Log.e(TAG, "setprop for GOPS is failed.");
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private long checkTimeNoa(int noa_num, int noa_dur) {
        if (noa_num != 0) {
            if (noa_num == 1) {
                if (mStartTimeForNoa != 0) {
                    mWorkingTimeForNoa += ((System.currentTimeMillis() - mStartTimeForNoa) * ((long) mDurationForNoa)) / 100;
                }
                mStartTimeForNoa = System.currentTimeMillis();
                mDurationForNoa = noa_dur;
                return 0;
            } else if (!(noa_num == 2 || noa_num == 3 || noa_num == 4 || noa_num == 5)) {
                long result = mWorkingTimeForNoa;
                Log.d(TAG, "mWorkingTimeForNoa: " + mWorkingTimeForNoa + " result: " + result);
                mWorkingTimeForNoa = 0;
                return result;
            }
        }
        if (mStartTimeForNoa == 0) {
            return 0;
        }
        mWorkingTimeForNoa += ((System.currentTimeMillis() - mStartTimeForNoa) * ((long) mDurationForNoa)) / 100;
        mStartTimeForNoa = 0;
        mDurationForNoa = 0;
        return 0;
    }

    public boolean isInactiveState() {
        enforceAccessPermission();
        enforceChangePermission();
        return this.mP2pStateMachine.mIsInactiveState;
    }

    public int getWifiP2pState() {
        enforceAccessPermission();
        enforceChangePermission();
        return this.mP2pStateMachine.mP2pState;
    }

    private void enforceAccessPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.ACCESS_WIFI_STATE", TAG);
    }

    private void enforceChangePermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.CHANGE_WIFI_STATE", TAG);
    }

    private void enforceConnectivityInternalPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.CONNECTIVITY_INTERNAL", TAG);
    }

    private int checkConnectivityInternalPermission() {
        return this.mContext.checkCallingOrSelfPermission("android.permission.CONNECTIVITY_INTERNAL");
    }

    private int checkLocationHardwarePermission() {
        return this.mContext.checkCallingOrSelfPermission("android.permission.LOCATION_HARDWARE");
    }

    private void enforceConnectivityInternalOrLocationHardwarePermission() {
        if (checkConnectivityInternalPermission() != 0 && checkLocationHardwarePermission() != 0) {
            enforceConnectivityInternalPermission();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void stopIpClient() {
        this.mIpClientStartIndex++;
        IIpClient iIpClient = this.mIpClient;
        if (iIpClient != null) {
            try {
                iIpClient.stop();
            } catch (RemoteException e) {
                e.rethrowFromSystemServer();
            }
            this.mIpClient = null;
        }
        this.mDhcpResults = null;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void startIpClient(String ifname, Handler smHandler) {
        stopIpClient();
        this.mIpClientStartIndex++;
        IpClientUtil.makeIpClient(this.mContext, ifname, new IpClientCallbacksImpl(this.mIpClientStartIndex, smHandler));
    }

    /* access modifiers changed from: private */
    public class IpClientCallbacksImpl extends IpClientCallbacks {
        private final Handler mHandler;
        private final int mStartIndex;

        private IpClientCallbacksImpl(int startIndex, Handler handler) {
            this.mStartIndex = startIndex;
            this.mHandler = handler;
        }

        public void onIpClientCreated(IIpClient ipClient) {
            this.mHandler.post(new Runnable(ipClient) {
                /* class com.android.server.wifi.p2p.RunnableC0521x8a18e5e1 */
                private final /* synthetic */ IIpClient f$1;

                {
                    this.f$1 = r2;
                }

                public final void run() {
                    WifiP2pServiceImpl.IpClientCallbacksImpl.this.mo5682x36e41d72(this.f$1);
                }
            });
        }

        /* renamed from: lambda$onIpClientCreated$0$WifiP2pServiceImpl$IpClientCallbacksImpl */
        public /* synthetic */ void mo5682x36e41d72(IIpClient ipClient) {
            if (WifiP2pServiceImpl.this.mIpClientStartIndex == this.mStartIndex) {
                WifiP2pServiceImpl.this.mIpClient = ipClient;
                try {
                    WifiP2pServiceImpl.this.mIpClient.startProvisioning(new ProvisioningConfiguration.Builder().withoutIpReachabilityMonitor().withPreDhcpAction(30000).withProvisioningTimeoutMs(36000).build().toStableParcelable());
                } catch (RemoteException e) {
                    e.rethrowFromSystemServer();
                }
            }
        }

        public void onPreDhcpAction() {
            WifiP2pServiceImpl.this.mP2pStateMachine.sendMessage(WifiP2pServiceImpl.IPC_PRE_DHCP_ACTION);
        }

        public void onPostDhcpAction() {
            WifiP2pServiceImpl.this.mP2pStateMachine.sendMessage(WifiP2pServiceImpl.IPC_POST_DHCP_ACTION);
        }

        public void onNewDhcpResults(DhcpResults dhcpResults) {
            WifiP2pServiceImpl.this.mP2pStateMachine.sendMessage(WifiP2pServiceImpl.IPC_DHCP_RESULTS, dhcpResults);
        }

        public void onProvisioningSuccess(LinkProperties newLp) {
            WifiP2pServiceImpl.this.mP2pStateMachine.sendMessage(WifiP2pServiceImpl.IPC_PROVISIONING_SUCCESS);
        }

        public void onProvisioningFailure(LinkProperties newLp) {
            WifiP2pServiceImpl.this.mP2pStateMachine.sendMessage(WifiP2pServiceImpl.IPC_PROVISIONING_FAILURE);
        }
    }

    public Messenger getMessenger(IBinder binder) {
        Messenger messenger;
        enforceAccessPermission();
        enforceChangePermission();
        synchronized (this.mLock) {
            messenger = new Messenger(this.mClientHandler);
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "getMessenger: uid=" + getCallingUid() + ", binder=" + binder + ", messenger=" + messenger);
            }
            IBinder.DeathRecipient dr = new IBinder.DeathRecipient(binder) {
                /* class com.android.server.wifi.p2p.$$Lambda$WifiP2pServiceImpl$LwceCrSRIRY_Lp9TjCEZZ62jls */
                private final /* synthetic */ IBinder f$1;

                {
                    this.f$1 = r2;
                }

                public final void binderDied() {
                    WifiP2pServiceImpl.this.lambda$getMessenger$0$WifiP2pServiceImpl(this.f$1);
                }
            };
            try {
                binder.linkToDeath(dr, 0);
                this.mDeathDataByBinder.put(binder, new DeathHandlerData(dr, messenger));
            } catch (RemoteException e) {
                Log.e(TAG, "Error on linkToDeath: e=" + e);
            }
            this.mP2pStateMachine.sendMessage(ENABLE_P2P);
        }
        return messenger;
    }

    public /* synthetic */ void lambda$getMessenger$0$WifiP2pServiceImpl(IBinder binder) {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "binderDied: binder=" + binder);
        }
        close(binder);
    }

    public Messenger getP2pStateMachineMessenger() {
        enforceConnectivityInternalOrLocationHardwarePermission();
        enforceAccessPermission();
        enforceChangePermission();
        return new Messenger(this.mP2pStateMachine.getHandler());
    }

    public void close(IBinder binder) {
        enforceAccessPermission();
        enforceChangePermission();
        synchronized (this.mLock) {
            DeathHandlerData dhd = this.mDeathDataByBinder.get(binder);
            if (dhd == null) {
                Log.w(TAG, "close(): no death recipient for binder");
                return;
            }
            this.mP2pStateMachine.sendMessage(REMOVE_CLIENT_INFO, 0, 0, binder);
            binder.unlinkToDeath(dhd.mDeathRecipient, 0);
            this.mDeathDataByBinder.remove(binder);
            if (dhd.mMessenger != null && this.mDeathDataByBinder.isEmpty()) {
                try {
                    dhd.mMessenger.send(this.mClientHandler.obtainMessage(139268));
                    dhd.mMessenger.send(this.mClientHandler.obtainMessage(139280));
                } catch (RemoteException e) {
                    Log.e(TAG, "close: Failed sending clean-up commands: e=" + e);
                }
                this.mP2pStateMachine.sendMessage(DISABLE_P2P);
            }
        }
    }

    public void setMiracastMode(int mode) {
        enforceConnectivityInternalPermission();
        checkConfigureWifiDisplayPermission();
        this.mP2pStateMachine.sendMessage(SET_MIRACAST_MODE, mode);
    }

    public void checkConfigureWifiDisplayPermission() {
        if (!getWfdPermission(Binder.getCallingUid())) {
            throw new SecurityException("Wifi Display Permission denied for uid = " + Binder.getCallingUid());
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean getWfdPermission(int uid) {
        if (this.mWifiInjector == null) {
            this.mWifiInjector = WifiInjector.getInstance();
        }
        return this.mWifiInjector.getWifiPermissionsWrapper().getUidPermission("android.permission.CONFIGURE_WIFI_DISPLAY", uid) != -1;
    }

    /* access modifiers changed from: protected */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.DUMP") != 0) {
            pw.println("Permission Denial: can't dump WifiP2pService from from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        this.mP2pStateMachine.dump(fd, pw, args);
        pw.println("mAutonomousGroup " + this.mAutonomousGroup);
        pw.println("mJoinExistingGroup " + this.mJoinExistingGroup);
        pw.println("mDiscoveryStarted " + this.mDiscoveryStarted);
        pw.println("mNetworkInfo " + this.mNetworkInfo);
        pw.println("mTemporarilyDisconnectedWifi " + this.mTemporarilyDisconnectedWifi);
        pw.println("mServiceDiscReqId " + this.mServiceDiscReqId);
        pw.println("mDeathDataByBinder " + this.mDeathDataByBinder);
        pw.println("mClientInfoList " + this.mClientInfoList.size());
        pw.println();
        IIpClient ipClient = this.mIpClient;
        if (ipClient != null) {
            pw.println("mIpClient:");
            IpClientUtil.dumpIpClient(ipClient, fd, pw, args);
        }
    }

    private class P2pBigDataLog {
        private static final String KEY_CNM = "WCVN";
        private static final String KEY_CONN_PERIOD = "CONP";
        private static final String KEY_CONN_RECEIVED = "CSOR";
        private static final String KEY_DISCONNECT_REASON = "DISR";
        private static final String KEY_DRV_VER = "WDRV";
        private static final String KEY_FREQ = "FREQ";
        private static final String KEY_FW_VER = "WFWV";
        private static final String KEY_GROUP_FORMATION_RESULT = "GRFR";
        private static final String KEY_GROUP_NEGO = "GRNE";
        private static final String KEY_IS_GO = "ISGO";
        private static final String KEY_NOA_PERIOD = "NOAP";
        private static final String KEY_NUM_CLIENT = "NOCL";
        private static final String KEY_PEER_DEVICE_TYPE = "DEVT";
        private static final String KEY_PEER_GO_INTENT = "PINT";
        private static final String KEY_PEER_MANUFACTURER = "MANU";
        private static final String KEY_PERSISTENT = "PSTC";
        private static final String KEY_PKG_NAME = "PKGN";
        private static final String PATH_OF_WIFIVER_INFO = "/data/misc/conn/.wifiver.info";
        private static final String TAG = "P2pBigDataLog";
        private static final String WIFI_VER_PREFIX_BRCM = "HD_ver";
        private static final String WIFI_VER_PREFIX_MAVL = "received";
        private static final String WIFI_VER_PREFIX_QCA = "FW:";
        private static final String WIFI_VER_PREFIX_QCOM = "CNSS";
        private static final String WIFI_VER_PREFIX_SPRTRM = "is 0x";
        private final String APP_ID = "android.net.wifi.p2p";
        public String mChipsetName;
        public String mConnReceived;
        public String mConnectionPeriod;
        public String mDisconnectReason;
        public String mDriverVer;
        public String mFirmwareVer;
        public String mFreq;
        public String mGroupNego;
        public String mIsGroupOwner;
        public String mNoaPeriod;
        public String mNumClient;
        public String mPeerDevType;
        public String mPeerGOIntent;
        public String mPeerManufacturer;
        public String mPersistent;
        public String mPkgName;
        public String mResult;

        public P2pBigDataLog() {
        }

        public void initialize() {
            this.mIsGroupOwner = "";
            this.mNumClient = "";
            this.mFreq = "";
            this.mConnectionPeriod = "";
            this.mNoaPeriod = "";
            this.mPeerManufacturer = "";
            this.mPeerDevType = "";
            this.mDisconnectReason = "";
            this.mPkgName = "";
            this.mConnReceived = "";
            this.mPersistent = "";
            this.mGroupNego = "";
            this.mPeerGOIntent = "";
            this.mResult = "";
        }

        public String getJsonFormat(String feature) {
            if (feature == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            if ("WDCL".equals(feature)) {
                sb.append(convertToQuotedString(KEY_FW_VER) + ":");
                sb.append(convertToQuotedString(this.mFirmwareVer) + ",");
                sb.append(convertToQuotedString(KEY_DRV_VER) + ":");
                sb.append(convertToQuotedString(this.mDriverVer) + ",");
                sb.append(convertToQuotedString(KEY_CNM) + ":");
                sb.append(convertToQuotedString(this.mChipsetName) + ",");
                sb.append(convertToQuotedString(KEY_IS_GO) + ":");
                sb.append(convertToQuotedString(this.mIsGroupOwner) + ",");
                sb.append(convertToQuotedString(KEY_NUM_CLIENT) + ":");
                sb.append(convertToQuotedString(this.mNumClient) + ",");
                sb.append(convertToQuotedString(KEY_FREQ) + ":");
                sb.append(convertToQuotedString(this.mFreq) + ",");
                sb.append(convertToQuotedString(KEY_PKG_NAME) + ":");
                sb.append(convertToQuotedString(this.mPkgName) + ",");
                sb.append(convertToQuotedString(KEY_CONN_PERIOD) + ":");
                sb.append(convertToQuotedString(this.mConnectionPeriod) + ",");
                sb.append(convertToQuotedString(KEY_NOA_PERIOD) + ":");
                sb.append(convertToQuotedString(this.mNoaPeriod) + ",");
                sb.append(convertToQuotedString(KEY_PEER_MANUFACTURER) + ":");
                sb.append(convertToQuotedString(this.mPeerManufacturer) + ",");
                sb.append(convertToQuotedString(KEY_PEER_DEVICE_TYPE) + ":");
                sb.append(convertToQuotedString(this.mPeerDevType) + ",");
                sb.append(convertToQuotedString(KEY_DISCONNECT_REASON) + ":");
                sb.append(convertToQuotedString(this.mDisconnectReason));
            } else if ("WDGF".equals(feature)) {
                sb.append(convertToQuotedString(KEY_FW_VER) + ":");
                sb.append(convertToQuotedString(this.mFirmwareVer) + ",");
                sb.append(convertToQuotedString(KEY_DRV_VER) + ":");
                sb.append(convertToQuotedString(this.mDriverVer) + ",");
                sb.append(convertToQuotedString(KEY_CNM) + ":");
                sb.append(convertToQuotedString(this.mChipsetName) + ",");
                sb.append(convertToQuotedString(KEY_PKG_NAME) + ":");
                sb.append(convertToQuotedString(this.mPkgName) + ",");
                sb.append(convertToQuotedString(KEY_CONN_RECEIVED) + ":");
                sb.append(convertToQuotedString(this.mConnReceived) + ",");
                sb.append(convertToQuotedString(KEY_PERSISTENT) + ":");
                sb.append(convertToQuotedString(this.mPersistent) + ",");
                sb.append(convertToQuotedString(KEY_GROUP_NEGO) + ":");
                sb.append(convertToQuotedString(this.mGroupNego) + ",");
                sb.append(convertToQuotedString(KEY_PEER_MANUFACTURER) + ":");
                sb.append(convertToQuotedString(this.mPeerManufacturer) + ",");
                sb.append(convertToQuotedString(KEY_PEER_DEVICE_TYPE) + ":");
                sb.append(convertToQuotedString(this.mPeerDevType) + ",");
                sb.append(convertToQuotedString(KEY_PEER_GO_INTENT) + ":");
                sb.append(convertToQuotedString(this.mPeerGOIntent) + ",");
                sb.append(convertToQuotedString(KEY_GROUP_FORMATION_RESULT) + ":");
                sb.append(convertToQuotedString(this.mResult));
            }
            return sb.toString();
        }

        public boolean parseData(String feature, String data) {
            if (feature == null || data == null) {
                return false;
            }
            String[] array = data.split("\\s+");
            if ("WDCL".equals(feature)) {
                if (array.length != 9) {
                    Log.d(TAG, "Wrong parseData for WDCL, length : " + array.length);
                    return false;
                }
                int index = 0 + 1;
                this.mIsGroupOwner = array[0];
                int index2 = index + 1;
                this.mNumClient = array[index];
                int index3 = index2 + 1;
                this.mFreq = array[index2];
                int index4 = index3 + 1;
                this.mPkgName = array[index3];
                int index5 = index4 + 1;
                this.mConnectionPeriod = array[index4];
                int index6 = index5 + 1;
                this.mNoaPeriod = array[index5];
                int index7 = index6 + 1;
                this.mPeerManufacturer = array[index6];
                int index8 = index7 + 1;
                this.mPeerDevType = array[index7];
                int i = index8 + 1;
                this.mDisconnectReason = array[index8];
                return true;
            } else if (!"WDGF".equals(feature)) {
                return false;
            } else {
                if (array.length != 8) {
                    Log.d(TAG, "Wrong parseData for WDGF, length : " + array.length);
                    return false;
                }
                int index9 = 0 + 1;
                this.mPkgName = array[0];
                int index10 = index9 + 1;
                this.mConnReceived = array[index9];
                int index11 = index10 + 1;
                this.mPersistent = array[index10];
                int index12 = index11 + 1;
                this.mGroupNego = array[index11];
                int index13 = index12 + 1;
                this.mPeerManufacturer = array[index12];
                int index14 = index13 + 1;
                this.mPeerDevType = array[index13];
                int index15 = index14 + 1;
                this.mPeerGOIntent = array[index14];
                int i2 = index15 + 1;
                this.mResult = array[index15];
                return true;
            }
        }

        private String getFirmwareVer() {
            String retString;
            FileReader fr = null;
            BufferedReader br = null;
            try {
                FileReader fr2 = new FileReader(PATH_OF_WIFIVER_INFO);
                BufferedReader br2 = new BufferedReader(fr2);
                String verString = br2.readLine();
                if (verString != null) {
                    if (verString.contains(WIFI_VER_PREFIX_BRCM)) {
                        verString = br2.readLine();
                        if (verString != null) {
                            int verStart = verString.indexOf("version");
                            if (verStart != -1) {
                                int verStart2 = "version".length() + verStart + 1;
                                String retString2 = verString.substring(verStart2, verString.indexOf(" ", verStart2));
                                try {
                                    br2.close();
                                    fr2.close();
                                    return retString2;
                                } catch (IOException e) {
                                    return "File Close error";
                                }
                            } else {
                                retString = "NG";
                            }
                        } else {
                            try {
                                br2.close();
                                fr2.close();
                                return "file was damaged, it need check !";
                            } catch (IOException e2) {
                                return "File Close error";
                            }
                        }
                    } else if (verString.contains(WIFI_VER_PREFIX_QCOM)) {
                        int verStart3 = verString.indexOf(WIFI_VER_PREFIX_QCOM);
                        if (verStart3 != -1) {
                            String retString3 = verString.substring(verStart3 + "CNSS-PR-".length());
                            try {
                                br2.close();
                                fr2.close();
                                return retString3;
                            } catch (IOException e3) {
                                return "File Close error";
                            }
                        } else {
                            retString = "NG";
                        }
                    } else if (verString.contains(WIFI_VER_PREFIX_QCA)) {
                        int verStart4 = verString.indexOf("FW");
                        if (verStart4 != -1) {
                            String retString4 = verString.substring("FW".length() + verStart4 + 1, verString.indexOf("HW") - 2);
                            try {
                                br2.close();
                                fr2.close();
                                return retString4;
                            } catch (IOException e4) {
                                return "File Close error";
                            }
                        } else {
                            retString = "NG";
                        }
                    } else if (verString.contains(WIFI_VER_PREFIX_MAVL)) {
                        int verStart5 = verString.indexOf(".p") + 1;
                        verString = verString.substring(verStart5);
                        if (verStart5 != -1) {
                            String retString5 = verString.substring(0, verString.indexOf("-"));
                            try {
                                br2.close();
                                fr2.close();
                                return retString5;
                            } catch (IOException e5) {
                                return "File Close error";
                            }
                        } else {
                            retString = "NG";
                        }
                    } else if (!verString.contains(WIFI_VER_PREFIX_SPRTRM)) {
                        retString = "NG";
                    } else if (verString.indexOf("driver version is ") + "driver version is ".length() + 1 != -1) {
                        String retString6 = verString.substring(0, verString.indexOf("] ["));
                        try {
                            br2.close();
                            fr2.close();
                            return retString6;
                        } catch (IOException e6) {
                            return "File Close error";
                        }
                    } else {
                        retString = "NG";
                    }
                    if ("NG".equals(retString)) {
                        try {
                            br2.close();
                            fr2.close();
                            return verString;
                        } catch (IOException e7) {
                            return "File Close error";
                        }
                    } else {
                        try {
                            br2.close();
                            fr2.close();
                            return "error";
                        } catch (IOException e8) {
                            return "File Close error";
                        }
                    }
                } else {
                    try {
                        br2.close();
                        fr2.close();
                        return "file is null .. !";
                    } catch (IOException e9) {
                        return "File Close error";
                    }
                }
            } catch (IOException e10) {
                if (0 != 0) {
                    try {
                        br.close();
                    } catch (IOException e11) {
                        return "File Close error";
                    }
                }
                if (0 != 0) {
                    fr.close();
                }
                return "/data/misc/conn/.wifiver.info doesn't exist or there are something wrong on handling it";
            }
        }

        private String getDriverVer() {
            String retString;
            FileReader fr = null;
            BufferedReader br = null;
            try {
                FileReader fr2 = new FileReader(PATH_OF_WIFIVER_INFO);
                BufferedReader br2 = new BufferedReader(fr2);
                String verString = br2.readLine();
                if (verString != null) {
                    if (verString.contains(WIFI_VER_PREFIX_BRCM)) {
                        int verStart = verString.indexOf("HD_ver:");
                        if (verStart != -1) {
                            int verStart2 = "HD_ver:".length() + verStart + 1;
                            String retString2 = verString.substring(verStart2, verString.indexOf(" ", verStart2));
                            try {
                                br2.close();
                                fr2.close();
                                return retString2;
                            } catch (IOException e) {
                                return "File Close error";
                            }
                        } else {
                            retString = "NG";
                        }
                    } else if (verString.contains(WIFI_VER_PREFIX_QCOM)) {
                        int verStart3 = verString.indexOf("v");
                        if (verStart3 != -1) {
                            String retString3 = verString.substring(verStart3 + "v".length(), verString.indexOf(" CNSS"));
                            try {
                                br2.close();
                                fr2.close();
                                return retString3;
                            } catch (IOException e2) {
                                return "File Close error";
                            }
                        } else {
                            retString = "NG";
                        }
                    } else if (verString.contains(WIFI_VER_PREFIX_QCA)) {
                        int verStart4 = verString.indexOf("SW");
                        if (verStart4 != -1) {
                            String retString4 = verString.substring("SW".length() + verStart4 + 1, verString.indexOf("FW") - 2);
                            try {
                                br2.close();
                                fr2.close();
                                return retString4;
                            } catch (IOException e3) {
                                return "File Close error";
                            }
                        } else {
                            retString = "NG";
                        }
                    } else if (verString.contains(WIFI_VER_PREFIX_MAVL)) {
                        int verStart5 = verString.indexOf("-GPL") - 4;
                        if (verStart5 != -1) {
                            String retString5 = verString.substring(verStart5, verString.indexOf("-GPL"));
                            try {
                                br2.close();
                                fr2.close();
                                return retString5;
                            } catch (IOException e4) {
                                return "File Close error";
                            }
                        } else {
                            retString = "NG";
                        }
                    } else if (verString.contains(WIFI_VER_PREFIX_SPRTRM)) {
                        int verStart6 = verString.indexOf("cp version is ") + "cp version is ".length();
                        if (verStart6 != -1) {
                            String retString6 = verString.substring(verStart6, verString.indexOf("date") - 2);
                            try {
                                br2.close();
                                fr2.close();
                                return retString6;
                            } catch (IOException e5) {
                                return "File Close error";
                            }
                        } else {
                            retString = "NG";
                        }
                    } else {
                        retString = "NG";
                    }
                    if ("NG".equals(retString)) {
                        try {
                            br2.close();
                            fr2.close();
                            return verString;
                        } catch (IOException e6) {
                            return "File Close error";
                        }
                    } else {
                        try {
                            br2.close();
                            fr2.close();
                            return "error";
                        } catch (IOException e7) {
                            return "File Close error";
                        }
                    }
                } else {
                    try {
                        br2.close();
                        fr2.close();
                        return "file is null .. !";
                    } catch (IOException e8) {
                        return "File Close error";
                    }
                }
            } catch (IOException e9) {
                if (0 != 0) {
                    try {
                        br.close();
                    } catch (IOException e10) {
                        return "File Close error";
                    }
                }
                if (0 != 0) {
                    fr.close();
                }
                return "/data/misc/conn/.wifiver.info doesn't exist or there are something wrong on handling it";
            }
        }

        private String getChipsetName() {
            FileReader fr = null;
            BufferedReader br = null;
            try {
                FileReader fr2 = new FileReader(PATH_OF_WIFIVER_INFO);
                BufferedReader br2 = new BufferedReader(fr2);
                String verString = br2.readLine();
                if (verString == null) {
                    try {
                        br2.close();
                        fr2.close();
                        return "file is null .. !";
                    } catch (IOException e) {
                        return "File Close error";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_BRCM)) {
                    try {
                        br2.close();
                        fr2.close();
                        return "1";
                    } catch (IOException e2) {
                        return "File Close error";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_QCOM)) {
                    try {
                        br2.close();
                        fr2.close();
                        return "2";
                    } catch (IOException e3) {
                        return "File Close error";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_QCA)) {
                    try {
                        br2.close();
                        fr2.close();
                        return "3";
                    } catch (IOException e4) {
                        return "File Close error";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_MAVL)) {
                    try {
                        br2.close();
                        fr2.close();
                        return "4";
                    } catch (IOException e5) {
                        return "File Close error";
                    }
                } else if (verString.contains(WIFI_VER_PREFIX_SPRTRM)) {
                    try {
                        br2.close();
                        fr2.close();
                        return "5";
                    } catch (IOException e6) {
                        return "File Close error";
                    }
                } else if ("NG".equals("NG")) {
                    String str = "Unknown String format..Full string is " + verString;
                    try {
                        br2.close();
                        fr2.close();
                        return str;
                    } catch (IOException e7) {
                        return "File Close error";
                    }
                } else {
                    try {
                        br2.close();
                        fr2.close();
                        return "error";
                    } catch (IOException e8) {
                        return "File Close error";
                    }
                }
            } catch (IOException e9) {
                if (0 != 0) {
                    try {
                        br.close();
                    } catch (IOException e10) {
                        return "File Close error";
                    }
                }
                if (0 != 0) {
                    fr.close();
                }
                return "/data/misc/conn/.wifiver.info doesn't exist or there are something wrong on handling it";
            }
        }

        private String convertToQuotedString(String string) {
            return "\"" + string + "\"";
        }

        private String removeDoubleQuotes(String string) {
            if (string == null) {
                return null;
            }
            int length = string.length();
            if (length > 1 && string.charAt(0) == '\"' && string.charAt(length - 1) == '\"') {
                return string.substring(1, length - 1);
            }
            return string;
        }
    }

    /* access modifiers changed from: private */
    public class P2pStateMachine extends StateMachine {
        private final int P2P_GO_OPER_FREQ = -999;
        private String filterMaskRTCP = "0xffff000000000000000000ff00000000000000000000000000000000000000ff";
        private String filterMaskRTSPDst = "0xffff000000000000000000ff000000000000000000000000ffff";
        private String filterMaskRTSPSrc = "0xffff000000000000000000ff00000000000000000000ffff";
        private String filterMaskSSDP = "0xffff000000000000000000ff000000000000000000000000ffff";
        private String filterOffset = "12";
        private String filterRTCP = "0x08000000000000000000001100000000000000000000000000000000000000c9";
        private String filterRTSPDst = "0x0800000000000000000000060000000000000000000000001c44";
        private String filterRTSPSrc = "0x080000000000000000000006000000000000000000001c44";
        private String filterSSDP = "0x080000000000000000000011000000000000000000000000076c";
        private String mConnectedDevAddr = null;
        private String mConnectedDevIntfAddr = null;
        private DefaultState mDefaultState = new DefaultState();
        private FrequencyConflictState mFrequencyConflictState = new FrequencyConflictState();
        private WifiP2pGroup mGroup;
        private WifiP2pGroup mGroupBackup;
        private GroupCreatedState mGroupCreatedState = new GroupCreatedState();
        private GroupCreatingState mGroupCreatingState = new GroupCreatingState();
        private GroupNegotiationState mGroupNegotiationState = new GroupNegotiationState();
        private final WifiP2pGroupList mGroups = new WifiP2pGroupList((WifiP2pGroupList) null, new WifiP2pGroupList.GroupDeleteListener() {
            /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.C05301 */

            public void onDeleteGroup(int netId) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.logd("called onDeleteGroup() netId=" + netId);
                }
                P2pStateMachine.this.mWifiNative.removeP2pNetwork(netId);
                P2pStateMachine.this.mWifiNative.saveConfig();
                P2pStateMachine.this.sendP2pPersistentGroupsChangedBroadcast();
            }
        });
        private List<String> mHistoricalDumpLogs = new ArrayList();
        private InactiveState mInactiveState = new InactiveState();
        private String mInterfaceName;
        private boolean mIsGotoJoinState = false;
        private boolean mIsHalInterfaceAvailable = false;
        private boolean mIsInactiveState = false;
        private boolean mIsWifiEnabled = false;
        private NfcProvisionState mNfcProvisionState = new NfcProvisionState();
        private OngoingGroupRemovalState mOngoingGroupRemovalState = new OngoingGroupRemovalState();
        private P2pDisabledState mP2pDisabledState = new P2pDisabledState();
        private P2pDisablingState mP2pDisablingState = new P2pDisablingState();
        private P2pEnabledState mP2pEnabledState = new P2pEnabledState();
        private P2pEnablingState mP2pEnablingState = new P2pEnablingState();
        private P2pNotSupportedState mP2pNotSupportedState = new P2pNotSupportedState();
        private int mP2pState = 1;
        private WifiP2pStaticIpConfig mP2pStaticIpConfig;
        private final WifiP2pDeviceList mPeers = new WifiP2pDeviceList();
        private final WifiP2pDeviceList mPeersLostDuringConnection = new WifiP2pDeviceList();
        private ProvisionDiscoveryState mProvisionDiscoveryState = new ProvisionDiscoveryState();
        private boolean mRequestNfcCalled = false;
        private WifiP2pGroup mSavedP2pGroup;
        private WifiP2pConfig mSavedPeerConfig = new WifiP2pConfig();
        private WifiP2pDevice mSavedProvDiscDevice;
        private int mSelectP2pConfigIndex;
        private WifiP2pConfigList mSelectP2pConfigList;
        private String mSelectedP2pGroupAddress;
        private UserAuthorizingInviteRequestState mUserAuthorizingInviteRequestState = new UserAuthorizingInviteRequestState();
        private UserAuthorizingJoinState mUserAuthorizingJoinState = new UserAuthorizingJoinState();
        private UserAuthorizingNegotiationRequestState mUserAuthorizingNegotiationRequestState = new UserAuthorizingNegotiationRequestState();
        private WaitForUserActionState mWaitForUserActionState = new WaitForUserActionState();
        private WaitForWifiDisableState mWaitForWifiDisableState = new WaitForWifiDisableState();
        private String mWifiInterface;
        private WifiNative mWifiLegacyNative = WifiInjector.getInstance().getWifiNative();
        private WifiP2pMonitor mWifiMonitor = WifiP2pServiceImpl.this.mWifiInjector.getWifiP2pMonitor();
        private WifiP2pNative mWifiNative = WifiP2pServiceImpl.this.mWifiInjector.getWifiP2pNative();
        private final WifiP2pInfo mWifiP2pInfo = new WifiP2pInfo();

        static /* synthetic */ int access$6808(P2pStateMachine x0) {
            int i = x0.mSelectP2pConfigIndex;
            x0.mSelectP2pConfigIndex = i + 1;
            return i;
        }

        P2pStateMachine(String name, Looper looper, boolean p2pSupported) {
            super(name, looper);
            addState(this.mDefaultState);
            addState(this.mP2pNotSupportedState, this.mDefaultState);
            addState(this.mP2pDisablingState, this.mDefaultState);
            addState(this.mP2pDisabledState, this.mDefaultState);
            addState(this.mWaitForUserActionState, this.mP2pDisabledState);
            addState(this.mWaitForWifiDisableState, this.mP2pDisabledState);
            addState(this.mP2pEnablingState, this.mDefaultState);
            addState(this.mP2pEnabledState, this.mDefaultState);
            addState(this.mInactiveState, this.mP2pEnabledState);
            addState(this.mNfcProvisionState, this.mInactiveState);
            addState(this.mGroupCreatingState, this.mP2pEnabledState);
            addState(this.mUserAuthorizingInviteRequestState, this.mGroupCreatingState);
            addState(this.mUserAuthorizingNegotiationRequestState, this.mGroupCreatingState);
            addState(this.mProvisionDiscoveryState, this.mGroupCreatingState);
            addState(this.mGroupNegotiationState, this.mGroupCreatingState);
            addState(this.mFrequencyConflictState, this.mGroupCreatingState);
            addState(this.mGroupCreatedState, this.mP2pEnabledState);
            addState(this.mUserAuthorizingJoinState, this.mGroupCreatedState);
            addState(this.mOngoingGroupRemovalState, this.mGroupCreatedState);
            if (p2pSupported) {
                setInitialState(this.mP2pDisabledState);
            } else {
                setInitialState(this.mP2pNotSupportedState);
            }
            setLogRecSize(50);
            setLogOnlyTransitions(true);
            if (p2pSupported) {
                WifiP2pServiceImpl.this.mContext.registerReceiver(new BroadcastReceiver(WifiP2pServiceImpl.this) {
                    /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.C05362 */

                    public void onReceive(Context context, Intent intent) {
                        int wifistate = intent.getIntExtra("wifi_state", 4);
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                            p2pStateMachine.logd("WIFI_STATE_CHANGED_ACTION wifistate : " + wifistate);
                        }
                        if (wifistate == 3) {
                            P2pStateMachine.this.mIsWifiEnabled = true;
                            P2pStateMachine.this.checkAndReEnableP2p();
                        } else if (wifistate == 0) {
                            P2pStateMachine.this.mIsWifiEnabled = false;
                            P2pStateMachine.this.sendMessage(139274);
                            P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.DISABLE_P2P);
                        }
                    }
                }, new IntentFilter("android.net.wifi.WIFI_STATE_CHANGED"));
                WifiP2pServiceImpl.this.mContext.registerReceiver(new BroadcastReceiver(WifiP2pServiceImpl.this) {
                    /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.C05373 */

                    public void onReceive(Context context, Intent intent) {
                        if (!WifiP2pServiceImpl.this.mWifiPermissionsUtil.isLocationModeEnabled()) {
                            P2pStateMachine.this.sendMessage(139268);
                        }
                    }
                }, new IntentFilter("android.location.MODE_CHANGED"));
                this.mWifiNative.registerInterfaceAvailableListener(new HalDeviceManager.InterfaceAvailableForRequestListener() {
                    /* class com.android.server.wifi.p2p.C0523xa4404efc */

                    @Override // com.android.server.wifi.HalDeviceManager.InterfaceAvailableForRequestListener
                    public final void onAvailabilityChanged(boolean z) {
                        WifiP2pServiceImpl.P2pStateMachine.this.lambda$new$0$WifiP2pServiceImpl$P2pStateMachine(z);
                    }
                }, getHandler());
                WifiP2pServiceImpl.this.mFrameworkFacade.registerContentObserver(WifiP2pServiceImpl.this.mContext, Settings.Global.getUriFor("wifi_verbose_logging_enabled"), true, new ContentObserver(new Handler(looper), WifiP2pServiceImpl.this) {
                    /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.C05384 */

                    public void onChange(boolean selfChange) {
                        P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                        p2pStateMachine.enableVerboseLogging(WifiP2pServiceImpl.this.mFrameworkFacade.getIntegerSetting(WifiP2pServiceImpl.this.mContext, "wifi_verbose_logging_enabled", 0));
                    }
                });
            }
        }

        public /* synthetic */ void lambda$new$0$WifiP2pServiceImpl$P2pStateMachine(boolean isAvailable) {
            this.mIsHalInterfaceAvailable = isAvailable;
            if (isAvailable) {
                checkAndReEnableP2p();
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void enableVerboseLogging(int verbose) {
            boolean unused = WifiP2pServiceImpl.mVerboseLoggingEnabled = verbose > 0;
            this.mWifiNative.enableVerboseLogging(verbose);
            this.mWifiMonitor.enableVerboseLogging(verbose);
        }

        public void registerForWifiMonitorEvents() {
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.AP_STA_CONNECTED_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.AP_STA_DISCONNECTED_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_DEVICE_FOUND_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_DEVICE_LOST_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_FIND_STOPPED_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_GO_NEGOTIATION_REQUEST_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_GO_NEGOTIATION_SUCCESS_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_GROUP_FORMATION_SUCCESS_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_GROUP_STARTED_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_INVITATION_RECEIVED_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_INVITATION_RESULT_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_PROV_DISC_PBC_RSP_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_SERV_DISC_RESP_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, 147457, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, 147458, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_NO_COMMON_CHANNEL, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, 147527, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_GOPS_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_WPS_SKIP_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_P2P_SCONNECT_PROBE_REQ_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_PERSISTENT_PSK_FAIL_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_BIGDATA_DISCONNECT_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_BIGDATA_CONNECTION_RESULT_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, WifiP2pMonitor.P2P_BIGDATA_GROUP_OWNER_INTENT_EVENT, getHandler());
            this.mWifiMonitor.registerHandler(this.mInterfaceName, 147499, getHandler());
            this.mWifiMonitor.startMonitoring(this.mInterfaceName);
        }

        class DefaultState extends State {
            DefaultState() {
            }

            public boolean processMessage(Message message) {
                String serviceData;
                String extra;
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    if (message.what == 143375) {
                        P2pStateMachine.this.logd(getName() + message.what);
                    } else {
                        P2pStateMachine.this.logd(getName() + message.toString());
                    }
                }
                WifiP2pConfigList wifiP2pConfigList = null;
                int i = 2;
                switch (message.what) {
                    case 69632:
                        if (message.arg1 != 0) {
                            P2pStateMachine.this.loge("Full connection failure, error = " + message.arg1);
                            WifiP2pServiceImpl.this.mWifiChannel = null;
                            P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                            p2pStateMachine.transitionTo(p2pStateMachine.mP2pDisabledState);
                            break;
                        } else {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd("Full connection with ClientModeImpl established");
                            }
                            WifiP2pServiceImpl.this.mWifiChannel = (AsyncChannel) message.obj;
                            break;
                        }
                    case 69633:
                        new WifiAsyncChannel(WifiP2pServiceImpl.TAG).connect(WifiP2pServiceImpl.this.mContext, P2pStateMachine.this.getHandler(), message.replyTo);
                        break;
                    case 69636:
                        if (message.arg1 == 2) {
                            P2pStateMachine.this.loge("Send failed, client connection lost");
                        } else {
                            P2pStateMachine.this.loge("Client connection lost with reason: " + message.arg1);
                        }
                        WifiP2pServiceImpl.this.mWifiChannel = null;
                        P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                        p2pStateMachine2.transitionTo(p2pStateMachine2.mP2pDisabledState);
                        break;
                    case 139265:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139266, 2);
                        break;
                    case 139268:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139269, 2);
                        break;
                    case 139271:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139272, 2);
                        WifiP2pServiceImpl.this.auditLog(false, "Connecting to device using Wi-Fi Direct (P2P) succeeded");
                        break;
                    case 139274:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139275, 2);
                        WifiP2pServiceImpl.this.auditLog(false, "Connecting to device using Wi-Fi Direct (P2P) cancelled");
                        break;
                    case 139277:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139278, 2);
                        break;
                    case 139280:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139281, 2);
                        break;
                    case 139283:
                        P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                        p2pStateMachine3.replyToMessage((P2pStateMachine) message, (Message) 139284, (int) p2pStateMachine3.getPeers(p2pStateMachine3.getCallingPkgName(message.sendingUid, message.replyTo), message.sendingUid));
                        break;
                    case 139285:
                        P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                        p2pStateMachine4.replyToMessage((P2pStateMachine) message, (Message) 139286, (int) new WifiP2pInfo(p2pStateMachine4.mWifiP2pInfo));
                        break;
                    case 139287:
                        if (WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkCanAccessWifiDirect(P2pStateMachine.this.getCallingPkgName(message.sendingUid, message.replyTo), message.sendingUid, false)) {
                            P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                            p2pStateMachine5.replyToMessage((P2pStateMachine) message, (Message) 139288, (int) p2pStateMachine5.maybeEraseOwnDeviceAddress((P2pStateMachine) p2pStateMachine5.mGroup, (WifiP2pGroup) message.sendingUid));
                            break;
                        } else {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139288, (int) null);
                            break;
                        }
                    case 139292:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139293, 2);
                        break;
                    case 139295:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139296, 2);
                        break;
                    case 139298:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139299, 2);
                        break;
                    case 139301:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139302, 2);
                        break;
                    case 139304:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139305, 2);
                        break;
                    case 139307:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139308, 2);
                        break;
                    case 139310:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139311, 2);
                        break;
                    case 139315:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139316, 2);
                        break;
                    case 139318:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139319, 2);
                        break;
                    case 139321:
                        P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                        p2pStateMachine6.replyToMessage((P2pStateMachine) message, (Message) 139322, (int) new WifiP2pGroupList(p2pStateMachine6.maybeEraseOwnDeviceAddress((P2pStateMachine) p2pStateMachine6.mGroups, (WifiP2pGroupList) message.sendingUid), (WifiP2pGroupList.GroupDeleteListener) null));
                        break;
                    case 139323:
                        if (WifiP2pServiceImpl.this.getWfdPermission(message.sendingUid)) {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139324, 2);
                            break;
                        } else {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139324, 0);
                            break;
                        }
                    case 139326:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139327, 2);
                        break;
                    case 139329:
                    case 139332:
                    case 139335:
                    case WifiP2pServiceImpl.GROUP_CREATING_TIMED_OUT /*{ENCODED_INT: 143361}*/:
                    case WifiP2pServiceImpl.PEER_CONNECTION_USER_ACCEPT /*{ENCODED_INT: 143362}*/:
                    case WifiP2pServiceImpl.PEER_CONNECTION_USER_REJECT /*{ENCODED_INT: 143363}*/:
                    case WifiP2pServiceImpl.DROP_WIFI_USER_ACCEPT /*{ENCODED_INT: 143364}*/:
                    case WifiP2pServiceImpl.DROP_WIFI_USER_REJECT /*{ENCODED_INT: 143365}*/:
                    case WifiP2pServiceImpl.DISABLE_P2P_TIMED_OUT /*{ENCODED_INT: 143366}*/:
                    case WifiP2pServiceImpl.DISCONNECT_WIFI_RESPONSE /*{ENCODED_INT: 143373}*/:
                    case WifiP2pServiceImpl.SET_MIRACAST_MODE /*{ENCODED_INT: 143374}*/:
                    case WifiP2pServiceImpl.SET_COUNTRY_CODE /*{ENCODED_INT: 143379}*/:
                    case WifiP2pServiceImpl.IPC_PRE_DHCP_ACTION /*{ENCODED_INT: 143390}*/:
                    case WifiP2pServiceImpl.IPC_POST_DHCP_ACTION /*{ENCODED_INT: 143391}*/:
                    case WifiP2pServiceImpl.IPC_DHCP_RESULTS /*{ENCODED_INT: 143392}*/:
                    case WifiP2pServiceImpl.IPC_PROVISIONING_SUCCESS /*{ENCODED_INT: 143393}*/:
                    case WifiP2pServiceImpl.IPC_PROVISIONING_FAILURE /*{ENCODED_INT: 143394}*/:
                    case 147457:
                    case 147458:
                    case WifiP2pMonitor.P2P_DEVICE_FOUND_EVENT:
                    case WifiP2pMonitor.P2P_DEVICE_LOST_EVENT:
                    case WifiP2pMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT:
                    case WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT:
                    case WifiP2pMonitor.P2P_FIND_STOPPED_EVENT:
                    case WifiP2pMonitor.P2P_SERV_DISC_RESP_EVENT:
                    case WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT:
                        break;
                    case 139339:
                    case 139340:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139341, (int) null);
                        break;
                    case 139342:
                    case 139343:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139345, 2);
                        break;
                    case 139346:
                        if (!P2pStateMachine.this.factoryReset(message.sendingUid)) {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139347, 0);
                            break;
                        } else {
                            P2pStateMachine.this.replyToMessage(message, 139348);
                            break;
                        }
                    case 139349:
                        if (!WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkNetworkStackPermission(message.sendingUid)) {
                            P2pStateMachine.this.loge("Permission violation - no NETWORK_STACK permission, uid = " + message.sendingUid);
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139350, (int) null);
                            break;
                        } else {
                            P2pStateMachine p2pStateMachine7 = P2pStateMachine.this;
                            p2pStateMachine7.replyToMessage((P2pStateMachine) message, (Message) 139350, (int) p2pStateMachine7.mSavedPeerConfig);
                            break;
                        }
                    case 139351:
                        if (!WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkNetworkStackPermission(message.sendingUid)) {
                            P2pStateMachine.this.loge("Permission violation - no NETWORK_STACK permission, uid = " + message.sendingUid);
                            P2pStateMachine.this.replyToMessage(message, 139352);
                            break;
                        } else {
                            WifiP2pConfig peerConfig = (WifiP2pConfig) message.obj;
                            if (!P2pStateMachine.this.isConfigInvalid(peerConfig)) {
                                P2pStateMachine.this.logd("setSavedPeerConfig to " + peerConfig);
                                P2pStateMachine.this.mSavedPeerConfig = peerConfig;
                                P2pStateMachine.this.replyToMessage(message, 139353);
                                break;
                            } else {
                                P2pStateMachine.this.loge("Dropping set mSavedPeerConfig requeset" + peerConfig);
                                P2pStateMachine.this.replyToMessage(message, 139352);
                                break;
                            }
                        }
                    case 139354:
                        P2pStateMachine p2pStateMachine8 = P2pStateMachine.this;
                        if (!p2pStateMachine8.mIsWifiEnabled || !P2pStateMachine.this.isHalInterfaceAvailable() || !WifiP2pServiceImpl.this.mWifiPermissionsUtil.isLocationModeEnabled()) {
                            i = 1;
                        }
                        p2pStateMachine8.replyToMessage((P2pStateMachine) message, (Message) 139355, i);
                        break;
                    case 139356:
                        P2pStateMachine p2pStateMachine9 = P2pStateMachine.this;
                        if (!WifiP2pServiceImpl.this.mDiscoveryStarted) {
                            i = 1;
                        }
                        p2pStateMachine9.replyToMessage((P2pStateMachine) message, (Message) 139357, i);
                        break;
                    case 139358:
                        P2pStateMachine p2pStateMachine10 = P2pStateMachine.this;
                        p2pStateMachine10.replyToMessage((P2pStateMachine) message, (Message) 139359, (int) WifiP2pServiceImpl.this.mNetworkInfo);
                        break;
                    case 139360:
                        if (message.obj instanceof Bundle) {
                            Bundle bundle = (Bundle) message.obj;
                            String pkgName = bundle.getString("android.net.wifi.p2p.CALLING_PACKAGE");
                            IBinder binder = bundle.getBinder("android.net.wifi.p2p.CALLING_BINDER");
                            try {
                                WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkPackage(message.sendingUid, pkgName);
                                if (!(binder == null || message.replyTo == null)) {
                                    WifiP2pServiceImpl.this.mClientChannelList.put(binder, message.replyTo);
                                    P2pStateMachine.this.getClientInfo(message.replyTo, true).mPackageName = pkgName;
                                    break;
                                }
                            } catch (SecurityException se) {
                                P2pStateMachine.this.loge("Unable to update calling package, " + se);
                                break;
                            }
                        }
                        break;
                    case 139361:
                        if (WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkCanAccessWifiDirect(P2pStateMachine.this.getCallingPkgName(message.sendingUid, message.replyTo), message.sendingUid, false)) {
                            P2pStateMachine p2pStateMachine11 = P2pStateMachine.this;
                            p2pStateMachine11.replyToMessage((P2pStateMachine) message, (Message) 139362, (int) p2pStateMachine11.maybeEraseOwnDeviceAddress((P2pStateMachine) WifiP2pServiceImpl.this.mThisDevice, (WifiP2pDevice) message.sendingUid));
                            break;
                        } else {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139362, (int) null);
                            break;
                        }
                    case 139378:
                        P2pStateMachine p2pStateMachine12 = P2pStateMachine.this;
                        if (p2pStateMachine12.mSelectP2pConfigList != null) {
                            wifiP2pConfigList = new WifiP2pConfigList(P2pStateMachine.this.mSelectP2pConfigList);
                        }
                        p2pStateMachine12.replyToMessage((P2pStateMachine) message, (Message) 139379, (int) wifiP2pConfigList);
                        break;
                    case 139380:
                        if (!P2pStateMachine.this.setDialogListenerApp(message.replyTo, message.getData().getString("appPkgName"), message.getData().getBoolean("dialogResetFlag"))) {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139381, 4);
                            break;
                        } else {
                            P2pStateMachine.this.replyToMessage(message, 139382);
                            break;
                        }
                    case 139408:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139409, 2);
                        break;
                    case 139412:
                        if (message.obj != null) {
                            P2pStateMachine.this.addHistoricalDumpLog(((Bundle) message.obj).getString("extra_log"));
                            break;
                        }
                        break;
                    case 139419:
                        if (!P2pStateMachine.this.mWifiNative.p2pSet("screen_sharing", message.arg1 == 1 ? "1" : "0")) {
                            P2pStateMachine.this.loge("Failed to set screen sharing");
                            break;
                        }
                        break;
                    case 139420:
                        int serviceId = message.arg1;
                        String serviceData2 = message.getData().getString("SDATA");
                        if (serviceData2 != null && serviceData2.length() > 0) {
                            serviceData = serviceId + " " + serviceData2;
                        } else if (serviceId <= 0 || serviceId >= 256) {
                            P2pStateMachine.this.loge("Failed to set service_data (invalid id)");
                            break;
                        } else {
                            serviceData = Integer.toString(serviceId);
                        }
                        if (!P2pStateMachine.this.mWifiNative.p2pSet("service_data", serviceData)) {
                            P2pStateMachine.this.loge("Failed to set service_data");
                            break;
                        }
                        break;
                    case WifiP2pServiceImpl.BLOCK_DISCOVERY /*{ENCODED_INT: 143375}*/:
                        WifiP2pServiceImpl.this.mDiscoveryBlocked = message.arg1 == 1;
                        WifiP2pServiceImpl.this.mDiscoveryPostponed = false;
                        if (WifiP2pServiceImpl.this.mDiscoveryBlocked) {
                            if (message.obj != null) {
                                try {
                                    ((StateMachine) message.obj).sendMessage(message.arg2);
                                    break;
                                } catch (Exception e) {
                                    P2pStateMachine.this.loge("unable to send BLOCK_DISCOVERY response: " + e);
                                    break;
                                }
                            } else {
                                Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                                break;
                            }
                        }
                        break;
                    case WifiP2pServiceImpl.ENABLE_P2P /*{ENCODED_INT: 143376}*/:
                        WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) enabling succeeded", 1);
                        break;
                    case WifiP2pServiceImpl.DISABLE_P2P /*{ENCODED_INT: 143377}*/:
                        if (WifiP2pServiceImpl.this.mWifiChannel != null) {
                            WifiP2pServiceImpl.this.mWifiChannel.sendMessage((int) WifiP2pServiceImpl.DISABLE_P2P_RSP);
                        } else {
                            P2pStateMachine.this.loge("Unexpected disable request when WifiChannel is null");
                        }
                        WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) disabling succeeded", 1);
                        break;
                    case WifiP2pServiceImpl.CMD_BOOT_COMPLETED /*{ENCODED_INT: 143415}*/:
                        P2pStateMachine.this.checkAndSetConnectivityInstance();
                        if (WifiP2pServiceImpl.this.mThisDevice.deviceAddress == null || WifiP2pServiceImpl.this.mThisDevice.deviceAddress.isEmpty()) {
                            if (!WifiP2pServiceImpl.this.mContext.getResources().getBoolean(17891609)) {
                                if (P2pStateMachine.this.makeP2pHwMac()) {
                                    WifiP2pServiceImpl.this.mThisDevice.deviceAddress = WifiP2pServiceImpl.P2P_HW_MAC_ADDRESS;
                                    P2pStateMachine.this.sendThisDeviceChangedBroadcast();
                                    break;
                                }
                            } else {
                                String unused = WifiP2pServiceImpl.RANDOM_MAC_ADDRESS = WifiP2pServiceImpl.toHexString(WifiP2pServiceImpl.this.createRandomMac());
                                WifiP2pServiceImpl.this.mThisDevice.deviceAddress = WifiP2pServiceImpl.RANDOM_MAC_ADDRESS;
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    Log.i(WifiP2pServiceImpl.TAG, "CMD_BOOT_COMPLETED. MAC will be changed: " + WifiP2pServiceImpl.this.mThisDevice.deviceAddress);
                                }
                                P2pStateMachine.this.sendThisDeviceChangedBroadcast();
                                break;
                            }
                        }
                        break;
                    case WifiP2pServiceImpl.CMD_SEC_LOGGING /*{ENCODED_INT: 143420}*/:
                        if (WifiP2pServiceImpl.this.mIsBootComplete) {
                            Bundle args = (Bundle) message.obj;
                            WifiP2pServiceImpl.this.mBigData.initialize();
                            if (args != null) {
                                if (!args.getBoolean("bigdata", false)) {
                                    P2pStateMachine.this.insertLog(args.getString("feature", null), args.getString("extra", null), args.getLong("value", -1));
                                    break;
                                } else {
                                    String feature = args.getString("feature", null);
                                    if (WifiP2pServiceImpl.this.mBigData.parseData(feature, args.getString("data", null)) && (extra = WifiP2pServiceImpl.this.mBigData.getJsonFormat(feature)) != null) {
                                        P2pStateMachine.this.insertLog(feature, extra, -1);
                                        break;
                                    }
                                }
                            } else {
                                P2pStateMachine.this.loge("CMD_SEC_LOGGING : args null!");
                                break;
                            }
                        }
                        break;
                    case WifiP2pServiceImpl.P2P_ADVOPP_LISTEN_TIMEOUT /*{ENCODED_INT: 143462}*/:
                        WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndListen = false;
                        P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.P2P_ADVOPP_LISTEN_TIMEOUT);
                        break;
                    case WifiP2pMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT:
                        if (WifiP2pServiceImpl.this.userRejected) {
                            P2pStateMachine.this.sendP2pRequestChangedBroadcast(false);
                            WifiP2pServiceImpl.this.userRejected = false;
                            break;
                        }
                        break;
                    case WifiP2pMonitor.P2P_GROUP_STARTED_EVENT:
                        if (message.obj != null) {
                            P2pStateMachine.this.mGroup = (WifiP2pGroup) message.obj;
                            P2pStateMachine.this.loge("Unexpected group creation, remove " + P2pStateMachine.this.mGroup);
                            P2pStateMachine.this.mWifiNative.p2pGroupRemove(P2pStateMachine.this.mGroup.getInterface());
                            break;
                        } else {
                            Log.e(WifiP2pServiceImpl.TAG, "Illegal arguments");
                            break;
                        }
                    case WifiP2pMonitor.P2P_INVITATION_RESULT_EVENT:
                        P2pStatus status = (P2pStatus) message.obj;
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("P2P_INVITATION_RESULT_EVENT : " + status);
                        }
                        if (status == P2pStatus.NO_COMMON_CHANNEL) {
                            if (P2pStateMachine.this.mSelectP2pConfigList != null) {
                                if (P2pStateMachine.this.mSelectP2pConfigList.getConfigIndex(P2pStateMachine.this.mSelectP2pConfigIndex) != null) {
                                    P2pStateMachine p2pStateMachine13 = P2pStateMachine.this;
                                    p2pStateMachine13.mConnectedDevAddr = p2pStateMachine13.mSelectP2pConfigList.getConfigIndex(P2pStateMachine.this.mSelectP2pConfigIndex).deviceAddress;
                                    P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mConnectedDevAddr, 2);
                                    P2pStateMachine.this.sendPeersChangedBroadcast();
                                    P2pStateMachine.access$6808(P2pStateMachine.this);
                                    P2pStateMachine p2pStateMachine14 = P2pStateMachine.this;
                                    p2pStateMachine14.sendMessageDelayed(p2pStateMachine14.obtainMessage(WifiP2pServiceImpl.INVITATION_PROCEDURE_TIMED_OUT, p2pStateMachine14.mSelectP2pConfigIndex, 0), 30000);
                                    P2pStateMachine.this.sendMessage(139271);
                                    break;
                                } else {
                                    P2pStateMachine.this.mSelectP2pConfigList = null;
                                    P2pStateMachine.this.mSelectP2pConfigIndex = 0;
                                    P2pStateMachine.this.stopLegacyWifiScan(false);
                                    break;
                                }
                            } else {
                                P2pStateMachine p2pStateMachine15 = P2pStateMachine.this;
                                p2pStateMachine15.transitionTo(p2pStateMachine15.mFrequencyConflictState);
                                break;
                            }
                        }
                        break;
                    case WifiP2pMonitor.P2P_NO_COMMON_CHANNEL:
                        if (P2pStateMachine.this.mSavedPeerConfig == null) {
                            P2pStateMachine.this.mSavedPeerConfig = new WifiP2pConfig();
                            P2pStateMachine.this.mSavedPeerConfig.deviceAddress = (String) message.obj;
                            WifiP2pDevice dev = P2pStateMachine.this.mPeers.get(P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                            if (dev != null) {
                                if (dev.wpsPbcSupported()) {
                                    P2pStateMachine.this.mSavedPeerConfig.wps.setup = 0;
                                } else if (dev.wpsKeypadSupported()) {
                                    P2pStateMachine.this.mSavedPeerConfig.wps.setup = 2;
                                } else if (dev.wpsDisplaySupported()) {
                                    P2pStateMachine.this.mSavedPeerConfig.wps.setup = 1;
                                }
                            }
                            P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mSavedPeerConfig.deviceAddress, 1);
                        }
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("P2P_NO_COMMON_CHANNEL : " + P2pStateMachine.this.mSavedPeerConfig.deviceAddress + " WPS = " + P2pStateMachine.this.mSavedPeerConfig.wps.setup);
                        }
                        P2pStateMachine p2pStateMachine16 = P2pStateMachine.this;
                        p2pStateMachine16.transitionTo(p2pStateMachine16.mFrequencyConflictState);
                        break;
                    case WifiP2pMonitor.P2P_BIGDATA_DISCONNECT_EVENT:
                    case WifiP2pMonitor.P2P_BIGDATA_CONNECTION_RESULT_EVENT:
                        String data = P2pStateMachine.this.buildLoggingData(message.what, (String) message.obj);
                        if (data != null) {
                            Bundle args2 = new Bundle();
                            args2.putBoolean("bigdata", true);
                            if (message.what == 147536) {
                                args2.putString("feature", "WDCL");
                            } else if (message.what == 147537) {
                                WifiP2pServiceImpl.this.mConnReqInfo.reset();
                                args2.putString("feature", "WDGF");
                            }
                            args2.putString("data", data);
                            P2pStateMachine p2pStateMachine17 = P2pStateMachine.this;
                            p2pStateMachine17.sendMessage(p2pStateMachine17.obtainMessage(WifiP2pServiceImpl.CMD_SEC_LOGGING, 0, 0, args2));
                            break;
                        }
                        break;
                    case WifiP2pMonitor.P2P_BIGDATA_GROUP_OWNER_INTENT_EVENT:
                        P2pStateMachine.this.buildLoggingData(message.what, (String) message.obj);
                        break;
                    default:
                        P2pStateMachine.this.loge("Unhandled message " + message);
                        return false;
                }
                return true;
            }
        }

        class P2pNotSupportedState extends State {
            P2pNotSupportedState() {
            }

            public boolean processMessage(Message message) {
                switch (message.what) {
                    case 139265:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139266, 1);
                        break;
                    case 139268:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139269, 1);
                        break;
                    case 139271:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139272, 1);
                        WifiP2pServiceImpl.this.auditLog(false, "Connecting to device using Wi-Fi Direct (P2P) failed");
                        break;
                    case 139274:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139275, 1);
                        break;
                    case 139277:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139278, 1);
                        break;
                    case 139280:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139281, 1);
                        break;
                    case 139292:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139293, 1);
                        break;
                    case 139295:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139296, 1);
                        break;
                    case 139298:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139299, 1);
                        break;
                    case 139301:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139302, 1);
                        break;
                    case 139304:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139305, 1);
                        break;
                    case 139307:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139308, 1);
                        break;
                    case 139310:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139311, 1);
                        break;
                    case 139315:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139316, 1);
                        break;
                    case 139318:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139319, 1);
                        break;
                    case 139323:
                        if (WifiP2pServiceImpl.this.getWfdPermission(message.sendingUid)) {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139324, 1);
                            break;
                        } else {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139324, 0);
                            break;
                        }
                    case 139326:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139327, 1);
                        break;
                    case 139329:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139330, 1);
                        break;
                    case 139332:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139333, 1);
                        break;
                    case 139346:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139347, 1);
                        break;
                    case 139380:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139381, 1);
                        break;
                    case 139408:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139409, 1);
                        break;
                    default:
                        return false;
                }
                return true;
            }
        }

        /* access modifiers changed from: package-private */
        public class P2pDisablingState extends State {
            P2pDisablingState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                p2pStateMachine.sendMessageDelayed(p2pStateMachine.obtainMessage(WifiP2pServiceImpl.DISABLE_P2P_TIMED_OUT, WifiP2pServiceImpl.access$8604(), 0), RttServiceImpl.HAL_RANGING_TIMEOUT_MS);
            }

            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.logd(getName() + message.toString());
                }
                switch (message.what) {
                    case 139365:
                    case 139368:
                        P2pStateMachine.this.deferMessage(message);
                        WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) disabling succeeded", 1);
                        break;
                    case WifiP2pServiceImpl.DISABLE_P2P_TIMED_OUT /*{ENCODED_INT: 143366}*/:
                        if (WifiP2pServiceImpl.sDisableP2pTimeoutIndex != message.arg1) {
                            WifiP2pServiceImpl.this.auditLog(false, "Wi-Fi Direct (P2P) disabling failed", 1);
                            break;
                        } else {
                            P2pStateMachine.this.loge("P2p disable timed out");
                            P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                            p2pStateMachine2.transitionTo(p2pStateMachine2.mP2pDisabledState);
                            WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) disabling succeeded", 1);
                            break;
                        }
                    case WifiP2pServiceImpl.ENABLE_P2P /*{ENCODED_INT: 143376}*/:
                    case WifiP2pServiceImpl.DISABLE_P2P /*{ENCODED_INT: 143377}*/:
                    case WifiP2pServiceImpl.REMOVE_CLIENT_INFO /*{ENCODED_INT: 143378}*/:
                        P2pStateMachine.this.deferMessage(message);
                        break;
                    case 147458:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("p2p socket connection lost");
                        }
                        P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                        p2pStateMachine3.transitionTo(p2pStateMachine3.mP2pDisabledState);
                        WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) disabling succeeded", 1);
                        break;
                    default:
                        return false;
                }
                return true;
            }

            public void exit() {
                if (WifiP2pServiceImpl.this.mWifiChannel != null) {
                    WifiP2pServiceImpl.this.mWifiChannel.sendMessage((int) WifiP2pServiceImpl.DISABLE_P2P_RSP);
                    P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.DISABLE_P2P_TIMED_OUT);
                    return;
                }
                P2pStateMachine.this.loge("DISABLE_P2P_SUCCEEDED(): WifiChannel is null");
            }
        }

        /* access modifiers changed from: package-private */
        public class P2pDisabledState extends State {
            P2pDisabledState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                if (WifiP2pServiceImpl.this.mContext.getResources().getBoolean(17891609)) {
                    if (WifiP2pServiceImpl.this.mThisDevice.deviceAddress != null && !WifiP2pServiceImpl.this.mThisDevice.deviceAddress.isEmpty()) {
                        String unused = WifiP2pServiceImpl.RANDOM_MAC_ADDRESS = WifiP2pServiceImpl.toHexString(WifiP2pServiceImpl.this.createRandomMac());
                        WifiP2pServiceImpl.this.mThisDevice.deviceAddress = WifiP2pServiceImpl.RANDOM_MAC_ADDRESS;
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            Log.i(WifiP2pServiceImpl.TAG, "P2pDisabledState MAC will be changed: " + WifiP2pServiceImpl.this.mThisDevice.deviceAddress);
                        }
                    }
                } else if ((WifiP2pServiceImpl.this.mThisDevice.deviceAddress == null || WifiP2pServiceImpl.this.mThisDevice.deviceAddress.isEmpty()) && P2pStateMachine.this.makeP2pHwMac()) {
                    WifiP2pServiceImpl.this.mThisDevice.deviceAddress = WifiP2pServiceImpl.P2P_HW_MAC_ADDRESS;
                    P2pStateMachine.this.sendThisDeviceChangedBroadcast();
                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                        Log.i(WifiP2pServiceImpl.TAG, "P2pDisabledState MAC will be changed: " + WifiP2pServiceImpl.this.mThisDevice.deviceAddress);
                    }
                }
            }

            private void setupInterfaceFeatures(String interfaceName) {
                if (WifiP2pServiceImpl.this.mContext.getResources().getBoolean(17891609)) {
                    Log.i(WifiP2pServiceImpl.TAG, "Supported feature: P2P MAC randomization");
                    P2pStateMachine.this.mWifiNative.p2pSet("random_mac", WifiP2pServiceImpl.RANDOM_MAC_ADDRESS);
                    P2pStateMachine.this.mWifiNative.setMacRandomization(true);
                    return;
                }
                Log.i(WifiP2pServiceImpl.TAG, "Unsupported feature: P2P MAC randomization");
                P2pStateMachine.this.mWifiNative.setMacRandomization(false);
            }

            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.logd(getName() + message.toString());
                }
                switch (message.what) {
                    case 139265:
                    case 139371:
                        Context context = WifiP2pServiceImpl.this.mContext;
                        Context unused = WifiP2pServiceImpl.this.mContext;
                        WifiManager tWifiManager = (WifiManager) context.getSystemService("wifi");
                        if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing() && !P2pStateMachine.this.isForegroundApp(WifiP2pServiceImpl.WIFI_DIRECT_SETTINGS_PKGNAME) && tWifiManager != null && (tWifiManager.isWifiApEnabled() || tWifiManager.getWifiApState() == 12 || (!tWifiManager.isWifiApEnabled() && tWifiManager.getWifiApState() != 12 && tWifiManager.isWifiEnabled()))) {
                            WifiP2pServiceImpl.this.mDelayedDiscoverPeersCmd = message.what;
                            WifiP2pServiceImpl.this.mDelayedDiscoverPeersArg = message.arg1;
                            WifiP2pServiceImpl.this.mDelayedDiscoverPeers = true;
                            P2pStateMachine.this.sendMessage(139365);
                            break;
                        }
                    case 139365:
                        Bundle bundle = message.getData();
                        if (bundle != null) {
                            WifiP2pServiceImpl.this.allowForcingEnableP2pForApp(bundle.getString("appPkgName"));
                        }
                        Context context2 = WifiP2pServiceImpl.this.mContext;
                        Context unused2 = WifiP2pServiceImpl.this.mContext;
                        WifiManager tWifiManager2 = (WifiManager) context2.getSystemService("wifi");
                        if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing() && tWifiManager2 != null && (tWifiManager2.isWifiApEnabled() || tWifiManager2.getWifiApState() == 12)) {
                            WifiP2pServiceImpl.this.checkAndShowP2pEnableDialog(0);
                        } else if (WifiP2pServiceImpl.this.mWifiAwareManager != null && WifiP2pServiceImpl.this.mWifiAwareManager.isEnabled() && !WifiP2pServiceImpl.this.mReceivedEnableP2p) {
                            WifiP2pServiceImpl.this.checkAndShowP2pEnableDialog(1);
                        } else if (!WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing() || tWifiManager2 == null || tWifiManager2.isWifiApEnabled() || tWifiManager2.getWifiApState() == 12 || !tWifiManager2.isWifiEnabled()) {
                            P2pStateMachine.this.setLegacyWifiEnable(true);
                        } else {
                            P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.ENABLE_P2P);
                        }
                        WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) disabling succeeded", 1);
                        break;
                    case 139368:
                        P2pStateMachine.this.replyToMessage(message, 139369);
                        WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) disabling succeeded", 1);
                        break;
                    case WifiP2pServiceImpl.ENABLE_P2P /*{ENCODED_INT: 143376}*/:
                        if (WifiP2pServiceImpl.this.mThisDevice.deviceAddress == null || WifiP2pServiceImpl.this.mThisDevice.deviceAddress.isEmpty()) {
                            if (WifiP2pServiceImpl.this.mContext.getResources().getBoolean(17891609)) {
                                String unused3 = WifiP2pServiceImpl.RANDOM_MAC_ADDRESS = WifiP2pServiceImpl.toHexString(WifiP2pServiceImpl.this.createRandomMac());
                                WifiP2pServiceImpl.this.mThisDevice.deviceAddress = WifiP2pServiceImpl.RANDOM_MAC_ADDRESS;
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    Log.i(WifiP2pServiceImpl.TAG, "ENABLE_P2P MAC will be changed: " + WifiP2pServiceImpl.this.mThisDevice.deviceAddress);
                                }
                            } else if (P2pStateMachine.this.makeP2pHwMac()) {
                                WifiP2pServiceImpl.this.mThisDevice.deviceAddress = WifiP2pServiceImpl.P2P_HW_MAC_ADDRESS;
                                P2pStateMachine.this.sendThisDeviceChangedBroadcast();
                            }
                        }
                        if (P2pStateMachine.this.mIsWifiEnabled) {
                            Context context3 = WifiP2pServiceImpl.this.mContext;
                            Context unused4 = WifiP2pServiceImpl.this.mContext;
                            WifiManager tWifiManager3 = (WifiManager) context3.getSystemService("wifi");
                            if (!WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing() || tWifiManager3 == null || (!tWifiManager3.isWifiApEnabled() && tWifiManager3.getWifiApState() != 12)) {
                                if (WifiP2pServiceImpl.this.mWifiAwareManager == null || !WifiP2pServiceImpl.this.mWifiAwareManager.isEnabled() || WifiP2pServiceImpl.this.mReceivedEnableP2p) {
                                    WifiP2pServiceImpl.this.mSetupInterfaceIsRunnging = true;
                                    P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                                    p2pStateMachine2.mInterfaceName = p2pStateMachine2.mWifiNative.setupInterface(new HalDeviceManager.InterfaceDestroyedListener() {
                                        /* class com.android.server.wifi.p2p.C0522x41329369 */

                                        @Override // com.android.server.wifi.HalDeviceManager.InterfaceDestroyedListener
                                        public final void onDestroyed(String str) {
                                            WifiP2pServiceImpl.P2pStateMachine.P2pDisabledState.this.mo5743xa9b01d2c(str);
                                        }
                                    }, P2pStateMachine.this.getHandler());
                                    WifiP2pServiceImpl.this.mSetupInterfaceIsRunnging = false;
                                    if (P2pStateMachine.this.mInterfaceName == null) {
                                        Log.e(WifiP2pServiceImpl.TAG, "Failed to setup interface for P2P");
                                        break;
                                    } else {
                                        setupInterfaceFeatures(P2pStateMachine.this.mInterfaceName);
                                        try {
                                            WifiP2pServiceImpl.this.mNwService.setInterfaceUp(P2pStateMachine.this.mInterfaceName);
                                        } catch (RemoteException re) {
                                            P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                                            p2pStateMachine3.loge("Unable to change interface settings: " + re);
                                        } catch (IllegalStateException ie) {
                                            P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                                            p2pStateMachine4.loge("Unable to change interface settings: " + ie);
                                        }
                                        P2pStateMachine.this.registerForWifiMonitorEvents();
                                        P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                                        p2pStateMachine5.transitionTo(p2pStateMachine5.mP2pEnablingState);
                                    }
                                } else if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    P2pStateMachine.this.logd("when Wi-Fi Aware is on, p2p cannot be enabled. so do nothing.");
                                }
                            } else if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd("when mobilehotspot is on, p2p cannot be enabled. so do nothing.");
                            }
                            WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) disabling succeeded", 1);
                            break;
                        } else {
                            Log.e(WifiP2pServiceImpl.TAG, "Ignore P2P enable since wifi is " + P2pStateMachine.this.mIsWifiEnabled);
                            break;
                        }
                    case WifiP2pServiceImpl.REMOVE_CLIENT_INFO /*{ENCODED_INT: 143378}*/:
                        if (message.obj instanceof IBinder) {
                            Map map = WifiP2pServiceImpl.this.mClientChannelList;
                            ClientInfo clientInfo = (ClientInfo) WifiP2pServiceImpl.this.mClientInfoList.remove((Messenger) map.remove((IBinder) message.obj));
                            if (clientInfo != null) {
                                P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                                p2pStateMachine6.logd("Remove client - " + clientInfo.mPackageName);
                                break;
                            }
                        } else {
                            P2pStateMachine.this.loge("Invalid obj when REMOVE_CLIENT_INFO");
                            break;
                        }
                        break;
                    default:
                        return false;
                }
                return true;
            }

            /* renamed from: lambda$processMessage$0$WifiP2pServiceImpl$P2pStateMachine$P2pDisabledState */
            public /* synthetic */ void mo5743xa9b01d2c(String ifaceName) {
                P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.DISABLE_P2P);
            }
        }

        class WaitForUserActionState extends State {
            WaitForUserActionState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
            }

            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.logd(getName() + "{ what=" + message.what + " }");
                }
                switch (message.what) {
                    case WifiP2pServiceImpl.DROP_WIFI_USER_ACCEPT /*{ENCODED_INT: 143364}*/:
                        Context context = WifiP2pServiceImpl.this.mContext;
                        Context unused = WifiP2pServiceImpl.this.mContext;
                        WifiManager tWifiManager = (WifiManager) context.getSystemService("wifi");
                        if (tWifiManager != null) {
                            if (WifiP2pServiceImpl.this.mWifiApState == 13 || WifiP2pServiceImpl.this.mWifiApState == 12) {
                                tWifiManager.semSetWifiApEnabled(null, false);
                            } else {
                                tWifiManager.setWifiEnabled(false);
                            }
                        }
                        WifiP2pServiceImpl.this.mWifiChannel.sendMessage((int) WifiP2pServiceImpl.P2P_ENABLE_PENDING);
                        P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                        p2pStateMachine2.transitionTo(p2pStateMachine2.mWaitForWifiDisableState);
                        return true;
                    case WifiP2pServiceImpl.DROP_WIFI_USER_REJECT /*{ENCODED_INT: 143365}*/:
                        P2pStateMachine.this.logd("User rejected enabling p2p");
                        P2pStateMachine.this.sendP2pStateChangedBroadcast(false);
                        P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                        p2pStateMachine3.transitionTo(p2pStateMachine3.mP2pDisabledState);
                        return true;
                    default:
                        return false;
                }
            }
        }

        /* access modifiers changed from: package-private */
        public class WaitForWifiDisableState extends State {
            WaitForWifiDisableState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
            }

            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.logd(getName() + "{ what=" + message.what + " }");
                }
                int i = message.what;
                if (i != 139365 && i != 139368) {
                    return false;
                }
                P2pStateMachine.this.deferMessage(message);
                return true;
            }
        }

        /* access modifiers changed from: package-private */
        public class P2pEnablingState extends State {
            P2pEnablingState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
            }

            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.logd(getName() + message.toString());
                }
                switch (message.what) {
                    case 139365:
                    case 139368:
                        WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) enabling succeeded", 1);
                        P2pStateMachine.this.deferMessage(message);
                        break;
                    case 147457:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("P2p socket connection successful");
                        }
                        P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                        p2pStateMachine2.transitionTo(p2pStateMachine2.mInactiveState);
                        break;
                    case 147458:
                        P2pStateMachine.this.loge("P2p socket connection failed");
                        P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                        p2pStateMachine3.transitionTo(p2pStateMachine3.mP2pDisabledState);
                        break;
                    default:
                        return false;
                }
                return true;
            }
        }

        class P2pEnabledState extends State {
            P2pEnabledState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                P2pStateMachine.this.mP2pState = 2;
                P2pStateMachine.this.sendP2pStateChangedBroadcast(true);
                WifiP2pServiceImpl.this.mNetworkInfo.setIsAvailable(true);
                if (P2pStateMachine.this.isPendingFactoryReset()) {
                    P2pStateMachine.this.factoryReset(1000);
                }
                P2pStateMachine.this.sendP2pConnectionChangedBroadcast();
                P2pStateMachine.this.initializeP2pSettings();
                WifiP2pServiceImpl.this.mReceivedEnableP2p = false;
                if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing() && WifiP2pServiceImpl.this.mDelayedDiscoverPeers) {
                    if (WifiP2pServiceImpl.this.mDelayedDiscoverPeersCmd == 139265 || WifiP2pServiceImpl.this.mDelayedDiscoverPeersCmd == 139371) {
                        P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                        p2pStateMachine.sendMessage(WifiP2pServiceImpl.this.mDelayedDiscoverPeersCmd, WifiP2pServiceImpl.this.mDelayedDiscoverPeersArg);
                    }
                    WifiP2pServiceImpl.this.mDelayedDiscoverPeers = false;
                    WifiP2pServiceImpl.this.mDelayedDiscoverPeersCmd = -1;
                    WifiP2pServiceImpl.this.mDelayedDiscoverPeersArg = -1;
                }
            }

            /* JADX INFO: Multiple debug info for r0v116 int: [D('build_req_str' java.lang.String), D('isTimerOn' int)] */
            /* JADX INFO: Multiple debug info for r0v142 int: [D('listenOffloadingTimer' int), D('p2pLOParams' android.os.Bundle)] */
            public boolean processMessage(Message message) {
                int p2pFreq;
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    if (message.what == 143375) {
                        P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                        p2pStateMachine.logd(getName() + message.what);
                    } else {
                        P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                        p2pStateMachine2.logd(getName() + message.toString());
                    }
                }
                switch (message.what) {
                    case 139265:
                        String pkgName = message.getData().getString("appPkgName");
                        int channelNum = message.arg1;
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                            p2pStateMachine3.logd(getName() + " package to call DISCOVER_PEERS (channel : " + channelNum + ") -> " + pkgName);
                        }
                        if (WifiP2pServiceImpl.this.mAdvancedOppSender || WifiP2pServiceImpl.this.mAdvancedOppReceiver || WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkCanAccessWifiDirect(P2pStateMachine.this.getCallingPkgName(message.sendingUid, message.replyTo), message.sendingUid, true)) {
                            if ("com.android.bluetooth".equals(pkgName)) {
                                WifiP2pServiceImpl.this.mAdvancedOppReceiver = true;
                                WifiP2pServiceImpl.this.mAdvancedOppInProgress = true;
                                P2pStateMachine.this.sendMessage(139277);
                                break;
                            } else {
                                P2pStateMachine.this.stopLegacyWifiScan(true);
                                if (WifiP2pServiceImpl.this.mDiscoveryBlocked) {
                                    WifiP2pServiceImpl.this.mDiscoveryPostponed = true;
                                    P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139266, 2);
                                    WifiP2pServiceImpl.this.auditLog(false, "Wi-Fi Direct (P2P) enabling failed", 1);
                                    break;
                                } else {
                                    P2pStateMachine.this.clearSupplicantServiceRequest();
                                    if (channelNum > 0 && channelNum != WifiP2pServiceImpl.P2P_LISTEN_OFFLOADING_CHAN_NUM) {
                                        if (P2pStateMachine.this.mPeers.clear()) {
                                            P2pStateMachine.this.sendPeersChangedBroadcast();
                                        }
                                        P2pStateMachine.this.mWifiNative.p2pFlush();
                                    }
                                    if (channelNum == WifiP2pServiceImpl.P2P_LISTEN_OFFLOADING_CHAN_NUM) {
                                        P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                                        p2pStateMachine4.logd(getName() + " Start discovery for next listen offloading ");
                                        P2pStateMachine.this.mWifiNative.p2pFind(1, channelNum);
                                    } else {
                                        P2pStateMachine.this.mWifiNative.p2pFind(0, channelNum);
                                    }
                                    P2pStateMachine.this.replyToMessage(message, 139267);
                                    P2pStateMachine.this.sendP2pDiscoveryChangedBroadcast(true);
                                    WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) enabling succeeded", 1);
                                    break;
                                }
                            }
                        } else {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139266, 0);
                            break;
                        }
                        break;
                    case 139268:
                        if (WifiP2pServiceImpl.this.mDiscoveryBlocked) {
                            WifiP2pServiceImpl.this.mDiscoveryPostponed = false;
                        }
                        P2pStateMachine.this.stopLegacyWifiScan(false);
                        if (P2pStateMachine.this.mWifiNative.p2pStopFind()) {
                            P2pStateMachine.this.replyToMessage(message, 139270);
                            break;
                        } else {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139269, 0);
                            break;
                        }
                    case 139292:
                        if (!WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkCanAccessWifiDirect(P2pStateMachine.this.getCallingPkgName(message.sendingUid, message.replyTo), message.sendingUid, false)) {
                            P2pStateMachine.this.replyToMessage(message, 139293);
                            break;
                        } else {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                                p2pStateMachine5.logd(getName() + " add service");
                            }
                            if (P2pStateMachine.this.addLocalService(message.replyTo, (WifiP2pServiceInfo) message.obj)) {
                                P2pStateMachine.this.replyToMessage(message, 139294);
                                break;
                            } else {
                                P2pStateMachine.this.replyToMessage(message, 139293);
                                break;
                            }
                        }
                    case 139295:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                            p2pStateMachine6.logd(getName() + " remove service");
                        }
                        P2pStateMachine.this.removeLocalService(message.replyTo, (WifiP2pServiceInfo) message.obj);
                        P2pStateMachine.this.replyToMessage(message, 139297);
                        break;
                    case 139298:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine7 = P2pStateMachine.this;
                            p2pStateMachine7.logd(getName() + " clear service");
                        }
                        P2pStateMachine.this.clearLocalServices(message.replyTo);
                        P2pStateMachine.this.replyToMessage(message, 139300);
                        break;
                    case 139301:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine8 = P2pStateMachine.this;
                            p2pStateMachine8.logd(getName() + " add service request");
                        }
                        if (!P2pStateMachine.this.addServiceRequest(message.replyTo, (WifiP2pServiceRequest) message.obj)) {
                            P2pStateMachine.this.replyToMessage(message, 139302);
                            break;
                        } else {
                            P2pStateMachine.this.replyToMessage(message, 139303);
                            break;
                        }
                    case 139304:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine9 = P2pStateMachine.this;
                            p2pStateMachine9.logd(getName() + " remove service request");
                        }
                        P2pStateMachine.this.removeServiceRequest(message.replyTo, (WifiP2pServiceRequest) message.obj);
                        P2pStateMachine.this.replyToMessage(message, 139306);
                        break;
                    case 139307:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine10 = P2pStateMachine.this;
                            p2pStateMachine10.logd(getName() + " clear service request");
                        }
                        P2pStateMachine.this.clearServiceRequests(message.replyTo);
                        P2pStateMachine.this.replyToMessage(message, 139309);
                        break;
                    case 139310:
                        if (WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkCanAccessWifiDirect(P2pStateMachine.this.getCallingPkgName(message.sendingUid, message.replyTo), message.sendingUid, true)) {
                            if (WifiP2pServiceImpl.this.mDiscoveryBlocked) {
                                P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139311, 2);
                                break;
                            } else {
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    P2pStateMachine p2pStateMachine11 = P2pStateMachine.this;
                                    p2pStateMachine11.logd(getName() + " discover services");
                                }
                                if (!P2pStateMachine.this.updateSupplicantServiceRequest()) {
                                    P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139311, 3);
                                    break;
                                } else {
                                    P2pStateMachine.this.stopLegacyWifiScan(true);
                                    P2pStateMachine.this.mWifiNative.p2pFind(0, message.arg1 * 1000);
                                    P2pStateMachine.this.replyToMessage(message, 139312);
                                    break;
                                }
                            }
                        } else {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139311, 0);
                            break;
                        }
                    case 139315:
                        WifiP2pDevice d = (WifiP2pDevice) message.obj;
                        if (d == null || !P2pStateMachine.this.setAndPersistDeviceName(d.deviceName)) {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139316, 0);
                            WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) changing device name  failed");
                            break;
                        } else {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine p2pStateMachine12 = P2pStateMachine.this;
                                p2pStateMachine12.logd("set device name " + d.deviceName);
                            }
                            P2pStateMachine.this.replyToMessage(message, 139317);
                            WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) changing device name  succeeded");
                            break;
                        }
                    case 139318:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine13 = P2pStateMachine.this;
                            p2pStateMachine13.logd(getName() + " delete persistent group");
                        }
                        P2pStateMachine.this.mGroups.remove(message.arg1);
                        WifiP2pServiceImpl.this.mWifiP2pMetrics.updatePersistentGroup(P2pStateMachine.this.mGroups);
                        P2pStateMachine.this.replyToMessage(message, 139320);
                        break;
                    case 139323:
                        WifiP2pWfdInfo d2 = (WifiP2pWfdInfo) message.obj;
                        if (!WifiP2pServiceImpl.this.getWfdPermission(message.sendingUid)) {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139324, 0);
                        } else if (d2 == null || !P2pStateMachine.this.setWfdInfo(d2)) {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139324, 0);
                        } else {
                            P2pStateMachine.this.replyToMessage(message, 139325);
                        }
                        if (d2 == null || !d2.isWfdEnabled()) {
                            if (WifiP2pServiceImpl.this.mWfdDialog && WifiP2pServiceImpl.this.mInvitationDialog != null && WifiP2pServiceImpl.this.mInvitationDialog.isShowing()) {
                                WifiP2pServiceImpl.this.mWfdDialog = false;
                                WifiP2pServiceImpl.this.mInvitationDialog.dismiss();
                                break;
                            }
                        } else {
                            WifiP2pServiceImpl.this.mWfdDialog = false;
                            break;
                        }
                        break;
                    case 139329:
                        if (!WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkNetworkSettingsPermission(message.sendingUid)) {
                            P2pStateMachine p2pStateMachine14 = P2pStateMachine.this;
                            p2pStateMachine14.loge("Permission violation - no NETWORK_SETTING permission, uid = " + message.sendingUid);
                            P2pStateMachine.this.replyToMessage(message, 139330);
                            break;
                        } else {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine p2pStateMachine15 = P2pStateMachine.this;
                                p2pStateMachine15.logd(getName() + " start listen mode");
                            }
                            P2pStateMachine.this.mWifiNative.p2pFlush();
                            if (P2pStateMachine.this.mWifiNative.p2pExtListen(true, 500, 500)) {
                                P2pStateMachine.this.replyToMessage(message, 139331);
                                break;
                            } else {
                                P2pStateMachine.this.replyToMessage(message, 139330);
                                break;
                            }
                        }
                    case 139332:
                        if (!WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkNetworkSettingsPermission(message.sendingUid)) {
                            P2pStateMachine p2pStateMachine16 = P2pStateMachine.this;
                            p2pStateMachine16.loge("Permission violation - no NETWORK_SETTING permission, uid = " + message.sendingUid);
                            P2pStateMachine.this.replyToMessage(message, 139333);
                            break;
                        } else {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine p2pStateMachine17 = P2pStateMachine.this;
                                p2pStateMachine17.logd(getName() + " stop listen mode");
                            }
                            if (P2pStateMachine.this.mWifiNative.p2pExtListen(false, 0, 0)) {
                                P2pStateMachine.this.replyToMessage(message, 139334);
                            } else {
                                P2pStateMachine.this.replyToMessage(message, 139333);
                            }
                            P2pStateMachine.this.mWifiNative.p2pFlush();
                            break;
                        }
                    case 139335:
                        Bundle p2pChannels = (Bundle) message.obj;
                        int lc = p2pChannels.getInt("lc", 0);
                        int oc = p2pChannels.getInt("oc", 0);
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine18 = P2pStateMachine.this;
                            p2pStateMachine18.logd(getName() + " set listen and operating channel");
                        }
                        if (P2pStateMachine.this.mWifiNative.p2pSetChannel(lc, oc)) {
                            P2pStateMachine.this.replyToMessage(message, 139337);
                            break;
                        } else {
                            P2pStateMachine.this.replyToMessage(message, 139336);
                            break;
                        }
                    case 139339:
                        Bundle requestBundle = new Bundle();
                        requestBundle.putString("android.net.wifi.p2p.EXTRA_HANDOVER_MESSAGE", P2pStateMachine.this.mWifiNative.getNfcHandoverRequest());
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139341, (int) requestBundle);
                        break;
                    case 139340:
                        Bundle selectBundle = new Bundle();
                        selectBundle.putString("android.net.wifi.p2p.EXTRA_HANDOVER_MESSAGE", P2pStateMachine.this.mWifiNative.getNfcHandoverSelect());
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139341, (int) selectBundle);
                        break;
                    case 139368:
                        if (!WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing()) {
                            P2pStateMachine.this.setLegacyWifiEnable(false);
                            WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) disabling succeeded", 1);
                            break;
                        } else {
                            P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.DISABLE_P2P);
                            break;
                        }
                    case 139371:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139266, 0);
                        break;
                    case 139372:
                        String pkgName2 = message.getData().getString("appPkgName");
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine19 = P2pStateMachine.this;
                            p2pStateMachine19.logd(getName() + " package to call P2P_LISTEN -> " + pkgName2);
                        }
                        if (!"com.android.bluetooth".equals(pkgName2)) {
                            if (P2pStateMachine.this.mWifiNative.p2pListen(message.arg1)) {
                                P2pStateMachine.this.replyToMessage(message, 139331);
                                break;
                            } else {
                                P2pStateMachine.this.replyToMessage(message, 139330);
                                break;
                            }
                        } else {
                            WifiP2pServiceImpl.this.mAdvancedOppSender = true;
                            WifiP2pServiceImpl.this.mAdvancedOppInProgress = true;
                            P2pStateMachine.this.sendMessage(139277);
                            break;
                        }
                    case 139375:
                        int isTimerOn = message.arg1;
                        if (isTimerOn != 1) {
                            if (isTimerOn == 2) {
                                WifiP2pServiceImpl.this.mAlarmManager.cancel(WifiP2pServiceImpl.this.mTimerIntent);
                                break;
                            }
                        } else {
                            WifiP2pServiceImpl.this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + 120000, WifiP2pServiceImpl.this.mTimerIntent);
                            break;
                        }
                        break;
                    case 139405:
                        P2pStateMachine.this.mWifiNative.p2pSet("PREQ", message.getData().getString("REQ_DATA"));
                        break;
                    case 139406:
                        P2pStateMachine.this.mWifiNative.p2pSet("PRESP", message.getData().getString("RESP_DATA"));
                        break;
                    case 139407:
                        if (WifiP2pServiceImpl.this.mDiscoveryBlocked) {
                            WifiP2pServiceImpl.this.mDiscoveryPostponed = false;
                        }
                        if (WifiP2pServiceImpl.this.mWfdConnected || (WifiP2pServiceImpl.this.mThisDevice.wfdInfo != null && WifiP2pServiceImpl.this.mThisDevice.wfdInfo.isWfdEnabled())) {
                            P2pStateMachine p2pStateMachine20 = P2pStateMachine.this;
                            p2pStateMachine20.logd(getName() + " Do not call stopLegacyWifiScan(false) because WFD is connected");
                        } else {
                            P2pStateMachine.this.stopLegacyWifiScan(false);
                        }
                        P2pStateMachine.this.mWifiNative.p2pStopFind();
                        break;
                    case 139414:
                        int listenOffloadingTimer = message.arg1;
                        if (listenOffloadingTimer == 1) {
                            P2pStateMachine p2pStateMachine21 = P2pStateMachine.this;
                            p2pStateMachine21.logd(getName() + " SET_LISTEN_OFFLOADING_TIMER " + listenOffloadingTimer);
                            WifiP2pServiceImpl.this.mAlarmManager.setExact(2, SystemClock.elapsedRealtime() + 31000, WifiP2pServiceImpl.this.mLOTimerIntent);
                            WifiP2pServiceImpl.this.mListenOffloading = true;
                            WifiP2pServiceImpl.access$1608(WifiP2pServiceImpl.this);
                            break;
                        } else {
                            P2pStateMachine p2pStateMachine22 = P2pStateMachine.this;
                            p2pStateMachine22.logd(getName() + " SET_LISTEN_OFFLOADING_TIMER " + listenOffloadingTimer);
                            WifiP2pServiceImpl.this.mAlarmManager.cancel(WifiP2pServiceImpl.this.mLOTimerIntent);
                            WifiP2pServiceImpl.this.mListenOffloading = false;
                            WifiP2pServiceImpl.this.mLOCount = 0;
                            break;
                        }
                    case 139415:
                        Bundle p2pLOParams = (Bundle) message.obj;
                        int channel = p2pLOParams.getInt("channel", 0);
                        int period = p2pLOParams.getInt("period", 0);
                        int interval = p2pLOParams.getInt("interval", 0);
                        int count = p2pLOParams.getInt(ReportIdKey.KEY_COUNT, 0);
                        if (channel > 0) {
                            try {
                                Thread.sleep(600);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            P2pStateMachine p2pStateMachine23 = P2pStateMachine.this;
                            p2pStateMachine23.logd(getName() + " startP2pListenOffloading " + channel + " " + period + " " + interval + " " + count);
                            P2pStateMachine.this.mWifiNative.startP2pListenOffloading(channel, period, interval, count);
                            break;
                        } else {
                            P2pStateMachine p2pStateMachine24 = P2pStateMachine.this;
                            p2pStateMachine24.logd(getName() + " stopP2pListenOffloading ");
                            P2pStateMachine.this.mWifiNative.stopP2pListenOffloading();
                            break;
                        }
                    case WifiP2pServiceImpl.SET_MIRACAST_MODE /*{ENCODED_INT: 143374}*/:
                        P2pStateMachine.this.mWifiNative.setMiracastMode(message.arg1);
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine25 = P2pStateMachine.this;
                            p2pStateMachine25.logd(getName() + "setMiracastMode : " + message.arg1);
                        }
                        if (message.arg1 != 0) {
                            WifiP2pServiceImpl.this.mWfdConnected = true;
                            P2pStateMachine.this.stopLegacyWifiScan(true);
                            if (P2pStateMachine.this.mGroup != null && (p2pFreq = P2pStateMachine.this.mGroup.getFrequency()) < 3000 && !WifiP2pServiceImpl.this.mBleLatency) {
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    P2pStateMachine p2pStateMachine26 = P2pStateMachine.this;
                                    p2pStateMachine26.logd("setMiracastMode = " + message.arg1 + ", P2P Group Freq = " + p2pFreq + " hz, set BLE scanning latency = " + message.arg1);
                                }
                                WifiP2pServiceImpl.this.mBleLatency = true;
                                P2pStateMachine.this.mWifiLegacyNative.setLatencyCritical(P2pStateMachine.this.mWifiInterface, message.arg1);
                                break;
                            }
                        } else {
                            WifiP2pServiceImpl.this.mWfdConnected = false;
                            if (WifiP2pServiceImpl.this.mBleLatency) {
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    P2pStateMachine p2pStateMachine27 = P2pStateMachine.this;
                                    p2pStateMachine27.logd("setMiracastMode = " + message.arg1 + ", P2P Group Freq = None, set BLE scanning latency = " + message.arg1);
                                }
                                WifiP2pServiceImpl.this.mBleLatency = false;
                                P2pStateMachine.this.mWifiLegacyNative.setLatencyCritical(P2pStateMachine.this.mWifiInterface, message.arg1);
                                break;
                            }
                        }
                        break;
                    case WifiP2pServiceImpl.BLOCK_DISCOVERY /*{ENCODED_INT: 143375}*/:
                        boolean blocked = message.arg1 == 1;
                        if (WifiP2pServiceImpl.this.mDiscoveryBlocked != blocked) {
                            WifiP2pServiceImpl.this.mDiscoveryBlocked = blocked;
                            if (blocked && WifiP2pServiceImpl.this.mDiscoveryStarted) {
                                P2pStateMachine.this.mWifiNative.p2pStopFind();
                                WifiP2pServiceImpl.this.mDiscoveryPostponed = true;
                            }
                            if (!blocked && WifiP2pServiceImpl.this.mDiscoveryPostponed && !WifiP2pServiceImpl.this.mWfdConnected) {
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    Log.d(WifiP2pServiceImpl.TAG, "p2pFind() called by BLOCK_DISCOVERY disabled");
                                }
                                WifiP2pServiceImpl.this.mDiscoveryPostponed = false;
                                P2pStateMachine.this.mWifiNative.p2pFind(120);
                            }
                            if (blocked) {
                                if (message.obj == null) {
                                    Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                                    break;
                                } else {
                                    try {
                                        ((StateMachine) message.obj).sendMessage(message.arg2);
                                        break;
                                    } catch (Exception e2) {
                                        P2pStateMachine p2pStateMachine28 = P2pStateMachine.this;
                                        p2pStateMachine28.loge("unable to send BLOCK_DISCOVERY response: " + e2);
                                        break;
                                    }
                                }
                            }
                        }
                        break;
                    case WifiP2pServiceImpl.ENABLE_P2P /*{ENCODED_INT: 143376}*/:
                        break;
                    case WifiP2pServiceImpl.DISABLE_P2P /*{ENCODED_INT: 143377}*/:
                        if (P2pStateMachine.this.mPeers.clear()) {
                            P2pStateMachine.this.sendPeersChangedBroadcast();
                        }
                        if (P2pStateMachine.this.mGroups.clear()) {
                            P2pStateMachine.this.sendP2pPersistentGroupsChangedBroadcast();
                        }
                        P2pStateMachine.this.clearServicesForAllClients();
                        P2pStateMachine.this.mWifiNative.p2pCancelConnect();
                        P2pStateMachine.this.handleGroupCreationFailure();
                        P2pStateMachine.this.mWifiMonitor.stopMonitoring(P2pStateMachine.this.mInterfaceName);
                        P2pStateMachine.this.mWifiNative.teardownInterface();
                        P2pStateMachine p2pStateMachine29 = P2pStateMachine.this;
                        p2pStateMachine29.transitionTo(p2pStateMachine29.mP2pDisablingState);
                        break;
                    case WifiP2pServiceImpl.REMOVE_CLIENT_INFO /*{ENCODED_INT: 143378}*/:
                        if (message.obj instanceof IBinder) {
                            IBinder b = (IBinder) message.obj;
                            P2pStateMachine p2pStateMachine30 = P2pStateMachine.this;
                            p2pStateMachine30.clearClientInfo((Messenger) WifiP2pServiceImpl.this.mClientChannelList.get(b));
                            WifiP2pServiceImpl.this.mClientChannelList.remove(b);
                            break;
                        }
                        break;
                    case WifiP2pServiceImpl.SET_COUNTRY_CODE /*{ENCODED_INT: 143379}*/:
                        String countryCode = ((String) message.obj).toUpperCase(Locale.ROOT);
                        if (Settings.Global.getInt(WifiP2pServiceImpl.this.mContext.getContentResolver(), "airplane_mode_on", 0) != 1) {
                            if (WifiP2pServiceImpl.this.mLastSetCountryCode != null) {
                                countryCode.equals(WifiP2pServiceImpl.this.mLastSetCountryCode);
                                break;
                            }
                        } else {
                            Log.e(WifiP2pServiceImpl.TAG, "Airplane mode : skipped SET_COUNTRY_CODE");
                            break;
                        }
                        break;
                    case 147458:
                        P2pStateMachine.this.loge("Unexpected loss of p2p socket connection");
                        P2pStateMachine p2pStateMachine31 = P2pStateMachine.this;
                        p2pStateMachine31.transitionTo(p2pStateMachine31.mP2pDisabledState);
                        break;
                    case WifiP2pMonitor.P2P_DEVICE_FOUND_EVENT:
                        if (message.obj == null) {
                            Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                            break;
                        } else {
                            WifiP2pDevice device = (WifiP2pDevice) message.obj;
                            if (WifiP2pServiceImpl.this.mThisDevice.deviceAddress != null && !WifiP2pServiceImpl.this.mThisDevice.deviceAddress.equals(device.deviceAddress)) {
                                if (Build.VERSION.SDK_INT < 29 && device.contactInfoHash != null) {
                                    P2pStateMachine.this.convertDeviceNameNSetIconViaContact(device);
                                }
                                P2pStateMachine.this.mPeers.updateSupplicantDetails(device);
                                P2pStateMachine.this.sendPeersChangedBroadcast();
                                break;
                            }
                        }
                    case WifiP2pMonitor.P2P_DEVICE_LOST_EVENT:
                        if (message.obj != null) {
                            if (P2pStateMachine.this.mPeers.remove(((WifiP2pDevice) message.obj).deviceAddress) != null) {
                                P2pStateMachine.this.sendPeersChangedBroadcast();
                                break;
                            }
                        } else {
                            Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                            break;
                        }
                        break;
                    case WifiP2pMonitor.P2P_FIND_STOPPED_EVENT:
                        if (WifiP2pServiceImpl.this.mListenOffloading) {
                            if (!P2pStateMachine.this.mPeers.isEmpty()) {
                                try {
                                    Thread.sleep(300);
                                } catch (InterruptedException e3) {
                                    e3.printStackTrace();
                                }
                                P2pStateMachine p2pStateMachine32 = P2pStateMachine.this;
                                p2pStateMachine32.logd(getName() + " Start listen offloading!");
                                P2pStateMachine.this.mWifiNative.startP2pListenOffloading(1, 500, 5000, 6);
                            }
                            P2pStateMachine p2pStateMachine33 = P2pStateMachine.this;
                            p2pStateMachine33.logd(getName() + " Set listen offloading timer!");
                            WifiP2pServiceImpl.this.mP2pStateMachine.sendMessage(139414, 1);
                            WifiP2pServiceImpl.this.mListenOffloading = false;
                        }
                        P2pStateMachine.this.sendP2pDiscoveryChangedBroadcast(false);
                        if (!WifiP2pServiceImpl.this.mWfdConnected) {
                            P2pStateMachine.this.stopLegacyWifiScan(false);
                        }
                        WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) disabling succeeded", 1);
                        break;
                    case WifiP2pMonitor.P2P_SERV_DISC_RESP_EVENT:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine34 = P2pStateMachine.this;
                            p2pStateMachine34.logd(getName() + " receive service response");
                        }
                        if (message.obj == null) {
                            Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                            break;
                        } else {
                            for (WifiP2pServiceResponse resp : (List) message.obj) {
                                resp.setSrcDevice(P2pStateMachine.this.mPeers.get(resp.getSrcDevice().deviceAddress));
                                P2pStateMachine.this.sendServiceResponse(resp);
                            }
                            break;
                        }
                    case WifiP2pMonitor.P2P_PERSISTENT_PSK_FAIL_EVENT:
                        String dataString = (String) message.obj;
                        if (dataString != null) {
                            try {
                                int networkId = Integer.parseInt(dataString.substring(dataString.indexOf("=") + 1));
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    P2pStateMachine p2pStateMachine35 = P2pStateMachine.this;
                                    p2pStateMachine35.logd(getName() + " delete persistent group(PERSISTENT_PSK_FAIL), networkId : " + networkId);
                                }
                                P2pStateMachine.this.mGroups.remove(networkId);
                                break;
                            } catch (NumberFormatException e4) {
                                break;
                            }
                        }
                        break;
                    case WifiP2pMonitor.P2P_P2P_SCONNECT_PROBE_REQ_EVENT:
                        Intent req_intent = new Intent("android.net.wifi.p2p.SCONNECT_PROBE_REQ");
                        req_intent.addFlags(67108864);
                        req_intent.putExtra("probeReq", new String((String) message.obj));
                        WifiP2pServiceImpl.this.mContext.sendBroadcastAsUser(req_intent, UserHandle.ALL);
                        break;
                    default:
                        return false;
                }
                return true;
            }

            public void exit() {
                P2pStateMachine.this.mP2pState = 1;
                P2pStateMachine.this.sendP2pDiscoveryChangedBroadcast(false);
                P2pStateMachine.this.sendP2pStateChangedBroadcast(false);
                WifiP2pServiceImpl.this.mNetworkInfo.setDetailedState(NetworkInfo.DetailedState.DISCONNECTED, null, null);
                WifiP2pServiceImpl.this.mNetworkInfo.setIsAvailable(false);
                P2pStateMachine.this.sendP2pConnectionChangedBroadcast();
                WifiP2pServiceImpl.this.mDiscoveryStarted = false;
                WifiP2pServiceImpl.this.mWfdConnected = false;
                if (WifiP2pServiceImpl.this.mInvitationDialog != null && WifiP2pServiceImpl.this.mInvitationDialog.isShowing()) {
                    WifiP2pServiceImpl.this.mWpsTimer.cancel();
                    WifiP2pServiceImpl.this.mInvitationDialog.dismiss();
                    WifiP2pServiceImpl.this.mInvitationDialog = null;
                }
                WifiP2pServiceImpl.this.mLastSetCountryCode = null;
                P2pStateMachine.this.mWifiNative.p2pSet("pre_auth", "0");
                WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) enabling succeeded", 1);
            }
        }

        /* access modifiers changed from: package-private */
        public class InactiveState extends State {
            InactiveState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                if (P2pStateMachine.this.mSavedPeerConfig != null && !WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndJoin) {
                    P2pStateMachine.this.mSavedPeerConfig.invalidate();
                }
                P2pStateMachine.this.mIsInactiveState = true;
                if (WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndListen) {
                    P2pStateMachine.this.sendMessage(139372, 20);
                    P2pStateMachine.this.sendMessageDelayed(WifiP2pServiceImpl.P2P_ADVOPP_LISTEN_TIMEOUT, 20000);
                }
                if (WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndJoin) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    WifiP2pServiceImpl.this.mConnReqInfo.set(p2pStateMachine.fetchCurrentDeviceDetails(p2pStateMachine.mSavedPeerConfig), "com.android.bluetooth.advopp", 0, 0, 1, P2pStateMachine.this.mWifiNative.p2pGetManufacturer(P2pStateMachine.this.mSavedPeerConfig.deviceAddress), P2pStateMachine.this.mWifiNative.p2pGetDeviceType(P2pStateMachine.this.mSavedPeerConfig.deviceAddress));
                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                        P2pStateMachine.this.logd("AdvancedOpp - remove autonomous group and join the found group");
                    }
                    WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndJoin = false;
                    WifiP2pServiceImpl.this.mJoinExistingGroup = true;
                    P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                    p2pStateMachine2.p2pConnectWithPinDisplay(p2pStateMachine2.mSavedPeerConfig);
                    P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                    p2pStateMachine3.transitionTo(p2pStateMachine3.mGroupNegotiationState);
                }
            }

            /* JADX INFO: Multiple debug info for r2v112 int: [D('bundle' android.os.Bundle), D('timeout' int)] */
            public boolean processMessage(Message message) {
                boolean ret;
                boolean result;
                WifiP2pDevice owner;
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    if (message.what == 143375) {
                        P2pStateMachine.this.logd(getName() + message.what);
                    } else {
                        P2pStateMachine.this.logd(getName() + message.toString());
                    }
                }
                boolean z = false;
                switch (message.what) {
                    case 139268:
                        if (WifiP2pServiceImpl.this.mDiscoveryBlocked) {
                            WifiP2pServiceImpl.this.mDiscoveryPostponed = false;
                        }
                        if (P2pStateMachine.this.mWifiNative.p2pStopFind()) {
                            P2pStateMachine.this.mWifiNative.p2pFlush();
                            WifiP2pServiceImpl.this.mServiceDiscReqId = null;
                            P2pStateMachine.this.replyToMessage(message, 139270);
                        } else {
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139269, 0);
                        }
                        P2pStateMachine.this.stopLegacyWifiScan(false);
                        return true;
                    case 139271:
                        if (!WifiP2pServiceImpl.this.isAllowWifiDirectByEDM()) {
                            P2pStateMachine.this.replyToMessage(message, 139272);
                            return true;
                        }
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(getName() + " sending connect");
                        }
                        Bundle bundle = message.getData();
                        WifiP2pConfig config = (WifiP2pConfig) bundle.getParcelable("wifiP2pConfig");
                        String pkgName = bundle.getString("appPkgName");
                        WifiP2pDevice peerDev = P2pStateMachine.this.fetchCurrentDeviceDetails(config);
                        int persistent = 0;
                        int join = (peerDev == null || !peerDev.isGroupOwner()) ? 0 : 1;
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(getName() + " package to call connect -> " + pkgName);
                        }
                        if (WifiP2pServiceImpl.this.mAdvancedOppSender || WifiP2pServiceImpl.this.mAdvancedOppReceiver || WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkCanAccessWifiDirect(P2pStateMachine.this.getCallingPkgName(message.sendingUid, message.replyTo), message.sendingUid, false)) {
                            boolean isConnectFailed = false;
                            if (P2pStateMachine.this.isConfigValidAsGroup(config)) {
                                WifiP2pServiceImpl.this.mAutonomousGroup = false;
                                P2pStateMachine.this.mWifiNative.p2pStopFind();
                                if (P2pStateMachine.this.mWifiNative.p2pGroupAdd(config, true)) {
                                    WifiP2pServiceImpl.this.mWifiP2pMetrics.startConnectionEvent(3, config);
                                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                                    p2pStateMachine.transitionTo(p2pStateMachine.mGroupNegotiationState);
                                } else {
                                    P2pStateMachine.this.loge("Cannot join a group with config.");
                                    isConnectFailed = true;
                                    P2pStateMachine.this.replyToMessage(message, 139272);
                                    WifiP2pServiceImpl.this.auditLog(false, "Connecting to device address  using Wi-Fi Direct (P2P) failed");
                                }
                            } else if (P2pStateMachine.this.isConfigInvalid(config)) {
                                P2pStateMachine.this.loge("Dropping connect request " + config);
                                P2pStateMachine.this.replyToMessage(message, 139272);
                                WifiP2pServiceImpl.this.auditLog(false, "Connecting to device address  using Wi-Fi Direct (P2P) failed");
                                return true;
                            } else {
                                P2pStateMachine.this.mPeers.updateGroupCapability(config.deviceAddress, P2pStateMachine.this.mWifiNative.getGroupCapability(config.deviceAddress));
                                if (P2pStateMachine.this.mWifiNative.p2pPeer(config.deviceAddress) == null) {
                                    P2pStateMachine.this.loge("Dropping connect requeset : peer is flushed " + config);
                                    P2pStateMachine.this.replyToMessage(message, 139272);
                                    WifiP2pServiceImpl.this.auditLog(false, "Connecting to device address  using Wi-Fi Direct (P2P) failed");
                                    return true;
                                }
                                WifiP2pServiceImpl.this.mJoinExistingGroup = false;
                                WifiP2pServiceImpl.this.mWfdDialog = true;
                                WifiP2pServiceImpl.this.mValidFreqConflict = true;
                                boolean isResp = P2pStateMachine.this.mSavedPeerConfig != null && config.deviceAddress.equals(P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                                P2pStateMachine.this.mSavedPeerConfig = config;
                                WifiP2pServiceImpl.this.mAutonomousGroup = false;
                                P2pStateMachine.this.mWifiNative.p2pStopFind();
                                if (P2pStateMachine.this.reinvokePersistentGroup(config)) {
                                    WifiP2pServiceImpl.this.mWifiP2pMetrics.startConnectionEvent(1, config);
                                    persistent = 1;
                                    P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                                    p2pStateMachine2.transitionTo(p2pStateMachine2.mGroupNegotiationState);
                                } else if (isResp) {
                                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                        P2pStateMachine.this.logd("prov disc is not needed!!");
                                    }
                                    P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                                    p2pStateMachine3.p2pConnectWithPinDisplay(p2pStateMachine3.mSavedPeerConfig);
                                    P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                                    p2pStateMachine4.transitionTo(p2pStateMachine4.mGroupNegotiationState);
                                } else {
                                    WifiP2pServiceImpl.this.mWifiP2pMetrics.startConnectionEvent(0, config);
                                    P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                                    p2pStateMachine5.transitionTo(p2pStateMachine5.mProvisionDiscoveryState);
                                }
                            }
                            if (isConnectFailed) {
                                return true;
                            }
                            P2pStateMachine.this.mSavedPeerConfig = config;
                            P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mSavedPeerConfig.deviceAddress, 1);
                            P2pStateMachine.this.sendPeersChangedBroadcast();
                            WifiP2pServiceImpl.this.auditLog(false, "Connecting to device address " + P2pStateMachine.this.mSavedPeerConfig.deviceAddress + " using Wi-Fi Direct (P2P) succeeded");
                            WifiP2pServiceImpl.this.mConnReqInfo.set(peerDev, pkgName, 0, persistent, join, P2pStateMachine.this.mWifiNative.p2pGetManufacturer(config.deviceAddress), P2pStateMachine.this.mWifiNative.p2pGetDeviceType(config.deviceAddress));
                            P2pStateMachine.this.replyToMessage(message, 139273);
                            return true;
                        }
                        P2pStateMachine.this.replyToMessage(message, 139272);
                        WifiP2pServiceImpl.this.auditLog(false, "Connecting to device address  using Wi-Fi Direct (P2P) failed");
                        return true;
                    case 139277:
                        if (WifiP2pServiceImpl.this.mAdvancedOppSender || WifiP2pServiceImpl.this.mAdvancedOppReceiver || WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkCanAccessWifiDirect(P2pStateMachine.this.getCallingPkgName(message.sendingUid, message.replyTo), message.sendingUid, false)) {
                            if (!WifiP2pServiceImpl.this.mAdvancedOppReceiver && !WifiP2pServiceImpl.this.mAdvancedOppSender) {
                                WifiP2pServiceImpl.this.mAutonomousGroup = true;
                            }
                            if (!WifiP2pServiceImpl.this.isAllowWifiDirectByEDM()) {
                                P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139278, 0);
                                return true;
                            }
                            int netId = message.arg1;
                            WifiP2pConfig config2 = (WifiP2pConfig) message.obj;
                            if (config2 != null) {
                                if (P2pStateMachine.this.isConfigValidAsGroup(config2)) {
                                    WifiP2pServiceImpl.this.mWifiP2pMetrics.startConnectionEvent(3, config2);
                                    ret = P2pStateMachine.this.mWifiNative.p2pGroupAdd(config2, false);
                                } else {
                                    ret = false;
                                }
                            } else if (netId != -2) {
                                WifiP2pServiceImpl.this.mWifiP2pMetrics.startConnectionEvent(2, null);
                                ret = P2pStateMachine.this.setP2pGroupForSamsung();
                            } else if (P2pStateMachine.this.mGroups.getNetworkId(WifiP2pServiceImpl.this.mThisDevice.deviceAddress) != -1) {
                                WifiP2pServiceImpl.this.mWifiP2pMetrics.startConnectionEvent(1, null);
                                ret = P2pStateMachine.this.setP2pGroupForSamsung();
                            } else {
                                WifiP2pServiceImpl.this.mWifiP2pMetrics.startConnectionEvent(2, null);
                                ret = P2pStateMachine.this.setP2pGroupForSamsung();
                            }
                            if (ret) {
                                P2pStateMachine.this.replyToMessage(message, 139279);
                                P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                                p2pStateMachine6.transitionTo(p2pStateMachine6.mGroupNegotiationState);
                                return true;
                            }
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139278, 0);
                            return true;
                        }
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139278, 0);
                        return true;
                    case 139329:
                        if (!WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkNetworkSettingsPermission(message.sendingUid)) {
                            P2pStateMachine.this.loge("Permission violation - no NETWORK_SETTING permission, uid = " + message.sendingUid);
                            P2pStateMachine.this.replyToMessage(message, 139330);
                            return true;
                        }
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(getName() + " start listen mode");
                        }
                        P2pStateMachine.this.mWifiNative.p2pFlush();
                        if (P2pStateMachine.this.mWifiNative.p2pExtListen(true, 500, 500)) {
                            P2pStateMachine.this.replyToMessage(message, 139331);
                            return true;
                        }
                        P2pStateMachine.this.replyToMessage(message, 139330);
                        return true;
                    case 139332:
                        if (!WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkNetworkSettingsPermission(message.sendingUid)) {
                            P2pStateMachine.this.loge("Permission violation - no NETWORK_SETTING permission, uid = " + message.sendingUid);
                            P2pStateMachine.this.replyToMessage(message, 139333);
                            return true;
                        }
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(getName() + " stop listen mode");
                        }
                        if (P2pStateMachine.this.mWifiNative.p2pExtListen(false, 0, 0)) {
                            P2pStateMachine.this.replyToMessage(message, 139334);
                        } else {
                            P2pStateMachine.this.replyToMessage(message, 139333);
                        }
                        P2pStateMachine.this.mWifiNative.p2pFlush();
                        return true;
                    case 139335:
                        if (message.obj == null) {
                            Log.e(WifiP2pServiceImpl.TAG, "Illegal arguments(s)");
                            return true;
                        }
                        Bundle p2pChannels = (Bundle) message.obj;
                        int lc = p2pChannels.getInt("lc", 0);
                        int oc = p2pChannels.getInt("oc", 0);
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(getName() + " set listen and operating channel");
                        }
                        if (P2pStateMachine.this.mWifiNative.p2pSetChannel(lc, oc)) {
                            P2pStateMachine.this.replyToMessage(message, 139337);
                            return true;
                        }
                        P2pStateMachine.this.replyToMessage(message, 139336);
                        return true;
                    case 139342:
                        String handoverSelect = null;
                        if (message.obj != null) {
                            handoverSelect = ((Bundle) message.obj).getString("android.net.wifi.p2p.EXTRA_HANDOVER_MESSAGE");
                        }
                        if (handoverSelect == null || !P2pStateMachine.this.mWifiNative.initiatorReportNfcHandover(handoverSelect)) {
                            P2pStateMachine.this.replyToMessage(message, 139345);
                            return true;
                        }
                        P2pStateMachine.this.replyToMessage(message, 139344);
                        P2pStateMachine p2pStateMachine7 = P2pStateMachine.this;
                        p2pStateMachine7.transitionTo(p2pStateMachine7.mGroupCreatingState);
                        return true;
                    case 139343:
                        String handoverRequest = null;
                        if (message.obj != null) {
                            handoverRequest = ((Bundle) message.obj).getString("android.net.wifi.p2p.EXTRA_HANDOVER_MESSAGE");
                        }
                        if (handoverRequest == null || !P2pStateMachine.this.mWifiNative.responderReportNfcHandover(handoverRequest)) {
                            P2pStateMachine.this.replyToMessage(message, 139345);
                            return true;
                        }
                        P2pStateMachine.this.replyToMessage(message, 139344);
                        P2pStateMachine p2pStateMachine8 = P2pStateMachine.this;
                        p2pStateMachine8.transitionTo(p2pStateMachine8.mGroupCreatingState);
                        return true;
                    case 139371:
                        int timeout = message.arg1;
                        P2pStateMachine.this.stopLegacyWifiScan(true);
                        if (P2pStateMachine.this.mPeers.clear()) {
                            P2pStateMachine.this.sendPeersChangedBroadcast();
                        }
                        P2pStateMachine.this.mWifiNative.p2pFlush();
                        if (timeout == -999) {
                            result = P2pStateMachine.this.mWifiNative.p2pFind(5, -999);
                        } else {
                            result = P2pStateMachine.this.mWifiNative.p2pFind(timeout);
                        }
                        P2pStateMachine.this.replyToMessage(message, 139267);
                        P2pStateMachine.this.logd(getName() + " p2pFlushFind result : " + result);
                        return true;
                    case 139376:
                        String pkgName2 = message.getData().getString("appPkgName");
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(getName() + " package to call requestNfcConnect -> " + pkgName2 + " with arg : " + message.arg1);
                        }
                        if (WifiP2pServiceImpl.this.mConnReqInfo.peerDev == null && WifiP2pServiceImpl.this.mConnReqInfo.pkgName == null) {
                            WifiP2pServiceImpl.this.mConnReqInfo.pkgName = pkgName2;
                        }
                        if (message.arg1 != 1) {
                            return true;
                        }
                        P2pStateMachine p2pStateMachine9 = P2pStateMachine.this;
                        p2pStateMachine9.transitionTo(p2pStateMachine9.mNfcProvisionState);
                        return true;
                    case 139377:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(getName() + " sending connect");
                        }
                        if (!WifiP2pServiceImpl.this.isAllowWifiDirectByEDM()) {
                            P2pStateMachine.this.replyToMessage(message, 139272);
                            return true;
                        }
                        int i = message.arg1;
                        WifiP2pServiceImpl.this.mAutonomousGroup = false;
                        WifiP2pServiceImpl.this.mJoinExistingGroup = false;
                        WifiP2pServiceImpl.this.mValidFreqConflict = true;
                        if (((WifiP2pConfig) message.obj).netId == 1) {
                            P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mSavedPeerConfig.deviceAddress, 1);
                            P2pStateMachine.this.sendPeersChangedBroadcast();
                        }
                        P2pStateMachine.this.replyToMessage(message, 139273);
                        P2pStateMachine p2pStateMachine10 = P2pStateMachine.this;
                        p2pStateMachine10.transitionTo(p2pStateMachine10.mGroupNegotiationState);
                        WifiP2pServiceImpl.this.auditLog(false, "Connecting to device address " + P2pStateMachine.this.mSavedPeerConfig.deviceAddress + " using Wi-Fi Direct (P2P) succeeded");
                        return true;
                    case WifiP2pMonitor.P2P_GO_NEGOTIATION_REQUEST_EVENT:
                        WifiP2pConfig config3 = (WifiP2pConfig) message.obj;
                        if (P2pStateMachine.this.isConfigInvalid(config3)) {
                            P2pStateMachine.this.loge("Dropping GO neg request " + config3);
                            return true;
                        }
                        P2pStateMachine.this.mSavedPeerConfig = config3;
                        WifiP2pServiceImpl.this.mAutonomousGroup = false;
                        WifiP2pServiceImpl.this.mJoinExistingGroup = false;
                        WifiP2pServiceImpl.this.mWifiP2pMetrics.startConnectionEvent(0, config3);
                        WifiP2pDevice peerDev2 = P2pStateMachine.this.fetchCurrentDeviceDetails(config3);
                        String manufacturer = P2pStateMachine.this.mWifiNative.p2pGetManufacturer(config3.deviceAddress);
                        String type = P2pStateMachine.this.mWifiNative.p2pGetDeviceType(config3.deviceAddress);
                        String pkgName3 = null;
                        List<ActivityManager.RunningTaskInfo> tasks = WifiP2pServiceImpl.this.mActivityMgr.getRunningTasks(1);
                        if (tasks.size() != 0) {
                            pkgName3 = tasks.get(0).baseActivity.getPackageName();
                        }
                        if (WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndListen) {
                            pkgName3 = "com.android.bluetooth.advopp";
                        }
                        WifiP2pServiceImpl.this.mConnReqInfo.set(peerDev2, pkgName3, 1, 0, 0, manufacturer, type);
                        P2pStateMachine p2pStateMachine11 = P2pStateMachine.this;
                        if (p2pStateMachine11.sendConnectNoticeToApp(p2pStateMachine11.mPeers.get(P2pStateMachine.this.mSavedPeerConfig.deviceAddress), P2pStateMachine.this.mSavedPeerConfig)) {
                            return true;
                        }
                        P2pStateMachine p2pStateMachine12 = P2pStateMachine.this;
                        p2pStateMachine12.transitionTo(p2pStateMachine12.mUserAuthorizingNegotiationRequestState);
                        return true;
                    case WifiP2pMonitor.P2P_GROUP_STARTED_EVENT:
                        if (message.obj == null) {
                            Log.e(WifiP2pServiceImpl.TAG, "Invalid argument(s)");
                            return true;
                        }
                        P2pStateMachine.this.mGroup = (WifiP2pGroup) message.obj;
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(getName() + " group started");
                        }
                        if (P2pStateMachine.this.mGroup.isGroupOwner() && WifiP2pServiceImpl.EMPTY_DEVICE_ADDRESS.equals(P2pStateMachine.this.mGroup.getOwner().deviceAddress)) {
                            P2pStateMachine.this.mGroup.getOwner().deviceAddress = WifiP2pServiceImpl.this.mThisDevice.deviceAddress;
                        }
                        if (!P2pStateMachine.this.mGroup.mStaticIp.isEmpty()) {
                            P2pStateMachine.this.logd("set staticIP from EAPOL-Key " + P2pStateMachine.this.mGroup.mStaticIp);
                            P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp = NetworkUtils.inetAddressToInt((Inet4Address) NetworkUtils.numericToInetAddress(P2pStateMachine.this.mGroup.mStaticIp));
                        } else if (P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp != 0 && !P2pStateMachine.this.mGroup.mGroupOwnerStaticIp.isEmpty()) {
                            P2pStateMachine.this.mGroup.mGroupOwnerStaticIp = "";
                        }
                        if (P2pStateMachine.this.mGroup.getNetworkId() == -2 || WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndListen) {
                            WifiP2pServiceImpl.this.mAutonomousGroup = false;
                            P2pStateMachine.this.deferMessage(message);
                            P2pStateMachine p2pStateMachine13 = P2pStateMachine.this;
                            p2pStateMachine13.transitionTo(p2pStateMachine13.mGroupNegotiationState);
                            return true;
                        }
                        P2pStateMachine.this.loge("Unexpected group creation, remove " + P2pStateMachine.this.mGroup);
                        P2pStateMachine.this.mWifiNative.p2pGroupRemove(P2pStateMachine.this.mGroup.getInterface());
                        return true;
                    case WifiP2pMonitor.P2P_INVITATION_RECEIVED_EVENT:
                        if (!WifiP2pServiceImpl.this.isAllowWifiDirectByEDM()) {
                            return true;
                        }
                        if (message.obj == null) {
                            Log.e(WifiP2pServiceImpl.TAG, "Invalid argument(s)");
                            return true;
                        }
                        WifiP2pGroup group = (WifiP2pGroup) message.obj;
                        WifiP2pDevice owner2 = group.getOwner();
                        if (owner2 == null) {
                            int id = group.getNetworkId();
                            if (id < 0) {
                                P2pStateMachine.this.loge("Ignored invitation from null owner");
                                return true;
                            }
                            String addr = P2pStateMachine.this.mGroups.getOwnerAddr(id);
                            if (addr != null) {
                                group.setOwner(new WifiP2pDevice(addr));
                                owner2 = group.getOwner();
                            } else {
                                P2pStateMachine.this.loge("Ignored invitation from null owner");
                                return true;
                            }
                        }
                        WifiP2pConfig config4 = new WifiP2pConfig();
                        config4.deviceAddress = group.getOwner().deviceAddress;
                        P2pStateMachine.this.mSelectedP2pGroupAddress = config4.deviceAddress;
                        if (P2pStateMachine.this.isConfigInvalid(config4)) {
                            P2pStateMachine.this.loge("Dropping invitation request " + config4);
                            return true;
                        }
                        P2pStateMachine.this.mSavedPeerConfig = config4;
                        if (!(owner2 == null || (owner = P2pStateMachine.this.mPeers.get(owner2.deviceAddress)) == null)) {
                            if (owner.wpsPbcSupported()) {
                                P2pStateMachine.this.mSavedPeerConfig.wps.setup = 0;
                            } else if (owner.wpsKeypadSupported()) {
                                P2pStateMachine.this.mSavedPeerConfig.wps.setup = 2;
                            } else if (owner.wpsDisplaySupported()) {
                                P2pStateMachine.this.mSavedPeerConfig.wps.setup = 1;
                            }
                        }
                        WifiP2pServiceImpl.this.mAutonomousGroup = false;
                        WifiP2pServiceImpl.this.mJoinExistingGroup = true;
                        WifiP2pServiceImpl.this.mWifiP2pMetrics.startConnectionEvent(0, config4);
                        P2pStateMachine p2pStateMachine14 = P2pStateMachine.this;
                        if (!p2pStateMachine14.sendConnectNoticeToApp(p2pStateMachine14.mPeers.get(P2pStateMachine.this.mSavedPeerConfig.deviceAddress), P2pStateMachine.this.mSavedPeerConfig)) {
                            P2pStateMachine p2pStateMachine15 = P2pStateMachine.this;
                            p2pStateMachine15.transitionTo(p2pStateMachine15.mUserAuthorizingInviteRequestState);
                        }
                        P2pStateMachine p2pStateMachine16 = P2pStateMachine.this;
                        p2pStateMachine16.transitionTo(p2pStateMachine16.mUserAuthorizingInviteRequestState);
                        return true;
                    case WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT:
                    case WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                    case WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                        if (message.obj == null) {
                            Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                            return true;
                        }
                        WifiP2pProvDiscEvent provDisc = (WifiP2pProvDiscEvent) message.obj;
                        WifiP2pDevice device = provDisc.device;
                        P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp = provDisc.device.candidateStaticIp;
                        if (device == null) {
                            P2pStateMachine.this.loge("Device entry is null");
                            return true;
                        }
                        P2pStateMachine.this.mSavedPeerConfig = new WifiP2pConfig();
                        P2pStateMachine.this.mSavedPeerConfig.deviceAddress = provDisc.device.deviceAddress;
                        if (message.what == 147491) {
                            P2pStateMachine.this.mSavedPeerConfig.wps.setup = 2;
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd("Keypad prov disc request");
                            }
                        } else if (message.what == 147492) {
                            P2pStateMachine.this.mSavedPeerConfig.wps.setup = 1;
                            P2pStateMachine.this.mSavedPeerConfig.wps.pin = provDisc.pin;
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd("Display prov disc request");
                            }
                            P2pStateMachine.this.notifyInvitationReceived();
                            P2pStateMachine p2pStateMachine17 = P2pStateMachine.this;
                            if (!p2pStateMachine17.sendConnectNoticeToApp(p2pStateMachine17.mPeers.get(P2pStateMachine.this.mSavedPeerConfig.deviceAddress), P2pStateMachine.this.mSavedPeerConfig)) {
                                P2pStateMachine p2pStateMachine18 = P2pStateMachine.this;
                                p2pStateMachine18.transitionTo(p2pStateMachine18.mUserAuthorizingNegotiationRequestState);
                            }
                        } else {
                            P2pStateMachine.this.mSavedPeerConfig.wps.setup = 0;
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd("PBC prov disc request");
                            }
                        }
                        WifiP2pDevice dev = P2pStateMachine.this.mPeers.get(P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                        if (dev != null) {
                            WifiP2pServiceImpl wifiP2pServiceImpl = WifiP2pServiceImpl.this;
                            if (dev.isGroupOwner() && provDisc.device.isGroupOwner()) {
                                z = true;
                            }
                            wifiP2pServiceImpl.mJoinExistingGroup = z;
                        }
                        if (!WifiP2pServiceImpl.this.mJoinExistingGroup) {
                            return true;
                        }
                        P2pStateMachine p2pStateMachine19 = P2pStateMachine.this;
                        if (p2pStateMachine19.sendConnectNoticeToApp(p2pStateMachine19.mPeers.get(P2pStateMachine.this.mSavedPeerConfig.deviceAddress), P2pStateMachine.this.mSavedPeerConfig)) {
                            return true;
                        }
                        P2pStateMachine p2pStateMachine20 = P2pStateMachine.this;
                        p2pStateMachine20.transitionTo(p2pStateMachine20.mUserAuthorizingInviteRequestState);
                        return true;
                    default:
                        return false;
                }
            }

            public void exit() {
                P2pStateMachine.this.mIsInactiveState = false;
            }
        }

        /* access modifiers changed from: package-private */
        public class GroupCreatingState extends State {
            GroupCreatingState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                p2pStateMachine.sendMessageDelayed(p2pStateMachine.obtainMessage(WifiP2pServiceImpl.GROUP_CREATING_TIMED_OUT, WifiP2pServiceImpl.access$16204(), 0), 120000);
            }

            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.logd(getName() + message.toString());
                }
                switch (message.what) {
                    case 139265:
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139266, 2);
                        return true;
                    case 139268:
                    case 139372:
                        return true;
                    case 139274:
                        P2pStateMachine.this.mWifiNative.p2pCancelConnect();
                        WifiP2pServiceImpl.this.mWifiP2pMetrics.endConnectionEvent(3);
                        P2pStateMachine.this.stopLegacyWifiScan(false);
                        if (P2pStateMachine.this.mSavedPeerConfig != null) {
                            P2pStateMachine.this.mWifiNative.p2pStopFind();
                            P2pStateMachine.this.mWifiNative.p2pReject(P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                            P2pStateMachine.this.mWifiNative.p2pProvisionDiscovery(P2pStateMachine.this.mSavedPeerConfig);
                        }
                        P2pStateMachine.this.handleGroupCreationFailure();
                        P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                        p2pStateMachine2.transitionTo(p2pStateMachine2.mInactiveState);
                        P2pStateMachine.this.replyToMessage(message, 139276);
                        return true;
                    case WifiP2pServiceImpl.GROUP_CREATING_TIMED_OUT /*{ENCODED_INT: 143361}*/:
                        if (WifiP2pServiceImpl.sGroupCreatingTimeoutIndex != message.arg1) {
                            return true;
                        }
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("Group negotiation timed out");
                        }
                        WifiP2pServiceImpl.this.mWifiP2pMetrics.endConnectionEvent(2);
                        P2pStateMachine.this.mWifiNative.p2pCancelConnect();
                        if (P2pStateMachine.this.mSavedPeerConfig != null) {
                            P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                            p2pStateMachine3.mConnectedDevAddr = p2pStateMachine3.mSavedPeerConfig.deviceAddress;
                        }
                        P2pStateMachine.this.handleGroupCreationFailure();
                        P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                        p2pStateMachine4.transitionTo(p2pStateMachine4.mInactiveState);
                        return true;
                    case WifiP2pMonitor.P2P_DEVICE_LOST_EVENT:
                        if (message.obj == null) {
                            Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                            return true;
                        }
                        WifiP2pDevice device = (WifiP2pDevice) message.obj;
                        if (P2pStateMachine.this.mSavedPeerConfig == null || P2pStateMachine.this.mSavedPeerConfig.deviceAddress.equals(device.deviceAddress)) {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                                p2pStateMachine5.logd("Add device to lost list " + device);
                            }
                            P2pStateMachine.this.mPeersLostDuringConnection.updateSupplicantDetails(device);
                            return true;
                        }
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                            p2pStateMachine6.logd("mSavedPeerConfig " + P2pStateMachine.this.mSavedPeerConfig.deviceAddress + "device " + device.deviceAddress);
                        }
                        return false;
                    case WifiP2pMonitor.P2P_GO_NEGOTIATION_SUCCESS_EVENT:
                        WifiP2pServiceImpl.this.mAutonomousGroup = false;
                        P2pStateMachine p2pStateMachine7 = P2pStateMachine.this;
                        p2pStateMachine7.transitionTo(p2pStateMachine7.mGroupNegotiationState);
                        return true;
                    case WifiP2pMonitor.P2P_FIND_STOPPED_EVENT:
                        P2pStateMachine.this.sendP2pDiscoveryChangedBroadcast(false);
                        if (!WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            return true;
                        }
                        P2pStateMachine.this.logd("skip resuming legacy wifi scan while group creating");
                        return true;
                    case 147527:
                        WifiP2pProvDiscEvent userReject = (WifiP2pProvDiscEvent) message.obj;
                        if (userReject != null && P2pStateMachine.this.mSavedPeerConfig != null && !userReject.device.deviceAddress.equals(P2pStateMachine.this.mSavedPeerConfig.deviceAddress)) {
                            return true;
                        }
                        if (WifiP2pServiceImpl.this.mInvitationDialog != null && WifiP2pServiceImpl.this.mInvitationDialog.isShowing()) {
                            WifiP2pServiceImpl.this.mWpsTimer.cancel();
                            WifiP2pServiceImpl.this.mInvitationDialog.dismiss();
                            WifiP2pServiceImpl.this.mInvitationDialog = null;
                        }
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("Peer cancelled connection establishment while creating group");
                        }
                        P2pStateMachine.this.mWifiNative.p2pCancelConnect();
                        P2pStateMachine.this.handleGroupCreationFailure();
                        P2pStateMachine p2pStateMachine8 = P2pStateMachine.this;
                        p2pStateMachine8.transitionTo(p2pStateMachine8.mInactiveState);
                        return true;
                    default:
                        return false;
                }
            }
        }

        /* access modifiers changed from: package-private */
        public class UserAuthorizingNegotiationRequestState extends State {
            UserAuthorizingNegotiationRequestState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                if (WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndListen) {
                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                        P2pStateMachine.this.logd("Accepted without notification since it's from advanced opp");
                    }
                    P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.PEER_CONNECTION_USER_ACCEPT);
                } else if (P2pStateMachine.this.mSavedPeerConfig.wps.setup == 0 || TextUtils.isEmpty(P2pStateMachine.this.mSavedPeerConfig.wps.pin)) {
                    P2pStateMachine.this.notifyInvitationReceived();
                    P2pStateMachine.this.soundNotification();
                }
            }

            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName() + message.toString());
                }
                boolean join = false;
                switch (message.what) {
                    case WifiP2pServiceImpl.PEER_CONNECTION_USER_ACCEPT /*{ENCODED_INT: 143362}*/:
                        if (P2pStateMachine.this.mSavedPeerConfig == null) {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd("Abort group creation since mSavedPeerConfig NULL");
                            }
                            P2pStateMachine.this.mWifiNative.p2pCancelConnect();
                            P2pStateMachine.this.handleGroupCreationFailure();
                            P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                            p2pStateMachine.transitionTo(p2pStateMachine.mInactiveState);
                            break;
                        } else {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd("Accept connection request");
                            }
                            P2pStateMachine.this.mWifiNative.p2pStopFind();
                            P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                            p2pStateMachine2.p2pConnectWithPinDisplay(p2pStateMachine2.mSavedPeerConfig);
                            P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mSavedPeerConfig.deviceAddress, 1);
                            P2pStateMachine.this.sendPeersChangedBroadcast();
                            if (P2pStateMachine.this.mSavedPeerConfig.wps.setup == 2) {
                                P2pStateMachine.this.sendP2pRequestChangedBroadcast(true);
                            }
                            P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                            p2pStateMachine3.transitionTo(p2pStateMachine3.mGroupNegotiationState);
                            break;
                        }
                    case WifiP2pServiceImpl.PEER_CONNECTION_USER_REJECT /*{ENCODED_INT: 143363}*/:
                        if (P2pStateMachine.this.mSavedPeerConfig != null) {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd("User rejected negotiation " + P2pStateMachine.this.mSavedPeerConfig);
                            }
                            P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                            WifiP2pDevice dev = p2pStateMachine4.fetchCurrentDeviceDetails(p2pStateMachine4.mSavedPeerConfig);
                            if ((dev != null && dev.isGroupOwner()) || WifiP2pServiceImpl.this.mJoinExistingGroup) {
                                join = true;
                            }
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd("User rejected negotiation, join =  " + join);
                            }
                            WifiP2pServiceImpl.this.userRejected = true;
                            if (join) {
                                P2pStateMachine.this.mWifiNative.p2pCancelConnect();
                                if (P2pStateMachine.this.mSavedPeerConfig != null) {
                                    P2pStateMachine.this.mWifiNative.p2pStopFind();
                                    P2pStateMachine.this.mWifiNative.p2pReject(P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                                    P2pStateMachine.this.mWifiNative.p2pProvisionDiscovery(P2pStateMachine.this.mSavedPeerConfig);
                                }
                                P2pStateMachine.this.mWifiNative.p2pFlush();
                                P2pStateMachine.this.mWifiNative.p2pFind();
                                P2pStateMachine.this.sendP2pConnectionChangedBroadcast();
                            } else {
                                P2pStateMachine.this.mWifiNative.p2pReject(P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                                P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                                p2pStateMachine5.p2pConnectWithPinDisplay(p2pStateMachine5.mSavedPeerConfig);
                            }
                            P2pStateMachine.this.mSavedPeerConfig = null;
                        } else {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd("User rejected negotiation - mSavedPeerConfig NULL");
                            }
                            P2pStateMachine.this.mWifiNative.p2pCancelConnect();
                            P2pStateMachine.this.handleGroupCreationFailure();
                        }
                        P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                        p2pStateMachine6.transitionTo(p2pStateMachine6.mInactiveState);
                        break;
                    case WifiP2pServiceImpl.PEER_CONNECTION_USER_CONFIRM /*{ENCODED_INT: 143367}*/:
                        P2pStateMachine.this.mSavedPeerConfig.wps.setup = 1;
                        P2pStateMachine.this.mWifiNative.p2pConnect(P2pStateMachine.this.mSavedPeerConfig, WifiP2pServiceImpl.FORM_GROUP.booleanValue());
                        P2pStateMachine p2pStateMachine7 = P2pStateMachine.this;
                        p2pStateMachine7.transitionTo(p2pStateMachine7.mGroupNegotiationState);
                        break;
                    default:
                        return false;
                }
                return true;
            }

            public void exit() {
            }
        }

        /* access modifiers changed from: package-private */
        public class UserAuthorizingInviteRequestState extends State {
            UserAuthorizingInviteRequestState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                if (WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndListen) {
                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                        P2pStateMachine.this.logd("Accepted without notification since it's from advanced opp");
                    }
                    P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.PEER_CONNECTION_USER_ACCEPT);
                    return;
                }
                P2pStateMachine.this.notifyInvitationReceived();
                P2pStateMachine.this.soundNotification();
            }

            /* JADX WARNING: Code restructure failed: missing block: B:13:0x0074, code lost:
                if (r1.reinvokePersistentGroup(r1.mSavedPeerConfig) == false) goto L_0x0076;
             */
            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.logd(getName() + message.toString());
                }
                switch (message.what) {
                    case WifiP2pServiceImpl.PEER_CONNECTION_USER_ACCEPT /*{ENCODED_INT: 143362}*/:
                        P2pStateMachine.this.mWifiNative.p2pStopFind();
                        if (P2pStateMachine.this.mSavedPeerConfig.netId != -1) {
                            P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                            break;
                        }
                        P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                        p2pStateMachine3.p2pConnectWithPinDisplay(p2pStateMachine3.mSavedPeerConfig);
                        P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mSavedPeerConfig.deviceAddress, 1);
                        P2pStateMachine.this.sendPeersChangedBroadcast();
                        P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                        p2pStateMachine4.transitionTo(p2pStateMachine4.mGroupNegotiationState);
                        break;
                    case WifiP2pServiceImpl.PEER_CONNECTION_USER_REJECT /*{ENCODED_INT: 143363}*/:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                            p2pStateMachine5.logd("User rejected invitation " + P2pStateMachine.this.mSavedPeerConfig);
                        }
                        P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                        p2pStateMachine6.transitionTo(p2pStateMachine6.mInactiveState);
                        break;
                    default:
                        return false;
                }
                return true;
            }

            public void exit() {
            }
        }

        /* access modifiers changed from: package-private */
        public class ProvisionDiscoveryState extends State {
            ProvisionDiscoveryState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                P2pStateMachine.this.mWifiNative.p2pProvisionDiscovery(P2pStateMachine.this.mSavedPeerConfig);
                P2pStateMachine.this.stopLegacyWifiScan(true);
            }

            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName() + message.toString());
                }
                int i = message.what;
                if (!(i == 139268 || i == 139372)) {
                    boolean z = false;
                    if (i != 147495) {
                        switch (i) {
                            case WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT:
                                WifiP2pProvDiscEvent provDisc = (WifiP2pProvDiscEvent) message.obj;
                                if (provDisc.device.deviceAddress.equals(P2pStateMachine.this.mSavedPeerConfig.deviceAddress)) {
                                    P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp = provDisc.device.candidateStaticIp;
                                    P2pStateMachine.this.mSavedPeerConfig.wps.setup = 0;
                                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                        P2pStateMachine.this.logd("PBC prov disc request");
                                    }
                                    WifiP2pDevice dev = P2pStateMachine.this.mPeers.get(P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                                    WifiP2pServiceImpl wifiP2pServiceImpl = WifiP2pServiceImpl.this;
                                    if (dev != null && dev.isGroupOwner() && provDisc.device.isGroupOwner()) {
                                        z = true;
                                    }
                                    wifiP2pServiceImpl.mJoinExistingGroup = z;
                                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                                    p2pStateMachine.p2pConnectWithPinDisplay(p2pStateMachine.mSavedPeerConfig);
                                    P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mSavedPeerConfig.deviceAddress, 1);
                                    P2pStateMachine.this.sendPeersChangedBroadcast();
                                    P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                                    p2pStateMachine2.transitionTo(p2pStateMachine2.mGroupNegotiationState);
                                    break;
                                }
                                break;
                            case WifiP2pMonitor.P2P_PROV_DISC_PBC_RSP_EVENT:
                                if (message.obj != null) {
                                    WifiP2pDevice device = ((WifiP2pProvDiscEvent) message.obj).device;
                                    if (device != null) {
                                        P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp = device.candidateStaticIp;
                                    }
                                    if ((device == null || device.deviceAddress.equals(P2pStateMachine.this.mSavedPeerConfig.deviceAddress)) && P2pStateMachine.this.mSavedPeerConfig.wps.setup == 0) {
                                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                            P2pStateMachine.this.logd("Found a match " + P2pStateMachine.this.mSavedPeerConfig);
                                        }
                                        P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                                        p2pStateMachine3.p2pConnectWithPinDisplay(p2pStateMachine3.mSavedPeerConfig);
                                        P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                                        p2pStateMachine4.transitionTo(p2pStateMachine4.mGroupNegotiationState);
                                        break;
                                    }
                                } else {
                                    Log.e(WifiP2pServiceImpl.TAG, "Invalid argument(s)");
                                    break;
                                }
                            case WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                                if (message.obj != null) {
                                    WifiP2pDevice device2 = ((WifiP2pProvDiscEvent) message.obj).device;
                                    if (device2 != null) {
                                        P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp = device2.candidateStaticIp;
                                    }
                                    if ((device2 == null || device2.deviceAddress.equals(P2pStateMachine.this.mSavedPeerConfig.deviceAddress)) && P2pStateMachine.this.mSavedPeerConfig.wps.setup == 2) {
                                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                            P2pStateMachine.this.logd("Found a match " + P2pStateMachine.this.mSavedPeerConfig);
                                        }
                                        if (TextUtils.isEmpty(P2pStateMachine.this.mSavedPeerConfig.wps.pin)) {
                                            WifiP2pServiceImpl.this.mJoinExistingGroup = false;
                                            P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                                            if (!p2pStateMachine5.sendConnectNoticeToApp(p2pStateMachine5.mPeers.get(P2pStateMachine.this.mSavedPeerConfig.deviceAddress), P2pStateMachine.this.mSavedPeerConfig)) {
                                                P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                                                p2pStateMachine6.transitionTo(p2pStateMachine6.mUserAuthorizingNegotiationRequestState);
                                                break;
                                            }
                                        } else {
                                            P2pStateMachine p2pStateMachine7 = P2pStateMachine.this;
                                            p2pStateMachine7.p2pConnectWithPinDisplay(p2pStateMachine7.mSavedPeerConfig);
                                            P2pStateMachine p2pStateMachine8 = P2pStateMachine.this;
                                            p2pStateMachine8.transitionTo(p2pStateMachine8.mGroupNegotiationState);
                                            break;
                                        }
                                    }
                                } else {
                                    Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                                    break;
                                }
                                break;
                            case WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                                if (message.obj != null) {
                                    WifiP2pProvDiscEvent provDisc2 = (WifiP2pProvDiscEvent) message.obj;
                                    WifiP2pDevice device3 = provDisc2.device;
                                    if (device3 != null) {
                                        P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp = provDisc2.device.candidateStaticIp;
                                        if (device3.deviceAddress.equals(P2pStateMachine.this.mSavedPeerConfig.deviceAddress) && P2pStateMachine.this.mSavedPeerConfig.wps.setup == 1) {
                                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                                P2pStateMachine.this.logd("Found a match " + P2pStateMachine.this.mSavedPeerConfig);
                                            }
                                            P2pStateMachine.this.mSavedPeerConfig.wps.pin = provDisc2.pin;
                                            P2pStateMachine p2pStateMachine9 = P2pStateMachine.this;
                                            p2pStateMachine9.p2pConnectWithPinDisplay(p2pStateMachine9.mSavedPeerConfig);
                                            P2pStateMachine.this.notifyInvitationSent(provDisc2.pin, device3.deviceAddress);
                                            P2pStateMachine p2pStateMachine10 = P2pStateMachine.this;
                                            p2pStateMachine10.transitionTo(p2pStateMachine10.mGroupNegotiationState);
                                            break;
                                        }
                                    } else {
                                        Log.e(WifiP2pServiceImpl.TAG, "Invalid device");
                                        break;
                                    }
                                } else {
                                    Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                                    break;
                                }
                            default:
                                return false;
                        }
                    } else {
                        P2pStateMachine.this.loge("provision discovery failed");
                        WifiP2pServiceImpl.this.mWifiP2pMetrics.endConnectionEvent(4);
                        P2pStateMachine.this.handleGroupCreationFailure();
                        P2pStateMachine p2pStateMachine11 = P2pStateMachine.this;
                        p2pStateMachine11.transitionTo(p2pStateMachine11.mInactiveState);
                        P2pStateMachine.this.stopLegacyWifiScan(false);
                    }
                }
                return true;
            }
        }

        /* access modifiers changed from: package-private */
        public class GroupNegotiationState extends State {
            GroupNegotiationState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                P2pStateMachine.this.stopLegacyWifiScan(true);
                P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.GROUP_CREATING_TIMED_OUT);
                P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                p2pStateMachine.sendMessageDelayed(p2pStateMachine.obtainMessage(WifiP2pServiceImpl.GROUP_CREATING_TIMED_OUT, WifiP2pServiceImpl.access$16204(), 0), 120000);
            }

            /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
            /* JADX WARNING: Removed duplicated region for block: B:123:0x04ff  */
            /* JADX WARNING: Removed duplicated region for block: B:126:0x0521  */
            /* JADX WARNING: Removed duplicated region for block: B:133:0x0563  */
            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.logd(getName() + message.toString());
                }
                int i = message.what;
                if (!(i == 139268 || i == 139372)) {
                    switch (i) {
                        case WifiP2pMonitor.P2P_GO_NEGOTIATION_SUCCESS_EVENT:
                            if (P2pStateMachine.this.mSavedPeerConfig != null) {
                                WifiP2pServiceImpl.this.connectRetryCount(P2pStateMachine.this.mSavedPeerConfig.deviceAddress, 1);
                            }
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                                p2pStateMachine2.logd(getName() + " go success");
                            }
                            P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.GROUP_CREATING_TIMED_OUT);
                            break;
                        case WifiP2pMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT:
                            P2pStatus status = (P2pStatus) message.obj;
                            if (status == P2pStatus.NO_COMMON_CHANNEL && WifiP2pServiceImpl.this.mValidFreqConflict) {
                                WifiP2pServiceImpl.this.mValidFreqConflict = false;
                                P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                                p2pStateMachine3.transitionTo(p2pStateMachine3.mFrequencyConflictState);
                                break;
                            } else {
                                if (status == P2pStatus.REJECTED_BY_USER) {
                                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                        P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                                        p2pStateMachine4.logd(getName() + " rejected by peer");
                                    }
                                    P2pStateMachine.this.sendP2pRequestChangedBroadcast(false);
                                }
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                                    p2pStateMachine5.logd(getName() + " go failure");
                                }
                                if (P2pStateMachine.this.mSavedPeerConfig != null) {
                                    P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                                    p2pStateMachine6.mConnectedDevAddr = p2pStateMachine6.mSavedPeerConfig.deviceAddress;
                                }
                                WifiP2pServiceImpl.this.mWifiP2pMetrics.endConnectionEvent(0);
                                P2pStateMachine.this.handleGroupCreationFailure();
                                P2pStateMachine p2pStateMachine7 = P2pStateMachine.this;
                                p2pStateMachine7.transitionTo(p2pStateMachine7.mInactiveState);
                                break;
                            }
                            break;
                        case WifiP2pMonitor.P2P_GROUP_FORMATION_SUCCESS_EVENT:
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            }
                            P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.GROUP_CREATING_TIMED_OUT);
                            break;
                        case WifiP2pMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT:
                            if (((P2pStatus) message.obj) == P2pStatus.NO_COMMON_CHANNEL) {
                                P2pStateMachine p2pStateMachine8 = P2pStateMachine.this;
                                p2pStateMachine8.transitionTo(p2pStateMachine8.mFrequencyConflictState);
                                break;
                            }
                            break;
                        case WifiP2pMonitor.P2P_GROUP_STARTED_EVENT:
                            if (message.obj != null) {
                                P2pStateMachine.this.mGroup = (WifiP2pGroup) message.obj;
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    P2pStateMachine p2pStateMachine9 = P2pStateMachine.this;
                                    p2pStateMachine9.logd(getName() + " group started");
                                }
                                if (P2pStateMachine.this.mGroup.isGroupOwner() && WifiP2pServiceImpl.EMPTY_DEVICE_ADDRESS.equals(P2pStateMachine.this.mGroup.getOwner().deviceAddress)) {
                                    P2pStateMachine.this.mGroup.getOwner().deviceAddress = WifiP2pServiceImpl.this.mThisDevice.deviceAddress;
                                }
                                WifiP2pServiceImpl.this.mPersistentGroup = false;
                                if (P2pStateMachine.this.mGroup.getNetworkId() == -2) {
                                    P2pStateMachine.this.updatePersistentNetworks(WifiP2pServiceImpl.RELOAD.booleanValue());
                                    P2pStateMachine.this.mGroup.setNetworkId(P2pStateMachine.this.mGroups.getNetworkId(P2pStateMachine.this.mGroup.getOwner().deviceAddress, P2pStateMachine.this.mGroup.getNetworkName()));
                                    WifiP2pServiceImpl.this.mPersistentGroup = true;
                                }
                                if (!P2pStateMachine.this.mGroup.mStaticIp.isEmpty()) {
                                    P2pStateMachine p2pStateMachine10 = P2pStateMachine.this;
                                    p2pStateMachine10.logd("set staticIP from EAPOL-Key " + P2pStateMachine.this.mGroup.mStaticIp);
                                    P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp = NetworkUtils.inetAddressToInt((Inet4Address) NetworkUtils.numericToInetAddress(P2pStateMachine.this.mGroup.mStaticIp));
                                } else if (P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp != 0 && !P2pStateMachine.this.mGroup.mGroupOwnerStaticIp.isEmpty()) {
                                    P2pStateMachine.this.mGroup.mGroupOwnerStaticIp = "";
                                }
                                if (P2pStateMachine.this.mGroup.isGroupOwner()) {
                                    if (!WifiP2pServiceImpl.this.mAutonomousGroup) {
                                        P2pStateMachine.this.mWifiNative.setP2pGroupIdle(P2pStateMachine.this.mGroup.getInterface(), 10);
                                    }
                                    if (WifiP2pServiceImpl.this.mThisDevice != null) {
                                        P2pStateMachine.this.mGroup.getOwner().update(WifiP2pServiceImpl.this.mThisDevice);
                                    }
                                    P2pStateMachine p2pStateMachine11 = P2pStateMachine.this;
                                    p2pStateMachine11.startDhcpServer(p2pStateMachine11.mGroup.getInterface());
                                } else if (P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp != 0) {
                                    String intf = P2pStateMachine.this.mGroup.getInterface();
                                    try {
                                        InterfaceConfiguration ifcg = WifiP2pServiceImpl.this.mNwService.getInterfaceConfig(intf);
                                        if (ifcg != null) {
                                            ifcg.setLinkAddress(new LinkAddress(NetworkUtils.intToInetAddress(P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp), 24));
                                            ifcg.setInterfaceUp();
                                            WifiP2pServiceImpl.this.mNwService.setInterfaceConfig(intf, ifcg);
                                            List<RouteInfo> routes = new ArrayList<>();
                                            routes.add(new RouteInfo(ifcg.getLinkAddress(), null, intf));
                                            WifiP2pServiceImpl.this.mNwService.addInterfaceToLocalNetwork(intf, routes);
                                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                                P2pStateMachine p2pStateMachine12 = P2pStateMachine.this;
                                                p2pStateMachine12.logd("Static IP configuration succeeded " + intf);
                                            }
                                        }
                                    } catch (Exception e) {
                                        P2pStateMachine p2pStateMachine13 = P2pStateMachine.this;
                                        p2pStateMachine13.loge("Error configuring interface " + intf + ", :" + e);
                                    }
                                    P2pStateMachine.this.mWifiNative.setP2pGroupIdle(P2pStateMachine.this.mGroup.getInterface(), 10);
                                    WifiP2pDevice groupOwner = P2pStateMachine.this.mGroup.getOwner();
                                    WifiP2pDevice peer = P2pStateMachine.this.mPeers.get(groupOwner.deviceAddress);
                                    if (peer != null) {
                                        groupOwner.updateSupplicantDetails(peer);
                                        P2pStateMachine.this.sendPeersChangedBroadcast();
                                    } else {
                                        P2pStateMachine p2pStateMachine14 = P2pStateMachine.this;
                                        p2pStateMachine14.logw("Unknown group owner " + groupOwner);
                                    }
                                } else {
                                    P2pStateMachine.this.mWifiNative.setP2pGroupIdle(P2pStateMachine.this.mGroup.getInterface(), 10);
                                    P2pStateMachine.this.mWifiNative.setP2pPowerSave(P2pStateMachine.this.mGroup.getInterface(), false);
                                    WifiP2pServiceImpl.this.startIpClient(P2pStateMachine.this.mGroup.getInterface(), P2pStateMachine.this.getHandler());
                                    WifiP2pDevice groupOwner2 = P2pStateMachine.this.mGroup.getOwner();
                                    WifiP2pDevice peer2 = P2pStateMachine.this.mPeers.get(groupOwner2.deviceAddress);
                                    if (peer2 != null) {
                                        groupOwner2.updateSupplicantDetails(peer2);
                                        P2pStateMachine.this.sendPeersChangedBroadcast();
                                    } else {
                                        P2pStateMachine p2pStateMachine15 = P2pStateMachine.this;
                                        p2pStateMachine15.logw("Unknown group owner " + groupOwner2);
                                    }
                                }
                                P2pStateMachine.this.mSavedPeerConfig = null;
                                P2pStateMachine p2pStateMachine16 = P2pStateMachine.this;
                                p2pStateMachine16.transitionTo(p2pStateMachine16.mGroupCreatedState);
                                break;
                            } else {
                                Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                                break;
                            }
                        case WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT:
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            }
                            if (P2pStateMachine.this.mSavedPeerConfig != null) {
                            }
                            WifiP2pServiceImpl.this.mWifiP2pMetrics.endConnectionEvent(0);
                            P2pStateMachine.this.handleGroupCreationFailure();
                            P2pStateMachine p2pStateMachine72 = P2pStateMachine.this;
                            p2pStateMachine72.transitionTo(p2pStateMachine72.mInactiveState);
                            break;
                        case WifiP2pMonitor.P2P_INVITATION_RECEIVED_EVENT:
                            WifiP2pGroup group = (WifiP2pGroup) message.obj;
                            WifiP2pDevice owner = group.getOwner();
                            if (P2pStateMachine.this.mSavedPeerConfig != null) {
                                WifiP2pDevice dev = P2pStateMachine.this.mPeers.get(P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                                if (dev == null) {
                                    dev = new WifiP2pDevice(P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                                }
                                if (owner == null) {
                                    int id = group.getNetworkId();
                                    if (id < 0) {
                                        P2pStateMachine.this.loge("Ignored invitation from null owner");
                                        return false;
                                    }
                                    String addr = P2pStateMachine.this.mGroups.getOwnerAddr(id);
                                    if (addr != null) {
                                        group.setOwner(new WifiP2pDevice(addr));
                                        group.getOwner();
                                    } else {
                                        P2pStateMachine.this.loge("Ignored invitation from null owner");
                                        return false;
                                    }
                                }
                                if (group.contains(dev)) {
                                    WifiP2pConfig config = new WifiP2pConfig();
                                    config.deviceAddress = group.getOwner().deviceAddress;
                                    config.fw_dev = dev.deviceAddress;
                                    P2pStateMachine.this.mSelectedP2pGroupAddress = config.deviceAddress;
                                    if (!TextUtils.isEmpty(config.deviceAddress)) {
                                        WifiP2pServiceImpl.this.mAutonomousGroup = false;
                                        WifiP2pServiceImpl.this.mJoinExistingGroup = true;
                                        P2pStateMachine.this.mSavedPeerConfig = config;
                                        P2pStateMachine p2pStateMachine17 = P2pStateMachine.this;
                                        p2pStateMachine17.p2pConnectWithPinDisplay(p2pStateMachine17.mSavedPeerConfig);
                                        break;
                                    } else {
                                        P2pStateMachine p2pStateMachine18 = P2pStateMachine.this;
                                        p2pStateMachine18.loge("Dropping invitation request " + config);
                                        break;
                                    }
                                } else {
                                    P2pStateMachine.this.loge("Ignored invitation during GO Negociation with other device.");
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        case WifiP2pMonitor.P2P_INVITATION_RESULT_EVENT:
                            P2pStatus status2 = (P2pStatus) message.obj;
                            if (status2 != P2pStatus.SUCCESS) {
                                P2pStateMachine p2pStateMachine19 = P2pStateMachine.this;
                                p2pStateMachine19.loge("Invitation result " + status2);
                                if (status2 != P2pStatus.UNKNOWN_P2P_GROUP) {
                                    if (status2 != P2pStatus.INFORMATION_IS_CURRENTLY_UNAVAILABLE) {
                                        if (status2 != P2pStatus.NO_COMMON_CHANNEL) {
                                            WifiP2pServiceImpl.this.mWifiP2pMetrics.endConnectionEvent(5);
                                            P2pStateMachine.this.handleGroupCreationFailure();
                                            P2pStateMachine p2pStateMachine20 = P2pStateMachine.this;
                                            p2pStateMachine20.transitionTo(p2pStateMachine20.mInactiveState);
                                            break;
                                        } else {
                                            P2pStateMachine p2pStateMachine21 = P2pStateMachine.this;
                                            p2pStateMachine21.transitionTo(p2pStateMachine21.mFrequencyConflictState);
                                            break;
                                        }
                                    } else {
                                        P2pStateMachine.this.mSavedPeerConfig.netId = -2;
                                        P2pStateMachine p2pStateMachine22 = P2pStateMachine.this;
                                        p2pStateMachine22.p2pConnectWithPinDisplay(p2pStateMachine22.mSavedPeerConfig);
                                        break;
                                    }
                                } else if (P2pStateMachine.this.mSavedPeerConfig != null) {
                                    int netId = P2pStateMachine.this.mSavedPeerConfig.netId;
                                    if (netId >= 0) {
                                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                            P2pStateMachine.this.logd("Remove unknown client from the list");
                                        }
                                        P2pStateMachine p2pStateMachine23 = P2pStateMachine.this;
                                        p2pStateMachine23.removeClientFromList(netId, p2pStateMachine23.mSavedPeerConfig.deviceAddress, true);
                                    }
                                    P2pStateMachine.this.mSavedPeerConfig.netId = -2;
                                    P2pStateMachine p2pStateMachine24 = P2pStateMachine.this;
                                    p2pStateMachine24.p2pConnectWithPinDisplay(p2pStateMachine24.mSavedPeerConfig);
                                    break;
                                }
                            }
                            break;
                        default:
                            switch (i) {
                                case WifiP2pMonitor.P2P_PROV_DISC_PBC_RSP_EVENT:
                                case WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                                case WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                                    P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp = ((WifiP2pProvDiscEvent) message.obj).device.candidateStaticIp;
                                    break;
                                case WifiP2pMonitor.P2P_FIND_STOPPED_EVENT:
                                    break;
                                default:
                                    return false;
                            }
                    }
                }
                return true;
            }

            public void exit() {
                if (P2pStateMachine.this.mGroup != null) {
                    if (P2pStateMachine.this.mGroup.isGroupOwner()) {
                        return;
                    }
                    if (WifiP2pServiceImpl.this.mThisDevice.wfdInfo != null && WifiP2pServiceImpl.this.mThisDevice.wfdInfo.isWfdEnabled()) {
                        return;
                    }
                }
                if (!WifiP2pServiceImpl.this.mAdvancedOppInProgress) {
                    P2pStateMachine.this.stopLegacyWifiScan(false);
                }
            }
        }

        /* access modifiers changed from: package-private */
        public class FrequencyConflictState extends State {
            private AlertDialog mFrequencyConflictDialog;

            FrequencyConflictState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                notifyFrequencyConflict();
            }

            private void notifyFrequencyConflict() {
                P2pStateMachine.this.logd("Notify frequency conflict");
                Resources r = Resources.getSystem();
                if (!WifiP2pServiceImpl.this.mCm.getNetworkInfo(1).isConnected()) {
                    P2pStateMachine.this.showNoCommonChannelsDialog();
                    if (P2pStateMachine.this.mSavedPeerConfig != null) {
                        P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mSavedPeerConfig.deviceAddress, 2);
                        P2pStateMachine.this.mSavedPeerConfig = null;
                    }
                    P2pStateMachine.this.handleGroupCreationFailure();
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.transitionTo(p2pStateMachine.mInactiveState);
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(WifiP2pServiceImpl.this.mContext);
                P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                AlertDialog dialog = builder.setMessage(r.getString(17042725, p2pStateMachine2.getDeviceName(p2pStateMachine2.mSavedPeerConfig.deviceAddress))).setPositiveButton(r.getString(17040166), new DialogInterface.OnClickListener() {
                    /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.FrequencyConflictState.DialogInterface$OnClickListenerC05463 */

                    public void onClick(DialogInterface dialog, int which) {
                        P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.DROP_WIFI_USER_ACCEPT);
                    }
                }).setNegativeButton(r.getString(17040084), new DialogInterface.OnClickListener() {
                    /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.FrequencyConflictState.DialogInterface$OnClickListenerC05452 */

                    public void onClick(DialogInterface dialog, int which) {
                        P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.DROP_WIFI_USER_REJECT);
                    }
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                    /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.FrequencyConflictState.DialogInterface$OnCancelListenerC05441 */

                    public void onCancel(DialogInterface arg0) {
                        P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.DROP_WIFI_USER_REJECT);
                    }
                }).create();
                dialog.setCanceledOnTouchOutside(false);
                dialog.getWindow().setType(2003);
                WindowManager.LayoutParams attrs = dialog.getWindow().getAttributes();
                attrs.privateFlags = 16;
                dialog.getWindow().setAttributes(attrs);
                dialog.show();
                this.mFrequencyConflictDialog = dialog;
            }

            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.logd(getName() + message.toString());
                }
                int i = message.what;
                if (i != 143373) {
                    switch (i) {
                        case WifiP2pServiceImpl.DROP_WIFI_USER_ACCEPT /*{ENCODED_INT: 143364}*/:
                            if (WifiP2pServiceImpl.this.mWifiChannel != null) {
                                WifiP2pServiceImpl.this.mWifiChannel.sendMessage((int) WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST, 1);
                            } else {
                                P2pStateMachine.this.loge("DROP_WIFI_USER_ACCEPT message received when WifiChannel is null");
                            }
                            WifiP2pServiceImpl.this.mTemporarilyDisconnectedWifi = true;
                            break;
                        case WifiP2pServiceImpl.DROP_WIFI_USER_REJECT /*{ENCODED_INT: 143365}*/:
                            if (P2pStateMachine.this.mSavedPeerConfig != null) {
                                P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mSavedPeerConfig.deviceAddress, 2);
                                P2pStateMachine.this.mSavedPeerConfig = null;
                            }
                            WifiP2pServiceImpl.this.mWifiP2pMetrics.endConnectionEvent(6);
                            P2pStateMachine.this.handleGroupCreationFailure();
                            P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                            p2pStateMachine2.transitionTo(p2pStateMachine2.mInactiveState);
                            break;
                        default:
                            switch (i) {
                                case WifiP2pMonitor.P2P_GO_NEGOTIATION_SUCCESS_EVENT:
                                case WifiP2pMonitor.P2P_GROUP_FORMATION_SUCCESS_EVENT:
                                    P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                                    p2pStateMachine3.loge(getName() + "group sucess during freq conflict!");
                                    break;
                                case WifiP2pMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT:
                                case WifiP2pMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT:
                                case WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT:
                                    break;
                                case WifiP2pMonitor.P2P_GROUP_STARTED_EVENT:
                                    P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                                    p2pStateMachine4.loge(getName() + "group started after freq conflict, handle anyway");
                                    P2pStateMachine.this.deferMessage(message);
                                    P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                                    p2pStateMachine5.transitionTo(p2pStateMachine5.mGroupNegotiationState);
                                    break;
                                default:
                                    return false;
                            }
                    }
                } else {
                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                        P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                        p2pStateMachine6.logd(getName() + "Wifi disconnected, retry p2p");
                    }
                    WifiP2pDevice dev = P2pStateMachine.this.mPeers.get(P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                    if (dev == null || dev.status != 1) {
                        P2pStateMachine p2pStateMachine7 = P2pStateMachine.this;
                        p2pStateMachine7.transitionTo(p2pStateMachine7.mInactiveState);
                        P2pStateMachine p2pStateMachine8 = P2pStateMachine.this;
                        p2pStateMachine8.sendMessage(139271, p2pStateMachine8.mSavedPeerConfig);
                    } else {
                        P2pStateMachine p2pStateMachine9 = P2pStateMachine.this;
                        p2pStateMachine9.p2pConnectWithPinDisplay(p2pStateMachine9.mSavedPeerConfig);
                        P2pStateMachine.this.sendPeersChangedBroadcast();
                        P2pStateMachine p2pStateMachine10 = P2pStateMachine.this;
                        p2pStateMachine10.transitionTo(p2pStateMachine10.mGroupNegotiationState);
                    }
                }
                return true;
            }

            public void exit() {
                AlertDialog alertDialog = this.mFrequencyConflictDialog;
                if (alertDialog != null) {
                    alertDialog.dismiss();
                }
            }
        }

        /* access modifiers changed from: package-private */
        public class GroupCreatedState extends State {
            GroupCreatedState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                WifiManager tWifiManager = (WifiManager) WifiP2pServiceImpl.this.mContext.getSystemService("wifi");
                if (tWifiManager != null && WifiP2pServiceImpl.this.mWiFiLock == null) {
                    WifiP2pServiceImpl.this.mWiFiLock = tWifiManager.createWifiLock(WifiP2pServiceImpl.TAG);
                }
                if (!P2pStateMachine.this.mIsGotoJoinState) {
                    WifiP2pServiceImpl.this.mWiFiLock.acquire();
                }
                if (P2pStateMachine.this.mIsGotoJoinState) {
                    P2pStateMachine.this.mIsGotoJoinState = false;
                } else if (WifiP2pServiceImpl.this.mAdvancedOppReceiver) {
                    P2pStateMachine.this.sendMessage(139265);
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.sendMessageDelayed(p2pStateMachine.obtainMessage(WifiP2pServiceImpl.P2P_ADVOPP_DISCOVER_PEER, WifiP2pServiceImpl.access$18508(WifiP2pServiceImpl.this)), 3000);
                } else if (WifiP2pServiceImpl.this.mAdvancedOppSender) {
                    P2pStateMachine.this.sendMessageDelayed(WifiP2pServiceImpl.P2P_ADVOPP_DELAYED_DISCOVER_PEER, 10000);
                } else {
                    if (P2pStateMachine.this.mSavedPeerConfig != null) {
                        P2pStateMachine.this.mSavedPeerConfig.invalidate();
                    }
                    if (!P2pStateMachine.this.mGroup.isGroupOwner() || WifiP2pServiceImpl.this.mAutonomousGroup) {
                        WifiP2pServiceImpl.this.mNetworkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, null, null);
                        P2pStateMachine.this.updateThisDevice(0);
                        P2pStateMachine.this.mP2pState = 3;
                    }
                    if (P2pStateMachine.this.mGroup.isGroupOwner()) {
                        P2pStateMachine.this.setWifiP2pInfoOnGroupFormation(NetworkUtils.numericToInetAddress(WifiP2pServiceImpl.SERVER_ADDRESS));
                        if (!WifiP2pServiceImpl.this.mPersistentGroup) {
                            P2pStateMachine.this.sendMessageDelayed(WifiP2pServiceImpl.P2P_GROUP_STARTED_TIMED_OUT, 300000);
                        } else {
                            WifiP2pServiceImpl.this.mPersistentGroup = false;
                        }
                    } else if (P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp != 0) {
                        if (P2pStateMachine.this.mGroup.mGroupOwnerStaticIp.isEmpty()) {
                            P2pStateMachine.this.mGroup.mGroupOwnerStaticIp = NetworkUtils.intToInetAddress((P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp | 16777216) & 33554431).getHostAddress();
                        }
                        P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                        p2pStateMachine2.mConnectedDevAddr = p2pStateMachine2.mGroup.getOwner().deviceAddress;
                        P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                        p2pStateMachine3.mConnectedDevIntfAddr = p2pStateMachine3.mGroup.getOwner().interfaceAddress;
                        P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mGroup.getOwner().deviceAddress, 0);
                        WifiP2pServiceImpl.this.connectRetryCount(P2pStateMachine.this.mConnectedDevAddr, 0);
                        P2pStateMachine.this.sendPeersChangedBroadcast();
                        P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                        p2pStateMachine4.setWifiP2pInfoOnGroupFormation(NetworkUtils.numericToInetAddress(p2pStateMachine4.mGroup.mGroupOwnerStaticIp));
                        P2pStateMachine.this.mP2pStaticIpConfig.isStaticIp = true;
                        WifiP2pServiceImpl.this.mConnectedDevicesCnt = 1;
                        P2pStateMachine.this.showNotification();
                        P2pStateMachine.this.sendP2pConnectionChangedBroadcast();
                        P2pStateMachine.this.mWifiNative.setP2pPowerSave(P2pStateMachine.this.mGroup.getInterface(), true);
                    }
                    if (WifiP2pServiceImpl.this.mAutonomousGroup) {
                        P2pStateMachine.this.sendP2pConnectionChangedBroadcast();
                    }
                    P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.INVITATION_PROCEDURE_TIMED_OUT);
                    P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.GROUP_CREATING_TIMED_OUT);
                    P2pStateMachine.this.sendP2pDiscoveryChangedBroadcast(false);
                    WifiP2pServiceImpl.this.mMaxClientCnt = 0;
                    P2pStateMachine.this.sendMessageDelayed(WifiP2pServiceImpl.TIME_ELAPSED_AFTER_CONNECTED, 300000);
                }
                boolean unused = WifiP2pServiceImpl.mWpsSkip = false;
                P2pStateMachine.this.addP2pPktLogFilter();
                WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndListen = false;
                WifiP2pServiceImpl.this.mWifiP2pMetrics.endConnectionEvent(1);
                WifiP2pServiceImpl.this.mWifiP2pMetrics.startGroupEvent(P2pStateMachine.this.mGroup);
                P2pStateMachine.this.addP2pPktLogFilter();
                WifiP2pServiceImpl.access$19608(WifiP2pServiceImpl.this);
                P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                p2pStateMachine5.sendMessage(WifiP2pServiceImpl.P2P_TRAFFIC_POLL, WifiP2pServiceImpl.this.mTrafficPollToken, 0);
            }

            /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
            public boolean processMessage(Message message) {
                String manufacturer;
                boolean ret;
                int i;
                int netId;
                String pkgName;
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName() + message.toString());
                }
                boolean z = false;
                switch (message.what) {
                    case 139268:
                    case 139372:
                        return true;
                    case 139271:
                        if (!WifiP2pServiceImpl.this.isAllowWifiDirectByEDM()) {
                            P2pStateMachine.this.replyToMessage(message, 139272);
                            return true;
                        }
                        if (P2pStateMachine.this.mGroup.isGroupOwner() && P2pStateMachine.this.mGroup.isClientListEmpty()) {
                            P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.P2P_GROUP_STARTED_TIMED_OUT);
                            P2pStateMachine.this.sendMessageDelayed(WifiP2pServiceImpl.P2P_GROUP_STARTED_TIMED_OUT, 300000);
                        }
                        if (!WifiP2pServiceImpl.this.mAdvancedOppSender && !WifiP2pServiceImpl.this.mAdvancedOppReceiver && !WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkCanAccessWifiDirect(P2pStateMachine.this.getCallingPkgName(message.sendingUid, message.replyTo), message.sendingUid, false)) {
                            P2pStateMachine.this.replyToMessage(message, 139272);
                            return true;
                        } else if (P2pStateMachine.this.mSelectP2pConfigList != null) {
                            WifiP2pConfig config = P2pStateMachine.this.mSelectP2pConfigList.getConfigIndex(P2pStateMachine.this.mSelectP2pConfigIndex);
                            if (config != null) {
                                P2pStateMachine.this.logd("Inviting device : " + config.deviceAddress);
                                if (P2pStateMachine.this.mWifiNative.p2pInvite(P2pStateMachine.this.mGroup, config.deviceAddress)) {
                                    P2pStateMachine.this.mPeers.updateStatus(config.deviceAddress, 1);
                                    P2pStateMachine.this.sendPeersChangedBroadcast();
                                    P2pStateMachine.this.mSelectedP2pGroupAddress = config.deviceAddress;
                                    WifiP2pServiceImpl.this.mReinvokePersistentNetId = -1;
                                }
                            }
                            if (config != null) {
                                return true;
                            }
                            P2pStateMachine.this.stopLegacyWifiScan(false);
                            return true;
                        } else {
                            Bundle bundle = message.getData();
                            WifiP2pConfig config2 = (WifiP2pConfig) bundle.getParcelable("wifiP2pConfig");
                            String pkgName2 = bundle.getString("appPkgName");
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd(getName() + " package to call connect -> " + pkgName2);
                            }
                            if (P2pStateMachine.this.isConfigInvalid(config2)) {
                                P2pStateMachine.this.loge("Dropping connect requeset " + config2);
                                P2pStateMachine.this.replyToMessage(message, 139272);
                                return true;
                            }
                            WifiP2pDevice peerDev = P2pStateMachine.this.fetchCurrentDeviceDetails(config2);
                            if (!"com.android.bluetooth".equals(pkgName2) || !WifiP2pServiceImpl.this.mAdvancedOppReceiver) {
                                manufacturer = null;
                            } else {
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    P2pStateMachine.this.logd("Connection establishment for Advanced OPP");
                                }
                                P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.P2P_ADVOPP_DISCOVER_PEER);
                                P2pStateMachine.this.mSavedPeerConfig = config2;
                                if (peerDev == null || !peerDev.isGroupOwner()) {
                                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                        P2pStateMachine.this.logd("Inviting normal peer");
                                    }
                                    WifiP2pServiceImpl.this.mConnReqInfo.set(peerDev, "com.android.bluetooth.advopp", 0, 0, 1, P2pStateMachine.this.mWifiNative.p2pGetManufacturer(P2pStateMachine.this.mSavedPeerConfig.deviceAddress), P2pStateMachine.this.mWifiNative.p2pGetDeviceType(P2pStateMachine.this.mSavedPeerConfig.deviceAddress));
                                    manufacturer = 1;
                                } else {
                                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                        P2pStateMachine.this.logd("Peer is group owner operating @ freq : " + P2pStateMachine.this.mWifiNative.p2pGetListenFreq(peerDev.deviceAddress));
                                    }
                                    WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndJoin = true;
                                    P2pStateMachine.this.sendMessage(139280);
                                    return true;
                                }
                            }
                            if (!(manufacturer == null && config2.deviceAddress == null) && (P2pStateMachine.this.mSavedProvDiscDevice == null || !P2pStateMachine.this.mSavedProvDiscDevice.deviceAddress.equals(config2.deviceAddress))) {
                                P2pStateMachine.this.logd("Inviting device : " + config2.deviceAddress);
                                P2pStateMachine.this.mSavedPeerConfig = config2;
                                if (P2pStateMachine.this.mWifiNative.p2pInvite(P2pStateMachine.this.mGroup, config2.deviceAddress)) {
                                    P2pStateMachine.this.mPeers.updateStatus(config2.deviceAddress, 1);
                                    P2pStateMachine.this.sendPeersChangedBroadcast();
                                    P2pStateMachine.this.mSelectedP2pGroupAddress = config2.deviceAddress;
                                    P2pStateMachine.this.replyToMessage(message, 139273);
                                    WifiP2pServiceImpl.this.auditLog(true, "Connecting to device address " + config2.deviceAddress + " using Wi-Fi Direct (P2P) succeeded");
                                    return true;
                                }
                                P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139272, 0);
                                WifiP2pServiceImpl.this.auditLog(false, "Connecting to device address " + config2.deviceAddress + " using Wi-Fi Direct (P2P) failed");
                                return true;
                            }
                            if (config2.wps.setup == 0) {
                                P2pStateMachine.this.mWifiNative.startWpsPbc(P2pStateMachine.this.mGroup.getInterface(), null);
                            } else if (config2.wps.pin == null) {
                                String pin = P2pStateMachine.this.mWifiNative.startWpsPinDisplay(P2pStateMachine.this.mGroup.getInterface(), null);
                                try {
                                    Integer.parseInt(pin);
                                    if (!P2pStateMachine.this.sendShowPinReqToFrontApp(pin)) {
                                        P2pStateMachine.this.notifyInvitationSent(pin, config2.deviceAddress != null ? config2.deviceAddress : "any");
                                    }
                                } catch (NumberFormatException e) {
                                }
                            } else {
                                P2pStateMachine.this.mWifiNative.startWpsPinKeypad(P2pStateMachine.this.mGroup.getInterface(), config2.wps.pin);
                            }
                            if (config2.deviceAddress != null) {
                                P2pStateMachine.this.mPeers.updateStatus(config2.deviceAddress, 1);
                                P2pStateMachine.this.sendPeersChangedBroadcast();
                            }
                            P2pStateMachine.this.replyToMessage(message, 139273);
                            return true;
                        }
                    case 139274:
                        if (!P2pStateMachine.this.mGroup.isGroupOwner()) {
                            return false;
                        }
                        P2pStateMachine.this.logd("We will do CANCEL_CONNECT if GO has no client");
                        if (P2pStateMachine.this.mGroup.isClientListEmpty()) {
                            P2pStateMachine.this.replyToMessage(message, 139276);
                            break;
                        } else {
                            return false;
                        }
                    case 139280:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(getName() + " remove group");
                        }
                        if (P2pStateMachine.this.mWifiNative.p2pGroupRemove(P2pStateMachine.this.mGroup.getInterface())) {
                            P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                            p2pStateMachine.transitionTo(p2pStateMachine.mOngoingGroupRemovalState);
                            P2pStateMachine.this.replyToMessage(message, 139282);
                            return true;
                        }
                        P2pStateMachine.this.handleGroupRemoved();
                        P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                        p2pStateMachine2.transitionTo(p2pStateMachine2.mInactiveState);
                        P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139281, 0);
                        return true;
                    case 139326:
                        WpsInfo wps = (WpsInfo) message.obj;
                        if (wps == null) {
                            P2pStateMachine.this.replyToMessage(message, 139327);
                            return true;
                        }
                        if (wps.setup == 0) {
                            ret = P2pStateMachine.this.mWifiNative.startWpsPbc(P2pStateMachine.this.mGroup.getInterface(), null);
                        } else if (wps.pin == null) {
                            String pin2 = P2pStateMachine.this.mWifiNative.startWpsPinDisplay(P2pStateMachine.this.mGroup.getInterface(), null);
                            try {
                                Integer.parseInt(pin2);
                                P2pStateMachine.this.notifyInvitationSent(pin2, "any");
                                ret = true;
                            } catch (NumberFormatException e2) {
                                ret = false;
                            }
                        } else {
                            ret = P2pStateMachine.this.mWifiNative.startWpsPinKeypad(P2pStateMachine.this.mGroup.getInterface(), wps.pin);
                        }
                        P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                        if (ret) {
                            i = 139328;
                        } else {
                            i = 139327;
                        }
                        p2pStateMachine3.replyToMessage(message, i);
                        return true;
                    case 139371:
                        int timeout = message.arg1;
                        if (P2pStateMachine.this.mPeers.clear()) {
                            P2pStateMachine.this.sendPeersChangedBroadcast();
                        }
                        P2pStateMachine.this.mWifiNative.p2pFlush();
                        P2pStateMachine.this.mPeers.updateSupplicantDetails(P2pStateMachine.this.mGroup.getOwner());
                        P2pStateMachine.this.stopLegacyWifiPeriodicScan(true);
                        if (timeout == -999) {
                            P2pStateMachine.this.mWifiNative.p2pFind(5, -999);
                        } else {
                            P2pStateMachine.this.mWifiNative.p2pFind(timeout);
                        }
                        P2pStateMachine.this.replyToMessage(message, 139267);
                        return true;
                    case 139374:
                        P2pStateMachine.this.mSelectP2pConfigList = (WifiP2pConfigList) message.obj;
                        P2pStateMachine.this.mSelectP2pConfigIndex = 0;
                        Iterator it = P2pStateMachine.this.mSelectP2pConfigList.getConfigList().iterator();
                        while (it.hasNext()) {
                            P2pStateMachine.this.loge("device :" + ((WifiP2pConfig) it.next()).deviceAddress);
                        }
                        P2pStateMachine.this.replyToMessage(message, 139273);
                        if (P2pStateMachine.this.mSelectP2pConfigList.getConfigIndex(P2pStateMachine.this.mSelectP2pConfigIndex) == null) {
                            P2pStateMachine.this.mSelectP2pConfigList = null;
                            P2pStateMachine.this.mSelectP2pConfigIndex = 0;
                            P2pStateMachine.this.stopLegacyWifiScan(false);
                            return true;
                        }
                        P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                        p2pStateMachine4.sendMessageDelayed(p2pStateMachine4.obtainMessage(WifiP2pServiceImpl.INVITATION_PROCEDURE_TIMED_OUT, p2pStateMachine4.mSelectP2pConfigIndex, 0), 30000);
                        P2pStateMachine.this.sendMessage(139271);
                        P2pStateMachine.this.stopLegacyWifiScan(true);
                        return true;
                    case 139376:
                        P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                        if (message.arg1 == 1) {
                            z = true;
                        }
                        p2pStateMachine5.mRequestNfcCalled = z;
                        if (!P2pStateMachine.this.mRequestNfcCalled) {
                            return true;
                        }
                        P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.NFC_REQUEST_TIMED_OUT);
                        P2pStateMachine.this.sendMessageDelayed(WifiP2pServiceImpl.NFC_REQUEST_TIMED_OUT, 30000);
                        return true;
                    case 139408:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(getName() + " remove client");
                        }
                        WifiP2pConfig p2p_config = (WifiP2pConfig) message.obj;
                        WifiP2pDevice dev = P2pStateMachine.this.mPeers.get(p2p_config.deviceAddress);
                        if (dev == null || TextUtils.isEmpty(dev.interfaceAddress) || !P2pStateMachine.this.mWifiNative.p2pRemoveClient(dev.interfaceAddress, true)) {
                            if (P2pStateMachine.this.mWifiNative.p2pRemoveClient(p2p_config.deviceAddress, true)) {
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    P2pStateMachine.this.logd(getName() + " remove client using dev_address");
                                }
                                P2pStateMachine.this.replyToMessage(message, 139410);
                                return true;
                            }
                            if (WifiP2pServiceImpl.this.mConnectedDevicesCnt == 1) {
                                P2pStateMachine.this.handleGroupRemoved();
                                P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                                p2pStateMachine6.transitionTo(p2pStateMachine6.mInactiveState);
                            }
                            P2pStateMachine.this.replyToMessage((P2pStateMachine) message, (Message) 139409, 0);
                            return true;
                        } else if (!WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            return true;
                        } else {
                            P2pStateMachine.this.logd(getName() + " remove client using interface_address");
                            return true;
                        }
                    case WifiP2pServiceImpl.DISABLE_P2P /*{ENCODED_INT: 143377}*/:
                        P2pStateMachine.this.sendMessage(139280);
                        P2pStateMachine.this.deferMessage(message);
                        return true;
                    case WifiP2pServiceImpl.IPC_PRE_DHCP_ACTION /*{ENCODED_INT: 143390}*/:
                        P2pStateMachine.this.mWifiNative.setP2pPowerSave(P2pStateMachine.this.mGroup.getInterface(), false);
                        try {
                            WifiP2pServiceImpl.this.mIpClient.completedPreDhcpAction();
                            return true;
                        } catch (RemoteException e3) {
                            e3.rethrowFromSystemServer();
                            return true;
                        }
                    case WifiP2pServiceImpl.IPC_POST_DHCP_ACTION /*{ENCODED_INT: 143391}*/:
                        P2pStateMachine.this.mWifiNative.setP2pPowerSave(P2pStateMachine.this.mGroup.getInterface(), true);
                        return true;
                    case WifiP2pServiceImpl.IPC_DHCP_RESULTS /*{ENCODED_INT: 143392}*/:
                        WifiP2pServiceImpl.this.mDhcpResults = (DhcpResults) message.obj;
                        return true;
                    case WifiP2pServiceImpl.IPC_PROVISIONING_SUCCESS /*{ENCODED_INT: 143393}*/:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("mDhcpResults: " + WifiP2pServiceImpl.this.mDhcpResults);
                        }
                        if (WifiP2pServiceImpl.this.mDhcpResults != null) {
                            P2pStateMachine p2pStateMachine7 = P2pStateMachine.this;
                            p2pStateMachine7.mConnectedDevAddr = p2pStateMachine7.mGroup.getOwner().deviceAddress;
                            P2pStateMachine p2pStateMachine8 = P2pStateMachine.this;
                            p2pStateMachine8.mConnectedDevIntfAddr = p2pStateMachine8.mGroup.getOwner().interfaceAddress;
                            P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mConnectedDevAddr, 0);
                            P2pStateMachine.this.sendPeersChangedBroadcast();
                            WifiP2pServiceImpl.this.mConnectedDevicesCnt = 1;
                            P2pStateMachine.this.showNotification();
                            P2pStateMachine p2pStateMachine9 = P2pStateMachine.this;
                            p2pStateMachine9.setWifiP2pInfoOnGroupFormation(WifiP2pServiceImpl.this.mDhcpResults.serverAddress);
                            P2pStateMachine.this.sendP2pConnectionChangedBroadcast();
                            try {
                                String ifname = P2pStateMachine.this.mGroup.getInterface();
                                if (WifiP2pServiceImpl.this.mDhcpResults == null) {
                                    return true;
                                }
                                WifiP2pServiceImpl.this.mNwService.addInterfaceToLocalNetwork(ifname, WifiP2pServiceImpl.this.mDhcpResults.getRoutes(ifname));
                                return true;
                            } catch (IllegalStateException ie) {
                                P2pStateMachine.this.loge("Failed to add iface to local network " + ie);
                                return true;
                            } catch (Exception e4) {
                                P2pStateMachine.this.loge("Failed to add iface to local network " + e4);
                                return true;
                            }
                        } else {
                            P2pStateMachine.this.loge("DHCP failed");
                            P2pStateMachine.this.mWifiNative.p2pGroupRemove(P2pStateMachine.this.mGroup.getInterface());
                            return true;
                        }
                    case WifiP2pServiceImpl.IPC_PROVISIONING_FAILURE /*{ENCODED_INT: 143394}*/:
                        P2pStateMachine.this.loge("IP provisioning failed");
                        P2pStateMachine.this.mWifiNative.p2pGroupRemove(P2pStateMachine.this.mGroup.getInterface());
                        return true;
                    case WifiP2pServiceImpl.NFC_REQUEST_TIMED_OUT /*{ENCODED_INT: 143410}*/:
                        P2pStateMachine.this.logd("Nfc join wait time expired");
                        P2pStateMachine.this.mRequestNfcCalled = false;
                        return true;
                    case WifiP2pServiceImpl.INVITATION_PROCEDURE_TIMED_OUT /*{ENCODED_INT: 143411}*/:
                        int index = message.arg1;
                        if (P2pStateMachine.this.mSelectP2pConfigList == null || index != P2pStateMachine.this.mSelectP2pConfigIndex) {
                            return true;
                        }
                        if (P2pStateMachine.this.mSelectP2pConfigList.getConfigIndex(P2pStateMachine.this.mSelectP2pConfigIndex) == null) {
                            P2pStateMachine.this.mSelectP2pConfigList = null;
                            P2pStateMachine.this.mSelectP2pConfigIndex = 0;
                            P2pStateMachine.this.stopLegacyWifiScan(false);
                            return true;
                        }
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("Invitation timed out in Multi-connecting : " + P2pStateMachine.this.mSelectP2pConfigList.getConfigIndex(P2pStateMachine.this.mSelectP2pConfigIndex).deviceAddress);
                        }
                        P2pStateMachine p2pStateMachine10 = P2pStateMachine.this;
                        p2pStateMachine10.mConnectedDevAddr = p2pStateMachine10.mSelectP2pConfigList.getConfigIndex(P2pStateMachine.this.mSelectP2pConfigIndex).deviceAddress;
                        P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mConnectedDevAddr, 2);
                        P2pStateMachine.this.sendPeersChangedBroadcast();
                        P2pStateMachine.access$6808(P2pStateMachine.this);
                        P2pStateMachine p2pStateMachine11 = P2pStateMachine.this;
                        p2pStateMachine11.sendMessageDelayed(p2pStateMachine11.obtainMessage(WifiP2pServiceImpl.INVITATION_PROCEDURE_TIMED_OUT, p2pStateMachine11.mSelectP2pConfigIndex, 0), 30000);
                        P2pStateMachine.this.sendMessage(139271);
                        return true;
                    case WifiP2pServiceImpl.P2P_GROUP_STARTED_TIMED_OUT /*{ENCODED_INT: 143412}*/:
                        break;
                    case WifiP2pServiceImpl.TIME_ELAPSED_AFTER_CONNECTED /*{ENCODED_INT: 143413}*/:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("mConnectedPkgName : " + WifiP2pServiceImpl.this.mConnectedPkgName);
                        }
                        if (!WifiP2pServiceImpl.WIFI_DIRECT_SETTINGS_PKGNAME.equals(WifiP2pServiceImpl.this.mConnectedPkgName)) {
                            return true;
                        }
                        P2pStateMachine.this.showP2pConnectedNotification();
                        return true;
                    case WifiP2pServiceImpl.P2P_ADVOPP_DISCOVER_PEER /*{ENCODED_INT: 143460}*/:
                        if (WifiP2pServiceImpl.this.mAdvancedOppScanRetryCount < 8) {
                            P2pStateMachine.this.sendMessage(139265);
                            P2pStateMachine p2pStateMachine12 = P2pStateMachine.this;
                            p2pStateMachine12.sendMessageDelayed(p2pStateMachine12.obtainMessage(WifiP2pServiceImpl.P2P_ADVOPP_DISCOVER_PEER, WifiP2pServiceImpl.access$18508(WifiP2pServiceImpl.this)), 3000);
                            return true;
                        }
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("p2p discovery failure : remove autonomous group");
                        }
                        P2pStateMachine.this.sendMessage(139280);
                        P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.P2P_ADVOPP_DISCOVER_PEER);
                        WifiP2pServiceImpl.this.mAdvancedOppScanRetryCount = 0;
                        return true;
                    case WifiP2pServiceImpl.P2P_ADVOPP_DELAYED_DISCOVER_PEER /*{ENCODED_INT: 143461}*/:
                        if (!WifiP2pServiceImpl.this.mAdvancedOppSender) {
                            return true;
                        }
                        WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndListen = true;
                        P2pStateMachine.this.sendMessage(139280);
                        P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.P2P_ADVOPP_DELAYED_DISCOVER_PEER);
                        return true;
                    case WifiP2pServiceImpl.P2P_TRAFFIC_POLL /*{ENCODED_INT: 143480}*/:
                        if (message.arg1 != WifiP2pServiceImpl.this.mTrafficPollToken) {
                            return true;
                        }
                        P2pStateMachine p2pStateMachine13 = P2pStateMachine.this;
                        p2pStateMachine13.sendMessageDelayed(p2pStateMachine13.obtainMessage(WifiP2pServiceImpl.P2P_TRAFFIC_POLL, WifiP2pServiceImpl.this.mTrafficPollToken, 0), (long) WifiP2pServiceImpl.this.mPollTrafficIntervalMsecs);
                        WifiP2pServiceImpl.this.mWifiInjector.getWifiTrafficPoller().notifyOnDataActivity(TrafficStats.getTxBytes(P2pStateMachine.this.mGroup.getInterface()), TrafficStats.getRxBytes(P2pStateMachine.this.mGroup.getInterface()));
                        return true;
                    case WifiP2pMonitor.P2P_DEVICE_LOST_EVENT:
                        if (message.obj == null) {
                            Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                            return false;
                        }
                        WifiP2pDevice device = (WifiP2pDevice) message.obj;
                        if (P2pStateMachine.this.mGroup.contains(device) || (P2pStateMachine.this.mSavedPeerConfig != null && P2pStateMachine.this.mSavedPeerConfig.deviceAddress.equals(device.deviceAddress))) {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd("Add device to lost list " + device);
                            }
                            P2pStateMachine.this.mPeersLostDuringConnection.updateSupplicantDetails(device);
                            return true;
                        }
                        WifiP2pDevice device2 = P2pStateMachine.this.mPeers.remove(device.deviceAddress);
                        if (!WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            return true;
                        }
                        P2pStateMachine.this.logd("device lost in connected state " + device2);
                        return true;
                    case WifiP2pMonitor.P2P_GO_NEGOTIATION_REQUEST_EVENT:
                        WifiP2pConfig config3 = (WifiP2pConfig) message.obj;
                        if (P2pStateMachine.this.isConfigInvalid(config3)) {
                            P2pStateMachine.this.loge("Dropping GO neg request " + config3);
                            return true;
                        }
                        P2pStateMachine.this.mSavedPeerConfig = config3;
                        WifiP2pServiceImpl.this.mAutonomousGroup = false;
                        WifiP2pServiceImpl.this.mJoinExistingGroup = false;
                        if (P2pStateMachine.this.mGroup.isGroupOwner()) {
                            return false;
                        }
                        WifiP2pDevice peer = P2pStateMachine.this.mPeers.get(config3.deviceAddress);
                        if (peer == null || peer.supportFwInvite != 1) {
                            return true;
                        }
                        P2pStateMachine p2pStateMachine14 = P2pStateMachine.this;
                        if (p2pStateMachine14.sendConnectNoticeToApp(peer, p2pStateMachine14.mSavedPeerConfig)) {
                            return true;
                        }
                        P2pStateMachine.this.logd("GC is receiving Connect Req");
                        P2pStateMachine.this.mIsGotoJoinState = true;
                        P2pStateMachine p2pStateMachine15 = P2pStateMachine.this;
                        p2pStateMachine15.transitionTo(p2pStateMachine15.mUserAuthorizingJoinState);
                        return true;
                    case WifiP2pMonitor.P2P_GROUP_STARTED_EVENT:
                        P2pStateMachine.this.loge("Duplicate group creation event notice, ignore");
                        return true;
                    case WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(getName() + " group removed");
                        }
                        P2pStateMachine.this.handleGroupRemoved();
                        if (!WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndJoin) {
                            P2pStateMachine.this.mPeers.clear();
                        }
                        P2pStateMachine p2pStateMachine16 = P2pStateMachine.this;
                        p2pStateMachine16.transitionTo(p2pStateMachine16.mInactiveState);
                        return true;
                    case WifiP2pMonitor.P2P_INVITATION_RESULT_EVENT:
                        P2pStatus status = (P2pStatus) message.obj;
                        if (status == P2pStatus.SUCCESS) {
                            return true;
                        }
                        P2pStateMachine.this.loge("Invitation result " + status);
                        if (status != P2pStatus.UNKNOWN_P2P_GROUP || (netId = P2pStateMachine.this.mGroup.getNetworkId()) < 0) {
                            return true;
                        }
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("Remove unknown client from the list");
                        }
                        P2pStateMachine p2pStateMachine17 = P2pStateMachine.this;
                        p2pStateMachine17.removeClientFromList(netId, p2pStateMachine17.mSavedPeerConfig.deviceAddress, false);
                        P2pStateMachine p2pStateMachine18 = P2pStateMachine.this;
                        p2pStateMachine18.sendMessage(139271, p2pStateMachine18.mSavedPeerConfig);
                        return true;
                    case WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT:
                    case WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                    case WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                        if (!P2pStateMachine.this.mGroup.isGroupOwner()) {
                            P2pStateMachine.this.logd("I'm GC. Ignore connection request.");
                            return true;
                        } else if (WifiP2pServiceImpl.this.mConnectedDevicesCnt >= WifiP2pManager.MAX_CLIENT_SUPPORT) {
                            P2pStateMachine.this.logd("Connection limited - mConnectedDevicesCnt : " + WifiP2pServiceImpl.this.mConnectedDevicesCnt);
                            P2pStateMachine.this.showConnectionLimitDialog();
                            return true;
                        } else {
                            WifiP2pProvDiscEvent provDisc = (WifiP2pProvDiscEvent) message.obj;
                            if (P2pStateMachine.this.mSavedPeerConfig == null || P2pStateMachine.this.mSavedPeerConfig.deviceAddress == null || !P2pStateMachine.this.mSavedPeerConfig.deviceAddress.equals(provDisc.device.deviceAddress)) {
                                P2pStateMachine.this.mSavedPeerConfig = new WifiP2pConfig();
                                if (!(provDisc == null || provDisc.device == null)) {
                                    P2pStateMachine.this.mSavedProvDiscDevice = provDisc.device;
                                    if (provDisc.device != null) {
                                        P2pStateMachine.this.mSavedPeerConfig.deviceAddress = provDisc.device.deviceAddress;
                                        P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp = provDisc.device.candidateStaticIp;
                                    }
                                }
                                if (message.what == 147491) {
                                    P2pStateMachine.this.mSavedPeerConfig.wps.setup = 2;
                                } else if (message.what == 147492) {
                                    P2pStateMachine.this.mSavedPeerConfig.wps.setup = 1;
                                    P2pStateMachine.this.mSavedPeerConfig.wps.pin = provDisc.pin;
                                } else {
                                    P2pStateMachine.this.mSavedPeerConfig.wps.setup = 0;
                                }
                                P2pStateMachine p2pStateMachine19 = P2pStateMachine.this;
                                WifiP2pDevice peerDev2 = p2pStateMachine19.fetchCurrentDeviceDetails(p2pStateMachine19.mSavedPeerConfig);
                                String manufacturer2 = P2pStateMachine.this.mWifiNative.p2pGetManufacturer(P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                                String type = P2pStateMachine.this.mWifiNative.p2pGetDeviceType(P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                                String pkgName3 = null;
                                List<ActivityManager.RunningTaskInfo> tasks = WifiP2pServiceImpl.this.mActivityMgr.getRunningTasks(1);
                                if (tasks.size() != 0) {
                                    pkgName3 = tasks.get(0).baseActivity.getPackageName();
                                }
                                if (WifiP2pServiceImpl.this.mAdvancedOppSender || WifiP2pServiceImpl.this.mAdvancedOppReceiver) {
                                    pkgName = "com.android.bluetooth.advopp";
                                } else {
                                    pkgName = pkgName3;
                                }
                                WifiP2pServiceImpl.this.mConnReqInfo.set(peerDev2, pkgName, 1, 0, 1, manufacturer2, type);
                                if (provDisc.fw_peer != null && P2pStateMachine.this.mGroup.contains(P2pStateMachine.this.mPeers.get(provDisc.fw_peer))) {
                                    P2pStateMachine.this.notifyInvitationReceivedForceAccept();
                                    P2pStateMachine.this.logd("accept fw_peer");
                                    return true;
                                } else if ((P2pStateMachine.this.mSelectedP2pGroupAddress == null || !P2pStateMachine.this.mSelectedP2pGroupAddress.equals(provDisc.device.deviceAddress)) && (!P2pStateMachine.this.mRequestNfcCalled || message.what != 147489)) {
                                    P2pStateMachine p2pStateMachine20 = P2pStateMachine.this;
                                    if (p2pStateMachine20.sendConnectNoticeToApp(p2pStateMachine20.mSavedProvDiscDevice, P2pStateMachine.this.mSavedPeerConfig)) {
                                        return true;
                                    }
                                    P2pStateMachine.this.logd("Go to UserAuthorizingJoinState");
                                    P2pStateMachine.this.mIsGotoJoinState = true;
                                    if (P2pStateMachine.this.mGroup.isGroupOwner()) {
                                        P2pStateMachine p2pStateMachine21 = P2pStateMachine.this;
                                        p2pStateMachine21.transitionTo(p2pStateMachine21.mUserAuthorizingJoinState);
                                        return true;
                                    } else if (!WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                        return true;
                                    } else {
                                        P2pStateMachine.this.logd("Ignore provision discovery for GC");
                                        return true;
                                    }
                                } else {
                                    if (!WifiP2pServiceImpl.mWpsSkip || P2pStateMachine.this.mRequestNfcCalled) {
                                        P2pStateMachine.this.notifyInvitationReceivedForceAccept();
                                    }
                                    P2pStateMachine.this.mRequestNfcCalled = false;
                                    boolean unused = WifiP2pServiceImpl.mWpsSkip = false;
                                    return true;
                                }
                            } else {
                                P2pStateMachine.this.logd("Ignore duplicated pd request");
                                return true;
                            }
                        }
                    case WifiP2pMonitor.P2P_FIND_STOPPED_EVENT:
                        P2pStateMachine.this.sendP2pDiscoveryChangedBroadcast(false);
                        if (WifiP2pServiceImpl.this.mWfdConnected) {
                            return true;
                        }
                        P2pStateMachine.this.stopLegacyWifiPeriodicScan(false);
                        return true;
                    case WifiP2pMonitor.AP_STA_DISCONNECTED_EVENT:
                        if (message.obj == null) {
                            Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                            return true;
                        }
                        WifiP2pDevice device3 = (WifiP2pDevice) message.obj;
                        String deviceAddress = device3.deviceAddress;
                        if (deviceAddress != null) {
                            P2pStateMachine.this.mConnectedDevAddr = deviceAddress;
                            P2pStateMachine.this.mPeers.updateStatus(deviceAddress, 3);
                            P2pStateMachine.this.sendPeersChangedBroadcast();
                            if (P2pStateMachine.this.mGroup.removeClient(deviceAddress)) {
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    P2pStateMachine.this.logd("Removed client " + deviceAddress);
                                }
                                if (!(P2pStateMachine.this.mSavedPeerConfig == null || P2pStateMachine.this.mSavedPeerConfig.deviceAddress == null || !P2pStateMachine.this.mSavedPeerConfig.deviceAddress.equals(deviceAddress))) {
                                    P2pStateMachine.this.logd("mSavedPeerConfig need to be cleared");
                                    P2pStateMachine.this.mSavedPeerConfig = null;
                                }
                                if ((!WifiP2pServiceImpl.this.mAutonomousGroup || WifiP2pServiceImpl.this.mAdvancedOppReceiver || WifiP2pServiceImpl.this.mAdvancedOppSender) && P2pStateMachine.this.mGroup.isClientListEmpty()) {
                                    P2pStateMachine.this.logd("Client list empty, remove non-persistent p2p group");
                                    P2pStateMachine.this.mWifiNative.p2pGroupRemove(P2pStateMachine.this.mGroup.getInterface());
                                } else {
                                    P2pStateMachine.this.sendP2pConnectionChangedBroadcast();
                                }
                                WifiP2pServiceImpl.this.mWifiP2pMetrics.updateGroupEvent(P2pStateMachine.this.mGroup);
                            } else {
                                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                    P2pStateMachine.this.logd("Failed to remove client " + deviceAddress);
                                }
                                for (WifiP2pDevice c : P2pStateMachine.this.mGroup.getClientList()) {
                                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                        P2pStateMachine.this.logd("client " + c.deviceAddress);
                                    }
                                }
                            }
                            WifiP2pServiceImpl.this.mConnectedDevicesCnt = P2pStateMachine.this.mGroup.getClientList().size();
                            P2pStateMachine.this.sendPeersChangedBroadcast();
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd(getName() + " ap sta disconnected");
                            }
                            WifiP2pServiceImpl.this.setProp("apstadis");
                            WifiP2pServiceImpl.this.checkTimeNoa(5, 0);
                            return true;
                        }
                        P2pStateMachine.this.loge("Disconnect on unknown device: " + device3);
                        return true;
                    case WifiP2pMonitor.AP_STA_CONNECTED_EVENT:
                        if (message.obj == null) {
                            Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                            return true;
                        }
                        WifiP2pServiceImpl.this.mNetworkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, null, null);
                        P2pStateMachine.this.updateThisDevice(0);
                        P2pStateMachine.this.mP2pState = 3;
                        P2pStateMachine.this.mSelectedP2pGroupAddress = null;
                        P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.P2P_GROUP_STARTED_TIMED_OUT);
                        P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.INVITATION_PROCEDURE_TIMED_OUT);
                        P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.P2P_ADVOPP_DISCOVER_PEER);
                        WifiP2pServiceImpl.this.mAdvancedOppReceiver = false;
                        WifiP2pServiceImpl.this.mAdvancedOppSender = false;
                        WifiP2pDevice device4 = (WifiP2pDevice) message.obj;
                        String deviceAddress2 = device4.deviceAddress;
                        P2pStateMachine.this.mWifiNative.setP2pGroupIdle(P2pStateMachine.this.mGroup.getInterface(), 0);
                        if (deviceAddress2 != null) {
                            WifiP2pServiceImpl.this.connectRetryCount(deviceAddress2, 0);
                            WifiP2pServiceImpl.this.mReinvokePersistentNetId = -1;
                            P2pStateMachine.this.mConnectedDevAddr = deviceAddress2;
                            P2pStateMachine.this.mConnectedDevIntfAddr = device4.interfaceAddress;
                            if (P2pStateMachine.this.mSavedProvDiscDevice != null && deviceAddress2.equals(P2pStateMachine.this.mSavedProvDiscDevice.deviceAddress)) {
                                P2pStateMachine.this.mSavedProvDiscDevice = null;
                            }
                            if (P2pStateMachine.this.mPeers.get(deviceAddress2) != null) {
                                P2pStateMachine.this.mGroup.addClient(P2pStateMachine.this.mPeers.get(deviceAddress2));
                            } else {
                                P2pStateMachine.this.mGroup.addClient(deviceAddress2);
                            }
                            P2pStateMachine.this.mPeers.updateStatus(deviceAddress2, 0);
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.logd(getName() + " ap sta connected");
                            }
                            WifiP2pServiceImpl.this.setProp("apstacon");
                            WifiP2pServiceImpl.this.mConnectedDevicesCnt = P2pStateMachine.this.mGroup.getClientList().size();
                            if (WifiP2pServiceImpl.this.mMaxClientCnt < WifiP2pServiceImpl.this.mConnectedDevicesCnt) {
                                WifiP2pServiceImpl.this.mMaxClientCnt = WifiP2pServiceImpl.this.mConnectedDevicesCnt;
                            }
                            P2pStateMachine.this.showNotification();
                            P2pStateMachine.this.sendPeersChangedBroadcast();
                            WifiP2pServiceImpl.this.mWifiP2pMetrics.updateGroupEvent(P2pStateMachine.this.mGroup);
                            if (P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp != 0) {
                                WifiP2pStaticIpConfig wifiP2pStaticIpConfig = P2pStateMachine.this.mP2pStaticIpConfig;
                                int mNextIpAddr = wifiP2pStaticIpConfig.mThisDeviceStaticIp + 16777216;
                                wifiP2pStaticIpConfig.mThisDeviceStaticIp = mNextIpAddr;
                                P2pStateMachine.this.mWifiNative.p2pSet("static_ip", Formatter.formatIpAddress(mNextIpAddr));
                            }
                            if (P2pStateMachine.this.mSelectP2pConfigList != null) {
                                P2pStateMachine.access$6808(P2pStateMachine.this);
                                if (P2pStateMachine.this.mSelectP2pConfigList.getConfigIndex(P2pStateMachine.this.mSelectP2pConfigIndex) == null) {
                                    P2pStateMachine.this.mSelectP2pConfigList = null;
                                    P2pStateMachine.this.mSelectP2pConfigIndex = 0;
                                    P2pStateMachine.this.stopLegacyWifiScan(false);
                                } else {
                                    P2pStateMachine p2pStateMachine22 = P2pStateMachine.this;
                                    p2pStateMachine22.sendMessageDelayed(p2pStateMachine22.obtainMessage(WifiP2pServiceImpl.INVITATION_PROCEDURE_TIMED_OUT, p2pStateMachine22.mSelectP2pConfigIndex, 0), 30000);
                                    P2pStateMachine.this.sendMessage(139271);
                                }
                            } else if ((WifiP2pServiceImpl.this.mThisDevice.wfdInfo == null || !WifiP2pServiceImpl.this.mThisDevice.wfdInfo.isWfdEnabled()) && !WifiP2pServiceImpl.this.mAdvancedOppInProgress) {
                                P2pStateMachine.this.stopLegacyWifiScan(false);
                            }
                        } else {
                            P2pStateMachine.this.loge("Connect on null device address, ignore");
                        }
                        P2pStateMachine.this.sendP2pConnectionChangedBroadcast();
                        return true;
                    case 147499:
                        if (((String) message.obj) == null) {
                            return false;
                        }
                        if (WifiP2pServiceImpl.this.mReinvokePersistentNetId < 0) {
                            return true;
                        }
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(" remove client from the list");
                        }
                        P2pStateMachine p2pStateMachine23 = P2pStateMachine.this;
                        p2pStateMachine23.removeClientFromList(WifiP2pServiceImpl.this.mReinvokePersistentNetId, WifiP2pServiceImpl.this.mReinvokePersistent, true);
                        WifiP2pServiceImpl.this.mReinvokePersistentNetId = -1;
                        return true;
                    case WifiP2pMonitor.P2P_GOPS_EVENT:
                        String ps_method = (String) message.obj;
                        if (ps_method == null || P2pStateMachine.this.mGroup == null || !P2pStateMachine.this.mGroup.isGroupOwner()) {
                            return true;
                        }
                        try {
                            int noa_dur = Integer.parseInt(ps_method.substring(ps_method.indexOf("noa_dur=") + 8, ps_method.indexOf("rx/s") - 2));
                            int noa_num = Integer.parseInt(ps_method.substring(ps_method.indexOf("NOA") + 3, ps_method.indexOf("NOA") + 4));
                            if (noa_num == 0) {
                                P2pStateMachine.this.mWifiNative.setP2pNoa(P2pStateMachine.this.mGroup.getInterface(), false, noa_dur);
                            } else if (noa_num == 1) {
                                P2pStateMachine.this.mWifiNative.setP2pNoa(P2pStateMachine.this.mGroup.getInterface(), true, noa_dur);
                            } else if (noa_num == 2) {
                                P2pStateMachine.this.mWifiNative.setP2pIncBw(P2pStateMachine.this.mGroup.getInterface(), false, noa_dur);
                            } else if (noa_num != 3) {
                                P2pStateMachine.this.mWifiNative.setP2pNoa(P2pStateMachine.this.mGroup.getInterface(), false, noa_dur);
                                P2pStateMachine.this.mWifiNative.setP2pIncBw(P2pStateMachine.this.mGroup.getInterface(), false, noa_dur);
                            } else {
                                P2pStateMachine.this.mWifiNative.setP2pIncBw(P2pStateMachine.this.mGroup.getInterface(), true, noa_dur);
                            }
                            WifiP2pServiceImpl.this.checkTimeNoa(noa_num, noa_dur);
                            return true;
                        } catch (Exception e5) {
                            P2pStateMachine.this.loge("parsing failed in GOPS because NumberFormatException or StringIndexOutOfBoundsException, so skip");
                            return true;
                        }
                    case WifiP2pMonitor.P2P_WPS_SKIP_EVENT:
                        boolean unused2 = WifiP2pServiceImpl.mWpsSkip = true;
                        return true;
                    case 147527:
                        WifiP2pProvDiscEvent userReject = (WifiP2pProvDiscEvent) message.obj;
                        if (P2pStateMachine.this.mSavedPeerConfig != null && (userReject == null || !userReject.device.deviceAddress.equals(P2pStateMachine.this.mSavedPeerConfig.deviceAddress))) {
                            return true;
                        }
                        if (WifiP2pServiceImpl.this.mInvitationDialog != null && WifiP2pServiceImpl.this.mInvitationDialog.isShowing()) {
                            WifiP2pServiceImpl.this.mWpsTimer.cancel();
                            WifiP2pServiceImpl.this.mInvitationDialog.dismiss();
                            WifiP2pServiceImpl.this.mInvitationDialog = null;
                        }
                        P2pStateMachine.this.mSavedPeerConfig = null;
                        return true;
                    default:
                        return false;
                }
                P2pStateMachine.this.logd("P2P_GROUP_STARTED_TIMED_OUT");
                P2pStateMachine.this.stopLegacyWifiScan(false);
                if (!P2pStateMachine.this.mGroup.isGroupOwner() || !P2pStateMachine.this.mGroup.isClientListEmpty()) {
                    return true;
                }
                P2pStateMachine.this.sendMessage(139280);
                return true;
            }

            public void exit() {
                if (!P2pStateMachine.this.mIsGotoJoinState) {
                    Slog.d(WifiP2pServiceImpl.TAG, "=========== Exit GroupCreatedState");
                    P2pStateMachine.this.mSavedProvDiscDevice = null;
                    if (WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndJoin || WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndListen) {
                        Log.i(WifiP2pServiceImpl.TAG, "Exit GroupCreatedState. advOPP: " + WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndJoin + " : " + WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndListen);
                    } else if (WifiP2pServiceImpl.this.mContext.getResources().getBoolean(17891609)) {
                        String unused = WifiP2pServiceImpl.RANDOM_MAC_ADDRESS = WifiP2pServiceImpl.toHexString(WifiP2pServiceImpl.this.createRandomMac());
                        P2pStateMachine.this.mWifiNative.p2pSet("random_mac", WifiP2pServiceImpl.RANDOM_MAC_ADDRESS);
                        if (WifiP2pServiceImpl.this.mThisDevice.deviceAddress == null || !WifiP2pServiceImpl.this.mThisDevice.deviceAddress.equals(WifiP2pServiceImpl.RANDOM_MAC_ADDRESS)) {
                            WifiP2pServiceImpl.this.mThisDevice.deviceAddress = WifiP2pServiceImpl.RANDOM_MAC_ADDRESS;
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                Log.i(WifiP2pServiceImpl.TAG, "Exit GroupCreatedState. MAC is changed: " + WifiP2pServiceImpl.this.mThisDevice.deviceAddress);
                            }
                        }
                    }
                    if (WifiP2pServiceImpl.this.mWiFiLock != null) {
                        WifiP2pServiceImpl.this.mWiFiLock.release();
                    }
                    WifiP2pServiceImpl.this.mWifiP2pMetrics.endGroupEvent();
                    P2pStateMachine.this.updateThisDevice(3);
                    P2pStateMachine.this.resetWifiP2pInfo();
                    P2pStateMachine.this.removeP2pPktLogFilter();
                    WifiP2pServiceImpl.this.mNetworkInfo.setDetailedState(NetworkInfo.DetailedState.DISCONNECTED, null, null);
                    P2pStateMachine.this.mP2pState = 2;
                    WifiP2pServiceImpl.this.setProp("groupexit");
                    WifiP2pServiceImpl.this.checkTimeNoa(5, 0);
                    P2pStateMachine.this.sendP2pConnectionChangedBroadcast();
                    P2pStateMachine.this.clearP2pConnectedNotification();
                    P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.TIME_ELAPSED_AFTER_CONNECTED);
                    P2pStateMachine.this.clearNotification();
                    P2pStateMachine.this.mRequestNfcCalled = false;
                    WifiP2pServiceImpl.this.mWfdConnected = false;
                    WifiP2pServiceImpl.this.mConnectedPkgName = null;
                    P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.P2P_GROUP_STARTED_TIMED_OUT);
                    WifiP2pServiceImpl.this.mAdvancedOppReceiver = false;
                    WifiP2pServiceImpl.this.mAdvancedOppSender = false;
                    WifiP2pServiceImpl.this.mAdvancedOppScanRetryCount = 0;
                    WifiP2pServiceImpl.this.mReinvokePersistentNetId = -1;
                    P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.P2P_ADVOPP_DISCOVER_PEER);
                    P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.P2P_ADVOPP_DELAYED_DISCOVER_PEER);
                }
            }
        }

        /* access modifiers changed from: package-private */
        public class UserAuthorizingJoinState extends State {
            UserAuthorizingJoinState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                if (WifiP2pServiceImpl.this.mAdvancedOppReceiver || WifiP2pServiceImpl.this.mAdvancedOppSender) {
                    P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.P2P_ADVOPP_DELAYED_DISCOVER_PEER);
                    P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.PEER_CONNECTION_USER_ACCEPT);
                    return;
                }
                P2pStateMachine.this.notifyInvitationReceived();
                P2pStateMachine.this.soundNotification();
            }

            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.logd(getName() + message.toString());
                }
                switch (message.what) {
                    case WifiP2pServiceImpl.PEER_CONNECTION_USER_ACCEPT /*{ENCODED_INT: 143362}*/:
                        P2pStateMachine.this.mWifiNative.p2pStopFind();
                        if (P2pStateMachine.this.mSavedPeerConfig != null) {
                            if (!P2pStateMachine.this.mGroup.isGroupOwner()) {
                                if (P2pStateMachine.this.mWifiNative.p2pInvite(P2pStateMachine.this.mGroup, P2pStateMachine.this.mSavedPeerConfig.deviceAddress)) {
                                    P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                                    p2pStateMachine2.logd("Inviting device : " + P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                                    P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mSavedPeerConfig.deviceAddress, 1);
                                    P2pStateMachine.this.sendPeersChangedBroadcast();
                                } else {
                                    P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                                    p2pStateMachine3.logd("Failed inviting device : " + P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                                }
                                P2pStateMachine.this.mSavedPeerConfig = null;
                            } else if (P2pStateMachine.this.mSavedPeerConfig.wps.setup == 0) {
                                if (!TextUtils.isEmpty(P2pStateMachine.this.mSavedPeerConfig.deviceAddress)) {
                                    P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                                    p2pStateMachine4.logd("Allowed device : " + P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                                }
                                P2pStateMachine.this.mWifiNative.startWpsPbc(P2pStateMachine.this.mGroup.getInterface(), P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                            } else {
                                P2pStateMachine.this.mWifiNative.startWpsPinKeypad(P2pStateMachine.this.mGroup.getInterface(), P2pStateMachine.this.mSavedPeerConfig.wps.pin);
                            }
                        }
                        P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                        p2pStateMachine5.transitionTo(p2pStateMachine5.mGroupCreatedState);
                        break;
                    case WifiP2pServiceImpl.PEER_CONNECTION_USER_REJECT /*{ENCODED_INT: 143363}*/:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("User rejected incoming request");
                        }
                        P2pStateMachine.this.mSavedPeerConfig = null;
                        P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                        p2pStateMachine6.transitionTo(p2pStateMachine6.mGroupCreatedState);
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT:
                    case WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                    case WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                        break;
                    case 147527:
                        if (WifiP2pServiceImpl.this.mInvitationDialog != null && WifiP2pServiceImpl.this.mInvitationDialog.isShowing()) {
                            WifiP2pServiceImpl.this.mWpsTimer.cancel();
                            WifiP2pServiceImpl.this.mInvitationDialog.dismiss();
                            WifiP2pServiceImpl.this.mInvitationDialog = null;
                        }
                        P2pStateMachine.this.mSavedPeerConfig = null;
                        P2pStateMachine p2pStateMachine7 = P2pStateMachine.this;
                        p2pStateMachine7.transitionTo(p2pStateMachine7.mGroupCreatedState);
                        break;
                    default:
                        return false;
                }
                return true;
            }

            public void exit() {
            }
        }

        /* access modifiers changed from: package-private */
        public class OngoingGroupRemovalState extends State {
            OngoingGroupRemovalState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
            }

            public boolean processMessage(Message message) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                    p2pStateMachine.logd(getName() + message.toString());
                }
                int i = message.what;
                if (i == 139280) {
                    P2pStateMachine.this.replyToMessage(message, 139282);
                    return true;
                } else if (i != WifiP2pServiceImpl.IPC_PROVISIONING_SUCCESS) {
                    return false;
                } else {
                    return true;
                }
            }
        }

        /* access modifiers changed from: package-private */
        public class NfcProvisionState extends State {
            NfcProvisionState() {
            }

            public void enter() {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName());
                }
                P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.NFC_REQUEST_TIMED_OUT);
                P2pStateMachine.this.sendMessageDelayed(WifiP2pServiceImpl.NFC_REQUEST_TIMED_OUT, 30000);
                P2pStateMachine.this.mWifiNative.p2pSet("pre_auth", "1");
            }

            public boolean processMessage(Message message) {
                String pkgName;
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    P2pStateMachine.this.logd(getName() + "{ what=" + message.what + " }");
                }
                switch (message.what) {
                    case 139372:
                        String pkgName2 = message.getData().getString("appPkgName");
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd(getName() + " package to call P2P_LISTEN -> " + pkgName2);
                        }
                        if (!"com.android.bluetooth".equals(pkgName2)) {
                            int timeout = message.arg1;
                            P2pStateMachine.this.stopLegacyWifiScan(true);
                            if (!P2pStateMachine.this.mWifiNative.p2pListen(timeout)) {
                                P2pStateMachine.this.replyToMessage(message, 139330);
                                break;
                            } else {
                                P2pStateMachine.this.replyToMessage(message, 139331);
                                break;
                            }
                        } else {
                            P2pStateMachine.this.loge(getName() + " Unhandled message { what=" + message.what + " }");
                            return false;
                        }
                    case 139376:
                        if (message.arg1 != 1) {
                            P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.NFC_REQUEST_TIMED_OUT);
                            break;
                        } else {
                            P2pStateMachine.this.removeMessages(WifiP2pServiceImpl.NFC_REQUEST_TIMED_OUT);
                            P2pStateMachine.this.sendMessageDelayed(WifiP2pServiceImpl.NFC_REQUEST_TIMED_OUT, 30000);
                            break;
                        }
                    case WifiP2pServiceImpl.NFC_REQUEST_TIMED_OUT /*{ENCODED_INT: 143410}*/:
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine.this.logd("Nfc wait time expired");
                        }
                        P2pStateMachine.this.mSavedPeerConfig = null;
                        P2pStateMachine.this.mWifiNative.p2pSet("pre_auth", "0");
                        P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                        p2pStateMachine.transitionTo(p2pStateMachine.mInactiveState);
                        break;
                    case WifiP2pMonitor.P2P_GO_NEGOTIATION_REQUEST_EVENT:
                        P2pStateMachine.this.mSavedPeerConfig = (WifiP2pConfig) message.obj;
                        P2pStateMachine p2pStateMachine2 = P2pStateMachine.this;
                        if (!p2pStateMachine2.isConfigInvalid(p2pStateMachine2.mSavedPeerConfig)) {
                            WifiP2pServiceImpl.this.mAutonomousGroup = false;
                            WifiP2pServiceImpl.this.mJoinExistingGroup = false;
                            P2pStateMachine p2pStateMachine3 = P2pStateMachine.this;
                            p2pStateMachine3.p2pConnectWithPinDisplay(p2pStateMachine3.mSavedPeerConfig);
                            P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mSavedPeerConfig.deviceAddress, 1);
                            P2pStateMachine.this.sendPeersChangedBroadcast();
                            P2pStateMachine p2pStateMachine4 = P2pStateMachine.this;
                            p2pStateMachine4.transitionTo(p2pStateMachine4.mGroupNegotiationState);
                            break;
                        } else {
                            P2pStateMachine.this.loge("Dropping GO neg request " + P2pStateMachine.this.mSavedPeerConfig);
                            break;
                        }
                    case WifiP2pMonitor.P2P_INVITATION_RECEIVED_EVENT:
                        WifiP2pGroup group = (WifiP2pGroup) message.obj;
                        if (group.getOwner() == null) {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                P2pStateMachine.this.loge("Ignored invitation from null owner");
                                break;
                            }
                        } else {
                            P2pStateMachine.this.mSavedPeerConfig = new WifiP2pConfig();
                            P2pStateMachine.this.mSavedPeerConfig.deviceAddress = group.getOwner().deviceAddress;
                            P2pStateMachine p2pStateMachine5 = P2pStateMachine.this;
                            p2pStateMachine5.mSelectedP2pGroupAddress = p2pStateMachine5.mSavedPeerConfig.deviceAddress;
                            P2pStateMachine.this.mSavedPeerConfig.wps.setup = 0;
                            P2pStateMachine p2pStateMachine6 = P2pStateMachine.this;
                            if (!p2pStateMachine6.isConfigInvalid(p2pStateMachine6.mSavedPeerConfig)) {
                                WifiP2pServiceImpl.this.mAutonomousGroup = false;
                                WifiP2pServiceImpl.this.mJoinExistingGroup = true;
                                P2pStateMachine p2pStateMachine7 = P2pStateMachine.this;
                                p2pStateMachine7.p2pConnectWithPinDisplay(p2pStateMachine7.mSavedPeerConfig);
                                P2pStateMachine.this.mPeers.updateStatus(P2pStateMachine.this.mSavedPeerConfig.deviceAddress, 1);
                                P2pStateMachine.this.sendPeersChangedBroadcast();
                                P2pStateMachine p2pStateMachine8 = P2pStateMachine.this;
                                p2pStateMachine8.transitionTo(p2pStateMachine8.mGroupNegotiationState);
                                break;
                            } else {
                                P2pStateMachine.this.loge("Dropping invitation request " + P2pStateMachine.this.mSavedPeerConfig);
                                break;
                            }
                        }
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT:
                        WifiP2pProvDiscEvent provDisc = (WifiP2pProvDiscEvent) message.obj;
                        if (!(provDisc == null || provDisc.device == null)) {
                            P2pStateMachine.this.mP2pStaticIpConfig.candidateStaticIp = provDisc.device.candidateStaticIp;
                            WifiP2pServiceImpl.this.mAutonomousGroup = false;
                            WifiP2pServiceImpl.this.mJoinExistingGroup = false;
                            P2pStateMachine.this.mPeers.updateStatus(provDisc.device.deviceAddress, 1);
                            P2pStateMachine.this.sendPeersChangedBroadcast();
                            P2pStateMachine p2pStateMachine9 = P2pStateMachine.this;
                            p2pStateMachine9.transitionTo(p2pStateMachine9.mGroupNegotiationState);
                            WifiP2pDevice peerDev = provDisc.device;
                            String manufacturer = P2pStateMachine.this.mWifiNative.p2pGetManufacturer(provDisc.device.deviceAddress);
                            String type = P2pStateMachine.this.mWifiNative.p2pGetDeviceType(provDisc.device.deviceAddress);
                            if (WifiP2pServiceImpl.this.mConnReqInfo.pkgName != null) {
                                pkgName = WifiP2pServiceImpl.this.mConnReqInfo.pkgName;
                            } else {
                                pkgName = null;
                            }
                            WifiP2pServiceImpl.this.mConnReqInfo.set(peerDev, pkgName, 0, 0, 0, manufacturer, type);
                            break;
                        }
                    default:
                        return false;
                }
                return true;
            }

            public void exit() {
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void addHistoricalDumpLog(String log) {
            if (this.mHistoricalDumpLogs.size() > 35) {
                this.mHistoricalDumpLogs.remove(0);
            }
            this.mHistoricalDumpLogs.add(log);
        }

        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            WifiP2pServiceImpl.super.dump(fd, pw, args);
            pw.println("mWifiP2pInfo " + this.mWifiP2pInfo);
            pw.println("mGroup " + this.mGroup);
            pw.println("mSavedPeerConfig " + this.mSavedPeerConfig);
            pw.println("mGroups" + this.mGroups);
            pw.println("mSavedP2pGroup " + this.mSavedP2pGroup);
            pw.println("Wi-Fi Direct api call history:");
            pw.println(this.mHistoricalDumpLogs.toString());
            pw.println("Config Change:");
            pw.println(WifiP2pServiceImpl.this.mModeChange.toString());
            pw.println();
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void insertLog(String feature, String extra, long value) {
            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                Log.d(WifiP2pServiceImpl.TAG, "insertLog (HQM : true, CONTEXT : " + WifiP2pServiceImpl.ENABLE_SURVEY_MODE + ") - feature : " + feature + ", extra : " + extra + ", value : " + value);
            }
            if (WifiP2pServiceImpl.this.mSemHqmManager == null) {
                WifiP2pServiceImpl wifiP2pServiceImpl = WifiP2pServiceImpl.this;
                wifiP2pServiceImpl.mSemHqmManager = (SemHqmManager) wifiP2pServiceImpl.mContext.getSystemService("HqmManagerService");
            }
            if (WifiP2pServiceImpl.this.mSemHqmManager != null) {
                WifiP2pServiceImpl.this.mSemHqmManager.sendHWParamToHQMwithAppId(0, BaseBigDataItem.COMPONENT_ID, feature, BaseBigDataItem.HIT_TYPE_ONCE_A_DAY, (String) null, (String) null, "", extra, "", "android.net.wifi.p2p");
            } else {
                Log.e(WifiP2pServiceImpl.TAG, "error - mSemHqmManager is null");
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private String buildLoggingData(int type, String dataString) {
            String peerAddr;
            String loggingData;
            String loggingData2;
            long noaTime;
            String[] tokens;
            String loggingData3;
            String[] strArr;
            String loggingData4;
            String loggingData5;
            String loggingData6;
            String[] tokens2 = dataString.split(" ");
            int reasonCode = -1;
            reasonCode = -1;
            if (tokens2.length == 3) {
                if (tokens2[1] != null) {
                    peerAddr = tokens2[1].substring(tokens2[1].indexOf("=") + 1);
                } else {
                    peerAddr = null;
                }
                if (tokens2[2] != null) {
                    try {
                        reasonCode = Integer.parseInt(tokens2[2].substring(tokens2[2].indexOf("=") + 1));
                    } catch (NumberFormatException e) {
                    }
                }
                if (type == 147536) {
                    if (this.mGroup != null) {
                        String loggingData7 = "" + (this.mGroup.isGroupOwner() ? 1 : 0) + " ";
                        if (!this.mGroup.isGroupOwner()) {
                            loggingData5 = loggingData7 + "1 ";
                        } else {
                            loggingData5 = loggingData7 + WifiP2pServiceImpl.this.mMaxClientCnt + " ";
                        }
                        loggingData2 = loggingData5 + this.mGroup.getFrequency() + " ";
                    } else if (this.mGroupBackup == null) {
                        Log.e(WifiP2pServiceImpl.TAG, "mGroup and mGroupBackup NULL");
                        loggingData2 = "" + "-1 -1 -1 ";
                    } else {
                        String loggingData8 = "" + (this.mGroupBackup.isGroupOwner() ? 1 : 0) + " ";
                        if (!this.mGroupBackup.isGroupOwner()) {
                            loggingData6 = loggingData8 + "1 ";
                        } else {
                            loggingData6 = loggingData8 + WifiP2pServiceImpl.this.mMaxClientCnt + " ";
                        }
                        loggingData2 = loggingData6 + this.mGroupBackup.getFrequency() + " ";
                    }
                    long period = 0;
                    period = 0;
                    long endTime = System.currentTimeMillis();
                    WifiP2pGroup wifiP2pGroup = this.mGroup;
                    if (wifiP2pGroup == null || !wifiP2pGroup.isGroupOwner() || this.mGroup.getClientList().size() != 0) {
                        noaTime = -1;
                    } else {
                        noaTime = WifiP2pServiceImpl.this.checkTimeNoa(10, 0) / 1000;
                    }
                    WifiP2pConnectedPeriodInfo savedInfo = (WifiP2pConnectedPeriodInfo) WifiP2pServiceImpl.this.mConnectedPeriodInfoList.remove(peerAddr);
                    if (savedInfo == null) {
                        tokens = tokens2;
                        savedInfo = (WifiP2pConnectedPeriodInfo) WifiP2pServiceImpl.this.mConnectedPeriodInfoList.remove(convertDevAddress(peerAddr));
                    } else {
                        tokens = tokens2;
                    }
                    if (savedInfo != null) {
                        String loggingData9 = loggingData2 + savedInfo.pkgName + " ";
                        if (savedInfo.startTime > 0 && savedInfo.startTime < endTime) {
                            period = (endTime - savedInfo.startTime) / 1000;
                        }
                        loggingData3 = loggingData9 + period + " " + noaTime + " ";
                    } else {
                        Log.i(WifiP2pServiceImpl.TAG, "No matching peer found with " + peerAddr);
                        loggingData3 = loggingData2 + "null -1 -1 ";
                    }
                    WifiP2pConnectReqInfo savedConnReqInfo = (WifiP2pConnectReqInfo) WifiP2pServiceImpl.this.mConnReqInfoList.remove(peerAddr);
                    if (savedConnReqInfo == null) {
                        savedConnReqInfo = (WifiP2pConnectReqInfo) WifiP2pServiceImpl.this.mConnReqInfoList.remove(convertDevAddress(peerAddr));
                    }
                    if (savedConnReqInfo != null) {
                        loggingData4 = loggingData3 + savedConnReqInfo.getPeerManufacturer() + " " + savedConnReqInfo.getPeerDevType() + " ";
                        strArr = tokens;
                    } else {
                        String peerManu = this.mWifiNative.p2pGetManufacturer(peerAddr);
                        if (peerManu == null) {
                            peerManu = this.mWifiNative.p2pGetManufacturer(convertDevAddress(peerAddr));
                        }
                        String peerType = this.mWifiNative.p2pGetDeviceType(peerAddr);
                        if (peerType == null) {
                            peerType = this.mWifiNative.p2pGetDeviceType(convertDevAddress(peerAddr));
                        }
                        String loggingData10 = loggingData3 + peerManu + " ";
                        if (peerType == null) {
                            loggingData4 = loggingData10 + "-1 ";
                            strArr = tokens;
                        } else {
                            String[] tokens3 = peerType.split("-");
                            loggingData4 = loggingData10 + tokens3[0] + " ";
                            strArr = tokens3;
                        }
                    }
                    String loggingData11 = loggingData4 + reasonCode;
                    this.mGroupBackup = null;
                    return loggingData11;
                } else if (type == 147537) {
                    if (WifiP2pServiceImpl.this.mConnReqInfo == null || peerAddr == null || WifiP2pServiceImpl.this.mConnReqInfo.peerDev == null || (!peerAddr.equals(WifiP2pServiceImpl.this.mConnReqInfo.peerDev.deviceAddress) && !convertDevAddress(peerAddr).equals(WifiP2pServiceImpl.this.mConnReqInfo.peerDev.deviceAddress))) {
                        Log.i(WifiP2pServiceImpl.TAG, "Connection request information doesn't exist or match");
                        loggingData = "" + "null 0 0 0 null -1 -1 " + reasonCode;
                    } else {
                        WifiP2pServiceImpl wifiP2pServiceImpl = WifiP2pServiceImpl.this;
                        WifiP2pServiceImpl.this.mConnReqInfoList.put(peerAddr, new WifiP2pConnectReqInfo(wifiP2pServiceImpl.mConnReqInfo));
                        loggingData = "" + WifiP2pServiceImpl.this.mConnReqInfo.toString() + reasonCode;
                    }
                    if (reasonCode != 0) {
                        return loggingData;
                    }
                    WifiP2pConnectedPeriodInfo timeInfo = new WifiP2pConnectedPeriodInfo();
                    if (!(WifiP2pServiceImpl.this.mConnReqInfo == null || WifiP2pServiceImpl.this.mConnReqInfo.peerDev == null)) {
                        timeInfo.peerDev = WifiP2pServiceImpl.this.mConnReqInfo.peerDev;
                        timeInfo.pkgName = WifiP2pServiceImpl.this.mConnReqInfo.pkgName;
                        timeInfo.startTime = System.currentTimeMillis();
                        WifiP2pServiceImpl.this.mConnectedPeriodInfoList.put(timeInfo.peerDev.deviceAddress, timeInfo);
                    }
                    WifiP2pServiceImpl.this.mConnectedPkgName = timeInfo.pkgName;
                    return loggingData;
                } else if (type != 147538) {
                    Log.e(WifiP2pServiceImpl.TAG, "Invalid event");
                    return null;
                } else if (WifiP2pServiceImpl.this.mConnReqInfo == null || peerAddr == null || WifiP2pServiceImpl.this.mConnReqInfo.peerDev == null) {
                    return "";
                } else {
                    if (!peerAddr.equals(WifiP2pServiceImpl.this.mConnReqInfo.peerDev.deviceAddress) && !convertDevAddress(peerAddr).equals(WifiP2pServiceImpl.this.mConnReqInfo.peerDev.deviceAddress)) {
                        return "";
                    }
                    if (reasonCode < 0 || reasonCode > 15) {
                        Log.i(WifiP2pServiceImpl.TAG, "Invalid intent value : " + reasonCode);
                        return "";
                    }
                    WifiP2pServiceImpl.this.mConnReqInfo.setPeerGOIntentValue(reasonCode);
                    return "";
                }
            } else {
                Log.e(WifiP2pServiceImpl.TAG, "Invalid argument for p2p logging data");
                return null;
            }
        }

        private String convertDevAddress(String addr) {
            java.util.Formatter partialMacAddr = new java.util.Formatter();
            String macAddrStr = "";
            try {
                partialMacAddr.format("%02x", Integer.valueOf(Integer.parseInt(addr.substring(12, 14), 16) ^ 128));
                macAddrStr = partialMacAddr.toString();
            } catch (NumberFormatException e) {
                e.printStackTrace();
            } catch (Throwable th) {
                partialMacAddr.close();
                throw th;
            }
            partialMacAddr.close();
            return addr.substring(0, 12) + macAddrStr + addr.substring(14, addr.length());
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void checkAndSetConnectivityInstance() {
            if (WifiP2pServiceImpl.this.mCm == null) {
                WifiP2pServiceImpl wifiP2pServiceImpl = WifiP2pServiceImpl.this;
                wifiP2pServiceImpl.mCm = (ConnectivityManager) wifiP2pServiceImpl.mContext.getSystemService("connectivity");
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void checkAndReEnableP2p() {
            boolean isHalInterfaceAvailable = isHalInterfaceAvailable();
            Log.d(WifiP2pServiceImpl.TAG, "Wifi enabled=" + this.mIsWifiEnabled + ", P2P Interface availability=" + isHalInterfaceAvailable + ", Number of clients=" + WifiP2pServiceImpl.this.mDeathDataByBinder.size());
            if (this.mIsWifiEnabled && isHalInterfaceAvailable && !WifiP2pServiceImpl.this.mDeathDataByBinder.isEmpty()) {
                sendMessage(WifiP2pServiceImpl.ENABLE_P2P);
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean isHalInterfaceAvailable() {
            if (this.mWifiNative.isHalInterfaceSupported()) {
                return this.mIsHalInterfaceAvailable;
            }
            return true;
        }

        private void checkAndSendP2pStateChangedBroadcast() {
            boolean isHalInterfaceAvailable = isHalInterfaceAvailable();
            Log.d(WifiP2pServiceImpl.TAG, "Wifi enabled=" + this.mIsWifiEnabled + ", P2P Interface availability=" + isHalInterfaceAvailable);
            sendP2pStateChangedBroadcast(this.mIsWifiEnabled && isHalInterfaceAvailable);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void sendP2pStateChangedBroadcast(boolean enabled) {
            Intent intent = new Intent("android.net.wifi.p2p.STATE_CHANGED");
            intent.addFlags(67108864);
            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                logd("sendP2pStateChangedBroadcast : " + enabled);
            }
            if (enabled) {
                WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) enabling succeeded", 1);
                intent.putExtra("wifi_p2p_state", 2);
            } else {
                intent.putExtra("wifi_p2p_state", 1);
            }
            WifiP2pServiceImpl.this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void sendP2pDiscoveryChangedBroadcast(boolean started) {
            int i;
            if (WifiP2pServiceImpl.this.mDiscoveryStarted != started) {
                WifiP2pServiceImpl.this.mDiscoveryStarted = started;
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    logd("discovery change broadcast " + started);
                }
                Intent intent = new Intent("android.net.wifi.p2p.DISCOVERY_STATE_CHANGE");
                intent.addFlags(67108864);
                if (started) {
                    i = 2;
                } else {
                    i = 1;
                }
                intent.putExtra("discoveryState", i);
                WifiP2pServiceImpl.this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void sendThisDeviceChangedBroadcast() {
            Intent intent = new Intent("android.net.wifi.p2p.THIS_DEVICE_CHANGED");
            intent.addFlags(67108864);
            intent.putExtra("wifiP2pDevice", eraseOwnDeviceAddress(WifiP2pServiceImpl.this.mThisDevice));
            WifiP2pServiceImpl.this.mContext.sendBroadcastAsUserMultiplePermissions(intent, UserHandle.ALL, WifiP2pServiceImpl.RECEIVER_PERMISSIONS_FOR_BROADCAST);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void sendPeersChangedBroadcast() {
            Intent intent = new Intent("android.net.wifi.p2p.PEERS_CHANGED");
            intent.putExtra("wifiP2pDeviceList", new WifiP2pDeviceList(this.mPeers));
            intent.addFlags(67108864);
            intent.putExtra("p2pGroupInfo", new WifiP2pGroup(this.mGroup));
            intent.putExtra("connectedDevAddress", this.mConnectedDevAddr);
            WifiP2pServiceImpl.this.mContext.sendBroadcastAsUserMultiplePermissions(intent, UserHandle.ALL, WifiP2pServiceImpl.RECEIVER_PERMISSIONS_FOR_BROADCAST);
            if (this.mConnectedDevAddr != null) {
                this.mConnectedDevAddr = null;
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void sendP2pConnectionChangedBroadcast() {
            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                logd("sending p2p connection changed broadcast");
            }
            Intent intent = new Intent("android.net.wifi.p2p.CONNECTION_STATE_CHANGE");
            intent.addFlags(603979776);
            intent.putExtra("wifiP2pInfo", new WifiP2pInfo(this.mWifiP2pInfo));
            intent.putExtra("networkInfo", new NetworkInfo(WifiP2pServiceImpl.this.mNetworkInfo));
            intent.putExtra("connectedDevAddress", this.mConnectedDevAddr);
            intent.putExtra("connectedDevIntfAddress", this.mConnectedDevIntfAddr);
            intent.putExtra("p2pGroupInfo", eraseOwnDeviceAddress(this.mGroup));
            intent.putExtra("countWifiAntenna", WifiP2pServiceImpl.this.mCountWifiAntenna);
            WifiP2pServiceImpl.this.mContext.sendBroadcastAsUserMultiplePermissions(intent, UserHandle.ALL, WifiP2pServiceImpl.RECEIVER_PERMISSIONS_FOR_BROADCAST);
            intent.setPackage("com.samsung.android.allshare.service.fileshare");
            intent.addFlags(268435456);
            WifiP2pServiceImpl.this.mContext.sendBroadcastAsUserMultiplePermissions(intent, UserHandle.ALL, WifiP2pServiceImpl.RECEIVER_PERMISSIONS_FOR_BROADCAST);
            if (WifiP2pServiceImpl.this.mWifiChannel != null) {
                WifiP2pServiceImpl.this.mWifiChannel.sendMessage((int) WifiP2pServiceImpl.P2P_CONNECTION_CHANGED, new NetworkInfo(WifiP2pServiceImpl.this.mNetworkInfo));
            } else {
                loge("sendP2pConnectionChangedBroadcast(): WifiChannel is null");
            }
            if (this.mConnectedDevAddr != null) {
                this.mConnectedDevAddr = null;
            }
            if (this.mConnectedDevIntfAddr != null) {
                this.mConnectedDevIntfAddr = null;
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void sendP2pRequestChangedBroadcast(boolean accepted) {
            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                logd("sending p2p request changed broadcast");
            }
            Intent intent = new Intent("android.net.wifi.p2p.REQUEST_STATE_CHANGE");
            intent.addFlags(603979776);
            intent.putExtra("requestState", accepted);
            WifiP2pServiceImpl.this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void sendP2pPersistentGroupsChangedBroadcast() {
            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                logd("sending p2p persistent groups changed broadcast");
            }
            Intent intent = new Intent("android.net.wifi.p2p.PERSISTENT_GROUPS_CHANGED");
            intent.addFlags(67108864);
            WifiP2pServiceImpl.this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void startDhcpServer(String intf) {
            boolean foundP2pRange = false;
            try {
                InterfaceConfiguration ifcg = WifiP2pServiceImpl.this.mNwService.getInterfaceConfig(intf);
                ifcg.setLinkAddress(new LinkAddress(NetworkUtils.numericToInetAddress(WifiP2pServiceImpl.SERVER_ADDRESS), 24));
                ifcg.setInterfaceUp();
                WifiP2pServiceImpl.this.mNwService.setInterfaceConfig(intf, ifcg);
                String[] tetheringDhcpRanges = ((ConnectivityManager) WifiP2pServiceImpl.this.mContext.getSystemService("connectivity")).getTetheredDhcpRanges();
                if (WifiP2pServiceImpl.this.mNwService.isTetheringStarted()) {
                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                        logd("Stop existing tethering and restart it");
                    }
                    WifiP2pServiceImpl.this.mNwService.stopTethering();
                }
                WifiP2pServiceImpl.this.mNwService.tetherInterface(intf);
                if (tetheringDhcpRanges.length <= 0 || tetheringDhcpRanges.length % 2 != 0) {
                    WifiP2pServiceImpl.this.mNwService.startTethering(WifiP2pServiceImpl.DHCP_RANGE);
                } else {
                    int i = 0;
                    while (true) {
                        if (i >= tetheringDhcpRanges.length) {
                            break;
                        } else if (tetheringDhcpRanges[i].contains("192.168.49")) {
                            tetheringDhcpRanges[i] = "192.168.49.100";
                            tetheringDhcpRanges[i + 1] = "192.168.49.199";
                            WifiP2pServiceImpl.this.mNwService.startTethering(tetheringDhcpRanges);
                            foundP2pRange = true;
                            break;
                        } else {
                            i++;
                        }
                    }
                    if (!foundP2pRange) {
                        String[] tempDhcpRange = new String[(tetheringDhcpRanges.length + WifiP2pServiceImpl.DHCP_RANGE.length)];
                        System.arraycopy(tetheringDhcpRanges, 0, tempDhcpRange, 0, tetheringDhcpRanges.length);
                        System.arraycopy(WifiP2pServiceImpl.DHCP_RANGE, 0, tempDhcpRange, tetheringDhcpRanges.length, WifiP2pServiceImpl.DHCP_RANGE.length);
                        WifiP2pServiceImpl.this.mNwService.startTethering(tempDhcpRange);
                    }
                }
                logd("Started Dhcp server on " + intf);
            } catch (Exception e) {
                loge("Error configuring interface " + intf + ", :" + e);
            }
        }

        private void stopDhcpServer(String intf) {
            String str = "Stopped Dhcp server";
            try {
                WifiP2pServiceImpl.this.mNwService.untetherInterface(intf);
                String[] listTetheredInterfaces = WifiP2pServiceImpl.this.mNwService.listTetheredInterfaces();
                for (String temp : listTetheredInterfaces) {
                    logd("List all interfaces " + temp);
                    if (temp.compareTo(intf) != 0) {
                        str = "Found other tethering interfaces, so keep tethering alive";
                        logd(str);
                        return;
                    }
                }
                WifiP2pServiceImpl.this.mNwService.stopTethering();
                logd(str);
            } catch (Exception e) {
                loge("Error stopping Dhcp server" + e);
            } finally {
                logd(str);
            }
        }

        private Context chooseDisplayContext() {
            Context displayContext = null;
            SemDesktopModeManager desktopModeManager = (SemDesktopModeManager) WifiP2pServiceImpl.this.mContext.getSystemService("desktopmode");
            if (desktopModeManager != null) {
                SemDesktopModeState state = desktopModeManager.getDesktopModeState();
                logd("Desktop mode : " + state.enabled);
                if (state.enabled == 3 || state.enabled == 4) {
                    logd("Dex Mode enabled");
                    Display[] displays = ((DisplayManager) WifiP2pServiceImpl.this.mContext.getSystemService("display")).getDisplays("com.samsung.android.hardware.display.category.DESKTOP");
                    if (displays != null && displays.length > 0) {
                        displayContext = WifiP2pServiceImpl.this.mContext.createDisplayContext(displays[0]);
                        if (WifiP2pServiceImpl.this.isNightMode) {
                            displayContext.setTheme(16974120);
                        } else {
                            displayContext.setTheme(16974123);
                        }
                    }
                }
            }
            return displayContext;
        }

        private void notifyP2pEnableFailure() {
            Resources r = Resources.getSystem();
            Context context = WifiP2pServiceImpl.this.mUiContext;
            Context displayContext = chooseDisplayContext();
            if (displayContext == null) {
                displayContext = WifiP2pServiceImpl.this.mUiContext;
            }
            AlertDialog dialog = new AlertDialog.Builder(displayContext).setTitle(r.getString(17042717)).setMessage(r.getString(17042724)).setPositiveButton(r.getString(17039370), (DialogInterface.OnClickListener) null).create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.getWindow().setType(2003);
            WindowManager.LayoutParams attrs = dialog.getWindow().getAttributes();
            attrs.privateFlags = 16;
            dialog.getWindow().setAttributes(attrs);
            dialog.show();
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void showConnectionLimitDialog() {
            Resources r = Resources.getSystem();
            Context context = WifiP2pServiceImpl.this.mUiContext;
            Context displayContext = chooseDisplayContext();
            if (displayContext == null) {
                displayContext = WifiP2pServiceImpl.this.mUiContext;
            }
            if (WifiP2pServiceImpl.this.mMaximumConnectionDialog == null || !WifiP2pServiceImpl.this.mMaximumConnectionDialog.isShowing()) {
                WifiP2pServiceImpl.this.mMaximumConnectionDialog = new AlertDialog.Builder(displayContext).setTitle(r.getString(17042715)).setMessage(r.getString(17042716, Integer.valueOf(WifiP2pManager.MAX_CLIENT_SUPPORT))).setPositiveButton(r.getString(17039370), (DialogInterface.OnClickListener) null).create();
                WifiP2pServiceImpl.this.mMaximumConnectionDialog.getWindow().setType(2003);
                WifiP2pServiceImpl.this.mMaximumConnectionDialog.show();
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void showNoCommonChannelsDialog() {
            Resources r = Resources.getSystem();
            Context context = WifiP2pServiceImpl.this.mUiContext;
            Context displayContext = chooseDisplayContext();
            if (displayContext == null) {
                displayContext = WifiP2pServiceImpl.this.mUiContext;
            }
            AlertDialog dialog = new AlertDialog.Builder(displayContext).setTitle(r.getString(17042717)).setMessage(r.getString(17042729)).setPositiveButton(r.getString(17039370), (DialogInterface.OnClickListener) null).create();
            dialog.getWindow().setType(2003);
            dialog.show();
        }

        private void addRowToDialog(ViewGroup group, int stringId, String value) {
            Resources r = Resources.getSystem();
            Context context = WifiP2pServiceImpl.this.mUiContext;
            Context displayContext = chooseDisplayContext();
            if (displayContext == null) {
                displayContext = WifiP2pServiceImpl.this.mUiContext;
            }
            View row = LayoutInflater.from(displayContext).inflate(17367467, group, false);
            ((TextView) row.findViewById(16909212)).setText(r.getString(stringId));
            ((TextView) row.findViewById(16909777)).setText(value);
            if (WifiP2pServiceImpl.this.mUiContext == WifiP2pServiceImpl.this.mUiContextNight) {
                ((TextView) row.findViewById(16909777)).setTextColor(WifiP2pServiceImpl.this.mContext.getResources().getColor(17171302));
            }
            group.addView(row);
        }

        private void addMsgToDialog(ViewGroup group, int stringId, String value) {
            Resources r = Resources.getSystem();
            Context context = WifiP2pServiceImpl.this.mUiContext;
            Context displayContext = chooseDisplayContext();
            if (displayContext == null) {
                displayContext = WifiP2pServiceImpl.this.mUiContext;
            }
            View row = LayoutInflater.from(displayContext).inflate(17367467, group, false);
            WifiP2pServiceImpl.this.mLapseTime = 30;
            WifiP2pServiceImpl.this.mInvitationMsg = (TextView) row.findViewById(16909777);
            WifiP2pServiceImpl.this.mInvitationMsg.setText(r.getString(stringId, Integer.valueOf(WifiP2pServiceImpl.this.mLapseTime), value));
            if (WifiP2pServiceImpl.this.mUiContext == WifiP2pServiceImpl.this.mUiContextNight) {
                WifiP2pServiceImpl.this.mInvitationMsg.setTextColor(WifiP2pServiceImpl.this.mContext.getResources().getColor(17171302));
            }
            ((TextView) row.findViewById(16909212)).setVisibility(8);
            group.addView(row);
        }

        private void addPluralsMsgToDialog(ViewGroup group, int stringId, String value) {
            Resources r = Resources.getSystem();
            Context context = WifiP2pServiceImpl.this.mUiContext;
            Context displayContext = chooseDisplayContext();
            if (displayContext == null) {
                displayContext = WifiP2pServiceImpl.this.mUiContext;
            }
            View row = LayoutInflater.from(displayContext).inflate(17367467, group, false);
            WifiP2pServiceImpl.this.mLapseTime = 30;
            WifiP2pServiceImpl.this.mInvitationMsg = (TextView) row.findViewById(16909777);
            WifiP2pServiceImpl.this.mInvitationMsg.setText(r.getQuantityString(stringId, WifiP2pServiceImpl.this.mLapseTime, value, Integer.valueOf(WifiP2pServiceImpl.this.mLapseTime)));
            if (WifiP2pServiceImpl.this.mUiContext == WifiP2pServiceImpl.this.mUiContextNight) {
                WifiP2pServiceImpl.this.mInvitationMsg.setTextColor(WifiP2pServiceImpl.this.mContext.getResources().getColor(17171302));
            }
            ((TextView) row.findViewById(16909212)).setVisibility(8);
            group.addView(row);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void notifyInvitationSent(String pin, String peerAddress) {
            Resources r = Resources.getSystem();
            Context context = WifiP2pServiceImpl.this.mUiContext;
            Context displayContext = chooseDisplayContext();
            if (displayContext == null) {
                displayContext = WifiP2pServiceImpl.this.mUiContext;
            }
            View textEntryView = LayoutInflater.from(displayContext).inflate(17367466, (ViewGroup) null);
            ViewGroup group = (ViewGroup) textEntryView.findViewById(16909067);
            addRowToDialog(group, 17042732, getDeviceName(peerAddress));
            addRowToDialog(group, 17042731, pin);
            AlertDialog dialog = new AlertDialog.Builder(displayContext).setTitle(r.getString(17042727)).setView(textEntryView).setPositiveButton(r.getString(17039370), (DialogInterface.OnClickListener) null).create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.getWindow().setType(2003);
            WindowManager.LayoutParams attrs = dialog.getWindow().getAttributes();
            attrs.privateFlags = 16;
            dialog.getWindow().setAttributes(attrs);
            dialog.show();
        }

        private void notifyP2pProvDiscShowPinRequest(String pin, String peerAddress) {
            Resources r = Resources.getSystem();
            View textEntryView = LayoutInflater.from(WifiP2pServiceImpl.this.mContext).inflate(17367466, (ViewGroup) null);
            ViewGroup group = (ViewGroup) textEntryView.findViewById(16909067);
            addRowToDialog(group, 17042732, getDeviceName(peerAddress));
            addRowToDialog(group, 17042731, pin);
            AlertDialog dialog = new AlertDialog.Builder(WifiP2pServiceImpl.this.mContext).setTitle(r.getString(17042727)).setView(textEntryView).setPositiveButton(r.getString(17039481), new DialogInterface.OnClickListener() {
                /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.DialogInterface$OnClickListenerC05395 */

                public void onClick(DialogInterface dialog, int which) {
                    P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.PEER_CONNECTION_USER_CONFIRM);
                }
            }).create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.getWindow().setType(2003);
            WindowManager.LayoutParams attrs = dialog.getWindow().getAttributes();
            attrs.privateFlags = 16;
            dialog.getWindow().setAttributes(attrs);
            dialog.show();
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void notifyInvitationReceived() {
            if (!WifiP2pServiceImpl.this.isAllowWifiDirectByEDM()) {
                sendMessage(WifiP2pServiceImpl.PEER_CONNECTION_USER_REJECT);
                WifiP2pServiceImpl.this.mLapseTime = 30;
                if (WifiP2pServiceImpl.this.mWpsTimer != null) {
                    WifiP2pServiceImpl.this.mWpsTimer.cancel();
                }
                WifiP2pServiceImpl.this.mDialogWakeLock.release();
                return;
            }
            if (WifiP2pServiceImpl.this.mWpsTimer != null) {
                WifiP2pServiceImpl.this.mWpsTimer.cancel();
            }
            if (WifiP2pServiceImpl.this.mInvitationDialog != null && WifiP2pServiceImpl.this.mInvitationDialog.isShowing()) {
                WifiP2pServiceImpl.this.mInvitationDialog.dismiss();
                WifiP2pServiceImpl.this.mInvitationDialog = null;
            }
            Context context = WifiP2pServiceImpl.this.mUiContext;
            Context displayContext = chooseDisplayContext();
            if (displayContext == null) {
                displayContext = WifiP2pServiceImpl.this.mUiContext;
            }
            Resources r = Resources.getSystem();
            final WpsInfo wps = this.mSavedPeerConfig.wps;
            View textEntryView = LayoutInflater.from(displayContext).inflate(17367466, (ViewGroup) null);
            ViewGroup group = (ViewGroup) textEntryView.findViewById(16909067);
            if (getDeviceName(this.mSavedPeerConfig.deviceAddress).equals(this.mSavedPeerConfig.deviceAddress)) {
                this.mWifiNative.p2pFind();
            }
            addPluralsMsgToDialog(group, 18153514, getDeviceName(this.mSavedPeerConfig.deviceAddress));
            WifiP2pServiceImpl.this.pinConn = (EditText) textEntryView.findViewById(16909800);
            final GestureDetector gestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
                /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.C05406 */

                public boolean onDoubleTap(MotionEvent e) {
                    return true;
                }

                public boolean onDoubleTapEvent(MotionEvent e) {
                    return true;
                }

                public boolean onDown(MotionEvent e) {
                    return true;
                }

                public void onLongPress(MotionEvent e) {
                }

                public void onShowPress(MotionEvent e) {
                }
            });
            WifiP2pServiceImpl.this.pinConn.setOnTouchListener(new View.OnTouchListener() {
                /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.View$OnTouchListenerC05417 */

                public boolean onTouch(View v, MotionEvent event) {
                    return gestureDetector.onTouchEvent(event);
                }
            });
            WifiP2pServiceImpl.this.mDialogWakeLock.acquire(30000);
            WifiP2pServiceImpl.this.mInvitationDialog = new AlertDialog.Builder(displayContext).setTitle(r.getString(17042728)).setView(textEntryView).setPositiveButton(r.getString(17042713), new DialogInterface.OnClickListener() {
                /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.DialogInterface$OnClickListenerC053211 */

                public void onClick(DialogInterface dialog, int which) {
                    if (P2pStateMachine.this.mSavedPeerConfig != null) {
                        if (wps.setup == 2) {
                            P2pStateMachine.this.mSavedPeerConfig.wps.pin = WifiP2pServiceImpl.this.pinConn.getText().toString();
                        }
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                            p2pStateMachine.logd(P2pStateMachine.this.getName() + " accept invitation " + P2pStateMachine.this.mSavedPeerConfig.deviceAddress);
                        }
                    }
                    WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) invitation accepted");
                    P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.PEER_CONNECTION_USER_ACCEPT);
                    WifiP2pServiceImpl.this.mLapseTime = 30;
                    WifiP2pServiceImpl.this.mWpsTimer.cancel();
                    WifiP2pServiceImpl.this.mDialogWakeLock.release();
                    WifiP2pServiceImpl.this.imm = (InputMethodManager) WifiP2pServiceImpl.this.mContext.getSystemService("input_method");
                    if (WifiP2pServiceImpl.this.imm != null) {
                        WifiP2pServiceImpl.this.imm.hideSoftInputFromWindow(WifiP2pServiceImpl.this.pinConn.getWindowToken(), 0);
                    }
                }
            }).setNegativeButton(r.getString(17042712), new DialogInterface.OnClickListener() {
                /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.DialogInterface$OnClickListenerC053110 */

                public void onClick(DialogInterface dialog, int which) {
                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                        P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                        p2pStateMachine.logd(P2pStateMachine.this.getName() + " ignore connect");
                    }
                    WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) invitation denied");
                    P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.PEER_CONNECTION_USER_REJECT);
                    WifiP2pServiceImpl.this.mLapseTime = 30;
                    WifiP2pServiceImpl.this.mWpsTimer.cancel();
                    WifiP2pServiceImpl.this.mDialogWakeLock.release();
                    WifiP2pServiceImpl.this.imm = (InputMethodManager) WifiP2pServiceImpl.this.mContext.getSystemService("input_method");
                    if (WifiP2pServiceImpl.this.imm != null) {
                        WifiP2pServiceImpl.this.imm.hideSoftInputFromWindow(WifiP2pServiceImpl.this.pinConn.getWindowToken(), 0);
                    }
                }
            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.DialogInterface$OnCancelListenerC05439 */

                public void onCancel(DialogInterface arg0) {
                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                        P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                        p2pStateMachine.logd(P2pStateMachine.this.getName() + " ignore connect");
                    }
                    P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.PEER_CONNECTION_USER_REJECT);
                    WifiP2pServiceImpl.this.auditLog(true, "Wi-Fi Direct (P2P) invitation denied");
                    WifiP2pServiceImpl.this.mLapseTime = 30;
                    WifiP2pServiceImpl.this.mWpsTimer.cancel();
                    WifiP2pServiceImpl.this.mDialogWakeLock.release();
                    WifiP2pServiceImpl.this.imm = (InputMethodManager) WifiP2pServiceImpl.this.mContext.getSystemService("input_method");
                    if (WifiP2pServiceImpl.this.imm != null) {
                        WifiP2pServiceImpl.this.imm.forceHideSoftInput();
                    }
                }
            }).setOnDismissListener(new DialogInterface.OnDismissListener() {
                /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.DialogInterface$OnDismissListenerC05428 */

                public void onDismiss(DialogInterface dialog) {
                    WifiP2pServiceImpl.this.imm = (InputMethodManager) WifiP2pServiceImpl.this.mContext.getSystemService("input_method");
                    if (WifiP2pServiceImpl.this.imm != null) {
                        WifiP2pServiceImpl.this.imm.hideSoftInputFromWindow(WifiP2pServiceImpl.this.pinConn.getWindowToken(), 0);
                    }
                    WifiP2pServiceImpl.this.setProp("closeInvitationDialog");
                }
            }).create();
            textEntryView.setFocusable(false);
            int i = wps.setup;
            if (i == 1) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    logd("Shown pin section visible");
                }
                addRowToDialog(group, 17042731, wps.pin);
            } else if (i == 2) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    logd("Enter pin section visible");
                }
                textEntryView.findViewById(16908928).setVisibility(0);
                WifiP2pServiceImpl.this.pinConn.requestFocus();
            }
            if ((r.getConfiguration().uiMode & 5) == 5) {
                WifiP2pServiceImpl.this.mInvitationDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                    /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.DialogInterface$OnKeyListenerC053312 */

                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode != 164) {
                            return false;
                        }
                        P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.PEER_CONNECTION_USER_ACCEPT);
                        dialog.dismiss();
                        return true;
                    }
                });
            }
            WifiP2pServiceImpl.this.mInvitationDialog.getWindow().setType(2008);
            WifiP2pServiceImpl.this.mInvitationDialog.getWindow().setGravity(80);
            WindowManager.LayoutParams attrs = WifiP2pServiceImpl.this.mInvitationDialog.getWindow().getAttributes();
            attrs.privateFlags = 16;
            WifiP2pServiceImpl.this.mInvitationDialog.getWindow().setAttributes(attrs);
            if (WifiP2pServiceImpl.this.pinConn.hasFocus()) {
                WifiP2pServiceImpl.this.mInvitationDialog.getWindow().setSoftInputMode(5);
            }
            WifiP2pServiceImpl.this.mInvitationDialog.show();
            if (wps.setup == 2) {
                WifiP2pServiceImpl.this.mInvitationDialog.getButton(-1).setEnabled(false);
                WifiP2pServiceImpl.this.pinConn.addTextChangedListener(new TextWatcher() {
                    /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.C053413 */

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    public void afterTextChanged(Editable s) {
                        if (s.length() == 4 || s.length() == 8) {
                            WifiP2pServiceImpl.this.mInvitationDialog.getButton(-1).setEnabled(true);
                        } else {
                            WifiP2pServiceImpl.this.mInvitationDialog.getButton(-1).setEnabled(false);
                        }
                    }
                });
            }
            WifiP2pServiceImpl.this.mWpsTimer = new CountDownTimer(30000, 1000) {
                /* class com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStateMachine.CountDownTimerC053514 */

                public void onTick(long millisUntilFinished) {
                    if (P2pStateMachine.this.mSavedPeerConfig != null) {
                        WifiP2pServiceImpl.access$24410(WifiP2pServiceImpl.this);
                        TextView textView = WifiP2pServiceImpl.this.mInvitationMsg;
                        Resources system = Resources.getSystem();
                        int i = WifiP2pServiceImpl.this.mLapseTime;
                        P2pStateMachine p2pStateMachine = P2pStateMachine.this;
                        textView.setText(system.getQuantityString(18153514, i, p2pStateMachine.getDeviceName(p2pStateMachine.mSavedPeerConfig.deviceAddress), Integer.valueOf(WifiP2pServiceImpl.this.mLapseTime)));
                    }
                }

                public void onFinish() {
                    WifiP2pServiceImpl.this.mLapseTime = 30;
                    P2pStateMachine.this.sendMessage(WifiP2pServiceImpl.PEER_CONNECTION_USER_REJECT);
                    WifiP2pServiceImpl.this.imm = (InputMethodManager) WifiP2pServiceImpl.this.mContext.getSystemService("input_method");
                    if (WifiP2pServiceImpl.this.imm != null) {
                        WifiP2pServiceImpl.this.imm.hideSoftInputFromWindow(WifiP2pServiceImpl.this.pinConn.getWindowToken(), 0);
                    }
                    WifiP2pServiceImpl.this.mInvitationDialog.dismiss();
                }
            }.start();
            WifiP2pServiceImpl.this.setProp("openInvitationDialog");
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void notifyInvitationReceivedForceAccept() {
            if (this.mSavedPeerConfig.wps.setup == 0) {
                this.mWifiNative.startWpsPbc(this.mGroup.getInterface(), null);
            } else {
                this.mWifiNative.startWpsPinKeypad(this.mGroup.getInterface(), this.mSavedPeerConfig.wps.pin);
            }
            this.mSavedPeerConfig = null;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void updatePersistentNetworks(boolean reload) {
            if (reload) {
                this.mGroups.clear();
            }
            if (this.mWifiNative.p2pListNetworks(this.mGroups) || reload) {
                for (WifiP2pGroup group : this.mGroups.getGroupList()) {
                    if (!(WifiP2pServiceImpl.this.mThisDevice == null || WifiP2pServiceImpl.this.mThisDevice.deviceAddress == null || group.getOwner() == null || !WifiP2pServiceImpl.this.mThisDevice.deviceAddress.equals(group.getOwner().deviceAddress))) {
                        group.setOwner(WifiP2pServiceImpl.this.mThisDevice);
                    }
                }
                this.mWifiNative.saveConfig();
                WifiP2pServiceImpl.this.mWifiP2pMetrics.updatePersistentGroup(this.mGroups);
                sendP2pPersistentGroupsChangedBroadcast();
            }
        }

        private void removePersistentNetworks() {
            this.mGroups.clear();
            if (this.mWifiNative.p2pRemoveNetworks()) {
                this.mWifiNative.saveConfig();
                sendP2pPersistentGroupsChangedBroadcast();
            }
            this.mWifiNative.p2pListNetworks(this.mGroups);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void stopLegacyWifiScan(boolean flag) {
            WifiManager tWifiManager;
            if (WifiP2pServiceImpl.this.mWifiState == 3 && (tWifiManager = (WifiManager) WifiP2pServiceImpl.this.mContext.getSystemService("wifi")) != null) {
                Message msg = new Message();
                msg.what = 74;
                Bundle args = new Bundle();
                args.putBoolean("enable", flag);
                msg.obj = args;
                tWifiManager.callSECApi(msg);
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void setLegacyWifiEnable(boolean flag) {
            WifiManager tWifiManager = (WifiManager) WifiP2pServiceImpl.this.mContext.getSystemService("wifi");
            if (tWifiManager == null) {
                return;
            }
            if (flag) {
                Message msg = new Message();
                msg.what = 26;
                Bundle args = new Bundle();
                args.putBoolean("enable", flag);
                msg.obj = args;
                tWifiManager.callSECApi(msg);
                return;
            }
            tWifiManager.setWifiEnabled(flag);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void stopLegacyWifiPeriodicScan(boolean flag) {
            WifiManager tWifiManager;
            if (WifiP2pServiceImpl.this.mWifiState == 3 && (tWifiManager = (WifiManager) WifiP2pServiceImpl.this.mContext.getSystemService("wifi")) != null) {
                Message msg = new Message();
                msg.what = 18;
                Bundle args = new Bundle();
                args.putBoolean("stop", flag);
                msg.obj = args;
                tWifiManager.callSECApi(msg);
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean isConfigInvalid(WifiP2pConfig config) {
            if (config == null || TextUtils.isEmpty(config.deviceAddress) || this.mPeers.get(config.deviceAddress) == null) {
                return true;
            }
            return false;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean isConfigValidAsGroup(WifiP2pConfig config) {
            if (config != null && !TextUtils.isEmpty(config.deviceAddress) && !TextUtils.isEmpty(config.networkName) && !TextUtils.isEmpty(config.passphrase)) {
                return true;
            }
            return false;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private WifiP2pDevice fetchCurrentDeviceDetails(WifiP2pConfig config) {
            if (config == null) {
                return null;
            }
            this.mPeers.updateGroupCapability(config.deviceAddress, this.mWifiNative.getGroupCapability(config.deviceAddress));
            return this.mPeers.get(config.deviceAddress);
        }

        private WifiP2pDevice eraseOwnDeviceAddress(WifiP2pDevice device) {
            if (device == null) {
                return null;
            }
            WifiP2pDevice result = new WifiP2pDevice(device);
            if (device.deviceAddress != null && WifiP2pServiceImpl.this.mThisDevice.deviceAddress != null && device.deviceAddress.length() > 0 && WifiP2pServiceImpl.this.mThisDevice.deviceAddress.equals(device.deviceAddress)) {
                result.deviceAddress = WifiP2pServiceImpl.ANONYMIZED_DEVICE_ADDRESS;
            }
            return result;
        }

        private WifiP2pGroup eraseOwnDeviceAddress(WifiP2pGroup group) {
            if (group == null) {
                return null;
            }
            WifiP2pGroup result = new WifiP2pGroup(group);
            for (WifiP2pDevice originalDevice : group.getClientList()) {
                result.removeClient(originalDevice);
                result.addClient(eraseOwnDeviceAddress(originalDevice));
            }
            result.setOwner(eraseOwnDeviceAddress(group.getOwner()));
            return result;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private WifiP2pDevice maybeEraseOwnDeviceAddress(WifiP2pDevice device, int uid) {
            if (device == null) {
                return null;
            }
            if (WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkLocalMacAddressPermission(uid)) {
                return new WifiP2pDevice(device);
            }
            return eraseOwnDeviceAddress(device);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private WifiP2pGroup maybeEraseOwnDeviceAddress(WifiP2pGroup group, int uid) {
            if (group == null) {
                return null;
            }
            if (WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkLocalMacAddressPermission(uid)) {
                return new WifiP2pGroup(group);
            }
            return eraseOwnDeviceAddress(group);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private WifiP2pGroupList maybeEraseOwnDeviceAddress(WifiP2pGroupList groupList, int uid) {
            if (groupList == null) {
                return null;
            }
            WifiP2pGroupList result = new WifiP2pGroupList();
            for (WifiP2pGroup group : groupList.getGroupList()) {
                result.add(maybeEraseOwnDeviceAddress(group, uid));
            }
            return result;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void p2pConnectWithPinDisplay(WifiP2pConfig config) {
            int modeValue;
            WifiManager tWifiManager;
            NetworkInfo netInfo;
            if (config == null) {
                Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                return;
            }
            WifiP2pDevice dev = fetchCurrentDeviceDetails(config);
            if (dev == null) {
                Log.e(WifiP2pServiceImpl.TAG, "Invalid device");
                return;
            }
            boolean join = dev.isGroupOwner() || WifiP2pServiceImpl.this.mJoinExistingGroup;
            if (!join && ((config.groupOwnerIntent < 0 || config.groupOwnerIntent > 15) && (tWifiManager = (WifiManager) WifiP2pServiceImpl.this.mContext.getSystemService("wifi")) != null && (netInfo = tWifiManager.getNetworkInfo()) != null && netInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED)) {
                int frequency = -1;
                if (tWifiManager.getConnectionInfo() != null) {
                    frequency = tWifiManager.getConnectionInfo().getFrequency();
                }
                if (frequency >= 5170) {
                    config.groupOwnerIntent = 8;
                } else {
                    config.groupOwnerIntent = 7;
                }
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    Log.d(WifiP2pServiceImpl.TAG, "set groupOwnerIntent : " + config.groupOwnerIntent);
                }
            }
            if (!join && config.wps.setup != 5 && WifiP2pServiceImpl.this.mThisDevice.wfdInfo != null && WifiP2pServiceImpl.this.mThisDevice.wfdInfo.isWfdEnabled() && (WifiP2pServiceImpl.this.mThisDevice.wfdInfo.getDeviceType() & 1) == 0) {
                int modeValue2 = 0;
                Integer mode = (Integer) WifiP2pServiceImpl.this.mModeChange.get(config.deviceAddress);
                if (mode != null) {
                    modeValue2 = mode.intValue();
                }
                if (modeValue == 0) {
                    if (WifiP2pServiceImpl.this.mConnReqInfo != null && config.groupOwnerIntent < WifiP2pServiceImpl.this.mConnReqInfo.getPeerGOIntentValue()) {
                        modeValue |= 4;
                    }
                    if (config.netId == -2) {
                        modeValue |= 8;
                    }
                } else {
                    if ((modeValue & 3) == 3) {
                        if (modeValue >= 11) {
                            modeValue = 0;
                        } else {
                            modeValue++;
                        }
                        logi("Connection Mode Change");
                    }
                    if ((modeValue & 4) > 0) {
                        config.groupOwnerIntent = 0;
                    }
                    if ((modeValue & 8) > 0) {
                        config.netId = -2;
                    }
                }
                logd("Connection Mode : " + modeValue);
                WifiP2pServiceImpl.this.mModeChange.put(config.deviceAddress, Integer.valueOf(modeValue));
            }
            WifiP2pServiceImpl.this.mReinvokePersistentNetId = -1;
            String pin = this.mWifiNative.p2pConnect(config, join);
            try {
                Integer.parseInt(pin);
                notifyInvitationSent(pin, config.deviceAddress);
            } catch (NumberFormatException e) {
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean reinvokePersistentGroup(WifiP2pConfig config) {
            int netId;
            if (config == null) {
                Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                return false;
            } else if (config.wps.setup == 2) {
                return false;
            } else {
                WifiP2pDevice dev = fetchCurrentDeviceDetails(config);
                if (dev == null) {
                    Log.e(WifiP2pServiceImpl.TAG, "Invalid device");
                    return false;
                }
                boolean join = dev.isGroupOwner();
                String ssid = this.mWifiNative.p2pGetSsid(dev.deviceAddress);
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    logd("target ssid is " + ssid + " join:" + join);
                }
                if (join && dev.isGroupLimit()) {
                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                        logd("target device reaches group limit.");
                    }
                    join = false;
                } else if (join && (netId = this.mGroups.getNetworkId(dev.deviceAddress, ssid)) >= 0) {
                    if (!this.mWifiNative.p2pGroupAdd(netId)) {
                        return false;
                    }
                    return true;
                }
                String modelNumber = this.mWifiNative.p2pGetModelName(dev.deviceAddress);
                if (modelNumber == null || !modelNumber.equals("TigaMini")) {
                    if (dev.deviceName != null) {
                        if (dev.deviceName.equals("IM10")) {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                logd("target device is sony dongle(IM10)");
                            }
                            config.groupOwnerIntent = 15;
                            config.netId = -1;
                            return false;
                        } else if (dev.deviceName.startsWith("SMARTBEAM_")) {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                logd("target device is SMARTBEAM_");
                            }
                            config.groupOwnerIntent = 1;
                        }
                    }
                    if (join || !dev.isDeviceLimit()) {
                        if (!join && dev.isInvitationCapable()) {
                            int netId2 = -2;
                            if (config.netId < 0) {
                                netId2 = this.mGroups.getNetworkId(dev.deviceAddress);
                            } else if (config.deviceAddress.equals(this.mGroups.getOwnerAddr(config.netId))) {
                                netId2 = config.netId;
                            }
                            if (netId2 < 0) {
                                if (config.netId == -1) {
                                    return false;
                                }
                                netId2 = getNetworkIdFromClientList(dev.deviceAddress);
                            }
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                logd("netId related with " + dev.deviceAddress + " = " + netId2);
                            }
                            if (netId2 >= 0) {
                                if (this.mWifiNative.p2pReinvoke(netId2, dev.deviceAddress)) {
                                    config.netId = netId2;
                                    WifiP2pServiceImpl.this.mReinvokePersistentNetId = netId2;
                                    WifiP2pServiceImpl.this.mReinvokePersistent = dev.deviceAddress;
                                    return true;
                                }
                                loge("p2pReinvoke() failed, update networks");
                                updatePersistentNetworks(WifiP2pServiceImpl.RELOAD.booleanValue());
                                return false;
                            }
                        }
                        return false;
                    }
                    loge("target device reaches the device limit.");
                    return false;
                }
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    logd("target device doesnot support persistent group");
                }
                config.groupOwnerIntent = 5;
                config.netId = -1;
                return false;
            }
        }

        private int getNetworkIdFromClientList(String deviceAddress) {
            if (deviceAddress == null) {
                return -1;
            }
            for (WifiP2pGroup group : this.mGroups.getGroupList()) {
                int netId = group.getNetworkId();
                String[] p2pClientList = getClientList(netId);
                if (p2pClientList != null) {
                    for (String client : p2pClientList) {
                        if (deviceAddress.equalsIgnoreCase(client)) {
                            return netId;
                        }
                    }
                    continue;
                }
            }
            return -1;
        }

        private String[] getClientList(int netId) {
            String p2pClients = this.mWifiNative.getP2pClientList(netId);
            if (p2pClients == null) {
                return null;
            }
            return p2pClients.split(" ");
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean removeClientFromList(int netId, String addr, boolean isRemovable) {
            StringBuilder modifiedClientList = new StringBuilder();
            String[] currentClientList = getClientList(netId);
            boolean isClientRemoved = false;
            if (currentClientList != null) {
                boolean isClientRemoved2 = false;
                for (String client : currentClientList) {
                    if (!client.equalsIgnoreCase(addr)) {
                        modifiedClientList.append(" ");
                        modifiedClientList.append(client);
                    } else {
                        isClientRemoved2 = true;
                    }
                }
                isClientRemoved = isClientRemoved2;
            }
            if (modifiedClientList.length() == 0 && isRemovable) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    logd("Remove unknown network");
                }
                this.mGroups.remove(netId);
                WifiP2pServiceImpl.this.mWifiP2pMetrics.updatePersistentGroup(this.mGroups);
                return true;
            } else if (!isClientRemoved) {
                return false;
            } else {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    logd("Modified client list: " + ((Object) modifiedClientList));
                }
                if (modifiedClientList.length() == 0) {
                    modifiedClientList.append("\"\"");
                }
                this.mWifiNative.setP2pClientList(netId, modifiedClientList.toString());
                this.mWifiNative.saveConfig();
                return true;
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void setWifiP2pInfoOnGroupFormation(InetAddress serverInetAddress) {
            WifiP2pInfo wifiP2pInfo = this.mWifiP2pInfo;
            wifiP2pInfo.groupFormed = true;
            wifiP2pInfo.isGroupOwner = this.mGroup.isGroupOwner();
            this.mWifiP2pInfo.groupOwnerAddress = serverInetAddress;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void resetWifiP2pInfo() {
            WifiP2pInfo wifiP2pInfo = this.mWifiP2pInfo;
            wifiP2pInfo.groupFormed = false;
            wifiP2pInfo.isGroupOwner = false;
            wifiP2pInfo.groupOwnerAddress = null;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private String getDeviceName(String deviceAddress) {
            WifiP2pDevice d = this.mPeers.get(deviceAddress);
            if (d != null) {
                return d.deviceName;
            }
            return deviceAddress;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private String getPersistedDeviceName() {
            String deviceName = WifiP2pServiceImpl.this.mFrameworkFacade.getStringSetting(WifiP2pServiceImpl.this.mContext, "wifi_p2p_device_name");
            if (deviceName != null) {
                return deviceName;
            }
            String id = WifiP2pServiceImpl.this.mFrameworkFacade.getSecureStringSetting(WifiP2pServiceImpl.this.mContext, "android_id");
            return "Android_" + id.substring(0, 4);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean setAndPersistDeviceName(String devName) {
            if (devName == null) {
                return false;
            }
            if (devName.getBytes().length > 32) {
                devName = cutString(devName, 32);
            }
            if (!this.mWifiNative.setDeviceName(devName)) {
                loge("Failed to set device name " + devName);
                return false;
            }
            WifiP2pServiceImpl.this.mThisDevice.deviceName = devName;
            WifiP2pServiceImpl.this.mFrameworkFacade.setStringSetting(WifiP2pServiceImpl.this.mContext, "wifi_p2p_device_name", devName);
            sendThisDeviceChangedBroadcast();
            return true;
        }

        public String cutString(String str, int cutByte) {
            StringBuilder ret = new StringBuilder();
            int i = 0;
            while (i < str.length()) {
                int chCount = Character.charCount(str.codePointAt(i));
                int length = cutByte - str.substring(i, i + chCount).getBytes().length;
                cutByte = length;
                if (length < 0) {
                    break;
                }
                ret.append(str.substring(i, i + chCount));
                i += chCount;
            }
            return ret.toString();
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean setWfdInfo(WifiP2pWfdInfo wfdInfo) {
            boolean success;
            if (!wfdInfo.isWfdEnabled()) {
                this.mWifiNative.setConfigMethods("virtual_push_button physical_display keypad ext_nfc_token nfc_interface");
                success = this.mWifiNative.setWfdEnable(false);
            } else {
                if (wfdInfo.getDeviceType() != 0) {
                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                        logd("WFD sink device does not support PIN display method");
                    }
                    this.mWifiNative.setConfigMethods("virtual_push_button ext_nfc_token nfc_interface");
                } else {
                    this.mWifiNative.setConfigMethods("virtual_push_button physical_display keypad ext_nfc_token nfc_interface");
                }
                success = this.mWifiNative.setWfdEnable(true) && this.mWifiNative.setWfdDeviceInfo(wfdInfo.getDeviceInfoHex());
            }
            if (!success) {
                loge("Failed to set wfd properties");
                return false;
            }
            WifiP2pServiceImpl.this.mThisDevice.wfdInfo = wfdInfo;
            sendThisDeviceChangedBroadcast();
            return true;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void convertDeviceNameNSetIconViaContact(WifiP2pDevice dev) {
            String hashInfo = dev.contactInfoHash;
            String hash = null;
            String crc = null;
            String phoneNumber = null;
            if (hashInfo.length() >= 10) {
                hash = hashInfo.substring(0, 6);
                crc = hashInfo.substring(6, 10);
            }
            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                logd("Peer contact info - hash : " + hash + ", crc : " + crc);
            }
            if (!(hash == null || crc == null)) {
                phoneNumber = Hash.retrieveDB(WifiP2pServiceImpl.this.mContext, hash, crc);
            }
            if (phoneNumber != null) {
                Util.retrieveContact(WifiP2pServiceImpl.this.mContext, phoneNumber);
            }
            if (dev.contactImage == null) {
                dev.contactImage = GUIUtil.cropIcon(WifiP2pServiceImpl.this.mContext, 17106259, GUIUtil.getContactImage(WifiP2pServiceImpl.this.mContext, phoneNumber));
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void setPhoneNumberIntoProbeResp() {
            String contactHash = null;
            String contactCRC = null;
            String phoneNumber = Util.getMyMobileNumber(WifiP2pServiceImpl.this.mContext);
            if (phoneNumber == null) {
                loge("Can't set my contact info since my phone number is not provided by some reason");
                return;
            }
            String myNumber = Util.cutNumber(phoneNumber);
            byte[] unused = WifiP2pServiceImpl.hash_byte = Hash.getSipHashByte(myNumber);
            byte[] unused2 = WifiP2pServiceImpl.hash2_byte = Hash.getDataCheckByte(myNumber);
            if (WifiP2pServiceImpl.hash_byte != null) {
                contactHash = Util.byteToString(WifiP2pServiceImpl.hash_byte);
            }
            if (WifiP2pServiceImpl.hash2_byte != null) {
                contactCRC = Util.byteToString(WifiP2pServiceImpl.hash2_byte);
            }
            if (contactHash == null || contactCRC == null) {
                loge("Can't set my contact info since the hash info is not created well");
                return;
            }
            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                logd("My contact info - hash : " + contactHash + ", crc : " + contactCRC);
            }
            WifiP2pNative wifiP2pNative = this.mWifiNative;
            wifiP2pNative.p2pSet("phone_number", contactHash + contactCRC);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void initializeP2pSettings() {
            this.mWifiInterface = this.mWifiLegacyNative.getClientInterfaceName();
            WifiP2pServiceImpl.this.checkDeviceNameInSettings();
            if (TextUtils.isEmpty(WifiP2pServiceImpl.this.mDeviceNameInSettings)) {
                WifiP2pServiceImpl.this.mDeviceNameInSettings = getPersistedDeviceName();
            }
            WifiP2pServiceImpl.this.mThisDevice.deviceName = WifiP2pServiceImpl.this.mDeviceNameInSettings;
            if (WifiP2pServiceImpl.isTablet()) {
                WifiP2pServiceImpl.this.idxIcon = 512;
            } else {
                WifiP2pServiceImpl.this.idxIcon = 256;
            }
            this.mWifiNative.p2pSet("discovery_icon", Integer.toString(WifiP2pServiceImpl.this.idxIcon));
            if (WifiP2pServiceImpl.this.mThisDevice.deviceName != null && WifiP2pServiceImpl.this.mThisDevice.deviceName.getBytes().length > 32) {
                WifiP2pServiceImpl.this.mThisDevice.deviceName = cutString(WifiP2pServiceImpl.this.mThisDevice.deviceName, 32);
            }
            if (!this.mWifiNative.setDeviceName(WifiP2pServiceImpl.this.mThisDevice.deviceName)) {
                loge("initializeP2pSettings - Failed to set device name");
            } else {
                WifiP2pServiceImpl.this.mFrameworkFacade.setStringSetting(WifiP2pServiceImpl.this.mContext, "wifi_p2p_device_name", WifiP2pServiceImpl.this.mDeviceNameInSettings);
            }
            this.mWifiNative.setP2pDeviceType(WifiP2pServiceImpl.this.mThisDevice.primaryDeviceType);
            String detail = SystemProperties.get("ro.product.model", "");
            WifiP2pNative wifiP2pNative = this.mWifiNative;
            if (!wifiP2pNative.setP2pSsidPostfix("-" + detail)) {
                loge("Failed to set ssid postfix " + detail);
            }
            if (Build.VERSION.SDK_INT < 29 && WifiP2pServiceImpl.this.mIsBootComplete) {
                setPhoneNumberIntoProbeResp();
            }
            this.mWifiNative.p2pSet("samsung_discovery", "1");
            this.mWifiNative.setConfigMethods("virtual_push_button physical_display keypad");
            if (!this.mWifiNative.p2pSet("fw_invite", "1")) {
                loge("Failed to set fw_invite");
            }
            this.mP2pStaticIpConfig = new WifiP2pStaticIpConfig();
            this.mWifiNative.p2pSet("static_ip", WifiP2pServiceImpl.DEFAULT_STATIC_IP);
            this.mP2pStaticIpConfig.mThisDeviceStaticIp = NetworkUtils.inetAddressToInt((Inet4Address) NetworkUtils.numericToInetAddress(WifiP2pServiceImpl.DEFAULT_STATIC_IP));
            String deviceAddr = this.mWifiNative.p2pGetDeviceAddress();
            if (deviceAddr != null && !deviceAddr.isEmpty()) {
                WifiP2pServiceImpl.this.mThisDevice.deviceAddress = deviceAddr;
                if (!WifiP2pServiceImpl.this.mContext.getResources().getBoolean(17891609)) {
                    String unused = WifiP2pServiceImpl.P2P_HW_MAC_ADDRESS = deviceAddr;
                }
            }
            updateThisDevice(3);
            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                logd("DeviceAddress: " + WifiP2pServiceImpl.this.mThisDevice.deviceAddress);
            }
            this.mWifiNative.p2pFlush();
            this.mWifiNative.p2pServiceFlush();
            WifiP2pServiceImpl.this.mServiceTransactionId = (byte) 0;
            WifiP2pServiceImpl.this.mServiceDiscReqId = null;
            String countryCode = Settings.Global.getString(WifiP2pServiceImpl.this.mContext.getContentResolver(), "wifi_country_code");
            if (countryCode != null && !countryCode.isEmpty()) {
                WifiP2pServiceImpl.this.mP2pStateMachine.sendMessage(WifiP2pServiceImpl.SET_COUNTRY_CODE, countryCode);
            }
            updatePersistentNetworks(WifiP2pServiceImpl.RELOAD.booleanValue());
            enableVerboseLogging(WifiP2pServiceImpl.this.mFrameworkFacade.getIntegerSetting(WifiP2pServiceImpl.this.mContext, "wifi_verbose_logging_enabled", 0));
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void updateThisDevice(int status) {
            WifiP2pServiceImpl.this.mThisDevice.status = status;
            sendThisDeviceChangedBroadcast();
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void handleGroupCreationFailure() {
            resetWifiP2pInfo();
            WifiP2pServiceImpl.this.mNetworkInfo.setDetailedState(NetworkInfo.DetailedState.FAILED, null, null);
            sendP2pConnectionChangedBroadcast();
            boolean peersChanged = this.mPeers.remove(this.mPeersLostDuringConnection);
            WifiP2pConfig wifiP2pConfig = this.mSavedPeerConfig;
            if (!(wifiP2pConfig == null || TextUtils.isEmpty(wifiP2pConfig.deviceAddress) || this.mPeers.remove(this.mSavedPeerConfig.deviceAddress) == null)) {
                peersChanged = true;
            }
            if (peersChanged) {
                sendPeersChangedBroadcast();
            }
            this.mWifiNative.p2pFlush();
            this.mPeersLostDuringConnection.clear();
            WifiP2pServiceImpl.this.mServiceDiscReqId = null;
            if (this.mSavedPeerConfig != null) {
                this.mSavedPeerConfig = null;
            }
            WifiP2pServiceImpl.this.mReinvokePersistentNetId = -1;
            this.mP2pStaticIpConfig.candidateStaticIp = 0;
            WifiP2pServiceImpl.this.mValidFreqConflict = false;
            WifiP2pServiceImpl.this.mAdvancedOppReceiver = false;
            WifiP2pServiceImpl.this.mAdvancedOppSender = false;
            WifiP2pServiceImpl.this.mAdvancedOppInProgress = false;
            WifiP2pServiceImpl.this.mAdvancedOppScanRetryCount = 0;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void handleGroupRemoved() {
            if (this.mGroup.isGroupOwner()) {
                stopDhcpServer(this.mGroup.getInterface());
            } else if (this.mP2pStaticIpConfig.isStaticIp) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    logd("initialize P2pStaticIpConfig");
                }
                this.mP2pStaticIpConfig.isStaticIp = false;
            } else {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    logd("stop IpClient");
                }
                WifiP2pServiceImpl.this.stopIpClient();
                try {
                    WifiP2pServiceImpl.this.mNwService.removeInterfaceFromLocalNetwork(this.mGroup.getInterface());
                } catch (RemoteException e) {
                    loge("Failed to remove iface from local network " + e);
                }
            }
            try {
                WifiP2pServiceImpl.this.mNwService.clearInterfaceAddresses(this.mGroup.getInterface());
            } catch (Exception e2) {
                loge("Failed to clear addresses " + e2);
            }
            this.mWifiNative.setP2pGroupIdle(this.mGroup.getInterface(), 0);
            boolean peersChanged = false;
            for (WifiP2pDevice d : this.mGroup.getClientList()) {
                if (this.mPeers.remove(d)) {
                    peersChanged = true;
                }
            }
            if (this.mPeers.remove(this.mGroup.getOwner())) {
                peersChanged = true;
            }
            if (this.mPeers.remove(this.mPeersLostDuringConnection)) {
                peersChanged = true;
            }
            if (peersChanged) {
                sendPeersChangedBroadcast();
            }
            if (!this.mGroup.isGroupOwner()) {
                this.mGroupBackup = this.mGroup;
            }
            this.mGroup = null;
            if (!WifiP2pServiceImpl.this.mAdvancedOppRemoveGroupAndJoin) {
                this.mWifiNative.p2pFlush();
            }
            WifiP2pServiceImpl.this.mConnectedDevicesCnt = 0;
            WifiP2pServiceImpl.this.mMaxClientCnt = 0;
            this.mSelectP2pConfigList = null;
            this.mSelectP2pConfigIndex = 0;
            this.mIsGotoJoinState = false;
            this.mPeersLostDuringConnection.clear();
            WifiP2pServiceImpl.this.mServiceDiscReqId = null;
            WifiP2pServiceImpl.this.mValidFreqConflict = false;
            if (!WifiP2pServiceImpl.this.mAdvancedOppReceiver && !WifiP2pServiceImpl.this.mAdvancedOppSender) {
                WifiP2pServiceImpl.this.mAdvancedOppInProgress = false;
            }
            WifiP2pServiceImpl.this.mAdvancedOppReceiver = false;
            WifiP2pServiceImpl.this.mAdvancedOppSender = false;
            removeMessages(WifiP2pServiceImpl.P2P_ADVOPP_DISCOVER_PEER);
            removeMessages(WifiP2pServiceImpl.P2P_ADVOPP_DELAYED_DISCOVER_PEER);
            WifiP2pServiceImpl.this.mAdvancedOppScanRetryCount = 0;
            if (!WifiP2pServiceImpl.this.mAdvancedOppInProgress) {
                stopLegacyWifiScan(false);
            }
            if (WifiP2pServiceImpl.this.mTemporarilyDisconnectedWifi) {
                if (WifiP2pServiceImpl.this.mWifiChannel != null) {
                    WifiP2pServiceImpl.this.mWifiChannel.sendMessage((int) WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST, 0);
                } else {
                    loge("handleGroupRemoved(): WifiChannel is null");
                }
                WifiP2pServiceImpl.this.mTemporarilyDisconnectedWifi = false;
            }
            this.mWifiNative.p2pSet("static_ip", WifiP2pServiceImpl.DEFAULT_STATIC_IP);
            WifiP2pStaticIpConfig wifiP2pStaticIpConfig = this.mP2pStaticIpConfig;
            wifiP2pStaticIpConfig.candidateStaticIp = 0;
            wifiP2pStaticIpConfig.mThisDeviceStaticIp = NetworkUtils.inetAddressToInt((Inet4Address) NetworkUtils.numericToInetAddress(WifiP2pServiceImpl.DEFAULT_STATIC_IP));
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void showP2pConnectedNotification() {
            Resources r = Resources.getSystem();
            NotificationManager notificationManager = (NotificationManager) WifiP2pServiceImpl.this.mContext.getSystemService("notification");
            if (notificationManager != null) {
                NotificationChannel notificationChannel = new NotificationChannel(WifiP2pServiceImpl.WIFI_P2P_NOTIFICATION_CHANNEL, r.getString(17042717), 2);
                Intent intent = new Intent("com.samsung.settings.WIFI_DIRECT_SETTINGS");
                intent.setFlags(604012544);
                PendingIntent pi = PendingIntent.getActivity(WifiP2pServiceImpl.this.mContext, 0, intent, 0);
                CharSequence title = r.getString(17042719);
                CharSequence message = r.getText(17042718);
                if (WifiP2pServiceImpl.this.mConnectedNotification == null) {
                    WifiP2pServiceImpl wifiP2pServiceImpl = WifiP2pServiceImpl.this;
                    wifiP2pServiceImpl.mConnectedNotification = new Notification.BigTextStyle(new Notification.Builder(wifiP2pServiceImpl.mContext, WifiP2pServiceImpl.WIFI_P2P_NOTIFICATION_CHANNEL).setContentTitle(title).setContentText(message).setSmallIcon(17301642).setWhen(0)).bigText(message).build();
                    WifiP2pServiceImpl.this.mConnectedNotification.when = 0;
                    WifiP2pServiceImpl.this.mConnectedNotification.icon = 17301642;
                    WifiP2pServiceImpl.this.mConnectedNotification.flags = 8;
                    WifiP2pServiceImpl.this.mConnectedNotification.tickerText = title;
                }
                WifiP2pServiceImpl.this.mConnectedNotification.setLatestEventInfo(WifiP2pServiceImpl.this.mContext, title, message, pi);
                notificationManager.createNotificationChannel(notificationChannel);
                notificationManager.notify(WifiP2pServiceImpl.this.mConnectedNotification.icon, WifiP2pServiceImpl.this.mConnectedNotification);
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void clearP2pConnectedNotification() {
            NotificationManager notificationManager = (NotificationManager) WifiP2pServiceImpl.this.mContext.getSystemService("notification");
            if (notificationManager != null && WifiP2pServiceImpl.this.mConnectedNotification != null) {
                notificationManager.cancel(WifiP2pServiceImpl.this.mConnectedNotification.icon);
                WifiP2pServiceImpl.this.mConnectedNotification = null;
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void showNotification() {
            showStatusBarIcon();
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void clearNotification() {
            clearStatusBarIcon();
        }

        private void showStatusBarIcon() {
            ((StatusBarManager) WifiP2pServiceImpl.this.mContext.getSystemService("statusbar")).setIcon("wifi_p2p", 17304254, 0, WifiP2pServiceImpl.this.mContext.getResources().getString(17042717));
        }

        private void clearStatusBarIcon() {
            ((StatusBarManager) WifiP2pServiceImpl.this.mContext.getSystemService("statusbar")).removeIcon("wifi_p2p");
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void soundNotification() {
            try {
                Ringtone r = RingtoneManager.getRingtone(WifiP2pServiceImpl.this.mContext, RingtoneManager.getDefaultUri(2));
                r.setStreamType(5);
                r.play();
                if (((AudioManager) WifiP2pServiceImpl.this.mContext.getSystemService("audio")).getRingerMode() == 1) {
                    ((Vibrator) WifiP2pServiceImpl.this.mContext.getSystemService("vibrator")).vibrate(1000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void replyToMessage(Message msg, int what) {
            if (msg.replyTo != null) {
                Message dstMsg = obtainMessage(msg);
                dstMsg.what = what;
                WifiP2pServiceImpl.this.mReplyChannel.replyToMessage(msg, dstMsg);
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void replyToMessage(Message msg, int what, int arg1) {
            if (msg.replyTo != null) {
                Message dstMsg = obtainMessage(msg);
                dstMsg.what = what;
                dstMsg.arg1 = arg1;
                WifiP2pServiceImpl.this.mReplyChannel.replyToMessage(msg, dstMsg);
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void replyToMessage(Message msg, int what, Object obj) {
            if (msg.replyTo != null) {
                Message dstMsg = obtainMessage(msg);
                dstMsg.what = what;
                dstMsg.obj = obj;
                WifiP2pServiceImpl.this.mReplyChannel.replyToMessage(msg, dstMsg);
            }
        }

        private Message obtainMessage(Message srcMsg) {
            Message msg = Message.obtain();
            msg.arg2 = srcMsg.arg2;
            return msg;
        }

        /* access modifiers changed from: protected */
        public void logd(String s) {
            Slog.d(WifiP2pServiceImpl.TAG, s.replaceAll("([0-9a-fA-F]{2}:)([0-9a-fA-F]{2}:){3}([0-9a-fA-F]{2}:[0-9a-fA-F]{2})", "$1$3"));
        }

        /* access modifiers changed from: protected */
        public void loge(String s) {
            Slog.e(WifiP2pServiceImpl.TAG, s.replaceAll("([0-9a-fA-F]{2}:)([0-9a-fA-F]{2}:){3}([0-9a-fA-F]{2}:[0-9a-fA-F]{2})", "$1$3"));
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean updateSupplicantServiceRequest() {
            clearSupplicantServiceRequest();
            StringBuffer sb = new StringBuffer();
            for (ClientInfo c : WifiP2pServiceImpl.this.mClientInfoList.values()) {
                for (int i = 0; i < c.mReqList.size(); i++) {
                    WifiP2pServiceRequest req = (WifiP2pServiceRequest) c.mReqList.valueAt(i);
                    if (req != null) {
                        sb.append(req.getSupplicantQuery());
                    }
                }
            }
            if (sb.length() == 0) {
                return false;
            }
            WifiP2pServiceImpl.this.mServiceDiscReqId = this.mWifiNative.p2pServDiscReq(WifiP2pServiceImpl.EMPTY_DEVICE_ADDRESS, sb.toString());
            if (WifiP2pServiceImpl.this.mServiceDiscReqId == null) {
                return false;
            }
            return true;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void clearSupplicantServiceRequest() {
            if (WifiP2pServiceImpl.this.mServiceDiscReqId != null) {
                this.mWifiNative.p2pServDiscCancelReq(WifiP2pServiceImpl.this.mServiceDiscReqId);
                WifiP2pServiceImpl.this.mServiceDiscReqId = null;
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean addServiceRequest(Messenger m, WifiP2pServiceRequest req) {
            if (m == null || req == null) {
                Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                return false;
            }
            clearClientDeadChannels();
            ClientInfo clientInfo = getClientInfo(m, false);
            if (clientInfo == null) {
                return false;
            }
            WifiP2pServiceImpl.access$25404(WifiP2pServiceImpl.this);
            if (WifiP2pServiceImpl.this.mServiceTransactionId == 0) {
                WifiP2pServiceImpl.access$25404(WifiP2pServiceImpl.this);
            }
            req.setTransactionId(WifiP2pServiceImpl.this.mServiceTransactionId);
            clientInfo.mReqList.put(WifiP2pServiceImpl.this.mServiceTransactionId, req);
            if (WifiP2pServiceImpl.this.mServiceDiscReqId == null) {
                return true;
            }
            return updateSupplicantServiceRequest();
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void removeServiceRequest(Messenger m, WifiP2pServiceRequest req) {
            if (m == null || req == null) {
                Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
            }
            ClientInfo clientInfo = getClientInfo(m, false);
            if (clientInfo != null) {
                boolean removed = false;
                int i = 0;
                while (true) {
                    if (i >= clientInfo.mReqList.size()) {
                        break;
                    } else if (req.equals(clientInfo.mReqList.valueAt(i))) {
                        removed = true;
                        clientInfo.mReqList.removeAt(i);
                        break;
                    } else {
                        i++;
                    }
                }
                if (removed && WifiP2pServiceImpl.this.mServiceDiscReqId != null) {
                    updateSupplicantServiceRequest();
                }
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void clearServiceRequests(Messenger m) {
            if (m == null) {
                Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                return;
            }
            ClientInfo clientInfo = getClientInfo(m, false);
            if (clientInfo != null && clientInfo.mReqList.size() != 0) {
                clientInfo.mReqList.clear();
                if (WifiP2pServiceImpl.this.mServiceDiscReqId != null) {
                    updateSupplicantServiceRequest();
                }
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void addP2pPktLogFilter() {
            WifiNative wifiNative = this.mWifiLegacyNative;
            String str = this.mWifiInterface;
            if (wifiNative.setPktlogFilter(str, this.filterOffset + " " + this.filterMaskSSDP + " " + this.filterSSDP)) {
                Log.i(WifiP2pServiceImpl.TAG, "addP2pPktLogFilter = [" + this.mWifiInterface + "]");
                WifiNative wifiNative2 = this.mWifiLegacyNative;
                String str2 = this.mWifiInterface;
                wifiNative2.setPktlogFilter(str2, this.filterOffset + " " + this.filterMaskRTSPDst + " " + this.filterRTSPDst);
                WifiNative wifiNative3 = this.mWifiLegacyNative;
                String str3 = this.mWifiInterface;
                wifiNative3.setPktlogFilter(str3, this.filterOffset + " " + this.filterMaskRTSPSrc + " " + this.filterRTSPSrc);
                WifiNative wifiNative4 = this.mWifiLegacyNative;
                String str4 = this.mWifiInterface;
                wifiNative4.setPktlogFilter(str4, this.filterOffset + " " + this.filterMaskRTCP + " " + this.filterRTCP);
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void removeP2pPktLogFilter() {
            WifiNative wifiNative = this.mWifiLegacyNative;
            String str = this.mWifiInterface;
            if (wifiNative.removePktlogFilter(str, this.filterOffset + " " + this.filterMaskSSDP + " " + this.filterSSDP)) {
                Log.i(WifiP2pServiceImpl.TAG, "removeP2pPktLogFilter = [" + this.mWifiInterface + "]");
                WifiNative wifiNative2 = this.mWifiLegacyNative;
                String str2 = this.mWifiInterface;
                wifiNative2.removePktlogFilter(str2, this.filterOffset + " " + this.filterMaskRTSPDst + " " + this.filterRTSPDst);
                WifiNative wifiNative3 = this.mWifiLegacyNative;
                String str3 = this.mWifiInterface;
                wifiNative3.removePktlogFilter(str3, this.filterOffset + " " + this.filterMaskRTSPSrc + " " + this.filterRTSPSrc);
                WifiNative wifiNative4 = this.mWifiLegacyNative;
                String str4 = this.mWifiInterface;
                wifiNative4.removePktlogFilter(str4, this.filterOffset + " " + this.filterMaskRTCP + " " + this.filterRTCP);
            }
        }

        private void saveFwDumpByP2P() {
            Log.i(WifiP2pServiceImpl.TAG, "saveFwDumpByP2P mWifiInterface = [" + this.mWifiInterface + "]");
            this.mWifiLegacyNative.saveFwDump(this.mWifiInterface);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean addLocalService(Messenger m, WifiP2pServiceInfo servInfo) {
            if (m == null || servInfo == null) {
                Log.e(WifiP2pServiceImpl.TAG, "Illegal arguments");
                return false;
            }
            clearClientDeadChannels();
            ClientInfo clientInfo = getClientInfo(m, false);
            if (clientInfo == null || !clientInfo.mServList.add(servInfo)) {
                return false;
            }
            if (this.mWifiNative.p2pServiceAdd(servInfo)) {
                return true;
            }
            clientInfo.mServList.remove(servInfo);
            return false;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void removeLocalService(Messenger m, WifiP2pServiceInfo servInfo) {
            if (m == null || servInfo == null) {
                Log.e(WifiP2pServiceImpl.TAG, "Illegal arguments");
                return;
            }
            ClientInfo clientInfo = getClientInfo(m, false);
            if (clientInfo != null) {
                this.mWifiNative.p2pServiceDel(servInfo);
                clientInfo.mServList.remove(servInfo);
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void clearLocalServices(Messenger m) {
            if (m == null) {
                Log.e(WifiP2pServiceImpl.TAG, "Illegal argument(s)");
                return;
            }
            ClientInfo clientInfo = getClientInfo(m, false);
            if (clientInfo != null) {
                for (WifiP2pServiceInfo servInfo : clientInfo.mServList) {
                    this.mWifiNative.p2pServiceDel(servInfo);
                }
                clientInfo.mServList.clear();
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void clearClientInfo(Messenger m) {
            clearLocalServices(m);
            clearServiceRequests(m);
            ClientInfo clientInfo = (ClientInfo) WifiP2pServiceImpl.this.mClientInfoList.remove(m);
            if (clientInfo != null) {
                logd("Client:" + clientInfo.mPackageName + " is removed");
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void sendServiceResponse(WifiP2pServiceResponse resp) {
            if (resp == null) {
                Log.e(WifiP2pServiceImpl.TAG, "sendServiceResponse with null response");
                return;
            }
            for (ClientInfo c : WifiP2pServiceImpl.this.mClientInfoList.values()) {
                if (((WifiP2pServiceRequest) c.mReqList.get(resp.getTransactionId())) != null) {
                    Message msg = Message.obtain();
                    msg.what = 139314;
                    msg.arg1 = 0;
                    msg.arg2 = 0;
                    msg.obj = resp;
                    if (c.mMessenger != null) {
                        try {
                            c.mMessenger.send(msg);
                        } catch (RemoteException e) {
                            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                                logd("detect dead channel");
                            }
                            clearClientInfo(c.mMessenger);
                            return;
                        }
                    }
                }
            }
        }

        private void clearClientDeadChannels() {
            ArrayList<Messenger> deadClients = new ArrayList<>();
            for (ClientInfo c : WifiP2pServiceImpl.this.mClientInfoList.values()) {
                Message msg = Message.obtain();
                msg.what = 139313;
                msg.arg1 = 0;
                msg.arg2 = 0;
                msg.obj = null;
                if (c.mMessenger != null) {
                    try {
                        c.mMessenger.send(msg);
                    } catch (RemoteException e) {
                        if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                            logd("detect dead channel");
                        }
                        deadClients.add(c.mMessenger);
                    }
                }
            }
            Iterator<Messenger> it = deadClients.iterator();
            while (it.hasNext()) {
                clearClientInfo(it.next());
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private ClientInfo getClientInfo(Messenger m, boolean createIfNotExist) {
            ClientInfo clientInfo = (ClientInfo) WifiP2pServiceImpl.this.mClientInfoList.get(m);
            if (clientInfo != null || !createIfNotExist) {
                return clientInfo;
            }
            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                logd("add a new client");
            }
            ClientInfo clientInfo2 = new ClientInfo(m);
            WifiP2pServiceImpl.this.mClientInfoList.put(m, clientInfo2);
            return clientInfo2;
        }

        private void sendDetachedMsg(int reason) {
            if (WifiP2pServiceImpl.this.mForegroundAppMessenger != null) {
                Message msg = Message.obtain();
                msg.what = 139381;
                msg.arg1 = reason;
                try {
                    WifiP2pServiceImpl.this.mForegroundAppMessenger.send(msg);
                } catch (RemoteException e) {
                }
                WifiP2pServiceImpl.this.mForegroundAppMessenger = null;
                WifiP2pServiceImpl.this.mForegroundAppPkgName = null;
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean sendShowPinReqToFrontApp(String pin) {
            if (!isForegroundApp(WifiP2pServiceImpl.this.mForegroundAppPkgName)) {
                sendDetachedMsg(4);
                return false;
            }
            Message msg = Message.obtain();
            msg.what = 139384;
            Bundle bundle = new Bundle();
            bundle.putString("wpsPin", pin);
            msg.setData(bundle);
            return sendDialogMsgToFrontApp(msg);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean sendConnectNoticeToApp(WifiP2pDevice dev, WifiP2pConfig config) {
            if (dev == null) {
                dev = new WifiP2pDevice(config.deviceAddress);
            }
            if (!isForegroundApp(WifiP2pServiceImpl.this.mForegroundAppPkgName)) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    logd("application is NOT foreground");
                }
                sendDetachedMsg(4);
                return false;
            }
            Message msg = Message.obtain();
            msg.what = 139383;
            Bundle bundle = new Bundle();
            bundle.putParcelable("wifiP2pDevice", dev);
            bundle.putParcelable("wifiP2pConfig", config);
            msg.setData(bundle);
            return sendDialogMsgToFrontApp(msg);
        }

        private boolean sendDialogMsgToFrontApp(Message msg) {
            try {
                WifiP2pServiceImpl.this.mForegroundAppMessenger.send(msg);
                return true;
            } catch (RemoteException e) {
                WifiP2pServiceImpl.this.mForegroundAppMessenger = null;
                WifiP2pServiceImpl.this.mForegroundAppPkgName = null;
                return false;
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean setDialogListenerApp(Messenger m, String appPkgName, boolean isReset) {
            if (WifiP2pServiceImpl.this.mForegroundAppPkgName != null && !WifiP2pServiceImpl.this.mForegroundAppPkgName.equals(appPkgName)) {
                if (isForegroundApp(WifiP2pServiceImpl.this.mForegroundAppPkgName)) {
                    if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                        logd("application is NOT foreground");
                    }
                    return false;
                }
                sendDetachedMsg(4);
            }
            if (isReset) {
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    logd("reset dialog listener");
                }
                WifiP2pServiceImpl.this.mForegroundAppMessenger = null;
                WifiP2pServiceImpl.this.mForegroundAppPkgName = null;
                return true;
            } else if (!isForegroundApp(appPkgName)) {
                return false;
            } else {
                WifiP2pServiceImpl.this.mForegroundAppMessenger = m;
                WifiP2pServiceImpl.this.mForegroundAppPkgName = appPkgName;
                if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                    logd("set dialog listener. app=" + appPkgName);
                }
                return true;
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean isForegroundApp(String pkgName) {
            if (pkgName == null) {
                return false;
            }
            List<ActivityManager.RunningTaskInfo> tasks = WifiP2pServiceImpl.this.mActivityMgr.getRunningTasks(1);
            if (tasks.size() == 0) {
                return false;
            }
            return pkgName.equals(tasks.get(0).baseActivity.getPackageName());
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private WifiP2pDeviceList getPeers(String pkgName, int uid) {
            if (WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkCanAccessWifiDirect(pkgName, uid, true)) {
                return new WifiP2pDeviceList(this.mPeers);
            }
            return new WifiP2pDeviceList();
        }

        private void setPendingFactoryReset(boolean pending) {
            WifiP2pServiceImpl.this.mFrameworkFacade.setIntegerSetting(WifiP2pServiceImpl.this.mContext, "wifi_p2p_pending_factory_reset", pending ? 1 : 0);
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean isPendingFactoryReset() {
            if (WifiP2pServiceImpl.this.mFrameworkFacade.getIntegerSetting(WifiP2pServiceImpl.this.mContext, "wifi_p2p_pending_factory_reset", 0) != 0) {
                return true;
            }
            return false;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean factoryReset(int uid) {
            String pkgName = WifiP2pServiceImpl.this.mContext.getPackageManager().getNameForUid(uid);
            UserManager userManager = WifiP2pServiceImpl.this.mWifiInjector.getUserManager();
            if (!WifiP2pServiceImpl.this.mWifiPermissionsUtil.checkNetworkSettingsPermission(uid) || userManager.hasUserRestriction("no_network_reset") || userManager.hasUserRestriction("no_config_wifi")) {
                return false;
            }
            Log.i(WifiP2pServiceImpl.TAG, "factoryReset uid=" + uid + " pkg=" + pkgName);
            if (WifiP2pServiceImpl.this.mNetworkInfo.isAvailable()) {
                if (this.mWifiNative.p2pListNetworks(this.mGroups)) {
                    for (WifiP2pGroup group : this.mGroups.getGroupList()) {
                        this.mWifiNative.removeP2pNetwork(group.getNetworkId());
                    }
                }
                updatePersistentNetworks(true);
                setPendingFactoryReset(false);
            } else {
                setPendingFactoryReset(true);
            }
            return true;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private String getCallingPkgName(int uid, Messenger replyMessenger) {
            ClientInfo clientInfo = (ClientInfo) WifiP2pServiceImpl.this.mClientInfoList.get(replyMessenger);
            if (clientInfo != null) {
                return clientInfo.mPackageName;
            }
            if (uid == 1000) {
                return WifiP2pServiceImpl.this.mContext.getOpPackageName();
            }
            return null;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void clearServicesForAllClients() {
            for (ClientInfo c : WifiP2pServiceImpl.this.mClientInfoList.values()) {
                clearLocalServices(c.mMessenger);
                clearServiceRequests(c.mMessenger);
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean setP2pGroupForSamsung() {
            NetworkInfo netInfo;
            boolean ret = false;
            WifiManager tWifiManager = (WifiManager) WifiP2pServiceImpl.this.mContext.getSystemService("wifi");
            int frequency = 0;
            if (!(tWifiManager == null || (netInfo = tWifiManager.getNetworkInfo()) == null || netInfo.getDetailedState() != NetworkInfo.DetailedState.CONNECTED || tWifiManager.getConnectionInfo() == null)) {
                frequency = tWifiManager.getConnectionInfo().getFrequency();
            }
            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                logd("legacy AP frequency : " + frequency);
            }
            if (WifiP2pServiceImpl.this.mAdvancedOppSender || WifiP2pServiceImpl.this.mAdvancedOppReceiver) {
                if (frequency >= 5170) {
                    ret = this.mWifiNative.p2pGroupAdd(false, frequency);
                } else {
                    ret = this.mWifiNative.p2pGroupAdd(false, 5);
                }
            } else if (frequency <= 2472) {
                ret = this.mWifiNative.p2pGroupAdd(false, frequency);
            }
            if (!ret) {
                ret = this.mWifiNative.p2pGroupAdd(false, 5);
            }
            if (!ret) {
                return this.mWifiNative.p2pGroupAdd(false, 2);
            }
            return ret;
        }

        private String getWifiHwMac() {
            String macAddress;
            if (WifiP2pServiceImpl.EMPTY_DEVICE_ADDRESS.equals(WifiP2pServiceImpl.WIFI_HW_MAC_ADDRESS) && (macAddress = this.mWifiLegacyNative.getVendorConnFileInfo(0)) != null && macAddress.length() >= 17) {
                String unused = WifiP2pServiceImpl.WIFI_HW_MAC_ADDRESS = macAddress.substring(0, 17);
            }
            if (WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                Log.i(WifiP2pServiceImpl.TAG, "getWifiHwMac: " + WifiP2pServiceImpl.WIFI_HW_MAC_ADDRESS);
            }
            return WifiP2pServiceImpl.WIFI_HW_MAC_ADDRESS;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean makeP2pHwMac() {
            if (WifiP2pServiceImpl.EMPTY_DEVICE_ADDRESS.equals(WifiP2pServiceImpl.P2P_HW_MAC_ADDRESS)) {
                byte[] bArr = new byte[6];
                byte[] addr = WifiP2pServiceImpl.macAddressToByteArray(getWifiHwMac());
                if (WifiP2pServiceImpl.EMPTY_DEVICE_ADDRESS.equals(WifiP2pServiceImpl.toHexString(addr))) {
                    Log.i(WifiP2pServiceImpl.TAG, "makeP2pHwMac: wifihwmac is empty");
                    return false;
                }
                addr[0] = (byte) (addr[0] | 2);
                String unused = WifiP2pServiceImpl.P2P_HW_MAC_ADDRESS = WifiP2pServiceImpl.toHexString(addr);
            }
            if (!WifiP2pServiceImpl.mVerboseLoggingEnabled) {
                return true;
            }
            Log.i(WifiP2pServiceImpl.TAG, "makeP2pHwMac: " + WifiP2pServiceImpl.P2P_HW_MAC_ADDRESS);
            return true;
        }
    }

    /* access modifiers changed from: private */
    public class ClientInfo {
        private Messenger mMessenger;
        private String mPackageName;
        private SparseArray<WifiP2pServiceRequest> mReqList;
        private List<WifiP2pServiceInfo> mServList;

        private ClientInfo(Messenger m) {
            this.mMessenger = m;
            this.mPackageName = null;
            this.mReqList = new SparseArray<>();
            this.mServList = new ArrayList();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void auditLog(boolean outcome, String msg) {
        auditLog(outcome, msg, 5);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void auditLog(boolean outcome, String msg, int group) {
        try {
            Uri uri = Uri.parse("content://com.sec.knox.provider/AuditLog");
            ContentValues cv = new ContentValues();
            cv.put("severity", (Integer) 5);
            cv.put("group", Integer.valueOf(group));
            cv.put("outcome", Boolean.valueOf(outcome));
            cv.put("uid", Integer.valueOf(Process.myPid()));
            cv.put("component", "WifiP2pServiceImpl");
            cv.put("message", msg);
            this.mContext.getContentResolver().insert(uri, cv);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, e.toString());
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean isAllowWifiDirectByEDM() {
        Cursor cr;
        if (this.mContext == null || (cr = this.mContext.getContentResolver().query(Uri.parse("content://com.sec.knox.provider/RestrictionPolicy4"), null, "isWifiDirectAllowed", new String[]{"true"}, null)) == null) {
            return true;
        }
        try {
            cr.moveToFirst();
            if (cr.getString(cr.getColumnIndex("isWifiDirectAllowed")).equals("false")) {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "isAllowWifiDirectByEDM() : wifi direct is not allowed.");
                }
                cr.close();
                return false;
            }
        } catch (Exception e) {
        } catch (Throwable th) {
            cr.close();
            throw th;
        }
        cr.close();
        return true;
    }

    private void sendPingForArpUpdate(InetAddress address) {
        if (address != null) {
            try {
                if (address.isReachable(2000)) {
                    Log.i(TAG, "sendPingForArpUpdate (SUCCESS)");
                } else {
                    Log.i(TAG, "sendPingForArpUpdate (FAILED)");
                }
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    /* access modifiers changed from: private */
    public static boolean isTablet() {
        String deviceType = SystemProperties.get("ro.build.characteristics");
        return deviceType != null && deviceType.contains("tablet");
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void connectRetryCount(String peerAddr, int count) {
        Integer mode = this.mModeChange.get(peerAddr);
        if (mode != null) {
            int modeValue = mode.intValue();
            if (count > 0 && (modeValue & 3) < 3) {
                modeValue++;
            } else if (count != 0 || modeValue <= 0) {
                if (count < 0 && modeValue > 0) {
                    modeValue--;
                }
            } else if ((modeValue & 3) == 1) {
                this.mModeChange.remove(peerAddr);
                return;
            } else {
                modeValue &= ~(modeValue & 3);
            }
            Log.d(TAG, "Connection Mode : " + modeValue);
            this.mModeChange.put(peerAddr, Integer.valueOf(modeValue));
        }
    }

    /* access modifiers changed from: private */
    public class WifiP2pConnectReqInfo {
        private int connectionReceived;
        private int isJoin;
        private int isPersistent;
        private WifiP2pDevice peerDev;
        private int peerDevType = -1;
        private int peerGOIntentValue = -1;
        private String peerManufacturer;
        private String pkgName;

        public WifiP2pConnectReqInfo() {
        }

        public WifiP2pConnectReqInfo(WifiP2pConnectReqInfo source) {
            if (source != null) {
                this.peerDev = new WifiP2pDevice(source.peerDev);
                this.pkgName = source.pkgName;
                this.connectionReceived = source.connectionReceived;
                this.isPersistent = source.isPersistent;
                this.isJoin = source.isJoin;
                this.peerManufacturer = source.peerManufacturer;
                this.peerDevType = source.peerDevType;
                this.peerGOIntentValue = source.peerGOIntentValue;
            }
        }

        public void set(WifiP2pDevice dev, String name, int conn, int p, int j, String ma, String typeStr) {
            this.peerDev = dev;
            if (name != null) {
                this.pkgName = name.replaceAll("\\s+", "");
            }
            this.connectionReceived = conn;
            this.isPersistent = p;
            this.isJoin = j;
            if (ma != null) {
                this.peerManufacturer = ma.replaceAll("\\s+", "");
            }
            if (name == null || TextUtils.isEmpty(this.pkgName)) {
                this.pkgName = "unknown";
            }
            if (ma == null || TextUtils.isEmpty(this.peerManufacturer)) {
                this.peerManufacturer = "unknown";
            }
            if (typeStr != null) {
                String[] tokens = typeStr.split("-");
                if (tokens.length == 3) {
                    try {
                        this.peerDevType = Integer.parseInt(tokens[0]);
                    } catch (NumberFormatException e) {
                        Log.e(WifiP2pServiceImpl.TAG, "NumberFormatException while getting peerDevType");
                    }
                }
            }
        }

        public void reset() {
            this.peerDev = null;
            this.pkgName = null;
            this.connectionReceived = 0;
            this.isPersistent = 0;
            this.isJoin = 0;
            this.peerManufacturer = null;
            this.peerDevType = -1;
            this.peerGOIntentValue = -1;
        }

        public String getPeerManufacturer() {
            return this.peerManufacturer;
        }

        public int getPeerDevType() {
            return this.peerDevType;
        }

        public void setPeerGOIntentValue(int val) {
            this.peerGOIntentValue = val;
        }

        public int getPeerGOIntentValue() {
            return this.peerGOIntentValue;
        }

        public String toString() {
            return this.pkgName + " " + this.connectionReceived + " " + this.isPersistent + " " + this.isJoin + " " + this.peerManufacturer + " " + this.peerDevType + " " + this.peerGOIntentValue + " ";
        }
    }

    /* access modifiers changed from: private */
    public class WifiP2pConnectedPeriodInfo {
        private WifiP2pDevice peerDev = null;
        private String pkgName = null;
        private long startTime = 0;

        public WifiP2pConnectedPeriodInfo() {
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void checkAndShowP2pEnableDialog(int req_type) {
        if (req_type == 0) {
            Log.d(TAG, "P2P is not allowed because MHS is enabled");
            showWifiEnableWarning(2, 0);
        } else if (req_type == 1) {
            Log.d(TAG, "P2P is not allowed because NAN is enabled");
            showWifiEnableWarning(2, 1);
        }
    }

    private void showWifiEnableWarning(int extra_type, int req_type) {
        Intent intent = new Intent();
        intent.setClassName(WIFI_DIRECT_SETTINGS_PKGNAME, "com.samsung.android.settings.wifi.WifiWarning");
        intent.putExtra("req_type", req_type);
        intent.putExtra("extra_type", extra_type);
        intent.setFlags(1015021568);
        try {
            this.mContext.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Exception is occured in showWifiEnableWarning.");
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void dialogRejectForThemeChanging() {
        auditLog(true, "Wi-Fi Direct (P2P) invitation denied");
        this.mP2pStateMachine.sendMessage(PEER_CONNECTION_USER_REJECT);
        this.mLapseTime = 30;
        this.mWpsTimer.cancel();
        this.mDialogWakeLock.release();
        this.imm = (InputMethodManager) this.mContext.getSystemService("input_method");
        InputMethodManager inputMethodManager = this.imm;
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(this.pinConn.getWindowToken(), 0);
        }
        this.mInvitationDialog.dismiss();
        this.mInvitationDialog = null;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private byte[] createRandomMac() {
        byte[] addr = new byte[6];
        new Random().nextBytes(addr);
        addr[0] = (byte) (addr[0] & 254);
        addr[0] = (byte) (addr[0] | 2);
        return addr;
    }

    public static String toHexString(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(data.length * 3);
        boolean first = true;
        for (byte b : data) {
            if (first) {
                first = false;
            } else {
                sb.append(':');
            }
            sb.append(String.format("%02x", Integer.valueOf(b & 255)));
        }
        return sb.toString();
    }

    public static byte[] macAddressToByteArray(String macStr) {
        if (TextUtils.isEmpty(macStr)) {
            return null;
        }
        String cleanMac = macStr.replace(":", "");
        if (cleanMac.length() == 12) {
            return HexEncoding.decode(cleanMac.toCharArray(), false);
        }
        throw new IllegalArgumentException("invalid mac string length: " + cleanMac);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void allowForcingEnableP2pForApp(String callPkgName) {
        if (callPkgName == null || callPkgName.isEmpty() || (!callPkgName.equals("WifiWarning") && !callPkgName.equals("SmartView") && !callPkgName.equals("BluetoothCast"))) {
            Log.i(TAG, "allowForcingEnableP2pForApp false");
            this.mReceivedEnableP2p = false;
            return;
        }
        Log.i(TAG, "allowForcingEnableP2pForApp true: called: " + callPkgName);
        this.mReceivedEnableP2p = true;
    }

    public boolean isSetupInterfaceRunning() {
        return this.mSetupInterfaceIsRunnging;
    }
}
