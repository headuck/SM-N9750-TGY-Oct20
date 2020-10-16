package com.android.server.wifi.hotspot2;

import android.net.RssiCurve;
import android.net.wifi.ScanResult;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.ScanDetail;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.Constants;
import com.android.server.wifi.hotspot2.anqp.HSWanMetricsElement;
import com.android.server.wifi.hotspot2.anqp.IPAddressTypeAvailabilityElement;
import com.android.server.wifi.util.GeneralUtil;
import com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartUtil;
import java.util.HashMap;
import java.util.Map;

public class PasspointNetworkScore {
    private static final int BAND_5GHZ_AWARD = 20;
    private static final int BAND_5GHZ_CHANNEL_EXTRA_AWARD = 10;
    private static final int BLUETOOTH_CONNECTION_ACTIVE_PENALTY = 50;
    private static final RssiCurve CHANNEL_UTILIZATION_SCORES = new RssiCurve(0, 85, new byte[]{1, 0, -1});
    private static final RssiCurve ESTIMATED_AIR_TIME_FRACTION_SCORES = new RssiCurve(0, 85, new byte[]{-1, 0, 1});
    @VisibleForTesting
    public static final int HOME_PROVIDER_AWARD = 100;
    @VisibleForTesting
    public static final int INTERNET_ACCESS_AWARD = 50;
    private static final Map<Integer, Integer> IPV4_SCORES = new HashMap();
    private static final Map<Integer, Integer> IPV6_SCORES = new HashMap();
    private static final Map<NetworkDetail.Ant, Integer> NETWORK_TYPE_SCORES = new HashMap();
    @VisibleForTesting
    public static final int PERSONAL_OR_EMERGENCY_NETWORK_AWARDS = 2;
    @VisibleForTesting
    public static final int PUBLIC_OR_PRIVATE_NETWORK_AWARDS = 4;
    @VisibleForTesting
    public static final int RESTRICTED_OR_UNKNOWN_IP_AWARDS = 1;
    @VisibleForTesting
    public static final RssiCurve RSSI_SCORE = new RssiCurve(-80, 20, new byte[]{-10, 0, 10, 20, 30, SemWifiApSmartUtil.BLE_BATT_5}, 20);
    @VisibleForTesting
    public static final int UNRESTRICTED_IP_AWARDS = 2;
    @VisibleForTesting
    public static final int WAN_PORT_DOWN_OR_CAPPED_PENALTY = 50;

    static {
        NETWORK_TYPE_SCORES.put(NetworkDetail.Ant.FreePublic, 4);
        NETWORK_TYPE_SCORES.put(NetworkDetail.Ant.ChargeablePublic, 4);
        NETWORK_TYPE_SCORES.put(NetworkDetail.Ant.PrivateWithGuest, 4);
        NETWORK_TYPE_SCORES.put(NetworkDetail.Ant.Private, 4);
        NETWORK_TYPE_SCORES.put(NetworkDetail.Ant.Personal, 2);
        NETWORK_TYPE_SCORES.put(NetworkDetail.Ant.EmergencyOnly, 2);
        NETWORK_TYPE_SCORES.put(NetworkDetail.Ant.Wildcard, 0);
        NETWORK_TYPE_SCORES.put(NetworkDetail.Ant.TestOrExperimental, 0);
        IPV4_SCORES.put(0, 0);
        IPV4_SCORES.put(2, 1);
        IPV4_SCORES.put(5, 1);
        IPV4_SCORES.put(6, 1);
        IPV4_SCORES.put(7, 1);
        IPV4_SCORES.put(1, 2);
        IPV4_SCORES.put(3, 2);
        IPV4_SCORES.put(4, 2);
        IPV6_SCORES.put(0, 0);
        IPV6_SCORES.put(2, 1);
        IPV6_SCORES.put(1, 2);
    }

