package pl.cg.ziehlconfig;

/** Inclusive MODBUS address interval. No I/O and no writes. */
public final class AddressRange {
    public final int first;
    public final int last;
    public AddressRange(int first, int last) {
        if (first < 1 || first > 247 || last < 1 || last > 247)
            throw new IllegalArgumentException("Adresy MODBUS muszą być w zakresie 1–247.");
        if (first > last)
            throw new IllegalArgumentException("Adres «Od» nie może być większy od adresu «Do».");
        this.first=first;
        this.last=last;
    }
    public int size() { return last-first+1; }
}
