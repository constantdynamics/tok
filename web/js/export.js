import { visibleBullets, labelsForBullet } from './store.js';

// Exporteert telkens de huidige (gefilterde + gesorteerde) lijst zoals zichtbaar.

function download(filename, text, type) {
  const blob = new Blob([text], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

const stamp = () => new Date().toISOString().slice(0, 10);

export function exportMarkdown() {
  const lines = visibleBullets().map((b) => {
    const labels = labelsForBullet(b.id).map((l) => `#${l.name}`).join(' ');
    const box = b.is_archived ? '- [x] ' : '- [ ] ';
    return `${box}${b.text}${labels ? '  ' + labels : ''}`;
  });
  const body = `# tok — bullets (${stamp()})\n\n${lines.join('\n')}\n`;
  download(`tok-${stamp()}.md`, body, 'text/markdown');
}

export function exportJson() {
  const data = visibleBullets().map((b) => ({
    id: b.id,
    text: b.text,
    created_at: b.created_at,
    updated_at: b.updated_at,
    is_archived: b.is_archived,
    sort_order: b.sort_order,
    labels: labelsForBullet(b.id).map((l) => ({ id: l.id, name: l.name, color: l.color })),
  }));
  download(`tok-${stamp()}.json`, JSON.stringify(data, null, 2), 'application/json');
}

export function exportCsv() {
  const esc = (s) => `"${String(s ?? '').replace(/"/g, '""')}"`;
  const rows = [['text', 'labels', 'created_at', 'archived']];
  for (const b of visibleBullets()) {
    rows.push([
      b.text,
      labelsForBullet(b.id).map((l) => l.name).join('; '),
      b.created_at,
      b.is_archived,
    ]);
  }
  const body = rows.map((r) => r.map(esc).join(',')).join('\n') + '\n';
  download(`tok-${stamp()}.csv`, body, 'text/csv');
}
