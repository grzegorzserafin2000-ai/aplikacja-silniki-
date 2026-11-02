package pl.cg.ziehlconfig;

import java.io.IOException;
import java.util.function.Consumer;

/** One request at a time, with an RTU silent interval. Never retries writes. */
public final class RtuSession {
    public interface Wire {
        void clearReceive() throws IOException;
        void write(byte[] data) throws IOException;
        int read(byte[] data, int timeoutMs) throws IOException;
    }
    public interface Clock { long now(); void sleep(long millis); }
    private final Wire wire;
    private final Clock clock;
    private final Consumer<String> trace;
    private long lastActivity=-1;
    private int baud=9600;
    public RtuSession(Wire wire, Clock clock, Consumer<String> trace) {
        this.wire=wire; this.clock=clock; this.trace=trace;
    }
    public void baud(int baud) { this.baud=baud; }
    public static int silenceMs(int baud) {
        if(baud<=0) throw new IllegalArgumentException("Baud must be positive");
        return Math.max(4,(int)Math.ceil(3.5*11*1000/baud));
    }
    public static String hex(byte[] bytes,int size) {
        StringBuilder s=new StringBuilder();
        for(int i=0;i<size;i++) { if(i>0)s.append(' ');s.append(String.format(java.util.Locale.US,"%02X",bytes[i]&255)); }
        return s.toString();
    }
    public ModbusRtu.Reply transaction(int slave,int function,int register,int value,int timeout) throws IOException {
        byte[] request=function==3?ModbusRtu.readHolding(slave,register):
                function==4?ModbusRtu.readInput(slave,register):
                function==6?ModbusRtu.writeRegister(slave,register,value):null;
        if(request==null || timeout<=0)throw new IllegalArgumentException("Invalid MODBUS request");
        if(lastActivity>=0) {
            long wait=silenceMs(baud)-(clock.now()-lastActivity);
            if(wait>0)clock.sleep(wait);
        }
        wire.clearReceive();
        trace.accept("TX "+hex(request,request.length));
        wire.write(request);
        // USB bulk completion may precede the last byte leaving the UART.
        // Conservative 11-bit character time also prevents a local echo from
        // permitting a second request while the first one is still on the wire.
        lastActivity=clock.now()+(long)Math.ceil(request.length*11.0*1000/baud);
        byte[] data=new byte[256],chunk=new byte[64];int count=0;
        long until=clock.now()+timeout;
        while(clock.now()<until) {
            int remaining=(int)(until-clock.now());
            if(remaining<=0)break;
            int n=wire.read(chunk,Math.min(remaining,45));
            if(n<=0)continue;
            lastActivity=Math.max(lastActivity,clock.now());
            trace.accept("RX "+hex(chunk,n));
            if(count+n>data.length) {
                int drop=count+n-data.length;
                System.arraycopy(data,drop,data,0,count-drop);count-=drop;
            }
            System.arraycopy(chunk,0,data,count,n);count+=n;
            ModbusRtu.Reply reply=ModbusRtu.parse(data,count,slave,function,register,value);
            if(reply!=null) {
                trace.accept(reply.successful()?"REPLY OK (FC06 requires readback; echo is indistinguishable)":"EXCEPTION "+reply.exception);
                return reply;
            }
        }
        trace.accept("TIMEOUT "+timeout+" ms");return null;
    }
}
