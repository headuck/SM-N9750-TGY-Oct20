package com.samsung.android.server.wifi;

import android.net.LinkProperties;
import android.net.util.InterfaceParams;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.PacketSocketAddress;
import android.system.StructTimeval;
import android.util.Log;
import com.samsung.android.server.wifi.bigdata.WifiChipInfo;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import libcore.io.IoBridge;

public class ArpPeer {
    private static final int ARP_LENGTH = 28;
    private static boolean A_DBG = true;
    private static final int ETHERNET_TYPE = 1;
    private static final byte[] ETHER_ARP_TYPE = {8, 6};
    private static final int ETHER_HEADER_LENGTH = 14;
    private static final int IPV4_LENGTH = 4;
    private static final int MAC_ADDR_LENGTH = 6;
    private static final int MAX_LENGTH = 1500;
    private static final String TAG = "ArpPeer";
    private byte[] L2_BROADCAST = {-1, -1, -1, -1, -1, -1};
    private byte[] SRC_ADDR = new byte[0];
    private FileDescriptor mSocket;
    private FileDescriptor mSocketGArp;
    private FileDescriptor mSocketGArpRecv;
    private FileDescriptor mSocketRecv;

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
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

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void makeGARP(ByteBuffer buf, byte[] senderMAC, byte[] senderIP) {
        if (buf != null && senderMAC != null && senderIP != null) {
            buf.putShort(1);
            buf.putShort((short) OsConstants.ETH_P_IP);
            buf.put((byte) 6);
            buf.put((byte) 4);
            buf.putShort(1);
            buf.put(senderMAC);
            buf.put(senderIP);
            buf.put(this.L2_BROADCAST);
            buf.put(senderIP);
            buf.flip();
        }
    }

