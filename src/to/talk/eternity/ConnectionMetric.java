package to.talk.eternity;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

public class ConnectionMetric {
    public static final String LOGTAG = "ConnectionMetric";
    private long dnsResolutionTime = 0;
    private long tcpHandshakeTime = 0;
    private long sslHandshakeTime = 0;
    private long responseTime = 0;
    private InetSocketAddress resolvedAddress = null;
    private String response = null;
    private String request = null;
    private boolean isSecure = false;
    private State state = State.INIT;
    private Proxy proxy;

    public State getState() {
        return state;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    public void startDnsResolutionTimer() {
        state = State.DNS_REQUESTED;
        dnsResolutionTime = System.nanoTime();
    }

    public long finishDnsResolutionTimer(InetSocketAddress address) {
        state = State.DNS_RESOLVED;
        dnsResolutionTime = System.nanoTime() - dnsResolutionTime;
        resolvedAddress = address;
        return dnsResolutionTime;
    }

    public void startTcpHandshakeTimer() {
        state = State.TCP_HANDSHAKE_REQUESTED;
        tcpHandshakeTime = System.nanoTime();
    }

    public long finishTcpHandshakeTimer() {
        state= State.TCP_HANDSHAKE_DONE;
        tcpHandshakeTime = System.nanoTime() - tcpHandshakeTime;
        return tcpHandshakeTime;
    }

    public void startSSLHandshakeTimer() {
        state = State.SSL_HANDSHAKE_RQUESTED;
        isSecure = true;
        sslHandshakeTime = System.nanoTime();
    }

    public long finishSSLHandshakeTimer() {
        state = State.SSL_HANDSHAKE_DONE;
        sslHandshakeTime = System.nanoTime() - sslHandshakeTime;
        return sslHandshakeTime;
    }

    public void startResponseTimer(String sentRequest) {
        state = State.REQ_SENT;
        responseTime = System.nanoTime();
        request = sentRequest;
    }

    public long finishResponseTimer(String rcvdResponse) {
        state = State.RESP_RECEIVED;
        responseTime = System.nanoTime() - responseTime;
        response = rcvdResponse;
        return responseTime;
    }

    public long getDnsResolutionTime(TimeUnit unit) {
        return unit.convert(dnsResolutionTime, TimeUnit.NANOSECONDS);
    }

    public long getTcpHandshakeTime(TimeUnit unit) {
        return unit.convert(tcpHandshakeTime, TimeUnit.NANOSECONDS);
    }

    public long getSslHandshakeTime(TimeUnit unit) {
        return unit.convert(sslHandshakeTime, TimeUnit.NANOSECONDS);
    }

    public long getResponseTime(TimeUnit unit) {
        return unit.convert(responseTime, TimeUnit.NANOSECONDS);
    }

    public InetSocketAddress getResolvedAddress() {
        return resolvedAddress;
    }

    public String getResponse() {
        return response;
    }

    public String getRequest() {
        return request;
    }

    public boolean isSecure() {
        return isSecure;
    }

    enum State {
        INIT, DNS_REQUESTED, DNS_RESOLVED, TCP_HANDSHAKE_REQUESTED, TCP_HANDSHAKE_DONE, SSL_HANDSHAKE_RQUESTED,
        SSL_HANDSHAKE_DONE, REQ_SENT, RESP_RECEIVED
    }
}
