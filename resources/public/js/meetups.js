(() => {
  'use strict';

  const dataEl = document.getElementById('meetup-data');
  if (!dataEl) return;

  const { meetups, aliases } = JSON.parse(dataEl.textContent);
  const canHover = window.matchMedia('(hover: hover)').matches;

  // Mirrors app.countries/normalize and app.countries/matches?
  const normalize = (s) =>
    (s || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();

  const countryOptions = document.querySelectorAll('#countries option');
  const countries = new Map([...countryOptions].map((o) => [normalize(o.value), o.value]));
  Object.entries(aliases).forEach(([alias, name]) => countries.set(alias, name));

  const canonicalCountry = (query) => countries.get(normalize(query));

  const matcher = (query) => {
    const q = normalize(query);
    if (!q) return () => true;
    const country = canonicalCountry(q);
    return country
      ? (m) => m.country === country
      : (m) => normalize(m.country).includes(q);
  };

  // ---- Map ---------------------------------------------------------------

  const mapEl = document.getElementById('map');
  const single = mapEl?.dataset.mode === 'single';
  const markers = new Map();
  let map;
  let pins;

  const pinIcon = (m, i) => L.divIcon({
    className: 'pin-icon',
    html: `<div class="pin pin--${m.format.id}" style="--delay:${i * 70}ms">
             <svg class="pin__head" viewBox="0 0 32 42" aria-hidden="true">
               <path d="M16 1C7.7 1 1 7.6 1 15.8 1 27 16 41 16 41s15-14 15-25.2C31 7.6 24.3 1 16 1z"/>
               <circle cx="16" cy="15.5" r="5.5"/>
             </svg>
             <span class="pin__shadow"></span>
           </div>`,
    iconSize: [32, 42],
    iconAnchor: [16, 42],
    tooltipAnchor: [0, -50],
    popupAnchor: [0, -50],
  });

  const summary = (m, { link = false } = {}) => {
    const el = document.createElement('div');
    el.className = 'meetup-tip__body';
    const add = (tag, className, text) => {
      const node = document.createElement(tag);
      node.className = className;
      node.textContent = text;
      el.append(node);
      return node;
    };
    add('span', `badge badge--${m.format.id}`, m.format.label);
    add('strong', 'meetup-tip__name', m.name);
    add('span', 'meetup-tip__place', m.place);
    add('span', 'meetup-tip__frequency', m.frequency);
    if (link) add('a', 'meetup-tip__link', 'View meetup →').href = m.url;
    return el;
  };

  const cardFor = (slug) => document.querySelector(`.meetup-card[data-slug="${slug}"]`);

  const highlight = (slug, on) => {
    const marker = markers.get(slug);
    marker?.getElement()?.querySelector('.pin')?.classList.toggle('is-active', on);
    marker?.setZIndexOffset(on ? 1000 : 0);
    cardFor(slug)?.classList.toggle('is-active', on);
  };

  const fit = ({ animate = false } = {}) => {
    if (!pins.getLayers().length) {
      map.flyTo([20, 0], 1.5, { duration: animate ? 0.8 : 0 });
      return;
    }
    const options = { padding: [48, 48], maxZoom: single ? 12 : 6 };
    if (animate) map.flyToBounds(pins.getBounds(), { ...options, duration: 0.8 });
    else map.fitBounds(pins.getBounds(), options);
  };

  if (mapEl && window.L) {
    map = L.map(mapEl, { scrollWheelZoom: false, worldCopyJump: true, zoomSnap: 0.5 });
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(map);
    pins = L.featureGroup().addTo(map);

    meetups.forEach((m, i) => {
      const marker = L.marker([m.lat, m.lng], { icon: pinIcon(m, i), keyboard: !single });

      if (canHover) {
        marker.bindTooltip(summary(m), { direction: 'top', className: 'meetup-tip', opacity: 1 });
        marker.on('tooltipopen', ({ tooltip }) => {
          // Pins close to the top edge would get their tooltip clipped
          const below = map.latLngToContainerPoint(marker.getLatLng()).y < 170;
          tooltip.options.direction = below ? 'bottom' : 'top';
          tooltip.options.offset = below ? [0, 58] : [0, 0];
          tooltip.update();
        });
        marker.on('mouseover', () => highlight(m.slug, true));
        marker.on('mouseout', () => highlight(m.slug, false));
        if (!single) marker.on('click', () => window.location.assign(m.url));
      } else if (!single) {
        marker.bindPopup(summary(m, { link: true }), { className: 'meetup-tip', closeButton: false });
      }

      marker.addTo(pins);
      markers.set(m.slug, marker);
    });

    fit();
    new ResizeObserver(() => { map.invalidateSize(); fit(); }).observe(mapEl);
  }

  document.querySelectorAll('.meetup-card').forEach((card) => {
    const { slug } = card.dataset;
    const show = () => {
      highlight(slug, true);
      if (pins?.hasLayer(markers.get(slug))) markers.get(slug).openTooltip();
    };
    const hide = () => {
      highlight(slug, false);
      markers.get(slug)?.closeTooltip();
    };
    card.addEventListener('mouseenter', show);
    card.addEventListener('mouseleave', hide);
    card.addEventListener('focusin', show);
    card.addEventListener('focusout', hide);
  });

  // ---- Country filter ----------------------------------------------------

  const input = document.getElementById('country-filter');
  if (!input) return;

  const cards = [...document.querySelectorAll('.meetup-card')];
  const shownCount = document.querySelector('.meetups-count__shown');
  const emptyCountry = document.querySelector('[data-empty="country"]');
  const emptyNone = document.querySelector('[data-empty="none"]');
  const applyLink = emptyCountry.querySelector('[data-apply-country]');

  const filter = (query, { animate = true } = {}) => {
    const visible = new Set(meetups.filter(matcher(query)).map((m) => m.slug));
    const country = canonicalCountry(query);
    const nothing = visible.size === 0;

    cards.forEach((card) => { card.hidden = !visible.has(card.dataset.slug); });
    shownCount.textContent = visible.size;

    emptyCountry.hidden = !(nothing && country);
    emptyNone.hidden = !(nothing && !country);
    if (country) {
      emptyCountry.querySelector('.empty-state__country').textContent = country;
      applyLink.dataset.applyCountry = country;
    }
    emptyNone.querySelector('.empty-state__query').textContent = query.trim();

    if (pins) {
      markers.forEach((marker, slug) => {
        if (visible.has(slug)) pins.addLayer(marker);
        else pins.removeLayer(marker);
      });
      fit({ animate });
    }

    const url = new URL(window.location.href);
    if (query.trim()) url.searchParams.set('country', query.trim());
    else url.searchParams.delete('country');
    window.history.replaceState(null, '', url);
  };

  let timer;
  input.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => filter(input.value), 180);
  });
  input.form.addEventListener('submit', (e) => {
    e.preventDefault();
    filter(input.value);
  });

  applyLink.addEventListener('click', () => {
    const field = document.querySelector('#apply [name="country"]');
    if (field) field.value = applyLink.dataset.applyCountry;
  });

  if (input.value) filter(input.value, { animate: false });
})();
