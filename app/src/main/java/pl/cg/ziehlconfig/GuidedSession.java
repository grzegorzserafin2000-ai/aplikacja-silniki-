package pl.cg.ziehlconfig;

/** Session progress can only be advanced by confirmed programming results. */
public final class GuidedSession {
  public static int[] addresses(int supply, int extract) {
    if (supply < 0 || supply > 8 || extract < 0 || extract > 8 || supply + extract == 0)
      throw new IllegalArgumentException("Wybierz od 1 do 8 silników dla aktywnej sekcji.");
    int[] result = new int[supply + extract];
    for (int n = 0; n < supply; n++) result[n] = AddressInput.ventilationAddress(true, n + 1);
    for (int n = 0; n < extract; n++)
      result[supply + n] = AddressInput.ventilationAddress(false, n + 1);
    return result;
  }

  public static String centralNumber(String prefix, String number) {
    if (prefix == null || !("C".equals(prefix) || "K".equals(prefix)))
      throw new IllegalArgumentException("Wybierz oznaczenie centrali: C albo K.");
    String digits = number == null ? "" : number.trim();
    if (!digits.matches("[0-9]{4,5}"))
      throw new IllegalArgumentException(
          "Wpisz 4 lub 5 cyfr numeru centrali, np. 4234. Literę C/K wybierz osobno.");
    return prefix + digits;
  }

  public static String motorLabel(int address) {
    if (address < 231 || address > 246)
      throw new IllegalArgumentException("Adres poza planem centrali.");
    return ((address & 1) == 1 ? "Nawiew " : "Wywiew ")
        + ((address - 231) / 2 + 1)
        + " — adres "
        + address;
  }

  public static boolean verificationMatches(int address, int product, int h3, int h4) {
    return (product == 0x0100 || product == 0x0101)
        && h3 == ModbusRtu.configValue(address, 9600, "8N2")
        && (h4 & 15) == 3;
  }

  public static boolean allConfirmed(int[] planned, boolean[] confirmed) {
    if (planned.length == 0 || planned.length != confirmed.length) return false;
    for (boolean ok : confirmed) if (!ok) return false;
    return true;
  }
}
