package com.samsung.android.server.wifi.dqa;

import android.os.Bundle;
import android.util.Log;
import com.samsung.android.server.wifi.bigdata.WifiBigDataLogManager;
import java.util.ArrayList;
import java.util.Collection;

public class PatternWifiConnecting extends WifiIssuePattern {
    private static final int BASE_RSSI_CONDITION = -70;
    private static final int CATEGORY_ID_CAN_NOT_CHANGE_WIFI_STATE = 2;
    private static final int CATEGORY_ID_CONNECT_FAIL_ASSOC_REJECT = 5;
    private static final int CATEGORY_ID_CONNECT_FAIL_AUTH_FAIL = 6;
    private static final int CATEGORY_ID_CONNECT_FAIL_DHCP_REASON = 7;
    private static final int CATEGORY_ID_HIDL_PROBLEM = 9;
    private static final int CATEGORY_ID_SCAN_FAIL = 8;
    private static final int CATEGORY_ID_SYSTEM_PROBLEM = 1;
    private static final int CATEGORY_ID_UNSTABLE_AP = 4;
    private static final long MAX_DURATION_FOR_DETECTING_WIFI_ONOFF_ISSUE = 1500;
    private static final String TAG = "PatternWifiConnecting";
    private static final String UNKNOWN = "unknown";
    private String mBssid;
    private int mCategoryId;
    private int mFrequency;
    private int mHangReason;
    private int mKeyMgmt;
    private int mLastProceedMessage;
    private String mLastProceedState;
    private int mLastReportId;
    private ReportData mLastTriedToConnectReport;
    private long mLastUpdatedWifiStateTime;
    private int mNumAssociation;
    private String mOui;
    private String mPackageName;
    private int mReason;
    private int mRssi;
    private String mSsid;
    private int mSupplicantDisconnectCount;
    private final String mVersion = "0.6";

    public PatternWifiConnecting() {
        initValues();
    }

    @Override // com.samsung.android.server.wifi.dqa.WifiIssuePattern
    public Collection<Integer> getAssociatedKeys() {
        Collection<Integer> ret = new ArrayList<>();
        ret.add(7);
        ret.add(10);
        ret.add(201);
        ret.add(11);
        ret.add(Integer.valueOf((int) ReportIdKey.ID_SCAN_FAIL));
        return ret;
    }

    @Override // com.samsung.android.server.wifi.dqa.WifiIssuePattern
    public boolean isAssociated(int reportId, ReportData reportData) {
        this.mLastReportId = reportId;
        if (reportId != 7) {
            if (reportId != 201) {
                if (reportId == 401 || reportId == 10 || reportId == 11) {
                    return true;
                }
            } else if (reportData.mTime - this.mLastUpdatedWifiStateTime >= MAX_DURATION_FOR_DETECTING_WIFI_ONOFF_ISSUE || isApiCalledBySystemApk((String) getValue(reportData, ReportIdKey.KEY_CALL_BY, ""))) {
                this.mLastUpdatedWifiStateTime = reportData.mTime;
            } else {
                this.mLastUpdatedWifiStateTime = reportData.mTime;
                return true;
            }
            return false;
        }
        int counter = ((Integer) getValue(reportData, ReportIdKey.KEY_COUNT, (Object) -1)).intValue();
        Log.i(TAG, "isAssociated counter:" + counter);
        return counter != -1 ? counter >= 1 : ((Integer) getValue(reportData, "reason", -1)).intValue() != -1 ? true : true;
    }

