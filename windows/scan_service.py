"""Read-only, model-agnostic MODBUS RTU discovery.

Detection means only a correctly addressed MODBUS response with a valid CRC.
It does NOT identify a motor model, nor authorize writes.
"""
from dataclasses import dataclass
from typing import Optional, Callable, Iterable
import threading
import time
from protocol import crc16, decode_h3, ModbusError, ComSetting
from model_profile import can_write, product_label

PROBES = ((3, 3), (3, 0), (4, 0), (4, 1))
COMMON_LINKS = (
    (19200,'E',1),(9600,'N',2),(9600,'E',1),(19200,'N',2),
    (19200,'N',1),(9600,'N',1),(4800,'E',1),(38400,'E',1),
    (38400,'N',2),(4800,'N',2),(19200,'O',1),(9600,'O',1),
    (4800,'N',1),(38400,'N',1),(4800,'O',1),(38400,'O',1),
)
PRIORITY = (247, 231, 232, 221, 1)

@dataclass(frozen=True)
class Detection:
    port: str
    active_address: int
    baud: int
    parity: str
    stopbits: int
    raw_h3: Optional[int]
    saved: Optional[ComSetting]
    function: int
    register: int
    exception: int = 0
    product_code: Optional[int] = None
    firmware: Optional[int] = None

    @property
    def active_info(self):
        parity = {'N':'None','E':'Even','O':'Odd'}[self.parity]
        return f'{self.active_address} / {self.baud} / {parity} / {self.stopbits}'

    @property
    def method(self):
        return f'FC{self.function:02d} / rejestr {self.register}'

    @property
    def can_consider_h3(self):
        return self.raw_h3 is not None and self.saved is not None

    @property
    def write_compatible(self):
        return can_write(self)

    @property
    def model_name(self):
        return product_label(self.product_code)


def validate_range(start, end):
    first, last = int(start), int(end)
    if not (1 <= first <= last <= 247):
        raise ValueError('Adresy: 1–247, a Adres od nie może być większy od Adres do.')
    return first, last


def make_request(address: int, function: int, register: int) -> bytes:
    if not 1 <= address <= 247 or function not in (3,4):
        raise ValueError('Nieprawidłowe zapytanie MODBUS')
    payload = bytes((address,function,register>>8,register&255,0,1))
    return payload + crc16(payload).to_bytes(2, 'little')


def parse_read_response(data: bytes, address: int, function: int):
    """Return (value or None, exception_code), or None for an invalid frame."""
    if len(data) not in (5,7) or data[0] != address:
        return None
    if data[-2:] != crc16(data[:-2]).to_bytes(2, 'little'):
        return None
    if len(data) == 5 and data[1] == (function | 0x80):
        return (None, data[2])
    if len(data) == 7 and data[1] == function and data[2] == 2:
        return (int.from_bytes(data[3:5], 'big'), 0)
    return None


def exchange_read(connection, address, function, register, timeout, stop=None):
    """Read one register; a valid MODBUS exception still indicates a device.

    Strip echo/garbage; never rely on a partial response or a CRC-less byte.
    """
    request = make_request(address, function, register)
    connection.timeout = .03
    connection.reset_input_buffer()
    connection.write(request)
    connection.flush()
    data = bytearray()
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if stop is not None and stop.is_set():
            return None
        chunk = connection.read(16)
        if chunk:
            data.extend(chunk)
            if len(data) > 120:
                del data[:-80]
            for n in (5,7):
                for i in range(len(data)-n+1):
                    parsed = parse_read_response(bytes(data[i:i+n]), address, function)
                    if parsed is not None:
                        return parsed
    return None


