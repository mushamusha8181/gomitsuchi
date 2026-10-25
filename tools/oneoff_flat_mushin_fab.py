from pathlib import Path

# trigger: 2026-10-25.2
p = Path('app/src/main/java/jp/mushamusha/gomireminder/MainActivity.kt')
s = p.read_text()
old = 'floatingActionButton = { SmallFloatingActionButton(onClick = { adding = true }) { Text("＋") } }'
new = '''floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { adding = true },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp
                )
            ) { Text("＋") }
        }'''
if old not in s:
    raise SystemExit('FAB anchor missing')
p.write_text(s.replace(old, new, 1))
