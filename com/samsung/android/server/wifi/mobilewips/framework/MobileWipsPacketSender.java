package com.samsung.android.server.wifi.mobilewips.framework;

import android.net.LinkProperties;
import android.net.TrafficStats;
import android.net.util.InterfaceParams;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.PacketSocketAddress;
import android.system.StructTimeval;
import android.util.Log;
import com.samsung.android.server.wifi.softap.DhcpPacket;
import com.samsung.android.server.wifi.softap.smarttethering.SemWifiApSmartUtil;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import libcore.io.IoBridge;

public class MobileWipsPacketSender {
    private static final int ARP_LENGTH = 28;
    private static final byte[] BOOTP_FLAGS = {Byte.MIN_VALUE, 0};
    private static final byte[] CI_YI_SI_AI_ADDR = {0, 0, 0, 0};
    private static final int CODE = 0;
    private static final byte[] DATA = {105, 46, 9, 0, 0, 0, 0, 0, SemWifiApSmartUtil.BLE_BATT_2, 17, 18, 19, 20, 21, 22, 23, SemWifiApSmartUtil.BLE_BATT_3, 25, 26, 27, 28, 29, 30, 31, SemWifiApSmartUtil.BLE_BATT_4, 33, 34, 35, 36, 37, 38, 39, SemWifiApSmartUtil.BLE_BATT_5, 41, 42, 43, 44, 45, 46, 47, SemWifiApSmartUtil.BLE_BATT_6, 49, 50, 51, 52, 53, 54, 55};
    private static final int DHCP_OPTION_START = 282;
    private static final int DNS_DPORT = 53;
    private static final int DNS_IPV4_MSG_TYPE_LOCATION = 42;
    private static final int DPORT = 67;
    private static final int ETHERNET_TYPE = 1;
    private static final byte[] ETHER_ARP_TYPE = {8, 6};
    private static final int ETHER_HEADER_LENGTH = 14;
    private static final byte[] ETHER_IP_TYPE = {8, 0};
    private static final int ETH_IPV4_MAC_SRC_LOCATION = 6;
    private static final byte[] FLAGS_FRAGMENT_OFFSET = {SemWifiApSmartUtil.BLE_WIFI, 0};
    private static final int HOPS = 0;
    private static final int HW_ADDR_LENGTH = 6;
    private static final int HW_TYPE = 1;
    private static final int ICMP_CHECKSUM = 0;
    private static final int ICMP_HEADER_LENGTH = 64;
    private static final int ICMP_REPLY_TTL_LOCATION = 22;
    private static final byte[] IDENTIFICATION = {-77, -40};
    private static final byte[] IDENTIFIER = {88, 6};
    private static final int IDENTIFIER_LOCATION = 38;
    private static final int IPV4_LENGTH = 4;
    private static final int IP_CHECKSUM = 0;
    private static final int IP_HEADER_LENGTH = 20;
    private static final int JAVA_IP_TTL = 25;
    private static final int MAC_ADDR_LENGTH = 6;
    private static final byte[] MAGIC_COOKIE = {99, -126, 83, 99};
    private static final int MAX_LENGTH = 1500;
    private static final int MSG_TYPE = 1;
    private static final int MSG_TYPE_LOCATION = 42;
    private static final int MSG_TYPE_OFFER = 2;
    private static final int MSG_TYPE_REQUEST = 1;
    private static final int OPTION_DISCOVER = 53;
    private static final int OPTION_DISCOVER_DHCP = 1;
    private static final int OPTION_DISCOVER_LENGTH = 1;
    private static final int OPTION_END = 255;
    private static final int OPTION_ROUTER = 3;
    private static final int OPTION_VENDOR = 43;
    private static final int PROTOCOL = 1;
    private static final int SECS = 0;
    private static final int SEQUENCE_LOCATION = 40;
    private static final byte[] SEQUENCE_NUMBER = {0, 2};
    private static final int SPORT = 68;
    private static final String TAG = "MobileWips::FrameworkPktSender";
    private static final byte[] TCP_ACK_NUMBER = {0, 0, 0, 100};
    private static final int TCP_CHECKSUM = 0;
    private static final int TCP_DPORT = 80;
    private static final int TCP_DPORT_DNS = 53;
    private static final int TCP_HEADER_LENGTH = 20;
    private static final byte[] TCP_HEADER_LENGTH_FLAGS = {80, 2};
    private static final int TCP_PROTOCOL = 6;
    private static final byte[] TCP_SEQ_NUMBER = {0, 0, 0, 100};
    private static final int TCP_SPORT = 65000;
    private static final int TCP_TOTAL_LENGTH = 40;
    private static final int TCP_WINDOW_SIZE = 4000;
    private static final byte[] TIMESTAMP = {-66, -29, 119, 90, 0, 0, 0, 0};
    private static final int TOTAL_LENGTH = 84;
    private static final byte[] TRANSACTION_ID = {-122, 22, 6, 2};
    private static final int TRANSACTION_ID_LOCATION = 46;
    private static final int TTL = 64;
    private static final int TYPE = 8;
    private static final int ToS = 0;
    private static final int UDP_CHECKSUM = 0;
    private static final int UDP_IPV4_DST_PORT_LOCATION = 36;
    private static final int UDP_IPV4_SRC_PORT_LOCATION = 34;
    private static final byte[] UDP_IP_DST_ADDR = {-1, -1, -1, -1};
    private static final byte[] UDP_IP_SRC_ADDR = {0, 0, 0, 0};
    private static final int UDP_LENGTH = 252;
    private static final int UDP_PROTOCOL = 17;
    private static final int UDP_TOTAL_LENGTH = 272;
    private static final int VERSION_HEADER_LENGTH = 69;
    private byte[] L2_BROADCAST = {-1, -1, -1, -1, -1, -1};
    private byte[] SRC_ADDR = new byte[0];
    private FileDescriptor mSocket;
    private FileDescriptor mSocketArpSniff;
    private FileDescriptor mSocketArpSniffRecv;
    private FileDescriptor mSocketDhcp;
    private FileDescriptor mSocketIcmp;
    private FileDescriptor mSocketRecv;

    private void makeEthernet(ByteBuffer etherBuf, byte[] dstMAC, byte[] srcMAC, byte[] ethernetType) {
        if (etherBuf != null && dstMAC != null && srcMAC != null) {
            etherBuf.put(dstMAC);
            etherBuf.put(srcMAC);
            etherBuf.put(ethernetType);
            etherBuf.flip();
        }
    }