    /* JADX WARNING: Removed duplicated region for block: B:102:0x0401  */
    @Override // com.samsung.android.server.wifi.dqa.WifiIssuePattern
    public boolean matches() {
        boolean issueDetected;
        boolean issueDetected2;
        String str;
        Integer num;
        String str2;
        String str3;
        String str4;
        Integer num2;
        int dhcpReason;
        ReportData scanFailReport;
        initValues();
        int i = this.mLastReportId;
        if (i == 7) {
            ReportData reportSystemProblem = getLastIndexOfData(7);
            if (reportSystemProblem != null) {
                this.mCategoryId = 1;
                this.mSupplicantDisconnectCount = ((Integer) getValue(reportSystemProblem, ReportIdKey.KEY_COUNT, (Object) 0)).intValue();
                this.mHangReason = ((Integer) getValue(reportSystemProblem, "reason", (Object) -1)).intValue();
                this.mLastProceedState = (String) getValue(reportSystemProblem, ReportIdKey.KEY_LAST_HANDLE_STATE, UNKNOWN);
                this.mLastProceedMessage = ((Integer) getValue(reportSystemProblem, ReportIdKey.KEY_PROCESS_MESSAGE, (Object) 0)).intValue();
                Log.i(TAG, "matched category id : " + this.mCategoryId);
                return true;
            }
        } else if (i == 17) {
            ReportData reportHidlProblem = getLastIndexOfData(17);
            if (reportHidlProblem != null) {
                this.mCategoryId = 9;
                this.mHangReason = ((Integer) getValue(reportHidlProblem, "reason", (Object) -1)).intValue();
                this.mLastProceedState = (String) getValue(reportHidlProblem, ReportIdKey.KEY_LAST_HANDLE_STATE, UNKNOWN);
                this.mLastProceedMessage = ((Integer) getValue(reportHidlProblem, ReportIdKey.KEY_PROCESS_MESSAGE, (Object) 0)).intValue();
                Log.i(TAG, "matched category id : " + this.mCategoryId);
                return true;
            }
        } else if (i == 10) {
            if (getLastIndexOfData(10) != null) {
                this.mCategoryId = 4;
                Log.i(TAG, "matched category id : " + this.mCategoryId);
                ReportData bigDataDiscReport = getLastIndexOfData(200);
                if (bigDataDiscReport != null) {
                    this.mRssi = ((Integer) getValue(bigDataDiscReport, ReportIdKey.KEY_RSSI, (Object) -200)).intValue();
                    this.mOui = (String) getValue(bigDataDiscReport, ReportIdKey.KEY_OUI, UNKNOWN);
                    this.mKeyMgmt = ((Integer) getValue(bigDataDiscReport, ReportIdKey.KEY_WPA_SECURE_TYPE, (Object) 0)).intValue();
                    this.mFrequency = ((Integer) getValue(bigDataDiscReport, ReportIdKey.KEY_FREQUENCY, (Object) 0)).intValue();
                }
                return true;
            }
        } else if (i == 201) {
            ReportData reportWifiState = getLastIndexOfData(201);
            ReportData prevWifiStateData = getLastIndexOfData(201, 2);
            if (reportWifiState != null && prevWifiStateData != null) {
                if (((Integer) getValue(prevWifiStateData, ReportIdKey.KEY_WIFI_STATE, (Object) 0)).intValue() != ((Integer) getValue(reportWifiState, ReportIdKey.KEY_WIFI_STATE, (Object) 0)).intValue()) {
                    String prevCallBy = (String) getValue(prevWifiStateData, ReportIdKey.KEY_CALL_BY, "");
                    this.mPackageName = (String) getValue(reportWifiState, ReportIdKey.KEY_CALL_BY, "");
                    if (prevCallBy != null && !prevCallBy.equals(this.mPackageName) && !isApiCalledBySystemApk(this.mPackageName)) {
                        this.mCategoryId = 2;
                        Log.i(TAG, "matched category id : " + this.mCategoryId);
                        return true;
                    }
                }
            }
        } else if (i == 401 && (scanFailReport = getLastIndexOfData(ReportIdKey.ID_SCAN_FAIL)) != null) {
            this.mCategoryId = 8;
            Log.i(TAG, "matched category id : " + this.mCategoryId);
            this.mSupplicantDisconnectCount = ((Integer) getValue(scanFailReport, ReportIdKey.KEY_COUNT, (Object) 0)).intValue();
            this.mHangReason = ((Integer) getValue(scanFailReport, "reason", (Object) -1)).intValue();
            return true;
        }
        ReportData tryingConnectApReport = this.mLastTriedToConnectReport;
        if (tryingConnectApReport != null) {
            int networkId = ((Integer) getValue(tryingConnectApReport, ReportIdKey.KEY_NET_ID, (Object) -1)).intValue();
            if (networkId == -1) {
                Log.i(TAG, "invalid network ID");
                issueDetected2 = false;
            } else {
                this.mNumAssociation = ((Integer) getValue(tryingConnectApReport, ReportIdKey.KEY_NUM_ASSOCIATION, (Object) 0)).intValue();
                if (this.mNumAssociation == 0) {
                    Log.i(TAG, "first time connection");
                }
                if (((Integer) getValue(tryingConnectApReport, ReportIdKey.KEY_IS_LINK_DEBOUNCING, (Object) 0)).intValue() == 1) {
                    Log.i(TAG, "it's link debouncing connection");
                    issueDetected2 = false;
                } else {
                    ReportData connectedReport = getLastIndexOfData(2);
                    if (connectedReport != null) {
                        issueDetected2 = false;
                        str2 = ReportIdKey.KEY_OUI;
                        long j = connectedReport.mTime;
                        num = -200;
                        str = ReportIdKey.KEY_RSSI;
                        if (j > tryingConnectApReport.mTime && ((Integer) getValue(connectedReport, ReportIdKey.KEY_NET_ID, (Object) -1)).intValue() == networkId) {
                            Log.i(TAG, "network is connected");
                        }
                    } else {
                        issueDetected2 = false;
                        str2 = ReportIdKey.KEY_OUI;
                        num = -200;
                        str = ReportIdKey.KEY_RSSI;
                    }
                    this.mSsid = (String) getValue(tryingConnectApReport, "ssid", UNKNOWN);
                    ReportData rejectReport = getLastIndexOfData(14);
                    if (rejectReport == null) {
                        str4 = str2;
                        str3 = str;
                        num2 = num;
                    } else if (networkId == ((Integer) getValue(rejectReport, ReportIdKey.KEY_NET_ID, (Object) -1)).intValue()) {
                        this.mBssid = (String) getValue(tryingConnectApReport, "bssid", "");
                        if ("any".equals(this.mBssid)) {
                            ReportData bigdataReport = getLastIndexOfData(ReportIdKey.ID_BIGDATA_ASSOC_REJECT);
                            if (bigdataReport != null) {
                                this.mRssi = ((Integer) getValue(bigdataReport, str, num)).intValue();
                                this.mOui = (String) getValue(bigdataReport, str2, UNKNOWN);
                                this.mReason = ((Integer) getValue(bigdataReport, "reason", (Object) 0)).intValue();
                                this.mKeyMgmt = ((Integer) getValue(bigdataReport, ReportIdKey.KEY_WPA_SECURE_TYPE, (Object) 0)).intValue();
                                this.mFrequency = ((Integer) getValue(bigdataReport, ReportIdKey.KEY_FREQUENCY, (Object) 0)).intValue();
                            }
                            if (this.mRssi < BASE_RSSI_CONDITION) {
                                Log.i(TAG, "weak signal " + this.mRssi);
                            } else {
                                this.mCategoryId = 5;
                                if (this.mReason == 0) {
                                    this.mReason = ((Integer) getValue(rejectReport, "reason", (Object) 0)).intValue();
                                }
                                issueDetected = true;
                                this.mLastTriedToConnectReport = getLastIndexOfData(11);
                                if (issueDetected) {
                                    Log.i(TAG, "pattern matched categoryId:" + this.mCategoryId);
                                }
                                return issueDetected;
                            }
                        } else {
                            str4 = str2;
                            str3 = str;
                            num2 = num;
                            Log.d(TAG, "assoc.rejected (auto connection)");
                        }
                    } else {
                        str4 = str2;
                        str3 = str;
                        num2 = num;
                        Log.d(TAG, "assoc.rejected but network id is mismatched. try:" + networkId);
                    }
                    ReportData bigDataDiscReport2 = getLastIndexOfData(200);
                    if (bigDataDiscReport2 != null) {
                        this.mRssi = ((Integer) getValue(bigDataDiscReport2, str3, num2)).intValue();
                        this.mOui = (String) getValue(bigDataDiscReport2, str4, UNKNOWN);
                        this.mKeyMgmt = ((Integer) getValue(bigDataDiscReport2, ReportIdKey.KEY_WPA_SECURE_TYPE, (Object) 0)).intValue();
                        this.mFrequency = ((Integer) getValue(bigDataDiscReport2, ReportIdKey.KEY_FREQUENCY, (Object) 0)).intValue();
                    }
                    if (this.mRssi < BASE_RSSI_CONDITION) {
                        Log.i(TAG, "weak signal " + this.mRssi);
                    } else {
                        ReportData authFailed = getLastIndexOfData(15);
                        if (authFailed != null) {
                            if (networkId == ((Integer) getValue(authFailed, ReportIdKey.KEY_NET_ID, (Object) -1)).intValue()) {
                                this.mCategoryId = 6;
                                this.mBssid = (String) getValue(tryingConnectApReport, "bssid", "");
                                this.mReason = ((Integer) getValue(authFailed, "reason", (Object) 0)).intValue();
                                issueDetected = true;
                                this.mLastTriedToConnectReport = getLastIndexOfData(11);
                                if (issueDetected) {
                                }
                                return issueDetected;
                            }
                            Log.d(TAG, "auth.failed but network id is mismatched. try:" + networkId);
                        }
                        ReportData dhcpReport = getLastIndexOfData(ReportIdKey.ID_DHCP_FAIL);
                        if (!(dhcpReport == null || (dhcpReason = ((Integer) getValue(dhcpReport, ReportIdKey.KEY_DHCP_FAIL_REASON, (Object) 1)).intValue()) == 1)) {
                            this.mReason = dhcpReason;
                            this.mCategoryId = 7;
                            issueDetected = true;
                            this.mLastTriedToConnectReport = getLastIndexOfData(11);
                            if (issueDetected) {
                            }
                            return issueDetected;
                        }
                    }
                }
            }
        } else {
            issueDetected2 = false;
        }
        issueDetected = issueDetected2;
        this.mLastTriedToConnectReport = getLastIndexOfData(11);
        if (issueDetected) {
        }
        return issueDetected;
    }

