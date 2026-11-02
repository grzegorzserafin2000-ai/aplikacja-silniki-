"""RS485 serial transport; all scans are read-only. No broadcast commands."""
from contextlib import contextmanager
from dataclasses import dataclass
import threading
import time
from typing import Callable, Optional

from protocol import (H0_PIN, H3, PIN_LEVEL_1, ComSetting, ModbusError,
                      ModbusException, read_request, write_request, check_reply, decode_h3)
from model_profile import can_write, product_label, pending_activation

_trace_local = threading.local()

@contextmanager
def capture_trace(callback):
    previous = getattr(_trace_local, 'callback', None)
    _trace_local.callback = callback
    try:
        yield
    finally:
        _trace_local.callback = previous

def _trace(message):
    callback = getattr(_trace_local, 'callback', None)
    if callback:
        callback(f'{time.monotonic():.3f} {message}')


@dataclass(frozen=True)
class Detection:
    port: str
    active_address: int
    baud: int
    parity: str
    stopbits: int
    raw_h3: int
    saved: ComSetting

    @property
    def active_info(self):
        label = 'Even' if self.parity == 'E' else 'None'
        return f"{self.active_address} · {self.baud} · {label} · {self.stopbits}"


def ports() -> list:
    import serial.tools.list_ports
    return [p.device for p in serial.tools.list_ports.comports()]


def transact(connection, address: int, function: int, register: int, value: int, timeout: float = 0.30):
    """One request, robust to a spurious echo or garbage preceding a valid reply."""
    if function not in (3, 4, 6) or timeout <= 0:
        raise ValueError('Nieobsługiwana funkcja MODBUS lub czas odpowiedzi')
    request = read_request(address, register, function) if function in (3, 4) else write_request(address, register, value)
    baud = getattr(connection, 'baudrate', 9600)
    silence = max(.004, 3.5 * 11 / baud)
    remaining = silence - (time.monotonic() - getattr(connection, '_climagold_last_io', -1e9))
    if remaining > 0:
        time.sleep(remaining)
    connection.timeout = min(timeout, 0.05)
    try:
        connection.reset_input_buffer()
        _trace('TX ' + request.hex(' ').upper())
        connection.write(request)
        connection.flush()
        connection._climagold_last_io = time.monotonic()
    except Exception as exc:
        raise ModbusError(f"Problem z portem: {exc}") from exc
    expected_size = 7 if function in (3, 4) else 8
    buffer = bytearray()
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        try:
            data = connection.read(max(1, expected_size - len(buffer)))
        except Exception as exc:
            raise ModbusError(f"Błąd odczytu portu: {exc}") from exc
        if not data:
            continue
        connection._climagold_last_io = time.monotonic()
        _trace('RX ' + data.hex(' ').upper())
        buffer.extend(data)
        if len(buffer) > 100:
            del buffer[:-60]
        # Find valid complete frames; do not accept an echoed FC06 request
        # as proof of a successful write. Normal RS485 adapters suppress echo.
        for size in (5, expected_size):
            for start in range(max(0, len(buffer) - size + 1)):
                candidate = bytes(buffer[start:start + size])
                if len(candidate) != size:
                    continue
                try:
                    result = check_reply(candidate, address, function, register, value)
                    _trace('FC06 ACK (wymaga odczytu; możliwe echo)' if function == 6 else 'READ OK')
                    return result
                except ModbusException:
                    raise
                except ModbusError:
                    pass
    _trace(f'TIMEOUT {timeout:.3f} s')
    raise TimeoutError("Brak poprawnej odpowiedzi MODBUS")


def _settings_groups(target_raw=None):
    base = [(19200, 'E', 1), (9600, 'N', 2)]
    if target_raw is not None:
        setting = decode_h3(target_raw)
        wanted = (setting.baud, setting.mode[1], int(setting.mode[2]))
        return [wanted] + [x for x in base if x != wanted]
    return base


def _priority_addresses(target_raw=None):
    base = [247, 231, 232, 221, 1]
    if target_raw is not None:
        address = decode_h3(target_raw).address
        return [address] + [x for x in base if x != address]
    return base


