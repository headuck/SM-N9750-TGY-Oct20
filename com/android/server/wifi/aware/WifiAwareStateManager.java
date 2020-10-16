package com.android.server.wifi.aware;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.wifi.V1_2.NanDataPathChannelInfo;
import android.location.LocationManager;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.IWifiAwareDiscoverySessionCallback;
import android.net.wifi.aware.IWifiAwareEventCallback;
import android.net.wifi.aware.IWifiAwareMacAddressProvider;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SemHqmManager;
import android.os.ShellCommand;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.MessageUtils;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.internal.util.WakeupMessage;
import com.android.server.wifi.Clock;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.aware.WifiAwareDiscoverySessionState;
import com.android.server.wifi.aware.WifiAwareShellCommand;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WifiPermissionsWrapper;
import com.samsung.android.feature.SemFloatingFeature;
import com.samsung.android.server.wifi.bigdata.BaseBigDataItem;
import com.samsung.android.server.wifi.bigdata.WifiChipInfo;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import libcore.util.HexEncoding;
import org.json.JSONException;
import org.json.JSONObject;
import org.ksoap2.SoapEnvelope;

public class WifiAwareStateManager implements WifiAwareShellCommand.DelegatedShellCommand {
    private static final byte[] ALL_ZERO_MAC = {0, 0, 0, 0, 0, 0};
    private static final int COMMAND_TYPE_CONNECT = 100;
    private static final int COMMAND_TYPE_CREATE_ALL_DATA_PATH_INTERFACES = 112;
    private static final int COMMAND_TYPE_CREATE_DATA_PATH_INTERFACE = 114;
    private static final int COMMAND_TYPE_DELAYED_INITIALIZATION = 121;
    private static final int COMMAND_TYPE_DELETE_ALL_DATA_PATH_INTERFACES = 113;
    private static final int COMMAND_TYPE_DELETE_DATA_PATH_INTERFACE = 115;
    private static final int COMMAND_TYPE_DISABLE_USAGE = 109;
    private static final int COMMAND_TYPE_DISCONNECT = 101;
    private static final int COMMAND_TYPE_ENABLE_USAGE = 108;
    private static final int COMMAND_TYPE_END_DATA_PATH = 118;
    private static final int COMMAND_TYPE_ENQUEUE_SEND_MESSAGE = 107;
    private static final int COMMAND_TYPE_GET_AWARE = 122;
    private static final int COMMAND_TYPE_GET_CAPABILITIES = 111;
    private static final int COMMAND_TYPE_INITIATE_DATA_PATH_SETUP = 116;
    private static final int COMMAND_TYPE_PUBLISH = 103;
    private static final int COMMAND_TYPE_RECONFIGURE = 120;
    private static final int COMMAND_TYPE_RELEASE_AWARE = 123;
    private static final int COMMAND_TYPE_RESPOND_TO_DATA_PATH_SETUP_REQUEST = 117;
    private static final int COMMAND_TYPE_SEC_BIGDATA_LOGGING = 160;
    private static final int COMMAND_TYPE_SET_CLUSTER_MERGE_ENABLE = 151;
    private static final int COMMAND_TYPE_SUBSCRIBE = 105;
    private static final int COMMAND_TYPE_TERMINATE_SESSION = 102;
    private static final int COMMAND_TYPE_TRANSMIT_NEXT_MESSAGE = 119;
    private static final int COMMAND_TYPE_UPDATE_PUBLISH = 104;
    private static final int COMMAND_TYPE_UPDATE_SUBSCRIBE = 106;
    private static final int DEFAULT_POLL_TRAFFIC_INTERVAL_MSECS = 3000;
    public static final String ENABLE_SURVEY_MODE = SemFloatingFeature.getInstance().getString("SEC_FLOATING_FEATURE_CONTEXTSERVICE_ENABLE_SURVEY_MODE");
    public static final boolean ENABLE_UNIFIED_HQM_SERVER = true;
    @VisibleForTesting
    public static final String HAL_COMMAND_TIMEOUT_TAG = "WifiAwareStateManager HAL Command Timeout";
    @VisibleForTesting
    public static final String HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG = "WifiAwareStateManager HAL Data Path Confirm Timeout";
    @VisibleForTesting
    public static final String HAL_SEND_MESSAGE_TIMEOUT_TAG = "WifiAwareStateManager HAL Send Message Timeout";
    private static final String MESSAGE_BUNDLE_KEY_APP_INFO = "app_info";
    private static final String MESSAGE_BUNDLE_KEY_CALLING_PACKAGE = "calling_package";
    private static final String MESSAGE_BUNDLE_KEY_CHANNEL = "channel";
    private static final String MESSAGE_BUNDLE_KEY_CHANNEL_REQ_TYPE = "channel_request_type";
    private static final String MESSAGE_BUNDLE_KEY_CLIENT_ID = "client_id";
    private static final String MESSAGE_BUNDLE_KEY_CONFIG = "config";
    private static final String MESSAGE_BUNDLE_KEY_FILTER_DATA = "filter_data";
    private static final String MESSAGE_BUNDLE_KEY_INTERFACE_NAME = "interface_name";
    private static final String MESSAGE_BUNDLE_KEY_MAC_ADDRESS = "mac_address";
    private static final String MESSAGE_BUNDLE_KEY_MESSAGE = "message";
    private static final String MESSAGE_BUNDLE_KEY_MESSAGE_ARRIVAL_SEQ = "message_arrival_seq";
    private static final String MESSAGE_BUNDLE_KEY_MESSAGE_DATA = "message_data";
    private static final String MESSAGE_BUNDLE_KEY_MESSAGE_ID = "message_id";
    private static final String MESSAGE_BUNDLE_KEY_MESSAGE_PEER_ID = "message_peer_id";
    private static final String MESSAGE_BUNDLE_KEY_NAN_OP_DURATION = "nan_op_duration";
    private static final String MESSAGE_BUNDLE_KEY_NDP_IDS = "ndp_ids";
    private static final String MESSAGE_BUNDLE_KEY_NOTIFY_IDENTITY_CHANGE = "notify_identity_chg";
    private static final String MESSAGE_BUNDLE_KEY_OOB = "out_of_band";
    private static final String MESSAGE_BUNDLE_KEY_OPERATION_TYPE = "op_type";
    private static final String MESSAGE_BUNDLE_KEY_PASSPHRASE = "passphrase";
    private static final String MESSAGE_BUNDLE_KEY_PEER_ID = "peer_id";
    private static final String MESSAGE_BUNDLE_KEY_PID = "pid";
    private static final String MESSAGE_BUNDLE_KEY_PMK = "pmk";
    private static final String MESSAGE_BUNDLE_KEY_RANGE_MM = "range_mm";
    private static final String MESSAGE_BUNDLE_KEY_REQ_INSTANCE_ID = "req_instance_id";
    private static final String MESSAGE_BUNDLE_KEY_RETRY_COUNT = "retry_count";
    private static final String MESSAGE_BUNDLE_KEY_SEND_MESSAGE_ENQUEUE_TIME = "message_queue_time";
    private static final String MESSAGE_BUNDLE_KEY_SENT_MESSAGE = "send_message";
    private static final String MESSAGE_BUNDLE_KEY_SERVICE_NAME = "svc_name";
    private static final String MESSAGE_BUNDLE_KEY_SESSION_ID = "session_id";
    private static final String MESSAGE_BUNDLE_KEY_SESSION_TYPE = "session_type";
    private static final String MESSAGE_BUNDLE_KEY_SSI_DATA = "ssi_data";
    private static final String MESSAGE_BUNDLE_KEY_STATUS_CODE = "status_code";
    private static final String MESSAGE_BUNDLE_KEY_SUCCESS_FLAG = "success_flag";
    private static final String MESSAGE_BUNDLE_KEY_UID = "uid";
    private static final String MESSAGE_RANGE_MM = "range_mm";
    private static final String MESSAGE_RANGING_INDICATION = "ranging_indication";
    private static final int MESSAGE_TYPE_COMMAND = 1;
    private static final int MESSAGE_TYPE_DATA_PATH_TIMEOUT = 6;
    private static final int MESSAGE_TYPE_NOTIFICATION = 3;
    private static final int MESSAGE_TYPE_RESPONSE = 2;
    private static final int MESSAGE_TYPE_RESPONSE_TIMEOUT = 4;
    private static final int MESSAGE_TYPE_SEND_MESSAGE_TIMEOUT = 5;
    private static final int NOTIFICATION_TYPE_AWARE_DOWN = 306;
    private static final int NOTIFICATION_TYPE_AWARE_TRAFFIC_POLL = 313;
    private static final int NOTIFICATION_TYPE_CLUSTER_CHANGE = 302;
    private static final int NOTIFICATION_TYPE_INTERFACE_CHANGE = 301;
    private static final int NOTIFICATION_TYPE_MATCH = 303;
    private static final int NOTIFICATION_TYPE_MESSAGE_RECEIVED = 305;
    private static final int NOTIFICATION_TYPE_ON_DATA_PATH_CONFIRM = 310;
    private static final int NOTIFICATION_TYPE_ON_DATA_PATH_END = 311;
    private static final int NOTIFICATION_TYPE_ON_DATA_PATH_REQUEST = 309;
    private static final int NOTIFICATION_TYPE_ON_DATA_PATH_SCHED_UPDATE = 312;
    private static final int NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL = 308;
    private static final int NOTIFICATION_TYPE_ON_MESSAGE_SEND_SUCCESS = 307;
    private static final int NOTIFICATION_TYPE_SESSION_TERMINATED = 304;
    public static final String PARAM_ON_IDLE_DISABLE_AWARE = "on_idle_disable_aware";
    public static final int PARAM_ON_IDLE_DISABLE_AWARE_DEFAULT = 1;
    private static final int RESPONSE_TIMEOUT_TYPE_ON_AWARE_DOWN = 250;
    private static final int RESPONSE_TYPE_ON_CAPABILITIES_UPDATED = 206;
    private static final int RESPONSE_TYPE_ON_CONFIG_FAIL = 201;
    private static final int RESPONSE_TYPE_ON_CONFIG_SUCCESS = 200;
    private static final int RESPONSE_TYPE_ON_CREATE_INTERFACE = 207;
    private static final int RESPONSE_TYPE_ON_DELETE_INTERFACE = 208;
    private static final int RESPONSE_TYPE_ON_DISABLE = 213;
    private static final int RESPONSE_TYPE_ON_END_DATA_PATH = 212;
    private static final int RESPONSE_TYPE_ON_INITIATE_DATA_PATH_FAIL = 210;
    private static final int RESPONSE_TYPE_ON_INITIATE_DATA_PATH_SUCCESS = 209;
    private static final int RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_FAIL = 205;
    private static final int RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_SUCCESS = 204;
    private static final int RESPONSE_TYPE_ON_RESPOND_TO_DATA_PATH_SETUP_REQUEST = 211;
    private static final int RESPONSE_TYPE_ON_SESSION_CONFIG_FAIL = 203;
    private static final int RESPONSE_TYPE_ON_SESSION_CONFIG_SUCCESS = 202;
    private static final String TAG = "WifiAwareStateManager";
    private static final boolean VDBG = true;
    private static final boolean VVDBG = true;
    public static final int WIFI_AWARE_BIGDATA_OPERATION_TYPE_ATTACH = 1;
    public static final int WIFI_AWARE_BIGDATA_OPERATION_TYPE_DISCONNECT = 6;
    public static final int WIFI_AWARE_BIGDATA_OPERATION_TYPE_NDP_SETUP = 4;
    public static final int WIFI_AWARE_BIGDATA_OPERATION_TYPE_NDP_TERMINATION = 5;
    public static final int WIFI_AWARE_BIGDATA_OPERATION_TYPE_PUBLISH = 2;
    public static final int WIFI_AWARE_BIGDATA_OPERATION_TYPE_SUBSCRIBE = 3;
    private static final SparseArray<String> sSmToString = MessageUtils.findMessageNames(new Class[]{WifiAwareStateManager.class}, new String[]{"MESSAGE_TYPE", "COMMAND_TYPE", "RESPONSE_TYPE", "NOTIFICATION_TYPE"});
    private final String FEATURE_ID = "WAOR";
    private boolean mAttachIsRunning = false;
    private WifiAwareMetrics mAwareMetrics;
    private int mAwareTrafficPollToken = 0;
    private volatile Capabilities mCapabilities;
    private WifiP2pManager.Channel mChannel;
    private volatile Characteristics mCharacteristics = null;
    private final SparseArray<WifiAwareClientState> mClients = new SparseArray<>();
    private Context mContext;
    private ConfigRequest mCurrentAwareConfiguration = null;
    private byte[] mCurrentDiscoveryInterfaceMac = ALL_ZERO_MAC;
    private boolean mCurrentIdentityNotification = false;
    public WifiAwareDataPathStateManager mDataPathMgr;
    boolean mDbg = true;
    private HashMap<Integer, String> mDisconnectedCallingPackage = new HashMap<>();
    private boolean mIsBootComplete = false;
    private LocationManager mLocationManager;
    private volatile int mPollTrafficIntervalMsecs = 3000;
    private PowerManager mPowerManager;
    private boolean mRequestDisable = false;
    private ArrayList<Integer> mSavedClientId = new ArrayList<>();
    private boolean mSelectedCancel = false;
    private SemHqmManager mSemHqmManager;
    private Map<String, Integer> mSettableParameters = new HashMap();
    private WifiAwareStateMachine mSm;
    private volatile boolean mUsageEnabled = false;
    public WifiAwareBigdata mWifiAwareBigData;
    private WifiAwareNativeApi mWifiAwareNativeApi;
    private WifiAwareNativeManager mWifiAwareNativeManager;
    private WifiManager mWifiManager;
    private WifiP2pManager mWifiP2pManager;
    private WifiPermissionsUtil mWifiPermissionsUtil;
    private Message msgBackup = null;

    static /* synthetic */ int access$3308(WifiAwareStateManager x0) {
        int i = x0.mAwareTrafficPollToken;
        x0.mAwareTrafficPollToken = i + 1;
        return i;
    }

    public WifiAwareStateManager() {
        onReset();
    }

    public void setNative(WifiAwareNativeManager wifiAwareNativeManager, WifiAwareNativeApi wifiAwareNativeApi) {
        this.mWifiAwareNativeManager = wifiAwareNativeManager;
        this.mWifiAwareNativeApi = wifiAwareNativeApi;
    }

    /* JADX INFO: Can't fix incorrect switch cases order, some code will duplicate */
    @Override // com.android.server.wifi.aware.WifiAwareShellCommand.DelegatedShellCommand
    public int onCommand(ShellCommand parentShell) {
        boolean z;
        PrintWriter pw_err = parentShell.getErrPrintWriter();
        PrintWriter pw_out = parentShell.getOutPrintWriter();
        String subCmd = parentShell.getNextArgRequired();
        Log.v(TAG, "onCommand: subCmd='" + subCmd + "'");
        switch (subCmd.hashCode()) {
            case -1212873217:
                if (subCmd.equals("get_capabilities")) {
                    z = true;
                    break;
                }
                z = true;
                break;
            case 102230:
                if (subCmd.equals("get")) {
                    z = true;
                    break;
                }
                z = true;
                break;
            case 113762:
                if (subCmd.equals("set")) {
                    z = false;
                    break;
                }
                z = true;
                break;
            case 1060304561:
                if (subCmd.equals("allow_ndp_any")) {
                    z = true;
                    break;
                }
                z = true;
                break;
            default:
                z = true;
                break;
        }
        if (!z) {
            String name = parentShell.getNextArgRequired();
            Log.v(TAG, "onCommand: name='" + name + "'");
            if (!this.mSettableParameters.containsKey(name)) {
                pw_err.println("Unknown parameter name -- '" + name + "'");
                return -1;
            }
            String valueStr = parentShell.getNextArgRequired();
            Log.v(TAG, "onCommand: valueStr='" + valueStr + "'");
            try {
                this.mSettableParameters.put(name, Integer.valueOf(Integer.valueOf(valueStr).intValue()));
                return 0;
            } catch (NumberFormatException e) {
                pw_err.println("Can't convert value to integer -- '" + valueStr + "'");
                return -1;
            }
        } else if (z) {
            String name2 = parentShell.getNextArgRequired();
            Log.v(TAG, "onCommand: name='" + name2 + "'");
            if (!this.mSettableParameters.containsKey(name2)) {
                pw_err.println("Unknown parameter name -- '" + name2 + "'");
                return -1;
            }
            pw_out.println(this.mSettableParameters.get(name2).intValue());
            return 0;
        } else if (!z) {
            if (z) {
                String flag = parentShell.getNextArgRequired();
                Log.v(TAG, "onCommand: flag='" + flag + "'");
                if (this.mDataPathMgr == null) {
                    pw_err.println("Null Aware data-path manager - can't configure");
                    return -1;
                } else if (TextUtils.equals("true", flag)) {
                    this.mDataPathMgr.mAllowNdpResponderFromAnyOverride = true;
                } else if (TextUtils.equals("false", flag)) {
                    this.mDataPathMgr.mAllowNdpResponderFromAnyOverride = false;
                } else {
                    pw_err.println("Unknown configuration flag for 'allow_ndp_any' - true|false expected -- '" + flag + "'");
                    return -1;
                }
            }
            pw_err.println("Unknown 'wifiaware state_mgr <cmd>'");
            return -1;
        } else {
            JSONObject j = new JSONObject();
            if (this.mCapabilities != null) {
                try {
                    j.put("maxConcurrentAwareClusters", this.mCapabilities.maxConcurrentAwareClusters);
                    j.put("maxPublishes", this.mCapabilities.maxPublishes);
                    j.put("maxSubscribes", this.mCapabilities.maxSubscribes);
                    j.put("maxServiceNameLen", this.mCapabilities.maxServiceNameLen);
                    j.put("maxMatchFilterLen", this.mCapabilities.maxMatchFilterLen);
                    j.put("maxTotalMatchFilterLen", this.mCapabilities.maxTotalMatchFilterLen);
                    j.put("maxServiceSpecificInfoLen", this.mCapabilities.maxServiceSpecificInfoLen);
                    j.put("maxExtendedServiceSpecificInfoLen", this.mCapabilities.maxExtendedServiceSpecificInfoLen);
                    j.put("maxNdiInterfaces", this.mCapabilities.maxNdiInterfaces);
                    j.put("maxNdpSessions", this.mCapabilities.maxNdpSessions);
                    j.put("maxAppInfoLen", this.mCapabilities.maxAppInfoLen);
                    j.put("maxQueuedTransmitMessages", this.mCapabilities.maxQueuedTransmitMessages);
                    j.put("maxSubscribeInterfaceAddresses", this.mCapabilities.maxSubscribeInterfaceAddresses);
                    j.put("supportedCipherSuites", this.mCapabilities.supportedCipherSuites);
                } catch (JSONException e2) {
                    Log.e(TAG, "onCommand: get_capabilities e=" + e2);
                }
            }
            pw_out.println(j.toString());
            return 0;
        }
    }

