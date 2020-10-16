package com.samsung.android.server.wifi.mobilewips.framework;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.List;

public interface IMobileWipsPacketSender extends IInterface {
    boolean pingTcp(byte[] bArr, byte[] bArr2, int i, int i2, int i3) throws RemoteException;

    List<String> sendArp(int i, byte[] bArr, byte[] bArr2, String str) throws RemoteException;

    List<String> sendArpToSniffing(int i, byte[] bArr, byte[] bArr2, String str) throws RemoteException;

    int sendDhcp(int i, byte[] bArr, int i2, String str) throws RemoteException;

    byte[] sendDns(long[] jArr, byte[] bArr, byte[] bArr2, byte[] bArr3, String str, boolean z) throws RemoteException;

    boolean sendDnsQueries(long[] jArr, byte[] bArr, byte[] bArr2, String str, List<String> list, int i) throws RemoteException;

    List<String> sendIcmp(int i, byte[] bArr, byte[] bArr2, String str) throws RemoteException;

    boolean sendTcp(int i, byte[] bArr, byte[] bArr2, String str) throws RemoteException;

    public static class Default implements IMobileWipsPacketSender {
        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
        public List<String> sendArp(int timeoutMillis, byte[] gateway, byte[] myAddr, String myMac) throws RemoteException {
            return null;
        }

        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
        public List<String> sendArpToSniffing(int timeoutMillis, byte[] gateway, byte[] myAddr, String myMac) throws RemoteException {
            return null;
        }

        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
        public List<String> sendIcmp(int timeoutMillis, byte[] gateway, byte[] myAddr, String dstMac) throws RemoteException {
            return null;
        }

        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
        public int sendDhcp(int timeoutMillis, byte[] myAddr, int equalOption, String equalString) throws RemoteException {
            return 0;
        }

        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
        public byte[] sendDns(long[] timeoutMillis, byte[] srcAddr, byte[] dstAddr, byte[] dnsMessage, String dstMac, boolean isUDP) throws RemoteException {
            return null;
        }

        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
        public boolean sendDnsQueries(long[] timeoutMillis, byte[] srcAddr, byte[] dstAddr, String dstMac, List<String> list, int tcpIndex) throws RemoteException {
            return false;
        }

        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
        public boolean sendTcp(int timeoutMillis, byte[] gateway, byte[] myAddr, String myMac) throws RemoteException {
            return false;
        }

        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
        public boolean pingTcp(byte[] srcAddr, byte[] dstAddr, int dstPort, int ttl, int timeoutMillis) throws RemoteException {
            return false;
        }

        public IBinder asBinder() {
            return null;
        }
    }