def scan(port: str, progress: Callable[[str], None], stop: threading.Event,
         quick: bool = False, target_raw=None) -> Optional[Detection]:
    import serial
    priority = _priority_addresses(target_raw)
    for baud, parity, bits in _settings_groups(target_raw):
        if stop.is_set():
            return None
        progress(f"Sprawdzam {baud} / {parity} / {bits} – {port}")
        try:
            conn = serial.Serial(port=port, baudrate=baud, bytesize=8,
                                 parity=parity, stopbits=bits, timeout=0.05, write_timeout=0.7,
                                 exclusive=True if hasattr(serial.Serial, 'exclusive') else None)
        except (ValueError, TypeError):
            conn = serial.Serial(port=port, baudrate=baud, bytesize=8,
                                 parity=parity, stopbits=bits, timeout=0.05, write_timeout=0.7)
        try:
            all_addrs = priority if quick else priority + [x for x in range(1, 248) if x not in priority]
            for addr in all_addrs:
                if stop.is_set():
                    return None
                if addr not in priority and addr % 20 == 1:
                    progress(f"Skanuję {baud} / {parity} / {bits} – adres {addr}/247")
                try:
                    value = transact(conn, addr, 3, H3, 1, timeout=0.15 if not quick else 0.23)
                    saved = decode_h3(value)
                    return Detection(port, addr, baud, parity, bits, value, saved)
                except (TimeoutError, ModbusError):
                    pass
                time.sleep(0.004)  # RS485 inter-frame idle gap
        finally:
            conn.close()
    return None


class SaveOutcome:
    """After an FC06 attempt, only an INDEPENDENT FC03 read can confirm h3.

    `status` values: already, stored_old, stored_new, unchanged, rejected, uncertain.
    A confirmed h3 value does not prove that the communication link is ACTIVE
    after a power cycle.
    """
    def __init__(self, readback: Optional[int], message: str, status: str,
                 attempted: bool, details: str):
        self.readback = readback
        self.message = message
        self.status = status
        self.attempted = attempted
        self.details = details


def assess_write(old_value: int, desired_value: int, old_read: Optional[int],
                 new_read: Optional[int], rejected=False) -> str:
    """Read-only verification; never infer success from FC06 echo alone."""
    if old_value == desired_value:
        return 'already'
    # An explicit MODBUS exception is a rejection; conflicting readback is uncertain.
    if rejected:
        return 'uncertain' if (old_read == desired_value or new_read == desired_value) else 'rejected'
    if old_read == desired_value:
        return 'stored_old'
    if new_read == desired_value:
        return 'stored_new'
    if old_read == old_value:
        return 'unchanged'
    return 'uncertain'


def _read_h3_safely(connection, address: int, timeout: float = 0.95):
    try:
        raw = transact(connection, address, 3, H3, 1, timeout=timeout)
        return raw, f'h3 = 0x{raw:04X}'
    except Exception as exc:
        return None, f'{type(exc).__name__}: {exc}'