    @Override // com.android.server.wifi.aware.WifiAwareShellCommand.DelegatedShellCommand
    public void onReset() {
        this.mSettableParameters.put(PARAM_ON_IDLE_DISABLE_AWARE, 1);
        WifiAwareDataPathStateManager wifiAwareDataPathStateManager = this.mDataPathMgr;
        if (wifiAwareDataPathStateManager != null) {
            wifiAwareDataPathStateManager.mAllowNdpResponderFromAnyOverride = false;
        }
    }

    @Override // com.android.server.wifi.aware.WifiAwareShellCommand.DelegatedShellCommand
    public void onHelp(String command, ShellCommand parentShell) {
        PrintWriter pw = parentShell.getOutPrintWriter();
        pw.println("  " + command);
        pw.println("    set <name> <value>: sets named parameter to value. Names: " + this.mSettableParameters.keySet());
        pw.println("    get <name>: gets named parameter value. Names: " + this.mSettableParameters.keySet());
        pw.println("    get_capabilities: prints out the capabilities as a JSON string");
        pw.println("    allow_ndp_any true|false: configure whether Responders can be specified to accept requests from ANY requestor (null peer spec)");
    }

    public void start(Context context, Looper looper, WifiAwareMetrics awareMetrics, final WifiPermissionsUtil wifiPermissionsUtil, WifiPermissionsWrapper permissionsWrapper, Clock clock) {
        Log.i(TAG, "start()");
        this.mContext = context;
        this.mAwareMetrics = awareMetrics;
        this.mWifiPermissionsUtil = wifiPermissionsUtil;
        this.mSm = new WifiAwareStateMachine(TAG, looper);
        this.mSm.setDbg(true);
        this.mSm.start();
        this.mDataPathMgr = new WifiAwareDataPathStateManager(this, clock);
        this.mDataPathMgr.start(this.mContext, this.mSm.getHandler().getLooper(), awareMetrics, wifiPermissionsUtil, permissionsWrapper);
        this.mPowerManager = (PowerManager) this.mContext.getSystemService(PowerManager.class);
        this.mLocationManager = (LocationManager) this.mContext.getSystemService("location");
        this.mWifiManager = (WifiManager) this.mContext.getSystemService("wifi");
        this.mWifiP2pManager = (WifiP2pManager) this.mContext.getSystemService("wifip2p");
        this.mWifiAwareBigData = new WifiAwareBigdata();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.SCREEN_ON");
        intentFilter.addAction("android.intent.action.SCREEN_OFF");
        intentFilter.addAction("android.os.action.DEVICE_IDLE_MODE_CHANGED");
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.aware.WifiAwareStateManager.C04961 */

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.v(WifiAwareStateManager.TAG, "BroadcastReceiver: action=" + action);
                if (action.equals("android.intent.action.SCREEN_ON") || action.equals("android.intent.action.SCREEN_OFF")) {
                    WifiAwareStateManager.this.reconfigure();
                }
                if (!action.equals("android.os.action.DEVICE_IDLE_MODE_CHANGED")) {
                    return;
                }
                if (((Integer) WifiAwareStateManager.this.mSettableParameters.get(WifiAwareStateManager.PARAM_ON_IDLE_DISABLE_AWARE)).intValue() == 0) {
                    WifiAwareStateManager.this.reconfigure();
                } else if (WifiAwareStateManager.this.mPowerManager.isDeviceIdleMode()) {
                    WifiAwareStateManager.this.disableUsage();
                } else {
                    WifiAwareStateManager.this.enableUsage();
                }
            }
        }, intentFilter);
        IntentFilter intentFilter2 = new IntentFilter();
        intentFilter2.addAction("android.location.MODE_CHANGED");
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.aware.WifiAwareStateManager.C04972 */

            public void onReceive(Context context, Intent intent) {
                if (WifiAwareStateManager.this.mDbg) {
                    Log.v(WifiAwareStateManager.TAG, "onReceive: MODE_CHANGED_ACTION: intent=" + intent);
                }
                if (wifiPermissionsUtil.isLocationModeEnabled()) {
                    WifiAwareStateManager.this.enableUsage();
                } else if (WifiAwareStateManager.this.mSavedClientId == null || WifiAwareStateManager.this.mSavedClientId.size() <= 0) {
                    WifiAwareStateManager.this.disableUsage();
                } else {
                    for (int i = 0; i < WifiAwareStateManager.this.mSavedClientId.size(); i++) {
                        Log.d(WifiAwareStateManager.TAG, "disableUsageLocal: because LocationWhiteList is running, disable is skiped: clientID : " + WifiAwareStateManager.this.mSavedClientId.get(i));
                    }
                }
            }
        }, intentFilter2);
        IntentFilter intentFilter3 = new IntentFilter();
        intentFilter3.addAction("com.samsung.android.settings.wifi.enableNan");
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.aware.WifiAwareStateManager.C04983 */

            public void onReceive(Context context, Intent intent) {
                Log.d(WifiAwareStateManager.TAG, "onReceive: enableNan: intent=" + intent);
                if (intent.getBooleanExtra("enabled", false)) {
                    if (WifiAwareStateManager.this.mSm != null && WifiAwareStateManager.this.msgBackup != null) {
                        if (WifiAwareStateManager.this.mCapabilities == null) {
                            WifiAwareStateManager.this.getAwareInterface();
                            WifiAwareStateManager.this.queryCapabilities();
                        }
                        WifiAwareStateManager.this.mSelectedCancel = false;
                        WifiAwareStateManager.this.mSm.sendMessage(WifiAwareStateManager.this.msgBackup);
                        WifiAwareStateManager.this.msgBackup = null;
                        Log.d(WifiAwareStateManager.TAG, "onReceive : enableNan: ok");
                    }
                } else if (!intent.getBooleanExtra("enabled", false) && WifiAwareStateManager.this.mSm != null && WifiAwareStateManager.this.msgBackup != null) {
                    WifiAwareStateManager.this.mSelectedCancel = true;
                    WifiAwareStateManager.this.mSm.sendMessage(WifiAwareStateManager.this.msgBackup);
                    WifiAwareStateManager.this.msgBackup = null;
                    Log.d(WifiAwareStateManager.TAG, "onReceive : enableNan: cancel");
                }
            }
        }, intentFilter3);
        IntentFilter intentFilter4 = new IntentFilter();
        intentFilter4.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.aware.WifiAwareStateManager.C04994 */

            public void onReceive(Context context, Intent intent) {
                if (!(intent.getIntExtra("wifi_state", 4) == 3)) {
                    WifiAwareStateManager.this.disableUsage();
                } else if (WifiAwareStateManager.this.mWifiManager.isWifiApEnabled() || WifiAwareStateManager.this.mWifiManager.getWifiApState() == 12) {
                    WifiAwareStateManager.this.enableUsage();
                }
            }
        }, intentFilter4);
        IntentFilter intentFilter5 = new IntentFilter();
        intentFilter5.addAction("android.net.wifi.p2p.STATE_CHANGED");
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.aware.WifiAwareStateManager.C05005 */

            public void onReceive(Context context, Intent intent) {
                boolean isEnabled = true;
                if (intent.getIntExtra("wifi_p2p_state", 1) != 2) {
                    isEnabled = false;
                }
                if (isEnabled) {
                    WifiAwareStateManager.this.enableUsage();
                }
            }
        }, intentFilter5);
        IntentFilter intentFilter6 = new IntentFilter();
        intentFilter6.addAction("android.intent.action.BOOT_COMPLETED");
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.android.server.wifi.aware.WifiAwareStateManager.C05016 */

            public void onReceive(Context context, Intent intent) {
                Log.d(WifiAwareStateManager.TAG, "OnReceive - ACTION_BOOT_COMPLETED");
                WifiAwareStateManager.this.mIsBootComplete = true;
            }
        }, intentFilter6);
    }

    public void startLate() {
        delayedInitialization();
    }

    /* access modifiers changed from: package-private */
    public WifiAwareClientState getClient(int clientId) {
        return this.mClients.get(clientId);
    }

    public Capabilities getCapabilities() {
        return this.mCapabilities;
    }

    public Characteristics getCharacteristics() {
        if (this.mCharacteristics == null && this.mCapabilities != null) {
            this.mCharacteristics = this.mCapabilities.toPublicCharacteristics();
        }
        return this.mCharacteristics;
    }

    public int getCountMaxNdp() {
        if (this.mCapabilities == null) {
            return -1;
        }
        if (this.mCapabilities.maxNdpSessions >= 5) {
            return 5;
        }
        return this.mCapabilities.maxNdpSessions;
    }

    public int getCountNdp() {
        WifiAwareDataPathStateManager wifiAwareDataPathStateManager = this.mDataPathMgr;
        if (wifiAwareDataPathStateManager != null) {
            return wifiAwareDataPathStateManager.getCountDataPath();
        }
        return -1;
    }

    public void requestMacAddresses(int uid, List<Integer> peerIds, IWifiAwareMacAddressProvider callback) {
        this.mSm.getHandler().post(new Runnable(uid, peerIds, callback) {
            /* class com.android.server.wifi.aware.$$Lambda$WifiAwareStateManager$k1e2sgI9ioQdd4UFKxciMG2eSr4 */
            private final /* synthetic */ int f$1;
            private final /* synthetic */ List f$2;
            private final /* synthetic */ IWifiAwareMacAddressProvider f$3;

            {
                this.f$1 = r2;
                this.f$2 = r3;
                this.f$3 = r4;
            }

            public final void run() {
                WifiAwareStateManager.this.lambda$requestMacAddresses$0$WifiAwareStateManager(this.f$1, this.f$2, this.f$3);
            }
        });
    }

    public /* synthetic */ void lambda$requestMacAddresses$0$WifiAwareStateManager(int uid, List peerIds, IWifiAwareMacAddressProvider callback) {
        Log.v(TAG, "requestMacAddresses: uid=" + uid + ", peerIds=" + peerIds);
        Map<Integer, byte[]> peerIdToMacMap = new HashMap<>();
        for (int i = 0; i < this.mClients.size(); i++) {
            WifiAwareClientState client = this.mClients.valueAt(i);
            if (client.getUid() == uid) {
                SparseArray<WifiAwareDiscoverySessionState> sessions = client.getSessions();
                for (int j = 0; j < sessions.size(); j++) {
                    WifiAwareDiscoverySessionState session = sessions.valueAt(j);
                    Iterator it = peerIds.iterator();
                    while (it.hasNext()) {
                        int peerId = ((Integer) it.next()).intValue();
                        WifiAwareDiscoverySessionState.PeerInfo peerInfo = session.getPeerInfo(peerId);
                        if (peerInfo != null) {
                            peerIdToMacMap.put(Integer.valueOf(peerId), peerInfo.mMac);
                        }
                    }
                }
            }
        }
        try {
            Log.v(TAG, "requestMacAddresses: peerIdToMacMap=" + peerIdToMacMap);
            callback.macAddress(peerIdToMacMap);
        } catch (RemoteException e) {
            Log.e(TAG, "requestMacAddress (sync): exception on callback -- " + e);
        }
    }

    public void delayedInitialization() {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = COMMAND_TYPE_DELAYED_INITIALIZATION;
        this.mSm.sendMessage(msg);
    }

    public void getAwareInterface() {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = COMMAND_TYPE_GET_AWARE;
        this.mSm.sendMessage(msg);
    }

    public void releaseAwareInterface() {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = COMMAND_TYPE_RELEASE_AWARE;
        this.mSm.sendMessage(msg);
    }

    public void connect(int clientId, int uid, int pid, String callingPackage, IWifiAwareEventCallback callback, ConfigRequest configRequest, boolean notifyOnIdentityChanged) {
        WifiManager wifiManager;
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 100;
        msg.arg2 = clientId;
        msg.obj = callback;
        msg.getData().putParcelable(MESSAGE_BUNDLE_KEY_CONFIG, configRequest);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_UID, uid);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PID, pid);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_CALLING_PACKAGE, callingPackage);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_NOTIFY_IDENTITY_CHANGE, notifyOnIdentityChanged);
        if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharing() && (wifiManager = this.mWifiManager) != null && (wifiManager.isWifiApEnabled() || this.mWifiManager.getWifiApState() == 12)) {
            checkAndShowAwareEnableDialog(0);
            this.msgBackup = msg;
        } else if (this.mWifiP2pManager.isWifiP2pConnected()) {
            checkAndShowAwareEnableDialog(1);
            this.msgBackup = msg;
        } else {
            if (this.mCapabilities == null) {
                getAwareInterface();
                queryCapabilities();
            }
            this.mSm.sendMessage(msg);
        }
    }

    public void disconnect(int clientId) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 101;
        msg.arg2 = clientId;
        this.mSm.sendMessage(msg);
    }

    public void reconfigure() {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 120;
        this.mSm.sendMessage(msg);
    }

    public void terminateSession(int clientId, int sessionId) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 102;
        msg.arg2 = clientId;
        msg.obj = Integer.valueOf(sessionId);
        this.mSm.sendMessage(msg);
    }

    public void publish(int clientId, PublishConfig publishConfig, IWifiAwareDiscoverySessionCallback callback) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 103;
        msg.arg2 = clientId;
        msg.obj = callback;
        msg.getData().putParcelable(MESSAGE_BUNDLE_KEY_CONFIG, publishConfig);
        this.mSm.sendMessage(msg);
    }

    public void updatePublish(int clientId, int sessionId, PublishConfig publishConfig) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 104;
        msg.arg2 = clientId;
        msg.obj = publishConfig;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_SESSION_ID, sessionId);
        this.mSm.sendMessage(msg);
    }

    public void subscribe(int clientId, SubscribeConfig subscribeConfig, IWifiAwareDiscoverySessionCallback callback) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 105;
        msg.arg2 = clientId;
        msg.obj = callback;
        msg.getData().putParcelable(MESSAGE_BUNDLE_KEY_CONFIG, subscribeConfig);
        this.mSm.sendMessage(msg);
    }

    public void updateSubscribe(int clientId, int sessionId, SubscribeConfig subscribeConfig) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 106;
        msg.arg2 = clientId;
        msg.obj = subscribeConfig;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_SESSION_ID, sessionId);
        this.mSm.sendMessage(msg);
    }

    public void sendMessage(int clientId, int sessionId, int peerId, byte[] message, int messageId, int retryCount) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 107;
        msg.arg2 = clientId;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_SESSION_ID, sessionId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_MESSAGE_PEER_ID, peerId);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MESSAGE, message);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_MESSAGE_ID, messageId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_RETRY_COUNT, retryCount);
        this.mSm.sendMessage(msg);
    }

    public void enableUsage() {
        if (this.mSettableParameters.get(PARAM_ON_IDLE_DISABLE_AWARE).intValue() == 0 || !this.mPowerManager.isDeviceIdleMode()) {
            if (!this.mWifiPermissionsUtil.isLocationModeEnabled()) {
                if (this.mDbg) {
                    Log.d(TAG, "enableUsage(): while location is disabled - ignoring");
                }
            } else if (this.mWifiManager.getWifiState() == 3) {
                Message msg = this.mSm.obtainMessage(1);
                msg.arg1 = COMMAND_TYPE_ENABLE_USAGE;
                this.mSm.sendMessage(msg);
            } else if (this.mDbg) {
                Log.d(TAG, "enableUsage(): while Wi-Fi is disabled - ignoring");
            }
        } else if (this.mDbg) {
            Log.d(TAG, "enableUsage(): while device is in IDLE mode - ignoring");
        }
    }

    public void disableUsage() {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = COMMAND_TYPE_DISABLE_USAGE;
        this.mSm.sendMessage(msg);
    }

    public boolean isUsageEnabled() {
        return this.mUsageEnabled;
    }

    public boolean isUsageEnabledForSem(String callingPackage) {
        if (allowLocationWhiteList(callingPackage)) {
            return true;
        }
        return this.mUsageEnabled;
    }

    public boolean isAwareEnabled() {
        SparseArray<WifiAwareClientState> sparseArray = this.mClients;
        if ((sparseArray == null || sparseArray.size() <= 0) && !this.mAttachIsRunning) {
            return false;
        }
        return true;
    }

    public void setClusterMergingEnabled(boolean mergeEnabled) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = COMMAND_TYPE_SET_CLUSTER_MERGE_ENABLE;
        if (mergeEnabled) {
            msg.arg2 = 1;
        } else {
            msg.arg2 = 0;
        }
        this.mSm.sendMessage(msg);
    }

    public void queryCapabilities() {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 111;
        this.mSm.sendMessage(msg);
    }

    public void createAllDataPathInterfaces() {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 112;
        this.mSm.sendMessage(msg);
    }

    public void deleteAllDataPathInterfaces() {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 113;
        this.mSm.sendMessage(msg);
    }

    public void createDataPathInterface(String interfaceName) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = COMMAND_TYPE_CREATE_DATA_PATH_INTERFACE;
        msg.obj = interfaceName;
        this.mSm.sendMessage(msg);
    }

    public void deleteDataPathInterface(String interfaceName) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = COMMAND_TYPE_DELETE_DATA_PATH_INTERFACE;
        msg.obj = interfaceName;
        this.mSm.sendMessage(msg);
    }

    public void initiateDataPathSetup(WifiAwareNetworkSpecifier networkSpecifier, int peerId, int channelRequestType, int channel, byte[] peer, String interfaceName, byte[] pmk, String passphrase, boolean isOutOfBand, byte[] appInfo) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = COMMAND_TYPE_INITIATE_DATA_PATH_SETUP;
        msg.obj = networkSpecifier;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PEER_ID, peerId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_CHANNEL_REQ_TYPE, channelRequestType);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_CHANNEL, channel);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, peer);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_INTERFACE_NAME, interfaceName);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_PMK, pmk);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_PASSPHRASE, passphrase);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_OOB, isOutOfBand);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_APP_INFO, appInfo);
        this.mSm.sendMessage(msg);
    }

    public void respondToDataPathRequest(boolean accept, int ndpId, String interfaceName, byte[] pmk, String passphrase, byte[] appInfo, boolean isOutOfBand) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 117;
        msg.arg2 = ndpId;
        msg.obj = Boolean.valueOf(accept);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_INTERFACE_NAME, interfaceName);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_PMK, pmk);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_PASSPHRASE, passphrase);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_APP_INFO, appInfo);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_OOB, isOutOfBand);
        this.mSm.sendMessage(msg);
    }

    public void endDataPath(int ndpId) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = COMMAND_TYPE_END_DATA_PATH;
        msg.arg2 = ndpId;
        this.mSm.sendMessage(msg);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void transmitNextMessage() {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = COMMAND_TYPE_TRANSMIT_NEXT_MESSAGE;
        this.mSm.sendMessage(msg);
    }

    public void sendAwareBigdata(String pkg, int type, int clientId, int rangeMm, String svcName, long nanOpDuration) {
        Message msg = this.mSm.obtainMessage(1);
        msg.arg1 = 160;
        msg.getData().putString(MESSAGE_BUNDLE_KEY_CALLING_PACKAGE, pkg);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_OPERATION_TYPE, type);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_CLIENT_ID, clientId);
        msg.getData().putInt("range_mm", rangeMm);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_SERVICE_NAME, svcName);
        msg.getData().putLong(MESSAGE_BUNDLE_KEY_NAN_OP_DURATION, nanOpDuration);
        this.mSm.sendMessage(msg);
    }

    public void onConfigSuccessResponse(short transactionId) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = 200;
        msg.arg2 = transactionId;
        this.mSm.sendMessage(msg);
    }

    public void onConfigFailedResponse(short transactionId, int reason) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = 201;
        msg.arg2 = transactionId;
        msg.obj = Integer.valueOf(reason);
        this.mSm.sendMessage(msg);
    }

    public void onDisableResponse(short transactionId, int reason) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = RESPONSE_TYPE_ON_DISABLE;
        msg.arg2 = transactionId;
        msg.obj = Integer.valueOf(reason);
        this.mSm.sendMessage(msg);
    }

    public void onAwaredownResponseTimeout() {
        Message msg = this.mSm.obtainMessage(4);
        msg.arg1 = 250;
        this.mSm.sendMessage(msg);
    }

    public void onSessionConfigSuccessResponse(short transactionId, boolean isPublish, byte pubSubId) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = 202;
        msg.arg2 = transactionId;
        msg.obj = Byte.valueOf(pubSubId);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SESSION_TYPE, isPublish);
        this.mSm.sendMessage(msg);
    }

    public void onSessionConfigFailResponse(short transactionId, boolean isPublish, int reason) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = 203;
        msg.arg2 = transactionId;
        msg.obj = Integer.valueOf(reason);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SESSION_TYPE, isPublish);
        this.mSm.sendMessage(msg);
    }

    public void onMessageSendQueuedSuccessResponse(short transactionId) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_SUCCESS;
        msg.arg2 = transactionId;
        this.mSm.sendMessage(msg);
    }

    public void onMessageSendQueuedFailResponse(short transactionId, int reason) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_FAIL;
        msg.arg2 = transactionId;
        msg.obj = Integer.valueOf(reason);
        this.mSm.sendMessage(msg);
    }

    public void onCapabilitiesUpdateResponse(short transactionId, Capabilities capabilities) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = RESPONSE_TYPE_ON_CAPABILITIES_UPDATED;
        msg.arg2 = transactionId;
        msg.obj = capabilities;
        this.mSm.sendMessage(msg);
    }

    public void onCreateDataPathInterfaceResponse(short transactionId, boolean success, int reasonOnFailure) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = RESPONSE_TYPE_ON_CREATE_INTERFACE;
        msg.arg2 = transactionId;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, success);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, reasonOnFailure);
        this.mSm.sendMessage(msg);
    }

    public void onDeleteDataPathInterfaceResponse(short transactionId, boolean success, int reasonOnFailure) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = RESPONSE_TYPE_ON_DELETE_INTERFACE;
        msg.arg2 = transactionId;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, success);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, reasonOnFailure);
        this.mSm.sendMessage(msg);
    }

    public void onInitiateDataPathResponseSuccess(short transactionId, int ndpId) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = RESPONSE_TYPE_ON_INITIATE_DATA_PATH_SUCCESS;
        msg.arg2 = transactionId;
        msg.obj = Integer.valueOf(ndpId);
        this.mSm.sendMessage(msg);
    }

    public void onInitiateDataPathResponseFail(short transactionId, int reason) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = RESPONSE_TYPE_ON_INITIATE_DATA_PATH_FAIL;
        msg.arg2 = transactionId;
        msg.obj = Integer.valueOf(reason);
        this.mSm.sendMessage(msg);
    }

    public void onRespondToDataPathSetupRequestResponse(short transactionId, boolean success, int reasonOnFailure) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = RESPONSE_TYPE_ON_RESPOND_TO_DATA_PATH_SETUP_REQUEST;
        msg.arg2 = transactionId;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, success);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, reasonOnFailure);
        this.mSm.sendMessage(msg);
    }

    public void onEndDataPathResponse(short transactionId, boolean success, int reasonOnFailure) {
        Message msg = this.mSm.obtainMessage(2);
        msg.arg1 = RESPONSE_TYPE_ON_END_DATA_PATH;
        msg.arg2 = transactionId;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, success);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, reasonOnFailure);
        this.mSm.sendMessage(msg);
    }

    public void onInterfaceAddressChangeNotification(byte[] mac) {
        Message msg = this.mSm.obtainMessage(3);
        msg.arg1 = NOTIFICATION_TYPE_INTERFACE_CHANGE;
        msg.obj = mac;
        this.mSm.sendMessage(msg);
    }

    public void onClusterChangeNotification(int flag, byte[] clusterId) {
        Message msg = this.mSm.obtainMessage(3);
        msg.arg1 = NOTIFICATION_TYPE_CLUSTER_CHANGE;
        msg.arg2 = flag;
        msg.obj = clusterId;
        this.mSm.sendMessage(msg);
    }

    public void onMatchNotification(int pubSubId, int requestorInstanceId, byte[] peerMac, byte[] serviceSpecificInfo, byte[] matchFilter, int rangingIndication, int rangeMm) {
        Message msg = this.mSm.obtainMessage(3);
        msg.arg1 = NOTIFICATION_TYPE_MATCH;
        msg.arg2 = pubSubId;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_REQ_INSTANCE_ID, requestorInstanceId);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, peerMac);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_SSI_DATA, serviceSpecificInfo);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_FILTER_DATA, matchFilter);
        msg.getData().putInt(MESSAGE_RANGING_INDICATION, rangingIndication);
        msg.getData().putInt("range_mm", rangeMm);
        this.mSm.sendMessage(msg);
    }

    public void onSessionTerminatedNotification(int pubSubId, int reason, boolean isPublish) {
        Message msg = this.mSm.obtainMessage(3);
        msg.arg1 = NOTIFICATION_TYPE_SESSION_TERMINATED;
        msg.arg2 = pubSubId;
        msg.obj = Integer.valueOf(reason);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SESSION_TYPE, isPublish);
        this.mSm.sendMessage(msg);
    }

    public void onMessageReceivedNotification(int pubSubId, int requestorInstanceId, byte[] peerMac, byte[] message) {
        Message msg = this.mSm.obtainMessage(3);
        msg.arg1 = NOTIFICATION_TYPE_MESSAGE_RECEIVED;
        msg.arg2 = pubSubId;
        msg.obj = Integer.valueOf(requestorInstanceId);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, peerMac);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MESSAGE_DATA, message);
        this.mSm.sendMessage(msg);
    }

    public void onAwareDownNotification(int reason) {
        Message msg = this.mSm.obtainMessage(3);
        msg.arg1 = NOTIFICATION_TYPE_AWARE_DOWN;
        msg.arg2 = reason;
        this.mSm.sendMessage(msg);
    }

    public void onMessageSendSuccessNotification(short transactionId) {
        Message msg = this.mSm.obtainMessage(3);
        msg.arg1 = NOTIFICATION_TYPE_ON_MESSAGE_SEND_SUCCESS;
        msg.arg2 = transactionId;
        this.mSm.sendMessage(msg);
    }

    public void onMessageSendFailNotification(short transactionId, int reason) {
        Message msg = this.mSm.obtainMessage(3);
        msg.arg1 = NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL;
        msg.arg2 = transactionId;
        msg.obj = Integer.valueOf(reason);
        this.mSm.sendMessage(msg);
    }

    public void onDataPathRequestNotification(int pubSubId, byte[] mac, int ndpId, byte[] message) {
        Message msg = this.mSm.obtainMessage(3);
        msg.arg1 = NOTIFICATION_TYPE_ON_DATA_PATH_REQUEST;
        msg.arg2 = pubSubId;
        msg.obj = Integer.valueOf(ndpId);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, mac);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MESSAGE, message);
        this.mSm.sendMessage(msg);
    }

    public void onDataPathConfirmNotification(int ndpId, byte[] mac, boolean accept, int reason, byte[] message, List<NanDataPathChannelInfo> channelInfo) {
        Message msg = this.mSm.obtainMessage(3);
        msg.arg1 = NOTIFICATION_TYPE_ON_DATA_PATH_CONFIRM;
        msg.arg2 = ndpId;
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, mac);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, accept);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, reason);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MESSAGE_DATA, message);
        msg.obj = channelInfo;
        this.mSm.sendMessage(msg);
    }

    public void onDataPathEndNotification(int ndpId) {
        Message msg = this.mSm.obtainMessage(3);
        msg.arg1 = NOTIFICATION_TYPE_ON_DATA_PATH_END;
        msg.arg2 = ndpId;
        this.mSm.sendMessage(msg);
    }

    public void onDataPathScheduleUpdateNotification(byte[] peerMac, ArrayList<Integer> ndpIds, List<NanDataPathChannelInfo> channelInfo) {
        Message msg = this.mSm.obtainMessage(3);
        msg.arg1 = NOTIFICATION_TYPE_ON_DATA_PATH_SCHED_UPDATE;
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, peerMac);
        msg.getData().putIntegerArrayList(MESSAGE_BUNDLE_KEY_NDP_IDS, ndpIds);
        msg.obj = channelInfo;
        this.mSm.sendMessage(msg);
    }

    /* access modifiers changed from: package-private */
    @VisibleForTesting
    public class WifiAwareStateMachine extends StateMachine {
        private static final long AWARE_SEND_MESSAGE_TIMEOUT = 10000;
        private static final long AWARE_WAIT_FOR_DP_CONFIRM_TIMEOUT = 20000;
        private static final int TRANSACTION_ID_IGNORE = 0;
        private Message mCurrentCommand;
        private short mCurrentTransactionId = 0;
        private final Map<WifiAwareNetworkSpecifier, WakeupMessage> mDataPathConfirmTimeoutMessages = new ArrayMap();
        private DefaultState mDefaultState = new DefaultState();
        private final Map<Short, Message> mFwQueuedSendMessages = new LinkedHashMap();
        private final SparseArray<Message> mHostQueuedSendMessages = new SparseArray<>();
        public int mNextSessionId = 1;
        private short mNextTransactionId = 1;
        private int mSendArrivalSequenceCounter = 0;
        private WakeupMessage mSendMessageTimeoutMessage = new WakeupMessage(WifiAwareStateManager.this.mContext, getHandler(), WifiAwareStateManager.HAL_SEND_MESSAGE_TIMEOUT_TAG, 5);
        private boolean mSendQueueBlocked = false;
        private WaitForResponseState mWaitForResponseState = new WaitForResponseState();
        private WaitState mWaitState = new WaitState();

        WifiAwareStateMachine(String name, Looper looper) {
            super(name, looper);
            addState(this.mDefaultState);
            addState(this.mWaitState, this.mDefaultState);
            addState(this.mWaitForResponseState, this.mDefaultState);
            setInitialState(this.mWaitState);
        }

        public void onAwareDownCleanupSendQueueState() {
            this.mSendQueueBlocked = false;
            this.mHostQueuedSendMessages.clear();
            this.mFwQueuedSendMessages.clear();
        }

        private class DefaultState extends State {
            private DefaultState() {
            }

            public boolean processMessage(Message msg) {
                Log.v(WifiAwareStateManager.TAG, getName() + msg.toString());
                int i = msg.what;
                if (i == 3) {
                    WifiAwareStateMachine.this.processNotification(msg);
                    return true;
                } else if (i == 5) {
                    WifiAwareStateMachine.this.processSendMessageTimeout();
                    return true;
                } else if (i != 6) {
                    Log.wtf(WifiAwareStateManager.TAG, "DefaultState: should not get non-NOTIFICATION in this state: msg=" + msg);
                    return false;
                } else {
                    WifiAwareNetworkSpecifier networkSpecifier = (WifiAwareNetworkSpecifier) msg.obj;
                    if (WifiAwareStateManager.this.mDbg) {
                        Log.v(WifiAwareStateManager.TAG, "MESSAGE_TYPE_DATA_PATH_TIMEOUT: networkSpecifier=" + networkSpecifier);
                    }
                    WifiAwareStateManager.this.mDataPathMgr.handleDataPathTimeout(networkSpecifier);
                    WifiAwareStateMachine.this.mDataPathConfirmTimeoutMessages.remove(networkSpecifier);
                    return true;
                }
            }
        }

        /* access modifiers changed from: private */
        public class WaitState extends State {
            private WaitState() {
            }

            public boolean processMessage(Message msg) {
                Log.v(WifiAwareStateManager.TAG, getName() + msg.toString());
                int i = msg.what;
                if (i == 1) {
                    if (WifiAwareStateMachine.this.processCommand(msg)) {
                        WifiAwareStateMachine wifiAwareStateMachine = WifiAwareStateMachine.this;
                        wifiAwareStateMachine.transitionTo(wifiAwareStateMachine.mWaitForResponseState);
                    }
                    return true;
                } else if (i != 2 && i != 4) {
                    return false;
                } else {
                    WifiAwareStateMachine.this.deferMessage(msg);
                    return true;
                }
            }
        }

        /* access modifiers changed from: private */
        public class WaitForResponseState extends State {
            private static final long AWARE_COMMAND_TIMEOUT = 5000;
            private WakeupMessage mTimeoutMessage;

            private WaitForResponseState() {
            }

            public void enter() {
                this.mTimeoutMessage = new WakeupMessage(WifiAwareStateManager.this.mContext, WifiAwareStateMachine.this.getHandler(), WifiAwareStateManager.HAL_COMMAND_TIMEOUT_TAG, 4, WifiAwareStateMachine.this.mCurrentCommand.arg1, WifiAwareStateMachine.this.mCurrentTransactionId);
                this.mTimeoutMessage.schedule(SystemClock.elapsedRealtime() + 5000);
            }

            public void exit() {
                this.mTimeoutMessage.cancel();
            }

            public boolean processMessage(Message msg) {
                Log.v(WifiAwareStateManager.TAG, getName() + msg.toString());
                int i = msg.what;
                if (i == 1) {
                    WifiAwareStateMachine.this.deferMessage(msg);
                    return true;
                } else if (i == 2) {
                    if (msg.arg2 == WifiAwareStateMachine.this.mCurrentTransactionId) {
                        WifiAwareStateMachine.this.processResponse(msg);
                        WifiAwareStateMachine wifiAwareStateMachine = WifiAwareStateMachine.this;
                        wifiAwareStateMachine.transitionTo(wifiAwareStateMachine.mWaitState);
                    } else {
                        Log.w(WifiAwareStateManager.TAG, "WaitForResponseState: processMessage: non-matching transaction ID on RESPONSE (a very late response) -- msg=" + msg);
                    }
                    return true;
                } else if (i != 4) {
                    return false;
                } else {
                    if (msg.arg1 == 250 && WifiAwareStateMachine.this.mCurrentCommand != null && WifiAwareStateMachine.this.mCurrentCommand.arg1 == 101) {
                        Log.w(WifiAwareStateManager.TAG, "WaitForResponseState: processMessage: msg.arg1: " + msg.arg1 + " mCurrentCommand: " + WifiAwareStateMachine.this.mCurrentCommand);
                        WifiAwareStateMachine wifiAwareStateMachine2 = WifiAwareStateMachine.this;
                        wifiAwareStateMachine2.processTimeout(wifiAwareStateMachine2.mCurrentCommand);
                        WifiAwareStateMachine wifiAwareStateMachine3 = WifiAwareStateMachine.this;
                        wifiAwareStateMachine3.transitionTo(wifiAwareStateMachine3.mWaitState);
                    } else if (msg.arg2 == WifiAwareStateMachine.this.mCurrentTransactionId) {
                        WifiAwareStateMachine.this.processTimeout(msg);
                        WifiAwareStateMachine wifiAwareStateMachine4 = WifiAwareStateMachine.this;
                        wifiAwareStateMachine4.transitionTo(wifiAwareStateMachine4.mWaitState);
                    } else {
                        Log.w(WifiAwareStateManager.TAG, "WaitForResponseState: processMessage: non-matching transaction ID on RESPONSE_TIMEOUT (either a non-cancelled timeout or a race condition with cancel) -- msg=" + msg);
                    }
                    return true;
                }
            }
        }

        /* JADX INFO: Multiple debug info for r2v5 int: [D('flag' int), D('pubSubId' int)] */
        /* JADX INFO: Multiple debug info for r2v8 int: [D('reason' int), D('pubSubId' int)] */
        /* JADX INFO: Multiple debug info for r2v9 int: [D('reason' int), D('transactionId' short)] */
        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void processNotification(Message msg) {
            WakeupMessage timeout;
            Log.v(WifiAwareStateManager.TAG, "processNotification: msg=" + msg);
            switch (msg.arg1) {
                case WifiAwareStateManager.NOTIFICATION_TYPE_INTERFACE_CHANGE /*{ENCODED_INT: 301}*/:
                    WifiAwareStateManager.this.onInterfaceAddressChangeLocal((byte[]) msg.obj);
                    return;
                case WifiAwareStateManager.NOTIFICATION_TYPE_CLUSTER_CHANGE /*{ENCODED_INT: 302}*/:
                    WifiAwareStateManager.this.onClusterChangeLocal(msg.arg2, (byte[]) msg.obj);
                    return;
                case WifiAwareStateManager.NOTIFICATION_TYPE_MATCH /*{ENCODED_INT: 303}*/:
                    int pubSubId = msg.arg2;
                    int requestorInstanceId = msg.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_REQ_INSTANCE_ID);
                    byte[] peerMac = msg.getData().getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MAC_ADDRESS);
                    byte[] serviceSpecificInfo = msg.getData().getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SSI_DATA);
                    byte[] matchFilter = msg.getData().getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_FILTER_DATA);
                    int rangingIndication = msg.getData().getInt(WifiAwareStateManager.MESSAGE_RANGING_INDICATION);
                    int rangeMm = msg.getData().getInt("range_mm");
                    if (peerMac != null) {
                        WifiAwareStateManager.this.onMatchLocal(pubSubId, requestorInstanceId, peerMac, serviceSpecificInfo, matchFilter, rangingIndication, rangeMm);
                        return;
                    }
                    return;
                case WifiAwareStateManager.NOTIFICATION_TYPE_SESSION_TERMINATED /*{ENCODED_INT: 304}*/:
                    WifiAwareStateManager.this.onSessionTerminatedLocal(msg.arg2, msg.getData().getBoolean(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SESSION_TYPE), ((Integer) msg.obj).intValue());
                    return;
                case WifiAwareStateManager.NOTIFICATION_TYPE_MESSAGE_RECEIVED /*{ENCODED_INT: 305}*/:
                    WifiAwareStateManager.this.onMessageReceivedLocal(msg.arg2, ((Integer) msg.obj).intValue(), msg.getData().getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MAC_ADDRESS), msg.getData().getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MESSAGE_DATA));
                    return;
                case WifiAwareStateManager.NOTIFICATION_TYPE_AWARE_DOWN /*{ENCODED_INT: 306}*/:
                    int reason = msg.arg2;
                    WifiAwareStateManager.this.onAwareDownLocal();
                    if (reason != 0) {
                        WifiAwareStateManager.this.sendAwareStateChangedBroadcast(false);
                        return;
                    }
                    return;
                case WifiAwareStateManager.NOTIFICATION_TYPE_ON_MESSAGE_SEND_SUCCESS /*{ENCODED_INT: 307}*/:
                    short transactionId = (short) msg.arg2;
                    Message queuedSendCommand = this.mFwQueuedSendMessages.get(Short.valueOf(transactionId));
                    Log.v(WifiAwareStateManager.TAG, "NOTIFICATION_TYPE_ON_MESSAGE_SEND_SUCCESS: queuedSendCommand=" + queuedSendCommand);
                    if (queuedSendCommand == null) {
                        Log.w(WifiAwareStateManager.TAG, "processNotification: NOTIFICATION_TYPE_ON_MESSAGE_SEND_SUCCESS: transactionId=" + ((int) transactionId) + " - no such queued send command (timed-out?)");
                    } else {
                        this.mFwQueuedSendMessages.remove(Short.valueOf(transactionId));
                        updateSendMessageTimeout();
                        WifiAwareStateManager.this.onMessageSendSuccessLocal(queuedSendCommand);
                    }
                    this.mSendQueueBlocked = false;
                    WifiAwareStateManager.this.transmitNextMessage();
                    return;
                case WifiAwareStateManager.NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL /*{ENCODED_INT: 308}*/:
                    short transactionId2 = (short) msg.arg2;
                    int reason2 = ((Integer) msg.obj).intValue();
                    Message sentMessage = this.mFwQueuedSendMessages.get(Short.valueOf(transactionId2));
                    Log.v(WifiAwareStateManager.TAG, "NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL: sentMessage=" + sentMessage);
                    if (sentMessage == null) {
                        Log.w(WifiAwareStateManager.TAG, "processNotification: NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL: transactionId=" + ((int) transactionId2) + " - no such queued send command (timed-out?)");
                        return;
                    }
                    this.mFwQueuedSendMessages.remove(Short.valueOf(transactionId2));
                    updateSendMessageTimeout();
                    int retryCount = sentMessage.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_RETRY_COUNT);
                    if (retryCount <= 0 || reason2 != 9) {
                        WifiAwareStateManager.this.onMessageSendFailLocal(sentMessage, reason2);
                    } else {
                        Log.v(WifiAwareStateManager.TAG, "NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL: transactionId=" + ((int) transactionId2) + ", reason=" + reason2 + ": retransmitting - retryCount=" + retryCount);
                        sentMessage.getData().putInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_RETRY_COUNT, retryCount + -1);
                        this.mHostQueuedSendMessages.put(sentMessage.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MESSAGE_ARRIVAL_SEQ), sentMessage);
                    }
                    this.mSendQueueBlocked = false;
                    WifiAwareStateManager.this.transmitNextMessage();
                    return;
                case WifiAwareStateManager.NOTIFICATION_TYPE_ON_DATA_PATH_REQUEST /*{ENCODED_INT: 309}*/:
                    WifiAwareNetworkSpecifier networkSpecifier = WifiAwareStateManager.this.mDataPathMgr.onDataPathRequest(msg.arg2, msg.getData().getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MAC_ADDRESS), ((Integer) msg.obj).intValue(), msg.getData().getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MESSAGE));
                    if (networkSpecifier != null) {
                        WakeupMessage timeout2 = new WakeupMessage(WifiAwareStateManager.this.mContext, getHandler(), WifiAwareStateManager.HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG, 6, 0, 0, networkSpecifier);
                        this.mDataPathConfirmTimeoutMessages.put(networkSpecifier, timeout2);
                        timeout2.schedule(SystemClock.elapsedRealtime() + AWARE_WAIT_FOR_DP_CONFIRM_TIMEOUT);
                        return;
                    }
                    return;
                case WifiAwareStateManager.NOTIFICATION_TYPE_ON_DATA_PATH_CONFIRM /*{ENCODED_INT: 310}*/:
                    WifiAwareNetworkSpecifier networkSpecifier2 = WifiAwareStateManager.this.mDataPathMgr.onDataPathConfirm(msg.arg2, msg.getData().getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MAC_ADDRESS), msg.getData().getBoolean(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SUCCESS_FLAG), msg.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_STATUS_CODE), msg.getData().getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MESSAGE_DATA), (List) msg.obj);
                    if (!(networkSpecifier2 == null || (timeout = this.mDataPathConfirmTimeoutMessages.remove(networkSpecifier2)) == null)) {
                        timeout.cancel();
                    }
                    WifiAwareStateManager.access$3308(WifiAwareStateManager.this);
                    WifiAwareStateManager.this.mSm.sendMessage(WifiAwareStateManager.this.mSm.obtainMessage(3, WifiAwareStateManager.NOTIFICATION_TYPE_AWARE_TRAFFIC_POLL, WifiAwareStateManager.this.mAwareTrafficPollToken));
                    Log.v(WifiAwareStateManager.TAG, "Send NOTIFICATION_TYPE_AWARE_TRAFFIC_POLL");
                    return;
                case WifiAwareStateManager.NOTIFICATION_TYPE_ON_DATA_PATH_END /*{ENCODED_INT: 311}*/:
                    WifiAwareStateManager.this.mDataPathMgr.onDataPathEnd(msg.arg2);
                    Log.v(WifiAwareStateManager.TAG, "onDataPathEnd. getCountDataPath : " + WifiAwareStateManager.this.mDataPathMgr.getCountDataPath());
                    if (WifiAwareStateManager.this.mDataPathMgr.getCountDataPath() == 0) {
                        WifiAwareStateManager.this.resetAwareTrafficPollToken();
                        return;
                    }
                    return;
                case WifiAwareStateManager.NOTIFICATION_TYPE_ON_DATA_PATH_SCHED_UPDATE /*{ENCODED_INT: 312}*/:
                    WifiAwareStateManager.this.mDataPathMgr.onDataPathSchedUpdate(msg.getData().getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MAC_ADDRESS), msg.getData().getIntegerArrayList(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_NDP_IDS), (List) msg.obj);
                    return;
                case WifiAwareStateManager.NOTIFICATION_TYPE_AWARE_TRAFFIC_POLL /*{ENCODED_INT: 313}*/:
                    if (msg.arg2 == WifiAwareStateManager.this.mAwareTrafficPollToken) {
                        WifiAwareStateManager.this.mSm.sendMessageDelayed(WifiAwareStateManager.this.mSm.obtainMessage(3, WifiAwareStateManager.NOTIFICATION_TYPE_AWARE_TRAFFIC_POLL, WifiAwareStateManager.this.mAwareTrafficPollToken), (long) WifiAwareStateManager.this.mPollTrafficIntervalMsecs);
                        WifiAwareStateManager.this.sendAwareTrafficToPoller();
                        return;
                    }
                    return;
                default:
                    Log.wtf(WifiAwareStateManager.TAG, "processNotification: this isn't a NOTIFICATION -- msg=" + msg);
                    return;
            }
        }

        /* JADX INFO: Multiple debug info for r3v18 int: [D('sendMsg' android.os.Message), D('clientId' int)] */
        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private boolean processCommand(Message msg) {
            boolean waitForResponse;
            String extra;
            Log.v(WifiAwareStateManager.TAG, "processCommand: msg=" + msg);
            if (this.mCurrentCommand != null) {
                Log.wtf(WifiAwareStateManager.TAG, "processCommand: receiving a command (msg=" + msg + ") but current (previous) command isn't null (prev_msg=" + this.mCurrentCommand + ")");
                this.mCurrentCommand = null;
            }
            short s = this.mNextTransactionId;
            this.mNextTransactionId = (short) (s + 1);
            this.mCurrentTransactionId = s;
            int i = msg.arg1;
            if (i == WifiAwareStateManager.COMMAND_TYPE_SET_CLUSTER_MERGE_ENABLE) {
                waitForResponse = WifiAwareStateManager.this.setClusterMergingConfigure(this.mCurrentTransactionId, msg.arg2);
            } else if (i != 160) {
                switch (i) {
                    case 100:
                        waitForResponse = WifiAwareStateManager.this.connectLocal(this.mCurrentTransactionId, msg.arg2, msg.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_UID), msg.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_PID), msg.getData().getString(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_CALLING_PACKAGE), (IWifiAwareEventCallback) msg.obj, msg.getData().getParcelable(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_CONFIG), msg.getData().getBoolean(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_NOTIFY_IDENTITY_CHANGE));
                        break;
                    case 101:
                        waitForResponse = WifiAwareStateManager.this.disconnectLocal(this.mCurrentTransactionId, msg.arg2);
                        break;
                    case 102:
                        WifiAwareStateManager.this.terminateSessionLocal(msg.arg2, ((Integer) msg.obj).intValue());
                        waitForResponse = false;
                        break;
                    case 103:
                        waitForResponse = WifiAwareStateManager.this.publishLocal(this.mCurrentTransactionId, msg.arg2, (PublishConfig) msg.getData().getParcelable(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_CONFIG), (IWifiAwareDiscoverySessionCallback) msg.obj);
                        break;
                    case 104:
                        waitForResponse = WifiAwareStateManager.this.updatePublishLocal(this.mCurrentTransactionId, msg.arg2, msg.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SESSION_ID), (PublishConfig) msg.obj);
                        break;
                    case 105:
                        waitForResponse = WifiAwareStateManager.this.subscribeLocal(this.mCurrentTransactionId, msg.arg2, (SubscribeConfig) msg.getData().getParcelable(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_CONFIG), (IWifiAwareDiscoverySessionCallback) msg.obj);
                        break;
                    case 106:
                        waitForResponse = WifiAwareStateManager.this.updateSubscribeLocal(this.mCurrentTransactionId, msg.arg2, msg.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SESSION_ID), (SubscribeConfig) msg.obj);
                        break;
                    case 107:
                        Log.v(WifiAwareStateManager.TAG, "processCommand: ENQUEUE_SEND_MESSAGE - messageId=" + msg.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MESSAGE_ID) + ", mSendArrivalSequenceCounter=" + this.mSendArrivalSequenceCounter);
                        Message sendMsg = obtainMessage(msg.what);
                        sendMsg.copyFrom(msg);
                        sendMsg.getData().putInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MESSAGE_ARRIVAL_SEQ, this.mSendArrivalSequenceCounter);
                        this.mHostQueuedSendMessages.put(this.mSendArrivalSequenceCounter, sendMsg);
                        this.mSendArrivalSequenceCounter = this.mSendArrivalSequenceCounter + 1;
                        waitForResponse = false;
                        if (!this.mSendQueueBlocked) {
                            WifiAwareStateManager.this.transmitNextMessage();
                            break;
                        }
                        break;
                    case WifiAwareStateManager.COMMAND_TYPE_ENABLE_USAGE /*{ENCODED_INT: 108}*/:
                        WifiAwareStateManager.this.enableUsageLocal();
                        waitForResponse = false;
                        break;
                    case WifiAwareStateManager.COMMAND_TYPE_DISABLE_USAGE /*{ENCODED_INT: 109}*/:
                        waitForResponse = WifiAwareStateManager.this.disableUsageLocal(this.mCurrentTransactionId);
                        break;
                    default:
                        switch (i) {
                            case 111:
                                if (WifiAwareStateManager.this.mCapabilities != null) {
                                    Log.v(WifiAwareStateManager.TAG, "COMMAND_TYPE_GET_CAPABILITIES: already have capabilities - skipping");
                                    waitForResponse = false;
                                    break;
                                } else {
                                    waitForResponse = WifiAwareStateManager.this.mWifiAwareNativeApi.getCapabilities(this.mCurrentTransactionId);
                                    break;
                                }
                            case 112:
                                WifiAwareStateManager.this.mDataPathMgr.createAllInterfaces();
                                waitForResponse = false;
                                break;
                            case 113:
                                WifiAwareStateManager.this.mDataPathMgr.deleteAllInterfaces();
                                waitForResponse = false;
                                break;
                            case WifiAwareStateManager.COMMAND_TYPE_CREATE_DATA_PATH_INTERFACE /*{ENCODED_INT: 114}*/:
                                waitForResponse = WifiAwareStateManager.this.mWifiAwareNativeApi.createAwareNetworkInterface(this.mCurrentTransactionId, (String) msg.obj);
                                break;
                            case WifiAwareStateManager.COMMAND_TYPE_DELETE_DATA_PATH_INTERFACE /*{ENCODED_INT: 115}*/:
                                waitForResponse = WifiAwareStateManager.this.mWifiAwareNativeApi.deleteAwareNetworkInterface(this.mCurrentTransactionId, (String) msg.obj);
                                break;
                            case WifiAwareStateManager.COMMAND_TYPE_INITIATE_DATA_PATH_SETUP /*{ENCODED_INT: 116}*/:
                                Bundle data = msg.getData();
                                WifiAwareNetworkSpecifier networkSpecifier = (WifiAwareNetworkSpecifier) msg.obj;
                                waitForResponse = WifiAwareStateManager.this.initiateDataPathSetupLocal(this.mCurrentTransactionId, networkSpecifier, data.getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_PEER_ID), data.getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_CHANNEL_REQ_TYPE), data.getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_CHANNEL), data.getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MAC_ADDRESS), data.getString(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_INTERFACE_NAME), data.getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_PMK), data.getString(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_PASSPHRASE), data.getBoolean(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_OOB), data.getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_APP_INFO));
                                if (waitForResponse) {
                                    WakeupMessage timeout = new WakeupMessage(WifiAwareStateManager.this.mContext, getHandler(), WifiAwareStateManager.HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG, 6, 0, 0, networkSpecifier);
                                    this.mDataPathConfirmTimeoutMessages.put(networkSpecifier, timeout);
                                    timeout.schedule(SystemClock.elapsedRealtime() + AWARE_WAIT_FOR_DP_CONFIRM_TIMEOUT);
                                    break;
                                }
                                break;
                            case 117:
                                Bundle data2 = msg.getData();
                                waitForResponse = WifiAwareStateManager.this.respondToDataPathRequestLocal(this.mCurrentTransactionId, ((Boolean) msg.obj).booleanValue(), msg.arg2, data2.getString(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_INTERFACE_NAME), data2.getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_PMK), data2.getString(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_PASSPHRASE), data2.getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_APP_INFO), data2.getBoolean(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_OOB));
                                break;
                            case WifiAwareStateManager.COMMAND_TYPE_END_DATA_PATH /*{ENCODED_INT: 118}*/:
                                waitForResponse = WifiAwareStateManager.this.endDataPathLocal(this.mCurrentTransactionId, msg.arg2);
                                break;
                            case WifiAwareStateManager.COMMAND_TYPE_TRANSMIT_NEXT_MESSAGE /*{ENCODED_INT: 119}*/:
                                if (!this.mSendQueueBlocked && this.mHostQueuedSendMessages.size() != 0) {
                                    Log.v(WifiAwareStateManager.TAG, "processCommand: SEND_TOP_OF_QUEUE_MESSAGE - sendArrivalSequenceCounter=" + this.mHostQueuedSendMessages.keyAt(0));
                                    Message sendMessage = this.mHostQueuedSendMessages.valueAt(0);
                                    this.mHostQueuedSendMessages.removeAt(0);
                                    Bundle data3 = sendMessage.getData();
                                    int clientId = sendMessage.arg2;
                                    int sessionId = sendMessage.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SESSION_ID);
                                    int peerId = data3.getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MESSAGE_PEER_ID);
                                    byte[] message = data3.getByteArray(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MESSAGE);
                                    int messageId = data3.getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MESSAGE_ID);
                                    msg.getData().putParcelable(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SENT_MESSAGE, sendMessage);
                                    waitForResponse = WifiAwareStateManager.this.sendFollowonMessageLocal(this.mCurrentTransactionId, clientId, sessionId, peerId, message, messageId);
                                    break;
                                } else {
                                    Log.v(WifiAwareStateManager.TAG, "processCommand: SEND_TOP_OF_QUEUE_MESSAGE - blocked or empty host queue");
                                    waitForResponse = false;
                                    break;
                                }
                            case 120:
                                waitForResponse = WifiAwareStateManager.this.reconfigureLocal(this.mCurrentTransactionId);
                                break;
                            case WifiAwareStateManager.COMMAND_TYPE_DELAYED_INITIALIZATION /*{ENCODED_INT: 121}*/:
                                WifiAwareStateManager.this.mWifiAwareNativeManager.start(getHandler());
                                waitForResponse = false;
                                break;
                            case WifiAwareStateManager.COMMAND_TYPE_GET_AWARE /*{ENCODED_INT: 122}*/:
                                WifiAwareStateManager.this.mWifiAwareNativeManager.tryToGetAware();
                                waitForResponse = false;
                                break;
                            case WifiAwareStateManager.COMMAND_TYPE_RELEASE_AWARE /*{ENCODED_INT: 123}*/:
                                WifiAwareStateManager.this.mWifiAwareNativeManager.releaseAware();
                                waitForResponse = false;
                                break;
                            default:
                                waitForResponse = false;
                                Log.wtf(WifiAwareStateManager.TAG, "processCommand: this isn't a COMMAND -- msg=" + msg);
                                break;
                        }
                }
            } else {
                Bundle data4 = msg.getData();
                String loggingData = WifiAwareStateManager.this.buildLoggingDataLocal(data4.getString(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_CALLING_PACKAGE), data4.getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_OPERATION_TYPE), data4.getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_CLIENT_ID), data4.getInt("range_mm"), data4.getString(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SERVICE_NAME), data4.getLong(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_NAN_OP_DURATION));
                if (WifiAwareStateManager.this.mWifiAwareBigData != null) {
                    WifiAwareStateManager.this.mWifiAwareBigData.initialize();
                    if (!WifiAwareStateManager.this.mIsBootComplete || loggingData == null) {
                        Log.e(WifiAwareStateManager.TAG, "failed to build logging data");
                    } else if (WifiAwareStateManager.this.mWifiAwareBigData.parseData("WAOR", loggingData) && (extra = WifiAwareStateManager.this.mWifiAwareBigData.getJsonFormat("WAOR")) != null) {
                        WifiAwareStateManager.this.insertLog("WAOR", extra, -1);
                    }
                }
                waitForResponse = false;
            }
            if (!waitForResponse) {
                this.mCurrentTransactionId = 0;
            } else {
                this.mCurrentCommand = obtainMessage(msg.what);
                this.mCurrentCommand.copyFrom(msg);
            }
            return waitForResponse;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void processResponse(Message msg) {
            Log.v(WifiAwareStateManager.TAG, "processResponse: msg=" + msg);
            if (this.mCurrentCommand == null) {
                Log.wtf(WifiAwareStateManager.TAG, "processResponse: no existing command stored!? msg=" + msg);
                this.mCurrentTransactionId = 0;
                return;
            }
            switch (msg.arg1) {
                case 200:
                    WifiAwareStateManager.this.onConfigCompletedLocal(this.mCurrentCommand);
                    break;
                case 201:
                    WifiAwareStateManager.this.onConfigFailedLocal(this.mCurrentCommand, ((Integer) msg.obj).intValue());
                    break;
                case 202:
                    WifiAwareStateManager.this.onSessionConfigSuccessLocal(this.mCurrentCommand, ((Byte) msg.obj).byteValue(), msg.getData().getBoolean(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SESSION_TYPE));
                    break;
                case 203:
                    int reason = ((Integer) msg.obj).intValue();
                    WifiAwareStateManager.this.onSessionConfigFailLocal(this.mCurrentCommand, msg.getData().getBoolean(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SESSION_TYPE), reason);
                    break;
                case WifiAwareStateManager.RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_SUCCESS /*{ENCODED_INT: 204}*/:
                    Message sentMessage = (Message) this.mCurrentCommand.getData().getParcelable(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SENT_MESSAGE);
                    if (sentMessage == null) {
                        Log.v(WifiAwareStateManager.TAG, "processResponse: ON_MESSAGE_SEND_QUEUED_SUCCESS - but sentMessage is null");
                        break;
                    } else {
                        sentMessage.getData().putLong(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SEND_MESSAGE_ENQUEUE_TIME, SystemClock.elapsedRealtime());
                        this.mFwQueuedSendMessages.put(Short.valueOf(this.mCurrentTransactionId), sentMessage);
                        updateSendMessageTimeout();
                        if (!this.mSendQueueBlocked) {
                            WifiAwareStateManager.this.transmitNextMessage();
                        }
                        Log.v(WifiAwareStateManager.TAG, "processResponse: ON_MESSAGE_SEND_QUEUED_SUCCESS - arrivalSeq=" + sentMessage.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MESSAGE_ARRIVAL_SEQ));
                        break;
                    }
                case WifiAwareStateManager.RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_FAIL /*{ENCODED_INT: 205}*/:
                    Log.v(WifiAwareStateManager.TAG, "processResponse: ON_MESSAGE_SEND_QUEUED_FAIL - blocking!");
                    if (((Integer) msg.obj).intValue() != 11) {
                        WifiAwareStateManager.this.onMessageSendFailLocal((Message) this.mCurrentCommand.getData().getParcelable(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SENT_MESSAGE), 1);
                        if (!this.mSendQueueBlocked) {
                            WifiAwareStateManager.this.transmitNextMessage();
                            break;
                        }
                    } else {
                        Message sentMessage2 = (Message) this.mCurrentCommand.getData().getParcelable(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SENT_MESSAGE);
                        int arrivalSeq = sentMessage2.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_MESSAGE_ARRIVAL_SEQ);
                        this.mHostQueuedSendMessages.put(arrivalSeq, sentMessage2);
                        this.mSendQueueBlocked = true;
                        Log.v(WifiAwareStateManager.TAG, "processResponse: ON_MESSAGE_SEND_QUEUED_FAIL - arrivalSeq=" + arrivalSeq + " -- blocking");
                        break;
                    }
                    break;
                case WifiAwareStateManager.RESPONSE_TYPE_ON_CAPABILITIES_UPDATED /*{ENCODED_INT: 206}*/:
                    WifiAwareStateManager.this.onCapabilitiesUpdatedResponseLocal((Capabilities) msg.obj);
                    break;
                case WifiAwareStateManager.RESPONSE_TYPE_ON_CREATE_INTERFACE /*{ENCODED_INT: 207}*/:
                    WifiAwareStateManager.this.onCreateDataPathInterfaceResponseLocal(this.mCurrentCommand, msg.getData().getBoolean(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SUCCESS_FLAG), msg.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_STATUS_CODE));
                    break;
                case WifiAwareStateManager.RESPONSE_TYPE_ON_DELETE_INTERFACE /*{ENCODED_INT: 208}*/:
                    WifiAwareStateManager.this.onDeleteDataPathInterfaceResponseLocal(this.mCurrentCommand, msg.getData().getBoolean(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SUCCESS_FLAG), msg.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_STATUS_CODE));
                    break;
                case WifiAwareStateManager.RESPONSE_TYPE_ON_INITIATE_DATA_PATH_SUCCESS /*{ENCODED_INT: 209}*/:
                    WifiAwareStateManager.this.onInitiateDataPathResponseSuccessLocal(this.mCurrentCommand, ((Integer) msg.obj).intValue());
                    break;
                case WifiAwareStateManager.RESPONSE_TYPE_ON_INITIATE_DATA_PATH_FAIL /*{ENCODED_INT: 210}*/:
                    WifiAwareStateManager.this.onInitiateDataPathResponseFailLocal(this.mCurrentCommand, ((Integer) msg.obj).intValue());
                    break;
                case WifiAwareStateManager.RESPONSE_TYPE_ON_RESPOND_TO_DATA_PATH_SETUP_REQUEST /*{ENCODED_INT: 211}*/:
                    WifiAwareStateManager.this.onRespondToDataPathSetupRequestResponseLocal(this.mCurrentCommand, msg.getData().getBoolean(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SUCCESS_FLAG), msg.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_STATUS_CODE));
                    break;
                case WifiAwareStateManager.RESPONSE_TYPE_ON_END_DATA_PATH /*{ENCODED_INT: 212}*/:
                    WifiAwareStateManager.this.onEndPathEndResponseLocal(this.mCurrentCommand, msg.getData().getBoolean(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SUCCESS_FLAG), msg.getData().getInt(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_STATUS_CODE));
                    break;
                case WifiAwareStateManager.RESPONSE_TYPE_ON_DISABLE /*{ENCODED_INT: 213}*/:
                    WifiAwareStateManager.this.onDisableResponseLocal(this.mCurrentCommand, ((Integer) msg.obj).intValue());
                    break;
                default:
                    Log.wtf(WifiAwareStateManager.TAG, "processResponse: this isn't a RESPONSE -- msg=" + msg);
                    this.mCurrentCommand = null;
                    this.mCurrentTransactionId = 0;
                    return;
            }
            this.mCurrentCommand = null;
            this.mCurrentTransactionId = 0;
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void processTimeout(Message msg) {
            if (WifiAwareStateManager.this.mDbg) {
                Log.v(WifiAwareStateManager.TAG, "processTimeout: msg=" + msg);
            }
            if (this.mCurrentCommand == null) {
                Log.wtf(WifiAwareStateManager.TAG, "processTimeout: no existing command stored!? msg=" + msg);
                this.mCurrentTransactionId = 0;
                return;
            }
            switch (msg.arg1) {
                case 100:
                    WifiAwareStateManager.this.onConfigFailedLocal(this.mCurrentCommand, 1);
                    break;
                case 101:
                    WifiAwareStateManager.this.onConfigFailedLocal(this.mCurrentCommand, 1);
                    break;
                case 102:
                    Log.wtf(WifiAwareStateManager.TAG, "processTimeout: TERMINATE_SESSION - shouldn't be waiting!");
                    break;
                case 103:
                    WifiAwareStateManager.this.onSessionConfigFailLocal(this.mCurrentCommand, true, 1);
                    break;
                case 104:
                    WifiAwareStateManager.this.onSessionConfigFailLocal(this.mCurrentCommand, true, 1);
                    break;
                case 105:
                    WifiAwareStateManager.this.onSessionConfigFailLocal(this.mCurrentCommand, false, 1);
                    break;
                case 106:
                    WifiAwareStateManager.this.onSessionConfigFailLocal(this.mCurrentCommand, false, 1);
                    break;
                case 107:
                    Log.wtf(WifiAwareStateManager.TAG, "processTimeout: ENQUEUE_SEND_MESSAGE - shouldn't be waiting!");
                    break;
                case WifiAwareStateManager.COMMAND_TYPE_ENABLE_USAGE /*{ENCODED_INT: 108}*/:
                    Log.wtf(WifiAwareStateManager.TAG, "processTimeout: ENABLE_USAGE - shouldn't be waiting!");
                    break;
                case WifiAwareStateManager.COMMAND_TYPE_DISABLE_USAGE /*{ENCODED_INT: 109}*/:
                    Log.wtf(WifiAwareStateManager.TAG, "processTimeout: DISABLE_USAGE - shouldn't be waiting!");
                    break;
                case SoapEnvelope.VER11 /*{ENCODED_INT: 110}*/:
                default:
                    Log.wtf(WifiAwareStateManager.TAG, "processTimeout: this isn't a COMMAND -- msg=" + msg);
                    break;
                case 111:
                    Log.e(WifiAwareStateManager.TAG, "processTimeout: GET_CAPABILITIES timed-out - strange, will try again when next enabled!?");
                    break;
                case 112:
                    Log.wtf(WifiAwareStateManager.TAG, "processTimeout: CREATE_ALL_DATA_PATH_INTERFACES - shouldn't be waiting!");
                    break;
                case 113:
                    Log.wtf(WifiAwareStateManager.TAG, "processTimeout: DELETE_ALL_DATA_PATH_INTERFACES - shouldn't be waiting!");
                    break;
                case WifiAwareStateManager.COMMAND_TYPE_CREATE_DATA_PATH_INTERFACE /*{ENCODED_INT: 114}*/:
                    WifiAwareStateManager.this.onCreateDataPathInterfaceResponseLocal(this.mCurrentCommand, false, 0);
                    break;
                case WifiAwareStateManager.COMMAND_TYPE_DELETE_DATA_PATH_INTERFACE /*{ENCODED_INT: 115}*/:
                    WifiAwareStateManager.this.onDeleteDataPathInterfaceResponseLocal(this.mCurrentCommand, false, 0);
                    break;
                case WifiAwareStateManager.COMMAND_TYPE_INITIATE_DATA_PATH_SETUP /*{ENCODED_INT: 116}*/:
                    WifiAwareStateManager.this.onInitiateDataPathResponseFailLocal(this.mCurrentCommand, 0);
                    break;
                case 117:
                    WifiAwareStateManager.this.onRespondToDataPathSetupRequestResponseLocal(this.mCurrentCommand, false, 0);
                    break;
                case WifiAwareStateManager.COMMAND_TYPE_END_DATA_PATH /*{ENCODED_INT: 118}*/:
                    WifiAwareStateManager.this.onEndPathEndResponseLocal(this.mCurrentCommand, false, 0);
                    break;
                case WifiAwareStateManager.COMMAND_TYPE_TRANSMIT_NEXT_MESSAGE /*{ENCODED_INT: 119}*/:
                    WifiAwareStateManager.this.onMessageSendFailLocal((Message) this.mCurrentCommand.getData().getParcelable(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SENT_MESSAGE), 1);
                    this.mSendQueueBlocked = false;
                    WifiAwareStateManager.this.transmitNextMessage();
                    break;
                case 120:
                    WifiAwareStateManager.this.onConfigFailedLocal(this.mCurrentCommand, 1);
                    break;
                case WifiAwareStateManager.COMMAND_TYPE_DELAYED_INITIALIZATION /*{ENCODED_INT: 121}*/:
                    Log.wtf(WifiAwareStateManager.TAG, "processTimeout: COMMAND_TYPE_DELAYED_INITIALIZATION - shouldn't be waiting!");
                    break;
                case WifiAwareStateManager.COMMAND_TYPE_GET_AWARE /*{ENCODED_INT: 122}*/:
                    Log.wtf(WifiAwareStateManager.TAG, "processTimeout: COMMAND_TYPE_GET_AWARE - shouldn't be waiting!");
                    break;
                case WifiAwareStateManager.COMMAND_TYPE_RELEASE_AWARE /*{ENCODED_INT: 123}*/:
                    Log.wtf(WifiAwareStateManager.TAG, "processTimeout: COMMAND_TYPE_RELEASE_AWARE - shouldn't be waiting!");
                    break;
            }
            this.mCurrentCommand = null;
            this.mCurrentTransactionId = 0;
        }

        private void updateSendMessageTimeout() {
            Log.v(WifiAwareStateManager.TAG, "updateSendMessageTimeout: mHostQueuedSendMessages.size()=" + this.mHostQueuedSendMessages.size() + ", mFwQueuedSendMessages.size()=" + this.mFwQueuedSendMessages.size() + ", mSendQueueBlocked=" + this.mSendQueueBlocked);
            Iterator<Message> it = this.mFwQueuedSendMessages.values().iterator();
            if (it.hasNext()) {
                this.mSendMessageTimeoutMessage.schedule(it.next().getData().getLong(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SEND_MESSAGE_ENQUEUE_TIME) + 10000);
            } else {
                this.mSendMessageTimeoutMessage.cancel();
            }
        }

        /* access modifiers changed from: private */
        /* access modifiers changed from: public */
        private void processSendMessageTimeout() {
            if (WifiAwareStateManager.this.mDbg) {
                Log.v(WifiAwareStateManager.TAG, "processSendMessageTimeout: mHostQueuedSendMessages.size()=" + this.mHostQueuedSendMessages.size() + ", mFwQueuedSendMessages.size()=" + this.mFwQueuedSendMessages.size() + ", mSendQueueBlocked=" + this.mSendQueueBlocked);
            }
            boolean first = true;
            long currentTime = SystemClock.elapsedRealtime();
            Iterator<Map.Entry<Short, Message>> it = this.mFwQueuedSendMessages.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Short, Message> entry = it.next();
                short transactionId = entry.getKey().shortValue();
                Message message = entry.getValue();
                long messageEnqueueTime = message.getData().getLong(WifiAwareStateManager.MESSAGE_BUNDLE_KEY_SEND_MESSAGE_ENQUEUE_TIME);
                if (!first && 10000 + messageEnqueueTime > currentTime) {
                    break;
                }
                if (WifiAwareStateManager.this.mDbg) {
                    Log.v(WifiAwareStateManager.TAG, "processSendMessageTimeout: expiring - transactionId=" + ((int) transactionId) + ", message=" + message + ", due to messageEnqueueTime=" + messageEnqueueTime + ", currentTime=" + currentTime);
                }
                WifiAwareStateManager.this.onMessageSendFailLocal(message, 1);
                it.remove();
                first = false;
            }
            updateSendMessageTimeout();
            this.mSendQueueBlocked = false;
            WifiAwareStateManager.this.transmitNextMessage();
        }

        /* access modifiers changed from: protected */
        public String getLogRecString(Message msg) {
            StringBuilder sb = new StringBuilder(WifiAwareStateManager.messageToString(msg));
            if (msg.what == 1 && this.mCurrentTransactionId != 0) {
                sb.append(" (Transaction ID=");
                sb.append((int) this.mCurrentTransactionId);
                sb.append(")");
            }
            return sb.toString();
        }

        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            pw.println("WifiAwareStateMachine:");
            pw.println("  mNextTransactionId: " + ((int) this.mNextTransactionId));
            pw.println("  mNextSessionId: " + this.mNextSessionId);
            pw.println("  mCurrentCommand: " + this.mCurrentCommand);
            pw.println("  mCurrentTransaction: " + ((int) this.mCurrentTransactionId));
            pw.println("  mSendQueueBlocked: " + this.mSendQueueBlocked);
            pw.println("  mSendArrivalSequenceCounter: " + this.mSendArrivalSequenceCounter);
            pw.println("  mHostQueuedSendMessages: [" + this.mHostQueuedSendMessages + "]");
            pw.println("  mFwQueuedSendMessages: [" + this.mFwQueuedSendMessages + "]");
            WifiAwareStateManager.super.dump(fd, pw, args);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendAwareStateChangedBroadcast(boolean enabled) {
        Log.v(TAG, "sendAwareStateChangedBroadcast: enabled=" + enabled);
        Intent intent = new Intent("android.net.wifi.aware.action.WIFI_AWARE_STATE_CHANGED");
        intent.addFlags(1073741824);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean connectLocal(short transactionId, int clientId, int uid, int pid, String callingPackage, IWifiAwareEventCallback callback, ConfigRequest configRequest, boolean notifyIdentityChange) {
        ConfigRequest merged;
        boolean z;
        boolean z2;
        Log.v(TAG, "connectLocal(): transactionId=" + ((int) transactionId) + ", clientId=" + clientId + ", uid=" + uid + ", pid=" + pid + ", callingPackage=" + callingPackage + ", callback=" + callback + ", configRequest=" + configRequest + ", notifyIdentityChange=" + notifyIdentityChange);
        if (this.msgBackup != null) {
            this.msgBackup = null;
        }
        if ((this.mUsageEnabled || allowLocationWhiteList(callingPackage)) && !this.mSelectedCancel) {
            this.mSelectedCancel = false;
            if (this.mClients.get(clientId) != null) {
                Log.e(TAG, "connectLocal: entry already exists for clientId=" + clientId);
            }
            Log.v(TAG, "mCurrentAwareConfiguration=" + this.mCurrentAwareConfiguration + ", mCurrentIdentityNotification=" + this.mCurrentIdentityNotification);
            ConfigRequest merged2 = mergeConfigRequests(configRequest);
            if (merged2 == null) {
                Log.e(TAG, "connectLocal: requested configRequest=" + configRequest + ", incompatible with current configurations");
                try {
                    callback.onConnectFail(1);
                    this.mAwareMetrics.recordAttachStatus(1);
                } catch (RemoteException e) {
                    Log.w(TAG, "connectLocal onConnectFail(): RemoteException (FYI): " + e);
                }
                return false;
            }
            Log.v(TAG, "connectLocal: merged=" + merged2);
            ConfigRequest configRequest2 = this.mCurrentAwareConfiguration;
            if (configRequest2 == null || !configRequest2.equals(merged2)) {
                merged = merged2;
                z = false;
                z2 = notifyIdentityChange;
            } else if (this.mCurrentIdentityNotification || !notifyIdentityChange) {
                try {
                    callback.onConnectSuccess(clientId);
                } catch (RemoteException e2) {
                    Log.w(TAG, "connectLocal onConnectSuccess(): RemoteException (FYI): " + e2);
                }
                WifiAwareClientState client = new WifiAwareClientState(this.mContext, clientId, uid, pid, callingPackage, callback, configRequest, notifyIdentityChange, SystemClock.elapsedRealtime(), this.mWifiPermissionsUtil);
                client.mDbg = this.mDbg;
                client.onInterfaceAddressChange(this.mCurrentDiscoveryInterfaceMac);
                this.mClients.append(clientId, client);
                setLocationWhiteList(clientId, callingPackage);
                this.mAwareMetrics.recordAttachSession(uid, notifyIdentityChange, this.mClients);
                return false;
            } else {
                merged = merged2;
                z = false;
                z2 = notifyIdentityChange;
            }
            boolean notificationRequired = (doesAnyClientNeedIdentityChangeNotifications() || z2) ? true : z;
            if (this.mCurrentAwareConfiguration == null) {
                this.mAttachIsRunning = true;
                if (this.mWifiP2pManager.isSetupInterfaceRunning()) {
                    int cnt = 50;
                    Log.v(TAG, "connectLocal: mAttachIsRunning: " + this.mAttachIsRunning + " isSetupInterfaceRunning: true : wait max cnt: " + 50);
                    while (true) {
                        int cnt2 = cnt - 1;
                        if (cnt <= 0) {
                            break;
                        }
                        try {
                            Thread.sleep(10);
                            if (!this.mWifiP2pManager.isSetupInterfaceRunning()) {
                                break;
                            }
                            cnt = cnt2;
                        } catch (InterruptedException e3) {
                            e3.printStackTrace();
                        }
                    }
                }
                this.mWifiAwareNativeManager.tryToGetAware();
            }
            boolean success = this.mWifiAwareNativeApi.enableAndConfigure(transactionId, merged, notificationRequired, this.mCurrentAwareConfiguration == null ? true : z, this.mPowerManager.isInteractive(), this.mPowerManager.isDeviceIdleMode());
            if (!success) {
                try {
                    this.mAttachIsRunning = z;
                    try {
                        callback.onConnectFail(1);
                        this.mAwareMetrics.recordAttachStatus(1);
                    } catch (RemoteException e4) {
                        e = e4;
                    }
                } catch (RemoteException e5) {
                    e = e5;
                    Log.w(TAG, "connectLocal onConnectFail(): RemoteException (FYI):  " + e);
                    return success;
                }
            }
            return success;
        }
        if (this.mSelectedCancel) {
            this.mSelectedCancel = false;
            Log.w(TAG, "connect(): cancel is selected in enableNAN popup");
        } else {
            Log.w(TAG, "connect(): called with mUsageEnabled=false");
        }
        try {
            callback.onConnectFail(1);
            this.mAwareMetrics.recordAttachStatus(1);
        } catch (RemoteException e6) {
            Log.w(TAG, "connectLocal onConnectFail(): RemoteException (FYI): " + e6);
        }
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean disconnectLocal(short transactionId, int clientId) {
        ArrayList<Integer> arrayList;
        Log.v(TAG, "disconnectLocal(): transactionId=" + ((int) transactionId) + ", clientId=" + clientId);
        WifiAwareClientState client = this.mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "disconnectLocal: no entry for clientId=" + clientId);
            sendAwareBigdata(null, 6, clientId, -1, "", -1);
            return false;
        }
        this.mClients.delete(clientId);
        String callingPackage = client.getCallingPackage();
        if (callingPackage != null && callingPackage.equals("com.samsung.android.mcfserver")) {
            this.mSettableParameters.put(PARAM_ON_IDLE_DISABLE_AWARE, 1);
            if (this.mPowerManager.isDeviceIdleMode()) {
                disableUsage();
            }
        }
        this.mAwareMetrics.recordAttachSessionDuration(client.getCreationTime());
        sendAwareBigdata(callingPackage, 6, clientId, -1, "", (SystemClock.elapsedRealtime() - client.getCreationTime()) / 1000);
        if (this.mDataPathMgr.getCountDataPath() > 0) {
            this.mDisconnectedCallingPackage.put(Integer.valueOf(clientId), callingPackage);
        }
        SparseArray<WifiAwareDiscoverySessionState> sessions = client.getSessions();
        for (int i = 0; i < sessions.size(); i++) {
            this.mAwareMetrics.recordDiscoverySessionDuration(sessions.valueAt(i).getCreationTime(), sessions.valueAt(i).isPublishSession());
        }
        client.destroy();
        if (this.mClients.size() == 0) {
            this.mCurrentAwareConfiguration = null;
            this.mSm.onAwareDownCleanupSendQueueState();
            deleteAllDataPathInterfaces();
            unsetAllLocationWhiteList();
            this.mAttachIsRunning = false;
            return this.mWifiAwareNativeApi.disable(transactionId);
        }
        if (unsetLocationWhiteList(clientId) && (arrayList = this.mSavedClientId) != null && arrayList.size() == 0 && !this.mWifiPermissionsUtil.isLocationModeEnabled()) {
            Log.d(TAG, "mSavedClientId is zero and location is off. so call disableUsage()");
            this.mRequestDisable = true;
            disableUsage();
        }
        ConfigRequest merged = mergeConfigRequests(null);
        if (merged == null) {
            Log.wtf(TAG, "disconnectLocal: got an incompatible merge on remaining configs!?");
            return false;
        }
        boolean notificationReqs = doesAnyClientNeedIdentityChangeNotifications();
        if (!merged.equals(this.mCurrentAwareConfiguration) || this.mCurrentIdentityNotification != notificationReqs) {
            return this.mWifiAwareNativeApi.enableAndConfigure(transactionId, merged, notificationReqs, false, this.mPowerManager.isInteractive(), this.mPowerManager.isDeviceIdleMode());
        }
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean reconfigureLocal(short transactionId) {
        Log.v(TAG, "reconfigureLocal(): transactionId=" + ((int) transactionId));
        if (this.mClients.size() == 0) {
            return false;
        }
        return this.mWifiAwareNativeApi.enableAndConfigure(transactionId, this.mCurrentAwareConfiguration, doesAnyClientNeedIdentityChangeNotifications(), false, this.mPowerManager.isInteractive(), this.mPowerManager.isDeviceIdleMode());
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean setClusterMergingConfigure(short transactionId, int enable) {
        Log.v(TAG, "enableClusterMerge(): transactionId=" + ((int) transactionId) + " enable=" + enable);
        if (this.mClients.size() == 0) {
            return false;
        }
        return this.mWifiAwareNativeApi.enableClusterMergeConfigure(transactionId, this.mCurrentAwareConfiguration, doesAnyClientNeedIdentityChangeNotifications(), false, this.mPowerManager.isInteractive(), this.mPowerManager.isDeviceIdleMode(), enable);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void terminateSessionLocal(int clientId, int sessionId) {
        Log.v(TAG, "terminateSessionLocal(): clientId=" + clientId + ", sessionId=" + sessionId);
        WifiAwareClientState client = this.mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "terminateSession: no client exists for clientId=" + clientId);
            return;
        }
        WifiAwareDiscoverySessionState session = client.terminateSession(sessionId);
        if (session != null) {
            this.mAwareMetrics.recordDiscoverySessionDuration(session.getCreationTime(), session.isPublishSession());
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean publishLocal(short transactionId, int clientId, PublishConfig publishConfig, IWifiAwareDiscoverySessionCallback callback) {
        Log.v(TAG, "publishLocal(): transactionId=" + ((int) transactionId) + ", clientId=" + clientId + ", publishConfig=" + publishConfig + ", callback=" + callback);
        WifiAwareClientState client = this.mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "publishLocal: no client exists for clientId=" + clientId);
            return false;
        }
        boolean success = this.mWifiAwareNativeApi.publish(transactionId, (byte) 0, publishConfig);
        if (!success) {
            try {
                callback.onSessionConfigFail(1);
            } catch (RemoteException e) {
                Log.w(TAG, "publishLocal onSessionConfigFail(): RemoteException (FYI): " + e);
            }
            this.mAwareMetrics.recordDiscoveryStatus(client.getUid(), 1, true);
        }
        return success;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean updatePublishLocal(short transactionId, int clientId, int sessionId, PublishConfig publishConfig) {
        Log.v(TAG, "updatePublishLocal(): transactionId=" + ((int) transactionId) + ", clientId=" + clientId + ", sessionId=" + sessionId + ", publishConfig=" + publishConfig);
        WifiAwareClientState client = this.mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "updatePublishLocal: no client exists for clientId=" + clientId);
            return false;
        }
        WifiAwareDiscoverySessionState session = client.getSession(sessionId);
        if (session == null) {
            Log.e(TAG, "updatePublishLocal: no session exists for clientId=" + clientId + ", sessionId=" + sessionId);
            return false;
        }
        boolean status = session.updatePublish(transactionId, publishConfig);
        if (!status) {
            this.mAwareMetrics.recordDiscoveryStatus(client.getUid(), 1, true);
        }
        return status;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean subscribeLocal(short transactionId, int clientId, SubscribeConfig subscribeConfig, IWifiAwareDiscoverySessionCallback callback) {
        Log.v(TAG, "subscribeLocal(): transactionId=" + ((int) transactionId) + ", clientId=" + clientId + ", subscribeConfig=" + subscribeConfig + ", callback=" + callback);
        WifiAwareClientState client = this.mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "subscribeLocal: no client exists for clientId=" + clientId);
            return false;
        }
        boolean success = this.mWifiAwareNativeApi.subscribe(transactionId, (byte) 0, subscribeConfig);
        if (!success) {
            try {
                callback.onSessionConfigFail(1);
            } catch (RemoteException e) {
                Log.w(TAG, "subscribeLocal onSessionConfigFail(): RemoteException (FYI): " + e);
            }
            this.mAwareMetrics.recordDiscoveryStatus(client.getUid(), 1, false);
        }
        return success;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean updateSubscribeLocal(short transactionId, int clientId, int sessionId, SubscribeConfig subscribeConfig) {
        Log.v(TAG, "updateSubscribeLocal(): transactionId=" + ((int) transactionId) + ", clientId=" + clientId + ", sessionId=" + sessionId + ", subscribeConfig=" + subscribeConfig);
        WifiAwareClientState client = this.mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "updateSubscribeLocal: no client exists for clientId=" + clientId);
            return false;
        }
        WifiAwareDiscoverySessionState session = client.getSession(sessionId);
        if (session == null) {
            Log.e(TAG, "updateSubscribeLocal: no session exists for clientId=" + clientId + ", sessionId=" + sessionId);
            return false;
        }
        boolean status = session.updateSubscribe(transactionId, subscribeConfig);
        if (!status) {
            this.mAwareMetrics.recordDiscoveryStatus(client.getUid(), 1, false);
        }
        return status;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean sendFollowonMessageLocal(short transactionId, int clientId, int sessionId, int peerId, byte[] message, int messageId) {
        Log.v(TAG, "sendFollowonMessageLocal(): transactionId=" + ((int) transactionId) + ", clientId=" + clientId + ", sessionId=" + sessionId + ", peerId=" + peerId + ", messageId=" + messageId);
        WifiAwareClientState client = this.mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "sendFollowonMessageLocal: no client exists for clientId=" + clientId);
            return false;
        }
        WifiAwareDiscoverySessionState session = client.getSession(sessionId);
        if (session != null) {
            return session.sendMessage(transactionId, peerId, message, messageId);
        }
        Log.e(TAG, "sendFollowonMessageLocal: no session exists for clientId=" + clientId + ", sessionId=" + sessionId);
        return false;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void enableUsageLocal() {
        Log.v(TAG, "enableUsageLocal: mUsageEnabled=" + this.mUsageEnabled);
        if (!this.mUsageEnabled) {
            this.mUsageEnabled = true;
            sendAwareStateChangedBroadcast(true);
            this.mAwareMetrics.recordEnableUsage();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean disableUsageLocal(short transactionId) {
        Log.v(TAG, "disableUsageLocal: transactionId=" + ((int) transactionId) + ", mUsageEnabled=" + this.mUsageEnabled);
        ArrayList<Integer> arrayList = this.mSavedClientId;
        if ((arrayList != null && arrayList.size() > 0 && !this.mWifiPermissionsUtil.isLocationModeEnabled()) || this.mRequestDisable) {
            Log.v(TAG, "disableUsageLocal: because this is LocationWhiteList, mUsageEnabled is false. so disableUsageLocal will be executed except location case");
            this.mRequestDisable = false;
        } else if (!this.mUsageEnabled) {
            return false;
        }
        onAwareDownLocal();
        boolean callDispatched = this.mWifiAwareNativeApi.disable(transactionId);
        if ((this.mSettableParameters.get(PARAM_ON_IDLE_DISABLE_AWARE).intValue() == 0 || !this.mPowerManager.isDeviceIdleMode()) && this.mWifiPermissionsUtil.isLocationModeEnabled() && this.mWifiManager.getWifiState() == 3) {
            Log.d(TAG, "disableUsageLocal() - set true to mUsageEnabled : (no idle mode, location enabled, wifi enabled)");
            this.mUsageEnabled = true;
        } else {
            this.mUsageEnabled = false;
            sendAwareStateChangedBroadcast(false);
            this.mAwareMetrics.recordDisableUsage();
        }
        return callDispatched;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean initiateDataPathSetupLocal(short transactionId, WifiAwareNetworkSpecifier networkSpecifier, int peerId, int channelRequestType, int channel, byte[] peer, String interfaceName, byte[] pmk, String passphrase, boolean isOutOfBand, byte[] appInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("initiateDataPathSetupLocal(): transactionId=");
        sb.append((int) transactionId);
        sb.append(", networkSpecifier=");
        sb.append(networkSpecifier);
        sb.append(", peerId=");
        sb.append(peerId);
        sb.append(", channelRequestType=");
        sb.append(channelRequestType);
        sb.append(", channel=");
        sb.append(channel);
        sb.append(", peer=");
        sb.append(String.valueOf(HexEncoding.encode(peer)));
        sb.append(", interfaceName=");
        sb.append(interfaceName);
        sb.append(", pmk=");
        String str = "";
        sb.append(pmk == null ? str : "*");
        sb.append(", passphrase=");
        if (passphrase != null) {
            str = "*";
        }
        sb.append(str);
        sb.append(", isOutOfBand=");
        sb.append(isOutOfBand);
        sb.append(", appInfo=");
        sb.append(appInfo == null ? "<null>" : "<non-null>");
        Log.v(TAG, sb.toString());
        boolean success = this.mWifiAwareNativeApi.initiateDataPath(transactionId, peerId, channelRequestType, channel, peer, interfaceName, pmk, passphrase, isOutOfBand, appInfo, this.mCapabilities);
        if (!success) {
            this.mDataPathMgr.onDataPathInitiateFail(networkSpecifier, 1);
        }
        return success;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean respondToDataPathRequestLocal(short transactionId, boolean accept, int ndpId, String interfaceName, byte[] pmk, String passphrase, byte[] appInfo, boolean isOutOfBand) {
        String str;
        StringBuilder sb = new StringBuilder();
        sb.append("respondToDataPathRequestLocal(): transactionId=");
        sb.append((int) transactionId);
        sb.append(", accept=");
        sb.append(accept);
        sb.append(", ndpId=");
        sb.append(ndpId);
        sb.append(", interfaceName=");
        sb.append(interfaceName);
        sb.append(", pmk=");
        String str2 = "";
        sb.append(pmk == null ? str2 : "*");
        sb.append(", passphrase=");
        if (passphrase != null) {
            str2 = "*";
        }
        sb.append(str2);
        sb.append(", isOutOfBand=");
        sb.append(isOutOfBand);
        sb.append(", appInfo=");
        if (appInfo == null) {
            str = "<null>";
        } else {
            str = "<non-null>";
        }
        sb.append(str);
        Log.v(TAG, sb.toString());
        boolean success = this.mWifiAwareNativeApi.respondToDataPathRequest(transactionId, accept, ndpId, interfaceName, pmk, passphrase, appInfo, isOutOfBand, this.mCapabilities);
        if (!success) {
            this.mDataPathMgr.onRespondToDataPathRequest(ndpId, false, 1);
        }
        return success;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private boolean endDataPathLocal(short transactionId, int ndpId) {
        Log.v(TAG, "endDataPathLocal: transactionId=" + ((int) transactionId) + ", ndpId=" + ndpId);
        return this.mWifiAwareNativeApi.endDataPath(transactionId, ndpId);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onConfigCompletedLocal(Message completedCommand) {
        Log.v(TAG, "onConfigCompleted: completedCommand=" + completedCommand);
        if (completedCommand.arg1 == 100) {
            Bundle data = completedCommand.getData();
            int clientId = completedCommand.arg2;
            IWifiAwareEventCallback callback = (IWifiAwareEventCallback) completedCommand.obj;
            int uid = data.getInt(MESSAGE_BUNDLE_KEY_UID);
            int pid = data.getInt(MESSAGE_BUNDLE_KEY_PID);
            boolean notifyIdentityChange = data.getBoolean(MESSAGE_BUNDLE_KEY_NOTIFY_IDENTITY_CHANGE);
            String callingPackage = data.getString(MESSAGE_BUNDLE_KEY_CALLING_PACKAGE);
            WifiAwareClientState client = new WifiAwareClientState(this.mContext, clientId, uid, pid, callingPackage, callback, data.getParcelable(MESSAGE_BUNDLE_KEY_CONFIG), notifyIdentityChange, SystemClock.elapsedRealtime(), this.mWifiPermissionsUtil);
            client.mDbg = this.mDbg;
            this.mClients.put(clientId, client);
            setLocationWhiteList(clientId, callingPackage);
            this.mAwareMetrics.recordAttachSession(uid, notifyIdentityChange, this.mClients);
            sendAwareBigdata(callingPackage, 1, clientId, -1, "", -1);
            if (this.mCurrentAwareConfiguration == null) {
                this.mDataPathMgr.createAllInterfaces();
            }
            try {
                this.mAttachIsRunning = false;
                callback.onConnectSuccess(clientId);
            } catch (RemoteException e) {
                Log.w(TAG, "onConfigCompletedLocal onConnectSuccess(): RemoteException (FYI): " + e);
            }
            client.onInterfaceAddressChange(this.mCurrentDiscoveryInterfaceMac);
        } else if (completedCommand.arg1 == 101) {
            if (this.mCurrentAwareConfiguration == null) {
                createAllDataPathInterfaces();
            }
        } else if (completedCommand.arg1 != 120) {
            Log.wtf(TAG, "onConfigCompletedLocal: unexpected completedCommand=" + completedCommand);
            return;
        } else if (this.mCurrentAwareConfiguration == null) {
            createAllDataPathInterfaces();
        }
        this.mCurrentAwareConfiguration = mergeConfigRequests(null);
        if (this.mCurrentAwareConfiguration == null) {
            Log.wtf(TAG, "onConfigCompletedLocal: got a null merged configuration after config!?");
        }
        this.mCurrentIdentityNotification = doesAnyClientNeedIdentityChangeNotifications();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onConfigFailedLocal(Message failedCommand, int reason) {
        Log.v(TAG, "onConfigFailedLocal: failedCommand=" + failedCommand + ", reason=" + reason);
        if (failedCommand.arg1 == 100) {
            IWifiAwareEventCallback callback = (IWifiAwareEventCallback) failedCommand.obj;
            try {
                this.mAttachIsRunning = false;
                callback.onConnectFail(reason);
                this.mAwareMetrics.recordAttachStatus(reason);
            } catch (RemoteException e) {
                Log.w(TAG, "onConfigFailedLocal onConnectFail(): RemoteException (FYI): " + e);
            }
        } else if (failedCommand.arg1 != 101 && failedCommand.arg1 != 120) {
            Log.wtf(TAG, "onConfigFailedLocal: unexpected failedCommand=" + failedCommand);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onDisableResponseLocal(Message command, int reason) {
        Log.v(TAG, "onDisableResponseLocal: command=" + command + ", reason=" + reason);
        if (reason != 0) {
            Log.e(TAG, "onDisableResponseLocal: FAILED!? command=" + command + ", reason=" + reason);
        }
        this.mAwareMetrics.recordDisableAware();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onSessionConfigSuccessLocal(Message completedCommand, byte pubSubId, boolean isPublish) {
        String svcName;
        int maxRange;
        int minRange;
        boolean isRangingEnabled;
        int i;
        Log.v(TAG, "onSessionConfigSuccessLocal: completedCommand=" + completedCommand + ", pubSubId=" + ((int) pubSubId) + ", isPublish=" + isPublish);
        int i2 = 0;
        if (completedCommand.arg1 == 103 || completedCommand.arg1 == 105) {
            int clientId = completedCommand.arg2;
            IWifiAwareDiscoverySessionCallback callback = (IWifiAwareDiscoverySessionCallback) completedCommand.obj;
            WifiAwareClientState client = this.mClients.get(clientId);
            if (client == null) {
                Log.e(TAG, "onSessionConfigSuccessLocal: no client exists for clientId=" + clientId);
                return;
            }
            WifiAwareStateMachine wifiAwareStateMachine = this.mSm;
            int sessionId = wifiAwareStateMachine.mNextSessionId;
            wifiAwareStateMachine.mNextSessionId = sessionId + 1;
            try {
                callback.onSessionStarted(sessionId);
                int minRange2 = -1;
                int maxRange2 = -1;
                if (completedCommand.arg1 == 103) {
                    PublishConfig publishConfig = (PublishConfig) completedCommand.getData().getParcelable(MESSAGE_BUNDLE_KEY_CONFIG);
                    isRangingEnabled = publishConfig.mEnableRanging;
                    minRange = -1;
                    svcName = new String(publishConfig.mServiceName);
                    maxRange = -1;
                } else {
                    SubscribeConfig subscribeConfig = (SubscribeConfig) completedCommand.getData().getParcelable(MESSAGE_BUNDLE_KEY_CONFIG);
                    isRangingEnabled = subscribeConfig.mMinDistanceMmSet || subscribeConfig.mMaxDistanceMmSet;
                    if (subscribeConfig.mMinDistanceMmSet) {
                        minRange2 = subscribeConfig.mMinDistanceMm;
                    }
                    if (subscribeConfig.mMaxDistanceMmSet) {
                        maxRange2 = subscribeConfig.mMaxDistanceMm;
                    }
                    minRange = minRange2;
                    svcName = new String(subscribeConfig.mServiceName);
                    maxRange = maxRange2;
                }
                WifiAwareDiscoverySessionState session = new WifiAwareDiscoverySessionState(this.mWifiAwareNativeApi, sessionId, pubSubId, callback, isPublish, isRangingEnabled, SystemClock.elapsedRealtime());
                session.mDbg = this.mDbg;
                client.addSession(session);
                if (isRangingEnabled) {
                    this.mAwareMetrics.recordDiscoverySessionWithRanging(client.getUid(), completedCommand.arg1 != 103, minRange, maxRange, this.mClients);
                } else {
                    this.mAwareMetrics.recordDiscoverySession(client.getUid(), this.mClients);
                }
                this.mAwareMetrics.recordDiscoveryStatus(client.getUid(), 0, completedCommand.arg1 == 103);
                String callingPackage = client.getCallingPackage();
                if (completedCommand.arg1 == 103) {
                    i = 2;
                } else {
                    i = 3;
                }
                if (isRangingEnabled) {
                    i2 = 1;
                }
                sendAwareBigdata(callingPackage, i, clientId, i2, svcName, -1);
            } catch (RemoteException e) {
                Log.e(TAG, "onSessionConfigSuccessLocal: onSessionStarted() RemoteException=" + e);
            }
        } else if (completedCommand.arg1 == 104 || completedCommand.arg1 == 106) {
            int clientId2 = completedCommand.arg2;
            int sessionId2 = completedCommand.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
            WifiAwareClientState client2 = this.mClients.get(clientId2);
            if (client2 == null) {
                Log.e(TAG, "onSessionConfigSuccessLocal: no client exists for clientId=" + clientId2);
                return;
            }
            WifiAwareDiscoverySessionState session2 = client2.getSession(sessionId2);
            if (session2 == null) {
                Log.e(TAG, "onSessionConfigSuccessLocal: no session exists for clientId=" + clientId2 + ", sessionId=" + sessionId2);
                return;
            }
            try {
                session2.getCallback().onSessionConfigSuccess();
            } catch (RemoteException e2) {
                Log.e(TAG, "onSessionConfigSuccessLocal: onSessionConfigSuccess() RemoteException=" + e2);
            }
            this.mAwareMetrics.recordDiscoveryStatus(client2.getUid(), 0, completedCommand.arg1 == 104);
        } else {
            Log.wtf(TAG, "onSessionConfigSuccessLocal: unexpected completedCommand=" + completedCommand);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onSessionConfigFailLocal(Message failedCommand, boolean isPublish, int reason) {
        Log.v(TAG, "onSessionConfigFailLocal: failedCommand=" + failedCommand + ", isPublish=" + isPublish + ", reason=" + reason);
        boolean z = true;
        if (failedCommand.arg1 == 103 || failedCommand.arg1 == 105) {
            int clientId = failedCommand.arg2;
            IWifiAwareDiscoverySessionCallback callback = (IWifiAwareDiscoverySessionCallback) failedCommand.obj;
            WifiAwareClientState client = this.mClients.get(clientId);
            if (client == null) {
                Log.e(TAG, "onSessionConfigFailLocal: no client exists for clientId=" + clientId);
                return;
            }
            try {
                callback.onSessionConfigFail(reason);
            } catch (RemoteException e) {
                Log.w(TAG, "onSessionConfigFailLocal onSessionConfigFail(): RemoteException (FYI): " + e);
            }
            WifiAwareMetrics wifiAwareMetrics = this.mAwareMetrics;
            int uid = client.getUid();
            if (failedCommand.arg1 != 103) {
                z = false;
            }
            wifiAwareMetrics.recordDiscoveryStatus(uid, reason, z);
        } else if (failedCommand.arg1 == 104 || failedCommand.arg1 == 106) {
            int clientId2 = failedCommand.arg2;
            int sessionId = failedCommand.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
            WifiAwareClientState client2 = this.mClients.get(clientId2);
            if (client2 == null) {
                Log.e(TAG, "onSessionConfigFailLocal: no client exists for clientId=" + clientId2);
                return;
            }
            WifiAwareDiscoverySessionState session = client2.getSession(sessionId);
            if (session == null) {
                Log.e(TAG, "onSessionConfigFailLocal: no session exists for clientId=" + clientId2 + ", sessionId=" + sessionId);
                return;
            }
            try {
                session.getCallback().onSessionConfigFail(reason);
            } catch (RemoteException e2) {
                Log.e(TAG, "onSessionConfigFailLocal: onSessionConfigFail() RemoteException=" + e2);
            }
            WifiAwareMetrics wifiAwareMetrics2 = this.mAwareMetrics;
            int uid2 = client2.getUid();
            if (failedCommand.arg1 != 104) {
                z = false;
            }
            wifiAwareMetrics2.recordDiscoveryStatus(uid2, reason, z);
            if (reason == 3) {
                client2.removeSession(sessionId);
            }
        } else {
            Log.wtf(TAG, "onSessionConfigFailLocal: unexpected failedCommand=" + failedCommand);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onMessageSendSuccessLocal(Message completedCommand) {
        Log.v(TAG, "onMessageSendSuccess: completedCommand=" + completedCommand);
        int clientId = completedCommand.arg2;
        int sessionId = completedCommand.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
        int messageId = completedCommand.getData().getInt(MESSAGE_BUNDLE_KEY_MESSAGE_ID);
        WifiAwareClientState client = this.mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "onMessageSendSuccessLocal: no client exists for clientId=" + clientId);
            return;
        }
        WifiAwareDiscoverySessionState session = client.getSession(sessionId);
        if (session == null) {
            Log.e(TAG, "onMessageSendSuccessLocal: no session exists for clientId=" + clientId + ", sessionId=" + sessionId);
            return;
        }
        try {
            session.getCallback().onMessageSendSuccess(messageId);
        } catch (RemoteException e) {
            Log.w(TAG, "onMessageSendSuccessLocal: RemoteException (FYI): " + e);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onMessageSendFailLocal(Message failedCommand, int reason) {
        Log.v(TAG, "onMessageSendFail: failedCommand=" + failedCommand + ", reason=" + reason);
        int clientId = failedCommand.arg2;
        int sessionId = failedCommand.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
        int messageId = failedCommand.getData().getInt(MESSAGE_BUNDLE_KEY_MESSAGE_ID);
        WifiAwareClientState client = this.mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "onMessageSendFailLocal: no client exists for clientId=" + clientId);
            return;
        }
        WifiAwareDiscoverySessionState session = client.getSession(sessionId);
        if (session == null) {
            Log.e(TAG, "onMessageSendFailLocal: no session exists for clientId=" + clientId + ", sessionId=" + sessionId);
            return;
        }
        try {
            session.getCallback().onMessageSendFail(messageId, reason);
        } catch (RemoteException e) {
            Log.e(TAG, "onMessageSendFailLocal: onMessageSendFail RemoteException=" + e);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onCapabilitiesUpdatedResponseLocal(Capabilities capabilities) {
        Log.v(TAG, "onCapabilitiesUpdatedResponseLocal: capabilites=" + capabilities);
        this.mCapabilities = capabilities;
        this.mCharacteristics = null;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onCreateDataPathInterfaceResponseLocal(Message command, boolean success, int reasonOnFailure) {
        Log.v(TAG, "onCreateDataPathInterfaceResponseLocal: command=" + command + ", success=" + success + ", reasonOnFailure=" + reasonOnFailure);
        if (success) {
            Log.v(TAG, "onCreateDataPathInterfaceResponseLocal: successfully created interface " + command.obj);
            this.mDataPathMgr.onInterfaceCreated((String) command.obj);
            return;
        }
        Log.e(TAG, "onCreateDataPathInterfaceResponseLocal: failed when trying to create interface " + command.obj + ". Reason code=" + reasonOnFailure);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onDeleteDataPathInterfaceResponseLocal(Message command, boolean success, int reasonOnFailure) {
        Log.v(TAG, "onDeleteDataPathInterfaceResponseLocal: command=" + command + ", success=" + success + ", reasonOnFailure=" + reasonOnFailure);
        if (success) {
            Log.v(TAG, "onDeleteDataPathInterfaceResponseLocal: successfully deleted interface " + command.obj);
            this.mDataPathMgr.onInterfaceDeleted((String) command.obj);
            return;
        }
        Log.e(TAG, "onDeleteDataPathInterfaceResponseLocal: failed when trying to delete interface " + command.obj + ". Reason code=" + reasonOnFailure);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onInitiateDataPathResponseSuccessLocal(Message command, int ndpId) {
        Log.v(TAG, "onInitiateDataPathResponseSuccessLocal: command=" + command + ", ndpId=" + ndpId);
        this.mDataPathMgr.onDataPathInitiateSuccess((WifiAwareNetworkSpecifier) command.obj, ndpId);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onInitiateDataPathResponseFailLocal(Message command, int reason) {
        Log.v(TAG, "onInitiateDataPathResponseFailLocal: command=" + command + ", reason=" + reason);
        this.mDataPathMgr.onDataPathInitiateFail((WifiAwareNetworkSpecifier) command.obj, reason);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onRespondToDataPathSetupRequestResponseLocal(Message command, boolean success, int reasonOnFailure) {
        Log.v(TAG, "onRespondToDataPathSetupRequestResponseLocal: command=" + command + ", success=" + success + ", reasonOnFailure=" + reasonOnFailure);
        this.mDataPathMgr.onRespondToDataPathRequest(command.arg2, success, reasonOnFailure);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onEndPathEndResponseLocal(Message command, boolean success, int reasonOnFailure) {
        Log.v(TAG, "onEndPathEndResponseLocal: command=" + command + ", success=" + success + ", reasonOnFailure=" + reasonOnFailure);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onInterfaceAddressChangeLocal(byte[] mac) {
        Log.v(TAG, "onInterfaceAddressChange: mac=" + String.valueOf(HexEncoding.encode(mac)));
        this.mCurrentDiscoveryInterfaceMac = mac;
        for (int i = 0; i < this.mClients.size(); i++) {
            this.mClients.valueAt(i).onInterfaceAddressChange(mac);
        }
        this.mAwareMetrics.recordEnableAware();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onClusterChangeLocal(int flag, byte[] clusterId) {
        Log.v(TAG, "onClusterChange: flag=" + flag + ", clusterId=" + String.valueOf(HexEncoding.encode(clusterId)));
        for (int i = 0; i < this.mClients.size(); i++) {
            this.mClients.valueAt(i).onClusterChange(flag, clusterId, this.mCurrentDiscoveryInterfaceMac);
        }
        this.mAwareMetrics.recordEnableAware();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onMatchLocal(int pubSubId, int requestorInstanceId, byte[] peerMac, byte[] serviceSpecificInfo, byte[] matchFilter, int rangingIndication, int rangeMm) {
        Log.v(TAG, "onMatch: pubSubId=" + pubSubId + ", requestorInstanceId=" + requestorInstanceId + ", peerDiscoveryMac=" + String.valueOf(HexEncoding.encode(peerMac)) + ", serviceSpecificInfo=" + Arrays.toString(serviceSpecificInfo) + ", matchFilter=" + Arrays.toString(matchFilter) + ", rangingIndication=" + rangingIndication + ", rangeMm=" + rangeMm);
        Pair<WifiAwareClientState, WifiAwareDiscoverySessionState> data = getClientSessionForPubSubId(pubSubId);
        if (data == null) {
            Log.e(TAG, "onMatch: no session found for pubSubId=" + pubSubId);
            return;
        }
        if (((WifiAwareDiscoverySessionState) data.second).isRangingEnabled()) {
            this.mAwareMetrics.recordMatchIndicationForRangeEnabledSubscribe(rangingIndication != 0);
        }
        ((WifiAwareDiscoverySessionState) data.second).onMatch(requestorInstanceId, peerMac, serviceSpecificInfo, matchFilter, rangingIndication, rangeMm);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onSessionTerminatedLocal(int pubSubId, boolean isPublish, int reason) {
        Log.v(TAG, "onSessionTerminatedLocal: pubSubId=" + pubSubId + ", isPublish=" + isPublish + ", reason=" + reason);
        Pair<WifiAwareClientState, WifiAwareDiscoverySessionState> data = getClientSessionForPubSubId(pubSubId);
        if (data == null) {
            Log.e(TAG, "onSessionTerminatedLocal: no session found for pubSubId=" + pubSubId);
            return;
        }
        try {
            ((WifiAwareDiscoverySessionState) data.second).getCallback().onSessionTerminated(reason);
        } catch (RemoteException e) {
            Log.w(TAG, "onSessionTerminatedLocal onSessionTerminated(): RemoteException (FYI): " + e);
        }
        ((WifiAwareClientState) data.first).removeSession(((WifiAwareDiscoverySessionState) data.second).getSessionId());
        this.mAwareMetrics.recordDiscoverySessionDuration(((WifiAwareDiscoverySessionState) data.second).getCreationTime(), ((WifiAwareDiscoverySessionState) data.second).isPublishSession());
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onMessageReceivedLocal(int pubSubId, int requestorInstanceId, byte[] peerMac, byte[] message) {
        Log.v(TAG, "onMessageReceivedLocal: pubSubId=" + pubSubId + ", requestorInstanceId=" + requestorInstanceId + ", peerDiscoveryMac=" + String.valueOf(HexEncoding.encode(peerMac)));
        Pair<WifiAwareClientState, WifiAwareDiscoverySessionState> data = getClientSessionForPubSubId(pubSubId);
        if (data == null) {
            Log.e(TAG, "onMessageReceivedLocal: no session found for pubSubId=" + pubSubId);
            return;
        }
        ((WifiAwareDiscoverySessionState) data.second).onMessageReceived(requestorInstanceId, peerMac, message);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void onAwareDownLocal() {
        Log.v(TAG, "onAwareDown: mCurrentAwareConfiguration=" + this.mCurrentAwareConfiguration);
        if (this.mCurrentAwareConfiguration != null) {
            for (int i = 0; i < this.mClients.size(); i++) {
                this.mAwareMetrics.recordAttachSessionDuration(this.mClients.valueAt(i).getCreationTime());
                SparseArray<WifiAwareDiscoverySessionState> sessions = this.mClients.valueAt(i).getSessions();
                for (int j = 0; j < sessions.size(); j++) {
                    this.mAwareMetrics.recordDiscoverySessionDuration(sessions.valueAt(j).getCreationTime(), sessions.valueAt(j).isPublishSession());
                }
            }
            this.mAwareMetrics.recordDisableAware();
            this.mClients.clear();
            this.mSettableParameters.put(PARAM_ON_IDLE_DISABLE_AWARE, 1);
            unsetAllLocationWhiteList();
            this.mCurrentAwareConfiguration = null;
            this.mSm.onAwareDownCleanupSendQueueState();
            this.mDataPathMgr.onAwareDownCleanupDataPaths();
            this.mCurrentDiscoveryInterfaceMac = ALL_ZERO_MAC;
            deleteAllDataPathInterfaces();
            this.mAttachIsRunning = false;
            this.mDisconnectedCallingPackage.clear();
        }
    }

    private Pair<WifiAwareClientState, WifiAwareDiscoverySessionState> getClientSessionForPubSubId(int pubSubId) {
        for (int i = 0; i < this.mClients.size(); i++) {
            WifiAwareClientState client = this.mClients.valueAt(i);
            WifiAwareDiscoverySessionState session = client.getAwareSessionStateForPubSubId(pubSubId);
            if (session != null) {
                return new Pair<>(client, session);
            }
        }
        return null;
    }

    private ConfigRequest mergeConfigRequests(ConfigRequest configRequest) {
        Log.v(TAG, "mergeConfigRequests(): mClients=[" + this.mClients + "], configRequest=" + configRequest);
        if (this.mClients.size() == 0 && configRequest == null) {
            Log.e(TAG, "mergeConfigRequests: invalid state - called with 0 clients registered!");
            return null;
        }
        boolean support5gBand = false;
        int masterPreference = 0;
        boolean clusterIdValid = false;
        int clusterLow = 0;
        int clusterHigh = 65535;
        int[] discoveryWindowInterval = {-1, -1};
        if (configRequest != null) {
            support5gBand = configRequest.mSupport5gBand;
            masterPreference = configRequest.mMasterPreference;
            clusterIdValid = true;
            clusterLow = configRequest.mClusterLow;
            clusterHigh = configRequest.mClusterHigh;
            discoveryWindowInterval = configRequest.mDiscoveryWindowInterval;
        }
        for (int i = 0; i < this.mClients.size(); i++) {
            ConfigRequest cr = this.mClients.valueAt(i).getConfigRequest();
            if (cr.mSupport5gBand) {
                support5gBand = true;
            }
            masterPreference = Math.max(masterPreference, cr.mMasterPreference);
            if (!clusterIdValid) {
                clusterIdValid = true;
                clusterLow = cr.mClusterLow;
                clusterHigh = cr.mClusterHigh;
            } else if (!(clusterLow == cr.mClusterLow && clusterHigh == cr.mClusterHigh)) {
                return null;
            }
            for (int band = 0; band <= 1; band++) {
                if (discoveryWindowInterval[band] == -1) {
                    discoveryWindowInterval[band] = cr.mDiscoveryWindowInterval[band];
                } else if (cr.mDiscoveryWindowInterval[band] != -1) {
                    if (discoveryWindowInterval[band] == 0) {
                        discoveryWindowInterval[band] = cr.mDiscoveryWindowInterval[band];
                    } else if (cr.mDiscoveryWindowInterval[band] != 0) {
                        discoveryWindowInterval[band] = Math.min(discoveryWindowInterval[band], cr.mDiscoveryWindowInterval[band]);
                    }
                }
            }
        }
        ConfigRequest.Builder builder = new ConfigRequest.Builder().setSupport5gBand(support5gBand).setMasterPreference(masterPreference).setClusterLow(clusterLow).setClusterHigh(clusterHigh);
        for (int band2 = 0; band2 <= 1; band2++) {
            if (discoveryWindowInterval[band2] != -1) {
                builder.setDiscoveryWindowInterval(band2, discoveryWindowInterval[band2]);
            }
        }
        return builder.build();
    }

    private boolean doesAnyClientNeedIdentityChangeNotifications() {
        for (int i = 0; i < this.mClients.size(); i++) {
            if (this.mClients.valueAt(i).getNotifyIdentityChange()) {
                return true;
            }
        }
        return false;
    }

    /* access modifiers changed from: private */
    public static String messageToString(Message msg) {
        StringBuilder sb = new StringBuilder();
        String s = sSmToString.get(msg.what);
        if (s == null) {
            s = "<unknown>";
        }
        sb.append(s);
        sb.append("/");
        if (msg.what == 3 || msg.what == 1 || msg.what == 2) {
            String s2 = sSmToString.get(msg.arg1);
            if (s2 == null) {
                s2 = "<unknown>";
            }
            sb.append(s2);
        }
        if (msg.what == 2 || msg.what == 4) {
            sb.append(" (Transaction ID=");
            sb.append(msg.arg2);
            sb.append(")");
        }
        return sb.toString();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("AwareStateManager:");
        pw.println("  mClients: [" + this.mClients + "]");
        StringBuilder sb = new StringBuilder();
        sb.append("  mUsageEnabled: ");
        sb.append(this.mUsageEnabled);
        pw.println(sb.toString());
        pw.println("  mCapabilities: [" + this.mCapabilities + "]");
        StringBuilder sb2 = new StringBuilder();
        sb2.append("  mCurrentAwareConfiguration: ");
        sb2.append(this.mCurrentAwareConfiguration);
        pw.println(sb2.toString());
        pw.println("  mCurrentIdentityNotification: " + this.mCurrentIdentityNotification);
        for (int i = 0; i < this.mClients.size(); i++) {
            this.mClients.valueAt(i).dump(fd, pw, args);
        }
        pw.println("  mSettableParameters: " + this.mSettableParameters);
        this.mSm.dump(fd, pw, args);
        this.mDataPathMgr.dump(fd, pw, args);
        this.mWifiAwareNativeApi.dump(fd, pw, args);
        pw.println("mAwareMetrics:");
        this.mAwareMetrics.dump(fd, pw, args);
    }

    private void checkAndShowAwareEnableDialog(int req_type) {
        if (req_type == 0) {
            Log.d(TAG, "Aware is not allowed because MHS is enabled");
            showWifiEnableWarning(6, 0);
        } else if (req_type == 1) {
            Log.d(TAG, "Aware is not allowed because P2P is enabled");
            showWifiEnableWarning(6, 1);
        }
    }

    private void showWifiEnableWarning(int extra_type, int req_type) {
        Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.samsung.android.settings.wifi.WifiWarning");
        intent.putExtra("req_type", req_type);
        intent.putExtra("extra_type", extra_type);
        intent.setFlags(1015021568);
        try {
            this.mContext.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Exception is occured in showWifiEnableWarning.");
        }
    }

    private boolean isLocationWhiteList(String callingPackage) {
        if (callingPackage.equals("com.samsung.android.mdx.kit") || callingPackage.equals("com.android.bluetooth") || callingPackage.equals("com.samsung.android.mcfserver") || callingPackage.equals("com.samsung.sept.WIFI")) {
            Log.i(TAG, "isLocationWhiteList true: callingPackage: " + callingPackage);
            return true;
        }
        Log.i(TAG, "isLocationWhiteList false: callingPackage: " + callingPackage);
        return false;
    }

    private boolean allowLocationWhiteList(String callingPackage) {
        if (!isLocationWhiteList(callingPackage) || this.mWifiManager.getWifiState() != 3 || (this.mSettableParameters.get(PARAM_ON_IDLE_DISABLE_AWARE).intValue() != 0 && this.mPowerManager.isDeviceIdleMode())) {
            Log.i(TAG, "allowLocationWhiteList false");
            return false;
        }
        Log.i(TAG, "allowLocationWhiteList true");
        return true;
    }

    public boolean isRunningLocationWhiteList() {
        ArrayList<Integer> arrayList = this.mSavedClientId;
        if (arrayList == null || arrayList.size() <= 0) {
            return false;
        }
        return true;
    }

    private boolean setLocationWhiteList(int clientId, String callingPackage) {
        ArrayList<Integer> arrayList;
        if (!isLocationWhiteList(callingPackage) || (arrayList = this.mSavedClientId) == null || arrayList.contains(Integer.valueOf(clientId))) {
            Log.i(TAG, "setLocationWhiteList false or this clientId already set. : callingPackage: " + callingPackage + " clientId: " + clientId);
            return false;
        }
        Log.i(TAG, "setLocationWhiteList true. callingPackage: " + callingPackage + " clientId: " + clientId);
        this.mSavedClientId.add(Integer.valueOf(clientId));
        if (!callingPackage.equals("com.samsung.android.mcfserver")) {
            return true;
        }
        this.mSettableParameters.put(PARAM_ON_IDLE_DISABLE_AWARE, 0);
        return true;
    }

    private boolean unsetLocationWhiteList(int clientId) {
        ArrayList<Integer> arrayList = this.mSavedClientId;
        if (arrayList == null || arrayList.size() <= 0 || !this.mSavedClientId.contains(Integer.valueOf(clientId))) {
            Log.i(TAG, "unsetLocationWhiteList false.");
            return false;
        }
        this.mSavedClientId.remove(Integer.valueOf(clientId));
        Log.i(TAG, "unsetLocationWhiteList true.");
        return true;
    }

    private boolean unsetAllLocationWhiteList() {
        ArrayList<Integer> arrayList = this.mSavedClientId;
        if (arrayList == null || arrayList.size() <= 0) {
            Log.i(TAG, "unsetAllLocationWhiteList : already cleared.");
            return false;
        }
        Log.i(TAG, "unsetAllLocationWhiteList : cleared.");
        this.mSavedClientId.clear();
        return true;
    }

    public void resetAwareTrafficPollToken() {
        this.mAwareTrafficPollToken = 0;
        sendZeroTrafficToPoller();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendAwareTrafficToPoller() {
        Set<String> interfaces = this.mDataPathMgr.getAllInterfaces();
        if (interfaces == null || interfaces.isEmpty()) {
            sendZeroTrafficToPoller();
            return;
        }
        long mTxBytes = 0;
        long mRxBytes = 0;
        for (String mInterface : interfaces) {
            if (!TextUtils.isEmpty(mInterface)) {
                mTxBytes += TrafficStats.getTxBytes(mInterface);
                mRxBytes += TrafficStats.getRxBytes(mInterface);
            }
        }
        WifiInjector.getInstance().getWifiTrafficPoller().notifyOnDataActivity(mTxBytes, mRxBytes);
    }

    private void sendZeroTrafficToPoller() {
        WifiInjector.getInstance().getWifiTrafficPoller().notifyOnDataActivity(0, 0);
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String buildLoggingDataLocal(String pkgName, int opType, int clientId, int rangeMm, String svcName, long nanOpDuration) {
        String packageName = "";
        String serviceName = "";
        if (pkgName != null) {
            packageName = pkgName.replaceAll("\\s+", "");
        }
        if (pkgName == null || TextUtils.isEmpty(packageName)) {
            packageName = "unknown";
        }
        if (svcName != null) {
            serviceName = svcName.replaceAll("\\s+", "");
        }
        if (!(opType == 2 || opType == 3) || svcName == null || TextUtils.isEmpty(serviceName)) {
            serviceName = "invalid";
        }
        return packageName + " " + opType + " " + clientId + " " + rangeMm + " " + serviceName + " " + nanOpDuration;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void insertLog(String feature, String extra, long value) {
        Log.d(TAG, "insertLog (HQM : true, CONTEXT : " + ENABLE_SURVEY_MODE + ") - feature : " + feature + ", extra : " + extra + ", value : " + value);
        if (this.mSemHqmManager == null) {
            this.mSemHqmManager = (SemHqmManager) this.mContext.getSystemService("HqmManagerService");
        }
        WifiChipInfo wifiChipInfo = WifiChipInfo.getInstance();
        WifiChipInfo.getChipsetName();
        wifiChipInfo.getCidInfo();
        SemHqmManager semHqmManager = this.mSemHqmManager;
        if (semHqmManager != null) {
            semHqmManager.sendHWParamToHQMwithAppId(0, BaseBigDataItem.COMPONENT_ID, feature, BaseBigDataItem.HIT_TYPE_ONCE_A_DAY, (String) null, (String) null, "", extra, "", WifiAwareBigdata.APP_ID);
        } else {
            Log.e(TAG, "error - mSemHqmManager is null");
        }
    }

    public String getDisconnectedCallingPackage(int clientId) {
        return this.mDisconnectedCallingPackage.remove(Integer.valueOf(clientId));
    }
}
