(() => {
  'use strict';

  const form = document.querySelector('.application-form');
  if (!form) return;

  const dependents = [...form.querySelectorAll('[data-show-field]')];

  const update = () => {
    dependents.forEach((field) => {
      const { showField, showValue } = field.dataset;
      const controls = [...form.querySelectorAll(`[name="${showField}"]`)];
      field.hidden = !controls.some((el) =>
        el.type === 'checkbox' || el.type === 'radio'
          ? el.checked && el.value === showValue
          : el.value === showValue);
    });
  };

  form.addEventListener('change', update);
  update();

  // City suggestions for the chosen country, from /cities/<code>.json.
  const country = form.querySelector('[name="country"]');
  const cities = document.getElementById('cities');
  const codes = new Map([...document.querySelectorAll('#countries option')]
    .map((option) => [option.value.toLowerCase(), option.dataset.code]));
  let shown = null;

  const suggestCities = async () => {
    const code = codes.get(country.value.trim().toLowerCase());
    if (code === shown) return;
    shown = code;
    if (!code) {
      cities.replaceChildren();
      return;
    }
    try {
      const response = await fetch(`/cities/${code}.json`);
      const names = response.ok ? await response.json() : [];
      if (shown !== code) return;
      cities.replaceChildren(...names.map((name) => new Option('', name)));
    } catch {
      shown = null;
    }
  };

  if (country && cities) {
    country.addEventListener('input', suggestCities);
    country.addEventListener('change', suggestCities);
    suggestCities();
  }
})();