    public static abstract class Stub extends Binder implements IMobileWipsPacketSender {
        private static final String DESCRIPTOR = "com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender";
        static final int TRANSACTION_pingTcp = 8;
        static final int TRANSACTION_sendArp = 1;
        static final int TRANSACTION_sendArpToSniffing = 2;
        static final int TRANSACTION_sendDhcp = 4;
        static final int TRANSACTION_sendDns = 5;
        static final int TRANSACTION_sendDnsQueries = 6;
        static final int TRANSACTION_sendIcmp = 3;
        static final int TRANSACTION_sendTcp = 7;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IMobileWipsPacketSender asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof IMobileWipsPacketSender)) {
                return new Proxy(obj);
            }
            return (IMobileWipsPacketSender) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code != 1598968902) {
                switch (code) {
                    case 1:
                        data.enforceInterface(DESCRIPTOR);
                        List<String> _result = sendArp(data.readInt(), data.createByteArray(), data.createByteArray(), data.readString());
                        reply.writeNoException();
                        reply.writeStringList(_result);
                        return true;
                    case 2:
                        data.enforceInterface(DESCRIPTOR);
                        List<String> _result2 = sendArpToSniffing(data.readInt(), data.createByteArray(), data.createByteArray(), data.readString());
                        reply.writeNoException();
                        reply.writeStringList(_result2);
                        return true;
                    case 3:
                        data.enforceInterface(DESCRIPTOR);
                        List<String> _result3 = sendIcmp(data.readInt(), data.createByteArray(), data.createByteArray(), data.readString());
                        reply.writeNoException();
                        reply.writeStringList(_result3);
                        return true;
                    case 4:
                        data.enforceInterface(DESCRIPTOR);
                        int _result4 = sendDhcp(data.readInt(), data.createByteArray(), data.readInt(), data.readString());
                        reply.writeNoException();
                        reply.writeInt(_result4);
                        return true;
                    case 5:
                        data.enforceInterface(DESCRIPTOR);
                        byte[] _result5 = sendDns(data.createLongArray(), data.createByteArray(), data.createByteArray(), data.createByteArray(), data.readString(), data.readInt() != 0);
                        reply.writeNoException();
                        reply.writeByteArray(_result5);
                        return true;
                    case 6:
                        data.enforceInterface(DESCRIPTOR);
                        boolean sendDnsQueries = sendDnsQueries(data.createLongArray(), data.createByteArray(), data.createByteArray(), data.readString(), data.createStringArrayList(), data.readInt());
                        reply.writeNoException();
                        reply.writeInt(sendDnsQueries ? 1 : 0);
                        return true;
                    case 7:
                        data.enforceInterface(DESCRIPTOR);
                        boolean sendTcp = sendTcp(data.readInt(), data.createByteArray(), data.createByteArray(), data.readString());
                        reply.writeNoException();
                        reply.writeInt(sendTcp ? 1 : 0);
                        return true;
                    case 8:
                        data.enforceInterface(DESCRIPTOR);
                        boolean pingTcp = pingTcp(data.createByteArray(), data.createByteArray(), data.readInt(), data.readInt(), data.readInt());
                        reply.writeNoException();
                        reply.writeInt(pingTcp ? 1 : 0);
                        return true;
                    default:
                        return super.onTransact(code, data, reply, flags);
                }
            } else {
                reply.writeString(DESCRIPTOR);
                return true;
            }
        }

        /* access modifiers changed from: private */
        public static class Proxy implements IMobileWipsPacketSender {
            public static IMobileWipsPacketSender sDefaultImpl;
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
            public List<String> sendArp(int timeoutMillis, byte[] gateway, byte[] myAddr, String myMac) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(timeoutMillis);
                    _data.writeByteArray(gateway);
                    _data.writeByteArray(myAddr);
                    _data.writeString(myMac);
                    if (!this.mRemote.transact(1, _data, _reply, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().sendArp(timeoutMillis, gateway, myAddr, myMac);
                    }
                    _reply.readException();
                    List<String> _result = _reply.createStringArrayList();
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
            public List<String> sendArpToSniffing(int timeoutMillis, byte[] gateway, byte[] myAddr, String myMac) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(timeoutMillis);
                    _data.writeByteArray(gateway);
                    _data.writeByteArray(myAddr);
                    _data.writeString(myMac);
                    if (!this.mRemote.transact(2, _data, _reply, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().sendArpToSniffing(timeoutMillis, gateway, myAddr, myMac);
                    }
                    _reply.readException();
                    List<String> _result = _reply.createStringArrayList();
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
            public List<String> sendIcmp(int timeoutMillis, byte[] gateway, byte[] myAddr, String dstMac) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(timeoutMillis);
                    _data.writeByteArray(gateway);
                    _data.writeByteArray(myAddr);
                    _data.writeString(dstMac);
                    if (!this.mRemote.transact(3, _data, _reply, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().sendIcmp(timeoutMillis, gateway, myAddr, dstMac);
                    }
                    _reply.readException();
                    List<String> _result = _reply.createStringArrayList();
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
            public int sendDhcp(int timeoutMillis, byte[] myAddr, int equalOption, String equalString) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(timeoutMillis);
                    _data.writeByteArray(myAddr);
                    _data.writeInt(equalOption);
                    _data.writeString(equalString);
                    if (!this.mRemote.transact(4, _data, _reply, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().sendDhcp(timeoutMillis, myAddr, equalOption, equalString);
                    }
                    _reply.readException();
                    int _result = _reply.readInt();
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
            public byte[] sendDns(long[] timeoutMillis, byte[] srcAddr, byte[] dstAddr, byte[] dnsMessage, String dstMac, boolean isUDP) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    try {
                        _data.writeLongArray(timeoutMillis);
                    } catch (Throwable th) {
                        th = th;
                        _reply.recycle();
                        _data.recycle();
                        throw th;
                    }
                    try {
                        _data.writeByteArray(srcAddr);
                        try {
                            _data.writeByteArray(dstAddr);
                            try {
                                _data.writeByteArray(dnsMessage);
                            } catch (Throwable th2) {
                                th = th2;
                                _reply.recycle();
                                _data.recycle();
                                throw th;
                            }
                        } catch (Throwable th3) {
                            th = th3;
                            _reply.recycle();
                            _data.recycle();
                            throw th;
                        }
                    } catch (Throwable th4) {
                        th = th4;
                        _reply.recycle();
                        _data.recycle();
                        throw th;
                    }
                    try {
                        _data.writeString(dstMac);
                        _data.writeInt(isUDP ? 1 : 0);
                    } catch (Throwable th5) {
                        th = th5;
                        _reply.recycle();
                        _data.recycle();
                        throw th;
                    }
                    try {
                        if (this.mRemote.transact(5, _data, _reply, 0) || Stub.getDefaultImpl() == null) {
                            _reply.readException();
                            byte[] _result = _reply.createByteArray();
                            _reply.recycle();
                            _data.recycle();
                            return _result;
                        }
                        byte[] sendDns = Stub.getDefaultImpl().sendDns(timeoutMillis, srcAddr, dstAddr, dnsMessage, dstMac, isUDP);
                        _reply.recycle();
                        _data.recycle();
                        return sendDns;
                    } catch (Throwable th6) {
                        th = th6;
                        _reply.recycle();
                        _data.recycle();
                        throw th;
                    }
                } catch (Throwable th7) {
                    th = th7;
                    _reply.recycle();
                    _data.recycle();
                    throw th;
                }
            }

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
            public boolean sendDnsQueries(long[] timeoutMillis, byte[] srcAddr, byte[] dstAddr, String dstMac, List<String> dnsMessages, int tcpIndex) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    try {
                        _data.writeLongArray(timeoutMillis);
                    } catch (Throwable th) {
                        th = th;
                        _reply.recycle();
                        _data.recycle();
                        throw th;
                    }
                    try {
                        _data.writeByteArray(srcAddr);
                        try {
                            _data.writeByteArray(dstAddr);
                            try {
                                _data.writeString(dstMac);
                            } catch (Throwable th2) {
                                th = th2;
                                _reply.recycle();
                                _data.recycle();
                                throw th;
                            }
                        } catch (Throwable th3) {
                            th = th3;
                            _reply.recycle();
                            _data.recycle();
                            throw th;
                        }
                    } catch (Throwable th4) {
                        th = th4;
                        _reply.recycle();
                        _data.recycle();
                        throw th;
                    }
                    try {
                        _data.writeStringList(dnsMessages);
                        try {
                            _data.writeInt(tcpIndex);
                            boolean _result = false;
                            if (this.mRemote.transact(6, _data, _reply, 0) || Stub.getDefaultImpl() == null) {
                                _reply.readException();
                                if (_reply.readInt() != 0) {
                                    _result = true;
                                }
                                _reply.recycle();
                                _data.recycle();
                                return _result;
                            }
                            boolean sendDnsQueries = Stub.getDefaultImpl().sendDnsQueries(timeoutMillis, srcAddr, dstAddr, dstMac, dnsMessages, tcpIndex);
                            _reply.recycle();
                            _data.recycle();
                            return sendDnsQueries;
                        } catch (Throwable th5) {
                            th = th5;
                            _reply.recycle();
                            _data.recycle();
                            throw th;
                        }
                    } catch (Throwable th6) {
                        th = th6;
                        _reply.recycle();
                        _data.recycle();
                        throw th;
                    }
                } catch (Throwable th7) {
                    th = th7;
                    _reply.recycle();
                    _data.recycle();
                    throw th;
                }
            }

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
            public boolean sendTcp(int timeoutMillis, byte[] gateway, byte[] myAddr, String myMac) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(timeoutMillis);
                    _data.writeByteArray(gateway);
                    _data.writeByteArray(myAddr);
                    _data.writeString(myMac);
                    boolean _result = false;
                    if (!this.mRemote.transact(7, _data, _reply, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().sendTcp(timeoutMillis, gateway, myAddr, myMac);
                    }
                    _reply.readException();
                    if (_reply.readInt() != 0) {
                        _result = true;
                    }
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsPacketSender
            public boolean pingTcp(byte[] srcAddr, byte[] dstAddr, int dstPort, int ttl, int timeoutMillis) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    try {
                        _data.writeByteArray(srcAddr);
                    } catch (Throwable th) {
                        th = th;
                        _reply.recycle();
                        _data.recycle();
                        throw th;
                    }
                    try {
                        _data.writeByteArray(dstAddr);
                        try {
                            _data.writeInt(dstPort);
                            try {
                                _data.writeInt(ttl);
                            } catch (Throwable th2) {
                                th = th2;
                                _reply.recycle();
                                _data.recycle();
                                throw th;
                            }
                        } catch (Throwable th3) {
                            th = th3;
                            _reply.recycle();
                            _data.recycle();
                            throw th;
                        }
                    } catch (Throwable th4) {
                        th = th4;
                        _reply.recycle();
                        _data.recycle();
                        throw th;
                    }
                    try {
                        _data.writeInt(timeoutMillis);
                        try {
                            boolean _result = false;
                            if (this.mRemote.transact(8, _data, _reply, 0) || Stub.getDefaultImpl() == null) {
                                _reply.readException();
                                if (_reply.readInt() != 0) {
                                    _result = true;
                                }
                                _reply.recycle();
                                _data.recycle();
                                return _result;
                            }
                            boolean pingTcp = Stub.getDefaultImpl().pingTcp(srcAddr, dstAddr, dstPort, ttl, timeoutMillis);
                            _reply.recycle();
                            _data.recycle();
                            return pingTcp;
                        } catch (Throwable th5) {
                            th = th5;
                            _reply.recycle();
                            _data.recycle();
                            throw th;
                        }
                    } catch (Throwable th6) {
                        th = th6;
                        _reply.recycle();
                        _data.recycle();
                        throw th;
                    }
                } catch (Throwable th7) {
                    th = th7;
                    _reply.recycle();
                    _data.recycle();
                    throw th;
                }
            }
        }

        public static boolean setDefaultImpl(IMobileWipsPacketSender impl) {
            if (Proxy.sDefaultImpl != null || impl == null) {
                return false;
            }
            Proxy.sDefaultImpl = impl;
            return true;
        }

        public static IMobileWipsPacketSender getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }
    }
}
