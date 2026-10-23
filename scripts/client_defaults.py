"""Original client defaults and live workspace review presets; no vendor code."""
from pathlib import Path
import json
import socket
import struct

ROOT = Path(__file__).resolve().parents[1]
FANCY_PATH = 'config/fancymenu/options.txt'
SERVERS_PATH = 'config/chronicle/client-servers.json'


def _varint(value):
    result = bytearray()
    while value > 127:
        result.append((value & 127) | 128)
        value >>= 7
    return bytes(result + bytes([value]))


def _status(port, protocol):
    with socket.create_connection(('127.0.0.1', port), timeout=.3) as stream:
        def read(count):
            result = b''
            while len(result) < count:
                part = stream.recv(count-len(result))
                if not part:
                    raise ValueError('Incomplete Minecraft status')
                result += part
            return result
        def number():
            value = 0
            for shift in range(0, 35, 7):
                part = read(1)[0]
                value |= (part & 127) << shift
                if not part & 128:
                    return value
            raise ValueError('Oversized Minecraft status integer')
        host = b'127.0.0.1'
        hello = b'\x00' + _varint(protocol) + _varint(len(host)) + host + struct.pack('>H', port) + b'\x01'
        stream.sendall(_varint(len(hello)) + hello + b'\x01\x00')
        size = number()
        if not 0 < size < 1_000_000 or number() != 0:
            raise ValueError('Invalid Minecraft status packet')
        length = number()
        if not 0 < length < size:
            raise ValueError('Invalid Minecraft status length')
        return json.loads(read(length).decode('utf-8'))


def review_servers(work, protocol):
    """Probe only explicit authenticated loopback review configurations, never test-runs."""
    reviews = (Path(work) / 'reviews').resolve()
    candidates = sorted(reviews.glob('*/*/server.properties'))
    if len(candidates) > 64:
        raise ValueError('Too many review configurations for client preset discovery')
    ports = {}
    for path in candidates:
        if not path.resolve().is_relative_to(reviews):
            continue
        values = dict(line.split('=', 1) for line in path.read_text(encoding='utf-8').splitlines()
                      if line and not line.startswith(('#', '!')) and '=' in line)
        if values.get('server-ip') not in ('127.0.0.1', 'localhost') or values.get('online-mode') != 'true':
            continue
        try:
            port = int(values.get('server-port', '0'))
        except ValueError:
            continue
        if not 1 <= port <= 65535 or port == 25575:
            continue
        kind = path.parent.parent.name
        label = 'Current review' if kind == 'contact' else kind.replace('-', ' ').capitalize() + ' review'
        ports[port] = {'name': 'Chronicle - ' + label, 'address': f'127.0.0.1:{port}'}
    result = []
    for port, entry in sorted(ports.items()):
        try:
            status = _status(port, protocol)
            if status.get('version', {}).get('protocol') == protocol:
                result.append(entry)
        except (OSError, ValueError, KeyError, TypeError):
            continue
    return result


def payloads(work, protocol):
    return {
        FANCY_PATH: (ROOT / 'config/client/fancymenu/options.txt').read_bytes(),
        SERVERS_PATH: (json.dumps({'version': 1, 'servers': review_servers(work, protocol)}, indent=2)+'\n').encode('utf-8'),
    }


def validate_payloads(values):
    if set(values) != {FANCY_PATH, SERVERS_PATH}:
        raise ValueError('Unexpected client defaults')
    if values[FANCY_PATH] != (ROOT / 'config/client/fancymenu/options.txt').read_bytes():
        raise ValueError('Unexpected FancyMenu defaults')
    document = json.loads(values[SERVERS_PATH])
    if set(document) != {'version', 'servers'} or type(document['version']) is not int or document['version'] != 1 or not isinstance(document['servers'], list) or len(document['servers']) > 64:
        raise ValueError('Invalid client server presets')
    seen = set()
    for entry in document['servers']:
        if not isinstance(entry, dict) or set(entry) != {'name', 'address'} or not isinstance(entry['name'], str) or not 0 < len(entry['name']) <= 80 or any(ord(c)<32 for c in entry['name']):
            raise ValueError('Invalid server preset name')
        address = entry['address']
        if not isinstance(address, str) or not address.startswith('127.0.0.1:') or not address[10:].isdigit() or not 1 <= int(address[10:]) <= 65535 or int(address[10:]) == 25575 or address in seen:
            raise ValueError('Invalid or duplicate workspace server address')
        seen.add(address)
