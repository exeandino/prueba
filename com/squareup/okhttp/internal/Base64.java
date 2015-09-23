package com.squareup.okhttp.internal;

import android.support.v4.view.MotionEventCompat;
import android.support.v4.widget.DrawerLayout;
import ch.ti8m.phonegap.plugins.DocumentHandler;
import java.io.UnsupportedEncodingException;

public final class Base64 {
    private static final byte[] MAP;

    private Base64() {
    }

    public static byte[] decode(byte[] in) {
        return decode(in, in.length);
    }

    public static byte[] decode(byte[] in, int len) {
        int length = (len / 4) * 3;
        if (length == 0) {
            return Util.EMPTY_BYTE_ARRAY;
        }
        int outIndex;
        byte[] result;
        byte[] out = new byte[length];
        int pad = 0;
        while (true) {
            byte chr = in[len - 1];
            if (!(chr == 10 || chr == 13 || chr == 32 || chr == 9)) {
                if (chr != 61) {
                    break;
                }
                pad++;
            }
            len--;
        }
        int inIndex = 0;
        int quantum = 0;
        int i = 0;
        int outIndex2 = 0;
        while (i < len) {
            chr = in[i];
            if (chr == 10 || chr == 13 || chr == 32) {
                outIndex = outIndex2;
            } else if (chr == 9) {
                outIndex = outIndex2;
            } else {
                int bits;
                if (chr >= 65 && chr <= 90) {
                    bits = chr - 65;
                } else if (chr >= 97 && chr <= 122) {
                    bits = chr - 71;
                } else if (chr >= 48 && chr <= 57) {
                    bits = chr + 4;
                } else if (chr == 43) {
                    bits = 62;
                } else if (chr != 47) {
                    return null;
                } else {
                    bits = 63;
                }
                quantum = (quantum << 6) | ((byte) bits);
                if (inIndex % 4 == 3) {
                    outIndex = outIndex2 + 1;
                    out[outIndex2] = (byte) (quantum >> 16);
                    outIndex2 = outIndex + 1;
                    out[outIndex] = (byte) (quantum >> 8);
                    outIndex = outIndex2 + 1;
                    out[outIndex2] = (byte) quantum;
                } else {
                    outIndex = outIndex2;
                }
                inIndex++;
            }
            i++;
            outIndex2 = outIndex;
        }
        if (pad > 0) {
            quantum <<= pad * 6;
            outIndex = outIndex2 + 1;
            out[outIndex2] = (byte) (quantum >> 16);
            if (pad == 1) {
                outIndex2 = outIndex + 1;
                out[outIndex] = (byte) (quantum >> 8);
            }
            result = new byte[outIndex];
            System.arraycopy(out, 0, result, 0, outIndex);
            return result;
        }
        outIndex = outIndex2;
        result = new byte[outIndex];
        System.arraycopy(out, 0, result, 0, outIndex);
        return result;
    }

    static {
        MAP = new byte[]{(byte) 65, (byte) 66, (byte) 67, (byte) 68, (byte) 69, (byte) 70, (byte) 71, (byte) 72, (byte) 73, (byte) 74, (byte) 75, (byte) 76, (byte) 77, (byte) 78, (byte) 79, (byte) 80, (byte) 81, (byte) 82, (byte) 83, (byte) 84, (byte) 85, (byte) 86, (byte) 87, (byte) 88, (byte) 89, (byte) 90, (byte) 97, (byte) 98, (byte) 99, (byte) 100, (byte) 101, (byte) 102, (byte) 103, (byte) 104, (byte) 105, (byte) 106, (byte) 107, (byte) 108, (byte) 109, (byte) 110, (byte) 111, (byte) 112, (byte) 113, (byte) 114, (byte) 115, (byte) 116, (byte) 117, (byte) 118, (byte) 119, (byte) 120, (byte) 121, (byte) 122, (byte) 48, (byte) 49, (byte) 50, (byte) 51, (byte) 52, (byte) 53, (byte) 54, (byte) 55, (byte) 56, (byte) 57, (byte) 43, (byte) 47};
    }

    public static String encode(byte[] in) {
        int i;
        byte[] out = new byte[(((in.length + 2) * 4) / 3)];
        int end = in.length - (in.length % 3);
        int index = 0;
        for (int i2 = 0; i2 < end; i2 += 3) {
            i = index + 1;
            out[index] = MAP[(in[i2] & MotionEventCompat.ACTION_MASK) >> 2];
            index = i + 1;
            out[i] = MAP[((in[i2] & 3) << 4) | ((in[i2 + 1] & MotionEventCompat.ACTION_MASK) >> 4)];
            i = index + 1;
            out[index] = MAP[((in[i2 + 1] & 15) << 2) | ((in[i2 + 2] & MotionEventCompat.ACTION_MASK) >> 6)];
            index = i + 1;
            out[i] = MAP[in[i2 + 2] & 63];
        }
        switch (in.length % 3) {
            case DocumentHandler.ERROR_UNKNOWN_ERROR /*1*/:
                i = index + 1;
                out[index] = MAP[(in[end] & MotionEventCompat.ACTION_MASK) >> 2];
                index = i + 1;
                out[i] = MAP[(in[end] & 3) << 4];
                i = index + 1;
                out[index] = (byte) 61;
                index = i + 1;
                out[i] = (byte) 61;
                i = index;
                break;
            case DrawerLayout.STATE_SETTLING /*2*/:
                i = index + 1;
                out[index] = MAP[(in[end] & MotionEventCompat.ACTION_MASK) >> 2];
                index = i + 1;
                out[i] = MAP[((in[end] & 3) << 4) | ((in[end + 1] & MotionEventCompat.ACTION_MASK) >> 4)];
                i = index + 1;
                out[index] = MAP[(in[end + 1] & 15) << 2];
                index = i + 1;
                out[i] = (byte) 61;
                break;
        }
        i = index;
        try {
            return new String(out, 0, i, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
