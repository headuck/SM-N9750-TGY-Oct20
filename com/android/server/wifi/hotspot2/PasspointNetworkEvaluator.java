package com.android.server.wifi.hotspot2;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Pair;
import com.android.server.wifi.CarrierNetworkConfig;
import com.android.server.wifi.NetworkUpdateResult;
import com.android.server.wifi.ScanDetail;
import com.android.server.wifi.WifiConfigManager;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiNetworkSelector;
import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.Constants;
import com.android.server.wifi.hotspot2.anqp.HSFriendlyNameElement;
import com.android.server.wifi.hotspot2.anqp.HSWanMetricsElement;
import com.android.server.wifi.util.RilUtil;
import com.android.server.wifi.util.ScanResultUtil;
import com.android.server.wifi.util.StringUtil;
import com.android.server.wifi.util.TelephonyUtil;
import com.samsung.android.net.wifi.OpBrandingLoader;
import com.samsung.android.server.wifi.SemSarManager;
import com.samsung.android.server.wifi.dqa.SemWifiIssueDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PasspointNetworkEvaluator implements WifiNetworkSelector.NetworkEvaluator {
    private static final String NAME = "PasspointNetworkEvaluator";
    private static final String SEC_FRIENDLY_NAME = "Samsung Hotspot2.0 Profile";
    private static final String VENDOR_FRIENDLY_NAME = "Vendor Hotspot2.0 Profile";
    private static String mImsi = "00101*";
    private static boolean mIsEnabledNetworkForOpenRoaming = false;
    private static final OpBrandingLoader.Vendor mOpBranding = OpBrandingLoader.getInstance().getOpBranding();
    private final CarrierNetworkConfig mCarrierNetworkConfig;
    private final Context mContext;
    private final LocalLog mLocalLog;
    private final PasspointManager mPasspointManager;
    private SubscriptionManager mSubscriptionManager;
    private TelephonyManager mTelephonyManager;
    private final WifiConfigManager mWifiConfigManager;
    private final WifiInjector mWifiInjector;

    /* access modifiers changed from: private */
    public class PasspointNetworkCandidate {
        PasspointMatch mMatchStatus;
        PasspointProvider mProvider;
        ScanDetail mScanDetail;

        PasspointNetworkCandidate(PasspointProvider provider, PasspointMatch matchStatus, ScanDetail scanDetail) {
            this.mProvider = provider;
            this.mMatchStatus = matchStatus;
            this.mScanDetail = scanDetail;
        }
    }

    public PasspointNetworkEvaluator(Context context, PasspointManager passpointManager, WifiConfigManager wifiConfigManager, LocalLog localLog, CarrierNetworkConfig carrierNetworkConfig, WifiInjector wifiInjector, SubscriptionManager subscriptionManager) {
        this.mContext = context;
        this.mPasspointManager = passpointManager;
        this.mWifiConfigManager = wifiConfigManager;
        this.mLocalLog = localLog;
        this.mCarrierNetworkConfig = carrierNetworkConfig;
        this.mWifiInjector = wifiInjector;
        this.mSubscriptionManager = subscriptionManager;
    }

    private TelephonyManager getTelephonyManager() {
        if (this.mTelephonyManager == null) {
            this.mTelephonyManager = this.mWifiInjector.makeTelephonyManager();
        }
        return this.mTelephonyManager;
    }

    @Override // com.android.server.wifi.WifiNetworkSelector.NetworkEvaluator
    public int getId() {
        return 2;
    }

    @Override // com.android.server.wifi.WifiNetworkSelector.NetworkEvaluator
    public String getName() {
        return NAME;
    }

    @Override // com.android.server.wifi.WifiNetworkSelector.NetworkEvaluator
    public void update(List<ScanDetail> list) {
    }

    @Override // com.android.server.wifi.WifiNetworkSelector.NetworkEvaluator
    public WifiConfiguration evaluateNetworks(List<ScanDetail> scanDetails, WifiConfiguration currentNetwork, String currentBssid, boolean connected, boolean untrustedNetworkAllowed, WifiNetworkSelector.NetworkEvaluator.OnConnectableListener onConnectableListener, boolean bluetoothConnected) {
        PasspointConfiguration passpointconfig;
        HSWanMetricsElement wm;
        if (!this.mPasspointManager.isPasspointEnabled()) {
            localLog("Passpoint is disabled");
            return null;
        }
        this.mPasspointManager.sweepCache();
        List<ScanDetail> filteredScanDetails = (List) scanDetails.stream().filter($$Lambda$PasspointNetworkEvaluator$GeomGkeNP2MEelBL59RV_0T1M8.INSTANCE).filter(new Predicate() {
            /* class com.android.server.wifi.hotspot2.$$Lambda$PasspointNetworkEvaluator$sa28nwdP8mGfetynNLsyMBO7E8 */

            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PasspointNetworkEvaluator.this.lambda$evaluateNetworks$1$PasspointNetworkEvaluator((ScanDetail) obj);
            }
        }).collect(Collectors.toList());
        createEphemeralProfileForMatchingAp(filteredScanDetails);
        List<PasspointNetworkCandidate> candidateList = new ArrayList<>();
        for (ScanDetail scanDetail : filteredScanDetails) {
            ScanResult scanResult = scanDetail.getScanResult();
            Map<Constants.ANQPElementType, ANQPElement> anqpElements = this.mPasspointManager.getANQPElements(scanDetail.getScanResult());
            if (OpBrandingLoader.Vendor.SKT == mOpBranding) {
                if (!scanDetail.getNetworkDetail().isInternet()) {
                    localLog("Passpoint network, ScanDetail: " + scanDetail + " has internet accessibility false. Skip");
                } else if (anqpElements != null && anqpElements.size() > 0 && (wm = (HSWanMetricsElement) anqpElements.get(Constants.ANQPElementType.HSWANMetrics)) != null && wm.isCapped()) {
                    this.mPasspointManager.forceRequestAnqp(scanDetail.getScanResult());
                    localLog("Passpoint network, ScanDetail: " + scanDetail + " has WAN capped. Skip and Request ANQP for update");
                }
            }
            WifiConfiguration configuredNetwork = this.mWifiConfigManager.getConfiguredNetworkForScanDetailAndCache(scanDetail, true);
            if (configuredNetwork != null) {
                if (configuredNetwork.getNetworkSelectionStatus().isDisabledByReason(19)) {
                    localLog("Passpoint network " + configuredNetwork.configKey() + " is unstable AP. Skip");
                } else if (!RilUtil.isWifiOnly(this.mContext) && !SemSarManager.isRfTestMode() && ((scanResult.is24GHz() && scanResult.level < configuredNetwork.entryRssi24GHz) || (scanResult.is5GHz() && scanResult.level < configuredNetwork.entryRssi5GHz))) {
                    localLog("Passpoint network " + configuredNetwork.configKey() + " has entryRssi (" + configuredNetwork.entryRssi24GHz + ", " + configuredNetwork.entryRssi5GHz + "). And current scan result has freq = " + scanResult.frequency + " and rssi = " + scanResult.level + ". Skip");
                }
            }
            Pair<PasspointProvider, PasspointMatch> bestProvider = this.mPasspointManager.matchProvider(scanResult);
            if (bestProvider != null && ((!((PasspointProvider) bestProvider.first).isSimCredential() || TelephonyUtil.isSimPresent(this.mSubscriptionManager)) && (passpointconfig = ((PasspointProvider) bestProvider.first).getConfig()) != null)) {
                if (((PasspointProvider) bestProvider.first).isSimCredential() && passpointconfig.getHomeSp().isVendorSpecificSsid() && !this.mPasspointManager.isVendorSimUseable()) {
                    localLog("Passpoint bestProvider has no MCC, MNC of vndor. Skip");
                } else if (this.mWifiConfigManager.getAutoConnectCarrierApEnabled() || !passpointconfig.getHomeSp().isVendorSpecificSsid() || RilUtil.isMptcpEnabled(this.mContext)) {
                    candidateList.add(new PasspointNetworkCandidate((PasspointProvider) bestProvider.first, (PasspointMatch) bestProvider.second, scanDetail));
                } else {
                    localLog("Passpoint bestProvider has a PasspointConfiguration from carrier. autoConnectCarrierAp is disabled. MPTCP is disabled. Skip");
                }
            }
        }
        if (candidateList.isEmpty()) {
            localLog("No suitable Passpoint network found");
            return null;
        }
        PasspointNetworkCandidate bestNetwork = findBestNetwork(candidateList, currentNetwork == null ? null : currentNetwork.SSID, bluetoothConnected);
        if (bestNetwork == null) {
            localLog("Passpoint's bestNetwork is null");
            return null;
        } else if (currentNetwork == null || !TextUtils.equals(currentNetwork.SSID, ScanResultUtil.createQuotedSSID(bestNetwork.mScanDetail.getSSID()))) {
            WifiConfiguration config = createWifiConfigForProvider(bestNetwork);
            if (config != null) {
                onConnectableListener.onConnectable(bestNetwork.mScanDetail, config, 0);
                localLog("Passpoint network to connect to: " + config.SSID);
            }
            return config;
        } else {
            localLog("Staying with current Passpoint network " + currentNetwork.SSID);
            this.mWifiConfigManager.setNetworkCandidateScanResult(currentNetwork.networkId, bestNetwork.mScanDetail.getScanResult(), 0);
            this.mWifiConfigManager.updateScanDetailForNetwork(currentNetwork.networkId, bestNetwork.mScanDetail);
            onConnectableListener.onConnectable(bestNetwork.mScanDetail, currentNetwork, 0);
            return currentNetwork;
        }
    }

    public /* synthetic */ boolean lambda$evaluateNetworks$1$PasspointNetworkEvaluator(ScanDetail s) {
        if (!this.mWifiConfigManager.wasEphemeralNetworkDeleted(ScanResultUtil.createQuotedSSID(s.getScanResult().SSID))) {
            return true;
        }
        LocalLog localLog = this.mLocalLog;
        localLog.log("Ignoring disabled the SSID of Passpoint AP: " + WifiNetworkSelector.toScanId(s.getScanResult()));
        return false;
    }

    private void createEphemeralProfileForMatchingAp(List<ScanDetail> filteredScanDetails) {
        PasspointConfiguration carrierConfig;
        TelephonyManager telephonyManager = getTelephonyManager();
        if (telephonyManager == null || TelephonyUtil.getCarrierType(telephonyManager) != 0 || !this.mCarrierNetworkConfig.isCarrierEncryptionInfoAvailable()) {
            return;
        }
        if (!this.mPasspointManager.hasCarrierProvider(telephonyManager.createForSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId()).getSimOperator())) {
            int eapMethod = this.mPasspointManager.findEapMethodFromNAIRealmMatchedWithCarrier(filteredScanDetails);
            if (Utils.isCarrierEapMethod(eapMethod) && (carrierConfig = this.mPasspointManager.createEphemeralPasspointConfigForCarrier(eapMethod)) != null) {
                this.mPasspointManager.installEphemeralPasspointConfigForCarrier(carrierConfig);
            }
        }
    }

    private WifiConfiguration createWifiConfigForProvider(PasspointNetworkCandidate networkInfo) {
        WifiConfiguration.NetworkSelectionStatus status;
        Map<Constants.ANQPElementType, ANQPElement> anqpElements;
        HSFriendlyNameElement fne;
        WifiConfiguration config = networkInfo.mProvider.getWifiConfig();
        config.SSID = ScanResultUtil.createQuotedSSID(networkInfo.mScanDetail.getSSID());
        if (networkInfo.mMatchStatus == PasspointMatch.HomeProvider) {
            config.isHomeProviderNetwork = true;
        }
        PasspointConfiguration passpointconfig = networkInfo.mProvider.getConfig();
        boolean isOAuthEnabled = false;
        String oAuthProvider = null;
        oAuthProvider = null;
        if (passpointconfig != null && (isOAuthEnabled = passpointconfig.getHomeSp().isOAuthEnabled())) {
            oAuthProvider = passpointconfig.getHomeSp().getOAuthProvider();
        }
        WifiConfiguration existingNetwork = this.mWifiConfigManager.getConfiguredNetwork(config.configKey());
        if (existingNetwork != null) {
            WifiConfiguration.NetworkSelectionStatus status2 = existingNetwork.getNetworkSelectionStatus();
            if (isOAuthEnabled) {
                if (!hasOAuthProvider(oAuthProvider)) {
                    if (status2.isNetworkEnabled()) {
                        this.mWifiConfigManager.disableNetwork(existingNetwork.networkId, 1010);
                    }
                    mIsEnabledNetworkForOpenRoaming = false;
                    localLog("Current configuration for the Passpoint AP " + config.SSID + " does not have OAuth, skip this candidate");
                    return null;
                } else if (!mIsEnabledNetworkForOpenRoaming) {
                    if (!status2.isNetworkEnabled()) {
                        this.mWifiConfigManager.enableNetwork(existingNetwork.networkId, false, 1010);
                    }
                    mIsEnabledNetworkForOpenRoaming = true;
                }
            }
            if (status2.isNetworkEnabled() || this.mWifiConfigManager.tryEnableNetwork(existingNetwork.networkId)) {
                return existingNetwork;
            }
            localLog("Current configuration for the Passpoint AP " + config.SSID + " is disabled, skip this candidate");
            return null;
        }
        config.priority = this.mWifiConfigManager.increaseAndGetPriority();
        boolean networkMustBeEnabled = false;
        networkMustBeEnabled = false;
        networkMustBeEnabled = false;
        networkMustBeEnabled = false;
        networkMustBeEnabled = false;
        boolean isFriendlyNameUpdated = false;
        isFriendlyNameUpdated = false;
        if (passpointconfig != null) {
            config.semAutoReconnect = passpointconfig.getHomeSp().isAutoReconnectEnabled() ? 1 : 0;
            if (passpointconfig.getHomeSp().isVendorSpecificSsid() && passpointconfig.getCredential().getSimCredential() != null) {
                String imsiOfConfig = passpointconfig.getCredential().getSimCredential().getImsi();
                if (!TextUtils.isEmpty(imsiOfConfig) && !TextUtils.equals(mImsi, imsiOfConfig)) {
                    mImsi = imsiOfConfig;
                    networkMustBeEnabled = true;
                    localLog("IMSI of Passpoint config is changed. So network must be enabled");
                }
            }
            if (VENDOR_FRIENDLY_NAME.equals(config.providerFriendlyName) || SEC_FRIENDLY_NAME.equals(config.providerFriendlyName)) {
                String friendlyName = null;
                friendlyName = null;
                friendlyName = null;
                friendlyName = null;
                friendlyName = null;
                if (OpBrandingLoader.Vendor.SKT == mOpBranding && (anqpElements = this.mPasspointManager.getANQPElements(networkInfo.mScanDetail.getScanResult())) != null && anqpElements.size() > 0 && (fne = (HSFriendlyNameElement) anqpElements.get(Constants.ANQPElementType.HSFriendlyName)) != null && !fne.getNames().isEmpty()) {
                    friendlyName = fne.getNames().get(0).getText();
                }
                if (!TextUtils.isEmpty(friendlyName)) {
                    localLog("The Friendlyname of " + config.providerFriendlyName + " was replaced by " + friendlyName);
                    config.providerFriendlyName = friendlyName;
                    isFriendlyNameUpdated = true;
                } else if (!TextUtils.isEmpty(config.SSID)) {
                    localLog("The Friendlyname of " + config.providerFriendlyName + " was replaced by " + config.SSID);
                    config.providerFriendlyName = StringUtil.removeDoubleQuotes(config.SSID);
                    isFriendlyNameUpdated = true;
                }
            }
            if (isFriendlyNameUpdated) {
                passpointconfig.getHomeSp().setFriendlyName(config.providerFriendlyName);
                if (this.mPasspointManager.addOrUpdateProvider(passpointconfig, networkInfo.mProvider.getCreatorUid(), networkInfo.mProvider.getPackageName())) {
                    localLog("FriendlyName of Passpoint config updated to " + passpointconfig.getHomeSp().getFriendlyName());
                }
            }
        }
        if (networkInfo.mMatchStatus == PasspointMatch.HomeProvider) {
            config.isHomeProviderNetwork = true;
        }
        NetworkUpdateResult result = this.mWifiConfigManager.addOrUpdateNetwork(config, 1010);
        if (!result.isSuccess()) {
            localLog("Failed to add passpoint network");
            return null;
        } else if (config.semAutoReconnect == 0) {
            localLog("AutoReconnect of " + config.configKey() + " Passpoint network is disabled");
            return null;
        } else {
            WifiConfiguration savedConfig = this.mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
            if (networkMustBeEnabled || savedConfig == null || (status = savedConfig.getNetworkSelectionStatus()) == null || status.isNetworkEnabled() || !status.isDisabledByReason(19)) {
                if (isOAuthEnabled) {
                    SemWifiIssueDetector semWifiIssueDetector = WifiInjector.getInstance().getIssueDetector();
                    if (semWifiIssueDetector != null) {
                        Bundle args = new Bundle();
                        args.putString("fqdn", passpointconfig.getHomeSp().getFqdn());
                        semWifiIssueDetector.sendMessage(semWifiIssueDetector.obtainMessage(0, 106, 0, args));
                    }
                    if (!hasOAuthProvider(oAuthProvider)) {
                        localLog("Do not create WifiConfig, the user did not agree to use OpenRoaming.");
                        return null;
                    }
                }
                this.mWifiConfigManager.enableNetwork(result.getNetworkId(), false, 1010);
                this.mWifiConfigManager.setNetworkCandidateScanResult(result.getNetworkId(), networkInfo.mScanDetail.getScanResult(), 0);
                this.mWifiConfigManager.updateScanDetailForNetwork(result.getNetworkId(), networkInfo.mScanDetail);
                return this.mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
            }
            localLog("Selected Passpoint network is unstable AP");
            return null;
        }
    }

    private PasspointNetworkCandidate findBestNetwork(List<PasspointNetworkCandidate> networkList, String currentNetworkSsid, boolean bluetoothConnected) {
        PasspointNetworkCandidate bestCandidate = null;
        int bestScore = Integer.MIN_VALUE;
        StringBuffer scoreHistory = new StringBuffer();
        for (PasspointNetworkCandidate candidate : networkList) {
            ScanDetail scanDetail = candidate.mScanDetail;
            PasspointMatch match = candidate.mMatchStatus;
            localLog("Cnadidate Passpoint network : " + scanDetail.getNetworkDetail());
            int score = PasspointNetworkScore.calculateScore(match == PasspointMatch.HomeProvider, scanDetail, this.mPasspointManager.getANQPElements(scanDetail.getScanResult()), TextUtils.equals(currentNetworkSsid, ScanResultUtil.createQuotedSSID(scanDetail.getSSID())), scoreHistory, bluetoothConnected);
            PasspointConfiguration passpointconfig = candidate.mProvider.getConfig();
            if (passpointconfig != null) {
                boolean isOAuthEnabled = passpointconfig.getHomeSp().isOAuthEnabled();
                if (isOAuthEnabled) {
                    String oAuthProvider = passpointconfig.getHomeSp().getOAuthProvider();
                    if (this.mWifiConfigManager.getConfiguredNetwork(candidate.mProvider.getWifiConfig().configKey()) != null && isOAuthEnabled && !hasOAuthProvider(oAuthProvider)) {
                        localLog("Do not candidate as the Best Passpoint network, the user did not agree to use OpenRoaming.");
                    }
                }
            }
            if (score > bestScore) {
                bestCandidate = candidate;
                bestScore = score;
            }
        }
        if (scoreHistory.length() > 0) {
            localLog("\n" + scoreHistory.toString());
        }
        if (!(bestCandidate == null || bestCandidate.mScanDetail == null)) {
            localLog("Best Passpoint network " + bestCandidate.mScanDetail.getSSID() + " provided by " + bestCandidate.mProvider.getConfig().getHomeSp().getFqdn());
        }
        return bestCandidate;
    }

    private void localLog(String log) {
        this.mLocalLog.log(log);
    }

    private boolean hasOAuthProvider(String oAuthProvider) {
        String values = Settings.Global.getString(this.mContext.getContentResolver(), "sem_wifi_allowed_oauth_provider");
        if (TextUtils.isEmpty(values)) {
            return false;
        }
        if (!values.contains("[" + oAuthProvider + "]")) {
            return false;
        }
        return true;
    }
}
