import sys,os,unittest,threading
sys.path.insert(0,os.path.dirname(os.path.dirname(__file__)))
from scan_service import *

class FakeCom:
    timeout=.03
    def __init__(self, responses):
        self.responses=responses
        self.buf=bytearray()
        self.requests=[]
    def reset_input_buffer(self): self.buf.clear()
    def write(self, data):
        self.requests.append(data)
        addr,fn,reg=data[0],data[1],int.from_bytes(data[2:4],'big')
        payload=self.responses.get((addr,fn,reg))
        if payload is not None:
            self.buf.extend(payload + crc16(payload).to_bytes(2,'little'))
    def flush(self): pass
    def read(self,n):
        if self.buf:
            out=bytes(self.buf[:n]);del self.buf[:n]; return out
        return b''

class ScanTests(unittest.TestCase):
    def test_range(self):
        self.assertEqual(validate_range('1','231'),(1,231))
        self.assertEqual(validate_range(220,220),(220,220))
        for start,end in [(0,1),(230,220),(1,248),(1,0)]:
            with self.assertRaises(ValueError): validate_range(start,end)
    def test_crc(self):
        req=make_request(247,4,1)
        self.assertEqual(req[:6],bytes([247,4,0,1,0,1]))
        self.assertEqual(req[-2:],crc16(req[:-2]).to_bytes(2,'little'))
    def test_fc04_response_and_exception(self):
        payload=bytes([221,4,2,0,33]);f=payload+crc16(payload).to_bytes(2,'little')
        self.assertEqual(parse_read_response(f,221,4),(33,0))
        self.assertIsNone(parse_read_response(f,221,3))
        bad=bytearray(f);bad[-1]^=1
        self.assertIsNone(parse_read_response(bytes(bad),221,4))
        p=bytes([221,0x84,2]);self.assertEqual(parse_read_response(p+crc16(p).to_bytes(2,'little'),221,4),(None,2))
    def test_other_register_detects_model_without_h3(self):
        fake=FakeCom({(222,4,0):bytes([222,4,2,0,17])})
        metrics={'requests':0}
        hit=read_at(fake,'COM3',222,19200,'E',1,.001,threading.Event(),metrics)
        self.assertEqual(hit.active_address,222)
        self.assertFalse(hit.can_consider_h3)
        self.assertEqual(hit.method,'FC04 / rejestr 0')
        self.assertEqual(metrics['requests'],7)  # 4 probings + i1/i0 + h3 recheck
    def test_h3_confirmed(self):
        fake=FakeCom({(247,3,3):bytes([247,3,2,0xF7,0x22])})
        hit=read_at(fake,'COM3',247,19200,'E',1,.002,threading.Event(),{'requests':0})
        self.assertTrue(hit.can_consider_h3)
        self.assertEqual(hit.saved.address,247)
    def test_exception_proves_presence_but_not_profile(self):
        fake=FakeCom({(232,3,3):bytes([232,0x83,2])})
        hit=read_at(fake,'COM5',232,9600,'N',2,.001,threading.Event(),{'requests':0})
        self.assertEqual(hit.exception,2)
        self.assertIsNone(hit.raw_h3)
        self.assertFalse(hit.can_consider_h3)

if __name__=='__main__': unittest.main()

class QuickScanTests(unittest.TestCase):
    def setup_scan(self, reader):
        from unittest.mock import patch, MagicMock
        connection=MagicMock();connection.__enter__.return_value=connection
        return patch('scan_service._open',return_value=connection),patch('scan_service.read_at',side_effect=reader)
    def test_no_motor_finishes_without_full_scan(self):
        a,b=self.setup_scan(lambda *args:None)
        with a,b as read:
            found,metrics=auto_scan('COM3',lambda _:None,threading.Event())
        self.assertIsNone(found);self.assertEqual(read.call_count,192)
        self.assertFalse(metrics['full_scan'])
        self.assertTrue(all(call.args[2] in PRIORITY for call in read.call_args_list))
    def test_first_reply_stops_immediately(self):
        marker=object();a,b=self.setup_scan(lambda *args:marker)
        with a,b as read:found,metrics=auto_scan('COM3',lambda _:None,threading.Event())
        self.assertIs(found,marker);self.assertEqual(read.call_count,1)
    def test_full_addresses_only_when_requested(self):
        stop=threading.Event();seen=[]
        def reader(*args):
            seen.append(args[2])
            if args[2] not in PRIORITY:stop.set()
            return None
        a,b=self.setup_scan(reader)
        with a,b:_,metrics=auto_scan('COM3',lambda _:None,stop,True)
        self.assertTrue(metrics['full_scan']);self.assertTrue(any(x not in PRIORITY for x in seen))
    def test_addresses_descend_from_247_to_200(self):
        a,b=self.setup_scan(lambda *args:None)
        with a,b as read:
            auto_scan('COM3',lambda _:None,threading.Event())
        addresses=[call.args[2] for call in read.call_args_list]
        self.assertEqual(addresses,list(range(247,199,-1))*4)
