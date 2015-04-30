package to.talk.eternity.network;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class CustomNetworkInfo
{
    private NetworkConnectionType networkConnectionType;
    private MobileNetworkConnectionType mobileNetworkConnectionType;
    private android.net.NetworkInfo.DetailedState _detailedState = android.net.NetworkInfo.DetailedState.DISCONNECTED;

    public CustomNetworkInfo(NetworkConnectionType networkConnectionType)
    {
        this.networkConnectionType = networkConnectionType;
    }

    public CustomNetworkInfo(NetworkConnectionType networkConnectionType,
                             MobileNetworkConnectionType mobileNetworkConnectionType)
    {
        this.networkConnectionType = networkConnectionType;
        this.mobileNetworkConnectionType = mobileNetworkConnectionType;
    }

    public android.net.NetworkInfo.DetailedState getDetailedState()
    {
        return _detailedState;
    }

    public void setDetailedState(android.net.NetworkInfo.DetailedState detailedState)
    {
        _detailedState = detailedState;
    }

    public NetworkConnectionType getNetworkConnectionType()
    {
        return networkConnectionType;
    }

    public void setNetworkConnectionType(NetworkConnectionType networkConnectionType)
    {
        this.networkConnectionType = networkConnectionType;
    }

    public MobileNetworkConnectionType getMobileNetworkConnectionType()
    {
        return mobileNetworkConnectionType;
    }

    public void setMobileNetworkConnectionType(
        MobileNetworkConnectionType mobileNetworkConnectionType)
    {
        this.mobileNetworkConnectionType = mobileNetworkConnectionType;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                        builder.append("\n\t").append(networkInterface.toString());
                    }
                }
            }
        } catch (SocketException e) {
        }

        return "NetworkInfo:" +
               "networkConnectionType=" + networkConnectionType + '\n' +
               ", mobileNetworkConnectionType=" + mobileNetworkConnectionType + '\n' +
               ", _detailedState=" + _detailedState + '\n' +
               ", _n/w I/F=" + builder.toString() +
               '}';
    }
}