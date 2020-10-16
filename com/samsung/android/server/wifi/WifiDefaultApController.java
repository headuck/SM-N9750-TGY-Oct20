package com.samsung.android.server.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.util.LocalLog;
import android.util.Log;
import com.android.server.wifi.ClientModeImpl;
import com.android.server.wifi.WifiBackupRestore;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiServiceImpl;
import com.android.server.wifi.tcp.WifiTransportLayerUtils;
import com.android.server.wifi.util.StringUtil;
import com.samsung.android.net.wifi.OpBrandingLoader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class WifiDefaultApController {
    private static final String ACTION_ATT_RESET = "com.samsung.intent.action.SETTINGS_RESET_WIFI";
    private static final String ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";
    private static final String ACTION_CSC_WIFI_DEFAULTAP_DONE = "com.samsung.intent.action.CSC_WIFI_DEFAULTAP_DONE";
    private static final String ACTION_LOAD_CONFIG_COMPLETE = "com.samsung.android.net.wifi.LOAD_INTERNAL_DATA_COMPLETE";
    private static final String ACTION_SIM_STATE_CHANGE = "android.intent.action.SIM_STATE_CHANGED";
    private static final int READ_FILE_AND_COPY_TO_CONFIG = 2;
    private static final boolean REMOVABLE_DEFAULT_AP = OpBrandingLoader.getInstance().isSupportRemovableDefaultAp();
    private static final int REMOVE_BY_SIM_ABSENT = 3;
    private static final int REQUEST_DONE = 3;
    private static final int REQUEST_FROM_CSC = 1;
    private static final int REQUEST_FROM_REBOOT = 0;
    private static final int REQUEST_FROM_SIM = 2;
    private static final String TAG = "WifiDefaultApController";
    private static final int UPDATE_DEFAULTAP_CONF = 1;
    private static final String[] invalidState = {"InitialState", "SupplicantStartingState"};
    private static File mFilePathDefaultAp = new File("/data/misc/wifi/default_ap.conf");
    private static File mFilePathGeneralNwInfo = new File("/data/misc/wifi/generalinfo_nw.conf");
    private static File mFilePathRemovedNwInfo = new File("/data/misc/wifi/removed_nw.conf");
    private static int mNeedtoAddVendorAp = 0;
    private static final String[] requestStrings = {"REQUEST_FROM_REBOOT", "REQUEST_FROM_CSC", "REQUEST_FROM_SIM", "REQUEST_DONE"};
    private static WifiDefaultApController sInstance;
    private static final String[] simStateStrings = {"SIM_STATE_UNKNOWN", "SIM_STATE_ABSENT", "SIM_STATE_PIN_REQUIRED", "SIM_STATE_PUK_REQUIRED", "SIM_STATE_NETWORK_LOCKED", "SIM_STATE_READY", "SIM_STATE_NOT_READY", "SIM_STATE_PERM_DISABLED", "SIM_STATE_CARD_IO_ERROR", "SIM_STATE_CARD_RESTRICTED", "SIM_STATE_PERSO_LOCKED", "SIM_STATE_NETWORK_SUBSET_LOCKED", "SIM_STATE_SIM_SERVICE_PROVIDER_LOCKED", "SIM_STATE_DETECTED"};
    private final int LocalLogSize = 256;
    private final ClientModeImpl mClientModeImpl;
    private final Context mContext;
    private ArrayList<DefinedVendorAp> mDefinedVedorApList = new ArrayList<>();
    private ArrayList<GeneralNetworkInfo> mGeneralNwInfoList = new ArrayList<>();
    private ResultFailHandler mHandler;
    private boolean mIsloadInternalDataCompleted = false;
    private final LocalLog mLocalLog;
    private String mMatchedNetworkName = "";
    private ArrayList<RemovedVendorAp> mRemovedVedorApList = new ArrayList<>();
    private List<WifiConfiguration> mToAddConfigList;
    private ArrayList<WifiConfiguration> mToBeDeletedList = new ArrayList<>();
    private WifiInjector mWifiInjector;
    private WifiServiceImpl mWifiService;
    private String mccmncOfSim = "";
    private int previousSimState = 0;

    public static synchronized WifiDefaultApController init(Context context, WifiServiceImpl service, ClientModeImpl clientModeImpl, WifiInjector wifiInjector) {
        WifiDefaultApController wifiDefaultApController;
        synchronized (WifiDefaultApController.class) {
            if (sInstance == null) {
                sInstance = new WifiDefaultApController(context, service, clientModeImpl, wifiInjector);
            }
            wifiDefaultApController = sInstance;
        }
        return wifiDefaultApController;
    }

    private WifiDefaultApController(Context context, WifiServiceImpl service, ClientModeImpl clientModeImpl, WifiInjector wifiInjector) {
        this.mContext = context;
        this.mWifiService = service;
        this.mClientModeImpl = clientModeImpl;
        this.mWifiInjector = wifiInjector;
        this.mLocalLog = new LocalLog(256);
        registerCscIntent();
        registerSimChangeIntent();
        registerAttReset();
        registerLoadConfigCompleteIntent();
        HandlerThread handlerThread = new HandlerThread("ResultFailHandler");
        handlerThread.start();
        this.mHandler = new ResultFailHandler(handlerThread.getLooper());
    }

    private void registerLoadConfigCompleteIntent() {
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.samsung.android.server.wifi.WifiDefaultApController.C07311 */

            public void onReceive(Context context, Intent intent) {
                WifiDefaultApController.this.mIsloadInternalDataCompleted = true;
                WifiDefaultApController.this.logi(WifiDefaultApController.TAG, "ACTION_LOAD_CONFIG_COMPLETE");
                if (intent.getBooleanExtra("passpointConfiguration", false)) {
                    WifiDefaultApController.this.logi(WifiDefaultApController.TAG, "skip - PasspointConfig Loaded");
                } else if (WifiDefaultApController.mNeedtoAddVendorAp == 1) {
                    WifiParsingCustomerFile.getInstance().getCustomerFile();
                    if (!WifiDefaultApController.mFilePathDefaultAp.exists()) {
                        WifiDefaultApController.this.removeVendorApfromConfig(null);
                    }
                    WifiDefaultApController.this.mHandler.sendEmptyMessage(2);
                } else if (WifiDefaultApController.mNeedtoAddVendorAp == 0) {
                    WifiDefaultApController.this.mHandler.sendEmptyMessage(2);
                }
            }
        }, new IntentFilter(ACTION_LOAD_CONFIG_COMPLETE));
    }

    private void registerCscIntent() {
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.samsung.android.server.wifi.WifiDefaultApController.C07322 */

            public void onReceive(Context context, Intent intent) {
                WifiDefaultApController.this.logi(WifiDefaultApController.TAG, "ACTION_CSC_WIFI_DEFAULTAP_DONE");
                int unused = WifiDefaultApController.mNeedtoAddVendorAp = 1;
                if (WifiDefaultApController.this.mIsloadInternalDataCompleted) {
                    WifiParsingCustomerFile.getInstance().getCustomerFile();
                    if (!WifiDefaultApController.mFilePathDefaultAp.exists()) {
                        WifiDefaultApController.this.removeVendorApfromConfig(null);
                    }
                    WifiDefaultApController.this.mHandler.sendEmptyMessage(2);
                }
            }
        }, new IntentFilter(ACTION_CSC_WIFI_DEFAULTAP_DONE));
    }

    private void registerSimChangeIntent() {
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.samsung.android.server.wifi.WifiDefaultApController.C07333 */

            public void onReceive(Context context, Intent intent) {
                if (WifiDefaultApController.mFilePathGeneralNwInfo.exists() && WifiDefaultApController.mFilePathGeneralNwInfo.length() > 0) {
                    int currentSimState = TelephonyManager.getDefault().getSimState();
                    WifiDefaultApController wifiDefaultApController = WifiDefaultApController.this;
                    wifiDefaultApController.logi(WifiDefaultApController.TAG, "SIM_STATE_CHANGED from " + WifiDefaultApController.this.previousSimState + " to " + currentSimState);
                    if (!WifiDefaultApController.mFilePathDefaultAp.exists() || WifiDefaultApController.mFilePathDefaultAp.length() <= 0) {
                        WifiDefaultApController.this.loge(WifiDefaultApController.TAG, "DefaultAp file does not exist");
                        return;
                    }
                    if (currentSimState == 5 && currentSimState != WifiDefaultApController.this.previousSimState && WifiDefaultApController.mNeedtoAddVendorAp == 3 && WifiDefaultApController.mNeedtoAddVendorAp != 0) {
                        int unused = WifiDefaultApController.mNeedtoAddVendorAp = 2;
                        WifiDefaultApController.this.mHandler.sendEmptyMessage(2);
                    } else if (WifiDefaultApController.this.previousSimState == 5 && currentSimState == 1) {
                        WifiDefaultApController.this.mHandler.sendEmptyMessage(3);
                    }
                    WifiDefaultApController.this.previousSimState = currentSimState;
                }
            }
        }, new IntentFilter(ACTION_SIM_STATE_CHANGE));
    }

    private void registerAttReset() {
        this.mContext.registerReceiver(new BroadcastReceiver() {
            /* class com.samsung.android.server.wifi.WifiDefaultApController.C07344 */

            public void onReceive(Context context, Intent intent) {
                WifiDefaultApController.this.logi(WifiDefaultApController.TAG, "ATT Reset");
                WifiDefaultApController.this.factoryReset();
            }
        }, new IntentFilter(ACTION_ATT_RESET));
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void readDefaultApAndCopytoConfig() {
        loge(TAG, "readDefaultApAndCopytoConfig");
        if (Arrays.asList(invalidState).contains(this.mClientModeImpl.getCurrentState().getName())) {
            loge(TAG, "mClientModeImpl is in : " + this.mClientModeImpl.getCurrentState().getName());
        } else if (!readDefaultApFile()) {
            mNeedtoAddVendorAp = 3;
        } else {
            boolean useSIM = readGeneralNwInfoFile();
            Log.i(TAG, " === useSIM ====   " + useSIM);
            if (useSIM) {
                this.mMatchedNetworkName = getMatchedNetworkNameUsingSIM();
            }
            readRemovedNwInfoFile();
            this.mToAddConfigList = makeDefaultAptoWifiConfigList();
            CopyDefaultApToConfig();
        }
    }

    private boolean readDefaultApFile() {
        StringBuilder sb;
        DefinedVendorAp net;
        loge(TAG, "readDefaultApFile: file (" + mFilePathDefaultAp + ") is not founded.");
        if (!mFilePathDefaultAp.exists()) {
            loge(TAG, "readDefaultApFile: file (" + mFilePathDefaultAp + ") is not founded.");
            return false;
        }
        this.mDefinedVedorApList.clear();
        BufferedReader defaultApStream = null;
        try {
            BufferedReader defaultApStream2 = new BufferedReader(new FileReader(mFilePathDefaultAp));
            while (defaultApStream2.ready()) {
                String line = defaultApStream2.readLine();
                if (line != null) {
                    Log.i(TAG, "Default AP Info: ");
                    if (line.startsWith("network") && (net = DefinedVendorAp.parsingNetworkBlock(defaultApStream2)) != null) {
                        this.mDefinedVedorApList.add(net);
                    }
                }
            }
            try {
                defaultApStream2.close();
            } catch (IOException e) {
                e = e;
                sb = new StringBuilder();
            }
        } catch (IOException e2) {
            loge(TAG, "readDefaultApFile: " + e2);
            if (0 != 0) {
                try {
                    defaultApStream.close();
                } catch (IOException e3) {
                    e = e3;
                    sb = new StringBuilder();
                }
            }
        } catch (Throwable th) {
            if (0 != 0) {
                try {
                    defaultApStream.close();
                } catch (IOException e4) {
                    loge(TAG, "readDefaultApFile: " + e4);
                }
            }
            throw th;
        }
        Log.i(TAG, " === readDefaultApFile THE END ==== ");
        return true;
        sb.append("readDefaultApFile: ");
        sb.append(e);
        loge(TAG, sb.toString());
        Log.i(TAG, " === readDefaultApFile THE END ==== ");
        return true;
    }

    private boolean readGeneralNwInfoFile() {
        StringBuilder sb;
        GeneralNetworkInfo net;
        if (!mFilePathGeneralNwInfo.exists()) {
            loge(TAG, "readGeneralNwInfoFile: file (" + mFilePathGeneralNwInfo + ") is not founded.");
            return false;
        }
        this.mGeneralNwInfoList.clear();
        BufferedReader generalNetworkStream = null;
        try {
            BufferedReader generalNetworkStream2 = new BufferedReader(new FileReader(mFilePathGeneralNwInfo));
            while (generalNetworkStream2.ready()) {
                String line = generalNetworkStream2.readLine();
                if (line != null) {
                    Log.i(TAG, "General Network Info: ");
                    if (line.startsWith("network") && (net = GeneralNetworkInfo.parsingNetworkBlock(generalNetworkStream2)) != null) {
                        this.mGeneralNwInfoList.add(net);
                    }
                }
            }
            try {
                generalNetworkStream2.close();
            } catch (IOException e) {
                e = e;
                sb = new StringBuilder();
            }
        } catch (IOException e2) {
            loge(TAG, "readGeneralNwInfoFile: " + e2);
            if (0 != 0) {
                try {
                    generalNetworkStream.close();
                } catch (IOException e3) {
                    e = e3;
                    sb = new StringBuilder();
                }
            }
        } catch (Throwable th) {
            if (0 != 0) {
                try {
                    generalNetworkStream.close();
                } catch (IOException e4) {
                    loge(TAG, "readGeneralNwInfoFile: " + e4);
                }
            }
            throw th;
        }
        Log.i(TAG, " === readGeneralNwInfoFile THE END ==== ");
        return true;
        sb.append("readGeneralNwInfoFile: ");
        sb.append(e);
        loge(TAG, sb.toString());
        Log.i(TAG, " === readGeneralNwInfoFile THE END ==== ");
        return true;
    }

    /* JADX WARNING: Removed duplicated region for block: B:40:0x00d3 A[LOOP:1: B:39:0x00d1->B:40:0x00d3, LOOP_END] */
    private void readRemovedNwInfoFile() {
        int removedNwInfoCount;
        int i;
        StringBuilder sb;
        RemovedVendorAp net;
        logi(TAG, "readRemovedNwInfoFile");
        this.mRemovedVedorApList.clear();
        if (!mFilePathRemovedNwInfo.exists()) {
            loge(TAG, "readRemovedNwInfoFile: file (" + mFilePathRemovedNwInfo + ") is not founded.");
            return;
        }
        BufferedReader removedApStream = null;
        try {
            BufferedReader removedApStream2 = new BufferedReader(new FileReader(mFilePathRemovedNwInfo));
            String line = removedApStream2.readLine();
            if (line == null) {
                loge(TAG, "readRemovedNwInfoFile - removedApStream.readLine() is null");
                try {
                    removedApStream2.close();
                    return;
                } catch (IOException e2) {
                    loge(TAG, "readRemovedNwInfoFile - IOException 2 " + e2);
                    return;
                }
            } else {
                if (line.startsWith("version=1")) {
                    while (removedApStream2.ready()) {
                        String line2 = removedApStream2.readLine();
                        if (!(line2 == null || !line2.startsWith("network") || (net = RemovedVendorAp.parsingNetworkBlock(removedApStream2)) == null)) {
                            this.mRemovedVedorApList.add(net);
                        }
                    }
                }
                try {
                    removedApStream2.close();
                } catch (IOException e) {
                    e2 = e;
                    sb = new StringBuilder();
                }
                removedNwInfoCount = this.mRemovedVedorApList.size();
                for (i = 0; i < removedNwInfoCount; i++) {
                    Log.d(TAG, "readRemovedNwInfoFile[" + i + "]: ssid(" + this.mRemovedVedorApList.get(i).mRemovedSSID + ")");
                }
            }
        } catch (IOException e1) {
            loge(TAG, "readRemovedNwInfoFile - IOException 1 " + e1);
            if (0 != 0) {
                try {
                    removedApStream.close();
                } catch (IOException e3) {
                    e2 = e3;
                    sb = new StringBuilder();
                }
            }
        } catch (Throwable th) {
            if (0 != 0) {
                try {
                    removedApStream.close();
                } catch (IOException e22) {
                    loge(TAG, "readRemovedNwInfoFile - IOException 2 " + e22);
                }
            }
            throw th;
        }
        sb.append("readRemovedNwInfoFile - IOException 2 ");
        sb.append(e2);
        loge(TAG, sb.toString());
        removedNwInfoCount = this.mRemovedVedorApList.size();
        while (i < removedNwInfoCount) {
        }
    }

    private void removeFile() {
        if (mFilePathDefaultAp.exists()) {
            logd(TAG, "delete default_ap.conf file");
            mFilePathDefaultAp.delete();
        }
    }

    private void removeSimilarAp(WifiConfiguration config) {
        List<WifiConfiguration> configs = this.mWifiInjector.getWifiConfigManager().getConfiguredNetworks();
        if (configs == null) {
            logi(TAG, "removeSimilarAp - config is null");
            return;
        }
        int toDeleteId = -1;
        Iterator<WifiConfiguration> it = configs.iterator();
        while (true) {
            if (!it.hasNext()) {
                break;
            }
            WifiConfiguration ap = it.next();
            if (ap.SSID != null && ap.SSID.equals(config.SSID)) {
                String keymgmt1 = StringUtil.makeString(ap.allowedKeyManagement, WifiConfiguration.KeyMgmt.strings);
                String keymgmt2 = StringUtil.makeString(config.allowedKeyManagement, WifiConfiguration.KeyMgmt.strings);
                logi(TAG, "keymgmt1 " + keymgmt1);
                logi(TAG, "keymgmt2 " + keymgmt2);
                if ((keymgmt1.contains("FT-EAP") && keymgmt2.contains("WPA-EAP")) || (keymgmt2.contains("FT-EAP") && keymgmt1.contains("WPA-EAP"))) {
                    logi(TAG, ap.SSID + ": " + keymgmt2 + " and " + config.SSID + ": " + keymgmt1 + " is different. But we regard as same AP.");
                    toDeleteId = ap.networkId;
                }
            }
        }
        if (toDeleteId != -1) {
            this.mWifiService.removeNetwork(toDeleteId, this.mContext.getPackageName());
        }
    }

    private void addVendorAP(WifiConfiguration config) {
        if (config == null) {
            logi(TAG, "addVendorAP - config is null");
            return;
        }
        removeSimilarAp(config);
        int netId = this.mWifiService.addOrUpdateNetwork(config, this.mContext.getPackageName());
        logd(TAG, "addVendorAP  SSID ( " + config.getPrintableSsid() + " )  netId ( " + netId + " ), Kmgmt ( " + StringUtil.makeString(config.allowedKeyManagement, WifiConfiguration.KeyMgmt.strings) + " ), EAP ( " + StringUtil.makeStringEapMethod(config) + " )");
        if (netId != -1) {
            this.mWifiService.enableNetwork(netId, false, this.mContext.getPackageName());
            logi(TAG, "is added");
            return;
        }
        mNeedtoAddVendorAp = 0;
        startScan();
        loge(TAG, "addVendorAP error");
    }

    private List<WifiConfiguration> makeDefaultAptoWifiConfigList() {
        ArrayList<WifiConfiguration> wifiConfigurations = new ArrayList<>();
        Iterator<DefinedVendorAp> it = this.mDefinedVedorApList.iterator();
        while (it.hasNext()) {
            DefinedVendorAp net = it.next();
            if (!mFilePathGeneralNwInfo.exists() || net.networkName == null || this.mMatchedNetworkName.contains(net.networkName)) {
                WifiConfiguration wifiConfiguration = net.createWifiConfiguration();
                if (wifiConfiguration != null) {
                    logv(TAG, "Parsed Configuration: " + wifiConfiguration.configKey());
                    wifiConfigurations.add(wifiConfiguration);
                }
            } else {
                logv(TAG, "makeDefaultAptoWifiConfigList: mismatch network name ( " + this.mMatchedNetworkName + " ) and ( " + net.networkName + " ) of " + net.SSID);
            }
        }
        return wifiConfigurations;
    }

    private void CopyDefaultApToConfig() {
        logd(TAG, "CopyDefaultApToConfig: mNeedtoAddVendorAp ( " + requestStrings[mNeedtoAddVendorAp] + " ) ");
        List<WifiConfiguration> configs = this.mWifiInjector.getWifiConfigManager().getConfiguredNetworks();
        if (mNeedtoAddVendorAp < 3 && mFilePathDefaultAp.exists() && mFilePathDefaultAp.length() > 0) {
            this.mToBeDeletedList.clear();
            if (!(mNeedtoAddVendorAp >= 3 || configs == null || configs.size() == 0)) {
                for (WifiConfiguration config : configs) {
                    if (config.semIsVendorSpecificSsid) {
                        this.mToBeDeletedList.add(config);
                    }
                }
            }
            if (!this.mToBeDeletedList.isEmpty()) {
                Iterator<WifiConfiguration> it = this.mToBeDeletedList.iterator();
                while (it.hasNext()) {
                    WifiConfiguration deleteConfig = it.next();
                    boolean findSameAp = false;
                    List<WifiConfiguration> list = this.mToAddConfigList;
                    if (!(list == null || list.size() == 0)) {
                        for (WifiConfiguration addConfig : this.mToAddConfigList) {
                            if (equalTwoConfig(addConfig, deleteConfig)) {
                                findSameAp = true;
                            }
                        }
                    }
                    if (!findSameAp) {
                        removeVendorApfromConfig(deleteConfig);
                    }
                }
            }
            if (!(configs == null || configs.size() == 0)) {
                for (WifiConfiguration config2 : configs) {
                    boolean findSameAp2 = false;
                    if (config2.semIsVendorSpecificSsid) {
                        int i = 0;
                        List<WifiConfiguration> list2 = this.mToAddConfigList;
                        if (list2 != null && list2.size() != 0) {
                            Iterator<WifiConfiguration> it2 = this.mToAddConfigList.iterator();
                            while (true) {
                                if (!it2.hasNext()) {
                                    break;
                                }
                                WifiConfiguration addConfig2 = it2.next();
                                if (equalTwoConfig(config2, addConfig2)) {
                                    logd(TAG, addConfig2.SSID + " is already saved");
                                    findSameAp2 = true;
                                    break;
                                }
                                i++;
                            }
                        }
                        if (findSameAp2) {
                            this.mToAddConfigList.remove(i);
                        }
                    }
                }
            }
            if (REMOVABLE_DEFAULT_AP) {
                for (int i2 = 0; i2 < this.mRemovedVedorApList.size(); i2++) {
                    int j = 0;
                    boolean findSameAp3 = false;
                    List<WifiConfiguration> list3 = this.mToAddConfigList;
                    if (list3 != null && list3.size() != 0) {
                        Iterator<WifiConfiguration> it3 = this.mToAddConfigList.iterator();
                        while (true) {
                            if (!it3.hasNext()) {
                                break;
                            }
                            WifiConfiguration addConfig3 = it3.next();
                            if (addConfig3.SSID.equals(this.mRemovedVedorApList.get(i2).mRemovedSSID) && StringUtil.makeString(addConfig3.allowedKeyManagement, WifiConfiguration.KeyMgmt.strings).equals(this.mRemovedVedorApList.get(i2).mRemovedKeymgmt) && StringUtil.makeStringEapMethod(addConfig3).equals(this.mRemovedVedorApList.get(i2).mRemovedEap)) {
                                logd(TAG, addConfig3.SSID + " was removed by user.");
                                findSameAp3 = true;
                                break;
                            }
                            j++;
                        }
                    }
                    if (findSameAp3) {
                        this.mToAddConfigList.remove(j);
                    }
                }
            }
            mNeedtoAddVendorAp = 3;
            List<WifiConfiguration> list4 = this.mToAddConfigList;
            if (!(list4 == null || list4.size() == 0)) {
                for (WifiConfiguration addConfig4 : this.mToAddConfigList) {
                    addVendorAP(addConfig4);
                }
            }
            this.mClientModeImpl.resetPeriodicScanTimer();
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void removeApSimChange() {
        ArrayList<DefinedVendorAp> arrayList = this.mDefinedVedorApList;
        if (arrayList != null && arrayList.size() != 0) {
            this.mHandler.removeMessages(1);
            Iterator<DefinedVendorAp> it = this.mDefinedVedorApList.iterator();
            while (it.hasNext()) {
                DefinedVendorAp net = it.next();
                if (net.networkName != null) {
                    removeVendorApfromConfig(net.createWifiConfiguration());
                }
            }
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void removeVendorApfromConfig(WifiConfiguration toRemoveConfig) {
        List<WifiConfiguration> configs = this.mWifiInjector.getWifiConfigManager().getConfiguredNetworks();
        if (toRemoveConfig != null) {
            logi(TAG, "removeVendorApfromConfig: " + toRemoveConfig.SSID);
        } else {
            logi(TAG, "removeVendorApfromConfig ALL");
        }
        if (!(configs == null || configs.size() == 0)) {
            for (WifiConfiguration config : configs) {
                if ((toRemoveConfig == null || equalTwoConfig(config, toRemoveConfig)) && !config.isPasspoint() && config.semIsVendorSpecificSsid) {
                    config.semIsVendorSpecificSsid = false;
                    this.mWifiService.addOrUpdateNetwork(config, this.mContext.getPackageName());
                    this.mWifiService.removeNetwork(config.networkId, this.mContext.getPackageName());
                }
            }
        }
    }

    private String getMatchedNetworkNameUsingSIM() {
        this.mccmncOfSim = SystemProperties.get("gsm.sim.operator.numeric");
        Log.i(TAG, "getMatchedNetworkNameUsingSIM: SIM info: MCCMNC = ( " + this.mccmncOfSim + " )");
        if (TelephonyManager.getDefault().getSimState() != 5) {
            loge(TAG, "getMatchedNetworkNameUsingSIM: SIM satus is not SIM_STATE_READY");
            return "";
        } else if ("".equals(this.mccmncOfSim) || ",".equals(this.mccmncOfSim)) {
            loge(TAG, "getMatchedNetworkNameUsingSIM: gsm.sim.operator.numeric is empty or ,");
            if (TelephonyManager.getDefault().getSimState() == 5) {
                mNeedtoAddVendorAp = 2;
                ResultFailHandler resultFailHandler = this.mHandler;
                resultFailHandler.sendMessageDelayed(Message.obtain(resultFailHandler, 1), 3000);
            }
            return "";
        } else {
            StringBuilder mMatchedNetworkName2 = new StringBuilder();
            int generalNwInfoCount = this.mGeneralNwInfoList.size();
            for (int i = 0; i < generalNwInfoCount; i++) {
                String mccmncOfGeneralFile = this.mGeneralNwInfoList.get(i).mccmnc;
                String networkNameOfGeneralFile = this.mGeneralNwInfoList.get(i).networkName;
                if (mccmncOfGeneralFile != null && this.mccmncOfSim.contains(mccmncOfGeneralFile) && networkNameOfGeneralFile != null && !mMatchedNetworkName2.toString().contains(networkNameOfGeneralFile)) {
                    mMatchedNetworkName2.append(networkNameOfGeneralFile + ",");
                }
            }
            logi(TAG, "getMatchedNetworkNameUsingSIM: mMatchedNetworkName = ( " + mMatchedNetworkName2.toString() + " )");
            return mMatchedNetworkName2.toString();
        }
    }

    public void factoryReset() {
        logi(TAG, "factoryReset");
        removeVendorApfromConfig(null);
        if (mFilePathRemovedNwInfo.exists()) {
            logi(TAG, "delete:  removed_nw.conf ");
            mFilePathRemovedNwInfo.delete();
        }
        mNeedtoAddVendorAp = 1;
        this.mHandler.sendEmptyMessage(2);
    }

    public void startScan() {
        if (this.mWifiService.getWifiEnabledState() == 3) {
            this.mWifiService.startScan(this.mContext.getPackageName());
        }
    }

    public boolean equalTwoConfig(WifiConfiguration config1, WifiConfiguration config2) {
        if (config1.SSID != null && !config1.SSID.equals(config2.SSID)) {
            return false;
        }
        String keymgmt1 = StringUtil.makeString(config1.allowedKeyManagement, WifiConfiguration.KeyMgmt.strings);
        String keymgmt2 = StringUtil.makeString(config2.allowedKeyManagement, WifiConfiguration.KeyMgmt.strings);
        if (keymgmt1 != null && !keymgmt1.equals(keymgmt2)) {
            return false;
        }
        String eap1 = StringUtil.makeStringEapMethod(config1);
        String eap2 = StringUtil.makeStringEapMethod(config2);
        if (eap1 == null || eap1.equals(eap2)) {
            return true;
        }
        return false;
    }

    /* access modifiers changed from: protected */
    public void loge(String tag, String s) {
        Log.e(tag, s);
        this.mLocalLog.log(s);
    }

    /* access modifiers changed from: protected */
    public void logd(String tag, String s) {
        Log.d(tag, s);
        this.mLocalLog.log(s);
    }

    /* access modifiers changed from: protected */
    public void logv(String tag, String s) {
        Log.v(tag, s);
        this.mLocalLog.log(s);
    }

    /* access modifiers changed from: protected */
    public void logi(String tag, String s) {
        Log.i(tag, s);
        this.mLocalLog.log(s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("==== WifiDefaultAp Dump ====");
        this.mLocalLog.dump(fd, pw, args);
        WifiParsingCustomerFile.getInstance().dump(fd, pw, args);
    }

    /* access modifiers changed from: package-private */
    public static class GeneralNetworkInfo {
        private String mccmnc;
        private String networkName;
        private String subsetCode;

        GeneralNetworkInfo() {
        }

        public static GeneralNetworkInfo parsingNetworkBlock(BufferedReader in) {
            String line;
            GeneralNetworkInfo mNetwork = new GeneralNetworkInfo();
            while (true) {
                try {
                    if (!in.ready() || (line = in.readLine()) == null) {
                        break;
                    } else if (line.startsWith("}")) {
                        break;
                    } else {
                        Log.d(WifiDefaultApController.TAG, line);
                        mNetwork.parseLine(line);
                    }
                } catch (IOException e) {
                    return null;
                }
            }
            return mNetwork;
        }

        /* access modifiers changed from: package-private */
        public void parseLine(String line) {
            String line2 = line.trim();
            if (line2.startsWith("networkname")) {
                this.networkName = StringUtil.removeDoubleQuotes(line2.substring(line2.indexOf(61) + 1));
            }
            if (line2.startsWith("mccmnc")) {
                this.mccmnc = StringUtil.removeDoubleQuotes(line2.substring(line2.indexOf(61) + 1));
            }
            if (line2.startsWith("SubsetCode")) {
                this.subsetCode = StringUtil.removeDoubleQuotes(line2.substring(line2.indexOf(61) + 1));
            }
        }
    }

    /* access modifiers changed from: package-private */
    public static class DefinedVendorAp {
        private String SSID;
        private String eapMethod;
        private String hidden;
        private String keyMgmt;
        private String networkName;
        private String preSharedKey;

        DefinedVendorAp() {
        }

        public static DefinedVendorAp parsingNetworkBlock(BufferedReader in) {
            String line;
            DefinedVendorAp mAP = new DefinedVendorAp();
            while (true) {
                try {
                    if (!in.ready() || (line = in.readLine()) == null) {
                        break;
                    } else if (line.startsWith("}")) {
                        break;
                    } else {
                        Log.d(WifiDefaultApController.TAG, line);
                        mAP.parseLine(line);
                    }
                } catch (IOException e) {
                    return null;
                }
            }
            return mAP;
        }

        /* access modifiers changed from: package-private */
        public void parseLine(String line) {
            String line2 = line.trim();
            if (line2.startsWith("ssid")) {
                this.SSID = line2.substring(line2.indexOf(61) + 1);
            }
            if (line2.startsWith(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_HIDDEN)) {
                this.hidden = line2.substring(line2.indexOf(61) + 1);
            }
            if (line2.startsWith(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_KEY_MGMT)) {
                this.keyMgmt = line2.substring(line2.indexOf(61) + 1);
            }
            if (line2.startsWith(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_EAP)) {
                this.eapMethod = line2.substring(line2.indexOf(61) + 1);
            }
            if (line2.startsWith(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_PSK)) {
                this.preSharedKey = line2.substring(line2.indexOf(61) + 1);
            }
            if (line2.startsWith("networkname")) {
                this.networkName = StringUtil.removeDoubleQuotes(line2.substring(line2.indexOf(61) + 1));
            }
        }

        public WifiConfiguration createWifiConfiguration() {
            WifiConfiguration configuration = new WifiConfiguration();
            configuration.SSID = this.SSID;
            if ("1".equals(this.hidden)) {
                configuration.hiddenSSID = true;
            } else {
                configuration.hiddenSSID = false;
            }
            if ("WPA-EAP IEEE8021X".equals(this.keyMgmt)) {
                configuration.allowedKeyManagement.set(2);
                configuration.allowedKeyManagement.set(3);
            } else if ("WPA-PSK".equals(this.keyMgmt)) {
                configuration.allowedKeyManagement.set(1);
            } else if ("FT-EAP".equals(this.keyMgmt)) {
                configuration.allowedKeyManagement.set(2);
            } else if (WifiTransportLayerUtils.CATEGORY_PLAYSTORE_NONE.equals(this.keyMgmt)) {
                configuration.allowedKeyManagement.set(0);
            }
            if (!"".equals(this.preSharedKey)) {
                configuration.preSharedKey = this.preSharedKey;
            }
            if ("SIM".equals(this.eapMethod)) {
                configuration.enterpriseConfig.setEapMethod(4);
            } else if ("AKA".equals(this.eapMethod)) {
                configuration.enterpriseConfig.setEapMethod(5);
            } else if ("AKA'".equals(this.eapMethod)) {
                configuration.enterpriseConfig.setEapMethod(6);
            }
            configuration.status = 2;
            configuration.semIsVendorSpecificSsid = true;
            return configuration;
        }
    }

    /* access modifiers changed from: package-private */
    public static class RemovedVendorAp {
        private String mRemovedEap;
        private String mRemovedKeymgmt;
        private String mRemovedSSID;

        RemovedVendorAp() {
        }

        static RemovedVendorAp parsingNetworkBlock(BufferedReader in) {
            String line;
            RemovedVendorAp mAP = new RemovedVendorAp();
            while (true) {
                try {
                    if (!in.ready() || (line = in.readLine()) == null) {
                        break;
                    } else if (line.startsWith("}")) {
                        break;
                    } else {
                        Log.d(WifiDefaultApController.TAG, line);
                        mAP.parseLine(line);
                    }
                } catch (IOException e) {
                    return null;
                }
            }
            return mAP;
        }

        /* access modifiers changed from: package-private */
        public void parseLine(String line) {
            String line2 = line.trim();
            if (line2.startsWith("ssid")) {
                this.mRemovedSSID = line2.substring(line2.indexOf(61) + 1);
            }
            if (line2.startsWith(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_KEY_MGMT)) {
                this.mRemovedKeymgmt = line2.substring(line2.indexOf(61) + 1);
            }
            if (line2.startsWith(WifiBackupRestore.SupplicantBackupMigration.SUPPLICANT_KEY_EAP)) {
                this.mRemovedEap = line2.substring(line2.indexOf(61) + 1);
            }
        }
    }

    /* access modifiers changed from: private */
    public class ResultFailHandler extends Handler {
        public ResultFailHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i != 1) {
                if (i == 2) {
                    WifiDefaultApController.this.readDefaultApAndCopytoConfig();
                } else if (i != 3) {
                    Log.d(WifiDefaultApController.TAG, "unhandled message: " + msg);
                } else {
                    WifiDefaultApController.this.removeApSimChange();
                }
            } else if (WifiDefaultApController.this.mIsloadInternalDataCompleted) {
                if (WifiDefaultApController.mFilePathGeneralNwInfo.exists() && WifiDefaultApController.mFilePathGeneralNwInfo.length() > 0 && WifiDefaultApController.this.previousSimState == 5 && "".equals(WifiDefaultApController.this.mccmncOfSim)) {
                    WifiDefaultApController.this.logd(WifiDefaultApController.TAG, "Try again to get sim infomation");
                    int unused = WifiDefaultApController.mNeedtoAddVendorAp = 2;
                    WifiDefaultApController.this.readDefaultApAndCopytoConfig();
                }
                if (WifiDefaultApController.mNeedtoAddVendorAp == 0 || WifiDefaultApController.mNeedtoAddVendorAp == 1) {
                    WifiDefaultApController.this.readDefaultApAndCopytoConfig();
                }
            }
        }
    }
}
