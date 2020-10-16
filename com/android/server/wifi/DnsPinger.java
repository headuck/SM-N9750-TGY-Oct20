package com.android.server.wifi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkUtils;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public final class DnsPinger extends Handler {
    private static final int ACTION_CANCEL_ALL_PINGS = 593923;
    private static final int ACTION_LISTEN_FOR_RESPONSE = 593922;
    private static final int ACTION_PING_DNS = 593921;
    private static final int ACTION_PING_DNS_SPECIFIC = 593924;
    private static final int BASE = 593920;
    public static final int CACHED_RESULT = 1;
    private static final boolean DBG = Debug.semIsProductDev();
    public static final int DNS_PING_RESULT = 593920;
    public static final int DNS_PING_RESULT_SPECIFIC = 593925;
    private static final int DNS_PORT = 53;
    private static final int DNS_RESPONSE_BUFFER_SIZE = 512;
    private static HashMap<String, DnsResult> MostRecentDnsResultMap = new HashMap<>();
    public static final int NO_INTERNET = -3;
    public static final int PRIVATE_IP_ADDRESS = 2;
    private static final int RECEIVE_POLL_INTERVAL_MS = 200;
    public static final int REQUESTED_URL_ALREADY_IP_ADDRESS = 3;
    private static final boolean SMARTCM_DBG = false;
    public static final int SOCKET_EXCEPTION = -2;
    private static final int SOCKET_TIMEOUT_MS = 1;
    public static final int TIMEOUT = -1;
    private static final AtomicInteger sCounter = new AtomicInteger();
    private static final Random sRandom = new Random();
    HashMap<String, List<DnsResult>> DnsResultMap = new HashMap<>();
    private String TAG;
    final Object lock = new Object();
    private List<ActivePing> mActivePings = new ArrayList();
    private final int mConnectionType;
    private ConnectivityManager mConnectivityManager = null;
    private final Context mContext;
    private AtomicInteger mCurrentToken = new AtomicInteger();
    private final ArrayList<InetAddress> mDefaultDns;
    private byte[] mDnsQuery;
    private int mEventCounter;
    LinkProperties mLp = null;
    private final Handler mTarget;
    WifiInfo mWifiInfo = null;

    private class ActivePing {
        int internalId;
        short packetId;
        Integer result;
        DatagramSocket socket;
        long start;
        int timeout;
        String url;

        private ActivePing() {
            this.start = SystemClock.elapsedRealtime();
        }
    }

    private class DnsArg {
        InetAddress dns;
        int seq;
        String targetUrl;

        DnsArg(InetAddress d, int s, String u) {
            this.dns = d;
            this.seq = s;
            this.targetUrl = u;
        }
    }

    /* access modifiers changed from: private */
    public class DnsResult {
        InetAddress resultIp;
        long ttl;

        DnsResult(InetAddress ip, long t) {
            this.resultIp = ip;
            this.ttl = t;
        }
    }

    public DnsPinger(Context context, String TAG2, Looper looper, Handler target, int connectionType) {
        super(looper);
        this.TAG = TAG2;
        this.mContext = context;
        this.mTarget = target;
        this.mConnectionType = connectionType;
        if (ConnectivityManager.isNetworkTypeValid(connectionType)) {
            this.mDefaultDns = new ArrayList<>();
            this.mDefaultDns.add(getDefaultDns());
            this.mEventCounter = 0;
            return;
        }
        throw new IllegalArgumentException("Invalid connectionType in constructor: " + connectionType);
    }

    public void handleMessage(Message msg) {
        Object obj;
        int oldTag = TrafficStats.getAndSetThreadStatsTag(-190);
        try {
            short s = 1;
            switch (msg.what) {
                case ACTION_PING_DNS /*{ENCODED_INT: 593921}*/:
                case ACTION_PING_DNS_SPECIFIC /*{ENCODED_INT: 593924}*/:
                    DnsArg dnsArg = (DnsArg) msg.obj;
                    if (dnsArg.seq == this.mCurrentToken.get()) {
                        try {
                            ActivePing newActivePing = new ActivePing();
                            InetAddress dnsAddress = dnsArg.dns;
                            updateDnsQuery(dnsArg.targetUrl);
                            newActivePing.internalId = msg.arg1;
                            newActivePing.timeout = msg.arg2;
                            newActivePing.url = dnsArg.targetUrl;
                            newActivePing.socket = new DatagramSocket();
                            newActivePing.socket.setSoTimeout(1);
                            try {
                                Os.setsockoptIfreq(newActivePing.socket.getFileDescriptor$(), OsConstants.SOL_SOCKET, OsConstants.SO_BINDTODEVICE, getCurrentLinkProperties().getInterfaceName());
                            } catch (Exception e) {
                                loge("sendDnsPing::Error binding to socket " + e);
                            }
                            if (msg.what == ACTION_PING_DNS) {
                                newActivePing.packetId = (short) (sRandom.nextInt() << 1);
                            } else {
                                newActivePing.packetId = (short) ((sRandom.nextInt() << 1) + 1);
                            }
                            byte[] buf = (byte[]) this.mDnsQuery.clone();
                            buf[0] = (byte) (newActivePing.packetId >> 8);
                            buf[1] = (byte) newActivePing.packetId;
                            DatagramPacket packet = new DatagramPacket(buf, buf.length, dnsAddress, 53);
                            if (DBG) {
                                log(getKernelTime() + "Sending a ping " + newActivePing.internalId + " to " + dnsAddress.getHostAddress() + " with packetId " + ((int) newActivePing.packetId) + "(" + Integer.toHexString(newActivePing.packetId & 65535) + ").");
                            }
                            newActivePing.socket.send(packet);
                            this.mActivePings.add(newActivePing);
                            this.mEventCounter++;
                            sendMessageDelayed(obtainMessage(ACTION_LISTEN_FOR_RESPONSE, this.mEventCounter, 0), 200);
                            break;
                        } catch (IOException e2) {
                            if (msg.what == ACTION_PING_DNS) {
                                sendResponse(msg.arg1, -9998, -2);
                                break;
                            } else {
                                sendResponse(msg.arg1, -9999, -2);
                                break;
                            }
                        }
                    }
                    break;
                case ACTION_LISTEN_FOR_RESPONSE /*{ENCODED_INT: 593922}*/:
                    if (msg.arg1 != this.mEventCounter) {
                        break;
                    } else {
                        for (ActivePing curPing : this.mActivePings) {
                            try {
                                byte[] responseBuf = new byte[512];
                                DatagramPacket replyPacket = new DatagramPacket(responseBuf, 512);
                                curPing.socket.receive(replyPacket);
                                boolean isUsableResponse = false;
                                if (responseBuf[0] == ((byte) (curPing.packetId >> 8)) && responseBuf[1] == ((byte) curPing.packetId)) {
                                    isUsableResponse = true;
                                } else {
                                    if (DBG) {
                                        log("response ID doesn't match with query ID.");
                                    }
                                    Iterator<ActivePing> it = this.mActivePings.iterator();
                                    while (true) {
                                        if (it.hasNext()) {
                                            ActivePing activePingForIdCheck = it.next();
                                            if (responseBuf[0] == ((byte) (activePingForIdCheck.packetId >> 8)) && responseBuf[1] == ((byte) activePingForIdCheck.packetId) && curPing.url != null && curPing.url.equals(activePingForIdCheck.url)) {
                                                log("response ID didn't match, but DNS response is usable.");
                                                isUsableResponse = true;
                                            }
                                        }
                                    }
                                }
                                if (!isUsableResponse) {
                                    log("response ID didn't match, ignoring packet");
                                } else if ((responseBuf[3] & 15) != 0 || (responseBuf[6] == 0 && responseBuf[7] == 0)) {
                                    if (DBG) {
                                        loge("Reply code is not 0(No Error) or Answer Record Count is 0");
                                    }
                                    curPing.result = -3;
                                } else {
                                    curPing.result = Integer.valueOf((int) (SystemClock.elapsedRealtime() - curPing.start));
                                    updateDnsDB((byte[]) responseBuf.clone(), replyPacket.getLength(), curPing.url);
                                    if (isDnsResponsePrivateAddress(curPing.url)) {
                                        curPing.result = 2;
                                    }
                                }
                            } catch (SocketTimeoutException e3) {
                            } catch (Exception e4) {
                                if (DBG) {
                                    log("DnsPinger.pingDns got socket exception: " + e4);
                                }
                                curPing.result = -2;
                            }
                        }
                        Iterator<ActivePing> iter = this.mActivePings.iterator();
                        while (iter.hasNext()) {
                            ActivePing curPing2 = iter.next();
                            if (curPing2.result != null) {
                                if ((curPing2.packetId & s) != s || curPing2.result.intValue() <= 0) {
                                    sendResponse(curPing2.internalId, curPing2.packetId, curPing2.result.intValue());
                                } else {
                                    Object obj2 = this.lock;
                                    synchronized (obj2) {
                                        try {
                                            List<DnsResult> list = this.DnsResultMap.get(curPing2.url);
                                            if (list == null || list.size() <= 0) {
                                                obj = obj2;
                                                if (DBG) {
                                                    Log.e(this.TAG, "There are no results about " + curPing2.url);
                                                }
                                                sendResponse(curPing2.internalId, curPing2.packetId, -2);
                                            } else {
                                                try {
                                                    obj = obj2;
                                                    try {
                                                        sendResponse(curPing2.internalId, curPing2.packetId, curPing2.result.intValue(), curPing2.url, sRandom.nextInt(this.DnsResultMap.get(curPing2.url).size()), 0);
                                                    } catch (Exception e5) {
                                                    }
                                                } catch (Exception e6) {
                                                    obj = obj2;
                                                }
                                            }
                                        } catch (Throwable th) {
                                            th = th;
                                            throw th;
                                        }
                                    }
                                }
                                curPing2.socket.close();
                                iter.remove();
                            } else if (SystemClock.elapsedRealtime() > curPing2.start + ((long) curPing2.timeout)) {
                                sendResponse(curPing2.internalId, curPing2.packetId, -1, curPing2.url);
                                curPing2.socket.close();
                                iter.remove();
                            }
                            s = 1;
                        }
                        if (!this.mActivePings.isEmpty()) {
                            sendMessageDelayed(obtainMessage(ACTION_LISTEN_FOR_RESPONSE, this.mEventCounter, 0), 200);
                            break;
                        }
                    }
                    break;
                case ACTION_CANCEL_ALL_PINGS /*{ENCODED_INT: 593923}*/:
                    for (ActivePing activePing : this.mActivePings) {
                        activePing.socket.close();
                    }
                    this.mActivePings.clear();
                    break;
            }
        } finally {
            TrafficStats.setThreadStatsTag(oldTag);
        }
    }

    public List<InetAddress> getDnsList() {
        LinkProperties curLinkProps = getCurrentLinkProperties();
        if (curLinkProps == null) {
            loge("getCurLinkProperties:: LP for type" + this.mConnectionType + " is null!");
            return this.mDefaultDns;
        }
        Collection<InetAddress> dnses = curLinkProps.getDnsServers();
        if (dnses != null && dnses.size() != 0) {
            return new ArrayList(dnses);
        }
        loge("getDns::LinkProps has null dns - returning default");
        return this.mDefaultDns;
    }

    public int pingDnsAsync(InetAddress dns, int timeout, int delay) {
        int id = sCounter.incrementAndGet();
        updateDnsResultMap("www.google.com");
        sendMessageDelayed(obtainMessage(ACTION_PING_DNS, id, timeout, new DnsArg(dns, this.mCurrentToken.get(), "www.google.com")), (long) delay);
        return id;
    }

    public int pingDnsAsyncSpecificForce(InetAddress dns, int timeout, int delay, String url) {
        int id = sCounter.incrementAndGet();
        sendMessageDelayed(obtainMessage(ACTION_PING_DNS_SPECIFIC, id, timeout, new DnsArg(dns, this.mCurrentToken.get(), url)), (long) delay);
        return id;
    }

    public int pingDnsAsyncSpecific(InetAddress dns, int timeout, int delay, String url) {
        int numOfResults;
        int id = sCounter.incrementAndGet();
        try {
            InetAddress addr = NetworkUtils.numericToInetAddress(url);
            if (DBG) {
                log("URL is already an IP address. " + url);
            }
            this.mTarget.sendMessageDelayed(obtainMessage(DNS_PING_RESULT_SPECIFIC, id, 3, addr), 50);
            return id;
        } catch (IllegalArgumentException e) {
            synchronized (this.lock) {
                if (this.DnsResultMap.get(url) == null) {
                    if (DBG) {
                        log("DNS Result Hashmap - NO HIT!!! SENDING DNS QUERY!  " + url);
                    }
                    sendMessageDelayed(obtainMessage(ACTION_PING_DNS_SPECIFIC, id, timeout, new DnsArg(dns, this.mCurrentToken.get(), url)), (long) delay);
                } else {
                    updateDnsResultMap(url);
                    if (this.DnsResultMap.get(url) != null) {
                        numOfResults = this.DnsResultMap.get(url).size();
                    } else {
                        numOfResults = 0;
                    }
                    if (numOfResults == 0) {
                        if (DBG) {
                            log("DNS Result Hashmap - HIT!!! BUT NO RESULTS   (" + numOfResults + ")" + url);
                        }
                        sendMessageDelayed(obtainMessage(ACTION_PING_DNS_SPECIFIC, id, timeout, new DnsArg(dns, this.mCurrentToken.get(), url)), (long) delay);
                    } else {
                        if (DBG) {
                            log("DNS Result Hashmap - HIT!!! USE PREVIOUS RESULT   (" + numOfResults + ")" + url);
                        }
                        sendResponse(id, -11111, 1, url, sRandom.nextInt(numOfResults), 50);
                    }
                }
                return id;
            }
        }
    }

    public void clear() {
        synchronized (this.lock) {
            this.DnsResultMap.clear();
            MostRecentDnsResultMap.clear();
        }
    }

    public void cancelPings() {
        this.mCurrentToken.incrementAndGet();
        obtainMessage(ACTION_CANCEL_ALL_PINGS).sendToTarget();
    }

    private void sendResponse(int internalId, int externalId, int responseVal) {
        if (DBG) {
            log("Responding to packet " + internalId + " externalId " + externalId + " and val " + responseVal);
        }
        if ((externalId & 1) == 1) {
            this.mTarget.sendMessage(obtainMessage(DNS_PING_RESULT_SPECIFIC, internalId, responseVal, null));
        } else {
            this.mTarget.sendMessage(obtainMessage(593920, internalId, responseVal));
        }
    }

    private void sendResponse(int internalId, int externalId, int responseVal, String url, int index, int delay) {
        if (DBG) {
            log("Responding to packet " + internalId + " externalId " + externalId + " and val " + responseVal);
            StringBuilder sb = new StringBuilder();
            sb.append("SPECIFIC DNS PING: url - ");
            sb.append(url);
            sb.append(", responseVal : ");
            sb.append(responseVal);
            log(sb.toString());
        }
        try {
            synchronized (this.lock) {
                this.mTarget.sendMessageDelayed(obtainMessage(DNS_PING_RESULT_SPECIFIC, internalId, responseVal, this.DnsResultMap.get(url).get(index).resultIp), (long) delay);
            }
        } catch (Exception e) {
        }
    }

    private void sendResponse(int internalId, int externalId, int responseVal, String url) {
        DnsResult res;
        if (DBG) {
            log("Responding to packet " + internalId + " externalId " + externalId + " val " + responseVal + " url " + url);
        }
        InetAddress resultIp = null;
        resultIp = null;
        resultIp = null;
        synchronized (this.lock) {
            if (responseVal == -1) {
                if (MostRecentDnsResultMap.containsKey(url) && (res = MostRecentDnsResultMap.get(url)) != null) {
                    resultIp = res.resultIp;
                    if (DBG) {
                        log("Sending most recent DNS result, " + resultIp.toString() + ", expired " + (System.currentTimeMillis() - res.ttl) + " msec ago.");
                    }
                }
            }
        }
        if ((externalId & 1) == 1) {
            this.mTarget.sendMessage(obtainMessage(DNS_PING_RESULT_SPECIFIC, internalId, responseVal, resultIp));
        } else {
            this.mTarget.sendMessage(obtainMessage(593920, internalId, responseVal));
        }
    }

    public void setCurrentLinkProperties(LinkProperties lp) {
        if (lp != null) {
            String str = this.TAG;
            Log.d(str, "setCurrentLinkProperties: lp=" + lp);
        }
        this.mLp = lp;
    }

    private LinkProperties getCurrentLinkProperties() {
        LinkProperties linkProperties = this.mLp;
        if (linkProperties != null) {
            return linkProperties;
        }
        if (this.mConnectivityManager == null) {
            this.mConnectivityManager = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        }
        return this.mConnectivityManager.getLinkProperties(this.mConnectionType);
    }

    private InetAddress getDefaultDns() {
        String dns = Settings.Global.getString(this.mContext.getContentResolver(), "default_dns_server");
        if (dns == null || dns.length() == 0) {
            dns = this.mContext.getResources().getString(17039927);
        }
        try {
            return NetworkUtils.numericToInetAddress(dns);
        } catch (IllegalArgumentException e) {
            loge("getDefaultDns::malformed default dns address");
            return null;
        }
    }

    private void updateDnsQuery(String url) {
        byte[] header = {0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0};
        byte[] trailer = {0, 0, 1, 0, 1};
        int length = url.length();
        byte blockSize = 0;
        byte[] middle = (byte[]) ('.' + url).getBytes().clone();
        for (int i = length; i >= 0; i--) {
            if (middle[i] == 46) {
                middle[i] = blockSize;
                blockSize = 0;
            } else {
                blockSize = (byte) (blockSize + 1);
            }
        }
        byte[] query = new byte[(length + 18)];
        System.arraycopy(header, 0, query, 0, 12);
        System.arraycopy(middle, 0, query, 12, length + 1);
        System.arraycopy(trailer, 0, query, length + 13, 5);
        this.mDnsQuery = (byte[]) query.clone();
    }

    private void updateDnsDB(byte[] buf, int length, String reqUrl) {
        int currPos;
        int i;
        long currTime = System.currentTimeMillis();
        int currPos2 = 0 + 1;
        int rrLength = ((buf[0] & 255) << 8) + (buf[currPos2] & 255);
        int currPos3 = currPos2 + 1;
        int currPos4 = currPos3 + 1;
        int flag = ((buf[currPos3] & 255) << 8) + (buf[currPos4] & 255);
        int currPos5 = currPos4 + 1;
        int currPos6 = currPos5 + 1;
        int numOfQuestion = ((buf[currPos5] & 255) << 8) + (buf[currPos6] & 255);
        int currPos7 = currPos6 + 1;
        int currPos8 = currPos7 + 1;
        int numOfAnswerRR = ((buf[currPos7] & 255) << 8) + (buf[currPos8] & 255);
        int currPos9 = currPos8 + 1;
        int currPos10 = currPos9 + 1;
        int i2 = ((buf[currPos9] & 255) << 8) + (buf[currPos10] & 255);
        int currPos11 = currPos10 + 1;
        int currPos12 = currPos11 + 1;
        int i3 = ((buf[currPos11] & 255) << 8) + (buf[currPos12] & 255);
        StringBuilder url = new StringBuilder();
        while (true) {
            currPos = currPos12 + 1;
            char c = 0;
            if (buf[currPos] == 0) {
                break;
            }
            int i4 = 1;
            while (i4 <= buf[currPos]) {
                Object[] objArr = new Object[1];
                objArr[c] = Byte.valueOf(buf[currPos + i4]);
                url.append(String.format("%c", objArr));
                i4++;
                c = 0;
            }
            url.append('.');
            currPos12 = currPos + buf[currPos];
        }
        url.deleteCharAt(url.length() - 1);
        url.toString().equals(reqUrl);
        int currPos13 = currPos + 4;
        List<DnsResult> mDnsResultList = new ArrayList<>();
        StringBuilder dbgShowResult = new StringBuilder();
        int i5 = 0;
        while (true) {
            if (i5 >= numOfAnswerRR) {
                break;
            }
            if (currPos13 + 12 >= 512) {
                break;
            }
            int currPos14 = currPos13 + 1;
            if ((buf[currPos14] & 192) == 192) {
                currPos14++;
                i = 1;
            } else {
                do {
                    i = 1;
                    currPos14++;
                } while (buf[currPos14] != 0);
            }
            int currPos15 = currPos14 + i;
            int currPos16 = currPos15 + i;
            int rrType = ((buf[currPos15] & 255) << 8) + (buf[currPos16] & 255);
            int currPos17 = currPos16 + 1;
            int currPos18 = currPos17 + 1;
            int i6 = ((buf[currPos17] & 255) << 8) + (buf[currPos18] & 255);
            int currPos19 = currPos18 + 1;
            int currPos20 = currPos19 + 1;
            int currPos21 = currPos20 + 1;
            int currPos22 = currPos21 + 1;
            int rrTtl = ((buf[currPos19] & 255) << 24) + ((buf[currPos20] & 255) << 16) + ((buf[currPos21] & 255) << 8) + (buf[currPos22] & 255);
            int currPos23 = currPos22 + 1;
            currPos13 = currPos23 + 1;
            int rrLength2 = ((buf[currPos23] & 255) << 8) + (buf[currPos13] & 255);
            if (currPos13 + rrLength2 >= 512) {
                break;
            }
            if (rrType == 1) {
                StringBuilder ipString = new StringBuilder();
                int currPos24 = currPos13 + 1;
                ipString.append(Integer.toString(buf[currPos24] & 255));
                ipString.append('.');
                int currPos25 = currPos24 + 1;
                ipString.append(Integer.toString(buf[currPos25] & 255));
                ipString.append('.');
                int currPos26 = currPos25 + 1;
                ipString.append(Integer.toString(buf[currPos26] & 255));
                ipString.append('.');
                int currPos27 = currPos26 + 1;
                ipString.append(Integer.toString(buf[currPos27] & 255));
                mDnsResultList.add(new DnsResult(NetworkUtils.numericToInetAddress(ipString.toString()), ((long) (rrTtl * 1000)) + currTime));
                dbgShowResult.append("[");
                dbgShowResult.append(ipString.toString());
                dbgShowResult.append("] ");
                currPos13 = currPos27;
            } else {
                StringBuilder rrData = new StringBuilder();
                for (int j = 0; j < rrLength2; j++) {
                    rrData.append('[');
                    currPos13++;
                    rrData.append(String.format("%02X", Byte.valueOf(buf[currPos13])));
                    rrData.append(']');
                }
            }
            i5++;
            rrLength = rrLength;
            flag = flag;
            numOfQuestion = numOfQuestion;
            numOfAnswerRR = numOfAnswerRR;
        }
        if (DBG) {
            log(getKernelTime() + "DNS Result - " + url.toString() + ", " + dbgShowResult.toString());
        }
        synchronized (this.lock) {
            if (!this.DnsResultMap.containsKey(reqUrl)) {
                this.DnsResultMap.put(reqUrl, mDnsResultList);
            } else {
                for (int i7 = 0; i7 < mDnsResultList.size(); i7++) {
                    this.DnsResultMap.get(reqUrl).add(mDnsResultList.get(i7));
                }
            }
            if (!isDnsResponsePrivateAddress(reqUrl)) {
                MostRecentDnsResultMap.put(reqUrl, mDnsResultList.get(0));
                for (String str : MostRecentDnsResultMap.keySet()) {
                }
            }
            if (DBG) {
                log("Hashmap DnsResultMap contains " + this.DnsResultMap.size() + " entries, url: " + reqUrl + " - " + this.DnsResultMap.get(reqUrl).size() + " IPs");
            }
        }
    }

    private void updateDnsResultMap(String url) {
        synchronized (this.lock) {
            List<DnsResult> mDnsResultList = this.DnsResultMap.get(url);
            long currTime = System.currentTimeMillis();
            if (mDnsResultList != null) {
                for (int i = mDnsResultList.size() - 1; i >= 0; i--) {
                    int ipByte1st = mDnsResultList.get(i).resultIp.getAddress()[0] & 255;
                    int ipByte2nd = mDnsResultList.get(i).resultIp.getAddress()[1] & 255;
                    int ipByte3rd = mDnsResultList.get(i).resultIp.getAddress()[2] & 255;
                    int ipByte4th = mDnsResultList.get(i).resultIp.getAddress()[3] & 255;
                    if (ipByte1st != 10 && (!(ipByte1st == 192 && ipByte2nd == 168) && (ipByte1st != 172 || ipByte2nd < 16 || ipByte2nd > 31))) {
                        if (ipByte1st != 1 || ipByte2nd != 33 || ipByte3rd != 203 || ipByte4th != 39) {
                            if (currTime > mDnsResultList.get(i).ttl) {
                                mDnsResultList.remove(i);
                            }
                        }
                    }
                    mDnsResultList.remove(i);
                }
            }
        }
    }

    private boolean isDnsResponsePrivateAddress(String url) {
        synchronized (this.lock) {
            List<DnsResult> mDnsResultList = this.DnsResultMap.get(url);
            if (mDnsResultList != null) {
                for (int i = mDnsResultList.size() - 1; i >= 0; i--) {
                    int ipByte1st = mDnsResultList.get(i).resultIp.getAddress()[0] & 255;
                    int ipByte2nd = mDnsResultList.get(i).resultIp.getAddress()[1] & 255;
                    int ipByte3rd = mDnsResultList.get(i).resultIp.getAddress()[2] & 255;
                    int ipByte4th = mDnsResultList.get(i).resultIp.getAddress()[3] & 255;
                    if (ipByte1st != 10 && (!(ipByte1st == 192 && ipByte2nd == 168) && (ipByte1st != 172 || ipByte2nd < 16 || ipByte2nd > 31))) {
                        if (ipByte1st == 1 && ipByte2nd == 33 && ipByte3rd == 203 && ipByte4th == 39) {
                        }
                    }
                    if (DBG) {
                        log(url + " - Dns Response with Private Network IP Address !!! - " + ipByte1st + "." + ipByte2nd + "." + ipByte3rd + "." + ipByte4th);
                    }
                    return true;
                }
            }
            return false;
        }
    }

    public String macToString(byte[] mac) {
        String macAddr = "";
        if (mac == null) {
            return null;
        }
        for (int i = 0; i < mac.length; i++) {
            String hexString = "0" + Integer.toHexString(mac[i]);
            macAddr = macAddr + hexString.substring(hexString.length() - 2);
            if (i != mac.length - 1) {
                macAddr = macAddr + ":";
            }
        }
        return macAddr;
    }

    private void log(String s) {
        Log.d(this.TAG, s);
    }

    private void loge(String s) {
        Log.e(this.TAG, s);
    }

    private String getKernelTime() {
        return "(" + (((double) (System.nanoTime() / 1000000)) / 1000.0d) + ") ";
    }
}
