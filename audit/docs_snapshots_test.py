#!/usr/bin/env python3
"""Archive registration regression: narrow author-source scope, immutable bytes/Git."""
import hashlib
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import docs_snapshots


class SnapshotRegistrationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='gonzotech-doc-snapshots-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.edition = self.root / 'docs/archive/editions/0.3.332'
        self.edition.mkdir(parents=True)
        (self.edition / 'README.md').write_text('# Frozen originals\n')
        self.content = b'# exact original\n'
        self.snapshot = self.edition / 'OUTDATED_AUTHOR332_00.md'
        self.snapshot.write_bytes(self.content)
        self.record = dict(source='audit/author test/00 — Карта пакетов.md',
                           source_commit='1' * 40,
                           snapshot=str(self.snapshot.relative_to(self.root)),
                           sha256=hashlib.sha256(self.content).hexdigest(),
                           bytes=len(self.content), task='Preserve exact author source')
        self.records = [self.record]
        # Deterministic shallow-clone path; exact-Git branch has a separate test.
        self.git = patch.object(docs_snapshots.subprocess, 'run',
                                return_value=SimpleNamespace(returncode=1))
        self.git.start()
        self.addCleanup(self.git.stop)

    def validate(self):
        (self.edition / 'manifest.json').write_text(
            json.dumps(dict(schema=1, files=self.records), ensure_ascii=False))
        return docs_snapshots.registered_snapshots(self.root)

    def test_direct_author_markdown(self):
        self.assertEqual(self.validate(), {self.snapshot})

    def test_docs_source_still_allowed(self):
        for source in ('docs/CURRENT-STATE.md', 'docs/archive/' + 'old.md'):
            with self.subTest(source=source):
                self.record['source'] = source
                self.assertEqual(self.validate(), {self.snapshot})

    def test_no_other_source_scope(self):
        for source in ('audit/other.md', 'audit/author test/nested/a.md',
                       'audit/author test/a.txt', 'audit/author test/a.MD',
                       'audit/author test/../other.md', '../docs/' + 'a.md',
                       'src/main/java/A.java', str(self.root / 'docs' / 'a.md'),
                       'audit/author test/../../docs/' + 'a.md'):
            with self.subTest(source=source):
                self.record['source'] = source
                with self.assertRaises(AssertionError):
                    self.validate()

    def test_author_file_symlink_escape(self):
        parent = self.root / 'audit/author test'
        parent.mkdir(parents=True)
        outside = self.root / 'outside.md'
        outside.write_bytes(self.content)
        (parent / 'escape.md').symlink_to(outside)
        self.record['source'] = 'audit/author test/escape.md'
        with self.assertRaises(AssertionError):
            self.validate()

    def test_author_folder_symlink_escape(self):
        (self.root / 'audit').mkdir()
        outside = self.root / 'outside'
        outside.mkdir()
        (self.root / 'audit/author test').symlink_to(outside, target_is_directory=True)
        with self.assertRaises(AssertionError):
            self.validate()

    def test_size_corruption(self):
        self.snapshot.write_bytes(self.content + b'!')
        with self.assertRaises(AssertionError):
            self.validate()

    def test_same_length_hash_corruption(self):
        self.snapshot.write_bytes(b'!' + self.content[1:])
        with self.assertRaises(AssertionError):
            self.validate()

    def test_duplicate_registration(self):
        self.records.append(dict(self.record))
        with self.assertRaises(AssertionError):
            self.validate()

    def test_unregistered_outdated_not_ignored(self):
        (self.edition / 'OUTDATED_unregistered.md').write_bytes(self.content)
        with self.assertRaises(AssertionError):
            self.validate()

    def test_missing_snapshot(self):
        self.snapshot.unlink()
        with self.assertRaises(FileNotFoundError):
            self.validate()

    def test_snapshot_must_stay_in_own_manifest_folder(self):
        outside = self.edition.parent / 'OUTDATED_escape.md'
        outside.write_bytes(self.content)
        self.record['snapshot'] = str(outside.relative_to(self.root))
        with self.assertRaises(AssertionError):
            self.validate()

    def test_git_source_content_not_just_self_reported_hash(self):
        with patch.object(docs_snapshots.subprocess, 'run',
                          return_value=SimpleNamespace(returncode=0)), \
                patch.object(docs_snapshots.subprocess, 'check_output',
                             return_value=self.content) as old:
            self.assertEqual(self.validate(), {self.snapshot})
            self.assertEqual(old.call_args.args[0],
                             ['git', 'show', '1' * 40 + ':' + self.record['source']])
            old.return_value = b'# different Git content\n'
            with self.assertRaisesRegex(AssertionError, 'not the exact previous edition'):
                self.validate()

    def test_full_commit_and_reason_required(self):
        self.record['source_commit'] = '1234567'
        with self.assertRaises(AssertionError):
            self.validate()
        self.record['source_commit'] = '1' * 40
        self.record['task'] = ''
        with self.assertRaises(AssertionError):
            self.validate()


if __name__ == '__main__':
    unittest.main()
