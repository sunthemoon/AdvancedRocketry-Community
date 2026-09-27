"""Finite ADR-031 example checks, not the production Codec or reload implementation."""

from copy import deepcopy
import json
import math
from pathlib import Path
import re
import unittest


HERE = Path(__file__).resolve().parent
SAMPLES = json.loads((HERE / 'examples.json').read_text(encoding='utf-8'))['samples']
BASE_FIELDS = {'id', 'gravity_multiplier', 'atmosphere', 'orbit', 'visual_profile'}
NEW_FIELDS = {'capabilities', 'solar_intensity', 'radiation'}
SPACE = 'advancedrocketrycommunity:space'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def identifier(value):
    require(isinstance(value, str) and len(value) <= 128
            and re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9/._-]+', value) is not None,
            'invalid ResourceLocation')


def number(value, low, high, integral=False):
    require(type(value) in ((int,) if integral else (int, float))
            and low <= value <= high and math.isfinite(value), 'invalid number')


def fields(value, required, optional=(), exact=True):
    require(isinstance(value, dict) and required <= value.keys(), 'missing fields/object')
    if exact:
        require(value.keys() <= required | set(optional), 'unknown fields')


def body(value):
    version = value.get('schema_version', 1) if isinstance(value, dict) else None
    require(type(version) is int and version in (1, 2), 'unsupported schema')
    legacy = version == 1
    required = BASE_FIELDS | ({'level'} if legacy else NEW_FIELDS | {'schema_version'})
    fields(value, required, {'schema_version', 'level', 'parent'}, exact=not legacy)
    if legacy:
        require(not (NEW_FIELDS & value.keys()), 'schema-2 fields require version 2')
    for key in ('id', 'visual_profile', 'level', 'parent'):
        if key in value:
            identifier(value[key])
    number(value['gravity_multiplier'], 0, 4)
    atmosphere = value['atmosphere']
    fields(atmosphere, {'pressure', 'breathable', 'temperature_kelvin', 'profile'}, exact=not legacy)
    number(atmosphere['pressure'], 0, 10)
    number(atmosphere['temperature_kelvin'], 0, 2000)
    require(type(atmosphere['breathable']) is bool, 'invalid breathable')
    require(not atmosphere['breathable'] or atmosphere['pressure'] > 0, 'breathable vacuum')
    identifier(atmosphere['profile'])
    orbit = value['orbit']
    fields(orbit, {'distance', 'period_ticks', 'inclination_degrees'}, exact=not legacy)
    number(orbit['distance'], 0, 1_000_000_000, integral=True)
    number(orbit['period_ticks'], 0, 10_000_000_000, integral=True)
    number(orbit['inclination_degrees'], -180, 180)
    require((orbit['distance'] == 0) == (orbit['period_ticks'] == 0), 'inconsistent root orbit')
    require(('parent' not in value) == (orbit['distance'] == 0), 'root/parent mismatch')
    require(value.get('parent') != value['id'], 'self parent')
    result = deepcopy(value)
    if legacy:
        result.update(schema_version=2, solar_intensity=1.0, radiation=0.0,
                      capabilities={'landable': value['level'] != SPACE,
                                    'orbitable': True, 'gas_giant': False})
    capabilities = result['capabilities']
    fields(capabilities, {'landable', 'orbitable', 'gas_giant'})
    require(all(type(v) is bool for v in capabilities.values()), 'invalid capability boolean')
    require(not capabilities['landable'] or 'level' in result, 'landable without Level')
    require(not capabilities['gas_giant']
            or (not capabilities['landable'] and 'level' not in result), 'gas giant surface')
    number(result['solar_intensity'], 0, 16)
    number(result['radiation'], 0, 1)
    return result


def raw_body(payload):
    require(len(payload) <= 32768, 'raw body exceeds byte limit')
    text = payload.decode('utf-8')
    depth, quoted, escaped = 0, False, False
    for char in text:
        if quoted:
            if escaped:
                escaped = False
            elif char == '\\':
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char in '{[':
            depth += 1
            require(depth <= 16, 'JSON nesting exceeds limit')
        elif char in '}]':
            depth -= 1

    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'duplicate JSON key')
            result[key] = value
        return result

    def constant(value):
        raise ValueError('non-JSON constant: ' + value)

    return body(json.loads(text, object_pairs_hook=unique, parse_constant=constant))


def catalog(values):
    require(0 < len(values) <= 128, 'body count')
    canonical = [body(value) for value in values]
    by_id = {value['id']: value for value in canonical}
    require(len(by_id) == len(values), 'duplicate body')
    for value in canonical:
        require('parent' not in value or value['parent'] in by_id, 'missing parent')
    for value in canonical:
        visited, current = set(), value['id']
        while current is not None:
            require(current not in visited, 'parent cycle')
            visited.add(current)
            current = by_id[current].get('parent')
    return by_id