def read_at(connection, port, addr, baud, parity, bits, timeout, stop, metrics):
    for fn, reg in PROBES:
        if stop.is_set():
            return None
        metrics['requests'] += 1
        try:
            result = exchange_read(connection, addr, fn, reg, timeout, stop)
        except Exception as exc:
            # A port fault should not be disguised as a missing motor.
            raise ModbusError(f'Błąd portu COM podczas odczytu {addr}: {exc}') from exc
        if result is not None:
            val, exception = result
            raw = val if fn == 3 and reg == 3 and exception == 0 else None
            saved = None
            if raw is not None:
                try:
                    saved = decode_h3(raw)
                except ModbusError:
                    pass
            if raw is None and exception == 0:
                # If detection was by another register, h3 may still be readable.
                metrics['requests'] += 1
                try:
                    extra = exchange_read(connection,addr,3,3,timeout,stop)
                    if extra is not None and extra[1] == 0:
                        raw = extra[0]
                        try:
                            saved = decode_h3(raw)
                        except ModbusError:
                            pass
                except Exception:
                    pass
            # FC04 input register i1 = product code; i0 = firmware.
            # A MODBUS reply proves presence only, NEVER model identity.
            product = None
            firmware = None
            for input_reg in (1, 0):
                if stop.is_set():
                    break
                metrics['requests'] += 1
                try:
                    info = exchange_read(connection, addr, 4, input_reg, max(timeout, .4), stop)
                except Exception:
                    info = None
                if info is not None and info[1] == 0:
                    if input_reg == 1:
                        product = info[0]
                    else:
                        firmware = info[0]
            # In case a valid exception identified the device, try h3 after ID queries.
            if raw is None and not stop.is_set():
                metrics['requests'] += 1
                try:
                    extra = exchange_read(connection, addr, 3, 3, max(timeout,.4), stop)
                    if extra is not None and extra[1] == 0:
                        raw = extra[0]
                        try:
                            saved = decode_h3(raw)
                        except ModbusError:
                            pass
                except Exception:
                    pass
            return Detection(port,addr,baud,parity,bits,raw,saved,fn,reg,exception,product,firmware)
    return None


def _open(port,baud,parity,bits):
    import serial
    return serial.Serial(port=port, baudrate=baud, bytesize=8, parity=parity,
                         stopbits=bits, timeout=.03, write_timeout=.8)


def manual_scan(port: str, first: int, last: int, baud: int, parity: str, stopbits: int,
                timeout_ms: int, progress: Callable[[str],None], stop: threading.Event):
    first,last=validate_range(first,last)
    if timeout_ms < 150 or timeout_ms > 3000:
        raise ValueError('Czas odpowiedzi: od 150 do 3000 ms.')
    if baud not in (4800,9600,14400,19200,38400,57600,115200) or parity not in 'NEO' or stopbits not in (1,2):
        raise ValueError('Niepoprawne parametry transmisji.')
    metrics={'requests':0, 'addresses':0, 'profiles':1}
    found = None
    with _open(port,baud,parity,stopbits) as conn:
        for address in range(first,last+1):
            if stop.is_set(): break
            metrics['addresses']+=1
            if address == first or (address-first)%5==0 or address==last:
                progress(f'Ręcznie: adres {address}/{last} / {baud} / {parity}{stopbits}')
            found=read_at(conn,port,address,baud,parity,stopbits,timeout_ms/1000,stop,metrics)
            if found is not None: break
    return found,metrics


def auto_scan(port, progress, stop, extended=False, target=None):
    """Priority scan first, full default scan second, optional remaining modes third."""
    metrics={'requests':0, 'addresses':0, 'profiles':0}
    detected=None
    links=list(COMMON_LINKS)
    targets=list(PRIORITY)
    if target is not None:
        # Check target after a restart before doing any other address.
        p=(target.baud, target.mode[1], int(target.mode[2]))
        links=[p]+[entry for entry in links if entry != p]
        targets=[target.address]+[a for a in targets if a!=target.address]
    phases=[('Szybkie',links,targets,.24), ('Pełne',links[:4],range(1,248),.22)]
    if extended:
        phases.append(('Rozszerzone',links[4:],range(1,248),.25))
    for name, profiles, addresses, timeout in phases:
        for baud, parity, bits in profiles:
            if stop.is_set(): return None,metrics
            try:
                with _open(port,baud,parity,bits) as conn:
                    metrics['profiles']+=1
                    for addr in addresses:
                        if stop.is_set(): return None,metrics
                        metrics['addresses']+=1
                        if metrics['addresses']%8==1 or addr in targets:
                            progress(f'{name}: {baud} / {parity}{bits} · adres {addr}/247')
                        detected=read_at(conn,port,addr,baud,parity,bits,timeout,stop,metrics)
                        if detected is not None: return detected,metrics
            except ModbusError:
                raise
            except Exception as e:
                raise ModbusError(f'Nie można otworzyć lub skonfigurować portu {port}: {e}') from e
    return None,metrics