def apply_preset(detection: Detection, target: int, progress: Callable[[str], None]) -> SaveOutcome:
    """Send FC06(h3) at most once and verify by read-only probes on old/new links.

    Compatible h3 model and matching active link are required. No automatic
    retry of a write after missing acknowledgment. No automatic power restart.
    """
    import serial
    desired = decode_h3(target)
    previous = detection.raw_h3
    if previous is None or not can_write(detection):
        raise ModbusError('Brak potwierdzonego profilu zapisu dla tego silnika.')
    old_link = (detection.baud, detection.parity, detection.stopbits)
    new_link = (desired.baud, desired.mode[1], int(desired.mode[2]))

    progress('Ponownie sprawdzam identyfikator silnika i1 oraz h3…')
    with serial.Serial(port=detection.port, baudrate=detection.baud, bytesize=8,
                       parity=detection.parity, stopbits=detection.stopbits,
                       timeout=0.05, write_timeout=0.9) as conn:
        code = transact(conn, detection.active_address, 4, 1, 1, timeout=0.65)
        if code != detection.product_code:
            raise ModbusError('Model zmienił się od wykrycia. Nie wysłano zapisu h3.')
        current = transact(conn, detection.active_address, 3, H3, 1, timeout=0.65)
        if current != previous:
            raise ModbusError('Parametry h3 zmieniły się od wykrycia. Bez zapisu, wykryj silnik ponownie.')
        if current == target:
            text = ('Żądane parametry są już zapisane w h3. Nie wysłano FC06. '
                    'Sprawdź aktywną komunikację po ewentualnym bezpiecznym restarcie.')
            return SaveOutcome(current, text, 'already', False, 'Zapis pominięty (wartość już jest w h3).')

        progress('Odblokowanie poziomu 1 (PIN 1234)…')
        transact(conn, detection.active_address, 6, H0_PIN, PIN_LEVEL_1, timeout=0.7)

        progress('Wysyłam ZAPIS h3 jeden raz…')
        # Treat a timeout / malformed response as UNCERTAIN, not as proof of failure.
        # Write request is never resent by this function.
        write_ack = 'Brak potwierdzenia'
        rejected = False
        try:
            transact(conn, detection.active_address, 6, H3, target, timeout=1.1)
            write_ack = 'Odebrano ramkę FC06 (wymagany niezależny odczyt h3)'
        except ModbusException as exc:
            rejected = True
            write_ack = f'Urządzenie odrzuciło FC06: {exc}'
        except Exception as exc:
            write_ack = f'Brak jednoznacznego potwierdzenia FC06: {type(exc).__name__}: {exc}'

        progress('Sprawdzam h3 na STARYM adresie / parametrach (tylko odczyt)…')
        old_read, old_note = _read_h3_safely(conn, detection.active_address)

    # Probe the new address / link only if it differs and old read is not enough.
    new_read = None
    new_note = 'Nie sprawdzano — odczyt na starym połączeniu potwierdził nową wartość'
    if old_read != target and (new_link != old_link or desired.address != detection.active_address):
        progress('Sprawdzam NOWY adres / parametry (tylko odczyt)…')
        try:
            with serial.Serial(port=detection.port, baudrate=desired.baud, bytesize=8,
                               parity=desired.mode[1], stopbits=int(desired.mode[2]),
                               timeout=0.05, write_timeout=0.9) as conn:
                new_read, new_note = _read_h3_safely(conn, desired.address)
        except Exception as exc:
            new_note = f'{type(exc).__name__}: {exc}'
    elif old_read != target:
        new_note = 'Nie sprawdzano — stary i nowy adres/tryb są identyczne'

    status = assess_write(previous, target, old_read, new_read, rejected=rejected)
    diagnostic = (f'FC06: {write_ack}\n'
                  f'Stary link ({detection.active_info}): {old_note}\n'
                  f'Nowy link ({desired.address} / {desired.baud} / {desired.mode}): {new_note}')
    if status == 'stored_old':
        summary = ('Odczyt h3 potwierdza NOWĄ wartość, ale silnik nadal odpowiada '
                   'na starym połączeniu. Zmiana może wymagać restartu zasilania.')
    elif status == 'stored_new':
        summary = 'Odczyt h3 potwierdza nową wartość na NOWYM połączeniu.'
    elif status == 'unchanged':
        summary = ('h3 nadal ma POPRZEDNIĄ wartość. Zmiana nie została potwierdzona. '
                   'Nie ponawiaj zapisu bez sprawdzenia w ZAset.')
    elif status == 'rejected':
        summary = 'Silnik jawnie odrzucił polecenie zapisu. Zweryfikuj model i dokumentację.'
    else:
        summary = ('WYNIK NIEPEWNY — nie wiadomo, czy silnik przyjął zmianę. '
                   'Nie powtarzaj zapisu. Sprawdź oba adresy w ZAset.')
    summary += ('\nPo bezpiecznym restarcie zasilania ponownie WYKRYJ silnik. '
                'Odczyt h3 sam w sobie nie potwierdza aktywnego adresu.')
    return SaveOutcome(old_read if old_read is not None else new_read,
                       summary, status, True, diagnostic)


# ZIEHL-ABEGG L-BAL-E311-GB p.7: PIN 3698 at h0 activates COM values stored in h3.
# V163 Carel EC_modbus FBD also uses za_pin_m1=3698 in its address-change flow.
# Dedicated operation only; never retry FC06 after a missing response.
PIN_COM_ACTIVATE = 3698


