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
})();
