package pl.cg.ziehlconfig;

import java.io.IOException;
import java.util.*;

public final class CommunicationTest {
    static int checks;
    static void check(boolean ok,String message) { checks++;if(!ok)throw new AssertionError(message); }
    static final int OLD=ModbusRtu.configValue(231,9600,"8N2");
    static final int TARGET=ModbusRtu.configValue(230,9600,"8N2");
    static class Clock implements RtuSession.Clock {
        long time;
        public long now(){return time;}
        public void sleep(long ms){time+=ms;}
    }
    static class Motor implements CommunicationProgrammer.Transport {
        final Clock clock;
        int product=0x0100,word=OLD,newWord=OLD;
        long storedAt=Long.MAX_VALUE,activatedAt=Long.MAX_VALUE;
        int storeDelay=0,activationDelay=0;
        boolean ack=true,rejectWrite=false,pinAck=true,ignoreWrite=false,ignoreActivation=false,immediateOnStore=false;
        int targetProduct=-1;
        int speed;String mode;
        boolean aliasReads=false;
        int liveAddress=231,liveBaud=9600;String liveMode="8N2";
        long pinAt=-1,h3At=-1;
        final List<String> writeModes=new ArrayList<>();
        final List<String> writes=new ArrayList<>();
        final List<Integer> writeAddresses=new ArrayList<>();
        Motor(Clock c){clock=c;}
        public void configure(int speed,String mode){this.speed=speed;this.mode=mode;}
        public ModbusRtu.Reply transaction(int slave,int fn,int reg,int value,int timeout) {
            clock.sleep(10);
            if(clock.time>=storedAt)word=newWord;
            CommunicationProgrammer.Settings target=CommunicationProgrammer.Settings.stored(newWord);
            boolean activated=clock.time>=activatedAt;
            int active=activated?target.address:liveAddress;
            int activeBaud=activated?target.baud:liveBaud;
            String activeMode=activated?target.mode:liveMode;
            boolean alias=aliasReads && !activated && mode.equals("8E1") && activeMode.equals("8N2");
            if(slave!=active || speed!=activeBaud || (!mode.equals(activeMode) && !alias)) {clock.sleep(timeout);return null;}
            if(fn==6) {
                // Hardware regression: wrong framing may allow reads/PIN echo yet reject h3.
                if(alias && reg==3){clock.sleep(timeout);return null;}
                writeModes.add(mode);
                if(reg==0 && value==1234)pinAt=clock.time;
                if(reg==3)h3At=clock.time;
                writes.add(reg+":"+value);writeAddresses.add(slave);
                if(reg==0 && value==1234 && !pinAck)return null;
                if(reg==3 && rejectWrite)return new ModbusRtu.Reply(slave,0x86,-1,3);
                if(reg==3 && !ignoreWrite){newWord=value;storedAt=clock.time+storeDelay;if(immediateOnStore)activatedAt=storedAt;}
                if(reg==0 && value==3698 && !ignoreActivation)activatedAt=clock.time+activationDelay;
                return ack?new ModbusRtu.Reply(slave,fn,value,0):
                        reg==0 && value==1234?new ModbusRtu.Reply(slave,fn,value,0):null;
            }
            return new ModbusRtu.Reply(slave,fn,fn==4?(activated && targetProduct>=0?targetProduct:product):word,0);
        }
    }
    static CommunicationProgrammer.Result program(Motor motor,int product,int previous,int target) {
        return new CommunicationProgrammer(motor,motor.clock).run(
                new CommunicationProgrammer.Settings(231,9600,"8N2"),product,previous,target);
    }
    static class Wire implements RtuSession.Wire {
        final Clock clock;final Queue<byte[]> chunks=new ArrayDeque<>();
        int writes;List<Long> times=new ArrayList<>();
        Wire(Clock c){clock=c;}
        public void clearReceive(){}
        public void write(byte[] data){writes++;times.add(clock.time);}
        public int read(byte[] data,int timeout){
            if(chunks.isEmpty()){clock.sleep(timeout);return 0;}
            byte[] b=chunks.remove();clock.sleep(1);System.arraycopy(b,0,data,0,b.length);return b.length;
        }
    }
    static byte[] response(int slave,int value) {
        byte[] b={(byte)slave,3,2,(byte)(value>>8),(byte)value,0,0};
        int crc=ModbusRtu.crc16(b,5);b[5]=(byte)crc;b[6]=(byte)(crc>>8);return b;
    }
    public static void main(String[] args) throws IOException {
        Clock c=new Clock();Motor m=new Motor(c);
        m.storeDelay=1700;m.activationDelay=9000;m.ack=false;
        CommunicationProgrammer.Result r=program(m,0x0100,OLD,TARGET);
        check(r.success,"Delayed h3 + lost ACK + 9s activation must confirm");
        check(m.writes.equals(Arrays.asList("0:1234","3:"+TARGET,"0:3698")),"No duplicate writes");
        check(c.time>=9000,"Activation must be polled beyond 260ms");
        check(m.writeAddresses.stream().allMatch(a->a==231),"Writes must use active address");

        c=new Clock();m=new Motor(c);m.word=TARGET;m.newWord=TARGET;m.activationDelay=3000;
        r=program(m,0x0100,TARGET,TARGET);
        check(r.success,"Stored 230 / active 231 must activate");
        check(m.writes.equals(Arrays.asList("0:3698")),"Pending state must not rewrite h3");
        check(m.writeAddresses.equals(Arrays.asList(231)),"Activation on live 231, not stored 230");

        c=new Clock();m=new Motor(c);r=program(m,0x0103,OLD,TARGET);
        check(!r.success && m.writes.isEmpty(),"Unknown model remains read-only");
        c=new Clock();m=new Motor(c);m.product=0x0101;r=program(m,0x0100,OLD,TARGET);
        check(!r.success && m.writes.isEmpty(),"Changed identity blocks writes");
        c=new Clock();m=new Motor(c);m.word=TARGET;r=program(m,0x0100,OLD,TARGET);
        check(!r.success && m.writes.isEmpty(),"Changed h3 blocks writes");
        c=new Clock();m=new Motor(c);m.pinAck=false;r=program(m,0x0100,OLD,TARGET);
        check(!r.success && m.writes.equals(Arrays.asList("0:1234")),"Missing PIN ACK blocks h3");
        c=new Clock();m=new Motor(c);m.rejectWrite=true;r=program(m,0x0100,OLD,TARGET);
        check(!r.success && m.writes.size()==2,"Rejected h3 must not activate");
        c=new Clock();m=new Motor(c);m.ignoreWrite=true;r=program(m,0x0100,OLD,TARGET);
        check(!r.success && m.writes.size()==2,"Echo/ACK without readback must not succeed or activate");
        c=new Clock();m=new Motor(c);m.ignoreActivation=true;r=program(m,0x0100,OLD,TARGET);
        check(!r.success && r.pending && m.writes.size()==3,"Stored but not active is pending, not success");
        c=new Clock();m=new Motor(c);r=program(m,0x0100,OLD,OLD);
        check(r.success && !r.attempted && m.writes.isEmpty(),"Already configured means no writes");
        c=new Clock();m=new Motor(c);r=program(m,0x0100,OLD,ModbusRtu.configValue(230,38400,"8N2"));
        check(!r.success && m.writes.isEmpty(),"Unsupported target baud blocks writes");

        c=new Clock();m=new Motor(c);
        m.liveAddress=247;m.liveBaud=19200;m.liveMode="8E1";
        m.word=ModbusRtu.configValue(247,19200,"8E1");m.newWord=m.word;
        m.activationDelay=5000;m.ack=false;
        r=new CommunicationProgrammer(m,c).run(new CommunicationProgrammer.Settings(247,19200,"8E1"),
                0x0100,m.word,ModbusRtu.configValue(231,9600,"8N2"));
        check(r.success,"Factory 247/19200/8E1 -> 231/9600/8N2");
        check(m.writeAddresses.stream().allMatch(a->a==247),"Factory activation uses old address and format");
        check(m.speed==9600 && m.mode.equals("8N2"),"Verification uses new serial format");
        c=new Clock();m=new Motor(c);m.activationDelay=2500;
        r=program(m,0x0100,OLD,ModbusRtu.configValue(231,19200,"8E1"));
        check(r.success,"Baud/parity change with same address");
        c=new Clock();m=new Motor(c);m.immediateOnStore=true;m.ack=false;
        r=program(m,0x0100,OLD,TARGET);
        check(r.success && m.writes.size()==2,"Immediate switch without h3 ACK requires no activation");
        c=new Clock();m=new Motor(c);m.targetProduct=0x0103;
        r=program(m,0x0100,OLD,TARGET);
        check(!r.success,"Different product on target link cannot confirm success");
        c=new Clock();m=new Motor(c);m.storeDelay=10000;
        r=program(m,0x0100,OLD,TARGET);
        check(!r.success && m.writes.size()==2,"Unconfirmed h3 after observation budget must not activate");
        c=new Clock();m=new Motor(c);m.aliasReads=true;
        r=new CommunicationProgrammer(m,c).run(new CommunicationProgrammer.Settings(231,9600,"8E1"),
                0x0100,OLD,TARGET);
        check(r.success,"Report regression: E1 reads work but N2 is required for h3 write");
        check(m.writeModes.stream().allMatch(format->format.equals("8N2")),"Use freshly verified h3 framing for writes");
        check(m.h3At-m.pinAt>=500,"PIN processing pause precedes h3 write");
        c=new Clock();m=new Motor(c);m.liveMode="8E1";
        r=new CommunicationProgrammer(m,c).run(new CommunicationProgrammer.Settings(231,9600,"8E1"),
                0x0100,OLD,TARGET);
        check(r.success && m.writeModes.stream().allMatch(format->format.equals("8E1")),
                "Unresponsive stored framing must never be chosen for writes");
        c=new Clock();m=new Motor(c);m.aliasReads=true;
        m.configure(9600,"8E1");
        check(m.transaction(231,6,3,TARGET,1100)==null && m.word==OLD,
                "Fixture reproduces unchanged h3 and missing FC06 reply with wrong framing");
        byte[] known={1,3,0,0,0,10};
        check(ModbusRtu.crc16(known,known.length)==0xCDC5,"Published MODBUS CRC vector");
        check(ModbusRtu.configValue(231,9600,"8N2")==0xE713,"CAREL bit layout");
        c=new Clock();Wire w=new Wire(c);List<String> trace=new ArrayList<>();
        RtuSession session=new RtuSession(w,c,trace::add);session.baud(4800);
        byte[] b=response(231,TARGET);
        w.chunks.add(Arrays.copyOfRange(b,0,3));w.chunks.add(Arrays.copyOfRange(b,3,7));
        ModbusRtu.Reply reply=session.transaction(231,3,3,0,100);
        check(reply!=null && reply.value==TARGET,"Fragmented response");
        long last=c.time;w.chunks.add(b);session.transaction(231,3,3,0,100);
        check(w.times.get(1)-last>=9,"3.5-character RTU silence at 4800");
        check(trace.stream().anyMatch(s->s.startsWith("TX ")) && trace.stream().anyMatch(s->s.startsWith("RX ")),"Raw trace available");
        byte[] corrupt=b.clone();corrupt[4]^=1;w.chunks.add(corrupt);
        check(session.transaction(231,3,3,0,100)==null,"Bad CRC rejected");
        int before=w.writes;
        check(session.transaction(231,6,3,TARGET,100)==null && w.writes==before+1,"FC06 timeout never retransmits");
        for(int i=0;i<6;i++)w.chunks.add(new byte[64]);w.chunks.add(b);
        check(session.transaction(231,3,3,0,100)!=null,"Long noise prefix retains trailing frame");
        check(ModbusRtu.parse(response(230,TARGET),7,231,3,3,0)==null,"Wrong slave rejected");
        byte[] wrong=ModbusRtu.writeRegister(231,4,TARGET);
        check(ModbusRtu.parse(wrong,8,231,6,3,TARGET)==null,"Wrong FC06 register rejected");
        System.out.println("PASS: "+checks+" communication assertions (simulated motor/serial clock)");
    }
}
