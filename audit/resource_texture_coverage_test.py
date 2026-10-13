#!/usr/bin/env python3
"""Resolve every mod JSON model path and verify its inherited texture references."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/gonzotech"
NS = "gonzotech"


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def split_id(resource: str):
    if ":" in resource:
        return tuple(resource.split(":", 1))
    return "minecraft", resource


model_files = {
    f"{NS}:{path.relative_to(ASSETS / 'models').with_suffix('').as_posix()}": path
    for path in (ASSETS / "models").rglob("*.json")
}
errors = []


def resolve_model(resource: str, stack=()):
    """Merge a model and its parents; unresolved template variables are checked by callers."""
    namespace, model_path = split_id(resource)
    if namespace != NS:
        return {}
    model_id = f"{namespace}:{model_path}"
    if model_id in stack:
        errors.append(f"model parent cycle: {' -> '.join((*stack, model_id))}")
        return {}
    path = model_files.get(model_id)
    if path is None:
        errors.append(f"missing model: {model_id}")
        return {}
    try:
        model = load(path)
    except Exception as exc:  # noqa: BLE001
        errors.append(f"invalid model JSON {path.relative_to(ROOT)}: {exc}")
        return {}
    textures = {}
    parent = model.get("parent")
    if isinstance(parent, str):
        textures.update(resolve_model(parent, (*stack, model_id)))
    own_textures = model.get("textures", {})
    if isinstance(own_textures, dict):
        textures.update(own_textures)
    return textures


def validate_resolved_model(resource: str):
    namespace, _ = split_id(resource)
    if namespace != NS:
        return
    textures = resolve_model(resource)
    for key, value in textures.items():
        if not isinstance(value, str):
            continue
        if value.startswith("#"):
            if value[1:] not in textures:
                errors.append(f"{resource}: unresolved texture variable {value}")
            continue
        tex_namespace, tex_path = split_id(value)
        if tex_namespace == NS:
            texture_file = ASSETS / "textures" / f"{tex_path}.png"
            if not texture_file.is_file():
                errors.append(f"{resource}: missing texture {value} ({texture_file.relative_to(ROOT)})")
            if any(character.isupper() for character in tex_path):
                errors.append(f"{resource}: uppercase texture path {value}")


def referenced_models(node):
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "model" and isinstance(value, str):
                yield value
            else:
                yield from referenced_models(value)
    elif isinstance(node, list):
        for value in node:
            yield from referenced_models(value)


# Validate all literal texture paths, model JSON, parent links, and cycles. Some reusable
# parent models intentionally defer a variable (for example #all) to their child.
for model_id, path in sorted(model_files.items()):
    try:
        model = load(path)
    except Exception as exc:  # noqa: BLE001
        errors.append(f"invalid model JSON {path.relative_to(ROOT)}: {exc}")
        continue
    resolve_model(model_id)
    for value in (model.get("textures") or {}).values():
        if not isinstance(value, str) or value.startswith("#"):
            continue
        namespace, texture_path = split_id(value)
        if namespace == NS and not (ASSETS / "textures" / f"{texture_path}.png").is_file():
            errors.append(f"{model_id}: missing texture {value}")

item_defs = list((ASSETS / "items").glob("*.json"))
for path in item_defs:
    try:
        item = load(path)
    except Exception as exc:  # noqa: BLE001
        errors.append(f"invalid item definition {path.relative_to(ROOT)}: {exc}")
        continue
    for model_id in referenced_models(item):
        validate_resolved_model(model_id)

blockstates = list((ASSETS / "blockstates").glob("*.json"))
for path in blockstates:
    try:
        state = load(path)
    except Exception as exc:  # noqa: BLE001
        errors.append(f"invalid blockstate {path.relative_to(ROOT)}: {exc}")
        continue
    for model_id in referenced_models(state):
        validate_resolved_model(model_id)

print(f"Checked {len(item_defs)} item definitions, {len(blockstates)} blockstates, and {len(model_files)} models")
if errors:
    for error in errors:
        print(f"ERROR: {error}")
    raise SystemExit(f"{len(errors)} resource/model texture errors")
print("PASS: all JSON model paths and mod texture references resolve")