class ContractExamplesTest(unittest.TestCase):
    def sample(self, name='schema2_mars_like'):
        return deepcopy(SAMPLES[name])

    def test_six_authored_examples(self):
        self.assertEqual(6, len(SAMPLES))
        for name, value in SAMPLES.items():
            with self.subTest(name=name):
                raw_body(json.dumps(value).encode('utf-8'))

    def test_legacy_values_preserved_and_defaults_explicit(self):
        original = self.sample('legacy_earth')
        self.assertEqual(self.sample('schema2_earth'), body(original))
        self.assertEqual(original, SAMPLES['legacy_earth'])
        original['level'] = SPACE
        self.assertFalse(body(original)['capabilities']['landable'])
        original['schema_version'] = 1
        self.assertEqual(2, body(original)['schema_version'])

    def test_schema_version_types_and_future_rejected(self):
        for value in (0, -1, 3, 2.0, 1.5, True, '2', None):
            candidate = self.sample()
            candidate['schema_version'] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                body(candidate)

    def test_new_fields_require_schema2(self):
        candidate = self.sample()
        del candidate['schema_version']
        with self.assertRaises(ValueError):
            body(candidate)

    def test_optional_is_not_null_or_malformed(self):
        for key in ('level', 'parent'):
            for value in (None, False, {}, 'INVALID ID', 'example:' + 'x' * 128):
                candidate = self.sample()
                candidate[key] = value
                with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                    body(candidate)

    def test_schema2_required_and_unknown_fields(self):
        for key in BASE_FIELDS | NEW_FIELDS:
            candidate = self.sample()
            del candidate[key]
            with self.subTest(key=key), self.assertRaises(ValueError):
                body(candidate)
        candidate = self.sample()
        candidate['landabel'] = True
        with self.assertRaises(ValueError):
            body(candidate)
        candidate = self.sample('legacy_earth')
        candidate['old_ignored_note'] = 'kept outside new format'
        body(candidate)

    def test_capability_combinations(self):
        candidate = self.sample()
        del candidate['level']
        with self.assertRaises(ValueError):
            body(candidate)
        candidate = self.sample('schema2_gas_giant')
        candidate['level'] = SPACE
        with self.assertRaises(ValueError):
            body(candidate)
        for value in (0, 1, 'false', None):
            candidate = self.sample()
            candidate['capabilities']['landable'] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                body(candidate)
        self.assertIn('level', body(self.sample('schema2_closed_moon')))

    def test_numeric_limits_and_finiteness(self):
        for key, maximum in (('gravity_multiplier', 4), ('solar_intensity', 16), ('radiation', 1)):
            for value in (-0.1, maximum + 0.1, math.nan, math.inf, True, 10 ** 400):
                candidate = self.sample()
                candidate[key] = value
                with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                    body(candidate)
            for value in (0, maximum):
                candidate = self.sample()
                candidate[key] = value
                body(candidate)

    def test_atmosphere_and_integral_orbits(self):
        for key, value in (('pressure', -1), ('pressure', 11), ('temperature_kelvin', 2001),
                           ('temperature_kelvin', math.nan), ('breathable', 1)):
            candidate = self.sample()
            candidate['atmosphere'][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                body(candidate)
        candidate = self.sample('schema2_earth')
        candidate['atmosphere']['pressure'] = 0
        with self.assertRaises(ValueError):
            body(candidate)
        for key in ('distance', 'period_ticks'):
            candidate = self.sample()
            candidate['orbit'][key] = 1.5
            with self.assertRaises(ValueError):
                body(candidate)

    def test_raw_syntax_duplicates_and_non_json_numbers(self):
        for raw in (b'{', b'{"id":1,"id":2}', b'{"id":NaN}', b'[]', b'null'):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                raw_body(raw)

    def test_raw_byte_limit_precedes_json_parsing(self):
        with self.assertRaisesRegex(ValueError, 'byte limit'):
            raw_body(b' ' * 32769)
        with self.assertRaisesRegex(ValueError, 'byte limit'):
            raw_body(('\u2603' * 11000).encode('utf-8'))

    def test_nesting_limit_and_braces_in_strings(self):
        with self.assertRaisesRegex(ValueError, 'nesting'):
            raw_body(('[' * 17 + '0' + ']' * 17).encode())
        candidate = self.sample('legacy_earth')
        candidate['ignored_note'] = '{' * 30 + '\\"' + '}' * 30
        raw_body(json.dumps(candidate).encode())

    def test_graph_missing_duplicate_and_cycle(self):
        earth, mars = self.sample('schema2_earth'), self.sample()
        for values in ([mars], [earth, earth]):
            with self.assertRaises(ValueError):
                catalog(values)
        earth['parent'] = mars['id']
        earth['orbit'].update(distance=1, period_ticks=1)
        with self.assertRaisesRegex(ValueError, 'cycle'):
            catalog([earth, mars])
        earth['parent'] = 'example:missing'
        with self.assertRaisesRegex(ValueError, 'missing parent'):
            catalog([mars, earth])

    def test_100_body_sample_and_128_body_ceiling(self):
        values = [self.sample('schema2_earth')]
        for index in range(127):
            value = self.sample('schema2_gas_giant')
            value['id'] = f'example:body_{index}'
            values.append(value)
        self.assertEqual(100, len(catalog(values[:100])))
        self.assertEqual(128, len(catalog(values)))
        values.append(self.sample())
        with self.assertRaisesRegex(ValueError, 'body count'):
            catalog(values)


if __name__ == '__main__':
    unittest.main(verbosity=2)
