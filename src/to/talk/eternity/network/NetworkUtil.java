package to.talk.eternity.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class NetworkUtil
{
    private static final String LOGTAG = "n/w Util";
    private static SparseArray<NetworkConnectionType> mNetworkConnectionTypeMap;
    private static SparseArray<MobileNetworkConnectionType> mMobileNetworkConnectionTypeMap;

    static {
        initializeMobileNetworkConnectionTypeMap();
        initializeNetworkConnectionTypeMap();
    }

    public static ArrayList<String> getDNSServers()
    {
        ArrayList<String> servers = new ArrayList<String>(4);
        try {
            Class<?> SystemProperties = Class.forName("android.os.SystemProperties");
            Method method = SystemProperties.getMethod("get", new Class[]{String.class});
            for (String name : new String[]{"net.dns1", "net.dns2", "net.dns3", "net.dns4",}) {
                String value = (String) method.invoke(null, name);
                if (value != null && !value.isEmpty() && !servers.contains(value)) {
                    servers.add(value);
                }
            }
        } catch (ClassNotFoundException e) {
            Log.d(LOGTAG, "DNSINFO threw:", e);
        } catch (NoSuchMethodException e) {
            Log.d(LOGTAG, "DNSINFO threw:", e);
        } catch (InvocationTargetException e) {
            Log.d(LOGTAG, "DNSINFO threw:", e);
        } catch (IllegalAccessException e) {
            Log.d(LOGTAG, "DNSINFO threw:", e);
        }
        return servers;
    }


    public static String getProxyPort()
    {
        return System.getProperty("http.proxyPort");
    }

    public static String getProxyHost()
    {
        return System.getProperty("http.proxyHost");
    }

    private static void initializeNetworkConnectionTypeMap()
    {
        mNetworkConnectionTypeMap = new SparseArray<NetworkConnectionType>();
        mNetworkConnectionTypeMap
            .put(ConnectivityManager.TYPE_BLUETOOTH, NetworkConnectionType.OTHER);
        mNetworkConnectionTypeMap.put(ConnectivityManager.TYPE_DUMMY, NetworkConnectionType.OTHER);
        mNetworkConnectionTypeMap
            .put(ConnectivityManager.TYPE_ETHERNET, NetworkConnectionType.OTHER);
        mNetworkConnectionTypeMap.put(ConnectivityManager.TYPE_WIFI, NetworkConnectionType.WIFI);
        mNetworkConnectionTypeMap.put(ConnectivityManager.TYPE_WIMAX, NetworkConnectionType.WIFI);
        mNetworkConnectionTypeMap
            .put(ConnectivityManager.TYPE_MOBILE, NetworkConnectionType.MOBILE);
        mNetworkConnectionTypeMap
            .put(ConnectivityManager.TYPE_MOBILE_DUN, NetworkConnectionType.MOBILE);
        mNetworkConnectionTypeMap
            .put(ConnectivityManager.TYPE_MOBILE_HIPRI, NetworkConnectionType.MOBILE);
        mNetworkConnectionTypeMap
            .put(ConnectivityManager.TYPE_MOBILE_MMS, NetworkConnectionType.MOBILE);
        mNetworkConnectionTypeMap
            .put(ConnectivityManager.TYPE_MOBILE_SUPL, NetworkConnectionType.MOBILE);
    }

    private static void initializeMobileNetworkConnectionTypeMap()
    {
        mMobileNetworkConnectionTypeMap = new SparseArray<MobileNetworkConnectionType>();
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_IDEN, MobileNetworkConnectionType.TWO_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_GPRS, MobileNetworkConnectionType.TWO_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_EDGE, MobileNetworkConnectionType.TWO_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_CDMA, MobileNetworkConnectionType.TWO_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_1xRTT, MobileNetworkConnectionType.THREE_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_EVDO_0, MobileNetworkConnectionType.THREE_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_EVDO_A, MobileNetworkConnectionType.THREE_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_EVDO_B, MobileNetworkConnectionType.THREE_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_UMTS, MobileNetworkConnectionType.THREE_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_EHRPD, MobileNetworkConnectionType.THREE_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_HSDPA, MobileNetworkConnectionType.THREE_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_HSUPA, MobileNetworkConnectionType.THREE_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_HSPA, MobileNetworkConnectionType.THREE_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_HSPAP, MobileNetworkConnectionType.FOUR_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_LTE, MobileNetworkConnectionType.FOUR_G);
        mMobileNetworkConnectionTypeMap
            .put(TelephonyManager.NETWORK_TYPE_UNKNOWN, MobileNetworkConnectionType.OTHER);
    }

    public static CustomNetworkInfo getNetworkInfo(Context context)
    {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            final NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                int networkType = activeNetworkInfo.getType();
                NetworkConnectionType customNetworkType = mNetworkConnectionTypeMap
                    .get(networkType);
                if (customNetworkType == null) {
                    customNetworkType = NetworkConnectionType.OTHER;
                }
                CustomNetworkInfo networkInfo = new CustomNetworkInfo(customNetworkType);
                networkInfo.setDetailedState(activeNetworkInfo.getDetailedState());
                final int mobileNetworkType = ((TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE)).getNetworkType();
                MobileNetworkConnectionType customMobileNetworkType = mMobileNetworkConnectionTypeMap
                    .get(mobileNetworkType);
                if (customNetworkType == NetworkConnectionType.MOBILE) {
                    if (customMobileNetworkType == null) {
                        customMobileNetworkType = MobileNetworkConnectionType.OTHER;
                    }
                }
                networkInfo.setMobileNetworkConnectionType(customMobileNetworkType);
                return networkInfo;
            }
        }
        return new CustomNetworkInfo(NetworkConnectionType.OTHER);
    }


}
