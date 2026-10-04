"""Neutral parser/identity negative controls, not K2/runtime behavior tests."""
import hashlib
import re
import unittest
import xml.etree.ElementTree as ET

def identity(raw, expected):
    actual={'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
    if actual!=expected:raise ValueError('identity mismatch')
    return actual
def totals(raw):
    root=ET.fromstring(raw)
    if root.tag!='testsuite':raise ValueError('suite required')
    children=root.findall('testcase')
    values={'tests':len(children), 'failures':sum(c.find('failure') is not None for c in children),
            'errors':sum(c.find('error') is not None for c in children),'skipped':sum(c.find('skipped') is not None for c in children)}
    if values!={k:int(root.attrib[k]) for k in values}:raise ValueError('declared mismatch')
    return values
def terminal(text):
    rows=re.findall(r'All (\d+) required tests passed',text)
    if len(rows)!=1:raise ValueError('one terminal required')
    return int(rows[0])
def phase_status(exit_code, documented_failure):
    if documented_failure!=(exit_code!=0):raise ValueError('nonpass mislabelled')
    return 'nonpass' if exit_code else 'passed'

class EvidenceControls(unittest.TestCase):
    def test_exact_identity(self):
        self.assertEqual(identity(b'ab',{'bytes':2,'sha256':hashlib.sha256(b'ab').hexdigest()})['bytes'],2)
    def test_same_size_changed_bytes(self):
        with self.assertRaises(ValueError):identity(b'ba',{'bytes':2,'sha256':hashlib.sha256(b'ab').hexdigest()})
    def test_declared_failure_is_measured(self):
        self.assertEqual(totals(b'<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase><failure/></testcase></testsuite>')['failures'],1)
    def test_bad_declared_status(self):
        with self.assertRaises(ValueError):totals(b'<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase><failure/></testcase></testsuite>')
    def test_repeated_parameterized_display_names_are_not_identity_failure(self):
        self.assertEqual(totals(b'<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase name="a"/><testcase name="a"/></testsuite>')['tests'],2)
    def test_missing_xml_suite(self):
        with self.assertRaises(ValueError):totals(b'<testsuites/>')
    def test_single_terminal(self):
        self.assertEqual(terminal('All 3 required tests passed'),3)
    def test_missing_terminal(self):
        with self.assertRaises(ValueError):terminal('waiting')
    def test_duplicate_terminal(self):
        with self.assertRaises(ValueError):terminal('All 3 required tests passed\nAll 3 required tests passed')
    def test_dirty_exit_is_nonpass(self):
        self.assertEqual(phase_status(1,True),'nonpass')
    def test_documented_exit_cannot_be_called_pass(self):
        with self.assertRaises(ValueError):phase_status(1,False)
    def test_timestamp_styles_split_before_next_event(self):
        pattern=re.compile(r'^\[[^\]\r\n]+\] \[[^\]\r\n]+/(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\]')
        for value in ('[04Oct2026 12:00:00.001] [Server thread/ERROR] [x/]: a','[12:00:00] [Server thread/INFO] [x/]: b'):
            self.assertIsNotNone(pattern.match(value))
        self.assertIsNone(pattern.match(' at class.method(File.java:1)'))

if __name__=='__main__':unittest.main(verbosity=2)
