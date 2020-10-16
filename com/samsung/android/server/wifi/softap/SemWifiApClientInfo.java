package com.samsung.android.server.wifi.softap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.UserHandle;
import android.sec.enterprise.auditlog.AuditLog;
import android.util.Log;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiNative;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

public class SemWifiApClientInfo {
    public static final int AP_STA_DISCONNECT_DELAY = 60000;
    public static final int AP_STA_RECONNECT_DELAY = 10000;
    private static final boolean MHSDBG = ("eng".equals(Build.TYPE) || Debug.semIsProductDev());
    private static final String TAG = "SemWifiApClientInfo";
    private static final String WIFI_AP_DRIVER_STATE_HANGED = "com.samsung.android.net.wifi.WIFI_AP_DRIVER_STATE_HANGED";
    private static final String WIFI_AP_STA_DHCPACK_EVENT = "com.samsung.android.net.wifi.WIFI_AP_STA_DHCPACK_EVENT";
    private static long mMHSOffTime = 0;
    private Intent intent;
    private String mApInterfaceName;
    private boolean mChannelSwitch = false;
    private int mClients = 0;
    private Context mContext;
    private Handler mHandler;
    private Looper mLooper;
    private Hashtable<String, ClientInfo> mMHSClients = new Hashtable<>();
    private List<String> mMHSDumpCSALogs = new ArrayList();
    private List<String> mMHSDumpLogs = new ArrayList();
    private SemWifiApMonitor mSemWifiApMonitor;
    private final BroadcastReceiver mSoftApReceiver;
    private final IntentFilter mSoftApReceiverFilter;
    private String[] mStr = null;
    private WifiNative mWifiNative;
    private String mac;

