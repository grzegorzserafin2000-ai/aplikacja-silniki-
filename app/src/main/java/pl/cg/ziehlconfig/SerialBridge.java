package pl.cg.ziehlconfig;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import java.io.IOException;
import java.util.List;

/** Serial transport, only ever issuing explicitly requested MODBUS commands. */
public final class SerialBridge implements AutoCloseable {
    public static final class Link {
        public final int baud, stop, parity;
        public final String label;
        public Link(int baud, int parity, int stop, String label) {
            this.baud=baud; this.parity=parity; this.stop=stop; this.label=label;
        }
    }
    // Standard ZIEHL-ABEGG values plus alternatives used on previously configured devices.
    // Read-only scan; wider search does not authorize a write.
    public static final Link[] COMMON_LINKS = {
        new Link(19200,UsbSerialPort.PARITY_EVEN,UsbSerialPort.STOPBITS_1,"19200 / Even / 1"),
        new Link(9600,UsbSerialPort.PARITY_NONE,UsbSerialPort.STOPBITS_2,"9600 / None / 2"),
        new Link(9600,UsbSerialPort.PARITY_EVEN,UsbSerialPort.STOPBITS_1,"9600 / Even / 1"),
        new Link(19200,UsbSerialPort.PARITY_NONE,UsbSerialPort.STOPBITS_2,"19200 / None / 2"),
        new Link(19200,UsbSerialPort.PARITY_NONE,UsbSerialPort.STOPBITS_1,"19200 / None / 1"),
        new Link(9600,UsbSerialPort.PARITY_NONE,UsbSerialPort.STOPBITS_1,"9600 / None / 1"),
        new Link(4800,UsbSerialPort.PARITY_EVEN,UsbSerialPort.STOPBITS_1,"4800 / Even / 1"),
        new Link(38400,UsbSerialPort.PARITY_EVEN,UsbSerialPort.STOPBITS_1,"38400 / Even / 1"),
        new Link(38400,UsbSerialPort.PARITY_NONE,UsbSerialPort.STOPBITS_2,"38400 / None / 2"),
        new Link(4800,UsbSerialPort.PARITY_NONE,UsbSerialPort.STOPBITS_2,"4800 / None / 2"),
        new Link(19200,UsbSerialPort.PARITY_ODD,UsbSerialPort.STOPBITS_1,"19200 / Odd / 1"),
        new Link(9600,UsbSerialPort.PARITY_ODD,UsbSerialPort.STOPBITS_1,"9600 / Odd / 1"),
        new Link(4800,UsbSerialPort.PARITY_NONE,UsbSerialPort.STOPBITS_1,"4800 / None / 1"),
        new Link(38400,UsbSerialPort.PARITY_NONE,UsbSerialPort.STOPBITS_1,"38400 / None / 1"),
        new Link(4800,UsbSerialPort.PARITY_ODD,UsbSerialPort.STOPBITS_1,"4800 / Odd / 1"),
        new Link(38400,UsbSerialPort.PARITY_ODD,UsbSerialPort.STOPBITS_1,"38400 / Odd / 1")
    };

    private final UsbManager manager;
    private final UsbSerialDriver driver;
    private UsbSerialPort port;
    private UsbDeviceConnection connection;
    private RtuSession session;
    private Link configuredLink;
    private final StringBuilder diagnostic=new StringBuilder();
    private boolean truncated;
    private void trace(String line) {
        diagnostic.append(android.os.SystemClock.elapsedRealtime()).append(" ").append(line).append('\n');
        if(diagnostic.length()>48000) { diagnostic.delete(0,diagnostic.length()-40000);truncated=true; }
    }
    public String diagnostics() { return (truncated?"[Wcześniejsze wpisy pominięte]\n":"")+diagnostic; }

    public SerialBridge(UsbManager manager, UsbSerialDriver driver) {
        this.manager=manager; this.driver=driver;
    }

    public void open() throws IOException {
        connection = manager.openDevice(driver.getDevice());
        if (connection == null) throw new IOException("Brak zgody dostępu do USB");
        try {
            List<UsbSerialPort> ports = driver.getPorts();
            if (ports.isEmpty()) throw new IOException("Brak portu szeregowego");
            port = ports.get(0);
            port.open(connection);
            session=new RtuSession(new RtuSession.Wire() {
                public void clearReceive() throws IOException {
                    try { port.purgeHwBuffers(false,true); }
                    catch(UnsupportedOperationException ignored) { }
                }
                public void write(byte[] data) throws IOException { port.write(data,1500); }
                public int read(byte[] data,int timeout) throws IOException { return port.read(data,timeout); }
            }, new RtuSession.Clock() {
                public long now() { return android.os.SystemClock.elapsedRealtime(); }
                public void sleep(long millis) { android.os.SystemClock.sleep(millis); }
            }, this::trace);
        } catch (Exception e) {
            close();
            if (e instanceof IOException) throw (IOException)e;
            throw new IOException(e);
        }
    }

    public void configure(Link link) throws IOException {
        if (port == null) throw new IOException("Port USB nie jest otwarty");
        if(configuredLink!=null && configuredLink.baud==link.baud &&
                configuredLink.parity==link.parity && configuredLink.stop==link.stop)return;
        port.setParameters(link.baud, 8, link.stop, link.parity);
        configuredLink=link;
        session.baud(link.baud);
        trace("LINK "+link.label);
    }

    /** Single query: retries / exceptions are handled by caller, no writes for detection. */
    public ModbusRtu.Reply transaction(int address, int function, int register, int value, int timeoutMs) throws IOException {
        if (port == null) throw new IOException("Brak portu USB");
        return session.transaction(address,function,register,value,timeoutMs);
    }

    @Override public void close() {
        configuredLink=null;
        if (port != null) { try { port.close(); } catch (IOException ignored) {} port = null; }
        if (connection != null) { connection.close(); connection = null; }
    }
}