    private void makeARP(ByteBuffer buf, byte[] senderMAC, byte[] senderIP, byte[] targetIP) {
        if (buf != null && senderMAC != null && senderIP != null && targetIP != null) {
            buf.putShort(1);
            buf.putShort((short) OsConstants.ETH_P_IP);
            buf.put((byte) 6);
            buf.put((byte) 4);
            buf.putShort(1);
            buf.put(senderMAC);
            buf.put(senderIP);
            buf.put(new byte[6]);
            buf.put(targetIP);
            buf.flip();
        }
    }

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(x);
        return buffer.array();
    }

    private void makeIP(ByteBuffer ipBuf, int totalLength, int nextProtocol, byte[] srcIP, byte[] dstIP) {
        if (ipBuf != null && srcIP != null && dstIP != null) {
            ByteBuffer tmpBuf = ByteBuffer.allocate(2);
            tmpBuf.clear();
            tmpBuf.order(ByteOrder.BIG_ENDIAN);
            ipBuf.put((byte) 69);
            ipBuf.put((byte) 0);
            ipBuf.putShort((short) totalLength);
            ipBuf.put(IDENTIFICATION);
            ipBuf.put(FLAGS_FRAGMENT_OFFSET);
            ipBuf.put(SemWifiApSmartUtil.BLE_WIFI);
            ipBuf.put((byte) nextProtocol);
            ipBuf.putShort(0);
            ipBuf.put(srcIP);
            ipBuf.put(dstIP);
            ipBuf.flip();
            byte[] resIpChecksum = MobileWipsFrameworkUtil.longToBytes(calculationChecksum(ipBuf.array()));
            tmpBuf.put(resIpChecksum[6]);
            tmpBuf.put(resIpChecksum[7]);
            ipBuf.putShort(10, tmpBuf.getShort(0));
        }
    }

    private void makeUDP(ByteBuffer udpBuf, int sourcePort, int destinationPort, int lenght, int checkSum) {
        if (udpBuf != null) {
            udpBuf.putShort((short) sourcePort);
            udpBuf.putShort((short) destinationPort);
            udpBuf.putShort((short) lenght);
            udpBuf.putShort((short) checkSum);
            udpBuf.flip();
        }
    }

    private void makeTCP(ByteBuffer tcpBuf, ByteBuffer pseudoBuf, int tcpDestinationPort) {
        if (tcpBuf != null && pseudoBuf != null) {
            ByteBuffer tmpBuf = ByteBuffer.allocate(2);
            tmpBuf.clear();
            tmpBuf.order(ByteOrder.BIG_ENDIAN);
            tcpBuf.putShort(-536);
            tcpBuf.putShort((short) tcpDestinationPort);
            tcpBuf.put(TCP_SEQ_NUMBER);
            tcpBuf.put(TCP_ACK_NUMBER);
            tcpBuf.put(TCP_HEADER_LENGTH_FLAGS);
            tcpBuf.putShort(4000);
            tcpBuf.putShort(0);
            tcpBuf.putShort(0);
            tcpBuf.flip();
            pseudoBuf.put(tcpBuf);
            pseudoBuf.flip();
            byte[] resTcpChecksum = MobileWipsFrameworkUtil.longToBytes(calculationChecksum(pseudoBuf.array()));
            tmpBuf.put(resTcpChecksum[6]);
            tmpBuf.put(resTcpChecksum[7]);
            tcpBuf.putShort(16, tmpBuf.getShort(0));
            tcpBuf.flip();
        }
    }

    private void makePsuedoHeader(ByteBuffer pseudoBuf, byte[] srcIP, byte[] dstIP, int protocol, int protoHeaderLength) {
        if (pseudoBuf != null && srcIP != null && dstIP != null) {
            pseudoBuf.put(srcIP);
            pseudoBuf.put(dstIP);
            pseudoBuf.put((byte) 0);
            pseudoBuf.put((byte) protocol);
            pseudoBuf.putShort((short) protoHeaderLength);
        }
    }

    private void makeICMP(ByteBuffer icmpBuf) {
        if (icmpBuf != null) {
            ByteBuffer tmpBuf = ByteBuffer.allocate(2);
            tmpBuf.clear();
            tmpBuf.order(ByteOrder.BIG_ENDIAN);
            icmpBuf.put((byte) 8);
            icmpBuf.put((byte) 0);
            icmpBuf.putShort(0);
            icmpBuf.put(IDENTIFIER);
            icmpBuf.put(SEQUENCE_NUMBER);
            icmpBuf.put(TIMESTAMP);
            icmpBuf.put(DATA);
            icmpBuf.flip();
            byte[] resIcmpChecksum = MobileWipsFrameworkUtil.longToBytes(calculationChecksum(icmpBuf.array()));
            tmpBuf.put(resIcmpChecksum[6]);
            tmpBuf.put(resIcmpChecksum[7]);
            icmpBuf.putShort(2, tmpBuf.getShort(0));
        }
    }

    private void makeDHCP(ByteBuffer dhcpBuf, byte[] srcMAC) {
        if (dhcpBuf != null && srcMAC != null) {
            dhcpBuf.put((byte) 1);
            dhcpBuf.put((byte) 1);
            dhcpBuf.put((byte) 6);
            dhcpBuf.put((byte) 0);
            dhcpBuf.put(TRANSACTION_ID);
            dhcpBuf.putShort(0);
            dhcpBuf.put(BOOTP_FLAGS);
            dhcpBuf.put(CI_YI_SI_AI_ADDR);
            dhcpBuf.put(CI_YI_SI_AI_ADDR);
            dhcpBuf.put(CI_YI_SI_AI_ADDR);
            dhcpBuf.put(CI_YI_SI_AI_ADDR);
            dhcpBuf.put(srcMAC);
            dhcpBuf.position(DhcpPacket.MIN_PACKET_LENGTH_BOOTP);
            dhcpBuf.put(MAGIC_COOKIE);
            dhcpBuf.put((byte) 53);
            dhcpBuf.put((byte) 1);
            dhcpBuf.put((byte) 1);
            dhcpBuf.put((byte) -1);
            dhcpBuf.flip();
        }
    }

    private byte[] macStringToByteArray(String dstMac) {
        byte[] dstMAC = new byte[6];
        if (dstMac != null) {
            for (int i = 0; i < 6; i++) {
                dstMAC[i] = (byte) Integer.parseInt(dstMac.substring(i * 3, (i * 3) + 2), 16);
            }
        }
        return dstMAC;
    }

    /* JADX WARNING: Code restructure failed: missing block: B:102:0x02d8, code lost:
        libcore.io.IoBridge.closeAndSignalBlockedThreads(r28.mSocketRecv);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:68:0x0225, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:69:0x0227, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:80:0x0244, code lost:
        android.util.Log.e(com.samsung.android.server.wifi.mobilewips.framework.MobileWipsPacketSender.TAG, "Exception " + java.lang.Thread.currentThread().getStackTrace()[2].getLineNumber() + r0);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:84:0x0272, code lost:
        android.util.Log.d(com.samsung.android.server.wifi.mobilewips.framework.MobileWipsPacketSender.TAG, "ARP result(" + r7 + ") = " + r0.get(r7));
     */
    /* JADX WARNING: Code restructure failed: missing block: B:85:0x0290, code lost:
        r7 = r7 + 1;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:88:0x0297, code lost:
        libcore.io.IoBridge.closeAndSignalBlockedThreads(r28.mSocket);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:91:0x02a3, code lost:
        libcore.io.IoBridge.closeAndSignalBlockedThreads(r28.mSocketRecv);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:99:0x02cc, code lost:
        libcore.io.IoBridge.closeAndSignalBlockedThreads(r28.mSocket);
     */
    /* JADX WARNING: Failed to process nested try/catch */
    /* JADX WARNING: Removed duplicated region for block: B:102:0x02d8  */
    /* JADX WARNING: Removed duplicated region for block: B:108:0x02e6 A[Catch:{ IOException -> 0x02fb }] */
    /* JADX WARNING: Removed duplicated region for block: B:111:0x02f2 A[Catch:{ IOException -> 0x02fb }] */
    /* JADX WARNING: Removed duplicated region for block: B:69:0x0227 A[ExcHandler: RuntimeException (e java.lang.RuntimeException), Splitter:B:4:0x00a6] */
    /* JADX WARNING: Removed duplicated region for block: B:84:0x0272 A[Catch:{ all -> 0x02ae }] */
    /* JADX WARNING: Removed duplicated region for block: B:88:0x0297  */
    /* JADX WARNING: Removed duplicated region for block: B:91:0x02a3  */
    /* JADX WARNING: Removed duplicated region for block: B:99:0x02cc  */
    public List<String> sendArp(LinkProperties linkProperties, int timeoutMillis, byte[] gateway, byte[] myAddr, String myMac) {
        byte[] result_mac;
        boolean pushIn;
        List<String> result = new ArrayList<>();
        try {
            this.mSocket = Os.socket(OsConstants.AF_PACKET, OsConstants.SOCK_RAW, 0);
            InterfaceParams iParams = InterfaceParams.getByName(linkProperties.getInterfaceName());
            Os.bind(this.mSocket, new PacketSocketAddress((short) OsConstants.ETH_P_IP, iParams.index));
            this.mSocketRecv = Os.socket(OsConstants.AF_PACKET, OsConstants.SOCK_RAW, 0);
            Os.bind(this.mSocketRecv, new PacketSocketAddress((short) OsConstants.ETH_P_ARP, iParams.index));
            this.SRC_ADDR = getMacAddress("wlan0");
            long timeout = SystemClock.elapsedRealtime() + ((long) timeoutMillis);
            byte[] mMyMac = macStringToByteArray(myMac);
            ByteBuffer sumBuf = ByteBuffer.allocate(1500);
            ByteBuffer etherBuf = ByteBuffer.allocate(14);
            ByteBuffer arpBuf = ByteBuffer.allocate(28);
            sumBuf.clear();
            ByteBuffer sumBuf2 = sumBuf;
            sumBuf2.order(ByteOrder.BIG_ENDIAN);
            etherBuf.clear();
            etherBuf.order(ByteOrder.BIG_ENDIAN);
            arpBuf.clear();
            arpBuf.order(ByteOrder.BIG_ENDIAN);
            makeEthernet(etherBuf, this.L2_BROADCAST, this.SRC_ADDR, ETHER_ARP_TYPE);
            try {
                makeARP(arpBuf, mMyMac, myAddr, gateway);
                sumBuf2.put(etherBuf).put(arpBuf);
                sumBuf2.flip();
                Os.sendto(this.mSocket, sumBuf2.array(), 0, sumBuf2.limit(), 0, new PacketSocketAddress(iParams.index, this.L2_BROADCAST));
                byte[] recvBuf = new byte[1500];
                byte[] result_mac2 = new byte[6];
                while (SystemClock.elapsedRealtime() < timeout) {
                    Os.setsockoptTimeval(this.mSocketRecv, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(timeout - SystemClock.elapsedRealtime()));
                    int readLen = 0;
                    readLen = Os.read(this.mSocketRecv, recvBuf, 0, recvBuf.length);
                    if (readLen >= 28 && recvBuf[14] == 0 && recvBuf[15] == 1 && recvBuf[16] == 8 && recvBuf[17] == 0 && recvBuf[18] == 6 && recvBuf[19] == 4 && recvBuf[20] == 0 && recvBuf[28] == gateway[0] && recvBuf[29] == gateway[1] && recvBuf[30] == gateway[2] && recvBuf[31] == gateway[3]) {
                        result_mac = result_mac2;
                        System.arraycopy(recvBuf, 22, result_mac, 0, 6);
                        String convert_mac = MobileWipsFrameworkUtil.macToString(result_mac);
                        boolean pushIn2 = true;
                        int i = 0;
                        while (true) {
                            if (i >= result.size()) {
                                pushIn = pushIn2;
                                break;
                            } else if (result.get(i).contains(convert_mac)) {
                                pushIn = false;
                                break;
                            } else {
                                i++;
                                pushIn2 = pushIn2;
                            }
                        }
                        if (pushIn) {
                            if (recvBuf[21] == 1) {
                                result.add("REQ" + convert_mac);
                            } else if (recvBuf[21] == 2) {
                                result.add("REP" + convert_mac);
                            }
                        }
                    } else {
                        result_mac = result_mac2;
                    }
                    result_mac2 = result_mac;
                    sumBuf2 = sumBuf2;
                    iParams = iParams;
                }
                for (int i2 = 0; i2 < result.size(); i2++) {
                    Log.d(TAG, "ARP result(" + i2 + ") = " + result.get(i2));
                }
                try {
                    if (this.mSocket != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocket);
                    }
                    this.mSocket = null;
                    if (this.mSocketRecv != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocketRecv);
                    }
                    this.mSocketRecv = null;
                } catch (IOException e) {
                }
            } catch (Exception e2) {
            } catch (RuntimeException e3) {
            }
        } catch (RuntimeException e4) {
            e = e4;
            Log.e(TAG, "RuntimeException " + e);
            if (this.mSocket != null) {
            }
            this.mSocket = null;
            if (this.mSocketRecv != null) {
            }
            this.mSocketRecv = null;
            return result;
        } catch (Exception e5) {
            e = e5;
            try {
                Throwable cause = e.getCause();
                if (cause instanceof ErrnoException) {
                }
                int i3 = 0;
                while (i3 < result.size()) {
                }
                if (this.mSocket != null) {
                }
                this.mSocket = null;
                if (this.mSocketRecv != null) {
                }
                this.mSocketRecv = null;
                return result;
            } catch (Throwable th) {
                th = th;
                try {
                    if (this.mSocket != null) {
                    }
                    this.mSocket = null;
                    if (this.mSocketRecv != null) {
                    }
                    this.mSocketRecv = null;
                } catch (IOException e6) {
                }
                throw th;
            }
        } catch (Throwable th2) {
            th = th2;
            if (this.mSocket != null) {
                IoBridge.closeAndSignalBlockedThreads(this.mSocket);
            }
            this.mSocket = null;
            if (this.mSocketRecv != null) {
                IoBridge.closeAndSignalBlockedThreads(this.mSocketRecv);
            }
            this.mSocketRecv = null;
            throw th;
        }
        return result;
    }

    /* JADX WARNING: Code restructure failed: missing block: B:52:0x01a2, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:53:0x01a5, code lost:
        r0 = e;
     */
    /* JADX WARNING: Failed to process nested try/catch */
    /* JADX WARNING: Removed duplicated region for block: B:101:0x02b2  */
    /* JADX WARNING: Removed duplicated region for block: B:104:0x02be  */
    /* JADX WARNING: Removed duplicated region for block: B:113:0x02cf A[Catch:{ IOException -> 0x02e4 }] */
    /* JADX WARNING: Removed duplicated region for block: B:116:0x02db A[Catch:{ IOException -> 0x02e4 }] */
    /* JADX WARNING: Removed duplicated region for block: B:53:0x01a5 A[ExcHandler: RuntimeException (e java.lang.RuntimeException), Splitter:B:13:0x00d6] */
    /* JADX WARNING: Removed duplicated region for block: B:76:0x0215 A[Catch:{ all -> 0x0290 }] */
    /* JADX WARNING: Removed duplicated region for block: B:82:0x024e A[Catch:{ all -> 0x0290 }] */
    /* JADX WARNING: Removed duplicated region for block: B:86:0x0279  */
    /* JADX WARNING: Removed duplicated region for block: B:89:0x0285  */
    public synchronized List<String> sendArpToSniffing(LinkProperties linkProperties, int timeoutMillis, byte[] gateway, byte[] myAddr, String myMac) {
        List<String> result;
        Throwable th;
        Throwable cause;
        int i;
        int readLen;
        byte[] result_mac;
        result = new ArrayList<>();
        try {
            this.mSocketArpSniff = Os.socket(OsConstants.AF_PACKET, OsConstants.SOCK_RAW, 0);
            InterfaceParams iParams = InterfaceParams.getByName(linkProperties.getInterfaceName());
            Os.bind(this.mSocketArpSniff, new PacketSocketAddress((short) OsConstants.ETH_P_IP, iParams.index));
            this.mSocketArpSniffRecv = Os.socket(OsConstants.AF_PACKET, OsConstants.SOCK_RAW, 0);
            Os.bind(this.mSocketArpSniffRecv, new PacketSocketAddress((short) OsConstants.ETH_P_ARP, iParams.index));
            this.SRC_ADDR = getMacAddress("wlan0");
            long timeout = SystemClock.elapsedRealtime() + ((long) timeoutMillis);
            try {
                byte[] mMyMac = macStringToByteArray(myMac);
                ByteBuffer sumBuf = ByteBuffer.allocate(1500);
                ByteBuffer etherBuf = ByteBuffer.allocate(14);
                ByteBuffer arpBuf = ByteBuffer.allocate(28);
                sumBuf.clear();
                sumBuf.order(ByteOrder.BIG_ENDIAN);
                etherBuf.clear();
                etherBuf.order(ByteOrder.BIG_ENDIAN);
                arpBuf.clear();
                arpBuf.order(ByteOrder.BIG_ENDIAN);
                makeEthernet(etherBuf, this.L2_BROADCAST, this.SRC_ADDR, ETHER_ARP_TYPE);
                makeARP(arpBuf, mMyMac, myAddr, gateway);
                sumBuf.put(etherBuf).put(arpBuf);
                sumBuf.flip();
                Os.sendto(this.mSocketArpSniff, sumBuf.array(), 0, sumBuf.limit(), 0, new PacketSocketAddress(iParams.index, this.L2_BROADCAST));
                byte[] recvBuf = new byte[1500];
                byte[] result_mac2 = new byte[6];
                while (SystemClock.elapsedRealtime() < timeout) {
                    try {
                        Os.setsockoptTimeval(this.mSocketArpSniffRecv, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(timeout - SystemClock.elapsedRealtime()));
                        readLen = 0;
                        readLen = Os.read(this.mSocketArpSniffRecv, recvBuf, 0, recvBuf.length);
                    } catch (Exception e) {
                    } catch (RuntimeException e2) {
                    }
                    if (readLen >= 28 && recvBuf[14] == 0 && recvBuf[15] == 1 && recvBuf[16] == 8 && recvBuf[17] == 0 && recvBuf[18] == 6 && recvBuf[19] == 4 && recvBuf[20] == 0 && recvBuf[28] == gateway[0] && recvBuf[29] == gateway[1] && recvBuf[30] == gateway[2] && recvBuf[31] == gateway[3]) {
                        result_mac = result_mac2;
                        System.arraycopy(recvBuf, 22, result_mac, 0, 6);
                        String convert_mac = MobileWipsFrameworkUtil.macToString(result_mac);
                        if (recvBuf[21] == 1) {
                            result.add("REQ" + convert_mac);
                        } else if (recvBuf[21] == 2) {
                            result.add("REP" + convert_mac);
                        }
                    } else {
                        result_mac = result_mac2;
                    }
                    result_mac2 = result_mac;
                    iParams = iParams;
                }
                int i2 = 0;
                while (i2 < result.size()) {
                    Log.d(TAG, "ARP result(" + i2 + ") = " + result.get(i2));
                    i2++;
                    recvBuf = recvBuf;
                }
                try {
                    if (this.mSocketArpSniff != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocketArpSniff);
                    }
                    this.mSocketArpSniff = null;
                    if (this.mSocketArpSniffRecv != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocketArpSniffRecv);
                    }
                    this.mSocketArpSniffRecv = null;
                } catch (IOException e3) {
                }
            } catch (RuntimeException e4) {
                e = e4;
                try {
                    Log.e(TAG, "RuntimeException " + e);
                    if (this.mSocketArpSniff != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocketArpSniff);
                    }
                    this.mSocketArpSniff = null;
                    if (this.mSocketArpSniffRecv != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocketArpSniffRecv);
                    }
                    this.mSocketArpSniffRecv = null;
                    return result;
                } catch (Throwable th2) {
                    th = th2;
                    try {
                        if (this.mSocketArpSniff != null) {
                        }
                        this.mSocketArpSniff = null;
                        if (this.mSocketArpSniffRecv != null) {
                        }
                        this.mSocketArpSniffRecv = null;
                    } catch (IOException e5) {
                    }
                    throw th;
                }
            } catch (Exception e6) {
                e = e6;
                try {
                    cause = e.getCause();
                    if ((cause instanceof ErrnoException) || ((ErrnoException) cause).errno == OsConstants.EAGAIN) {
                        for (i = 0; i < result.size(); i++) {
                            Log.d(TAG, "ARP result(" + i + ") = " + result.get(i));
                        }
                    } else {
                        Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                    }
                    if (this.mSocketArpSniff != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocketArpSniff);
                    }
                    this.mSocketArpSniff = null;
                    if (this.mSocketArpSniffRecv != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocketArpSniffRecv);
                    }
                    this.mSocketArpSniffRecv = null;
                    return result;
                } catch (Throwable th3) {
                    th = th3;
                    if (this.mSocketArpSniff != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocketArpSniff);
                    }
                    this.mSocketArpSniff = null;
                    if (this.mSocketArpSniffRecv != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocketArpSniffRecv);
                    }
                    this.mSocketArpSniffRecv = null;
                    throw th;
                }
            }
        } catch (RuntimeException e7) {
            e = e7;
            Log.e(TAG, "RuntimeException " + e);
            if (this.mSocketArpSniff != null) {
            }
            this.mSocketArpSniff = null;
            if (this.mSocketArpSniffRecv != null) {
            }
            this.mSocketArpSniffRecv = null;
            return result;
        } catch (Exception e8) {
            e = e8;
            cause = e.getCause();
            if (cause instanceof ErrnoException) {
            }
            while (i < result.size()) {
            }
            if (this.mSocketArpSniff != null) {
            }
            this.mSocketArpSniff = null;
            if (this.mSocketArpSniffRecv != null) {
            }
            this.mSocketArpSniffRecv = null;
            return result;
        } catch (Throwable th4) {
            th = th4;
            if (this.mSocketArpSniff != null) {
            }
            this.mSocketArpSniff = null;
            if (this.mSocketArpSniffRecv != null) {
            }
            this.mSocketArpSniffRecv = null;
            throw th;
        }
        return result;
    }

    /* JADX INFO: Multiple debug info for r0v17 byte[]: [D('iParams' android.net.util.InterfaceParams), D('result_src_mac' byte[])] */
    /* JADX WARNING: Code restructure failed: missing block: B:20:0x019c, code lost:
        r0.add(r5);
        r0.add(java.lang.String.valueOf(r3[22] & 255));
        r4 = new java.lang.StringBuilder();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:21:0x01b1, code lost:
        r6 = r17;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:23:?, code lost:
        r4.append(r6);
        r4.append(r0.get(0));
     */
    /* JADX WARNING: Code restructure failed: missing block: B:24:0x01c4, code lost:
        r1 = r16;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:26:?, code lost:
        r4.append(r1);
        r4.append(r0.get(1));
        android.util.Log.d(com.samsung.android.server.wifi.mobilewips.framework.MobileWipsPacketSender.TAG, r4.toString());
     */
    /* JADX WARNING: Code restructure failed: missing block: B:27:0x01df, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:28:0x01e1, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:29:0x01e2, code lost:
        r1 = r16;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:44:0x022e, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:45:0x022f, code lost:
        r1 = " - ";
        r6 = "ICMP echo reply src : ";
     */
    /* JADX WARNING: Code restructure failed: missing block: B:51:0x0243, code lost:
        android.util.Log.e(com.samsung.android.server.wifi.mobilewips.framework.MobileWipsPacketSender.TAG, "Exception " + java.lang.Thread.currentThread().getStackTrace()[2].getLineNumber() + r0);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:54:0x026f, code lost:
        android.util.Log.d(com.samsung.android.server.wifi.mobilewips.framework.MobileWipsPacketSender.TAG, r6 + r0.get(0) + r1 + r0.get(1));
     */
    /* JADX WARNING: Code restructure failed: missing block: B:57:0x0299, code lost:
        libcore.io.IoBridge.closeAndSignalBlockedThreads(r28.mSocketIcmp);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:60:0x02a4, code lost:
        r0 = move-exception;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:61:0x02a5, code lost:
        android.util.Log.e(com.samsung.android.server.wifi.mobilewips.framework.MobileWipsPacketSender.TAG, "RuntimeException " + r0);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:62:0x02bb, code lost:
        if (r28.mSocketIcmp != null) goto L_0x02bd;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:63:0x02bd, code lost:
        libcore.io.IoBridge.closeAndSignalBlockedThreads(r28.mSocketIcmp);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:64:0x02c2, code lost:
        r28.mSocketIcmp = null;
     */
    /* JADX WARNING: Failed to process nested try/catch */
    /* JADX WARNING: Removed duplicated region for block: B:49:0x023a A[Catch:{ all -> 0x022a }] */
    /* JADX WARNING: Removed duplicated region for block: B:54:0x026f A[Catch:{ all -> 0x022a }] */
    /* JADX WARNING: Removed duplicated region for block: B:57:0x0299  */
    /* JADX WARNING: Removed duplicated region for block: B:60:0x02a4 A[EDGE_INSN: B:26:?->B:60:0x02a4 ?: BREAK  , ExcHandler: RuntimeException (r0v1 'e' java.lang.RuntimeException A[CUSTOM_DECLARE]), Splitter:B:1:0x000f] */
    public List<String> sendIcmp(LinkProperties linkProperties, int timeoutMillis, byte[] gateway, byte[] myAddr, String dstMac) {
        String str;
        String str2;
        String str3;
        List<String> result = new ArrayList<>();
        try {
            this.mSocketIcmp = Os.socket(OsConstants.AF_PACKET, OsConstants.SOCK_RAW, 0);
            InterfaceParams iParams = InterfaceParams.getByName(linkProperties.getInterfaceName());
            Os.bind(this.mSocketIcmp, new PacketSocketAddress((short) OsConstants.ETH_P_IP, iParams.index));
            byte[] SrcIp = myAddr;
            byte[] DstIp = gateway;
            this.SRC_ADDR = getMacAddress("wlan0");
            byte[] targetMac = macStringToByteArray(dstMac);
            long timeout = ((long) timeoutMillis) + SystemClock.elapsedRealtime();
            ByteBuffer sumBuf = ByteBuffer.allocate(1500);
            ByteBuffer etherBuf = ByteBuffer.allocate(14);
            ByteBuffer ipBuf = ByteBuffer.allocate(20);
            ByteBuffer icmpBuf = ByteBuffer.allocate(64);
            sumBuf.clear();
            sumBuf.order(ByteOrder.BIG_ENDIAN);
            etherBuf.clear();
            etherBuf.order(ByteOrder.BIG_ENDIAN);
            ipBuf.clear();
            ipBuf.order(ByteOrder.BIG_ENDIAN);
            icmpBuf.clear();
            icmpBuf.order(ByteOrder.BIG_ENDIAN);
            makeEthernet(etherBuf, targetMac, this.SRC_ADDR, ETHER_IP_TYPE);
            str = " - ";
            str2 = "ICMP echo reply src : ";
            makeIP(ipBuf, 84, 1, SrcIp, DstIp);
            makeICMP(icmpBuf);
            sumBuf.put(etherBuf).put(ipBuf).put(icmpBuf);
            sumBuf.flip();
            byte[] bytes = sumBuf.array();
            Log.d(TAG, "ICMP : " + MobileWipsFrameworkUtil.byteArrayToHexString(bytes));
            Os.sendto(this.mSocketIcmp, sumBuf.array(), 0, sumBuf.limit(), 0, new PacketSocketAddress(iParams.index, targetMac));
            byte[] recvBuf = new byte[1500];
            byte[] result_src_mac = new byte[6];
            byte[] result_dst_mac = new byte[6];
            while (true) {
                if (SystemClock.elapsedRealtime() >= timeout) {
                    break;
                }
                Os.setsockoptTimeval(this.mSocketIcmp, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(timeout - SystemClock.elapsedRealtime()));
                int readLen = Os.read(this.mSocketIcmp, recvBuf, 0, recvBuf.length);
                if (recvBuf[38] == IDENTIFIER[0] && recvBuf[39] == IDENTIFIER[1] && recvBuf[40] == SEQUENCE_NUMBER[0] && recvBuf[41] == SEQUENCE_NUMBER[1]) {
                    Log.d(TAG, "ICMP Recv (length: " + String.valueOf(readLen) + ") : " + MobileWipsFrameworkUtil.byteArrayToHexString(recvBuf) + " ");
                    System.arraycopy(recvBuf, 0, result_dst_mac, 0, 6);
                    System.arraycopy(recvBuf, 6, result_src_mac, 0, 6);
                    if (MobileWipsFrameworkUtil.compareByteArray(this.SRC_ADDR, result_dst_mac)) {
                        String convertMacToString = MobileWipsFrameworkUtil.macToString(result_src_mac);
                        if (convertMacToString != null) {
                            break;
                        }
                        str3 = str2;
                    } else {
                        str3 = str2;
                    }
                } else {
                    str3 = str2;
                }
                str = str;
                result_dst_mac = result_dst_mac;
                bytes = bytes;
                SrcIp = SrcIp;
                str2 = str3;
                DstIp = DstIp;
            }
            try {
                if (this.mSocketIcmp != null) {
                    IoBridge.closeAndSignalBlockedThreads(this.mSocketIcmp);
                }
                this.mSocketIcmp = null;
            } catch (IOException e) {
            }
        } catch (RuntimeException e2) {
        } catch (Exception e3) {
            e = e3;
            String str4 = str;
            String str5 = str2;
            try {
                Throwable cause = e.getCause();
                if (cause instanceof ErrnoException) {
                }
                if (result.size() == 2) {
                }
                if (this.mSocketIcmp != null) {
                }
                this.mSocketIcmp = null;
            } catch (Throwable th) {
                try {
                    if (this.mSocketIcmp != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocketIcmp);
                    }
                    this.mSocketIcmp = null;
                } catch (IOException e4) {
                }
                throw th;
            }
        }
        return result;
    }

    /* JADX WARNING: Removed duplicated region for block: B:103:0x032f A[Catch:{ IOException -> 0x0338 }] */
    /* JADX WARNING: Removed duplicated region for block: B:89:0x02f6 A[Catch:{ IOException -> 0x02ff }] */
    /* JADX WARNING: Removed duplicated region for block: B:98:0x0321  */
    public int sendDhcp(LinkProperties linkProperties, int timeoutMillis, byte[] myAddr, int equalOption, String equalString) {
        Throwable th;
        byte[] opString;
        byte[] bytes;
        byte[] opString2;
        int result = 0;
        try {
            this.mSocketDhcp = Os.socket(OsConstants.AF_PACKET, OsConstants.SOCK_RAW, 0);
            InterfaceParams iParams = InterfaceParams.getByName(linkProperties.getInterfaceName());
            Os.bind(this.mSocketDhcp, new PacketSocketAddress((short) OsConstants.ETH_P_IP, iParams.index));
            this.SRC_ADDR = getMacAddress("wlan0");
            byte[] opString3 = new byte[100];
            if (equalOption == -1 || equalString == null) {
                opString = opString3;
            } else {
                try {
                    opString = equalString.getBytes(StandardCharsets.UTF_8);
                } catch (RuntimeException e) {
                    e = e;
                    Log.e(TAG, "RuntimeException " + e);
                    if (this.mSocketDhcp != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocketDhcp);
                    }
                    this.mSocketDhcp = null;
                    return result;
                } catch (Exception e2) {
                    iParams = e2;
                    try {
                        Throwable cause = iParams.getCause();
                        if ((cause instanceof ErrnoException) && ((ErrnoException) cause).errno != OsConstants.EAGAIN) {
                            Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + iParams);
                        }
                        try {
                            if (this.mSocketDhcp != null) {
                                IoBridge.closeAndSignalBlockedThreads(this.mSocketDhcp);
                            }
                            this.mSocketDhcp = null;
                            return result;
                        } catch (IOException e3) {
                            return result;
                        }
                    } catch (Throwable th2) {
                        th = th2;
                        try {
                            if (this.mSocketDhcp != null) {
                                IoBridge.closeAndSignalBlockedThreads(this.mSocketDhcp);
                            }
                            this.mSocketDhcp = null;
                        } catch (IOException e4) {
                        }
                        throw th;
                    }
                }
            }
            long timeout = ((long) timeoutMillis) + SystemClock.elapsedRealtime();
            ByteBuffer sumBuf = ByteBuffer.allocate(1500);
            ByteBuffer etherBuf = ByteBuffer.allocate(14);
            ByteBuffer ipBuf = ByteBuffer.allocate(20);
            ByteBuffer udpBuf = ByteBuffer.allocate(8);
            ByteBuffer dhcpBuf = ByteBuffer.allocate(245);
            sumBuf.clear();
            sumBuf.order(ByteOrder.BIG_ENDIAN);
            etherBuf.clear();
            etherBuf.order(ByteOrder.BIG_ENDIAN);
            ipBuf.clear();
            ipBuf.order(ByteOrder.BIG_ENDIAN);
            udpBuf.clear();
            udpBuf.order(ByteOrder.BIG_ENDIAN);
            dhcpBuf.clear();
            dhcpBuf.order(ByteOrder.BIG_ENDIAN);
            makeEthernet(etherBuf, this.L2_BROADCAST, this.SRC_ADDR, ETHER_IP_TYPE);
            int result2 = 0;
            byte[] opString4 = opString;
            try {
                makeIP(ipBuf, 272, 17, UDP_IP_SRC_ADDR, UDP_IP_DST_ADDR);
                makeUDP(udpBuf, 68, 67, 252, 0);
                makeDHCP(dhcpBuf, this.SRC_ADDR);
                ByteBuffer sumBuf2 = sumBuf;
                ByteBuffer etherBuf2 = etherBuf;
                sumBuf2.put(etherBuf2).put(ipBuf).put(udpBuf).put(dhcpBuf);
                sumBuf2.flip();
                byte[] bytes2 = sumBuf2.array();
                Log.d(TAG, "DHCP : " + MobileWipsFrameworkUtil.byteArrayToHexString(bytes2));
                Os.sendto(this.mSocketDhcp, sumBuf2.array(), 0, sumBuf2.limit(), 0, new PacketSocketAddress(iParams.index, this.L2_BROADCAST));
                byte[] recvBuf = new byte[1500];
                byte[] result_router = new byte[4];
                InetSocketAddress inetSockAddr = new InetSocketAddress();
                while (SystemClock.elapsedRealtime() < timeout) {
                    Os.setsockoptTimeval(this.mSocketDhcp, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(timeout - SystemClock.elapsedRealtime()));
                    int readLen = Os.recvfrom(this.mSocketDhcp, recvBuf, 0, recvBuf.length, 0, inetSockAddr);
                    if (recvBuf[46] == TRANSACTION_ID[0] && recvBuf[47] == TRANSACTION_ID[1] && recvBuf[48] == TRANSACTION_ID[2] && recvBuf[49] == TRANSACTION_ID[3] && recvBuf[42] == 2) {
                        Log.d(TAG, "DHCP Recv (length: " + String.valueOf(readLen) + ") : " + MobileWipsFrameworkUtil.byteArrayToHexString(recvBuf) + " ");
                        int idx = DHCP_OPTION_START;
                        while (true) {
                            if (idx < recvBuf.length) {
                                if ((recvBuf[idx] & 255) != 255) {
                                    if (recvBuf[idx] != 0) {
                                        if (recvBuf[idx] == 3 && equalOption == -1) {
                                            System.arraycopy(recvBuf, idx + 2, result_router, 0, 4);
                                            result2 = MobileWipsFrameworkUtil.ipToInt(result_router);
                                            bytes = bytes2;
                                            opString2 = opString4;
                                            break;
                                        } else if (recvBuf[idx] != equalOption || equalString == null) {
                                            bytes = bytes2;
                                            opString2 = opString4;
                                            int idx2 = idx + 1;
                                            if (idx2 >= recvBuf.length) {
                                                break;
                                            }
                                            idx = idx2 + recvBuf[idx2] + 1;
                                            opString4 = opString2;
                                            readLen = readLen;
                                            bytes2 = bytes;
                                        } else {
                                            int i = 0;
                                            while (true) {
                                                opString2 = opString4;
                                                if (i >= opString2.length) {
                                                    bytes = bytes2;
                                                    break;
                                                }
                                                bytes = bytes2;
                                                if (recvBuf[idx + 2 + i] != opString2[i]) {
                                                    break;
                                                }
                                                i++;
                                                opString4 = opString2;
                                                bytes2 = bytes;
                                            }
                                            result2 = 1;
                                        }
                                    } else {
                                        bytes = bytes2;
                                        opString2 = opString4;
                                        break;
                                    }
                                } else {
                                    bytes = bytes2;
                                    opString2 = opString4;
                                    break;
                                }
                            } else {
                                bytes = bytes2;
                                opString2 = opString4;
                                break;
                            }
                        }
                    } else {
                        bytes = bytes2;
                        opString2 = opString4;
                    }
                    opString4 = opString2;
                    iParams = iParams;
                    sumBuf2 = sumBuf2;
                    etherBuf2 = etherBuf2;
                    bytes2 = bytes;
                }
                if (equalOption == -1) {
                    Log.d(TAG, "Router IP in DHCP Offer : " + MobileWipsFrameworkUtil.ipToString(result_router));
                }
                try {
                    if (this.mSocketDhcp != null) {
                        IoBridge.closeAndSignalBlockedThreads(this.mSocketDhcp);
                    }
                    this.mSocketDhcp = null;
                } catch (IOException e5) {
                }
                return result2;
            } catch (RuntimeException e6) {
                e = e6;
                result = 0;
                Log.e(TAG, "RuntimeException " + e);
                if (this.mSocketDhcp != null) {
                }
                this.mSocketDhcp = null;
                return result;
            } catch (Exception e7) {
                iParams = e7;
                result = 0;
                Throwable cause2 = iParams.getCause();
                Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + iParams);
                if (this.mSocketDhcp != null) {
                }
                this.mSocketDhcp = null;
                return result;
            } catch (Throwable th3) {
                th = th3;
                if (this.mSocketDhcp != null) {
                }
                this.mSocketDhcp = null;
                throw th;
            }
        } catch (RuntimeException e8) {
            e = e8;
            Log.e(TAG, "RuntimeException " + e);
            if (this.mSocketDhcp != null) {
            }
            this.mSocketDhcp = null;
            return result;
        } catch (Exception e9) {
            iParams = e9;
            Throwable cause22 = iParams.getCause();
            Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + iParams);
            if (this.mSocketDhcp != null) {
            }
            this.mSocketDhcp = null;
            return result;
        } catch (Throwable th4) {
            th = th4;
            if (this.mSocketDhcp != null) {
            }
            this.mSocketDhcp = null;
            throw th;
        }
    }

    private ByteBuffer createPacketDns(int dnsSourcePort, byte[] srcIp, byte[] dstIp, byte[] dnsMessage, byte[] dstMac, boolean isUDP) {
        ByteBuffer sumBuf = ByteBuffer.allocate(1500);
        ByteBuffer etherBuf = ByteBuffer.allocate(14);
        ByteBuffer ipBuf = ByteBuffer.allocate(20);
        sumBuf.clear();
        sumBuf.order(ByteOrder.BIG_ENDIAN);
        etherBuf.clear();
        etherBuf.order(ByteOrder.BIG_ENDIAN);
        ipBuf.clear();
        ipBuf.order(ByteOrder.BIG_ENDIAN);
        makeEthernet(etherBuf, dstMac, getMacAddress("wlan0"), ETHER_IP_TYPE);
        if (isUDP) {
            ByteBuffer udpBuf = ByteBuffer.allocate(8);
            ByteBuffer dnsBuf = ByteBuffer.allocate(dnsMessage.length);
            udpBuf.clear();
            udpBuf.order(ByteOrder.BIG_ENDIAN);
            dnsBuf.clear();
            dnsBuf.order(ByteOrder.BIG_ENDIAN);
            makeIP(ipBuf, dnsMessage.length + 28, 17, srcIp, dstIp);
            makeUDP(udpBuf, dnsSourcePort, 53, 8 + dnsMessage.length, 0);
            sumBuf.put(etherBuf).put(ipBuf).put(udpBuf).put(ByteBuffer.wrap(dnsMessage));
            sumBuf.flip();
            return sumBuf;
        }
        ByteBuffer tcpBuf = ByteBuffer.allocate(20);
        ByteBuffer pseudoBuf = ByteBuffer.allocate(32);
        tcpBuf.clear();
        tcpBuf.order(ByteOrder.BIG_ENDIAN);
        pseudoBuf.clear();
        pseudoBuf.order(ByteOrder.BIG_ENDIAN);
        makeIP(ipBuf, 40, 6, srcIp, dstIp);
        makePsuedoHeader(pseudoBuf, srcIp, dstIp, 6, 20);
        makeTCP(tcpBuf, pseudoBuf, 53);
        sumBuf.put(etherBuf).put(ipBuf).put(tcpBuf);
        sumBuf.flip();
        return sumBuf;
    }

    public byte[] sendDns(LinkProperties linkProperties, byte[] srcAddr, byte[] dstAddr, String dstMac, byte[] dnsMessage, long[] timeoutInMs, boolean isUDP) throws IOException {
        if (isUDP) {
            return sendDnsToUDP(linkProperties, timeoutInMs, srcAddr, dstAddr, dnsMessage, dstMac);
        }
        return sendDNSToTCP(linkProperties, timeoutInMs, srcAddr, dstAddr, dnsMessage, dstMac);
    }

    /* JADX INFO: Multiple debug info for r2v19 'recvBuf'  byte[]: [D('sumBuf' java.nio.ByteBuffer), D('recvBuf' byte[])] */
    /* JADX WARNING: Code restructure failed: missing block: B:120:0x0348, code lost:
        libcore.io.IoBridge.closeAndSignalBlockedThreads(r14);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:68:0x01e3, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:69:0x01e4, code lost:
        r21 = r2;
        r22 = r3;
        r23 = r4;
        r17 = r5;
        r2 = r7;
        r6 = r29;
        r1 = r18;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:79:0x0269, code lost:
        r0 = move-exception;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:80:0x026a, code lost:
        r1 = r0;
     */
    /* JADX WARNING: Failed to process nested try/catch */
    /* JADX WARNING: Removed duplicated region for block: B:109:0x0316 A[Catch:{ IOException -> 0x031f }] */
    /* JADX WARNING: Removed duplicated region for block: B:120:0x0348 A[Catch:{ IOException -> 0x0351 }] */
    /* JADX WARNING: Removed duplicated region for block: B:79:0x0269 A[ExcHandler: all (r0v27 'th' java.lang.Throwable A[CUSTOM_DECLARE]), Splitter:B:12:0x00ae] */
    public byte[] sendDnsToUDP(LinkProperties linkProperties, long[] timeoutMillis, byte[] srcAddr, byte[] dstAddr, byte[] dnsMessage, String strDstMac) throws IOException {
        Throwable th;
        int readLen;
        byte[] recvBuf;
        byte[] dstMac;
        long[] jArr = timeoutMillis;
        FileDescriptor socketDns = null;
        int oldTag = TrafficStats.getAndSetThreadStatsTag(-190);
        try {
            DatagramSocket datagramSocket = new DatagramSocket();
            int dnsDestinationPort = datagramSocket.getLocalPort();
            byte[] dstMac2 = macStringToByteArray(strDstMac);
            int readLen2 = 0;
            ByteBuffer sumBuf = createPacketDns(dnsDestinationPort, srcAddr, dstAddr, dnsMessage, dstMac2, true);
            byte[] recvBuf2 = new byte[1500];
            int i = 0;
            try {
                socketDns = Os.socket(OsConstants.AF_PACKET, OsConstants.SOCK_RAW, 0);
                InterfaceParams iParams = InterfaceParams.getByName(linkProperties.getInterfaceName());
                Os.bind(socketDns, new PacketSocketAddress((short) OsConstants.ETH_P_IP, iParams.index));
                Log.d(TAG, "DNS : " + MobileWipsFrameworkUtil.byteArrayToHexString(sumBuf.array()));
                byte[] dstMac3 = dstMac2;
                try {
                    PacketSocketAddress interfaceUnicast = new PacketSocketAddress(iParams.index, dstMac3);
                    int length = jArr.length;
                    while (i < length) {
                        long timeout = SystemClock.elapsedRealtime() + jArr[i];
                        Os.sendto(socketDns, sumBuf.array(), 0, sumBuf.limit(), 0, interfaceUnicast);
                        InetSocketAddress inetSockAddr = new InetSocketAddress();
                        byte[] dstMac4 = dstMac3;
                        while (SystemClock.elapsedRealtime() < timeout) {
                            try {
                                Os.setsockoptTimeval(socketDns, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(timeout - SystemClock.elapsedRealtime()));
                                int i2 = length;
                                InterfaceParams iParams2 = iParams;
                                int i3 = i;
                                ByteBuffer sumBuf2 = sumBuf;
                                recvBuf = recvBuf2;
                                try {
                                    readLen = Os.recvfrom(socketDns, recvBuf2, 0, recvBuf2.length, 0, inetSockAddr);
                                    if (readLen > 44) {
                                        try {
                                            if (MobileWipsFrameworkUtil.byteArrayToInt(new byte[]{recvBuf[34], recvBuf[35]}) == 53) {
                                                if (MobileWipsFrameworkUtil.byteArrayToInt(new byte[]{recvBuf[36], recvBuf[37]}) == dnsDestinationPort) {
                                                    int index = 0;
                                                    boolean isMacEqual = true;
                                                    while (true) {
                                                        dstMac = dstMac4;
                                                        try {
                                                            if (index >= dstMac.length) {
                                                                break;
                                                            }
                                                            if (recvBuf[index + 6] != dstMac[index]) {
                                                                isMacEqual = false;
                                                            }
                                                            index++;
                                                            dstMac4 = dstMac;
                                                        } catch (Exception e) {
                                                            e = e;
                                                            try {
                                                                Log.d(TAG, "X-Exception -> DNS Recv (length: " + readLen + ") EMPTY  size recvBuf" + recvBuf.length);
                                                                Throwable cause = e.getCause();
                                                                Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                                                                readLen2 = readLen;
                                                                recvBuf2 = recvBuf;
                                                                dstMac4 = dstMac;
                                                                i = i3;
                                                                sumBuf = sumBuf2;
                                                                length = i2;
                                                                iParams = iParams2;
                                                            } catch (Exception e2) {
                                                                e = e2;
                                                                try {
                                                                    Log.d(TAG, "Exception -> DNS Recv (length: " + readLen + ") EMPTY  size recvBuf" + recvBuf.length);
                                                                    Throwable cause2 = e.getCause();
                                                                    e.printStackTrace();
                                                                    if ((cause2 instanceof ErrnoException) && ((ErrnoException) cause2).errno != OsConstants.EAGAIN) {
                                                                        Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                                                                    }
                                                                    try {
                                                                        TrafficStats.setThreadStatsTag(oldTag);
                                                                        if (socketDns != null) {
                                                                            IoBridge.closeAndSignalBlockedThreads(socketDns);
                                                                        }
                                                                        datagramSocket.close();
                                                                    } catch (IOException ex) {
                                                                        Log.d(TAG, "Closing Exception");
                                                                        ex.printStackTrace();
                                                                    }
                                                                    Log.d(TAG, "No Response > DNS Recv (length: " + readLen);
                                                                    return new byte[0];
                                                                } catch (Throwable th2) {
                                                                    th = th2;
                                                                    try {
                                                                        TrafficStats.setThreadStatsTag(oldTag);
                                                                        if (socketDns != null) {
                                                                        }
                                                                        datagramSocket.close();
                                                                    } catch (IOException ex2) {
                                                                        Log.d(TAG, "Closing Exception");
                                                                        ex2.printStackTrace();
                                                                    }
                                                                    throw th;
                                                                }
                                                            }
                                                        }
                                                    }
                                                    int outputID = MobileWipsFrameworkUtil.byteArrayToInt(new byte[]{recvBuf[42], recvBuf[43]});
                                                    if (isMacEqual) {
                                                        if (MobileWipsFrameworkUtil.byteArrayToInt(new byte[]{dnsMessage[0], dnsMessage[1]}) == outputID) {
                                                            byte[] dnsResponse = new byte[(readLen - 42)];
                                                            for (int i4 = 0; i4 < dnsResponse.length; i4++) {
                                                                dnsResponse[i4] = recvBuf[i4 + 42];
                                                            }
                                                            Log.d(TAG, "DNS Recv (length: " + readLen + ") ID: " + outputID);
                                                            Log.d(TAG, "DNS ll Final (length: " + readLen + ") : " + MobileWipsFrameworkUtil.byteArrayToHexString(dnsResponse) + " ");
                                                            try {
                                                                TrafficStats.setThreadStatsTag(oldTag);
                                                                if (socketDns != null) {
                                                                    IoBridge.closeAndSignalBlockedThreads(socketDns);
                                                                }
                                                                datagramSocket.close();
                                                            } catch (IOException ex3) {
                                                                Log.d(TAG, "Closing Exception");
                                                                ex3.printStackTrace();
                                                            }
                                                            return dnsResponse;
                                                        }
                                                    } else {
                                                        continue;
                                                    }
                                                } else {
                                                    dstMac = dstMac4;
                                                }
                                            } else {
                                                dstMac = dstMac4;
                                            }
                                        } catch (Exception e3) {
                                            e = e3;
                                            dstMac = dstMac4;
                                            Log.d(TAG, "X-Exception -> DNS Recv (length: " + readLen + ") EMPTY  size recvBuf" + recvBuf.length);
                                            Throwable cause3 = e.getCause();
                                            Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                                            readLen2 = readLen;
                                            recvBuf2 = recvBuf;
                                            dstMac4 = dstMac;
                                            i = i3;
                                            sumBuf = sumBuf2;
                                            length = i2;
                                            iParams = iParams2;
                                        } catch (Throwable th3) {
                                            th = th3;
                                            TrafficStats.setThreadStatsTag(oldTag);
                                            if (socketDns != null) {
                                            }
                                            datagramSocket.close();
                                            throw th;
                                        }
                                    } else {
                                        dstMac = dstMac4;
                                    }
                                } catch (Exception e4) {
                                    e = e4;
                                    dstMac = dstMac4;
                                    readLen = readLen2;
                                    Log.d(TAG, "X-Exception -> DNS Recv (length: " + readLen + ") EMPTY  size recvBuf" + recvBuf.length);
                                    Throwable cause32 = e.getCause();
                                    Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                                    readLen2 = readLen;
                                    recvBuf2 = recvBuf;
                                    dstMac4 = dstMac;
                                    i = i3;
                                    sumBuf = sumBuf2;
                                    length = i2;
                                    iParams = iParams2;
                                } catch (Throwable th4) {
                                    th = th4;
                                    TrafficStats.setThreadStatsTag(oldTag);
                                    if (socketDns != null) {
                                    }
                                    datagramSocket.close();
                                    throw th;
                                }
                                readLen2 = readLen;
                                recvBuf2 = recvBuf;
                                dstMac4 = dstMac;
                                i = i3;
                                sumBuf = sumBuf2;
                                length = i2;
                                iParams = iParams2;
                            } catch (Exception e5) {
                                e = e5;
                                recvBuf = recvBuf2;
                                readLen = readLen2;
                                Log.d(TAG, "Exception -> DNS Recv (length: " + readLen + ") EMPTY  size recvBuf" + recvBuf.length);
                                Throwable cause22 = e.getCause();
                                e.printStackTrace();
                                Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                                TrafficStats.setThreadStatsTag(oldTag);
                                if (socketDns != null) {
                                }
                                datagramSocket.close();
                                Log.d(TAG, "No Response > DNS Recv (length: " + readLen);
                                return new byte[0];
                            } catch (Throwable th5) {
                            }
                        }
                        i++;
                        jArr = timeoutMillis;
                        dstMac3 = dstMac4;
                        sumBuf = sumBuf;
                    }
                    try {
                        TrafficStats.setThreadStatsTag(oldTag);
                        if (socketDns != null) {
                            IoBridge.closeAndSignalBlockedThreads(socketDns);
                        }
                        datagramSocket.close();
                        readLen = readLen2;
                    } catch (IOException ex4) {
                        Log.d(TAG, "Closing Exception");
                        ex4.printStackTrace();
                        readLen = readLen2;
                    }
                } catch (Exception e6) {
                    e = e6;
                    recvBuf = recvBuf2;
                    readLen = 0;
                    Log.d(TAG, "Exception -> DNS Recv (length: " + readLen + ") EMPTY  size recvBuf" + recvBuf.length);
                    Throwable cause222 = e.getCause();
                    e.printStackTrace();
                    Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                    TrafficStats.setThreadStatsTag(oldTag);
                    if (socketDns != null) {
                    }
                    datagramSocket.close();
                    Log.d(TAG, "No Response > DNS Recv (length: " + readLen);
                    return new byte[0];
                } catch (Throwable th6) {
                    th = th6;
                    TrafficStats.setThreadStatsTag(oldTag);
                    if (socketDns != null) {
                    }
                    datagramSocket.close();
                    throw th;
                }
            } catch (Exception e7) {
                e = e7;
                recvBuf = recvBuf2;
                readLen = 0;
                Log.d(TAG, "Exception -> DNS Recv (length: " + readLen + ") EMPTY  size recvBuf" + recvBuf.length);
                Throwable cause2222 = e.getCause();
                e.printStackTrace();
                Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                TrafficStats.setThreadStatsTag(oldTag);
                if (socketDns != null) {
                }
                datagramSocket.close();
                Log.d(TAG, "No Response > DNS Recv (length: " + readLen);
                return new byte[0];
            } catch (Throwable th7) {
                th = th7;
                TrafficStats.setThreadStatsTag(oldTag);
                if (socketDns != null) {
                }
                datagramSocket.close();
                throw th;
            }
            Log.d(TAG, "No Response > DNS Recv (length: " + readLen);
            return new byte[0];
        } catch (SocketException exception) {
            throw new IOException("SocketException of DatagramSocket. Message: " + exception.getMessage());
        }
    }

    /* JADX INFO: Multiple debug info for r13v16 'bytes'  byte[]: [D('recvDstPort' byte[]), D('bytes' byte[])] */
    /* JADX WARNING: Code restructure failed: missing block: B:44:0x0119, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:59:0x0185, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:60:0x0186, code lost:
        r18 = r1;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:61:0x0189, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:62:0x018a, code lost:
        r23 = r13;
        r13 = r18;
        r18 = r1;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:70:0x01bf, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:71:0x01c0, code lost:
        r23 = r13;
        r10 = r17;
        r13 = r18;
        r18 = r19;
        r17 = r9;
        r19 = r12;
        r11 = r16;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:79:0x0235, code lost:
        r0 = move-exception;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:80:0x0236, code lost:
        r1 = r0;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:84:0x0252, code lost:
        r0 = move-exception;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:85:0x0253, code lost:
        r1 = r0;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:86:0x025a, code lost:
        r0 = e;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:87:0x025b, code lost:
        r8 = r16;
     */
    /* JADX WARNING: Failed to process nested try/catch */
    /* JADX WARNING: Removed duplicated region for block: B:109:0x02bf A[SYNTHETIC, Splitter:B:109:0x02bf] */
    /* JADX WARNING: Removed duplicated region for block: B:119:0x02eb A[SYNTHETIC, Splitter:B:119:0x02eb] */
    /* JADX WARNING: Removed duplicated region for block: B:79:0x0235 A[ExcHandler: all (r0v31 'th' java.lang.Throwable A[CUSTOM_DECLARE]), Splitter:B:26:0x00e4] */
    /* JADX WARNING: Removed duplicated region for block: B:84:0x0252 A[ExcHandler: all (r0v25 'th' java.lang.Throwable A[CUSTOM_DECLARE]), PHI: r9 r16 
      PHI: (r9v8 'sumBuf' java.nio.ByteBuffer) = (r9v7 'sumBuf' java.nio.ByteBuffer), (r9v9 'sumBuf' java.nio.ByteBuffer), (r9v9 'sumBuf' java.nio.ByteBuffer), (r9v9 'sumBuf' java.nio.ByteBuffer), (r9v9 'sumBuf' java.nio.ByteBuffer) binds: [B:8:0x007a, B:14:0x00b6, B:15:?, B:17:0x00c5, B:18:?] A[DONT_GENERATE, DONT_INLINE]
      PHI: (r16v5 'readLen' int) = (r16v4 'readLen' int), (r16v6 'readLen' int), (r16v6 'readLen' int), (r16v6 'readLen' int), (r16v6 'readLen' int) binds: [B:8:0x007a, B:14:0x00b6, B:15:?, B:17:0x00c5, B:18:?] A[DONT_GENERATE, DONT_INLINE], Splitter:B:8:0x007a] */
    public byte[] sendDNSToTCP(LinkProperties linkProperties, long[] timeoutMillis, byte[] srcAddr, byte[] dstAddr, byte[] dnsMessage, String strDstMac) {
        Throwable th;
        byte[] recvBuf;
        byte[] recvSrcPort;
        byte[] recvSrcPort2;
        byte[] bytes;
        InterfaceParams iParams;
        byte[] recvSrcPort3;
        ByteBuffer sumBuf;
        byte[] bytes2;
        int readLen;
        byte[] recvBuf2;
        long[] jArr = timeoutMillis;
        FileDescriptor socketTcp = null;
        int readLen2 = 0;
        byte[] dstMac = macStringToByteArray(strDstMac);
        ByteBuffer sumBuf2 = createPacketDns(0, srcAddr, dstAddr, new byte[3], dstMac, false);
        int i = 2;
        try {
            socketTcp = Os.socket(OsConstants.AF_PACKET, OsConstants.SOCK_RAW, 0);
            InterfaceParams iParams2 = InterfaceParams.getByName(linkProperties.getInterfaceName());
            Os.bind(socketTcp, new PacketSocketAddress((short) OsConstants.ETH_P_IP, iParams2.index));
            byte[] recvDstPort = sumBuf2.array();
            Log.d(TAG, "TCP : " + MobileWipsFrameworkUtil.byteArrayToHexString(recvDstPort));
            try {
                PacketSocketAddress interfaceDestination = new PacketSocketAddress(iParams2.index, dstMac);
                Log.d(TAG, "going to send now");
                int length = jArr.length;
                int readLen3 = 0;
                int readLen4 = 0;
                while (readLen4 < length) {
                    try {
                        long timeout = SystemClock.elapsedRealtime() + jArr[readLen4];
                        Os.sendto(socketTcp, sumBuf2.array(), 0, sumBuf2.limit(), 0, interfaceDestination);
                        recvBuf = new byte[1500];
                        recvSrcPort = new byte[i];
                        recvSrcPort2 = new byte[i];
                        InetSocketAddress inetSockAddr = new InetSocketAddress();
                        while (SystemClock.elapsedRealtime() < timeout) {
                            Os.setsockoptTimeval(socketTcp, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(timeout - SystemClock.elapsedRealtime()));
                            recvBuf2 = recvBuf;
                            readLen = Os.recvfrom(socketTcp, recvBuf2, 0, recvBuf2.length, 0, inetSockAddr);
                            if (readLen > 0) {
                                try {
                                    if (recvBuf2.length >= 54) {
                                        sumBuf = sumBuf2;
                                        byte[] recvSrcPort4 = recvSrcPort2;
                                        iParams = iParams2;
                                        try {
                                            System.arraycopy(recvBuf2, 34, recvSrcPort4, 0, 2);
                                            bytes = recvDstPort;
                                            bytes2 = recvSrcPort;
                                            System.arraycopy(recvBuf2, 36, bytes2, 0, 2);
                                            if (MobileWipsFrameworkUtil.byteArrayToInt(recvSrcPort4) != 53) {
                                                recvSrcPort3 = recvSrcPort4;
                                            } else if (MobileWipsFrameworkUtil.byteArrayToInt(bytes2) == TCP_SPORT) {
                                                int index = 0;
                                                boolean isMacEqual = true;
                                                while (index < dstMac.length) {
                                                    recvSrcPort3 = recvSrcPort4;
                                                    if (recvBuf2[index + 6] != dstMac[index]) {
                                                        isMacEqual = false;
                                                    }
                                                    index++;
                                                    recvSrcPort4 = recvSrcPort3;
                                                }
                                                recvSrcPort3 = recvSrcPort4;
                                                if (isMacEqual) {
                                                    Log.d(TAG, "TCP Recv (length: " + readLen + ") : " + MobileWipsFrameworkUtil.byteArrayToHexString(recvBuf2) + " ");
                                                    if (recvBuf2[47] == 18) {
                                                        Log.d(TAG, "TCP SYN/ACK(" + strDstMac + ") : " + true);
                                                        byte[] tcpRet = new byte[12];
                                                        tcpRet[0] = dnsMessage[0];
                                                        tcpRet[1] = dnsMessage[1];
                                                        if (socketTcp != null) {
                                                            try {
                                                                IoBridge.closeAndSignalBlockedThreads(socketTcp);
                                                            } catch (IOException e) {
                                                            }
                                                        }
                                                        return tcpRet;
                                                    }
                                                } else {
                                                    continue;
                                                }
                                            } else {
                                                recvSrcPort3 = recvSrcPort4;
                                            }
                                            readLen3 = readLen;
                                            sumBuf2 = sumBuf;
                                            iParams2 = iParams;
                                            recvBuf = recvBuf2;
                                            recvSrcPort2 = recvSrcPort3;
                                            recvSrcPort = bytes2;
                                            recvDstPort = bytes;
                                        } catch (Exception e2) {
                                            e = e2;
                                            readLen2 = readLen;
                                            try {
                                                Throwable cause = e.getCause();
                                                Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                                                if (socketTcp != null) {
                                                }
                                                Log.d(TAG, "TCP SYN/ACK(" + strDstMac + ") : " + false);
                                                return new byte[0];
                                            } catch (Throwable th2) {
                                                th = th2;
                                                if (socketTcp != null) {
                                                    try {
                                                        IoBridge.closeAndSignalBlockedThreads(socketTcp);
                                                    } catch (IOException e3) {
                                                    }
                                                }
                                                throw th;
                                            }
                                        } catch (Throwable th3) {
                                        }
                                    }
                                } catch (Exception e4) {
                                    e = e4;
                                    sumBuf = sumBuf2;
                                    bytes = recvDstPort;
                                    bytes2 = recvSrcPort;
                                    recvSrcPort3 = recvSrcPort2;
                                    iParams = iParams2;
                                    Log.d(TAG, "X-Exception -> DNS Recv (length: " + readLen + ") EMPTY  size recvBuf" + recvBuf2.length);
                                    Throwable cause2 = e.getCause();
                                    Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                                    readLen3 = readLen;
                                    sumBuf2 = sumBuf;
                                    iParams2 = iParams;
                                    recvBuf = recvBuf2;
                                    recvSrcPort2 = recvSrcPort3;
                                    recvSrcPort = bytes2;
                                    recvDstPort = bytes;
                                } catch (Throwable th4) {
                                    th = th4;
                                    if (socketTcp != null) {
                                    }
                                    throw th;
                                }
                            }
                            sumBuf = sumBuf2;
                            bytes = recvDstPort;
                            bytes2 = recvSrcPort;
                            recvSrcPort3 = recvSrcPort2;
                            iParams = iParams2;
                            readLen3 = readLen;
                            sumBuf2 = sumBuf;
                            iParams2 = iParams;
                            recvBuf = recvBuf2;
                            recvSrcPort2 = recvSrcPort3;
                            recvSrcPort = bytes2;
                            recvDstPort = bytes;
                        }
                        readLen4++;
                        jArr = timeoutMillis;
                        recvDstPort = recvDstPort;
                        i = 2;
                    } catch (Exception e5) {
                        e = e5;
                        sumBuf = sumBuf2;
                        bytes = recvDstPort;
                        bytes2 = recvSrcPort;
                        recvSrcPort3 = recvSrcPort2;
                        iParams = iParams2;
                        readLen = readLen3;
                        Log.d(TAG, "X-Exception -> DNS Recv (length: " + readLen + ") EMPTY  size recvBuf" + recvBuf2.length);
                        Throwable cause22 = e.getCause();
                        Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                        readLen3 = readLen;
                        sumBuf2 = sumBuf;
                        iParams2 = iParams;
                        recvBuf = recvBuf2;
                        recvSrcPort2 = recvSrcPort3;
                        recvSrcPort = bytes2;
                        recvDstPort = bytes;
                    } catch (Throwable th5) {
                    }
                }
                if (socketTcp != null) {
                    try {
                        IoBridge.closeAndSignalBlockedThreads(socketTcp);
                    } catch (IOException e6) {
                    }
                }
            } catch (Exception e7) {
                e = e7;
                Throwable cause3 = e.getCause();
                if ((cause3 instanceof ErrnoException) && ((ErrnoException) cause3).errno != OsConstants.EAGAIN) {
                    Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                }
                if (socketTcp != null) {
                    try {
                        IoBridge.closeAndSignalBlockedThreads(socketTcp);
                    } catch (IOException e8) {
                    }
                }
                Log.d(TAG, "TCP SYN/ACK(" + strDstMac + ") : " + false);
                return new byte[0];
            } catch (Throwable th6) {
                th = th6;
                if (socketTcp != null) {
                }
                throw th;
            }
        } catch (Exception e9) {
            e = e9;
            Throwable cause32 = e.getCause();
            Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
            if (socketTcp != null) {
            }
            Log.d(TAG, "TCP SYN/ACK(" + strDstMac + ") : " + false);
            return new byte[0];
        } catch (Throwable th7) {
            th = th7;
            if (socketTcp != null) {
            }
            throw th;
        }
        Log.d(TAG, "TCP SYN/ACK(" + strDstMac + ") : " + false);
        return new byte[0];
    }

    /* JADX INFO: Multiple debug info for r3v29 'socketTcp'  java.io.FileDescriptor: [D('pseudoBuf' java.nio.ByteBuffer), D('socketTcp' java.io.FileDescriptor)] */
    /* JADX INFO: Multiple debug info for r3v33 'socketTcp'  java.io.FileDescriptor: [D('socketTcp' java.io.FileDescriptor), D('pseudoBuf' java.nio.ByteBuffer)] */
    /* JADX WARNING: Removed duplicated region for block: B:113:0x02e8 A[SYNTHETIC, Splitter:B:113:0x02e8] */
    /* JADX WARNING: Removed duplicated region for block: B:124:0x030d  */
    /* JADX WARNING: Removed duplicated region for block: B:126:0x0314 A[SYNTHETIC, Splitter:B:126:0x0314] */
    public boolean sendTcp(LinkProperties linkProperties, int timeoutMillis, byte[] gateway, byte[] myAddr, String dstMac) {
        FileDescriptor socketTcp;
        Throwable th;
        FileDescriptor fileDescriptor;
        FileDescriptor fileDescriptor2;
        InterfaceParams iParams;
        byte[] DstMac;
        long timeout;
        ByteBuffer sumBuf;
        ByteBuffer etherBuf;
        ByteBuffer ipBuf;
        ByteBuffer tcpBuf;
        ByteBuffer pseudoBuf;
        boolean result;
        FileDescriptor socketTcp2 = null;
        boolean result2 = false;
        try {
            FileDescriptor socketTcp3 = Os.socket(OsConstants.AF_PACKET, OsConstants.SOCK_RAW, 0);
            try {
                iParams = InterfaceParams.getByName(linkProperties.getInterfaceName());
                Os.bind(socketTcp3, new PacketSocketAddress((short) OsConstants.ETH_P_IP, iParams.index));
                this.SRC_ADDR = getMacAddress("wlan0");
                DstMac = macStringToByteArray(dstMac);
                timeout = SystemClock.elapsedRealtime() + ((long) timeoutMillis);
                sumBuf = ByteBuffer.allocate(1500);
                etherBuf = ByteBuffer.allocate(14);
                ipBuf = ByteBuffer.allocate(20);
                tcpBuf = ByteBuffer.allocate(20);
                pseudoBuf = ByteBuffer.allocate(32);
                sumBuf.clear();
                sumBuf.order(ByteOrder.BIG_ENDIAN);
                etherBuf.clear();
                etherBuf.order(ByteOrder.BIG_ENDIAN);
                ipBuf.clear();
                ipBuf.order(ByteOrder.BIG_ENDIAN);
                tcpBuf.clear();
                tcpBuf.order(ByteOrder.BIG_ENDIAN);
                pseudoBuf.clear();
                pseudoBuf.order(ByteOrder.BIG_ENDIAN);
                makeEthernet(etherBuf, DstMac, this.SRC_ADDR, ETHER_IP_TYPE);
            } catch (RuntimeException e) {
                e = e;
                fileDescriptor = socketTcp3;
                socketTcp2 = fileDescriptor;
                Log.e(TAG, "RuntimeException " + e);
                if (socketTcp2 != null) {
                }
                return result2;
            } catch (Exception e2) {
                e = e2;
                fileDescriptor2 = socketTcp3;
                socketTcp2 = fileDescriptor2;
                try {
                    Throwable cause = e.getCause();
                    Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                    if (socketTcp2 != null) {
                    }
                    return result2;
                } catch (Throwable th2) {
                    socketTcp = socketTcp2;
                    th = th2;
                    if (socketTcp != null) {
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                socketTcp = socketTcp3;
                th = th;
                if (socketTcp != null) {
                }
                throw th;
            }
            try {
                makeIP(ipBuf, 40, 6, myAddr, gateway);
                ByteBuffer sumBuf2 = sumBuf;
                byte[] DstMac2 = DstMac;
                FileDescriptor socketTcp4 = socketTcp3;
                ByteBuffer pseudoBuf2 = pseudoBuf;
                try {
                    makePsuedoHeader(pseudoBuf, myAddr, gateway, 6, 20);
                    makeTCP(tcpBuf, pseudoBuf2, 80);
                    sumBuf2.put(etherBuf).put(ipBuf).put(tcpBuf);
                    sumBuf2.flip();
                    byte[] bytes = sumBuf2.array();
                    Log.d(TAG, "TCP : " + MobileWipsFrameworkUtil.byteArrayToHexString(bytes));
                    Os.sendto(socketTcp4, sumBuf2.array(), 0, sumBuf2.limit(), 0, new PacketSocketAddress(iParams.index, DstMac2));
                    byte[] recvBuf = new byte[1500];
                    byte[] recvDstPort = new byte[2];
                    byte[] recvSrcPort = new byte[2];
                    InetSocketAddress inetSockAddr = new InetSocketAddress();
                    result = false;
                    while (SystemClock.elapsedRealtime() < timeout) {
                        try {
                            try {
                                socketTcp = socketTcp4;
                            } catch (RuntimeException e3) {
                                e = e3;
                                socketTcp2 = socketTcp4;
                                result2 = result;
                                Log.e(TAG, "RuntimeException " + e);
                                if (socketTcp2 != null) {
                                }
                                return result2;
                            } catch (Exception e4) {
                                e = e4;
                                socketTcp2 = socketTcp4;
                                result2 = result;
                                Throwable cause2 = e.getCause();
                                Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                                if (socketTcp2 != null) {
                                }
                                return result2;
                            } catch (Throwable th4) {
                                socketTcp = socketTcp4;
                                th = th4;
                                if (socketTcp != null) {
                                }
                                throw th;
                            }
                        } catch (RuntimeException e5) {
                            e = e5;
                            result2 = result;
                            socketTcp2 = socketTcp4;
                            Log.e(TAG, "RuntimeException " + e);
                            if (socketTcp2 != null) {
                            }
                            return result2;
                        } catch (Exception e6) {
                            e = e6;
                            result2 = result;
                            socketTcp2 = socketTcp4;
                            Throwable cause22 = e.getCause();
                            Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                            if (socketTcp2 != null) {
                            }
                            return result2;
                        } catch (Throwable th5) {
                            socketTcp = socketTcp4;
                            th = th5;
                            if (socketTcp != null) {
                            }
                            throw th;
                        }
                        try {
                            Os.setsockoptTimeval(socketTcp, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(timeout - SystemClock.elapsedRealtime()));
                            int readLen = Os.recvfrom(socketTcp, recvBuf, 0, recvBuf.length, 0, inetSockAddr);
                            System.arraycopy(recvBuf, 34, recvSrcPort, 0, 2);
                            System.arraycopy(recvBuf, 36, recvDstPort, 0, 2);
                            if (MobileWipsFrameworkUtil.byteArrayToInt(recvSrcPort) == 80 && MobileWipsFrameworkUtil.byteArrayToInt(recvDstPort) == TCP_SPORT) {
                                Log.d(TAG, "TCP Recv (length: " + String.valueOf(readLen) + ") : " + MobileWipsFrameworkUtil.byteArrayToHexString(recvBuf) + " ");
                                if (recvBuf[47] == 18) {
                                    result = true;
                                }
                            }
                            socketTcp4 = socketTcp;
                            iParams = iParams;
                            DstMac2 = DstMac2;
                            sumBuf2 = sumBuf2;
                            pseudoBuf2 = pseudoBuf2;
                        } catch (RuntimeException e7) {
                            e = e7;
                            socketTcp2 = socketTcp;
                            result2 = result;
                            Log.e(TAG, "RuntimeException " + e);
                            if (socketTcp2 != null) {
                            }
                            return result2;
                        } catch (Exception e8) {
                            e = e8;
                            socketTcp2 = socketTcp;
                            result2 = result;
                            Throwable cause222 = e.getCause();
                            Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                            if (socketTcp2 != null) {
                            }
                            return result2;
                        } catch (Throwable th6) {
                            th = th6;
                            if (socketTcp != null) {
                            }
                            throw th;
                        }
                    }
                    socketTcp = socketTcp4;
                } catch (RuntimeException e9) {
                    e = e9;
                    socketTcp2 = socketTcp4;
                    Log.e(TAG, "RuntimeException " + e);
                    if (socketTcp2 != null) {
                    }
                    return result2;
                } catch (Exception e10) {
                    e = e10;
                    socketTcp2 = socketTcp4;
                    Throwable cause2222 = e.getCause();
                    Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                    if (socketTcp2 != null) {
                    }
                    return result2;
                } catch (Throwable th7) {
                    socketTcp = socketTcp4;
                    th = th7;
                    if (socketTcp != null) {
                    }
                    throw th;
                }
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("TCP SYN/ACK(");
                    try {
                        sb.append(dstMac);
                        sb.append(") : ");
                        try {
                            sb.append(result);
                            Log.d(TAG, sb.toString());
                            if (socketTcp != null) {
                                try {
                                    IoBridge.closeAndSignalBlockedThreads(socketTcp);
                                } catch (IOException e11) {
                                    return result;
                                }
                            }
                            return result;
                        } catch (RuntimeException e12) {
                            e = e12;
                            result2 = result;
                            socketTcp2 = socketTcp;
                            Log.e(TAG, "RuntimeException " + e);
                            if (socketTcp2 != null) {
                                IoBridge.closeAndSignalBlockedThreads(socketTcp2);
                            }
                            return result2;
                        } catch (Exception e13) {
                            e = e13;
                            result2 = result;
                            socketTcp2 = socketTcp;
                            Throwable cause22222 = e.getCause();
                            if ((cause22222 instanceof ErrnoException) && ((ErrnoException) cause22222).errno != OsConstants.EAGAIN) {
                                Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                            }
                            if (socketTcp2 != null) {
                                try {
                                    IoBridge.closeAndSignalBlockedThreads(socketTcp2);
                                } catch (IOException e14) {
                                    return result2;
                                }
                            }
                            return result2;
                        } catch (Throwable th8) {
                            th = th8;
                            if (socketTcp != null) {
                                try {
                                    IoBridge.closeAndSignalBlockedThreads(socketTcp);
                                } catch (IOException e15) {
                                }
                            }
                            throw th;
                        }
                    } catch (RuntimeException e16) {
                        e = e16;
                        result2 = result;
                        socketTcp2 = socketTcp;
                        Log.e(TAG, "RuntimeException " + e);
                        if (socketTcp2 != null) {
                        }
                        return result2;
                    } catch (Exception e17) {
                        e = e17;
                        result2 = result;
                        socketTcp2 = socketTcp;
                        Throwable cause222222 = e.getCause();
                        Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                        if (socketTcp2 != null) {
                        }
                        return result2;
                    } catch (Throwable th9) {
                        th = th9;
                        th = th;
                        if (socketTcp != null) {
                        }
                        throw th;
                    }
                } catch (RuntimeException e18) {
                    e = e18;
                    result2 = result;
                    socketTcp2 = socketTcp;
                    Log.e(TAG, "RuntimeException " + e);
                    if (socketTcp2 != null) {
                    }
                    return result2;
                } catch (Exception e19) {
                    e = e19;
                    result2 = result;
                    socketTcp2 = socketTcp;
                    Throwable cause2222222 = e.getCause();
                    Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                    if (socketTcp2 != null) {
                    }
                    return result2;
                } catch (Throwable th10) {
                    th = th10;
                    th = th;
                    if (socketTcp != null) {
                    }
                    throw th;
                }
            } catch (RuntimeException e20) {
                e = e20;
                fileDescriptor = socketTcp3;
                socketTcp2 = fileDescriptor;
                Log.e(TAG, "RuntimeException " + e);
                if (socketTcp2 != null) {
                }
                return result2;
            } catch (Exception e21) {
                e = e21;
                fileDescriptor2 = socketTcp3;
                socketTcp2 = fileDescriptor2;
                Throwable cause22222222 = e.getCause();
                Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
                if (socketTcp2 != null) {
                }
                return result2;
            } catch (Throwable th11) {
                th = th11;
                socketTcp = socketTcp3;
                th = th;
                if (socketTcp != null) {
                }
                throw th;
            }
        } catch (RuntimeException e22) {
            e = e22;
            Log.e(TAG, "RuntimeException " + e);
            if (socketTcp2 != null) {
            }
            return result2;
        } catch (Exception e23) {
            e = e23;
            Throwable cause222222222 = e.getCause();
            Log.e(TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e);
            if (socketTcp2 != null) {
            }
            return result2;
        } catch (Throwable th12) {
            socketTcp = null;
            th = th12;
            if (socketTcp != null) {
            }
            throw th;
        }
    }

    public boolean pingTcp(byte[] srcAddrByte, byte[] dstAddrByte, int dstPort, int ttl, int timeoutMillis) throws IOException {
        InetAddress srcAddr = InetAddress.getByAddress(srcAddrByte);
        InetAddress dstAddr = InetAddress.getByAddress(dstAddrByte);
        int oldTag = TrafficStats.getAndSetThreadStatsTag(-190);
        FileDescriptor fd = null;
        try {
            fd = IoBridge.socket(OsConstants.AF_INET, OsConstants.SOCK_STREAM, 0);
            if (ttl > 0) {
                Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, OsConstants.IP_TTL, Integer.valueOf(ttl).intValue());
            }
            if (srcAddr != null) {
                Os.bind(fd, srcAddr, 0);
            }
            Os.setsockoptIfreq(fd, OsConstants.SOL_SOCKET, OsConstants.SO_BINDTODEVICE, "wlan0");
            Os.connect(fd, dstAddr, dstPort);
            return true;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (!(!(cause instanceof ErrnoException) || ((ErrnoException) cause).errno == OsConstants.EHOSTUNREACH || ((ErrnoException) cause).errno == OsConstants.EADDRNOTAVAIL)) {
                Log.d(TAG, "Exception : " + e);
            }
            return false;
        } finally {
            IoBridge.closeAndSignalBlockedThreads(fd);
            TrafficStats.setThreadStatsTag(oldTag);
        }
    }

    private long calculationChecksum(byte[] buf) {
        if (buf == null) {
            return 0;
        }
        int length = buf.length;
        int i = 0;
        long sum = 0;
        while (length > 1) {
            sum += (long) ((65280 & (buf[i] << 8)) | (buf[i + 1] & 255));
            if ((-65536 & sum) > 0) {
                sum = (sum & 65535) + 1;
            }
            i += 2;
            length -= 2;
        }
        if (length > 0) {
            sum += (long) (65280 & (buf[i] << 8));
            if ((-65536 & sum) > 0) {
                sum = (sum & 65535) + 1;
            }
        }
        return (~sum) & 65535;
    }

    /* JADX WARNING: Removed duplicated region for block: B:10:0x0021  */
    private byte[] getMacAddress(String interfaceName) {
        try {
            if (NetworkInterface.getNetworkInterfaces() == null) {
                Log.e(TAG, "NetworkInterface.getNetworkInterfaces() is null");
                return null;
            }
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (interfaceName == null || intf.getName().equalsIgnoreCase(interfaceName)) {
                    byte[] mac = intf.getHardwareAddress();
                    ByteBuffer concatBuf = ByteBuffer.allocate(6);
                    if (mac == null) {
                        Log.e(TAG, "Get hardware interface failed");
                        return null;
                    }
                    concatBuf.clear();
                    concatBuf.order(ByteOrder.BIG_ENDIAN);
                    for (int idx = 0; idx < mac.length; idx++) {
                        concatBuf.put(MobileWipsFrameworkUtil.intToByteArray(Integer.valueOf(String.format("%02X", Byte.valueOf(mac[idx])), 16).intValue())[3]);
                    }
                    concatBuf.flip();
                    return concatBuf.array();
                }
                while (r3.hasNext()) {
                }
            }
            return null;
        } catch (Exception e) {
        }
    }
}
