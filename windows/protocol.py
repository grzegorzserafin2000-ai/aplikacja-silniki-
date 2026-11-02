"""Minimal MODBUS RTU framing for documented ECblue h3 COM parameters.

Reference: ZIEHL-ABEGG L-BAL-E076-GB (1517), p.7 and p.9.
Not a generic device programmer: writes must only be enabled after an operator
has confirmed that the model actually implements this register layout.
"""
from dataclasses import dataclass

H3 = 3
H0_PIN = 0
PIN_LEVEL_1 = 1234

class ModbusError(Exception):
    pass

class ModbusException(ModbusError):
    def __init__(self, code):
        self.code = code
        super().__init__(f"Urządzenie zgłosiło wyjątek MODBUS: 0x{code:02X}")


def crc16(payload: bytes) -> int:
    crc = 0xFFFF
    for byte in payload:
        crc ^= byte
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else (crc >> 1)
    return crc & 0xFFFF


def frame(address: int, function: int, register: int, value: int) -> bytes:
    if not 1 <= address <= 247:
        raise ValueError("Adres MODBUS musi mieścić się w zakresie 1–247")
    if not 0 <= register <= 65535 or not 0 <= value <= 65535:
        raise ValueError("Numer rejestru lub wartość poza zakresem")
    content = bytes((address, function)) + register.to_bytes(2, 'big') + value.to_bytes(2, 'big')
    return content + crc16(content).to_bytes(2, 'little')


def read_request(address: int, register: int = H3, function: int = 3) -> bytes:
    if function not in (3, 4):
        raise ValueError('Odczyt jest możliwy przez FC03 lub FC04')
    return frame(address, function, register, 1)


def write_request(address: int, register: int, value: int) -> bytes:
    return frame(address, 6, register, value)


def check_reply(reply: bytes, expected_addr: int, expected_function: int, register: int, value: int):
    """Validate FC03, FC06 and exception responses; return value for successful reply."""
    if len(reply) < 5:
        raise ModbusError("Niekompletna odpowiedź MODBUS")
    if crc16(reply[:-2]).to_bytes(2, 'little') != reply[-2:]:
        raise ModbusError("Błędna suma CRC odpowiedzi")
    if reply[0] != expected_addr:
        raise ModbusError("Odpowiedź z innego adresu")
    if reply[1] == (expected_function | 0x80) and len(reply) == 5:
        raise ModbusException(reply[2])
    if reply[1] != expected_function:
        raise ModbusError("Niewłaściwa funkcja w odpowiedzi")
    if expected_function in (3, 4):
        if len(reply) != 7 or reply[2] != 2:
            raise ModbusError("Nieprawidłowy format odpowiedzi FC03")
        return int.from_bytes(reply[3:5], 'big')
    if expected_function == 6:
        if len(reply) != 8 or int.from_bytes(reply[2:4], 'big') != register:
            raise ModbusError("Brak potwierdzenia zapisu rejestru")
        returned = int.from_bytes(reply[4:6], 'big')
        if returned != value:
            raise ModbusError("Potwierdzono inną wartość niż żądana")
        return returned
    raise ValueError("Nieobsługiwana funkcja")

BAUD_CODES = {0: 4800, 1: 9600, 2: 19200, 3: 38400, 4: 115200}
MODE_CODES = {0: '8N1', 1: '8O1', 2: '8E1', 3: '8N2'}

@dataclass(frozen=True)
class ComSetting:
    address: int
    baud: int
    mode: str


def decode_h3(raw: int) -> ComSetting:
    addr = (raw >> 8) & 0xFF
    baud_code = (raw >> 4) & 0xF
    mode_code = raw & 0xF
    if not 1 <= addr <= 247 or baud_code not in BAUD_CODES or mode_code not in MODE_CODES:
        raise ModbusError(f"Rejestr h3 nie odpowiada formatowi ECblue: 0x{raw:04X}")
    return ComSetting(addr, BAUD_CODES[baud_code], MODE_CODES[mode_code])


def encode_h3(address: int, baud: int = 9600, mode: str = '8N2') -> int:
    if not 1 <= address <= 247 or baud not in BAUD_CODES.values() or mode not in MODE_CODES.values():
        raise ValueError("Niepoprawne parametry transmisji")
    baud_code = next(c for c, b in BAUD_CODES.items() if b == baud)
    mode_code = next(c for c, m in MODE_CODES.items() if m == mode)
    return address << 8 | baud_code << 4 | mode_code


PRESETS = {"NAWIEW": encode_h3(231), "WYWIEW": encode_h3(232)}
