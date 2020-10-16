package com.android.server.wifi;

import android.content.Context;
import android.net.NetworkKey;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.sec.enterprise.WifiPolicyCache;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.server.wifi.WifiCandidates;
import com.android.server.wifi.WifiNetworkSelector;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.util.ScanResultUtil;
import com.samsung.android.feature.SemCscFeature;
import com.samsung.android.net.wifi.OpBrandingLoader;
import com.samsung.android.server.wifi.mobilewips.framework.MobileWipsFrameworkService;
import com.sec.android.app.CscFeatureTagWifi;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class WifiNetworkSelector {
    private static final String CONFIG_SECURE_SVC_INTEGRATION = SemCscFeature.getInstance().getString(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_CONFIGSECURESVCINTEGRATION);
    private static final boolean CSC_SUPPORT_ASSOCIATED_NETWORK_SELECTION = SemCscFeature.getInstance().getBoolean(CscFeatureTagWifi.TAG_CSCFEATURE_WIFI_SUPPORTASSOCIATEDNETWORKSELECTION, false);
    private static final int ID_PREFIX = 42;
    private static final int ID_SUFFIX_MOD = 1000000;
    private static final long INVALID_TIME_STAMP = Long.MIN_VALUE;
    @VisibleForTesting
    public static final int LAST_USER_SELECTION_DECAY_TO_ZERO_MS = 28800000;
    @VisibleForTesting
    public static final int LAST_USER_SELECTION_SUFFICIENT_MS = 30000;
    public static final int LEGACY_CANDIDATE_SCORER_EXP_ID = 0;
    @VisibleForTesting
    public static final int MINIMUM_NETWORK_SELECTION_INTERVAL_MS = 6000;
    private static final int MIN_SCORER_EXP_ID = 42000000;
    public static final String PRESET_CANDIDATE_SCORER_NAME = "CompatibilityScorer";
    private static final String TAG = "WifiNetworkSelector";
    @VisibleForTesting
    public static final int WIFI_POOR_SCORE = 40;
    private static final OpBrandingLoader.Vendor mOpBranding = OpBrandingLoader.getInstance().getOpBranding();
    private final Map<String, WifiCandidates.CandidateScorer> mCandidateScorers = new ArrayMap();
    private final Clock mClock;
    private final List<Pair<ScanDetail, WifiConfiguration>> mConnectableNetworks = new ArrayList();
    private final Context mContext;
    private final boolean mEnableAutoJoinWhenAssociated;
    private final List<NetworkEvaluator> mEvaluators = new ArrayList(3);
    private List<ScanDetail> mFilteredNetworks = new ArrayList();
    private boolean mIsEnhancedOpenSupported;
    private boolean mIsEnhancedOpenSupportedInitialized = false;
    private long mLastNetworkSelectionTimeStamp = INVALID_TIME_STAMP;
    private final LocalLog mLocalLog;
    private final ScoringParams mScoringParams;
    private final int mStayOnNetworkMinimumRxRate;
    private final int mStayOnNetworkMinimumTxRate;
    private final WifiConfigManager mWifiConfigManager;
    private final WifiMetrics mWifiMetrics;
    private final WifiNative mWifiNative;
    private final WifiScoreCard mWifiScoreCard;

    public interface NetworkEvaluator {
        public static final int EVALUATOR_ID_CARRIER = 3;
        public static final int EVALUATOR_ID_PASSPOINT = 2;
        public static final int EVALUATOR_ID_SAVED = 0;
        public static final int EVALUATOR_ID_SCORED = 4;
        public static final int EVALUATOR_ID_SUGGESTION = 1;

        @Retention(RetentionPolicy.SOURCE)
        public @interface EvaluatorId {
        }

        public interface OnConnectableListener {
            void onConnectable(ScanDetail scanDetail, WifiConfiguration wifiConfiguration, int i);
        }

        WifiConfiguration evaluateNetworks(List<ScanDetail> list, WifiConfiguration wifiConfiguration, String str, boolean z, boolean z2, OnConnectableListener onConnectableListener, boolean z3);

        int getId();

        String getName();

        void update(List<ScanDetail> list);
    }

    private void localLog(String log) {
        this.mLocalLog.log(log);
    }

    private boolean isCurrentNetworkSufficient(WifiInfo wifiInfo, List<ScanDetail> scanDetails) {
        if (wifiInfo.getSupplicantState() != SupplicantState.COMPLETED) {
            localLog("No current connected network.");
            return false;
        }
        localLog("Current connected network: " + wifiInfo.getSSID() + " , ID: " + wifiInfo.getNetworkId());
        int currentRssi = wifiInfo.getRssi();
        boolean hasQualifiedRssi = currentRssi > this.mScoringParams.getSufficientRssi(wifiInfo.getFrequency());
        boolean hasActiveStream = wifiInfo.txSuccessRate > ((double) this.mStayOnNetworkMinimumTxRate) || wifiInfo.rxSuccessRate > ((double) this.mStayOnNetworkMinimumRxRate);
        if (!hasQualifiedRssi || !hasActiveStream) {
            WifiConfiguration network = this.mWifiConfigManager.getConfiguredNetwork(wifiInfo.getNetworkId());
            if (network == null) {
                localLog("Current network was removed.");
                return false;
            } else if (this.mWifiConfigManager.getLastSelectedNetwork() == network.networkId && this.mClock.getElapsedSinceBootMillis() - this.mWifiConfigManager.getLastSelectedTimeStamp() <= 30000) {
                localLog("Current network is recently user-selected.");
                return true;
            } else if (network.osu) {
                return true;
            } else {
                if (wifiInfo.isEphemeral()) {
                    localLog("Current network is an ephemeral one.");
                    return false;
                } else if (wifiInfo.is24GHz() && is5GHzNetworkAvailable(scanDetails)) {
                    localLog("Current network is 2.4GHz. 5GHz networks available.");
                    return false;
                } else if (!hasQualifiedRssi) {
                    localLog("Current network RSSI[" + currentRssi + "]-acceptable but not qualified.");
                    return false;
                } else if (WifiConfigurationUtil.isConfigForOpenNetwork(network)) {
                    localLog("Current network is a open one.");
                    return false;
                } else if (network.numNoInternetAccessReports <= 0 || network.noInternetAccessExpected) {
                    return true;
                } else {
                    localLog("Current network has [" + network.numNoInternetAccessReports + "] no-internet access reports.");
                    return false;
                }
            }
        } else {
            localLog("Stay on current network because of good RSSI and ongoing traffic");
            return true;
        }
    }

    private boolean is5GHzNetworkAvailable(List<ScanDetail> scanDetails) {
        for (ScanDetail detail : scanDetails) {
            if (detail.getScanResult().is5GHz()) {
                return true;
            }
        }
        return false;
    }

    private boolean isNetworkSelectionNeeded(List<ScanDetail> scanDetails, WifiInfo wifiInfo, boolean connected, boolean disconnected) {
        if (scanDetails.size() == 0) {
            localLog("Empty connectivity scan results. Skip network selection.");
            return false;
        } else if (connected) {
            if (!this.mEnableAutoJoinWhenAssociated) {
                localLog("Switching networks in connected state is not allowed. Skip network selection.");
                return false;
            }
            if (this.mLastNetworkSelectionTimeStamp != INVALID_TIME_STAMP) {
                long gap = this.mClock.getElapsedSinceBootMillis() - this.mLastNetworkSelectionTimeStamp;
                if (gap < 6000) {
                    localLog("Too short since last network selection: " + gap + " ms. Skip network selection.");
                    return false;
                }
            }
            if (isCurrentNetworkSufficient(wifiInfo, scanDetails)) {
                localLog("Current connected network already sufficient. Skip network selection.");
                return false;
            }
            localLog("Current connected network is not sufficient.");
            return true;
        } else if (disconnected) {
            return true;
        } else {
            localLog("ClientModeImpl is in neither CONNECTED nor DISCONNECTED state. Skip network selection.");
            return false;
        }
    }

    public static String toScanId(ScanResult scanResult) {
        if (scanResult == null) {
            return "NULL";
        }
        return String.format("%s:%s", scanResult.SSID, scanResult.BSSID);
    }

    public static String toNetworkString(WifiConfiguration network) {
        if (network == null) {
            return null;
        }
        return network.SSID + ":" + network.networkId;
    }

    public boolean isSignalTooWeak(ScanResult scanResult) {
        return scanResult.level < this.mScoringParams.getEntryRssi(scanResult.frequency);
    }

    private List<ScanDetail> filterScanResults(List<ScanDetail> scanDetails, HashSet<String> bssidBlacklist, boolean isConnected, String currentBssid) {
        StringBuffer noValidSsid;
        StringBuffer lowRssi;
        StringBuffer blacklistedBssid;
        ArrayList<NetworkKey> unscoredNetworks = new ArrayList<>();
        List<ScanDetail> validScanDetails = new ArrayList<>();
        StringBuffer noValidSsid2 = new StringBuffer();
        StringBuffer blacklistedBssid2 = new StringBuffer();
        StringBuffer lowRssi2 = new StringBuffer();
        StringBuffer maliciousBssid = new StringBuffer();
        StringBuffer mboAssociationDisallowedBssid = new StringBuffer();
        StringBuffer blockedByMDM = new StringBuffer();
        WifiPolicyCache mWifiPolicyCache = WifiPolicyCache.getInstance(this.mContext);
        boolean scanResultsHaveCurrentBssid = false;
        Iterator<ScanDetail> it = scanDetails.iterator();
        while (it.hasNext()) {
            ScanDetail scanDetail = it.next();
            ScanResult scanResult = scanDetail.getScanResult();
            if (TextUtils.isEmpty(scanResult.SSID)) {
                noValidSsid2.append(scanResult.BSSID);
                noValidSsid2.append(" / ");
                unscoredNetworks = unscoredNetworks;
            } else {
                if (scanResult.BSSID.equals(currentBssid)) {
                    scanResultsHaveCurrentBssid = true;
                }
                String scanId = toScanId(scanResult);
                if (bssidBlacklist.contains(scanResult.BSSID)) {
                    blacklistedBssid2.append(scanId);
                    blacklistedBssid2.append(" / ");
                    noValidSsid = noValidSsid2;
                    blacklistedBssid = blacklistedBssid2;
                    lowRssi = lowRssi2;
                } else {
                    blacklistedBssid = blacklistedBssid2;
                    if (isSignalTooWeak(scanResult)) {
                        lowRssi2.append(scanId);
                        lowRssi2.append("(");
                        lowRssi2.append(scanResult.is24GHz() ? "2.4GHz" : "5GHz");
                        lowRssi2.append(")");
                        lowRssi2.append(scanResult.level);
                        lowRssi2.append(" / ");
                        noValidSsid = noValidSsid2;
                        lowRssi = lowRssi2;
                    } else {
                        MobileWipsFrameworkService mwfs = MobileWipsFrameworkService.getInstance();
                        if (mwfs != null) {
                            lowRssi = lowRssi2;
                            noValidSsid = noValidSsid2;
                            if (mwfs.checkMWIPS(scanResult.BSSID, scanResult.frequency)) {
                                maliciousBssid.append(scanId);
                                maliciousBssid.append(" / ");
                            }
                        } else {
                            noValidSsid = noValidSsid2;
                            lowRssi = lowRssi2;
                        }
                        NetworkDetail networkDetail = scanDetail.getNetworkDetail();
                        if (networkDetail != null) {
                            if (networkDetail.getMboAssociationDisallowedReasonCode() != -1) {
                                mboAssociationDisallowedBssid.append(scanId);
                                mboAssociationDisallowedBssid.append("(");
                                mboAssociationDisallowedBssid.append(networkDetail.getMboAssociationDisallowedReasonCode());
                                mboAssociationDisallowedBssid.append(")");
                                mboAssociationDisallowedBssid.append(" / ");
                            }
                        }
                        if (!mWifiPolicyCache.isNetworkAllowed(ScanResultUtil.createNetworkFromScanResult(scanResult), false)) {
                            blockedByMDM.append(scanId);
                            blockedByMDM.append(" / ");
                        } else {
                            validScanDetails.add(scanDetail);
                        }
                    }
                }
                unscoredNetworks = unscoredNetworks;
                scanResultsHaveCurrentBssid = scanResultsHaveCurrentBssid;
                it = it;
                blacklistedBssid2 = blacklistedBssid;
                lowRssi2 = lowRssi;
                noValidSsid2 = noValidSsid;
            }
        }
        if (!isConnected || scanResultsHaveCurrentBssid) {
            if (noValidSsid2.length() != 0) {
                localLog("Networks filtered out due to invalid SSID: " + ((Object) noValidSsid2));
            }
            if (blacklistedBssid2.length() != 0) {
                localLog("Networks filtered out due to blacklist: " + ((Object) blacklistedBssid2));
            }
            if (lowRssi2.length() != 0) {
                localLog("Networks filtered out due to low signal strength: " + ((Object) lowRssi2));
            }
            if (maliciousBssid.length() != 0) {
                localLog("Networks filtered out due to malicious: " + ((Object) maliciousBssid));
            }
            if (mboAssociationDisallowedBssid.length() != 0) {
                localLog("Networks filtered out due to MBO association disallowed: " + ((Object) mboAssociationDisallowedBssid));
            }
            if (blockedByMDM.length() != 0) {
                localLog("Networks filtered out due to MDM policy: " + ((Object) blockedByMDM));
            }
            return validScanDetails;
        }
        localLog("Current connected BSSID " + currentBssid + " is not in the scan results. Skip network selection.");
        validScanDetails.clear();
        return validScanDetails;
    }

    private boolean isEnhancedOpenSupported() {
        if (this.mIsEnhancedOpenSupportedInitialized) {
            return this.mIsEnhancedOpenSupported;
        }
        boolean z = true;
        this.mIsEnhancedOpenSupportedInitialized = true;
        WifiNative wifiNative = this.mWifiNative;
        if ((wifiNative.getSupportedFeatureSet(wifiNative.getClientInterfaceName()) & 536870912) == 0) {
            z = false;
        }
        this.mIsEnhancedOpenSupported = z;
        return this.mIsEnhancedOpenSupported;
    }

    public List<ScanDetail> getFilteredScanDetailsForOpenUnsavedNetworks() {
        List<ScanDetail> openUnsavedNetworks = new ArrayList<>();
        boolean enhancedOpenSupported = isEnhancedOpenSupported();
        for (ScanDetail scanDetail : this.mFilteredNetworks) {
            ScanResult scanResult = scanDetail.getScanResult();
            if (ScanResultUtil.isScanResultForOpenNetwork(scanResult) && ((!ScanResultUtil.isScanResultForOweNetwork(scanResult) || enhancedOpenSupported) && this.mWifiConfigManager.getConfiguredNetworkForScanDetailAndCache(scanDetail) == null)) {
                openUnsavedNetworks.add(scanDetail);
            }
        }
        return openUnsavedNetworks;
    }

    public List<ScanDetail> getFilteredScanDetailsForCarrierUnsavedNetworks(CarrierNetworkConfig carrierConfig) {
        List<ScanDetail> carrierUnsavedNetworks = new ArrayList<>();
        for (ScanDetail scanDetail : this.mFilteredNetworks) {
            ScanResult scanResult = scanDetail.getScanResult();
            if (ScanResultUtil.isScanResultForEapNetwork(scanResult) && carrierConfig.isCarrierNetwork(scanResult.SSID) && this.mWifiConfigManager.getConfiguredNetworkForScanDetailAndCache(scanDetail) == null) {
                carrierUnsavedNetworks.add(scanDetail);
            }
        }
        return carrierUnsavedNetworks;
    }

    public List<Pair<ScanDetail, WifiConfiguration>> getConnectableScanDetails() {
        return this.mConnectableNetworks;
    }

    public boolean setUserConnectChoice(int netId) {
        localLog("userSelectNetwork: network ID=" + netId);
        WifiConfiguration selected = this.mWifiConfigManager.getConfiguredNetwork(netId);
        if (selected == null || selected.SSID == null) {
            localLog("userSelectNetwork: Invalid configuration with nid=" + netId);
            return false;
        }
        if (!selected.getNetworkSelectionStatus().isNetworkEnabled()) {
            this.mWifiConfigManager.updateNetworkSelectionStatus(netId, 0);
        }
        return setLegacyUserConnectChoice(selected);
    }

    private boolean setLegacyUserConnectChoice(WifiConfiguration selected) {
        Iterator<WifiConfiguration> it;
        List<WifiConfiguration> configuredNetworks;
        Iterator<WifiConfiguration> it2;
        List<WifiConfiguration> configuredNetworks2;
        boolean change = false;
        String key = selected.configKey();
        long currentTime = this.mClock.getWallClockMillis();
        List<WifiConfiguration> configuredNetworks3 = this.mWifiConfigManager.getConfiguredNetworks();
        Iterator<WifiConfiguration> it3 = configuredNetworks3.iterator();
        while (it3.hasNext()) {
            WifiConfiguration network = it3.next();
            WifiConfiguration.NetworkSelectionStatus status = network.getNetworkSelectionStatus();
            if (network.networkId == selected.networkId) {
                configuredNetworks = configuredNetworks3;
                it = it3;
            } else if (key.equals(network.configKey())) {
                configuredNetworks = configuredNetworks3;
                it = it3;
            } else {
                if (OpBrandingLoader.Vendor.ATT == mOpBranding) {
                    WifiConfiguration connectChoice = this.mWifiConfigManager.getConfiguredNetwork(status.getConnectChoice());
                    if (connectChoice == null || !connectChoice.isOpenNetwork()) {
                        configuredNetworks2 = configuredNetworks3;
                        it2 = it3;
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("[ATT] Remove user selection preference of ");
                        sb.append(status.getConnectChoice());
                        sb.append(" Set Time: ");
                        configuredNetworks2 = configuredNetworks3;
                        it2 = it3;
                        sb.append(status.getConnectChoiceTimestamp());
                        sb.append(" from ");
                        sb.append(network.SSID);
                        sb.append(" : ");
                        sb.append(network.networkId);
                        localLog(sb.toString());
                        this.mWifiConfigManager.clearNetworkConnectChoice(network.networkId);
                        change = true;
                    }
                    if (selected.isOpenNetwork()) {
                        localLog("[ATT] Do not setNetworkConnectChoice because an open network is selected");
                        configuredNetworks3 = configuredNetworks2;
                        it3 = it2;
                    }
                } else {
                    configuredNetworks2 = configuredNetworks3;
                    it2 = it3;
                }
                if (status.getSeenInLastQualifiedNetworkSelection() && !key.equals(status.getConnectChoice())) {
                    localLog("Add key: " + key + " Set Time: " + currentTime + " to " + toNetworkString(network));
                    this.mWifiConfigManager.setNetworkConnectChoice(network.networkId, key, currentTime);
                    change = true;
                }
                configuredNetworks3 = configuredNetworks2;
                it3 = it2;
            }
            if (status.getConnectChoice() != null) {
                localLog("Remove user selection preference of " + status.getConnectChoice() + " Set Time: " + status.getConnectChoiceTimestamp() + " from " + network.SSID + " : " + network.networkId);
                this.mWifiConfigManager.clearNetworkConnectChoice(network.networkId);
                change = true;
                key = key;
                configuredNetworks3 = configuredNetworks;
                it3 = it;
                currentTime = currentTime;
            } else {
                configuredNetworks3 = configuredNetworks;
                it3 = it;
            }
        }
        return change;
    }

    private void updateConfiguredNetworks() {
        List<WifiConfiguration> configuredNetworks = this.mWifiConfigManager.getConfiguredNetworks();
        if (configuredNetworks.size() == 0) {
            localLog("No configured networks.");
            return;
        }
        StringBuffer sbuf = new StringBuffer();
        for (WifiConfiguration network : configuredNetworks) {
            this.mWifiConfigManager.tryEnableNetwork(network.networkId);
            this.mWifiConfigManager.clearNetworkCandidateScanResult(network.networkId);
            WifiConfiguration.NetworkSelectionStatus status = network.getNetworkSelectionStatus();
            if (!status.isNetworkEnabled()) {
                sbuf.append("  ");
                sbuf.append(toNetworkString(network));
                sbuf.append(" ");
                for (int index = 1; index < 21; index++) {
                    int count = status.getDisableReasonCounter(index);
                    if (count > 0) {
                        sbuf.append("reason=");
                        sbuf.append(WifiConfiguration.NetworkSelectionStatus.getNetworkDisableReasonString(index));
                        sbuf.append(", count=");
                        sbuf.append(count);
                        sbuf.append("; ");
                    }
                }
                sbuf.append("\n");
            }
        }
        if (sbuf.length() > 0) {
            localLog("Disabled configured networks:");
            localLog(sbuf.toString());
        }
    }

    /* JADX INFO: Multiple debug info for r7v4 int: [D('numConnectChoice' int), D('tempStatus' android.net.wifi.WifiConfiguration$NetworkSelectionStatus)] */
    private WifiConfiguration overrideCandidateWithUserConnectChoice(WifiConfiguration candidate) {
        boolean hasQualifiedRssi;
        WifiConfiguration connectChoice;
        WifiConfiguration tempConfig = (WifiConfiguration) Preconditions.checkNotNull(candidate);
        ScanResult scanResultCandidate = candidate.getNetworkSelectionStatus().getCandidate();
        int numConnectChoice = 0;
        while (true) {
            hasQualifiedRssi = true;
            if (tempConfig.getNetworkSelectionStatus().getConnectChoice() == null) {
                break;
            }
            String key = tempConfig.getNetworkSelectionStatus().getConnectChoice();
            if (!key.equals(tempConfig.configKey())) {
                int numConnectChoice2 = numConnectChoice + 1;
                if (numConnectChoice <= WifiConfigManager.CSC_DEFAULT_MAX_NETWORKS_FOR_CURRENT_USER) {
                    if (OpBrandingLoader.Vendor.ATT == mOpBranding && (connectChoice = this.mWifiConfigManager.getConfiguredNetwork(key)) != null && connectChoice.isOpenNetwork()) {
                        WifiConfiguration.NetworkSelectionStatus tempStatus = tempConfig.getNetworkSelectionStatus();
                        localLog("[ATT] Open network of connect choice..");
                        localLog("[ATT] While user choice adjust, clear user selection preference of " + tempStatus.getConnectChoice() + " Set Time: " + tempStatus.getConnectChoiceTimestamp() + " from " + tempConfig.SSID + " : " + tempConfig.networkId);
                        this.mWifiConfigManager.clearNetworkConnectChoice(tempConfig.networkId);
                        this.mWifiConfigManager.saveToStore(true);
                        break;
                    }
                    tempConfig = this.mWifiConfigManager.getConfiguredNetwork(key);
                    if (tempConfig == null) {
                        localLog("Connect choice: " + key + " has no corresponding saved config.");
                        break;
                    }
                    WifiConfiguration.NetworkSelectionStatus tempStatus2 = tempConfig.getNetworkSelectionStatus();
                    if (tempStatus2.getCandidate() != null && tempStatus2.isNetworkEnabled()) {
                        scanResultCandidate = tempStatus2.getCandidate();
                        candidate = tempConfig;
                    }
                    numConnectChoice = numConnectChoice2;
                } else {
                    WifiConfiguration.NetworkSelectionStatus tempStatus3 = tempConfig.getNetworkSelectionStatus();
                    localLog("Too long chain of connect choice..");
                    localLog("While user choice adjust, clear user selection preference of " + tempStatus3.getConnectChoice() + " Set Time: " + tempStatus3.getConnectChoiceTimestamp() + " from " + tempConfig.SSID + " : " + tempConfig.networkId);
                    this.mWifiConfigManager.clearNetworkConnectChoice(tempConfig.networkId);
                    this.mWifiConfigManager.saveToStore(true);
                    break;
                }
            } else {
                WifiConfiguration.NetworkSelectionStatus tempStatus4 = tempConfig.getNetworkSelectionStatus();
                localLog("Tries to self connect choice..");
                localLog("While user choice adjust, clear user selection preference of " + tempStatus4.getConnectChoice() + " Set Time: " + tempStatus4.getConnectChoiceTimestamp() + " from " + tempConfig.SSID + " : " + tempConfig.networkId);
                this.mWifiConfigManager.clearNetworkConnectChoice(tempConfig.networkId);
                this.mWifiConfigManager.saveToStore(true);
                break;
            }
        }
        if (candidate == candidate) {
            return candidate;
        }
        if (scanResultCandidate.level <= this.mScoringParams.getSufficientRssi(scanResultCandidate.frequency)) {
            hasQualifiedRssi = false;
        }
        if (hasQualifiedRssi) {
            localLog("After user selection adjustment, the final candidate is:" + toNetworkString(candidate) + " : " + scanResultCandidate.BSSID);
            this.mWifiMetrics.setNominatorForNetwork(candidate.networkId, 8);
            return candidate;
        }
        localLog("After user selection adjustment, the final candidate is not changed due to low rssi");
        return candidate;
    }

    /* JADX INFO: Multiple debug info for r0v19 int: [D('selectedNetworkId' int), D('legacySelectedNetworkId' int)] */
    public WifiConfiguration selectNetwork(List<ScanDetail> scanDetails, HashSet<String> bssidBlacklist, WifiInfo wifiInfo, boolean connected, boolean disconnected, boolean untrustedNetworkAllowed, boolean bluetoothConnected) {
        int legacySelectedNetworkId;
        int networkId;
        ScanDetail scanDetail;
        this.mFilteredNetworks.clear();
        this.mConnectableNetworks.clear();
        if (scanDetails.size() == 0) {
            localLog("Empty connectivity scan result");
            return null;
        }
        WifiConfiguration currentNetwork = this.mWifiConfigManager.getConfiguredNetwork(wifiInfo.getNetworkId());
        String currentBssid = wifiInfo.getBSSID();
        if (!isNetworkSelectionNeeded(scanDetails, wifiInfo, connected, disconnected)) {
            return null;
        }
        updateConfiguredNetworks();
        for (NetworkEvaluator registeredEvaluator : this.mEvaluators) {
            registeredEvaluator.update(scanDetails);
        }
        this.mFilteredNetworks = filterScanResults(scanDetails, bssidBlacklist, connected && wifiInfo.score >= 40, currentBssid);
        if (this.mFilteredNetworks.size() == 0) {
            return null;
        }
        int lastUserSelectedNetworkId = this.mWifiConfigManager.getLastSelectedNetwork();
        double lastSelectionWeight = calculateLastSelectionWeight();
        ArraySet<Integer> mNetworkIds = new ArraySet<>();
        WifiCandidates wifiCandidates = new WifiCandidates(this.mWifiScoreCard);
        if (currentNetwork != null) {
            wifiCandidates.setCurrent(currentNetwork.networkId, currentBssid);
        }
        WifiConfiguration selectedNetwork = null;
        for (NetworkEvaluator registeredEvaluator2 : this.mEvaluators) {
            localLog("About to run " + registeredEvaluator2.getName() + " :");
            WifiConfiguration choice = registeredEvaluator2.evaluateNetworks(new ArrayList(this.mFilteredNetworks), currentNetwork, currentBssid, connected, untrustedNetworkAllowed, new NetworkEvaluator.OnConnectableListener(mNetworkIds, lastUserSelectedNetworkId, wifiCandidates, registeredEvaluator2, lastSelectionWeight) {
                /* class com.android.server.wifi.$$Lambda$WifiNetworkSelector$Z7htivbXF5AzGeTh0ZNbtUXC_0Q */
                private final /* synthetic */ ArraySet f$1;
                private final /* synthetic */ int f$2;
                private final /* synthetic */ WifiCandidates f$3;
                private final /* synthetic */ WifiNetworkSelector.NetworkEvaluator f$4;
                private final /* synthetic */ double f$5;

                {
                    this.f$1 = r2;
                    this.f$2 = r3;
                    this.f$3 = r4;
                    this.f$4 = r5;
                    this.f$5 = r6;
                }

                @Override // com.android.server.wifi.WifiNetworkSelector.NetworkEvaluator.OnConnectableListener
                public final void onConnectable(ScanDetail scanDetail, WifiConfiguration wifiConfiguration, int i) {
                    WifiNetworkSelector.this.lambda$selectNetwork$0$WifiNetworkSelector(this.f$1, this.f$2, this.f$3, this.f$4, this.f$5, scanDetail, wifiConfiguration, i);
                }
            }, bluetoothConnected);
            if (choice != null && !mNetworkIds.contains(Integer.valueOf(choice.networkId))) {
                Log.wtf(TAG, registeredEvaluator2.getName() + " failed to report choice with noConnectibleListener");
            }
            if (selectedNetwork != null || choice == null) {
                selectedNetwork = selectedNetwork;
            } else {
                localLog(registeredEvaluator2.getName() + " selects " + toNetworkString(choice));
                selectedNetwork = choice;
            }
            mNetworkIds = mNetworkIds;
            wifiCandidates = wifiCandidates;
        }
        WifiConfiguration selectedNetwork2 = selectedNetwork;
        WifiCandidates wifiCandidates2 = wifiCandidates;
        ArraySet<Integer> mNetworkIds2 = mNetworkIds;
        if (this.mConnectableNetworks.size() != wifiCandidates2.size()) {
            localLog("Connectable: " + this.mConnectableNetworks.size() + " Candidates: " + wifiCandidates2.size());
        }
        Collection<Collection<WifiCandidates.Candidate>> groupedCandidates = wifiCandidates2.getGroupedCandidates();
        for (Collection<WifiCandidates.Candidate> group : groupedCandidates) {
            WifiCandidates.Candidate best = null;
            for (WifiCandidates.Candidate candidate : group) {
                if (best == null || candidate.getEvaluatorId() < best.getEvaluatorId() || (candidate.getEvaluatorId() == best.getEvaluatorId() && candidate.getEvaluatorScore() > best.getEvaluatorScore())) {
                    best = candidate;
                }
            }
            if (!(best == null || (scanDetail = best.getScanDetail()) == null)) {
                this.mWifiConfigManager.setNetworkCandidateScanResult(best.getNetworkConfigId(), scanDetail.getScanResult(), best.getEvaluatorScore());
            }
        }
        ArrayMap<Integer, Integer> experimentNetworkSelections = new ArrayMap<>();
        if (selectedNetwork2 == null) {
            legacySelectedNetworkId = -1;
        } else {
            legacySelectedNetworkId = selectedNetwork2.networkId;
        }
        boolean legacyOverrideWanted = true;
        WifiCandidates.CandidateScorer activeScorer = getActiveCandidateScorer();
        Iterator<WifiCandidates.CandidateScorer> it = this.mCandidateScorers.values().iterator();
        int selectedNetworkId = legacySelectedNetworkId;
        while (it.hasNext()) {
            WifiCandidates.CandidateScorer candidateScorer = it.next();
            try {
                WifiCandidates.ScoredCandidate choice2 = wifiCandidates2.choose(candidateScorer);
                wifiCandidates2 = wifiCandidates2;
                if (choice2.candidateKey == null) {
                    networkId = -1;
                } else {
                    networkId = choice2.candidateKey.networkId;
                }
                String chooses = " would choose ";
                if (candidateScorer == activeScorer) {
                    chooses = " chooses ";
                    legacyOverrideWanted = candidateScorer.userConnectChoiceOverrideWanted();
                    selectedNetworkId = networkId;
                }
                String id = candidateScorer.getIdentifier();
                int expid = experimentIdFromIdentifier(id);
                localLog(id + chooses + networkId + " score " + choice2.value + "+/-" + choice2.err + " expid " + expid);
                experimentNetworkSelections.put(Integer.valueOf(expid), Integer.valueOf(networkId));
                it = it;
                selectedNetworkId = selectedNetworkId;
                legacyOverrideWanted = legacyOverrideWanted;
                selectedNetwork2 = selectedNetwork2;
                currentNetwork = currentNetwork;
                mNetworkIds2 = mNetworkIds2;
            } catch (RuntimeException e) {
                wifiCandidates2 = wifiCandidates2;
                Log.wtf(TAG, "Exception running a CandidateScorer", e);
                it = it;
                selectedNetwork2 = selectedNetwork2;
                currentNetwork = currentNetwork;
                mNetworkIds2 = mNetworkIds2;
            }
        }
        int activeExperimentId = activeScorer == null ? 0 : experimentIdFromIdentifier(activeScorer.getIdentifier());
        experimentNetworkSelections.put(0, Integer.valueOf(legacySelectedNetworkId));
        for (Map.Entry<Integer, Integer> entry : experimentNetworkSelections.entrySet()) {
            int experimentId = entry.getKey().intValue();
            if (experimentId != activeExperimentId) {
                this.mWifiMetrics.logNetworkSelectionDecision(experimentId, activeExperimentId, selectedNetworkId == entry.getValue().intValue(), groupedCandidates.size());
                experimentNetworkSelections = experimentNetworkSelections;
            }
        }
        WifiConfiguration selectedNetwork3 = this.mWifiConfigManager.getConfiguredNetwork(selectedNetworkId);
        if (selectedNetwork3 == null || !legacyOverrideWanted) {
            return selectedNetwork3;
        }
        WifiConfiguration selectedNetwork4 = overrideCandidateWithUserConnectChoice(selectedNetwork3);
        this.mLastNetworkSelectionTimeStamp = this.mClock.getElapsedSinceBootMillis();
        return selectedNetwork4;
    }

    public /* synthetic */ void lambda$selectNetwork$0$WifiNetworkSelector(ArraySet mNetworkIds, int lastUserSelectedNetworkId, WifiCandidates wifiCandidates, NetworkEvaluator registeredEvaluator, double lastSelectionWeight, ScanDetail scanDetail, WifiConfiguration config, int score) {
        if (config != null) {
            this.mConnectableNetworks.add(Pair.create(scanDetail, config));
            mNetworkIds.add(Integer.valueOf(config.networkId));
            if (config.networkId == lastUserSelectedNetworkId) {
                wifiCandidates.add(scanDetail, config, registeredEvaluator.getId(), score, lastSelectionWeight);
            } else {
                wifiCandidates.add(scanDetail, config, registeredEvaluator.getId(), score);
            }
            this.mWifiMetrics.setNominatorForNetwork(config.networkId, evaluatorIdToNominatorId(registeredEvaluator.getId()));
        }
    }

    private static int evaluatorIdToNominatorId(int evaluatorId) {
        if (evaluatorId == 0) {
            return 2;
        }
        if (evaluatorId == 1) {
            return 3;
        }
        if (evaluatorId == 2) {
            return 4;
        }
        if (evaluatorId == 3) {
            return 5;
        }
        if (evaluatorId == 4) {
            return 6;
        }
        Log.e(TAG, "UnrecognizedEvaluatorId" + evaluatorId);
        return 0;
    }

    private double calculateLastSelectionWeight() {
        if (this.mWifiConfigManager.getLastSelectedNetwork() != -1) {
            return Math.min(Math.max(1.0d - (((double) (this.mClock.getElapsedSinceBootMillis() - this.mWifiConfigManager.getLastSelectedTimeStamp())) / 2.88E7d), 0.0d), 1.0d);
        }
        return 0.0d;
    }

    private WifiCandidates.CandidateScorer getActiveCandidateScorer() {
        int i;
        WifiCandidates.CandidateScorer ans = this.mCandidateScorers.get(PRESET_CANDIDATE_SCORER_NAME);
        int overrideExperimentId = this.mScoringParams.getExperimentIdentifier();
        if (overrideExperimentId >= MIN_SCORER_EXP_ID) {
            Iterator<WifiCandidates.CandidateScorer> it = this.mCandidateScorers.values().iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                WifiCandidates.CandidateScorer candidateScorer = it.next();
                if (experimentIdFromIdentifier(candidateScorer.getIdentifier()) == overrideExperimentId) {
                    ans = candidateScorer;
                    break;
                }
            }
        }
        if (ans == null) {
            Log.wtf(TAG, "CompatibilityScorer is not registered!");
        }
        WifiMetrics wifiMetrics = this.mWifiMetrics;
        if (ans == null) {
            i = 0;
        } else {
            i = experimentIdFromIdentifier(ans.getIdentifier());
        }
        wifiMetrics.setNetworkSelectorExperimentId(i);
        return ans;
    }

    public void registerNetworkEvaluator(NetworkEvaluator evaluator) {
        this.mEvaluators.add((NetworkEvaluator) Preconditions.checkNotNull(evaluator));
    }

    public void registerCandidateScorer(WifiCandidates.CandidateScorer candidateScorer) {
        String name = ((WifiCandidates.CandidateScorer) Preconditions.checkNotNull(candidateScorer)).getIdentifier();
        if (name != null) {
            this.mCandidateScorers.put(name, candidateScorer);
        }
    }

    public void unregisterCandidateScorer(WifiCandidates.CandidateScorer candidateScorer) {
        String name = ((WifiCandidates.CandidateScorer) Preconditions.checkNotNull(candidateScorer)).getIdentifier();
        if (name != null) {
            this.mCandidateScorers.remove(name);
        }
    }

    public static int experimentIdFromIdentifier(String id) {
        return MIN_SCORER_EXP_ID + (((int) (((long) id.hashCode()) & 2147483647L)) % ID_SUFFIX_MOD);
    }

    WifiNetworkSelector(Context context, WifiScoreCard wifiScoreCard, ScoringParams scoringParams, WifiConfigManager configManager, Clock clock, LocalLog localLog, WifiMetrics wifiMetrics, WifiNative wifiNative) {
        this.mContext = context;
        this.mWifiConfigManager = configManager;
        this.mClock = clock;
        this.mWifiScoreCard = wifiScoreCard;
        this.mScoringParams = scoringParams;
        this.mLocalLog = localLog;
        this.mWifiMetrics = wifiMetrics;
        this.mWifiNative = wifiNative;
        this.mEnableAutoJoinWhenAssociated = CSC_SUPPORT_ASSOCIATED_NETWORK_SELECTION;
        this.mStayOnNetworkMinimumTxRate = context.getResources().getInteger(17694994);
        this.mStayOnNetworkMinimumRxRate = context.getResources().getInteger(17694993);
    }
}
