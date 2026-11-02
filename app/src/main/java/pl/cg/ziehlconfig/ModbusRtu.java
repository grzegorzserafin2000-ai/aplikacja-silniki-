package pl.cg.ziehlconfig;

/** Pure-Java Modbus RTU frames. No register writes happen in this class implicitly. */
public final class ModbusRtu {
    private ModbusRtu() {}

    public static int crc16(byte[] data, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; ++i) {
            crc ^= data[i] & 0xFF;
            for (int b = 0; b < 8; b++)
                crc = (crc & 1) == 1 ? ((crc >>> 1) ^ 0xA001) : (crc >>> 1);
        }
        return crc & 0xFFFF;
    }

    private static byte[] packet(int address, int function, int a, int b) {
        if (address < 1 || address > 247) throw new IllegalArgumentException("Adres 1–247");
        byte[] frame = new byte[] { (byte) address, (byte) function,
                (byte) (a >> 8), (byte) a, (byte) (b >> 8), (byte) b, 0, 0 };
        int crc = crc16(frame, 6);
        frame[6] = (byte) crc;
        frame[7] = (byte) (crc >>> 8);
        return frame;
    }

    public static byte[] readHolding(int address, int register) {
        return packet(address, 3, register, 1);
    }

    public static byte[] readInput(int address, int register) {
        return packet(address, 4, register, 1);
    }

    public static byte[] writeRegister(int address, int register, int value) {
        if (register < 0 || register > 65535 || value < 0 || value > 65535)
            throw new IllegalArgumentException("Rejestr/wartość poza zakresem");
        return packet(address, 6, register, value);
    }

    public static final class Reply {
        public final int slave, function, value, exception;
        Reply(int slave, int function, int value, int exception) {
            this.slave = slave; this.function = function;
            this.value = value; this.exception = exception;
        }
        public boolean successful() { return exception == 0; }
    }

    /** Match a complete response inside a receive buffer (also tolerates extraneous preceding bytes). */
    public static Reply parse(byte[] buffer, int length, int expectedSlave, int expectedFunction,
                              int requestedRegister, int requestedValue) {
        for (int p = 0; p + 5 <= length; p++) {
            if ((buffer[p] & 0xff) != expectedSlave) continue;
            int fn = buffer[p+1] & 0xff;
            int size = fn == (expectedFunction | 0x80) ? 5 :
                       (fn == 3 || fn == 4) && fn == expectedFunction ? 7 :
                       fn == 6 && expectedFunction == 6 ? 8 : 0;
            if (size == 0 || p+size > length) continue;
            byte[] candidate = new byte[size];
            System.arraycopy(buffer, p, candidate, 0, size);
            int crc = crc16(candidate, size-2);
            if ((candidate[size-2] & 0xff) != (crc & 0xff) ||
                (candidate[size-1] & 0xff) != (crc >>> 8)) continue;
            if (fn == (expectedFunction | 0x80))
                return new Reply(expectedSlave, fn, -1, candidate[2] & 0xff);
            if ((expectedFunction == 3 || expectedFunction == 4) && (candidate[2] & 0xff) == 2) {
                int value = ((candidate[3] & 0xff) << 8) | (candidate[4] & 0xff);
                return new Reply(expectedSlave, fn, value, 0);
            }
            if (expectedFunction == 6) {
                int reg = ((candidate[2] & 0xff) << 8) | (candidate[3] & 0xff);
                int value = ((candidate[4] & 0xff) << 8) | (candidate[5] & 0xff);
                if (reg == requestedRegister && value == requestedValue)
                    return new Reply(expectedSlave, fn, value, 0);
            }
        }
        return null;
    }

    public static int configValue(int address, int baud, String mode) {
        int baudNibble;
        switch (baud) {
            case 4800: baudNibble = 0; break;
            case 9600: baudNibble = 1; break;
            case 19200: baudNibble = 2; break;
            case 38400: baudNibble = 3; break;
            case 115200: baudNibble = 4; break;
            default: throw new IllegalArgumentException("Nieobsługiwana prędkość");
        }
        int modeNibble;
        switch (mode) {
            case "8N1": modeNibble = 0; break;
            case "8O1": modeNibble = 1; break;
            case "8E1": modeNibble = 2; break;
            case "8N2": modeNibble = 3; break;
            default: throw new IllegalArgumentException("Nieobsługiwany format");
        }
        if (address < 1 || address > 247) throw new IllegalArgumentException("Adres 1–247");
        return (address << 8) | (baudNibble << 4) | modeNibble;
    }

    /** h3 encoding per ZIEHL-ABEGG L-BAL-E076-GB 1517, Index 013, p.9. */
    public static boolean resemblesEcblueComParameter(int raw) {
        int address = (raw >> 8) & 0xff;
        int baud = (raw >> 4) & 0xf;
        int mode = raw & 0xf;
        return address >= 1 && address <= 247 && baud <= 4 && mode <= 3;
    }

    /** Decode communication settings from h3, keeping firmware-specific caveats. */
    public static int decodeBaud(int h3) {
        int b=(h3 >>> 4) & 0xF;
        int[] baud={4800,9600,19200,38400,115200};
        if(b>=baud.length) throw new IllegalArgumentException("Nieznany kod prędkości h3");
        return baud[b];
    }

    public static String decodeMode(int h3) {
        int m=h3 & 0xF;
        String[] modes={"8N1","8O1","8E1","8N2"};
        if(m>=modes.length) throw new IllegalArgumentException("Nieznany kod transmisji h3");
        return modes[m];
    }

    public static String description(int raw) {
        int[] baud = {4800,9600,19200,38400,115200};
        String[] mode = {"8N1", "8O1", "8E1", "8N2"};
        int b = (raw >> 4) & 0xf, m = raw & 0xf;
        return "Adres " + ((raw >> 8) & 0xff) + ", " +
                (b < baud.length ? baud[b] : "?") + " / " +
                (m < mode.length ? mode[m] : "?");
    }
}
