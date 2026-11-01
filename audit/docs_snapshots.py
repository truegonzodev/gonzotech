"""Validate explicitly registered, immutable previous editions (not all archive links)."""
from pathlib import Path
import hashlib
import json
import re
import subprocess


def registered_snapshots(root):
    root = Path(root).resolve()
    editions = root / 'docs/archive/editions'
    frozen = set()
    for manifest in sorted(editions.glob('*/manifest.json')):
        data = json.loads(manifest.read_text())
        assert data['schema'] == 1 and data['files'], manifest
        assert (manifest.parent / 'README.md').is_file(), manifest
        for record in data['files']:
            source_name = Path(record['source'])
            source = root / source_name
            resolved_source = source.resolve()
            docs_source = (not source_name.is_absolute()
                           and source_name.parts[:1] == ('docs',)
                           and resolved_source.is_relative_to(root / 'docs'))
            # Only the author's direct Markdown package files join docs sources.
            # Do not allow arbitrary audit files, nested paths or symlink escapes.
            author_source = (not source_name.is_absolute()
                             and len(source_name.parts) == 3
                             and source_name.parts[:2] == ('audit', 'author test')
                             and source_name.suffix == '.md'
                             and resolved_source.parent == root / 'audit/author test')
            assert docs_source or author_source, source
            path = (root / record['snapshot']).resolve()
            assert path.parent == manifest.parent.resolve(), path
            assert path.name.startswith('OUTDATED_') and path.suffix == '.md', path
            assert re.fullmatch(r'[0-9a-f]{40}', record['source_commit']), record
            assert record['task'] and path not in frozen, record
            content = path.read_bytes()
            assert len(content) == record['bytes'], path
            assert hashlib.sha256(content).hexdigest() == record['sha256'], path
            # A shallow checkout may not have the old object; hashes still apply.
            commit = record['source_commit']
            if subprocess.run(['git', 'cat-file', '-e', f'{commit}^{{commit}}'],
                              cwd=root, capture_output=True).returncode == 0:
                old = subprocess.check_output(['git', 'show', f'{commit}:{record["source"]}'], cwd=root)
                assert old == content, f'{path}: not the exact previous edition'
            frozen.add(path)
    actual = {p.resolve() for p in editions.rglob('OUTDATED_*') if p.is_file()}
    assert actual == frozen, 'unregistered or missing previous editions'
    return frozen
