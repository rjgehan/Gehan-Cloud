// Clean up if hot-reloading
if (window._leaflet_map) { window._leaflet_map.off(); window._leaflet_map.remove(); }

const light = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19, attribution: '&copy; OpenStreetMap contributors'
});
const dark  = L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
  maxZoom: 19, attribution: '&copy; OpenStreetMap contributors &copy; CARTO'
});

const mq = window.matchMedia('(prefers-color-scheme: dark)');
const saved = localStorage.getItem('mapTheme');              // 'light' | 'dark' | null
const startDark = saved ? saved === 'dark' : mq.matches;
const startLayer = startDark ? dark : light;

const map = L.map('map', {
  center: [39.040758, -77.487111],
  zoom: 13,
  layers: [startLayer],
});
window._leaflet_map = map;

// ----- Toggle control -----
const ThemeToggle = L.Control.extend({
  options: { position: 'topright' },
  onAdd(m) {
    const box = L.DomUtil.create('div', 'leaflet-bar theme-toggle');
    box.innerHTML = `
      <input id="themeSwitch" type="checkbox" ${startDark ? 'checked' : ''} />
      <label for="themeSwitch" title="Toggle light/dark" aria-label="Toggle light/dark"></label>
    `;
    const input = box.querySelector('#themeSwitch');

    const apply = (useDark) => {
      if (useDark) { if (!m.hasLayer(dark)) m.addLayer(dark); if (m.hasLayer(light)) m.removeLayer(light); }
      else         { if (!m.hasLayer(light)) m.addLayer(light); if (m.hasLayer(dark)) m.removeLayer(dark); }
    };

    input.addEventListener('change', e => {
      const on = e.target.checked;
      apply(on);
      localStorage.setItem('mapTheme', on ? 'dark' : 'light'); // remember choice
    });

    // Only auto-follow system theme if no manual choice saved
    if (!saved) {
      mq.addEventListener('change', e => {
        input.checked = e.matches;
        apply(e.matches);
      });
    }

    L.DomEvent.disableClickPropagation(box);
    return box;
  }
});
map.addControl(new ThemeToggle());

// Keep map sized correctly
requestAnimationFrame(() => map.invalidateSize());
window.addEventListener('resize', () => map.invalidateSize());
new ResizeObserver(() => map.invalidateSize()).observe(document.getElementById('map'));