def classify_activation(old_address, target_address, current_old, current_new):
    """Determine LIVE link using read-only probes, not FC06 acknowledgement."""
    if current_new == target_address:
        return 'activated'
    if current_old == target_address:
        return 'stored_pending'
    return 'uncertain'


def activate_stored_communication(detection: Detection, progress: Callable[[str], None]) -> SaveOutcome:
    """Explicitly apply previously stored COM settings using h0 PIN 3698.

    Preconditions: device recognised, h3 differs from active connection,
    one device connected, technician confirmation in UI. No writes to h3.
    FC06 to h0 is sent ONCE, with read-only verification of both links.
    """
    if not pending_activation(detection):
        raise ModbusError('Brak potwierdzonej, oczekującej zmiany. Nie wysłano PIN 3698.')
    import serial
    if detection.saved is None:
        raise ModbusError('Nie odczytano nowych parametrów z h3.')
    target = detection.saved
    progress('Przed aktywacją sprawdzam ponownie identyfikator i1 oraz zapisany h3…')
    issued=False
    acknowledgement='Nie wysłano'
    with serial.Serial(port=detection.port, baudrate=detection.baud, bytesize=8,
                       parity=detection.parity, stopbits=detection.stopbits,
                       timeout=0.05, write_timeout=0.9) as conn:
        code = transact(conn, detection.active_address, 4, 1, 1, timeout=0.75)
        stored = transact(conn, detection.active_address, 3, H3, 1, timeout=0.75)
        if code != detection.product_code or stored != detection.raw_h3:
            raise ModbusError('Identyfikator lub h3 zmieniły się od wykrycia. Nic nie zapisano; wykryj ponownie.')
        progress('Aktywuję wcześniej zapisane h3: PIN 3698 → h0 (TYLKO RAZ)…')
        issued=True
        try:
            transact(conn, detection.active_address, 6, H0_PIN, PIN_COM_ACTIVATE, timeout=1.1)
            acknowledgement='FC06 ACK (nie dowodzi przełączenia)'
        except ModbusException as e:
            # A definite device exception is not an activation confirmation.
            acknowledgement=f'MODBUS wyjątek: {e}'
        except Exception as e:
            # Takeover may change the address before the ACK arrives.
            acknowledgement=f'Brak ACK lub odpowiedź niejednoznaczna: {type(e).__name__}: {e}'
        time.sleep(0.22)
        progress('Sprawdzam stary adres — tylko odczyt…')
        old_read, old_note = _read_h3_safely(conn, detection.active_address, 0.8)

    progress('Sprawdzam zapisany adres i tryb — tylko odczyt…')
    new_read=None; new_note='Nie sprawdzono'
    try:
        with serial.Serial(port=detection.port, baudrate=target.baud, bytesize=8,
                           parity=target.mode[1], stopbits=int(target.mode[2]),
                           timeout=0.05, write_timeout=0.9) as conn:
            new_read, new_note=_read_h3_safely(conn, target.address, 1.0)
    except Exception as e:
        new_note=f'{type(e).__name__}: {e}'

    # Merely reading h3 at the OLD address does not verify a live transition.
    # The new link must ANSWER to prove activation.
    outcome = 'activated' if new_read == detection.raw_h3 else (
        'stored_pending' if old_read == detection.raw_h3 else 'uncertain')
    result=(f'Aktywacja 3698: {acknowledgement}\n'
            f'AKTYWNY poprzednio: {detection.active_info}\n'
            f'ZAPISANY docelowo: {target.address} / {target.baud} / {target.mode}\n'
            f'STARY: {old_note}\nNOWY: {new_note}\n'
            'Nie wykonywano ponownego zapisu h3. ')
    if outcome=='activated':
        result += 'Nowy adres i parametry ODPOWIADAJĄ. Zweryfikuj je jeszcze w ZAset.'
    elif outcome=='stored_pending':
        result += 'Silnik nadal odpowiada na starym połączeniu. Aktywacja niepotwierdzona; nie ponawiaj jej w ciemno.'
    else:
        result += 'Nie udało się jednoznacznie potwierdzić aktywacji. Sprawdź w ZAset; bez ponownych zapisów.'
    return SaveOutcome(new_read if new_read is not None else old_read, result, outcome, issued,
                       f'PIN h0=3698; FC06 wydano tylko raz. {acknowledgement}')
