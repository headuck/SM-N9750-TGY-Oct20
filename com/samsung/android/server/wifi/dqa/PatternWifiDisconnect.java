package com.samsung.android.server.wifi.dqa;

import android.os.Bundle;
import android.util.Log;
import com.samsung.android.server.wifi.bigdata.WifiBigDataLogManager;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PatternWifiDisconnect extends WifiIssuePattern {
    private static final long ASSOC_CHECK_TIME_DIFF = 800;
    private static final int ISSUE_TYPE_DISCONNECT_AP_REASON = 3;
    private static final int ISSUE_TYPE_DISCONNECT_BY_3RDPARTY_APK = 1;
    private static final int ISSUE_TYPE_DISCONNECT_BY_SNS = 7;
    private static final int ISSUE_TYPE_DISCONNECT_DHCP_FAILED = 6;
    private static final int ISSUE_TYPE_DISCONNECT_LCD_OFF_STATE = 2;
    private static final int ISSUE_TYPE_DISCONNECT_NO_INTERNET_IP_GW = 5;
    private static final int ISSUE_TYPE_DISCONNECT_STATE_ILLEGAL = 4;
    private static final int ISSUE_TYPE_DISCONNECT_STRONG_SIGNAL = 0;
    private static final int ISSUE_TYPE_SYSTEM_PROBLEM = 8;
    private static final String TAG = "PatternWifiDisc";
    private int adps = 0;
    private int apdr = 0;
    private String appName = null;
    private int apwe = 0;
    private final String aver = "2.9";
    private String bssid = "00:20:00:00:00:00";
    private int dhcp = 0;
    private int dhfs = 0;
    private int disconnectReason = 0;
    private int freq = -120;
    private String gateway = "0.0.0.0";

    /* renamed from: ip */
    private String f51ip = "0.0.0.0";
    private int isct = 0;
    private int locallyGenerated = 0;
    private int mLastAssociatedId = 0;
    private long mLastAssocitatedTime = 0;
    private String oui = "00:00:00";
    private int pprem = 0;
    private int prem = 0;
    private String pres = "default";
    private int rssi = 0;
    private int scrs = 0;
    private int slpp = 2;
    private String ssid = "default";
    private int uwrs = -1;
    private int wpaState = 0;

    @Override // com.samsung.android.server.wifi.dqa.WifiIssuePattern
    public Collection<Integer> getAssociatedKeys() {
        Collection<Integer> ret = new ArrayList<>();
        ret.add(1);
        ret.add(200);
        return ret;
    }

    @Override // com.samsung.android.server.wifi.dqa.WifiIssuePattern
    public boolean isAssociated(int reportId, ReportData reportData) {
        boolean ret = false;
        if (reportId == 1 || reportId == 200) {
            long reportTime = reportData.mTime;
            int i = this.mLastAssociatedId;
            if (i != 0) {
                long diff = reportTime - this.mLastAssocitatedTime;
                if (i != reportId) {
                    if (diff >= 0 && diff < ASSOC_CHECK_TIME_DIFF) {
                        if (DBG) {
                            Log.i(TAG, "associated diff:" + diff);
                        }
                        ret = true;
                    } else if (DBG) {
                        Log.i(TAG, "not associated diff:" + diff);
                    }
                }
            }
            this.mLastAssociatedId = reportId;
            this.mLastAssocitatedTime = reportTime;
        }
        return ret;
    }

    private void resetData() {
        this.f51ip = "0.0.0.0";
        this.gateway = "0.0.0.0";
        this.isct = -1;
        this.oui = "00:00:00";
        this.ssid = "default";
        this.bssid = "00:20:00:00:00:00";
        this.rssi = 0;
        this.freq = -120;
        this.apwe = 0;
        this.dhcp = 0;
        this.apdr = 0;
        this.adps = 0;
        this.slpp = 2;
        this.scrs = 0;
        this.pres = "default";
        this.prem = 0;
        this.pprem = 0;
        this.uwrs = -1;
        this.dhfs = 0;
        this.disconnectReason = 0;
        this.locallyGenerated = 0;
        this.wpaState = 0;
        this.appName = null;
    }

    /* JADX WARNING: Removed duplicated region for block: B:204:0x0564  */
    @Override // com.samsung.android.server.wifi.dqa.WifiIssuePattern
    public boolean matches() {
        boolean patternMatches;
        boolean patternMatches2;
        int i;
        int i2;
        boolean patternMatches3 = false;
        resetData();
        if (DBG) {
            Log.d(TAG, "check pattern disc1");
        }
        ReportData lastDisconnectReport = getLastIndexOfData(1);
        if (lastDisconnectReport == null) {
            Log.e(TAG, "not exit report: disconnect transtion");
            return false;
        }
        ReportData bigDataDisconnetData = getLastIndexOfData(200);
        if (bigDataDisconnetData == null) {
            Log.e(TAG, "not exist report: bigdata disconnect");
            return false;
        }
        this.apwe = ((Integer) getValue(bigDataDisconnetData.mData, ReportIdKey.KEY_AP_INTERNAL_TYPE, (Object) 0)).intValue();
        if (this.apwe == 3) {
            Log.d(TAG, "AP is mobile hotspot");
            return false;
        }
        this.f51ip = (String) getValue(lastDisconnectReport, ReportIdKey.KEY_IP, "0.0.0.0");
        int networkPrefix = ((Integer) getValue(lastDisconnectReport, ReportIdKey.KEY_NETWORK_PREFIX, (Object) 0)).intValue();
        this.gateway = (String) getValue(lastDisconnectReport, ReportIdKey.KEY_GATEWAY, "0.0.0.0");
        String str = this.f51ip;
        if (!(str == null || this.gateway == null)) {
            try {
                if (!isInRange(InetAddress.getByName(str), InetAddress.getByName(this.gateway), networkPrefix)) {
                    this.isct = 5;
                    patternMatches3 = true;
                    Log.d(TAG, "ip and gateway is wrong ");
                }
            } catch (UnknownHostException e) {
                Log.d(TAG, "Fail to get InetAddress from IP and Gateway!! IP : " + this.f51ip + " Getway : " + this.gateway);
            }
        }
        this.rssi = ((Integer) getValue(bigDataDisconnetData.mData, ReportIdKey.KEY_RSSI, (Object) -120)).intValue();
        if (!patternMatches3 && ((i2 = this.rssi) < -60 || i2 >= 0)) {
            Log.d(TAG, "weak signal");
            return false;
        } else if (getLastIndexOfData(6) != null) {
            Log.d(TAG, "disconnected by sleep policy");
            return false;
        } else {
            ReportData airplainModeChanged = getLastIndexOfData(8);
            if (airplainModeChanged == null || ((Integer) getValue(airplainModeChanged, "state", (Object) 0)).intValue() != 1) {
                ReportData emergencyModeChanged = getLastIndexOfData(9);
                if (emergencyModeChanged == null || ((Integer) getValue(emergencyModeChanged, "state", (Object) 0)).intValue() != 1) {
                    if (this.isct == -1) {
                        this.isct = 0;
                    }
                    boolean patternMatches4 = true;
                    this.disconnectReason = ((Integer) getValue(bigDataDisconnetData, ReportIdKey.KEY_DISCONNECT_REASON, (Object) 3)).intValue();
                    this.locallyGenerated = ((Integer) getValue(bigDataDisconnetData, ReportIdKey.KEY_LOCALLY_GENERATED, (Object) 1)).intValue();
                    this.wpaState = ((Integer) getValue(bigDataDisconnetData, ReportIdKey.KEY_WPA_STATE, (Object) 0)).intValue();
                    this.slpp = ((Integer) getValue(lastDisconnectReport, ReportIdKey.KEY_SLEEP_POLICY, (Object) 2)).intValue();
                    this.scrs = ((Integer) getValue(lastDisconnectReport, ReportIdKey.KEY_SCREEN_ON, (Object) 0)).intValue();
                    this.prem = ((Integer) getValue(lastDisconnectReport, ReportIdKey.KEY_PROCESS_MESSAGE, (Object) 0)).intValue();
                    this.pprem = ((Integer) getValue(lastDisconnectReport, ReportIdKey.KEY_PREV_PROCESS_MESSAGE, (Object) 0)).intValue();
                    this.apdr = ((Integer) getValue(lastDisconnectReport, ReportIdKey.KEY_CONNECT_DURATION, (Object) 0)).intValue();
                    if (this.slpp == 2 && this.scrs == 0) {
                        this.isct = 2;
                        patternMatches4 = true;
                    }
                    if (this.locallyGenerated != 1 || this.isct == 5) {
                        patternMatches2 = patternMatches4;
                        int i3 = this.isct;
                        if (i3 == 0 || i3 == 2) {
                            int i4 = this.disconnectReason;
                            if (i4 == 3) {
                                this.isct = 4;
                            } else if (i4 == 6) {
                                if (this.wpaState >= 5) {
                                    Log.d(TAG, "disconnected reason=6 illegal state");
                                    this.isct = 4;
                                    patternMatches = true;
                                    if (patternMatches) {
                                        if (this.isct == -1) {
                                            Log.e(TAG, "invalid isct value");
                                            return false;
                                        }
                                        this.oui = (String) getValue(bigDataDisconnetData.mData, ReportIdKey.KEY_OUI, "00:00:00");
                                        this.freq = ((Integer) getValue(bigDataDisconnetData.mData, ReportIdKey.KEY_FREQUENCY, (Object) 0)).intValue();
                                        this.gateway = (String) getValue(lastDisconnectReport, ReportIdKey.KEY_GATEWAY, "0.0.0.0");
                                        this.f51ip = (String) getValue(lastDisconnectReport, ReportIdKey.KEY_IP, "0.0.0.0");
                                        this.dhcp = ((Integer) getValue(bigDataDisconnetData, ReportIdKey.KEY_AP_INTERNAL_REASON, (Object) 0)).intValue();
                                        this.adps = ((Integer) getValue(lastDisconnectReport, ReportIdKey.KEY_ADPS_STATE, (Object) 0)).intValue();
                                        this.pres = (String) getValue(lastDisconnectReport, ReportIdKey.KEY_LAST_HANDLE_STATE, " ");
                                        this.ssid = removeDoubleQuotes((String) getValue(lastDisconnectReport, "ssid", "default"));
                                        this.bssid = (String) getValue(lastDisconnectReport, "bssid", "00:20:00:00:00:00");
                                    }
                                    Log.d(TAG, "matches return " + patternMatches + " isct:" + this.isct);
                                    return patternMatches;
                                }
                            } else if (i4 == 7) {
                                if (this.wpaState < 6) {
                                    Log.d(TAG, "disconnected reason=7 illegal state");
                                    this.isct = 4;
                                    patternMatches = true;
                                    if (patternMatches) {
                                    }
                                    Log.d(TAG, "matches return " + patternMatches + " isct:" + this.isct);
                                    return patternMatches;
                                }
                            } else if (i4 == 15) {
                                if (this.wpaState != 7) {
                                    Log.d(TAG, "disconnected reason=15 illegal state");
                                    this.isct = 4;
                                    patternMatches = true;
                                    if (patternMatches) {
                                    }
                                    Log.d(TAG, "matches return " + patternMatches + " isct:" + this.isct);
                                    return patternMatches;
                                }
                            } else if (i4 == 16) {
                                if (this.wpaState != 8) {
                                    Log.d(TAG, "disconnected reason=16 illegal state");
                                    this.isct = 4;
                                    patternMatches = true;
                                    if (patternMatches) {
                                    }
                                    Log.d(TAG, "matches return " + patternMatches + " isct:" + this.isct);
                                    return patternMatches;
                                }
                            } else if (i4 == 4 || i4 == 5 || i4 == 22 || i4 == 10 || i4 == 11 || i4 == 13 || i4 == 14 || i4 == 17 || i4 == 18 || i4 == 19 || i4 == 20 || i4 == 21) {
                                Log.d(TAG, "disconnected reason=" + this.disconnectReason + " maybe AP side issue");
                                this.isct = 3;
                                patternMatches = true;
                                if (patternMatches) {
                                }
                                Log.d(TAG, "matches return " + patternMatches + " isct:" + this.isct);
                                return patternMatches;
                            } else if (i4 == 0) {
                                Log.d(TAG, "maybe beacon loss");
                                return false;
                            }
                        }
                    } else {
                        ReportData wifiOffBy3rd = getLastIndexOfData(201);
                        if (wifiOffBy3rd == null || ((Integer) getValue(wifiOffBy3rd.mData, ReportIdKey.KEY_WIFI_STATE, (Object) 0)).intValue() != 0) {
                            ReportData wifiDisconnectBy3rd = getLastIndexOfData(100);
                            if (wifiDisconnectBy3rd != null) {
                                this.appName = (String) getValue(wifiDisconnectBy3rd.mData, ReportIdKey.KEY_CALL_BY, "com.android.settings");
                                if (!isApiCalledBySystemApk(this.appName)) {
                                    Log.d(TAG, "WifiManager." + ((String) getValue(wifiDisconnectBy3rd.mData, ReportIdKey.KEY_API_NAME, "disconnect")) + " api was called by " + this.appName);
                                    this.isct = 1;
                                    patternMatches = true;
                                } else {
                                    Log.d(TAG, "user tirggered");
                                    return false;
                                }
                            } else {
                                patternMatches2 = patternMatches4;
                                ReportData wifiDisabledBy3rd = getLastIndexOfData(101);
                                if (wifiDisabledBy3rd != null) {
                                    this.appName = (String) getValue(wifiDisabledBy3rd.mData, ReportIdKey.KEY_CALL_BY, "com.android.settings");
                                    if (!isApiCalledBySystemApk(this.appName)) {
                                        Log.d(TAG, "WifiManager." + ((String) getValue(wifiDisabledBy3rd.mData, ReportIdKey.KEY_API_NAME, "disable")) + " api was called by " + this.appName);
                                        this.isct = 1;
                                        patternMatches = true;
                                    } else {
                                        Log.d(TAG, "user tirggered");
                                        return false;
                                    }
                                } else {
                                    ReportData wifiRemoveBy3rd = getLastIndexOfData(102);
                                    if (wifiRemoveBy3rd != null) {
                                        this.appName = (String) getValue(wifiRemoveBy3rd.mData, ReportIdKey.KEY_CALL_BY, "com.android.settings");
                                        String str2 = this.appName;
                                        if (str2 != null && "unknown".equals(str2)) {
                                            String callingUid = (String) getValue(wifiRemoveBy3rd.mData, ReportIdKey.KEY_CALL_BY_UID, "android.uid.system");
                                            if (!isApiCalledBySystemUid(callingUid)) {
                                                Log.d(TAG, "WifiManager." + ((String) getValue(wifiRemoveBy3rd.mData, ReportIdKey.KEY_API_NAME, "remove")) + " api was called by " + callingUid);
                                                this.isct = 1;
                                                patternMatches = true;
                                            } else {
                                                Log.d(TAG, "user tirggered");
                                                return false;
                                            }
                                        } else if (!isApiCalledBySystemApk(this.appName)) {
                                            Log.d(TAG, "WifiManager.Remove api was called by " + this.appName);
                                            this.isct = 1;
                                            patternMatches = true;
                                        } else {
                                            Log.d(TAG, "user tirggered");
                                            return false;
                                        }
                                    } else {
                                        ReportData wifiConnectedBy3rd = getLastIndexOfData(103);
                                        if (wifiConnectedBy3rd != null) {
                                            String callingUid2 = (String) getValue(wifiConnectedBy3rd.mData, ReportIdKey.KEY_CALL_BY_UID, "android.uid.system");
                                            if (!isApiCalledBySystemUid(callingUid2)) {
                                                Log.d(TAG, "WifiManager." + ((String) getValue(wifiConnectedBy3rd.mData, ReportIdKey.KEY_API_NAME, "connect")) + " api was called by " + callingUid2);
                                                this.isct = 1;
                                                patternMatches = true;
                                            } else {
                                                Log.d(TAG, "user tirggered");
                                                return false;
                                            }
                                        } else {
                                            ReportData wifiWpsBy3rd = getLastIndexOfData(104);
                                            if (wifiWpsBy3rd != null) {
                                                String callingUid3 = (String) getValue(wifiWpsBy3rd.mData, ReportIdKey.KEY_CALL_BY_UID, "android.uid.system");
                                                if (!isApiCalledBySystemUid(callingUid3)) {
                                                    Log.d(TAG, "WifiManager." + ((String) getValue(wifiWpsBy3rd.mData, ReportIdKey.KEY_API_NAME, "startWps")) + " api was called by " + callingUid3);
                                                    this.isct = 1;
                                                    patternMatches = true;
                                                } else {
                                                    Log.d(TAG, "user tirggered");
                                                    return false;
                                                }
                                            } else if (getLastIndexOfData(7) != null) {
                                                this.isct = 8;
                                                patternMatches = true;
                                            } else if (this.prem == 131215) {
                                                Log.d(TAG, "start connect");
                                                return false;
                                            } else {
                                                ReportData unWantedData = getLastIndexOfData(5);
                                                if (unWantedData != null) {
                                                    this.uwrs = ((Integer) getValue(unWantedData.mData, ReportIdKey.KEY_UNWANTED_REASON, (Object) -1)).intValue();
                                                    if (this.prem == 131216 && this.uwrs != -1) {
                                                        if (this.apdr == 0) {
                                                            Log.d(TAG, "unwanted network");
                                                            return false;
                                                        }
                                                        this.isct = 7;
                                                        patternMatches = true;
                                                    }
                                                }
                                                if (this.prem == 147462 && this.uwrs == 1 && this.disconnectReason == 3) {
                                                    this.isct = 7;
                                                    patternMatches = true;
                                                } else {
                                                    ReportData dhcpFailReport = getLastIndexOfData(ReportIdKey.ID_DHCP_FAIL);
                                                    if (dhcpFailReport != null) {
                                                        this.dhfs = ((Integer) getValue(dhcpFailReport.mData, ReportIdKey.KEY_DHCP_FAIL_REASON, (Object) 0)).intValue();
                                                        int i5 = this.prem;
                                                        if (!((i5 != 131273 && i5 != 131211) || (i = this.dhfs) == 0 || i == 1)) {
                                                            this.isct = 6;
                                                            patternMatches = true;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (patternMatches) {
                            }
                            Log.d(TAG, "matches return " + patternMatches + " isct:" + this.isct);
                            return patternMatches;
                        }
                        this.appName = (String) getValue(wifiOffBy3rd.mData, ReportIdKey.KEY_CALL_BY, "com.android.settings");
                        if (!isApiCalledBySystemApk(this.appName)) {
                            Log.d(TAG, "WifiManager.setWifiEnabled(false) api was called by " + this.appName);
                            this.isct = 1;
                            patternMatches = true;
                            if (patternMatches) {
                            }
                            Log.d(TAG, "matches return " + patternMatches + " isct:" + this.isct);
                            return patternMatches;
                        }
                        Log.d(TAG, "user tirggered");
                        return false;
                    }
                    patternMatches = patternMatches2;
                    if (patternMatches) {
                    }
                    Log.d(TAG, "matches return " + patternMatches + " isct:" + this.isct);
                    return patternMatches;
                }
                Log.d(TAG, "emergency mode is enabled");
                return false;
            }
            Log.d(TAG, "airplane mode is enabled");
            return false;
        }
    }

    private boolean isApiCalledBySystemUid(String callUid) {
        if (callUid != null && callUid.contains("android.uid.system")) {
            return true;
        }
        return false;
    }

    public String getParams() {
        StringBuffer sb = new StringBuffer();
        sb.append(this.oui);
        sb.append(" ");
        sb.append(this.freq);
        sb.append(" ");
        sb.append(this.rssi);
        sb.append(" ");
        sb.append(this.wpaState);
        sb.append(" ");
        sb.append(this.locallyGenerated);
        sb.append(" ");
        sb.append(this.disconnectReason);
        sb.append(" ");
        sb.append(this.dhcp);
        sb.append(" ");
        sb.append(this.apdr);
        sb.append(" ");
        sb.append(this.isct);
        sb.append(" ");
        sb.append(this.dhfs);
        sb.append(" ");
        sb.append(this.adps);
        sb.append(" ");
        sb.append(this.slpp);
        sb.append(" ");
        sb.append(this.scrs);
        sb.append(" ");
        sb.append(this.pres);
        sb.append(" ");
        sb.append(this.prem);
        sb.append(" ");
        sb.append(this.pprem);
        sb.append(" ");
        sb.append(this.uwrs);
        sb.append(" ");
        sb.append("2.9");
        sb.append(" ");
        sb.append(this.apwe);
        sb.append(" ");
        String str = this.appName;
        if (str == null || str.length() <= 0) {
            sb.append("null");
        } else {
            sb.append(this.appName);
        }
        sb.append(" ");
        sb.append(this.ssid);
        sb.append(" ");
        sb.append(this.bssid);
        sb.append(" ");
        sb.append(this.gateway);
        sb.append(" ");
        sb.append(this.f51ip);
        Log.d(TAG, "============================================================================ ");
        Log.d(TAG, sb.toString());
        Log.d(TAG, "============================================================================ ");
        return sb.toString();
    }

    @Override // com.samsung.android.server.wifi.dqa.WifiIssuePattern
    public Bundle getBigDataParams() {
        Bundle bigDataBundle = getBigDataBundle(WifiBigDataLogManager.FEATURE_ISSUE_DETECTOR_DISC1, getParams());
        bigDataBundle.putInt(ReportIdKey.KEY_CATEGORY_ID, this.isct);
        return bigDataBundle;
    }

    @Override // com.samsung.android.server.wifi.dqa.WifiIssuePattern
    public String getPatternId() {
        return "disc1";
    }

    static String removeDoubleQuotes(String string) {
        if (string == null) {
            return "unknown.ssid";
        }
        int length = string.length();
        if (length > 1 && string.charAt(0) == '\"' && string.charAt(length - 1) == '\"') {
            return string.substring(1, length - 1);
        }
        return string;
    }

    private boolean isInRange(InetAddress ipAddress, InetAddress gatewayAddress, int prefix) throws UnknownHostException {
        int targetSize;
        ByteBuffer maskBuffer;
        if (ipAddress.getAddress().length == 4) {
            maskBuffer = ByteBuffer.allocate(4).putInt(-1);
            targetSize = 4;
        } else {
            maskBuffer = ByteBuffer.allocate(16).putLong(-1).putLong(-1);
            targetSize = 16;
        }
        BigInteger mask = new BigInteger(1, maskBuffer.array()).not().shiftRight(prefix);
        BigInteger startIp = new BigInteger(1, ByteBuffer.wrap(ipAddress.getAddress()).array()).and(mask);
        BigInteger endIp = startIp.add(mask.not());
        byte[] startIpArr = toBytes(startIp.toByteArray(), targetSize);
        byte[] endIpArr = toBytes(endIp.toByteArray(), targetSize);
        InetAddress startAddress = InetAddress.getByAddress(startIpArr);
        InetAddress endAddress = InetAddress.getByAddress(endIpArr);
        BigInteger start = new BigInteger(1, startAddress.getAddress());
        BigInteger end = new BigInteger(1, endAddress.getAddress());
        BigInteger target = new BigInteger(1, gatewayAddress.getAddress());
        int st = start.compareTo(target);
        int te = target.compareTo(end);
        return (st == -1 || st == 0) && (te == -1 || te == 0);
    }

    private byte[] toBytes(byte[] array, int targetSize) {
        int counter = 0;
        List<Byte> newArr = new ArrayList<>();
        while (counter < targetSize && (array.length - 1) - counter >= 0) {
            newArr.add(0, Byte.valueOf(array[(array.length - 1) - counter]));
            counter++;
        }
        int size = newArr.size();
        for (int i = 0; i < targetSize - size; i++) {
            newArr.add(0, (byte) 0);
        }
        byte[] ret = new byte[newArr.size()];
        for (int i2 = 0; i2 < newArr.size(); i2++) {
            ret[i2] = newArr.get(i2).byteValue();
        }
        return ret;
    }
}