    @Override // com.samsung.android.server.wifi.dqa.WifiIssuePattern
    public Bundle getBigDataParams() {
        Bundle bigDataBundle = getBigDataBundle(WifiBigDataLogManager.FEATURE_ISSUE_DETECTOR_DISC2, getParams());
        bigDataBundle.putInt(ReportIdKey.KEY_CATEGORY_ID, this.mCategoryId);
        return bigDataBundle;
    }

    private void initValues() {
        this.mCategoryId = 0;
        this.mLastProceedMessage = 0;
        this.mLastProceedState = UNKNOWN;
        this.mHangReason = -1;
        this.mSupplicantDisconnectCount = 0;
        this.mReason = 0;
        this.mSsid = UNKNOWN;
        this.mBssid = UNKNOWN;
        this.mOui = UNKNOWN;
        this.mNumAssociation = 0;
        this.mRssi = -200;
        this.mKeyMgmt = 0;
        this.mFrequency = 0;
        this.mPackageName = UNKNOWN;
    }

    private String getParams() {
        StringBuffer sb = new StringBuffer();
        sb.append(this.mCategoryId);
        sb.append(" ");
        sb.append(this.mLastProceedState);
        sb.append(" ");
        sb.append(this.mLastProceedMessage);
        sb.append(" ");
        sb.append(this.mHangReason);
        sb.append(" ");
        sb.append(this.mSupplicantDisconnectCount);
        sb.append(" ");
        sb.append(this.mReason);
        sb.append(" ");
        sb.append(this.mNumAssociation);
        sb.append(" ");
        sb.append(this.mRssi);
        sb.append(" ");
        sb.append(this.mOui);
        sb.append(" ");
        sb.append(this.mKeyMgmt);
        sb.append(" ");
        sb.append(this.mFrequency);
        sb.append(" ");
        sb.append(this.mPackageName);
        sb.append(" ");
        sb.append("0.6");
        sb.append(" ");
        sb.append(this.mSsid);
        sb.append(" ");
        sb.append(this.mBssid);
        return sb.toString();
    }

    @Override // com.samsung.android.server.wifi.dqa.WifiIssuePattern
    public String getPatternId() {
        return "disc2";
    }
}
