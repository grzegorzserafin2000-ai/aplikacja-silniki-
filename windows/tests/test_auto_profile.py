import os,sys,threading,unittest
sys.path.insert(0,os.path.dirname(os.path.dirname(__file__)))
from protocol import crc16, encode_h3, decode_h3, read_request, check_reply
from model_profile import can_write, product_label, reason_write_blocked, pending_activation, pending_message
from scan_service import Detection,read_at

class StubSerial:
    timeout = .03
    def __init__(self, values):
        self.values = values
        self.buf = bytearray()
    def reset_input_buffer(self): self.buf.clear()
    def write(self,req):
        addr,fn,reg = req[0],req[1],int.from_bytes(req[2:4],'big')
        val = self.values.get((addr,fn,reg))
        if val is None: return
        payload=bytes((addr,fn,2)) + val.to_bytes(2,'big')
        self.buf.extend(payload + crc16(payload).to_bytes(2,'little'))
    def flush(self): pass
    def read(self,n):
        if not self.buf:return b''
        chunk=bytes(self.buf[:n]);del self.buf[:n];return chunk

class AutoProfileTests(unittest.TestCase):
    def detect(self,code, h3=0xf722, baud=19200,parity='E',bits=1,address=247):
        values={(address,4,1):code, (address,4,0):1401, (address,3,3):h3}
        hit=read_at(StubSerial(values),'COM3',address,baud,parity,bits,.005,threading.Event(),{'requests':0})
        return hit
    def test_supported_known_product(self):
        for code in (0x100,0x101):
            d=self.detect(code)
            self.assertTrue(can_write(d))
            self.assertIn('ECblue',product_label(code))
            self.assertEqual(d.firmware,1401)
    def test_other_product_is_read_only(self):
        for code in (0x0032,0x0102,0x0103,0x0200,0x0300,0x0401):
            d=self.detect(code)
            self.assertFalse(can_write(d))
            self.assertIn('nie ma potwierdzonego',reason_write_blocked(d))
    def test_pending_reboot_blocks_write(self):
        d=self.detect(0x0100, h3=encode_h3(231,9600,'8N2'))
        self.assertFalse(can_write(d))
        self.assertIn('restart',reason_write_blocked(d))
    def test_active_231_saved_230_is_not_unknown(self):
        d=self.detect(0x0100, h3=encode_h3(230,9600,'8N2'),
                      address=231,baud=9600,parity='N',bits=2)
        self.assertTrue(pending_activation(d))
        self.assertFalse(can_write(d))
        self.assertIn('AKTYWNY', pending_message(d))
        self.assertIn('ZAPISANY', pending_message(d))
        self.assertIn('230', reason_write_blocked(d))
        self.assertIn('231', reason_write_blocked(d))
        self.assertNotIn('nie ma potwierdzonego', reason_write_blocked(d))
        self.assertEqual(d.model_name,'ECblue Basic Standard (new) [i1=0x0100]')
    def test_after_restart_matching_230_allows_known_profile(self):
        d=self.detect(0x0100, h3=encode_h3(230,9600,'8N2'),
                      address=230,baud=9600,parity='N',bits=2)
        self.assertFalse(pending_activation(d))
        self.assertTrue(can_write(d))
    def test_known_model_pending_different_speed(self):
        d=self.detect(0x0101, h3=encode_h3(231,19200,'8E1'),
                      address=231,baud=9600,parity='N',bits=2)
        self.assertTrue(pending_activation(d))
        self.assertFalse(can_write(d))
    def test_unknown_product_mismatch_is_not_claimed_pending(self):
        d=self.detect(0x0200, h3=encode_h3(230,9600,'8N2'),
                      address=231,baud=9600,parity='N',bits=2)
        self.assertFalse(pending_activation(d))
        self.assertFalse(can_write(d))
        self.assertIn('nie ma potwierdzonego',reason_write_blocked(d))
    def test_bad_h3_not_pending(self):
        d=self.detect(0x0100, h3=0x0000)
        self.assertFalse(pending_activation(d))
        self.assertFalse(can_write(d))

    def test_no_product_id_blocks_write(self):
        d=Detection('COM3',247,19200,'E',1,0xf722,decode_h3(0xf722),3,3)
        self.assertFalse(can_write(d))
    def test_modbus_fc04_reply(self):
        request=read_request(247,1,4)
        self.assertEqual(request[:6], bytes([247,4,0,1,0,1]))
        r=bytes([247,4,2,1,0]);r+=crc16(r).to_bytes(2,'little')
        self.assertEqual(check_reply(r,247,4,1,1),0x100)
if __name__ == '__main__': unittest.main()
