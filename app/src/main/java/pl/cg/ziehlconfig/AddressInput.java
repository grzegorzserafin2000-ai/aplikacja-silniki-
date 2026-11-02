package pl.cg.ziehlconfig;

/** Validates the editable target independently of the selected shortcut. */
public final class AddressInput {
    private AddressInput() {}
    public static int parse(String input) {
        try {
            int address = Integer.parseInt(input.trim());
            if (address >= 1 && address <= 247) return address;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Wpisz adres MODBUS od 1 do 247.");
    }
}