    public void checkArpReply(final LinkProperties linkProperties, final int timeoutMillis, final byte[] gateway, byte[] myAddr, String myMac) {
        WifiChipInfo.getInstance().setArpResult(false);
        new Thread(new Runnable() {
            /* class com.samsung.android.server.wifi.ArpPeer.RunnableC07011 */

            /* JADX WARNING: Code restructure failed: missing block: B:10:0x007b, code lost:
                android.util.Log.e(com.samsung.android.server.wifi.ArpPeer.TAG, "Exception");
             */
            /* JADX WARNING: Code restructure failed: missing block: B:72:0x01d7, code lost:
                r0 = move-exception;
             */
            /* JADX WARNING: Code restructure failed: missing block: B:73:0x01d8, code lost:
                android.util.Log.e(com.samsung.android.server.wifi.ArpPeer.TAG, "RuntimeException " + r0);
             */
            /* JADX WARNING: Code restructure failed: missing block: B:76:0x01f2, code lost:
                if (r17.this$0.mSocketRecv != null) goto L_0x01f4;
             */
            /* JADX WARNING: Code restructure failed: missing block: B:78:?, code lost:
                libcore.io.IoBridge.closeAndSignalBlockedThreads(r17.this$0.mSocketRecv);
             */
            /* JADX WARNING: Code restructure failed: missing block: B:79:0x01fe, code lost:
                r0 = move-exception;
             */
            /* JADX WARNING: Code restructure failed: missing block: B:80:0x01ff, code lost:
                android.util.Log.e(com.samsung.android.server.wifi.ArpPeer.TAG, "IOException " + r0);
             */
            /* JADX WARNING: Code restructure failed: missing block: B:82:0x0218, code lost:
                r0 = e;
             */
            /* JADX WARNING: Code restructure failed: missing block: B:83:0x0219, code lost:
                r2 = new java.lang.StringBuilder();
             */
            /* JADX WARNING: Failed to process nested try/catch */
            /* JADX WARNING: Removed duplicated region for block: B:72:0x01d7 A[ExcHandler: RuntimeException (r0v0 'e' java.lang.RuntimeException A[CUSTOM_DECLARE]), Splitter:B:1:0x0008] */
            public void run() {
                StringBuilder sb;
                try {
                    InterfaceParams iParams = InterfaceParams.getByName(linkProperties.getInterfaceName());
                    int i = 0;
                    ArpPeer.this.mSocketRecv = Os.socket(OsConstants.AF_PACKET, OsConstants.SOCK_RAW, 0);
                    Os.bind(ArpPeer.this.mSocketRecv, new PacketSocketAddress((short) OsConstants.ETH_P_ARP, iParams.index));
                    byte[] desiredIp = gateway;
                    long timeout = SystemClock.elapsedRealtime() + ((long) timeoutMillis);
                    byte[] recvBuf = new byte[1500];
                    byte[] result_mac = new byte[6];
                    while (true) {
                        if (SystemClock.elapsedRealtime() < timeout) {
                            Os.setsockoptTimeval(ArpPeer.this.mSocketRecv, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(timeout - SystemClock.elapsedRealtime()));
                            int readLen = 0;
                            Log.d(ArpPeer.TAG, "start to read recvSocket");
                            readLen = Os.read(ArpPeer.this.mSocketRecv, recvBuf, i, recvBuf.length);
                            Log.d(ArpPeer.TAG, "readLen:" + readLen);
                            if (readLen >= 28 && recvBuf[14] == 0 && recvBuf[15] == 1 && recvBuf[16] == 8 && recvBuf[17] == 0 && recvBuf[18] == 6 && recvBuf[19] == 4 && recvBuf[20] == 0 && recvBuf[28] == desiredIp[i] && recvBuf[29] == desiredIp[1] && recvBuf[30] == desiredIp[2] && recvBuf[31] == desiredIp[3]) {
                                System.arraycopy(recvBuf, 22, result_mac, i, 6);
                                String convert_mac = ArpPeer.macToString(result_mac);
                                if (recvBuf[21] == 1) {
                                    Log.d(ArpPeer.TAG, "ARP Request");
                                } else if (recvBuf[21] == 2) {
                                    WifiChipInfo.getInstance().setArpResult(true);
                                    Log.d(ArpPeer.TAG, "ARP result(" + convert_mac + ")");
                                    break;
                                }
                            }
                            i = 0;
                        }
                    }
                    try {
                        if (ArpPeer.this.mSocketRecv != null) {
                            try {
                                IoBridge.closeAndSignalBlockedThreads(ArpPeer.this.mSocketRecv);
                            } catch (IOException ignored) {
                                Log.e(ArpPeer.TAG, "IOException " + ignored);
                            }
                        }
                        ArpPeer.this.mSocketRecv = null;
                        return;
                    } catch (Exception e) {
                        ex = e;
                        sb = new StringBuilder();
                        sb.append("Exception ");
                        sb.append(ex);
                        Log.e(ArpPeer.TAG, sb.toString());
                        return;
                    }
                } catch (RuntimeException e2) {
                } catch (Exception e3) {
                    Throwable cause = e3.getCause();
                    if ((cause instanceof ErrnoException) && ((ErrnoException) cause).errno != OsConstants.EAGAIN) {
                        Log.e(ArpPeer.TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e3);
                    }
                    try {
                        if (ArpPeer.this.mSocketRecv != null) {
                            try {
                                IoBridge.closeAndSignalBlockedThreads(ArpPeer.this.mSocketRecv);
                            } catch (IOException ignored2) {
                                Log.e(ArpPeer.TAG, "IOException " + ignored2);
                            }
                        }
                        ArpPeer.this.mSocketRecv = null;
                        return;
                    } catch (Exception e4) {
                        ex = e4;
                        sb = new StringBuilder();
                        sb.append("Exception ");
                        sb.append(ex);
                        Log.e(ArpPeer.TAG, sb.toString());
                        return;
                    }
                } catch (Throwable ex) {
                    try {
                        if (ArpPeer.this.mSocketRecv != null) {
                            try {
                                IoBridge.closeAndSignalBlockedThreads(ArpPeer.this.mSocketRecv);
                            } catch (IOException ignored3) {
                                Log.e(ArpPeer.TAG, "IOException " + ignored3);
                            }
                        }
                        ArpPeer.this.mSocketRecv = null;
                    } catch (Exception ex2) {
                        Log.e(ArpPeer.TAG, "Exception " + ex2);
                    }
                    throw ex;
                }
                ArpPeer.this.mSocketRecv = null;
            }
        }).start();
    }

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
                        concatBuf.put(intToByteArray(Integer.valueOf(String.format("%02X", Byte.valueOf(mac[idx])), 16).intValue())[3]);
                    }
                    concatBuf.flip();
                    return concatBuf.array();
                }
            }
            return null;
        } catch (Exception ex) {
            Log.e(TAG, "Exception " + ex);
        }
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private byte[] macStringToByteArray(String dstMac) {
        byte[] dstMAC = new byte[6];
        if (dstMac != null) {
            for (int i = 0; i < 6; i++) {
                dstMAC[i] = (byte) Integer.parseInt(dstMac.substring(i * 3, (i * 3) + 2), 16);
            }
        }
        return dstMAC;
    }

    private static Integer getInterfaceIndex(String ifname) {
        try {
            return Integer.valueOf(NetworkInterface.getByName(ifname).getIndex());
        } catch (NullPointerException | SocketException e) {
            return null;
        }
    }

    public static String macToString(byte[] mac) {
        String macAddr = "";
        if (mac == null) {
            return null;
        }
        for (int i = 0; i < mac.length; i++) {
            try {
                String hexString = "0" + Integer.toHexString(mac[i]);
                macAddr = macAddr + hexString.substring(hexString.length() - 2);
                if (i != mac.length - 1) {
                    macAddr = macAddr + ":";
                }
            } catch (IndexOutOfBoundsException e) {
                Log.d(TAG, "macAddressFromArpResult indexoutofboundsexception");
                return null;
            }
        }
        return macAddr;
    }

    public static byte[] intToByteArray(int value) {
        ByteBuffer converter = ByteBuffer.allocate(4);
        converter.order(ByteOrder.nativeOrder());
        converter.putInt(value);
        return converter.array();
    }

    public void sendGArp(final LinkProperties mLinkProperties, final byte[] myAddr, final String myMac) {
        new Thread(new Runnable() {
            /* class com.samsung.android.server.wifi.ArpPeer.RunnableC07022 */

            public void run() {
                StringBuilder sb;
                try {
                    ArpPeer.this.mSocketGArp = Os.socket(OsConstants.AF_PACKET, OsConstants.SOCK_RAW, 0);
                    InterfaceParams iParams = InterfaceParams.getByName(mLinkProperties.getInterfaceName());
                    Os.bind(ArpPeer.this.mSocketGArp, new PacketSocketAddress((short) OsConstants.ETH_P_IP, iParams.index));
                    byte[] macAddr = ArpPeer.this.macStringToByteArray(myMac);
                    ArpPeer.this.SRC_ADDR = macAddr;
                    ByteBuffer sumBuf = ByteBuffer.allocate(1500);
                    ByteBuffer etherBuf = ByteBuffer.allocate(14);
                    ByteBuffer arpBuf = ByteBuffer.allocate(28);
                    sumBuf.clear();
                    sumBuf.order(ByteOrder.BIG_ENDIAN);
                    etherBuf.clear();
                    etherBuf.order(ByteOrder.BIG_ENDIAN);
                    arpBuf.clear();
                    arpBuf.order(ByteOrder.BIG_ENDIAN);
                    ArpPeer.this.makeEthernet(etherBuf, ArpPeer.this.L2_BROADCAST, ArpPeer.this.SRC_ADDR, ArpPeer.ETHER_ARP_TYPE);
                    ArpPeer.this.makeGARP(arpBuf, macAddr, myAddr);
                    sumBuf.put(etherBuf).put(arpBuf);
                    sumBuf.flip();
                    Os.sendto(ArpPeer.this.mSocketGArp, sumBuf.array(), 0, sumBuf.limit(), 0, new PacketSocketAddress(iParams.index, ArpPeer.this.L2_BROADCAST));
                    try {
                        if (((ArpPeer) ArpPeer.this).mSocketGArp != null) {
                            IoBridge.closeAndSignalBlockedThreads(ArpPeer.this.mSocketGArp);
                        }
                        ArpPeer.this.mSocketGArp = null;
                    } catch (IOException e) {
                        ex = e;
                        sb = new StringBuilder();
                        sb.append("IOException ");
                        sb.append(ex);
                        Log.e(ArpPeer.TAG, sb.toString());
                    }
                } catch (RuntimeException e2) {
                    Log.e(ArpPeer.TAG, "RuntimeException " + e2);
                    try {
                        if (ArpPeer.this.mSocketGArp != null) {
                            IoBridge.closeAndSignalBlockedThreads(ArpPeer.this.mSocketGArp);
                        }
                        ArpPeer.this.mSocketGArp = null;
                    } catch (IOException e3) {
                        ex = e3;
                        sb = new StringBuilder();
                        sb.append("IOException ");
                        sb.append(ex);
                        Log.e(ArpPeer.TAG, sb.toString());
                    }
                } catch (Exception e4) {
                    Throwable cause = e4.getCause();
                    if ((cause instanceof ErrnoException) && ((ErrnoException) cause).errno != OsConstants.EAGAIN) {
                        Log.e(ArpPeer.TAG, "Exception " + Thread.currentThread().getStackTrace()[2].getLineNumber() + e4);
                    }
                    try {
                        if (ArpPeer.this.mSocketGArp != null) {
                            IoBridge.closeAndSignalBlockedThreads(ArpPeer.this.mSocketGArp);
                        }
                        ArpPeer.this.mSocketGArp = null;
                    } catch (IOException e5) {
                        ex = e5;
                        sb = new StringBuilder();
                        sb.append("IOException ");
                        sb.append(ex);
                        Log.e(ArpPeer.TAG, sb.toString());
                    }
                } catch (Throwable th) {
                    try {
                        if (ArpPeer.this.mSocketGArp != null) {
                            IoBridge.closeAndSignalBlockedThreads(ArpPeer.this.mSocketGArp);
                        }
                        ArpPeer.this.mSocketGArp = null;
                    } catch (IOException ex) {
                        Log.e(ArpPeer.TAG, "IOException " + ex);
                    }
                    throw th;
                }
            }
        }).start();
    }
}