    public static int calculateScore(boolean isHomeProvider, ScanDetail scanDetail, Map<Constants.ANQPElementType, ANQPElement> anqpElements, boolean isActiveNetwork, StringBuffer sbuf, boolean bluetoothConnected) {
        NetworkDetail networkDetail = scanDetail.getNetworkDetail();
        ScanResult scanResult = scanDetail.getScanResult();
        int score = 0;
        sbuf.append("[ ");
        sbuf.append(scanResult.SSID);
        sbuf.append(" ");
        sbuf.append(scanResult.BSSID);
        sbuf.append(" RSSI:");
        sbuf.append(scanResult.level);
        sbuf.append(" ] ");
        if (isHomeProvider) {
            score = 0 + 100;
            sbuf.append(" Home provider bonus: ");
            sbuf.append(100);
            sbuf.append(",");
        }
        int score2 = score + ((networkDetail.isInternet() ? 1 : -1) * 50);
        sbuf.append(" Internet accessibility score: ");
        sbuf.append((networkDetail.isInternet() ? 1 : -1) * 50);
        sbuf.append(",");
        Integer ndScore = NETWORK_TYPE_SCORES.get(networkDetail.getAnt());
        if (ndScore != null) {
            score2 += ndScore.intValue();
        }
        sbuf.append(" Network type score: ");
        sbuf.append(NETWORK_TYPE_SCORES.get(networkDetail.getAnt()));
        sbuf.append(",");
        if (anqpElements != null) {
            HSWanMetricsElement wm = (HSWanMetricsElement) anqpElements.get(Constants.ANQPElementType.HSWANMetrics);
            if (wm != null && (wm.getStatus() != 1 || wm.isCapped())) {
                score2 -= 50;
                sbuf.append(" Wan port down or capped penalty: ");
                sbuf.append(-50);
                sbuf.append(",");
            }
            IPAddressTypeAvailabilityElement ipa = (IPAddressTypeAvailabilityElement) anqpElements.get(Constants.ANQPElementType.ANQPIPAddrAvailability);
            if (ipa != null) {
                Integer v4Score = IPV4_SCORES.get(Integer.valueOf(ipa.getV4Availability()));
                Integer v6Score = IPV6_SCORES.get(Integer.valueOf(ipa.getV6Availability()));
                Integer v4Score2 = Integer.valueOf(v4Score != null ? v4Score.intValue() : 0);
                sbuf.append(" Ipv4 availability score: ");
                sbuf.append(v4Score2);
                sbuf.append(",");
                Integer v6Score2 = Integer.valueOf(v6Score != null ? v6Score.intValue() : 0);
                sbuf.append(" Ipv6 availability score: ");
                sbuf.append(v6Score2);
                sbuf.append(",");
                score2 += v4Score2.intValue() + v6Score2.intValue();
            }
        }
        int score3 = score2 + RSSI_SCORE.lookupScore(scanDetail.getScanResult().level, isActiveNetwork);
        sbuf.append(" RSSI score: ");
        sbuf.append((int) RSSI_SCORE.lookupScore(scanDetail.getScanResult().level, isActiveNetwork));
        sbuf.append(",");
        if (scanResult.is5GHz()) {
            score3 += 20;
            sbuf.append(" 5GHz bonus: ");
            sbuf.append(20);
            sbuf.append(",");
            if (scanResult.channelWidth >= 2) {
                score3 += 20;
                sbuf.append(" Channel bandwidth bonus: 20");
                sbuf.append(",");
            }
            if (networkDetail.getWifiMode() > 5) {
                score3 += 20;
                sbuf.append(" Mode 11ax bonus: 20");
                sbuf.append(",");
            }
            if (GeneralUtil.isDomesticModel() && GeneralUtil.isDomesticDfsChannel(scanResult.frequency)) {
                score3 += 10;
                sbuf.append(" DFS channel bonus: 10");
                sbuf.append(",");
            }
        }
        if (bluetoothConnected && scanResult.is24GHz()) {
            score3 -= 50;
            sbuf.append(" Active Bluetooth connection penalty: ");
            sbuf.append(-50);
            sbuf.append(",");
        }
        int score4 = score3 + CHANNEL_UTILIZATION_SCORES.lookupScore(networkDetail.getChannelUtilization());
        sbuf.append(" Channel utilization award: ");
        sbuf.append((int) CHANNEL_UTILIZATION_SCORES.lookupScore(networkDetail.getChannelUtilization()));
        sbuf.append(",");
        int estimatedAirTimeFraction = networkDetail.getEstimatedAirTimeFraction(1);
        if (estimatedAirTimeFraction != -1) {
            score4 += ESTIMATED_AIR_TIME_FRACTION_SCORES.lookupScore(estimatedAirTimeFraction);
            sbuf.append(" Estimated air fractional time (AC_BE) award: ");
            sbuf.append((int) ESTIMATED_AIR_TIME_FRACTION_SCORES.lookupScore(estimatedAirTimeFraction));
            sbuf.append(",");
        }
        sbuf.append(" ## Total score: ");
        sbuf.append(score4);
        sbuf.append("\n");
        return score4;
    }
}
