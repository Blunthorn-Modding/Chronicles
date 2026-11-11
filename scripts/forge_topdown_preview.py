"""Render orthographic Forge design views from the project's original Blockbench exports.

The SVGs embed unchanged existing campaign textures; no new texture or game art
is invented. This is a design-preview helper, not a Minecraft renderer or UI change.
"""
from pathlib import Path
import json
import base64
from xml.sax.saxutils import quoteattr

REPO = Path(__file__).resolve().parents[1]
ASSETS = REPO / 'campaigns/quiet-below/src/main/resources/assets/quiet_below'
OUTPUT = REPO / 'docs/design'
STATES = {'idle': 'ancient_forge', 'kindling': 'ancient_forge_kindling', 'active': 'ancient_forge_active'}


def render(name, model):
    data = json.loads((ASSETS / 'models/block' / (model + '.json')).read_text(encoding='utf-8'))
    faces = []
    for element in data['elements']:
        if element.get('rotation', {}).get('angle', 0) != 0:
            raise ValueError('Preview requires the current axis-aligned Forge export')
        face = element.get('faces', {}).get('up')
        if not face:
            continue
        if face.get('rotation', 0) != 0:
            raise ValueError('Preview requires unrotated up-face UVs')
        start, end = element['from'], element['to']
        if end[0] <= start[0] or end[2] <= start[2]:
            raise ValueError('Degenerate top face')
        faces.append((end[1], start, end, face))
    result = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="-3.5 -3.5 23 23" shape-rendering="crispEdges">',
        '  <!-- Generated top-down projection of the original Forge model and texture. -->',
    ]
    atlases = {}
    result.append('  <defs>')
    for _, _, _, face in faces:
        texture = data['textures'][face['texture'].removeprefix('#')]
        if texture in atlases:
            continue
        namespace, relative = texture.split(':', 1)
        if namespace != 'quiet_below' or not relative.startswith('block/'):
            raise ValueError('Unexpected texture ownership')
        raw = (ASSETS / 'textures' / (relative + '.png')).read_bytes()
        atlas = 'atlas' + str(len(atlases))
        atlases[texture] = atlas
        encoded = base64.b64encode(raw).decode('ascii')
        result.append(f'    <image id="{atlas}" href="data:image/png;base64,{encoded}" width="16" height="16" preserveAspectRatio="none" style="image-rendering:pixelated"/>')
    result.append('  </defs>')
    # All current elements are unrotated cuboids: depth-sorted up faces are the
    # exact visible projection. Transparent texture pixels reveal lower faces.
    for _, start, end, face in sorted(faces, key=lambda row: row[0]):
        texture = data['textures'][face['texture'].removeprefix('#')]
        u0, v0, u1, v1 = face['uv']
        if u0 == u1 or v0 == v1:
            raise ValueError('Degenerate top-face UV')
        # Model UVs are normalized to16 even though the original atlas is128px.
        sx, sy = (end[0] - start[0]) / (u1-u0), (end[2] - start[2]) / (v1-v0)
        tx, ty = start[0] - u0*sx, start[2] - v0*sy
        result.append(f'  <svg x="{start[0]:g}" y="{start[2]:g}" width="{end[0]-start[0]:g}" height="{end[2]-start[2]:g}" viewBox="{start[0]:g} {start[2]:g} {end[0]-start[0]:g} {end[2]-start[2]:g}" overflow="hidden">')
        result.append(f'    <use href={quoteattr("#" + atlases[texture])} transform="matrix({sx:g} 0 0 {sy:g} {tx:g} {ty:g})"/>')
        result.append('  </svg>')
    result.append('</svg>')
    target = OUTPUT / f'forge-top-{name}.svg'
    target.write_text('\n'.join(result) + '\n', encoding='utf-8')
    return target, len(faces)


if __name__ == '__main__':
    for state, model in STATES.items():
        target, count = render(state, model)
        print(f'{target.name}: {count} original top faces')
