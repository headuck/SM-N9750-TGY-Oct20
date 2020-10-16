package com.samsung.android.server.wifi.mobilewips.framework;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.List;

public interface IMobileWipsFramework extends IInterface {
    List<MobileWipsScanResult> getScanResults() throws RemoteException;

    boolean invokeMethodBool(int i) throws RemoteException;

    String invokeMethodStr(int i) throws RemoteException;

    void partialScanStart(Message message) throws RemoteException;

    void sendHWParamToHQMwithAppId(int i, String str, String str2, String str3, String str4, String str5, String str6, String str7, String str8, String str9) throws RemoteException;

    public static class Default implements IMobileWipsFramework {
        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsFramework
        public boolean invokeMethodBool(int value) throws RemoteException {
            return false;
        }

        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsFramework
        public String invokeMethodStr(int index) throws RemoteException {
            return null;
        }

        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsFramework
        public void partialScanStart(Message msg) throws RemoteException {
        }

        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsFramework
        public List<MobileWipsScanResult> getScanResults() throws RemoteException {
            return null;
        }

        @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsFramework
        public void sendHWParamToHQMwithAppId(int type, String compId, String feature, String hitType, String compVer, String compManufacture, String devCustomDataSet, String basicCustomDataSet, String priCustomDataSet, String appId) throws RemoteException {
        }

        public IBinder asBinder() {
            return null;
        }
    }

    public static abstract class Stub extends Binder implements IMobileWipsFramework {
        private static final String DESCRIPTOR = "com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsFramework";
        static final int TRANSACTION_getScanResults = 4;
        static final int TRANSACTION_invokeMethodBool = 1;
        static final int TRANSACTION_invokeMethodStr = 2;
        static final int TRANSACTION_partialScanStart = 3;
        static final int TRANSACTION_sendHWParamToHQMwithAppId = 5;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IMobileWipsFramework asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof IMobileWipsFramework)) {
                return new Proxy(obj);
            }
            return (IMobileWipsFramework) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            Message _arg0;
            if (code == 1) {
                data.enforceInterface(DESCRIPTOR);
                boolean invokeMethodBool = invokeMethodBool(data.readInt());
                reply.writeNoException();
                reply.writeInt(invokeMethodBool ? 1 : 0);
                return true;
            } else if (code == 2) {
                data.enforceInterface(DESCRIPTOR);
                String _result = invokeMethodStr(data.readInt());
                reply.writeNoException();
                reply.writeString(_result);
                return true;
            } else if (code == 3) {
                data.enforceInterface(DESCRIPTOR);
                if (data.readInt() != 0) {
                    _arg0 = (Message) Message.CREATOR.createFromParcel(data);
                } else {
                    _arg0 = null;
                }
                partialScanStart(_arg0);
                reply.writeNoException();
                return true;
            } else if (code == 4) {
                data.enforceInterface(DESCRIPTOR);
                List<MobileWipsScanResult> _result2 = getScanResults();
                reply.writeNoException();
                reply.writeTypedList(_result2);
                return true;
            } else if (code == 5) {
                data.enforceInterface(DESCRIPTOR);
                sendHWParamToHQMwithAppId(data.readInt(), data.readString(), data.readString(), data.readString(), data.readString(), data.readString(), data.readString(), data.readString(), data.readString(), data.readString());
                reply.writeNoException();
                return true;
            } else if (code != 1598968902) {
                return super.onTransact(code, data, reply, flags);
            } else {
                reply.writeString(DESCRIPTOR);
                return true;
            }
        }

        /* access modifiers changed from: private */
        public static class Proxy implements IMobileWipsFramework {
            public static IMobileWipsFramework sDefaultImpl;
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

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsFramework
            public boolean invokeMethodBool(int value) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(value);
                    boolean _result = false;
                    if (!this.mRemote.transact(1, _data, _reply, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().invokeMethodBool(value);
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

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsFramework
            public String invokeMethodStr(int index) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(index);
                    if (!this.mRemote.transact(2, _data, _reply, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().invokeMethodStr(index);
                    }
                    _reply.readException();
                    String _result = _reply.readString();
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsFramework
            public void partialScanStart(Message msg) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (msg != null) {
                        _data.writeInt(1);
                        msg.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    if (this.mRemote.transact(3, _data, _reply, 0) || Stub.getDefaultImpl() == null) {
                        _reply.readException();
                        _reply.recycle();
                        _data.recycle();
                        return;
                    }
                    Stub.getDefaultImpl().partialScanStart(msg);
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsFramework
            public List<MobileWipsScanResult> getScanResults() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (!this.mRemote.transact(4, _data, _reply, 0) && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().getScanResults();
                    }
                    _reply.readException();
                    List<MobileWipsScanResult> _result = _reply.createTypedArrayList(MobileWipsScanResult.CREATOR);
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.samsung.android.server.wifi.mobilewips.framework.IMobileWipsFramework
            public void sendHWParamToHQMwithAppId(int type, String compId, String feature, String hitType, String compVer, String compManufacture, String devCustomDataSet, String basicCustomDataSet, String priCustomDataSet, String appId) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    try {
                        _data.writeInt(type);
                    } catch (Throwable th) {
                        th = th;
                        _reply.recycle();
                        _data.recycle();
                        throw th;
                    }
                    try {
                        _data.writeString(compId);
                        _data.writeString(feature);
                        _data.writeString(hitType);
                        _data.writeString(compVer);
                        _data.writeString(compManufacture);
                        _data.writeString(devCustomDataSet);
                        _data.writeString(basicCustomDataSet);
                        _data.writeString(priCustomDataSet);
                        _data.writeString(appId);
                        if (this.mRemote.transact(5, _data, _reply, 0) || Stub.getDefaultImpl() == null) {
                            _reply.readException();
                            _reply.recycle();
                            _data.recycle();
                            return;
                        }
                        Stub.getDefaultImpl().sendHWParamToHQMwithAppId(type, compId, feature, hitType, compVer, compManufacture, devCustomDataSet, basicCustomDataSet, priCustomDataSet, appId);
                        _reply.recycle();
                        _data.recycle();
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
            }
        }

        public static boolean setDefaultImpl(IMobileWipsFramework impl) {
            if (Proxy.sDefaultImpl != null || impl == null) {
                return false;
            }
            Proxy.sDefaultImpl = impl;
            return true;
        }

        public static IMobileWipsFramework getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }
    }
}
