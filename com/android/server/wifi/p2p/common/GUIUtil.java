package com.android.server.wifi.p2p.common;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.TypedValue;
import com.android.server.wifi.WifiGeofenceDBHelper;
import java.io.IOException;
import java.io.InputStream;

public class GUIUtil {
    static final String TAG = "GUIUtil";
    private static volatile GUIUtil mInstance = null;
    private Context mContext = null;

    public GUIUtil(Context context) {
        this.mContext = context;
    }

    public static GUIUtil getInstance(Context context) {
        if (context != null && mInstance == null) {
            synchronized (GUIUtil.class) {
                if (mInstance == null) {
                    mInstance = new GUIUtil(context);
                }
            }
        }
        return mInstance;
    }

    public static final Bitmap getIconBackground(int iconSize, int circleSize, int color) {
        Bitmap colorIcon = Bitmap.createBitmap(iconSize * 2, iconSize * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(colorIcon);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(color);
        canvas.drawCircle((float) iconSize, (float) iconSize, (float) circleSize, paint);
        return colorIcon;
    }

    /* JADX WARNING: Removed duplicated region for block: B:111:0x01dd  */
    /* JADX WARNING: Removed duplicated region for block: B:113:0x01e2  */
    /* JADX WARNING: Removed duplicated region for block: B:115:0x01e7  */
    /* JADX WARNING: Removed duplicated region for block: B:119:0x01f4  */
    /* JADX WARNING: Removed duplicated region for block: B:121:0x01f9  */
    /* JADX WARNING: Removed duplicated region for block: B:123:0x01fe A[SYNTHETIC, Splitter:B:123:0x01fe] */
    /* JADX WARNING: Removed duplicated region for block: B:132:? A[RETURN, SYNTHETIC] */
    /* JADX WARNING: Removed duplicated region for block: B:135:? A[RETURN, SYNTHETIC] */
    /* JADX WARNING: Removed duplicated region for block: B:25:0x009a A[SYNTHETIC, Splitter:B:25:0x009a] */
    /* JADX WARNING: Removed duplicated region for block: B:86:0x0198  */
    /* JADX WARNING: Removed duplicated region for block: B:88:0x019d  */
    /* JADX WARNING: Removed duplicated region for block: B:90:0x01a2 A[SYNTHETIC, Splitter:B:90:0x01a2] */
    public static Bitmap getContactImage(Context context, String number) {
        InputStream clsInputStream;
        Cursor cursor;
        Cursor cursor2;
        Throwable th;
        Drawable myDrawable;
        Cursor phones = null;
        Cursor cursor3 = null;
        InputStream clsInputStream2 = null;
        String[] contactProjection = {WifiGeofenceDBHelper.KEY_ID, "display_name", "lookup"};
        try {
            ContentResolver cr = context.getContentResolver();
            if (cr != null) {
                phones = cr.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)), new String[]{"photo_uri"}, null, null, null);
                cursor3 = cr.query(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(number)), contactProjection, null, null, null);
                long id = -1;
                DefaultImageRequest request = null;
                if (cursor3 != null) {
                    try {
                        if (cursor3.moveToFirst()) {
                            id = cursor3.getLong(0);
                            try {
                                request = new DefaultImageRequest(cursor3.getString(1), cursor3.getString(2), true);
                                if (phones != null) {
                                    try {
                                        if (phones.moveToNext()) {
                                            String photoString = phones.getString(phones.getInt(phones.getColumnIndex("photo_uri")));
                                            if (photoString != null) {
                                                InputStream clsInputStream3 = context.getContentResolver().openInputStream(Uri.parse(photoString));
                                                Bitmap decodeStream = BitmapFactory.decodeStream(clsInputStream3);
                                                phones.close();
                                                if (cursor3 != null) {
                                                    cursor3.close();
                                                }
                                                if (clsInputStream3 != null) {
                                                    try {
                                                        clsInputStream3.close();
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                                return decodeStream;
                                            }
                                            if (request != null) {
                                                Drawable myDrawable2 = getDefaultImageForContact(context.getResources(), request, id);
                                                if (myDrawable2 != null) {
                                                    try {
                                                        int dpInpx = Util.dpTopx(context, (int) context.getResources().getDimension(17106259));
                                                        myDrawable2.setBounds(0, 0, dpInpx, dpInpx);
                                                        Bitmap bm = Util.drawableToBitmap(myDrawable2);
                                                        phones.close();
                                                        if (cursor3 != null) {
                                                            cursor3.close();
                                                        }
                                                        if (0 != 0) {
                                                            try {
                                                                clsInputStream2.close();
                                                            } catch (IOException e2) {
                                                                e2.printStackTrace();
                                                            }
                                                        }
                                                        return bm;
                                                    } catch (Exception e3) {
                                                        e = e3;
                                                        try {
                                                            e.printStackTrace();
                                                            if (phones != null) {
                                                                phones.close();
                                                            }
                                                            if (cursor3 != null) {
                                                                cursor3.close();
                                                            }
                                                            if (0 == 0) {
                                                                return null;
                                                            }
                                                            clsInputStream2.close();
                                                            return null;
                                                        } catch (Throwable th2) {
                                                            clsInputStream = null;
                                                            cursor = cursor3;
                                                            cursor2 = phones;
                                                            th = th2;
                                                            if (cursor2 != null) {
                                                                cursor2.close();
                                                            }
                                                            if (cursor != null) {
                                                                cursor.close();
                                                            }
                                                            if (clsInputStream != null) {
                                                                try {
                                                                    clsInputStream.close();
                                                                } catch (IOException e4) {
                                                                    e4.printStackTrace();
                                                                }
                                                            }
                                                            throw th;
                                                        }
                                                    }
                                                }
                                            }
                                            phones.close();
                                            if (cursor3 != null) {
                                                cursor3.close();
                                            }
                                            if (0 == 0) {
                                                return null;
                                            }
                                            try {
                                                clsInputStream2.close();
                                                return null;
                                            } catch (IOException e5) {
                                                e5.printStackTrace();
                                                return null;
                                            }
                                        }
                                    } catch (Exception e6) {
                                        e = e6;
                                        e.printStackTrace();
                                        if (phones != null) {
                                        }
                                        if (cursor3 != null) {
                                        }
                                        if (0 == 0) {
                                        }
                                    } catch (Throwable th3) {
                                        clsInputStream = null;
                                        cursor = cursor3;
                                        cursor2 = phones;
                                        th = th3;
                                        if (cursor2 != null) {
                                        }
                                        if (cursor != null) {
                                        }
                                        if (clsInputStream != null) {
                                        }
                                        throw th;
                                    }
                                }
                                if (request != null || (myDrawable = getDefaultImageForContact(context.getResources(), request, id)) == null) {
                                    if (phones != null) {
                                        phones.close();
                                    }
                                    if (cursor3 != null) {
                                        cursor3.close();
                                    }
                                    if (0 != 0) {
                                        return null;
                                    }
                                    try {
                                        clsInputStream2.close();
                                        return null;
                                    } catch (IOException e7) {
                                        e7.printStackTrace();
                                        return null;
                                    }
                                } else {
                                    int dpInpx2 = Util.dpTopx(context, (int) context.getResources().getDimension(17106259));
                                    myDrawable.setBounds(0, 0, dpInpx2, dpInpx2);
                                    Bitmap bm2 = Util.drawableToBitmap(myDrawable);
                                    if (phones != null) {
                                        phones.close();
                                    }
                                    if (cursor3 != null) {
                                        cursor3.close();
                                    }
                                    if (0 != 0) {
                                        try {
                                            clsInputStream2.close();
                                        } catch (IOException e8) {
                                            e8.printStackTrace();
                                        }
                                    }
                                    return bm2;
                                }
                            } catch (Exception e9) {
                                e = e9;
                                e.printStackTrace();
                                if (phones != null) {
                                }
                                if (cursor3 != null) {
                                }
                                if (0 == 0) {
                                }
                            } catch (Throwable th4) {
                                clsInputStream = null;
                                cursor = cursor3;
                                cursor2 = phones;
                                th = th4;
                                if (cursor2 != null) {
                                }
                                if (cursor != null) {
                                }
                                if (clsInputStream != null) {
                                }
                                throw th;
                            }
                        }
                    } catch (Exception e10) {
                        e = e10;
                        e.printStackTrace();
                        if (phones != null) {
                        }
                        if (cursor3 != null) {
                        }
                        if (0 == 0) {
                        }
                    } catch (Throwable th5) {
                        clsInputStream = null;
                        cursor = cursor3;
                        cursor2 = phones;
                        th = th5;
                        if (cursor2 != null) {
                        }
                        if (cursor != null) {
                        }
                        if (clsInputStream != null) {
                        }
                        throw th;
                    }
                }
                if (phones != null) {
                }
                if (request != null) {
                }
                if (phones != null) {
                }
                if (cursor3 != null) {
                }
                if (0 != 0) {
                }
            } else {
                if (0 != 0) {
                    phones.close();
                }
                if (0 != 0) {
                    cursor3.close();
                }
                if (0 == 0) {
                    return null;
                }
                try {
                    clsInputStream2.close();
                    return null;
                } catch (IOException e11) {
                    e11.printStackTrace();
                    return null;
                }
            }
        } catch (Exception e12) {
            e = e12;
            e.printStackTrace();
            if (phones != null) {
            }
            if (cursor3 != null) {
            }
            if (0 == 0) {
            }
        } catch (Throwable th6) {
            clsInputStream = null;
            cursor = null;
            cursor2 = null;
            th = th6;
            if (cursor2 != null) {
            }
            if (cursor != null) {
            }
            if (clsInputStream != null) {
            }
            throw th;
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:10:0x0032, code lost:
        if (r1 != null) goto L_0x0034;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:11:0x0034, code lost:
        r1.close();
     */
    /* JADX WARNING: Code restructure failed: missing block: B:16:0x003e, code lost:
        if (0 == 0) goto L_0x0041;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:17:0x0041, code lost:
        return r2;
     */
    public static String getContactName(Context context, String number) {
        Cursor phones = null;
        String contactName = null;
        try {
            ContentResolver cr = context.getContentResolver();
            if (!(cr == null || (phones = cr.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)), new String[]{"display_name"}, null, null, null)) == null || !phones.moveToNext())) {
                contactName = phones.getString(phones.getColumnIndex("display_name"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } catch (Throwable th) {
            if (0 != 0) {
                phones.close();
            }
            throw th;
        }
    }

    /* JADX WARNING: Code restructure failed: missing block: B:16:0x007f, code lost:
        if (0 != 0) goto L_0x0070;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:17:0x0082, code lost:
        return r0;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:8:0x006e, code lost:
        if (r2 != null) goto L_0x0070;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:9:0x0070, code lost:
        r2.recycle();
     */
    public static Bitmap cropIcon(Context context, int sizeDimen, Bitmap source) {
        Bitmap resultIcon = null;
        Bitmap mask = null;
        Bitmap scaledSource = null;
        int size = (int) context.getResources().getDimension(sizeDimen);
        try {
            Bitmap mask2 = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas maskCanvas = new Canvas(mask2);
            Paint maskPaint = new Paint();
            maskPaint.setAntiAlias(true);
            maskPaint.setColor(-328966);
            maskCanvas.drawCircle((float) (size / 2), (float) (size / 2), (float) (size / 2), maskPaint);
            if (!(mask2 == null || source == null)) {
                scaledSource = Bitmap.createScaledBitmap(source.copy(Bitmap.Config.ARGB_8888, true), size, size, true);
                resultIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(resultIcon);
                canvas.drawBitmap(scaledSource, DefaultImageRequest.OFFSET_DEFAULT, DefaultImageRequest.OFFSET_DEFAULT, (Paint) null);
                Paint paint = new Paint();
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                canvas.drawBitmap(mask2, DefaultImageRequest.OFFSET_DEFAULT, DefaultImageRequest.OFFSET_DEFAULT, paint);
                paint.setXfermode(null);
            }
            if (mask2 != null) {
                mask2.recycle();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (0 != 0) {
                mask.recycle();
            }
        } catch (Throwable th) {
            if (0 != 0) {
                mask.recycle();
            }
            if (0 != 0) {
                scaledSource.recycle();
            }
            throw th;
        }
    }

    public static boolean isHorizentalDisplay(Context context) {
        if (context.getResources().getConfiguration().orientation == 2) {
            return true;
        }
        return false;
    }

    public static int getValueFromAttr(Context context, int[] attrs) {
        int valueFromAttr = 0;
        try {
            TypedArray a = context.obtainStyledAttributes(new TypedValue().data, attrs);
            valueFromAttr = a.getDimensionPixelSize(0, -1);
            a.recycle();
            return valueFromAttr;
        } catch (Resources.NotFoundException e) {
            e.printStackTrace();
            return valueFromAttr;
        }
    }

    public static Drawable getDefaultImageForContact(Resources resources, DefaultImageRequest defaultImageRequest, long contactId) {
        return null;
    }

    public static Drawable getDefaultAvatar(Resources res, boolean hires, boolean darkTheme, boolean isCircular, long contactId) {
        int resId;
        int i = (int) (contactId % 5);
        if (!hires || !darkTheme) {
            resId = 17302193;
        } else {
            resId = 17302192;
        }
        Bitmap image = null;
        if (0 == 0) {
            image = BitmapFactory.decodeResource(res, resId);
        }
        int color = res.getColor(Util.getDefaultPhotoBackgroundColor(contactId));
        StrokeRoundedBitmapDrawable drawable = new StrokeRoundedBitmapDrawable(res, image);
        drawable.setAntiAlias(true);
        drawable.setColorFilter(color, PorterDuff.Mode.DST_OVER);
        if (isCircular) {
            drawable.setCornerRadius(Util.getCornerRadius(res, image.getHeight()));
        }
        return drawable;
    }
}
