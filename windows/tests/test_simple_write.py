import sys,unittest
from unittest.mock import patch
from types import SimpleNamespace
from protocol import encode_h3
import simple_write

class Device:
    def __init__(self,active,stored,allow_activation=True):
        self.active=active;self.stored=stored;self.allow_activation=allow_activation
        self.ops=[];self.control_word=3
    def conn(self,**kw):
        dev=self
        class Link:
            def __enter__(self):return self
            def __exit__(self,*a):pass
            def matches(self):
                return self.address==dev.active[0] and (self.baudrate,self.parity,self.stopbits)==dev.active[1:]
        s=Link();s.baudrate=kw['baudrate'];s.parity=kw['parity'];s.stopbits=kw['stopbits'];s.address=None
        return s
    def transact(self,c,a,fn,reg,val,timeout=1):
        c.address=a
        if not c.matches():raise TimeoutError('not at active address')
        if fn==4 and reg==1:return 0x0100
        if fn==3 and reg==3:return self.stored
        if fn==3 and reg==4:return self.control_word
        if fn==6:
            self.ops.append((a,reg,val))
            if reg==3:self.stored=val
            if reg==4:self.control_word=val
            if reg==0 and val==3698 and self.allow_activation:
                d=simple_write.decode_h3(self.stored)
                self.active=(d.address,d.baud,d.mode[1],int(d.mode[2]))
            return val
        raise RuntimeError('Unexpected call')

class Tests(unittest.TestCase):
    def run_motor(self,dev,target):
        d=SimpleNamespace(product_code=0x0100,raw_h3=dev.stored,
           saved=simple_write.decode_h3(dev.stored),active_address=dev.active[0],
           baud=dev.active[1],parity=dev.active[2],stopbits=dev.active[3],port='COM3')
        clock=SimpleNamespace(value=0.)
        clock.monotonic=lambda:clock.value
        clock.sleep=lambda delay:setattr(clock,'value',clock.value+delay)
        with patch.dict(sys.modules,{'serial':SimpleNamespace(Serial=lambda **kw:dev.conn(**kw))}), \
             patch.object(simple_write,'transact',side_effect=dev.transact), \
             patch.object(simple_write.time,'sleep',return_value=None):
            return simple_write.program_one_click(d,target,clock=clock)
    def test_factory_to_nawiew_single_write_and_activation(self):
        dev=Device((247,19200,'E',1),encode_h3(247,19200,'8E1'))
        r=self.run_motor(dev,encode_h3(231))
        self.assertEqual(r.state,'ok');self.assertEqual(dev.active[0],231)
        self.assertEqual(len([a for a in dev.ops if a[1]==3]),1)
        self.assertEqual(len([a for a in dev.ops if a[1]==0 and a[2]==3698]),1)
    def test_pending_return_to_previous_address(self):
        dev=Device((231,9600,'N',2),encode_h3(230))
        r=self.run_motor(dev,encode_h3(231))
        self.assertEqual(r.state,'ok');self.assertEqual(dev.active[0],231)
        self.assertEqual(len([a for a in dev.ops if a[1]==3]),1)
    def test_only_activate_no_redundant_h3_write(self):
        dev=Device((231,9600,'N',2),encode_h3(230))
        r=self.run_motor(dev,encode_h3(230))
        self.assertEqual(r.state,'ok');self.assertEqual(dev.active[0],230)
        self.assertEqual(len([a for a in dev.ops if a[1]==3]),0)
        self.assertEqual(len([a for a in dev.ops if a[1]==0 and a[2]==3698]),1)
    def test_activation_not_applied(self):
        dev=Device((247,19200,'E',1),encode_h3(247,19200,'8E1'),False)
        r=self.run_motor(dev,encode_h3(232))
        self.assertEqual(r.state,'pending')
        self.assertEqual(len([a for a in dev.ops if a[1]==3]),1)
    def test_already_configured_no_write(self):
        dev=Device((231,9600,'N',2),encode_h3(231))
        r=self.run_motor(dev,encode_h3(231))
        self.assertEqual(r.state,'already');self.assertFalse(dev.ops)
    def test_unknown_model_rejected(self):
        dev=Device((247,19200,'E',1),encode_h3(247,19200,'8E1'))
        d=SimpleNamespace(product_code=0xFFFF,raw_h3=dev.stored,saved=simple_write.decode_h3(dev.stored))
        with self.assertRaises(Exception):simple_write.program_one_click(d,encode_h3(231))

if __name__=='__main__':unittest.main()
