package com.samsung.android.server.wifi.softap;

import com.samsung.android.server.wifi.mobilewips.external.NetworkConstants;
import java.net.Inet4Address;
import java.nio.ByteBuffer;

/* access modifiers changed from: package-private */
/* compiled from: DhcpPacket */
public class DhcpNakPacket extends DhcpPacket {
    DhcpNakPacket(int transId, short secs, Inet4Address relayIp, byte[] clientMac, boolean broadcast) {
        super(transId, secs, INADDR_ANY, INADDR_ANY, INADDR_ANY, relayIp, clientMac, broadcast);
    }

    @Override // com.samsung.android.server.wifi.softap.DhcpPacket
    public String toString() {
        String s = super.toString();
        StringBuilder sb = new StringBuilder();
        sb.append(s);
        sb.append(" NAK, reason ");
        sb.append(this.mMessage == null ? "(none)" : this.mMessage);
        return sb.toString();
    }

    @Override // com.samsung.android.server.wifi.softap.DhcpPacket
    public ByteBuffer buildPacket(int encap, short destUdp, short srcUdp) {
        ByteBuffer result = ByteBuffer.allocate(NetworkConstants.ETHER_MTU);
        fillInPacket(encap, INADDR_ANY, INADDR_ANY, destUdp, srcUdp, result, (byte) 2, this.mBroadcast);
        result.flip();
        return result;
    }

    /* access modifiers changed from: package-private */
    @Override // com.samsung.android.server.wifi.softap.DhcpPacket
    public void finishPacket(ByteBuffer buffer) {
        addTlv(buffer, (byte) 53, (byte) 6);
        addTlv(buffer, (byte) 54, this.mServerIdentifier);
        addTlv(buffer, (byte) 56, this.mMessage);
        addTlvEnd(buffer);
    }
}
