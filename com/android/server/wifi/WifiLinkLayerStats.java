package com.android.server.wifi;

import android.util.SparseArray;
import java.util.Arrays;

public class WifiLinkLayerStats {
    public static final String V1_0 = "V1_0";
    public static final String V1_3 = "V1_3";
    public int beacon_rx;
    public final SparseArray<ChannelStats> channelStatsMap = new SparseArray<>();
    public long lostmpdu_be;
    public long lostmpdu_bk;
    public long lostmpdu_vi;
    public long lostmpdu_vo;
    public int on_time;
    public int on_time_background_scan = -1;
    public int on_time_hs20_scan = -1;
    public int on_time_nan_scan = -1;
    public int on_time_pno_scan = -1;
    public int on_time_roam_scan = -1;
    public int on_time_scan;
    public long retries_be;
    public long retries_bk;
    public long retries_vi;
    public long retries_vo;
    public int rssi_mgmt;
    public int rx_time;
    public long rxmpdu_be;
    public long rxmpdu_bk;
    public long rxmpdu_vi;
    public long rxmpdu_vo;
    public long timeStampInMs;
    public int tx_time;
    public int[] tx_time_per_level;
    public long txmpdu_be;
    public long txmpdu_bk;
    public long txmpdu_vi;
    public long txmpdu_vo;
    public String version;

    public static class ChannelStats {
        public int ccaBusyTimeMs;
        public int frequency;
        public int radioOnTimeMs;
    }

    public String toString() {
        StringBuilder sbuf = new StringBuilder();
        sbuf.append(" WifiLinkLayerStats: ");
        sbuf.append('\n');
        sbuf.append(" version of StaLinkLayerStats: ");
        sbuf.append(this.version);
        sbuf.append('\n');
        sbuf.append(" my bss beacon rx: ");
        sbuf.append(Integer.toString(this.beacon_rx));
        sbuf.append('\n');
        sbuf.append(" RSSI mgmt: ");
        sbuf.append(Integer.toString(this.rssi_mgmt));
        sbuf.append('\n');
        sbuf.append(" BE : ");
        sbuf.append(" rx=");
        sbuf.append(Long.toString(this.rxmpdu_be));
        sbuf.append(" tx=");
        sbuf.append(Long.toString(this.txmpdu_be));
        sbuf.append(" lost=");
        sbuf.append(Long.toString(this.lostmpdu_be));
        sbuf.append(" retries=");
        sbuf.append(Long.toString(this.retries_be));
        sbuf.append('\n');
        sbuf.append(" BK : ");
        sbuf.append(" rx=");
        sbuf.append(Long.toString(this.rxmpdu_bk));
        sbuf.append(" tx=");
        sbuf.append(Long.toString(this.txmpdu_bk));
        sbuf.append(" lost=");
        sbuf.append(Long.toString(this.lostmpdu_bk));
        sbuf.append(" retries=");
        sbuf.append(Long.toString(this.retries_bk));
        sbuf.append('\n');
        sbuf.append(" VI : ");
        sbuf.append(" rx=");
        sbuf.append(Long.toString(this.rxmpdu_vi));
        sbuf.append(" tx=");
        sbuf.append(Long.toString(this.txmpdu_vi));
        sbuf.append(" lost=");
        sbuf.append(Long.toString(this.lostmpdu_vi));
        sbuf.append(" retries=");
        sbuf.append(Long.toString(this.retries_vi));
        sbuf.append('\n');
        sbuf.append(" VO : ");
        sbuf.append(" rx=");
        sbuf.append(Long.toString(this.rxmpdu_vo));
        sbuf.append(" tx=");
        sbuf.append(Long.toString(this.txmpdu_vo));
        sbuf.append(" lost=");
        sbuf.append(Long.toString(this.lostmpdu_vo));
        sbuf.append(" retries=");
        sbuf.append(Long.toString(this.retries_vo));
        sbuf.append('\n');
        sbuf.append(" on_time : ");
        sbuf.append(Integer.toString(this.on_time));
        sbuf.append(" tx_time=");
        sbuf.append(Integer.toString(this.tx_time));
        sbuf.append(" rx_time=");
        sbuf.append(Integer.toString(this.rx_time));
        sbuf.append(" scan_time=");
        sbuf.append(Integer.toString(this.on_time_scan));
        sbuf.append('\n');
        sbuf.append(" nan_scan_time=");
        sbuf.append(Integer.toString(this.on_time_nan_scan));
        sbuf.append('\n');
        sbuf.append(" g_scan_time=");
        sbuf.append(Integer.toString(this.on_time_background_scan));
        sbuf.append('\n');
        sbuf.append(" roam_scan_time=");
        sbuf.append(Integer.toString(this.on_time_roam_scan));
        sbuf.append('\n');
        sbuf.append(" pno_scan_time=");
        sbuf.append(Integer.toString(this.on_time_pno_scan));
        sbuf.append('\n');
        sbuf.append(" hs2.0_scan_time=");
        sbuf.append(Integer.toString(this.on_time_hs20_scan));
        sbuf.append('\n');
        sbuf.append(" tx_time_per_level=" + Arrays.toString(this.tx_time_per_level));
        sbuf.append('\n');
        int numChanStats = this.channelStatsMap.size();
        sbuf.append(" Number of channel stats=");
        sbuf.append(numChanStats);
        sbuf.append('\n');
        for (int i = 0; i < numChanStats; i++) {
            ChannelStats channelStatsEntry = this.channelStatsMap.valueAt(i);
            sbuf.append(" Frequency=");
            sbuf.append(channelStatsEntry.frequency);
            sbuf.append(" radioOnTimeMs=");
            sbuf.append(channelStatsEntry.radioOnTimeMs);
            sbuf.append(" ccaBusyTimeMs=");
            sbuf.append(channelStatsEntry.ccaBusyTimeMs);
            sbuf.append('\n');
        }
        sbuf.append(" ts=" + this.timeStampInMs);
        return sbuf.toString();
    }
}
