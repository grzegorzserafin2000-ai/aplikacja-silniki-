import java.util.Arrays;
import pl.cg.ziehlconfig.*;

public final class GuidedSessionTest {
  static void check(boolean b) {
    if (!b) throw new AssertionError();
  }

  static void invalid(Runnable r) {
    try {
      r.run();
      throw new AssertionError("Accepted invalid input");
    } catch (IllegalArgumentException expected) {
    }
  }

  public static void main(String[] args) {
    check(Arrays.equals(GuidedSession.addresses(3, 2), new int[] {231, 233, 235, 232, 234}));
    check(Arrays.equals(GuidedSession.addresses(0, 2), new int[] {232, 234}));
    check(Arrays.equals(GuidedSession.addresses(2, 0), new int[] {231, 233}));
    int[] all = GuidedSession.addresses(8, 8);
    check(all.length == 16 && all[7] == 245 && all[15] == 246);
    invalid(() -> GuidedSession.addresses(0, 0));
    invalid(() -> GuidedSession.addresses(9, 1));
    invalid(() -> GuidedSession.addresses(-1, 2));
    check(GuidedSession.centralNumber("C", "04234").equals("C04234"));
    invalid(() -> GuidedSession.centralNumber("C", "123"));
    invalid(() -> GuidedSession.centralNumber("X", "12345"));
    check(!GuidedSession.allConfirmed(new int[] {231, 232}, new boolean[] {true, false}));
    check(!GuidedSession.allConfirmed(new int[0], new boolean[0]));
    check(!GuidedSession.allConfirmed(new int[] {231, 232}, new boolean[] {true}));
    check(GuidedSession.allConfirmed(new int[] {231, 232}, new boolean[] {true, true}));
    int h3 = ModbusRtu.configValue(231, 9600, "8N2");
    check(GuidedSession.verificationMatches(231, 0x100, h3, 0xA503));
    check(GuidedSession.verificationMatches(231, 0x101, h3, 3));
    check(!GuidedSession.verificationMatches(232, 0x100, h3, 3));
    check(!GuidedSession.verificationMatches(231, 0x100, h3, 2));
    check(!GuidedSession.verificationMatches(231, 0x32, h3, 3));
    check(
        !GuidedSession.verificationMatches(231, 0x100, ModbusRtu.configValue(231, 9600, "8E1"), 3));
    System.out.println(
        "PASS: guided address plans, identity validation, completion gates and final readback"
            + " checks");
  }
}
