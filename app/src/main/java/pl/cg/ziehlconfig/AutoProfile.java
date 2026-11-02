package pl.cg.ziehlconfig;

/** Recognition codes from ZIEHL-ABEGG L-BAL-E076-GB 1517 Index 013, p.18.
 * h3 layout and restrictions: same manual p.9. Live readback is essential:
 * detecting an address or product code alone must never unlock a write.
 */
public final class AutoProfile {
    private AutoProfile() {}

    public static boolean canWrite(int productCode, int rawH3, int liveAddress,
                                   int liveBaud, int liveParity, int liveStop) {
        // ECblue Basic Standard (new) and ECblue Modbus 02. Other families are read-only.
        if ((productCode != 0x0100 && productCode != 0x0101) ||
                !ModbusRtu.resemblesEcblueComParameter(rawH3)) return false;
        if (((rawH3 >>> 8) & 0xff) != liveAddress) return false;
        int baudCode=(rawH3 >>> 4) & 0xf;
        int baud=baudCode==0?4800:baudCode==1?9600:baudCode==2?19200:
                 baudCode==3?38400:baudCode==4?115200:-1;
        int mode=rawH3 & 0xf;
        // UsbSerialPort: NONE=0, ODD=1, EVEN=2. STOPBITS_1=1, STOPBITS_2=2.
        int parity=mode==1?1:mode==2?2:0;
        int stops=mode==3?2:1;
        return baud==liveBaud && parity==liveParity && stops==liveStop;
    }

    /**
     * The controller may already have stored new settings in h3 while responding
     * on the PREVIOUS live link until a safe controller reset / parameter takeover.
     * This is informational only, never grants permission to write.
     */
    public static boolean pendingActivation(int code, int rawH3, int liveAddress,
                                            int liveBaud, int liveParity, int liveStop) {
        if ((code != 0x0100 && code != 0x0101) ||
                !ModbusRtu.resemblesEcblueComParameter(rawH3) ||
                liveAddress < 1 || liveAddress > 247) return false;
        return !canWrite(code, rawH3, liveAddress, liveBaud, liveParity, liveStop);
    }

    public static String productLabel(int code) {
        if(code<0) return "Nie udało się odczytać kodu produktu";
        switch(code) {
            case 0x0100: return "ECblue Basic Standard (new) • i1=0x0100";
            case 0x0101: return "ECblue MODBUS 02 (EC116) • i1=0x0101";
            case 0x0032: return "ECblue Basic Standard (old) • i1=0x0032";
            case 0x0102: return "ECblue Lite (EC116) • i1=0x0102";
            case 0x0103: return "ECblue (EC90) • i1=0x0103";
            case 0x0200: return "Icontrol Basic • i1=0x0200";
            case 0x0201: return "Fcontrol Basic • i1=0x0201";
            case 0x0300: return "PMcontrol Basic • i1=0x0300";
            case 0x0401: return "AM-MODBUS • i1=0x0401";
            case 0x0402: return "AM-MODBUS-W • i1=0x0402";
            default: return String.format(java.util.Locale.US,
                    "Nieobsługiwany kod produktu i1=0x%04X", code);
        }
    }
}
