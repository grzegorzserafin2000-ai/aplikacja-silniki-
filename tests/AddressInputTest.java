import pl.cg.ziehlconfig.AddressInput;
public final class AddressInputTest {
    public static void main(String[] args) {
        for (int address : new int[]{1, 17, 231, 232, 247}) {
            if (AddressInput.parse(" " + address + " ") != address)
                throw new AssertionError("Wrong target address");
        }
        for (String input : new String[]{"", " ", "0", "248", "-1", "1.5", "abc", "99999999999999"}) {
            try {
                AddressInput.parse(input);
                throw new AssertionError("Accepted invalid address: " + input);
            } catch (IllegalArgumentException expected) { }
        }
        for(int number=1;number<=8;number++) {
            int supply=AddressInput.ventilationAddress(true,number);
            int extract=AddressInput.ventilationAddress(false,number);
            if(supply!=231+2*(number-1) || extract!=supply+1 || supply%2!=1 || extract%2!=0)
                throw new AssertionError("Wrong ventilation address pair");
        }
        for(int number:new int[]{0,9}) {
            try { AddressInput.ventilationAddress(true,number);throw new AssertionError("Invalid motor number"); }
            catch(IllegalArgumentException expected) { }
        }
        System.out.println("PASS: 5 valid target addresses, 8 invalid inputs");
    }
}
