import sys,unittest
from types import SimpleNamespace as NS
from unittest.mock import patch
from protocol import encode_h3,decode_h3,ModbusException
import simple_write
class Clock:
 def __init__(self):self.value=0
 def monotonic(self):return self.value
 def sleep(self,n):self.value+=n
class Motor:
 def __init__(self):
  self.clock=Clock();self.active=(231,9600,'N',2);self.raw=encode_h3(231)
  self.pending=None;self.store_at=float('inf');self.activate_at=float('inf')
  self.store_delay=0;self.delay=0;self.alias=False;self.lost=False;self.reject=False;self.ignore=False;self.no_activation=False
  self.ops=[];self.formats=[]
 def factory(self,**kw):
  conn=NS(baudrate=kw['baudrate'],parity=kw['parity'],stopbits=kw['stopbits'])
  class Context:
   def __enter__(self):return conn
   def __exit__(self,*args):return False
  return Context()
 def transact(self,c,address,fn,reg,val,timeout=1):
  self.clock.sleep(.01)
  if self.clock.value>=self.store_at:self.raw=self.pending
  if self.clock.value>=self.activate_at:
   v=decode_h3(self.raw);self.active=(v.address,v.baud,v.mode[1],int(v.mode[2]))
  link=(address,c.baudrate,c.parity,c.stopbits)
  alias=self.alias and link==(231,9600,'E',1) and self.active==(231,9600,'N',2)
  if link!=self.active and not alias:self.clock.sleep(timeout);raise TimeoutError('offline')
  if fn==4:return 0x0100
  if fn==3:return self.raw
  if alias and reg==3:raise TimeoutError('wrong framing')
  self.ops.append((address,reg,val));self.formats.append(link)
  if reg==3:
   if self.reject:raise ModbusException(3)
   if not self.ignore:self.pending=val;self.store_at=self.clock.value+self.store_delay
  if reg==0 and val==3698 and not self.no_activation:self.activate_at=self.clock.value+self.delay
  if self.lost and (reg==3 or val==3698):raise TimeoutError('lost ACK')
  return val
class ProgrammingTests(unittest.TestCase):
 def run_motor(self,m,live=None,product=0x0100,target=None):
  a,b,p,s=live or m.active
  d=NS(product_code=product,raw_h3=m.raw,saved=decode_h3(m.raw),active_address=a,baud=b,parity=p,stopbits=s,port='COM3')
  with patch.dict(sys.modules,{'serial':NS(Serial=m.factory)}),patch.object(simple_write,'transact',m.transact):
   return simple_write.program_one_click(d,target or encode_h3(232),clock=m.clock)
 def test_delayed_lost_ack(self):
  m=Motor();m.store_delay=1.5;m.delay=9;m.lost=True;r=self.run_motor(m)
  self.assertEqual(r.state,'ok');self.assertEqual(len(m.ops),3);self.assertGreaterEqual(m.clock.value,9)
 def test_pending_no_h3(self):
  m=Motor();m.raw=encode_h3(230);r=self.run_motor(m,target=encode_h3(230))
  self.assertEqual(r.state,'ok');self.assertEqual(m.ops,[(231,0,3698)])
 def test_report_e1_reads_n2_writes(self):
  m=Motor();m.alias=True;r=self.run_motor(m,live=(231,9600,'E',1))
  self.assertEqual(r.state,'ok');self.assertTrue(all(v==(231,9600,'N',2) for v in m.formats))
  self.assertIn('Tryb z h3 potwierdzony',r.details)
 def test_unknown_no_write(self):
  m=Motor()
  with self.assertRaises(Exception):self.run_motor(m,product=0x0103)
  self.assertFalse(m.ops)
 def test_rejected_no_activation(self):
  m=Motor();m.reject=True;r=self.run_motor(m)
  self.assertEqual(r.state,'rejected');self.assertEqual(len(m.ops),2)
 def test_unchanged_h3_no_activation(self):
  m=Motor();m.ignore=True;r=self.run_motor(m)
  self.assertEqual(r.state,'uncertain');self.assertEqual(len(m.ops),2)
 def test_not_activated_pending(self):
  m=Motor();m.no_activation=True;r=self.run_motor(m)
  self.assertEqual(r.state,'pending');self.assertEqual(len(m.ops),3)
 def test_factory_to_232(self):
  m=Motor();m.active=(247,19200,'E',1);m.raw=encode_h3(247,19200,'8E1');r=self.run_motor(m)
  self.assertEqual(r.state,'ok');self.assertEqual(m.active,(232,9600,'N',2))
  self.assertTrue(all(v==(247,19200,'E',1) for v in m.formats))