    public SemWifiApClientInfo(Context context, Looper looper) {
        this.mContext = context;
        this.mLooper = looper;
        this.mSoftApReceiverFilter = new IntentFilter(WIFI_AP_STA_DHCPACK_EVENT);
        this.mSoftApReceiver = new BroadcastReceiver() {
            /* class com.samsung.android.server.wifi.softap.SemWifiApClientInfo.C07931 */

            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(SemWifiApClientInfo.WIFI_AP_STA_DHCPACK_EVENT)) {
                    Log.d(SemWifiApClientInfo.TAG, "softApManager got WIFI_AP_STA_DHCPACK_EVENT");
                    String mMac = (String) intent.getExtra("MAC");
                    if (SemWifiApClientInfo.this.mMHSClients.containsKey(mMac)) {
                        ClientInfo ci = (ClientInfo) SemWifiApClientInfo.this.mMHSClients.get(mMac);
                        String preState = ci.mState;
                        SemWifiApClientInfo.this.MHSClientSetState(mMac, "sta_dhcpack", -1);
                        ci.mIp = (String) intent.getExtra("IP");
                        ci.mDeviceName = (String) intent.getExtra("DEVICE");
                        ci.isInUIList = true;
                        if (preState.equals("sta_assoc")) {
                            ci.mConnectedTime = System.currentTimeMillis();
                            SemWifiApClientInfo semWifiApClientInfo = SemWifiApClientInfo.this;
                            semWifiApClientInfo.addMHSDumpLog("dnsmasq dhcpack mac:" + SemWifiApClientInfo.this.showMacAddress(mMac) + " ip:" + ci.mIp + " name:" + ci.mDeviceName + " mConnectedTime:" + ci.mConnectedTime);
                            int dhcpcnt = SemWifiApClientInfo.this.getClientCntDhcpack();
                            Intent intent2 = new Intent("com.samsung.android.net.wifi.WIFI_AP_STA_STATUS_CHANGED");
                            intent2.putExtra("EVENT", "sta_join");
                            intent2.putExtra("MAC", ci.mMac);
                            intent2.putExtra("IP", ci.mIp);
                            intent2.putExtra("DEVICE", ci.mDeviceName);
                            intent2.putExtra("TIME", ci.mConnectedTime);
                            intent2.putExtra("NUM", dhcpcnt);
                            Log.d(SemWifiApClientInfo.TAG, "mhs client cnt:" + SemWifiApClientInfo.this.mMHSClients.size() + " d:" + dhcpcnt + " h:" + SemWifiApClientInfo.this.getConnectedDeviceLength());
                            if (SemWifiApClientInfo.MHSDBG) {
                                SemWifiApClientInfo.this.showClientsInfo();
                            }
                            context.sendBroadcastAsUser(intent2, UserHandle.ALL);
                            intent2.setClassName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApBroadcastReceiver");
                            context.sendBroadcastAsUser(intent2, UserHandle.ALL);
                        }
                    }
                }
            }
        };
    }

    /* access modifiers changed from: private */
    public class ClientInfo {
        public boolean isInUIList = false;
        public int mAntmode = 0;
        public int mBw = 0;
        public long mConnectedTime = 0;
        public int mDataRate = 0;
        public String mDeviceName = "";
        public int mDis = 0;
        public String mIp = "";
        public String mMac = "";
        public int mMode = 0;
        public int mMumimo = 9;
        public String mOui = "aa:aa:aa";
        public long mRemovedTime = 0;
        public int mRssi = 100;
        public int mSrsn = 0;
        public String mState = "";
        public int mWrsn = -1;

        ClientInfo(String mac) {
            this.mMac = mac;
            this.mOui = mac.substring(0, 8);
        }

        public void setState(String state, int wrsn) {
            SemWifiApClientInfo.this.addMHSDumpLog("MHSClient setState() [" + SemWifiApClientInfo.this.showMacAddress(this.mMac) + "] " + this.mState + " > " + state + " wrsn: " + wrsn);
            if (state.equals("sta_notidisassoc") || state.equals("sta_disconn")) {
                if (this.mState.equals("sta_assoc")) {
                    if (this.mIp.equals("")) {
                        this.mDis = 1;
                        this.mSrsn = 1;
                    }
                } else if (this.mSrsn == 0) {
                    this.mDis = 2;
                }
            } else if (state.equals("sta_mismatch")) {
                this.mDis = 1;
                this.mSrsn = 2;
            } else if (state.equals("sta_notallow")) {
                this.mDis = 1;
                this.mSrsn = 3;
            } else if (state.equals("disassoc_sta")) {
                this.mDis = 1;
                this.mSrsn = 4;
            } else if (state.equals("sta_disassoc")) {
                this.mDis = 1;
            } else if (state.equals("sta_deauth")) {
                this.mDis = 1;
            }
            if (state.equals("sta_remove")) {
                if (SemWifiApClientInfo.this.mWifiNative != null) {
                    String staList = SemWifiApClientInfo.this.mWifiNative.sendHostapdCommand("GET_STA_INFO " + this.mMac);
                    if (staList != null) {
                        String[] part = staList.split("=|\\s");
                        try {
                            this.mBw = Integer.parseInt(part[10]);
                            this.mRssi = Integer.parseInt(part[11]);
                            this.mDataRate = Integer.parseInt(part[12]);
                            this.mMode = Integer.parseInt(part[13]);
                            this.mAntmode = Integer.parseInt(part[14]);
                            this.mMumimo = Integer.parseInt(part[15]);
                            this.mWrsn = Integer.parseInt(part[16]);
                        } catch (NumberFormatException e) {
                            if (SemWifiApClientInfo.MHSDBG) {
                                Log.d(SemWifiApClientInfo.TAG, "MHDC NumberFormatException occurs");
                            }
                        } catch (ArrayIndexOutOfBoundsException e2) {
                            if (SemWifiApClientInfo.MHSDBG) {
                                Log.d(SemWifiApClientInfo.TAG, "MHDC ArrayIndexOutOfBoundsException occurs");
                            }
                        }
                    }
                }
                int i = this.mSrsn;
                if (i == 1) {
                    Log.d(SemWifiApClientInfo.TAG, "MHSClient => send MHDC ip failed");
                } else if (i == 2) {
                    Log.d(SemWifiApClientInfo.TAG, "MHSClient => send MHDC wrong password ");
                } else if (i == 3) {
                    Log.d(SemWifiApClientInfo.TAG, "MHSClient => send MHDC not allowed");
                } else if (i == 4) {
                    Log.d(SemWifiApClientInfo.TAG, "MHSClient => send MHDC Client removed from allowed list");
                }
                String tdata = this.mOui + " " + this.mDis + " " + this.mSrsn + " " + this.mWrsn + " " + this.mBw + " " + this.mRssi + " " + this.mDataRate + " " + this.mMode + " " + this.mAntmode + " " + this.mMumimo;
                Log.d(SemWifiApClientInfo.TAG, "   =>  send MHDC : " + tdata);
                SemWifiApClientInfo.this.sendMHSBigdata(tdata);
                this.mRemovedTime = System.currentTimeMillis();
                if (!this.isInUIList) {
                    SemWifiApClientInfo.this.mMHSClients.remove(this.mMac);
                }
                if (SemWifiApClientInfo.MHSDBG) {
                    SemWifiApClientInfo.this.showClientsInfo();
                }
            }
            this.mState = state;
        }

        public String getState() {
            return this.mState;
        }
    }

    public void addMHSDumpLog(String log) {
        StringBuffer value = new StringBuffer();
        Log.i(TAG, log + " mhs: " + this.mMHSDumpLogs.size());
        value.append(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Long.valueOf(System.currentTimeMillis())) + " " + log + "\n");
        if (this.mMHSDumpLogs.size() > 100) {
            this.mMHSDumpLogs.remove(0);
        }
        this.mMHSDumpLogs.add(value.toString());
    }

    public void addMHSDumpCSALog(String log) {
        StringBuffer value = new StringBuffer();
        Log.i(TAG, log + " mhs: " + this.mMHSDumpCSALogs.size());
        value.append(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Long.valueOf(System.currentTimeMillis())) + " " + log + "\n");
        if (this.mMHSDumpCSALogs.size() > 100) {
            this.mMHSDumpCSALogs.remove(0);
        }
        this.mMHSDumpCSALogs.add(value.toString());
    }

    public String getDumpLogs() {
        StringBuffer retValue = new StringBuffer();
        retValue.append("--WifiApClientInfo history \n");
        retValue.append(this.mMHSDumpLogs.toString());
        retValue.append("\n--showClientsInfo \n");
        retValue.append(showClientsInfo());
        retValue.append("\n--CSA history \n");
        retValue.append(this.mMHSDumpCSALogs.toString());
        return retValue.toString();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void sendMHSBigdata(String aStr) {
        Log.d(TAG, "sendMHSBigdata MHDC " + aStr);
        Message msg = new Message();
        msg.what = 77;
        Bundle args = new Bundle();
        args.putBoolean("bigdata", true);
        args.putString("feature", "MHDC");
        args.putString("data", aStr);
        msg.obj = args;
        ((WifiManager) this.mContext.getSystemService("wifi")).callSECApi(msg);
    }

    public void startReceivingHostapdEvents(String minterface) {
        this.mApInterfaceName = minterface;
        this.mSemWifiApMonitor = WifiInjector.getInstance().getWifiApMonitor();
        this.mWifiNative = WifiInjector.getInstance().getWifiNative();
        Context context = this.mContext;
        if (context != null) {
            context.registerReceiver(this.mSoftApReceiver, this.mSoftApReceiverFilter);
        }
        if (this.mHandler != null) {
            Log.d(TAG, "mHandler is not null");
            stopReceivingEvents();
            this.mHandler = null;
        }
        if (mMHSOffTime != 0) {
            long tgap = System.currentTimeMillis() - mMHSOffTime;
            Log.i(TAG, " mhs on gap:" + tgap);
            if (tgap > 60000) {
                this.mMHSClients.clear();
            }
        }
        this.mHandler = new Handler(Looper.getMainLooper()) {
            /* class com.samsung.android.server.wifi.softap.SemWifiApClientInfo.HandlerC07942 */

            public void handleMessage(Message message) {
                Log.d(SemWifiApClientInfo.TAG, "handleMessage" + message.what);
                switch (message.what) {
                    case SemWifiApMonitor.AP_STA_DISCONNECTED_EVENT /*{ENCODED_INT: 548865}*/:
                        String mac = (String) message.obj;
                        Log.d(SemWifiApClientInfo.TAG, "AP_STA_DISCONNECTED_EVENT - disconnected_device : " + SemWifiApClientInfo.this.showMacAddress(mac) + " remaining_cnt :" + SemWifiApClientInfo.this.getConnectedDeviceLength());
                        AuditLog.log(5, 4, true, Process.myPid(), "SoftApManager", "Client device disconnected from Wi-Fi hotspot");
                        if (SemWifiApClientInfo.this.mMHSClients.containsKey(mac)) {
                            String str = ((ClientInfo) SemWifiApClientInfo.this.mMHSClients.get(mac)).mState;
                            SemWifiApClientInfo.this.MHSClientSetState(mac, "sta_disconn", -1);
                        }
                        Log.d(SemWifiApClientInfo.TAG, "Channel switch status:" + SemWifiApClientInfo.this.mChannelSwitch);
                        if (SemWifiApClientInfo.this.mChannelSwitch) {
                            Log.d(SemWifiApClientInfo.TAG, "Wait for 10 sec for reconnection of client. Sending CMD_AP_STA_RECONNECT");
                            sendMessageDelayed(obtainMessage(SemWifiApMonitor.CMD_AP_STA_RECONNECT), 10000);
                            return;
                        }
                        return;
                    case SemWifiApMonitor.AP_STA_ASSOCIATION_EVENT /*{ENCODED_INT: 548866}*/:
                        String mac2 = (String) message.obj;
                        Log.d(SemWifiApClientInfo.TAG, "AP_STA_ASSOCIATION_EVENT " + SemWifiApClientInfo.this.showMacAddress(mac2) + " remaining_cnt: " + SemWifiApClientInfo.this.getConnectedDeviceLength());
                        SemWifiApClientInfo.this.MHSClientSetState(mac2, "sta_assoc", -1);
                        if (SemWifiApClientInfo.this.mMHSClients.containsKey(mac2)) {
                            ClientInfo ci = (ClientInfo) SemWifiApClientInfo.this.mMHSClients.get(mac2);
                            long tNow = System.currentTimeMillis();
                            long tgap = tNow - ci.mRemovedTime;
                            SemWifiApClientInfo semWifiApClientInfo = SemWifiApClientInfo.this;
                            semWifiApClientInfo.addMHSDumpLog("sta_assoc " + SemWifiApClientInfo.this.showMacAddress(mac2) + " gap:" + tgap + " mConnectedTime:" + ci.mConnectedTime);
                            if (ci.mConnectedTime != 0 && tNow - ci.mRemovedTime < 60000) {
                                SemWifiApClientInfo.this.MHSClientSetState(mac2, "sta_dhcpack", -1);
                                SemWifiApClientInfo semWifiApClientInfo2 = SemWifiApClientInfo.this;
                                semWifiApClientInfo2.addMHSDumpLog("roaming dhcpack mac:" + SemWifiApClientInfo.this.showMacAddress(mac2) + " ip:" + ci.mIp + " name:" + ci.mDeviceName + " mConnectedTime:" + ci.mConnectedTime + " gap :" + tgap);
                                ci.isInUIList = true;
                                int dhcpcnt = SemWifiApClientInfo.this.getClientCntDhcpack();
                                Intent intent = new Intent("com.samsung.android.net.wifi.WIFI_AP_STA_STATUS_CHANGED");
                                intent.putExtra("EVENT", "sta_join");
                                intent.putExtra("MAC", ci.mMac);
                                intent.putExtra("IP", ci.mIp);
                                intent.putExtra("DEVICE", ci.mDeviceName);
                                intent.putExtra("TIME", ci.mConnectedTime);
                                intent.putExtra("NUM", dhcpcnt);
                                Log.d(SemWifiApClientInfo.TAG, "mhs client cnt:" + SemWifiApClientInfo.this.mMHSClients.size() + " d:" + dhcpcnt + " h:" + SemWifiApClientInfo.this.getConnectedDeviceLength());
                                if (SemWifiApClientInfo.MHSDBG) {
                                    SemWifiApClientInfo.this.showClientsInfo();
                                }
                                SemWifiApClientInfo.this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                                intent.setClassName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApBroadcastReceiver");
                                SemWifiApClientInfo.this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                                return;
                            }
                            return;
                        }
                        return;
                    case SemWifiApMonitor.AP_STA_DISASSOCIATION_EVENT /*{ENCODED_INT: 548867}*/:
                        SemWifiApClientInfo.this.mStr = ((String) message.obj).split(" ");
                        String mac3 = SemWifiApClientInfo.this.mStr[0];
                        Log.d(SemWifiApClientInfo.TAG, "AP_STA_DISASSOCIATION_EVENT" + ((String) message.obj));
                        SemWifiApClientInfo semWifiApClientInfo3 = SemWifiApClientInfo.this;
                        semWifiApClientInfo3.MHSClientSetState(mac3, "sta_disassoc", Integer.parseInt(semWifiApClientInfo3.mStr[1]));
                        return;
                    case SemWifiApMonitor.WPS_SUCCESS_EVENT /*{ENCODED_INT: 548868}*/:
                    case SemWifiApMonitor.WPS_FAIL_EVENT /*{ENCODED_INT: 548869}*/:
                    case SemWifiApMonitor.WPS_TIMEOUT_EVENT /*{ENCODED_INT: 548870}*/:
                    case SemWifiApMonitor.WPS_PIN_NEEDED_EVENT /*{ENCODED_INT: 548871}*/:
                    case SemWifiApMonitor.AP_STA_JOIN_EVENT /*{ENCODED_INT: 548876}*/:
                    case SemWifiApMonitor.WPS_OVERLAP_DETECTED /*{ENCODED_INT: 548882}*/:
                    case SemWifiApMonitor.AP_STA_CONNECTED_EVENT /*{ENCODED_INT: 548883}*/:
                    default:
                        Log.d(SemWifiApClientInfo.TAG, "Not Impplemented");
                        return;
                    case SemWifiApMonitor.AP_STA_POSSIBLE_PSK_MISMATCH_EVENT /*{ENCODED_INT: 548872}*/:
                        SemWifiApClientInfo.this.MHSClientSetState((String) message.obj, "sta_mismatch", -1);
                        return;
                    case SemWifiApMonitor.CTRL_EVENT_DRIVER_STATE_EVENT /*{ENCODED_INT: 548873}*/:
                        SemWifiApClientInfo.this.intent = new Intent(SemWifiApClientInfo.WIFI_AP_DRIVER_STATE_HANGED);
                        SemWifiApClientInfo.this.mContext.sendBroadcast(SemWifiApClientInfo.this.intent);
                        SemWifiApClientInfo.this.intent.setClassName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApBroadcastReceiver");
                        SemWifiApClientInfo.this.mContext.sendBroadcast(SemWifiApClientInfo.this.intent);
                        return;
                    case SemWifiApMonitor.AP_CSA_FINISHED_EVENT /*{ENCODED_INT: 548874}*/:
                        if (WifiInjector.getInstance().getSemWifiApChipInfo().supportWifiSharingLite()) {
                            String frequency = ((String) message.obj).split("=")[1];
                            Log.d(SemWifiApClientInfo.TAG, "AP_CSA_FINISHED_EVENT : " + frequency);
                            SemWifiApClientInfo.this.addMHSDumpCSALog(frequency);
                            if (frequency != null && frequency.startsWith("5")) {
                                SemWifiApClientInfo semWifiApClientInfo4 = SemWifiApClientInfo.this;
                                semWifiApClientInfo4.mClients = Integer.parseInt(semWifiApClientInfo4.mWifiNative.sendHostapdCommand("NUM_STA"));
                                SemWifiApClientInfo.this.mChannelSwitch = true;
                                Log.d(SemWifiApClientInfo.TAG, "Channel switched from 2.4GHz to 5GHz: " + frequency + " Switch flag set to:" + SemWifiApClientInfo.this.mChannelSwitch);
                                sendMessageDelayed(obtainMessage(SemWifiApMonitor.CMD_AP_STA_DISCONNECT), 60000);
                                return;
                            }
                            return;
                        }
                        return;
                    case SemWifiApMonitor.AP_CHANGED_CHANNEL_EVENT /*{ENCODED_INT: 548875}*/:
                        return;
                    case SemWifiApMonitor.AP_STA_NEW_EVENT /*{ENCODED_INT: 548877}*/:
                        String mac4 = (String) message.obj;
                        ClientInfo ci2 = (ClientInfo) SemWifiApClientInfo.this.mMHSClients.get(mac4);
                        if (ci2 == null || (!ci2.getState().equals("sta_assoc") && !ci2.getState().equals("sta_dhcpack"))) {
                            SemWifiApClientInfo.this.MHSClientSetState(mac4, "sta_new", -1);
                            if (SemWifiApClientInfo.MHSDBG) {
                                SemWifiApClientInfo.this.showClientsInfo();
                            }
                            if (SemWifiApClientInfo.this.mChannelSwitch) {
                                Log.d(SemWifiApClientInfo.TAG, "Resetting the mChannelSwitch");
                                SemWifiApClientInfo.this.mChannelSwitch = false;
                                return;
                            }
                            return;
                        }
                        Log.e(SemWifiApClientInfo.TAG, "Got sta_new, but already in associated state, ignoring");
                        return;
                    case SemWifiApMonitor.AP_STA_NOTALLOW_EVENT /*{ENCODED_INT: 548878}*/:
                        SemWifiApClientInfo.this.MHSClientSetState((String) message.obj, "sta_notallow", -1);
                        return;
                    case SemWifiApMonitor.AP_STA_NOTIFY_DISASSOCIATION_EVENT /*{ENCODED_INT: 548879}*/:
                        SemWifiApClientInfo.this.MHSClientSetState((String) message.obj, "sta_notidisassoc", -1);
                        return;
                    case SemWifiApMonitor.AP_STA_REMOVE_EVENT /*{ENCODED_INT: 548880}*/:
                        SemWifiApClientInfo.this.mStr = ((String) message.obj).split(" ");
                        String mac5 = SemWifiApClientInfo.this.mStr[0];
                        Log.d(SemWifiApClientInfo.TAG, "AP_STA_REMOVE_EVENT" + ((String) message.obj));
                        if (SemWifiApClientInfo.this.mMHSClients.containsKey(mac5)) {
                            ClientInfo ci3 = (ClientInfo) SemWifiApClientInfo.this.mMHSClients.get(mac5);
                            String str2 = ci3.mState;
                            SemWifiApClientInfo semWifiApClientInfo5 = SemWifiApClientInfo.this;
                            semWifiApClientInfo5.MHSClientSetState(mac5, "sta_remove", Integer.parseInt(semWifiApClientInfo5.mStr[1]));
                            if (ci3.isInUIList) {
                                ci3.isInUIList = false;
                                SemWifiApClientInfo.this.intent = new Intent("com.samsung.android.net.wifi.WIFI_AP_STA_STATUS_CHANGED");
                                SemWifiApClientInfo.this.intent.putExtra("EVENT", "sta_leave");
                                SemWifiApClientInfo.this.intent.putExtra("MAC", mac5);
                                SemWifiApClientInfo.this.intent.putExtra("NUM", SemWifiApClientInfo.this.getClientCntDhcpack());
                                SemWifiApClientInfo.this.mContext.sendBroadcastAsUser(SemWifiApClientInfo.this.intent, UserHandle.ALL);
                                SemWifiApClientInfo.this.intent.setClassName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApBroadcastReceiver");
                                SemWifiApClientInfo.this.mContext.sendBroadcastAsUser(SemWifiApClientInfo.this.intent, UserHandle.ALL);
                                return;
                            }
                            return;
                        }
                        return;
                    case SemWifiApMonitor.AP_STA_DEAUTH_EVENT /*{ENCODED_INT: 548881}*/:
                        SemWifiApClientInfo.this.mStr = ((String) message.obj).split(" ");
                        String mac6 = SemWifiApClientInfo.this.mStr[0];
                        Log.d(SemWifiApClientInfo.TAG, "AP_STA_DEAUTH_EVENT" + ((String) message.obj));
                        SemWifiApClientInfo semWifiApClientInfo6 = SemWifiApClientInfo.this;
                        semWifiApClientInfo6.MHSClientSetState(mac6, "sta_deauth", Integer.parseInt(semWifiApClientInfo6.mStr[1]));
                        return;
                    case SemWifiApMonitor.CMD_AP_STA_DISCONNECT /*{ENCODED_INT: 548884}*/:
                        Log.d(SemWifiApClientInfo.TAG, "CMD_AP_STA_DISCONNECT.Current val" + SemWifiApClientInfo.this.mChannelSwitch);
                        SemWifiApClientInfo.this.mChannelSwitch = false;
                        Log.d(SemWifiApClientInfo.TAG, "CMD_AP_STA_DISCONNECT.Reset val" + SemWifiApClientInfo.this.mChannelSwitch);
                        return;
                    case SemWifiApMonitor.CMD_AP_STA_RECONNECT /*{ENCODED_INT: 548885}*/:
                        Log.d(SemWifiApClientInfo.TAG, "CMD_AP_STA_RECONNECT.Current val" + SemWifiApClientInfo.this.mChannelSwitch);
                        Log.d(SemWifiApClientInfo.TAG, "Old client list" + SemWifiApClientInfo.this.mClients + "New client list" + SemWifiApClientInfo.this.mWifiNative.sendHostapdCommand("NUM_STA"));
                        int num = Integer.parseInt(SemWifiApClientInfo.this.mWifiNative.sendHostapdCommand("NUM_STA"));
                        if (SemWifiApClientInfo.this.mChannelSwitch && SemWifiApClientInfo.this.mClients > num) {
                            Log.d(SemWifiApClientInfo.TAG, "Reconnect didn't happen in 10 sec");
                            SemWifiApClientInfo.this.mChannelSwitch = false;
                            Log.d(SemWifiApClientInfo.TAG, "Sending Broadcast com.samsung.actoin.24GHZ_AP_STA_DISCONNECTED");
                            SemWifiApClientInfo.this.intent = new Intent(SemWifiApBroadcastReceiver.AP_STA_24GHZ_DISCONNECTED);
                            SemWifiApClientInfo.this.mContext.sendBroadcast(SemWifiApClientInfo.this.intent);
                            Log.d(SemWifiApClientInfo.TAG, "Channel switch flag reset status:" + SemWifiApClientInfo.this.mChannelSwitch);
                            return;
                        }
                        return;
                }
            }
        };
        this.mSemWifiApMonitor.registerHandler(this.mApInterfaceName, SemWifiApMonitor.AP_STA_ASSOCIATION_EVENT, this.mHandler);
        this.mSemWifiApMonitor.registerHandler(this.mApInterfaceName, SemWifiApMonitor.AP_STA_DISCONNECTED_EVENT, this.mHandler);
        this.mSemWifiApMonitor.registerHandler(this.mApInterfaceName, SemWifiApMonitor.AP_STA_DISASSOCIATION_EVENT, this.mHandler);
        this.mSemWifiApMonitor.registerHandler(this.mApInterfaceName, SemWifiApMonitor.AP_STA_POSSIBLE_PSK_MISMATCH_EVENT, this.mHandler);
        this.mSemWifiApMonitor.registerHandler(this.mApInterfaceName, SemWifiApMonitor.CTRL_EVENT_DRIVER_STATE_EVENT, this.mHandler);
        this.mSemWifiApMonitor.registerHandler(this.mApInterfaceName, SemWifiApMonitor.AP_CSA_FINISHED_EVENT, this.mHandler);
        this.mSemWifiApMonitor.registerHandler(this.mApInterfaceName, SemWifiApMonitor.AP_CHANGED_CHANNEL_EVENT, this.mHandler);
        this.mSemWifiApMonitor.registerHandler(this.mApInterfaceName, SemWifiApMonitor.AP_STA_NEW_EVENT, this.mHandler);
        this.mSemWifiApMonitor.registerHandler(this.mApInterfaceName, SemWifiApMonitor.AP_STA_NOTALLOW_EVENT, this.mHandler);
        this.mSemWifiApMonitor.registerHandler(this.mApInterfaceName, SemWifiApMonitor.AP_STA_NOTIFY_DISASSOCIATION_EVENT, this.mHandler);
        this.mSemWifiApMonitor.registerHandler(this.mApInterfaceName, SemWifiApMonitor.AP_STA_REMOVE_EVENT, this.mHandler);
        this.mSemWifiApMonitor.registerHandler(this.mApInterfaceName, SemWifiApMonitor.AP_STA_DEAUTH_EVENT, this.mHandler);
    }

    public void stopReceivingEvents() {
        this.mHandler = null;
        mMHSOffTime = System.currentTimeMillis();
        for (String key : this.mMHSClients.keySet()) {
            MHSClientSetState(key, "sta_disconn", -1);
            this.mMHSClients.get(key).mRemovedTime = System.currentTimeMillis();
            this.mMHSClients.get(key).isInUIList = false;
        }
        Context context = this.mContext;
        if (context != null) {
            context.unregisterReceiver(this.mSoftApReceiver);
        }
        this.mSemWifiApMonitor.unRegisterHandler();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized String showClientsInfo() {
        StringBuilder sb;
        Log.d(TAG, "showClientsInfo() size : " + this.mMHSClients.size());
        sb = new StringBuilder();
        int i = 0;
        for (String key : this.mMHSClients.keySet()) {
            ClientInfo ci = this.mMHSClients.get(key);
            sb.append("idx : " + i + " " + showMacAddress(key) + " " + showMacAddress(ci.mMac) + " " + ci.mIp + " " + ci.mDeviceName + " ct:" + ci.mConnectedTime + " rt:" + ci.mRemovedTime + " " + ci.getState() + " isInUIList:" + ci.isInUIList + "\n");
            i++;
        }
        if (MHSDBG) {
            Log.d(TAG, sb.toString());
        }
        return sb.toString();
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized int getClientCntDhcpack() {
        int rtn;
        rtn = 0;
        int i = 0;
        for (String key : this.mMHSClients.keySet()) {
            if (this.mMHSClients.get(key).getState().equals("sta_dhcpack")) {
                rtn++;
            }
            if (MHSDBG) {
                Log.d(TAG, "idx : " + i + " rtn : " + rtn + " " + showMacAddress(key) + " " + showMacAddress(this.mMHSClients.get(key).mMac) + " " + this.mMHSClients.get(key).getState() + " " + this.mMHSClients.get(key).mConnectedTime);
                i++;
            }
        }
        return rtn;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private synchronized void MHSClientSetState(String aMac, String aState, int aWrsn) {
        String mac2 = aMac.toLowerCase();
        if (this.mMHSClients.containsKey(mac2)) {
            this.mMHSClients.get(mac2).setState(aState, aWrsn);
        } else if (!aState.equals("sta_new")) {
            Log.d(TAG, " MHSClient do not add " + showMacAddress(aMac) + " state :" + aState);
        } else {
            ClientInfo ci = new ClientInfo(mac2);
            this.mMHSClients.put(mac2, ci);
            ci.setState(aState, -1);
            Log.d(TAG, "new client :" + showMacAddress(mac2));
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private String showMacAddress(String aMac) {
        if (MHSDBG) {
            return aMac;
        }
        return aMac.substring(0, 3) + aMac.substring(12, 17);
    }

    /* access modifiers changed from: protected */
    public int getConnectedDeviceLength() {
        int num = 0;
        String staList = this.mWifiNative.sendHostapdCommand("GET_STA_LIST");
        if (staList != null) {
            num = staList.length() / 18;
        }
        Log.d(TAG, "getAccessPointStaList num is " + num);
        return num;
    }

    public void setAccessPointDisassocSta(String mMac) {
        MHSClientSetState(mMac, "disassoc_sta", -1);
    }

    public synchronized List<String> getWifiApStaListDetail() {
        List<String> rListString;
        rListString = new ArrayList<>();
        for (String key : this.mMHSClients.keySet()) {
            ClientInfo ci = this.mMHSClients.get(key);
            if (ci.getState().equals("sta_dhcpack")) {
                rListString.add(ci.mMac + " " + ci.mIp + " " + ci.mDeviceName + " " + ci.mConnectedTime);
                Log.d(TAG, "wifiap list detail: " + showMacAddress(ci.mMac) + " " + ci.mIp + " " + ci.mDeviceName + " " + ci.mConnectedTime);
            }
        }
        return rListString;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
    }
}
