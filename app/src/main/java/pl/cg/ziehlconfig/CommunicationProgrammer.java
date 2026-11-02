package pl.cg.ziehlconfig;

import java.io.IOException;

/** Explicit single-write stages, followed by bounded read-only polling. */
public final class CommunicationProgrammer {
    public interface Transport {
        void configure(int baud,String mode) throws IOException;
        ModbusRtu.Reply transaction(int address,int function,int register,int value,int timeout) throws IOException;
    }
    public static final class Settings {
        public final int address,baud;
        public final String mode;
        public Settings(int address,int baud,String mode) {
            ModbusRtu.configValue(address,baud,mode);
            this.address=address;this.baud=baud;this.mode=mode;
        }
        public static Settings stored(int word) {
            if(!ModbusRtu.resemblesEcblueComParameter(word))throw new IllegalArgumentException("Niepoprawne h3");
            return new Settings((word>>>8)&255,ModbusRtu.decodeBaud(word),ModbusRtu.decodeMode(word));
        }
        public boolean same(Settings other) {
            return address==other.address && baud==other.baud && mode.equals(other.mode);
        }
    }
    public static final class Result {
        public final boolean success,pending,attempted;
        public final String message,details;
        public final int controlWord;
        Result(boolean success,boolean pending,boolean attempted,String message,String details) {
            this(success,pending,attempted,message,details,-1);
        }
        Result(boolean success,boolean pending,boolean attempted,String message,String details,int controlWord) {
            this.success=success;this.pending=pending;this.attempted=attempted;this.message=message;this.details=details;
            this.controlWord=controlWord;
        }
    }
    private final Transport io;
    private final RtuSession.Clock clock;
    private final StringBuilder log=new StringBuilder();
    private java.util.function.Consumer<String> progress=value->{};
    public void setProgressListener(java.util.function.Consumer<String> listener){progress=listener;}
    private void phase(String value){try{progress.accept(value);}catch(RuntimeException ignored){}}
    private boolean attempted;
    private int confirmedControlWord=-1;
    public CommunicationProgrammer(Transport io,RtuSession.Clock clock) { this.io=io;this.clock=clock; }
    private Result result(boolean success,boolean pending,String message) {
        phase(success?"✓ Odczyt kontrolny zakończony — ustawienia potwierdzone":"Nie potwierdzono pełnego kodowania — sprawdź raport");
        return new Result(success,pending,attempted,message,log.toString(),confirmedControlWord);
    }
    private ModbusRtu.Reply request(Settings link,int function,int register,int value,int timeout) throws IOException {
        io.configure(link.baud,link.mode);
        return io.transaction(link.address,function,register,value,timeout);
    }
    private int read(Settings link,int function,int register) {
        try {
            ModbusRtu.Reply reply=request(link,function,register,0,500);
            if(reply!=null && reply.successful())return reply.value;
        } catch(IOException e) { log.append("Odczyt: ").append(e.getMessage()).append('\n'); }
        return -1;
    }
    private boolean targetConfirmed(Settings target,int product,int word) {
        // A write echo / acknowledgement is never proof of a changed link.
        return read(target,4,1)==product && read(target,3,3)==word;
    }
    public static int withCm3(int h4) {
        if(h4<0 || h4>65535)throw new IllegalArgumentException("Niepoprawne h4");
        return (h4 & 0xFFF0) | 3;
    }
    /** Finish only on a freshly confirmed target link. CM is h4 bits 0..3. */
    private Result finish(Settings target,int product,int word) throws IOException {
        phase("4/5 — kontrola połączenia i ustawianie CM=3");
        if(!targetConfirmed(target,product,word))
            return result(false,false,"Nie potwierdzono docelowego silnika przed CM. CM nie zapisano.");
        int before=read(target,3,4);
        if(before<0)return result(false,false,"Komunikacja potwierdzona, ale nie odczytano CM z h4. Kodowanie niepełne.");
        int desired=withCm3(before);
        log.append(String.format(java.util.Locale.US,"CM h4: 0x%04X -> 0x%04X; cel CM=3\n",before,desired));
        if(before!=desired) {
            attempted=true;
            ModbusRtu.Reply pin=request(target,6,0,1234,900);
            if(pin==null || !pin.successful())
                return result(false,false,"Komunikacja potwierdzona, ale brak potwierdzenia PIN przed CM. CM nie zapisano.");
            clock.sleep(500);
            if(read(target,3,4)!=before)
                return result(false,false,"h4 zmienił się przed zapisem CM. Zapis CM wstrzymany.");
            try {
                ModbusRtu.Reply written=request(target,6,4,desired,1100);
                if(written!=null && !written.successful())
                    return result(false,false,"Silnik odrzucił zapis CM do h4 (kod "+written.exception+").");
                log.append("h4 wysłano raz; ").append(written==null?"brak ACK":"ACK").append('\n');
            } catch(IOException e) { log.append("h4: odpowiedź niepewna: ").append(e.getMessage()).append('\n'); }
            long deadline=clock.now()+5000;
            boolean verified=false;
            do {
                int value=read(target,3,4);
                log.append(String.format(java.util.Locale.US,"Kontrola h4: %s\n",value<0?"brak odpowiedzi":String.format(java.util.Locale.US,"0x%04X",value)));
                if(value==desired) { verified=true;break; }
                if(clock.now()<deadline)clock.sleep(Math.min(200,deadline-clock.now()));
            } while(clock.now()<deadline);
            if(!verified)return result(false,false,"Komunikacja potwierdzona, ale CM=3 niepotwierdzone odczytem. Kodowanie niepełne.");
            if(!targetConfirmed(target,product,word))
                return result(false,false,"CM odczytano jako 3, ale końcowa kontrola komunikacji nie powiodła się.");
        }
        confirmedControlWord=desired;
        phase("5/5 — końcowy odczyt kontrolny potwierdzony");
        return result(true,false,"Potwierdzono "+target.address+" / "+target.baud+" / "+target.mode+"; CM 3/3.");
    }
    public Result run(Settings live,int product,int previousWord,int targetWord) {
        log.setLength(0);attempted=false;confirmedControlWord=-1;
        phase("1/5 — połączenie i odczyt parametrów silnika");
        if((product!=0x0100 && product!=0x0101) ||
                !ModbusRtu.resemblesEcblueComParameter(previousWord) ||
                !ModbusRtu.resemblesEcblueComParameter(targetWord))
            return result(false,false,"Niepotwierdzony profil silnika. Bez zapisu.");
        Settings target=Settings.stored(targetWord);
        if(target.baud==38400 || target.baud==115200)
            return result(false,false,"Niepotwierdzona obsługa docelowej prędkości. Bez zapisu.");
        log.append("Aktywny adres ").append(live.address).append(" -> ").append(target.address)
            .append("; cel ").append(target.baud).append('/').append(target.mode).append('\n');
        try {
            if(read(live,4,1)!=product || read(live,3,3)!=previousWord)
                return result(false,false,"Dane silnika zmieniły się. Wykryj go ponownie.");
            Settings savedLink=Settings.stored(previousWord);
            // Some adapters can deliver valid reads on a mismatched framing.
            // Prefer h3 framing ONLY after fresh ID+h3 readback on the same address.
            // Never select the stored address here: it may await activation.
            if(savedLink.address==live.address && !savedLink.same(live)) {
                log.append("Rozbieżność trybu: wykryty ").append(live.baud).append('/').append(live.mode)
                   .append(", h3 ").append(savedLink.baud).append('/').append(savedLink.mode).append('\n');
                if(read(savedLink,4,1)==product && read(savedLink,3,3)==previousWord) {
                    live=savedLink;
                    log.append("Tryb z h3 potwierdzony świeżym odczytem i1+h3; używam go do zapisu.\n");
                } else {
                    log.append("Tryb z h3 nie odpowiedział; ponownie sprawdzam wykryte połączenie.\n");
                    if(read(live,4,1)!=product || read(live,3,3)!=previousWord)
                        return result(false,false,"Nie potwierdzono stabilnego trybu komunikacji. Bez zapisu.");
                }
            }
            if(previousWord==targetWord && live.same(target))
                return finish(target,product,targetWord);
            phase("2/5 — adres i parametry komunikacji");
            if(previousWord!=targetWord) {
                attempted=true;
                ModbusRtu.Reply pin=request(live,6,0,1234,900);
                if(pin==null || !pin.successful())
                    return result(false,false,"Brak potwierdzenia PIN 1234. Nie wysłano zapisu h3.");
                // PIN acknowledgement need not mean firmware has completed access processing.
                // A fixed pause is a compatibility measure, not a claim about the failure cause.
                clock.sleep(500);
                log.append("PIN 1234: odpowiedź odebrana; 500 ms przed zapisem h3.\n");
                ModbusRtu.Reply saved=null;
                try { saved=request(live,6,3,targetWord,1100); }
                catch(IOException e) { log.append("Zapis h3: ").append(e.getMessage()).append('\n'); }
                if(saved!=null && !saved.successful())
                    return result(false,false,"Silnik odrzucił zapis h3 (kod "+saved.exception+").");
                log.append("h3 FC06 wysłany raz: ").append(saved==null?"brak ACK":"ACK")
                   .append("; odczyty kontrolne do 5 s.\n");
                long deadline=clock.now()+5000;
                boolean stored=false;
                do {
                    int raw=read(live,3,3);
                    log.append("Odczyt h3: ").append(raw<0?"brak odpowiedzi":String.format(java.util.Locale.US,"0x%04X",raw)).append('\n');
                    if(raw==targetWord) { stored=true;break; }
                    if(!live.same(target) && targetConfirmed(target,product,targetWord))
                        return finish(target,product,targetWord);
                    if(clock.now()<deadline)clock.sleep(Math.min(200,deadline-clock.now()));
                } while(clock.now()<deadline);
                if(!stored)return result(false,false,"h3 nie potwierdził nowej wartości po zapisie FC06. Aktywacji 3698 nie wysłano. Skopiuj raport techniczny.");
            } else log.append("h3 już zawiera cel; pominięto PIN 1234 i zapis h3.\n");
            phase("3/5 — zastosowanie zapisanych parametrów");
            // Activation is sent once on the ACTIVE link, not the address stored in h3.
            attempted=true;
            try {
                ModbusRtu.Reply activated=request(live,6,0,3698,1200);
                log.append("PIN 3698: ").append(activated==null?"brak ACK":activated.successful()?"ACK":"wyjątek "+activated.exception).append('\n');
            } catch(IOException e) { log.append("PIN 3698: ").append(e.getMessage()).append('\n'); }
            log.append("Kontrola nowego połączenia do 15 s, bez kolejnych zapisów.\n");
            long deadline=clock.now()+15000;
            do {
                clock.sleep(Math.min(200,Math.max(0,deadline-clock.now())));
                if(targetConfirmed(target,product,targetWord))
                    return finish(target,product,targetWord);
            } while(clock.now()<deadline);
            if(read(live,4,1)==product && read(live,3,3)==targetWord)
                return result(false,true,"Nowe ustawienia są zapisane w h3, ale nadal odpowiada poprzednie połączenie. Aktywacja niepotwierdzona.");
            return result(false,false,"Nie potwierdzono nowego połączenia. Sprawdź stary i nowy adres; nie ponawiaj zapisu w ciemno.");
        } catch(IOException e) {
            log.append("Błąd: ").append(e.getMessage()).append('\n');
            return result(false,false,"Operacja przerwana: "+e.getMessage()+". Wymagany ponowny odczyt.");
        }
    }
}
